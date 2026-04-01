<!-- Copyright (c) 2026 Olo Labs. All rights reserved. -->

# Operations and configuration guidelines

Part of the [olo-worker-configuration](olo-worker-configuration.md) documentation.

---

## Snapshot size limits and expectations

**Recommended limits (expectations):**

| What | Recommended max | Rationale |
|------|-----------------|------------|
| **Pipelines per region** | 2 000 | Above this, refresh and merge cost and heap usage grow. |
| **Connections per region** | 500 | Same as pipelines; contributes to snapshot size. |
| **Core snapshot (JSON)** | 1 MB | Very large core slows every load and refresh. |
| **Pipelines section (JSON)** | 5 MB | Single Redis value; very large payloads increase latency. |
| **Connections section (JSON)** | 1 MB | Same as core. |

These are **not enforced by default**. Optional enforcement via config keys:

- **`olo.config.snapshot.max.pipelines`** — Max pipeline count per region. **0 = no check.** If set and exceeded, worker logs a warning.
- **`olo.config.snapshot.max.connections`** — Max connection count per region. **0 = no check.** If set and exceeded, worker logs a warning.
- **`olo.config.snapshot.max.redis.value.bytes`** — Max size in bytes for a **single** Redis value (e.g. 8388608 = 8MB). **0 = no check.** When exceeded, the store rejects the value on read/write to protect Redis.
- **`olo.config.snapshot.max.size.bytes`** — Reserved for future enforcement.

Set limits in env (e.g. `OLO_CONFIG_SNAPSHOT_MAX_PIPELINES=2000`) if you want them applied on first load.

**Operational guidance:** Prefer smaller pipeline configs; split very large regions; monitor worker heap and GC.

---

## Region (local config + DB mapping)

Workers may serve **one or more regions** depending on deployment. Each configured region has an **independent configuration snapshot** in Redis. A single worker process can serve multiple regions (comma-separated) or a single region; deployment choice.

- **Local config key**: **`olo.regions`** (preferred) or legacy **`olo.region`** — comma-separated list of regions this instance serves.
- **ENV override**: `OLO_REGIONS=default,us-east` (or `OLO_REGION=...` for legacy)
- **DB table**: `olo_configuration_region` — `tenant_id` PK, `region`, `updated_at`. Index on `region`.
- **Redis cache**: Hash `olo:worker:tenant:region` — field = tenant ID, value = region.
- **Usage:** **Regions.getRegions(Configuration)**, **TenantRegionResolver.loadFrom(Configuration)** (once at bootstrap), **TenantRegionResolver.getRegion(tenantId)**.

**If a tenant moves region:** The resolver (and Redis cache `olo:worker:tenant:region`) must be updated; otherwise the worker will keep using the old region’s snapshot and the mapping becomes **stale**. Cache invalidation / refresh is done by **TenantRegionRefreshScheduler** (periodic) or by a Pub/Sub event (if supported). See below.

### TenantRegionResolver cache behavior and refresh

**Tenant region mapping refresh runs independently** of config snapshot refresh (e.g. every 60 sec or via event). Without it, the mapping would be stale when a tenant moves region. **TenantRegionRefreshScheduler** is responsible for keeping the tenant→region mapping up to date so that when a tenant moves region, workers eventually see the new region.

**Behavior:**

- **Periodic refresh:** Every N seconds (configurable), the scheduler reloads tenant→region from **DB** (or from the Redis cache that the admin updates). The worker’s in-memory view and cache are refreshed; then a config refresh is triggered so the worker loads the new region’s snapshot.
- **Pub/Sub (if supported):** On a region-change event, workers can refresh the tenant→region mapping immediately instead of waiting for the next interval.

If neither periodic refresh nor Pub/Sub is used, the mapping **may become stale** after a tenant move: workers will continue to use the old region until the process restarts or the cache is refreshed by some other means.

**Config and lifecycle:**

- **Config key**: `olo.tenant.region.refresh.interval.seconds` — **0 or unset = disabled.**
- When set &gt; 0, Bootstrap starts **TenantRegionRefreshScheduler**; it periodically reloads tenant→region and triggers config refresh so the new region's snapshot is loaded.
- Call **Bootstrap.stopTenantRegionRefreshScheduler()** on shutdown if started.

### Admin update flow (when region changes)

