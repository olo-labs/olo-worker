<!-- Copyright (c) 2026 Olo Labs. All rights reserved. -->

# Testing and verification

This repository uses **JUnit 5**. Unit tests run with the standard `test` task. **Integration**-style tests are tagged with **`@Tag("integration")`** and are **not** all included in a single named task by default; instead, the **`olo-worker`** module exposes focused Gradle tasks that run specific test classes.

## Quick commands

| Command | What it runs |
|---------|----------------|
| `./gradlew test` | All **unit** tests in subprojects (integration-tagged tests may be excluded per module) |
| `./gradlew :olo-worker:test` | All tests in `olo-worker`, **including** integration-tagged tests (needs Temporal/Redis/DB where applicable) |
| `./gradlew :olo-worker:bootstrapDump` | Only **`BootstrapGlobalContextDumpTest`** (tag `integration`) |
| `./gradlew :olo-worker:workflowInputDebug` | Only **`WorkflowInputDebugIntegrationTest`** |
| `./gradlew :olo-worker:consensusUseCase` | Only **`ConsensusUseCaseIntegrationTest`** |
| `./gradlew :olo-worker:refreshData` | Only **`FreshIntegrationCleanupTest`** |

On Windows, use `gradlew.bat` instead of `./gradlew`.

## Integration test prerequisites

These tests expect **real** infrastructure unless you use mocks in a different setup:

| Test / task | Typical requirements |
|-------------|-------------------------|
| **bootstrapDump** | Redis, PostgreSQL, env from `configuration/debug/env.example` (or equivalent). Builds/loads config snapshots. |
| **workflowInputDebug** | Temporal server, Redis, DB, same env patterns as other integration tests. |
| **consensusUseCase** | Temporal, Redis, DB; uses sample pipelines under `configuration/debug/`. |

Set **`OLO_TEMPORAL_TARGET`**, **`OLO_REDIS_HOST`**, DB variables, and region keys as documented in **`docs/architecture/bootstrap/how-to-debug.md`** and **`configuration/debug/README.md`**.

## Tags

- Use **`@Tag("integration")`** on tests that need external services so CI can exclude them with `excludeTags 'integration'` if desired.

## Dump output (bootstrap dump)

- Default output path is under **`olo-worker/build/`** (e.g. global-context debug JSON). Override with system property **`olo.bootstrap.dump.output`** when the test supports it.

See **`docs/architecture/bootstrap/how-to-debug.md`** for step-by-step bootstrap debugging.
