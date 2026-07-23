package com.inventra.api.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Immutable;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "order_status_history")
@Immutable
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
@EqualsAndHashCode
public class OrderStatusHistory {
    
    @Id
    @Column(length = 36, updatable = false)
    private String id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false, updatable = false)
    private Order order;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", length = 20, updatable = false)
    private OrderStatus fromStatus;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false, length = 20, updatable = false)
    private OrderStatus toStatus;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "changed_by", nullable = false, updatable = false)
    private User changedBy;
    
    @Column(name = "changed_at", nullable = false, updatable = false)
    private Instant changedAt;
    
    @Column(columnDefinition = "TEXT", updatable = false)
    private String notes;
    
    @PrePersist
    protected void onCreate() {
        if (id == null) {
            id = UUID.randomUUID().toString();
        }
        if (changedAt == null) {
            changedAt = Instant.now();
        }
    }
}
