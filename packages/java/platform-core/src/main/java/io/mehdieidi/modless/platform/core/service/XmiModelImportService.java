package io.mehdieidi.modless.platform.core.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.mehdieidi.modless.platform.core.PlatformException;
import io.mehdieidi.modless.platform.core.model.ModelLevel;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.eclipse.emf.common.util.Enumerator;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EAttribute;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EClassifier;
import org.eclipse.emf.ecore.EDataType;
import org.eclipse.emf.ecore.EEnum;
import org.eclipse.emf.ecore.EEnumLiteral;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.EReference;
import org.eclipse.emf.ecore.EStructuralFeature;
import org.eclipse.emf.ecore.EcorePackage;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.eclipse.emf.ecore.xmi.impl.EcoreResourceFactoryImpl;
import org.eclipse.emf.ecore.xmi.impl.XMIResourceFactoryImpl;

final class XmiModelImportService {

    private static final Map<ModelLevel, String> METAMODEL_RESOURCE_BY_LEVEL = Map.of(
            ModelLevel.CIM, "modeling/metamodels/cim/cim-combined.ecore",
            ModelLevel.PIM, "modeling/metamodels/pim/pim-combined.ecore",
            ModelLevel.PSM, "modeling/metamodels/psm/psm-combined.ecore");

    private final ObjectMapper objectMapper;

    XmiModelImportService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    JsonNode importModel(ModelLevel level, byte[] bytes) {
        try {
            ResourceSet resourceSet = newResourceSet();
            registerMetamodel(resourceSet, level);
            Resource resource = resourceSet.createResource(URI.createURI(
                    "memory:/import-" + level.apiName() + ".xmi"));
            try (ByteArrayInputStream input = new ByteArrayInputStream(bytes)) {
                resource.load(input, Map.of());
            }
            EcoreUtil.resolveAll(resourceSet);
            EObject root = resource.getContents().stream()
                    .filter(EObject.class::isInstance)
                    .map(EObject.class::cast)
                    .findFirst()
                    .orElseThrow(() -> new PlatformException(400,
                            "Uploaded XMI does not contain a model root."));
            SerializationContext context = new SerializationContext();
            ObjectNode rootJson = serializeContainedObject(root, context);
            rootJson.set("graph", context.graphNode(objectMapper));
            rootJson.set("diagram", context.diagramNode(objectMapper));
            return rootJson;
        } catch (PlatformException ex) {
            throw ex;
        } catch (Exception ex) {
            String detail = ex.getMessage() == null || ex.getMessage().isBlank()
                    ? ex.getClass().getSimpleName()
                    : ex.getMessage();
            throw new PlatformException(400, "Uploaded model is not valid XMI: " + detail);
        }
    }

    byte[] exportModel(ModelLevel level, JsonNode modelJson) {
        try {
            ResourceSet resourceSet = newResourceSet();
            registerMetamodel(resourceSet, level);
            Resource resource = resourceSet.createResource(URI.createURI(
                    "memory:/export-" + level.apiName() + ".xmi"));
            ExportContext context = new ExportContext(resourceSet);
            EObject root = context.createContainedObject(modelJson, null);
            if (root == null) {
                throw new PlatformException(400, "Model JSON does not contain a valid root.");
            }
            resource.getContents().add(root);
            context.resolveReferences();
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            resource.save(output, Map.of());
            return output.toByteArray();
        } catch (PlatformException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new PlatformException(400, "Model JSON cannot be exported as XMI: "
                    + (ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage()));
        }
    }

    private ResourceSet newResourceSet() {
        Resource.Factory.Registry.INSTANCE.getExtensionToFactoryMap()
                .put("ecore", new EcoreResourceFactoryImpl());
        Resource.Factory.Registry.INSTANCE.getExtensionToFactoryMap()
                .put("xmi", new XMIResourceFactoryImpl());

        ResourceSet resourceSet = new ResourceSetImpl();
        resourceSet.getResourceFactoryRegistry().getExtensionToFactoryMap()
                .put("ecore", new EcoreResourceFactoryImpl());
        resourceSet.getResourceFactoryRegistry().getExtensionToFactoryMap()
                .put("xmi", new XMIResourceFactoryImpl());
        resourceSet.getPackageRegistry().put(EcorePackage.eNS_URI, EcorePackage.eINSTANCE);
        return resourceSet;
    }

