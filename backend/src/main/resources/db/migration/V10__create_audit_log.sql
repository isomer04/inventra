-- V10: Audit log table for state-changing operations
-- Persist an immutable audit trail for all
-- security-relevant mutations: user creation/deletion/status changes, tenant
-- updates, order lifecycle transitions, stock adjustments.
--
-- Design decisions:
--   • Immutable: no UPDATE/DELETE on this table (enforced via DB user privileges — see ADR-0004).
--   • tenant_id nullable: allows cross-tenant admin events (future).
--   • actor_id nullable: allows system-initiated events (scheduled tasks).
--   • old_value / new_value: TEXT to hold arbitrary JSON diff snippets.
--   • No FK to user/tenant: audit records must survive user/tenant deletion.

CREATE TABLE audit_log (
    id            CHAR(36)     NOT NULL,
    tenant_id     CHAR(36),
    actor_id      CHAR(36),
    actor_email   VARCHAR(150),
    event_type    VARCHAR(100) NOT NULL,
    entity_type   VARCHAR(100) NOT NULL,
    entity_id     VARCHAR(100) NOT NULL,
    old_value     TEXT,
    new_value     TEXT,
    occurred_at   DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    ip_address    VARCHAR(45),
    PRIMARY KEY (id),
    INDEX idx_audit_tenant_occurred (tenant_id, occurred_at),
    INDEX idx_audit_entity          (entity_type, entity_id),
    INDEX idx_audit_actor           (actor_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
