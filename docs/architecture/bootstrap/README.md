<!-- Copyright (c) 2026 Olo Labs. All rights reserved. -->

# Olo Bootstrap Architecture

This folder documents how the Olo worker **bootstraps** itself: where plugins and features are registered, how configuration is loaded, and how the runtime is wired before Temporal (or a local runtime) starts.

## Conceptual split: Loader vs Runtime

| Module | Role |
|--------|------|
| **olo-worker-bootstrap-loader** | **Startup wiring.** Runs once at worker start. Handles environment resolution, configuration loading, descriptor discovery, plugin/feature registration, pipeline loading, registry wiring, validation. **Output:** `WorkerRuntime`. Entry: `BootstrapLoader.initialize()`. |
| **olo-worker-bootstrap-runtime** | **Execution infrastructure.** Contains runtime service contracts used during execution (not startup): `PluginExecutorFactory`, `FeatureRuntime`, `EventBus`, `ConnectionResolver`, `SecretResolver`, `ResourceCleanup`. These services live for the lifetime of the worker and are exposed via `WorkerRuntime`. |

Flow: `BootstrapLoader.initialize()` → `WorkerRuntime` (with plugins, features, events, connectionResolver, secretResolver, resourceCleanup) → Temporal workers start using the runtime.

## Documents

| Document | Contents |
|----------|----------|
| **[01_architecture](01_architecture.md)** | High-level flow, bootstrap phases, modules, BootstrapLoader responsibilities, WorkerRuntime, plugin and feature registration, event system, descriptor discovery, microkernel direction. |
| **[02_phase_model_and_improvements](02_phase_model_and_improvements.md)** | Orchestrator split, dependency ordering, INFRASTRUCTURE_READY, failure strategy, lifecycle state, metrics, descriptor index, BootstrapSnapshot, graceful shutdown, immutable registries, integrity validation, final phase order. |
| **[how-to-debug](how-to-debug.md)** | How to run the bootstrap global-context dump (`:olo-worker:bootstrapDump`), where to find the JSON dump, and required DB schema plus SQL queries for debugging. See also **[testing.md](../../testing.md)**. |

## Modules

- **`olo-worker-bootstrap-loader`** — `BootstrapLoader`, `BootstrapOrchestrator`, `BootstrapServiceRegistry`, `BootstrapPhase`, `BootstrapContributor`, phases and registry. Depends on `olo-worker-bootstrap-runtime`.
- **`olo-worker-bootstrap-runtime`** — `WorkerRuntime`, `WorkerRuntimeBuilder`, `ServiceRegistry`, and service interfaces: `PluginExecutorFactory`, `FeatureRuntime`, `EventBus`, `ConnectionResolver`, `SecretResolver`, `ResourceCleanup`.

## Key rule

**All runtime wiring happens during bootstrap (loader).** Runtime must not perform discovery or scanning; only registry lookup and direct calls. Execution infrastructure lives in the runtime module and is used for the lifetime of the worker.
