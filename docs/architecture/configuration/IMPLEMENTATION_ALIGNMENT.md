<!-- Copyright (c) 2026 Olo Labs. All rights reserved. -->

# Implementation vs documentation alignment

This document summarizes how the codebase aligns with the configuration architecture docs (`01_architecture` through `06_operational_guidelines`, `olo-worker-configuration`). It is updated when implementation or docs change.

---

## Aligned

| Doc / behavior | Implementation | Notes |
|----------------|-----------------|--------|
| **Redis key schema** | `ConfigurationSnapshotStore` / `RedisConfigurationSnapshotStore`: `META_KEY`, `CORE_KEY`, `PIPELINES_KEY_PREFIX`, `CONNECTIONS_KEY_PREFIX`, `RESOURCES_KEY_PREFIX`, `TENANT_OVERRIDES_KEY_PREFIX` | Keys match formal schema: `olo:config:meta`, `olo:config:core`, `olo:config:pipelines:<region>`, `olo:config:connections:<region>`, `olo:config:resources:<region>`, `olo:config:overrides:tenant:<tenantId>` (legacy `olo:configuration:*` still readable). |
| **Bootstrap flow** | `Bootstrap.run()`: defaults → env → `TenantRegionResolver.loadFrom` when Redis/DB → wait for Redis + snapshot (optional DB build/backfill of Redis) → load snapshot → `ConfigurationProvider` + snapshot map; optional refresh + Pub/Sub + TenantRegionRefreshScheduler | Order matches doc; DB used only at bootstrap when ports + JDBC configured. |
| **Atomic snapshot install** | `ConfigurationProvider`: `snapshotByRegion` and `primaryRegion` are `volatile`; `setSnapshotMap` / `putComposite` replace in one write | Matches “atomically install snapshot into ConfigurationProvider”. |
| **Version check before reload** | `RedisSnapshotLoader.refreshIfNeeded` → `refreshRegion`: GET meta, then `isNewerVersionAvailable(current, meta)`; if unchanged, return | Matches “compare version; if same skip”. |
| **Full snapshot reload** | `RedisSnapshotLoader.refreshRegion`: when version changed, always `fetchAndBuildFullComposite` (full reload). No partial/section-wise reload | Aligned with “reload entire snapshot” (partial reload removed). |
| **Load failure handling** | On `loadSectioned` / `fetchAndBuildFullComposite` returning null: do not replace snapshot; log error; retry next cycle | Matches “discard reload; keep previous snapshot; log error”. |
| **Redis unavailable** | `refreshRegion` catch: log warning, keep current snapshot, retry next cycle | Matches doc. |
| **ConfigRefreshScheduler** | Single-thread executor, fixed-delay, debounce for Pub/Sub, “already running” skip | Matches refresh strategy and behavior. |
| **Tenant region mapping refresh** | `TenantRegionRefreshScheduler`: runs independently (e.g. every N s); reloads tenant→region then triggers config refresh | Matches “tenant region mapping refresh runs independently”. |
| **Admin write order** | `RedisConfigurationSnapshotStore.put()`: MULTI → SET core → SET meta → EXEC | Data (core) first, meta last. If pipelines/connections are written elsewhere, they must be before meta. |
| **Checksum validation** | `RedisSnapshotLoader.validateChecksum`: when `olo.configuration.checksum` true, compare with metadata checksum; on mismatch do not apply snapshot | Matches snapshot consistency / checksum rule. |

---

## Gaps / callouts

| Doc | Implementation | Recommendation |
|-----|----------------|----------------|
| **Region mismatch protection** | `ConfigurationProvider.forContext(tenantId, resourceId)` uses `TenantRegionResolver.getRegion(tenantId)` and returns config for that region. If the worker does not serve that region, `getComposite(tenantRegion)` may be null and code falls back to `get()` (primary region), which can be wrong config | **Application layer** should reject or route when tenant’s region is not in `Regions.getRegions(Configuration)`. Optionally, add an explicit check in the worker entry (e.g. workflow or API) before calling `forTenant` / `forContext`. |
| **Snapshot consistency rule (version coherence)** | Docs say: reject snapshot if section versions differ from metadata. Implementation validates presence of meta + core and checksum; does not explicitly compare each section version to metadata block version before accept | Consider adding a strict check that loaded section versions match metadata block versions; currently consistency is implied by full reload and checksum. |
| **Admin: pipelines/connections keys** | `RedisConfigurationSnapshotStore.put(region, snapshot)` only writes **core** and **meta**. Pipelines and connections are not written by this `put()` | If admin writes pipelines/connections in another path, ensure write order remains: core, pipelines, connections, **meta last**. |

---

## Summary

- **Redis keys, bootstrap, atomic install, version check, full reload, load failure, Redis unavailable, scheduler, tenant-region refresh, admin write order, and checksum** are aligned with the docs.
- **Region mismatch**: Doc says “reject or route” if tenant region not served; implementation does not enforce this. Callers should check `Regions.getRegions()` and reject or route.
- **Snapshot version coherence**: Doc recommends rejecting when section versions do not match metadata; implementation relies on full reload + checksum; optional hardening is to add an explicit version-match check.
- **Admin store** currently writes only core + meta in `put()`; any separate pipelines/connections writes must keep “meta last”.

Implementation was updated so that **refresh always performs a full snapshot reload** (no partial reload) and **load failures are logged** and result in keeping the current snapshot and retrying next cycle.
