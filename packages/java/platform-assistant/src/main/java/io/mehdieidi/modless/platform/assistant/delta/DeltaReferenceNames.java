package io.mehdieidi.modless.platform.assistant.delta;

import io.mehdieidi.modless.platform.assistant.patch.AssistantMetamodelSchemaService;
import io.mehdieidi.modless.platform.kernel.ModelLevel;
import java.util.List;
import java.util.Locale;

/** Resolves common LLM reference aliases to exact Ecore feature names. */
final class DeltaReferenceNames {

  private DeltaReferenceNames() {}

  static String canonicalReferenceName(
      AssistantMetamodelSchemaService schemas,
      ModelLevel level,
      String sourceType,
      String requestedName,
      String targetType) {
    if (sourceType == null
        || targetType == null
        || requestedName == null
        || requestedName.isBlank()) {
      return requestedName == null ? "" : requestedName;
    }
    if (schemas.acceptsReferenceTarget(level, sourceType, requestedName, targetType)) {
      return requestedName;
    }
    List<String> candidates =
        schemas.typeSchema(level, sourceType).stream()
            .flatMap(type -> type.references().stream())
            .filter(reference -> !reference.containment() && !reference.readonly())
            .filter(
                reference ->
                    schemas.acceptsReferenceTarget(level, sourceType, reference.name(), targetType))
            .map(AssistantMetamodelSchemaService.ReferenceSchema::name)
            .toList();
    if (candidates.isEmpty()) {
      return requestedName;
    }
    String normalized = requestedName.trim().toLowerCase(Locale.ROOT);
    if ("Function".equals(sourceType) && "EventType".equals(targetType)) {
      if (List.of(
                  "publishesevents",
                  "publishevents",
                  "publishes",
                  "publish",
                  "producesevents",
                  "produceevents",
                  "emits",
                  "emitted")
              .contains(normalized)
          && candidates.contains("publishes")) {
        return "publishes";
      }
    }
    if ("AggregateCandidate".equals(sourceType) && "DomainEntity".equals(targetType)) {
      if (List.of("entities", "entity", "domainentities", "domainentity").contains(normalized)) {
        if (candidates.contains("members")) {
          return "members";
        }
        if (candidates.contains("root")) {
          return "root";
        }
      }
    }
    if (List.of(
            "emit",
            "emits",
            "emitted",
            "publish",
            "publishes",
            "produce",
            "produces",
            "raise",
            "raises")
        .contains(normalized)) {
      if ("Command".equals(sourceType) && candidates.contains("expectedEvents")) {
        return "expectedEvents";
      }
      if ("Policy".equals(sourceType)
          && "BusinessEvent".equals(targetType)
          && candidates.contains("emitsEvents")) {
        return "emitsEvents";
      }
      if ("Policy".equals(sourceType)
          && "Command".equals(targetType)
          && candidates.contains("emitsCommands")) {
        return "emitsCommands";
      }
    }
    if (List.of("trigger", "triggers", "causes", "causedBy", "caused_by").contains(normalized)) {
      if ("Policy".equals(sourceType)
          && "BusinessEvent".equals(targetType)
          && candidates.contains("triggeredBy")) {
        return "triggeredBy";
      }
      if ("BusinessEvent".equals(sourceType) && candidates.contains("consumedByPolicies")) {
        return "consumedByPolicies";
      }
    }
    if ("ApiGatewayIntegration".equals(sourceType) && "AwsLambdaFunction".equals(targetType)) {
      if ((normalized.contains("lambda") || normalized.contains("function"))
          && candidates.contains("lambdaTarget")) {
        return "lambdaTarget";
      }
    }
    if ("EventBridgeRule".equals(sourceType) && "EventBridgeBus".equals(targetType)) {
      if ((normalized.contains("bus") || normalized.equals("eventbus"))
          && candidates.contains("bus")) {
        return "bus";
      }
    }
    if ("TaskStep".equals(sourceType) && "Function".equals(targetType)) {
      if ((normalized.contains("invoke") || normalized.contains("function"))
          && candidates.contains("invokesFunction")) {
        return "invokesFunction";
      }
    }
    return candidates.size() == 1 ? candidates.get(0) : requestedName;
  }
}
