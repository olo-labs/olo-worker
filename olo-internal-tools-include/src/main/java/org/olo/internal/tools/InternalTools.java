/*
 * Copyright (c) 2026 Olo Labs. All rights reserved.
 */

package org.olo.internal.tools;

import org.olo.plugin.PluginManager;
import org.olo.tools.ToolProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Registers internal tools with the plugin manager so they are available as plugins
 * (by pluginRef) in the execution tree. Call {@link #registerInternalTools(PluginManager)}
 * after {@link org.olo.internal.plugins.InternalPlugins#createPluginManager()} so that
 * tool providers are included in {@link PluginManager#getInternalProviders()}.
 */
public final class InternalTools {

    private static final Logger log = LoggerFactory.getLogger(InternalTools.class);

    private InternalTools() {
    }

    /**
     * Registers all internal tool providers with the given plugin manager.
     * The worker should call this after creating the plugin manager and before
     * registering providers with {@link org.olo.plugin.PluginRegistry}.
     *
     * @param pluginManager the manager to which tool providers are added as internal
     */
    public static void registerInternalTools(PluginManager pluginManager) {
        if (pluginManager == null) return;
        register(pluginManager, new org.olo.tool.research.ResearchToolProvider());
        register(pluginManager, new org.olo.tool.critic.CriticToolProvider());
        register(pluginManager, new org.olo.tool.evaluator.EvaluatorToolProvider());
        register(pluginManager, new org.olo.tool.echo.EchoToolProvider());
        log.info("Registered {} internal tool(s)", 4);
    }

    private static void register(PluginManager pluginManager, ToolProvider provider) {
        if (provider != null && provider.isEnabled()) {
            pluginManager.registerInternal(provider);
        }
    }
}
