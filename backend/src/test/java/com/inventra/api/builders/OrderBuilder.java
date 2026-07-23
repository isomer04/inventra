package com.inventra.api.builders;

import com.inventra.api.entity.Customer;
import com.inventra.api.entity.Order;
import com.inventra.api.entity.OrderStatus;
import com.inventra.api.entity.User;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Builder for creating Order test fixtures.
 * 
 * Wraps the Lombok @Builder with test-friendly defaults and UUID suffixes
 * for unique identifiers to ensure tenant isolation in tests.
 * 
 * Example usage:
 * <pre>
 * Order order = OrderBuilder.builder()
 *     .withCustomer(customer)
 *     .withCreatedBy(user)
 *     .build();
 * </pre>
 */
public class OrderBuilder {
    private String id = UUID.randomUUID().toString();
    private String tenantId = UUID.randomUUID().toString();
    private String orderNumber = "ORD-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    private Customer customer;
    private OrderStatus status = OrderStatus.DRAFT;
    private BigDecimal totalAmount = BigDecimal.ZERO;
    private String notes = "Test order notes";
    private User createdBy;
    private Instant createdAt = Instant.now();
    private Instant updatedAt = Instant.now();
    private Integer version = 0;

    private OrderBuilder() {
    }

    public static OrderBuilder builder() {
        return new OrderBuilder();
    }

    public OrderBuilder withId(String id) {
        this.id = id;
        return this;
    }

    public OrderBuilder withTenantId(String tenantId) {
        this.tenantId = tenantId;
        return this;
    }

    public OrderBuilder withOrderNumber(String orderNumber) {
        this.orderNumber = orderNumber;
        return this;
    }

    public OrderBuilder withCustomer(Customer customer) {
        this.customer = customer;
        return this;
    }

    public OrderBuilder withStatus(OrderStatus status) {
        this.status = status;
        return this;
    }

    public OrderBuilder withTotalAmount(BigDecimal totalAmount) {
        this.totalAmount = totalAmount;
        return this;
    }

    public OrderBuilder withNotes(String notes) {
        this.notes = notes;
        return this;
    }

    public OrderBuilder withCreatedBy(User createdBy) {
        this.createdBy = createdBy;
        return this;
    }

    public OrderBuilder withCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
        return this;
    }

    public OrderBuilder withUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
        return this;
    }

    public OrderBuilder withVersion(Integer version) {
        this.version = version;
        return this;
    }

    public Order build() {
        return Order.builder()
                .id(id)
                .tenantId(tenantId)
                .orderNumber(orderNumber)
                .customer(customer)
                .status(status)
                .totalAmount(totalAmount)
                .notes(notes)
                .createdBy(createdBy)
                .createdAt(createdAt)
                .updatedAt(updatedAt)
                .version(version)
                .build();
    }
}
