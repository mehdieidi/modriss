package io.mehdieidi.modriss.platform.model.application;

import io.mehdieidi.modriss.platform.identity.domain.UserRecord;
import io.mehdieidi.modriss.platform.kernel.ModelLevel;
import io.mehdieidi.modriss.platform.kernel.PlatformException;
import io.mehdieidi.modriss.platform.model.domain.ModelRecord;
import io.mehdieidi.modriss.platform.modeling.layout.LayoutService;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Applies backend layout to a persisted model view and stores the resulting node positions and edge
 * pins back into the model JSON.
 */
public final class StoredViewLayoutService {

  /** Default CIM node width used when a view node has no positive width. */
  private static final double CIM_NODE_WIDTH = 176.0d;

  /** Default CIM node height used when a view node has no positive height. */
  private static final double CIM_NODE_HEIGHT = 96.0d;

  /** Default non-CIM node width used when a view node has no positive width. */
  private static final double DEFAULT_NODE_WIDTH = 228.0d;

  /** Default non-CIM node height used when a view node has no positive height. */
  private static final double DEFAULT_NODE_HEIGHT = 112.0d;

  /** Version of the persisted node/edge coordinate contract used by the canvas renderer. */
  private static final int LAYOUT_GEOMETRY_VERSION = 3;

  /** Model service used to load and persist model JSON. */
  private final ModelService models;

  /** Stateless ELK layout adapter. */
  private final LayoutService layouts;

  /**
   * Creates a stored-view layout service.
   *
   * @param models model persistence service
   * @param layouts backend layout service
   */
  public StoredViewLayoutService(ModelService models, LayoutService layouts) {
    this.models = models;
    this.layouts = layouts;
  }

  /**
   * Computes and persists layout for a stored view unless a previous automatic layout is still
   * valid and force mode is disabled.
   *
   * @param user requesting user
   * @param level model level
   * @param modelId model identifier
   * @param viewId view identifier
   * @param force whether to reapply layout even if already applied
   * @param layoutStrategy legacy strategy hint; all values use the canonical layered layout
   * @return stored-view layout response
   */
  public StoredViewLayoutResponse layout(
      UserRecord user,
      ModelLevel level,
      String modelId,
      String viewId,
      boolean force,
      String layoutStrategy) {
    ModelRecord stored = models.get(user, level, modelId);
    ObjectNode model =
        requireObject(stored.modelJson(), "Stored model JSON is invalid.").deepCopy();
    ObjectNode view = findView(model, viewId);
    if (!force
        && view.path("autoLayoutApplied").asBoolean(false)
        && view.path("layoutGeometryVersion").asInt(0) == LAYOUT_GEOMETRY_VERSION) {
      return response(stored, view, false, List.of());
    }

    String strategy = "LAYERED";
    LayoutService.LayoutRequest request = buildRequest(level, model, view);
    LayoutService.LayoutResponse result = layouts.layout(request);
    verifyComplete(request, result);
    applyLayout(view, request, result, strategy);
    ModelRecord updated =
        models.update(user, level, modelId, stored.name(), model, stored.revision());
    ObjectNode persistedView =
        findView(requireObject(updated.modelJson(), "Updated model JSON is invalid."), viewId);
    return response(updated, persistedView, true, result.warnings());
  }

