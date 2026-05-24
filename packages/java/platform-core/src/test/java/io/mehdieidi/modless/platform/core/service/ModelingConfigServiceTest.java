package io.mehdieidi.modless.platform.core.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ModelingConfigServiceTest {

    private final ModelingConfigService service = new ModelingConfigService();

    @Test
    void exposesConcretePimVisualSyntaxFromMetamodel() {
        Map<String, Object> pim = level("pim");
        List<Map<String, Object>> elements = listOfMaps(pim.get("elements"));

        assertTrue(elements.size() > 40);
        assertTrue(isCreatable(elements, "Function"));
        assertTrue(isCreatable(elements, "Api"));
        assertTrue(isCreatable(elements, "Queue"));
        assertTrue(isCreatable(elements, "Topic"));
        assertTrue(isCreatable(elements, "EventBus"));
        assertTrue(isCreatable(elements, "TraceModel"));
        assertTrue(isCreatable(elements, "ProductionReadinessAssessment"));
        assertFalse(isCreatable(elements, "EventChannel"));
        assertFalse(isCreatable(elements, "Flow"));
        assertFalse(isCreatable(elements, "RequestResponseFlow"));
        assertFalse(isCreatable(elements, "PIMModel"));
        assertFalse(isCreatable(elements, "KeyValue"));
        assertFalse(isCreatable(elements, "StructuredDocument"));

        Map<String, Object> function = element(elements, "Function");
        assertTrue(stringList(function.get("supertypes")).contains("FunctionTarget"));
        assertTrue(stringList(function.get("supertypes")).contains("DeployableElement"));

        Map<String, Object> rootTemplate = map(pim.get("rootTemplate"));
        assertEquals("PIMModel", rootTemplate.get("eClass"));
        assertEquals("EVENT_DRIVEN_SERVERLESS", rootTemplate.get("architectureStyle"));
        assertEquals(Boolean.TRUE, rootTemplate.get("providerIndependent"));
    }

    @Test
    void cimPaletteContainsRootCreatableElementsAndNoAbstractOrContainedRows() {
        Map<String, Object> cim = level("cim");
        List<Map<String, Object>> elements = listOfMaps(cim.get("elements"));
        Set<String> palette = creatableTypes(elements);

        Set<String> expectedRootCreatable = Set.of(
                "Requirement", "NonFunctionalRequirement", "SecurityConstraint",
                "PrivacyConstraint", "ComplianceConstraint", "BusinessGoal", "KPI",
                "Stakeholder", "Actor", "ExternalSystem", "Role", "BusinessCapability",
                "BoundedContextCandidate", "UbiquitousLanguageTerm", "DomainEntity",
                "ValueObject", "AggregateCandidate", "InformationItem", "DataClassification",
                "Command", "Query", "BusinessEvent", "BusinessError", "Condition",
                "BusinessProcess", "Policy", "DecisionTable", "Risk", "Assumption", "Hotspot",
                "TransformationProfile", "TraceModel", "ProductionReadinessAssessment");
        assertTrue(palette.containsAll(expectedRootCreatable));

        Set<String> notPaletteItems = Set.of(
                "ModelElement", "TraceableElement", "SemanticRelationship",
                "TransformationAssumption", "DomainConcept", "ProcessStep", "CIMModel",
                "CIMWorkspace", "DiagramModel", "DiagramView", "DiagramNode", "DiagramEdge",
                "DomainRelationship", "CapabilityDependency", "ProcessTransition", "TraceLink",
                "AcceptanceCriterion", "QualityScenario", "LifecycleStateDefinition",
                "BusinessInvariant", "StartStep", "EndStep", "CommandStep", "QueryStep",
                "EventStep", "PolicyStep", "HumanTaskStep", "ExternalInteractionStep",
                "DecisionStep", "WaitStep", "DecisionRule", "ExceptionScenario",
                "TemporalConstraint", "Annotation", "ReadinessFinding", "ReadinessCheck",
                "ManualDecision", "KeyValue", "StructuredDocument");
        notPaletteItems.forEach(type -> assertFalse(palette.contains(type),
                type + " should be edited through a root register, connector, or legal parent"));

        assertTrue(Boolean.TRUE.equals(element(elements, "AcceptanceCriterion").get(
                "containedOnly")));
        assertTrue(Boolean.TRUE.equals(element(elements, "DecisionRule").get("containedOnly")));
        assertTrue(Boolean.TRUE.equals(element(elements, "TraceLink").get(
                "relationshipElement")));
    }

    @Test
    void exposesCimViewsAndRootTemplateForWorkbench() {
        Map<String, Object> cim = level("cim");

        List<Map<String, Object>> views = listOfMaps(cim.get("viewDefinitions"));
        assertTrue(views.stream().anyMatch(view -> "dashboard".equals(view.get("id"))));
        assertTrue(views.stream().anyMatch(view -> "domain-model".equals(view.get("id"))));
        assertTrue(views.stream().anyMatch(view -> "business-process".equals(view.get("id"))));
        assertTrue(views.stream().anyMatch(view -> "traceability".equals(view.get("id"))));

        Map<String, Object> rootTemplate = map(cim.get("rootTemplate"));
        assertEquals("CIM", rootTemplate.get("modelLevel"));
        assertEquals("en", rootTemplate.get("language"));

        List<Map<String, Object>> semanticRules = listOfMaps(cim.get(
                "semanticReferenceRules"));
        assertTrue(semanticRules.stream().anyMatch(rule ->
                "BusinessCapability".equals(rule.get("sourceType"))
                        && "BusinessGoal".equals(rule.get("targetType"))
                        && "supports".equals(rule.get("feature"))
                        && "SUPPORTS".equals(rule.get("kind"))));
        assertTrue(semanticRules.stream().anyMatch(rule ->
                "TraceLink".equals(rule.get("sourceType"))
                        && "*".equals(rule.get("targetType"))
                        && "target".equals(rule.get("feature"))));
    }

    @Test
    void pimPaletteContainsEveryTopLevelConcreteElementAndNoSupportRows() {
        Map<String, Object> pim = level("pim");
        List<Map<String, Object>> elements = listOfMaps(pim.get("elements"));
        Set<String> palette = creatableTypes(elements);

        Set<String> expectedTopLevel = Set.of(
                "ServerlessService", "DeploymentUnit", "Environment",
                "ImplementationProfile", "Function", "Api", "Schema", "EventType",
                "DataStore", "ObjectStore", "Queue", "Topic", "EventBus", "Schedule",
                "Workflow", "ExternalAdapter", "ConfigurationSet", "Secret",
                "IdentityProvider", "Principal", "DataProtectionPolicy", "CompliancePolicy",
                "ResiliencePolicy", "TimeoutPolicy", "IdempotencyPolicy", "ConcurrencyPolicy",
                "RateLimitPolicy", "BatchPolicy", "OrderingPolicy", "CachePolicy",
                "BackupPolicy", "RetentionPolicy", "CostPolicy", "ObservabilityConfig",
                "CorsPolicy", "SecurityPolicy", "AuthPolicy", "AuthorizationPolicy",
                "TraceModel", "ProductionReadinessAssessment");
        assertTrue(palette.containsAll(expectedTopLevel));

        Set<String> relationshipAndNested = Set.of("Trigger", "DataAccess",
                "RequestResponseFlow", "EventFlow", "MessageFlow", "PubSubFlow",
                "OrchestrationFlow", "ExternalIntegrationFlow", "WorkflowTransition",
                "Permission", "Subscription", "EventRoutingRule", "TraceLink",
                "FunctionContract", "ApiRoute", "ErrorMapping", "SchemaField",
                "SchemaEnumLiteral", "SchemaConstraint", "ApiContract", "EventEnvelope",
                "DataModel", "DataField", "AccessPattern", "IndexCandidate",
                "WorkflowState", "ErrorHandler", "CompensationPolicy", "ConfigParameter",
                "EnvironmentVariable", "CredentialRequirement", "RetryPolicy",
                "DeadLetterPolicy", "LoggingPolicy", "MetricPolicy", "MetricDimension",
                "TracingPolicy", "AlertPolicy", "Slo", "Annotation", "ReadinessFinding",
                "ReadinessCheck", "ManualDecision", "PIMModel", "KeyValue",
                "StructuredDocument");
        relationshipAndNested.forEach(type -> assertFalse(palette.contains(type),
                type + " should be edited as a connector, nested row, or support object"));

        expectedTopLevel.forEach(type -> assertTrue(isCreatable(elements, type),
                type + " should be creatable from the PIM palette"));
    }

    @Test
    void exposesEveryPimMetamodelElementWithVisualMetadata() {
        List<Map<String, Object>> elements = listOfMaps(level("pim").get("elements"));
        Set<String> actualTypes = new LinkedHashSet<>();
        elements.forEach(element -> {
            actualTypes.add(String.valueOf(element.get("type")));
            assertNotBlank(element.get("category"), element.get("type") + " category");
            assertNotBlank(element.get("color"), element.get("type") + " color");
            assertNotNull(element.get("notation"), element.get("type") + " notation");
            assertNotNull(element.get("attributes"), element.get("type") + " attributes");
            assertNotNull(element.get("references"), element.get("type") + " references");
        });

        Set<String> expectedTypes = Set.of(
                "PIMModel", "Annotation", "KeyValue", "ModelElement",
                "TraceableElement", "SemanticRelationship", "TraceModel", "TraceLink",
                "TransformationAssumption", "StructuredDocument",
                "ProductionReadinessAssessment", "ReadinessFinding", "ReadinessCheck",
                "ManualDecision", "Api", "ApiRoute", "ErrorMapping", "ComputeElement",
                "Function", "Trigger", "ConfigurationSet", "ConfigParameter",
                "EnvironmentVariable", "Secret", "CredentialRequirement", "Schema",
                "SchemaField", "SchemaEnumLiteral", "SchemaConstraint", "FunctionContract",
                "ApiContract", "EventEnvelope", "EventType", "StorageElement",
                "DataStore", "ObjectStore", "DataModel", "DataField", "AccessPattern",
                "IndexCandidate", "DataAccess", "ServerlessService", "DeploymentUnit",
                "Environment", "ImplementationProfile", "ExternalAdapter",
                "IntegrationElement", "EventChannel", "Queue", "Topic", "EventBus",
                "Schedule", "Subscription", "EventRoutingRule", "Flow",
                "RequestResponseFlow", "EventFlow", "MessageFlow", "PubSubFlow",
                "OrchestrationFlow", "ExternalIntegrationFlow", "ArchitecturePolicy",
                "DataProtectionPolicy", "CompliancePolicy", "ResiliencePolicy",
                "RetryPolicy", "DeadLetterPolicy", "TimeoutPolicy", "IdempotencyPolicy",
                "ConcurrencyPolicy", "RateLimitPolicy", "BatchPolicy", "OrderingPolicy",
                "CachePolicy", "BackupPolicy", "RetentionPolicy", "CostPolicy",
                "ObservabilityConfig", "LoggingPolicy", "MetricPolicy", "MetricDimension",
                "TracingPolicy", "AlertPolicy", "Slo", "CorsPolicy", "IdentityProvider",
                "Principal", "Permission", "SecurityPolicy", "AuthPolicy",
                "AuthorizationPolicy", "Workflow", "WorkflowState", "WorkflowTransition",
                "ErrorHandler", "CompensationPolicy", "DeployableElement",
                "InvocationSource", "InvocationTarget", "FunctionTarget",
                "WorkflowTarget", "SubscriptionTarget", "RoutingTarget", "FlowEndpoint",
                "ProtectedResource", "PolicyTarget", "DataAccessTarget",
                "ExternalCallTarget", "EnvironmentTarget", "CredentialRequirementLike",
                "RouteEndpoint", "EventCarrier");
        assertEquals(expectedTypes, actualTypes);
    }

    @Test
    void exposesPimViewsAndSemanticReferenceRules() {
        Map<String, Object> pim = level("pim");

        List<Map<String, Object>> views = listOfMaps(pim.get("viewDefinitions"));
        assertTrue(views.stream().anyMatch(view -> "pim-api-surface".equals(view.get("id"))));
        assertTrue(views.stream().anyMatch(view -> "pim-workflow-designer".equals(view.get("id"))));
        assertTrue(views.stream().anyMatch(view -> "pim-readiness-traceability".equals(
                view.get("id"))));

        List<Map<String, Object>> relationshipRules = listOfMaps(pim.get("relationshipRules"));
        assertTrue(relationshipRules.stream().anyMatch(rule ->
                "DeploymentUnit".equals(rule.get("sourceType"))
                        && "DeployableElement".equals(rule.get("targetType"))
                        && stringList(rule.get("allowedKinds")).contains("DEPLOYS")));
        assertTrue(relationshipRules.stream().anyMatch(rule ->
                "Principal".equals(rule.get("sourceType"))
                        && "Permission".equals(rule.get("targetType"))
                        && stringList(rule.get("allowedKinds")).contains("PERMISSION")));

        List<Map<String, Object>> semanticRules = listOfMaps(pim.get("semanticReferenceRules"));
        assertTrue(semanticRules.stream().anyMatch(rule ->
                "Function".equals(rule.get("sourceType"))
                        && "StorageElement".equals(rule.get("targetType"))
                        && "reads".equals(rule.get("feature"))
                        && "READS".equals(rule.get("kind"))));
        assertTrue(semanticRules.stream().anyMatch(rule ->
                "ArchitecturePolicy".equals(rule.get("sourceType"))
                        && "PolicyTarget".equals(rule.get("targetType"))
                        && "attachedTo".equals(rule.get("feature"))));

        Map<String, String> labels = stringMap(pim.get("relationshipKindLabels"));
        assertEquals("read/write", labels.get("READ_WRITE"));
        assertEquals("dead letter", labels.get("DEAD_LETTER"));
    }

    @Test
    void everyCreatablePimPaletteTypeIsAssignedToAConcreteViewPalette() {
        Map<String, Object> pim = level("pim");
        Set<String> palette = creatableTypes(listOfMaps(pim.get("elements")));
        Set<String> viewPalette = new LinkedHashSet<>();
        listOfMaps(pim.get("viewDefinitions")).forEach(view ->
                viewPalette.addAll(stringList(view.get("palette"))));

        palette.forEach(type -> assertTrue(viewPalette.contains(type),
                type + " is creatable but not available from any PIM view palette"));
    }

    private Map<String, Object> level(String key) {
        return map(map(service.config().get("levels")).get(key));
    }

    private boolean isCreatable(List<Map<String, Object>> elements, String type) {
        return Boolean.TRUE.equals(element(elements, type).get("creatable"));
    }

    private Set<String> creatableTypes(List<Map<String, Object>> elements) {
        Set<String> result = new LinkedHashSet<>();
        elements.stream().filter(element -> Boolean.TRUE.equals(element.get("creatable")))
                .map(element -> String.valueOf(element.get("type"))).forEach(result::add);
        return result;
    }

    private Map<String, Object> element(List<Map<String, Object>> elements, String type) {
        return elements.stream().filter(item -> type.equals(item.get("type"))).findFirst()
                .orElseThrow();
    }

    private void assertNotBlank(Object value, String label) {
        assertNotNull(value, label);
        assertFalse(String.valueOf(value).isBlank(), label);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> map(Object value) {
        assertNotNull(value);
        return (Map<String, Object>) value;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> listOfMaps(Object value) {
        assertNotNull(value);
        return (List<Map<String, Object>>) value;
    }

    @SuppressWarnings("unchecked")
    private List<String> stringList(Object value) {
        assertNotNull(value);
        return (List<String>) value;
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> stringMap(Object value) {
        assertNotNull(value);
        return (Map<String, String>) value;
    }
}
