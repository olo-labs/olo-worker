/*
 * Copyright (c) 2026 Olo Labs. All rights reserved.
 */

package org.olo.executiontree;

import java.util.Map;

/**
 * Runtime context for executing one run. Provides tenantId, runId, variable engine,
 * and snapshot identity. Plugin registry, feature registry, and tenant config are
 * supplied by the execution module; this interface defines the minimal contract
 * that the execution tree layer depends on.
 */
public interface ExecutionContext {
  String getTenantId();
  String getRunId();
  VariableEngine getVariableEngine();
  String getSnapshotVersionId();
  /** Tenant configuration (tenantConfigMap). */
  Map<String, Object> getTenantConfig();
}
