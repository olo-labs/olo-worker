/*
 * Copyright (c) 2026 Olo Labs. All rights reserved.
 */

package org.olo.db;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.function.Function;

/**
 * Abstraction over a database connection. The only interface the rest of the system uses for DB access.
 * Implementations (Postgres, MySQL, Cockroach, Citus) provide connections and execute callbacks.
 */
public interface DbClient {

  /**
   * Returns a connection from the pool. Caller must close it when done.
   */
  Connection getConnection() throws SQLException;

  /**
   * Executes an action with a connection. Connection is automatically closed after the action.
   */
  <T> T execute(Function<Connection, T> action);
}
