package io.mehdieidi.modless.platform.core.model;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.Map;

/**
 * Persisted generated artifact bundle and its in-repository file contents.
 *
 * @param id        artifact identifier
 * @param projectId owning project identifier
 * @param name      display name
 * @param modelJson metadata describing the artifact bundle
 * @param files     generated files keyed by normalized artifact path
 * @param createdAt creation timestamp
 * @param updatedAt last update timestamp
 */
public record ArtifactRecord(
        String id,
        String projectId,
        String name,
        JsonNode modelJson,
        Map<String, String> files,
        Instant createdAt,
        Instant updatedAt) {

}
