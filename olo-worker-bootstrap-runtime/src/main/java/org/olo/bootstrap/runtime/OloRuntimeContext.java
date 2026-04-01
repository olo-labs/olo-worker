/*
 * Copyright (c) 2026 Olo Labs. All rights reserved.
 */

package org.olo.bootstrap.runtime;

import org.olo.executiontree.config.PipelineDefinition;
import org.olo.input.model.WorkflowInput;

/**
 * Holds everything needed for a single worker run: a copy of the workflow input and the
 * pipeline definition (either a reference to the global context tree or a deep copy when
 * the pipeline is dynamic).
 *
 * <p>Created as the very first thing when a task is picked from the worker queue. For
 * {@code isDynamicPipeline: true} the execution tree is deep-copied so the run can mutate
 * it (e.g. planner expansion); otherwise a shallow reference to the global context tree is kept.
 */
public interface OloRuntimeContext {

  /** Snapshot of the workflow input for this run. */
  WorkflowInput getWorkflowInput();

  /**
   * Pipeline definition for this run. For dynamic pipelines this is a copy with a deep-copied
   * execution tree; otherwise it is the same reference as in the global context.
   */
  PipelineDefinition getPipelineDefinition();

  /**
   * Builds the runtime context: copies input and, when {@code isDynamicPipeline} is true,
   * uses a deep copy of the execution tree; otherwise keeps a reference to the given pipeline.
   */
  static OloRuntimeContext create(WorkflowInput workflowInput, PipelineDefinition pipelineFromGlobal) {
    return org.olo.bootstrap.runtime.impl.OloRuntimeContextImpl.create(workflowInput, pipelineFromGlobal);
  }
}
