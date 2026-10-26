package io.mehdieidi.modriss.platform.assistant.agent;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import io.mehdieidi.modriss.platform.assistant.metamodel.MetamodelGuideGenerator;
import io.mehdieidi.modriss.platform.assistant.metamodel.MetamodelKnowledgeService;
import io.mehdieidi.modriss.platform.assistant.metamodel.TypeContractService;
import io.mehdieidi.modriss.platform.assistant.patch.AssistantMetamodelSchemaService;
import io.mehdieidi.modriss.platform.assistant.patch.AssistantPatchCompiler;
import io.mehdieidi.modriss.platform.assistant.tools.AgentModelTools;
import io.mehdieidi.modriss.platform.assistant.workspace.ModelWorkspace;
import io.mehdieidi.modriss.platform.kernel.ModelLevel;
import io.mehdieidi.modriss.platform.model.application.ModelService;
import io.mehdieidi.modriss.platform.project.application.ProjectService;
import io.mehdieidi.modriss.platform.storage.api.PlatformStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import tools.jackson.databind.ObjectMapper;

/** Paid, opt-in checks against the configured production LLM on an isolated synthetic model. */
@Tag("llm-provider")
@EnabledIfSystemProperty(named = "modriss.edits.live", matches = "true")
class SurgicalModelEditLiveTest {
  @Test
  void renameReportedEntityStaysWithinBudgetAndChangesOnlyItsName() throws Exception {
    String snapshot = System.getProperty("modriss.edits.currentModelSnapshot", "");
    org.junit.jupiter.api.Assumptions.assumeFalse(snapshot.isBlank());
    var mapper = new ObjectMapper();
    var initial = mapper.readTree(Files.readString(Path.of(snapshot))).path("model");
    var store = mock(PlatformStore.class);
    when(store.objectMapper()).thenReturn(mapper);
    var models = new ModelService(store, mock(ProjectService.class));
    assertTrue(models.validateStructural(ModelLevel.CIM, initial).valid());
    var knowledge = new MetamodelKnowledgeService(new AssistantMetamodelSchemaService());
    var compiler = new AssistantPatchCompiler();
    var loop =
        new AgentTurnLoop(
            recordingProvider("reported-rename"),
            new AgentModelTools(new TypeContractService(knowledge), models),
            new MetamodelGuideGenerator(knowledge),
            null,
            Duration.ofMinutes(4),
            Duration.ofMinutes(4),
            8,
            9);
    var workspace =
        new ModelWorkspace(ModelLevel.CIM, "synthetic", 1, initial, compiler, models, null);
    var result =
        loop.run(
            "reported-rename",
            ModelLevel.CIM,
            "update the name of the load entity to 'loan2'\n\n"
                + "Selected canvas elements are focus context only:"
                + " f8ccbbe8-dbdb-49cd-95b7-3a6d71345b12.",
            null,
            workspace,
            false,
            AgentTurnLoop.WorkflowMode.ADAPTIVE);
    writeReport("reported-rename", result, workspace);
    assertEquals(1, result.commandBatch().updates().size());
    var update = result.commandBatch().updates().get(0);
    assertEquals("366aff91-de3b-4dca-ab4d-6fd04ee67ad5", update.elementId());
    assertEquals("loan2", update.attributes().get("name").asText());
    assertEquals(1, update.attributes().size());
    assertTrue(result.commandBatch().creates().isEmpty());
    assertTrue(result.commandBatch().deletions().isEmpty());
    assertTrue(result.commandBatch().connections().isEmpty());
    var restored = compiler.prepareApplyRoot(workspace.snapshot(), null);
    workspace.inversePatch().forEach(operation -> compiler.applyOperation(restored, operation));
    assertEquals(initial, restored);
  }

  @Test
  void adaptiveRenameThenDeletePreservesUnrelatedContentWithRealStructuralValidation()
      throws Exception {
    var mapper = new ObjectMapper();
    var store = mock(PlatformStore.class);
    when(store.objectMapper()).thenReturn(mapper);
    var models = new ModelService(store, mock(ProjectService.class));
    var knowledge = new MetamodelKnowledgeService(new AssistantMetamodelSchemaService());
    var compiler = new AssistantPatchCompiler();
    var provider = recordingProvider("simple");
    var loop =
        new AgentTurnLoop(
            provider,
            new AgentModelTools(new TypeContractService(knowledge), models),
            new MetamodelGuideGenerator(knowledge),
            null,
            Duration.ofMinutes(4),
            Duration.ofMinutes(4),
            8,
            9);
    var initial =
        mapper.readTree(
            """
{"id":"root","eClass":"CIMModel","modelLevel":"CIM","domainName":"Equipment lending",
"informationItems":[
{"id":"load-id","eClass":"InformationItem","name":"Load ID","type":"TEXT","required":true,"collection":false},
{"id":"borrower","eClass":"InformationItem","name":"Borrower","type":"TEXT","required":true,"collection":false}],
"diagram":{"elements":[
{"id":"load-id","eClass":"InformationItem","name":"Load ID"},
{"id":"borrower","eClass":"InformationItem","name":"Borrower"}],"relationships":[]}}
""");
    assertTrue(models.validateStructural(ModelLevel.CIM, initial).valid());
    var workspace =
        new ModelWorkspace(ModelLevel.CIM, "synthetic", 1, initial, compiler, models, null);
    var rename =
        loop.run(
            "live-rename",
            ModelLevel.CIM,
            "Change the name of the 'Load ID' information item element to 'load identity'.",
            null,
            workspace,
            false,
            AgentTurnLoop.WorkflowMode.ADAPTIVE);
    writeReport("rename", rename, workspace);
    assertNotNull(rename.commandBatch());
    assertEquals(1, rename.commandBatch().updates().size());
    assertEquals(
        "load identity",
        workspace.snapshot().path("informationItems").path(0).path("name").asText());
    assertEquals(
        initial.path("informationItems").path(1),
        workspace.snapshot().path("informationItems").path(1));
    assertTrue(rename.commandBatch().creates().isEmpty());
    assertTrue(rename.commandBatch().deletions().isEmpty());

    // A fresh workspace reproduces the next durable turn after a checkpoint and confirmation.
    var next =
        new ModelWorkspace(
            ModelLevel.CIM, "synthetic", 2, workspace.snapshot(), compiler, models, null);
    var deletion =
        loop.run(
            "live-delete",
            ModelLevel.CIM,
            "Delete the 'load identity' information item.",
            null,
            next,
            true,
            AgentTurnLoop.WorkflowMode.ADAPTIVE);
    writeReport("delete", deletion, next);
    assertNotNull(deletion.commandBatch());
    assertEquals(1, deletion.commandBatch().deletions().size());
    assertEquals(1, next.snapshot().path("informationItems").size());
    assertEquals(
        initial.path("informationItems").path(1), next.snapshot().path("informationItems").path(0));
    assertEquals(1, next.snapshot().path("diagram").path("elements").size());
    assertTrue(models.validateStructural(ModelLevel.CIM, next.snapshot()).valid());
  }

