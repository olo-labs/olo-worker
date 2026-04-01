/*
 * Copyright (c) 2026 Olo Labs. All rights reserved.
 */

package org.olo.workflow.input.model;

import org.olo.workflow.input.model.enums.ExecutionPriority;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

@Value
@Builder
@Jacksonized
public class Execution {
  /** SYNC or ASYNC; see {@link org.olo.workflow.input.model.enums.ExecutionMode}. */
  String mode;
  ExecutionPriority priority;
  Integer timeoutSeconds;
  /** Retry policy. When set, workers apply it instead of hiding retries inside workflow logic. */
  RetryPolicy retryPolicy;
}
