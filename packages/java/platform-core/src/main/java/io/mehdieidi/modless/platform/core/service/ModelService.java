package io.mehdieidi.modless.platform.core.service;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.mehdieidi.modless.mde.validation.EpsilonEvlValidator;
import io.mehdieidi.modless.mde.validation.EvlConstraintKind;
import io.mehdieidi.modless.mde.validation.EvlConstraintViolation;
import io.mehdieidi.modless.mde.validation.EvlDiagnostic;
import io.mehdieidi.modless.mde.validation.EvlValidationException;
import io.mehdieidi.modless.mde.validation.EvlValidationReport;
import io.mehdieidi.modless.mde.validation.EvlValidationRequest;
import io.mehdieidi.modless.mde.validation.ResourceEvlModelConfiguration;
import io.mehdieidi.modless.mde.validation.ValidationSeverity;
import io.mehdieidi.modless.platform.core.PlatformException;
import io.mehdieidi.modless.platform.core.model.ModelIndexRecord;
import io.mehdieidi.modless.platform.core.model.ModelLevel;
import io.mehdieidi.modless.platform.core.model.ModelRecord;
import io.mehdieidi.modless.platform.core.model.ProjectRecord;
import io.mehdieidi.modless.platform.core.model.StagedImportRecord;
import io.mehdieidi.modless.platform.core.model.UserRecord;
import io.mehdieidi.modless.platform.core.repository.JsonFileStore;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

public final class ModelService {

