/*
 * Copyright (c) 2026 Olo Labs. All rights reserved.
 */

package org.olo.bootstrap.loader.phases;

public interface BootstrapPhaseExecutor {
  void execute(BootstrapPhase phase, BootstrapContext context);
}
