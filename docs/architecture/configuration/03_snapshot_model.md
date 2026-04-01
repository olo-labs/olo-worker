<!-- Copyright (c) 2026 Olo Labs. All rights reserved. -->

# Snapshot model

Part of the [olo-worker-configuration](olo-worker-configuration.md) documentation.

---

## Redis sectioned snapshot model

**Workers only:** defaults → env → Redis **sectioned** config (core required; pipelines and connections optional). **Workers never rebuild config from DB.** On Redis miss or missing core at startup, the loader returns null and Bootstrap fails; no fallback to defaults + env or empty snapshot.

**Admin service only:** DB → build snapshot → write to **sectioned** Redis keys (core + meta; optionally pipelines, connections) → increment version.

---

## Redis key layout (canonical)

### Formal Redis schema

Configuration snapshot keys. **Implementations and tooling must use these key patterns** to avoid drift. `<region>` is the region id (e.g. `default`, `us-east`).

```
olo:config:meta
olo:config:core
olo:config:pipelines:<region>
olo:config:connections:<region>
olo:config:resources:<region>
olo:config:overrides:tenant:<tenantId>
```

There is **no** single key like `olo:config:<region>` holding the whole snapshot. Only the sectioned keys above. Optional future: **`olo:config:resources:<region>`** for resource-scoped config if needed.

**Legacy keys (read-only compatibility during migration):**

```
olo:configuration:meta:<region>
olo:configuration:core:<region>
olo:configuration:pipelines:<region>
olo:configuration:connections:<region>
```

### Key purposes

| Redis key | Purpose |
|-----------|---------|
| **`olo:config:meta`** | Metadata for change detection and snapshot control, including per-region versions (`snapshotsByRegion`). Workers read this first every refresh to decide if reload is needed. |
| **`olo:config:core`** | Core config (small + frequently accessed): global config + tenant→region routing (and optional small shared config). Required. |
| **`olo:config:pipelines:<region>`** | Pipeline definitions (frequent updates). Optional. |
| **`olo:config:connections:<region>`** | Connection configs (medium frequency). Optional. |
| **`olo:config:resources:<region>`** | Region-scoped resources (runtime resources / configs). Optional. |
| **`olo:config:overrides:tenant:<tenantId>`** | Tenant-specific overrides (lazy/cached). Optional. |

Explicit key layout for **operators**, **debugging**, **migrations**, and **tooling**. All keys use the **`olo:config:*`** prefix.

**TTL:** Redis keys do **not** use TTL. Snapshots are **persistent** until replaced by the admin service. Do not configure expiry on these keys; otherwise workers may see keys disappear and fail to load or refresh.

---

## Metadata value

The value at **`olo:config:meta`** is a JSON object. Workers read it first on every refresh and use it for **version check**, **change detection**, and **refresh logic**. **Section versions live only in this metadata** — there is no separate source; workers always get versions from this key.

### Metadata structure (explicit)

Metadata **must** contain version information for the snapshot and for each section so workers can decide whether to reload. Required logical shape:

| Field | Meaning |
|-------|---------|
| **generation** (or top-level **version**) | Global snapshot identity; admin increments on every write. |
| **updatedAt** / **lastUpdated** | Timestamp of last update (for debugging and change detection). |
| **sections** (or per-section blocks) | Version per section: **core**, **pipelines**, **connections**. Used for consistency checks and debugging; when the metadata version changes, workers reload the **entire** snapshot. |

Workers **compare the metadata version** (e.g. `regionalSettings.version` or a single snapshot version). When it changes, workers **reload the entire snapshot** (core + pipelines + connections). Section versions in metadata (`core.version`, `pipelines.version`, `connections.version`) are still useful for debugging and consistency checks, but the recommended strategy is **full snapshot reload** on any version change, not partial reload of only changed sections.

**Example (canonical shape):**

