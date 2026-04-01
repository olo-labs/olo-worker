-- Copyright (c) 2026 Olo Labs. All rights reserved.

-- 003: Per-tenant config overrides (tenant_id, config_key, config_value). Part of snapshot.
-- config_value JSONB: store native JSON so snapshot builder does not need parsing (e.g. model.settings = {"temperature":0.3}).

-- DROP TABLE IF EXISTS olo_configuration_tenant;

CREATE TABLE IF NOT EXISTS olo_configuration_tenant (
    tenant_id    VARCHAR(64) NOT NULL,
    config_key   VARCHAR(200) NOT NULL,
    config_value JSONB,
    updated_at   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (tenant_id, config_key)
);

CREATE INDEX IF NOT EXISTS idx_olo_configuration_tenant_tenant
ON olo_configuration_tenant(tenant_id);

-- Example rows (tenant, key, value):
-- tenantA  feature.debug       true
-- tenantA  pipeline.timeout     15
-- tenantA  model.settings       {"temperature":0.3}
-- INSERT INTO olo_configuration_tenant (tenant_id, config_key, config_value) VALUES
-- ('tenantA', 'feature.debug', 'true'),
-- ('tenantA', 'pipeline.timeout', '15'),
-- ('tenantA', 'model.settings', '{"temperature":0.3}');
