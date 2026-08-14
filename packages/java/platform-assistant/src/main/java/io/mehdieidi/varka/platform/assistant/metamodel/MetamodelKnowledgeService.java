package io.mehdieidi.varka.platform.assistant.metamodel;

import io.mehdieidi.varka.platform.assistant.patch.AssistantMetamodelSchemaService;
import io.mehdieidi.varka.platform.assistant.provider.AssistantModelProvider;
import io.mehdieidi.varka.platform.kernel.ModelLevel;
import io.mehdieidi.varka.platform.modeling.config.ModelingConfigService;
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
    EcoreContractExtractor extractor = new EcoreContractExtractor(modelingConfig);
    this.index =
        this.schemas.profile().mode() == AssistantMetamodelMode.NORMAL
            ? extractor.extract()
            : extractor.extract(this.schemas);
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
                new io.mehdieidi.varka.platform.kernel.PlatformException(
                    422, "Unknown metamodel type: " + typeName));
  }

  /** Returns the deterministic Ecore knowledge index used by the agent guide and tools. */
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
    java.util.LinkedHashSet<String> pending =
        new java.util.LinkedHashSet<>(schemas.knownTypes(level, candidateTypes));
    java.util.LinkedHashSet<String> visited = new java.util.LinkedHashSet<>();
    while (!pending.isEmpty()) {
      String type = pending.iterator().next();
      pending.remove(type);
      if (!visited.add(type)) continue;
      typeContract(level, type).references().stream()
          .filter(ReferenceContract::required)
          .filter(ReferenceContract::containment)
          .map(ReferenceContract::targetType)
          .filter(target -> !visited.contains(target))
          .forEach(pending::add);
    }
    return visited.stream().map(type -> typeContractSnippet(level, type)).toList();
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
      String name, String type, boolean required, boolean many, List<String> enumLiterals) {
    public AttributeContract(
        String name, String type, boolean required, List<String> enumLiterals) {
      this(name, type, required, false, enumLiterals);
    }
  }

  /** One EReference contract. */
  public record ReferenceContract(
      String name,
      String targetType,
      boolean required,
      boolean many,
      boolean containment,
      boolean readonly) {}
}
