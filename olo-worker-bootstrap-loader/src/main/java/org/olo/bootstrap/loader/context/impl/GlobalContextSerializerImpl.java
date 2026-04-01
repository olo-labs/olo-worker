/*
 * Copyright (c) 2026 Olo Labs. All rights reserved.
 */

package org.olo.bootstrap.loader.context.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.olo.bootstrap.loader.context.GlobalContext;
import org.olo.bootstrap.loader.context.GlobalContextSerializer;
import org.olo.configuration.ConfigurationProvider;
import org.olo.configuration.Regions;
import org.olo.configuration.snapshot.CompositeConfigurationSnapshot;
import org.olo.configuration.snapshot.ConfigurationSnapshot;
import org.olo.executiontree.CompiledPipeline;
import org.olo.executiontree.ExecutionTreeCompiler;
import org.olo.executiontree.PipelineDefinition;
import org.olo.executiontree.VariableDeclaration;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** Implementation of {@link GlobalContextSerializer}; uses {@link GlobalContext} for data. */
public final class GlobalContextSerializerImpl implements GlobalContextSerializer {

  private static final ObjectMapper JSON_MAPPER = new ObjectMapper()
      .registerModule(new JavaTimeModule())
      .enable(SerializationFeature.INDENT_OUTPUT);

  private final GlobalContext context;

  public GlobalContextSerializerImpl(GlobalContext context) {
    this.context = context;
  }

  @Override
  public Map<String, Object> fullDump() {
    Map<String, Object> root = new LinkedHashMap<>();
    root.put("globalConfig", context.getConfig().asMap());
    if (context.getPrimaryComposite() != null) {
      root.put("primaryRegion", context.getPrimaryComposite().getRegion());
    }
    List<String> served = ConfigurationProvider.getConfiguredRegions();
    root.put("servedRegions", served.isEmpty() ? Regions.getRegions(context.getConfig()) : served);

    // Capabilities views for debugging/inspection.
    root.put("globalCapabilities", capabilitiesGlobal());
    root.put("regionCapabilities", capabilitiesByRegion());
    root.put("tenantCapabilities", capabilitiesByTenant());

    root.put("tenantToRegion", context.getTenantToRegionMap());
    root.put("snapshotsByRegion", snapshotsByRegion());
    root.put("globalContextTree", globalContextTree());
    return root;
  }

  @Override
  public String toJson(Map<String, Object> payload) throws JsonProcessingException {
    return JSON_MAPPER.writeValueAsString(payload);
  }

  private Map<String, Object> snapshotsByRegion() {
    Map<String, CompositeConfigurationSnapshot> snapshotMap = context.getSnapshotMap();
    if (snapshotMap == null) return Map.of();
    Map<String, Object> out = new LinkedHashMap<>();
    for (Map.Entry<String, CompositeConfigurationSnapshot> e : snapshotMap.entrySet()) {
      if (e.getValue() != null) {
        out.put(e.getKey(), snapshotSummary(e.getValue()));
      }
    }
    return out;
  }

  /** Dump matches cache shape: region → pipelineId → compiled pipeline entry. */
  private Map<String, Object> globalContextTree() {
    Map<String, Object> byRegion = new LinkedHashMap<>();
    Map<String, CompositeConfigurationSnapshot> snapshotMap = context.getSnapshotMap();
    if (snapshotMap == null) return byRegion;

    // Ensure execution trees are built for all regions before inspection.
    for (CompositeConfigurationSnapshot composite : snapshotMap.values()) {
      if (composite != null) {
        context.rebuildTreeForRegion(composite);
      }
    }

    for (Map.Entry<String, CompositeConfigurationSnapshot> e : snapshotMap.entrySet()) {
      String region = e.getKey();
      CompositeConfigurationSnapshot composite = e.getValue();
      if (composite == null) continue;

      Set<String> pipelineIds = composite.getPipelines().keySet();
      Map<String, Object> pipelines = new LinkedHashMap<>();
      for (String pipelineId : pipelineIds) {
        CompiledPipeline compiled = context.getCompiledPipeline(region, pipelineId, null);
        if (compiled == null) {
          pipelines.put(pipelineId, Map.of("present", false));
          continue;
        }
        pipelines.put(pipelineId, compiledPipelineEntry(compiled));
      }
      byRegion.put(region, pipelines);
    }
    return byRegion;
  }

