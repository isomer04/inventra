package com.inventra.api.builders;

import com.inventra.api.entity.InventoryItem;
import com.inventra.api.entity.Product;

import java.time.Instant;
import java.util.UUID;

/**
 * Builder for creating InventoryItem test fixtures.
 * 
 * Wraps the Lombok @Builder with test-friendly defaults and UUID suffixes
 * for unique identifiers to ensure tenant isolation in tests.
 * 
 * Example usage:
 * <pre>
 * InventoryItem item = InventoryItemBuilder.builder()
 *     .withProductId(productId)
 *     .withQuantityOnHand(100)
 *     .build();
 * </pre>
 */
public class InventoryItemBuilder {
    private String id = UUID.randomUUID().toString();
    private String tenantId = UUID.randomUUID().toString();
    private String productId = UUID.randomUUID().toString();
    private Product product;
    private Integer quantityOnHand = 100;
    private Integer quantityReserved = 0;
    private Integer reorderPoint = 10;
    private Integer version = 0;
    private Instant lastUpdated = Instant.now();

    private InventoryItemBuilder() {
    }

    public static InventoryItemBuilder builder() {
        return new InventoryItemBuilder();
    }

    public InventoryItemBuilder withId(String id) {
        this.id = id;
        return this;
    }

    public InventoryItemBuilder withTenantId(String tenantId) {
        this.tenantId = tenantId;
        return this;
    }

    public InventoryItemBuilder withProductId(String productId) {
        this.productId = productId;
        return this;
    }

    public InventoryItemBuilder withProduct(Product product) {
        this.product = product;
        return this;
    }

    public InventoryItemBuilder withQuantityOnHand(Integer quantityOnHand) {
        this.quantityOnHand = quantityOnHand;
        return this;
    }

    public InventoryItemBuilder withQuantityReserved(Integer quantityReserved) {
        this.quantityReserved = quantityReserved;
        return this;
    }

    public InventoryItemBuilder withReorderPoint(Integer reorderPoint) {
        this.reorderPoint = reorderPoint;
        return this;
    }

    public InventoryItemBuilder withVersion(Integer version) {
        this.version = version;
        return this;
    }

    public InventoryItemBuilder withLastUpdated(Instant lastUpdated) {
        this.lastUpdated = lastUpdated;
        return this;
    }

    public InventoryItem build() {
        return InventoryItem.builder()
                .id(id)
                .tenantId(tenantId)
                .productId(productId)
                .product(product)
                .quantityOnHand(quantityOnHand)
                .quantityReserved(quantityReserved)
                .reorderPoint(reorderPoint)
                .version(version)
                .lastUpdated(lastUpdated)
                .build();
    }
}
