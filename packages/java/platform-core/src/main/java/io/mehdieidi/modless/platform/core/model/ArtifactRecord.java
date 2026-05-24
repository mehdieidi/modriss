package io.mehdieidi.modless.platform.core.model;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.Map;

public record ArtifactRecord(
        String id,
        String projectId,
        String name,
        JsonNode modelJson,
        Map<String, String> files,
        Instant createdAt,
        Instant updatedAt) {

}
