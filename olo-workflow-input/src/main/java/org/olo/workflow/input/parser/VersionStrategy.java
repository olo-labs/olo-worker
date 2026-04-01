/*
 * Copyright (c) 2026 Olo Labs. All rights reserved.
 */

package org.olo.workflow.input.parser;

import org.olo.workflow.input.model.OloWorkerRequest;

/**
 * Resolves the request type to use for a given payload schema version.
 * schemaVersion = payload schema (e.g. 1.0, 1.1); routing.pipelineVersion = execution/pipeline version.
 */
@FunctionalInterface
public interface VersionStrategy {
  /**
   * Returns the class to deserialize into for the given schema version string.
   *
   * @param schemaVersion the "schemaVersion" field from the payload (may be null or blank)
   * @return the request type for that version
   * @throws IllegalArgumentException if the version is not supported
   */
  Class<? extends OloWorkerRequest> requestTypeForVersion(String schemaVersion);

  /**
   * Default strategy: "1.0" and "1.1" map to current {@link OloWorkerRequest}; others throw.
   */
  static VersionStrategy defaultStrategy() {
    return schemaVersion -> {
      if (schemaVersion == null || schemaVersion.isBlank()) {
        return OloWorkerRequest.class;
      }
      switch (schemaVersion) {
        case "1.0":
        case "1.1":
          return OloWorkerRequest.class;
        default:
          throw new IllegalArgumentException("Unsupported payload schemaVersion: " + schemaVersion);
      }
    };
  }
}
