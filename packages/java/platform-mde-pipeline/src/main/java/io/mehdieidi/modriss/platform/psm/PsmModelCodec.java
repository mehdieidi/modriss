package io.mehdieidi.modriss.platform.psm;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.eclipse.emf.ecore.*;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.xmi.XMLResource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/** Ecore-driven semantic JSON bridge used by the ordinary workbench graph editor. */
final class PsmModelCodec {
  private static final ObjectMapper JSON = new ObjectMapper();
  private final List<EClass> types;
  private final Map<EObject, String> ids = new IdentityHashMap<>();
  private final Map<String, EObject> objects = new LinkedHashMap<>();
  private final List<Runnable> references = new ArrayList<>();

  PsmModelCodec(List<EPackage> packages) {
    types =
        packages.stream()
            .flatMap(p -> p.getEClassifiers().stream())
            .filter(EClass.class::isInstance)
            .map(EClass.class::cast)
            .toList();
  }

  String typeKey(EClass type) {
    return types.stream().filter(t -> t.getName().equals(type.getName())).count() == 1
        ? type.getName()
        : type.getEPackage().getNsURI() + "#" + type.getName();
  }

  static String featureKey(EStructuralFeature feature) {
    return java.util.Set.of(
                "id",
                "eClass",
                "graph",
                "views",
                "fragments",
                "diagram",
                "activeViewId",
                "traceLinks",
                "manualBacklog",
                "validationIssues",
                "_emfId",
                "_resourceRoots",
                "x",
                "y",
                "label",
                "status",
                "tags",
                "visualOnly",
                "bundle",
                "countsByKind",
                "underlyingRelationshipIds",
                "sourceType",
                "targetType",
                "semanticFeature",
                "semanticSourceElementId",
                "semanticTargetElementId",
                "semanticDirection",
                "rootFeature",
                "__ownerId",
                "__containmentFeature",
                "kind",
                "lifecycleStatus")
            .contains(feature.getName())
        ? "_semantic_" + feature.getName()
        : feature.getName();
  }

  ObjectNode read(Resource resource) {
    ids.clear();
    for (EObject root : resource.getContents()) {
      identify(root, resource);
      root.eAllContents().forEachRemaining(object -> identify(object, resource));
    }
    ObjectNode root = object(resource.getContents().get(0));
    if (resource.getContents().size() > 1) {
      var additional = root.putArray("_resourceRoots");
      resource
          .getContents()
          .subList(1, resource.getContents().size())
          .forEach(value -> additional.add(object(value)));
    }
    return root;
  }

  private void identify(EObject object, Resource resource) {
    String id = resource instanceof XMLResource xml ? xml.getID(object) : null;
    if (id == null || id.isBlank()) id = "psm-" + java.util.UUID.randomUUID();
    if (resource instanceof XMLResource xml) xml.setID(object, id);
    ids.put(object, id);
  }

  private ObjectNode object(EObject object) {
    var node =
        JSON.createObjectNode().put("eClass", typeKey(object.eClass())).put("id", ids.get(object));
    node.put("_emfId", ids.get(object));
    for (EStructuralFeature feature : object.eClass().getEAllStructuralFeatures()) {
      if (feature.isDerived() || feature.isTransient() || !object.eIsSet(feature)) continue;
      Object value = object.eGet(feature);
      if (feature.isMany()) {
        var values = node.putArray(featureKey(feature));
        for (Object item : (List<?>) value) values.add(value(feature, item));
      } else node.set(featureKey(feature), value(feature, value));
    }
    // The workbench identity is separate from a language's optional id attribute.
    node.put("id", ids.get(object));
    return node;
  }

  private JsonNode value(EStructuralFeature feature, Object value) {
    if (feature instanceof EReference reference) {
      if (!reference.isContainment() && !ids.containsKey((EObject) value))
        throw new IllegalArgumentException(
            "References must target objects in this model resource.");
      return reference.isContainment()
          ? object((EObject) value)
          : JSON.createObjectNode().put("$ref", ids.get((EObject) value));
    }
    if (value instanceof Number || value instanceof Boolean) return JSON.valueToTree(value);
    EDataType type = (EDataType) feature.getEType();
    return JSON.getNodeFactory()
        .stringNode(type.getEPackage().getEFactoryInstance().convertToString(type, value));
  }

  void write(Resource resource, JsonNode model) {
    if (model == null || !model.isObject())
      throw new IllegalArgumentException("Supply a semantic model object.");
    resource.getContents().clear();
    resource.getContents().add(create(resource, model, null));
    for (JsonNode root : model.path("_resourceRoots"))
      resource.getContents().add(create(resource, root, null));
    references.forEach(Runnable::run);
  }

  @SuppressWarnings("unchecked")
  private EObject create(Resource resource, JsonNode node, EClass expected) {
    String key = node.path("eClass").asText();
    EClass type =
        key.isBlank()
            ? expected
            : types.stream().filter(t -> typeKey(t).equals(key)).findFirst().orElse(null);
    if (type == null
        || type.isAbstract()
        || type.isInterface()
        || (expected != null && !expected.isSuperTypeOf(type))) {
      throw new IllegalArgumentException("Unknown or incompatible EClass: " + key);
    }
    EObject object = type.getEPackage().getEFactoryInstance().create(type);
    String id = node.path("_emfId").asText(node.path("id").asText());
    if (id.isBlank() || objects.putIfAbsent(id, object) != null)
      throw new IllegalArgumentException("Every model element needs a unique id.");
    ((XMLResource) resource).setID(object, id);
    for (EStructuralFeature feature : type.getEAllStructuralFeatures()) {
      if (feature.isDerived() || !feature.isChangeable() || feature.isTransient()) continue;
      JsonNode value = node.get(featureKey(feature));
      if (value == null || value.isNull()) continue;
      if (feature instanceof EReference reference && !reference.isContainment()) {
        references.add(
            () -> {
              if (feature.isMany()) {
                for (JsonNode item : value)
                  ((List<Object>) object.eGet(feature)).add(resolve(item));
              } else object.eSet(feature, resolve(value));
            });
      } else if (feature.isMany()) {
        if (!value.isArray())
          throw new IllegalArgumentException("Expected a list for " + feature.getName());
        for (JsonNode item : value)
          ((List<Object>) object.eGet(feature)).add(convert(resource, feature, item));
      } else object.eSet(feature, convert(resource, feature, value));
    }
    return object;
  }

  private Object convert(Resource resource, EStructuralFeature feature, JsonNode value) {
    if (feature instanceof EReference reference)
      return create(resource, value, reference.getEReferenceType());
    EDataType type = (EDataType) feature.getEType();
    return type.getEPackage().getEFactoryInstance().createFromString(type, value.asText());
  }

  private EObject resolve(JsonNode value) {
    String id = value.isObject() ? value.path("$ref").asText() : value.asText();
    EObject target = objects.get(id);
    if (target == null) throw new IllegalArgumentException("Unresolved model reference: " + id);
    return target;
  }
}
