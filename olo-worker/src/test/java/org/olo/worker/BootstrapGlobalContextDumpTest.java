/*
 * Copyright (c) 2026 Olo Labs. All rights reserved.
 */

package org.olo.worker;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.olo.bootstrap.loader.context.GlobalContextProvider;
import org.olo.configuration.Bootstrap;
import org.olo.configuration.Configuration;
import org.olo.worker.cache.CachePortRegistrar;
import org.olo.worker.db.DbPortRegistrar;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Integration test: runs full bootstrap (Redis + DB), then dumps the constructed global context
 * to a JSON file for debugging. Not included in the normal build cycle because it requires
 * external resources (Redis, DB). Run explicitly with:
 * <pre>
 *   ./gradlew :olo-worker:integrationTest
 * </pre>
 * Output file: {@code build/global-context-debug.json} or path from system property
 * {@code olo.bootstrap.dump.output}.
 * <p>
 * Redis key hierarchy (under configurable root, default {@code olo}):<br>
 * {@code <root>:config:meta}, {@code <root>:config:core},<br>
 * {@code <root>:config:pipelines:<region>}, {@code <root>:config:resources:<region>},<br>
 * {@code <root>:config:overrides:tenant:<tenantId>},<br>
 * {@code <root>:worker:tenant:region} (hash).
 */
@Tag("integration")
class BootstrapGlobalContextDumpTest {

  private static final String OUTPUT_PROP = "olo.bootstrap.dump.output";
  private static final String DEFAULT_OUTPUT = "build/global-context-debug.json";
  private static final String DEFAULT_DB_PASSWORD = "pgpass";
  private static final ObjectMapper JSON_MAPPER = new ObjectMapper();
  /** Top-level and nested keys removed before comparing expected vs actual (volatile or optional). */
  private static final Set<String> VOLATILE_FIELDS = new HashSet<>(Arrays.asList(
      "globalConfig",
      "snapshotId",
      "allowedTenantIds",
      "checksum",
      "coreVersion",
      "pipelinesVersion",
      "connectionsVersion",
      "regionalSettingsVersion",
      "coreLastUpdated",
      "generation",
      "lastUpdated",
      "updatedAt",
      "updated_at",
      // New optional/debug-only fields we don't assert on structurally
      "isDebugPipeline",
      "isDynamicPipeline",
      // Compiled tree shape varies with pipeline JSON; DB seed is source of truth
      "compiledPipeline"
  ));

  @AfterEach
  void tearDown() {
    Bootstrap.stopTenantRegionRefreshScheduler();
    Bootstrap.stopRefreshScheduler();
  }

  @Test
  void runBootstrapAndDumpGlobalContextToJson() throws Exception {
    Configuration bootstrapConfig = org.olo.configuration.Bootstrap.loadConfiguration();
    // Prepare DB schema and seed minimal data if a DB is configured.
    runEnsureSchemaScript(bootstrapConfig);

    DbPortRegistrar.registerDefaults();
    CachePortRegistrar.registerDefaults();
    Bootstrap.run(true);

    // Build in-memory global context dump via bootstrap-loader and serialize to JSON.
    var serializer = GlobalContextProvider.getSerializer();
    var payload = serializer.fullDump();
    String json = serializer.toJson(payload);

    // Compare against committed expected JSON (after stripping volatile fields).
    try (InputStream expectedIn = BootstrapGlobalContextDumpTest.class.getResourceAsStream("/global-context-expected.json")) {
      if (expectedIn == null) {
        throw new IllegalStateException("Expected global context JSON not found on classpath");
      }
      JsonNode expected = JSON_MAPPER.readTree(expectedIn);
      JsonNode actual = JSON_MAPPER.readTree(json);
      sanitize(expected);
      sanitize(actual);
      assertEquals(expected, actual, "Global context JSON does not match expected structure/content");
    }

    Path output = Paths.get(System.getProperty(OUTPUT_PROP, DEFAULT_OUTPUT));
    Path parent = output.getParent();
    if (parent != null && !Files.exists(parent)) {
      Files.createDirectories(parent);
    }
    Files.writeString(output, json);
    System.out.println("Global context dumped to: " + output.toAbsolutePath());
  }

  /**
   * Runs the ensure-schema script from configuration/debug. Creates tables if not exist
   * and inserts default data (idempotent). Loads from classpath first, then from
   * configuration/debug relative to project root.
   */
  private void runEnsureSchemaScript(Configuration config) throws Exception {
    String jdbcUrl = buildJdbcUrl(config);
    if (jdbcUrl.isEmpty()) {
      // No DB configured: nothing to ensure/seed.
      return;
    }

    String sql = loadEnsureSchemaSql();
    if (sql == null || sql.isBlank()) {
      throw new IllegalStateException("Could not load configuration/debug/ensure-schema.sql from classpath or path");
    }

    String user = config.get("olo.db.username", config.get("olo.db.user", "olo")).trim();
    if (user.isEmpty()) user = "olo";
    String password = config.get("olo.db.password", DEFAULT_DB_PASSWORD).trim();

    try (Connection conn = DriverManager.getConnection(jdbcUrl, user, password);
         Statement st = conn.createStatement()) {
      for (String statement : splitSqlStatements(sql)) {
        if (!statement.isBlank() && !statement.stripLeading().startsWith("--")) {
          st.execute(statement);
        }
      }
    }
  }

