package com.inventra.api.tenant;

import com.inventra.api.entity.Customer;
import com.inventra.api.entity.Tenant;
import com.inventra.api.entity.TenantStatus;
import com.inventra.api.entity.User;
import com.inventra.api.exception.InvalidRequestException;
import com.inventra.api.exception.ResourceNotFoundException;
import com.inventra.api.repository.CustomerRepository;
import com.inventra.api.repository.RefreshTokenRepository;
import com.inventra.api.repository.TenantRepository;
import com.inventra.api.repository.UserRepository;
import com.inventra.api.tenant.dto.TenantResponse;
import com.inventra.api.tenant.dto.UpdateTenantRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TenantServiceTest {

    @Mock
    private TenantRepository tenantRepository;

    @Mock
    private TenantMapper tenantMapper;

    @Mock
    private UserRepository userRepository;

    @Mock
    private CustomerRepository customerRepository;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private TenantService tenantService;

    private static final String TENANT_ID = "tenant-123";
    private static final String TENANT_SLUG = "test-tenant";
    private static final String USER_ID = "user-123";
    private static final String CUSTOMER_ID = "customer-123";
    /** Stand-in bcrypt hash returned by the mocked PasswordEncoder during erasure. */
    private static final String ERASED_HASH = "$2a$12$erasedAccountHashReturnedByMockEncoder";

    @Test
    void getTenant_whenTenantExists_thenReturnsTenant() {
        Tenant tenant = Tenant.builder()
                .id(TENANT_ID)
                .name("Test Tenant")
                .slug(TENANT_SLUG)
                .build();

        TenantResponse expectedResponse = new TenantResponse(
                TENANT_ID,
                "Test Tenant",
                TENANT_SLUG,
                TenantStatus.ACTIVE,
                null
        );

        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(tenant));
        when(tenantMapper.toResponse(tenant)).thenReturn(expectedResponse);

        TenantResponse result = tenantService.getTenant(TENANT_ID);

        assertThat(result).isNotNull();
        assertThat(result.id()).isEqualTo(TENANT_ID);
        assertThat(result.name()).isEqualTo("Test Tenant");
    }

    @Test
    void getTenant_whenTenantNotFound_thenThrowsResourceNotFoundException() {
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> tenantService.getTenant(TENANT_ID))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Tenant not found");
    }

    @Test
    void updateTenant_whenValidRequest_thenUpdatesTenantName() {
        UpdateTenantRequest request = new UpdateTenantRequest("Updated Tenant Name");

        Tenant tenant = Tenant.builder()
                .id(TENANT_ID)
                .name("Old Name")
                .slug(TENANT_SLUG)
                .build();

        Tenant updatedTenant = Tenant.builder()
                .id(TENANT_ID)
                .name("Updated Tenant Name")
                .slug(TENANT_SLUG)
                .build();

        TenantResponse expectedResponse = new TenantResponse(
                TENANT_ID,
                "Updated Tenant Name",
                TENANT_SLUG,
                TenantStatus.ACTIVE,
                null
        );

        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(tenant));
        when(tenantRepository.save(any(Tenant.class))).thenReturn(updatedTenant);
        when(tenantMapper.toResponse(updatedTenant)).thenReturn(expectedResponse);

        TenantResponse result = tenantService.updateTenant(TENANT_ID, request);

        assertThat(result).isNotNull();
        assertThat(result.name()).isEqualTo("Updated Tenant Name");
        assertThat(tenant.getName()).isEqualTo("Updated Tenant Name");

        verify(tenantRepository).save(tenant);
    }

    @Test
    void updateTenant_whenTenantNotFound_thenThrowsResourceNotFoundException() {
        UpdateTenantRequest request = new UpdateTenantRequest("New Name");

        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> tenantService.updateTenant(TENANT_ID, request))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Tenant not found");

        verify(tenantRepository, never()).save(any());
    }

    @Test
    void eraseTenant_whenValidConfirmation_thenPseudonymisesAllDataAndSuspendsTenant() {
        Tenant tenant = Tenant.builder()
                .id(TENANT_ID)
                .name("Test Tenant")
                .slug(TENANT_SLUG)
                .status(TenantStatus.ACTIVE)
                .build();

        User user1 = User.builder()
                .id("user-1")
                .firstName("John")
                .lastName("Doe")
                .email("john@test.com")
                .passwordHash("$2a$10$originalHash")
                .build();

        User user2 = User.builder()
                .id("user-2")
                .firstName("Jane")
                .lastName("Smith")
                .email("jane@test.com")
                .passwordHash("$2a$10$anotherHash")
                .build();

        Customer customer1 = Customer.builder()
                .id("customer-1")
                .name("Customer One")
                .email("customer1@test.com")
                .phone("123-456-7890")
                .address("123 Main St")
                .notes("VIP customer")
                .build();

        Customer customer2 = Customer.builder()
                .id("customer-2")
                .name("Customer Two")
                .email("customer2@test.com")
                .phone("098-765-4321")
                .address("456 Oak Ave")
                .notes("Regular customer")
                .build();

        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(tenant));
        when(userRepository.findAllByTenantId(TENANT_ID)).thenReturn(List.of(user1, user2));
        when(customerRepository.findByTenantId(eq(TENANT_ID), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(customer1, customer2)));
        when(passwordEncoder.encode(anyString())).thenReturn(ERASED_HASH);

        tenantService.eraseTenant(TENANT_ID, TENANT_SLUG);

        assertThat(user1.getFirstName()).isEqualTo("[deleted]");
        assertThat(user1.getLastName()).isEqualTo("[deleted]");
        assertThat(user1.getEmail()).isEqualTo("[deleted-user-1]@deleted.invalid");
        assertThat(user1.getPasswordHash()).isEqualTo(ERASED_HASH);

        assertThat(user2.getFirstName()).isEqualTo("[deleted]");
        assertThat(user2.getLastName()).isEqualTo("[deleted]");
        assertThat(user2.getEmail()).isEqualTo("[deleted-user-2]@deleted.invalid");
        assertThat(user2.getPasswordHash()).isEqualTo(ERASED_HASH);

        assertThat(customer1.getName()).isEqualTo("[deleted]");
        assertThat(customer1.getEmail()).isNull();
        assertThat(customer1.getPhone()).isNull();
        assertThat(customer1.getAddress()).isNull();
        assertThat(customer1.getNotes()).isNull();

        assertThat(customer2.getName()).isEqualTo("[deleted]");
        assertThat(customer2.getEmail()).isNull();
        assertThat(customer2.getPhone()).isNull();
        assertThat(customer2.getAddress()).isNull();
        assertThat(customer2.getNotes()).isNull();

        assertThat(tenant.getName()).isEqualTo("[deleted-" + TENANT_ID + "]");
        assertThat(tenant.getStatus()).isEqualTo(TenantStatus.SUSPENDED);

        verify(userRepository, times(2)).save(any(User.class));
        verify(customerRepository, times(2)).save(any(Customer.class));
        verify(tenantRepository).save(tenant);
    }

    @Test
    void eraseTenant_whenIncorrectConfirmationSlug_thenThrowsInvalidRequestException() {
        Tenant tenant = Tenant.builder()
                .id(TENANT_ID)
                .name("Test Tenant")
                .slug(TENANT_SLUG)
                .build();

        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(tenant));

        assertThatThrownBy(() -> tenantService.eraseTenant(TENANT_ID, "wrong-slug"))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("Confirmation slug does not match");

        verify(userRepository, never()).findAllByTenantId(anyString());
        verify(customerRepository, never()).findByTenantId(anyString(), any());
        verify(userRepository, never()).save(any());
        verify(customerRepository, never()).save(any());
        verify(tenantRepository, never()).save(any());
    }

    @Test
    void eraseTenant_whenTenantNotFound_thenThrowsResourceNotFoundException() {
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> tenantService.eraseTenant(TENANT_ID, TENANT_SLUG))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Tenant not found");

        verify(userRepository, never()).findAllByTenantId(anyString());
    }

    @Test
    void eraseTenant_whenNoUsers_thenStillPseudonymisesCustomersAndTenant() {
        Tenant tenant = Tenant.builder()
                .id(TENANT_ID)
                .name("Test Tenant")
                .slug(TENANT_SLUG)
                .status(TenantStatus.ACTIVE)
                .build();

        Customer customer = Customer.builder()
                .id(CUSTOMER_ID)
                .name("Customer")
                .email("customer@test.com")
                .build();

        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(tenant));
        when(userRepository.findAllByTenantId(TENANT_ID)).thenReturn(List.of());
        when(customerRepository.findByTenantId(eq(TENANT_ID), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(customer)));

        tenantService.eraseTenant(TENANT_ID, TENANT_SLUG);

        assertThat(customer.getName()).isEqualTo("[deleted]");
        assertThat(tenant.getStatus()).isEqualTo(TenantStatus.SUSPENDED);

        verify(userRepository, never()).save(any());
        verify(customerRepository).save(customer);
        verify(tenantRepository).save(tenant);
    }

    @Test
    void eraseTenant_whenNoCustomers_thenStillPseudonymisesUsersAndTenant() {
        Tenant tenant = Tenant.builder()
                .id(TENANT_ID)
                .name("Test Tenant")
                .slug(TENANT_SLUG)
                .status(TenantStatus.ACTIVE)
                .build();

        User user = User.builder()
                .id(USER_ID)
                .firstName("John")
                .lastName("Doe")
                .email("john@test.com")
                .passwordHash("$2a$10$hash")
                .build();

        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(tenant));
        when(userRepository.findAllByTenantId(TENANT_ID)).thenReturn(List.of(user));
        when(customerRepository.findByTenantId(eq(TENANT_ID), any(Pageable.class)))
                .thenReturn(Page.empty());
        when(passwordEncoder.encode(anyString())).thenReturn(ERASED_HASH);

        tenantService.eraseTenant(TENANT_ID, TENANT_SLUG);

        assertThat(user.getFirstName()).isEqualTo("[deleted]");
        assertThat(tenant.getStatus()).isEqualTo(TenantStatus.SUSPENDED);

        verify(userRepository).save(user);
        verify(customerRepository, never()).save(any());
        verify(tenantRepository).save(tenant);
    }

    @Test
    void eraseTenant_whenAlreadySuspended_thenStillPseudonymisesData() {
        Tenant tenant = Tenant.builder()
                .id(TENANT_ID)
                .name("Test Tenant")
                .slug(TENANT_SLUG)
                .status(TenantStatus.SUSPENDED)
                .build();

        User user = User.builder()
                .id(USER_ID)
                .firstName("John")
                .lastName("Doe")
                .email("john@test.com")
                .passwordHash("$2a$10$hash")
                .build();

        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(tenant));
        when(userRepository.findAllByTenantId(TENANT_ID)).thenReturn(List.of(user));
        when(customerRepository.findByTenantId(eq(TENANT_ID), any(Pageable.class)))
                .thenReturn(Page.empty());
        when(passwordEncoder.encode(anyString())).thenReturn(ERASED_HASH);

        tenantService.eraseTenant(TENANT_ID, TENANT_SLUG);

        assertThat(user.getFirstName()).isEqualTo("[deleted]");
        assertThat(tenant.getStatus()).isEqualTo(TenantStatus.SUSPENDED);

        verify(userRepository).save(user);
        verify(tenantRepository).save(tenant);
    }

    @Test
    void eraseTenant_whenMultipleUsers_thenEachGetsUniqueDeletedEmail() {
        Tenant tenant = Tenant.builder()
                .id(TENANT_ID)
                .slug(TENANT_SLUG)
                .build();

        User user1 = User.builder()
                .id("user-1")
                .email("user1@test.com")
                .build();

        User user2 = User.builder()
                .id("user-2")
                .email("user2@test.com")
                .build();

        User user3 = User.builder()
                .id("user-3")
                .email("user3@test.com")
                .build();

        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(tenant));
        when(userRepository.findAllByTenantId(TENANT_ID)).thenReturn(List.of(user1, user2, user3));
        when(customerRepository.findByTenantId(eq(TENANT_ID), any(Pageable.class)))
                .thenReturn(Page.empty());
        when(passwordEncoder.encode(anyString())).thenReturn(ERASED_HASH);

        tenantService.eraseTenant(TENANT_ID, TENANT_SLUG);

        assertThat(user1.getEmail()).isEqualTo("[deleted-user-1]@deleted.invalid");
        assertThat(user2.getEmail()).isEqualTo("[deleted-user-2]@deleted.invalid");
        assertThat(user3.getEmail()).isEqualTo("[deleted-user-3]@deleted.invalid");

        assertThat(user1.getEmail()).isNotEqualTo(user2.getEmail());
        assertThat(user2.getEmail()).isNotEqualTo(user3.getEmail());
        assertThat(user1.getEmail()).isNotEqualTo(user3.getEmail());
    }
}
