package io.mehdieidi.modless.platform.assistant.delta;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.mehdieidi.modless.platform.assistant.domain.AssistantTurnPlan;
import io.mehdieidi.modless.platform.kernel.PlatformException;
import org.junit.jupiter.api.Test;

class ModelDeltaParserTest {

  private final ModelDeltaParser parser = new ModelDeltaParser(new ObjectMapper());

  @Test
  void parsesStrictModelDeltaJson() {
    ModelDelta delta =
        parser.parse(
            """
            {
              "intent": "MUTATION",
              "kind": "MODEL_DELTA",
              "message": "Created a slice.",
              "elements": [
                {
                  "localId": "fn",
                  "eClass": "Function",
                  "attributes": {"name": "Book order"},
                  "placement": {"ownerId": "root", "referenceName": "functions"},
                  "references": []
                }
              ],
              "references": [],
              "attributeUpdates": [],
              "deletions": [],
              "assumptions": ["REST is acceptable."]
            }
            """);

    assertEquals(AssistantTurnPlan.Intent.MUTATION, delta.intent());
    assertEquals(ModelDelta.Kind.MODEL_DELTA, delta.kind());
    assertEquals("fn", delta.elements().get(0).localId());
    assertEquals("functions", delta.elements().get(0).placement().referenceName());
    assertEquals("REST is acceptable.", delta.assumptions().get(0));
  }

  @Test
  void rejectsProseWrappedJsonInsteadOfSalvaging() {
    assertThrows(
        PlatformException.class,
        () ->
            parser.parse(
                """
                Here is the delta:
                {"intent":"MUTATION","kind":"MODEL_DELTA","message":"","elements":[]}
                """));
  }

  @Test
  void rejectsMissingRequiredRootFields() {
    assertThrows(
        PlatformException.class,
        () ->
            parser.parse(
                """
                {
                  "kind": "MODEL_DELTA",
                  "message": "Missing intent"
                }
                """));
  }

  @Test
  void rejectsUnsupportedFields() {
    assertThrows(
        PlatformException.class,
        () ->
            parser.parse(
                """
                {
                  "intent": "MUTATION",
                  "kind": "MODEL_DELTA",
                  "message": "No extras",
                  "elements": [],
                  "jsonPatch": []
                }
                """));
  }
}
