package com.inventra.api.customer;

import com.inventra.api.customer.dto.CreateCustomerRequest;
import com.inventra.api.customer.dto.CustomerResponse;
import com.inventra.api.customer.dto.UpdateCustomerRequest;
import com.inventra.api.entity.Customer;
import com.inventra.api.entity.CustomerStatus;
import com.inventra.api.exception.CustomerHasOrdersException;
import com.inventra.api.exception.ResourceNotFoundException;
import com.inventra.api.order.OrderRepository;
import com.inventra.api.repository.CustomerRepository;
import com.inventra.api.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.sql.SQLIntegrityConstraintViolationException;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CustomerServiceTest {

    @Mock
    private CustomerRepository customerRepository;

    @Mock
    private CustomerMapper customerMapper;

    @Mock
    private OrderRepository orderRepository;

    @InjectMocks
    private CustomerService customerService;

    private static final String TENANT_ID = "tenant-123";
    private static final String CUSTOMER_ID = "customer-123";

    @BeforeEach
    void setUp() {
        TenantContext.setTenantId(TENANT_ID);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void createCustomer_whenValidRequest_thenCreatesCustomer() {
        CreateCustomerRequest request = new CreateCustomerRequest(
                "John Doe",
                "john@test.com",
                "123-456-7890",
                "123 Main St",
                "VIP customer"
        );

        Customer mappedCustomer = Customer.builder()
                .name("John Doe")
                .email("john@test.com")
                .build();

        Customer savedCustomer = Customer.builder()
                .id(CUSTOMER_ID)
                .tenantId(TENANT_ID)
                .name("John Doe")
                .email("john@test.com")
                .build();

        CustomerResponse expectedResponse = new CustomerResponse();
        expectedResponse.setId(CUSTOMER_ID);

        when(customerMapper.toEntity(request)).thenReturn(mappedCustomer);
        when(customerRepository.save(mappedCustomer)).thenReturn(savedCustomer);
        when(customerMapper.toResponse(savedCustomer)).thenReturn(expectedResponse);

        CustomerResponse result = customerService.createCustomer(request);

        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(CUSTOMER_ID);
        assertThat(mappedCustomer.getTenantId()).isEqualTo(TENANT_ID);

        verify(customerRepository).save(mappedCustomer);
    }

    @Test
    void updateCustomer_whenValidRequest_thenUpdatesCustomer() {
        UpdateCustomerRequest request = new UpdateCustomerRequest(
                "Updated Name",
                "updated@test.com",
                "098-765-4321",
                "456 Oak Ave",
                "Updated notes",
                CustomerStatus.ACTIVE
        );

        Customer customer = Customer.builder()
                .id(CUSTOMER_ID)
                .tenantId(TENANT_ID)
                .name("Old Name")
                .status(CustomerStatus.ACTIVE)
                .build();

        Customer updatedCustomer = Customer.builder()
                .id(CUSTOMER_ID)
                .tenantId(TENANT_ID)
                .name("Updated Name")
                .build();

        CustomerResponse expectedResponse = new CustomerResponse();
        expectedResponse.setId(CUSTOMER_ID);

        when(customerRepository.findByTenantIdAndId(TENANT_ID, CUSTOMER_ID)).thenReturn(Optional.of(customer));
        when(customerRepository.save(customer)).thenReturn(updatedCustomer);
        when(customerMapper.toResponse(updatedCustomer)).thenReturn(expectedResponse);

        CustomerResponse result = customerService.updateCustomer(CUSTOMER_ID, request);

        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(CUSTOMER_ID);

        verify(customerMapper).updateEntity(request, customer);
        verify(customerRepository).save(customer);
    }

    @Test
    void updateCustomer_whenCustomerNotFound_thenThrowsResourceNotFoundException() {
        UpdateCustomerRequest request = new UpdateCustomerRequest(
                "Name",
                null,
                null,
                null,
                null,
                null
        );

        when(customerRepository.findByTenantIdAndId(TENANT_ID, CUSTOMER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> customerService.updateCustomer(CUSTOMER_ID, request))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Customer not found");

        verify(customerRepository, never()).save(any());
    }

    @Test
    void deleteCustomer_whenNoOrders_thenDeletesCustomer() {
        Customer customer = Customer.builder()
                .id(CUSTOMER_ID)
                .tenantId(TENANT_ID)
                .build();

        when(customerRepository.findByTenantIdAndId(TENANT_ID, CUSTOMER_ID)).thenReturn(Optional.of(customer));
        when(orderRepository.existsByTenantIdAndCustomerId(TENANT_ID, CUSTOMER_ID)).thenReturn(false);

        customerService.deleteCustomer(CUSTOMER_ID);

        verify(customerRepository).delete(customer);
    }

    @Test
    void deleteCustomer_whenHasOrders_thenThrowsCustomerHasOrdersException() {
        Customer customer = Customer.builder()
                .id(CUSTOMER_ID)
                .tenantId(TENANT_ID)
                .build();

        when(customerRepository.findByTenantIdAndId(TENANT_ID, CUSTOMER_ID)).thenReturn(Optional.of(customer));
        when(orderRepository.existsByTenantIdAndCustomerId(TENANT_ID, CUSTOMER_ID)).thenReturn(true);

        assertThatThrownBy(() -> customerService.deleteCustomer(CUSTOMER_ID))
                .isInstanceOf(CustomerHasOrdersException.class);

        verify(customerRepository, never()).delete(any());
    }

    @Test
    void deleteCustomer_whenRaceConditionCreatesOrder_thenThrowsCustomerHasOrdersException() {
        Customer customer = Customer.builder()
                .id(CUSTOMER_ID)
                .tenantId(TENANT_ID)
                .build();

        SQLIntegrityConstraintViolationException sqlException = 
                new SQLIntegrityConstraintViolationException("Cannot delete or update a parent row: a foreign key constraint fails (fk_order_customer)");
        DataIntegrityViolationException exception = new DataIntegrityViolationException("FK violation", sqlException);

        when(customerRepository.findByTenantIdAndId(TENANT_ID, CUSTOMER_ID)).thenReturn(Optional.of(customer));
        when(orderRepository.existsByTenantIdAndCustomerId(TENANT_ID, CUSTOMER_ID)).thenReturn(false);
        doThrow(exception).when(customerRepository).delete(customer);

        assertThatThrownBy(() -> customerService.deleteCustomer(CUSTOMER_ID))
                .isInstanceOf(CustomerHasOrdersException.class);
    }

    @Test
    void deleteCustomer_whenOtherIntegrityViolation_thenRethrowsException() {
        Customer customer = Customer.builder()
                .id(CUSTOMER_ID)
                .tenantId(TENANT_ID)
                .build();

        SQLIntegrityConstraintViolationException sqlException = 
                new SQLIntegrityConstraintViolationException("Some other constraint violation");
        DataIntegrityViolationException exception = new DataIntegrityViolationException("Other violation", sqlException);

        when(customerRepository.findByTenantIdAndId(TENANT_ID, CUSTOMER_ID)).thenReturn(Optional.of(customer));
        when(orderRepository.existsByTenantIdAndCustomerId(TENANT_ID, CUSTOMER_ID)).thenReturn(false);
        doThrow(exception).when(customerRepository).delete(customer);

        assertThatThrownBy(() -> customerService.deleteCustomer(CUSTOMER_ID))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("Other violation");
    }

    @Test
    void deleteCustomer_whenCustomerNotFound_thenThrowsResourceNotFoundException() {
        when(customerRepository.findByTenantIdAndId(TENANT_ID, CUSTOMER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> customerService.deleteCustomer(CUSTOMER_ID))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Customer not found");

        verify(customerRepository, never()).delete(any());
    }

    @Test
    void getCustomerById_whenCustomerExists_thenReturnsCustomer() {
        Customer customer = Customer.builder()
                .id(CUSTOMER_ID)
                .tenantId(TENANT_ID)
                .build();

        CustomerResponse expectedResponse = new CustomerResponse();
        expectedResponse.setId(CUSTOMER_ID);

        when(customerRepository.findByTenantIdAndId(TENANT_ID, CUSTOMER_ID)).thenReturn(Optional.of(customer));
        when(customerMapper.toResponse(customer)).thenReturn(expectedResponse);

        CustomerResponse result = customerService.getCustomerById(CUSTOMER_ID);

        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(CUSTOMER_ID);
    }

    @Test
    void getCustomerById_whenCustomerNotFound_thenThrowsResourceNotFoundException() {
        when(customerRepository.findByTenantIdAndId(TENANT_ID, CUSTOMER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> customerService.getCustomerById(CUSTOMER_ID))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Customer not found");
    }

    @Test
    void getCustomerEntity_whenCustomerExists_thenReturnsEntity() {
        Customer customer = Customer.builder()
                .id(CUSTOMER_ID)
                .tenantId(TENANT_ID)
                .build();

        when(customerRepository.findByTenantIdAndId(TENANT_ID, CUSTOMER_ID)).thenReturn(Optional.of(customer));

        Customer result = customerService.getCustomerEntity(CUSTOMER_ID);

        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(CUSTOMER_ID);
    }

    @Test
    void getCustomerEntity_whenCustomerNotFound_thenThrowsResourceNotFoundException() {
        when(customerRepository.findByTenantIdAndId(TENANT_ID, CUSTOMER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> customerService.getCustomerEntity(CUSTOMER_ID))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Customer not found");
    }
}
