/*
 * Copyright (c) 2026 Olo Labs. All rights reserved.
 */

package org.olo.executiontree.config;

import java.util.Map;

/**
 * Container for pipeline definitions keyed by name. Used when deserializing
 * config JSON and by bootstrap/worker for pipeline config by queue.
 */
public class PipelineConfiguration {

    private Map<String, PipelineDefinition> pipelines;
    private String version;

    public PipelineConfiguration() {}

    public Map<String, PipelineDefinition> getPipelines() {
        return pipelines;
    }

    public void setPipelines(Map<String, ? extends PipelineDefinition> pipelines) {
        this.pipelines = pipelines != null ? new java.util.LinkedHashMap<>(pipelines) : null;
    }

    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }
}
