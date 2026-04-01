/*
 * Copyright (c) 2026 Olo Labs. All rights reserved.
 */

package org.olo.executiontree.load;

import org.olo.executiontree.config.PipelineConfiguration;

/** Stub: global config storage by tenant/queue. No-op when not wired. */
public final class GlobalConfigurationContext {
    private GlobalConfigurationContext() {}
    public static void put(String tenantKey, String queueName, PipelineConfiguration config) {}
}
