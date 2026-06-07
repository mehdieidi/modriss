package io.mehdieidi.modless.platform.core.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.mehdieidi.modless.platform.core.PlatformException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Tests backend ELK layout request validation, routing, and strategy selection.
 */
class LayoutServiceTest {

    /**
     * Service under test.
     */
    private final LayoutService service = new LayoutService();

    /**
     * Verifies that a basic layered layout returns positioned nodes and routed edge sections.
     */
    @Test
    void computesLayeredLayoutWithEdgeRouting() {
        LayoutService.LayoutResponse response = service.layout(new LayoutService.LayoutRequest(
                "view-1",
                "DEFAULT_LAYERED",
                true,
                List.of(),
                Map.of(),
                List.of(
                        new LayoutService.LayoutNode(
                                "start", "Start", 180, 90, 0.0, 0.0,
                                List.of(new LayoutService.LayoutPort(
                                        "flow-out", "out", 10, 10, null, null))),
                        new LayoutService.LayoutNode(
                                "finish", "Finish", 180, 90, 0.0, 0.0,
                                List.of(new LayoutService.LayoutPort(
                                        "flow-in", "in", 10, 10, null, null)))),
                List.of(new LayoutService.LayoutEdge(
                        "edge-1", "flow", "start", "finish", "flow-out", "flow-in"))));

        assertFalse(response.nodes().isEmpty());
        assertFalse(response.edges().isEmpty());
        assertTrue(
                response.nodes().stream().allMatch(node -> node.width() > 0 && node.height() > 0));

        LayoutService.RoutedEdge edge = response.edges().get(0);
        assertNotNull(edge);
        assertFalse(edge.sections().isEmpty());
        assertNotNull(edge.sections().get(0).startPoint());
        assertNotNull(edge.sections().get(edge.sections().size() - 1).endPoint());
    }

    /**
     * Verifies that an edge targeting a missing node is rejected before ELK runs.
     */
    @Test
    void rejectsEdgesThatReferenceMissingNodes() {
        PlatformException exception = assertThrows(PlatformException.class,
                () -> service.layout(new LayoutService.LayoutRequest(
                        null,
                        "DEFAULT_LAYERED",
                        false,
                        List.of(),
                        Map.of(),
                        List.of(new LayoutService.LayoutNode(
                                "only-node", "Only", 180, 90, 0.0, 0.0, List.of())),
                        List.of(new LayoutService.LayoutEdge(
                                "edge-1", "broken", "only-node", "missing", null, null)))));

        assertTrue(exception.getMessage().contains("missing target node"));
    }

    /**
     * Verifies that every supported layout strategy can be selected without dropping nodes or
     * edges.
     */
    @Test
    void supportsSelectableLayoutStrategies() {
        List<String> strategies = List.of("SPACIOUS_LAYERED", "BALANCED_LAYERED",
                "RELAXED_SPLINES", "VERTICAL_FLOW", "TREE", "RADIAL", "FORCE");

        for (String strategy : strategies) {
            LayoutService.LayoutResponse response = layoutForStrategy(strategy);

            assertEquals(3, response.nodes().size(), strategy);
            assertEquals(2, response.edges().size(), strategy);
        }
    }

    /**
     * Verifies that non-layered strategies invoke distinct ELK algorithms instead of falling back
     * to the same layered coordinates.
     */
    @Test
    void selectableAlgorithmsProduceDistinctGeometry() {
        String balanced = geometrySignature(layoutForStrategy("BALANCED_LAYERED"));

        assertNotEquals(balanced, geometrySignature(layoutForStrategy("TREE")));
        assertNotEquals(balanced, geometrySignature(layoutForStrategy("RADIAL")));
        assertNotEquals(balanced, geometrySignature(layoutForStrategy("FORCE")));
    }

    /**
     * Computes a small layout for a strategy.
     *
     * @param strategy layout strategy
     * @return layout response
     */
    private LayoutService.LayoutResponse layoutForStrategy(String strategy) {
        return service.layout(new LayoutService.LayoutRequest(
                "view-" + strategy.toLowerCase(),
                "DEFAULT_LAYERED",
                false,
                List.of(),
                Map.of("layoutStrategy", strategy),
                List.of(
                        new LayoutService.LayoutNode(
                                "a", "A", 180, 90, 0.0, 0.0, List.of()),
                        new LayoutService.LayoutNode(
                                "b", "B", 180, 90, 0.0, 0.0, List.of()),
                        new LayoutService.LayoutNode(
                                "c", "C", 180, 90, 0.0, 0.0, List.of())),
                List.of(
                        new LayoutService.LayoutEdge(
                                "edge-ab", "ab", "a", "b", null, null),
                        new LayoutService.LayoutEdge(
                                "edge-ac", "ac", "a", "c", null, null))));
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
}
