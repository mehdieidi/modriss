package io.mehdieidi.modriss.platform.modeling.layout;

import io.mehdieidi.modriss.platform.kernel.PlatformException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.eclipse.elk.alg.layered.options.CrossingMinimizationStrategy;
import org.eclipse.elk.alg.layered.options.GreedySwitchType;
import org.eclipse.elk.alg.layered.options.LayeredOptions;
import org.eclipse.elk.alg.layered.options.NodePlacementStrategy;
import org.eclipse.elk.core.RecursiveGraphLayoutEngine;
import org.eclipse.elk.core.math.ElkPadding;
import org.eclipse.elk.core.options.Alignment;
import org.eclipse.elk.core.options.CoreOptions;
import org.eclipse.elk.core.options.Direction;
import org.eclipse.elk.core.options.EdgeRouting;
import org.eclipse.elk.core.options.HierarchyHandling;
import org.eclipse.elk.core.options.PortAlignment;
import org.eclipse.elk.core.options.PortConstraints;
import org.eclipse.elk.core.options.PortSide;
import org.eclipse.elk.core.options.SizeOptions;
import org.eclipse.elk.core.util.BasicProgressMonitor;
import org.eclipse.elk.graph.ElkConnectableShape;
import org.eclipse.elk.graph.ElkEdge;
import org.eclipse.elk.graph.ElkEdgeSection;
import org.eclipse.elk.graph.ElkLabel;
import org.eclipse.elk.graph.ElkNode;
import org.eclipse.elk.graph.ElkPort;
import org.eclipse.elk.graph.util.ElkGraphUtil;

/**
 * Adapts platform graph layout requests to Eclipse Layout Kernel graphs and maps the computed
 * coordinates back to API records.
 */
public final class LayoutService {

  /** Default size used when a port omits dimensions. */
  private static final double DEFAULT_PORT_SIZE = 10.0d;

  /** Canonical west-side input port id used when a request omits ports. */
  private static final String DEFAULT_INPUT_PORT_ID = "flow-in";

  /** Canonical east-side output port id used when a request omits ports. */
  private static final String DEFAULT_OUTPUT_PORT_ID = "flow-out";

  /** Default spacing between nodes for balanced layouts. */
  private static final double DEFAULT_NODE_SPACING = 156.0d;

  /** Default spacing between layers for balanced layouts. */
  private static final double DEFAULT_LAYER_SPACING = 260.0d;

  /** Horizontal distance from a node anchor before an edge enters a routing corridor. */
  private static final double ROUTE_STUB = 56.0d;

  /** Distance between candidate edge corridors while avoiding previously routed edges. */
  private static final double ROUTE_LANE_STEP = 34.0d;

  /** Maximum number of nearby alternative corridors considered for one edge. */
  private static final int MAX_ROUTE_ATTEMPTS = 32;

  /** ELK id for the layered algorithm. */
  private static final String LAYERED_ALGORITHM = "org.eclipse.elk.layered";

  /** ELK id for the tree algorithm. */
  private static final String TREE_ALGORITHM = "org.eclipse.elk.mrtree";

  /** ELK id for the radial algorithm. */
  private static final String RADIAL_ALGORITHM = "org.eclipse.elk.radial";

  /** ELK id for the force-directed algorithm. */
  private static final String FORCE_ALGORITHM = "org.eclipse.elk.force";

  /** Shared ELK layout engine; access is synchronized during layout execution. */
  private static final RecursiveGraphLayoutEngine LAYOUT_ENGINE = new RecursiveGraphLayoutEngine();

  /**
   * Computes node positions and edge routes for a layout request.
   *
   * @param request layout request
   * @return computed layout response
   */
  public LayoutResponse layout(LayoutRequest request) {
    LayoutRequest normalizedRequest = validate(request);
    List<String> warnings = new ArrayList<>();
    if (!normalizedRequest.fixedNodeIds().isEmpty()) {
      warnings.add("Layout fixedNodeIds are currently treated as soft hints only.");
    }
    if (normalizedRequest.nodes().isEmpty()) {
      return new LayoutResponse(List.of(), List.of(), warnings);
    }

    ElkNode graph = ElkGraphUtil.createGraph();
    configureGraph(graph, normalizedRequest);

    Map<String, ElkNode> nodesById = new LinkedHashMap<>();
    Map<String, Map<String, ElkPort>> portsByNodeId = new LinkedHashMap<>();
    Map<String, ElkEdge> edgesById = new LinkedHashMap<>();
    LayoutStyle style = LayoutStyle.from(normalizedRequest);
    Set<String> layoutEdgeIds =
        style == LayoutStyle.RADIAL
            ? radialSpanningForestEdgeIds(normalizedRequest, warnings)
            : null;

    for (LayoutNode nodeRequest : normalizedRequest.nodes()) {
      ElkNode node = ElkGraphUtil.createNode(graph);
      node.setIdentifier(nodeRequest.id());
      node.setDimensions(nodeRequest.width(), nodeRequest.height());
      if (hasCoordinates(nodeRequest.x(), nodeRequest.y())) {
        node.setLocation(nodeRequest.x(), nodeRequest.y());
      }
      addLabel(node, nodeRequest.label());
      nodesById.put(nodeRequest.id(), node);
      portsByNodeId.put(nodeRequest.id(), createPorts(node, nodeRequest));
    }

    for (LayoutEdge edgeRequest : normalizedRequest.edges()) {
      if (layoutEdgeIds != null && !layoutEdgeIds.contains(edgeRequest.id())) {
        continue;
      }
      ElkConnectableShape source =
          resolveEndpoint(
              edgeRequest.sourceNodeId(),
              defaultPortId(edgeRequest.sourcePortId(), DEFAULT_OUTPUT_PORT_ID),
              nodesById,
              portsByNodeId,
              "source",
              warnings);
      ElkConnectableShape target =
          resolveEndpoint(
              edgeRequest.targetNodeId(),
              defaultPortId(edgeRequest.targetPortId(), DEFAULT_INPUT_PORT_ID),
              nodesById,
              portsByNodeId,
              "target",
              warnings);
      ElkEdge edge = ElkGraphUtil.createSimpleEdge(source, target);
      edge.setIdentifier(edgeRequest.id());
      addLabel(edge, edgeRequest.label());
      edgesById.put(edgeRequest.id(), edge);
    }

    try {
      synchronized (LAYOUT_ENGINE) {
        LAYOUT_ENGINE.layout(graph, new BasicProgressMonitor());
      }
    } catch (NoClassDefFoundError | ExceptionInInitializerError exception) {
      throw new PlatformException(
          500, "ELK layout runtime is not available on the backend classpath.");
    } catch (StackOverflowError error) {
      throw new PlatformException(
          400, "ELK layout failed: radial layout could not process this graph topology.");
    } catch (RuntimeException exception) {
      throw new PlatformException(400, "ELK layout failed: " + safeMessage(exception));
    }

    return new LayoutResponse(
        buildNodeLayouts(normalizedRequest, nodesById),
        buildEdgeLayouts(normalizedRequest, nodesById, edgesById, warnings),
        List.copyOf(warnings));
  }

