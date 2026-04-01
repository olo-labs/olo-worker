/*
 * Copyright (c) 2026 Olo Labs. All rights reserved.
 */

package org.olo.bootstrap.runtime;

/**
 * Runtime façade exposed to the worker. Contains execution infrastructure services
 * that live for the lifetime of the worker. Built once at startup by the bootstrap loader.
 *
 * <p>Used during execution, not during bootstrap. The worker uses it for plugin execution,
 * features, events, connections, secrets, and shutdown.
 */
public interface WorkerRuntime {

  PluginExecutorFactory plugins();

  FeatureRuntime features();

  EventBus events();

  ConnectionResolver connectionResolver();

  SecretResolver secretResolver();

  ResourceCleanup resourceCleanup();

  /** Generic service lookup for extensions. */
  ServiceRegistry services();
}
