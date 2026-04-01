/*
 * Copyright (c) 2026 Olo Labs. All rights reserved.
 */

package org.olo.db.impl.postgres;

import org.olo.db.DbClient;
import org.olo.db.DbConfig;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.function.Function;

/**
 * {@link DbClient} implementation using HikariCP. Uses PostgreSQL driver; also compatible with
 * CockroachDB and Citus when using JDBC URL and same driver.
 */
public final class PostgresDbClient implements DbClient {

  private final HikariDataSource dataSource;

  public PostgresDbClient(DbConfig config) {
    HikariConfig hc = new HikariConfig();
    hc.setJdbcUrl(config.getUrl());
    hc.setUsername(config.getUsername());
    hc.setPassword(config.getPassword());
    hc.setMaximumPoolSize(config.getPoolSize());
    if (config.getDriver() != null && !config.getDriver().isEmpty()) {
      hc.setDriverClassName(config.getDriver());
    }
    this.dataSource = new HikariDataSource(hc);
  }

  @Override
  public Connection getConnection() throws SQLException {
    return dataSource.getConnection();
  }

  @Override
  public <T> T execute(Function<Connection, T> action) {
    try (Connection conn = dataSource.getConnection()) {
      return action.apply(conn);
    } catch (SQLException e) {
      throw new RuntimeException("DB execution failed", e);
    }
  }
}
