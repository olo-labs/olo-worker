/*
 * Copyright (c) 2026 Olo Labs. All rights reserved.
 */

package org.olo.executiontree;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Pipeline-level variable registry: list of variables with name, type, scope (IN, INTERNAL, OUT).
 * Defines the pipeline's data contract; only these variables may be used in mappings and conditions.
 *
 * @see VariableDeclaration
 * @see VariableScope
 */
public final class VariableRegistry {
  private final List<VariableDeclaration> declarations;
  private final Map<String, VariableDeclaration> byName;

  @JsonCreator
  public VariableRegistry(@JsonProperty("declarations") List<VariableDeclaration> declarations) {
    this.declarations = declarations == null ? List.of() : List.copyOf(declarations);
    this.byName = this.declarations.stream().collect(Collectors.toUnmodifiableMap(VariableDeclaration::getName, d -> d));
  }

  public List<VariableDeclaration> getDeclarations() {
    return declarations;
  }

  public VariableDeclaration get(String name) {
    return byName.get(name);
  }

  public boolean contains(String name) {
    return byName.containsKey(name);
  }
}
