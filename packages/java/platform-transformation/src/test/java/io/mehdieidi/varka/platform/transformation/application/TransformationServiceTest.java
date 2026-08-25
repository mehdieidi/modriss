package io.mehdieidi.varka.platform.transformation.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.mehdieidi.varka.platform.artifact.domain.ArtifactRecord;
import io.mehdieidi.varka.platform.kernel.ModelLevel;
import io.mehdieidi.varka.platform.kernel.PlatformException;
import io.mehdieidi.varka.platform.model.application.ModelService;
import io.mehdieidi.varka.platform.model.domain.ModelRecord;
import io.mehdieidi.varka.platform.transformation.synchronization.ConflictResolution;
import io.mehdieidi.varka.platform.transformation.synchronization.GeneratedBaseline;
import io.mehdieidi.varka.platform.transformation.synchronization.ModelBaselineRepository;
import io.mehdieidi.varka.platform.transformation.synchronization.SynchronizationResult;
import io.mehdieidi.varka.platform.transformation.synchronization.SynchronizationStatus;
import io.mehdieidi.varka.platform.transformation.synchronization.TransformationDirection;
import io.mehdieidi.varka.platform.transformation.synchronization.TransformationSynchronizationCoordinator;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.ResourceLock;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

/** End-to-end regression tests for platform transformations backed by the MDE runners. */
@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("epsilon-runtime")
class TransformationServiceTest {

  /** Isolated repository root used by the JSON store for each test. */
  @TempDir Path tempDir;

  /**
   * Verifies that CIM-to-PIM invokes the formal ETL pipeline and returns PIM semantics rather than
   * relabeled CIM JSON.
   *
   * @throws Exception when fixture import, persistence, or transformation fails
   */
  @Test
  void cimToPimCreatesPimSemanticModelInsteadOfRelabelingCimJson() throws Exception {
    PlatformTestFixtures.ServiceStack services =
        PlatformTestFixtures.createServicesWithTransformations(tempDir);
    PlatformTestFixtures.AuthenticatedContext context =
        PlatformTestFixtures.registerOwner(services, "owner@example.com", "Owner", "Climate");

    ModelService.ImportResult imported =
        services
            .models()
            .importModel(ModelLevel.CIM, "cim.xmi", PlatformTestFixtures.climateCimXmi(), "xmi");
    ModelRecord cim =
        services
            .models()
            .create(
                context.user(),
                ModelLevel.CIM,
                context.project().id(),
                "climate-cim",
                imported.modelJson());
    services.models().update(context.user(), ModelLevel.CIM, cim.id(), cim.name(), cim.modelJson());

    ModelRecord pim = services.transformations().cimToPim(context.user(), cim.id());

    assertEquals(ModelLevel.PIM, pim.level());
    assertEquals(
        pim.id(),
        services.projects().get(context.user(), context.project().id()).activeModelIds().get("pim"),
        "Generating PIM must make it the project's active PIM so it is restored after refresh.");
    assertEquals("PIM", pim.modelJson().path("modelLevel").asText());
    assertEquals("PIMModel", pim.modelJson().path("eClass").asText());
    assertFalse(pim.modelJson().path("services").isEmpty());
    assertTrue(
        serviceContainmentNonEmpty(pim.modelJson(), "functions"),
        "Generated PIM functions should be contained by ServerlessService.");
    assertTrue(
        serviceContainmentHasApiRoutes(pim.modelJson()),
        "Generated PIM APIs and routes should be contained by ServerlessService.");
    assertFalse(pim.modelJson().path("eventTypes").isEmpty());
    assertTrue(
        serviceContainmentNonEmpty(pim.modelJson(), "stores")
            || serviceContainmentNonEmpty(pim.modelJson(), "dataStores"),
        "Generated PIM storage should be contained by ServerlessService.");
    assertTrue(
        serviceContainmentNonEmpty(pim.modelJson(), "workflows"),
        "Generated PIM workflows should be contained by ServerlessService.");
    assertTrue(
        pim.modelJson()
            .path("graph")
            .path("elements")
            .findValuesAsString("eClass")
            .contains("Workflow"),
        "Generated PIM graph should include workflow containers for the workflow view.");
    assertEquals("GENERATED_BY_ETL", pim.modelJson().path("transformationStatus").asText());
    assertTrue(
        pim.modelJson().path("commands").isMissingNode(),
        "Generated PIM must not retain CIM root containments.");
    assertEquals(
        pim.modelJson().path("readiness").path("manualDecisions").size(),
        pim.modelJson().path("manualBacklog").size(),
        "Only explicit ETL manual decisions should be mirrored into the frontend backlog.");
    assertFalse(
        pim.modelJson().path("readiness").path("findings").isEmpty(),
        "Readiness findings should remain on the readiness assessment.");
    assertTrue(
        pim.modelJson()
            .path("graph")
            .path("elements")
            .findValuesAsString("eClass")
            .contains("Function"));
  }

