package io.mehdieidi.modless.platform.core.model;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;

public record ModelRecord(
        String id,
        String projectId,
        ModelLevel level,
        String name,
        JsonNode modelJson,
        Instant createdAt,
        Instant updatedAt) {

}
