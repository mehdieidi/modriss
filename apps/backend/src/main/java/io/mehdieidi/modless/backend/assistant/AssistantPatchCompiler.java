package io.mehdieidi.modless.backend.assistant;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.mehdieidi.modless.platform.core.PlatformException;
import io.mehdieidi.modless.platform.core.service.ModelService;
import java.util.ArrayList;
import java.util.List;
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
        if (!root.has("diagram") || !root.get("diagram").isObject()) {
            root.putObject("diagram");
        }
        ArrayNode elements = root.with("diagram").withArray("elements");
        ArrayNode relationships = root.with("diagram").withArray("relationships");
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
                    patch.add(new ModelService.ModelPatchOperation("add", "/diagram/elements/-",
                            element));
                    affected.add(text(element.get("id")));
                    inverse.add(new ModelService.ModelPatchOperation("remove",
                            "/diagram/elements/" + elements.size(), null));
                }
                case CONNECT_ELEMENTS -> {
                    JsonNode relationship = relationshipPayload(operation);
                    patch.add(new ModelService.ModelPatchOperation("add",
                            "/diagram/relationships/-", relationship));
                    affected.add(text(relationship.get("id")));
                    inverse.add(new ModelService.ModelPatchOperation("remove",
                            "/diagram/relationships/" + relationships.size(), null));
                }
                case SET_ATTRIBUTE -> {
                    if (operation.referenceName() == null
                            || !operation.referenceName().matches("[A-Za-z][A-Za-z0-9_-]*")) {
                        throw new PlatformException(400,
                                "Assistant attribute name is not allowed.");
                    }
                    int index = elementIndex(elements, operation.targetElementId());
                    JsonNode previous = elements.get(index).get(operation.referenceName());
                    patch.add(new ModelService.ModelPatchOperation("replace",
                            "/diagram/elements/" + index + "/" + operation.referenceName(),
                            operation.attributes()));
                    inverse.add(new ModelService.ModelPatchOperation(previous == null
                            ? "remove" : "replace",
                            "/diagram/elements/" + index + "/" + operation.referenceName(),
                            previous));
                    affected.add(operation.targetElementId());
                }
                case DELETE_ELEMENT -> {
                    int index = elementIndex(elements, operation.targetElementId());
                    ObjectNode snapshot = elements.get(index).deepCopy();
                    patch.add(new ModelService.ModelPatchOperation("remove",
                            "/diagram/elements/" + index, null));
                    inverse.add(new ModelService.ModelPatchOperation("add",
                            "/diagram/elements/" + index, snapshot));
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
        if (!root.has("diagram") || !root.get("diagram").isObject()) {
            root.putObject("diagram");
        }
        ArrayNode elements = root.with("diagram").withArray("elements");
        ArrayNode relationships = root.with("diagram").withArray("relationships");
        for (ModelService.ModelPatchOperation operation : compiled.patch()) {
            apply(elements, relationships, operation);
        }
        return root;
    }

    private void apply(ArrayNode elements, ArrayNode relationships,
            ModelService.ModelPatchOperation operation) {
        String path = Optional.ofNullable(operation.path()).orElse("");
        if (path.startsWith("/diagram/elements/")) {
            applyElementPatch(elements, operation);
            return;
        }
        if (path.startsWith("/diagram/relationships/")) {
            applyRelationshipPatch(relationships, operation);
            return;
        }
        throw new PlatformException(400, "Unsupported assistant patch path: " + path);
    }

    private void applyElementPatch(ArrayNode array,
            ModelService.ModelPatchOperation operation) {
        String[] segments = operation.path().split("/");
        if (segments.length == 4) {
            applyArrayPatch(array, operation);
            return;
        }
        int index = Integer.parseInt(segments[3]);
        ObjectNode element = (ObjectNode) array.get(index);
        String field = segments[4];
        switch (operation.op()) {
            case "add", "replace" -> element.set(field, operation.value());
            case "remove" -> element.remove(field);
            default -> throw new PlatformException(400,
                    "Unsupported assistant patch op: " + operation.op());
        }
    }

    private void applyRelationshipPatch(ArrayNode array,
            ModelService.ModelPatchOperation operation) {
        applyArrayPatch(array, operation);
    }

    private void applyArrayPatch(ArrayNode array, ModelService.ModelPatchOperation operation) {
        String[] segments = operation.path().split("/");
        String last = segments[segments.length - 1];
        switch (operation.op()) {
            case "add" -> {
                if ("-".equals(last)) {
                    array.add(operation.value());
                } else {
                    array.insert(Integer.parseInt(last), operation.value());
                }
            }
            case "replace" -> array.set(Integer.parseInt(last), operation.value());
            case "remove" -> array.remove(Integer.parseInt(last));
            default -> throw new PlatformException(400,
                    "Unsupported assistant patch op: " + operation.op());
        }
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

    private JsonNode relationshipPayload(SemanticModelPatch.Operation operation) {
        ObjectNode node = JsonNodeFactory.instance.objectNode();
        node.put("id", safe(operation.sourceElementId()) + "-" + safe(operation.targetElementId())
                + "-" + safe(operation.referenceName()));
        node.put("kind", safe(operation.referenceName()));
        node.put("source", safe(operation.sourceElementId()));
        node.put("target", safe(operation.targetElementId()));
        return node;
    }

    private int elementIndex(ArrayNode elements, String id) {
        for (int index = 0; index < elements.size(); index++) {
            if (id.equals(text(elements.get(index).get("id")))) {
                return index;
            }
        }
        throw new PlatformException(404, "Assistant could not locate element: " + id);
    }

    private String text(JsonNode node) {
        return node == null || node.isNull() ? "" : node.asText("");
    }

    private String safe(String value) {
        return value == null ? "" : value;
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
