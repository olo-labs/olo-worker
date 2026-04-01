/*
 * Copyright (c) 2026 Olo Labs. All rights reserved.
 */

package org.olo.worker;

import org.olo.bootstrap.loader.context.GlobalContext;
import org.olo.bootstrap.loader.context.GlobalContextProvider;
import org.olo.configuration.Bootstrap;
import org.olo.configuration.ConfigurationProvider;
import org.olo.configuration.Regions;
import org.olo.configuration.snapshot.CompositeConfigurationSnapshot;
import org.olo.configuration.snapshot.ConfigurationSnapshot;
import org.olo.config.OloSessionCache;
import org.olo.config.impl.InMemorySessionCache;
import org.olo.executiontree.CompiledPipeline;
import org.olo.executiontree.PipelineDefinition;
import org.olo.executiontree.tree.ExecutionTreeNode;
import org.olo.node.DynamicNodeBuilder;
import org.olo.node.NodeFeatureEnricher;
import org.olo.ledger.ExecutionEventSink;
import org.olo.ledger.RunLedger;
import org.olo.ledger.impl.NoOpLedgerStore;
import org.olo.ledger.impl.NoOpRunLedger;
import org.olo.plugin.PluginExecutorFactory;
import org.olo.worker.activity.impl.ExecuteNodeDynamicActivity;
import org.olo.worker.activity.impl.OloKernelActivitiesImpl;
import org.olo.worker.cache.CachePortRegistrar;
import org.olo.worker.db.DbPortRegistrar;
import org.olo.worker.workflow.impl.OloKernelWorkflowImpl;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowClientOptions;
import io.temporal.serviceclient.WorkflowServiceStubs;
import io.temporal.serviceclient.WorkflowServiceStubsOptions;
import io.temporal.worker.Worker;
import io.temporal.worker.WorkerFactory;
import io.temporal.worker.WorkerOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * OLO Temporal worker entry point. Uses the new configuration module: runs bootstrap
 * ({@link Bootstrap#run()}), derives task queues from global context (format {@code olo.<region>.<pipelineId>}),
 * and registers workers per queue. No dependency on legacy {@code OloBootstrap.initializeWorker()}.
 */
public final class OloWorkerApplication {

    private static final Logger log = LoggerFactory.getLogger(OloWorkerApplication.class);
    private static final int SHUTDOWN_TIMEOUT_SECONDS = 30;

    public static void main(String[] args) {
        DbPortRegistrar.registerDefaults();
        CachePortRegistrar.registerDefaults();
        Bootstrap.run();

        GlobalContext globalContext = GlobalContextProvider.getGlobalContext();
        org.olo.configuration.Configuration config = globalContext.getConfig();
        Map<String, CompositeConfigurationSnapshot> snapshotMap = globalContext.getSnapshotMap();
        Map<String, String> tenantToRegion = globalContext.getTenantToRegionMap();

        List<String> taskQueues = new ArrayList<>();
        Map<String, List<String>> allowedTenantsByQueue = new LinkedHashMap<>();

        List<String> regions = ConfigurationProvider.getConfiguredRegions();
        if (regions.isEmpty()) {
            regions = new ArrayList<>(Regions.getRegions(config));
        }
        if (regions.isEmpty()) {
            regions.add(Regions.DEFAULT_REGION);
        }

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
                CompiledPipeline compiled = globalContext.getCompiledPipeline(region, pipelineId, null);
                Set<String> effectiveTenants = new HashSet<>();
                if (tenantToRegion != null) {
                    for (Map.Entry<String, String> e : tenantToRegion.entrySet()) {
                        if (region.equals(e.getValue())) effectiveTenants.add(e.getKey());
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
                allowedTenantsByQueue.put(queueName, new ArrayList<>(effectiveTenants));
            }
        }

        if (taskQueues.isEmpty()) {
            log.warn("No task queues derived from config (no regions/pipelines). Add olo.regions and ensure pipeline templates exist.");
            taskQueues.add("olo.default.default");
            allowedTenantsByQueue.put("olo.default.default", List.of("default"));
        }

        RunLedger runLedger = new NoOpRunLedger(new NoOpLedgerStore());
        OloSessionCache sessionCache = new InMemorySessionCache();
        ExecutionEventSink executionEventSink = ExecutionEventSink.noOp();
        PluginExecutorFactory pluginExecutorFactory = createPluginExecutorFactory();
        var dynamicNodeBuilder = createDynamicNodeBuilder();
        var nodeFeatureEnricher = createNodeFeatureEnricher();

        String temporalTarget = envOrConfig("OLO_TEMPORAL_TARGET", config.get("olo.temporal.target", "localhost:47233"));
        String temporalNamespace = envOrConfig("OLO_TEMPORAL_NAMESPACE", config.get("olo.temporal.namespace", "default"));

        // Long polls can exceed ~60s; default RPC timeout is shorter and causes noisy DEADLINE_EXCEEDED + stack traces on idle queues.
        WorkflowServiceStubs service = WorkflowServiceStubs.newServiceStubs(
                WorkflowServiceStubsOptions.newBuilder()
                        .setTarget(temporalTarget)
                        .setRpcTimeout(Duration.ofSeconds(120))
                        .build()
        );
        WorkflowClient client = WorkflowClient.newInstance(
                service,
                WorkflowClientOptions.newBuilder().setNamespace(temporalNamespace).build()
        );
        WorkerFactory factory = WorkerFactory.newInstance(client);

        WorkerOptions workerOptions = WorkerOptions.newBuilder()
                .setMaxConcurrentActivityExecutionSize(10)
                .setMaxConcurrentWorkflowTaskExecutionSize(10)
                .build();

        for (String taskQueue : taskQueues) {
            List<String> tenantIds = allowedTenantsByQueue.getOrDefault(taskQueue, List.of("default"));
            OloKernelActivitiesImpl activities = new OloKernelActivitiesImpl(
                    sessionCache, tenantIds, runLedger, executionEventSink,
                    pluginExecutorFactory, dynamicNodeBuilder, nodeFeatureEnricher
            );
            ExecuteNodeDynamicActivity executeNodeDynamicActivity = new ExecuteNodeDynamicActivity(activities);
            Worker worker = factory.newWorker(taskQueue, workerOptions);
            worker.registerWorkflowImplementationTypes(OloKernelWorkflowImpl.class);
            worker.registerActivitiesImplementations(activities, executeNodeDynamicActivity);
            log.info("Registered worker for task queue: {}", taskQueue);
        }

        log.info("Task queues registered: {}", taskQueues);
        log.info("Starting worker | Temporal: {} | namespace: {} | Redis: {} | DB: {}",
                temporalTarget, temporalNamespace,
                config.get("olo.redis.host", "") + ":" + config.get("olo.redis.port", "6379"),
                config.get("olo.db.host", "") + ":" + config.get("olo.db.port", "5432"));

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("Shutting down worker...");
            Bootstrap.stopRefreshScheduler();
            Bootstrap.stopTenantRegionRefreshScheduler();
            factory.shutdown();
            try {
                factory.awaitTermination(SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            } catch (Exception e) {
                log.error("Error during worker shutdown: {}", e.getMessage());
            }
        }));

        ConfigurationProvider.addSnapshotChangeListener((region, composite) -> {
            if (composite != null) globalContext.rebuildTreeForRegion(composite);
            else globalContext.removeTreeForRegion(region);
        });

        factory.start();

        try {
            Thread.currentThread().join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.info("Interrupted, shutting down worker...");
            Bootstrap.stopRefreshScheduler();
            Bootstrap.stopTenantRegionRefreshScheduler();
            factory.shutdown();
            try {
                factory.awaitTermination(SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            } catch (Exception ex) {
                log.error("Error during worker shutdown: {}", ex.getMessage());
            }
        }
    }

    private static String envOrConfig(String envKey, String configDefault) {
        String v = System.getenv(envKey);
        if (v != null && !v.isBlank()) return v.trim();
        return configDefault != null ? configDefault : "";
    }

    private static PluginExecutorFactory createPluginExecutorFactory() {
        try {
            Class<?> clazz = Class.forName("org.olo.plugin.impl.DefaultPluginExecutorFactory");
            return (PluginExecutorFactory) clazz.getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            log.warn("DefaultPluginExecutorFactory not on classpath; using no-op plugin factory: {}", e.getMessage());
            return (tenantId, cache) -> new org.olo.plugin.PluginExecutor() {
                @Override public String execute(String pluginId, String inputsJson, String nodeId) { return "{}"; }
                @Override public String toJson(java.util.Map<String, Object> map) { return "{}"; }
                @Override public java.util.Map<String, Object> fromJson(String json) { return java.util.Map.of(); }
            };
        }
    }

    private static org.olo.node.DynamicNodeBuilder createDynamicNodeBuilder() {
        return (spec, context) -> {
            throw new UnsupportedOperationException("DynamicNodeBuilder not wired; add olo-worker-execution-tree or bootstrap implementation.");
        };
    }

    private static org.olo.node.NodeFeatureEnricher createNodeFeatureEnricher() {
        return (node, context) -> node;
    }
}
