package io.mehdieidi.modless.platform.model.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.mehdieidi.modless.platform.kernel.ModelLevel;
import io.mehdieidi.modless.platform.model.domain.ModelRecord;
import java.nio.file.Path;
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
import org.junit.jupiter.api.parallel.ResourceLock;

/**
 * Regression coverage for importing, validating, and re-exporting the repository climate-relief
 * sample models.
 */
@ResourceLock("emf-registry")
@ResourceLock("epsilon-runtime")
class ClimateReliefSampleXmiImportTest {

  /** Isolated store directory for persisted platform records created by tests. */
  @TempDir Path tempDir;

  /**
   * Verifies that the canonical CIM sample imports into semantic JSON and a populated graph
   * projection.
   *
   * @throws Exception when sample loading or import fails
   */
  @Test
  void importsClimateReliefSample() throws Exception {
    PlatformTestFixtures.ServiceStack services = PlatformTestFixtures.createServices(tempDir);
    ModelService service = services.models();

    ModelService.ImportResult result =
        service.importModel(ModelLevel.CIM, "cim.xmi", PlatformTestFixtures.climateCimXmi(), "xmi");

    assertFalse(result.modelJson().path("goals").isEmpty());
    assertFalse(result.modelJson().path("actors").isEmpty());
    assertTrue(result.modelJson().path("graph").path("elements").size() > 10);
  }

  /**
   * Confirms that the canonical CIM sample is semantically valid immediately after import.
   *
   * @throws Exception when sample loading or import fails
   */
  @Test
  void importedClimateReliefSampleHasNoValidationErrors() throws Exception {
    PlatformTestFixtures.ServiceStack services = PlatformTestFixtures.createServices(tempDir);
    ModelService service = services.models();

    ModelService.ImportResult result =
        service.importModel(ModelLevel.CIM, "cim.xmi", PlatformTestFixtures.climateCimXmi(), "xmi");

    assertTrue(
        result.issues().isEmpty(),
        () -> "Expected no validation issues, found: " + result.issues());
    assertEquals("cim-root", result.modelJson().path("id").asText());
  }

  /**
   * Guards the exporter against reintroducing duplicate nested information item IDs from the
   * climate-relief sample.
   *
   * @throws Exception when sample import or export fails
   */
  @Test
  void importedClimateReliefSampleExportsWithoutDuplicateInformationItemIds() throws Exception {
    PlatformTestFixtures.ServiceStack services = PlatformTestFixtures.createServices(tempDir);
    ModelService service = services.models();

    ModelService.ImportResult result =
        service.importModel(ModelLevel.CIM, "cim.xmi", PlatformTestFixtures.climateCimXmi(), "xmi");

    byte[] exported = service.exportModel(ModelLevel.CIM, result.modelJson(), "xmi");

    assertTrue(exported.length > 0);
  }

  /**
   * Ensures the validate-by-id path tolerates older JSON payloads that exposed nested information
   * items both at the owning element and top level.
   *
   * @throws Exception when setup, persistence, or validation fails
   */
  @Test
  void validateButtonPathToleratesPreviouslyDuplicatedInformationItemJson() throws Exception {
    PlatformTestFixtures.ServiceStack services = PlatformTestFixtures.createServices(tempDir);
    ModelService service = services.models();
    PlatformTestFixtures.AuthenticatedContext context =
        PlatformTestFixtures.registerOwner(
            services, "cim-validator@example.com", "Owner", "Climate");

    ObjectNode model =
        (ObjectNode)
            service
                .importModel(ModelLevel.CIM, "cim.xmi", PlatformTestFixtures.climateCimXmi(), "xmi")
                .modelJson();
    duplicateInformationItemsUnderAddress(model);
    ModelRecord created =
        service.create(context.user(), ModelLevel.CIM, context.project().id(), "cim", model);

    ModelService.ValidationResult validation =
        service.validate(context.user(), ModelLevel.CIM, created.id());

    assertFalse(
        validation.issues().stream()
            .anyMatch(
                issue ->
                    "XmiExport".equals(issue.constraint())
                        && issue.message().contains("Duplicate model element id")));
  }

