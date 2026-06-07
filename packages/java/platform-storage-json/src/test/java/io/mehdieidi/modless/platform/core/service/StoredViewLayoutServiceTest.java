package io.mehdieidi.modless.platform.core.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.mehdieidi.modless.platform.core.model.ModelLevel;
import io.mehdieidi.modless.platform.core.model.ModelRecord;
import io.mehdieidi.modless.platform.core.model.ProjectRecord;
import io.mehdieidi.modless.platform.core.model.UserRecord;
import io.mehdieidi.modless.platform.core.repository.JsonFileStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Tests stored-view auto layout persistence and routed edge pin generation.
 */
class StoredViewLayoutServiceTest {

    /**
     * Temporary repository root for file-backed service tests.
     */
    @TempDir
    Path tempDir;

    /**
     * Verifies that stored-view layout is applied once, persisted, and skipped on the next call
     * unless forced.
     *
     * @throws Exception when temporary repository setup fails
     */
    @Test
    void lazilyLayoutsAndPersistsAStoredViewOnlyOnce() throws Exception {
        JsonFileStore store = new JsonFileStore(tempDir);
        store.initialize();
        AuthService auth = new AuthService(store, Duration.ofHours(1));
        ProjectService projects = new ProjectService(store, auth);
        ModelService models = new ModelService(store, projects);
        StoredViewLayoutService service = new StoredViewLayoutService(models,
                new LayoutService());
        UserRecord user = auth.register("layout-owner@example.com", "password123",
                "Layout Owner").user();
        ProjectRecord project = projects.create(user, "Layout Project", "");

        byte[] xmi = Files.readAllBytes(Path.of("..", "..", "..", "mde", "samples",
                "cim.xmi").normalize());
        ObjectNode model = (ObjectNode) models.importModel(ModelLevel.CIM, "cim.xmi", xmi,
                "xmi").modelJson().deepCopy();
        addView(model);
        ModelRecord created = models.create(user, ModelLevel.CIM, project.id(), "layout-cim",
                model);

        StoredViewLayoutService.StoredViewLayoutResponse first = service.layout(user,
                ModelLevel.CIM, created.id(), "view-test", false, "SPACIOUS_LAYERED");
        assertTrue(first.layoutApplied());
        assertTrue(first.view().path("autoLayoutApplied").asBoolean());
        assertTrue(first.view().path("nodes").findValuesAsText("x").size() >= 2);
        JsonNode edge = first.view().path("edges").path(0);
        assertEquals("right", edge.path("sourceAnchor").path("side").asText());
        assertEquals("left", edge.path("targetAnchor").path("side").asText());
        assertTrue(edge.path("sourceAnchor").path("offsetY").isNumber());
        assertTrue(edge.path("targetAnchor").path("offsetY").isNumber());
        assertEquals(2, edge.path("pinPoints").size());
        assertOrthogonalPins(edge);
        assertEquals(created.revision() + 1, first.revision());

        StoredViewLayoutService.StoredViewLayoutResponse second = service.layout(user,
                ModelLevel.CIM, created.id(), "view-test", false, "SPACIOUS_LAYERED");
        assertFalse(second.layoutApplied());
        assertEquals(first.revision(), second.revision());
        assertEquals(first.view().toString(), second.view().toString());
    }

    /**
     * Verifies that large dashboard views do not force every selected algorithm through the same
     * semantic grid post-processing.
     *
     * @throws Exception when temporary repository setup fails
     */
    @Test
    void dashboardViewKeepsSelectedAlgorithmGeometry() throws Exception {
        JsonFileStore store = new JsonFileStore(tempDir);
        store.initialize();
        AuthService auth = new AuthService(store, Duration.ofHours(1));
        ProjectService projects = new ProjectService(store, auth);
        ModelService models = new ModelService(store, projects);
        StoredViewLayoutService service = new StoredViewLayoutService(models,
                new LayoutService());
        UserRecord user = auth.register("dashboard-layout-owner@example.com", "password123",
                "Dashboard Layout Owner").user();
        ProjectRecord project = projects.create(user, "Dashboard Layout Project", "");

        byte[] xmi = Files.readAllBytes(Path.of("..", "..", "..", "mde", "samples",
                "cim.xmi").normalize());
        ObjectNode model = (ObjectNode) models.importModel(ModelLevel.CIM, "cim.xmi", xmi,
                "xmi").modelJson().deepCopy();
        addDashboardView(model);
        ModelRecord created = models.create(user, ModelLevel.CIM, project.id(),
                "dashboard-layout-cim", model);

        StoredViewLayoutService.StoredViewLayoutResponse spacious = service.layout(user,
                ModelLevel.CIM, created.id(), "dashboard-view", true, "SPACIOUS_LAYERED");
        StoredViewLayoutService.StoredViewLayoutResponse tree = service.layout(user,
                ModelLevel.CIM, created.id(), "dashboard-view", true, "TREE");
        StoredViewLayoutService.StoredViewLayoutResponse radial = service.layout(user,
                ModelLevel.CIM, created.id(), "dashboard-view", true, "RADIAL");

        assertEquals("SPACIOUS_LAYERED", spacious.view().path("layoutStrategy").asText());
        assertEquals("TREE", tree.view().path("layoutStrategy").asText());
        assertEquals("RADIAL", radial.view().path("layoutStrategy").asText());
        assertNotEquals(nodeGeometrySignature(spacious.view()), nodeGeometrySignature(tree.view()));
        assertNotEquals(nodeGeometrySignature(spacious.view()),
                nodeGeometrySignature(radial.view()));
    }