    private final JsonFileStore store;
    private final ProjectService projectService;
    private final ModelingConfigService modelingConfig;
    private final MdeRuntimeOptions runtimeOptions;
    private final MdeRuntimePaths mdePaths;
    private final MetamodelResolver metamodelResolver;
    private final ModelLockService modelLocks;
    private final XmiModelImportService xmiImportService;
    private final EpsilonEvlValidator evlValidator;
    private final Map<String, ValidationResult> validationCache = Collections.synchronizedMap(
            new LinkedHashMap<>(128, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, ValidationResult> eldest) {
                    return size() > 128;
                }
            });

    public ModelService(JsonFileStore store, ProjectService projectService) {
        this(store, projectService, new ModelingConfigService());
    }

    public ModelService(JsonFileStore store, ProjectService projectService,
            ModelingConfigService modelingConfig) {
        this(store, projectService, modelingConfig, MdeRuntimeOptions.defaults(),
                new ModelLockService());
    }

    public ModelService(JsonFileStore store, ProjectService projectService,
            ModelingConfigService modelingConfig, MdeRuntimeOptions runtimeOptions,
            ModelLockService modelLocks) {
        this(store, projectService, modelingConfig, runtimeOptions,
                new MdeRuntimePaths(runtimeOptions), null, modelLocks);
    }

    public ModelService(JsonFileStore store, ProjectService projectService,
            ModelingConfigService modelingConfig, MdeRuntimeOptions runtimeOptions,
            MdeRuntimePaths mdePaths, MetamodelResolver metamodelResolver,
            ModelLockService modelLocks) {
        this.store = store;
        this.projectService = projectService;
        this.modelingConfig = modelingConfig;
        this.runtimeOptions = runtimeOptions == null ? MdeRuntimeOptions.defaults()
                : runtimeOptions;
        this.mdePaths = mdePaths == null ? new MdeRuntimePaths(this.runtimeOptions) : mdePaths;
        this.metamodelResolver = metamodelResolver == null
                ? new FileMetamodelResolver(this.mdePaths) : metamodelResolver;
        this.modelLocks = modelLocks == null ? new ModelLockService() : modelLocks;
        this.xmiImportService = new XmiModelImportService(store.objectMapper(),
                this.metamodelResolver);
        this.evlValidator = new EpsilonEvlValidator(this.runtimeOptions.executionTimeout(),
                this.runtimeOptions.maxCapturedOutputBytes());
    }

    private static ValidationIssue issue(String severity, String constraint, String message) {
        return new ValidationIssue(severity, constraint, "MODEL", message, null, null, null);
    }

    ModelLockService modelLocks() {
        return modelLocks;
    }

    public long maxModelUploadBytes() {
        return runtimeOptions.maxModelUploadBytes();
    }

    public List<ModelRecord> list(UserRecord user, ModelLevel level, String projectId) {
        if (projectId == null || projectId.isBlank()) {
            return List.of();
        }
        projectService.get(user, projectId);
        Path dir = store.resolve(modelDir(projectId, level));
        if (!Files.isDirectory(dir)) {
            return List.of();
        }
        try (Stream<Path> files = Files.list(dir)) {
            return files.filter(path -> path.getFileName().toString().endsWith(".json"))
                    .map(path -> store.read(store.root().relativize(path), ModelRecord.class)
                            .orElse(null))
                    .filter(model -> model != null)
                    .sorted(Comparator.comparing(ModelRecord::updatedAt).reversed())
                    .toList();
        } catch (Exception ex) {
            throw new PlatformException(500, "Could not list models.");
        }
    }

    public List<ModelSummary> listSummaries(UserRecord user, ModelLevel level, String projectId) {
        if (projectId == null || projectId.isBlank()) {
            return List.of();
        }
        projectService.get(user, projectId);
        Path dir = store.resolve(modelDir(projectId, level));
        if (!Files.isDirectory(dir)) {
            return List.of();
        }
        try (Stream<Path> files = Files.list(dir)) {
            return files.filter(path -> path.getFileName().toString().endsWith(".json"))
                    .map(path -> readSummary(path, level))
                    .filter(model -> model != null)
                    .sorted(Comparator.comparing(ModelSummary::updatedAt).reversed())
                    .toList();
        } catch (Exception ex) {
            throw new PlatformException(500, "Could not list model summaries.");
        }
    }

    public ModelRecord get(UserRecord user, ModelLevel level, String id) {
        ModelRecord model = find(level, id);
        projectService.get(user, model.projectId());
        return clientRecord(model);
    }

    public ModelRecord get(UserRecord user, ModelLevel level, String projectId, String id) {
        projectService.get(user, projectId);
        ModelRecord model = store.require(modelPath(projectId, level, id), ModelRecord.class,
                "Model not found.");
        writeModelIndex(model);
        return clientRecord(repairMetadataIfNeeded(model));
    }

    public ModelRecord create(UserRecord user, ModelLevel level, String projectId, String name,
            JsonNode modelJson) {
        return create(user, level, projectId, name, modelJson, null);
    }

    public ModelRecord create(UserRecord user, ModelLevel level, String projectId, String name,
            JsonNode modelJson, byte[] sourceXmiBytes) {
        ProjectRecord project = projectService.get(user, projectId);
        projectService.requireEditor(project, user.id());
        Instant now = Instant.now();
        JsonNode normalizedModel = normalizeModel(name, level, modelJson);
        SourceXmiUpdate sourceXmi = sourceXmiBytes == null || sourceXmiBytes.length == 0
                ? resolveSourceXmiUpdate(user, projectId, level, normalizedModel, false)
                : new SourceXmiUpdate(sourceXmiBytes, hashBytes(sourceXmiBytes), null);
        sourceXmi = canonicalSourceXmi(level, normalizedModel, sourceXmi);
        MetamodelDescriptor metamodel = metamodelResolver.resolve(level);
        ModelRecord model = new ModelRecord(UUID.randomUUID().toString(), projectId, level,
                requireName(name, level), normalizedModel, metamodel.version(),
                metamodel.sha256(), 1, sourceXmi.hash(), "CURRENT", now, now);
        persistModelAndSourceXmi(null, model, sourceXmi);
        return clientRecord(model);
    }

    public ModelRecord update(UserRecord user, ModelLevel level, String id, String name,
            JsonNode modelJson) {
        return update(user, level, id, name, modelJson, null);
    }

    public ModelRecord update(UserRecord user, ModelLevel level, String id, String name,
            JsonNode modelJson, Long expectedRevision) {
        return modelLocks.withModelLock(id, Duration.ofSeconds(30), () -> {
            ModelRecord existing = get(user, level, id);
            requireExpectedRevision(existing, expectedRevision);
            ProjectRecord project = projectService.get(user, existing.projectId());
            projectService.requireEditor(project, user.id());
            JsonNode normalizedModel = normalizeModel(name, level, modelJson);
            SourceXmiUpdate sourceXmi = resolveSourceXmiUpdate(user, existing.projectId(),
                    level, normalizedModel, true);
            if (!(sourceXmi.shouldPreserve() && sourceXmi(existing).isPresent()
                    && !hasDiagramLayout(normalizedModel))) {
                sourceXmi = canonicalSourceXmi(level, normalizedModel, sourceXmi);
            }
            MetamodelDescriptor metamodel = metamodelResolver.resolve(level);
            ModelRecord updated = new ModelRecord(existing.id(), existing.projectId(), level,
                    requireName(name, level), normalizedModel, metamodel.version(),
                    metamodel.sha256(), nextRevision(existing),
                    sourceXmi.effectiveHash(existing.sourceXmiHash()), "CURRENT",
                    existing.createdAt(), Instant.now());
            persistModelAndSourceXmi(existing, updated, sourceXmi);
            return clientRecord(updated);
        });
    }

    public ModelRecord patch(UserRecord user, ModelLevel level, String id, String name,
            List<ModelPatchOperation> operations) {
        return patch(user, level, id, name, operations, null);
    }

    public ModelRecord patch(UserRecord user, ModelLevel level, String id, String name,
            List<ModelPatchOperation> operations, Long expectedRevision) {
        return modelLocks.withModelLock(id, Duration.ofSeconds(30), () -> {
            ModelRecord existing = get(user, level, id);
            requireExpectedRevision(existing, expectedRevision);
            ProjectRecord project = projectService.get(user, existing.projectId());
            projectService.requireEditor(project, user.id());
            if (operations == null || operations.isEmpty()) {
                return clientRecord(existing);
            }
            ObjectNode patchedModel = existing.modelJson() instanceof ObjectNode objectNode
                    ? objectNode.deepCopy()
                    : store.objectMapper().createObjectNode();
            for (ModelPatchOperation operation : operations) {
                applyPatchOperation(patchedModel, operation);
            }
            JsonNode normalizedModel = normalizeModel(name == null ? existing.name() : name,
                    level, patchedModel);
            SourceXmiUpdate sourceXmi = resolveSourceXmiUpdate(user, existing.projectId(),
                    level, normalizedModel, true);
            if (!(sourceXmi.shouldPreserve() && sourceXmi(existing).isPresent()
                    && !hasDiagramLayout(normalizedModel))) {
                sourceXmi = canonicalSourceXmi(level, normalizedModel, sourceXmi);
            }
            MetamodelDescriptor metamodel = metamodelResolver.resolve(level);
            ModelRecord updated = new ModelRecord(existing.id(), existing.projectId(), level,
                    requireName(name == null ? existing.name() : name, level), normalizedModel,
                    metamodel.version(), metamodel.sha256(), nextRevision(existing),
                    sourceXmi.effectiveHash(existing.sourceXmiHash()), "CURRENT",
                    existing.createdAt(), Instant.now());
            persistModelAndSourceXmi(existing, updated, sourceXmi);
            return clientRecord(updated);
        });
    }

    public void delete(UserRecord user, ModelLevel level, String id) {
        modelLocks.withModelLock(id, Duration.ofSeconds(30), () -> {
            ModelRecord model = get(user, level, id);
            ProjectRecord project = projectService.get(user, model.projectId());
            projectService.requireEditor(project, user.id());
            store.deleteIfExists(modelPath(model.projectId(), level, id));
            store.deleteIfExists(sourceXmiPath(model.projectId(), level, id));
            store.deleteIfExists(modelIndexPath(id));
            return null;
        });
    }

    public ValidationResult validate(ModelLevel level, JsonNode modelJson) {
        if (modelJson == null || modelJson.isNull()) {
            return new ValidationResult(false,
                    List.of(issue("ERROR", "ModelRequired", "Model JSON is required.")));
        }
        List<ValidationIssue> issues = new ArrayList<>(validateWithEvl(level, modelJson));
        if (level == ModelLevel.CIM) {
            issues.addAll(validateCimModel(modelJson));
        }
        boolean valid = issues.stream().noneMatch(issue -> "ERROR".equals(issue.severity()));
        return new ValidationResult(valid, issues);
    }

    public ValidationResult validate(UserRecord user, ModelLevel level, String id) {
        ModelRecord model = get(user, level, id);
        String cacheKey = validationCacheKey(model);
        ValidationResult cached = validationCache.get(cacheKey);
        if (cached != null) {
            return cached;
        }
        Optional<byte[]> xmiBytes = sourceXmi(model);
        ValidationResult result;
        if (xmiBytes.isPresent()) {
            result = validateGeneratedXmi(level, xmiBytes.get());
            if (hasRecoverableStaleSourceError(result)) {
                ValidationResult repairedResult = validateRegeneratedSourceXmi(model);
                if (!hasRecoverableStaleSourceError(repairedResult)) {
                    result = repairedResult;
                }
            }
        } else {
            // Legacy JSON-only records are converted in memory so existing projects remain usable.
            result = validate(level, model.modelJson());
        }
        validationCache.put(cacheKey, result);
        return result;
    }

    private ValidationResult validateRegeneratedSourceXmi(ModelRecord model) {
        try {
            SourceXmiUpdate sourceXmi = canonicalSourceXmi(model.level(), model.modelJson(),
                    null);
            ValidationResult result = validateGeneratedXmi(model.level(), sourceXmi.bytes());
            if (!hasRecoverableStaleSourceError(result)) {
                attachSourceXmi(model, sourceXmi.bytes());
            }
            return result;
        } catch (RuntimeException ex) {
            return validate(model.level(), model.modelJson());
        }
    }

    private boolean hasRecoverableStaleSourceError(ValidationResult result) {
        return hasRelationshipEndpointLoadingError(result) || hasTraceEndpointError(result);
    }

    private boolean hasRelationshipEndpointLoadingError(ValidationResult result) {
        return result != null && result.issues().stream().anyMatch(issue ->
                "ERROR".equals(issue.severity())
                        && "EVL_MODEL_LOADING".equals(issue.constraint())
                        && (issue.message().contains("required feature 'source'")
                        || issue.message().contains("required feature 'target'")));
    }

    private boolean hasTraceEndpointError(ValidationResult result) {
        return result != null && result.issues().stream().anyMatch(issue ->
                "ERROR".equals(issue.severity())
                        && "TraceLinkHasReferenceOrExternalId".equals(issue.constraint()));
    }

    public ValidationResult validateGeneratedXmi(ModelLevel level, byte[] xmiBytes) {
        List<ValidationIssue> issues = validateWithEvl(level, xmiBytes);
        boolean valid = issues.stream().noneMatch(issue -> "ERROR".equals(issue.severity()));
        return new ValidationResult(valid, issues);
    }

    private List<ValidationIssue> validateWithEvl(ModelLevel level, JsonNode modelJson) {
        try {
            MetamodelDescriptor metamodel = metamodelResolver.resolve(level);
            EvlValidationReport report = evlValidator.validate(new EvlValidationRequest(
                    mdePaths.validationRoot(level),
                    List.of(mdePaths.validationEntryFile(level)),
                    List.of(ResourceEvlModelConfiguration.readOnly(
                            validationModelName(level),
                            xmiImportService.exportResource(level,
                                    hydrateSemanticReferences(modelJson)),
                            metamodel.packages())),
                    true));
            return validationIssues(report);
        } catch (PlatformException ex) {
            return List.of(issue("ERROR", "XmiExport", ex.getMessage()));
        } catch (EvlValidationException ex) {
            return validationIssues(ex.getReport());
        } catch (Exception ex) {
            return List.of(issue("ERROR", "EvlValidationExecution",
                    "EVL validation could not run: "
                            + (ex.getMessage() == null ? ex.getClass().getSimpleName()
                            : ex.getMessage())));
        }
    }

    private List<ValidationIssue> validateWithEvl(ModelLevel level, byte[] xmiBytes) {
        try {
            MetamodelDescriptor metamodel = metamodelResolver.resolve(level);
            EvlValidationReport report = evlValidator.validate(new EvlValidationRequest(
                    mdePaths.validationRoot(level),
                    List.of(mdePaths.validationEntryFile(level)),
                    List.of(ResourceEvlModelConfiguration.readOnly(
                            validationModelName(level),
                            validationModelAliases(level),
                            xmiImportService.loadResource(level, xmiBytes, "validation"),
                            metamodel.packages())),
                    true));
            return validationIssues(report);
        } catch (PlatformException ex) {
            return List.of(issue("ERROR", "XmiLoad", ex.getMessage()));
        } catch (EvlValidationException ex) {
            return validationIssues(ex.getReport());
        } catch (Exception ex) {
            return List.of(issue("ERROR", "EvlValidationExecution",
                    "EVL validation could not run: "
                            + (ex.getMessage() == null ? ex.getClass().getSimpleName()
                            : ex.getMessage())));
        }
    }

    private List<ValidationIssue> validationIssues(EvlValidationReport report) {
        if (report == null) {
            return List.of(issue("ERROR", "EvlValidationExecution",
                    "EVL validation failed without a report."));
        }
        List<ValidationIssue> issues = new ArrayList<>();
        report.violations().forEach(violation -> issues.add(validationIssue(violation)));
        report.diagnostics().forEach(diagnostic -> issues.add(validationIssue(diagnostic)));
        return issues;
    }

    private ValidationIssue validationIssue(EvlConstraintViolation violation) {
        String severity = violation.kind() == EvlConstraintKind.MANDATORY ? "ERROR" : "WARNING";
        String elementId = violation.element().attributes().getOrDefault("id", null);
        String elementName = violation.element().attributes().getOrDefault("name",
                violation.element().summary());
        String guidance = violation.fixes().isEmpty() ? ""
                : "Suggested fix: " + violation.fixes().get(0).title();
        return new ValidationIssue(severity, textOrDefault(violation.constraintName(),
                "EvlConstraint"), violation.contextType(), violation.message(), guidance,
                elementId, elementName);
    }

    private ValidationIssue validationIssue(EvlDiagnostic diagnostic) {
        String severity = diagnostic.severity() == ValidationSeverity.ERROR ? "ERROR" : "WARNING";
        String constraint = "EVL_" + diagnostic.phase().name();
        String message = !diagnostic.reason().isBlank() ? diagnostic.reason()
                : diagnostic.whatWentWrong();
        String guidance = diagnostic.howToFix();
        return new ValidationIssue(severity, constraint, "EVL_DIAGNOSTIC", message, guidance,
                null, diagnostic.file() == null ? null : diagnostic.file().toString());
    }

    private List<ValidationIssue> validateCimModel(JsonNode modelJson) {
        List<ValidationIssue> issues = new java.util.ArrayList<>();
        Map<String, Object> config = modelingConfig.config();
        Map<?, ?> levels = (Map<?, ?>) config.get("levels");
        Map<?, ?> cim = (Map<?, ?>) levels.get("cim");
        Map<String, Map<?, ?>> definitionsByType = new java.util.HashMap<>();
        if (cim.get("elements") instanceof List<?> elements) {
            elements.stream().filter(Map.class::isInstance).map(Map.class::cast)
                    .forEach(element -> {
                        Object type = element.get("type");
                        if (type != null) {
                            definitionsByType.put(String.valueOf(type), element);
                        }
                    });
        }
        Map<String, JsonNode> graphObjectsById = new java.util.HashMap<>();
        indexGraphObjects(modelJson.path("graph").path("elements"), graphObjectsById);
        indexGraphObjects(modelJson.path("graph").path("relationships"), graphObjectsById);
        List<JsonNode> graphRelationships = new java.util.ArrayList<>();
        collectGraphRelationships(modelJson.path("graph").path("relationships"),
                graphRelationships);
        List<JsonNode> elements = new java.util.ArrayList<>();
        collectSemanticElements(modelJson, elements);
        if (elements.isEmpty()) {
            issues.add(issue("WARNING", "CimElementsMissing",
                    "CIM model has no semantic elements."));
            return issues;
        }
        for (JsonNode element : elements) {
            String type = element.path("eClass").asText(element.path("type").asText(""));
            String elementId = element.path("id").asText(null);
            String elementName = element.path("name").asText(element.path("label").asText(null));
            Map<?, ?> definition = definitionsByType.get(type);
            if (definition == null) {
                issues.add(new ValidationIssue("ERROR", "UnknownCimElement", type,
                        "Element type is not defined by the CIM metamodel.", null, elementId,
                        elementName));
                continue;
            }
            JsonNode fallback = resolveGraphFallback(element, graphObjectsById, graphRelationships);
            validateRequiredFeatures(element, fallback, definition, issues, elementId, elementName);
        }
        return issues;
    }

    private void indexGraphObjects(JsonNode nodes, Map<String, JsonNode> index) {
        if (!nodes.isArray()) {
            return;
        }
        for (JsonNode node : nodes) {
            String id = node.path("id").asText("");
            if (!id.isBlank()) {
                index.put(id, node);
            }
        }
    }

    private void collectGraphRelationships(JsonNode nodes, List<JsonNode> relationships) {
        if (!nodes.isArray()) {
            return;
        }
        nodes.forEach(relationships::add);
    }

    private JsonNode resolveGraphFallback(JsonNode element, Map<String, JsonNode> graphObjectsById,
            List<JsonNode> graphRelationships) {
        String id = element.path("id").asText("");
        if (!id.isBlank()) {
            JsonNode byId = graphObjectsById.get(id);
            if (byId != null) {
                return byId;
            }
        }
        String type = element.path("eClass").asText("");
        String name = element.path("name").asText("");
        for (JsonNode relationship : graphRelationships) {
            if (!type.equals(relationship.path("eClass").asText(""))) {
                continue;
            }
            if (!name.isBlank() && name.equals(relationship.path("name").asText(""))) {
                return relationship;
            }
        }
        return null;
    }

    private void collectSemanticElements(JsonNode node, List<JsonNode> elements) {
        if (node == null || node.isNull()) {
            return;
        }
        if (node.isObject()) {
            if (node.hasNonNull("eClass")) {
                elements.add(node);
            }
            node.fields().forEachRemaining(entry -> {
                if ("graph".equals(entry.getKey()) || "diagram".equals(entry.getKey())) {
                    return;
                }
                collectSemanticElements(entry.getValue(), elements);
            });
            return;
        }
        if (node.isArray()) {
            node.forEach(child -> collectSemanticElements(child, elements));
        }
    }

    private String validationModelName(ModelLevel level) {
        return switch (level) {
            case CIM -> "CIM";
            case PIM -> "PIM";
            case PSM -> "AWSPSM";
        };
    }

    private List<String> validationModelAliases(ModelLevel level) {
        return switch (level) {
            case CIM, PIM -> List.of("KERNEL");
            case PSM -> List.of("AWSPSMENUMS", "KERNEL");
        };
    }

    private void validateRequiredFeatures(JsonNode element, JsonNode fallbackElement,
            Map<?, ?> definition,
            List<ValidationIssue> issues, String elementId, String elementName) {
        validateRequiredFeatureList(element, fallbackElement, definition,
                definition.get("attributes"),
                "RequiredAttribute", issues, elementId, elementName);
        validateRequiredFeatureList(element, fallbackElement, definition,
                definition.get("references"),
                "RequiredReference", issues, elementId, elementName);
    }

    private void validateRequiredFeatureList(JsonNode element, JsonNode fallbackElement,
            Map<?, ?> definition, Object fieldsObject, String constraint,
            List<ValidationIssue> issues, String elementId, String elementName) {
        if (!(fieldsObject instanceof List<?> fields)) {
            return;
        }
        for (Object fieldObject : fields) {
            if (!(fieldObject instanceof Map<?, ?> field)
                    || !Boolean.TRUE.equals(field.get("required"))) {
                continue;
            }
            if ("RequiredReference".equals(constraint)
                    && Boolean.TRUE.equals(field.get("readonly"))) {
                continue;
            }
            String name = String.valueOf(field.get("name"));
            if ("RequiredReference".equals(constraint)
                    && Boolean.TRUE.equals(definition.get("relationshipElement"))
                    && ("source".equals(name) || "target".equals(name))) {
                continue;
            }
            JsonNode value = element.get(name);
            if ((value == null || value.isNull() || (value.isTextual() && value.asText().isBlank())
                    || (value.isArray() && value.isEmpty()))
                    && fallbackElement != null) {
                value = fallbackElement.get(name);
            }
            if (value == null || value.isNull() || (value.isTextual() && value.asText().isBlank())
                    || (value.isArray() && value.isEmpty())) {
                issues.add(new ValidationIssue("ERROR", constraint, element.path("eClass").asText(),
                        "Required CIM feature is missing: " + name, null, elementId, elementName));
            }
        }
    }

    private JsonNode hydrateSemanticReferences(JsonNode modelJson) {
        if (modelJson == null || !modelJson.isObject()) {
            return modelJson;
        }
        Map<String, JsonNode> graphRelationships = new LinkedHashMap<>();
        JsonNode relationships = modelJson.path("graph").path("relationships");
        if (relationships.isArray()) {
            relationships.forEach(relationship -> {
                String id = text(relationship, "id", "");
                if (!id.isBlank()) {
                    graphRelationships.put(id, relationship);
                }
            });
        }
        ObjectNode copy = (ObjectNode) modelJson.deepCopy();
        stripValidationExportOnlyFields(copy);
        if (!graphRelationships.isEmpty()) {
            hydrateSemanticReferences(copy, graphRelationships);
        }
        return copy;
    }

    private void stripValidationExportOnlyFields(ObjectNode model) {
        model.remove(List.of("graph", "diagram", "views", "manualBacklog",
                "validationIssues"));
    }

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

    private void hydrateTraceEndpointIds(ObjectNode object) {
        if (!"TraceLink".equals(text(object, "eClass", ""))) {
            return;
        }
        copyReferenceIfMissing(object, object, "sourceElementId", "source");
        copyReferenceIfMissing(object, object, "targetElementId", "target");
    }

    private String text(JsonNode node, String field, String fallback) {
        return node == null ? fallback : node.path(field).asText(fallback);
    }

    private String textOrDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private void deleteQuietly(Path path) {
        try (var paths = Files.walk(path)) {
            paths.sorted(java.util.Comparator.reverseOrder()).forEach(item -> {
                try {
                    Files.deleteIfExists(item);
                } catch (Exception ignored) {
                    // Temporary validation files should not mask validation results.
                }
            });
        } catch (Exception ignored) {
            // Best-effort cleanup.
        }
    }

    public ImportResult importModel(ModelLevel level, String fileName, byte[] bytes,
            String format) {
        return importModel(null, null, level, fileName, bytes, format);
    }

    public ImportResult importModel(UserRecord user, String projectId, ModelLevel level,
            String fileName, byte[] bytes, String format) {
        if (bytes != null && bytes.length > runtimeOptions.maxModelUploadBytes()) {
            throw new PlatformException(413, "Model file is too large.");
        }
        try {
            String normalizedFormat = String.valueOf(format).toLowerCase();
            JsonNode model = switch (normalizedFormat) {
                case "json" ->
                        store.objectMapper().readTree(new String(bytes, StandardCharsets.UTF_8));
                case "xmi" -> xmiImportService.importModel(level, bytes);
                default -> throw new PlatformException(400,
                        "Unsupported import format. Use JSON or XMI.");
            };
            String name = fileName == null || fileName.isBlank() ? level.apiName() + "-model"
                    : fileName.replaceFirst("\\.[^.]+$", "");
            JsonNode normalizedModel = normalizeModel(name, level, model);
            if ("xmi".equals(normalizedFormat)
                    && normalizedModel instanceof ObjectNode objectModel) {
                objectModel.put("_sourceXmiBase64", Base64.getEncoder().encodeToString(bytes));
            }
            ValidationResult validation = "xmi".equals(normalizedFormat)
                    ? new ValidationResult(true, List.of())
                    : validate(level, normalizedModel);
            return new ImportResult(name, normalizedModel, validation.issues());
        } catch (PlatformException ex) {
            throw ex;
        } catch (Exception ex) {
            String normalizedFormat = String.valueOf(format).toLowerCase();
            throw new PlatformException(400, "Uploaded model is not valid "
                    + ("xmi".equals(normalizedFormat) ? "XMI." : "JSON."));
        }
    }

    public ImportResult importModelFromStream(UserRecord user, String projectId, ModelLevel level,
            String fileName, InputStream input, long sizeBytes, String format) {
        if (sizeBytes > runtimeOptions.maxModelUploadBytes()) {
            throw new PlatformException(413, "Model file is too large.");
        }
        try {
            byte[] bytes = input.readNBytes(Math.toIntExact(runtimeOptions.maxModelUploadBytes()
                    + 1));
            if (bytes.length > runtimeOptions.maxModelUploadBytes()) {
                throw new PlatformException(413, "Model file is too large.");
            }
            return importModel(user, projectId, level, fileName, bytes, format);
        } catch (PlatformException ex) {
            throw ex;
        } catch (ArithmeticException ex) {
            throw new PlatformException(500, "Configured upload limit is too large.");
        } catch (Exception ex) {
            throw new PlatformException(400, "Uploaded model could not be read.");
        }
    }

    public byte[] exportModel(ModelLevel level, JsonNode modelJson, String format) {
        try {
            return switch (String.valueOf(format == null ? "json" : format).toLowerCase()) {
                case "json" -> store.objectMapper().writerWithDefaultPrettyPrinter()
                        .writeValueAsBytes(modelJson);
                case "xmi" -> xmiImportService.exportModel(level,
                        hydrateSemanticReferences(modelJson));
                default -> throw new PlatformException(400,
                        "Unsupported export format. Use JSON or XMI.");
            };
        } catch (PlatformException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new PlatformException(500, "Could not export model.");
        }
    }

    public byte[] exportModel(JsonNode modelJson, String format) {
        return exportModel(ModelLevel.CIM, modelJson, format);
    }

    public byte[] exportModel(UserRecord user, ModelLevel level, String id, String format) {
        ModelRecord model = get(user, level, id);
        if ("xmi".equalsIgnoreCase(String.valueOf(format))) {
            Optional<byte[]> xmiBytes = sourceXmi(model);
            if (xmiBytes.isPresent()) {
                return xmiBytes.get();
            }
        }
        return exportModel(level, model.modelJson(), format);
    }

    public ModelRecord find(ModelLevel level, String id) {
        Optional<ModelIndexRecord> index = store.read(modelIndexPath(id), ModelIndexRecord.class);
        if (index.isPresent()) {
            ModelIndexRecord record = index.get();
            if (record.level() != level) {
                throw new PlatformException(404, "Model not found.");
            }
            return repairMetadataIfNeeded(store.require(
                    modelPath(record.projectId(), level, id), ModelRecord.class,
                    "Model not found."));
        }
        return findLegacyAndIndex(level, id);
    }

    private JsonNode normalizeModel(String name, ModelLevel level, JsonNode modelJson) {
        ObjectNode copy = modelJson == null || !modelJson.isObject()
                ? store.objectMapper().createObjectNode()
                : ((ObjectNode) modelJson.deepCopy());
        if (!copy.hasNonNull("name")) {
            copy.put("name", requireName(name, level));
        }
        if (!copy.hasNonNull("modelLevel")) {
            copy.put("modelLevel", level.name());
        }
        return copy;
    }

    public ModelSummary summary(ModelRecord model) {
        return new ModelSummary(model.id(), model.projectId(), model.level(), model.name(),
                Math.max(1, model.revision()), model.metamodelVersion(), model.migrationState(),
                model.createdAt(), model.updatedAt());
    }

    public Optional<byte[]> sourceXmi(ModelRecord model) {
        Path path = sourceXmiPath(model.projectId(), model.level(), model.id());
        try {
            Path resolved = store.resolve(path);
            return Files.isRegularFile(resolved)
                    ? Optional.of(Files.readAllBytes(resolved))
                    : Optional.empty();
        } catch (Exception ex) {
            throw new PlatformException(500, "Could not read stored source XMI.");
        }
    }

    public void attachSourceXmi(ModelRecord model, byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return;
        }
        store.writeBytesAtomically(sourceXmiPath(model.projectId(), model.level(), model.id()),
                bytes);
        ModelRecord stored = store.read(modelPath(model.projectId(), model.level(), model.id()),
                ModelRecord.class).orElse(model);
        ModelRecord updated = new ModelRecord(stored.id(), stored.projectId(), stored.level(),
                stored.name(), stored.modelJson(),
                textOrDefault(stored.metamodelVersion(), currentVersion(stored.level())),
                textOrDefault(stored.metamodelHash(), currentHash(stored.level())),
                Math.max(1, stored.revision()), hashBytes(bytes),
                textOrDefault(stored.migrationState(), "CURRENT"), stored.createdAt(),
                stored.updatedAt());
        store.write(modelPath(updated.projectId(), updated.level(), updated.id()), updated);
        writeModelIndex(updated);
    }

    private SourceXmiUpdate canonicalSourceXmi(ModelLevel level, JsonNode modelJson,
            SourceXmiUpdate requestedSourceXmi) {
        if (requestedSourceXmi != null && requestedSourceXmi.bytes() != null
                && !hasDiagramLayout(modelJson)) {
            return requestedSourceXmi;
        }
        byte[] bytes = xmiImportService.exportModel(level,
                hydrateSemanticReferences(withLayoutAnnotations(modelJson)));
        return new SourceXmiUpdate(bytes, hashBytes(bytes), null);
    }

    private boolean hasDiagramLayout(JsonNode modelJson) {
        JsonNode elements = modelJson == null ? null : modelJson.path("diagram").path("elements");
        if (elements == null || !elements.isArray()) {
            return false;
        }
        for (JsonNode element : elements) {
            if ((element.hasNonNull("x") || element.hasNonNull("y"))
                    && !text(element, "id", "").isBlank()) {
                return true;
            }
        }
        return false;
    }

    private JsonNode withLayoutAnnotations(JsonNode modelJson) {
        if (!(modelJson instanceof ObjectNode)) {
            return modelJson;
        }
        Map<String, JsonNode> layoutById = new LinkedHashMap<>();
        JsonNode elements = modelJson.path("diagram").path("elements");
        if (elements.isArray()) {
            elements.forEach(element -> {
                String id = text(element, "id", "");
                if (!id.isBlank() && (element.hasNonNull("x") || element.hasNonNull("y"))) {
                    layoutById.put(id, element);
                }
            });
        }
        if (layoutById.isEmpty()) {
            return modelJson;
        }
        ObjectNode copy = (ObjectNode) modelJson.deepCopy();
        applyLayoutAnnotations(copy, layoutById);
        return copy;
    }

    private void applyLayoutAnnotations(JsonNode node, Map<String, JsonNode> layoutById) {
        if (node == null || node.isNull()) {
            return;
        }
        if (node instanceof ObjectNode objectNode) {
            String id = text(objectNode, "id", "");
            JsonNode layout = layoutById.get(id);
            if (layout != null && !"Annotation".equals(text(objectNode, "eClass", ""))) {
                mergeLayoutAnnotations(objectNode, layout);
            }
            objectNode.fields().forEachRemaining(entry ->
                    applyLayoutAnnotations(entry.getValue(), layoutById));
            return;
        }
        if (node.isArray()) {
            node.forEach(child -> applyLayoutAnnotations(child, layoutById));
        }
    }

    private void mergeLayoutAnnotations(ObjectNode element, JsonNode layout) {
        ArrayNode annotations = element.withArray("annotations");
        ArrayNode retained = store.objectMapper().createArrayNode();
        annotations.forEach(annotation -> {
            String key = text(annotation, "key", "");
            if (!"modless.layout.x".equals(key) && !"modless.layout.y".equals(key)) {
                retained.add(annotation.deepCopy());
            }
        });
        if (layout.hasNonNull("x")) {
            retained.add(layoutAnnotation("modless.layout.x", layout.path("x").asText()));
        }
        if (layout.hasNonNull("y")) {
            retained.add(layoutAnnotation("modless.layout.y", layout.path("y").asText()));
        }
        element.set("annotations", retained);
    }

    private ObjectNode layoutAnnotation(String key, String value) {
        ObjectNode annotation = store.objectMapper().createObjectNode();
        annotation.put("eClass", "Annotation");
        annotation.put("key", key);
        annotation.put("value", value);
        annotation.put("source", "modless.ui");
        return annotation;
    }

    private ModelRecord clientRecord(ModelRecord model) {
        JsonNode modelJson = model.modelJson();
        if (modelJson instanceof ObjectNode objectNode) {
            ObjectNode copy = objectNode.deepCopy();
            stripTransportOnlyFields(copy);
            copy.remove("_sourceXmiToken");
            modelJson = copy;
        }
        return new ModelRecord(model.id(), model.projectId(), model.level(), model.name(),
                modelJson, textOrDefault(model.metamodelVersion(), currentVersion(model.level())),
                textOrDefault(model.metamodelHash(), currentHash(model.level())),
                Math.max(1, model.revision()), model.sourceXmiHash(),
                textOrDefault(model.migrationState(), migrationState(model)),
                model.createdAt(), model.updatedAt());
    }

    private void stripTransportOnlyFields(ObjectNode model) {
        model.remove("_sourceXmiBase64");
        JsonNode diagram = model.get("diagram");
        if (isGeneratedDiagramDuplicate(model.path("graph"), diagram)) {
            model.remove("diagram");
        }
    }

    private boolean isGeneratedDiagramDuplicate(JsonNode graph, JsonNode diagram) {
        if (graph == null || diagram == null || !graph.isObject() || !diagram.isObject()) {
            return false;
        }
        JsonNode graphElements = graph.path("elements");
        JsonNode graphRelationships = graph.path("relationships");
        JsonNode diagramElements = diagram.path("elements");
        JsonNode diagramRelationships = diagram.path("relationships");
        if (!graphElements.isArray() || !graphRelationships.isArray()
                || !diagramElements.isArray() || !diagramRelationships.isArray()) {
            return false;
        }
        return graphElements.equals(diagramElements)
                && sameRelationshipEndpoints(graphRelationships, diagramRelationships);
    }

    private boolean sameRelationshipEndpoints(JsonNode graphRelationships,
            JsonNode diagramRelationships) {
        if (graphRelationships.size() != diagramRelationships.size()) {
            return false;
        }
        for (int i = 0; i < graphRelationships.size(); i++) {
            JsonNode graphRelationship = graphRelationships.get(i);
            JsonNode diagramRelationship = diagramRelationships.get(i);
            if (!text(graphRelationship, "id", "").equals(text(diagramRelationship, "id", ""))
                    || !text(graphRelationship, "kind", "").equals(
                    text(diagramRelationship, "kind", ""))
                    || !text(graphRelationship, "source", "").equals(
                    text(diagramRelationship, "source", ""))
                    || !text(graphRelationship, "target", "").equals(
                    text(diagramRelationship, "target", ""))) {
                return false;
            }
        }
        return true;
    }

    private String stageSourceXmi(UserRecord user, String projectId, ModelLevel level,
            byte[] bytes) {
        String token = UUID.randomUUID().toString();
        Instant now = Instant.now();
        Path xmiPath = stagedSourceXmiPath(token);
        store.writeBytesAtomically(xmiPath, bytes);
        store.write(stagedSourceXmiRecordPath(token), new StagedImportRecord(
                token,
                user == null ? "" : user.id(),
                projectId == null ? "" : projectId,
                level,
                xmiPath.toString().replace('\\', '/'),
                bytes == null ? 0 : bytes.length,
                now,
                now.plus(runtimeOptions.stagedImportTtl())));
        return token;
    }

    private String removeSourceXmiToken(JsonNode modelJson) {
        if (modelJson instanceof ObjectNode objectNode) {
            String token = text(objectNode, "_sourceXmiToken", "");
            objectNode.remove("_sourceXmiToken");
            return token;
        }
        return "";
    }

    private SourceXmiUpdate resolveSourceXmiUpdate(UserRecord user, String projectId,
            ModelLevel level, JsonNode modelJson, boolean preserveExistingWhenMissing) {
        byte[] inlineBytes = removeSourceXmiBase64(modelJson);
        if (inlineBytes != null && inlineBytes.length > 0) {
            return new SourceXmiUpdate(inlineBytes, hashBytes(inlineBytes), null);
        }
        String token = removeSourceXmiToken(modelJson);
        if (token == null || token.isBlank()) {
            return preserveExistingWhenMissing ? SourceXmiUpdate.preserveUpdate()
                    : SourceXmiUpdate.deleteUpdate();
        }
        StagedImportRecord record = store.require(stagedSourceXmiRecordPath(token),
                StagedImportRecord.class, "Staged XMI import token was not found.");
        if (record.expiresAt() != null && record.expiresAt().isBefore(Instant.now())) {
            cleanupStagedImport(record);
            throw new PlatformException(410, "Staged XMI import token has expired.");
        }
        if (record.level() != level) {
            throw new PlatformException(400, "Staged XMI import token is for a different level.");
        }
        if (record.userId() != null && !record.userId().isBlank()
                && (user == null || !record.userId().equals(user.id()))) {
            throw new PlatformException(403, "Staged XMI import token belongs to another user.");
        }
        if (record.projectId() != null && !record.projectId().isBlank()
                && !record.projectId().equals(projectId)) {
            throw new PlatformException(403,
                    "Staged XMI import token belongs to another project.");
        }
        Path xmiPath = Path.of(record.xmiPath());
        Path resolved = store.resolve(xmiPath);
        if (!Files.isRegularFile(resolved)) {
            throw new PlatformException(410, "Staged XMI import file is no longer available.");
        }
        try {
            byte[] bytes = Files.readAllBytes(resolved);
            return new SourceXmiUpdate(bytes, hashBytes(bytes), record);
        } catch (Exception ex) {
            throw new PlatformException(500, "Could not read staged source XMI.");
        }
    }

    private byte[] removeSourceXmiBase64(JsonNode modelJson) {
        if (!(modelJson instanceof ObjectNode objectNode)) {
            return null;
        }
        String encoded = text(objectNode, "_sourceXmiBase64", "");
        objectNode.remove("_sourceXmiBase64");
        if (encoded.isBlank()) {
            return null;
        }
        try {
            return Base64.getDecoder().decode(encoded);
        } catch (IllegalArgumentException ex) {
            throw new PlatformException(400, "Imported source XMI payload is not valid base64.");
        }
    }

    private void persistModelAndSourceXmi(ModelRecord previous, ModelRecord model,
            SourceXmiUpdate sourceXmi) {
        Path modelPath = modelPath(model.projectId(), model.level(), model.id());
        Path sourcePath = sourceXmiPath(model.projectId(), model.level(), model.id());
        byte[] previousModelBytes = readBytesIfPresent(modelPath);
        byte[] previousSourceBytes = readBytesIfPresent(sourcePath);
        boolean hadPreviousModel = previous != null && previousModelBytes != null;
        boolean hadPreviousSource = previousSourceBytes != null;
        try {
            store.write(modelPath, model);
            writeModelIndex(model);
            if (sourceXmi.shouldPreserve()) {
                return;
            }
            if (sourceXmi.shouldDelete()) {
                store.deleteIfExists(sourcePath);
            } else {
                store.writeBytesAtomically(sourcePath, sourceXmi.bytes());
                cleanupStagedImport(sourceXmi.record());
            }
        } catch (RuntimeException ex) {
            rollback(modelPath, sourcePath, previousModelBytes, previousSourceBytes,
                    hadPreviousModel, hadPreviousSource, previous == null ? model.id() : null);
            throw ex;
        }
    }

    private void rollback(Path modelPath, Path sourcePath, byte[] previousModelBytes,
            byte[] previousSourceBytes, boolean hadPreviousModel, boolean hadPreviousSource,
            String newModelId) {
        try {
            if (hadPreviousModel) {
                store.writeBytesAtomically(modelPath, previousModelBytes);
            } else {
                store.deleteIfExists(modelPath);
            }
            if (hadPreviousSource) {
                store.writeBytesAtomically(sourcePath, previousSourceBytes);
            } else {
                store.deleteIfExists(sourcePath);
            }
            if (newModelId != null) {
                store.deleteIfExists(modelIndexPath(newModelId));
            }
        } catch (RuntimeException ignored) {
            // Preserve the original persistence failure.
        }
    }

    private byte[] readBytesIfPresent(Path path) {
        try {
            Path resolved = store.resolve(path);
            return Files.isRegularFile(resolved) ? Files.readAllBytes(resolved) : null;
        } catch (Exception ex) {
            throw new PlatformException(500, "Could not snapshot stored model state.");
        }
    }

    private void writeModelIndex(ModelRecord model) {
        store.write(modelIndexPath(model.id()),
                new ModelIndexRecord(model.id(), model.projectId(), model.level()));
    }

    private ModelRecord findLegacyAndIndex(ModelLevel level, String id) {
        Path projects = store.resolve(Path.of("projects"));
        try (Stream<Path> projectDirs = Files.list(projects)) {
            ModelRecord model = projectDirs.map(project -> store.read(Path.of("projects",
                            project.getFileName().toString(), "models", level.apiName(),
                            id + ".json"), ModelRecord.class).orElse(null))
                    .filter(candidate -> candidate != null)
                    .findFirst()
                    .orElseThrow(() -> new PlatformException(404, "Model not found."));
            writeModelIndex(model);
            return repairMetadataIfNeeded(model);
        } catch (PlatformException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new PlatformException(500, "Could not read model store.");
        }
    }

    private ModelRecord repairMetadataIfNeeded(ModelRecord model) {
        if (model == null) {
            return null;
        }
        MetamodelDescriptor metamodel = metamodelResolver.resolve(model.level());
        long revision = Math.max(1, model.revision());
        String migrationState = model.metamodelHash() == null || model.metamodelHash().isBlank()
                || metamodel.sha256().equals(model.metamodelHash()) ? "CURRENT"
                : "NEEDS_MIGRATION";
        if (model.metamodelVersion() != null && !model.metamodelVersion().isBlank()
                && model.metamodelHash() != null && !model.metamodelHash().isBlank()
                && model.revision() > 0
                && model.migrationState() != null && !model.migrationState().isBlank()) {
            return model;
        }
        ModelRecord repaired = new ModelRecord(model.id(), model.projectId(), model.level(),
                model.name(), model.modelJson(), metamodel.version(), metamodel.sha256(),
                revision, model.sourceXmiHash(), migrationState, model.createdAt(),
                model.updatedAt());
        store.write(modelPath(model.projectId(), model.level(), model.id()), repaired);
        writeModelIndex(repaired);
        return repaired;
    }

    private void requireExpectedRevision(ModelRecord existing, Long expectedRevision) {
        if (expectedRevision == null) {
            return;
        }
        if (expectedRevision.longValue() != existing.revision()) {
            throw new PlatformException(409, "Model was modified by another operation.");
        }
    }

    private long nextRevision(ModelRecord existing) {
        return Math.max(1, existing.revision()) + 1;
    }

    private String currentVersion(ModelLevel level) {
        return metamodelResolver.resolve(level).version();
    }

    private String currentHash(ModelLevel level) {
        return metamodelResolver.resolve(level).sha256();
    }

    private String migrationState(ModelRecord model) {
        String hash = model.metamodelHash();
        return hash == null || hash.isBlank() || hash.equals(currentHash(model.level()))
                ? "CURRENT" : "NEEDS_MIGRATION";
    }

    private void cleanupStagedImport(StagedImportRecord record) {
        if (record == null) {
            return;
        }
        store.deleteIfExists(Path.of(record.xmiPath()));
        store.deleteIfExists(stagedSourceXmiRecordPath(record.token()));
    }

    public void cleanupExpiredImports() {
        Path imports = store.resolve(Path.of("model-imports"));
        if (!Files.isDirectory(imports)) {
            return;
        }
        try (Stream<Path> files = Files.list(imports)) {
            files.filter(path -> path.getFileName().toString().endsWith(".json"))
                    .map(path -> store.read(store.root().relativize(path),
                            StagedImportRecord.class).orElse(null))
                    .filter(record -> record != null && record.expiresAt() != null
                            && record.expiresAt().isBefore(Instant.now()))
                    .forEach(this::cleanupStagedImport);
        } catch (Exception ex) {
            throw new PlatformException(500, "Could not clean up staged imports.");
        }
    }

    private String hashBytes(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(bytes == null ? new byte[0] : bytes));
        } catch (Exception ex) {
            throw new PlatformException(500, "Could not hash source XMI.");
        }
    }

    private String validationCacheKey(ModelRecord model) {
        return model.level().name() + ":" + model.id() + ":" + model.revision() + ":"
                + String.valueOf(model.sourceXmiHash());
    }

    private Path modelDir(String projectId, ModelLevel level) {
        return Path.of("projects", projectId, "models", level.apiName());
    }

    private ModelSummary readSummary(Path path, ModelLevel fallbackLevel) {
        try (JsonParser parser = store.objectMapper().getFactory().createParser(path.toFile())) {
            String id = null;
            String projectId = null;
            ModelLevel level = fallbackLevel;
            String name = null;
            long revision = 1;
            String metamodelVersion = null;
            String migrationState = null;
            Instant updatedAt = Files.getLastModifiedTime(path).toInstant();
            Instant createdAt = updatedAt;
            if (parser.nextToken() != JsonToken.START_OBJECT) {
                return null;
            }
            while (parser.nextToken() != JsonToken.END_OBJECT) {
                String field = parser.currentName();
                parser.nextToken();
                if ("id".equals(field)) {
                    id = parser.getValueAsString();
                } else if ("projectId".equals(field)) {
                    projectId = parser.getValueAsString();
                } else if ("level".equals(field)) {
                    level = ModelLevel.valueOf(parser.getValueAsString());
                } else if ("name".equals(field)) {
                    name = parser.getValueAsString();
                } else if ("revision".equals(field)) {
                    revision = Math.max(1, parser.getLongValue());
                } else if ("metamodelVersion".equals(field)) {
                    metamodelVersion = parser.getValueAsString();
                } else if ("migrationState".equals(field)) {
                    migrationState = parser.getValueAsString();
                } else if ("createdAt".equals(field)) {
                    createdAt = parseInstant(parser.getValueAsString(), createdAt);
                } else if ("updatedAt".equals(field)) {
                    updatedAt = parseInstant(parser.getValueAsString(), updatedAt);
                } else if ("modelJson".equals(field)) {
                    break;
                } else {
                    parser.skipChildren();
                }
            }
            if (id == null || projectId == null) {
                return null;
            }
            return new ModelSummary(id, projectId, level, name == null ? id : name,
                    revision, metamodelVersion, migrationState, createdAt, updatedAt);
        } catch (Exception ex) {
            return null;
        }
    }

    private Instant parseInstant(String value, Instant fallback) {
        try {
            return value == null || value.isBlank() ? fallback : Instant.parse(value);
        } catch (Exception ex) {
            return fallback;
        }
    }

    private Path modelPath(String projectId, ModelLevel level, String id) {
        return modelDir(projectId, level).resolve(id + ".json");
    }

    private Path sourceXmiPath(String projectId, ModelLevel level, String id) {
        return modelDir(projectId, level).resolve(id + ".xmi");
    }

    private Path stagedSourceXmiPath(String token) {
        return Path.of("model-imports", token + ".xmi");
    }

    private Path stagedSourceXmiRecordPath(String token) {
        return Path.of("model-imports", token + ".json");
    }

    private Path modelIndexPath(String id) {
        return Path.of("indexes", "models", id + ".json");
    }

    private String requireName(String name, ModelLevel level) {
        return name == null || name.trim().isEmpty() ? level.apiName() + "-model" : name.trim();
    }

    private void applyPatchOperation(ObjectNode root, ModelPatchOperation operation) {
        if (operation == null || operation.op() == null || operation.path() == null) {
            throw new PlatformException(400, "Patch operations require op and path.");
        }
        String op = operation.op().trim().toLowerCase();
        String path = operation.path().trim();
        if (!path.startsWith("/")) {
            throw new PlatformException(400, "Patch paths must be JSON Pointer paths.");
        }
        switch (op) {
            case "add" -> addPatchValue(root, path, operation.value());
            case "replace" -> replacePatchValue(root, path, operation.value());
            case "remove" -> removePatchValue(root, path);
            default -> throw new PlatformException(400,
                    "Unsupported patch operation: " + operation.op());
        }
    }

    private void addPatchValue(ObjectNode root, String path, JsonNode value) {
        JsonNode parent = patchParent(root, path);
        String key = lastPointerSegment(path);
        JsonNode copy = value == null ? store.objectMapper().nullNode() : value.deepCopy();
        if (parent instanceof ObjectNode objectNode) {
            objectNode.set(key, copy);
            return;
        }
        if (parent instanceof ArrayNode arrayNode) {
            if ("-".equals(key)) {
                arrayNode.add(copy);
                return;
            }
            int index = arrayIndex(key, arrayNode.size());
            arrayNode.insert(index, copy);
            return;
        }
        throw new PlatformException(400, "Patch parent is not writable.");
    }

    private void replacePatchValue(ObjectNode root, String path, JsonNode value) {
        JsonNode parent = patchParent(root, path);
        String key = lastPointerSegment(path);
        JsonNode copy = value == null ? store.objectMapper().nullNode() : value.deepCopy();
        if (parent instanceof ObjectNode objectNode) {
            if (!objectNode.has(key)) {
                throw new PlatformException(400, "Patch replace path does not exist: " + path);
            }
            objectNode.set(key, copy);
            return;
        }
        if (parent instanceof ArrayNode arrayNode) {
            int index = arrayIndex(key, arrayNode.size() - 1);
            arrayNode.set(index, copy);
            return;
        }
        throw new PlatformException(400, "Patch parent is not writable.");
    }

    private void removePatchValue(ObjectNode root, String path) {
        JsonNode parent = patchParent(root, path);
        String key = lastPointerSegment(path);
        if (parent instanceof ObjectNode objectNode) {
            objectNode.remove(key);
            return;
        }
        if (parent instanceof ArrayNode arrayNode) {
            arrayNode.remove(arrayIndex(key, arrayNode.size() - 1));
            return;
        }
        throw new PlatformException(400, "Patch parent is not writable.");
    }

    private JsonNode patchParent(ObjectNode root, String path) {
        String[] segments = pointerSegments(path);
        if (segments.length == 0) {
            throw new PlatformException(400, "Root model replacement is not supported by patch.");
        }
        JsonNode current = root;
        for (int i = 0; i < segments.length - 1; i++) {
            if (current instanceof ObjectNode objectNode) {
                current = objectNode.get(segments[i]);
            } else if (current instanceof ArrayNode arrayNode) {
                current = arrayNode.get(arrayIndex(segments[i], arrayNode.size() - 1));
            } else {
                current = null;
            }
            if (current == null || current.isMissingNode() || current.isNull()) {
                throw new PlatformException(400, "Patch parent path does not exist: " + path);
            }
        }
        return current;
    }

    private String[] pointerSegments(String path) {
        if ("/".equals(path)) {
            return new String[]{""};
        }
        String[] raw = path.substring(1).split("/", -1);
        for (int i = 0; i < raw.length; i++) {
            raw[i] = raw[i].replace("~1", "/").replace("~0", "~");
        }
        return raw;
    }

    private String lastPointerSegment(String path) {
        String[] segments = pointerSegments(path);
        return segments[segments.length - 1];
    }

    private int arrayIndex(String value, int maxInclusive) {
        try {
            int index = Integer.parseInt(value);
            if (index < 0 || index > maxInclusive) {
                throw new PlatformException(400, "Patch array index is out of bounds.");
            }
            return index;
        } catch (NumberFormatException ex) {
            throw new PlatformException(400, "Patch array index must be numeric.");
        }
    }

    public record ValidationResult(boolean valid, List<ValidationIssue> issues) {

    }

    public record ValidationIssue(
            String severity,
            String constraint,
            String issueClass,
            String message,
            String guidance,
            String elementId,
            String elementName) {

    }

    public record ImportResult(String name, JsonNode modelJson, List<ValidationIssue> issues) {

    }

    public record ModelPatchOperation(String op, String path, JsonNode value) {

    }

    public record ModelSummary(
            String id,
            String projectId,
            ModelLevel level,
            String name,
            long revision,
            String metamodelVersion,
            String migrationState,
            Instant createdAt,
            Instant updatedAt) {

    }

    private record SourceXmiUpdate(byte[] bytes, String hash, StagedImportRecord record) {

        static SourceXmiUpdate preserveUpdate() {
            return new SourceXmiUpdate(null, null, null);
        }

        static SourceXmiUpdate deleteUpdate() {
            return new SourceXmiUpdate(null, "", null);
        }

        boolean shouldPreserve() {
            return bytes == null && hash == null;
        }

        boolean shouldDelete() {
            return bytes == null && hash != null;
        }

        String effectiveHash(String previousHash) {
            return shouldPreserve() ? previousHash : hash;
        }
    }
}
