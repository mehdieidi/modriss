package io.mehdieidi.modless.platform.assistant.tools;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.mehdieidi.modless.platform.assistant.delta.DeltaCompiler;
import io.mehdieidi.modless.platform.assistant.patch.AssistantMetamodelSchemaService;
import io.mehdieidi.modless.platform.assistant.patch.AssistantPatchCompiler;
import io.mehdieidi.modless.platform.assistant.persistence.jdbc.JdbcAssistantModelContextIndex;
import io.mehdieidi.modless.platform.assistant.spi.AssistantCatalog;
import io.mehdieidi.modless.platform.assistant.spi.AssistantToolBridge;
import io.mehdieidi.modless.platform.kernel.ModelLevel;
import io.mehdieidi.modless.platform.model.application.ModelService;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.annotation.Tool;

class AssistantToolServiceTest {

  private final ObjectMapper mapper = new ObjectMapper();
  private final ModelService models = mock(ModelService.class);

  @Test
  void exposesWhitelistedSpringAiTools() {
    long annotated =
        java.util.Arrays.stream(AssistantToolService.class.getDeclaredMethods())
            .filter(method -> method.isAnnotationPresent(Tool.class))
            .count();

    assertEquals(15, annotated);
    assertNotNull(tool("searchCatalogs"));
    assertNotNull(tool("previewModelDelta"));
    assertNotNull(tool("summarizeValidation"));
    assertNotNull(tool("requestUserChoice"));
    assertNotNull(tool("getElementContext"));
    assertNotNull(tool("getTypeContract"));
    assertNotNull(tool("validateSnapshot"));
    assertNotNull(tool("listModelElements"));
    assertNotNull(tool("inspectCurrentModelDelta"));
    assertNotNull(tool("getLanguageIndex"));
    assertNotNull(tool("getMetamodelCoverage"));
    assertNotNull(tool("findCreatableTypes"));
    assertNotNull(tool("findContainmentOptions"));
    assertNotNull(tool("findReferenceOptions"));
    assertNotNull(tool("summarizeCurrentModel"));
  }

  @Test
  void previewsModelDeltaWithoutCommitting() throws Exception {
    AssistantCatalog catalogs = mock(AssistantCatalog.class);
    when(catalogs.search(anyString(), anyString(), anyInt())).thenReturn(java.util.List.of());
    AssistantToolService tools =
        new AssistantToolService(
            catalogs,
            new AssistantPatchCompiler(),
            new DeltaCompiler(new AssistantMetamodelSchemaService()),
            new AssistantMetamodelSchemaService(),
            models,
            mapper);

    AssistantToolService.PreviewResult result =
        tools.previewModelDelta(
            "{\"eClass\":\"PIMModel\",\"modelLevel\":\"PIM\",\"diagram\":{\"elements\":[{\"id\":\"service-1\",\"eClass\":\"Function\",\"name\":\"Old\"}],\"relationships\":[]}}",
            "{\"kind\":\"MODEL_DELTA\",\"attributeUpdates\":[{\"elementId\":\"service-1\",\"attributeName\":\"name\",\"value\":\"New\"}]}");

    assertEquals("service-1", result.affectedElements().get(0));
    assertEquals("New", result.preview().at("/diagram/elements/0/name").asText());
    assertTrue(result.inversePatch().size() == 1);
  }

  @Test
  void listModelElementsUsesBoundSession() throws Exception {
    when(models.validate(any(), any()))
        .thenReturn(new ModelService.ValidationResult(true, java.util.List.of()));
    AssistantToolService tools =
        new AssistantToolService(
            mock(AssistantCatalog.class),
            new AssistantPatchCompiler(),
            new DeltaCompiler(new AssistantMetamodelSchemaService()),
            new AssistantMetamodelSchemaService(),
            models,
            mapper);
    var model =
        mapper.readTree(
            "{\"id\":\"root\",\"eClass\":\"PIMModel\",\"modelLevel\":\"PIM\",\"diagram\":{\"elements\":[{\"id\":\"fn-1\",\"eClass\":\"Function\",\"name\":\"A\"}],\"relationships\":[]}}");
    var context =
        new JdbcAssistantModelContextIndex()
            .transientSnapshot("project", ModelLevel.PIM, "Orders", 1L, model, null);
    tools.bindSession(new AssistantToolBridge.ToolSession(ModelLevel.PIM, model, context));

    AssistantToolService.ElementPage page = tools.listModelElements("Function", "", 0, 10);

    assertEquals(1, page.totalElements());
    assertEquals("fn-1", page.elements().get(0).id());
    tools.clearSession();
  }

