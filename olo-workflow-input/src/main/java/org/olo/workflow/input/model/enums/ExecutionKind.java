/*
 * Copyright (c) 2026 Olo Labs. All rights reserved.
 */

package org.olo.workflow.input.model.enums;

/**
 * Kind of execution triggered by an {@link org.olo.workflow.input.model.OloWorkerRequest}.
 * The same envelope can drive pipeline, agent, workflow, job, tool, or task execution—
 * simplifying the platform to a single trigger contract.
 */
public enum ExecutionKind {
  PIPELINE,
  AGENT,
  WORKFLOW,
  JOB,
  TOOL,
  TASK
}
