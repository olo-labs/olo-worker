/*
 * Copyright (c) 2026 Olo Labs. All rights reserved.
 */

package org.olo.executiontree.config;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import org.olo.executiontree.inputcontract.InputContract;
import org.olo.executiontree.outputcontract.ResultMapping;
import org.olo.executiontree.scope.Scope;
import org.olo.executiontree.tree.ExecutionTreeNode;
import org.olo.executiontree.variableregistry.VariableRegistryEntry;

import java.util.List;

/**
 * Contract for pipeline definition used by worker and plan services.
 * Implemented by {@link org.olo.executiontree.PipelineDefinition}.
 * Type info is required so activity payload configJson can be deserialized from JSON.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.CLASS, property = "@class")
@JsonSubTypes(@JsonSubTypes.Type(org.olo.executiontree.PipelineDefinition.class))
public interface PipelineDefinition {

    String getName();
    Scope getScope();
    ExecutionTreeNode getExecutionTree();
    ExecutionType getExecutionType();
    InputContract getInputContract();
    List<VariableRegistryEntry> getVariableRegistry();
    List<ResultMapping> getResultMapping();
}
