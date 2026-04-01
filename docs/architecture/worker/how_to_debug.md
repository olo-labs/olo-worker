<!-- Copyright (c) 2026 Olo Labs. All rights reserved. -->

# How to debug workflows (input format and starting a run)

Part of the [architecture](../../README.md) documentation.

---

## Purpose

This guide explains how to:

1. **Use input JSON** from `configuration/debug/workflow/` to drive a workflow run.
2. **Start a workflow** via the Temporal UI or from an integration test.
3. **Dump the input and LocalContext** to a file when the environment is configured for debug, so you can inspect what the worker received and which pipeline context was resolved.

---

## 1. Prerequisites

- **Temporal** server running at the address in **`olo.temporal.target`** / **`OLO_TEMPORAL_TARGET`** (see `olo-defaults.properties`; map Docker ports so host:port matches).
- Namespace configured (e.g. **`default`**) to match **`olo.temporal.namespace`** / **`OLO_TEMPORAL_NAMESPACE`**.
- **Redis** and **PostgreSQL** if you use full bootstrap (see [How to debug bootstrap and global context](../bootstrap/how-to-debug.md)).
- **Environment** set from `configuration/debug/` (e.g. `source configuration/debug/env.example` or `call configuration\debug\env.example.bat`).

---

## 2. Workflow input format

The worker expects a single argument of type **`WorkflowInput`** (from the `olo-worker-input` module). Place sample input JSON files under **`configuration/debug/workflow/`** so you can reuse them for Temporal UI and integration tests.

### 2.1 Top-level fields

| Field       | Description |
|------------|-------------|
| `version`  | Payload schema version (e.g. `"1.0"`). |
| `inputs`  | Array of input items (name, type, storage, value). |
| `context` | Tenant, session, roles, etc. (`tenantId` must match a tenant allowed for the pipeline). |
| `routing` | `pipeline` (task queue / pipeline id), `transactionType`, `transactionId`, optional `configVersion`. |
| `metadata` | Optional (e.g. `ragTag`, `timestamp`). |

### 2.2 Task queue / pipeline

The **`routing.pipeline`** value must match a **task queue** the worker is polling. Task queue names come from **pipeline IDs** in the loaded configuration snapshot (see worker startup logs: `Registered worker for task queue: ...`). Examples:

- `default-pipeline`
- `order-processing`
- `settlement`

Use a pipeline that exists in your Redis/DB snapshot. For a fresh DB, ensure schema/seed scripts have run (bundled SQL in **`olo-worker-db`** with **`olo.db.schema.autoapply`**, or **`configuration/debug/ensure-schema.sql`** / **`schema-and-data.sql`**).

### 2.3 Sample input location

Put your JSON in:

```
configuration/debug/workflow/<your-file>.json
```

Example: `configuration/debug/workflow/sample-default-pipeline.json`. See the sample file in that folder for a minimal valid payload.

---

## 3. Starting a workflow through Temporal

You can trigger the workflow from the **Temporal Web UI** (manual) or via the **integration test** (automated: starts workflow, then compares the dump file).

### 3.1 Integration test (submit to Temporal server, start worker, compare dump)

The test **`WorkflowInputDebugIntegrationTest`** uses a **real Temporal server** and **starts the worker in-process** so it consumes the workflow:

1. Runs **bootstrap** (ensure-schema, Redis, DB) so the worker has pipelines.
2. Connects to the Temporal server (host/port from config / env).
3. **Starts the worker** (same process) polling the task queue for the sample pipeline in the test (see test source for the exact queue name).
4. Submits the workflow to the same server.
5. Worker consumes the task and completes the workflow; test waits for the result.
6. Shuts down the worker and compares the debug dump file to the sent input and expected LocalContext.

**Prerequisites:** Temporal server running; Redis and DB for bootstrap (same as other integration tests). The test sets **`olo.debug.dump.input.dir`** to **`build/debug-dump`** so the in-process worker writes dumps there.

Run:

```bash
./gradlew :olo-worker:workflowInputDebug
```

or

```bash
./gradlew :olo-worker:test --tests "org.olo.worker.WorkflowInputDebugIntegrationTest"
```

### 3.2 Temporal Web UI (manual)