    private void registerMetamodel(ResourceSet resourceSet, ModelLevel level) throws IOException {
        String resourcePath = METAMODEL_RESOURCE_BY_LEVEL.get(level);
        if (resourcePath == null) {
            throw new PlatformException(400, "Unsupported model level for XMI import.");
        }
        try (InputStream stream = getClass().getClassLoader().getResourceAsStream(resourcePath)) {
            if (stream == null) {
                throw new PlatformException(500, "Could not load metamodel for XMI import.");
            }
            Resource ecore = resourceSet.createResource(URI.createURI("memory:/" + level.apiName()
                    + "-metamodel.ecore"));
            ecore.load(stream, Map.of());
            ecore.getContents().stream()
                    .filter(EPackage.class::isInstance)
                    .map(EPackage.class::cast)
                    .forEach(pkg -> registerPackage(resourceSet, pkg));
        }
    }

    private void registerPackage(ResourceSet resourceSet, EPackage ePackage) {
        if (ePackage.getNsURI() != null && !ePackage.getNsURI().isBlank()) {
            resourceSet.getPackageRegistry().put(ePackage.getNsURI(), ePackage);
        }
        ePackage.getESubpackages().forEach(child -> registerPackage(resourceSet, child));
    }

    private String scalarText(JsonNode value) {
        if (value == null || value.isNull() || value.isContainerNode()) {
            return "";
        }
        return value.asText("").trim();
    }

    private ObjectNode serializeContainedObject(EObject object, SerializationContext context) {
        if (!context.beginSerialization(object)) {
            return null;
        }
        try {
            ObjectNode node = objectMapper.createObjectNode();
            String objectId = context.ensureId(object);
            node.put("eClass", object.eClass().getName());
            node.put("id", objectId);

            for (EStructuralFeature feature : object.eClass().getEAllStructuralFeatures()) {
                if (feature instanceof EAttribute attribute) {
                    if (!object.eIsSet(feature)) {
                        continue;
                    }
                    JsonNode value = attributeValueNode(object, attribute);
                    if (value != null) {
                        node.set(attribute.getName(), value);
                    }
                    continue;
                }
                if (!(feature instanceof EReference reference)) {
                    continue;
                }
                if (reference.isContainer() || reference.isContainment()) {
                    JsonNode value = containmentValueNode(object, reference, context);
                    if (value != null) {
                        node.set(reference.getName(), value);
                    }
                    continue;
                }
                JsonNode value = referenceValueNode(object, reference, context);
                if (value != null) {
                    node.set(reference.getName(), value);
                }
            }

            context.captureGraphObject(object, node);
            return node;
        } finally {
            context.endSerialization(object);
        }
    }

    private JsonNode containmentValueNode(EObject owner, EReference reference,
            SerializationContext context) {
        Object rawValue = owner.eGet(reference);
        if (reference.isMany()) {
            List<?> values = rawValue instanceof List<?> list ? list : List.of();
            ArrayNode array = objectMapper.createArrayNode();
            for (Object value : values) {
                if (value instanceof EObject child) {
                    ObjectNode serializedChild = serializeContainedObject(child, context);
                    if (serializedChild != null) {
                        array.add(serializedChild);
                    }
                }
            }
            return array;
        }
        if (rawValue instanceof EObject child) {
            return serializeContainedObject(child, context);
        }
        return null;
    }

