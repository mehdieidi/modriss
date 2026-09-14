package io.mehdieidi.modriss.mde.etl;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import io.mehdieidi.modriss.mde.validation.EpsilonEvlValidator;
import io.mehdieidi.modriss.mde.validation.EvlValidationReport;
import io.mehdieidi.modriss.mde.validation.EvlValidationRequest;
import io.mehdieidi.modriss.mde.validation.EvlValidationStatus;
import io.mehdieidi.modriss.mde.validation.FileEvlModelConfiguration;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
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
import org.junit.jupiter.api.parallel.ResourceLock;

/**
 * Regression tests for the CIM-to-PIM ETL profile using both repository samples and synthetic EMF
 * fixtures.
 */
@ResourceLock("epsilon-runtime")
final class CimToPimEtlRegressionTest {

  /** Repository root discovered from the current test working directory. */
  private static final Path REPOSITORY_ROOT = findRepositoryRoot();

  /** Temporary directory for generated CIM and PIM XMI fixtures. */
  @TempDir Path tempDir;

  /**
   * Locates the repository root by walking upward to the MDE directories used by ETL regression
   * fixtures.
   *
   * @return normalized repository root path
   */
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

  /**
   * Runs CIM-to-PIM against a representative order-management CIM fixture and verifies the
   * generated PIM contains the main semantic projections.
   *
   * @throws Exception when fixture creation, ETL execution, or model loading fails
   */
  @Test
  void executesCimToPimTransformationForRepresentativeBusinessModel() throws Exception {
    Path cimMetamodel = REPOSITORY_ROOT.resolve("mde/metamodels/cim/cim-combined.ecore");
    Path pimMetamodel = REPOSITORY_ROOT.resolve("mde/metamodels/pim/pim-combined.ecore");
    Path cimModel = tempDir.resolve("order-cim.xmi");
    Path pimModel = tempDir.resolve("order-pim.xmi");

    createRepresentativeCimModel(cimMetamodel, cimModel);

    EtlExecutionRequest request =
        new EtlExecutionRequest(
            REPOSITORY_ROOT.resolve("mde/transformations/cim-to-pim/cim-to-pim.etl"),
            REPOSITORY_ROOT.resolve("mde/transformations/cim-to-pim"),
            List.of(
                EtlModelConfiguration.source(
                    "CIM", CimToPimDefaults.SOURCE_ALIASES, cimModel, List.of(cimMetamodel)),
                EtlModelConfiguration.target(
                    "PIM",
                    CimToPimDefaults.TARGET_ALIASES,
                    pimModel,
                    List.of(pimMetamodel),
                    false)),
            true,
            true);

    EtlExecutionReport report = executeOrFail(request);

    assertEquals(EtlExecutionStatus.SUCCEEDED, report.status(), report.diagnostics().toString());
    assertTrue(Files.isRegularFile(pimModel), "The transformation should persist a PIM model.");

    Resource pimResource = loadModel(pimMetamodel, pimModel);
    EObject root = pimResource.getContents().get(0);
    assertEquals("PIMModel", root.eClass().getName());
    assertFalse(values(root, "services").isEmpty(), "Expected generated services.");
    assertFalse(
        serviceDeployables(root, "functions").isEmpty(), "Expected command/query functions.");
    assertFalse(
        serviceDeployables(root, "apis").isEmpty(), "Expected an API for user-facing behavior.");
    assertFalse(values(root, "eventTypes").isEmpty(), "Expected business event types.");
    assertFalse(serviceDeployables(root, "stores").isEmpty(), "Expected aggregate data stores.");
    assertFalse(values(root, "deploymentUnits").isEmpty(), "Expected deployment units.");

    EObject readiness = reference(root, "readiness");
    assertTrue(readiness != null, "Expected readiness assessment.");
    assertFalse(
        values(readiness, "manualDecisions").isEmpty(),
        "Runtime/language review decision should be generated.");
  }

  /**
   * Verifies that relationship cardinalities are preserved on both contract and storage
   * projections.
   *
   * @throws Exception when fixture creation, ETL execution, or model loading fails
   */
  @Test
  void preservesRelationshipTargetMultiplicityAcrossPimProjections() throws Exception {
    Path cimMetamodel = REPOSITORY_ROOT.resolve("mde/metamodels/cim/cim-combined.ecore");
    Path pimMetamodel = REPOSITORY_ROOT.resolve("mde/metamodels/pim/pim-combined.ecore");
    Path cimModel = tempDir.resolve("relationship-cardinality-cim.xmi");
    Path pimModel = tempDir.resolve("relationship-cardinality-pim.xmi");

    createCoverageCimModel(cimMetamodel, cimModel);
    executeOrFail(CimToPimDefaults.request(REPOSITORY_ROOT, cimModel, pimModel, true, true));

    EObject root = loadModel(pimMetamodel, pimModel).getContents().get(0);
    EObject traceModel = reference(root, "traceModel");
    assertRelationshipProjection(traceModel, "rel_product_inventory", false, true, "ARRAY");
    assertRelationshipProjection(traceModel, "rel_product_inventory_required", true, true, "ARRAY");
    assertRelationshipProjection(traceModel, "rel_product_inventory_single", true, false, "OBJECT");
  }

  /**
   * Asserts requiredness and cardinality for the contract and storage projections of a relation.
   */
  private void assertRelationshipProjection(
      EObject traceModel,
      String sourceElementId,
      boolean required,
      boolean array,
      String fieldType) {
    EObject contractField =
        values(traceModel, "links").stream()
            .filter(link -> sourceElementId.equals(get(link, "sourceElementId")))
            .filter(link -> "TR-040".equals(get(link, "transformationRule")))
            .map(link -> reference(link, "target"))
            .findFirst()
            .orElseThrow(() -> new AssertionError("Missing contract field for " + sourceElementId));
    assertEquals(required, get(contractField, "required"));
    assertEquals(array, get(contractField, "array"));

    EObject storageField =
        values(traceModel, "links").stream()
            .filter(link -> sourceElementId.equals(get(link, "sourceElementId")))
            .filter(link -> "TR-050".equals(get(link, "transformationRule")))
            .map(link -> reference(link, "target"))
            .findFirst()
            .orElseThrow(() -> new AssertionError("Missing storage field for " + sourceElementId));
    assertEquals(fieldType, enumLabel(get(storageField, "fieldType")));
    assertEquals(required, get(storageField, "required"));
  }

