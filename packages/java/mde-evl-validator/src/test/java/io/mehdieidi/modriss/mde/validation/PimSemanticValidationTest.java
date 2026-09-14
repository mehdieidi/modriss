package io.mehdieidi.modriss.mde.validation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.ResourceLock;

/** Semantic regression coverage for the PIM EVL profile. */
@ResourceLock("epsilon-runtime")
class PimSemanticValidationTest {

  private static final Path REPOSITORY_ROOT = findRepositoryRoot();
  private static final Path PIM_SAMPLE = REPOSITORY_ROOT.resolve("mde/samples/pim.xmi");
  private static final Path PIM_EVL =
      REPOSITORY_ROOT.resolve("mde/validation/pim/pim-semantic-validation.evl");
  private static final Path PIM_ECORE =
      REPOSITORY_ROOT.resolve("mde/metamodels/pim/pim-combined.ecore");
  private static final List<String> ALIASES = List.of("KERNEL");

  private static final String SERVICE_ID = "f9c24e2e-c321-40b5-b153-2ca6a604cb4f";
  private static final String FUNCTION_ID = "db4d4ca3-e36c-407b-accd-69c19211de8d";
  private static final String FUNCTION_2_ID = "283ff10c-7898-4897-8d37-2804e3b3f8b4";
  private static final String API_ID = "api_f9c24e2e-c321-40b5-b153-2ca6a604cb4f";
  private static final String ROUTE_ID = "043a5d05-0b6d-46ed-ba04-6bb7a6d24a8b";
  private static final String SCHEMA_ID = "4115af39-97cd-427e-89e2-964bc133e556";
  private static final String INPUT_SCHEMA_ID = "de72796f-9b1b-4e4c-bbeb-0f9d627eecf9";
  private static final String EVENT_ID = "b03a1a31-d7b8-42d0-a818-05604a2e4134";
  private static final String STORE_ID = "3d1ba5c0-4b14-4e56-995e-3d8b783e8029";
  private static final String WORKFLOW_ID = "d6fc1f72-b3a6-457b-8f3e-67ba486b0dc4";
  private static final String START_STEP_ID = "91d04210-d345-4072-9b02-141a60b954a6";
  private static final String TASK_STEP_ID = "17ed3677-6ac9-48cd-bf54-f3d20db6cb18";
  private static final String END_STEP_ID = "4f8ec946-35a7-4090-a869-442e67436fd6";
  private static final String EVENT_BUS_ID = "eventbus_f9c24e2e-c321-40b5-b153-2ca6a604cb4f";
  private static final String TOPIC_ID = "4788257d-8e4f-4059-a60f-51e301aefcdb";
  private static final String QUEUE_ID = "2f2b473d-6069-4e90-87f2-44c16935f412";
  private static final String AUTH_ID = "5284e278-f020-407c-8a42-2b4c51f7056c";
  private static final String PRINCIPAL_ID = "3d40fa0d-4c97-4475-8a20-ec184bb81d18";
  private static final String SECRET_ID = "secret_payment_partner_api";
  private static final String TIMEOUT_ID = "b8fc0af9-2161-4510-bb0e-63d63c241907";
  private static final String IDEMPOTENCY_ID = "53aed98c-0619-434c-93ed-0a37a38f4c2b";
  private static final String RETENTION_ID = "83e07331-ced5-4878-80fe-6e03de1b201f";
  private static final String BACKUP_ID = "45b65185-c198-424b-9492-88328d89e02d";

  @TempDir Path tempDir;

  @Test
  void repositoryPimSamplePassesAllMandatoryAndOptionalSemantics() throws Exception {
    EvlValidationReport report = validate(PIM_SAMPLE);

    assertEquals(EvlValidationStatus.SUCCEEDED, report.status());
    assertTrue(report.diagnostics().isEmpty(), report.diagnostics().toString());
    assertTrue(report.violations().isEmpty(), report.violations().toString());
    assertFalse(report.hasMandatoryViolations());
  }

  @Test
  void pimNegativeScenariosExerciseDocumentedSemanticRules() throws Exception {
    Set<String> covered = new LinkedHashSet<>();

    for (Scenario scenario : scenarios()) {
      Path model = writeModel(scenario.name(), scenario.model());
      EvlValidationReport report =
          validate(scenario.name(), model, scenario.structuralValidation());
      Set<String> actual = violationNames(report);

      for (Expected expected : scenario.expected()) {
        assertTrue(
            actual.contains(expected.name()),
            () ->
                scenario.name()
                    + " did not trigger "
                    + expected.name()
                    + ". Actual violations: "
                    + actual
                    + "\nDiagnostics: "
                    + report.diagnostics());
        assertEquals(
            expected.kind(),
            kindOf(report, expected.name()),
            () -> scenario.name() + " reported the wrong EVL severity for " + expected.name());
        covered.add(expected.name());
      }
    }

    assertTrue(
        covered.containsAll(executableRuleCoverage()),
        () -> "Missing coverage for " + missingCoverage(covered));
  }

