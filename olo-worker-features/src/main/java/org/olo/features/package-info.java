/*
 * Copyright (c) 2026 Olo Labs. All rights reserved.
 */

/**
 * OLO worker features: annotation-based feature registration and pre/post node call contracts.
 * <ul>
 *   <li>{@link org.olo.annotations.OloFeature} – annotate a class to register it as a feature (name, phase, applicableNodeTypes)</li>
 *   <li>Phase contracts (implement one or more per feature): {@link org.olo.features.PreNodeCall} (PRE), {@link org.olo.features.PostSuccessCall} (POST_SUCCESS), {@link org.olo.features.PostErrorCall} (POST_ERROR), {@link org.olo.features.FinallyCall} (FINALLY), {@link org.olo.features.PreFinallyCall} (PRE_FINALLY). Use <b>POST_SUCCESS</b> / <b>POST_ERROR</b> for heavy lifting (exception-prone, needs success vs error); use <b>FINALLY</b> / <b>PRE_FINALLY</b> for non–exception-prone code (logging, metrics, cleanup) to achieve the functionality.</li>
 *   <li>{@link org.olo.features.FeatureRegistry} – global registry; register feature instances and resolve by name or for a node</li>
 *   <li>{@link org.olo.features.NodeExecutionContext} – context passed to pre/post hooks</li>
 *   <li>{@link org.olo.annotations.ResourceCleanup} – implement {@link org.olo.annotations.ResourceCleanup#onExit()} to release resources at worker shutdown</li>
 * </ul>
 * Execution tree nodes (olo-worker-execution-tree) can list feature names in {@code features}; the runtime invokes registered features accordingly.
 */
package org.olo.features;
