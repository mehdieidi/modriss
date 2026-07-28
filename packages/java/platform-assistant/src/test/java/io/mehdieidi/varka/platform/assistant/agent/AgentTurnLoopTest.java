package io.mehdieidi.varka.platform.assistant.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.mehdieidi.varka.platform.assistant.application.ProviderCallBudget;
import io.mehdieidi.varka.platform.assistant.metamodel.MetamodelGuideGenerator;
import io.mehdieidi.varka.platform.assistant.metamodel.MetamodelKnowledgeService;
import io.mehdieidi.varka.platform.assistant.metamodel.TypeContractService;
import io.mehdieidi.varka.platform.assistant.patch.AssistantMetamodelSchemaService;
import io.mehdieidi.varka.platform.assistant.patch.AssistantPatchCompiler;
import io.mehdieidi.varka.platform.assistant.provider.AssistantModelProvider;
import io.mehdieidi.varka.platform.assistant.tools.AgentModelTools;
import io.mehdieidi.varka.platform.assistant.workspace.ModelWorkspace;
import io.mehdieidi.varka.platform.kernel.ModelLevel;
import io.mehdieidi.varka.platform.kernel.PlatformException;
import io.mehdieidi.varka.platform.model.application.ModelService;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class AgentTurnLoopTest {
  @Test
  void createsValidatedSourceBlueprintBeforeModelApplication() throws Exception {
    ModelService models = mock(ModelService.class);
    when(models.validateStructural(any(), any()))
        .thenReturn(new ModelService.ValidationResult(true, List.of()));
    var knowledge = new MetamodelKnowledgeService(new AssistantMetamodelSchemaService());
    AgentTurnLoop loop =
        new AgentTurnLoop(
            new SourcePlanProvider(),
            new AgentModelTools(new TypeContractService(knowledge), models),
            new MetamodelGuideGenerator(knowledge),
            null,
            Duration.ofSeconds(5),
            Duration.ofSeconds(5),
            3,
            3);
    var json =
        new ObjectMapper()
            .readTree("{\"id\":\"root\",\"eClass\":\"CIMModel\",\"modelLevel\":\"CIM\"}");
    var workspace =
        new ModelWorkspace(ModelLevel.CIM, "m", 1, json, new AssistantPatchCompiler(), null);

    var result =
        loop.run(
            "s",
            ModelLevel.CIM,
            "Create a CIM",
            "<source-unit id=\"src-1\">Order placed</source-unit>",
            workspace);

    assertTrue(result.sourceBlueprint() != null);
    assertEquals("Commerce", result.sourceBlueprint().path("domain").asText());
    assertEquals(1, result.sourceBlueprint().path("slices").size());
  }

  @Test
  void executesTerminalStructuredActionWithinBudget() throws Exception {
    ModelService models = mock(ModelService.class);
    when(models.validateStructural(any(), any()))
        .thenReturn(new ModelService.ValidationResult(true, List.of()));
    var knowledge = new MetamodelKnowledgeService(new AssistantMetamodelSchemaService());
    var tools = new AgentModelTools(new TypeContractService(knowledge), models);
    FakeProvider provider = new FakeProvider();
    List<String> events = new ArrayList<>();
    AgentTurnLoop loop =
        new AgentTurnLoop(
            provider,
            tools,
            new MetamodelGuideGenerator(knowledge),
            (session, type, payload) -> events.add(type),
            Duration.ofSeconds(5),
            2);
    var json =
        new ObjectMapper()
            .readTree(
                """
{"id":"root","eClass":"CIMModel","modelLevel":"CIM","diagram":{"elements":[],"relationships":[]}}
""");
    var workspace =
        new ModelWorkspace(ModelLevel.CIM, "m", 1, json, new AssistantPatchCompiler(), null);

    var result = loop.run("s", ModelLevel.CIM, "Create a model change", null, workspace);

    assertEquals("Done", result.message());
    assertEquals(1, result.providerCalls());
    assertTrue(events.contains("tool.started"));
  }

  @Test
  void honorsTheModelsAnswerForAnExplanationThatMentionsUpdating() throws Exception {
    ModelService models = mock(ModelService.class);
    when(models.validateStructural(any(), any()))
        .thenReturn(new ModelService.ValidationResult(true, List.of()));
    var knowledge = new MetamodelKnowledgeService(new AssistantMetamodelSchemaService());
    FakeProvider provider = new FakeProvider();
    AgentTurnLoop loop =
        new AgentTurnLoop(
            provider,
            new AgentModelTools(new TypeContractService(knowledge), models),
            new MetamodelGuideGenerator(knowledge),
            null,
            Duration.ofSeconds(5),
            2);
    var json =
        new ObjectMapper()
            .readTree(
                """
{"id":"root","eClass":"CIMModel","modelLevel":"CIM","diagram":{"elements":[],"relationships":[]}}
""");
    var workspace =
        new ModelWorkspace(ModelLevel.CIM, "m", 1, json, new AssistantPatchCompiler(), null);

    var result =
        loop.run(
            "s",
            ModelLevel.CIM,
            "Explain how this model could be updated, but do not change it.",
            null,
            workspace);

    assertEquals("Done", result.message());
    assertEquals(1, result.providerCalls());
    assertTrue(result.patch().isEmpty());
  }

  @Test
  void durableCancellationStopsBeforeTheProviderBoundary() throws Exception {
    ModelService models = mock(ModelService.class);
    var knowledge = new MetamodelKnowledgeService(new AssistantMetamodelSchemaService());
    FakeProvider provider = new FakeProvider();
    AgentTurnLoop loop =
        new AgentTurnLoop(
            provider,
            new AgentModelTools(new TypeContractService(knowledge), models),
            new MetamodelGuideGenerator(knowledge),
            null,
            Duration.ofSeconds(5),
            2);
    var json =
        new ObjectMapper()
            .readTree(
                """
{"id":"root","eClass":"CIMModel","modelLevel":"CIM","diagram":{"elements":[],"relationships":[]}}
""");
    var workspace =
        new ModelWorkspace(ModelLevel.CIM, "m", 1, json, new AssistantPatchCompiler(), null);

    PlatformException failure =
        assertThrows(
            PlatformException.class,
            () ->
                loop.run(
                    "s",
                    ModelLevel.CIM,
                    "Explain",
                    null,
                    workspace,
                    false,
                    () -> true,
                    () -> null));

    assertEquals(499, failure.status());
    assertEquals(0, provider.calls);
  }

  @Test
  void failedProviderCallsRetainBudgetAccounting() throws Exception {
    ModelService models = mock(ModelService.class);
    var knowledge = new MetamodelKnowledgeService(new AssistantMetamodelSchemaService());
    FailingProvider provider = new FailingProvider();
    AgentTurnLoop loop =
        new AgentTurnLoop(
            provider,
            new AgentModelTools(new TypeContractService(knowledge), models),
            new MetamodelGuideGenerator(knowledge),
            null,
            Duration.ofSeconds(5),
            2);
    var json =
        new ObjectMapper()
            .readTree(
                """
{"id":"root","eClass":"CIMModel","modelLevel":"CIM","diagram":{"elements":[],"relationships":[]}}
""");
    var workspace =
        new ModelWorkspace(ModelLevel.CIM, "m", 1, json, new AssistantPatchCompiler(), null);

    AgentTurnLoop.TurnExecutionException failure =
        assertThrows(
            AgentTurnLoop.TurnExecutionException.class,
            () -> loop.run("s", ModelLevel.CIM, "Explain", null, workspace));

    assertEquals(502, failure.status());
    assertEquals(1, failure.providerCalls());
    assertEquals(0, failure.promptTokens());
    assertEquals(0, failure.completionTokens());
    assertTrue(failure.providerCallDetails().isEmpty());
    assertEquals(1, provider.calls);
  }

  @Test
  void repairsInvalidToolPayloadWithinBudget() throws Exception {
    ModelService models = mock(ModelService.class);
    when(models.validateStructural(any(), any()))
        .thenReturn(new ModelService.ValidationResult(true, List.of()));
    var knowledge = new MetamodelKnowledgeService(new AssistantMetamodelSchemaService());
    RepairingProvider provider = new RepairingProvider();
    AgentTurnLoop loop =
        new AgentTurnLoop(
            provider,
            new AgentModelTools(new TypeContractService(knowledge), models),
            new MetamodelGuideGenerator(knowledge),
            null,
            Duration.ofSeconds(5),
            Duration.ofSeconds(5),
            3,
            2);
    var json =
        new ObjectMapper()
            .readTree(
                """
{"id":"root","eClass":"CIMModel","modelLevel":"CIM","diagram":{"elements":[],"relationships":[]}}
""");
    var workspace =
        new ModelWorkspace(ModelLevel.CIM, "m", 1, json, new AssistantPatchCompiler(), null);

    var result = loop.run("s", ModelLevel.CIM, "Create a model change", null, workspace);

    assertEquals("Recovered", result.message());
    assertEquals(2, result.providerCalls());
    assertEquals(2, provider.calls);
  }

  @Test
  void acceptsStringEncodedCommandItemsFromStructuredProviders() throws Exception {
    ModelService models = mock(ModelService.class);
    when(models.validateStructural(any(), any()))
        .thenReturn(new ModelService.ValidationResult(true, List.of()));
    var knowledge = new MetamodelKnowledgeService(new AssistantMetamodelSchemaService());
    AgentTurnLoop loop =
        new AgentTurnLoop(
            new StringEncodedPatchProvider(),
            new AgentModelTools(new TypeContractService(knowledge), models),
            new MetamodelGuideGenerator(knowledge),
            null,
            Duration.ofSeconds(5),
            2);
    var json =
        new ObjectMapper()
            .readTree(
                """
{"id":"root","eClass":"CIMModel","modelLevel":"CIM","diagram":{"elements":[],"relationships":[]}}
""");
    var workspace =
        new ModelWorkspace(ModelLevel.CIM, "m", 1, json, new AssistantPatchCompiler(), null);

    var result = loop.run("s", ModelLevel.CIM, "Create a goal", null, workspace);

    assertEquals("Model checkpoint saved.", result.message());
    assertEquals(1, result.providerCalls());
    assertTrue(!result.patch().isEmpty());
  }

  @Test
  void repairsAnEmptyTerminalAnswerWithinBudget() throws Exception {
    ModelService models = mock(ModelService.class);
    var knowledge = new MetamodelKnowledgeService(new AssistantMetamodelSchemaService());
    EmptyAnswerThenRecoveryProvider provider = new EmptyAnswerThenRecoveryProvider();
    AgentTurnLoop loop =
        new AgentTurnLoop(
            provider,
            new AgentModelTools(new TypeContractService(knowledge), models),
            new MetamodelGuideGenerator(knowledge),
            null,
            Duration.ofSeconds(5),
            Duration.ofSeconds(5),
            3,
            2);
    var json =
        new ObjectMapper()
            .readTree(
                """
{"id":"root","eClass":"CIMModel","modelLevel":"CIM","diagram":{"elements":[],"relationships":[]}}
""");
    var workspace =
        new ModelWorkspace(ModelLevel.CIM, "m", 1, json, new AssistantPatchCompiler(), null);

    var result = loop.run("s", ModelLevel.CIM, "Create a model change", null, workspace);

    assertEquals("Recovered", result.message());
    assertEquals(2, result.providerCalls());
  }

  @Test
  void rejectsClarificationForAnExistingElementWhenTheModelIsEmpty() throws Exception {
    ModelService models = mock(ModelService.class);
    var knowledge = new MetamodelKnowledgeService(new AssistantMetamodelSchemaService());
    AskThenAnswerProvider provider = new AskThenAnswerProvider();
    AgentTurnLoop loop =
        new AgentTurnLoop(
            provider,
            new AgentModelTools(new TypeContractService(knowledge), models),
            new MetamodelGuideGenerator(knowledge),
            null,
            Duration.ofSeconds(5),
            Duration.ofSeconds(5),
            3,
            2);
    var json =
        new ObjectMapper()
            .readTree(
                """
{"id":"root","eClass":"PIMModel","modelLevel":"PIM","diagram":{"elements":[],"relationships":[]}}
""");
    var workspace =
        new ModelWorkspace(ModelLevel.PIM, "m", 1, json, new AssistantPatchCompiler(), null);

    var result = loop.run("s", ModelLevel.PIM, "Create a vending-machine backend", null, workspace);

    assertEquals("Recovered", result.message());
    assertEquals(2, result.providerCalls());
    assertTrue(provider.correctivePromptReceived);
  }

  private static final class FakeProvider implements AssistantModelProvider {
    int calls;

    @Override
    public AssistantProviderMetadata metadata() {
      return new AssistantProviderMetadata("fake", "", "");
    }

    @Override
    public boolean available() {
      return true;
    }

    @Override
    public AssistantReply complete(AssistantPrompt prompt) {
      calls++;
      ProviderCallBudget.consume(prompt.role());
      return new AssistantReply(
          "{\"tool\":\"answer_user\",\"arguments\":{\"message\":\"Done\"}}", "fake", "fake");
    }
  }

  private static final class SourcePlanProvider implements AssistantModelProvider {
    @Override
    public AssistantProviderMetadata metadata() {
      return new AssistantProviderMetadata("fake", "", "");
    }

    @Override
    public boolean available() {
      return true;
    }

    @Override
    public AssistantReply complete(AssistantPrompt prompt) {
      ProviderCallBudget.consume(prompt.role());
      return new AssistantReply(
          "{\"tool\":\"plan_source_model\",\"arguments\":{\"domain\":\"Commerce\",\"slices\":[{\"focus\":\"Order"
              + " intake\",\"sourceUnitIds\":[\"src-1\"]}]}}",
          "fake",
          "fake");
    }
  }

  private static final class FailingProvider implements AssistantModelProvider {
    int calls;

    @Override
    public AssistantProviderMetadata metadata() {
      return new AssistantProviderMetadata("fake", "", "");
    }

    @Override
    public boolean available() {
      return true;
    }

    @Override
    public AssistantReply complete(AssistantPrompt prompt) {
      calls++;
      ProviderCallBudget.consume(prompt.role());
      throw new PlatformException(502, "Provider failed");
    }
  }

  private static final class RepairingProvider implements AssistantModelProvider {
    int calls;

    @Override
    public AssistantProviderMetadata metadata() {
      return new AssistantProviderMetadata("fake", "", "");
    }

    @Override
    public boolean available() {
      return true;
    }

    @Override
    public AssistantReply complete(AssistantPrompt prompt) {
      calls++;
      ProviderCallBudget.consume(prompt.role());
      if (calls == 1) {
        return new AssistantReply(
            "{\"tool\":\"commit_model_batch\",\"arguments\":{\"creates\":[{\"eClass\":\"Goal\"}]}}",
            "fake",
            "fake");
      }
      return new AssistantReply(
          "{\"tool\":\"answer_user\",\"arguments\":{\"message\":\"Recovered\"}}", "fake", "fake");
    }
  }

  private static final class EmptyAnswerThenRecoveryProvider implements AssistantModelProvider {
    int calls;

    @Override
    public AssistantProviderMetadata metadata() {
      return new AssistantProviderMetadata("fake", "", "");
    }

    @Override
    public boolean available() {
      return true;
    }

    @Override
    public AssistantReply complete(AssistantPrompt prompt) {
      calls++;
      ProviderCallBudget.consume(prompt.role());
      return new AssistantReply(
          calls == 1
              ? "{\"tool\":\"answer_user\",\"arguments\":{\"message\":\"\"}}"
              : "{\"tool\":\"answer_user\",\"arguments\":{\"message\":\"Recovered\"}}",
          "fake",
          "fake");
    }
  }

  private static final class StringEncodedPatchProvider implements AssistantModelProvider {
    @Override
    public AssistantProviderMetadata metadata() {
      return new AssistantProviderMetadata("fake", "", "");
    }

    @Override
    public boolean available() {
      return true;
    }

    @Override
    public AssistantReply complete(AssistantPrompt prompt) {
      ProviderCallBudget.consume(prompt.role());
      return new AssistantReply(
          """
{"tool":"commit_model_batch","arguments":{"creates":["{\\"clientRef\\":\\"goal_online_scheduling\\",\\"eClass\\":\\"BusinessGoal\\",\\"attributes\\":{\\"name\\":\\"Online Scheduling\\"},\\"owner\\":\\"rootId\\",\\"reference\\":\\"goals\\"}"],"updates":[],"connections":[],"deletions":[],"evidence":[],"planSummary":"Created goal","turnComplete":true}}
""",
          "fake",
          "fake");
    }
  }

  private static final class AskThenAnswerProvider implements AssistantModelProvider {
    int calls;
    boolean correctivePromptReceived;

    @Override
    public AssistantProviderMetadata metadata() {
      return new AssistantProviderMetadata("fake", "", "");
    }

    @Override
    public boolean available() {
      return true;
    }

    @Override
    public AssistantReply complete(AssistantPrompt prompt) {
      calls++;
      ProviderCallBudget.consume(prompt.role());
      if (calls == 1) {
        return new AssistantReply(
            "{\"tool\":\"ask_user\",\"arguments\":{\"message\":\"Which existing service should own"
                + " this?\"}}",
            "fake",
            "fake");
      }
      correctivePromptReceived =
          prompt.user().contains("asking the user to choose an existing owner");
      return new AssistantReply(
          "{\"tool\":\"answer_user\",\"arguments\":{\"message\":\"Recovered\"}}", "fake", "fake");
    }
  }
}
