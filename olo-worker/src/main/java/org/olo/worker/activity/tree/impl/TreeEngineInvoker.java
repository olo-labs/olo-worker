/*
 * Copyright (c) 2026 Olo Labs. All rights reserved.
 */

package org.olo.worker.activity.tree.impl;

import org.olo.bootstrap.runtime.OloRuntimeContext;
import org.olo.input.model.InputItem;
import org.olo.worker.engine.ExecutionEngine;
import org.olo.plugin.PluginExecutorFactory;

import java.util.LinkedHashMap;
import java.util.Map;

final class TreeEngineInvoker {

    static String run(TreeContextResolver.ResolvedContext ctx,
                      OloRuntimeContext runtimeContext,
                      PluginExecutorFactory pluginExecutorFactory,
                      org.olo.node.DynamicNodeBuilder dynamicNodeBuilder,
                      org.olo.node.NodeFeatureEnricher nodeFeatureEnricher) {
        var executor = pluginExecutorFactory.create(ctx.tenantId, ctx.nodeInstanceCache);
        Map<String, Object> inputValues = inputValuesFrom(runtimeContext.getWorkflowInput());
        return ExecutionEngine.run(
                runtimeContext.getPipelineDefinition(),
                ctx.effectiveQueue,
                inputValues,
                executor,
                ctx.tenantId,
                ctx.tenantConfigMap,
                ctx.runId,
                ctx.config,
                dynamicNodeBuilder,
                nodeFeatureEnricher);
    }

    private static Map<String, Object> inputValuesFrom(org.olo.input.model.WorkflowInput input) {
        Map<String, Object> out = new LinkedHashMap<>();
        if (input != null && input.getInputs() != null) {
            for (InputItem item : input.getInputs()) {
                if (item != null && item.getName() != null) {
                    out.put(item.getName(), item.getValue() != null ? item.getValue() : "");
                }
            }
        }
        return out;
    }
}
