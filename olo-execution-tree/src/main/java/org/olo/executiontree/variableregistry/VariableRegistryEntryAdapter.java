/*
 * Copyright (c) 2026 Olo Labs. All rights reserved.
 */

package org.olo.executiontree.variableregistry;

import org.olo.executiontree.VariableDeclaration;

/** Adapts {@link VariableDeclaration} to {@link VariableRegistryEntry}. */
public final class VariableRegistryEntryAdapter implements VariableRegistryEntry {
    private final VariableDeclaration d;

    public VariableRegistryEntryAdapter(VariableDeclaration d) {
        this.d = d;
    }

    @Override
    public String getName() { return d != null ? d.getName() : ""; }

    @Override
    public VariableScope getScope() {
        if (d == null || d.getScope() == null) return VariableScope.INTERNAL;
        try {
            return VariableScope.valueOf(d.getScope().name());
        } catch (Exception e) {
            return VariableScope.INTERNAL;
        }
    }
}
