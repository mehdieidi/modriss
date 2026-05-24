package io.mehdieidi.modless.platform.core.model;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record ProjectRecord(
        String id,
        String name,
        String description,
        String ownerUserId,
        Map<String, String> activeModelIds,
        List<ProjectMember> members,
        Instant createdAt,
        Instant updatedAt) {

}
