package com.inventra.api.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;

@Entity
@Table(name = "inventory_item")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InventoryItem {

    @Id
    @UuidGenerator
    @Column(length = 36)
    private String id;

    @Column(name = "tenant_id", nullable = false, length = 36)
    private String tenantId;

    @Column(name = "product_id", nullable = false, length = 36)
    private String productId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", insertable = false, updatable = false)
    private Product product;

    @Builder.Default
    @Column(name = "quantity_on_hand", nullable = false)
    private Integer quantityOnHand = 0;

    @Builder.Default
    @Column(name = "quantity_reserved", nullable = false)
    private Integer quantityReserved = 0;

    @Builder.Default
    @Column(name = "reorder_point", nullable = false)
    private Integer reorderPoint = 0;

    @Builder.Default
    @Version
    @Column(nullable = false)
    private Integer version = 0;

    @Column(name = "last_updated", nullable = false)
    private java.time.Instant lastUpdated;

    @PrePersist
    @PreUpdate
    protected void onUpdate() {
        this.lastUpdated = java.time.Instant.now();
    }

    @Transient
    public Integer getAvailableStock() {
        return quantityOnHand - quantityReserved;
    }
}
