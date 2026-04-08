/*
 * Copyright (c) 2026 Olo Labs. All rights reserved.
 */

package org.olo.configuration.chat;

/** Profile id with its resolved {@link ChatProfile}. */
public record NamedChatProfile(String id, ChatProfile profile) {}
