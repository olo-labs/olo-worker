/*
 * Copyright (c) 2026 Olo Labs. All rights reserved.
 */

package org.olo.bootstrap.loader.context;

import org.olo.bootstrap.loader.context.impl.GlobalContextImpl;
import org.olo.bootstrap.loader.context.impl.GlobalContextSerializerImpl;

/**
 * Provides the global context and serializer singletons.
 * Only the contract types ({@link GlobalContext}, {@link GlobalContextSerializer}) are exposed.
 */
public final class GlobalContextProvider {

  private static volatile GlobalContext instance;
  private static volatile GlobalContextSerializer serializer;

  private GlobalContextProvider() {}

  public static GlobalContext getGlobalContext() {
    if (instance == null) {
      synchronized (GlobalContextProvider.class) {
        if (instance == null) {
          instance = new GlobalContextImpl();
        }
      }
    }
    return instance;
  }

  public static GlobalContextSerializer getSerializer() {
    if (serializer == null) {
      synchronized (GlobalContextProvider.class) {
        if (serializer == null) {
          serializer = new GlobalContextSerializerImpl(getGlobalContext());
        }
      }
    }
    return serializer;
  }
}
