/*
 * Copyright (c) 2026 Olo Labs. All rights reserved.
 */

package org.olo.worker.engine.node;

import org.olo.executiontree.config.PipelineDefinition;
import org.olo.executiontree.tree.ExecutionTreeNode;
import org.olo.executiontree.tree.NodeType;
import org.olo.features.FeatureRegistry;
import org.olo.features.NodeExecutionContext;
import org.olo.features.ResolvedPrePost;
import org.olo.ledger.LedgerContext;
import org.olo.worker.engine.VariableEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

/**
 * Single responsibility: run PLANNER node only (model + parse + inject variables) and return step nodes.
 * Manages LedgerContext and pre/post features.
 */
public final class PlannerOnlyRunner {

    private static final Logger log = LoggerFactory.getLogger(PlannerOnlyRunner.class);

    private final NodeFeatureRunner featureRunner;
    private final NodeExecutionDispatcher dispatcher;
    private final String tenantId;
    private final Map<String, Object> tenantConfigMap;
    private final String ledgerRunId;

    public PlannerOnlyRunner(NodeFeatureRunner featureRunner, NodeExecutionDispatcher dispatcher,
                             String tenantId, Map<String, Object> tenantConfigMap, String ledgerRunId) {
        this.featureRunner = featureRunner;
        this.dispatcher = dispatcher;
        this.tenantId = tenantId != null ? tenantId : "";
        this.tenantConfigMap = tenantConfigMap != null ? Map.copyOf(tenantConfigMap) : Map.of();
        this.ledgerRunId = ledgerRunId;
    }

    /**
     * Run planner only; returns list of step nodes. Caller must not pass non-PLANNER nodes.
     */
    public List<ExecutionTreeNode> executePlannerOnly(ExecutionTreeNode node, PipelineDefinition pipeline,
                                                      VariableEngine variableEngine, String queueName) {
        if (node == null || node.getType() != NodeType.PLANNER) return List.of();
        if (ledgerRunId != null && !ledgerRunId.isBlank()) {
            LedgerContext.setRunId(ledgerRunId);
        }
        try {
            FeatureRegistry registry = FeatureRegistry.getInstance();
            ResolvedPrePost resolved = FeatureResolver.resolve(node, queueName, pipeline.getScope(), registry);
            NodeExecutionContext context = new NodeExecutionContext(
                    node.getId(), node.getType().getTypeName(), node.getNodeType(), null, tenantId, tenantConfigMap,
                    queueName, null, null);
            featureRunner.runPre(resolved, context, registry);
            List<ExecutionTreeNode> steps = dispatcher.runPlannerReturnSteps(node, pipeline, variableEngine, queueName);
            featureRunner.runPostSuccess(resolved, context.withExecutionSucceeded(true), null, registry);
            return steps;
        } catch (Throwable t) {
            log.warn("Planner-only execution failed: nodeId={} error={}", node.getId(), t.getMessage(), t);
            FeatureRegistry registry = FeatureRegistry.getInstance();
            ResolvedPrePost resolved = FeatureResolver.resolve(node, queueName, pipeline.getScope(), registry);
            NodeExecutionContext context = new NodeExecutionContext(
                    node.getId(), node.getType().getTypeName(), node.getNodeType(), null, tenantId, tenantConfigMap,
                    queueName, null, null);
            featureRunner.runPostError(resolved, context.withExecutionSucceeded(false), null, registry);
            throw t;
        } finally {
            if (ledgerRunId != null && !ledgerRunId.isBlank()) {
                LedgerContext.clear();
            }
        }
    }
}
