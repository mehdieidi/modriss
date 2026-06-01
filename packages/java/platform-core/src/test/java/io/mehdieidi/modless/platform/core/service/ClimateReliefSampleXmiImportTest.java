package io.mehdieidi.modless.platform.core.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.node.ObjectNode;
import io.mehdieidi.modless.platform.core.model.ModelLevel;
import io.mehdieidi.modless.platform.core.repository.JsonFileStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.EcorePackage;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.eclipse.emf.ecore.xmi.impl.EcoreResourceFactoryImpl;
import org.eclipse.emf.ecore.xmi.impl.XMIResourceFactoryImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ClimateReliefSampleXmiImportTest {

    @TempDir
    Path tempDir;

    @Test
    void importsClimateReliefSample() throws Exception {
        JsonFileStore store = new JsonFileStore(tempDir);
        store.initialize();
        AuthService authService = new AuthService(store, Duration.ofHours(1));
        ProjectService projectService = new ProjectService(store, authService);
        ModelService service = new ModelService(store, projectService);

        byte[] bytes = Files.readAllBytes(Path.of("..", "..", "..", "mde", "samples",
                "climate-relief-grants-cim-sample.xmi").normalize());

        ModelService.ImportResult result = service.importModel(ModelLevel.CIM,
                "climate-relief-grants-cim-sample.xmi", bytes, "xmi");

        assertFalse(result.modelJson().path("goals").isEmpty());
        assertFalse(result.modelJson().path("actors").isEmpty());
        assertTrue(result.modelJson().path("graph").path("elements").size() > 10);
    }

    @Test
    void importedClimateReliefSampleHasNoValidationErrors() throws Exception {
        JsonFileStore store = new JsonFileStore(tempDir);
        store.initialize();
        AuthService authService = new AuthService(store, Duration.ofHours(1));
        ProjectService projectService = new ProjectService(store, authService);
        ModelService service = new ModelService(store, projectService);

        byte[] bytes = Files.readAllBytes(Path.of("..", "..", "..", "mde", "samples",
                "climate-relief-grants-cim-sample.xmi").normalize());

        ModelService.ImportResult result = service.importModel(ModelLevel.CIM,
                "climate-relief-grants-cim-sample.xmi", bytes, "xmi");

        assertTrue(result.issues().isEmpty(),
                () -> "Expected no validation issues, found: " + result.issues());
        assertEquals("cim-root", result.modelJson().path("id").asText());
    }

    @Test
    void validationExportPreservesCimAssumptions() throws Exception {
        JsonFileStore store = new JsonFileStore(tempDir);
        store.initialize();
        AuthService authService = new AuthService(store, Duration.ofHours(1));
        ProjectService projectService = new ProjectService(store, authService);
        ModelService service = new ModelService(store, projectService);

        ObjectNode model = store.objectMapper().createObjectNode();
        model.put("eClass", "CIMModel");
        model.put("id", "cim-root");
        model.put("name", "Climate");
        model.put("domainName", "Climate");
        ObjectNode goal = model.putArray("goals").addObject();
        goal.put("eClass", "BusinessGoal");
        goal.put("id", "goal-rapid");
        goal.put("name", "Rapid relief");
        ObjectNode actor = model.putArray("actors").addObject();
        actor.put("eClass", "Actor");
        actor.put("id", "actor-resident");
        actor.put("name", "Resident");
        ObjectNode capability = model.putArray("capabilities").addObject();
        capability.put("eClass", "BusinessCapability");
        capability.put("id", "cap-intake");
        capability.put("name", "Intake");
        capability.putArray("supports").add("goal-rapid");
        ObjectNode assumption = model.putArray("assumptions").addObject();
        assumption.put("eClass", "Assumption");
        assumption.put("id", "asm-identity");
        assumption.put("name", "Identity Registry Availability Assumption");
        assumption.put("assumptionStatement", "Identity registry is available.");
        ObjectNode readiness = model.putObject("readiness");
        readiness.put("eClass", "ProductionReadinessAssessment");
        readiness.put("id", "readiness");
        readiness.put("name", "Readiness");
        ObjectNode finding = readiness.putArray("findings").addObject();
        finding.put("eClass", "ReadinessFinding");
        finding.put("id", "finding-2");
        finding.put("name", "Identity assumption finding");
        finding.put("severity", "WARNING");
        finding.put("message",
                "The identity registry availability assumption needs pilot validation.");
        finding.put("recommendation", "Exercise degraded-mode flows.");
        finding.putArray("affectedElements").add("asm-identity");

        service.exportModel(ModelLevel.CIM, model, "xmi");
    }

    @Test
    void importsAwsPsmSampleWithNestedPsmPackages() throws Exception {
        JsonFileStore store = new JsonFileStore(tempDir);
        store.initialize();
        AuthService authService = new AuthService(store, Duration.ofHours(1));
        ProjectService projectService = new ProjectService(store, authService);
        ModelService service = new ModelService(store, projectService);

        byte[] bytes = Files.readAllBytes(Path.of("..", "..", "..", "mde", "samples",
                "psm.xmi").normalize());

        ModelService.ImportResult result = service.importModel(ModelLevel.PSM, "psm.xmi", bytes,
                "xmi");

        assertEquals("AwsPsmModel", result.modelJson().path("eClass").asText());
        assertFalse(result.modelJson().path("allResources").isEmpty());
    }

    @Test
    void rawEmfLoadOfClimateReliefSample() throws Exception {
        Path sample = Path.of("..", "..", "..", "mde", "samples",
                "climate-relief-grants-cim-sample.xmi").normalize().toAbsolutePath();
        Path metamodel = Path.of("..", "..", "..", "mde", "metamodels", "cim",
                "cim-combined.ecore").normalize().toAbsolutePath();

        Resource.Factory.Registry.INSTANCE.getExtensionToFactoryMap()
                .put("ecore", new EcoreResourceFactoryImpl());
        Resource.Factory.Registry.INSTANCE.getExtensionToFactoryMap()
                .put("xmi", new XMIResourceFactoryImpl());

        ResourceSet resourceSet = new ResourceSetImpl();
        resourceSet.getResourceFactoryRegistry().getExtensionToFactoryMap()
                .put("ecore", new EcoreResourceFactoryImpl());
        resourceSet.getResourceFactoryRegistry().getExtensionToFactoryMap()
                .put("xmi", new XMIResourceFactoryImpl());
        resourceSet.getPackageRegistry().put(EcorePackage.eNS_URI, EcorePackage.eINSTANCE);

        Resource ecore = resourceSet.getResource(URI.createFileURI(metamodel.toString()), true);
        ecore.getContents().stream().filter(EPackage.class::isInstance).map(EPackage.class::cast)
                .forEach(pkg -> registerPackage(resourceSet, pkg));

        Resource resource = resourceSet.getResource(URI.createFileURI(sample.toString()), true);
        resource.load(Map.of());
        assertFalse(resource.getContents().isEmpty());
    }

    private void registerPackage(ResourceSet resourceSet, EPackage ePackage) {
        if (ePackage.getNsURI() != null && !ePackage.getNsURI().isBlank()) {
            resourceSet.getPackageRegistry().put(ePackage.getNsURI(), ePackage);
        }
        ePackage.getESubpackages().forEach(child -> registerPackage(resourceSet, child));
    }
}
