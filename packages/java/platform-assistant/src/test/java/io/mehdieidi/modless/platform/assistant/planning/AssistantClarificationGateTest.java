package io.mehdieidi.modless.platform.assistant.planning;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.mehdieidi.modless.platform.assistant.domain.AssistantChoice;
import io.mehdieidi.modless.platform.assistant.domain.AssistantTurnPlan;
import io.mehdieidi.modless.platform.assistant.domain.SemanticModelPatch;
import java.util.List;
import org.junit.jupiter.api.Test;

class AssistantClarificationGateTest {

  private final AssistantClarificationGate gate = new AssistantClarificationGate();

  @Test
  void blocksTrivialIdentifierQuestionsForMutations() {
    AssistantTurnPlan plan =
        new AssistantTurnPlan(
            AssistantTurnPlan.Intent.MUTATION,
            AssistantTurnPlan.Kind.CLARIFICATION,
            "Need an id",
            List.of(
                new AssistantChoice(
                    "id-format",
                    "What stable element id should I use?",
                    AssistantChoice.SelectionMode.SINGLE,
                    List.of(
                        new AssistantChoice.Option("uuid", "UUID", "Use UUID."),
                        new AssistantChoice.Option("slug", "Slug", "Use slug.")),
                    false)),
            new SemanticModelPatch(List.of()));

    AssistantTurnPlan gated = gate.apply(plan);

    assertEquals(AssistantTurnPlan.Kind.PATCH, gated.kind());
  }

  @Test
  void keepsDomainArchitectureAndRuntimeQuestionsForLlmOrUserResolution() {
    AssistantTurnPlan plan =
        new AssistantTurnPlan(
            AssistantTurnPlan.Intent.MUTATION,
            AssistantTurnPlan.Kind.CLARIFICATION,
            "Need architecture decisions",
            List.of(
                new AssistantChoice(
                    "interaction-style",
                    "Primary Interaction Style: REST APIs or event-driven IoT updates?",
                    AssistantChoice.SelectionMode.SINGLE,
                    List.of(
                        new AssistantChoice.Option(
                            "api",
                            "APIFIRSTSERVERLESS",
                            "Expose REST APIs for inventory and sales."),
                        new AssistantChoice.Option(
                            "events",
                            "EVENTDRIVENSERVERLESS",
                            "Use EventChannels for IoT sensors.")),
                    true),
                new AssistantChoice(
                    "runtime",
                    "What is the preferred backend language and package manager?",
                    AssistantChoice.SelectionMode.SINGLE,
                    List.of(
                        new AssistantChoice.Option("py", "Python / PIP", "Python runtime."),
                        new AssistantChoice.Option("node", "Node / NPM", "Node runtime.")),
                    true)),
            new SemanticModelPatch(List.of()));

    String request = "Create a serverless model for vending machine backend";
    AssistantTurnPlan gated = gate.apply(plan, request);

    assertEquals(AssistantTurnPlan.Kind.CLARIFICATION, gated.kind());
    assertTrue(!gate.shouldDeferToProposal(plan, request));
  }

  @Test
  void keepsExplicitUserForksInTheMessage() {
    AssistantChoice question =
        new AssistantChoice(
            "billing",
            "Should billing be prepaid wallet or postpaid invoice?",
            AssistantChoice.SelectionMode.SINGLE,
            List.of(
                new AssistantChoice.Option("prepaid", "Prepaid", "Wallet based."),
                new AssistantChoice.Option("postpaid", "Postpaid", "Invoice based.")),
            true);
    AssistantTurnPlan plan =
        new AssistantTurnPlan(
            AssistantTurnPlan.Intent.MUTATION,
            AssistantTurnPlan.Kind.CLARIFICATION,
            "Need one decision.",
            List.of(question),
            new SemanticModelPatch(List.of()));

    String request =
        "Create a vending model. Which should I use: prepaid wallet or postpaid invoice?";
    AssistantTurnPlan gated = gate.apply(plan, request);

    assertEquals(AssistantTurnPlan.Kind.CLARIFICATION, gated.kind());
    assertEquals(1, gated.questions().size());
  }

  @Test
  void defersAttachmentReuploadQuestionsForDocumentBackedRequests() {
    AssistantChoice question =
        new AssistantChoice(
            "provide-file",
            "Please provide the contents of community-clinic-user-stories.md so the CIM model can"
                + " be generated.",
            AssistantChoice.SelectionMode.SINGLE,
            List.of(
                new AssistantChoice.Option(
                    "paste",
                    "Paste the file contents here",
                    "Provide the full text of the user stories."),
                new AssistantChoice.Option(
                    "reattach", "Reattach the file", "Upload the markdown file again.")),
            true);
    AssistantTurnPlan plan =
        new AssistantTurnPlan(
            AssistantTurnPlan.Intent.MUTATION,
            AssistantTurnPlan.Kind.CLARIFICATION,
            "Need the file.",
            List.of(question),
            new SemanticModelPatch(List.of()));

    String request = "Create a complete CIM model from the attached user story document.";
    AssistantTurnPlan gated = gate.apply(plan, request, true);

    assertEquals(AssistantTurnPlan.Kind.PATCH, gated.kind());
    assertTrue(gate.shouldDeferToProposal(plan, request, true));
  }

  @Test
  void emptyClarificationIsNotDeferrable() {
    AssistantTurnPlan plan =
        new AssistantTurnPlan(
            AssistantTurnPlan.Intent.MUTATION,
            AssistantTurnPlan.Kind.CLARIFICATION,
            "Need a decision.",
            List.of(),
            new SemanticModelPatch(List.of()));

    assertEquals(AssistantTurnPlan.Kind.PATCH, gate.apply(plan).kind());
    assertTrue(!gate.shouldDeferToProposal(plan, "Create a model"));
  }
}
