/*
 * Copyright (c) 2026 Olo Labs. All rights reserved.
 */

package org.olo.node;

import org.olo.executiontree.scope.Scope;

/**
 * Default immutable implementation of {@link PipelineFeatureContext}.
 * Use when building context from pipeline scope and queue name.
 */
public record PipelineFeatureContextImpl(Scope scope, String queueName) implements PipelineFeatureContext {

    @Override
    public Scope getScope() {
        return scope;
    }

    @Override
    public String getQueueName() {
        return queueName;
    }
}
