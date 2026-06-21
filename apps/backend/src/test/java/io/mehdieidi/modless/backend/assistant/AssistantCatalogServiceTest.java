package io.mehdieidi.modless.backend.assistant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.mehdieidi.modless.platform.model.application.ModelService;
import java.lang.reflect.Method;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class AssistantCatalogServiceTest {

  @Test
  void sharedArtifactsAreTaggedAsSharedForAllLevelSearches() throws Exception {
    AssistantCatalogService catalogs = new AssistantCatalogService((JdbcTemplate) null);
    Method level = AssistantCatalogService.class.getDeclaredMethod("level", String.class);
    level.setAccessible(true);

    assertEquals("SHARED", level.invoke(catalogs, "mde/validation/shared/kernel-constraints.evl"));
    assertEquals("SHARED", level.invoke(catalogs, "mde/metamodels/shared/kernel.ecore"));
    assertEquals("PIM", level.invoke(catalogs, "mde/metamodels/pim/pim-root.emf"));
  }

  @Test
  void canonicalizesCaseInsensitiveEnumLiteralFromMetamodelDescription() {
    AssistantCatalogService catalogs = new AssistantCatalogService((JdbcTemplate) null);

    assertEquals(
        "TYPESCRIPT",
        catalogs
            .canonicalEnumLiteral(
                "enum RuntimeLanguage allowed literals TYPESCRIPT, JAVASCRIPT, PYTHON",
                "TypeScript")
            .orElseThrow());
    assertTrue(
        catalogs
            .canonicalEnumLiteral(
                "enum RuntimeLanguage allowed literals TYPESCRIPT, JAVASCRIPT, PYTHON", "Ruby")
            .isEmpty());
  }

  @Test
  void searchCatalogsReturnsValidationConstraintsForApiRoutesQuery() {
    AssistantCatalogService catalogs = mock(AssistantCatalogService.class);
    when(catalogs.search("Api routes validation", "PIM", 8))
        .thenReturn(
            List.of(
                new AssistantModelProvider.ContextSnippet(
                    "mde/validation/pim/api-routes.evl",
                    "ApiRoutesMustBeConnected",
                    "context Api\nkind mandatory\nconstraint ApiRoutesMustBeConnected")));
    AssistantToolService tools =
        new AssistantToolService(
            catalogs,
            new AssistantPatchCompiler(),
            new AssistantMetamodelSchemaService(),
            mock(ModelService.class),
            new ObjectMapper());

    List<AssistantModelProvider.ContextSnippet> results =
        tools.searchCatalogs("Api routes validation", "PIM", 8);

    assertEquals(1, results.size());
    assertEquals("ApiRoutesMustBeConnected", results.get(0).title());
  }
}
