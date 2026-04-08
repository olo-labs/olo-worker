/*
 * Copyright (c) 2026 Olo Labs. All rights reserved.
 */

package org.olo.worker.workflow.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.olo.worker.activity.OloKernelActivities;
import io.temporal.workflow.ActivityStub;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Single responsibility: run a linear or parallel execution plan (steps/nodes) and return updated variable map JSON.
 */
public final class WorkflowPlanExecutor {
    public record PlanRunResult(String variableMapJson, long nextSequenceNumber) {}


    private static final ObjectMapper MAPPER = new ObjectMapper();

    private WorkflowPlanExecutor() {
    }

    /**
     * Runs the plan (parallel steps or linear nodes) and returns the final variable map JSON.
     * Throws on parse/execution failure so the workflow can fall back to RunExecutionTree.
     */
    public static PlanRunResult runPlan(String planJson, ActivityStub untypedActivityStub,
                                        OloKernelActivities activities,
                                        String queueForActivities, String workflowInputJson,
                                        String runId, String callbackBaseUrl, String correlationId,
                                        long sequenceStart) throws Exception {
        @SuppressWarnings("unchecked")
        Map<String, Object> plan = MAPPER.readValue(planJson, Map.class);
        long sequence = sequenceStart;
        String variableMapJson = (String) plan.get("initialVariableMapJson");
        String planQueueName = (String) plan.get("queueName");
        if (planQueueName == null || planQueueName.isBlank()) planQueueName = queueForActivities;
        @SuppressWarnings("unchecked")
        List<List<Map<String, Object>>> steps = (List<List<Map<String, Object>>>) plan.get("steps");
        @SuppressWarnings("unchecked")
        Map<String, Object> tryCatchMeta = (Map<String, Object>) plan.get("tryCatch");
        Integer catchStepIndex = tryCatchMeta != null && tryCatchMeta.get("catchStepIndex") instanceof Number
                ? ((Number) tryCatchMeta.get("catchStepIndex")).intValue() : null;
        String errorVariable = tryCatchMeta != null && tryCatchMeta.get("errorVariable") != null
                ? tryCatchMeta.get("errorVariable").toString() : null;

        if (steps != null && !steps.isEmpty() && variableMapJson != null) {
            int tryCatchCatchStepIndex = catchStepIndex != null && catchStepIndex >= 0 ? catchStepIndex : -1;
            try {
                for (int stepIndex = 0; stepIndex < steps.size(); stepIndex++) {
                    if (tryCatchCatchStepIndex >= 0 && stepIndex == tryCatchCatchStepIndex) continue;
                    List<Map<String, Object>> step = steps.get(stepIndex);
                    variableMapJson = WorkflowVariableMapHelper.runStep(untypedActivityStub, planJson, queueForActivities, workflowInputJson,
                            step, variableMapJson);
                    if (variableMapJson == null) variableMapJson = "{}";
                }
            } catch (Exception e) {
                if (tryCatchCatchStepIndex >= 0 && tryCatchCatchStepIndex < steps.size() && errorVariable != null) {
                    variableMapJson = WorkflowVariableMapHelper.mergeErrorIntoVariableMap(variableMapJson, errorVariable, e.getMessage());
                    variableMapJson = WorkflowVariableMapHelper.runStep(untypedActivityStub, planJson, queueForActivities, workflowInputJson,
                            steps.get(tryCatchCatchStepIndex), variableMapJson);
                    if (variableMapJson == null) variableMapJson = "{}";
                } else {
                    throw e;
                }
            }
            return new PlanRunResult(variableMapJson, sequence);
        }

        @SuppressWarnings("unchecked")
        List<Map<String, String>> nodes = (List<Map<String, String>>) plan.get("nodes");
        if (nodes == null || variableMapJson == null) {
            throw new IllegalStateException("linear plan missing nodes or variable map");
        }
        for (Map<String, String> node : nodes) {
            String activityType = node.get("activityType");
            String nodeId = node.get("nodeId");
            if (activityType == null || nodeId == null) continue;
            Map<String, Object> beforeMap = parseAsMap(variableMapJson);
            variableMapJson = untypedActivityStub.execute(
                    activityType, String.class,
                    planJson, nodeId, variableMapJson, planQueueName, workflowInputJson, null);
            if (variableMapJson == null) variableMapJson = "{}";
            Map<String, Object> afterMap = parseAsMap(variableMapJson);
            sequence = emitNodeProgressEvent(activities, runId, callbackBaseUrl, correlationId,
                    sequence, activityType, nodeId, beforeMap, afterMap);
            Map<String, Object> parsed = parseAsMap(variableMapJson);
            if (parsed != null && parsed.containsKey("dynamicSteps")) {
                Object vm = parsed.get("variableMapJson");
                variableMapJson = vm != null ? vm.toString() : "{}";
                Object stepsObj = parsed.get("dynamicSteps");
                String dynamicStepsJson = stepsObj != null ? MAPPER.writeValueAsString(stepsObj) : "[]";
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> dynamicSteps = (List<Map<String, Object>>) stepsObj;
                if (dynamicSteps != null) {
                    for (Map<String, Object> step : dynamicSteps) {
                        String stepActivityType = step.get("activityType") != null ? step.get("activityType").toString() : null;
                        String stepNodeId = step.get("nodeId") != null ? step.get("nodeId").toString() : null;
                        if (stepActivityType == null || stepNodeId == null) continue;
                        Map<String, Object> stepBefore = parseAsMap(variableMapJson);
                        variableMapJson = untypedActivityStub.execute(
                                stepActivityType, String.class,
                                planJson, stepNodeId, variableMapJson, planQueueName, workflowInputJson, dynamicStepsJson);
                        if (variableMapJson == null) variableMapJson = "{}";
                        Map<String, Object> stepAfter = parseAsMap(variableMapJson);
                        sequence = emitNodeProgressEvent(activities, runId, callbackBaseUrl, correlationId,
                                sequence, stepActivityType, stepNodeId, stepBefore, stepAfter);
                    }
                }
                break;
            }
        }
        return new PlanRunResult(variableMapJson, sequence);
    }

