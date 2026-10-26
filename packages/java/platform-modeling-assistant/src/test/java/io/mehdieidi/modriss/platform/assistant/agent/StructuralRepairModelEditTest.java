package io.mehdieidi.modriss.platform.assistant.agent;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import io.mehdieidi.modriss.platform.assistant.application.ProviderCallBudget;
import io.mehdieidi.modriss.platform.assistant.metamodel.MetamodelGuideGenerator;
import io.mehdieidi.modriss.platform.assistant.metamodel.MetamodelKnowledgeService;
import io.mehdieidi.modriss.platform.assistant.metamodel.TypeContractService;
import io.mehdieidi.modriss.platform.assistant.patch.AssistantMetamodelSchemaService;
import io.mehdieidi.modriss.platform.assistant.patch.AssistantPatchCompiler;
import io.mehdieidi.modriss.platform.assistant.provider.AssistantModelProvider;
import io.mehdieidi.modriss.platform.assistant.tools.AgentModelTools;
import io.mehdieidi.modriss.platform.assistant.workspace.ModelWorkspace;
import io.mehdieidi.modriss.platform.kernel.ModelLevel;
import io.mehdieidi.modriss.platform.model.application.ModelService;
import io.mehdieidi.modriss.platform.project.application.ProjectService;
import io.mehdieidi.modriss.platform.storage.api.PlatformStore;
import java.time.Duration;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class StructuralRepairModelEditTest {
  private final ObjectMapper mapper = new ObjectMapper();

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void narrowEditCannotSilentlyRepairUnrelatedStructuralBlockers(boolean deletion)
      throws Exception {
    var store = mock(PlatformStore.class);
    when(store.objectMapper()).thenReturn(mapper);
    var models = spy(new ModelService(store, mock(ProjectService.class)));
    var initial = initial();
    var baseline = models.validateStructural(ModelLevel.CIM, initial);
    assertFalse(baseline.valid());
    assertEquals(2, baseline.issues().size(), () -> baseline.issues().toString());
    assertTrue(
        baseline.issues().stream().allMatch(issue -> "Multiplicity".equals(issue.issueClass())));
    var compiler = new AssistantPatchCompiler();
    var workspace = new ModelWorkspace(ModelLevel.CIM, "m", 1, initial, compiler, models, null);
    var knowledge = new MetamodelKnowledgeService(new AssistantMetamodelSchemaService());
    var provider = new RepairProvider(deletion, workspace, initial);
    var loop =
        new AgentTurnLoop(
            provider,
            new AgentModelTools(new TypeContractService(knowledge), models),
            new MetamodelGuideGenerator(knowledge),
            null,
            Duration.ofSeconds(30),
            Duration.ofSeconds(30),
            4,
            4);

    var result =
        loop.run(
            "edit",
            ModelLevel.CIM,
            deletion ? "Delete 'Load ID'" : "Rename 'Load ID' to 'load identity'",
            null,
            workspace,
            true,
            AgentTurnLoop.WorkflowMode.AUTO);

    assertEquals(3, result.providerCalls());
    assertNull(result.validation());
    assertNull(result.commandBatch());
    assertEquals(
        "The edit was blocked because the model has two incomplete multiplicities. No changes were"
            + " made.",
        result.message());
    assertTrue(result.needsInput());
    assertEquals(initial, workspace.snapshot());
    assertTrue(workspace.patch().isEmpty());
    assertTrue(workspace.inversePatch().isEmpty());
    verify(models, never()).validate(any(ModelLevel.class), any());
  }

  private JsonNode initial() throws Exception {
    try (var input = getClass().getResourceAsStream("/assistant-incomplete-edit-model.json")) {
      return mapper.readTree(input);
    }
  }

  private final class RepairProvider implements AssistantModelProvider {
    private final boolean deletion;
    private final ModelWorkspace workspace;
    private final JsonNode initial;
    int calls;
    String hash;

    RepairProvider(boolean deletion, ModelWorkspace workspace, JsonNode initial) {
      this.deletion = deletion;
      this.workspace = workspace;
      this.initial = initial;
    }

    public AssistantProviderMetadata metadata() {
      return new AssistantProviderMetadata("fake", "", "");
    }

    public boolean available() {
      return true;
    }

    public AssistantReply complete(AssistantPrompt prompt) {
      ProviderCallBudget.consume();
      calls++;
      if (calls == 1)
        return reply(
            """
{"action":"plan_model_edit","arguments":{"intent":"EDIT_MODEL","features":["Edit Load ID"],
"reuseTargets":["Load ID"],"newElements":[],"requiredContracts":[],
"slices":[{"label":"Edit item","purpose":"Make the requested edit","requiredContracts":[]}]}}
""");
      if (calls == 2) {
        hash =
            mapper
                .readTree(
                    prompt
                        .user()
                        .substring(
                            prompt.user().lastIndexOf("\n3. Neighborhoods:\n")
                                + "\n3. Neighborhoods:\n".length()))
                .path("neighborhoods")
                .path(0)
                .path("center")
                .path("preconditionHash")
                .asText();
        assertFalse(
            prompt.patchContracts().stream()
                .anyMatch(contract -> "Multiplicity".equals(contract.eClass())));
      } else {
        // The rejected candidate never changed the workspace. The LLM receives all repair facts.
        assertEquals(initial, workspace.snapshot());
        assertTrue(workspace.patch().isEmpty());
        assertTrue(prompt.user().contains("affectedExistingElements"));
        assertTrue(prompt.user().contains("\"lowerBound\":null"));
        assertTrue(prompt.user().contains("source-multiplicity"));
        assertTrue(prompt.user().contains("target-multiplicity"));
        assertEquals("answer_user", prompt.requiredTool());
        return reply(
            """
{"action":"answer_user","arguments":{"message":"The edit was blocked because the model has two incomplete multiplicities. No changes were made."}}
""");
      }
      String updates =
          deletion ? "" : "{\"elementId\":\"load-id\",\"attributes\":{\"name\":\"load identity\"}}";
      String deletes =
          deletion ? "{\"elementId\":\"load-id\",\"preconditionHash\":\"" + hash + "\"}" : "";
      return reply(
          """
          {"action":"commit_model_batch","arguments":{"creates":[],"updates":[%s],
          "connections":[],"deletions":[%s],"evidence":[],"turnComplete":true,
          "planSummary":"Edited Load ID."}}
          """
              .formatted(updates, deletes));
    }

    private AssistantReply reply(String content) {
      return new AssistantReply(content, "fake", "fake");
    }
  }
}
