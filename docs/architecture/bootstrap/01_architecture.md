<!-- Copyright (c) 2026 Olo Labs. All rights reserved. -->

# Bootstrap Architecture

Part of the [bootstrap](README.md) documentation.

---

## 1. High-Level Bootstrap Flow

Worker startup:

```text
OloWorkerApplication
      ↓
BootstrapLoader.initialize()
      ↓
WorkerRuntime
      ↓
Temporal workers / runtimes start using the configured context
```

**Loader** (startup, runs once): environment resolution, configuration loading, descriptor discovery, plugin/feature registration, pipeline loading, registry wiring, validation. **Output:** `WorkerRuntime`.

**Runtime** (execution infrastructure): `WorkerRuntime` exposes `PluginExecutorFactory`, `FeatureRuntime`, `EventBus`, `ConnectionResolver`, `SecretResolver`, `ResourceCleanup`. These services live for the lifetime of the worker and are used during execution, not during bootstrap.

**Outcomes of bootstrap:**

- Plugins and tools registered and wired via `PluginRegistry` / `PluginExecutorFactory`.
- Features registered via `FeatureRegistry` / `PipelineFeatureContext`.
- Execution trees and tenant configs loaded and snapshotted.
- Connection and secret resolvers (when implemented) registered.
- `EventBus` / `ExecutionEventBus` registered for logging, metrics, UI, human-approval, etc.
- Shutdown hook (`ResourceCleanup.onExit()` etc.) configured.

**Goals:** Explicit, modular, aligned with microkernel — kernel contracts, runtimes, and plugins wired in a clear, one-way dependency graph.

---

## 1.1 Bootstrap Phases (Summary)

Bootstrap is a sequence of **phases** so ordering and extension points are explicit.

| Phase | Purpose |
|-------|---------|
| **CORE_SERVICES** | Core infrastructure: logging, config, basic registries. |
| **ENVIRONMENT_LOAD** | Resolve environment (paths, plugin dirs, tenants, queues). |
| **INFRASTRUCTURE_READY** | Verify Redis, DB, secrets manager reachable before starting workers. |
| **PLUGIN_DISCOVERY** | Discover plugin descriptors; populate `PluginRegistry`. |
| **FEATURE_DISCOVERY** | Discover feature descriptors; populate `FeatureRegistry`. |
| **RESOURCE_PROVIDERS** | Wire connection, secret, and event providers. |
| **PIPELINE_LOADING** | Load and validate pipelines; build execution trees and snapshots. |
| **VALIDATION** | Validate plugin refs, connection refs, feature attachments; fail fast. |
| **CONTEXT_BUILD** | Build `WorkerBootstrapContext` / `WorkerRuntime` from registries. |
| **WORKER_START** | Start Temporal (or local) workers. |

After **WORKER_START**, no further discovery or wiring; runtime only does registry lookups and direct calls.

---

## 1.2 Phase Behavior (Detail)

**Phase 1 — Environment & config load**

- Resolve environment (paths, plugin directories, tenants, queues).
- Load **static config**: tenant list, pipeline definitions, global settings (timeouts, limits, feature flags).
- Build `ExecutionConfigSnapshot` per tenant/queue (immutable snapshot per run).

**Phase 2 — Descriptor discovery**

- Scan plugin/feature/connection descriptors:
  - `META-INF/olo-plugin.json` (per plugin JAR; `PluginDescriptor`).
  - `META-INF/olo-features.json` (optional).
  - Optional `META-INF/olo-connections.json`.
- Parse into in-memory models: `PluginDescriptor`, feature descriptors, connection schemas.

**Phase 3 — Registry wiring**

- Instantiate and register: `PluginRegistry`, `FeatureRegistry`, (future) `ConnectionRegistry`, `SecretResolver`, event adapters.
- Bind factories: `PluginExecutorFactory` → `RegistryPluginExecutor` → `PluginRegistry`; feature enrichers and contexts.

**Phase 4 — Validation**

- All `pluginRef` in execution trees resolve to registered plugins.
- All connection refs resolve to known `connectionType` + schema.
- Capabilities, `executionMode`, node-level overrides allowed per plugin.
- Feature attachments consistent with node types.
- Fail fast on any inconsistency (no workers started).

**Phase 5 — Context construction**

- Build `WorkerBootstrapContext` / `WorkerRuntime` exposing only what runtimes need: `PluginExecutorFactory`, `PipelineFeatureContext`, connection/secret/event services, `runResourceCleanup()`.

