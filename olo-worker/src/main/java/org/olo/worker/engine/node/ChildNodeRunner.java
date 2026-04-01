/*
 * Copyright (c) 2026 Olo Labs. All rights reserved.
 */

package org.olo.worker.engine.node;

import org.olo.executiontree.config.PipelineDefinition;
import org.olo.executiontree.tree.ExecutionTreeNode;
import org.olo.worker.engine.VariableEngine;

/**
 * Callback to run a child node (used by NodeExecutionDispatcher for SEQUENCE, IF, FORK, etc.).
 */
@FunctionalInterface
public interface ChildNodeRunner {

    void run(ExecutionTreeNode child, PipelineDefinition pipeline,
             VariableEngine variableEngine, String queueName);
}
