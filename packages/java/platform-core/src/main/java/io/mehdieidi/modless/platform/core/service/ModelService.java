package io.mehdieidi.modless.platform.core.service;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.mehdieidi.modless.mde.validation.EpsilonEvlValidator;
import io.mehdieidi.modless.mde.validation.EvlConstraintKind;
import io.mehdieidi.modless.mde.validation.EvlConstraintViolation;
import io.mehdieidi.modless.mde.validation.EvlDiagnostic;
import io.mehdieidi.modless.mde.validation.EvlValidationException;
import io.mehdieidi.modless.mde.validation.EvlValidationReport;
import io.mehdieidi.modless.mde.validation.EvlValidationRequest;
import io.mehdieidi.modless.mde.validation.FileEvlModelConfiguration;
import io.mehdieidi.modless.mde.validation.ValidationSeverity;
import io.mehdieidi.modless.platform.core.PlatformException;
import io.mehdieidi.modless.platform.core.model.ModelLevel;
import io.mehdieidi.modless.platform.core.model.ModelRecord;
import io.mehdieidi.modless.platform.core.model.ProjectRecord;
import io.mehdieidi.modless.platform.core.model.UserRecord;
import io.mehdieidi.modless.platform.core.repository.JsonFileStore;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

public final class ModelService {

    private final JsonFileStore store;
    private final ProjectService projectService;
    private final ModelingConfigService modelingConfig;
    private final XmiModelImportService xmiImportService;
    private final EpsilonEvlValidator evlValidator;

    public ModelService(JsonFileStore store, ProjectService projectService) {
        this(store, projectService, new ModelingConfigService());
    }

    public ModelService(JsonFileStore store, ProjectService projectService,
            ModelingConfigService modelingConfig) {
        this.store = store;
        this.projectService = projectService;
        this.modelingConfig = modelingConfig;
        this.xmiImportService = new XmiModelImportService(store.objectMapper());
        this.evlValidator = new EpsilonEvlValidator();
    }

    private static ValidationIssue issue(String severity, String constraint, String message) {
        return new ValidationIssue(severity, constraint, "MODEL", message, null, null, null);
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
        return model;
    }

    public ModelRecord create(UserRecord user, ModelLevel level, String projectId, String name,
            JsonNode modelJson) {
        ProjectRecord project = projectService.get(user, projectId);
        projectService.requireEditor(project, user.id());
        Instant now = Instant.now();
        ModelRecord model = new ModelRecord(UUID.randomUUID().toString(), projectId, level,
                requireName(name, level),
                normalizeModel(name, level, modelJson), now, now);
        store.write(modelPath(projectId, level, model.id()), model);
        return model;
    }

    public ModelRecord update(UserRecord user, ModelLevel level, String id, String name,
            JsonNode modelJson) {
        ModelRecord existing = get(user, level, id);
        ProjectRecord project = projectService.get(user, existing.projectId());
        projectService.requireEditor(project, user.id());
        JsonNode normalizedModel = normalizeModel(name, level, modelJson);
        preserveServerManagedFields(existing.modelJson(), normalizedModel);
        ModelRecord updated = new ModelRecord(existing.id(), existing.projectId(), level,
                requireName(name, level),
                normalizedModel, existing.createdAt(), Instant.now());
        store.write(modelPath(existing.projectId(), level, id), updated);
        return updated;
    }

