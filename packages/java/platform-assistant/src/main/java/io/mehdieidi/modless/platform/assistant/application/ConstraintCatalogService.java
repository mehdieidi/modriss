package io.mehdieidi.modless.platform.assistant.application;

import io.mehdieidi.modless.platform.assistant.provider.AssistantModelProvider;
import io.mehdieidi.modless.platform.assistant.spi.AssistantCatalog;
import java.util.List;

/** Plan-facing facade for EVL constraint catalog lookup. */
public class ConstraintCatalogService {

  private final AssistantCatalog catalogs;

  public ConstraintCatalogService(AssistantCatalog catalogs) {
    this.catalogs = catalogs;
  }

  /**
   * Searches EVL constraint documents.
   *
   * @param query search query
   * @param level modeling level
   * @param limit maximum snippets
   * @return matching constraint snippets
   */
  public List<AssistantModelProvider.ContextSnippet> search(String query, String level, int limit) {
    return catalogs.search(query, level, limit).stream()
        .filter(snippet -> snippet.source().toLowerCase(java.util.Locale.ROOT).endsWith(".evl"))
        .toList();
  }
}
