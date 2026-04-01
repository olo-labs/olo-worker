/*
 * Copyright (c) 2026 Olo Labs. All rights reserved.
 */

package org.olo.executiontree.variableregistry;

/** Single entry in the pipeline variable registry. */
public interface VariableRegistryEntry {
    String getName();
    VariableScope getScope();
}
