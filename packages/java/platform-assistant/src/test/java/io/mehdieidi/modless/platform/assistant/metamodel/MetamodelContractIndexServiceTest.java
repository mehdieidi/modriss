package io.mehdieidi.modless.platform.assistant.metamodel;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.mehdieidi.modless.platform.assistant.patch.AssistantMetamodelSchemaService;
import io.mehdieidi.modless.platform.assistant.spi.AssistantMetamodelContractStore;
import io.mehdieidi.modless.platform.assistant.spi.AssistantMetamodelContractStore.MetamodelContractRecord;
import io.mehdieidi.modless.platform.kernel.ModelLevel;
import io.mehdieidi.modless.platform.modeling.config.ModelingConfigService;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class MetamodelContractIndexServiceTest {

  private final ObjectMapper mapper = new ObjectMapper();
  private final MetamodelKnowledgeService metamodels =
      new MetamodelKnowledgeService(new AssistantMetamodelSchemaService());

  @Test
  void buildsLevelTypeAndFeatureContractsFromEcoreKnowledge() {
    MetamodelContractIndexService index =
        new MetamodelContractIndexService(
            metamodels, AssistantMetamodelContractStore.noop(), mapper);

    List<MetamodelContractRecord> records = index.recordsFor(ModelLevel.PIM, Instant.EPOCH);

    assertTrue(records.stream().anyMatch(record -> "level-overview".equals(record.contractKind())));
    assertTrue(
        records.stream()
            .anyMatch(
                record ->
                    "type-contract".equals(record.contractKind())
                        && "Function".equals(record.eClass())));
    assertTrue(
        records.stream().anyMatch(record -> "attribute-contract".equals(record.contractKind())));
    assertTrue(
        records.stream().anyMatch(record -> "reference-contract".equals(record.contractKind())));
    assertTrue(records.stream().allMatch(record -> !record.id().isBlank()));
    assertTrue(records.stream().allMatch(record -> !record.metamodelHash().isBlank()));
    assertTrue(records.stream().allMatch(record -> record.content() != null));
  }

  @Test
  void extractorBuildsQueryableIndexWithContainmentAndEnumDocuments() {
    MetamodelKnowledgeIndex index =
        new EcoreContractExtractor(new ModelingConfigService()).extract();

    assertTrue(index.typeContract(ModelLevel.PIM, "Function").isPresent());
    assertTrue(index.typeContract(ModelLevel.CIM, "Actor").isPresent());
    assertTrue(index.typeContract(ModelLevel.PSM, "AwsLambdaFunction").isPresent());
    assertTrue(
        index.records(ModelLevel.PIM).stream()
            .anyMatch(record -> "containment-recipe".equals(record.kind())));
    assertTrue(
        index.records().stream()
            .anyMatch(
                record ->
                    record.content().contains("enumLiterals=[")
                        && !record.content().endsWith("[]")));
  }

  @Test
  void refreshAllPersistsEveryLevelThroughStorePort() {
    CapturingStore store = new CapturingStore();
    MetamodelContractIndexService index =
        new MetamodelContractIndexService(metamodels, store, mapper);

    int count = index.refreshAll();

    assertFalse(store.records.isEmpty());
    assertTrue(count == store.records.size());
    assertTrue(store.records.stream().anyMatch(record -> record.level() == ModelLevel.CIM));
    assertTrue(store.records.stream().anyMatch(record -> record.level() == ModelLevel.PIM));
    assertTrue(store.records.stream().anyMatch(record -> record.level() == ModelLevel.PSM));
  }

  private static final class CapturingStore implements AssistantMetamodelContractStore {
    private final List<MetamodelContractRecord> records = new ArrayList<>();

    @Override
    public void saveAll(List<MetamodelContractRecord> records) {
      this.records.addAll(records);
    }
  }
}
