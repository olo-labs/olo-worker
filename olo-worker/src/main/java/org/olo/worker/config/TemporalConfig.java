/*
 * Copyright (c) 2026 Olo Labs. All rights reserved.
 */

package org.olo.worker.config;

import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowClientOptions;
import io.temporal.serviceclient.WorkflowServiceStubs;
import io.temporal.serviceclient.WorkflowServiceStubsOptions;
import org.olo.configuration.Configuration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;

/**
 * Manages Temporal connection configuration and lifecycle.
 * Responsible for creating and configuring WorkflowServiceStubs and WorkflowClient.
 */
public final class TemporalConfig {

    private static final Logger log = LoggerFactory.getLogger(TemporalConfig.class);
    
    private static final int RPC_TIMEOUT_SECONDS = 120;

    private final String target;
    private final String namespace;
    private final WorkflowServiceStubs serviceStubs;
    private final WorkflowClient workflowClient;

    private TemporalConfig(String target, String namespace, 
                          WorkflowServiceStubs serviceStubs, WorkflowClient workflowClient) {
        this.target = target;
        this.namespace = namespace;
        this.serviceStubs = serviceStubs;
        this.workflowClient = workflowClient;
    }

    /**
     * Creates a TemporalConfig from environment variables and configuration.
     * Environment variables take precedence over config defaults.
     *
     * @param config the application configuration
     * @return configured TemporalConfig instance
     */
    public static TemporalConfig fromConfiguration(Configuration config) {
        String target = resolveTemporalTarget(config);
        String namespace = resolveTemporalNamespace(config);
        
        log.info("Initializing Temporal connection: target={}, namespace={}", target, namespace);
        
        WorkflowServiceStubs serviceStubs = createServiceStubs(target);
        WorkflowClient workflowClient = createWorkflowClient(serviceStubs, namespace);
        
        return new TemporalConfig(target, namespace, serviceStubs, workflowClient);
    }

    private static String resolveTemporalTarget(Configuration config) {
        String envTarget = System.getenv("OLO_TEMPORAL_TARGET");
        if (envTarget != null && !envTarget.isBlank()) {
            return envTarget.trim();
        }
        return config.get("olo.temporal.target", "localhost:47233");
    }

    private static String resolveTemporalNamespace(Configuration config) {
        String envNamespace = System.getenv("OLO_TEMPORAL_NAMESPACE");
        if (envNamespace != null && !envNamespace.isBlank()) {
            return envNamespace.trim();
        }
        return config.get("olo.temporal.namespace", "default");
    }

    private static WorkflowServiceStubs createServiceStubs(String target) {
        // Long polls can exceed ~60s; default RPC timeout is shorter and causes noisy DEADLINE_EXCEEDED
        return WorkflowServiceStubs.newServiceStubs(
                WorkflowServiceStubsOptions.newBuilder()
                        .setTarget(target)
                        .setRpcTimeout(Duration.ofSeconds(RPC_TIMEOUT_SECONDS))
                        .build()
        );
    }

    private static WorkflowClient createWorkflowClient(WorkflowServiceStubs serviceStubs, String namespace) {
        return WorkflowClient.newInstance(
                serviceStubs,
                WorkflowClientOptions.newBuilder().setNamespace(namespace).build()
        );
    }

    public String getTarget() {
        return target;
    }

    public String getNamespace() {
        return namespace;
    }

    public WorkflowServiceStubs getServiceStubs() {
        return serviceStubs;
    }

    public WorkflowClient getWorkflowClient() {
        return workflowClient;
    }

    /**
     * Shutdown the Temporal connection gracefully.
     */
    public void shutdown() {
        if (serviceStubs != null) {
            serviceStubs.shutdown();
        }
    }
}