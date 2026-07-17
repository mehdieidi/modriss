package io.mehdieidi.varka.platform.assistant.patch;

import io.mehdieidi.varka.platform.assistant.domain.SemanticModelPatch;
import io.mehdieidi.varka.platform.kernel.PlatformException;
import io.mehdieidi.varka.platform.model.application.ModelService;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

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
   * @param semantic backend operation IR produced by validated agent tools
   * @return compiled patch
   */
  public CompiledPatch compile(JsonNode modelJson, SemanticModelPatch semantic) {
    ObjectNode root =
        modelJson == null || !modelJson.isObject()
            ? JsonNodeFactory.instance.objectNode()
            : (ObjectNode) modelJson.deepCopy();
    List<ModelService.ModelPatchOperation> patch = new ArrayList<>();
    List<ModelService.ModelPatchOperation> inverse = new ArrayList<>();
    List<String> affected = new ArrayList<>();
    String visualContainer = visualContainer(root);
    ensureVisualContainerPatch(modelJson, root, visualContainer, patch, inverse);
    io.mehdieidi.varka.platform.kernel.ModelLevel level =
        schemas.resolveLevel(
            root,
            semantic.operations().stream()
                .filter(java.util.Objects::nonNull)
                .map(SemanticModelPatch.Operation::elementType)
                .filter(java.util.Objects::nonNull)
                .findFirst()
                .orElse(root.path("eClass").asText("")));
    ArrayNode elements = root.withObject(visualContainer).withArray("elements");
    ArrayNode relationships = root.withObject(visualContainer).withArray("relationships");
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
            Optional<AssistantMetamodelSchemaService.ReferenceSchema> rootContainment =
                rootContainment(level, root, requestedOwner, operation);
            if (rootContainment.isPresent()) {
              addRootContainedElement(root, patch, inverse, rootContainment.get(), element);
            } else {
              Optional<ExistingPlacement> existingPlacement =
                  existingPlacement(level, root, operation);
              if (existingPlacement.isPresent()) {
                addContainedElement(
                    existingPlacement.get().owner(),
                    existingPlacement.get().containment(),
                    element,
                    patch,
                    inverse);
              } else {
                GenericPlacement placement =
                    genericPlacement(level, operation.elementType())
                        .orElseThrow(
                            () ->
                                new PlatformException(
                                    422,
                                    "Element type requires an explicit Ecore containment owner: "
                                        + operation.elementType()));
                ObjectNode owner =
                    JsonNodeFactory.instance
                        .objectNode()
                        .put("id", "__assistant_owner_" + placement.ownerType())
                        .put("eClass", placement.ownerType());
                addRootContainedElement(root, patch, inverse, placement.rootContainment(), owner);
                LocatedElement locatedOwner = locateElement(root, owner.path("id").asText());
                addContainedElement(locatedOwner, placement.containment(), element, patch, inverse);
              }
            }
          }
          if (isRelationshipElement(level, operation.elementType())) {
            // Relationship EClasses are semantic edge records. They are projected after their
            // source and target references have both been established.
            continue;
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
          LocatedElement target = locateElement(root, operation.targetElementId());
          AssistantMetamodelSchemaService.ReferenceSchema reference =
              schemas
                  .reference(
                      level, source.node().path("eClass").asText(), operation.referenceName())
                  .filter(value -> !value.containment() && !value.readonly())
                  .orElseThrow(
                      () ->
                          new PlatformException(
                              422, "Relationship reference is not writable in the metamodel."));
          if (!schemas.acceptsReferenceTarget(
              level,
              source.node().path("eClass").asText(),
              operation.referenceName(),
              target.node().path("eClass").asText())) {
            throw new PlatformException(422, "Relationship target is not valid for the reference.");
          }
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
          if (isRelationshipElement(level, source.node().path("eClass").asText())) {
            addRelationshipElementProjection(
                source, visualContainer, patch, inverse, affected, relationships);
            continue;
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
          String targetType = located.node().path("eClass").asText("");
          if (schemas.attribute(level, targetType, operation.referenceName()).isEmpty()) {
            throw new PlatformException(
                422, "Assistant attribute is not writable in the metamodel.");
          }
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
    ObjectNode root = prepareApplyRoot(modelJson, compiled);
    for (ModelService.ModelPatchOperation operation : compiled.patch()) {
      applyOperation(root, operation);
    }
    return root;
  }

  /**
   * Creates a writable model clone with visual containers initialized for incremental patch
   * previews.
   *
   * @param modelJson model JSON
   * @param compiled compiled patch whose visual containers may be touched
   * @return writable model clone
   */
  public ObjectNode prepareApplyRoot(JsonNode modelJson, CompiledPatch compiled) {
    ObjectNode root =
        modelJson == null || !modelJson.isObject()
            ? JsonNodeFactory.instance.objectNode()
            : (ObjectNode) modelJson.deepCopy();
    String visualContainer = visualContainer(root);
    root.withObject(visualContainer).withArray("elements");
    root.withObject(visualContainer).withArray("relationships");
    if (compiled != null) {
      compiled.patch().stream()
          .map(ModelService.ModelPatchOperation::path)
          .filter(java.util.Objects::nonNull)
          .filter(path -> path.startsWith("/diagram/") || path.startsWith("/graph/"))
          .map(path -> path.substring(1, path.indexOf('/', 1)))
          .distinct()
          .forEach(
              container -> {
                root.withObject(container).withArray("elements");
                root.withObject(container).withArray("relationships");
              });
    }
    return root;
  }

  /**
   * Applies one compiled operation to a writable model clone.
   *
   * @param root writable model clone
   * @param operation executable patch operation
   */
  public void applyOperation(ObjectNode root, ModelService.ModelPatchOperation operation) {
    apply(root, operation);
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

  private void ensureVisualContainerPatch(
      JsonNode original,
      ObjectNode root,
      String visualContainer,
      List<ModelService.ModelPatchOperation> patch,
      List<ModelService.ModelPatchOperation> inverse) {
    String containerPath = "/" + escapePointer(visualContainer);
    JsonNode originalContainer =
        original == null || !original.isObject() ? null : original.get(visualContainer);
    if (originalContainer == null || originalContainer.isNull() || !originalContainer.isObject()) {
      ObjectNode container = JsonNodeFactory.instance.objectNode();
      container.putArray("elements");
      container.putArray("relationships");
      patch.add(new ModelService.ModelPatchOperation("add", containerPath, container));
      inverse.add(new ModelService.ModelPatchOperation("remove", containerPath, null));
      root.set(visualContainer, container.deepCopy());
      return;
    }
    ObjectNode container = root.withObject(visualContainer);
    if (!originalContainer.has("elements") || !originalContainer.get("elements").isArray()) {
      patch.add(
          new ModelService.ModelPatchOperation(
              "add", containerPath + "/elements", JsonNodeFactory.instance.arrayNode()));
      inverse.add(
          new ModelService.ModelPatchOperation("remove", containerPath + "/elements", null));
      container.putArray("elements");
    }
    if (!originalContainer.has("relationships")
        || !originalContainer.get("relationships").isArray()) {
      patch.add(
          new ModelService.ModelPatchOperation(
              "add", containerPath + "/relationships", JsonNodeFactory.instance.arrayNode()));
      inverse.add(
          new ModelService.ModelPatchOperation("remove", containerPath + "/relationships", null));
      container.putArray("relationships");
    }
  }

  private Optional<AssistantMetamodelSchemaService.ReferenceSchema> rootContainment(
      io.mehdieidi.varka.platform.kernel.ModelLevel level,
      ObjectNode root,
      LocatedElement requestedOwner,
      SemanticModelPatch.Operation operation) {
    if (requestedOwner != null
        && requestedOwner.path().isBlank()
        && operation.referenceName() != null
        && operation.referenceName().matches("[A-Za-z][A-Za-z0-9_-]*")) {
      Optional<AssistantMetamodelSchemaService.ReferenceSchema> explicit =
          schemas
              .reference(level, root.path("eClass").asText(), operation.referenceName())
              .filter(AssistantMetamodelSchemaService.ReferenceSchema::containment);
      if (explicit.isPresent()) {
        schemas.requireContainment(
            level,
            root.path("eClass").asText(),
            operation.referenceName(),
            operation.elementType());
        return explicit;
      }
    }
    return schemas.rootContainment(level, operation.elementType());
  }

  /** Synthesizes one owner only when Ecore gives a unique concrete root-contained route. */
  private Optional<GenericPlacement> genericPlacement(
      io.mehdieidi.varka.platform.kernel.ModelLevel level, String childType) {
    List<GenericPlacement> candidates = new java.util.ArrayList<>();
    for (AssistantMetamodelSchemaService.TypeSchema owner : schemas.types(level)) {
      if (!owner.creatable()) continue;
      Optional<AssistantMetamodelSchemaService.ReferenceSchema> root =
          schemas.rootContainment(level, owner.name());
      if (root.isEmpty()) continue;
      for (AssistantMetamodelSchemaService.ReferenceSchema containment :
          schemas.containments(level, owner.name(), childType)) {
        candidates.add(new GenericPlacement(owner.name(), root.get(), containment));
      }
    }
    return candidates.size() == 1 ? Optional.of(candidates.get(0)) : Optional.empty();
  }

  private void addRootContainedElement(
      ObjectNode root,
      List<ModelService.ModelPatchOperation> patch,
      List<ModelService.ModelPatchOperation> inverse,
      AssistantMetamodelSchemaService.ReferenceSchema containment,
      JsonNode element) {
    String path = "/" + escapePointer(containment.name());
    JsonNode owned = root.get(containment.name());
    if (!containment.many()) {
      if (owned != null && !owned.isNull()) {
        throw new PlatformException(422, "Single-valued containment already has an element.");
      }
      patch.add(new ModelService.ModelPatchOperation("add", path, element));
      inverse.add(0, new ModelService.ModelPatchOperation("remove", path, null));
      root.set(containment.name(), element.deepCopy());
      return;
    }
    if (owned == null || owned.isNull()) {
      ArrayNode initial = JsonNodeFactory.instance.arrayNode().add(element.deepCopy());
      patch.add(new ModelService.ModelPatchOperation("add", path, initial));
      inverse.add(0, new ModelService.ModelPatchOperation("remove", path, null));
      root.set(containment.name(), initial.deepCopy());
      return;
    }
    if (!owned.isArray()) {
      throw new PlatformException(400, "Assistant root containment must target a collection.");
    }
    int index = owned.size();
    patch.add(new ModelService.ModelPatchOperation("add", path + "/-", element));
    inverse.add(0, new ModelService.ModelPatchOperation("remove", path + "/" + index, null));
    ((ArrayNode) owned).add(element.deepCopy());
  }

  /**
   * Finds the unambiguous existing semantic owner for an otherwise unplaced element.
   *
   * <p>The visual graph deliberately duplicates semantic elements. Searching only the semantic tree
   * prevents a canvas projection from becoming an owner, and avoids creating an artificial
   * container whenever the user is extending an existing model.
   */
  private Optional<ExistingPlacement> existingPlacement(
      io.mehdieidi.varka.platform.kernel.ModelLevel level,
      ObjectNode root,
      SemanticModelPatch.Operation operation) {
    List<ExistingPlacement> candidates = new ArrayList<>();
    collectExistingPlacements(level, root, "", operation.elementType(), candidates);
    return candidates.size() == 1 ? Optional.of(candidates.get(0)) : Optional.empty();
  }

  private void collectExistingPlacements(
      io.mehdieidi.varka.platform.kernel.ModelLevel level,
      JsonNode node,
      String path,
      String childType,
      List<ExistingPlacement> candidates) {
    if (node == null || node.isNull()) return;
    if (node.isObject()) {
      ObjectNode object = (ObjectNode) node;
      String ownerType = object.path("eClass").asText();
      if (!ownerType.isBlank() && !path.isBlank()) {
        for (AssistantMetamodelSchemaService.ReferenceSchema containment :
            schemas.containments(level, ownerType, childType)) {
          candidates.add(new ExistingPlacement(new LocatedElement(path, object), containment));
        }
      }
      var fields = object.properties().iterator();
      while (fields.hasNext()) {
        var entry = fields.next();
        if (path.isBlank()
            && ("diagram".equals(entry.getKey()) || "graph".equals(entry.getKey()))) {
          continue;
        }
        collectExistingPlacements(
            level,
            entry.getValue(),
            path + "/" + escapePointer(entry.getKey()),
            childType,
            candidates);
      }
      return;
    }
    if (node.isArray()) {
      for (int index = 0; index < node.size(); index++) {
        collectExistingPlacements(
            level, node.get(index), path + "/" + index, childType, candidates);
      }
    }
  }

  private void addContainedElement(
      LocatedElement owner,
      AssistantMetamodelSchemaService.ReferenceSchema containment,
      JsonNode element,
      List<ModelService.ModelPatchOperation> patch,
      List<ModelService.ModelPatchOperation> inverse) {
    String path = owner.path() + "/" + escapePointer(containment.name());
    JsonNode owned = owner.node().get(containment.name());
    if (!containment.many()) {
      if (owned != null && !owned.isNull())
        throw new PlatformException(422, "Single-valued containment already has an element.");
      patch.add(new ModelService.ModelPatchOperation("add", path, element));
      inverse.add(0, new ModelService.ModelPatchOperation("remove", path, null));
      owner.node().set(containment.name(), element.deepCopy());
    } else if (owned == null || owned.isNull()) {
      ArrayNode initial = JsonNodeFactory.instance.arrayNode().add(element.deepCopy());
      patch.add(new ModelService.ModelPatchOperation("add", path, initial));
      inverse.add(0, new ModelService.ModelPatchOperation("remove", path, null));
      owner.node().set(containment.name(), initial.deepCopy());
    } else if (owned.isArray()) {
      int index = owned.size();
      patch.add(new ModelService.ModelPatchOperation("add", path + "/-", element));
      inverse.add(0, new ModelService.ModelPatchOperation("remove", path + "/" + index, null));
      ((ArrayNode) owned).add(element.deepCopy());
    } else throw new PlatformException(422, "Containment feature is not a collection.");
  }

  private record ExistingPlacement(
      LocatedElement owner, AssistantMetamodelSchemaService.ReferenceSchema containment) {}

  private record GenericPlacement(
      String ownerType,
      AssistantMetamodelSchemaService.ReferenceSchema rootContainment,
      AssistantMetamodelSchemaService.ReferenceSchema containment) {}

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
      io.mehdieidi.varka.platform.kernel.ModelLevel level, SemanticModelPatch.Operation operation) {
    ObjectNode node = JsonNodeFactory.instance.objectNode();
    String elementType = schemas.canonicalType(level, operation.elementType());
    if (operation.attributes() != null && operation.attributes().isObject()) {
      ObjectNode attributes = ((ObjectNode) operation.attributes()).deepCopy();
      attributes.remove("id");
      attributes.remove("eClass");
      node.setAll(attributes);
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
        .properties()
        .forEach(
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
    node.put("id", java.util.UUID.randomUUID().toString());
    node.put("kind", safe(operation.referenceName()));
    node.put("source", safe(operation.sourceElementId()));
    node.put("target", safe(operation.targetElementId()));
    node.put("sourceElementId", safe(operation.sourceElementId()));
    node.put("targetElementId", safe(operation.targetElementId()));
    return node;
  }

  /** Projects a CVS-marked relationship EClass as a canvas edge once both endpoints exist. */
  private void addRelationshipElementProjection(
      LocatedElement relationshipElement,
      String visualContainer,
      List<ModelService.ModelPatchOperation> patch,
      List<ModelService.ModelPatchOperation> inverse,
      List<String> affected,
      ArrayNode relationships) {
    String sourceId = text(relationshipElement.node().get("source"));
    String targetId = text(relationshipElement.node().get("target"));
    if (sourceId.isBlank() || targetId.isBlank()) return;
    String relationshipId = text(relationshipElement.node().get("id"));
    if (relationshipId.isBlank()
        || java.util.stream.StreamSupport.stream(relationships.spliterator(), false)
            .anyMatch(existing -> relationshipId.equals(text(existing.get("id"))))) return;
    ObjectNode edge = JsonNodeFactory.instance.objectNode();
    edge.put("id", relationshipId);
    edge.put("kind", relationshipElement.node().path("eClass").asText("RELATIONSHIP"));
    edge.put("source", sourceId);
    edge.put("target", targetId);
    edge.put("sourceElementId", sourceId);
    edge.put("targetElementId", targetId);
    String label = text(relationshipElement.node().get("name"));
    if (!label.isBlank()) edge.put("label", label);
    patch.add(
        new ModelService.ModelPatchOperation(
            "add", "/" + visualContainer + "/relationships/-", edge));
    inverse.add(
        0,
        new ModelService.ModelPatchOperation(
            "remove", "/" + visualContainer + "/relationships/" + relationships.size(), null));
    relationships.add(edge.deepCopy());
    affected.add(relationshipId);
  }

  private boolean isRelationshipElement(
      io.mehdieidi.varka.platform.kernel.ModelLevel level, String type) {
    return schemas
        .typeSchema(level, type)
        .map(AssistantMetamodelSchemaService.TypeSchema::relationshipElement)
        .orElse(false);
  }

  private LocatedElement locateElement(JsonNode node, String id) {
    if (isRootAlias(node, id)) {
      return new LocatedElement("", (ObjectNode) node);
    }
    LocatedElement found = locateElement(node, id, "", true);
    if (found == null) {
      found = locateElement(node, id, "", false);
    }
    if (found == null) {
      throw new PlatformException(404, "Assistant could not locate element: " + id);
    }
    return found;
  }

  private boolean isRootAlias(JsonNode node, String id) {
    if (node == null || !node.isObject() || id == null || id.isBlank()) {
      return false;
    }
    String normalized = id.trim().toLowerCase(java.util.Locale.ROOT);
    String rootId = node.path("id").asText("").trim().toLowerCase(java.util.Locale.ROOT);
    String rootType = node.path("eClass").asText("").trim().toLowerCase(java.util.Locale.ROOT);
    String level = node.path("modelLevel").asText("").trim().toLowerCase(java.util.Locale.ROOT);
    return normalized.equals(rootId)
        || normalized.equals(rootType)
        || normalized.equals(level + "model")
        || normalized.equals("root")
        || normalized.equals("model")
        || normalized.equals("m1")
        || normalized.endsWith("-model");
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
      var fields = object.properties().iterator();
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

  private LocatedElement locateElement(
      JsonNode node, String id, String path, boolean skipVisualContainers) {
    if (node == null || node.isNull()) {
      return null;
    }
    if (node.isObject()) {
      ObjectNode object = (ObjectNode) node;
      if (id.equals(text(object.get("id")))) {
        return new LocatedElement(path.isBlank() ? "" : path, object);
      }
      var fields = object.properties().iterator();
      while (fields.hasNext()) {
        var entry = fields.next();
        if (skipVisualContainers
            && path.isBlank()
            && ("diagram".equals(entry.getKey()) || "graph".equals(entry.getKey()))) {
          continue;
        }
        LocatedElement found =
            locateElement(
                entry.getValue(),
                id,
                path + "/" + escapePointer(entry.getKey()),
                skipVisualContainers);
        if (found != null) {
          return found;
        }
      }
      return null;
    }
    if (node.isArray()) {
      for (int index = 0; index < node.size(); index++) {
        LocatedElement found =
            locateElement(node.get(index), id, path + "/" + index, skipVisualContainers);
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
