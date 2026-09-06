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
import io.mehdieidi.varka.platform.modeling.xmi.XmiModelImportService;
import io.mehdieidi.varka.platform.transformation.synchronization.ConflictResolution;
import io.mehdieidi.varka.platform.transformation.synchronization.GeneratedBaseline;
import io.mehdieidi.varka.platform.transformation.synchronization.ModelBaselineRepository;
import io.mehdieidi.varka.platform.transformation.synchronization.SynchronizationResult;
import io.mehdieidi.varka.platform.transformation.synchronization.SynchronizationStatus;
import io.mehdieidi.varka.platform.transformation.synchronization.TransformationDirection;
import io.mehdieidi.varka.platform.transformation.synchronization.TransformationSynchronizationCoordinator;
import io.mehdieidi.varka.platform.transformation.synchronization.TransformationValidationException;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.eclipse.emf.common.util.TreeIterator;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.util.EcoreUtil;
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
   * Catalog F-01/V-01: an invalid source is rejected before ETL and creates no downstream model.
   */
  @Test
  void invalidSourceIsRejectedBeforeCimToPimEtl() throws Exception {
    PlatformTestFixtures.ServiceStack services =
        PlatformTestFixtures.createServicesWithTransformations(tempDir);
    PlatformTestFixtures.AuthenticatedContext context =
        PlatformTestFixtures.registerOwner(services, "v01@example.com", "V01", "Validation");
    ModelRecord cim = createClimateCim(services, context, "v01-cim");
    ObjectNode invalid = (ObjectNode) cim.modelJson().deepCopy();
    ObjectNode process = invalid.putArray("processes").addObject();
    process.put("eClass", "BusinessProcess");
    process.put("id", "v01-invalid-process");
    process.put("name", "Invalid process");
    process.putArray("steps");
    cim =
        services
            .models()
            .update(context.user(), ModelLevel.CIM, cim.id(), cim.name(), invalid, cim.revision());
    String cimId = cim.id();

    PlatformException failure =
        assertThrows(
            PlatformException.class,
            () -> services.transformations().cimToPim(context.user(), cimId));

    assertEquals(422, failure.status());
    assertTrue(failure.getMessage().contains("Source model validation failed"));
    assertFalse(
        services
            .projects()
            .get(context.user(), context.project().id())
            .activeModelIds()
            .containsKey("pim"));
  }

  /** Catalog V-02: malformed fresh generated XMI is rejected before baseline creation. */
  @Test
  void malformedFreshGeneratedTargetDoesNotCreateModelOrAdvanceBaseline() throws Exception {
    PlatformTestFixtures.ServiceStack services =
        PlatformTestFixtures.createServicesWithTransformations(tempDir);
    PlatformTestFixtures.AuthenticatedContext context =
        PlatformTestFixtures.registerOwner(services, "v02@example.com", "V02", "Validation");
    ModelRecord cim = createClimateCim(services, context, "v02-cim");
    ObjectNode generated =
        (ObjectNode)
            services
                .models()
                .importModel(ModelLevel.PIM, "pim.xmi", PlatformTestFixtures.pimXmi(), "xmi")
                .modelJson();
    TransformationSynchronizationCoordinator coordinator =
        new TransformationSynchronizationCoordinator(services.store(), services.models());

    assertThrows(
        TransformationValidationException.class,
        () ->
            coordinator.synchronize(
                context.user(),
                cim,
                ModelLevel.PIM,
                "v02-pim",
                generated,
                "not-xmi".getBytes(StandardCharsets.UTF_8),
                TransformationDirection.CIM_TO_PIM));

    assertFalse(
        services
            .projects()
            .get(context.user(), context.project().id())
            .activeModelIds()
            .containsKey("pim"));
    assertTrue(
        new ModelBaselineRepository(services.store())
            .get(context.project().id(), TransformationDirection.CIM_TO_PIM, cim.id())
            .isEmpty());
  }

  /** Catalog F-07: concurrent requests for one source/direction serialize to one target state. */
  @Test
  void concurrentTransformationsForOneRelationshipAreSerialized() throws Exception {
    PlatformTestFixtures.ServiceStack services =
        PlatformTestFixtures.createServicesWithTransformations(tempDir);
    PlatformTestFixtures.AuthenticatedContext context =
        PlatformTestFixtures.registerOwner(services, "f07@example.com", "F07", "Failure");
    ModelRecord cim = createClimateCim(services, context, "f07-cim");
    ObjectNode generated =
        (ObjectNode)
            services
                .models()
                .importModel(ModelLevel.PIM, "pim.xmi", PlatformTestFixtures.pimXmi(), "xmi")
                .modelJson();
    TransformationSynchronizationCoordinator coordinator =
        new TransformationSynchronizationCoordinator(services.store(), services.models());
    ExecutorService executor = Executors.newFixedThreadPool(2);
    try {
      Future<TransformationSynchronizationCoordinator.CoordinatedResult> first =
          executor.submit(
              () ->
                  coordinator.synchronize(
                      context.user(),
                      cim,
                      ModelLevel.PIM,
                      "f07-pim",
                      generated,
                      services.models().exportModel(ModelLevel.PIM, generated, "xmi"),
                      TransformationDirection.CIM_TO_PIM));
      Future<TransformationSynchronizationCoordinator.CoordinatedResult> second =
          executor.submit(
              () ->
                  coordinator.synchronize(
                      context.user(),
                      cim,
                      ModelLevel.PIM,
                      "f07-pim",
                      generated.deepCopy(),
                      services.models().exportModel(ModelLevel.PIM, generated, "xmi"),
                      TransformationDirection.CIM_TO_PIM));

      ModelRecord firstModel = first.get().model();
      ModelRecord secondModel = second.get().model();
      assertEquals(firstModel.id(), secondModel.id());
      assertTrue(
          new ModelBaselineRepository(services.store())
              .get(context.project().id(), TransformationDirection.CIM_TO_PIM, cim.id())
              .isPresent());
    } finally {
      executor.shutdownNow();
    }
  }

  /** Catalog F-08: a stale client revision is rejected before transformation commit. */
  @Test
  void staleSourceRevisionIsRejectedBeforeTransformationCommit() throws Exception {
    PlatformTestFixtures.ServiceStack services =
        PlatformTestFixtures.createServicesWithTransformations(tempDir);
    PlatformTestFixtures.AuthenticatedContext context =
        PlatformTestFixtures.registerOwner(services, "f08@example.com", "F08", "Failure");
    ModelRecord cim = createClimateCim(services, context, "f08-cim");

    PlatformException failure =
        assertThrows(
            PlatformException.class,
            () ->
                services.transformations().cimToPim(context.user(), cim.id(), cim.revision() + 1));

    assertEquals(409, failure.status());
    assertFalse(
        services
            .projects()
            .get(context.user(), context.project().id())
            .activeModelIds()
            .containsKey("pim"));
  }

  /** Catalog F-14: another project user cannot observe or mutate synchronization state. */
  @Test
  void unauthorizedTransformationDoesNotDiscloseOrChangeProjectState() throws Exception {
    PlatformTestFixtures.ServiceStack services =
        PlatformTestFixtures.createServicesWithTransformations(tempDir);
    PlatformTestFixtures.AuthenticatedContext owner =
        PlatformTestFixtures.registerOwner(services, "f14-owner@example.com", "Owner", "Failure");
    ModelRecord cim = createClimateCim(services, owner, "f14-cim");
    var intruder =
        services.auth().register("f14-intruder@example.com", "password123", "Intruder").user();

    PlatformException failure =
        assertThrows(
            PlatformException.class, () -> services.transformations().cimToPim(intruder, cim.id()));

    assertTrue(failure.status() == 403 || failure.status() == 404);
    assertFalse(
        services
            .projects()
            .get(owner.user(), owner.project().id())
            .activeModelIds()
            .containsKey("pim"));
  }

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
    assertEquals(
        pim.modelJson().path("manualBacklog").size(),
        pim.modelJson().path("graph").path("manualBacklog").size(),
        "The issue board must receive the same generated tasks from root and graph metadata.");
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
    for (int index = 0; index < generatedManualTasks; index++) {
      String title = pim.modelJson().path("manualBacklog").path(index).path("title").asText();
      String question =
          pim.modelJson()
              .path("readiness")
              .path("manualDecisions")
              .path(index)
              .path("question")
              .asText();
      assertFalse(title.endsWith(" "), "Manual task titles must not end with a dangling space.");
      if (title.startsWith("Manual decision -") && question.length() > 80) {
        assertTrue(
            title.endsWith("..."),
            "Long manual decision titles must be visibly truncated: " + title);
      }
    }
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
    assertFalse(
        psm.modelJson().path("manualBacklog").isEmpty(),
        "Generated PSM manual decisions must be visible to the frontend issue board.");
    for (int index = 0; index < psm.modelJson().path("manualBacklog").size(); index++) {
      String title = psm.modelJson().path("manualBacklog").path(index).path("title").asText();
      String question =
          psm.modelJson()
              .path("readiness")
              .path("manualDecisions")
              .path(index)
              .path("question")
              .asText();
      assertEquals(
          question,
          title,
          "AWS manual task titles should present the reviewer question, not an internal rule id.");
    }
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

  /** Catalog L-03: an unchanged source is an idempotent synchronization no-op. */
  @Test
  void unchangedSourceDoesNotDuplicateOrRewriteWorkingModel() throws Exception {
    PlatformTestFixtures.ServiceStack services =
        PlatformTestFixtures.createServicesWithTransformations(tempDir);
    PlatformTestFixtures.AuthenticatedContext context =
        PlatformTestFixtures.registerOwner(services, "l03@example.com", "L03", "Lifecycle");
    ModelRecord cim = createClimateCim(services, context, "l03-cim");
    ModelRecord first = services.transformations().cimToPim(context.user(), cim.id());
    TransformationService.consumeLastSynchronization();
    GeneratedBaseline before =
        new ModelBaselineRepository(services.store())
            .get(context.project().id(), TransformationDirection.CIM_TO_PIM, cim.id())
            .orElseThrow();

    ModelRecord repeated = services.transformations().cimToPim(context.user(), cim.id());
    SynchronizationResult result = TransformationService.consumeLastSynchronization();

    assertEquals(SynchronizationStatus.APPLIED, result.status());
    assertEquals(first.id(), repeated.id());
    assertEquals(first.revision(), repeated.revision());
    assertEquals(first.modelJson(), repeated.modelJson());
    assertEquals(
        before.baselineVersion(),
        new ModelBaselineRepository(services.store())
            .get(context.project().id(), TransformationDirection.CIM_TO_PIM, cim.id())
            .orElseThrow()
            .baselineVersion());
  }

  /** Catalog L-04: source metadata/revision changes do not create semantic target changes. */
  @Test
  void sourceRevisionChangeWithEquivalentGeneratedOutputAdvancesBaselineSafely() throws Exception {
    PlatformTestFixtures.ServiceStack services =
        PlatformTestFixtures.createServicesWithTransformations(tempDir);
    PlatformTestFixtures.AuthenticatedContext context =
        PlatformTestFixtures.registerOwner(services, "l04@example.com", "L04", "Lifecycle");
    ModelRecord cim = createClimateCim(services, context, "l04-cim");
    ModelRecord first = services.transformations().cimToPim(context.user(), cim.id());
    TransformationService.consumeLastSynchronization();
    GeneratedBaseline before =
        new ModelBaselineRepository(services.store())
            .get(context.project().id(), TransformationDirection.CIM_TO_PIM, cim.id())
            .orElseThrow();
    ObjectNode changed = (ObjectNode) cim.modelJson().deepCopy();
    changed.put("summary", "equivalent generated target revision");
    ModelRecord revised =
        services
            .models()
            .update(context.user(), ModelLevel.CIM, cim.id(), cim.name(), changed, cim.revision());

    ModelRecord repeated = services.transformations().cimToPim(context.user(), revised.id());
    SynchronizationResult result = TransformationService.consumeLastSynchronization();
    GeneratedBaseline after =
        new ModelBaselineRepository(services.store())
            .get(context.project().id(), TransformationDirection.CIM_TO_PIM, revised.id())
            .orElseThrow();

    assertEquals(SynchronizationStatus.APPLIED, result.status());
    assertEquals(first.id(), repeated.id());
    assertTrue(result.conflicts() == 0);
    assertEquals(revised.revision(), after.sourceRevision());
    assertTrue(after.baselineVersion() >= before.baselineVersion());
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
  void regenerationPreservesIssueBoardTasksAcrossBothTransformationLevels() throws Exception {
    PlatformTestFixtures.ServiceStack services =
        PlatformTestFixtures.createServicesWithTransformations(tempDir);
    PlatformTestFixtures.AuthenticatedContext context =
        PlatformTestFixtures.registerOwner(services, "issues@example.com", "Issues", "Issues");
    ModelRecord cim = createClimateCim(services, context, "issues-cim");

    ModelRecord pim = services.transformations().cimToPim(context.user(), cim.id());
    Set<String> pimTaskIds = taskIds(pim.modelJson());
    assertFalse(pimTaskIds.isEmpty());
    ModelRecord psm = services.transformations().pimToPsm(context.user(), pim.id());
    Set<String> psmTaskIds = taskIds(psm.modelJson());
    assertFalse(psmTaskIds.isEmpty());
    ObjectNode reviewedPsm = (ObjectNode) psm.modelJson().deepCopy();
    ((ObjectNode) reviewedPsm.path("manualBacklog").path(0)).put("id", "legacy-title-derived-id");
    ((ObjectNode) reviewedPsm.path("manualBacklog").path(0)).put("status", "DONE");
    ((ObjectNode) reviewedPsm.path("readiness").path("manualDecisions").path(0))
        .put("blocking", false);
    services
        .models()
        .update(context.user(), ModelLevel.PSM, psm.id(), psm.name(), reviewedPsm, psm.revision());

    ModelRecord regeneratedPim = services.transformations().cimToPim(context.user(), cim.id());
    ModelRecord regeneratedPsm = services.transformations().pimToPsm(context.user(), pim.id());

    assertEquals(pimTaskIds, taskIds(regeneratedPim.modelJson()));
    assertEquals(
        regeneratedPim.modelJson().path("manualBacklog").size(),
        regeneratedPim.modelJson().path("graph").path("manualBacklog").size());
    assertEquals(psmTaskIds, taskIds(regeneratedPsm.modelJson()));
    assertEquals(
        regeneratedPsm.modelJson().path("manualBacklog").size(),
        regeneratedPsm.modelJson().path("graph").path("manualBacklog").size());
    assertEquals(
        "DONE", regeneratedPsm.modelJson().path("manualBacklog").path(0).path("status").asText());

    services.models().validate(context.user(), ModelLevel.PIM, regeneratedPim.id());
    services.models().validate(context.user(), ModelLevel.PSM, regeneratedPsm.id());
    assertEquals(
        pimTaskIds,
        taskIds(
            services
                .models()
                .get(context.user(), ModelLevel.PIM, regeneratedPim.id())
                .modelJson()));
    assertEquals(
        psmTaskIds,
        taskIds(
            services
                .models()
                .get(context.user(), ModelLevel.PSM, regeneratedPsm.id())
                .modelJson()));
  }

  /**
   * The browser's position save is a transport-only patch. It must not make a later regeneration
   * reuse a partially linked generated model or invalidate the PIM that is shown on the issue
   * board.
   */
  @Test
  void transportOnlyPositionEditsSurviveFullGenerationCycleWithoutSemanticIssues() {
    PlatformTestFixtures.ServiceStack services =
        PlatformTestFixtures.createServicesWithTransformations(tempDir);
    PlatformTestFixtures.AuthenticatedContext context =
        PlatformTestFixtures.registerOwner(
            services, "position-cycle@example.com", "Position", "Cycle");
    ModelRecord cim = createClimateCim(services, context, "position-cycle-cim");

    ModelRecord pim = services.transformations().cimToPim(context.user(), cim.id());
    TransformationService.consumeLastSynchronization();
    ModelRecord psm = services.transformations().pimToPsm(context.user(), pim.id());
    TransformationService.consumeLastSynchronization();
    services.transformations().psmToArtifact(context.user(), psm.id());

    cim = moveFirstGraphElement(services, context, ModelLevel.CIM, cim);
    pim = moveFirstGraphElement(services, context, ModelLevel.PIM, pim);

    ModelRecord regeneratedPim = services.transformations().cimToPim(context.user(), cim.id());
    assertEquals(
        SynchronizationStatus.APPLIED, TransformationService.consumeLastSynchronization().status());

    ModelService.ValidationResult validation =
        services.models().validate(context.user(), ModelLevel.PIM, regeneratedPim.id());
    assertTrue(
        validation.valid(),
        () ->
            "A transport-only edit must not expose stale generated references: "
                + validation.issues());
    assertTrue(
        validation.issues().stream()
            .noneMatch(
                issue ->
                    Set.of(
                            "ApiHasRoutes",
                            "TaskStepHasExactlyOneInvocation",
                            "RequiredDeadLetterPolicyHasChannel",
                            "GeneratedBlockingManualDecisionShouldHaveOwner")
                        .contains(issue.constraint())),
        () -> "Unexpected generated-model issues: " + validation.issues());
  }

  /**
   * Reproduces the UI order: PIM layout save, CIM layout save, then PIM regeneration/validation.
   */
  @Test
  void importedCimExactLayoutSaveOrderDoesNotCorruptRegeneratedPim() {
    PlatformTestFixtures.ServiceStack services =
        PlatformTestFixtures.createServicesWithTransformations(tempDir);
    PlatformTestFixtures.AuthenticatedContext context =
        PlatformTestFixtures.registerOwner(
            services, "exact-layout-order@example.com", "Exact", "Layout Order");
    ModelRecord cim = createClimateCim(services, context, "exact-layout-order-cim");

    ModelRecord pim = services.transformations().cimToPim(context.user(), cim.id());
    assertTrue(services.models().validate(context.user(), ModelLevel.PIM, pim.id()).valid());
    ModelRecord psm = services.transformations().pimToPsm(context.user(), pim.id());
    services.transformations().psmToArtifact(context.user(), psm.id());

    pim = moveFirstGraphElement(services, context, ModelLevel.PIM, pim);
    cim = moveFirstGraphElement(services, context, ModelLevel.CIM, cim);

    ModelRecord regeneratedPim = services.transformations().cimToPim(context.user(), cim.id());
    ModelService.ValidationResult validation =
        services.models().validate(context.user(), ModelLevel.PIM, regeneratedPim.id());

    assertTrue(
        validation.valid(),
        () -> "Exact layout-save order produced PIM issues: " + validation.issues());
    assertEquals(
        0,
        validation.issues().stream()
            .filter(issue -> "ERROR".equalsIgnoreCase(issue.severity()))
            .count(),
        () -> "Exact layout-save order produced PIM errors: " + validation.issues());
    assertTrue(
        validation.issues().isEmpty(),
        () -> "Exact layout-save order produced PIM warnings: " + validation.issues());
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

  /** Catalog L-07: deleting Working is explicit failure, never stale-baseline recreation. */
  @Test
  void deletedWorkingModelIsNotSilentlyRecreated() throws Exception {
    PlatformTestFixtures.ServiceStack services =
        PlatformTestFixtures.createServicesWithTransformations(tempDir);
    PlatformTestFixtures.AuthenticatedContext context =
        PlatformTestFixtures.registerOwner(services, "l07@example.com", "L07", "Lifecycle");
    ModelRecord cim = createClimateCim(services, context, "l07-cim");
    ModelRecord working = services.transformations().cimToPim(context.user(), cim.id());
    TransformationService.consumeLastSynchronization();
    ModelBaselineRepository repository = new ModelBaselineRepository(services.store());
    GeneratedBaseline before =
        repository
            .get(context.project().id(), TransformationDirection.CIM_TO_PIM, cim.id())
            .orElseThrow();
    services.models().delete(context.user(), ModelLevel.PIM, working.id());

    assertThrows(
        PlatformException.class,
        () -> services.transformations().cimToPim(context.user(), cim.id()));
    assertEquals(
        before.baselineVersion(),
        repository
            .get(context.project().id(), TransformationDirection.CIM_TO_PIM, cim.id())
            .orElseThrow()
            .baselineVersion());
    assertThrows(
        PlatformException.class,
        () -> services.models().get(context.user(), ModelLevel.PIM, working.id()));
  }

  /** Catalog L-08: a deleted source stops the relationship without deleting downstream history. */
  @Test
  void deletedSourceStopsTransformationAndRetainsDownstreamHistory() throws Exception {
    PlatformTestFixtures.ServiceStack services =
        PlatformTestFixtures.createServicesWithTransformations(tempDir);
    PlatformTestFixtures.AuthenticatedContext context =
        PlatformTestFixtures.registerOwner(services, "l08@example.com", "L08", "Lifecycle");
    ModelRecord cim = createClimateCim(services, context, "l08-cim");
    ModelRecord working = services.transformations().cimToPim(context.user(), cim.id());
    TransformationService.consumeLastSynchronization();
    ModelBaselineRepository repository = new ModelBaselineRepository(services.store());
    GeneratedBaseline before =
        repository
            .get(context.project().id(), TransformationDirection.CIM_TO_PIM, cim.id())
            .orElseThrow();
    services.models().delete(context.user(), ModelLevel.CIM, cim.id());

    assertThrows(
        PlatformException.class,
        () -> services.transformations().cimToPim(context.user(), cim.id()));
    assertEquals(
        working.id(), services.models().get(context.user(), ModelLevel.PIM, working.id()).id());
    assertEquals(
        before.baselineVersion(),
        repository
            .get(context.project().id(), TransformationDirection.CIM_TO_PIM, cim.id())
            .orElseThrow()
            .baselineVersion());
  }

  /** Catalog L-10: a JSON-only legacy Working model is reconstructed through the supported path. */
  @Test
  void missingWorkingXmiFallsBackToValidatedLegacyJsonImport() throws Exception {
    PlatformTestFixtures.ServiceStack services =
        PlatformTestFixtures.createServicesWithTransformations(tempDir);
    PlatformTestFixtures.AuthenticatedContext context =
        PlatformTestFixtures.registerOwner(services, "l10@example.com", "L10", "Lifecycle");
    ModelRecord cim = createClimateCim(services, context, "l10-cim");
    ModelRecord working = services.transformations().cimToPim(context.user(), cim.id());
    TransformationService.consumeLastSynchronization();
    ObjectNode edited = (ObjectNode) working.modelJson().deepCopy();
    edited.put("description", "legacy JSON refinement");
    working =
        services
            .models()
            .update(
                context.user(),
                ModelLevel.PIM,
                working.id(),
                working.name(),
                edited,
                working.revision());
    services
        .store()
        .deleteIfExists(
            Path.of(
                "projects",
                context.project().id(),
                "models",
                ModelLevel.PIM.apiName(),
                working.id() + ".xmi"));
    ObjectNode changed = (ObjectNode) cim.modelJson().deepCopy();
    changed.put("summary", "trigger legacy working import");
    cim =
        services
            .models()
            .update(context.user(), ModelLevel.CIM, cim.id(), cim.name(), changed, cim.revision());

    ModelRecord synchronizedWorking = services.transformations().cimToPim(context.user(), cim.id());
    SynchronizationResult result = TransformationService.consumeLastSynchronization();

    assertEquals(SynchronizationStatus.APPLIED, result.status());
    assertEquals(
        "legacy JSON refinement", synchronizedWorking.modelJson().path("description").asText());
    assertTrue(services.models().sourceXmi(synchronizedWorking).isPresent());
  }

  /** Catalog L-09/F-02: a corrupt Base aborts comparison without changing canonical Working. */
  @Test
  void corruptedBaselineFailsSafelyWithoutChangingWorking() throws Exception {
    PlatformTestFixtures.ServiceStack services =
        PlatformTestFixtures.createServicesWithTransformations(tempDir);
    PlatformTestFixtures.AuthenticatedContext context =
        PlatformTestFixtures.registerOwner(services, "corrupt-base@example.com", "Corrupt", "Base");
    ModelRecord cim = createClimateCim(services, context, "corrupt-base-cim");
    ModelRecord working = services.transformations().cimToPim(context.user(), cim.id());
    TransformationService.consumeLastSynchronization();
    ModelBaselineRepository repository = new ModelBaselineRepository(services.store());
    GeneratedBaseline baseline =
        repository
            .get(context.project().id(), TransformationDirection.CIM_TO_PIM, cim.id())
            .orElseThrow();
    repository.save(
        new GeneratedBaseline(
            baseline.direction(),
            baseline.projectId(),
            baseline.sourceModelId(),
            baseline.sourceRevision(),
            baseline.sourceFingerprint(),
            baseline.targetModelId(),
            baseline.baselineVersion(),
            baseline.transformationFingerprint(),
            baseline.createdAt(),
            baseline.rawGeneratedModel(),
            "not-xmi".getBytes(StandardCharsets.UTF_8)));
    ObjectNode changed = (ObjectNode) cim.modelJson().deepCopy();
    changed.put("summary", "force a new synchronization attempt");
    cim =
        services
            .models()
            .update(context.user(), ModelLevel.CIM, cim.id(), cim.name(), changed, cim.revision());
    ModelRecord changedCim = cim;

    assertThrows(
        PlatformException.class,
        () -> services.transformations().cimToPim(context.user(), changedCim.id()));
    ModelRecord unchanged = services.models().get(context.user(), ModelLevel.PIM, working.id());
    assertEquals(working.revision(), unchanged.revision());
    assertEquals(working.modelJson(), unchanged.modelJson());
  }

  /** Catalog F-04/F-05/F-06: persistence failure rolls back the canonical model and baseline. */
  @Test
  void persistenceFailureDoesNotAdvanceWorkingOrRawBaseline() throws Exception {
    PlatformTestFixtures.ServiceStack services =
        PlatformTestFixtures.createServicesWithTransformations(tempDir);
    PlatformTestFixtures.AuthenticatedContext context =
        PlatformTestFixtures.registerOwner(
            services, "tx@example.com", "Transaction", "Transaction");
    ModelRecord cim = createClimateCim(services, context, "tx-cim");
    ModelRecord working = services.transformations().cimToPim(context.user(), cim.id());
    TransformationService.consumeLastSynchronization();
    ModelBaselineRepository baselines = new ModelBaselineRepository(services.store());
    GeneratedBaseline before =
        baselines
            .get(context.project().id(), TransformationDirection.CIM_TO_PIM, cim.id())
            .orElseThrow();
    ObjectNode changed = (ObjectNode) cim.modelJson().deepCopy();
    changed.put("summary", "transaction failure source revision");
    cim =
        services
            .models()
            .update(context.user(), ModelLevel.CIM, cim.id(), cim.name(), changed, cim.revision());
    String cimId = cim.id();

    services.store().failAfterWrites(1);
    assertThrows(
        PlatformException.class, () -> services.transformations().cimToPim(context.user(), cimId));

    ModelRecord after = services.models().get(context.user(), ModelLevel.PIM, working.id());
    GeneratedBaseline baselineAfter =
        baselines
            .get(context.project().id(), TransformationDirection.CIM_TO_PIM, cim.id())
            .orElseThrow();
    assertEquals(working.revision(), after.revision());
    assertEquals(working.modelJson(), after.modelJson());
    assertEquals(before.baselineVersion(), baselineAfter.baselineVersion());
    assertEquals(before.rawGeneratedModel(), baselineAfter.rawGeneratedModel());
  }

  /** Catalog D-04: changing Working while pending makes the session stale. */
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

  /** Catalog D-05: an upstream edit after conflict creation invalidates the pending session. */
  @Test
  void modifyingUpstreamModelMakesPendingConflictSessionStale() throws Exception {
    PlatformTestFixtures.ServiceStack services =
        PlatformTestFixtures.createServicesWithTransformations(tempDir);
    PlatformTestFixtures.AuthenticatedContext context =
        PlatformTestFixtures.registerOwner(
            services, "upstream-stale@example.com", "Upstream", "Stale");
    ModelRecord cim = createClimateCim(services, context, "upstream-stale-cim");
    ModelRecord pim = services.transformations().cimToPim(context.user(), cim.id());
    TransformationService.consumeLastSynchronization();
    ObjectNode local = (ObjectNode) pim.modelJson().deepCopy();
    local.put("name", "local pending name");
    services
        .models()
        .update(context.user(), ModelLevel.PIM, pim.id(), pim.name(), local, pim.revision());
    ObjectNode source = (ObjectNode) cim.modelJson().deepCopy();
    source.put("name", "generated pending name");
    cim =
        services
            .models()
            .update(context.user(), ModelLevel.CIM, cim.id(), cim.name(), source, cim.revision());
    byte[] sourceXmi = services.models().sourceXmi(cim).orElseThrow();
    services
        .store()
        .writeBytesAtomically(
            Path.of("projects", context.project().id(), "models", "cim", cim.id() + ".xmi"),
            new String(sourceXmi, StandardCharsets.UTF_8)
                .replace(
                    "name=\"ClimateReliefGrantsBusinessModel\"", "name=\"generated pending name\"")
                .getBytes(StandardCharsets.UTF_8));

    services.transformations().cimToPim(context.user(), cim.id());
    SynchronizationResult pending = TransformationService.consumeLastSynchronization();
    assertEquals(SynchronizationStatus.CONFLICTS, pending.status());
    ObjectNode changedAgain = (ObjectNode) cim.modelJson().deepCopy();
    changedAgain.put("summary", "changed after pending session");
    cim =
        services
            .models()
            .update(
                context.user(), ModelLevel.CIM, cim.id(), cim.name(), changedAgain, cim.revision());
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
    PlatformException stale =
        assertThrows(
            PlatformException.class,
            () ->
                coordinator.finalizeSession(
                    context.user(), context.project().id(), pending.sessionId()));
    assertEquals(409, stale.status());
  }

  /** Catalog D-02/D-07: cancellation preserves state and a retry creates a new session. */
  @Test
  void cancelingSessionPreservesStateAndRetryCreatesNewSession() throws Exception {
    PlatformTestFixtures.ServiceStack services =
        PlatformTestFixtures.createServicesWithTransformations(tempDir);
    PlatformTestFixtures.AuthenticatedContext context =
        PlatformTestFixtures.registerOwner(services, "cancel@example.com", "Cancel", "Session");
    ModelRecord cim = createClimateCim(services, context, "cancel-cim");
    ModelRecord pim = services.transformations().cimToPim(context.user(), cim.id());
    TransformationService.consumeLastSynchronization();
    ModelBaselineRepository baselines = new ModelBaselineRepository(services.store());
    GeneratedBaseline before =
        baselines
            .get(context.project().id(), TransformationDirection.CIM_TO_PIM, cim.id())
            .orElseThrow();
    ObjectNode local = (ObjectNode) pim.modelJson().deepCopy();
    local.put("name", "Cancel user name");
    pim =
        services
            .models()
            .update(context.user(), ModelLevel.PIM, pim.id(), pim.name(), local, pim.revision());
    ObjectNode source = (ObjectNode) cim.modelJson().deepCopy();
    source.put("name", "Cancel generated name");
    cim =
        services
            .models()
            .update(context.user(), ModelLevel.CIM, cim.id(), cim.name(), source, cim.revision());
    byte[] sourceXmi = services.models().sourceXmi(cim).orElseThrow();
    services
        .store()
        .writeBytesAtomically(
            Path.of("projects", context.project().id(), "models", "cim", cim.id() + ".xmi"),
            new String(sourceXmi, StandardCharsets.UTF_8)
                .replace(
                    "name=\"ClimateReliefGrantsBusinessModel\"", "name=\"Cancel generated name\"")
                .getBytes(StandardCharsets.UTF_8));

    services.transformations().cimToPim(context.user(), cim.id());
    SynchronizationResult pending = TransformationService.consumeLastSynchronization();
    assertEquals(SynchronizationStatus.CONFLICTS, pending.status());
    TransformationSynchronizationCoordinator coordinator =
        new TransformationSynchronizationCoordinator(services.store(), services.models());
    coordinator.cancel(context.user(), context.project().id(), pending.sessionId());

    assertThrows(
        PlatformException.class,
        () -> coordinator.getSession(context.user(), context.project().id(), pending.sessionId()));
    assertEquals(
        "Cancel user name",
        services
            .models()
            .get(context.user(), ModelLevel.PIM, pim.id())
            .modelJson()
            .path("name")
            .asText());
    assertEquals(
        before.baselineVersion(),
        baselines
            .get(context.project().id(), TransformationDirection.CIM_TO_PIM, cim.id())
            .orElseThrow()
            .baselineVersion());

    services.transformations().cimToPim(context.user(), cim.id());
    SynchronizationResult retried = TransformationService.consumeLastSynchronization();
    assertEquals(SynchronizationStatus.CONFLICTS, retried.status());
    assertNotEquals(pending.sessionId(), retried.sessionId());
  }

  /** Catalog D-06: a session cannot finalize after its baseline version advances. */
  @Test
  void baselineAdvanceRejectsOldResolvedSession() throws Exception {
    PlatformTestFixtures.ServiceStack services =
        PlatformTestFixtures.createServicesWithTransformations(tempDir);
    PlatformTestFixtures.AuthenticatedContext context =
        PlatformTestFixtures.registerOwner(
            services, "baseline-stale@example.com", "Baseline", "Stale");
    ModelRecord cim = createClimateCim(services, context, "baseline-stale-cim");
    ModelRecord pim = services.transformations().cimToPim(context.user(), cim.id());
    TransformationService.consumeLastSynchronization();
    ObjectNode local = (ObjectNode) pim.modelJson().deepCopy();
    local.put("name", "Baseline user name");
    pim =
        services
            .models()
            .update(context.user(), ModelLevel.PIM, pim.id(), pim.name(), local, pim.revision());
    ObjectNode source = (ObjectNode) cim.modelJson().deepCopy();
    source.put("name", "Baseline generated name");
    cim =
        services
            .models()
            .update(context.user(), ModelLevel.CIM, cim.id(), cim.name(), source, cim.revision());
    byte[] sourceXmi = services.models().sourceXmi(cim).orElseThrow();
    services
        .store()
        .writeBytesAtomically(
            Path.of("projects", context.project().id(), "models", "cim", cim.id() + ".xmi"),
            new String(sourceXmi, StandardCharsets.UTF_8)
                .replace(
                    "name=\"ClimateReliefGrantsBusinessModel\"", "name=\"Baseline generated name\"")
                .getBytes(StandardCharsets.UTF_8));

    services.transformations().cimToPim(context.user(), cim.id());
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
    ModelBaselineRepository baselines = new ModelBaselineRepository(services.store());
    GeneratedBaseline baseline =
        baselines
            .get(context.project().id(), TransformationDirection.CIM_TO_PIM, cim.id())
            .orElseThrow();
    baselines.save(
        new GeneratedBaseline(
            baseline.direction(),
            baseline.projectId(),
            baseline.sourceModelId(),
            baseline.sourceRevision(),
            baseline.sourceFingerprint(),
            baseline.targetModelId(),
            baseline.baselineVersion() + 1,
            baseline.transformationFingerprint(),
            Instant.now(),
            baseline.rawGeneratedModel(),
            baseline.rawGeneratedXmi()));

    PlatformException stale =
        assertThrows(
            PlatformException.class,
            () ->
                coordinator.finalizeSession(
                    context.user(), context.project().id(), pending.sessionId()));
    assertEquals(409, stale.status());
  }

  /** Catalog F-09/F-10: finalization is one-shot and a fresh coordinator reloads durable state. */
  @Test
  void finalizesConflictWithGeneratedValueAndAdvancesBaseline() throws Exception {
    PlatformTestFixtures.ServiceStack services =
        PlatformTestFixtures.createServicesWithTransformations(tempDir);
    PlatformTestFixtures.AuthenticatedContext context =
        PlatformTestFixtures.registerOwner(services, "resolve@example.com", "Resolve", "Resolve");
    ModelRecord cim = createClimateCim(services, context, "resolve-cim");
    ModelRecord pim = services.transformations().cimToPim(context.user(), cim.id());
    TransformationService.consumeLastSynchronization();
    int manualTaskCount = pim.modelJson().path("manualBacklog").size();

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
    // Catalog D-01/D-03/F-03: an unresolved session is durable, cannot be finalized, and leaves
    // the canonical Working model untouched.
    assertEquals(
        pending.sessionId(),
        coordinator.getSession(context.user(), context.project().id(), pending.sessionId()).id());
    assertEquals("User target name", unchanged.modelJson().path("name").asText());
    assertThrows(
        PlatformException.class,
        () ->
            coordinator.finalizeSession(
                context.user(), context.project().id(), pending.sessionId()));
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
    assertEquals(
        manualTaskCount,
        finalized.modelJson().path("manualBacklog").size(),
        "Taking every generated conflict must not erase platform manual tasks.");
    assertTrue(
        new ModelBaselineRepository(services.store())
            .get(context.project().id(), TransformationDirection.CIM_TO_PIM, cim.id())
            .isPresent());
    assertThrows(
        PlatformException.class,
        () -> coordinator.getSession(context.user(), context.project().id(), pending.sessionId()));
    assertThrows(
        PlatformException.class,
        () ->
            coordinator.finalizeSession(
                context.user(), context.project().id(), pending.sessionId()));
    assertEquals(unchanged.id(), finalized.id());
  }

  @Test
  void cimAddEditDeleteWorkflowThenPimToPsmRegenerationCompletes() throws Exception {
    PlatformTestFixtures.ServiceStack services =
        PlatformTestFixtures.createServicesWithTransformations(tempDir);
    PlatformTestFixtures.AuthenticatedContext context =
        PlatformTestFixtures.registerOwner(services, "journey@example.com", "Journey", "Journey");
    ModelRecord cim = createClimateCim(services, context, "journey-cim");
    ModelRecord pim = services.transformations().cimToPim(context.user(), cim.id());
    TransformationService.consumeLastSynchronization();
    Set<String> initialPimWarnings =
        warningSignatures(services.models().validate(context.user(), ModelLevel.PIM, pim.id()));
    assertFalse(
        initialPimWarnings.stream()
            .anyMatch(
                warning ->
                    warning.startsWith("FilteringTopicShouldUseSubscriptionFilters::")
                        || warning.startsWith("PrivilegedPrincipalNeedsPermissions::")),
        () -> "Fresh ETL output lost generated nested elements: " + initialPimWarnings);
    Resource initialPim = loadSourceXmi(services, ModelLevel.PIM, pim, "fresh-pim-containment");
    for (String eventId :
        List.of(
            "evt-submitted", "evt-approved", "evt-disbursement-scheduled", "evt-appeal-filed")) {
      EObject topic = findByGeneratedFrom(initialPim, eventId, "Topic");
      assertTrue(topic != null, () -> "Expected topic generated from " + eventId);
      assertFalse(
          values(topic, "subscriptions").isEmpty(),
          () -> "Expected generated subscriptions for topic from " + eventId);
      ObjectNode topicJson = findGeneratedElement(pim.modelJson(), "Topic", eventId);
      assertTrue(topicJson != null, () -> "Expected stored JSON topic generated from " + eventId);
      assertFalse(
          topicJson.path("subscriptions").isEmpty(),
          () -> "Stored JSON lost generated subscriptions for topic from " + eventId);
    }
    for (String principalSourceId :
        List.of(
            "actor-senior-reviewer", "actor-finance-lead", "rolesenior-review", "role-finance")) {
      EObject principal = findByGeneratedFrom(initialPim, principalSourceId, "Principal");
      assertTrue(principal != null, () -> "Expected principal generated from " + principalSourceId);
      assertFalse(
          values(principal, "permissions").isEmpty(),
          () -> "Expected generated permissions for principal from " + principalSourceId);
      ObjectNode principalJson =
          findGeneratedElement(pim.modelJson(), "Principal", principalSourceId);
      assertTrue(
          principalJson != null,
          () -> "Expected stored JSON principal generated from " + principalSourceId);
      assertFalse(
          principalJson.path("permissions").isEmpty(),
          () -> "Stored JSON lost generated permissions for principal from " + principalSourceId);
    }
    int initialPimTasks = pim.modelJson().path("manualBacklog").size();
    ModelRecord initialPsm = services.transformations().pimToPsm(context.user(), pim.id());
    TransformationService.consumeLastSynchronization();
    // The browser persists generated PSM view/layout state before the upstream iterative edit.
    // This transport-only patch must not re-export the semantic resource.
    initialPsm =
        services
            .models()
            .patch(
                context.user(),
                ModelLevel.PSM,
                initialPsm.id(),
                initialPsm.name(),
                List.of(
                    new ModelService.ModelPatchOperation(
                        "add", "/views/-", services.store().objectMapper().createObjectNode())),
                initialPsm.revision());
    Resource roundTrippedPsm =
        loadSourceXmi(services, ModelLevel.PSM, initialPsm, "round-trip-psm");
    roundTrippedPsm
        .getAllContents()
        .forEachRemaining(
            object -> {
              if (!"IamPolicyDocument".equals(object.eClass().getName())) return;
              assertFalse(
                  values(object, "statements").isEmpty(),
                  () ->
                      "Browser PSM persistence emptied required policy document "
                          + object.eGet(object.eClass().getEStructuralFeature("id")));
            });
    Set<String> initialPsmWarnings =
        warningSignatures(
            services.models().validate(context.user(), ModelLevel.PSM, initialPsm.id()));
    int initialPsmTasks = initialPsm.modelJson().path("manualBacklog").size();

    String addedProcessId = "journey-added-process";
    cim =
        cloneCimProcessIntoSourceXmi(
            services, context, cim, "proc-appeal-lifecycle", addedProcessId);
    pim = services.transformations().cimToPim(context.user(), cim.id());
    assertEquals(
        SynchronizationStatus.APPLIED, TransformationService.consumeLastSynchronization().status());
    Resource regeneratedPim = loadSourceXmi(services, ModelLevel.PIM, pim, "added-process-pim");
    EObject generatedWorkflow = findByGeneratedFrom(regeneratedPim, addedProcessId, "Workflow");
    assertTrue(generatedWorkflow != null, "The ETL rule must generate the added workflow.");
    assertFalse(
        values(generatedWorkflow, "transitions").isEmpty(),
        "The ETL-generated workflow must retain the CIM process transitions.");
    for (EObject step : values(generatedWorkflow, "steps")) {
      if ("StartStep".equals(step.eClass().getName())) continue;
      assertTrue(
          values(generatedWorkflow, "transitions").stream()
              .anyMatch(transition -> reference(transition, "target") == step),
          () -> step.eClass().getName() + " must be targeted by an ETL-generated transition.");
    }
    // Exact shorter failure path: after adding the CIM process, PIM→PSM must succeed before any
    // PIM refinement or deletion. This catches empty required IAM policy statements immediately.
    ModelRecord psmAfterAddedProcess =
        services.transformations().pimToPsm(context.user(), pim.id());
    SynchronizationResult addedProcessPsmResult =
        TransformationService.consumeLastSynchronization();
    assertEquals(SynchronizationStatus.APPLIED, addedProcessPsmResult.status());
    assertTrue(
        services
            .models()
            .validateGeneratedXmi(
                ModelLevel.PSM, services.models().sourceXmi(psmAfterAddedProcess).orElseThrow())
            .valid(),
        "PIM→PSM after adding a CIM business process must preserve required IAM statements.");
    psmAfterAddedProcess
        .modelJson()
        .path("elements")
        .forEach(
            element -> {
              if (!"IamPolicyDocument".equals(element.path("type").asText())) return;
              assertTrue(
                  element.path("statements").size() > 0,
                  "Generated IAM policy documents must contain at least one statement.");
            });
    Resource refinedPim = loadSourceXmi(services, ModelLevel.PIM, pim, "refine-added-workflow");
    EObject addedWorkflow = findByGeneratedFrom(refinedPim, addedProcessId, "Workflow");
    assertTrue(addedWorkflow != null, "The added CIM process must create a PIM workflow.");
    addedWorkflow.eSet(
        addedWorkflow.eClass().getEStructuralFeature("executionSemantics"),
        "Manually refined execution semantics");
    pim =
        saveChangedSourceXmi(services, context, pim, ModelLevel.PIM, refinedPim, "refine workflow");

    cim = deleteCimProcessFromSourceXmi(services, context, cim, addedProcessId);
    services.transformations().cimToPim(context.user(), cim.id());
    SynchronizationResult pending = TransformationService.consumeLastSynchronization();
    assertEquals(SynchronizationStatus.CONFLICTS, pending.status());
    TransformationSynchronizationCoordinator coordinator =
        new TransformationSynchronizationCoordinator(services.store(), services.models());
    coordinator.resolveAll(
        context.user(),
        context.project().id(),
        pending.sessionId(),
        pending.conflictDetails().stream()
            .collect(
                java.util.stream.Collectors.toMap(
                    conflict -> conflict.conflictId(), conflict -> ConflictResolution.KEEP_USER)));
    try {
      pim =
          coordinator.finalizeSession(context.user(), context.project().id(), pending.sessionId());
    } catch (TransformationValidationException ex) {
      throw new AssertionError("PIM finalization issues: " + ex.issues(), ex);
    }
    assertTrue(
        services
            .models()
            .validateGeneratedXmi(ModelLevel.PIM, services.models().sourceXmi(pim).orElseThrow())
            .valid());
    assertEquals(initialPimTasks, pim.modelJson().path("manualBacklog").size());
    assertEquals(
        initialPimWarnings,
        warningSignatures(services.models().validate(context.user(), ModelLevel.PIM, pim.id())),
        "Keeping a locally refined workflow must not alter unrelated PIM warnings.");

    ModelRecord psm = services.transformations().pimToPsm(context.user(), pim.id());
    SynchronizationResult psmResult = TransformationService.consumeLastSynchronization();
    assertEquals(
        SynchronizationStatus.APPLIED,
        psmResult.status(),
        () ->
            "An untouched PSM must not conflict after the upstream keep-user merge: "
                + psmResult.conflictDetails());
    assertEquals(initialPsmTasks, psm.modelJson().path("manualBacklog").size());
    Set<String> regeneratedPsmWarnings =
        warningSignatures(services.models().validate(context.user(), ModelLevel.PSM, psm.id()));
    assertTrue(
        regeneratedPsmWarnings.containsAll(initialPsmWarnings),
        "Regeneration must not lose warnings for the unchanged PSM subgraph; the retained workflow"
            + " may add warnings for newly generated resources.");
    assertTrue(
        services
            .models()
            .validateGeneratedXmi(ModelLevel.PSM, services.models().sourceXmi(psm).orElseThrow())
            .valid(),
        "The full CIM/PIM synchronization journey must leave a valid PSM after generated choices.");
  }

  private Set<String> warningSignatures(ModelService.ValidationResult result) {
    Set<String> signatures = new HashSet<>();
    result.issues().stream()
        .filter(issue -> "WARNING".equals(issue.severity()))
        .forEach(
            issue ->
                signatures.add(
                    issue.constraint() + "::" + issue.elementId() + "::" + issue.message()));
    return signatures;
  }

  /** Catalog X-09: an upstream CIM edit completes CIM→PIM before PIM→PSM consumes it. */
  @Test
  void x09PropagatesUpstreamEditThroughBothValidatedBoundaries() throws Exception {
    PlatformTestFixtures.ServiceStack services =
        PlatformTestFixtures.createServicesWithTransformations(tempDir);
    PlatformTestFixtures.AuthenticatedContext context =
        PlatformTestFixtures.registerOwner(services, "x09@example.com", "X09", "Combined");
    ModelRecord cim = createClimateCim(services, context, "x09-cim");
    ModelRecord pim = services.transformations().cimToPim(context.user(), cim.id());
    assertEquals(
        SynchronizationStatus.APPLIED, TransformationService.consumeLastSynchronization().status());
    services.transformations().pimToPsm(context.user(), pim.id());
    assertEquals(
        SynchronizationStatus.APPLIED, TransformationService.consumeLastSynchronization().status());

    Resource cimXmi = loadSourceXmi(services, ModelLevel.CIM, cim, "x09-edit-cim");
    EObject cimRoot = cimXmi.getContents().get(0);
    cimRoot.eSet(
        cimRoot.eClass().getEStructuralFeature("name"), "X09 Updated Climate Relief Model");
    cim = saveChangedSourceXmi(services, context, cim, ModelLevel.CIM, cimXmi, "x09 upstream edit");

    ModelRecord updatedPim = services.transformations().cimToPim(context.user(), cim.id());
    SynchronizationResult pimResult = TransformationService.consumeLastSynchronization();
    assertEquals(SynchronizationStatus.APPLIED, pimResult.status());
    assertTrue(
        services
            .models()
            .validateGeneratedXmi(
                ModelLevel.PIM, services.models().sourceXmi(updatedPim).orElseThrow())
            .valid());

    ModelRecord updatedPsm = services.transformations().pimToPsm(context.user(), updatedPim.id());
    SynchronizationResult psmResult = TransformationService.consumeLastSynchronization();
    assertEquals(SynchronizationStatus.APPLIED, psmResult.status());
    assertTrue(
        services
            .models()
            .validateGeneratedXmi(
                ModelLevel.PSM, services.models().sourceXmi(updatedPsm).orElseThrow())
            .valid());
  }

  /** Catalog X-10: PIM and PSM refinements survive independent regeneration cycles. */
  @Test
  void x10PreservesIndependentPimAndPsmRefinementsAcrossCycles() throws Exception {
    PlatformTestFixtures.ServiceStack services =
        PlatformTestFixtures.createServicesWithTransformations(tempDir);
    PlatformTestFixtures.AuthenticatedContext context =
        PlatformTestFixtures.registerOwner(services, "x10@example.com", "X10", "Combined");
    ModelRecord cim = createClimateCim(services, context, "x10-cim");
    ModelRecord pim = services.transformations().cimToPim(context.user(), cim.id());
    TransformationService.consumeLastSynchronization();
    ModelRecord psm = services.transformations().pimToPsm(context.user(), pim.id());
    TransformationService.consumeLastSynchronization();

    ObjectNode pimRefined = (ObjectNode) pim.modelJson().deepCopy();
    pimRefined.put("description", "independent PIM refinement");
    pim =
        services
            .models()
            .update(
                context.user(), ModelLevel.PIM, pim.id(), pim.name(), pimRefined, pim.revision());
    ObjectNode psmRefined = (ObjectNode) psm.modelJson().deepCopy();
    psmRefined.put("summary", "independent PSM refinement");
    psm =
        services
            .models()
            .update(
                context.user(), ModelLevel.PSM, psm.id(), psm.name(), psmRefined, psm.revision());

    ObjectNode sourceChanged = (ObjectNode) cim.modelJson().deepCopy();
    sourceChanged.put("summary", "x10 third-cycle upstream revision");
    cim =
        services
            .models()
            .update(
                context.user(),
                ModelLevel.CIM,
                cim.id(),
                cim.name(),
                sourceChanged,
                cim.revision());

    pim = services.transformations().cimToPim(context.user(), cim.id());
    assertEquals(
        SynchronizationStatus.APPLIED, TransformationService.consumeLastSynchronization().status());
    assertEquals("independent PIM refinement", pim.modelJson().path("description").asText());
    psm = services.transformations().pimToPsm(context.user(), pim.id());
    assertEquals(
        SynchronizationStatus.APPLIED, TransformationService.consumeLastSynchronization().status());
    assertEquals("independent PSM refinement", psm.modelJson().path("summary").asText());
  }

  @Test
  void savedPsmStepFunctionEditThenDeletedPimWorkflowCreatesOneResolvableConflict()
      throws Exception {
    PlatformTestFixtures.ServiceStack services =
        PlatformTestFixtures.createServicesWithTransformations(tempDir);
    PlatformTestFixtures.AuthenticatedContext context =
        PlatformTestFixtures.registerOwner(services, "psm-delete@example.com", "PSM", "Delete");
    ModelRecord cim = createClimateCim(services, context, "psm-delete-cim");
    ModelRecord pim = services.transformations().cimToPim(context.user(), cim.id());
    TransformationService.consumeLastSynchronization();
    ModelRecord psm = services.transformations().pimToPsm(context.user(), pim.id());
    TransformationService.consumeLastSynchronization();

    String processId = "psm-delete-process";
    cim = cloneCimProcessIntoSourceXmi(services, context, cim, "proc-appeal-lifecycle", processId);
    pim = services.transformations().cimToPim(context.user(), cim.id());
    assertEquals(
        SynchronizationStatus.APPLIED, TransformationService.consumeLastSynchronization().status());
    psm = services.transformations().pimToPsm(context.user(), pim.id());
    assertEquals(
        SynchronizationStatus.APPLIED, TransformationService.consumeLastSynchronization().status());

    ObjectNode workflow = findGeneratedElement(pim.modelJson(), "Workflow", processId);
    assertTrue(workflow != null, "The added process must generate a PIM workflow.");
    String workflowId = workflow.path("id").asText();
    ObjectNode refinedPsm = (ObjectNode) psm.modelJson().deepCopy();
    ObjectNode stateMachine =
        findGeneratedElement(refinedPsm, "StepFunctionStateMachine", workflowId);
    assertTrue(stateMachine != null, "The workflow must generate a Step Function state machine.");
    stateMachine.put("name", "User-refined state machine");
    psm =
        services
            .models()
            .update(
                context.user(), ModelLevel.PSM, psm.id(), psm.name(), refinedPsm, psm.revision());

    Resource pimXmi = loadSourceXmi(services, ModelLevel.PIM, pim, "delete-pim-workflow");
    EObject workflowObject = findById(pimXmi, workflowId);
    assertTrue(workflowObject != null, "Expected generated PIM workflow " + workflowId);
    EcoreUtil.delete(workflowObject, true);
    pim = saveChangedSourceXmi(services, context, pim, ModelLevel.PIM, pimXmi, "delete workflow");

    ModelRecord unchanged = services.transformations().pimToPsm(context.user(), pim.id());
    SynchronizationResult pending = TransformationService.consumeLastSynchronization();
    assertEquals(psm.id(), unchanged.id(), "A conflict must not replace the saved PSM.");
    assertEquals(SynchronizationStatus.CONFLICTS, pending.status());
    assertEquals(
        1,
        pending.conflictDetails().size(),
        "A deleted state machine and its nested generated helpers are one logical conflict.");
    assertEquals("DELETE", pending.conflictDetails().get(0).differenceKind());

    // The API returns a pending synchronization session rather than failing the generation or
    // publishing the dependent EMF diagnostics to the issue board. Resolution is intentionally
    // tested separately: it operates on the same durable session without mutating this PSM.
    TransformationSynchronizationCoordinator coordinator =
        new TransformationSynchronizationCoordinator(services.store(), services.models());
    coordinator.cancel(context.user(), context.project().id(), pending.sessionId());
  }

  private ModelRecord cloneCimProcessIntoSourceXmi(
      PlatformTestFixtures.ServiceStack services,
      PlatformTestFixtures.AuthenticatedContext context,
      ModelRecord cim,
      String sourceProcessId,
      String copiedProcessId)
      throws Exception {
    Resource resource = loadSourceXmi(services, ModelLevel.CIM, cim, "clone-cim-process");
    EObject original = findById(resource, sourceProcessId);
    assertTrue(original != null, "Expected source process " + sourceProcessId);
    EObject copy = EcoreUtil.copy(original);
    replaceIds(copy, copiedProcessId);
    @SuppressWarnings("unchecked")
    List<EObject> processes =
        (List<EObject>)
            resource
                .getContents()
                .get(0)
                .eGet(resource.getContents().get(0).eClass().getEStructuralFeature("processes"));
    processes.add(copy);
    return saveChangedSourceXmi(services, context, cim, ModelLevel.CIM, resource, "add process");
  }

  private ModelRecord deleteCimProcessFromSourceXmi(
      PlatformTestFixtures.ServiceStack services,
      PlatformTestFixtures.AuthenticatedContext context,
      ModelRecord cim,
      String processId)
      throws Exception {
    Resource resource = loadSourceXmi(services, ModelLevel.CIM, cim, "delete-cim-process");
    EObject process = findById(resource, processId);
    assertTrue(process != null, "Expected added process " + processId);
    EcoreUtil.delete(process, true);
    return saveChangedSourceXmi(services, context, cim, ModelLevel.CIM, resource, "delete process");
  }

  private ModelRecord saveChangedSourceXmi(
      PlatformTestFixtures.ServiceStack services,
      PlatformTestFixtures.AuthenticatedContext context,
      ModelRecord model,
      ModelLevel level,
      Resource resource,
      String summary)
      throws Exception {
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    resource.save(output, Map.of());
    // This mirrors the frontend: persist both its editable JSON graph and the XMI sidecar from
    // the same saved model. Keeping the old JSON while replacing only XMI can make a subsequent
    // synchronization combine two different PIM revisions.
    ObjectNode changed =
        (ObjectNode)
            services
                .models()
                .importModel(
                    level, "changed-" + level.apiName() + ".xmi", output.toByteArray(), "xmi")
                .modelJson()
                .deepCopy();
    changed.put("summary", summary);
    ModelRecord updated =
        services
            .models()
            .update(context.user(), level, model.id(), model.name(), changed, model.revision());
    services
        .store()
        .writeBytesAtomically(
            Path.of(
                "projects",
                context.project().id(),
                "models",
                level.apiName(),
                updated.id() + ".xmi"),
            output.toByteArray());
    return updated;
  }

  private Resource loadSourceXmi(
      PlatformTestFixtures.ServiceStack services,
      ModelLevel level,
      ModelRecord model,
      String purpose) {
    return new XmiModelImportService(services.store().objectMapper())
        .loadResource(level, services.models().sourceXmi(model).orElseThrow(), purpose);
  }

  private void replaceIds(EObject root, String rootId) {
    setId(root, rootId);
    int index = 0;
    for (TreeIterator<EObject> iterator = root.eAllContents(); iterator.hasNext(); ) {
      setId(iterator.next(), rootId + "-" + (++index));
    }
  }

  private void setId(EObject object, String id) {
    var idFeature = object.eClass().getEStructuralFeature("id");
    if (idFeature != null) {
      object.eSet(idFeature, id);
    }
  }

  private EObject findById(Resource resource, String id) {
    for (EObject root : resource.getContents()) {
      if (id.equals(EcoreUtil.getID(root))) return root;
      for (TreeIterator<EObject> iterator = root.eAllContents(); iterator.hasNext(); ) {
        EObject candidate = iterator.next();
        if (id.equals(EcoreUtil.getID(candidate))) return candidate;
      }
    }
    return null;
  }

  private EObject findByGeneratedFrom(Resource resource, String sourceId, String className) {
    for (EObject root : resource.getContents()) {
      EObject match = generatedFromMatch(root, sourceId, className);
      if (match != null) return match;
      for (TreeIterator<EObject> iterator = root.eAllContents(); iterator.hasNext(); ) {
        EObject candidate = iterator.next();
        match = generatedFromMatch(candidate, sourceId, className);
        if (match != null) return match;
      }
    }
    return null;
  }

  private EObject generatedFromMatch(EObject candidate, String sourceId, String className) {
    var generatedFrom = candidate.eClass().getEStructuralFeature("generatedFrom");
    if (className.equals(candidate.eClass().getName())
        && generatedFrom != null
        && sourceId.equals(candidate.eGet(generatedFrom))) return candidate;
    return null;
  }

  @SuppressWarnings("unchecked")
  private List<EObject> values(EObject owner, String featureName) {
    return (List<EObject>) owner.eGet(owner.eClass().getEStructuralFeature(featureName));
  }

  private EObject reference(EObject owner, String featureName) {
    return (EObject) owner.eGet(owner.eClass().getEStructuralFeature(featureName));
  }

  private ObjectNode findGeneratedElement(JsonNode node, String eClass, String generatedFrom) {
    if (node instanceof ObjectNode object
        && eClass.equals(object.path("eClass").asText())
        && generatedFrom.equals(object.path("generatedFrom").asText())) return object;
    for (JsonNode child : node) {
      ObjectNode found = findGeneratedElement(child, eClass, generatedFrom);
      if (found != null) return found;
    }
    return null;
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

  private ModelRecord moveFirstGraphElement(
      PlatformTestFixtures.ServiceStack services,
      PlatformTestFixtures.AuthenticatedContext context,
      ModelLevel level,
      ModelRecord model) {
    JsonNode elements = model.modelJson().path("graph").path("elements");
    for (int index = 0; index < elements.size(); index++) {
      JsonNode element = elements.path(index);
      if (!element.hasNonNull("id")) {
        continue;
      }
      double x = element.path("x").asDouble(0.0d) + 7.0d;
      return services
          .models()
          .patch(
              context.user(),
              level,
              model.id(),
              model.name(),
              List.of(
                  new ModelService.ModelPatchOperation(
                      "add",
                      "/graph/elements/" + index + "/x",
                      services.store().objectMapper().getNodeFactory().numberNode(x))),
              model.revision());
    }
    throw new AssertionError("Expected at least one positioned graph element in " + level);
  }

  private Set<String> taskIds(JsonNode model) {
    Set<String> ids = new HashSet<>();
    model.path("manualBacklog").forEach(task -> ids.add(task.path("id").asText()));
    return ids;
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
