/*
 * Copyright (c) 2026 Olo Labs. All rights reserved.
 */

package org.olo.executiontree;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Compiles pipeline config (e.g. from JSON) into ExecutionTreeNode and PipelineDefinition.
 * Used during bootstrap PIPELINE_LOADING to produce immutable structures. Does not
 * resolve plugins or features; only builds the tree and registry from config.
 */
public final class ExecutionTreeCompiler {

  private ExecutionTreeCompiler() {}

  private static final String KEY_ID = "id";
  private static final String KEY_TYPE = "type";
  private static final String KEY_NAME = "name";
  private static final String KEY_DISPLAY_NAME = "displayName";
  private static final String KEY_VERSION = "version";
  private static final String KEY_CHILDREN = "children";
  private static final String KEY_PARAMS = "params";
  private static final String KEY_INPUT_MAPPINGS = "inputMappings";
  private static final String KEY_OUTPUT_MAPPINGS = "outputMappings";
  private static final String KEY_TIMEOUT = "timeout";
  private static final String KEY_RETRY_POLICY = "retryPolicy";
  private static final String KEY_EXECUTION_MODE = "executionMode";
  private static final String KEY_METADATA = "metadata";
  private static final String KEY_CONNECTIONS = "connections";
  private static final String KEY_PRE = "preExecution";
  private static final String KEY_POST_SUCCESS = "postSuccessExecution";
  private static final String KEY_POST_ERROR = "postErrorExecution";
  private static final String KEY_FINALLY = "finallyExecution";
  private static final String KEY_FEATURES = "features";
  private static final String KEY_ALLOWED_TENANT_IDS = "allowedTenantIds";

  /**
   * Builds an ExecutionTreeNode from a map (e.g. parsed from executionTree JSON).
   */
  @SuppressWarnings("unchecked")
  public static ExecutionTreeNode compileNode(Map<String, Object> nodeMap) {
    return compileNode(nodeMap, null);
  }

  /**
   * Builds an ExecutionTreeNode from a map, with optional pipeline-level allowed tenant IDs
   * (applied to the root when provided; also read from node map if present).
   */
  @SuppressWarnings("unchecked")
  public static ExecutionTreeNode compileNode(Map<String, Object> nodeMap, List<String> pipelineAllowedTenantIds) {
    String id = string(nodeMap, KEY_ID, "node");
    String typeStr = string(nodeMap, KEY_TYPE, "SEQUENCE");
    NodeType type = parseNodeType(typeStr);

    ExecutionTreeNode.Builder builder = ExecutionTreeNode.builder(id, type);

    String name = string(nodeMap, KEY_NAME, null);
    if (name == null) name = string(nodeMap, KEY_DISPLAY_NAME, null);
    if (name != null) builder.name(name);
    String version = string(nodeMap, KEY_VERSION, null);
    if (version != null) builder.version(version);

    List<Map<String, Object>> childMaps = (List<Map<String, Object>>) nodeMap.get(KEY_CHILDREN);
    if (childMaps != null && !childMaps.isEmpty()) {
      List<ExecutionTreeNode> children = new ArrayList<>();
      for (Map<String, Object> child : childMaps) {
        children.add(compileNode(child));
      }
      builder.children(children);
    }

    Map<String, Object> params = (Map<String, Object>) nodeMap.get(KEY_PARAMS);
    if (params != null && !params.isEmpty()) {
      builder.params(params);
    }

    map(nodeMap, KEY_INPUT_MAPPINGS).ifPresent(builder::inputMappings);
    map(nodeMap, KEY_OUTPUT_MAPPINGS).ifPresent(builder::outputMappings);
    map(nodeMap, KEY_TIMEOUT).ifPresent(builder::timeout);
    map(nodeMap, KEY_RETRY_POLICY).ifPresent(builder::retryPolicy);
    map(nodeMap, KEY_METADATA).ifPresent(builder::metadata);

    Map<String, String> connections = (Map<String, String>) nodeMap.get(KEY_CONNECTIONS);
    if (connections != null && !connections.isEmpty()) {
      builder.connections(connections);
    }

    String execMode = string(nodeMap, KEY_EXECUTION_MODE, null);
    if (execMode != null) {
      builder.executionMode(parseExecutionMode(execMode));
    }

    list(nodeMap, KEY_PRE).ifPresent(builder::preExecution);
    list(nodeMap, KEY_POST_SUCCESS).ifPresent(builder::postSuccessExecution);
    list(nodeMap, KEY_POST_ERROR).ifPresent(builder::postErrorExecution);
    list(nodeMap, KEY_FINALLY).ifPresent(builder::finallyExecution);
    list(nodeMap, KEY_FEATURES).ifPresent(builder::features);

    List<String> allowedTenantIds = list(nodeMap, KEY_ALLOWED_TENANT_IDS).orElse(pipelineAllowedTenantIds);
    if (allowedTenantIds != null && !allowedTenantIds.isEmpty()) {
      builder.allowedTenantIds(allowedTenantIds);
    }
    return builder.build();
  }

