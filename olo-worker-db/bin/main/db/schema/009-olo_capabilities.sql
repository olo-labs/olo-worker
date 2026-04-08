-- Copyright (c) 2026 Olo Labs. All rights reserved.

-- Capabilities configuration: global, region, and tenant scopes.
-- Scope values:
--   GLOBAL — environment-wide capabilities (tenant_id='', region='default' by convention)
--   REGION — region-level capabilities (tenant_id='')
--   TENANT — tenant-level capabilities (tenant_id set, region is tenant's region)
--
-- Plugins and features are stored as JSON arrays of strings.

CREATE TABLE IF NOT EXISTS olo_capabilities (
    scope      VARCHAR(16)  NOT NULL,
    tenant_id  VARCHAR(64)  NOT NULL DEFAULT '',
    region     VARCHAR(64)  NOT NULL DEFAULT 'default',
    plugins    JSONB        NOT NULL,
    features   JSONB        NOT NULL,
    updated_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (scope, tenant_id, region)
);

CREATE INDEX IF NOT EXISTS idx_olo_capabilities_scope
ON olo_capabilities(scope);

CREATE INDEX IF NOT EXISTS idx_olo_capabilities_region
ON olo_capabilities(region);

CREATE INDEX IF NOT EXISTS idx_olo_capabilities_tenant
ON olo_capabilities(tenant_id);

