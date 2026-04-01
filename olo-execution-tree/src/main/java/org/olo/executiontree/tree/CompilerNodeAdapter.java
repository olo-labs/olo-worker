/*
 * Copyright (c) 2026 Olo Labs. All rights reserved.
 */

package org.olo.executiontree.tree;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Adapts a compiler {@link org.olo.executiontree.ExecutionTreeNode} to the
 * protocol/worker {@link ExecutionTreeNode} type so pipeline.getExecutionTree()
 * can return a tree.ExecutionTreeNode.
 */
public final class CompilerNodeAdapter extends ExecutionTreeNode {

    @SuppressWarnings("unused")
    private final org.olo.executiontree.ExecutionTreeNode delegate;

    public CompilerNodeAdapter(org.olo.executiontree.ExecutionTreeNode delegate) {
        super(
            delegate.getId(),
            delegate.getName(),
            toTreeNodeType(delegate.getType()),
            wrapChildren(delegate.getChildren()),
            "PLUGIN",
            getPluginRef(delegate),
            toParamList(delegate.getInputMappings()),
            toParamList(delegate.getOutputMappings()),
            delegate.getFeatures() != null ? delegate.getFeatures() : List.of(),
            delegate.getPreExecution() != null ? delegate.getPreExecution() : List.of(),
            delegate.getPostSuccessExecution() != null ? delegate.getPostSuccessExecution() : List.of(),
            delegate.getPostErrorExecution() != null ? delegate.getPostErrorExecution() : List.of(),
            delegate.getFinallyExecution() != null ? delegate.getFinallyExecution() : List.of(),
            List.<String>of(), List.<String>of(), List.<String>of(),
            delegate.getParams() != null ? delegate.getParams() : Map.of(),
            null, null, null, null
        );
        this.delegate = delegate;
    }

    private static NodeType toTreeNodeType(org.olo.executiontree.NodeType t) {
        if (t == null) return NodeType.PLUGIN;
        try {
            return NodeType.valueOf(t.name());
        } catch (Exception e) {
            return NodeType.PLUGIN;
        }
    }

    private static List<ExecutionTreeNode> wrapChildren(List<org.olo.executiontree.ExecutionTreeNode> children) {
        if (children == null || children.isEmpty()) return List.of();
        List<ExecutionTreeNode> out = new ArrayList<>(children.size());
        for (org.olo.executiontree.ExecutionTreeNode c : children) {
            out.add(new CompilerNodeAdapter(c));
        }
        return out;
    }

    private static String getPluginRef(org.olo.executiontree.ExecutionTreeNode n) {
        Map<String, Object> p = n.getParams();
        if (p == null) return "";
        Object o = p.get("pluginRef");
        return o != null ? o.toString() : "";
    }

    private static List<ParameterMapping> toParamList(Map<String, Object> map) {
        if (map == null || map.isEmpty()) return List.of();
        return map.entrySet().stream()
            .map(e -> new ParameterMapping(e.getKey(), e.getValue() != null ? e.getValue().toString() : ""))
            .collect(Collectors.toList());
    }

    @Override
    public java.util.List<String> getAllowedTenantIds() {
        return delegate != null ? delegate.getAllowedTenantIds() : List.of();
    }
}
