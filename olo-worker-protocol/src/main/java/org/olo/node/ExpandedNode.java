/*
 * Copyright (c) 2026 Olo Labs. All rights reserved.
 */

package org.olo.node;

/**
 * Read-only view of a node created by the worker after expansion. The planner receives
 * this; it does not see {@link org.olo.executiontree.tree.ExecutionTreeNode} or tree internals.
 */
public record ExpandedNode(
        String id,
        String displayName,
        String pluginRef
) {}
