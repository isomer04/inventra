package com.inventra.api.builders;

import com.inventra.api.entity.Customer;
import com.inventra.api.entity.CustomerStatus;

import java.time.Instant;
import java.util.UUID;

/**
 * Builder for creating Customer test fixtures.
 *
 * Provides sensible defaults for all required fields and uses UUID suffixes
 * for unique identifiers to ensure tenant isolation in tests.
 *
 * Example usage:
 * <pre>
 * Customer customer = CustomerBuilder.builder()
 *     .withName("Test Customer")
 *     .withEmail("test@example.test")
 *     .build();
 * </pre>
 */
public class CustomerBuilder {
    private String id = UUID.randomUUID().toString();
    private String tenantId = UUID.randomUUID().toString();
    private String name = "Test Customer " + UUID.randomUUID().toString().substring(0, 8);
    private String email = "test-" + UUID.randomUUID().toString().substring(0, 8) + "@example.test";
    private String phone = "+1-555-0100";
    private String address = "123 Test Street, Test City, TC 12345";
    private String notes = "Test customer notes";
    private CustomerStatus status = CustomerStatus.ACTIVE;
    private Instant createdAt = Instant.now();
    private Instant updatedAt = Instant.now();

    private CustomerBuilder() {
    }

    public static CustomerBuilder builder() {
        return new CustomerBuilder();
    }

    public CustomerBuilder withId(String id) {
        this.id = id;
        return this;
    }

    public CustomerBuilder withTenantId(String tenantId) {
        this.tenantId = tenantId;
        return this;
    }

    public CustomerBuilder withName(String name) {
        this.name = name;
        return this;
    }

    public CustomerBuilder withEmail(String email) {
        this.email = email;
        return this;
    }

    public CustomerBuilder withPhone(String phone) {
        this.phone = phone;
        return this;
    }

    public CustomerBuilder withAddress(String address) {
        this.address = address;
        return this;
    }

    public CustomerBuilder withNotes(String notes) {
        this.notes = notes;
        return this;
    }

    public CustomerBuilder withStatus(CustomerStatus status) {
        this.status = status;
        return this;
    }

    public CustomerBuilder withCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
        return this;
    }

    public CustomerBuilder withUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
        return this;
    }

    public Customer build() {
        return Customer.builder()
                .id(id)
                .tenantId(tenantId)
                .name(name)
                .email(email)
                .phone(phone)
                .address(address)
                .notes(notes)
                .status(status)
                .createdAt(createdAt)
                .updatedAt(updatedAt)
                .build();
    }
}