**Phase 6 — Runtime start**

- Start Temporal (or local) workers with the constructed context.

---

## 1.3 Bootstrap Phase Model (Extensibility)

Phases are represented in code via `BootstrapPhase` enum and `BootstrapContributor` (in the **loader** module):

```java
public enum BootstrapPhase {
    CORE_SERVICES,
    ENVIRONMENT_LOAD,
    INFRASTRUCTURE_READY,
    PLUGIN_DISCOVERY,
    FEATURE_DISCOVERY,
    RESOURCE_PROVIDERS,
    PIPELINE_LOADING,
    VALIDATION,
    CONTEXT_BUILD,
    WORKER_START
}

public interface BootstrapContributor {
    Set<BootstrapPhase> phases();
    default Set<Class<? extends BootstrapContributor>> dependsOn() { return Set.of(); }
    void contribute(BootstrapPhase phase, BootstrapContext context);
}
```

`BootstrapLoader` (via `BootstrapOrchestrator`) runs:

```text
for (BootstrapPhase phase : BootstrapPhase.values())
    for (BootstrapContributor c : contributors)
        if (c.phases().contains(phase)) c.contribute(phase, context);
```

Contributors can live in separate modules (e.g. `PluginBootstrapContributor`, `FeatureBootstrapContributor`, `ConnectionBootstrapContributor`), so new logic is added without changing core bootstrap.

---

## 1.4 Bootstrap-Time Binding Principle

All plugins, features, and runtime services are **bound during worker bootstrap**, not at call time. No runtime reflection-based discovery.

Binding pipeline:

```text
annotations → descriptor (e.g. META-INF/olo-plugin.json)
        ↓
bootstrap binding (BootstrapLoader / loaders)
        ↓
runtime registries (PluginRegistry, FeatureRegistry, …)
```

This gives fast startup, deterministic wiring, and predictable plugin behavior.

---

## 2. Modules Involved in Bootstrap

| Module | Role |
|--------|------|
| **olo-worker-bootstrap-loader** | Startup wiring: `BootstrapLoader`, `BootstrapOrchestrator`, `BootstrapServiceRegistry`, phases, contributors. Output: `WorkerRuntime`. |
| **olo-worker-bootstrap-runtime** | Execution infrastructure: `WorkerRuntime`, `WorkerRuntimeBuilder`, `ServiceRegistry`, and contracts for `PluginExecutorFactory`, `FeatureRuntime`, `EventBus`, `ConnectionResolver`, `SecretResolver`, `ResourceCleanup`. |
| **olo-internal-plugins** / **olo-internal-tools** | Register built-in plugins and tools. |
| **olo-worker-plugin** | `PluginRegistry`, `PluginExecutorFactory`, `RegistryPluginExecutor`. |
| **olo-worker-features** / feature modules | Register built-in features (logging, metrics, quota, ledger, debug). |
| **olo-execution-tree** / **olo-worker-configuration** | Load pipeline config and build execution trees per tenant/queue. |

---

## 3. BootstrapLoader Responsibilities (Startup Wiring)

`BootstrapLoader` handles startup only; it runs once when the worker starts.

- **Discover and load configuration** — tenant list and pipeline config (Redis/DB/file/env); build `ExecutionConfigSnapshot` per tenant/queue.
- **Initialize registries** — construct and populate `PluginRegistry`, `FeatureRegistry`, (future) `ConnectionRegistry`, `SecretResolver`, event adapters.
- **Run bootstrap contributors** — call extension points that participate in startup.
- **Build and return WorkerRuntime** — so the worker can start Temporal workers without knowing how plugins/features were registered.

Flow:

```text
BootstrapLoader.initialize()
    ↓
load tenant + config
    ↓
register plugins/tools
    ↓
register features
    ↓
build WorkerRuntime (from olo-worker-bootstrap-runtime)
```

---

## 4. WorkerRuntime (Execution Infrastructure)

**WorkerRuntime** lives in **olo-worker-bootstrap-runtime**. It is the bridge between bootstrap and execution:

- Exposes execution infrastructure used **during execution** (not during bootstrap): `PluginExecutorFactory`, `FeatureRuntime`, `EventBus`, `ConnectionResolver`, `SecretResolver`, `ResourceCleanup`, and a generic `ServiceRegistry` for extensions.
- Built once by `BootstrapLoader.initialize()`; the worker holds it for the lifetime of the process.
- Worker uses: `runtime.plugins()`, `runtime.features()`, `runtime.events()`, `runtime.connectionResolver()`, `runtime.secretResolver()`, `runtime.resourceCleanup()`, and never sees descriptors or loading details.

