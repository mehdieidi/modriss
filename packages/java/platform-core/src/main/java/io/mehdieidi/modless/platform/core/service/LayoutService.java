package io.mehdieidi.modless.platform.core.service;

import io.mehdieidi.modless.platform.core.PlatformException;
import java.util.ArrayList;
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

    /**
     * Default size used when a port omits dimensions.
     */
    private static final double DEFAULT_PORT_SIZE = 10.0d;
    /**
     * Default spacing between nodes for balanced layouts.
     */
    private static final double DEFAULT_NODE_SPACING = 156.0d;
    /**
     * Default spacing between layers for balanced layouts.
     */
    private static final double DEFAULT_LAYER_SPACING = 260.0d;
    /**
     * ELK id for the layered algorithm.
     */
    private static final String LAYERED_ALGORITHM = "org.eclipse.elk.layered";
    /**
     * ELK id for the tree algorithm.
     */
    private static final String TREE_ALGORITHM = "org.eclipse.elk.mrtree";
    /**
     * ELK id for the radial algorithm.
     */
    private static final String RADIAL_ALGORITHM = "org.eclipse.elk.radial";
    /**
     * ELK id for the force-directed algorithm.
     */
    private static final String FORCE_ALGORITHM = "org.eclipse.elk.force";
    /**
     * Shared ELK layout engine; access is synchronized during layout execution.
     */
    private static final RecursiveGraphLayoutEngine LAYOUT_ENGINE =
            new RecursiveGraphLayoutEngine();

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
            ElkConnectableShape source = resolveEndpoint(
                    edgeRequest.sourceNodeId(),
                    edgeRequest.sourcePortId(),
                    nodesById,
                    portsByNodeId,
                    "source",
                    warnings);
            ElkConnectableShape target = resolveEndpoint(
                    edgeRequest.targetNodeId(),
                    edgeRequest.targetPortId(),
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
            throw new PlatformException(500,
                    "ELK layout runtime is not available on the backend classpath.");
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
                throw new PlatformException(400,
                        "Layout node '" + nodeId + "' must have positive width and height.");
            }
            if (!nodeIds.add(nodeId)) {
                throw new PlatformException(400, "Duplicate layout node id: " + nodeId);
            }
            Set<String> portIds = new LinkedHashSet<>();
            for (LayoutPort port : node.ports()) {
                String portId = normalize(port.id());
                if (portId.isEmpty()) {
                    throw new PlatformException(400,
                            "Layout port id is required for node '" + nodeId + "'.");
                }
                if (!portIds.add(portId)) {
                    throw new PlatformException(400,
                            "Duplicate layout port id '" + portId + "' for node '" + nodeId
                                    + "'.");
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
                throw new PlatformException(400,
                        "Layout edge '" + edgeId + "' references missing source node '"
                                + sourceNodeId + "'.");
            }
            if (!nodeIds.contains(targetNodeId)) {
                throw new PlatformException(400,
                        "Layout edge '" + edgeId + "' references missing target node '"
                                + targetNodeId + "'.");
            }
        }

        return request;
    }

    /**
     * Applies ELK graph-level options derived from the request profile and options.
     *
     * @param graph   ELK graph
     * @param request layout request
     */
    private void configureGraph(ElkNode graph, LayoutRequest request) {
        LayoutStyle style = LayoutStyle.from(request);
        graph.setProperty(CoreOptions.ALGORITHM, algorithm(style));
        graph.setProperty(CoreOptions.DIRECTION, direction(request));
        graph.setProperty(CoreOptions.EDGE_ROUTING, edgeRouting(request));
        graph.setProperty(CoreOptions.SPACING_NODE_NODE, nodeSpacing(request));
        graph.setProperty(LayeredOptions.SPACING_NODE_NODE_BETWEEN_LAYERS,
                layerSpacing(request));
        graph.setProperty(CoreOptions.SPACING_EDGE_EDGE, style.edgeSpacing());
        graph.setProperty(CoreOptions.SPACING_EDGE_NODE, style.edgeNodeSpacing());
        graph.setProperty(CoreOptions.PADDING, new ElkPadding(style.padding()));
        graph.setProperty(CoreOptions.HIERARCHY_HANDLING,
                HierarchyHandling.INCLUDE_CHILDREN);
        graph.setProperty(CoreOptions.ALIGNMENT, Alignment.CENTER);
        graph.setProperty(CoreOptions.PORT_ALIGNMENT_DEFAULT, PortAlignment.JUSTIFIED);
        graph.setProperty(CoreOptions.NODE_SIZE_OPTIONS,
                EnumSet.of(SizeOptions.DEFAULT_MINIMUM_SIZE,
                        SizeOptions.MINIMUM_SIZE_ACCOUNTS_FOR_PADDING,
                        SizeOptions.PORTS_OVERHANG));
        graph.setProperty(CoreOptions.SEPARATE_CONNECTED_COMPONENTS, true);
        graph.setProperty(LayeredOptions.NODE_PLACEMENT_STRATEGY,
                nodePlacementStrategy(request));
        graph.setProperty(LayeredOptions.CROSSING_MINIMIZATION_STRATEGY,
                CrossingMinimizationStrategy.LAYER_SWEEP);
        graph.setProperty(LayeredOptions.CROSSING_MINIMIZATION_GREEDY_SWITCH_TYPE,
                GreedySwitchType.TWO_SIDED);
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
     * Selects the primary layout direction.
     *
     * @param request layout request
     * @return ELK direction option
     */
    private Direction direction(LayoutRequest request) {
        String normalized = configuredValue(request, "direction", request.profile())
                .toUpperCase(Locale.ROOT);
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
        String defaultRouting = switch (style) {
            case RELAXED, FORCE, RADIAL -> "SPLINES";
            case TREE -> "POLYLINE";
            default -> "ORTHOGONAL";
        };
        String normalized = configuredValue(request, "edgeRouting", defaultRouting)
                .toUpperCase(Locale.ROOT);
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
            }
        }
        String profile = normalize(request.profile()).toUpperCase(Locale.ROOT);
        LayoutStyle style = LayoutStyle.from(request);
        if (style == LayoutStyle.SPACIOUS) {
            return 220.0d;
        }
        if (style == LayoutStyle.RELAXED || style == LayoutStyle.RADIAL
                || style == LayoutStyle.FORCE) {
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
        String defaultStrategy = style == LayoutStyle.SPACIOUS
                ? "BRANDES_KOEPF" : "NETWORK_SIMPLEX";
        String normalized = configuredValue(request, "nodePlacementStrategy",
                defaultStrategy).toUpperCase(Locale.ROOT);
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
     * @param node        ELK node
     * @param nodeRequest request node
     * @return ports keyed by id
     */
    private Map<String, ElkPort> createPorts(ElkNode node, LayoutNode nodeRequest) {
        Map<String, ElkPort> portsById = new LinkedHashMap<>();
        if (!nodeRequest.ports().isEmpty()) {
            node.setProperty(CoreOptions.PORT_CONSTRAINTS, PortConstraints.FIXED_SIDE);
        }
        for (LayoutPort portRequest : nodeRequest.ports()) {
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
     * Resolves an edge endpoint to a port when available, otherwise to the node boundary.
     *
     * @param nodeId        endpoint node id
     * @param portId        endpoint port id
     * @param nodesById     nodes keyed by id
     * @param portsByNodeId ports keyed by node id and port id
     * @param endpointName  human-readable endpoint name
     * @param warnings      mutable warning sink
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
        warnings.add("Missing " + endpointName + " port '" + portId
                + "' on node '" + nodeId + "'. Falling back to the node boundary.");
        return nodesById.get(nodeId);
    }

    /**
     * Builds platform node layout records from ELK nodes.
     *
     * @param request   original layout request
     * @param nodesById ELK nodes keyed by id
     * @return immutable laid-out node list
     */
    private List<LaidOutNode> buildNodeLayouts(
            LayoutRequest request,
            Map<String, ElkNode> nodesById) {
        List<LaidOutNode> result = new ArrayList<>();
        for (LayoutNode nodeRequest : request.nodes()) {
            ElkNode node = nodesById.get(nodeRequest.id());
            result.add(new LaidOutNode(
                    nodeRequest.id(),
                    node.getX(),
                    node.getY(),
                    node.getWidth(),
                    node.getHeight()));
        }
        return List.copyOf(result);
    }

    /**
     * Builds platform edge route records from ELK edge sections.
     *
     * @param request   original layout request
     * @param nodesById ELK nodes keyed by id
     * @param edgesById ELK edges keyed by id
     * @param warnings  mutable warning sink
     * @return immutable routed edge list
     */
    private List<RoutedEdge> buildEdgeLayouts(
            LayoutRequest request,
            Map<String, ElkNode> nodesById,
            Map<String, ElkEdge> edgesById,
            List<String> warnings) {
        List<RoutedEdge> result = new ArrayList<>();
        for (LayoutEdge edgeRequest : request.edges()) {
            ElkEdge edge = edgesById.get(edgeRequest.id());
            List<EdgeSection> sections = new ArrayList<>();
            List<LayoutPoint> bendPoints = new ArrayList<>();
            for (ElkEdgeSection section : edge.getSections()) {
                List<LayoutPoint> sectionBendPoints = new ArrayList<>();
                section.getBendPoints().forEach(point -> {
                    LayoutPoint bendPoint = new LayoutPoint(point.getX(), point.getY());
                    sectionBendPoints.add(bendPoint);
                    bendPoints.add(bendPoint);
                });
                sections.add(new EdgeSection(
                        new LayoutPoint(section.getStartX(), section.getStartY()),
                        new LayoutPoint(section.getEndX(), section.getEndY()),
                        List.copyOf(sectionBendPoints)));
            }
            if (sections.isEmpty()) {
                warnings.add("Edge '" + edgeRequest.id()
                        + "' was laid out without explicit sections.");
                sections.add(fallbackSection(edgeRequest, nodesById, warnings));
            }
            result.add(new RoutedEdge(
                    edgeRequest.id(),
                    List.copyOf(sections),
                    List.copyOf(bendPoints)));
        }
        return List.copyOf(result);
    }

    /**
     * Creates a simple source-to-target route when ELK omits explicit sections.
     *
     * @param edge      edge request
     * @param nodesById ELK nodes keyed by id
     * @param warnings  mutable warning sink
     * @return fallback edge section
     */
    private EdgeSection fallbackSection(LayoutEdge edge, Map<String, ElkNode> nodesById,
            List<String> warnings) {
        ElkNode source = nodesById.get(edge.sourceNodeId());
        ElkNode target = nodesById.get(edge.targetNodeId());
        if (source == null || target == null) {
            warnings.add("Edge '" + edge.id()
                    + "' could not be given a fallback route because an endpoint was missing.");
            return new EdgeSection(new LayoutPoint(0, 0), new LayoutPoint(0, 0), List.of());
        }
        LayoutPoint start = new LayoutPoint(
                finiteOrDefault(source.getX(), 0) + source.getWidth(),
                finiteOrDefault(source.getY(), 0) + source.getHeight() / 2.0d);
        LayoutPoint end = new LayoutPoint(
                finiteOrDefault(target.getX(), 0),
                finiteOrDefault(target.getY(), 0) + target.getHeight() / 2.0d);
        return new EdgeSection(start, end, List.of());
    }

    /**
     * Adds a text label to an ELK node when present.
     *
     * @param node      ELK node
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
     * @param port      ELK port
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
     * @param edge      ELK edge
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
     * @param request    layout request
     * @param optionName option name
     * @param fallback   fallback value
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
        if (normalized.endsWith("-in") || normalized.startsWith("in-")
                || normalized.contains("input") || normalized.equals("flow-in")
                || normalized.equals("resource-in")) {
            return PortSide.WEST;
        }
        if (normalized.endsWith("-out") || normalized.startsWith("out-")
                || normalized.contains("output") || normalized.equals("flow-out")
                || normalized.equals("resource-out") || normalized.equals("data")
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
     * @param value    candidate value
     * @param fallback fallback value
     * @return positive value
     */
    private double positiveOrDefault(double value, double fallback) {
        return value > 0 ? value : fallback;
    }

    /**
     * Returns a finite value or a fallback.
     *
     * @param value    candidate value
     * @param fallback fallback value
     * @return finite value
     */
    private double finiteOrDefault(double value, double fallback) {
        return Double.isFinite(value) ? value : fallback;
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
        return message == null || message.isBlank() ? exception.getClass().getSimpleName()
                : message;
    }

    /**
     * Normalized layout style used to derive ELK algorithm and spacing options.
     */
    private enum LayoutStyle {
        /**
         * Default layered layout with balanced spacing.
         */
        BALANCED(72.0d, 42.0d, 66.0d),
        /**
         * Layered layout with larger padding and spacing.
         */
        SPACIOUS(96.0d, 72.0d, 96.0d),
        /**
         * Layered layout tuned for relaxed routed edges.
         */
        RELAXED(84.0d, 64.0d, 86.0d),
        /**
         * Downward layered flow.
         */
        VERTICAL(80.0d, 56.0d, 80.0d),
        /**
         * Tree layout style.
         */
        TREE(84.0d, 56.0d, 82.0d),
        /**
         * Radial layout style.
         */
        RADIAL(96.0d, 64.0d, 90.0d),
        /**
         * Force-directed layout style.
         */
        FORCE(96.0d, 60.0d, 84.0d);

        /**
         * Graph padding for this style.
         */
        private final double padding;
        /**
         * Edge-to-edge spacing for this style.
         */
        private final double edgeSpacing;
        /**
         * Edge-to-node spacing for this style.
         */
        private final double edgeNodeSpacing;

        /**
         * Creates a layout style.
         *
         * @param padding         graph padding
         * @param edgeSpacing     edge-to-edge spacing
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
            String value = String.valueOf(request.options().getOrDefault("layoutStrategy",
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
     * @param viewId                    view identifier
     * @param profile                   layout profile name
     * @param preserveExistingPositions whether existing positions should be preserved
     * @param fixedNodeIds              node ids requested as fixed-position hints
     * @param options                   layout algorithm options
     * @param nodes                     nodes to lay out
     * @param edges                     edges to route
     */
    public record LayoutRequest(
            String viewId,
            String profile,
            boolean preserveExistingPositions,
            List<String> fixedNodeIds,
            Map<String, Object> options,
            List<LayoutNode> nodes,
            List<LayoutEdge> edges) {

        /**
         * Normalizes nullable collection fields to immutable empty collections.
         */
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
     * @param id     node identifier
     * @param label  optional node label
     * @param width  node width
     * @param height node height
     * @param x      existing x coordinate, when available
     * @param y      existing y coordinate, when available
     * @param ports  node ports
     */
    public record LayoutNode(
            String id,
            String label,
            double width,
            double height,
            Double x,
            Double y,
            List<LayoutPort> ports) {

        /**
         * Normalizes nullable ports to an immutable empty list.
         */
        public LayoutNode {
            ports = ports == null ? List.of() : List.copyOf(ports);
        }
    }

    /**
     * Port included in a layout node.
     *
     * @param id     port identifier
     * @param label  optional port label
     * @param width  port width
     * @param height port height
     * @param x      existing x coordinate, when available
     * @param y      existing y coordinate, when available
     */
    public record LayoutPort(
            String id,
            String label,
            double width,
            double height,
            Double x,
            Double y) {

    }

    /**
     * Edge included in a layout request.
     *
     * @param id           edge identifier
     * @param label        optional edge label
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
            String targetPortId) {

    }

    /**
     * Backend layout response.
     *
     * @param nodes    laid-out nodes
     * @param edges    routed edges
     * @param warnings non-fatal layout warnings
     */
    public record LayoutResponse(
            List<LaidOutNode> nodes,
            List<RoutedEdge> edges,
            List<String> warnings) {

        /**
         * Normalizes nullable collection fields to immutable empty collections.
         */
        public LayoutResponse {
            nodes = nodes == null ? List.of() : List.copyOf(nodes);
            edges = edges == null ? List.of() : List.copyOf(edges);
            warnings = warnings == null ? List.of() : List.copyOf(warnings);
        }
    }

    /**
     * Computed node geometry.
     *
     * @param id     node identifier
     * @param x      x coordinate
     * @param y      y coordinate
     * @param width  computed width
     * @param height computed height
     */
    public record LaidOutNode(
            String id,
            double x,
            double y,
            double width,
            double height) {

    }

    /**
     * Computed route for an edge.
     *
     * @param id         edge identifier
     * @param sections   ordered edge sections
     * @param bendPoints flattened bend points across all sections
     */
    public record RoutedEdge(
            String id,
            List<EdgeSection> sections,
            List<LayoutPoint> bendPoints) {

        /**
         * Normalizes nullable collection fields to immutable empty collections.
         */
        public RoutedEdge {
            sections = sections == null ? List.of() : List.copyOf(sections);
            bendPoints = bendPoints == null ? List.of() : List.copyOf(bendPoints);
        }
    }

    /**
     * One routed edge section.
     *
     * @param startPoint section start point
     * @param endPoint   section end point
     * @param bendPoints section bend points
     */
    public record EdgeSection(
            LayoutPoint startPoint,
            LayoutPoint endPoint,
            List<LayoutPoint> bendPoints) {

        /**
         * Normalizes nullable bend points to an immutable empty list.
         */
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
    public record LayoutPoint(double x, double y) {

    }
}