    static Map<String, Object> parseAsMap(String json) {
        if (json == null || json.isBlank()) return null;
        try {
            return MAPPER.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            return null;
        }
    }

    private static long emitNodeProgressEvent(OloKernelActivities activities,
                                              String runId, String callbackBaseUrl, String correlationId,
                                              long sequence, String activityType, String nodeId,
                                              Map<String, Object> beforeMap, Map<String, Object> afterMap) {
        if (activities == null || runId == null || runId.isBlank() || callbackBaseUrl == null || callbackBaseUrl.isBlank()) {
            return sequence;
        }
        String nodeType = normalizeNodeType(activityType);
        Map<String, Object> input = buildSummaryMap(beforeMap);
        Map<String, Object> output = buildSummaryMap(afterMap);
        activities.reportRunEvent(
                runId, callbackBaseUrl, sequence, correlationId,
                nodeId, "root", nodeType, "COMPLETED",
                input, output, Map.of("source", "leaf-node"));
        return sequence + 1;
    }

    private static String normalizeNodeType(String activityType) {
        if (activityType == null || activityType.isBlank()) return "PLUGIN";
        int idx = activityType.indexOf(':');
        String t = idx > 0 ? activityType.substring(0, idx) : activityType;
        return t.toUpperCase();
    }

    private static Map<String, Object> buildSummaryMap(Map<String, Object> map) {
        if (map == null || map.isEmpty()) return Map.of();
        Map<String, Object> out = new LinkedHashMap<>();
        putIfPresent(out, "user_input", map, "user_input", "userInput");
        putIfPresent(out, "user_output", map, "user_output", "userOutput");
        putIfPresent(out, "user_message", map, "user_message", "userMessage");
        return out;
    }

    private static void putIfPresent(Map<String, Object> target, String targetKey,
                                     Map<String, Object> source, String... sourceKeys) {
        for (String k : sourceKeys) {
            Object v = source.get(k);
            if (v != null && !Objects.toString(v, "").isBlank()) {
                target.put(targetKey, v);
                return;
            }
        }
    }
}
