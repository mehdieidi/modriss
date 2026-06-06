package io.mehdieidi.modless.platform.core.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.mehdieidi.modless.mde.etl.CimToPimDefaults;
import io.mehdieidi.modless.mde.etl.EpsilonEtlExecutor;
import io.mehdieidi.modless.mde.etl.EtlExecutionException;
import io.mehdieidi.modless.mde.etl.EtlExecutionReport;
import io.mehdieidi.modless.mde.etl.EtlExecutionStatus;
import io.mehdieidi.modless.mde.etl.PimToAwsPsmDefaults;
import io.mehdieidi.modless.mde.generation.AwsPsmToArtifactsDefaults;
import io.mehdieidi.modless.mde.generation.EgxGenerationException;
import io.mehdieidi.modless.mde.generation.EgxGenerationReport;
import io.mehdieidi.modless.mde.generation.EpsilonEgxGenerator;
import io.mehdieidi.modless.mde.generation.GenerationStatus;
import io.mehdieidi.modless.platform.core.PlatformException;
import io.mehdieidi.modless.platform.core.model.ArtifactRecord;
import io.mehdieidi.modless.platform.core.model.ModelLevel;
import io.mehdieidi.modless.platform.core.model.ModelRecord;
import io.mehdieidi.modless.platform.core.model.UserRecord;
import io.mehdieidi.modless.platform.core.repository.JsonFileStore;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * Coordinates formal MDE transformations and artifact generation between stored platform models.
 */
public final class TransformationService {

    /**
     * File repository used for hashing and serialization helpers.
     */
    private final JsonFileStore store;
    /**
     * Model service used to load and persist generated models.
     */
    private final ModelService modelService;
    /**
     * Artifact service used to persist generated artifact bundles.
     */
    private final ArtifactService artifactService;
    /**
     * Runtime limits used during ETL and EGX execution.
     */
    private final MdeRuntimeOptions runtimeOptions;
    /**
     * Runtime asset path resolver.
     */
    private final MdeRuntimePaths mdePaths;
    /**
     * Metamodel resolver shared with XMI import/export.
     */
    private final MetamodelResolver metamodelResolver;
    /**
     * Per-model lock service for source model operations.
     */
    private final ModelLockService modelLocks;
    /**
     * XMI import/export bridge for model JSON and EMF resources.
     */
    private final XmiModelImportService xmiModelIo;
    /**
     * ETL executor used for model-to-model transformations.
     */
    private final EpsilonEtlExecutor etlExecutor;
    /**
     * EGX generator used for PSM-to-artifact generation.
     */
    private final EpsilonEgxGenerator artifactGenerator;

    /**
     * Creates a transformation service using default runtime options.
     *
     * @param store           backing JSON repository
     * @param modelService    model service
     * @param artifactService artifact service
     */
    public TransformationService(JsonFileStore store, ModelService modelService,
            ArtifactService artifactService) {
        this(store, modelService, artifactService, MdeRuntimeOptions.defaults(),
                modelService.modelLocks());
    }

    /**
     * Creates a transformation service with explicit runtime options and locks.
     *
     * @param store           backing JSON repository
     * @param modelService    model service
     * @param artifactService artifact service
     * @param runtimeOptions  runtime limits and asset roots
     * @param modelLocks      per-model lock service
     */
    public TransformationService(JsonFileStore store, ModelService modelService,
            ArtifactService artifactService, MdeRuntimeOptions runtimeOptions,
            ModelLockService modelLocks) {
        this(store, modelService, artifactService, runtimeOptions,
                new MdeRuntimePaths(runtimeOptions), null, modelLocks);
    }

