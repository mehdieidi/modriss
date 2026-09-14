package io.mehdieidi.modriss.platform.assistant.metamodel;

import io.mehdieidi.modriss.platform.kernel.ModelLevel;
import io.mehdieidi.modriss.platform.kernel.PlatformException;
import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashSet;
import java.util.Set;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Non-structural assistant methodology metadata kept outside Java workflow logic. */
public final class AssistantMetamodelSemantics {

  private static final String RESOURCE = "/assistant/metamodel/metamodel-semantics.json";
  private static final Catalog CATALOG = load();

  private AssistantMetamodelSemantics() {}

  /** Whether a type counts as substantive source-derived modeling for quality telemetry. */
  public static boolean isSourceRichType(ModelLevel level, String typeName) {
    return level == ModelLevel.CIM && CATALOG.richTypes().contains(typeName);
  }

  /** Whether a type is intentionally treated as a generic source-modeling concept. */
  public static boolean isSourceGenericType(ModelLevel level, String typeName) {
    return level == ModelLevel.CIM && CATALOG.genericTypes().contains(typeName);
  }

  /** Configured type names for startup/test validation against canonical Ecore. */
  public static Set<String> configuredTypes(ModelLevel level) {
    if (level != ModelLevel.CIM) {
      return Set.of();
    }
    LinkedHashSet<String> types = new LinkedHashSet<>(CATALOG.genericTypes());
    types.addAll(CATALOG.richTypes());
    return Set.copyOf(types);
  }

  private static Catalog load() {
    try (InputStream input = AssistantMetamodelSemantics.class.getResourceAsStream(RESOURCE)) {
      if (input == null) {
        throw new PlatformException(500, "Assistant metamodel semantics resource is missing.");
      }
      JsonNode quality =
          new ObjectMapper().readTree(input).path("levels").path("cim").path("sourceQuality");
      Set<String> genericTypes = strings(quality.path("genericTypes"));
      Set<String> richTypes = strings(quality.path("richTypes"));
      if (genericTypes.isEmpty() || richTypes.isEmpty()) {
        throw new PlatformException(500, "Assistant metamodel semantics are incomplete.");
      }
      return new Catalog(genericTypes, richTypes);
    } catch (IOException failure) {
      throw new PlatformException(500, "Assistant metamodel semantics cannot be read.");
    }
  }

  private static Set<String> strings(JsonNode values) {
    LinkedHashSet<String> result = new LinkedHashSet<>();
    if (values.isArray()) {
      values.forEach(value -> result.add(value.asText()));
    }
    return Set.copyOf(result);
  }

  private record Catalog(Set<String> genericTypes, Set<String> richTypes) {}
}
