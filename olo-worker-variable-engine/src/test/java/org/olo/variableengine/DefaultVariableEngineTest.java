/*
 * Copyright (c) 2026 Olo Labs. All rights reserved.
 */

package org.olo.variableengine;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class DefaultVariableEngineTest {

  @Test
  void getSetAndSeed() {
    DefaultVariableEngine engine = new DefaultVariableEngine();
    assertNull(engine.get("x"));

    engine.set("x", "value");
    assertEquals("value", engine.get("x"));

    engine.seedFromInput(Map.of("a", 1, "b", "two"));
    assertEquals(1, engine.get("a"));
    assertEquals("two", engine.get("b"));
    assertEquals("value", engine.get("x"));

    Map<String, Object> snapshot = engine.toMap();
    assertEquals("value", snapshot.get("x"));
    assertEquals(1, snapshot.get("a"));
    assertEquals("two", snapshot.get("b"));
  }
}
