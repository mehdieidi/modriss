package io.mehdieidi.modriss.platform.modeling.layout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.mehdieidi.modriss.platform.kernel.PlatformException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Tests backend ELK layout request validation, routing, and strategy selection. */
class LayoutServiceTest {

  /** Service under test. */
  private final LayoutService service = new LayoutService();

  /** Verifies that a basic layered layout returns positioned nodes and routed edge sections. */
  @Test
  void computesLayeredLayoutWithEdgeRouting() {
    LayoutService.LayoutResponse response =
        service.layout(
            new LayoutService.LayoutRequest(
                "view-1",
                "DEFAULT_LAYERED",
                true,
                List.of(),
                Map.of(),
                List.of(
                    new LayoutService.LayoutNode(
                        "start",
                        "Start",
                        180,
                        90,
                        0.0,
                        0.0,
                        List.of(
                            new LayoutService.LayoutPort("flow-out", "out", 10, 10, null, null))),
                    new LayoutService.LayoutNode(
                        "finish",
                        "Finish",
                        180,
                        90,
                        0.0,
                        0.0,
                        List.of(
                            new LayoutService.LayoutPort("flow-in", "in", 10, 10, null, null)))),
                List.of(
                    new LayoutService.LayoutEdge(
                        "edge-1", "flow", "start", "finish", "flow-out", "flow-in"))));

    assertFalse(response.nodes().isEmpty());
    assertFalse(response.edges().isEmpty());
    assertTrue(response.nodes().stream().allMatch(node -> node.width() > 0 && node.height() > 0));

    LayoutService.RoutedEdge edge = response.edges().get(0);
    assertNotNull(edge);
    assertFalse(edge.sections().isEmpty());
    assertNotNull(edge.sections().get(0).startPoint());
    assertNotNull(edge.sections().get(edge.sections().size() - 1).endPoint());
  }

  /** Verifies that an edge targeting a missing node is rejected before ELK runs. */
  @Test
  void rejectsEdgesThatReferenceMissingNodes() {
    PlatformException exception =
        assertThrows(
            PlatformException.class,
            () ->
                service.layout(
                    new LayoutService.LayoutRequest(
                        null,
                        "DEFAULT_LAYERED",
                        false,
                        List.of(),
                        Map.of(),
                        List.of(
                            new LayoutService.LayoutNode(
                                "only-node", "Only", 180, 90, 0.0, 0.0, List.of())),
                        List.of(
                            new LayoutService.LayoutEdge(
                                "edge-1", "broken", "only-node", "missing", null, null)))));

    assertTrue(exception.getMessage().contains("missing target node"));
  }

  /** Verifies that legacy strategy names resolve to one layout without dropping content. */
  @Test
  void legacyStrategyNamesUseTheSingleLayout() {
    List<String> strategies =
        List.of(
            "SPACIOUS_LAYERED",
            "BALANCED_LAYERED",
            "RELAXED_SPLINES",
            "VERTICAL_FLOW",
            "TREE",
            "RADIAL",
            "FORCE");

    for (String strategy : strategies) {
      LayoutService.LayoutResponse response = layoutForStrategy(strategy);

      assertEquals(3, response.nodes().size(), strategy);
      assertEquals(2, response.edges().size(), strategy);
      assertEquals(layoutForStrategy("LAYERED"), response, strategy);
    }
  }

  /** Verifies that backend-routed edge sections do not sit on top of each other. */
  @Test
  void separatesOverlappingEdgeCorridors() {
    LayoutService.LayoutResponse response =
        service.layout(
            new LayoutService.LayoutRequest(
                "view-overlap",
                "DEFAULT_LAYERED",
                false,
                List.of(),
                Map.of("layoutStrategy", "SPACIOUS_LAYERED"),
                List.of(
                    new LayoutService.LayoutNode("a", "A", 180, 90, 0.0, 0.0, List.of()),
                    new LayoutService.LayoutNode("b", "B", 180, 90, 300.0, 0.0, List.of()),
                    new LayoutService.LayoutNode("c", "C", 180, 90, 300.0, 160.0, List.of()),
                    new LayoutService.LayoutNode("d", "D", 180, 90, 0.0, 160.0, List.of())),
                List.of(
                    new LayoutService.LayoutEdge("edge-ab", "ab", "a", "b", null, null),
                    new LayoutService.LayoutEdge("edge-ac", "ac", "a", "c", null, null),
                    new LayoutService.LayoutEdge("edge-db", "db", "d", "b", null, null),
                    new LayoutService.LayoutEdge("edge-dc", "dc", "d", "c", null, null))));

    assertNoOverlappingSegments(response);
  }

