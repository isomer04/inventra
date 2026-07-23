-- Customer table
-- Creates customer table for storing customer information per tenant
-- Note: charset/collation intentionally omitted to inherit server default,
-- matching the charset of the tenant table (see V1__init.sql).

CREATE TABLE customer (
    id CHAR(36) PRIMARY KEY COMMENT 'UUID primary key',
    tenant_id CHAR(36) NOT NULL COMMENT 'Foreign key to tenant table',
    name VARCHAR(200) NOT NULL COMMENT 'Customer name',
    email VARCHAR(150) COMMENT 'Customer email address (optional)',
    phone VARCHAR(30) COMMENT 'Customer phone number (optional)',
    address TEXT COMMENT 'Customer address (optional)',
    notes TEXT COMMENT 'Additional notes about customer',
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' COMMENT 'Customer status: ACTIVE or INACTIVE',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Record creation timestamp',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Record last update timestamp',
    
    CONSTRAINT fk_customer_tenant FOREIGN KEY (tenant_id) REFERENCES tenant(id) ON DELETE CASCADE,
    CONSTRAINT chk_customer_status CHECK (status IN ('ACTIVE', 'INACTIVE'))
) ENGINE=InnoDB COMMENT='Customer directory scoped per tenant';

-- Indexes for performance
CREATE INDEX idx_customer_tenant ON customer(tenant_id);
CREATE INDEX idx_customer_name ON customer(tenant_id, name);
CREATE INDEX idx_customer_email ON customer(tenant_id, email);
CREATE INDEX idx_customer_status ON customer(tenant_id, status);
CREATE INDEX idx_customer_created ON customer(tenant_id, created_at);