  /**
   * Validates required ids, dimensions, and edge endpoints.
   *
   * @param request layout request
   * @return original request when valid
   */
  private LayoutRequest validate(LayoutRequest request) {
    if (request == null) {
      throw new PlatformException(400, "Layout request is required.");
    }

    Set<String> nodeIds = new LinkedHashSet<>();
    Set<String> edgeIds = new LinkedHashSet<>();
    for (LayoutNode node : request.nodes()) {
      String nodeId = normalize(node.id());
      if (nodeId.isEmpty()) {
        throw new PlatformException(400, "Layout node id is required.");
      }
      if (node.width() <= 0 || node.height() <= 0) {
        throw new PlatformException(
            400, "Layout node '" + nodeId + "' must have positive width and height.");
      }
      if (!nodeIds.add(nodeId)) {
        throw new PlatformException(400, "Duplicate layout node id: " + nodeId);
      }
      Set<String> portIds = new LinkedHashSet<>();
      for (LayoutPort port : node.ports()) {
        String portId = normalize(port.id());
        if (portId.isEmpty()) {
          throw new PlatformException(400, "Layout port id is required for node '" + nodeId + "'.");
        }
        if (!portIds.add(portId)) {
          throw new PlatformException(
              400, "Duplicate layout port id '" + portId + "' for node '" + nodeId + "'.");
        }
      }
    }

    for (LayoutEdge edge : request.edges()) {
      String edgeId = normalize(edge.id());
      if (edgeId.isEmpty()) {
        throw new PlatformException(400, "Layout edge id is required.");
      }
      if (!edgeIds.add(edgeId)) {
        throw new PlatformException(400, "Duplicate layout edge id: " + edgeId);
      }
      String sourceNodeId = normalize(edge.sourceNodeId());
      String targetNodeId = normalize(edge.targetNodeId());
      if (!nodeIds.contains(sourceNodeId)) {
        throw new PlatformException(
            400,
            "Layout edge '" + edgeId + "' references missing source node '" + sourceNodeId + "'.");
      }
      if (!nodeIds.contains(targetNodeId)) {
        throw new PlatformException(
            400,
            "Layout edge '" + edgeId + "' references missing target node '" + targetNodeId + "'.");
      }
    }

    return request;
  }

  /**
   * Applies ELK graph-level options derived from the request profile and options.
   *
   * @param graph ELK graph
   * @param request layout request
   */
  private void configureGraph(ElkNode graph, LayoutRequest request) {
    LayoutStyle style = LayoutStyle.from(request);
    graph.setProperty(CoreOptions.ALGORITHM, algorithm(style));
    graph.setProperty(CoreOptions.DIRECTION, direction(request));
    graph.setProperty(CoreOptions.EDGE_ROUTING, edgeRouting(request));
    graph.setProperty(CoreOptions.SPACING_NODE_NODE, nodeSpacing(request));
    graph.setProperty(LayeredOptions.SPACING_NODE_NODE_BETWEEN_LAYERS, layerSpacing(request));
    graph.setProperty(CoreOptions.SPACING_EDGE_EDGE, style.edgeSpacing());
    graph.setProperty(CoreOptions.SPACING_EDGE_NODE, style.edgeNodeSpacing());
    graph.setProperty(CoreOptions.PADDING, new ElkPadding(style.padding()));
    graph.setProperty(CoreOptions.HIERARCHY_HANDLING, HierarchyHandling.INCLUDE_CHILDREN);
    graph.setProperty(CoreOptions.ALIGNMENT, Alignment.CENTER);
    graph.setProperty(CoreOptions.PORT_ALIGNMENT_DEFAULT, PortAlignment.JUSTIFIED);
    graph.setProperty(
        CoreOptions.NODE_SIZE_OPTIONS,
        EnumSet.of(
            SizeOptions.DEFAULT_MINIMUM_SIZE,
            SizeOptions.MINIMUM_SIZE_ACCOUNTS_FOR_PADDING,
            SizeOptions.PORTS_OVERHANG));
    graph.setProperty(CoreOptions.SEPARATE_CONNECTED_COMPONENTS, true);
    if (!style.usesLayeredOptions()) {
      return;
    }
    graph.setProperty(LayeredOptions.NODE_PLACEMENT_STRATEGY, nodePlacementStrategy(request));
    graph.setProperty(
        LayeredOptions.CROSSING_MINIMIZATION_STRATEGY, CrossingMinimizationStrategy.LAYER_SWEEP);
    graph.setProperty(
        LayeredOptions.CROSSING_MINIMIZATION_GREEDY_SWITCH_TYPE, GreedySwitchType.TWO_SIDED);
    graph.setProperty(
        LayeredOptions.CROSSING_MINIMIZATION_GREEDY_SWITCH_HIERARCHICAL_TYPE,
        GreedySwitchType.TWO_SIDED);
    graph.setProperty(LayeredOptions.NODE_PLACEMENT_FAVOR_STRAIGHT_EDGES, true);
    graph.setProperty(LayeredOptions.CONSIDER_MODEL_ORDER_NO_MODEL_ORDER, true);
    graph.setProperty(LayeredOptions.UNNECESSARY_BENDPOINTS, true);
  }

  /**
   * Maps a layout style to an ELK algorithm id.
   *
   * @param style normalized layout style
   * @return ELK algorithm id
   */
  private String algorithm(LayoutStyle style) {
    return switch (style) {
      case TREE -> TREE_ALGORITHM;
      case RADIAL -> RADIAL_ALGORITHM;
      case FORCE -> FORCE_ALGORITHM;
      default -> LAYERED_ALGORITHM;
    };
  }