  @Test
  void pimEolHelpersHaveExplicitBehaviorCoverage() throws Exception {
    Path evlRoot = tempDir.resolve("helper-evl");
    Files.createDirectories(evlRoot.resolve("lib"));
    Files.createDirectories(evlRoot.resolve("../shared").normalize());
    Files.copy(
        REPOSITORY_ROOT.resolve("mde/validation/pim/lib/pim-validation-helpers.eol"),
        evlRoot.resolve("lib/pim-validation-helpers.eol"));
    Files.copy(
        REPOSITORY_ROOT.resolve("mde/validation/shared/shared-validation-helpers.eol"),
        evlRoot.resolve("../shared/shared-validation-helpers.eol").normalize());
    Path evl = evlRoot.resolve("helper-contract.evl");
    Files.writeString(
        evl,
        """
import "../shared/shared-validation-helpers.eol";
import "lib/pim-validation-helpers.eol";

context PIM!PIMModel {
  constraint HelperContracts {
    check {
      var service = PIM!ServerlessService.all.select(s | s.`id` = "helper-service").first();
      var root = PIM!PIMModel.all.first();
      var schema = PIM!Schema.all.select(s | s.`id` = "helper-schema").first();
      var functionItem = PIM!Function.all.select(f | f.`id` = "helper-function").first();
      var eventItem = PIM!EventType.all.select(e | e.`id` = "helper-event").first();
      var channel = PIM!EventChannel.all.select(c | c.`id` = "helper-channel").first();
      var workflow = PIM!Workflow.all.select(w | w.`id` = "helper-workflow").first();
      var start = PIM!WorkflowStep.all.select(s | s.`id` = "helper-start").first();
      var task = PIM!TaskStep.all.select(s | s.`id` = "helper-task").first();
      var end = PIM!WorkflowStep.all.select(s | s.`id` = "helper-end").first();
      var store = PIM!DataStore.all.select(s | s.`id` = "helper-store").first();
      var objectStore = PIM!ObjectStore.all.select(s | s.`id` = "helper-object-store").first();
      var access = PIM!AccessPattern.all.select(a | a.`id` = "helper-access").first();
      var route = PIM!ApiRoute.all.select(r | r.`id` = "helper-route").first();
      var transition = PIM!WorkflowTransition.all.select(t | t.`id` = "helper-transition-a").first();
      var profile = PIM!ImplementationProfile.all.select(p | p.`id` = "helper-profile").first();
      var transitionIndex = new Map;
      addWorkflowTransition(transitionIndex, "manual-key", transition);

      return " text ".hasText() and not "".hasText() and true.isTrue() and
        PIM!DataAccessMode#READ.enumIs("READ") and
        PIM!DataAccessMode#READ.enumIn(Sequence{"READ", "WRITE"}) and
        Sequence{1}.notEmpty() and not Sequence{}.notEmpty() and
        service.displayName() = "Helper Service" and
        not "generic queue".containsProviderToken() and "AWS Lambda".containsProviderToken() and
        reachableFunctionIds().includes("helper-function") and functionItem.hasAnyIncomingBinding() and
        service.ownedElements().includes(functionItem) and service.serviceMemberships().size() = 1 and
        serviceMembershipsByServiceId().get("helper-service").size() = 1 and
        root.allServiceApis().size() = 1 and root.allServiceChannels().size() = 1 and root.allServiceWorkflows().size() = 1 and
        service.hasMembershipFor(functionItem) and functionItem.changesStateOrEmitsEvents() and
        functionItem.needsIdempotencyPolicy() and
        idempotentConsumerFunctionIds().includes("helper-function") and
        schema.hasFieldNamed("correlationId") and schema.hasClassifiedSensitiveFields() and
        eventItem.carriesPersonalData() and channel.carriesPersonalData() and
        transitionIndex.get("manual-key").size() = 1 and
        start.outgoingTransitions().size() = 1 and task.incomingTransitions().size() = 1 and task.outgoingTransitions().size() = 1 and
        end.incomingTransitions().size() = 1 and workflowTransitionsBySourceId().get("helper-task").size() = 1 and
        workflowTransitionsByTargetId().get("helper-task").size() = 1 and
        task.actionCount() = 1 and workflow.startSteps().size() = 1 and workflow.endSteps().size() = 1 and
        store.dataStoreContainsSensitiveData() and objectStore.objectStoreContainsSensitiveData() and
        store.storageContainsSensitiveData() and objectStore.storageContainsSensitiveData() and
        access.hasQueryShape() and route.routeUniquenessKey() = "helper-api::POST::/helper" and
        duplicatePimApiRouteKeys().includes("helper-api::POST::/helper") and
        access.indexSupportKey() = "helper-store::helper-access" and
        indexedAccessPatternKeys().includes("helper-store::helper-access") and
        eventChannelProducerKeys().includes("helper-event::helper-function") and
        eventChannelConsumerKeys().includes("helper-event::helper-function") and
        eventItem.participantKey(functionItem) = "helper-event::helper-function" and
        eventFlowWorkflowConsumerKeys().includes("helper-channel::helper-workflow") and
        channel.workflowConsumerKey(workflow) = "helper-channel::helper-workflow" and
        rootPolicyIds().includes("helper-idempotency") and profile.packageManagerFitsLanguage();
    }
  }
}
""");

    Path model = writeModel("helper-contract", minimalHelperModel());
    EvlValidationReport report =
        new EpsilonEvlValidator()
            .validate(
                EvlValidationRequest.forRoot(
                    evl,
                    List.of(
                        FileEvlModelConfiguration.readOnly(
                            "PIM", ALIASES, model, List.of(PIM_ECORE))),
                    true));

    assertEquals(EvlValidationStatus.SUCCEEDED, report.status());
    assertTrue(report.diagnostics().isEmpty(), report.diagnostics().toString());
    assertTrue(report.violations().isEmpty(), report.violations().toString());
  }

