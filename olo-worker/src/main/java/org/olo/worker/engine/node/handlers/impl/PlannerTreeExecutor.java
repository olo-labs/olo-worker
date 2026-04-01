/*
 * Copyright (c) 2026 Olo Labs. All rights reserved.
 */

package org.olo.worker.engine.node.handlers.impl;

import org.olo.executiontree.config.PipelineDefinition;
import org.olo.executiontree.tree.ExecutionTreeNode;
import org.olo.node.DynamicNodeExpansionRequest;
import org.olo.node.DynamicNodeFactory;
import org.olo.node.ExpandedNode;
import org.olo.node.NodeSpec;
import org.olo.node.PipelineFeatureContextImpl;
import org.olo.worker.engine.VariableEngine;
import org.olo.worker.engine.node.impl.DynamicNodeFactoryImpl;
import org.olo.worker.engine.node.ExpansionLimits;
import org.olo.worker.engine.node.ExpansionState;
import org.olo.worker.engine.node.NodeParams;
import org.olo.worker.engine.runtime.RuntimeExecutionTree;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Single responsibility: run planner tree expansion (model/interpret, subtree creator or parser, attach to tree).
 */
final class PlannerTreeExecutor {

    private static final Logger log = LoggerFactory.getLogger(PlannerTreeExecutor.class);

    static Object executePlannerTree(ExecutionTreeNode node, PipelineDefinition pipeline,
                                     VariableEngine variableEngine, String queueName, RuntimeExecutionTree tree,
                                     ExpansionState expansionState, ExpansionLimits expansionLimits,
                                     org.olo.worker.engine.node.handlers.HandlerContext ctx) {
        var pluginInvoker = ctx.getPluginInvoker();
        var nodeFeatureEnricher = ctx.getNodeFeatureEnricher();
        String modelPluginRef = NodeParams.paramString(node, "modelPluginRef");
        boolean interpretOnly = (modelPluginRef == null || modelPluginRef.isBlank());
        String parserName = NodeParams.paramString(node, "parser");
        if (parserName == null || parserName.isBlank()) parserName = NodeParams.paramString(node, "treeBuilder");
        if (parserName == null || parserName.isBlank()) parserName = "default";
        String subtreeCreatorPluginRef = NodeParams.paramString(node, "subtreeCreatorPluginRef");
        log.info("Executing PLANNER | nodeId={} | mode={} | modelPluginRef={} | parser={} | subtreeCreator={}",
                node.getId(), interpretOnly ? "interpretOnly" : "model", modelPluginRef, parserName,
                subtreeCreatorPluginRef != null && !subtreeCreatorPluginRef.isBlank() ? subtreeCreatorPluginRef : "-");
        log.info("PLANNER step 1 | nodeId={} | entry | tree present", node.getId());
        String planInputVariable = NodeParams.paramString(node, "planInputVariable");
        if (planInputVariable == null || planInputVariable.isBlank()) planInputVariable = "__planner_result";
        log.info("PLANNER step 2 | nodeId={} | mode={} | modelPluginRef={} | planInputVariable={}", node.getId(), interpretOnly ? "interpretOnly" : "model", modelPluginRef, planInputVariable);

        String planResultJson = PlannerPlanResolver.resolvePlanResultJson(
                node, variableEngine, queueName, interpretOnly, planInputVariable, modelPluginRef, pluginInvoker);
        int planInputLen = planResultJson != null ? planResultJson.length() : 0;
        String planInputSnippet = planResultJson != null && planResultJson.length() > 500
                ? planResultJson.substring(0, 500) + "...[truncated]" : (planResultJson != null ? planResultJson : "");
        log.info("PLANNER step 4 | nodeId={} | plan input | length={} | snippet={}", node.getId(), planInputLen, planInputSnippet);

        if (subtreeCreatorPluginRef != null && !subtreeCreatorPluginRef.isBlank()) {
            log.info("PLANNER step 5a | nodeId={} | using subtreeCreatorPluginRef={}", node.getId(), subtreeCreatorPluginRef);
            Map<String, Object> creatorInput = Map.of("planText", planResultJson != null ? planResultJson : "");
            Map<String, Object> creatorOutput = pluginInvoker.invokeWithInputMap(subtreeCreatorPluginRef, creatorInput);
            @SuppressWarnings("unchecked")
            Map<String, Object> variablesToInject = (Map<String, Object>) creatorOutput.get("variablesToInject");
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> steps = (List<Map<String, Object>>) creatorOutput.get("steps");
            if (variablesToInject != null) {
                for (Map.Entry<String, Object> e : variablesToInject.entrySet()) variableEngine.put(e.getKey(), e.getValue());
            }
            if (steps != null && !steps.isEmpty()) {
                for (int i = 0; i < steps.size(); i++) {
                    Map<String, Object> step = steps.get(i);
                    if (step != null && step.containsKey("prompt")) variableEngine.put("__planner_step_" + i + "_prompt", step.get("prompt"));
                }
                PipelineFeatureContextImpl featureContext = new PipelineFeatureContextImpl(pipeline.getScope(), queueName);
                List<NodeSpec> specs = PlannerCreatorSteps.nodeSpecsFromCreatorSteps(steps);
                DynamicNodeFactory factory = new DynamicNodeFactoryImpl(tree, featureContext, nodeFeatureEnricher, expansionLimits, expansionState);
                factory.expand(new DynamicNodeExpansionRequest(node.getId(), specs));
                log.info("PLANNER step 6a | nodeId={} | expand from creator | count={} | stepRefs={}", node.getId(), specs.size(),
                        specs.stream().map(NodeSpec::pluginRef).filter(Objects::nonNull).toList());
                String planSummary = humanReadablePlanSummary(specs, null);
                log.info("PLANNER step 8a | nodeId={} | exit (subtreeCreator path)", node.getId());
                return planSummary != null ? Map.of("planSummary", planSummary, "message", planSummary) : null;
            }
            log.info("PLANNER step 8a | nodeId={} | exit (subtreeCreator path, no steps)", node.getId());
            return null;
        }

        log.info("PLANNER step 5b | nodeId={} | using parser (no subtreeCreator)", node.getId());
        log.warn("PLANNER node {}: no parser wired for '{}'; use subtreeCreatorPluginRef for tree expansion", node.getId(), parserName);
        log.info("PLANNER step 9 | nodeId={} | exit", node.getId());
        return null;
    }

    /** Build a human-readable numbered list of the plan for chat UI (e.g. "Planner suggested:\n1. searchDocuments\n2. evaluator"). */
    private static String humanReadablePlanSummary(List<NodeSpec> specs, List<ExpandedNode> expanded) {
        List<String> names = new ArrayList<>();
        if (specs != null) {
            for (NodeSpec s : specs) {
                String ref = s != null ? s.pluginRef() : null;
                String display = s != null && s.displayName() != null && !s.displayName().isBlank() ? s.displayName() : ref;
                if (display != null && !display.isBlank()) names.add(display);
            }
        }
        if (expanded != null) {
            for (ExpandedNode n : expanded) {
                String ref = n != null ? n.pluginRef() : null;
                String display = n != null && n.displayName() != null && !n.displayName().isBlank() ? n.displayName() : ref;
                if (display != null && !display.isBlank() && !names.contains(display)) names.add(display);
            }
        }
        if (names.isEmpty()) return null;
        StringBuilder sb = new StringBuilder("Planner suggested:\n");
        for (int i = 0; i < names.size(); i++) {
            sb.append(i + 1).append(". ").append(names.get(i));
            if (i < names.size() - 1) sb.append("\n");
        }
        return sb.toString();
    }

}
