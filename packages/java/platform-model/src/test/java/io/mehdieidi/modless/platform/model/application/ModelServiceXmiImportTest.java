package io.mehdieidi.modless.platform.model.application;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.mehdieidi.modless.platform.identity.application.AuthService;
import io.mehdieidi.modless.platform.identity.domain.UserRecord;
import io.mehdieidi.modless.platform.kernel.ModelLevel;
import io.mehdieidi.modless.platform.kernel.PlatformException;
import io.mehdieidi.modless.platform.model.domain.ModelRecord;
import io.mehdieidi.modless.platform.project.application.ProjectService;
import io.mehdieidi.modless.platform.project.domain.ProjectRecord;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.ResourceLock;

/**
 * Regression tests for model import, export, validation, patching, and source-XMI preservation
 * across CIM, PIM, and PSM levels.
 */
@ResourceLock("epsilon-runtime")
class ModelServiceXmiImportTest {

  /** Isolated JSON-file store directory for each test case. */
  @TempDir Path tempDir;

  /** Verifies CIM XMI import into semantic JSON plus graph relationships. */
  @Test
  void importsCimXmiIntoSemanticAndGraphJson() {
    TestPlatformStore store = new TestPlatformStore(tempDir);
    store.initialize();
    AuthService authService = new AuthService(store, Duration.ofHours(1));
    ProjectService projectService = new ProjectService(store, authService);
    ModelService service = new ModelService(store, projectService);

    ModelService.ImportResult result =
        service.importModel(
            ModelLevel.CIM, "sample.xmi", sampleCimXmi().getBytes(StandardCharsets.UTF_8), "xmi");

    assertEquals("sample", result.name());
    assertEquals("Test Grants", result.modelJson().path("domainName").asText());
    assertEquals("BusinessGoal", result.modelJson().path("goals").path(0).path("eClass").asText());
    assertEquals(
        "goal-1",
        result.modelJson().path("capabilities").path(0).path("supports").path(0).asText());
    assertEquals("CIM", result.modelJson().path("modelLevel").asText());
    assertFalse(result.modelJson().path("_sourceXmiBase64").asText().isBlank());
    assertTrue(result.modelJson().path("_sourceXmiToken").isMissingNode());
    assertTrue(result.modelJson().path("diagram").isMissingNode());

    assertFalse(result.modelJson().path("graph").path("elements").isEmpty());
    assertTrue(
        result.modelJson().path("graph").path("elements").findValuesAsText("id").contains("cap-1"));
    assertFalse(result.modelJson().path("graph").path("relationships").isEmpty());
    assertTrue(
        result
            .modelJson()
            .path("graph")
            .path("relationships")
            .findValuesAsText("semanticFeature")
            .contains("supports"));
    JsonNode supports =
        relationship(
            result.modelJson().path("graph").path("relationships"), "cap-1", "goal-1", "SUPPORTS");
    assertNotNull(supports);
    JsonNode rootContainsCapability =
        relationship(
            result.modelJson().path("graph").path("relationships"),
            "cim-root",
            "cap-1",
            "CONTAINS");
    assertNotNull(rootContainsCapability);
    assertTrue(rootContainsCapability.path("containment").asBoolean());
    assertNotNull(result.issues());
  }

  /** Ensures required enum values equal to metamodel defaults survive import. */
  @Test
  void importsRequiredCimEnumValuesThatMatchMetamodelDefaults() {
    TestPlatformStore store = new TestPlatformStore(tempDir);
    store.initialize();
    AuthService authService = new AuthService(store, Duration.ofHours(1));
    ProjectService projectService = new ProjectService(store, authService);
    ModelService service = new ModelService(store, projectService);

    ModelService.ImportResult result =
        service.importModel(
            ModelLevel.CIM,
            "required-enums.xmi",
            sampleCimRequiredEnumXmi().getBytes(StandardCharsets.UTF_8),
            "xmi");

    JsonNode item = result.modelJson().path("informationItems").path(0);
    JsonNode classification = result.modelJson().path("classifications").path(0);

    assertEquals("TEXT", item.path("type").asText());
    assertEquals("PUBLIC", classification.path("kind").asText());
    assertTrue(
        result.issues().stream()
            .noneMatch(
                issue ->
                    "RequiredAttribute".equals(issue.constraint())
                        && ("type".equals(missingFeature(issue))
                            || "kind".equals(missingFeature(issue)))));
  }

