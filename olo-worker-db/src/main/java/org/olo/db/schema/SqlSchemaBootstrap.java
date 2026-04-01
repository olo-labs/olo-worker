/*
 * Copyright (c) 2026 Olo Labs. All rights reserved.
 */

package org.olo.db.schema;

import org.olo.db.DbClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Applies bundled PostgreSQL DDL/DML scripts from {@code /db/schema/*.sql} in order during bootstrap
 * when {@code olo.db.schema.autoapply} is true (default). Safe for empty databases: uses
 * {@code CREATE TABLE IF NOT EXISTS} and idempotent inserts where defined.
 */
public final class SqlSchemaBootstrap {

  private static final Logger log = LoggerFactory.getLogger(SqlSchemaBootstrap.class);

  /** When true (default), run classpath SQL scripts after the pool is created. */
  public static final String AUTOAPPLY_KEY = "olo.db.schema.autoapply";

  private static final String RESOURCE_PREFIX = "db/schema/";

  private static final String[] SCRIPT_NAMES = {
      "002-olo_configuration_region.sql",
      "003-olo_configuration_tenant.sql",
      "004-olo_config_resource.sql",
      "005-olo_pipeline_template.sql",
      "006-olo_tenant_pipeline_override.sql",
      "007-seed_default_pipeline.sql",
      "008-seed_global_context_us_east.sql",
      "009-olo_capabilities.sql",
  };

  private SqlSchemaBootstrap() {}

  /**
   * Runs schema scripts when {@link #AUTOAPPLY_KEY} is true and {@code olo.db.type} is PostgreSQL-compatible.
   */
  public static void applyIfEnabled(Map<String, String> config, DbClient dbClient) {
    if (config == null || dbClient == null) {
      return;
    }
    if (!parseBool(config.get(AUTOAPPLY_KEY), true)) {
      log.info("SQL schema bootstrap skipped ({}=false)", AUTOAPPLY_KEY);
      return;
    }
    String dbType = get(config, "olo.db.type", "postgres").toLowerCase();
    if (!isPostgresCompatible(dbType)) {
      log.debug("SQL schema bootstrap skipped for db.type={}", dbType);
      return;
    }
    log.info("Applying SQL schema scripts from classpath {}", RESOURCE_PREFIX);
    dbClient.execute(conn -> {
      try {
        for (String name : SCRIPT_NAMES) {
          applyScript(conn, name);
        }
      } catch (SQLException e) {
        throw new RuntimeException("SQL schema bootstrap failed", e);
      }
      return null;
    });
  }

  private static boolean isPostgresCompatible(String dbType) {
    return "postgres".equals(dbType) || "cockroach".equals(dbType) || "citus".equals(dbType);
  }

  private static void applyScript(Connection conn, String fileName) throws SQLException {
    String sql = readClasspathScript(fileName);
    List<String> statements = splitStatements(sql);
    if (statements.isEmpty()) {
      log.warn("No executable statements in {}", fileName);
      return;
    }
    boolean prev = conn.getAutoCommit();
    conn.setAutoCommit(false);
    try (Statement st = conn.createStatement()) {
      for (String stmt : statements) {
        st.execute(stmt);
      }
      conn.commit();
      log.info("Applied SQL schema script: {} ({} statement(s))", fileName, statements.size());
    } catch (SQLException e) {
      conn.rollback();
      log.error("SQL schema script failed: {}", fileName, e);
      throw e;
    } finally {
      conn.setAutoCommit(prev);
    }
  }

  static List<String> splitStatements(String sql) {
    String stripped = stripFullLineComments(sql);
    String[] parts = stripped.split("(?m);\\s*\n");
    List<String> out = new ArrayList<>();
    for (String p : parts) {
      String t = p.trim();
      if (!t.isEmpty()) {
        out.add(t);
      }
    }
    return out;
  }

  /** Removes lines whose first non-whitespace characters are {@code --}. */
  static String stripFullLineComments(String sql) {
    return java.util.Arrays.stream(sql.split("\n", -1))
        .filter(line -> !line.trim().startsWith("--"))
        .collect(Collectors.joining("\n"));
  }

  private static String readClasspathScript(String fileName) {
    String path = RESOURCE_PREFIX + fileName;
    ClassLoader cl = SqlSchemaBootstrap.class.getClassLoader();
    InputStream in = cl != null ? cl.getResourceAsStream(path) : null;
    if (in == null) {
      in = SqlSchemaBootstrap.class.getResourceAsStream("/" + path);
    }
    if (in == null) {
      throw new IllegalStateException("Missing classpath resource: " + path);
    }
    try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
      return reader.lines().collect(Collectors.joining("\n"));
    } catch (IOException e) {
      throw new IllegalStateException("Failed to read " + path, e);
    }
  }

  private static boolean parseBool(String v, boolean defaultValue) {
    if (v == null || v.isBlank()) {
      return defaultValue;
    }
    return Boolean.parseBoolean(v.trim());
  }

  private static String get(Map<String, String> m, String key, String def) {
    String v = m.get(key);
    return v != null && !v.isBlank() ? v.trim() : def;
  }
}
