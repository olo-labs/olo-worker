<!-- Copyright (c) 2026 Olo Labs. All rights reserved. -->

# Refresh

Part of the [olo-worker-configuration](olo-worker-configuration.md) documentation.

---

## Snapshot refresh strategy

Workers refresh configuration using **two mechanisms**:

**Polling**

Every **`olo.config.refresh.interval.seconds`** (default 60):

1. Read meta key — `GET olo:configuration:meta:<region>`
2. Compare version — metadata version vs locally loaded version
3. Reload **entire snapshot** if changed — fetch core, pipelines, connections, build new composite, atomic swap; otherwise skip. When reloading, workers must accept only snapshots that satisfy the [snapshot consistency rule](03_snapshot_model.md#sectioned-config-workers-vs-admin) (all sections match metadata version).

**Pub/Sub trigger**

Workers subscribe to a refresh channel (e.g. **`olo:configuration:refresh`** or `olo:configuration:updates`). Admin publishes after writing the snapshot, for example:

```json
{ "region": "us-east", "version": 42 }
```

Workers for that region **reload the snapshot immediately** instead of waiting for the next polling interval. See [Event-driven refresh (Pub/Sub)](#event-driven-refresh-pubsub) below.

---

## Refresh flow

**Version check before reload:** Workers poll **`olo:configuration:meta:<region>`** every refresh cycle. They compare the **metadata version** (e.g. `regionalSettings.version` or a single snapshot version) with the locally loaded version. **If the version differs**, the worker reloads the **entire snapshot**; **if the same**, the cycle is skipped. This avoids unnecessary Redis reads when nothing has changed.

**Reload entire snapshot (avoid partial reload):** When the metadata version changes, workers must **reload the entire snapshot** (core + pipelines + connections). Do **not** reload only changed sections. Partial reload is error-prone: e.g. core and pipelines are both updated in Redis, but the worker reloads only core and keeps old pipelines → inconsistent snapshot. Even with large configs, full snapshots are small enough that full reload is the safer and simpler strategy.

Rule in steps:

1. Worker reads meta (`GET olo:configuration:meta:<region>`).
2. Compare metadata version with local.
3. If version changed → **reload entire snapshot** (fetch core, pipelines, connections; build new composite; atomic swap).
4. If same → skip; no fetches this cycle.

### Explicit refresh algorithm

Every refresh interval (default 60s), the scheduler runs the following. This is **critical operational behavior**:

1. **Read snapshot metadata from Redis** — `GET olo:configuration:meta:<region>` for each served region.
2. **Compare metadata version with local snapshot version** — If unchanged, skip this cycle.
3. **If version changed:**  
   - **Reload entire snapshot** — Fetch core, pipelines, and connections from Redis; build a **new** CompositeConfigurationSnapshot; replace the snapshot in ConfigurationProvider in **one** volatile write. Do not fetch only “changed” sections or reuse unchanged section references; that can lead to inconsistent state (e.g. new core with old pipelines).
4. **If Redis unavailable:**  
   - Log warning.  
   - Skip this refresh cycle.  
   - Keep current snapshot and retry next cycle.
5. **If snapshot loading fails** (e.g. missing section, invalid data, parse error):  
   - **Discard the reload** — do not replace the current snapshot.  
   - Keep the **previous snapshot**.  
   - Log error (e.g. missing pipelines section, checksum mismatch).  
   - Retry during the next refresh cycle.

**Rule:** If snapshot loading fails, the worker **keeps the current snapshot** and retries during the next refresh cycle. This prevents configuration corruption (e.g. partial or invalid config replacing a good snapshot).

**Simplified logic:** If the metadata version changed → **reload entire snapshot**. Otherwise skip. No partial reload. On any load failure, keep current snapshot and retry next cycle.

When a new snapshot is loaded successfully, **ConfigurationProvider replaces the current snapshot atomically**. Workers immediately see the new configuration without restarting. This avoids thread-safety concerns.

**Pseudocode:**

```
meta = GET olo:configuration:meta:<region>
if meta unavailable: keep existing snapshot, log warning, retry next cycle
if metadata.version (e.g. regionalSettings.version) not changed: return
fetch core, pipelines, connections from Redis
if any section missing or load/parse fails: discard reload, keep current snapshot, log error, retry next cycle
build new composite snapshot
publish new composite once (atomic swap)
```

**Runtime refresh behavior:** If Redis is unreachable, any store call fails, or snapshot loading fails (missing section, invalid data), the worker keeps the current snapshot, logs a warning or error, and retries on the next cycle. The worker continues running with the last good configuration.

### Refresh failure handling

**Example scenario:** Meta version = 42; worker starts reload; **pipelines section is missing** (or core/connections fail to load, or checksum mismatch).

**Worker behavior:**

- **Discard the reload** — do not apply a partial or invalid snapshot.
- **Keep the previous snapshot** — continue serving traffic with the last known-good configuration.
- **Log an error** — e.g. missing section, parse failure, or checksum mismatch.

**Rule:** If snapshot loading fails, the worker keeps the current snapshot and retries during the next refresh cycle. This prevents configuration corruption (e.g. replacing a good snapshot with an incomplete or invalid one).

---

## Redis atomic update and write order

**Redis atomic update:** The admin service should use a **Redis MULTI/EXEC transaction** so all keys are updated atomically.

**Write order guarantee:** Admin must write **data first, meta last**. Set core, pipelines, and connections before meta. **Meta must always be last.**

> **IMPORTANT**  
> The admin service **must** always write snapshot **data** first (core, pipelines, connections) and **metadata last**. Workers treat the presence of metadata as the snapshot availability signal. Writing meta before data would allow workers to load incomplete snapshots. Workers also validate that metadata and core (and any referenced sections) exist before accepting a snapshot; see [03_snapshot_model](03_snapshot_model.md).

**Note:** MULTI/EXEC does not prevent readers from seeing old keys before EXEC completes. **Versioned snapshot keys** are safer and prevent partial reads entirely (see [03_snapshot_model](03_snapshot_model.md)).

Example (correct order):

```
MULTI
SET olo:config:core ...
SET olo:config:pipelines:<region> ...
SET olo:config:connections:<region> ...
SET olo:config:meta ...
EXEC
```

**Version increment rules (admin):** When the admin updates Redis, it must increment the correct meta block version:

| Change | Version to increment |
|--------|----------------------|
| Core config changed | **core.version** |
| Pipeline added/updated | **pipelines.version** |
| Connection updated | **connections.version** |
| Region infrastructure change | **regionalSettings.version** |

---

## Event-driven refresh (Pub/Sub)

**Recommended architecture:** After writing the snapshot to Redis, the admin service publishes a refresh event. Workers subscribed to the channel run an immediate refresh instead of waiting for the next poll. This is implemented by **ConfigRefreshPubSubSubscriber** on the worker side.

```
Admin service
     │
     │ snapshot update
     ▼
Redis snapshot
     │
     │ publish event
     ▼
olo:configuration:refresh  (or olo:configuration:updates / sectioned channels)
     │
     ▼
Workers reload immediately
```

**Benefits:**

- **Near-instant config rollout** — Workers pick up changes as soon as the admin publishes, without waiting for the next polling interval.
- **Avoids polling delay** — Reduces latency for config changes (e.g. pipeline or connection updates).
- **Common pattern** in distributed config systems (event-driven propagation).

**Fallback:** Workers **must still run periodic polling** (e.g. every 60s via **ConfigRefreshScheduler**). Polling is the safety fallback when Pub/Sub is unavailable, a message is missed, or the worker joins after the event was published.

---

**Implementation:** Admin publishes to a dedicated channel (e.g. `olo:configuration:refresh` or sectioned channels `olo:configuration:<section>:<region>`) after writing snapshot keys. On a Pub/Sub message, workers trigger an immediate refresh. Example: channel `olo:configuration:updates`, message `{"region": "us-east", "generation": 1843}` — workers for that region run a refresh cycle.

**Debounce protection:** If a refresh is already running, an incoming Pub/Sub trigger is ignored. Optionally, Pub/Sub triggers are delayed by `olo.config.refresh.pubsub.debounce.ms` (default 1500 ms) so that when admin updates pipeline, connection, and core in quick succession, workers run at most one refresh after the burst. Set to 0 for no delay.

**Snapshot replacement:** One volatile reference to the composite. Always **build a new composite**, then **publish once** (`snapshot = newComposite`). When a new snapshot is loaded, ConfigurationProvider replaces the current snapshot **atomically**; workers immediately see the new configuration without restarting. Never mutate or replace sections individually on the live snapshot. The composite must be **fully immutable** (section maps via `Map.copyOf(...)` or `Map.of()`).

---

## ConfigRefreshScheduler

Runs on a **dedicated single-thread executor** (`config-refresh-thread`). Every N seconds (with jitter) read Redis metadata; build a **new immutable composite**; publish in one volatile write.

- **Config keys**: `olo.config.refresh.interval.seconds` (default 60), `olo.config.refresh.interval.jitter.seconds` (default 10).
- **Pub/Sub keys**: `olo.config.refresh.pubsub.enabled`, `olo.config.refresh.pubsub.channel.prefix`, `olo.config.refresh.pubsub.debounce.ms` (default 1500).
- **Behaviour**: Check meta. If metadata version changed → **reload entire snapshot** (fetch core, pipelines, connections; build new composite). Replace ConfigurationProvider's composite in **one** volatile write. No partial reload.
- **Fail-safe**: If any load fails, **keep current snapshot**, log error, retry next cycle.
- Call **Bootstrap.stopRefreshScheduler()** on shutdown.

---

## Meta-first refresh and pipelining

**GET meta first; only if version changed fetch all sections (full reload).** This keeps the refresh algorithm simple and avoids partial-reload bugs. **RedisSnapshotLoader.refreshIfNeeded** implements this. **RedisConfigurationSnapshotStore** provides **getCorePipelinesConnectionsBatch(region)** for pipelined fetch of the three section keys in one round-trip.

---

## Redis client lifecycle (Lettuce)

**Worker must use a singleton Redis client and a shared connection.** Bootstrap creates one via `client.connect()` and passes it to **RedisConfigurationSnapshotStore**; the store uses it for all operations. **ConfigRefreshScheduler** receives the shared store from bootstrap. No new RedisClient or connection per refresh.

---

## Other refresh details

- **Startup wait timeout:** `olo.bootstrap.config.wait.timeout.seconds` — max time to wait for Redis + snapshot at startup; 0 = forever.
- **Refresh thread isolation:** Config refresh runs on **config-refresh-thread**; never block worker threads or Temporal pools.
- **Refresh duration guard:** Next run is scheduled only after the current run completes (fixed-delay semantics); runs never overlap.
- **Pipeline/connections memory:** On reload, the loader fetches all sections and builds a new composite; no reuse of previous section references (full snapshot reload).
