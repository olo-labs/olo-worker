/*
 * Copyright (c) 2026 Olo Labs. All rights reserved.
 */

package org.olo.executiontree;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.olo.configuration.chat.PipelineChatProfilesSection;
import org.olo.executiontree.config.ExecutionType;
import org.olo.executiontree.inputcontract.InputContract;
import org.olo.executiontree.inputcontract.InputContractImpl;
import org.olo.executiontree.outputcontract.ResultMapping;
import org.olo.executiontree.outputcontract.ResultMappingImpl;
import org.olo.executiontree.tree.CompilerNodeAdapter;
import org.olo.executiontree.variableregistry.VariableRegistryEntry;
import org.olo.executiontree.variableregistry.VariableRegistryEntryAdapter;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Pipeline definition: name, input/output contract, variable registry, scope,
 * and the execution tree root. Immutable once built; used to produce ExecutionConfigSnapshot.
 * Implements {@link org.olo.executiontree.config.PipelineDefinition} for worker use.
 *
 * @see ExecutionTreeNode
 * @see VariableRegistry
 * @see Scope
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public final class PipelineDefinition implements org.olo.executiontree.config.PipelineDefinition {
  private final String name;
  private final Map<String, Object> inputContract;
  @JsonProperty("variableRegistryRaw")
  private final VariableRegistry variableRegistry;
  private final Scope scope;
  private final ExecutionTreeNode executionTree;
  private final Map<String, Object> outputContract;
  private final Map<String, String> resultMapping;
  private final String executionType;
  private final boolean isDebugPipeline;
  private final boolean isDynamicPipeline;
  /** Optional UI chat profiles (queue/pipeline presets); deserialized from pipeline JSON only. */
  private final PipelineChatProfilesSection chatProfiles;

  /** Converts Map or List of result mappings (e.g. {variable}) to Map<String, String>. */
  @SuppressWarnings("unchecked")
  private static Map<String, String> toResultMappingMap(Object o) {
    if (o == null) return Map.of();
    if (o instanceof Map) {
      Map<String, ?> m = (Map<String, ?>) o;
      Map<String, String> out = new LinkedHashMap<>();
      m.forEach((k, v) -> out.put(k, v != null ? v.toString() : ""));
      return out;
    }
    if (o instanceof List) {
      Map<String, String> out = new LinkedHashMap<>();
      for (Object item : (List<?>) o) {
        if (item instanceof Map) {
          Map<String, ?> m = (Map<String, ?>) item;
          Object var = m.get("variable");
          if (var != null) out.put(var.toString(), "");
        }
      }
      return out;
    }
    return Map.of();
  }

  @JsonCreator
  public PipelineDefinition(
      @JsonProperty("name") String name,
      @JsonProperty("inputContractMap") Map<String, Object> inputContract,
      @JsonProperty("variableRegistryRaw") VariableRegistry variableRegistry,
      @JsonProperty("scope") Scope scope,
      @JsonProperty("executionTreeRoot") ExecutionTreeNode executionTree,
      @JsonProperty("outputContract") Map<String, Object> outputContract,
      @JsonProperty("resultMappingMap") Object resultMappingMap,
      @JsonProperty("resultMapping") Object resultMappingList,
      @JsonProperty("executionType") String executionType,
      @JsonProperty("debugPipeline") boolean isDebugPipeline,
      @JsonProperty("dynamicPipeline") boolean isDynamicPipeline,
      @JsonProperty("chatProfiles") PipelineChatProfilesSection chatProfiles) {
    this.name = name;
    this.inputContract = inputContract == null ? Map.of() : Map.copyOf(inputContract);
    this.variableRegistry = variableRegistry;
    this.scope = scope;
    this.executionTree = executionTree;
    this.outputContract = outputContract == null ? Map.of() : Map.copyOf(outputContract);
    this.resultMapping = toResultMappingMap(resultMappingMap != null ? resultMappingMap : resultMappingList);
    this.executionType = executionType != null ? executionType : "SYNC";
    this.isDebugPipeline = isDebugPipeline;
    this.isDynamicPipeline = isDynamicPipeline;
    this.chatProfiles = chatProfiles;
  }

  public String getName() { return name; }
  @Override
  public InputContract getInputContract() { return new InputContractImpl(inputContract, false); }
  public Map<String, Object> getInputContractMap() { return inputContract; }
  @Override
  @JsonIgnore
  public List<VariableRegistryEntry> getVariableRegistry() {
    return variableRegistry != null && variableRegistry.getDeclarations() != null
        ? variableRegistry.getDeclarations().stream().map(VariableRegistryEntryAdapter::new).collect(Collectors.toList())
        : List.of();
  }
  public VariableRegistry getVariableRegistryRaw() { return variableRegistry; }
  public Scope getScope() { return scope; }
  /** Returns the execution tree as protocol/worker type. */
  @Override
  public org.olo.executiontree.tree.ExecutionTreeNode getExecutionTree() {
    return executionTree != null ? new CompilerNodeAdapter(executionTree) : null;
  }
  /** Returns the raw compiler execution tree (for serialization). */
  public ExecutionTreeNode getExecutionTreeRoot() {
    return executionTree;
  }
  public Map<String, Object> getOutputContract() { return outputContract; }
  @Override
  public List<ResultMapping> getResultMapping() {
    return resultMapping != null
        ? resultMapping.entrySet().stream().map(e -> new ResultMappingImpl(e.getKey())).collect(Collectors.toList())
        : List.of();
  }
  public Map<String, String> getResultMappingMap() { return resultMapping; }
  @Override
  public ExecutionType getExecutionType() {
    try {
      return ExecutionType.valueOf(executionType != null ? executionType.toUpperCase() : "SYNC");
    } catch (Exception e) {
      return ExecutionType.SYNC;
    }
  }

  /** Whether this pipeline is marked as a debug pipeline in its config. */
  public boolean isDebugPipeline() {
    return isDebugPipeline;
  }

  /** Whether this pipeline is marked as a dynamic pipeline in its config. */
  public boolean isDynamicPipeline() {
    return isDynamicPipeline;
  }

  /** Regional chat UI profiles from pipeline JSON; absent unless {@code chatProfiles} was set in the definition. */
  public PipelineChatProfilesSection getChatProfiles() {
    return chatProfiles;
  }
}