  /**
   * Confirms validation can reconstruct semantic relationship endpoints from frontend graph
   * endpoint IDs.
   */
  @Test
  void validatesRelationshipsPersistedWithFrontendEndpointIds() {
    TestPlatformStore store = new TestPlatformStore(tempDir);
    store.initialize();
    AuthService authService = new AuthService(store, Duration.ofHours(1));
    ProjectService projectService = new ProjectService(store, authService);
    ModelService service = new ModelService(store, projectService);

    ObjectNode model =
        (ObjectNode)
            service
                .importModel(
                    ModelLevel.CIM,
                    "relationships.xmi",
                    sampleCimRelationshipXmi().getBytes(StandardCharsets.UTF_8),
                    "xmi")
                .modelJson();
    ObjectNode semanticRelationship = (ObjectNode) model.path("relationships").path(0);
    semanticRelationship.remove("source");
    semanticRelationship.remove("target");
    ObjectNode graphRelationship =
        ((ObjectNode) model.path("graph")).withArray("relationships").addObject();
    graphRelationship.put("id", "rel-source-target");
    graphRelationship.put("kind", "DOMAIN_RELATIONSHIP");
    graphRelationship.put("sourceElementId", "vo-source");
    graphRelationship.put("targetElementId", "vo-target");

    assertEquals("vo-source", graphRelationship.path("sourceElementId").asText());
    assertEquals("vo-target", graphRelationship.path("targetElementId").asText());

    ModelService.ValidationResult validation = service.validate(ModelLevel.CIM, model);

    assertTrue(
        validation.issues().stream()
            .noneMatch(
                issue ->
                    "EVL_MODEL_LOADING".equals(issue.constraint())
                        && (issue.message().contains("required feature 'source'")
                            || issue.message().contains("required feature 'target'"))));
  }

  /**
   * Ensures validate-by-id repairs stale source XMI when the stored JSON has only frontend
   * relationship endpoint IDs.
   */
  @Test
  void validateByIdRepairsStaleSourceXmiFromFrontendEndpointIds() {
    TestPlatformStore store = new TestPlatformStore(tempDir);
    store.initialize();
    AuthService authService = new AuthService(store, Duration.ofHours(1));
    ProjectService projectService = new ProjectService(store, authService);
    ModelService service = new ModelService(store, projectService);
    UserRecord user =
        authService.register("stale-xmi-owner@example.com", "password123", "Owner").user();
    ProjectRecord project = projectService.create(user, "Stale XMI Project", "");

    ObjectNode model = frontendEndpointOnlyRelationshipModel(service);
    ModelRecord created =
        service.create(user, ModelLevel.CIM, project.id(), "relationships", model);
    service.attachSourceXmi(
        created, sampleCimRelationshipXmiWithoutEndpoints().getBytes(StandardCharsets.UTF_8));

    ModelService.ValidationResult validation = service.validate(user, ModelLevel.CIM, created.id());

    assertNoRelationshipEndpointLoadingError(validation);
    ModelService.ValidationResult repairedSourceValidation =
        service.validateGeneratedXmi(
            ModelLevel.CIM,
            service.sourceXmi(service.get(user, ModelLevel.CIM, created.id())).orElseThrow());
    assertNoRelationshipEndpointLoadingError(repairedSourceValidation);
  }

  /** Verifies strict export diagnostics for enum literals outside the metamodel. */
  @Test
  void rejectsInvalidEnumValuesDuringXmiExport() {
    TestPlatformStore store = new TestPlatformStore(tempDir);
    store.initialize();
    AuthService authService = new AuthService(store, Duration.ofHours(1));
    ProjectService projectService = new ProjectService(store, authService);
    ModelService service = new ModelService(store, projectService);
    ObjectNode model = minimalCimModel(store);
    ObjectNode item = model.putArray("informationItems").addObject();
    item.put("eClass", "InformationItem");
    item.put("id", "info-1");
    item.put("name", "Postal code");
    item.put("businessName", "Postal code");
    item.put("type", "NOT_A_PRIMITIVE_TYPE");

    PlatformException exception =
        assertThrows(
            PlatformException.class, () -> service.exportModel(ModelLevel.CIM, model, "xmi"));

    assertTrue(exception.getMessage().contains("Unknown enum literal"));
  }

  /** Verifies that blank enum values from older frontend defaults are treated as unset. */
  @Test
  void ignoresBlankEnumValuesDuringXmiExport() {
    TestPlatformStore store = new TestPlatformStore(tempDir);
    store.initialize();
    AuthService authService = new AuthService(store, Duration.ofHours(1));
    ProjectService projectService = new ProjectService(store, authService);
    ModelService service = new ModelService(store, projectService);
    ObjectNode model = minimalCimModel(store);
    ObjectNode goal = model.putArray("goals").addObject();
    goal.put("eClass", "BusinessGoal");
    goal.put("id", "goal-1");
    goal.put("name", "Improve customer retention");
    goal.put("priority", "");

    byte[] exported = assertDoesNotThrow(() -> service.exportModel(ModelLevel.CIM, model, "xmi"));

    assertTrue(exported.length > 0);
  }

