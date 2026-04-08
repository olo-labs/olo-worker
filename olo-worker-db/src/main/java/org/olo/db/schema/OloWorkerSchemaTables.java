/*
 * Copyright (c) 2026 Olo Labs. All rights reserved.
 */

package org.olo.db.schema;

import org.olo.db.DbClient;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

/**
 * Canonical list of tables owned by olo-worker-db schema scripts ({@code /db/schema/*.sql}).
 * Use {@link #dropTableStatements()} or {@link #dropAllTables(DbClient)} to wipe the database before
 * re-running {@link SqlSchemaBootstrap}.
 * <p>
 * <strong>Maintenance:</strong> When adding a new {@code CREATE TABLE} in schema SQL, append the table name here
 * (typically in reverse dependency order; {@code CASCADE} relaxes ordering when there are no cross-table FKs).
 */
public final class OloWorkerSchemaTables {

  /**
   * All tables created by the bundled schema. Order is suitable for {@code DROP TABLE ... CASCADE}.
   */
  public static final List<String> ALL =
      List.of(
          "olo_capabilities",
          "olo_tenant_pipeline_override",
          "olo_pipeline_template",
          "olo_config_section",
          "olo_config_resource",
          "olo_configuration_tenant",
          "olo_configuration_region");

  private OloWorkerSchemaTables() {}

  /** One statement per table: {@code DROP TABLE IF EXISTS &lt;name&gt; CASCADE}. */
  public static List<String> dropTableStatements() {
    return ALL.stream().map(t -> "DROP TABLE IF EXISTS " + t + " CASCADE").toList();
  }

  /**
   * Drops every table in {@link #ALL}. Intended for development / manual refresh after tests.
   *
   * @param dbClient non-null database client
   */
  public static void dropAllTables(DbClient dbClient) {
    if (dbClient == null) {
      throw new IllegalArgumentException("dbClient");
    }
    dbClient.execute(
        (Connection conn) -> {
          try (Statement st = conn.createStatement()) {
            for (String sql : dropTableStatements()) {
              st.execute(sql);
            }
          } catch (SQLException e) {
            throw new RuntimeException("dropAllTables failed", e);
          }
          return null;
        });
  }
}