  /**
   * Selects an undirected spanning forest for ELK radial placement.
   *
   * <p>ELK radial recursively traverses successors and can overflow on cyclic graphs. A spanning
   * forest preserves the graph's connected structure for node placement while allowing the stored
   * view service to route every original edge after positions are computed.
   *
   * @param request layout request
   * @param warnings mutable warning sink
   * @return edge ids included in the radial placement graph
   */
  private Set<String> radialSpanningForestEdgeIds(LayoutRequest request, List<String> warnings) {
    Map<String, String> parents = new LinkedHashMap<>();
    request.nodes().forEach(node -> parents.put(node.id(), node.id()));
    Set<String> selected = new LinkedHashSet<>();
    int omitted = 0;
    for (LayoutEdge edge : request.edges()) {
      String sourceRoot = findRoot(parents, edge.sourceNodeId());
      String targetRoot = findRoot(parents, edge.targetNodeId());
      if (sourceRoot.equals(targetRoot)) {
        omitted += 1;
        continue;
      }
      parents.put(targetRoot, sourceRoot);
      selected.add(edge.id());
    }
    if (omitted > 0) {
      warnings.add(
          "Radial layout used a spanning forest for placement and routed "
              + omitted
              + " non-tree edge(s) after layout.");
    }
    return selected;
  }

  /**
   * Finds the root of a union-find set.
   *
   * @param parents parent map
   * @param nodeId node id
   * @return root id
   */
  private String findRoot(Map<String, String> parents, String nodeId) {
    String parent = parents.getOrDefault(nodeId, nodeId);
    if (parent.equals(nodeId)) {
      parents.putIfAbsent(nodeId, nodeId);
      return nodeId;
    }
    String root = findRoot(parents, parent);
    parents.put(nodeId, root);
    return root;
  }

  /**
   * Selects the primary layout direction.
   *
   * @param request layout request
   * @return ELK direction option
   */
  private Direction direction(LayoutRequest request) {
    String normalized =
        configuredValue(request, "direction", request.profile()).toUpperCase(Locale.ROOT);
    LayoutStyle style = LayoutStyle.from(request);
    if (style == LayoutStyle.VERTICAL || style == LayoutStyle.TREE) {
      return Direction.DOWN;
    }
    if (normalized.contains("DOWN")) {
      return Direction.DOWN;
    }
    if (normalized.contains("UP")) {
      return Direction.UP;
    }
    if (normalized.contains("LEFT")) {
      return Direction.LEFT;
    }
    return Direction.RIGHT;
  }

  /**
   * Selects the edge routing strategy for the requested style.
   *
   * @param request layout request
   * @return ELK edge routing option
   */
  private EdgeRouting edgeRouting(LayoutRequest request) {
    LayoutStyle style = LayoutStyle.from(request);
    String defaultRouting =
        switch (style) {
          case RELAXED, FORCE, RADIAL -> "SPLINES";
          case TREE -> "POLYLINE";
          default -> "ORTHOGONAL";
        };
    String normalized =
        configuredValue(request, "edgeRouting", defaultRouting).toUpperCase(Locale.ROOT);
    if ("SPLINE".equals(normalized) || "SPLINES".equals(normalized)) {
      return EdgeRouting.SPLINES;
    }
    if ("POLYLINE".equals(normalized)) {
      return EdgeRouting.POLYLINE;
    }
    return EdgeRouting.ORTHOGONAL;
  }

  /**
   * Computes node spacing from explicit options, style defaults, or legacy profile hints.
   *
   * @param request layout request
   * @return node spacing in ELK units
   */
  private double nodeSpacing(LayoutRequest request) {
    Object configured = request.options().get("nodeSpacing");
    if (configured instanceof Number number && number.doubleValue() > 0) {
      return number.doubleValue();
    }
    if (configured instanceof String stringValue) {
      try {
        double parsed = Double.parseDouble(stringValue);
        if (parsed > 0) {
          return parsed;
        }
      } catch (NumberFormatException ignored) {
        // Ignore non-numeric profile overrides and fall back to defaults.
      }
    }
    String profile = normalize(request.profile()).toUpperCase(Locale.ROOT);
    LayoutStyle style = LayoutStyle.from(request);
    if (style == LayoutStyle.SPACIOUS) {
      return 220.0d;
    }
    if (style == LayoutStyle.RELAXED || style == LayoutStyle.RADIAL || style == LayoutStyle.FORCE) {
      return 190.0d;
    }
    if (style == LayoutStyle.TREE || style == LayoutStyle.VERTICAL) {
      return 170.0d;
    }
    if (profile.contains("SECURITY") || profile.contains("IAM")) {
      return 96.0d;
    }
    if (profile.contains("OVERLAY")) {
      return 72.0d;
    }
    return DEFAULT_NODE_SPACING;
  }

  /**
   * Computes layer spacing from explicit options, style defaults, or legacy profile hints.
   *
   * @param request layout request
   * @return layer spacing in ELK units
   */
  private double layerSpacing(LayoutRequest request) {
    Object configured = request.options().get("layerSpacing");
    if (configured instanceof Number number && number.doubleValue() > 0) {
      return number.doubleValue();
    }
    if (configured instanceof String stringValue) {
      try {
        double parsed = Double.parseDouble(stringValue);
        if (parsed > 0) {
          return parsed;
        }
      } catch (NumberFormatException ignored) {
        // Ignore non-numeric profile overrides and fall back to defaults.
      }
    }
    String profile = normalize(request.profile()).toUpperCase(Locale.ROOT);
    LayoutStyle style = LayoutStyle.from(request);
    if (style == LayoutStyle.SPACIOUS) {
      return 340.0d;
    }
    if (style == LayoutStyle.RELAXED) {
      return 300.0d;
    }
    if (style == LayoutStyle.TREE || style == LayoutStyle.VERTICAL) {
      return 260.0d;
    }
    if (profile.contains("CONTAINER") || profile.contains("FOCUS")) {
      return 196.0d;
    }
    if (profile.contains("SECURITY") || profile.contains("IAM")) {
      return 176.0d;
    }
    return DEFAULT_LAYER_SPACING;
  }

