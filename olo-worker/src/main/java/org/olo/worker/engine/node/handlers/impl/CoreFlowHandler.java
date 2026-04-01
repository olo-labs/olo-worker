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
import org.olo.worker.engine.node.handlers.HandlerContext;
import org.olo.worker.engine.node.handlers.NodeHandler;
import org.olo.worker.engine.runtime.RuntimeExecutionTree;

import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/** Handles SEQUENCE/GROUP/CASE control-flow containers. */
public final class CoreFlowHandler implements NodeHandler {

    @Override
    public Set<NodeType> supportedTypes() {
        return Set.of(NodeType.SEQUENCE, NodeType.GROUP, NodeType.CASE);
    }

    @Override
    public Object dispatch(ExecutionTreeNode node, PipelineDefinition pipeline,
                           VariableEngine variableEngine, String queueName,
                           ChildNodeRunner runChild, ChildNodeRunner runChildSync, HandlerContext ctx) {
        NodeType type = node.getType();
        if (type == NodeType.SEQUENCE || type == NodeType.GROUP) {
            for (ExecutionTreeNode child : node.getChildren()) {
                runChild.run(child, pipeline, variableEngine, queueName);
            }
            return null;
        }
        if (type == NodeType.CASE) {
            for (ExecutionTreeNode child : node.getChildren()) {
                runChild.run(child, pipeline, variableEngine, queueName);
            }
        }
        return null;
    }

    @Override
    public Object dispatchWithTree(ExecutionTreeNode node, PipelineDefinition pipeline,
                                   VariableEngine variableEngine, String queueName,
                                   RuntimeExecutionTree tree, Consumer<String> subtreeRunner,
                                   ExpansionState expansionState, ExpansionLimits expansionLimits, HandlerContext ctx) {
        return null;
    }
}