  /**
   * Ensures validation of a generated PIM model does not duplicate manual decisions already
   * produced by the ETL readiness assessment.
   *
   * @throws Exception when fixture import, persistence, transformation, or validation fails
   */
  @Test
  void generatedPimValidationDoesNotCreateAdditionalManualTasks() throws Exception {
    PlatformTestFixtures.ServiceStack services =
        PlatformTestFixtures.createServicesWithTransformations(tempDir);
    PlatformTestFixtures.AuthenticatedContext context =
        PlatformTestFixtures.registerOwner(services, "owner@example.com", "Owner", "Climate");

    ModelService.ImportResult imported =
        services
            .models()
            .importModel(ModelLevel.CIM, "cim.xmi", PlatformTestFixtures.climateCimXmi(), "xmi");
    ModelRecord cim =
        services
            .models()
            .create(
                context.user(),
                ModelLevel.CIM,
                context.project().id(),
                "climate-cim",
                imported.modelJson());

    ModelRecord pim = services.transformations().cimToPim(context.user(), cim.id());
    int generatedManualTasks = pim.modelJson().path("manualBacklog").size();
    Set<String> manualDecisionIds = new HashSet<>();
    pim.modelJson()
        .path("readiness")
        .path("manualDecisions")
        .forEach(decision -> manualDecisionIds.add(decision.path("id").asText()));
    long graphManualDecisions =
        pim.modelJson().path("graph").path("elements").findValuesAsString("eClass").stream()
            .filter("ManualDecision"::equals)
            .count();

    ModelRecord saved =
        services
            .models()
            .update(
                context.user(),
                ModelLevel.PIM,
                pim.id(),
                pim.name(),
                pim.modelJson(),
                pim.revision());
    ModelService.ValidationResult validation =
        services.models().validate(context.user(), ModelLevel.PIM, saved.id());
    List<ModelService.ValidationIssue> readinessIssues =
        validation.issues().stream()
            .filter(issue -> issue.constraint().startsWith("PIM-READY-"))
            .toList();

    assertEquals(
        40,
        generatedManualTasks,
        "Climate sample should expose the generated manual backlog once.");
    assertEquals(
        generatedManualTasks,
        manualDecisionIds.size(),
        "Generated ManualDecision IDs must be unique so the issue board does not "
            + "deduplicate visible manual tasks.");
    assertEquals(
        generatedManualTasks,
        graphManualDecisions,
        "Generated graph elements must expose every ManualDecision immediately.");
    assertTrue(
        readinessIssues.isEmpty(),
        "Validating the stored generated PIM must not add readiness issues that look "
            + "like extra manual tasks: "
            + readinessIssues);
  }

