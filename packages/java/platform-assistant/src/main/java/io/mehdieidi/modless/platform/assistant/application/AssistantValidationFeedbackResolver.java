package io.mehdieidi.modless.platform.assistant.application;

import io.mehdieidi.modless.platform.assistant.domain.SemanticModelPatch;
import io.mehdieidi.modless.platform.assistant.patch.AssistantMetamodelSchemaService;
import io.mehdieidi.modless.platform.assistant.provider.AssistantModelProvider;
import io.mehdieidi.modless.platform.kernel.ModelLevel;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Maps structural validation feedback to metamodel type contracts for repair and retrieval. */
public class AssistantValidationFeedbackResolver {

  private static final Pattern REQUIRED_FEATURE =
      Pattern.compile("required feature '([^']+)'", Pattern.CASE_INSENSITIVE);
  private static final Pattern ELEMENT_TYPE =
      Pattern.compile(
          "(?:of|for)\\s+'([^']+)'@|@([A-Za-z][A-Za-z0-9_]*)", Pattern.CASE_INSENSITIVE);
  private static final Pattern ELEMENT_UUID =
      Pattern.compile(
          "#([0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12})",
          Pattern.CASE_INSENSITIVE);

  /** One required containment feature reported by structural validation. */
  public record MissingRequiredFeature(String ownerElementId, String featureName) {}

  private final AssistantMetamodelSchemaService schemas;

  public AssistantValidationFeedbackResolver(AssistantMetamodelSchemaService schemas) {
    this.schemas = schemas;
  }

  /**
   * Resolves metamodel types implicated by validation feedback and the rejected patch.
   *
   * @param level model level
   * @param feedback validation messages
   * @param patch rejected patch
   * @return canonical type names
   */
  public List<String> resolveTypes(
      ModelLevel level, List<String> feedback, SemanticModelPatch patch) {
    Set<String> types = new LinkedHashSet<>();
    if (patch != null) {
      patch.operations().stream()
          .filter(java.util.Objects::nonNull)
          .map(SemanticModelPatch.Operation::elementType)
          .filter(type -> type != null && !type.isBlank())
          .forEach(
              type -> {
                try {
                  types.add(schemas.canonicalType(level, type));
                } catch (RuntimeException ignored) {
                  // Unknown types are already surfaced by validation.
                }
              });
    }
    for (String line : feedback == null ? List.<String>of() : feedback) {
      if (line == null || line.isBlank()) {
        continue;
      }
      Matcher featureMatcher = REQUIRED_FEATURE.matcher(line);
      if (featureMatcher.find()) {
        String feature = featureMatcher.group(1);
        resolveOwnerTypes(level, feature, patch).forEach(types::add);
      }
      Matcher typeMatcher = ELEMENT_TYPE.matcher(line);
      while (typeMatcher.find()) {
        String raw = typeMatcher.group(1) != null ? typeMatcher.group(1) : typeMatcher.group(2);
        if (raw != null && !raw.isBlank()) {
          try {
            types.add(schemas.canonicalType(level, raw));
          } catch (RuntimeException ignored) {
            types.add(raw);
          }
        }
      }
    }
    return List.copyOf(types);
  }

  /**
   * Returns type-contract snippets for validation feedback.
   *
   * @param level model level
   * @param feedback validation messages
   * @param patch rejected patch
   * @param limit maximum snippets
   * @return context snippets
   */
  public List<AssistantModelProvider.ContextSnippet> contractsForFeedback(
      ModelLevel level, List<String> feedback, SemanticModelPatch patch, int limit) {
    List<AssistantModelProvider.ContextSnippet> result = new ArrayList<>();
    for (String type : resolveTypes(level, feedback, patch)) {
      if (result.size() >= limit) {
        break;
      }
      try {
        result.add(schemas.typeContract(level, type));
      } catch (RuntimeException ignored) {
        // Skip unknown types.
      }
    }
    return result;
  }

  /**
   * Parses required-feature validation lines into owner element IDs and feature names.
   *
   * @param feedback validation messages
   * @return missing required containments
   */
  public List<MissingRequiredFeature> missingRequiredFeatures(List<String> feedback) {
    List<MissingRequiredFeature> result = new ArrayList<>();
    if (feedback == null) {
      return result;
    }
    for (String line : feedback) {
      if (line == null || line.isBlank()) {
        continue;
      }
      Matcher featureMatcher = REQUIRED_FEATURE.matcher(line);
      if (!featureMatcher.find()) {
        continue;
      }
      String feature = featureMatcher.group(1);
      Matcher uuidMatcher = ELEMENT_UUID.matcher(line);
      if (!uuidMatcher.find()) {
        continue;
      }
      result.add(new MissingRequiredFeature(uuidMatcher.group(1), feature));
    }
    return List.copyOf(result);
  }

  /** Returns whether feedback can be repaired by adding required nested elements. */
  public boolean isRepairableStructuralFailure(List<String> feedback) {
    if (feedback == null || feedback.isEmpty()) {
      return false;
    }
    return feedback.stream().anyMatch(this::isRepairableStructuralLine);
  }

  /** Returns whether feedback describes formal structural constraints rather than domain choice. */
  public boolean isFormalFailure(List<String> feedback) {
    if (feedback == null || feedback.isEmpty()) {
      return false;
    }
    return feedback.stream().anyMatch(this::isFormalLine);
  }

  private boolean isRepairableStructuralLine(String line) {
    if (line == null || line.isBlank()) {
      return false;
    }
    return REQUIRED_FEATURE.matcher(line).find();
  }

  private boolean isFormalLine(String line) {
    if (line == null || line.isBlank()) {
      return false;
    }
    String normalized = line.toLowerCase(Locale.ROOT);
    if (REQUIRED_FEATURE.matcher(line).find()) {
      return true;
    }
    return normalized.contains("not grounded in the metamodel")
        || normalized.contains("unknown metamodel")
        || normalized.contains("invalid or duplicate stable id")
        || normalized.contains("missing its owner")
        || normalized.contains("operation summary");
  }

  private List<String> resolveOwnerTypes(
      ModelLevel level, String feature, SemanticModelPatch patch) {
    if (patch == null) {
      return List.of();
    }
    Set<String> owners = new LinkedHashSet<>();
    for (SemanticModelPatch.Operation operation : patch.operations()) {
      if (operation == null || operation.type() != SemanticModelPatch.OperationType.ADD_ELEMENT) {
        continue;
      }
      try {
        String type = schemas.canonicalType(level, operation.elementType());
        if (schemas
            .reference(level, type, feature)
            .filter(reference -> reference.required() && reference.containment())
            .isPresent()) {
          owners.add(type);
        }
      } catch (RuntimeException ignored) {
        // Ignore unknown operation types.
      }
    }
    return List.copyOf(owners);
  }
}
