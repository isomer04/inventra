-- Order, order item, and order status history tables

-- Order status enum
CREATE TABLE IF NOT EXISTS order_status_enum (
    status VARCHAR(20) PRIMARY KEY
);

-- Insert status values idempotently (MySQL syntax)
INSERT IGNORE INTO order_status_enum (status) VALUES
    ('DRAFT'),
    ('SUBMITTED'),
    ('APPROVED'),
    ('PICKING'),
    ('SHIPPED'),
    ('DELIVERED'),
    ('REJECTED'),
    ('CANCELLED');

-- Orders table
CREATE TABLE `order` (
    id CHAR(36) PRIMARY KEY,
    tenant_id CHAR(36) NOT NULL,
    order_number VARCHAR(30) NOT NULL,
    customer_id CHAR(36) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    total_amount DECIMAL(12,2) NOT NULL DEFAULT 0.00,
    notes TEXT,
    created_by CHAR(36) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    version INT NOT NULL DEFAULT 0,
    
    CONSTRAINT fk_order_tenant FOREIGN KEY (tenant_id) REFERENCES tenant(id),
    CONSTRAINT fk_order_customer FOREIGN KEY (customer_id) REFERENCES customer(id),
    CONSTRAINT fk_order_created_by FOREIGN KEY (created_by) REFERENCES user(id),
    CONSTRAINT fk_order_status CHECK (status IN ('DRAFT', 'SUBMITTED', 'APPROVED', 'PICKING', 'SHIPPED', 'DELIVERED', 'REJECTED', 'CANCELLED')),
    CONSTRAINT uq_order_number UNIQUE (tenant_id, order_number)
);

-- Indexes for order table
CREATE INDEX idx_order_tenant_customer ON `order`(tenant_id, customer_id);
CREATE INDEX idx_order_tenant_status ON `order`(tenant_id, status);
CREATE INDEX idx_order_tenant_created ON `order`(tenant_id, created_at);

-- Order items table
CREATE TABLE order_item (
    id CHAR(36) PRIMARY KEY,
    order_id CHAR(36) NOT NULL,
    product_id CHAR(36) NOT NULL,
    quantity INT NOT NULL,
    unit_price DECIMAL(12,2) NOT NULL,
    total_price DECIMAL(12,2) NOT NULL,
    
    CONSTRAINT fk_order_item_order FOREIGN KEY (order_id) REFERENCES `order`(id) ON DELETE CASCADE,
    CONSTRAINT fk_order_item_product FOREIGN KEY (product_id) REFERENCES product(id),
    CONSTRAINT chk_order_item_quantity CHECK (quantity > 0),
    CONSTRAINT chk_order_item_unit_price CHECK (unit_price >= 0),
    CONSTRAINT chk_order_item_total_price CHECK (total_price >= 0),
    CONSTRAINT uq_order_item_product UNIQUE (order_id, product_id)
);

-- Index for order items
CREATE INDEX idx_order_item_order ON order_item(order_id);
CREATE INDEX idx_order_item_product ON order_item(product_id);

-- Order status history table
-- NOTE: ON DELETE RESTRICT prevents deletion of orders with history, preserving audit trail.
-- In production, consider implementing soft-delete on orders instead of physical deletion.
CREATE TABLE order_status_history (
    id CHAR(36) PRIMARY KEY,
    order_id CHAR(36) NOT NULL,
    from_status VARCHAR(20),
    to_status VARCHAR(20) NOT NULL,
    changed_by CHAR(36) NOT NULL,
    changed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    notes TEXT,
    
    CONSTRAINT fk_order_history_order FOREIGN KEY (order_id) REFERENCES `order`(id) ON DELETE RESTRICT,
    CONSTRAINT fk_order_history_changed_by FOREIGN KEY (changed_by) REFERENCES user(id),
    CONSTRAINT fk_order_history_from_status CHECK (from_status IS NULL OR from_status IN ('DRAFT', 'SUBMITTED', 'APPROVED', 'PICKING', 'SHIPPED', 'DELIVERED', 'REJECTED', 'CANCELLED')),
    CONSTRAINT fk_order_history_to_status CHECK (to_status IN ('DRAFT', 'SUBMITTED', 'APPROVED', 'PICKING', 'SHIPPED', 'DELIVERED', 'REJECTED', 'CANCELLED'))
);

-- Index for order status history
CREATE INDEX idx_order_history_order ON order_status_history(order_id, changed_at);
