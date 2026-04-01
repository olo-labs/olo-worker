/*
 * Copyright (c) 2026 Olo Labs. All rights reserved.
 */

package org.olo.workflow.input.validation;

import java.util.Collections;
import java.util.List;

/**
 * Thrown when {@link OloPayloadValidator#validate} finds one or more validation errors.
 */
public class OloValidationException extends IllegalArgumentException {
  private final List<String> errors;

  public OloValidationException(List<String> errors) {
    super("Payload validation failed: " + String.join("; ", errors));
    this.errors = errors == null ? List.of() : Collections.unmodifiableList(errors);
  }

  public List<String> getErrors() {
    return errors;
  }
}
