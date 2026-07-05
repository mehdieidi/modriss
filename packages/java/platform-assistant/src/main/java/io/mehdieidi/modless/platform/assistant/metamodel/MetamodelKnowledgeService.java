package io.mehdieidi.modless.platform.assistant.metamodel;

import io.mehdieidi.modless.platform.assistant.patch.AssistantMetamodelSchemaService;
import io.mehdieidi.modless.platform.assistant.provider.AssistantModelProvider;
import io.mehdieidi.modless.platform.kernel.ModelLevel;
import io.mehdieidi.modless.platform.modeling.config.ModelingConfigService;
import java.util.ArrayList;
import java.util.List;

/** Ecore-derived knowledge service used by the unified modeling agent path. */
public class MetamodelKnowledgeService {

  private final AssistantMetamodelSchemaService schemas;
  private final MetamodelKnowledgeIndex index;

  public MetamodelKnowledgeService(AssistantMetamodelSchemaService schemas) {
    this(schemas, new ModelingConfigService());
  }

  public MetamodelKnowledgeService(
      AssistantMetamodelSchemaService schemas, ModelingConfigService modelingConfig) {
    this.schemas = schemas == null ? new AssistantMetamodelSchemaService() : schemas;
    this.index = new EcoreContractExtractor(modelingConfig).extract();
  }

  /** Returns all creatable type contracts for a level. */
  public List<TypeContract> typeContracts(ModelLevel level) {
    return index.typeContracts(level);
  }

  /** Returns one resolved type contract. */
  public TypeContract typeContract(ModelLevel level, String typeName) {
    return index
        .typeContract(level, typeName)
        .orElseThrow(
            () ->
                new io.mehdieidi.modless.platform.kernel.PlatformException(
                    422, "Unknown metamodel type: " + typeName));
  }

  /** Returns the extracted knowledge index for retrieval-document generation. */
  public MetamodelKnowledgeIndex index() {
    return index;
  }

  /** Returns the canonical root EClass for a modeling level. */
  public String rootType(ModelLevel level) {
    return schemas.rootType(level);
  }

  /** Returns the canonical case-sensitive EClass name from combined Ecore contracts. */
  public String canonicalType(ModelLevel level, String type) {
    return index
        .typeContract(level, type)
        .map(TypeContract::eClass)
        .orElseGet(() -> schemas.canonicalType(level, type));
  }

  /** Finds the root containment reference for a top-level creatable element. */
  public java.util.Optional<ReferenceContract> rootContainment(
      ModelLevel level, String elementType) {
    String canonical = canonicalType(level, elementType);
    return index
        .typeContract(level, rootType(level))
        .flatMap(
            root ->
                root.references().stream()
                    .filter(ReferenceContract::containment)
                    .filter(reference -> assignable(level, canonical, reference.targetType()))
                    .sorted(
                        java.util.Comparator.comparing(ReferenceContract::many)
                            .reversed()
                            .thenComparing(
                                reference ->
                                    reference.targetType().equalsIgnoreCase(canonical) ? 0 : 1))
                    .findFirst());
  }

  /** Stable hash for the combined Ecore contract set at a level. */
  public String metamodelHash(ModelLevel level) {
    return Integer.toHexString(
        typeContracts(level).stream().map(TypeContract::eClass).sorted().toList().hashCode());
  }

  private boolean assignable(ModelLevel level, String childType, String targetType) {
    if (childType == null || targetType == null) {
      return false;
    }
    if (childType.equalsIgnoreCase(targetType)) {
      return true;
    }
    return index
        .typeContract(level, childType)
        .map(
            contract ->
                contract.supertypes().stream()
                    .anyMatch(supertype -> supertype.equalsIgnoreCase(targetType)))
        .orElse(false);
  }

  /** Returns exact type-contract snippets for a candidate set plus their required containments. */
  public List<AssistantModelProvider.ContextSnippet> contractClosure(
      ModelLevel level, List<String> candidateTypes) {
    List<AssistantModelProvider.ContextSnippet> result = new ArrayList<>();
    for (String type : schemas.knownTypes(level, candidateTypes)) {
      result.add(typeContractSnippet(level, type));
      typeContract(level, type).references().stream()
          .filter(MetamodelKnowledgeService.ReferenceContract::required)
          .filter(MetamodelKnowledgeService.ReferenceContract::containment)
          .forEach(reference -> result.add(typeContractSnippet(level, reference.targetType())));
    }
    return result.stream()
        .collect(
            java.util.stream.Collectors.toMap(
                AssistantModelProvider.ContextSnippet::title,
                snippet -> snippet,
                (left, ignored) -> left,
                java.util.LinkedHashMap::new))
        .values()
        .stream()
        .toList();
  }

  private AssistantModelProvider.ContextSnippet typeContractSnippet(
      ModelLevel level, String typeName) {
    TypeContract type = typeContract(level, typeName);
    String attributes =
        type.attributes().stream()
            .map(attribute -> attribute.name() + ":" + attribute.type())
            .collect(java.util.stream.Collectors.joining(", "));
    String references =
        type.references().stream()
            .map(reference -> reference.name() + " -> " + reference.targetType())
            .collect(java.util.stream.Collectors.joining(", "));
    return new AssistantModelProvider.ContextSnippet(
        "metamodel-contract",
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
            + references);
  }

  /** One EClass contract. */
  public record TypeContract(
      ModelLevel level,
      String eClass,
      boolean creatable,
      List<String> supertypes,
      List<AttributeContract> attributes,
      List<ReferenceContract> references) {}

  /** One EAttribute contract. */
  public record AttributeContract(
      String name, String type, boolean required, List<String> enumLiterals) {}

  /** One EReference contract. */
  public record ReferenceContract(
      String name,
      String targetType,
      boolean required,
      boolean many,
      boolean containment,
      boolean readonly) {}
}
