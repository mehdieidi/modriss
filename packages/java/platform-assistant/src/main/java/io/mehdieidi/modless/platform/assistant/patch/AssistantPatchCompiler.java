package io.mehdieidi.modless.platform.assistant.patch;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.mehdieidi.modless.platform.assistant.domain.SemanticModelPatch;
import io.mehdieidi.modless.platform.kernel.PlatformException;
import io.mehdieidi.modless.platform.model.application.ModelService;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Compiles semantic assistant operations into backend model patch operations. */
public class AssistantPatchCompiler {

  private final AssistantMetamodelSchemaService schemas;

  public AssistantPatchCompiler() {
    this(new AssistantMetamodelSchemaService());
  }

  public AssistantPatchCompiler(AssistantMetamodelSchemaService schemas) {
    this.schemas = schemas;
  }

  /**
   * Compiles semantic operations against a model snapshot.
   *
   * @param modelJson model JSON
   * @param semantic semantic patch
   * @return compiled patch
   */
  public CompiledPatch compile(JsonNode modelJson, SemanticModelPatch semantic) {
    ObjectNode root =
        modelJson == null || !modelJson.isObject()
            ? JsonNodeFactory.instance.objectNode()
            : (ObjectNode) modelJson.deepCopy();
    String visualContainer = visualContainer(root);
    io.mehdieidi.modless.platform.kernel.ModelLevel level =
        schemas.resolveLevel(
            root,
            semantic.operations().stream()
                .filter(java.util.Objects::nonNull)
                .map(SemanticModelPatch.Operation::elementType)
                .filter(java.util.Objects::nonNull)
                .findFirst()
                .orElse(root.path("eClass").asText("")));
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
          JsonNode element = elementPayload(level, operation);
          LocatedElement requestedOwner =
              operation.sourceElementId() == null || operation.sourceElementId().isBlank()
                  ? null
                  : locateElement(root, operation.sourceElementId());
          if (requestedOwner != null && !requestedOwner.path().isBlank()) {
            if (operation.referenceName() == null
                || !operation.referenceName().matches("[A-Za-z][A-Za-z0-9_-]*")) {
              throw new PlatformException(400, "Assistant containment reference is not allowed.");
            }
            String ownerType = requestedOwner.node().path("eClass").asText();
            schemas.requireContainment(
                level, ownerType, operation.referenceName(), operation.elementType());
            AssistantMetamodelSchemaService.ReferenceSchema containment =
                schemas
                    .reference(level, ownerType, operation.referenceName())
                    .filter(AssistantMetamodelSchemaService.ReferenceSchema::containment)
                    .orElseThrow(
                        () ->
                            new PlatformException(
                                422, "The requested containment is not in the metamodel."));
            JsonNode owned = requestedOwner.node().get(operation.referenceName());
            String collectionPath =
                requestedOwner.path() + "/" + escapePointer(operation.referenceName());
            if (!containment.many()) {
              if (owned != null && !owned.isNull()) {
                throw new PlatformException(
                    422, "Single-valued containment already has an element.");
              }
              patch.add(new ModelService.ModelPatchOperation("add", collectionPath, element));
              inverse.add(0, new ModelService.ModelPatchOperation("remove", collectionPath, null));
              requestedOwner.node().set(operation.referenceName(), element.deepCopy());
            } else if (owned == null || owned.isNull()) {
              ArrayNode initial = JsonNodeFactory.instance.arrayNode().add(element.deepCopy());
              patch.add(new ModelService.ModelPatchOperation("add", collectionPath, initial));
              inverse.add(0, new ModelService.ModelPatchOperation("remove", collectionPath, null));
              requestedOwner.node().set(operation.referenceName(), initial.deepCopy());
            } else if (owned.isArray()) {
              int index = owned.size();
              patch.add(
                  new ModelService.ModelPatchOperation("add", collectionPath + "/-", element));
              inverse.add(
                  0,
                  new ModelService.ModelPatchOperation(
                      "remove", collectionPath + "/" + index, null));
              ((ArrayNode) owned).add(element.deepCopy());
            } else {
              throw new PlatformException(
                  400, "Assistant containment reference must target a collection.");
            }
          } else {
            Optional<String> collection = schemas.rootCollection(level, operation.elementType());
            if (collection.isPresent()) {
              ArrayNode semanticElements = root.withArray(collection.get());
              patch.add(
                  new ModelService.ModelPatchOperation(
                      "add", "/" + collection.get() + "/-", element));
              inverse.add(
                  0,
                  new ModelService.ModelPatchOperation(
                      "remove", "/" + collection.get() + "/" + semanticElements.size(), null));
              semanticElements.add(element.deepCopy());
            } else {
              throw new PlatformException(
                  422,
                  "Element type requires a metamodel containment owner: "
                      + operation.elementType());
            }
          }
          patch.add(
              new ModelService.ModelPatchOperation(
                  "add", "/" + visualContainer + "/elements/-", diagramElementPayload(element)));
          affected.add(text(element.get("id")));
          inverse.add(
              0,
              new ModelService.ModelPatchOperation(
                  "remove", "/" + visualContainer + "/elements/" + elements.size(), null));
          elements.add(diagramElementPayload(element));
        }
        case CONNECT_ELEMENTS -> {
          LocatedElement source = locateElement(root, operation.sourceElementId());
          AssistantMetamodelSchemaService.ReferenceSchema reference =
              schemas
                  .reference(
                      level, source.node().path("eClass").asText(), operation.referenceName())
                  .filter(value -> !value.containment() && !value.readonly())
                  .orElseThrow(
                      () ->
                          new PlatformException(
                              422, "Relationship reference is not writable in the metamodel."));
          JsonNode previous = source.node().get(operation.referenceName());
          String referencePath = source.path() + "/" + escapePointer(operation.referenceName());
          JsonNode targetId = JsonNodeFactory.instance.textNode(operation.targetElementId());
          if (reference.many()) {
            if (previous != null
                && previous.isArray()
                && java.util.stream.StreamSupport.stream(previous.spliterator(), false)
                    .anyMatch(value -> operation.targetElementId().equals(value.asText()))) {
              continue;
            }
            if (previous == null || previous.isNull()) {
              ArrayNode initial = JsonNodeFactory.instance.arrayNode().add(targetId);
              patch.add(new ModelService.ModelPatchOperation("add", referencePath, initial));
              inverse.add(0, new ModelService.ModelPatchOperation("remove", referencePath, null));
              source.node().set(operation.referenceName(), initial.deepCopy());
            } else if (previous.isArray()) {
              int referenceIndex = previous.size();
              patch.add(
                  new ModelService.ModelPatchOperation("add", referencePath + "/-", targetId));
              inverse.add(
                  0,
                  new ModelService.ModelPatchOperation(
                      "remove", referencePath + "/" + referenceIndex, null));
              ((ArrayNode) previous).add(targetId);
            } else {
              throw new PlatformException(422, "Multi-valued relationship is not an array.");
            }
          } else {
            if (operation.targetElementId().equals(text(previous))) {
              continue;
            }
            patch.add(
                new ModelService.ModelPatchOperation(
                    previous == null ? "add" : "replace", referencePath, targetId));
            inverse.add(
                0,
                new ModelService.ModelPatchOperation(
                    previous == null ? "remove" : "replace", referencePath, previous));
            source.node().set(operation.referenceName(), targetId);
          }
          JsonNode relationship = relationshipPayload(operation);
          patch.add(
              new ModelService.ModelPatchOperation(
                  "add", "/" + visualContainer + "/relationships/-", relationship));
          affected.add(text(relationship.get("id")));
          inverse.add(
              0,
              new ModelService.ModelPatchOperation(
                  "remove",
                  "/" + visualContainer + "/relationships/" + relationships.size(),
                  null));
          relationships.add(relationship.deepCopy());
        }
        case SET_ATTRIBUTE -> {
          if (operation.referenceName() == null
              || !operation.referenceName().matches("[A-Za-z][A-Za-z0-9_-]*")) {
            throw new PlatformException(400, "Assistant attribute name is not allowed.");
          }
          LocatedElement located = locateElement(root, operation.targetElementId());
          JsonNode previous = located.node().get(operation.referenceName());
          if (java.util.Objects.equals(previous, operation.attributes())) {
            continue;
          }
          String path = located.path() + "/" + escapePointer(operation.referenceName());
          patch.add(
              new ModelService.ModelPatchOperation(
                  previous == null ? "add" : "replace", path, operation.attributes()));
          inverse.add(
              new ModelService.ModelPatchOperation(
                  previous == null ? "remove" : "replace", path, previous));
          affected.add(operation.targetElementId());
        }
        case DELETE_ELEMENT -> {
          List<LocatedElement> located =
              locateDeletedElements(root, operation.targetElementId()).stream()
                  .sorted(this::compareForRemoval)
                  .toList();
          if (located.isEmpty()) {
            throw new PlatformException(
                404, "Assistant could not locate element: " + operation.targetElementId());
          }
          located.forEach(
              element ->
                  patch.add(new ModelService.ModelPatchOperation("remove", element.path(), null)));
          located.stream()
              .sorted(this::compareForRestore)
              .forEach(
                  element ->
                      inverse.add(
                          new ModelService.ModelPatchOperation(
                              "add", element.path(), element.node().deepCopy())));
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
   * @param compiled compiled patch
   * @return patched model
   */
  public ObjectNode apply(JsonNode modelJson, CompiledPatch compiled) {
    ObjectNode root =
        modelJson == null || !modelJson.isObject()
            ? JsonNodeFactory.instance.objectNode()
            : (ObjectNode) modelJson.deepCopy();
    String visualContainer = visualContainer(root);
    root.with(visualContainer).withArray("elements");
    root.with(visualContainer).withArray("relationships");
    compiled.patch().stream()
        .map(ModelService.ModelPatchOperation::path)
        .filter(java.util.Objects::nonNull)
        .filter(path -> path.startsWith("/diagram/") || path.startsWith("/graph/"))
        .map(path -> path.substring(1, path.indexOf('/', 1)))
        .distinct()
        .forEach(
            container -> {
              root.with(container).withArray("elements");
              root.with(container).withArray("relationships");
            });
    for (ModelService.ModelPatchOperation operation : compiled.patch()) {
      apply(root, operation);
    }
    return root;
  }

  /** Drops no-op visual removals when persistence normalized away an optional canvas container. */
  public CompiledPatch adaptToSnapshot(JsonNode modelJson, CompiledPatch compiled) {
    List<ModelService.ModelPatchOperation> applicable =
        compiled.patch().stream()
            .filter(operation -> !missingOptionalVisualRemoval(modelJson, operation))
            .toList();
    return new CompiledPatch(applicable, compiled.inversePatch(), compiled.affectedElements());
  }

  private boolean missingOptionalVisualRemoval(
      JsonNode modelJson, ModelService.ModelPatchOperation operation) {
    if (operation == null
        || !"remove".equals(operation.op())
        || operation.path() == null
        || !(operation.path().startsWith("/diagram/") || operation.path().startsWith("/graph/"))) {
      return false;
    }
    int separator = operation.path().lastIndexOf('/');
    JsonNode parent = modelJson.at(operation.path().substring(0, separator));
    if (parent.isMissingNode()) {
      return true;
    }
    String leaf = unescapePointer(operation.path().substring(separator + 1));
    if (parent.isArray()) {
      try {
        return Integer.parseInt(leaf) >= parent.size();
      } catch (NumberFormatException ignored) {
        return true;
      }
    }
    return parent.isObject() && !parent.has(leaf);
  }

  private void apply(ObjectNode root, ModelService.ModelPatchOperation operation) {
    if (operation == null
        || operation.op() == null
        || operation.path() == null
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
            throw new PlatformException(
                400, "Assistant replace path does not exist: " + operation.path());
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
      default ->
          throw new PlatformException(400, "Unsupported assistant patch op: " + operation.op());
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
            throw new PlatformException(400, "Assistant patch path does not exist: /" + segment);
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

  private JsonNode elementPayload(
      io.mehdieidi.modless.platform.kernel.ModelLevel level,
      SemanticModelPatch.Operation operation) {
    ObjectNode node = JsonNodeFactory.instance.objectNode();
    String elementType = schemas.canonicalType(level, operation.elementType());
    if (operation.attributes() != null && operation.attributes().isObject()) {
      node.setAll((ObjectNode) operation.attributes());
    }
    node.put("id", safe(operation.targetElementId()));
    node.put("eClass", elementType);
    if (!node.hasNonNull("name")) {
      node.put("name", elementType);
    }
    if (!node.hasNonNull("label")) {
      node.set("label", node.get("name").deepCopy());
    }
    return node;
  }

  private JsonNode diagramElementPayload(JsonNode element) {
    ObjectNode node = JsonNodeFactory.instance.objectNode();
    element
        .fields()
        .forEachRemaining(
            entry -> {
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
    node.put(
        "id",
        safe(operation.sourceElementId())
            + "-"
            + safe(operation.targetElementId())
            + "-"
            + safe(operation.referenceName()));
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

  private List<LocatedElement> locateDeletedElements(JsonNode node, String id) {
    List<LocatedElement> found = new ArrayList<>();
    locateDeletedElements(node, id, "", found);
    return found.stream()
        .collect(
            java.util.stream.Collectors.toMap(
                LocatedElement::path,
                element -> element,
                (first, ignored) -> first,
                java.util.LinkedHashMap::new))
        .values()
        .stream()
        .toList();
  }

  private void locateDeletedElements(
      JsonNode node, String id, String path, List<LocatedElement> found) {
    if (node == null || node.isNull()) {
      return;
    }
    if (node.isObject()) {
      ObjectNode object = (ObjectNode) node;
      if (id.equals(text(object.get("id")))
          || id.equals(text(object.get("source")))
          || id.equals(text(object.get("target")))) {
        found.add(new LocatedElement(path, object));
        return;
      }
      var fields = object.fields();
      while (fields.hasNext()) {
        var entry = fields.next();
        locateDeletedElements(
            entry.getValue(), id, path + "/" + escapePointer(entry.getKey()), found);
      }
      return;
    }
    if (node.isArray()) {
      for (int index = 0; index < node.size(); index++) {
        locateDeletedElements(node.get(index), id, path + "/" + index, found);
      }
    }
  }

  private int compareForRemoval(LocatedElement left, LocatedElement right) {
    String leftParent = parentPath(left.path());
    String rightParent = parentPath(right.path());
    if (leftParent.equals(rightParent)) {
      return Integer.compare(lastIndex(right.path()), lastIndex(left.path()));
    }
    return Integer.compare(right.path().length(), left.path().length());
  }

  private int compareForRestore(LocatedElement left, LocatedElement right) {
    String leftParent = parentPath(left.path());
    String rightParent = parentPath(right.path());
    if (leftParent.equals(rightParent)) {
      return Integer.compare(lastIndex(left.path()), lastIndex(right.path()));
    }
    return Integer.compare(left.path().length(), right.path().length());
  }

  private String parentPath(String path) {
    int separator = path.lastIndexOf('/');
    return separator < 0 ? "" : path.substring(0, separator);
  }

  private int lastIndex(String path) {
    int separator = path.lastIndexOf('/');
    if (separator < 0) {
      return -1;
    }
    try {
      return Integer.parseInt(path.substring(separator + 1));
    } catch (NumberFormatException ignored) {
      return -1;
    }
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
        LocatedElement found =
            locateElement(entry.getValue(), id, path + "/" + escapePointer(entry.getKey()));
        if (found != null) {
          return found;
        }
      }
      return null;
    }
    if (node.isArray()) {
      for (int index = 0; index < node.size(); index++) {
        LocatedElement found = locateElement(node.get(index), id, path + "/" + index);
        if (found != null) {
          return found;
        }
      }
    }
    return null;
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

  private record LocatedElement(String path, ObjectNode node) {}

  /**
   * Compiled patch bundle.
   *
   * @param patch executable patch operations
   * @param inversePatch inverse patch operations
   * @param affectedElements affected element IDs
   */
  public record CompiledPatch(
      List<ModelService.ModelPatchOperation> patch,
      List<ModelService.ModelPatchOperation> inversePatch,
      List<String> affectedElements) {

    public CompiledPatch {
      patch = patch == null ? List.of() : List.copyOf(patch);
      inversePatch = inversePatch == null ? List.of() : List.copyOf(inversePatch);
      affectedElements = affectedElements == null ? List.of() : List.copyOf(affectedElements);
    }
  }
}
