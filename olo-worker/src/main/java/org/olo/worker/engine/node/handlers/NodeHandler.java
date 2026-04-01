/*
 * Copyright (c) 2026 Olo Labs. All rights reserved.
 */

package org.olo.worker.engine.node.handlers;

import org.olo.executiontree.config.PipelineDefinition;
import org.olo.executiontree.tree.ExecutionTreeNode;
import org.olo.executiontree.tree.NodeType;
import org.olo.worker.engine.VariableEngine;
import org.olo.worker.engine.node.ChildNodeRunner;

import java.util.Set;
import java.util.function.Consumer;

/**
 * Single responsibility: handle execution for one or more node types (dispatch or tree-driven).
 */
public interface NodeHandler {

    /** Node types this handler supports. */
    Set<NodeType> supportedTypes();

    /**
     * Handle in recursive dispatch mode (runChild/runChildSync). Return null for "no result".
     */
    default Object dispatch(ExecutionTreeNode node, PipelineDefinition pipeline,
                            VariableEngine variableEngine, String queueName,
                            ChildNodeRunner runChild, ChildNodeRunner runChildSync,
                            HandlerContext ctx) {
        return null;
    }

    /**
     * Handle in tree-driven mode (tree mutation, subtreeRunner). Return null for "no result".
     */
    default Object dispatchWithTree(ExecutionTreeNode node, PipelineDefinition pipeline,
                                    VariableEngine variableEngine, String queueName,
                                    org.olo.worker.engine.runtime.RuntimeExecutionTree tree,
                                    Consumer<String> subtreeRunner,
                                    org.olo.worker.engine.node.ExpansionState expansionState,
                                    org.olo.worker.engine.node.ExpansionLimits expansionLimits,
                                    HandlerContext ctx) {
        return null;
    }
}
