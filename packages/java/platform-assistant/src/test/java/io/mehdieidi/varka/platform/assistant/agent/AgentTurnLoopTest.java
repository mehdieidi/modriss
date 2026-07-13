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

    var result = loop.run("s", ModelLevel.CIM, "Explain", null, workspace);

    assertEquals("Done", result.message());
    assertEquals(1, result.providerCalls());
    assertTrue(events.contains("tool.started"));
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

  private static final class FakeProvider implements AssistantModelProvider {
    int calls;

    public AssistantProviderMetadata metadata() {
      return new AssistantProviderMetadata("fake", "", "");
    }

    public boolean available() {
      return true;
    }

    public AssistantReply complete(AssistantPrompt prompt) {
      calls++;
      ProviderCallBudget.consume(prompt.role());
      return new AssistantReply(
          "{\"tool\":\"answer_user\",\"arguments\":{\"message\":\"Done\"}}", "fake", "fake");
    }
  }
}
