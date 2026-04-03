<!-- Copyright (c) 2026 Olo Labs. All rights reserved. -->

# Debug / sample configuration

Ready-made SQL, environment, and property files for **local bootstrap debugging** (e.g. running the [bootstrap global-context dump test](../../docs/architecture/bootstrap/how-to-debug.md)).

## Files

| File | Purpose |
|------|---------|
| **[schema-and-data.sql](schema-and-data.sql)** | PostgreSQL schema and sample data: tenant→region, tenant config, resource config, worker config. Run once against your DB. |
| **[ensure-schema.sql](ensure-schema.sql)** | Idempotent script (CREATE TABLE IF NOT EXISTS, INSERT ... ON CONFLICT DO NOTHING). **Executed automatically** by the bootstrap integration test when DB is configured. |
| **[consensus-pipeline.json](consensus-pipeline.json)** | Multi-Model Consensus pipeline definition. **Canonical copy** is also seeded as **`olo.default.consensus-pipeline`** via **`db/schema/007-seed_default_pipeline.sql`** (and `ensure-schema.sql`) so Redis/DB match for Chat BE / UI. |
| **[workflow/](workflow/)** | Sample workflow input JSON files. Use with Temporal UI or the workflow-input debug integration test. See [How to debug workflows](../../docs/architecture/worker/how_to_debug.md). |
| **[env.example](env.example)** | Environment variables (Unix/macOS). `source env.example` or copy to `.env`. |
| **[env.example.bat](env.example.bat)** | Environment variables (Windows). `call env.example.bat` before running tests. |
| **[olo-debug.properties](olo-debug.properties)** | Sample key=value overrides. Use as reference or merge into `olo-defaults.properties`; not loaded automatically. |

## Quick start

1. **Database:** Run the schema and data:
   ```bash
   psql -h localhost -U olo -d olo -f configuration/debug/schema-and-data.sql
   ```

2. **Environment:** Set Redis and DB connection (Unix):
   ```bash
   source configuration/debug/env.example
   ```
   Windows:
   ```cmd
   call configuration\debug\env.example.bat
   ```

3. **Redis:** Ensure a configuration snapshot exists for your region (meta + core). The admin service normally pushes this from DB to Redis; for local debug you may need to run an admin or pre-populate Redis.

4. **Run the dump test** (requires Redis, DB, and matching env — see architecture docs):
   ```bash
   ./gradlew :olo-worker:bootstrapDump
   ```
   Output: `olo-worker/build/global-context-debug.json` (unless overridden).

**Note:** With **`olo.db.schema.autoapply=true`** (default in `olo-defaults.properties`), the worker applies SQL from **`olo-worker-db/src/main/resources/db/schema/`** on startup. The scripts here in `configuration/debug/` are still useful for one-shot setup and tests that run raw SQL (e.g. `ensure-schema.sql`).

See **[How to debug bootstrap and global context](../../docs/architecture/bootstrap/how-to-debug.md)** for full steps and troubleshooting.
