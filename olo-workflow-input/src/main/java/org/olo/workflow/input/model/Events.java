/*
 * Copyright (c) 2026 Olo Labs. All rights reserved.
 */

package org.olo.workflow.input.model;

import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

import java.util.List;

/**
 * Event delivery: sinks (webhook, kafka, pubsub, websocket) and subscriptions.
 */
@Value
@Builder
@Jacksonized
public class Events {
  /** Event sinks (webhook, kafka, pubsub, websocket). */
  List<EventSink> sinks;
  List<String> subscriptions;
}