  /** Verifies strict export diagnostics for unresolved model references. */
  @Test
  void rejectsUnresolvedReferencesDuringXmiExport() {
    TestPlatformStore store = new TestPlatformStore(tempDir);
    store.initialize();
    AuthService authService = new AuthService(store, Duration.ofHours(1));
    ProjectService projectService = new ProjectService(store, authService);
    ModelService service = new ModelService(store, projectService);
    ObjectNode model = minimalCimModel(store);
    ObjectNode capability = model.putArray("capabilities").addObject();
    capability.put("eClass", "BusinessCapability");
    capability.put("id", "cap-1");
    capability.put("name", "Review applications");
    capability.putArray("supports").add("missing-goal");

    PlatformException exception =
        assertThrows(
            PlatformException.class, () -> service.exportModel(ModelLevel.CIM, model, "xmi"));

    assertTrue(exception.getMessage().contains("Unresolved reference id 'missing-goal'"));
  }

  /** Verifies strict export diagnostics for containment type mismatches. */
  @Test
  void rejectsWrongContainedChildTypeDuringXmiExport() {
    TestPlatformStore store = new TestPlatformStore(tempDir);
    store.initialize();
    AuthService authService = new AuthService(store, Duration.ofHours(1));
    ProjectService projectService = new ProjectService(store, authService);
    ModelService service = new ModelService(store, projectService);
    ObjectNode model = minimalCimModel(store);
    ObjectNode actor = model.putArray("goals").addObject();
    actor.put("eClass", "Actor");
    actor.put("id", "actor-1");
    actor.put("name", "Resident");

    PlatformException exception =
        assertThrows(
            PlatformException.class, () -> service.exportModel(ModelLevel.CIM, model, "xmi"));

    assertTrue(exception.getMessage().contains("is not valid for containment BusinessGoal"));
  }

  /** Ensures XMI documents with multiple model roots are rejected. */
  @Test
  void rejectsMultiRootXmiImports() {
    TestPlatformStore store = new TestPlatformStore(tempDir);
    store.initialize();
    AuthService authService = new AuthService(store, Duration.ofHours(1));
    ProjectService projectService = new ProjectService(store, authService);
    ModelService service = new ModelService(store, projectService);

    PlatformException exception =
        assertThrows(
            PlatformException.class,
            () ->
                service.importModel(
                    ModelLevel.CIM,
                    "multi-root.xmi",
                    multiRootCimXmi().getBytes(StandardCharsets.UTF_8),
                    "xmi"));

    assertTrue(exception.getMessage().contains("exactly one model root"));
  }

  /** Confirms transport-only source-XMI fields are never persisted in model JSON. */
  @Test
  void stripsTransportOnlyFieldsWhenSavingModel() {
    TestPlatformStore store = new TestPlatformStore(tempDir);
    store.initialize();
    AuthService authService = new AuthService(store, Duration.ofHours(1));
    ProjectService projectService = new ProjectService(store, authService);
    ModelService service = new ModelService(store, projectService);
    UserRecord user = authService.register("owner@example.com", "password123", "Owner").user();
    ProjectRecord project = projectService.create(user, "Climate", "");

    ObjectNode model = store.objectMapper().createObjectNode();
    model.put("eClass", "CIMModel");
    model.put("id", "cim-root");
    model.put("name", "Climate");
    model.put("_sourceXmiBase64", "PGNpbS8+");
    ModelRecord created = service.create(user, ModelLevel.CIM, project.id(), "climate", model);

    ObjectNode update = store.objectMapper().createObjectNode();
    update.put("eClass", "CIMModel");
    update.put("id", "cim-root");
    update.put("name", "Climate edited");
    ModelRecord updated =
        service.update(user, ModelLevel.CIM, created.id(), "climate-edited", update);

    assertTrue(created.modelJson().path("_sourceXmiBase64").isMissingNode());
    assertTrue(updated.modelJson().path("_sourceXmiBase64").isMissingNode());
    var summaries = service.listSummaries(user, ModelLevel.CIM, project.id());
    assertEquals(1, summaries.size());
    assertEquals(created.id(), summaries.get(0).id());
    assertEquals("climate-edited", summaries.get(0).name());
  }

