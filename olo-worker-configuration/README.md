<!-- Copyright (c) 2026 Olo Labs. All rights reserved. -->

# olo-worker-configuration

Loads configuration at bootstrap from a **defaults file** and **environment variables**, keeps a copy **in memory**, and serves it when needed.

**Architecture docs:** See `docs/architecture/configuration/` — architecture, bootstrap, snapshot model, refresh, admin service, operational guidelines.

## Usage

1. **Bootstrap** (e.g. in `main`): call **`Bootstrap.run()`** once. It loads defaults + env and, when Redis is configured, waits for the config store and loads the snapshot. If Redis is empty but **DB** and snapshot **ports** are registered (as in **olo-worker**), bootstrap may **build or backfill** Redis from the database **at startup only**. When Redis is not configured, it sets in-memory config from defaults + env only.

   ```java
   Bootstrap.run();
   ```

2. **Read config** anywhere:

   ```java
   Configuration config = ConfigurationProvider.get();
   String dbHost = config.get("olo.db.host", "localhost");
   int port = config.getInteger("olo.db.port", 5432);
   ```

   Or use `ConfigurationProvider.require()` to throw if config was not loaded.

## Config key structure (all prefixed with `olo.`)

Use a single style everywhere; do not mix prefixed and unprefixed keys.

| Namespace | Purpose |
|-----------|---------|
| `olo.temporal.*` | Temporal client (target, namespace, task_queue) |
| `olo.redis.*` | Redis (host, port, uri, password) |
| `olo.db.*` | Database (host, port, url, username, password, pool.size, type, name) |
| `olo.region` | Region this instance serves (one per process) |
| `olo.db.schema.autoapply` | When `true`, `olo-worker-db` applies bundled `db/schema/*.sql` on startup (PostgreSQL family only). Env: `OLO_DB_SCHEMA_AUTOAPPLY` |
| `olo.config.*` | Refresh, snapshot limits, pub/sub |
| `olo.bootstrap.*` | Startup wait and retry |
| `olo.configuration.checksum` | Snapshot checksum validation |
| `olo.app.*` | App env, log level |

## Default values file

- **Resource**: `olo-defaults.properties` on the classpath (e.g. `src/main/resources/olo-defaults.properties`).
- Defines default values for all keys under the namespaces above.
- If the resource is missing, only ENV overrides are used.

## Environment overrides

- **Prefix**: `OLO_`
- **Mapping**: env name after the prefix → config key: **`olo.`** + lowercase, underscore → dot.

Example (very clean 1:1 with config keys):

```bash
OLO_DB_HOST=db.internal
OLO_REDIS_HOST=redis.internal
OLO_REGION=us-east
OLO_CONFIG_REFRESH_INTERVAL_SECONDS=30
```

## API

- **`Bootstrap.run()`** — full bootstrap: defaults + env; when Redis configured, load snapshot and set provider. Call once at startup.
- **`Bootstrap.loadConfiguration()`** — load from classpath defaults + ENV only (e.g. for tests). Does not set `ConfigurationProvider`.
- **`Bootstrap.loadConfiguration(InputStream)`** — load from custom defaults stream + ENV (e.g. for tests).
- **`ConfigurationProvider.get()`** / **`ConfigurationProvider.require()`** — access the in-memory config after bootstrap.
- **`Configuration`** — `get(key)`, `get(key, default)`, `getInteger`, `getLong`, `getBoolean`.
