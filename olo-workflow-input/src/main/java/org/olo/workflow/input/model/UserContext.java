/*
 * Copyright (c) 2026 Olo Labs. All rights reserved.
 */

package org.olo.workflow.input.model;

import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

import java.util.List;

@Value
@Builder
@Jacksonized
public class UserContext {
  String userId;
  String groupId;
  List<String> roles;
  List<String> permissions;
  String sessionId;
  String callbackBaseUrl;
  String correlationId;
}
