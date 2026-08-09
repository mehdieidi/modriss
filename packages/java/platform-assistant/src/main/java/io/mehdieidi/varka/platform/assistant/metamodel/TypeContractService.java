package io.mehdieidi.varka.platform.assistant.metamodel;

import io.mehdieidi.varka.platform.assistant.metamodel.MetamodelKnowledgeService.TypeContract;
import io.mehdieidi.varka.platform.kernel.ModelLevel;
import io.mehdieidi.varka.platform.kernel.PlatformException;
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
    java.util.Optional<TypeContract> resolved = resolveProviderTypeName(level, name);
    return resolved.orElseThrow(() -> unknown(level, name));
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
    String requested = requestedName == null ? "" : requestedName.toLowerCase(Locale.ROOT);
    return knowledge.typeContracts(level).stream()
        .map(TypeContract::eClass)
        .sorted(Comparator.comparingInt(name -> distance(requested, name.toLowerCase(Locale.ROOT))))
        .limit(3)
        .toList();
  }

  private java.util.Optional<TypeContract> resolveProviderTypeName(ModelLevel level, String name) {
    String normalized = normalizedName(name);
    if (normalized.isBlank()) return java.util.Optional.empty();
    List<TypeContract> contracts = knowledge.typeContracts(level);
    java.util.Optional<TypeContract> caseInsensitive =
        contracts.stream().filter(type -> type.eClass().equalsIgnoreCase(name)).findFirst();
    if (caseInsensitive.isPresent()) return caseInsensitive;
    java.util.Optional<TypeContract> normalizedExact =
        contracts.stream()
            .filter(type -> normalizedName(type.eClass()).equals(normalized))
            .findFirst();
    if (normalizedExact.isPresent()) return normalizedExact;
    List<TypeContract> embedded =
        contracts.stream()
            .filter(type -> normalized.contains(normalizedName(type.eClass())))
            .sorted(
                Comparator.<TypeContract>comparingInt(
                        type -> normalizedName(type.eClass()).length())
                    .reversed()
                    .thenComparing(TypeContract::eClass))
            .toList();
    return embedded.size() == 1 || (embedded.size() > 1 && distinctBestEmbedded(embedded))
        ? java.util.Optional.of(embedded.get(0))
        : resolveCommonProviderAlias(level, normalized, contracts);
  }

  private java.util.Optional<TypeContract> resolveCommonProviderAlias(
      ModelLevel level, String normalized, List<TypeContract> contracts) {
    String alias =
        switch (level) {
          case CIM -> cimAlias(normalized);
          case PIM -> pimAlias(normalized);
          case PSM -> null;
        };
    if (alias == null) return java.util.Optional.empty();
    return contracts.stream().filter(type -> type.eClass().equals(alias)).findFirst();
  }

  private String cimAlias(String normalized) {
    return switch (normalized) {
      case "concept", "businessconcept", "domainconcept" -> "InformationItem";
      case "process", "workflow", "businessworkflow" -> "BusinessProcess";
      case "userstory", "story", "request", "businessrequirement" -> "Requirement";
      case "capability", "feature" -> "BusinessCapability";
      case "policy", "rule" -> "Policy";
      default -> normalized.endsWith("request") ? "Requirement" : null;
    };
  }

  private String pimAlias(String normalized) {
    return switch (normalized) {
      case "requirement", "userstory", "story", "feature", "capability" -> "PlatformCapability";
      case "service", "application", "pimmodel" -> "ServerlessService";
      case "function", "commandhandler", "queryhandler", "eventhandler", "handler" -> "Function";
      case "api", "apicomponent", "httpapi", "restapi" -> "Api";
      case "schedule", "scheduler", "scheduledtask" -> "Schedule";
      case "idempotencypattern", "idempotency", "duplicatehandling" -> "IdempotencyPolicy";
      case "cache", "caching", "cachepolicy", "cachingpolicy" -> "CachePolicy";
      case "datastore", "storage", "database" -> "DataStore";
      case "event", "message" -> "EventType";
      case "security", "securitypolicy", "architecturepolicy", "policy" -> "ArchitecturePolicy";
      default -> null;
    };
  }

  private boolean distinctBestEmbedded(List<TypeContract> embedded) {
    int best = normalizedName(embedded.get(0).eClass()).length();
    int next = normalizedName(embedded.get(1).eClass()).length();
    return best > next;
  }

  private String normalizedName(String value) {
    if (value == null) return "";
    return value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
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
