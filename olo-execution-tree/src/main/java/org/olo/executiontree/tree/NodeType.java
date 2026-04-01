/*
 * Copyright (c) 2026 Olo Labs. All rights reserved.
 */

package org.olo.executiontree.tree;

/**
 * Node type for the runtime execution tree (protocol/worker).
 * Mirrors {@link org.olo.executiontree.NodeType} for the tree API.
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
  REFLECTION,
  UNKNOWN
  ;

  /** Alias for {@link #name()} for feature resolution. */
  public String getTypeName() { return name(); }
}