  /**
   * Runs the default profile against the canonical climate-relief sample and verifies spec-complete
   * PIM shape plus EVL semantic validity.
   *
   * @throws Exception when ETL execution, validation, or model loading fails
   */
  @Test
  void transformsClimateReliefSampleThroughDefaultProfileWithSpecCompletenessShape()
      throws Exception {
    Path pimMetamodel = REPOSITORY_ROOT.resolve("mde/metamodels/pim/pim-combined.ecore");
    Path sampleModel = REPOSITORY_ROOT.resolve("mde/samples/cim.xmi");
    Path pimModel = tempDir.resolve("climate-relief-grants-pim.xmi");

    EtlExecutionReport report =
        executeOrFail(CimToPimDefaults.request(REPOSITORY_ROOT, sampleModel, pimModel, true, true));

    assertEquals(EtlExecutionStatus.SUCCEEDED, report.status(), report.diagnostics().toString());
    assertTrue(Files.isRegularFile(pimModel), "The default profile should persist a PIM model.");

    EObject root = loadModel(pimMetamodel, pimModel).getContents().get(0);
    assertEquals("PIMModel", root.eClass().getName());
    EObject lifecycleWorkflow =
        allObjects(root).stream()
            .filter(object -> "Workflow".equals(object.eClass().getName()))
            .filter(object -> "Emergency Grant Case Lifecycle Workflow".equals(get(object, "name")))
            .findFirst()
            .orElseThrow(() -> new AssertionError("Missing generated case lifecycle workflow."));
    EObject conditionalHumanTransition =
        first(
            values(lifecycleWorkflow, "transitions"),
            "WorkflowTransition",
            "Decision to Human Review");
    EObject conditionalWaitTransition =
        first(values(lifecycleWorkflow, "transitions"), "WorkflowTransition", "Decision to Wait");
    assertFalse(
        Boolean.TRUE.equals(get(conditionalHumanTransition, "defaultTransition")),
        "A transition with a CIM conditionRef must not become the PIM default branch.");
    assertFalse(
        Boolean.TRUE.equals(get(conditionalWaitTransition, "defaultTransition")),
        "A transition with a CIM conditionRef must not become the PIM default branch.");
    assertEquals(
        "incomeBandEligible and locationEligible and completenessSatisfied",
        get(conditionalHumanTransition, "conditionExpression"));
    assertEquals("missingEvidenceCount > 0", get(conditionalWaitTransition, "conditionExpression"));
    assertEquals(
        2,
        values(root, "services").size(),
        "The sample's two bounded contexts should become two services.");
    assertEquals(
        3,
        values(root, "environments").size(),
        "The default dev/test/prod environments should be generated.");
    assertTrue(
        reference(root, "implementationProfile") != null,
        "Expected an implementation profile review artifact.");
    assertTrue(reference(root, "traceModel") != null, "Expected a trace model.");
    assertFalse(
        values(reference(root, "traceModel"), "links").isEmpty(),
        "Expected generated trace links.");

    assertFalse(serviceDeployables(root, "functions").isEmpty(), "Expected generated functions.");
    assertTrue(
        serviceDeployables(root, "functions").stream()
            .allMatch(function -> reference(function, "contract") != null),
        "Every generated function should have a function contract.");
    assertFalse(serviceDeployables(root, "apis").isEmpty(), "Expected generated APIs.");
    assertTrue(
        serviceDeployables(root, "apis").stream().allMatch(api -> !values(api, "routes").isEmpty()),
        "Every generated API should have at least one route.");
    assertFalse(values(root, "eventTypes").isEmpty(), "Expected generated event types.");
    assertTrue(
        values(root, "eventTypes").stream()
            .allMatch(
                eventType ->
                    reference(eventType, "schema") != null
                        && reference(eventType, "envelope") != null),
        "Every generated event type should have a schema and envelope.");
    assertFalse(serviceDeployables(root, "channels").isEmpty(), "Expected generated channels.");
    assertFalse(serviceDeployables(root, "stores").isEmpty(), "Expected generated data stores.");
    assertTrue(
        serviceDeployables(root, "stores").stream()
            .allMatch(
                store ->
                    !values(store, "ownedDataModels").isEmpty()
                        && !values(store, "accessPatterns").isEmpty()),
        "Every generated data store should have data models and access patterns.");
    assertFalse(serviceDeployables(root, "workflows").isEmpty(), "Expected generated workflows.");
    assertFalse(
        serviceDeployables(root, "adapters").isEmpty(), "Expected generated external adapters.");
    assertFalse(
        values(root, "policies").isEmpty(),
        "Expected generated governance, security, resilience, and data policies.");
    assertFalse(
        values(root, "businessRules").isEmpty(),
        "Expected requirements, goals, and glossary terms to be retained as business rules.");
    assertFalse(
        values(root, "decisionModels").isEmpty(),
        "Expected CIM decision tables to be retained as PIM decision models.");
    assertTrue(
        values(root, "deploymentUnits").stream()
            .allMatch(unit -> !values(unit, "contains").isEmpty()),
        "Every deployment unit should contain deployable elements.");

    EObject readiness = reference(root, "readiness");
    assertTrue(readiness != null, "Expected readiness assessment.");
    assertFalse(
        values(readiness, "manualDecisions").isEmpty(),
        "The sample's open transformation decisions should remain visible.");
    assertFalse(
        values(readiness, "findings").isEmpty(),
        "The sample's risks and assumptions should become readiness findings.");
    String persisted = Files.readString(pimModel);
    assertFalse(
        persisted.contains("https://modriss.org/cim/"),
        "Generated PIM XMI must be importable with only the PIM metamodel registered.");

    EvlValidationReport validation =
        new EpsilonEvlValidator()
            .validate(
                EvlValidationRequest.forRoot(
                    REPOSITORY_ROOT.resolve("mde/validation/pim/pim-semantic-validation.evl"),
                    List.of(
                        FileEvlModelConfiguration.readOnly(
                            "PIM", List.of("KERNEL"), pimModel, List.of(pimMetamodel))),
                    true));
    assertEquals(
        EvlValidationStatus.SUCCEEDED, validation.status(), validation.diagnostics().toString());
    assertTrue(
        validation.violations().isEmpty(),
        "Generated climate-relief PIM should pass semantic validation: " + validation.violations());
  }