  /**
   * Verifies that validating a stored CIM model preserves trace link endpoint references through
   * the source-XMI round trip.
   *
   * @throws Exception when sample import, persistence, or validation fails
   */
  @Test
  void validateButtonPathPreservesTraceLinkEndpoints() throws Exception {
    PlatformTestFixtures.ServiceStack services = PlatformTestFixtures.createServices(tempDir);
    ModelService service = services.models();
    PlatformTestFixtures.AuthenticatedContext context =
        PlatformTestFixtures.registerOwner(
            services, "trace-validator@example.com", "Owner", "Climate Trace");

    ObjectNode model =
        (ObjectNode)
            service
                .importModel(ModelLevel.CIM, "cim.xmi", PlatformTestFixtures.climateCimXmi(), "xmi")
                .modelJson();
    ModelRecord created =
        service.create(context.user(), ModelLevel.CIM, context.project().id(), "cim", model);

    ModelService.ValidationResult validation =
        service.validate(context.user(), ModelLevel.CIM, created.id());

    assertTrue(
        validation.valid(),
        () ->
            "The imported canonical CIM sample should validate without issues: "
                + validation.issues());
    assertNoTraceLinkEndpointErrors(validation);
  }

  /**
   * Confirms that stale source XMI without trace endpoints is repaired during validation so later
   * source-XMI validation sees the restored endpoints.
   *
   * @throws Exception when sample import, persistence, or validation fails
   */
  @Test
  void validateButtonPathRepairsStaleTraceLinkSourceXmi() throws Exception {
    PlatformTestFixtures.ServiceStack services = PlatformTestFixtures.createServices(tempDir);
    ModelService service = services.models();
    PlatformTestFixtures.AuthenticatedContext context =
        PlatformTestFixtures.registerOwner(
            services, "stale-trace-validator@example.com", "Owner", "Climate Stale Trace");

    byte[] sample = PlatformTestFixtures.climateCimXmi();
    ObjectNode model =
        (ObjectNode) service.importModel(ModelLevel.CIM, "cim.xmi", sample, "xmi").modelJson();
    ModelRecord created =
        service.create(context.user(), ModelLevel.CIM, context.project().id(), "cim", model);
    service.attachSourceXmi(
        created,
        removeTraceLinkEndpoints(new String(sample))
            .getBytes(java.nio.charset.StandardCharsets.UTF_8));

    ModelService.ValidationResult validation =
        service.validate(context.user(), ModelLevel.CIM, created.id());

    assertNoTraceLinkEndpointErrors(validation);
    ModelService.ValidationResult repairedSourceValidation =
        service.validateGeneratedXmi(
            ModelLevel.CIM,
            service
                .sourceXmi(service.get(context.user(), ModelLevel.CIM, created.id()))
                .orElseThrow());
    assertNoTraceLinkEndpointErrors(repairedSourceValidation);
  }

  /**
   * Verifies that CIM assumptions referenced by readiness findings export to XMI without requiring
   * a full sample model.
   *
   * @throws Exception when export fails
   */
  @Test
  void validationExportPreservesCimAssumptions() throws Exception {
    PlatformTestFixtures.ServiceStack services = PlatformTestFixtures.createServices(tempDir);
    ModelService service = services.models();

    ObjectNode model = services.store().objectMapper().createObjectNode();
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
    finding.put("message", "The identity registry availability assumption needs pilot validation.");
    finding.put("recommendation", "Exercise degraded-mode flows.");
    finding.putArray("affectedElements").add("asm-identity");

    service.exportModel(ModelLevel.CIM, model, "xmi");
  }

  /**
   * Ensures AWS PSM samples with nested PSM package namespaces import through the platform
   * metamodel resolver.
   *
   * @throws Exception when sample loading or import fails
   */
  @Test
  void importsAwsPsmSampleWithNestedPsmPackages() throws Exception {
    PlatformTestFixtures.ServiceStack services = PlatformTestFixtures.createServices(tempDir);
    ModelService service = services.models();

    ModelService.ImportResult result =
        service.importModel(ModelLevel.PSM, "psm.xmi", PlatformTestFixtures.awsPsmXmi(), "xmi");

    assertEquals("AwsPsmModel", result.modelJson().path("eClass").asText());
    assertFalse(result.modelJson().path("allResources").isEmpty());
  }

