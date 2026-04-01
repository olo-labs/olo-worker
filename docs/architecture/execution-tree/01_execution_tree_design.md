<!-- Copyright (c) 2026 Olo Labs. All rights reserved. -->

# Execution Tree Design

Design spec for the Execution Tree: declarative tree of nodes, variable model, scope, execution context. See [README](README.md) for module contents and [01_overview](01_overview.md) for bootstrap role.

## Goals

- Declarative pipelines (tree of nodes; no code in tree).
- Clear control flow: SEQUENCE, IF, SWITCH, ITERATOR, FORK/JOIN, PLUGIN, PLANNER.
- Variable-centric: variableRegistry, inputMappings, outputMappings, IN/INTERNAL/OUT.
- Feature hooks: PRE → node → POST_SUCCESS/POST_ERROR → FINALLY.
- Plugin-agnostic: pluginRef from scope; executor resolves and invokes.

## Tree Structure

**ExecutionTreeNode** (immutable for replay safety):

| Field | Purpose |
|-------|--------|
| id | Unique node id |
| type | NodeType |
| name | Human-readable name (observability, debugging) |
| version | Node/pipeline version (tracing, compatibility) |
| params | Node-specific configuration |
| children | Child nodes (containers) |
| inputMappings | Variable/parameter input mappings |
| outputMappings | Output → variable mappings |
| timeout | Timeout config (e.g. scheduleToStartSeconds, startToCloseSeconds) |
| retryPolicy | Retry config (e.g. maxAttempts, backoffCoefficient) |
| executionMode | SYNC / ASYNC / FIRE_AND_FORGET |
| metadata | Observability: owner, description, tags, etc. |
| connections | Logical connection names by role |
| preExecution, postSuccessExecution, postErrorExecution, finallyExecution, features | Feature hooks |

Example **metadata** (production observability, tenant debugging, monitoring):

```yaml
metadata:
  owner: payments-team
  description: fraud detection step
  tags: ["fraud", "risk"]
```

**NodeType**: SEQUENCE, GROUP, PLUGIN, PLANNER, IF, SWITCH, CASE, ITERATOR, FORK, JOIN, TRY_CATCH, RETRY, SUB_PIPELINE, EVENT_WAIT, FILL_TEMPLATE, LLM_DECISION, TOOL_ROUTER, EVALUATION, REFLECTION.

## Variable Model

VariableRegistry (declarations: name, type, VariableScope.IN/INTERNAL/OUT). VariableEngine per run; IN seeded from input; nodes read/write via mappings.

## Scope

scope.plugins, scope.features. pluginRef in PLUGIN node must be in scope.

## Execution Context

tenantId, runId, variableEngine, pluginRegistry, featureRegistry, tenantConfig, snapshotVersionId.

## Module (olo-execution-tree)

ExecutionTreeNode, NodeType, ExecutionMode, VariableRegistry, VariableDeclaration, VariableScope, Scope, PipelineDefinition, CompiledPipeline, ExecutionConfigSnapshot, VariableEngine, ExecutionContext, ExecutionTreeCompiler.

## Runtime compiled pipeline cache (worker)

The worker maintains an immutable, per-region cache of compiled pipelines built from the Redis pipelines section (`olo:config:pipelines:<region>`):

- **Global registry**: `checksum → CompiledPipeline` to deduplicate identical pipelines across regions.
- **Per-region registry**: `region → (pipelineId → CompiledPipeline)` for fast lookup by region and pipeline id.
- On startup and on each configuration refresh, the worker rebuilds the registry for affected regions from `CompositeConfigurationSnapshot.getPipelines()`, then replaces the registry via a **single volatile write** (no in-place mutation).
- At runtime, execution logic only reads from this cache (no Redis/DB access), using `CompiledPipeline`’s metadata (id, version, checksum) for logging and debugging.
