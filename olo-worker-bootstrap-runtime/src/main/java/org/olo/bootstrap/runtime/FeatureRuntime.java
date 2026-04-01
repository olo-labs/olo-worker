/*
 * Copyright (c) 2026 Olo Labs. All rights reserved.
 */

package org.olo.bootstrap.runtime;

/**
 * Runtime for pipeline/queue-scoped features (before/after hooks, enrichment).
 * Lives for the lifetime of the worker; registered by the bootstrap loader.
 */
public interface FeatureRuntime {
  // Contract: attach features to node context; run before/after; implementation in feature module.
}
