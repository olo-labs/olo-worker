/*
 * Copyright (c) 2026 Olo Labs. All rights reserved.
 */

package org.olo.worker;

import org.olo.configuration.Bootstrap;
import org.olo.configuration.Configuration;
import io.lettuce.core.RedisClient;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.List;

/**
 * Integration test: drops all integration-related DB tables and deletes all
 * olo configuration/worker Redis keys so a subsequent test run starts from a clean slate.
 * Requires Redis and DB to be configured. Run explicitly with:
 * <pre>
 *   ./gradlew :olo-worker:integrationTest
 * </pre>
 * (Include with other integration tests, or run this first for a fresh environment.)
 */
@Tag("integration")
class FreshIntegrationCleanupTest {

  private static final String[] TABLES_TO_DROP = {
      "olo_worker_configuration",
      "olo_config_resource",
      "olo_tenant_pipeline_override",
      "olo_pipeline_template",
      "olo_configuration_tenant",
      "olo_configuration_region",
      "olo_capabilities"
  };

  private static final String[] REDIS_KEY_PATTERNS = {
      // Use the configured Redis root key so non-"olo" prefixes are also cleaned.
      Bootstrap.loadConfiguration().get("olo.cache.root-key", "olo") + ":*"
  };

  @Test
  void dropAllTablesAndDeleteRedisKeysForFreshTest() throws Exception {
    Configuration config = Bootstrap.loadConfiguration();

    dropTablesIfConfigured(config);
    deleteRedisKeysIfConfigured(config);
  }

  private void dropTablesIfConfigured(Configuration config) throws Exception {
    String jdbcUrl = buildJdbcUrl(config);
    if (jdbcUrl.isEmpty()) return;

    String user = config.get("olo.db.username", config.get("olo.db.user", "olo")).trim();
    if (user.isEmpty()) user = "olo";
    String password = config.get("olo.db.password", "pgpass").trim();

    try (Connection conn = DriverManager.getConnection(jdbcUrl, user, password);
         Statement st = conn.createStatement()) {
      for (String table : TABLES_TO_DROP) {
        st.execute("DROP TABLE IF EXISTS " + table);
      }
    }
  }

  private void deleteRedisKeysIfConfigured(Configuration config) {
    String redisUri = buildRedisUri(config);
    if (redisUri.isEmpty()) return;

    RedisClient client = RedisClient.create(redisUri);
    try (StatefulRedisConnection<String, String> conn = client.connect()) {
      RedisCommands<String, String> cmd = conn.sync();
      for (String pattern : REDIS_KEY_PATTERNS) {
        List<String> keys = cmd.keys(pattern);
        if (!keys.isEmpty()) {
          cmd.del(keys.toArray(new String[0]));
        }
      }
    } finally {
      client.shutdown();
    }
  }

  private static String buildJdbcUrl(Configuration config) {
    String url = config.get("olo.db.url", "").trim();
    if (!url.isEmpty()) return url;
    String host = config.get("olo.db.host", "").trim();
    if (host.isEmpty()) return "";
    int port = config.getInteger("olo.db.port", 5432);
    String name = config.get("olo.db.name", "olo").trim();
    return "jdbc:postgresql://" + host + ":" + port + "/" + name;
  }

  private static String buildRedisUri(Configuration config) {
    String uri = config.get("olo.redis.uri", "").trim();
    if (!uri.isEmpty()) return uri;
    String host = config.get("olo.redis.host", "").trim();
    if (host.isEmpty()) return "";
    int port = config.getInteger("olo.redis.port", 6379);
    String pw = config.get("olo.redis.password", "").trim();
    return pw.isEmpty() ? "redis://" + host + ":" + port : "redis://:" + pw + "@" + host + ":" + port;
  }
}