  /**
   * Builds the layout request from visible nodes and edges in a stored view.
   *
   * @param level model level
   * @param model model JSON
   * @param view view JSON object
   * @return layout service request
   */
  private LayoutService.LayoutRequest buildRequest(
      ModelLevel level, ObjectNode model, ObjectNode view) {
    Map<String, JsonNode> elements = indexById(model.path("graph").path("elements"), "id");
    Map<String, JsonNode> relationships =
        indexById(model.path("graph").path("relationships"), "id");
    Set<String> hiddenNodeIds = textSet(view.path("hidden").path("elementIds"));
    Set<String> hiddenEdgeIds = textSet(view.path("hidden").path("relationshipIds"));
    double defaultWidth = level == ModelLevel.CIM ? CIM_NODE_WIDTH : DEFAULT_NODE_WIDTH;
    double defaultHeight = level == ModelLevel.CIM ? CIM_NODE_HEIGHT : DEFAULT_NODE_HEIGHT;

    List<LayoutService.LayoutNode> nodes = new ArrayList<>();
    Map<String, Object> portInsets = new LinkedHashMap<>();
    Set<String> nodeIds = new HashSet<>();
    for (JsonNode viewNode : view.path("nodes")) {
      String id = text(viewNode, "elementId", text(viewNode, "id", ""));
      if (id.isBlank() || hiddenNodeIds.contains(id) || !nodeIds.add(id)) {
        continue;
      }
      JsonNode element = elements.get(id);
      if (element == null) {
        throw new PlatformException(409, "Stored view references missing element '" + id + "'.");
      }
      JsonNode insets = viewNode.path("layoutPortInsets");
      if (insets.isObject()) {
        portInsets.put(
            id,
            Map.of(
                "top", positive(insets.path("top").asDouble(8), 8),
                "bottom", positive(insets.path("bottom").asDouble(8), 8)));
      }
      nodes.add(
          new LayoutService.LayoutNode(
              id,
              text(element, "name", text(element, "label", id)),
              positive(viewNode.path("width").asDouble(defaultWidth), defaultWidth),
              positive(viewNode.path("height").asDouble(defaultHeight), defaultHeight),
              finite(viewNode.get("x")),
              finite(viewNode.get("y")),
              List.of()));
    }

    List<LayoutService.LayoutEdge> edges = new ArrayList<>();
    Set<String> edgeIds = new HashSet<>();
    for (JsonNode viewEdge : view.path("edges")) {
      String id = text(viewEdge, "relationshipId", text(viewEdge, "id", ""));
      if (id.isBlank()
          || hiddenEdgeIds.contains(id)
          || !edgeIds.add(id)
          || (viewEdge.has("visible") && !viewEdge.path("visible").asBoolean(true))) {
        continue;
      }
      JsonNode relationship = relationships.get(id);
      if (relationship == null) {
        // The frontend derives edges from Ecore references. Their endpoints belong to
        // view presentation, while the references themselves stay in semantic model data.
        if (!viewEdge.hasNonNull("sourceElementId") || !viewEdge.hasNonNull("targetElementId")) {
          continue;
        }
        relationship = viewEdge;
      }
      String sourceId = endpoint(relationship, "sourceElementId", "sourceId", "source");
      String targetId = endpoint(relationship, "targetElementId", "targetId", "target");
      if (sourceId.isBlank() || targetId.isBlank()) {
        continue;
      }
      if (!nodeIds.contains(sourceId) || !nodeIds.contains(targetId)) {
        throw new PlatformException(
            409,
            "Stored view is incomplete for relationship '"
                + id
                + "'. Save the model again before auto layout.");
      }
      edges.add(
          new LayoutService.LayoutEdge(
              id,
              text(relationship, "kind", text(relationship, "label", "")),
              sourceId,
              targetId,
              null,
              null));
    }
    return new LayoutService.LayoutRequest(
        text(view, "id", ""),
        text(view, "layoutProfile", "DEFAULT_LAYERED"),
        false,
        List.of(),
        Map.of("portInsetsByNodeId", portInsets),
        nodes,
        edges);
  }

  /**
   * Writes computed node positions and deterministic orthogonal edge pins back into the stored
   * view.
   *
   * @param view view JSON object being updated
   * @param request layout request
   * @param result layout response
   * @param strategy normalized layout strategy
   */
  private void applyLayout(
      ObjectNode view,
      LayoutService.LayoutRequest request,
      LayoutService.LayoutResponse result,
      String strategy) {
    Map<String, NodeBox> nodesById = new HashMap<>();
    result
        .nodes()
        .forEach(
            node ->
                nodesById.put(
                    node.id(),
                    new NodeBox(
                        node.id(),
                        Math.round(node.x()),
                        Math.round(node.y()),
                        node.width(),
                        node.height())));
    for (JsonNode node : view.path("nodes")) {
      if (!(node instanceof ObjectNode objectNode)) {
        continue;
      }
      String id = text(node, "elementId", text(node, "id", ""));
      NodeBox positioned = nodesById.get(id);
      if (positioned == null) {
        continue;
      }
      objectNode.put("x", Math.round(positioned.x));
      objectNode.put("y", Math.round(positioned.y));
      objectNode.put("width", positioned.width);
      objectNode.put("height", positioned.height);
    }

    Map<String, LayoutService.RoutedEdge> edgesById = new HashMap<>();
    result.edges().forEach(edge -> edgesById.put(edge.id(), edge));
    Map<String, LayoutService.LayoutEdge> requestsById = new HashMap<>();
    request.edges().forEach(edge -> requestsById.put(edge.id(), edge));
    for (JsonNode edge : view.path("edges")) {
      if (!(edge instanceof ObjectNode objectEdge)) {
        continue;
      }
      String id = text(edge, "relationshipId", text(edge, "id", ""));
      if (!edgesById.containsKey(id) || !requestsById.containsKey(id)) {
        continue;
      }
      LayoutService.LayoutEdge edgeRequest = requestsById.get(id);
      NodeBox source = nodesById.get(edgeRequest.sourceNodeId());
      NodeBox target = nodesById.get(edgeRequest.targetNodeId());
      if (source == null || target == null) {
        continue;
      }
      writeLayoutServiceEdge(objectEdge, edgesById.get(id), source, target);
    }
    view.put("autoLayoutApplied", true);
    view.put("layoutStrategy", strategy);
    view.put("layoutGeometryVersion", LAYOUT_GEOMETRY_VERSION);
  }

