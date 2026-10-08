package io.mehdieidi.modriss.platform.modeling.layout;

import io.mehdieidi.modriss.platform.kernel.PlatformException;
import java.util.ArrayList;
import java.util.Comparator;
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
import org.eclipse.elk.core.math.ElkMargin;
import org.eclipse.elk.core.math.ElkPadding;
import org.eclipse.elk.core.options.CoreOptions;
import org.eclipse.elk.core.options.Direction;
import org.eclipse.elk.core.options.EdgeRouting;
import org.eclipse.elk.core.options.PortAlignment;
import org.eclipse.elk.core.options.PortConstraints;
import org.eclipse.elk.core.options.PortSide;
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

  /** The single automatic layout algorithm used by every view. */
  public static final String ALGORITHM = "org.eclipse.elk.layered";

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
    for (LayoutNode nodeRequest :
        normalizedRequest.nodes().stream().sorted(Comparator.comparing(LayoutNode::id)).toList()) {
      ElkNode node = ElkGraphUtil.createNode(graph);
      node.setIdentifier(nodeRequest.id());
      node.setDimensions(nodeRequest.width(), nodeRequest.height());
      node.setProperty(
          CoreOptions.SPACING_PORTS_SURROUNDING, portInsets(normalizedRequest, nodeRequest));
      addLabel(node, nodeRequest.label());
      nodesById.put(nodeRequest.id(), node);
      portsByNodeId.put(nodeRequest.id(), createPorts(node, nodeRequest));
    }

    for (LayoutEdge edgeRequest :
        normalizedRequest.edges().stream().sorted(Comparator.comparing(LayoutEdge::id)).toList()) {
      ElkConnectableShape source =
          resolveEndpoint(
              edgeRequest.sourceNodeId(),
              edgeRequest.sourcePortId(),
              nodesById,
              portsByNodeId,
              "source",
              edgeRequest.id(),
              warnings);
      ElkConnectableShape target =
          resolveEndpoint(
              edgeRequest.targetNodeId(),
              edgeRequest.targetPortId(),
              nodesById,
              portsByNodeId,
              "target",
              edgeRequest.id(),
              warnings);
      ElkEdge edge = ElkGraphUtil.createSimpleEdge(source, target);
      edge.setIdentifier(edgeRequest.id());
      edgesById.put(edgeRequest.id(), edge);
    }

    try {
      synchronized (LAYOUT_ENGINE) {
        LAYOUT_ENGINE.layout(graph, new BasicProgressMonitor());
        // Discover a low-crossing port order, then constrain attachments to the rendered
        // icon area and let ELK route again. No geometry is changed after the final pass.
        fixAutomaticPortPositions(normalizedRequest, nodesById);
        edgesById.values().forEach(edge -> edge.getSections().clear());
        LAYOUT_ENGINE.layout(graph, new BasicProgressMonitor());
      }
    } catch (NoClassDefFoundError | ExceptionInInitializerError exception) {
      throw new PlatformException(
          500, "ELK layout runtime is not available on the backend classpath.");
    } catch (StackOverflowError error) {
      throw new PlatformException(400, "ELK layout failed: graph topology exceeded layout limits.");
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
      if (!Double.isFinite(node.width())
          || !Double.isFinite(node.height())
          || node.width() <= 0
          || node.height() <= 0) {
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
   * Applies the same deterministic, orthogonal layered layout to every graph.
   *
   * @param graph ELK graph
   * @param request layout request
   */
  private void configureGraph(ElkNode graph, LayoutRequest request) {
    graph.setProperty(CoreOptions.ALGORITHM, ALGORITHM);
    graph.setProperty(CoreOptions.DIRECTION, Direction.RIGHT);
    graph.setProperty(CoreOptions.EDGE_ROUTING, EdgeRouting.ORTHOGONAL);
    graph.setProperty(CoreOptions.RANDOM_SEED, 1);
    graph.setProperty(CoreOptions.SPACING_NODE_NODE, 48.0d);
    graph.setProperty(CoreOptions.SPACING_COMPONENT_COMPONENT, 96.0d);
    graph.setProperty(CoreOptions.SPACING_EDGE_EDGE, 8.0d);
    graph.setProperty(CoreOptions.SPACING_EDGE_NODE, 24.0d);
    graph.setProperty(CoreOptions.PADDING, new ElkPadding(48.0d));
    graph.setProperty(CoreOptions.PORT_ALIGNMENT_DEFAULT, PortAlignment.JUSTIFIED);
    graph.setProperty(CoreOptions.SPACING_PORT_PORT, 3.0d);
    graph.setProperty(CoreOptions.SEPARATE_CONNECTED_COMPONENTS, true);
    graph.setProperty(CoreOptions.ASPECT_RATIO, 1.6d);
    graph.setProperty(LayeredOptions.SPACING_NODE_NODE_BETWEEN_LAYERS, 80.0d);
    graph.setProperty(LayeredOptions.SPACING_EDGE_NODE_BETWEEN_LAYERS, 24.0d);
    graph.setProperty(LayeredOptions.SPACING_EDGE_EDGE_BETWEEN_LAYERS, 8.0d);
    graph.setProperty(
        LayeredOptions.NODE_PLACEMENT_STRATEGY, NodePlacementStrategy.NETWORK_SIMPLEX);
    graph.setProperty(
        LayeredOptions.CROSSING_MINIMIZATION_STRATEGY, CrossingMinimizationStrategy.LAYER_SWEEP);
    graph.setProperty(
        LayeredOptions.CROSSING_MINIMIZATION_GREEDY_SWITCH_TYPE, GreedySwitchType.TWO_SIDED);
    graph.setProperty(LayeredOptions.THOROUGHNESS, 32);
    graph.setProperty(LayeredOptions.NODE_PLACEMENT_FAVOR_STRAIGHT_EDGES, false);
    graph.setProperty(LayeredOptions.MERGE_EDGES, false);
  }

  /** Reads finite attachment insets while keeping a nonempty interval for ports. */
  private ElkMargin portInsets(LayoutRequest request, LayoutNode node) {
    Object configured = request.options().get("portInsetsByNodeId");
    Map<?, ?> byNode = configured instanceof Map<?, ?> map ? map : Map.of();
    Object nodeInsets = byNode.get(node.id());
    Map<?, ?> margins = nodeInsets instanceof Map<?, ?> map ? map : Map.of();
    double fallback = Math.min(8.0d, node.height() / 4.0d);
    double top = margins.get("top") instanceof Number value ? value.doubleValue() : fallback;
    double bottom = margins.get("bottom") instanceof Number value ? value.doubleValue() : fallback;
    if (!Double.isFinite(top)
        || !Double.isFinite(bottom)
        || top < 0
        || bottom < 0
        || top + bottom >= node.height()) {
      throw new PlatformException(400, "Invalid port insets for node '" + node.id() + "'.");
    }
    return new ElkMargin(top, 0, bottom, 0);
  }

  /** Keeps ELK's optimized port order within the measured attachment area. */
  private void fixAutomaticPortPositions(LayoutRequest request, Map<String, ElkNode> nodesById) {
    for (LayoutNode nodeRequest : request.nodes()) {
      if (!nodeRequest.ports().isEmpty()) {
        continue;
      }
      ElkNode node = nodesById.get(nodeRequest.id());
      ElkMargin insets = portInsets(request, nodeRequest);
      double available = node.getHeight() - insets.top - insets.bottom;
      for (PortSide side : List.of(PortSide.WEST, PortSide.EAST)) {
        List<ElkPort> ports =
            node.getPorts().stream()
                .filter(port -> port.getProperty(CoreOptions.PORT_SIDE) == side)
                .sorted(
                    Comparator.comparingDouble(ElkPort::getY).thenComparing(ElkPort::getIdentifier))
                .toList();
        for (int index = 0; index < ports.size(); index++) {
          ports
              .get(index)
              .setLocation(
                  side == PortSide.WEST ? 0 : node.getWidth(),
                  Math.round(insets.top + available * (index + 1) / (ports.size() + 1)));
        }
      }
      node.setProperty(CoreOptions.PORT_CONSTRAINTS, PortConstraints.FIXED_POS);
    }
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
    List<LayoutPort> ports = nodeRequest.ports();
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
   * Resolves explicit ports, or gives each automatic endpoint its own movable side port.
   *
   * @param nodeId endpoint node id
   * @param portId endpoint port id
   * @param nodesById nodes keyed by id
   * @param portsByNodeId ports keyed by node id and port id
   * @param endpointName human-readable endpoint name
   * @param edgeId edge identifier used for automatic ports
   * @param warnings mutable warning sink
   * @return resolved ELK connectable shape
   */
  private ElkConnectableShape resolveEndpoint(
      String nodeId,
      String portId,
      Map<String, ElkNode> nodesById,
      Map<String, Map<String, ElkPort>> portsByNodeId,
      String endpointName,
      String edgeId,
      List<String> warnings) {
    if (portId == null || portId.isBlank()) {
      ElkNode node = nodesById.get(nodeId);
      node.setProperty(CoreOptions.PORT_CONSTRAINTS, PortConstraints.FIXED_SIDE);
      ElkPort port = ElkGraphUtil.createPort(node);
      port.setIdentifier(edgeId + "-" + endpointName);
      port.setDimensions(0, 0);
      port.setProperty(
          CoreOptions.PORT_SIDE, "source".equals(endpointName) ? PortSide.EAST : PortSide.WEST);
      return port;
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
    for (LayoutEdge edgeRequest : request.edges()) {
      EdgeSection section = elkSection(edgesById.get(edgeRequest.id()));
      if (section == null) {
        throw new PlatformException(500, "ELK omitted route for edge '" + edgeRequest.id() + "'.");
      }
      List<EdgeSection> sections = List.of(section);
      List<LayoutPoint> bendPoints = section.bendPoints();
      result.add(new RoutedEdge(edgeRequest.id(), List.copyOf(sections), List.copyOf(bendPoints)));
    }
    return List.copyOf(result);
  }

  /**
   * Reads the routed ELK section without replacing its obstacle-aware geometry.
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
}
