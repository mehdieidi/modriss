package io.mehdieidi.modriss.platform.transformation.application;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/** Maps exact generator source IDs, with unambiguous stable-key fallback for legacy reports. */
final class ArtifactTraceability {
  private ArtifactTraceability() {}

  static ObjectNode build(JsonNode model, JsonNode report, ObjectMapper mapper) {
    Map<String, Set<String>> idsByStableKey = new LinkedHashMap<>();
    Set<String> ids = new LinkedHashSet<>();
    collect(model, idsByStableKey, ids);
    Map<String, Set<String>> pathsById = new LinkedHashMap<>();
    for (JsonNode artifact : report.path("artifacts")) {
      String path = artifact.path("path").asText("");
      if (path.isBlank()) continue;
      if (artifact.path("sourceElementIds").isArray()) {
        for (JsonNode source : artifact.path("sourceElementIds")) {
          String id = source.asText("");
          if (ids.contains(id))
            pathsById.computeIfAbsent(id, ignored -> new LinkedHashSet<>()).add(path);
        }
      } else if (artifact.path("sourceStableIds").isArray()) {
        // Old reports cannot distinguish shared stable keys. Do not manufacture contributors.
        for (JsonNode stableKey : artifact.path("sourceStableIds")) {
          Set<String> matches = idsByStableKey.getOrDefault(stableKey.asText(""), Set.of());
          if (matches.size() == 1) {
            pathsById
                .computeIfAbsent(matches.iterator().next(), ignored -> new LinkedHashSet<>())
                .add(path);
          }
        }
      }
    }
    ObjectNode result = mapper.createObjectNode();
    pathsById.forEach(
        (id, paths) -> {
          var array = result.putArray(id);
          paths.stream().sorted().forEach(array::add);
        });
    return result;
  }

  private static void collect(JsonNode node, Map<String, Set<String>> index, Set<String> seen) {
    if (node.isObject()) {
      String id = node.path("id").asText("");
      if (!id.isBlank() && seen.add(id)) {
        // A name is used by the generator only when stronger identities are absent.
        // Matching every alias could attribute another element's files to this one.
        for (String field :
            List.of("traceId", "externalId", "sourceQualifiedName", "logicalId", "name", "id")) {
          String key = node.path(field).asText("");
          if (!key.isBlank()) {
            index.computeIfAbsent(key, ignored -> new LinkedHashSet<>()).add(id);
            break;
          }
        }
      }
      node.properties()
          .forEach(
              entry -> {
                if (!Set.of("graph", "diagram", "views").contains(entry.getKey()))
                  collect(entry.getValue(), index, seen);
              });
      // Graph-only legacy records are handled separately without indexing visual copies.
      if (node.path("graph").path("elements").isArray()) {
        for (JsonNode element : node.path("graph").path("elements")) {
          collect(element, index, seen);
        }
      }
    } else if (node.isArray()) {
      node.forEach(child -> collect(child, index, seen));
    }
  }
}
