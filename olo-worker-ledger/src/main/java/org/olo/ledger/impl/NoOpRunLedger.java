/*
 * Copyright (c) 2026 Olo Labs. All rights reserved.
 */

package org.olo.ledger.impl;

import org.olo.ledger.RunLedger;

/**
 * No-op run ledger. Persists nothing; use when DB persistence is not required.
 */
public final class NoOpRunLedger implements RunLedger {

    private final NoOpLedgerStore store;

    public NoOpRunLedger(NoOpLedgerStore store) {
        this.store = store;
    }

    @Override
    public void runStarted(String runId, String tenantId, String queueName, String snapshotVersionId,
                           String configVersionId, String pluginVersionsJson, String workflowInputJson,
                           long startTimeMs, Object o1, Object o2, String configTreeJson, String tenantConfigJson) {
        // no-op
    }

    @Override
    public void runEnded(String runId, long endTimeMs, String runResult, String runStatus, Long durationMs,
                         String errorMessage, String failureStage, Object o1, Object o2, String currency) {
        // no-op
    }
}
