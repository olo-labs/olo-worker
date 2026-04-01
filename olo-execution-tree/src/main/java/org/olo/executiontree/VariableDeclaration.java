/*
 * Copyright (c) 2026 Olo Labs. All rights reserved.
 */

package org.olo.executiontree;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

public final class VariableDeclaration {
  private final String name;
  private final String type;
  private final VariableScope scope;

  @JsonCreator
  public VariableDeclaration(
      @JsonProperty("name") String name,
      @JsonProperty("type") String type,
      @JsonProperty("scope") VariableScope scope) {
    this.name = Objects.requireNonNull(name, "name");
    this.type = type != null ? type : "string";
    this.scope = scope != null ? scope : VariableScope.INTERNAL;
  }

  public String getName() { return name; }
  public String getType() { return type; }
  public VariableScope getScope() { return scope; }
}
