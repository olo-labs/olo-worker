/*
 * Copyright (c) 2026 Olo Labs. All rights reserved.
 */

package org.olo.worker.factory;

import org.olo.config.OloSessionCache;
import org.olo.config.impl.InMemorySessionCache;
import org.olo.ledger.ExecutionEventSink;
import org.olo.ledger.RunLedger;
import org.olo.ledger.impl.NoOpLedgerStore;
import org.olo.ledger.impl.NoOpRunLedger;
import org.olo.node.DynamicNodeBuilder;
import org.olo.node.NodeFeatureEnricher;
import org.olo.plugin.PluginExecutorFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Factory for creating worker components with sensible defaults.
 * Handles fallback to no-op implementations when real implementations are not available.
 */
public final class WorkerComponentFactory {

    private static final Logger log = LoggerFactory.getLogger(WorkerComponentFactory.class);

    private WorkerComponentFactory() {}

    /**
     * Creates a RunLedger instance. Returns a no-op implementation by default.
     *
     * @return RunLedger instance
     */
    public static RunLedger createRunLedger() {
        return new NoOpRunLedger(new NoOpLedgerStore());
    }

    /**
     * Creates an OloSessionCache instance. Returns an in-memory implementation by default.
     *
     * @return OloSessionCache instance
     */
    public static OloSessionCache createSessionCache() {
        return new InMemorySessionCache();
    }

    /**
     * Creates an ExecutionEventSink instance. Returns a no-op implementation by default.
     *
     * @return ExecutionEventSink instance
     */
    public static ExecutionEventSink createExecutionEventSink() {
        return ExecutionEventSink.noOp();
    }

    /**
     * Creates a PluginExecutorFactory instance.
     * Attempts to load DefaultPluginExecutorFactory, falls back to no-op if not available.
     *
     * @return PluginExecutorFactory instance
     */
    public static PluginExecutorFactory createPluginExecutorFactory() {
        try {
            Class<?> clazz = Class.forName("org.olo.plugin.impl.DefaultPluginExecutorFactory");
            return (PluginExecutorFactory) clazz.getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            log.warn("DefaultPluginExecutorFactory not on classpath; using no-op plugin factory: {}", 
                     e.getMessage());
            return createNoOpPluginExecutorFactory();
        }
    }

    private static PluginExecutorFactory createNoOpPluginExecutorFactory() {
        return (tenantId, cache) -> new org.olo.plugin.PluginExecutor() {
            @Override
            public String execute(String pluginId, String inputsJson, String nodeId) {
                return "{}";
            }

            @Override
            public String toJson(java.util.Map<String, Object> map) {
                return "{}";
            }

            @Override
            public java.util.Map<String, Object> fromJson(String json) {
                return java.util.Map.of();
            }
        };
    }

    /**
     * Creates a DynamicNodeBuilder instance.
     * Returns a stub that throws UnsupportedOperationException if not properly wired.
     *
     * @return DynamicNodeBuilder instance
     */
    public static DynamicNodeBuilder createDynamicNodeBuilder() {
        return (spec, context) -> {
            throw new UnsupportedOperationException(
                    "DynamicNodeBuilder not wired; add olo-worker-execution-tree or bootstrap implementation."
            );
        };
    }

    /**
     * Creates a NodeFeatureEnricher instance.
     * Returns a no-op implementation that passes through nodes unchanged.
     *
     * @return NodeFeatureEnricher instance
     */
    public static NodeFeatureEnricher createNodeFeatureEnricher() {
        return (node, context) -> node;
    }
}