<!-- Copyright (c) 2026 Olo Labs. All rights reserved. -->

# How to debug bootstrap and global context

Part of the [bootstrap](README.md) documentation.

---

## Purpose

The **Bootstrap Global Context Dump** integration test runs full configuration bootstrap (Redis + DB), then writes the constructed global context to a JSON file. Use it to verify that bootstrap completed correctly and to inspect regions, snapshots, tenant→region mapping, pipelines, and connections.

This test is **not** included in the normal build cycle (`./gradlew build` / `check`) because it requires external resources (Redis and DB).

---

## 1. Prerequisites

- **Redis** running and reachable (configuration snapshot store).
- **PostgreSQL** (or compatible) running (tenant–region mapping and, for admin, snapshot source).
- **Environment or config** for Redis and DB connection (see below).

---

## 2. Configuration required

### 2.0 Ready-made files

The repository includes sample SQL, environment, and property files under **`configuration/debug/`**:

| File | Purpose |
|------|---------|
| **[configuration/debug/schema-and-data.sql](../../configuration/debug/schema-and-data.sql)** | Single SQL script: all tables (tenant→region, tenant config, resource config, worker config) plus sample data. Run once against your PostgreSQL database. |
| **[configuration/debug/env.example](../../configuration/debug/env.example)** | Environment variables for Unix/macOS. `source configuration/debug/env.example` or copy to `.env`. |
| **[configuration/debug/env.example.bat](../../configuration/debug/env.example.bat)** | Environment variables for Windows. `call configuration\debug\env.example.bat` before running the test. |
| **[configuration/debug/olo-debug.properties](../../configuration/debug/olo-debug.properties)** | Sample key=value overrides (reference only; not loaded automatically). |

See **[configuration/debug/README.md](../../configuration/debug/README.md)** for a quick start.

### 2.1 Environment / defaults

Ensure the worker can connect to Redis and DB. Typical keys (from `olo-defaults.properties` or ENV):

| Key / ENV | Purpose | Example |
|-----------|---------|---------|
| `olo.regions` / `OLO_REGIONS` (preferred) or `olo.region` / `OLO_REGION` (legacy) | Comma-separated regions this instance serves | `default,us-east` |
| `olo.db.schema.autoapply` / `OLO_DB_SCHEMA_AUTOAPPLY` | Apply bundled SQL from `olo-worker-db` on startup | `true` / `false` |
| `olo.redis.uri` or `olo.redis.host` + `olo.redis.port` | Redis connection | `redis://localhost:6379` |
| `olo.db.url` or `olo.db.host` + `olo.db.port` + `olo.db.name` | PostgreSQL JDBC URL | `jdbc:postgresql://localhost:5432/olo` |
| `olo.db.username` / `olo.db.password` | DB credentials | — |

Without Redis configured, bootstrap uses defaults + env only and does not load a snapshot from Redis.

### 2.2 Redis: configuration snapshot

Workers **do not** read configuration from the DB at bootstrap. They load a **snapshot from Redis**. That snapshot is normally built and pushed by an **admin service** (which reads from the DB and writes to Redis).

For the debug test to complete when Redis is configured:

- Redis must contain **snapshot metadata** and **core** (prefix depends on `olo.cache.root-key`, default root `olo`):
  - `<root>:config:meta`
  - `<root>:config:core`
- Optionally pipelines and connections for that region:
  - `olo:config:pipelines:<region>`

If these keys are missing, the worker blocks until they appear (or until `olo.bootstrap.config.wait.timeout.seconds` if set). See [configuration bootstrap](../../configuration/02_bootstrap.md).

### 2.3 Database: schema and data

The DB is used for **tenant→region** mapping (and by the admin service to build the snapshot). Ensure the schema exists and, for a useful dump, at least tenant–region rows.

**Recommended:** Run the ready-made script once:

```bash
psql -h localhost -U olo -d olo -f configuration/debug/schema-and-data.sql
```

This creates and populates:

| Table | Purpose |
|-------|---------|
| `olo_configuration_region` | Tenant ID → region (worker reads this; cached in Redis). **Required** for `tenantToRegion` in the dump. |
| `olo_configuration_tenant` | Per-tenant config overrides (admin uses to build snapshot). |
| `olo_config_resource` | Resource-scoped config (admin uses to build snapshot). |
| `olo_worker_configuration` | Worker-level keys (e.g. Temporal; optional). |

