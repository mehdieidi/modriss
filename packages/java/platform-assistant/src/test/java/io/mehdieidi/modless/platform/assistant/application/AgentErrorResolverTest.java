package io.mehdieidi.modless.platform.assistant.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.mehdieidi.modless.platform.assistant.domain.AgentError;
import io.mehdieidi.modless.platform.kernel.PlatformException;
import org.junit.jupiter.api.Test;

class AgentErrorResolverTest {

  @Test
  void mapsStaleRevisionFailures() {
    AgentError error =
        AgentErrorResolver.resolve(
            new PlatformException(409, "The model changed while I was planning."),
            "APPLY",
            "turn-1");

    assertEquals("STALE_REVISION", error.code());
    assertTrue(error.retryable());
    assertEquals(false, error.modelChanged());
  }

  @Test
  void mapsSchemaRejectedFailures() {
    AgentError error =
        AgentErrorResolver.resolve(
            new PlatformException(502, "ModelDelta was rejected by schema parsing."),
            "PLANNING",
            "turn-2");

    assertEquals("SCHEMA_REJECTED", error.code());
    assertEquals(false, error.retryable());
  }
}
