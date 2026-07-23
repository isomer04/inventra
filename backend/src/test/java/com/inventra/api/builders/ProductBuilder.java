package com.inventra.api.builders;

import com.inventra.api.entity.Category;
import com.inventra.api.entity.Product;
import com.inventra.api.entity.ProductStatus;
import com.inventra.api.entity.Tenant;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Builder for creating Product test fixtures.
 *
 * Provides sensible defaults for all required fields and uses UUID suffixes
 * for unique identifiers to ensure tenant isolation in tests.
 *
 * Example usage:
 * <pre>
 * Product product = ProductBuilder.builder()
 *     .withName("Test Product")
 *     .withSku("TEST-SKU-001")
 *     .withUnitPrice(new BigDecimal("99.99"))
 *     .build();
 * </pre>
 */
public class ProductBuilder {
    private String id = UUID.randomUUID().toString();
    private Tenant tenant = null;
    private String sku = "TEST-SKU-" + UUID.randomUUID().toString().substring(0, 8);
    private String name = "Test Product " + UUID.randomUUID().toString().substring(0, 8);
    private String description = "Test product description";
    private Category category = null;
    private BigDecimal unitPrice = new BigDecimal("99.99");
    private String unitOfMeasure = "EA";
    private ProductStatus status = ProductStatus.ACTIVE;
    private Instant createdAt = Instant.now();
    private Instant updatedAt = Instant.now();

    private ProductBuilder() {
    }

    public static ProductBuilder builder() {
        return new ProductBuilder();
    }

    public ProductBuilder withId(String id) {
        this.id = id;
        return this;
    }

    public ProductBuilder withTenant(Tenant tenant) {
        this.tenant = tenant;
        return this;
    }

    public ProductBuilder withSku(String sku) {
        this.sku = sku;
        return this;
    }

    public ProductBuilder withName(String name) {
        this.name = name;
        return this;
    }

    public ProductBuilder withDescription(String description) {
        this.description = description;
        return this;
    }

    public ProductBuilder withCategory(Category category) {
        this.category = category;
        return this;
    }

    public ProductBuilder withUnitPrice(BigDecimal unitPrice) {
        this.unitPrice = unitPrice;
        return this;
    }

    public ProductBuilder withUnitOfMeasure(String unitOfMeasure) {
        this.unitOfMeasure = unitOfMeasure;
        return this;
    }

    public ProductBuilder withStatus(ProductStatus status) {
        this.status = status;
        return this;
    }

    public ProductBuilder withCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
        return this;
    }

    public ProductBuilder withUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
        return this;
    }

    public Product build() {
        return Product.builder()
                .id(id)
                .tenant(tenant)
                .sku(sku)
                .name(name)
                .description(description)
                .category(category)
                .unitPrice(unitPrice)
                .unitOfMeasure(unitOfMeasure)
                .status(status)
                .createdAt(createdAt)
                .updatedAt(updatedAt)
                .build();
    }
}
