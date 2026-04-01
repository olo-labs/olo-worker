/*
 * Copyright (c) 2026 Olo Labs. All rights reserved.
 */

package org.olo.executiontree.outputcontract;

/** Simple implementation of {@link ResultMapping}. */
public record ResultMappingImpl(String variable) implements ResultMapping {
    @Override
    public String getVariable() { return variable != null ? variable : ""; }
}
