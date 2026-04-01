/*
 * Copyright (c) 2026 Olo Labs. All rights reserved.
 */

package org.olo.workflow.input.util;

import org.olo.workflow.input.model.Input;

import java.util.Map;

/**
 * Resolves an {@link Input} to a string value based on its storage.
 * LOCAL is supported; CACHE, FILE, S3 require external resolution (e.g. in activities).
 */
public final class InputResolver {

  private InputResolver() {}

  /**
   * Resolves the input to a string. For LOCAL storage returns the value; for other types throws.
   *
   * @param input the input (may be null)
   * @return the string value for LOCAL, or null if input is null
   * @throws IllegalStateException for CACHE, FILE, S3 or unknown type/mode
   */
  public static String resolveToString(Input input) {
    if (input == null) {
      return null;
    }
    Map<String, Object> storage = input.getStorage();
    if (storage == null) {
      throw new IllegalStateException("Input storage is missing for input=" + input.getName());
    }
    String type = getString(storage, "type");
    if (type == null || type.isBlank()) {
      throw new IllegalStateException("Input storage.type is missing for input=" + input.getName());
    }

    if ("LOCAL".equalsIgnoreCase(type)) {
      Object v = input.getValue();
      return v == null ? null : String.valueOf(v);
    }

    if ("CACHE".equalsIgnoreCase(type)) {
      String key = getString(storage, "key");
      String resource = getString(storage, "resource");
      throw new IllegalStateException("CACHE input resolution not implemented. resource=" + resource + " key=" + key);
    }

    if ("FILE".equalsIgnoreCase(type)) {
      String path = getString(storage, "path");
      String resource = getString(storage, "resource");
      throw new IllegalStateException("FILE input resolution not implemented. resource=" + resource + " path=" + path);
    }

    if ("S3".equalsIgnoreCase(type)) {
      throw new IllegalStateException("S3 input resolution not implemented");
    }

    throw new IllegalStateException("Unsupported input storage type=" + type + " for input=" + input.getName());
  }

  private static String getString(Map<String, Object> map, String key) {
    if (map == null) {
      return null;
    }
    Object v = map.get(key);
    return v == null ? null : String.valueOf(v);
  }
}
