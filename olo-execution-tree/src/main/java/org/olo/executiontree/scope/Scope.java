/*
 * Copyright (c) 2026 Olo Labs. All rights reserved.
 */

package org.olo.executiontree.scope;

import java.util.Map;
import java.util.Set;

/**
 * Pipeline scope (plugins and features). Used by protocol for feature attachment.
 *
 * @see org.olo.executiontree.Scope
 */
public interface Scope {
    Map<String, Object> getPlugins();
    Set<FeatureDef> getFeatures();
}
