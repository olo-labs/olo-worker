<!-- Copyright (c) 2026 Olo Labs. All rights reserved. -->

# Use case: Multi-Model Consensus Solver

Part of the [worker architecture](README.md). Describes the **Multi-Model Consensus** use case: two models (Architect + Critic) discuss a problem until they converge on a final answer.

---

## Goal

Solve complex tasks (design, coding, research, analysis) by letting two different models collaborate:

- **Model A (Architect)** — creative / exploratory; proposes solutions.
- **Model B (Critic)** — analytical / critical; reviews and suggests improvements.

They communicate repeatedly (propose → critique → revise) until the design is agreed or a fixed number of rounds is reached.

**Example:** Design a fault-tolerant microservice architecture. Architect proposes; Critic analyzes weaknesses; Architect revises; repeat.

---

## Execution flow

1. **Planner** creates the execution plan: the **Consensus Subtree Creator** plugin (`consensus_subtree_creator`) receives the user task as `planText` and returns:
   - **variablesToInject:** `user_query`, `maxRounds` (default 2, max 5).
   - **steps:** A list of steps, each with `pluginRef` and `prompt`. Each round is:
     - **PROPOSE** (architect) — “Design a solution for: {{user_query}}” or “Revise based on critique…”
     - **CRITIQUE** (critic) — “Review the following proposal: {{__planner_step_N_response}}”
     - **REVISE** (architect) — “Produce final revised solution given critique…”

2. **Variable substitution:** At runtime, prompts that contain `{{variableName}}` are resolved from the variable engine (e.g. `{{__planner_step_0_response}}` is replaced by the previous step’s output). The worker’s **PluginInvoker** substitutes `{{varName}}` when building plugin inputs.

3. **Execution:** The engine runs each step in order (PLUGIN nodes). Architect and Critic are invoked via the same plugin path as any MODEL_EXECUTOR; the consensus plugin is a SUBTREE_CREATOR that only runs once to produce the step list.

---

## Pluggable resources

### 1. Plugins

| Plugin id | Contract type | Purpose |
|-----------|--------------|---------|
| **consensus_subtree_creator** | SUBTREE_CREATOR | Builds variablesToInject + steps (propose/critique/revise) from the user task. Registered by **ConsensusPluginProvider** (olo-internal-plugins-include). |
| **architect** | MODEL_EXECUTOR | Creative/exploratory model (e.g. GPT-4, Claude). Must be registered per tenant with this id. |
| **critic** | MODEL_EXECUTOR | Analytical/critical model (e.g. another GPT-4 instance with different system prompt, or Llama 3). Must be registered per tenant with this id. |

**Registration:**  
- **Consensus:** Registered in **InternalPlugins.createPluginManager()** via **ConsensusPluginProvider**.  
- **Architect / Critic:** Register with **PluginRegistry** per tenant, e.g.:

  - Same backend (e.g. LiteLLM) with two different model names or system prompts, or  
  - Two different providers (e.g. Ollama “architect” model + LiteLLM “critic” model).

  Ensure the bootstrap or application code that registers providers from **PluginManager.getProviders()** runs for all tenants that use this pipeline, and that each provider registers under the id expected by the pipeline scope (`architect`, `critic`).

### 2. Tools

No extra tools are required. The use case uses only PLANNER (with a SUBTREE_CREATOR) and PLUGIN (model executor) nodes. Optional: you can attach **features** (e.g. logging, metrics) to the pipeline or nodes.

### 3. Connections

If Architect and Critic call external APIs (e.g. OpenAI, Anthropic, LiteLLM proxy), configure **connections** in the composite snapshot (region → connections) and reference them from the plugin config if your plugin implementation supports it. Tenant config can supply API keys or base URLs; **TenantConfig** is passed to **ExecutablePlugin.execute(inputs, tenantConfig)**.

---

## Pipeline definition

Use the pipeline JSON that references the planner and scope:

- **Pipeline id:** e.g. `olo.default.consensus-pipeline` (or your region and id).
- **Execution tree:** One **PLANNER** node with:
  - **planInputVariable:** `user_query` (variable that holds the user task; set from workflow input or an earlier node).
  - **subtreeCreatorPluginRef:** `consensus_subtree_creator`.
- **Scope.plugins:** Include `architect`, `critic`, and `consensus_subtree_creator` with the contract types above.
- **variableRegistry:** Declare `user_query` (or the name you use) as IN.

A sample pipeline JSON is in **configuration/debug/consensus-pipeline.json**. You can load it via your DB/Redis pipeline template flow or admin API.

---

## Workflow input

- Set the task in the variable that the PLANNER reads (e.g. **user_query**). For example, from workflow input you might map an input field to `user_query` so the first node or the planner sees it.
- **routing.pipeline** must match the task queue that serves this pipeline (e.g. `olo.default.consensus-pipeline`).
- **context.tenantId** must be one of **allowedTenantIds** for the pipeline and must have **architect** and **critic** registered in **PluginRegistry** for that tenant.

---

## Summary

| Resource | Type | Id / location | Notes |
|----------|------|----------------|--------|
| Consensus planner | SUBTREE_CREATOR plugin | consensus_subtree_creator | ConsensusPluginProvider in InternalPlugins |
| Architect | MODEL_EXECUTOR plugin | architect | Register per tenant (e.g. LiteLLM/Ollama) |
| Critic | MODEL_EXECUTOR plugin | critic | Register per tenant |
| Pipeline | Pipeline JSON | e.g. `olo.default.consensus-pipeline` | Sample JSON under `configuration/debug/`; DB seeds in `olo-worker-db/.../db/schema/` |
| Variable substitution | Engine | — | `{{varName}}` in prompts resolved by PluginInvoker |

**Integration test:** `./gradlew :olo-worker:consensusUseCase` (requires Temporal, Redis, DB — see **`docs/testing.md`**).

For planner architecture, see [Tools](tools.md) and [Plugins](plugins.md). For pipeline and scope format, see [Execution tree](../execution-tree/01_execution_tree_design.md).
