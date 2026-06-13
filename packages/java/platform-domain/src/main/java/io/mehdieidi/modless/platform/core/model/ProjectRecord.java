package io.mehdieidi.modless.platform.core.model;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Persisted project metadata and membership state.
 *
 * @param id project identifier
 * @param name display name
 * @param description project description
 * @param ownerUserId owner user identifier
 * @param activeModelIds active model id by level or view key
 * @param members project members
 * @param createdAt creation timestamp
 * @param updatedAt last update timestamp
 */
public record ProjectRecord(
    String id,
    String name,
    String description,
    String ownerUserId,
    Map<String, String> activeModelIds,
    List<ProjectMember> members,
    Instant createdAt,
    Instant updatedAt) {}
