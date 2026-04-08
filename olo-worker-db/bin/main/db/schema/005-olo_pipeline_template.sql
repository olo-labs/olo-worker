-- Copyright (c) 2026 Olo Labs. All rights reserved.

-- 005: Region base pipeline (execution tree template). Multiple versions per (region, pipeline_id).
-- Only one version is active per pipeline; use is_active for safe rollout, rollback, A/B testing.
-- Runtime: SELECT ... WHERE region = ? AND pipeline_id = ? AND is_active = true.
-- Then apply tenant override for same (tenant_id, pipeline_id, pipeline_version).
-- JSONB: validation, indexing, JSON operators, partial queries. checksum: detect tree changes (governance/debug).
--
-- See: olo_tenant_pipeline_override (006) for per-tenant, per-version overrides.

-- DROP TABLE IF EXISTS olo_pipeline_template;

CREATE TABLE IF NOT EXISTS olo_pipeline_template (
    region      VARCHAR(64)  NOT NULL,
    pipeline_id VARCHAR(255) NOT NULL,
    version     BIGINT       NOT NULL,
    is_active   BOOLEAN      NOT NULL DEFAULT true,
    tree_json   JSONB       NOT NULL,
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

-- Example: region us-east, pipeline order-processing v1 (active)
-- INSERT INTO olo_pipeline_template (region, pipeline_id, version, is_active, tree_json, checksum) VALUES
-- ('us-east', 'order-processing', 1, true, '{"id":"root","type":"SEQUENCE","children":[]}'::jsonb, 'sha256...');
-- Rollout v2: insert version 2, then flip is_active; checksum helps detect if execution tree changed.
