<!-- Copyright (c) 2026 Olo Labs. All rights reserved. -->

# Phase Model and Improvements

Part of the [bootstrap](README.md) documentation.

This document describes the **phased `BootstrapLoader` design** and future improvements. The **running worker process** uses **`Bootstrap.run()`** and **`GlobalContext`** (see [01_architecture](01_architecture.md)); the orchestration split below applies when adopting or extending **`BootstrapLoader.initialize()`**.

---

## 1. Separate Bootstrap Orchestration from Services

**Problem:** A single `OloBootstrap` doing orchestration, contributor management, service wiring, and context building becomes a large, hard-to-maintain class.

**Change:** Split into loader (orchestration + registry) and runtime (execution infrastructure):

```text
BootstrapLoader (public entry point, loader module)
    ↓
BootstrapOrchestrator
    ↓
BootstrapServiceRegistry
    ↓
WorkerRuntimeBuilder → WorkerRuntime  (runtime module)
```

**Module layout:**

- **olo-worker-bootstrap-loader:** `BootstrapLoader`, `BootstrapOrchestrator`, `BootstrapServiceRegistry`, phases (`BootstrapPhase`, `BootstrapContributor`, `BootstrapPhaseExecutor`).
- **olo-worker-bootstrap-runtime:** `WorkerRuntime`, `WorkerRuntimeBuilder`, `ServiceRegistry`, and service contracts (`PluginExecutorFactory`, `FeatureRuntime`, `EventBus`, etc.).

---

## 2. Dependency Ordering Between Contributors

Add `dependsOn()` to `BootstrapContributor` and resolve order via topological sort so dependencies run first.

---

## 3. Infrastructure Readiness Phase

Add **INFRASTRUCTURE_READY** after ENVIRONMENT_LOAD. Verify Redis, DB, secrets manager reachable before PLUGIN_DISCOVERY. Workers do not start until this phase succeeds.

---

## 4. Bootstrap Failure Strategy

Define **FAIL_FAST**, **RETRY**, **OPTIONAL** per check (e.g. Redis: RETRY; invalid feature: FAIL_FAST; optional plugin: WARN).

---

## 5. Service Registry View

Worker receives **WorkerRuntime** backed by `BootstrapServiceRegistry` (or `OloServiceRegistry`); no god object.

---

## 6. Explicit Bootstrap Lifecycle State

**BootstrapState:** CREATED, INITIALIZING, INITIALIZED, FAILED. Prevents use of registries before initialization.

---

## 7. Bootstrap Metrics

Emit `bootstrap.phase.start`, `bootstrap.phase.complete`, `bootstrap.phase.failed` for observability.

---

## 8. Descriptor Index

Use `META-INF/olo-plugin.index` (list of plugin names) and load descriptors by path instead of scanning classpath.

---

## 9. Bootstrap Snapshot Object

**BootstrapSnapshot:** plugins loaded, features loaded, pipelines loaded, tenants loaded, version, startup timestamp. For debug/admin/health.

---

## 10. Graceful Shutdown Phase

**ShutdownContributor** hook; lifecycle states include RUNTIME_STOPPING, RUNTIME_STOPPED. Close plugin pools, flush event bus, close connections in reverse order.

---

## 11. Immutable Registries After Bootstrap

After bootstrap, call `pluginRegistry.freeze()` (and equivalent for FeatureRegistry, ConnectionRegistry). No runtime mutation.

---

## 12. Bootstrap Integrity Validation

After VALIDATION: no duplicate plugin IDs, all pipelines valid, no orphan features, tenant configs consistent.

---

## 13. Parallel Phase Execution (Optional)

Allow independent phases (e.g. PLUGIN_DISCOVERY and FEATURE_DISCOVERY) to run contributors in parallel. Optional.

---

## Final Phase Order

1. CORE_SERVICES  
2. ENVIRONMENT_LOAD  
3. INFRASTRUCTURE_READY  
4. PLUGIN_DISCOVERY  
5. FEATURE_DISCOVERY  
6. RESOURCE_PROVIDERS  
7. PIPELINE_LOADING  
8. VALIDATION  
9. CONTEXT_BUILD  
10. WORKER_START  

---

## Overall Architecture After Improvements

```text
OloWorkerApplication
    → BootstrapLoader.initialize()
    → BootstrapOrchestrator → BootstrapPhaseExecutor
    → BootstrapServiceRegistry (loader)
    → WorkerRuntimeBuilder (runtime) → WorkerRuntime
    → Temporal Worker
```