1. **DB update** — `UPDATE olo_configuration_region SET region = ?, updated_at = CURRENT_TIMESTAMP WHERE tenant_id = ?`. Or use `TenantRegionRepository.updateRegion(tenantId, region)`.
2. **Update Redis cache** — `TenantRegionCache.put(tenantId, region)`.
3. **Worker refresh** — Workers pick up on next TenantRegionRefreshScheduler cycle (or use Pub/Sub / shorten interval).

### Region mismatch protection

A worker may receive a request for a **tenant whose region is not served by this worker** (e.g. worker region = `us-east`, tenant region = `eu-west`).

**Rule:** If **tenantRegion is not served by the worker**, **reject the request** or **route to the correct region**. Do not serve config for a region this worker does not own.

Without this check:
- **Wrong configuration** — Tenant could get another region’s snapshot.
- **Cross-region bugs** — Data or behavior tied to the wrong region.

Implement by checking **Regions.getRegions(Configuration)** or the configured **`olo.regions` / `olo.region`** list before using **TenantRegionResolver.getRegion(tenantId)** to resolve config; if the resolved region is not in the worker’s served list, reject or redirect.

---

## Recommended monitoring metrics

To maintain the configuration and refresh pipeline, consider tracking:

| Metric | Purpose |
|--------|---------|
| **Snapshot reload count** | How often workers reload config (per region); detect refresh storms or stuck workers. |
| **Snapshot reload failures** | Failed reloads indicate missing/invalid snapshot or Redis issues. |
| **Redis refresh latency** | Time to fetch metadata and sections; surface Redis or network slowness. |
| **Snapshot size** | Core, pipelines, connections payload sizes; align with limits and capacity. |
| **Pipeline count per region** | Compare against recommended limits (e.g. 2 000); plan scaling. |

These help operators detect drift, capacity issues, and refresh failures early.

---

## Config key structure (all prefixed with `olo.`)

Avoid mixing styles (e.g. `olo.redis.host` with `db.host`). Use **`olo.`** everywhere:

- **`olo.temporal.*`** — Temporal (target, namespace, task_queue)
- **`olo.redis.*`** — Redis (host, port, uri, password)
- **`olo.db.*`** — DB (host, port, url, username, password, pool.size, type, name)
- **`olo.regions`** (preferred) / **`olo.region`** (legacy) — Comma-separated regions this instance serves (one or more; each has its own snapshot)
- **`olo.config.*`** — Refresh, snapshot limits, pub/sub
- **`olo.bootstrap.*`** — Startup wait and retry
- **`olo.configuration.checksum`** — Snapshot checksum validation
- **`olo.app.*`** — App env, log level

## Environment overrides

- **Prefix**: `OLO_`
- **Mapping**: Env name after the prefix → config key: **`olo.`** + **lowercase**, **underscore → dot** (e.g. `OLO_DB_HOST` → `olo.db.host`).
- **Order**: Defaults first; then every `OLO_*` env var overwrites the corresponding key.

Example:

```bash
OLO_DB_HOST=db.internal
OLO_REDIS_HOST=redis.internal
OLO_REGIONS=us-east
OLO_CONFIG_REFRESH_INTERVAL_SECONDS=30
```

---

## Public API summary

| Component | Description |
|-----------|-------------|
| **`Bootstrap.run()`** | Full bootstrap: defaults → env → wait for Redis (if configured) → build immutable snapshot; atomically install into ConfigurationProvider; tenant regions; optional refresh. Call once at startup. |
| **`Bootstrap.stopRefreshScheduler()`** / **`stopTenantRegionRefreshScheduler()`** | Call on shutdown if started. |
| **`ConfigurationProvider.get()`** / **`require()`** | Current in-memory config. |
| **`ConfigurationProvider.forTenant(tenantId)`** / **`forContext(tenantId, resourceId)`** | Config for tenant (resolved by tenant region). |
| **`TenantRegionResolver.loadFrom(Configuration)`** | Load tenant→region from Redis/DB (call once at bootstrap). |
| **`TenantRegionResolver.getRegion(tenantId)`** | Region for tenant, or `default`. |
| **`Regions.getRegions(Configuration)`** | List of regions from **`olo.regions`** or legacy **`olo.region`**. |
| **`FeatureFlags.isEnabled(flagKey, tenantId, pipelineId)`** | Feature flag with scope. |
| **Config keys** | See [02_bootstrap](02_bootstrap.md), [04_refresh](04_refresh.md), and size limits above. |
