/*
 * Copyright (c) 2026 Olo Labs. All rights reserved.
 */

package org.olo.workflow.input.parser;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.olo.workflow.input.model.OloWorkerRequest;

public final class OloPayloadParser {
  private static final ObjectMapper MAPPER = new ObjectMapper()
      .registerModule(new JavaTimeModule())
      .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
      .configure(MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS, true)
      .configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);

  private OloPayloadParser() {}

  /** Deserialize JSON using default version strategy (1.0, 1.1). */
  public static OloWorkerRequest parse(String json) {
    return parse(json, VersionStrategy.defaultStrategy());
  }

  /**
   * Deserialize JSON using the given version strategy.
   * The "schemaVersion" field selects the request type (e.g. 1.0, 1.1).
   */
  public static OloWorkerRequest parse(String json, VersionStrategy versionStrategy) {
    if (json == null || json.isBlank()) {
      throw new IllegalArgumentException("payload JSON is empty");
    }
    if (versionStrategy == null) {
      throw new IllegalArgumentException("versionStrategy is null");
    }
    try {
      JsonNode root = MAPPER.readTree(json);
      String schemaVersion = root.has("schemaVersion") ? root.path("schemaVersion").asText(null) : null;
      Class<? extends OloWorkerRequest> type = versionStrategy.requestTypeForVersion(schemaVersion);
      return MAPPER.treeToValue(root, type);
    } catch (JsonProcessingException e) {
      throw new IllegalArgumentException("payload JSON is invalid: " + e.getOriginalMessage(), e);
    }
  }

  /** Serialize {@link OloWorkerRequest} to JSON string. */
  public static String toJson(OloWorkerRequest request) {
    if (request == null) {
      throw new IllegalArgumentException("request is null");
    }
    try {
      return MAPPER.writeValueAsString(request);
    } catch (JsonProcessingException e) {
      throw new IllegalArgumentException("serialization failed: " + e.getOriginalMessage(), e);
    }
  }
}
