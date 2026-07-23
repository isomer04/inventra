-- Product and category tables

CREATE TABLE category (
    id         CHAR(36)     NOT NULL,
    tenant_id  CHAR(36)     NOT NULL,
    name       VARCHAR(100) NOT NULL,
    parent_id  CHAR(36),
    created_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_category_tenant_id (tenant_id, id),
    CONSTRAINT fk_category_tenant FOREIGN KEY (tenant_id) REFERENCES tenant (id),
    CONSTRAINT fk_category_parent FOREIGN KEY (parent_id) REFERENCES category (id)
);

CREATE TABLE product (
    id              CHAR(36)                       NOT NULL,
    tenant_id       CHAR(36)                       NOT NULL,
    sku             VARCHAR(100)                   NOT NULL,
    name            VARCHAR(200)                   NOT NULL,
    description     TEXT,
    category_id     CHAR(36),
    unit_price      DECIMAL(12,2)                  NOT NULL,
    unit_of_measure VARCHAR(30),
    status          ENUM('ACTIVE', 'DISCONTINUED') NOT NULL DEFAULT 'ACTIVE',
    created_at      TIMESTAMP                      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP                      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_product_tenant_sku (tenant_id, sku),
    CONSTRAINT fk_product_tenant   FOREIGN KEY (tenant_id)   REFERENCES tenant (id),
    CONSTRAINT fk_product_category_tenant FOREIGN KEY (tenant_id, category_id) REFERENCES category (tenant_id, id)
);
