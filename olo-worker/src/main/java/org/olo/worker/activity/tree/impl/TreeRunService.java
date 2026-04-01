/*
 * Copyright (c) 2026 Olo Labs. All rights reserved.
 */

package org.olo.worker.activity.tree.impl;

import org.olo.config.OloConfig;
import org.olo.config.OloSessionCache;
import org.olo.input.model.WorkflowInput;
import org.olo.ledger.ExecutionEvent;
import org.olo.worker.dump.ExecutionDumpHelper;
import org.olo.ledger.ExecutionEventSink;
import org.olo.ledger.RunLedger;
import org.olo.plugin.PluginExecutorFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;

/** Coordinate full execution tree run (context resolver + engine invoker + ledger). */
public final class TreeRunService {

    private static final Logger log = LoggerFactory.getLogger(TreeRunService.class);

    private final Set<String> allowedTenantIds;
    private final OloSessionCache sessionCache;
    private final RunLedger runLedger;
    private final ExecutionEventSink executionEventSink;
    private final PluginExecutorFactory pluginExecutorFactory;
    private final org.olo.node.DynamicNodeBuilder dynamicNodeBuilder;
    private final org.olo.node.NodeFeatureEnricher nodeFeatureEnricher;

    public TreeRunService(Set<String> allowedTenantIds, OloSessionCache sessionCache, RunLedger runLedger,
                          PluginExecutorFactory pluginExecutorFactory,
                          org.olo.node.DynamicNodeBuilder dynamicNodeBuilder,
                          org.olo.node.NodeFeatureEnricher nodeFeatureEnricher) {
        this(allowedTenantIds, sessionCache, runLedger, null, pluginExecutorFactory, dynamicNodeBuilder, nodeFeatureEnricher);
    }

    public TreeRunService(Set<String> allowedTenantIds, OloSessionCache sessionCache, RunLedger runLedger,
                          ExecutionEventSink executionEventSink,
                          PluginExecutorFactory pluginExecutorFactory,
                          org.olo.node.DynamicNodeBuilder dynamicNodeBuilder,
                          org.olo.node.NodeFeatureEnricher nodeFeatureEnricher) {
        this.allowedTenantIds = allowedTenantIds != null ? allowedTenantIds : Set.of();
        this.sessionCache = sessionCache;
        this.runLedger = runLedger;
        this.executionEventSink = executionEventSink;
        this.pluginExecutorFactory = pluginExecutorFactory;
        this.dynamicNodeBuilder = dynamicNodeBuilder;
        this.nodeFeatureEnricher = nodeFeatureEnricher;
    }

    public String runExecutionTree(String queueName, String workflowInputJson) {
        int inputLen = workflowInputJson != null ? workflowInputJson.length() : 0;
        String inputSnippet = workflowInputJson != null && workflowInputJson.length() > 300 ? workflowInputJson.substring(0, 300) + "...[truncated]" : (workflowInputJson != null ? workflowInputJson : "");
        log.info("Activity entry | runExecutionTree | queue={} | workflowInputLength={} | workflowInputSnippet={}", queueName != null ? queueName : "", inputLen, inputSnippet);
        WorkflowInput workflowInput;
        try { workflowInput = WorkflowInput.fromJson(workflowInputJson); } catch (Exception e) {
            log.warn("Invalid workflow input JSON for runExecutionTree", e);
            return "";
        }
        String tenantId = OloConfig.normalizeTenantId(workflowInput.getContext() != null ? workflowInput.getContext().getTenantId() : null);
        log.info("RunExecutionTree activity started | invoked from workflow | queue={} | tenantId={}", queueName != null ? queueName : "", tenantId);
        if (!allowedTenantIds.isEmpty() && !allowedTenantIds.contains(tenantId)) throw new IllegalArgumentException("Unknown tenant: " + tenantId);
        sessionCache.incrActiveWorkflows(tenantId);
        try { return doRunExecutionTree(tenantId, queueName, workflowInput, workflowInputJson); }
        finally { sessionCache.decrActiveWorkflows(tenantId); }
    }

    private String doRunExecutionTree(String tenantId, String queueName, WorkflowInput workflowInput, String workflowInputJson) {
        TreeContextResolver.ResolvedContext ctx = TreeContextResolver.resolve(tenantId, queueName, workflowInput);
        if (ctx.status == TreeContextResolver.Status.NO_CONFIG || ctx.status == TreeContextResolver.Status.NO_PIPELINE) return "";
        org.olo.bootstrap.runtime.OloRuntimeContext runtimeContext = org.olo.bootstrap.runtime.OloRuntimeContext.create(workflowInput, ctx.pipeline);
        ExecutionDumpHelper.dumpIfEnabled(runtimeContext, ctx.runId);
        TreeRunLedger.LedgerRunContext ledgerCtx = TreeRunLedger.startRun(ctx, runLedger, workflowInputJson);
        if (executionEventSink != null && ctx.runId != null) {
            executionEventSink.emit(ctx.runId, new ExecutionEvent(
                    ExecutionEvent.EventType.WORKFLOW_STARTED, "Workflow started", null, System.currentTimeMillis(), null));
        }
        String runResult = null;
        String runStatus = "FAILED";
        Throwable runFailure = null;
        try {
            runResult = TreeEngineInvoker.run(ctx, runtimeContext, pluginExecutorFactory, dynamicNodeBuilder, nodeFeatureEnricher);
            runStatus = "SUCCESS";
            log.info("Execution tree exit | transactionId={} | status={} | resultLength={}", ctx.transactionId, runStatus, runResult != null ? runResult.length() : 0);
            return runResult != null ? runResult : "";
        } catch (IllegalArgumentException e) {
            log.warn("Execution engine validation failed: {}", e.getMessage());
            runFailure = e;
            return "";
        } catch (Throwable t) {
            runFailure = t;
            log.info("Execution tree exit | transactionId={} | status=FAILED | error={}", ctx.transactionId, t.getMessage());
            throw t;
        } finally {
            if (executionEventSink != null && ctx.runId != null) {
                String eventType = "SUCCESS".equals(runStatus) ? ExecutionEvent.EventType.WORKFLOW_COMPLETED : ExecutionEvent.EventType.WORKFLOW_FAILED;
                String label = "SUCCESS".equals(runStatus) ? "Done" : ("Error: " + (runFailure != null ? runFailure.getMessage() : runStatus));
                executionEventSink.emit(ctx.runId, new ExecutionEvent(eventType, label, null, System.currentTimeMillis(), null));
            }
            TreeRunLedger.endRun(ctx, ledgerCtx, runResult, runStatus, runFailure);
        }
    }
}
