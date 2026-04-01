/*
 * Copyright (c) 2026 Olo Labs. All rights reserved.
 */

package org.olo.workflow.input.model;

import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

import java.util.Map;

/**
 * Runtime config: models, connections, credentials, resources.
 * Prefer credentialRef in connections (e.g. olo:resource:credential:openai:primary); worker resolves from vault/redis/secret manager.
 * Avoid sending raw secrets (apiKey, etc.) in the payload.
 */
@Value
@Builder
@Jacksonized
public class Runtime {
  RuntimeModels models;
  Map<String, Map<String, Object>> connections;
  RuntimeResources resources;
}
