/*
 * Copyright (c) 2026 Olo Labs. All rights reserved.
 */

package org.olo.worker.db.factory;

import org.olo.configuration.port.DbConnectionSettings;
import org.olo.configuration.port.TenantRegionRepositoryFactory;
import org.olo.configuration.region.TenantRegionRepository;
import org.olo.db.DbClient;
import org.olo.db.DbClientProvider;
import org.olo.db.repository.JdbcTenantRegionRepository;
import org.olo.worker.db.DbClientInitializerImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * Creates {@link TenantRegionRepository} using {@link DbClientProvider}. If no client is set,
 * initializes one from the given settings (default postgres) so callers can pass config without
 * calling the initializer explicitly.
 */
public final class JdbcTenantRegionRepositoryFactory implements TenantRegionRepositoryFactory {

  private static final Logger log = LoggerFactory.getLogger(JdbcTenantRegionRepositoryFactory.class);

  @Override
  public TenantRegionRepository create(DbConnectionSettings dbSettings) {
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
    if (client == null) {
      log.warn("DbClient not available; skipping tenant-region repository");
      return null;
    }
    return new JdbcTenantRegionRepository(client);
  }
}
