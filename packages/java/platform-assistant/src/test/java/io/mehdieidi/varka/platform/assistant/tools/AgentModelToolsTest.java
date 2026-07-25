package io.mehdieidi.varka.platform.assistant.tools;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
