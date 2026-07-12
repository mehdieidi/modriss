package io.mehdieidi.varka.platform.model.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.mehdieidi.varka.platform.identity.domain.UserRecord;
import io.mehdieidi.varka.platform.kernel.ModelLevel;
import io.mehdieidi.varka.platform.kernel.PlatformException;
import io.mehdieidi.varka.platform.model.domain.ModelRecord;
import io.mehdieidi.varka.platform.modeling.layout.LayoutService;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

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

  /** Canonical west-side input port id used by automatic layout. */
  private static final String INPUT_PORT_ID = "flow-in";

  /** Canonical east-side output port id used by automatic layout. */
  private static final String OUTPUT_PORT_ID = "flow-out";

  /** Default rendered size for canonical layout ports. */
  private static final double LAYOUT_PORT_SIZE = 10.0d;

  /** Horizontal distance from a node anchor before an edge enters a routing corridor. */
  private static final double ROUTE_STUB = 56.0d;

  /** Distance between candidate edge corridors while avoiding previously routed edges. */
  private static final double ROUTE_LANE_STEP = 34.0d;

  /** Maximum number of alternative corridors considered for one edge. */
  private static final int MAX_ROUTE_ATTEMPTS = 512;

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
   * @param layoutStrategy requested strategy name
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
    if (!force && view.path("autoLayoutApplied").asBoolean(false)) {
      return response(stored, view, false, List.of());
    }

    String strategy =
        normalizeStrategy(layoutStrategy, text(view, "layoutStrategy", "SPACIOUS_LAYERED"));
    LayoutService.LayoutRequest request = buildRequest(level, model, view, strategy);
    LayoutService.LayoutResponse result = layouts.layout(request);
    verifyComplete(request, result);
    applyLayout(model, view, request, result, strategy);
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
   * @param layoutStrategy normalized layout strategy
   * @return layout service request
   */
  private LayoutService.LayoutRequest buildRequest(
      ModelLevel level, ObjectNode model, ObjectNode view, String layoutStrategy) {
    Map<String, JsonNode> elements = indexById(model.path("graph").path("elements"), "id");
    Map<String, JsonNode> relationships =
        indexById(model.path("graph").path("relationships"), "id");
    Set<String> hiddenNodeIds = textSet(view.path("hidden").path("elementIds"));
    Set<String> hiddenEdgeIds = textSet(view.path("hidden").path("relationshipIds"));
    double defaultWidth = level == ModelLevel.CIM ? CIM_NODE_WIDTH : DEFAULT_NODE_WIDTH;
    double defaultHeight = level == ModelLevel.CIM ? CIM_NODE_HEIGHT : DEFAULT_NODE_HEIGHT;

    List<LayoutService.LayoutNode> nodes = new ArrayList<>();
    Set<String> nodeIds = new HashSet<>();
    for (JsonNode viewNode : view.path("nodes")) {
      String id = text(viewNode, "elementId", text(viewNode, "id", ""));
      if (id.isBlank() || hiddenNodeIds.contains(id) || !nodeIds.add(id)) {
        continue;
      }
      JsonNode element = elements.get(id);
      nodes.add(
          new LayoutService.LayoutNode(
              id,
              text(element, "name", text(element, "label", id)),
              positive(viewNode.path("width").asDouble(defaultWidth), defaultWidth),
              positive(viewNode.path("height").asDouble(defaultHeight), defaultHeight),
              finite(viewNode.get("x")),
              finite(viewNode.get("y")),
              canonicalPorts(
                  positive(viewNode.path("width").asDouble(defaultWidth), defaultWidth),
                  positive(viewNode.path("height").asDouble(defaultHeight), defaultHeight))));
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
        continue;
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
              text(relationship, "kind", ""),
              sourceId,
              targetId,
              OUTPUT_PORT_ID,
              INPUT_PORT_ID));
    }
    Map<String, Object> options = new LinkedHashMap<>(layoutOptions(layoutStrategy));
    List<List<String>> semanticDashboardColumns =
        textColumns(view.path("semanticDashboardColumns"));
    if (!semanticDashboardColumns.isEmpty()) {
      options.put("semanticDashboardColumns", semanticDashboardColumns);
    }
    return new LayoutService.LayoutRequest(
        text(view, "id", ""),
        text(view, "layoutProfile", "DEFAULT_LAYERED"),
        false,
        List.of(),
        options,
        nodes,
        edges);
  }

  /**
   * Writes computed node positions and deterministic orthogonal edge pins back into the stored
   * view.
   *
   * @param model model JSON being updated
   * @param view view JSON object being updated
   * @param request layout request
   * @param result layout response
   * @param strategy normalized layout strategy
   */
  private void applyLayout(
      ObjectNode model,
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
                    new NodeBox(node.id(), node.x(), node.y(), node.width(), node.height())));
    Map<String, JsonNode> elements = indexById(model.path("graph").path("elements"), "id");
    if (shouldUseDenseDashboardGrid(view, request)) {
      applyDenseDashboardGrid(nodesById, elements, request);
    }
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
    boolean denseDashboard = shouldUseDenseDashboardGrid(view, request);
    Map<String, EdgeRoute> routesById = new HashMap<>();
    if (!denseDashboard) {
      List<EdgeRoute> routes = new ArrayList<>();
      for (LayoutService.LayoutEdge edgeRequest : request.edges()) {
        NodeBox source = nodesById.get(edgeRequest.sourceNodeId());
        NodeBox target = nodesById.get(edgeRequest.targetNodeId());
        if (source == null || target == null) {
          continue;
        }
        routes.add(orthogonalRoute(edgeRequest, source, target));
      }
      spreadAnchors(nodesById, routes);
      assignSeparatedPinPoints(nodesById, routes);
      routes.forEach(route -> routesById.put(route.id, route));
    }
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
      if (denseDashboard) {
        writeLayoutServiceEdge(objectEdge, edgesById.get(id), source, target);
        continue;
      }
      EdgeRoute routed = routesById.get(id);
      if (routed == null) {
        continue;
      }
      writeAnchor(objectEdge, "sourceAnchor", routed.sourceAnchor);
      writeAnchor(objectEdge, "targetAnchor", routed.targetAnchor);
      ArrayNode pinPoints = objectEdge.putArray("pinPoints");
      routed.pinPoints.forEach(
          point -> {
            ObjectNode pin = pinPoints.addObject();
            pin.put("x", Math.round(point.x));
            pin.put("y", Math.round(point.y));
          });
    }
    view.put("autoLayoutApplied", true);
    view.put("layoutStrategy", strategy);
  }

  /**
   * Creates an orthogonal route between two positioned node boxes.
   *
   * @param edge edge request
   * @param source source node box
   * @param target target node box
   * @return edge route
   */
  private EdgeRoute orthogonalRoute(LayoutService.LayoutEdge edge, NodeBox source, NodeBox target) {
    if (source.id.equals(target.id)) {
      Anchor sourceAnchor = new Anchor("right", source.height / 2.0d);
      Anchor targetAnchor = new Anchor("left", source.height / 2.0d);
      return new EdgeRoute(
          edge.id(), source.id, target.id, sourceAnchor, targetAnchor, new ArrayList<>());
    }

    Anchor sourceAnchor = new Anchor("right", source.height / 2.0d);
    Anchor targetAnchor = new Anchor("left", target.height / 2.0d);
    return new EdgeRoute(
        edge.id(), source.id, target.id, sourceAnchor, targetAnchor, new ArrayList<>());
  }

  /**
   * Detects large dashboard-like views that benefit from deterministic semantic columns after ELK
   * sizing.
   *
   * @param view stored view JSON
   * @param request layout request
   * @return {@code true} when dense grid post-processing should run
   */
  private boolean shouldUseDenseDashboardGrid(
      ObjectNode view, LayoutService.LayoutRequest request) {
    if (!Boolean.TRUE.equals(request.options().get("semanticDashboardGrid"))) {
      return false;
    }
    if (dashboardColumns(request).isEmpty()) {
      return false;
    }
    String profile = text(view, "layoutProfile", request.profile()).toUpperCase();
    String kind = text(view, "kind", "").toUpperCase();
    return request.nodes().size() >= 36
        && (profile.contains("DASHBOARD") || kind.contains("DASHBOARD"));
  }

  /**
   * Repositions large dashboard views into fixed semantic columns.
   *
   * @param nodesById positioned nodes keyed by id
   * @param elements graph elements keyed by id
   * @param request layout request
   */
  private void applyDenseDashboardGrid(
      Map<String, NodeBox> nodesById,
      Map<String, JsonNode> elements,
      LayoutService.LayoutRequest request) {
    List<List<String>> columns = dashboardColumns(request);
    if (columns.isEmpty()) {
      return;
    }
    Map<String, Integer> columnByType = new HashMap<>();
    for (int index = 0; index < columns.size(); index++) {
      for (String type : columns.get(index)) {
        columnByType.put(type, index);
      }
    }
    List<List<NodeBox>> buckets = new ArrayList<>();
    for (int index = 0; index < columns.size(); index++) {
      buckets.add(new ArrayList<>());
    }
    List<NodeBox> overflow = new ArrayList<>();
    for (LayoutService.LayoutNode node : request.nodes()) {
      NodeBox box = nodesById.get(node.id());
      if (box == null) {
        continue;
      }
      String type =
          text(elements.get(node.id()), "eClass", text(elements.get(node.id()), "type", ""));
      Integer column = columnByType.get(type);
      if (column == null) {
        overflow.add(box);
      } else {
        buckets.get(column).add(box);
      }
    }
    if (!overflow.isEmpty()) {
      buckets.get(buckets.size() - 1).addAll(overflow);
    }
    for (List<NodeBox> bucket : buckets) {
      bucket.sort(
          Comparator.comparing(
              box ->
                  text(elements.get(box.id), "name", text(elements.get(box.id), "label", box.id))));
    }
    double columnSpacing = 430.0d;
    double rowSpacing = 178.0d;
    double top = 80.0d;
    double left = 80.0d;
    for (int column = 0; column < buckets.size(); column++) {
      List<NodeBox> bucket = buckets.get(column);
      double y = top;
      if (bucket.size() < maxBucketSize(buckets)) {
        y += (maxBucketSize(buckets) - bucket.size()) * rowSpacing / 2.0d;
      }
      for (NodeBox box : bucket) {
        nodesById.put(
            box.id, new NodeBox(box.id, left + column * columnSpacing, y, box.width, box.height));
        y += rowSpacing;
      }
    }
  }

  /**
   * Returns the largest semantic-column bucket size.
   *
   * @param buckets semantic buckets
   * @return maximum bucket size
   */
  private int maxBucketSize(List<List<NodeBox>> buckets) {
    return buckets.stream().mapToInt(List::size).max().orElse(1);
  }

  /**
   * Spreads edge anchor offsets so parallel routes do not all attach to the same point.
   *
   * @param nodesById positioned nodes keyed by id
   * @param routes mutable edge routes
   */
  private void spreadAnchors(Map<String, NodeBox> nodesById, List<EdgeRoute> routes) {
    for (NodeBox node : nodesById.values()) {
      spreadAnchorsFor(node, routes, true);
      spreadAnchorsFor(node, routes, false);
    }
    routes.forEach(
        route -> {
          NodeBox source = nodesById.get(route.sourceNodeId);
          NodeBox target = nodesById.get(route.targetNodeId);
          if (source == null || target == null || route.pinPoints.isEmpty()) {
            return;
          }
          Point start = pointForAnchor(source, route.sourceAnchor);
          Point end = pointForAnchor(target, route.targetAnchor);
          route.pinPoints.get(0).y = start.y;
          route.pinPoints.get(route.pinPoints.size() - 1).y = end.y;
        });
  }

  /**
   * Assigns deterministic pin points while preventing routed edge segments from overlapping.
   *
   * @param nodesById positioned nodes keyed by id
   * @param routes mutable edge routes
   */
  private void assignSeparatedPinPoints(Map<String, NodeBox> nodesById, List<EdgeRoute> routes) {
    List<RouteSegment> occupiedSegments = new ArrayList<>();
    List<EdgeRoute> ordered = new ArrayList<>(routes);
    ordered.sort(Comparator.comparing(route -> route.id));
    int routeIndex = 0;
    for (EdgeRoute route : ordered) {
      NodeBox source = nodesById.get(route.sourceNodeId);
      NodeBox target = nodesById.get(route.targetNodeId);
      if (source == null || target == null) {
        continue;
      }
      int attemptOffset = routeIndex * 5;
      routeIndex += 1;
      List<Point> selected = null;
      for (int attempt = 0; attempt < MAX_ROUTE_ATTEMPTS; attempt++) {
        List<Point> candidate =
            candidatePath(route, source, target, attempt + attemptOffset, routeIndex);
        if (!overlapsExistingSegments(candidate, occupiedSegments)) {
          selected = candidate;
          break;
        }
      }
      if (selected == null) {
        selected =
            candidatePath(route, source, target, MAX_ROUTE_ATTEMPTS + attemptOffset, routeIndex);
      }
      route.pinPoints.clear();
      route.pinPoints.addAll(selected.subList(1, selected.size() - 1));
      occupiedSegments.addAll(segments(route.id, selected));
    }
  }

  /**
   * Builds a candidate orthogonal path through a deterministic edge corridor.
   *
   * @param route edge route
   * @param source source node
   * @param target target node
   * @param attempt candidate attempt
   * @return full path including endpoints
   */
  private List<Point> candidatePath(
      EdgeRoute route, NodeBox source, NodeBox target, int attempt, int routeIndex) {
    Point start = pointForAnchor(source, route.sourceAnchor);
    Point end = pointForAnchor(target, route.targetAnchor);
    int lane = lane(attempt / 2);
    boolean verticalFirst = attempt % 2 == 1;
    double jitter = stableJitter(route.id);
    if (source.id.equals(target.id)) {
      double loopX =
          source.x + source.width + ROUTE_STUB + Math.abs(lane) * ROUTE_LANE_STEP + jitter;
      double loopY = source.y - ROUTE_STUB - Math.max(0, lane) * ROUTE_LANE_STEP - jitter;
      return compactPath(
          List.of(
              start,
              new Point(loopX, start.y),
              new Point(loopX, loopY),
              new Point(end.x, loopY),
              end));
    }
    double corridorY = corridorY(source, target, start, end, lane, jitter, routeIndex);
    double detour = ROUTE_STUB + Math.abs(lane) * ROUTE_LANE_STEP + jitter;
    double sourceX = start.x + detour;
    double targetX = end.x - detour;
    if (same(start.x, end.x)) {
      sourceX = start.x + (lane < 0 ? -detour : detour);
      targetX = sourceX;
    }
    if (verticalFirst) {
      if (same(start.x, end.x)) {
        return compactPath(
            List.of(
                start,
                new Point(start.x, corridorY),
                new Point(sourceX, corridorY),
                new Point(sourceX, end.y),
                end));
      }
      return compactPath(
          List.of(
              start,
              new Point(start.x, corridorY),
              new Point(targetX, corridorY),
              new Point(targetX, end.y),
              end));
    }
    return compactPath(
        List.of(
            start,
            new Point(sourceX, start.y),
            new Point(sourceX, corridorY),
            new Point(targetX, corridorY),
            new Point(targetX, end.y),
            end));
  }

  /**
   * Computes a horizontal corridor y coordinate for a route candidate.
   *
   * @param source source node
   * @param target target node
   * @param start start point
   * @param end end point
   * @param lane signed lane index
   * @param jitter stable route jitter
   * @return corridor y
   */
  private double corridorY(
      NodeBox source,
      NodeBox target,
      Point start,
      Point end,
      int lane,
      double jitter,
      int routeIndex) {
    double routeBand = routeIndex * ROUTE_LANE_STEP;
    if (Math.abs(start.y - end.y) > 80.0d) {
      return Math.round((start.y + end.y) / 2.0d + lane * ROUTE_LANE_STEP + jitter + routeBand);
    }
    double top = Math.min(source.y, target.y);
    double bottom = Math.max(source.y + source.height, target.y + target.height);
    if (lane == 0 || lane > 0) {
      return Math.round(
          top - ROUTE_STUB - Math.max(0, lane - 1) * ROUTE_LANE_STEP - jitter - routeBand);
    }
    return Math.round(
        bottom + ROUTE_STUB + Math.abs(lane + 1) * ROUTE_LANE_STEP + jitter + routeBand);
  }

  /**
   * Removes duplicate and unnecessary collinear points from a path.
   *
   * @param points path points
   * @return compact path
   */
  private List<Point> compactPath(List<Point> points) {
    List<Point> result = new ArrayList<>();
    for (Point point : points) {
      Point rounded = new Point(Math.round(point.x), Math.round(point.y));
      if (!result.isEmpty() && samePoint(result.get(result.size() - 1), rounded)) {
        continue;
      }
      result.add(rounded);
      while (result.size() >= 3 && collinearLastThree(result)) {
        Point last = result.remove(result.size() - 1);
        result.remove(result.size() - 1);
        result.add(last);
      }
    }
    return result;
  }

  /**
   * Returns whether the last three points in a path are collinear.
   *
   * @param points path points
   * @return {@code true} when the middle point can be removed
   */
  private boolean collinearLastThree(List<Point> points) {
    int size = points.size();
    Point first = points.get(size - 3);
    Point middle = points.get(size - 2);
    Point last = points.get(size - 1);
    return (same(first.x, middle.x) && same(middle.x, last.x))
        || (same(first.y, middle.y) && same(middle.y, last.y));
  }

  /**
   * Checks whether a candidate path overlaps any already occupied edge segment.
   *
   * @param candidate candidate path
   * @param occupiedSegments occupied edge segments
   * @return {@code true} when a non-point overlap exists
   */
  private boolean overlapsExistingSegments(
      List<Point> candidate, List<RouteSegment> occupiedSegments) {
    for (RouteSegment candidateSegment : segments("", candidate)) {
      for (RouteSegment occupiedSegment : occupiedSegments) {
        if (candidateSegment.overlaps(occupiedSegment)) {
          return true;
        }
      }
    }
    return false;
  }

  /**
   * Converts a path into horizontal and vertical segments.
   *
   * @param edgeId edge id
   * @param points path points
   * @return axis-aligned segments
   */
  private List<RouteSegment> segments(String edgeId, List<Point> points) {
    List<RouteSegment> result = new ArrayList<>();
    for (int index = 1; index < points.size(); index++) {
      Point start = points.get(index - 1);
      Point end = points.get(index);
      if (same(start.x, end.x)) {
        result.add(RouteSegment.vertical(edgeId, start.x, start.y, end.y));
      } else if (same(start.y, end.y)) {
        result.add(RouteSegment.horizontal(edgeId, start.y, start.x, end.x));
      }
    }
    return result;
  }

  /**
   * Maps attempt number to the sequence 0, 1, -1, 2, -2, ...
   *
   * @param attempt candidate attempt
   * @return signed lane index
   */
  private int lane(int attempt) {
    if (attempt == 0) {
      return 0;
    }
    int distance = (attempt + 1) / 2;
    return attempt % 2 == 1 ? distance : -distance;
  }

  /**
   * Computes a small deterministic integer jitter for an edge id.
   *
   * @param edgeId edge id
   * @return jitter in layout units
   */
  private double stableJitter(String edgeId) {
    int hash = 0;
    for (char character : edgeId.toCharArray()) {
      hash = (hash * 31 + character) % 997;
    }
    return hash % 9;
  }

  /**
   * Spreads anchors on one side of a node for either source or target endpoints.
   *
   * @param node positioned node box
   * @param routes mutable edge routes
   * @param sourceEndpoint {@code true} to spread source anchors
   */
  private void spreadAnchorsFor(NodeBox node, List<EdgeRoute> routes, boolean sourceEndpoint) {
    Map<String, List<EdgeRoute>> bySide = new HashMap<>();
    for (EdgeRoute route : routes) {
      boolean matches =
          sourceEndpoint ? node.id.equals(route.sourceNodeId) : node.id.equals(route.targetNodeId);
      if (!matches) {
        continue;
      }
      Anchor anchor = sourceEndpoint ? route.sourceAnchor : route.targetAnchor;
      bySide.computeIfAbsent(anchor.side, ignored -> new ArrayList<>()).add(route);
    }
    bySide
        .values()
        .forEach(
            sideRoutes -> {
              if (sideRoutes.size() < 2) {
                return;
              }
              sideRoutes.sort(
                  Comparator.comparing(
                          (EdgeRoute route) ->
                              sourceEndpoint ? route.targetNodeId : route.sourceNodeId)
                      .thenComparing(route -> route.id));
              double step =
                  Math.max(
                      14.0d,
                      Math.min(30.0d, (node.height - 20.0d) / Math.max(1, sideRoutes.size() - 1)));
              double start = Math.max(10.0d, (node.height - step * (sideRoutes.size() - 1)) / 2.0d);
              for (int index = 0; index < sideRoutes.size(); index++) {
                EdgeRoute route = sideRoutes.get(index);
                Anchor anchor = sourceEndpoint ? route.sourceAnchor : route.targetAnchor;
                anchor.offsetY =
                    Math.max(10.0d, Math.min(node.height - 10.0d, start + index * step));
              }
            });
  }

  /**
   * Converts an anchor offset to an absolute point on a node.
   *
   * @param node positioned node
   * @param anchor anchor to resolve
   * @return absolute point
   */
  private Point pointForAnchor(NodeBox node, Anchor anchor) {
    return new Point(
        "right".equals(anchor.side) ? node.x + node.width : node.x,
        node.y + Math.max(8.0d, Math.min(node.height - 8.0d, anchor.offsetY)));
  }

  /**
   * Checks whether two points have the same rounded coordinates.
   *
   * @param left left point
   * @param right right point
   * @return {@code true} when both rounded coordinates match
   */
  private boolean samePoint(Point left, Point right) {
    return same(left.x, right.x) && same(left.y, right.y);
  }

  /**
   * Checks whether two layout coordinates are effectively identical.
   *
   * @param left left coordinate
   * @param right right coordinate
   * @return {@code true} when the coordinates are close enough to share a rendered pixel
   */
  private boolean same(double left, double right) {
    return Math.abs(left - right) < 0.5d;
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
    double offsetY = Math.max(8.0d, Math.min(node.height - 8.0d, pointY - node.y));
    writeAnchor(edge, field, new Anchor(side, offsetY));
  }

  /**
   * Writes an anchor object to an edge JSON object.
   *
   * @param edge edge JSON object
   * @param field target field name
   * @param anchor anchor to write
   */
  private void writeAnchor(ObjectNode edge, String field, Anchor anchor) {
    ObjectNode anchorNode = edge.putObject(field);
    anchorNode.put("side", anchor.side);
    anchorNode.put("offsetY", Math.round(anchor.offsetY));
  }

  /**
   * Creates canonical input/output ports with fixed sides and approximate middle-side positions.
   *
   * @param width node width
   * @param height node height
   * @return immutable canonical port list
   */
  private List<LayoutService.LayoutPort> canonicalPorts(double width, double height) {
    double y = Math.max(8.0d, Math.min(height - 8.0d, height / 2.0d)) - LAYOUT_PORT_SIZE / 2.0d;
    return List.of(
        new LayoutService.LayoutPort(
            INPUT_PORT_ID, "in", LAYOUT_PORT_SIZE, LAYOUT_PORT_SIZE, 0.0d, y),
        new LayoutService.LayoutPort(
            OUTPUT_PORT_ID,
            "out",
            LAYOUT_PORT_SIZE,
            LAYOUT_PORT_SIZE,
            Math.max(0.0d, width - LAYOUT_PORT_SIZE),
            y));
  }

  /**
   * Expands a normalized strategy into concrete layout options.
   *
   * @param strategy normalized strategy name
   * @return layout option map
   */
  private Map<String, Object> layoutOptions(String strategy) {
    return switch (strategy) {
      case "BALANCED_LAYERED" ->
          Map.of(
              "layoutStrategy",
              strategy,
              "nodeSpacing",
              230,
              "layerSpacing",
              360,
              "nodePlacementStrategy",
              "NETWORK_SIMPLEX");
      case "VERTICAL_FLOW" ->
          Map.of(
              "layoutStrategy",
              strategy,
              "direction",
              "DOWN",
              "nodeSpacing",
              240,
              "layerSpacing",
              360,
              "nodePlacementStrategy",
              "BRANDES_KOEPF");
      case "RELAXED_SPLINES" ->
          Map.of(
              "layoutStrategy",
              strategy,
              "edgeRouting",
              "SPLINES",
              "nodeSpacing",
              260,
              "layerSpacing",
              400,
              "nodePlacementStrategy",
              "BRANDES_KOEPF");
      case "TREE" ->
          Map.of(
              "layoutStrategy",
              strategy,
              "direction",
              "DOWN",
              "edgeRouting",
              "POLYLINE",
              "nodeSpacing",
              250,
              "layerSpacing",
              380);
      case "RADIAL" ->
          Map.of("layoutStrategy", strategy, "edgeRouting", "SPLINES", "nodeSpacing", 260);
      case "FORCE" ->
          Map.of("layoutStrategy", strategy, "edgeRouting", "SPLINES", "nodeSpacing", 280);
      default ->
          Map.of(
              "layoutStrategy",
              "SPACIOUS_LAYERED",
              "semanticDashboardGrid",
              true,
              "nodeSpacing",
              300,
              "layerSpacing",
              460,
              "nodePlacementStrategy",
              "BRANDES_KOEPF");
    };
  }

  /**
   * Normalizes strategy aliases and defaults unsupported values.
   *
   * @param requested requested strategy
   * @param fallback fallback strategy
   * @return normalized strategy name
   */
  private String normalizeStrategy(String requested, String fallback) {
    String value =
        (requested == null || requested.isBlank() ? fallback : requested)
            .trim()
            .toUpperCase()
            .replace('-', '_');
    return switch (value) {
      case "BALANCED", "BALANCED_LAYERED" -> "BALANCED_LAYERED";
      case "VERTICAL", "VERTICAL_FLOW" -> "VERTICAL_FLOW";
      case "RELAXED", "RELAXED_SPLINES", "SPLINES" -> "RELAXED_SPLINES";
      case "TREE", "TREE_FLOW" -> "TREE";
      case "RADIAL" -> "RADIAL";
      case "FORCE", "FORCE_DIRECTED" -> "FORCE";
      default -> "SPACIOUS_LAYERED";
    };
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
   * Converts a JSON array of string arrays to dashboard column definitions.
   *
   * @param values JSON column array
   * @return non-empty semantic dashboard columns
   */
  private List<List<String>> textColumns(JsonNode values) {
    List<List<String>> result = new ArrayList<>();
    if (!values.isArray()) {
      return result;
    }
    values.forEach(
        column -> {
          List<String> types = new ArrayList<>();
          if (column.isArray()) {
            column.forEach(
                value -> {
                  if (value.isTextual() && !value.asText().isBlank()) {
                    types.add(value.asText());
                  }
                });
          }
          if (!types.isEmpty()) {
            result.add(types);
          }
        });
    return result;
  }

  /**
   * Reads semantic dashboard columns from layout request options.
   *
   * @param request layout request
   * @return configured columns or an empty list
   */
  @SuppressWarnings("unchecked")
  private List<List<String>> dashboardColumns(LayoutService.LayoutRequest request) {
    Object rawColumns = request.options().get("semanticDashboardColumns");
    if (!(rawColumns instanceof List<?> columns)) {
      return List.of();
    }
    List<List<String>> result = new ArrayList<>();
    for (Object rawColumn : columns) {
      if (!(rawColumn instanceof List<?> column)) {
        continue;
      }
      List<String> types =
          column.stream().map(String::valueOf).filter(value -> !value.isBlank()).toList();
      if (!types.isEmpty()) {
        result.add(types);
      }
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

  /** Edge endpoint anchor expressed as a side plus vertical offset. */
  private static final class Anchor {

    /** Node side where the edge attaches. */
    private final String side;

    /** Vertical offset from the top of the node. */
    private double offsetY;

    /**
     * Creates an anchor.
     *
     * @param side node side
     * @param offsetY vertical offset
     */
    private Anchor(String side, double offsetY) {
      this.side = side;
      this.offsetY = offsetY;
    }
  }

  /** Mutable route point used while adjusting pins. */
  private static final class Point {

    /** X coordinate. */
    private final double x;

    /** Y coordinate. */
    private double y;

    /**
     * Creates a route point.
     *
     * @param x x coordinate
     * @param y y coordinate
     */
    private Point(double x, double y) {
      this.x = x;
      this.y = y;
    }
  }

  /** Mutable orthogonal route plus anchor metadata before it is written to stored view JSON. */
  private static final class EdgeRoute {

    /** Edge identifier. */
    private final String id;

    /** Source node identifier. */
    private final String sourceNodeId;

    /** Target node identifier. */
    private final String targetNodeId;

    /** Source anchor. */
    private final Anchor sourceAnchor;

    /** Target anchor. */
    private final Anchor targetAnchor;

    /** Mutable pin points for the route. */
    private final List<Point> pinPoints;

    /**
     * Creates an edge route.
     *
     * @param id edge identifier
     * @param sourceNodeId source node identifier
     * @param targetNodeId target node identifier
     * @param sourceAnchor source anchor
     * @param targetAnchor target anchor
     * @param pinPoints route pin points
     */
    private EdgeRoute(
        String id,
        String sourceNodeId,
        String targetNodeId,
        Anchor sourceAnchor,
        Anchor targetAnchor,
        List<Point> pinPoints) {
      this.id = id;
      this.sourceNodeId = sourceNodeId;
      this.targetNodeId = targetNodeId;
      this.sourceAnchor = sourceAnchor;
      this.targetAnchor = targetAnchor;
      this.pinPoints = pinPoints;
    }
  }

  /** Axis-aligned edge route segment claimed by a routed edge. */
  private static final class RouteSegment {

    /** Edge that owns the segment. */
    private final String edgeId;

    /** Whether this segment is vertical. */
    private final boolean vertical;

    /** Constant x for vertical segments, constant y for horizontal segments. */
    private final double constant;

    /** Normalized start of the varying interval. */
    private final double start;

    /** Normalized end of the varying interval. */
    private final double end;

    /**
     * Creates a route segment.
     *
     * @param edgeId owner edge id
     * @param vertical whether the segment is vertical
     * @param constant constant coordinate
     * @param first first varying coordinate
     * @param second second varying coordinate
     */
    private RouteSegment(
        String edgeId, boolean vertical, double constant, double first, double second) {
      this.edgeId = edgeId;
      this.vertical = vertical;
      this.constant = constant;
      this.start = Math.min(first, second);
      this.end = Math.max(first, second);
    }

    /**
     * Creates a vertical segment.
     *
     * @param edgeId owner edge id
     * @param x x coordinate
     * @param firstY first y coordinate
     * @param secondY second y coordinate
     * @return vertical segment
     */
    private static RouteSegment vertical(String edgeId, double x, double firstY, double secondY) {
      return new RouteSegment(edgeId, true, x, firstY, secondY);
    }

    /**
     * Creates a horizontal segment.
     *
     * @param edgeId owner edge id
     * @param y y coordinate
     * @param firstX first x coordinate
     * @param secondX second x coordinate
     * @return horizontal segment
     */
    private static RouteSegment horizontal(String edgeId, double y, double firstX, double secondX) {
      return new RouteSegment(edgeId, false, y, firstX, secondX);
    }

    /**
     * Checks whether this segment shares a non-zero rendered length with another segment.
     *
     * @param other other segment
     * @return {@code true} when the segments sit on top of each other
     */
    private boolean overlaps(RouteSegment other) {
      if (edgeId.equals(other.edgeId) || vertical != other.vertical) {
        return false;
      }
      if (Math.abs(constant - other.constant) >= 0.5d) {
        return false;
      }
      double overlap = Math.min(end, other.end) - Math.max(start, other.start);
      return overlap > 1.0d;
    }
  }
}
