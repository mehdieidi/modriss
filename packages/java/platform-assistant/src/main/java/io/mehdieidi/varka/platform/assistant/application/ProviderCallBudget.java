package io.mehdieidi.varka.platform.assistant.application;

import io.mehdieidi.varka.platform.assistant.domain.AssistantModelRole;
import io.mehdieidi.varka.platform.kernel.PlatformException;

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

  /**
   * Reserves one provider call for the given role.
   *
   * @throws PlatformException when the turn budget is exhausted
   */
  public static void consume(AssistantModelRole role) {
    if (role == AssistantModelRole.SUMMARIZER) {
      return;
    }
    State state = CURRENT.get();
    if (state == null) {
      return;
    }
    if (state.count >= state.limit) {
      throw new PlatformException(429, "Assistant provider call budget exceeded for this turn.");
    }
    CURRENT.set(new State(state.limit, state.count + 1));
  }

  private record State(int limit, int count) {}
}
