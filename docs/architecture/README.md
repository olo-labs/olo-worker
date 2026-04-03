<!-- Copyright (c) 2026 Olo Labs. All rights reserved. -->

# Architecture documentation

- **[Configuration](configuration/olo-worker-configuration.md)** — olo-worker-configuration: bootstrap, snapshot model, refresh, admin service, operational guidelines.
- **[Bootstrap](bootstrap/README.md)** — **`Bootstrap.run()`** (olo-worker-configuration) plus **`GlobalContext`**; phased **`BootstrapLoader`** / **`WorkerRuntime`** modules for extension and tests. See bootstrap README for the distinction.
- **[Execution tree](execution-tree/README.md)** — olo-execution-tree: execution trees per tenant/queue, PIPELINE_LOADING, relationship to configuration.
- **[Execution context](execution-context/README.md)** — Execution-time context: global context, local context, and runtime context. See [Global context](execution-context/global-context.md) for the global context contract and lifecycle.
- **[Worker](worker/README.md)** — Worker runtime: [tools](worker/tools.md), [plugins](worker/plugins.md), [features](worker/features.md) architecture, and [how to debug](worker/how_to_debug.md) workflows.
- **[Global context (storage/seed)](global_context.md)** — Regions, tenants, region pipelines, tenant overrides; storage and seed (008).
- **[olo-workflow-input](olo-workflow-input.md)** — Workflow input and payload parsing.
- **[Testing](../testing.md)** — Unit vs integration tasks, Gradle verification targets, prerequisites.