  private List<Scenario> scenarios() throws Exception {
    String sample = Files.readString(PIM_SAMPLE);
    return List.of(
        scenario(
            "root-core",
            minimalRootCoreModel(),
            mandatory("DomainNameRequired"),
            mandatory("ImplementationProfileRequiredForGeneration"),
            mandatory("NoProviderSpecificNamesInModel"),
            mandatory("ModelElementIdRequired"),
            optional("DefaultCorrelationIdShouldBeNamed"),
            optional("ArchitectureStyleShouldMatchContents"),
            optional("GeneratedElementShouldBeTraceable"),
            optional("RationaleRecommendedForManuallyMaintainedElements")),
        scenario(
            "deployment",
            insertBeforeRootClose(
                sample
                    .replace("sourceLayout=\"TBD\"", "sourceLayout=\"\"")
                    .replace("buildCommand=\"TBD\"", "buildCommand=\"\"")
                    .replace("generateTypedContracts=\"true\"", "generateTypedContracts=\"false\"")
                    .replace(
                        "primaryLanguage=\"CUSTOM\" packageManager=\"NONE\"",
                        "primaryLanguage=\"PYTHON\" packageManager=\"NPM\""),
                """
  <services id="bad-service-empty" name="1 Bad Service" externallyExposed="true" ownsData="true" boundaryType="CAPABILITY_BASED"/>
  <deploymentUnits id="bad-du" name="Bad Deployment Unit" independentlyDeployable="true" unitType="SERVICE"/>
  <environments id="bad-prod" name="Bad Prod" productionLike="false" requiresApproval="false" environmentClass="PROD"/>
  <serviceMemberships id="bad-membership" name="Bad Membership" ownershipKind="OWNS" service="bad-service-empty" element="%s"/>
"""
                    .formatted(FUNCTION_ID)),
            mandatory("ServiceResponsibilityRequired"),
            mandatory("ServiceOwnsAtLeastOneElement"),
            mandatory("ExternallyExposedServiceNeedsApi"),
            mandatory("DataOwningServiceNeedsStore"),
            optional("ServiceOwnerTeamRecommended"),
            mandatory("DeploymentUnitContainsElements"),
            mandatory("DeploymentUnitTargetsEnvironment"),
            optional("IndependentlyDeployableUnitNeedsReleaseStrategy"),
            mandatory("ProdEnvironmentIsProductionLike"),
            optional("ProdEnvironmentShouldRequireApproval"),
            mandatory("ProfileHasGenerationBasics"),
            mandatory("PackageManagerMatchesRuntimeLanguage"),
            optional("RuntimeValidationShouldGenerateTypedContracts"),
            mandatory("MembershipMatchesServiceOwnership"),
            optional("PortableNameRecommended")),
        scenario(
            "compute-contracts-api",
            insertBeforeFirstServiceClose(
                sample,
                """
    <functions id="bad-function" name="Bad Function" writesState="true" readsState="true" publishesEvents="true" requiresNetworkAccess="true" publicEntryPoint="true" expectedAverageDurationMs="10" expectedP95DurationMs="5" functionKind="COMMAND_HANDLER">
      <contract id="bad-contract" name="Bad Contract" validatesInput="true" validatesOutput="true" correlationIdField="missingCorrelation" idempotencyKeyField="missingIdempotency"/>
      <triggers id="bad-trigger" name="Bad Trigger" enabled="false" invocationMode="SYNCHRONOUS" source="%s" startsWorkflow="%s"/>
    </functions>
    <functions id="bad-contract-field-function" name="Bad Contract Field Function" responsibility="Exercises contract field checks" functionKind="QUERY_HANDLER">
      <contract id="bad-contract-field" name="Bad Contract Field" inputSchema="%s" correlationIdField="missingCorrelation" idempotencyKeyField="missingIdempotency"/>
    </functions>
    <functions id="bad-unreachable-function" name="Bad Unreachable Function" responsibility="Unreachable" functionKind="QUERY_HANDLER">
      <contract id="bad-unreachable-contract" name="Bad Unreachable Contract" inputSchema="%s"/>
    </functions>
    <apis id="bad-api" name="Bad API" authRequired="true" externalConsumerFacing="true" generatedOpenApiRequired="true" apiStyle="RESOURCE_ORIENTED_HTTP">
      <routes id="bad-route" name="Bad Route" pathTemplate="bad" publicRoute="true" authRequired="true" requestValidationRequired="true" responseValidationRequired="true" method="POST"/>
      <routes id="bad-route-duplicate-a" name="Bad Route Duplicate A" pathTemplate="/duplicate" method="GET" functionIntegration="%s"/>
      <routes id="bad-route-duplicate-b" name="Bad Route Duplicate B" pathTemplate="/duplicate" method="GET" functionIntegration="%s"/>
      <routes id="bad-route-both" name="Bad Route Both" pathTemplate="/both" method="POST" functionIntegration="%s" workflowIntegration="%s"/>
      <routes id="bad-route-errors" name="Bad Route Errors" pathTemplate="/errors" method="POST" functionIntegration="%s">
        <errorMappings id="bad-error-mapping" name="Bad Error Mapping"/>
      </routes>
    </apis>
"""
                    .formatted(
                        EVENT_BUS_ID,
                        WORKFLOW_ID,
                        INPUT_SCHEMA_ID,
                        INPUT_SCHEMA_ID,
                        FUNCTION_ID,
                        FUNCTION_2_ID,
                        FUNCTION_ID,
                        WORKFLOW_ID,
                        FUNCTION_ID)),
            mandatory("FunctionResponsibilityRequired"),
            mandatory("FunctionMustBeReachable"),
            mandatory("StateChangingFunctionNeedsIdempotency"),
            mandatory("ReadsStateMustReferenceStores"),
            mandatory("WritesStateMustReferenceStores"),
            mandatory("PublishesEventsMustReferenceEventTypes"),
            optional("ExternalCallsNeedResilienceAndTimeout"),
            optional("FunctionDurationEstimatesShouldBeConsistent"),
            optional("PublicFunctionShouldHaveSecurityPolicy"),
            mandatory("ExactlyOneInvocationTarget"),
            optional("DisabledTriggerShouldExplainWhy"),
            mandatory("ProtectedApiHasAuthPolicy"),
            optional("ConsumerFacingApiHasPublicMetadata"),
            optional("GeneratedOpenApiNeedsContract"),
            mandatory("ExactlyOneIntegration"),
            mandatory("UniqueMethodPathWithinApi"),
            mandatory("RoutePathStartsWithSlash"),
            mandatory("RouteIntegrationIsExactlyOneBackend"),
            mandatory("ProtectedRouteHasAuthorization"),
            mandatory("RequestValidationNeedsRequestSchema"),
            mandatory("ResponseValidationNeedsResponseSchema"),
            optional("PublicRouteShouldDescribeConsumers"),
            optional("ErrorMappingShouldBeActionable"),
            mandatory("ContractHasAtLeastInputOrOutput"),
            mandatory("InputValidationRequiresInputSchema"),
            mandatory("OutputValidationRequiresOutputSchema"),
            optional("CorrelationIdFieldShouldExistInSchema"),
            optional("IdempotencyKeyFieldShouldExistInSchema")),
        scenarioWithoutStructuralValidation(
            "schemas-events-channels",
            insertBeforeRootClose(
                insertBeforeFirstServiceClose(
                    sample,
                    """
    <functions id="bad-consumer-function" name="Bad Consumer Function" responsibility="Consumes exactly-once events without idempotency" functionKind="EVENT_HANDLER">
      <contract id="bad-consumer-contract" name="Bad Consumer Contract" inputSchema="%s"/>
    </functions>
    <channels xsi:type="integration:Topic" id="bad-channel" name="Bad Channel" encrypted="false" channelKind="TOPIC" orderingRequirement="PER_KEY" deliverySemantics="EXACTLY_ONCE_REQUIRED" eventTypes="bad-event" consumers="bad-consumer-function" filteringRequired="true"/>
    <channels xsi:type="integration:Topic" id="bad-subscription-topic" name="Bad Subscription Topic" encrypted="true" channelKind="TOPIC" orderingRequirement="NONE" deliverySemantics="AT_LEAST_ONCE" eventTypes="%s">
      <subscriptions id="bad-subscription" name="Bad Subscription" deadLetterRequired="true" channel="bad-subscription-topic" target="bad-consumer-function"/>
    </channels>
    <channels xsi:type="integration:Queue" id="bad-queue" name="Bad Queue" channelKind="QUEUE" orderingRequirement="NONE" deliverySemantics="AT_LEAST_ONCE" eventTypes="%s" consumers="bad-consumer-function" maxReceiveAttempts="3" fifoRequired="true" deduplicationRequired="false" batchPolicy="bad-batch"/>
    <channels xsi:type="integration:EventBus" id="bad-bus" name="Bad Bus" channelKind="EVENT_BUS" orderingRequirement="NONE" deliverySemantics="AT_LEAST_ONCE" eventTypes="%s">
      <routingRules id="bad-rule" name="Bad Rule" enabled="true"/>
    </channels>
    <channels xsi:type="integration:EventBus" id="bad-empty-bus" name="Bad Empty Bus" channelKind="EVENT_BUS" orderingRequirement="NONE" deliverySemantics="AT_LEAST_ONCE" eventTypes="%s"/>
    <schedules id="bad-schedule" name="Bad Schedule" enabled="true" scheduleExpression=""/>
"""
                        .formatted(INPUT_SCHEMA_ID, EVENT_ID, EVENT_ID, EVENT_ID, EVENT_ID)),
                """
  <schemas id="bad-empty-schema" name="Bad Empty Schema" semanticVersion="1" schemaKind="REQUEST"/>
  <schemas id="bad-external-schema" name="Bad External Schema" semanticVersion="x" externalSchemaUri="https://example.invalid/schema.json" schemaKind="MESSAGE"/>
  <schemas id="bad-field-schema" name="Bad Field Schema" semanticVersion="1.0.0" schemaKind="EVENT">
    <fields id="bad-sensitive-field" name="Bad Sensitive Field" personalData="true" example="real person" fieldType="STRING"/>
    <fields id="bad-enum-field" name="Bad Enum Field" fieldType="ENUM"/>
    <fields id="bad-object-field" name="Bad Object Field" fieldType="OBJECT"/>
    <fields id="bad-length-field" name="Bad Length Field" minLength="10" maxLength="2" fieldType="STRING"/>
    <fields id="bad-null-field" name="Bad Null Field" required="true" nullable="true" fieldType="STRING"/>
    <constraints id="bad-schema-constraint" name="Bad Schema Constraint"/>
  </schemas>
  <eventTypes id="bad-event" name="Bad Event" semanticName="BadEvent" containsPersonalData="true" externalEvent="true" replayable="true" producedBy="%s"/>
  <flows xsi:type="integration:EventFlow" id="bad-flow" name="Bad Flow" criticalPath="true" containsPersonalData="true" source="%s" target="%s" eventType="%s" channel="%s"/>
  <policies xsi:type="policy:BatchPolicy" id="bad-batch" name="Bad Batch" partialFailureHandling="UNDECIDED"/>
"""
                    .formatted(FUNCTION_ID, FUNCTION_ID, EVENT_BUS_ID, EVENT_ID, EVENT_BUS_ID)),
            mandatory("SchemaHasFieldsUnlessExternal"),
            optional("SchemaVersionShouldBeSemver"),
            optional("ExternalSchemaShouldStateCompatibility"),
            mandatory("SensitiveFieldIsClassified"),
            mandatory("EnumFieldHasLiterals"),
            mandatory("ObjectFieldHasObjectSchema"),
            mandatory("FieldLengthBoundsAreValid"),
            optional("RequiredNullableFieldNeedsRationale"),
            optional("SensitiveFieldShouldNotExposeExamplesOrDefaults"),
            mandatory("SchemaConstraintHasExpressionAndMessage"),
            mandatory("EventTypeHasSchema"),
            mandatory("PersonalDataEventSchemaIsClassified"),
            optional("EventTypeShouldHaveVersionAndSource"),
            optional("ExternalOrReplayableEventShouldHaveEnvelope"),
            optional("EventTypeProducerConsumerLinksConsistent"),
            mandatory("PersonalDataChannelMustBeEncrypted"),
            mandatory("ExactlyOnceRequiresIdempotentConsumers"),
            mandatory("OrderedChannelHasOrderingKey"),
            optional("ChannelShouldHaveProducerAndConsumerIntent"),
            optional("ChannelProducerConsumerLinksConsistent"),
            mandatory("RetryQueueNeedsDeadLetterChannel"),
            mandatory("BatchQueueNeedsPartialFailureDecision"),
            mandatory("FifoQueueNeedsDeduplicationOrIdempotency"),
            optional("QueueShouldHaveVisibilityTimeout"),
            optional("FilteringTopicShouldUseSubscriptionFilters"),
            optional("EventBusShouldHaveRoutingRules"),
            mandatory("RoutingRuleHasPatternOrSchedule"),
            mandatory("EnabledRoutingRuleHasTargets"),
            optional("DeadLetterSubscriptionShouldExplainHandling"),
            mandatory("ScheduleExpressionRequired"),
            optional("ScheduleShouldDeclareTimezone"),
            mandatory("EnabledScheduleHasSingleTarget"),
            mandatory("FlowPurposeRequired"),
            optional("CriticalFlowNeedsResilienceAndObservability"),
            optional("PersonalDataFlowNeedsPolicy")),
        scenarioWithoutStructuralValidation(
            "data-workflow-security-policies-readiness",
            insertIntoExistingReadiness(
                insertBeforeRootClose(
                    insertBeforeFirstServiceClose(
                        sample.replace(
                            "transformationReady=\"true\" deploymentReady=\"false\""
                                + " productionReady=\"false\"",
                            "transformationReady=\"false\" deploymentReady=\"false\""
                                + " productionReady=\"true\""),
                        """
    <stores xsi:type="data:DataStore" id="bad-store" name="Bad Store" persistent="true" encrypted="false" containsPersonalData="true" transactional="true" storeKind="DOCUMENT" consistencyNeed="EVENTUAL">
      <ownedDataModels id="bad-data-model" name="Bad Data Model" sourceOfTruth="true" readModel="true" dataModelKind="ENTITY">
        <storageFields id="bad-data-field" name="Bad Data Field" identifier="true" required="false" personalData="true" fieldType="STRING"/>
      </ownedDataModels>
      <ownedDataModels id="bad-empty-data-model" name="Bad Empty Data Model" schema="de72796f-9b1b-4e4c-bbeb-0f9d627eecf9" dataModelKind="ENTITY"/>
      <accessPatterns id="bad-access" name="Bad Access" highFrequency="true"/>
      <indexCandidates id="bad-index" name="Bad Index" requiredForProduction="true"/>
    </stores>
    <stores xsi:type="data:ObjectStore" id="bad-object" name="Bad Object" persistent="true" encrypted="true" eventNotificationRequired="true" versioningRequired="true"/>
    <workflows id="bad-workflow" name="Bad Workflow" stateful="true" workflowKind="ORCHESTRATION">
      <steps xsi:type="workflow:TaskStep" id="bad-task" name="Bad Task" timeoutSeconds="5"/>
      <steps xsi:type="workflow:ChoiceStep" id="bad-choice" name="Bad Choice"/>
      <steps xsi:type="workflow:SuccessEndStep" id="bad-end" name="Bad End"/>
      <steps xsi:type="workflow:TaskStep" id="bad-handler-source" name="Bad Handler Source" invokesFunction="%s">
        <catchHandlers id="bad-cross-workflow-handler" name="Bad Cross Workflow Handler" nextStep="91d04210-d345-4072-9b02-141a60b954a6"/>
      </steps>
      <transitions id="bad-transition" name="Bad Transition" source="bad-end" target="91d04210-d345-4072-9b02-141a60b954a6"/>
      <transitions id="bad-choice-transition" name="Bad Choice Transition" source="bad-choice" target="bad-end"/>
    </workflows>
    <workflows id="bad-no-end-workflow" name="Bad No End Workflow" workflowKind="ORCHESTRATION">
      <steps xsi:type="workflow:StartStep" id="bad-start-only" name="Bad Start Only"/>
    </workflows>
    <functions id="bad-no-contract-function" name="Bad No Contract Function" responsibility="Missing contract" functionKind="QUERY_HANDLER"/>
    <adapters id="bad-adapter" name="Bad Adapter"/>
    <adapters id="bad-missing-credentials-adapter" name="Bad Missing Credentials Adapter" endpoint="bad-endpoint"/>
    <adapters id="bad-credentialed-adapter" name="Bad Credentialed Adapter" endpoint="bad-endpoint">
      <credentials id="bad-credential" name="Bad Credential" secretKind="API_KEY"/>
    </adapters>
"""
                            .formatted(FUNCTION_ID)),
                    """
  <dataAccesses id="bad-data-access" name="Bad Data Access" mode="READ_WRITE" function="bec9f700-9a6a-4407-9d37-f2014ea60483" store="%s"/>
  <externalEndpoints id="bad-endpoint" name="Bad Endpoint" credentialsRequired="true" rateLimitedByProvider="true"/>
  <identityProviders id="bad-idp" name="Bad Idp" federationRequired="true" identityKind="FEDERATED_IDENTITY"/>
  <principals id="bad-principal" name="Bad Principal" privileged="true" principalKind="ROLE">
    <permissions id="bad-permission" name="Bad Permission" action="*" resource="*" leastPrivilegeConfirmed="false" effect="ALLOW"/>
  </principals>
  <principals id="bad-empty-privileged-principal" name="Bad Empty Privileged Principal" privileged="true" principalKind="ROLE"/>
  <configurations id="bad-config" name="Bad Config" scope="SERVICE">
    <parameters id="bad-param" name="Bad Param" required="true"/>
    <environmentVariables id="bad-env" name="Bad Env" variableName="badName" secretReference="true" valueSource="plaintext"/>
  </configurations>
  <secrets id="bad-secret" name="Bad Secret" rotationRequired="true" environmentSpecific="true" generatedReferenceOnly="false" secretKind="API_KEY"/>
  <policies xsi:type="policy:RetryPolicy" id="bad-retry" name="Bad Retry" maxAttempts="11" initialDelaySeconds="-1" backoffRate="0.5" maxDelaySeconds="-1"/>
  <policies xsi:type="policy:DeadLetterPolicy" id="bad-dlq" name="Bad Dlq" required="true"/>
  <policies xsi:type="policy:TimeoutPolicy" id="bad-timeout" name="Bad Timeout" timeoutSeconds="0" clientTimeoutSeconds="5"/>
  <policies xsi:type="policy:IdempotencyPolicy" id="bad-idempotency" name="Bad Idempotency"/>
  <policies xsi:type="policy:ConcurrencyPolicy" id="bad-concurrency" name="Bad Concurrency"/>
  <policies xsi:type="policy:RateLimitPolicy" id="bad-rate" name="Bad Rate" requestsPerSecond="0"/>
  <policies xsi:type="policy:BatchPolicy" id="bad-batch-policy" name="Bad Batch Policy" partialFailureHandling="NOT_REQUIRED"/>
  <policies xsi:type="policy:ObservabilityConfig" id="bad-obs" name="Bad Obs" productionRequired="true" correlationIdRequired="false"/>
  <policies xsi:type="policy:DataProtectionPolicy" id="bad-protection" name="Bad Protection" classification="PII"/>
  <policies xsi:type="policy:RetentionPolicy" id="bad-retention" name="Bad Retention"/>
  <policies xsi:type="policy:BackupPolicy" id="bad-backup" name="Bad Backup" backupRequired="true"/>
  <policies xsi:type="policy:DataQualityPolicy" id="bad-quality" name="Bad Quality"/>
  <policies xsi:type="security:AuthPolicy" id="bad-auth" name="Bad Auth" authenticationRequired="true"/>
  <policies xsi:type="security:AuthorizationPolicy" id="bad-authorization" name="Bad Authorization" resourceLevelAuthorization="true"/>
  <policies xsi:type="workflow:CompensationPolicy" id="bad-compensation" name="Bad Compensation"/>
"""
                        .formatted(STORE_ID)),
                """
    <findings id="bad-finding" name="Bad Finding" blocking="true" severity="ERROR"/>
    <checks id="bad-check" name="Bad Check" checkId="bad-check" passed="false"/>
    <manualDecisions id="bad-decision" name="Bad Decision" question="Decide?" blocking="true"/>
    <manualDecisions id="bad-generated-decision" name="Bad Generated Decision" question="Decide generated?" blocking="true" generatedByTransformation="true"/>
"""),
            mandatory("SensitiveStorageMustBeEncrypted"),
            optional("PersistentStorageShouldHaveRetentionPolicy"),
            optional("PersistentStorageShouldHaveBackupDecision"),
            mandatory("TransactionalStoreNeedsTransactionalConsistency"),
            mandatory("OwnedElementsHaveMembershipRecords"),
            optional("SourceOfTruthShouldHavePitrOrBackup"),
            optional("ObjectStoreWithEventsShouldDeclareEventTypes"),
            optional("VersionedObjectStoreShouldHaveLifecycleDecision"),
            mandatory("DataModelHasSchema"),
            mandatory("SourceOfTruthCannotBeReadModel"),
            optional("DataModelShouldExposeStorageFields"),
            mandatory("SensitiveDataFieldIsClassified"),
            optional("IdentifierFieldShouldBeRequired"),
            optional("HighFrequencyAccessPatternShouldHaveIndex"),
            optional("ProductionIndexSupportsAccessPattern"),
            mandatory("FunctionContractRequired"),
            mandatory("DataAccessConsistentWithFunctionRefs"),
            optional("GeneratesOrReferencesPermission"),
            mandatory("DataAccessHasPurpose"),
            mandatory("WorkflowHasStartStep"),
            mandatory("WorkflowHasEndSteps"),
            mandatory("ReachableFromStart"),
            mandatory("WorkflowHasAtLeastOneEndStep"),
            optional("StatefulWorkflowNeedsIdempotency"),
            optional("NonStartStepShouldHaveIncomingTransition"),
            optional("TimedStepShouldUseTimeoutPolicy"),
            mandatory("ChoiceHasAtLeastTwoTransitions"),
            mandatory("EndStepsHaveNoOutgoingTransitions"),
            mandatory("TaskStepInvokesExactlyOneAction"),
            mandatory("SameWorkflow"),
            mandatory("TransitionStaysInsideWorkflow"),
            optional("ConditionalTransitionShouldHaveConditionUnlessDefault"),
            mandatory("NextStepInSameWorkflow"),
            mandatory("AdapterHasEndpoint"),
            mandatory("CredentialRequirementForCredentialedAdapter"),
            optional("ExternalAdapterShouldDescribeEndpointAndProtocol"),
            optional("RateLimitedAdapterShouldHaveResilience"),
            mandatory("RetryPolicyBounded"),
            mandatory("RetryBackoffIsValid"),
            mandatory("RequiredDeadLetterPolicyHasChannel"),
            mandatory("TimeoutPolicyIsPositive"),
            mandatory("IdempotencyKeyRequired"),
            optional("IdempotencyStoreDecisionRecommended"),
            optional("ConcurrencyPolicyShouldExplainScaling"),
            mandatory("RateLimitPolicyIsPositive"),
            mandatory("BatchPolicyHasSizeAndFailureDecision"),
            optional("BatchPolicyNeedsRationaleForNoPartialFailureHandling"),
            mandatory("ProductionObservabilityNeedsCorrelation"),
            optional("ObservabilityShouldEnableSignals"),
            mandatory("ProtectedDataHasRetentionDecision"),
            mandatory("ClassifiedDataNeedsProtectionDecision"),
            mandatory("RetentionPolicyHasPeriod"),
            optional("RequiredBackupPolicyHasObjectives"),
            mandatory("DataQualityPolicyHasMeasurementRule"),
            optional("ConfigurationSetShouldApplySomewhere"),
            optional("RequiredParameterShouldHaveDefaultOrStageSpecificDecision"),
            mandatory("SecretEnvironmentVariableReferencesSecret"),
            mandatory("EnvironmentVariableNameIsPortable"),
            mandatory("SecretNotPlainEnvironmentValue"),
            mandatory("SecretReferenceOnly"),
            mandatory("RotationRequiredNeedsFrequency"),
            optional("EnvironmentSpecificSecretShouldHaveOwner"),
            mandatory("CredentialRequirementHasSecret"),
            optional("CredentialRequirementShouldExplainPurpose"),
            optional("FederatedIdentityShouldDescribeTokenAndAttributes"),
            optional("PrivilegedPrincipalNeedsPermissions"),
            mandatory("PermissionIsScoped"),
            optional("LeastPrivilegeShouldBeConfirmed"),
            mandatory("AuthPolicyHasScheme"),
            mandatory("AuthorizationPolicyHasDecisionLogic"),
            optional("ResourceLevelAuthorizationShouldHaveExpression"),
            mandatory("CompensationPolicyHasAction"),
            mandatory("ProductionReadyRequiresPassedChecksAndNoBlockingFindings"),
            optional("FindingShouldRecommendRemediation"),
            optional("FailedCheckShouldHaveRemediation"),
            mandatory("BlockingManualDecisionMustBeAnswered"),
            optional("GeneratedBlockingManualDecisionShouldHaveOwner")),
        scenarioWithoutStructuralValidation(
            "orphan-semantics",
            minimalBrokenContainerModel(),
            mandatory("ApiHasAtLeastOneRoute"),
            mandatory("ChannelHasEventTypes"),
            mandatory("DataStoreHasDataModel"),
            mandatory("DataStoreHasAccessPattern"),
            mandatory("ErrorHandlerHasRecoveryTarget"),
            optional("ErrorHandlerShouldSelectErrors"),
            mandatory("TraceLinkHasReferenceOrExternalId"),
            optional("EnvelopeShouldCarryCorrelationFields"),
            mandatory("StructuredLoggingNeedsFormat"),
            optional("LoggingShouldIncludeCorrelationId"),
            optional("MetricPolicyShouldNameMetricAndUnit"),
            mandatory("AlertPolicyIsActionable"),
            optional("SloShouldBeMeasurable"),
            mandatory("CredentialedCorsCannotUseWildcardOrigins"),
            optional("CorsPolicyShouldDeclareMethodsAndHeaders")));
  }

