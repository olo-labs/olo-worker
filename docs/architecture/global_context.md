<!-- Copyright (c) 2026 Olo Labs. All rights reserved. -->

# Global Context

The **global context** (`org.olo.bootstrap.loader.context.GlobalContext`) is the view of configuration and compiled pipelines used after **`Bootstrap.run()`**. Tenant→region comes from **`TenantRegionResolver`** (Redis/DB at bootstrap); pipeline definitions and merges come from the **Redis-backed configuration snapshot** and **ExecutionTreeRegistry**. It is the contract tests and tooling use alongside **`ConfigurationProvider`**.

## Structure

```
GlobalContext
├── Regions
│   └── <region-id> (e.g. us-east)
│       └── pipelines
│           ├── <pipeline-id> v<version> (active)
│           └── ...
├── Tenants
│   └── <tenant-id> → region <region-id>
└── TenantOverrides
    └── <tenant-id>
        └── <pipeline-id>:v<version> (override applied on top of region template)
```

## Example

```
GlobalContext
   Regions
      us-east
          pipelines
              order-processing v2
              settlement v1

   Tenants
      tenant-A → region us-east
      tenant-B → region us-east

   TenantOverrides
      tenant-A
          order-processing:v2
```

- **Region us-east** has two active pipelines: **order-processing** (version 2) and **settlement** (version 1).
- **tenant-A** and **tenant-B** are both in region **us-east**.
- **tenant-A** has an override for **order-processing v2** (merge at runtime: template + override).

## Storage

| Concept | Table | Key / query |
|--------|--------|-------------|
| Tenant → region | `olo_configuration_region` | (tenant_id) → region |
| Region pipelines | `olo_pipeline_template` | (region, pipeline_id, version), `is_active = true` |
| Tenant overrides | `olo_tenant_pipeline_override` | (tenant_id, pipeline_id, pipeline_version) |

## Rules

- **Single active template**: For a given `(region, pipeline_id)`, there should be **exactly one** row with `is_active = true`. (This is a data rule; enforce in writes and/or with a partial unique index in a future migration.)
- **Version-bound overrides**: Tenant overrides are keyed by `pipeline_version` to avoid applying a v1 override to a v2 template.

## Seed

To create the example global context above in the DB, run after 002, 005, 006:

- **[008-seed_global_context_us_east.sql](../../olo-worker-db/src/main/resources/db/schema/008-seed_global_context_us_east.sql)** — us-east region, order-processing v2, settlement v1, tenant-A/tenant-B → us-east, tenant-A override for order-processing v2.

## Runtime flow

1. Resolve tenant → region from `olo_configuration_region`.
2. Load the **active** region pipeline template from `olo_pipeline_template` (`is_active = true`) for the requested `pipeline_id`.
3. Load tenant override from `olo_tenant_pipeline_override` for `(tenant_id, pipeline_id, pipeline_version = template.version)` if present.
4. Merge `template.tree_json` + `override.override_json` → effective pipeline JSON.
5. Expand execution tree → compile execution plan → execute.

## Optional optimization: Effective pipeline cache

To make runtime lookup \(almost\) O(1), compute and cache **effective pipelines** at bootstrap (or refresh time):

- Keyed by `(tenant_id, pipeline_id, pipeline_version)` (or by tenant + pipeline_id resolving to the active version).
- Value is the merged JSON (or compiled execution plan) so runtime does not repeatedly merge/expand.