  /**
   * Verifies that PIM-to-PSM invokes the formal ETL pipeline and preserves stack, stage, resource,
   * and relationship information needed by the frontend.
   *
   * @throws Exception when fixture import, persistence, transformation, or validation fails
   */
  @Test
  void pimToPsmRunsFormalEtlAndPreservesGeneratedRelationships() throws Exception {
    PlatformTestFixtures.ServiceStack services =
        PlatformTestFixtures.createServicesWithTransformations(tempDir);
    PlatformTestFixtures.AuthenticatedContext context =
        PlatformTestFixtures.registerOwner(services, "owner@example.com", "Owner", "Climate");

    ModelService.ImportResult imported =
        services
            .models()
            .importModel(ModelLevel.CIM, "cim.xmi", PlatformTestFixtures.climateCimXmi(), "xmi");
    ModelRecord cim =
        services
            .models()
            .create(
                context.user(),
                ModelLevel.CIM,
                context.project().id(),
                "climate-cim",
                imported.modelJson());
    ModelRecord pim = services.transformations().cimToPim(context.user(), cim.id());
    services.models().update(context.user(), ModelLevel.PIM, pim.id(), pim.name(), pim.modelJson());

    ModelRecord psm = services.transformations().pimToPsm(context.user(), pim.id());

    assertEquals(
        psm.id(),
        services.projects().get(context.user(), context.project().id()).activeModelIds().get("psm"),
        "Generating PSM must make it the project's active PSM so it is restored after refresh.");

    byte[] generatedSourceXmi = services.models().sourceXmi(psm).orElseThrow();
    ModelService.ValidationResult sourceXmiValidation =
        services.models().validateGeneratedXmi(ModelLevel.PSM, generatedSourceXmi);
    assertTrue(
        sourceXmiValidation.issues().stream()
            .noneMatch(issue -> "ApiHasRoutes".equals(issue.constraint())),
        "ETL source XMI must link routes to every API: " + sourceXmiValidation.issues());

    assertEquals(ModelLevel.PSM, psm.level());
    assertEquals("PSM", psm.modelJson().path("modelLevel").asText());
    assertEquals("AwsPsmModel", psm.modelJson().path("eClass").asText());
    assertEquals("GENERATED_BY_ETL", psm.modelJson().path("transformationStatus").asText());
    assertTrue(
        psm.modelJson().path("validationIssues").isMissingNode()
            || psm.modelJson().path("validationIssues").isEmpty());
    assertTrue(
        psm.modelJson().path("stacks").findValues("resources").stream()
            .anyMatch(resources -> resources.isArray() && !resources.isEmpty()),
        "Generated PSM JSON should preserve stack-contained resources.");
    assertTrue(
        psm.modelJson().path("stages").findValues("deploysStacks").stream()
            .anyMatch(stacks -> stacks.isArray() && !stacks.isEmpty()),
        "Generated PSM JSON should preserve stage-to-stack deployment references.");
    assertFalse(
        psm.modelJson().path("relationshipViews").isEmpty(),
        "Generated PSM should include relationship view elements for integrations.");
    assertEquals(
        psm.modelJson().path("manualBacklog").size(),
        psm.modelJson().path("graph").path("manualBacklog").size(),
        "Root and graph manual backlog counts should stay in sync.");
    assertTrue(
        psm.modelJson()
            .path("graph")
            .path("elements")
            .findValuesAsString("eClass")
            .contains("AwsLambdaFunction"));
    assertTrue(
        psm.modelJson().path("graph").path("elements").size() > 10,
        "Generated PSM graph should expose enough elements for frontend views.");
    assertFalse(
        psm.modelJson().path("graph").path("relationships").isEmpty(),
        "Imported AWS PSM graph should include reference and relationship edges.");
    var relationshipKinds =
        psm.modelJson().path("graph").path("relationships").findValuesAsString("kind");
    assertFalse(
        relationshipKinds.stream().anyMatch(kind -> kind.endsWith("_VIEW")),
        "Generated PSM relationship views should be rendered as canonical edge kinds.");
    assertTrue(
        relationshipKinds.contains("EVENT_FLOW"),
        "Generated event/messaging shortcuts should be visible in PSM views.");
    assertTrue(
        relationshipKinds.contains("TARGETS"),
        "Generated EventBridge targets should be visible in PSM views.");
    assertTrue(
        psm.modelJson().path("commands").isMissingNode(),
        "Generated PSM must not retain PIM/CIM root containments.");
    ModelService.ValidationResult validation =
        services.models().validate(ModelLevel.PSM, psm.modelJson());
    assertTrue(
        validation.issues().stream().noneMatch(issue -> "ApiHasRoutes".equals(issue.constraint())),
        "Generated PSM must link routes to every API: " + validation.issues());
    ModelRecord savedPsm =
        services
            .models()
            .update(context.user(), ModelLevel.PSM, psm.id(), psm.name(), psm.modelJson());
    ModelService.ValidationResult storedValidation =
        services.models().validate(context.user(), ModelLevel.PSM, savedPsm.id());
    assertTrue(
        storedValidation.issues().stream()
            .noneMatch(issue -> "ApiHasRoutes".equals(issue.constraint())),
        "Stored PSM source XMI must retain API routes: " + storedValidation.issues());
    assertTrue(
        validation.issues().stream()
            .noneMatch(
                issue ->
                    "ProductionLogGroupShouldUseKms".equals(issue.constraint())
                        || "ApiLambdaPermissionRecommended".equals(issue.constraint())),
        "Generated PSM should satisfy production log group KMS and API Lambda permission links: "
            + validation.issues());
    assertTrue(
        validation.issues().stream()
            .noneMatch(
                issue ->
                    "StackResourcesExist".equals(issue.constraint())
                        || "StageDeploysAtLeastOneStack".equals(issue.constraint())
                        || "StackHasResources".equals(issue.constraint())
                        || "DeployableStackHasResources".equals(issue.constraint())),
        "PSM validation should not lose stack resources or stage deployment references: "
            + validation.issues());
  }

