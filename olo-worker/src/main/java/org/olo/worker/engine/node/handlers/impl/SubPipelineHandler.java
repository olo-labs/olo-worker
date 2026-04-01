/*
 * Copyright (c) 2026 Olo Labs. All rights reserved.
 */

package org.olo.worker.engine.node.handlers.impl;

import org.olo.executiontree.config.PipelineDefinition;
import org.olo.executiontree.tree.ExecutionTreeNode;
import org.olo.executiontree.tree.NodeType;
import org.olo.worker.engine.VariableEngine;
import org.olo.worker.engine.node.ChildNodeRunner;
import org.olo.worker.engine.node.ExpansionLimits;
import org.olo.worker.engine.node.ExpansionState;
import org.olo.worker.engine.node.NodeParams;
import org.olo.worker.engine.node.handlers.HandlerContext;
import org.olo.worker.engine.node.handlers.NodeHandler;
import org.olo.worker.engine.runtime.RuntimeExecutionTree;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;
import java.util.function.Consumer;

/**
 * Single responsibility: SUB_PIPELINE nodes.
 */
public final class SubPipelineHandler implements NodeHandler {

    private static final Logger log = LoggerFactory.getLogger(SubPipelineHandler.class);

    @Override
    public Set<NodeType> supportedTypes() {
        return Set.of(NodeType.SUB_PIPELINE);
    }

    @Override
    public Object dispatch(ExecutionTreeNode node,
                           PipelineDefinition pipeline,
                           VariableEngine variableEngine,
                           String queueName,
                           ChildNodeRunner runChild,
                           ChildNodeRunner runChildSync,
                           HandlerContext ctx) {
        if (ctx.getConfig() == null || ctx.getConfig().getPipelines() == null) {
            log.warn("SUB_PIPELINE node {} has no PipelineConfiguration; skipping", node.getId());
            return null;
        }
        String pipelineRef = NodeParams.paramString(node, "pipelineRef");
        if (pipelineRef == null || pipelineRef.isBlank()) {
            log.warn("SUB_PIPELINE node {} missing pipelineRef in params", node.getId());
            return null;
        }
        PipelineDefinition subPipeline = ctx.getConfig().getPipelines().get(pipelineRef);
        if (subPipeline == null) {
            log.warn("SUB_PIPELINE node {} pipelineRef '{}' not found in config", node.getId(), pipelineRef);
            return null;
        }
        ExecutionTreeNode subRoot = subPipeline.getExecutionTree();
        if (subRoot != null) {
            runChild.run(subRoot, subPipeline, variableEngine, queueName);
        }
        return null;
    }

    @Override
    public Object dispatchWithTree(ExecutionTreeNode node,
                                   PipelineDefinition pipeline,
                                   VariableEngine variableEngine,
                                   String queueName,
                                   RuntimeExecutionTree tree,
                                   Consumer<String> subtreeRunner,
                                   ExpansionState expansionState,
                                   ExpansionLimits expansionLimits,
                                   HandlerContext ctx) {
        return null;
    }
}
