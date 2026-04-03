-- Copyright (c) 2026 Olo Labs. All rights reserved.

-- 007: Seed default region primary pipeline: Multi-Model Consensus (same JSON as configuration/debug/consensus-pipeline.json).
-- Run after 005 and 006. Idempotent: ON CONFLICT DO NOTHING.
-- Redis key: pipelines map entry olo.default.consensus-pipeline (Temporal task queue name for Chat BE / UI).

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
ON CONFLICT (region, pipeline_id, version) DO NOTHING;
