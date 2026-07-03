package io.mehdieidi.modless.platform.assistant.spi;

import com.fasterxml.jackson.databind.JsonNode;
import io.mehdieidi.modless.platform.kernel.ModelLevel;
import java.time.Instant;
import java.util.List;

/** Durable store for resolved Ecore/metamodel contracts used by assistant retrieval. */
public interface AssistantMetamodelContractStore {

  /** Saves or updates a batch of metamodel contracts. */
  default void saveAll(List<MetamodelContractRecord> records) {}

  /** No-op implementation for tests and non-JDBC deployments. */
  static AssistantMetamodelContractStore noop() {
    return new AssistantMetamodelContractStore() {};
  }

  /** One persisted contract document. */
  record MetamodelContractRecord(
      String id,
      ModelLevel level,
      String metamodelHash,
      String contractKind,
      String eClass,
      String feature,
      JsonNode content,
      Instant updatedAt) {
    public MetamodelContractRecord {
      id = id == null ? "" : id.trim();
      metamodelHash = metamodelHash == null ? "" : metamodelHash.trim();
      contractKind = contractKind == null ? "" : contractKind.trim();
      eClass = eClass == null ? "" : eClass.trim();
      feature = feature == null ? "" : feature.trim();
      updatedAt = updatedAt == null ? Instant.now() : updatedAt;
    }
  }
}
