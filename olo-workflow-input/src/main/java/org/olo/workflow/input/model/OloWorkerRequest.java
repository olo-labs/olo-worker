/*
 * Copyright (c) 2026 Olo Labs. All rights reserved.
 */

package org.olo.workflow.input.model;

import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Execution input payload (pipeline, agent, workflow, job, tool, task). Immutable; fixed structure as POJOs, dynamic data as key-value maps.
 * Supports schemaVersion (payload schema) vs routing.pipelineVersion (execution/pipeline version).
 */
@Value
@Builder
@Jacksonized
public class OloWorkerRequest {
  /** Payload schema version (e.g. "1.0", "1.1"). Distinct from routing.pipelineVersion. */
  String schemaVersion;
  String runId;
  /** API-level request id (e.g. from gateway); used for logs and metrics. */
  String requestId;
  String tenantId;
  /** Idempotency key for retry protection, duplicate prevention, exactly-once workflows. */
  String idempotencyKey;
  /** When the request was created (ISO-8601). Used for timeouts, replay detection, queue metrics, debugging. */
  String requestTime;
  /** Environment (e.g. prod, staging). Workers may run in multiple environments. */
  String environment;
  /** Region (e.g. ap-south-1). Routing may depend on region. */
  String region;
  Trace trace;
  Routing routing;
  UserContext userContext;
  List<Input> inputs;
  Map<String, Object> metadata;
  /** Labels for observability (logs, metrics, tracing). e.g. team, experiment. */
  Map<String, String> labels;
  Map<String, Object> context;
  Runtime runtime;
  Events events;
  Execution execution;
  /** Worker config version at request time (e.g. from Redis config refresh). Enables config consistency checks. */
  String configVersion;
  /** Extensible payload for plugins (e.g. rag, evaluation). */
  Map<String, Object> extensions;

  /**
   * Returns inputs indexed by name (first occurrence wins for duplicates).
   * Returns an empty unmodifiable map if inputs is null or empty.
   */
  public Map<String, Input> getInputMap() {
    if (inputs == null || inputs.isEmpty()) {
      return Collections.emptyMap();
    }
    Map<String, Input> map = new LinkedHashMap<>();
    for (Input in : inputs) {
      if (in != null && in.getName() != null && !map.containsKey(in.getName())) {
        map.put(in.getName(), in);
      }
    }
    return Collections.unmodifiableMap(map);
  }

  /**
   * Returns the input with the given name, or null if not found.
   */
  public Input getInput(String name) {
    if (name == null) {
      return null;
    }
    return getInputMap().get(name);
  }
}
