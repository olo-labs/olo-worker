/*
 * Copyright (c) 2026 Olo Labs. All rights reserved.
 */

package org.olo.db.repository;

import org.olo.db.DbClient;

/**
 * Base for repository layer: holds {@link DbClient} and runs SQL via {@link DbClient#execute}.
 * Repositories contain queries; the client abstracts the database implementation.
 */
public abstract class DbRepository {

  private final DbClient dbClient;

  protected DbRepository(DbClient dbClient) {
    this.dbClient = dbClient;
  }

  protected DbClient getDbClient() {
    return dbClient;
  }
}
