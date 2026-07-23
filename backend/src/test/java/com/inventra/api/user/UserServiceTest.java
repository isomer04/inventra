package com.inventra.api.user;

import com.inventra.api.BaseIntegrationTest;
import com.inventra.api.audit.AuditService;
import com.inventra.api.entity.RefreshToken;
import com.inventra.api.entity.Tenant;
import com.inventra.api.entity.User;
import com.inventra.api.entity.UserRole;
import com.inventra.api.entity.UserStatus;
import com.inventra.api.exception.DuplicateResourceException;
import com.inventra.api.exception.InvalidRequestException;
import com.inventra.api.exception.ResourceNotFoundException;
import com.inventra.api.repository.RefreshTokenRepository;
import com.inventra.api.repository.TenantRepository;
import com.inventra.api.repository.UserRepository;
import com.inventra.api.tenant.TenantContext;
import com.inventra.api.user.dto.CreateUserRequest;
import com.inventra.api.user.dto.UpdateStatusRequest;
import com.inventra.api.user.dto.UpdateUserRequest;
import com.inventra.api.user.dto.UserResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;

/**
 * Integration tests for {@link UserService}.
 *
 * <p>Tests comprehensive user management functionality including:
 * <ul>
 *   <li>User listing with tenant isolation</li>
 *   <li>User retrieval with authorization checks</li>
 *   <li>User creation with audit logging</li>
 *   <li>User updates with role-based permissions</li>
 *   <li>User deletion with self-deletion prevention</li>
 *   <li>Status updates with audit trails</li>
 *   <li>Password hashing</li>
 *   <li>Admin vs non-admin access control</li>
 * </ul>
 */
