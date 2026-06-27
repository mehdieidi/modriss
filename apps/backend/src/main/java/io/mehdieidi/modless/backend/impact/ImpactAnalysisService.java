package io.mehdieidi.modless.backend.impact;

import com.fasterxml.jackson.databind.JsonNode;
import io.mehdieidi.modless.platform.artifact.application.ArtifactService;
import io.mehdieidi.modless.platform.artifact.domain.ArtifactRecord;
import io.mehdieidi.modless.platform.identity.domain.UserRecord;
import io.mehdieidi.modless.platform.kernel.ModelLevel;
import io.mehdieidi.modless.platform.kernel.PlatformException;
import io.mehdieidi.modless.platform.model.application.ModelService;
import io.mehdieidi.modless.platform.model.domain.ModelRecord;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;

/** Builds impact trees from transformation trace links and artifact generation metadata. */
@Service
public class ImpactAnalysisService {

  private static final int MAX_DEPTH = 8;

  private final ModelService models;
  private final ArtifactService artifacts;

  public ImpactAnalysisService(ModelService models, ArtifactService artifacts) {
    this.models = models;
    this.artifacts = artifacts;
  }

  public ElementImpactResponse elementImpact(
      UserRecord user, ModelLevel level, String modelId, String elementId) {
    ModelRecord focalModel = models.get(user, level, modelId);
    Workspace workspace = workspace(user, focalModel.projectId());
    IndexedModel focalIndexed = workspace.model(modelId);
    if (focalIndexed == null) {
      throw new PlatformException(404, "Model not found.");
    }
    ElementRef focalElement =
        focalIndexed
            .ref(elementId, "FOCAL_ELEMENT")
            .orElseGet(() -> missingRef(focalModel, elementId));
    return new ElementImpactResponse(
        focalElement,
        upstream(workspace, focalIndexed, elementId),
        downstream(workspace, focalIndexed, elementId),
        connected(focalIndexed, elementId));
  }

  public ArtifactImpactResponse artifactImpact(UserRecord user, String artifactId) {
    ArtifactRecord artifact = artifacts.get(user, artifactId);
    Workspace workspace = workspace(user, artifact.projectId());
    List<ModelAncestor> ancestors = new ArrayList<>();
    String sourceModelId = text(artifact.modelJson(), "sourceModelId", "");
    Set<String> visited = new LinkedHashSet<>();
    while (!sourceModelId.isBlank() && visited.add(sourceModelId)) {
      IndexedModel source = workspace.model(sourceModelId);
      if (source == null) {
        break;
      }
      ancestors.add(
          new ModelAncestor(
              source.record.id(), source.record.level().apiName(), source.record.name()));
      sourceModelId = text(source.record.modelJson(), "sourceModelId", "");
    }
    return new ArtifactImpactResponse(
        artifact.id(), "ARTIFACT", artifact.name(), artifact.modelJson(), ancestors);
  }

  private List<ElementRef> upstream(Workspace workspace, IndexedModel focal, String elementId) {
    List<ElementRef> refs = new ArrayList<>();
    collectUpstream(workspace, focal, elementId, refs, new LinkedHashSet<>(), 0);
    return distinct(refs);
  }

  private void collectUpstream(
      Workspace workspace,
      IndexedModel current,
      String elementId,
      List<ElementRef> refs,
      Set<String> visited,
      int depth) {
    if (depth >= MAX_DEPTH || !visited.add(current.record.id() + "|" + elementId)) {
      return;
    }
    String sourceModelId = text(current.record.modelJson(), "sourceModelId", "");
    if (sourceModelId.isBlank()) {
      return;
    }
    IndexedModel source = workspace.model(sourceModelId);
    if (source == null) {
      return;
    }
    List<TraceLink> links = current.reverseTrace(elementId);
    if (links.isEmpty()) {
      String generatedFrom = current.generatedFrom(elementId);
      if (!generatedFrom.isBlank()) {
        links = List.of(new TraceLink(generatedFrom, elementId, "GENERATED_FROM", "generatedFrom"));
      }
    }
    for (TraceLink link : links) {
      source
          .ref(link.sourceElementId(), link.relationship())
          .ifPresent(
              ref -> {
                refs.add(ref);
                collectUpstream(
                    workspace, source, link.sourceElementId(), refs, visited, depth + 1);
              });
    }
  }

