/*
 * Copyright (c) 2026 Olo Labs. All rights reserved.
 */

package org.olo.executiontree.tree;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Runtime representation of an execution tree node for protocol and worker.
 * Used by {@link org.olo.node.NodeFeatureEnricher}, {@link org.olo.node.DynamicNodeBuilder},
 * and worker activities. Can be constructed directly (e.g. dynamic steps) or adapted from
 * {@link org.olo.executiontree.ExecutionTreeNode} via {@link CompilerNodeAdapter}.
 */
public class ExecutionTreeNode {

    private final String id;
    private final String displayName;
    private final NodeType type;
    private final List<ExecutionTreeNode> children;
    private final String executionKind;
    private final String pluginRef;
    private final List<ParameterMapping> inputMappings;
    private final List<ParameterMapping> outputMappings;
    private final List<String> features;
    private final List<String> preExecution;
    private final List<String> postSuccessExecution;
    private final List<String> postErrorExecution;
    private final List<String> finallyExecution;
    private final List<String> postExecution;
    private final List<String> postFailureExecution;
    private final List<String> postCompleteExecution;
    private final Map<String, Object> params;

    public ExecutionTreeNode(
            String id,
            String displayName,
            NodeType type,
            List<ExecutionTreeNode> children,
            String executionKind,
            String pluginRef,
            List<ParameterMapping> inputMappings,
            List<ParameterMapping> outputMappings,
            List<String> features,
            List<String> preExecution,
            List<String> postSuccessExecution,
            List<String> postErrorExecution,
            List<String> finallyExecution,
            List<String> postExecution,
            List<String> postFailureExecution,
            List<String> postCompleteExecution,
            Map<String, Object> params,
            Object unused1,
            Object unused2,
            Object unused3,
            Object unused4) {
        this.id = id != null ? id : "";
        this.displayName = displayName != null ? displayName : "";
        this.type = type != null ? type : NodeType.PLUGIN;
        this.children = children != null ? List.copyOf(children) : List.of();
        this.executionKind = executionKind != null ? executionKind : "PLUGIN";
        this.pluginRef = pluginRef != null ? pluginRef : "";
        this.inputMappings = inputMappings != null ? List.copyOf(inputMappings) : List.of();
        this.outputMappings = outputMappings != null ? List.copyOf(outputMappings) : List.of();
        this.features = features != null ? List.copyOf(features) : List.of();
        this.preExecution = preExecution != null ? List.copyOf(preExecution) : List.of();
        this.postSuccessExecution = postSuccessExecution != null ? List.copyOf(postSuccessExecution) : List.of();
        this.postErrorExecution = postErrorExecution != null ? List.copyOf(postErrorExecution) : List.of();
        this.finallyExecution = finallyExecution != null ? List.copyOf(finallyExecution) : List.of();
        this.postExecution = postExecution != null ? List.copyOf(postExecution) : List.of();
        this.postFailureExecution = postFailureExecution != null ? List.copyOf(postFailureExecution) : List.of();
        this.postCompleteExecution = postCompleteExecution != null ? List.copyOf(postCompleteExecution) : List.of();
        this.params = params != null ? Map.copyOf(params) : Map.of();
    }

    public String getId() { return id; }
    public String getDisplayName() { return displayName; }
    public NodeType getType() { return type; }
    public List<ExecutionTreeNode> getChildren() { return children; }
    public String getExecutionKind() { return executionKind; }
    public String getPluginRef() { return pluginRef; }
    public List<ParameterMapping> getInputMappings() { return inputMappings; }
    public List<ParameterMapping> getOutputMappings() { return outputMappings; }
    public List<String> getFeatures() { return features; }
    public List<String> getPreExecution() { return preExecution; }
    public List<String> getPostSuccessExecution() { return postSuccessExecution; }
    public List<String> getPostErrorExecution() { return postErrorExecution; }
    public List<String> getFinallyExecution() { return finallyExecution; }
    public List<String> getPostExecution() { return postExecution; }
    public Map<String, Object> getParams() { return params; }
    /** For feature attachment; empty if not set. */
    public List<String> getFeatureNotRequired() { return List.of(); }
    /** For feature attachment; empty if not set. */
    public List<String> getFeatureRequired() { return List.of(); }
    /** Node type string for feature resolution. */
    public String getNodeType() { return type != null ? type.name() : "PLUGIN"; }
    /** Tenant IDs allowed to run (e.g. on root). Empty = no restriction. */
    public List<String> getAllowedTenantIds() { return List.of(); }

    /** Find a node by id in the tree rooted at {@code root}. */
    public static ExecutionTreeNode findNodeById(ExecutionTreeNode root, String nodeId) {
        if (root == null || nodeId == null || nodeId.isBlank()) return null;
        if (nodeId.equals(root.getId())) return root;
        for (ExecutionTreeNode child : root.getChildren()) {
            ExecutionTreeNode found = findNodeById(child, nodeId);
            if (found != null) return found;
        }
        return null;
    }
}