    /**
     * Adds a representative view with one visible relationship edge to a model.
     *
     * @param model model JSON to mutate
     */
    private void addView(ObjectNode model) {
        JsonNode relationship = model.path("graph").path("relationships").findParents(
                        "sourceElementId").stream()
                .filter(candidate -> candidate.hasNonNull("targetElementId")
                        && candidate.hasNonNull("id"))
                .findFirst()
                .orElseThrow();
        String sourceId = relationship.path("sourceElementId").asText();
        String targetId = relationship.path("targetElementId").asText();
        String relationshipId = relationship.path("id").asText();
        ArrayNode views = model.putArray("views");
        ObjectNode view = views.addObject();
        view.put("id", "view-test");
        view.put("name", "Test View");
        view.put("layoutProfile", "DEFAULT_LAYERED");
        ArrayNode nodes = view.putArray("nodes");
        addNode(nodes, sourceId);
        addNode(nodes, targetId);
        ObjectNode edge = view.putArray("edges").addObject();
        edge.put("relationshipId", relationshipId);
        edge.put("visible", true);
        ObjectNode hidden = view.putObject("hidden");
        hidden.putArray("elementIds");
        hidden.putArray("relationshipIds");
    }

    /**
     * Adds a dashboard-like view with enough nodes to trigger the old semantic-grid path.
     *
     * @param model model JSON to mutate
     */
    private void addDashboardView(ObjectNode model) {
        ArrayNode views = model.putArray("views");
        ObjectNode view = views.addObject();
        view.put("id", "dashboard-view");
        view.put("name", "Dashboard View");
        view.put("kind", "dashboard");
        view.put("layoutProfile", "DASHBOARD");
        ArrayNode nodes = view.putArray("nodes");
        Set<String> selectedIds = new HashSet<>();
        int added = 0;
        for (JsonNode element : model.path("graph").path("elements")) {
            String id = element.path("id").asText("");
            if (id.isBlank()) {
                continue;
            }
            addNode(nodes, id);
            selectedIds.add(id);
            added += 1;
            if (added >= 40) {
                break;
            }
        }
        assertTrue(added >= 36, "Sample model should contain enough dashboard nodes.");
        ArrayNode edges = view.putArray("edges");
        int edgeCount = 0;
        for (JsonNode relationship : model.path("graph").path("relationships")) {
            String sourceId = relationship.path("sourceElementId").asText("");
            String targetId = relationship.path("targetElementId").asText("");
            String relationshipId = relationship.path("id").asText("");
            if (relationshipId.isBlank() || !selectedIds.contains(sourceId)
                    || !selectedIds.contains(targetId)) {
                continue;
            }
            ObjectNode edge = edges.addObject();
            edge.put("relationshipId", relationshipId);
            edge.put("visible", true);
            edgeCount += 1;
        }
        assertTrue(edgeCount > 0, "Sample dashboard view should include visible edges.");
        ObjectNode hidden = view.putObject("hidden");
        hidden.putArray("elementIds");
        hidden.putArray("relationshipIds");
    }

    /**
     * Adds a view node for a model element id.
     *
     * @param nodes     view node array
     * @param elementId element identifier
     */
    private void addNode(ArrayNode nodes, String elementId) {
        ObjectNode node = nodes.addObject();
        node.put("elementId", elementId);
        node.put("x", 0);
        node.put("y", 0);
    }

    /**
     * Asserts that the stored edge has a vertical orthogonal pin segment.
     *
     * @param edge stored edge JSON
     */
    private void assertOrthogonalPins(JsonNode edge) {
        JsonNode pins = edge.path("pinPoints");
        JsonNode first = pins.path(0);
        JsonNode second = pins.path(1);
        assertNotNull(first);
        assertNotNull(second);
        assertEquals(first.path("x").asInt(), second.path("x").asInt());
    }

    /**
     * Builds a deterministic signature from stored view node coordinates.
     *
     * @param view stored view JSON
     * @return compact geometry signature
     */
    private String nodeGeometrySignature(JsonNode view) {
        StringBuilder signature = new StringBuilder();
        view.path("nodes").forEach(node -> signature.append('|')
                .append(node.path("elementId").asText(node.path("id").asText()))
                .append('=')
                .append(node.path("x").asInt())
                .append(',')
                .append(node.path("y").asInt()));
        return signature.toString();
    }
}