  /**
   * Ensures artifact generation uses the formal EGX generator and stores artifact metadata without
   * embedding full file contents in model JSON.
   *
   * @throws Exception when fixture import, persistence, transformation, or generation fails
   */
  @Test
  void psmToArtifactRunsFormalEgxGeneratorInsteadOfScaffold() throws Exception {
    PlatformTestFixtures.ServiceStack services =
        PlatformTestFixtures.createServicesWithTransformations(tempDir);
    PlatformTestFixtures.AuthenticatedContext context =
        PlatformTestFixtures.registerOwner(services, "owner@example.com", "Owner", "Climate");

    ModelService.ImportResult imported =
        services
            .models()
            .importModel(ModelLevel.CIM, "cim.xmi", PlatformTestFixtures.climateCimXmi(), "xmi");
    ModelRecord cim =
        services
            .models()
            .create(
                context.user(),
                ModelLevel.CIM,
                context.project().id(),
                "climate-cim",
                imported.modelJson());
    ModelRecord pim = services.transformations().cimToPim(context.user(), cim.id());
    ModelRecord psm = services.transformations().pimToPsm(context.user(), pim.id());
    services.models().update(context.user(), ModelLevel.PSM, psm.id(), psm.name(), psm.modelJson());
    services
        .models()
        .patch(
            context.user(),
            ModelLevel.PSM,
            psm.id(),
            psm.name(),
            java.util.List.of(
                new ModelService.ModelPatchOperation(
                    "replace",
                    "/summary",
                    services
                        .store()
                        .objectMapper()
                        .getNodeFactory()
                        .textNode("Saved before artifact generation."))));

    ArtifactRecord artifact = services.transformations().psmToArtifact(context.user(), psm.id());

    assertFalse(artifact.files().isEmpty());
    assertTrue(
        artifact.modelJson().path("files").isMissingNode(),
        "Artifact metadata must not duplicate generated file contents.");
    assertEquals(artifact.files().size(), artifact.modelJson().path("fileCount").asInt());
    assertTrue(
        artifact.modelJson().path("traceability").isObject(),
        "Artifact metadata must include source-element traceability.");
    assertTrue(
        hasTraceabilityPath(artifact.modelJson().path("traceability"), artifact.files().keySet()),
        "Artifact traceability must point at generated files.");
    assertEquals(artifact.id(), services.artifacts().get(context.user(), artifact.id()).id());
    assertFalse(services.artifacts().list(context.user(), context.project().id()).isEmpty());
    assertTrue(artifact.files().containsKey("generated/reports/generation-report.md"));
    assertTrue(
        artifact.files().keySet().stream().anyMatch(path -> path.startsWith("src/")),
        "Formal generation should produce source files, not only a placeholder scaffold.");
    assertTrue(
        artifact.files().keySet().stream()
            .anyMatch(path -> path.startsWith("src/functions/") && path.endsWith("/handler.go")),
        "Formal generation should preserve generated PSM XMI and emit Lambda handlers.");
    String samTemplate =
        artifact.files().entrySet().stream()
            .filter(
                entry -> entry.getKey().startsWith("template-") && entry.getKey().endsWith(".yaml"))
            .map(Map.Entry::getValue)
            .findFirst()
            .orElseThrow();
    assertTrue(
        samTemplate.length() < 500_000, "SAM template must not contain runaway EGL indentation.");
    assertFalse(
        samTemplate.contains("Resources: {}"),
        "SAM template must be generated from AWS PSM resources.");

    String handlerPath =
        artifact.files().keySet().stream()
            .filter(path -> path.startsWith("src/functions/") && path.endsWith("/handler.go"))
            .findFirst()
            .orElseThrow();
    String customLogic = "\t// Developer-owned validation.";
    services
        .artifacts()
        .updateFile(
            context.user(),
            artifact.id(),
            handlerPath,
            artifact
                .files()
                .get(handlerPath)
                .replace(
                    "\t// TODO: add developer-owned validation that cannot be derived from JSON"
                        + " Schema.",
                    customLogic));

    ArtifactRecord regenerated = services.transformations().psmToArtifact(context.user(), psm.id());

    assertEquals(psm.id(), regenerated.modelJson().path("sourceModelId").asText());
    assertTrue(
        regenerated.files().get(handlerPath).contains(customLogic),
        "Regeneration must preserve content edited inside protected regions.");
  }

