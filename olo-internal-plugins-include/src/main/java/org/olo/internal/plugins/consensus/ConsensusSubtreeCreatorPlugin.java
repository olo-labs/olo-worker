/*
 * Copyright (c) 2026 Olo Labs. All rights reserved.
 */

package org.olo.internal.plugins.consensus;

import org.olo.config.TenantConfig;
import org.olo.plugin.ExecutablePlugin;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * SUBTREE_CREATOR plugin for the Multi-Model Consensus use case.
 * <p>
 * Input: {@code planText} — user task (e.g. "Design a fault-tolerant microservice architecture").
 * Output: {@code variablesToInject} (user_query, maxRounds), {@code steps} — list of steps for
 * propose → critique → revise (repeated for maxRounds). Each step has {@code pluginRef} and {@code prompt}.
 * Prompts for critique/revise use placeholders {{__planner_step_N_response}} resolved at runtime.
 */
public final class ConsensusSubtreeCreatorPlugin implements ExecutablePlugin {

    private static final String ARCHITECT_REF = "architect";
    private static final String CRITIC_REF = "critic";
    private static final int DEFAULT_MAX_ROUNDS = 2;

    @Override
    public Map<String, Object> execute(Map<String, Object> inputs, TenantConfig tenantConfig) throws Exception {
        String planText = inputs != null && inputs.get("planText") != null
                ? inputs.get("planText").toString().trim()
                : "";
        if (planText.isEmpty()) planText = "No task specified";

        Object maxRoundsObj = inputs != null ? inputs.get("maxRounds") : null;
        int maxRounds = DEFAULT_MAX_ROUNDS;
        if (maxRoundsObj instanceof Number n) maxRounds = Math.max(1, Math.min(5, n.intValue()));

        Map<String, Object> variablesToInject = new LinkedHashMap<>();
        variablesToInject.put("user_query", planText);
        variablesToInject.put("maxRounds", maxRounds);

        List<Map<String, Object>> steps = new ArrayList<>();
        int stepIndex = 0;

        for (int round = 0; round < maxRounds; round++) {
            // PROPOSE (Architect)
            String proposePrompt = round == 0
                    ? "You are the Architect. Design a solution for the following task. Be creative and exploratory.\n\nTask: {{user_query}}"
                    : "You are the Architect. Based on the critique below, revise and improve your proposal.\n\nPrevious revised solution:\n{{__planner_step_" + (stepIndex - 1) + "_response}}\n\nCritique:\n{{__planner_step_" + (stepIndex - 2) + "_response}}\n\nProvide your revised solution.";
            steps.add(step(ARCHITECT_REF, proposePrompt));
            stepIndex++;

            // CRITIQUE (Critic)
            String critiquePrompt = "You are the Critic. Review and analyze the following proposal for weaknesses, risks, and improvements. Be analytical and constructive.\n\nProposal:\n{{__planner_step_" + (stepIndex - 1) + "_response}}";
            steps.add(step(CRITIC_REF, critiquePrompt));
            stepIndex++;

            // REVISE (Architect) — optional second architect step per round to produce final revision
            String revisePrompt = "You are the Architect. Produce a final revised solution incorporating the critique.\n\nYour previous proposal:\n{{__planner_step_" + (stepIndex - 2) + "_response}}\n\nCritique:\n{{__planner_step_" + (stepIndex - 1) + "_response}}\n\nProvide the final revised solution.";
            steps.add(step(ARCHITECT_REF, revisePrompt));
            stepIndex++;
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("variablesToInject", variablesToInject);
        out.put("steps", steps);
        return out;
    }

    private static Map<String, Object> step(String pluginRef, String prompt) {
        Map<String, Object> step = new LinkedHashMap<>();
        step.put("pluginRef", pluginRef);
        step.put("prompt", prompt);
        return step;
    }
}
