package io.mehdieidi.modless.backend.assistant;

import java.util.List;
import org.springframework.stereotype.Service;

/**
 * Plan-facing facade for metamodel catalog lookup.
 */
@Service
public class MetamodelCatalogService {

    private final AssistantCatalogService catalogs;

    public MetamodelCatalogService(AssistantCatalogService catalogs) {
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
    public List<AssistantModelProvider.ContextSnippet> search(String query, String level,
            int limit) {
        return catalogs.search(query, level, limit).stream()
                .filter(snippet -> !snippet.source().toLowerCase(java.util.Locale.ROOT)
                        .endsWith(".evl"))
                .toList();
    }
}