    /**
     * Creates a transformation service with injectable path and metamodel resolvers for tests or
     * embedded runtimes.
     *
     * @param store             backing JSON repository
     * @param modelService      model service
     * @param artifactService   artifact service
     * @param runtimeOptions    runtime limits and asset roots
     * @param mdePaths          runtime asset path resolver
     * @param metamodelResolver metamodel resolver
     * @param modelLocks        per-model lock service
     */
    public TransformationService(JsonFileStore store, ModelService modelService,
            ArtifactService artifactService, MdeRuntimeOptions runtimeOptions,
            MdeRuntimePaths mdePaths, MetamodelResolver metamodelResolver,
            ModelLockService modelLocks) {
        this.store = store;
        this.modelService = modelService;
        this.artifactService = artifactService;
        this.runtimeOptions = runtimeOptions == null ? MdeRuntimeOptions.defaults()
                : runtimeOptions;
        this.mdePaths = mdePaths == null ? new MdeRuntimePaths(this.runtimeOptions) : mdePaths;
        this.metamodelResolver = metamodelResolver == null
                ? new FileMetamodelResolver(this.mdePaths) : metamodelResolver;
        this.modelLocks = modelLocks == null ? modelService.modelLocks() : modelLocks;
        this.xmiModelIo = new XmiModelImportService(store.objectMapper(),
                this.metamodelResolver);
        this.etlExecutor = new EpsilonEtlExecutor(this.runtimeOptions.executionTimeout(),
                this.runtimeOptions.maxCapturedOutputBytes());
        this.artifactGenerator = new EpsilonEgxGenerator(this.runtimeOptions.executionTimeout(),
                this.runtimeOptions.maxCapturedOutputBytes());
    }

    /**
     * Generates a PIM model from a CIM source without a revision precondition.
     *
     * @param user          requesting user
     * @param sourceModelId source CIM model id
     * @return generated PIM model
     */
    public ModelRecord cimToPim(UserRecord user, String sourceModelId) {
        return cimToPim(user, sourceModelId, null);
    }

    /**
     * Generates a PIM model from a CIM source.
     *
     * @param user             requesting user
     * @param sourceModelId    source CIM model id
     * @param expectedRevision expected source revision, or {@code null}
     * @return generated PIM model
     */
    public ModelRecord cimToPim(UserRecord user, String sourceModelId, Long expectedRevision) {
        return modelLocks.withModelLock(sourceModelId, Duration.ofSeconds(30), () -> {
            ModelRecord source = modelService.getForTransformation(user, ModelLevel.CIM,
                    sourceModelId);
            requireSourceRevision(source, expectedRevision);
            GeneratedModel generated = formalCimToPimModel(source);
            generated.model().put("sourceModelId", source.id());
            generated.model().put("sourceModelRevision", source.revision());
            generated.model().put("sourceModelHash", sourceModelHash(source));
            mirrorReadinessToManualBacklog(generated.model());
            ModelRecord target = modelService.createGenerated(user, ModelLevel.PIM,
                    source.projectId(),
                    source.name() + "-pim", generated.model(), generated.sourceXmi());
            return target;
        });
    }

    /**
     * Generates an AWS PSM model from a PIM source without a revision precondition.
     *
     * @param user          requesting user
     * @param sourceModelId source PIM model id
     * @return generated PSM model
     */
    public ModelRecord pimToPsm(UserRecord user, String sourceModelId) {
        return pimToPsm(user, sourceModelId, null);
    }

    /**
     * Generates an AWS PSM model from a PIM source.
     *
     * @param user             requesting user
     * @param sourceModelId    source PIM model id
     * @param expectedRevision expected source revision, or {@code null}
     * @return generated PSM model
     */
    public ModelRecord pimToPsm(UserRecord user, String sourceModelId, Long expectedRevision) {
        return modelLocks.withModelLock(sourceModelId, Duration.ofSeconds(30), () -> {
            ModelRecord source = modelService.getForTransformation(user, ModelLevel.PIM,
                    sourceModelId);
            requireSourceRevision(source, expectedRevision);
            GeneratedModel generated = formalPimToPsmModel(source);
            generated.model().put("sourceModelId", source.id());
            generated.model().put("sourceModelRevision", source.revision());
            generated.model().put("sourceModelHash", sourceModelHash(source));
            ModelRecord target = modelService.createGenerated(user, ModelLevel.PSM,
                    source.projectId(),
                    source.name() + "-psm", generated.model(), generated.sourceXmi());
            return target;
        });
    }

