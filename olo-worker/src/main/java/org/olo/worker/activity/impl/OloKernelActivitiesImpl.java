/*
 * Copyright (c) 2026 Olo Labs. All rights reserved.
 */

package org.olo.worker.activity.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.olo.config.OloConfig;
import org.olo.config.OloSessionCache;
import org.olo.bootstrap.loader.context.LocalContext;
import org.olo.input.model.WorkflowInput;
import org.olo.node.DynamicNodeBuilder;
import org.olo.node.NodeFeatureEnricher;
import org.olo.ledger.ExecutionEventSink;
import org.olo.worker.activity.OloKernelActivities;
import org.olo.worker.activity.node.impl.NodeExecutionService;
import org.olo.worker.activity.plan.impl.ExecutionPlanService;
import org.olo.worker.activity.plugin.impl.PluginExecutionService;
import org.olo.worker.activity.tree.impl.TreeRunService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** OLO Kernel activity implementation. Delegates to plan, plugin, node, and tree services. */
public class OloKernelActivitiesImpl implements OloKernelActivities {

    private static final Logger log = LoggerFactory.getLogger(OloKernelActivitiesImpl.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder().build();

    private final OloSessionCache sessionCache;
    private final Set<String> allowedTenantIds;
    private final ExecutionPlanService planService;
    private final PluginExecutionService pluginService;
    private final NodeExecutionService nodeExecutionService;
    private final TreeRunService treeRunService;

    public OloKernelActivitiesImpl(OloSessionCache sessionCache, List<String> allowedTenantIds, org.olo.ledger.RunLedger runLedger,
                                   org.olo.plugin.PluginExecutorFactory pluginExecutorFactory,
                                   DynamicNodeBuilder dynamicNodeBuilder,
                                   NodeFeatureEnricher nodeFeatureEnricher) {
        this(sessionCache, allowedTenantIds, runLedger, null, pluginExecutorFactory, dynamicNodeBuilder, nodeFeatureEnricher);
    }

    public OloKernelActivitiesImpl(OloSessionCache sessionCache, List<String> allowedTenantIds, org.olo.ledger.RunLedger runLedger,
                                   ExecutionEventSink executionEventSink,
                                   org.olo.plugin.PluginExecutorFactory pluginExecutorFactory,
                                   DynamicNodeBuilder dynamicNodeBuilder,
                                   NodeFeatureEnricher nodeFeatureEnricher) {
        this.sessionCache = sessionCache;
        this.allowedTenantIds = allowedTenantIds != null ? Set.copyOf(allowedTenantIds) : Set.of();
        this.planService = new ExecutionPlanService(this.allowedTenantIds);
        this.pluginService = new PluginExecutionService(pluginExecutorFactory);
        this.nodeExecutionService = new NodeExecutionService(this.allowedTenantIds, runLedger, executionEventSink, pluginExecutorFactory, dynamicNodeBuilder, nodeFeatureEnricher);
        this.treeRunService = new TreeRunService(this.allowedTenantIds, sessionCache, runLedger, executionEventSink, pluginExecutorFactory, dynamicNodeBuilder, nodeFeatureEnricher);
    }

    private static final String DEBUG_DUMP_DIR_ENV = "OLO_DEBUG_DUMP_INPUT_DIR";
    private static final String DEBUG_DUMP_DIR_PROP = "olo.debug.dump.input.dir";

    @Override
    public String processInput(String workflowInputJson) {
        WorkflowInput input = WorkflowInput.fromJson(workflowInputJson);
        sessionCache.cacheUpdate(input);
        String transactionId = input.getRouting() != null ? input.getRouting().getTransactionId() : null;
        String pipeline = input.getRouting() != null ? input.getRouting().getPipeline() : null;
        String transactionType = input.getRouting() != null && input.getRouting().getTransactionType() != null ? input.getRouting().getTransactionType().name() : null;
        String configVersion = input.getRouting() != null ? input.getRouting().getConfigVersion() : null;
        String tenantId = input.getContext() != null ? input.getContext().getTenantId() : null;
        String sessionId = input.getContext() != null ? input.getContext().getSessionId() : null;
        String version = input.getVersion();
        int inputsCount = input.getInputs() != null ? input.getInputs().size() : 0;
        String ragTag = input.getMetadata() != null ? input.getMetadata().getRagTag() : null;
        Long metadataTimestamp = input.getMetadata() != null ? input.getMetadata().getTimestamp() : null;
        log.info("OloKernel processed workflow input | transactionId={} | pipeline={} | transactionType={} | tenantId={} | sessionId={} | version={} | configVersion={} | inputsCount={} | ragTag={} | metadataTimestamp={}",
                transactionId, pipeline, transactionType, tenantId, sessionId, version, configVersion, inputsCount, ragTag, metadataTimestamp);

        String dumpDir = System.getenv(DEBUG_DUMP_DIR_ENV);
        if (dumpDir == null || dumpDir.isBlank()) dumpDir = System.getProperty(DEBUG_DUMP_DIR_PROP, "");
        if (dumpDir != null && !dumpDir.isBlank()) {
            dumpWorkflowInputAndLocalContext(input, pipeline, tenantId, configVersion, dumpDir, transactionId);
        }

        return transactionId != null ? transactionId : "unknown";
    }

    /**
     * When {@value #DEBUG_DUMP_DIR_ENV} or {@value #DEBUG_DUMP_DIR_PROP} is set, writes workflow input
     * and resolved LocalContext summary to a JSON file under the given directory.
     */
    private void dumpWorkflowInputAndLocalContext(WorkflowInput input, String effectiveQueue,
                                                  String tenantId, String requestedVersion, String dumpDir, String transactionId) {
        try {
            Path dir = Paths.get(dumpDir);
            Files.createDirectories(dir);
            String safeTxn = (transactionId != null && !transactionId.isBlank()) ? transactionId : "no-txn";
            String fileName = "workflow-input-" + safeTxn + "-" + Instant.now().toEpochMilli() + ".json";
            Path file = dir.resolve(fileName);

            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("workflowInput", MAPPER.readValue(input.toJson(), Map.class));
            Map<String, Object> localContextSummary = new LinkedHashMap<>();
            String normalizedTenant = (tenantId != null && !tenantId.isBlank()) ? tenantId : OloConfig.normalizeTenantId(null);
            if (effectiveQueue != null && !effectiveQueue.isBlank()) {
                LocalContext localContext = LocalContext.forQueue(normalizedTenant, effectiveQueue, requestedVersion);
                if (localContext == null && !normalizedTenant.equals(OloConfig.normalizeTenantId(null))) {
                    localContext = LocalContext.forQueue(OloConfig.normalizeTenantId(null), effectiveQueue, requestedVersion);
                }
                if (localContext != null) {
                    localContextSummary.put("effectiveQueue", effectiveQueue);
                    localContextSummary.put("tenantId", normalizedTenant);
                    var config = localContext.getPipelineConfiguration();
                    if (config != null && config.getPipelines() != null) {
                        localContextSummary.put("pipelineIds", new ArrayList<>(config.getPipelines().keySet()));
                    }
                } else {
                    localContextSummary.put("resolved", false);
                    localContextSummary.put("effectiveQueue", effectiveQueue);
                    localContextSummary.put("tenantId", normalizedTenant);
                }
            }
            payload.put("localContext", localContextSummary);

            Files.writeString(file, MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(payload));
            log.info("Debug dump written: {}", file.toAbsolutePath());
        } catch (Exception e) {
            log.warn("Failed to write debug dump to {}: {}", dumpDir, e.getMessage());
        }
    }

    @Override
    public String executePlugin(String pluginId, String inputsJson) {
        return pluginService.executePlugin(OloConfig.normalizeTenantId(null), pluginId, inputsJson, null, null);
    }

    @Override
    public String getChatResponse(String pluginId, String prompt) {
        return pluginService.getChatResponse(pluginId, prompt);
    }

    @Override
    public String getExecutionPlan(String queueName, String workflowInputJson) {
        return planService.getExecutionPlan(queueName, workflowInputJson);
    }

    @Override
    public String applyResultMapping(String planJson, String variableMapJson) {
        return planService.applyResultMapping(planJson, variableMapJson);
    }

    @Override
    public String executeNode(String activityType, String planJson, String nodeId, String variableMapJson,
                              String queueName, String workflowInputJson, String dynamicStepsJson) {
        Map<String, Object> payload = new java.util.LinkedHashMap<>();
        payload.put("planJson", planJson);
        payload.put("nodeId", nodeId);
        payload.put("variableMapJson", variableMapJson);
        payload.put("queueName", queueName != null ? queueName : "");
        payload.put("workflowInputJson", workflowInputJson);
        if (dynamicStepsJson != null && !dynamicStepsJson.isBlank()) payload.put("dynamicStepsJson", dynamicStepsJson);
        try {
            return nodeExecutionService.executeNode(MAPPER.writeValueAsString(payload));
        } catch (Exception e) {
            throw new RuntimeException("Failed to build executeNode payload", e);
        }
    }

    @Override
    public String runExecutionTree(String queueName, String workflowInputJson) {
        return treeRunService.runExecutionTree(queueName, workflowInputJson);
    }

    @Override
    public void reportRunEvent(String runId, String callbackBaseUrl, long sequenceNumber, String correlationId,
                               String nodeId, String parentNodeId, String nodeType, String status,
                               Map<String, Object> input, Map<String, Object> output, Map<String, Object> metadata) {
        if (runId == null || runId.isBlank() || callbackBaseUrl == null || callbackBaseUrl.isBlank()) {
            return;
        }
        String url = callbackBaseUrl.replaceAll("/$", "") + "/api/runs/" + runId + "/events";
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("sequenceNumber", sequenceNumber);
        if (correlationId != null && !correlationId.isBlank()) body.put("correlationId", correlationId);
        body.put("nodeId", nodeId != null ? nodeId : "human-routing-gate");
        body.put("parentNodeId", parentNodeId);
        body.put("nodeType", nodeType != null ? nodeType : "HUMAN");
        body.put("status", status != null ? status : "WAITING");
        body.put("input", input != null ? input : Map.of());
        body.put("output", output != null ? output : Map.of());
        body.put("metadata", metadata != null ? metadata : Map.of());
        try {
            String json = MAPPER.writeValueAsString(body);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() >= 400) {
                log.warn("reportRunEvent failed runId={} status={} body={}", runId, response.statusCode(), response.body());
            }
        } catch (Exception e) {
            log.warn("reportRunEvent error runId={} message={}", runId, e.getMessage());
        }
    }
}
