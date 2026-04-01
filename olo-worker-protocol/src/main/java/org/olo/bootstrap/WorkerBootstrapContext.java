/*
 * Copyright (c) 2026 Olo Labs. All rights reserved.
 */

package org.olo.bootstrap;

import org.olo.node.DynamicNodeBuilder;
import org.olo.node.NodeFeatureEnricherFactory;
import org.olo.plugin.PluginExecutorFactory;

/**
 * Extended bootstrap context returned from {@code OloBootstrap.initializeWorker()}.
 * Adds run ledger, session cache, plugin executor factory, and dynamic-node builder/enricher
 * so the worker and planner depend only on protocol contracts.
 * <p>
 * Types are {@link Object} where needed so that protocol does not depend on ledger or
 * configuration implementation.
 */
public interface WorkerBootstrapContext extends BootstrapContext {

    /** Run ledger (or null if disabled). Concrete type: {@link org.olo.ledger.RunLedger}. */
    Object getRunLedger();

    /** Session cache. Concrete type: {@link org.olo.config.OloSessionCache}. */
    Object getSessionCache();

    /** Execution event sink for chat UI (semantic steps). May be null. */
    Object getExecutionEventSink();

    /**
     * Factory that creates {@link org.olo.plugin.PluginExecutor} for a tenant.
     * Implementation (e.g. from plugin module) uses PluginRegistry; worker uses only this contract.
     */
    PluginExecutorFactory getPluginExecutorFactory();

    /**
     * Builder for fully designed dynamic nodes (planner requests new nodes through this contract).
     * Implementation in bootstrap; returns nodes with pipeline/queue features attached.
     */
    DynamicNodeBuilder getDynamicNodeBuilder();

    /**
     * Factory for {@link org.olo.node.NodeFeatureEnricher} (attach features to existing nodes).
     * Used when enriching dynamic steps from JSON in the worker.
     */
    NodeFeatureEnricherFactory getNodeFeatureEnricherFactory();

    /**
     * Invokes resource cleanup (e.g. {@link org.olo.annotations.ResourceCleanup#onExit()}) on all
     * registered plugins and features. Implementation in bootstrap; worker calls this on shutdown
     * without depending on PluginRegistry or FeatureRegistry.
     */
    void runResourceCleanup();
}