  @Test
  void firstTransformationCreatesWorkingAndExactRawBaseline() throws Exception {
    PlatformTestFixtures.ServiceStack services =
        PlatformTestFixtures.createServicesWithTransformations(tempDir);
    PlatformTestFixtures.AuthenticatedContext context =
        PlatformTestFixtures.registerOwner(services, "first@example.com", "First", "First");
    ModelRecord cim = createClimateCim(services, context, "first-cim");

    ModelRecord working = services.transformations().cimToPim(context.user(), cim.id());
    SynchronizationResult result = TransformationService.consumeLastSynchronization();
    GeneratedBaseline baseline =
        new ModelBaselineRepository(services.store())
            .get(context.project().id(), TransformationDirection.CIM_TO_PIM, cim.id())
            .orElseThrow();

    assertEquals(SynchronizationStatus.APPLIED, result.status());
    assertEquals(working.id(), baseline.targetModelId());
    assertEquals(working.modelJson(), baseline.rawGeneratedModel());
    assertTrue(
        java.util.Arrays.equals(
            services.models().sourceXmi(working).orElseThrow(), baseline.rawGeneratedXmi()));
  }

  @Test
  void successfulSynchronizationKeepsUserRefinementButAdvancesRawBaseline() throws Exception {
    PlatformTestFixtures.ServiceStack services =
        PlatformTestFixtures.createServicesWithTransformations(tempDir);
    PlatformTestFixtures.AuthenticatedContext context =
        PlatformTestFixtures.registerOwner(
            services, "baseline@example.com", "Baseline", "Baseline");
    ModelRecord cim = createClimateCim(services, context, "baseline-cim");
    ModelRecord first = services.transformations().cimToPim(context.user(), cim.id());
    TransformationService.consumeLastSynchronization();
    String generatedDescription = first.modelJson().path("description").asText();
    ObjectNode refined = (ObjectNode) first.modelJson().deepCopy();
    refined.put("description", "User-owned architectural refinement");
    services
        .models()
        .update(
            context.user(), ModelLevel.PIM, first.id(), first.name(), refined, first.revision());

    ModelRecord merged = services.transformations().cimToPim(context.user(), cim.id());
    SynchronizationResult mergedResult = TransformationService.consumeLastSynchronization();
    GeneratedBaseline baseline =
        new ModelBaselineRepository(services.store())
            .get(context.project().id(), TransformationDirection.CIM_TO_PIM, cim.id())
            .orElseThrow();

    assertEquals(SynchronizationStatus.APPLIED, mergedResult.status());
    assertEquals(
        "User-owned architectural refinement", merged.modelJson().path("description").asText());
    assertEquals(generatedDescription, baseline.rawGeneratedModel().path("description").asText());
    assertNotEquals(merged.modelJson(), baseline.rawGeneratedModel());

    ModelRecord idempotent = services.transformations().cimToPim(context.user(), cim.id());
    SynchronizationResult idempotentResult = TransformationService.consumeLastSynchronization();
    assertEquals(0, idempotentResult.incomingChanges());
    assertEquals(0, idempotentResult.conflicts());
    assertEquals(
        "User-owned architectural refinement", idempotent.modelJson().path("description").asText());
  }

