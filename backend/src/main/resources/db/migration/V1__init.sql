-- Foundation: tenant, user, and refresh_token tables

CREATE TABLE tenant (
    id         CHAR(36)                      NOT NULL,
    name       VARCHAR(100)                  NOT NULL,
    slug       VARCHAR(50)                   NOT NULL,
    status     ENUM('ACTIVE', 'SUSPENDED')   NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP                     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_tenant_slug (slug)
);

CREATE TABLE user (
    id            CHAR(36)                                              NOT NULL,
    tenant_id     CHAR(36)                                              NOT NULL,
    email         VARCHAR(150)                                          NOT NULL,
    password_hash VARCHAR(255)                                          NOT NULL,
    first_name    VARCHAR(80),
    last_name     VARCHAR(80),
    role          ENUM('ADMIN', 'MANAGER', 'WAREHOUSE_STAFF', 'VIEWER') NOT NULL,
    status        ENUM('ACTIVE', 'INACTIVE')                           NOT NULL DEFAULT 'ACTIVE',
    created_at    TIMESTAMP                                             NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMP                                             NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_email (email),
    CONSTRAINT fk_user_tenant FOREIGN KEY (tenant_id) REFERENCES tenant (id)
);

CREATE TABLE refresh_token (
    id         CHAR(36)     NOT NULL,
    user_id    CHAR(36)     NOT NULL,
    tenant_id  CHAR(36)     NOT NULL,
    token_hash VARCHAR(255) NOT NULL,
    expires_at TIMESTAMP    NOT NULL,
    revoked    BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_refresh_token_hash (token_hash),
    CONSTRAINT fk_refresh_token_user   FOREIGN KEY (user_id)   REFERENCES user (id),
    CONSTRAINT fk_refresh_token_tenant FOREIGN KEY (tenant_id) REFERENCES tenant (id)
);
