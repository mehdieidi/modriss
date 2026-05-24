package io.mehdieidi.modless.platform.core.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
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
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

public final class ModelService {

    private final JsonFileStore store;
    private final ProjectService projectService;
    private final ModelingConfigService modelingConfig;
    private final XmiModelImportService xmiImportService;

    public ModelService(JsonFileStore store, ProjectService projectService) {
        this(store, projectService, new ModelingConfigService());
    }

    public ModelService(JsonFileStore store, ProjectService projectService,
            ModelingConfigService modelingConfig) {
        this.store = store;
        this.projectService = projectService;
        this.modelingConfig = modelingConfig;
        this.xmiImportService = new XmiModelImportService(store.objectMapper());
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
        ModelRecord updated = new ModelRecord(existing.id(), existing.projectId(), level,
                requireName(name, level),
                normalizeModel(name, level, modelJson), existing.createdAt(), Instant.now());
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
        if (level != ModelLevel.CIM) {
            return new ValidationResult(true, List.of());
        }
        List<ValidationIssue> issues = validateCimModel(modelJson);
        boolean valid = issues.stream().noneMatch(issue -> "ERROR".equals(issue.severity()));
        return new ValidationResult(valid, issues);
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

    public ImportResult importModel(ModelLevel level, String fileName, byte[] bytes,
            String format) {
        try {
            JsonNode model = switch (String.valueOf(format).toLowerCase()) {
                case "json" ->
                        store.objectMapper().readTree(new String(bytes, StandardCharsets.UTF_8));
                case "xmi" -> xmiImportService.importModel(level, bytes);
                default -> throw new PlatformException(400,
                        "Unsupported import format. Use JSON or XMI.");
            };
            String name = fileName == null || fileName.isBlank() ? level.apiName() + "-model"
                    : fileName.replaceFirst("\\.[^.]+$", "");
            ValidationResult validation = validate(level, model);
            return new ImportResult(name, normalizeModel(name, level, model), validation.issues());
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

    private Path modelDir(String projectId, ModelLevel level) {
        return Path.of("projects", projectId, "models", level.apiName());
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
}
