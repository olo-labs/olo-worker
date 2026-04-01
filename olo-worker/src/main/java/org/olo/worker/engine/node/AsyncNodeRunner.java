/*
 * Copyright (c) 2026 Olo Labs. All rights reserved.
 */

package org.olo.worker.engine.node;

import org.olo.executiontree.config.ExecutionType;
import org.olo.executiontree.config.PipelineDefinition;
import org.olo.executiontree.tree.ExecutionTreeNode;
import org.olo.executiontree.tree.NodeType;
import org.olo.ledger.LedgerContext;
import org.olo.worker.engine.VariableEngine;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

/**
 * Single responsibility: run one node with optional async execution and LedgerContext.
 * Delegates the actual node execution to SingleNodeRunner.
 */
public final class AsyncNodeRunner {

    private final SingleNodeRunner singleNodeRunner;
    private final ExecutionType executionType;
    private final ExecutorService executor;
    private final String ledgerRunId;

    public AsyncNodeRunner(SingleNodeRunner singleNodeRunner, ExecutionType executionType,
                           ExecutorService executor, String ledgerRunId) {
        this.singleNodeRunner = singleNodeRunner;
        this.executionType = executionType != null ? executionType : ExecutionType.SYNC;
        this.executor = executor;
        this.ledgerRunId = ledgerRunId;
    }

    /**
     * Execute node: set LedgerContext, then run sync or async (activity nodes only), then clear context.
     */
    public void executeNode(ExecutionTreeNode node, PipelineDefinition pipeline, VariableEngine variableEngine,
                            String queueName, ChildNodeRunner runChild, ChildNodeRunner runChildSync) {
        if (node == null) return;
        if (ledgerRunId != null && !ledgerRunId.isBlank()) {
            LedgerContext.setRunId(ledgerRunId);
        }
        try {
            boolean runAsync = executionType == ExecutionType.ASYNC
                    && executor != null
                    && NodeActivityPredicate.isActivityNode(node)
                    && node.getType() != NodeType.JOIN;
            if (runAsync) {
                Future<?> future = executor.submit(() -> {
                    if (ledgerRunId != null && !ledgerRunId.isBlank()) {
                        LedgerContext.setRunId(ledgerRunId);
                    }
                    try {
                        singleNodeRunner.runOne(node, pipeline, variableEngine, queueName, runChild, runChildSync);
                    } finally {
                        if (ledgerRunId != null && !ledgerRunId.isBlank()) {
                            LedgerContext.clear();
                        }
                    }
                });
                try {
                    future.get();
                } catch (Exception e) {
                    Throwable cause = e.getCause() != null ? e.getCause() : e;
                    if (cause instanceof RuntimeException re) throw re;
                    throw new RuntimeException(cause);
                }
            } else {
                singleNodeRunner.runOne(node, pipeline, variableEngine, queueName, runChild, runChildSync);
            }
        } finally {
            if (ledgerRunId != null && !ledgerRunId.isBlank()) {
                LedgerContext.clear();
            }
        }
    }
}
