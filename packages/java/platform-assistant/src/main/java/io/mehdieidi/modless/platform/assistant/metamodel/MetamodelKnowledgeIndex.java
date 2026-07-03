package io.mehdieidi.modless.platform.assistant.metamodel;

import io.mehdieidi.modless.platform.assistant.metamodel.MetamodelKnowledgeService.TypeContract;
import io.mehdieidi.modless.platform.kernel.ModelLevel;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Immutable in-memory index of resolved Ecore contracts by modeling level and EClass. */
public class MetamodelKnowledgeIndex {

  private final Map<ModelLevel, List<TypeContract>> byLevel;
  private final Map<ModelLevel, Map<String, TypeContract>> byType;
  private final List<MetamodelContractRecord> records;

  public MetamodelKnowledgeIndex(
      Map<ModelLevel, List<TypeContract>> byLevel, List<MetamodelContractRecord> records) {
    this.byLevel = copyByLevel(byLevel);
    this.byType = byType(this.byLevel);
    this.records = records == null ? List.of() : List.copyOf(records);
  }

  public List<TypeContract> typeContracts(ModelLevel level) {
    return byLevel.getOrDefault(level, List.of());
  }

  public Optional<TypeContract> typeContract(ModelLevel level, String eClass) {
    if (level == null || eClass == null) {
      return Optional.empty();
    }
    return Optional.ofNullable(byType.getOrDefault(level, Map.of()).get(eClass.trim()));
  }

  public List<MetamodelContractRecord> records(ModelLevel level) {
    return records.stream().filter(record -> record.level() == level).toList();
  }

  public List<MetamodelContractRecord> records() {
    return records;
  }

  private Map<ModelLevel, List<TypeContract>> copyByLevel(
      Map<ModelLevel, List<TypeContract>> input) {
    Map<ModelLevel, List<TypeContract>> copy = new EnumMap<>(ModelLevel.class);
    if (input != null) {
      input.forEach((level, contracts) -> copy.put(level, List.copyOf(contracts)));
    }
    return Map.copyOf(copy);
  }

  private Map<ModelLevel, Map<String, TypeContract>> byType(
      Map<ModelLevel, List<TypeContract>> contracts) {
    Map<ModelLevel, Map<String, TypeContract>> result = new EnumMap<>(ModelLevel.class);
    contracts.forEach(
        (level, types) -> {
          Map<String, TypeContract> levelTypes = new LinkedHashMap<>();
          for (TypeContract type : types) {
            levelTypes.put(type.eClass(), type);
          }
          result.put(level, Map.copyOf(levelTypes));
        });
    return Map.copyOf(result);
  }
}
