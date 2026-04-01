/*
 * Copyright (c) 2026 Olo Labs. All rights reserved.
 */

package org.olo.workflow.input.model.enums;

/**
 * Execution priority for scheduling (e.g. REALTIME, BATCH).
 */
public enum ExecutionPriority {
  REALTIME,
  HIGH,
  NORMAL,
  LOW,
  BATCH
}