  private List<ElementRef> downstream(Workspace workspace, IndexedModel focal, String elementId) {
    List<ElementRef> refs = new ArrayList<>();
    ArrayDeque<TraversalNode> queue = new ArrayDeque<>();
    Set<String> visited = new LinkedHashSet<>();
    queue.add(new TraversalNode(focal.record.id(), elementId, 0));
    while (!queue.isEmpty()) {
      TraversalNode cursor = queue.removeFirst();
      if (cursor.depth >= MAX_DEPTH || !visited.add(cursor.modelId + "|" + cursor.elementId)) {
        continue;
      }
      for (IndexedModel target : workspace.childModels(cursor.modelId)) {
        List<TraceLink> links = target.forwardTrace(cursor.elementId);
        if (links.isEmpty()) {
          links = target.generatedFromMatches(cursor.elementId);
        }
        for (TraceLink link : links) {
          target
              .ref(link.targetElementId(), link.relationship())
              .ifPresent(
                  ref -> {
                    refs.add(ref);
                    queue.add(
                        new TraversalNode(
                            target.record.id(), link.targetElementId(), cursor.depth + 1));
                  });
        }
      }
      for (ArtifactRecord artifact : workspace.childArtifacts(cursor.modelId)) {
        if (artifactPathsForElement(artifact, cursor.elementId).isEmpty()) {
          continue;
        }
        refs.add(
            new ElementRef(
                artifact.id(),
                "ARTIFACT",
                artifact.name(),
                cursor.modelId,
                cursor.elementId,
                "Generated Artifact",
                artifact.name(),
                "GENERATED_ARTIFACT"));
      }
    }
    return distinct(refs);
  }

  private List<ElementRef> connected(IndexedModel model, String elementId) {
    List<ElementRef> refs = new ArrayList<>();
    JsonNode relationships = model.record.modelJson().path("graph").path("relationships");
    if (!relationships.isArray()) {
      return refs;
    }
    for (JsonNode relationship : relationships) {
      String kind =
          firstText(text(relationship, "kind", ""), text(relationship, "relationshipType", ""));
      if ("TRACE".equalsIgnoreCase(kind)) {
        continue;
      }
      String source =
          firstText(text(relationship, "sourceElementId", ""), text(relationship, "source", ""));
      String target =
          firstText(text(relationship, "targetElementId", ""), text(relationship, "target", ""));
      String peer = "";
      if (elementId.equals(source)) {
        peer = target;
      } else if (elementId.equals(target)) {
        peer = source;
      }
      if (!peer.isBlank()) {
        model.ref(peer, kind.isBlank() ? "CONNECTED" : kind).ifPresent(refs::add);
      }
    }
    return distinct(refs);
  }

  private List<String> artifactPathsForElement(ArtifactRecord artifact, String elementId) {
    JsonNode traceability = artifact.modelJson().path("traceability");
    if (!traceability.isObject()) {
      return List.of();
    }
    JsonNode paths = traceability.path(elementId);
    if (!paths.isArray()) {
      return List.of();
    }
    List<String> values = new ArrayList<>();
    for (JsonNode path : paths) {
      String value = path.asText("");
      if (!value.isBlank()) {
        values.add(value);
      }
    }
    return values;
  }

  private Workspace workspace(UserRecord user, String projectId) {
    Map<String, IndexedModel> indexedModels = new LinkedHashMap<>();
    for (ModelLevel level : ModelLevel.values()) {
      for (ModelRecord model : models.list(user, level, projectId)) {
        indexedModels.put(model.id(), new IndexedModel(model));
      }
    }
    List<ArtifactRecord> projectArtifacts = artifacts.list(user, projectId);
    return new Workspace(indexedModels, projectArtifacts);
  }

  private ElementRef missingRef(ModelRecord model, String elementId) {
    return new ElementRef(
        model.id(),
        model.level().name(),
        model.name(),
        text(model.modelJson(), "sourceModelId", null),
        elementId,
        "Model Element",
        elementId,
        "FOCAL_ELEMENT");
  }

