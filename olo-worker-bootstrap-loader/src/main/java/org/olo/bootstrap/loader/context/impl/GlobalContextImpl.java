/*
 * Copyright (c) 2026 Olo Labs. All rights reserved.
 */

package org.olo.bootstrap.loader.context.impl;

import org.olo.bootstrap.loader.context.GlobalContext;
import org.olo.configuration.Configuration;
import org.olo.configuration.ConfigurationProvider;
import org.olo.configuration.region.TenantRegionResolver;
import org.olo.configuration.snapshot.CompositeConfigurationSnapshot;
import org.olo.executiontree.CompiledPipeline;

import java.util.Map;

/** Implementation of {@link GlobalContext}; delegates to configuration and execution tree registry. */
public final class GlobalContextImpl implements GlobalContext {

  @Override
  public Configuration getConfig() {
    return ConfigurationProvider.require();
  }

  @Override
  public Configuration getConfigForTenant(String tenantId) {
    return ConfigurationProvider.forTenant(tenantId);
  }

  @Override
  public Map<String, CompositeConfigurationSnapshot> getSnapshotMap() {
    return ConfigurationProvider.getSnapshotMap();
  }

  @Override
  public CompositeConfigurationSnapshot getPrimaryComposite() {
    return ConfigurationProvider.getComposite();
  }

  @Override
  public Map<String, String> getTenantToRegionMap() {
    return TenantRegionResolver.getTenantToRegionMap();
  }

  @Override
  public CompiledPipeline getCompiledPipeline(String region, String pipelineId, Long version) {
    return ExecutionTreeRegistry.get(region, pipelineId, version);
  }

  @Override
  public CompiledPipeline getCompiledPipelineForTenant(String tenantId, String pipelineId, Long version) {
    if (tenantId == null || tenantId.isBlank()) {
      CompositeConfigurationSnapshot primary = ConfigurationProvider.getComposite();
      if (primary == null) return null;
      return ExecutionTreeRegistry.get(primary.getRegion(), pipelineId, version);
    }
    String region = TenantRegionResolver.getRegion(tenantId);
    if (region == null || region.isBlank()) {
      CompositeConfigurationSnapshot primary = ConfigurationProvider.getComposite();
      region = primary != null ? primary.getRegion() : null;
    }
    if (region == null || region.isBlank()) return null;
    return ExecutionTreeRegistry.get(region, pipelineId, version);
  }

  @Override
  public void rebuildTreeForRegion(CompositeConfigurationSnapshot composite) {
    ExecutionTreeRegistry.rebuildForRegion(composite);
  }

  @Override
  public void removeTreeForRegion(String region) {
    ExecutionTreeRegistry.removeRegion(region);
  }
}
