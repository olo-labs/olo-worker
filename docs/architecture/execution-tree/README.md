<!-- Copyright (c) 2026 Olo Labs. All rights reserved. -->

# Execution Tree Architecture

This folder documents the **execution tree**: the declarative tree of nodes that defines a pipeline's control flow, variable flow, plugin invocations, and feature attachment.

## Documents

| Document | Contents |
|----------|----------|
| **[01_overview](01_overview.md)** | Role of execution tree in bootstrap, PIPELINE_LOADING, relationship to configuration. |
| **[01_execution_tree_design](01_execution_tree_design.md)** | Full design: goals, tree structure, NodeType, variable model, scope, execution context, module layout. |

## Module (olo-execution-tree)

- **ExecutionTreeNode**, **NodeType**, **ExecutionMode** — Tree structure; immutable for replay safety.
- **VariableRegistry**, **VariableDeclaration**, **VariableScope** — Pipeline variable declarations.
- **Scope** — plugins and features for the pipeline.
- **PipelineDefinition** — name, contracts, variableRegistry, scope, executionTree root, resultMapping.
- **CompiledPipeline** — compiled, immutable pipeline instance (id, version, checksum, PipelineDefinition).
- **ExecutionConfigSnapshot** — Immutable snapshot per run (tenantId, queueName, pipeline).
- **VariableEngine**, **ExecutionContext** — Contracts for the execution layer.
- **ExecutionTreeCompiler** — Compile config (e.g. JSON) into ExecutionTreeNode, VariableRegistry, Scope.

## Relationship to Bootstrap

Execution trees are produced in **PIPELINE_LOADING**. They are validated in **VALIDATION** (e.g. all pluginRef in scope) and exposed to the runtime via the bootstrap context. The Execution Engine (in olo-worker) interprets the tree; this module holds only the config and structure.
