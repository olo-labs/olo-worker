/*
 * Copyright (c) 2026 Olo Labs. All rights reserved.
 */

package org.olo.features;

/**
 * Contract for feature logic that runs before a tree node executes.
 * Implement this (and optionally {@link FinallyCall}, {@link PostSuccessCall}, {@link PostErrorCall}, or {@link PreFinallyCall}) and annotate the class with {@link org.olo.annotations.OloFeature}.
 */
@FunctionalInterface
public interface PreNodeCall {

    /**
     * Called before the node is executed.
     *
     * @param context node context (id, type, nodeType, attributes)
     */
    void before(NodeExecutionContext context);
}
