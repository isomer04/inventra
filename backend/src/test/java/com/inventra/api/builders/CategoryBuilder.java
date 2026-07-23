package com.inventra.api.builders;

import com.inventra.api.entity.Category;
import com.inventra.api.entity.Tenant;

import java.time.Instant;
import java.util.UUID;

/**
 * Builder for creating Category test fixtures.
 *
 * Provides sensible defaults for all required fields and uses UUID suffixes
 * for unique identifiers to ensure tenant isolation in tests.
 *
 * Example usage:
 * <pre>
 * Category category = CategoryBuilder.builder()
 *     .withName("Test Category")
 *     .build();
 * </pre>
 */
public class CategoryBuilder {
    private String id = UUID.randomUUID().toString();
    private Tenant tenant = null;
    private String name = "Test Category " + UUID.randomUUID().toString().substring(0, 8);
    private Category parent = null;
    private Instant createdAt = Instant.now();

    private CategoryBuilder() {
    }

    public static CategoryBuilder builder() {
        return new CategoryBuilder();
    }

    public CategoryBuilder withId(String id) {
        this.id = id;
        return this;
    }

    public CategoryBuilder withTenant(Tenant tenant) {
        this.tenant = tenant;
        return this;
    }

    public CategoryBuilder withName(String name) {
        this.name = name;
        return this;
    }

    public CategoryBuilder withParent(Category parent) {
        this.parent = parent;
        return this;
    }

    public CategoryBuilder withCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
        return this;
    }

    public Category build() {
        return Category.builder()
                .id(id)
                .tenant(tenant)
                .name(name)
                .parent(parent)
                .createdAt(createdAt)
                .build();
    }
}