  private static Scenario scenario(String name, String model, Expected... expected) {
    return new Scenario(name, model, true, List.of(expected));
  }

  private static Scenario scenarioWithoutStructuralValidation(
      String name, String model, Expected... expected) {
    return new Scenario(name, model, false, List.of(expected));
  }

  private static Expected mandatory(String name) {
    return new Expected(name, EvlConstraintKind.MANDATORY);
  }

  private static Expected optional(String name) {
    return new Expected(name, EvlConstraintKind.OPTIONAL);
  }

  private EvlValidationReport validate(Path model) throws Exception {
    return validate(model.toString(), model);
  }

  private EvlValidationReport validate(String scenario, Path model) throws Exception {
    return validate(scenario, model, true);
  }

  private EvlValidationReport validate(String scenario, Path model, boolean structuralValidation)
      throws Exception {
    try {
      return new EpsilonEvlValidator()
          .validate(
              EvlValidationRequest.forRoot(
                  PIM_EVL,
                  List.of(
                      new FileEvlModelConfiguration(
                          "PIM", ALIASES, model, List.of(PIM_ECORE), structuralValidation)),
                  true));
    } catch (EvlValidationException ex) {
      throw new AssertionError(
          scenario + " failed with diagnostics: " + ex.getReport().diagnostics(), ex);
    }
  }

