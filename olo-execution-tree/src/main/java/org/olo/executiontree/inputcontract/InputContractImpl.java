/*
 * Copyright (c) 2026 Olo Labs. All rights reserved.
 */

package org.olo.executiontree.inputcontract;

import java.util.Map;

/** Simple implementation of {@link InputContract}. */
public final class InputContractImpl implements InputContract {
    private final Map<String, Object> map;
    private final boolean strict;

    public InputContractImpl(Map<String, Object> map, boolean strict) {
        this.map = map != null ? Map.copyOf(map) : Map.of();
        this.strict = strict;
    }

    @Override
    public boolean isStrict() { return strict; }
}
