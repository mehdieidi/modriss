package io.mehdieidi.modless.mde.etl;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import io.mehdieidi.modless.mde.validation.EpsilonEvlValidator;
import io.mehdieidi.modless.mde.validation.EvlValidationReport;
import io.mehdieidi.modless.mde.validation.EvlValidationRequest;
import io.mehdieidi.modless.mde.validation.EvlValidationStatus;
import io.mehdieidi.modless.mde.validation.FileEvlModelConfiguration;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EClassifier;
import org.eclipse.emf.ecore.EEnum;
import org.eclipse.emf.ecore.EFactory;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.EStructuralFeature;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.eclipse.emf.ecore.xmi.impl.EcoreResourceFactoryImpl;
import org.eclipse.emf.ecore.xmi.impl.XMIResourceFactoryImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class CimToPimEtlRegressionTest {

    private static final Path REPOSITORY_ROOT = findRepositoryRoot();

    @TempDir
    Path tempDir;

    private static Path findRepositoryRoot() {
        Path current = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        while (current != null) {
            if (Files.isDirectory(current.resolve("mde/metamodels"))
                    && Files.isDirectory(current.resolve("mde/transformations"))) {
                return current;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("Could not locate repository root from user.dir.");
    }

    @Test
    void executesCimToPimTransformationForRepresentativeBusinessModel() throws Exception {
        Path cimMetamodel = REPOSITORY_ROOT.resolve("mde/metamodels/cim/cim-combined.ecore");
        Path pimMetamodel = REPOSITORY_ROOT.resolve("mde/metamodels/pim/pim-combined.ecore");
        Path cimModel = tempDir.resolve("order-cim.xmi");
        Path pimModel = tempDir.resolve("order-pim.xmi");

        createRepresentativeCimModel(cimMetamodel, cimModel);

        EtlExecutionRequest request = new EtlExecutionRequest(
                REPOSITORY_ROOT.resolve("mde/transformations/cim-to-pim/cim-to-pim.etl"),
                REPOSITORY_ROOT.resolve("mde/transformations/cim-to-pim"),
                List.of(
                        EtlModelConfiguration.source("CIM", CimToPimDefaults.SOURCE_ALIASES,
                                cimModel, List.of(cimMetamodel)),
                        EtlModelConfiguration.target("PIM", CimToPimDefaults.TARGET_ALIASES,
                                pimModel, List.of(pimMetamodel), false)),
                true,
                true);

        EtlExecutionReport report = executeOrFail(request);

        assertEquals(EtlExecutionStatus.SUCCEEDED, report.status(),
                report.diagnostics().toString());
        assertTrue(Files.isRegularFile(pimModel), "The transformation should persist a PIM model.");

        Resource pimResource = loadModel(pimMetamodel, pimModel);
        EObject root = pimResource.getContents().get(0);
        assertEquals("PIMModel", root.eClass().getName());
        assertFalse(values(root, "services").isEmpty(), "Expected generated services.");
        assertFalse(values(root, "functions").isEmpty(), "Expected command/query functions.");
        assertFalse(values(root, "apis").isEmpty(), "Expected an API for user-facing behavior.");
        assertFalse(values(root, "eventTypes").isEmpty(), "Expected business event types.");
        assertFalse(values(root, "dataStores").isEmpty(), "Expected aggregate data stores.");
        assertFalse(values(root, "deploymentUnits").isEmpty(), "Expected deployment units.");

        EObject readiness = reference(root, "readiness");
        assertTrue(readiness != null, "Expected readiness assessment.");
        assertFalse(values(readiness, "manualDecisions").isEmpty(),
                "Runtime/language review decision should be generated.");
    }

    @Test
    void transformsClimateReliefSampleThroughDefaultProfileWithSpecCompletenessShape()
            throws Exception {
        Path pimMetamodel = REPOSITORY_ROOT.resolve("mde/metamodels/pim/pim-combined.ecore");
        Path sampleModel = REPOSITORY_ROOT.resolve(
                "mde/samples/climate-relief-grants-cim-sample.xmi");
        Path pimModel = tempDir.resolve("climate-relief-grants-pim.xmi");

        EtlExecutionReport report = executeOrFail(CimToPimDefaults.request(
                REPOSITORY_ROOT, sampleModel, pimModel, true, true));

        assertEquals(EtlExecutionStatus.SUCCEEDED, report.status(),
                report.diagnostics().toString());
        assertTrue(Files.isRegularFile(pimModel),
                "The default profile should persist a PIM model.");

        EObject root = loadModel(pimMetamodel, pimModel).getContents().get(0);
        assertEquals("PIMModel", root.eClass().getName());
        assertEquals(2, values(root, "services").size(),
                "The sample's two bounded contexts should become two services.");
        assertEquals(3, values(root, "environments").size(),
                "The default dev/test/prod environments should be generated.");
        assertTrue(reference(root, "implementationProfile") != null,
                "Expected an implementation profile review artifact.");
        assertTrue(reference(root, "traceModel") != null, "Expected a trace model.");
        assertFalse(values(reference(root, "traceModel"), "links").isEmpty(),
                "Expected generated trace links.");

        assertFalse(values(root, "functions").isEmpty(), "Expected generated functions.");
        assertTrue(values(root, "functions").stream()
                        .allMatch(function -> reference(function, "contract") != null),
                "Every generated function should have a function contract.");
        assertFalse(values(root, "apis").isEmpty(), "Expected generated APIs.");
        assertTrue(values(root, "apis").stream().allMatch(api -> !values(api, "routes").isEmpty()),
                "Every generated API should have at least one route.");
        assertFalse(values(root, "eventTypes").isEmpty(), "Expected generated event types.");
        assertTrue(values(root, "eventTypes").stream()
                        .allMatch(eventType -> reference(eventType, "schema") != null
                                && reference(eventType, "envelope") != null),
                "Every generated event type should have a schema and envelope.");
        assertFalse(values(root, "channels").isEmpty(), "Expected generated channels.");
        assertFalse(values(root, "dataStores").isEmpty(), "Expected generated data stores.");
        assertTrue(values(root, "dataStores").stream()
                        .allMatch(store -> !values(store, "ownedDataModels").isEmpty()
                                && !values(store, "accessPatterns").isEmpty()),
                "Every generated data store should have data models and access patterns.");
        assertFalse(values(root, "workflows").isEmpty(), "Expected generated workflows.");
        assertFalse(values(root, "externalAdapters").isEmpty(),
                "Expected generated external adapters.");
        assertFalse(values(root, "policies").isEmpty(),
                "Expected generated governance, security, resilience, and data policies.");
        assertTrue(values(root, "deploymentUnits").stream()
                        .allMatch(unit -> !values(unit, "contains").isEmpty()),
                "Every deployment unit should contain deployable elements.");

        EObject readiness = reference(root, "readiness");
        assertTrue(readiness != null, "Expected readiness assessment.");
        assertFalse(values(readiness, "manualDecisions").isEmpty(),
                "The sample's open transformation decisions should remain visible.");
        assertFalse(values(readiness, "findings").isEmpty(),
                "The sample's risks and assumptions should become readiness findings.");
        String persisted = Files.readString(pimModel);
        assertFalse(persisted.contains("https://modless.org/cim/"),
                "Generated PIM XMI must be importable with only the PIM metamodel registered.");

        EvlValidationReport validation = new EpsilonEvlValidator().validate(
                EvlValidationRequest.forRoot(
                        REPOSITORY_ROOT.resolve("mde/validation/pim/pim-semantic-validation.evl"),
                        List.of(FileEvlModelConfiguration.readOnly(
                                "PIM",
                                List.of("KERNEL"),
                                pimModel,
                                List.of(pimMetamodel))),
                        true));
        assertEquals(EvlValidationStatus.SUCCEEDED, validation.status(),
                validation.diagnostics().toString());
        assertTrue(validation.violations().isEmpty(),
                "Generated climate-relief PIM should pass semantic validation: "
                        + validation.violations());
    }

    @Test
    void coversCrossBoundaryRelationshipAndReviewBacklogRules() throws Exception {
        Path cimMetamodel = REPOSITORY_ROOT.resolve("mde/metamodels/cim/cim-combined.ecore");
        Path pimMetamodel = REPOSITORY_ROOT.resolve("mde/metamodels/pim/pim-combined.ecore");
        Path cimModel = tempDir.resolve("coverage-cim.xmi");
        Path pimModel = tempDir.resolve("coverage-pim.xmi");

        createCoverageCimModel(cimMetamodel, cimModel);

        EtlExecutionRequest request = new EtlExecutionRequest(
                REPOSITORY_ROOT.resolve("mde/transformations/cim-to-pim/cim-to-pim.etl"),
                REPOSITORY_ROOT.resolve("mde/transformations/cim-to-pim"),
                List.of(
                        EtlModelConfiguration.source("CIM", CimToPimDefaults.SOURCE_ALIASES,
                                cimModel, List.of(cimMetamodel)),
                        EtlModelConfiguration.target("PIM", CimToPimDefaults.TARGET_ALIASES,
                                pimModel, List.of(pimMetamodel), false)),
                true,
                true);

        executeOrFail(request);

        EObject root = loadModel(pimMetamodel, pimModel).getContents().get(0);
        assertTrue(values(root, "channels").stream()
                        .anyMatch(channel -> "Queue".equals(channel.eClass().getName())),
                "Cross-service capability dependency should create a provider-independent queue.");
        assertTrue(values(root, "flows").stream()
                        .anyMatch(flow -> "MessageFlow".equals(flow.eClass().getName())),
                "Cross-service capability dependency should create message flows.");
        assertTrue(values(root, "policies").stream()
                        .anyMatch(policy -> "AuthorizationPolicy".equals(policy.eClass().getName())
                                && "Check Product Authorization".equals(get(policy, "name"))),
                "Protected queries should receive authorization policy coverage.");
        assertTrue(values(root, "schemas").stream()
                        .flatMap(schema -> values(schema, "fields").stream())
                        .anyMatch(field -> !values(field, "enumValues").isEmpty()),
                "Enumeration information items should become schema enum literals.");
        assertTrue(values(root, "dataStores").stream()
                        .flatMap(store -> values(store, "ownedDataModels").stream())
                        .flatMap(model -> values(model, "storageFields").stream())
                        .anyMatch(field -> "inventory".equals(get(field, "name"))),
                "Domain relationships should be represented in data model repository fields.");
        assertTrue(values(root, "flows").stream()
                        .anyMatch(flow -> "ExternalIntegrationFlow".equals(flow.eClass().getName())),
                "External process steps should create external integration flows.");
        assertTrue(values(root, "policies").stream()
                        .anyMatch(policy -> "ConcurrencyPolicy".equals(policy.eClass().getName())),
                "Performance/scalability NFRs should create concurrency policies.");
        assertTrue(values(root, "policies").stream()
                        .anyMatch(policy -> "RateLimitPolicy".equals(policy.eClass().getName())),
                "Performance/scalability NFRs should create rate limit policies.");
        assertTrue(values(root, "policies").stream()
                        .anyMatch(policy -> "CachePolicy".equals(policy.eClass().getName())),
                "Performance/scalability NFRs should create cache policies.");
        assertTrue(values(root, "policies").stream()
                        .anyMatch(policy -> "TimeoutPolicy".equals(policy.eClass().getName())
                                && Integer.valueOf(300).equals(get(policy, "timeoutSeconds"))),
                "Parseable temporal constraints should set timeout seconds.");
        assertTrue(values(root, "dataStores").stream()
                        .anyMatch(store -> !values(store, "dataProtectionPolicies").isEmpty()),
                "Classified data in stores should receive data protection policy attachment.");

        EObject readiness = reference(root, "readiness");
        assertTrue(values(readiness, "manualDecisions").stream()
                        .anyMatch(decision -> get(decision, "question").toString()
                                .contains("critical capability dependency")),
                "Critical capability dependencies should remain an explicit manual review item.");
        assertTrue(values(readiness, "manualDecisions").stream()
                        .anyMatch(decision -> get(decision, "question").toString()
                                .contains("Confirm authorization policy")),
                "Missing query authorization rule should remain an explicit manual decision.");
    }

    @Test
    void reportsParseDiagnosticsForBrokenEtl() throws IOException {
        Path broken = tempDir.resolve("broken.etl");
        Files.writeString(broken,
                "rule Broken transform x : Missing!Type to y : Missing!Type { if ( }");
        EtlExecutionRequest request = new EtlExecutionRequest(
                broken,
                tempDir,
                List.of(EtlModelConfiguration.source(
                        "IN",
                        List.of("IN"),
                        tempDir.resolve("missing.xmi"),
                        List.of(tempDir.resolve("missing.ecore")))),
                false,
                true);

        EtlExecutionException exception = assertDoesNotThrow(() -> {
            try {
                new EpsilonEtlExecutor().execute(request);
            } catch (EtlExecutionException ex) {
                return ex;
            }
            throw new AssertionError("Expected ETL execution to fail.");
        });

        assertTrue(exception.getReport().diagnostics().stream()
                .anyMatch(d -> d.phase() == ExecutionPhase.VALIDATION
                        || d.phase() == ExecutionPhase.PARSE));
    }

    private EtlExecutionReport executeOrFail(EtlExecutionRequest request) {
        try {
            return new EpsilonEtlExecutor().execute(request);
        } catch (EtlExecutionException ex) {
            fail("ETL execution failed: " + ex.getReport().diagnostics());
            throw new AssertionError(ex);
        }
    }

    private void createRepresentativeCimModel(Path metamodel, Path modelFile) throws IOException {
        ResourceSet resourceSet = new ResourceSetImpl();
        resourceSet.getResourceFactoryRegistry().getExtensionToFactoryMap()
                .put("ecore", new EcoreResourceFactoryImpl());
        resourceSet.getResourceFactoryRegistry().getExtensionToFactoryMap()
                .put("xmi", new XMIResourceFactoryImpl());
        Resource metamodelResource = resourceSet.getResource(
                URI.createFileURI(metamodel.toString()), true);
        EcoreUtil.resolveAll(resourceSet);
        registerPackages(metamodelResource);

        EObject goal = create(metamodelResource, "BusinessGoal");
        set(goal, "id", "goal_order_growth");
        set(goal, "name", "Order Growth");
        set(goal, "successCriterion", "Customers can place and track orders.");
        set(goal, "priority", enumValue(metamodelResource, "Priority", "HIGH"));

        EObject capability = create(metamodelResource, "BusinessCapability");
        set(capability, "id", "cap_order_management");
        set(capability, "name", "Order Management");
        set(capability, "responsibility", "Manage customer orders.");
        set(capability, "criticality",
                enumValue(metamodelResource, "CapabilityCriticality", "CORE"));
        add(capability, "supports", goal);

        EObject actor = create(metamodelResource, "Actor");
        set(actor, "id", "actor_customer");
        set(actor, "name", "Customer");
        set(actor, "actorType", enumValue(metamodelResource, "ActorType", "HUMAN"));
        set(actor, "trustLevel", enumValue(metamodelResource, "TrustLevel", "PARTIALLY_TRUSTED"));
        set(actor, "authenticationExpectation", "Authenticated customer account.");

        EObject orderId = informationItem(metamodelResource, "item_order_id", "orderId",
                "IDENTIFIER", true);
        set(orderId, "formatHint", "uuid");
        EObject customerId = informationItem(metamodelResource, "item_customer_id", "customerId",
                "IDENTIFIER", true);
        EObject cartId = informationItem(metamodelResource, "item_cart_id", "cartId", "IDENTIFIER",
                true);
        EObject status = informationItem(metamodelResource, "item_order_status", "status", "TEXT",
                true);
        EObject total = informationItem(metamodelResource, "item_total_amount", "totalAmount",
                "MONEY", true);

        EObject entity = create(metamodelResource, "DomainEntity");
        set(entity, "id", "entity_order");
        set(entity, "name", "Order");
        set(entity, "identityDescription", "Order identity.");
        set(entity, "auditRelevant", true);
        set(entity, "identityStrategy",
                enumValue(metamodelResource, "IdentityStrategy", "SURROGATE_KEY"));
        add(entity, "identityAttributes", orderId);
        set(entity, "primaryIdentityAttribute", orderId);
        add(entity, "attributes", orderId);
        add(entity, "attributes", customerId);
        add(entity, "attributes", status);
        add(entity, "attributes", total);
        set(entity, "owningCapability", capability);

        EObject aggregate = create(metamodelResource, "AggregateCandidate");
        set(aggregate, "id", "aggregate_order");
        set(aggregate, "name", "Order Aggregate");
        set(aggregate, "root", entity);
        add(aggregate, "members", entity);
        set(aggregate, "consistencyExpectation",
                enumValue(metamodelResource, "ConsistencyExpectation", "SINGLE_ENTITY"));

        EObject event = create(metamodelResource, "BusinessEvent");
        set(event, "id", "event_order_placed");
        set(event, "name", "Order Placed");
        set(event, "semanticName", "OrderPlaced");
        set(event, "occurredInPastTenseName", "OrderPlaced");
        set(event, "eventTimeSemantics",
                enumValue(metamodelResource, "EventTimeSemantics", "BUSINESS_TIME"));
        set(event, "externallyVisible", false);
        set(event, "auditRelevant", true);
        set(event, "retentionRelevant", true);
        set(event, "orderingKeyCandidate", "customerId");
        add(event, "payload", orderId);
        add(event, "payload", customerId);
        add(event, "payload", total);
        add(event, "affects", entity);

        EObject command = create(metamodelResource, "Command");
        set(command, "id", "command_place_order");
        set(command, "name", "Place Order");
        set(command, "intent", "Place a customer order.");
        set(command, "commandType", enumValue(metamodelResource, "CommandType", "USER_INTENT"));
        set(command, "interactionExpectation",
                enumValue(metamodelResource, "InteractionExpectation",
                        "IMMEDIATE_RESPONSE_EXPECTED"));
        set(command, "userInitiated", true);
        set(command, "authorizationRequired", true);
        set(command, "authorizationRule", "Customer can place own order.");
        set(command, "duplicateSubmissionPossible", true);
        set(command, "auditRequired", true);
        set(command, "targetCapability", capability);
        set(command, "targetAggregate", aggregate);
        add(command, "issuedBy", actor);
        add(command, "input", customerId);
        add(command, "input", cartId);
        add(command, "expectedEvents", event);

        EObject query = create(metamodelResource, "Query");
        set(query, "id", "query_order_status");
        set(query, "name", "Get Order Status");
        set(query, "intent", "Get current order status.");
        set(query, "queryType", enumValue(metamodelResource, "QueryType", "STATUS"));
        set(query, "freshnessNeed",
                enumValue(metamodelResource, "FreshnessNeed", "NEAR_REAL_TIME"));
        set(query, "authorizationRequired", true);
        set(query, "containsPersonalData", false);
        set(query, "targetCapability", capability);
        add(query, "issuedBy", actor);
        add(query, "input", orderId);
        add(query, "output", status);
        add(query, "reads", entity);

        EObject model = create(metamodelResource, "CIMModel");
        set(model, "id", "cim_order_model");
        set(model, "name", "Order Management");
        set(model, "domainName", "Order Management");
        set(model, "businessScope", "Ordering");
        add(model, "goals", goal);
        add(model, "actors", actor);
        add(model, "capabilities", capability);
        add(model, "informationItems", orderId);
        add(model, "informationItems", customerId);
        add(model, "informationItems", cartId);
        add(model, "informationItems", status);
        add(model, "informationItems", total);
        add(model, "entities", entity);
        add(model, "aggregates", aggregate);
        add(model, "events", event);
        add(model, "commands", command);
        add(model, "queries", query);

        Resource modelResource = resourceSet.createResource(
                URI.createFileURI(modelFile.toString()));
        modelResource.getContents().add(model);
        modelResource.save(null);
    }

    private void createCoverageCimModel(Path metamodel, Path modelFile) throws IOException {
        ResourceSet resourceSet = new ResourceSetImpl();
        resourceSet.getResourceFactoryRegistry().getExtensionToFactoryMap()
                .put("ecore", new EcoreResourceFactoryImpl());
        resourceSet.getResourceFactoryRegistry().getExtensionToFactoryMap()
                .put("xmi", new XMIResourceFactoryImpl());
        Resource metamodelResource = resourceSet.getResource(
                URI.createFileURI(metamodel.toString()), true);
        EcoreUtil.resolveAll(resourceSet);
        registerPackages(metamodelResource);

        EObject goal = create(metamodelResource, "BusinessGoal");
        set(goal, "id", "goal_product_ops");
        set(goal, "name", "Product Operations");
        set(goal, "successCriterion", "Products can be checked against inventory.");
        set(goal, "priority", enumValue(metamodelResource, "Priority", "HIGH"));

        EObject sales = create(metamodelResource, "BusinessCapability");
        set(sales, "id", "cap_sales");
        set(sales, "name", "Sales");
        set(sales, "responsibility", "Sell products.");
        set(sales, "criticality", enumValue(metamodelResource, "CapabilityCriticality", "CORE"));
        add(sales, "supports", goal);

        EObject inventoryCap = create(metamodelResource, "BusinessCapability");
        set(inventoryCap, "id", "cap_inventory");
        set(inventoryCap, "name", "Inventory");
        set(inventoryCap, "responsibility", "Track product availability.");
        set(inventoryCap, "criticality",
                enumValue(metamodelResource, "CapabilityCriticality", "MISSION_CRITICAL"));
        add(inventoryCap, "supports", goal);

        EObject dependency = create(metamodelResource, "CapabilityDependency");
        set(dependency, "id", "dep_sales_inventory");
        set(dependency, "name", "Sales depends on Inventory");
        set(dependency, "dependencyReason", "Sales checks inventory before product confirmation.");
        set(dependency, "criticalPath", true);
        set(dependency, "source", sales);
        set(dependency, "target", inventoryCap);

        EObject actor = create(metamodelResource, "Actor");
        set(actor, "id", "actor_partner");
        set(actor, "name", "Partner");
        set(actor, "actorType", enumValue(metamodelResource, "ActorType", "EXTERNAL_ORGANIZATION"));
        set(actor, "trustLevel", enumValue(metamodelResource, "TrustLevel", "UNTRUSTED_EXTERNAL"));
        set(actor, "authenticationExpectation", "Federated partner account.");

        EObject productId = informationItem(metamodelResource, "item_product_id", "productId",
                "IDENTIFIER", true);
        EObject emailClassification = create(metamodelResource, "DataClassification");
        set(emailClassification, "id", "class_partner_email");
        set(emailClassification, "name", "Partner Email Classification");
        set(emailClassification, "kind", enumValue(metamodelResource, "DataKind", "PERSONAL"));
        set(emailClassification, "identifiability",
                enumValue(metamodelResource, "Identifiability", "DIRECTLY_IDENTIFYING"));
        set(emailClassification, "encryptionExpected", true);
        set(emailClassification, "maskingExpected", true);
        set(emailClassification, "auditAccessRequired", true);
        set(emailClassification, "deletionRightApplies", true);
        set(emailClassification, "classificationRationale", "Partner contact data.");
        EObject sku = informationItem(metamodelResource, "item_sku", "sku", "TEXT", true);
        EObject partnerEmail = informationItem(metamodelResource, "item_partner_email",
                "partnerEmail", "EMAIL", true);
        set(partnerEmail, "classification", emailClassification);
        EObject productStatus = informationItem(metamodelResource, "item_product_status",
                "productStatus", "ENUMERATION", true);
        addValue(productStatus, "allowedValues", "ACTIVE");
        addValue(productStatus, "allowedValues", "DISCONTINUED");
        EObject inventoryId = informationItem(metamodelResource, "item_inventory_id",
                "inventoryId", "IDENTIFIER", true);
        EObject available = informationItem(metamodelResource, "item_available", "available",
                "BOOLEAN", true);

        EObject product = create(metamodelResource, "DomainEntity");
        set(product, "id", "entity_product");
        set(product, "name", "Product");
        set(product, "identityDescription", "Product identity.");
        set(product, "auditRelevant", true);
        set(product, "identityStrategy",
                enumValue(metamodelResource, "IdentityStrategy", "SURROGATE_KEY"));
        add(product, "identityAttributes", productId);
        set(product, "primaryIdentityAttribute", productId);
        add(product, "attributes", productId);
        add(product, "attributes", sku);
        add(product, "attributes", partnerEmail);
        add(product, "attributes", productStatus);
        set(product, "owningCapability", sales);

        EObject inventory = create(metamodelResource, "DomainEntity");
        set(inventory, "id", "entity_inventory");
        set(inventory, "name", "Inventory");
        set(inventory, "identityDescription", "Inventory identity.");
        set(inventory, "auditRelevant", true);
        set(inventory, "identityStrategy",
                enumValue(metamodelResource, "IdentityStrategy", "SURROGATE_KEY"));
        add(inventory, "identityAttributes", inventoryId);
        set(inventory, "primaryIdentityAttribute", inventoryId);
        add(inventory, "attributes", inventoryId);
        add(inventory, "attributes", available);
        set(inventory, "owningCapability", inventoryCap);

        EObject relationship = create(metamodelResource, "DomainRelationship");
        set(relationship, "id", "rel_product_inventory");
        set(relationship, "name", "Product Inventory");
        set(relationship, "relationshipType",
                enumValue(metamodelResource, "DomainRelationshipType", "ASSOCIATION"));
        set(relationship, "sourceRole", "product");
        set(relationship, "targetRole", "inventory");
        set(relationship, "sourceMultiplicity",
                multiplicity(metamodelResource, "mult_rel_product_inventory_source", 1, 1,
                        false));
        set(relationship, "targetMultiplicity",
                multiplicity(metamodelResource, "mult_rel_product_inventory_target", 0, null,
                        true));
        set(relationship, "ownership", false);
        set(relationship, "navigableFromSource", true);
        set(relationship, "navigableFromTarget", false);
        set(relationship, "source", product);
        set(relationship, "target", inventory);

        EObject productAggregate = create(metamodelResource, "AggregateCandidate");
        set(productAggregate, "id", "aggregate_product");
        set(productAggregate, "name", "Product Aggregate");
        set(productAggregate, "root", product);
        add(productAggregate, "members", product);
        set(productAggregate, "consistencyExpectation",
                enumValue(metamodelResource, "ConsistencyExpectation", "SINGLE_ENTITY"));

        EObject inventoryAggregate = create(metamodelResource, "AggregateCandidate");
        set(inventoryAggregate, "id", "aggregate_inventory");
        set(inventoryAggregate, "name", "Inventory Aggregate");
        set(inventoryAggregate, "root", inventory);
        add(inventoryAggregate, "members", inventory);
        set(inventoryAggregate, "consistencyExpectation",
                enumValue(metamodelResource, "ConsistencyExpectation", "SINGLE_ENTITY"));

        EObject query = create(metamodelResource, "Query");
        set(query, "id", "query_check_product");
        set(query, "name", "Check Product");
        set(query, "intent", "Check product status for a partner.");
        set(query, "queryType", enumValue(metamodelResource, "QueryType", "LOOKUP"));
        set(query, "freshnessNeed", enumValue(metamodelResource, "FreshnessNeed", "REAL_TIME"));
        set(query, "authorizationRequired", true);
        set(query, "containsPersonalData", false);
        set(query, "targetCapability", sales);
        add(query, "issuedBy", actor);
        add(query, "input", productId);
        add(query, "output", productStatus);
        add(query, "reads", product);

        EObject inventoryQuery = create(metamodelResource, "Query");
        set(inventoryQuery, "id", "query_inventory_status");
        set(inventoryQuery, "name", "Inventory Status");
        set(inventoryQuery, "intent", "Read inventory status.");
        set(inventoryQuery, "queryType", enumValue(metamodelResource, "QueryType", "STATUS"));
        set(inventoryQuery, "freshnessNeed",
                enumValue(metamodelResource, "FreshnessNeed", "NEAR_REAL_TIME"));
        set(inventoryQuery, "authorizationRequired", false);
        set(inventoryQuery, "containsPersonalData", false);
        set(inventoryQuery, "targetCapability", inventoryCap);
        add(inventoryQuery, "input", inventoryId);
        add(inventoryQuery, "output", available);
        add(inventoryQuery, "reads", inventory);

        EObject externalSystem = create(metamodelResource, "ExternalSystem");
        set(externalSystem, "id", "external_fulfillment");
        set(externalSystem, "name", "Fulfillment Partner");
        set(externalSystem, "actorType",
                enumValue(metamodelResource, "ActorType", "EXTERNAL_SYSTEM"));
        set(externalSystem, "trustLevel",
                enumValue(metamodelResource, "TrustLevel", "UNTRUSTED_EXTERNAL"));
        set(externalSystem, "businessPurpose", "Fulfill product shipment.");
        set(externalSystem, "storesBusinessData", true);
        set(externalSystem, "sendsBusinessEvents", false);
        set(externalSystem, "receivesBusinessEvents", true);

        EObject start = create(metamodelResource, "StartStep");
        set(start, "id", "step_start");
        set(start, "name", "Start");
        set(start, "stepKind", enumValue(metamodelResource, "StepKind", "START"));
        set(start, "orderIndex", 1);
        set(start, "optional", false);
        set(start, "repeatable", false);

        EObject externalStep = create(metamodelResource, "ExternalInteractionStep");
        set(externalStep, "id", "step_external_fulfillment");
        set(externalStep, "name", "Send Fulfillment Request");
        set(externalStep, "stepKind",
                enumValue(metamodelResource, "StepKind", "EXTERNAL_INTERACTION"));
        set(externalStep, "orderIndex", 2);
        set(externalStep, "optional", false);
        set(externalStep, "repeatable", false);
        set(externalStep, "externalSystem", externalSystem);
        set(externalStep, "interactionPurpose", "Request fulfillment.");

        EObject end = create(metamodelResource, "EndStep");
        set(end, "id", "step_end");
        set(end, "name", "End");
        set(end, "stepKind", enumValue(metamodelResource, "StepKind", "END"));
        set(end, "orderIndex", 3);
        set(end, "optional", false);
        set(end, "repeatable", false);

        EObject transition1 = create(metamodelResource, "ProcessTransition");
        set(transition1, "id", "transition_start_external");
        set(transition1, "name", "Start to Fulfillment");
        set(transition1, "source", start);
        set(transition1, "target", externalStep);
        set(transition1, "orderIndex", 1);

        EObject transition2 = create(metamodelResource, "ProcessTransition");
        set(transition2, "id", "transition_external_end");
        set(transition2, "name", "Fulfillment to End");
        set(transition2, "source", externalStep);
        set(transition2, "target", end);
        set(transition2, "orderIndex", 2);

        EObject temporal = create(metamodelResource, "TemporalConstraint");
        set(temporal, "id", "temporal_fulfillment_timeout");
        set(temporal, "name", "Fulfillment Timeout");
        set(temporal, "durationExpression", "5 minutes");
        set(temporal, "orderingExpression", "productId");
        set(temporal, "violationSeverity", enumValue(metamodelResource, "Severity", "ERROR"));
        add(temporal, "constrainedElements", externalStep);

        EObject process = create(metamodelResource, "BusinessProcess");
        set(process, "id", "process_fulfill_product");
        set(process, "name", "Fulfill Product");
        set(process, "processKind",
                enumValue(metamodelResource, "ProcessKind", "SAGA_LIKE_BUSINESS_PROCESS"));
        set(process, "criticality",
                enumValue(metamodelResource, "CapabilityCriticality", "MISSION_CRITICAL"));
        set(process, "businessTriggerDescription", "Product is ready for fulfillment.");
        set(process, "longRunning", true);
        set(process, "humanApprovalPossible", false);
        set(process, "compensationExpected", true);
        set(process, "completionCriterion", "Fulfillment request accepted.");
        set(process, "owningCapability", sales);
        add(process, "steps", start);
        add(process, "steps", externalStep);
        add(process, "steps", end);
        add(process, "transitions", transition1);
        add(process, "transitions", transition2);
        add(process, "temporalConstraints", temporal);

        EObject performanceNfr = create(metamodelResource, "NonFunctionalRequirement");
        set(performanceNfr, "id", "nfr_partner_latency");
        set(performanceNfr, "name", "Partner Latency");
        set(performanceNfr, "requirementType",
                enumValue(metamodelResource, "RequirementType", "QUALITY"));
        set(performanceNfr, "sourceType",
                enumValue(metamodelResource, "RequirementSourceType", "STAKEHOLDER"));
        set(performanceNfr, "priority", enumValue(metamodelResource, "Priority", "HIGH"));
        set(performanceNfr, "mandatory", true);
        set(performanceNfr, "productionBlocking", true);
        set(performanceNfr, "fitCriterion", "P95 under 5 minutes.");
        set(performanceNfr, "qualityType", enumValue(metamodelResource, "QualityType", "LATENCY"));
        set(performanceNfr, "metric", "p95 latency");
        set(performanceNfr, "target", "5 minutes");
        add(performanceNfr, "constrainedElements", query);

        EObject model = create(metamodelResource, "CIMModel");
        set(model, "id", "cim_coverage_model");
        set(model, "name", "Coverage Model");
        set(model, "domainName", "Commerce");
        set(model, "businessScope", "Product and inventory checks");
        add(model, "goals", goal);
        add(model, "actors", actor);
        add(model, "actors", externalSystem);
        add(model, "capabilities", sales);
        add(model, "capabilities", inventoryCap);
        add(model, "capabilityDependencies", dependency);
        add(model, "requirements", performanceNfr);
        add(model, "classifications", emailClassification);
        add(model, "informationItems", productId);
        add(model, "informationItems", sku);
        add(model, "informationItems", partnerEmail);
        add(model, "informationItems", productStatus);
        add(model, "informationItems", inventoryId);
        add(model, "informationItems", available);
        add(model, "entities", product);
        add(model, "entities", inventory);
        add(model, "relationships", relationship);
        add(model, "aggregates", productAggregate);
        add(model, "aggregates", inventoryAggregate);
        add(model, "queries", query);
        add(model, "queries", inventoryQuery);
        add(model, "processes", process);

        Resource modelResource = resourceSet.createResource(
                URI.createFileURI(modelFile.toString()));
        modelResource.getContents().add(model);
        modelResource.save(null);
    }

    private EObject informationItem(Resource metamodelResource, String id, String name, String type,
            boolean required) {
        EObject item = create(metamodelResource, "InformationItem");
        set(item, "id", id);
        set(item, "name", name);
        set(item, "businessName", name);
        set(item, "type", enumValue(metamodelResource, "PrimitiveBusinessType", type));
        set(item, "required", required);
        set(item, "collection", false);
        return item;
    }

    private EObject multiplicity(Resource metamodelResource, String id, int lowerBound,
            Integer upperBound, boolean unbounded) {
        EObject multiplicity = create(metamodelResource, "Multiplicity");
        set(multiplicity, "id", id);
        set(multiplicity, "name", id);
        set(multiplicity, "lowerBound", lowerBound);
        if (upperBound != null) {
            set(multiplicity, "upperBound", upperBound);
        }
        set(multiplicity, "unbounded", unbounded);
        set(multiplicity, "ordered", false);
        set(multiplicity, "unique", true);
        return multiplicity;
    }

    private Resource loadModel(Path metamodel, Path modelFile) {
        ResourceSet resourceSet = new ResourceSetImpl();
        resourceSet.getResourceFactoryRegistry().getExtensionToFactoryMap()
                .put("ecore", new EcoreResourceFactoryImpl());
        resourceSet.getResourceFactoryRegistry().getExtensionToFactoryMap()
                .put("xmi", new XMIResourceFactoryImpl());
        Resource metamodelResource = resourceSet.getResource(
                URI.createFileURI(metamodel.toString()), true);
        registerPackages(metamodelResource);
        Resource modelResource = resourceSet.getResource(URI.createFileURI(modelFile.toString()),
                true);
        EcoreUtil.resolveAll(resourceSet);
        return modelResource;
    }

    private void registerPackages(Resource metamodelResource) {
        for (EObject content : metamodelResource.getContents()) {
            if (content instanceof EPackage ePackage) {
                registerPackage(ePackage);
            }
        }
    }

    private void registerPackage(EPackage ePackage) {
        EPackage.Registry.INSTANCE.put(ePackage.getNsURI(), ePackage);
        for (EPackage child : ePackage.getESubpackages()) {
            registerPackage(child);
        }
    }

    private EObject create(Resource metamodelResource, String classifierName) {
        EClass eClass = (EClass) classifier(metamodelResource, classifierName);
        EFactory factory = eClass.getEPackage().getEFactoryInstance();
        return factory.create(eClass);
    }

    private Object enumValue(Resource metamodelResource, String enumName, String literalName) {
        EEnum eEnum = (EEnum) classifier(metamodelResource, enumName);
        return eEnum.getEEnumLiteral(literalName).getInstance();
    }

    private EClassifier classifier(Resource metamodelResource, String name) {
        for (EObject content : metamodelResource.getContents()) {
            EClassifier classifier = classifier((EPackage) content, name);
            if (classifier != null) {
                return classifier;
            }
        }
        throw new IllegalArgumentException("Classifier not found: " + name);
    }

    private EClassifier classifier(EPackage ePackage, String name) {
        EClassifier classifier = ePackage.getEClassifier(name);
        if (classifier != null) {
            return classifier;
        }
        for (EPackage child : ePackage.getESubpackages()) {
            EClassifier nested = classifier(child, name);
            if (nested != null) {
                return nested;
            }
        }
        return null;
    }

    private void set(EObject object, String featureName, Object value) {
        object.eSet(feature(object, featureName), value);
    }

    @SuppressWarnings("unchecked")
    private void add(EObject object, String featureName, EObject value) {
        ((List<EObject>) object.eGet(feature(object, featureName))).add(value);
    }

    @SuppressWarnings("unchecked")
    private void addValue(EObject object, String featureName, Object value) {
        ((List<Object>) object.eGet(feature(object, featureName))).add(value);
    }

    @SuppressWarnings("unchecked")
    private List<EObject> values(EObject object, String featureName) {
        return (List<EObject>) object.eGet(feature(object, featureName));
    }

    private EObject reference(EObject object, String featureName) {
        return (EObject) object.eGet(feature(object, featureName));
    }

    private Object get(EObject object, String featureName) {
        return object.eGet(feature(object, featureName));
    }

    private EStructuralFeature feature(EObject object, String featureName) {
        EStructuralFeature feature = object.eClass().getEStructuralFeature(featureName);
        if (feature == null) {
            throw new IllegalArgumentException(
                    object.eClass().getName() + " has no feature " + featureName);
        }
        return feature;
    }
}
