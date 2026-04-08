/*
 * Copyright (c) 2026 Olo Labs. All rights reserved.
 */

package org.olo.worker.workflow.impl;

import org.olo.input.model.WorkflowInput;
import org.olo.worker.activity.OloKernelActivities;
import io.temporal.activity.ActivityOptions;
import io.temporal.workflow.ActivityStub;
import io.temporal.workflow.Workflow;
import io.temporal.workflow.WorkflowInfo;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * OLO Kernel workflow: get plan, then run per-step/per-node or fallback to RunExecutionTree.
 * Plan execution (steps, nodes, variable map merge) is delegated to {@link WorkflowPlanExecutor}.
 */
public class OloKernelWorkflowImpl implements org.olo.worker.workflow.OloKernelWorkflow {

    private static final Duration ACTIVITY_SCHEDULE_TO_CLOSE = Duration.ofMinutes(5);
    private static final Duration ACTIVITY_NO_TIMEOUT_DEBUG = Duration.ofDays(365);
    private static final String EXPENSIVE_ROUTING_PROMPT = "Use expensive routing (dynamic planner flow)?";
    /** Shown in UI and persisted as user input; {@code message} matches {@code label} so clients send this text back. */
    private static final String EXPENSIVE_ROUTING_OPT_YES = "Yes, use dynamic flow";
    private static final String EXPENSIVE_ROUTING_OPT_NO = "No, direct response only";

    private boolean humanApproved;
    private String humanMessage;

    @Override
    public String run(WorkflowInput workflowInput) {
        String taskQueue = null;
        try {
            WorkflowInfo info = Workflow.getInfo();
            if (info != null) taskQueue = info.getTaskQueue();
        } catch (Exception ignored) {
        }
        boolean debugQueue = taskQueue != null && taskQueue.endsWith("-debug");
        Duration activityTimeout = debugQueue ? ACTIVITY_NO_TIMEOUT_DEBUG : ACTIVITY_SCHEDULE_TO_CLOSE;

        OloKernelActivities activities = Workflow.newActivityStub(
                OloKernelActivities.class,
                ActivityOptions.newBuilder().setStartToCloseTimeout(activityTimeout).build());
        ActivityStub untypedActivityStub = Workflow.newUntypedActivityStub(
                ActivityOptions.newBuilder().setStartToCloseTimeout(activityTimeout).build());

        activities.processInput(workflowInput.toJson());
        String queueName = workflowInput.getRouting() != null ? workflowInput.getRouting().getPipeline() : null;
        String queueNameOrEmpty = queueName != null ? queueName : "";
        String workflowInputJson = workflowInput.toJson();
        String runId = workflowInput.getContext() != null ? workflowInput.getContext().getRunId() : "";
        String callbackBaseUrl = workflowInput.getContext() != null ? workflowInput.getContext().getCallbackBaseUrl() : "";
        String correlationId = workflowInput.getContext() != null ? workflowInput.getContext().getCorrelationId() : null;
        String userQuery = extractUserQuery(workflowInput);
        long sequenceNumber = 1L;

        Map<String, Object> humanWaitInput = new LinkedHashMap<>();
        humanWaitInput.put("message", EXPENSIVE_ROUTING_PROMPT);
        humanWaitInput.put(
                "options",
                List.of(
                        Map.of("label", EXPENSIVE_ROUTING_OPT_YES, "approved", true, "message", EXPENSIVE_ROUTING_OPT_YES),
                        Map.of("label", EXPENSIVE_ROUTING_OPT_NO, "approved", false, "message", EXPENSIVE_ROUTING_OPT_NO)));
        activities.reportRunEvent(
                runId, callbackBaseUrl, sequenceNumber++, correlationId,
                "human-routing-gate", "root", "HUMAN", "WAITING",
                humanWaitInput,
                Map.of("type", "USER_INPUT_REQUIRED", "taskId", "expensive-routing"),
                Map.of("awaitingSignal", "humanInput"));
        Workflow.await(() -> humanMessage != null);
        activities.reportRunEvent(
                runId, callbackBaseUrl, sequenceNumber++, correlationId,
                "human-routing-gate", "root", "HUMAN", "COMPLETED",
                null,
                Map.of(
                        "approved", humanApproved,
                        "response", humanMessage != null ? humanMessage : ""),
                Map.of("taskId", "expensive-routing"));

        if (!humanApproved) {
            String directResponse = activities.getChatResponse("architect", userQuery);
            String safeResponse = directResponse != null ? directResponse : "";
            activities.reportRunEvent(
                    runId, callbackBaseUrl, sequenceNumber, correlationId,
                    "direct-model-response", "root", "MODEL", "COMPLETED",
                    Map.of("mode", "direct", "plannerSkipped", true),
                    Map.of("response", safeResponse),
                    Map.of("reason", "expensive_routing_declined"));
            return safeResponse;
        }

        String planJson = activities.getExecutionPlan(queueNameOrEmpty, workflowInputJson);
        if (planJson == null || !planJson.contains("\"linear\":true")) {
            Workflow.getLogger(OloKernelWorkflowImpl.class).info(
                    "Scheduling RunExecutionTree activity: tree is non-linear (plan is null or linear=false)");
            String result = activities.runExecutionTree(queueNameOrEmpty, workflowInputJson);
            return result != null ? result : "";
        }

        try {
            WorkflowPlanExecutor.PlanRunResult planRun = WorkflowPlanExecutor.runPlan(
                    planJson, untypedActivityStub, activities,
                    queueNameOrEmpty, workflowInputJson, runId, callbackBaseUrl, correlationId, sequenceNumber);
            String variableMapJson = planRun.variableMapJson();
            String result = activities.applyResultMapping(planJson, variableMapJson);
            return result != null ? result : "";
        } catch (Exception e) {
            Workflow.getLogger(OloKernelWorkflowImpl.class).warn(
                    "Per-node execution failed, falling back to RunExecutionTree: {}", e.getMessage());
            Workflow.getLogger(OloKernelWorkflowImpl.class).info(
                    "Scheduling RunExecutionTree activity: fallback after per-node execution failure");
            String result = activities.runExecutionTree(queueNameOrEmpty, workflowInputJson);
            return result != null ? result : "";
        }
    }

    @Override
    public void humanInput(boolean approved, String message) {
        String normalized = message != null ? message.trim().toLowerCase() : "";
        boolean approvedByMessage = "yes".equals(normalized) || "y".equals(normalized)
                || "approve".equals(normalized) || "approved".equals(normalized);
        humanApproved = approved || approvedByMessage;
        humanMessage = message != null ? message : "";
    }

    private static Optional<String> getInputValue(WorkflowInput input, String name) {
        if (input == null || input.getInputs() == null) return Optional.empty();
        return input.getInputs().stream()
                .filter(i -> name.equals(i.getName()))
                .findFirst()
                .map(i -> i.getValue() != null ? i.getValue() : "");
    }

    private static String extractUserQuery(WorkflowInput input) {
        return getInputValue(input, "user_query")
                .or(() -> getInputValue(input, "userQuery"))
                .orElse("");
    }
}
