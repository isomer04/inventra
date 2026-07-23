package com.inventra.api.customer;

import com.inventra.api.tenant.TenantContext;
import com.inventra.api.util.LogSanitizer;
import com.inventra.api.customer.dto.CreateCustomerRequest;
import com.inventra.api.customer.dto.CustomerResponse;
import com.inventra.api.customer.dto.UpdateCustomerRequest;
import com.inventra.api.entity.Customer;
import com.inventra.api.entity.CustomerStatus;
import com.inventra.api.exception.CustomerHasOrdersException;
import com.inventra.api.exception.ResourceNotFoundException;
import com.inventra.api.order.OrderRepository;
import com.inventra.api.repository.CustomerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class CustomerService {
    
    private final CustomerRepository customerRepository;
    private final CustomerMapper customerMapper;
    private final OrderRepository orderRepository;
    
    private String requireTenantId() {
        return TenantContext.requireTenantId();
    }
    
    @Transactional
    public CustomerResponse createCustomer(CreateCustomerRequest request) {
        String tenantId = requireTenantId();
        log.info("Creating customer for tenant: {}", LogSanitizer.sanitize(tenantId));
        
        Customer customer = customerMapper.toEntity(request);
        customer.setTenantId(tenantId);
        
        Customer saved = customerRepository.save(customer);
        log.info("Created customer with ID: {} for tenant: {}", LogSanitizer.sanitize(saved.getId()), LogSanitizer.sanitize(tenantId));
        
        return customerMapper.toResponse(saved);
    }
    
    @Transactional(readOnly = true)
    public Page<CustomerResponse> getAllCustomers(String search, CustomerStatus status, Pageable pageable) {
        String tenantId = requireTenantId();
        log.debug("Fetching customers for tenant: {} with search: {}, status: {}", LogSanitizer.sanitize(tenantId), LogSanitizer.sanitize(search), LogSanitizer.sanitize(status));
        
        Page<Customer> customers;
        
        if (search != null && !search.isBlank() && status != null) {
            customers = customerRepository.searchByTenantIdAndNameOrEmailAndStatus(
                tenantId, search.trim(), status, pageable
            );
        } else if (search != null && !search.isBlank()) {
            customers = customerRepository.searchByTenantIdAndNameOrEmail(
                tenantId, search.trim(), pageable
            );
        } else if (status != null) {
            customers = customerRepository.findByTenantIdAndStatus(tenantId, status, pageable);
        } else {
            customers = customerRepository.findByTenantId(tenantId, pageable);
        }
        
        return customers.map(customerMapper::toResponse);
    }
    
    @Transactional(readOnly = true)
    public CustomerResponse getCustomerById(String customerId) {
        String tenantId = requireTenantId();
        log.debug("Fetching customer {} for tenant: {}", LogSanitizer.sanitize(customerId), LogSanitizer.sanitize(tenantId));
        
        Customer customer = customerRepository.findByTenantIdAndId(tenantId, customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found with ID: " + customerId));
        
        return customerMapper.toResponse(customer);
    }
    
    @Transactional
    public CustomerResponse updateCustomer(String customerId, UpdateCustomerRequest request) {
        String tenantId = requireTenantId();
        log.info("Updating customer {} for tenant: {}", LogSanitizer.sanitize(customerId), LogSanitizer.sanitize(tenantId));
        
        Customer customer = customerRepository.findByTenantIdAndId(tenantId, customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found with ID: " + customerId));
        
        customerMapper.updateEntity(request, customer);
        
        Customer updated = customerRepository.save(customer);
        log.info("Updated customer {} for tenant: {}", LogSanitizer.sanitize(customerId), LogSanitizer.sanitize(tenantId));
        
        return customerMapper.toResponse(updated);
    }
    
    @Transactional
    public void deleteCustomer(String customerId) {
        String tenantId = requireTenantId();
        log.info("Deleting customer {} for tenant: {}", LogSanitizer.sanitize(customerId), LogSanitizer.sanitize(tenantId));
        
        Customer customer = customerRepository.findByTenantIdAndId(tenantId, customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found with ID: " + customerId));
        
        if (orderRepository.existsByTenantIdAndCustomerId(tenantId, customerId)) {
            throw new CustomerHasOrdersException(customerId);
        }
        
        try {
            customerRepository.delete(customer);
            log.info("Deleted customer {} for tenant: {}", LogSanitizer.sanitize(customerId), LogSanitizer.sanitize(tenantId));
        } catch (DataIntegrityViolationException ex) {
            // Handle race condition: order was created between check and delete
            // Only translate FK constraint violation for fk_order_customer
            Throwable rootCause = ex.getRootCause();
            if (rootCause instanceof java.sql.SQLIntegrityConstraintViolationException) {
                String message = rootCause.getMessage();
                if (message != null && message.contains("fk_order_customer")) {
                    log.warn("Failed to delete customer {} due to FK constraint fk_order_customer", LogSanitizer.sanitize(customerId));
                    throw new CustomerHasOrdersException(customerId);
                }
            }
            log.error("Failed to delete customer {} due to unexpected integrity constraint", LogSanitizer.sanitize(customerId), ex);
            throw ex;
        }
    }
    
    public Customer getCustomerEntity(String customerId) {
        String tenantId = requireTenantId();
        return customerRepository.findByTenantIdAndId(tenantId, customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found with ID: " + customerId));
    }
}
