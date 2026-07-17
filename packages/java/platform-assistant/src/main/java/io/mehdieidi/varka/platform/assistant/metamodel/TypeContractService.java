package io.mehdieidi.varka.platform.assistant.metamodel;

import io.mehdieidi.varka.platform.assistant.metamodel.MetamodelKnowledgeService.TypeContract;
import io.mehdieidi.varka.platform.kernel.ModelLevel;
import io.mehdieidi.varka.platform.kernel.PlatformException;
import java.util.Comparator;
import java.util.List;

/** Resolves live Ecore contracts and produces actionable close-match errors. */
public final class TypeContractService {

  private final MetamodelKnowledgeService knowledge;

  public TypeContractService(MetamodelKnowledgeService knowledge) {
    this.knowledge = java.util.Objects.requireNonNull(knowledge, "knowledge");
  }

  public TypeContract require(ModelLevel level, String requestedName) {
    String name = requestedName == null ? "" : requestedName.trim();
    return knowledge.typeContracts(level).stream()
        .filter(type -> type.eClass().equals(name))
        .findFirst()
        .orElseThrow(() -> unknown(level, name));
  }

  public List<TypeContract> describe(ModelLevel level, List<String> names) {
    if (names == null || names.isEmpty()) {
      throw new PlatformException(400, "At least one type name is required.");
    }
    return names.stream().distinct().map(name -> require(level, name)).toList();
  }

  /**
   * Returns requested contracts plus their complete required-containment closure.
   *
   * <p>This follows the live Ecore graph and is deliberately independent of prompt wording or
   * DSML-specific names. A provider that requests a parent contract receives every mandatory
   * contained contract needed to construct it correctly.
   */
  public List<TypeContract> requiredContainmentClosure(ModelLevel level, List<String> names) {
    if (names == null || names.isEmpty()) {
      throw new PlatformException(400, "At least one type name is required.");
    }
    java.util.LinkedHashSet<String> pending = new java.util.LinkedHashSet<>();
    for (String name : names) pending.add(require(level, name).eClass());
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
    return visited.stream().map(type -> require(level, type)).toList();
  }

  public List<TypeContract> all(ModelLevel level) {
    return knowledge.typeContracts(level);
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
    String requested = requestedName == null ? "" : requestedName.toLowerCase();
    return knowledge.typeContracts(level).stream()
        .map(TypeContract::eClass)
        .sorted(Comparator.comparingInt(name -> distance(requested, name.toLowerCase())))
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