    private JsonNode attributeValueNode(EObject owner, EAttribute attribute) {
        Object rawValue = owner.eGet(attribute);
        if (attribute.isMany()) {
            List<?> values = rawValue instanceof List<?> list ? list : List.of();
            ArrayNode array = objectMapper.createArrayNode();
            values.stream().map(this::normalizePrimitive)
                    .forEach(value -> array.add(objectMapper.valueToTree(value)));
            return array;
        }
        return objectMapper.valueToTree(normalizePrimitive(rawValue));
    }

    private JsonNode referenceValueNode(EObject owner, EReference reference,
            SerializationContext context) {
        Object rawValue = owner.eGet(reference);
        if (reference.isMany()) {
            List<?> values = rawValue instanceof List<?> list ? list : List.of();
            ArrayNode array = objectMapper.createArrayNode();
            for (Object value : values) {
                if (value instanceof EObject target) {
                    array.add(context.ensureId(target));
                }
            }
            return array;
        }
        if (rawValue instanceof EObject target) {
            return objectMapper.valueToTree(context.ensureId(target));
        }
        return null;
    }

    private Object normalizePrimitive(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Date date) {
            return Instant.ofEpochMilli(date.getTime()).toString();
        }
        if (value instanceof Enumerator enumerator) {
            return enumerator.getLiteral() != null ? enumerator.getLiteral() : enumerator.getName();
        }
        return value;
    }

    private record PendingReference(EObject owner, EReference reference, JsonNode value) {

    }

    private final class ExportContext {

        private final ResourceSet resourceSet;
        private final Map<String, EObject> objectsById = new java.util.LinkedHashMap<>();
        private final List<PendingReference> pendingReferences = new ArrayList<>();

        ExportContext(ResourceSet resourceSet) {
            this.resourceSet = resourceSet;
        }

        EObject createContainedObject(JsonNode node, EClass expectedType) {
            if (node == null || !node.isObject()) {
                return null;
            }
            EClass eClass = eClassFor(node.path("eClass").asText(node.path("type").asText("")),
                    expectedType);
            EObject object = eClass.getEPackage().getEFactoryInstance().create(eClass);
            String objectId = scalarText(node.get("id"));
            if (!objectId.isBlank()) {
                objectsById.put(objectId, object);
            }
            for (EStructuralFeature feature : eClass.getEAllStructuralFeatures()) {
                if (feature.isDerived() || !feature.isChangeable()) {
                    continue;
                }
                JsonNode value = node.get(feature.getName());
                if (value == null || value.isNull()) {
                    continue;
                }
                if (feature instanceof EAttribute attribute) {
                    setAttribute(object, attribute, value);
                    continue;
                }
                if (feature instanceof EReference reference) {
                    if (reference.isContainment()) {
                        setContainment(object, reference, value);
                    } else if (!reference.isContainer()) {
                        pendingReferences.add(new PendingReference(object, reference, value));
                    }
                }
            }
            applySafeAttributeDefaults(object);
            return object;
        }

        private void applySafeAttributeDefaults(EObject object) {
            for (EAttribute attribute : object.eClass().getEAllAttributes()) {
                if (attribute.isMany() || object.eIsSet(attribute)) {
                    continue;
                }
                EDataType type = attribute.getEAttributeType();
                if (type == EcorePackage.Literals.EBOOLEAN
                        || type == EcorePackage.Literals.EBOOLEAN_OBJECT) {
                    object.eSet(attribute, false);
                }
            }
        }

        private void setContainment(EObject owner, EReference reference, JsonNode value) {
            if (reference.isMany()) {
                @SuppressWarnings("unchecked")
                List<EObject> target = (List<EObject>) owner.eGet(reference);
                if (value.isArray()) {
                    value.forEach(item -> {
                        EObject child = createContainedObject(item,
                                reference.getEReferenceType());
                        if (child != null) {
                            target.add(child);
                        }
                    });
                }
                return;
            }
            EObject child = createContainedObject(value, reference.getEReferenceType());
            if (child != null) {
                owner.eSet(reference, child);
            }
        }

        private void setAttribute(EObject object, EAttribute attribute, JsonNode value) {
            if (attribute.isMany()) {
                @SuppressWarnings("unchecked")
                List<Object> target = (List<Object>) object.eGet(attribute);
                if (value.isArray()) {
                    value.forEach(item -> {
                        Object converted = attributeValue(attribute, item);
                        if (converted != null) {
                            target.add(converted);
                        }
                    });
                }
                return;
            }
            Object converted = attributeValue(attribute, value);
            if (converted != null) {
                object.eSet(attribute, converted);
            }
        }

        private Object attributeValue(EAttribute attribute, JsonNode value) {
            EDataType type = attribute.getEAttributeType();
            if (type instanceof EEnum eEnum) {
                String literal = scalarText(value);
                EEnumLiteral enumLiteral = eEnum.getEEnumLiteral(literal);
                if (enumLiteral == null) {
                    enumLiteral = eEnum.getEEnumLiteralByLiteral(literal);
                }
                if (enumLiteral == null) {
                    return null;
                }
                return enumLiteral.getInstance();
            }
            String text = scalarText(value);
            if (text.isBlank() && !value.isTextual()) {
                text = value.asText();
            }
            try {
                return type.getEPackage().getEFactoryInstance().createFromString(type, text);
            } catch (RuntimeException ex) {
                return null;
            }
        }

        void resolveReferences() {
            for (PendingReference pending : pendingReferences) {
                if (pending.reference().isMany()) {
                    @SuppressWarnings("unchecked")
                    List<EObject> target = (List<EObject>) pending.owner().eGet(
                            pending.reference());
                    referenceIds(pending.value()).stream()
                            .map(objectsById::get)
                            .filter(candidate -> candidate != null)
                            .forEach(target::add);
                    continue;
                }
                referenceIds(pending.value()).stream()
                        .map(objectsById::get)
                        .filter(candidate -> candidate != null)
                        .findFirst()
                        .ifPresent(target -> pending.owner().eSet(pending.reference(), target));
            }
        }

        private List<String> referenceIds(JsonNode value) {
            if (value == null || value.isNull()) {
                return List.of();
            }
            if (value.isArray()) {
                List<String> ids = new ArrayList<>();
                value.forEach(item -> {
                    String id = referenceId(item);
                    if (!id.isBlank()) {
                        ids.add(id);
                    }
                });
                return ids;
            }
            String id = referenceId(value);
            return id.isBlank() ? List.of() : List.of(id);
        }

        private String referenceId(JsonNode value) {
            if (value.isTextual() || value.isNumber() || value.isBoolean()) {
                return value.asText("");
            }
            if (value.isObject()) {
                return scalarText(value.get("$ref")).isBlank()
                        ? scalarText(value.get("id"))
                        : scalarText(value.get("$ref"));
            }
            return "";
        }

        private EClass eClassFor(String requestedType, EClass expectedType) {
            if (requestedType != null && !requestedType.isBlank()) {
                EClass found = findEClass(requestedType);
                if (found != null) {
                    return found;
                }
            }
            if (expectedType != null) {
                return expectedType;
            }
            throw new PlatformException(400, "Unknown model element type: " + requestedType);
        }

        private EClass findEClass(String name) {
            for (Object value : resourceSet.getPackageRegistry().values()) {
                if (value instanceof EPackage ePackage) {
                    EClass found = findEClass(ePackage, name);
                    if (found != null) {
                        return found;
                    }
                }
            }
            return null;
        }

        private EClass findEClass(EPackage ePackage, String name) {
            EClassifier classifier = ePackage.getEClassifier(name);
            if (classifier instanceof EClass eClass) {
                return eClass;
            }
            for (EPackage child : ePackage.getESubpackages()) {
                EClass found = findEClass(child, name);
                if (found != null) {
                    return found;
                }
            }
            return null;
        }
    }

    private final class SerializationContext {

        private final Map<EObject, String> ids = new IdentityHashMap<>();
        private final Set<EObject> serializing = java.util.Collections.newSetFromMap(
                new IdentityHashMap<>());
        private final Set<String> usedIds = new LinkedHashSet<>();
        private final List<ObjectNode> graphElements = new ArrayList<>();
        private final List<ObjectNode> graphRelationships = new ArrayList<>();
        private final Set<String> graphRelationshipKeys = new LinkedHashSet<>();
        private final AtomicInteger syntheticIds = new AtomicInteger(1);

        String ensureId(EObject object) {
            return ids.computeIfAbsent(object, this::createId);
        }

        boolean beginSerialization(EObject object) {
            return serializing.add(object);
        }

        void endSerialization(EObject object) {
            serializing.remove(object);
        }

        private String createId(EObject object) {
            String existingId = explicitId(object);
            if (!existingId.isBlank() && usedIds.add(existingId)) {
                return existingId;
            }
            String xmiId = EcoreUtil.getID(object);
            if (xmiId != null && !xmiId.isBlank() && usedIds.add(xmiId)) {
                return xmiId;
            }
            String base = object.eClass().getName().toLowerCase(Locale.ROOT);
            String candidate;
            do {
                candidate = base + "-" + syntheticIds.getAndIncrement();
            } while (!usedIds.add(candidate));
            return candidate;
        }

        private String explicitId(EObject object) {
            EStructuralFeature feature = object.eClass().getEStructuralFeature("id");
            if (feature instanceof EAttribute && object.eIsSet(feature)) {
                Object value = object.eGet(feature);
                if (value != null) {
                    return String.valueOf(value).trim();
                }
            }
            return "";
        }

        void captureGraphObject(EObject object, ObjectNode semanticNode) {
            if (isRelationshipObject(object)) {
                captureGraphRelationship(object, semanticNode);
                return;
            }

            ObjectNode element = shallowGraphElement(semanticNode);
            graphElements.add(element);
            captureReferenceEdges(object);
        }

        private ObjectNode shallowGraphElement(ObjectNode semanticNode) {
            ObjectNode element = objectMapper.createObjectNode();
            semanticNode.fields().forEachRemaining(entry -> {
                JsonNode value = entry.getValue();
                if (value == null || value.isArray() || value.isObject()) {
                    return;
                }
                element.set(entry.getKey(), value.deepCopy());
            });
            if (!element.has("label") && element.hasNonNull("name")) {
                element.set("label", element.get("name").deepCopy());
            }
            return element;
        }

        private void captureGraphRelationship(EObject object, ObjectNode semanticNode) {
            ObjectNode relationship = objectMapper.createObjectNode();
            relationship.put("id", semanticNode.path("id").asText());
            relationship.put("eClass", object.eClass().getName());
            relationship.put("kind", relationshipKind(object.eClass().getName()));
            relationship.put("source", ensureId((EObject) object.eGet(
                    object.eClass().getEStructuralFeature("source"))));
            relationship.put("target", ensureId((EObject) object.eGet(
                    object.eClass().getEStructuralFeature("target"))));
            relationship.put("sourceElementId", relationship.path("source").asText());
            relationship.put("targetElementId", relationship.path("target").asText());
            copyScalarIfPresent(semanticNode, relationship, "name");
            copyScalarIfPresent(semanticNode, relationship, "label");
            copyScalarIfPresent(semanticNode, relationship, "relationshipType");
            graphRelationships.add(relationship);
            graphRelationshipKeys.add(graphEdgeKey(relationship));
        }

        private void captureReferenceEdges(EObject object) {
            String sourceId = ensureId(object);
            for (EReference reference : object.eClass().getEAllReferences()) {
                if (reference.isContainment() || reference.isContainer()) {
                    continue;
                }
                if (isRelationshipEndpointFeature(reference, object)) {
                    continue;
                }
                if (reference.isMany()) {
                    Object raw = object.eGet(reference);
                    List<?> values = raw instanceof List<?> list ? list : List.of();
                    for (Object value : values) {
                        if (value instanceof EObject target) {
                            addReferenceEdge(sourceId, object, reference, target);
                        }
                    }
                    continue;
                }
                Object value = object.eGet(reference);
                if (value instanceof EObject target) {
                    addReferenceEdge(sourceId, object, reference, target);
                }
            }
        }

        private void addReferenceEdge(String sourceId, EObject sourceObject, EReference reference,
                EObject targetObject) {
            String targetId = ensureId(targetObject);
            String kind = relationshipKind(reference.getName());
            String key = sourceId + "|" + targetId + "|" + kind;
            if (!graphRelationshipKeys.add(key)) {
                return;
            }
            ObjectNode relationship = objectMapper.createObjectNode();
            relationship.put("id", "ref-" + sanitizeId(sourceId) + "-" + sanitizeId(kind) + "-"
                    + sanitizeId(targetId));
            relationship.put("kind", kind);
            relationship.put("source", sourceId);
            relationship.put("target", targetId);
            relationship.put("sourceElementId", sourceId);
            relationship.put("targetElementId", targetId);
            relationship.put("semanticFeature", reference.getName());
            relationship.put("sourceType", sourceObject.eClass().getName());
            relationship.put("targetType", targetObject.eClass().getName());
            relationship.put("visualOnly", true);
            graphRelationships.add(relationship);
        }

        private boolean isRelationshipObject(EObject object) {
            EStructuralFeature source = object.eClass().getEStructuralFeature("source");
            EStructuralFeature target = object.eClass().getEStructuralFeature("target");
            return source instanceof EReference sourceRef
                    && target instanceof EReference targetRef
                    && !sourceRef.isContainment()
                    && !targetRef.isContainment()
                    && object.eGet(sourceRef) instanceof EObject
                    && object.eGet(targetRef) instanceof EObject;
        }

        private boolean isRelationshipEndpointFeature(EReference reference, EObject owner) {
            return isRelationshipObject(owner)
                    && ("source".equals(reference.getName()) || "target".equals(
                    reference.getName()));
        }

        private void copyScalarIfPresent(ObjectNode from, ObjectNode to, String key) {
            JsonNode value = from.get(key);
            if (value != null && value.isValueNode()) {
                to.set(key, value.deepCopy());
            }
        }

        private String graphEdgeKey(ObjectNode relationship) {
            return relationship.path("sourceElementId").asText() + "|"
                    + relationship.path("targetElementId").asText() + "|"
                    + relationship.path("kind").asText();
        }

        private String relationshipKind(String value) {
            return String.valueOf(value)
                    .replaceAll("([a-z0-9])([A-Z])", "$1_$2")
                    .replaceAll("[^A-Za-z0-9]+", "_")
                    .replaceAll("^_+|_+$", "")
                    .toUpperCase(Locale.ROOT);
        }

        private String sanitizeId(String value) {
            return String.valueOf(value)
                    .replaceAll("[^A-Za-z0-9_-]+", "-")
                    .replaceAll("^-+|-+$", "");
        }

        ObjectNode graphNode(ObjectMapper mapper) {
            ObjectNode graph = mapper.createObjectNode();
            ArrayNode elements = graph.putArray("elements");
            graphElements.forEach(element -> elements.add(element.deepCopy()));
            ArrayNode relationships = graph.putArray("relationships");
            graphRelationships.forEach(relationship -> relationships.add(relationship.deepCopy()));
            graph.putArray("traceLinks");
            graph.putArray("assumptions");
            graph.putArray("validationIssues");
            graph.putArray("manualBacklog");
            return graph;
        }

        ObjectNode diagramNode(ObjectMapper mapper) {
            ObjectNode diagram = mapper.createObjectNode();
            ArrayNode elements = diagram.putArray("elements");
            graphElements.forEach(element -> elements.add(element.deepCopy()));
            ArrayNode relationships = diagram.putArray("relationships");
            graphRelationships.forEach(relationship -> relationships.add(relationship.deepCopy()));
            return diagram;
        }
    }
}
