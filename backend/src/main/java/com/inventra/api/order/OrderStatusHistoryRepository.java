package com.inventra.api.order;

import com.inventra.api.entity.OrderStatusHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OrderStatusHistoryRepository extends JpaRepository<OrderStatusHistory, String> {
    
    /**
     * Find status history entries for an order, ordered chronologically.
     *
     * <p>Scoped through the parent order's tenant so isolation is enforced by the query
     * itself. The previous unscoped finder was safe only because the controller made a
     * tenant-checking call whose result it discarded — a guarantee any reasonable
     * refactor would delete as dead code, and one that a second caller would not inherit.
     */
    @Query("""
            SELECT h FROM OrderStatusHistory h
            WHERE h.order.id = :orderId AND h.order.tenantId = :tenantId
            ORDER BY h.changedAt ASC
            """)
    List<OrderStatusHistory> findByOrderIdAndTenantId(@Param("orderId") String orderId,
                                                      @Param("tenantId") String tenantId);

    /**
     * Whether this user has ever changed an order's status.
     *
     * <p>{@code order_status_history.changed_by} is {@code ON DELETE RESTRICT} (V5 migration).
     * See {@code OrderRepository.existsByCreatedById} for the rationale.
     */
    boolean existsByChangedById(String userId);

    /**
     * Bulk-delete history entries for an order.
     *
     * <p>The {@code order_status_history.order_id} foreign key uses
     * {@code ON DELETE RESTRICT} (see V5 migration) to preserve audit trails
     * for completed orders. Callers that legitimately delete a parent order
     * (only DRAFT orders are deletable per business rules) must clear its
     * history first to avoid {@code DataIntegrityViolationException}.
     *
     * <p><b>Tenant contract:</b> caller MUST have already validated the
     * parent Order belongs to the current tenant before calling this.
     */
    void deleteByOrderId(String orderId);
}
