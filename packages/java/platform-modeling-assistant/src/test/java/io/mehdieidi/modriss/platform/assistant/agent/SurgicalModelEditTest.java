package io.mehdieidi.modriss.platform.assistant.agent;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
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
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class SurgicalModelEditTest {
  private static final String PLAN =
      """
      {"action":"plan_model_edit","arguments":{"intent":"EDIT_MODEL",
      "features":["Edit the requested information item"],"reuseTargets":["Load ID"],
      "newElements":[],"requiredContracts":["InformationItem"],
      "slices":[{"label":"Edit information item","purpose":"Apply the requested edit",
      "requiredContracts":["InformationItem"]}]}}
      """;
  private final ObjectMapper mapper = new ObjectMapper();
  private final ModelService models = mock(ModelService.class);
  private final MetamodelKnowledgeService knowledge =
      new MetamodelKnowledgeService(new AssistantMetamodelSchemaService());

  @Test
  void renameChangesSemanticElementAndPreservesOtherContent() throws Exception {
    var workspace = workspace();
    var provider = new ScriptedProvider(prompt -> rename());
    var result =
        loop(provider, 3)
            .run(
                "rename",
                ModelLevel.CIM,
                "Change the name of 'Load ID' to 'load identity'",
                null,
                workspace);

    assertEquals(
        "load identity",
        workspace.snapshot().path("informationItems").path(0).path("name").asText());
    assertEquals(
        "TEXT", workspace.snapshot().path("informationItems").path(0).path("type").asText());
    assertEquals(
        "Borrower", workspace.snapshot().path("informationItems").path(1).path("name").asText());
    assertEquals(1, result.commandBatch().updates().size());
    assertTrue(result.commandBatch().creates().isEmpty());
    var editPrompt = provider.prompts.get(1);
    assertTrue(editPrompt.existingElementsOnly());
    assertEquals(
        List.of("InformationItem"),
        editPrompt.patchContracts().stream()
            .map(MetamodelKnowledgeService.TypeContract::eClass)
            .toList());
    assertFalse(result.patch().isEmpty());
    assertTrue(result.message().startsWith("Model checkpoint saved"));
    assertFalse(result.message().contains("\"action\""));
    verify(models, never()).validate(any(ModelLevel.class), any());
  }

  @Test
  void deleteUsesInspectedHashAndRemovesSemanticAndCanvasElements() throws Exception {
    var workspace = workspace();
    var provider = new ScriptedProvider(this::deletion);
    var result =
        loop(provider, 3)
            .run(
                "delete",
                ModelLevel.CIM,
                "Delete the 'Load ID' information item",
                null,
                workspace,
                true);

    assertEquals(1, workspace.snapshot().path("informationItems").size());
    assertEquals(
        "other", workspace.snapshot().path("informationItems").path(0).path("id").asText());
    assertEquals(1, workspace.snapshot().path("diagram").path("elements").size());
    assertEquals(
        "other", workspace.snapshot().path("diagram").path("elements").path(0).path("id").asText());
    assertEquals(1, result.commandBatch().deletions().size());
    assertFalse(result.patch().isEmpty());
  }

  @Test
  void deletionStillRequiresDurableConfirmation() throws Exception {
    var workspace = workspace();
    var before = workspace.snapshot();
    var failure =
        assertThrows(
            AgentTurnLoop.TurnExecutionException.class,
            () ->
                loop(new ScriptedProvider(this::deletion), 3)
                    .run(
                        "unconfirmed", ModelLevel.CIM, "Delete 'Load ID'", null, workspace, false));
    assertEquals(409, failure.status());
    assertTrue(failure.getMessage().contains("confirmation"));
    assertEquals(before, workspace.snapshot());
    assertTrue(workspace.patch().isEmpty());
  }

  @Test
  void anExistingElementEditRejectsAReplacementBeforeMutatingTheModel() throws Exception {
    var workspace = workspace();
    var provider =
        new ScriptedProvider(
            prompt ->
                """
{"action":"commit_model_batch","arguments":{"creates":[{
"clientRef":"replacement","eClass":"InformationItem","attributes":{"name":"Replacement","type":"TEXT"},
"owner":"rootId","reference":"informationItems"}],"updates":[],"connections":[],
"deletions":[],"evidence":[],"planSummary":"Replace Load ID","turnComplete":true}}
""");
    provider.repair =
        prompt -> {
          assertEquals(2, workspace.snapshot().path("informationItems").size());
          assertTrue(workspace.patch().isEmpty());
          assertTrue(prompt.user().contains("Do not create replacements"));
          return deletion(prompt);
        };
    var result =
        loop(provider, 3)
            .run("no-replacement", ModelLevel.CIM, "Delete 'Load ID'", null, workspace, true);
    assertTrue(result.commandBatch().creates().isEmpty());
    assertEquals(1, workspace.snapshot().path("informationItems").size());
    assertEquals(
        "other", workspace.snapshot().path("informationItems").path(0).path("id").asText());
  }

  @Test
  void confirmationReplaysTheApprovedBatchWithoutAnotherProviderCall() throws Exception {
    var workspace = workspace();
    var proposalProvider = new ScriptedProvider(this::deletion);
    var failure =
        assertThrows(
            AgentTurnLoop.TurnExecutionException.class,
            () ->
                loop(proposalProvider, 3)
                    .run("propose", ModelLevel.CIM, "Delete 'Load ID'", null, workspace, false));
    var proposal = ((AgentModelTools.ConfirmationRequiredException) failure.getCause()).proposal();
    assertEquals(1, proposal.baseRevision());
    assertEquals("item", proposal.batch().deletions().get(0).elementId());
    assertTrue(workspace.patch().isEmpty());
    var replayProvider =
        new ScriptedProvider(
            prompt -> {
              throw new AssertionError("Confirmation must replay the approved action");
            });
    var replayLoop = loop(replayProvider, 3);
    var result =
        io.mehdieidi.modriss.platform.assistant.application.DurableTurnExecutionContext.with(
            "confirmed",
            null,
            proposal,
            () ->
                replayLoop.run(
                    "confirmed", ModelLevel.CIM, "Delete 'Load ID'", null, workspace, true));
    assertEquals(0, result.providerCalls());
    assertTrue(replayProvider.prompts.isEmpty());
    assertEquals(1, workspace.snapshot().path("informationItems").size());
    assertEquals(
        "other", workspace.snapshot().path("informationItems").path(0).path("id").asText());
    assertNull(
        io.mehdieidi.modriss.platform.assistant.application.DurableTurnExecutionContext
            .pendingBatch());
  }

  @Test
  void replayRejectsAChangedDeletionTargetWithoutCallingTheProvider() throws Exception {
    var workspace = workspace();
    var failure =
        assertThrows(
            AgentTurnLoop.TurnExecutionException.class,
            () ->
                loop(new ScriptedProvider(this::deletion), 3)
                    .run(
                        "propose-stale",
                        ModelLevel.CIM,
                        "Delete 'Load ID'",
                        null,
                        workspace,
                        false));
    var proposal = ((AgentModelTools.ConfirmationRequiredException) failure.getCause()).proposal();
    workspace.mutate(
        new io.mehdieidi.modriss.platform.assistant.domain.SemanticModelPatch(
            List.of(
                new io.mehdieidi.modriss.platform.assistant.domain.SemanticModelPatch.Operation(
                    io.mehdieidi.modriss.platform.assistant.domain.SemanticModelPatch.OperationType
                        .SET_ATTRIBUTE,
                    "item",
                    "InformationItem",
                    mapper.readTree("\"Manually edited\""),
                    null,
                    "name"))));
    var beforeReplay = workspace.snapshot();
    var provider =
        new ScriptedProvider(
            prompt -> {
              throw new AssertionError("A stale proposal cannot be regenerated");
            });
    var replayLoop = loop(provider, 3);
    var conflict =
        assertThrows(
            AgentTurnLoop.TurnExecutionException.class,
            () ->
                io.mehdieidi.modriss.platform.assistant.application.DurableTurnExecutionContext
                    .with(
                        "stale",
                        null,
                        proposal,
                        () ->
                            replayLoop.run(
                                "stale",
                                ModelLevel.CIM,
                                "Delete 'Load ID'",
                                null,
                                workspace,
                                true)));
    assertEquals(409, conflict.status());
    assertEquals(0, conflict.providerCalls());
    assertEquals(beforeReplay, workspace.snapshot());
    assertTrue(provider.prompts.isEmpty());
  }

  @Test
  void lastStepRejectedRenameCannotBecomeASuccessOrRawJsonAnswer() throws Exception {
    var provider =
        new ScriptedProvider(
            prompt ->
                """
                {"action":"commit_model_batch","batch":[{"action":"update","id":"item",
                "attributes":{"name":"load identity"}}],"turnComplete":true}
                """);
    assertUnfinished(provider);
  }

  @Test
  void lastStepRejectedDeletionCannotBecomeASuccessOrRawJsonAnswer() throws Exception {
    var provider =
        new ScriptedProvider(
            prompt ->
                """
                {"action":"commit_model_batch","arguments":{"batch":{"deletes":[{"id":"item",
                "reason":"Remove Load ID"}],"creates":[],"connections":[],"evidence":[]},
                "turnComplete":true}}
                """);
    assertUnfinished(provider);
  }

  @Test
  void lastStepCompletionClaimCannotReplaceThePlannedMutation() throws Exception {
    assertUnfinished(
        new ScriptedProvider(
            prompt ->
                "{\"action\":\"answer_user\",\"arguments\":{\"message\":\"Renamed Load ID.\"}}"));
  }

  @Test
  void lastStepInspectionCannotBecomeASuccessfulExplanation() throws Exception {
    var provider = new ScriptedProvider(prompt -> "{}");
    provider.first = "{\"action\":\"inspect_model\",\"arguments\":{\"id\":\"\"}}";
    var workspace = workspace();
    var failure =
        assertThrows(
            AgentTurnLoop.TurnExecutionException.class,
            () ->
                loop(provider, 1)
                    .run(
                        "inspect",
                        ModelLevel.CIM,
                        "Explain this model",
                        null,
                        workspace,
                        false,
                        AgentTurnLoop.WorkflowMode.EXPLAIN_MODEL));
    assertEquals(502, failure.status());
    assertTrue(workspace.patch().isEmpty());
    verify(models, never()).validateStructural(any(), any(tools.jackson.databind.JsonNode.class));
  }

  @Test
  void llmRepairRetainsRejectedPayloadAndInspectedIdsAndHashes() throws Exception {
    var provider =
        new ScriptedProvider(
            prompt ->
                """
                {"action":"commit_model_batch","arguments":{"deletions":[{"id":"item"}],
                "turnComplete":true}}
                """);
    provider.repair = this::deletion;
    var workspace = workspace();
    var result =
        loop(provider, 3).run("repair", ModelLevel.CIM, "Delete 'Load ID'", null, workspace, true);
    assertEquals(1, result.commandBatch().deletions().size());
    var repairPrompt = provider.prompts.get(2);
    assertTrue(repairPrompt.user().contains("Rejected commit_model_batch arguments"));
    assertTrue(repairPrompt.user().contains("\"id\":\"item\""));
    assertTrue(repairPrompt.user().contains("preconditionHash"));
    assertEquals("commit_model_batch", repairPrompt.requiredTool());
  }

  @Test
  void fencedProtocolMessageIsRepairedByLlmInsteadOfRendered() throws Exception {
    var provider = new ScriptedProvider(prompt -> "{}");
    provider.first =
        mapper.writeValueAsString(
            java.util.Map.of(
                "action",
                "answer_user",
                "arguments",
                java.util.Map.of("message", "```json\n{\"action\":\"commit_model_batch\"}\n```")));
    provider.next =
        prompt ->
            "{\"action\":\"answer_user\",\"arguments\":{\"message\":\"This model contains"
                + " information items.\"}}";
    var result =
        loop(provider, 2)
            .run(
                "fence",
                ModelLevel.CIM,
                "Explain this model",
                null,
                workspace(),
                false,
                AgentTurnLoop.WorkflowMode.EXPLAIN_MODEL);
    assertEquals("This model contains information items.", result.message());
    assertTrue(result.patch().isEmpty());
  }

  private void assertUnfinished(ScriptedProvider provider) throws Exception {
    var workspace = workspace();
    var before = workspace.snapshot();
    var failure =
        assertThrows(
            AgentTurnLoop.TurnExecutionException.class,
            () ->
                loop(provider, 2)
                    .run("unfinished", ModelLevel.CIM, "Edit 'Load ID'", null, workspace, true));
    assertEquals(502, failure.status());
    assertEquals(2, failure.providerCalls());
    assertEquals(before, workspace.snapshot());
    assertTrue(workspace.patch().isEmpty());
  }

  private String rename() {
    return """
    {"action":"commit_model_batch","arguments":{"creates":[],
    "updates":[{"elementId":"item","attributes":{"name":"load identity"},"preconditionHash":""}],
    "connections":[],"deletions":[],"evidence":[],
    "planSummary":"Renamed Load ID to load identity.","turnComplete":true}}
    """;
  }

  private String deletion(AssistantModelProvider.AssistantPrompt prompt) {
    var inspection =
        mapper.readTree(
            prompt
                .user()
                .substring(
                    prompt.user().lastIndexOf("\n3. Neighborhoods:\n")
                        + "\n3. Neighborhoods:\n".length()));
    String hash =
        inspection.path("neighborhoods").path(0).path("center").path("preconditionHash").asText();
    assertFalse(hash.isBlank());
    return """
    {"action":"commit_model_batch","arguments":{"creates":[],"updates":[],
    "connections":[],"deletions":[{"elementId":"item","preconditionHash":"%s"}],
    "evidence":[],"planSummary":"Deleted Load ID.","turnComplete":true}}
    """
        .formatted(hash);
  }

  private AgentTurnLoop loop(ScriptedProvider provider, int steps) {
    when(models.validateStructural(any(), any(tools.jackson.databind.JsonNode.class)))
        .thenReturn(new ModelService.ValidationResult(true, List.of()));
    return new AgentTurnLoop(
        provider,
        new AgentModelTools(new TypeContractService(knowledge), models),
        new MetamodelGuideGenerator(knowledge),
        null,
        Duration.ofSeconds(10),
        Duration.ofSeconds(10),
        steps,
        4);
  }

  private ModelWorkspace workspace() {
    var root =
        mapper.readTree(
            """
{"id":"root","eClass":"CIMModel","modelLevel":"CIM","domainName":"Lending",
"informationItems":[
{"id":"item","eClass":"InformationItem","name":"Load ID","type":"TEXT","required":true,"collection":false},
{"id":"other","eClass":"InformationItem","name":"Borrower","type":"TEXT","required":true,"collection":false}],
"diagram":{"elements":[
{"id":"item","eClass":"InformationItem","name":"Load ID"},
{"id":"other","eClass":"InformationItem","name":"Borrower"}],"relationships":[]}}
""");
    return new ModelWorkspace(ModelLevel.CIM, "m", 1, root, new AssistantPatchCompiler(), null);
  }

  private static final class ScriptedProvider implements AssistantModelProvider {
    String first = PLAN;
    Function<AssistantPrompt, String> next;
    Function<AssistantPrompt, String> repair;
    final List<AssistantPrompt> prompts = new ArrayList<>();

    ScriptedProvider(Function<AssistantPrompt, String> next) {
      this.next = next;
    }

    public AssistantProviderMetadata metadata() {
      return new AssistantProviderMetadata("fake", "", "");
    }

    public boolean available() {
      return true;
    }

    public AssistantReply complete(AssistantPrompt prompt) {
      ProviderCallBudget.consume();
      prompts.add(prompt);
      return new AssistantReply(
          prompts.size() == 1
              ? first
              : prompts.size() > 2 && repair != null ? repair.apply(prompt) : next.apply(prompt),
          "fake",
          "fake");
    }
  }
}
