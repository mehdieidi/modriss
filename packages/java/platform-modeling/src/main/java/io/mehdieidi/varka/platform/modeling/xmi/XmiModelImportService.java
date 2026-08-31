package io.mehdieidi.varka.platform.modeling.xmi;

import io.mehdieidi.varka.platform.kernel.ModelLevel;
import io.mehdieidi.varka.platform.kernel.PlatformException;
import io.mehdieidi.varka.platform.modeling.config.ModelingConfigService;
import io.mehdieidi.varka.platform.modeling.metamodel.FileMetamodelResolver;
import io.mehdieidi.varka.platform.modeling.metamodel.MetamodelResolver;
import io.mehdieidi.varka.platform.modeling.runtime.MdeRuntimeOptions;
import io.mehdieidi.varka.platform.modeling.runtime.MdeRuntimePaths;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamReader;
import org.eclipse.emf.common.util.Enumerator;
import org.eclipse.emf.common.util.TreeIterator;
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
import org.eclipse.emf.ecore.xmi.XMLResource;
import org.eclipse.emf.ecore.xmi.impl.EcoreResourceFactoryImpl;
import org.eclipse.emf.ecore.xmi.impl.XMIResourceFactoryImpl;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/** Converts between platform model JSON and EMF/XMI resources for each model level. */
public final class XmiModelImportService {

  /** Mapper used to build JSON trees during import/export. */
  private final ObjectMapper objectMapper;

  /** Metamodel resolver used to register EPackages in EMF resource sets. */
  private final MetamodelResolver metamodelResolver;

  /** Modeling configuration used for CVS-owned import/export decisions. */
  private final ModelingConfigService modelingConfig;

  /**
   * Creates an XMI service using the default file-backed metamodel resolver.
   *
   * @param objectMapper mapper used for JSON nodes
   */
  public XmiModelImportService(ObjectMapper objectMapper) {
    this(
        objectMapper, new FileMetamodelResolver(new MdeRuntimePaths(MdeRuntimeOptions.defaults())));
  }

  /**
   * Creates an XMI service with an explicit metamodel resolver.
   *
   * @param objectMapper mapper used for JSON nodes
   * @param metamodelResolver metamodel resolver
   */
  public XmiModelImportService(ObjectMapper objectMapper, MetamodelResolver metamodelResolver) {
    this(objectMapper, metamodelResolver, new ModelingConfigService());
  }

  /**
   * Creates an XMI service with explicit metamodel and modeling configuration services.
   *
   * @param objectMapper mapper used for JSON nodes
   * @param metamodelResolver metamodel resolver
   * @param modelingConfig modeling configuration service
   */
  public XmiModelImportService(
      ObjectMapper objectMapper,
      MetamodelResolver metamodelResolver,
      ModelingConfigService modelingConfig) {
    this.objectMapper = objectMapper;
    this.metamodelResolver = metamodelResolver;
    this.modelingConfig = modelingConfig == null ? new ModelingConfigService() : modelingConfig;
  }

  /**
   * Imports user-supplied XMI into platform model JSON.
   *
   * @param level model level
   * @param bytes XMI bytes
   * @return imported model JSON
   */
  public JsonNode importModel(ModelLevel level, byte[] bytes) {
    return importModel(level, bytes, "import", true);
  }

  /**
   * Imports generated XMI into platform model JSON.
   *
   * @param level model level
   * @param bytes generated XMI bytes
   * @return imported model JSON
   */
  public JsonNode importGeneratedModel(ModelLevel level, byte[] bytes) {
    return importModel(level, bytes, "generated-import", true);
  }

  /**
   * Imports XMI bytes into platform JSON with optional reference resolution.
   *
   * @param level model level
   * @param bytes XMI bytes
   * @param purpose memory resource purpose label
   * @param resolveAllReferences whether EMF proxies should be resolved
   * @return imported model JSON
   */
  private JsonNode importModel(
      ModelLevel level, byte[] bytes, String purpose, boolean resolveAllReferences) {
    Resource resource = loadResource(level, bytes, purpose, resolveAllReferences);
    try {
      List<EObject> roots =
          resource.getContents().stream()
              .filter(EObject.class::isInstance)
              .map(EObject.class::cast)
              .toList();
      if (roots.isEmpty()) {
        throw new PlatformException(400, "Uploaded XMI does not contain a model root.");
      }
      if (roots.size() > 1) {
        throw new PlatformException(400, "Uploaded XMI must contain exactly one model root.");
      }
      EObject root = roots.get(0);
      validateRoot(level, root);
      SerializationContext context = new SerializationContext(level);
      ObjectNode rootJson = serializeContainedObject(root, context);
      rootJson.set("graph", context.graphNode(objectMapper));
      restoreRelationshipEndpoints(level, rootJson);
      restoreDerivedPsmAllResources(level, rootJson);
      return rootJson;
    } catch (PlatformException ex) {
      throw ex;
    } catch (Exception ex) {
      String detail =
          ex.getMessage() == null || ex.getMessage().isBlank()
              ? ex.getClass().getSimpleName()
              : ex.getMessage();
      throw new PlatformException(400, "Uploaded model is not valid XMI: " + detail);
    }
  }

  /**
   * Loads an XMI resource and resolves all references.
   *
   * @param level model level
   * @param bytes XMI bytes
   * @param purpose memory resource purpose label
   * @return loaded EMF resource
   */
  public Resource loadResource(ModelLevel level, byte[] bytes, String purpose) {
    return loadResource(level, bytes, purpose, true);
  }

  /**
   * Loads an XMI resource with optional reference resolution.
   *
   * @param level model level
   * @param bytes XMI bytes
   * @param purpose memory resource purpose label
   * @param resolveAllReferences whether EMF proxies should be resolved
   * @return loaded EMF resource
   */
  public Resource loadResource(
      ModelLevel level, byte[] bytes, String purpose, boolean resolveAllReferences) {
    try {
      ResourceSet resourceSet = newResourceSet();
      registerMetamodel(resourceSet, level);
      Resource resource =
          resourceSet.createResource(
              URI.createURI(
                  "memory:/" + sanitizePurpose(purpose) + "-" + level.apiName() + ".xmi"));
      try (ByteArrayInputStream input = new ByteArrayInputStream(bytes)) {
        resource.load(input, Map.of(XMLResource.OPTION_DEFER_IDREF_RESOLUTION, Boolean.FALSE));
      }
      assertNoLoadErrors(resource);
      if (resolveAllReferences) {
        EcoreUtil.resolveAll(resourceSet);
        assertNoLoadErrors(resource);
      }
      repairUnresolvedReferences(resource, bytes);
      validateResourceRoot(level, resource);
      return resource;
    } catch (PlatformException ex) {
      throw ex;
    } catch (Exception ex) {
      String detail =
          ex.getMessage() == null || ex.getMessage().isBlank()
              ? ex.getClass().getSimpleName()
              : ex.getMessage();
      throw new PlatformException(400, "Uploaded model is not valid XMI: " + detail);
    }
  }

  /**
   * Verifies that an EMF resource contains exactly one valid root.
   *
   * @param level expected model level
   * @param resource loaded resource
   */
  private void validateResourceRoot(ModelLevel level, Resource resource) {
    List<EObject> roots =
        resource.getContents().stream()
            .filter(EObject.class::isInstance)
            .map(EObject.class::cast)
            .toList();
    if (roots.isEmpty()) {
      throw new PlatformException(400, "Uploaded XMI does not contain a model root.");
    }
    if (roots.size() > 1) {
      String rootTypes =
          roots.stream()
              .map(root -> root.eClass().getName())
              .distinct()
              .collect(java.util.stream.Collectors.joining(", "));
      throw new PlatformException(
          400, "Uploaded XMI must contain exactly one model root (found: " + rootTypes + ").");
    }
    validateRoot(level, roots.get(0));
  }