  private static Map<String, Object> compiledPipelineEntry(CompiledPipeline compiled) {
    Map<String, Object> meta = new LinkedHashMap<>();
    meta.put("pipelineId", compiled.getPipelineId());
    meta.put("version", compiled.getVersion());
    meta.put("checksum", compiled.getChecksum());
    PipelineDefinition def = compiled.getDefinition();
    if (def != null) {
      meta.put("compiledPipeline", compiledPipelineDefinitionToMap(def));
    }
    return meta;
  }

  private static Map<String, Object> compiledPipelineDefinitionToMap(PipelineDefinition def) {
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("id", def.getName());
    out.put("inputContract", def.getInputContractMap() != null ? def.getInputContractMap() : Map.<String, Object>of());
    out.put("outputContract", def.getOutputContract() != null ? def.getOutputContract() : Map.<String, Object>of());
    out.put("resultMapping", def.getResultMappingMap() != null ? def.getResultMappingMap() : Map.<String, String>of());
    out.put("executionType", def.getExecutionType() != null ? def.getExecutionType().name() : "SYNC");
    out.put("variableRegistry", variableRegistryToJsonSafe(def.getVariableRegistryRaw()));
    out.put("scope", scopeToJsonSafe(def.getScope()));
    out.put("executionTree", def.getExecutionTreeRoot() != null ? ExecutionTreeCompiler.toNodeMap(def.getExecutionTreeRoot()) : Map.<String, Object>of());
    out.put("isDebugPipeline", def.isDebugPipeline());
    out.put("isDynamicPipeline", def.isDynamicPipeline());
    return out;
  }

  private static List<Map<String, Object>> variableRegistryToJsonSafe(org.olo.executiontree.VariableRegistry registry) {
    if (registry == null) return List.of();
    List<VariableDeclaration> decls = registry.getDeclarations();
    if (decls == null || decls.isEmpty()) return List.of();
    List<Map<String, Object>> out = new ArrayList<>();
    for (VariableDeclaration d : decls) {
      Map<String, Object> m = new LinkedHashMap<>();
      m.put("name", d.getName());
      m.put("type", d.getType());
      m.put("scope", d.getScope() != null ? d.getScope().name() : "INTERNAL");
      out.add(m);
    }
    return out;
  }

  private static Map<String, Object> scopeToJsonSafe(org.olo.executiontree.Scope scope) {
    Map<String, Object> out = new LinkedHashMap<>();
    if (scope == null) {
      out.put("plugins", Map.of());
      out.put("features", List.of());
      return out;
    }
    out.put("plugins", scope.getPlugins() != null ? scope.getPlugins() : Map.of());
    out.put("features", scope.getFeatures() != null ? scope.getFeatures().stream().map(f -> f != null ? f.getId() : null).filter(java.util.Objects::nonNull).collect(Collectors.toList()) : List.of());
    return out;
  }

