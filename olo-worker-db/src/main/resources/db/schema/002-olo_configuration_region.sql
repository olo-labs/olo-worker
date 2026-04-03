-- Copyright (c) 2026 Olo Labs. All rights reserved.

-- 002: Tenant ID → region mapping. Cached in Redis hash olo:worker:tenant:region (HSET/HGET).

-- DROP TABLE IF EXISTS olo_configuration_region;

CREATE TABLE IF NOT EXISTS olo_configuration_region (
    tenant_id  VARCHAR(64) PRIMARY KEY,
    region     VARCHAR(64) NOT NULL DEFAULT 'default',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_olo_configuration_region_region
ON olo_configuration_region(region);

INSERT INTO olo_configuration_region (tenant_id, region) VALUES
    ('default', 'default'),
    ('tenant-a', 'default'),
    ('tenant-b', 'us-east'),
    ('tenant-c', 'eu-west')
ON CONFLICT (tenant_id) DO NOTHING;
