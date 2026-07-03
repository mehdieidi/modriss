package io.mehdieidi.modless.platform.assistant.spi;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;

/** Durable store for source evidence graphs and coverage derived from untrusted attachments. */
public interface AssistantSourceEvidenceStore {

  /** Saves or updates one source evidence record. */
  default void save(SourceEvidenceRecord record) {}

  /** No-op implementation for tests and non-JDBC deployments. */
  static AssistantSourceEvidenceStore noop() {
    return new AssistantSourceEvidenceStore() {};
  }

  /** One persisted source evidence snapshot. */
  record SourceEvidenceRecord(
      String sourceId,
      String sessionId,
      String modelId,
      String sourceHash,
      JsonNode evidence,
      JsonNode coverage,
      Instant recordedAt) {
    public SourceEvidenceRecord {
      sourceId = sourceId == null ? "" : sourceId.trim();
      sessionId = sessionId == null ? "" : sessionId.trim();
      modelId = modelId == null ? "" : modelId.trim();
      sourceHash = sourceHash == null ? "" : sourceHash.trim();
      recordedAt = recordedAt == null ? Instant.now() : recordedAt;
    }
  }
}
