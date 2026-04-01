/*
 * Copyright (c) 2026 Olo Labs. All rights reserved.
 */

package org.olo.executiontree;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Round-trip test: sample JSON → deserialize to Map → compile to ExecutionTreeNode
 * → export to Map → serialize to JSON → deserialize again and compare with original.
 * Includes a deep (depth=5) mixed node-type tree to catch recursion bugs.
 */
class ExecutionTreeJsonRoundTripTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final int DEEP_TREE_DEPTH = 5;
  private static final NodeType[] CONTAINER_TYPES = { NodeType.SEQUENCE, NodeType.IF, NodeType.FORK };

  private static final String SAMPLE_JSON = """
      {
        "id": "root",
        "type": "SEQUENCE",
        "name": "payments-pipeline",
        "version": "1.0",
        "params": {},
        "metadata": {
          "owner": "payments-team",
          "description": "fraud detection step",
          "tags": ["fraud", "risk"]
        },
        "children": [
          {
            "id": "step1",
            "type": "PLUGIN",
            "name": "validate-input",
            "params": { "pluginRef": "validator" },
            "inputMappings": { "payload": "input" },
            "outputMappings": { "result": "validationResult" },
            "timeout": { "startToCloseSeconds": 30 },
            "retryPolicy": { "maximumAttempts": 3 },
            "executionMode": "SYNC"
          },
          {
            "id": "step2",
            "type": "SEQUENCE",
            "children": []
          }
        ]
      }
      """;

  @Test
  void roundTripJson_serialiseDeserialise_compareResult() throws Exception {
    // Deserialize: JSON → Map
    Map<String, Object> originalMap = MAPPER.readValue(SAMPLE_JSON, Map.class);

    // Compile: Map → ExecutionTreeNode
    ExecutionTreeNode node = ExecutionTreeCompiler.compileNode(originalMap);

    // Serialise: ExecutionTreeNode → Map → JSON
    Map<String, Object> roundTripMap = ExecutionTreeCompiler.toNodeMap(node);
    String roundTripJson = MAPPER.writeValueAsString(roundTripMap);

    // Deserialize round-trip JSON back to Map
    Map<String, Object> roundTripParsed = MAPPER.readValue(roundTripJson, Map.class);

    // Normalise: remove empty maps/lists so original (which may have "params": {}, "children": [])
    // is comparable to round-trip (which omits empty collections)
    Map<String, Object> normalisedOriginal = (Map<String, Object>) normalise(originalMap);
    Map<String, Object> normalisedRoundTrip = (Map<String, Object>) normalise(roundTripParsed);

    // Compare: original content is preserved in round-trip
    assertTrue(
        mapsEquivalent(normalisedOriginal, normalisedRoundTrip),
        "Round-trip map should be equivalent to original. Original keys: " + normalisedOriginal.keySet()
            + ", roundTrip keys: " + normalisedRoundTrip.keySet());
  }

  /**
   * Deep nested tree (depth=5) with mixed node types (SEQUENCE, IF, FORK, PLUGIN)
   * to exercise recursion in compileNode and toNodeMap and catch recursion bugs.
   */
  @Test
  void roundTripDeepMixedTree_depth5_equivalentAfterRoundTrip() throws Exception {
    Map<String, Object> originalMap = buildDeepMixedTree(DEEP_TREE_DEPTH, "root");

    // Compile: Map → ExecutionTreeNode (recursive)
    ExecutionTreeNode node = ExecutionTreeCompiler.compileNode(originalMap);

    // Assert structure: depth and node count
    int[] depthAndCount = depthAndNodeCount(node);
    assertTrue(depthAndCount[0] >= DEEP_TREE_DEPTH,
        "Expected depth >= " + DEEP_TREE_DEPTH + ", got " + depthAndCount[0]);
    assertTrue(depthAndCount[1] > 10,
        "Expected many nodes in deep tree, got " + depthAndCount[1]);

    // Serialise: ExecutionTreeNode → Map → JSON
    Map<String, Object> roundTripMap = ExecutionTreeCompiler.toNodeMap(node);
    String roundTripJson = MAPPER.writeValueAsString(roundTripMap);

    // Deserialize and compile again (double round-trip)
    Map<String, Object> roundTripParsed = MAPPER.readValue(roundTripJson, Map.class);
    ExecutionTreeNode node2 = ExecutionTreeCompiler.compileNode(roundTripParsed);
    Map<String, Object> roundTripMap2 = ExecutionTreeCompiler.toNodeMap(node2);

    // Normalise and compare: original vs first round-trip
    Map<String, Object> normalisedOriginal = (Map<String, Object>) normalise(originalMap);
    Map<String, Object> normalisedRoundTrip = (Map<String, Object>) normalise(roundTripParsed);
    assertTrue(
        mapsEquivalent(normalisedOriginal, normalisedRoundTrip),
        "First round-trip: map should be equivalent to original");

    // Second round-trip should still match
    Map<String, Object> normalisedRoundTrip2 = (Map<String, Object>) normalise(roundTripMap2);
    assertTrue(
        mapsEquivalent(normalisedOriginal, normalisedRoundTrip2),
        "Second round-trip: map should be equivalent to original");
  }

  /** Builds a deep tree: at depth 0 returns a PLUGIN leaf; otherwise a container (SEQUENCE/IF/FORK) with 2 children. */
  private static Map<String, Object> buildDeepMixedTree(int depth, String id) {
    Map<String, Object> node = new LinkedHashMap<>();
    node.put("id", id);
    if (depth <= 0) {
      node.put("type", NodeType.PLUGIN.name());
      node.put("name", "leaf-" + id);
      node.put("params", Map.of("pluginRef", "plugin-" + id));
      return node;
    }
    NodeType containerType = CONTAINER_TYPES[depth % CONTAINER_TYPES.length];
    node.put("type", containerType.name());
    node.put("name", "container-" + id);
    List<Map<String, Object>> children = new ArrayList<>();
    children.add(buildDeepMixedTree(depth - 1, id + ".0"));
    children.add(buildDeepMixedTree(depth - 1, id + ".1"));
    node.put("children", children);
    return node;
  }

  /** Returns { maxDepth, nodeCount } for the tree. */
  private static int[] depthAndNodeCount(ExecutionTreeNode node) {
    int[] out = new int[] { 0, 0 };
    depthAndNodeCountRec(node, 0, out);
    return out;
  }

  private static void depthAndNodeCountRec(ExecutionTreeNode node, int depth, int[] out) {
    out[0] = Math.max(out[0], depth + 1);
    out[1]++;
    for (ExecutionTreeNode child : node.getChildren()) {
      depthAndNodeCountRec(child, depth + 1, out);
    }
  }

  @SuppressWarnings("unchecked")
  private static Object normalise(Object o) {
    if (o instanceof Map) {
      Map<String, Object> m = (Map<String, Object>) o;
      Map<String, Object> out = new LinkedHashMap<>();
      for (Map.Entry<String, Object> e : m.entrySet()) {
        Object v = normalise(e.getValue());
        if (v instanceof Map && ((Map<?, ?>) v).isEmpty()) continue;
        if (v instanceof List && ((List<?>) v).isEmpty()) continue;
        out.put(e.getKey(), v);
      }
      return out;
    }
    if (o instanceof List) {
      List<Object> out = new ArrayList<>();
      for (Object item : (List<?>) o) out.add(normalise(item));
      return out;
    }
    return o;
  }

  @SuppressWarnings("unchecked")
  private static boolean mapsEquivalent(Object a, Object b) {
    if (a == b) return true;
    if (a == null || b == null) return false;
    if (a instanceof Map && b instanceof Map) {
      Map<String, Object> ma = (Map<String, Object>) a;
      Map<String, Object> mb = (Map<String, Object>) b;
      if (!ma.keySet().equals(mb.keySet())) return false;
      for (String key : ma.keySet()) {
        if (!mapsEquivalent(ma.get(key), mb.get(key))) return false;
      }
      return true;
    }
    if (a instanceof List && b instanceof List) {
      List<?> la = (List<?>) a;
      List<?> lb = (List<?>) b;
      if (la.size() != lb.size()) return false;
      for (int i = 0; i < la.size(); i++) {
        if (!mapsEquivalent(la.get(i), lb.get(i))) return false;
      }
      return true;
    }
    return a.equals(b);
  }
}
