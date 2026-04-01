/*
 * Copyright (c) 2026 Olo Labs. All rights reserved.
 */

package org.olo.variableengine;

import org.olo.executiontree.VariableEngine;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Default in-memory implementation of {@link VariableEngine} for a single run.
 * Thread-safe; seeded from workflow input and updated by outputMappings.
 */
public final class DefaultVariableEngine implements VariableEngine {

  private final Map<String, Object> variables = new ConcurrentHashMap<>();

  @Override
  public Object get(String name) {
    return variables.get(name);
  }

  @Override
  public void set(String name, Object value) {
    variables.put(name, value);
  }

  @Override
  public void seedFromInput(Map<String, Object> inputValues) {
    if (inputValues != null && !inputValues.isEmpty()) {
      variables.putAll(inputValues);
    }
  }

  @Override
  public Map<String, Object> toMap() {
    return new HashMap<>(variables);
  }
}
