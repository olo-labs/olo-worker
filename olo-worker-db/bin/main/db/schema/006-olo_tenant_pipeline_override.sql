-- Copyright (c) 2026 Olo Labs. All rights reserved.

-- 006: Per-tenant, per-version pipeline overrides. Applied on top of region template (olo_pipeline_template).
-- pipeline_version ties override to a specific template version; avoids v1 override applied to v2 template.
-- Runtime merge: template(region, pipeline_id, version) + override(tenant_id, pipeline_id, pipeline_version).
-- JSONB: validation, indexing, JSON operators, partial queries.
--
-- See: olo_pipeline_template (005) for region base; olo_configuration_region (002) for tenant→region.

-- DROP TABLE IF EXISTS olo_tenant_pipeline_override;

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

-- Example: tenant-A, order-processing v1, disable Kafka plugin
-- INSERT INTO olo_tenant_pipeline_override (tenant_id, pipeline_id, pipeline_version, override_json) VALUES
-- ('tenant-a', 'order-processing', 1, '{"plugin.kafka.enabled":false}'::jsonb);
--
-- Example: tenant-B, order-processing v1, add fraud-check plugin
-- INSERT INTO olo_tenant_pipeline_override (tenant_id, pipeline_id, pipeline_version, override_json) VALUES
-- ('tenant-b', 'order-processing', 1, '{"plugins.add":["fraud-check"]}'::jsonb);