  /**
   * Ensures JSON Patch updates the stored model incrementally while preserving platform-added model
   * metadata.
   *
   * @throws Exception when patch payload parsing fails
   */
  @Test
  void patchesStoredModelWithoutReplacingWholeJson() throws Exception {
    TestPlatformStore store = new TestPlatformStore(tempDir);
    store.initialize();
    AuthService authService = new AuthService(store, Duration.ofHours(1));
    ProjectService projectService = new ProjectService(store, authService);
    ModelService service = new ModelService(store, projectService);
    UserRecord user =
        authService.register("patch-owner@example.com", "password123", "Owner").user();
    ProjectRecord project = projectService.create(user, "Patch Project", "");

    ObjectNode model = store.objectMapper().createObjectNode();
    model.put("eClass", "CIMModel");
    model.put("id", "cim-root");
    model.put("name", "Before");
    ObjectNode diagram = model.putObject("diagram");
    var elements = diagram.putArray("elements");
    ObjectNode element = elements.addObject();
    element.put("eClass", "BusinessGoal");
    element.put("id", "goal-1");
    element.put("name", "Before goal");
    ModelRecord created = service.create(user, ModelLevel.CIM, project.id(), "patch", model);

    service.patch(
        user,
        ModelLevel.CIM,
        created.id(),
        "patch",
        java.util.List.of(
            new ModelService.ModelPatchOperation(
                "replace",
                "/diagram/elements/0/name",
                store.objectMapper().getNodeFactory().textNode("After goal")),
            new ModelService.ModelPatchOperation(
                "add",
                "/diagram/elements/-",
                store
                    .objectMapper()
                    .readTree(
                        """
                        {"eClass":"Actor","id":"actor-1","name":"Actor"}
                        """))));

    ModelRecord updated = service.get(user, ModelLevel.CIM, created.id());
    assertEquals(
        "After goal",
        updated.modelJson().path("diagram").path("elements").path(0).path("name").asText());
    assertEquals(
        "Actor",
        updated.modelJson().path("diagram").path("elements").path(1).path("name").asText());
    assertEquals("CIM", updated.modelJson().path("modelLevel").asText());
  }

  /** Verifies PSM relationship views are projected as canonical graph edges. */
  @Test
  void importsPsmRelationshipViewsAsFilterableEdges() {
    TestPlatformStore store = new TestPlatformStore(tempDir);
    store.initialize();
    AuthService authService = new AuthService(store, Duration.ofHours(1));
    ProjectService projectService = new ProjectService(store, authService);
    ModelService service = new ModelService(store, projectService);

    ModelService.ImportResult result =
        service.importModel(
            ModelLevel.PSM, "psm.xmi", samplePsmXmi().getBytes(StandardCharsets.UTF_8), "xmi");

    JsonNode relationships = result.modelJson().path("graph").path("relationships");
    assertFalse(relationships.isEmpty());
    JsonNode routeToLambda = null;
    JsonNode stackContainsRoute = null;
    for (JsonNode relationship : relationships) {
      if ("view_api_lambda_submit".equals(relationship.path("id").asText())) {
        routeToLambda = relationship;
      }
      if ("stack-main".equals(relationship.path("sourceElementId").asText())
          && "route-submit".equals(relationship.path("targetElementId").asText())
          && "CONTAINS".equals(relationship.path("kind").asText())) {
        stackContainsRoute = relationship;
      }
    }
    assertNotNull(routeToLambda);
    assertEquals("INVOKES", routeToLambda.path("kind").asText());
    assertEquals("route-submit", routeToLambda.path("sourceElementId").asText());
    assertEquals("lambda-submit", routeToLambda.path("targetElementId").asText());
    assertNotNull(stackContainsRoute);
    assertTrue(stackContainsRoute.path("containment").asBoolean());
  }

  /** Verifies PIM reference relationships are projected with PIM-specific edge kinds. */
  @Test
  void importsPimReferenceEdgesWithPimSpecificKinds() {
    TestPlatformStore store = new TestPlatformStore(tempDir);
    store.initialize();
    AuthService authService = new AuthService(store, Duration.ofHours(1));
    ProjectService projectService = new ProjectService(store, authService);
    ModelService service = new ModelService(store, projectService);

    ModelService.ImportResult result =
        service.importModel(
            ModelLevel.PIM, "pim.xmi", samplePimXmi().getBytes(StandardCharsets.UTF_8), "xmi");

    JsonNode relationships = result.modelJson().path("graph").path("relationships");
    assertFalse(relationships.isEmpty());
    JsonNode routeToFunction =
        relationship(relationships, "route-submit", "fn-submit", "ROUTES_TO");
    JsonNode functionReadsStore = relationship(relationships, "fn-submit", "store-app", "READS");
    JsonNode workflowTransition = relationship(relationships, "wf-start", "wf-task", "TRANSITION");
    JsonNode principalPermission =
        relationship(relationships, "principal-resident", "fn-submit", "PERMISSION");
    JsonNode rootContainsService = relationship(relationships, "pim-root", "svc-main", "CONTAINS");
    JsonNode serviceContainsApi = relationship(relationships, "svc-main", "api-main", "CONTAINS");
    assertNotNull(routeToFunction, relationships::toPrettyString);
    assertEquals("functionIntegration", routeToFunction.path("semanticFeature").asText());
    assertNotNull(functionReadsStore);
    assertEquals("reads", functionReadsStore.path("semanticFeature").asText());
    assertNotNull(workflowTransition);
    assertEquals("WorkflowTransition", workflowTransition.path("eClass").asText());
    assertNotNull(principalPermission);
    assertEquals("Permission", principalPermission.path("eClass").asText());
    assertNotNull(rootContainsService);
    assertTrue(rootContainsService.path("containment").asBoolean());
    assertNotNull(serviceContainsApi);
    assertTrue(serviceContainsApi.path("containment").asBoolean());
  }