1. Open the Temporal Web UI (e.g. `http://localhost:8233` or your Temporal server URL).
2. Select the **namespace** configured for the worker (e.g. `default`).
3. Go to **Workflows** → **Start Workflow** (or equivalent).
4. Set:
   - **Workflow Type:** `OloKernelWorkflow::run` (or the registered workflow type name your worker uses).
   - **Task Queue:** the queue matching `routing.pipeline` in your input (e.g. `olo.default.default-pipeline`).
   - **Workflow ID:** any unique id (e.g. `debug-run-1`).
5. **Input:** paste the contents of a JSON file from `configuration/debug/workflow/` as the **first (and only) argument**. It must be a single JSON object matching `WorkflowInput` (e.g. `version`, `inputs`, `context`, `routing`, `metadata`).
6. Start the workflow. The worker will run `processInput`, then resolve the execution plan and run the tree.

---

## 4. Starting a workflow from an integration test

To test input format and workflow start from tests under **`olo-worker/src/test/java/org/olo/worker`**:

1. **Take input JSON** from `configuration/debug/workflow/<file>.json`.
2. **Trigger a workflow** using the Temporal test client (or in-process worker) with that payload as `WorkflowInput`.
3. Optionally assert on the result or on side effects (e.g. debug dump file).

### 4.1 Example flow

- Read JSON from `configuration/debug/workflow/sample-default-pipeline.json` (or a path relative to project root / classpath).
- Deserialize to `WorkflowInput` with `WorkflowInput.fromJson(json)`.
- Start the workflow with `WorkflowClient.start(OloKernelWorkflow::run, workflowInput)` (or use a test environment that registers the worker and starts the workflow).
- When **env is configured for debug** (see below), the worker dumps the received input and resolved LocalContext to a file so you can inspect them.

See the integration test **`WorkflowInputDebugIntegrationTest`** in `olo-worker/src/test/java/org/olo/worker/WorkflowInputDebugIntegrationTest.java`: it loads input from `configuration/debug/workflow/sample-default-pipeline.json`, runs bootstrap, starts a workflow using Temporal’s test environment, sets `olo.debug.dump.input.dir` to `build/debug-dump`, and asserts that the dump file contains `workflowInput` and `localContext`.

---

## 5. Dumping input and LocalContext to a file (when env is configured)

When you need to inspect exactly what the worker received and which pipeline context was used, enable the **debug dump**.

### 5.1 Enabling the dump

Set **one** of:

- **Environment variable:** `OLO_DEBUG_DUMP_INPUT_DIR=<directory-path>`
- **System property:** `olo.debug.dump.input.dir=<directory-path>`

Example (Unix):

```bash
export OLO_DEBUG_DUMP_INPUT_DIR=olo-worker/build/debug-dump
```

Example (Windows):

```cmd
set OLO_DEBUG_DUMP_INPUT_DIR=olo-worker\build\debug-dump
```

When set, the worker will write a debug file for each workflow run that reaches `processInput`.

### 5.2 Dump location and contents

- **Directory:** the path you set (e.g. `olo-worker/build/debug-dump`). The worker creates the directory if it does not exist.
- **File name:** one file per run (e.g. `workflow-input-<transactionId>-<timestamp>.json` or similar) so multiple runs do not overwrite each other.
- **Contents:** a JSON object with:
  - **`workflowInput`:** the full workflow input as received (deserialized then serialized back to JSON).
  - **`localContext`:** summary of the resolved context: `effectiveQueue`, `tenantId`, and `pipelineIds` from the pipeline configuration (when resolution succeeds). If LocalContext cannot be resolved (e.g. wrong tenant or queue), the dump still contains `workflowInput` and a minimal or null `localContext` so you can see what was passed in.

Use this to verify input format, routing, and that the correct pipeline and tenant were resolved.

---

## 6. Quick reference

| Step | Action |
|------|--------|
| 1 | Put input JSON in `configuration/debug/workflow/<name>.json`. |
| 2 | Start workflow via Temporal UI (Workflow Type, Task Queue, Input) or via integration test. |
| 3 | Set `OLO_DEBUG_DUMP_INPUT_DIR` or `olo.debug.dump.input.dir` to dump input + LocalContext to a file. |

For bootstrap and global context debugging (Redis, DB, snapshot), see [How to debug bootstrap and global context](../bootstrap/how-to-debug.md).
