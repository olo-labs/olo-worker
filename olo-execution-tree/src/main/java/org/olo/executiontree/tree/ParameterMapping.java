/*
 * Copyright (c) 2026 Olo Labs. All rights reserved.
 */

package org.olo.executiontree.tree;

/**
 * Mapping between a plugin parameter and a variable (or vice versa).
 * Used in protocol and worker for input/output mappings.
 */
public record ParameterMapping(String pluginParameter, String variable) {
    public ParameterMapping {
        pluginParameter = pluginParameter != null ? pluginParameter : "";
        variable = variable != null ? variable : "";
    }
    /** Alias for record accessor for protocol/worker compatibility. */
    public String getPluginParameter() { return pluginParameter; }
    /** Alias for record accessor for protocol/worker compatibility. */
    public String getVariable() { return variable; }
}