  @Test
  void inspectsCurrentModelDeltaAgainstBoundSnapshot() throws Exception {
    when(models.validateStructural(any(), any()))
        .thenReturn(new ModelService.ValidationResult(true, java.util.List.of()));
    AssistantToolService tools =
        new AssistantToolService(
            mock(AssistantCatalog.class),
            new AssistantPatchCompiler(),
            new DeltaCompiler(new AssistantMetamodelSchemaService()),
            new AssistantMetamodelSchemaService(),
            models,
            mapper);
    var model =
        mapper.readTree(
            "{\"id\":\"root\",\"eClass\":\"PIMModel\",\"modelLevel\":\"PIM\",\"diagram\":{\"elements\":[{\"id\":\"fn-1\",\"eClass\":\"Function\",\"name\":\"A\"}],\"relationships\":[]}}");
    var context =
        new JdbcAssistantModelContextIndex()
            .transientSnapshot("project", ModelLevel.PIM, "Orders", 1L, model, null);
    tools.bindSession(new AssistantToolBridge.ToolSession(ModelLevel.PIM, model, context));

    AssistantToolService.PatchInspectionResult result =
        tools.inspectCurrentModelDelta(
            "{\"kind\":\"MODEL_DELTA\",\"attributeUpdates\":[{\"elementId\":\"fn-1\",\"attributeName\":\"name\",\"value\":\"B\"}]}");

    assertTrue(result.acceptable());
    assertEquals(1, result.semanticOperationCount());
    assertEquals("fn-1", result.affectedElements().get(0));
    tools.clearSession();
  }

  @Test
  void findsContainmentOptionsForBoundSnapshot() throws Exception {
    AssistantToolService tools =
        new AssistantToolService(
            mock(AssistantCatalog.class),
            new AssistantPatchCompiler(),
            new DeltaCompiler(new AssistantMetamodelSchemaService()),
            new AssistantMetamodelSchemaService(),
            models,
            mapper);
    var model =
        mapper.readTree(
            "{\"id\":\"root\",\"eClass\":\"PIMModel\",\"modelLevel\":\"PIM\",\"services\":[{\"id\":\"svc-1\",\"eClass\":\"ServerlessService\",\"name\":\"Orders\",\"functions\":[]}],\"diagram\":{\"elements\":[],\"relationships\":[]}}");
    var context =
        new JdbcAssistantModelContextIndex()
            .transientSnapshot("project", ModelLevel.PIM, "Orders", 1L, model, null);
    tools.bindSession(new AssistantToolBridge.ToolSession(ModelLevel.PIM, model, context));

    AssistantToolService.ContainmentOptions result =
        tools.findContainmentOptions("Function", "", 10);

    assertEquals("Function", result.childType());
    assertFalse(result.options().isEmpty());
    assertTrue(
        result.options().stream()
            .anyMatch(
                option ->
                    "svc-1".equals(option.ownerElementId())
                        && "ServerlessService".equals(option.ownerType())
                        && "functions".equals(option.referenceName())));
    tools.clearSession();
  }

  @Test
  void findsReferenceOptionsForBoundSnapshot() throws Exception {
    AssistantToolService tools =
        new AssistantToolService(
            mock(AssistantCatalog.class),
            new AssistantPatchCompiler(),
            new DeltaCompiler(new AssistantMetamodelSchemaService()),
            new AssistantMetamodelSchemaService(),
            models,
            mapper);
    var model =
        mapper.readTree(
            "{\"id\":\"root\",\"eClass\":\"PIMModel\",\"modelLevel\":\"PIM\",\"diagram\":{\"elements\":[{\"id\":\"fn-1\",\"eClass\":\"Function\",\"name\":\"Handler\"},{\"id\":\"store-1\",\"eClass\":\"DataStore\",\"name\":\"Orders\"}],\"relationships\":[]}}");
    var context =
        new JdbcAssistantModelContextIndex()
            .transientSnapshot("project", ModelLevel.PIM, "Orders", 1L, model, null);
    tools.bindSession(new AssistantToolBridge.ToolSession(ModelLevel.PIM, model, context));

    AssistantToolService.ReferenceOptions result =
        tools.findReferenceOptions("Function", "DataStore", "reads", 10);

    assertEquals("Function", result.sourceTypeFilter());
    assertEquals("DataStore", result.targetTypeFilter());
    assertTrue(
        result.options().stream()
            .anyMatch(
                option ->
                    "fn-1".equals(option.sourceElementId())
                        && "store-1".equals(option.targetElementId())
                        && "reads".equals(option.referenceName())));
    tools.clearSession();
  }

  @Test
  void reportsMetamodelCoverageForAgentSelfChecks() {
    AssistantToolService tools =
        new AssistantToolService(
            mock(AssistantCatalog.class),
            new AssistantPatchCompiler(),
            new DeltaCompiler(new AssistantMetamodelSchemaService()),
            new AssistantMetamodelSchemaService(),
            models,
            mapper);

    var coverage = tools.getMetamodelCoverage("CIM");

    assertEquals(ModelLevel.CIM, coverage.level());
    assertTrue(coverage.creatableTypes() > 0);
    assertTrue(coverage.attributes() > 0);
    assertTrue(coverage.containments() > 0);
    assertTrue(coverage.relationships() > 0);
    assertFalse(coverage.typeNames().isEmpty());
  }

  private Method tool(String name) {
    return java.util.Arrays.stream(AssistantToolService.class.getDeclaredMethods())
        .filter(method -> method.isAnnotationPresent(Tool.class))
        .filter(method -> method.getAnnotation(Tool.class).name().equals(name))
        .findFirst()
        .orElse(null);
  }
}
