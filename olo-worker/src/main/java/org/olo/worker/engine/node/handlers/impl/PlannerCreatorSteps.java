/*
 * Copyright (c) 2026 Olo Labs. All rights reserved.
 */

package org.olo.worker.engine.node.handlers.impl;

import org.olo.executiontree.tree.ExecutionTreeNode;
import org.olo.executiontree.tree.NodeType;
import org.olo.executiontree.tree.ParameterMapping;
import org.olo.node.NodeFeatureEnricher;
import org.olo.node.NodeSpec;
import org.olo.node.PipelineFeatureContextImpl;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Single responsibility: build NodeSpec and ExecutionTreeNode instances from planner creator steps.
 */
final class PlannerCreatorSteps {

    private PlannerCreatorSteps() {
    }

  /** Stable variable that always holds the latest step's `responseText`. */
  private static final String LAST_RESPONSE_VAR = "__planner_last_response";

    static List<NodeSpec> nodeSpecsFromCreatorSteps(List<Map<String, Object>> steps) {
        List<NodeSpec> specs = new ArrayList<>();
        for (int i = 0; i < steps.size(); i++) {
            Map<String, Object> step = steps.get(i);
            String pluginRef = step != null && step.get("pluginRef") != null
                    ? step.get("pluginRef").toString().trim()
                    : null;
            if (pluginRef == null || pluginRef.isBlank()) continue;
            String promptVar = "__planner_step_" + i + "_prompt";
            String responseVar = "__planner_step_" + i + "_response";
            List<ParameterMapping> inputMappings = List.of(new ParameterMapping("prompt", promptVar));
            // Also write every step's response into a stable "last response" variable so resultMapping
            // doesn't depend on internal step indexes changing over time.
            List<ParameterMapping> outputMappings = List.of(
                    new ParameterMapping("responseText", responseVar),
                    new ParameterMapping("responseText", LAST_RESPONSE_VAR)
            );
            specs.add(NodeSpec.plugin("step-" + i + "-" + pluginRef, pluginRef, inputMappings, outputMappings));
        }
        return specs;
    }

    static List<ExecutionTreeNode> buildNodesFromCreatorSteps(List<Map<String, Object>> steps,
                                                              PipelineFeatureContextImpl featureContext,
                                                              NodeFeatureEnricher nodeFeatureEnricher) {
        List<String> emptyStrList = List.of();
        List<ExecutionTreeNode> nodes = new ArrayList<>();
        for (int i = 0; i < steps.size(); i++) {
            Map<String, Object> step = steps.get(i);
            String pluginRef = step != null && step.get("pluginRef") != null
                    ? step.get("pluginRef").toString().trim()
                    : null;
            if (pluginRef == null || pluginRef.isBlank()) continue;
            String promptVar = "__planner_step_" + i + "_prompt";
            String responseVar = "__planner_step_" + i + "_response";
            List<ParameterMapping> inputMappings = List.of(new ParameterMapping("prompt", promptVar));
            List<ParameterMapping> outputMappings = List.of(
                    new ParameterMapping("responseText", responseVar),
                    new ParameterMapping("responseText", LAST_RESPONSE_VAR)
            );
            ExecutionTreeNode child = new ExecutionTreeNode(
                    UUID.randomUUID().toString(),
                    "step-" + i + "-" + pluginRef,
                    NodeType.PLUGIN,
                    List.<ExecutionTreeNode>of(),
                    "PLUGIN",
                    pluginRef,
                    inputMappings,
                    outputMappings,
                    emptyStrList, emptyStrList, emptyStrList, emptyStrList, emptyStrList, emptyStrList, emptyStrList, emptyStrList,
                    Map.<String, Object>of(),
                    null, null, null, null
            );
            nodes.add(nodeFeatureEnricher.enrich(child, featureContext));
        }
        return nodes;
    }
}