  /**
   * Sanity-checks direct EMF loading of the climate sample with the same metamodel registration
   * shape used by the platform importer.
   *
   * @throws Exception when EMF registration, loading, or resource parsing fails
   */
  @Test
  void rawEmfLoadOfClimateReliefSample() throws Exception {
    Path sample =
        Path.of("..", "..", "..", "mde", "samples", "cim.xmi").normalize().toAbsolutePath();
    Path metamodel =
        Path.of("..", "..", "..", "mde", "metamodels", "cim", "cim-combined.ecore")
            .normalize()
            .toAbsolutePath();

    Resource.Factory.Registry.INSTANCE
        .getExtensionToFactoryMap()
        .put("ecore", new EcoreResourceFactoryImpl());
    Resource.Factory.Registry.INSTANCE
        .getExtensionToFactoryMap()
        .put("xmi", new XMIResourceFactoryImpl());

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

    Resource ecore = resourceSet.getResource(URI.createFileURI(metamodel.toString()), true);
    ecore.getContents().stream()
        .filter(EPackage.class::isInstance)
        .map(EPackage.class::cast)
        .forEach(pkg -> registerPackage(resourceSet, pkg));

    Resource resource = resourceSet.getResource(URI.createFileURI(sample.toString()), true);
    resource.load(Map.of());
    assertFalse(resource.getContents().isEmpty());
  }

  /**
   * Adds stale duplicate information items to the top-level CIM array to emulate legacy JSON
   * persisted before import de-duplication.
   *
   * @param model mutable imported CIM JSON model
   */
  private void duplicateInformationItemsUnderAddress(ObjectNode model) {
    ObjectNode address = informationItem(model, "info-address");
    ArrayNode topLevelItems = (ArrayNode) model.path("informationItems");
    // These duplicates intentionally mirror nested children that already exist under Address.
    topLevelItems.add(informationItem(address, "info-postcode").deepCopy());
    topLevelItems.add(informationItem(address, "info-location-reference").deepCopy());
  }

  /**
   * Asserts that validation did not report missing trace link endpoints.
   *
   * @param validation validation result to inspect
   */
  private void assertNoTraceLinkEndpointErrors(ModelService.ValidationResult validation) {
    assertFalse(
        validation.issues().stream()
            .anyMatch(issue -> "TraceLinkHasReferenceOrExternalId".equals(issue.constraint())));
  }

  /**
   * Removes trace link endpoint attributes from XMI while leaving the links themselves intact.
   *
   * @param xmi source XMI text
   * @return XMI text with {@code source} and {@code target} attributes removed from links
   */
  private String removeTraceLinkEndpoints(String xmi) {
    return xmi.replaceAll("(<links[^>]*?)\\s+source=\"[^\"]*\"", "$1")
        .replaceAll("(<links[^>]*?)\\s+target=\"[^\"]*\"", "$1");
  }

  /**
   * Finds an information item by ID under either top-level or nested item containments.
   *
   * @param owner JSON object that may contain {@code informationItems} or {@code subItems}
   * @param id expected information item ID
   * @return matching information item object
   */
  private ObjectNode informationItem(JsonNode owner, String id) {
    for (JsonNode item : owner.path("informationItems")) {
      if (id.equals(item.path("id").asText())) {
        return (ObjectNode) item;
      }
    }
    for (JsonNode item : owner.path("subItems")) {
      if (id.equals(item.path("id").asText())) {
        return (ObjectNode) item;
      }
    }
    throw new AssertionError("Missing information item: " + id);
  }

  /**
   * Registers an EPackage tree by namespace URI for XMI loading.
   *
   * @param resourceSet EMF resource set receiving registrations
   * @param ePackage root package to register recursively
   */
  private void registerPackage(ResourceSet resourceSet, EPackage ePackage) {
    if (ePackage.getNsURI() != null && !ePackage.getNsURI().isBlank()) {
      resourceSet.getPackageRegistry().put(ePackage.getNsURI(), ePackage);
    }
    ePackage.getESubpackages().forEach(child -> registerPackage(resourceSet, child));
  }
}
