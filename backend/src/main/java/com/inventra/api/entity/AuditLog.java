package com.inventra.api.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Immutable;

import java.time.Instant;
import java.util.UUID;

/**
 * Immutable audit record for security-relevant state-changing operations.
 *
 * <p>Provides an append-only audit
 * trail for security-relevant mutations (user lifecycle, order
 * transitions, stock adjustments, tenant updates).
 *
 * <p><b>Immutability contract:</b> once persisted, audit records are never
 * updated or deleted by application code. The DB user should be restricted to
 * INSERT-only on this table in production (enforced via DB user privileges — see ADR-0004).
 *
 * <p><b>No FK to users/tenants:</b> intentional — audit records must survive
 * user and tenant deletion.
 */
@Entity
@Table(name = "audit_log")
@Immutable
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditLog {

    @Id
    @Column(columnDefinition = "CHAR(36)", nullable = false, updatable = false)
    private String id;

    /** Tenant in which the event occurred. Null for cross-tenant system events. */
    @Column(name = "tenant_id", length = 36)
    private String tenantId;

    /** User who triggered the event. Null for system-initiated events. */
    @Column(name = "actor_id", length = 36)
    private String actorId;

    /** Email snapshot at time of event (denormalised — survives user deletion). */
    @Column(name = "actor_email", length = 150)
    private String actorEmail;

    /** Coarse event category, e.g. USER_CREATED, ORDER_SUBMITTED, STOCK_ADJUSTED. */
    @Column(name = "event_type", nullable = false, length = 100)
    private String eventType;

    /** Entity class name, e.g. "User", "Order", "InventoryItem". */
    @Column(name = "entity_type", nullable = false, length = 100)
    private String entityType;

    /** Entity primary-key value. */
    @Column(name = "entity_id", nullable = false, length = 100)
    private String entityId;

    /** JSON snippet of the previous state (key fields only, no secrets). */
    @Column(name = "old_value", columnDefinition = "TEXT")
    private String oldValue;

    /** JSON snippet of the new state (key fields only, no secrets). */
    @Column(name = "new_value", columnDefinition = "TEXT")
    private String newValue;

    /** Wall-clock UTC timestamp of the event. Set by the application, not the DB. */
    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    /** Client IP address captured from the HTTP request (best-effort). */
    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @PrePersist
    void prePersist() {
        if (id == null) id = UUID.randomUUID().toString();
        if (occurredAt == null) occurredAt = Instant.now();
    }
}
