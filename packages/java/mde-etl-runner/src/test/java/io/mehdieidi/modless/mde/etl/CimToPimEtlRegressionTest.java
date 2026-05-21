package io.mehdieidi.modless.mde.etl;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import io.mehdieidi.modless.mdecli.service.ConversionRequest;
import io.mehdieidi.modless.mdecli.service.EmfConversionService;
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
        Path cimMetamodel = compileMetamodel("cim/cim-root.emf", "cim-combined.ecore");
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

    private Path compileMetamodel(String rootFile, String outputName) {
        Path output = tempDir.resolve(outputName);
        new EmfConversionService().convert(new ConversionRequest(
                REPOSITORY_ROOT.resolve("mde/metamodels").resolve(rootFile).getParent(),
                output,
                REPOSITORY_ROOT.resolve("mde/metamodels").resolve(rootFile),
                true,
                false));
        return output;
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
        set(entity, "identityAttribute", orderId);
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
    private List<EObject> values(EObject object, String featureName) {
        return (List<EObject>) object.eGet(feature(object, featureName));
    }

    private EObject reference(EObject object, String featureName) {
        return (EObject) object.eGet(feature(object, featureName));
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
