package io.mehdieidi.modriss.backend.impact;

import io.mehdieidi.modriss.platform.artifact.application.ArtifactService;
import io.mehdieidi.modriss.platform.artifact.domain.ArtifactRecord;
import io.mehdieidi.modriss.platform.identity.domain.UserRecord;
import io.mehdieidi.modriss.platform.kernel.ModelLevel;
import io.mehdieidi.modriss.platform.kernel.PlatformException;
import io.mehdieidi.modriss.platform.model.application.ModelService;
import io.mehdieidi.modriss.platform.model.domain.ModelRecord;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;

/** Follows recorded transformation and generation edges, using model-scoped element identities. */
@Service
public class ImpactAnalysisService {
  private final ModelService models;
  private final ArtifactService artifacts;

  public ImpactAnalysisService(ModelService models, ArtifactService artifacts) {
    this.models = models;
    this.artifacts = artifacts;
  }

  public ElementImpactResponse elementImpact(
      UserRecord user, ModelLevel level, String modelId, String elementId) {
    ModelRecord model = models.get(user, level, modelId);
    Workspace workspace = workspace(user, model.projectId());
    NodeKey focal = new NodeKey(modelId, elementId);
    ElementRef ref = workspace.nodes.get(focal);
    if (ref == null) {
      throw new PlatformException(
          404, "Element not found in the saved model. Save your changes and try again.");
    }
    Traversal upstream = workspace.walk(List.of(focal), true);
    Traversal downstream = workspace.walk(List.of(focal), false);
    Set<TraceEdge> edges = new LinkedHashSet<>(upstream.edges);
    edges.addAll(downstream.edges);
    return new ElementImpactResponse(
        ref,
        upstream.nodes,
        downstream.nodes,
        List.of(),
        List.copyOf(edges),
        java.util.stream.Stream.concat(upstream.notices.stream(), downstream.notices.stream())
            .distinct()
            .toList());
  }

  public ArtifactImpactResponse artifactImpact(UserRecord user, String artifactId) {
    return artifactImpact(user, artifactId, null);
  }

  public ArtifactImpactResponse artifactImpact(
      UserRecord user, String artifactId, String filePath) {
    ArtifactRecord artifact = artifacts.get(user, artifactId);
    if (filePath != null && !artifact.files().containsKey(filePath)) {
      throw new PlatformException(404, "Artifact file not found.");
    }
    Workspace workspace = workspace(user, artifact.projectId());
    // Use the authorized, repaired record even when a legacy summary lacks trace metadata.
    workspace.addArtifact(artifact);
    List<NodeKey> starts =
        workspace.nodes.keySet().stream()
            .filter(key -> artifactId.equals(key.modelId))
            .filter(key -> filePath == null || filePath.equals(key.elementId))
            .toList();
    Traversal upstream = workspace.walk(starts, true);
    List<ElementRef> sourceElements =
        upstream.nodes.stream().filter(ref -> !"ARTIFACT".equals(ref.modelType())).toList();
    List<ModelAncestor> ancestors = new ArrayList<>();
    String sourceId = text(artifact.modelJson(), "sourceModelId");
    Set<String> visited = new LinkedHashSet<>();
    while (!sourceId.isBlank() && visited.add(sourceId)) {
      ModelRecord source = workspace.models.get(sourceId);
      if (source == null) break;
      ancestors.add(new ModelAncestor(source.id(), source.level().apiName(), source.name()));
      sourceId = text(source.modelJson(), "sourceModelId");
    }
    return new ArtifactImpactResponse(
        artifact.id(),
        "ARTIFACT",
        artifact.name(),
        artifact.modelJson(),
        ancestors,
        sourceElements,
        upstream.edges,
        workspace.ambiguousFiles.getOrDefault(artifactId, Set.of()).stream()
            .filter(path -> filePath == null || filePath.equals(path))
            .findAny()
            .map(path -> List.of(legacyNotice(artifact.name())))
            .orElse(List.of()));
  }

