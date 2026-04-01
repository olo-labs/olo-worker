/*
 * Copyright (c) 2026 Olo Labs. All rights reserved.
 */

/**
 * OLO annotations: feature and UI component metadata for bootstrap and plug-and-play loading.
 * <ul>
 *   <li>{@link org.olo.annotations.OloFeature} – feature (name, phase, applicableNodeTypes); processor generates {@code META-INF/olo-features.json}</li>
 *   <li>{@link org.olo.annotations.OloPlugin} – plugin (id, contractType, input/output parameters); processor generates {@code META-INF/olo-plugins.json} for drag-and-drop canvas and variable mapping</li>
 *   <li>{@link org.olo.annotations.OloUiComponent} – UI component (id, name, category); processor generates {@code META-INF/olo-ui-components.json}</li>
 *   <li>{@link org.olo.annotations.FeatureInfo} / {@link org.olo.annotations.PluginInfo} / {@link org.olo.annotations.UiComponentInfo} – JSON DTOs for loading</li>
 *   <li>{@link org.olo.annotations.ResourceCleanup} – lifecycle: {@link org.olo.annotations.ResourceCleanup#onExit()} for plugins/features to release resources at worker shutdown</li>
 * </ul>
 * Use {@code annotationProcessor project(':olo-annotations')} so the processor runs at compile time and produces the JSON resources.
 */
package org.olo.annotations;
