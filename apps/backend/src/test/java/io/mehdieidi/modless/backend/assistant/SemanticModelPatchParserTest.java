package io.mehdieidi.modless.backend.assistant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.mehdieidi.modless.platform.core.PlatformException;
import org.junit.jupiter.api.Test;

class SemanticModelPatchParserTest {

  private final SemanticModelPatchParser parser = new SemanticModelPatchParser(new ObjectMapper());

  @Test
  void parsesFreeFormAttributesFromJsonFence() {
    SemanticModelPatch patch =
        parser.parse(
            """
            ```json
            {"operations":[{"type":"ADD_ELEMENT","targetElementId":"api-orders",
            "elementType":"Api","attributes":{"name":"Orders API"},
            "sourceElementId":null,"referenceName":null}]}
            ```
            """);

    assertEquals(1, patch.operations().size());
    assertEquals("Orders API", patch.operations().get(0).attributes().path("name").asText());
  }

  @Test
  void rejectsInvalidPlannerContent() {
    PlatformException exception =
        assertThrows(PlatformException.class, () -> parser.parse("not a semantic patch"));

    assertEquals(502, exception.status());
  }

  @Test
  void normalizesGeminiAttributeMapIntoCanonicalOperations() {
    SemanticModelPatch patch =
        parser.parse(
            """
            {"operations":[{"type":"SET_ATTRIBUTE","targetElementId":"api-orders",
            "attributes":{"name":"Orders API","basePath":"/orders"}}]}
            """);

    assertEquals(2, patch.operations().size());
    assertEquals("name", patch.operations().get(0).referenceName());
    assertEquals("Orders API", patch.operations().get(0).attributes().asText());
    assertEquals("basePath", patch.operations().get(1).referenceName());
    assertEquals("/orders", patch.operations().get(1).attributes().asText());
  }

  @Test
  void normalizesGeminiAttributeAliases() {
    SemanticModelPatch patch =
        parser.parse(
            """
            {"operations":[{"operation":"SET_ATTRIBUTE","targetElementId":"api-orders",
            "attributeName":"name","value":"Orders API"}]}
            """);

    assertEquals(SemanticModelPatch.OperationType.SET_ATTRIBUTE, patch.operations().get(0).type());
    assertEquals("name", patch.operations().get(0).referenceName());
    assertEquals("Orders API", patch.operations().get(0).attributes().asText());
  }
}
