/*
 * Copyright (c) 2026 Olo Labs. All rights reserved.
 */

package org.olo.executiontree;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;
import java.util.Set;

public final class Scope implements org.olo.executiontree.scope.Scope {
  private final Map<String, Object> plugins;
  private final Set<String> features;

  @JsonCreator
  public Scope(@JsonProperty("plugins") Map<String, Object> plugins, @JsonProperty("featuresRaw") Set<String> features) {
    this.plugins = plugins == null ? Map.of() : Map.copyOf(plugins);
    this.features = features == null ? Set.of() : Set.copyOf(features);
  }

  public Map<String, Object> getPlugins() { return plugins; }
  public Set<String> getFeaturesRaw() { return features; }
  @Override
  public Set<org.olo.executiontree.scope.FeatureDef> getFeatures() {
    return features.stream().map(org.olo.executiontree.scope.SimpleFeatureDef::new).collect(java.util.stream.Collectors.toSet());
  }
  public boolean hasPlugin(String pluginId) { return plugins.containsKey(pluginId); }
  public boolean hasFeature(String featureId) { return features.contains(featureId); }
}