    /**
     * Generates an artifact bundle from an AWS PSM source without a revision precondition.
     *
     * @param user          requesting user
     * @param sourceModelId source PSM model id
     * @return generated artifact bundle
     */
    public ArtifactRecord psmToArtifact(UserRecord user, String sourceModelId) {
        return psmToArtifact(user, sourceModelId, null);
    }

    /**
     * Generates an artifact bundle from an AWS PSM source.
     *
     * @param user             requesting user
     * @param sourceModelId    source PSM model id
     * @param expectedRevision expected source revision, or {@code null}
     * @return generated artifact bundle
     */
    public ArtifactRecord psmToArtifact(UserRecord user, String sourceModelId,
            Long expectedRevision) {
        return modelLocks.withModelLock(sourceModelId, Duration.ofSeconds(30), () -> {
            ModelRecord source = modelService.get(user, ModelLevel.PSM, sourceModelId);
            requireSourceRevision(source, expectedRevision);
            Map<String, String> files = formalPsmToArtifactFiles(source);
            return artifactService.create(user, source.projectId(), source.name() + "-artifact",
                    files);
        });
    }

    /**
     * Runs the formal CIM-to-PIM ETL module and imports the produced PIM XMI.
     *
     * @param source source CIM model
     * @return generated model JSON and source XMI bytes
     */
    private GeneratedModel formalCimToPimModel(ModelRecord source) {
        Path repositoryRoot = mdePaths.repositoryRoot();
        Path workDir = null;
        try {
            workDir = Files.createTempDirectory("modless-cim-to-pim-");
            Path cimXmi = workDir.resolve("source-cim.xmi");
            Path pimXmi = workDir.resolve("target-pim.xmi");
            byte[] sourceBytes = sourceCimXmi(source);
            requireExecutionInputBudget(sourceBytes, "CIM-to-PIM source model");
            Files.write(cimXmi, sourceBytes);

            EtlExecutionReport report = etlExecutor.execute(CimToPimDefaults.request(
                    repositoryRoot, cimXmi, pimXmi, true, false));
            if (report.status() != EtlExecutionStatus.SUCCEEDED) {
                throw new PlatformException(500, "CIM-to-PIM ETL failed: "
                        + summarizeDiagnostics(report));
            }
            byte[] targetBytes = Files.readAllBytes(pimXmi);
            JsonNode imported = xmiModelIo.importGeneratedModel(ModelLevel.PIM, targetBytes);
            if (!imported.isObject()) {
                throw new PlatformException(500, "CIM-to-PIM ETL did not produce a PIM model.");
            }
            ObjectNode target = (ObjectNode) imported;
            target.put("transformedFrom", ModelLevel.CIM.name());
            target.put("transformedFromModelId", source.id());
            markGeneratedTarget(target);
            return new GeneratedModel(target, targetBytes);
        } catch (PlatformException ex) {
            throw ex;
        } catch (EtlExecutionException ex) {
            throw new PlatformException(500, "CIM-to-PIM ETL failed: "
                    + summarizeDiagnostics(ex.getReport()));
        } catch (Exception ex) {
            throw new PlatformException(500, "CIM-to-PIM ETL could not run: "
                    + (ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage()));
        } finally {
            if (workDir != null && !Boolean.getBoolean("modless.keepEtlTemp")) {
                deleteQuietly(workDir);
            }
        }
    }