```json
{
  "generation": 1842,
  "checksum": "9fa34c9b3baf...",
  "regionalSettings": {
    "version": 7,
    "lastUpdated": "2026-03-06T11:00:00Z"
  },
  "core": {
    "version": 3,
    "lastUpdated": "2026-03-06T10:25:00Z"
  },
  "pipelines": {
    "version": 98,
    "lastUpdated": "2026-03-06T10:25:00Z"
  },
  "connections": {
    "version": 15,
    "lastUpdated": "2026-03-06T10:25:00Z"
  }
}
```

**Equivalent flattened shape** (same information; some implementations may use a single `sections` object):

```json
{
  "version": 42,
  "updatedAt": 171234234234,
  "sections": {
    "core": 12,
    "pipelines": 8,
    "connections": 3
  }
}
```

In both shapes, **section versions are inside the metadata value**. Workers read `olo:config:meta` once per refresh, then compare either the top-level / regionalSettings version or each section’s version with their local state to decide what to reload.

Workers use **version** (and **regionalSettings.version**) to decide whether to reload; **lastUpdated** and **checksum** support change detection and validation. This structure supports **version check** (workers reload entire snapshot when metadata version changes), **cluster-wide version identity** (generation, snapshotId), and **snapshot debugging** in distributed systems.

**generation vs regionalSettings.version:**

- **generation** — Global snapshot generation, incremented on every snapshot write by the admin. Useful for ordering and debugging.
- **regionalSettings.version** — Region-level configuration version used by **workers** to determine when a **full snapshot reload** is required. When this changes, workers reload the **entire snapshot** for that region. Partial reload (only changed sections) is not recommended; full reload is safer and avoids inconsistent snapshots.

Workers compare **regionalSettings.version** (or the single metadata version) to decide when to reload; generation is primarily for admin and debugging.

When `olo.configuration.checksum=true` (or env `OLO_CONFIGURATION_CHECKSUM=true`), workers compute SHA-256 of **canonical** `core + pipelines + connections` JSON and compare with metadata `checksum`. On mismatch, workers reject that snapshot and keep the current one. **JSON must be serialized using deterministic key ordering** (e.g. sorted keys) to ensure checksum consistency across admin and workers; otherwise two systems may compute different hashes.

**regionalSettings** is the region-level “global” version for the snapshot. When it changes, workers perform a **full snapshot reload** (not per-section). Use it for changes that affect the entire worker runtime, e.g. region routing rules, region-scoped feature flags, or resource availability in the region.

**Worker snapshot identity (snapshotId):** Workers expose **one** identity for debugging: **snapshotId** = `region:regionalSettings.version` (e.g. `us-east:7`). This helps with cluster rollouts, config mismatches, and worker restarts. **Log snapshotId at three events:** (1) snapshot loaded, (2) snapshot replaced, (3) worker startup.

### Snapshot version semantics

**SnapshotMetadata** (and each block inside meta) carries **version** and **updatedAt** (and **region** is implicit from the key). Workers should compare versions to avoid unnecessary reloads:

- **Rule:** `if new.version > current.version` (for the relevant block or regionalSettings) **then** replace (reload) that part of the snapshot; otherwise keep current.
- Workers track per-section versions locally. Only when a block’s version in Redis is **greater** than the local one do they fetch and replace that block. This avoids unnecessary reloads when nothing changed.

---

## Core key value

At **`olo:config:core`**: small, frequently accessed JSON with **global configuration** and **tenant → region routing** (and optionally small shared config). Region-specific configuration (pipelines, connections, resources) lives in region keys such as `olo:config:pipelines:<region>` and `olo:config:resources:<region>`. Use **resource IDs in `type:name` form** in resource overrides, e.g. `"pipeline:chat"`, `"connection:openai"`, `"model:gpt4"`, `"plugin:search"`. For a full example including merge model, see [01_architecture](01_architecture.md) (Resource overrides / merge model).

---

## Worker local: CompositeConfigurationSnapshot and execution trees

**Worker local:** **CompositeConfigurationSnapshot** (one reference; always build new composite and publish once — never replace sections individually):

