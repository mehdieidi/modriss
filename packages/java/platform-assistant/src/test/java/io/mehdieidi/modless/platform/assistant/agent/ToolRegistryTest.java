package io.mehdieidi.modless.platform.assistant.agent;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.mehdieidi.modless.platform.kernel.PlatformException;
import org.junit.jupiter.api.Test;

class ToolRegistryTest {

  @Test
  void exposesPlanToolSurfaceAndRejectsUnknownTools() {
    ToolRegistry registry = new ToolRegistry(8, 2, 3);

    assertEquals("getTypeContract", registry.require("get_type_contract").implementationName());
    assertEquals("findReferenceOptions", registry.require("find_reference").implementationName());
    assertTrue(registry.require("list_model_elements").readOnly());
    assertThrows(PlatformException.class, () -> registry.require("delete_everything"));
  }

  @Test
  void enforcesTurnStepAndAgentStepBudgets() {
    ToolRegistry registry = new ToolRegistry(2, 1, 2);

    assertDoesNotThrow(() -> registry.checkBudget("retrieve_context", 0, 0, 1));
    assertThrows(PlatformException.class, () -> registry.checkBudget("retrieve_context", 0, 1, 1));
    assertThrows(PlatformException.class, () -> registry.checkBudget("retrieve_context", 2, 0, 1));
    assertThrows(PlatformException.class, () -> registry.checkBudget("retrieve_context", 0, 0, 3));
  }
}
