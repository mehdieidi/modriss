package io.mehdieidi.modless.platform.assistant.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.mehdieidi.modless.platform.assistant.metamodel.MetamodelGuideGenerator;
import io.mehdieidi.modless.platform.assistant.metamodel.MetamodelKnowledgeService;
import io.mehdieidi.modless.platform.assistant.metamodel.TypeContractService;
import io.mehdieidi.modless.platform.assistant.patch.AssistantMetamodelSchemaService;
import io.mehdieidi.modless.platform.assistant.patch.AssistantPatchCompiler;
import io.mehdieidi.modless.platform.assistant.provider.AssistantModelProvider;
import io.mehdieidi.modless.platform.assistant.tools.AgentModelTools;
import io.mehdieidi.modless.platform.assistant.workspace.ModelWorkspace;
import io.mehdieidi.modless.platform.kernel.ModelLevel;
import io.mehdieidi.modless.platform.model.application.ModelService;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;

class AgentTurnLoopTest {
  @Test
  void streamsDeltasAndCompletesWithinBudget() throws Exception {
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
    assertTrue(events.contains("assistant.text.delta"));
    assertTrue(events.contains("assistant.delta.validated"));
    assertEquals("root", provider.toolThreadModelId);
  }

  private static final class FakeProvider implements AssistantModelProvider {
    private String toolThreadModelId;
    public AssistantProviderMetadata metadata() {
      return new AssistantProviderMetadata("fake", "", "");
    }

    public boolean available() {
      return true;
    }

    public AssistantReply complete(AssistantPrompt prompt) {
      return new AssistantReply("Done", "fake", "fake");
    }

    public AssistantReply streamWithTools(
        AssistantPrompt prompt, Object tools, java.util.function.Consumer<String> consumer) {
      toolThreadModelId =
          CompletableFuture.supplyAsync(
                  () -> ((AgentModelTools) tools).readModel("").path("id").asText())
              .join();
      consumer.accept("Do");
      consumer.accept("ne");
      return complete(prompt);
    }
  }
}
