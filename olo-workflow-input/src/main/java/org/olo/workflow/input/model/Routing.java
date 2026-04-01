/*
 * Copyright (c) 2026 Olo Labs. All rights reserved.
 */

package org.olo.workflow.input.model;

import org.olo.workflow.input.model.enums.ExecutionKind;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

@Value
@Builder
@Jacksonized
public class Routing {
  String pipeline;
  /** Pipeline/execution version (e.g. "1.0", "2.0"). Upgraded independently from schemaVersion. */
  String pipelineVersion;
  /** Optional: explicit execution kind (pipeline, agent, workflow, job, tool, task) for platform routing. */
  ExecutionKind executionKind;
  String transactionType;
}