  /**
   * Exercises cross-boundary dependencies, protected queries, enum schema fields, external
   * interactions, policy derivation, and manual review backlog rules.
   *
   * @throws Exception when fixture creation, ETL execution, or model loading fails
   */
  @Test
  void coversCrossBoundaryRelationshipAndReviewBacklogRules() throws Exception {
    Path cimMetamodel = REPOSITORY_ROOT.resolve("mde/metamodels/cim/cim-combined.ecore");
    Path pimMetamodel = REPOSITORY_ROOT.resolve("mde/metamodels/pim/pim-combined.ecore");
    Path cimModel = tempDir.resolve("coverage-cim.xmi");
    Path pimModel = tempDir.resolve("coverage-pim.xmi");

    createCoverageCimModel(cimMetamodel, cimModel);

    EtlExecutionRequest request =
        new EtlExecutionRequest(
            REPOSITORY_ROOT.resolve("mde/transformations/cim-to-pim/cim-to-pim.etl"),
            REPOSITORY_ROOT.resolve("mde/transformations/cim-to-pim"),
            List.of(
                EtlModelConfiguration.source(
                    "CIM", CimToPimDefaults.SOURCE_ALIASES, cimModel, List.of(cimMetamodel)),
                EtlModelConfiguration.target(
                    "PIM",
                    CimToPimDefaults.TARGET_ALIASES,
                    pimModel,
                    List.of(pimMetamodel),
                    false)),
            true,
            true);

    executeOrFail(request);

    EObject root = loadModel(pimMetamodel, pimModel).getContents().get(0);
    assertTrue(
        serviceDeployables(root, "channels").stream()
            .anyMatch(channel -> "Queue".equals(channel.eClass().getName())),
        "Cross-service capability dependency should create a provider-independent queue.");
    assertTrue(
        values(root, "flows").stream()
            .anyMatch(flow -> "MessageFlow".equals(flow.eClass().getName())),
        "Cross-service capability dependency should create message flows.");
    assertTrue(
        values(root, "policies").stream()
            .anyMatch(
                policy ->
                    "AuthorizationPolicy".equals(policy.eClass().getName())
                        && "Check Product Authorization".equals(get(policy, "name"))),
        "Protected queries should receive authorization policy coverage.");
    assertTrue(
        values(root, "schemas").stream()
            .flatMap(schema -> values(schema, "fields").stream())
            .anyMatch(field -> !values(field, "enumValues").isEmpty()),
        "Enumeration information items should become schema enum literals.");
    assertTrue(
        serviceDeployables(root, "stores").stream()
            .flatMap(store -> values(store, "ownedDataModels").stream())
            .flatMap(model -> values(model, "storageFields").stream())
            .anyMatch(field -> "inventory".equals(get(field, "name"))),
        "Domain relationships should be represented in data model repository fields.");
    assertTrue(
        values(root, "flows").stream()
            .anyMatch(flow -> "ExternalIntegrationFlow".equals(flow.eClass().getName())),
        "External process steps should create external integration flows.");
    assertTrue(
        values(root, "policies").stream()
            .anyMatch(policy -> "ConcurrencyPolicy".equals(policy.eClass().getName())),
        "Performance/scalability NFRs should create concurrency policies.");
    assertTrue(
        values(root, "policies").stream()
            .anyMatch(policy -> "RateLimitPolicy".equals(policy.eClass().getName())),
        "Performance/scalability NFRs should create rate limit policies.");
    assertTrue(
        values(root, "policies").stream()
            .anyMatch(policy -> "CachePolicy".equals(policy.eClass().getName())),
        "Performance/scalability NFRs should create cache policies.");
    assertTrue(
        values(root, "policies").stream()
            .anyMatch(
                policy ->
                    "TimeoutPolicy".equals(policy.eClass().getName())
                        && Integer.valueOf(300).equals(get(policy, "timeoutSeconds"))),
        "Parseable temporal constraints should set timeout seconds.");
    assertTrue(
        serviceDeployables(root, "stores").stream()
            .anyMatch(store -> !values(store, "dataProtectionPolicies").isEmpty()),
        "Classified data in stores should receive data protection policy attachment.");

    EObject readiness = reference(root, "readiness");
    assertTrue(
        values(readiness, "manualDecisions").stream()
            .anyMatch(
                decision ->
                    get(decision, "question")
                        .toString()
                        .contains("critical capability dependency")),
        "Critical capability dependencies should remain an explicit manual review item.");
    assertTrue(
        values(readiness, "manualDecisions").stream()
            .anyMatch(
                decision ->
                    get(decision, "question").toString().contains("Confirm authorization policy")),
        "Missing query authorization rule should remain an explicit manual decision.");
  }

  /**
   * Verifies that the transformation remains useful for an underspecified CIM by generating an
   * explicit placeholder service, manual completion function, deployment unit, and blocking
   * readiness decisions instead of silently producing an empty PIM.
   *
   * @throws Exception when fixture creation, ETL execution, or model loading fails
   */
  @Test
  void createsReviewablePlaceholderArtifactsForCimWithoutServiceBoundaries() throws Exception {
    Path cimMetamodel = REPOSITORY_ROOT.resolve("mde/metamodels/cim/cim-combined.ecore");
    Path pimMetamodel = REPOSITORY_ROOT.resolve("mde/metamodels/pim/pim-combined.ecore");
    Path cimModel = tempDir.resolve("minimal-cim.xmi");
    Path pimModel = tempDir.resolve("minimal-pim.xmi");

    createMinimalCimModel(cimMetamodel, cimModel);

    executeOrFail(CimToPimDefaults.request(REPOSITORY_ROOT, cimModel, pimModel, true, true));

    EObject root = loadModel(pimMetamodel, pimModel).getContents().get(0);
    assertEquals(1, values(root, "services").size(), "Expected one generated placeholder service.");
    EObject service = values(root, "services").get(0);
    assertEquals("svc_default_review_required", get(service, "id"));
    assertEquals("CAPABILITY_BASED", enumLabel(get(service, "boundaryType")));
    assertEquals("INCOMPLETE", enumLabel(get(service, "lifecycleStatus")));

    List<EObject> functions = serviceDeployables(root, "functions");
    assertEquals(1, functions.size(), "Expected the manual completion function.");
    assertEquals("fn_tbd_manual_completion", get(functions.get(0), "id"));
    assertEquals("MAINTENANCE_TASK", enumLabel(get(functions.get(0), "functionKind")));

    assertEquals(1, values(root, "deploymentUnits").size());
    assertTrue(
        values(values(root, "deploymentUnits").get(0), "contains").contains(functions.get(0)),
        "The deployment unit should contain the manual completion function.");

    EObject readiness = reference(root, "readiness");
    assertTrue(
        values(readiness, "manualDecisions").stream()
            .anyMatch(
                decision ->
                    Boolean.TRUE.equals(get(decision, "blocking"))
                        && get(decision, "question")
                            .toString()
                            .contains("Define service boundaries")),
        "Missing service boundaries should be a blocking manual decision.");
    assertTrue(
        values(readiness, "manualDecisions").stream()
            .anyMatch(
                decision ->
                    Boolean.TRUE.equals(get(decision, "blocking"))
                        && get(decision, "question")
                            .toString()
                            .contains("Add at least one executable")),
        "Missing executable behavior should be a blocking manual decision.");
  }

