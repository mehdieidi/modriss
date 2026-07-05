package io.mehdieidi.modless.platform.assistant.application;

/**
 * Per-turn limits for ModelDelta provider responses (thread-local, like {@link
 * ProviderCallBudget}).
 */
public final class ModelDeltaTurnContext {

  private static final ThreadLocal<Context> CURRENT = new ThreadLocal<>();

  private ModelDeltaTurnContext() {}

  /** Binds limits for the current assistant turn. */
  public static void bind(Context context) {
    CURRENT.set(context);
  }

  /** Returns the active context, or {@code null} when unbound. */
  public static Context current() {
    return CURRENT.get();
  }

  /** Clears the active context. */
  public static void clear() {
    CURRENT.remove();
  }

  /** Per-pass modeling limits for source-backed CIM turns. */
  public record Context(int maxElementsPerPass, int passNumber, boolean sourceBacked) {
    public Context {
      maxElementsPerPass = Math.max(0, maxElementsPerPass);
      passNumber = Math.max(1, passNumber);
    }
  }
}
