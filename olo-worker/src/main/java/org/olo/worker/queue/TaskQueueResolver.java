/*
 * Copyright (c) 2026 Olo Labs. All rights reserved.
 */

package org.olo.worker.queue;

import org.olo.configuration.ConfigurationProvider;
import org.olo.configuration.Regions;
import org.olo.configuration.snapshot.CompositeConfigurationSnapshot;
import org.olo.executiontree.CompiledPipeline;
import org.olo.executiontree.PipelineDefinition;
import org.olo.executiontree.tree.ExecutionTreeNode;
import org.olo.bootstrap.loader.context.GlobalContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Resolves task queues from configuration and global context.
 * Derives queue names from regions and pipelines, and determines allowed tenants per queue.
 */
public final class TaskQueueResolver {

    private static final Logger log = LoggerFactory.getLogger(TaskQueueResolver.class);

    private TaskQueueResolver() {}

    /**
     * Resolves task queues and their allowed tenants from the global context.
     *
     * @param globalContext the global context containing configuration and pipeline data
     * @return TaskQueueResolution containing queue names and tenant mappings
     */
    public static TaskQueueResolution resolveTaskQueues(GlobalContext globalContext) {
        List<String> taskQueues = new ArrayList<>();
        Map<String, List<String>> allowedTenantsByQueue = new LinkedHashMap<>();

        org.olo.configuration.Configuration config = globalContext.getConfig();
        Map<String, CompositeConfigurationSnapshot> snapshotMap = globalContext.getSnapshotMap();
        Map<String, String> tenantToRegion = globalContext.getTenantToRegionMap();

        List<String> regions = resolveRegions(config);

        for (String region : regions) {
            CompositeConfigurationSnapshot composite = snapshotMap != null ? snapshotMap.get(region) : null;
            if (composite == null) {
                continue;
            }
            
            globalContext.rebuildTreeForRegion(composite);
            Map<String, ?> pipelines = composite.getPipelines();
            if (pipelines == null) continue;

            for (String pipelineId : pipelines.keySet()) {
                // pipelineId is already in the form olo.<region>.<pipeline>
                String queueName = pipelineId;
                taskQueues.add(queueName);

                Set<String> effectiveTenants = resolveAllowedTenants(
                        region, pipelineId, tenantToRegion, globalContext
                );
                allowedTenantsByQueue.put(queueName, new ArrayList<>(effectiveTenants));
            }
        }

        if (taskQueues.isEmpty()) {
            log.warn("No task queues derived from config (no regions/pipelines). " +
                     "Add olo.region and ensure pipeline templates exist.");
            taskQueues.add("olo.default.default");
            allowedTenantsByQueue.put("olo.default.default", List.of("default"));
        }

        return new TaskQueueResolution(taskQueues, allowedTenantsByQueue);
    }

    private static List<String> resolveRegions(org.olo.configuration.Configuration config) {
        List<String> regions = ConfigurationProvider.getConfiguredRegions();
        if (regions.isEmpty()) {
            regions = new ArrayList<>(Regions.getRegions(config));
        }
        if (regions.isEmpty()) {
            regions.add(Regions.DEFAULT_REGION);
        }
        return regions;
    }

    private static Set<String> resolveAllowedTenants(
            String region, 
            String pipelineId, 
            Map<String, String> tenantToRegion,
            GlobalContext globalContext
    ) {
        CompiledPipeline compiled = globalContext.getCompiledPipeline(region, pipelineId, null);
        Set<String> effectiveTenants = new HashSet<>();

        if (tenantToRegion != null) {
            for (Map.Entry<String, String> e : tenantToRegion.entrySet()) {
                if (region.equals(e.getValue())) {
                    effectiveTenants.add(e.getKey());
                }
            }
        }

        if (compiled != null && compiled.getDefinition() != null) {
            PipelineDefinition def = compiled.getDefinition();
            ExecutionTreeNode root = def.getExecutionTree();
            if (root != null && root.getAllowedTenantIds() != null && !root.getAllowedTenantIds().isEmpty()) {
                effectiveTenants.retainAll(root.getAllowedTenantIds());
            }
        }

        if (effectiveTenants.isEmpty() && (tenantToRegion == null || tenantToRegion.isEmpty())) {
            effectiveTenants.add("default");
        }

        return effectiveTenants;
    }

    /**
     * Collects all unique tenant IDs that need consensus plugins registered.
     *
     * @param tenantToRegion mapping of tenant IDs to regions
     * @return set of tenant IDs for consensus plugin registration
     */
    public static Set<String> collectConsensusTenantIds(Map<String, String> tenantToRegion) {
        Set<String> consensusTenantIds = new HashSet<>();
        if (tenantToRegion != null) {
            consensusTenantIds.addAll(tenantToRegion.keySet());
        }
        consensusTenantIds.add("default");
        return consensusTenantIds;
    }

    /**
     * Holds the result of task queue resolution.
     */
    public static final class TaskQueueResolution {
        private final List<String> taskQueues;
        private final Map<String, List<String>> allowedTenantsByQueue;

        public TaskQueueResolution(List<String> taskQueues, Map<String, List<String>> allowedTenantsByQueue) {
            this.taskQueues = taskQueues;
            this.allowedTenantsByQueue = allowedTenantsByQueue;
        }

        public List<String> getTaskQueues() {
            return taskQueues;
        }

        public Map<String, List<String>> getAllowedTenantsByQueue() {
            return allowedTenantsByQueue;
        }
    }
}