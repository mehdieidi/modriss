package io.mehdieidi.modless.backend.assistant;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
}
