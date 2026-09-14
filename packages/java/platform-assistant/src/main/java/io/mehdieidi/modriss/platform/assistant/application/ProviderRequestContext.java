package io.mehdieidi.modriss.platform.assistant.application;

import io.mehdieidi.modriss.platform.kernel.PlatformException;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/** Bridges a durable turn's cancellation/deadline state to a blocking provider request. */
public final class ProviderRequestContext {
  private static final ThreadLocal<Context> CURRENT = new ThreadLocal<>();

  private ProviderRequestContext() {}

  public static <T> T with(
      BooleanSupplier cancellationRequested,
      Supplier<PlatformException> stopReason,
      Supplier<T> invocation) {
    Context previous = CURRENT.get();
    CURRENT.set(new Context(cancellationRequested, stopReason));
    try {
      return invocation.get();
    } finally {
      if (previous == null) CURRENT.remove();
      else CURRENT.set(previous);
    }
  }

  /** Throws when the active durable turn has been cancelled or reached its deadline. */
  public static void check() {
    Context context = CURRENT.get();
    if (context == null) return;
    if (context.cancellationRequested() != null && context.cancellationRequested().getAsBoolean()) {
      throw new PlatformException(499, "Assistant turn was canceled during the provider request.");
    }
    PlatformException stop = context.stopReason() == null ? null : context.stopReason().get();
    if (stop != null) throw stop;
  }

  private record Context(
      BooleanSupplier cancellationRequested, Supplier<PlatformException> stopReason) {}
}