  /** Confirms imported PIM and PSM JSON can be exported and validated by EVL. */
  @Test
  void validatesPimAndPsmModelsWithEvl() {
    TestPlatformStore store = new TestPlatformStore(tempDir);
    store.initialize();
    AuthService authService = new AuthService(store, Duration.ofHours(1));
    ProjectService projectService = new ProjectService(store, authService);
    ModelService service = new ModelService(store, projectService);

    JsonNode pim =
        service
            .importModel(
                ModelLevel.PIM, "pim.xmi", samplePimXmi().getBytes(StandardCharsets.UTF_8), "xmi")
            .modelJson();
    JsonNode psm =
        service
            .importModel(
                ModelLevel.PSM, "psm.xmi", samplePsmXmi().getBytes(StandardCharsets.UTF_8), "xmi")
            .modelJson();

    ModelService.ValidationResult pimValidation = service.validate(ModelLevel.PIM, pim);
    ModelService.ValidationResult psmValidation = service.validate(ModelLevel.PSM, psm);

    assertFalse(pimValidation.valid());
    assertFalse(pimValidation.issues().isEmpty());
    assertTrue(
        pimValidation.issues().stream()
            .anyMatch(
                issue ->
                    issue.constraint().startsWith("EVL_")
                        || issue.constraint().startsWith("PIM-")));
    assertFalse(psmValidation.valid());
    assertFalse(psmValidation.issues().isEmpty());
    assertTrue(
        psmValidation.issues().stream()
            .anyMatch(
                issue ->
                    issue.constraint().startsWith("EVL_")
                        || issue.constraint().startsWith("AWS-")
                        || issue.constraint().startsWith("PSM-")));
  }

  /**
   * Ensures inverse-only trace references do not prevent PIM JSON from being exported back to XMI.
   */
  @Test
  void exportsPimWhenOnlyInverseTraceReferencesRemain() {
    TestPlatformStore store = new TestPlatformStore(tempDir);
    store.initialize();
    AuthService authService = new AuthService(store, Duration.ofHours(1));
    ProjectService projectService = new ProjectService(store, authService);
    ModelService service = new ModelService(store, projectService);

    ObjectNode pim =
        (ObjectNode)
            service
                .importModel(
                    ModelLevel.PIM,
                    "pim.xmi",
                    samplePimXmi().getBytes(StandardCharsets.UTF_8),
                    "xmi")
                .modelJson()
                .deepCopy();
    pim.remove("traceModel");

    byte[] exported = assertDoesNotThrow(() -> service.exportModel(ModelLevel.PIM, pim, "xmi"));

    assertTrue(exported.length > 0);
  }

  /** Verifies validate-by-id prefers stored source XMI when editable JSON has stale references. */
  @Test
  void validatesStoredXmiByIdWhenJsonHasStaleReferences() {
    TestPlatformStore store = new TestPlatformStore(tempDir);
    store.initialize();
    AuthService authService = new AuthService(store, Duration.ofHours(1));
    ProjectService projectService = new ProjectService(store, authService);
    ModelService service = new ModelService(store, projectService);
    UserRecord user = authService.register("xmi-owner@example.com", "password123", "Owner").user();
    ProjectRecord project = projectService.create(user, "XMI Project", "");
    byte[] xmi = samplePimXmi().getBytes(StandardCharsets.UTF_8);
    JsonNode imported = service.importModel(ModelLevel.PIM, "pim.xmi", xmi, "xmi").modelJson();
    ModelRecord created = service.create(user, ModelLevel.PIM, project.id(), "pim", imported);
    service.attachSourceXmi(created, xmi);

    ObjectNode staleJson = (ObjectNode) created.modelJson().deepCopy();
    ((ObjectNode)
            staleJson.path("services").path(0).path("workflows").path(0).path("steps").path(0))
        .put("compensation", "missing-compensation");
    service.update(user, ModelLevel.PIM, created.id(), "pim", staleJson);

    ModelService.ValidationResult validation = service.validate(user, ModelLevel.PIM, created.id());

    assertFalse(
        validation.issues().stream().anyMatch(issue -> "XmiExport".equals(issue.constraint())));
  }

  /** Ensures patching a PIM model does not discard the stored source XMI fallback. */
  @Test
  void patchPreservesStoredXmiWhenJsonHasStaleReferences() {
    TestPlatformStore store = new TestPlatformStore(tempDir);
    store.initialize();
    AuthService authService = new AuthService(store, Duration.ofHours(1));
    ProjectService projectService = new ProjectService(store, authService);
    ModelService service = new ModelService(store, projectService);
    UserRecord user =
        authService.register("xmi-patch-owner@example.com", "password123", "Owner").user();
    ProjectRecord project = projectService.create(user, "XMI Patch Project", "");
    JsonNode imported =
        service
            .importModel(
                ModelLevel.PIM, "pim.xmi", samplePimXmi().getBytes(StandardCharsets.UTF_8), "xmi")
            .modelJson();
    ModelRecord created = service.create(user, ModelLevel.PIM, project.id(), "pim", imported);

    assertDoesNotThrow(
        () ->
            service.patch(
                user,
                ModelLevel.PIM,
                created.id(),
                "pim",
                java.util.List.of(
                    new ModelService.ModelPatchOperation(
                        "add",
                        "/services/0/workflows/0/steps/0/compensation",
                        store.objectMapper().getNodeFactory().textNode("missing-compensation")))));

    ModelService.ValidationResult validation = service.validate(user, ModelLevel.PIM, created.id());

    assertFalse(
        validation.issues().stream().anyMatch(issue -> "XmiExport".equals(issue.constraint())));
  }

