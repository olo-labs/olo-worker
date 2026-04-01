/*
 * Copyright (c) 2026 Olo Labs. All rights reserved.
 */

package org.olo.bootstrap.loader.validation;

/** Thrown when pipeline config fails version or contract validation. */
public class ConfigIncompatibleException extends Exception {
    public ConfigIncompatibleException(String message) { super(message); }
    public ConfigIncompatibleException(String message, Throwable cause) { super(message, cause); }
}
