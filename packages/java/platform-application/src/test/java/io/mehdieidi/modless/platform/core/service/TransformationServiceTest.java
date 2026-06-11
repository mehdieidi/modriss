package io.mehdieidi.modless.platform.core.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.mehdieidi.modless.platform.core.model.ArtifactRecord;
import io.mehdieidi.modless.platform.core.model.ModelLevel;
import io.mehdieidi.modless.platform.core.model.ModelRecord;
import io.mehdieidi.modless.platform.core.model.ProjectRecord;
import io.mehdieidi.modless.platform.core.model.UserRecord;
import io.mehdieidi.modless.platform.core.repository.TestPlatformStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * End-to-end regression tests for platform transformations backed by the MDE runners.
 */
class TransformationServiceTest {

    /**
     * Isolated repository root used by the JSON store for each test.
     */
    @TempDir
    Path tempDir;

    /**
     * Verifies that CIM-to-PIM invokes the formal ETL pipeline and returns PIM semantics rather
     * than relabeled CIM JSON.
     *
     * @throws Exception when fixture import, persistence, or transformation fails
     */
    @Test
    void cimToPimCreatesPimSemanticModelInsteadOfRelabelingCimJson() throws Exception {
        TestPlatformStore store = new TestPlatformStore(tempDir);
        store.initialize();
        AuthService authService = new AuthService(store, Duration.ofHours(1));
        ProjectService projectService = new ProjectService(store, authService);
        ModelService modelService = new ModelService(store, projectService);
        ArtifactService artifactService = new ArtifactService(store, projectService);
        TransformationService transformations = new TransformationService(store, modelService,
                artifactService);
        UserRecord user = authService.register("owner@example.com", "password123", "Owner").user();
        ProjectRecord project = projectService.create(user, "Climate", "");

        byte[] sample = Files.readAllBytes(Path.of("..", "..", "..", "mde", "samples",
                "cim.xmi").normalize());
        ModelService.ImportResult imported = modelService.importModel(ModelLevel.CIM,
                "cim.xmi", sample, "xmi");
        ModelRecord cim = modelService.create(user, ModelLevel.CIM, project.id(), "climate-cim",
                imported.modelJson());
        modelService.update(user, ModelLevel.CIM, cim.id(), cim.name(), cim.modelJson());

        ModelRecord pim = transformations.cimToPim(user, cim.id());

        assertEquals(ModelLevel.PIM, pim.level());
        assertEquals("PIM", pim.modelJson().path("modelLevel").asText());
        assertEquals("PIMModel", pim.modelJson().path("eClass").asText());
        assertFalse(pim.modelJson().path("services").isEmpty());
        assertFalse(pim.modelJson().path("functions").isEmpty());
        assertFalse(pim.modelJson().path("apis").path(0).path("routes").isEmpty());
        assertFalse(pim.modelJson().path("eventTypes").isEmpty());
        assertFalse(pim.modelJson().path("dataStores").isEmpty());
        assertEquals("GENERATED_BY_ETL", pim.modelJson().path("transformationStatus").asText());
        assertTrue(pim.modelJson().path("commands").isMissingNode(),
                "Generated PIM must not retain CIM root containments.");
        assertEquals(pim.modelJson().path("readiness").path("manualDecisions").size(),
                pim.modelJson().path("manualBacklog").size(),
                "Only explicit ETL manual decisions should be mirrored into the frontend backlog.");
        assertFalse(pim.modelJson().path("readiness").path("findings").isEmpty(),
                "Readiness findings should remain on the readiness assessment.");
        assertTrue(pim.modelJson().path("graph").path("elements").findValuesAsText("eClass")
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
        TestPlatformStore store = new TestPlatformStore(tempDir);
        store.initialize();
        AuthService authService = new AuthService(store, Duration.ofHours(1));
        ProjectService projectService = new ProjectService(store, authService);
        ModelService modelService = new ModelService(store, projectService);
        ArtifactService artifactService = new ArtifactService(store, projectService);
        TransformationService transformations = new TransformationService(store, modelService,
                artifactService);
        UserRecord user = authService.register("owner@example.com", "password123", "Owner").user();
        ProjectRecord project = projectService.create(user, "Climate", "");

        byte[] sample = Files.readAllBytes(Path.of("..", "..", "..", "mde", "samples",
                "cim.xmi").normalize());
        ModelService.ImportResult imported = modelService.importModel(ModelLevel.CIM,
                "cim.xmi", sample, "xmi");
        ModelRecord cim = modelService.create(user, ModelLevel.CIM, project.id(), "climate-cim",
                imported.modelJson());

        ModelRecord pim = transformations.cimToPim(user, cim.id());
        int generatedManualTasks = pim.modelJson().path("manualBacklog").size();
        Set<String> manualDecisionIds = new HashSet<>();
        pim.modelJson().path("readiness").path("manualDecisions").forEach(decision ->
                manualDecisionIds.add(decision.path("id").asText()));
        long graphManualDecisions = pim.modelJson().path("graph").path("elements")
                .findValuesAsText("eClass").stream()
                .filter("ManualDecision"::equals)
                .count();

        ModelRecord saved = modelService.update(user, ModelLevel.PIM, pim.id(), pim.name(),
                pim.modelJson(), pim.revision());
        ModelService.ValidationResult validation = modelService.validate(user, ModelLevel.PIM,
                saved.id());
        List<ModelService.ValidationIssue> readinessIssues = validation.issues().stream()
                .filter(issue -> issue.constraint().startsWith("PIM-READY-"))
                .toList();

        assertEquals(40, generatedManualTasks,
                "Climate sample should expose the generated manual backlog once.");
        assertEquals(generatedManualTasks, manualDecisionIds.size(),
                "Generated ManualDecision IDs must be unique so the issue board does not "
                        + "deduplicate visible manual tasks.");
        assertEquals(generatedManualTasks, graphManualDecisions,
                "Generated graph elements must expose every ManualDecision immediately.");
        assertTrue(readinessIssues.isEmpty(),
                "Validating the stored generated PIM must not add readiness issues that look "
                        + "like extra manual tasks: " + readinessIssues);
    }

    /**
     * Verifies that PIM-to-PSM invokes the formal ETL pipeline and preserves stack, stage,
     * resource, and relationship information needed by the frontend.
     *
     * @throws Exception when fixture import, persistence, transformation, or validation fails
     */
    @Test
    void pimToPsmRunsFormalEtlAndPreservesGeneratedRelationships() throws Exception {
        TestPlatformStore store = new TestPlatformStore(tempDir);
        store.initialize();
        AuthService authService = new AuthService(store, Duration.ofHours(1));
        ProjectService projectService = new ProjectService(store, authService);
        ModelService modelService = new ModelService(store, projectService);
        ArtifactService artifactService = new ArtifactService(store, projectService);
        TransformationService transformations = new TransformationService(store, modelService,
                artifactService);
        UserRecord user = authService.register("owner@example.com", "password123", "Owner").user();
        ProjectRecord project = projectService.create(user, "Climate", "");

        byte[] sample = Files.readAllBytes(Path.of("..", "..", "..", "mde", "samples",
                "cim.xmi").normalize());
        ModelService.ImportResult imported = modelService.importModel(ModelLevel.CIM,
                "cim.xmi", sample, "xmi");
        ModelRecord cim = modelService.create(user, ModelLevel.CIM, project.id(), "climate-cim",
                imported.modelJson());
        ModelRecord pim = transformations.cimToPim(user, cim.id());
        modelService.update(user, ModelLevel.PIM, pim.id(), pim.name(), pim.modelJson());

        ModelRecord psm = transformations.pimToPsm(user, pim.id());

        assertEquals(ModelLevel.PSM, psm.level());
        assertEquals("PSM", psm.modelJson().path("modelLevel").asText());
        assertEquals("AwsPsmModel", psm.modelJson().path("eClass").asText());
        assertEquals("GENERATED_BY_ETL",
                psm.modelJson().path("transformationStatus").asText());
        assertTrue(psm.modelJson().path("validationIssues").isMissingNode()
                || psm.modelJson().path("validationIssues").isEmpty());
        assertTrue(psm.modelJson().path("stacks").findValues("resources").stream()
                        .anyMatch(resources -> resources.isArray() && !resources.isEmpty()),
                "Generated PSM JSON should preserve stack-contained resources.");
        assertTrue(psm.modelJson().path("stages").findValues("deploysStacks").stream()
                        .anyMatch(stacks -> stacks.isArray() && !stacks.isEmpty()),
                "Generated PSM JSON should preserve stage-to-stack deployment references.");
        assertFalse(psm.modelJson().path("relationshipViews").isEmpty(),
                "Generated PSM should include relationship view elements for integrations.");
        assertEquals(psm.modelJson().path("manualBacklog").size(),
                psm.modelJson().path("graph").path("manualBacklog").size(),
                "Root and graph manual backlog counts should stay in sync.");
        assertTrue(psm.modelJson().path("graph").path("elements").findValuesAsText("eClass")
                .contains("AwsLambdaFunction"));
        assertTrue(psm.modelJson().path("graph").path("elements").size() > 10,
                "Generated PSM graph should expose enough elements for frontend views.");
        assertFalse(psm.modelJson().path("graph").path("relationships").isEmpty(),
                "Imported AWS PSM graph should include reference and relationship edges.");
        var relationshipKinds = psm.modelJson().path("graph").path("relationships")
                .findValuesAsText("kind");
        assertFalse(relationshipKinds.stream().anyMatch(kind -> kind.endsWith("_VIEW")),
                "Generated PSM relationship views should be rendered as canonical edge kinds.");
        assertTrue(relationshipKinds.contains("EVENT_FLOW"),
                "Generated event/messaging shortcuts should be visible in PSM views.");
        assertTrue(relationshipKinds.contains("TARGETS"),
                "Generated EventBridge targets should be visible in PSM views.");
        assertTrue(psm.modelJson().path("commands").isMissingNode(),
                "Generated PSM must not retain PIM/CIM root containments.");
        ModelService.ValidationResult validation = modelService.validate(ModelLevel.PSM,
                psm.modelJson());
        assertTrue(validation.issues().stream().noneMatch(issue ->
                        "ProductionLogGroupShouldUseKms".equals(issue.constraint())
                                || "ApiLambdaPermissionRecommended".equals(issue.constraint())),
                "Generated PSM should satisfy production log group KMS and API Lambda permission links: "
                        + validation.issues());
        assertTrue(validation.issues().stream().noneMatch(issue ->
                        "StackResourcesExist".equals(issue.constraint())
                                || "StageDeploysAtLeastOneStack".equals(issue.constraint())
                                || "StackHasResources".equals(issue.constraint())
                                || "DeployableStackHasResources".equals(issue.constraint())),
                "PSM validation should not lose stack resources or stage deployment references: "
                        + validation.issues());
    }

    /**
     * Ensures artifact generation uses the formal EGX generator and stores artifact metadata
     * without embedding full file contents in model JSON.
     *
     * @throws Exception when fixture import, persistence, transformation, or generation fails
     */
    @Test
    void psmToArtifactRunsFormalEgxGeneratorInsteadOfScaffold() throws Exception {
        TestPlatformStore store = new TestPlatformStore(tempDir);
        store.initialize();
        AuthService authService = new AuthService(store, Duration.ofHours(1));
        ProjectService projectService = new ProjectService(store, authService);
        ModelService modelService = new ModelService(store, projectService);
        ArtifactService artifactService = new ArtifactService(store, projectService);
        TransformationService transformations = new TransformationService(store, modelService,
                artifactService);
        UserRecord user = authService.register("owner@example.com", "password123", "Owner").user();
        ProjectRecord project = projectService.create(user, "Climate", "");

        byte[] sample = Files.readAllBytes(Path.of("..", "..", "..", "mde", "samples",
                "cim.xmi").normalize());
        ModelService.ImportResult imported = modelService.importModel(ModelLevel.CIM,
                "cim.xmi", sample, "xmi");
        ModelRecord cim = modelService.create(user, ModelLevel.CIM, project.id(), "climate-cim",
                imported.modelJson());
        ModelRecord pim = transformations.cimToPim(user, cim.id());
        ModelRecord psm = transformations.pimToPsm(user, pim.id());
        modelService.update(user, ModelLevel.PSM, psm.id(), psm.name(), psm.modelJson());
        modelService.patch(user, ModelLevel.PSM, psm.id(), psm.name(), java.util.List.of(
                new ModelService.ModelPatchOperation("replace", "/summary",
                        store.objectMapper().getNodeFactory().textNode(
                                "Saved before artifact generation."))));

        ArtifactRecord artifact = transformations.psmToArtifact(user, psm.id());

        assertFalse(artifact.files().isEmpty());
        assertTrue(artifact.modelJson().path("files").isMissingNode(),
                "Artifact metadata must not duplicate generated file contents.");
        assertEquals(artifact.files().size(), artifact.modelJson().path("fileCount").asInt());
        assertEquals(artifact.id(), artifactService.get(user, artifact.id()).id());
        assertFalse(artifactService.list(user, project.id()).isEmpty());
        assertTrue(artifact.files().containsKey("generated/reports/generation-report.md"));
        assertTrue(artifact.files().keySet().stream().anyMatch(path -> path.startsWith("src/")),
                "Formal generation should produce source files, not only a placeholder scaffold.");
        assertTrue(artifact.files().keySet().stream()
                        .anyMatch(path -> path.startsWith("src/functions/")
                                && path.endsWith("/handler.go")),
                "Formal generation should preserve generated PSM XMI and emit Lambda handlers.");
        String samTemplate = artifact.files().entrySet().stream()
                .filter(entry -> entry.getKey().startsWith("template-")
                        && entry.getKey().endsWith(".yaml"))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElseThrow();
        assertTrue(samTemplate.length() < 500_000,
                "SAM template must not contain runaway EGL indentation.");
        assertFalse(samTemplate.contains("Resources: {}"),
                "SAM template must be generated from AWS PSM resources.");
    }
}
