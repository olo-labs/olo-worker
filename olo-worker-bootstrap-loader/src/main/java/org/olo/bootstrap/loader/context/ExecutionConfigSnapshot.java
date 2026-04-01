/*
 * Copyright (c) 2026 Olo Labs. All rights reserved.
 */

package org.olo.bootstrap.loader.context;

import org.olo.executiontree.config.PipelineConfiguration;

/** Immutable snapshot of execution config for a run (tenant, queue, config version, run id). */
public final class ExecutionConfigSnapshot {

    private final String tenantId;
    private final String queueName;
    private final PipelineConfiguration pipelineConfiguration;
    private final String snapshotVersionId;
    private final String runId;

    private ExecutionConfigSnapshot(String tenantId, String queueName, PipelineConfiguration pipelineConfiguration,
                                    String snapshotVersionId, String runId) {
        this.tenantId = tenantId != null ? tenantId : "";
        this.queueName = queueName != null ? queueName : "";
        this.pipelineConfiguration = pipelineConfiguration;
        this.snapshotVersionId = snapshotVersionId != null ? snapshotVersionId : "";
        this.runId = runId != null ? runId : "";
    }

    public static ExecutionConfigSnapshot of(String tenantId, String effectiveQueue, PipelineConfiguration config,
                                            String snapshotVersionId, String runId) {
        return new ExecutionConfigSnapshot(tenantId, effectiveQueue, config, snapshotVersionId, runId);
    }

    public String getTenantId() { return tenantId; }
    public String getQueueName() { return queueName; }
    public PipelineConfiguration getPipelineConfiguration() { return pipelineConfiguration; }
    public String getSnapshotVersionId() { return snapshotVersionId; }
    public String getRunId() { return runId; }
}
