/*
 * Copyright (c) 2026 Olo Labs. All rights reserved.
 */

package org.olo.bootstrap.runtime;

/**
 * Builds {@link WorkerRuntime} from a {@link ServiceRegistry} populated during bootstrap.
 * The bootstrap loader runs discovery and wiring, registers services in a registry that
 * implements ServiceRegistry, then calls this builder to produce the runtime façade.
 */
public final class WorkerRuntimeBuilder {

  private final ServiceRegistry registry;

  public WorkerRuntimeBuilder(ServiceRegistry registry) {
    this.registry = registry;
  }

  public WorkerRuntime build() {
    return new DefaultWorkerRuntime(registry);
  }

  private static final class DefaultWorkerRuntime implements WorkerRuntime {
    private final ServiceRegistry registry;

    DefaultWorkerRuntime(ServiceRegistry registry) {
      this.registry = registry;
    }

    @Override
    public PluginExecutorFactory plugins() {
      return registry.get(PluginExecutorFactory.class);
    }

    @Override
    public FeatureRuntime features() {
      return registry.get(FeatureRuntime.class);
    }

    @Override
    public EventBus events() {
      return registry.get(EventBus.class);
    }

    @Override
    public ConnectionResolver connectionResolver() {
      return registry.get(ConnectionResolver.class);
    }

    @Override
    public SecretResolver secretResolver() {
      return registry.get(SecretResolver.class);
    }

    @Override
    public ResourceCleanup resourceCleanup() {
      return registry.get(ResourceCleanup.class);
    }

    @Override
    public ServiceRegistry services() {
      return registry;
    }
  }
}
