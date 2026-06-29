package io.mehdieidi.modless.platform.assistant.spi;

import io.mehdieidi.modless.platform.assistant.provider.AssistantModelProvider;
import java.util.List;
import java.util.Optional;

/** Metamodel and methodology catalog retrieval for assistant prompts and tools. */
public interface AssistantCatalog {

  /** Rebuilds catalog indexes from packaged metamodel definitions. */
  void refresh();

  /**
   * Searches catalogs using exact symbol lookup first and fuzzy matching second.
   *
   * @param query search text
   * @param level optional model level
   * @param limit maximum number of results
   * @return catalog snippets
   */
  List<AssistantModelProvider.ContextSnippet> search(String query, String level, int limit);

  /**
   * Describes one metamodel type.
   *
   * @param typeName type name
   * @param level model level
   * @param limit maximum snippets
   * @return catalog snippets
   */
  List<AssistantModelProvider.ContextSnippet> describeType(
      String typeName, String level, int limit);

  /**
   * Resolves a canonical enum literal when available.
   *
   * @param enumType enum type name
   * @param literal candidate literal
   * @param level model level
   * @return canonical literal when found
   */
  Optional<String> canonicalEnumLiteral(
      String ownerType, String featureName, String value, String level);
}
