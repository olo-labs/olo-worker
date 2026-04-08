/*
 * Copyright (c) 2026 Olo Labs. All rights reserved.
 */

package org.olo.worker.plugins;

import org.olo.plugin.ExecutablePlugin;
import org.olo.plugin.ModelExecutorPlugin;
import org.olo.plugin.PluginRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Registers default consensus plugins (architect/critic) for development environments.
 * These are mock implementations that can be replaced with real model executors in production.
 */
public final class ConsensusPluginRegistrar {

    private static final Logger log = LoggerFactory.getLogger(ConsensusPluginRegistrar.class);

    private ConsensusPluginRegistrar() {}

    /**
     * Registers consensus plugins for the given tenant IDs if they are not already registered.
     * Creates mock architect and critic model executors, and a consensus subtree creator.
     *
     * @param tenantIds set of tenant IDs to register plugins for
     */
    public static void registerConsensusPluginsIfMissing(Set<String> tenantIds) {
        PluginRegistry registry = PluginRegistry.getInstance();
        
        ExecutablePlugin consensusSubtreeCreator = createConsensusSubtreeCreator();
        ModelExecutorPlugin architect = createArchitectModelExecutor();
        ModelExecutorPlugin critic = createCriticModelExecutor();

        for (String tenantId : tenantIds) {
            registerSubtreeCreatorIfMissing(registry, tenantId, consensusSubtreeCreator);
            registerModelExecutorIfMissing(registry, tenantId, "architect", architect);
            registerModelExecutorIfMissing(registry, tenantId, "critic", critic);
        }
    }

    private static ExecutablePlugin createConsensusSubtreeCreator() {
        return (inputs, tenantConfig) -> {
            String planText = extractPlanText(inputs);
            int maxRounds = extractMaxRounds(inputs);

            Map<String, Object> variablesToInject = new LinkedHashMap<>();
            variablesToInject.put("user_query", planText);
            variablesToInject.put("maxRounds", maxRounds);

            Map<String, Object> out = new LinkedHashMap<>();
            out.put("variablesToInject", variablesToInject);
            out.put("steps", buildConsensusSteps(maxRounds));
            out.put("user_input", planText);
            out.put("user_output", out.get("steps") != null ? 
                    ((java.util.List<?>) out.get("steps")).size() + " planner step(s)" : "0 planner step(s)");
            out.put("user_message", "Consensus subtree: " + maxRounds + " round(s), " + 
                    (out.get("steps") != null ? ((java.util.List<?>) out.get("steps")).size() : 0) + " step(s)");
            return out;
        };
    }

    private static String extractPlanText(Map<String, Object> inputs) {
        if (inputs == null || inputs.get("planText") == null) {
            return "No task specified";
        }
        String planText = inputs.get("planText").toString().trim();
        return planText.isEmpty() ? "No task specified" : planText;
    }

    private static int extractMaxRounds(Map<String, Object> inputs) {
        if (inputs == null || inputs.get("maxRounds") == null) {
            return 2;
        }
        Object maxRoundsObj = inputs.get("maxRounds");
        if (maxRoundsObj instanceof Number n) {
            return Math.max(1, Math.min(5, n.intValue()));
        }
        return 2;
    }

    private static java.util.List<Map<String, Object>> buildConsensusSteps(int maxRounds) {
        java.util.List<Map<String, Object>> steps = new java.util.ArrayList<>();
        int stepIndex = 0;

        for (int round = 0; round < maxRounds; round++) {
            // PROPOSE (Architect)
            String proposePrompt = buildProposePrompt(round, stepIndex);
            steps.add(createStep("architect", proposePrompt));
            stepIndex++;

            // CRITIQUE (Critic)
            String critiquePrompt = buildCritiquePrompt(stepIndex);
            steps.add(createStep("critic", critiquePrompt));
            stepIndex++;

            // REVISE (Architect) — final revision each round
            String revisePrompt = buildRevisePrompt(stepIndex);
            steps.add(createStep("architect", revisePrompt));
            stepIndex++;
        }

        return steps;
    }

    private static String buildProposePrompt(int round, int stepIndex) {
        if (round == 0) {
            return "You are the Architect. Design a solution for the following task. Be creative and exploratory.\n\nTask: {{user_query}}";
        }
        return "You are the Architect. Based on the critique below, revise and improve your proposal.\n\n" +
               "Previous revised solution:\n{{__planner_step_" + (stepIndex - 1) + "_response}}\n" +
               "Critique:\n{{__planner_step_" + (stepIndex - 2) + "_response}}\n\n" +
               "Provide your revised solution.";
    }

    private static String buildCritiquePrompt(int stepIndex) {
        return "You are the Critic. Review and analyze the following proposal for weaknesses, risks, and improvements. " +
               "Be analytical and constructive.\n\nProposal:\n{{__planner_step_" + (stepIndex - 1) + "_response}}";
    }

    private static String buildRevisePrompt(int stepIndex) {
        return "You are the Architect. Produce a final revised solution incorporating the critique.\n\n" +
               "Your previous proposal:\n{{__planner_step_" + (stepIndex - 2) + "_response}}\n" +
               "Critique:\n{{__planner_step_" + (stepIndex - 1) + "_response}}\n\n" +
               "Provide the final revised solution.";
    }

    private static Map<String, Object> createStep(String pluginRef, String prompt) {
        Map<String, Object> step = new LinkedHashMap<>();
        step.put("pluginRef", pluginRef);
        step.put("prompt", prompt);
        return step;
    }

    private static ModelExecutorPlugin createArchitectModelExecutor() {
        return (inputs, tenantConfig) -> {
            String prompt = inputs != null && inputs.get("prompt") != null 
                    ? inputs.get("prompt").toString() 
                    : "";
            String response = "[Architect mock] " + prompt;
            
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("responseText", response);
            m.put("user_input", prompt);
            m.put("user_output", response);
            m.put("user_message", "Architect model response");
            return m;
        };
    }

    private static ModelExecutorPlugin createCriticModelExecutor() {
        return (inputs, tenantConfig) -> {
            String prompt = inputs != null && inputs.get("prompt") != null 
                    ? inputs.get("prompt").toString() 
                    : "";
            String response = "[Critic mock] " + prompt;
            
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("responseText", response);
            m.put("user_input", prompt);
            m.put("user_output", response);
            m.put("user_message", "Critic model response");
            return m;
        };
    }

    private static void registerSubtreeCreatorIfMissing(
            PluginRegistry registry, 
            String tenantId, 
            ExecutablePlugin plugin
    ) {
        if (registry.getExecutable(tenantId, "consensus_subtree_creator") == null) {
            registry.registerSubtreeCreator(tenantId, "consensus_subtree_creator", plugin);
            log.info("Registered dev consensus SUBTREE_CREATOR for tenant={}", tenantId);
        }
    }

    private static void registerModelExecutorIfMissing(
            PluginRegistry registry, 
            String tenantId, 
            String pluginId, 
            ModelExecutorPlugin plugin
    ) {
        if (registry.getModelExecutor(tenantId, pluginId) == null) {
            registry.registerModelExecutor(tenantId, pluginId, plugin);
            log.info("Registered dev consensus MODEL_EXECUTOR ({}) for tenant={}", pluginId, tenantId);
        }
    }
}