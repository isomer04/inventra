package com.inventra.api.repository;

import com.inventra.api.entity.Customer;
import com.inventra.api.entity.CustomerStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repository for Customer entity operations.
 * All queries are automatically scoped by tenant_id.
 */
@Repository
public interface CustomerRepository extends JpaRepository<Customer, String> {
    
    Optional<Customer> findByTenantIdAndId(String tenantId, String id);
    
    Page<Customer> findByTenantId(String tenantId, Pageable pageable);
    
    Page<Customer> findByTenantIdAndStatus(String tenantId, CustomerStatus status, Pageable pageable);
    
    /**
     * Search customers by name or email within a specific tenant.
     * Performs case-insensitive partial matching.
     */
    @Query("SELECT c FROM Customer c WHERE c.tenantId = :tenantId " +
           "AND (LOWER(c.name) LIKE LOWER(CONCAT('%', :searchTerm, '%')) " +
           "OR LOWER(c.email) LIKE LOWER(CONCAT('%', :searchTerm, '%')))")
    Page<Customer> searchByTenantIdAndNameOrEmail(
        @Param("tenantId") String tenantId,
        @Param("searchTerm") String searchTerm,
        Pageable pageable
    );
    
    /**
     * Search customers by name or email and status within a specific tenant.
     * Performs case-insensitive partial matching.
     */
    @Query("SELECT c FROM Customer c WHERE c.tenantId = :tenantId " +
           "AND c.status = :status " +
           "AND (LOWER(c.name) LIKE LOWER(CONCAT('%', :searchTerm, '%')) " +
           "OR LOWER(c.email) LIKE LOWER(CONCAT('%', :searchTerm, '%')))")
    Page<Customer> searchByTenantIdAndNameOrEmailAndStatus(
        @Param("tenantId") String tenantId,
        @Param("searchTerm") String searchTerm,
        @Param("status") CustomerStatus status,
        Pageable pageable
    );
    
    boolean existsByTenantIdAndId(String tenantId, String id);
}