  /**
   * Sanitizes a purpose string for use in an in-memory resource URI.
   *
   * @param purpose raw purpose
   * @return safe URI path fragment
   */
  private String sanitizePurpose(String purpose) {
    String value =
        String.valueOf(purpose == null ? "xmi" : purpose)
            .replaceAll("[^A-Za-z0-9_.-]+", "-")
            .replaceAll("^-+|-+$", "");
    return value.isBlank() ? "xmi" : value;
  }

  /**
   * Exports platform model JSON to XMI bytes using strict export options.
   *
   * @param level model level
   * @param modelJson model JSON
   * @return XMI bytes
   */
  public byte[] exportModel(ModelLevel level, JsonNode modelJson) {
    try {
      Resource resource = exportResource(level, modelJson, XmiExportOptions.strict());
      ByteArrayOutputStream output = new ByteArrayOutputStream();
      resource.save(output, Map.of());
      return output.toByteArray();
    } catch (PlatformException ex) {
      throw ex;
    } catch (Exception ex) {
      throw new PlatformException(
          400,
          "Model JSON cannot be exported as XMI: "
              + (ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage()));
    }
  }

  /**
   * Removes PIM features that are not accepted by the downstream ETL source loader.
   *
   * @param level model level
   * @param bytes source XMI bytes
   * @return pruned XMI bytes
   */
  public byte[] pruneTransformationInput(ModelLevel level, byte[] bytes) {
    if (level != ModelLevel.PIM || bytes == null || bytes.length == 0) {
      return bytes;
    }
    try {
      Resource resource = loadResource(level, bytes, "transform-source", false);
      EObject root = singleRoot(resource);
      unsetFeature(root, "traceModel");
      unsetFeature(root, "readiness");
      stripInverseTraceReferences(root);
      ByteArrayOutputStream output = new ByteArrayOutputStream(bytes.length);
      resource.save(output, Map.of());
      return output.toByteArray();
    } catch (PlatformException ex) {
      throw ex;
    } catch (Exception ex) {
      throw new PlatformException(500, "Could not prepare PIM source XMI for transformation.");
    }
  }

  /**
   * Returns the first EObject root from a resource.
   *
   * @param resource loaded resource
   * @return root EObject
   */
  private EObject singleRoot(Resource resource) {
    return resource.getContents().stream()
        .filter(EObject.class::isInstance)
        .map(EObject.class::cast)
        .findFirst()
        .orElseThrow(
            () -> new PlatformException(400, "Uploaded XMI does not contain a model root."));
  }

  /**
   * Removes inverse trace references from a root and all contained objects.
   *
   * @param root resource root object
   */
  private void stripInverseTraceReferences(EObject root) {
    unsetFeature(root, "incomingTraces");
    unsetFeature(root, "outgoingTraces");
    TreeIterator<EObject> contents = root.eAllContents();
    while (contents.hasNext()) {
      EObject object = contents.next();
      unsetFeature(object, "incomingTraces");
      unsetFeature(object, "outgoingTraces");
    }
  }

  /**
   * Unsets a changeable feature when present.
   *
   * @param object EMF object
   * @param featureName feature name
   */
  private void unsetFeature(EObject object, String featureName) {
    EStructuralFeature feature = object.eClass().getEStructuralFeature(featureName);
    if (feature != null && feature.isChangeable() && object.eIsSet(feature)) {
      object.eUnset(feature);
    }
  }

  /**
   * Exports platform JSON to an EMF resource using strict options.
   *
   * @param level model level
   * @param modelJson model JSON
   * @return EMF resource
   */
  public Resource exportResource(ModelLevel level, JsonNode modelJson) {
    return exportResource(level, modelJson, XmiExportOptions.strict());
  }

  /**
   * Exports platform JSON to an EMF resource.
   *
   * @param level model level
   * @param modelJson model JSON
   * @param options export strictness options
   * @return EMF resource
   */
  public Resource exportResource(ModelLevel level, JsonNode modelJson, XmiExportOptions options) {
    try {
      ResourceSet resourceSet = newResourceSet();
      registerMetamodel(resourceSet, level);
      Resource resource =
          resourceSet.createResource(URI.createURI("memory:/export-" + level.apiName() + ".xmi"));
      ExportDiagnostics diagnostics = new ExportDiagnostics(options);
      ExportContext context = new ExportContext(resourceSet, diagnostics, level);
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
      throw new PlatformException(
          400,
          "Model JSON cannot be exported as XMI: "
              + (ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage()));
    }
  }

  /**
   * Creates a resource set with Ecore and XMI resource factories registered.
   *
   * @return resource set
   */
  private ResourceSet newResourceSet() {
    ResourceSet resourceSet = new ResourceSetImpl();
    resourceSet
        .getResourceFactoryRegistry()
        .getExtensionToFactoryMap()
        .put("ecore", new EcoreResourceFactoryImpl());
    resourceSet
        .getResourceFactoryRegistry()
        .getExtensionToFactoryMap()
        .put("xmi", new XMIResourceFactoryImpl());
    resourceSet.getPackageRegistry().put(EcorePackage.eNS_URI, EcorePackage.eINSTANCE);
    return resourceSet;
  }

  /**
   * Registers all EPackages for a model level.
   *
   * @param resourceSet resource set to mutate
   * @param level model level
   */
  private void registerMetamodel(ResourceSet resourceSet, ModelLevel level) {
    metamodelResolver
        .resolve(level)
        .packages()
        .forEach(ePackage -> registerPackage(resourceSet, ePackage));
  }

  /**
   * Recursively registers an EPackage and subpackages by namespace URI.
   *
   * @param resourceSet resource set to mutate
   * @param ePackage package to register
   */
  private void registerPackage(ResourceSet resourceSet, EPackage ePackage) {
    if (ePackage.getNsURI() != null && !ePackage.getNsURI().isBlank()) {
      resourceSet.getPackageRegistry().put(ePackage.getNsURI(), ePackage);
    }
    ePackage.getESubpackages().forEach(child -> registerPackage(resourceSet, child));
  }

  /**
   * Raises a platform error for the first EMF resource load error.
   *
   * @param resource loaded resource
   */
  private void assertNoLoadErrors(Resource resource) {
    if (resource.getErrors().isEmpty()) {
      return;
    }
    Resource.Diagnostic diagnostic = resource.getErrors().get(0);
    String location =
        diagnostic.getLine() > 0
            ? " at line " + diagnostic.getLine() + ", column " + diagnostic.getColumn()
            : "";
    throw new PlatformException(
        400,
        "Uploaded XMI does not conform to the metamodel"
            + location
            + ": "
            + diagnostic.getMessage());
  }

