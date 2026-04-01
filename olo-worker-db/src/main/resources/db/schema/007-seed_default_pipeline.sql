-- Copyright (c) 2026 Olo Labs. All rights reserved.

-- 007: Seed default region pipeline (default-pipeline). Run after 005 and 006.
-- Idempotent: ON CONFLICT DO NOTHING.
--
-- Execution defaults (engine, temporal, activity timeouts) are in tree_json for the pipeline;
-- at runtime they may be overridden by global/region config (olo_config_resource or env).

INSERT INTO olo_pipeline_template (region, pipeline_id, version, is_active, tree_json, updated_at)
VALUES (
  'default',
  'default-pipeline',
  1,
  true,
  '{
    "id": "default-pipeline",
    "name": "default-pipeline",
    "version": 1,
    "workflowId": "default",
    "description": "Bootstrap default pipeline",
    "inputContract": { "strict": false, "parameters": [] },
    "variableRegistry": [],
    "scope": { "plugins": [], "features": [] },
    "executionTree": {
      "id": "root",
      "displayName": "Pipeline",
      "type": "SEQUENCE",
      "children": []
    },
    "outputContract": { "parameters": [] },
    "resultMapping": []
  }'::jsonb,
  CURRENT_TIMESTAMP
)
ON CONFLICT (region, pipeline_id, version) DO NOTHING;
