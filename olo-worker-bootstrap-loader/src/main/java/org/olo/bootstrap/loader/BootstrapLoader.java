/*
 * Copyright (c) 2026 Olo Labs. All rights reserved.
 */

package org.olo.bootstrap.loader;

import org.olo.bootstrap.loader.orchestrator.BootstrapOrchestrator;
import org.olo.bootstrap.loader.phases.BootstrapContributor;
import org.olo.bootstrap.loader.phases.BootstrapContext;
import org.olo.bootstrap.loader.phases.BootstrapState;
import org.olo.bootstrap.loader.registry.BootstrapServiceRegistry;
import org.olo.bootstrap.runtime.WorkerRuntime;
import org.olo.bootstrap.runtime.WorkerRuntimeBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Startup wiring: runs once when the worker starts. Handles environment resolution,
 * configuration loading, descriptor discovery, plugin/feature registration, pipeline
 * loading, registry wiring, and validation. Output is {@link WorkerRuntime}.
 *
 * <p>Example: {@code WorkerRuntime runtime = BootstrapLoader.initialize();}
 *
 * <p>Responsibilities: environment resolution, configuration loading, descriptor discovery,
 * plugin registration, feature registration, pipeline loading, registry wiring, validation.
 */
public final class BootstrapLoader {

  private static final Logger log = LoggerFactory.getLogger(BootstrapLoader.class);

  private BootstrapLoader() {}

  /**
   * Initializes the worker: runs bootstrap phases via contributors, then builds and returns
   * the runtime for starting Temporal (or local) workers. The returned runtime contains
   * execution infrastructure (PluginExecutorFactory, FeatureRuntime, EventBus, etc.) that
   * lives for the lifetime of the worker.
   *
   * @param contributors contributors to run (should be dependency-ordered)
   * @return the built WorkerRuntime; never null if no phase failed
   */
  public static WorkerRuntime initialize(List<? extends BootstrapContributor> contributors) {
    List<BootstrapContributor> list = contributors != null
        ? new ArrayList<>(contributors)
        : new ArrayList<>();
    BootstrapServiceRegistry registry = new DefaultBootstrapServiceRegistry();
    BootstrapContext context = new DefaultBootstrapContext(registry);
    BootstrapOrchestrator orchestrator = new BootstrapOrchestrator(list);
    orchestrator.run(context);
    return new WorkerRuntimeBuilder(registry).build();
  }

  private static final class DefaultBootstrapServiceRegistry implements BootstrapServiceRegistry {
    private final Map<Class<?>, Object> map = new ConcurrentHashMap<>();

    @Override
    public <T> void register(Class<T> serviceType, T implementation) {
      map.put(serviceType, implementation);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T get(Class<T> serviceType) {
      return (T) map.get(serviceType);
    }
  }

  private static final class DefaultBootstrapContext implements BootstrapContext {
    private final BootstrapServiceRegistry registry;
    private volatile BootstrapState state = BootstrapState.INITIALIZING;

    DefaultBootstrapContext(BootstrapServiceRegistry registry) {
      this.registry = registry;
    }

    @Override
    public BootstrapServiceRegistry getRegistry() {
      return registry;
    }

    @Override
    public BootstrapState getState() {
      return state;
    }
  }
}