    /**
     * Runs the formal PIM-to-AWS-PSM ETL module and imports the produced PSM XMI.
     *
     * @param source source PIM model
     * @return generated model JSON and source XMI bytes
     */
    private GeneratedModel formalPimToPsmModel(ModelRecord source) {
        Path repositoryRoot = mdePaths.repositoryRoot();
        Path workDir = null;
        try {
            workDir = Files.createTempDirectory("modless-pim-to-psm-");
            Path pimXmi = workDir.resolve("source-pim.xmi");
            Path psmXmi = workDir.resolve("target-awspsm.xmi");
            byte[] sourceBytes = sourcePimXmi(source);
            requireExecutionInputBudget(sourceBytes, "PIM-to-PSM source model");
            Files.write(pimXmi, sourceBytes);

            EtlExecutionReport report = etlExecutor.execute(PimToAwsPsmDefaults.request(
                    repositoryRoot, pimXmi, psmXmi, true, false));
            if (report.status() != EtlExecutionStatus.SUCCEEDED) {
                throw new PlatformException(500, "PIM-to-PSM ETL failed: "
                        + summarizeDiagnostics(report));
            }
            byte[] targetBytes = Files.readAllBytes(psmXmi);
            JsonNode imported = xmiModelIo.importGeneratedModel(ModelLevel.PSM, targetBytes);
            if (!imported.isObject()) {
                throw new PlatformException(500, "PIM-to-PSM ETL did not produce a PSM model.");
            }
            ObjectNode target = (ObjectNode) imported;
            target.put("transformedFrom", ModelLevel.PIM.name());
            target.put("transformedFromModelId", source.id());
            markGeneratedTarget(target);
            mirrorReadinessToManualBacklog(target);
            return new GeneratedModel(target, targetBytes);
        } catch (PlatformException ex) {
            throw ex;
        } catch (EtlExecutionException ex) {
            throw new PlatformException(500, "PIM-to-PSM ETL failed: "
                    + summarizeDiagnostics(ex.getReport()));
        } catch (Exception ex) {
            throw new PlatformException(500, "PIM-to-PSM ETL could not run: "
                    + (ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage()));
        } finally {
            if (workDir != null && !Boolean.getBoolean("modless.keepEtlTemp")) {
                deleteQuietly(workDir);
            }
        }
    }

    /**
     * Runs the formal AWS PSM artifact generator and reads the generated files.
     *
     * @param source source PSM model
     * @return generated files keyed by artifact-relative path
     */
    private Map<String, String> formalPsmToArtifactFiles(ModelRecord source) {
        Path repositoryRoot = mdePaths.repositoryRoot();
        Path workDir = null;
        try {
            workDir = Files.createTempDirectory("modless-psm-to-artifact-");
            Path psmXmi = workDir.resolve("source-awspsm.xmi");
            Path outputDirectory = workDir.resolve("generated-artifacts");
            byte[] sourceBytes = sourcePsmXmi(source);
            requireExecutionInputBudget(sourceBytes, "PSM-to-artifact source model");
            Files.write(psmXmi, sourceBytes);

            EgxGenerationReport report = artifactGenerator.generate(
                    AwsPsmToArtifactsDefaults.request(
                            repositoryRoot, psmXmi, outputDirectory, true, true));
            if (report.status() != GenerationStatus.SUCCEEDED) {
                throw new PlatformException(500, "PSM-to-artifact generation failed: "
                        + summarizeDiagnostics(report));
            }
            Map<String, String> files = generatedFiles(outputDirectory);
            if (files.isEmpty()) {
                throw new PlatformException(500,
                        "PSM-to-artifact generation did not produce files.");
            }
            validateGeneratedArtifactCompleteness(source, files);
            return files;
        } catch (PlatformException ex) {
            throw ex;
        } catch (EgxGenerationException ex) {
            throw new PlatformException(500, "PSM-to-artifact generation failed: "
                    + summarizeDiagnostics(ex.getReport()));
        } catch (Exception ex) {
            throw new PlatformException(500, "PSM-to-artifact generation could not run: "
                    + (ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage()));
        } finally {
            if (workDir != null && !Boolean.getBoolean("modless.keepGenerationTemp")) {
                deleteQuietly(workDir);
            }
        }
    }

    /**
     * Returns the best available CIM XMI source for transformation.
     *
     * @param model source model
     * @return source XMI bytes
     */
    private byte[] sourceCimXmi(ModelRecord model) {
        return modelService.sourceXmi(model)
                .orElseGet(() -> xmiModelIo.exportModel(ModelLevel.CIM,
                        hydrateSemanticReferences(model.modelJson())));
    }

