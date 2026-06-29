package io.mehdieidi.modless.platform.assistant.application;

import io.mehdieidi.modless.platform.assistant.provider.AssistantModelProvider;
import io.mehdieidi.modless.platform.assistant.spi.AssistantCatalog;
import java.util.List;

/** Plan-facing facade for metamodel catalog lookup. */
public class MetamodelCatalogService {

  private final AssistantCatalog catalogs;

  public MetamodelCatalogService(AssistantCatalog catalogs) {
    this.catalogs = catalogs;
  }

  /**
   * Searches metamodel documents.
   *
   * @param query search query
   * @param level modeling level
   * @param limit maximum snippets
   * @return matching catalog snippets
   */
  public List<AssistantModelProvider.ContextSnippet> search(String query, String level, int limit) {
    return catalogs.search(query, level, limit);
  }
}