  /** Ensures patching a PSM model does not discard the stored source XMI fallback. */
  @Test
  void patchPreservesStoredPsmXmiWhenJsonHasStaleReferences() {
    TestPlatformStore store = new TestPlatformStore(tempDir);
    store.initialize();
    AuthService authService = new AuthService(store, Duration.ofHours(1));
    ProjectService projectService = new ProjectService(store, authService);
    ModelService service = new ModelService(store, projectService);
    UserRecord user =
        authService.register("psm-xmi-patch-owner@example.com", "password123", "Owner").user();
    ProjectRecord project = projectService.create(user, "PSM XMI Patch Project", "");
    JsonNode imported =
        service
            .importModel(
                ModelLevel.PSM, "psm.xmi", samplePsmXmi().getBytes(StandardCharsets.UTF_8), "xmi")
            .modelJson();
    ModelRecord created = service.create(user, ModelLevel.PSM, project.id(), "psm", imported);

    assertDoesNotThrow(
        () ->
            service.patch(
                user,
                ModelLevel.PSM,
                created.id(),
                "psm",
                java.util.List.of(
                    new ModelService.ModelPatchOperation(
                        "replace",
                        "/relationshipViews/0/source",
                        store.objectMapper().getNodeFactory().textNode("missing-source")))));

    ModelService.ValidationResult validation = service.validate(user, ModelLevel.PSM, created.id());

    assertFalse(
        validation.issues().stream().anyMatch(issue -> "XmiExport".equals(issue.constraint())));
  }

  /**
   * Finds a graph relationship by endpoint IDs and edge kind.
   *
   * @param relationships graph relationship array
   * @param source expected source element ID
   * @param target expected target element ID
   * @param kind expected relationship kind
   * @return matching relationship node, or {@code null} when absent
   */
  private JsonNode relationship(JsonNode relationships, String source, String target, String kind) {
    for (JsonNode relationship : relationships) {
      if (source.equals(relationship.path("sourceElementId").asText())
          && target.equals(relationship.path("targetElementId").asText())
          && kind.equals(relationship.path("kind").asText())) {
        return relationship;
      }
    }
    return null;
  }

  /**
   * Builds a CIM JSON model whose semantic relationship endpoints are missing but whose frontend
   * graph edge still exposes endpoint IDs.
   *
   * @param service model service used to import the base relationship fixture
   * @return mutable CIM JSON model with graph-only relationship endpoints
   */
  private ObjectNode frontendEndpointOnlyRelationshipModel(ModelService service) {
    ObjectNode model =
        (ObjectNode)
            service
                .importModel(
                    ModelLevel.CIM,
                    "relationships.xmi",
                    sampleCimRelationshipXmi().getBytes(StandardCharsets.UTF_8),
                    "xmi")
                .modelJson();
    ObjectNode semanticRelationship = (ObjectNode) model.path("relationships").path(0);
    semanticRelationship.remove("source");
    semanticRelationship.remove("target");
    ObjectNode graphRelationship =
        ((ObjectNode) model.path("graph")).withArray("relationships").addObject();
    graphRelationship.put("id", "rel-source-target");
    graphRelationship.put("kind", "DOMAIN_RELATIONSHIP");
    graphRelationship.put("sourceElementId", "vo-source");
    graphRelationship.put("targetElementId", "vo-target");
    return model;
  }

  /**
   * Asserts validation did not fail because relationship source or target features were missing
   * during EVL model loading.
   *
   * @param validation validation result to inspect
   */
  private void assertNoRelationshipEndpointLoadingError(ModelService.ValidationResult validation) {
    assertTrue(
        validation.issues().stream()
            .noneMatch(
                issue ->
                    "EVL_MODEL_LOADING".equals(issue.constraint())
                        && (issue.message().contains("required feature 'source'")
                            || issue.message().contains("required feature 'target'"))));
  }

  /**
   * Extracts the feature name from the platform's required-feature validation issue message.
   *
   * @param issue validation issue to parse
   * @return missing feature name, or an empty string for unrelated messages
   */
  private String missingFeature(ModelService.ValidationIssue issue) {
    String prefix = "Required CIM feature is missing: ";
    return issue.message().startsWith(prefix) ? issue.message().substring(prefix.length()) : "";
  }

