/*
 * Copyright (c) 2026 Olo Labs. All rights reserved.
 */

package org.olo.db.schema;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SqlSchemaBootstrapTest {

  @Test
  void splitStatements_splitsOnSemicolonNewline() {
    String sql =
        "-- header\n"
            + "CREATE TABLE IF NOT EXISTS t (id INT);\n"
            + "INSERT INTO t VALUES (1);\n";
    List<String> parts = SqlSchemaBootstrap.splitStatements(sql);
    assertEquals(2, parts.size());
    assertTrue(parts.get(0).contains("CREATE TABLE"));
    assertTrue(parts.get(1).contains("INSERT INTO"));
  }

  @Test
  void stripFullLineComments_removesDashDashLines() {
    String sql = "-- drop\nCREATE TABLE t (x INT);";
    String stripped = SqlSchemaBootstrap.stripFullLineComments(sql);
    assertTrue(stripped.contains("CREATE TABLE"));
    assertTrue(!stripped.contains("drop"));
  }
}
