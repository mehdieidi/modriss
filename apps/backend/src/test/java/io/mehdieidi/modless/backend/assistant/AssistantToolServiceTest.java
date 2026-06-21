package io.mehdieidi.modless.backend.assistant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
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

    assertEquals(8, annotated);
    assertNotNull(tool("searchCatalogs"));
    assertNotNull(tool("previewSemanticPatch"));
    assertNotNull(tool("summarizeValidation"));
    assertNotNull(tool("requestUserChoice"));
    assertNotNull(tool("getElementContext"));
    assertNotNull(tool("getTypeContract"));
    assertNotNull(tool("validateSnapshot"));
    assertNotNull(tool("listModelElements"));
  }

  @Test
  void previewsSemanticPatchWithoutCommitting() throws Exception {
    AssistantToolService tools =
        new AssistantToolService(
            new AssistantCatalogService(null) {
              @Override
              public java.util.List<AssistantModelProvider.ContextSnippet> search(
                  String query, String level, int limit) {
                return java.util.List.of();
              }
            },
            new AssistantPatchCompiler(),
            new AssistantMetamodelSchemaService(),
            models,
            mapper);

    AssistantToolService.PreviewResult result =
        tools.previewSemanticPatch(
            "{\"eClass\":\"PIMModel\",\"modelLevel\":\"PIM\",\"diagram\":{\"elements\":[{\"id\":\"service-1\",\"eClass\":\"Function\",\"name\":\"Old\"}],\"relationships\":[]}}",
            "{\"operations\":[{\"type\":\"SET_ATTRIBUTE\",\"targetElementId\":\"service-1\",\"elementType\":\"Function\",\"attributes\":\"New\",\"referenceName\":\"name\"}]}");

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
            new AssistantCatalogService(null),
            new AssistantPatchCompiler(),
            new AssistantMetamodelSchemaService(),
            models,
            mapper);
    var model =
        mapper.readTree(
            "{\"id\":\"root\",\"eClass\":\"PIMModel\",\"modelLevel\":\"PIM\",\"diagram\":{\"elements\":[{\"id\":\"fn-1\",\"eClass\":\"Function\",\"name\":\"A\"}],\"relationships\":[]}}");
    var context =
        new AssistantModelContextIndexService()
            .transientSnapshot("project", ModelLevel.PIM, "Orders", 1L, model, null);
    tools.bindSession(new AssistantToolService.ToolSession(ModelLevel.PIM, model, context));

    AssistantToolService.ElementPage page = tools.listModelElements("Function", "", 0, 10);

    assertEquals(1, page.totalElements());
    assertEquals("fn-1", page.elements().get(0).id());
    tools.clearSession();
  }

  private Method tool(String name) {
    return java.util.Arrays.stream(AssistantToolService.class.getDeclaredMethods())
        .filter(method -> method.isAnnotationPresent(Tool.class))
        .filter(method -> method.getAnnotation(Tool.class).name().equals(name))
        .findFirst()
        .orElse(null);
  }
}
