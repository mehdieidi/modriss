package io.mehdieidi.modriss.platform.assistant.metamodel;

import io.mehdieidi.modriss.platform.assistant.metamodel.MetamodelKnowledgeService.AttributeContract;
import io.mehdieidi.modriss.platform.assistant.metamodel.MetamodelKnowledgeService.ReferenceContract;
import io.mehdieidi.modriss.platform.assistant.metamodel.MetamodelKnowledgeService.TypeContract;
import io.mehdieidi.modriss.platform.assistant.patch.AssistantMetamodelSchemaService;
import io.mehdieidi.modriss.platform.kernel.ModelLevel;
import io.mehdieidi.modriss.platform.modeling.config.ModelingConfigService;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Extracts resolved, inherited, task-friendly contracts directly from combined Ecore resources. */
public class EcoreContractExtractor {

  private final ModelingConfigService modelingConfig;

  public EcoreContractExtractor(ModelingConfigService modelingConfig) {
    this.modelingConfig = modelingConfig == null ? new ModelingConfigService() : modelingConfig;
  }

  public MetamodelKnowledgeIndex extract() {
    return extract(null);
  }

  /** Extracts contracts, optionally projecting them through an assistant-facing schema. */
  public MetamodelKnowledgeIndex extract(AssistantMetamodelSchemaService schemas) {
    Map<ModelLevel, List<TypeContract>> byLevel = new EnumMap<>(ModelLevel.class);
    List<MetamodelContractRecord> records = new ArrayList<>();
    for (ModelLevel level : ModelLevel.values()) {
      List<TypeContract> types =
          modelingConfig.ecoreDerivedElements(level).stream()
              .map(raw -> typeContract(level, raw))
              .filter(
                  type ->
                      schemas == null || schemas.tryCanonicalType(level, type.eClass()).isPresent())
              .map(type -> projectReferences(level, type, schemas))
              .toList();
      byLevel.put(level, types);
      records.add(levelOverview(level, types));
      for (TypeContract type : types) {
        records.add(typeRecord(type));
        type.attributes().forEach(attribute -> records.add(attributeRecord(type, attribute)));
        type.references().forEach(reference -> records.add(referenceRecord(type, reference)));
      }
    }
    return new MetamodelKnowledgeIndex(byLevel, records);
  }

  private TypeContract projectReferences(
      ModelLevel level, TypeContract type, AssistantMetamodelSchemaService schemas) {
    if (schemas == null) {
      return type;
    }
    List<ReferenceContract> references =
        type.references().stream()
            .filter(
                reference -> schemas.reference(level, type.eClass(), reference.name()).isPresent())
            .toList();
    return new TypeContract(
        type.level(),
        type.eClass(),
        type.creatable(),
        type.supertypes(),
        type.attributes(),
        references);
  }

  @SuppressWarnings("unchecked")
  private TypeContract typeContract(ModelLevel level, Map<String, Object> raw) {
    String typeName = text(raw, "type");
    List<AttributeContract> attributes = new ArrayList<>();
    for (Object value : (List<?>) raw.getOrDefault("attributes", List.of())) {
      if (!(value instanceof Map<?, ?> attribute)) {
        continue;
      }
      Map<String, Object> map = (Map<String, Object>) attribute;
      attributes.add(
          new AttributeContract(
              text(map, "name"),
              text(map, "type"),
              Boolean.TRUE.equals(map.get("required")),
              Boolean.TRUE.equals(map.get("many")),
              strings(map.get("options"))));
    }
    List<ReferenceContract> references = new ArrayList<>();
    for (Object value : (List<?>) raw.getOrDefault("references", List.of())) {
      if (!(value instanceof Map<?, ?> reference)) {
        continue;
      }
      Map<String, Object> map = (Map<String, Object>) reference;
      references.add(
          new ReferenceContract(
              text(map, "name"),
              text(map, "targetType"),
              Boolean.TRUE.equals(map.get("required")),
              Boolean.TRUE.equals(map.get("many")),
              Boolean.TRUE.equals(map.get("containment")),
              Boolean.TRUE.equals(map.get("readonly"))));
    }
    return new TypeContract(
        level,
        typeName,
        Boolean.TRUE.equals(raw.get("creatable")),
        strings(raw.get("supertypes")),
        attributes,
        references);
  }

  private String text(Map<String, Object> map, String key) {
    Object value = map.get(key);
    return value == null ? "" : String.valueOf(value).trim();
  }

  private List<String> strings(Object value) {
    if (!(value instanceof List<?> list)) {
      return List.of();
    }
    return list.stream()
        .map(item -> String.valueOf(item).trim())
        .filter(item -> !item.isBlank())
        .toList();
  }

  private MetamodelContractRecord levelOverview(ModelLevel level, List<TypeContract> types) {
    return new MetamodelContractRecord(
        level,
        "level-overview",
        "",
        "",
        level.name() + " metamodel overview",
        "Creatable EClasses: "
            + types.stream().map(TypeContract::eClass).collect(Collectors.joining(", ")),
        types.stream().map(TypeContract::eClass).toList());
  }

  private MetamodelContractRecord typeRecord(TypeContract type) {
    String references =
        type.references().stream()
            .map(reference -> reference.name() + " -> " + reference.targetType())
            .collect(Collectors.joining(", "));
    String attributes =
        type.attributes().stream()
            .map(attribute -> attribute.name() + ":" + attribute.type())
            .collect(Collectors.joining(", "));
    return new MetamodelContractRecord(
        type.level(),
        "type-contract",
        type.eClass(),
        "",
        type.eClass() + " EClass contract",
        "EClass "
            + type.eClass()
            + " creatable="
            + type.creatable()
            + "; supertypes="
            + type.supertypes()
            + "; attributes="
            + attributes
            + "; references="
            + references,
        type.references().stream().map(ReferenceContract::targetType).distinct().toList());
  }

  private MetamodelContractRecord attributeRecord(TypeContract type, AttributeContract attribute) {
    return new MetamodelContractRecord(
        type.level(),
        "attribute-contract",
        type.eClass(),
        attribute.name(),
        type.eClass() + "." + attribute.name() + " attribute",
        "EAttribute "
            + type.eClass()
            + "."
            + attribute.name()
            + " type="
            + attribute.type()
            + " required="
            + attribute.required()
            + " enumLiterals="
            + attribute.enumLiterals(),
        List.of(type.eClass()));
  }

  private MetamodelContractRecord referenceRecord(TypeContract type, ReferenceContract reference) {
    return new MetamodelContractRecord(
        type.level(),
        reference.containment() ? "containment-recipe" : "relationship-recipe",
        type.eClass(),
        reference.name(),
        type.eClass() + "." + reference.name() + " reference",
        "EReference "
            + type.eClass()
            + "."
            + reference.name()
            + " target="
            + reference.targetType()
            + " required="
            + reference.required()
            + " many="
            + reference.many()
            + " containment="
            + reference.containment()
            + " readonly="
            + reference.readonly(),
        List.of(type.eClass(), reference.targetType()));
  }
}