  /**
   * Uses the canonical sample and the synthetic branch fixture as rule-family sentinels. The test
   * asserts that every CIM-to-PIM TR family has trace/readiness evidence and that the generated PIM
   * contains the semantic target classes produced by the ETL rule set.
   *
   * @throws Exception when ETL execution or model loading fails
   */
  @Test
  void generatedOutputsCoverEveryCimToPimTransformationRuleFamily() throws Exception {
    Path pimMetamodel = REPOSITORY_ROOT.resolve("mde/metamodels/pim/pim-combined.ecore");
    Path samplePim = tempDir.resolve("sample-rule-family-coverage.pim.xmi");
    executeOrFail(
        CimToPimDefaults.request(
            REPOSITORY_ROOT,
            REPOSITORY_ROOT.resolve("mde/samples/cim.xmi"),
            samplePim,
            true,
            true));

    Path cimMetamodel = REPOSITORY_ROOT.resolve("mde/metamodels/cim/cim-combined.ecore");
    Path branchCim = tempDir.resolve("branch-rule-family-coverage.cim.xmi");
    Path branchPim = tempDir.resolve("branch-rule-family-coverage.pim.xmi");
    createCoverageCimModel(cimMetamodel, branchCim);
    executeOrFail(CimToPimDefaults.request(REPOSITORY_ROOT, branchCim, branchPim, true, true));

    EObject sampleRoot = loadModel(pimMetamodel, samplePim).getContents().get(0);
    EObject branchRoot = loadModel(pimMetamodel, branchPim).getContents().get(0);
    List<EObject> generated = new ArrayList<>();
    generated.addAll(allObjects(sampleRoot));
    generated.addAll(allObjects(branchRoot));

    for (String ruleId :
        List.of(
            "TR-001", "TR-003", "TR-004", "TR-010", "TR-020", "TR-030", "TR-040", "TR-050",
            "TR-060", "TR-070", "TR-080", "TR-090", "TR-100", "TR-110", "TR-120", "TR-130",
            "TR-140", "TR-150", "TR-160", "TR-170")) {
      assertTrue(
          generated.stream().anyMatch(element -> hasRuleEvidence(element, ruleId)),
          "Expected generated trace/readiness evidence for " + ruleId);
    }

    for (String className :
        List.of(
            "PIMModel",
            "Environment",
            "ImplementationProfile",
            "BusinessRule",
            "ReadinessCheck",
            "ManualDecision",
            "ServerlessService",
            "Principal",
            "IdentityProvider",
            "ExternalAdapter",
            "ExternalEndpoint",
            "Schema",
            "SchemaField",
            "SchemaEnumLiteral",
            "DataStore",
            "DataModel",
            "DataField",
            "AccessPattern",
            "IndexCandidate",
            "DataProtectionPolicy",
            "RetentionPolicy",
            "BackupPolicy",
            "CompliancePolicy",
            "EventType",
            "EventEnvelope",
            "Function",
            "FunctionContract",
            "Api",
            "ApiRoute",
            "Workflow",
            "StartStep",
            "SuccessEndStep",
            "TaskStep",
            "WorkflowTransition",
            "TimeoutPolicy",
            "OrderingPolicy",
            "ResiliencePolicy",
            "ConcurrencyPolicy",
            "RateLimitPolicy",
            "CachePolicy",
            "ObservabilityConfig",
            "RequestResponseFlow",
            "MessageFlow",
            "ExternalIntegrationFlow",
            "DeploymentUnit",
            "ConfigurationSet",
            "ServiceElementMembership",
            "ReadinessFinding",
            "TraceLink")) {
      assertTrue(
          generated.stream().anyMatch(element -> className.equals(element.eClass().getName())),
          "Expected generated PIM class " + className);
    }
  }

  /**
   * Verifies the shared EOL helper libraries through their observable generated PIM semantics:
   * naming, type mapping, schema builders, trace/readiness evidence, service resolution, ownership,
   * idempotency, observability, resilience, and deployment/configuration helpers.
   *
   * @throws Exception when fixture creation, ETL execution, or model loading fails
   */
  @Test
  void eolHelperLibrariesProduceExpectedSemanticArtifacts() throws Exception {
    Path cimMetamodel = REPOSITORY_ROOT.resolve("mde/metamodels/cim/cim-combined.ecore");
    Path pimMetamodel = REPOSITORY_ROOT.resolve("mde/metamodels/pim/pim-combined.ecore");
    Path cimModel = tempDir.resolve("helper-semantics-cim.xmi");
    Path pimModel = tempDir.resolve("helper-semantics-pim.xmi");

    createRepresentativeCimModel(cimMetamodel, cimModel);
    executeOrFail(CimToPimDefaults.request(REPOSITORY_ROOT, cimModel, pimModel, true, true));

    EObject root = loadModel(pimMetamodel, pimModel).getContents().get(0);
    List<EObject> generated = allObjects(root);

    EObject service = single(root, "services", "ServerlessService", "Order Management Service");
    assertEquals("cap_order_management", get(service, "businessCapabilityRef"));
    assertTrue(Boolean.TRUE.equals(get(service, "externallyExposed")));
    assertTrue(Boolean.TRUE.equals(get(service, "ownsData")));

    EObject commandFunction = first(generated, "Function", "Place Order Handler");
    assertEquals("PlaceOrderHandler", get(commandFunction, "sourceNameSuggestion"));
    assertEquals("COMMAND_HANDLER", enumLabel(get(commandFunction, "functionKind")));
    assertEquals("REQUEST_RESPONSE", enumLabel(get(commandFunction, "executionModel")));
    assertEquals("LIGHTWEIGHT", enumLabel(get(commandFunction, "computeProfile")));
    assertTrue(Boolean.TRUE.equals(get(commandFunction, "requiresIdempotency")));
    assertTrue(values(service, "functions").contains(commandFunction));

    EObject commandContract = reference(commandFunction, "contract");
    EObject commandRequest = reference(commandContract, "inputSchema");
    assertTrue(fieldNames(commandRequest).containsAll(List.of("customerId", "cartId")));
    assertTrue(fieldNames(commandRequest).containsAll(List.of("correlationId", "causationId")));
    assertTrue(fieldNames(commandRequest).contains("idempotencyKey"));
    assertEquals("idempotencyKey", get(commandContract, "idempotencyKeyField"));

    EObject idempotency = reference(commandFunction, "idempotency");
    assertEquals("IdempotencyPolicy", idempotency.eClass().getName());
    assertEquals("idempotencyKey", get(idempotency, "keySource"));
    assertTrue(Boolean.TRUE.equals(get(idempotency, "storeRequired")));

    EObject commandRoute = first(generated, "ApiRoute", "Place Order Route");
    assertEquals("POST", enumLabel(get(commandRoute, "method")));
    assertEquals("/orders/place-order", get(commandRoute, "pathTemplate"));
    assertEquals("placeOrder", get(commandRoute, "operationId"));
    assertEquals(Integer.valueOf(200), get(commandRoute, "expectedSuccessStatus"));
    assertEquals(commandFunction, reference(commandRoute, "functionIntegration"));
    assertTrue(Boolean.TRUE.equals(get(commandRoute, "authRequired")));

    EObject queryFunction = first(generated, "Function", "Get Order Status Query Handler");
    EObject queryRoute = first(generated, "ApiRoute", "Get Order Status Route");
    assertEquals("GET", enumLabel(get(queryRoute, "method")));
    assertEquals("/orders/{id}", get(queryRoute, "pathTemplate"));
    assertEquals(queryFunction, reference(queryRoute, "functionIntegration"));

    EObject orderSchema = first(generated, "Schema", "Order Schema");
    EObject orderIdField = field(orderSchema, "orderId");
    assertEquals("UUID", enumLabel(get(orderIdField, "fieldType")));
    assertEquals("uuid", get(orderIdField, "format"));

    EObject eventType = first(generated, "EventType", "OrderPlaced");
    assertEquals("OrderPlaced", get(eventType, "semanticName"));
    assertEquals("customerId", get(eventType, "subjectExpression"));
    assertTrue(Boolean.TRUE.equals(get(eventType, "replayable")));
    assertTrue(
        fieldNames(reference(eventType, "schema"))
            .containsAll(
                List.of(
                    "eventId",
                    "eventType",
                    "source",
                    "time",
                    "version",
                    "correlationId",
                    "causationId",
                    "subject")));

    EObject store = first(generated, "DataStore", "Order Aggregate Store");
    assertTrue(values(service, "stores").contains(store));
    EObject dataModel = values(store, "ownedDataModels").get(0);
    EObject orderIdDataField = first(values(dataModel, "storageFields"), "DataField", "orderId");
    assertEquals("UUID", enumLabel(get(orderIdDataField, "fieldType")));
    assertTrue(Boolean.TRUE.equals(get(orderIdDataField, "identifier")));
    assertTrue(Boolean.TRUE.equals(get(orderIdDataField, "partitionKeyCandidate")));
    assertTrue(
        values(store, "accessPatterns").stream()
            .anyMatch(ap -> "orderId".equals(get(ap, "queryBy"))));
    assertTrue(
        values(store, "indexCandidates").stream()
            .anyMatch(ix -> "orderId".equals(get(ix, "partitionKeyField"))));
    assertTrue(reference(store, "retentionPolicy") != null);
    assertTrue(reference(store, "backupPolicy") != null);

    EObject api = values(service, "apis").get(0);
    assertEquals("/order-management", get(api, "basePath"));
    assertTrue(values(api, "routes").contains(commandRoute));
    assertTrue(values(api, "routes").contains(queryRoute));

    EObject deploymentUnit = values(root, "deploymentUnits").get(0);
    assertTrue(values(deploymentUnit, "contains").contains(commandFunction));
    assertTrue(values(deploymentUnit, "contains").contains(api));
    assertTrue(values(deploymentUnit, "contains").contains(store));

    EObject configuration =
        first(generated, "ConfigurationSet", "Order Management Service Configuration");
    assertTrue(
        configParameterNames(configuration)
            .containsAll(List.of("LOG_LEVEL", "CORRELATION_ID_NAME")));

    EObject traceModel = reference(root, "traceModel");
    assertTrue(
        values(traceModel, "links").stream()
            .anyMatch(link -> "TR-070".equals(get(link, "transformationRule"))));
    assertTrue(
        values(traceModel, "links").stream()
            .anyMatch(link -> commandFunction.equals(reference(link, "target"))));

    EObject readiness = reference(root, "readiness");
    assertTrue(
        values(readiness, "checks").stream()
            .anyMatch(check -> "CIM_HAS_AT_LEAST_ONE_BEHAVIOR".equals(get(check, "checkId"))));
    assertTrue(
        generated.stream()
            .filter(element -> "ObservabilityConfig".equals(element.eClass().getName()))
            .anyMatch(config -> "correlationId".equals(get(config, "correlationIdField"))));
    assertTrue(
        generated.stream()
            .anyMatch(element -> "ResiliencePolicy".equals(element.eClass().getName())));
    assertTrue(
        generated.stream()
            .anyMatch(element -> "RequestResponseFlow".equals(element.eClass().getName())));
    assertTrue(
        values(root, "serviceMemberships").stream()
            .anyMatch(membership -> commandFunction.equals(reference(membership, "element"))));
  }

