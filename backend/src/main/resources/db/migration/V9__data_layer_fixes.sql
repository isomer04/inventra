-- V9: Data layer fixes — FK indexes, ENUM alignment, drop unused table

-- MySQL InnoDB does not auto-create indexes on FK referencing columns.
-- Without these, every JOIN on the FK column requires a full table scan.

-- user.tenant_id — every tenant-scoped user query depends on this
CREATE INDEX idx_user_tenant ON user (tenant_id);

-- order.created_by — JOIN to load order creator name
CREATE INDEX idx_order_created_by ON `order` (created_by);

-- order_status_history.changed_by — JOIN to load who made each transition
CREATE INDEX idx_order_history_changed_by ON order_status_history (changed_by);

-- stock_movement.created_by — JOIN to load movement creator name
CREATE INDEX idx_stock_movement_created_by ON stock_movement (created_by);


-- This table was created in V5 to enumerate order statuses, but order.status
-- is constrained by a CHECK constraint, not a FK to this table.
-- The table serves no purpose and adds confusion.
DROP TABLE IF EXISTS order_status_enum;


-- All other status columns in the schema use MySQL ENUM type (tenant.status,
-- user.status, product.status). customer.status uses VARCHAR(20) + CHECK.
-- Standardise to ENUM. MySQL preserves all existing data during this conversion.
ALTER TABLE customer
    MODIFY COLUMN status ENUM('ACTIVE', 'INACTIVE') NOT NULL DEFAULT 'ACTIVE';