  /**
   * Selects ELK's layered node placement strategy.
   *
   * @param request layout request
   * @return node placement strategy
   */
  private NodePlacementStrategy nodePlacementStrategy(LayoutRequest request) {
    LayoutStyle style = LayoutStyle.from(request);
    String defaultStrategy = style == LayoutStyle.SPACIOUS ? "BRANDES_KOEPF" : "NETWORK_SIMPLEX";
    String normalized =
        configuredValue(request, "nodePlacementStrategy", defaultStrategy).toUpperCase(Locale.ROOT);
    if (normalized.contains("BRANDES")) {
      return NodePlacementStrategy.BRANDES_KOEPF;
    }
    if (normalized.contains("LINEAR")) {
      return NodePlacementStrategy.LINEAR_SEGMENTS;
    }
    if (normalized.contains("SIMPLE")) {
      return NodePlacementStrategy.SIMPLE;
    }
    return NodePlacementStrategy.NETWORK_SIMPLEX;
  }

  /**
   * Creates ELK ports for a node and indexes them by request id.
   *
   * @param node ELK node
   * @param nodeRequest request node
   * @return ports keyed by id
   */
  private Map<String, ElkPort> createPorts(ElkNode node, LayoutNode nodeRequest) {
    Map<String, ElkPort> portsById = new LinkedHashMap<>();
    List<LayoutPort> ports =
        nodeRequest.ports().isEmpty() ? defaultPorts(nodeRequest) : nodeRequest.ports();
    if (!ports.isEmpty()) {
      node.setProperty(CoreOptions.PORT_CONSTRAINTS, PortConstraints.FIXED_POS);
    }
    for (LayoutPort portRequest : ports) {
      ElkPort port = ElkGraphUtil.createPort(node);
      port.setIdentifier(portRequest.id());
      port.setDimensions(
          positiveOrDefault(portRequest.width(), DEFAULT_PORT_SIZE),
          positiveOrDefault(portRequest.height(), DEFAULT_PORT_SIZE));
      port.setProperty(CoreOptions.PORT_SIDE, inferPortSide(portRequest.id()));
      if (hasCoordinates(portRequest.x(), portRequest.y())) {
        port.setLocation(portRequest.x(), portRequest.y());
      }
      addLabel(port, portRequest.label());
      portsById.put(portRequest.id(), port);
    }
    return portsById;
  }

  /**
   * Creates canonical input/output ports for nodes that do not declare any ports.
   *
   * @param nodeRequest layout node request
   * @return canonical west/east ports
   */
  private List<LayoutPort> defaultPorts(LayoutNode nodeRequest) {
    double y =
        Math.max(8.0d, Math.min(nodeRequest.height() - 8.0d, nodeRequest.height() / 2.0d))
            - DEFAULT_PORT_SIZE / 2.0d;
    return List.of(
        new LayoutPort(DEFAULT_INPUT_PORT_ID, "in", DEFAULT_PORT_SIZE, DEFAULT_PORT_SIZE, 0.0d, y),
        new LayoutPort(
            DEFAULT_OUTPUT_PORT_ID,
            "out",
            DEFAULT_PORT_SIZE,
            DEFAULT_PORT_SIZE,
            Math.max(0.0d, nodeRequest.width() - DEFAULT_PORT_SIZE),
            y));
  }

  /**
   * Returns an explicit port id or the canonical side-specific default.
   *
   * @param requested requested port id
   * @param fallback fallback port id
   * @return normalized port id
   */
  private String defaultPortId(String requested, String fallback) {
    return requested == null || requested.isBlank() ? fallback : requested;
  }

  /**
   * Resolves an edge endpoint to a port when available, otherwise to the node boundary.
   *
   * @param nodeId endpoint node id
   * @param portId endpoint port id
   * @param nodesById nodes keyed by id
   * @param portsByNodeId ports keyed by node id and port id
   * @param endpointName human-readable endpoint name
   * @param warnings mutable warning sink
   * @return resolved ELK connectable shape
   */
  private ElkConnectableShape resolveEndpoint(
      String nodeId,
      String portId,
      Map<String, ElkNode> nodesById,
      Map<String, Map<String, ElkPort>> portsByNodeId,
      String endpointName,
      List<String> warnings) {
    if (portId == null || portId.isBlank()) {
      return nodesById.get(nodeId);
    }
    ElkPort port = portsByNodeId.getOrDefault(nodeId, Map.of()).get(portId);
    if (port != null) {
      return port;
    }
    warnings.add(
        "Missing "
            + endpointName
            + " port '"
            + portId
            + "' on node '"
            + nodeId
            + "'. Falling back to the node boundary.");
    return nodesById.get(nodeId);
  }

  /**
   * Builds platform node layout records from ELK nodes.
   *
   * @param request original layout request
   * @param nodesById ELK nodes keyed by id
   * @return immutable laid-out node list
   */
  private List<LaidOutNode> buildNodeLayouts(
      LayoutRequest request, Map<String, ElkNode> nodesById) {
    List<LaidOutNode> result = new ArrayList<>();
    for (LayoutNode nodeRequest : request.nodes()) {
      ElkNode node = nodesById.get(nodeRequest.id());
      result.add(
          new LaidOutNode(
              nodeRequest.id(), node.getX(), node.getY(), node.getWidth(), node.getHeight()));
    }
    return List.copyOf(result);
  }

  /**
   * Builds platform edge route records from ELK edge sections.
   *
   * @param request original layout request
   * @param nodesById ELK nodes keyed by id
   * @param edgesById ELK edges keyed by id
   * @param warnings mutable warning sink
   * @return immutable routed edge list
   */
  private List<RoutedEdge> buildEdgeLayouts(
      LayoutRequest request,
      Map<String, ElkNode> nodesById,
      Map<String, ElkEdge> edgesById,
      List<String> warnings) {
    List<RoutedEdge> result = new ArrayList<>();
    Map<String, NodeBox> nodeBoxes = nodeBoxes(nodesById);
    Map<String, RouteAnchor> sourceAnchors = spreadRouteAnchors(request.edges(), nodeBoxes, true);
    Map<String, RouteAnchor> targetAnchors = spreadRouteAnchors(request.edges(), nodeBoxes, false);
    List<RouteSegment> occupiedSegments = new ArrayList<>();
    List<LayoutEdge> orderedEdges = new ArrayList<>(request.edges());
    orderedEdges.sort(Comparator.comparing(LayoutEdge::id));
    for (LayoutEdge edgeRequest : orderedEdges) {
      EdgeSection section =
          separatedSection(
              edgeRequest, nodeBoxes, sourceAnchors, targetAnchors, occupiedSegments, warnings);
      if (section == null) {
        ElkEdge edge = edgesById.get(edgeRequest.id());
        section = elkSection(edge);
      }
      if (section == null) {
        warnings.add("Edge '" + edgeRequest.id() + "' was given a fallback route.");
        section = fallbackSection(edgeRequest, nodesById, warnings);
      }
      List<EdgeSection> sections = List.of(section);
      List<LayoutPoint> bendPoints = section.bendPoints();
      result.add(new RoutedEdge(edgeRequest.id(), List.copyOf(sections), List.copyOf(bendPoints)));
    }
    return List.copyOf(result);
  }