    public void delete(UserRecord user, ModelLevel level, String id) {
        ModelRecord model = get(user, level, id);
        ProjectRecord project = projectService.get(user, model.projectId());
        projectService.requireEditor(project, user.id());
        try {
            Files.deleteIfExists(store.resolve(modelPath(model.projectId(), level, id)));
        } catch (Exception ex) {
            throw new PlatformException(500, "Could not delete model.");
        }
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

    private ValidationResult validateImportedXmi(ModelLevel level, JsonNode modelJson,
            byte[] xmiBytes) {
        if (xmiBytes == null || xmiBytes.length == 0) {
            return validate(level, modelJson);
        }
        List<ValidationIssue> issues = new ArrayList<>(validateWithEvl(level, xmiBytes));
        if (level == ModelLevel.CIM) {
            issues.addAll(validateCimModel(modelJson));
        }
        boolean valid = issues.stream().noneMatch(issue -> "ERROR".equals(issue.severity()));
        return new ValidationResult(valid, issues);
    }

    private List<ValidationIssue> validateWithEvl(ModelLevel level, JsonNode modelJson) {
        Path workDir = null;
        try {
            Path repositoryRoot = findRepositoryRoot();
            workDir = Files.createTempDirectory("modless-" + level.apiName() + "-validation-");
            Path modelFile = workDir.resolve("model-" + level.apiName() + ".xmi");
            Files.write(modelFile, xmiImportService.exportModel(level,
                    hydrateSemanticReferences(modelJson)));
            EvlValidationReport report = evlValidator.validate(new EvlValidationRequest(
                    validationRoot(repositoryRoot, level),
                    List.of(Path.of(validationEntryFile(level))),
                    List.of(FileEvlModelConfiguration.readOnly(
                            validationModelName(level),
                            validationModelAliases(level),
                            modelFile,
                            List.of(metamodelFile(repositoryRoot, level)))),
                    true));
            return validationIssues(report);
        } catch (EvlValidationException ex) {
            return validationIssues(ex.getReport());
        } catch (Exception ex) {
            return List.of(issue("ERROR", "EvlValidationExecution",
                    "EVL validation could not run: "
                            + (ex.getMessage() == null ? ex.getClass().getSimpleName()
                            : ex.getMessage())));
        } finally {
            if (workDir != null) {
                deleteQuietly(workDir);
            }
        }
    }

    private List<ValidationIssue> validateWithEvl(ModelLevel level, byte[] xmiBytes) {
        Path workDir = null;
        try {
            Path repositoryRoot = findRepositoryRoot();
            workDir = Files.createTempDirectory("modless-" + level.apiName() + "-validation-");
            Path modelFile = workDir.resolve("model-" + level.apiName() + ".xmi");
            Files.write(modelFile, xmiBytes);
            EvlValidationReport report = evlValidator.validate(new EvlValidationRequest(
                    validationRoot(repositoryRoot, level),
                    List.of(Path.of(validationEntryFile(level))),
                    List.of(FileEvlModelConfiguration.readOnly(
                            validationModelName(level),
                            validationModelAliases(level),
                            modelFile,
                            List.of(metamodelFile(repositoryRoot, level)))),
                    true));
            return validationIssues(report);
        } catch (EvlValidationException ex) {
            return validationIssues(ex.getReport());
        } catch (Exception ex) {
            return List.of(issue("ERROR", "EvlValidationExecution",
                    "EVL validation could not run: "
                            + (ex.getMessage() == null ? ex.getClass().getSimpleName()
                            : ex.getMessage())));
        } finally {
            if (workDir != null) {
                deleteQuietly(workDir);
            }
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

    private Path findRepositoryRoot() {
        Path current = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        while (current != null) {
            if (Files.isRegularFile(
                    current.resolve("mde/validation/cim/cim-semantic-validation.evl"))
                    && Files.isRegularFile(current.resolve(
                    "mde/validation/pim/pim-semantic-validation.evl"))
                    && Files.isRegularFile(current.resolve(
                    "mde/validation/psm/psm-semantic-validation.evl"))
                    && Files.isRegularFile(current.resolve(
                    "mde/metamodels/cim/cim-combined.ecore"))
                    && Files.isRegularFile(current.resolve(
                    "mde/metamodels/pim/pim-combined.ecore"))
                    && Files.isRegularFile(current.resolve(
                    "mde/metamodels/psm/psm-combined.ecore"))) {
                return current;
            }
            current = current.getParent();
        }
        throw new PlatformException(500,
                "EVL validation files were not found from the backend working directory.");
    }

    private Path validationRoot(Path repositoryRoot, ModelLevel level) {
        return repositoryRoot.resolve("mde/validation/" + level.apiName());
    }

    private String validationEntryFile(ModelLevel level) {
        return switch (level) {
            case CIM -> "cim-semantic-validation.evl";
            case PIM -> "pim-semantic-validation.evl";
            case PSM -> "psm-semantic-validation.evl";
        };
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

    private Path metamodelFile(Path repositoryRoot, ModelLevel level) {
        return repositoryRoot.resolve("mde/metamodels/" + level.apiName() + "/"
                + switch (level) {
            case CIM -> "cim-combined.ecore";
            case PIM -> "pim-combined.ecore";
            case PSM -> "psm-combined.ecore";
        });
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

    private void hydrateSemanticReferences(JsonNode node,
            Map<String, JsonNode> graphRelationships) {
        if (node == null || node.isNull()) {
            return;
        }
        if (node.isObject()) {
            ObjectNode object = (ObjectNode) node;
            JsonNode graphRelationship = graphRelationships.get(text(object, "id", ""));
            if (graphRelationship != null) {
                copyReferenceIfMissing(object, graphRelationship, "source");
                copyReferenceIfMissing(object, graphRelationship, "target");
            }
            object.fields().forEachRemaining(entry -> hydrateSemanticReferences(entry.getValue(),
                    graphRelationships));
            return;
        }
        if (node.isArray()) {
            node.forEach(child -> hydrateSemanticReferences(child, graphRelationships));
        }
    }

    private void copyReferenceIfMissing(ObjectNode target, JsonNode source, String fieldName) {
        if (target.hasNonNull(fieldName) && !target.path(fieldName).asText("").isBlank()) {
            return;
        }
        String value = text(source, fieldName, "");
        if (!value.isBlank()) {
            target.put(fieldName, value);
        }
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
        try {
            String normalizedFormat = String.valueOf(format).toLowerCase();
            JsonNode model = switch (normalizedFormat) {
                case "json" ->
                        store.objectMapper().readTree(new String(bytes, StandardCharsets.UTF_8));
                case "xmi" -> xmiImportService.importModel(level, bytes);
                default -> throw new PlatformException(400,
                        "Unsupported import format. Use JSON or XMI.");
            };
            if ("xmi".equals(normalizedFormat) && model instanceof ObjectNode objectModel) {
                objectModel.put("_sourceXmiBase64", Base64.getEncoder().encodeToString(bytes));
            }
            String name = fileName == null || fileName.isBlank() ? level.apiName() + "-model"
                    : fileName.replaceFirst("\\.[^.]+$", "");
            JsonNode normalizedModel = normalizeModel(name, level, model);
            ValidationResult validation = "xmi".equals(normalizedFormat)
                    ? validateImportedXmi(level, normalizedModel, bytes)
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

    public byte[] exportModel(JsonNode modelJson, String format) {
        if (!"json".equalsIgnoreCase(format)) {
            throw new PlatformException(400, "Only JSON export is currently supported.");
        }
        try {
            return store.objectMapper().writerWithDefaultPrettyPrinter()
                    .writeValueAsBytes(modelJson);
        } catch (Exception ex) {
            throw new PlatformException(500, "Could not export model.");
        }
    }

    public ModelRecord find(ModelLevel level, String id) {
        Path projects = store.resolve(Path.of("projects"));
        try (Stream<Path> projectDirs = Files.list(projects)) {
            return projectDirs.map(
                            project -> store.read(Path.of("projects", project.getFileName().toString(),
                                            "models", level.apiName(), id + ".json"), ModelRecord.class)
                                    .orElse(null))
                    .filter(model -> model != null)
                    .findFirst()
                    .orElseThrow(() -> new PlatformException(404, "Model not found."));
        } catch (PlatformException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new PlatformException(500, "Could not read model store.");
        }
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
        if (!copy.has("diagram")) {
            ObjectNode diagram = copy.putObject("diagram");
            diagram.putArray("elements");
            diagram.putArray("relationships");
        }
        return copy;
    }

    private void preserveServerManagedFields(JsonNode existingModel, JsonNode updatedModel) {
        if (!(updatedModel instanceof ObjectNode updatedObject) || existingModel == null) {
            return;
        }
        preserveTextField(existingModel, updatedObject, "_sourceXmiBase64");
    }

    private void preserveTextField(JsonNode existingModel, ObjectNode updatedModel,
            String fieldName) {
        if (updatedModel.has(fieldName)) {
            return;
        }
        JsonNode existingValue = existingModel.get(fieldName);
        if (existingValue != null && existingValue.isTextual()
                && !existingValue.asText("").isBlank()) {
            updatedModel.set(fieldName, existingValue.deepCopy());
        }
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
                    createdAt, updatedAt);
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

    private String requireName(String name, ModelLevel level) {
        return name == null || name.trim().isEmpty() ? level.apiName() + "-model" : name.trim();
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

    public record ModelSummary(
            String id,
            String projectId,
            ModelLevel level,
            String name,
            Instant createdAt,
            Instant updatedAt) {

    }
}
