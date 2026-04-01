/*
 * Copyright (c) 2026 Olo Labs. All rights reserved.
 */

package org.olo.bootstrap.loader.context;

import java.util.Map;

/**
 * Contract for serializing the global context (or sub-parts) to JSON-standard structures.
 * Implementation lives in this module; only this interface is exposed.
 */
public interface GlobalContextSerializer {

  /**
   * Full in-memory global context as a JSON-serializable map.
   * Does not include DB-backed data; callers may merge extra sections (e.g. dbGlobalContext) before {@link #toJson}.
   */
  Map<String, Object> fullDump();

  /**
   * Serializes a payload map to a JSON string (e.g. for writing dumps to file).
   *
   * @param payload JSON-serializable map (e.g. from {@link #fullDump()} or fullDump + extra)
   * @return indented JSON string
   */
  String toJson(Map<String, Object> payload) throws Exception;
}