  /** Verifies that dense parallel edges do not drift away based on their edge-list position. */
  @Test
  void keepsDenseParallelRoutesNearTheirNodes() {
    List<LayoutService.LayoutEdge> edges = new ArrayList<>();
    for (int index = 0; index < 20; index++) {
      edges.add(
          new LayoutService.LayoutEdge(
              "parallel-" + index, "flow", "source", "target", null, null));
    }

    LayoutService.LayoutResponse response =
        service.layout(
            new LayoutService.LayoutRequest(
                "view-dense-parallel",
                "DEFAULT_LAYERED",
                false,
                List.of(),
                Map.of("layoutStrategy", "SPACIOUS_LAYERED"),
                List.of(
                    new LayoutService.LayoutNode("source", "Source", 180, 90, 0.0, 0.0, List.of()),
                    new LayoutService.LayoutNode("target", "Target", 180, 90, 0.0, 0.0, List.of())),
                edges));

    double minNodeY =
        response.nodes().stream().mapToDouble(LayoutService.LaidOutNode::y).min().orElse(0);
    double maxNodeY =
        response.nodes().stream().mapToDouble(node -> node.y() + node.height()).max().orElse(0);
    response.edges().stream()
        .flatMap(edge -> edge.sections().stream())
        .flatMap(section -> routePoints(section).stream())
        .forEach(
            point -> {
              assertTrue(
                  point.y() >= minNodeY - 500,
                  () -> "Route escaped above the node cluster: " + point.y() + " < " + minNodeY);
              assertTrue(
                  point.y() <= maxNodeY + 500,
                  () -> "Route escaped below the node cluster: " + point.y() + " > " + maxNodeY);
            });
  }

  /** Verifies that legacy strategy hints cannot change the canonical layout. */
  @Test
  void layoutIsRepeatable() {
    String balanced = geometrySignature(layoutForStrategy("BALANCED_LAYERED"));

    assertEquals(balanced, geometrySignature(layoutForStrategy("TREE")));
    assertEquals(balanced, geometrySignature(layoutForStrategy("RADIAL")));
    assertEquals(balanced, geometrySignature(layoutForStrategy("FORCE")));
  }

  /** Automatic ports stay clear of the text below an icon after ELK's final route pass. */
  @Test
  void respectsMeasuredAttachmentArea() {
    List<LayoutService.LayoutEdge> edges = new ArrayList<>();
    for (int index = 0; index < 12; index++) {
      edges.add(new LayoutService.LayoutEdge("edge-" + index, "flow", "a", "b", null, null));
    }
    LayoutService.LayoutResponse response =
        service.layout(
            new LayoutService.LayoutRequest(
                "icons",
                "",
                false,
                List.of(),
                Map.of(
                    "portInsetsByNodeId",
                    Map.of(
                        "a", Map.of("top", 10, "bottom", 68),
                        "b", Map.of("top", 10, "bottom", 68))),
                List.of(
                    new LayoutService.LayoutNode(
                        "a", "Multiline label", 120, 138, null, null, List.of()),
                    new LayoutService.LayoutNode(
                        "b", "Other multiline label", 120, 138, null, null, List.of())),
                edges));
    LayoutService.LaidOutNode a = response.nodes().get(0);
    LayoutService.LaidOutNode b = response.nodes().get(1);
    for (LayoutService.RoutedEdge edge : response.edges()) {
      LayoutService.EdgeSection section = edge.sections().get(0);
      assertTrue(section.startPoint().y() - a.y() >= 10);
      assertTrue(section.startPoint().y() - a.y() <= 70);
      assertTrue(section.endPoint().y() - b.y() >= 10);
      assertTrue(section.endPoint().y() - b.y() <= 70);
    }
    assertNoOverlappingSegments(response);
  }

