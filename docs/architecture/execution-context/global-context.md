<!-- Copyright (c) 2026 Olo Labs. All rights reserved. -->

# Global context

The **global context** is the worker’s bootstrap-time and runtime view of configuration, regions, compiled execution trees, and tenant-to-region mapping. It is the single source used to resolve which pipeline runs for a given task queue and tenant.

## Role

- **Configuration** — Global and per-tenant config (from the primary region and `ConfigurationProvider`).
- **Snapshots by region** — Each region has a `CompositeConfigurationSnapshot` (core, pipelines, connections). The worker loads these from Redis (or DB) at bootstrap and on refresh.
- **Compiled pipelines** — Per region, pipeline JSON is compiled into `CompiledPipeline` (execution tree, contracts, scope) and cached in `ExecutionTreeRegistry`. The registry is **region → pipelineId → version → CompiledPipeline**. Lookup is **getCompiledPipeline(region, pipelineId, version)**; when **version** is null, the latest (max version) is returned.
- **Tenant → region** — Resolves which region a tenant uses so pipeline lookup can use the correct region.

Execution-time resolution flows: **queue name** (see format below) → **LocalContext.forQueue(tenantId, queue, requestedVersion)** → **GlobalContext.getCompiledPipeline(region, pipelineId, version)** (version null → latest) → **PipelineConfiguration** for the run.

## Contract and implementation

- **Interface:** `org.olo.bootstrap.loader.context.GlobalContext` (olo-worker-bootstrap-loader).
- **Implementation:** `GlobalContextImpl` in the same module; it delegates to:
  - **ConfigurationProvider** — config, snapshot map, primary composite.
  - **TenantRegionResolver** — tenant → region map.
  - **ExecutionTreeRegistry** — compiled pipeline cache per region; populated by `rebuildTreeForRegion(composite)`.

Access is via **GlobalContextProvider.getGlobalContext()** (singleton). The main worker registers a snapshot-change listener and calls **globalContext.rebuildTreeForRegion(composite)** whenever a region’s composite is set or updated, so the execution tree cache stays in sync with configuration.

## API summary

| Method | Purpose |
|--------|--------|
| `getConfig()` | Global configuration (primary region). |
| `getConfigForTenant(tenantId)` | Config for a tenant (global → region → tenant). |
| `getSnapshotMap()` | Region → `CompositeConfigurationSnapshot`. |
| `getPrimaryComposite()` | Primary (worker) region composite. |
| `getTenantToRegionMap()` | Tenant ID → region ID. |
| `getCompiledPipeline(region, pipelineId, version)` | Compiled pipeline for region + pipeline id + version; **version == null** → latest (max version). |
| `getCompiledPipelineForTenant(tenantId, pipelineId, version)` | Resolve tenant’s region, then return compiled pipeline; version null → latest. |
| `rebuildTreeForRegion(composite)` | Rebuild execution tree cache for that region from the composite. |
| `removeTreeForRegion(region)` | Remove a region from the cache (e.g. config refresh removed the region). |

## Queue format and lookup

Task queues use the form **`olo.<region>.<pipeline>[.<tenant>][.<mode>]`** (e.g. `olo.default.default-pipeline`, `olo.us-east.my-pipeline.tenant1`, `olo.us-east.my-pipeline.tenant1.debug`). The registry is keyed by pipeline id (often the base `<pipeline>` or the full suffix after region).

**LocalContext.forQueue(tenantId, effectiveQueue, requestedVersion):**

1. Parses `effectiveQueue` into `olo`, `region`, and the suffix after region (`<pipeline>` or `<pipeline>.<tenant>` or `<pipeline>.<tenant>.<mode>`); parses `requestedVersion` to `Long` (null/blank → latest).
2. Gets `GlobalContext` from `GlobalContextProvider`.
3. Looks up in order: **getCompiledPipeline(region, effectiveQueue, version)** (full queue name), then **getCompiledPipeline(region, suffixAfterRegion, version)**. If still missing and the suffix contains dots, tries base pipeline id by stripping trailing segments (e.g. `pipeline.tenant.mode` → `pipeline.tenant` → `pipeline`).
4. Builds a **PipelineConfiguration** with a single pipeline (that definition) and returns a **LocalContext** holding it.

