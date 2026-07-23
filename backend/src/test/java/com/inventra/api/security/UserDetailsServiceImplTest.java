package com.inventra.api.security;

import com.inventra.api.entity.Tenant;
import com.inventra.api.entity.User;
import com.inventra.api.entity.UserRole;
import com.inventra.api.entity.UserStatus;
import com.inventra.api.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link UserDetailsServiceImpl}.
 *
 * <p>Tests the Spring Security UserDetailsService implementation that loads
 * users by email for authentication.
 *
 * <p>Coverage areas:
 * <ul>
 *   <li>loadUserByUsername returns user for valid email</li>
 *   <li>UsernameNotFoundException thrown when user not found</li>
 *   <li>Email is case-sensitive</li>
 *   <li>Inactive users still load (Spring Security handles status)</li>
 *   <li>User authorities correctly mapped from role</li>
 *   <li>Multiple roles supported</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("UserDetailsServiceImpl")
class UserDetailsServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private UserDetailsServiceImpl userDetailsService;

    private Tenant testTenant;
    private User testUser;

    @BeforeEach
    void setUp() {
        testTenant = new Tenant();
        testTenant.setId("tenant-test-001");
        testTenant.setName("Test Tenant");
        testTenant.setSlug("test");

        testUser = new User();
        testUser.setId("user-test-001");
        testUser.setEmail("test@example.com");
        testUser.setPasswordHash("$2a$10$hashedPassword");
        testUser.setFirstName("Test");
        testUser.setLastName("User");
        testUser.setRole(UserRole.ADMIN);
        testUser.setStatus(UserStatus.ACTIVE);
        testUser.setTenant(testTenant);
    }

    @Nested
    @DisplayName("loadUserByUsername")
    class LoadUserByUsername {

        @Test
        @DisplayName("returns UserDetails for valid email")
        void returnsUserDetailsForValidEmail() {
            when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(testUser));

            UserDetails userDetails = userDetailsService.loadUserByUsername("test@example.com");

            assertThat(userDetails).isNotNull();
            assertThat(userDetails.getUsername()).isEqualTo("test@example.com");
            assertThat(userDetails.getPassword()).isEqualTo("$2a$10$hashedPassword");
            verify(userRepository).findByEmail("test@example.com");
        }

        @Test
        @DisplayName("throws UsernameNotFoundException when user not found")
        void throwsExceptionWhenUserNotFound() {
            when(userRepository.findByEmail(anyString())).thenReturn(Optional.empty());

            assertThatThrownBy(() -> userDetailsService.loadUserByUsername("nonexistent@example.com"))
                    .isInstanceOf(UsernameNotFoundException.class)
                    .hasMessage("User not found");
            
            verify(userRepository).findByEmail("nonexistent@example.com");
        }

        @Test
        @DisplayName("throws UsernameNotFoundException for null email")
        void throwsExceptionForNullEmail() {
            when(userRepository.findByEmail(null)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> userDetailsService.loadUserByUsername(null))
                    .isInstanceOf(UsernameNotFoundException.class)
                    .hasMessage("User not found");
        }

        @Test
        @DisplayName("throws UsernameNotFoundException for empty email")
        void throwsExceptionForEmptyEmail() {
            when(userRepository.findByEmail("")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> userDetailsService.loadUserByUsername(""))
                    .isInstanceOf(UsernameNotFoundException.class)
                    .hasMessage("User not found");
        }
    }

    @Nested
    @DisplayName("email case sensitivity")
    class EmailCaseSensitivity {

        @Test
        @DisplayName("email lookup is case-sensitive")
        void emailLookupIsCaseSensitive() {
            when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(testUser));
            when(userRepository.findByEmail("TEST@EXAMPLE.COM")).thenReturn(Optional.empty());

            // Lowercase email succeeds
            UserDetails lowercase = userDetailsService.loadUserByUsername("test@example.com");
            assertThat(lowercase).isNotNull();

            // Uppercase email fails (different from stored)
            assertThatThrownBy(() -> userDetailsService.loadUserByUsername("TEST@EXAMPLE.COM"))
                    .isInstanceOf(UsernameNotFoundException.class);
            
            verify(userRepository).findByEmail("test@example.com");
            verify(userRepository).findByEmail("TEST@EXAMPLE.COM");
        }

        @Test
        @DisplayName("mixed case email is treated as different from lowercase")
        void mixedCaseIsDifferent() {
            when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(testUser));
            when(userRepository.findByEmail("Test@Example.Com")).thenReturn(Optional.empty());

            assertThat(userDetailsService.loadUserByUsername("test@example.com")).isNotNull();

            assertThatThrownBy(() -> userDetailsService.loadUserByUsername("Test@Example.Com"))
                    .isInstanceOf(UsernameNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("user status handling")
    class UserStatusHandling {

        @Test
        @DisplayName("loads ACTIVE user successfully")
        void loadsActiveUser() {
            testUser.setStatus(UserStatus.ACTIVE);
            when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(testUser));

            UserDetails userDetails = userDetailsService.loadUserByUsername("test@example.com");

            assertThat(userDetails).isNotNull();
            // Spring Security's User class will check isEnabled() later
        }

        @Test
        @DisplayName("loads INACTIVE user (Spring Security handles status)")
        void loadsInactiveUser() {
            testUser.setStatus(UserStatus.INACTIVE);
            when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(testUser));

            // UserDetailsService just loads the user — it doesn't enforce status
            // Spring Security checks isEnabled() separately during authentication
            UserDetails userDetails = userDetailsService.loadUserByUsername("test@example.com");

            assertThat(userDetails).isNotNull();
            assertThat(userDetails.isEnabled()).isFalse(); // User entity implements isEnabled()
        }

        @Test
        @DisplayName("loaded INACTIVE user has isEnabled() false")
        void inactiveUserIsNotEnabled() {
            testUser.setStatus(UserStatus.INACTIVE);
            when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(testUser));

            UserDetails userDetails = userDetailsService.loadUserByUsername("test@example.com");

            assertThat(userDetails.isEnabled()).isFalse();
        }
    }

    @Nested
    @DisplayName("user authorities mapping")
    class UserAuthoritiesMapping {

        @Test
        @DisplayName("maps ADMIN role to GrantedAuthority")
        void mapsAdminRole() {
            testUser.setRole(UserRole.ADMIN);
            when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(testUser));

            UserDetails userDetails = userDetailsService.loadUserByUsername("test@example.com");

            assertThat(userDetails.getAuthorities())
                    .hasSize(1)
                    .extracting(GrantedAuthority::getAuthority)
                    .containsExactly("ROLE_ADMIN");
        }

        @Test
        @DisplayName("maps MANAGER role to GrantedAuthority")
        void mapsManagerRole() {
            testUser.setRole(UserRole.MANAGER);
            when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(testUser));

            UserDetails userDetails = userDetailsService.loadUserByUsername("test@example.com");

            assertThat(userDetails.getAuthorities())
                    .extracting(GrantedAuthority::getAuthority)
                    .containsExactly("ROLE_MANAGER");
        }

        @Test
        @DisplayName("maps WAREHOUSE_STAFF role to GrantedAuthority")
        void mapsStaffRole() {
            testUser.setRole(UserRole.WAREHOUSE_STAFF);
            when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(testUser));

            UserDetails userDetails = userDetailsService.loadUserByUsername("test@example.com");

            assertThat(userDetails.getAuthorities())
                    .extracting(GrantedAuthority::getAuthority)
                    .containsExactly("ROLE_WAREHOUSE_STAFF");
        }

        @Test
        @DisplayName("maps VIEWER role to GrantedAuthority")
        void mapsViewerRole() {
            testUser.setRole(UserRole.VIEWER);
            when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(testUser));

            UserDetails userDetails = userDetailsService.loadUserByUsername("test@example.com");

            assertThat(userDetails.getAuthorities())
                    .extracting(GrantedAuthority::getAuthority)
                    .containsExactly("ROLE_VIEWER");
        }

        @Test
        @DisplayName("authorities collection is not null or empty")
        void authoritiesCollectionNotEmpty() {
            when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(testUser));

            UserDetails userDetails = userDetailsService.loadUserByUsername("test@example.com");

            assertThat(userDetails.getAuthorities()).isNotNull();
            assertThat(userDetails.getAuthorities()).isNotEmpty();
        }
    }

    @Nested
    @DisplayName("UserDetails properties")
    class UserDetailsProperties {

        @Test
        @DisplayName("username is the user's email")
        void usernameIsEmail() {
            when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(testUser));

            UserDetails userDetails = userDetailsService.loadUserByUsername("test@example.com");

            assertThat(userDetails.getUsername()).isEqualTo("test@example.com");
        }

        @Test
        @DisplayName("password is the hashed password from database")
        void passwordIsHashedPassword() {
            when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(testUser));

            UserDetails userDetails = userDetailsService.loadUserByUsername("test@example.com");

            assertThat(userDetails.getPassword()).isEqualTo("$2a$10$hashedPassword");
        }

        @Test
        @DisplayName("account is not expired")
        void accountNotExpired() {
            when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(testUser));

            UserDetails userDetails = userDetailsService.loadUserByUsername("test@example.com");

            assertThat(userDetails.isAccountNonExpired()).isTrue();
        }

        @Test
        @DisplayName("account is not locked")
        void accountNotLocked() {
            when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(testUser));

            UserDetails userDetails = userDetailsService.loadUserByUsername("test@example.com");

            assertThat(userDetails.isAccountNonLocked()).isTrue();
        }

        @Test
        @DisplayName("credentials are not expired")
        void credentialsNotExpired() {
            when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(testUser));

            UserDetails userDetails = userDetailsService.loadUserByUsername("test@example.com");

            assertThat(userDetails.isCredentialsNonExpired()).isTrue();
        }
    }

    @Nested
    @DisplayName("repository interaction")
    class RepositoryInteraction {

        @Test
        @DisplayName("calls userRepository.findByEmail exactly once")
        void callsRepositoryOnce() {
            when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(testUser));

            userDetailsService.loadUserByUsername("test@example.com");

            verify(userRepository, times(1)).findByEmail("test@example.com");
        }

        @Test
        @DisplayName("does not call repository if email is cached (not applicable here)")
        void doesNotCacheRepositoryCalls() {
            // UserDetailsServiceImpl doesn't cache — each call hits the repository
            // This test documents that behavior
            when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(testUser));

            userDetailsService.loadUserByUsername("test@example.com");
            userDetailsService.loadUserByUsername("test@example.com");

            verify(userRepository, times(2)).findByEmail("test@example.com");
        }
    }

    @Nested
    @DisplayName("@Transactional annotation")
    class TransactionalAnnotation {

        @Test
        @DisplayName("method is marked as @Transactional(readOnly = true)")
        void methodIsTransactional() throws NoSuchMethodException {
            var method = UserDetailsServiceImpl.class.getMethod("loadUserByUsername", String.class);
            var transactional = method.getAnnotation(org.springframework.transaction.annotation.Transactional.class);

            assertThat(transactional).isNotNull();
            assertThat(transactional.readOnly()).isTrue();
        }
    }
}
