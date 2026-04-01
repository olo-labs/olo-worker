/*
 * Copyright (c) 2026 Olo Labs. All rights reserved.
 */

package org.olo.bootstrap.runtime;

/**
 * Resolves connection config by type and id. Used during execution.
 * Lives for the lifetime of the worker; registered by the bootstrap loader.
 */
public interface ConnectionResolver {
  // Contract: get(connectionType, connectionId, tenantId) → connection config
}
