/*
 * Copyright (c) 2026 Olo Labs. All rights reserved.
 */

package org.olo.workflow.input.validation;

import org.olo.workflow.input.model.Input;
import org.olo.workflow.input.model.OloWorkerRequest;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Validates {@link OloWorkerRequest} before submission to Temporal.
 * Call {@link #validate(OloWorkerRequest)} after parsing; throws {@link OloValidationException}
 * with all collected errors if invalid.
 */
public final class OloPayloadValidator {

  private static final Set<String> VALID_EXECUTION_MODES = Set.of("SYNC", "ASYNC");

  private OloPayloadValidator() {}

  /**
   * Validates the request. Throws {@link OloValidationException} if any check fails.
   *
   * @param request the parsed request (must not be null)
   * @throws OloValidationException if validation fails (contains all error messages)
   */
  public static void validate(OloWorkerRequest request) {
    if (request == null) {
      throw new OloValidationException(List.of("request is null"));
    }
    List<String> errors = new ArrayList<>();

    if (isBlank(request.getTenantId())) {
      errors.add("tenantId is required");
    }
    if (isBlank(request.getRunId())) {
      errors.add("runId is required");
    }

    if (request.getRouting() == null) {
      errors.add("routing is required");
    } else if (isBlank(request.getRouting().getPipeline())) {
      errors.add("routing.pipeline is required");
    }

    if (request.getExecution() != null) {
      String mode = request.getExecution().getMode();
      if (mode != null && !VALID_EXECUTION_MODES.contains(mode.toUpperCase())) {
        errors.add("execution.mode must be one of: " + VALID_EXECUTION_MODES);
      }
    }

    if (request.getInputs() != null && !request.getInputs().isEmpty()) {
      Set<String> seen = new HashSet<>();
      for (Input in : request.getInputs()) {
        if (in == null) {
          continue;
        }
        String name = in.getName();
        if (name == null) {
          errors.add("inputs[].name is required for every input");
          continue;
        }
        if (!seen.add(name)) {
          errors.add("inputs: duplicate name '" + name + "'");
        }
      }
    }

    if (!errors.isEmpty()) {
      throw new OloValidationException(errors);
    }
  }

  private static boolean isBlank(String s) {
    return s == null || s.isBlank();
  }
}
