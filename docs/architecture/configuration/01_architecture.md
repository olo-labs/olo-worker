<!-- Copyright (c) 2026 Olo Labs. All rights reserved. -->

# Configuration architecture

Part of the [olo-worker-configuration](olo-worker-configuration.md) documentation.

---

## Region model (introduced first)

**Tenants belong to a region.** Workers run one region per process. Workers load **only their region’s snapshot** from Redis. Pipelines and connections are **region-scoped** (e.g. `olo:config:pipelines:us-east`), while core config is global (`olo:config:core`). This is why **TenantRegionResolver.getRegion(tenantId)** is used before config access—to know which region’s snapshot to use. Region is configured via **`olo.region`** (defaults + ENV) and optionally via DB table `olo_configuration_region` (tenant → region mapping).

---

## Module overview

This module is responsible for **loading configuration during bootstrap**, keeping a **copy in memory** (like DB/cache configuration), and **serving it when needed**. Default values are defined in a separate file; environment variables override them when present.

The rest of the OLO platform (e.g. `olo-worker`) depends on this module and uses a single bootstrap step plus in-memory reads—no repeated file or ENV access.

---

## Configuration architecture (layers)

```
             ┌────────────────────┐
             │   PostgreSQL DB    │
             │  (admin config)    │
             └─────────┬──────────┘
                       │
                       │ build snapshot
                       ▼
             ┌────────────────────┐
             │        Redis       │
             │  Config Snapshots  │
             └─────────┬──────────┘
                       │
                       │ bootstrap load
                       ▼
             ┌────────────────────┐
             │    OLO Worker      │
             │ Local Snapshot     │
             └─────────┬──────────┘
                       │
                       │ runtime access
                       ▼
               Worker components
```

**Key rule:** **Workers do not read configuration from the database during workflow execution.** At **bootstrap**, **`Bootstrap.run()`** uses **defaults → env → Redis snapshot**; if Redis is configured but empty, **DB may be used once** to build or backfill Redis (e.g. **`ConfigSnapshotBuilder`**, **`PipelineSectionBuilder`**) when those port implementations are registered. After bootstrap, **only** in-memory **`ConfigurationProvider`** state is used for runs.

