package io.mehdieidi.modless.platform.assistant.metamodel;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.mehdieidi.modless.platform.assistant.spi.AssistantMetamodelContractStore;
import io.mehdieidi.modless.platform.assistant.spi.AssistantMetamodelContractStore.MetamodelContractRecord;
import io.mehdieidi.modless.platform.kernel.ModelLevel;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

/** Builds and persists Ecore-derived metamodel contracts for assistant retrieval. */
public class MetamodelContractIndexService {

  private final MetamodelKnowledgeService metamodels;
  private final AssistantMetamodelContractStore store;
  private final ObjectMapper mapper;

  public MetamodelContractIndexService(
      MetamodelKnowledgeService metamodels,
      AssistantMetamodelContractStore store,
      ObjectMapper mapper) {
    this.metamodels = metamodels;
    this.store = store == null ? AssistantMetamodelContractStore.noop() : store;
    this.mapper = mapper == null ? new ObjectMapper() : mapper;
  }

  /** Rebuilds persisted contract records for every modeling level. */
  public int refreshAll() {
    List<MetamodelContractRecord> records = new ArrayList<>();
    Instant now = Instant.now();
    for (ModelLevel level : ModelLevel.values()) {
      records.addAll(recordsFor(level, now));
    }
    store.saveAll(records);
    return records.size();
  }

  /** Creates retrieval records for one level without persisting them. */
  public List<MetamodelContractRecord> recordsFor(ModelLevel level, Instant updatedAt) {
    List<MetamodelKnowledgeService.TypeContract> types = metamodels.typeContracts(level);
    String metamodelHash = hash(json(types));
    List<MetamodelContractRecord> records = new ArrayList<>();
    records.add(
        record(
            level,
            metamodelHash,
            "level-overview",
            "",
            "",
            levelOverview(level, metamodelHash, types),
            updatedAt));
    for (MetamodelKnowledgeService.TypeContract type : types) {
      records.add(
          record(
              level,
              metamodelHash,
              "type-contract",
              type.eClass(),
              "",
              mapper.valueToTree(type),
              updatedAt));
      for (MetamodelKnowledgeService.AttributeContract attribute : type.attributes()) {
        records.add(
            record(
                level,
                metamodelHash,
                "attribute-contract",
                type.eClass(),
                attribute.name(),
                featureContract(level, type.eClass(), "attribute", attribute.name(), attribute),
                updatedAt));
      }
      for (MetamodelKnowledgeService.ReferenceContract reference : type.references()) {
        records.add(
            record(
                level,
                metamodelHash,
                "reference-contract",
                type.eClass(),
                reference.name(),
                featureContract(level, type.eClass(), "reference", reference.name(), reference),
                updatedAt));
      }
    }
    return records;
  }

  private MetamodelContractRecord record(
      ModelLevel level,
      String metamodelHash,
      String kind,
      String eClass,
      String feature,
      JsonNode content,
      Instant updatedAt) {
    String id = stableId(level, metamodelHash, kind, eClass, feature);
    return new MetamodelContractRecord(
        id, level, metamodelHash, kind, eClass, feature, content, updatedAt);
  }

  private JsonNode levelOverview(
      ModelLevel level, String metamodelHash, List<MetamodelKnowledgeService.TypeContract> types) {
    ObjectNode node = mapper.createObjectNode();
    node.put("level", level.name());
    node.put("metamodelHash", metamodelHash);
    node.put("typeCount", types.size());
    node.set(
        "types",
        mapper.valueToTree(
            types.stream().map(MetamodelKnowledgeService.TypeContract::eClass).toList()));
    return node;
  }

  private JsonNode featureContract(
      ModelLevel level, String owner, String featureKind, String featureName, Object details) {
    ObjectNode node = mapper.createObjectNode();
    node.put("level", level.name());
    node.put("owner", owner);
    node.put("featureKind", featureKind);
    node.put("feature", featureName);
    node.set("details", mapper.valueToTree(details));
    return node;
  }

  private String stableId(
      ModelLevel level, String metamodelHash, String kind, String eClass, String feature) {
    return level.name()
        + ":"
        + hash(
            level.name()
                + "|"
                + metamodelHash
                + "|"
                + kind
                + "|"
                + safe(eClass)
                + "|"
                + safe(feature));
  }

  private byte[] json(Object value) {
    try {
      return mapper.writeValueAsBytes(value);
    } catch (JsonProcessingException ex) {
      return String.valueOf(value).getBytes(StandardCharsets.UTF_8);
    }
  }

  private String hash(byte[] bytes) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    } catch (Exception ex) {
      return Integer.toHexString(java.util.Arrays.hashCode(bytes));
    }
  }

  private String hash(String value) {
    return hash(value.getBytes(StandardCharsets.UTF_8));
  }

  private String safe(String value) {
    return value == null ? "" : value.trim();
  }
}