  /**
   * Verifies malformed ETL input yields structured validation or parse diagnostics instead of an
   * unreported runner failure.
   *
   * @throws IOException when the broken ETL fixture cannot be written
   */
  @Test
  void reportsParseDiagnosticsForBrokenEtl() throws IOException {
    Path broken = tempDir.resolve("broken.etl");
    Files.writeString(
        broken, "rule Broken transform x : Missing!Type to y : Missing!Type { if ( }");
    EtlExecutionRequest request =
        new EtlExecutionRequest(
            broken,
            tempDir,
            List.of(
                EtlModelConfiguration.source(
                    "IN",
                    List.of("IN"),
                    tempDir.resolve("missing.xmi"),
                    List.of(tempDir.resolve("missing.ecore")))),
            false,
            true);

    EtlExecutionException exception =
        assertDoesNotThrow(
            () -> {
              try {
                new EpsilonEtlExecutor().execute(request);
              } catch (EtlExecutionException ex) {
                return ex;
              }
              throw new AssertionError("Expected ETL execution to fail.");
            });

    assertTrue(
        exception.getReport().diagnostics().stream()
            .anyMatch(
                d -> d.phase() == ExecutionPhase.VALIDATION || d.phase() == ExecutionPhase.PARSE));
  }

  /**
   * Executes an ETL request and fails the test with diagnostics on failure.
   *
   * @param request ETL execution request
   * @return successful execution report
   */
  private EtlExecutionReport executeOrFail(EtlExecutionRequest request) {
    try {
      return new EpsilonEtlExecutor().execute(request);
    } catch (EtlExecutionException ex) {
      fail("ETL execution failed: " + ex.getReport().diagnostics());
      throw new AssertionError(ex);
    }
  }

