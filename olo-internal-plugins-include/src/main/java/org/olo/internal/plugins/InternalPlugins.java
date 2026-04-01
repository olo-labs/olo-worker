/*
 * Copyright (c) 2026 Olo Labs. All rights reserved.
 */

package org.olo.internal.plugins;

import org.olo.internal.plugins.consensus.ConsensusPluginProvider;
import org.olo.join.reducer.OutputReducerPluginProvider;
import org.olo.plugin.PluginManager;
import org.olo.plugin.PluginProvider;
import org.olo.plugin.embedding.ollama.OllamaEmbeddingPluginProvider;
import org.olo.plugin.litellm.LiteLLMPluginProvider;
import org.olo.plugin.ollama.OllamaPluginProvider;
import org.olo.plugin.qdrant.QdrantPluginProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Plugin-side bootstrap: creates a {@link PluginManager} with internal providers registered
 * and community plugins loaded from a controlled directory. The worker only calls
 * {@link #createPluginManager()} and then registers the returned providers with {@link org.olo.plugin.PluginRegistry}.
 */
public final class InternalPlugins {

    private static final Logger log = LoggerFactory.getLogger(InternalPlugins.class);
    private static final String OLO_PLUGINS_DIR_ENV = "OLO_PLUGINS_DIR";
    private static final String DEFAULT_PLUGINS_DIR = "/opt/olo/plugins";

    private InternalPlugins() {
    }

    /**
     * Creates a PluginManager with internal providers (Ollama, LiteLLM, Qdrant, Ollama Embedding)
     * registered and community plugins loaded from the directory given by {@code OLO_PLUGINS_DIR}
     * (default {@value #DEFAULT_PLUGINS_DIR}). Only that directory is scanned for {@code *.jar} files.
     *
     * @return configured PluginManager; use {@link PluginManager#getProviders()} to register with PluginRegistry
     */
    public static PluginManager createPluginManager() {
        PluginManager pluginManager = new PluginManager();

        pluginManager.registerInternal(new OllamaPluginProvider());
        pluginManager.registerInternal(new LiteLLMPluginProvider());
        pluginManager.registerInternal(new QdrantPluginProvider());
        pluginManager.registerInternal(new OllamaEmbeddingPluginProvider());
        pluginManager.registerInternal(new OutputReducerPluginProvider());
        pluginManager.registerInternal(new ConsensusPluginProvider());

        String pluginsDirEnv = System.getenv(OLO_PLUGINS_DIR_ENV);
        Path pluginsDir = (pluginsDirEnv != null && !pluginsDirEnv.isBlank())
                ? Paths.get(pluginsDirEnv.trim())
                : Paths.get(DEFAULT_PLUGINS_DIR);
        pluginManager.loadCommunityPlugins(pluginsDir);

        log.info("Plugins: {} internal, {} community (dir={})",
                pluginManager.getInternalCount(), pluginManager.getCommunityCount(), pluginsDir);

        return pluginManager;
    }
}