  private List<ElementRef> distinct(List<ElementRef> refs) {
    Map<String, ElementRef> byKey = new LinkedHashMap<>();
    for (ElementRef ref : refs) {
      byKey.putIfAbsent(ref.modelId() + "|" + ref.elementId() + "|" + ref.relationship(), ref);
    }
    return new ArrayList<>(byKey.values());
  }

  private static String text(JsonNode node, String field, String fallback) {
    JsonNode value = node == null ? null : node.get(field);
    if (value == null || value.isNull()) {
      return fallback;
    }
    String text = value.asText("");
    return text.isBlank() ? fallback : text;
  }

  private static String firstText(String... values) {
    for (String value : values) {
      if (value != null && !value.isBlank()) {
        return value;
      }
    }
    return "";
  }

  private static void addAlias(Set<String> aliases, JsonNode node, String field) {
    String value = text(node, field, "");
    if (!value.isBlank()) {
      aliases.add(value);
    }
  }

  private record Workspace(Map<String, IndexedModel> models, List<ArtifactRecord> artifacts) {
    IndexedModel model(String modelId) {
      return models.get(modelId);
    }

    List<IndexedModel> childModels(String sourceModelId) {
      return models.values().stream()
          .filter(
              model -> sourceModelId.equals(text(model.record.modelJson(), "sourceModelId", "")))
          .sorted(Comparator.comparing(model -> model.record.level().ordinal()))
          .toList();
    }

    List<ArtifactRecord> childArtifacts(String sourceModelId) {
      return artifacts.stream()
          .filter(artifact -> sourceModelId.equals(text(artifact.modelJson(), "sourceModelId", "")))
          .toList();
    }
  }

  private static final class IndexedModel {
    private final ModelRecord record;
    private final Map<String, JsonNode> elementsById = new LinkedHashMap<>();
    private final Map<String, Set<String>> aliasesById = new LinkedHashMap<>();
    private final List<TraceLink> traces = new ArrayList<>();

    IndexedModel(ModelRecord record) {
      this.record = record;
      indexElement(record.modelJson());
      JsonNode graphElements = record.modelJson().path("graph").path("elements");
      if (graphElements.isArray()) {
        graphElements.forEach(this::indexElement);
      }
      collectElements(record.modelJson());
      collectTraces(record.modelJson());
    }

    OptionalRef ref(String elementId, String relationship) {
      String resolved = resolveAlias(elementId);
      JsonNode node = resolved.isBlank() ? null : elementsById.get(resolved);
      if (node == null) {
        return OptionalRef.empty();
      }
      String type = firstText(text(node, "eClass", ""), text(node, "type", ""), "Model Element");
      String name =
          firstText(
              text(node, "label", ""),
              text(node, "displayName", ""),
              text(node, "name", ""),
              text(node, "id", ""),
              resolved);
      return OptionalRef.of(
          new ElementRef(
              record.id(),
              record.level().name(),
              record.name(),
              text(record.modelJson(), "sourceModelId", null),
              resolved,
              type,
              name,
              relationship));
    }

    List<TraceLink> forwardTrace(String sourceElementId) {
      Set<String> sourceAliases = aliases(sourceElementId);
      return traces.stream()
          .filter(trace -> sourceAliases.contains(trace.sourceElementId()))
          .filter(trace -> !trace.targetElementId().isBlank())
          .toList();
    }

    List<TraceLink> reverseTrace(String targetElementId) {
      Set<String> targetAliases = aliases(targetElementId);
      return traces.stream()
          .filter(trace -> targetAliases.contains(trace.targetElementId()))
          .filter(trace -> !trace.sourceElementId().isBlank())
          .toList();
    }

    List<TraceLink> generatedFromMatches(String sourceElementId) {
      Set<String> sourceAliases = aliases(sourceElementId);
      List<TraceLink> links = new ArrayList<>();
      for (Map.Entry<String, JsonNode> entry : elementsById.entrySet()) {
        String generatedFrom = text(entry.getValue(), "generatedFrom", "");
        if (sourceAliases.contains(generatedFrom)) {
          links.add(
              new TraceLink(sourceElementId, entry.getKey(), "GENERATED_FROM", "generatedFrom"));
        }
      }
      return links;
    }

    String generatedFrom(String elementId) {
      String resolved = resolveAlias(elementId);
      JsonNode node = elementsById.get(resolved);
      return text(node, "generatedFrom", "");
    }