  /**
   * Creates a compact order-management CIM model that covers goals, capabilities, actors, aggregate
   * data, commands, queries, and events.
   *
   * @param metamodel combined CIM metamodel path
   * @param modelFile output XMI path
   * @throws IOException when the model cannot be saved
   */
  private void createRepresentativeCimModel(Path metamodel, Path modelFile) throws IOException {
    ResourceSet resourceSet = new ResourceSetImpl();
    resourceSet
        .getResourceFactoryRegistry()
        .getExtensionToFactoryMap()
        .put("ecore", new EcoreResourceFactoryImpl());
    resourceSet
        .getResourceFactoryRegistry()
        .getExtensionToFactoryMap()
        .put("xmi", new XMIResourceFactoryImpl());
    Resource metamodelResource =
        resourceSet.getResource(URI.createFileURI(metamodel.toString()), true);
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
    set(capability, "criticality", enumValue(metamodelResource, "CapabilityCriticality", "CORE"));
    add(capability, "supports", goal);

    EObject actor = create(metamodelResource, "Actor");
    set(actor, "id", "actor_customer");
    set(actor, "name", "Customer");
    set(actor, "actorType", enumValue(metamodelResource, "ActorType", "HUMAN"));
    set(actor, "trustLevel", enumValue(metamodelResource, "TrustLevel", "PARTIALLY_TRUSTED"));
    set(actor, "authenticationExpectation", "Authenticated customer account.");

    EObject orderId =
        informationItem(metamodelResource, "item_order_id", "orderId", "IDENTIFIER", true);
    set(orderId, "formatHint", "uuid");
    EObject customerId =
        informationItem(metamodelResource, "item_customer_id", "customerId", "IDENTIFIER", true);
    EObject cartId =
        informationItem(metamodelResource, "item_cart_id", "cartId", "IDENTIFIER", true);
    EObject status =
        informationItem(metamodelResource, "item_order_status", "status", "TEXT", true);
    EObject total =
        informationItem(metamodelResource, "item_total_amount", "totalAmount", "MONEY", true);

    EObject entity = create(metamodelResource, "DomainEntity");
    set(entity, "id", "entity_order");
    set(entity, "name", "Order");
    set(entity, "identityDescription", "Order identity.");
    set(entity, "auditRelevant", true);
    set(
        entity,
        "identityStrategy",
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
    set(
        aggregate,
        "consistencyExpectation",
        enumValue(metamodelResource, "ConsistencyExpectation", "SINGLE_ENTITY"));

    EObject event = create(metamodelResource, "BusinessEvent");
    set(event, "id", "event_order_placed");
    set(event, "name", "Order Placed");
    set(event, "semanticName", "OrderPlaced");
    set(event, "occurredInPastTenseName", "OrderPlaced");
    set(
        event,
        "eventTimeSemantics",
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
    set(
        command,
        "interactionExpectation",
        enumValue(metamodelResource, "InteractionExpectation", "IMMEDIATE_RESPONSE_EXPECTED"));
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
    set(query, "freshnessNeed", enumValue(metamodelResource, "FreshnessNeed", "NEAR_REAL_TIME"));
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

    Resource modelResource = resourceSet.createResource(URI.createFileURI(modelFile.toString()));
    modelResource.getContents().add(model);
    modelResource.save(null);
  }

  /**
   * Creates a broad CIM fixture for transformation branches not covered by the repository sample,
   * including cross-capability messaging, policy derivation, classifications, domain relationships,
   * and external process steps.
   *
   * @param metamodel combined CIM metamodel path
   * @param modelFile output XMI path
   * @throws IOException when the model cannot be saved
   */
  private void createCoverageCimModel(Path metamodel, Path modelFile) throws IOException {
    ResourceSet resourceSet = new ResourceSetImpl();
    resourceSet
        .getResourceFactoryRegistry()
        .getExtensionToFactoryMap()
        .put("ecore", new EcoreResourceFactoryImpl());
    resourceSet
        .getResourceFactoryRegistry()
        .getExtensionToFactoryMap()
        .put("xmi", new XMIResourceFactoryImpl());
    Resource metamodelResource =
        resourceSet.getResource(URI.createFileURI(metamodel.toString()), true);
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
    set(
        inventoryCap,
        "criticality",
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

    EObject productId =
        informationItem(metamodelResource, "item_product_id", "productId", "IDENTIFIER", true);
    EObject emailClassification = create(metamodelResource, "DataClassification");
    set(emailClassification, "id", "class_partner_email");
    set(emailClassification, "name", "Partner Email Classification");
    set(emailClassification, "kind", enumValue(metamodelResource, "DataKind", "PERSONAL"));
    set(
        emailClassification,
        "identifiability",
        enumValue(metamodelResource, "Identifiability", "DIRECTLY_IDENTIFYING"));
    set(emailClassification, "encryptionExpected", true);
    set(emailClassification, "maskingExpected", true);
    set(emailClassification, "auditAccessRequired", true);
    set(emailClassification, "deletionRightApplies", true);
    set(emailClassification, "classificationRationale", "Partner contact data.");
    EObject sku = informationItem(metamodelResource, "item_sku", "sku", "TEXT", true);
    EObject partnerEmail =
        informationItem(metamodelResource, "item_partner_email", "partnerEmail", "EMAIL", true);
    set(partnerEmail, "classification", emailClassification);
    EObject productStatus =
        informationItem(
            metamodelResource, "item_product_status", "productStatus", "ENUMERATION", true);
    addValue(productStatus, "allowedValues", "ACTIVE");
    addValue(productStatus, "allowedValues", "DISCONTINUED");
    EObject inventoryId =
        informationItem(metamodelResource, "item_inventory_id", "inventoryId", "IDENTIFIER", true);
    EObject available =
        informationItem(metamodelResource, "item_available", "available", "BOOLEAN", true);

    EObject product = create(metamodelResource, "DomainEntity");
    set(product, "id", "entity_product");
    set(product, "name", "Product");
    set(product, "identityDescription", "Product identity.");
    set(product, "auditRelevant", true);
    set(
        product,
        "identityStrategy",
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
    set(
        inventory,
        "identityStrategy",
        enumValue(metamodelResource, "IdentityStrategy", "SURROGATE_KEY"));
    add(inventory, "identityAttributes", inventoryId);
    set(inventory, "primaryIdentityAttribute", inventoryId);
    add(inventory, "attributes", inventoryId);
    add(inventory, "attributes", available);
    set(inventory, "owningCapability", inventoryCap);

    EObject relationship = create(metamodelResource, "DomainRelationship");
    set(relationship, "id", "rel_product_inventory");
    set(relationship, "name", "Product Inventory");
    set(
        relationship,
        "relationshipType",
        enumValue(metamodelResource, "DomainRelationshipType", "ASSOCIATION"));
    set(relationship, "sourceRole", "product");
    set(relationship, "targetRole", "inventory");
    set(
        relationship,
        "sourceMultiplicity",
        multiplicity(metamodelResource, "mult_rel_product_inventory_source", 1, 1, false));
    set(
        relationship,
        "targetMultiplicity",
        multiplicity(metamodelResource, "mult_rel_product_inventory_target", 0, null, true));
    set(relationship, "ownership", false);
    set(relationship, "navigableFromSource", true);
    set(relationship, "navigableFromTarget", false);
    set(relationship, "source", product);
    set(relationship, "target", inventory);

    EObject requiredManyRelationship =
        domainRelationship(
            metamodelResource,
            "rel_product_inventory_required",
            "Required Product Inventory",
            "requiredInventories",
            product,
            inventory,
            1,
            null,
            true);
    EObject singleRelationship =
        domainRelationship(
            metamodelResource,
            "rel_product_inventory_single",
            "Primary Product Inventory",
            "primaryInventory",
            product,
            inventory,
            1,
            1,
            false);

    EObject productAggregate = create(metamodelResource, "AggregateCandidate");
    set(productAggregate, "id", "aggregate_product");
    set(productAggregate, "name", "Product Aggregate");
    set(productAggregate, "root", product);
    add(productAggregate, "members", product);
    set(
        productAggregate,
        "consistencyExpectation",
        enumValue(metamodelResource, "ConsistencyExpectation", "SINGLE_ENTITY"));

    EObject inventoryAggregate = create(metamodelResource, "AggregateCandidate");
    set(inventoryAggregate, "id", "aggregate_inventory");
    set(inventoryAggregate, "name", "Inventory Aggregate");
    set(inventoryAggregate, "root", inventory);
    add(inventoryAggregate, "members", inventory);
    set(
        inventoryAggregate,
        "consistencyExpectation",
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
    set(
        inventoryQuery,
        "freshnessNeed",
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
    set(externalSystem, "actorType", enumValue(metamodelResource, "ActorType", "EXTERNAL_SYSTEM"));
    set(
        externalSystem,
        "trustLevel",
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
    set(externalStep, "stepKind", enumValue(metamodelResource, "StepKind", "EXTERNAL_INTERACTION"));
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
    set(
        process,
        "processKind",
        enumValue(metamodelResource, "ProcessKind", "SAGA_LIKE_BUSINESS_PROCESS"));
    set(
        process,
        "criticality",
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
    set(
        performanceNfr,
        "requirementType",
        enumValue(metamodelResource, "RequirementType", "QUALITY"));
    set(
        performanceNfr,
        "sourceType",
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
    add(model, "relationships", requiredManyRelationship);
    add(model, "relationships", singleRelationship);
    add(model, "aggregates", productAggregate);
    add(model, "aggregates", inventoryAggregate);
    add(model, "queries", query);
    add(model, "queries", inventoryQuery);
    add(model, "processes", process);

    Resource modelResource = resourceSet.createResource(URI.createFileURI(modelFile.toString()));
    modelResource.getContents().add(model);
    modelResource.save(null);
  }

  /**
   * Creates a deliberately underspecified CIM root with no boundaries or executable behavior.
   *
   * @param metamodel combined CIM metamodel path
   * @param modelFile output XMI path
   * @throws IOException when the model cannot be saved
   */
  private void createMinimalCimModel(Path metamodel, Path modelFile) throws IOException {
    ResourceSet resourceSet = new ResourceSetImpl();
    resourceSet
        .getResourceFactoryRegistry()
        .getExtensionToFactoryMap()
        .put("ecore", new EcoreResourceFactoryImpl());
    resourceSet
        .getResourceFactoryRegistry()
        .getExtensionToFactoryMap()
        .put("xmi", new XMIResourceFactoryImpl());
    Resource metamodelResource =
        resourceSet.getResource(URI.createFileURI(metamodel.toString()), true);
    EcoreUtil.resolveAll(resourceSet);
    registerPackages(metamodelResource);

    EObject model = create(metamodelResource, "CIMModel");
    set(model, "id", "cim_minimal_model");
    set(model, "name", "Minimal CIM");
    set(model, "domainName", "Minimal Domain");
    set(model, "businessScope", "No boundaries or behavior yet.");

    Resource modelResource = resourceSet.createResource(URI.createFileURI(modelFile.toString()));
    modelResource.getContents().add(model);
    modelResource.save(null);
  }

  /**
   * Creates an information item with the common required attributes used by the synthetic CIM
   * fixtures.
   *
   * @param metamodelResource loaded CIM metamodel
   * @param id element ID
   * @param name business and display name
   * @param type primitive business type literal
   * @param required whether the item is required
   * @return configured information item
   */
  private EObject informationItem(
      Resource metamodelResource, String id, String name, String type, boolean required) {
    EObject item = create(metamodelResource, "InformationItem");
    set(item, "id", id);
    set(item, "name", name);
    set(item, "businessName", name);
    set(item, "type", enumValue(metamodelResource, "PrimitiveBusinessType", type));
    set(item, "required", required);
    set(item, "collection", false);
    return item;
  }

  /**
   * Creates a CIM multiplicity value object for domain relationship endpoints.
   *
   * @param metamodelResource loaded CIM metamodel
   * @param id element ID
   * @param lowerBound lower multiplicity bound
   * @param upperBound optional upper multiplicity bound
   * @param unbounded whether the multiplicity has no upper bound
   * @return configured multiplicity object
   */
  private EObject multiplicity(
      Resource metamodelResource,
      String id,
      int lowerBound,
      Integer upperBound,
      boolean unbounded) {
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

  /** Creates a source-navigable association with a fixed 1..1 source end. */
  private EObject domainRelationship(
      Resource metamodelResource,
      String id,
      String name,
      String targetRole,
      EObject source,
      EObject target,
      int targetLowerBound,
      Integer targetUpperBound,
      boolean targetUnbounded) {
    EObject relationship = create(metamodelResource, "DomainRelationship");
    set(relationship, "id", id);
    set(relationship, "name", name);
    set(
        relationship,
        "relationshipType",
        enumValue(metamodelResource, "DomainRelationshipType", "ASSOCIATION"));
    set(relationship, "sourceRole", "product");
    set(relationship, "targetRole", targetRole);
    set(
        relationship,
        "sourceMultiplicity",
        multiplicity(metamodelResource, id + "_source", 1, 1, false));
    set(
        relationship,
        "targetMultiplicity",
        multiplicity(
            metamodelResource,
            id + "_target",
            targetLowerBound,
            targetUpperBound,
            targetUnbounded));
    set(relationship, "ownership", false);
    set(relationship, "navigableFromSource", true);
    set(relationship, "navigableFromTarget", false);
    set(relationship, "source", source);
    set(relationship, "target", target);
    return relationship;
  }

  /**
   * Loads an XMI model after registering all packages from the supplied metamodel.
   *
   * @param metamodel combined Ecore metamodel path
   * @param modelFile XMI model path
   * @return loaded model resource
   */
  private Resource loadModel(Path metamodel, Path modelFile) {
    ResourceSet resourceSet = new ResourceSetImpl();
    resourceSet
        .getResourceFactoryRegistry()
        .getExtensionToFactoryMap()
        .put("ecore", new EcoreResourceFactoryImpl());
    resourceSet
        .getResourceFactoryRegistry()
        .getExtensionToFactoryMap()
        .put("xmi", new XMIResourceFactoryImpl());
    Resource metamodelResource =
        resourceSet.getResource(URI.createFileURI(metamodel.toString()), true);
    registerPackages(metamodelResource);
    Resource modelResource = resourceSet.getResource(URI.createFileURI(modelFile.toString()), true);
    EcoreUtil.resolveAll(resourceSet);
    return modelResource;
  }

  /**
   * Registers all root packages contained in a loaded metamodel resource.
   *
   * @param metamodelResource loaded Ecore resource
   */
  private void registerPackages(Resource metamodelResource) {
    for (EObject content : metamodelResource.getContents()) {
      if (content instanceof EPackage ePackage) {
        registerPackage(ePackage);
      }
    }
  }

  /**
   * Registers an EPackage tree in the global EMF registry.
   *
   * @param ePackage package to register recursively
   */
  private void registerPackage(EPackage ePackage) {
    EPackage.Registry.INSTANCE.put(ePackage.getNsURI(), ePackage);
    for (EPackage child : ePackage.getESubpackages()) {
      registerPackage(child);
    }
  }

  /**
   * Creates an EMF object by classifier name from the loaded metamodel.
   *
   * @param metamodelResource loaded metamodel resource
   * @param classifierName EClass name to instantiate
   * @return new EObject instance
   */
  private EObject create(Resource metamodelResource, String classifierName) {
    EClass eClass = (EClass) classifier(metamodelResource, classifierName);
    EFactory factory = eClass.getEPackage().getEFactoryInstance();
    return factory.create(eClass);
  }

  /**
   * Resolves an enum literal instance by enum and literal name.
   *
   * @param metamodelResource loaded metamodel resource
   * @param enumName EEnum name
   * @param literalName literal name
   * @return EMF enum literal instance
   */
  private Object enumValue(Resource metamodelResource, String enumName, String literalName) {
    EEnum eEnum = (EEnum) classifier(metamodelResource, enumName);
    return eEnum.getEEnumLiteral(literalName).getInstance();
  }

  /**
   * Finds a classifier anywhere in a metamodel resource's package tree.
   *
   * @param metamodelResource loaded metamodel resource
   * @param name classifier name
   * @return matching classifier
   */
  private EClassifier classifier(Resource metamodelResource, String name) {
    for (EObject content : metamodelResource.getContents()) {
      EClassifier classifier = classifier((EPackage) content, name);
      if (classifier != null) {
        return classifier;
      }
    }
    throw new IllegalArgumentException("Classifier not found: " + name);
  }

  /**
   * Finds a classifier in a package or any nested subpackage.
   *
   * @param ePackage package to search
   * @param name classifier name
   * @return matching classifier, or {@code null}
   */
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

  /**
   * Sets a named EMF feature on an object.
   *
   * @param object owner object
   * @param featureName feature to set
   * @param value value to assign
   */
  private void set(EObject object, String featureName, Object value) {
    object.eSet(feature(object, featureName), value);
  }

  /**
   * Adds an EMF object to a many-valued containment or reference feature.
   *
   * @param object owner object
   * @param featureName many-valued feature name
   * @param value value to add
   */
  @SuppressWarnings("unchecked")
  private void add(EObject object, String featureName, EObject value) {
    ((List<EObject>) object.eGet(feature(object, featureName))).add(value);
  }

  /**
   * Adds a scalar value to a many-valued EMF attribute feature.
   *
   * @param object owner object
   * @param featureName many-valued attribute feature name
   * @param value value to add
   */
  @SuppressWarnings("unchecked")
  private void addValue(EObject object, String featureName, Object value) {
    ((List<Object>) object.eGet(feature(object, featureName))).add(value);
  }

  /**
   * Collects deployable elements from all {@code ServerlessService} instances under a PIM root.
   *
   * @param pimRoot PIM model root
   * @param featureName service containment feature (for example {@code functions} or {@code
   *     stores})
   * @return flattened deployable elements
   */
  private List<EObject> serviceDeployables(EObject pimRoot, String featureName) {
    List<EObject> deployables = new ArrayList<>();
    for (EObject service : values(pimRoot, "services")) {
      deployables.addAll(values(service, featureName));
    }
    return deployables;
  }

  /**
   * Returns a root object and all nested contained objects.
   *
   * @param root model root
   * @return flattened containment tree
   */
  private List<EObject> allObjects(EObject root) {
    List<EObject> objects = new ArrayList<>();
    objects.add(root);
    for (var iterator = root.eAllContents(); iterator.hasNext(); ) {
      objects.add(iterator.next());
    }
    return objects;
  }

  /**
   * Finds the first element by class and name in a list.
   *
   * @param objects search space
   * @param className expected EClass name
   * @param name expected name
   * @return matching object
   */
  private EObject first(List<EObject> objects, String className, String name) {
    return objects.stream()
        .filter(object -> className.equals(object.eClass().getName()))
        .filter(object -> name.equals(featureText(object, "name")))
        .findFirst()
        .orElseThrow(() -> new AssertionError("Missing " + className + " named " + name));
  }

  /**
   * Finds a named child under a direct many-valued root feature.
   *
   * @param root owner root
   * @param featureName many-valued feature name
   * @param className expected EClass name
   * @param name expected name
   * @return matching child
   */
  private EObject single(EObject root, String featureName, String className, String name) {
    return first(values(root, featureName), className, name);
  }

  /**
   * Finds a schema field by name.
   *
   * @param schema owner schema
   * @param name field name
   * @return matching field
   */
  private EObject field(EObject schema, String name) {
    return values(schema, "fields").stream()
        .filter(field -> name.equals(get(field, "name")))
        .findFirst()
        .orElseThrow(() -> new AssertionError("Missing field " + name));
  }

  /**
   * Collects schema field names.
   *
   * @param schema owner schema
   * @return field names
   */
  private List<String> fieldNames(EObject schema) {
    return values(schema, "fields").stream().map(field -> get(field, "name").toString()).toList();
  }

  /**
   * Collects configuration parameter names.
   *
   * @param configuration configuration set
   * @return parameter names
   */
  private List<String> configParameterNames(EObject configuration) {
    return values(configuration, "parameters").stream()
        .map(parameter -> get(parameter, "name").toString())
        .toList();
  }

  /**
   * Reads a many-valued EMF feature as a list of model objects.
   *
   * @param object owner object
   * @param featureName structural feature name
   * @return feature value cast to a list of {@link EObject}s
   */
  @SuppressWarnings("unchecked")
  private List<EObject> values(EObject object, String featureName) {
    return (List<EObject>) object.eGet(feature(object, featureName));
  }

  /**
   * Reads a single-valued EMF reference.
   *
   * @param object owner object
   * @param featureName reference feature name
   * @return referenced object, or {@code null}
   */
  private EObject reference(EObject object, String featureName) {
    return (EObject) object.eGet(feature(object, featureName));
  }

  /**
   * Reads an arbitrary EMF feature value.
   *
   * @param object owner object
   * @param featureName structural feature name
   * @return current feature value
   */
  private Object get(EObject object, String featureName) {
    return object.eGet(feature(object, featureName));
  }

  /**
   * Reads an optional feature as text without failing when the feature is not present.
   *
   * @param object owner object
   * @param featureName optional feature name
   * @return textual feature value, or an empty string
   */
  private String featureText(EObject object, String featureName) {
    EStructuralFeature feature = object.eClass().getEStructuralFeature(featureName);
    if (feature == null) {
      return "";
    }
    Object value = object.eGet(feature);
    return value == null ? "" : value.toString();
  }

  /**
   * Checks the rule identifier features used by generated target elements, trace links, and
   * readiness evidence.
   *
   * @param object generated object
   * @param ruleId expected transformation rule family
   * @return true when the object carries evidence for the rule family
   */
  private boolean hasRuleEvidence(EObject object, String ruleId) {
    return ruleId.equals(featureText(object, "ruleId"))
        || ruleId.equals(featureText(object, "transformationRule"));
  }

  /**
   * Returns an EMF enum's user-facing label where available.
   *
   * @param value enum instance
   * @return enum label
   */
  private String enumLabel(Object value) {
    return String.valueOf(value);
  }

  /**
   * Resolves a structural feature and fails fast when fixture code no longer matches the metamodel.
   *
   * @param object owner object
   * @param featureName expected feature name
   * @return resolved structural feature
   */
  private EStructuralFeature feature(EObject object, String featureName) {
    EStructuralFeature feature = object.eClass().getEStructuralFeature(featureName);
    if (feature == null) {
      throw new IllegalArgumentException(
          object.eClass().getName() + " has no feature " + featureName);
    }
    return feature;
  }
}
