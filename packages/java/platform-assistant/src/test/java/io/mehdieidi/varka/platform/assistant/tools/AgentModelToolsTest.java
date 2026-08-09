package io.mehdieidi.varka.platform.assistant.tools;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import io.mehdieidi.varka.platform.assistant.domain.ModelCommandBatch;
import io.mehdieidi.varka.platform.assistant.metamodel.MetamodelKnowledgeService;
import io.mehdieidi.varka.platform.assistant.metamodel.TypeContractService;
import io.mehdieidi.varka.platform.assistant.patch.AssistantMetamodelSchemaService;
import io.mehdieidi.varka.platform.assistant.patch.AssistantPatchCompiler;
import io.mehdieidi.varka.platform.assistant.patch.ModelCommandCompiler;
import io.mehdieidi.varka.platform.assistant.workspace.ModelWorkspace;
import io.mehdieidi.varka.platform.kernel.ModelLevel;
import io.mehdieidi.varka.platform.kernel.PlatformException;
import io.mehdieidi.varka.platform.model.application.ModelService;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class AgentModelToolsTest {

  private final ObjectMapper mapper = new ObjectMapper();

  @Test
  void rejectsUnknownTypeWithActionableSuggestion() throws Exception {
    var knowledge = new MetamodelKnowledgeService(new AssistantMetamodelSchemaService());
    AgentModelTools tools =
        new AgentModelTools(new TypeContractService(knowledge), mock(ModelService.class));
    var model =
        mapper.readTree(
            """
{"id":"root","eClass":"CIMModel","modelLevel":"CIM","diagram":{"elements":[],"relationships":[]}}
""");
    tools.bind(
        ModelLevel.CIM,
        new ModelWorkspace(ModelLevel.CIM, "m", 1, model, new AssistantPatchCompiler(), null));

    PlatformException error =
        assertThrows(
            PlatformException.class,
            () ->
                tools.createElements(
                    List.of(
                        new AgentModelTools.CreateElement(
                            null, "BusinesProces", "Checkout", null, null, null))));

    assertTrue(error.getMessage().contains("Did you mean"));
  }

  @Test
  void rejectsCreateWithoutExplicitContainmentInsteadOfInventingDefaults() throws Exception {
    AgentModelTools tools = cimTools();
    tools.bind(ModelLevel.CIM, workspace());

    assertThrows(
        PlatformException.class,
        () ->
            tools.commitModelBatch(
                new ModelCommandBatch(
                    List.of(
                        new ModelCommandBatch.Create(
                            "patient",
                            "DomainEntity",
                            Map.of("name", text("Patient")),
                            null,
                            null,
                            null)),
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of(),
                    "draft entity",
                    true)));
  }

  @Test
  void rejectsAnEmptyBatchBecauseItCannotProduceASavedCheckpoint() throws Exception {
    AgentModelTools tools = cimTools();
    tools.bind(ModelLevel.CIM, workspace());

    PlatformException error =
        assertThrows(
            PlatformException.class,
            () ->
                tools.commitModelBatch(
                    new ModelCommandBatch(
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        "no changes",
                        true)));

    assertEquals(422, error.status());
    assertTrue(error.getMessage().contains("at least one create"));
  }

  @Test
  void keepsLlmAuthoredDuplicateNamedCreateInsteadOfSemanticallyMergingIt() throws Exception {
    AgentModelTools tools = cimTools();
    JsonNode model =
        mapper.readTree(
            """
{"id":"root","eClass":"CIMModel","modelLevel":"CIM","actors":[
  {"id":"actor-existing","eClass":"Actor","name":"Cyclist","actorType":"HUMAN"}],
 "diagram":{"elements":[],"relationships":[]}}
""");
    tools.bind(
        ModelLevel.CIM,
        new ModelWorkspace(ModelLevel.CIM, "m", 1, model, new AssistantPatchCompiler(), null));

    var result =
        tools.commitModelBatch(
            new ModelCommandBatch(
                List.of(
                    new ModelCommandBatch.Create(
                        "actor_new",
                        "Actor",
                        Map.of("name", text("Cyclist"), "actorType", text("HUMAN")),
                        "rootId",
                        "actors",
                        null),
                    new ModelCommandBatch.Create(
                        "command_new",
                        "Command",
                        Map.of("name", text("Book appointment")),
                        "rootId",
                        "commands",
                        null)),
                List.of(),
                List.of(
                    new ModelCommandBatch.Connection("actor_new", "issuesCommands", "command_new")),
                List.of(),
                List.of(),
                "duplicate actor",
                true));

    JsonNode actor = result.model().at("/actors/1");
    assertEquals(2, result.model().path("actors").size());
    assertEquals(
        result.model().at("/commands/0/id").asText(),
        actor.path("issuesCommands").path(0).asText());
  }

  @Test
  void rejectsTypePrefixedClientRefAliases() throws Exception {
    AgentModelTools tools = cimTools();
    ModelWorkspace workspace = workspace();
    tools.bind(ModelLevel.CIM, workspace);

    PlatformException error =
        assertThrows(
            PlatformException.class,
            () ->
                tools.commitModelBatch(
                    new ModelCommandBatch(
                        List.of(
                            new ModelCommandBatch.Create(
                                "RequestAppointment",
                                "Requirement",
                                Map.of("name", text("Request appointment")),
                                "rootId",
                                "requirements",
                                null),
                            new ModelCommandBatch.Create(
                                "ScheduleAppointment",
                                "BusinessGoal",
                                Map.of("name", text("Schedule appointment")),
                                "rootId",
                                "goals",
                                null)),
                        List.of(),
                        List.of(
                            new ModelCommandBatch.Connection(
                                "Requirement_RequestAppointment",
                                "supportsGoals",
                                "BusinessGoal_ScheduleAppointment")),
                        List.of(),
                        List.of(),
                        "type-prefixed refs",
                        true)));

    assertEquals(422, error.status());
    assertTrue(error.getMessage().contains("exact clientRef"));
  }

  @Test
  void resolvesSameBatchClientRefsToBackendGeneratedUuids() throws Exception {
    AgentModelTools tools = cimTools();
    ModelWorkspace workspace = workspace();
    tools.bind(ModelLevel.CIM, workspace);

    var result =
        tools.commitModelBatch(
            new ModelCommandBatch(
                List.of(
                    new ModelCommandBatch.Create(
                        "appointment_process",
                        "BusinessProcess",
                        Map.of("name", text("Schedule appointment")),
                        "rootId",
                        "processes",
                        null),
                    new ModelCommandBatch.Create(
                        "start_step",
                        "StartStep",
                        Map.of("name", text("Start scheduling")),
                        "appointment_process",
                        "steps",
                        null)),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                "draft domain",
                true));

    JsonNode model = result.model();
    String processId = model.at("/processes/0/id").asText();
    String stepId = model.at("/processes/0/steps/0/id").asText();

    assertNotEquals("appointment_process", processId);
    assertNotEquals("start_step", stepId);
    assertTrue(processId.matches("[0-9a-fA-F-]{36}"));
    assertTrue(stepId.matches("[0-9a-fA-F-]{36}"));
    assertEquals("Start scheduling", model.at("/processes/0/steps/0/name").asText());
  }

  @Test
  void doesNotSynthesizeCimSemanticContentWhenBatchOmitsIt() throws Exception {
    AgentModelTools tools = cimTools();
    ModelWorkspace workspace = workspace();
    tools.bind(ModelLevel.CIM, workspace);

    var result =
        tools.commitModelBatch(
            new ModelCommandBatch(
                List.of(
                    new ModelCommandBatch.Create(
                        "request_repair_appointment",
                        "Requirement",
                        Map.of(
                            "name", text("Request repair appointment online"),
                            "fitCriterion", text("Cyclist receives an appointment reference."),
                            "mandatory", mapper.getNodeFactory().booleanNode(true),
                            "productionBlocking", mapper.getNodeFactory().booleanNode(false)),
                        "rootId",
                        "requirements",
                        null)),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                "draft requirement",
                true));

    JsonNode model = result.model();
    assertEquals("Requirement", model.at("/requirements/0/eClass").asText());
    assertTrue(model.path("goals").isMissingNode() || model.path("goals").isEmpty());
    assertTrue(model.path("actors").isMissingNode() || model.path("actors").isEmpty());
    assertTrue(model.path("capabilities").isMissingNode() || model.path("capabilities").isEmpty());
  }

  @Test
  void rejectsConnectionAliasWithKnownClientRefs() throws Exception {
    AgentModelTools tools = cimTools();
    tools.bind(ModelLevel.CIM, workspace());

    PlatformException error =
        assertThrows(
            PlatformException.class,
            () ->
                tools.commitModelBatch(
                    new ModelCommandBatch(
                        List.of(
                            new ModelCommandBatch.Create(
                                "repair_appointment_request",
                                "InformationItem",
                                Map.of(
                                    "name", text("Repair appointment request"),
                                    "type", text("OBJECT")),
                                "rootId",
                                "informationItems",
                                null),
                            new ModelCommandBatch.Create(
                                "submit_request",
                                "Command",
                                Map.of("name", text("Submit request")),
                                "rootId",
                                "commands",
                                null)),
                        List.of(),
                        List.of(
                            new ModelCommandBatch.Connection(
                                "submit_request", "input", "info_repair_appointment_request")),
                        List.of(),
                        List.of(),
                        "draft command",
                        true)));

    assertEquals(422, error.status());
    assertTrue(error.getMessage().contains("info_repair_appointment_request"));
    assertTrue(error.getMessage().contains("repair_appointment_request"));
    assertTrue(error.getMessage().contains("without prefixes or aliases"));
  }

  @Test
  void rejectsInvalidConnectionReferenceWithWritableAlternatives() throws Exception {
    AgentModelTools tools = cimTools();
    tools.bind(ModelLevel.CIM, workspace());

    PlatformException error =
        assertThrows(
            PlatformException.class,
            () ->
                tools.commitModelBatch(
                    new ModelCommandBatch(
                        List.of(
                            new ModelCommandBatch.Create(
                                "submit_request",
                                "Command",
                                Map.of("name", text("Submit request")),
                                "rootId",
                                "commands",
                                null),
                            new ModelCommandBatch.Create(
                                "request_payload",
                                "InformationItem",
                                Map.of("name", text("Request payload"), "type", text("OBJECT")),
                                "rootId",
                                "informationItems",
                                null)),
                        List.of(),
                        List.of(
                            new ModelCommandBatch.Connection(
                                "submit_request", "dependsOn", "request_payload")),
                        List.of(),
                        List.of(),
                        "draft command",
                        true)));

    assertEquals(422, error.status());
    assertTrue(error.getMessage().contains("dependsOn"));
    assertTrue(error.getMessage().contains("Valid writable references"));
    assertTrue(error.getMessage().contains("input"));
  }

  @Test
  void inspectsSelectedModelRecordsWithTypeAndOwnershipContext() throws Exception {
    AgentModelTools tools = cimTools();
    JsonNode model =
        mapper.readTree(
            """
{"id":"root","eClass":"CIMModel","modelLevel":"CIM","domains":[
  {"id":"sales","eClass":"Domain","name":"Sales","entities":[
    {"id":"order","eClass":"DomainEntity","name":"Order"}]}]}
""");
    tools.bind(
        ModelLevel.CIM,
        new ModelWorkspace(ModelLevel.CIM, "m", 1, model, new AssistantPatchCompiler(), null));

    JsonNode inspected =
        tools.inspectModel(
            new AgentModelTools.InspectionSelector(
                List.of("order"), List.of("DomainEntity"), List.of("sales"), null, 0, 10));

    assertEquals(1, inspected.path("total").asInt());
    assertEquals("order", inspected.path("elements").get(0).path("id").asText());
    assertEquals("sales", inspected.path("elements").get(0).path("ownerId").asText());
  }

  @Test
  void rejectsCreateWithoutExplicitRequiredChildrenInsteadOfSynthesizingThem() throws Exception {
    AgentModelTools tools = cimTools();
    tools.bind(ModelLevel.CIM, workspace());

    assertThrows(
        PlatformException.class,
        () ->
            tools.commitModelBatch(
                new ModelCommandBatch(
                    List.of(
                        new ModelCommandBatch.Create(
                            "schedule",
                            "BusinessProcess",
                            Map.of("name", text("Schedule appointment")),
                            null,
                            null,
                            null)),
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of(),
                    "draft process",
                    true)));
  }

  @Test
  void treatsUpdatePreconditionHashAsAdvisoryForCurrentRevisionEdits() throws Exception {
    AgentModelTools tools = cimTools();
    JsonNode model =
        mapper.readTree(
            """
{"id":"root","eClass":"CIMModel","modelLevel":"CIM","goals":[
  {"id":"goal-1","eClass":"BusinessGoal","name":"Original"}],
 "diagram":{"elements":[],"relationships":[]}}
""");
    tools.bind(
        ModelLevel.CIM,
        new ModelWorkspace(ModelLevel.CIM, "m", 1, model, new AssistantPatchCompiler(), null));

    var result =
        tools.commitModelBatch(
            new ModelCommandBatch(
                List.of(),
                List.of(
                    new ModelCommandBatch.Update(
                        "goal-1", Map.of("name", text("Updated")), "provider-stale-hash")),
                List.of(),
                List.of(),
                List.of(),
                "rename goal",
                true));

    assertEquals("Updated", result.model().at("/goals/0/name").asText());
  }

  @Test
  void rejectsMissingClientRefForStandaloneCreate() throws Exception {
    AgentModelTools tools = cimTools();
    tools.bind(ModelLevel.CIM, workspace());

    PlatformException error =
        assertThrows(
            PlatformException.class,
            () ->
                tools.commitModelBatch(
                    new ModelCommandBatch(
                        List.of(
                            new ModelCommandBatch.Create(
                                null,
                                "BusinessGoal",
                                Map.of("name", text("Library borrowing")),
                                "rootId",
                                "goals",
                                null)),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        "create goal",
                        true)));

    assertEquals(422, error.status());
    assertTrue(error.getMessage().contains("non-empty clientRef"));
  }

  @Test
  void rejectsWrongOwnerForRootPimElement() throws Exception {
    AgentModelTools tools = pimTools();
    JsonNode model =
        mapper.readTree(
            """
{"id":"root","eClass":"PIMModel","modelLevel":"PIM","architectureStyle":"SERVERLESS",
 "implementationProfile":{"id":"profile","eClass":"ImplementationProfile","name":"AWS"},
 "diagram":{"elements":[],"relationships":[]}}
""");
    tools.bind(
        ModelLevel.PIM,
        new ModelWorkspace(ModelLevel.PIM, "m", 1, model, new AssistantPatchCompiler(), null));

    PlatformException error =
        assertThrows(
            PlatformException.class,
            () ->
                tools.commitModelBatch(
                    new ModelCommandBatch(
                        List.of(
                            new ModelCommandBatch.Create(
                                "duplicate-rule",
                                "BusinessRule",
                                Map.of("name", text("Reject duplicate commands")),
                                "profile",
                                "businessRules",
                                null)),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        "add idempotency rule",
                        true)));

    assertEquals(422, error.status());
    assertTrue(error.getMessage().contains("Valid exact containments"));
  }

  @Test
  void rejectsWrongOwnerForCachePolicy() throws Exception {
    AgentModelTools tools = pimTools();
    JsonNode model =
        mapper.readTree(
            """
{"id":"root","eClass":"PIMModel","modelLevel":"PIM","architectureStyle":"SERVERLESS",
 "implementationProfile":{"id":"profile","eClass":"ImplementationProfile","name":"AWS"},
 "diagram":{"elements":[],"relationships":[]}}
""");
    tools.bind(
        ModelLevel.PIM,
        new ModelWorkspace(ModelLevel.PIM, "m", 1, model, new AssistantPatchCompiler(), null));

    PlatformException error =
        assertThrows(
            PlatformException.class,
            () ->
                tools.commitModelBatch(
                    new ModelCommandBatch(
                        List.of(
                            new ModelCommandBatch.Create(
                                "cache-policy",
                                "CachePolicy",
                                Map.of(
                                    "name",
                                    text("Read cache"),
                                    "cacheRequired",
                                    mapper.getNodeFactory().booleanNode(true)),
                                "profile",
                                "policies",
                                null)),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        "add cache policy",
                        true)));

    assertEquals(422, error.status());
    assertTrue(error.getMessage().contains("Valid exact containments"));
  }

  @Test
  void rejectsDuplicateRootSingletonProfileCreate() throws Exception {
    AgentModelTools tools = pimTools();
    JsonNode model =
        mapper.readTree(
            """
{"id":"root","eClass":"PIMModel","modelLevel":"PIM","architectureStyle":"SERVERLESS",
 "implementationProfile":{"id":"profile","eClass":"ImplementationProfile","name":"AWS"},
 "diagram":{"elements":[],"relationships":[]}}
""");
    tools.bind(
        ModelLevel.PIM,
        new ModelWorkspace(ModelLevel.PIM, "m", 1, model, new AssistantPatchCompiler(), null));

    PlatformException error =
        assertThrows(
            PlatformException.class,
            () ->
                tools.commitModelBatch(
                    new ModelCommandBatch(
                        List.of(
                            new ModelCommandBatch.Create(
                                "profile_duplicate",
                                "ImplementationProfile",
                                Map.of("name", text("OpenRouter deployment")),
                                "rootId",
                                "implementationProfile",
                                null),
                            new ModelCommandBatch.Create(
                                "handler",
                                "Function",
                                Map.of(
                                    "name",
                                    text("Submit order handler"),
                                    "functionKind",
                                    text("COMMAND_HANDLER")),
                                "profile_duplicate",
                                "functions",
                                null)),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        "add duplicate profile and handler",
                        true)));

    assertEquals(422, error.status());
  }

  @Test
  void rejectsRootModelCreateAndRequiresExplicitRootUpdate() throws Exception {
    AgentModelTools tools = cimTools();
    tools.bind(ModelLevel.CIM, workspace());

    PlatformException error =
        assertThrows(
            PlatformException.class,
            () ->
                tools.commitModelBatch(
                    new ModelCommandBatch(
                        List.of(
                            new ModelCommandBatch.Create(
                                "root_model",
                                "CIMModel",
                                Map.of("domainName", text("Community pantry")),
                                "rootId",
                                "exactContainment",
                                null),
                            new ModelCommandBatch.Create(
                                "pantry_policy",
                                "Policy",
                                Map.of("name", text("Eligibility policy")),
                                null,
                                null,
                                null)),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        "draft pantry",
                        true)));

    assertEquals(422, error.status());
    assertTrue(error.getMessage().contains("root already exists"));
  }

  @Test
  void rejectsUnknownIdempotencyUpdateInsteadOfInventingCreate() throws Exception {
    AgentModelTools tools = pimTools();
    JsonNode model =
        mapper.readTree(
            """
{"id":"root","eClass":"PIMModel","modelLevel":"PIM","architectureStyle":"SERVERLESS",
 "diagram":{"elements":[],"relationships":[]}}
""");
    tools.bind(
        ModelLevel.PIM,
        new ModelWorkspace(ModelLevel.PIM, "m", 1, model, new AssistantPatchCompiler(), null));

    PlatformException error =
        assertThrows(
            PlatformException.class,
            () ->
                tools.commitModelBatch(
                    new ModelCommandBatch(
                        List.of(),
                        List.of(
                            new ModelCommandBatch.Update(
                                "IdempotencyPolicy_1",
                                Map.of(
                                    "name",
                                    text("Command idempotency"),
                                    "storeRequired",
                                    mapper.getNodeFactory().booleanNode(true)),
                                "")),
                        List.of(),
                        List.of(),
                        List.of(),
                        "add idempotency",
                        true)));

    assertEquals(422, error.status());
    assertTrue(error.getMessage().contains("not a known clientRef"));
  }

  @Test
  void rejectsUnknownCacheUpdateInsteadOfInventingCreate() throws Exception {
    AgentModelTools tools = pimTools();
    JsonNode model =
        mapper.readTree(
            """
{"id":"root","eClass":"PIMModel","modelLevel":"PIM","architectureStyle":"SERVERLESS",
 "diagram":{"elements":[],"relationships":[]}}
""");
    tools.bind(
        ModelLevel.PIM,
        new ModelWorkspace(ModelLevel.PIM, "m", 1, model, new AssistantPatchCompiler(), null));

    PlatformException error =
        assertThrows(
            PlatformException.class,
            () ->
                tools.commitModelBatch(
                    new ModelCommandBatch(
                        List.of(),
                        List.of(
                            new ModelCommandBatch.Update(
                                "cache_component",
                                Map.of(
                                    "name",
                                    text("Read-through cache"),
                                    "owner",
                                    text("application"),
                                    "cacheRequired",
                                    mapper.getNodeFactory().booleanNode(true)),
                                "")),
                        List.of(),
                        List.of(),
                        List.of(),
                        "add cache",
                        true)));

    assertEquals(422, error.status());
    assertTrue(error.getMessage().contains("not a known clientRef"));
  }

  @Test
  void rejectsMisownedFunctionInsteadOfCreatingSyntheticService() throws Exception {
    AgentModelTools tools = pimTools();
    JsonNode model =
        mapper.readTree(
            """
{"id":"root","eClass":"PIMModel","modelLevel":"PIM","architectureStyle":"SERVERLESS",
 "implementationProfile":{"id":"profile","eClass":"ImplementationProfile","name":"AWS"},
 "diagram":{"elements":[],"relationships":[]}}
""");
    tools.bind(
        ModelLevel.PIM,
        new ModelWorkspace(ModelLevel.PIM, "m", 1, model, new AssistantPatchCompiler(), null));

    PlatformException error =
        assertThrows(
            PlatformException.class,
            () ->
                tools.commitModelBatch(
                    new ModelCommandBatch(
                        List.of(
                            new ModelCommandBatch.Create(
                                "handler",
                                "Function",
                                Map.of(
                                    "name",
                                    text("Submit order handler"),
                                    "functionKind",
                                    text("COMMAND_HANDLER")),
                                "profile",
                                "exactContainment",
                                null)),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        "add handler",
                        true)));

    assertEquals(422, error.status());
    assertTrue(error.getMessage().contains("Valid exact containments"));
  }

  private AgentModelTools cimTools() {
    var knowledge = new MetamodelKnowledgeService(new AssistantMetamodelSchemaService());
    return new AgentModelTools(
        new TypeContractService(knowledge),
        mock(ModelService.class),
        new ModelCommandCompiler(new AssistantPatchCompiler()));
  }

  private AgentModelTools pimTools() {
    var knowledge = new MetamodelKnowledgeService(new AssistantMetamodelSchemaService());
    return new AgentModelTools(
        new TypeContractService(knowledge),
        mock(ModelService.class),
        new ModelCommandCompiler(new AssistantPatchCompiler()));
  }

  @Test
  void listsExactCompatibleExistingOwnersForRepairWithoutResolvingNames() throws Exception {
    AgentModelTools tools = pimTools();
    JsonNode model =
        mapper.readTree(
            """
{"id":"pim-root","eClass":"PIMModel","modelLevel":"PIM","services":[{"id":"svc-123","eClass":"ServerlessService","name":"OrderFulfillmentService","boundaryType":"CAPABILITY_BASED","functions":[]}],"diagram":{"elements":[],"relationships":[]}}
""");
    tools.bind(
        ModelLevel.PIM,
        new ModelWorkspace(ModelLevel.PIM, "pim", 1, model, new AssistantPatchCompiler(), null));

    JsonNode owners = tools.eligibleExistingOwners(List.of("Function"));

    JsonNode candidates = owners.path("byChildType").get(0).path("candidates");
    assertEquals(1, candidates.size());
    assertEquals("svc-123", candidates.get(0).path("id").asText());
    assertEquals("OrderFulfillmentService", candidates.get(0).path("name").asText());
    assertEquals("ServerlessService", candidates.get(0).path("eClass").asText());
    assertEquals("functions", candidates.get(0).path("containments").get(0).asText());
  }

  private ModelWorkspace workspace() throws Exception {
    JsonNode model =
        mapper.readTree(
            """
{"id":"root","eClass":"CIMModel","modelLevel":"CIM","diagram":{"elements":[],"relationships":[]}}
""");
    return new ModelWorkspace(ModelLevel.CIM, "m", 1, model, new AssistantPatchCompiler(), null);
  }

  private JsonNode text(String value) {
    return mapper.getNodeFactory().textNode(value);
  }
}
