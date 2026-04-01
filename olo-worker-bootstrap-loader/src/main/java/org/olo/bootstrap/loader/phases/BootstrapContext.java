/*
 * Copyright (c) 2026 Olo Labs. All rights reserved.
 */

package org.olo.bootstrap.loader.phases;

import org.olo.bootstrap.loader.registry.BootstrapServiceRegistry;

public interface BootstrapContext {
  BootstrapServiceRegistry getRegistry();
  BootstrapState getState();
}
