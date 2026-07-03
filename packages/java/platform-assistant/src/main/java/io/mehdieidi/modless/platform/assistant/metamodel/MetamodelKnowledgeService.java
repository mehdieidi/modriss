package io.mehdieidi.modless.platform.assistant.metamodel;

import io.mehdieidi.modless.platform.assistant.patch.AssistantMetamodelSchemaService;
import io.mehdieidi.modless.platform.assistant.provider.AssistantModelProvider;
import io.mehdieidi.modless.platform.kernel.ModelLevel;
import java.util.ArrayList;
import java.util.List;

/** Ecore-derived knowledge service used by the unified modeling agent path. */
public class MetamodelKnowledgeService {

  private final AssistantMetamodelSchemaService schemas;
  private final MetamodelKnowledgeIndex index;

  public MetamodelKnowledgeService(AssistantMetamodelSchemaService schemas) {
    this.schemas = schemas == null ? new AssistantMetamodelSchemaService() : schemas;
    this.index = new EcoreContractExtractor(this.schemas).extract();
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

  /** Returns exact type-contract snippets for a candidate set plus their required containments. */
  public List<AssistantModelProvider.ContextSnippet> contractClosure(
      ModelLevel level, List<String> candidateTypes) {
    List<AssistantModelProvider.ContextSnippet> result = new ArrayList<>();
    for (String type : candidateTypes == null ? List.<String>of() : candidateTypes) {
      result.add(schemas.typeContract(level, type));
      schemas.typeSchema(level, type).stream()
          .flatMap(schema -> schema.references().stream())
          .filter(AssistantMetamodelSchemaService.ReferenceSchema::required)
          .filter(AssistantMetamodelSchemaService.ReferenceSchema::containment)
          .forEach(reference -> result.add(schemas.typeContract(level, reference.targetType())));
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
