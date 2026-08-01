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
  void rejectsDuplicateNamedCreateSoAgentReusesExistingElements() throws Exception {
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

    PlatformException error =
        assertThrows(
            PlatformException.class,
            () ->
                tools.commitModelBatch(
                    new ModelCommandBatch(
                        List.of(
                            new ModelCommandBatch.Create(
                                "actor_new",
                                "Actor",
                                Map.of("name", text("Cyclist"), "actorType", text("HUMAN")),
                                "rootId",
                                "actors",
                                null)),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        "duplicate actor",
                        true)));

    assertEquals(422, error.status());
    assertTrue(error.getMessage().contains("duplicates existing Actor named 'Cyclist'"));
    assertTrue(error.getMessage().contains("actor-existing"));
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

  private AgentModelTools cimTools() {
    var knowledge = new MetamodelKnowledgeService(new AssistantMetamodelSchemaService());
    return new AgentModelTools(
        new TypeContractService(knowledge),
        mock(ModelService.class),
        new ModelCommandCompiler(new AssistantPatchCompiler()));
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