Individual SQL scripts (002–009) live in **`olo-worker-db/src/main/resources/db/schema/`** if you need to run them separately (they are also applied automatically at startup when `olo.db.schema.autoapply` is true).

---

## 3. Execute the debug test

### 3.1 From project root

1. Set environment (or rely on `olo-defaults.properties`). You can use the ready-made files:
   - **Unix/macOS:** `source configuration/debug/env.example`
   - **Windows:** `call configuration\debug\env.example.bat`
   - Or set manually, e.g. `OLO_REGIONS=default`, `OLO_REDIS_HOST=localhost`, `OLO_DB_URL=jdbc:postgresql://localhost:5432/olo`, `OLO_DB_USERNAME`, `OLO_DB_PASSWORD`.

2. Ensure Redis has a configuration snapshot for the worker region (meta + core). If you use an admin service, run it first; otherwise bootstrap will wait until Redis has the snapshot (or timeout if configured).

3. Run the **bootstrap dump** verification task (runs `BootstrapGlobalContextDumpTest` only):

   ```bash
   ./gradlew :olo-worker:bootstrapDump
   ```

   This is separate from the default `./gradlew :olo-worker:test` filter; see **`docs/testing.md`**.

### 3.2 Custom dump path (optional)

To write the JSON to a specific file:

```bash
./gradlew :olo-worker:bootstrapDump -Dolo.bootstrap.dump.output=path/to/global-context.json
```

---

## 4. Dump location and contents

### 4.1 Default location

- **Path:** `olo-worker/build/global-context-debug.json`
- **Override:** System property `olo.bootstrap.dump.output` (see above).

### 4.2 What the dump contains

The JSON is a snapshot of the **global context** after bootstrap:

| Top-level key | Description |
|---------------|-------------|
| `globalConfig` | Flat config map from the primary region (e.g. `olo.regions` / `olo.region`, `olo.temporal.*`, feature flags). |
| `primaryRegion` | The worker’s primary region. |
| `servedRegions` | List of regions this instance serves (from config). |
| `snapshotsByRegion` | Per-region snapshot summary: `snapshotId`, versions, core config keys, tenant/resource ids, `pipelineIds`, `connectionIds`. |
| `tenantToRegion` | Tenant ID → region map (from DB/Redis). Present if tenant–region was loaded. |

Use it to confirm regions, snapshot IDs, pipeline/connection registration, and tenant→region mapping after bootstrap.

### 4.3 Dump JSON example

Example shape of `global-context-debug.json` (actual dump may include additional fields such as `coreVersion`, `coreGlobalConfigKeys`, etc.):

```json
{
  "primaryRegion": "default",
  "servedRegions": ["default"],
  "globalConfig": {
    "olo.regions": "default",
    "olo.temporal.enabled": "true"
  },
  "snapshotsByRegion": {
    "default": {
      "snapshotId": "snapshot-123",
      "pipelineIds": ["pipelineA", "pipelineB"],
      "connectionIds": ["redis", "temporal"]
    }
  },
  "tenantToRegion": {
    "tenant1": "default",
    "tenant2": "us-east"
  }
}
```

---

## 5. Troubleshooting

| Symptom | Likely cause |
|---------|----------------|
| Test blocks or times out | Redis not reachable or snapshot (meta + core) missing for the worker region. Start Redis and ensure an admin has pushed the snapshot, or disable Redis for local-only config. |
| `tenantToRegion` empty | DB not configured, `olo_configuration_region` missing/empty, or Redis tenant-region cache empty and DB unreachable. Run the `002-olo_configuration_region.sql` script and insert rows. |
| “Configuration not set” / “No configuration snapshot loaded” | Worker region has no snapshot in Redis. Push snapshot for that region (admin) or set `olo.regions` / `olo.region` to a region that has snapshot data. |
| “Snapshot store factory is not registered” | Ports not registered: ensure the test runs with `DbPortRegistrar.registerDefaults()` and `CachePortRegistrar.registerDefaults()` (the integration test does this). |

For full bootstrap flow and Redis key layout, see [Configuration bootstrap](../../configuration/02_bootstrap.md) and the configuration architecture docs.
