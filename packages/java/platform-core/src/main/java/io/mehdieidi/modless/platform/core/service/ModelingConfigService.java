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
import java.util.LinkedHashSet;
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
            "ROUTES_TO", "DEPLOYS", "DEPLOYS_TO", "HAS_ENV", "AUTHORIZED_BY", "TARGETS",
            "PUBLISHES", "SUBSCRIBES_TO", "CALLS", "USES_SECRET", "FLOW", "REQUEST_RESPONSE",
            "EVENT_FLOW", "MESSAGE_FLOW", "PUB_SUB", "ORCHESTRATES", "EXTERNAL_CALL",
            "DATA_ACCESS", "PERMISSION", "TRANSITION", "TRACE");
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
    private static final Map<String, String> PIM_REFERENCE_KINDS = Map.ofEntries(
            Map.entry("ownsFunctions", "OWNS"), Map.entry("ownsApis", "OWNS"),
            Map.entry("ownsChannels", "OWNS"), Map.entry("ownsStores", "OWNS"),
            Map.entry("ownsWorkflows", "OWNS"), Map.entry("ownsAdapters", "OWNS"),
            Map.entry("constrainedBy", "CONSTRAINS"), Map.entry("services", "OWNS"),
            Map.entry("contains", "DEPLOYS"), Map.entry("targetEnvironments", "DEPLOYS_TO"),
            Map.entry("routes", "CONTAINS"), Map.entry("functionIntegration", "ROUTES_TO"),
            Map.entry("workflowIntegration", "ROUTES_TO"), Map.entry("auth", "AUTHORIZED_BY"),
            Map.entry("authorization", "AUTHORIZED_BY"), Map.entry("requestSchema", "USES"),
            Map.entry("responseSchema", "USES"), Map.entry("errorSchema", "USES"),
            Map.entry("inputSchema", "USES"), Map.entry("outputSchema", "USES"),
            Map.entry("errorSchemas", "USES"), Map.entry("schema", "USES"),
            Map.entry("emittedEvents", "PUBLISHES"), Map.entry("producedBy", "PUBLISHES"),
            Map.entry("consumedBy", "SUBSCRIBES_TO"), Map.entry("triggers", "TRIGGERS"),
            Map.entry("source", "INVOKES"), Map.entry("invokesFunction", "INVOKES"),
            Map.entry("startsWorkflow", "INVOKES"), Map.entry("reads", "READS"),
            Map.entry("writes", "WRITES"), Map.entry("publishes", "PUBLISHES"),
            Map.entry("subscribesTo", "SUBSCRIBES_TO"), Map.entry("callsAdapters", "CALLS"),
            Map.entry("usesSecrets", "USES_SECRET"), Map.entry("environmentVariables",
                    "HAS_ENV"), Map.entry("eventTypes", "CONTAINS"), Map.entry("producers",
                    "PUBLISHES"), Map.entry("consumers", "SUBSCRIBES_TO"),
            Map.entry("workflowConsumers", "SUBSCRIBES_TO"), Map.entry("externalProducers",
                    "PUBLISHES"), Map.entry("externalConsumers", "SUBSCRIBES_TO"),
            Map.entry("deadLetterChannel", "DEAD_LETTER"), Map.entry("subscriptions",
                    "SUBSCRIBES_TO"), Map.entry("target", "FLOW"), Map.entry("targets",
                    "ROUTES_TO"), Map.entry("apiRoute", "ROUTES_TO"), Map.entry("eventType",
                    "EVENT_FLOW"), Map.entry("channel", "EVENT_FLOW"), Map.entry("messageSchema",
                    "MESSAGE_FLOW"), Map.entry("queue", "MESSAGE_FLOW"), Map.entry("topic",
                    "PUB_SUB"), Map.entry("workflow", "ORCHESTRATES"), Map.entry("adapter",
                    "EXTERNAL_CALL"), Map.entry("store", "DATA_ACCESS"), Map.entry("function",
                    "DATA_ACCESS"), Map.entry("dataModels", "DATA_ACCESS"),
            Map.entry("accessPatterns", "DATA_ACCESS"), Map.entry("startState", "TRANSITION"),
            Map.entry("endStates", "TRANSITION"), Map.entry("invokesAdapter", "EXTERNAL_CALL"),
            Map.entry("nestedWorkflow", "ORCHESTRATES"), Map.entry("nextState", "TRANSITION"),
            Map.entry("handlerFunction", "INVOKES"), Map.entry("targetResource", "PERMISSION"),
            Map.entry("allowedPrincipals", "AUTHORIZED_BY"), Map.entry("permissions",
                    "PERMISSION"), Map.entry("identityProvider", "AUTHORIZED_BY"),
            Map.entry("principals", "AUTHORIZED_BY"), Map.entry("attachedTo", "ATTACHED_TO"),
            Map.entry("configurationSets", "HAS_ENV"), Map.entry("parameters", "HAS_ENV"),
            Map.entry("variables", "HAS_ENV"), Map.entry("environments", "DEPLOYS_TO"),
            Map.entry("appliesTo", "ATTACHED_TO"), Map.entry("secret", "USES_SECRET"),
            Map.entry("usedForCredentials", "USES_SECRET"), Map.entry("credentials",
                    "USES_SECRET"), Map.entry("adapterFunctions", "CALLS"),
            Map.entry("affectedElements", "ATTACHED_TO"));
    private static final Map<String, String> PIM_CATEGORY_BY_PACKAGE = Map.ofEntries(
            Map.entry("pim", "PIM Root"),
            Map.entry("api", "API Surface"),
            Map.entry("compute", "Compute and Triggers"),
            Map.entry("config", "Configuration and Secrets"),
            Map.entry("contracts", "Contracts and Events"),
            Map.entry("data", "Data Design"),
            Map.entry("deployment", "Deployment and Environment"),
            Map.entry("external", "External Integration"),
            Map.entry("integration", "Integration and Events"),
            Map.entry("policy", "Policies and Operations"),
            Map.entry("security", "Security and Access"),
            Map.entry("workflow", "Workflow"),
            Map.entry("kernel", "Traceability and Readiness"));
    private static final Map<String, Map<String, Object>> PIM_VISUALS = Map.ofEntries(
            visual("PIMModel", "PIM Root", "database", "#334155", "model",
                    List.of("architectureStyle", "domainName", "providerIndependent")),
            visual("ServerlessService", "Architecture Overview", "box", "#0F766E", "service",
                    List.of("boundaryType", "responsibility", "ownerTeam")),
            visual("DeploymentUnit", "Deployment and Environment", "package", "#0F766E",
                    "deployment unit", List.of("unitType", "independentlyDeployable",
                            "targetEnvironments")),
            visual("Environment", "Deployment and Environment", "landmark", "#0F766E",
                    "environment", List.of("environmentClass", "productionLike",
                            "requiresApproval")),
            visual("ImplementationProfile", "Deployment and Environment", "settings",
                    "#64748B", "implementation",
                    List.of("primaryLanguage", "packageManager", "sourceLayout")),
            visual("Function", "Compute and Triggers", "braces", "#2563EB", "function",
                    List.of("functionKind", "handlerResponsibility", "executionModel")),
            visual("FunctionContract", "Contracts and Events", "file-text", "#2563EB",
                    "contract", List.of("contractVersion", "inputSchema", "outputSchema")),
            visual("Trigger", "Compute and Triggers", "log-in", "#DB2777", "trigger",
                    List.of("triggerKind", "invocationMode", "source")),
            visual("Api", "API Surface", "route", "#7C3AED", "api",
                    List.of("publicName", "version", "basePath", "apiStyle")),
            visual("ApiRoute", "API Surface", "corner-down-right", "#7C3AED", "route",
                    List.of("method", "pathTemplate", "operationId", "authRequired")),
            visual("ErrorMapping", "API Surface", "triangle-alert", "#DC2626", "error map",
                    List.of("domainErrorCode", "abstractStatusClass", "errorSchema")),
            visual("Schema", "Contracts and Events", "braces", "#0891B2", "schema",
                    List.of("schemaKind", "semanticVersion", "compatibility")),
            visual("SchemaField", "Contracts and Events", "list", "#0891B2", "field",
                    List.of("fieldType", "required", "classification")),
            visual("SchemaEnumLiteral", "Contracts and Events", "list-checks", "#0891B2",
                    "enum", List.of("literal", "displayName", "deprecationReason")),
            visual("SchemaConstraint", "Contracts and Events", "badge-alert", "#0891B2",
                    "constraint", List.of("expressionLanguage", "severity", "message")),
            visual("ApiContract", "Contracts and Events", "file-text", "#7C3AED",
                    "api contract", List.of("publicVersion", "backwardCompatible")),
            visual("EventEnvelope", "Contracts and Events", "mail", "#DB2777", "envelope",
                    List.of("eventIdField", "eventTypeField", "metadataRequired")),
            visual("EventType", "Contracts and Events", "sparkles", "#DB2777", "event",
                    List.of("semanticName", "version", "schema")),
            visual("DataStore", "Data Design", "database", "#0891B2", "data store",
                    List.of("storeKind", "consistencyNeed", "expectedAccessRate")),
            visual("ObjectStore", "Data Design", "folder-archive", "#0891B2", "object store",
                    List.of("objectTypes", "versioningRequired", "eventNotificationRequired")),
            visual("DataModel", "Data Design", "table", "#0891B2", "data model",
                    List.of("dataModelKind", "schema", "sourceOfTruth")),
            visual("DataField", "Data Design", "columns", "#0891B2", "data field",
                    List.of("fieldType", "identifier", "storageName")),
            visual("AccessPattern", "Data Design", "search", "#0891B2", "access pattern",
                    List.of("patternName", "operation", "queryBy")),
            visual("IndexCandidate", "Data Design", "list-filter", "#0891B2", "index",
                    List.of("indexPurpose", "partitionKeyField", "sortKeyField")),
            visual("DataAccess", "Data Design", "database-zap", "#0891B2", "data access",
                    List.of("mode", "purpose", "transactional")),
            visual("Queue", "Integration and Events", "inbox", "#DB2777", "queue",
                    List.of("channelKind", "deliverySemantics", "fifoRequired")),
            visual("Topic", "Integration and Events", "radio", "#DB2777", "topic",
                    List.of("channelKind", "subscriptionMode", "filteringRequired")),
            visual("EventBus", "Integration and Events", "git-branch", "#DB2777",
                    "event bus", List.of("channelKind", "routingExpressionLanguage")),
            visual("Schedule", "Integration and Events", "clock", "#DB2777", "schedule",
                    List.of("scheduleExpression", "enabled", "timeZone")),
            visual("Subscription", "Integration and Events", "rss", "#DB2777",
                    "subscription", List.of("filterExpression", "target", "rawDelivery")),
            visual("EventRoutingRule", "Integration and Events", "route", "#DB2777",
                    "routing rule", List.of("eventPattern", "enabled", "targets")),
            visual("RequestResponseFlow", "Integration and Events", "move-right", "#EA580C",
                    "request flow", List.of("flowPurpose", "synchronous", "apiRoute")),
            visual("EventFlow", "Integration and Events", "shuffle", "#EA580C", "event flow",
                    List.of("flowPurpose", "eventType", "channel")),
            visual("MessageFlow", "Integration and Events", "mail", "#EA580C", "message flow",
                    List.of("flowPurpose", "messageSchema", "queue")),
            visual("PubSubFlow", "Integration and Events", "radio", "#EA580C", "pub/sub",
                    List.of("flowPurpose", "topic", "subscriptions")),
            visual("OrchestrationFlow", "Integration and Events", "workflow", "#EA580C",
                    "orchestration", List.of("flowPurpose", "workflow")),
            visual("ExternalIntegrationFlow", "Integration and Events", "server", "#EA580C",
                    "external flow", List.of("flowPurpose", "adapter")),
            visual("Workflow", "Workflow", "workflow", "#EA580C", "workflow",
                    List.of("workflowKind", "executionSemantics", "startState")),
            visual("WorkflowState", "Workflow", "circle-dot", "#EA580C", "state",
                    List.of("stateKind", "orderIndex", "terminal")),
            visual("WorkflowTransition", "Workflow", "arrow-right", "#EA580C", "transition",
                    List.of("conditionExpression", "defaultTransition")),
            visual("ErrorHandler", "Workflow", "octagon-alert", "#DC2626", "catch",
                    List.of("errorSelector", "recoveryAction", "nextState")),
            visual("CompensationPolicy", "Workflow", "rotate-ccw", "#EA580C", "compensation",
                    List.of("compensationStrategy", "automatic")),
            visual("ExternalAdapter", "External Integration", "server", "#64748B",
                    "external adapter", List.of("externalSystemName", "protocolFamily",
                            "endpointDescription")),
            visual("ConfigurationSet", "Configuration and Secrets", "settings", "#CA8A04",
                    "configuration", List.of("scope", "parameters", "environmentVariables")),
            visual("ConfigParameter", "Configuration and Secrets", "sliders-horizontal",
                    "#CA8A04", "parameter", List.of("valueKind", "stageSpecific", "required")),
            visual("EnvironmentVariable", "Configuration and Secrets", "terminal", "#CA8A04",
                    "env var", List.of("variableName", "valueSource", "secretReference")),
            visual("Secret", "Configuration and Secrets", "key-round", "#CA8A04", "secret",
                    List.of("secretKind", "rotationRequired", "ownerTeam")),
            visual("CredentialRequirement", "Configuration and Secrets", "key", "#CA8A04",
                    "credential", List.of("secretKind", "purpose", "rotationRequired")),
            visual("IdentityProvider", "Security and Access", "shield-check", "#DC2626",
                    "identity", List.of("identityKind", "mfaRequired", "tokenType")),
            visual("Principal", "Security and Access", "users", "#DC2626", "principal",
                    List.of("principalKind", "privileged", "externalRef")),
            visual("Permission", "Security and Access", "badge-check", "#DC2626",
                    "permission", List.of("effect", "action", "targetResource")),
            visual("ArchitecturePolicy", "Policies and Operations", "gavel", "#64748B",
                    "policy", List.of("policyScope", "productionRequired", "attachedTo")),
            visual("SecurityPolicy", "Security and Access", "shield", "#DC2626",
                    "security policy", List.of("authenticationRequired", "authorizationRequired")),
            visual("AuthPolicy", "Security and Access", "lock", "#DC2626", "auth policy",
                    List.of("authScheme", "mfaRequired", "identityProvider")),
            visual("AuthorizationPolicy", "Security and Access", "user-check", "#DC2626",
                    "authorization", List.of("roleOrScopeRequired", "allowedPrincipals")),
            visual("DataProtectionPolicy", "Policies and Operations", "shield", "#16A34A",
                    "data policy", List.of("classification", "encryptionRequired")),
            visual("CompliancePolicy", "Policies and Operations", "scale", "#CA8A04",
                    "compliance", List.of("regulation", "controlId")),
            visual("ResiliencePolicy", "Policies and Operations", "shield-alert", "#64748B",
                    "resilience", List.of("retryEnabled", "deadLetterRequired")),
            visual("RetryPolicy", "Policies and Operations", "refresh-cw", "#64748B", "retry",
                    List.of("maxAttempts", "backoffRate")),
            visual("DeadLetterPolicy", "Policies and Operations", "mail-x", "#64748B",
                    "dead letter", List.of("required", "deadLetterChannel")),
            visual("TimeoutPolicy", "Policies and Operations", "timer", "#64748B", "timeout",
                    List.of("timeoutSeconds", "clientTimeoutSeconds")),
            visual("IdempotencyPolicy", "Policies and Operations", "repeat", "#64748B",
                    "idempotency", List.of("keySource", "storeRequired")),
            visual("ConcurrencyPolicy", "Policies and Operations", "gauge", "#64748B",
                    "concurrency", List.of("maxConcurrency", "reservedConcurrencyHint")),
            visual("RateLimitPolicy", "Policies and Operations", "activity", "#64748B",
                    "rate limit", List.of("requestsPerSecond", "burstLimit")),
            visual("BatchPolicy", "Policies and Operations", "rows", "#64748B", "batch",
                    List.of("batchSize", "partialFailureHandling")),
            visual("OrderingPolicy", "Policies and Operations", "list-ordered", "#64748B",
                    "ordering", List.of("orderingRequirement", "orderingKey")),
            visual("CachePolicy", "Policies and Operations", "archive", "#64748B", "cache",
                    List.of("cacheRequired", "ttlSeconds")),
            visual("BackupPolicy", "Policies and Operations", "database-backup", "#64748B",
                    "backup", List.of("backupRequired", "backupFrequency")),
            visual("RetentionPolicy", "Policies and Operations", "calendar-clock", "#64748B",
                    "retention", List.of("retentionPeriod", "deletionAfterRetention")),
            visual("CostPolicy", "Policies and Operations", "badge-dollar-sign", "#64748B",
                    "cost", List.of("budget", "costDriver")),
            visual("ObservabilityConfig", "Policies and Operations", "monitoring", "#64748B",
                    "observability", List.of("loggingEnabled", "metricsEnabled",
                            "tracingEnabled")),
            visual("LoggingPolicy", "Policies and Operations", "file-text", "#64748B",
                    "logging", List.of("logFormat", "logLevel", "structuredLogging")),
            visual("MetricPolicy", "Policies and Operations", "chart-line", "#64748B",
                    "metric", List.of("metricName", "unit", "statistic")),
            visual("MetricDimension", "Policies and Operations", "tags", "#64748B",
                    "dimension", List.of("key", "valueExpression")),
            visual("TracingPolicy", "Policies and Operations", "git-merge", "#64748B",
                    "tracing", List.of("tracingRequired", "samplingPolicy")),
            visual("AlertPolicy", "Policies and Operations", "bell", "#64748B", "alert",
                    List.of("metricName", "condition", "threshold")),
            visual("Slo", "Policies and Operations", "target", "#64748B", "slo",
                    List.of("objectiveName", "metric", "target")),
            visual("CorsPolicy", "Policies and Operations", "globe", "#64748B", "cors",
                    List.of("allowedOrigins", "allowedMethods")),
            visual("TraceModel", "Traceability and Readiness", "git-merge", "#4F46E5",
                    "trace model", List.of("links")),
            visual("TraceLink", "Traceability and Readiness", "link", "#4F46E5", "trace",
                    List.of("linkType", "source", "target")),
            visual("ProductionReadinessAssessment", "Traceability and Readiness",
                    "clipboard-check", "#0F766E", "readiness",
                    List.of("readinessStatus", "transformationReady", "productionReady")),
            visual("ReadinessFinding", "Traceability and Readiness", "triangle-alert",
                    "#DC2626", "finding", List.of("severity", "message", "blocking")),
            visual("ReadinessCheck", "Traceability and Readiness", "list-checks", "#0F766E",
                    "check", List.of("checkId", "severity", "passed")),
            visual("ManualDecision", "Traceability and Readiness", "clipboard-check",
                    "#7C3AED", "decision", List.of("question", "decisionOwner", "blocking")),
            visual("StructuredDocument", "Traceability and Readiness", "file-text", "#64748B",
                    "document", List.of("format", "externalUri")),
            visual("KeyValue", "Traceability and Readiness", "list", "#64748B",
                    "key value", List.of("key", "value")),
            visual("Annotation", "Traceability and Readiness", "sticky-note", "#64748B",
                    "annotation", List.of("key", "value", "source")));
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
    private static final Set<String> CONTAINED_ONLY_CIM_TYPES = Set.of("AcceptanceCriterion",
            "QualityScenario", "LifecycleStateDefinition", "BusinessInvariant", "StartStep",
            "EndStep", "CommandStep", "QueryStep", "EventStep", "PolicyStep", "HumanTaskStep",
            "ExternalInteractionStep", "DecisionStep", "WaitStep", "DecisionRule",
            "ExceptionScenario", "TemporalConstraint", "Annotation", "ReadinessFinding",
            "ReadinessCheck", "ManualDecision");
    private static final Set<String> NON_CREATABLE_CIM_TYPES = Set.of("CIMWorkspace",
            "CIMModel", "DiagramModel", "DiagramView", "DiagramNode", "DiagramEdge",
            "KeyValue", "StructuredDocument");
    private static final Set<String> ABSTRACT_PIM_TYPES = Set.of("ModelElement",
            "TraceableElement", "SemanticRelationship", "TransformationAssumption",
            "ComputeElement", "StorageElement", "IntegrationElement", "EventChannel", "Flow",
            "ArchitecturePolicy", "DeployableElement", "InvocationSource", "InvocationTarget",
            "FunctionTarget", "WorkflowTarget", "SubscriptionTarget", "RoutingTarget",
            "FlowEndpoint", "ProtectedResource", "PolicyTarget", "DataAccessTarget",
            "ExternalCallTarget", "EnvironmentTarget", "CredentialRequirementLike",
            "RouteEndpoint", "EventCarrier");
    private static final Set<String> RELATIONSHIP_ONLY_PIM_TYPES = Set.of("Trigger",
            "DataAccess", "RequestResponseFlow", "EventFlow", "MessageFlow", "PubSubFlow",
            "OrchestrationFlow", "ExternalIntegrationFlow", "WorkflowTransition", "Permission",
            "Subscription", "EventRoutingRule", "TraceLink");
    private static final Set<String> CONTAINED_ONLY_PIM_TYPES = Set.of("FunctionContract",
            "ApiRoute", "ErrorMapping", "SchemaField", "SchemaEnumLiteral", "SchemaConstraint",
            "ApiContract", "EventEnvelope", "DataModel", "DataField", "AccessPattern",
            "IndexCandidate", "WorkflowState", "ErrorHandler", "CompensationPolicy",
            "ConfigParameter", "EnvironmentVariable", "CredentialRequirement", "RetryPolicy",
            "DeadLetterPolicy", "LoggingPolicy", "MetricPolicy", "MetricDimension",
            "TracingPolicy", "AlertPolicy", "Slo", "Annotation", "ReadinessFinding",
            "ReadinessCheck", "ManualDecision");
    private static final Set<String> NON_CREATABLE_PIM_TYPES = Set.of("PIMModel", "KeyValue",
            "StructuredDocument");
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
        } else if ("pim".equals(key)) {
            metadata = augmentPimMetadata(metadata);
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

    private Map<String, Object> augmentPimMetadata(Map<String, Object> base) {
        Map<String, Object> metadata = new LinkedHashMap<>(base);
        CimMetamodel metamodel = readEcoreMetamodel("pim", PIM_VISUALS,
                PIM_CATEGORY_BY_PACKAGE, ABSTRACT_PIM_TYPES, RELATIONSHIP_ONLY_PIM_TYPES,
                CONTAINED_ONLY_PIM_TYPES, NON_CREATABLE_PIM_TYPES);
        metadata.put("elements", metamodel.elements());
        metadata.put("relationshipRules", metamodel.relationshipRules());
        metadata.put("semanticReferenceRules", metamodel.semanticReferenceRules());
        metadata.put("relationshipKindLabels", pimRelationshipKindLabels());
        metadata.put("viewDefinitions", pimViewDefinitions());
        metadata.put("constraints", pimConstraints());
        metadata.put("rootTemplate", pimRootTemplate());
        return metadata;
    }

    private CimMetamodel readEcoreMetamodel(String key, Map<String, Map<String, Object>> visuals,
            Map<String, String> categoryByPackage, Set<String> abstractTypes,
            Set<String> relationshipOnlyTypes, Set<String> containedOnlyTypes,
            Set<String> nonCreatableTypes) {
        String resource = "modeling/metamodels/" + key + "/" + key + "-combined.ecore";
        try (InputStream input = Thread.currentThread().getContextClassLoader()
                .getResourceAsStream(resource)) {
            if (input == null) {
                throw new PlatformException(500,
                        "Missing " + key.toUpperCase() + " metamodel metadata resource.");
            }
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
                elements.add(metamodelElement(modelClass, classByType, enumLiteralsByType,
                        visuals, categoryByPackage, abstractTypes, relationshipOnlyTypes,
                        containedOnlyTypes, nonCreatableTypes));
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
                    "Could not read " + key.toUpperCase() + " metamodel metadata.");
        }
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
            Map<String, List<String>> enumLiteralsByType,
            Map<String, Map<String, Object>> visuals,
            Map<String, String> categoryByPackage, Set<String> abstractTypes,
            Set<String> relationshipOnlyTypes, Set<String> containedOnlyTypes,
            Set<String> nonCreatableTypes) {
        String type = modelClass.name();
        Map<String, Object> visual = visuals.getOrDefault(type, Map.of());
        List<Map<String, Object>> attributes = new ArrayList<>();
        List<Map<String, Object>> references = new ArrayList<>();
        inheritedEcoreFeatures(modelClass, classByType, true).forEach(feature ->
                attributes.add(cimAttribute(feature, enumLiteralsByType)));
        inheritedEcoreFeatures(modelClass, classByType, false).forEach(feature ->
                references.add(cimReference(feature)));
        boolean abstractType = modelClass.abstractType() || abstractTypes.contains(type);
        boolean relationshipElement = relationshipOnlyTypes.contains(type);
        Map<String, Object> element = new LinkedHashMap<>();
        element.put("type", type);
        element.put("label", humanize(type));
        element.put("package", modelClass.packageName());
        element.put("category", visual.getOrDefault("category",
                categoryByPackage.getOrDefault(modelClass.packageName(), type)));
        element.put("icon", PLACEHOLDER_ICON);
        element.put("color", visual.getOrDefault("color", "#64748B"));
        element.put("notation", Map.of("tag", visual.getOrDefault("notation", "element"),
                "lineFields", visual.getOrDefault("visibleFields", List.of())));
        element.put("visibleFields", visual.getOrDefault("visibleFields", List.of()));
        element.put("attributes", attributes);
        element.put("references", references);
        element.put("supertypes", allEcoreSuperTypes(modelClass, classByType));
        element.put("abstract", abstractType);
        element.put("relationshipElement", relationshipElement);
        element.put("containedOnly", containedOnlyTypes.contains(type));
        element.put("supportOnly", nonCreatableTypes.contains(type));
        element.put("creatable",
                !abstractType && !relationshipElement && !containedOnlyTypes.contains(type)
                        && !nonCreatableTypes.contains(type));
        return element;
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
        element.put("containedOnly", CONTAINED_ONLY_CIM_TYPES.contains(type));
        element.put("creatable", !abstractType && !RELATIONSHIP_ONLY_CIM_TYPES.contains(type)
                && !CONTAINED_ONLY_CIM_TYPES.contains(type)
                && !NON_CREATABLE_CIM_TYPES.contains(type));
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
        element.put("containedOnly", CONTAINED_ONLY_CIM_TYPES.contains(type));
        element.put("creatable", !abstractType && !RELATIONSHIP_ONLY_CIM_TYPES.contains(type)
                && !CONTAINED_ONLY_CIM_TYPES.contains(type)
                && !NON_CREATABLE_CIM_TYPES.contains(type));
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

    private void collectReferenceRules(String levelKey, EcoreClass modelClass,
            List<Map<String, Object>> relationshipRules,
            List<Map<String, Object>> semanticReferenceRules) {
        String sourceType = modelClass.name();
        for (EmfaticFeature feature : modelClass.references()) {
            if (feature.type().isBlank() || feature.readonly()) {
                continue;
            }
            String kind = relationshipKind(levelKey, sourceType, feature.name(),
                    feature.containment());
            relationshipRules.add(Map.of("sourceType", sourceType, "targetType",
                    genericTarget(feature.type()), "allowedKinds", List.of(kind), "feature",
                    feature.name(), "containment", feature.containment()));
            if (!feature.containment()) {
                semanticReferenceRules.add(Map.of("sourceType", sourceType, "targetType",
                        genericTarget(feature.type()), "feature", feature.name(), "kind", kind,
                        "reverse", reverseReference(levelKey, feature.name())));
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

    private String relationshipKind(String levelKey, String sourceType, String featureName,
            boolean containment) {
        if ("pim".equals(levelKey)) {
            return pimRelationshipKind(sourceType, featureName, containment);
        }
        return relationshipKind(sourceType, featureName, containment);
    }

    private String pimRelationshipKind(String sourceType, String featureName, boolean containment) {
        if (containment) {
            if ("Workflow".equals(sourceType) && "transitions".equals(featureName)) {
                return "TRANSITION";
            }
            if ("Principal".equals(sourceType) && "permissions".equals(featureName)) {
                return "PERMISSION";
            }
            if ("Topic".equals(sourceType) && "subscriptions".equals(featureName)) {
                return "SUBSCRIBES_TO";
            }
            if ("EventBus".equals(sourceType) && "routingRules".equals(featureName)) {
                return "ROUTES_TO";
            }
            return "CONTAINS";
        }
        if (Set.of("RequestResponseFlow", "EventFlow", "MessageFlow", "PubSubFlow",
                "OrchestrationFlow", "ExternalIntegrationFlow").contains(sourceType)
                && Set.of("source", "target").contains(featureName)) {
            return switch (sourceType) {
                case "RequestResponseFlow" -> "REQUEST_RESPONSE";
                case "EventFlow" -> "EVENT_FLOW";
                case "MessageFlow" -> "MESSAGE_FLOW";
                case "PubSubFlow" -> "PUB_SUB";
                case "OrchestrationFlow" -> "ORCHESTRATES";
                case "ExternalIntegrationFlow" -> "EXTERNAL_CALL";
                default -> "FLOW";
            };
        }
        if ("WorkflowTransition".equals(sourceType)
                && Set.of("source", "target").contains(featureName)) {
            return "TRANSITION";
        }
        if ("TraceLink".equals(sourceType) && Set.of("source", "target").contains(featureName)) {
            return "TRACE";
        }
        return PIM_REFERENCE_KINDS.getOrDefault(featureName, featureNameToKind(featureName));
    }

    private boolean reverseReference(String levelKey, String featureName) {
        if ("pim".equals(levelKey)) {
            return Set.of("producedBy", "consumedBy", "invokesFunction", "startsWorkflow")
                    .contains(featureName);
        }
        return reverseReference(featureName);
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

    private Map<String, String> pimRelationshipKindLabels() {
        return Map.ofEntries(Map.entry("CONTAINS", "contains"),
                Map.entry("OWNS", "owns"), Map.entry("DEPLOYS", "packages"),
                Map.entry("DEPLOYS_TO", "deploys to"), Map.entry("ROUTES_TO", "routes to"),
                Map.entry("INVOKES", "invokes"), Map.entry("TRIGGERS", "triggers"),
                Map.entry("READS", "reads"), Map.entry("WRITES", "writes"),
                Map.entry("READ_WRITE", "read/write"), Map.entry("APPEND", "append"),
                Map.entry("DELETE", "delete"), Map.entry("PUBLISHES", "publishes"),
                Map.entry("SUBSCRIBES_TO", "subscribes to"), Map.entry("CALLS", "calls"),
                Map.entry("USES", "uses"), Map.entry("USES_SECRET", "uses secret"),
                Map.entry("HAS_ENV", "has env"), Map.entry("AUTHORIZED_BY", "authorized by"),
                Map.entry("ATTACHED_TO", "attached to"), Map.entry("FLOW", "flow"),
                Map.entry("REQUEST_RESPONSE", "request/response"),
                Map.entry("EVENT_FLOW", "event flow"), Map.entry("MESSAGE_FLOW", "message flow"),
                Map.entry("PUB_SUB", "pub/sub"), Map.entry("ORCHESTRATES", "orchestrates"),
                Map.entry("EXTERNAL_CALL", "external call"),
                Map.entry("DATA_ACCESS", "data access"), Map.entry("PERMISSION", "permission"),
                Map.entry("TRANSITION", "transition"), Map.entry("TRACE", "trace"),
                Map.entry("DEAD_LETTER", "dead letter"), Map.entry("TARGETS", "targets"),
                Map.entry("CONSTRAINS", "constrains"));
    }

    private List<Map<String, Object>> pimViewDefinitions() {
        return List.of(
                view("pim-architecture-overview", "Architecture Overview",
                        "ARCHITECTURE_OVERVIEW", "architecture",
                        List.of("ServerlessService", "Api", "Function", "Workflow", "Queue",
                                "Topic", "EventBus", "Schedule", "DataStore", "ObjectStore",
                                "ExternalAdapter", "IdentityProvider", "Secret",
                                "RequestResponseFlow", "EventFlow", "MessageFlow", "PubSubFlow",
                                "OrchestrationFlow", "ExternalIntegrationFlow"),
                        List.of("ServerlessService", "Api", "Function", "Workflow", "Queue",
                                "Topic", "EventBus", "Schedule", "DataStore", "ObjectStore",
                                "ExternalAdapter", "IdentityProvider", "Secret"),
                        List.of("OWNS", "ROUTES_TO", "INVOKES", "PUBLISHES", "SUBSCRIBES_TO",
                                "READS", "WRITES", "CALLS", "FLOW", "REQUEST_RESPONSE",
                                "EVENT_FLOW", "MESSAGE_FLOW", "PUB_SUB", "ORCHESTRATES",
                                "EXTERNAL_CALL"), "CONTAINER"),
                view("pim-api-surface", "API Surface", "API_SURFACE", "api",
                        List.of("Api", "ApiRoute", "ApiContract", "ErrorMapping", "Schema",
                                "Function", "Workflow", "AuthPolicy", "AuthorizationPolicy",
                                "CorsPolicy", "RateLimitPolicy", "TimeoutPolicy",
                                "ObservabilityConfig"),
                        List.of("Api", "Schema", "Function", "Workflow", "AuthPolicy",
                                "AuthorizationPolicy", "CorsPolicy", "RateLimitPolicy",
                                "TimeoutPolicy", "ObservabilityConfig"),
                        List.of("CONTAINS", "ROUTES_TO", "AUTHORIZED_BY", "USES",
                                "ATTACHED_TO"), "TABLE"),
                view("pim-compute-trigger", "Compute and Trigger", "COMPUTE_TRIGGER", "compute",
                        List.of("Function", "FunctionContract", "Trigger", "Schedule",
                                "ApiRoute", "Queue", "Topic", "EventBus", "ObjectStore",
                                "Workflow", "Schema", "EventType", "Secret",
                                "EnvironmentVariable", "IdempotencyPolicy", "TimeoutPolicy",
                                "ResiliencePolicy", "ConcurrencyPolicy", "ObservabilityConfig"),
                        List.of("Function", "Schedule", "Queue", "Topic", "EventBus",
                                "ObjectStore", "Workflow", "Schema", "EventType", "Secret",
                                "IdempotencyPolicy", "TimeoutPolicy", "ResiliencePolicy",
                                "ConcurrencyPolicy", "ObservabilityConfig"),
                        List.of("INVOKES", "TRIGGERS", "ROUTES_TO", "PUBLISHES",
                                "SUBSCRIBES_TO", "READS", "WRITES", "CALLS", "USES_SECRET",
                                "HAS_ENV", "ATTACHED_TO"), "DEFAULT_LAYERED"),
                view("pim-contract-schema-event", "Contract Schema and Event",
                        "CONTRACT_SCHEMA_EVENT", "contracts",
                        List.of("Schema", "SchemaField", "SchemaEnumLiteral", "SchemaConstraint",
                                "FunctionContract", "ApiContract", "EventType",
                                "EventEnvelope", "Function", "Api"),
                        List.of("Schema", "EventType", "Function", "Api"),
                        List.of("CONTAINS", "USES", "PUBLISHES", "SUBSCRIBES_TO"), "TABLE"),
                view("pim-data-design", "Data Design", "DATA_DESIGN", "data",
                        List.of("DataStore", "ObjectStore", "DataModel", "DataField",
                                "AccessPattern", "IndexCandidate", "DataAccess", "Schema",
                                "Function", "ApiRoute", "DataProtectionPolicy", "RetentionPolicy",
                                "BackupPolicy"),
                        List.of("DataStore", "ObjectStore", "Schema", "Function",
                                "DataProtectionPolicy", "RetentionPolicy", "BackupPolicy"),
                        List.of("READS", "WRITES", "DATA_ACCESS", "CONTAINS", "USES",
                                "ATTACHED_TO"), "TABLE"),
                view("pim-integration-events", "Integration and Event Channels",
                        "INTEGRATION_EVENTS", "integration",
                        List.of("Queue", "Topic", "EventBus", "Schedule", "Subscription",
                                "EventRoutingRule", "RequestResponseFlow", "EventFlow",
                                "MessageFlow", "PubSubFlow", "OrchestrationFlow",
                                "ExternalIntegrationFlow", "EventType", "Function", "Workflow",
                                "ExternalAdapter"),
                        List.of("Queue", "Topic", "EventBus", "Schedule", "EventType",
                                "Function", "Workflow", "ExternalAdapter"),
                        List.of("PUBLISHES", "SUBSCRIBES_TO", "ROUTES_TO", "INVOKES",
                                "EVENT_FLOW", "MESSAGE_FLOW", "PUB_SUB", "ORCHESTRATES",
                                "EXTERNAL_CALL", "DEAD_LETTER"), "EVENT_FLOW"),
                view("pim-workflow-designer", "Workflow Designer", "WORKFLOW_DESIGNER",
                        "workflow",
                        List.of("Workflow", "WorkflowState", "WorkflowTransition",
                                "ErrorHandler", "CompensationPolicy", "Function",
                                "ExternalAdapter", "RetryPolicy", "ResiliencePolicy",
                                "ObservabilityConfig", "IdempotencyPolicy"),
                        List.of("Workflow", "Function", "ExternalAdapter", "ResiliencePolicy",
                                "ObservabilityConfig", "IdempotencyPolicy"),
                        List.of("TRANSITION", "INVOKES", "EXTERNAL_CALL", "ORCHESTRATES",
                                "CONTAINS", "ATTACHED_TO"), "PROCESS"),
                view("pim-security-access", "Security and Access", "SECURITY_ACCESS",
                        "security",
                        List.of("IdentityProvider", "Principal", "Permission", "Api",
                                "ApiRoute", "Function", "Secret", "DataStore", "ObjectStore",
                                "ExternalAdapter", "Queue", "Topic", "EventBus", "Schedule",
                                "Workflow", "WorkflowState", "SecurityPolicy", "AuthPolicy",
                                "AuthorizationPolicy"),
                        List.of("IdentityProvider", "Principal", "Api", "Function", "Secret",
                                "DataStore", "ObjectStore", "Workflow", "SecurityPolicy",
                                "AuthPolicy", "AuthorizationPolicy"),
                        List.of("PERMISSION", "AUTHORIZED_BY", "ATTACHED_TO", "CONSTRAINS",
                                "USES_SECRET"), "GOVERNANCE"),
                view("pim-deployment-environment", "Deployment and Environment",
                        "DEPLOYMENT_ENVIRONMENT", "deployment",
                        List.of("DeploymentUnit", "ServerlessService", "Environment",
                                "ImplementationProfile", "Function", "Api", "Workflow",
                                "ConfigurationSet", "ConfigParameter", "EnvironmentVariable",
                                "Secret", "Queue", "Topic", "EventBus", "Schedule",
                                "DataStore", "ObjectStore", "ExternalAdapter"),
                        List.of("DeploymentUnit", "ServerlessService", "Environment",
                                "ImplementationProfile", "ConfigurationSet", "Secret"),
                        List.of("DEPLOYS", "DEPLOYS_TO", "OWNS", "HAS_ENV", "ATTACHED_TO"),
                        "MATRIX"),
                view("pim-policy-operations", "Policy and Operations", "POLICY_OPERATIONS",
                        "policy",
                        List.of("ArchitecturePolicy", "DataProtectionPolicy", "CompliancePolicy",
                                "ResiliencePolicy", "RetryPolicy", "DeadLetterPolicy",
                                "TimeoutPolicy", "IdempotencyPolicy", "ConcurrencyPolicy",
                                "RateLimitPolicy", "BatchPolicy", "OrderingPolicy",
                                "CachePolicy", "BackupPolicy", "RetentionPolicy", "CostPolicy",
                                "ObservabilityConfig", "LoggingPolicy", "MetricPolicy",
                                "MetricDimension", "TracingPolicy", "AlertPolicy", "Slo",
                                "CorsPolicy", "Api", "Function", "DataStore", "Queue",
                                "Workflow"),
                        List.of("DataProtectionPolicy", "CompliancePolicy", "ResiliencePolicy",
                                "TimeoutPolicy", "IdempotencyPolicy", "ConcurrencyPolicy",
                                "RateLimitPolicy", "BatchPolicy", "OrderingPolicy",
                                "CachePolicy", "BackupPolicy", "RetentionPolicy", "CostPolicy",
                                "ObservabilityConfig", "CorsPolicy"),
                        List.of("ATTACHED_TO", "CONSTRAINS", "DEAD_LETTER", "CONTAINS"),
                        "TABLE"),
                view("pim-configuration-secrets", "Configuration and Secrets",
                        "CONFIGURATION_SECRETS", "configuration",
                        List.of("ConfigurationSet", "ConfigParameter", "EnvironmentVariable",
                                "Secret", "CredentialRequirement", "Environment", "Function",
                                "ExternalAdapter", "AuthPolicy"),
                        List.of("ConfigurationSet", "Secret", "Environment", "Function",
                                "ExternalAdapter"),
                        List.of("HAS_ENV", "USES_SECRET", "ATTACHED_TO", "DEPLOYS_TO"),
                        "TABLE"),
                view("pim-readiness-traceability", "Readiness and Traceability",
                        "READINESS_TRACEABILITY", "readiness",
                        List.of("TraceModel", "TraceLink", "ProductionReadinessAssessment",
                                "ReadinessFinding", "ReadinessCheck", "ManualDecision",
                                "ServerlessService", "Function", "Api", "Workflow",
                                "DataStore", "Queue", "ArchitecturePolicy"),
                        List.of("TraceModel", "ProductionReadinessAssessment"),
                        List.of("TRACE", "ATTACHED_TO", "CONSTRAINS"), "MATRIX"));
    }

    private List<Map<String, Object>> pimConstraints() {
        return List.of(
                Map.of("type", "PIMModel", "requiredAny",
                        List.of("services", "deploymentUnits", "environments", "functions"),
                        "message",
                        "PIM models need a service, deployment unit, environment and function."),
                Map.of("type", "Function", "requiredAny", List.of("functionKind", "contract"),
                        "message", "Functions need a function kind and contract."),
                Map.of("type", "Api", "requiredAny", List.of("apiStyle", "routes"), "message",
                        "APIs need a style and at least one route."),
                Map.of("type", "ApiRoute", "requiredAny", List.of("method", "pathTemplate"),
                        "message", "API routes need method and path template."),
                Map.of("type", "DataStore", "requiredAny",
                        List.of("storeKind", "consistencyNeed", "ownedDataModels",
                                "accessPatterns"), "message",
                        "Data stores need a kind, consistency need, data model and access pattern."),
                Map.of("type", "Workflow", "requiredAny",
                        List.of("workflowKind", "states", "startState", "endStates"), "message",
                        "Workflows need states, one start state and at least one end state."),
                Map.of("type", "EventType", "requiredAny", List.of("semanticName", "schema"),
                        "message", "Event types need a semantic name and schema."),
                Map.of("type", "Trigger", "requiredAny",
                        List.of("source", "invocationMode"), "message",
                        "Triggers need an invocation source and mode."),
                Map.of("type", "DataAccess", "requiredAny", List.of("mode", "function", "store"),
                        "message", "Data access edges need mode, function and store."),
                Map.of("type", "Permission", "requiredAny",
                        List.of("effect", "targetResource"), "message",
                        "Permissions need an effect and protected target."));
    }

    private Map<String, Object> pimRootTemplate() {
        Map<String, Object> root = new LinkedHashMap<>(defaultRootTemplate());
        root.put("modelLevel", "PIM");
        root.put("eClass", "PIMModel");
        root.put("architectureStyle", "EVENT_DRIVEN_SERVERLESS");
        root.put("domainName", "");
        root.put("architectureRationale", "");
        root.put("defaultCorrelationIdName", "correlationId");
        root.put("providerIndependent", true);
        root.put("services", List.of());
        root.put("deploymentUnits", List.of());
        root.put("environments", List.of());
        root.put("schemas", List.of());
        root.put("functions", List.of());
        root.put("apis", List.of());
        root.put("eventTypes", List.of());
        root.put("channels", List.of());
        root.put("schedules", List.of());
        root.put("triggers", List.of());
        root.put("dataStores", List.of());
        root.put("objectStores", List.of());
        root.put("dataAccesses", List.of());
        root.put("workflows", List.of());
        root.put("externalAdapters", List.of());
        root.put("identityProviders", List.of());
        root.put("principals", List.of());
        root.put("policies", List.of());
        root.put("flows", List.of());
        root.put("configurations", List.of());
        root.put("secrets", List.of());
        root.put("traceModel", null);
        root.put("readiness", null);
        root.put("implementationProfile", null);
        return root;
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
