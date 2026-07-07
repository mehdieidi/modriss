package io.mehdieidi.modless.platform.assistant.delta;

import io.mehdieidi.modless.platform.assistant.patch.AssistantMetamodelSchemaService;
import io.mehdieidi.modless.platform.kernel.ModelLevel;
import io.mehdieidi.modless.platform.kernel.PlatformException;
import java.util.List;
import java.util.Locale;

/** Resolves common LLM containment aliases to exact Ecore containment feature names. */
final class DeltaPlacementNames {

  private DeltaPlacementNames() {}

  static String canonicalContainmentName(
      AssistantMetamodelSchemaService schemas,
      ModelLevel level,
      String ownerType,
      String requestedName,
      String childType) {
    if (ownerType == null
        || ownerType.isBlank()
        || requestedName == null
        || requestedName.isBlank()) {
      return requestedName == null ? "" : requestedName;
    }
    try {
      schemas.requireContainment(level, ownerType, requestedName, childType);
      return requestedName;
    } catch (PlatformException ignored) {
      // Resolve aliases below.
    }
    List<AssistantMetamodelSchemaService.ReferenceSchema> candidates =
        schemas.containments(level, ownerType, childType);
    if (candidates.isEmpty()) {
      return requestedName;
    }
    String normalized = requestedName.trim().toLowerCase(Locale.ROOT);
    for (AssistantMetamodelSchemaService.ReferenceSchema candidate : candidates) {
      String name = candidate.name();
      if (name.equalsIgnoreCase(requestedName)) {
        return name;
      }
      String lower = name.toLowerCase(Locale.ROOT);
      if (normalized.equals(lower)
          || normalized.equals(lower + "s")
          || (normalized.endsWith("s")
              && normalized.substring(0, normalized.length() - 1).equals(lower))) {
        return name;
      }
    }
    String alias = containmentAlias(ownerType, childType, normalized, candidates);
    if (alias != null) {
      return alias;
    }
    return candidates.size() == 1 ? candidates.get(0).name() : requestedName;
  }

  private static String containmentAlias(
      String ownerType,
      String childType,
      String normalized,
      List<AssistantMetamodelSchemaService.ReferenceSchema> candidates) {
    if ("ServerlessService".equals(ownerType)) {
      String legacy = legacyServiceContainmentAlias(normalized, childType, candidates);
      if (legacy != null) {
        return legacy;
      }
    }
    if ("PIMModel".equals(ownerType) && "ServerlessService".equals(childType)) {
      if (List.of("service", "serverlessservice", "serverlessservices").contains(normalized)
          && candidates.stream().anyMatch(reference -> "services".equals(reference.name()))) {
        return "services";
      }
    }
    if ("Function".equals(ownerType) && "FunctionContract".equals(childType)) {
      if (List.of("contract", "contracts", "functioncontract", "functioncontracts")
              .contains(normalized)
          && candidates.stream().anyMatch(reference -> "contract".equals(reference.name()))) {
        return "contract";
      }
    }
    if ("Api".equals(ownerType) && "ApiRoute".equals(childType)) {
      if (List.of("route", "routes", "endpoint", "endpoints").contains(normalized)
          && candidates.stream().anyMatch(reference -> "routes".equals(reference.name()))) {
        return "routes";
      }
    }
    if ("CIMModel".equals(ownerType) && "Actor".equals(childType)) {
      if (List.of("actor", "actors", "role", "roles").contains(normalized)
          && candidates.stream().anyMatch(reference -> "actors".equals(reference.name()))) {
        return "actors";
      }
    }
    if ("CIMModel".equals(ownerType) && "Command".equals(childType)) {
      if (List.of("command", "commands").contains(normalized)
          && candidates.stream().anyMatch(reference -> "commands".equals(reference.name()))) {
        return "commands";
      }
    }
    if ("CIMModel".equals(ownerType) && "BusinessEvent".equals(childType)) {
      if (List.of("event", "events", "businessevent", "businessevents").contains(normalized)
          && candidates.stream().anyMatch(reference -> "events".equals(reference.name()))) {
        return "events";
      }
    }
    if ("CIMModel".equals(ownerType) && "BusinessCapability".equals(childType)) {
      if (List.of("capability", "capabilities", "businesscapability", "businesscapabilities")
              .contains(normalized)
          && candidates.stream().anyMatch(reference -> "capabilities".equals(reference.name()))) {
        return "capabilities";
      }
    }
    if ("CIMModel".equals(ownerType) && "Policy".equals(childType)) {
      if (List.of("policy", "policies").contains(normalized)
          && candidates.stream().anyMatch(reference -> "policies".equals(reference.name()))) {
        return "policies";
      }
    }
    if ("CIMModel".equals(ownerType) && "DomainEntity".equals(childType)) {
      if (List.of("entity", "entities", "domainentity", "domainentities").contains(normalized)
          && candidates.stream().anyMatch(reference -> "entities".equals(reference.name()))) {
        return "entities";
      }
    }
    if ("BusinessCapability".equals(ownerType) && "DomainEntity".equals(childType)) {
      return null;
    }
    if ("BusinessCapability".equals(ownerType)
        && List.of("Command", "BusinessEvent", "Query", "Policy").contains(childType)) {
      return null;
    }
    return null;
  }

