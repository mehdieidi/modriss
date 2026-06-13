package io.mehdieidi.modless.backend.assistant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
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
}
