/*
 * Copyright (c) 2026 Olo Labs. All rights reserved.
 */

package org.olo.worker.registry;

import io.temporal.client.WorkflowClient;
import io.temporal.worker.Worker;
import io.temporal.worker.WorkerFactory;
import io.temporal.worker.WorkerOptions;
import org.olo.node.DynamicNodeBuilder;
import org.olo.node.NodeFeatureEnricher;
import org.olo.plugin.PluginExecutorFactory;
import org.olo.config.OloSessionCache;
import org.olo.ledger.ExecutionEventSink;
import org.olo.ledger.RunLedger;
import org.olo.worker.activity.impl.ExecuteNodeDynamicActivity;
import org.olo.worker.activity.impl.OloKernelActivitiesImpl;
import org.olo.worker.workflow.impl.OloKernelWorkflowImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

/**
 * Manages registration of Temporal workers for task queues.
 * Handles creation and configuration of workers with appropriate activities and workflows.
 */
public final class WorkerRegistry {

    private static final Logger log = LoggerFactory.getLogger(WorkerRegistry.class);

    private static final int MAX_CONCURRENT_ACTIVITY_EXECUTION_SIZE = 10;
    private static final int MAX_CONCURRENT_WORKFLOW_TASK_EXECUTION_SIZE = 10;

    private final WorkerFactory workerFactory;
    private final OloSessionCache sessionCache;
    private final RunLedger runLedger;
    private final ExecutionEventSink executionEventSink;
    private final PluginExecutorFactory pluginExecutorFactory;
    private final DynamicNodeBuilder dynamicNodeBuilder;
    private final NodeFeatureEnricher nodeFeatureEnricher;

    private WorkerRegistry(Builder builder) {
        this.workerFactory = builder.workerFactory;
        this.sessionCache = builder.sessionCache;
        this.runLedger = builder.runLedger;
        this.executionEventSink = builder.executionEventSink;
        this.pluginExecutorFactory = builder.pluginExecutorFactory;
        this.dynamicNodeBuilder = builder.dynamicNodeBuilder;
        this.nodeFeatureEnricher = builder.nodeFeatureEnricher;
    }

    /**
     * Creates a new builder for WorkerRegistry.
     *
     * @return new Builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Registers workers for all task queues with their allowed tenants.
     *
     * @param taskQueues list of task queue names
     * @param allowedTenantsByQueue mapping of queue names to allowed tenant IDs
     */
    public void registerWorkers(
            List<String> taskQueues,
            Map<String, List<String>> allowedTenantsByQueue
    ) {
        WorkerOptions workerOptions = WorkerOptions.newBuilder()
                .setMaxConcurrentActivityExecutionSize(MAX_CONCURRENT_ACTIVITY_EXECUTION_SIZE)
                .setMaxConcurrentWorkflowTaskExecutionSize(MAX_CONCURRENT_WORKFLOW_TASK_EXECUTION_SIZE)
                .build();

        for (String taskQueue : taskQueues) {
            List<String> tenantIds = allowedTenantsByQueue.getOrDefault(taskQueue, List.of("default"));
            registerWorkerForQueue(taskQueue, tenantIds, workerOptions);
        }

        log.info("Task queues registered: {}", taskQueues);
    }

    private void registerWorkerForQueue(
            String taskQueue,
            List<String> tenantIds,
            WorkerOptions workerOptions
    ) {
        OloKernelActivitiesImpl activities = new OloKernelActivitiesImpl(
                sessionCache, tenantIds, runLedger, executionEventSink,
                pluginExecutorFactory, dynamicNodeBuilder, nodeFeatureEnricher
        );
        ExecuteNodeDynamicActivity executeNodeDynamicActivity = new ExecuteNodeDynamicActivity(activities);

        Worker worker = workerFactory.newWorker(taskQueue, workerOptions);
        worker.registerWorkflowImplementationTypes(OloKernelWorkflowImpl.class);
        worker.registerActivitiesImplementations(activities, executeNodeDynamicActivity);

        log.info("Registered worker for task queue: {}", taskQueue);
    }

    /**
     * Starts the worker factory.
     */
    public void start() {
        workerFactory.start();
    }

    /**
     * Shuts down the worker factory gracefully.
     *
     * @param timeoutSeconds maximum time to wait for termination
     */
    public void shutdown(int timeoutSeconds) {
        log.info("Shutting down worker...");
        workerFactory.shutdown();
        try {
            workerFactory.awaitTermination(timeoutSeconds, java.util.concurrent.TimeUnit.SECONDS);
        } catch (Exception e) {
            log.error("Error during worker shutdown: {}", e.getMessage());
        }
    }

    /**
     * Builder for WorkerRegistry.
     */
    public static final class Builder {
        private WorkerFactory workerFactory;
        private OloSessionCache sessionCache;
        private RunLedger runLedger;
        private ExecutionEventSink executionEventSink;
        private PluginExecutorFactory pluginExecutorFactory;
        private DynamicNodeBuilder dynamicNodeBuilder;
        private NodeFeatureEnricher nodeFeatureEnricher;

        private Builder() {}

        public Builder workerFactory(WorkerFactory workerFactory) {
            this.workerFactory = workerFactory;
            return this;
        }

        public Builder sessionCache(OloSessionCache sessionCache) {
            this.sessionCache = sessionCache;
            return this;
        }

        public Builder runLedger(RunLedger runLedger) {
            this.runLedger = runLedger;
            return this;
        }

        public Builder executionEventSink(ExecutionEventSink executionEventSink) {
            this.executionEventSink = executionEventSink;
            return this;
        }

        public Builder pluginExecutorFactory(PluginExecutorFactory pluginExecutorFactory) {
            this.pluginExecutorFactory = pluginExecutorFactory;
            return this;
        }

        public Builder dynamicNodeBuilder(DynamicNodeBuilder dynamicNodeBuilder) {
            this.dynamicNodeBuilder = dynamicNodeBuilder;
            return this;
        }

        public Builder nodeFeatureEnricher(NodeFeatureEnricher nodeFeatureEnricher) {
            this.nodeFeatureEnricher = nodeFeatureEnricher;
            return this;
        }

        public WorkerRegistry build() {
            validate();
            return new WorkerRegistry(this);
        }

        private void validate() {
            if (workerFactory == null) {
                throw new IllegalStateException("WorkerFactory is required");
            }
            if (sessionCache == null) {
                throw new IllegalStateException("OloSessionCache is required");
            }
            if (runLedger == null) {
                throw new IllegalStateException("RunLedger is required");
            }
            if (executionEventSink == null) {
                throw new IllegalStateException("ExecutionEventSink is required");
            }
            if (pluginExecutorFactory == null) {
                throw new IllegalStateException("PluginExecutorFactory is required");
            }
            if (dynamicNodeBuilder == null) {
                throw new IllegalStateException("DynamicNodeBuilder is required");
            }
            if (nodeFeatureEnricher == null) {
                throw new IllegalStateException("NodeFeatureEnricher is required");
            }
        }
    }
}