  private Workspace workspace(UserRecord user, String projectId) {
    Workspace workspace = new Workspace();
    for (ModelLevel level : ModelLevel.values()) {
      for (ModelRecord model : models.list(user, level, projectId)) {
        workspace.models.put(model.id(), model);
        Map<String, JsonNode> elements = new LinkedHashMap<>();
        collectElements(model.modelJson(), elements);
        // Some older editor records store elements only in the semantic graph.
        for (JsonNode element : model.modelJson().path("graph").path("elements")) {
          String id = text(element, "id");
          if (!id.isBlank()) elements.putIfAbsent(id, element);
        }
        workspace.elements.put(model.id(), elements);
        elements.forEach(
            (id, element) ->
                workspace.nodes.put(
                    new NodeKey(model.id(), id),
                    new ElementRef(
                        model.id(),
                        model.level().name(),
                        model.name(),
                        text(model.modelJson(), "sourceModelId"),
                        id,
                        firstText(text(element, "eClass"), text(element, "type"), "Model Element"),
                        firstText(
                            text(element, "name"),
                            text(element, "label"),
                            text(element, "displayName"),
                            id),
                        "MODEL_ELEMENT")));
      }
    }
    for (ModelRecord target : workspace.models.values()) {
      String sourceId = text(target.modelJson(), "sourceModelId");
      if (!workspace.models.containsKey(sourceId)) continue;
      List<JsonNode> traces = new ArrayList<>();
      collectTraces(target.modelJson(), traces, false);
      for (JsonNode trace : traces) {
        workspace.addEdge(
            new TraceEdge(
                sourceId,
                firstText(text(trace, "sourceElementId"), text(trace, "source")),
                target.id(),
                firstText(text(trace, "targetElementId"), text(trace, "target")),
                firstText(text(trace, "linkType"), "TRANSFORMS_TO"),
                text(trace, "transformationRule")));
      }
      // generatedFrom is provenance, never an alias for the target element's identity.
      workspace
          .elements
          .get(target.id())
          .forEach(
              (id, element) -> {
                NodeKey destination = new NodeKey(target.id(), id);
                String origin = text(element, "generatedFrom");
                if (!origin.isBlank()
                    && workspace.incoming.getOrDefault(destination, List.of()).isEmpty()) {
                  workspace.addEdge(
                      new TraceEdge(
                          sourceId, origin, target.id(), id, "GENERATED_FROM", "generatedFrom"));
                }
              });
    }
    for (ArtifactRecord artifact : artifacts.listSummaries(user, projectId)) {
      workspace.addArtifact(artifact);
    }
    return workspace;
  }

  private static void collectElements(JsonNode node, Map<String, JsonNode> elements) {
    if (node.isObject()) {
      String id = text(node, "id");
      if (!id.isBlank()) elements.putIfAbsent(id, node);
      node.properties()
          .forEach(
              entry -> {
                if (!Set.of("graph", "diagram", "views").contains(entry.getKey())) {
                  collectElements(entry.getValue(), elements);
                }
              });
    } else if (node.isArray()) {
      node.forEach(child -> collectElements(child, elements));
    }
  }

  private static void collectTraces(JsonNode node, List<JsonNode> traces, boolean traceContainer) {
    if (node.isObject()) {
      if (traceContainer || "TraceLink".equals(text(node, "eClass"))) traces.add(node);
      node.properties()
          .forEach(
              entry -> {
                if (!Set.of("diagram", "views").contains(entry.getKey())) {
                  collectTraces(entry.getValue(), traces, "traceLinks".equals(entry.getKey()));
                }
              });
    } else if (node.isArray()) {
      node.forEach(child -> collectTraces(child, traces, traceContainer));
    }
  }

  private static String text(JsonNode node, String field) {
    return node == null ? "" : node.path(field).asText("");
  }

  private static String firstText(String... values) {
    for (String value : values) {
      if (!value.isBlank()) return value;
    }
    return "";
  }

  private static Set<String> legacyKeys(JsonNode element) {
    Set<String> keys = new LinkedHashSet<>();
    for (String field :
        List.of(
            "id",
            "traceId",
            "externalId",
            "sourceQualifiedName",
            "logicalId",
            "physicalName",
            "name",
            "displayName",
            "label")) {
      String key = text(element, field);
      if (!key.isBlank()) keys.add(key);
    }
    return keys;
  }

  private static String legacyNotice(String artifactName) {
    return "Some file traces in "
        + artifactName
        + " use a shared legacy trace key and cannot identify an exact source. Regenerate this"
        + " artifact to include those files in impact analysis.";
  }

  private static final class Workspace {
    private final Map<String, ModelRecord> models = new LinkedHashMap<>();
    private final Map<String, Map<String, JsonNode>> elements = new LinkedHashMap<>();
    private final Map<NodeKey, ElementRef> nodes = new LinkedHashMap<>();
    private final Map<NodeKey, List<TraceEdge>> incoming = new LinkedHashMap<>();
    private final Map<NodeKey, List<TraceEdge>> outgoing = new LinkedHashMap<>();
    private final Set<TraceEdge> edges = new LinkedHashSet<>();
    private final Map<NodeKey, Set<String>> sourceNotices = new LinkedHashMap<>();
    private final Map<String, Set<String>> ambiguousFiles = new LinkedHashMap<>();

    void addEdge(TraceEdge edge) {
      if (!nodes.containsKey(edge.source())
          || !nodes.containsKey(edge.target())
          || !edges.add(edge)) return;
      incoming.computeIfAbsent(edge.target(), ignored -> new ArrayList<>()).add(edge);
      outgoing.computeIfAbsent(edge.source(), ignored -> new ArrayList<>()).add(edge);
    }