    /**
     * Returns a PIM source XMI pruned for transformation input.
     *
     * @param model source model
     * @return source XMI bytes
     */
    private byte[] sourcePimXmi(ModelRecord model) {
        byte[] sourceBytes = modelService.sourceXmi(model)
                .orElseGet(() -> xmiModelIo.exportModel(ModelLevel.PIM,
                        hydrateSemanticReferences(model.modelJson())));
        return xmiModelIo.pruneTransformationInput(ModelLevel.PIM, sourceBytes);
    }

    /**
     * Returns the best available PSM XMI source for artifact generation.
     *
     * @param model source model
     * @return source XMI bytes
     */
    private byte[] sourcePsmXmi(ModelRecord model) {
        return modelService.sourceXmi(model)
                .orElseGet(() -> xmiModelIo.exportModel(ModelLevel.PSM,
                        hydrateSemanticReferences(model.modelJson())));
    }

    /**
     * Summarizes the first ETL diagnostics for an exception message.
     *
     * @param report ETL report
     * @return compact diagnostic summary
     */
    private String summarizeDiagnostics(EtlExecutionReport report) {
        if (report == null || report.diagnostics().isEmpty()) {
            return "no diagnostics were reported";
        }
        return report.diagnostics().stream()
                .limit(3)
                .map(diagnostic -> diagnostic.phase() + " "
                        + diagnostic.file() + ":" + diagnostic.line() + ":"
                        + diagnostic.column() + " " + diagnostic.reason())
                .collect(java.util.stream.Collectors.joining("; "));
    }

    /**
     * Summarizes the first EGX diagnostics for an exception message.
     *
     * @param report EGX report
     * @return compact diagnostic summary
     */
    private String summarizeDiagnostics(EgxGenerationReport report) {
        if (report == null || report.diagnostics().isEmpty()) {
            return "no diagnostics were reported";
        }
        return report.diagnostics().stream()
                .limit(3)
                .map(diagnostic -> diagnostic.phase() + " "
                        + diagnostic.file() + ":" + diagnostic.line() + ":"
                        + diagnostic.column() + " " + diagnostic.reason())
                .collect(java.util.stream.Collectors.joining("; "));
    }

    /**
     * Marks imported transformation output as generated and clears stale validation state.
     *
     * @param target target model JSON
     */
    private void markGeneratedTarget(ObjectNode target) {
        target.put("transformationStatus", "GENERATED_BY_ETL");
        target.remove("validationIssues");
    }

    /**
     * Reads generated artifact files while enforcing configured count and size budgets.
     *
     * @param outputDirectory generator output directory
     * @return generated files keyed by artifact-relative path
     * @throws Exception when files cannot be read
     */
    private Map<String, String> generatedFiles(Path outputDirectory) throws Exception {
        Map<String, String> files = new LinkedHashMap<>();
        long totalBytes = 0;
        try (Stream<Path> paths = Files.walk(outputDirectory)) {
            for (Path path : paths.filter(Files::isRegularFile).sorted().toList()) {
                Path relative = outputDirectory.relativize(path);
                String artifactPath = relative.toString().replace('\\', '/');
                if (files.size() + 1 > runtimeOptions.maxGeneratedFiles()) {
                    throw new PlatformException(413, "Generated artifact contains too many files.");
                }
                long size = Files.size(path);
                if (size > runtimeOptions.maxGeneratedFileBytes()) {
                    throw new PlatformException(413,
                            "Generated artifact file is too large: " + artifactPath);
                }
                totalBytes += size;
                if (totalBytes > runtimeOptions.maxGeneratedArtifactBytes()) {
                    throw new PlatformException(413,
                            "Generated artifact exceeds the configured size limit.");
                }
                files.put(artifactPath, Files.readString(path, StandardCharsets.UTF_8));
            }
        }
        return files;
    }

