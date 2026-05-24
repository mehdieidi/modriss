package io.mehdieidi.modless.platform.core.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.mehdieidi.modless.platform.core.PlatformException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public final class ModelingConfigService {

    private static final List<String> KINDS = List.of("CONTAINS", "DEPENDS_ON", "TRIGGERS",
            "INVOKES", "READS", "WRITES", "USES", "OWNS", "SUPPORTS", "CONSTRAINS",
            "ROUTES_TO", "DEPLOYS", "HAS_ENV", "AUTHORIZED_BY", "TARGETS");
    private static final Map<String, String> CIM_REFERENCE_KINDS = Map.ofEntries(
            Map.entry("supportsGoals", "SUPPORTS"), Map.entry("supports", "SUPPORTS"),
            Map.entry("refinedBy", "REFINES"), Map.entry("dependsOn", "DEPENDS_ON"),
            Map.entry("conflictsWith", "CONFLICTS_WITH"), Map.entry("constrains", "CONSTRAINS"),
            Map.entry("realizesRequirements", "REALIZES"), Map.entry("containsCommands",
                    "CONTAINS_COMMAND"), Map.entry("containsQueries", "CONTAINS_QUERY"),
            Map.entry("containsEvents", "CONTAINS_EVENT"), Map.entry("managesEntities", "MANAGES"),
            Map.entry("ownsProcesses", "OWNS"), Map.entry("owner", "OWNS"),
            Map.entry("issuesCommands", "ISSUES"), Map.entry("issuesQueries", "ISSUES"),
            Map.entry("observesEvents", "OBSERVES"), Map.entry("playsRoles", "PLAYS_ROLE"),
            Map.entry("assignedTo", "ASSIGNED_TO"),
            Map.entry("producedEvents", "PRODUCES"), Map.entry("consumedEvents", "CONSUMED_BY"),
            Map.entry("exchangedInformation", "EXCHANGES_INFORMATION"),
            Map.entry("expectedEvents", "EXPECTS"), Map.entry("rejectionEvents", "REJECTS_WITH"),
            Map.entry("possibleErrors", "MAY_FAIL_WITH"), Map.entry("targetAggregate", "TARGETS"),
            Map.entry("targetCapability", "HANDLED_BY"), Map.entry("reads", "READS"),
            Map.entry("triggeredBy", "TRIGGERS"), Map.entry("consumedByPolicies", "TRIGGERS"),
            Map.entry("consumedByProcesses", "FEEDS"), Map.entry("emitsCommands", "EMITS_COMMAND"),
            Map.entry("emitsEvents", "EMITS_EVENT"), Map.entry("guards", "GUARDS"),
            Map.entry("constrainsQueries", "CONSTRAINS"), Map.entry("resultingCommands",
                    "RESULTS_IN"), Map.entry("resultingEvents", "RESULTS_IN"),
            Map.entry("decisionTable", "USES"), Map.entry("dataItems", "CONSTRAINS"),
            Map.entry("constrainedElements", "CONSTRAINS"), Map.entry("constrainedActors",
                    "CONSTRAINS"), Map.entry("constrainedCommands", "CONSTRAINS"),
            Map.entry("constrainedQueries", "CONSTRAINS"), Map.entry("constrainedInformation",
                    "CONSTRAINS"), Map.entry("scopedElements", "CONSTRAINS"),
            Map.entry("affectedElements", "ATTACHED_TO"), Map.entry("attachedTo", "ATTACHED_TO"),
            Map.entry("root", "ROOT"), Map.entry("members", "MEMBER"),
            Map.entry("source", "TRANSITION"), Map.entry("target", "TRANSITION"));
    private static final Map<String, String> CIM_CATEGORY_BY_PACKAGE = Map.of(
            "cimbehavior", "Behavior",
            "cimdomain", "Domain and Information",
            "cimgov", "Governance",
            "cimorg", "Organization and Capability",
            "cimprocess", "Process and Decision",
            "cimtransformation", "Transformation",
            "cimdiagram", "Diagram Layout",
            "kernel", "Traceability");
    private static final Map<String, Map<String, Object>> CIM_VISUALS = Map.ofEntries(
            visual("BusinessGoal", "Intent and Measurement", "target", "#D97706", "goal",
                    List.of("successCriterion", "businessValue", "measuredBy")),
            visual("CIMWorkspace", "Workspace", "folder", "#334155", "workspace",
                    List.of("semanticModel", "diagrams")),
            visual("CIMModel", "Workspace", "database", "#334155", "model",
                    List.of("domainName", "businessScope", "organizationName")),
            visual("DiagramModel", "Diagram Layout", "layout-dashboard", "#64748B",
                    "diagram model", List.of("views")),
            visual("DiagramView", "Diagram Layout", "panel-top", "#64748B", "diagram view",
                    List.of("viewType", "viewpoint", "primary")),
            visual("DiagramNode", "Diagram Layout", "square", "#64748B", "diagram node",
                    List.of("semanticElement", "presentation", "collapsed")),
            visual("DiagramEdge", "Diagram Layout", "move-right", "#64748B", "diagram edge",
                    List.of("semanticSource", "semanticTarget", "edgeKind")),
            visual("TraceModel", "Traceability", "git-merge", "#4F46E5", "trace model",
                    List.of("links")),
            visual("TraceLink", "Traceability", "link", "#4F46E5", "trace",
                    List.of("linkType", "source", "target")),
            visual("TransformationAssumption", "Transformation", "circle-help", "#7C3AED",
                    "assumption", List.of("assumptionStatement", "accepted")),
            visual("ProductionReadinessAssessment", "Transformation", "clipboard-check",
                    "#0F766E", "readiness", List.of("readinessStatus", "transformationReady",
                            "productionReady")),
            visual("ReadinessCheck", "Transformation", "list-checks", "#0F766E", "check",
                    List.of("checkId", "severity", "passed")),
            visual("StructuredDocument", "Traceability", "file-text", "#64748B", "document",
                    List.of("format", "externalUri")),
            visual("Annotation", "Traceability", "sticky-note", "#64748B", "annotation",
                    List.of("key", "value")),
            visual("KeyValue", "Traceability", "list", "#64748B", "key value",
                    List.of("key", "value")),
            visual("DeployableElement", "Transformation", "package-check", "#64748B",
                    "deployable", List.of("deploymentReadiness", "deploymentNotes")),
            visual("CredentialRequirementLike", "Governance", "key-round", "#DC2626",
                    "credential", List.of("credentialType", "secretRequired")),
            visual("EnvironmentTarget", "Transformation", "landmark", "#64748B",
                    "environment target", List.of("environmentName")),
            visual("FunctionTarget", "Behavior", "braces", "#2563EB", "function target",
                    List.of("functionName")),
            visual("InvocationSource", "Behavior", "log-in", "#2563EB", "invocation source",
                    List.of("sourceName")),
            visual("InvocationTarget", "Behavior", "log-out", "#2563EB",
                    "invocation target", List.of("targetName")),
            visual("DataAccessTarget", "Domain and Information", "database", "#16A34A",
                    "data target", List.of("dataName")),
            visual("FlowEndpoint", "Process and Decision", "circle-dot", "#0891B2",
                    "flow endpoint", List.of("endpointName")),
            visual("RouteEndpoint", "Process and Decision", "route", "#0891B2",
                    "route endpoint", List.of("routeName")),
            visual("RoutingTarget", "Process and Decision", "route", "#0891B2",
                    "routing target", List.of("targetName")),
            visual("SubscriptionTarget", "Behavior", "rss", "#D97706", "subscription target",
                    List.of("subscriptionName")),
            visual("EventCarrier", "Behavior", "send", "#D97706", "event carrier",
                    List.of("eventName")),
            visual("ExternalCallTarget", "Organization and Capability", "server", "#64748B",
                    "external call", List.of("systemName")),
            visual("PolicyTarget", "Governance", "gavel", "#7C3AED", "policy target",
                    List.of("policyName")),
            visual("ProtectedResource", "Governance", "shield", "#DC2626",
                    "protected resource", List.of("resourceName")),
            visual("WorkflowTarget", "Process and Decision", "workflow", "#0891B2",
                    "workflow target", List.of("workflowName")),
            visual("Requirement", "Intent and Measurement", "file-text", "#2563EB",
                    "requirement", List.of("requirementType", "priority", "fitCriterion")),
            visual("AcceptanceCriterion", "Intent and Measurement", "check-square", "#2563EB",
                    "acceptance", List.of("givenContext", "whenAction", "thenOutcome")),
            visual("NonFunctionalRequirement", "Governance", "activity", "#65A30D", "quality",
                    List.of("qualityType", "metric", "target")),
            visual("QualityScenario", "Governance", "activity", "#65A30D",
                    "quality scenario", List.of("stimulus", "response", "measure")),
            visual("SecurityConstraint", "Governance", "lock", "#DC2626", "security",
                    List.of("authorizationRule", "auditRequired", "constrainedCommands")),
            visual("PrivacyConstraint", "Governance", "shield", "#E11D48", "privacy",
                    List.of("purpose", "legalBasis", "dataItems")),
            visual("ComplianceConstraint", "Governance", "scale", "#CA8A04", "compliance",
                    List.of("regulation", "controlId", "scopedElements")),
            visual("KPI", "Intent and Measurement", "gauge", "#D97706", "kpi",
                    List.of("metricName", "operator", "targetValue", "unit")),
            visual("Stakeholder", "Intent and Measurement", "user-round", "#DB2777",
                    "stakeholder", List.of("stakeholderType", "influenceLevel", "concerns")),
            visual("Actor", "Organization and Capability", "user", "#DB2777", "participant",
                    List.of("actorType", "trustLevel", "issuesCommands", "issuesQueries")),
            visual("Role", "Organization and Capability", "badge", "#BE185D", "role",
                    List.of("responsibilities", "permissions")),
            visual("ExternalSystem", "Organization and Capability", "server", "#64748B",
                    "external", List.of("owningOrganization", "trustLevel", "producedEvents")),
            visual("BusinessCapability", "Organization and Capability", "blocks", "#0F766E",
                    "capability", List.of("criticality", "supports", "owner")),
            visual("BoundedContextCandidate", "Organization and Capability", "box", "#4F46E5",
                    "context", List.of("languageBoundary", "ownershipBoundary", "capabilities")),
            visual("UbiquitousLanguageTerm", "Organization and Capability", "book-open",
                    "#4F46E5", "term", List.of("term", "definition", "aliases")),
            visual("CapabilityDependency", "Organization and Capability", "git-branch",
                    "#0F766E", "dependency", List.of("dependencyType", "reason", "criticality")),
            visual("DomainConcept", "Domain and Information", "component", "#0891B2",
                    "concept", List.of("businessName", "owningCapability")),
            visual("DomainEntity", "Domain and Information", "component", "#0891B2", "entity",
                    List.of("identityAttribute", "attributes", "lifecycleStates")),
            visual("ValueObject", "Domain and Information", "diamond", "#65A30D", "value object",
                    List.of("immutable", "equalityAttributes", "attributes")),
            visual("DomainRelationship", "Domain and Information", "join", "#2563EB",
                    "relationship", List.of("relationshipType", "sourceMultiplicity",
                            "targetMultiplicity")),
            visual("AggregateCandidate", "Domain and Information", "package", "#EA580C",
                    "aggregate", List.of("root", "members", "strongConsistencyRequired")),
            visual("InformationItem", "Domain and Information", "file-text", "#16A34A", "data",
                    List.of("businessName", "type", "classification", "containsPersonalData")),
            visual("DataClassification", "Governance", "shield-check", "#16A34A",
                    "classification", List.of("classificationLevel", "personalDataKind")),
            visual("LifecycleStateDefinition", "Domain and Information", "circle-dot",
                    "#0891B2", "state", List.of("stateKind", "terminal")),
            visual("BusinessInvariant", "Domain and Information", "check-check", "#7C3AED",
                    "condition", List.of("naturalLanguage", "severity")),
            visual("Command", "Behavior", "zap", "#2563EB", "command",
                    List.of("intent", "input", "expectedEvents", "authorizationRequired")),
            visual("Query", "Behavior", "search", "#16A34A", "query",
                    List.of("input", "output", "reads", "containsPersonalData")),
            visual("BusinessEvent", "Behavior", "sparkles", "#D97706", "event",
                    List.of("occurredInPastTenseName", "payload", "affects")),
            visual("BusinessError", "Behavior", "octagon-alert", "#DC2626", "error",
                    List.of("errorCode", "userVisibleMessage", "emittedEvents")),
            visual("Condition", "Behavior", "diamond", "#7C3AED", "condition",
                    List.of("naturalLanguage", "expression")),
            visual("BusinessProcess", "Process and Decision", "workflow", "#0891B2", "process",
                    List.of("processKind", "trigger", "preconditions", "postconditions")),
            visual("StartStep", "Process and Decision", "circle-play", "#64748B", "start",
                    List.of("stepKind")),
            visual("EndStep", "Process and Decision", "circle-stop", "#64748B", "end",
                    List.of("stepKind")),
            visual("CommandStep", "Process and Decision", "zap", "#2563EB", "command step",
                    List.of("command", "responsibleRoles")),
            visual("QueryStep", "Process and Decision", "search", "#16A34A", "query step",
                    List.of("query", "responsibleRoles")),
            visual("EventStep", "Process and Decision", "sparkles", "#D97706", "event step",
                    List.of("event", "responsibleRoles")),
            visual("PolicyStep", "Process and Decision", "gavel", "#7C3AED", "policy step",
                    List.of("policy", "responsibleRoles")),
            visual("HumanTaskStep", "Process and Decision", "user-check", "#DB2777",
                    "human task", List.of("taskDescription", "responsibleRoles")),
            visual("ExternalInteractionStep", "Process and Decision", "server", "#64748B",
                    "external task", List.of("externalSystem", "interactionPurpose")),
            visual("DecisionStep", "Process and Decision", "diamond", "#CA8A04", "decision",
                    List.of("condition", "decisionTable")),
            visual("WaitStep", "Process and Decision", "clock", "#CA8A04", "wait",
                    List.of("durationExpression", "waitReason")),
            visual("ProcessTransition", "Process and Decision", "arrow-right", "#0F766E",
                    "flow", List.of("label", "conditionExpression", "orderIndex")),
            visual("Policy", "Process and Decision", "gavel", "#7C3AED", "policy",
                    List.of("policyType", "naturalLanguageRule", "triggeredBy", "guards")),
            visual("DecisionTable", "Process and Decision", "table", "#CA8A04",
                    "decision table", List.of("inputs", "outputs", "rules")),
            visual("DecisionRule", "Process and Decision", "list-checks", "#CA8A04",
                    "decision rule", List.of("conditionExpression", "outcome",
                            "resultingCommands")),
            visual("ExceptionScenario", "Process and Decision", "triangle-alert", "#DC2626",
                    "error", List.of("errors", "resultingEvents")),
            visual("TemporalConstraint", "Process and Decision", "clock", "#CA8A04", "temporal",
                    List.of("timeExpression", "constrainedElements")),
            visual("Risk", "Transformation", "triangle-alert", "#DC2626", "risk",
                    List.of("probability", "impact", "affectedElements")),
            visual("Assumption", "Transformation", "circle-help", "#7C3AED", "assumption",
                    List.of("assumptionStatement", "affectedElements")),
            visual("Hotspot", "Transformation", "flame", "#EA580C", "hotspot",
                    List.of("severity", "blocksTransformation", "owner")),
            visual("TransformationProfile", "Transformation", "settings", "#64748B",
                    "transformation profile", List.of("targetStyle", "cloudProvider",
                            "requiredDecisions")),
            visual("ManualDecision", "Transformation", "clipboard-check", "#7C3AED",
                    "manual decision", List.of("question", "decisionOwner", "blocking")),
            visual("ReadinessFinding", "Transformation", "triangle-alert", "#DC2626", "finding",
                    List.of("severity", "message", "blocking")));
    private static final String PLACEHOLDER_ICON = "/assets/icons/placeholder.svg";
    private static final Set<String> ABSTRACT_CIM_TYPES = Set.of("ModelElement",
            "TraceableElement", "SemanticRelationship", "TransformationAssumption",
            "DomainConcept", "ProcessStep");
    private static final Set<String> RELATIONSHIP_ONLY_CIM_TYPES = Set.of("DomainRelationship",
            "CapabilityDependency", "ProcessTransition", "TraceLink");
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static Map.Entry<String, Map<String, Object>> visual(String type, String category,
            String icon, String color, String notation, List<String> visibleFields) {
        return Map.entry(type, Map.of("category", category, "icon", icon, "color", color,
                "notation", notation, "visibleFields", visibleFields));
    }

    public Map<String, Object> config() {
        return Map.of(
                "version", 1,
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
        if ("cim".equals(key)) {
            metadata = augmentCimMetadata(metadata);
        }
        List<String> relationshipKinds = relationshipKinds(metadata);
        return Map.ofEntries(
                Map.entry("displayName", metadata.getOrDefault("displayName", key.toUpperCase())),
                Map.entry("elementsPath", "/diagram/elements"),
                Map.entry("relationshipsPath", "/diagram/relationships"),
                Map.entry("labelField", "name"),
                Map.entry("relationshipKinds", relationshipKinds),
                Map.entry("elements", metadata.getOrDefault("elements", List.of())),
                Map.entry("relationshipRules",
                        metadata.getOrDefault("relationshipRules",
                                List.of(Map.of("sourceType", "*", "targetType", "*",
                                        "allowedKinds", relationshipKinds)))),
                Map.entry("relationshipKindLabels",
                        metadata.getOrDefault("relationshipKindLabels", Map.of())),
                Map.entry("semanticReferenceRules",
                        metadata.getOrDefault("semanticReferenceRules", List.of())),
                Map.entry("viewDefinitions", metadata.getOrDefault("viewDefinitions", List.of())),
                Map.entry("strictnessModes", List.of("exploration", "methodology", "production")),
                Map.entry("constraints", metadata.getOrDefault("constraints", List.of())),
                Map.entry("rootTemplate", metadata.getOrDefault("rootTemplate",
                        defaultRootTemplate())));
    }

    private Map<String, Object> defaultRootTemplate() {
        return Map.of("name", "", "diagram", Map.of("elements", List.of(), "relationships",
                        List.of()), "graph",
                Map.of("elements", List.of(), "relationships", List.of(), "traceLinks", List.of(),
                        "assumptions", List.of(), "validationIssues", List.of(), "manualBacklog",
                        List.of()),
                "views", List.of(), "fragments", List.of());
    }

    private List<String> relationshipKinds(Map<String, Object> metadata) {
        Object configured = metadata.get("relationshipKinds");
        if (configured instanceof List<?> list && !list.isEmpty()) {
            return list.stream().map(String::valueOf).distinct().toList();
        }
        List<String> kinds = new ArrayList<>(KINDS);
        Object rules = metadata.get("relationshipRules");
        if (rules instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof Map<?, ?> rule && rule.get(
                        "allowedKinds") instanceof List<?> allowed) {
                    allowed.stream().map(String::valueOf).forEach(kind -> {
                        if (!kinds.contains(kind)) {
                            kinds.add(kind);
                        }
                    });
                }
            }
        }
        return kinds;
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

    private Map<String, Object> augmentCimMetadata(Map<String, Object> base) {
        Map<String, Object> metadata = new LinkedHashMap<>(base);
        CimMetamodel metamodel = readCimMetamodel();
        metadata.put("elements", metamodel.elements());
        metadata.put("relationshipRules", metamodel.relationshipRules());
        metadata.put("semanticReferenceRules", metamodel.semanticReferenceRules());
        metadata.put("relationshipKindLabels", cimRelationshipKindLabels());
        metadata.put("viewDefinitions", cimViewDefinitions());
        metadata.put("constraints", cimConstraints());
        metadata.put("rootTemplate", cimRootTemplate());
        return metadata;
    }

    private CimMetamodel readCimMetamodel() {
        String resource = "modeling/metamodels/cim/cim-combined.ecore";
        try (InputStream input = Thread.currentThread().getContextClassLoader()
                .getResourceAsStream(resource)) {
            if (input == null) {
                return readCimEmfaticMetamodel();
            }
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setNamespaceAware(false);
            Document document = factory.newDocumentBuilder().parse(input);
            NodeList packages = document.getElementsByTagName("ecore:EPackage");
            Map<String, String> typeByPath = typeByPath(packages);
            Map<String, List<String>> enumLiteralsByType = enumLiteralsByType(packages);
            List<Map<String, Object>> elements = new ArrayList<>();
            List<Map<String, Object>> relationshipRules = new ArrayList<>();
            List<Map<String, Object>> semanticReferenceRules = new ArrayList<>();
            for (int packageIndex = 0; packageIndex < packages.getLength(); packageIndex++) {
                Element ePackage = (Element) packages.item(packageIndex);
                String packageName = ePackage.getAttribute("name");
                NodeList children = ePackage.getChildNodes();
                for (int classIndex = 0; classIndex < children.getLength(); classIndex++) {
                    Node node = children.item(classIndex);
                    if (!(node instanceof Element classifier)
                            || !"eClassifiers".equals(classifier.getTagName())) {
                        continue;
                    }
                    String xsiType = classifier.getAttribute("xsi:type");
                    if ("ecore:EClass".equals(xsiType)) {
                        elements.add(cimElement(packageName, classifier, typeByPath,
                                enumLiteralsByType));
                        collectReferenceRules(classifier, typeByPath, relationshipRules,
                                semanticReferenceRules);
                    }
                }
            }
            relationshipRules.sort(
                    Comparator.comparing(rule -> String.valueOf(rule.get("sourceType"))
                            + String.valueOf(rule.get("targetType")) + String.valueOf(
                            rule.get("feature"))));
            return new CimMetamodel(elements, relationshipRules, semanticReferenceRules);
        } catch (PlatformException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new PlatformException(500, "Could not read CIM metamodel metadata.");
        }
    }

    private CimMetamodel readCimEmfaticMetamodel() {
        try {
            List<Path> paths = cimEmfaticPaths();
            Map<String, List<String>> enumLiteralsByType = new HashMap<>();
            List<EmfaticClass> classes = new ArrayList<>();
            for (Path path : paths) {
                parseEmfatic(path, enumLiteralsByType, classes);
            }

            Map<String, EmfaticClass> classByType = new LinkedHashMap<>();
            for (EmfaticClass modelClass : classes) {
                classByType.put(modelClass.name(), modelClass);
            }

            List<Map<String, Object>> elements = new ArrayList<>();
            List<Map<String, Object>> relationshipRules = new ArrayList<>();
            List<Map<String, Object>> semanticReferenceRules = new ArrayList<>();
            for (EmfaticClass modelClass : classes) {
                elements.add(cimElement(modelClass, classByType, enumLiteralsByType));
                collectReferenceRules(modelClass, relationshipRules, semanticReferenceRules);
            }
            relationshipRules.sort(
                    Comparator.comparing(rule -> String.valueOf(rule.get("sourceType"))
                            + String.valueOf(rule.get("targetType")) + String.valueOf(
                            rule.get("feature"))));
            return new CimMetamodel(elements, relationshipRules, semanticReferenceRules);
        } catch (Exception ex) {
            throw new PlatformException(500, "Could not read CIM Emfatic metadata.");
        }
    }

    private List<Path> cimEmfaticPaths() throws Exception {
        List<Path> candidates = List.of(Path.of("mde", "metamodels"),
                Path.of("..", "mde", "metamodels"), Path.of("..", "..", "mde", "metamodels"));
        for (Path base : candidates) {
            Path shared = base.resolve(Path.of("shared", "kernel.emf")).normalize();
            Path cim = base.resolve("cim").normalize();
            if (Files.isRegularFile(shared) && Files.isDirectory(cim)) {
                List<Path> paths = new ArrayList<>();
                paths.add(shared);
                try (var stream = Files.list(cim)) {
                    stream.filter(path -> path.getFileName().toString().endsWith(".emf"))
                            .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                            .forEach(paths::add);
                }
                return paths;
            }
        }
        throw new PlatformException(500, "Missing CIM Emfatic metamodel files.");
    }

    private void parseEmfatic(Path path, Map<String, List<String>> enumLiteralsByType,
            List<EmfaticClass> classes) throws Exception {
        String packageName = "";
        EmfaticClass currentClass = null;
        String currentEnum = null;
        List<String> enumLiterals = new ArrayList<>();
        for (String rawLine : Files.readAllLines(path, StandardCharsets.UTF_8)) {
            String line = rawLine.replaceAll("//.*$", "").trim();
            if (line.isBlank() || line.startsWith("@") || line.startsWith("import ")
                    || line.startsWith("/*") || line.startsWith("*")) {
                continue;
            }
            if (line.startsWith("package ")) {
                packageName = line.replace("package", "").replace(";", "").trim();
                continue;
            }
            Matcher enumStart = Pattern.compile("^enum\\s+(\\w+)\\s*\\{").matcher(line);
            if (enumStart.find()) {
                currentEnum = enumStart.group(1);
                enumLiterals = new ArrayList<>();
                continue;
            }
            if (currentEnum != null) {
                if (line.startsWith("}")) {
                    enumLiteralsByType.put(currentEnum, List.copyOf(enumLiterals));
                    currentEnum = null;
                } else {
                    String literal = line.replace(";", "").trim();
                    if (!literal.isBlank()) {
                        enumLiterals.add(literal);
                    }
                }
                continue;
            }
            Matcher classStart = Pattern.compile(
                            "^(abstract\\s+)?class\\s+(\\w+)(?:\\s+extends\\s+([\\w.]+))?.*\\{")
                    .matcher(line);
            if (classStart.find()) {
                currentClass = new EmfaticClass(packageName, classStart.group(2),
                        simpleType(classStart.group(3)), classStart.group(1) != null,
                        new ArrayList<>(), new ArrayList<>());
                classes.add(currentClass);
                continue;
            }
            if (currentClass != null && line.startsWith("}")) {
                currentClass = null;
                continue;
            }
            if (currentClass != null && line.endsWith(";")) {
                EmfaticFeature feature = parseEmfaticFeature(line);
                if (feature == null) {
                    continue;
                }
                if ("attribute".equals(feature.kind())) {
                    currentClass.attributes().add(feature);
                } else {
                    currentClass.references().add(feature);
                }
            }
        }
    }

    private EmfaticFeature parseEmfaticFeature(String rawLine) {
        String line = rawLine.replace(";", "").replaceAll("\\s+", " ").trim();
        String[] tokens = line.split(" ");
        int marker = -1;
        String kind = "";
        for (int index = 0; index < tokens.length; index++) {
            if ("attr".equals(tokens[index])) {
                marker = index;
                kind = "attribute";
                break;
            }
            if ("ref".equals(tokens[index]) || "val".equals(tokens[index])) {
                marker = index;
                kind = "reference";
                break;
            }
        }
        if (marker < 0 || marker + 1 >= tokens.length) {
            return null;
        }
        String typeToken = tokens[marker + 1];
        String featureName =
                marker > 0 && !Set.of("readonly", "transient", "id").contains(tokens[marker - 1])
                        ? tokens[marker - 1] : tokens[Math.min(marker + 2, tokens.length - 1)];
        if (marker > 0 && "id".equals(tokens[marker - 1])) {
            featureName = "id";
        }
        featureName = featureName.replaceFirst("^~", "");
        boolean containment = "val".equals(tokens[marker]);
        String multiplicity = "";
        Matcher multiplicityMatcher = Pattern.compile("\\[([^]]+)]").matcher(typeToken);
        if (multiplicityMatcher.find()) {
            multiplicity = multiplicityMatcher.group(1);
        }
        String type = simpleType(typeToken.replaceAll("\\[[^]]+]", "").replaceAll("#.*$", ""));
        String opposite = "";
        int hash = typeToken.indexOf('#');
        if (hash >= 0) {
            opposite = typeToken.substring(hash + 1).replaceAll("\\[[^]]+]", "");
        }
        return new EmfaticFeature(featureName, kind, type, containment, multiplicity, opposite,
                line.startsWith("readonly") || line.contains(" readonly "));
    }

    private String simpleType(String value) {
        if (value == null) {
            return "";
        }
        String text = value.trim();
        int dot = text.lastIndexOf('.');
        return dot >= 0 ? text.substring(dot + 1) : text;
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

    private Map<String, Object> cimElement(String packageName, Element classifier,
            Map<String, String> typeByPath, Map<String, List<String>> enumLiteralsByType) {
        String type = classifier.getAttribute("name");
        Map<String, Object> visual = CIM_VISUALS.getOrDefault(type, Map.of());
        List<Map<String, Object>> attributes = new ArrayList<>();
        List<Map<String, Object>> references = new ArrayList<>();
        NodeList children = classifier.getChildNodes();
        for (int index = 0; index < children.getLength(); index++) {
            Node node = children.item(index);
            if (!(node instanceof Element feature)
                    || !"eStructuralFeatures".equals(feature.getTagName())) {
                continue;
            }
            String xsiType = feature.getAttribute("xsi:type");
            if ("ecore:EAttribute".equals(xsiType)) {
                attributes.add(cimAttribute(feature, typeByPath, enumLiteralsByType));
            } else if ("ecore:EReference".equals(xsiType)) {
                references.add(cimReference(feature, typeByPath));
            }
        }
        boolean abstractType = "true".equals(classifier.getAttribute("abstract"));
        Map<String, Object> element = new LinkedHashMap<>();
        element.put("type", type);
        element.put("label", humanize(type));
        element.put("package", packageName);
        element.put("category", visual.getOrDefault("category",
                CIM_CATEGORY_BY_PACKAGE.getOrDefault(packageName, "CIM")));
        element.put("icon", PLACEHOLDER_ICON);
        element.put("color", visual.getOrDefault("color", "#64748B"));
        element.put("notation", Map.of("tag", visual.getOrDefault("notation", "element"),
                "lineFields", visual.getOrDefault("visibleFields", List.of())));
        element.put("visibleFields", visual.getOrDefault("visibleFields", List.of()));
        element.put("attributes", attributes);
        element.put("references", references);
        element.put("abstract", abstractType);
        element.put("relationshipElement",
                RELATIONSHIP_ONLY_CIM_TYPES.contains(type));
        element.put("creatable", !abstractType && !RELATIONSHIP_ONLY_CIM_TYPES.contains(type));
        return element;
    }

    private Map<String, Object> cimElement(EmfaticClass modelClass,
            Map<String, EmfaticClass> classByType,
            Map<String, List<String>> enumLiteralsByType) {
        String type = modelClass.name();
        Map<String, Object> visual = CIM_VISUALS.getOrDefault(type, Map.of());
        List<EmfaticFeature> inheritedAttributes = inheritedFeatures(modelClass, classByType, true);
        List<EmfaticFeature> inheritedReferences = inheritedFeatures(modelClass, classByType,
                false);
        List<Map<String, Object>> attributes = new ArrayList<>();
        List<Map<String, Object>> references = new ArrayList<>();
        inheritedAttributes.forEach(feature -> attributes.add(cimAttribute(feature,
                enumLiteralsByType)));
        inheritedReferences.forEach(feature -> references.add(cimReference(feature)));

        boolean abstractType = modelClass.abstractType() || ABSTRACT_CIM_TYPES.contains(type);
        Map<String, Object> element = new LinkedHashMap<>();
        element.put("type", type);
        element.put("label", humanize(type));
        element.put("package", modelClass.packageName());
        element.put("category", visual.getOrDefault("category",
                CIM_CATEGORY_BY_PACKAGE.getOrDefault(modelClass.packageName(), "CIM")));
        element.put("icon", PLACEHOLDER_ICON);
        element.put("color", visual.getOrDefault("color", "#64748B"));
        element.put("notation", Map.of("tag", visual.getOrDefault("notation", "element"),
                "lineFields", visual.getOrDefault("visibleFields", List.of())));
        element.put("visibleFields", visual.getOrDefault("visibleFields", List.of()));
        element.put("attributes", attributes);
        element.put("references", references);
        element.put("abstract", abstractType);
        element.put("relationshipElement", RELATIONSHIP_ONLY_CIM_TYPES.contains(type));
        element.put("creatable", !abstractType && !RELATIONSHIP_ONLY_CIM_TYPES.contains(type));
        return element;
    }

    private List<EmfaticFeature> inheritedFeatures(EmfaticClass modelClass,
            Map<String, EmfaticClass> classByType, boolean attributes) {
        LinkedHashMap<String, EmfaticFeature> result = new LinkedHashMap<>();
        if (!modelClass.superType().isBlank() && classByType.containsKey(modelClass.superType())) {
            for (EmfaticFeature feature : inheritedFeatures(classByType.get(modelClass.superType()),
                    classByType, attributes)) {
                result.put(feature.name(), feature);
            }
        }
        List<EmfaticFeature> local = attributes ? modelClass.attributes() : modelClass.references();
        for (EmfaticFeature feature : local) {
            result.put(feature.name(), feature);
        }
        return new ArrayList<>(result.values());
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
            String kind = relationshipKind(sourceType, featureName, containment);
            Map<String, Object> rule = Map.of("sourceType", sourceType, "targetType",
                    genericTarget(targetType), "allowedKinds", List.of(kind), "feature",
                    featureName, "containment", containment);
            relationshipRules.add(rule);
            if (!containment) {
                semanticReferenceRules.add(Map.of("sourceType", sourceType, "targetType",
                        genericTarget(targetType), "feature", featureName, "kind", kind,
                        "reverse", reverseReference(featureName)));
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
            String kind = relationshipKind(sourceType, feature.name(), feature.containment());
            relationshipRules.add(Map.of("sourceType", sourceType, "targetType",
                    genericTarget(feature.type()), "allowedKinds", List.of(kind), "feature",
                    feature.name(), "containment", feature.containment()));
            if (!feature.containment()) {
                semanticReferenceRules.add(Map.of("sourceType", sourceType, "targetType",
                        genericTarget(feature.type()), "feature", feature.name(), "kind", kind,
                        "reverse", reverseReference(feature.name())));
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

    private String genericTarget(String targetType) {
        return switch (targetType) {
            case "ModelElement", "TraceableElement", "SemanticRelationship" -> "*";
            default -> targetType;
        };
    }

    private String relationshipKind(String sourceType, String featureName, boolean containment) {
        if (containment) {
            return "CONTAINS";
        }
        if ("DomainRelationship".equals(sourceType)
                && Set.of("source", "target").contains(featureName)) {
            return "DOMAIN_RELATIONSHIP";
        }
        if ("CapabilityDependency".equals(sourceType)
                && Set.of("source", "target").contains(featureName)) {
            return "DEPENDS_ON";
        }
        if ("ProcessTransition".equals(sourceType)
                && Set.of("source", "target").contains(featureName)) {
            return "TRANSITION";
        }
        if ("TraceLink".equals(sourceType)
                && Set.of("source", "target").contains(featureName)) {
            return "TRACE";
        }
        return CIM_REFERENCE_KINDS.getOrDefault(featureName, featureNameToKind(featureName));
    }

    private boolean reverseReference(String featureName) {
        return Set.of("issuedBy", "triggeredBy", "causedByExternalSystems").contains(featureName);
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

    private Map<String, String> cimRelationshipKindLabels() {
        return Map.ofEntries(Map.entry("ISSUES", "issues"), Map.entry("OBSERVES", "observes"),
                Map.entry("PLAYS_ROLE", "plays role"), Map.entry("ASSIGNED_TO", "assigned to"),
                Map.entry("PRODUCES", "produces"),
                Map.entry("CONSUMED_BY", "consumed by"), Map.entry("SUPPORTS", "supports"),
                Map.entry("EXCHANGES_INFORMATION", "exchanges information"),
                Map.entry("REALIZES", "realizes"), Map.entry("CONTAINS", "contains"),
                Map.entry("CONTAINS_COMMAND", "contains command"), Map.entry("CONTAINS_QUERY",
                        "contains query"), Map.entry("CONTAINS_EVENT", "contains event"),
                Map.entry("MANAGES", "manages"), Map.entry("DEPENDS_ON", "depends on"),
                Map.entry("DOMAIN_RELATIONSHIP", "relationship"), Map.entry("ROOT", "root"),
                Map.entry("MEMBER", "member"), Map.entry("EXPECTS", "expects"),
                Map.entry("REJECTS_WITH", "rejects with"), Map.entry("MAY_FAIL_WITH",
                        "may fail with"), Map.entry("TARGETS", "targets"),
                Map.entry("HANDLED_BY", "handled by"), Map.entry("READS", "reads"),
                Map.entry("TRIGGERS", "triggers"), Map.entry("FEEDS", "feeds"),
                Map.entry("EMITS_COMMAND", "emits command"), Map.entry("EMITS_EVENT",
                        "emits event"), Map.entry("GUARDS", "guards"),
                Map.entry("CONSTRAINS", "constrains"), Map.entry("TRANSITION", "transition"),
                Map.entry("USES", "uses"), Map.entry("RESULTS_IN", "results in"),
                Map.entry("ATTACHED_TO", "attached to"), Map.entry("REFINES", "refines"),
                Map.entry("CONFLICTS_WITH", "conflicts with"));
    }

    private List<Map<String, Object>> cimViewDefinitions() {
        return List.of(
                view("dashboard", "Model Dashboard", "DASHBOARD", "dashboard",
                        List.of("CIMModel", "BusinessGoal", "Actor", "BusinessCapability",
                                "Requirement", "DomainEntity", "Command", "BusinessEvent",
                                "Policy", "Risk", "Hotspot", "ProductionReadinessAssessment",
                                "TransformationProfile", "TraceModel"), List.of(), List.of(),
                        "DASHBOARD"),
                view("strategy-goals", "Strategy Goals Requirements and KPIs",
                        "REQUIREMENTS_GOALS", "requirements",
                        List.of("Requirement", "NonFunctionalRequirement", "SecurityConstraint",
                                "PrivacyConstraint", "ComplianceConstraint", "AcceptanceCriterion",
                                "BusinessGoal", "KPI", "Stakeholder", "TraceLink"),
                        List.of("Requirement", "BusinessGoal", "KPI", "Stakeholder",
                                "NonFunctionalRequirement", "SecurityConstraint",
                                "PrivacyConstraint", "ComplianceConstraint", "AcceptanceCriterion",
                                "TraceLink"),
                        List.of("SUPPORTS", "CONSTRAINS", "DEPENDS_ON", "CONFLICTS_WITH",
                                "REFINES"), "DEFAULT_LAYERED"),
                view("capability-map", "Capability Map",
                        "CAPABILITY_CONTEXT", "capability",
                        List.of("BusinessCapability", "CapabilityDependency", "Actor",
                                "ExternalSystem", "Role", "BusinessGoal", "Requirement",
                                "BoundedContextCandidate", "NonFunctionalRequirement", "Command",
                                "Query", "BusinessEvent", "DomainEntity", "BusinessProcess"),
                        List.of("BusinessCapability", "BoundedContextCandidate", "Actor",
                                "ExternalSystem", "Role", "BusinessGoal", "Requirement",
                                "CapabilityDependency", "NonFunctionalRequirement"),
                        List.of("SUPPORTS", "OWNS", "REALIZES", "CONTAINS_COMMAND",
                                "CONTAINS_QUERY", "CONTAINS_EVENT", "MANAGES", "DEPENDS_ON"),
                        "CONTAINER"),
                view("actor-interactions", "Actor Role and External System Interactions",
                        "ACTOR_INTERACTION", "actor",
                        List.of("Actor", "ExternalSystem", "Role", "Command", "Query",
                                "BusinessEvent", "InformationItem"),
                        List.of("Actor", "ExternalSystem", "Role", "Command", "Query",
                                "BusinessEvent", "InformationItem"),
                        List.of("ISSUES", "OBSERVES", "PLAYS_ROLE", "ASSIGNED_TO", "PRODUCES",
                                "CONSUMED_BY", "EXCHANGES_INFORMATION"),
                        "DEFAULT_LAYERED"),
                view("bounded-context-language", "Bounded Context and Ubiquitous Language",
                        "BOUNDED_CONTEXT", "bounded-context",
                        List.of("BoundedContextCandidate", "BusinessCapability", "DomainEntity",
                                "Command", "Query", "BusinessEvent", "Policy",
                                "UbiquitousLanguageTerm"),
                        List.of("BoundedContextCandidate", "BusinessCapability", "DomainEntity",
                                "Command", "Query", "BusinessEvent", "Policy",
                                "UbiquitousLanguageTerm"),
                        List.of("CONTAINS", "MANAGES", "HANDLED_BY", "TRIGGERS"), "CONTAINER"),
                view("domain-model", "Domain Model View", "DOMAIN_MODEL", "domain",
                        List.of("DomainEntity", "ValueObject", "DomainRelationship",
                                "InformationItem", "AggregateCandidate",
                                "LifecycleStateDefinition", "BusinessInvariant",
                                "DataClassification", "UbiquitousLanguageTerm"),
                        List.of("DomainEntity", "ValueObject", "AggregateCandidate",
                                "InformationItem", "BusinessInvariant",
                                "LifecycleStateDefinition", "DomainRelationship",
                                "DataClassification", "UbiquitousLanguageTerm"),
                        List.of("DOMAIN_RELATIONSHIP", "ROOT", "MEMBER", "CONTAINS", "MANAGES"),
                        "CLASS_DIAGRAM"),
                view("aggregate-consistency", "Aggregate and Consistency View",
                        "AGGREGATE", "aggregate",
                        List.of("AggregateCandidate", "DomainEntity", "BusinessInvariant",
                                "Command", "BusinessEvent"),
                        List.of("AggregateCandidate", "DomainEntity", "BusinessInvariant",
                                "Command", "BusinessEvent"),
                        List.of("ROOT", "MEMBER", "TARGETS", "EXPECTS", "CONTAINS"),
                        "CONTAINER"),
                view("data-dictionary", "Data Dictionary and Classification",
                        "DATA_DICTIONARY", "data",
                        List.of("InformationItem", "DataClassification", "PrivacyConstraint",
                                "ComplianceConstraint", "SecurityConstraint",
                                "NonFunctionalRequirement", "Command", "Query", "BusinessEvent",
                                "ExternalSystem"),
                        List.of("InformationItem", "DataClassification", "PrivacyConstraint",
                                "ComplianceConstraint", "SecurityConstraint",
                                "NonFunctionalRequirement"),
                        List.of("CONSTRAINS", "READS", "PRODUCES", "CONSUMED_BY"),
                        "TABLE"),
                view("event-storming", "Behavior Event Storming View", "EVENT_STORMING",
                        "eventstorming",
                        List.of("Actor", "ExternalSystem", "Command", "Query", "BusinessEvent",
                                "BusinessError", "Condition", "Policy", "AggregateCandidate",
                                "InformationItem"),
                        List.of("Actor", "ExternalSystem", "Command", "Query", "BusinessEvent",
                                "BusinessError", "Condition", "Policy", "InformationItem",
                                "AggregateCandidate"),
                        List.of("ISSUES", "PRODUCES", "CONSUMED_BY", "EXPECTS",
                                "REJECTS_WITH", "MAY_FAIL_WITH", "TARGETS", "READS",
                                "TRIGGERS", "EMITS_COMMAND", "EMITS_EVENT", "GUARDS"),
                        "EVENT_STORMING"),
                view("business-process", "Business Process View", "BUSINESS_PROCESS",
                        "process",
                        List.of("BusinessProcess", "StartStep", "EndStep", "CommandStep",
                                "QueryStep", "EventStep", "PolicyStep", "HumanTaskStep",
                                "ExternalInteractionStep", "DecisionStep", "WaitStep",
                                "ProcessTransition", "ExceptionScenario", "TemporalConstraint",
                                "Role", "Condition"),
                        List.of("BusinessProcess", "StartStep", "EndStep", "CommandStep",
                                "QueryStep", "EventStep", "PolicyStep", "HumanTaskStep",
                                "ExternalInteractionStep", "DecisionStep", "WaitStep",
                                "ProcessTransition", "ExceptionScenario", "TemporalConstraint",
                                "Role"),
                        List.of("TRANSITION", "USES", "CONSTRAINS", "CONTAINS"), "PROCESS"),
                view("decision", "Policy and Decision View", "DECISION", "decision",
                        List.of("Policy", "DecisionTable", "DecisionRule", "Condition",
                                "Command", "Query", "BusinessEvent", "InformationItem",
                                "BusinessError"),
                        List.of("Policy", "DecisionTable", "DecisionRule", "Condition", "Command",
                                "Query", "BusinessEvent", "InformationItem", "BusinessError"),
                        List.of("TRIGGERS", "GUARDS", "CONSTRAINS", "EMITS_COMMAND",
                                "EMITS_EVENT", "USES", "RESULTS_IN"), "DECISION"),
                view("governance-matrix", "Governance Constraint Matrix",
                        "GOVERNANCE_MATRIX", "governance",
                        List.of("InformationItem", "DataClassification", "PrivacyConstraint",
                                "SecurityConstraint", "ComplianceConstraint",
                                "NonFunctionalRequirement", "QualityScenario", "Actor",
                                "Command", "Query", "ExternalSystem"),
                        List.of("InformationItem", "DataClassification", "PrivacyConstraint",
                                "SecurityConstraint", "ComplianceConstraint",
                                "NonFunctionalRequirement", "QualityScenario", "Actor", "Command",
                                "Query", "ExternalSystem"),
                        List.of("CONSTRAINS", "READS", "PRODUCES", "CONSUMED_BY"),
                        "GOVERNANCE"),
                view("transformation-readiness", "Transformation Readiness",
                        "TRANSFORMATION_READINESS", "readiness",
                        List.of("TransformationProfile", "Risk", "Assumption", "Hotspot",
                                "ManualDecision", "ProductionReadinessAssessment",
                                "ReadinessFinding", "TraceLink", "Requirement",
                                "BusinessCapability", "Command", "Query", "BusinessEvent",
                                "BusinessProcess", "AggregateCandidate"),
                        List.of("TransformationProfile", "Risk", "Assumption", "Hotspot",
                                "ManualDecision", "ProductionReadinessAssessment",
                                "ReadinessFinding", "TraceLink"),
                        List.of("ATTACHED_TO", "CONSTRAINS"), "KANBAN"),
                view("traceability", "Traceability Matrix", "CUSTOM", "traceability",
                        List.of("TraceModel", "TraceLink", "Requirement", "BusinessGoal",
                                "Command", "Query", "BusinessEvent", "DomainEntity", "Policy",
                                "Risk", "ReadinessFinding"),
                        List.of("TraceLink", "Requirement", "BusinessGoal", "Command", "Query",
                                "BusinessEvent", "DomainEntity", "Policy", "Risk",
                                "ReadinessFinding"),
                        List.of("TRACE", "SATISFIES", "CONSTRAINS", "ATTACHED_TO"), "MATRIX"));
    }

    private Map<String, Object> view(String id, String displayName, String type, String viewpoint,
            List<String> elementTypes, List<String> palette, List<String> relationshipKinds,
            String layoutProfile) {
        return Map.of("id", id, "displayName", displayName, "viewType", type, "viewpoint",
                viewpoint, "elementTypes", elementTypes, "palette", palette, "relationshipKinds",
                relationshipKinds, "layoutProfile", layoutProfile, "defaultDepth", 1,
                "edgeLayers", List.of("core", "data", "constraint", "trace", "readiness",
                        "containment", "derived"));
    }

    private List<Map<String, Object>> cimConstraints() {
        return List.of(
                Map.of("type", "BusinessGoal", "requiredAny", List.of("successCriterion"),
                        "message", "Business goals need a success criterion."),
                Map.of("type", "KPI", "requiredAny",
                        List.of("metricName", "operator", "targetValue", "unit"), "message",
                        "KPIs need measurement semantics."),
                Map.of("type", "BusinessCapability", "requiredAny", List.of("supports"),
                        "message", "Capabilities should support at least one business goal."),
                Map.of("type", "Command", "requiredAny", List.of("expectedEvents"), "message",
                        "Commands should produce at least one expected event."),
                Map.of("type", "Query", "requiredAny", List.of("output"), "message",
                        "Queries need at least one output."),
                Map.of("type", "PrivacyConstraint", "requiredAny",
                        List.of("purpose", "legalBasis", "dataItems"), "message",
                        "Privacy constraints need purpose, legal basis and data items."),
                Map.of("type", "Hotspot", "whenAny", List.of("blocksTransformation",
                                "productionBlocking"), "requiredAny", List.of("owner"), "message",
                        "Blocking hotspots need an owner."));
    }

    private Map<String, Object> cimRootTemplate() {
        Map<String, Object> root = new LinkedHashMap<>(defaultRootTemplate());
        root.put("modelLevel", "CIM");
        root.put("domainName", "");
        root.put("businessScope", "");
        root.put("organizationName", "");
        root.put("modelingDate", "");
        root.put("language", "en");
        return root;
    }

    private record CimMetamodel(List<Map<String, Object>> elements,
                                List<Map<String, Object>> relationshipRules,
                                List<Map<String, Object>> semanticReferenceRules) {

    }

    private record EmfaticClass(String packageName, String name, String superType,
                                boolean abstractType, List<EmfaticFeature> attributes,
                                List<EmfaticFeature> references) {

    }

    private record EmfaticFeature(String name, String kind, String type, boolean containment,
                                  String multiplicity, String opposite, boolean readonly) {

    }
}
