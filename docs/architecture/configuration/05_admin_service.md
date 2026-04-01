<!-- Copyright (c) 2026 Olo Labs. All rights reserved. -->

# Admin service

Part of the [olo-worker-configuration](olo-worker-configuration.md) documentation.

---

## Configuration resource table

**`olo_config_resource`** — used **only by the admin service**. Each row represents configuration for a **specific resource** (e.g. pipeline, connection, model, plugin) within a tenant or region scope. This connects DB rows to the resource-based config model (global → region → tenant → resource). **ConfigSnapshotBuilder** (admin only) reads from this table, builds the snapshot, and writes to Redis. Workers never read this table for config.

---

## Admin service contract

After any change to `olo_config_resource`, the admin service **must** call **ConfigSnapshotBuilder.buildAndStore(region)** so the snapshot is written to Redis and the version is incremented.

**Transaction rule:** DB changes and snapshot generation should occur in the same logical operation. The admin should **commit DB changes before** calling **ConfigSnapshotBuilder**, so the snapshot reflects the latest committed state. Otherwise the snapshot might be built from uncommitted data.

**Snapshot write order (critical):**

> **IMPORTANT**  
> Workers treat the **metadata** key as the snapshot availability signal. The admin service **must** always write **core, pipelines, connections** first and write the **meta** key **last**. This prevents partial snapshot visibility. See [04_refresh](04_refresh.md).

**Version increment:** Admin must increment the correct metadata block version so workers reload correctly:
- **core.version** — core configuration changes
- **pipelines.version** — pipeline updates
- **connections.version** — connection updates
- **regionalSettings.version** — region-wide configuration changes

For full version semantics and refresh behavior, see [04_refresh](04_refresh.md).

Use **MULTI/EXEC** when writing sectioned keys (data first, meta last). Workers never rebuild config from DB; they only read the Redis snapshot.

**Eventual consistency:** Multiple DB updates may be batched before building a snapshot. Workers only observe changes **once the snapshot is written to Redis**.

**Pub/Sub:** If Pub/Sub refresh is enabled, the admin service should **publish a refresh event** after writing the snapshot to Redis (e.g. to `olo:configuration:updates` or sectioned channels). This triggers immediate worker refresh instead of waiting for the next polling interval. See [04_refresh](04_refresh.md).

**Failure handling:** If the Redis write fails, snapshot generation should be **retried**. Workers continue using the previous snapshot until a new snapshot is successfully written.

---

## ConfigSnapshotBuilder

- **Location:** `org.olo.configuration.impl.snapshot.ConfigSnapshotBuilder`
- **Usage:** After updating and committing DB (e.g. `olo_config_resource`), call **`buildAndStore(region)`**. The **region** parameter determines **which regional snapshot** is built; each region maintains an independent configuration snapshot in Redis.

**ConfigSnapshotBuilder responsibilities:**

1. Load configuration resources from DB for the given region
2. Build multilayered core configuration (global, region, tenant, resource)
3. Build pipeline and connection registries
4. Compute metadata (versions, checksum, generation)
5. Write sectioned snapshot to Redis (core, pipelines, connections, meta — **meta last**)

**Worker visibility:** Workers see the change on the next refresh (periodic or Pub/Sub-triggered).
