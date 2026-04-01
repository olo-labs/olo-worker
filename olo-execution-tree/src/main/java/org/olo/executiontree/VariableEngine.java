/*
 * Copyright (c) 2026 Olo Labs. All rights reserved.
 */

package org.olo.executiontree;

import java.util.Map;

/**
 * Variable store for a single run. Seeded with IN variables from workflow input;
 * updated by outputMappings; read by inputMappings and conditions. Implementation
 * lives in the execution module; this interface defines the contract for the tree module.
 */
public interface VariableEngine {
  Object get(String name);
  void set(String name, Object value);
  void seedFromInput(Map<String, Object> inputValues);
  Map<String, Object> toMap();
}
