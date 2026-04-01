<!-- Copyright (c) 2026 Olo Labs. All rights reserved. -->

# Execution context

This folder describes execution-time context resolution for the worker.

- **[Global context](global-context.md)** — The worker's global view: configuration, region snapshots, compiled execution trees, and tenant-to-region mapping. Backed by `GlobalContext` (bootstrap-loader) and used by `LocalContext` to resolve pipelines per queue.
