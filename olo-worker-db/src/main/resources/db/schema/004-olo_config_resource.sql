-- Copyright (c) 2026 Olo Labs. All rights reserved.

-- 004: Resource-scoped configuration. One row per resource (optionally per tenant/region).
-- Used by ConfigSnapshotBuilder to build region snapshot: group by tenant, merge global + tenant, store in Redis.
--
-- tenant_id NOT NULL: NULL in a PK breaks uniqueness in PostgreSQL. Use '' for global (non-tenant) resources.

-- DROP TABLE IF EXISTS olo_config_resource;

CREATE TABLE IF NOT EXISTS olo_config_resource (
    resource_id   VARCHAR(255) NOT NULL,
    tenant_id     VARCHAR(64) NOT NULL DEFAULT '',
    region        VARCHAR(64) NOT NULL DEFAULT 'default',
    config_json   TEXT NOT NULL,
    updated_at    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (resource_id, tenant_id, region)
);

-- Example: global model config (tenant_id '' = global)
INSERT INTO olo_config_resource
(resource_id, tenant_id, region, config_json)
VALUES
('model:gpt4', '', 'default', '{"provider":"openai","model":"gpt-4"}')
ON CONFLICT (resource_id, tenant_id, region) DO NOTHING;