  /**
   * Builds a map of laid-out node boxes from ELK nodes.
   *
   * @param nodesById ELK nodes keyed by id
   * @return node boxes keyed by id
   */
  private Map<String, NodeBox> nodeBoxes(Map<String, ElkNode> nodesById) {
    Map<String, NodeBox> result = new LinkedHashMap<>();
    nodesById.forEach(
        (id, node) ->
            result.put(
                id, new NodeBox(id, node.getX(), node.getY(), node.getWidth(), node.getHeight())));
    return result;
  }

  /**
   * Spreads source or target anchors on each node so route stubs do not share the same endpoint.
   *
   * @param edges layout edges
   * @param nodesById positioned nodes
   * @param sourceEndpoint whether source anchors should be spread
   * @return anchors keyed by edge id
   */
  private Map<String, RouteAnchor> spreadRouteAnchors(
      List<LayoutEdge> edges, Map<String, NodeBox> nodesById, boolean sourceEndpoint) {
    Map<String, RouteAnchor> result = new LinkedHashMap<>();
    Map<String, List<LayoutEdge>> edgesByNode = new LinkedHashMap<>();
    for (LayoutEdge edge : edges) {
      String nodeId = sourceEndpoint ? edge.sourceNodeId() : edge.targetNodeId();
      edgesByNode.computeIfAbsent(nodeId, ignored -> new ArrayList<>()).add(edge);
    }
    edgesByNode.forEach(
        (nodeId, nodeEdges) -> {
          NodeBox node = nodesById.get(nodeId);
          if (node == null) {
            return;
          }
          nodeEdges.sort(
              Comparator.comparing(
                      (LayoutEdge edge) ->
                          sourceEndpoint ? edge.targetNodeId() : edge.sourceNodeId())
                  .thenComparing(LayoutEdge::id));
          double step =
              Math.max(
                  8.0d,
                  Math.min(30.0d, (node.height() - 16.0d) / Math.max(1, nodeEdges.size() - 1)));
          double start = Math.max(8.0d, (node.height() - step * (nodeEdges.size() - 1)) / 2.0d);
          for (int index = 0; index < nodeEdges.size(); index++) {
            LayoutEdge edge = nodeEdges.get(index);
            double offsetY = Math.max(8.0d, Math.min(node.height() - 8.0d, start + index * step));
            result.put(edge.id(), new RouteAnchor(sourceEndpoint ? "right" : "left", offsetY));
          }
        });
    return result;
  }

  /**
   * Creates one non-overlapping orthogonal section for an edge.
   *
   * @param edge edge request
   * @param nodesById positioned nodes
   * @param sourceAnchors source anchors keyed by edge id
   * @param targetAnchors target anchors keyed by edge id
   * @param occupiedSegments segments already claimed by earlier edges
   * @param warnings mutable warning sink when routing cannot avoid overlap
   * @return separated edge section or {@code null} when endpoints are missing
   */
  private EdgeSection separatedSection(
      LayoutEdge edge,
      Map<String, NodeBox> nodesById,
      Map<String, RouteAnchor> sourceAnchors,
      Map<String, RouteAnchor> targetAnchors,
      List<RouteSegment> occupiedSegments,
      List<String> warnings) {
    NodeBox source = nodesById.get(edge.sourceNodeId());
    NodeBox target = nodesById.get(edge.targetNodeId());
    if (source == null || target == null) {
      return null;
    }
    RouteAnchor sourceAnchor =
        sourceAnchors.getOrDefault(edge.id(), new RouteAnchor("right", source.height() / 2.0d));
    RouteAnchor targetAnchor =
        targetAnchors.getOrDefault(edge.id(), new RouteAnchor("left", target.height() / 2.0d));
    List<LayoutPoint> selected = null;
    for (int attempt = 0; attempt < MAX_ROUTE_ATTEMPTS; attempt++) {
      List<LayoutPoint> candidate =
          candidatePath(edge.id(), source, target, sourceAnchor, targetAnchor, attempt);
      if (!overlapsExistingSegments(candidate, occupiedSegments)) {
        selected = candidate;
        break;
      }
    }
    if (selected == null) {
      warnings.add("Edge '" + edge.id() + "' could not be routed without overlap.");
      selected =
          candidatePath(edge.id(), source, target, sourceAnchor, targetAnchor, MAX_ROUTE_ATTEMPTS);
    }
    occupiedSegments.addAll(segments(edge.id(), selected));
    return new EdgeSection(
        selected.get(0),
        selected.get(selected.size() - 1),
        List.copyOf(selected.subList(1, selected.size() - 1)));
  }

