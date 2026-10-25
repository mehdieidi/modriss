package io.mehdieidi.modriss.platform.assistant.agent;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import io.mehdieidi.modriss.platform.assistant.application.AssistantPromptGuard;
import io.mehdieidi.modriss.platform.assistant.application.DurableTurnExecutionContext;
import io.mehdieidi.modriss.platform.assistant.application.ProviderCallBudget;
import io.mehdieidi.modriss.platform.assistant.patch.AssistantPatchCompiler;
import io.mehdieidi.modriss.platform.assistant.provider.AssistantModelProvider;
import io.mehdieidi.modriss.platform.assistant.spi.AssistantSettings;
import io.mehdieidi.modriss.platform.assistant.tools.AgentModelTools;
import io.mehdieidi.modriss.platform.assistant.workspace.ModelWorkspace;
import io.mehdieidi.modriss.platform.kernel.ModelLevel;
import io.mehdieidi.modriss.platform.kernel.PlatformException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class ServerlessSuitabilityWorkflowTest {
  private static final ObjectMapper JSON = new ObjectMapper();
  private static final String SPEC =
      "Bursty file uploads; workers finish in seconds. GPU training stays external.";

  @Test
  void adaptiveAssessmentUsesFullSourceAuditsCallsAndNeverTouchesModelTools() {
    String answer =
        "A hybrid design fits. Model the upload workers and connect GPU training through an"
            + " authenticated API.";
    var provider = new Scripted("{\"strategy\":\"SERVERLESS_SUITABILITY\"}", answer);
    var tools = mock(AgentModelTools.class);
    var loop = new AgentTurnLoop(provider, tools, null, null, Duration.ofSeconds(10), 3);
    var workspace =
        new ModelWorkspace(
            ModelLevel.CIM,
            "m",
            7,
            JSON.readTree("{\"id\":\"root\",\"eClass\":\"CIMModel\"}"),
            new AssistantPatchCompiler(),
            null);
    String before = workspace.snapshot().toString();
    var result =
        DurableTurnExecutionContext.with(
            "turn",
            SPEC,
            () ->
                loop.run(
                    "session",
                    ModelLevel.CIM,
                    "Assess suitability",
                    "only an early source window",
                    workspace,
                    false,
                    AgentTurnLoop.WorkflowMode.ADAPTIVE));
    assertEquals(answer, result.message());
    assertEquals(1, provider.textCalls);
    assertEquals(2, result.providerCalls());
    assertEquals(2, result.providerCallDetails().size());
    assertEquals(20, result.promptTokens());
    assertTrue(provider.prompts.get(1).user().contains(SPEC));
    assertFalse(provider.prompts.get(1).user().contains("only an early source window"));
    assertNull(result.validation());
    assertNull(result.commandBatch());
    assertTrue(result.patch().isEmpty());
    assertTrue(result.inversePatch().isEmpty());
    assertEquals(before, workspace.snapshot().toString());
    assertTrue(workspace.patch().isEmpty());
    verifyNoInteractions(tools);
    assertNull(DurableTurnExecutionContext.fullSource());
    assertFalse(ProviderCallBudget.isBound());
  }

  @Test
  void externallyDeliveredServicesDoNotTriggerDeterministicVerdictRejection() {
    String answer =
        """
        Serverless is suitable for the upload application.

        Use managed storage and an external identity service. An external billing API can be
        called through an adapter. These dependencies do not require a separate application core.
        Confirm callback authentication, retries and the provider's quotas before implementation.
        """;
    var provider = new Scripted(answer);
    var result =
        new ServerlessSuitabilityWorkflow(provider)
            .run("Would MODRISS suit this project?", SPEC, () -> {});
    assertEquals(answer, result.message());
    assertEquals(1, result.providerCalls());
    assertEquals(1, provider.textCalls);
  }

  @Test
  void clarificationAndProvisionalAdviceAreSuccessfulLlmResponsesWithoutRequiredLabels() {
    for (String answer :
        List.of(
            "What are the task duration, latency target and data residency constraints?",
            "This could work, subject to measuring cold-start latency. What deadline is mandatory?",
            "این معماری به زمان پاسخ مورد نیاز بستگی دارد. محدودیت زمانی چیست؟",
            "The disconnected controller cannot use the proposed cloud services. Keep the control"
                + " loop local.")) {
      var result =
          new ServerlessSuitabilityWorkflow(new Scripted(answer)).run("Assess", "", () -> {});
      assertEquals(answer, result.message());
      assertTrue(result.patch().isEmpty());
    }
  }

  @Test
  void anEmptyProviderResponseGetsAnotherLlmCallWithoutACannedAnalysis() {
    String answer = "Please describe the workload and its response-time requirements.";
    var provider = new Scripted("", answer);
    var result = new ServerlessSuitabilityWorkflow(provider).run("Assess", SPEC, () -> {});
    assertEquals(answer, result.message());
    assertEquals(2, result.providerCalls());
    assertEquals(2, result.providerCallDetails().size());
    assertTrue(provider.prompts.get(1).user().contains("no visible response"));

    var failing = new Scripted("", "");
    var failure =
        assertThrows(
            AgentTurnLoop.TurnExecutionException.class,
            () -> new ServerlessSuitabilityWorkflow(failing).run("Assess", SPEC, () -> {}));
    assertEquals(502, failure.status());
    assertEquals(2, failure.providerCallDetails().size());
    assertFalse(ProviderCallBudget.isBound());
  }

  @Test
  void providerOutagesRemainTechnicalFailuresAndNeverBecomeInventedRecommendations() {
    var provider = mock(AssistantModelProvider.class);
    when(provider.completeText(any()))
        .thenThrow(new PlatformException(503, "Upstream unavailable"));
    var failure =
        assertThrows(
            AgentTurnLoop.TurnExecutionException.class,
            () -> new ServerlessSuitabilityWorkflow(provider).run("Assess", SPEC, () -> {}));
    assertEquals(503, failure.status());
    assertEquals("Upstream unavailable", failure.getMessage());
    verify(provider, never()).completeStructured(any());
    assertFalse(ProviderCallBudget.isBound());
  }

  @Test
  void cancellationBeforeOrAfterTheProviderReleasesTheSessionWithoutReturningAnAnswer() {
    var provider = new Scripted("A serverless design may fit.");
    var tools = mock(AgentModelTools.class);
    var loop = new AgentTurnLoop(provider, tools, null, null, Duration.ofSeconds(5), 3);
    assertThrows(
        PlatformException.class,
        () ->
            loop.run(
                "s",
                ModelLevel.CIM,
                "Assess",
                SPEC,
                null,
                false,
                AgentTurnLoop.WorkflowMode.SERVERLESS_SUITABILITY,
                () -> true,
                () -> null));
    assertEquals(0, provider.prompts.size());
    assertEquals(
        "A serverless design may fit.",
        loop.run(
                "s",
                ModelLevel.CIM,
                "Assess",
                SPEC,
                null,
                false,
                AgentTurnLoop.WorkflowMode.SERVERLESS_SUITABILITY)
            .message());

    var checks = new java.util.concurrent.atomic.AtomicInteger();
    assertThrows(
        PlatformException.class,
        () ->
            new ServerlessSuitabilityWorkflow(new Scripted("An answer"))
                .run(
                    "Assess",
                    SPEC,
                    () -> {
                      if (checks.incrementAndGet() == 2)
                        throw new PlatformException(499, "Cancelled");
                    }));
    assertFalse(ProviderCallBudget.isBound());
    verifyNoInteractions(tools);
  }

  @Test
  void promptGuardPreservesTheRubricAndRedactsSecretsWithoutAnAssessmentSchema() {
    var settings = mock(AssistantSettings.class);
    when(settings.maxSystemChars()).thenReturn(14000);
    var sanitized =
        new AssistantPromptGuard(settings)
            .sanitize(
                new AssistantModelProvider.AssistantPrompt(
                    ServerlessSuitabilityWorkflow.systemPrompt(),
                    "Assess password=example-secret",
                    List.of(),
                    List.of(),
                    ServerlessSuitabilityWorkflow.TOOL));
    assertFalse(sanitized.system().contains("[truncated by backend]"));
    assertTrue(sanitized.system().contains(ServerlessSuitabilityWorkflow.knowledge()));
    assertFalse(sanitized.user().contains("example-secret"));
    assertTrue(sanitized.system().contains("ordinary Markdown"));
  }

  @Test
  void theCompleteSpecificationAndConversationReachTheProviderUnchanged() {
    String source =
        "🚀 "
            + "A long requirement with state and timing constraints. ".repeat(25)
            + "\nA second paragraph with a mandatory constraint!";
    String conversation =
        "USER: Prior requirement.\n"
            + "ASSISTANT: A question.\n"
            + "Current request: latency can be 30 seconds.";
    var input = JSON.readTree(ServerlessSuitabilityWorkflow.input(conversation, source));
    assertEquals(conversation, input.path("requestAndConversation").asText());
    assertEquals(source, input.path("projectSpecification").asText());
  }

  private static final class Scripted implements AssistantModelProvider {
    private final List<String> replies;
    final List<AssistantPrompt> prompts = new ArrayList<>();
    int textCalls;

    Scripted(String... replies) {
      this.replies = List.of(replies);
    }

    public AssistantProviderMetadata metadata() {
      return new AssistantProviderMetadata("test", "local", "direct");
    }

    public boolean available() {
      return true;
    }

    public AssistantReply completeText(AssistantPrompt prompt) {
      textCalls++;
      return complete(prompt);
    }

    public AssistantReply complete(AssistantPrompt prompt) {
      ProviderCallBudget.consume();
      prompts.add(prompt);
      return new AssistantReply(
          replies.get(prompts.size() - 1), "test", "test", new TokenUsage(10, 20));
    }
  }
}
