package io.mehdieidi.modless.backend.assistant;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.mehdieidi.modless.platform.core.PlatformException;
import io.mehdieidi.modless.platform.core.service.ModelService;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * Compiles semantic assistant operations into backend model patch operations.
 */
@Service
public class AssistantPatchCompiler {

    /**
     * Compiles semantic operations against a model snapshot.
     *
     * @param modelJson model JSON
     * @param semantic  semantic patch
     * @return compiled patch
     */
    public CompiledPatch compile(JsonNode modelJson, SemanticModelPatch semantic) {
        ObjectNode root = modelJson == null || !modelJson.isObject()
                ? JsonNodeFactory.instance.objectNode() : (ObjectNode) modelJson.deepCopy();
        String visualContainer = visualContainer(root);
        ArrayNode elements = root.with(visualContainer).withArray("elements");
        ArrayNode relationships = root.with(visualContainer).withArray("relationships");
        List<ModelService.ModelPatchOperation> patch = new ArrayList<>();
        List<ModelService.ModelPatchOperation> inverse = new ArrayList<>();
        List<String> affected = new ArrayList<>();
        for (SemanticModelPatch.Operation operation : semantic.operations()) {
            if (operation == null || operation.type() == null) {
                throw new PlatformException(400, "Assistant semantic operation type is required.");
            }
            switch (operation.type()) {
                case ADD_ELEMENT -> {
                    JsonNode element = elementPayload(operation);
                    Optional<String> collection = semanticRootCollection(operation.elementType());
                    if (collection.isPresent()) {
                        ArrayNode semanticElements = root.withArray(collection.get());
                        patch.add(new ModelService.ModelPatchOperation("add",
                                "/" + collection.get() + "/-", element));
                        inverse.add(0, new ModelService.ModelPatchOperation("remove",
                                "/" + collection.get() + "/" + semanticElements.size(), null));
                        semanticElements.add(element.deepCopy());
                    }
                    patch.add(new ModelService.ModelPatchOperation("add",
                            "/" + visualContainer + "/elements/-",
                            diagramElementPayload(element)));
                    affected.add(text(element.get("id")));
                    inverse.add(0, new ModelService.ModelPatchOperation("remove",
                            "/" + visualContainer + "/elements/" + elements.size(), null));
                    elements.add(diagramElementPayload(element));
                }
                case CONNECT_ELEMENTS -> {
                    JsonNode relationship = relationshipPayload(operation);
                    patch.add(new ModelService.ModelPatchOperation("add",
                            "/" + visualContainer + "/relationships/-", relationship));
                    affected.add(text(relationship.get("id")));
                    inverse.add(0, new ModelService.ModelPatchOperation("remove",
                            "/" + visualContainer + "/relationships/" + relationships.size(),
                            null));
                    relationships.add(relationship.deepCopy());
                }
                case SET_ATTRIBUTE -> {
                    if (operation.referenceName() == null
                            || !operation.referenceName().matches("[A-Za-z][A-Za-z0-9_-]*")) {
                        throw new PlatformException(400,
                                "Assistant attribute name is not allowed.");
                    }
                    LocatedElement located = locateElement(root, operation.targetElementId());
                    JsonNode previous = located.node().get(operation.referenceName());
                    String path = located.path() + "/" + escapePointer(operation.referenceName());
                    patch.add(new ModelService.ModelPatchOperation(previous == null ? "add"
                            : "replace", path,
                            operation.attributes()));
                    inverse.add(new ModelService.ModelPatchOperation(previous == null
                            ? "remove" : "replace",
                            path, previous));
                    affected.add(operation.targetElementId());
                }
                case DELETE_ELEMENT -> {
                    LocatedElement located = locateElement(root, operation.targetElementId());
                    ObjectNode snapshot = located.node().deepCopy();
                    patch.add(new ModelService.ModelPatchOperation("remove",
                            located.path(), null));
                    inverse.add(new ModelService.ModelPatchOperation("add",
                            located.path(), snapshot));
                    affected.add(operation.targetElementId());
                }
            }
        }
        return new CompiledPatch(patch, inverse, affected);
    }

    /**
     * Applies a compiled patch to a JSON model clone.
     *
     * @param modelJson model JSON
     * @param compiled  compiled patch
     * @return patched model
     */
    public ObjectNode apply(JsonNode modelJson, CompiledPatch compiled) {
        ObjectNode root = modelJson == null || !modelJson.isObject()
                ? JsonNodeFactory.instance.objectNode() : (ObjectNode) modelJson.deepCopy();
        String visualContainer = visualContainer(root);
        root.with(visualContainer).withArray("elements");
        root.with(visualContainer).withArray("relationships");
        for (ModelService.ModelPatchOperation operation : compiled.patch()) {
            apply(root, operation);
        }
        return root;
    }

