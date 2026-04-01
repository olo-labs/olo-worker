<!-- Copyright (c) 2026 Olo Labs. All rights reserved. -->

# Features architecture

Part of the [architecture](../../README.md) documentation. Describes how **features** (pre/post node hooks) are defined, registered, and executed in the worker.

---

## Role

Features are **hooks** that run before and after a tree node executes. They are used for observability (logging, metrics), policy (quota, compliance), and cross-cutting behavior (ledger, audit) without encoding that logic in every node. Features are attached to nodes via the execution tree (node/pipeline scope) and resolved at runtime by **FeatureAttachmentResolver**; the engine runs them in order via **NodeFeatureRunner**.

- **Module:** **olo-worker-features** (contracts and **FeatureRegistry**); **olo-worker** runs pre/post using **NodeFeatureRunner** and **FeatureAttachmentResolver**.
- **Registration:** Classes annotated with **@OloFeature** implement one or more phase interfaces and are registered with **FeatureRegistry** (e.g. at bootstrap). **InternalFeatures** registers internal feature instances.

---

## Phases and contracts

Execution order around a node: **PRE → node execution → POST_SUCCESS / POST_ERROR → PRE_FINALLY → FINALLY**.

| Phase | Contract | When run |
|-------|----------|----------|
| **PRE** | PreNodeCall | Before the node runs |
| **POST_SUCCESS** | PostSuccessCall or PreFinallyCall | After the node completes without throwing |
| **POST_ERROR** | PostErrorCall or PreFinallyCall | After the node throws |
| **PRE_FINALLY** | PreFinallyCall | Before FINALLY; receives success or error |
| **FINALLY** | FinallyCall | Always after the node (cleanup, logging) |

- **PreNodeCall:** `before(NodeExecutionContext context)`  
  Use for: validation, quota check, setting up context. Internal features can block (throw); community features are observer-only.

- **PostSuccessCall:** `afterSuccess(NodeExecutionContext context, Object nodeResult)`  
  Use for: ledger success, metrics, post-processing on result.

- **PostErrorCall:** `afterError(NodeExecutionContext context, Object nodeResult)`  
  Use for: ledger failure, error metrics. `nodeResult` may be the exception or error payload.

- **PreFinallyCall:** `afterSuccess(...)` and `afterError(...)`  
  One implementation can handle both success and error (e.g. common cleanup or logging).

- **FinallyCall:** `after(NodeExecutionContext context, Object nodeResult)`  
  Use for: non–exception-prone cleanup, logging, metrics that should always run.

**NodeExecutionContext** is immutable: node id, type, attributes, tenant id, tenant config, queue name, plugin id (for PLUGIN nodes), and execution outcome (for post phases). Community features must not mutate execution state.

---

## Privilege: INTERNAL vs COMMUNITY

| Privilege | Who | Allowed | On throw |
|-----------|-----|--------|----------|
| **INTERNAL** | Kernel, fat JAR | Block execution, mutate context, affect failure, ledger, quota, policy | Exception propagated; run fails |
| **COMMUNITY** | Observer-only | Read context, log, emit metrics, append attributes | Caught and logged; execution continues |

- **ObserverPreNodeCall** / **ObserverPostNodeCall**: Optional observer-only interfaces for community features; the executor treats them as non-blocking and catches throws.
- **FeatureRegistry.register(instance)** defaults to INTERNAL; **register(instance, FeaturePrivilege.COMMUNITY)** for observer-only.

---

## Attachment and resolution

Features are attached to a node from several sources (merged in a defined order):

1. **Node explicit lists:** `preExecution`, `postSuccessExecution`, `postErrorExecution`, `finallyExecution` (and legacy `postExecution` merged into all three post lists).
2. **Node’s features:** `features` (list of feature names); each feature’s phase(s) come from **FeatureRegistry**.
3. **Pipeline/scope:** Pipeline scope feature names and queue-based rules (e.g. queue name ending with `-debug` adds the `debug` feature).
4. **Required:** `featureRequired` (always included).
5. **Exclusion:** `featureNotRequired` (names to omit). Duplicates are dropped (first occurrence wins).

**FeatureAttachmentResolver.resolve(node, queueName, pipelineScopeFeatureNames, registry)** returns **ResolvedPrePost**: separate lists for pre, postSuccess, postError, and finally. The engine uses these lists to call **NodeFeatureRunner** in order.

---

## Execution flow

1. **Before node:** Engine calls **NodeFeatureRunner.runPre(resolved, context, registry)** for each name in `resolved.getPreExecution()`; for each, looks up the feature in **FeatureRegistry** and, if it implements **PreNodeCall**, calls `before(context)`. Community features: exceptions are caught and logged.
2. **Node runs** (dispatcher, handler, plugin/tooling as applicable).
3. **After node:** Depending on success or failure, engine calls **runPostSuccess**, **runPostError**, then **runFinally** (and **PreFinallyCall** where applicable) with **NodeExecutionContext** and node result. Each list is iterated in order; the same feature can implement multiple phases (e.g. **PreFinallyCall** for both success and error).

---

## Registration and discovery

- **@OloFeature(name, phase, applicableNodeTypes):** Annotation on the feature class; **FeatureRegistry** reads it when registering an instance.
- **FeatureRegistry.register(featureInstance)** or **register(featureInstance, privilege)**. The instance must implement the contract(s) for its phase(s).
- **InternalFeatures** (olo-internal-features-include): Registers internal feature instances (e.g. debug, ledger, quota) at bootstrap so they are available by name in the registry.

---

## Relationship to other docs

- **Execution tree:** Node fields `preExecution`, `postSuccessExecution`, `features`, `featureRequired`, `featureNotRequired` and scope features are defined in [Execution tree design](../execution-tree/01_execution_tree_design.md).
- **Bootstrap:** Feature registration runs during bootstrap; see [Bootstrap](../bootstrap/README.md).
- **Plugins / Tools:** Features wrap any node type, including PLUGIN and tooling nodes; see [Plugins](plugins.md) and [Tools](tools.md).

---

## Summary

| Concept | Location | Purpose |
|--------|----------|--------|
| OloFeature, FeaturePhase | olo-annotations | Annotation and phase enum for feature registration |
| PreNodeCall, PostSuccessCall, PostErrorCall, FinallyCall, PreFinallyCall | olo-worker-features | Phase contracts |
| ObserverPreNodeCall, ObserverPostNodeCall | olo-worker-features | Observer-only contracts for community features |
| FeatureRegistry | olo-worker-features | Global registry; register by instance, resolve by name and phase |
| FeatureAttachmentResolver | olo-worker-features | Resolve effective pre/post lists for a node (scope, queue, required, exclusion) |
| NodeFeatureRunner | olo-worker (engine) | Run pre and post hooks in order using FeatureRegistry |
| NodeExecutionContext | olo-worker-features | Immutable context passed to every hook |
| InternalFeatures | olo-internal-features-include | Registers internal feature instances at bootstrap |
