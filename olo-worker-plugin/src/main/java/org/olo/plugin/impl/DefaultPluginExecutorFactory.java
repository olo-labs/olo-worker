/*
 * Copyright (c) 2026 Olo Labs. All rights reserved.
 */

package org.olo.plugin.impl;

import org.olo.plugin.PluginExecutorFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Implementation of {@link PluginExecutorFactory} that creates {@link RegistryPluginExecutor}
 * instances. Bootstrap wires this so the worker receives the factory without depending on PluginRegistry.
 */
public final class DefaultPluginExecutorFactory implements PluginExecutorFactory {

    @Override
    public org.olo.plugin.PluginExecutor create(String tenantId, Map<String, ?> nodeInstanceCache) {
        Map<String, ?> cache = nodeInstanceCache != null ? nodeInstanceCache : new ConcurrentHashMap<>();
        return new RegistryPluginExecutor(tenantId, cache);
    }
}
