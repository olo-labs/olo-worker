/*
 * Copyright (c) 2026 Olo Labs. All rights reserved.
 */

package org.olo.ledger;

/**
 * Execution event for semantic steps / chat UI. Contract in protocol.
 */
public final class ExecutionEvent {

    public static final class EventType {
        public static final String WORKFLOW_STARTED = "WORKFLOW_STARTED";
        public static final String WORKFLOW_COMPLETED = "WORKFLOW_COMPLETED";
        public static final String WORKFLOW_FAILED = "WORKFLOW_FAILED";
    }

    private final String eventType;
    private final String label;
    private final Object payload;
    private final long timestamp;
    private final Object metadata;

    public ExecutionEvent(String eventType, String label, Object payload, long timestamp, Object metadata) {
        this.eventType = eventType;
        this.label = label;
        this.payload = payload;
        this.timestamp = timestamp;
        this.metadata = metadata;
    }

    public String getEventType() { return eventType; }
    public String getLabel() { return label; }
    public Object getPayload() { return payload; }
    public long getTimestamp() { return timestamp; }
    public Object getMetadata() { return metadata; }
}
