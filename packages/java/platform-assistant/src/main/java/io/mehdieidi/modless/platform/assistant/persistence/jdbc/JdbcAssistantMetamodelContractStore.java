package io.mehdieidi.modless.platform.assistant.persistence.jdbc;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.mehdieidi.modless.platform.assistant.persistence.embedding.LocalEmbeddingService;
import io.mehdieidi.modless.platform.assistant.spi.AssistantMetamodelContractStore;
import io.mehdieidi.modless.platform.kernel.ModelLevel;
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

  @Override
  public List<ContractSearchHit> search(ModelLevel level, String query, int limit) {
    String normalized = query == null ? "" : query.trim();
    if (jdbc == null || level == null || normalized.isBlank() || limit <= 0) {
      return List.of();
    }
    String tsQuery = toTsQuery(normalized);
    if (tsQuery.isBlank()) {
      return List.of();
    }
    String queryVector = embeddings.vectorLiteral(normalized);
    return jdbc.query(
        """
        SELECT contract_kind, eclass, feature, content_json
        FROM assistant_metamodel_contracts
        WHERE level = ?
        ORDER BY
          CASE
            WHEN to_tsvector('simple', eclass || ' ' || feature || ' ' || content_json::text)
              @@ to_tsquery('simple', ?)
            THEN ts_rank(
              to_tsvector('simple', eclass || ' ' || feature || ' ' || content_json::text),
              to_tsquery('simple', ?))
            ELSE 0
          END DESC,
          embedding <=> ?::vector ASC,
          updated_at DESC
        LIMIT ?
        """,
        (rs, rowNum) ->
            new ContractSearchHit(
                rs.getString("contract_kind"),
                rs.getString("eclass"),
                rs.getString("feature"),
                title(
                    rs.getString("contract_kind"), rs.getString("eclass"), rs.getString("feature")),
                rs.getString("content_json")),
        level.name(),
        tsQuery,
        tsQuery,
        queryVector,
        limit);
  }

  private String title(String kind, String eClass, String feature) {
    if (feature == null || feature.isBlank()) {
      return eClass == null || eClass.isBlank() ? kind : eClass + " " + kind;
    }
    return eClass + "." + feature;
  }

  private String toTsQuery(String query) {
    String[] terms = query.toLowerCase(java.util.Locale.ROOT).split("[^a-z0-9]+");
    StringBuilder builder = new StringBuilder();
    for (String term : terms) {
      if (term.length() < 2) {
        continue;
      }
      if (!builder.isEmpty()) {
        builder.append(" | ");
      }
      builder.append(term).append(":*");
    }
    return builder.toString();
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
