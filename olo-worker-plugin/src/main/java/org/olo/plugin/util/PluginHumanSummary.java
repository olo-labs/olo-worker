/*
 * Copyright (c) 2026 Olo Labs. All rights reserved.
 */

package org.olo.plugin.util;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Adds human-readable {@code user_input}, {@code user_output}, and {@code user_message} to plugin
 * output maps for UI/event streaming. Plugins may set these explicitly; this utility fills gaps.
 */
public final class PluginHumanSummary {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private PluginHumanSummary() {}

    /**
     * Returns a new map containing all plugin outputs plus summary fields where missing.
     */
    public static Map<String, Object> enrich(String pluginId, Map<String, Object> inputs, Map<String, Object> outputs) {
        LinkedHashMap<String, Object> m = new LinkedHashMap<>();
        if (outputs != null) {
            m.putAll(outputs);
        }
        if (isBlank(m.get("user_input"))) {
            m.put("user_input", summarizeInputs(inputs));
        }
        if (isBlank(m.get("user_output"))) {
            m.put("user_output", summarizeOutputs(m));
        }
        if (isBlank(m.get("user_message"))) {
            m.put("user_message", defaultMessage(pluginId, m));
        }
        return m;
    }

    private static boolean isBlank(Object v) {
        return v == null || Objects.toString(v).isBlank();
    }

    private static String summarizeInputs(Map<String, Object> inputs) {
        if (inputs == null || inputs.isEmpty()) return "";
        String[] keys = { "prompt", "planText", "query", "message", "text", "userQuery", "input", "question", "user_query" };
        for (String k : keys) {
            Object v = inputs.get(k);
            if (v != null && !Objects.toString(v).isBlank()) {
                return truncate(Objects.toString(v), 2000);
            }
        }
        try {
            return truncate(MAPPER.writeValueAsString(inputs), 2000);
        } catch (Exception e) {
            return truncate(inputs.toString(), 2000);
        }
    }

    private static String summarizeOutputs(Map<String, Object> outputs) {
        if (outputs == null || outputs.isEmpty()) return "";
        String[] keys = { "responseText", "result", "content", "answer", "output", "text", "response" };
        for (String k : keys) {
            Object v = outputs.get(k);
            if (v != null && !Objects.toString(v).isBlank()) {
                return truncate(Objects.toString(v), 4000);
            }
        }
        try {
            return truncate(MAPPER.writeValueAsString(outputs), 4000);
        } catch (Exception e) {
            return truncate(outputs.toString(), 4000);
        }
    }

    private static String defaultMessage(String pluginId, Map<String, Object> outputs) {
        String id = pluginId != null && !pluginId.isBlank() ? pluginId : "plugin";
        Object steps = outputs != null ? outputs.get("steps") : null;
        if (steps instanceof List<?> list) {
            return "Subtree creator produced " + list.size() + " step(s) for " + id;
        }
        if (outputs != null && !isBlank(outputs.get("responseText"))) {
            return "Model output from " + id;
        }
        return "Completed " + id;
    }

    private static String truncate(String s, int max) {
        if (s == null) return "";
        String t = s.trim();
        return t.length() <= max ? t : t.substring(0, max) + "…";
    }
}
