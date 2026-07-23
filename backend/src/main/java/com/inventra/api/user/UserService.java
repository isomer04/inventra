package com.inventra.api.user;

import com.inventra.api.audit.AuditEventType;
import com.inventra.api.audit.AuditPayload;
import com.inventra.api.audit.AuditService;
import com.inventra.api.entity.Tenant;
import com.inventra.api.entity.User;
import com.inventra.api.entity.UserRole;
import com.inventra.api.entity.UserStatus;
import com.inventra.api.exception.DuplicateResourceException;
import com.inventra.api.exception.InvalidRequestException;
import com.inventra.api.exception.ResourceInUseException;
import com.inventra.api.exception.ResourceNotFoundException;
import com.inventra.api.order.OrderRepository;
import com.inventra.api.order.OrderStatusHistoryRepository;
import com.inventra.api.repository.RefreshTokenRepository;
import com.inventra.api.repository.StockMovementRepository;
import com.inventra.api.repository.TenantRepository;
import com.inventra.api.repository.UserRepository;
import com.inventra.api.tenant.TenantContext;
import com.inventra.api.util.LogSanitizer;
import com.inventra.api.user.dto.CreateUserRequest;
import com.inventra.api.user.dto.UpdateStatusRequest;
import com.inventra.api.user.dto.UpdateUserRequest;
import com.inventra.api.user.dto.UserResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class UserService {

    private final UserRepository userRepository;
    private final TenantRepository tenantRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final OrderRepository orderRepository;
    private final OrderStatusHistoryRepository orderStatusHistoryRepository;
    private final StockMovementRepository stockMovementRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserMapper userMapper;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public List<UserResponse> getAll() {
        return userRepository.findAllByTenantId(TenantContext.requireTenantId())
                .stream().map(userMapper::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public UserResponse getById(String id, User caller) {
        User target = loadInTenant(id);
        if (!caller.getId().equals(target.getId()) && !isAdminOrManager(caller)) {
            throw new AccessDeniedException("Access denied");
        }
        return userMapper.toResponse(target);
    }

    public UserResponse create(CreateUserRequest req, User caller) {
        Tenant tenant = tenantRepository.findById(TenantContext.requireTenantId())
                .orElseThrow(() -> new ResourceNotFoundException("Tenant not found"));

        // Friendly pre-check. uk_user_email is global, not tenant-scoped, so without this
        // every duplicate-email create hit the FK/unique violation path. The constraint is
        // still the real guarantee — two concurrent creates can both pass this check and
        // land on the DataIntegrityViolationException handler (409).
        //
        // The message stays deliberately vague. Because the constraint is global, echoing
        // "already registered" told any tenant admin whether an address exists somewhere
        // else in the system — a cross-tenant membership oracle over arbitrary addresses.
        // Repeating req.email() back also put the submitted address into error responses
        // and their logs. "Not available" answers the caller without either disclosure, and
        // matches the generic 409 the race path already returns.
        if (userRepository.existsByEmail(req.email())) {
            throw new DuplicateResourceException("That email address is not available");
        }

        User user = userRepository.save(User.builder()
                .tenant(tenant)
                .email(req.email())
                .passwordHash(passwordEncoder.encode(req.password()))
                .firstName(req.firstName())
                .lastName(req.lastName())
                .role(req.role())
                .build());
        // Creating a user honours req.role() verbatim, so this is a privilege-granting
        // operation. Recording a null actor left the log unable to answer "who created
        // this ADMIN account?" — the question an audit log exists for.
        auditService.record(caller.getId(), caller.getEmail(), AuditEventType.USER_CREATED.toString(), "User", user.getId(),
                null, AuditPayload.of("role", user.getRole(), "status", user.getStatus()));
        return userMapper.toResponse(user);
    }

    public UserResponse update(String id, UpdateUserRequest req, User caller) {
        User target = loadInTenant(id);
        boolean isSelf = caller.getId().equals(target.getId());
        boolean isAdmin = isAdmin(caller);

        if (!isSelf && !isAdmin) {
            throw new AccessDeniedException("Access denied");
        }

        if (req.firstName() != null) target.setFirstName(req.firstName());
        if (req.lastName() != null) target.setLastName(req.lastName());

        boolean passwordChanged = req.password() != null;
        if (passwordChanged) {
            // A self-service credential change must prove knowledge of the current password.
            // Without this, a hijacked session (XSS, unattended machine) converts into
            // permanent account takeover. Admins resetting another user's password are
            // exempt — they are already authorised and do not know the target's password.
            if (isSelf) {
                if (req.currentPassword() == null) {
                    throw new InvalidRequestException(
                            "currentPassword is required when changing your own password");
                }
                if (!passwordEncoder.matches(req.currentPassword(), target.getPasswordHash())) {
                    throw new InvalidRequestException("Current password is incorrect");
                }
            }
            target.setPasswordHash(passwordEncoder.encode(req.password()));
        }

        UserRole oldRole = target.getRole();
        UserStatus oldStatus = target.getStatus();
        boolean roleChanged = false;
        boolean statusChanged = false;

        if (isAdmin) {
            // Same self-protection as updateStatus/delete. Without it an admin could
            // demote or deactivate themselves through this endpoint — the exact lockout
            // the neighbouring methods refuse — just by using a different door.
            if (req.role() != null && req.role() != oldRole) {
                if (isSelf) {
                    throw new AccessDeniedException("You cannot change your own role");
                }
                target.setRole(req.role());
                roleChanged = true;
            }
            if (req.status() != null && req.status() != oldStatus) {
                if (isSelf) {
                    throw new AccessDeniedException("You cannot change your own status");
                }
                target.setStatus(req.status());
                statusChanged = true;
            }
        }

        UserResponse result = userMapper.toResponse(userRepository.save(target));

        // update() can grant ADMIN, so it needs the same audit trail as updateStatus.
        // It was previously the only user-lifecycle method writing no record at all.
        if (roleChanged) {
            auditService.record(caller.getId(), caller.getEmail(), AuditEventType.USER_ROLE_CHANGED.toString(),
                    "User", target.getId(), AuditPayload.of("role", oldRole),
                    AuditPayload.of("role", target.getRole()));
        }
        if (statusChanged) {
            if (target.getStatus() == UserStatus.INACTIVE) {
                refreshTokenRepository.revokeAllByUserId(target.getId());
            }
            auditService.record(caller.getId(), caller.getEmail(), AuditEventType.USER_STATUS_CHANGED.toString(),
                    "User", target.getId(), AuditPayload.of("status", oldStatus),
                    AuditPayload.of("status", target.getStatus()));
        }

        if (passwordChanged) {
            // OWASP Session Management: a password change must invalidate existing sessions.
            // Otherwise a user who rotates their password because they suspect compromise
            // leaves the attacker's refresh token usable for the rest of its 7-day life.
            refreshTokenRepository.revokeAllByUserId(target.getId());
            auditService.record(caller.getId(), caller.getEmail(), AuditEventType.USER_PASSWORD_CHANGED.toString(),
                    "User", target.getId(), null, AuditPayload.of("sessionsRevoked", true));
            log.info("Password changed for user [{}] — all refresh tokens revoked",
                    LogSanitizer.sanitize(target.getId()));
        }

        return result;
    }

    public void delete(String id, User caller) {
        User target = loadInTenant(id);
        if (!isAdmin(caller)) {
            throw new AccessDeniedException("Access denied");
        }
        if (caller.getId().equals(target.getId())) {
            throw new AccessDeniedException("You cannot delete your own account");
        }

        // order.created_by, order_status_history.changed_by and stock_movement.created_by
        // all reference user(id) with ON DELETE RESTRICT, so a user who has ever acted in
        // the system cannot be hard-deleted — the FK violation used to surface as a 500.
        // Deliberately keep the restriction rather than cascading: deleting an actor
        // referenced by audit-relevant tables would destroy the "who did this" trail.
        if (hasActivityReferences(id)) {
            throw new ResourceInUseException(
                    "This user has order or inventory activity and cannot be deleted. "
                            + "Deactivate the account instead (set status to INACTIVE).");
        }

        // Clear sessions BEFORE the delete, so a deleted user's refresh token cannot outlive
        // the account. Doing it afterwards never ran: fk_refresh_token_user is RESTRICT, so
        // the user DELETE failed for anyone who had ever logged in and the revocation was
        // unreachable. Deleting the rows rather than revoking them is what unblocks the FK —
        // revoked rows still reference the user — and a deleted row is just as unusable.
        refreshTokenRepository.deleteAllByUserId(id);
        userRepository.delete(target);
        auditService.record(caller.getId(), caller.getEmail(), AuditEventType.USER_DELETED.toString(), "User", id,
                AuditPayload.of("role", target.getRole(), "status", target.getStatus()), null);
        log.info("User [{}] deleted by admin [{}]", LogSanitizer.sanitize(id), LogSanitizer.sanitize(caller.getId()));
    }

    private boolean hasActivityReferences(String userId) {
        return orderRepository.existsByCreatedById(userId)
                || orderStatusHistoryRepository.existsByChangedById(userId)
                || stockMovementRepository.existsByCreatedBy(userId);
    }

    public UserResponse updateStatus(String id, UpdateStatusRequest req, User caller) {
        User target = loadInTenant(id);
        if (!isAdmin(caller)) {
            throw new AccessDeniedException("Access denied");
        }
        if (caller.getId().equals(target.getId())) {
            throw new AccessDeniedException("You cannot change your own status");
        }
        // Capture the previous status BEFORE mutating. Reading target.getStatus() after
        // setStatus() made every USER_STATUS_CHANGED row record oldValue == newValue,
        // which defeated the point of auditing deactivations.
        UserStatus oldStatus = target.getStatus();

        target.setStatus(req.status());
        UserResponse result = userMapper.toResponse(userRepository.save(target));

        // Deactivation must kill existing sessions. refresh() already re-checks
        // UserStatus.ACTIVE, so this is defence in depth — but it also makes the audit
        // trail honest about when the user's sessions actually ended.
        if (req.status() == UserStatus.INACTIVE) {
            refreshTokenRepository.revokeAllByUserId(id);
            log.info("User [{}] deactivated by admin [{}] — all refresh tokens revoked",
                    LogSanitizer.sanitize(id), LogSanitizer.sanitize(caller.getId()));
        }

        // Audit status changes (security-sensitive — can deactivate accounts)
        auditService.record(caller.getId(), caller.getEmail(), "USER_STATUS_CHANGED", "User", id,
                AuditPayload.of("status", oldStatus), AuditPayload.of("status", req.status()));
        return result;
    }

    private User loadInTenant(String id) {
        return userRepository.findByIdAndTenantId(id, TenantContext.requireTenantId())
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + id));
    }

    private boolean isAdmin(User user) {
        return user.getRole() == UserRole.ADMIN;
    }

    private boolean isAdminOrManager(User user) {
        return user.getRole() == UserRole.ADMIN || user.getRole() == UserRole.MANAGER;
    }

    public User getUserEntity(String id) {
        return loadInTenant(id);
    }
}
