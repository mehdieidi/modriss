package io.mehdieidi.varka.platform.assistant.agent;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
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
  void admitsNonCompressedSourceLedgersAndBlueprints() throws Exception {
    var obligationSchema =
        mapper.readTree(ConceptualInstanceModelWorkflow.obligationLedgerSchema());
    var blueprintSchema = mapper.readTree(ConceptualInstanceModelWorkflow.blueprintSchema());
    var blueprintPatchSchema =
        mapper.readTree(ConceptualInstanceModelWorkflow.blueprintPatchSchema());
    var reviewSchema = mapper.readTree(ConceptualInstanceModelWorkflow.obligationReviewSchema());
    var blueprintReviewSchema =
        mapper.readTree(ConceptualInstanceModelWorkflow.blueprintCompletenessSchema());

    assertEquals(64, obligationSchema.at("/properties/obligations/maxItems").asInt());
    assertEquals(96, blueprintSchema.at("/properties/objects/maxItems").asInt());
    assertEquals(96, blueprintSchema.at("/properties/types/maxItems").asInt());
    assertEquals(24, blueprintPatchSchema.at("/properties/upsertObjects/maxItems").asInt());
    assertEquals(64, reviewSchema.at("/properties/coverage/maxItems").asInt());
    assertTrue(blueprintReviewSchema.at("/properties/acceptable").isObject());
    assertEquals(12, blueprintReviewSchema.at("/properties/findings/maxItems").asInt());
  }

  @Test
  void preservesEveryLlmBlueprintSourceAllocationWhenASliceOmitsEvidence() throws Exception {
    var provider =
        new ScriptedAssistantModelProvider(
            List.of(
                ScriptedAssistantModelProvider.reply("{\"types\":[\"Actor\"]}"),
                ScriptedAssistantModelProvider.reply(
                    """
{"types":["Actor"],"objects":[
  {"instanceId":"patient","type":"Actor","purpose":"Patient","ownerInstanceId":"rootId","containment":"actors","referenceTargets":[],"obligationIds":[],"sourceUnitIds":["src-1"],"slice":1}
]}
"""),
                ScriptedAssistantModelProvider.reply(actor("patient", "Patient"))));
    AiProperties properties = mock(AiProperties.class);
    when(properties.maxProviderCallsPerTurn()).thenReturn(10);
    when(properties.maxProviderCallsSourceTurn()).thenReturn(10);
    when(properties.maxRepairAttempts()).thenReturn(2);
    when(properties.model()).thenReturn("Gemma-4-31B-IT");
    when(properties.llmReviewEnabled()).thenReturn(false);
    var knowledge = new MetamodelKnowledgeService(new AssistantMetamodelSchemaService());
    var workflow =
        new ConceptualInstanceModelWorkflow(
            provider, new MetamodelGuideGenerator(knowledge), contracts, properties);
    ModelService models = mock(ModelService.class);
    when(models.validateStructural(
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(tools.jackson.databind.JsonNode.class)))
        .thenReturn(new ModelService.ValidationResult(true, List.of()));
    var workspace =
        new ModelWorkspace(
            ModelLevel.CIM, "model", 1, emptyCim(), new AssistantPatchCompiler(), null);

    var result =
        workflow.run(
            "session",
            ModelLevel.CIM,
            "SOURCE SPECIFICATION (authoritative input)\n"
                + "<source-unit id=\"src-1\">Patient</source-unit>",
            workspace,
            new AgentModelTools(contracts, models).scoped(ModelLevel.CIM, workspace),
            false);

    assertEquals(1, result.commandBatch().evidence().size());
    assertEquals("src-1", result.commandBatch().evidence().get(0).sourceUnitId());
    assertEquals("SOURCE_GROUNDED", result.commandBatch().evidence().get(0).kind());
    assertEquals(0, provider.remainingSteps());
  }

  @Test
  void repairsInventedSourceEvidenceAcrossMultipleSliceCorrections() throws Exception {
    String blueprint =
        """
{"types":["ServerlessService"],"objects":[
  {"instanceId":"service-1","type":"ServerlessService","purpose":"Appointment service","ownerInstanceId":"rootId","containment":"services","referenceTargets":[],"sourceUnitIds":[],"slice":1}
]}
""";
    String objectPrefix =
        """
{"service-1":{"type":"ServerlessService","attributes":[
  {"attributeName":"name","value":"Appointment service"},
  {"attributeName":"boundaryType","value":"CAPABILITY_BASED"}
],"associations":{"compositions":[],"references":[]},"evidence":[
""";
    var provider =
        new ScriptedAssistantModelProvider(
            List.of(
                ScriptedAssistantModelProvider.reply("{\"types\":[\"ServerlessService\"]}"),
                ScriptedAssistantModelProvider.reply(blueprint),
                ScriptedAssistantModelProvider.reply(
                    objectPrefix
                        + "{\"sourceUnitId\":\"OBL-1\",\"requirementId\":\"R1\",\"kind\":\"source_grounded\",\"assumption\":\"\"}]}}"),
                ScriptedAssistantModelProvider.reply(
                    objectPrefix
                        + "{\"sourceUnitId\":\"OBL-1\",\"requirementId\":\"R1\",\"kind\":\"SOURCE_GROUNDED\",\"assumption\":\"\"}]}}"),
                ScriptedAssistantModelProvider.reply(
                    objectPrefix
                        + "{\"sourceUnitId\":\"\",\"requirementId\":\"R1\",\"kind\":\"INFERRED\",\"assumption\":\"The"
                        + " user requested an appointment service.\"}]}}")));
    AiProperties properties = mock(AiProperties.class);
    when(properties.maxProviderCallsPerTurn()).thenReturn(10);
    when(properties.maxProviderCallsSourceTurn()).thenReturn(10);
    when(properties.maxRepairAttempts()).thenReturn(2);
    when(properties.model()).thenReturn("Gemma-4-31B-IT");
    when(properties.llmReviewEnabled()).thenReturn(false);
    var knowledge = new MetamodelKnowledgeService(new AssistantMetamodelSchemaService());
    var workflow =
        new ConceptualInstanceModelWorkflow(
            provider, new MetamodelGuideGenerator(knowledge), contracts, properties);
    ModelService models = mock(ModelService.class);
    when(models.validateStructural(
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(tools.jackson.databind.JsonNode.class)))
        .thenReturn(new ModelService.ValidationResult(true, List.of()));
    var workspace =
        new ModelWorkspace(
            ModelLevel.PIM, "model", 1, emptyPim(), new AssistantPatchCompiler(), null);

    var result =
        workflow.run(
            "session",
            ModelLevel.PIM,
            "Create an appointment service",
            workspace,
            new AgentModelTools(contracts, models).scoped(ModelLevel.PIM, workspace),
            false);

    assertTrue(provider.prompts().get(2).user().contains("EXACT SOURCE-UNIT ALLOWLIST"));
    assertTrue(provider.prompts().get(3).user().contains("unknown sourceUnitId 'OBL-1'"));
    assertTrue(provider.prompts().get(4).user().contains("unknown sourceUnitId 'OBL-1'"));
    assertEquals("INFERRED", result.commandBatch().evidence().get(0).kind());
    assertEquals(0, provider.remainingSteps());
  }

  @Test
  void requiresEveryJointlyNecessaryEClassFromTheLlmObligationLedger() throws Exception {
    var provider =
        new ScriptedAssistantModelProvider(
            List.of(
                ScriptedAssistantModelProvider.reply(
                    "{\"obligations\":[{\"id\":\"OBL-1\",\"obligation\":\"Publish and consume order"
                        + " events\",\"importance\":\"MANDATORY\",\"sourceUnitIds\":[],\"expectedEClasses\":[\"EventBus\",\"EventType\"]}]}"),
                ScriptedAssistantModelProvider.reply("{\"types\":[\"EventBus\"]}"),
                ScriptedAssistantModelProvider.reply("{\"types\":[\"EventBus\",\"EventType\"]}"),
                ScriptedAssistantModelProvider.failure(new PlatformException(502, "stop"))));
    AssistantTurnStore store = mock(AssistantTurnStore.class);
    when(store.workflow("turn-joint-types")).thenReturn(Optional.empty());
    when(store.workItems("turn-joint-types")).thenReturn(List.of());
    AiProperties properties = properties();
    var knowledge = new MetamodelKnowledgeService(new AssistantMetamodelSchemaService());
    ModelService models = mock(ModelService.class);
    var workspace =
        new ModelWorkspace(
            ModelLevel.PIM, "model", 1, emptyPim(), new AssistantPatchCompiler(), null);
    var workflow =
        new ConceptualInstanceModelWorkflow(
            provider, new MetamodelGuideGenerator(knowledge), contracts, properties, store);

    assertThrows(
        AgentTurnLoop.TurnExecutionException.class,
        () ->
            DurableTurnExecutionContext.with(
                "turn-joint-types",
                () ->
                    workflow.run(
                        "session",
                        ModelLevel.PIM,
                        "Create event publication and consumption",
                        workspace,
                        new AgentModelTools(contracts, models).scoped(ModelLevel.PIM, workspace),
                        false)));

    assertTrue(provider.prompts().get(2).user().contains("OBL-1=[EventType]"));
    assertEquals("conceptual_blueprint", provider.prompts().get(3).requiredTool());
    assertTrue(
        provider
            .prompts()
            .get(0)
            .system()
            .contains("both a legal behavioral carrier and the metamodel type"));
  }

  @Test
  void appliesAndStructurallyChecksTheFinalBoundedBlueprintCritiqueRepair() throws Exception {
    var provider =
        new ScriptedAssistantModelProvider(
            List.of(
                ScriptedAssistantModelProvider.reply(
                    "{\"obligations\":[{\"id\":\"OBL-1\",\"obligation\":\"Authorize the"
                        + " existing customer\",\"importance\":\"MANDATORY\","
                        + "\"minimumEvidenceObjects\":1,\"sourceUnitIds\":[],"
                        + "\"expectedEClasses\":[\"Actor\"]}]}"),
                ScriptedAssistantModelProvider.reply("{\"types\":[\"Actor\"]}"),
                ScriptedAssistantModelProvider.reply(
                    "{\"types\":[\"Actor\"],\"objects\":[{\"instanceId\":\"actor-new\","
                        + "\"type\":\"Actor\",\"purpose\":\"Customer\","
                        + "\"ownerInstanceId\":\"rootId\",\"containment\":\"actors\","
                        + "\"referenceTargets\":[],\"obligationIds\":[\"OBL-1\"],"
                        + "\"sourceUnitIds\":[],\"slice\":1}]}"),
                ScriptedAssistantModelProvider.reply(
                    "{\"acceptable\":false,\"findings\":[{\"missingConcept\":\"Persisted"
                        + " customer reuse\",\"reason\":\"The plan duplicates actor-existing\","
                        + "\"recommendedCorrection\":\"Reuse actor-existing\"}]}"),
                ScriptedAssistantModelProvider.reply(
                    "{\"removeObjectIds\":[\"actor-new\"],\"upsertObjects\":[{"
                        + "\"instanceId\":\"actor-existing\",\"type\":\"Actor\","
                        + "\"purpose\":\"Authorize existing customer\","
                        + "\"ownerInstanceId\":\"rootId\",\"containment\":\"actors\","
                        + "\"referenceTargets\":[],\"obligationIds\":[\"OBL-1\"],"
                        + "\"sourceUnitIds\":[],\"slice\":1}]}"),
                ScriptedAssistantModelProvider.reply(
                    """
{"acceptable":false,"findings":[{"missingConcept":"Explicit authorization purpose","reason":"The reuse intent is too terse","recommendedCorrection":"Clarify the authorization purpose"}]}
"""),
                ScriptedAssistantModelProvider.reply(
                    """
{"removeObjectIds":[],"upsertObjects":[{"instanceId":"actor-existing","type":"Actor","purpose":"Authorize the persisted customer","ownerInstanceId":"rootId","containment":"actors","referenceTargets":[],"obligationIds":["OBL-1"],"sourceUnitIds":[],"slice":1}]}
"""),
                ScriptedAssistantModelProvider.reply(
                    """
{"acceptable":false,"findings":[{"missingConcept":"Authentication strength","reason":"The purpose does not name MFA","recommendedCorrection":"Plan strong authentication"}]}
"""),
                ScriptedAssistantModelProvider.reply(
                    """
{"removeObjectIds":[],"upsertObjects":[{"instanceId":"actor-existing","type":"Actor","purpose":"Authorize the persisted customer with MFA","ownerInstanceId":"rootId","containment":"actors","referenceTargets":[],"obligationIds":["OBL-1"],"sourceUnitIds":[],"slice":1}]}
"""),
                ScriptedAssistantModelProvider.reply(
                    "{\"actor-existing\":{\"type\":\"Actor\",\"attributes\":[{"
                        + "\"attributeName\":\"authenticationExpectation\",\"value\":\"MFA\"}],"
                        + "\"associations\":{\"compositions\":[],\"references\":[]}}}"),
                ScriptedAssistantModelProvider.reply(
                    "{\"acceptable\":true,\"coverage\":[{\"obligationId\":\"OBL-1\","
                        + "\"state\":\"SATISFIED\",\"evidenceObjectIds\":[\"actor-existing\"],"
                        + "\"evidenceRelationships\":[],\"explanation\":\"Existing customer is"
                        + " updated\"}],\"findings\":[]}")));
    AssistantTurnStore store = mock(AssistantTurnStore.class);
    when(store.workflow("turn-recheck")).thenReturn(Optional.empty());
    when(store.workItems("turn-recheck")).thenReturn(List.of());
    AiProperties properties = properties();
    when(properties.llmReviewEnabled()).thenReturn(true);
    when(properties.maxProviderCallsPerTurn()).thenReturn(14);
    when(properties.maxProviderCallsSourceTurn()).thenReturn(14);
    var knowledge = new MetamodelKnowledgeService(new AssistantMetamodelSchemaService());
    ModelService models = mock(ModelService.class);
    when(models.validateStructural(
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(tools.jackson.databind.JsonNode.class)))
        .thenReturn(new ModelService.ValidationResult(true, List.of()));
    var workspace =
        new ModelWorkspace(
            ModelLevel.CIM, "model", 1, existingCim(), new AssistantPatchCompiler(), null);
    var workflow =
        new ConceptualInstanceModelWorkflow(
            provider, new MetamodelGuideGenerator(knowledge), contracts, properties, store);

    var result =
        DurableTurnExecutionContext.with(
            "turn-recheck",
            () ->
                workflow.run(
                    "session",
                    ModelLevel.CIM,
                    "Authorize the existing customer",
                    workspace,
                    new AgentModelTools(contracts, models).scoped(ModelLevel.CIM, workspace),
                    false));

    assertEquals("conceptual_blueprint_review", provider.prompts().get(3).requiredTool());
    assertTrue(
        provider
            .prompts()
            .get(3)
            .system()
            .contains("exact persisted-ID blueprint record is the required notation"));
    assertTrue(
        provider
            .prompts()
            .get(3)
            .system()
            .contains("referenceTargets are intentionally unlabelled stable target IDs"));
    assertTrue(provider.prompts().get(3).user().contains("AUTHORITATIVE WRITABLE ECORE CONTRACTS"));
    assertTrue(
        provider
            .prompts()
            .get(3)
            .user()
            .contains("CONCRETE OPTIONS FOR ABSTRACT REQUIRED TARGETS"));
    assertEquals("conceptual_blueprint_patch", provider.prompts().get(4).requiredTool());
    assertEquals("conceptual_blueprint_review", provider.prompts().get(5).requiredTool());
    assertEquals("conceptual_blueprint_patch", provider.prompts().get(6).requiredTool());
    assertEquals("conceptual_blueprint_review", provider.prompts().get(7).requiredTool());
    assertEquals("conceptual_blueprint_patch", provider.prompts().get(8).requiredTool());
    assertEquals("conceptual_instance_slice", provider.prompts().get(9).requiredTool());
    assertEquals(1, result.commandBatch().updates().size());
    assertEquals("actor-existing", result.commandBatch().updates().get(0).elementId());
    assertEquals(0, provider.remainingSteps());
  }

  @Test
  void acceptsObligationEvidenceRelationshipsToPersistedTargets() throws Exception {
    var provider =
        new ScriptedAssistantModelProvider(
            List.of(
                ScriptedAssistantModelProvider.reply(
                    """
{"obligations":[{"id":"OBL-1","obligation":"Compensate through the existing service","importance":"MANDATORY","minimumEvidenceObjects":1,"sourceUnitIds":[],"expectedEClasses":["CompensationPolicy"]}]}
"""),
                ScriptedAssistantModelProvider.reply("{\"types\":[\"CompensationPolicy\"]}"),
                ScriptedAssistantModelProvider.reply(
                    """
{"types":["CompensationPolicy"],"objects":[{"instanceId":"compensation-new","type":"CompensationPolicy","purpose":"Compensate through the existing order service","ownerInstanceId":"rootId","containment":"policies","referenceTargets":["service-existing"],"obligationIds":["OBL-1"],"sourceUnitIds":[],"slice":1}]}
"""),
                ScriptedAssistantModelProvider.reply("{\"acceptable\":true,\"findings\":[]}"),
                ScriptedAssistantModelProvider.reply(
                    """
{"compensation-new":{"type":"CompensationPolicy","attributes":[{"attributeName":"name","value":"Order Compensation"},{"attributeName":"compensationStrategy","value":"REFUND"}],"associations":{"compositions":[],"references":[{"associationName":"attachedTo","associatedClassName":"PolicyTarget","instanceID":"service-existing"}]}}}
"""),
                ScriptedAssistantModelProvider.reply(
                    """
{"acceptable":true,"coverage":[{"obligationId":"OBL-1","state":"SATISFIED","evidenceObjectIds":["compensation-new"],"evidenceRelationships":[{"sourceId":"compensation-new","feature":"attachedTo","targetId":"service-existing"}],"explanation":"Policy reuses the persisted service"}],"findings":[]}
""")));
    AssistantTurnStore store = mock(AssistantTurnStore.class);
    when(store.workflow("turn-persisted-evidence")).thenReturn(Optional.empty());
    when(store.workItems("turn-persisted-evidence")).thenReturn(List.of());
    AiProperties properties = properties();
    ModelService models = mock(ModelService.class);
    when(models.validateStructural(
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(tools.jackson.databind.JsonNode.class)))
        .thenReturn(new ModelService.ValidationResult(true, List.of()));
    var current =
        mapper.readTree(
            """
{"id":"root","eClass":"PIMModel","modelLevel":"PIM","services":[{"id":"service-existing","eClass":"ServerlessService","name":"Orders"}],"diagram":{"elements":[],"relationships":[]}}
""");
    var workspace =
        new ModelWorkspace(ModelLevel.PIM, "model", 1, current, new AssistantPatchCompiler(), null);
    var workflow =
        new ConceptualInstanceModelWorkflow(
            provider,
            new MetamodelGuideGenerator(
                new MetamodelKnowledgeService(new AssistantMetamodelSchemaService())),
            contracts,
            properties,
            store);

    var result =
        DurableTurnExecutionContext.with(
            "turn-persisted-evidence",
            () ->
                workflow.run(
                    "session",
                    ModelLevel.PIM,
                    "Add compensation through the existing service",
                    workspace,
                    new AgentModelTools(contracts, models).scoped(ModelLevel.PIM, workspace),
                    false));

    assertEquals(1, result.commandBatch().creates().size());
    assertEquals(1, result.commandBatch().connections().size());
    assertEquals("service-existing", result.commandBatch().connections().get(0).target());
    assertEquals(0, provider.remainingSteps());
  }

  @Test
  void doesNotMisclassifyProviderAccountFailureAsBlueprintRepairFeedback() throws Exception {
    var provider =
        new ScriptedAssistantModelProvider(
            List.of(
                ScriptedAssistantModelProvider.reply(
                    "{\"types\":[\"CIMModel\",\"Actor\",\"BusinessGoal\"]}"),
                ScriptedAssistantModelProvider.reply(
                    "{\"types\":[\"Actor\"],\"objects\":[{\"instanceId\":\"actor-1\","
                        + "\"type\":\"Actor\",\"purpose\":\"Borrower\","
                        + "\"ownerInstanceId\":\"rootId\",\"containment\":\"actors\","
                        + "\"referenceTargets\":[],\"sourceUnitIds\":[],\"slice\":1}]}"),
                ScriptedAssistantModelProvider.failure(
                    new PlatformException(400, "AI provider account has no remaining credit.")),
                ScriptedAssistantModelProvider.reply(
                    "{\"removeObjectIds\":[],\"upsertObjects\":[]}")));
    AiProperties properties = properties();
    when(properties.llmReviewEnabled()).thenReturn(false);
    var knowledge = new MetamodelKnowledgeService(new AssistantMetamodelSchemaService());
    var workflow =
        new ConceptualInstanceModelWorkflow(
            provider, new MetamodelGuideGenerator(knowledge), contracts, properties);
    ModelService models = mock(ModelService.class);
    var workspace =
        new ModelWorkspace(
            ModelLevel.CIM, "model", 1, emptyCim(), new AssistantPatchCompiler(), null);

    AgentTurnLoop.TurnExecutionException failure =
        assertThrows(
            AgentTurnLoop.TurnExecutionException.class,
            () ->
                workflow.run(
                    "session",
                    ModelLevel.CIM,
                    "Create a borrower and borrowing goal",
                    workspace,
                    new AgentModelTools(contracts, models).scoped(ModelLevel.CIM, workspace),
                    false));

    assertTrue(failure.getMessage().contains("no remaining credit"));
    assertEquals(3, provider.prompts().size());
    assertEquals(1, provider.remainingSteps());
  }

  @Test
  void exposesAbstractWorkflowStepOptionsRejectsTheAbstractTypeAndCompilesTheLlmSubtype()
      throws Exception {
    var provider =
        new ScriptedAssistantModelProvider(
            List.of(
                ScriptedAssistantModelProvider.reply("{\"types\":[\"Workflow\"]}"),
                ScriptedAssistantModelProvider.reply(
                    """
{"types":["ServerlessService","Workflow","WorkflowStep"],"objects":[
  {"instanceId":"service-1","type":"ServerlessService","purpose":"Order service","ownerInstanceId":"rootId","containment":"services","referenceTargets":[],"sourceUnitIds":[],"slice":1},
  {"instanceId":"workflow-1","type":"Workflow","purpose":"Order workflow","ownerInstanceId":"service-1","containment":"workflows","referenceTargets":[],"sourceUnitIds":[],"slice":2},
  {"instanceId":"step-1","type":"WorkflowStep","purpose":"Handle order command","ownerInstanceId":"workflow-1","containment":"steps","referenceTargets":[],"sourceUnitIds":[],"slice":3}
]}
"""),
                ScriptedAssistantModelProvider.reply(
                    """
{"removeObjectIds":[],"upsertObjects":[
  {"instanceId":"step-1","type":"TaskStep","purpose":"Handle order command","ownerInstanceId":"workflow-1","containment":"steps","referenceTargets":[],"sourceUnitIds":[],"slice":3}
]}
"""),
                ScriptedAssistantModelProvider.reply(
                    """
{"service-1":{"type":"ServerlessService","attributes":[
  {"attributeName":"name","value":"Order service"},
  {"attributeName":"boundaryType","value":"CAPABILITY_BASED"}
],"associations":{"compositions":[{"associationName":"workflows","associatedClassName":"Workflow","instanceID":"workflow-1"}],"references":[]}}}
"""),
                ScriptedAssistantModelProvider.reply(
                    """
{"workflow-1":{"type":"Workflow","attributes":[
  {"attributeName":"name","value":"Order workflow"},
  {"attributeName":"workflowKind","value":"ORCHESTRATION"}
],"associations":{"compositions":[{"associationName":"steps","associatedClassName":"TaskStep","instanceID":"step-1"}],"references":[]}}}
"""),
                ScriptedAssistantModelProvider.reply(
                    """
{"step-1":{"type":"TaskStep","attributes":[{"attributeName":"name","value":"Handle order command"}],"associations":{"compositions":[],"references":[]}}}
""")));
    AiProperties properties = mock(AiProperties.class);
    when(properties.maxProviderCallsPerTurn()).thenReturn(10);
    when(properties.maxProviderCallsSourceTurn()).thenReturn(10);
    when(properties.model()).thenReturn("DeepSeek-V4-Flash");
    when(properties.llmReviewEnabled()).thenReturn(false);
    var knowledge = new MetamodelKnowledgeService(new AssistantMetamodelSchemaService());
    var workflow =
        new ConceptualInstanceModelWorkflow(
            provider, new MetamodelGuideGenerator(knowledge), contracts, properties);
    ModelService models = mock(ModelService.class);
    when(models.validateStructural(
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(tools.jackson.databind.JsonNode.class)))
        .thenReturn(new ModelService.ValidationResult(true, List.of()));
    var workspace =
        new ModelWorkspace(
            ModelLevel.PIM, "model", 1, emptyPim(), new AssistantPatchCompiler(), null);

    var result =
        workflow.run(
            "session",
            ModelLevel.PIM,
            "Create an order workflow with command-handling behavior",
            workspace,
            new AgentModelTools(contracts, models).scoped(ModelLevel.PIM, workspace),
            false);

    String blueprintPrompt = provider.prompts().get(1).user();
    assertTrue(
        blueprintPrompt.contains("Workflow.steps requires one or more concrete WorkflowStep"));
    assertTrue(blueprintPrompt.contains("StartStep"));
    assertTrue(blueprintPrompt.contains("TaskStep"));
    assertTrue(
        provider
            .prompts()
            .get(2)
            .user()
            .contains("uses abstract or non-creatable EClass 'WorkflowStep'"));
    assertEquals("conceptual_blueprint_patch", provider.prompts().get(2).requiredTool());
    assertEquals(
        List.of("ServerlessService", "Workflow", "TaskStep"),
        result.commandBatch().creates().stream().map(create -> create.eClass()).toList());
    assertEquals("workflow-1", result.commandBatch().creates().get(2).owner());
    assertEquals("steps", result.commandBatch().creates().get(2).reference());
    assertEquals(0, provider.remainingSteps());
  }

  @Test
  void asksDeepSeekForASemanticSubsetWhenTheSelectedPimClosureExceedsCapacity() throws Exception {
    var provider =
        new ScriptedAssistantModelProvider(
            List.of(
                ScriptedAssistantModelProvider.reply(
                    "{\"types\":[\"Api\",\"ApiRoute\",\"EventFlow\",\"DataStore\","
                        + "\"ExternalAdapter\",\"ObservabilityConfig\",\"SecurityPolicy\","
                        + "\"Workflow\",\"Function\",\"EventType\",\"Queue\",\"DataModel\"]}"),
                ScriptedAssistantModelProvider.reply(
                    "{\"types\":[\"Api\",\"DataStore\",\"ObservabilityConfig\","
                        + "\"SecurityPolicy\"]}"),
                ScriptedAssistantModelProvider.failure(new PlatformException(502, "stop"))));
    AiProperties properties = reviewEnabledProperties();
    when(properties.maxProviderCallsPerTurn()).thenReturn(18);
    when(properties.maxProviderCallsSourceTurn()).thenReturn(18);
    when(properties.model()).thenReturn("DeepSeek-V4-Flash");
    var knowledge = new MetamodelKnowledgeService(new AssistantMetamodelSchemaService());
    var workflow =
        new ConceptualInstanceModelWorkflow(
            provider, new MetamodelGuideGenerator(knowledge), contracts, properties);
    ModelService models = mock(ModelService.class);
    var workspace =
        new ModelWorkspace(
            ModelLevel.PIM, "model", 1, emptyPim(), new AssistantPatchCompiler(), null);

    assertThrows(
        AgentTurnLoop.TurnExecutionException.class,
        () ->
            workflow.run(
                "session",
                ModelLevel.PIM,
                "Create serverless order processing",
                workspace,
                new AgentModelTools(contracts, models).scoped(ModelLevel.PIM, workspace),
                false));

    String repairPrompt = provider.prompts().get(1).user();
    assertTrue(repairPrompt.contains("importance-ranked selection"));
    assertTrue(repairPrompt.contains("Preserve every required type"));
    assertTrue(repairPrompt.contains("marginal closure savings"));
    assertTrue(repairPrompt.contains("object excess"));
    assertTrue(repairPrompt.contains("Fewer than 4 types is valid"));
    assertEquals("conceptual_blueprint", provider.prompts().get(2).requiredTool());
  }

  @Test
  void combinesMalformedAssociationAndMissingRequiredAttributeInOneSliceCorrection()
      throws Exception {
    String blueprint =
        """
{"types":["ServerlessService","DataStore"],"objects":[
  {"instanceId":"service-1","type":"ServerlessService","purpose":"Order service","ownerInstanceId":"rootId","containment":"services","referenceTargets":[],"sourceUnitIds":[],"slice":1},
  {"instanceId":"ds1","type":"DataStore","purpose":"Order store","ownerInstanceId":"service-1","containment":"stores","referenceTargets":[],"sourceUnitIds":[],"slice":2}
]}
""";
    var provider =
        new ScriptedAssistantModelProvider(
            List.of(
                ScriptedAssistantModelProvider.reply(
                    "{\"types\":[\"ServerlessService\",\"DataStore\"]}"),
                ScriptedAssistantModelProvider.reply(blueprint),
                ScriptedAssistantModelProvider.reply(
                    """
{"service-1":{"type":"ServerlessService","attributes":[
  {"attributeName":"name","value":"Order service"},
  {"attributeName":"boundaryType","value":"CAPABILITY_BASED"}
],"associations":{"compositions":[{"associationName":"stores","associatedClassName":"DataStore","instanceID":"ds1"}],"references":[]}}}
"""),
                ScriptedAssistantModelProvider.reply(
                    """
{"ds1":{"type":"DataStore","attributes":[
  {"attributeName":"name","value":"Order store"},
  {"attributeName":"storeKind","value":"KEY_VALUE"}
],"associations":{"compositions":[],"references":[{"associationName":"retentionPolicy","associatedClassName":"RetentionPolicy","instanceID":[]}]}}}
"""),
                ScriptedAssistantModelProvider.reply(
                    """
{"ds1":{"type":"DataStore","attributes":[
  {"attributeName":"name","value":"Order store"},
  {"attributeName":"storeKind","value":"KEY_VALUE"},
  {"attributeName":"consistencyNeed","value":"EVENTUAL"}
],"associations":{"compositions":[],"references":[]}}}
"""),
                ScriptedAssistantModelProvider.reply(
                    "{\"acceptable\":true,\"findings\":[],\"corrections\":{}}")));
    AiProperties properties = reviewEnabledProperties();
    when(properties.maxProviderCallsPerTurn()).thenReturn(18);
    when(properties.maxProviderCallsSourceTurn()).thenReturn(18);
    when(properties.model()).thenReturn("DeepSeek-V4-Flash");
    var knowledge = new MetamodelKnowledgeService(new AssistantMetamodelSchemaService());
    var workflow =
        new ConceptualInstanceModelWorkflow(
            provider, new MetamodelGuideGenerator(knowledge), contracts, properties);
    ModelService models = mock(ModelService.class);
    when(models.validateStructural(
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(tools.jackson.databind.JsonNode.class)))
        .thenReturn(new ModelService.ValidationResult(true, List.of()));
    var workspace =
        new ModelWorkspace(
            ModelLevel.PIM, "model", 1, emptyPim(), new AssistantPatchCompiler(), null);

    var result =
        workflow.run(
            "session",
            ModelLevel.PIM,
            "Create serverless order processing",
            workspace,
            new AgentModelTools(contracts, models).scoped(ModelLevel.PIM, workspace),
            false);

    String correctionPrompt = provider.prompts().get(4).user();
    assertTrue(
        correctionPrompt.contains(
            "requires non-empty associationName, associatedClassName, and instanceID"));
    assertEquals(6, result.providerCalls());
    assertEquals(2, result.commandBatch().creates().size());
    assertEquals(0, provider.remainingSteps());
  }

  @Test
  void rejectsABlueprintThatSilentlyDropsASelectedSemanticType() throws Exception {
    String correctedBlueprint =
        """
{"removeObjectIds":[],"upsertObjects":[
  {"instanceId":"goal-1","type":"BusinessGoal","purpose":"Borrow books","ownerInstanceId":"rootId","containment":"goals","referenceTargets":[],"sourceUnitIds":[],"slice":2}
]}
""";
    var provider =
        new ScriptedAssistantModelProvider(
            List.of(
                ScriptedAssistantModelProvider.reply(
                    "{\"types\":[\"CIMModel\",\"Actor\",\"BusinessGoal\"]}"),
                ScriptedAssistantModelProvider.reply(
                    """
{"types":["Actor"],"objects":[
  {"instanceId":"actor-1","type":"Actor","purpose":"Borrower","ownerInstanceId":"rootId","containment":"actors","referenceTargets":[],"sourceUnitIds":[],"slice":1}
]}
"""),
                ScriptedAssistantModelProvider.failure(
                    new PlatformException(502, "finish_reason=length")),
                ScriptedAssistantModelProvider.reply(correctedBlueprint),
                ScriptedAssistantModelProvider.reply(actor("actor-1", "Borrower")),
                ScriptedAssistantModelProvider.reply(
                    """
{"goal-1":{"type":"BusinessGoal","attributes":[{"attributeName":"name","value":"Borrow books"}],"associations":{"compositions":[],"references":[]}}}
"""),
                ScriptedAssistantModelProvider.reply(
                    "{\"acceptable\":true,\"findings\":[],\"corrections\":{}}")));
    AiProperties properties = reviewEnabledProperties();
    when(properties.maxProviderCallsPerTurn()).thenReturn(10);
    when(properties.maxProviderCallsSourceTurn()).thenReturn(10);
    when(properties.model()).thenReturn("DeepSeek-V4-Flash");
    var knowledge = new MetamodelKnowledgeService(new AssistantMetamodelSchemaService());
    var workflow =
        new ConceptualInstanceModelWorkflow(
            provider, new MetamodelGuideGenerator(knowledge), contracts, properties);
    ModelService models = mock(ModelService.class);
    when(models.validateStructural(
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(tools.jackson.databind.JsonNode.class)))
        .thenReturn(new ModelService.ValidationResult(true, List.of()));
    var workspace =
        new ModelWorkspace(
            ModelLevel.CIM, "model", 1, emptyCim(), new AssistantPatchCompiler(), null);

    var result =
        workflow.run(
            "session",
            ModelLevel.CIM,
            "Create a library borrower and borrowing goal",
            workspace,
            new AgentModelTools(contracts, models).scoped(ModelLevel.CIM, workspace),
            false);

    assertTrue(
        provider
            .prompts()
            .get(2)
            .user()
            .contains("omitted selected semantic EClasses [BusinessGoal]"));
    assertTrue(provider.prompts().get(2).user().contains("REJECTED BLUEPRINT TO EDIT"));
    assertTrue(provider.prompts().get(2).user().contains("\"purpose\":\"Borrower\""));
    assertTrue(
        provider
            .prompts()
            .get(3)
            .user()
            .contains("omitted selected semantic EClasses [BusinessGoal]"));
    assertTrue(provider.prompts().get(3).user().contains("Prior blueprint repair attempt failed"));
    assertEquals("TRUNCATED", result.providerCallDetails().get(2).finishReason());
    assertEquals(2, result.commandBatch().creates().size());
    assertEquals(0, provider.remainingSteps());
  }

  @Test
  void stagesStableIdSlicesAndAppliesStructuredQualityCorrectionAtomically() throws Exception {
    var provider =
        new ScriptedAssistantModelProvider(
            List.of(
                ScriptedAssistantModelProvider.reply("{\"types\":[\"Actor\"]}"),
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
    AiProperties properties = reviewEnabledProperties();
    when(properties.maxProviderCallsPerTurn()).thenReturn(10);
    when(properties.maxProviderCallsSourceTurn()).thenReturn(10);
    var knowledge = new MetamodelKnowledgeService(new AssistantMetamodelSchemaService());
    var workflow =
        new ConceptualInstanceModelWorkflow(
            provider, new MetamodelGuideGenerator(knowledge), contracts, properties);
    ModelService models = mock(ModelService.class);
    when(models.validateStructural(
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(tools.jackson.databind.JsonNode.class)))
        .thenReturn(new ModelService.ValidationResult(true, List.of()));
    var workspace =
        new ModelWorkspace(
            ModelLevel.CIM, "model", 1, emptyCim(), new AssistantPatchCompiler(), null);
    var tools = new AgentModelTools(contracts, models).scoped(ModelLevel.CIM, workspace);

    var result =
        workflow.run("session", ModelLevel.CIM, "Create a library", workspace, tools, false);

    assertEquals(7, result.providerCalls());
    assertEquals("TRUNCATED", result.providerCallDetails().get(4).finishReason());
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
                ScriptedAssistantModelProvider.reply("{\"types\":[\"Actor\"]}"),
                ScriptedAssistantModelProvider.reply(blueprint),
                ScriptedAssistantModelProvider.failure(
                    new PlatformException(502, "finish_reason=length")),
                ScriptedAssistantModelProvider.reply(actor("actor-1", "Borrower")),
                ScriptedAssistantModelProvider.reply(actor("actor-2", "Librarian")),
                ScriptedAssistantModelProvider.reply(
                    "{\"acceptable\":true,\"findings\":[],\"corrections\":{}}")));
    AiProperties properties = reviewEnabledProperties();
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
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(tools.jackson.databind.JsonNode.class)))
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

    assertEquals(6, result.providerCalls());
    assertEquals(6, result.providerCallDetails().size());
    assertEquals("TRUNCATED", result.providerCallDetails().get(2).finishReason());
    assertEquals(2, result.commandBatch().creates().size());
  }

  @Test
  void malformedMultiObjectSliceIsRetriedAsSingleObjectSlices() throws Exception {
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
                ScriptedAssistantModelProvider.reply("{\"types\":[\"Actor\"]}"),
                ScriptedAssistantModelProvider.reply(blueprint),
                ScriptedAssistantModelProvider.reply(
                    "{\"actor-1\":[],\"actor-2\":{\"type\":\"Actor\",\"attributes\":[],\"associations\":{}}}"),
                ScriptedAssistantModelProvider.reply(actor("actor-1", "Borrower")),
                ScriptedAssistantModelProvider.reply(actor("actor-2", "Librarian"))));
    AiProperties properties = mock(AiProperties.class);
    when(properties.maxProviderCallsPerTurn()).thenReturn(8);
    when(properties.maxProviderCallsSourceTurn()).thenReturn(8);
    when(properties.maxRepairAttempts()).thenReturn(2);
    when(properties.model()).thenReturn("Gemma-4-31B-IT");
    when(properties.llmReviewEnabled()).thenReturn(false);
    var knowledge = new MetamodelKnowledgeService(new AssistantMetamodelSchemaService());
    var workflow =
        new ConceptualInstanceModelWorkflow(
            provider, new MetamodelGuideGenerator(knowledge), contracts, properties);
    ModelService models = mock(ModelService.class);
    when(models.validateStructural(
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(tools.jackson.databind.JsonNode.class)))
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
    assertEquals(2, result.commandBatch().creates().size());
    assertEquals(0, provider.remainingSteps());
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
                ScriptedAssistantModelProvider.reply("{\"types\":[\"Actor\"]}"),
                ScriptedAssistantModelProvider.reply(blueprint),
                ScriptedAssistantModelProvider.failure(
                    new PlatformException(502, "finish_reason=length")),
                ScriptedAssistantModelProvider.reply(actor("actor-1", "Borrower")),
                ScriptedAssistantModelProvider.reply(
                    "{\"acceptable\":true,\"findings\":[],\"corrections\":{}}")));
    AiProperties properties = reviewEnabledProperties();
    when(properties.maxProviderCallsPerTurn()).thenReturn(14);
    when(properties.maxProviderCallsSourceTurn()).thenReturn(14);
    when(properties.model()).thenReturn("DeepSeek-V4-Flash");
    var knowledge = new MetamodelKnowledgeService(new AssistantMetamodelSchemaService());
    var workflow =
        new ConceptualInstanceModelWorkflow(
            provider, new MetamodelGuideGenerator(knowledge), contracts, properties);
    ModelService models = mock(ModelService.class);
    when(models.validateStructural(
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(tools.jackson.databind.JsonNode.class)))
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
    assertEquals("TRUNCATED", result.providerCallDetails().get(2).finishReason());
    assertEquals(1, result.commandBatch().creates().size());
    assertEquals(0, provider.remainingSteps());
  }

  @Test
  void retriesATruncatedBlueprintWithinTheDurableCallBudget() throws Exception {
    String blueprint =
        """
{"types":["Actor"],"objects":[
  {"instanceId":"actor-1","type":"Actor","purpose":"Borrower","ownerInstanceId":"rootId","containment":"actors","referenceTargets":[],"sourceUnitIds":[],"slice":1}
]}
""";
    var provider =
        new ScriptedAssistantModelProvider(
            List.of(
                ScriptedAssistantModelProvider.reply("{\"types\":[\"Actor\"]}"),
                ScriptedAssistantModelProvider.failure(
                    new PlatformException(502, "finish_reason=length")),
                ScriptedAssistantModelProvider.reply(blueprint),
                ScriptedAssistantModelProvider.reply(actor("actor-1", "Borrower")),
                ScriptedAssistantModelProvider.reply(
                    "{\"acceptable\":true,\"findings\":[],\"corrections\":{}}")));
    AiProperties properties = reviewEnabledProperties();
    when(properties.maxProviderCallsPerTurn()).thenReturn(18);
    when(properties.maxProviderCallsSourceTurn()).thenReturn(18);
    when(properties.model()).thenReturn("DeepSeek-V4-Flash");
    var knowledge = new MetamodelKnowledgeService(new AssistantMetamodelSchemaService());
    var workflow =
        new ConceptualInstanceModelWorkflow(
            provider, new MetamodelGuideGenerator(knowledge), contracts, properties);
    ModelService models = mock(ModelService.class);
    when(models.validateStructural(
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(tools.jackson.databind.JsonNode.class)))
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
    assertEquals("TRUNCATED", result.providerCallDetails().get(1).finishReason());
    assertEquals(1, result.commandBatch().creates().size());
    assertEquals(0, provider.remainingSteps());
  }

  @Test
  void retriesARejectingReviewThatOmitsStableObjectIds() throws Exception {
    String blueprint =
        """
{"types":["Actor"],"objects":[
  {"instanceId":"actor-1","type":"Actor","purpose":"Borrower","ownerInstanceId":"rootId","containment":"actors","referenceTargets":[],"sourceUnitIds":[],"slice":1}
]}
""";
    var provider =
        new ScriptedAssistantModelProvider(
            List.of(
                ScriptedAssistantModelProvider.reply("{\"types\":[\"Actor\"]}"),
                ScriptedAssistantModelProvider.reply(blueprint),
                ScriptedAssistantModelProvider.reply(actor("actor-1", "Borrower")),
                ScriptedAssistantModelProvider.reply(
                    "{\"acceptable\":false,\"findings\":[{\"message\":\"Rename the actor\"}]}"),
                ScriptedAssistantModelProvider.reply(
                    "{\"acceptable\":true,\"findings\":[],\"corrections\":{}}")));
    AiProperties properties = reviewEnabledProperties();
    when(properties.maxProviderCallsPerTurn()).thenReturn(18);
    when(properties.maxProviderCallsSourceTurn()).thenReturn(18);
    when(properties.model()).thenReturn("DeepSeek-V4-Flash");
    var knowledge = new MetamodelKnowledgeService(new AssistantMetamodelSchemaService());
    var workflow =
        new ConceptualInstanceModelWorkflow(
            provider, new MetamodelGuideGenerator(knowledge), contracts, properties);
    ModelService models = mock(ModelService.class);
    when(models.validateStructural(
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(tools.jackson.databind.JsonNode.class)))
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
    assertEquals(1, result.commandBatch().creates().size());
    assertEquals(0, provider.remainingSteps());
  }

  @Test
  void completesRequiredReferencesFromAnUnambiguousLlmBlueprintTarget() throws Exception {
    String invalidBlueprint =
        """
{"types":["DomainEntity"],"objects":[
  {"instanceId":"book","type":"DomainEntity","purpose":"Book","ownerInstanceId":"rootId","containment":"entities","referenceTargets":[],"sourceUnitIds":[],"slice":1}
]}
""";
    String correctedBlueprint =
        """
{"removeObjectIds":[],"upsertObjects":[
  {"instanceId":"book","type":"DomainEntity","purpose":"Book","ownerInstanceId":"rootId","containment":"entities","referenceTargets":["book-id"],"sourceUnitIds":[],"slice":1},
  {"instanceId":"book-id","type":"InformationItem","purpose":"Book identifier","ownerInstanceId":"rootId","containment":"informationItems","referenceTargets":[],"sourceUnitIds":[],"slice":2}
]}
""";
    var provider =
        new ScriptedAssistantModelProvider(
            List.of(
                ScriptedAssistantModelProvider.reply(
                    "{\"types\":[\"DomainEntity\",\"InformationItem\"]}"),
                ScriptedAssistantModelProvider.reply(invalidBlueprint),
                ScriptedAssistantModelProvider.reply(correctedBlueprint),
                ScriptedAssistantModelProvider.reply(
                    """
{"book":{"type":"DomainEntity","attributes":[
  {"attributeName":"name","value":"Book"},
  {"attributeName":"identityStrategy","value":"NATURAL_KEY"}
],"associations":{"compositions":[],"references":[
  {"associationName":"identityAttributes","associatedClassName":"InformationItem","instanceID":"book-id"}
]}}}
"""),
                ScriptedAssistantModelProvider.reply(
                    """
{"book-id":{"type":"InformationItem","attributes":[
  {"attributeName":"name","value":"Book ID"},
  {"attributeName":"type","value":"IDENTIFIER"}
],"associations":{"compositions":[],"references":[]}}}
"""),
                ScriptedAssistantModelProvider.reply(
                    "{\"acceptable\":true,\"findings\":[],\"corrections\":{}}")));
    AiProperties properties = reviewEnabledProperties();
    when(properties.maxProviderCallsPerTurn()).thenReturn(18);
    when(properties.maxProviderCallsSourceTurn()).thenReturn(18);
    when(properties.model()).thenReturn("DeepSeek-V4-Flash");
    var knowledge = new MetamodelKnowledgeService(new AssistantMetamodelSchemaService());
    var workflow =
        new ConceptualInstanceModelWorkflow(
            provider, new MetamodelGuideGenerator(knowledge), contracts, properties);
    ModelService models = mock(ModelService.class);
    when(models.validateStructural(
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(tools.jackson.databind.JsonNode.class)))
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

    assertEquals(6, result.providerCalls());
    assertEquals(2, result.commandBatch().creates().size());
    assertEquals("DomainEntity", result.commandBatch().creates().get(0).eClass());
    String slicePrompt =
        provider.prompts().stream()
            .filter(prompt -> "conceptual_instance_slice".equals(prompt.requiredTool()))
            .findFirst()
            .orElseThrow()
            .user();
    assertTrue(slicePrompt.contains("DomainEntity"));
    assertTrue(slicePrompt.contains("InformationItem"));
    assertTrue(
        result.commandBatch().connections().stream()
            .anyMatch(connection -> "primaryIdentityAttribute".equals(connection.reference())));
    assertEquals(0, provider.remainingSteps());
  }

  @Test
  void rejectsAnAssociationWhoseLiveTargetViolatesTheEcoreType() throws Exception {
    String blueprint =
        """
{"types":["Actor","BusinessGoal"],"objects":[
  {"instanceId":"customer","type":"Actor","purpose":"Customer","ownerInstanceId":"rootId","containment":"actors","referenceTargets":[],"sourceUnitIds":[],"slice":1},
  {"instanceId":"place-order","type":"BusinessGoal","purpose":"Place orders","ownerInstanceId":"rootId","containment":"goals","referenceTargets":["customer"],"sourceUnitIds":[],"slice":1}
]}
""";
    String actor =
        """
"customer":{"type":"Actor","attributes":[{"attributeName":"name","value":"Customer"}],"associations":{"compositions":[],"references":[]}}
""";
    String invalidGoal =
        """
"place-order":{"type":"BusinessGoal","attributes":[{"attributeName":"name","value":"Place orders"}],"associations":{"compositions":[],"references":[{"associationName":"owners","associatedClassName":"Stakeholder","instanceID":"customer"}]}}
""";
    String correctedGoal =
        """
"place-order":{"type":"BusinessGoal","attributes":[{"attributeName":"name","value":"Place orders"}],"associations":{"compositions":[],"references":[]}}
""";
    var provider =
        new ScriptedAssistantModelProvider(
            List.of(
                ScriptedAssistantModelProvider.reply("{\"types\":[\"Actor\",\"BusinessGoal\"]}"),
                ScriptedAssistantModelProvider.reply(blueprint),
                ScriptedAssistantModelProvider.reply("{" + actor + "," + invalidGoal + "}"),
                ScriptedAssistantModelProvider.reply("{" + actor + "," + correctedGoal + "}")));
    AiProperties properties = mock(AiProperties.class);
    when(properties.maxProviderCallsPerTurn()).thenReturn(10);
    when(properties.maxProviderCallsSourceTurn()).thenReturn(10);
    when(properties.maxRepairAttempts()).thenReturn(1);
    when(properties.model()).thenReturn("Gemma-4-31B-IT");
    when(properties.llmReviewEnabled()).thenReturn(false);
    var knowledge = new MetamodelKnowledgeService(new AssistantMetamodelSchemaService());
    var workflow =
        new ConceptualInstanceModelWorkflow(
            provider, new MetamodelGuideGenerator(knowledge), contracts, properties);
    ModelService models = mock(ModelService.class);
    when(models.validateStructural(
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(tools.jackson.databind.JsonNode.class)))
        .thenReturn(new ModelService.ValidationResult(true, List.of()));
    var workspace =
        new ModelWorkspace(
            ModelLevel.CIM, "model", 1, emptyCim(), new AssistantPatchCompiler(), null);

    var result =
        workflow.run(
            "session",
            ModelLevel.CIM,
            "Create order goals and actors",
            workspace,
            new AgentModelTools(contracts, models).scoped(ModelLevel.CIM, workspace),
            false);

    assertTrue(
        provider
            .prompts()
            .get(3)
            .user()
            .contains("requires Stakeholder but instance 'customer' is Actor"));
    assertTrue(provider.prompts().get(3).user().contains("REJECTED SLICE JSON"));
    assertTrue(provider.prompts().get(3).user().contains("\"customer\":\"Actor\""));
    assertTrue(!provider.prompts().get(3).user().contains("REQUEST AND SOURCE SPECIFICATION"));
    assertEquals(2, result.commandBatch().creates().size());
    assertEquals(0, result.commandBatch().connections().size());
    assertEquals(0, provider.remainingSteps());
  }

  @Test
  void compilerRepairTargetsTheSourceInstanceWhenAReviewIntroducesAnInvalidReference()
      throws Exception {
    String blueprint =
        """
{"types":["Actor","BusinessGoal"],"objects":[
  {"instanceId":"customer","type":"Actor","purpose":"Customer","ownerInstanceId":"rootId","containment":"actors","referenceTargets":[],"sourceUnitIds":[],"slice":1},
  {"instanceId":"place-order","type":"BusinessGoal","purpose":"Place orders","ownerInstanceId":"rootId","containment":"goals","referenceTargets":["customer"],"sourceUnitIds":[],"slice":1}
]}
""";
    String generated =
        """
{"customer":{"type":"Actor","attributes":[{"attributeName":"name","value":"Customer"}],"associations":{"compositions":[],"references":[]}},
"place-order":{"type":"BusinessGoal","attributes":[{"attributeName":"name","value":"Place orders"}],"associations":{"compositions":[],"references":[]}}}
""";
    String invalidReview =
        """
{"acceptable":false,"findings":[{"objectIds":["place-order"],"problem":"Add an owner","recommendedCorrection":"Connect the customer"}],"corrections":{"place-order":{"type":"BusinessGoal","attributes":[{"attributeName":"name","value":"Place orders"}],"associations":{"compositions":[],"references":[{"associationName":"owners","associatedClassName":"Actor","instanceID":"customer"}]}}}}
""";
    String invalidCorrection =
        """
{"acceptable":false,"findings":[{"objectIds":["place-order"],"problem":"Add the requested owner","recommendedCorrection":"Connect the customer"}],"corrections":{"place-order":{"type":"BusinessGoal","attributes":[{"attributeName":"name","value":"Place orders"}],"associations":{"compositions":[],"references":[{"associationName":"owners","associatedClassName":"Actor","instanceID":"customer"}]}}}}
""";
    String repaired =
        """
{"acceptable":false,"findings":[{"objectIds":["place-order"],"problem":"Actor is not a Stakeholder","recommendedCorrection":"Remove the invalid optional owner"}],"corrections":{"place-order":{"type":"BusinessGoal","attributes":[{"attributeName":"name","value":"Place orders"}],"associations":{"compositions":[],"references":[]}}}}
""";
    var provider =
        new ScriptedAssistantModelProvider(
            List.of(
                ScriptedAssistantModelProvider.reply("{\"types\":[\"Actor\",\"BusinessGoal\"]}"),
                ScriptedAssistantModelProvider.reply(blueprint),
                ScriptedAssistantModelProvider.reply(generated),
                ScriptedAssistantModelProvider.reply(invalidReview),
                ScriptedAssistantModelProvider.reply(invalidCorrection),
                ScriptedAssistantModelProvider.reply(repaired)));
    AiProperties properties = reviewEnabledProperties();
    when(properties.maxProviderCallsPerTurn()).thenReturn(10);
    when(properties.maxProviderCallsSourceTurn()).thenReturn(10);
    when(properties.model()).thenReturn("Gemma-4-31B-IT");
    var knowledge = new MetamodelKnowledgeService(new AssistantMetamodelSchemaService());
    var workflow =
        new ConceptualInstanceModelWorkflow(
            provider, new MetamodelGuideGenerator(knowledge), contracts, properties);
    ModelService models = mock(ModelService.class);
    when(models.validateStructural(
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(tools.jackson.databind.JsonNode.class)))
        .thenReturn(new ModelService.ValidationResult(true, List.of()));
    var workspace =
        new ModelWorkspace(
            ModelLevel.CIM, "model", 1, emptyCim(), new AssistantPatchCompiler(), null);

    var result =
        workflow.run(
            "session",
            ModelLevel.CIM,
            "Create an order goal and customer",
            workspace,
            new AgentModelTools(contracts, models).scoped(ModelLevel.CIM, workspace),
            false);

    String qualityCorrectionPrompt = provider.prompts().get(4).user();
    assertTrue(
        qualityCorrectionPrompt.contains("ONLY AFFECTED REJECTED OBJECTS:\n{\"place-order\""));
    assertTrue(!qualityCorrectionPrompt.contains("ONLY AFFECTED REJECTED OBJECTS:\n{\"customer\""));
    String correctionPrompt = provider.prompts().get(5).user();
    assertTrue(
        correctionPrompt.contains("Association place-order.owners on BusinessGoal"),
        correctionPrompt);
    assertTrue(correctionPrompt.contains("ONLY AFFECTED REJECTED OBJECTS:\n{\"place-order\""));
    assertTrue(!correctionPrompt.contains("ONLY AFFECTED REJECTED OBJECTS:\n{\"customer\""));
    assertEquals(0, result.commandBatch().connections().size());
    assertEquals(0, provider.remainingSteps());
  }

  @Test
  void requiresAPersistedOwnerBlueprintRecordForANewNestedObject() throws Exception {
    var provider =
        new ScriptedAssistantModelProvider(
            List.of(
                ScriptedAssistantModelProvider.reply("{\"types\":[\"TaskStep\"]}"),
                ScriptedAssistantModelProvider.reply(
                    """
{"types":["TaskStep"],"objects":[{"instanceId":"task-new","type":"TaskStep","purpose":"Cancel an order","ownerInstanceId":"workflow-existing","containment":"steps","referenceTargets":["service-existing"],"sourceUnitIds":[],"slice":1}]}
"""),
                ScriptedAssistantModelProvider.reply(
                    """
{"removeObjectIds":[],"upsertObjects":[{"instanceId":"workflow-existing","type":"Workflow","purpose":"Existing order workflow that will own the cancellation task","ownerInstanceId":"service-existing","containment":"workflows","referenceTargets":["task-new"],"sourceUnitIds":[],"slice":1}]}
"""),
                ScriptedAssistantModelProvider.failure(new PlatformException(502, "stop"))));
    AiProperties properties = mock(AiProperties.class);
    when(properties.maxProviderCallsPerTurn()).thenReturn(10);
    when(properties.maxProviderCallsSourceTurn()).thenReturn(10);
    when(properties.maxRepairAttempts()).thenReturn(1);
    when(properties.model()).thenReturn("Gemma-4-31B-IT");
    when(properties.llmReviewEnabled()).thenReturn(false);
    var workflow =
        new ConceptualInstanceModelWorkflow(
            provider,
            new MetamodelGuideGenerator(
                new MetamodelKnowledgeService(new AssistantMetamodelSchemaService())),
            contracts,
            properties);
    ModelService models = mock(ModelService.class);
    when(models.validateStructural(
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(tools.jackson.databind.JsonNode.class)))
        .thenReturn(new ModelService.ValidationResult(true, List.of()));
    var current =
        mapper.readTree(
            """
{"id":"root","eClass":"PIMModel","modelLevel":"PIM","services":[{"id":"service-existing","eClass":"ServerlessService","name":"Orders"}],"workflows":[{"id":"workflow-existing","eClass":"Workflow","name":"Orders workflow"}],"diagram":{"elements":[],"relationships":[]}}
""");
    var workspace =
        new ModelWorkspace(ModelLevel.PIM, "model", 1, current, new AssistantPatchCompiler(), null);

    assertThrows(
        AgentTurnLoop.TurnExecutionException.class,
        () ->
            workflow.run(
                "session",
                ModelLevel.PIM,
                "Add a cancellation task to the existing workflow",
                workspace,
                new AgentModelTools(contracts, models).scoped(ModelLevel.PIM, workspace),
                false));

    String correctionPrompt = provider.prompts().get(2).user();
    assertEquals("conceptual_blueprint_patch", provider.prompts().get(2).requiredTool());
    assertTrue(
        correctionPrompt.contains(
            "add that owner's exact persisted-ID blueprint record and include task-new in its"
                + " referenceTargets"));
    assertEquals(0, provider.remainingSteps());
  }

  @Test
  void revisesUnaffordableAndTruncatedTypeSelectionsBeforeBlueprinting() throws Exception {
    String blueprint =
        """
{"types":["Actor"],"objects":[
  {"instanceId":"actor-1","type":"Actor","purpose":"Borrower","ownerInstanceId":"rootId","containment":"actors","referenceTargets":[],"sourceUnitIds":[],"slice":1}
]}
""";
    var provider =
        new ScriptedAssistantModelProvider(
            List.of(
                ScriptedAssistantModelProvider.reply(
                    "{\"types\":[\"Actor\",\"BusinessGoal\",\"BusinessCapability\",\"DomainEntity\","
                        + "\"DomainRelationship\",\"Policy\",\"UbiquitousLanguageTerm\","
                        + "\"Requirement\",\"BusinessProcess\",\"Command\",\"BusinessEvent\","
                        + "\"Risk\",\"Hotspot\"]}"),
                ScriptedAssistantModelProvider.failure(
                    new PlatformException(502, "finish_reason=length")),
                ScriptedAssistantModelProvider.reply(
                    "{\"types\":[\"Actor\",\"BusinessGoal\",\"BusinessCapability\",\"DomainEntity\","
                        + "\"DomainRelationship\",\"Policy\",\"UbiquitousLanguageTerm\","
                        + "\"Requirement\",\"BusinessProcess\",\"Command\",\"BusinessEvent\","
                        + "\"Risk\",\"Hotspot\"]}"),
                ScriptedAssistantModelProvider.reply("{\"types\":[\"Actor\"]}"),
                ScriptedAssistantModelProvider.reply(blueprint),
                ScriptedAssistantModelProvider.reply(actor("actor-1", "Borrower")),
                ScriptedAssistantModelProvider.reply(
                    "{\"acceptable\":true,\"findings\":[],\"corrections\":{}}")));
    AiProperties properties = reviewEnabledProperties();
    when(properties.maxProviderCallsPerTurn()).thenReturn(18);
    when(properties.maxProviderCallsSourceTurn()).thenReturn(18);
    when(properties.model()).thenReturn("DeepSeek-V4-Flash");
    var knowledge = new MetamodelKnowledgeService(new AssistantMetamodelSchemaService());
    var workflow =
        new ConceptualInstanceModelWorkflow(
            provider, new MetamodelGuideGenerator(knowledge), contracts, properties);
    ModelService models = mock(ModelService.class);
    when(models.validateStructural(
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(tools.jackson.databind.JsonNode.class)))
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

    assertEquals(7, result.providerCalls());
    assertEquals(1, result.commandBatch().creates().size());
    assertTrue(provider.prompts().get(2).user().contains("The prior selection was rejected"));
    assertTrue(
        provider.prompts().get(2).user().contains("prior type-selection response was truncated"));
    assertTrue(provider.prompts().get(3).user().contains("capacity"));
    assertEquals(0, provider.remainingSteps());
  }

  @Test
  void resumesFromPersistedBlueprintAndGeneratedObjectsAfterWorkerRestart() throws Exception {
    String blueprint =
        """
{"types":["Actor"],"objects":[
  {"instanceId":"actor-1","type":"Actor","purpose":"Borrower","ownerInstanceId":"rootId","containment":"actors","referenceTargets":[],"obligationIds":["OBL-1"],"sourceUnitIds":[],"slice":1},
  {"instanceId":"actor-2","type":"Actor","purpose":"Librarian","ownerInstanceId":"rootId","containment":"actors","referenceTargets":[],"obligationIds":["OBL-1"],"sourceUnitIds":[],"slice":1}
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
                ScriptedAssistantModelProvider.reply(
                    "{\"obligations\":[{\"id\":\"OBL-1\",\"obligation\":\"Represent library"
                        + " participants\",\"importance\":\"MANDATORY\",\"minimumEvidenceObjects\":2,\"sourceUnitIds\":[],\"expectedEClasses\":[\"Actor\"]}]}"),
                ScriptedAssistantModelProvider.reply("{\"types\":[\"Actor\"]}"),
                ScriptedAssistantModelProvider.reply(blueprint),
                ScriptedAssistantModelProvider.reply(actor("actor-1", "Borrower")),
                ScriptedAssistantModelProvider.failure(new PlatformException(503, "restart"))));
    AiProperties properties = properties();
    var knowledge = new MetamodelKnowledgeService(new AssistantMetamodelSchemaService());
    ModelService models = mock(ModelService.class);
    when(models.validateStructural(
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(tools.jackson.databind.JsonNode.class)))
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
      assertEquals("Actor", savedWorkflow.get().plan().path("selectedTypes").get(0).asText());
      assertEquals(
          "OBL-1",
          savedWorkflow
              .get()
              .plan()
              .path("obligationLedger")
              .path("obligations")
              .get(0)
              .path("id")
              .asText());
      assertEquals(
          2,
          savedWorkflow
              .get()
              .plan()
              .path("obligationLedger")
              .path("obligations")
              .get(0)
              .path("minimumEvidenceObjects")
              .asInt());
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
                      "{\"acceptable\":true,\"coverage\":[{\"obligationId\":\"OBL-1\",\"state\":\"SATISFIED\",\"evidenceObjectIds\":[\"actor-1\",\"actor-2\"],\"evidenceRelationships\":[],\"explanation\":\"Both"
                          + " participants are modeled\"}],\"findings\":[]}")));
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

      assertEquals(2, result.providerCalls());
      assertEquals(2, result.providerCallDetails().size());
      assertEquals(7, savedCalls.size());
      assertEquals(2, result.commandBatch().creates().size());
      assertTrue(
          resumedProvider
              .prompts()
              .get(1)
              .system()
              .contains("minimum jointly required mappings selected during interpretation"));
      assertEquals(0, resumedProvider.remainingSteps());
    } finally {
      // DurableTurnExecutionContext restores its thread-local value after each invocation.
    }
  }

  @Test
  void usesOnlyLedgerCandidatesAfterATypeSelectionTruncation() throws Exception {
    String blueprint =
        "{\"types\":[\"Actor\"],\"objects\":[{\"instanceId\":\"actor-1\",\"type\":\"Actor\","
            + "\"purpose\":\"Borrower\",\"ownerInstanceId\":\"rootId\",\"containment\":\"actors\","
            + "\"referenceTargets\":[],\"obligationIds\":[\"OBL-1\"],\"sourceUnitIds\":[],\"slice\":1}]}";
    var provider =
        new ScriptedAssistantModelProvider(
            List.of(
                ScriptedAssistantModelProvider.reply(
                    "{\"obligations\":[{\"id\":\"OBL-1\",\"obligation\":\"Represent"
                        + " participants\",\"importance\":\"MANDATORY\",\"sourceUnitIds\":[],\"expectedEClasses\":[\"Actor\"]}]}"),
                ScriptedAssistantModelProvider.failure(
                    new PlatformException(502, "finish_reason=length")),
                ScriptedAssistantModelProvider.reply("{\"types\":[\"Actor\"]}"),
                ScriptedAssistantModelProvider.reply(blueprint),
                ScriptedAssistantModelProvider.reply(actor("actor-1", "Borrower")),
                ScriptedAssistantModelProvider.reply(
                    "{\"acceptable\":true,\"coverage\":[{\"obligationId\":\"OBL-1\",\"state\":\"SATISFIED\",\"evidenceObjectIds\":[\"actor-1\"],\"evidenceRelationships\":[],\"explanation\":\"Participant"
                        + " modeled\"}],\"findings\":[]}")));
    AssistantTurnStore store = mock(AssistantTurnStore.class);
    when(store.workflow("turn-compact-selection")).thenReturn(Optional.empty());
    when(store.workItems("turn-compact-selection")).thenReturn(List.of());
    AiProperties properties = properties();
    var knowledge = new MetamodelKnowledgeService(new AssistantMetamodelSchemaService());
    ModelService models = mock(ModelService.class);
    when(models.validateStructural(
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(tools.jackson.databind.JsonNode.class)))
        .thenReturn(new ModelService.ValidationResult(true, List.of()));
    var workspace =
        new ModelWorkspace(
            ModelLevel.CIM, "model", 1, emptyCim(), new AssistantPatchCompiler(), null);
    var workflow =
        new ConceptualInstanceModelWorkflow(
            provider, new MetamodelGuideGenerator(knowledge), contracts, properties, store);

    DurableTurnExecutionContext.with(
        "turn-compact-selection",
        () ->
            workflow.run(
                "session",
                ModelLevel.CIM,
                "Create participants",
                workspace,
                new AgentModelTools(contracts, models).scoped(ModelLevel.CIM, workspace),
                false));

    String retry = provider.prompts().get(2).user();
    assertTrue(retry.contains("Actor=1"));
    assertTrue(!retry.contains("Requirement=1"));
    assertTrue(retry.contains("prior type-selection response was truncated"));
    assertTrue(!retry.contains("UbiquitousLanguageTerm"));
  }

  private AiProperties properties() {
    AiProperties properties = reviewEnabledProperties();
    when(properties.maxProviderCallsPerTurn()).thenReturn(10);
    when(properties.maxProviderCallsSourceTurn()).thenReturn(10);
    when(properties.model()).thenReturn("DeepSeek-V4-Flash");
    return properties;
  }

  @Test
  void repairsCompressedEvidenceBeforeCheckpointingRequiredCardinality() throws Exception {
    String blueprint =
        """
{"types":["Actor"],"objects":[
  {"instanceId":"actor-1","type":"Actor","purpose":"Borrower","ownerInstanceId":"rootId","containment":"actors","referenceTargets":[],"obligationIds":["OBL-1"],"sourceUnitIds":[],"slice":1}
  ,{"instanceId":"actor-2","type":"Actor","purpose":"Librarian","ownerInstanceId":"rootId","containment":"actors","referenceTargets":[],"obligationIds":["OBL-1"],"sourceUnitIds":[],"slice":1}
]}
""";
    var provider =
        new ScriptedAssistantModelProvider(
            List.of(
                ScriptedAssistantModelProvider.reply(
                    "{\"obligations\":[{\"id\":\"OBL-1\",\"obligation\":\"Represent borrowing"
                        + " participants\",\"importance\":\"MANDATORY\",\"minimumEvidenceObjects\":2,\"sourceUnitIds\":[],\"expectedEClasses\":[\"Actor\"]}]}"),
                ScriptedAssistantModelProvider.reply("{\"types\":[\"Actor\"]}"),
                ScriptedAssistantModelProvider.reply(blueprint),
                ScriptedAssistantModelProvider.reply(actor("actor-1", "Borrower")),
                ScriptedAssistantModelProvider.reply(actor("actor-2", "Librarian")),
                ScriptedAssistantModelProvider.reply(
                    "{\"acceptable\":true,\"coverage\":[{\"obligationId\":\"OBL-1\",\"state\":\"SATISFIED\",\"evidenceObjectIds\":[\"actor-1\"],\"evidenceRelationships\":[],\"explanation\":\"Participants"
                        + " modeled\"}],\"findings\":[]}"),
                ScriptedAssistantModelProvider.reply(
                    "{\"acceptable\":false,\"findings\":[{\"problem\":\"Cardinality evidence was"
                        + " compressed\"}],\"corrections\":{\"actor-1\":{\"type\":\"Actor\",\"attributes\":[{\"attributeName\":\"name\",\"value\":\"Borrower"
                        + " participant\"}],\"associations\":{\"compositions\":[],\"references\":[]}},\"actor-2\":{\"type\":\"Actor\",\"attributes\":[{\"attributeName\":\"name\",\"value\":\"Librarian"
                        + " participant\"}],\"associations\":{\"compositions\":[],\"references\":[]}}}}"),
                ScriptedAssistantModelProvider.reply(
                    "{\"acceptable\":true,\"coverage\":[{\"obligationId\":\"OBL-1\",\"state\":\"SATISFIED\",\"evidenceObjectIds\":[\"actor-1\",\"actor-2\"],\"evidenceRelationships\":[],\"explanation\":\"Both"
                        + " participants modeled\"}],\"findings\":[]}")));
    AssistantTurnStore store = mock(AssistantTurnStore.class);
    AtomicReference<AssistantTurnStore.Workflow> savedWorkflow = new AtomicReference<>();
    when(store.workflow("turn-obligation-failure"))
        .thenAnswer(ignored -> Optional.ofNullable(savedWorkflow.get()));
    when(store.workItems("turn-obligation-failure")).thenReturn(List.of());
    doAnswer(
            invocation -> {
              savedWorkflow.set(invocation.getArgument(0));
              return null;
            })
        .when(store)
        .saveWorkflow(org.mockito.ArgumentMatchers.any());
    AiProperties properties = reviewEnabledProperties();
    when(properties.maxProviderCallsPerTurn()).thenReturn(12);
    when(properties.maxProviderCallsSourceTurn()).thenReturn(12);
    when(properties.model()).thenReturn("DeepSeek-V4-Flash");
    var knowledge = new MetamodelKnowledgeService(new AssistantMetamodelSchemaService());
    ModelService models = mock(ModelService.class);
    when(models.validateStructural(
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(tools.jackson.databind.JsonNode.class)))
        .thenReturn(new ModelService.ValidationResult(true, List.of()));
    var workspace =
        new ModelWorkspace(
            ModelLevel.CIM, "model", 1, emptyCim(), new AssistantPatchCompiler(), null);
    var workflow =
        new ConceptualInstanceModelWorkflow(
            provider, new MetamodelGuideGenerator(knowledge), contracts, properties, store);

    assertDoesNotThrow(
        () ->
            DurableTurnExecutionContext.with(
                "turn-obligation-failure",
                () ->
                    workflow.run(
                        "session",
                        ModelLevel.CIM,
                        "Create borrowing behavior",
                        workspace,
                        new AgentModelTools(contracts, models).scoped(ModelLevel.CIM, workspace),
                        false)));

    String reviewPrompt = provider.prompts().get(5).user();
    assertTrue(reviewPrompt.contains("OBLIGATION LEDGER (authoritative requirements"));
    assertTrue(!reviewPrompt.contains("REQUEST AND SOURCE SPECIFICATION"));
    assertTrue(!reviewPrompt.contains("\"purpose\":\"Borrower\""));
    assertTrue(provider.prompts().get(6).user().contains("REJECTED REVIEW VERDICT"));
    assertTrue(provider.prompts().get(7).user().contains("OBLIGATION LEDGER"));
    assertTrue(workspace.snapshot().toString().contains("Borrower participant"));
    assertEquals(0, provider.remainingSteps());
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
  void restoresAnOmittedObjectTypeFromItsAuthoritativeBlueprintEntry() throws Exception {
    var conceptual =
        ConceptualInstanceModelWorkflow.ConceptualModel.parse(
            mapper,
            "{\"actor-1\":{\"attributes\":[],\"associations\":{\"compositions\":[],\"references\":[]}}}",
            Map.of("actor-1", "Actor"));

    var batch = conceptual.commands(emptyCim(), contracts, ModelLevel.CIM);

    assertEquals("Actor", batch.creates().get(0).eClass());
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
  void derivesAttributeDatatypeFromEcoreInsteadOfProviderMetadata() throws Exception {
    var conceptual =
        ConceptualInstanceModelWorkflow.ConceptualModel.parse(
            mapper,
            """
            {
              "capability": {
                "type": "BusinessCapability",
                "attributes": [
                  {"dataType": "EString", "attributeName": "lifecycleStatus", "value": "DRAFT"}
                ],
                "associations": {"compositions": [], "references": []}
              }
            }
            """);

    var batch = conceptual.commands(emptyCim(), contracts, ModelLevel.CIM);

    assertEquals("DRAFT", batch.creates().get(0).attributes().get("lifecycleStatus").asText());
  }

  @Test
  void normalizesScalarValuesForEcoreMultiValuedAttributes() throws Exception {
    var conceptual =
        ConceptualInstanceModelWorkflow.ConceptualModel.parse(
            mapper,
            """
            {
              "actor": {
                "type": "Actor",
                "attributes": [
                  {"attributeName": "name", "value": "Borrower"},
                  {"attributeName": "modelTags", "value": "library"}
                ],
                "associations": {"compositions": [], "references": []}
              }
            }
            """);

    var batch = conceptual.commands(emptyCim(), contracts, ModelLevel.CIM);

    assertTrue(batch.creates().get(0).attributes().get("modelTags").isArray());
    assertEquals("library", batch.creates().get(0).attributes().get("modelTags").get(0).asText());
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
  },
  "function": {
    "type": "Function",
    "attributes": [
      {"dataType": "", "attributeName": "name", "value": "Process order"},
      {"dataType": "", "attributeName": "functionKind", "value": "COMMAND_HANDLER"}
    ],
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
  void dropsKnownReadonlyInverseAssociationWhileCompilingConceptualObjects() throws Exception {
    var conceptual =
        ConceptualInstanceModelWorkflow.ConceptualModel.parse(
            mapper,
            """
{
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
  },
  "function": {
    "type": "Function",
    "attributes": [
      {"dataType": "", "attributeName": "name", "value": "Process order"},
      {"dataType": "", "attributeName": "functionKind", "value": "COMMAND_HANDLER"}
    ],
    "associations": {
      "compositions": [],
      "references": [
        {"associationName": "service", "associatedClassName": "ServerlessService", "instanceID": "service"}
      ]
    }
  }
}
""");

    var batch = conceptual.commands(emptyPim(), contracts, ModelLevel.PIM);

    assertEquals(2, batch.creates().size());
    assertTrue(batch.connections().isEmpty());
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

  @Test
  void treatsAnExistingContainmentRepeatedByItsCurrentParentAsIdempotent() throws Exception {
    var conceptual =
        ConceptualInstanceModelWorkflow.ConceptualModel.parse(
            mapper,
            """
{"api-existing":{"type":"Api","attributes":[{"attributeName":"name","value":"Orders API"},{"attributeName":"apiStyle","value":"RESOURCE_ORIENTED_HTTP"}],"associations":{"compositions":[{"associationName":"routes","associatedClassName":"ApiRoute","instanceID":"route-existing"}],"references":[]}}}
""");
    var current =
        mapper.readTree(
            """
{"id":"root","eClass":"PIMModel","modelLevel":"PIM","services":[{"id":"service-existing","eClass":"ServerlessService","apis":[{"id":"api-existing","eClass":"Api","name":"Orders API","apiStyle":"RESOURCE_ORIENTED_HTTP","routes":[{"id":"route-existing","eClass":"ApiRoute","name":"Submit order","pathTemplate":"/orders"}]}]}],"diagram":{"elements":[],"relationships":[]}}
""");

    var batch = conceptual.commands(current, contracts, ModelLevel.PIM);

    assertEquals(1, batch.updates().size());
    assertTrue(batch.connections().isEmpty());
  }

  private tools.jackson.databind.JsonNode emptyCim() throws Exception {
    return mapper.readTree(
        """
{"id":"root","eClass":"CIMModel","modelLevel":"CIM","diagram":{"elements":[],"relationships":[]}}
""");
  }

  private static AiProperties reviewEnabledProperties() {
    AiProperties properties = mock(AiProperties.class);
    when(properties.llmReviewEnabled()).thenReturn(true);
    return properties;
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
