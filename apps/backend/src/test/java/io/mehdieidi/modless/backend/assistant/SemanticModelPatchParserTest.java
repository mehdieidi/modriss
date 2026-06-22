package io.mehdieidi.modless.backend.assistant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.mehdieidi.modless.platform.assistant.domain.SemanticModelPatch;
import io.mehdieidi.modless.platform.assistant.patch.SemanticModelPatchParser;
import io.mehdieidi.modless.platform.kernel.PlatformException;
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

  @Test
  void extractsAndNormalizesCommonProviderPatchAliases() {
    SemanticModelPatch patch =
        parser.parse(
            """
            Here is the patch:
            [
              {"operation":"create_element","body":{"id":"api-users","type":"Api",
                "name":"Users API"}},
              {"op":"updateAttribute","element":"api-users","attribute":"basePath",
                "value":"/users"}
            ]
            Apply it after validation.
            """);

    assertEquals(2, patch.operations().size());
    assertEquals(SemanticModelPatch.OperationType.ADD_ELEMENT, patch.operations().get(0).type());
    assertEquals("api-users", patch.operations().get(0).targetElementId());
    assertEquals("Users API", patch.operations().get(0).attributes().path("name").asText());
    assertEquals(SemanticModelPatch.OperationType.SET_ATTRIBUTE, patch.operations().get(1).type());
    assertEquals("basePath", patch.operations().get(1).referenceName());
  }

  @Test
  void parsesJsonFollowedByProviderCommentary() {
    SemanticModelPatch patch =
        parser.parse(
            """
            {"operations":[{"type":"ADD_ELEMENT","targetElementId":"api-users",
            "elementType":"Api","attributes":{"name":"Users API"}}]}
            I created the requested model changes.
            """);

    assertEquals(1, patch.operations().size());
    assertEquals("api-users", patch.operations().get(0).targetElementId());
  }

  @Test
  void skipsNonPatchJsonAndParsesWrappedSemanticPatch() {
    SemanticModelPatch patch =
        parser.parse(
            """
            Planning metadata: {"status":"ready"}
            {"semanticPatch":{"operations":[{"type":"ADD_ELEMENT",
            "targetElementId":"api-users","elementType":"Api",
            "attributes":{"name":"Users API"}}]}}
            """);

    assertEquals(1, patch.operations().size());
    assertEquals("Users API", patch.operations().get(0).attributes().path("name").asText());
  }

  @Test
  void toleratesCommonModelGeneratedJsonFormattingMistakes() {
    SemanticModelPatch patch =
        parser.parse(
            """
            {
              'operations': [
                // Add the requested API.
                {'type':'ADD_ELEMENT','targetElementId':'api-users',
                 'elementType':'Api','attributes':{'name':'Users API'},},
              ],
            }
            """);

    assertEquals(1, patch.operations().size());
    assertEquals("api-users", patch.operations().get(0).targetElementId());
  }

  @Test
  void collectsStandaloneJsonLineOperations() {
    SemanticModelPatch patch =
        parser.parse(
            """
            {"operation":"create_element","body":{"id":"api-users","type":"Api",
              "name":"Users API"}}
            {"operation":"create_element","body":{"id":"api-books","type":"Api",
              "name":"Books API"}}
            """);

    assertEquals(2, patch.operations().size());
    assertEquals("api-users", patch.operations().get(0).targetElementId());
    assertEquals("api-books", patch.operations().get(1).targetElementId());
  }
}
