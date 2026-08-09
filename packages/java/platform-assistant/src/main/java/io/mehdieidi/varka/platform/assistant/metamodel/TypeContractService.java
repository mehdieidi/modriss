package io.mehdieidi.varka.platform.assistant.metamodel;

import io.mehdieidi.varka.platform.assistant.metamodel.MetamodelKnowledgeService.TypeContract;
import io.mehdieidi.varka.platform.kernel.ModelLevel;
import io.mehdieidi.varka.platform.kernel.PlatformException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** Resolves live Ecore contracts and produces actionable close-match errors. */
public final class TypeContractService {

  private final MetamodelKnowledgeService knowledge;

  public TypeContractService(MetamodelKnowledgeService knowledge) {
    this.knowledge = java.util.Objects.requireNonNull(knowledge, "knowledge");
  }

  public TypeContract require(ModelLevel level, String requestedName) {
    String name = requestedName == null ? "" : requestedName.trim();
    java.util.Optional<TypeContract> exact =
        knowledge.typeContracts(level).stream()
            .filter(type -> type.eClass().equals(name))
            .findFirst();
    if (exact.isPresent()) return exact.get();
    throw unknown(level, name);
  }

  public List<TypeContract> describe(ModelLevel level, List<String> names) {
    if (names == null || names.isEmpty()) {
      throw new PlatformException(400, "At least one type name is required.");
    }
    return names.stream().distinct().map(name -> require(level, name)).toList();
  }

  /**
   * Returns requested contracts plus the Ecore-derived owner path needed to construct them and
   * their complete required-containment closure.
   *
   * <p>This follows the live Ecore graph and is deliberately independent of prompt wording or
   * DSML-specific names. A provider that requests a nested type receives the shortest valid
   * containment path from the authoritative root as well as every mandatory contained contract.
   */
  public List<TypeContract> requiredContainmentClosure(ModelLevel level, List<String> names) {
    if (names == null || names.isEmpty()) {
      throw new PlatformException(400, "At least one type name is required.");
    }
    java.util.LinkedHashSet<String> pending = new java.util.LinkedHashSet<>();
    for (String name : names) {
      String requested = require(level, name).eClass();
      pending.addAll(constructionPath(level, requested));
    }
    java.util.LinkedHashSet<String> visited = new java.util.LinkedHashSet<>();
    while (!pending.isEmpty()) {
      String typeName = pending.iterator().next();
      pending.remove(typeName);
      if (!visited.add(typeName)) continue;
      require(level, typeName).references().stream()
          .filter(MetamodelKnowledgeService.ReferenceContract::required)
          .filter(MetamodelKnowledgeService.ReferenceContract::containment)
          .map(MetamodelKnowledgeService.ReferenceContract::targetType)
          .map(target -> require(level, target).eClass())
          .filter(target -> !visited.contains(target))
          .forEach(pending::add);
    }
    return visited.stream().map(type -> assistantContract(level, type)).toList();
  }

  private List<String> constructionPath(ModelLevel level, String requestedType) {
    String rootType = knowledge.rootType(level);
    if (rootType.equals(requestedType)) return List.of(rootType);
    ArrayDeque<List<String>> paths = new ArrayDeque<>();
    paths.add(List.of(rootType));
    java.util.LinkedHashSet<String> visited = new java.util.LinkedHashSet<>();
    visited.add(rootType);
    List<TypeContract> candidates =
        all(level).stream()
            .filter(TypeContract::creatable)
            .filter(type -> !rootType.equals(type.eClass()))
            .sorted(Comparator.comparing(TypeContract::eClass))
            .toList();
    while (!paths.isEmpty()) {
      List<String> path = paths.removeFirst();
      TypeContract owner = require(level, path.get(path.size() - 1));
      List<MetamodelKnowledgeService.ReferenceContract> containments =
          owner.references().stream()
              .filter(MetamodelKnowledgeService.ReferenceContract::containment)
              .sorted(Comparator.comparing(MetamodelKnowledgeService.ReferenceContract::name))
              .toList();
      for (MetamodelKnowledgeService.ReferenceContract containment : containments) {
        if (assignable(level, requestedType, containment.targetType())) {
          ArrayList<String> result = new ArrayList<>(path);
          result.add(requestedType);
          return List.copyOf(result);
        }
        for (TypeContract candidate : candidates) {
          if (!assignable(level, candidate.eClass(), containment.targetType())
              || !visited.add(candidate.eClass())) continue;
          ArrayList<String> next = new ArrayList<>(path);
          next.add(candidate.eClass());
          paths.addLast(List.copyOf(next));
        }
      }
    }
    return List.of(requestedType);
  }

  private TypeContract assistantContract(ModelLevel level, String typeName) {
    TypeContract contract = require(level, typeName);
    if (!knowledge.rootType(level).equals(contract.eClass())) return contract;
    return new TypeContract(
        contract.level(),
        contract.eClass(),
        false,
        contract.supertypes(),
        contract.attributes(),
        contract.references());
  }

  public List<TypeContract> all(ModelLevel level) {
    return knowledge.typeContracts(level);
  }

  /** Returns every exact Ecore owner.containment placement that accepts the child type. */
  public List<String> containmentPlacements(ModelLevel level, String childType) {
    String exactChild = require(level, childType).eClass();
    return all(level).stream()
        .filter(TypeContract::creatable)
        .flatMap(
            owner ->
                owner.references().stream()
                    .filter(MetamodelKnowledgeService.ReferenceContract::containment)
                    .filter(reference -> assignable(level, exactChild, reference.targetType()))
                    .map(reference -> owner.eClass() + "." + reference.name()))
        .distinct()
        .sorted()
        .toList();
  }

  public boolean assignable(ModelLevel level, String actualType, String expectedType) {
    if (actualType == null || expectedType == null) return false;
    if (actualType.equals(expectedType)) return true;
    return knowledge
        .index()
        .typeContract(level, actualType)
        .map(type -> type.supertypes().contains(expectedType))
        .orElse(false);
  }

  public List<String> suggestions(ModelLevel level, String requestedName) {
    String requested = requestedName == null ? "" : requestedName.toLowerCase(Locale.ROOT);
    return knowledge.typeContracts(level).stream()
        .map(TypeContract::eClass)
        .sorted(Comparator.comparingInt(name -> distance(requested, name.toLowerCase(Locale.ROOT))))
        .limit(3)
        .toList();
  }

  private PlatformException unknown(ModelLevel level, String name) {
    List<String> close = suggestions(level, name);
    return new PlatformException(
        422,
        "Unknown metamodel type '" + name + "'. Did you mean " + String.join(", ", close) + "?");
  }

  private int distance(String left, String right) {
    int[] previous = new int[right.length() + 1];
    for (int j = 0; j <= right.length(); j++) previous[j] = j;
    for (int i = 1; i <= left.length(); i++) {
      int[] current = new int[right.length() + 1];
      current[0] = i;
      for (int j = 1; j <= right.length(); j++) {
        int cost = left.charAt(i - 1) == right.charAt(j - 1) ? 0 : 1;
        current[j] =
            Math.min(Math.min(current[j - 1] + 1, previous[j] + 1), previous[j - 1] + cost);
      }
      previous = current;
    }
    return previous[right.length()];
  }
}
