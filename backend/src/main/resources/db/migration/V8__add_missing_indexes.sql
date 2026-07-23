-- V8: Add missing composite tenant indexes

-- stock_movement: composite index for movement history queries by tenant + product
CREATE INDEX idx_stock_movement_tenant_product ON stock_movement (tenant_id, product_id);

-- product: category filter joins on (tenant_id, category_id)
CREATE INDEX idx_product_tenant_category ON product (tenant_id, category_id);

-- product: status filter (soft-delete exclusion)
CREATE INDEX idx_product_tenant_status ON product (tenant_id, status);
