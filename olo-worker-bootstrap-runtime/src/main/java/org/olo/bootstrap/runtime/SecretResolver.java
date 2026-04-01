/*
 * Copyright (c) 2026 Olo Labs. All rights reserved.
 */

package org.olo.bootstrap.runtime;

/**
 * Resolves secrets by key/scope. Used during execution.
 * Lives for the lifetime of the worker; registered by the bootstrap loader.
 */
public interface SecretResolver {
  // Contract: resolve(secretKey, scope) → secret value
}
