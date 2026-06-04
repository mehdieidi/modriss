package io.mehdieidi.modless.platform.core.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.mehdieidi.modless.platform.core.PlatformException;
import io.mehdieidi.modless.platform.core.model.ModelLevel;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
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

    private final ObjectMapper objectMapper;
    private final MetamodelResolver metamodelResolver;

    XmiModelImportService(ObjectMapper objectMapper) {
        this(objectMapper, new FileMetamodelResolver(new MdeRuntimePaths(
                MdeRuntimeOptions.defaults())));
    }

    XmiModelImportService(ObjectMapper objectMapper, MetamodelResolver metamodelResolver) {
        this.objectMapper = objectMapper;
        this.metamodelResolver = metamodelResolver;
    }

    JsonNode importModel(ModelLevel level, byte[] bytes) {
        Resource resource = loadResource(level, bytes, "import");
        try {
            List<EObject> roots = resource.getContents().stream()
                    .filter(EObject.class::isInstance)
                    .map(EObject.class::cast)
                    .toList();
            if (roots.isEmpty()) {
                throw new PlatformException(400,
                        "Uploaded XMI does not contain a model root.");
            }
            if (roots.size() > 1) {
                throw new PlatformException(400,
                        "Uploaded XMI must contain exactly one model root.");
            }
            EObject root = roots.get(0);
            validateRoot(level, root);
            SerializationContext context = new SerializationContext(level);
            ObjectNode rootJson = serializeContainedObject(root, context);
            rootJson.set("graph", context.graphNode(objectMapper));
            restoreRelationshipEndpoints(rootJson);
            restoreDerivedPsmAllResources(level, rootJson);
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

    Resource loadResource(ModelLevel level, byte[] bytes, String purpose) {
        try {
            ResourceSet resourceSet = newResourceSet();
            registerMetamodel(resourceSet, level);
            Resource resource = resourceSet.createResource(URI.createURI(
                    "memory:/" + sanitizePurpose(purpose) + "-" + level.apiName() + ".xmi"));
            try (ByteArrayInputStream input = new ByteArrayInputStream(bytes)) {
                resource.load(input, Map.of());
            }
            assertNoLoadErrors(resource);
            EcoreUtil.resolveAll(resourceSet);
            assertNoLoadErrors(resource);
            validateResourceRoot(level, resource);
            return resource;
        } catch (PlatformException ex) {
            throw ex;
        } catch (Exception ex) {
            String detail = ex.getMessage() == null || ex.getMessage().isBlank()
                    ? ex.getClass().getSimpleName()
                    : ex.getMessage();
            throw new PlatformException(400, "Uploaded model is not valid XMI: " + detail);
        }
    }

    private void validateResourceRoot(ModelLevel level, Resource resource) {
        List<EObject> roots = resource.getContents().stream()
                .filter(EObject.class::isInstance)
                .map(EObject.class::cast)
                .toList();
        if (roots.isEmpty()) {
            throw new PlatformException(400,
                    "Uploaded XMI does not contain a model root.");
        }
        if (roots.size() > 1) {
            throw new PlatformException(400,
                    "Uploaded XMI must contain exactly one model root.");
        }
        validateRoot(level, roots.get(0));
    }

    private String sanitizePurpose(String purpose) {
        String value = String.valueOf(purpose == null ? "xmi" : purpose)
                .replaceAll("[^A-Za-z0-9_.-]+", "-")
                .replaceAll("^-+|-+$", "");
        return value.isBlank() ? "xmi" : value;
    }

    byte[] exportModel(ModelLevel level, JsonNode modelJson) {
        try {
            Resource resource = exportResource(level, modelJson, XmiExportOptions.strict());
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

    Resource exportResource(ModelLevel level, JsonNode modelJson) {
        return exportResource(level, modelJson, XmiExportOptions.strict());
    }

    Resource exportResource(ModelLevel level, JsonNode modelJson, XmiExportOptions options) {
        try {
            ResourceSet resourceSet = newResourceSet();
            registerMetamodel(resourceSet, level);
            Resource resource = resourceSet.createResource(URI.createURI(
                    "memory:/export-" + level.apiName() + ".xmi"));
            ExportDiagnostics diagnostics = new ExportDiagnostics(options);
            ExportContext context = new ExportContext(resourceSet, diagnostics);
            EObject root = context.createContainedObject(modelJson, null);
            if (root == null) {
                throw new PlatformException(400, "Model JSON does not contain a valid root.");
            }
            validateRoot(level, root);
            resource.getContents().add(root);
            context.resolveReferences();
            diagnostics.throwIfErrors();
            return resource;
        } catch (PlatformException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new PlatformException(400, "Model JSON cannot be exported as XMI: "
                    + (ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage()));
        }
    }

    private ResourceSet newResourceSet() {
        ResourceSet resourceSet = new ResourceSetImpl();
        resourceSet.getResourceFactoryRegistry().getExtensionToFactoryMap()
                .put("ecore", new EcoreResourceFactoryImpl());
        resourceSet.getResourceFactoryRegistry().getExtensionToFactoryMap()
                .put("xmi", new XMIResourceFactoryImpl());
        resourceSet.getPackageRegistry().put(EcorePackage.eNS_URI, EcorePackage.eINSTANCE);
        return resourceSet;
    }

    private void registerMetamodel(ResourceSet resourceSet, ModelLevel level) {
        metamodelResolver.resolve(level).packages()
                .forEach(ePackage -> registerPackage(resourceSet, ePackage));
    }

    private void registerPackage(ResourceSet resourceSet, EPackage ePackage) {
        if (ePackage.getNsURI() != null && !ePackage.getNsURI().isBlank()) {
            resourceSet.getPackageRegistry().put(ePackage.getNsURI(), ePackage);
        }
        ePackage.getESubpackages().forEach(child -> registerPackage(resourceSet, child));
    }

    private void assertNoLoadErrors(Resource resource) {
        if (resource.getErrors().isEmpty()) {
            return;
        }
        Resource.Diagnostic diagnostic = resource.getErrors().get(0);
        String location = diagnostic.getLine() > 0
                ? " at line " + diagnostic.getLine() + ", column " + diagnostic.getColumn()
                : "";
        throw new PlatformException(400, "Uploaded XMI does not conform to the metamodel"
                + location + ": " + diagnostic.getMessage());
    }

    private void validateRoot(ModelLevel level, EObject root) {
        String expected = expectedRootEClass(level);
        String actual = root.eClass().getName();
        if (!expected.equals(actual)) {
            throw new PlatformException(400, "Expected " + expected + " root for "
                    + level.name() + " model but found " + actual + ".");
        }
    }

    private String expectedRootEClass(ModelLevel level) {
        return switch (level) {
            case CIM -> "CIMModel";
            case PIM -> "PIMModel";
            case PSM -> "AwsPsmModel";
        };
    }

    private String scalarText(JsonNode value) {
        if (value == null || value.isNull() || value.isContainerNode()) {
            return "";
        }
        return value.asText("").trim();
    }

    private void restoreRelationshipEndpoints(ObjectNode rootJson) {
        JsonNode graphRelationships = rootJson.path("graph").path("relationships");
        if (!graphRelationships.isArray()) {
            return;
        }
        Map<String, JsonNode> relationshipsById = new java.util.LinkedHashMap<>();
        graphRelationships.forEach(relationship -> {
            String id = scalarText(relationship.get("id"));
            if (!id.isBlank()) {
                relationshipsById.put(id, relationship);
            }
        });
        if (relationshipsById.isEmpty()) {
            return;
        }
        restoreRelationshipEndpoints(rootJson, relationshipsById);
    }

    private void restoreRelationshipEndpoints(JsonNode node,
            Map<String, JsonNode> relationshipsById) {
        if (node == null || node.isNull()) {
            return;
        }
        if (node.isObject()) {
            ObjectNode object = (ObjectNode) node;
            JsonNode relationship = relationshipsById.get(scalarText(object.get("id")));
            if (relationship != null) {
                copyTextIfMissing(object, relationship, "source", "sourceElementId");
                copyTextIfMissing(object, relationship, "target", "targetElementId");
                copyTextIfMissing(object, relationship, "sourceElementId", "source");
                copyTextIfMissing(object, relationship, "targetElementId", "target");
            }
            restoreTraceEndpointIds(object);
            object.fields().forEachRemaining(entry -> restoreRelationshipEndpoints(
                    entry.getValue(), relationshipsById));
            return;
        }
        if (node.isArray()) {
            node.forEach(child -> restoreRelationshipEndpoints(child, relationshipsById));
        }
    }

    private void restoreTraceEndpointIds(ObjectNode object) {
        if (!"TraceLink".equals(scalarText(object.get("eClass")))) {
            return;
        }
        copyTextIfMissing(object, object, "sourceElementId", "source");
        copyTextIfMissing(object, object, "targetElementId", "target");
    }

    private void copyTextIfMissing(ObjectNode target, JsonNode source, String fieldName,
            String fallbackFieldName) {
        if (target.hasNonNull(fieldName) && !target.path(fieldName).asText("").isBlank()) {
            return;
        }
        String value = scalarText(source.get(fieldName));
        if (value.isBlank()) {
            value = scalarText(source.get(fallbackFieldName));
        }
        if (!value.isBlank()) {
            target.put(fieldName, value);
        }
    }

    private void restoreDerivedPsmAllResources(ModelLevel level, ObjectNode rootJson) {
        if (level != ModelLevel.PSM || !rootJson.path("allResources").isEmpty()) {
            return;
        }
        ArrayNode allResources = objectMapper.createArrayNode();
        JsonNode stacks = rootJson.path("stacks");
        if (stacks.isArray()) {
            stacks.forEach(stack -> {
                JsonNode resources = stack.path("resources");
                if (resources.isArray()) {
                    resources.forEach(resource -> {
                        String id = scalarText(resource.get("id"));
                        if (!id.isBlank()) {
                            allResources.add(id);
                        }
                    });
                }
            });
        }
        rootJson.set("allResources", allResources);
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
                    if (!object.eIsSet(feature) && !isRequiredAttribute(attribute)) {
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
            ensureTraceEndpointIds(object, node, context);
            return node;
        } finally {
            context.endSerialization(object);
        }
    }

    private void ensureTraceEndpointIds(EObject object, ObjectNode node,
            SerializationContext context) {
        if (!"TraceLink".equals(object.eClass().getName())) {
            return;
        }
        copyReferenceIdAttributeIfMissing(object, node, context, "source", "sourceElementId");
        copyReferenceIdAttributeIfMissing(object, node, context, "target", "targetElementId");
    }

    private void copyReferenceIdAttributeIfMissing(EObject object, ObjectNode node,
            SerializationContext context, String referenceName, String attributeName) {
        if (node.hasNonNull(attributeName) && !node.path(attributeName).asText("").isBlank()) {
            return;
        }
        EStructuralFeature reference = object.eClass().getEStructuralFeature(referenceName);
        Object value = reference == null ? null : object.eGet(reference);
        if (value instanceof EObject target) {
            node.put(attributeName, context.ensureId(target));
        }
    }

    private boolean isRequiredAttribute(EAttribute attribute) {
        return attribute.getLowerBound() > 0 && !attribute.isMany();
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
                        context.addContainmentEdge(owner, reference, child);
                    }
                }
            }
            return array;
        }
        if (rawValue instanceof EObject child) {
            ObjectNode serializedChild = serializeContainedObject(child, context);
            if (serializedChild != null) {
                context.addContainmentEdge(owner, reference, child);
            }
            return serializedChild;
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

    private record ExportDiagnostic(String ownerType, String ownerId, String feature,
                                    String message) {

    }

    private record PendingReference(EObject owner, EReference reference, JsonNode value) {

    }

    private final class ExportDiagnostics {

        private final XmiExportOptions options;
        private final List<ExportDiagnostic> errors = new ArrayList<>();

        ExportDiagnostics(XmiExportOptions options) {
            this.options = options == null ? XmiExportOptions.strict() : options;
        }

        void attributeError(EObject owner, EAttribute attribute, String message) {
            if (options.strictAttributes()) {
                error(owner, attribute.getName(), message);
            }
        }

        void referenceError(EObject owner, EReference reference, String message) {
            if (options.strictReferences()) {
                error(owner, reference.getName(), message);
            }
        }

        void error(EObject owner, String feature, String message) {
            errors.add(new ExportDiagnostic(
                    owner == null ? "" : owner.eClass().getName(),
                    ownerId(owner),
                    feature == null ? "" : feature,
                    message == null ? "" : message));
        }

        void throwIfErrors() {
            if (errors.isEmpty()) {
                return;
            }
            String details = errors.stream()
                    .limit(5)
                    .map(this::message)
                    .collect(java.util.stream.Collectors.joining("; "));
            if (errors.size() > 5) {
                details = details + "; and " + (errors.size() - 5) + " more";
            }
            throw new PlatformException(400, "Model JSON cannot be exported as XMI: "
                    + details);
        }

        private String message(ExportDiagnostic diagnostic) {
            String owner = diagnostic.ownerType();
            if (!diagnostic.ownerId().isBlank()) {
                owner = owner + "[" + diagnostic.ownerId() + "]";
            }
            String feature = diagnostic.feature().isBlank() ? "" : "."
                                                                   + diagnostic.feature();
            return owner + feature + ": " + diagnostic.message();
        }

        private String ownerId(EObject owner) {
            if (owner == null) {
                return "";
            }
            EStructuralFeature idFeature = owner.eClass().getEStructuralFeature("id");
            if (idFeature instanceof EAttribute && owner.eIsSet(idFeature)) {
                Object value = owner.eGet(idFeature);
                return value == null ? "" : String.valueOf(value);
            }
            return "";
        }
    }

    private final class ExportContext {

        private final ResourceSet resourceSet;
        private final ExportDiagnostics diagnostics;
        private final Map<String, EObject> objectsById = new java.util.LinkedHashMap<>();
        private final List<PendingReference> pendingReferences = new ArrayList<>();

        ExportContext(ResourceSet resourceSet, ExportDiagnostics diagnostics) {
            this.resourceSet = resourceSet;
            this.diagnostics = diagnostics;
        }

        EObject createContainedObject(JsonNode node, EClass expectedType) {
            if (node == null || !node.isObject()) {
                return null;
            }
            EClass eClass = eClassFor(node.path("eClass").asText(node.path("type").asText("")),
                    expectedType);
            String objectId = scalarText(node.get("id"));
            if (!objectId.isBlank()) {
                EObject existing = objectsById.get(objectId);
                if (existing != null) {
                    if (existing.eClass() == eClass) {
                        return existing;
                    }
                    EObject duplicate = eClass.getEPackage().getEFactoryInstance().create(eClass);
                    diagnostics.error(duplicate, "id",
                            "Duplicate model element id '" + objectId + "'.");
                    return duplicate;
                }
            }
            EObject object = eClass.getEPackage().getEFactoryInstance().create(eClass);
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
                    } else if (!reference.isContainer()
                            && !isInverseTraceReference(reference)) {
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
                if (attribute.getDefaultValueLiteral() != null) {
                    object.eSet(attribute, attribute.getDefaultValue());
                }
            }
        }

        private boolean isInverseTraceReference(EReference reference) {
            String name = reference.getName();
            return ("incomingTraces".equals(name) || "outgoingTraces".equals(name))
                    && "TraceLink".equals(reference.getEReferenceType().getName());
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
                if (!value.isArray()) {
                    diagnostics.attributeError(object, attribute,
                            "Expected an array for multi-valued attribute.");
                    return;
                }
                if (value.isArray()) {
                    value.forEach(item -> {
                        Object converted = attributeValue(object, attribute, item);
                        if (converted != null) {
                            target.add(converted);
                        }
                    });
                }
                return;
            }
            Object converted = attributeValue(object, attribute, value);
            if (converted != null) {
                object.eSet(attribute, converted);
            }
        }

        private Object attributeValue(EObject owner, EAttribute attribute, JsonNode value) {
            if (value != null && value.isContainerNode()) {
                diagnostics.attributeError(owner, attribute,
                        "Expected a scalar value but found " + value.getNodeType() + ".");
                return null;
            }
            EDataType type = attribute.getEAttributeType();
            if (type instanceof EEnum eEnum) {
                String literal = scalarText(value);
                EEnumLiteral enumLiteral = eEnum.getEEnumLiteral(literal);
                if (enumLiteral == null) {
                    enumLiteral = eEnum.getEEnumLiteralByLiteral(literal);
                }
                if (enumLiteral == null) {
                    diagnostics.attributeError(owner, attribute,
                            "Unknown enum literal '" + literal + "'.");
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
                diagnostics.attributeError(owner, attribute,
                        "Invalid value '" + text + "': " + ex.getMessage());
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
                            .map(id -> resolveReference(pending, id))
                            .filter(candidate -> candidate != null)
                            .forEach(target::add);
                    continue;
                }
                referenceIds(pending.value()).stream()
                        .map(id -> resolveReference(pending, id))
                        .filter(candidate -> candidate != null)
                        .findFirst()
                        .ifPresent(target -> pending.owner().eSet(pending.reference(), target));
            }
        }

        private EObject resolveReference(PendingReference pending, String id) {
            EObject target = objectsById.get(id);
            if (target == null) {
                diagnostics.referenceError(pending.owner(), pending.reference(),
                        "Unresolved reference id '" + id + "'.");
                return null;
            }
            if (!pending.reference().getEReferenceType().isInstance(target)) {
                diagnostics.referenceError(pending.owner(), pending.reference(),
                        "Reference id '" + id + "' resolves to "
                                + target.eClass().getName() + ", which is not valid for "
                                + pending.reference().getEReferenceType().getName() + ".");
                return null;
            }
            return target;
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
                if (found == null) {
                    throw new PlatformException(400,
                            "Unknown model element type: " + requestedType);
                }
                if (expectedType != null && !expectedType.isSuperTypeOf(found)) {
                    throw new PlatformException(400, "Element type " + requestedType
                            + " is not valid for containment " + expectedType.getName() + ".");
                }
                return found;
            }
            if (expectedType != null) {
                return expectedType;
            }
            throw new PlatformException(400, "Missing model element type.");
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

        private final ModelLevel level;
        private final Map<EObject, String> ids = new IdentityHashMap<>();
        private final Set<EObject> serializing = java.util.Collections.newSetFromMap(
                new IdentityHashMap<>());
        private final Set<EObject> serialized = java.util.Collections.newSetFromMap(
                new IdentityHashMap<>());
        private final Set<String> usedIds = new LinkedHashSet<>();
        private final List<ObjectNode> graphElements = new ArrayList<>();
        private final List<ObjectNode> graphRelationships = new ArrayList<>();
        private final Set<String> graphRelationshipKeys = new LinkedHashSet<>();
        private final AtomicInteger syntheticIds = new AtomicInteger(1);

        SerializationContext(ModelLevel level) {
            this.level = level;
        }

        String ensureId(EObject object) {
            return ids.computeIfAbsent(object, this::createId);
        }

        boolean beginSerialization(EObject object) {
            if (serialized.contains(object)) {
                return false;
            }
            return serializing.add(object);
        }

        void endSerialization(EObject object) {
            serializing.remove(object);
            serialized.add(object);
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
            copyLayoutAnnotation(semanticNode, element, "modless.layout.x", "x");
            copyLayoutAnnotation(semanticNode, element, "modless.layout.y", "y");
            return element;
        }

        private void copyLayoutAnnotation(ObjectNode semanticNode, ObjectNode element,
                String key, String fieldName) {
            JsonNode annotations = semanticNode.path("annotations");
            if (!annotations.isArray()) {
                return;
            }
            for (JsonNode annotation : annotations) {
                if (key.equals(annotation.path("key").asText())) {
                    element.put(fieldName, annotation.path("value").asDouble());
                    return;
                }
            }
        }

        private void captureGraphRelationship(EObject object, ObjectNode semanticNode) {
            ObjectNode relationship = objectMapper.createObjectNode();
            relationship.put("id", semanticNode.path("id").asText());
            relationship.put("eClass", object.eClass().getName());
            relationship.put("kind", relationshipKindForObject(object));
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

        private String relationshipKindForObject(EObject object) {
            String eClassName = object.eClass().getName();
            if (level == ModelLevel.PSM && eClassName.endsWith("View")) {
                return psmRelationshipViewKind(eClassName);
            }
            return relationshipClassKind(eClassName);
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
            String kind = referenceFeatureKind(reference.getName());
            if (kind.isBlank()) {
                kind = relationshipKind(reference.getName());
            }
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

        private void addContainmentEdge(EObject owner, EReference reference, EObject child) {
            String sourceId = ensureId(owner);
            String targetId = ensureId(child);
            String kind = containmentRelationshipKind(reference.getName());
            String key = sourceId + "|" + targetId + "|" + kind;
            if (!graphRelationshipKeys.add(key)) {
                return;
            }
            ObjectNode relationship = objectMapper.createObjectNode();
            relationship.put("id", "contains-" + sanitizeId(sourceId) + "-"
                    + sanitizeId(targetId));
            relationship.put("kind", kind);
            relationship.put("source", sourceId);
            relationship.put("target", targetId);
            relationship.put("sourceElementId", sourceId);
            relationship.put("targetElementId", targetId);
            relationship.put("semanticFeature", reference.getName());
            relationship.put("sourceType", owner.eClass().getName());
            relationship.put("targetType", child.eClass().getName());
            relationship.put("containment", true);
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

        private String referenceFeatureKind(String value) {
            return switch (String.valueOf(value)) {
                case "functionIntegration" -> "ROUTES_TO";
                case "reads" -> "READS";
                case "writes" -> "WRITES";
                case "publishes" -> "PUBLISHES";
                case "subscribesTo" -> "SUBSCRIBES_TO";
                case "invokesFunction" -> "INVOKES";
                case "invokedResource" -> "INVOKES";
                case "startState" -> "STARTS_AT";
                case "nextState" -> "TRANSITION";
                case "endStates" -> "ENDS_AT";
                case "targetResource" -> "PERMISSION_TARGET";
                case "permissions" -> "PERMISSION";
                case "deploysStacks" -> "DEPLOYS";
                case "resources", "allResources" -> "CONTAINS";
                case "route" -> "USES_ROUTE";
                case "function" -> "INVOKES";
                case "role" -> "USES_ROLE";
                case "logGroup" -> "WRITES_LOGS_TO";
                default -> "";
            };
        }

        private String containmentRelationshipKind(String featureName) {
            String kind = referenceFeatureKind(featureName);
            return kind.isBlank() ? "CONTAINS" : kind;
        }

        private String relationshipClassKind(String value) {
            return switch (String.valueOf(value)) {
                case "WorkflowTransition" -> "TRANSITION";
                case "TraceLink" -> "TRACE";
                case "ApiGatewayLambdaIntegrationView" -> "INVOKES";
                default -> relationshipKind(value);
            };
        }

        private String psmRelationshipViewKind(String value) {
            return switch (String.valueOf(value)) {
                case "ApiGatewayLambdaIntegrationView" -> "INVOKES";
                case "EventBridgeLambdaTargetView" -> "TARGETS";
                case "SnsLambdaSubscriptionView", "SqsLambdaEventSourceView",
                     "S3LambdaNotificationView", "S3TopicNotificationView" -> "EVENT_FLOW";
                case "S3QueueNotificationView" -> "MESSAGE_FLOW";
                case "StepFunctionEventBridgeTargetView" -> "INVOKES";
                default -> relationshipClassKind(value);
            };
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

    }
}
