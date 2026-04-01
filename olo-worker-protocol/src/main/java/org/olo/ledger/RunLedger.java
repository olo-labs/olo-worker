/*
 * Copyright (c) 2026 Olo Labs. All rights reserved.
 */

package org.olo.ledger;

/**
 * Run ledger for persisting run start/end and node steps. Contract in protocol; implementations in olo-worker-ledger or similar.
 */
public interface RunLedger {

    void runStarted(String runId, String tenantId, String queueName, String snapshotVersionId,
                    String configVersionId, String pluginVersionsJson, String workflowInputJson,
                    long startTimeMs, Object o1, Object o2, String configTreeJson, String tenantConfigJson);

    void runEnded(String runId, long endTimeMs, String runResult, String runStatus, Long durationMs,
                  String errorMessage, String failureStage, Object o1, Object o2, String currency);
}