  /** Cycles, long edges, disconnected nodes and self loops remain orthogonal and avoid nodes. */
  @Test
  void routesCyclicGraphAroundNodeObstacles() {
    List<LayoutService.LayoutNode> nodes =
        List.of(
            new LayoutService.LayoutNode("a", "A", 120, 140, null, null, List.of()),
            new LayoutService.LayoutNode("b", "B", 160, 110, null, null, List.of()),
            new LayoutService.LayoutNode("c", "C", 120, 170, null, null, List.of()),
            new LayoutService.LayoutNode("d", "D", 120, 120, null, null, List.of()),
            new LayoutService.LayoutNode("isolated", "Isolated", 120, 120, null, null, List.of()));
    List<LayoutService.LayoutEdge> edges =
        List.of(
            new LayoutService.LayoutEdge("ab", "flow", "a", "b", null, null),
            new LayoutService.LayoutEdge("bc", "flow", "b", "c", null, null),
            new LayoutService.LayoutEdge("ca", "feedback", "c", "a", null, null),
            new LayoutService.LayoutEdge("ad", "long", "a", "d", null, null),
            new LayoutService.LayoutEdge("cd", "flow", "c", "d", null, null),
            new LayoutService.LayoutEdge("bb", "loop", "b", "b", null, null));
    LayoutService.LayoutResponse response =
        service.layout(
            new LayoutService.LayoutRequest(
                "obstacles", "DEFAULT_LAYERED", false, List.of(), Map.of(), nodes, edges));
    assertEquals(nodes.size(), response.nodes().size());
    assertEquals(edges.size(), response.edges().size());
    assertTrue(response.warnings().isEmpty());
    for (LayoutService.RoutedEdge edge : response.edges()) {
      List<LayoutService.LayoutPoint> points = routePoints(edge.sections().get(0));
      for (int index = 1; index < points.size(); index++) {
        LayoutService.LayoutPoint start = points.get(index - 1);
        LayoutService.LayoutPoint end = points.get(index);
        assertTrue(
            Math.abs(start.x() - end.x()) < 0.01 || Math.abs(start.y() - end.y()) < 0.01,
            edge.id() + " has diagonal segment " + start + " -> " + end);
        for (LayoutService.LaidOutNode node : response.nodes()) {
          boolean horizontal = Math.abs(start.y() - end.y()) < 0.01;
          boolean intersects =
              horizontal
                  ? start.y() > node.y() + 0.01
                      && start.y() < node.y() + node.height() - 0.01
                      && Math.max(start.x(), end.x()) > node.x() + 0.01
                      && Math.min(start.x(), end.x()) < node.x() + node.width() - 0.01
                  : start.x() > node.x() + 0.01
                      && start.x() < node.x() + node.width() - 0.01
                      && Math.max(start.y(), end.y()) > node.y() + 0.01
                      && Math.min(start.y(), end.y()) < node.y() + node.height() - 0.01;
          assertFalse(intersects, edge.id() + " intersects " + node.id());
        }
      }
    }
    List<LayoutService.LayoutNode> reversedNodes = new ArrayList<>(nodes);
    List<LayoutService.LayoutEdge> reversedEdges = new ArrayList<>(edges);
    java.util.Collections.reverse(reversedNodes);
    java.util.Collections.reverse(reversedEdges);
    LayoutService.LayoutResponse shuffled =
        service.layout(
            new LayoutService.LayoutRequest(
                "obstacles",
                "DEFAULT_LAYERED",
                false,
                List.of(),
                Map.of(),
                reversedNodes,
                reversedEdges));
    assertEquals(geometrySignature(response), geometrySignature(shuffled));
    assertEquals(
        response.edges().stream()
            .sorted(java.util.Comparator.comparing(LayoutService.RoutedEdge::id))
            .toList(),
        shuffled.edges().stream()
            .sorted(java.util.Comparator.comparing(LayoutService.RoutedEdge::id))
            .toList());
  }