  /**
   * Exports an ExecutionTreeNode back to a map with the same shape as compileNode input.
   * Used for round-trip tests and serialization to JSON.
   */
  public static Map<String, Object> toNodeMap(ExecutionTreeNode node) {
    Map<String, Object> out = new LinkedHashMap<>();
    out.put(KEY_ID, node.getId());
    out.put(KEY_TYPE, node.getType().name());
    if (node.getName() != null) out.put(KEY_NAME, node.getName());
    if (node.getVersion() != null) out.put(KEY_VERSION, node.getVersion());
    if (!node.getParams().isEmpty()) out.put(KEY_PARAMS, new LinkedHashMap<>(node.getParams()));
    if (!node.getChildren().isEmpty()) {
      List<Map<String, Object>> childMaps = new ArrayList<>();
      for (ExecutionTreeNode child : node.getChildren()) {
        childMaps.add(toNodeMap(child));
      }
      out.put(KEY_CHILDREN, childMaps);
    }
    if (!node.getInputMappings().isEmpty()) out.put(KEY_INPUT_MAPPINGS, new LinkedHashMap<>(node.getInputMappings()));
    if (!node.getOutputMappings().isEmpty()) out.put(KEY_OUTPUT_MAPPINGS, new LinkedHashMap<>(node.getOutputMappings()));
    if (!node.getTimeout().isEmpty()) out.put(KEY_TIMEOUT, new LinkedHashMap<>(node.getTimeout()));
    if (!node.getRetryPolicy().isEmpty()) out.put(KEY_RETRY_POLICY, new LinkedHashMap<>(node.getRetryPolicy()));
    if (node.getExecutionMode() != null) out.put(KEY_EXECUTION_MODE, node.getExecutionMode().name());
    if (!node.getMetadata().isEmpty()) out.put(KEY_METADATA, new LinkedHashMap<>(node.getMetadata()));
    if (!node.getConnections().isEmpty()) out.put(KEY_CONNECTIONS, new LinkedHashMap<>(node.getConnections()));
    if (!node.getPreExecution().isEmpty()) out.put(KEY_PRE, new ArrayList<>(node.getPreExecution()));
    if (!node.getPostSuccessExecution().isEmpty()) out.put(KEY_POST_SUCCESS, new ArrayList<>(node.getPostSuccessExecution()));
    if (!node.getPostErrorExecution().isEmpty()) out.put(KEY_POST_ERROR, new ArrayList<>(node.getPostErrorExecution()));
    if (!node.getFinallyExecution().isEmpty()) out.put(KEY_FINALLY, new ArrayList<>(node.getFinallyExecution()));
    if (!node.getFeatures().isEmpty()) out.put(KEY_FEATURES, new ArrayList<>(node.getFeatures()));
    if (!node.getAllowedTenantIds().isEmpty()) out.put(KEY_ALLOWED_TENANT_IDS, new ArrayList<>(node.getAllowedTenantIds()));
    return out;
  }

  /**
   * Builds a VariableRegistry from a list of variable declarations (e.g. variableRegistry in config).
   */
  @SuppressWarnings("unchecked")
  public static VariableRegistry compileVariableRegistry(List<Map<String, Object>> varList) {
    if (varList == null || varList.isEmpty()) {
      return new VariableRegistry(List.of());
    }
    List<VariableDeclaration> declarations = new ArrayList<>();
    for (Map<String, Object> v : varList) {
      String name = string(v, "name", null);
      if (name == null) continue;
      String type = string(v, "type", "string");
      String scopeStr = string(v, "scope", "INTERNAL");
      VariableScope scope = parseVariableScope(scopeStr);
      declarations.add(new VariableDeclaration(name, type, scope));
    }
    return new VariableRegistry(declarations);
  }

  /**
   * Builds a Scope from plugins and features maps/sets in config.
   */
  @SuppressWarnings("unchecked")
  public static Scope compileScope(Map<String, Object> plugins, Object features) {
    Map<String, Object> pluginMap = plugins != null ? Map.copyOf(plugins) : Map.of();
    Set<String> featureSet = Set.of();
    if (features instanceof List) {
      featureSet = ((List<?>) features).stream()
          .map(String::valueOf)
          .collect(Collectors.toUnmodifiableSet());
    } else if (features instanceof Set) {
      featureSet = ((Set<?>) features).stream()
          .map(String::valueOf)
          .collect(Collectors.toUnmodifiableSet());
    }
    return new Scope(pluginMap, featureSet);
  }

  private static String string(Map<String, Object> m, String key, String def) {
    Object v = m.get(key);
    return v != null ? v.toString() : def;
  }

  private static Optional<List<String>> list(Map<String, Object> m, String key) {
    Object v = m.get(key);
    if (v instanceof List) {
      List<String> out = ((List<?>) v).stream().map(String::valueOf).collect(Collectors.toList());
      return Optional.of(out);
    }
    return Optional.empty();
  }

  @SuppressWarnings("unchecked")
  private static Optional<Map<String, Object>> map(Map<String, Object> m, String key) {
    Object v = m.get(key);
    if (v instanceof Map) {
      return Optional.of((Map<String, Object>) v);
    }
    return Optional.empty();
  }

  private static NodeType parseNodeType(String s) {
    if (s == null) return NodeType.SEQUENCE;
    try {
      return NodeType.valueOf(s.toUpperCase());
    } catch (IllegalArgumentException e) {
      return NodeType.SEQUENCE;
    }
  }

  private static ExecutionMode parseExecutionMode(String s) {
    if (s == null) return null;
    try {
      return ExecutionMode.valueOf(s.toUpperCase());
    } catch (IllegalArgumentException e) {
      return null;
    }
  }

  private static VariableScope parseVariableScope(String s) {
    if (s == null) return VariableScope.INTERNAL;
    try {
      return VariableScope.valueOf(s.toUpperCase());
    } catch (IllegalArgumentException e) {
      return VariableScope.INTERNAL;
    }
  }
}
