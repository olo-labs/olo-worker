/*
 * Copyright (c) 2026 Olo Labs. All rights reserved.
 */

package org.olo.db.schema;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OloWorkerSchemaTablesTest {

  @Test
  void allTables_listMatchesDropStatements() {
    List<String> drops = OloWorkerSchemaTables.dropTableStatements();
    assertEquals(OloWorkerSchemaTables.ALL.size(), drops.size());
    Set<String> seen = new HashSet<>();
    for (int i = 0; i < OloWorkerSchemaTables.ALL.size(); i++) {
      String name = OloWorkerSchemaTables.ALL.get(i);
      assertTrue(seen.add(name), "duplicate table name: " + name);
      assertEquals("DROP TABLE IF EXISTS " + name + " CASCADE", drops.get(i));
    }
  }

  @Test
  void allTables_coversKnownSchemaTables() {
    // Keep in sync with CREATE TABLE in db/schema/*.sql
    Set<String> expected =
        Set.of(
            "olo_configuration_region",
            "olo_configuration_tenant",
            "olo_config_resource",
            "olo_config_section",
            "olo_pipeline_template",
            "olo_tenant_pipeline_override",
            "olo_capabilities");
    assertEquals(expected, new HashSet<>(OloWorkerSchemaTables.ALL));
  }

  /**
   * Manual refresh: run with {@code -Dolo.schema.drop.test=true} and JDBC system properties, e.g.
   * {@code -Dolo.db.url=jdbc:postgresql://localhost:5432/olo -Dolo.db.user=olo -Dolo.db.password=...}
   * <p>
   * Drops all worker tables; run {@link SqlSchemaBootstrap} or migrations afterward to recreate.
   */
  @Test
  @EnabledIfSystemProperty(named = "olo.schema.drop.test", matches = "true")
  void dropAllTables_manualRefresh() throws Exception {
    String url = System.getProperty("olo.db.url");
    String user = System.getProperty("olo.db.user", "");
    String password = System.getProperty("olo.db.password", "");
    if (url == null || url.isBlank()) {
      throw new IllegalStateException("Set -Dolo.db.url=jdbc:postgresql://...");
    }
    try (Connection conn = DriverManager.getConnection(url, user, password)) {
      conn.setAutoCommit(false);
      try (Statement st = conn.createStatement()) {
        for (String sql : OloWorkerSchemaTables.dropTableStatements()) {
          st.execute(sql);
        }
      }
      conn.commit();
    }
  }
}
