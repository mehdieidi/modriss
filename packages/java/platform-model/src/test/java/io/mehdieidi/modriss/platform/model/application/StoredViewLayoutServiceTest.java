package io.mehdieidi.modriss.platform.model.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.mehdieidi.modriss.platform.kernel.ModelLevel;
import io.mehdieidi.modriss.platform.model.domain.ModelRecord;
import io.mehdieidi.modriss.platform.modeling.layout.LayoutService;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/** Tests stored-view auto layout persistence and routed edge pin generation. */
class StoredViewLayoutServiceTest {

  /** Temporary repository root for file-backed service tests. */
  @TempDir Path tempDir;

  /**
   * Verifies that stored-view layout is applied once, persisted, and skipped on the next call
   * unless forced.
   *
   * @throws Exception when temporary repository setup fails
   */
  @Test
  void lazilyLayoutsAndPersistsAStoredViewOnlyOnce() throws Exception {
    PlatformTestFixtures.ServiceStack services = PlatformTestFixtures.createServices(tempDir);
    StoredViewLayoutService service =
        new StoredViewLayoutService(services.models(), new LayoutService());
    PlatformTestFixtures.AuthenticatedContext context =
        PlatformTestFixtures.registerOwner(
            services, "layout-owner@example.com", "Layout Owner", "Layout Project");

    ObjectNode model =
        (ObjectNode)
            services
                .models()
                .importModel(ModelLevel.CIM, "cim.xmi", PlatformTestFixtures.climateCimXmi(), "xmi")
                .modelJson()
                .deepCopy();
    addView(model);
    ModelRecord created =
        services
            .models()
            .create(context.user(), ModelLevel.CIM, context.project().id(), "layout-cim", model);

    StoredViewLayoutService.StoredViewLayoutResponse first =
        service.layout(
            context.user(), ModelLevel.CIM, created.id(), "view-test", false, "SPACIOUS_LAYERED");
    assertTrue(first.layoutApplied());
    assertTrue(first.view().path("autoLayoutApplied").asBoolean());
    assertEquals(2, first.view().path("layoutGeometryVersion").asInt());
    assertTrue(first.view().path("nodes").findValuesAsString("x").size() >= 2);
    JsonNode edge = first.view().path("edges").path(0);
    assertEquals("right", edge.path("sourceAnchor").path("side").asText());
    assertEquals("left", edge.path("targetAnchor").path("side").asText());
    assertTrue(edge.path("sourceAnchor").path("offsetY").isNumber());
    assertTrue(edge.path("targetAnchor").path("offsetY").isNumber());
    assertTrue(edge.path("pinPoints").size() >= 2);
    assertOrthogonalPins(edge);
    assertEquals(created.revision() + 1, first.revision());

    StoredViewLayoutService.StoredViewLayoutResponse second =
        service.layout(
            context.user(), ModelLevel.CIM, created.id(), "view-test", false, "SPACIOUS_LAYERED");
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
    PlatformTestFixtures.ServiceStack services = PlatformTestFixtures.createServices(tempDir);
    StoredViewLayoutService service =
        new StoredViewLayoutService(services.models(), new LayoutService());
    PlatformTestFixtures.AuthenticatedContext context =
        PlatformTestFixtures.registerOwner(
            services,
            "dashboard-layout-owner@example.com",
            "Dashboard Layout Owner",
            "Dashboard Layout Project");

    ObjectNode model =
        (ObjectNode)
            services
                .models()
                .importModel(ModelLevel.CIM, "cim.xmi", PlatformTestFixtures.climateCimXmi(), "xmi")
                .modelJson()
                .deepCopy();
    addDashboardView(model);
    ModelRecord created =
        services
            .models()
            .create(
                context.user(),
                ModelLevel.CIM,
                context.project().id(),
                "dashboard-layout-cim",
                model);

    StoredViewLayoutService.StoredViewLayoutResponse spacious =
        service.layout(
            context.user(),
            ModelLevel.CIM,
            created.id(),
            "dashboard-view",
            true,
            "SPACIOUS_LAYERED");
    StoredViewLayoutService.StoredViewLayoutResponse tree =
        service.layout(
            context.user(), ModelLevel.CIM, created.id(), "dashboard-view", true, "TREE");
    StoredViewLayoutService.StoredViewLayoutResponse radial =
        service.layout(
            context.user(), ModelLevel.CIM, created.id(), "dashboard-view", true, "RADIAL");

    assertEquals("SPACIOUS_LAYERED", spacious.view().path("layoutStrategy").asText());
    assertEquals("TREE", tree.view().path("layoutStrategy").asText());
    assertEquals("RADIAL", radial.view().path("layoutStrategy").asText());
    assertOrthogonalPins(spacious.view().path("edges").path(0));
    assertNotEquals(nodeGeometrySignature(spacious.view()), nodeGeometrySignature(tree.view()));
    assertNotEquals(nodeGeometrySignature(spacious.view()), nodeGeometrySignature(radial.view()));
  }