  /**
   * Creates the minimal valid CIM root used by strict-export negative tests.
   *
   * @param store file store that supplies the configured object mapper
   * @return mutable CIM model JSON object
   */
  private ObjectNode minimalCimModel(TestPlatformStore store) {
    ObjectNode model = store.objectMapper().createObjectNode();
    model.put("eClass", "CIMModel");
    model.put("id", "cim-root");
    model.put("name", "Strict Export");
    model.put("domainName", "Strict Export");
    return model;
  }

  /**
   * Returns a small CIM XMI document with a goal, actor, and supporting capability.
   *
   * @return sample CIM XMI text
   */
  private String sampleCimXmi() {
    return """
    <?xml version="1.0" encoding="UTF-8"?>
    <cim:CIMModel xmi:version="2.0"
        xmlns:xmi="http://www.omg.org/XMI"
        xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
        xmlns:cim="https://modless.org/cim/1.0"
        xmlns:kernel="https://modless.org/kernel/1.0"
        xmi:id="cim-root"
        id="cim-root"
        name="Test Grants Model"
        domainName="Test Grants">
      <goals xmi:id="goal-1" id="goal-1" name="Protect residents"/>
      <actors xmi:id="actor-1" id="actor-1" name="Resident"/>
      <capabilities xmi:id="cap-1" id="cap-1" name="Review applications" supports="goal-1"/>
    </cim:CIMModel>
    """;
  }

  /**
   * Returns a CIM XMI document with a relationship that has explicit semantic endpoints.
   *
   * @return relationship-focused CIM XMI text
   */
  private String sampleCimRelationshipXmi() {
    return """
    <?xml version="1.0" encoding="UTF-8"?>
    <cim:CIMModel xmi:version="2.0"
        xmlns:xmi="http://www.omg.org/XMI"
        xmlns:cim="https://modless.org/cim/1.0"
        xmi:id="cim-root"
        id="cim-root"
        name="Relationship Model"
        domainName="Relationship Test">
      <valueObjects xmi:id="vo-source" id="vo-source" name="Source"/>
      <valueObjects xmi:id="vo-target" id="vo-target" name="Target"/>
      <relationships xmi:id="rel-source-target" id="rel-source-target"
          name="Source to target"
          source="vo-source"
          target="vo-target">
        <sourceMultiplicity xmi:id="source-multiplicity" id="source-multiplicity"
            name="Source multiplicity"
            lowerBound="1" upperBound="1"/>
        <targetMultiplicity xmi:id="target-multiplicity" id="target-multiplicity"
            name="Target multiplicity"
            lowerBound="0" upperBound="1"/>
      </relationships>
    </cim:CIMModel>
    """;
  }

  /**
   * Returns a stale CIM relationship XMI fixture that omits required endpoint references.
   *
   * @return CIM XMI text without relationship {@code source} and {@code target}
   */
  private String sampleCimRelationshipXmiWithoutEndpoints() {
    return """
    <?xml version="1.0" encoding="UTF-8"?>
    <cim:CIMModel xmi:version="2.0"
        xmlns:xmi="http://www.omg.org/XMI"
        xmlns:cim="https://modless.org/cim/1.0"
        xmi:id="cim-root"
        id="cim-root"
        name="Relationship Model"
        domainName="Relationship Test">
      <valueObjects xmi:id="vo-source" id="vo-source" name="Source"/>
      <valueObjects xmi:id="vo-target" id="vo-target" name="Target"/>
      <relationships xmi:id="rel-source-target" id="rel-source-target"
          name="Source to target">
        <sourceMultiplicity xmi:id="source-multiplicity" id="source-multiplicity"
            name="Source multiplicity"
            lowerBound="1" upperBound="1"/>
        <targetMultiplicity xmi:id="target-multiplicity" id="target-multiplicity"
            name="Target multiplicity"
            lowerBound="0" upperBound="1"/>
      </relationships>
    </cim:CIMModel>
    """;
  }

  /**
   * Returns CIM XMI with required enum values set to their metamodel default literals.
   *
   * @return CIM XMI text for required enum import checks
   */
  private String sampleCimRequiredEnumXmi() {
    return """
    <?xml version="1.0" encoding="UTF-8"?>
    <cim:CIMModel xmi:version="2.0"
        xmlns:xmi="http://www.omg.org/XMI"
        xmlns:cim="https://modless.org/cim/1.0"
        id="cim-root"
        name="Required Enum Model"
        domainName="Required Enum Test">
      <informationItems id="info-public-text"
          name="PublicText"
          businessName="Public text"
          type="TEXT"
          required="true"
          collection="false"
          classification="class-public"/>
      <classifications id="class-public"
          name="Public"
          kind="PUBLIC"
          identifiability="NON_PERSONAL"/>
    </cim:CIMModel>
    """;
  }

