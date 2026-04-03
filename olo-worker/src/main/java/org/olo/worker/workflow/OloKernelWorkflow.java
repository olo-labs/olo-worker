/*
 * Copyright (c) 2026 Olo Labs. All rights reserved.
 */

package org.olo.worker.workflow;

import org.olo.input.model.WorkflowInput;
import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;
import io.temporal.workflow.SignalMethod;

/**
 * OLO Kernel workflow. Receives workflow input and orchestrates processing.
 */
@WorkflowInterface
public interface OloKernelWorkflow {

    /**
     * Runs the kernel workflow with the given workflow input.
     * Accepts {@link WorkflowInput} so Temporal can deserialize the start payload
     * when the client sends a JSON object (not a JSON string).
     *
     * @param workflowInput workflow input (version, inputs, context, routing, metadata)
     * @return result summary (e.g. transaction id or status)
     */
    @WorkflowMethod
    String run(WorkflowInput workflowInput);

    /**
     * Human input signal used by BE endpoint POST /api/runs/{runId}/human-input.
     * approved=true  -> execute expensive dynamic planner flow
     * approved=false -> skip planner and use direct LLM response path
     */
    @SignalMethod
    void humanInput(boolean approved, String message);
}
