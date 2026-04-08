/*
 * Copyright (c) 2026 Olo Labs. All rights reserved.
 */

package org.olo.worker;

import io.temporal.worker.WorkerFactory;
import org.olo.bootstrap.loader.context.GlobalContext;
import org.olo.bootstrap.loader.context.GlobalContextProvider;
import org.olo.configuration.Bootstrap;
import org.olo.configuration.ConfigurationProvider;
import org.olo.configuration.snapshot.CompositeConfigurationSnapshot;
import org.olo.worker.cache.CachePortRegistrar;
import org.olo.worker.config.TemporalConfig;
import org.olo.worker.db.DbPortRegistrar;
import org.olo.worker.factory.WorkerComponentFactory;
import org.olo.worker.plugins.ConsensusPluginRegistrar;
import org.olo.worker.queue.TaskQueueResolver;
import org.olo.worker.queue.TaskQueueResolver.TaskQueueResolution;
import org.olo.worker.registry.WorkerRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Set;

/**
 * OLO Temporal worker entry point. Uses the new configuration module: runs bootstrap
 * ({@link Bootstrap#run()}), derives task queues from global context (format {@code olo.<region>.<pipelineId>}),
 * and registers workers per queue. No dependency on legacy {@code OloBootstrap.initializeWorker()}.
 * 
 * <p>This class now follows the single responsibility principle by delegating to:
 * <ul>
 *   <li>{@link TemporalConfig} - Temporal connection management</li>
 *   <li>{@link TaskQueueResolver} - Task queue derivation from configuration</li>
 *   <li>{@link ConsensusPluginRegistrar} - Consensus plugin registration</li>
 *   <li>{@link WorkerComponentFactory} - Component creation with defaults</li>
 *   <li>{@link WorkerRegistry} - Worker registration and lifecycle</li>
 * </ul>
 */
public final class OloWorkerApplication {

    private static final Logger log = LoggerFactory.getLogger(OloWorkerApplication.class);
    private static final int SHUTDOWN_TIMEOUT_SECONDS = 30;

    public static void main(String[] args) {
        // Initialize ports
        DbPortRegistrar.registerDefaults();
        CachePortRegistrar.registerDefaults();
        
        // Bootstrap configuration
        Bootstrap.run();

        // Get global context
        GlobalContext globalContext = GlobalContextProvider.getGlobalContext();
        org.olo.configuration.Configuration config = globalContext.getConfig();
        Map<String, CompositeConfigurationSnapshot> snapshotMap = globalContext.getSnapshotMap();
        Map<String, String> tenantToRegion = globalContext.getTenantToRegionMap();

        // Register consensus plugins for development
        Set<String> consensusTenantIds = TaskQueueResolver.collectConsensusTenantIds(tenantToRegion);
        ConsensusPluginRegistrar.registerConsensusPluginsIfMissing(consensusTenantIds);

        // Resolve task queues and allowed tenants
        TaskQueueResolution queueResolution = TaskQueueResolver.resolveTaskQueues(globalContext);

        // Create worker components
        var runLedger = WorkerComponentFactory.createRunLedger();
        var sessionCache = WorkerComponentFactory.createSessionCache();
        var executionEventSink = WorkerComponentFactory.createExecutionEventSink();
        var pluginExecutorFactory = WorkerComponentFactory.createPluginExecutorFactory();
        var dynamicNodeBuilder = WorkerComponentFactory.createDynamicNodeBuilder();
        var nodeFeatureEnricher = WorkerComponentFactory.createNodeFeatureEnricher();

        // Initialize Temporal connection
        TemporalConfig temporalConfig = TemporalConfig.fromConfiguration(config);
        WorkerFactory factory = WorkerFactory.newInstance(temporalConfig.getWorkflowClient());

        // Register and start workers
        WorkerRegistry workerRegistry = WorkerRegistry.builder()
                .workerFactory(factory)
                .sessionCache(sessionCache)
                .runLedger(runLedger)
                .executionEventSink(executionEventSink)
                .pluginExecutorFactory(pluginExecutorFactory)
                .dynamicNodeBuilder(dynamicNodeBuilder)
                .nodeFeatureEnricher(nodeFeatureEnricher)
                .build();

        workerRegistry.registerWorkers(
                queueResolution.getTaskQueues(),
                queueResolution.getAllowedTenantsByQueue()
        );

        log.info("Starting worker | Temporal: {} | namespace: {} | Redis: {} | DB: {}",
                temporalConfig.getTarget(), temporalConfig.getNamespace(),
                config.get("olo.redis.host", "") + ":" + config.get("olo.redis.port", "6379"),
                config.get("olo.db.host", "") + ":" + config.get("olo.db.port", "5432"));

        // Setup shutdown hook
        setupShutdownHook(factory, workerRegistry);

        // Setup configuration change listener
        setupConfigurationChangeListener(globalContext);

        // Start the worker factory
        workerRegistry.start();

        // Keep main thread alive
        waitForTermination();
    }

    private static void setupShutdownHook(WorkerFactory factory, WorkerRegistry workerRegistry) {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("Shutting down worker...");
            Bootstrap.stopRefreshScheduler();
            Bootstrap.stopTenantRegionRefreshScheduler();
            workerRegistry.shutdown(SHUTDOWN_TIMEOUT_SECONDS);
        }));
    }

    private static void setupConfigurationChangeListener(GlobalContext globalContext) {
        ConfigurationProvider.addSnapshotChangeListener((region, composite) -> {
            if (composite != null) {
                globalContext.rebuildTreeForRegion(composite);
            } else {
                globalContext.removeTreeForRegion(region);
            }
        });
    }

    private static void waitForTermination() {
        try {
            Thread.currentThread().join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.info("Interrupted, shutting down worker...");
        }
    }
}