  /**
   * Returns XMI with two CIM roots to exercise importer root validation.
   *
   * @return invalid multi-root CIM XMI text
   */
  private String multiRootCimXmi() {
    return """
    <?xml version="1.0" encoding="UTF-8"?>
    <xmi:XMI xmi:version="2.0"
        xmlns:xmi="http://www.omg.org/XMI"
        xmlns:cim="https://modless.org/cim/1.0">
      <cim:CIMModel id="cim-root-1" name="One" domainName="One"/>
      <cim:CIMModel id="cim-root-2" name="Two" domainName="Two"/>
    </xmi:XMI>
    """;
  }

  /**
   * Returns a compact PIM XMI fixture covering function, API, datastore, workflow, and principal
   * reference edges.
   *
   * @return sample PIM XMI text
   */
  private String samplePimXmi() {
    return """
    <?xml version="1.0" encoding="UTF-8"?>
    <pim:PIMModel xmi:version="2.0"
        xmlns:xmi="http://www.omg.org/XMI"
        xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
        xmlns:pim="https://modless.org/pim/1.0"
        xmlns:data="https://modless.org/pim/data/1.0"
        xmlns:workflow="https://modless.org/pim/workflow/1.0"
        id="pim-root"
        name="PIM Test"
        architectureStyle="WORKFLOW_ORCHESTRATED_SERVERLESS"
        domainName="Test Grants">
      <services id="svc-main"
          name="Main Service"
          responsibility="Test service capability">
        <functions id="fn-submit"
            name="Submit Handler"
            functionKind="COMMAND_HANDLER"
            reads="store-app"/>
        <apis id="api-main"
            name="Main API"
            apiStyle="RESOURCE_ORIENTED_HTTP">
          <routes id="route-submit"
              name="Submit route"
              method="POST"
              pathTemplate="/submit"
              functionIntegration="fn-submit"/>
        </apis>
        <stores xsi:type="data:DataStore" id="store-app"
            name="Application Store"
            storeKind="DOCUMENT"
            consistencyNeed="EVENTUAL"/>
        <workflows id="workflow-main"
            name="Main workflow"
            workflowKind="ORCHESTRATION">
          <steps xsi:type="workflow:StartStep"
              id="wf-start"
              name="Start"
              orderIndex="1"/>
          <steps xsi:type="workflow:TaskStep"
              id="wf-task"
              name="Submit"
              orderIndex="2"
              invokesFunction="fn-submit"/>
          <steps xsi:type="workflow:SuccessEndStep"
              id="wf-end"
              name="End"
              orderIndex="3"/>
          <transitions id="wf-transition"
              name="Start to Submit"
              source="wf-start"
              target="wf-task"
              defaultTransition="true"/>
          <transitions id="wf-transition-end"
              name="Submit to End"
              source="wf-task"
              target="wf-end"
              defaultTransition="true"/>
        </workflows>
      </services>
      <principals id="principal-resident"
          name="Resident"
          principalKind="HUMAN_USER"
          privileged="false">
        <permissions id="permission-submit"
            name="Submit permission"
            effect="ALLOW"
            action="invoke"
            resource="submit"
            targetResource="fn-submit"/>
      </principals>
    </pim:PIMModel>
    """;
  }

  /**
   * Returns a compact AWS PSM XMI fixture with stack containments and a relationship view.
   *
   * @return sample AWS PSM XMI text
   */
  private String samplePsmXmi() {
    return """
    <?xml version="1.0" encoding="UTF-8"?>
    <xmi:XMI xmi:version="2.0"
        xmlns:xmi="http://www.omg.org/XMI"
        xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
        xmlns:awspsm="https://modless.org/awspsm/1.0"
        xmlns:awspsmapi="https://modless.org/awspsm/api/1.0"
        xmlns:awspsmcompute="https://modless.org/awspsm/compute/1.0"
        xmlns:awspsmintegrations="https://modless.org/awspsm/integrations/1.0">
      <awspsm:AwsPsmModel id="psm-root" name="PSM Test"
          allResources="route-submit lambda-submit">
        <stacks id="stack-main" name="Main stack" resources="route-submit lambda-submit">
          <resources xsi:type="awspsmapi:HttpApiRoute"
              id="route-submit"
              name="Submit route"
              logicalId="SubmitRoute"
              method="POST"
              path="/submit"
              routeKey="POST /submit"/>
          <resources xsi:type="awspsmcompute:AwsLambdaFunction"
              id="lambda-submit"
              name="Submit handler"
              logicalId="SubmitHandler"
              functionName="submit-handler"/>
        </stacks>
        <relationshipViews xsi:type="awspsmintegrations:ApiGatewayLambdaIntegrationView"
            id="view_api_lambda_submit"
            name="Submit route to handler"
            source="route-submit"
            target="lambda-submit"
            route="route-submit"
            function="lambda-submit"/>
      </awspsm:AwsPsmModel>
    </xmi:XMI>
    """;
  }
}