  /**
   * Repairs non-containment references that the EMF XMI reader can leave unset when an external
   * model uses stable {@code id} values alongside {@code xmi:id} values. The XML attributes are the
   * authoritative serialized representation; repairing from them keeps import/export lossless
   * without inventing endpoint values in the transformation layer.
   *
   * @param resource loaded XMI resource
   * @param bytes original XMI bytes
   */
  private void repairUnresolvedReferences(Resource resource, byte[] bytes) {
    Map<String, Map<String, String>> rawReferences = rawReferenceAttributes(bytes);
    if (rawReferences.isEmpty()) {
      return;
    }
    Map<String, EObject> objectsById = new HashMap<>();
    List<EObject> objects = new ArrayList<>();
    resource
        .getContents()
        .forEach(
            root -> {
              objects.add(root);
              collectObjects(root, objects);
            });
    for (EObject object : objects) {
      String xmiId = EcoreUtil.getID(object);
      if (xmiId != null && !xmiId.isBlank()) {
        objectsById.putIfAbsent(xmiId, object);
      }
      EStructuralFeature idFeature = object.eClass().getEStructuralFeature("id");
      if (idFeature instanceof EAttribute && object.eIsSet(idFeature)) {
        Object value = object.eGet(idFeature);
        if (value != null && !String.valueOf(value).isBlank()) {
          objectsById.putIfAbsent(String.valueOf(value), object);
        }
      }
    }
    for (EObject object : objects) {
      Map<String, String> attributes = rawReferences.get(EcoreUtil.getID(object));
      if (attributes == null) {
        continue;
      }
      for (EReference reference : object.eClass().getEAllReferences()) {
        if (reference.isContainment() || reference.isContainer() || !reference.isChangeable()) {
          continue;
        }
        String raw = attributes.get(reference.getName());
        if (raw == null || raw.isBlank()) {
          continue;
        }
        if (reference.isMany()) {
          @SuppressWarnings("unchecked")
          List<EObject> current = (List<EObject>) object.eGet(reference);
          if (!current.isEmpty()) {
            continue;
          }
          for (String id : raw.trim().split("\\s+")) {
            EObject target = objectsById.get(id);
            if (target != null && reference.getEReferenceType().isInstance(target)) {
              current.add(target);
            }
          }
        } else if (object.eGet(reference) == null) {
          EObject target = objectsById.get(raw.trim());
          if (target != null && reference.getEReferenceType().isInstance(target)) {
            object.eSet(reference, target);
          }
        }
      }
    }
  }

  /** Collects an object's full containment tree. */
  private void collectObjects(EObject object, List<EObject> objects) {
    object
        .eContents()
        .forEach(
            child -> {
              objects.add(child);
              collectObjects(child, objects);
            });
  }

  /** Reads serialized XML reference attributes keyed by xmi:id. */
  private Map<String, Map<String, String>> rawReferenceAttributes(byte[] bytes) {
    Map<String, Map<String, String>> result = new HashMap<>();
    try {
      XMLStreamReader reader =
          XMLInputFactory.newFactory().createXMLStreamReader(new ByteArrayInputStream(bytes));
      while (reader.hasNext()) {
        if (reader.next() == XMLStreamConstants.START_ELEMENT) {
          String id = reader.getAttributeValue("http://www.omg.org/XMI", "id");
          if (id == null || id.isBlank()) {
            id = reader.getAttributeValue(null, "id");
          }
          if (id == null || id.isBlank()) {
            continue;
          }
          Map<String, String> attributes = new HashMap<>();
          for (int index = 0; index < reader.getAttributeCount(); index++) {
            String name = reader.getAttributeLocalName(index);
            String value = reader.getAttributeValue(index);
            if (!"id".equals(name) && !"xmi".equals(reader.getAttributePrefix(index))) {
              attributes.put(name, value);
            }
          }
          result.put(id, attributes);
        }
      }
      reader.close();
    } catch (Exception ignored) {
      // Normal EMF loading has already validated the XMI; repair is best effort.
    }
    return result;
  }

  /**
   * Verifies the EMF root class matches the requested model level.
   *
   * @param level expected model level
   * @param root root EObject
   */
  private void validateRoot(ModelLevel level, EObject root) {
    String expected = expectedRootEClass(level);
    String actual = root.eClass().getName();
    if (!expected.equals(actual)) {
      throw new PlatformException(
          400,
          "Expected "
              + expected
              + " root for "
              + level.name()
              + " model but found "
              + actual
              + ".");
    }
  }

  /**
   * Returns the expected root EClass name for a model level.
   *
   * @param level model level
   * @return expected root class name
   */
  private String expectedRootEClass(ModelLevel level) {
    String expected =
        String.valueOf(mapValue(levelConfig(level).get("rootTemplate")).getOrDefault("eClass", ""));
    if (expected.isBlank()) {
      throw new PlatformException(
          500, "Missing configured rootTemplate.eClass for " + level.apiName());
    }
    return expected;
  }

  private Map<String, Object> levelConfig(ModelLevel level) {
    Map<String, Object> config = modelingConfig.config();
    Map<String, Object> levels = mapValue(config.get("levels"));
    Map<String, Object> levelConfig = mapValue(levels.get(level.apiName()));
    if (levelConfig.isEmpty()) {
      throw new PlatformException(500, "Missing modeling config for " + level.apiName());
    }
    return levelConfig;
  }

  private String traceRelationshipObjectType(ModelLevel level) {
    Map<String, Object> config = levelConfig(level);
    String traceKind =
        String.valueOf(mapValue(config.get("relationshipSemantics")).getOrDefault("traceKind", ""));
    if (traceKind.isBlank()) {
      return "";
    }
    for (Map<String, Object> rule : mapList(config.get("semanticEdgeObjectRules"))) {
      if (stringList(rule.get("matchKinds")).contains(traceKind)) {
        return String.valueOf(rule.getOrDefault("eClass", ""));
      }
    }
    return "";
  }

  private Map<String, Object> mapValue(Object value) {
    if (!(value instanceof Map<?, ?> raw)) {
      return Map.of();
    }
    Map<String, Object> result = new java.util.LinkedHashMap<>();
    raw.forEach((key, item) -> result.put(String.valueOf(key), item));
    return result;
  }

  private List<Map<String, Object>> mapList(Object value) {
    if (!(value instanceof List<?> rawList)) {
      return List.of();
    }
    List<Map<String, Object>> result = new ArrayList<>();
    for (Object item : rawList) {
      if (item instanceof Map<?, ?> raw) {
        result.add(mapValue(raw));
      }
    }
    return result;
  }

  private List<String> stringList(Object value) {
    if (!(value instanceof List<?> rawList)) {
      return List.of();
    }
    return rawList.stream().map(String::valueOf).toList();
  }

  /**
   * Reads a scalar JSON value as trimmed text.
   *
   * @param value JSON value
   * @return text or empty string
   */
  private String scalarText(JsonNode value) {
    if (value == null || value.isNull() || value.isContainer()) {
      return "";
    }
    return value.asText("").trim();
  }

  /**
   * Restores source and target endpoint fields on imported relationship JSON from graph
   * relationship records.
   *
   * @param rootJson imported root JSON
   */
  private void restoreRelationshipEndpoints(ModelLevel level, ObjectNode rootJson) {
    JsonNode graphRelationships = rootJson.path("graph").path("relationships");
    if (!graphRelationships.isArray()) {
      return;
    }
    Map<String, JsonNode> relationshipsById = new java.util.LinkedHashMap<>();
    graphRelationships.forEach(
        relationship -> {
          String id = scalarText(relationship.get("id"));
          if (!id.isBlank()) {
            relationshipsById.put(id, relationship);
          }
        });
    if (relationshipsById.isEmpty()) {
      return;
    }
    restoreRelationshipEndpoints(rootJson, relationshipsById, traceRelationshipObjectType(level));
  }

