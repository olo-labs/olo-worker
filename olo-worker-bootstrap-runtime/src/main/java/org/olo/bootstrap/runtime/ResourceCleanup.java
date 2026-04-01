/*
 * Copyright (c) 2026 Olo Labs. All rights reserved.
 */

package org.olo.bootstrap.runtime;

/**
 * Shutdown hook: close plugin pools, flush event bus, close connections.
 * Registered by the bootstrap loader; invoked when the worker stops.
 */
public interface ResourceCleanup {
  void onExit();
}
