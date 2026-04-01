/*
 * Copyright (c) 2026 Olo Labs. All rights reserved.
 */

package org.olo.internal.plugins.consensus;

import org.olo.plugin.ContractType;
import org.olo.plugin.PluginProvider;

/**
 * Registers the consensus subtree creator for the Multi-Model Consensus use case.
 * Plugin id must match pipeline scope and PLANNER node subtreeCreatorPluginRef (e.g. "consensus_subtree_creator").
 */
public final class ConsensusPluginProvider implements PluginProvider {

    private static final String PLUGIN_ID = "consensus_subtree_creator";
    private final ConsensusSubtreeCreatorPlugin plugin = new ConsensusSubtreeCreatorPlugin();

    @Override
    public String getPluginId() {
        return PLUGIN_ID;
    }

    @Override
    public String getContractType() {
        return ContractType.SUBTREE_CREATOR;
    }

    @Override
    public org.olo.plugin.ExecutablePlugin getPlugin() {
        return plugin;
    }
}
