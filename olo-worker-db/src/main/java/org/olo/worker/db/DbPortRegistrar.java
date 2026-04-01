/*
 * Copyright (c) 2026 Olo Labs. All rights reserved.
 */

package org.olo.worker.db;

import org.olo.configuration.port.ConfigurationPortRegistry;
import org.olo.worker.db.factory.JdbcConfigurationSnapshotRepositoryFactory;
import org.olo.worker.db.factory.JdbcTenantRegionRepositoryFactory;

/**
 * Registers DB factory implementations and DbClient initializer into configuration port registry.
 */
public final class DbPortRegistrar {

  private DbPortRegistrar() {}

  public static void registerDefaults() {
    ConfigurationPortRegistry.registerDbClientInitializer(new DbClientInitializerImpl());
    ConfigurationPortRegistry.registerTenantRegionRepositoryFactory(new JdbcTenantRegionRepositoryFactory());
    ConfigurationPortRegistry.registerSnapshotRepositoryFactory(new JdbcConfigurationSnapshotRepositoryFactory());
  }
}
