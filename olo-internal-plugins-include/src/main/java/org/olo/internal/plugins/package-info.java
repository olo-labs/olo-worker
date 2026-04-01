/*
 * Copyright (c) 2026 Olo Labs. All rights reserved.
 */

/**
 * Aggregator module for internal plugins (Ollama, LiteLLM, Qdrant, Ollama Embedding).
 * No code; only brings these plugins onto the classpath for the worker fat JAR.
 * Registration is done in the worker via {@link org.olo.plugin.PluginManager#registerInternal}.
 */
package org.olo.internal.plugins;
