package io.mehdieidi.modriss.platform.assistant.metamodel;

import io.mehdieidi.modriss.platform.kernel.ModelLevel;
import io.mehdieidi.modriss.platform.kernel.PlatformException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Curated projection of the canonical metamodel surface presented to the assistant. */
public final class AssistantMetamodelProfile {

  private static final String EXCERPT_RESOURCE = "/assistant/metamodel/excerpt-metamodel.json";

  private final AssistantMetamodelMode mode;
  private final Map<ModelLevel, Set<String>> includedTypes;

  private AssistantMetamodelProfile(
      AssistantMetamodelMode mode, Map<ModelLevel, Set<String>> includedTypes) {
    this.mode = mode;
    this.includedTypes = Map.copyOf(includedTypes);
  }

  /** Creates the configured assistant metamodel projection. */
  public static AssistantMetamodelProfile forMode(AssistantMetamodelMode mode) {
    AssistantMetamodelMode selected = mode == null ? AssistantMetamodelMode.NORMAL : mode;
    return selected == AssistantMetamodelMode.EXCERPT ? loadExcerpt() : normal();
  }

  /** Complete canonical surface used by existing behavior and by default. */
  public static AssistantMetamodelProfile normal() {
    return new AssistantMetamodelProfile(AssistantMetamodelMode.NORMAL, Map.of());
  }

  /** Curated CIM/PIM surface loaded from the versioned assistant resource. */
  public static AssistantMetamodelProfile excerpt() {
    return loadExcerpt();
  }

  public AssistantMetamodelMode mode() {
    return mode;
  }

  /** Whether a canonical EClass belongs to the assistant-facing surface. */
  public boolean includes(ModelLevel level, String typeName) {
    if (mode == AssistantMetamodelMode.NORMAL || level == ModelLevel.PSM) {
      return true;
    }
    return includedTypes.getOrDefault(level, Set.of()).contains(typeName);
  }

  /** Ordered type names selected for one level. */
  public Set<String> includedTypes(ModelLevel level) {
    return includedTypes.getOrDefault(level, Set.of());
  }

  private static AssistantMetamodelProfile loadExcerpt() {
    try (InputStream input =
        AssistantMetamodelProfile.class.getResourceAsStream(EXCERPT_RESOURCE)) {
      if (input == null) {
        throw new PlatformException(500, "Assistant excerpt metamodel resource is missing.");
      }
      JsonNode root = new ObjectMapper().readTree(input);
      Map<ModelLevel, Set<String>> levels = new EnumMap<>(ModelLevel.class);
      for (ModelLevel level : List.of(ModelLevel.CIM, ModelLevel.PIM)) {
        JsonNode values = root.path("levels").path(level.apiName()).path("includedTypes");
        if (!values.isArray() || values.isEmpty()) {
          throw new PlatformException(
              500, "Assistant excerpt metamodel has no types for " + level + ".");
        }
        LinkedHashSet<String> types = new LinkedHashSet<>();
        values.forEach(value -> types.add(value.asText()));
        levels.put(level, Collections.unmodifiableSet(new LinkedHashSet<>(types)));
      }
      return new AssistantMetamodelProfile(AssistantMetamodelMode.EXCERPT, levels);
    } catch (IOException failure) {
      throw new PlatformException(500, "Assistant excerpt metamodel cannot be read.");
    }
  }
}
