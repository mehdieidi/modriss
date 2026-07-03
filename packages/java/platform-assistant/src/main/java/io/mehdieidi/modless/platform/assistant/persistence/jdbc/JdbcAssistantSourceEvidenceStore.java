package io.mehdieidi.modless.platform.assistant.persistence.jdbc;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.mehdieidi.modless.platform.assistant.spi.AssistantSourceEvidenceStore;
import java.sql.Timestamp;
import java.time.Instant;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

/** JDBC persistence for source evidence extracted from assistant attachments. */
public class JdbcAssistantSourceEvidenceStore implements AssistantSourceEvidenceStore {

  private final JdbcTemplate jdbc;
  private final ObjectMapper mapper;

  public JdbcAssistantSourceEvidenceStore(JdbcTemplate jdbc, ObjectMapper mapper) {
    this.jdbc = jdbc;
    this.mapper = mapper;
  }

  @Override
  public void save(SourceEvidenceRecord record) {
    if (jdbc == null
        || record == null
        || record.sourceId().isBlank()
        || record.sessionId().isBlank()
        || record.sourceHash().isBlank()
        || record.evidence() == null) {
      return;
    }
    Instant now = record.recordedAt();
    try {
      jdbc.update(
          """
          INSERT INTO assistant_source_evidence(source_id, session_id, model_id, source_hash,
            evidence_json, coverage_json, created_at, updated_at)
          VALUES (?, ?, ?, ?, ?::jsonb, ?::jsonb, ?, ?)
          ON CONFLICT (source_id) DO UPDATE SET model_id = EXCLUDED.model_id,
            source_hash = EXCLUDED.source_hash, evidence_json = EXCLUDED.evidence_json,
            coverage_json = EXCLUDED.coverage_json, updated_at = EXCLUDED.updated_at
          """,
          record.sourceId(),
          record.sessionId(),
          record.modelId().isBlank() ? null : record.modelId(),
          record.sourceHash(),
          mapper.writeValueAsString(record.evidence()),
          mapper.writeValueAsString(
              record.coverage() == null ? mapper.createObjectNode() : record.coverage()),
          Timestamp.from(now),
          Timestamp.from(now));
    } catch (DataAccessException | com.fasterxml.jackson.core.JsonProcessingException ignored) {
      // Evidence persistence must never change assistant turn behavior.
    }
  }
}
