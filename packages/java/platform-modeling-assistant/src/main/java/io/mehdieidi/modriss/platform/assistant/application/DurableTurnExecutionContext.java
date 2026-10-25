package io.mehdieidi.modriss.platform.assistant.application;

import java.util.function.Supplier;

/**
 * Makes the durable turn identity available to nested staged workflows without changing chat IDs.
 */
public final class DurableTurnExecutionContext {
  private static final ThreadLocal<String> CURRENT = new ThreadLocal<>();
  private static final ThreadLocal<String> FULL_SOURCE = new ThreadLocal<>();

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

  /**
   * Keeps the full specification available to read-only analysis outside modeling slice windows.
   */
  public static <T> T with(String turnId, String fullSource, Supplier<T> invocation) {
    String previous = FULL_SOURCE.get();
    if (fullSource == null) FULL_SOURCE.remove();
    else FULL_SOURCE.set(fullSource);
    try {
      return with(turnId, invocation);
    } finally {
      if (previous == null) FULL_SOURCE.remove();
      else FULL_SOURCE.set(previous);
    }
  }

  public static String fullSource() {
    return FULL_SOURCE.get();
  }
}
