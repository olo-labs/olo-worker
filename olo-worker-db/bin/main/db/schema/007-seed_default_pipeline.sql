-- Copyright (c) 2026 Olo Labs. All rights reserved.

-- 007: Seed default region primary pipeline (execution logic only) + queues/profiles columns (binding & execution layer).
-- Run after 005, 012, 006. Idempotent: ON CONFLICT DO NOTHING.
-- Redis: olo:config:pipelines:<region> wraps { "pipelines": { ... } }; queues/profiles copied to olo:config:queues / olo:config:profiles.

INSERT INTO olo_pipeline_template (region, pipeline_id, version, is_active, tree_json, queues_json, profiles_json, updated_at)
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
  $json$
{
  "queues": {
    "olo-fast": { "workerType": "fast-model" },
    "olo-smart": { "workerType": "gpt4" },
    "olo-cheap": { "workerType": "low-cost" },
    "olo-debug": { "workerType": "debug" }
  }
}
$json$::jsonb,
  $json$
{
  "profileOrder": "fast,smart,cheap,debug",
  "profiles": {
    "fast": {
      "displayName": "Fast Response",
      "displaySummary": "Quick replies with a fast, lightweight model.",
      "emoji": "⚡",
      "pipeline": "olo.default.consensus-pipeline",
      "queue": "olo-fast",
      "runAgain": true
    },
    "smart": {
      "displayName": "Smart Assistant",
      "displaySummary": "Stronger reasoning for harder questions.",
      "emoji": "🧠",
      "pipeline": "olo.default.consensus-pipeline",
      "queue": "olo-smart",
      "runAgain": true
    },
    "cheap": {
      "displayName": "Low Cost Model",
      "displaySummary": "Lowest cost; best for simple queries.",
      "emoji": "💬",
      "pipeline": "olo.default.consensus-pipeline",
      "queue": "olo-cheap",
      "runAgain": true
    },
    "debug": {
      "displayName": "Debug Mode",
      "displaySummary": "Verbose traces and a debug-oriented pipeline.",
      "emoji": "🐞",
      "pipeline": "olo.default.consensus-pipeline",
      "queue": "olo-debug",
      "runAgain": false
    }
  }
}
$json$::jsonb,
  CURRENT_TIMESTAMP
)
ON CONFLICT (region, pipeline_id, version) DO NOTHING;
