package io.mehdieidi.varka.platform.assistant.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.mehdieidi.varka.platform.kernel.PlatformException;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

class ProviderRequestContextTest {

  @Test
  void exposesDurableCancellationToTheProviderBoundary() {
    AtomicBoolean cancelled = new AtomicBoolean(true);

    PlatformException failure =
        assertThrows(
            PlatformException.class,
            () ->
                ProviderRequestContext.with(
                    cancelled::get,
                    () -> null,
                    () -> {
                      ProviderRequestContext.check();
                      return null;
                    }));

    assertEquals(499, failure.status());
  }

  @Test
  void exposesDeadlineFailureToTheProviderBoundary() {
    PlatformException deadline = new PlatformException(504, "deadline exceeded");

    PlatformException failure =
        assertThrows(
            PlatformException.class,
            () ->
                ProviderRequestContext.with(
                    () -> false,
                    () -> deadline,
                    () -> {
                      ProviderRequestContext.check();
                      return null;
                    }));

    assertEquals(504, failure.status());
  }
}
