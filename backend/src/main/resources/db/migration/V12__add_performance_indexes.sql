-- V12: Performance indexes for reports and order history queries.

-- ReportService date-range queries filter on (tenant_id, DATE(created_at)).
-- The existing idx_movement_tenant and idx_movement_created_at are single-column;
-- MySQL can use only one index per table per query. A composite covering index
-- allows a single range scan instead of two index merges.
CREATE INDEX idx_stock_movement_tenant_created
    ON stock_movement (tenant_id, created_at);

-- getAverageFulfillmentTime() self-joins order_status_history on
-- (order_id, to_status = 'SUBMITTED') and (order_id, to_status = 'DELIVERED').
-- The existing idx_order_history_order indexes (order_id, changed_at) but not
-- to_status. A covering index on (order_id, to_status, changed_at) allows
-- both halves of the self-join to be served from the index alone.
CREATE INDEX idx_order_history_order_status
    ON order_status_history (order_id, to_status, changed_at);

-- CategoryService.delete() calls countByParentIdAndTenantId(id, tenantId).
-- There is no index on (tenant_id, parent_id); the query falls back to a
-- full idx_category_tenant scan filtered by parent_id. A composite index
-- makes the count fast for large category trees.
CREATE INDEX idx_category_tenant_parent
    ON category (tenant_id, parent_id);
