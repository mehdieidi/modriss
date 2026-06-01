package io.mehdieidi.modless.platform.core.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.mehdieidi.modless.platform.core.PlatformException;
import io.mehdieidi.modless.platform.core.model.ModelLevel;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public final class ModelingConfigService {

    private final ObjectMapper objectMapper = new ObjectMapper();

    public Map<String, Object> config() {
        return Map.of(
                "version", 2,
                "dynamicPersistenceEnabled", true,
                "levels", Map.of(
                        "cim", level("cim"),
                        "pim", level("pim"),
                        "psm", level("psm")),
                "transformations", Map.of(
                        "cim_to_pim", transformation(),
                        "pim_to_psm", transformation(),
                        "psm_to_artifact", Map.of("enabled", true, "artifactType", "aws-sam",
                                "generationMode", "serverless_mda", "elementMappings", List.of(),
                                "relationshipMappings", List.of(), "templates", List.of(),
                                "projectStructure",
                                Map.of("directories", List.of(), "pathMappings", List.of(),
                                        "passthroughUnmatched", true, "emitGitkeep", true))));
    }

    private Map<String, Object> level(String key) {
        Map<String, Object> metadata = readMetadata(key);
        metadata = mergeEcoreStructure(key, metadata);
        List<String> relationshipKinds = relationshipKinds(metadata);
        return Map.ofEntries(
                Map.entry("displayName", metadata.getOrDefault("displayName", key.toUpperCase())),
                Map.entry("elementsPath", "/diagram/elements"),
                Map.entry("relationshipsPath", "/diagram/relationships"),
                Map.entry("labelField", "name"),
                Map.entry("relationshipKinds", relationshipKinds),
                Map.entry("elements", requireList(metadata, "elements", key)),
                Map.entry("relationshipRules", requireList(metadata, "relationshipRules", key)),
                Map.entry("relationshipKindLabels",
                        requireMap(metadata, "relationshipKindLabels", key)),
                Map.entry("semanticReferenceRules", metadata.getOrDefault(
                        "semanticReferenceRules", List.of())),
                Map.entry("shortcutConnectorRules",
                        metadata.getOrDefault("shortcutConnectorRules", List.of())),
                Map.entry("viewDefinitions", requireList(metadata, "viewDefinitions", key)),
                Map.entry("universalSyntax", metadata.getOrDefault("universalSyntax", List.of())),
                Map.entry("kernelSyntax", metadata.getOrDefault("kernelSyntax", List.of())),
                Map.entry("kernelNotation", metadata.getOrDefault("kernelNotation",
                        metadata.getOrDefault("kernelSyntax", List.of()))),
                Map.entry("complexityManagement",
                        metadata.getOrDefault("complexityManagement", List.of())),
                Map.entry("strictnessModes", metadata.getOrDefault("strictnessModes",
                        List.of("exploration", "methodology", "production"))),
                Map.entry("constraints", metadata.getOrDefault("constraints", List.of())),
                Map.entry("rootTemplate", requireMap(metadata, "rootTemplate", key)));
    }

    private Map<String, Object> mergeEcoreStructure(String key, Map<String, Object> metadata) {
        Map<String, Object> merged = new LinkedHashMap<>(metadata);
        CimMetamodel metamodel = readEcoreMetamodel(key);
        merged.put("elements", mergeElements(key, metamodel.elements(),
                requireList(metadata, "elements", key)));
        if (!merged.containsKey("semanticReferenceRules")) {
            merged.put("semanticReferenceRules", metamodel.semanticReferenceRules());
        }
        requireList(merged, "relationshipRules", key);
        requireList(merged, "viewDefinitions", key);
        requireMap(merged, "relationshipKindLabels", key);
        requireMap(merged, "rootTemplate", key);
        return merged;
    }

    private List<Map<String, Object>> mergeElements(String key,
            List<Map<String, Object>> structuralElements, List<?> uiElements) {
        Map<String, Map<String, Object>> uiByType = new LinkedHashMap<>();
        for (Object item : uiElements) {
            if (!(item instanceof Map<?, ?> raw)) {
                throw new PlatformException(500,
                        "Modeling UI metadata element entries must be objects for " + key + ".");
            }
            Object type = raw.get("type");
            if (type == null || String.valueOf(type).isBlank()) {
                throw new PlatformException(500,
                        "Modeling UI metadata element is missing type for " + key + ".");
            }
            uiByType.put(String.valueOf(type), stringKeyMap(raw));
        }

        List<Map<String, Object>> elements = new ArrayList<>();
        for (Map<String, Object> structural : structuralElements) {
            String type = String.valueOf(structural.get("type"));
            Map<String, Object> ui = uiByType.get(type);
            Map<String, Object> merged = new LinkedHashMap<>(structural);
            if (ui != null) {
                merged.putAll(ui);
                requireElementVisualMetadata(key, type, merged);
            } else {
                merged.put("creatable", false);
                merged.put("uiMetadataMissing", true);
            }
            elements.add(merged);
        }
        return elements;
    }

    private void requireElementVisualMetadata(String key, String type, Map<String, Object> item) {
        for (String field : List.of("label", "icon", "color", "category")) {
            Object value = item.get(field);
            if (value == null || String.valueOf(value).isBlank()) {
                throw new PlatformException(500, "Modeling UI metadata for "
                        + key.toUpperCase() + " type " + type + " is missing " + field + ".");
            }
        }
    }

    private Map<String, Object> stringKeyMap(Map<?, ?> raw) {
        Map<String, Object> result = new LinkedHashMap<>();
        raw.forEach((key, value) -> result.put(String.valueOf(key), value));
        return result;
    }

    private List<?> requireList(Map<String, Object> metadata, String field, String key) {
        Object value = metadata.get(field);
        if (value instanceof List<?> list) {
            return list;
        }
        throw new PlatformException(500, "Modeling UI metadata for "
                + key.toUpperCase() + " must define list field '" + field + "'.");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> requireMap(Map<String, Object> metadata, String field,
            String key) {
        Object value = metadata.get(field);
        if (value instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        throw new PlatformException(500, "Modeling UI metadata for "
                + key.toUpperCase() + " must define object field '" + field + "'.");
    }

    private List<String> relationshipKinds(Map<String, Object> metadata) {
        Object configured = metadata.get("relationshipKinds");
        if (configured instanceof List<?> list && !list.isEmpty()) {
            return list.stream().map(String::valueOf).distinct().toList();
        }
        throw new PlatformException(500, "Modeling UI metadata must define relationshipKinds.");
    }

    private Map<String, Object> transformation() {
        return Map.of("enabled", true, "elementMappings", List.of(), "relationshipMappings",
                List.of());
    }

    private Map<String, Object> readMetadata(String key) {
        String resource = "modeling/" + key + "-ui-metadata.json";
        try (InputStream input = Thread.currentThread().getContextClassLoader()
                .getResourceAsStream(resource)) {
            if (input == null) {
                throw new PlatformException(500,
                        "Missing modeling UI metadata resource: " + resource);
            }
            Map<String, Object> metadata = objectMapper.readValue(input, new TypeReference<>() {
            });
            Object elements = metadata.get("elements");
            if (!(elements instanceof List<?> list) || list.isEmpty()) {
                throw new PlatformException(500,
                        "Modeling UI metadata contains no elements: " + resource);
            }
            return metadata;
        } catch (PlatformException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new PlatformException(500, "Could not load modeling UI metadata: " + resource);
        }
    }

    private CimMetamodel readEcoreMetamodel(String key) {
        Path ecoreFile = ecoreFile(key);
        try (InputStream input = Files.newInputStream(ecoreFile)) {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setNamespaceAware(false);
            Document document = factory.newDocumentBuilder().parse(input);
            NodeList packages = document.getElementsByTagName("ecore:EPackage");
            Map<String, String> typeByPath = typeByPath(packages);
            Map<String, List<String>> enumLiteralsByType = enumLiteralsByType(packages);
            Map<String, EcoreClass> classByType = ecoreClasses(packages, typeByPath);
            List<Map<String, Object>> elements = new ArrayList<>();
            List<Map<String, Object>> relationshipRules = new ArrayList<>();
            List<Map<String, Object>> semanticReferenceRules = new ArrayList<>();
            for (EcoreClass modelClass : classByType.values()) {
                elements.add(metamodelElement(modelClass, classByType, enumLiteralsByType));
                collectReferenceRules(key, modelClass, relationshipRules, semanticReferenceRules);
            }
            relationshipRules.sort(
                    Comparator.comparing(rule -> String.valueOf(rule.get("sourceType"))
                            + String.valueOf(rule.get("targetType")) + String.valueOf(
                            rule.get("feature"))));
            return new CimMetamodel(elements, relationshipRules, semanticReferenceRules);
        } catch (PlatformException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new PlatformException(500,
                    "Could not read " + key.toUpperCase() + " Ecore metamodel: "
                            + ecoreFile);
        }
    }

    private Path ecoreFile(String key) {
        ModelLevel level = switch (key) {
            case "cim" -> ModelLevel.CIM;
            case "pim" -> ModelLevel.PIM;
            case "psm" -> ModelLevel.PSM;
            default -> throw new PlatformException(500, "Unknown modeling level: " + key);
        };
        return new MdeRuntimePaths(MdeRuntimeOptions.defaults()).metamodelFile(level);
    }

    private Map<String, String> typeByPath(NodeList packages) {
        Map<String, String> result = new HashMap<>();
        for (int packageIndex = 0; packageIndex < packages.getLength(); packageIndex++) {
            Element ePackage = (Element) packages.item(packageIndex);
            int classifierIndex = 0;
            NodeList children = ePackage.getChildNodes();
            for (int childIndex = 0; childIndex < children.getLength(); childIndex++) {
                Node node = children.item(childIndex);
                if (node instanceof Element classifier && "eClassifiers".equals(
                        classifier.getTagName())) {
                    result.put("#/" + packageIndex + "/" + classifier.getAttribute("name"),
                            classifier.getAttribute("name"));
                    classifierIndex++;
                }
            }
        }
        return result;
    }

    private Map<String, EcoreClass> ecoreClasses(NodeList packages,
            Map<String, String> typeByPath) {
        Map<String, EcoreClass> result = new LinkedHashMap<>();
        for (int packageIndex = 0; packageIndex < packages.getLength(); packageIndex++) {
            Element ePackage = (Element) packages.item(packageIndex);
            String packageName = ePackage.getAttribute("name");
            NodeList children = ePackage.getChildNodes();
            for (int classIndex = 0; classIndex < children.getLength(); classIndex++) {
                Node node = children.item(classIndex);
                if (!(node instanceof Element classifier)
                        || !"eClassifiers".equals(classifier.getTagName())
                        || !"ecore:EClass".equals(classifier.getAttribute("xsi:type"))) {
                    continue;
                }
                List<String> superTypes = new ArrayList<>();
                for (String rawSuperType : classifier.getAttribute("eSuperTypes").split("\\s+")) {
                    String superType = typeName(rawSuperType, typeByPath);
                    if (!superType.isBlank()) {
                        superTypes.add(superType);
                    }
                }
                List<EmfaticFeature> attributes = new ArrayList<>();
                List<EmfaticFeature> references = new ArrayList<>();
                NodeList featureNodes = classifier.getChildNodes();
                for (int featureIndex = 0; featureIndex < featureNodes.getLength();
                        featureIndex++) {
                    Node featureNode = featureNodes.item(featureIndex);
                    if (!(featureNode instanceof Element feature)
                            || !"eStructuralFeatures".equals(feature.getTagName())) {
                        continue;
                    }
                    String xsiType = feature.getAttribute("xsi:type");
                    if ("ecore:EAttribute".equals(xsiType)) {
                        attributes.add(ecoreFeature(feature, typeByPath, "attribute"));
                    } else if ("ecore:EReference".equals(xsiType)) {
                        references.add(ecoreFeature(feature, typeByPath, "reference"));
                    }
                }
                String type = classifier.getAttribute("name");
                result.put(type, new EcoreClass(packageName, type, superTypes,
                        "true".equals(classifier.getAttribute("abstract")), attributes,
                        references));
            }
        }
        return result;
    }

    private EmfaticFeature ecoreFeature(Element feature, Map<String, String> typeByPath,
            String kind) {
        int lower = lowerBound(feature);
        int upper = upperBound(feature);
        String multiplicity = multiplicity(lower, upper);
        boolean containment = "true".equals(feature.getAttribute("containment"));
        boolean readonly = "false".equals(feature.getAttribute("changeable"))
                || "true".equals(feature.getAttribute("transient"));
        return new EmfaticFeature(feature.getAttribute("name"), kind,
                typeName(feature.getAttribute("eType"), typeByPath), containment, multiplicity,
                feature.getAttribute("eOpposite"), readonly);
    }

    private String multiplicity(int lower, int upper) {
        if (upper == -1 || upper > 1) {
            return lower > 0 ? "+" : "*";
        }
        return lower > 0 ? "1" : "";
    }

    private Map<String, Object> metamodelElement(EcoreClass modelClass,
            Map<String, EcoreClass> classByType,
            Map<String, List<String>> enumLiteralsByType) {
        String type = modelClass.name();
        List<Map<String, Object>> attributes = new ArrayList<>();
        List<Map<String, Object>> references = new ArrayList<>();
        inheritedEcoreFeatures(modelClass, classByType, true).forEach(feature ->
                attributes.add(cimAttribute(feature, enumLiteralsByType)));
        inheritedEcoreFeatures(modelClass, classByType, false).forEach(feature ->
                references.add(cimReference(feature)));
        boolean abstractType = modelClass.abstractType();
        Map<String, Object> element = new LinkedHashMap<>();
        element.put("type", type);
        element.put("package", modelClass.packageName());
        element.put("visibleFields", defaultVisibleFields(type, attributes, references));
        element.put("attributes", attributes);
        element.put("references", references);
        element.put("supertypes", allEcoreSuperTypes(modelClass, classByType));
        element.put("abstract", abstractType);
        element.put("relationshipElement", false);
        element.put("containedOnly", false);
        element.put("supportOnly", false);
        element.put("creatable", !abstractType);
        return element;
    }

    private List<String> defaultVisibleFields(String type, List<Map<String, Object>> attributes,
            List<Map<String, Object>> references) {
        LinkedHashSet<String> fields = new LinkedHashSet<>();
        for (String baseline : List.of("name", "id")) {
            if (attributes.stream().anyMatch(attribute -> baseline.equals(attribute.get("name")))) {
                fields.add(baseline);
            }
        }
        for (Map<String, Object> attribute : attributes) {
            if (fields.size() >= 5) {
                break;
            }
            String name = String.valueOf(attribute.getOrDefault("name", ""));
            if (!name.isBlank()) {
                fields.add(name);
            }
        }
        for (Map<String, Object> reference : references) {
            if (fields.size() >= 5) {
                break;
            }
            String name = String.valueOf(reference.getOrDefault("name", ""));
            if (!name.isBlank() && !Boolean.TRUE.equals(reference.get("containment"))) {
                fields.add(name);
            }
        }
        if (fields.isEmpty()) {
            fields.add(type.endsWith("Model") ? "name" : "id");
        }
        return new ArrayList<>(fields);
    }

    private List<EmfaticFeature> inheritedEcoreFeatures(EcoreClass modelClass,
            Map<String, EcoreClass> classByType, boolean attributes) {
        LinkedHashMap<String, EmfaticFeature> result = new LinkedHashMap<>();
        for (String superType : modelClass.superTypes()) {
            EcoreClass parent = classByType.get(superType);
            if (parent == null) {
                continue;
            }
            for (EmfaticFeature feature : inheritedEcoreFeatures(parent, classByType,
                    attributes)) {
                result.put(feature.name(), feature);
            }
        }
        List<EmfaticFeature> local = attributes ? modelClass.attributes()
                : modelClass.references();
        for (EmfaticFeature feature : local) {
            result.put(feature.name(), feature);
        }
        return new ArrayList<>(result.values());
    }

    private List<String> allEcoreSuperTypes(EcoreClass modelClass,
            Map<String, EcoreClass> classByType) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        collectEcoreSuperTypes(modelClass, classByType, result);
        return new ArrayList<>(result);
    }

    private void collectEcoreSuperTypes(EcoreClass modelClass, Map<String, EcoreClass> classByType,
            LinkedHashSet<String> result) {
        for (String superType : modelClass.superTypes()) {
            if (!result.add(superType)) {
                continue;
            }
            EcoreClass parent = classByType.get(superType);
            if (parent != null) {
                collectEcoreSuperTypes(parent, classByType, result);
            }
        }
    }

    private Map<String, List<String>> enumLiteralsByType(NodeList packages) {
        Map<String, List<String>> result = new HashMap<>();
        for (int packageIndex = 0; packageIndex < packages.getLength(); packageIndex++) {
            Element ePackage = (Element) packages.item(packageIndex);
            NodeList children = ePackage.getChildNodes();
            for (int childIndex = 0; childIndex < children.getLength(); childIndex++) {
                Node node = children.item(childIndex);
                if (!(node instanceof Element classifier) || !"eClassifiers".equals(
                        classifier.getTagName())
                        || !"ecore:EEnum".equals(classifier.getAttribute("xsi:type"))) {
                    continue;
                }
                List<String> literals = new ArrayList<>();
                NodeList enumChildren = classifier.getChildNodes();
                for (int literalIndex = 0; literalIndex < enumChildren.getLength();
                        literalIndex++) {
                    Node literalNode = enumChildren.item(literalIndex);
                    if (literalNode instanceof Element literal && "eLiterals".equals(
                            literal.getTagName())) {
                        literals.add(literal.getAttribute("name"));
                    }
                }
                result.put(classifier.getAttribute("name"), literals);
            }
        }
        return result;
    }

    private Map<String, Object> cimAttribute(EmfaticFeature feature,
            Map<String, List<String>> enumLiteralsByType) {
        Map<String, Object> attribute = new LinkedHashMap<>();
        attribute.put("name", feature.name());
        attribute.put("kind", "attribute");
        attribute.put("type", feature.type());
        attribute.put("required", required(feature.multiplicity()));
        attribute.put("many", many(feature.multiplicity()));
        attribute.put("fieldType", fieldType(feature.type(), enumLiteralsByType));
        attribute.put("defaultValue", defaultValue(feature.type(), feature.multiplicity()));
        if (enumLiteralsByType.containsKey(feature.type())) {
            attribute.put("options", enumLiteralsByType.get(feature.type()));
        }
        return attribute;
    }

    private Map<String, Object> cimReference(EmfaticFeature feature) {
        Map<String, Object> reference = new LinkedHashMap<>();
        reference.put("name", feature.name());
        reference.put("kind", "reference");
        reference.put("targetType", feature.type());
        reference.put("required", required(feature.multiplicity()));
        reference.put("many", many(feature.multiplicity()));
        reference.put("containment", feature.containment());
        reference.put("opposite", feature.opposite());
        reference.put("readonly", feature.readonly());
        reference.put("defaultValue", many(feature.multiplicity()) ? List.of() : null);
        return reference;
    }

    private Map<String, Object> cimAttribute(Element feature, Map<String, String> typeByPath,
            Map<String, List<String>> enumLiteralsByType) {
        String type = typeName(feature.getAttribute("eType"), typeByPath);
        Map<String, Object> attribute = new LinkedHashMap<>();
        attribute.put("name", feature.getAttribute("name"));
        attribute.put("kind", "attribute");
        attribute.put("type", type);
        attribute.put("required", lowerBound(feature) > 0);
        attribute.put("many", upperBound(feature) == -1 || upperBound(feature) > 1);
        attribute.put("fieldType", fieldType(type, enumLiteralsByType));
        attribute.put("defaultValue", defaultValue(type, upperBound(feature)));
        if (enumLiteralsByType.containsKey(type)) {
            attribute.put("options", enumLiteralsByType.get(type));
        }
        return attribute;
    }

    private Map<String, Object> cimReference(Element feature, Map<String, String> typeByPath) {
        Map<String, Object> reference = new LinkedHashMap<>();
        reference.put("name", feature.getAttribute("name"));
        reference.put("kind", "reference");
        reference.put("targetType", typeName(feature.getAttribute("eType"), typeByPath));
        reference.put("required", lowerBound(feature) > 0);
        reference.put("many", upperBound(feature) == -1 || upperBound(feature) > 1);
        reference.put("containment", "true".equals(feature.getAttribute("containment")));
        reference.put("opposite", feature.getAttribute("eOpposite"));
        reference.put("readonly", "false".equals(feature.getAttribute("changeable")) ||
                "true".equals(feature.getAttribute("transient")));
        reference.put("defaultValue", upperBound(feature) == -1 || upperBound(feature) > 1
                ? List.of() : null);
        return reference;
    }

    private void collectReferenceRules(Element classifier, Map<String, String> typeByPath,
            List<Map<String, Object>> relationshipRules,
            List<Map<String, Object>> semanticReferenceRules) {
        String sourceType = classifier.getAttribute("name");
        NodeList children = classifier.getChildNodes();
        for (int index = 0; index < children.getLength(); index++) {
            Node node = children.item(index);
            if (!(node instanceof Element feature) || !"eStructuralFeatures".equals(
                    feature.getTagName())
                    || !"ecore:EReference".equals(feature.getAttribute("xsi:type"))) {
                continue;
            }
            String featureName = feature.getAttribute("name");
            boolean containment = "true".equals(feature.getAttribute("containment"));
            String targetType = typeName(feature.getAttribute("eType"), typeByPath);
            if (targetType.isBlank()) {
                continue;
            }
            String kind = relationshipKind(featureName, containment);
            Map<String, Object> rule = Map.of("sourceType", sourceType, "targetType",
                    genericTarget(targetType), "allowedKinds", List.of(kind), "feature",
                    featureName, "containment", containment);
            relationshipRules.add(rule);
            if (!containment) {
                semanticReferenceRules.add(Map.of("sourceType", sourceType, "targetType",
                        genericTarget(targetType), "feature", featureName, "kind", kind,
                        "reverse", false));
            }
        }
    }

    private void collectReferenceRules(EmfaticClass modelClass,
            List<Map<String, Object>> relationshipRules,
            List<Map<String, Object>> semanticReferenceRules) {
        String sourceType = modelClass.name();
        for (EmfaticFeature feature : modelClass.references()) {
            if (feature.type().isBlank() || feature.readonly()) {
                continue;
            }
            String kind = relationshipKind(feature.name(), feature.containment());
            relationshipRules.add(Map.of("sourceType", sourceType, "targetType",
                    genericTarget(feature.type()), "allowedKinds", List.of(kind), "feature",
                    feature.name(), "containment", feature.containment()));
            if (!feature.containment()) {
                semanticReferenceRules.add(Map.of("sourceType", sourceType, "targetType",
                        genericTarget(feature.type()), "feature", feature.name(), "kind", kind,
                        "reverse", false));
            }
        }
    }

    private void collectReferenceRules(String levelKey, EcoreClass modelClass,
            List<Map<String, Object>> relationshipRules,
            List<Map<String, Object>> semanticReferenceRules) {
        String sourceType = modelClass.name();
        for (EmfaticFeature feature : modelClass.references()) {
            if (feature.type().isBlank() || feature.readonly()) {
                continue;
            }
            String kind = relationshipKind(feature.name(), feature.containment());
            relationshipRules.add(Map.of("sourceType", sourceType, "targetType",
                    genericTarget(feature.type()), "allowedKinds", List.of(kind), "feature",
                    feature.name(), "containment", feature.containment()));
            if (!feature.containment()) {
                semanticReferenceRules.add(Map.of("sourceType", sourceType, "targetType",
                        genericTarget(feature.type()), "feature", feature.name(), "kind", kind,
                        "reverse", false));
            }
        }
    }

    private String typeName(String eType, Map<String, String> typeByPath) {
        if (eType == null || eType.isBlank()) {
            return "";
        }
        if (eType.startsWith("#/")) {
            return typeByPath.getOrDefault(eType, eType.substring(eType.lastIndexOf('/') + 1));
        }
        int marker = eType.indexOf("#//");
        if (marker >= 0) {
            return eType.substring(marker + 3);
        }
        return eType;
    }

    private List<String> directSuperTypes(Element classifier, Map<String, String> typeByPath) {
        List<String> result = new ArrayList<>();
        for (String rawSuperType : classifier.getAttribute("eSuperTypes").split("\\s+")) {
            String superType = typeName(rawSuperType, typeByPath);
            if (!superType.isBlank()) {
                result.add(superType);
            }
        }
        return result;
    }

    private String genericTarget(String targetType) {
        return switch (targetType) {
            case "ModelElement", "TraceableElement", "SemanticRelationship" -> "*";
            default -> targetType;
        };
    }

    private String relationshipKind(String featureName, boolean containment) {
        if (containment) {
            return "CONTAINS";
        }
        return featureNameToKind(featureName);
    }

    private int lowerBound(Element feature) {
        String value = feature.getAttribute("lowerBound");
        return value == null || value.isBlank() ? 0 : Integer.parseInt(value);
    }

    private int upperBound(Element feature) {
        String value = feature.getAttribute("upperBound");
        return value == null || value.isBlank() ? 1 : Integer.parseInt(value);
    }

    private boolean required(String multiplicity) {
        return "1".equals(multiplicity) || "+".equals(multiplicity);
    }

    private boolean many(String multiplicity) {
        return "*".equals(multiplicity) || "+".equals(multiplicity);
    }

    private String fieldType(String type, Map<String, List<String>> enumLiteralsByType) {
        if (type.contains("Boolean")) {
            return "boolean";
        }
        if (type.contains("Integer") || type.contains("Int") || type.contains("Double")
                || type.contains("Float")) {
            return "number";
        }
        if ("EDate".equals(type)) {
            return "date";
        }
        if (enumLiteralsByType.containsKey(type)) {
            return "select";
        }
        return "text";
    }

    private Object defaultValue(String type, int upperBound) {
        if (upperBound == -1 || upperBound > 1) {
            return List.of();
        }
        if (type.contains("Boolean")) {
            return false;
        }
        if (type.contains("Integer") || type.contains("Int") || type.contains("Double")
                || type.contains("Float")) {
            return 0;
        }
        return "";
    }

    private Object defaultValue(String type, String multiplicity) {
        if (many(multiplicity)) {
            return List.of();
        }
        if (type.contains("Boolean")) {
            return false;
        }
        if (type.contains("Integer") || type.contains("Int") || type.contains("Double")
                || type.contains("Float")) {
            return 0;
        }
        return "";
    }

    private String featureNameToKind(String featureName) {
        return featureName.replaceAll("([a-z])([A-Z])", "$1_$2").toUpperCase();
    }

    private String humanize(String type) {
        return type.replaceAll("([a-z])([A-Z])", "$1 $2");
    }

    private record CimMetamodel(List<Map<String, Object>> elements,
                                List<Map<String, Object>> relationshipRules,
                                List<Map<String, Object>> semanticReferenceRules) {

    }

    private record EcoreClass(String packageName, String name, List<String> superTypes,
                              boolean abstractType, List<EmfaticFeature> attributes,
                              List<EmfaticFeature> references) {

    }

    private record EmfaticClass(String packageName, String name, String superType,
                                boolean abstractType, List<EmfaticFeature> attributes,
                                List<EmfaticFeature> references) {

    }

    private record EmfaticFeature(String name, String kind, String type, boolean containment,
                                  String multiplicity, String opposite, boolean readonly) {

    }
}
