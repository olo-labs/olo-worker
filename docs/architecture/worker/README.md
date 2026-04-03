<!-- Copyright (c) 2026 Olo Labs. All rights reserved. -->

# Worker architecture

This folder documents the **OLO worker** runtime: tools, plugins, features, and how to debug workflows.

## Documents

| Document | Contents |
|----------|----------|
| **[tools](tools.md)** | Tools architecture: tooling node types (EVENT_WAIT, LLM_DECISION, TOOL_ROUTER, EVALUATION, REFLECTION, FILL_TEMPLATE), ToolingHandler, ToolingExecutions, internal tool providers. |
| **[plugins](plugins.md)** | Plugin architecture: contracts (PluginExecutor, ExecutablePlugin), PluginRegistry, contract types, execution path, threading and lifecycle. |
| **[features](features.md)** | Features architecture: pre/post hooks, phases (PRE, POST_SUCCESS, POST_ERROR, FINALLY), FeatureRegistry, FeatureAttachmentResolver, privilege (INTERNAL vs COMMUNITY). |
| **[how_to_debug](how_to_debug.md)** | How to debug workflows: input format, starting a run (Temporal UI, integration test), dumping input and LocalContext. |
| **[use-case-multi-model-consensus](use-case-multi-model-consensus.md)** | Multi-Model Consensus: planner plugin, architect/critic plugins, pipeline, and connections. |

## Relationship to other architecture docs

- **Execution tree** ([../execution-tree/README.md](../execution-tree/README.md)) defines node types and variable model; the worker engine dispatches and runs them.
- **Execution context** ([../execution-context/README.md](../execution-context/README.md)) covers global context, local context, and runtime context used by the worker.
- **Bootstrap** ([../bootstrap/README.md](../bootstrap/README.md)) — **`Bootstrap.run()`** loads Redis-backed snapshots into **`ConfigurationProvider`**; **`GlobalContext`** exposes config and compiled pipelines. The phased **`BootstrapLoader`** path is documented for contributors/registry wiring but is not the `main` entry today.
