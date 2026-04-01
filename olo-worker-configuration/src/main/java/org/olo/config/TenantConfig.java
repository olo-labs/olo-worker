/*
 * Copyright (c) 2026 Olo Labs. All rights reserved.
 */

package org.olo.config;

import java.util.Collections;
import java.util.Map;

/** Tenant-scoped config view. Stub when using new configuration module. */
public final class TenantConfig {

    public static final TenantConfig EMPTY = new TenantConfig(Collections.emptyMap());

    private final Map<String, Object> configMap;

    public TenantConfig(Map<String, Object> configMap) {
        this.configMap = configMap != null ? Map.copyOf(configMap) : Map.of();
    }

    public Map<String, Object> getConfigMap() {
        return configMap;
    }
}
