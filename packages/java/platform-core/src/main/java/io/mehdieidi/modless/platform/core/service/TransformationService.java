package io.mehdieidi.modless.platform.core.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.mehdieidi.modless.mde.etl.CimToPimDefaults;
import io.mehdieidi.modless.mde.etl.EpsilonEtlExecutor;
import io.mehdieidi.modless.mde.etl.EtlExecutionException;
import io.mehdieidi.modless.mde.etl.EtlExecutionReport;
import io.mehdieidi.modless.mde.etl.EtlExecutionStatus;
import io.mehdieidi.modless.platform.core.PlatformException;
import io.mehdieidi.modless.platform.core.model.ArtifactRecord;
import io.mehdieidi.modless.platform.core.model.ModelLevel;
import io.mehdieidi.modless.platform.core.model.ModelRecord;
import io.mehdieidi.modless.platform.core.model.UserRecord;
import io.mehdieidi.modless.platform.core.repository.JsonFileStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class TransformationService {

    private final JsonFileStore store;
    private final ModelService modelService;
    private final ArtifactService artifactService;
    private final XmiModelImportService xmiModelIo;
    private final EpsilonEtlExecutor etlExecutor;

    public TransformationService(JsonFileStore store, ModelService modelService,
            ArtifactService artifactService) {
        this.store = store;
        this.modelService = modelService;
        this.artifactService = artifactService;
        this.xmiModelIo = new XmiModelImportService(store.objectMapper());
        this.etlExecutor = new EpsilonEtlExecutor();
    }

    public ModelRecord cimToPim(UserRecord user, String sourceModelId) {
        ModelRecord source = modelService.get(user, ModelLevel.CIM, sourceModelId);
        ObjectNode target = formalCimToPimModel(source);
        target.put("sourceModelId", source.id());
        mirrorReadinessToManualBacklog(target);
        return modelService.create(user, ModelLevel.PIM, source.projectId(), source.name() + "-pim",
                target);
    }

    public ModelRecord pimToPsm(UserRecord user, String sourceModelId) {
        ModelRecord source = modelService.get(user, ModelLevel.PIM, sourceModelId);
        ObjectNode target = transformedModel(source, ModelLevel.PSM);
        target.put("sourceModelId", source.id());
        addManualBacklog(target,
                "Review generated AWS resource sizing, IAM scope, and deployment settings.");
        return modelService.create(user, ModelLevel.PSM, source.projectId(), source.name() + "-psm",
                target);
    }

    public ArtifactRecord psmToArtifact(UserRecord user, String sourceModelId) {
        ModelRecord source = modelService.get(user, ModelLevel.PSM, sourceModelId);
        Map<String, String> files = new LinkedHashMap<>();
        String projectName = sanitize(source.name());
        files.put("README.md", "# " + source.name() + "\n\nGenerated from PSM model `" + source.id()
                + "` at " + Instant.now() + ".\n\nReview generated files before deployment.\n");
        files.put("template.yaml", """
                AWSTemplateFormatVersion: '2010-09-09'
                Transform: AWS::Serverless-2016-10-31
                Description: Generated Modless AWS serverless scaffold
                Resources: {}
                """);
        files.put("modless/model.psm.json", pretty(source.modelJson()));
        files.put("docs/generation-report.md", "Generated artifact scaffold for `" + projectName
                + "`.\n\nThe formal EGX generator can replace this file-backed implementation when XMI export is wired.\n");
        return artifactService.create(user, source.projectId(), source.name() + "-artifact", files);
    }

    private ObjectNode formalCimToPimModel(ModelRecord source) {
        Path repositoryRoot = findRepositoryRoot();
        Path workDir = null;
        try {
            workDir = Files.createTempDirectory("modless-cim-to-pim-");
            Path cimXmi = workDir.resolve("source-cim.xmi");
            Path pimXmi = workDir.resolve("target-pim.xmi");
            Files.write(cimXmi, sourceCimXmi(source.modelJson()));

            EtlExecutionReport report = etlExecutor.execute(CimToPimDefaults.request(
                    repositoryRoot, cimXmi, pimXmi, true, true));
            if (report.status() != EtlExecutionStatus.SUCCEEDED) {
                throw new PlatformException(500, "CIM-to-PIM ETL failed: "
                        + summarizeDiagnostics(report));
            }
            JsonNode imported = xmiModelIo.importModel(ModelLevel.PIM, Files.readAllBytes(pimXmi));
            if (!imported.isObject()) {
                throw new PlatformException(500, "CIM-to-PIM ETL did not produce a PIM model.");
            }
            ObjectNode target = (ObjectNode) imported;
            target.put("transformedFrom", ModelLevel.CIM.name());
            target.put("transformedFromModelId", source.id());
            target.put("transformationStatus", "GENERATED_BY_ETL");
            return target;
        } catch (PlatformException ex) {
            throw ex;
        } catch (EtlExecutionException ex) {
            throw new PlatformException(500, "CIM-to-PIM ETL failed: "
                    + summarizeDiagnostics(ex.getReport()));
        } catch (Exception ex) {
            throw new PlatformException(500, "CIM-to-PIM ETL could not run: "
                    + (ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage()));
        } finally {
            if (workDir != null && !Boolean.getBoolean("modless.keepEtlTemp")) {
                deleteQuietly(workDir);
            }
        }
    }

    private Path findRepositoryRoot() {
        Path current = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        while (current != null) {
            if (Files.isRegularFile(
                    current.resolve("mde/transformations/cim-to-pim/cim-to-pim.etl"))
                    && Files.isRegularFile(current.resolve(
                    "mde/metamodels/cim/cim-combined.ecore"))
                    && Files.isRegularFile(current.resolve(
                    "mde/metamodels/pim/pim-combined.ecore"))) {
                return current;
            }
            current = current.getParent();
        }
        throw new PlatformException(500,
                "CIM-to-PIM ETL files were not found from the backend working directory.");
    }

    private byte[] sourceCimXmi(JsonNode modelJson) {
        String sourceXmi = text(modelJson, "_sourceXmiBase64", "");
        if (!sourceXmi.isBlank()) {
            try {
                return Base64.getDecoder().decode(sourceXmi);
            } catch (IllegalArgumentException ex) {
                throw new PlatformException(400, "Stored source XMI is not valid base64.");
            }
        }
        return xmiModelIo.exportModel(ModelLevel.CIM, hydrateSemanticReferences(modelJson));
    }

    private String summarizeDiagnostics(EtlExecutionReport report) {
        if (report == null || report.diagnostics().isEmpty()) {
            return "no diagnostics were reported";
        }
        return report.diagnostics().stream()
                .limit(3)
                .map(diagnostic -> diagnostic.phase() + " "
                        + diagnostic.file() + ":" + diagnostic.line() + ":"
                        + diagnostic.column() + " " + diagnostic.reason())
                .collect(java.util.stream.Collectors.joining("; "));
    }

    private JsonNode hydrateSemanticReferences(JsonNode modelJson) {
        if (modelJson == null || !modelJson.isObject()) {
            return modelJson;
        }
        ObjectNode copy = (ObjectNode) modelJson.deepCopy();
        Map<String, JsonNode> graphRelationships = new LinkedHashMap<>();
        JsonNode relationships = copy.path("graph").path("relationships");
        if (relationships.isArray()) {
            relationships.forEach(relationship -> {
                String id = text(relationship, "id", "");
                if (!id.isBlank()) {
                    graphRelationships.put(id, relationship);
                }
            });
        }
        if (!graphRelationships.isEmpty()) {
            hydrateSemanticReferences(copy, graphRelationships);
        }
        return copy;
    }

    private void hydrateSemanticReferences(JsonNode node,
            Map<String, JsonNode> graphRelationships) {
        if (node == null || node.isNull()) {
            return;
        }
        if (node.isObject()) {
            ObjectNode object = (ObjectNode) node;
            JsonNode graphRelationship = graphRelationships.get(text(object, "id", ""));
            if (graphRelationship != null) {
                copyReferenceIfMissing(object, graphRelationship, "source");
                copyReferenceIfMissing(object, graphRelationship, "target");
            }
            object.fields().forEachRemaining(entry -> hydrateSemanticReferences(entry.getValue(),
                    graphRelationships));
            return;
        }
        if (node.isArray()) {
            node.forEach(child -> hydrateSemanticReferences(child, graphRelationships));
        }
    }

    private void copyReferenceIfMissing(ObjectNode target, JsonNode source, String fieldName) {
        if (target.hasNonNull(fieldName) && !target.path(fieldName).asText("").isBlank()) {
            return;
        }
        String value = text(source, fieldName, "");
        if (!value.isBlank()) {
            target.put(fieldName, value);
        }
    }

    private void deleteQuietly(Path path) {
        try (var paths = Files.walk(path)) {
            paths.sorted(java.util.Comparator.reverseOrder()).forEach(item -> {
                try {
                    Files.deleteIfExists(item);
                } catch (Exception ignored) {
                    // Temporary ETL files should not mask transformation results.
                }
            });
        } catch (Exception ignored) {
            // Best-effort cleanup.
        }
    }

    private ObjectNode transformedModel(ModelRecord source, ModelLevel targetLevel) {
        ObjectNode target = source.modelJson() != null && source.modelJson().isObject()
                ? ((ObjectNode) source.modelJson().deepCopy())
                : store.objectMapper().createObjectNode();
        target.put("name", source.name() + "-" + targetLevel.apiName());
        target.put("modelLevel", targetLevel.name());
        target.put("transformedFrom", source.level().name());
        target.put("transformedFromModelId", source.id());
        target.put("transformationStatus", "GENERATED_FOR_REVIEW");
        return target;
    }

    private ObjectNode cimToPimModel(ModelRecord source) {
        JsonNode cim = source.modelJson();
        ObjectNode pim = element("PIMModel", "pim-" + idOf(cim, source.id()),
                text(cim, "name", source.name() + " PIM"));
        pim.put("modelLevel", ModelLevel.PIM.name());
        pim.put("architectureStyle", architectureStyle(cim));
        pim.put("domainName", text(cim, "domainName", source.name()));
        pim.put("architectureRationale", "Generated from CIM model " + source.name()
                + " for review in the PIM workbench.");
        pim.put("defaultCorrelationIdName", "correlationId");
        pim.put("providerIndependent", true);
        pim.put("transformedFrom", ModelLevel.CIM.name());
        pim.put("transformedFromModelId", source.id());
        pim.put("transformationStatus", "GENERATED_FOR_REVIEW");

        ArrayNode services = pim.putArray("services");
        ArrayNode deploymentUnits = pim.putArray("deploymentUnits");
        ArrayNode environments = pim.putArray("environments");
        ArrayNode schemas = pim.putArray("schemas");
        ArrayNode functions = pim.putArray("functions");
        ArrayNode apis = pim.putArray("apis");
        ArrayNode eventTypes = pim.putArray("eventTypes");
        ArrayNode channels = pim.putArray("channels");
        ArrayNode dataStores = pim.putArray("dataStores");
        ArrayNode workflows = pim.putArray("workflows");
        ArrayNode externalAdapters = pim.putArray("externalAdapters");
        ArrayNode policies = pim.putArray("policies");
        pim.putArray("flows");
        pim.putArray("triggers");
        pim.putArray("dataAccesses");
        pim.putArray("objectStores");
        pim.putArray("identityProviders");
        pim.putArray("principals");
        pim.putArray("configurations");
        pim.putArray("secrets");

        List<JsonNode> sourceServices = sourceServices(cim);
        List<String> serviceIds = new ArrayList<>();
        for (JsonNode sourceService : sourceServices) {
            String serviceId = "svc-" + slug(idOf(sourceService, text(sourceService, "name",
                    "service")));
            ObjectNode service = element("ServerlessService", serviceId,
                    text(sourceService, "name", "Application Service"));
            service.put("boundaryType", "CAPABILITY_BASED");
            service.put("responsibility", firstNonBlank(text(sourceService, "description", ""),
                    text(sourceService, "summary", ""), service.path("name").asText()));
            service.put("generatedFrom", idOf(sourceService, ""));
            service.put("generatedByTransformation", true);
            services.add(service);
            serviceIds.add(serviceId);
        }
        if (serviceIds.isEmpty()) {
            ObjectNode service = element("ServerlessService", "svc-application",
                    "Application Service");
            service.put("boundaryType", "CAPABILITY_BASED");
            service.put("responsibility", "Generated service boundary for CIM behavior.");
            service.put("generatedByTransformation", true);
            services.add(service);
            serviceIds.add(service.path("id").asText());
        }

        environments.add(environment("env-dev", "Development", "DEV"));
        environments.add(environment("env-test", "Test", "TEST"));
        environments.add(environment("env-prod", "Production", "PROD"));
        ObjectNode profile = element("ImplementationProfile", "profile-review",
                "Implementation Profile Review");
        profile.put("primaryLanguage", "CUSTOM");
        profile.put("packageManager", "NONE");
        profile.put("generatedByTransformation", true);
        pim.set("implementationProfile", profile);

        Map<String, String> schemaBySourceId = new LinkedHashMap<>();
        for (JsonNode item : concat(elements(cim, "informationItems"), elements(cim, "entities"),
                elements(cim, "valueObjects"))) {
            ObjectNode schema = schemaFor(item, "VALUE_OBJECT");
            schemas.add(schema);
            schemaBySourceId.put(idOf(item, schema.path("id").asText()),
                    schema.path("id").asText());
        }

        ObjectNode api = element("Api", "api-" + slug(idOf(cim, source.id())), "Generated API");
        api.put("apiStyle", "RESOURCE_ORIENTED_HTTP");
        ArrayNode routes = api.putArray("routes");
        boolean hasRoutes = false;

        for (JsonNode command : elements(cim, "commands")) {
            ObjectNode request = schemaForBehavior(command, "schema-req-", "REQUEST");
            ObjectNode response = schemaForBehavior(command, "schema-res-", "RESPONSE");
            schemas.add(request);
            schemas.add(response);
            ObjectNode fn = functionFor(command, "fn-cmd-", "Command Handler",
                    "COMMAND_HANDLER");
            fn.put("writesState", true);
            fn.put("readsState", false);
            fn.set("contract", contractFor(fn, request, response));
            functions.add(fn);
            routes.add(routeFor(command, fn, request, response, "POST"));
            hasRoutes = true;
        }
        for (JsonNode query : elements(cim, "queries")) {
            ObjectNode request = schemaForBehavior(query, "schema-query-req-", "REQUEST");
            ObjectNode response = schemaForBehavior(query, "schema-query-res-", "RESPONSE");
            schemas.add(request);
            schemas.add(response);
            ObjectNode fn = functionFor(query, "fn-query-", "Query Handler", "QUERY_HANDLER");
            fn.put("writesState", false);
            fn.put("readsState", true);
            fn.set("contract", contractFor(fn, request, response));
            functions.add(fn);
            routes.add(routeFor(query, fn, request, response, "GET"));
            hasRoutes = true;
        }
        if (hasRoutes) {
            apis.add(api);
        }

        ObjectNode bus = element("EventBus", "eventbus-domain", "Domain Event Bus");
        bus.put("channelKind", "EVENT_BUS");
        bus.put("orderingRequirement", "NONE");
        bus.put("deliverySemantics", "AT_LEAST_ONCE");
        bus.put("encrypted", true);
        ArrayNode busEventTypes = bus.putArray("eventTypes");
        for (JsonNode event : elements(cim, "events")) {
            ObjectNode schema = schemaForBehavior(event, "schema-event-", "EVENT");
            schemas.add(schema);
            ObjectNode eventType = element("EventType", "eventtype-" + slug(idOf(event,
                    text(event, "name", "event"))), text(event, "name", "Domain Event"));
            eventType.put("semanticName", eventType.path("name").asText());
            eventType.put("schema", schema.path("id").asText());
            eventType.set("envelope", eventEnvelope(eventType));
            eventType.put("generatedFrom", idOf(event, ""));
            eventType.put("generatedByTransformation", true);
            eventTypes.add(eventType);
            busEventTypes.add(eventType.path("id").asText());
        }
        if (!busEventTypes.isEmpty()) {
            channels.add(bus);
        }

        for (JsonNode aggregate : concat(elements(cim, "aggregates"), elements(cim, "entities"))) {
            ObjectNode storeNode = dataStoreFor(aggregate, schemaBySourceId);
            dataStores.add(storeNode);
        }

        for (JsonNode process : elements(cim, "processes")) {
            workflows.add(workflowFor(process));
        }
        for (JsonNode actor : elements(cim, "actors")) {
            if ("ExternalSystem".equals(typeOf(actor))) {
                externalAdapters.add(externalAdapterFor(actor));
            }
        }
        for (JsonNode requirement : elements(cim, "requirements")) {
            policies.add(policyFor(requirement));
        }

        for (int i = 0; i < serviceIds.size(); i++) {
            ObjectNode unit = element("DeploymentUnit", "du-" + slug(serviceIds.get(i)),
                    services.get(i).path("name").asText() + " Deployment Unit");
            unit.put("unitType", "SERVICE");
            unit.put("lifecycle", "application");
            unit.put("independentlyDeployable", true);
            unit.putArray("services").add(serviceIds.get(i));
            ArrayNode contains = unit.putArray("contains");
            functions.forEach(fn -> contains.add(fn.path("id").asText()));
            apis.forEach(item -> contains.add(item.path("id").asText()));
            workflows.forEach(item -> contains.add(item.path("id").asText()));
            dataStores.forEach(item -> contains.add(item.path("id").asText()));
            unit.putArray("targetEnvironments").add("env-dev").add("env-test").add("env-prod");
            unit.put("generatedByTransformation", true);
            deploymentUnits.add(unit);
        }

        pim.set("traceModel", traceModel(cim, pim));
        pim.set("readiness", readiness(cim));
        attachGraph(pim);
        return pim;
    }

    private List<JsonNode> sourceServices(JsonNode cim) {
        List<JsonNode> contexts = elements(cim, "boundedContexts");
        if (!contexts.isEmpty()) {
            return contexts;
        }
        List<JsonNode> capabilities = elements(cim, "capabilities");
        if (!capabilities.isEmpty()) {
            return capabilities;
        }
        return List.of();
    }

    private ObjectNode element(String eClass, String id, String name) {
        ObjectNode node = store.objectMapper().createObjectNode();
        node.put("eClass", eClass);
        node.put("id", slug(id));
        node.put("name", name == null || name.isBlank() ? eClass : name);
        return node;
    }

    private ObjectNode environment(String id, String name, String environmentClass) {
        ObjectNode env = element("Environment", id, name);
        env.put("environmentClass", environmentClass);
        env.put("generatedByTransformation", true);
        return env;
    }

    private ObjectNode functionFor(JsonNode source, String prefix, String suffix,
            String functionKind) {
        ObjectNode fn = element("Function", prefix + idOf(source, text(source, "name", suffix)),
                text(source, "name", "Generated") + " " + suffix);
        fn.put("functionKind", functionKind);
        fn.put("executionModel", "REQUEST_RESPONSE");
        fn.put("computeProfile", "LIGHTWEIGHT");
        fn.put("stateless", true);
        fn.put("publicEntryPoint", true);
        fn.put("handlerResponsibility", firstNonBlank(text(source, "intent", ""),
                text(source, "description", ""), text(source, "summary", ""),
                "Handle " + text(source, "name", "behavior") + "."));
        fn.put("generatedFrom", idOf(source, ""));
        fn.put("generatedByTransformation", true);
        fn.put("reviewStatus", "review-required");
        return fn;
    }

    private ObjectNode contractFor(ObjectNode function, ObjectNode request, ObjectNode response) {
        ObjectNode contract = element("FunctionContract",
                function.path("id").asText() + "-contract",
                function.path("name").asText() + " Contract");
        contract.put("inputSchema", request.path("id").asText());
        contract.put("outputSchema", response.path("id").asText());
        contract.put("generatedByTransformation", true);
        return contract;
    }

    private ObjectNode routeFor(JsonNode source, ObjectNode function, ObjectNode request,
            ObjectNode response, String method) {
        ObjectNode route = element("ApiRoute", "route-" + idOf(source,
                function.path("id").asText()), text(source, "name", "Generated") + " Route");
        route.put("method", method);
        route.put("pathTemplate", "/" + pluralResourceName(source) + "/"
                + slug(text(source, "name", "operation")).replace("-", ""));
        route.put("operationId", camel(text(source, "name", "operation")));
        route.put("publicRoute", true);
        route.put("authRequired", true);
        route.put("requestValidationRequired", true);
        route.put("responseValidationRequired", true);
        route.put("requestSchema", request.path("id").asText());
        route.put("responseSchema", response.path("id").asText());
        route.put("functionIntegration", function.path("id").asText());
        route.put("generatedFrom", idOf(source, ""));
        route.put("generatedByTransformation", true);
        return route;
    }

    private ObjectNode schemaFor(JsonNode source, String schemaKind) {
        ObjectNode schema = element("Schema", "schema-" + idOf(source, text(source, "name",
                schemaKind)), text(source, "name", "Generated Schema") + " Schema");
        schema.put("schemaKind", schemaKind);
        schema.put("compatibility", "NONE");
        schema.put("generatedFrom", idOf(source, ""));
        schema.put("generatedByTransformation", true);
        ArrayNode fields = schema.putArray("fields");
        if (source.has("attributes") && source.path("attributes").isArray()) {
            source.path("attributes").forEach(attribute -> fields.add(schemaField(attribute)));
        }
        if (fields.isEmpty()) {
            fields.add(field("reference", "STRING", true));
            fields.add(field("description", "STRING", false));
        }
        return schema;
    }

    private ObjectNode schemaForBehavior(JsonNode source, String prefix, String schemaKind) {
        ObjectNode schema = element("Schema", prefix + idOf(source, text(source, "name",
                schemaKind)), text(source, "name", "Generated") + " " + schemaKind + " Schema");
        schema.put("schemaKind", schemaKind);
        schema.put("compatibility", "NONE");
        schema.put("generatedFrom", idOf(source, ""));
        schema.put("generatedByTransformation", true);
        ArrayNode fields = schema.putArray("fields");
        fields.add(field("correlationId", "STRING", true));
        fields.add(field("sourceElementId", "STRING", false));
        return schema;
    }

    private ObjectNode schemaField(JsonNode source) {
        String name = text(source, "name", text(source, "attributeName", "field"));
        return field(name, fieldType(text(source, "fieldType", text(source, "type", ""))),
                !source.path("optional").asBoolean(false));
    }

    private ObjectNode field(String name, String fieldType, boolean required) {
        ObjectNode field = element("SchemaField", "field-" + slug(name) + "-" + fieldType, name);
        field.put("fieldType", fieldType);
        field.put("required", required);
        return field;
    }

    private ObjectNode eventEnvelope(ObjectNode eventType) {
        ObjectNode envelope = element("EventEnvelope", eventType.path("id").asText() + "-envelope",
                eventType.path("name").asText() + " Envelope");
        envelope.put("correlationIdField", "correlationId");
        envelope.put("eventIdField", "eventId");
        envelope.put("eventTypeField", "eventType");
        envelope.put("timestampField", "time");
        envelope.put("payloadField", "payload");
        return envelope;
    }

    private ObjectNode dataStoreFor(JsonNode source, Map<String, String> schemaBySourceId) {
        ObjectNode storeNode = element("DataStore", "store-" + idOf(source, text(source, "name",
                "data")), text(source, "name", "Generated") + " Store");
        storeNode.put("storeKind", "DOCUMENT");
        storeNode.put("consistencyNeed", "EVENTUAL");
        storeNode.put("persistent", true);
        storeNode.put("encrypted", true);
        storeNode.put("generatedFrom", idOf(source, ""));
        storeNode.put("generatedByTransformation", true);
        ArrayNode models = storeNode.putArray("ownedDataModels");
        ObjectNode model = element("DataModel", storeNode.path("id").asText() + "-model",
                storeNode.path("name").asText() + " Model");
        model.put("dataModelKind", "DOCUMENT");
        model.put("schema", schemaBySourceId.getOrDefault(idOf(source, ""), ""));
        model.putArray("storageFields").add(field("id", "STRING", true));
        models.add(model);
        storeNode.putArray("accessPatterns").add(accessPattern(storeNode));
        return storeNode;
    }

    private ObjectNode accessPattern(ObjectNode storeNode) {
        ObjectNode pattern = element("AccessPattern", storeNode.path("id").asText() + "-by-id",
                "Lookup by id");
        pattern.put("queryShape", "GET_BY_ID");
        pattern.put("readOnly", true);
        return pattern;
    }

    private ObjectNode workflowFor(JsonNode source) {
        ObjectNode workflow = element("Workflow", "workflow-" + idOf(source, text(source, "name",
                "process")), text(source, "name", "Generated Process") + " Workflow");
        workflow.put("workflowKind", "STANDARD");
        workflow.put("stateful", true);
        workflow.put("longRunning", true);
        workflow.put("generatedFrom", idOf(source, ""));
        workflow.put("generatedByTransformation", true);
        ArrayNode states = workflow.putArray("states");
        ObjectNode start = workflowState(workflow, "start", "Start", "PASS", false);
        ObjectNode end = workflowState(workflow, "end", "End", "SUCCESS", true);
        states.add(start);
        states.add(end);
        workflow.put("startState", start.path("id").asText());
        workflow.putArray("endStates").add(end.path("id").asText());
        return workflow;
    }

    private ObjectNode workflowState(ObjectNode workflow, String suffix, String name, String kind,
            boolean terminal) {
        ObjectNode state = element("WorkflowState", workflow.path("id").asText() + "-" + suffix,
                name);
        state.put("stateKind", kind);
        state.put("terminal", terminal);
        return state;
    }

    private ObjectNode externalAdapterFor(JsonNode source) {
        ObjectNode adapter = element("ExternalAdapter", "adapter-" + idOf(source, text(source,
                "name", "external")), text(source, "name", "External System") + " Adapter");
        adapter.put("protocol", "HTTPS");
        adapter.put("integrationPurpose", "Integrate with " + adapter.path("name").asText() + ".");
        adapter.put("generatedFrom", idOf(source, ""));
        adapter.put("generatedByTransformation", true);
        return adapter;
    }

    private ObjectNode policyFor(JsonNode source) {
        ObjectNode policy = element("CompliancePolicy", "policy-" + idOf(source, text(source,
                "name", "requirement")), text(source, "name", "Requirement") + " Policy");
        policy.put("policyStatement", firstNonBlank(text(source, "statement", ""),
                text(source, "description", ""), text(source, "summary", ""),
                policy.path("name").asText()));
        policy.put("generatedFrom", idOf(source, ""));
        policy.put("generatedByTransformation", true);
        return policy;
    }

    private ObjectNode traceModel(JsonNode cim, ObjectNode pim) {
        ObjectNode trace = element("TraceModel", "trace-" + pim.path("id").asText(),
                "CIM to PIM Trace");
        ArrayNode links = trace.putArray("links");
        addTraceLinks(links, elements(pim, "services"), "TR-FE-SERVICE");
        addTraceLinks(links, elements(pim, "functions"), "TR-FE-BEHAVIOR");
        addTraceLinks(links, elements(pim, "eventTypes"), "TR-FE-EVENT");
        addTraceLinks(links, elements(pim, "dataStores"), "TR-FE-DATA");
        trace.put("sourceElementId", idOf(cim, ""));
        trace.put("targetElementId", pim.path("id").asText());
        return trace;
    }

    private void addTraceLinks(ArrayNode links, List<JsonNode> targets, String rule) {
        for (JsonNode target : targets) {
            String sourceId = text(target, "generatedFrom", "");
            if (sourceId.isBlank()) {
                continue;
            }
            ObjectNode link = element("TraceLink", "trace-" + rule + "-" + sourceId + "-"
                    + target.path("id").asText(), rule + " " + sourceId + " -> "
                    + target.path("name").asText());
            link.put("linkType", "TRANSFORMS_TO");
            link.put("transformationRule", rule);
            link.put("sourceElementId", sourceId);
            link.put("targetElementId", target.path("id").asText());
            link.put("target", target.path("id").asText());
            links.add(link);
        }
    }

    private ObjectNode readiness(JsonNode cim) {
        ObjectNode readiness = element("ProductionReadinessAssessment", "readiness-pim",
                "PIM readiness assessment");
        readiness.put("readinessStatus", "REVIEW_REQUIRED");
        readiness.put("assessedBy", "CIM-to-PIM frontend transformation");
        readiness.put("transformationReady", false);
        readiness.put("deploymentReady", false);
        readiness.put("productionReady", false);
        readiness.putArray("checks");
        ArrayNode findings = readiness.putArray("findings");
        for (JsonNode risk : elements(cim, "risks")) {
            ObjectNode finding = element("ReadinessFinding", "finding-" + idOf(risk, text(risk,
                    "name", "risk")), "Risk - " + text(risk, "name", "Risk"));
            finding.put("findingType", risk.path("productionBlocking").asBoolean(false)
                    ? "BLOCKING_RISK" : "OTHER");
            finding.put("severity", "WARNING");
            finding.put("message", firstNonBlank(text(risk, "riskStatement", ""),
                    text(risk, "description", ""), finding.path("name").asText()));
            finding.put("recommendation", text(risk, "mitigation", "Review before promotion."));
            finding.put("blocking", risk.path("productionBlocking").asBoolean(false));
            findings.add(finding);
        }
        ArrayNode decisions = readiness.putArray("manualDecisions");
        for (JsonNode hotspot : elements(cim, "hotspots")) {
            ObjectNode decision = element("ManualDecision", "decision-" + idOf(hotspot, text(
                    hotspot, "name", "hotspot")), "Hotspot - " + text(hotspot, "name",
                    "Hotspot"));
            decision.put("question", text(hotspot, "question", "Review transformation hotspot."));
            decision.put("severity", "WARNING");
            decision.put("blocking", hotspot.path("blocksTransformation").asBoolean(false)
                    || hotspot.path("blocksProduction").asBoolean(false));
            decisions.add(decision);
        }
        return readiness;
    }

    private void attachGraph(ObjectNode model) {
        ObjectNode graph = store.objectMapper().createObjectNode();
        ArrayNode elements = graph.putArray("elements");
        ArrayNode relationships = graph.putArray("relationships");
        collectGraph(model, elements, relationships, Set.of("graph", "diagram"));
        graph.putArray("traceLinks");
        graph.putArray("assumptions");
        graph.putArray("validationIssues");
        graph.putArray("manualBacklog");
        model.set("graph", graph);
        ObjectNode diagram = store.objectMapper().createObjectNode();
        diagram.set("elements", elements.deepCopy());
        diagram.set("relationships", relationships.deepCopy());
        model.set("diagram", diagram);
    }

    private void collectGraph(JsonNode node, ArrayNode elements, ArrayNode relationships,
            Set<String> skippedFields) {
        if (!node.isObject()) {
            return;
        }
        String id = text(node, "id", "");
        String eClass = typeOf(node);
        if (!id.isBlank() && !eClass.isBlank() && !"SchemaField".equals(eClass)
                && !"FunctionContract".equals(eClass) && !"EventEnvelope".equals(eClass)) {
            ObjectNode graphNode = store.objectMapper().createObjectNode();
            graphNode.put("id", id);
            graphNode.put("eClass", eClass);
            graphNode.put("type", eClass);
            graphNode.put("label", text(node, "name", id));
            elements.add(graphNode);
        }
        node.fields().forEachRemaining(entry -> {
            if (skippedFields.contains(entry.getKey())) {
                return;
            }
            JsonNode value = entry.getValue();
            if (value.isArray()) {
                value.forEach(child -> collectGraph(child, elements, relationships, skippedFields));
            } else if (value.isObject()) {
                collectGraph(value, elements, relationships, skippedFields);
            }
        });
    }

    private List<JsonNode> elements(JsonNode model, String field) {
        JsonNode value = model == null ? null : model.path(field);
        if (value == null || !value.isArray()) {
            return List.of();
        }
        List<JsonNode> result = new ArrayList<>();
        value.forEach(result::add);
        return result;
    }

    private List<JsonNode> concat(List<JsonNode> first, List<JsonNode> second) {
        List<JsonNode> result = new ArrayList<>(first);
        result.addAll(second);
        return result;
    }

    private List<JsonNode> concat(List<JsonNode> first, List<JsonNode> second,
            List<JsonNode> third) {
        List<JsonNode> result = concat(first, second);
        result.addAll(third);
        return result;
    }

    private String architectureStyle(JsonNode cim) {
        String targetStyle = cim.path("transformationProfile").path("targetStyle").asText("");
        if (targetStyle.toUpperCase(Locale.ROOT).contains("EVENT")) {
            return "EVENT_DRIVEN_SERVERLESS";
        }
        if (targetStyle.toUpperCase(Locale.ROOT).contains("API")) {
            return "API_FIRST_SERVERLESS";
        }
        if (!elements(cim, "processes").isEmpty()) {
            return "WORKFLOW_ORCHESTRATED_SERVERLESS";
        }
        return "HYBRID_SERVERLESS";
    }

    private String fieldType(String value) {
        String normalized = String.valueOf(value).toUpperCase(Locale.ROOT);
        if (normalized.contains("DATE")) {
            return "DATETIME";
        }
        if (normalized.contains("BOOL")) {
            return "BOOLEAN";
        }
        if (normalized.contains("INT") || normalized.contains("DECIMAL")
                || normalized.contains("NUMBER")) {
            return "NUMBER";
        }
        if (normalized.contains("ARRAY") || normalized.contains("LIST")) {
            return "ARRAY";
        }
        if (normalized.contains("OBJECT")) {
            return "OBJECT";
        }
        return "STRING";
    }

    private String pluralResourceName(JsonNode source) {
        String name = text(source, "name", "resources");
        String slug = slug(name);
        return slug.endsWith("s") ? slug : slug + "s";
    }

    private String idOf(JsonNode node, String fallback) {
        return firstNonBlank(text(node, "id", ""), text(node, "elementId", ""), fallback);
    }

    private String typeOf(JsonNode node) {
        return text(node, "eClass", text(node, "type", ""));
    }

    private String text(JsonNode node, String field, String fallback) {
        JsonNode value = node == null ? null : node.get(field);
        if (value == null || value.isNull()) {
            return fallback;
        }
        String text = value.asText("");
        return text.isBlank() ? fallback : text;
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    private String slug(String value) {
        String normalized = String.valueOf(value == null ? "" : value).trim().toLowerCase(
                Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("^-+|-+$", "");
        return normalized.isBlank() ? "generated" : normalized;
    }

    private String camel(String value) {
        String[] parts = slug(value).split("-");
        if (parts.length == 0) {
            return "operation";
        }
        StringBuilder result = new StringBuilder(parts[0]);
        for (int i = 1; i < parts.length; i++) {
            if (!parts[i].isBlank()) {
                result.append(parts[i].substring(0, 1).toUpperCase(Locale.ROOT))
                        .append(parts[i].substring(1));
            }
        }
        return result.toString();
    }

    private void mirrorReadinessToManualBacklog(ObjectNode model) {
        ArrayNode backlog = store.objectMapper().createArrayNode();
        JsonNode readiness = model.path("readiness");
        appendManualDecisionTasks(backlog, readiness.path("manualDecisions"));
        appendFindingTasks(backlog, readiness.path("findings"));
        appendFailedCheckTasks(backlog, readiness.path("checks"));
        if (backlog.isEmpty()) {
            backlog.add(manualTask("review-generated-pim",
                    "Review generated PIM responsibilities and integration contracts.",
                    "Semi-automated transformations require human review before promotion.",
                    true, "MANUAL_MODELING", "OPEN"));
        }
        model.set("manualBacklog", backlog);
        ObjectNode graph = model.withObject("graph");
        graph.set("manualBacklog", backlog.deepCopy());
    }

    private void appendManualDecisionTasks(ArrayNode backlog, JsonNode decisions) {
        if (!decisions.isArray()) {
            return;
        }
        decisions.forEach(decision -> backlog.add(manualTask(
                "decision-" + text(decision, "id", UUID.randomUUID().toString()),
                text(decision, "name", "Manual transformation decision"),
                firstNonBlank(text(decision, "question", ""), text(decision, "rationale", ""),
                        "Review the generated model decision before promotion."),
                decision.path("blocking").asBoolean(true),
                "ETL_MANUAL_DECISION",
                "OPEN")));
    }

    private void appendFindingTasks(ArrayNode backlog, JsonNode findings) {
        if (!findings.isArray()) {
            return;
        }
        findings.forEach(finding -> {
            boolean required = finding.path("blocking").asBoolean(false)
                    || isAtLeastWarning(text(finding, "severity", ""));
            if (!required) {
                return;
            }
            backlog.add(manualTask(
                    "finding-" + text(finding, "id", UUID.randomUUID().toString()),
                    text(finding, "name", "Readiness finding"),
                    firstNonBlank(text(finding, "recommendation", ""), text(finding, "message", ""),
                            "Review the readiness finding before promotion."),
                    finding.path("blocking").asBoolean(false),
                    "ETL_READINESS_FINDING",
                    "OPEN"));
        });
    }

    private void appendFailedCheckTasks(ArrayNode backlog, JsonNode checks) {
        if (!checks.isArray()) {
            return;
        }
        checks.forEach(check -> {
            if (check.path("passed").asBoolean(true)) {
                return;
            }
            backlog.add(manualTask(
                    "check-" + text(check, "id", UUID.randomUUID().toString()),
                    text(check, "name", text(check, "checkId", "Readiness check")),
                    firstNonBlank(text(check, "remediation", ""), text(check, "message", ""),
                            "Resolve the failed readiness check."),
                    isAtLeastWarning(text(check, "severity", "")),
                    "ETL_READINESS_CHECK",
                    "OPEN"));
        });
    }

    private boolean isAtLeastWarning(String severity) {
        String normalized = String.valueOf(severity).toUpperCase(Locale.ROOT);
        return "WARNING".equals(normalized) || "ERROR".equals(normalized)
                || "CRITICAL".equals(normalized) || "BLOCKER".equals(normalized);
    }

    private ObjectNode manualTask(String id, String title, String rationale, boolean required,
            String category, String status) {
        ObjectNode task = store.objectMapper().createObjectNode();
        task.put("id", slug(id));
        task.put("name", title);
        task.put("title", title);
        task.put("status", status);
        task.put("required", required);
        task.put("category", category);
        task.put("rationale", rationale);
        return task;
    }

    private void addManualBacklog(ObjectNode model, String title) {
        model.withArray("manualBacklog").addObject()
                .put("id", "review-" + System.currentTimeMillis())
                .put("name", title)
                .put("status", "OPEN")
                .put("required", true)
                .put("category", "MANUAL_MODELING")
                .put("rationale",
                        "Semi-automated transformations require human review before promotion.");
    }

    private String pretty(JsonNode node) {
        try {
            return store.objectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(node);
        } catch (Exception ex) {
            throw new PlatformException(500, "Could not serialize model.");
        }
    }

    private String sanitize(String value) {
        return String.valueOf(value == null ? "artifact" : value)
                .replaceAll("[^A-Za-z0-9_.-]+", "-");
    }
}
