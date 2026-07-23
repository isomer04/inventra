package com.inventra.api.order;

import com.inventra.api.entity.OrderItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OrderItemRepository extends JpaRepository<OrderItem, String> {
    
    /**
     * Find items by order ID.
     *
     * <p><b>Tenant contract:</b> this method does NOT verify tenant ownership.
     * Caller MUST have already validated the parent Order belongs to the current tenant
     * via {@code orderRepository.findByIdAndTenantId(orderId, tenantId)} before calling this.
     */
    List<OrderItem> findByOrderId(String orderId);

    /**
     * Delete all items for an order.
     *
     * <p><b>Tenant contract:</b> same as {@link #findByOrderId} — caller
     * must validate tenant ownership of the parent Order first.
     */
    void deleteByOrderId(String orderId);
}
