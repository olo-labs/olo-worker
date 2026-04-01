<!-- Copyright (c) 2026 Olo Labs. All rights reserved. -->

# Tools architecture

Part of the [architecture](../../README.md) documentation. Describes how **tools** (tooling nodes) are defined, registered, and executed in the worker.

---

## Role

Tools are execution-tree node types that support **LLM workflows**, **evaluations**, **templating**, and **control flow** without requiring custom plugin code for each step. They are implemented in the worker engine and may delegate to plugins (e.g. model executor) via `pluginRef` / `evaluatorRef`.

- **Node types:** `EVENT_WAIT`, `LLM_DECISION`, `TOOL_ROUTER`, `EVALUATION`, `REFLECTION`, `FILL_TEMPLATE`.
- **Handler:** A single **ToolingHandler** in the node dispatcher routes these types to **ToolingExecutions**.
- **Internal tools:** Optional tool *providers* (e.g. research, critic, evaluator, echo) can be registered with **PluginManager** as internal providers so they appear as plugins by `pluginRef` in the tree.

---

## Node types and semantics

| NodeType       | Purpose | Key params | Behavior |
|----------------|--------|------------|----------|
| **EVENT_WAIT** | Wait for an event/variable to be set | `resultVariable` | In activity mode: no blocking; returns existing value from `resultVariable` or null. |
| **LLM_DECISION** | Call an LLM (model executor) | `pluginRef`, `promptVariable`, `outputVariable` | Maps prompt variable → plugin `prompt`, plugin `responseText` → output variable. |
| **TOOL_ROUTER** | Route by value to one of several child branches | `inputVariable`, children with `caseValue`/`toolValue` | Reads `inputVariable`; runs the child whose case matches, or first child as default. |
| **EVALUATION** | Run an evaluator plugin | `evaluatorRef`, `inputVariable`, `outputVariable` | Maps input → plugin `input`, plugin `result`/`score` → output variable. |
| **REFLECTION** | Call a model for reflection (e.g. critique) | `pluginRef`, `inputVariable`, `outputVariable` | Same mapping as LLM: input → `prompt`, `responseText` → output. |
| **FILL_TEMPLATE** | Fill a prompt template with user query | `templateKey` or `template`, `userQueryVariable`, `outputVariable` | Resolves template via **PromptTemplateProvider**; replaces `{{user_query}}`; writes to output variable. |

All of these use the **variable engine** for input/output and the **PluginInvoker** (and thus **PluginExecutor** / **PluginRegistry**) when a `pluginRef` or `evaluatorRef` is involved.

---

## Execution flow

1. **Node dispatcher** sees a node with one of the tooling types and delegates to **ToolingHandler**.
2. **ToolingHandler** calls the appropriate **ToolingExecutions** static method (`executeLlmDecision`, `executeToolRouter`, etc.).
3. **ToolingExecutions** reads params (e.g. `pluginRef`, `promptVariable`), gets/sets variables via **VariableEngine**, and for plugin-based nodes calls **HandlerContext.getPluginInvoker().invokeWithVariableMapping(...)**.
4. **PluginInvoker** resolves the plugin by id (from **PluginRegistry**), builds input map from variable→parameter mapping, executes the plugin, and maps outputs back to variables.

So tools are **engine-level** behavior; plugins are **capabilities** (model executor, evaluator, etc.) registered per tenant and invoked by ref.

---

## Internal tools (tool providers)

Some “tools” are implemented as **plugin-like providers** so they can be referenced by `pluginRef` in the tree:

- **Module:** `olo-internal-tools-include` (and related tool modules).
- **Registration:** **InternalTools.registerInternalTools(PluginManager)** registers providers (e.g. research, critic, evaluator, echo) with **PluginManager** as internal providers. This is typically called after **InternalPlugins.createPluginManager()** so internal tools are available alongside other internal plugins.
- **ToolProvider** (from `org.olo.tools`) is the contract; **PluginManager** exposes them so **PluginRegistry** / plugin execution can resolve and run them like other plugins.

Internal tools are **not** a separate execution path: they are plugins that happen to be shipped as “tools” and registered as internal providers. The execution tree still references them by `pluginRef` and the same **PluginExecutor** / **PluginRegistry** path is used.

---

## Relationship to other docs

- **Execution tree:** Node types and variable model are in [Execution tree design](../execution-tree/01_execution_tree_design.md). **TOOL_ROUTER**, **LLM_DECISION**, etc. are defined there.
- **Plugins:** Plugin contracts, registry, and execution are in [Plugin architecture](plugins.md). Tools that call a model or evaluator go through the plugin layer.
- **Features:** Pre/post hooks (e.g. for observability) are in [Features architecture](features.md). Tooling nodes can have features attached like any other node.

---

## Summary

| Concept | Location | Purpose |
|--------|----------|--------|
| NodeType (tooling) | olo-execution-tree | EVENT_WAIT, LLM_DECISION, TOOL_ROUTER, EVALUATION, REFLECTION, FILL_TEMPLATE |
| ToolingHandler | olo-worker (engine) | Dispatches tooling node types to ToolingExecutions |
| ToolingExecutions | olo-worker (engine) | Implements semantics for each tooling type; uses VariableEngine and PluginInvoker |
| InternalTools | olo-internal-tools-include | Registers tool providers with PluginManager so they are available as plugins by pluginRef |
| PromptTemplateProvider | olo-worker-protocol | Contract for template resolution used by FILL_TEMPLATE |
