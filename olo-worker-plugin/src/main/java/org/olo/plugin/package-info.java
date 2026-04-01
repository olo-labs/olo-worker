/*
 * Copyright (c) 2026 Olo Labs. All rights reserved.
 */

/**
 * OLO worker plugin contracts and registry. Plugins implement contracts (e.g. {@link org.olo.plugin.ModelExecutorPlugin})
 * and register with {@link org.olo.plugin.PluginRegistry} per tenant by id so the worker can resolve {@code pluginRef}
 * from execution tree nodes and invoke them with input/output mappings.
 * <ul>
 *   <li>{@link org.olo.plugin.ExecutablePlugin} – base contract: execute(Map, TenantConfig) → Map</li>
 *   <li>{@link org.olo.plugin.PluginProvider} – SPI for pluggable discovery (ServiceLoader)</li>
 *   <li>{@link org.olo.plugin.ContractType} – MODEL_EXECUTOR, EMBEDDING, VECTOR_STORE, IMAGE_GENERATOR, REDUCER</li>
 *   <li>{@link org.olo.plugin.ModelExecutorPlugin}, {@link org.olo.plugin.EmbeddingPlugin}, {@link org.olo.plugin.VectorStorePlugin}, {@link org.olo.plugin.ImageGenerationPlugin}, {@link org.olo.plugin.ReducerPlugin} – extend ExecutablePlugin</li>
 *   <li>{@link org.olo.plugin.PluginManager} – internal registration and community loading from a controlled directory</li>
 *   <li>{@link org.olo.plugin.RestrictedPluginClassLoader} – hardened parent for community JARs (plugin API + slf4j only)</li>
 *   <li>{@link org.olo.plugin.PluginRegistry} – tenant-scoped registration and lookup</li>
 *   <li>{@link org.olo.annotations.ResourceCleanup} – onExit() for shutdown</li>
 * </ul>
 * <p>
 * Evolution path – versioning and capability metadata: {@link org.olo.plugin.PluginProvider#getVersion()} and
 * {@link org.olo.plugin.PluginProvider#getCapabilityMetadata()} support semantic compatibility, multi-team
 * deployments, and enterprise audit. Future extensions may use pluginId+version as a composite key.
 */
package org.olo.plugin;
