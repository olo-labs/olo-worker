/*
 * Copyright (c) 2026 Olo Labs. All rights reserved.
 */

package org.olo.db.repository;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.olo.configuration.snapshot.ConfigurationSnapshot;
import org.olo.configuration.snapshot.ConfigurationSnapshotRepository;
import org.olo.db.DbClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Loads {@link ConfigurationSnapshot} from DB table {@code olo_config_resource} for a region.
 * Repository layer: contains SQL; uses {@link DbClient} for execution.
 */
public final class ResourceSnapshotRepository extends DbRepository implements ConfigurationSnapshotRepository {

  private static final Logger log = LoggerFactory.getLogger(ResourceSnapshotRepository.class);
  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final String SQL = "SELECT resource_id, tenant_id, region, config_json FROM olo_config_resource WHERE region = ?";

  public ResourceSnapshotRepository(DbClient dbClient) {
    super(dbClient);
  }

  @Override
  public ConfigurationSnapshot load(String region) {
    String r = region == null || region.isEmpty() ? "default" : region;
    Map<String, String> global = new LinkedHashMap<>();
    Map<String, Map<String, String>> tenantConfig = new HashMap<>();

    try {
      getDbClient().execute(conn -> {
        try (PreparedStatement ps = conn.prepareStatement(SQL)) {
          ps.setString(1, r);
          try (ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
              String tenantId = rs.getString(2);
              String configJson = rs.getString(4);
              if (configJson == null || configJson.isBlank()) continue;
              Map<String, String> flat = parseConfigJson(configJson);
              if (tenantId == null || tenantId.isBlank()) {
                global.putAll(flat);
              } else {
                tenantConfig.computeIfAbsent(tenantId, k -> new LinkedHashMap<>()).putAll(flat);
              }
            }
          }
        } catch (SQLException e) {
          throw new RuntimeException(e);
        }
        return null;
      });
    } catch (Exception e) {
      log.warn("Failed to load olo_config_resource for region {}: {}", r, e.getMessage());
      return null;
    }

    long version = System.currentTimeMillis();
    Instant lastUpdated = Instant.now();
    return new ConfigurationSnapshot(r, version, lastUpdated, global, tenantConfig);
  }

  @SuppressWarnings("unchecked")
  static Map<String, String> parseConfigJson(String json) {
    try {
      Map<String, Object> m = MAPPER.readValue(json, new TypeReference<Map<String, Object>>() {});
      Map<String, String> out = new LinkedHashMap<>();
      flatten("", m, out);
      return out;
    } catch (Exception e) {
      return Map.of();
    }
  }

  private static void flatten(String prefix, Map<String, Object> m, Map<String, String> out) {
    for (Map.Entry<String, Object> e : m.entrySet()) {
      String key = prefix.isEmpty() ? e.getKey() : prefix + "." + e.getKey();
      Object v = e.getValue();
      if (v instanceof Map) {
        @SuppressWarnings("unchecked")
        Map<String, Object> nested = (Map<String, Object>) v;
        flatten(key, nested, out);
      } else if (v != null) {
        out.put(key, v.toString());
      }
    }
  }
}
