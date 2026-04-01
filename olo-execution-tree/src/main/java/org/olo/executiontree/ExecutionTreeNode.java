/*
 * Copyright (c) 2026 Olo Labs. All rights reserved.
 */

package org.olo.executiontree;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Runtime representation of a single node in the Execution Tree. Immutable for
 * replay safety; the compiled snapshot must not be mutated at runtime.
 *
 * <p>Core: id, type, name, version, params, children. Data flow: inputMappings, outputMappings.
 * Execution: timeout, retryPolicy, executionMode. Features: pre/post lists, features. Observability: metadata.
 *
 * @see NodeType
 */
@JsonDeserialize(builder = ExecutionTreeNode.Builder.class)
public final class ExecutionTreeNode {

  private final String id;
  private final NodeType type;
  private final String name;
  private final String version;
  private final Map<String, Object> params;
  private final List<ExecutionTreeNode> children;
  private final Map<String, Object> inputMappings;
  private final Map<String, Object> outputMappings;
  private final Map<String, Object> timeout;
  private final Map<String, Object> retryPolicy;
  private final ExecutionMode executionMode;
  private final Map<String, Object> metadata;
  private final List<String> preExecution;
  private final List<String> postSuccessExecution;
  private final List<String> postErrorExecution;
  private final List<String> finallyExecution;
  private final List<String> features;
  private final Map<String, String> connections;
  /** When set (e.g. on root node), restricts which tenant IDs may run this pipeline. Empty = all tenants in region. */
  private final List<String> allowedTenantIds;

  private ExecutionTreeNode(Builder b) {
    this.id = b.id;
    this.type = b.type;
    this.name = b.name;
    this.version = b.version;
    this.params = b.params == null ? Map.of() : Map.copyOf(b.params);
    this.children = b.children == null ? List.of() : List.copyOf(b.children);
    this.inputMappings = b.inputMappings == null ? Map.of() : Map.copyOf(b.inputMappings);
    this.outputMappings = b.outputMappings == null ? Map.of() : Map.copyOf(b.outputMappings);
    this.timeout = b.timeout == null ? Map.of() : Map.copyOf(b.timeout);
    this.retryPolicy = b.retryPolicy == null ? Map.of() : Map.copyOf(b.retryPolicy);
    this.executionMode = b.executionMode;
    this.metadata = b.metadata == null ? Map.of() : Map.copyOf(b.metadata);
    this.preExecution = b.preExecution == null ? List.of() : List.copyOf(b.preExecution);
    this.postSuccessExecution = b.postSuccessExecution == null ? List.of() : List.copyOf(b.postSuccessExecution);
    this.postErrorExecution = b.postErrorExecution == null ? List.of() : List.copyOf(b.postErrorExecution);
    this.finallyExecution = b.finallyExecution == null ? List.of() : List.copyOf(b.finallyExecution);
    this.features = b.features == null ? List.of() : List.copyOf(b.features);
    this.connections = b.connections == null ? Map.of() : Map.copyOf(b.connections);
    this.allowedTenantIds = b.allowedTenantIds == null ? List.of() : List.copyOf(b.allowedTenantIds);
  }

  public String getId() { return id; }
  public NodeType getType() { return type; }
  /** Human-readable name for observability and debugging. */
  public String getName() { return name; }
  /** Node/pipeline version for tracing and compatibility. */
  public String getVersion() { return version; }
  public Map<String, Object> getParams() { return params; }
  public List<ExecutionTreeNode> getChildren() { return children; }
  /** Variable/parameter input mappings (e.g. variable name -> plugin parameter or structured list). */
  public Map<String, Object> getInputMappings() { return inputMappings; }
  /** Output mappings (e.g. plugin output -> variable name). */
  public Map<String, Object> getOutputMappings() { return outputMappings; }
  /** Timeout config (e.g. scheduleToStartSeconds, startToCloseSeconds, scheduleToCloseSeconds). */
  public Map<String, Object> getTimeout() { return timeout; }
  /** Retry policy (e.g. maxAttempts, backoffCoefficient). */
  public Map<String, Object> getRetryPolicy() { return retryPolicy; }
  public ExecutionMode getExecutionMode() { return executionMode; }
  /** Observability: owner, description, tags, etc. */
  public Map<String, Object> getMetadata() { return metadata; }
  public List<String> getPreExecution() { return preExecution; }
  public List<String> getPostSuccessExecution() { return postSuccessExecution; }
  public List<String> getPostErrorExecution() { return postErrorExecution; }
  public List<String> getFinallyExecution() { return finallyExecution; }
  public List<String> getFeatures() { return features; }
  /** Logical connection names by role (e.g. "model" -> "gpt4-prod"). Resolved at execution time. */
  public Map<String, String> getConnections() { return connections; }
  /** Tenant IDs allowed to run this (node/pipeline). Empty = no restriction. Typically set on root only. */
  public List<String> getAllowedTenantIds() { return allowedTenantIds; }

  public static Builder builder(String id, NodeType type) {
    return new Builder(id, type);
  }

