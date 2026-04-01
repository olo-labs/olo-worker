/*
 * Copyright (c) 2026 Olo Labs. All rights reserved.
 */

package org.olo.workflow.input.model;

import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

import java.util.Map;

/**
 * Single input (name, type, storage, value). Storage can use resource-based or legacy keys.
 * Preferred: {@code type}, {@code resource} (e.g. olo:resource:connection:redis:cache), {@code key} or {@code path}.
 */
@Value
@Builder
@Jacksonized
public class Input {
  String name;
  /** Type as string (e.g. STRING, FILE); see {@link org.olo.workflow.input.model.enums.InputType} for known values. */
  String type;
  /** Storage: use "type" + "resource" + "key" (or "path") to reference connections. */
  Map<String, Object> storage;
  Object value;
}
