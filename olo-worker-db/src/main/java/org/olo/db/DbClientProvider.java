/*
 * Copyright (c) 2026 Olo Labs. All rights reserved.
 */

package org.olo.db;

/**
 * Holder for the current {@link DbClient}. Set at bootstrap from config (e.g. db.type=postgres).
 * Repositories obtain the client via {@link #get()}.
 */
public final class DbClientProvider {

  private static volatile DbClient instance;

  private DbClientProvider() {}

  public static void set(DbClient client) {
    instance = client;
  }

  public static DbClient get() {
    return instance;
  }
}
