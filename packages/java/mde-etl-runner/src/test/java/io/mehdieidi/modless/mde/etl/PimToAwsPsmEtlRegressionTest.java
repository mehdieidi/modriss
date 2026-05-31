package io.mehdieidi.modless.mde.etl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.eclipse.emf.common.util.TreeIterator;
import org.eclipse.emf.common.util.URI;
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

final class PimToAwsPsmEtlRegressionTest {

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
    void transformsSamplePimToAwsPsmWithSpecCompletenessShape() throws Exception {
        Path psmMetamodel = REPOSITORY_ROOT.resolve("mde/metamodels/psm/psm-combined.ecore");
        Path sampleModel = REPOSITORY_ROOT.resolve("mde/samples/pim.xmi");
        Path psmModel = tempDir.resolve("climate-relief-grants-awspsm.xmi");

        EtlExecutionReport report = executeOrFail(PimToAwsPsmDefaults.request(
                REPOSITORY_ROOT, sampleModel, psmModel, true, true));

        assertEquals(EtlExecutionStatus.SUCCEEDED, report.status(),
                report.diagnostics().toString());
        assertTrue(Files.isRegularFile(psmModel),
                "The PIM-to-AWS-PSM profile should persist an AWS PSM model.");

        EObject root = loadModel(psmMetamodel, psmModel).getContents().get(0);
        assertEquals("AwsPsmModel", root.eClass().getName());
        assertEquals(3, values(root, "stages").size(),
                "The sample's dev/test/prod environments should become AWS stages.");
        assertEquals(2, values(root, "stacks").size(),
                "The sample's two deployment units should become SAM stacks.");
        assertTrue(Boolean.TRUE.equals(get(root, "productionMode")),
                "The sample has a production-like PROD environment.");
        assertTrue(reference(root, "traceModel") != null, "Expected trace model coverage.");
        assertFalse(values(reference(root, "traceModel"), "links").isEmpty(),
                "Expected generated trace links.");

        List<EObject> resources = containedAwsResources(root);
        assertFalse(resources.isEmpty(), "Expected generated AWS resources.");
        assertAny(resources, "AwsLambdaFunction", "Expected Lambda functions for PIM functions.");
        assertAnyOf(resources, List.of("HttpApi", "RestApi"),
                "Expected API Gateway APIs for resource/RPC HTTP PIM APIs.");
        assertAny(resources, "DynamoDbTable",
                "Expected DynamoDB tables for key-value/document stores.");
        assertAny(resources, "SqsQueue", "Expected SQS queues for PIM queues.");
        assertAny(resources, "SnsTopic", "Expected SNS topics for PIM topics.");
        assertAny(resources, "EventBridgeBus", "Expected EventBridge buses for PIM event buses.");
        assertAny(resources, "StepFunctionStateMachine",
                "Expected Step Functions state machines for PIM workflows.");
        assertAny(resources, "CognitoUserPool",
                "Expected Cognito user pools for PIM identity providers.");
        assertAny(resources, "SecretsManagerSecret",
                "Expected Secrets Manager secrets for PIM secrets.");
        assertAny(resources, "CloudWatchLogGroup",
                "Expected CloudWatch log groups for generated compute/API/workflow resources.");

        assertFalse(values(root, "relationshipViews").isEmpty(),
                "Expected generated relationship views for API/event/message integrations.");

        EObject readiness = reference(root, "readiness");
        assertTrue(readiness != null, "Expected readiness assessment.");
        assertFalse(values(readiness, "manualDecisions").isEmpty(),
                "The sample's open AWS-specific decisions should remain visible.");
        assertFalse(values(readiness, "checks").isEmpty(),
                "Expected generated readiness checks.");
    }

    private EtlExecutionReport executeOrFail(EtlExecutionRequest request) {
        try {
            return new EpsilonEtlExecutor().execute(request);
        } catch (EtlExecutionException ex) {
            fail("ETL execution failed: " + ex.getReport().diagnostics());
            throw new AssertionError(ex);
        }
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

    private void assertAny(List<EObject> objects, String className, String message) {
        assertTrue(objects.stream().anyMatch(object -> className.equals(object.eClass().getName())),
                message);
    }

    private void assertAnyOf(List<EObject> objects, List<String> classNames, String message) {
        assertTrue(
                objects.stream().anyMatch(object -> classNames.contains(object.eClass().getName())),
                message);
    }

    private List<EObject> containedAwsResources(EObject root) {
        List<EObject> resources = new java.util.ArrayList<>();
        TreeIterator<EObject> contents = root.eAllContents();
        while (contents.hasNext()) {
            EObject object = contents.next();
            if (object.eClass().getEAllSuperTypes().stream()
                    .anyMatch(type -> "AwsResource".equals(type.getName()))) {
                resources.add(object);
            }
        }
        return resources;
    }
}
