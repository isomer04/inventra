-- Inventory and stock movement tables

-- InventoryItem table
CREATE TABLE inventory_item (
    id CHAR(36) PRIMARY KEY,
    tenant_id CHAR(36) NOT NULL,
    product_id CHAR(36) NOT NULL,
    quantity_on_hand INT NOT NULL DEFAULT 0,
    quantity_reserved INT NOT NULL DEFAULT 0,
    reorder_point INT NOT NULL DEFAULT 0,
    version INT NOT NULL DEFAULT 0,
    last_updated TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    
    CONSTRAINT fk_inventory_tenant FOREIGN KEY (tenant_id) REFERENCES tenant(id),
    CONSTRAINT fk_inventory_product FOREIGN KEY (product_id) REFERENCES product(id),
    CONSTRAINT uq_inventory_product UNIQUE (tenant_id, product_id),
    CONSTRAINT chk_quantity_on_hand CHECK (quantity_on_hand >= 0),
    CONSTRAINT chk_quantity_reserved CHECK (quantity_reserved >= 0),
    CONSTRAINT chk_reorder_point CHECK (reorder_point >= 0)
);

CREATE INDEX idx_inventory_tenant ON inventory_item(tenant_id);
CREATE INDEX idx_inventory_product ON inventory_item(product_id);
CREATE INDEX idx_inventory_low_stock ON inventory_item(tenant_id, quantity_on_hand, reorder_point);

-- StockMovement table
CREATE TABLE stock_movement (
    id CHAR(36) PRIMARY KEY,
    tenant_id CHAR(36) NOT NULL,
    product_id CHAR(36) NOT NULL,
    type ENUM('RECEIPT', 'ADJUSTMENT', 'RESERVATION', 'RESERVATION_RELEASE', 'DEDUCTION') NOT NULL,
    quantity INT NOT NULL,
    reference_id CHAR(36),
    reference_type VARCHAR(50),
    notes TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by CHAR(36) NOT NULL,
    
    CONSTRAINT fk_movement_tenant FOREIGN KEY (tenant_id) REFERENCES tenant(id),
    CONSTRAINT fk_movement_product FOREIGN KEY (product_id) REFERENCES product(id),
    CONSTRAINT fk_movement_user FOREIGN KEY (created_by) REFERENCES user(id)
);

CREATE INDEX idx_movement_tenant ON stock_movement(tenant_id);
CREATE INDEX idx_movement_product ON stock_movement(product_id);
CREATE INDEX idx_movement_type ON stock_movement(type);
CREATE INDEX idx_movement_created_at ON stock_movement(created_at);
CREATE INDEX idx_movement_reference ON stock_movement(reference_id, reference_type);
