/*
 * Copyright (c) 2026 Olo Labs. All rights reserved.
 */

package org.olo.db;

/**
 * Database connection configuration. Used to create a {@link DbClient}.
 */
public final class DbConfig {

  private final String url;
  private final String username;
  private final String password;
  private final int poolSize;
  private final String driver;

  public DbConfig(String url, String username, String password, int poolSize, String driver) {
    this.url = url == null ? "" : url.trim();
    this.username = username == null ? "" : username.trim();
    this.password = password == null ? "" : password;
    this.poolSize = poolSize <= 0 ? 5 : poolSize;
    this.driver = driver == null ? "" : driver.trim();
  }

  public String getUrl() { return url; }
  public String getUsername() { return username; }
  public String getPassword() { return password; }
  public int getPoolSize() { return poolSize; }
  public String getDriver() { return driver; }

  public boolean isConfigured() {
    return !url.isEmpty();
  }
}
