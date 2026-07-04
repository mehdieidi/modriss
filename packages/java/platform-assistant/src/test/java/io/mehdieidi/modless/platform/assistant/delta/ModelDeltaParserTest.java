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
  void expandsFeatureNameAliasToReferenceName() {
    ModelDelta delta =
        parser.parse(
            """
            {
              "intent": "MUTATION",
              "kind": "MODEL_DELTA",
              "message": "Connected actor to command.",
              "elements": [],
              "references": [
                {
                  "sourceId": "actor-1",
                  "featureName": "issuesCommands",
                  "targetId": "command-1"
                }
              ]
            }
            """);

    assertEquals(1, delta.references().size());
    assertEquals("issuesCommands", delta.references().get(0).referenceName());
  }

  @Test
  void expandsPluralReferenceTargets() {
    ModelDelta delta =
        parser.parse(
            """
            {
              "intent": "MUTATION",
              "kind": "MODEL_DELTA",
              "message": "Connected fan-out.",
              "elements": [],
              "references": [
                {
                  "sourceId": "policy",
                  "referenceName": "triggers",
                  "targetIds": ["command-a", "command-b"]
                }
              ]
            }
            """);

    assertEquals(2, delta.references().size());
    assertEquals("command-a", delta.references().get(0).targetId());
    assertEquals("command-b", delta.references().get(1).targetId());
  }

  @Test
  void treatsReferenceOwnerIdAsSourceIdAlias() {
    ModelDelta delta =
        parser.parse(
            """
            {
              "intent": "MUTATION",
              "kind": "MODEL_DELTA",
              "message": "Connected an alias reference.",
              "elements": [],
              "references": [
                {
                  "ownerId": "policy",
                  "referenceName": "triggers",
                  "targetId": "command-a"
                }
              ]
            }
            """);

    assertEquals("policy", delta.references().get(0).sourceId());
  }

  @Test
  void acceptsReferenceEvidenceIdsAsCitationMetadata() {
    ModelDelta delta =
        parser.parse(
            """
            {
              "intent": "MUTATION",
              "kind": "MODEL_DELTA",
              "message": "Connected with evidence.",
              "elements": [],
              "references": [
                {
                  "sourceId": "actor",
                  "referenceName": "issuesCommands",
                  "targetId": "command",
                  "evidenceIds": ["fact-1"]
                }
              ]
            }
            """);

    assertEquals("command", delta.references().get(0).targetId());
  }

  @Test
  void rejectsReferenceWithoutTarget() {
    assertThrows(
        PlatformException.class,
        () ->
            parser.parse(
                """
                {
                  "intent": "MUTATION",
                  "kind": "MODEL_DELTA",
                  "message": "Broken reference.",
                  "elements": [],
                  "references": [
                    {
                      "sourceId": "actor",
                      "referenceName": "issuesCommands"
                    }
                  ]
                }
                """));
  }

  @Test
  void treatsTaskKindIntentAsMutation() {
    ModelDelta delta =
        parser.parse(
            """
            {
              "intent": "TRANSFORM_SOURCE_TO_MODEL",
              "kind": "MODEL_DELTA",
              "message": "Modeled source evidence.",
              "elements": []
            }
            """);

    assertEquals(AssistantTurnPlan.Intent.MUTATION, delta.intent());
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