- **Workers**: defaults → env → Redis snapshot. If Redis is configured, Bootstrap waits until Redis is reachable and a valid snapshot exists (or can be built from DB when implemented). At **runtime**, if Redis is unavailable during refresh, workers keep the current snapshot and retry next cycle.
- **Admin service** (or bootstrap seed path): DB → build snapshot (**ConfigSnapshotBuilder**) → write Redis snapshot → increment version. Workers see the change on next refresh.
- **Worker runtime (per activity/workflow):** uses only `ConfigurationProvider.get()` / `require()`, `config.forTenant(tenantId)`, and `TenantRegionResolver.getRegion(tenantId)`. No Redis, no DB **for config reads** on the hot path. If the tenant’s region is not served by this worker, reject the request or route to the correct region to avoid wrong configuration and cross-region bugs. See [06_operational_guidelines](06_operational_guidelines.md#region-mismatch-protection).

Use **Bootstrap.run()** at worker startup. Use **ConfigSnapshotBuilder.buildAndStore(region)** (in `org.olo.configuration.impl.snapshot`) in the admin service after updating DB. Implementation classes live under **`org.olo.configuration.impl`** and subpackages (`impl.connection`, `impl.config`, `impl.refresh`, `impl.snapshot`, `impl.source`, `impl.region`); API types remain in `configuration`, `configuration.snapshot`, `configuration.source`, and `configuration.region`.

### Startup vs runtime

| Phase | Behavior |
|--------|----------|
| **Startup** | If Redis is configured, the worker never falls back to defaults + env only. Configuration store (Redis) must be available **and** snapshot (meta + core) must be present; worker **blocks** until both are true. This avoids config drift across workers. |
| **Runtime** | If Redis is unavailable during a refresh cycle: **keep current snapshot**, log warning, skip cycle. Workers continue processing requests; configuration reload resumes when Redis returns. |

### Worker startup flow (Redis meta-first)

When Redis snapshot is enabled, workers should follow a **meta-first** boot sequence to avoid fetching large JSON blobs unnecessarily:

1. Read **`olo:config:meta`**
2. Determine **served regions** (from `servedRegions` / `snapshotsByRegion`)
3. Load **`olo:config:core`** once (global + tenant→region routing)
4. Load **`olo:config:pipelines:<region>`** for each served region
5. Load **`olo:config:resources:<region>`** for each served region (if used)
6. Load **`olo:config:overrides:tenant:<tenantId>`** lazily or via a small cache (only when tenant seen)
7. Build effective pipelines (template + tenant override) and optionally cache effective results

**Required at startup when Redis is configured:** Redis (configuration snapshot). DB only if tenant–region mapping or other bootstrap data requires it.

### Bootstrap dependency rule

**Workers require Redis snapshot availability at startup.**

**During bootstrap:**

- Worker waits until Redis is reachable
- Worker waits until the configuration snapshot exists (meta + core for the region)
- Worker does not start with partial configuration

**During runtime:**

- Redis failures do not stop the worker
- Existing configuration snapshot remains active
- Refresh resumes when Redis becomes reachable again

### Bootstrap wait loop: exit conditions

The worker waits until **both** are true: Redis is reachable **and** the configuration snapshot exists. “Snapshot exists” means **snapshot metadata** (`olo:config:meta`) and **core configuration section** (`olo:config:core`) are present in Redis (plus region sections such as `olo:config:pipelines:<region>` if used). Without a timeout, if the admin never initializes the snapshot (or Redis is never available), workers wait forever.

**Timeout behavior** (`olo.bootstrap.config.wait.timeout.seconds`):

- **If timeout &gt; 0 and exceeded:** Worker exits with non-zero status; the orchestrator (e.g. Kubernetes) restarts the container.
- **If timeout = 0 or unset:** Worker waits indefinitely.

Set a finite timeout in production so orchestration can restart workers when the store becomes ready.

---

## Multilayered configuration (scopes)

Large systems rarely have a single config scope. Configuration is merged from multiple layers; later scopes override earlier ones.

### Where config is stored (classification)

| Scope | Stored where |
|-------|----------------|
| **Infrastructure** | defaults + ENV (e.g. `olo-defaults.properties`, `OLO_*`) |
| **Global platform config** | Redis snapshot |
| **Region config** | Redis snapshot |
| **Tenant overrides** | Redis snapshot |
| **Resource overrides** | Redis snapshot |

**Infrastructure config** (Temporal endpoint/namespace, DB connection, Redis/cache connection, region list) lives in **defaults + ENV only** and **does not go into the Redis snapshot**. The snapshot holds only platform config (pipeline timeouts, feature flags, tenant/resource overrides, etc.).

### Scopes (example — snapshot layers only)

| Scope    | Example use |
|----------|-------------|
| **Global**  | Platform defaults (e.g. pipeline timeouts, feature flags) |
| **Region**  | Region-specific platform config (e.g. region-scoped feature flags, region routing) |
| **Tenant**  | Tenant features and overrides |
| **Resource**| Pipeline, connection, model, or plugin level (see resource naming below) |

### Resource naming (formalized)

Resource IDs use **`type:name`** (colon, not dot). This aligns with OLO moving toward resource plugins and keeps a clear namespace per type.

| Form       | Example | Use |
|------------|---------|-----|
| `pipeline:<name>`  | `pipeline:chat`   | Pipeline config / overrides |
| `connection:<name>`| `connection:openai` | Connection config |
| `model:<name>`     | `model:gpt4`      | Model config |
| `plugin:<name>`    | `plugin:search`   | Plugin config |

**Not:** `pipeline.chat` (dot is ambiguous with nested keys). **Use:** `pipeline:chat`.

In config JSON, **resourceOverrides** keys are resource IDs: e.g. `"pipeline:chat"`, `"connection:openai"`. The value is a map of config keys (e.g. `model`, `timeout`).

### Merge model

```
global
   ↓
region
   ↓
tenant
   ↓
resource
```

**Final config** = merged result (each layer overrides the previous). Keys are flat (e.g. `pipeline.timeout`, `model`, feature flag keys). **Infrastructure config** (Temporal, DB, Redis) is **not** in the snapshot; it stays in defaults + ENV.

### Resource overrides (scope)

**resourceOverrides exist inside a scope** (e.g. inside core’s tenant layer or global). They are not global-only. Example core JSON structure showing all four layers (**global**, **regionOverrides**, **tenantOverrides**, **resourceOverrides**):

```json
{
  "global": {
    "pipeline.timeout": 60
  },
  "regionOverrides": {
    "us-east": {
      "olo.feature.streaming.enabled": true
    }
  },
  "tenantOverrides": {
    "tenantA": {
      "pipeline.timeout": 120,
      "resourceOverrides": {
        "pipeline:chat": {
          "model": "gpt4"
        }
      }
    }
  }
}
```

Merge order: global → region → tenant → resource. So **resourceOverrides** in the core JSON apply in the scope where they appear (e.g. global vs tenant-scoped in your snapshot shape). Use **resource IDs in `type:name` form** (e.g. `pipeline:chat`, `connection:openai`). **API:** `forContext(tenantId, "pipeline:chat").get("model")` = merge global → region → tenant → resource for that pipeline.

### OLO examples (snapshot layers)

- **Global** — platform defaults (e.g. `pipeline.timeout`, `olo.feature.streaming.enabled`)
- **Region** — `regionOverrides.us-east` (region-scoped overrides, e.g. feature flags per region)
- **Tenant** — `tenantA.pipeline.timeout` (tenant override for key `pipeline.timeout`)
- **Resource** — `pipeline:chat` with key `model`: use `forContext(tenantId, "pipeline:chat").get("model")`

**API:** Use `ConfigurationProvider.require().forContext(tenantId, resourceId)` with resource IDs in `type:name` form (e.g. `pipeline:chat`, `connection:openai`). `forTenant(tenantId)` is equivalent to `forContext(tenantId, null)`.

---

## Feature flags

Feature flags use the same multilayered config and allow enable/disable of platform features at different scopes.

### Goals

- Enable / disable platform features
- Enable features **per tenant**
- Enable features **per region** (via region layer)
- Enable features **temporarily** (config change + snapshot refresh)
- Enable features **gradually** (per tenant or per pipeline rollout)

### Scope model (three scopes)

| Scope    | Example |
|----------|---------|
| **Global**  | Streaming enabled for everyone |
| **Tenant**  | Enable tool execution for a specific tenant |
| **Pipeline**| Enable streaming only for certain pipeline |

**Hierarchy:** global → tenant → pipeline. Final flag value = merged result (later scope overrides earlier). Pipeline maps to config **resource** using formalized naming (e.g. `resourceId = "pipeline:chat"`).

### Example flag keys

- `olo.feature.streaming.enabled`
- `olo.feature.tool_execution`
- `olo.feature.pipeline_cache`
- `olo.feature.dynamic_model_selection`
- `olo.feature.pipeline_debug`

### Reading flags

Use the existing Configuration merge; flags are boolean config keys.

- **Global:** `ConfigurationProvider.require().getBoolean("olo.feature.streaming.enabled", false)`
- **Per tenant:** `ConfigurationProvider.require().forTenant(tenantId).getBoolean("olo.feature.tool_execution", false)`
- **Per tenant + pipeline:** `ConfigurationProvider.require().forContext(tenantId, pipelineId).getBoolean("olo.feature.streaming.enabled", false)`

Or use **FeatureFlags.isEnabled(flagKey, tenantId, pipelineId)** for a consistent API.

**Caching:** Feature flags are resolved from the **same in-memory configuration snapshot** as the rest of config. They incur **no remote lookup** at resolution time—zero runtime cost beyond the already-loaded snapshot.

---

## Responsibilities

- **Load at bootstrap**: Defaults (olo-defaults.properties) → env (OLO_*) → RedisSnapshotLoader (in `impl`; sectioned Redis). When Redis is configured, worker waits until store and snapshot are available; no fallback. When Redis is not configured, defaults + env only.
- **Store in memory**: Hold config in ConfigurationProvider (composite or snapshot); thread-safe via volatile reads.
- **Serve on demand**: Expose via `Configuration` (and `forTenant` / `forContext`) wherever needed (Temporal, pipelines, feature flags).
- **Default values file**: `olo-defaults.properties` defines defaults; ENV overrides with prefix `OLO_`.

---

## Configuration sources

Workers use exactly three steps. There is no plugin pipeline; Redis loading is done in **Bootstrap** via **RedisSnapshotLoader**, not via a loader chain.

**Flow:**

```
defaults
   ↓
env
   ↓
RedisSnapshotLoader
   ↓
ConfigurationProvider
```

**Sources (used by Bootstrap for defaults + env only; implementations in `impl.source`):**

- **DefaultsConfigurationSource** — loads `olo-defaults.properties` (region, Redis URI, db.*, etc.).
- **EnvironmentConfigurationSource** — loads `OLO_*` env vars (overrides).

Bootstrap then calls **RedisSnapshotLoader.load(region, store)** (in `impl.refresh`) to load from Redis (sectioned: core, pipelines, connections, meta) and sets **ConfigurationProvider**. If Redis is configured, the worker never falls back to defaults + env only; Bootstrap waits until Redis is reachable and a valid snapshot exists. If Redis is unavailable or core is missing at load time, the loader returns null and Bootstrap fails. Avoiding fallback is important to prevent config drift across workers.

**Worker path:** Defaults and env are loaded via **Bootstrap** (using **DefaultsConfigurationSource** and **EnvironmentConfigurationSource**); Redis is loaded in Bootstrap via **RedisSnapshotLoader** only. There is no separate ConfigurationLoader; Bootstrap owns the full flow.

---

## Module structure

**Package layout:** API types stay in `org.olo.configuration`, `org.olo.configuration.snapshot`, `org.olo.configuration.source`, and `org.olo.configuration.region`. All **implementations** live under **`org.olo.configuration.impl`** and small subpackages.

```
org.olo.configuration
├── Bootstrap.java
├── Configuration.java
├── ConfigurationConstants.java
├── ConfigurationProvider.java
├── FeatureFlags.java
├── Regions.java
│
├── snapshot/                        # API: interfaces and value types
│   ├── ConfigurationSnapshotStore.java
│   ├── ConfigurationSnapshotRepository.java
│   ├── ConfigurationSnapshot.java
│   ├── SnapshotMetadata.java, BlockMetadata.java
│   ├── CompositeConfigurationSnapshot.java
│   ├── PipelineRegistry.java
│   └── ConnectionRegistry.java
│
├── source/
│   └── ConfigurationSource.java    # API only
│
├── region/
│   ├── TenantRegionResolver.java
│   ├── TenantRegionRepository.java # interface
│   └── TenantRegionCache.java       # interface
│
└── impl/                            # implementations (org.olo.configuration.impl)
    ├── connection/                  # impl.connection — Redis/DB connection params
    │   └── ConnectionConfig.java
    ├── config/                      # impl.config — Configuration implementations
    │   ├── DefaultConfiguration.java
    │   └── SnapshotConfiguration.java
    ├── refresh/                     # impl.refresh — config refresh and Redis snapshot loading
    │   ├── ConfigRefreshScheduler.java
    │   ├── ConfigRefreshPubSubSubscriber.java
    │   └── RedisSnapshotLoader.java
    ├── snapshot/                    # impl.snapshot
    │   ├── RedisConfigurationSnapshotStore.java  # in olo-worker-cache
    │   ├── NullConfigurationSnapshotStore.java
    │   ├── ConfigSnapshotBuilder.java   # admin only
    │   └── ResourceSnapshotRepository.java # in olo-worker-db
    ├── source/                      # impl.source
    │   ├── DefaultsConfigurationSource.java
    │   ├── EnvironmentConfigurationSource.java
    │   └── ...
    └── region/                      # impl.region
        ├── TenantRegionRefreshScheduler.java
        └── ...
```

**Worker path:** defaults → env → **RedisSnapshotLoader** (in `impl.refresh`) only. Bootstrap loads defaults + env and, when Redis is configured, loads the snapshot; no separate loader pipeline.

### Atomic snapshot replacement

**Snapshots are immutable.** When a new snapshot is loaded (on bootstrap or refresh), **ConfigurationProvider** swaps the reference **atomically** (single volatile write). Workers always see a consistent snapshot; there is no partial update. Never mutate or replace sections individually on the live snapshot—always build a new composite and publish once.

---

## Dependencies

**Worker dependencies:** **Lettuce** (Redis), **Jackson** (JSON for snapshot), **SLF4J** (logging). Workers never use DB for config.

**Admin service dependencies:** **HikariCP** + **PostgreSQL** (table `olo_config_resource`), **Lettuce**, **Jackson**, **SLF4J**. Admin builds snapshot from DB and writes to Redis.

If Redis is not configured, the worker runs with defaults + ENV only (no snapshot wait). If Redis is configured but DB is not, bootstrap cannot build missing snapshots from DB; ensure Redis is pre-populated by an admin process or migration.
