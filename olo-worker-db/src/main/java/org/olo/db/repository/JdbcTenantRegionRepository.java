/*
 * Copyright (c) 2026 Olo Labs. All rights reserved.
 */

package org.olo.db.repository;

import org.olo.configuration.Regions;
import org.olo.configuration.region.TenantRegionRepository;
import org.olo.db.DbClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Loads tenant→region mapping from DB table {@code olo_configuration_region}.
 * Repository layer: contains SQL; uses {@link DbClient} for execution.
 */
public final class JdbcTenantRegionRepository extends DbRepository implements TenantRegionRepository {

  private static final Logger log = LoggerFactory.getLogger(JdbcTenantRegionRepository.class);
  private static final String SELECT_ALL = "SELECT tenant_id, region FROM olo_configuration_region";
  private static final String UPDATE_REGION = "UPDATE olo_configuration_region SET region = ?, updated_at = CURRENT_TIMESTAMP WHERE tenant_id = ?";

  public JdbcTenantRegionRepository(DbClient dbClient) {
    super(dbClient);
  }

  @Override
  public Map<String, String> findAll() {
    Map<String, String> map = new LinkedHashMap<>();
    try {
      getDbClient().execute(conn -> {
        try (PreparedStatement ps = conn.prepareStatement(SELECT_ALL);
             ResultSet rs = ps.executeQuery()) {
          while (rs.next()) {
            String tenantId = rs.getString(1);
            String region = rs.getString(2);
            if (tenantId != null) {
              map.put(tenantId, region != null ? region.trim() : Regions.DEFAULT_REGION);
            }
          }
        } catch (SQLException e) {
          throw new RuntimeException(e);
        }
        return null;
      });
    } catch (Exception e) {
      log.warn("Failed to load tenant regions from DB: {}", e.getMessage());
    }
    return map;
  }

  @Override
  public void updateRegion(String tenantId, String region) {
    if (tenantId == null || tenantId.isBlank()) return;
    String r = (region == null || region.isBlank()) ? Regions.DEFAULT_REGION : region.trim();
    try {
      getDbClient().execute(conn -> {
        try (PreparedStatement ps = conn.prepareStatement(UPDATE_REGION)) {
          ps.setString(1, r);
          ps.setString(2, tenantId);
          ps.executeUpdate();
        } catch (SQLException e) {
          throw new RuntimeException(e);
        }
        return null;
      });
    } catch (Exception e) {
      log.warn("Failed to update tenant region in DB: tenantId={} region={} error={}", tenantId, r, e.getMessage());
      throw new RuntimeException(e);
    }
  }
}
