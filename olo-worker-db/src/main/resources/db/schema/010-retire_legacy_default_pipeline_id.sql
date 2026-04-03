-- Copyright (c) 2026 Olo Labs. All rights reserved.

-- 010: Remove legacy seed id default-pipeline (replaced by olo.default.consensus-pipeline in 007).
-- Safe if row absent. Upsert consensus pipeline JSON for upgrades from DBs that already ran old 007.

DELETE FROM olo_pipeline_template
WHERE region = 'default' AND pipeline_id = 'default-pipeline';

INSERT INTO olo_pipeline_template (region, pipeline_id, version, is_active, tree_json, updated_at)
VALUES (
  'default',
  'olo.default.consensus-pipeline',
  1,
  true,
  $json$
{
  "id": "olo.default.consensus-pipeline",
  "name": "Multi-Model Consensus Pipeline",
  "version": 1,
  "description": "Architect and Critic models discuss until consensus. Planner uses consensus_subtree_creator.",
  "inputContract": { "strict": false, "parameters": [] },
  "variableRegistry": [
    { "name": "user_query", "type": "string", "scope": "IN" }
  ],
  "scope": {
    "plugins": {
      "architect": { "contractType": "MODEL_EXECUTOR" },
      "critic": { "contractType": "MODEL_EXECUTOR" },
      "consensus_subtree_creator": { "contractType": "SUBTREE_CREATOR" }
    },
    "features": []
  },
  "executionTree": {
    "id": "root",
    "displayName": "Consensus pipeline",
    "type": "SEQUENCE",
    "children": [
      {
        "id": "planner-consensus",
        "type": "PLANNER",
        "displayName": "Consensus planner",
        "params": {
          "planInputVariable": "user_query",
          "subtreeCreatorPluginRef": "consensus_subtree_creator"
        },
        "children": []
      }
    ]
  },
  "outputContract": { "parameters": [] },
  "resultMapping": [ { "variable": "__planner_last_response" } ],
  "allowedTenantIds": ["tenant-a", "default"],
  "isDebugPipeline": true,
  "isDynamicPipeline": true
}
$json$::jsonb,
  CURRENT_TIMESTAMP
)
ON CONFLICT (region, pipeline_id, version) DO UPDATE SET
  tree_json = EXCLUDED.tree_json,
  is_active = EXCLUDED.is_active,
  updated_at = CURRENT_TIMESTAMP;
