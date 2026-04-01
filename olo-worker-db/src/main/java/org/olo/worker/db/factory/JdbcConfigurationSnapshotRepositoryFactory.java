/*
 * Copyright (c) 2026 Olo Labs. All rights reserved.
 */

package org.olo.worker.db.factory;

import org.olo.configuration.port.ConfigurationSnapshotRepositoryFactory;
import org.olo.configuration.port.DbConnectionSettings;
import org.olo.configuration.snapshot.ConfigurationSnapshotRepository;
import org.olo.db.DbClient;
import org.olo.db.DbClientProvider;
import org.olo.worker.db.DbClientInitializerImpl;
import org.olo.db.repository.ResourceSnapshotRepository;

import java.util.Map;

/**
 * Creates {@link ConfigurationSnapshotRepository} using {@link DbClientProvider}. If no client is set,
 * initializes one from the given settings (default postgres).
 */
public final class JdbcConfigurationSnapshotRepositoryFactory implements ConfigurationSnapshotRepositoryFactory {

  @Override
  public ConfigurationSnapshotRepository create(DbConnectionSettings dbSettings) {
    if (dbSettings == null || !dbSettings.isConfigured()) return null;
    DbClient client = DbClientProvider.get();
    if (client == null) {
      Map<String, String> config = Map.of(
          "olo.db.url", dbSettings.jdbcUrl(),
          "olo.db.username", dbSettings.username(),
          "olo.db.password", dbSettings.password(),
          "olo.db.pool.size", String.valueOf(dbSettings.poolSize()),
          "olo.db.type", "postgres");
      new DbClientInitializerImpl().initializeDbClient(config);
      client = DbClientProvider.get();
    }
    if (client == null) return null;
    return new ResourceSnapshotRepository(client);
  }
}
