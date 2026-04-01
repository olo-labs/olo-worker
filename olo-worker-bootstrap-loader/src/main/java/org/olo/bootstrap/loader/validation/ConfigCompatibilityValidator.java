/*
 * Copyright (c) 2026 Olo Labs. All rights reserved.
 */

package org.olo.bootstrap.loader.validation;

import org.olo.executiontree.config.PipelineConfiguration;
import org.olo.features.FeatureRegistry;
import org.olo.plugin.PluginRegistry;

/** Validates config version and plugin/feature contracts. No-op when not wired. */
public final class ConfigCompatibilityValidator {

    public ConfigCompatibilityValidator(Object pluginExecutorFactory, Object featureRegistryOrNull,
                                        PluginRegistry pluginRegistry, FeatureRegistry featureRegistry) {}

    public void validateOrThrow(String tenantKey, PipelineConfiguration config) throws ConfigIncompatibleException {}
}
