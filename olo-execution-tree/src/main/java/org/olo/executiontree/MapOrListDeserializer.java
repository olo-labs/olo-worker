/*
 * Copyright (c) 2026 Olo Labs. All rights reserved.
 */

package org.olo.executiontree;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Deserializes inputMappings/outputMappings from either a JSON object (Map) or
 * a JSON array of {pluginParameter, variable} (protocol List&lt;ParameterMapping&gt; form).
 */
public final class MapOrListDeserializer extends JsonDeserializer<Map<String, Object>> {

  @Override
  public Map<String, Object> deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
    JsonNode node = p.getCodec().readTree(p);
    if (node == null || node.isNull()) return new LinkedHashMap<>();
    if (node.isObject()) {
      Map<String, Object> out = new LinkedHashMap<>();
      node.fields().forEachRemaining(e -> out.put(e.getKey(), e.getValue().isTextual() ? e.getValue().asText() : e.getValue()));
      return out;
    }
    if (node.isArray()) {
      Map<String, Object> out = new LinkedHashMap<>();
      for (JsonNode item : node) {
        if (!item.isObject()) continue;
        String param = item.has("pluginParameter") ? item.get("pluginParameter").asText("") : null;
        String variable = item.has("variable") ? item.get("variable").asText("") : null;
        if (param != null) out.put(param, variable != null ? variable : "");
      }
      return out;
    }
    return new LinkedHashMap<>();
  }
}
