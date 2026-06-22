package io.mehdieidi.modless.platform.assistant.planning;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.mehdieidi.modless.platform.assistant.domain.AssistantChoice;
import io.mehdieidi.modless.platform.assistant.domain.AssistantTurnPlan;
import io.mehdieidi.modless.platform.kernel.PlatformException;
import org.junit.jupiter.api.Test;

class AssistantTurnPlanParserTest {

  private final AssistantTurnPlanParser parser = new AssistantTurnPlanParser(new ObjectMapper());

  @Test
  void parsesMultipleStructuredQuestionsAndOperations() {
    AssistantTurnPlan plan =
        parser.parse(
            """
{"intent":"MUTATION","kind":"CLARIFICATION","message":"Choose the guarantees.","questions":[
  {"id":"delivery","prompt":"Delivery?","selectionMode":"SINGLE",
   "allowFreeText":false,"options":[
     {"id":"once","label":"At least once","description":"Deduplicate consumers."},
     {"id":"effective","label":"Effectively once","description":"Use idempotency."}]},
  {"id":"concerns","prompt":"Extra concerns?","selectionMode":"MULTIPLE",
   "allowFreeText":true,"options":[]}
],"operations":[]}
""");

    assertEquals(AssistantTurnPlan.Kind.CLARIFICATION, plan.kind());
    assertEquals(2, plan.questions().size());
    assertEquals(AssistantChoice.SelectionMode.MULTIPLE, plan.questions().get(1).selectionMode());
  }

  @Test
  void rejectsUnstructuredProviderText() {
    assertThrows(PlatformException.class, () -> parser.parse("I think you should add a function."));
  }

  @Test
  void extractsACompletePlanFromNoisyProviderOutput() {
    AssistantTurnPlan plan =
        parser.parse(
            """
Provider preamble with an incomplete brace { that must not swallow the plan.
{"intent":"INFORMATION","kind":"ANSWER","message":"The model is valid.","questions":[],"operations":[]}
trailing text
""");

    assertEquals(AssistantTurnPlan.Kind.ANSWER, plan.kind());
    assertEquals("The model is valid.", plan.message());
  }

  @Test
  void normalizesAddElementIdentityFromTheAttributePayload() {
    AssistantTurnPlan plan =
        parser.parse(
            """
            {"intent":"MUTATION","kind":"PATCH","message":"Add policy.","questions":[],
             "operations":[{"type":"ADD_ELEMENT","attributes":{"id":"policy-1",
             "eClass":"IdempotencyPolicy","displayName":"Policy"}}]}
            """);

    assertEquals("policy-1", plan.patch().operations().get(0).targetElementId());
    assertEquals("IdempotencyPolicy", plan.patch().operations().get(0).elementType());
  }

  @Test
  void acceptsAnswerPlansWithoutAnOperationsField() {
    AssistantTurnPlan plan =
        parser.parse(
            """
            {"intent":"INFORMATION","kind":"ANSWER","message":"APIs route to functions.",
            "questions":[]}
            """);

    assertEquals(AssistantTurnPlan.Intent.INFORMATION, plan.intent());
    assertEquals(AssistantTurnPlan.Kind.ANSWER, plan.kind());
    assertTrue(plan.patch().operations().isEmpty());
  }

  @Test
  void normalizesExplainSynonymsForInformationAnswers() {
    AssistantTurnPlan plan =
        parser.parse(
            """
            {"intent":"explanation","kind":"explain","message":"Stores back APIs.",
            "questions":[]}
            """);

    assertEquals(AssistantTurnPlan.Intent.INFORMATION, plan.intent());
    assertEquals(AssistantTurnPlan.Kind.ANSWER, plan.kind());
  }

  @Test
  void fallsBackToPatchOnlyPayloadWhenTurnEnvelopeIsMissing() {
    AssistantTurnPlan plan =
        parser.parse(
            """
            {"operations":[{"type":"ADD_ELEMENT","targetElementId":"svc-1",
            "elementType":"ServerlessService","attributes":"Orders"}]}
            """);

    assertEquals(AssistantTurnPlan.Kind.PATCH, plan.kind());
    assertEquals(1, plan.patch().operations().size());
  }
}
