/*
 * Copyright (c) 2026 Olo Labs. All rights reserved.
 */

package org.olo.bootstrap.loader.context;

import org.olo.executiontree.CompiledPipeline;
import org.olo.executiontree.config.PipelineConfiguration;
import org.olo.executiontree.config.PipelineDefinition;

import java.util.Map;

/**
 * Resolves pipeline configuration for a tenant/queue. Backed by GlobalContext.
 */
public final class LocalContext {

    private static Long parseVersion(String requestedVersion) {
        if (requestedVersion == null || requestedVersion.isBlank()) return null;
        try {
            return Long.parseLong(requestedVersion.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private final PipelineConfiguration pipelineConfiguration;

    private LocalContext(PipelineConfiguration pipelineConfiguration) {
        this.pipelineConfiguration = pipelineConfiguration;
    }

    public PipelineConfiguration getPipelineConfiguration() {
        return pipelineConfiguration;
    }

    /**
     * Resolves context for the given tenant and queue.
     * Queue format: olo.region.pipeline with optional .tenant and .mode.
     * requestedVersion: when null or blank, latest (max version) is used.
     */
    public static LocalContext forQueue(String tenantId, String effectiveQueue, String requestedVersion) {
        if (effectiveQueue == null || effectiveQueue.isBlank()) return null;
        String[] parts = effectiveQueue.split("\\.", 3);
        if (parts.length < 3 || !"olo".equals(parts[0])) return null;
        String region = parts[1];
        String suffixAfterRegion = parts[2];
        Long version = parseVersion(requestedVersion);
        GlobalContext global = GlobalContextProvider.getGlobalContext();
        if (global == null) return null;
        CompiledPipeline compiled = global.getCompiledPipeline(region, effectiveQueue, version);
        if (compiled == null) {
            compiled = global.getCompiledPipeline(region, suffixAfterRegion, version);
        }
        if (compiled == null && suffixAfterRegion.contains(".")) {
            String rest = suffixAfterRegion;
            do {
                rest = rest.substring(0, rest.lastIndexOf('.'));
                compiled = global.getCompiledPipeline(region, rest, version);
            } while (compiled == null && rest.contains("."));
        }
        if (compiled == null || compiled.getDefinition() == null) return null;
        PipelineDefinition def = compiled.getDefinition();
        String keyForConfig = compiled.getPipelineId();
        PipelineConfiguration config = new PipelineConfiguration();
        config.setPipelines(Map.of(keyForConfig, def));
        return new LocalContext(config);
    }
}
