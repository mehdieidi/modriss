package io.mehdieidi.modless.backend.assistant;

import java.util.List;
import org.springframework.stereotype.Service;

/** Plan-facing facade for EVL constraint catalog lookup. */
@Service
public class ConstraintCatalogService {

  private final AssistantCatalogService catalogs;

  public ConstraintCatalogService(AssistantCatalogService catalogs) {
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