  private String loadEnsureSchemaSql() throws Exception {
    // 1) Classpath (e.g. when copied to src/test/resources by Gradle)
    try (InputStream in = BootstrapGlobalContextDumpTest.class.getResourceAsStream("/configuration/debug/ensure-schema.sql")) {
      if (in != null) {
        return new String(in.readAllBytes(), StandardCharsets.UTF_8);
      }
    }
    // 2) Path relative to working directory (configuration/debug or ../configuration/debug)
    Path p = Paths.get("configuration/debug/ensure-schema.sql");
    if (!Files.exists(p)) {
      p = Paths.get("../configuration/debug/ensure-schema.sql");
    }
    if (Files.exists(p)) {
      return Files.readString(p);
    }
    return null;
  }

  private static List<String> splitSqlStatements(String sql) {
    // Strip full-line comments so ";" inside a comment (e.g. "-- a; b") is not split
    String withoutLineComments = sql.lines()
        .filter(line -> !line.stripLeading().startsWith("--"))
        .collect(Collectors.joining("\n"));
    return Arrays.stream(withoutLineComments.split(";"))
        .map(String::trim)
        .filter(s -> !s.isEmpty())
        .collect(Collectors.toList());
  }

  private static void sanitize(JsonNode node) {
    if (node == null) {
      return;
    }
    if (node.isObject()) {
      ObjectNode obj = (ObjectNode) node;
      Iterator<String> fields = obj.fieldNames();
      // Collect to avoid concurrent modification
      java.util.List<String> toRemove = new java.util.ArrayList<>();
      while (fields.hasNext()) {
        String name = fields.next();
        if (VOLATILE_FIELDS.contains(name)) {
          toRemove.add(name);
        } else {
          sanitize(obj.get(name));
        }
      }
      for (String f : toRemove) {
        obj.remove(f);
      }
      // Normalize and canonicalize pipelineIds: drop queue prefixes and sort.
      if (obj.has("pipelineIds") && obj.get("pipelineIds").isArray()) {
        ArrayNode ids = (ArrayNode) obj.get("pipelineIds");
        java.util.List<String> values = new java.util.ArrayList<>();
        for (JsonNode idNode : ids) {
          values.add(shortPipelineId(idNode.asText()));
        }
        java.util.Collections.sort(values);
        ArrayNode sorted = JSON_MAPPER.createArrayNode();
        for (String v : values) {
          sorted.add(v);
        }
        obj.set("pipelineIds", sorted);
      }
      // Canonicalize globalContextTree keys and nested pipeline ids so they don't depend on queue naming.
      if (obj.has("globalContextTree") && obj.get("globalContextTree").isObject()) {
        ObjectNode trees = (ObjectNode) obj.get("globalContextTree");
        java.util.List<String> regionNames = new java.util.ArrayList<>();
        trees.fieldNames().forEachRemaining(regionNames::add);
        for (String region : regionNames) {
          JsonNode regionNode = trees.get(region);
          if (regionNode instanceof ObjectNode regionObj) {
            normalizeRegionPipelines(regionObj);
          }
        }
      }
    } else if (node.isArray()) {
      ArrayNode arr = (ArrayNode) node;
      for (JsonNode child : arr) {
        sanitize(child);
      }
    }
  }

  private static String shortPipelineId(String raw) {
    if (raw == null) return null;
    // Our new loader uses olo.<region>.<pipelineId>; keep only the last segment.
    if (raw.startsWith("olo.")) {
      int lastDot = raw.lastIndexOf('.');
      if (lastDot != -1 && lastDot + 1 < raw.length()) {
        return raw.substring(lastDot + 1);
      }
    }
    return raw;
  }

  /** Normalizes keys and nested ids under a single region entry of globalContextTree. */
  private static void normalizeRegionPipelines(ObjectNode regionObj) {
    java.util.List<String> pipelineKeys = new java.util.ArrayList<>();
    regionObj.fieldNames().forEachRemaining(pipelineKeys::add);
    for (String key : pipelineKeys) {
      JsonNode pipelineNode = regionObj.get(key);
      if (!(pipelineNode instanceof ObjectNode pipelineObj)) continue;
      String shortKey = shortPipelineId(key);
      // Update pipelineId field if present
      JsonNode pipelineIdNode = pipelineObj.get("pipelineId");
      if (pipelineIdNode != null && pipelineIdNode.isTextual()) {
        ((ObjectNode) pipelineObj).put("pipelineId", shortPipelineId(pipelineIdNode.asText()));
      }
      // Update compiledPipeline.id if present
      JsonNode compiled = pipelineObj.get("compiledPipeline");
      if (compiled instanceof ObjectNode compiledObj) {
        JsonNode idNode = compiledObj.get("id");
        if (idNode != null && idNode.isTextual()) {
          compiledObj.put("id", shortPipelineId(idNode.asText()));
        }
      }
      // If the key itself has a prefix, move it under the short key
      if (!shortKey.equals(key)) {
        regionObj.set(shortKey, pipelineObj);
        regionObj.remove(key);
      }
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
}
