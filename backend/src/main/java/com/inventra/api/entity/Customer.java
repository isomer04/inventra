package com.inventra.api.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Each customer belongs to a single tenant and can be referenced by orders.
 */
@Entity
@Table(name = "customer", indexes = {
    @Index(name = "idx_customer_tenant", columnList = "tenant_id"),
    @Index(name = "idx_customer_name", columnList = "tenant_id, name"),
    @Index(name = "idx_customer_email", columnList = "tenant_id, email"),
    @Index(name = "idx_customer_status", columnList = "tenant_id, status")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Customer {
    
    @Id
    @Column(name = "id", length = 36, nullable = false)
    private String id;
    
    @Column(name = "tenant_id", length = 36, nullable = false)
    private String tenantId;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id", insertable = false, updatable = false)
    private Tenant tenant;
    
    @Column(name = "name", length = 200, nullable = false)
    private String name;
    
    @Column(name = "email", length = 150)
    private String email;
    
    @Column(name = "phone", length = 30)
    private String phone;
    
    @Column(name = "address", columnDefinition = "TEXT")
    private String address;
    
    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    @Builder.Default
    private CustomerStatus status = CustomerStatus.ACTIVE;
    
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    
    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    
    @PrePersist
    public void prePersist() {
        if (id == null) {
            id = UUID.randomUUID().toString();
        }
        if (status == null) {
            status = CustomerStatus.ACTIVE;
        }
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Customer)) return false;
        Customer customer = (Customer) o;
        // If either ID is null, entities are not equal (transient entities)
        if (id == null || customer.id == null) return false;
        return Objects.equals(id, customer.id);
    }
    
    @Override
    public int hashCode() {
        // Use identity hashCode for transient entities, id hashCode for persistent
        return id != null ? id.hashCode() : System.identityHashCode(this);
    }
    
    @Override
    public String toString() {
        return "Customer{" +
                "id='" + id + '\'' +
                ", tenantId='" + tenantId + '\'' +
                ", name='" + name + '\'' +
                ", email='" + email + '\'' +
                ", status=" + status +
                '}';
    }
}
