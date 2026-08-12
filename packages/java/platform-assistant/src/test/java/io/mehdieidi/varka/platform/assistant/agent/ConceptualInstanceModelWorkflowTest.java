package io.mehdieidi.varka.platform.assistant.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.mehdieidi.varka.platform.assistant.application.DurableTurnExecutionContext;
import io.mehdieidi.varka.platform.assistant.config.AiProperties;
import io.mehdieidi.varka.platform.assistant.metamodel.MetamodelGuideGenerator;
import io.mehdieidi.varka.platform.assistant.metamodel.MetamodelKnowledgeService;
import io.mehdieidi.varka.platform.assistant.metamodel.TypeContractService;
import io.mehdieidi.varka.platform.assistant.patch.AssistantMetamodelSchemaService;
import io.mehdieidi.varka.platform.assistant.patch.AssistantPatchCompiler;
import io.mehdieidi.varka.platform.assistant.support.ScriptedAssistantModelProvider;
import io.mehdieidi.varka.platform.assistant.tools.AgentModelTools;
import io.mehdieidi.varka.platform.assistant.turn.AssistantTurnStore;
import io.mehdieidi.varka.platform.assistant.workspace.ModelWorkspace;
import io.mehdieidi.varka.platform.kernel.ModelLevel;
import io.mehdieidi.varka.platform.kernel.PlatformException;
import io.mehdieidi.varka.platform.model.application.ModelService;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class ConceptualInstanceModelWorkflowTest {
  private final ObjectMapper mapper = new ObjectMapper();
  private final TypeContractService contracts =
      new TypeContractService(new MetamodelKnowledgeService(new AssistantMetamodelSchemaService()));

  @Test
  void stagesStableIdSlicesAndAppliesStructuredQualityCorrectionAtomically() throws Exception {
    var provider =
        new ScriptedAssistantModelProvider(
            List.of(
                ScriptedAssistantModelProvider.reply(
                    """
{"types":["Actor"],"objects":[
  {"instanceId":"actor-1","type":"Actor","purpose":"Borrower","ownerInstanceId":"rootId","containment":"CIMModel.actors","referenceTargets":[],"sourceUnitIds":[],"slice":1},
  {"instanceId":"actor-2","type":"Actor","purpose":"Librarian","ownerInstanceId":"rootId","containment":"actors","referenceTargets":[],"sourceUnitIds":[],"slice":2}
]}
"""),
                ScriptedAssistantModelProvider.reply(
                    """
{"actor-1":{"type":"Actor","attributes":[],"associations":{}},"actor-2":{"type":"Actor","attributes":[],"associations":{}}}
"""),
                ScriptedAssistantModelProvider.reply(
                    """
{"actor-1":{"type":"Actor","attributes":[{"dataType":"EString","attributeName":"name","value":"Borrower"}],"associations":{"compositions":[],"references":[]}},"actor-2":{"type":"Actor","attributes":[{"dataType":"EString","attributeName":"name","value":"Staff"}],"associations":{"compositions":[],"references":[]}}}
"""),
                ScriptedAssistantModelProvider.failure(
                    new PlatformException(502, "finish_reason=length")),
                ScriptedAssistantModelProvider.reply(
                    """
{"acceptable":false,"findings":[{"objectIds":["actor-2"],"problem":"Name is not domain-specific","recommendedCorrection":"Use Librarian"}]}
"""),
                ScriptedAssistantModelProvider.reply(
                    """
{"acceptable":false,"findings":[{"objectIds":["actor-2"],"problem":"Name is not domain-specific"}],"corrections":{"actor-2":{"type":"Actor","attributes":[{"dataType":"EString","attributeName":"name","value":"Librarian"}],"associations":{"compositions":[],"references":[]}}}}
""")));
    AiProperties properties = mock(AiProperties.class);
    when(properties.maxProviderCallsPerTurn()).thenReturn(8);
    when(properties.maxProviderCallsSourceTurn()).thenReturn(8);
    var knowledge = new MetamodelKnowledgeService(new AssistantMetamodelSchemaService());
    var workflow =
        new ConceptualInstanceModelWorkflow(
            provider, new MetamodelGuideGenerator(knowledge), contracts, properties);
    ModelService models = mock(ModelService.class);
    when(models.validateStructural(
            org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
        .thenReturn(new ModelService.ValidationResult(true, List.of()));
    var workspace =
        new ModelWorkspace(
            ModelLevel.CIM, "model", 1, emptyCim(), new AssistantPatchCompiler(), null);
    var tools = new AgentModelTools(contracts, models).scoped(ModelLevel.CIM, workspace);

    var result =
        workflow.run("session", ModelLevel.CIM, "Create a library", workspace, tools, false);

    assertEquals(6, result.providerCalls());
    assertEquals("TRUNCATED", result.providerCallDetails().get(3).finishReason());
    assertEquals(2, result.commandBatch().creates().size());
    assertTrue(workspace.snapshot().toString().contains("Librarian"));
    assertTrue(!workspace.snapshot().toString().contains("\"Staff\""));
    assertEquals(0, provider.remainingSteps());
  }

  @Test
  void truncationSplitsTheSliceAndKeepsTheFailedCallInAudit() throws Exception {
    String blueprint =
        """
{"types":["Actor"],"objects":[
  {"instanceId":"actor-1","type":"Actor","purpose":"Borrower","ownerInstanceId":"rootId","containment":"actors","referenceTargets":[],"sourceUnitIds":[],"slice":1},
  {"instanceId":"actor-2","type":"Actor","purpose":"Librarian","ownerInstanceId":"rootId","containment":"actors","referenceTargets":[],"sourceUnitIds":[],"slice":1}
]}
""";
    var provider =
        new ScriptedAssistantModelProvider(
            List.of(
                ScriptedAssistantModelProvider.reply(blueprint),
                ScriptedAssistantModelProvider.failure(
                    new PlatformException(502, "finish_reason=length")),
                ScriptedAssistantModelProvider.reply(actor("actor-1", "Borrower")),
                ScriptedAssistantModelProvider.reply(actor("actor-2", "Librarian")),
                ScriptedAssistantModelProvider.reply(
                    "{\"acceptable\":true,\"findings\":[],\"corrections\":{}}")));
    AiProperties properties = mock(AiProperties.class);
    when(properties.maxProviderCallsPerTurn()).thenReturn(8);
    when(properties.maxProviderCallsSourceTurn()).thenReturn(8);
    // Exercise adaptive splitting from the two-object default used by providers which have not
    // demonstrated DeepSeek's live one-object completion constraint.
    when(properties.model()).thenReturn("gpt-4.1");
    var knowledge = new MetamodelKnowledgeService(new AssistantMetamodelSchemaService());
    var workflow =
        new ConceptualInstanceModelWorkflow(
            provider, new MetamodelGuideGenerator(knowledge), contracts, properties);
    ModelService models = mock(ModelService.class);
    when(models.validateStructural(
            org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
        .thenReturn(new ModelService.ValidationResult(true, List.of()));
    var workspace =
        new ModelWorkspace(
            ModelLevel.CIM, "model", 1, emptyCim(), new AssistantPatchCompiler(), null);

    var result =
        workflow.run(
            "session",
            ModelLevel.CIM,
            "Create a library",
            workspace,
            new AgentModelTools(contracts, models).scoped(ModelLevel.CIM, workspace),
            false);

    assertEquals(5, result.providerCalls());
    assertEquals(5, result.providerCallDetails().size());
    assertEquals("TRUNCATED", result.providerCallDetails().get(1).finishReason());
    assertEquals(2, result.commandBatch().creates().size());
  }

  @Test
  void retriesOneDeepSeekObjectWithACompactPromptAfterTruncation() throws Exception {
    String blueprint =
        """
{"types":["Actor"],"objects":[
  {"instanceId":"actor-1","type":"Actor","purpose":"Borrower","ownerInstanceId":"rootId","containment":"actors","referenceTargets":[],"sourceUnitIds":[],"slice":1}
]}
""";
    var provider =
        new ScriptedAssistantModelProvider(
            List.of(
                ScriptedAssistantModelProvider.reply(blueprint),
                ScriptedAssistantModelProvider.failure(
                    new PlatformException(502, "finish_reason=length")),
                ScriptedAssistantModelProvider.reply(actor("actor-1", "Borrower")),
                ScriptedAssistantModelProvider.reply(
                    "{\"acceptable\":true,\"findings\":[],\"corrections\":{}}")));
    AiProperties properties = mock(AiProperties.class);
    when(properties.maxProviderCallsPerTurn()).thenReturn(14);
    when(properties.maxProviderCallsSourceTurn()).thenReturn(14);
    when(properties.model()).thenReturn("DeepSeek-V4-Flash");
    var knowledge = new MetamodelKnowledgeService(new AssistantMetamodelSchemaService());
    var workflow =
        new ConceptualInstanceModelWorkflow(
            provider, new MetamodelGuideGenerator(knowledge), contracts, properties);
    ModelService models = mock(ModelService.class);
    when(models.validateStructural(
            org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
        .thenReturn(new ModelService.ValidationResult(true, List.of()));
    var workspace =
        new ModelWorkspace(
            ModelLevel.CIM, "model", 1, emptyCim(), new AssistantPatchCompiler(), null);

    var result =
        workflow.run(
            "session",
            ModelLevel.CIM,
            "Create a library",
            workspace,
            new AgentModelTools(contracts, models).scoped(ModelLevel.CIM, workspace),
            false);

    assertEquals(4, result.providerCalls());
    assertEquals("TRUNCATED", result.providerCallDetails().get(1).finishReason());
    assertEquals(1, result.commandBatch().creates().size());
    assertEquals(0, provider.remainingSteps());
  }

  @Test
  void rejectsBlueprintMissingRequiredReferenceClosureBeforeGeneratingSlices() throws Exception {
    String invalidBlueprint =
        """
{"types":["DomainEntity"],"objects":[
  {"instanceId":"book","type":"DomainEntity","purpose":"Book","ownerInstanceId":"rootId","containment":"domainEntities","referenceTargets":[],"sourceUnitIds":[],"slice":1}
]}
""";
    String correctedBlueprint =
        """
{"types":["Actor"],"objects":[
  {"instanceId":"actor-1","type":"Actor","purpose":"Borrower","ownerInstanceId":"rootId","containment":"actors","referenceTargets":[],"sourceUnitIds":[],"slice":1}
]}
""";
    var provider =
        new ScriptedAssistantModelProvider(
            List.of(
                ScriptedAssistantModelProvider.reply(invalidBlueprint),
                ScriptedAssistantModelProvider.reply(correctedBlueprint),
                ScriptedAssistantModelProvider.reply(actor("actor-1", "Borrower")),
                ScriptedAssistantModelProvider.reply(
                    "{\"acceptable\":true,\"findings\":[],\"corrections\":{}}")));
    AiProperties properties = mock(AiProperties.class);
    when(properties.maxProviderCallsPerTurn()).thenReturn(18);
    when(properties.maxProviderCallsSourceTurn()).thenReturn(18);
    when(properties.model()).thenReturn("DeepSeek-V4-Flash");
    var knowledge = new MetamodelKnowledgeService(new AssistantMetamodelSchemaService());
    var workflow =
        new ConceptualInstanceModelWorkflow(
            provider, new MetamodelGuideGenerator(knowledge), contracts, properties);
    ModelService models = mock(ModelService.class);
    when(models.validateStructural(
            org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
        .thenReturn(new ModelService.ValidationResult(true, List.of()));
    var workspace =
        new ModelWorkspace(
            ModelLevel.CIM, "model", 1, emptyCim(), new AssistantPatchCompiler(), null);

    var result =
        workflow.run(
            "session",
            ModelLevel.CIM,
            "Create a library",
            workspace,
            new AgentModelTools(contracts, models).scoped(ModelLevel.CIM, workspace),
            false);

    assertEquals(4, result.providerCalls());
    assertEquals(1, result.commandBatch().creates().size());
    assertEquals("Actor", result.commandBatch().creates().get(0).eClass());
    assertEquals(0, provider.remainingSteps());
  }

  @Test
  void resumesFromPersistedBlueprintAndGeneratedObjectsAfterWorkerRestart() throws Exception {
    String blueprint =
        """
{"types":["Actor"],"objects":[
  {"instanceId":"actor-1","type":"Actor","purpose":"Borrower","ownerInstanceId":"rootId","containment":"actors","referenceTargets":[],"sourceUnitIds":[],"slice":1},
  {"instanceId":"actor-2","type":"Actor","purpose":"Librarian","ownerInstanceId":"rootId","containment":"actors","referenceTargets":[],"sourceUnitIds":[],"slice":1}
]}
""";
    AtomicReference<AssistantTurnStore.Workflow> savedWorkflow = new AtomicReference<>();
    Map<Integer, AssistantTurnStore.WorkItem> savedItems = new java.util.LinkedHashMap<>();
    List<AssistantTurnStore.ProviderCall> savedCalls = new java.util.ArrayList<>();
    AssistantTurnStore store = mock(AssistantTurnStore.class);
    when(store.workflow("turn-restart"))
        .thenAnswer(ignored -> Optional.ofNullable(savedWorkflow.get()));
    when(store.workItems("turn-restart"))
        .thenAnswer(
            ignored ->
                savedItems.values().stream()
                    .sorted(java.util.Comparator.comparingInt(AssistantTurnStore.WorkItem::ordinal))
                    .toList());
    doAnswer(
            invocation -> {
              savedWorkflow.set(invocation.getArgument(0));
              return null;
            })
        .when(store)
        .saveWorkflow(org.mockito.ArgumentMatchers.any());
    doAnswer(
            invocation -> {
              List<AssistantTurnStore.WorkItem> items = invocation.getArgument(1);
              items.forEach(item -> savedItems.put(item.ordinal(), item));
              return null;
            })
        .when(store)
        .saveWorkItems(
            org.mockito.ArgumentMatchers.eq("turn-restart"),
            org.mockito.ArgumentMatchers.anyList());
    doAnswer(
            invocation -> {
              savedCalls.add(invocation.getArgument(1));
              return null;
            })
        .when(store)
        .recordProviderCall(
            org.mockito.ArgumentMatchers.eq("turn-restart"), org.mockito.ArgumentMatchers.any());

    var firstProvider =
        new ScriptedAssistantModelProvider(
            List.of(
                ScriptedAssistantModelProvider.reply(blueprint),
                ScriptedAssistantModelProvider.reply(actor("actor-1", "Borrower")),
                ScriptedAssistantModelProvider.failure(new PlatformException(503, "restart"))));
    AiProperties properties = properties();
    var knowledge = new MetamodelKnowledgeService(new AssistantMetamodelSchemaService());
    ModelService models = mock(ModelService.class);
    when(models.validateStructural(
            org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
        .thenReturn(new ModelService.ValidationResult(true, List.of()));
    try {
      var firstWorkspace =
          new ModelWorkspace(
              ModelLevel.CIM, "model", 1, emptyCim(), new AssistantPatchCompiler(), null);
      var first =
          new ConceptualInstanceModelWorkflow(
              firstProvider, new MetamodelGuideGenerator(knowledge), contracts, properties, store);
      assertThrows(
          AgentTurnLoop.TurnExecutionException.class,
          () ->
              DurableTurnExecutionContext.with(
                  "turn-restart",
                  () ->
                      first.run(
                          "session",
                          ModelLevel.CIM,
                          "Create a library",
                          firstWorkspace,
                          new AgentModelTools(contracts, models)
                              .scoped(ModelLevel.CIM, firstWorkspace),
                          false)));

      assertEquals("CONCEPTUAL_GENERATION", savedWorkflow.get().workflowKind());
      assertEquals(1, savedWorkflow.get().plan().path("sliceSize").asInt());
      assertEquals(
          "actors",
          savedWorkflow
              .get()
              .plan()
              .path("blueprint")
              .path("objects")
              .get(0)
              .path("containment")
              .asText());
      assertTrue(savedItems.get(1).payload().path("generated").isObject());
      assertTrue(savedItems.get(2).payload().path("generated").isMissingNode());

      var resumedProvider =
          new ScriptedAssistantModelProvider(
              List.of(
                  ScriptedAssistantModelProvider.reply(actor("actor-2", "Librarian")),
                  ScriptedAssistantModelProvider.reply(
                      "{\"acceptable\":true,\"findings\":[],\"corrections\":{}}")));
      var resumedWorkspace =
          new ModelWorkspace(
              ModelLevel.CIM, "model", 1, emptyCim(), new AssistantPatchCompiler(), null);
      var resumed =
          new ConceptualInstanceModelWorkflow(
              resumedProvider,
              new MetamodelGuideGenerator(knowledge),
              contracts,
              properties,
              store);
      var result =
          DurableTurnExecutionContext.with(
              "turn-restart",
              () ->
                  resumed.run(
                      "session",
                      ModelLevel.CIM,
                      "Create a library",
                      resumedWorkspace,
                      new AgentModelTools(contracts, models)
                          .scoped(ModelLevel.CIM, resumedWorkspace),
                      false));

      assertEquals(5, result.providerCalls());
      assertEquals(2, result.providerCallDetails().size());
      assertEquals(5, savedCalls.size());
      assertEquals(2, result.commandBatch().creates().size());
      assertEquals(0, resumedProvider.remainingSteps());
    } finally {
      // DurableTurnExecutionContext restores its thread-local value after each invocation.
    }
  }

  private AiProperties properties() {
    AiProperties properties = mock(AiProperties.class);
    when(properties.maxProviderCallsPerTurn()).thenReturn(8);
    when(properties.maxProviderCallsSourceTurn()).thenReturn(8);
    when(properties.model()).thenReturn("DeepSeek-V4-Flash");
    return properties;
  }

  private String actor(String id, String name) {
    return "{\""
        + id
        + "\":{\"type\":\"Actor\",\"attributes\":[{\"dataType\":\"EString\","
        + "\"attributeName\":\"name\",\"value\":\""
        + name
        + "\"}],\"associations\":{\"compositions\":[],\"references\":[]}}}";
  }

  @Test
  void compilesPaperParentToChildCompositionsInDependencyOrder() throws Exception {
    var conceptual =
        ConceptualInstanceModelWorkflow.ConceptualModel.parse(
            mapper,
            """
{
  "contract": {
    "type": "FunctionContract",
    "attributes": [],
    "associations": {"compositions": [], "references": []}
  },
  "function": {
    "type": "Function",
    "attributes": [
      {"dataType": "", "attributeName": "name", "value": "Process order"},
      {"dataType": "", "attributeName": "functionKind", "value": "COMMAND_HANDLER"}
    ],
    "associations": {
      "compositions": [
        {"associationName": "contract", "associatedClassName": "FunctionContract", "instanceID": "contract"}
      ],
      "references": []
    }
  },
  "service": {
    "type": "ServerlessService",
    "attributes": [
      {"dataType": "", "attributeName": "name", "value": "Orders"},
      {"dataType": "", "attributeName": "boundaryType", "value": "CAPABILITY_BASED"}
    ],
    "associations": {
      "compositions": [
        {"associationName": "functions", "associatedClassName": "Function", "instanceID": "function"}
      ],
      "references": []
    }
  }
}
""");

    var batch = conceptual.commands(emptyPim(), contracts, ModelLevel.PIM);

    assertEquals(
        java.util.List.of("service", "function", "contract"),
        batch.creates().stream().map(create -> create.clientRef()).toList());
    assertEquals("rootId", batch.creates().get(0).owner());
    assertEquals("services", batch.creates().get(0).reference());
    assertEquals("service", batch.creates().get(1).owner());
    assertEquals("functions", batch.creates().get(1).reference());
    assertEquals("function", batch.creates().get(2).owner());
    assertEquals("contract", batch.creates().get(2).reference());
  }

  @Test
  void updatesExactPersistedIdWithoutDeletingOmittedExistingContent() throws Exception {
    var conceptual =
        ConceptualInstanceModelWorkflow.ConceptualModel.parse(
            mapper,
            """
            {
              "actor-existing": {
                "type": "Actor",
                "attributes": [
                  {"dataType": "", "attributeName": "authenticationExpectation", "value": "MFA"}
                ],
                "associations": {"compositions": [], "references": []}
              }
            }
            """);

    var batch = conceptual.commands(existingCim(), contracts, ModelLevel.CIM);

    assertEquals(1, batch.updates().size());
    assertEquals("actor-existing", batch.updates().get(0).elementId());
    assertTrue(batch.creates().isEmpty());
    assertTrue(batch.deletions().isEmpty());
  }

  @Test
  void rejectsWrongCompositionDirectionWithPreciseOwnerDiagnostic() throws Exception {
    var conceptual =
        ConceptualInstanceModelWorkflow.ConceptualModel.parse(
            mapper,
            """
{
  "service": {
    "type": "ServerlessService",
    "attributes": [],
    "associations": {"compositions": [], "references": []}
  },
  "function": {
    "type": "Function",
    "attributes": [],
    "associations": {
      "compositions": [
        {"associationName": "functions", "associatedClassName": "ServerlessService", "instanceID": "service"}
      ],
      "references": []
    }
  }
}
""");

    PlatformException error =
        assertThrows(
            PlatformException.class,
            () -> conceptual.commands(emptyPim(), contracts, ModelLevel.PIM));

    assertTrue(error.getMessage().contains("not writable on Function"));
    assertTrue(error.getMessage().contains("Legal compositions"));
  }

  @Test
  void compilesSourceEvidenceWithoutChangingBusinessContent() throws Exception {
    var conceptual =
        ConceptualInstanceModelWorkflow.ConceptualModel.parse(
            mapper,
            """
{
  "actor": {
    "type": "Actor",
    "attributes": [{"dataType": "", "attributeName": "name", "value": "Visitor"}],
    "associations": {"compositions": [], "references": []},
    "evidence": [
      {"sourceUnitId": "turn:source-1", "requirementId": "US-1", "kind": "SOURCE_GROUNDED", "assumption": ""}
    ]
  }
}
""");

    var batch = conceptual.commands(emptyCim(), contracts, ModelLevel.CIM);

    assertEquals(1, batch.evidence().size());
    assertEquals("actor", batch.evidence().get(0).elementRef());
    assertEquals("turn:source-1", batch.evidence().get(0).sourceUnitId());
  }

  private tools.jackson.databind.JsonNode emptyCim() throws Exception {
    return mapper.readTree(
        """
{"id":"root","eClass":"CIMModel","modelLevel":"CIM","diagram":{"elements":[],"relationships":[]}}
""");
  }

  private tools.jackson.databind.JsonNode existingCim() throws Exception {
    return mapper.readTree(
        """
        {"id":"root","eClass":"CIMModel","modelLevel":"CIM",
         "actors":[{"id":"actor-existing","eClass":"Actor","name":"Customer"}],
         "goals":[{"id":"goal-existing","eClass":"BusinessGoal","name":"Place order"}],
         "diagram":{"elements":[],"relationships":[]}}
        """);
  }

  private tools.jackson.databind.JsonNode emptyPim() throws Exception {
    return mapper.readTree(
        """
{"id":"root","eClass":"PIMModel","modelLevel":"PIM","diagram":{"elements":[],"relationships":[]}}
""");
  }
}