  private static String legacyServiceContainmentAlias(
      String normalized,
      String childType,
      List<AssistantMetamodelSchemaService.ReferenceSchema> candidates) {
    record Mapping(String legacy, String feature) {}
    List<Mapping> mappings =
        List.of(
            new Mapping("ownsfunctions", "functions"),
            new Mapping("ownfunctions", "functions"),
            new Mapping("ownsapis", "apis"),
            new Mapping("ownapis", "apis"),
            new Mapping("ownschannels", "channels"),
            new Mapping("ownsstores", "stores"),
            new Mapping("ownsworkflows", "workflows"),
            new Mapping("ownadapters", "adapters"),
            new Mapping("ownschedules", "schedules"));
    for (Mapping mapping : mappings) {
      if (normalized.equals(mapping.legacy())
          && candidates.stream()
              .anyMatch(reference -> mapping.feature().equals(reference.name()))) {
        return mapping.feature();
      }
    }
    if ("Function".equals(childType)
        && List.of("function", "compute").contains(normalized)
        && candidates.stream().anyMatch(reference -> "functions".equals(reference.name()))) {
      return "functions";
    }
    if ("Api".equals(childType)
        && List.of("api", "apis").contains(normalized)
        && candidates.stream().anyMatch(reference -> "apis".equals(reference.name()))) {
      return "apis";
    }
    if ("EventChannel".equals(childType)
        && List.of("channel", "channels").contains(normalized)
        && candidates.stream().anyMatch(reference -> "channels".equals(reference.name()))) {
      return "channels";
    }
    if ("StorageElement".equals(childType)
        && List.of("store", "stores", "datastore", "datastores").contains(normalized)
        && candidates.stream().anyMatch(reference -> "stores".equals(reference.name()))) {
      return "stores";
    }
    if ("Workflow".equals(childType)
        && List.of("workflow", "workflows").contains(normalized)
        && candidates.stream().anyMatch(reference -> "workflows".equals(reference.name()))) {
      return "workflows";
    }
    if ("ExternalAdapter".equals(childType)
        && List.of("adapter", "adapters").contains(normalized)
        && candidates.stream().anyMatch(reference -> "adapters".equals(reference.name()))) {
      return "adapters";
    }
    if ("Schedule".equals(childType)
        && List.of("schedule", "schedules").contains(normalized)
        && candidates.stream().anyMatch(reference -> "schedules".equals(reference.name()))) {
      return "schedules";
    }
    return null;
  }
}
