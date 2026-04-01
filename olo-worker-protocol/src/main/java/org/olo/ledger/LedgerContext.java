/*
 * Copyright (c) 2026 Olo Labs. All rights reserved.
 */

package org.olo.ledger;

/**
 * Thread-local run ID for ledger and node execution. Contract in protocol.
 */
public final class LedgerContext {

    private static final ThreadLocal<String> RUN_ID = new ThreadLocal<>();

    public static void setRunId(String runId) {
        RUN_ID.set(runId);
    }

    public static String getRunId() {
        return RUN_ID.get();
    }

    public static void clear() {
        RUN_ID.remove();
    }

    private LedgerContext() {}
}
