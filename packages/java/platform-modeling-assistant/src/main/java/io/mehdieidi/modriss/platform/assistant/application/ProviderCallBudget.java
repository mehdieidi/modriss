package io.mehdieidi.modriss.platform.assistant.application;

import io.mehdieidi.modriss.platform.kernel.PlatformException;

/** Per-turn provider call budget enforced across assistant LLM invocations. */
public final class ProviderCallBudget {

  private static final ThreadLocal<State> CURRENT = new ThreadLocal<>();

  private ProviderCallBudget() {}

  /** Binds a provider call budget for the current assistant turn thread. */
  public static void bind(int limit) {
    CURRENT.set(new State(Math.max(1, limit), 0));
  }

  /** Clears the bound budget for the current thread. */
  public static void clear() {
    CURRENT.remove();
  }

  /** Returns whether a caller already owns the enclosing turn budget. */
  public static boolean isBound() {
    return CURRENT.get() != null;
  }

  /** Returns consumed provider calls for the active turn, or zero when unbound. */
  public static int count() {
    State state = CURRENT.get();
    return state == null ? 0 : state.count;
  }

  /** Returns whether at least one provider call remains in the active budget. */
  public static boolean hasRemaining() {
    State state = CURRENT.get();
    return state == null || state.count < state.limit;
  }

  /** Returns whether a failure was caused by this turn's provider-call budget. */
  public static boolean isExceeded(Throwable failure) {
    for (Throwable current = failure; current != null; current = current.getCause()) {
      if (current instanceof PlatformException platform
          && platform.status() == 429
          && current.getMessage() != null
          && current
              .getMessage()
              .toLowerCase(java.util.Locale.ROOT)
              .replace('-', ' ')
              .contains("provider call budget")) {
        return true;
      }
    }
    return false;
  }

  /** Creates the canonical failure used when no provider reservation remains. */
  public static PlatformException exceeded() {
    return new PlatformException(429, "Assistant provider call budget exceeded for this turn.");
  }

  /**
   * Reserves one provider call.
   *
   * @throws PlatformException when the turn budget is exhausted
   */
  public static void consume() {
    State state = CURRENT.get();
    if (state == null) {
      return;
    }
    if (state.count >= state.limit) {
      throw exceeded();
    }
    CURRENT.set(new State(state.limit, state.count + 1));
  }

  private record State(int limit, int count) {}
}