```text
WorkerRuntime
     ├── PluginExecutorFactory
     ├── FeatureRuntime
     ├── EventBus
     ├── ConnectionResolver
     ├── SecretResolver
     └── ResourceCleanup
```

These services live for the lifetime of the worker. The loader module wires them; the runtime module defines their contracts.

### 4.1 End-to-End Diagram

```text
                OloWorkerApplication
                         │
                         ▼
                BootstrapLoader.initialize()
                         │
                         ▼
                ┌─────────────────┐
                │ BootstrapServiceRegistry  (loader)
                └─────────────────┘
                     │     │
        ┌────────────┘     └─────────────┐
        ▼                                ▼
   PluginRegistry                   FeatureRegistry
        │                                │
        └───────────────┬────────────────┘
                        ▼
                WorkerRuntime  (runtime module)
                        │
     ├── PluginExecutorFactory   ├── EventBus
     ├── FeatureRuntime          ├── ConnectionResolver
     └── SecretResolver          └── ResourceCleanup
                        │
                        ▼
                Temporal Worker
                        │
                        ▼
                Execution Engine
```

---

## 5. Plugin & Feature Registration

**Plugins:** Internal plugins register in `olo-internal-plugins` via `PluginRegistry.getInstance().register…(tenantId, id, plugin)`. `RegistryPluginExecutor` resolves from `PluginRegistry` and invokes `ExecutablePlugin.execute(inputs, tenantConfig)`. Bootstrap wires a single `PluginExecutorFactory` into the context so the worker only calls `executorFactory.create(nodeContext)` and `executor.execute(pluginId, inputsJson, nodeId)`.

**Features:** Registered via `FeatureRegistry`. `PipelineNodeFeatureEnricher` attaches queue/pipeline and node-level overrides. `NodeExecutionContext` runs feature chains: `before`, `afterSuccess` / `afterError`, `afterFinally`. Bootstrap ensures `FeatureRegistry` and `PipelineFeatureContext` are ready before any pipeline runs.

---

## 6. Event System Wiring

- **Service:** `ExecutionEventBus` (or `EventBus`).
- **Producers:** features (logging, metrics, quota, ledger), node handlers, planner, human-step handlers.
- **Consumers:** UI/WebSocket, metrics, audit, debugging.

Bootstrap registers the event bus in the service registry; features and runtimes obtain it via context or registry. At runtime: `events.publish(new NodeStartedEvent(...))`.

---

## 7. Descriptor-Driven Plugin Bootstrap (Future)

Target flow:

```text
PluginLoader
    ↓
locate META-INF/olo-plugin.json (or descriptor index)
    ↓
deserialize PluginDescriptor
    ↓
register in PluginRegistry
```

No reflection scanning; `PluginDescriptor` is the only runtime source of plugin metadata. `OloBootstrap` delegates discovery to the plugin runtime and only wires the resulting `PluginRegistry` into the context.

---

## 8. Towards the Microkernel Layout

- **olo-kernel** — `ExecutablePlugin`, `PluginDescriptor`, `ExecutionContext`, `ExecutionMode`, `Feature`, events, connection/secret contracts.
- **olo-plugin-runtime** — plugin discovery and registration.
- **olo-feature-runtime** — feature resolution and execution.
- **olo-execution** — execution engine.
- **olo-runtime-temporal** — adapts execution to Temporal.
- **olo-annotations** — compile-time only; generates descriptors.

The **loader** is a thin orchestrator: wires kernel contracts (via `BootstrapServiceRegistry`), calls plugin/feature/connection runtimes to do their discovery, and builds `WorkerRuntime` (from the **runtime** module). The **runtime** module holds only execution infrastructure contracts; no startup logic.

---

## 9. Key Rule: All Wiring at Bootstrap

**Rule:** All runtime wiring happens during **bootstrap**. Runtime **must not** do discovery or scanning.

**Allowed at runtime:** Descriptor lookup (once at startup), registry lookup, direct method calls.

**Not allowed at runtime:** Classpath/annotation scanning, ad-hoc plugin/feature discovery, dynamic loading that bypasses bootstrap contracts.

This keeps the runtime fast and aligned with the microkernel: discovery and binding are explicit, one-time concerns of `BootstrapLoader` and the loaders. Execution infrastructure (plugins, features, events, connections, secrets) lives in **olo-worker-bootstrap-runtime** and is used for the lifetime of the worker.
