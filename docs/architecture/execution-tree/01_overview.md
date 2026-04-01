<!-- Copyright (c) 2026 Olo Labs. All rights reserved. -->

# Execution Tree Overview

Part of the [execution-tree](README.md) documentation.

---

## What Is the Execution Tree?

The **execution tree** is the in-memory, per-tenant/queue representation of pipeline definitions after they are loaded and validated at bootstrap. It is built from:

- Tenant list and pipeline configuration (from Redis, DB, file, or env).
- Global settings (timeouts, limits, feature flags).

The result is an **ExecutionConfigSnapshot** (or equivalent) per tenant/queue: an immutable snapshot used by the runtime to execute pipelines without touching config stores during execution.

---

## Role in Bootstrap

| Bootstrap phase | Execution tree involvement |
|-----------------|----------------------------|
| **ENVIRONMENT_LOAD** | Tenant list and pipeline config sources are resolved. |
| **PIPELINE_LOADING** | Pipeline definitions are loaded; execution trees are built per tenant/queue and stored in the bootstrap context or registry. |
| **VALIDATION** | All `pluginRef` in execution trees are checked against `PluginRegistry`; connection refs and feature attachments are validated. |
| **CONTEXT_BUILD** | Trees/snapshots are exposed to the worker via `WorkerRuntime` or the service registry. |

The **olo-execution-tree** module (and/or **olo-worker-configuration**) provides the builders and types that turn pipeline config into these trees. The runtime (e.g. Temporal workflow or local executor) then uses only the built trees, not raw config.

---

## Design Principles

- **Built once at bootstrap** — No runtime discovery or reload of pipeline structure; only the pre-built tree is used.
- **Immutable snapshots** — Per-tenant/queue snapshots are immutable so the runtime does not need to guard against concurrent config changes.
- **Validation before start** — All plugin refs, connection refs, and feature attachments are validated in VALIDATION phase; workers do not start if validation fails.

---

## Relationship to Configuration

- **olo-worker-configuration** — Loads and refreshes **configuration** (defaults, env, Redis snapshot: core, pipelines, connections, meta). Provides `ConfigurationProvider`, tenant–region resolution, and refresh.
- **olo-execution-tree** — Consumes that configuration (or a dedicated pipeline source) to **build execution trees** and snapshots for each tenant/queue. Focus is on the structure used by the execution engine (nodes, plugin refs, features), not on the raw config format or refresh policy.

In deployments where pipelines come from the Redis snapshot, the pipeline section from configuration is the input to the execution tree builder. In others, pipelines might come from DB or file; either way, the execution tree is built once at bootstrap from that source.
