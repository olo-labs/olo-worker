/*
 * Copyright (c) 2026 Olo Labs. All rights reserved.
 */

package org.olo.executiontree.scope;

/** Simple implementation of {@link FeatureDef}. */
public record SimpleFeatureDef(String id) implements FeatureDef {
    @Override
    public String getId() { return id != null ? id : ""; }
}
