package com.inventra.api.auth;

import com.inventra.api.audit.AuditService;
import com.inventra.api.auth.dto.LoginRequest;
import com.inventra.api.auth.dto.RegisterRequest;
import com.inventra.api.auth.dto.TokenResponse;
import com.inventra.api.entity.*;
import com.inventra.api.exception.DuplicateResourceException;
import com.inventra.api.exception.ResourceNotFoundException;
import com.inventra.api.repository.RefreshTokenRepository;
import com.inventra.api.repository.TenantRepository;
import com.inventra.api.repository.UserRepository;
import com.inventra.api.security.JwtService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private TenantRepository tenantRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private JwtService jwtService;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private AuditService auditService;

    @InjectMocks
    private AuthService authService;

    private static final String EMAIL = "user@test.com";
    private static final String PASSWORD = "password123";
    private static final String PASSWORD_HASH = "$2a$10$hashedpassword";
    private static final String TENANT_SLUG = "test-tenant";
    private static final String ACCESS_TOKEN = "access.token.jwt";
    private static final String REFRESH_TOKEN = "refresh-token-raw";
    private static final String REFRESH_TOKEN_HASH = "hashed-refresh-token";
    private static final long ACCESS_TOKEN_EXPIRY_MS = 900000L; // 15 minutes
    private static final long REFRESH_TOKEN_EXPIRY_MS = 604800000L; // 7 days

    @Test
    void register_whenValidRequest_thenCreatesTenantAndUserAndReturnsTokens() {
        RegisterRequest request = new RegisterRequest(
                "Test Tenant",
                TENANT_SLUG,
                EMAIL,
                PASSWORD,
                "John",
                "Doe"
        );

        Tenant savedTenant = Tenant.builder()
                .id("tenant-123")
                .name("Test Tenant")
                .slug(TENANT_SLUG)
                .status(TenantStatus.ACTIVE)
                .build();

        User savedUser = User.builder()
                .id("user-123")
                .tenant(savedTenant)
                .email(EMAIL)
                .passwordHash(PASSWORD_HASH)
                .firstName("John")
                .lastName("Doe")
                .role(UserRole.ADMIN)
                .status(UserStatus.ACTIVE)
                .build();

        when(tenantRepository.existsBySlug(TENANT_SLUG)).thenReturn(false);
        when(tenantRepository.save(any(Tenant.class))).thenReturn(savedTenant);
        when(passwordEncoder.encode(PASSWORD)).thenReturn(PASSWORD_HASH);
        when(userRepository.save(any(User.class))).thenReturn(savedUser);
        when(jwtService.generateAccessToken(savedUser)).thenReturn(ACCESS_TOKEN);
        when(jwtService.generateRawRefreshToken()).thenReturn(REFRESH_TOKEN);
        when(jwtService.hashToken(REFRESH_TOKEN)).thenReturn(REFRESH_TOKEN_HASH);
        when(jwtService.accessTokenExpiryMs()).thenReturn(ACCESS_TOKEN_EXPIRY_MS);
        when(jwtService.refreshTokenExpiryMs()).thenReturn(REFRESH_TOKEN_EXPIRY_MS);

        TokenResponse result = authService.register(request);

        assertThat(result).isNotNull();
        assertThat(result.accessToken()).isEqualTo(ACCESS_TOKEN);
        assertThat(result.refreshToken()).isEqualTo(REFRESH_TOKEN);
        assertThat(result.tokenType()).isEqualTo("Bearer");
        assertThat(result.expiresIn()).isEqualTo(ACCESS_TOKEN_EXPIRY_MS / 1000);

        ArgumentCaptor<Tenant> tenantCaptor = ArgumentCaptor.forClass(Tenant.class);
        verify(tenantRepository).save(tenantCaptor.capture());
        Tenant capturedTenant = tenantCaptor.getValue();
        assertThat(capturedTenant.getName()).isEqualTo("Test Tenant");
        assertThat(capturedTenant.getSlug()).isEqualTo(TENANT_SLUG);

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        User capturedUser = userCaptor.getValue();
        assertThat(capturedUser.getEmail()).isEqualTo(EMAIL);
        assertThat(capturedUser.getPasswordHash()).isEqualTo(PASSWORD_HASH);
        assertThat(capturedUser.getFirstName()).isEqualTo("John");
        assertThat(capturedUser.getLastName()).isEqualTo("Doe");
        assertThat(capturedUser.getRole()).isEqualTo(UserRole.ADMIN);

        ArgumentCaptor<RefreshToken> tokenCaptor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository).save(tokenCaptor.capture());
        RefreshToken capturedToken = tokenCaptor.getValue();
        assertThat(capturedToken.getTokenHash()).isEqualTo(REFRESH_TOKEN_HASH);
        assertThat(capturedToken.isRevoked()).isFalse();

        verify(auditService).record(eq("user-123"), isNull(), eq("TENANT_REGISTERED"), eq("Tenant"), eq("tenant-123"), isNull(), anyString());
    }

    @Test
    void register_whenSlugAlreadyExists_thenThrowsDuplicateResourceException() {
        RegisterRequest request = new RegisterRequest(
                "Test Tenant",
                TENANT_SLUG,
                EMAIL,
                PASSWORD,
                "John",
                "Doe"
        );

        when(tenantRepository.existsBySlug(TENANT_SLUG)).thenReturn(true);

        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("Slug already taken");

        verify(tenantRepository, never()).save(any());
        verify(userRepository, never()).save(any());
    }

    @Test
    void register_whenPasswordIsEncoded_thenUsesEncodedPassword() {
        RegisterRequest request = new RegisterRequest(
                "Test Tenant",
                TENANT_SLUG,
                EMAIL,
                "plaintext-password",
                "John",
                "Doe"
        );

        Tenant savedTenant = Tenant.builder()
                .id("tenant-123")
                .name("Test Tenant")
                .slug(TENANT_SLUG)
                .build();

        User savedUser = User.builder()
                .id("user-123")
                .tenant(savedTenant)
                .email(EMAIL)
                .passwordHash("$2a$10$encoded")
                .build();

        when(tenantRepository.existsBySlug(TENANT_SLUG)).thenReturn(false);
        when(tenantRepository.save(any(Tenant.class))).thenReturn(savedTenant);
        when(passwordEncoder.encode("plaintext-password")).thenReturn("$2a$10$encoded");
        when(userRepository.save(any(User.class))).thenReturn(savedUser);
        when(jwtService.generateAccessToken(any())).thenReturn(ACCESS_TOKEN);
        when(jwtService.generateRawRefreshToken()).thenReturn(REFRESH_TOKEN);
        when(jwtService.hashToken(any())).thenReturn(REFRESH_TOKEN_HASH);
        when(jwtService.accessTokenExpiryMs()).thenReturn(ACCESS_TOKEN_EXPIRY_MS);
        when(jwtService.refreshTokenExpiryMs()).thenReturn(REFRESH_TOKEN_EXPIRY_MS);

        authService.register(request);

        verify(passwordEncoder).encode("plaintext-password");
        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        assertThat(userCaptor.getValue().getPasswordHash()).isEqualTo("$2a$10$encoded");
    }

    @Test
    void login_whenValidCredentials_thenReturnsTokens() {
        LoginRequest request = new LoginRequest(EMAIL, PASSWORD);

        Tenant tenant = Tenant.builder()
                .id("tenant-123")
                .status(TenantStatus.ACTIVE)
                .build();

        User user = User.builder()
                .id("user-123")
                .tenant(tenant)
                .email(EMAIL)
                .passwordHash(PASSWORD_HASH)
                .status(UserStatus.ACTIVE)
                .build();

        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(PASSWORD, PASSWORD_HASH)).thenReturn(true);
        when(jwtService.generateAccessToken(user)).thenReturn(ACCESS_TOKEN);
        when(jwtService.generateRawRefreshToken()).thenReturn(REFRESH_TOKEN);
        when(jwtService.hashToken(REFRESH_TOKEN)).thenReturn(REFRESH_TOKEN_HASH);
        when(jwtService.accessTokenExpiryMs()).thenReturn(ACCESS_TOKEN_EXPIRY_MS);
        when(jwtService.refreshTokenExpiryMs()).thenReturn(REFRESH_TOKEN_EXPIRY_MS);

        TokenResponse result = authService.login(request);

        assertThat(result).isNotNull();
        assertThat(result.accessToken()).isEqualTo(ACCESS_TOKEN);
        assertThat(result.refreshToken()).isEqualTo(REFRESH_TOKEN);
        assertThat(result.tokenType()).isEqualTo("Bearer");

        verify(refreshTokenRepository).save(any(RefreshToken.class));
    }

    @Test
    void login_whenUserNotFound_thenThrowsBadCredentialsException() {
        LoginRequest request = new LoginRequest(EMAIL, PASSWORD);

        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessageContaining("Invalid credentials");

        verify(passwordEncoder, never()).matches(anyString(), anyString());
        verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    void login_whenPasswordIncorrect_thenThrowsBadCredentialsException() {
        LoginRequest request = new LoginRequest(EMAIL, "wrong-password");

        Tenant tenant = Tenant.builder()
                .id("tenant-123")
                .status(TenantStatus.ACTIVE)
                .build();

        User user = User.builder()
                .id("user-123")
                .tenant(tenant)
                .email(EMAIL)
                .passwordHash(PASSWORD_HASH)
                .status(UserStatus.ACTIVE)
                .build();

        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong-password", PASSWORD_HASH)).thenReturn(false);

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessageContaining("Invalid credentials");

        verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    void login_whenUserIsInactive_thenThrowsBadCredentialsException() {
        LoginRequest request = new LoginRequest(EMAIL, PASSWORD);

        Tenant tenant = Tenant.builder()
                .id("tenant-123")
                .status(TenantStatus.ACTIVE)
                .build();

        User user = User.builder()
                .id("user-123")
                .tenant(tenant)
                .email(EMAIL)
                .passwordHash(PASSWORD_HASH)
                .status(UserStatus.INACTIVE)
                .build();

        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(PASSWORD, PASSWORD_HASH)).thenReturn(true);

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessageContaining("Account is inactive");

        verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    void login_whenTenantIsSuspended_thenThrowsBadCredentialsException() {
        LoginRequest request = new LoginRequest(EMAIL, PASSWORD);

        Tenant tenant = Tenant.builder()
                .id("tenant-123")
                .status(TenantStatus.SUSPENDED)
                .build();

        User user = User.builder()
                .id("user-123")
                .tenant(tenant)
                .email(EMAIL)
                .passwordHash(PASSWORD_HASH)
                .status(UserStatus.ACTIVE)
                .build();

        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(PASSWORD, PASSWORD_HASH)).thenReturn(true);

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessageContaining("Tenant account is suspended");

        verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    void refresh_whenValidToken_thenReturnsNewTokenPair() {
        Instant now = Instant.now();

        Tenant tenant = Tenant.builder()
                .id("tenant-123")
                .status(TenantStatus.ACTIVE)
                .build();

        User user = User.builder()
                .id("user-123")
                .tenant(tenant)
                .email(EMAIL)
                .status(UserStatus.ACTIVE)
                .build();

        RefreshToken storedToken = RefreshToken.builder()
                .id("token-123")
                .user(user)
                .tenant(tenant)
                .tokenHash(REFRESH_TOKEN_HASH)
                .expiresAt(now.plusSeconds(3600))
                .revoked(false)
                .build();

        when(jwtService.hashToken(REFRESH_TOKEN)).thenReturn(REFRESH_TOKEN_HASH);
        when(refreshTokenRepository.findActiveByTokenHash(eq(REFRESH_TOKEN_HASH), any(Instant.class)))
                .thenReturn(Optional.of(storedToken));
        when(refreshTokenRepository.consumeIfActive(eq(REFRESH_TOKEN_HASH), any(Instant.class)))
                .thenReturn(1);
        when(jwtService.generateAccessToken(user)).thenReturn(ACCESS_TOKEN);
        when(jwtService.generateRawRefreshToken()).thenReturn("new-refresh-token");
        when(jwtService.hashToken("new-refresh-token")).thenReturn("new-hash");
        when(jwtService.accessTokenExpiryMs()).thenReturn(ACCESS_TOKEN_EXPIRY_MS);
        when(jwtService.refreshTokenExpiryMs()).thenReturn(REFRESH_TOKEN_EXPIRY_MS);

        TokenResponse result = authService.refresh(REFRESH_TOKEN);

        assertThat(result).isNotNull();
        assertThat(result.accessToken()).isEqualTo(ACCESS_TOKEN);
        assertThat(result.refreshToken()).isEqualTo("new-refresh-token");

        verify(refreshTokenRepository).consumeIfActive(eq(REFRESH_TOKEN_HASH), any(Instant.class));
        verify(refreshTokenRepository).save(any(RefreshToken.class));
    }

    @Test
    void refresh_whenTokenNotFound_thenThrowsBadCredentialsException() {
        when(jwtService.hashToken(REFRESH_TOKEN)).thenReturn(REFRESH_TOKEN_HASH);
        when(refreshTokenRepository.findActiveByTokenHash(eq(REFRESH_TOKEN_HASH), any(Instant.class)))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.refresh(REFRESH_TOKEN))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessageContaining("Refresh token is invalid, expired, or already used");

        verify(refreshTokenRepository, never()).consumeIfActive(anyString(), any());
    }

    @Test
    void refresh_whenTokenAlreadyConsumed_thenThrowsBadCredentialsException() {
        Tenant tenant = Tenant.builder()
                .id("tenant-123")
                .status(TenantStatus.ACTIVE)
                .build();

        User user = User.builder()
                .id("user-123")
                .tenant(tenant)
                .status(UserStatus.ACTIVE)
                .build();

        RefreshToken storedToken = RefreshToken.builder()
                .id("token-123")
                .user(user)
                .tenant(tenant)
                .tokenHash(REFRESH_TOKEN_HASH)
                .revoked(false)
                .build();

        when(jwtService.hashToken(REFRESH_TOKEN)).thenReturn(REFRESH_TOKEN_HASH);
        when(refreshTokenRepository.findActiveByTokenHash(eq(REFRESH_TOKEN_HASH), any(Instant.class)))
                .thenReturn(Optional.of(storedToken));
        when(refreshTokenRepository.consumeIfActive(eq(REFRESH_TOKEN_HASH), any(Instant.class)))
                .thenReturn(0); // Token was consumed by concurrent request

        assertThatThrownBy(() -> authService.refresh(REFRESH_TOKEN))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessageContaining("Refresh token is invalid, expired, or already used");

        verify(refreshTokenRepository, never()).save(any());
        // Losing the consume race means someone else presented this same token: two holders
        // of one token is reuse. Rejecting only this request would leave the winner's rotated
        // chain alive, so the whole family must go.
        verify(refreshTokenRepository).revokeAllByUserId("user-123");
        verify(auditService).record(eq("user-123"), isNull(), eq("REFRESH_TOKEN_REUSE_DETECTED"),
                eq("User"), eq("user-123"), isNull(), anyString());
    }

    @Test
    void refresh_whenUserIsInactive_thenThrowsBadCredentialsException() {
        Tenant tenant = Tenant.builder()
                .id("tenant-123")
                .status(TenantStatus.ACTIVE)
                .build();

        User user = User.builder()
                .id("user-123")
                .tenant(tenant)
                .status(UserStatus.INACTIVE)
                .build();

        RefreshToken storedToken = RefreshToken.builder()
                .id("token-123")
                .user(user)
                .tenant(tenant)
                .tokenHash(REFRESH_TOKEN_HASH)
                .revoked(false)
                .build();

        when(jwtService.hashToken(REFRESH_TOKEN)).thenReturn(REFRESH_TOKEN_HASH);
        when(refreshTokenRepository.findActiveByTokenHash(eq(REFRESH_TOKEN_HASH), any(Instant.class)))
                .thenReturn(Optional.of(storedToken));
        when(refreshTokenRepository.consumeIfActive(eq(REFRESH_TOKEN_HASH), any(Instant.class)))
                .thenReturn(1);

        assertThatThrownBy(() -> authService.refresh(REFRESH_TOKEN))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessageContaining("Account is inactive");

        verify(jwtService, never()).generateAccessToken(any());
    }

    @Test
    void refresh_whenTenantIsSuspended_thenThrowsBadCredentialsException() {
        Tenant tenant = Tenant.builder()
                .id("tenant-123")
                .status(TenantStatus.SUSPENDED)
                .build();

        User user = User.builder()
                .id("user-123")
                .tenant(tenant)
                .status(UserStatus.ACTIVE)
                .build();

        RefreshToken storedToken = RefreshToken.builder()
                .id("token-123")
                .user(user)
                .tenant(tenant)
                .tokenHash(REFRESH_TOKEN_HASH)
                .revoked(false)
                .build();

        when(jwtService.hashToken(REFRESH_TOKEN)).thenReturn(REFRESH_TOKEN_HASH);
        when(refreshTokenRepository.findActiveByTokenHash(eq(REFRESH_TOKEN_HASH), any(Instant.class)))
                .thenReturn(Optional.of(storedToken));
        when(refreshTokenRepository.consumeIfActive(eq(REFRESH_TOKEN_HASH), any(Instant.class)))
                .thenReturn(1);

        assertThatThrownBy(() -> authService.refresh(REFRESH_TOKEN))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessageContaining("Tenant account is suspended");

        verify(jwtService, never()).generateAccessToken(any());
    }

    @Test
    void logout_whenValidToken_thenRevokesToken() {
        RefreshToken storedToken = RefreshToken.builder()
                .id("token-123")
                .tokenHash(REFRESH_TOKEN_HASH)
                .revoked(false)
                .build();

        when(jwtService.hashToken(REFRESH_TOKEN)).thenReturn(REFRESH_TOKEN_HASH);
        when(refreshTokenRepository.findByTokenHash(REFRESH_TOKEN_HASH))
                .thenReturn(Optional.of(storedToken));

        authService.logout(REFRESH_TOKEN);

        assertThat(storedToken.isRevoked()).isTrue();
        verify(refreshTokenRepository).save(storedToken);
    }

    @Test
    void logout_whenTokenNotFound_thenSucceedsSilently() {
        // Logout is idempotent. It previously threw 404 for an unknown token, which turned
        // the endpoint into an existence oracle for token hashes and made the ordinary
        // double-logout case fail. Unknown token == already logged out.
        when(jwtService.hashToken(REFRESH_TOKEN)).thenReturn(REFRESH_TOKEN_HASH);
        when(refreshTokenRepository.findByTokenHash(REFRESH_TOKEN_HASH))
                .thenReturn(Optional.empty());

        assertThatCode(() -> authService.logout(REFRESH_TOKEN)).doesNotThrowAnyException();

        verify(refreshTokenRepository, never()).save(any());
    }
}
