/*
 * Copyright (c) 2026 Olo Labs. All rights reserved.
 */

package org.olo.bootstrap.runtime;

/**
 * Read-only view of services registered during bootstrap. Used by {@link WorkerRuntime}
 * to expose execution infrastructure to the worker. Implementations are provided by
 * the bootstrap loader.
 */
public interface ServiceRegistry {
  <T> T get(Class<T> serviceType);
}
