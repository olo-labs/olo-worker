/*
 * Copyright (c) 2026 Olo Labs. All rights reserved.
 */

package org.olo.workflow.input.model;

import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

/**
 * Event sink for streaming or delivery (webhook, kafka, pubsub, websocket, etc.).
 */
@Value
@Builder
@Jacksonized
public class EventSink {
  /** Sink type: WEBHOOK, KAFKA, PUBSUB, WEBSOCKET, etc. */
  String type;
  /** Endpoint URL (webhook) or topic/channel identifier. */
  String endpoint;
}
