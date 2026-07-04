package io.mehdieidi.modless.platform.assistant.metamodel;

import io.mehdieidi.modless.platform.assistant.patch.AssistantMetamodelSchemaService;
import io.mehdieidi.modless.platform.kernel.ModelLevel;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Compares direct combined-Ecore contracts with the runtime schema service. */
public final class MetamodelDriftReport {

  private MetamodelDriftReport() {}

  /** Returns drift entries where runtime creatable contracts are not grounded in combined Ecore. */
  public static List<String> drift(
      MetamodelKnowledgeIndex ecoreIndex, AssistantMetamodelSchemaService runtimeSchemas) {
    List<String> issues = new ArrayList<>();
    MetamodelKnowledgeService knowledge = new MetamodelKnowledgeService(runtimeSchemas);
    for (ModelLevel level : ModelLevel.values()) {
      for (String runtimeType : runtimeSchemas.coverage(level).typeNames()) {
        Optional<MetamodelKnowledgeService.TypeContract> ecoreType =
            ecoreIndex.typeContract(level, runtimeType);
        if (ecoreType.isEmpty()) {
          issues.add(
              level + ": runtime creatable type missing from combined Ecore: " + runtimeType);
          continue;
        }
        runtimeSchemas
            .typeSchema(level, runtimeType)
            .ifPresent(
                runtime ->
                    runtime.attributes().stream()
                        .filter(AssistantMetamodelSchemaService.AttributeSchema::required)
                        .forEach(
                            attribute -> {
                              boolean present =
                                  ecoreType.get().attributes().stream()
                                      .anyMatch(
                                          item ->
                                              item.name().equals(attribute.name())
                                                  && item.required());
                              if (!present) {
                                issues.add(
                                    level
                                        + ": "
                                        + runtimeType
                                        + " missing required Ecore attribute "
                                        + attribute.name());
                              }
                              Optional<MetamodelKnowledgeService.AttributeContract> ecoreAttr =
                                  ecoreType.get().attributes().stream()
                                      .filter(item -> item.name().equals(attribute.name()))
                                      .findFirst();
                              if (ecoreAttr.isPresent()
                                  && !attribute.options().isEmpty()
                                  && !ecoreAttr
                                      .get()
                                      .enumLiterals()
                                      .containsAll(attribute.options())) {
                                issues.add(
                                    level
                                        + ": "
                                        + runtimeType
                                        + " enum literals drift for attribute "
                                        + attribute.name());
                              }
                            }));
        runtimeSchemas
            .typeSchema(level, runtimeType)
            .ifPresent(
                runtime ->
                    runtime.references().stream()
                        .filter(AssistantMetamodelSchemaService.ReferenceSchema::containment)
                        .forEach(
                            reference -> {
                              boolean present =
                                  ecoreType.get().references().stream()
                                      .anyMatch(
                                          item ->
                                              item.name().equals(reference.name())
                                                  && item.containment());
                              if (!present) {
                                issues.add(
                                    level
                                        + ": "
                                        + runtimeType
                                        + " missing Ecore containment "
                                        + reference.name());
                              }
                            }));
        if (knowledge.rootContainment(level, runtimeType).isEmpty()
            && runtimeSchemas.rootContainment(level, runtimeType).isPresent()) {
          issues.add(
              level + ": root containment for " + runtimeType + " missing from combined Ecore");
        }
      }
    }
    return List.copyOf(issues);
  }
}