    /**
     * Verifies that generated files contain the artifact families implied by the source PSM.
     *
     * @param source source PSM model
     * @param files  generated file map
     */
    private void validateGeneratedArtifactCompleteness(ModelRecord source,
            Map<String, String> files) {
        JsonNode elements = source.modelJson().path("graph").path("elements");
        long lambdaCount = countElementsByClass(elements, "AwsLambdaFunction");
        long stackCount = countElementsByClass(elements, "SamStack");
        boolean hasLambdaHandlers = files.keySet().stream()
                .anyMatch(path -> path.startsWith("src/functions/")
                        && path.endsWith("/handler.go"));
        boolean hasSamTemplates = files.keySet().stream()
                .anyMatch(path -> path.startsWith("template") && path.endsWith(".yaml"));
        if (lambdaCount > 0 && !hasLambdaHandlers) {
            throw new PlatformException(500,
                    "PSM-to-artifact generation produced no Lambda handlers even though the "
                            + "source PSM contains " + lambdaCount
                            + " Lambda function(s). Regenerate the PSM from PIM so its source "
                            + "XMI sidecar is restored, then generate artifacts again.");
        }
        if (stackCount > 0 && !hasSamTemplates) {
            throw new PlatformException(500,
                    "PSM-to-artifact generation produced no SAM templates even though the "
                            + "source PSM contains " + stackCount
                            + " stack(s). Regenerate the PSM from PIM so its source XMI sidecar "
                            + "is restored, then generate artifacts again.");
        }
    }

    /**
     * Counts graph elements by EMF class name.
     *
     * @param elements graph element array
     * @param eClass   EMF class name
     * @return matching element count
     */
    private long countElementsByClass(JsonNode elements, String eClass) {
        if (!elements.isArray()) {
            return 0;
        }
        long count = 0;
        for (JsonNode element : elements) {
            if (eClass.equals(text(element, "eClass", ""))) {
                count++;
            }
        }
        return count;
    }

    /**
     * Enforces an optional optimistic source revision precondition.
     *
     * @param source           source model
     * @param expectedRevision expected revision, or {@code null}
     */
    private void requireSourceRevision(ModelRecord source, Long expectedRevision) {
        if (expectedRevision == null) {
            return;
        }
        if (source.revision() != expectedRevision.longValue()) {
            throw new PlatformException(409,
                    "Source model changed before the MDE operation could run.");
        }
    }

