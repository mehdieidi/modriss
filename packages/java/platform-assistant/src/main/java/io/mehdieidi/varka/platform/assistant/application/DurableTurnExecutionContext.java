package io.mehdieidi.varka.platform.assistant.application;

import java.util.function.Supplier;

/**
 * Makes the durable turn identity available to nested staged workflows without changing chat IDs.
 */
public final class DurableTurnExecutionContext {
  private static final ThreadLocal<String> CURRENT = new ThreadLocal<>();

  private DurableTurnExecutionContext() {}

  public static <T> T with(String turnId, Supplier<T> invocation) {
    String previous = CURRENT.get();
    CURRENT.set(turnId);
    try {
      return invocation.get();
    } finally {
      if (previous == null) CURRENT.remove();
      else CURRENT.set(previous);
    }
  }

  public static String turnId() {
    return CURRENT.get();
  }
}