  /**
   * Builds a candidate orthogonal path through a deterministic edge corridor.
   *
   * @param edgeId edge id
   * @param source source node
   * @param target target node
   * @param sourceAnchor source anchor
   * @param targetAnchor target anchor
   * @param attempt candidate attempt
   * @return full path including endpoints
   */
  private List<LayoutPoint> candidatePath(
      String edgeId,
      NodeBox source,
      NodeBox target,
      RouteAnchor sourceAnchor,
      RouteAnchor targetAnchor,
      int attempt) {
    LayoutPoint start = pointForAnchor(source, sourceAnchor);
    LayoutPoint end = pointForAnchor(target, targetAnchor);
    int lane = lane(attempt / 2);
    boolean verticalFirst = attempt % 2 == 1;
    double jitter = stableJitter(edgeId);
    if (source.id().equals(target.id())) {
      double loopX =
          source.x() + source.width() + ROUTE_STUB + Math.abs(lane) * ROUTE_LANE_STEP + jitter;
      double loopY = source.y() - ROUTE_STUB - Math.max(0, lane) * ROUTE_LANE_STEP - jitter;
      return compactPath(
          List.of(
              start,
              new LayoutPoint(loopX, start.y()),
              new LayoutPoint(loopX, loopY),
              new LayoutPoint(end.x(), loopY),
              end));
    }
    double corridorY = corridorY(source, target, start, end, lane, jitter);
    double detour = ROUTE_STUB + Math.abs(lane) * ROUTE_LANE_STEP + jitter;
    double sourceX = start.x() + detour;
    double targetX = end.x() - detour;
    if (same(start.x(), end.x())) {
      sourceX = start.x() + (lane < 0 ? -detour : detour);
      targetX = sourceX;
    }
    if (verticalFirst) {
      if (same(start.x(), end.x())) {
        return compactPath(
            List.of(
                start,
                new LayoutPoint(start.x(), corridorY),
                new LayoutPoint(sourceX, corridorY),
                new LayoutPoint(sourceX, end.y()),
                end));
      }
      return compactPath(
          List.of(
              start,
              new LayoutPoint(start.x(), corridorY),
              new LayoutPoint(targetX, corridorY),
              new LayoutPoint(targetX, end.y()),
              end));
    }
    return compactPath(
        List.of(
            start,
            new LayoutPoint(sourceX, start.y()),
            new LayoutPoint(sourceX, corridorY),
            new LayoutPoint(targetX, corridorY),
            new LayoutPoint(targetX, end.y()),
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
      NodeBox source, NodeBox target, LayoutPoint start, LayoutPoint end, int lane, double jitter) {
    if (Math.abs(start.y() - end.y()) > 80.0d) {
      return Math.round((start.y() + end.y()) / 2.0d + lane * ROUTE_LANE_STEP + jitter);
    }
    double top = Math.min(source.y(), target.y());
    double bottom = Math.max(source.y() + source.height(), target.y() + target.height());
    if (lane == 0 || lane > 0) {
      return Math.round(top - ROUTE_STUB - Math.max(0, lane - 1) * ROUTE_LANE_STEP - jitter);
    }
    return Math.round(bottom + ROUTE_STUB + Math.abs(lane + 1) * ROUTE_LANE_STEP + jitter);
  }

  /**
   * Removes duplicate and unnecessary collinear points from a path.
   *
   * @param points path points
   * @return compact path
   */
  private List<LayoutPoint> compactPath(List<LayoutPoint> points) {
    List<LayoutPoint> result = new ArrayList<>();
    for (LayoutPoint point : points) {
      LayoutPoint rounded = new LayoutPoint(Math.round(point.x()), Math.round(point.y()));
      if (!result.isEmpty() && samePoint(result.get(result.size() - 1), rounded)) {
        continue;
      }
      result.add(rounded);
      while (result.size() >= 3 && collinearLastThree(result)) {
        LayoutPoint last = result.remove(result.size() - 1);
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
  private boolean collinearLastThree(List<LayoutPoint> points) {
    int size = points.size();
    LayoutPoint first = points.get(size - 3);
    LayoutPoint middle = points.get(size - 2);
    LayoutPoint last = points.get(size - 1);
    return (same(first.x(), middle.x()) && same(middle.x(), last.x()))
        || (same(first.y(), middle.y()) && same(middle.y(), last.y()));
  }

  /**
   * Checks whether a candidate path overlaps any already occupied edge segment.
   *
   * @param candidate candidate path
   * @param occupiedSegments occupied edge segments
   * @return {@code true} when a non-point overlap exists
   */
  private boolean overlapsExistingSegments(
      List<LayoutPoint> candidate, List<RouteSegment> occupiedSegments) {
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
  private List<RouteSegment> segments(String edgeId, List<LayoutPoint> points) {
    List<RouteSegment> result = new ArrayList<>();
    for (int index = 1; index < points.size(); index++) {
      LayoutPoint start = points.get(index - 1);
      LayoutPoint end = points.get(index);
      if (same(start.x(), end.x())) {
        result.add(RouteSegment.vertical(edgeId, start.x(), start.y(), end.y()));
      } else if (same(start.y(), end.y())) {
        result.add(RouteSegment.horizontal(edgeId, start.y(), start.x(), end.x()));
      }
    }
    return result;
  }

  /**
   * Returns a point on a node boundary for a route anchor.
   *
   * @param node positioned node
   * @param anchor route anchor
   * @return absolute point
   */
  private LayoutPoint pointForAnchor(NodeBox node, RouteAnchor anchor) {
    return new LayoutPoint(
        "right".equals(anchor.side()) ? node.x() + node.width() : node.x(),
        node.y() + Math.max(8.0d, Math.min(node.height() - 8.0d, anchor.offsetY())));
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
    for (char character : normalize(edgeId).toCharArray()) {
      hash = (hash * 31 + character) % 997;
    }
    return hash % 9;
  }

  /**
   * Reads the first ELK section for fallback use.
   *
   * @param edge ELK edge
   * @return first ELK section or {@code null}
   */
  private EdgeSection elkSection(ElkEdge edge) {
    if (edge == null || edge.getSections().isEmpty()) {
      return null;
    }
    ElkEdgeSection section = edge.getSections().get(0);
    List<LayoutPoint> bendPoints = new ArrayList<>();
    section
        .getBendPoints()
        .forEach(point -> bendPoints.add(new LayoutPoint(point.getX(), point.getY())));
    return new EdgeSection(
        new LayoutPoint(section.getStartX(), section.getStartY()),
        new LayoutPoint(section.getEndX(), section.getEndY()),
        List.copyOf(bendPoints));
  }

  /**
   * Creates a simple source-to-target route when ELK omits explicit sections.
   *
   * @param edge edge request
   * @param nodesById ELK nodes keyed by id
   * @param warnings mutable warning sink
   * @return fallback edge section
   */
  private EdgeSection fallbackSection(
      LayoutEdge edge, Map<String, ElkNode> nodesById, List<String> warnings) {
    ElkNode source = nodesById.get(edge.sourceNodeId());
    ElkNode target = nodesById.get(edge.targetNodeId());
    if (source == null || target == null) {
      warnings.add(
          "Edge '"
              + edge.id()
              + "' could not be given a fallback route because an endpoint was missing.");
      return new EdgeSection(new LayoutPoint(0, 0), new LayoutPoint(0, 0), List.of());
    }
    LayoutPoint start =
        new LayoutPoint(
            finiteOrDefault(source.getX(), 0) + source.getWidth(),
            finiteOrDefault(source.getY(), 0) + source.getHeight() / 2.0d);
    LayoutPoint end =
        new LayoutPoint(
            finiteOrDefault(target.getX(), 0),
            finiteOrDefault(target.getY(), 0) + target.getHeight() / 2.0d);
    return new EdgeSection(start, end, List.of());
  }

  /**
   * Adds a text label to an ELK node when present.
   *
   * @param node ELK node
   * @param labelText label text
   */
  private void addLabel(ElkNode node, String labelText) {
    if (labelText == null || labelText.isBlank()) {
      return;
    }
    ElkLabel label = ElkGraphUtil.createLabel(node);
    label.setText(labelText.trim());
  }

  /**
   * Adds a text label to an ELK port when present.
   *
   * @param port ELK port
   * @param labelText label text
   */
  private void addLabel(ElkPort port, String labelText) {
    if (labelText == null || labelText.isBlank()) {
      return;
    }
    ElkLabel label = ElkGraphUtil.createLabel(port);
    label.setText(labelText.trim());
  }

  /**
   * Adds a text label to an ELK edge when present.
   *
   * @param edge ELK edge
   * @param labelText label text
   */
  private void addLabel(ElkEdge edge, String labelText) {
    if (labelText == null || labelText.isBlank()) {
      return;
    }
    ElkLabel label = ElkGraphUtil.createLabel(edge);
    label.setText(labelText.trim());
  }

  /**
   * Reads a layout option as text with a normalized fallback.
   *
   * @param request layout request
   * @param optionName option name
   * @param fallback fallback value
   * @return configured or fallback text
   */
  private String configuredValue(LayoutRequest request, String optionName, String fallback) {
    Object value = request.options().get(optionName);
    return value == null ? normalize(fallback) : value.toString().trim();
  }

  /**
   * Infers a port side from common input/output naming conventions.
   *
   * @param portId port identifier
   * @return inferred port side
   */
  private PortSide inferPortSide(String portId) {
    String normalized = normalize(portId).toLowerCase(Locale.ROOT);
    if (normalized.endsWith("-in")
        || normalized.startsWith("in-")
        || normalized.contains("input")
        || normalized.equals("flow-in")
        || normalized.equals("resource-in")) {
      return PortSide.WEST;
    }
    if (normalized.endsWith("-out")
        || normalized.startsWith("out-")
        || normalized.contains("output")
        || normalized.equals("flow-out")
        || normalized.equals("resource-out")
        || normalized.equals("data")
        || normalized.equals("security")) {
      return PortSide.EAST;
    }
    return PortSide.EAST;
  }

  /**
   * Checks whether a coordinate pair is present and finite.
   *
   * @param x x coordinate
   * @param y y coordinate
   * @return {@code true} when both coordinates are finite
   */
  private boolean hasCoordinates(Double x, Double y) {
    return x != null && y != null && Double.isFinite(x) && Double.isFinite(y);
  }

  /**
   * Returns a positive value or a fallback.
   *
   * @param value candidate value
   * @param fallback fallback value
   * @return positive value
   */
  private double positiveOrDefault(double value, double fallback) {
    return value > 0 ? value : fallback;
  }

  /**
   * Returns a finite value or a fallback.
   *
   * @param value candidate value
   * @param fallback fallback value
   * @return finite value
   */
  private double finiteOrDefault(double value, double fallback) {
    return Double.isFinite(value) ? value : fallback;
  }

  /**
   * Checks whether two points have the same rounded coordinates.
   *
   * @param left left point
   * @param right right point
   * @return {@code true} when both rounded coordinates match
   */
  private boolean samePoint(LayoutPoint left, LayoutPoint right) {
    return same(left.x(), right.x()) && same(left.y(), right.y());
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
   * Trims nullable text.
   *
   * @param value raw value
   * @return trimmed value or empty string
   */
  private String normalize(String value) {
    return value == null ? "" : value.trim();
  }

  /**
   * Extracts a safe error message from an ELK runtime exception.
   *
   * @param exception runtime exception
   * @return non-blank message
   */
  private String safeMessage(RuntimeException exception) {
    String message = exception.getMessage();
    return message == null || message.isBlank() ? exception.getClass().getSimpleName() : message;
  }

  /** Normalized layout style used to derive ELK algorithm and spacing options. */
  private enum LayoutStyle {
    /** Default layered layout with balanced spacing. */
    BALANCED(72.0d, 42.0d, 66.0d),
    /** Layered layout with larger padding and spacing. */
    SPACIOUS(96.0d, 72.0d, 96.0d),
    /** Layered layout tuned for relaxed routed edges. */
    RELAXED(84.0d, 64.0d, 86.0d),
    /** Downward layered flow. */
    VERTICAL(80.0d, 56.0d, 80.0d),
    /** Tree layout style. */
    TREE(84.0d, 56.0d, 82.0d),
    /** Radial layout style. */
    RADIAL(96.0d, 64.0d, 90.0d),
    /** Force-directed layout style. */
    FORCE(96.0d, 60.0d, 84.0d);

    /** Graph padding for this style. */
    private final double padding;

    /** Edge-to-edge spacing for this style. */
    private final double edgeSpacing;

    /** Edge-to-node spacing for this style. */
    private final double edgeNodeSpacing;

    /**
     * Creates a layout style.
     *
     * @param padding graph padding
     * @param edgeSpacing edge-to-edge spacing
     * @param edgeNodeSpacing edge-to-node spacing
     */
    LayoutStyle(double padding, double edgeSpacing, double edgeNodeSpacing) {
      this.padding = padding;
      this.edgeSpacing = edgeSpacing;
      this.edgeNodeSpacing = edgeNodeSpacing;
    }

    /**
     * Derives a layout style from request options and profile text.
     *
     * @param request layout request
     * @return normalized layout style
     */
    static LayoutStyle from(LayoutRequest request) {
      String value =
          String.valueOf(
                  request
                      .options()
                      .getOrDefault(
                          "layoutStrategy",
                          request.options().getOrDefault("algorithm", request.profile())))
              .toUpperCase(Locale.ROOT);
      if (value.contains("SPACIOUS")) {
        return SPACIOUS;
      }
      if (value.contains("RELAXED") || value.contains("SPLINE")) {
        return RELAXED;
      }
      if (value.contains("VERTICAL")) {
        return VERTICAL;
      }
      if (value.contains("TREE")) {
        return TREE;
      }
      if (value.contains("RADIAL")) {
        return RADIAL;
      }
      if (value.contains("FORCE")) {
        return FORCE;
      }
      return BALANCED;
    }

    /**
     * Whether this style uses ELK layered algorithm specific options.
     *
     * @return {@code true} for layered styles
     */
    boolean usesLayeredOptions() {
      return switch (this) {
        case TREE, RADIAL, FORCE -> false;
        default -> true;
      };
    }

    /**
     * Returns graph padding for this style.
     *
     * @return padding
     */
    double padding() {
      return padding;
    }

    /**
     * Returns edge-to-edge spacing for this style.
     *
     * @return edge spacing
     */
    double edgeSpacing() {
      return edgeSpacing;
    }

    /**
     * Returns edge-to-node spacing for this style.
     *
     * @return edge-to-node spacing
     */
    double edgeNodeSpacing() {
      return edgeNodeSpacing;
    }
  }

  /**
   * Request passed from the platform graph view into the backend layout engine.
   *
   * @param viewId view identifier
   * @param profile layout profile name
   * @param preserveExistingPositions whether existing positions should be preserved
   * @param fixedNodeIds node ids requested as fixed-position hints
   * @param options layout algorithm options
   * @param nodes nodes to lay out
   * @param edges edges to route
   */
  public record LayoutRequest(
      String viewId,
      String profile,
      boolean preserveExistingPositions,
      List<String> fixedNodeIds,
      Map<String, Object> options,
      List<LayoutNode> nodes,
      List<LayoutEdge> edges) {

    /** Normalizes nullable collection fields to immutable empty collections. */
    public LayoutRequest {
      fixedNodeIds = fixedNodeIds == null ? List.of() : List.copyOf(fixedNodeIds);
      options = options == null ? Map.of() : Map.copyOf(options);
      nodes = nodes == null ? List.of() : List.copyOf(nodes);
      edges = edges == null ? List.of() : List.copyOf(edges);
    }
  }

  /**
   * Node included in a layout request.
   *
   * @param id node identifier
   * @param label optional node label
   * @param width node width
   * @param height node height
   * @param x existing x coordinate, when available
   * @param y existing y coordinate, when available
   * @param ports node ports
   */
  public record LayoutNode(
      String id,
      String label,
      double width,
      double height,
      Double x,
      Double y,
      List<LayoutPort> ports) {

    /** Normalizes nullable ports to an immutable empty list. */
    public LayoutNode {
      ports = ports == null ? List.of() : List.copyOf(ports);
    }
  }

  /**
   * Port included in a layout node.
   *
   * @param id port identifier
   * @param label optional port label
   * @param width port width
   * @param height port height
   * @param x existing x coordinate, when available
   * @param y existing y coordinate, when available
   */
  public record LayoutPort(
      String id, String label, double width, double height, Double x, Double y) {}

  /**
   * Edge included in a layout request.
   *
   * @param id edge identifier
   * @param label optional edge label
   * @param sourceNodeId source node identifier
   * @param targetNodeId target node identifier
   * @param sourcePortId optional source port identifier
   * @param targetPortId optional target port identifier
   */
  public record LayoutEdge(
      String id,
      String label,
      String sourceNodeId,
      String targetNodeId,
      String sourcePortId,
      String targetPortId) {}

  /**
   * Backend layout response.
   *
   * @param nodes laid-out nodes
   * @param edges routed edges
   * @param warnings non-fatal layout warnings
   */
  public record LayoutResponse(
      List<LaidOutNode> nodes, List<RoutedEdge> edges, List<String> warnings) {

    /** Normalizes nullable collection fields to immutable empty collections. */
    public LayoutResponse {
      nodes = nodes == null ? List.of() : List.copyOf(nodes);
      edges = edges == null ? List.of() : List.copyOf(edges);
      warnings = warnings == null ? List.of() : List.copyOf(warnings);
    }
  }

  /**
   * Computed node geometry.
   *
   * @param id node identifier
   * @param x x coordinate
   * @param y y coordinate
   * @param width computed width
   * @param height computed height
   */
  public record LaidOutNode(String id, double x, double y, double width, double height) {}

  /**
   * Computed route for an edge.
   *
   * @param id edge identifier
   * @param sections ordered edge sections
   * @param bendPoints flattened bend points across all sections
   */
  public record RoutedEdge(String id, List<EdgeSection> sections, List<LayoutPoint> bendPoints) {

    /** Normalizes nullable collection fields to immutable empty collections. */
    public RoutedEdge {
      sections = sections == null ? List.of() : List.copyOf(sections);
      bendPoints = bendPoints == null ? List.of() : List.copyOf(bendPoints);
    }
  }

  /**
   * One routed edge section.
   *
   * @param startPoint section start point
   * @param endPoint section end point
   * @param bendPoints section bend points
   */
  public record EdgeSection(
      LayoutPoint startPoint, LayoutPoint endPoint, List<LayoutPoint> bendPoints) {

    /** Normalizes nullable bend points to an immutable empty list. */
    public EdgeSection {
      bendPoints = bendPoints == null ? List.of() : List.copyOf(bendPoints);
    }
  }

  /**
   * Two-dimensional layout point.
   *
   * @param x x coordinate
   * @param y y coordinate
   */
  public record LayoutPoint(double x, double y) {}

  /**
   * Positioned node box used by the backend edge router.
   *
   * @param id node identifier
   * @param x x coordinate
   * @param y y coordinate
   * @param width node width
   * @param height node height
   */
  private record NodeBox(String id, double x, double y, double width, double height) {}

  /**
   * Left/right route anchor used by the backend edge router.
   *
   * @param side node side
   * @param offsetY vertical offset from the top of the node
   */
  private record RouteAnchor(String side, double offsetY) {}

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
