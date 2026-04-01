/*
 * Copyright (c) 2026 Olo Labs. All rights reserved.
 */

package org.olo.bootstrap.runtime.impl;

import org.olo.bootstrap.runtime.OloRuntimeContext;
import org.olo.executiontree.ExecutionTreeNode;
import org.olo.executiontree.config.PipelineDefinition;
import org.olo.input.model.WorkflowInput;

import java.util.Objects;

/**
 * Implementation of {@link OloRuntimeContext}: holds a copy of workflow input and either
 * a reference to the global pipeline or a new pipeline with a deep-copied execution tree
 * when {@code isDynamicPipeline} is true.
 */
public final class OloRuntimeContextImpl implements OloRuntimeContext {

    private final WorkflowInput workflowInput;
    private final PipelineDefinition pipelineDefinition;

    private OloRuntimeContextImpl(WorkflowInput workflowInput, PipelineDefinition pipelineDefinition) {
        this.workflowInput = Objects.requireNonNull(workflowInput, "workflowInput");
        this.pipelineDefinition = Objects.requireNonNull(pipelineDefinition, "pipelineDefinition");
    }

    @Override
    public WorkflowInput getWorkflowInput() {
        return workflowInput;
    }

    @Override
    public PipelineDefinition getPipelineDefinition() {
        return pipelineDefinition;
    }

    /**
     * Creates the runtime context: copies input; for dynamic pipelines uses a deep copy of
     * the execution tree, otherwise keeps a shallow reference to the global pipeline.
     */
    public static OloRuntimeContext create(WorkflowInput workflowInput, PipelineDefinition pipelineFromGlobal) {
        WorkflowInput inputCopy = copyInput(workflowInput);
        PipelineDefinition pipeline = resolvePipeline(pipelineFromGlobal);
        return new OloRuntimeContextImpl(inputCopy, pipeline);
    }

    private static WorkflowInput copyInput(WorkflowInput input) {
        if (input == null) return null;
        try {
            return WorkflowInput.fromJson(input.toJson());
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to copy workflow input", e);
        }
    }

    private static PipelineDefinition resolvePipeline(PipelineDefinition fromGlobal) {
        if (fromGlobal == null) return null;
        if (!(fromGlobal instanceof org.olo.executiontree.PipelineDefinition def)) {
            return fromGlobal;
        }
        if (!def.isDynamicPipeline()) {
            return fromGlobal;
        }
        ExecutionTreeNode root = def.getExecutionTreeRoot();
        if (root == null) return fromGlobal;
        ExecutionTreeNode deepCopyRoot = ExecutionTreeNode.deepCopy(root);
        return new org.olo.executiontree.PipelineDefinition(
                def.getName(),
                def.getInputContractMap(),
                def.getVariableRegistryRaw(),
                def.getScope(),
                deepCopyRoot,
                def.getOutputContract(),
                def.getResultMappingMap(),
                null,
                def.getExecutionType() != null ? def.getExecutionType().name() : "SYNC",
                def.isDebugPipeline(),
                def.isDynamicPipeline());
    }
}