  /**
   * Writes routed edge geometry produced by the shared layout service.
   *
   * @param edge stored edge JSON
   * @param routed routed edge from layout service
   * @param source source node box
   * @param target target node box
   */
  private void writeLayoutServiceEdge(
      ObjectNode edge, LayoutService.RoutedEdge routed, NodeBox source, NodeBox target) {
    if (routed == null || routed.sections().isEmpty()) {
      return;
    }
    LayoutService.EdgeSection section = routed.sections().get(0);
    writeAnchorFromPoint(
        edge, "sourceAnchor", source, section.startPoint().x(), section.startPoint().y());
    writeAnchorFromPoint(
        edge, "targetAnchor", target, section.endPoint().x(), section.endPoint().y());
    ArrayNode pinPoints = edge.putArray("pinPoints");
    List<LayoutService.LayoutPoint> bends =
        routed.bendPoints().isEmpty() ? section.bendPoints() : routed.bendPoints();
    bends.forEach(
        point -> {
          ObjectNode pin = pinPoints.addObject();
          pin.put("x", Math.round(point.x()));
          pin.put("y", Math.round(point.y()));
        });
  }

  /**
   * Writes an anchor derived from an absolute route point on a node boundary.
   *
   * @param edge edge JSON object
   * @param field target field name
   * @param node positioned node box
   * @param pointX absolute x coordinate
   * @param pointY absolute y coordinate
   */
  private void writeAnchorFromPoint(
      ObjectNode edge, String field, NodeBox node, double pointX, double pointY) {
    String side = pointX >= node.x + node.width / 2.0d ? "right" : "left";
    double offsetY = Math.round(pointY) - node.y;
    ObjectNode anchor = edge.putObject(field);
    anchor.put("side", side);
    anchor.put("offsetY", Math.round(offsetY));
  }

  /**
   * Verifies that the layout response contains every visible node and edge requested.
   *
   * @param request layout request
   * @param result layout response
   */
  private void verifyComplete(
      LayoutService.LayoutRequest request, LayoutService.LayoutResponse result) {
    Set<String> nodeIds = new HashSet<>();
    result.nodes().forEach(node -> nodeIds.add(node.id()));
    Set<String> edgeIds = new HashSet<>();
    result.edges().forEach(edge -> edgeIds.add(edge.id()));
    List<String> missingNodes =
        request.nodes().stream()
            .map(LayoutService.LayoutNode::id)
            .filter(id -> !nodeIds.contains(id))
            .toList();
    List<String> missingEdges =
        request.edges().stream()
            .map(LayoutService.LayoutEdge::id)
            .filter(id -> !edgeIds.contains(id))
            .toList();
    if (!missingNodes.isEmpty() || !missingEdges.isEmpty()) {
      throw new PlatformException(500, "Backend layout omitted visible view content.");
    }
  }

  /**
   * Creates a response from the persisted view.
   *
   * @param model persisted model record
   * @param view persisted view JSON
   * @param applied whether layout was applied during this call
   * @param warnings layout warnings
   * @return stored-view response
   */
  private StoredViewLayoutResponse response(
      ModelRecord model, ObjectNode view, boolean applied, List<String> warnings) {
    int nodeCount = view.path("nodes").size();
    int edgeCount = view.path("edges").size();
    return new StoredViewLayoutResponse(
        model.id(),
        model.level(),
        model.revision(),
        view.deepCopy(),
        applied,
        nodeCount,
        edgeCount,
        warnings);
  }