  /**
   * Recursively restores relationship endpoint fields from graph relationships.
   *
   * @param node current JSON node
   * @param relationshipsById graph relationships keyed by id
   */
  private void restoreRelationshipEndpoints(
      JsonNode node, Map<String, JsonNode> relationshipsById, String traceRelationshipType) {
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
      restoreTraceEndpointIds(object, traceRelationshipType);
      object
          .properties()
          .forEach(
              entry ->
                  restoreRelationshipEndpoints(
                      entry.getValue(), relationshipsById, traceRelationshipType));
      return;
    }
    if (node.isArray()) {
      node.forEach(
          child -> restoreRelationshipEndpoints(child, relationshipsById, traceRelationshipType));
    }
  }

  /**
   * Restores trace relationship endpoint id fields after import.
   *
   * @param object imported object JSON
   * @param traceRelationshipType configured trace relationship object type
   */
  private void restoreTraceEndpointIds(ObjectNode object, String traceRelationshipType) {
    if (traceRelationshipType.isBlank()
        || !traceRelationshipType.equals(scalarText(object.get("eClass")))) {
      return;
    }
    copyTextIfMissing(object, object, "sourceElementId", "source");
    copyTextIfMissing(object, object, "targetElementId", "target");
  }

  /**
   * Copies a text field from a source object when the target field is blank.
   *
   * @param target target object to update
   * @param source source object to read
   * @param fieldName preferred field name
   * @param fallbackFieldName fallback field name
   */
  private void copyTextIfMissing(
      ObjectNode target, JsonNode source, String fieldName, String fallbackFieldName) {
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

  /**
   * Reconstructs the derived PSM allResources field from stack resources when it is absent.
   *
   * @param level model level
   * @param rootJson imported root JSON
   */
  private void restoreDerivedPsmAllResources(ModelLevel level, ObjectNode rootJson) {
    if (level != ModelLevel.PSM || !rootJson.path("allResources").isEmpty()) {
      return;
    }
    ArrayNode allResources = objectMapper.createArrayNode();
    JsonNode stacks = rootJson.path("stacks");
    if (stacks.isArray()) {
      stacks.forEach(
          stack -> {
            JsonNode resources = stack.path("resources");
            if (resources.isArray()) {
              resources.forEach(resource -> collectPsmResourceIds(resource, allResources));
            }
          });
    }
    rootJson.set("allResources", allResources);
  }

  private void collectPsmResourceIds(JsonNode resource, ArrayNode allResources) {
    if (resource == null || !resource.isObject()) {
      return;
    }
    String id = scalarText(resource.get("id"));
    if (!id.isBlank()) {
      allResources.add(id);
    }
    resource
        .properties()
        .forEach(
            entry -> {
              JsonNode value = entry.getValue();
              if (value.isArray()) {
                value.forEach(child -> collectPsmResourceIds(child, allResources));
              } else if (value.isObject() && value.has("id") && value.has("eClass")) {
                collectPsmResourceIds(value, allResources);
              }
            });
  }

  /**
   * Serializes an EMF object and its contained contents to platform JSON.
   *
   * @param object EMF object to serialize
   * @param context serialization context
   * @return serialized JSON object, or {@code null} for already-serialized objects
   */
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

      ensureTraceEndpointIds(object, node, context);
      context.captureGraphObject(object, node);
      return node;
    } finally {
      context.endSerialization(object);
    }
  }

  /**
   * Adds trace relationship endpoint id fields derived from EMF references.
   *
   * @param object EMF object being serialized
   * @param node JSON node being populated
   * @param context serialization context
   */
  private void ensureTraceEndpointIds(
      EObject object, ObjectNode node, SerializationContext context) {
    if (!context.isTraceObject(object)) {
      return;
    }
    copyReferenceIdAttributeIfMissing(object, node, context, "source", "sourceElementId");
    copyReferenceIdAttributeIfMissing(object, node, context, "target", "targetElementId");
  }

  /**
   * Copies a referenced object's stable id into a scalar attribute field when the field is missing.
   *
   * @param object EMF object being serialized
   * @param node JSON node being populated
   * @param context serialization context
   * @param referenceName EMF reference name
   * @param attributeName JSON attribute name
   */
  private void copyReferenceIdAttributeIfMissing(
      EObject object,
      ObjectNode node,
      SerializationContext context,
      String referenceName,
      String attributeName) {
    if (node.hasNonNull(attributeName) && !node.path(attributeName).asText("").isBlank()) {
      return;
    }
    EStructuralFeature reference = object.eClass().getEStructuralFeature(referenceName);
    Object value = reference == null ? null : object.eGet(reference);
    if (value instanceof EObject target) {
      node.put(attributeName, context.ensureId(target));
    }
  }

  /**
   * Checks whether an attribute should be serialized even when unset.
   *
   * @param attribute EMF attribute
   * @return {@code true} for required scalar attributes
   */
  private boolean isRequiredAttribute(EAttribute attribute) {
    return attribute.getLowerBound() > 0 && !attribute.isMany();
  }

  /**
   * Serializes containment reference values and records containment graph edges.
   *
   * @param owner owning EMF object
   * @param reference containment reference
   * @param context serialization context
   * @return JSON value for the containment reference
   */
  private JsonNode containmentValueNode(
      EObject owner, EReference reference, SerializationContext context) {
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

  /**
   * Serializes an attribute value.
   *
   * @param owner owning EMF object
   * @param attribute EMF attribute
   * @return JSON value for the attribute
   */
  private JsonNode attributeValueNode(EObject owner, EAttribute attribute) {
    Object rawValue = owner.eGet(attribute);
    if (attribute.isMany()) {
      List<?> values = rawValue instanceof List<?> list ? list : List.of();
      ArrayNode array = objectMapper.createArrayNode();
      values.stream()
          .map(this::normalizePrimitive)
          .forEach(value -> array.add(objectMapper.valueToTree(value)));
      return array;
    }
    return objectMapper.valueToTree(normalizePrimitive(rawValue));
  }

  /**
   * Serializes a non-containment reference as one or more target ids.
   *
   * @param owner owning EMF object
   * @param reference EMF reference
   * @param context serialization context
   * @return JSON value for the reference
   */
  private JsonNode referenceValueNode(
      EObject owner, EReference reference, SerializationContext context) {
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

  /**
   * Converts EMF primitive values to JSON-friendly representations.
   *
   * @param value raw EMF value
   * @return normalized primitive
   */
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

  /**
   * Export conversion diagnostic before it is rendered in an exception.
   *
   * @param ownerType EMF owner type
   * @param ownerId owner id when available
   * @param feature feature that failed
   * @param message diagnostic message
   */
  private record ExportDiagnostic(
      String ownerType, String ownerId, String feature, String message) {}

  /**
   * Deferred reference assignment collected while creating EMF objects.
   *
   * @param owner owner object
   * @param reference reference to resolve
   * @param value JSON reference value
   */
  private record PendingReference(EObject owner, EReference reference, JsonNode value) {}

  /** Accumulates JSON-to-XMI conversion diagnostics according to export strictness options. */
  private final class ExportDiagnostics {

    /** Export strictness options. */
    private final XmiExportOptions options;

    /** Collected conversion errors. */
    private final List<ExportDiagnostic> errors = new ArrayList<>();

    /**
     * Creates an export diagnostics accumulator.
     *
     * @param options export strictness options
     */
    ExportDiagnostics(XmiExportOptions options) {
      this.options = options == null ? XmiExportOptions.strict() : options;
    }

    /**
     * Records an attribute conversion error when strict attributes are enabled.
     *
     * @param owner owner object
     * @param attribute attribute that failed
     * @param message diagnostic message
     */
    void attributeError(EObject owner, EAttribute attribute, String message) {
      if (options.strictAttributes()) {
        error(owner, attribute.getName(), message);
      }
    }

    /**
     * Records a reference conversion error when strict references are enabled.
     *
     * @param owner owner object
     * @param reference reference that failed
     * @param message diagnostic message
     */
    void referenceError(EObject owner, EReference reference, String message) {
      if (options.strictReferences()) {
        error(owner, reference.getName(), message);
      }
    }

    /**
     * Records a conversion error.
     *
     * @param owner owner object
     * @param feature feature that failed
     * @param message diagnostic message
     */
    void error(EObject owner, String feature, String message) {
      errors.add(
          new ExportDiagnostic(
              owner == null ? "" : owner.eClass().getName(),
              ownerId(owner),
              feature == null ? "" : feature,
              message == null ? "" : message));
    }

    /** Throws a platform exception when conversion errors were collected. */
    void throwIfErrors() {
      if (errors.isEmpty()) {
        return;
      }
      String details =
          errors.stream()
              .limit(5)
              .map(this::message)
              .collect(java.util.stream.Collectors.joining("; "));
      if (errors.size() > 5) {
        details = details + "; and " + (errors.size() - 5) + " more";
      }
      throw new PlatformException(400, "Model JSON cannot be exported as XMI: " + details);
    }

    /**
     * Formats one export diagnostic.
     *
     * @param diagnostic diagnostic to format
     * @return formatted diagnostic message
     */
    private String message(ExportDiagnostic diagnostic) {
      String owner = diagnostic.ownerType();
      if (!diagnostic.ownerId().isBlank()) {
        owner = owner + "[" + diagnostic.ownerId() + "]";
      }
      String feature = diagnostic.feature().isBlank() ? "" : "." + diagnostic.feature();
      return owner + feature + ": " + diagnostic.message();
    }

    /**
     * Extracts an owner's id attribute when available.
     *
     * @param owner owner object
     * @return owner id or empty string
     */
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

  /**
   * Builds EMF objects from platform JSON and resolves references after all contained objects have
   * been created.
   */
  private final class ExportContext {

    /** Resource set containing registered metamodel packages. */
    private final ResourceSet resourceSet;

    /** Diagnostics sink for conversion errors. */
    private final ExportDiagnostics diagnostics;

    /** Configured trace relationship object type for this level. */
    private final String traceRelationshipType;

    /** Created EMF objects keyed by explicit model id. */
    private final Map<String, EObject> objectsById = new java.util.LinkedHashMap<>();

    /** Non-containment references to resolve after object creation. */
    private final List<PendingReference> pendingReferences = new ArrayList<>();

    /**
     * Creates an export context.
     *
     * @param resourceSet resource set containing registered metamodels
     * @param diagnostics diagnostics sink
     */
    ExportContext(ResourceSet resourceSet, ExportDiagnostics diagnostics, ModelLevel level) {
      this.resourceSet = resourceSet;
      this.diagnostics = diagnostics;
      this.traceRelationshipType = traceRelationshipObjectType(level);
    }

    /**
     * Creates an EMF object and recursively creates contained children.
     *
     * @param node source JSON node
     * @param expectedType expected containment type, or {@code null}
     * @return created object, or {@code null} when input is not an object
     */
    EObject createContainedObject(JsonNode node, EClass expectedType) {
      if (node == null || !node.isObject()) {
        return null;
      }
      EClass eClass =
          eClassFor(node.path("eClass").asText(node.path("type").asText("")), expectedType);
      String objectId = scalarText(node.get("id"));
      if (!objectId.isBlank()) {
        EObject existing = objectsById.get(objectId);
        if (existing != null) {
          if (existing.eClass() == eClass) {
            return existing;
          }
          EObject duplicate = eClass.getEPackage().getEFactoryInstance().create(eClass);
          diagnostics.error(duplicate, "id", "Duplicate model element id '" + objectId + "'.");
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
          if (feature instanceof EAttribute attribute) {
            String implied = impliedEnumLiteral(eClass, attribute);
            if (implied != null) {
              setAttribute(object, attribute, objectMapper.getNodeFactory().textNode(implied));
            }
          }
          continue;
        }
        if (feature instanceof EAttribute attribute) {
          setAttribute(object, attribute, value);
          continue;
        }
        if (feature instanceof EReference reference) {
          if (reference.isContainment()) {
            setContainment(object, reference, value);
          } else if (!reference.isContainer() && !isInverseTraceReference(reference)) {
            pendingReferences.add(new PendingReference(object, reference, value));
          }
        }
      }
      applySafeAttributeDefaults(object);
      return object;
    }

    /**
     * Applies Ecore default values for unset scalar attributes.
     *
     * @param object EMF object to mutate
     */
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

    /**
     * Identifies inverse trace references that should be skipped during export.
     *
     * @param reference EMF reference
     * @return {@code true} for inverse trace references
     */
    private boolean isInverseTraceReference(EReference reference) {
      String name = reference.getName();
      return ("incomingTraces".equals(name) || "outgoingTraces".equals(name))
          && traceRelationshipType.equals(reference.getEReferenceType().getName());
    }

    /**
     * Sets a containment reference from JSON.
     *
     * @param owner owning EMF object
     * @param reference containment reference
     * @param value JSON value
     */
    private void setContainment(EObject owner, EReference reference, JsonNode value) {
      if (reference.isMany()) {
        @SuppressWarnings("unchecked")
        List<EObject> target = (List<EObject>) owner.eGet(reference);
        if (value.isArray()) {
          value.forEach(
              item -> {
                EObject child = createContainedObject(item, reference.getEReferenceType());
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

    /**
     * Sets an attribute from JSON.
     *
     * @param object owning EMF object
     * @param attribute attribute to set
     * @param value JSON value
     */
    private void setAttribute(EObject object, EAttribute attribute, JsonNode value) {
      if (attribute.isMany()) {
        @SuppressWarnings("unchecked")
        List<Object> target = (List<Object>) object.eGet(attribute);
        if (!value.isArray()) {
          // Older frontend saves represented editable String[*] fields as a comma-separated
          // scalar. Accept that legacy shape at the XMI boundary so one such edit cannot make
          // the whole transformation pipeline unusable. New writes are normalized by the UI.
          if (value.isTextual() && "modelTags".equals(attribute.getName())) {
            for (String item : value.asText().split(",")) {
              Object converted =
                  attributeValue(
                      object, attribute, objectMapper.getNodeFactory().textNode(item.trim()));
              if (converted != null && !item.isBlank()) {
                target.add(converted);
              }
            }
          } else {
            Object converted = attributeValue(object, attribute, value);
            if (converted != null) {
              target.add(converted);
            }
          }
          return;
        }
        if (value.isArray()) {
          value.forEach(
              item -> {
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

    /** Infers a required enum discriminator when the concrete EClass names one option exactly. */
    private String impliedEnumLiteral(EClass eClass, EAttribute attribute) {
      if (attribute.isMany()
          || attribute.getLowerBound() < 1
          || !(attribute.getEAttributeType() instanceof EEnum eEnum)) {
        return null;
      }
      String typeName =
          eClass
              .getName()
              .replaceFirst("(Step|State)$", "")
              .replaceAll("([a-z])([A-Z])", "$1_$2")
              .toUpperCase(java.util.Locale.ROOT);
      if (typeName.isBlank()) {
        return null;
      }
      List<EEnumLiteral> matches =
          eEnum.getELiterals().stream()
              .filter(literal -> typeName.equals(literal.getName()))
              .toList();
      return matches.size() == 1 ? matches.get(0).getLiteral() : null;
    }

    /**
     * Converts a JSON scalar to an EMF attribute value.
     *
     * @param owner owning EMF object
     * @param attribute attribute to set
     * @param value JSON scalar value
     * @return converted value, or {@code null} when conversion failed
     */
    private Object attributeValue(EObject owner, EAttribute attribute, JsonNode value) {
      if (value != null && value.isContainer()) {
        diagnostics.attributeError(
            owner, attribute, "Expected a scalar value but found " + value.getNodeType() + ".");
        return null;
      }
      EDataType type = attribute.getEAttributeType();
      if (type instanceof EEnum eEnum) {
        String literal = scalarText(value);
        if (literal.isBlank()) {
          return null;
        }
        EEnumLiteral enumLiteral = eEnum.getEEnumLiteral(literal);
        if (enumLiteral == null) {
          enumLiteral = eEnum.getEEnumLiteralByLiteral(literal);
        }
        if (enumLiteral == null) {
          try {
            enumLiteral = eEnum.getEEnumLiteral(Integer.parseInt(literal));
          } catch (NumberFormatException ignored) {
            // The final diagnostic below reports the original invalid value.
          }
        }
        if (enumLiteral == null) {
          diagnostics.attributeError(owner, attribute, "Unknown enum literal '" + literal + "'.");
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
        diagnostics.attributeError(
            owner, attribute, "Invalid value '" + text + "': " + ex.getMessage());
        return null;
      }
    }

    /** Resolves all deferred non-containment references. */
    void resolveReferences() {
      for (PendingReference pending : pendingReferences) {
        if (pending.reference().isMany()) {
          @SuppressWarnings("unchecked")
          List<EObject> target = (List<EObject>) pending.owner().eGet(pending.reference());
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

    /**
     * Resolves one reference id for a pending reference.
     *
     * @param pending pending reference metadata
     * @param id target id
     * @return target object, or {@code null} when unresolved/invalid
     */
    private EObject resolveReference(PendingReference pending, String id) {
      EObject target = objectsById.get(id);
      if (target == null) {
        diagnostics.referenceError(
            pending.owner(), pending.reference(), "Unresolved reference id '" + id + "'.");
        return null;
      }
      if (!pending.reference().getEReferenceType().isInstance(target)) {
        diagnostics.referenceError(
            pending.owner(),
            pending.reference(),
            "Reference id '"
                + id
                + "' resolves to "
                + target.eClass().getName()
                + ", which is not valid for "
                + pending.reference().getEReferenceType().getName()
                + ".");
        return null;
      }
      return target;
    }

    /**
     * Extracts one or more reference ids from a JSON value.
     *
     * @param value JSON reference value
     * @return reference ids
     */
    private List<String> referenceIds(JsonNode value) {
      if (value == null || value.isNull()) {
        return List.of();
      }
      if (value.isArray()) {
        List<String> ids = new ArrayList<>();
        value.forEach(
            item -> {
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

    /**
     * Extracts a single reference id from a scalar or object value.
     *
     * @param value JSON reference value
     * @return reference id or empty string
     */
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

    /**
     * Resolves the EClass requested by a JSON node and validates containment compatibility.
     *
     * @param requestedType requested type name
     * @param expectedType expected containment type
     * @return resolved EClass
     */
    private EClass eClassFor(String requestedType, EClass expectedType) {
      if (requestedType != null && !requestedType.isBlank()) {
        EClass found = findEClass(requestedType);
        if (found == null) {
          throw new PlatformException(400, "Unknown model element type: " + requestedType);
        }
        if (expectedType != null && !expectedType.isSuperTypeOf(found)) {
          throw new PlatformException(
              400,
              "Element type "
                  + requestedType
                  + " is not valid for containment "
                  + expectedType.getName()
                  + ".");
        }
        return found;
      }
      if (expectedType != null) {
        return expectedType;
      }
      throw new PlatformException(400, "Missing model element type.");
    }

    /**
     * Finds an EClass by name across registered packages.
     *
     * @param name EClass name
     * @return matching EClass, or {@code null}
     */
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

    /**
     * Recursively finds an EClass in a package tree.
     *
     * @param ePackage package to inspect
     * @param name EClass name
     * @return matching EClass, or {@code null}
     */
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

  /**
   * Tracks EMF-to-JSON serialization state and reconstructs the platform graph projection while
   * walking contained objects.
   */
  private final class SerializationContext {

    /** Model level being imported. */
    private final ModelLevel level;

    /** Configured semantic relationship-object rules keyed by EClass. */
    private final Map<String, Map<String, Object>> edgeObjectRulesByClass;

    /** Configured semantic reference rules derived from metadata and Ecore. */
    private final List<Map<String, Object>> semanticReferenceRules;

    /** Feature-name to relationship-kind overrides for containment and reference edges. */
    private final Map<String, Object> semanticReferenceKindMappings;

    /** Canonical containment relationship kind from modeling metadata. */
    private final String containmentKind;

    /** Stable ids assigned to EMF objects by identity. */
    private final Map<EObject, String> ids = new IdentityHashMap<>();

    /** Objects currently in the recursion stack. */
    private final Set<EObject> serializing =
        java.util.Collections.newSetFromMap(new IdentityHashMap<>());

    /** Objects already serialized. */
    private final Set<EObject> serialized =
        java.util.Collections.newSetFromMap(new IdentityHashMap<>());

    /** Ids already emitted while repairing imported external models. */
    private final Set<String> usedIds = new LinkedHashSet<>();

    /** Graph element nodes reconstructed during serialization. */
    private final List<ObjectNode> graphElements = new ArrayList<>();

    /** Graph relationships reconstructed during serialization. */
    private final List<ObjectNode> graphRelationships = new ArrayList<>();

    /** Trace links reconstructed during serialization. */
    private final List<ObjectNode> graphTraceLinks = new ArrayList<>();

    /** Configured trace relationship object type for this level. */
    private final String traceRelationshipType;

    /** De-duplication keys for reconstructed graph relationships. */
    private final Set<String> graphRelationshipKeys = new LinkedHashSet<>();

    /**
     * Creates a serialization context.
     *
     * @param level model level being imported
     */
    SerializationContext(ModelLevel level) {
      this.level = level;
      Map<String, Object> levelConfig = levelConfig(level);
      this.semanticReferenceRules = mapList(levelConfig.get("semanticReferenceRules"));
      this.semanticReferenceKindMappings =
          mapValue(levelConfig.get("semanticReferenceKindMappings"));
      this.containmentKind =
          String.valueOf(
              mapValue(levelConfig.get("relationshipSemantics"))
                  .getOrDefault("containmentKind", ""));
      if (this.containmentKind.isBlank()) {
        throw new PlatformException(
            500, "Missing configured containmentKind for " + level.apiName());
      }
      this.edgeObjectRulesByClass = new java.util.LinkedHashMap<>();
      for (Map<String, Object> rule : mapList(levelConfig.get("semanticEdgeObjectRules"))) {
        this.edgeObjectRulesByClass.put(String.valueOf(rule.getOrDefault("eClass", "")), rule);
      }
      this.traceRelationshipType = traceRelationshipObjectType(level);
    }

    private Map<String, Object> mapValue(Object value) {
      if (!(value instanceof Map<?, ?> raw)) {
        return Map.of();
      }
      Map<String, Object> result = new java.util.LinkedHashMap<>();
      raw.forEach((key, item) -> result.put(String.valueOf(key), item));
      return result;
    }

    private List<Map<String, Object>> mapList(Object value) {
      if (!(value instanceof List<?> rawList)) {
        return List.of();
      }
      return rawList.stream().map(this::mapValue).filter(rule -> !rule.isEmpty()).toList();
    }

    private List<String> stringList(Object value) {
      if (!(value instanceof List<?> rawList)) {
        return List.of();
      }
      return rawList.stream().map(String::valueOf).toList();
    }

    private boolean isTraceObject(EObject object) {
      return object != null && isTraceObject(object.eClass().getName());
    }

    private boolean isTraceObject(String type) {
      return !traceRelationshipType.isBlank() && traceRelationshipType.equals(type);
    }

    /**
     * Returns or creates a stable id for an EMF object.
     *
     * @param object EMF object
     * @return stable id
     */
    String ensureId(EObject object) {
      return ids.computeIfAbsent(object, this::createId);
    }

    /**
     * Marks an object as actively serializing.
     *
     * @param object EMF object
     * @return {@code false} when the object was already serialized
     */
    boolean beginSerialization(EObject object) {
      if (serialized.contains(object)) {
        return false;
      }
      return serializing.add(object);
    }

    /**
     * Marks an object as fully serialized.
     *
     * @param object EMF object
     */
    void endSerialization(EObject object) {
      serializing.remove(object);
      serialized.add(object);
    }

    /** Preserves an external id when possible and generates a UUID when one is unavailable. */
    private String createId(EObject object) {
      String existingId = explicitId(object);
      if (!existingId.isBlank() && usedIds.add(existingId)) {
        return existingId;
      }
      String xmiId = EcoreUtil.getID(object);
      if (xmiId != null && !xmiId.isBlank() && usedIds.add(xmiId)) {
        return xmiId;
      }
      return UUID.randomUUID().toString();
    }

    /**
     * Reads an explicit id feature from an EMF object.
     *
     * @param object EMF object
     * @return explicit id or empty string
     */
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

    /**
     * Adds an object to the reconstructed graph projection when appropriate.
     *
     * @param object EMF object
     * @param semanticNode serialized semantic JSON node
     */
    void captureGraphObject(EObject object, ObjectNode semanticNode) {
      if (isTraceObject(object)) {
        graphTraceLinks.add(shallowGraphElement(semanticNode));
        return;
      }
      if (isGraphSupportObject(object)) {
        return;
      }
      if (isRelationshipObject(object)) {
        captureGraphRelationship(object, semanticNode);
        return;
      }

      ObjectNode element = shallowGraphElement(semanticNode);
      graphElements.add(element);
      captureReferenceEdges(object);
    }

    /**
     * Creates a shallow graph element from scalar semantic fields.
     *
     * @param semanticNode serialized semantic JSON node
     * @return graph element JSON
     */
    private ObjectNode shallowGraphElement(ObjectNode semanticNode) {
      ObjectNode element = objectMapper.createObjectNode();
      semanticNode
          .properties()
          .forEach(
              entry -> {
                JsonNode value = entry.getValue();
                if (value == null || value.isArray() || value.isObject()) {
                  return;
                }
                element.set(entry.getKey(), value.deepCopy());
              });
      if (!element.has("label") && element.hasNonNull("name")) {
        element.set("label", element.get("name").deepCopy());
      }
      copyLayoutAnnotation(semanticNode, element, "varka.layout.x", "x");
      copyLayoutAnnotation(semanticNode, element, "varka.layout.y", "y");
      return element;
    }

    /**
     * Copies a layout annotation from semantic JSON into graph coordinates.
     *
     * @param semanticNode semantic node containing annotations
     * @param element graph element to mutate
     * @param key annotation key
     * @param fieldName graph coordinate field
     */
    private void copyLayoutAnnotation(
        ObjectNode semanticNode, ObjectNode element, String key, String fieldName) {
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

    /**
     * Captures a semantic relationship object as a graph relationship.
     *
     * @param object relationship EMF object
     * @param semanticNode serialized semantic JSON
     */
    private void captureGraphRelationship(EObject object, ObjectNode semanticNode) {
      Map<String, Object> rule = edgeObjectRulesByClass.get(object.eClass().getName());
      EObject source = relationshipEndpoint(object, rule, true);
      EObject target = relationshipEndpoint(object, rule, false);
      ObjectNode relationship = objectMapper.createObjectNode();
      relationship.put("id", semanticNode.path("id").asText());
      relationship.put("eClass", object.eClass().getName());
      relationship.put("kind", relationshipKindForObject(object));
      relationship.put("source", ensureId(source));
      relationship.put("target", ensureId(target));
      relationship.put("sourceElementId", relationship.path("source").asText());
      relationship.put("targetElementId", relationship.path("target").asText());
      copyScalarIfPresent(semanticNode, relationship, "name");
      copyScalarIfPresent(semanticNode, relationship, "label");
      copyScalarIfPresent(semanticNode, relationship, "relationshipType");
      graphRelationships.add(relationship);
      graphRelationshipKeys.add(graphEdgeKey(relationship));
    }

    /**
     * Returns the graph relationship kind for a semantic relationship object.
     *
     * @param object relationship object
     * @return relationship kind
     */
    private String relationshipKindForObject(EObject object) {
      Map<String, Object> rule = edgeObjectRulesByClass.get(object.eClass().getName());
      List<String> allowedKinds = stringList(rule.get("matchKinds"));
      String kindField = String.valueOf(rule.getOrDefault("kindField", ""));
      String fieldValue = relationshipKindAttribute(object, kindField);
      String mappedKind =
          String.valueOf(mapValue(rule.get("kindMap")).getOrDefault(fieldValue, fieldValue));
      for (String candidate :
          List.of(mappedKind, String.valueOf(rule.getOrDefault("defaultKind", "")))) {
        if (!candidate.isBlank() && allowedKinds.contains(candidate)) {
          return candidate;
        }
      }
      if (allowedKinds.size() == 1) {
        return allowedKinds.get(0);
      }
      throw new PlatformException(
          500,
          "Cannot resolve relationship kind for "
              + object.eClass().getName()
              + " from modeling metadata.");
    }

    /** Returns a normalized relationship kind stored in a semantic attribute. */
    private String relationshipKindAttribute(EObject object, String attributeName) {
      EStructuralFeature feature = object.eClass().getEStructuralFeature(attributeName);
      if (feature == null || feature instanceof EReference) {
        return "";
      }
      Object value = object.eGet(feature);
      return value == null ? "" : relationshipKind(String.valueOf(value));
    }

    /**
     * Captures non-containment EMF references as visual graph edges.
     *
     * @param object source EMF object
     */
    private void captureReferenceEdges(EObject object) {
      String sourceId = ensureId(object);
      for (EReference reference : object.eClass().getEAllReferences()) {
        if (reference.isContainment() || reference.isContainer()) {
          continue;
        }
        if (isRelationshipEndpointFeature(reference, object)) {
          continue;
        }
        if (isGraphSupportReference(reference)) {
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

    /**
     * Adds a graph edge for one non-containment reference.
     *
     * @param sourceId source id
     * @param sourceObject source EMF object
     * @param reference EMF reference
     * @param targetObject target EMF object
     */
    private void addReferenceEdge(
        String sourceId, EObject sourceObject, EReference reference, EObject targetObject) {
      if (isGraphSupportObject(targetObject) || isRelationshipObject(targetObject)) {
        return;
      }
      String targetId = ensureId(targetObject);
      String kind = configuredReferenceKind(sourceObject, reference, targetObject);
      if (kind.isBlank()) {
        return;
      }
      String key = sourceId + "|" + targetId + "|" + kind;
      if (!graphRelationshipKeys.add(key)) {
        return;
      }
      ObjectNode relationship = objectMapper.createObjectNode();
      relationship.put("id", UUID.randomUUID().toString());
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

    private String configuredReferenceKind(
        EObject sourceObject, EReference reference, EObject targetObject) {
      for (Map<String, Object> rule : semanticReferenceRules) {
        if (!reference.getName().equals(String.valueOf(rule.getOrDefault("feature", "")))) {
          continue;
        }
        String sourceType = String.valueOf(rule.getOrDefault("sourceType", ""));
        String targetType = String.valueOf(rule.getOrDefault("targetType", ""));
        if (matchesType(sourceObject.eClass(), sourceType)
            && matchesType(targetObject.eClass(), targetType)) {
          return String.valueOf(rule.getOrDefault("kind", ""));
        }
      }
      return "";
    }

    private boolean matchesType(EClass actual, String expected) {
      return "*".equals(expected)
          || actual.getName().equals(expected)
          || actual.getEAllSuperTypes().stream().anyMatch(type -> type.getName().equals(expected));
    }

    /**
     * Adds a graph edge for a containment reference when the child is visible in graph views.
     *
     * @param owner containing EMF object
     * @param reference containment reference
     * @param child contained EMF object
     */
    private void addContainmentEdge(EObject owner, EReference reference, EObject child) {
      if (isGraphSupportObject(child)
          || isRelationshipObject(child)
          || isRelationshipObject(owner)) {
        return;
      }
      String sourceId = ensureId(owner);
      String targetId = ensureId(child);
      String kind =
          String.valueOf(semanticReferenceKindMappings.getOrDefault(reference.getName(), ""));
      if (kind.isBlank()) {
        kind = configuredReferenceKind(owner, reference, child);
      }
      if (kind.isBlank()) {
        kind = containmentRelationshipKind();
      }
      String key = sourceId + "|" + targetId + "|" + kind;
      if (!graphRelationshipKeys.add(key)) {
        return;
      }
      ObjectNode relationship = objectMapper.createObjectNode();
      relationship.put("id", UUID.randomUUID().toString());
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

    /**
     * Detects semantic relationship objects with source and target references.
     *
     * @param object EMF object
     * @return {@code true} when the object is a relationship
     */
    private boolean isRelationshipObject(EObject object) {
      Map<String, Object> rule = edgeObjectRulesByClass.get(object.eClass().getName());
      return rule != null
          && relationshipEndpoint(object, rule, true) != null
          && relationshipEndpoint(object, rule, false) != null;
    }

    private EObject relationshipEndpoint(
        EObject object, Map<String, Object> rule, boolean sourceEndpoint) {
      if (sourceEndpoint && Boolean.TRUE.equals(rule.get("ownerAsSource"))) {
        return object.eContainer();
      }
      List<String> featureNames = new ArrayList<>();
      String singular =
          String.valueOf(rule.getOrDefault(sourceEndpoint ? "sourceFeature" : "targetFeature", ""));
      if (!singular.isBlank()) {
        featureNames.add(singular);
      }
      featureNames.addAll(
          stringList(rule.get(sourceEndpoint ? "sourceFeatures" : "targetFeatures")));
      for (String featureName : featureNames) {
        EStructuralFeature feature = object.eClass().getEStructuralFeature(featureName);
        if (feature != null && object.eGet(feature) instanceof EObject endpoint) {
          return endpoint;
        }
      }
      return null;
    }

    /**
     * Checks whether a reference is one of the endpoint references on a relationship object.
     *
     * @param reference EMF reference
     * @param owner owning object
     * @return {@code true} for source/target endpoint references
     */
    private boolean isRelationshipEndpointFeature(EReference reference, EObject owner) {
      return isRelationshipObject(owner)
          && ("source".equals(reference.getName()) || "target".equals(reference.getName()));
    }

    /**
     * Checks whether an object should be omitted from graph element output.
     *
     * @param object EMF object
     * @return {@code true} for support objects
     */
    private boolean isGraphSupportObject(EObject object) {
      if (isTraceObject(object)) {
        return true;
      }
      return switch (object.eClass().getName()) {
        case "Annotation", "AwsTag", "ReadinessFinding" -> true;
        default -> false;
      };
    }

    /**
     * Checks whether a reference should be omitted from graph edge output.
     *
     * @param reference EMF reference
     * @return {@code true} for support references
     */
    private boolean isGraphSupportReference(EReference reference) {
      return switch (reference.getName()) {
        case "incomingTraces", "outgoingTraces", "affectedElements" -> true;
        default -> isTraceObject(reference.getEReferenceType().getName());
      };
    }

    /**
     * Copies a scalar field when present.
     *
     * @param from source JSON object
     * @param to target JSON object
     * @param key field name
     */
    private void copyScalarIfPresent(ObjectNode from, ObjectNode to, String key) {
      JsonNode value = from.get(key);
      if (value != null && value.isValueNode()) {
        to.set(key, value.deepCopy());
      }
    }

    /**
     * Builds a de-duplication key for a graph relationship.
     *
     * @param relationship graph relationship
     * @return de-duplication key
     */
    private String graphEdgeKey(ObjectNode relationship) {
      return relationship.path("sourceElementId").asText()
          + "|"
          + relationship.path("targetElementId").asText()
          + "|"
          + relationship.path("kind").asText();
    }

    /**
     * Converts arbitrary text to an uppercase relationship kind token.
     *
     * @param value raw relationship value
     * @return relationship kind
     */
    private String relationshipKind(String value) {
      return String.valueOf(value)
          .replaceAll("([a-z0-9])([A-Z])", "$1_$2")
          .replaceAll("[^A-Za-z0-9]+", "_")
          .replaceAll("^_+|_+$", "")
          .toUpperCase(Locale.ROOT);
    }

    /**
     * Maps known reference feature names to user-facing relationship kinds.
     *
     * @param value reference feature name
     * @return relationship kind or empty string
     */
    /**
     * Maps containment feature names to relationship kinds.
     *
     * @return containment relationship kind
     */
    private String containmentRelationshipKind() {
      return containmentKind;
    }

    /**
     * Builds the final graph JSON object.
     *
     * @param mapper mapper used to create JSON nodes
     * @return graph JSON object
     */
    ObjectNode graphNode(ObjectMapper mapper) {
      ObjectNode graph = mapper.createObjectNode();
      ArrayNode elements = graph.putArray("elements");
      graphElements.forEach(element -> elements.add(element.deepCopy()));
      ArrayNode relationships = graph.putArray("relationships");
      graphRelationships.forEach(relationship -> relationships.add(relationship.deepCopy()));
      ArrayNode traceLinks = graph.putArray("traceLinks");
      graphTraceLinks.forEach(traceLink -> traceLinks.add(traceLink.deepCopy()));
      graph.putArray("assumptions");
      graph.putArray("validationIssues");
      graph.putArray("manualBacklog");
      return graph;
    }
  }
}