  @Test
  void requiredDeletionIsExplainedWithoutConfirmationOrMutation() throws Exception {
    var mapper = new ObjectMapper();
    var store = mock(PlatformStore.class);
    when(store.objectMapper()).thenReturn(mapper);
    var models = new ModelService(store, mock(ProjectService.class));
    var knowledge = new MetamodelKnowledgeService(new AssistantMetamodelSchemaService());
    var compiler = new AssistantPatchCompiler();
    var provider = recordingProvider("confirmed-incomplete");
    var loop =
        new AgentTurnLoop(
            provider,
            new AgentModelTools(new TypeContractService(knowledge), models),
            new MetamodelGuideGenerator(knowledge),
            null,
            Duration.ofMinutes(4),
            Duration.ofMinutes(4),
            8,
            9);
    String snapshot = System.getProperty("modriss.edits.modelSnapshot", "");
    tools.jackson.databind.JsonNode initial;
    if (snapshot.isBlank()) {
      try (var input = getClass().getResourceAsStream("/assistant-incomplete-edit-model.json")) {
        initial = mapper.readTree(input);
      }
    } else {
      initial = mapper.readTree(Files.readString(Path.of(snapshot))).path("model");
    }
    assertFalse(models.validateStructural(ModelLevel.CIM, initial).valid());
    String request = "delete the 'load id' information item";
    var workspace =
        new ModelWorkspace(ModelLevel.CIM, "synthetic", 1, initial, compiler, models, null);
    var result =
        loop.run(
            "live-unconfirmed",
            ModelLevel.CIM,
            request,
            null,
            workspace,
            false,
            AgentTurnLoop.WorkflowMode.ADAPTIVE);
    assertEquals(initial, workspace.snapshot());
    assertTrue(workspace.patch().isEmpty());
    assertNull(result.commandBatch());
    assertFalse(result.message().isBlank());
    assertFalse(result.message().contains("\"action\""));
    assertTrue(result.needsInput());
    assertTrue(workspace.patch().isEmpty());
    assertEquals(initial, workspace.snapshot());
    Path directory = Path.of("target", "surgical-model-edit-live");
    Files.createDirectories(directory);
    Files.writeString(
        directory.resolve("blocked-deletion.json"),
        mapper.writeValueAsString(
            Map.of(
                "message",
                result.message(),
                "needsInput",
                result.needsInput(),
                "calls",
                result.providerCalls(),
                "promptTokens",
                result.promptTokens())));
  }

  private io.mehdieidi.modriss.platform.assistant.provider.AssistantModelProvider recordingProvider(
      String prefix) throws Exception {
    var mapper = new ObjectMapper();
    var delegate = new ServerlessSuitabilityLiveTest().liveProvider();
    return new io.mehdieidi.modriss.platform.assistant.provider.AssistantModelProvider() {
      int calls;

      public AssistantProviderMetadata metadata() {
        return delegate.metadata();
      }

      public ProviderCapabilities capabilities() {
        return delegate.capabilities();
      }

      public boolean available() {
        return delegate.available();
      }

      public AssistantReply complete(AssistantPrompt prompt) {
        var reply = delegate.completeStructured(prompt);
        try {
          Path directory = Path.of("target", "surgical-model-edit-live");
          Files.createDirectories(directory);
          Files.writeString(
              directory.resolve(prefix + "-call-" + ++calls + ".json"),
              mapper.writeValueAsString(
                  Map.of(
                      "response",
                      reply.content(),
                      "user",
                      prompt.user(),
                      "system",
                      prompt.system())));
        } catch (Exception failure) {
          throw new IllegalStateException(failure);
        }
        return reply;
      }
    };
  }

  private void writeReport(String name, AgentTurnLoop.TurnResult result, ModelWorkspace workspace)
      throws Exception {
    assertTrue(result.validation().valid());
    assertFalse(result.patch().isEmpty());
    assertFalse(result.message().contains("\"action\""));
    Path directory = Path.of("target", "surgical-model-edit-live");
    Files.createDirectories(directory);
    Files.writeString(
        directory.resolve(name + ".json"),
        new ObjectMapper()
            .writeValueAsString(
                Map.of(
                    "message",
                    result.message(),
                    "model",
                    workspace.snapshot(),
                    "calls",
                    result.providerCalls(),
                    "promptTokens",
                    result.promptTokens(),
                    "completionTokens",
                    result.completionTokens(),
                    "providerCalls",
                    result.providerCallDetails())));
  }
}