  private static Map<String, Object> snapshotSummary(CompositeConfigurationSnapshot composite) {
    Map<String, Object> summary = new LinkedHashMap<>();
    summary.put("region", composite.getRegion());
    summary.put("snapshotId", composite.getSnapshotId());
    summary.put("coreVersion", composite.getCoreVersion());
    summary.put("pipelinesVersion", composite.getPipelinesVersion());
    summary.put("connectionsVersion", composite.getConnectionsVersion());
    summary.put("regionalSettingsVersion", composite.getRegionalSettingsVersion());
    ConfigurationSnapshot core = composite.getCore();
    if (core != null) {
      summary.put("coreLastUpdated", core.getLastUpdated() != null ? core.getLastUpdated().toString() : null);
      summary.put("coreGlobalConfigKeys", core.getGlobalConfig().keySet().stream().sorted().collect(Collectors.toList()));
      summary.put("coreRegionConfigKeys", core.getRegionConfig().keySet().stream().sorted().collect(Collectors.toList()));
      summary.put("coreTenantIds", core.getTenantConfig().isEmpty() ? List.<String>of() : core.getTenantConfig().keySet().stream().sorted().collect(Collectors.toList()));
      summary.put("coreResourceIds", core.getResourceConfig().isEmpty() ? List.<String>of() : core.getResourceConfig().keySet().stream().sorted().collect(Collectors.toList()));
    }
    Set<String> pipelineIds = composite.getPipelines().keySet();
    summary.put("pipelineIds", pipelineIds.isEmpty() ? List.<String>of() : pipelineIds.stream().sorted().collect(Collectors.toList()));
    Set<String> connectionIds = composite.getConnections().keySet();
    summary.put("connectionIds", connectionIds.isEmpty() ? List.<String>of() : connectionIds.stream().sorted().collect(Collectors.toList()));
    return summary;
  }

  /**
   * Global capabilities derived from configuration (non-region / non-tenant scoped).
   * Currently a placeholder: callers can extend this to inspect config keys such as
   * feature flags or global plugin enablement.
   */
  private Map<String, Object> capabilitiesGlobal() {
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("plugins", List.of());
    out.put("features", List.of());
    return out;
  }

  /**
   * Region-level capabilities. Currently infers enabled plugins/features from the
   * compiled pipelines per region.
   */
  private Map<String, Object> capabilitiesByRegion() {
    Map<String, Object> byRegion = new LinkedHashMap<>();
    Map<String, CompositeConfigurationSnapshot> snapshotMap = context.getSnapshotMap();
    if (snapshotMap == null) return byRegion;
    for (Map.Entry<String, CompositeConfigurationSnapshot> e : snapshotMap.entrySet()) {
      String region = e.getKey();
      CompositeConfigurationSnapshot composite = e.getValue();
      if (composite == null) continue;
      Set<String> pipelineIds = composite.getPipelines().keySet();
      List<String> plugins = new ArrayList<>();
      List<String> features = new ArrayList<>();
      for (String pipelineId : pipelineIds) {
        CompiledPipeline compiled = context.getCompiledPipeline(region, pipelineId, null);
        if (compiled == null || compiled.getDefinition() == null || compiled.getDefinition().getScope() == null) {
          continue;
        }
        var scope = compiled.getDefinition().getScope();
        if (scope.getPlugins() != null) {
          plugins.addAll(scope.getPlugins().keySet());
        }
        if (scope.getFeatures() != null) {
          scope.getFeatures().stream().map(f -> f != null ? f.getId() : null).filter(java.util.Objects::nonNull).forEach(features::add);
        }
      }
      Map<String, Object> caps = new LinkedHashMap<>();
      caps.put("plugins", plugins.stream().distinct().sorted().toList());
      caps.put("features", features.stream().distinct().sorted().toList());
      byRegion.put(region, caps);
    }
    return byRegion;
  }

  /**
   * Tenant-level capabilities. Uses the tenant→region map and the region capabilities;
   * tenants in the same region share capabilities in this view.
   */
  private Map<String, Object> capabilitiesByTenant() {
    Map<String, Object> byTenant = new LinkedHashMap<>();
    Map<String, String> tenantToRegion = context.getTenantToRegionMap();
    if (tenantToRegion == null || tenantToRegion.isEmpty()) return byTenant;

    Map<String, Object> regionCaps = capabilitiesByRegion();
    for (Map.Entry<String, String> e : tenantToRegion.entrySet()) {
      String tenantId = e.getKey();
      String region = e.getValue();
      Object caps = regionCaps.getOrDefault(region, Map.of("plugins", List.of(), "features", List.of()));
      byTenant.put(tenantId, caps);
    }
    return byTenant;
  }
}
