package io.mehdieidi.modless.platform.assistant.persistence.jdbc;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.mehdieidi.modless.platform.assistant.persistence.embedding.LocalEmbeddingService;
import io.mehdieidi.modless.platform.assistant.spi.AssistantMetamodelContractStore;
import java.sql.Timestamp;
import java.util.List;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

/** JDBC persistence for Ecore-derived assistant metamodel contracts. */
public class JdbcAssistantMetamodelContractStore implements AssistantMetamodelContractStore {

  private final JdbcTemplate jdbc;
  private final ObjectMapper mapper;
  private final LocalEmbeddingService embeddings;

  public JdbcAssistantMetamodelContractStore(
      JdbcTemplate jdbc, ObjectMapper mapper, LocalEmbeddingService embeddings) {
    this.jdbc = jdbc;
    this.mapper = mapper;
    this.embeddings = embeddings == null ? new LocalEmbeddingService() : embeddings;
  }

  @Override
  public void saveAll(List<MetamodelContractRecord> records) {
    if (jdbc == null || records == null || records.isEmpty()) {
      return;
    }
    for (MetamodelContractRecord record : records) {
      save(record);
    }
  }

  private void save(MetamodelContractRecord record) {
    if (record == null
        || record.id().isBlank()
        || record.level() == null
        || record.metamodelHash().isBlank()
        || record.contractKind().isBlank()
        || record.content() == null) {
      return;
    }
    try {
      String content = mapper.writeValueAsString(record.content());
      jdbc.update(
          """
          INSERT INTO assistant_metamodel_contracts(id, level, metamodel_hash, contract_kind,
            eclass, feature, content_json, embedding, updated_at)
          VALUES (?, ?, ?, ?, ?, ?, ?::jsonb, ?::vector, ?)
          ON CONFLICT (level, metamodel_hash, contract_kind, eclass, feature)
          DO UPDATE SET content_json = EXCLUDED.content_json,
            embedding = EXCLUDED.embedding, updated_at = EXCLUDED.updated_at
          """,
          record.id(),
          record.level().name(),
          record.metamodelHash(),
          record.contractKind(),
          record.eClass(),
          record.feature(),
          content,
          embeddings.vectorLiteral(content),
          Timestamp.from(record.updatedAt()));
    } catch (DataAccessException | com.fasterxml.jackson.core.JsonProcessingException ignored) {
      // Contract cache persistence must not prevent assistant startup.
    }
  }
}