@SpringBootTest
@Sql(scripts = "/test-data/cleanup.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
@DisplayName("UserService")
class UserServiceTest extends BaseIntegrationTest {

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @MockitoSpyBean
    private AuditService auditService;

    private Tenant testTenant;
    private User adminUser;
    private User managerUser;
    private User staffUser;
    private User viewerUser;

    @BeforeEach
    void setUp() {
        testTenant = new Tenant();
        testTenant.setId("tenant-user-test-001");
        testTenant.setName("User Test Tenant");
        testTenant.setSlug("user-test");
        testTenant = tenantRepository.save(testTenant);

        TenantContext.setTenantId(testTenant.getId());

        adminUser = createUser("admin", UserRole.ADMIN, UserStatus.ACTIVE);
        managerUser = createUser("manager", UserRole.MANAGER, UserStatus.ACTIVE);
        staffUser = createUser("staff", UserRole.WAREHOUSE_STAFF, UserStatus.ACTIVE);
        viewerUser = createUser("viewer", UserRole.VIEWER, UserStatus.ACTIVE);
    }

    private User createUser(String prefix, UserRole role, UserStatus status) {
        User user = new User();
        user.setId("user-" + prefix + "-001");
        user.setEmail(prefix + "@example.com");
        user.setPasswordHash(passwordEncoder.encode("password123"));
        user.setFirstName(prefix.substring(0, 1).toUpperCase() + prefix.substring(1));
        user.setLastName("User");
        user.setRole(role);
        user.setStatus(status);
        user.setTenant(testTenant);
        return userRepository.save(user);
    }

    @Nested
    @DisplayName("getAll()")
    class GetAll {

        @Test
        @Transactional
        @DisplayName("returns all users in the current tenant")
        void returnsAllUsersInTenant() {
            List<UserResponse> users = userService.getAll();

            assertThat(users).hasSize(4);
            assertThat(users).extracting(UserResponse::email)
                    .containsExactlyInAnyOrder(
                            "admin@example.com",
                            "manager@example.com",
                            "staff@example.com",
                            "viewer@example.com"
                    );
        }

        @Test
        @Transactional
        @DisplayName("filters by tenant - does not return users from other tenants")
        void filtersByTenant() {
            Tenant otherTenant = new Tenant();
            otherTenant.setId("tenant-other-001");
            otherTenant.setName("Other Tenant");
            otherTenant.setSlug("other");
            otherTenant = tenantRepository.save(otherTenant);

            User otherUser = new User();
            otherUser.setId("user-other-001");
            otherUser.setEmail("other@example.com");
            otherUser.setPasswordHash(passwordEncoder.encode("password"));
            otherUser.setFirstName("Other");
            otherUser.setLastName("User");
            otherUser.setRole(UserRole.ADMIN);
            otherUser.setStatus(UserStatus.ACTIVE);
            otherUser.setTenant(otherTenant);
            userRepository.save(otherUser);

            List<UserResponse> users = userService.getAll();

            assertThat(users).hasSize(4);
            assertThat(users).extracting(UserResponse::email)
                    .doesNotContain("other@example.com");
        }

        @Test
        @Transactional
        @DisplayName("returns empty list when tenant has no users")
        void returnsEmptyListForTenantWithNoUsers() {
            Tenant emptyTenant = new Tenant();
            emptyTenant.setId("tenant-empty-001");
            emptyTenant.setName("Empty Tenant");
            emptyTenant.setSlug("empty");
            emptyTenant = tenantRepository.save(emptyTenant);

            TenantContext.setTenantId(emptyTenant.getId());

            List<UserResponse> users = userService.getAll();

            assertThat(users).isEmpty();
        }
    }

    @Nested
    @DisplayName("getById()")
    class GetById {

        @Test
        @Transactional
        @DisplayName("admin can retrieve any user")
        void adminCanRetrieveAnyUser() {
            UserResponse response = userService.getById(staffUser.getId(), adminUser);

            assertThat(response.id()).isEqualTo(staffUser.getId());
            assertThat(response.email()).isEqualTo("staff@example.com");
        }

        @Test
        @Transactional
        @DisplayName("manager can retrieve any user")
        void managerCanRetrieveAnyUser() {
            UserResponse response = userService.getById(viewerUser.getId(), managerUser);

            assertThat(response.id()).isEqualTo(viewerUser.getId());
            assertThat(response.email()).isEqualTo("viewer@example.com");
        }

        @Test
        @Transactional
        @DisplayName("user can retrieve their own profile")
        void userCanRetrieveOwnProfile() {
            UserResponse response = userService.getById(staffUser.getId(), staffUser);

            assertThat(response.id()).isEqualTo(staffUser.getId());
            assertThat(response.email()).isEqualTo("staff@example.com");
        }

        @Test
        @Transactional
        @DisplayName("non-admin cannot retrieve other users")
        void nonAdminCannotRetrieveOtherUsers() {
            assertThatThrownBy(() -> userService.getById(adminUser.getId(), staffUser))
                    .isInstanceOf(AccessDeniedException.class)
                    .hasMessage("Access denied");
        }

        @Test
        @Transactional
        @DisplayName("throws ResourceNotFoundException when user not found")
        void throwsExceptionWhenUserNotFound() {
            assertThatThrownBy(() -> userService.getById("non-existent-id", adminUser))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("User not found");
        }

        @Test
        @Transactional
        @DisplayName("throws ResourceNotFoundException when user is in different tenant")
        void throwsExceptionWhenUserInDifferentTenant() {
            Tenant otherTenant = new Tenant();
            otherTenant.setId("tenant-other-002");
            otherTenant.setName("Other Tenant");
            otherTenant.setSlug("other");
            otherTenant = tenantRepository.save(otherTenant);

            User otherUser = new User();
            otherUser.setId("user-other-002");
            otherUser.setEmail("other@example.com");
            otherUser.setPasswordHash(passwordEncoder.encode("password"));
            otherUser.setFirstName("Other");
            otherUser.setLastName("User");
            otherUser.setRole(UserRole.ADMIN);
            otherUser.setStatus(UserStatus.ACTIVE);
            otherUser.setTenant(otherTenant);
            userRepository.save(otherUser);

            assertThatThrownBy(() -> userService.getById(otherUser.getId(), adminUser))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("create()")
    class Create {

        @Test
        @Transactional
        @DisplayName("creates user with hashed password")
        void createsUserWithHashedPassword() {
            CreateUserRequest request = new CreateUserRequest(
                    "newuser@example.com",
                    "password123",
                    "New",
                    "User",
                    UserRole.WAREHOUSE_STAFF
            );

            UserResponse response = userService.create(request, adminUser);

            assertThat(response.email()).isEqualTo("newuser@example.com");
            assertThat(response.firstName()).isEqualTo("New");
            assertThat(response.lastName()).isEqualTo("User");
            assertThat(response.role()).isEqualTo(UserRole.WAREHOUSE_STAFF);
            assertThat(response.status()).isEqualTo(UserStatus.ACTIVE);

            User saved = userRepository.findById(response.id()).orElseThrow();
            assertThat(saved.getPassword()).isNotEqualTo("password123");
            assertThat(passwordEncoder.matches("password123", saved.getPassword())).isTrue();
        }

        @Test
        @Transactional
        @DisplayName("associates user with current tenant")
        void associatesUserWithCurrentTenant() {
            CreateUserRequest request = new CreateUserRequest(
                    "newuser@example.com",
                    "password123",
                    "New",
                    "User",
                    UserRole.VIEWER
            );

            UserResponse response = userService.create(request, adminUser);

            User saved = userRepository.findById(response.id()).orElseThrow();
            assertThat(saved.getTenant().getId()).isEqualTo(testTenant.getId());
        }

        @Test
        @Transactional
        @DisplayName("rejects an email that is already registered")
        void rejectsDuplicateEmail() {
            // uk_user_email is global, so this used to reach the DB constraint and surface
            // as a 500 on every attempt — not just under concurrency.
            CreateUserRequest request = new CreateUserRequest(
                    staffUser.getEmail(),
                    "password123",
                    "Duplicate",
                    "User",
                    UserRole.VIEWER
            );

            // The message deliberately does not confirm that the address is registered,
            // and does not echo the submitted address back into the response or its logs.
            assertThatThrownBy(() -> userService.create(request, adminUser))
                    .isInstanceOf(DuplicateResourceException.class)
                    .hasMessageContaining("not available")
                    .hasMessageNotContaining(staffUser.getEmail());
        }

        @Test
        @Transactional
        @DisplayName("generates audit log for user creation")
        void generatesAuditLogForCreation() {
            CreateUserRequest request = new CreateUserRequest(
                    "newuser@example.com",
                    "password123",
                    "New",
                    "User",
                    UserRole.MANAGER
            );

            UserResponse response = userService.create(request, adminUser);

            ArgumentCaptor<String> eventTypeCaptor = ArgumentCaptor.forClass(String.class);
            ArgumentCaptor<String> entityTypeCaptor = ArgumentCaptor.forClass(String.class);
            ArgumentCaptor<String> entityIdCaptor = ArgumentCaptor.forClass(String.class);

            // The actor is the admin who created the account. It used to be recorded as
            // null, which left USER_CREATED — a privilege-granting event — unattributable.
            verify(auditService).record(
                    eq(adminUser.getId()),
                    eq(adminUser.getEmail()),
                    eventTypeCaptor.capture(),
                    entityTypeCaptor.capture(),
                    entityIdCaptor.capture(),
                    isNull(),
                    anyString()
            );

            assertThat(eventTypeCaptor.getValue()).isEqualTo("USER_CREATED");
            assertThat(entityTypeCaptor.getValue()).isEqualTo("User");
            assertThat(entityIdCaptor.getValue()).isEqualTo(response.id());
        }

        @Test
        @Transactional
        @DisplayName("sets default status to ACTIVE")
        void setsDefaultStatusToActive() {
            CreateUserRequest request = new CreateUserRequest(
                    "newuser@example.com",
                    "password123",
                    "New",
                    "User",
                    UserRole.WAREHOUSE_STAFF
            );

            UserResponse response = userService.create(request, adminUser);

            assertThat(response.status()).isEqualTo(UserStatus.ACTIVE);
        }

        @Test
        @Transactional
        @DisplayName("throws ResourceNotFoundException when tenant not found")
        void throwsExceptionWhenTenantNotFound() {
            TenantContext.setTenantId("non-existent-tenant");

            CreateUserRequest request = new CreateUserRequest(
                    "newuser@example.com",
                    "password123",
                    "New",
                    "User",
                    UserRole.WAREHOUSE_STAFF
            );

            assertThatThrownBy(() -> userService.create(request, adminUser))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessage("Tenant not found");
        }
    }

    @Nested
    @DisplayName("update()")
    class Update {

        @Test
        @Transactional
        @DisplayName("user can update their own profile (name and password)")
        void userCanUpdateOwnProfile() {
            UpdateUserRequest request = new UpdateUserRequest(
                    "UpdatedFirst",
                    "UpdatedLast",
                    "newPassword456",
                    "password123", // current password — required for a self-service change
                    null, // role cannot be changed by self
                    null  // status cannot be changed by self
            );

            UserResponse response = userService.update(staffUser.getId(), request, staffUser);

            assertThat(response.firstName()).isEqualTo("UpdatedFirst");
            assertThat(response.lastName()).isEqualTo("UpdatedLast");

            User updated = userRepository.findById(staffUser.getId()).orElseThrow();
            assertThat(passwordEncoder.matches("newPassword456", updated.getPassword())).isTrue();
        }

        @Test
        @Transactional
        @DisplayName("self password change is rejected without the current password")
        void selfPasswordChangeRequiresCurrentPassword() {
            UpdateUserRequest request = new UpdateUserRequest(
                    null, null, "newPassword456", null, null, null);

            assertThatThrownBy(() -> userService.update(staffUser.getId(), request, staffUser))
                    .isInstanceOf(InvalidRequestException.class)
                    .hasMessageContaining("currentPassword is required");

            User unchanged = userRepository.findById(staffUser.getId()).orElseThrow();
            assertThat(passwordEncoder.matches("newPassword456", unchanged.getPassword())).isFalse();
        }

        @Test
        @Transactional
        @DisplayName("self password change is rejected when the current password is wrong")
        void selfPasswordChangeRejectsWrongCurrentPassword() {
            UpdateUserRequest request = new UpdateUserRequest(
                    null, null, "newPassword456", "notMyPassword", null, null);

            assertThatThrownBy(() -> userService.update(staffUser.getId(), request, staffUser))
                    .isInstanceOf(InvalidRequestException.class)
                    .hasMessageContaining("Current password is incorrect");

            User unchanged = userRepository.findById(staffUser.getId()).orElseThrow();
            assertThat(passwordEncoder.matches("newPassword456", unchanged.getPassword())).isFalse();
        }

        @Test
        @Transactional
        @DisplayName("admin can reset another user's password without knowing it")
        void adminCanResetPasswordWithoutCurrentPassword() {
            UpdateUserRequest request = new UpdateUserRequest(
                    null, null, "adminSetPassword1", null, null, null);

            userService.update(staffUser.getId(), request, adminUser);

            User updated = userRepository.findById(staffUser.getId()).orElseThrow();
            assertThat(passwordEncoder.matches("adminSetPassword1", updated.getPassword())).isTrue();
        }

        @Test
        @Transactional
        @DisplayName("changing a password revokes that user's refresh tokens")
        void passwordChangeRevokesRefreshTokens() {
            // OWASP Session Management: rotating a password because you suspect compromise
            // is pointless if the attacker's existing refresh token keeps working.
            RefreshToken token = refreshTokenRepository.save(RefreshToken.builder()
                    .user(staffUser)
                    .tenant(staffUser.getTenant())
                    .tokenHash("hash-for-password-change-test")
                    .expiresAt(Instant.now().plusSeconds(3600))
                    .revoked(false)
                    .build());

            UpdateUserRequest request = new UpdateUserRequest(
                    null, null, "brandNewPassword1", "password123", null, null);
            userService.update(staffUser.getId(), request, staffUser);

            assertThat(refreshTokenRepository.findById(token.getId()).orElseThrow().isRevoked())
                    .isTrue();
        }

        @Test
        @Transactional
        @DisplayName("user cannot change their own role")
        void userCannotChangeOwnRole() {
            UpdateUserRequest request = new UpdateUserRequest(
                    null,
                    null,
                    null,
                    null,
                    UserRole.ADMIN,
                    null
            );

            userService.update(staffUser.getId(), request, staffUser);

            User updated = userRepository.findById(staffUser.getId()).orElseThrow();
            assertThat(updated.getRole()).isEqualTo(UserRole.WAREHOUSE_STAFF);
        }

        @Test
        @Transactional
        @DisplayName("user cannot change their own status")
        void userCannotChangeOwnStatus() {
            UpdateUserRequest request = new UpdateUserRequest(
                    null,
                    null,
                    null,
                    null,
                    null,
                    UserStatus.INACTIVE
            );

            userService.update(staffUser.getId(), request, staffUser);

            User updated = userRepository.findById(staffUser.getId()).orElseThrow();
            assertThat(updated.getStatus()).isEqualTo(UserStatus.ACTIVE);
        }

        @Test
        @Transactional
        @DisplayName("admin can update any user's profile, role, and status")
        void adminCanUpdateAnyUser() {
            UpdateUserRequest request = new UpdateUserRequest(
                    "AdminUpdated",
                    "LastName",
                    null,
                    null,
                    UserRole.MANAGER,
                    UserStatus.INACTIVE
            );

            UserResponse response = userService.update(staffUser.getId(), request, adminUser);

            assertThat(response.firstName()).isEqualTo("AdminUpdated");
            assertThat(response.role()).isEqualTo(UserRole.MANAGER);
            assertThat(response.status()).isEqualTo(UserStatus.INACTIVE);
        }

        @Test
        @Transactional
        @DisplayName("non-admin cannot update other users")
        void nonAdminCannotUpdateOtherUsers() {
            UpdateUserRequest request = new UpdateUserRequest(
                    "Hacker",
                    "Attempt",
                    null,
                    null,
                    null,
                    null
            );

            assertThatThrownBy(() -> userService.update(adminUser.getId(), request, staffUser))
                    .isInstanceOf(AccessDeniedException.class)
                    .hasMessage("Access denied");
        }

        @Test
        @Transactional
        @DisplayName("throws ResourceNotFoundException when user not found")
        void throwsExceptionWhenUserNotFound() {
            UpdateUserRequest request = new UpdateUserRequest(
                    "Test",
                    "User",
                    null,
                    null,
                    null,
                    null
            );

            assertThatThrownBy(() -> userService.update("non-existent-id", request, adminUser))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("delete()")
    class Delete {

        @Test
        @Transactional
        @DisplayName("admin can delete other users")
        void adminCanDeleteOtherUsers() {
            userService.delete(staffUser.getId(), adminUser);

            assertThat(userRepository.findById(staffUser.getId())).isEmpty();
        }

        @Test
        @Transactional
        @DisplayName("deleting a user removes their refresh tokens")
        void deleteRemovesRefreshTokens() {
            // fk_refresh_token_user is RESTRICT, so the token rows must be deleted rather
            // than revoked — otherwise deleting anyone who had ever logged in would fail.
            RefreshToken token = refreshTokenRepository.save(RefreshToken.builder()
                    .user(staffUser)
                    .tenant(staffUser.getTenant())
                    .tokenHash("hash-for-delete-test")
                    .expiresAt(Instant.now().plusSeconds(3600))
                    .revoked(false)
                    .build());

            userService.delete(staffUser.getId(), adminUser);

            assertThat(refreshTokenRepository.findById(token.getId())).isEmpty();
            assertThat(userRepository.findById(staffUser.getId())).isEmpty();
        }

        @Test
        @Transactional
        @DisplayName("admin cannot delete their own account")
        void adminCannotDeleteOwnAccount() {
            assertThatThrownBy(() -> userService.delete(adminUser.getId(), adminUser))
                    .isInstanceOf(AccessDeniedException.class)
                    .hasMessage("You cannot delete your own account");
        }

        @Test
        @Transactional
        @DisplayName("non-admin cannot delete users")
        void nonAdminCannotDeleteUsers() {
            assertThatThrownBy(() -> userService.delete(viewerUser.getId(), staffUser))
                    .isInstanceOf(AccessDeniedException.class)
                    .hasMessage("Access denied");
        }

        @Test
        @Transactional
        @DisplayName("generates audit log for deletion")
        void generatesAuditLogForDeletion() {
            userService.delete(staffUser.getId(), adminUser);

            ArgumentCaptor<String> eventTypeCaptor = ArgumentCaptor.forClass(String.class);
            ArgumentCaptor<String> entityIdCaptor = ArgumentCaptor.forClass(String.class);

            verify(auditService).record(
                    eq(adminUser.getId()),
                    eq(adminUser.getEmail()),
                    eventTypeCaptor.capture(),
                    eq("User"),
                    entityIdCaptor.capture(),
                    anyString(),
                    isNull()
            );

            assertThat(eventTypeCaptor.getValue()).isEqualTo("USER_DELETED");
            assertThat(entityIdCaptor.getValue()).isEqualTo(staffUser.getId());
        }

        @Test
        @Transactional
        @DisplayName("throws ResourceNotFoundException when user not found")
        void throwsExceptionWhenUserNotFound() {
            assertThatThrownBy(() -> userService.delete("non-existent-id", adminUser))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("updateStatus()")
    class UpdateStatus {

        @Test
        @Transactional
        @DisplayName("admin can change user status to INACTIVE")
        void adminCanChangeStatusToInactive() {
            UpdateStatusRequest request = new UpdateStatusRequest(UserStatus.INACTIVE);

            UserResponse response = userService.updateStatus(staffUser.getId(), request, adminUser);

            assertThat(response.status()).isEqualTo(UserStatus.INACTIVE);
        }

        @Test
        @Transactional
        @DisplayName("admin can change user status to ACTIVE")
        void adminCanChangeStatusToActive() {
            staffUser.setStatus(UserStatus.INACTIVE);
            userRepository.save(staffUser);

            UpdateStatusRequest request = new UpdateStatusRequest(UserStatus.ACTIVE);

            UserResponse response = userService.updateStatus(staffUser.getId(), request, adminUser);

            assertThat(response.status()).isEqualTo(UserStatus.ACTIVE);
        }

        @Test
        @Transactional
        @DisplayName("deactivating a user revokes their refresh tokens")
        void deactivationRevokesRefreshTokens() {
            RefreshToken token = refreshTokenRepository.save(RefreshToken.builder()
                    .user(staffUser)
                    .tenant(staffUser.getTenant())
                    .tokenHash("hash-for-deactivation-test")
                    .expiresAt(Instant.now().plusSeconds(3600))
                    .revoked(false)
                    .build());

            userService.updateStatus(staffUser.getId(),
                    new UpdateStatusRequest(UserStatus.INACTIVE), adminUser);

            assertThat(refreshTokenRepository.findById(token.getId()).orElseThrow().isRevoked())
                    .isTrue();
        }

        @Test
        @Transactional
        @DisplayName("status-change audit records the previous status, not the new one")
        void auditRecordsPreviousStatus() {
            // Regression guard: the old value was read after setStatus(), so every
            // USER_STATUS_CHANGED row recorded oldValue == newValue.
            ArgumentCaptor<String> oldValueCaptor = ArgumentCaptor.forClass(String.class);
            ArgumentCaptor<String> newValueCaptor = ArgumentCaptor.forClass(String.class);

            userService.updateStatus(staffUser.getId(),
                    new UpdateStatusRequest(UserStatus.INACTIVE), adminUser);

            verify(auditService).record(
                    eq(adminUser.getId()),
                    eq(adminUser.getEmail()),
                    eq("USER_STATUS_CHANGED"),
                    eq("User"),
                    eq(staffUser.getId()),
                    oldValueCaptor.capture(),
                    newValueCaptor.capture()
            );

            assertThat(oldValueCaptor.getValue()).contains("ACTIVE").doesNotContain("INACTIVE");
            assertThat(newValueCaptor.getValue()).contains("INACTIVE");
        }

        @Test
        @Transactional
        @DisplayName("admin cannot change their own status")
        void adminCannotChangeOwnStatus() {
            UpdateStatusRequest request = new UpdateStatusRequest(UserStatus.INACTIVE);

            assertThatThrownBy(() -> userService.updateStatus(adminUser.getId(), request, adminUser))
                    .isInstanceOf(AccessDeniedException.class)
                    .hasMessage("You cannot change your own status");
        }

        @Test
        @Transactional
        @DisplayName("non-admin cannot change user status")
        void nonAdminCannotChangeUserStatus() {
            UpdateStatusRequest request = new UpdateStatusRequest(UserStatus.INACTIVE);

            assertThatThrownBy(() -> userService.updateStatus(viewerUser.getId(), request, staffUser))
                    .isInstanceOf(AccessDeniedException.class)
                    .hasMessage("Access denied");
        }

        @Test
        @Transactional
        @DisplayName("generates audit log for status change")
        void generatesAuditLogForStatusChange() {
            UpdateStatusRequest request = new UpdateStatusRequest(UserStatus.INACTIVE);

            userService.updateStatus(staffUser.getId(), request, adminUser);

            ArgumentCaptor<String> eventTypeCaptor = ArgumentCaptor.forClass(String.class);

            verify(auditService).record(
                    eq(adminUser.getId()),
                    eq(adminUser.getEmail()),
                    eventTypeCaptor.capture(),
                    eq("User"),
                    eq(staffUser.getId()),
                    anyString(),
                    anyString()
            );

            assertThat(eventTypeCaptor.getValue()).isEqualTo("USER_STATUS_CHANGED");
        }

        @Test
        @Transactional
        @DisplayName("throws ResourceNotFoundException when user not found")
        void throwsExceptionWhenUserNotFound() {
            UpdateStatusRequest request = new UpdateStatusRequest(UserStatus.INACTIVE);

            assertThatThrownBy(() -> userService.updateStatus("non-existent-id", request, adminUser))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("getUserEntity()")
    class GetUserEntity {

        @Test
        @Transactional
        @DisplayName("returns user entity for internal service use")
        void returnsUserEntityForInternalUse() {
            User user = userService.getUserEntity(staffUser.getId());

            assertThat(user).isNotNull();
            assertThat(user.getId()).isEqualTo(staffUser.getId());
            assertThat(user.getEmail()).isEqualTo("staff@example.com");
        }

        @Test
        @Transactional
        @DisplayName("throws ResourceNotFoundException when user not found")
        void throwsExceptionWhenUserNotFound() {
            assertThatThrownBy(() -> userService.getUserEntity("non-existent-id"))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @Transactional
        @DisplayName("enforces tenant isolation")
        void enforcesTenantIsolation() {
            Tenant otherTenant = new Tenant();
            otherTenant.setId("tenant-other-003");
            otherTenant.setName("Other Tenant");
            otherTenant.setSlug("other");
            otherTenant = tenantRepository.save(otherTenant);

            User otherUser = new User();
            otherUser.setId("user-other-003");
            otherUser.setEmail("other@example.com");
            otherUser.setPasswordHash(passwordEncoder.encode("password"));
            otherUser.setFirstName("Other");
            otherUser.setLastName("User");
            otherUser.setRole(UserRole.ADMIN);
            otherUser.setStatus(UserStatus.ACTIVE);
            otherUser.setTenant(otherTenant);
            userRepository.save(otherUser);

            assertThatThrownBy(() -> userService.getUserEntity(otherUser.getId()))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("role hierarchy")
    class RoleHierarchy {

        @Test
        @Transactional
        @DisplayName("ADMIN has full access")
        void adminHasFullAccess() {
            assertThat(userService.getById(staffUser.getId(), adminUser)).isNotNull();
            
            UpdateUserRequest update = new UpdateUserRequest("New", "Name", null, null, null, null);
            assertThat(userService.update(staffUser.getId(), update, adminUser)).isNotNull();
            
        }

        @Test
        @Transactional
        @DisplayName("MANAGER can view but not modify")
        void managerCanViewButNotModify() {
            assertThat(userService.getById(staffUser.getId(), managerUser)).isNotNull();
            
            UpdateUserRequest update = new UpdateUserRequest("New", "Name", null, null, null, null);
            assertThatThrownBy(() -> userService.update(staffUser.getId(), update, managerUser))
                    .isInstanceOf(AccessDeniedException.class);
        }

        @Test
        @Transactional
        @DisplayName("STAFF can only access own profile")
        void staffCanOnlyAccessOwnProfile() {
            assertThat(userService.getById(staffUser.getId(), staffUser)).isNotNull();
            
            assertThatThrownBy(() -> userService.getById(adminUser.getId(), staffUser))
                    .isInstanceOf(AccessDeniedException.class);
        }

        @Test
        @Transactional
        @DisplayName("VIEWER has same restrictions as STAFF")
        void viewerHasSameRestrictionsAsStaff() {
            assertThat(userService.getById(viewerUser.getId(), viewerUser)).isNotNull();
            
            assertThatThrownBy(() -> userService.getById(adminUser.getId(), viewerUser))
                    .isInstanceOf(AccessDeniedException.class);
        }
    }
}