  /**
   * Verifies that client-side visual-only view edges are ignored during backend layout.
   *
   * @throws Exception when temporary repository setup fails
   */
  @Test
  void ignoresVisualOnlyViewEdgesMissingFromPersistedGraph() throws Exception {
    PlatformTestFixtures.ServiceStack services = PlatformTestFixtures.createServices(tempDir);
    StoredViewLayoutService service =
        new StoredViewLayoutService(services.models(), new LayoutService());
    PlatformTestFixtures.AuthenticatedContext context =
        PlatformTestFixtures.registerOwner(
            services, "layout-visual@example.com", "Layout Visual", "Layout Visual Project");

    ObjectNode model =
        (ObjectNode)
            services
                .models()
                .importModel(ModelLevel.CIM, "cim.xmi", PlatformTestFixtures.climateCimXmi(), "xmi")
                .modelJson()
                .deepCopy();
    addView(model);
    ObjectNode view = (ObjectNode) model.path("views").path(0);
    ObjectNode visualEdge = view.putArray("edges").addObject();
    visualEdge.put("relationshipId", "containment-parent-tags-child");
    visualEdge.put("visible", true);
    ModelRecord created =
        services
            .models()
            .create(
                context.user(), ModelLevel.CIM, context.project().id(), "layout-visual-cim", model);

    StoredViewLayoutService.StoredViewLayoutResponse response =
        service.layout(
            context.user(), ModelLevel.CIM, created.id(), "view-test", false, "SPACIOUS_LAYERED");
    assertTrue(response.layoutApplied());
    assertTrue(response.view().path("autoLayoutApplied").asBoolean());
  }

  /**
   * Adds a representative view with one visible relationship edge to a model.
   *
   * @param model model JSON to mutate
   */
  private void addView(ObjectNode model) {
    JsonNode relationship =
        model.path("graph").path("relationships").findParents("sourceElementId").stream()
            .filter(
                candidate -> candidate.hasNonNull("targetElementId") && candidate.hasNonNull("id"))
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
      if (relationshipId.isBlank()
          || !selectedIds.contains(sourceId)
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
   * @param nodes view node array
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
    assertTrue(
        first.path("x").asInt() == second.path("x").asInt()
            || first.path("y").asInt() == second.path("y").asInt());
  }

  /**
   * Asserts that stored view anchors and pin points do not produce overlapping edge segments.
   *
   * @param model model JSON with graph relationships
   * @param view laid-out view JSON
   */
  private void assertStoredViewEdgesDoNotOverlap(ObjectNode model, JsonNode view) {
    Map<String, JsonNode> relationships = new HashMap<>();
    model
        .path("graph")
        .path("relationships")
        .forEach(relationship -> relationships.put(relationship.path("id").asText(), relationship));
    Map<String, JsonNode> nodes = new HashMap<>();
    view.path("nodes")
        .forEach(node -> nodes.put(node.path("elementId").asText(node.path("id").asText()), node));

    List<TestSegment> segments = new ArrayList<>();
    for (JsonNode edge : view.path("edges")) {
      String edgeId = edge.path("relationshipId").asText(edge.path("id").asText());
      JsonNode relationship = relationships.get(edgeId);
      if (relationship == null) {
        continue;
      }
      JsonNode source = nodes.get(relationship.path("sourceElementId").asText());
      JsonNode target = nodes.get(relationship.path("targetElementId").asText());
      if (source == null || target == null) {
        continue;
      }
      List<TestPoint> points = new ArrayList<>();
      points.add(anchorPoint(source, edge.path("sourceAnchor")));
      edge.path("pinPoints")
          .forEach(
              pin -> points.add(new TestPoint(pin.path("x").asDouble(), pin.path("y").asDouble())));
      points.add(anchorPoint(target, edge.path("targetAnchor")));
      for (int index = 1; index < points.size(); index++) {
        TestSegment current = TestSegment.from(edgeId, points.get(index - 1), points.get(index));
        if (current == null) {
          continue;
        }
        for (TestSegment existing : segments) {
          assertFalse(
              current.overlaps(existing),
              () -> edgeId + " " + current + " overlaps " + existing.edgeId() + " " + existing);
        }
        segments.add(current);
      }
    }
  }

  /**
   * Resolves a stored left/right anchor to a test point.
   *
   * @param node view node
   * @param anchor stored anchor
   * @return absolute anchor point
   */
  private TestPoint anchorPoint(JsonNode node, JsonNode anchor) {
    double x = node.path("x").asDouble();
    double y = node.path("y").asDouble();
    double width = node.path("width").asDouble(176.0d);
    double height = node.path("height").asDouble(96.0d);
    String side = anchor.path("side").asText("right");
    double offsetY =
        Math.max(8.0d, Math.min(height - 8.0d, anchor.path("offsetY").asDouble(height / 2.0d)));
    return new TestPoint("right".equals(side) ? x + width : x, y + offsetY);
  }

  /**
   * Builds a deterministic signature from stored view node coordinates.
   *
   * @param view stored view JSON
   * @return compact geometry signature
   */
  private String nodeGeometrySignature(JsonNode view) {
    StringBuilder signature = new StringBuilder();
    view.path("nodes")
        .forEach(
            node ->
                signature
                    .append('|')
                    .append(node.path("elementId").asText(node.path("id").asText()))
                    .append('=')
                    .append(node.path("x").asInt())
                    .append(',')
                    .append(node.path("y").asInt()));
    return signature.toString();
  }

  /** Test-only route point. */
  private record TestPoint(double x, double y) {}

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
    static TestSegment from(String edgeId, TestPoint startPoint, TestPoint endPoint) {
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