    private Set<String> aliases(String elementId) {
      String resolved = resolveAlias(elementId);
      Set<String> aliases = new LinkedHashSet<>();
      if (elementId != null && !elementId.isBlank()) {
        aliases.add(elementId);
      }
      if (!resolved.isBlank()) {
        aliases.add(resolved);
        aliases.addAll(aliasesById.getOrDefault(resolved, Set.of()));
      }
      return aliases;
    }

    private String resolveAlias(String alias) {
      if (alias == null || alias.isBlank()) {
        return "";
      }
      if (elementsById.containsKey(alias)) {
        return alias;
      }
      for (Map.Entry<String, Set<String>> entry : aliasesById.entrySet()) {
        if (entry.getValue().contains(alias)) {
          return entry.getKey();
        }
      }
      return alias;
    }

    private void collectElements(JsonNode node) {
      if (node == null || node.isNull()) {
        return;
      }
      if (node.isObject()) {
        indexElement(node);
        node.fields().forEachRemaining(entry -> collectElements(entry.getValue()));
      } else if (node.isArray()) {
        node.forEach(this::collectElements);
      }
    }

    private void indexElement(JsonNode node) {
      String id = text(node, "id", "");
      if (id.isBlank()) {
        return;
      }
      elementsById.putIfAbsent(id, node);
      Set<String> aliases = aliasesById.computeIfAbsent(id, ignored -> new LinkedHashSet<>());
      aliases.add(id);
      for (String field :
          List.of(
              "traceId",
              "externalId",
              "sourceQualifiedName",
              "name",
              "displayName",
              "label",
              "logicalId",
              "physicalName",
              "generatedFrom")) {
        addAlias(aliases, node, field);
      }
    }

    private void collectTraces(JsonNode node) {
      if (node == null || node.isNull()) {
        return;
      }
      if (node.isObject()) {
        TraceLink.from(node).ifPresent(traces::add);
        node.fields().forEachRemaining(entry -> collectTraces(entry.getValue()));
      } else if (node.isArray()) {
        node.forEach(this::collectTraces);
      }
    }
  }

  private record TraversalNode(String modelId, String elementId, int depth) {}

  private record TraceLink(
      String sourceElementId,
      String targetElementId,
      String relationship,
      String transformationRule) {
    static OptionalRefTrace from(JsonNode node) {
      String source = firstText(text(node, "sourceElementId", ""), text(node, "source", ""));
      String target = firstText(text(node, "targetElementId", ""), text(node, "target", ""));
      if (source.isBlank() || target.isBlank()) {
        return OptionalRefTrace.empty();
      }
      String relationship =
          firstText(
              text(node, "linkType", ""),
              text(node, "kind", ""),
              text(node, "relationshipType", ""));
      String rule = text(node, "transformationRule", "");
      if (relationship.isBlank() && rule.isBlank()) {
        return OptionalRefTrace.empty();
      }
      return OptionalRefTrace.of(
          new TraceLink(source, target, firstText(relationship, rule), rule));
    }
  }

  private record OptionalRef(ElementRef value) {
    static OptionalRef of(ElementRef value) {
      return new OptionalRef(value);
    }

    static OptionalRef empty() {
      return new OptionalRef(null);
    }

    void ifPresent(java.util.function.Consumer<ElementRef> consumer) {
      if (value != null) {
        consumer.accept(value);
      }
    }

    ElementRef orElseGet(java.util.function.Supplier<ElementRef> supplier) {
      return value == null ? supplier.get() : value;
    }
  }

  private record OptionalRefTrace(TraceLink value) {
    static OptionalRefTrace of(TraceLink value) {
      return new OptionalRefTrace(value);
    }

    static OptionalRefTrace empty() {
      return new OptionalRefTrace(null);
    }

    void ifPresent(java.util.function.Consumer<TraceLink> consumer) {
      if (value != null) {
        consumer.accept(value);
      }
    }
  }

  public record ElementImpactResponse(
      ElementRef focalElement,
      List<ElementRef> upstream,
      List<ElementRef> downstream,
      List<ElementRef> connectedElements) {}

  public record ArtifactImpactResponse(
      String modelId,
      String modelType,
      String modelName,
      JsonNode metadata,
      List<ModelAncestor> ancestors) {}

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
