/*
 * Copyright (c) 2026 Olo Labs. All rights reserved.
 */

package org.olo.bootstrap.runtime;

/**
 * Factory for plugin executors. Used during execution to obtain an executor for a node.
 * Lives for the lifetime of the worker; registered by the bootstrap loader.
 */
public interface PluginExecutorFactory {
  // Contract: create executor for the given node/context; implementation in plugin module.
}
