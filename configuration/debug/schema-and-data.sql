-- Copyright (c) 2026 Olo Labs. All rights reserved.

-- Ready-made schema and sample data for local/debug bootstrap.
-- Run this against your PostgreSQL database (e.g. psql -f schema-and-data.sql).
-- See: docs/architecture/bootstrap/how-to-debug.md

-- ---------------------------------------------------------------------------
-- 1. Tenant ID → region mapping (worker reads this; cached in Redis)
-- ---------------------------------------------------------------------------
DROP TABLE IF EXISTS olo_configuration_region;

CREATE TABLE olo_configuration_region (
    tenant_id  VARCHAR(64) PRIMARY KEY,
    region     VARCHAR(64) NOT NULL DEFAULT 'default',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_olo_configuration_region_region ON olo_configuration_region(region);

INSERT INTO olo_configuration_region (tenant_id, region) VALUES
    ('tenant-a', 'default'),
    ('tenant-b', 'us-east'),
    ('tenant-c', 'eu-west');

-- ---------------------------------------------------------------------------
-- 2. Per-tenant config overrides (admin uses to build snapshot)
-- ---------------------------------------------------------------------------
DROP TABLE IF EXISTS olo_configuration_tenant;

CREATE TABLE olo_configuration_tenant (
    tenant_id    VARCHAR(64) NOT NULL,
    config_key   VARCHAR(200) NOT NULL,
    config_value JSONB,
    updated_at   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (tenant_id, config_key)
);

CREATE INDEX idx_olo_configuration_tenant_tenant ON olo_configuration_tenant(tenant_id);

INSERT INTO olo_configuration_tenant (tenant_id, config_key, config_value) VALUES
    ('tenant-a', 'feature.debug', 'true'),
    ('tenant-a', 'pipeline.timeout', '15');

-- ---------------------------------------------------------------------------
-- 3. Resource-scoped configuration (admin uses to build snapshot)
-- tenant_id '' = global resource
-- ---------------------------------------------------------------------------
DROP TABLE IF EXISTS olo_config_resource;

CREATE TABLE olo_config_resource (
    resource_id   VARCHAR(255) NOT NULL,
    tenant_id     VARCHAR(64) NOT NULL DEFAULT '',
    region        VARCHAR(64) NOT NULL DEFAULT 'default',
    config_json   TEXT NOT NULL,
    updated_at    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (resource_id, tenant_id, region)
);

-- Global (per-region) + tenant overrides for a simple sample key.
-- Merge order in ConfigurationSnapshot: global (tenant_id = '') → tenant (tenant_id != '').
INSERT INTO olo_config_resource (resource_id, tenant_id, region, config_json) VALUES
    -- Region=default, global defaults
    ('core:sample', '', 'default', '{
      "app": { "theme": "light", "timeoutSecs": 30 },
      "feature": { "beta": false }
    }'),
    -- Region=us-east, regional overrides (take effect for all tenants in us-east unless tenant override exists)
    ('core:sample', '', 'us-east', '{
      "app": { "theme": "dark", "timeoutSecs": 25 },
      "feature": { "beta": true }
    }'),
    -- Region=us-east, tenant-specific override for tenant-a (overrides global/us-east for this tenant only)
    ('core:sample', 'tenant-a', 'us-east', '{
      "app": { "timeoutSecs": 10 }
    }'),
    -- Region=default, additional tenant-specific override for tenant-a (example for config cache tenant overrides)
    ('core:sample', 'tenant-a', 'default', '{
      "feature": { "beta": true }
    }'),
    -- Existing model example (global, default region)
    ('model:gpt4', '', 'default', '{"provider":"openai","model":"gpt-4"}');

-- ---------------------------------------------------------------------------
-- 4. Worker-level configuration (optional; admin can cache in Redis)
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS olo_worker_configuration (
    config_key   VARCHAR(255) PRIMARY KEY,
    config_value TEXT,
    updated_at   TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO olo_worker_configuration (config_key, config_value) VALUES
    ('temporal.target', 'localhost:47233'),
    ('temporal.namespace', 'default'),
    ('temporal.task_queue', 'olo-chat-queue-ollama')
ON CONFLICT (config_key) DO NOTHING;