  private Path writeModel(String name, String xml) throws Exception {
    Path model = tempDir.resolve(name + ".pim.xmi");
    Files.writeString(model, xml);
    return model;
  }

  private static String insertBeforeRootClose(String xmi, String fragment) {
    return xmi.replace("</pim:PIMModel>", fragment + "\n</pim:PIMModel>");
  }

  private static String insertBeforeFirstServiceClose(String xmi, String fragment) {
    return xmi.replaceFirst(
        "(?m)^  </services>", Matcher.quoteReplacement(fragment + "\n  </services>"));
  }

  private static String insertIntoExistingReadiness(String xmi, String fragment) {
    return xmi.replace("</readiness>", fragment + "\n  </readiness>");
  }

  private static Set<String> violationNames(EvlValidationReport report) {
    Set<String> names = new LinkedHashSet<>();
    for (EvlConstraintViolation violation : report.violations()) {
      names.add(violation.constraintName());
    }
    return names;
  }

  private static EvlConstraintKind kindOf(EvlValidationReport report, String name) {
    return report.violations().stream()
        .filter(v -> v.constraintName().equals(name))
        .findFirst()
        .orElseThrow()
        .kind();
  }

  private static String minimalHelperModel() {
    return """
<?xml version="1.0" encoding="ASCII"?>
<pim:PIMModel xmi:version="2.0" xmlns:xmi="http://www.omg.org/XMI" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns:data="https://modriss.org/pim/data/1.0" xmlns:integration="https://modriss.org/pim/integration/1.0" xmlns:pim="https://modriss.org/pim/1.0" xmlns:policy="https://modriss.org/pim/policy/1.0" xmlns:workflow="https://modriss.org/pim/workflow/1.0" id="helper-root" name="Helper Root" domainName="Helper" architectureStyle="HYBRID_SERVERLESS" defaultCorrelationIdName="correlationId">
  <services id="helper-service" name="Helper Service" responsibility="Helper" boundaryType="CAPABILITY_BASED">
    <functions id="helper-function" name="Helper Function" responsibility="Writes and publishes" writesState="true" publishesEvents="true" functionKind="COMMAND_HANDLER" writes="helper-store" publishes="helper-event" idempotency="helper-idempotency">
      <contract id="helper-contract" name="Helper Contract" inputSchema="helper-schema"/>
    </functions>
    <apis id="helper-api" name="Helper Api" apiStyle="RESOURCE_ORIENTED_HTTP">
      <routes id="helper-route" name="Helper Route" pathTemplate="/helper" method="POST" functionIntegration="helper-function"/>
      <routes id="helper-route-duplicate" name="Helper Route Duplicate" pathTemplate="/helper" method="POST" functionIntegration="helper-function"/>
    </apis>
    <channels xsi:type="integration:Topic" id="helper-channel" name="Helper Channel" encrypted="true" channelKind="TOPIC" orderingRequirement="NONE" deliverySemantics="EXACTLY_ONCE_REQUIRED" eventTypes="helper-event" producers="helper-function" consumers="helper-function" workflowConsumers="helper-workflow"/>
    <stores xsi:type="data:DataStore" id="helper-store" name="Helper Store" encrypted="true" containsPersonalData="true" storeKind="DOCUMENT" consistencyNeed="STRONG">
      <ownedDataModels id="helper-data-model" name="Helper Data Model" schema="helper-schema" dataModelKind="ENTITY">
        <storageFields id="helper-data-field" name="Helper Data Field" personalData="true" classification="PII" fieldType="STRING"/>
      </ownedDataModels>
      <accessPatterns id="helper-access" name="Helper Access" queryBy="id" operation="READ"/>
      <indexCandidates id="helper-index" name="Helper Index" partitionKeyField="id" supportsAccessPatterns="helper-access"/>
    </stores>
    <stores xsi:type="data:ObjectStore" id="helper-object-store" name="Helper Object Store" encrypted="true" containsPersonalData="true"/>
    <workflows id="helper-workflow" name="Helper Workflow" workflowKind="ORCHESTRATION">
      <steps xsi:type="workflow:StartStep" id="helper-start" name="Helper Start"/>
      <steps xsi:type="workflow:TaskStep" id="helper-task" name="Helper Task" invokesFunction="helper-function"/>
      <steps xsi:type="workflow:SuccessEndStep" id="helper-end" name="Helper End"/>
      <transitions id="helper-transition-a" name="Helper Transition A" source="helper-start" target="helper-task" defaultTransition="true"/>
      <transitions id="helper-transition-b" name="Helper Transition B" source="helper-task" target="helper-end" defaultTransition="true"/>
    </workflows>
  </services>
  <serviceMemberships id="helper-membership" name="Helper Membership" ownershipKind="OWNS" service="helper-service" element="helper-function"/>
  <implementationProfile id="helper-profile" name="Helper Profile" sourceLayout="src" buildCommand="build" primaryLanguage="TYPESCRIPT" packageManager="NPM"/>
  <schemas id="helper-schema" name="Helper Schema" semanticVersion="1.0.0" schemaKind="REQUEST">
    <fields id="helper-correlation" name="correlationId" fieldType="STRING"/>
    <fields id="helper-sensitive" name="subject" personalData="true" classification="PII" fieldType="STRING"/>
  </schemas>
  <eventTypes id="helper-event" name="Helper Event" semanticName="HelperEvent" containsPersonalData="true" schema="helper-schema" producedBy="helper-function" consumedBy="helper-function"/>
  <flows xsi:type="integration:EventFlow" id="helper-flow" name="Helper Flow" flowPurpose="Helper flow" source="helper-channel" target="helper-workflow" eventType="helper-event" channel="helper-channel"/>
  <policies xsi:type="policy:IdempotencyPolicy" id="helper-idempotency" name="Helper Idempotency" keySource="eventId" storeRequired="false" scope="function"/>
</pim:PIMModel>
""";
  }