  @Test
  void legacyWorkingModelWithoutBaselineIsNeverOverwritten() throws Exception {
    PlatformTestFixtures.ServiceStack services =
        PlatformTestFixtures.createServicesWithTransformations(tempDir);
    PlatformTestFixtures.AuthenticatedContext context =
        PlatformTestFixtures.registerOwner(services, "legacy@example.com", "Legacy", "Legacy");
    ModelRecord cim = createClimateCim(services, context, "legacy-cim");
    ModelService.ImportResult imported =
        services
            .models()
            .importModel(ModelLevel.PIM, "legacy-pim.xmi", PlatformTestFixtures.pimXmi(), "xmi");
    ObjectNode legacyJson = (ObjectNode) imported.modelJson().deepCopy();
    legacyJson.put("transformedFromModelId", cim.id());
    legacyJson.put("description", "Do not overwrite this legacy working model");
    ModelRecord legacy =
        services
            .models()
            .create(
                context.user(), ModelLevel.PIM, context.project().id(), "legacy-pim", legacyJson);

    ModelRecord returned = services.transformations().cimToPim(context.user(), cim.id());
    SynchronizationResult result = TransformationService.consumeLastSynchronization();

    assertEquals(SynchronizationStatus.BOOTSTRAP_REQUIRED, result.status());
    assertEquals(legacy.id(), returned.id());
    assertEquals(
        "Do not overwrite this legacy working model",
        returned.modelJson().path("description").asText());
    assertTrue(
        new ModelBaselineRepository(services.store())
            .get(context.project().id(), TransformationDirection.CIM_TO_PIM, cim.id())
            .isEmpty());
  }

  @Test
  void modifyingWorkingModelMakesPendingConflictSessionStale() throws Exception {
    PlatformTestFixtures.ServiceStack services =
        PlatformTestFixtures.createServicesWithTransformations(tempDir);
    PlatformTestFixtures.AuthenticatedContext context =
        PlatformTestFixtures.registerOwner(services, "stale@example.com", "Stale", "Stale");
    ModelRecord cim = createClimateCim(services, context, "stale-cim");
    ModelRecord pim = services.transformations().cimToPim(context.user(), cim.id());
    TransformationService.consumeLastSynchronization();

    ObjectNode userChanged = (ObjectNode) pim.modelJson().deepCopy();
    userChanged.put("name", "User target name");
    pim =
        services
            .models()
            .update(
                context.user(), ModelLevel.PIM, pim.id(), pim.name(), userChanged, pim.revision());
    ObjectNode generatorChanged = (ObjectNode) cim.modelJson().deepCopy();
    generatorChanged.put("name", "Upstream generated name");
    cim =
        services
            .models()
            .update(
                context.user(),
                ModelLevel.CIM,
                cim.id(),
                cim.name(),
                generatorChanged,
                cim.revision());
    byte[] changedSourceXmi =
        new String(services.models().sourceXmi(cim).orElseThrow(), StandardCharsets.UTF_8)
            .replace(
                "name=\"ClimateReliefGrantsBusinessModel\"", "name=\"Upstream generated name\"")
            .getBytes(StandardCharsets.UTF_8);
    services
        .store()
        .writeBytesAtomically(
            Path.of("projects", context.project().id(), "models", "cim", cim.id() + ".xmi"),
            changedSourceXmi);

    ModelRecord unchanged = services.transformations().cimToPim(context.user(), cim.id());
    SynchronizationResult conflictResult = TransformationService.consumeLastSynchronization();
    assertEquals(SynchronizationStatus.CONFLICTS, conflictResult.status());
    assertEquals("User target name", unchanged.modelJson().path("name").asText());
    assertFalse(conflictResult.conflictDetails().isEmpty());

    ObjectNode changedWhilePending = (ObjectNode) unchanged.modelJson().deepCopy();
    changedWhilePending.put("description", "Changed after conflict creation");
    services
        .models()
        .update(
            context.user(),
            ModelLevel.PIM,
            unchanged.id(),
            unchanged.name(),
            changedWhilePending,
            unchanged.revision());
    TransformationSynchronizationCoordinator coordinator =
        new TransformationSynchronizationCoordinator(services.store(), services.models());
    conflictResult
        .conflictDetails()
        .forEach(
            conflict ->
                coordinator.resolve(
                    context.user(),
                    context.project().id(),
                    conflictResult.sessionId(),
                    conflict.conflictId(),
                    ConflictResolution.KEEP_USER));

    PlatformException stale =
        assertThrows(
            PlatformException.class,
            () ->
                coordinator.finalizeSession(
                    context.user(), context.project().id(), conflictResult.sessionId()));
    assertEquals(409, stale.status());
    assertEquals(
        "Changed after conflict creation",
        services
            .models()
            .get(context.user(), ModelLevel.PIM, unchanged.id())
            .modelJson()
            .path("description")
            .asText());
  }