  /**
   * Returns a deep copy of the tree rooted at {@code root}.
   * Used when {@code isDynamicPipeline} is true so the run has its own mutable copy of the execution tree.
   */
  public static ExecutionTreeNode deepCopy(ExecutionTreeNode root) {
    if (root == null) return null;
    List<ExecutionTreeNode> childCopies = new ArrayList<>(root.getChildren().size());
    for (ExecutionTreeNode child : root.getChildren()) {
      childCopies.add(deepCopy(child));
    }
    return builder(root.getId(), root.getType())
        .name(root.getName())
        .version(root.getVersion())
        .params(root.getParams())
        .children(childCopies)
        .inputMappings(root.getInputMappings())
        .outputMappings(root.getOutputMappings())
        .timeout(root.getTimeout())
        .retryPolicy(root.getRetryPolicy())
        .executionMode(root.getExecutionMode())
        .metadata(root.getMetadata())
        .preExecution(root.getPreExecution())
        .postSuccessExecution(root.getPostSuccessExecution())
        .postErrorExecution(root.getPostErrorExecution())
        .finallyExecution(root.getFinallyExecution())
        .features(root.getFeatures())
        .connections(root.getConnections())
        .allowedTenantIds(root.getAllowedTenantIds())
        .build();
  }

  /** Converts Map or List of {pluginParameter, variable} to Map for inputMappings/outputMappings. */
  @SuppressWarnings("unchecked")
  private static Map<String, Object> toInputOutputMap(Object o) {
    if (o == null) return new LinkedHashMap<>();
    if (o instanceof Map) return new LinkedHashMap<>((Map<String, Object>) o);
    if (o instanceof List) {
      Map<String, Object> out = new LinkedHashMap<>();
      for (Object item : (List<?>) o) {
        if (!(item instanceof Map)) continue;
        Map<String, ?> m = (Map<String, ?>) item;
        Object param = m.get("pluginParameter");
        Object variable = m.get("variable");
        if (param != null) out.put(param.toString(), variable != null ? variable.toString() : "");
      }
      return out;
    }
    return new LinkedHashMap<>();
  }

  @JsonPOJOBuilder(buildMethodName = "build", withPrefix = "")
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static final class Builder {
    private final String id;
    private final NodeType type;
    private String name;
    private String version;
    private Map<String, Object> params;
    private List<ExecutionTreeNode> children;
    private Map<String, Object> inputMappings;
    private Map<String, Object> outputMappings;
    private Map<String, Object> timeout;
    private Map<String, Object> retryPolicy;
    private ExecutionMode executionMode;
    private Map<String, Object> metadata;
    private List<String> preExecution;
    private List<String> postSuccessExecution;
    private List<String> postErrorExecution;
    private List<String> finallyExecution;
    private List<String> features;
    private Map<String, String> connections;
    private List<String> allowedTenantIds;

    @JsonCreator
    public Builder(@JsonProperty("id") String id, @JsonProperty("type") NodeType type) {
      this.id = id;
      this.type = type;
    }

    public Builder name(String name) { this.name = name; return this; }
    /** Alias for name; accepts "displayName" from protocol tree JSON. */
    public Builder displayName(String displayName) { this.name = displayName; return this; }
    public Builder version(String version) { this.version = version; return this; }
    public Builder params(Map<String, Object> params) { this.params = params; return this; }
    public Builder children(List<ExecutionTreeNode> children) {
      this.children = children;
      return this;
    }
    /** Accepts Map (compiler format) or List of {pluginParameter, variable} (protocol format). */
    public Builder inputMappings(Object inputMappings) { this.inputMappings = toInputOutputMap(inputMappings); return this; }
    /** Accepts Map (compiler format) or List of {pluginParameter, variable} (protocol format). */
    public Builder outputMappings(Object outputMappings) { this.outputMappings = toInputOutputMap(outputMappings); return this; }
    public Builder timeout(Map<String, Object> timeout) { this.timeout = timeout; return this; }
    public Builder retryPolicy(Map<String, Object> retryPolicy) { this.retryPolicy = retryPolicy; return this; }
    public Builder metadata(Map<String, Object> metadata) { this.metadata = metadata; return this; }
    public Builder preExecution(List<String> preExecution) {
      this.preExecution = preExecution;
      return this;
    }
    public Builder postSuccessExecution(List<String> postSuccessExecution) {
      this.postSuccessExecution = postSuccessExecution;
      return this;
    }
    public Builder postErrorExecution(List<String> postErrorExecution) {
      this.postErrorExecution = postErrorExecution;
      return this;
    }
    public Builder finallyExecution(List<String> finallyExecution) {
      this.finallyExecution = finallyExecution;
      return this;
    }
    public Builder features(List<String> features) {
      this.features = features;
      return this;
    }
    public Builder executionMode(ExecutionMode executionMode) {
      this.executionMode = executionMode;
      return this;
    }
    public Builder connections(Map<String, String> connections) {
      this.connections = connections;
      return this;
    }
    public Builder allowedTenantIds(List<String> allowedTenantIds) {
      this.allowedTenantIds = allowedTenantIds;
      return this;
    }
    public ExecutionTreeNode build() {
      return new ExecutionTreeNode(this);
    }
  }
}