  /**
   * Computes a small layout for a strategy.
   *
   * @param strategy layout strategy
   * @return layout response
   */
  private LayoutService.LayoutResponse layoutForStrategy(String strategy) {
    return service.layout(
        new LayoutService.LayoutRequest(
            "view-" + strategy.toLowerCase(),
            "DEFAULT_LAYERED",
            false,
            List.of(),
            Map.of("layoutStrategy", strategy),
            List.of(
                new LayoutService.LayoutNode("a", "A", 180, 90, 0.0, 0.0, List.of()),
                new LayoutService.LayoutNode("b", "B", 180, 90, 0.0, 0.0, List.of()),
                new LayoutService.LayoutNode("c", "C", 180, 90, 0.0, 0.0, List.of())),
            List.of(
                new LayoutService.LayoutEdge("edge-ab", "ab", "a", "b", null, null),
                new LayoutService.LayoutEdge("edge-ac", "ac", "a", "c", null, null))));
  }

  /**
   * Builds a deterministic signature from node coordinates.
   *
   * @param response layout response
   * @return compact coordinate signature
   */
  private String geometrySignature(LayoutService.LayoutResponse response) {
    return response.nodes().stream()
        .map(node -> node.id() + "=" + Math.round(node.x()) + "," + Math.round(node.y()))
        .sorted()
        .reduce("", (left, right) -> left + "|" + right);
  }

  /** Returns every point in one routed edge section. */
  private List<LayoutService.LayoutPoint> routePoints(LayoutService.EdgeSection section) {
    List<LayoutService.LayoutPoint> points = new ArrayList<>();
    points.add(section.startPoint());
    points.addAll(section.bendPoints());
    points.add(section.endPoint());
    return points;
  }

  /**
   * Asserts that no two edge segments share a rendered length.
   *
   * @param response layout response
   */
  private void assertNoOverlappingSegments(LayoutService.LayoutResponse response) {
    List<TestSegment> segments = new ArrayList<>();
    for (LayoutService.RoutedEdge edge : response.edges()) {
      for (LayoutService.EdgeSection section : edge.sections()) {
        List<LayoutService.LayoutPoint> points = new ArrayList<>();
        points.add(section.startPoint());
        points.addAll(section.bendPoints());
        points.add(section.endPoint());
        for (int index = 1; index < points.size(); index++) {
          TestSegment current =
              TestSegment.from(edge.id(), points.get(index - 1), points.get(index));
          if (current == null) {
            continue;
          }
          for (TestSegment existing : segments) {
            assertFalse(
                current.overlaps(existing), () -> edge.id() + " overlaps " + existing.edgeId());
          }
          segments.add(current);
        }
      }
    }
  }

  /** Test-only axis-aligned segment. */
  private record TestSegment(
      String edgeId, boolean vertical, double constant, double start, double end) {

    /**
     * Creates a segment from two route points.
     *
     * @param edgeId edge id
     * @param startPoint start point
     * @param endPoint end point
     * @return segment or {@code null} for diagonal/zero-length input
     */
    static TestSegment from(
        String edgeId, LayoutService.LayoutPoint startPoint, LayoutService.LayoutPoint endPoint) {
      if (Math.round(startPoint.x()) == Math.round(endPoint.x())) {
        return new TestSegment(
            edgeId,
            true,
            Math.round(startPoint.x()),
            Math.min(startPoint.y(), endPoint.y()),
            Math.max(startPoint.y(), endPoint.y()));
      }
      if (Math.round(startPoint.y()) == Math.round(endPoint.y())) {
        return new TestSegment(
            edgeId,
            false,
            Math.round(startPoint.y()),
            Math.min(startPoint.x(), endPoint.x()),
            Math.max(startPoint.x(), endPoint.x()));
      }
      return null;
    }

    /**
     * Checks whether this segment overlaps another segment.
     *
     * @param other other segment
     * @return {@code true} when they share more than an endpoint
     */
    boolean overlaps(TestSegment other) {
      if (edgeId.equals(other.edgeId()) || vertical != other.vertical()) {
        return false;
      }
      if (Math.abs(constant - other.constant()) >= 0.5d) {
        return false;
      }
      return Math.min(end, other.end()) - Math.max(start, other.start()) > 1.0d;
    }
  }
}
