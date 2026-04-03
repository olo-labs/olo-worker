-- Copyright (c) 2026 Olo Labs. All rights reserved.

-- Idempotent schema and default data for bootstrap integration test.
-- Executed by BootstrapGlobalContextDumpTest when DB is configured.
-- Creates tables only if they do not exist; inserts default rows only when absent.
-- See: docs/architecture/bootstrap/how-to-debug.md

-- ---------------------------------------------------------------------------
-- 1. Tenant ID → region mapping (worker reads this; cached in Redis)
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS olo_configuration_region (
    tenant_id  VARCHAR(64) PRIMARY KEY,
    region     VARCHAR(64) NOT NULL DEFAULT 'default',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_olo_configuration_region_region
ON olo_configuration_region(region);

INSERT INTO olo_configuration_region (tenant_id, region) VALUES
    ('tenant-a', 'default'),
    ('tenant-b', 'us-east'),
    ('tenant-c', 'eu-west')
ON CONFLICT (tenant_id) DO NOTHING;

-- ---------------------------------------------------------------------------
-- 2. Resource-scoped configuration (snapshot source for DB → Redis)
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS olo_config_resource (
    resource_id   VARCHAR(255) NOT NULL,
    tenant_id     VARCHAR(64) NOT NULL DEFAULT '',
    region        VARCHAR(64) NOT NULL DEFAULT 'default',
    config_json   TEXT NOT NULL,
    updated_at    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (resource_id, tenant_id, region)
);

-- Sample config precedence for the same logical config key:
-- global (per-region) → regional (different region snapshot) → tenant-specific.
INSERT INTO olo_config_resource (resource_id, tenant_id, region, config_json) VALUES
    -- Region=default, global defaults
    ('core:sample', '', 'default', '{
      "app": { "theme": "light", "timeoutSecs": 30 },
      "feature": { "beta": false }
    }'),
    -- Region=us-east, regional defaults (for all tenants in us-east)
    ('core:sample', '', 'us-east', '{
      "app": { "theme": "dark", "timeoutSecs": 25 },
      "feature": { "beta": true }
    }'),
    -- Region=us-east, tenant-specific override for tenant-a
    ('core:sample', 'tenant-a', 'us-east', '{
      "app": { "timeoutSecs": 10 }
    }'),
    -- Primary region and served regions (comma-separated) so bootstrap creates olo:config:pipelines:<region> for each
    ('olo.region', '', 'default', '{"olo.region":"default,us-east,eu-west"}')
ON CONFLICT (resource_id, tenant_id, region) DO NOTHING;

-- ---------------------------------------------------------------------------
-- 3. Region pipeline template (005) + tenant override (006)
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS olo_pipeline_template (
    region      VARCHAR(64)  NOT NULL,
    pipeline_id VARCHAR(255) NOT NULL,
    version     BIGINT       NOT NULL,
    is_active   BOOLEAN      NOT NULL DEFAULT true,
    tree_json   JSONB        NOT NULL,
    checksum    VARCHAR(64),
    updated_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (region, pipeline_id, version)
);

CREATE INDEX IF NOT EXISTS idx_olo_pipeline_template_region
ON olo_pipeline_template(region);

CREATE INDEX IF NOT EXISTS idx_olo_pipeline_template_region_pipeline
ON olo_pipeline_template(region, pipeline_id);

CREATE INDEX IF NOT EXISTS idx_olo_pipeline_template_active
ON olo_pipeline_template(region, pipeline_id, is_active);

CREATE TABLE IF NOT EXISTS olo_tenant_pipeline_override (
    tenant_id        VARCHAR(64)  NOT NULL,
    pipeline_id      VARCHAR(255) NOT NULL,
    pipeline_version BIGINT       NOT NULL,
    override_json    JSONB        NOT NULL,
    updated_at       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (tenant_id, pipeline_id, pipeline_version)
);

CREATE INDEX IF NOT EXISTS idx_olo_tenant_pipeline_override_tenant
ON olo_tenant_pipeline_override(tenant_id);

CREATE INDEX IF NOT EXISTS idx_olo_tenant_pipeline_override_pipeline
ON olo_tenant_pipeline_override(pipeline_id, pipeline_version);

-- Reset sample pipelines so inserts below always reflect current IDs, even on rerun.
DELETE FROM olo_pipeline_template WHERE region IN ('default', 'us-east', 'eu-west');
DELETE FROM olo_tenant_pipeline_override;

-- Default region: consensus pipeline (configuration/debug/consensus-pipeline.json) + second sample pipeline
INSERT INTO olo_pipeline_template (region, pipeline_id, version, is_active, tree_json, updated_at)
VALUES
  (
    'default',
    'olo.default.consensus-pipeline',
    1,
    true,
    '{"id":"olo.default.consensus-pipeline","name":"Multi-Model Consensus Pipeline","version":1,"description":"Architect and Critic models discuss until consensus. Planner uses consensus_subtree_creator.","inputContract":{"strict":false,"parameters":[]},"variableRegistry":[{"name":"user_query","type":"string","scope":"IN"}],"scope":{"plugins":{"architect":{"contractType":"MODEL_EXECUTOR"},"critic":{"contractType":"MODEL_EXECUTOR"},"consensus_subtree_creator":{"contractType":"SUBTREE_CREATOR"}},"features":[]},"executionTree":{"id":"root","displayName":"Consensus pipeline","type":"SEQUENCE","children":[{"id":"planner-consensus","type":"PLANNER","displayName":"Consensus planner","params":{"planInputVariable":"user_query","subtreeCreatorPluginRef":"consensus_subtree_creator"},"children":[]}]},"outputContract":{"parameters":[]},"resultMapping":[{"variable":"__planner_last_response"}],"allowedTenantIds":["tenant-a","default"],"isDebugPipeline":true,"isDynamicPipeline":true}'::jsonb,
    CURRENT_TIMESTAMP
  ),
  (
    'default',
    'olo.default.default-pipeline-2',
    1,
    true,
    '{"id":"olo.default.default-pipeline-2","name":"olo.default.default-pipeline-2","version":1,"workflowId":"default-2","description":"Second default pipeline for testing","inputContract":{"strict":false,"parameters":[]},"variableRegistry":[],"scope":{"plugins":[],"features":[]},"executionTree":{"id":"root","displayName":"Pipeline2","type":"SEQUENCE","children":[]},"outputContract":{"parameters":[]},"resultMapping":[],"allowedTenantIds":["tenant-a"],"isDebugPipeline":false,"isDynamicPipeline":true}'::jsonb,
    CURRENT_TIMESTAMP
  )
ON CONFLICT (region, pipeline_id, version) DO NOTHING;

-- us-east region: two sample pipelines
INSERT INTO olo_pipeline_template (region, pipeline_id, version, is_active, tree_json, updated_at)
VALUES
  (
    'us-east',
    'olo.us-east.us-east-pipeline-1',
    1,
    true,
    '{"id":"olo.us-east.us-east-pipeline-1","name":"olo.us-east.us-east-pipeline-1","version":1,"workflowId":"us-east-1","description":"us-east sample pipeline 1","inputContract":{"strict":false,"parameters":[]},"variableRegistry":[],"scope":{"plugins":[],"features":[]},"executionTree":{"id":"root","displayName":"UsEast1","type":"SEQUENCE","children":[]},"outputContract":{"parameters":[]},"resultMapping":[],"allowedTenantIds":["tenant-b"],"isDebugPipeline":false,"isDynamicPipeline":true}'::jsonb,
    CURRENT_TIMESTAMP
  ),
  (
    'us-east',
    'olo.us-east.us-east-pipeline-2',
    1,
    true,
    '{"id":"olo.us-east.us-east-pipeline-2","name":"olo.us-east.us-east-pipeline-2","version":1,"workflowId":"us-east-2","description":"us-east sample pipeline 2","inputContract":{"strict":false,"parameters":[]},"variableRegistry":[],"scope":{"plugins":[],"features":[]},"executionTree":{"id":"root","displayName":"UsEast2","type":"SEQUENCE","children":[]},"outputContract":{"parameters":[]},"resultMapping":[],"allowedTenantIds":["tenant-b"],"isDebugPipeline":false,"isDynamicPipeline":true}'::jsonb,
    CURRENT_TIMESTAMP
  )
ON CONFLICT (region, pipeline_id, version) DO NOTHING;

-- eu-west region: two sample pipelines
INSERT INTO olo_pipeline_template (region, pipeline_id, version, is_active, tree_json, updated_at)
VALUES
  (
    'eu-west',
    'olo.eu-west.eu-west-pipeline-1',
    1,
    true,
    '{"id":"olo.eu-west.eu-west-pipeline-1","name":"olo.eu-west.eu-west-pipeline-1","version":1,"workflowId":"eu-west-1","description":"eu-west sample pipeline 1","inputContract":{"strict":false,"parameters":[]},"variableRegistry":[],"scope":{"plugins":[],"features":[]},"executionTree":{"id":"root","displayName":"EuWest1","type":"SEQUENCE","children":[]},"outputContract":{"parameters":[]},"resultMapping":[],"allowedTenantIds":["tenant-c"],"isDebugPipeline":false,"isDynamicPipeline":true}'::jsonb,
    CURRENT_TIMESTAMP
  ),
  (
    'eu-west',
    'olo.eu-west.eu-west-pipeline-2',
    1,
    true,
    '{"id":"olo.eu-west.eu-west-pipeline-2","name":"olo.eu-west.eu-west-pipeline-2","version":1,"workflowId":"eu-west-2","description":"eu-west sample pipeline 2","inputContract":{"strict":false,"parameters":[]},"variableRegistry":[],"scope":{"plugins":[],"features":[]},"executionTree":{"id":"root","displayName":"EuWest2","type":"SEQUENCE","children":[]},"outputContract":{"parameters":[]},"resultMapping":[],"allowedTenantIds":["tenant-c"],"isDebugPipeline":false,"isDynamicPipeline":true}'::jsonb,
    CURRENT_TIMESTAMP
  )
ON CONFLICT (region, pipeline_id, version) DO NOTHING;

-- ---------------------------------------------------------------------------
-- 4. Capabilities (global, region, tenant)
-- ---------------------------------------------------------------------------
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

-- Sample capabilities matching the debug JSON shape.
INSERT INTO olo_capabilities (scope, tenant_id, region, plugins, features) VALUES
    -- Global capabilities (environment-wide defaults)
    ('GLOBAL', '', 'default', '["http"]'::jsonb, '["retry"]'::jsonb),
    -- Region-level capabilities
    ('REGION', '', 'us-east', '["http","sql"]'::jsonb, '["retry","circuitBreaker"]'::jsonb),
    -- Tenant-level capabilities
    ('TENANT', 'tenant-a', 'default', '["http"]'::jsonb, '["retry"]'::jsonb),
    ('TENANT', 'tenant-b', 'us-east', '["http","sql"]'::jsonb, '["retry","circuitBreaker"]'::jsonb)
ON CONFLICT (scope, tenant_id, region) DO NOTHING;
