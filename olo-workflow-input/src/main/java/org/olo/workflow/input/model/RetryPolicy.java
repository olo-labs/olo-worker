/*
 * Copyright (c) 2026 Olo Labs. All rights reserved.
 */

package org.olo.workflow.input.model;

import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

/**
 * Retry policy for execution. When present, workers apply this instead of hiding retries inside workflow logic.
 */
@Value
@Builder
@Jacksonized
public class RetryPolicy {
  /** Maximum number of attempts (including the first). Must be ≥ 1. */
  Integer maxAttempts;
  /** Delay in seconds before retry (constant or initial backoff). */
  Integer backoffSeconds;
}