  /**
   * Finds a mutable view object by id.
   *
   * @param model model JSON
   * @param viewId view identifier
   * @return matching view object
   */
  private ObjectNode findView(ObjectNode model, String viewId) {
    for (JsonNode candidate : model.path("views")) {
      if (candidate instanceof ObjectNode objectNode && text(candidate, "id", "").equals(viewId)) {
        return objectNode;
      }
    }
    throw new PlatformException(404, "View not found in stored model: " + viewId);
  }

  /**
   * Requires a JSON value to be an object node.
   *
   * @param value JSON value
   * @param message error message
   * @return object node
   */
  private ObjectNode requireObject(JsonNode value, String message) {
    if (value instanceof ObjectNode objectNode) {
      return objectNode;
    }
    throw new PlatformException(400, message);
  }

  /**
   * Indexes array items by a text field.
   *
   * @param values array of JSON objects
   * @param field id field name
   * @return values keyed by non-blank id
   */
  private Map<String, JsonNode> indexById(JsonNode values, String field) {
    Map<String, JsonNode> result = new LinkedHashMap<>();
    if (values.isArray()) {
      values.forEach(
          value -> {
            String id = text(value, field, "");
            if (!id.isBlank()) {
              result.put(id, value);
            }
          });
    }
    return result;
  }

  /**
   * Converts a JSON string array to a set of non-blank text values.
   *
   * @param values JSON array
   * @return text set
   */
  private Set<String> textSet(JsonNode values) {
    Set<String> result = new HashSet<>();
    if (values.isArray()) {
      values.forEach(
          value -> {
            if (value.isTextual() && !value.asText().isBlank()) {
              result.add(value.asText());
            }
          });
    }
    return result;
  }

  /**
   * Resolves a relationship endpoint from preferred, secondary, or nested reference fields.
   *
   * @param relationship relationship JSON
   * @param primary primary endpoint field
   * @param secondary secondary endpoint field
   * @param fallback nested endpoint field
   * @return endpoint id or empty string
   */
  private String endpoint(
      JsonNode relationship, String primary, String secondary, String fallback) {
    String value = text(relationship, primary, text(relationship, secondary, ""));
    if (!value.isBlank()) {
      return value;
    }
    JsonNode endpoint = relationship == null ? null : relationship.get(fallback);
    if (endpoint != null && endpoint.isObject()) {
      return text(endpoint, "$ref", text(endpoint, "id", ""));
    }
    return endpoint == null ? "" : endpoint.asText("");
  }

  /**
   * Reads a text field with fallback handling for null or missing nodes.
   *
   * @param value JSON object
   * @param field field name
   * @param fallback fallback text
   * @return field text or fallback
   */
  private String text(JsonNode value, String field, String fallback) {
    if (value == null || value.isMissingNode() || value.isNull()) {
      return fallback;
    }
    JsonNode child = value.get(field);
    return child == null || child.isNull() ? fallback : child.asText(fallback);
  }

  /**
   * Reads a finite numeric JSON value.
   *
   * @param value JSON numeric value
   * @return finite double or {@code null}
   */
  private Double finite(JsonNode value) {
    if (value == null || !value.isNumber()) {
      return null;
    }
    double number = value.asDouble();
    return Double.isFinite(number) ? number : null;
  }

  /**
   * Returns a positive finite value or a fallback.
   *
   * @param value candidate value
   * @param fallback fallback value
   * @return positive finite value
   */
  private double positive(double value, double fallback) {
    return Double.isFinite(value) && value > 0 ? value : fallback;
  }

  /**
   * Response returned after reading or applying stored view layout.
   *
   * @param modelId model identifier
   * @param level model level
   * @param revision model revision after layout
   * @param view persisted view JSON
   * @param layoutApplied whether layout was applied during this call
   * @param nodeCount number of view nodes
   * @param edgeCount number of view edges
   * @param warnings non-fatal layout warnings
   */
  public record StoredViewLayoutResponse(
      String modelId,
      ModelLevel level,
      long revision,
      JsonNode view,
      boolean layoutApplied,
      int nodeCount,
      int edgeCount,
      List<String> warnings) {

    /** Normalizes nullable warnings to an immutable empty list. */
    public StoredViewLayoutResponse {
      warnings = warnings == null ? List.of() : List.copyOf(warnings);
    }
  }

  /**
   * Mutable-friendly positioned node box used while post-processing routes.
   *
   * @param id node identifier
   * @param x x coordinate
   * @param y y coordinate
   * @param width node width
   * @param height node height
   */
  private record NodeBox(String id, double x, double y, double width, double height) {}
}
