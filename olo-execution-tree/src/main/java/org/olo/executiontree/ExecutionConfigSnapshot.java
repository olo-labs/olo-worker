/*
 * Copyright (c) 2026 Olo Labs. All rights reserved.
 */

package org.olo.executiontree;

public final class ExecutionConfigSnapshot {
  private final String snapshotVersionId;
  private final String tenantId;
  private final String queueName;
  private final PipelineDefinition pipeline;

  public ExecutionConfigSnapshot(String snapshotVersionId, String tenantId, String queueName, PipelineDefinition pipeline) {
    this.snapshotVersionId = snapshotVersionId;
    this.tenantId = tenantId;
    this.queueName = queueName;
    this.pipeline = pipeline;
  }

  public String getSnapshotVersionId() { return snapshotVersionId; }
  public String getTenantId() { return tenantId; }
  public String getQueueName() { return queueName; }
  public PipelineDefinition getPipeline() { return pipeline; }
}
