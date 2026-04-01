/*
 * Copyright (c) 2026 Olo Labs. All rights reserved.
 */

package org.olo.executiontree;

/**
 * Compiled, immutable pipeline instance: id + version + checksum + definition.
 * Instances are shared across regions via checksum-based deduplication.
 */
public final class CompiledPipeline {

  private final String pipelineId;
  private final long version;
  private final String checksum;
  private final PipelineDefinition definition;

  public CompiledPipeline(String pipelineId, long version, String checksum, PipelineDefinition definition) {
    this.pipelineId = pipelineId;
    this.version = version;
    this.checksum = checksum;
    this.definition = definition;
  }

  public String getPipelineId() {
    return pipelineId;
  }

  public long getVersion() {
    return version;
  }

  public String getChecksum() {
    return checksum;
  }

  public PipelineDefinition getDefinition() {
    return definition;
  }
}

