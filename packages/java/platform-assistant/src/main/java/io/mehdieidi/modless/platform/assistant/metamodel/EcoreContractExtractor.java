package io.mehdieidi.modless.platform.assistant.metamodel;

import io.mehdieidi.modless.platform.assistant.metamodel.MetamodelKnowledgeService.AttributeContract;
import io.mehdieidi.modless.platform.assistant.metamodel.MetamodelKnowledgeService.ReferenceContract;
import io.mehdieidi.modless.platform.assistant.metamodel.MetamodelKnowledgeService.TypeContract;
import io.mehdieidi.modless.platform.assistant.patch.AssistantMetamodelSchemaService;
import io.mehdieidi.modless.platform.kernel.ModelLevel;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Extracts resolved, inherited, task-friendly contracts from the Ecore-backed schema service. */
public class EcoreContractExtractor {

  private final AssistantMetamodelSchemaService schemas;

  public EcoreContractExtractor(AssistantMetamodelSchemaService schemas) {
    this.schemas = schemas == null ? new AssistantMetamodelSchemaService() : schemas;
  }

  public MetamodelKnowledgeIndex extract() {
    Map<ModelLevel, List<TypeContract>> byLevel = new EnumMap<>(ModelLevel.class);
    List<MetamodelContractRecord> records = new ArrayList<>();
    for (ModelLevel level : ModelLevel.values()) {
      List<TypeContract> types =
          schemas.coverage(level).typeNames().stream()
              .map(type -> typeContract(level, type))
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

  private TypeContract typeContract(ModelLevel level, String typeName) {
    AssistantMetamodelSchemaService.TypeSchema type =
        schemas
            .typeSchema(level, typeName)
            .orElseThrow(
                () ->
                    new io.mehdieidi.modless.platform.kernel.PlatformException(
                        422, "Unknown metamodel type: " + typeName));
    List<AttributeContract> attributes =
        type.attributes().stream()
            .map(
                attribute ->
                    new AttributeContract(
                        attribute.name(),
                        attribute.type(),
                        attribute.required(),
                        attribute.options()))
            .toList();
    List<ReferenceContract> references =
        type.references().stream()
            .map(
                reference ->
                    new ReferenceContract(
                        reference.name(),
                        reference.targetType(),
                        reference.required(),
                        reference.many(),
                        reference.containment(),
                        reference.readonly()))
            .toList();
    return new TypeContract(
        level, type.name(), type.creatable(), type.supertypes(), attributes, references);
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
