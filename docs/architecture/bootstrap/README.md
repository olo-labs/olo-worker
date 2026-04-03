<!-- Copyright (c) 2026 Olo Labs. All rights reserved. -->

# Olo Bootstrap Architecture

This folder documents how the Olo worker **bootstraps** itself: configuration loading, global execution context, plugin-related contracts, and how work is wired before Temporal starts.

## Current worker entry (what `main` runs)

The **olo-worker** application (`org.olo.worker.OloWorkerApplication`) uses **`org.olo.configuration.Bootstrap.run()`** from **olo-worker-configuration** — not `BootstrapLoader.initialize()`. Sequence:

1. **Ports** — `DbPortRegistrar` / `CachePortRegistrar` register JDBC and Redis snapshot factories (and related ports) so bootstrap can use DB and Redis.
2. **`Bootstrap.run()`** — defaults → env → optional Redis snapshot wait/load (and optional DB-assisted snapshot build or pipeline backfill when Redis/DB are configured); installs **`ConfigurationProvider`**; optional refresh and tenant-region schedulers.
3. **`GlobalContextProvider.getGlobalContext()`** — lazy **`GlobalContext`** (`GlobalContextImpl`) backed by **`ConfigurationProvider`**, **`TenantRegionResolver`**, and **`ExecutionTreeRegistry`** (compiled pipelines per region).
4. **Temporal** — task queues are derived from pipeline IDs in the loaded snapshot (e.g. `olo.<region>.<pipeline>`); one Temporal `Worker` per queue.

Execution-time reads use **in-memory config and registries** only; no per-request Redis or DB for configuration.

## Conceptual split: phased loader vs runtime (library modules)

| Module | Role |
|--------|------|
| **olo-worker-bootstrap-loader** | **Phased orchestration and global context types.** `BootstrapLoader.initialize(contributors)` → `WorkerRuntime`; `GlobalContext`, `LocalContext`, `ExecutionTreeRegistry`. **Not invoked from `OloWorkerApplication.main` today** — useful for tests (`bootstrapDump`), extensions, and future wiring. |
| **olo-worker-bootstrap-runtime** | **Execution infrastructure contracts:** `WorkerRuntime`, `ServiceRegistry`, `PluginExecutorFactory`, `EventBus`, etc. Consumed if you build a runtime via `BootstrapLoader`; the current `main` wires plugins via classpath (`DefaultPluginExecutorFactory`) instead. |

For the **live process**, treat **`Bootstrap.run()` + `GlobalContext`** as the authoritative bootstrap path; the loader/runtime modules supply **shared types** (`GlobalContext`, execution tree registry) and an optional **phased** startup model.

## Documents

| Document | Contents |
|----------|----------|
| **[01_architecture](01_architecture.md)** | High-level flow, bootstrap phases, modules, BootstrapLoader responsibilities, WorkerRuntime, plugin and feature registration, event system, descriptor discovery, microkernel direction. |
| **[02_phase_model_and_improvements](02_phase_model_and_improvements.md)** | Orchestrator split, dependency ordering, INFRASTRUCTURE_READY, failure strategy, lifecycle state, metrics, descriptor index, BootstrapSnapshot, graceful shutdown, immutable registries, integrity validation, final phase order. |
| **[how-to-debug](how-to-debug.md)** | How to run the bootstrap global-context dump (`:olo-worker:bootstrapDump`), where to find the JSON dump, and required DB schema plus SQL queries for debugging. See also **[testing.md](../../testing.md)**. |

## Modules

- **`olo-worker-bootstrap-loader`** — `BootstrapLoader`, `BootstrapOrchestrator`, `BootstrapServiceRegistry`, `BootstrapPhase`, `BootstrapContributor`, phases and registry. Depends on `olo-worker-bootstrap-runtime`.
- **`olo-worker-bootstrap-runtime`** — `WorkerRuntime`, `WorkerRuntimeBuilder`, `ServiceRegistry`, and service interfaces: `PluginExecutorFactory`, `FeatureRuntime`, `EventBus`, `ConnectionResolver`, `SecretResolver`, `ResourceCleanup`.

## Key rules

- **Configuration and compiled pipelines** are fixed at bootstrap (`Bootstrap.run()` and execution tree rebuild on snapshot change). Workers do not reload pipeline definitions from Redis on every activity call.
- **Phased loader** (`BootstrapLoader`) is the right place for **descriptor-driven** registration when you adopt that path; the current **`main`** keeps wiring minimal and relies on **`Bootstrap.run()`** plus **`GlobalContext`**.
