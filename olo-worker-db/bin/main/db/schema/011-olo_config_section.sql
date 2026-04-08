-- Copyright (c) 2026 Olo Labs. All rights reserved.

-- 011: Regional configuration sections mirrored to Redis (read-through cache).
-- Authoritative copy lives in PostgreSQL; workers copy to olo:config:queues:<region> and olo:config:profiles:<region> when missing in Redis.
--
-- section_name:
--   'queues'   — JSON object: queue id -> definition (e.g. { "taskQueue": "olo-fast-queue" }).
--   'profiles' — JSON object: same root shape as pipeline chatProfiles (profileOrder, profiles, ...).
--
-- If no row exists for a section, bootstrap may still populate Redis by deriving from olo_pipeline_template.tree_json
-- (e.g. chatProfiles embedded in a pipeline document).

CREATE TABLE IF NOT EXISTS olo_config_section (
    region       VARCHAR(64)  NOT NULL,
    section_name VARCHAR(32)  NOT NULL,
    json_document JSONB       NOT NULL,
    updated_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (region, section_name)
);

CREATE INDEX IF NOT EXISTS idx_olo_config_section_region
ON olo_config_section(region);
