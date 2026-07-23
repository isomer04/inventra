package com.inventra.api.order;

import com.inventra.api.entity.Order;
import com.inventra.api.entity.OrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;

@Repository
public interface OrderRepository extends JpaRepository<Order, String> {

    Optional<Order> findByIdAndTenantId(String id, String tenantId);

    /**
     * Whether any order was created by this user.
     *
     * <p>{@code order.created_by} is {@code ON DELETE RESTRICT} (V5 migration), so a user
     * with orders cannot be hard-deleted. Used by {@code UserService.delete} to reject the
     * request with an actionable 409 rather than letting the FK violation surface.
     */
    boolean existsByCreatedById(String userId);

    Optional<Order> findByOrderNumberAndTenantId(String orderNumber, String tenantId);

    // JOIN FETCH customer and createdBy so the OrderMapper does not
    // trigger N+1 lazy-loads when building OrderSummaryResponse list pages.
    @Query(value = "SELECT DISTINCT o FROM Order o LEFT JOIN FETCH o.customer LEFT JOIN FETCH o.createdBy " +
                   "WHERE o.tenantId = :tenantId",
           countQuery = "SELECT COUNT(o) FROM Order o WHERE o.tenantId = :tenantId")
    Page<Order> findByTenantId(@Param("tenantId") String tenantId, Pageable pageable);

    @Query(value = "SELECT DISTINCT o FROM Order o LEFT JOIN FETCH o.customer LEFT JOIN FETCH o.createdBy " +
                   "WHERE o.tenantId = :tenantId AND o.status = :status",
           countQuery = "SELECT COUNT(o) FROM Order o WHERE o.tenantId = :tenantId AND o.status = :status")
    Page<Order> findByTenantIdAndStatus(@Param("tenantId") String tenantId,
                                        @Param("status") OrderStatus status,
                                        Pageable pageable);

    @Query(value = "SELECT DISTINCT o FROM Order o LEFT JOIN FETCH o.customer LEFT JOIN FETCH o.createdBy " +
                   "WHERE o.tenantId = :tenantId AND o.customer.id = :customerId",
           countQuery = "SELECT COUNT(o) FROM Order o WHERE o.tenantId = :tenantId AND o.customer.id = :customerId")
    Page<Order> findByTenantIdAndCustomerId(
            @Param("tenantId") String tenantId,
            @Param("customerId") String customerId,
            Pageable pageable
    );

    @Query(value = "SELECT DISTINCT o FROM Order o LEFT JOIN FETCH o.customer LEFT JOIN FETCH o.createdBy " +
                   "WHERE o.tenantId = :tenantId " +
                   "AND (:status IS NULL OR o.status = :status) " +
                   "AND (:customerId IS NULL OR o.customer.id = :customerId) " +
                   "AND (:startDate IS NULL OR o.createdAt >= :startDate) " +
                   "AND (:endDate IS NULL OR o.createdAt <= :endDate)",
           countQuery = "SELECT COUNT(o) FROM Order o WHERE o.tenantId = :tenantId " +
                        "AND (:status IS NULL OR o.status = :status) " +
                        "AND (:customerId IS NULL OR o.customer.id = :customerId) " +
                        "AND (:startDate IS NULL OR o.createdAt >= :startDate) " +
                        "AND (:endDate IS NULL OR o.createdAt <= :endDate)")
    Page<Order> findByFilters(
            @Param("tenantId") String tenantId,
            @Param("status") OrderStatus status,
            @Param("customerId") String customerId,
            @Param("startDate") Instant startDate,
            @Param("endDate") Instant endDate,
            Pageable pageable
    );

    @Query(value = "SELECT COALESCE(MAX(CAST(SUBSTRING(order_number, 10) AS UNSIGNED)), 0) " +
                   "FROM `order` WHERE tenant_id = :tenantId " +
                   "AND order_number LIKE :yearPrefix",
           nativeQuery = true)
    Integer findMaxSequenceForYear(
            @Param("tenantId") String tenantId,
            @Param("yearPrefix") String yearPrefix
    );

    @Query("SELECT COUNT(o) > 0 FROM Order o WHERE o.tenantId = :tenantId AND o.customer.id = :customerId")
    boolean existsByTenantIdAndCustomerId(
            @Param("tenantId") String tenantId,
            @Param("customerId") String customerId
    );
}