    /**
     * Hashes a model JSON payload.
     *
     * @param modelJson model JSON
     * @return lowercase hexadecimal SHA-256 digest
     */
    private String hash(JsonNode modelJson) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(
                    digest.digest(store.objectMapper().writeValueAsBytes(modelJson)));
        } catch (Exception ex) {
            throw new PlatformException(500, "Could not hash source model.");
        }
    }

    /**
     * Chooses the source XMI hash when present, otherwise hashes JSON.
     *
     * @param source source model
     * @return source model hash
     */
    private String sourceModelHash(ModelRecord source) {
        if (source.sourceXmiHash() != null && !source.sourceXmiHash().isBlank()) {
            return source.sourceXmiHash();
        }
        return hash(source.modelJson());
    }

    /**
     * Enforces the configured maximum model input size before invoking Epsilon.
     *
     * @param bytes source bytes
     * @param label user-facing source label
     */
    private void requireExecutionInputBudget(byte[] bytes, String label) {
        if (bytes != null && bytes.length > runtimeOptions.maxModelUploadBytes()) {
            throw new PlatformException(413, label + " exceeds the configured size limit.");
        }
    }

    /**
     * Copies graph endpoint fields back into semantic relationship objects before exporting JSON to
     * XMI.
     *
     * @param modelJson model JSON
     * @return hydrated model JSON copy
     */
    private JsonNode hydrateSemanticReferences(JsonNode modelJson) {
        if (modelJson == null || !modelJson.isObject()) {
            return modelJson;
        }
        ObjectNode copy = (ObjectNode) modelJson.deepCopy();
        Map<String, JsonNode> graphRelationships = new LinkedHashMap<>();
        JsonNode relationships = copy.path("graph").path("relationships");
        if (relationships.isArray()) {
            relationships.forEach(relationship -> {
                String id = text(relationship, "id", "");
                if (!id.isBlank()) {
                    graphRelationships.put(id, relationship);
                }
            });
        }
        if (!graphRelationships.isEmpty()) {
            hydrateSemanticReferences(copy, graphRelationships);
        }
        return copy;
    }

    /**
     * Recursively hydrates semantic relationship endpoint fields from graph relationship records.
     *
     * @param node               current JSON node
     * @param graphRelationships graph relationships keyed by id
     */
    private void hydrateSemanticReferences(JsonNode node,
            Map<String, JsonNode> graphRelationships) {
        if (node == null || node.isNull()) {
            return;
        }
        if (node.isObject()) {
            ObjectNode object = (ObjectNode) node;
            JsonNode graphRelationship = graphRelationships.get(text(object, "id", ""));
            if (graphRelationship != null) {
                copyReferenceIfMissing(object, graphRelationship, "source", "sourceElementId");
                copyReferenceIfMissing(object, graphRelationship, "target", "targetElementId");
                copyReferenceIfMissing(object, graphRelationship, "sourceElementId", "source");
                copyReferenceIfMissing(object, graphRelationship, "targetElementId", "target");
            }
            hydrateTraceEndpointIds(object);
            object.fields().forEachRemaining(entry -> hydrateSemanticReferences(entry.getValue(),
                    graphRelationships));
            return;
        }
        if (node.isArray()) {
            node.forEach(child -> hydrateSemanticReferences(child, graphRelationships));
        }
    }

    /**
     * Copies a text reference from a source node when the target field is blank.
     *
     * @param target            target object to update
     * @param source            source object to read
     * @param fieldName         preferred field name
     * @param fallbackFieldName fallback field name
     */
    private void copyReferenceIfMissing(ObjectNode target, JsonNode source, String fieldName,
            String fallbackFieldName) {
        if (target.hasNonNull(fieldName) && !target.path(fieldName).asText("").isBlank()) {
            return;
        }
        String value = text(source, fieldName, "");
        if (value.isBlank()) {
            value = text(source, fallbackFieldName, "");
        }
        if (!value.isBlank()) {
            target.put(fieldName, value);
        }
    }

    /**
     * Ensures TraceLink endpoint id fields are present for export.
     *
     * @param object semantic JSON object
     */
    private void hydrateTraceEndpointIds(ObjectNode object) {
        if (!"TraceLink".equals(text(object, "eClass", ""))) {
            return;
        }
        copyReferenceIfMissing(object, object, "sourceElementId", "source");
        copyReferenceIfMissing(object, object, "targetElementId", "target");
    }

    /**
     * Best-effort recursive deletion for temporary transformation directories.
     *
     * @param path directory to delete
     */
    private void deleteQuietly(Path path) {
        try (var paths = Files.walk(path)) {
            paths.sorted(java.util.Comparator.reverseOrder()).forEach(item -> {
                try {
                    Files.deleteIfExists(item);
                } catch (Exception ignored) {
                    // Temporary ETL files should not mask transformation results.
                }
            });
        } catch (Exception ignored) {
            // Best-effort cleanup.
        }
    }

    /**
     * Reads a non-blank text field from a JSON object.
     *
     * @param node     JSON object
     * @param field    field name
     * @param fallback fallback text
     * @return field text or fallback
     */
    private String text(JsonNode node, String field, String fallback) {
        JsonNode value = node == null ? null : node.get(field);
        if (value == null || value.isNull()) {
            return fallback;
        }
        String text = value.asText("");
        return text.isBlank() ? fallback : text;
    }

    /**
     * Returns the first non-blank string from a set of candidates.
     *
     * @param values candidate values
     * @return first non-blank value or an empty string
     */
    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    /**
     * Converts text to a stable kebab-case slug.
     *
     * @param value raw value
     * @return slug value
     */
    private String slug(String value) {
        String normalized = String.valueOf(value == null ? "" : value).trim().toLowerCase(
                Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("^-+|-+$", "");
        return normalized.isBlank() ? "generated" : normalized;
    }

    /**
     * Converts text to lower camel case.
     *
     * @param value raw value
     * @return camel-case value
     */
    private String camel(String value) {
        String[] parts = slug(value).split("-");
        if (parts.length == 0) {
            return "operation";
        }
        StringBuilder result = new StringBuilder(parts[0]);
        for (int i = 1; i < parts.length; i++) {
            if (!parts[i].isBlank()) {
                result.append(parts[i].substring(0, 1).toUpperCase(Locale.ROOT))
                        .append(parts[i].substring(1));
            }
        }
        return result.toString();
    }

    /**
     * Mirrors generated readiness decisions into the manual backlog expected by the platform UI.
     *
     * @param model generated model JSON
     */
    private void mirrorReadinessToManualBacklog(ObjectNode model) {
        ArrayNode backlog = store.objectMapper().createArrayNode();
        JsonNode readiness = model.path("readiness");
        appendManualDecisionTasks(backlog, readiness.path("manualDecisions"));
        if (backlog.isEmpty()) {
            backlog.add(manualTask("review-generated-pim",
                    "Review generated PIM responsibilities and integration contracts.",
                    "Semi-automated transformations require human review before promotion.",
                    true, "MANUAL_MODELING", "OPEN"));
        }
        model.set("manualBacklog", backlog);
        ObjectNode graph = model.withObject("graph");
        graph.set("manualBacklog", backlog.deepCopy());
    }

    /**
     * Appends manual decision tasks from generated readiness decisions.
     *
     * @param backlog   backlog array to append to
     * @param decisions readiness decision array
     */
    private void appendManualDecisionTasks(ArrayNode backlog, JsonNode decisions) {
        if (!decisions.isArray()) {
            return;
        }
        decisions.forEach(decision -> backlog.add(manualTask(
                "decision-" + text(decision, "id", UUID.randomUUID().toString()),
                text(decision, "name", "Manual transformation decision"),
                firstNonBlank(text(decision, "question", ""), text(decision, "rationale", ""),
                        "Review the generated model decision before promotion."),
                decision.path("blocking").asBoolean(true),
                "ETL_MANUAL_DECISION",
                "OPEN")));
    }

    /**
     * Builds a manual backlog task object.
     *
     * @param id        task identifier seed
     * @param title     task title
     * @param rationale task rationale
     * @param required  whether completion is required
     * @param category  task category
     * @param status    task status
     * @return task JSON object
     */
    private ObjectNode manualTask(String id, String title, String rationale, boolean required,
            String category, String status) {
        ObjectNode task = store.objectMapper().createObjectNode();
        task.put("id", slug(id));
        task.put("name", title);
        task.put("title", title);
        task.put("status", status);
        task.put("required", required);
        task.put("category", category);
        task.put("rationale", rationale);
        return task;
    }

    /**
     * Appends a generic manual review task to a model.
     *
     * @param model generated model JSON
     * @param title task title
     */
    private void addManualBacklog(ObjectNode model, String title) {
        model.withArray("manualBacklog").addObject()
                .put("id", "review-" + System.currentTimeMillis())
                .put("name", title)
                .put("status", "OPEN")
                .put("required", true)
                .put("category", "MANUAL_MODELING")
                .put("rationale",
                        "Semi-automated transformations require human review before promotion.");
    }

    /**
     * Serializes a JSON node with pretty printing.
     *
     * @param node JSON node to serialize
     * @return pretty-printed JSON
     */
    private String pretty(JsonNode node) {
        try {
            return store.objectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(node);
        } catch (Exception ex) {
            throw new PlatformException(500, "Could not serialize model.");
        }
    }

    /**
     * Sanitizes text for artifact path fragments.
     *
     * @param value raw value
     * @return sanitized value
     */
    private String sanitize(String value) {
        return String.valueOf(value == null ? "artifact" : value)
                .replaceAll("[^A-Za-z0-9_.-]+", "-");
    }

    /**
     * Generated model JSON plus the canonical XMI sidecar bytes used to persist it.
     *
     * @param model     generated model JSON
     * @param sourceXmi canonical source XMI bytes
     */
    private record GeneratedModel(ObjectNode model, byte[] sourceXmi) {

    }
}
