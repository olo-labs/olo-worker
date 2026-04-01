/*
 * Copyright (c) 2026 Olo Labs. All rights reserved.
 */

package org.olo.worker.db;

import org.olo.configuration.port.DbClientInitializer;
import org.olo.db.DbClient;
import org.olo.db.DbClientProvider;
import org.olo.db.DbConfig;
import org.olo.db.impl.postgres.PostgresDbClient;
import org.olo.db.schema.SqlSchemaBootstrap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * Creates {@link DbClient} from config (olo.db.url, olo.db.type, etc.) and sets {@link DbClientProvider}.
 * Supports db.type=postgres (default), cockroach, citus (same driver as Postgres); mysql can be added.
 */
public final class DbClientInitializerImpl implements DbClientInitializer {

  private static final Logger log = LoggerFactory.getLogger(DbClientInitializerImpl.class);
  private static final String DEFAULT_DRIVER_POSTGRES = "org.postgresql.Driver";
  private static final String DEFAULT_DRIVER_MYSQL = "com.mysql.cj.jdbc.Driver";

  @Override
  public void initializeDbClient(Map<String, String> config) {
    if (config == null || config.isEmpty()) return;
    String url = get(config, "olo.db.url", "").trim();
    if (url.isEmpty()) {
      url = buildJdbcUrl(config);
    }
    if (url.isEmpty()) return;

    String username = get(config, "olo.db.username", get(config, "olo.db.user", "")).trim();
    String password = get(config, "olo.db.password", "");
    int poolSize = parseInt(config.get("olo.db.pool.size"), 5);
    String dbType = get(config, "olo.db.type", "postgres").trim().toLowerCase();

    DbConfig dbConfig = new DbConfig(url, username, password, poolSize, driverFor(dbType));
    DbClient client = createClient(dbType, dbConfig);
    if (client != null) {
      DbClientProvider.set(client);
      log.info("DbClient initialized: db.type={}", dbType);
      SqlSchemaBootstrap.applyIfEnabled(config, client);
    }
  }

  private static String driverFor(String dbType) {
    switch (dbType) {
      case "mysql":
        return DEFAULT_DRIVER_MYSQL;
      case "postgres":
      case "cockroach":
      case "citus":
      default:
        return DEFAULT_DRIVER_POSTGRES;
    }
  }

  private static DbClient createClient(String dbType, DbConfig dbConfig) {
    switch (dbType) {
      case "postgres":
      case "cockroach":
      case "citus":
        return new PostgresDbClient(dbConfig);
      case "mysql":
        try {
          Class<?> clazz = Class.forName("org.olo.db.impl.mysql.MysqlDbClient");
          return (DbClient) clazz.getConstructor(DbConfig.class).newInstance(dbConfig);
        } catch (Exception e) {
          log.warn("MySQL driver not on classpath; add olo-worker-db-mysql or use db.type=postgres: {}", e.getMessage());
          return null;
        }
      default:
        log.warn("Unknown db.type={}, using postgres", dbType);
        return new PostgresDbClient(dbConfig);
    }
  }

  private static String buildJdbcUrl(Map<String, String> config) {
    String host = get(config, "olo.db.host", "").trim();
    if (host.isEmpty()) return "";
    int port = parseInt(config.get("olo.db.port"), 5432);
    String name = get(config, "olo.db.name", "olo").trim();
    String dbType = get(config, "olo.db.type", "postgres").trim().toLowerCase();
    if ("mysql".equals(dbType)) {
      return "jdbc:mysql://" + host + ":" + port + "/" + name;
    }
    return "jdbc:postgresql://" + host + ":" + port + "/" + name;
  }

  private static String get(Map<String, String> m, String key, String def) {
    String v = m.get(key);
    return v != null ? v.trim() : def;
  }

  private static int parseInt(String v, int def) {
    if (v == null || v.isBlank()) return def;
    try {
      return Integer.parseInt(v.trim());
    } catch (NumberFormatException e) {
      return def;
    }
  }
}