    private void apply(ObjectNode root, ModelService.ModelPatchOperation operation) {
        if (operation == null || operation.op() == null || operation.path() == null
                || !operation.path().startsWith("/")) {
            throw new PlatformException(400, "Assistant patch operations require op and path.");
        }
        String[] segments = operation.path().substring(1).split("/");
        if (segments.length == 0) {
            throw new PlatformException(400, "Assistant patch path cannot target the root.");
        }
        JsonNode parent = parent(root, segments, operation.op());
        String last = unescapePointer(segments[segments.length - 1]);
        switch (operation.op()) {
            case "add" -> {
                if (parent instanceof ObjectNode objectNode) {
                    objectNode.set(last, copyValue(operation.value()));
                } else if (parent instanceof ArrayNode arrayNode) {
                    if ("-".equals(last)) {
                        arrayNode.add(copyValue(operation.value()));
                    } else {
                        arrayNode.insert(Integer.parseInt(last), copyValue(operation.value()));
                    }
                } else {
                    throw new PlatformException(400, "Assistant patch parent is not writable.");
                }
            }
            case "replace" -> {
                if (parent instanceof ObjectNode objectNode) {
                    if (!objectNode.has(last)) {
                        throw new PlatformException(400,
                                "Assistant replace path does not exist: " + operation.path());
                    }
                    objectNode.set(last, copyValue(operation.value()));
                } else if (parent instanceof ArrayNode arrayNode) {
                    arrayNode.set(Integer.parseInt(last), copyValue(operation.value()));
                } else {
                    throw new PlatformException(400, "Assistant patch parent is not writable.");
                }
            }
            case "remove" -> {
                if (parent instanceof ObjectNode objectNode) {
                    objectNode.remove(last);
                } else if (parent instanceof ArrayNode arrayNode) {
                    arrayNode.remove(Integer.parseInt(last));
                } else {
                    throw new PlatformException(400, "Assistant patch parent is not writable.");
                }
            }
            default -> throw new PlatformException(400,
                    "Unsupported assistant patch op: " + operation.op());
        }
    }

    private String visualContainer(ObjectNode root) {
        if (root.has("diagram") && root.get("diagram").isObject()) {
            return "diagram";
        }
        if (root.has("graph") && root.get("graph").isObject()) {
            return "graph";
        }
        root.putObject("diagram");
        return "diagram";
    }

    private JsonNode parent(ObjectNode root, String[] segments, String op) {
        JsonNode current = root;
        for (int index = 0; index < segments.length - 1; index++) {
            String segment = unescapePointer(segments[index]);
            if (current instanceof ObjectNode objectNode) {
                JsonNode next = objectNode.get(segment);
                if (next == null || next.isNull()) {
                    if ("add".equals(op) && index == segments.length - 2) {
                        next = JsonNodeFactory.instance.arrayNode();
                        objectNode.set(segment, next);
                    } else {
                        throw new PlatformException(400,
                                "Assistant patch path does not exist: /" + segment);
                    }
                }
                current = next;
            } else if (current instanceof ArrayNode arrayNode) {
                current = arrayNode.get(Integer.parseInt(segment));
            } else {
                throw new PlatformException(400, "Assistant patch path is not traversable.");
            }
        }
        return current;
    }

    private JsonNode elementPayload(SemanticModelPatch.Operation operation) {
        ObjectNode node = JsonNodeFactory.instance.objectNode();
        node.put("id", safe(operation.targetElementId()));
        node.put("eClass", safe(operation.elementType()));
        node.put("name", safe(operation.elementType()));
        node.put("label", safe(operation.elementType()));
        if (operation.attributes() != null && operation.attributes().isObject()) {
            node.setAll((ObjectNode) operation.attributes());
        }
        return node;
    }

    private JsonNode diagramElementPayload(JsonNode element) {
        ObjectNode node = JsonNodeFactory.instance.objectNode();
        element.fields().forEachRemaining(entry -> {
            JsonNode value = entry.getValue();
            if (value == null || value.isArray() || value.isObject()) {
                return;
            }
            node.set(entry.getKey(), value.deepCopy());
        });
        if (!node.has("label") && node.has("name")) {
            node.set("label", node.get("name").deepCopy());
        }
        return node;
    }

    private JsonNode relationshipPayload(SemanticModelPatch.Operation operation) {
        ObjectNode node = JsonNodeFactory.instance.objectNode();
        node.put("id", safe(operation.sourceElementId()) + "-" + safe(operation.targetElementId())
                + "-" + safe(operation.referenceName()));
        node.put("kind", safe(operation.referenceName()));
        node.put("source", safe(operation.sourceElementId()));
        node.put("target", safe(operation.targetElementId()));
        return node;
    }