```
CompositeConfigurationSnapshot
 ├─ CoreConfigurationSnapshot (ConfigurationSnapshot) — global, region, tenant, resource layers
 ├─ PipelineRegistry — pipeline id → config (from Redis pipelines section)
 └─ ConnectionRegistry — connection id → config
```

**The worker replaces the entire CompositeConfigurationSnapshot atomically.** Partial updates of individual sections are not allowed. This ensures thread safety and consistency across pipelines and connections.

**CompositeConfigurationSnapshot must be fully immutable.** Section maps (e.g. pipelines, connections) must be immutable. Prefer **`Map.copyOf(...)`** over `Collections.unmodifiableMap(...)`: `unmodifiableMap` wraps the original map, while `Map.copyOf()` guarantees a true immutable copy so the snapshot cannot be affected by later changes to the source. Otherwise use `Map.of()`. The loader should use `Map.copyOf(...)` before setting. Do not expose or store mutable maps.

Merge order for core: global → region → tenant → resource.

**API:** `forTenant(tenantId)` = merge global + region + tenant. `forContext(tenantId, resourceId)` = full merge including resource.

**Execution tree cache:** the worker compiles **PipelineRegistry** entries into immutable **CompiledPipeline** instances and stores them in a per-region, checksum-deduplicated cache (`region → pipelineId → CompiledPipeline`). On each snapshot load/refresh, the worker rebuilds the cache for affected regions and swaps it atomically so runtime execution always uses an up-to-date, in-memory view.

---

## Why sectioned only

| Feature | Benefit |
|--------|--------|
| Pipeline updates | Fast reload (only pipelines fetched) |
| Core | Rarely touched; core reload is rare |
| Connections | Isolated reload |
| Smaller Redis payload | Faster GETs |
| Less GC pressure | Significant when pipelines are large |

---

## Sectioned config: workers vs admin

**Workers:** defaults → env → **Redis sectioned only** (core, pipelines, connections, meta). Workers **never** touch DB for config. If Redis is configured, the worker never falls back to defaults + env only; Bootstrap waits until Redis is reachable and a valid snapshot exists (avoids config drift). On Redis miss or missing core at startup, loader returns null and Bootstrap fails.

**Admin service:** DB → **ConfigSnapshotBuilder.buildAndStore(region)** (in `impl.snapshot`) → write to **core key + meta** (and optionally pipelines, connections). Write order: data first (core, pipelines, connections), **meta last**. Increment the correct meta block version per change (core / pipelines / connections / regionalSettings). Workers pick up on next refresh cycle (full snapshot reload).

- **Redis keys (sectioned only):** `olo:configuration:core:<region>`, `olo:configuration:pipelines:<region>`, `olo:configuration:connections:<region>`, `olo:configuration:meta:<region>`. Optional future: add a version prefix for schema migration (e.g. `olo:configuration:v1:core:<region>`) if the snapshot schema changes.
- **DB table** `olo_config_resource`: used **only by admin** (ConfigSnapshotBuilder in `impl.snapshot`). Workers do not read it.
- **Worker Redis connection**: `olo.redis.uri` or `olo.redis.host` + `olo.redis.port` (from defaults + env).

**Snapshot consistency check:** Workers validate snapshot consistency before accepting a load: **metadata must exist**, **core section must exist**, and **any referenced sections** (e.g. pipelines, connections) that are present in metadata must exist. If any required section is missing, the snapshot load fails and the worker retries. This avoids loading incomplete snapshots.

**Snapshot consistency rule (version coherence):** All snapshot sections must be from the same logical snapshot. Workers must **load a snapshot only if all sections match the metadata version** (i.e. the versions in metadata for core, pipelines, and connections are consistent with the sections being loaded). If any section version differs from what metadata declares, or sections appear to be from different write batches, the worker should **reject the snapshot** and retry on the next cycle. Example: metadata says `core.version = 42`, `pipelines.version = 42`, `connections.version = 42` — the worker must not mix an old core with a new pipelines section. Rejecting on version mismatch prevents **partial snapshot bugs** (inconsistent config across sections).

If Redis is not configured, worker uses defaults + env only.
