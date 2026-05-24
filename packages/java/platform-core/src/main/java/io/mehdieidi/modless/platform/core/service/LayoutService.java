package io.mehdieidi.modless.platform.core.service;

import io.mehdieidi.modless.platform.core.PlatformException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.eclipse.elk.core.RecursiveGraphLayoutEngine;
import org.eclipse.elk.core.math.ElkPadding;
import org.eclipse.elk.core.options.CoreOptions;
import org.eclipse.elk.core.options.Direction;
import org.eclipse.elk.core.options.EdgeRouting;
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

public final class LayoutService {

    private static final double DEFAULT_PORT_SIZE = 10.0d;
    private static final double DEFAULT_NODE_SPACING = 48.0d;
    private static final String LAYERED_ALGORITHM = "org.eclipse.elk.layered";

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
            new RecursiveGraphLayoutEngine().layout(graph, new BasicProgressMonitor());
        } catch (NoClassDefFoundError | ExceptionInInitializerError exception) {
            throw new PlatformException(500,
                    "ELK layout runtime is not available on the backend classpath.");
        } catch (RuntimeException exception) {
            throw new PlatformException(400, "ELK layout failed: " + safeMessage(exception));
        }

        return new LayoutResponse(
                buildNodeLayouts(normalizedRequest, nodesById),
                buildEdgeLayouts(normalizedRequest, edgesById, warnings),
                List.copyOf(warnings));
    }

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

    private void configureGraph(ElkNode graph, LayoutRequest request) {
        graph.setProperty(CoreOptions.ALGORITHM, LAYERED_ALGORITHM);
        graph.setProperty(CoreOptions.DIRECTION, direction(request));
        graph.setProperty(CoreOptions.EDGE_ROUTING, edgeRouting(request));
        graph.setProperty(CoreOptions.SPACING_NODE_NODE, nodeSpacing(request));
        graph.setProperty(CoreOptions.PADDING, new ElkPadding(24));
    }

    private Direction direction(LayoutRequest request) {
        String normalized = configuredValue(request, "direction", request.profile())
                .toUpperCase(Locale.ROOT);
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

    private EdgeRouting edgeRouting(LayoutRequest request) {
        String normalized = configuredValue(request, "edgeRouting", "ORTHOGONAL")
                .toUpperCase(Locale.ROOT);
        if ("SPLINE".equals(normalized) || "SPLINES".equals(normalized)) {
            return EdgeRouting.SPLINES;
        }
        if ("POLYLINE".equals(normalized)) {
            return EdgeRouting.POLYLINE;
        }
        return EdgeRouting.ORTHOGONAL;
    }

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
        if (profile.contains("SECURITY") || profile.contains("IAM")) {
            return 64.0d;
        }
        if (profile.contains("OVERLAY")) {
            return 36.0d;
        }
        return DEFAULT_NODE_SPACING;
    }

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

    private List<RoutedEdge> buildEdgeLayouts(
            LayoutRequest request,
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
            }
            result.add(new RoutedEdge(
                    edgeRequest.id(),
                    List.copyOf(sections),
                    List.copyOf(bendPoints)));
        }
        return List.copyOf(result);
    }

    private void addLabel(ElkNode node, String labelText) {
        if (labelText == null || labelText.isBlank()) {
            return;
        }
        ElkLabel label = ElkGraphUtil.createLabel(node);
        label.setText(labelText.trim());
    }

    private void addLabel(ElkPort port, String labelText) {
        if (labelText == null || labelText.isBlank()) {
            return;
        }
        ElkLabel label = ElkGraphUtil.createLabel(port);
        label.setText(labelText.trim());
    }

    private void addLabel(ElkEdge edge, String labelText) {
        if (labelText == null || labelText.isBlank()) {
            return;
        }
        ElkLabel label = ElkGraphUtil.createLabel(edge);
        label.setText(labelText.trim());
    }

    private String configuredValue(LayoutRequest request, String optionName, String fallback) {
        Object value = request.options().get(optionName);
        return value == null ? normalize(fallback) : value.toString().trim();
    }

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

    private boolean hasCoordinates(Double x, Double y) {
        return x != null && y != null && Double.isFinite(x) && Double.isFinite(y);
    }

    private double positiveOrDefault(double value, double fallback) {
        return value > 0 ? value : fallback;
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private String safeMessage(RuntimeException exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank() ? exception.getClass().getSimpleName()
                : message;
    }

    public record LayoutRequest(
            String viewId,
            String profile,
            boolean preserveExistingPositions,
            List<String> fixedNodeIds,
            Map<String, Object> options,
            List<LayoutNode> nodes,
            List<LayoutEdge> edges) {

        public LayoutRequest {
            fixedNodeIds = fixedNodeIds == null ? List.of() : List.copyOf(fixedNodeIds);
            options = options == null ? Map.of() : Map.copyOf(options);
            nodes = nodes == null ? List.of() : List.copyOf(nodes);
            edges = edges == null ? List.of() : List.copyOf(edges);
        }
    }

    public record LayoutNode(
            String id,
            String label,
            double width,
            double height,
            Double x,
            Double y,
            List<LayoutPort> ports) {

        public LayoutNode {
            ports = ports == null ? List.of() : List.copyOf(ports);
        }
    }

    public record LayoutPort(
            String id,
            String label,
            double width,
            double height,
            Double x,
            Double y) {

    }

    public record LayoutEdge(
            String id,
            String label,
            String sourceNodeId,
            String targetNodeId,
            String sourcePortId,
            String targetPortId) {

    }

    public record LayoutResponse(
            List<LaidOutNode> nodes,
            List<RoutedEdge> edges,
            List<String> warnings) {

        public LayoutResponse {
            nodes = nodes == null ? List.of() : List.copyOf(nodes);
            edges = edges == null ? List.of() : List.copyOf(edges);
            warnings = warnings == null ? List.of() : List.copyOf(warnings);
        }
    }

    public record LaidOutNode(
            String id,
            double x,
            double y,
            double width,
            double height) {

    }

    public record RoutedEdge(
            String id,
            List<EdgeSection> sections,
            List<LayoutPoint> bendPoints) {

        public RoutedEdge {
            sections = sections == null ? List.of() : List.copyOf(sections);
            bendPoints = bendPoints == null ? List.of() : List.copyOf(bendPoints);
        }
    }

    public record EdgeSection(
            LayoutPoint startPoint,
            LayoutPoint endPoint,
            List<LayoutPoint> bendPoints) {

        public EdgeSection {
            bendPoints = bendPoints == null ? List.of() : List.copyOf(bendPoints);
        }
    }

    public record LayoutPoint(double x, double y) {

    }
}
