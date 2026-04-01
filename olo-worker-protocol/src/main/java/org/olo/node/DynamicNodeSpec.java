/*
 * Copyright (c) 2026 Olo Labs. All rights reserved.
 */

package org.olo.node;

import org.olo.executiontree.tree.ParameterMapping;

import java.util.List;

/**
 * Spec for a dynamic (e.g. planner-added) PLUGIN node. The planner (or other caller)
 * supplies this to {@link DynamicNodeBuilder#buildNode} and receives a fully designed
 * {@link org.olo.executiontree.tree.ExecutionTreeNode} with pipeline features attached,
 * ready to attach to the execution tree.
 */
public record DynamicNodeSpec(
        String id,
        String displayName,
        String pluginRef,
        List<ParameterMapping> inputMappings,
        List<ParameterMapping> outputMappings
) {
    public DynamicNodeSpec {
        inputMappings = inputMappings != null ? List.copyOf(inputMappings) : List.of();
        outputMappings = outputMappings != null ? List.copyOf(outputMappings) : List.of();
    }
}
