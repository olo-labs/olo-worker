/*
 * Copyright (c) 2026 Olo Labs. All rights reserved.
 */

package org.olo.bootstrap.loader.phases;

/**
 * Bootstrap phases in execution order. All discovery and wiring happens during these phases;
 * runtime does not perform discovery or scanning.
 */
public enum BootstrapPhase {
  CORE_SERVICES,
  ENVIRONMENT_LOAD,
  INFRASTRUCTURE_READY,
  PLUGIN_DISCOVERY,
  FEATURE_DISCOVERY,
  RESOURCE_PROVIDERS,
  PIPELINE_LOADING,
  VALIDATION,
  CONTEXT_BUILD,
  WORKER_START
}