  @Test
  void finalizesConflictWithGeneratedValueAndAdvancesBaseline() throws Exception {
    PlatformTestFixtures.ServiceStack services =
        PlatformTestFixtures.createServicesWithTransformations(tempDir);
    PlatformTestFixtures.AuthenticatedContext context =
        PlatformTestFixtures.registerOwner(services, "resolve@example.com", "Resolve", "Resolve");
    ModelRecord cim = createClimateCim(services, context, "resolve-cim");
    ModelRecord pim = services.transformations().cimToPim(context.user(), cim.id());
    TransformationService.consumeLastSynchronization();

    ObjectNode userChanged = (ObjectNode) pim.modelJson().deepCopy();
    userChanged.put("name", "User target name");
    pim =
        services
            .models()
            .update(
                context.user(), ModelLevel.PIM, pim.id(), pim.name(), userChanged, pim.revision());
    ObjectNode generatorChanged = (ObjectNode) cim.modelJson().deepCopy();
    generatorChanged.put("name", "Generated target name");
    cim =
        services
            .models()
            .update(
                context.user(),
                ModelLevel.CIM,
                cim.id(),
                cim.name(),
                generatorChanged,
                cim.revision());
    byte[] sourceXmi = services.models().sourceXmi(cim).orElseThrow();
    services
        .store()
        .writeBytesAtomically(
            Path.of("projects", context.project().id(), "models", "cim", cim.id() + ".xmi"),
            new String(sourceXmi, StandardCharsets.UTF_8)
                .replace(
                    "name=\"ClimateReliefGrantsBusinessModel\"", "name=\"Generated target name\"")
                .getBytes(StandardCharsets.UTF_8));

    ModelRecord unchanged = services.transformations().cimToPim(context.user(), cim.id());
    SynchronizationResult pending = TransformationService.consumeLastSynchronization();
    assertEquals(SynchronizationStatus.CONFLICTS, pending.status());
    TransformationSynchronizationCoordinator coordinator =
        new TransformationSynchronizationCoordinator(services.store(), services.models());
    pending
        .conflictDetails()
        .forEach(
            conflict ->
                coordinator.resolve(
                    context.user(),
                    context.project().id(),
                    pending.sessionId(),
                    conflict.conflictId(),
                    ConflictResolution.TAKE_GENERATED));

    ModelRecord finalized =
        coordinator.finalizeSession(context.user(), context.project().id(), pending.sessionId());
    assertEquals("Generated target name", finalized.modelJson().path("name").asText());
    assertTrue(
        new ModelBaselineRepository(services.store())
            .get(context.project().id(), TransformationDirection.CIM_TO_PIM, cim.id())
            .isPresent());
    assertThrows(
        PlatformException.class,
        () -> coordinator.getSession(context.user(), context.project().id(), pending.sessionId()));
    assertEquals(unchanged.id(), finalized.id());
  }

  private ModelRecord createClimateCim(
      PlatformTestFixtures.ServiceStack services,
      PlatformTestFixtures.AuthenticatedContext context,
      String name) {
    ModelService.ImportResult imported =
        services
            .models()
            .importModel(ModelLevel.CIM, "cim.xmi", PlatformTestFixtures.climateCimXmi(), "xmi");
    return services
        .models()
        .create(context.user(), ModelLevel.CIM, context.project().id(), name, imported.modelJson());
  }

  private boolean serviceContainmentNonEmpty(JsonNode model, String childField) {
    for (JsonNode service : model.path("services")) {
      JsonNode children = service.path(childField);
      if (children.isArray() && !children.isEmpty()) {
        return true;
      }
    }
    return false;
  }

  private boolean hasGeneratedElement(JsonNode model, String eClass, String generatedFrom) {
    return model.path("graph").path("elements").findValuesAsString("eClass").contains(eClass)
        && model
            .path("graph")
            .path("elements")
            .findValuesAsString("generatedFrom")
            .contains(generatedFrom);
  }

  private boolean serviceContainmentHasApiRoutes(JsonNode model) {
    for (JsonNode service : model.path("services")) {
      for (JsonNode api : service.path("apis")) {
        if (!api.path("routes").isEmpty()) {
          return true;
        }
      }
    }
    return false;
  }

  private boolean hasTraceabilityPath(JsonNode traceability, Set<String> files) {
    for (var field : traceability.properties()) {
      JsonNode paths = field.getValue();
      if (!paths.isArray()) {
        continue;
      }
      for (JsonNode path : paths) {
        if (files.contains(path.asText())) {
          return true;
        }
      }
    }
    return false;
  }
}