So global context is the only place that holds the compiled pipelines; local context is a thin, per-queue view over it.

## Snapshot and tree lifecycle

1. **Bootstrap / refresh** — Configuration loader loads or refreshes region snapshots and calls **ConfigurationProvider.setSnapshotMap(...)** (or **putComposite**).
2. **Listener** — The worker (or test) registers **ConfigurationProvider.addSnapshotChangeListener((region, composite) -> …)**. On each (region, composite):
   - If `composite != null`, call **globalContext.rebuildTreeForRegion(composite)**.
   - If `composite == null`, call **globalContext.removeTreeForRegion(region)**.
3. **ExecutionTreeRegistry** — **rebuildTreeForRegion** compiles each pipeline in the composite (execution tree, contracts, scope, `isDynamicPipeline`, etc.) and stores **CompiledPipeline** by **region → pipelineId → version**. Global context’s **getCompiledPipeline(region, pipelineId, version)** reads from this cache; **version == null** returns the latest (max version) for that (region, pipelineId).

Until **rebuildTreeForRegion** is called for a region, **getCompiledPipeline** for that region returns null and **LocalContext.forQueue** will not resolve.

## Inspecting global context

- **Dump (JSON)** — With **olo.app.dump=true**, each run writes a **global context summary** under **olo.app.dump.dir** (e.g. `run-<runId>-<ts>-global-context.json`). It contains **servedRegions** and **snapshotsByRegion** (per region: pipelineIds, versions, etc.). See [Worker: How to debug](../worker/how_to_debug.md).
- **Full debug dump** — The **BootstrapGlobalContextDumpTest** (or a main that uses **GlobalContextSerializer**) can write a full dump (e.g. **build/global-context-debug.json**) that includes:
  - **globalConfig**, **primaryRegion**, **servedRegions**
  - **tenantToRegion**, **snapshotsByRegion**
  - **globalContextTree**: per region, per pipeline id, the **CompiledPipeline** (id, inputContract, outputContract, executionTree, isDebugPipeline, isDynamicPipeline, etc.)

That file is the canonical “what does global context look like?” snapshot for a running worker.

## Relationship to other docs

- **Configuration** — Snapshots, bootstrap, and refresh are described under [Configuration](../configuration/01_architecture.md) and [Snapshot model](../configuration/03_snapshot_model.md). Global context *uses* that snapshot map; it does not define how snapshots are stored or loaded.
- **Execution tree** — The compiled execution tree inside each **CompiledPipeline** is produced by **ExecutionTreeCompiler** (olo-execution-tree). See [Execution tree](../execution-tree/README.md).
- **Storage and seed** — For DB/Redis storage and seed scripts (e.g. tenant→region, pipeline templates), see the top-level [Global context](../global_context.md) doc.

## Summary

| Concept | Location | Purpose |
|--------|----------|--------|
| GlobalContext | bootstrap-loader (contract + impl) | Single view of config, snapshots, tenant→region, and compiled pipelines. |
| ExecutionTreeRegistry | bootstrap-loader (impl) | region → pipelineId → version → CompiledPipeline; filled by rebuildTreeForRegion; version null → latest. |
| LocalContext | olo-worker-bootstrap-loader (context) | Resolves (tenant, queue) → PipelineConfiguration using GlobalContext. |
| OloRuntimeContext | bootstrap-runtime | Per-run snapshot: copy of workflow input and pipeline (reference or deep copy when isDynamicPipeline). |

Global context is the **authoritative** place for “what pipelines exist per region and what is their compiled form.” Local context and runtime context both depend on it for execution.
