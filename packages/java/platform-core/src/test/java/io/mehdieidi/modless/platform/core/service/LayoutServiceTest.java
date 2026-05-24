package io.mehdieidi.modless.platform.core.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.mehdieidi.modless.platform.core.PlatformException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class LayoutServiceTest {

    private final LayoutService service = new LayoutService();

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
}
