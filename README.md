<!-- Copyright (c) 2026 Olo Labs. All rights reserved. -->

# olo-worker (Temporal)

Gradle multi-module **Temporal worker** for the OLO platform. Java packages use the **`org.olo.*`** namespace.

## Modules (overview)

| Module | Role |
|--------|------|
| **`olo-worker`** | Application entrypoint: `org.olo.worker.OloWorkerApplication` — bootstrap, Temporal `WorkerFactory`, task queues from config |
| **`olo-worker-configuration`** | Defaults + env, Redis snapshots, `Bootstrap.run()` |
| **`olo-worker-db`** | JDBC / `DbClient`, optional **SQL schema bootstrap** (`db/schema/*.sql` on classpath) |
| **`olo-worker-bootstrap-loader` / `olo-worker-bootstrap-runtime`** | Startup wiring, execution context, plugin/feature registration |
| **`olo-execution-tree`** | Pipeline / execution tree model |
| **`olo-workflow-input`** | `WorkflowInput` JSON and parsers |
| **`olo-worker-input`**, **`olo-worker-plugin`**, **`olo-worker-protocol`**, … | Input, plugins, contracts |

See **`docs/architecture/README.md`** for deeper architecture links.

## Prerequisites

- **JDK 21** (Gradle uses Java toolchains; sources target **Java 17** API level)
- **Gradle** (wrapper: `gradlew` / `gradlew.bat`)

**Runtime dependencies** (typical local dev):

- **Temporal** — reachable at the address in `olo.temporal.target` (defaults are in `olo-worker-configuration/src/main/resources/olo-defaults.properties`, e.g. `localhost:47233` if you map the gRPC port that way; standard Temporal frontend is often **`7233`**).
- **Redis** — when configured, used for configuration snapshots and caches.
- **PostgreSQL** — when configured, used for tenant→region and snapshot build; DDL can be applied automatically (see below).

## Run the worker

From the **repository root** (the `run` task sets the working directory there):

```bash
./gradlew :olo-worker:run
```

On Windows:

```bat
gradlew.bat :olo-worker:run
```

### Configuration

- **Defaults file:** `olo-worker-configuration` → `olo-defaults.properties` on the classpath.
- **Environment overrides:** prefix **`OLO_`**, then the config key in `UPPER_SNAKE` → `olo.*` with underscores as dots (e.g. `OLO_TEMPORAL_TARGET` → `olo.temporal.target`).

Useful variables:

| Env / property | Meaning |
|----------------|---------|
| `OLO_TEMPORAL_TARGET` / `olo.temporal.target` | Temporal gRPC host:port |
| `OLO_TEMPORAL_NAMESPACE` / `olo.temporal.namespace` | Temporal namespace (e.g. `default`) |
| `OLO_REGION` / `olo.region` | Region this process serves |
| `OLO_DB_*`, `OLO_REDIS_*` | Database and Redis connection |
| `OLO_DB_SCHEMA_AUTOAPPLY` / `olo.db.schema.autoapply` | If `true` (default), **`olo-worker-db`** applies bundled `db/schema/*.sql` on startup (PostgreSQL). Seeds the **consensus** pipeline as **`olo.default.consensus-pipeline`** (same JSON as `configuration/debug/consensus-pipeline.json`). Point Chat BE (`OLO_TEMPORAL_TASK_QUEUE`) or the UI queue picker at that id so Temporal work lands on this worker. |

If Temporal is not running at the configured target, the process will fail when the worker connects or polls (see logs). Long-poll RPCs use an extended client timeout in `OloWorkerApplication` to reduce spurious `DEADLINE_EXCEEDED` noise on idle queues.

### Database schema

Bundled scripts live under:

`olo-worker-db/src/main/resources/db/schema/` (002–009, idempotent DDL + seeds).

With **`olo.db.schema.autoapply=true`**, they are executed in order after the connection pool is created. You can still apply **`configuration/debug/schema-and-data.sql`** or **`ensure-schema.sql`** manually for ad hoc or test databases.

## Verification and integration tests

Gradle **verification** tasks (require external services where noted — see **`docs/testing.md`**):

| Task | Purpose |
|------|---------|
| `:olo-worker:bootstrapDump` | Bootstrap + dump global context JSON (integration tag) |
| `:olo-worker:workflowInputDebug` | Workflow input debug scenario |
| `:olo-worker:consensusUseCase` | Multi-model consensus integration |
| `:olo-worker:refreshData` | Cleanup integration test |

```bash
./gradlew :olo-worker:bootstrapDump
```

Full list and prerequisites: **`docs/testing.md`**.

## Code layout (high level)

- **`olo-worker/src/main/java/org/olo/worker/`** — `OloWorkerApplication`, activities, engine, workflow implementation.
- **`configuration/debug/`** — sample env files, SQL, and workflow JSON for local debugging (not the only source of schema; see DB section above).

## Documentation

- **`docs/architecture/`** — configuration, bootstrap, worker, execution tree, workflow input.
- **`docs/testing.md`** — how to run tests and integration tasks.
- Module **`README.md`** files under `olo-worker-configuration`, `olo-worker-input`, etc.
