/*
 * Copyright (c) 2026 Olo Labs. All rights reserved.
 */

package org.olo.executiontree;

/**
 * Node type for the Execution Tree. Determines execution semantics.
 *
 * @see ExecutionTreeNode
 */
public enum NodeType {
  SEQUENCE,
  GROUP,
  PLUGIN,
  PLANNER,
  IF,
  SWITCH,
  CASE,
  ITERATOR,
  FORK,
  JOIN,
  TRY_CATCH,
  RETRY,
  SUB_PIPELINE,
  EVENT_WAIT,
  FILL_TEMPLATE,
  LLM_DECISION,
  TOOL_ROUTER,
  EVALUATION,
  REFLECTION
}
