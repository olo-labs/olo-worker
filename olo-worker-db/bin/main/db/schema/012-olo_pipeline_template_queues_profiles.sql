-- Copyright (c) 2026 Olo Labs. All rights reserved.

-- 012: Regional queue definitions and UI profile bindings (decoupled from pipeline execution JSON).
-- Mirrors Redis: olo:config:queues:<region> and olo:config:profiles:<region>.
-- tree_json holds pipeline logic only (no chatProfiles).

ALTER TABLE olo_pipeline_template ADD COLUMN IF NOT EXISTS queues_json JSONB;
ALTER TABLE olo_pipeline_template ADD COLUMN IF NOT EXISTS profiles_json JSONB;