    private LocatedElement locateElement(JsonNode node, String id) {
        LocatedElement found = locateElement(node, id, "");
        if (found == null) {
            throw new PlatformException(404, "Assistant could not locate element: " + id);
        }
        return found;
    }

    private LocatedElement locateElement(JsonNode node, String id, String path) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isObject()) {
            ObjectNode object = (ObjectNode) node;
            if (id.equals(text(object.get("id")))) {
                return new LocatedElement(path.isBlank() ? "" : path, object);
            }
            var fields = object.fields();
            while (fields.hasNext()) {
                var entry = fields.next();
                LocatedElement found = locateElement(entry.getValue(), id,
                        path + "/" + escapePointer(entry.getKey()));
                if (found != null) {
                    return found;
                }
            }
            return null;
        }
        if (node.isArray()) {
            for (int index = 0; index < node.size(); index++) {
                LocatedElement found = locateElement(node.get(index), id,
                        path + "/" + index);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private Optional<String> semanticRootCollection(String elementType) {
        return Optional.ofNullable(SEMANTIC_ROOT_COLLECTIONS.get(safe(elementType)));
    }

    private JsonNode copyValue(JsonNode value) {
        return value == null ? JsonNodeFactory.instance.nullNode() : value.deepCopy();
    }

    private String escapePointer(String value) {
        return value.replace("~", "~0").replace("/", "~1");
    }

    private String unescapePointer(String value) {
        return value.replace("~1", "/").replace("~0", "~");
    }

    private String text(JsonNode node) {
        return node == null || node.isNull() ? "" : node.asText("");
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private static final Map<String, String> SEMANTIC_ROOT_COLLECTIONS = Map.ofEntries(
            Map.entry("ServerlessService", "services"),
            Map.entry("ServiceElementMembership", "serviceMemberships"),
            Map.entry("DeploymentUnit", "deploymentUnits"),
            Map.entry("Environment", "environments"),
            Map.entry("Schema", "schemas"),
            Map.entry("Function", "functions"),
            Map.entry("Api", "apis"),
            Map.entry("EventType", "eventTypes"),
            Map.entry("EventChannel", "channels"),
            Map.entry("Queue", "channels"),
            Map.entry("Topic", "channels"),
            Map.entry("EventBus", "channels"),
            Map.entry("Schedule", "schedules"),
            Map.entry("Trigger", "triggers"),
            Map.entry("DataStore", "dataStores"),
            Map.entry("ObjectStore", "objectStores"),
            Map.entry("DataAccess", "dataAccesses"),
            Map.entry("Workflow", "workflows"),
            Map.entry("HumanTask", "humanTasks"),
            Map.entry("ExternalEndpoint", "externalEndpoints"),
            Map.entry("ExternalAdapter", "externalAdapters"),
            Map.entry("IdentityProvider", "identityProviders"),
            Map.entry("Principal", "principals"),
            Map.entry("ArchitecturePolicy", "policies"),
            Map.entry("AuthPolicy", "policies"),
            Map.entry("AuthorizationPolicy", "policies"),
            Map.entry("BackupPolicy", "policies"),
            Map.entry("ConcurrencyPolicy", "policies"),
            Map.entry("CorsPolicy", "policies"),
            Map.entry("DataProtectionPolicy", "policies"),
            Map.entry("IdempotencyPolicy", "policies"),
            Map.entry("ObservabilityConfig", "policies"),
            Map.entry("RateLimitPolicy", "policies"),
            Map.entry("ResiliencePolicy", "policies"),
            Map.entry("RetentionPolicy", "policies"),
            Map.entry("SecurityPolicy", "policies"),
            Map.entry("TimeoutPolicy", "policies"),
            Map.entry("Flow", "flows"),
            Map.entry("ConfigurationSet", "configurations"),
            Map.entry("Secret", "secrets"),
            Map.entry("PlatformCapability", "platformCapabilities"),
            Map.entry("PlatformMappingAssessment", "platformMappingAssessments"));

    private record LocatedElement(String path, ObjectNode node) {
    }

    /**
     * Compiled patch bundle.
     *
     * @param patch            executable patch operations
     * @param inversePatch     inverse patch operations
     * @param affectedElements affected element IDs
     */
    public record CompiledPatch(List<ModelService.ModelPatchOperation> patch,
                                List<ModelService.ModelPatchOperation> inversePatch,
                                List<String> affectedElements) {

        public CompiledPatch {
            patch = patch == null ? List.of() : List.copyOf(patch);
            inversePatch = inversePatch == null ? List.of() : List.copyOf(inversePatch);
            affectedElements = affectedElements == null ? List.of() : List.copyOf(affectedElements);
        }
    }
}
