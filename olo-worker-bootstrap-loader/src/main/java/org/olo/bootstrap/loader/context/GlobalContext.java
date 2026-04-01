/*
 * Copyright (c) 2026 Olo Labs. All rights reserved.
 */

package org.olo.bootstrap.loader.context;

import org.olo.configuration.Configuration;
import org.olo.configuration.snapshot.CompositeConfigurationSnapshot;
import org.olo.executiontree.CompiledPipeline;

import java.util.Map;

/**
 * Contract for the worker's global context: configuration view and compiled execution trees.
 * Implementation lives in this module; only this interface is exposed to limit control over internals.
 */
public interface GlobalContext {

  /** Global configuration view from the primary region. */
  Configuration getConfig();

  /** Configuration view for a specific tenant (global → region → tenant → resource). */
  Configuration getConfigForTenant(String tenantId);

  /** Current multi-region snapshot map (region → composite). */
  Map<String, CompositeConfigurationSnapshot> getSnapshotMap();

  /** Primary region composite (worker region), or null if using defaults-only. */
  CompositeConfigurationSnapshot getPrimaryComposite();

  /** Tenant → region map as currently resolved in-memory. */
  Map<String, String> getTenantToRegionMap();

  /**
   * Compiled pipeline by region + pipelineId + version. When version is null, returns latest (max version).
   */
  CompiledPipeline getCompiledPipeline(String region, String pipelineId, Long version);

  /**
   * Compiled pipeline for tenant + pipelineId + version, resolving tenant's region.
   * When version is null, returns latest. Falls back to primary region when tenant or region is unknown.
   */
  CompiledPipeline getCompiledPipelineForTenant(String tenantId, String pipelineId, Long version);

  /**
   * Rebuilds the execution tree cache for the given region from the composite snapshot.
   * Called when config is updated (bootstrap or refresh).
   */
  void rebuildTreeForRegion(CompositeConfigurationSnapshot composite);

  /**
   * Removes a region from the execution tree cache (e.g. when config refresh removes that region).
   */
  void removeTreeForRegion(String region);
}