  private static String minimalRootCoreModel() {
    return """
<?xml version="1.0" encoding="ASCII"?>
<pim:PIMModel xmi:version="2.0" xmlns:xmi="http://www.omg.org/XMI" xmlns:pim="https://modriss.org/pim/1.0" id="" name="AWS Lambda Root" domainName="" architectureStyle="API_FIRST_SERVERLESS" generatedByTransformation="true" manuallyMaintained="true">
</pim:PIMModel>
""";
  }

  private static String minimalBrokenContainerModel() {
    return """
<?xml version="1.0" encoding="ASCII"?>
<pim:PIMModel xmi:version="2.0" xmlns:xmi="http://www.omg.org/XMI" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns:data="https://modriss.org/pim/data/1.0" xmlns:integration="https://modriss.org/pim/integration/1.0" xmlns:pim="https://modriss.org/pim/1.0" xmlns:policy="https://modriss.org/pim/policy/1.0" xmlns:security="https://modriss.org/pim/security/1.0" xmlns:workflow="https://modriss.org/pim/workflow/1.0" id="broken-root" name="Broken Root" domainName="Broken" architectureStyle="HYBRID_SERVERLESS" defaultCorrelationIdName="correlationId">
  <services id="broken-service" name="Broken Service" responsibility="Broken" ownerTeam="Team" boundaryType="CAPABILITY_BASED">
    <functions id="broken-function" name="Broken Function" responsibility="Broken" functionKind="COMMAND_HANDLER">
      <contract id="broken-contract" name="Broken Contract" inputSchema="broken-schema"/>
      <triggers id="broken-trigger" name="Broken Trigger" invocationMode="SYNCHRONOUS"/>
    </functions>
    <apis id="broken-api" name="Broken Api" apiStyle="RESOURCE_ORIENTED_HTTP"/>
    <channels xsi:type="integration:Topic" id="broken-channel" name="Broken Channel" encrypted="true" channelKind="TOPIC" orderingRequirement="NONE" deliverySemantics="AT_LEAST_ONCE"/>
    <stores xsi:type="data:DataStore" id="broken-store" name="Broken Store" encrypted="true" storeKind="DOCUMENT" consistencyNeed="STRONG"/>
    <workflows id="broken-workflow" name="Broken Workflow" workflowKind="ORCHESTRATION">
      <steps xsi:type="workflow:StartStep" id="broken-start" name="Broken Start">
        <catchHandlers id="broken-handler" name="Broken Handler"/>
      </steps>
      <steps xsi:type="workflow:SuccessEndStep" id="broken-end" name="Broken End"/>
      <transitions id="broken-transition" name="Broken Transition" source="broken-start" target="broken-end" defaultTransition="true"/>
    </workflows>
  </services>
  <schemas id="broken-schema" name="Broken Schema" semanticVersion="1.0.0" schemaKind="REQUEST">
    <fields id="broken-field" name="correlationId" fieldType="STRING"/>
  </schemas>
  <eventTypes id="broken-event" name="Broken Event" semanticName="BrokenEvent" schema="broken-schema">
    <envelope id="broken-envelope" name="Broken Envelope"/>
  </eventTypes>
  <policies xsi:type="workflow:CompensationPolicy" id="orphan-compensation" name="Orphan Compensation"/>
  <policies xsi:type="policy:TimeoutPolicy" id="orphan-timeout" name="Orphan Timeout" timeoutSeconds="10"/>
  <policies xsi:type="policy:ObservabilityConfig" id="obs-with-settings" name="Obs With Settings">
    <logging id="bad-logging" name="Bad Logging" structuredLogging="true" includeCorrelationId="false"/>
    <metrics id="bad-metric" name="Bad Metric"/>
    <alerts id="bad-alert" name="Bad Alert"/>
    <slos id="bad-slo" name="Bad Slo"/>
  </policies>
  <policies xsi:type="policy:CorsPolicy" id="bad-cors" name="Bad Cors" credentialsAllowed="true" allowedOrigins="*"/>
  <configurations id="cred-config" name="Cred Config" scope="SERVICE">
    <parameters id="cred-param" name="Cred Param"/>
  </configurations>
  <secrets id="cred-secret" name="Cred Secret" generatedReferenceOnly="true" secretKind="API_KEY"/>
  <externalEndpoints id="broken-endpoint" name="Broken Endpoint" credentialsRequired="true"/>
  <readiness id="broken-readiness" name="Broken Readiness">
    <findings id="ready-target" name="Ready Target" severity="WARNING"/>
  </readiness>
  <traceModel id="broken-trace-model" name="Broken Trace Model">
    <links id="bad-trace" name="Bad Trace" linkType="TRANSFORMS_TO" confidence="MEDIUM"/>
  </traceModel>
  <identityProviders id="broken-idp" name="Broken Idp" identityKind="USER_DIRECTORY"/>
  <principals id="broken-principal" name="Broken Principal" principalKind="ROLE"/>
  <implementationProfile id="broken-profile" name="Broken Profile" sourceLayout="src" buildCommand="build" primaryLanguage="TYPESCRIPT" packageManager="NPM"/>
</pim:PIMModel>
""";
  }

