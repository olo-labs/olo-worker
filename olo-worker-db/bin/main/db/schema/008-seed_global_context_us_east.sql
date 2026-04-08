-- Copyright (c) 2026 Olo Labs. All rights reserved.

-- 008: Seed global context: us-east region, pipelines (order-processing v2, settlement v1),
--      tenants (tenant-A, tenant-B → us-east), overrides (tenant-A → order-processing v2).
-- Idempotent: ON CONFLICT DO NOTHING (templates, overrides); DO UPDATE (tenant→region so seed wins).
--
-- GlobalContext
--   Regions
--     us-east
--       pipelines: order-processing v2, settlement v1
--   Tenants
--     tenant-A → region us-east
--     tenant-B → region us-east
--   Overrides
--     tenant-A → order-processing v2

-- ---------------------------------------------------------------------------
-- Tenants: tenant-A, tenant-B → us-east
-- ---------------------------------------------------------------------------
INSERT INTO olo_configuration_region (tenant_id, region, updated_at)
VALUES
  ('tenant-A', 'us-east', CURRENT_TIMESTAMP),
  ('tenant-B', 'us-east', CURRENT_TIMESTAMP)
ON CONFLICT (tenant_id) DO UPDATE SET region = EXCLUDED.region, updated_at = EXCLUDED.updated_at;

-- ---------------------------------------------------------------------------
-- Region us-east: pipelines order-processing v2 (active), settlement v1 (active)
-- ---------------------------------------------------------------------------
INSERT INTO olo_pipeline_template (region, pipeline_id, version, is_active, tree_json, updated_at)
VALUES
  (
    'us-east',
    'order-processing',
    2,
    true,
    '{"id":"order-processing","name":"order-processing","version":2,"workflowId":"order-processing","description":"Order processing pipeline v2","inputContract":{"strict":false,"parameters":[]},"variableRegistry":[],"scope":{"plugins":[],"features":[]},"executionTree":{"id":"root","displayName":"OrderProcessing","type":"SEQUENCE","children":[]},"outputContract":{"parameters":[]},"resultMapping":[]}'::jsonb,
    CURRENT_TIMESTAMP
  ),
  (
    'us-east',
    'settlement',
    1,
    true,
    '{"id":"settlement","name":"settlement","version":1,"workflowId":"settlement","description":"Settlement pipeline v1","inputContract":{"strict":false,"parameters":[]},"variableRegistry":[],"scope":{"plugins":[],"features":[]},"executionTree":{"id":"root","displayName":"Settlement","type":"SEQUENCE","children":[]},"outputContract":{"parameters":[]},"resultMapping":[]}'::jsonb,
    CURRENT_TIMESTAMP
  )
ON CONFLICT (region, pipeline_id, version) DO NOTHING;

-- ---------------------------------------------------------------------------
-- Overrides: tenant-A → order-processing v2
-- ---------------------------------------------------------------------------
INSERT INTO olo_tenant_pipeline_override (tenant_id, pipeline_id, pipeline_version, override_json, updated_at)
VALUES (
  'tenant-A',
  'order-processing',
  2,
  '{}'::jsonb,
  CURRENT_TIMESTAMP
)
ON CONFLICT (tenant_id, pipeline_id, pipeline_version) DO NOTHING;
