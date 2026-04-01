/*
 * Copyright (c) 2026 Olo Labs. All rights reserved.
 */

package org.olo.node;

import org.olo.executiontree.scope.Scope;

/**
 * Immutable context used when attaching pipeline and queue-based features to a node.
 * High-level modules (worker, planner integrations) use this with {@link NodeFeatureEnricher}
 * so they depend only on the abstraction, not on how features are resolved.
 *
 * @see NodeFeatureEnricher
 * @see NodeFeatureEnricherFactory
 */
public interface PipelineFeatureContext {

    /** Pipeline scope (plugins, features). Used to determine which scope features apply to the node. */
    Scope getScope();

    /** Task queue name (e.g. for queue-based feature attachment such as {@code -debug} → debug feature). */
    String getQueueName();
}