  private static Set<String> executableRuleCoverage() {
    return Set.of(
        "DomainNameRequired",
        "ImplementationProfileRequiredForGeneration",
        "NoProviderSpecificNamesInModel",
        "DefaultCorrelationIdShouldBeNamed",
        "ArchitectureStyleShouldMatchContents",
        "ModelElementIdRequired",
        "PortableNameRecommended",
        "GeneratedElementShouldBeTraceable",
        "RationaleRecommendedForManuallyMaintainedElements",
        "ServiceResponsibilityRequired",
        "ServiceOwnsAtLeastOneElement",
        "OwnedElementsHaveMembershipRecords",
        "ExternallyExposedServiceNeedsApi",
        "DataOwningServiceNeedsStore",
        "ServiceOwnerTeamRecommended",
        "DeploymentUnitContainsElements",
        "DeploymentUnitTargetsEnvironment",
        "IndependentlyDeployableUnitNeedsReleaseStrategy",
        "ProdEnvironmentIsProductionLike",
        "ProdEnvironmentShouldRequireApproval",
        "ProfileHasGenerationBasics",
        "PackageManagerMatchesRuntimeLanguage",
        "RuntimeValidationShouldGenerateTypedContracts",
        "MembershipMatchesServiceOwnership",
        "FunctionResponsibilityRequired",
        "FunctionContractRequired",
        "FunctionMustBeReachable",
        "StateChangingFunctionNeedsIdempotency",
        "ReadsStateMustReferenceStores",
        "WritesStateMustReferenceStores",
        "PublishesEventsMustReferenceEventTypes",
        "ExternalCallsNeedResilienceAndTimeout",
        "FunctionDurationEstimatesShouldBeConsistent",
        "PublicFunctionShouldHaveSecurityPolicy",
        "ExactlyOneInvocationTarget",
        "DisabledTriggerShouldExplainWhy",
        "ContractHasAtLeastInputOrOutput",
        "InputValidationRequiresInputSchema",
        "OutputValidationRequiresOutputSchema",
        "CorrelationIdFieldShouldExistInSchema",
        "IdempotencyKeyFieldShouldExistInSchema",
        "SchemaHasFieldsUnlessExternal",
        "SchemaVersionShouldBeSemver",
        "ExternalSchemaShouldStateCompatibility",
        "SensitiveFieldIsClassified",
        "EnumFieldHasLiterals",
        "ObjectFieldHasObjectSchema",
        "FieldLengthBoundsAreValid",
        "RequiredNullableFieldNeedsRationale",
        "SensitiveFieldShouldNotExposeExamplesOrDefaults",
        "SchemaConstraintHasExpressionAndMessage",
        "ApiHasAtLeastOneRoute",
        "ProtectedApiHasAuthPolicy",
        "ConsumerFacingApiHasPublicMetadata",
        "GeneratedOpenApiNeedsContract",
        "ExactlyOneIntegration",
        "UniqueMethodPathWithinApi",
        "RoutePathStartsWithSlash",
        "RouteIntegrationIsExactlyOneBackend",
        "ProtectedRouteHasAuthorization",
        "RequestValidationNeedsRequestSchema",
        "ResponseValidationNeedsResponseSchema",
        "PublicRouteShouldDescribeConsumers",
        "ErrorMappingShouldBeActionable",
        "EventTypeHasSchema",
        "PersonalDataEventSchemaIsClassified",
        "EventTypeShouldHaveVersionAndSource",
        "ExternalOrReplayableEventShouldHaveEnvelope",
        "EventTypeProducerConsumerLinksConsistent",
        "EnvelopeShouldCarryCorrelationFields",
        "ChannelHasEventTypes",
        "PersonalDataChannelMustBeEncrypted",
        "ExactlyOnceRequiresIdempotentConsumers",
        "OrderedChannelHasOrderingKey",
        "ChannelShouldHaveProducerAndConsumerIntent",
        "ChannelProducerConsumerLinksConsistent",
        "RetryQueueNeedsDeadLetterChannel",
        "BatchQueueNeedsPartialFailureDecision",
        "FifoQueueNeedsDeduplicationOrIdempotency",
        "QueueShouldHaveVisibilityTimeout",
        "FilteringTopicShouldUseSubscriptionFilters",
        "EventBusShouldHaveRoutingRules",
        "RoutingRuleHasPatternOrSchedule",
        "EnabledRoutingRuleHasTargets",
        "DeadLetterSubscriptionShouldExplainHandling",
        "ScheduleExpressionRequired",
        "ScheduleShouldDeclareTimezone",
        "EnabledScheduleHasSingleTarget",
        "FlowPurposeRequired",
        "CriticalFlowNeedsResilienceAndObservability",
        "PersonalDataFlowNeedsPolicy",
        "SensitiveStorageMustBeEncrypted",
        "PersistentStorageShouldHaveRetentionPolicy",
        "PersistentStorageShouldHaveBackupDecision",
        "DataStoreHasDataModel",
        "DataStoreHasAccessPattern",
        "TransactionalStoreNeedsTransactionalConsistency",
        "SourceOfTruthShouldHavePitrOrBackup",
        "ObjectStoreWithEventsShouldDeclareEventTypes",
        "VersionedObjectStoreShouldHaveLifecycleDecision",
        "DataModelHasSchema",
        "SourceOfTruthCannotBeReadModel",
        "DataModelShouldExposeStorageFields",
        "SensitiveDataFieldIsClassified",
        "IdentifierFieldShouldBeRequired",
        "HighFrequencyAccessPatternShouldHaveIndex",
        "ProductionIndexSupportsAccessPattern",
        "DataAccessConsistentWithFunctionRefs",
        "GeneratesOrReferencesPermission",
        "DataAccessHasPurpose",
        "WorkflowHasStartStep",
        "WorkflowHasEndSteps",
        "ReachableFromStart",
        "WorkflowHasAtLeastOneEndStep",
        "StatefulWorkflowNeedsIdempotency",
        "NonStartStepShouldHaveIncomingTransition",
        "TimedStepShouldUseTimeoutPolicy",
        "ChoiceHasAtLeastTwoTransitions",
        "EndStepsHaveNoOutgoingTransitions",
        "TaskStepInvokesExactlyOneAction",
        "SameWorkflow",
        "TransitionStaysInsideWorkflow",
        "ConditionalTransitionShouldHaveConditionUnlessDefault",
        "NextStepInSameWorkflow",
        "ErrorHandlerHasRecoveryTarget",
        "ErrorHandlerShouldSelectErrors",
        "CompensationPolicyHasAction",
        "AdapterHasEndpoint",
        "CredentialRequirementForCredentialedAdapter",
        "ExternalAdapterShouldDescribeEndpointAndProtocol",
        "RateLimitedAdapterShouldHaveResilience",
        "ConfigurationSetShouldApplySomewhere",
        "RequiredParameterShouldHaveDefaultOrStageSpecificDecision",
        "SecretEnvironmentVariableReferencesSecret",
        "EnvironmentVariableNameIsPortable",
        "SecretNotPlainEnvironmentValue",
        "SecretReferenceOnly",
        "RotationRequiredNeedsFrequency",
        "EnvironmentSpecificSecretShouldHaveOwner",
        "CredentialRequirementHasSecret",
        "CredentialRequirementShouldExplainPurpose",
        "FederatedIdentityShouldDescribeTokenAndAttributes",
        "PrivilegedPrincipalNeedsPermissions",
        "PermissionIsScoped",
        "LeastPrivilegeShouldBeConfirmed",
        "AuthPolicyHasScheme",
        "AuthorizationPolicyHasDecisionLogic",
        "ResourceLevelAuthorizationShouldHaveExpression",
        "RetryPolicyBounded",
        "RetryBackoffIsValid",
        "RequiredDeadLetterPolicyHasChannel",
        "TimeoutPolicyIsPositive",
        "IdempotencyKeyRequired",
        "IdempotencyStoreDecisionRecommended",
        "ConcurrencyPolicyShouldExplainScaling",
        "RateLimitPolicyIsPositive",
        "BatchPolicyHasSizeAndFailureDecision",
        "BatchPolicyNeedsRationaleForNoPartialFailureHandling",
        "ProductionObservabilityNeedsCorrelation",
        "ObservabilityShouldEnableSignals",
        "StructuredLoggingNeedsFormat",
        "LoggingShouldIncludeCorrelationId",
        "MetricPolicyShouldNameMetricAndUnit",
        "AlertPolicyIsActionable",
        "SloShouldBeMeasurable",
        "CredentialedCorsCannotUseWildcardOrigins",
        "CorsPolicyShouldDeclareMethodsAndHeaders",
        "ProtectedDataHasRetentionDecision",
        "ClassifiedDataNeedsProtectionDecision",
        "RetentionPolicyHasPeriod",
        "RequiredBackupPolicyHasObjectives",
        "DataQualityPolicyHasMeasurementRule",
        "TraceLinkHasReferenceOrExternalId",
        "ProductionReadyRequiresPassedChecksAndNoBlockingFindings",
        "FindingShouldRecommendRemediation",
        "FailedCheckShouldHaveRemediation",
        "BlockingManualDecisionMustBeAnswered",
        "GeneratedBlockingManualDecisionShouldHaveOwner");
  }

  private static Set<String> missingCoverage(Set<String> covered) {
    Set<String> missing = new LinkedHashSet<>(executableRuleCoverage());
    missing.removeAll(covered);
    return missing;
  }

  private static Path findRepositoryRoot() {
    Path current = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
    while (current != null) {
      if (Files.isDirectory(current.resolve("mde/metamodels"))
          && Files.isDirectory(current.resolve("mde/validation/pim"))) {
        return current;
      }
      current = current.getParent();
    }
    throw new IllegalStateException("Could not locate repository root from user.dir.");
  }

  private record Scenario(
      String name, String model, boolean structuralValidation, List<Expected> expected) {}

  private record Expected(String name, EvlConstraintKind kind) {}
}