    void addArtifact(ArtifactRecord artifact) {
      String sourceId = text(artifact.modelJson(), "sourceModelId");
      if (!models.containsKey(sourceId)) return;
      // Only files with recorded, resolvable generation provenance participate in impact.
      JsonNode traceability = artifact.modelJson().path("traceability");
      if (!traceability.isObject()) return;
      Map<String, Set<String>> idsByKey = new LinkedHashMap<>();
      if (artifact.modelJson().path("traceabilityVersion").asInt(0) < 2) {
        elements
            .get(sourceId)
            .forEach(
                (id, element) ->
                    legacyKeys(element)
                        .forEach(
                            key ->
                                idsByKey
                                    .computeIfAbsent(key, ignored -> new LinkedHashSet<>())
                                    .add(id)));
      }
      traceability
          .properties()
          .forEach(
              entry -> {
                if (!nodes.containsKey(new NodeKey(sourceId, entry.getKey()))
                    || !entry.getValue().isArray()) return;
                for (JsonNode pathNode : entry.getValue()) {
                  String path = pathNode.asText("");
                  if (path.isBlank() || !artifact.files().containsKey(path)) continue;
                  boolean ambiguous =
                      legacyKeys(elements.get(sourceId).get(entry.getKey())).stream()
                          .flatMap(key -> idsByKey.getOrDefault(key, Set.of()).stream())
                          .filter(id -> !id.equals(entry.getKey()))
                          .anyMatch(
                              id -> {
                                for (JsonNode otherPath : traceability.path(id)) {
                                  if (path.equals(otherPath.asText(""))) return true;
                                }
                                return false;
                              });
                  if (ambiguous) {
                    ambiguousFiles
                        .computeIfAbsent(artifact.id(), ignored -> new LinkedHashSet<>())
                        .add(path);
                    sourceNotices
                        .computeIfAbsent(
                            new NodeKey(sourceId, entry.getKey()), ignored -> new LinkedHashSet<>())
                        .add(legacyNotice(artifact.name()));
                    continue;
                  }
                  nodes.putIfAbsent(
                      new NodeKey(artifact.id(), path),
                      new ElementRef(
                          artifact.id(),
                          "ARTIFACT",
                          artifact.name(),
                          sourceId,
                          path,
                          "Generated File",
                          path,
                          "GENERATED_ARTIFACT"));
                  addEdge(
                      new TraceEdge(
                          sourceId,
                          entry.getKey(),
                          artifact.id(),
                          path,
                          "GENERATED_ARTIFACT",
                          text(artifact.modelJson().path("generationRules"), path)));
                }
              });
    }

    Traversal walk(List<NodeKey> starts, boolean reverse) {
      Set<NodeKey> visited = new LinkedHashSet<>(starts);
      ArrayDeque<NodeKey> queue = new ArrayDeque<>(starts);
      List<ElementRef> reached = new ArrayList<>();
      Set<TraceEdge> traversed = new LinkedHashSet<>();
      Set<String> notices = new LinkedHashSet<>();
      while (!queue.isEmpty()) {
        NodeKey cursor = queue.removeFirst();
        notices.addAll(sourceNotices.getOrDefault(cursor, Set.of()));
        for (TraceEdge edge : (reverse ? incoming : outgoing).getOrDefault(cursor, List.of())) {
          traversed.add(edge);
          NodeKey next = reverse ? edge.source() : edge.target();
          if (visited.add(next)) {
            reached.add(nodes.get(next));
            queue.addLast(next);
          }
        }
      }
      return new Traversal(reached, List.copyOf(traversed), List.copyOf(notices));
    }
  }

  private record NodeKey(String modelId, String elementId) {}

  private record Traversal(List<ElementRef> nodes, List<TraceEdge> edges, List<String> notices) {}

  public record TraceEdge(
      String sourceModelId,
      String sourceElementId,
      String targetModelId,
      String targetElementId,
      String relationship,
      String transformationRule) {
    private NodeKey source() {
      return new NodeKey(sourceModelId, sourceElementId);
    }

    private NodeKey target() {
      return new NodeKey(targetModelId, targetElementId);
    }
  }

  public record ElementImpactResponse(
      ElementRef focalElement,
      List<ElementRef> upstream,
      List<ElementRef> downstream,
      List<ElementRef> connectedElements,
      List<TraceEdge> traceEdges,
      List<String> notices) {}

  public record ArtifactImpactResponse(
      String modelId,
      String modelType,
      String modelName,
      JsonNode metadata,
      List<ModelAncestor> ancestors,
      List<ElementRef> upstream,
      List<TraceEdge> traceEdges,
      List<String> notices) {}

  public record ModelAncestor(String id, String type, String name) {}

  public record ElementRef(
      String modelId,
      String modelType,
      String modelName,
      String sourceModelId,
      String elementId,
      String elementType,
      String elementName,
      String relationship) {}
}
