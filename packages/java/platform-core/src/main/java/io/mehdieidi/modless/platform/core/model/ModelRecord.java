package io.mehdieidi.modless.platform.core.model;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;

/**
 * Persisted model JSON plus metadata used for revisioning, migration, and XMI sidecar integrity.
 *
 * @param id               model identifier
 * @param projectId        owning project identifier
 * @param level            model level
 * @param name             display name
 * @param modelJson        stored model JSON
 * @param metamodelVersion metamodel namespace/version observed at persistence time
 * @param metamodelHash    SHA-256 hash of the metamodel observed at persistence time
 * @param revision         optimistic concurrency revision
 * @param sourceXmiHash    SHA-256 hash of the stored source XMI sidecar
 * @param migrationState   migration status relative to the current metamodel
 * @param createdAt        creation timestamp
 * @param updatedAt        last update timestamp
 */
public record ModelRecord(
        String id,
        String projectId,
        ModelLevel level,
        String name,
        JsonNode modelJson,
        String metamodelVersion,
        String metamodelHash,
        long revision,
        String sourceXmiHash,
        String migrationState,
        Instant createdAt,
        Instant updatedAt) {

}
