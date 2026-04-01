<!-- Copyright (c) 2026 Olo Labs. All rights reserved. -->

# Plugin architecture

Part of the [architecture](../../README.md) documentation. Describes how **plugins** are defined, registered, and executed in the worker.

---

## Role

Plugins are **tenant-scoped capabilities** that the execution tree invokes by reference (`pluginRef`, `evaluatorRef`, etc.). They implement contracts (e.g. model executor, embedding, vector store) and are registered with **PluginRegistry** per tenant and id. The worker resolves the plugin at runtime and invokes it via the **PluginExecutor** contract (protocol); concrete execution is provided by the plugin module (e.g. **RegistryPluginExecutor** calling **PluginRegistry**).

- **Contracts:** **olo-worker-protocol** defines **PluginExecutor** and **PluginExecutorFactory**.
- **Implementations and registry:** **olo-worker-plugin** provides **ExecutablePlugin**, **PluginRegistry**, contract types, and the default executor that looks up and runs plugins.

---

## Contracts (protocol)

| Contract | Purpose |
|----------|---------|
| **PluginExecutor** | Execute a plugin by id with JSON inputs; return JSON outputs. Also `toJson` / `fromJson` for map↔JSON. Optional `nodeId` for per-node instance caching. |
| **PluginExecutorFactory** | Create a **PluginExecutor** for a given tenant (and optional cache). The worker obtains the factory at bootstrap and uses it to get an executor per run or activity. |

The worker (kernel) depends only on these interfaces; the plugin module supplies the implementation that talks to **PluginRegistry** and the concrete plugin instances.

---

## Plugin contracts (olo-worker-plugin)

All executable plugins extend **ExecutablePlugin**:

- **execute(Map&lt;String, Object&gt; inputs, TenantConfig tenantConfig) → Map&lt;String, Object&gt;**  
  Inputs and outputs are contract-specific (e.g. model executor: `prompt` → `responseText`).

- **executionMode()**  
  Returns **ACTIVITY** (default) or **WORKFLOW** for where the plugin runs (Temporal activity vs in-workflow).

**Contract types** (aligned with pipeline scope `contractType`):

| ContractType | Interface | Typical use |
|--------------|-----------|-------------|
| **MODEL_EXECUTOR** | ModelExecutorPlugin | LLM chat/completion: prompt → responseText |
| **EMBEDDING** | EmbeddingPlugin | Text → embedding vector |
| **VECTOR_STORE** | VectorStorePlugin | Upsert/query/delete vectors |
| **IMAGE_GENERATOR** | ImageGenerationPlugin | Prompt + options → image |
| **REDUCER** | ReducerPlugin | Combine multiple plugin outputs into one |
| **SUBTREE_CREATOR** | (special) | Plan text → subtree (variables, steps) for dynamic execution |

Plugins register per tenant and id so that pipeline scope can reference them by the same id.

---

## Registry and lookup

- **PluginRegistry** (singleton): **tenantId → (pluginId → PluginEntry)**.  
  Methods: `registerModelExecutor(tenantId, id, plugin)`, `registerEmbedding(...)`, `registerVectorStore(...)`, `registerImageGenerator(...)`, `registerReducer(...)`, and generic `register(tenantId, id, contractType, version, plugin)`.  
  Lookup: **getExecutable(tenantId, pluginId)** or **getExecutable(tenantId, pluginId, nodeId, nodeInstanceCache)** for per-node instance reuse.

- **PluginManager**: Loads and registers plugins (internal providers, community JARs). **InternalPlugins** and **InternalTools** register internal implementations with the manager; the manager can also load from a controlled directory with **RestrictedPluginClassLoader** (plugin API + slf4j only).

- **PluginProvider** (SPI): Optional **ServiceLoader**-based discovery; **getVersion()** and **getCapabilityMetadata()** support compatibility and audit.

---

## Execution path

1. **Execution tree node** (e.g. PLUGIN, LLM_DECISION, EVALUATION) has a **pluginRef** (or evaluatorRef) and **inputMappings** / **outputMappings** (variable ↔ parameter).
2. **PluginHandler** / **ToolingExecutions** (in the worker engine) calls **PluginInvoker.invokeWithVariableMapping(pluginRef, inputVarToParam, outputParamToVar, variableEngine)**.
3. **PluginInvoker** builds the input map from the variable engine, calls **PluginExecutor.execute(pluginId, inputsJson, nodeId)**, then maps the output JSON back to variables.
4. **PluginExecutor** (implementation in olo-worker-plugin) uses **PluginRegistry.getExecutable(tenantId, pluginId)** (or with nodeId for caching), deserializes inputs, calls **ExecutablePlugin.execute(inputs, tenantConfig)**, serializes outputs to JSON and returns.

So: **tree → PluginInvoker → PluginExecutor (protocol) → RegistryPluginExecutor → PluginRegistry → ExecutablePlugin**.

---

## Threading and lifecycle

- Plugin instances are **run-scoped** (one per node when using nodeId; same node in a loop reuses the same instance). The engine runs nodes sequentially per run; do not rely on cross-node instance sharing.
- **ResourceCleanup** (annotations): plugins can implement **onExit()** for worker shutdown.

---

## Relationship to other docs

- **Execution tree:** Scope defines which plugins are available; nodes reference them by id. See [Execution tree design](../execution-tree/01_execution_tree_design.md).
- **Tools:** Tooling nodes (LLM_DECISION, EVALUATION, etc.) invoke plugins via the same PluginExecutor path. See [Tools architecture](tools.md).
- **Bootstrap:** Plugin registration (internal + community) happens during bootstrap; PluginExecutorFactory is wired into the worker runtime. See [Bootstrap](../bootstrap/README.md).

---

## Summary

| Concept | Location | Purpose |
|--------|----------|--------|
| PluginExecutor, PluginExecutorFactory | olo-worker-protocol | Contract for “execute by id, JSON in/out” and factory per tenant |
| ExecutablePlugin, ContractType | olo-worker-plugin | Base plugin contract and contract-type constants |
| PluginRegistry | olo-worker-plugin | Tenant-scoped registration and lookup by plugin id |
| RegistryPluginExecutor | olo-worker-plugin | Implements PluginExecutor using PluginRegistry |
| PluginManager | olo-worker-plugin | Internal/community loading and registration |
| PluginHandler | olo-worker (engine) | Dispatches PLUGIN nodes and calls PluginInvoker |
