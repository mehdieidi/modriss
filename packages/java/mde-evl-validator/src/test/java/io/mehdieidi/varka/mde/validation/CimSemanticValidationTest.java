package io.mehdieidi.varka.mde.validation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.UnaryOperator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.ResourceLock;

/** Semantic regression coverage for the CIM EVL profile. */
@ResourceLock("epsilon-runtime")
class CimSemanticValidationTest {

  private static final Path REPOSITORY_ROOT = findRepositoryRoot();
  private static final Path CIM_SAMPLE = REPOSITORY_ROOT.resolve("mde/samples/cim.xmi");
  private static final Path CIM_EVL =
      REPOSITORY_ROOT.resolve("mde/validation/cim/cim-semantic-validation.evl");
  private static final Path CIM_ECORE =
      REPOSITORY_ROOT.resolve("mde/metamodels/cim/cim-combined.ecore");
  private static final List<String> ALIASES =
      List.of(
          "CIMORG", "CIMDOMAIN", "CIMBEHAVIOR", "CIMPROCESS", "CIMGOV", "CIMTRANSFORM", "KERNEL");

  @TempDir Path tempDir;

  @Test
  void repositoryCimSamplePassesAllMandatoryAndOptionalSemantics() throws Exception {
    EvlValidationReport report = validate(CIM_SAMPLE);

    assertEquals(EvlValidationStatus.SUCCEEDED, report.status());
    assertTrue(report.diagnostics().isEmpty(), report.diagnostics().toString());
    assertTrue(report.violations().isEmpty(), report.violations().toString());
    assertFalse(report.hasMandatoryViolations());
  }

  @Test
  void cimNegativeScenariosExerciseAllDocumentedSemanticRules() throws Exception {
    Set<String> covered = new LinkedHashSet<>();

    for (Scenario scenario : scenarios()) {
      Path model = writeMutatedSample(scenario.name(), scenario.mutator());
      EvlValidationReport report = validate(scenario.name(), model);
      Set<String> actual = violationNames(report);

      for (Expected expected : scenario.expected()) {
        assertTrue(
            actual.contains(expected.name()),
            () ->
                scenario.name()
                    + " did not trigger "
                    + expected.name()
                    + ". Actual violations: "
                    + actual);
        assertEquals(
            expected.kind(),
            kindOf(report, expected.name()),
            () -> scenario.name() + " reported the wrong EVL severity for " + expected.name());
        covered.add(expected.name());
      }
    }

    assertEquals(allCimRuleNames(), covered);
  }

  @Test
  void cimEolHelpersHaveExplicitBehaviorCoverage() throws Exception {
    Path evlRoot = tempDir.resolve("helper-evl");
    Files.createDirectories(evlRoot.resolve("lib"));
    Files.createDirectories(evlRoot.resolve("../shared").normalize());
    Files.copy(
        REPOSITORY_ROOT.resolve("mde/validation/cim/lib/cim-validation-helpers.eol"),
        evlRoot.resolve("lib/cim-validation-helpers.eol"));
    Files.copy(
        REPOSITORY_ROOT.resolve("mde/validation/shared/shared-validation-helpers.eol"),
        evlRoot.resolve("../shared/shared-validation-helpers.eol").normalize());
    Path evl = evlRoot.resolve("helper-contract.evl");
    Files.writeString(
        evl,
        """
import "../shared/shared-validation-helpers.eol";
import "lib/cim-validation-helpers.eol";

context CIM!CIMModel {
  constraint TextCollectionAndLabelHelpers {
    check :
      " value ".hasText() and not "".hasText() and not Sequence{}.hasItems() and
      self.goals.hasItems() and firstText("", "fallback") = "fallback" and
      CIM!Role.all.select(r | r.`id` = "role-resident").first().labelText() =
        "Resident Applicant Role"
  }

  constraint MultiplicityHelpers {
    check {
      var bounded = CIM!DomainRelationship.all.select(r | r.`id` = "rel-app-review").first().targetMultiplicity;
      var missing = CIM!DomainRelationship.all.select(r | r.`id` = "helper-rel-missing-multiplicity").first().sourceMultiplicity;
      return bounded.hasMultiplicityBounds() and bounded.isMany() and not missing.hasMultiplicityBounds();
    }
  }

  constraint ExpressionAndInformationStructureHelpers {
    check {
      var expressionCondition = CIM!Condition.all.select(c | c.`id` = "helper-expression-condition").first();
      var textCondition = CIM!Condition.all.select(c | c.`id` = "cond-complete").first();
      var uniqueParent = CIM!InformationItem.all.select(i | i.`id` = "helper-unique-parent").first();
      var duplicateParent = CIM!InformationItem.all.select(i | i.`id` = "helper-duplicate-parent").first();
      return expressionCondition.expressionModel.hasExpressionBody() and
        not textCondition.hasExpressionBody() and collectionHasUniqueNames(uniqueParent.subItems) and
        not collectionHasUniqueNames(duplicateParent.subItems);
    }
  }

  constraint ProcessProviderAndReadinessHelpers {
    check {
      var decision = CIM!DecisionStep.all.select(s | s.`id` = "helper-decision").first();
      var providerNamed = CIM!Role.all.select(r | r.`id` = "helper-provider-name").first();
      var providerSummary = CIM!Role.all.select(r | r.`id` = "helper-provider-summary").first();
      return decision.outgoingTransitionCount() = 2 and
        providerTechnologyPattern().matches("(?i).*lambda.*") and
        providerNamed.containsProviderTechnologyTerm() and
        providerSummary.containsProviderTechnologyTerm() and
        self.isProductionReadyIntent();
    }
  }

  constraint DataPrivacyHelpers {
    check {
      var publicClass = CIM!DataClassification.all.select(c | c.`id` = "helper-class-public").first();
      var personalClass = CIM!DataClassification.all.select(c | c.`id` = "class-personal").first();
      var secretClass = CIM!DataClassification.all.select(c | c.`id` = "class-auth-secret").first();
      var personalInfo = CIM!InformationItem.all.select(i | i.`id` = "info-resident-id").first();
      var publicInfo = CIM!InformationItem.all.select(i | i.`id` = "helper-public-info").first();
      return not publicClass.isPersonalKind() and personalClass.isPersonalKind() and
        secretClass.requiresStrongProtection() and personalInfo.hasPersonalClassification() and
        personalInfo.requiresPrivacyControls() and not publicInfo.requiresPrivacyControls();
    }
  }

  constraint CommandAndQueryHelpers {
    check {
      var humanCommand = CIM!Command.all.select(c | c.`id` = "cmd-submit-app").first();
      var externalCommand = CIM!Command.all.select(c | c.`id` = "cmd-record-invoice").first();
      var internalCommand = CIM!Command.all.select(c | c.`id` = "cmd-request-docs").first();
      var personalQuery = CIM!Query.all.select(q | q.`id` = "qry-status").first();
      var publicQuery = CIM!Query.all.select(q | q.`id` = "helper-public-query").first();
      return humanCommand.hasHumanIssuer() and humanCommand.hasAuthorizationDecision() and
        externalCommand.hasExternalOrUntrustedIssuer() and
        not internalCommand.hasExternalOrUntrustedIssuer() and
        personalQuery.outputsPersonalData() and not publicQuery.outputsPersonalData();
    }
  }
}
""");

    Path model =
        writeMutatedSample(
            "helper-contract",
            xmi ->
                insertBeforeRootClose(
                    xmi.replace("productionReady=\"false\"", "productionReady=\"true\""),
                    """
  <roles id="helper-unnamed-role" />
  <roles id="helper-provider-name" name="AWS Operator" />
  <roles id="helper-provider-summary" name="Provider Summary Role" summary="Uses Lambda operational language" />
  <relationships id="helper-rel-missing-multiplicity" name="Helper relationship" source="entity-application" target="entity-reviewcase">
    <sourceMultiplicity id="helper-mult-missing-source" name="Missing source multiplicity" lowerBound="0" />
    <targetMultiplicity id="helper-mult-missing-target" name="Missing target multiplicity" lowerBound="0" />
  </relationships>
  <informationItems id="helper-unique-parent" name="Unique Parent" type="OBJECT">
    <subItems id="helper-unique-child-a" name="Child A" businessName="Child A" type="TEXT" />
    <subItems id="helper-unique-child-b" name="Child B" businessName="Child B" type="TEXT" />
  </informationItems>
  <informationItems id="helper-duplicate-parent" name="Duplicate Parent" type="OBJECT">
    <subItems id="helper-duplicate-child-a" name="Duplicate" businessName="Duplicate" type="TEXT" />
    <subItems id="helper-duplicate-child-b" name="Duplicate" businessName="Duplicate" type="TEXT" />
  </informationItems>
  <conditions id="helper-expression-condition" name="Helper Expression Condition">
    <expressionModel id="helper-expression" name="Helper Expression" language="FEEL" body="x = true" />
  </conditions>
  <processes id="helper-process" name="Helper Process" businessTriggerDescription="Trigger" completionCriterion="Complete">
    <steps xsi:type="cimprocess:DecisionStep" id="helper-decision" name="Helper Decision" stepKind="DECISION" condition="cond-complete" />
    <steps xsi:type="cimprocess:EndStep" id="helper-end-a" name="Helper End A" stepKind="END" />
    <steps xsi:type="cimprocess:EndStep" id="helper-end-b" name="Helper End B" stepKind="END" />
    <transitions id="helper-transition-a" name="Helper Transition A" source="helper-decision" target="helper-end-a" />
    <transitions id="helper-transition-b" name="Helper Transition B" source="helper-decision" target="helper-end-b" />
  </processes>
  <classifications id="helper-class-public" name="Helper Public Classification" kind="PUBLIC" identifiability="NON_PERSONAL" />
  <classifications id="class-auth-secret" name="Authentication Secret Classification" kind="AUTHENTICATION_SECRET" encryptionExpected="true" auditAccessRequired="true" />
  <informationItems id="helper-public-info" name="HelperPublicInfo" businessName="Helper public info" type="TEXT" classification="helper-class-public" />
  <queries id="helper-public-query" name="Helper Public Query" queryType="LOOKUP" output="helper-public-info" />
"""));

    EvlValidationReport report;
    try {
      report =
          new EpsilonEvlValidator()
              .validate(
                  EvlValidationRequest.forRoot(
                      evl,
                      List.of(
                          FileEvlModelConfiguration.readOnly(
                              "CIM", ALIASES, model, List.of(CIM_ECORE))),
                      true));
    } catch (EvlValidationException ex) {
      throw new AssertionError(
          "helper-contract failed with diagnostics: " + ex.getReport().diagnostics(), ex);
    }

    assertEquals(EvlValidationStatus.SUCCEEDED, report.status());
    assertTrue(report.diagnostics().isEmpty(), report.diagnostics().toString());
    assertTrue(report.violations().isEmpty(), report.violations().toString());
  }

  private List<Scenario> scenarios() {
    return List.of(
        scenario(
            "core-root-readiness",
            xmi ->
                xmi.replace(
                        "domainName=\"Climate Relief Grants and Reimbursements\"",
                        "domainName=\"\"")
                    .replace(
                        " lifecycleStatus=\"APPROVED\"", " lifecycleStatus=\"PRODUCTION_READY\"")
                    .replace(
                        "authorizationRequired=\"true\" authorizationRule=\"Verified resident may"
                            + " submit only their own household application\"",
                        "authorizationRequired=\"false\"")
                    .replace(" classification=\"class-personal\"", ""),
            mandatory("ProductionReadyModelHasNoBlockers"),
            mandatory("TransformationProfileExplicitAuthIsEnforced"),
            mandatory("TransformationProfilePrivacyClassificationIsEnforced"),
            optional("CIMModelDeclaresBusinessScope")),
        scenario(
            "kernel-trace",
            xmi ->
                insertIntoExistingTraceModel(
                    insertBeforeRootClose(
                        xmi,
                        """
  <actors id="actor-generated-bad" name="Generated actor" actorType="HUMAN" trustLevel="TRUSTED_INTERNAL" generatedByTransformation="true" />
  <roles id="role-kernel-bad" name="AWS Administrator" />
  <roles id="role-no-doc" name="Role Without Explanation" sourceReference="Synthetic semantic coverage" />
"""),
                    """
    <links id="trace-link-empty" name="Trace Link Empty" />
    <links id="trace-link-self" name="Trace Link Self" source="actor-resident" target="actor-resident" />
"""),
            mandatory("TraceLinkHasReferenceOrExternalId"),
            mandatory("TraceLinkDoesNotPointToSameElement"),
            mandatory("GeneratedElementHasOrigin"),
            mandatory("ModelElementAvoidsProviderSpecificTerms"),
            optional("ModelElementHasExplanation"),
            optional("TraceableElementHasSourceOrRationale")),
        scenario(
            "organization-intent",
            xmi ->
                insertBeforeRootClose(
                    xmi,
                    """
  <requirements id="req-empty-bad" name="Empty Requirement" />
  <requirements id="req-bad" name="Bad Requirement" productionBlocking="true" mandatory="false" dependsOn="req-bad" conflictsWith="req-bad">
    <acceptanceCriteria id="ac-bad" name="Bad AC" automatableTestCandidate="true" />
  </requirements>
  <goals id="goal-bad" name="Bad Goal" priority="CRITICAL" />
  <kpis id="kpi-bad" name="Bad KPI" operator="approximately" />
  <stakeholders id="stakeholder-bad" name="Bad Stakeholder" />
  <actors id="actor-bad" name="Bad Actor" />
  <actors id="actor-human-bad" name="Bad Human" actorType="HUMAN" trustLevel="TRUSTED_INTERNAL" />
  <actors xsi:type="cimorg:ExternalSystem" id="ext-bad" name="Bad External" actorType="HUMAN" trustLevel="UNTRUSTED_EXTERNAL" storesBusinessData="true" />
  <roles id="role-priv-bad" name="Bad Privileged Role" privileged="true" />
  <roles id="role-unassigned-bad" name="Bad Unassigned Role" responsibility="Unused" businessPermissionSummary="Unused" />
  <capabilities id="cap-bad" name="Bad Capability" criticality="CORE" />
  <capabilityDependencies id="capdep-bad" name="Bad Capability Dependency" source="cap-intake" target="cap-intake" criticalPath="true" />
  <boundedContexts id="bc-bad" name="Bad Bounded Context">
    <glossaryTerms id="term-bad" term="bad" />
  </boundedContexts>
  <boundedContexts id="bc-dup" name="Duplicate Terms" languageBoundary="Language" ownershipBoundary="Owner">
    <glossaryTerms id="term-dup-1" name="Dup 1" term="duplicate" definition="One" />
    <glossaryTerms id="term-dup-2" name="Dup 2" term="duplicate" definition="Two" />
  </boundedContexts>
"""),
            mandatory("RequirementHasFitCriterionOrAcceptanceCriteria"),
            mandatory("ProductionBlockingRequirementIsMandatory"),
            mandatory("RequirementDoesNotDependOnItself"),
            optional("RequirementSupportsGoalOrConstrainsElement"),
            mandatory("AcceptanceCriterionUsesGivenWhenThen"),
            optional("AutomatableAcceptanceCriterionHasTarget"),
            mandatory("BusinessGoalHasSuccessCriterion"),
            optional("CriticalGoalHasOwnerAndKpi"),
            optional("BusinessGoalExplainsValueAndRisk"),
            mandatory("KPIIsMeasurable"),
            optional("KPIHasMeasurementMethod"),
            optional("KPIHasRecognisableOperator"),
            optional("StakeholderConcernIsDeclared"),
            optional("HumanActorHasRole"),
            mandatory("ExternalActorDeclaresAuthExpectations"),
            mandatory("ActorTypeMatchesSubclass"),
            mandatory("ExternalSystemDeclaresPurposeAndTrust"),
            mandatory("ExternalSystemDataExchangeIsExplicit"),
            mandatory("PrivilegedRoleHasPermissionSummary"),
            optional("RoleAssignedToActor"),
            mandatory("CapabilitySupportsGoal"),
            mandatory("CapabilityHasOwnerOrResponsibility"),
            optional("CapabilityHasBehaviorOrManagedData"),
            optional("CriticalCapabilityHasRequirementsAndNfrs"),
            mandatory("CapabilityDependencyIsMeaningful"),
            optional("CriticalDependencyHasRationale"),
            mandatory("BoundedContextHasBoundaryDefinition"),
            optional("BoundedContextHasScopedContent"),
            mandatory("UbiquitousLanguageTermHasDefinition"),
            optional("UbiquitousLanguageTermIsUniqueInContext")),
        scenario(
            "domain-data",
            xmi ->
                insertBeforeRootClose(
                    xmi,
                    """
  <entities id="entity-bad" name="Bad Entity" identityStrategy="SURROGATE_KEY" primaryIdentityAttribute="info-application-id" identityAttributes="info-household-id" attributes="info-household-id">
    <lifecycleStates id="ls-bad-1" name="Bad State One" stateName="One" initial="true" />
    <lifecycleStates id="ls-bad-2" name="Bad State Two" stateName="Two" initial="true" />
    <invariants id="inv-bad" name="Bad Invariant" />
  </entities>
  <valueObjects id="vo-bad" name="Bad Value Object" immutable="false" />
  <relationships id="rel-bad" name="Bad Relationship" source="entity-application" target="entity-application" ownership="true" relationshipType="ASSOCIATION">
    <sourceMultiplicity id="mult-bad-source" name="Bad source multiplicity" lowerBound="1" upperBound="1" />
    <targetMultiplicity id="mult-bad-target" name="Bad target multiplicity" lowerBound="0" unbounded="true" />
  </relationships>
  <commands id="cmd-domain-bad" name="Domain Bad Command" duplicateSubmissionPossible="true" expectedEvents="evt-submitted" targetAggregate="agg-bad" />
  <aggregates id="agg-bad" name="Bad Aggregate" root="entity-application" members="entity-reviewcase" strongConsistencyRequired="true" consistencyExpectation="EVENTUAL_CONSISTENCY_ACCEPTABLE" handledCommands="cmd-domain-bad" />
  <informationItems id="info-parent-bad" name="Parent Bad" businessName="Parent Bad" type="TEXT">
    <subItems id="info-child-dup-1" name="Child" businessName="Child" type="TEXT" />
    <subItems id="info-child-dup-2" name="Child" businessName="Child" type="TEXT" />
  </informationItems>
  <informationItems id="info-type-bad" businessName="Type Bad" />
  <informationItems id="info-name-bad" type="TEXT" />
  <informationItems id="info-privacy-bad" name="Privacy Bad" type="TEXT" classification="class-personal" />
  <informationItems id="info-collection-bad" name="Collection Bad" type="LIST" collection="true" />
  <informationItems id="info-derived-bad" name="Derived Bad" type="TEXT" derived="true" />
  <informationItems id="info-report-bad" name="Report Bad" type="TEXT" reportingRelevant="true" />
  <informationItems id="info-length-bad" name="Length Bad" type="TEXT" minLength="10" maxLength="3" />
  <classifications id="class-bad" name="Bad Classification" />
  <classifications id="class-sensitive-bad" name="Sensitive Bad" kind="SENSITIVE_PERSONAL" encryptionExpected="false" auditAccessRequired="false" />
  <classifications id="class-personal-bad" name="Personal Bad" kind="PERSONAL" identifiability="NON_PERSONAL" />
  <classifications id="class-regulated-bad" name="Regulated Bad" kind="REGULATED" encryptionExpected="true" auditAccessRequired="true" identifiability="DIRECTLY_IDENTIFYING" />
"""),
            optional("DomainConceptHasBusinessDefinition"),
            mandatory("IdentityAttributesAreOwnedAttributes"),
            optional("EntityHasOwningCapability"),
            mandatory("LifecycleStatesHaveSingleInitialState"),
            optional("ValueObjectShouldBeImmutable"),
            mandatory("ValueObjectDefinesEquality"),
            mandatory("DomainRelationshipHasDistinctEnds"),
            optional("DomainRelationshipHasRolesAndMultiplicities"),
            mandatory("OwnershipRelationshipUsesOwnershipType"),
            mandatory("RootIsMember"),
            mandatory("StrongConsistencyRequiresRationale"),
            mandatory("StrongConsistencyExpectationIsConsistent"),
            optional("EventualConsistencyHasConflictPolicy"),
            optional("AggregateCommandsDeclareIdempotency"),
            mandatory("BusinessInvariantIsExpressed"),
            optional("BusinessInvariantConstrainsConcepts"),
            mandatory("StructuredItemsUseObjectOrList"),
            mandatory("UniqueChildNames"),
            mandatory("InformationItemHasBusinessName"),
            mandatory("PersonalOrSensitiveInformationHasPrivacyConstraint"),
            mandatory("CollectionInformationHasMultiplicity"),
            mandatory("DerivedInformationHasDerivationRule"),
            optional("SearchOrReportItemHasSourceOfTruth"),
            mandatory("LengthBoundsAreConsistent"),
            mandatory("SensitiveDataRequiresProtectionExpectation"),
            mandatory("PersonalDataHasIdentifiability"),
            optional("RegulatedDataHasRegulatoryCategory")),
        scenario(
            "behavior",
            xmi ->
                insertBeforeRootClose(
                    xmi,
                    """
  <commands id="cmd-bad" name="Bad Command" issuedBy="actor-resident" duplicateSubmissionPossible="true">
    <outcomes id="outcome-bad" name="Bad Outcome" success="true" emittedEvents="evt-approved" errors="err-eligibility" />
  </commands>
  <commands id="cmd-external-bad" name="Bad External Command" issuedBy="ext-identity" expectedEvents="evt-submitted" targetCapability="cap-intake" />
  <queries id="qry-bad" name="Bad Query" queryType="LIST" containsPersonalData="true" output="info-resident-id" />
  <queries id="qry-flag-bad" name="Bad Flag Query" queryType="SEARCH" containsPersonalData="false" output="info-resident-id" authorizationRequired="true" authorizationRule="Reader must be authorized" />
  <events id="evt-bad" name="ApproveCustomer" externallyVisible="true" semanticVersion="one" payload="info-resident-id" />
  <businessErrors id="err-bad" name="Bad Error" retryMeaningful="true" recoverable="false" />
  <conditions id="cond-bad" name="Bad Condition" mustBeAutomatable="true" />
"""),
            mandatory("CommandMustCauseBusinessOutcome"),
            mandatory("CommandHasBehavioralOwner"),
            mandatory("ActorFacingCommandHasAuthorizationDecision"),
            mandatory("ExternalOrUntrustedCommandRequiresAuditAndAuthorization"),
            optional("DuplicateSubmissionCommandHasIdempotencyKey"),
            optional("CommandHasPreconditionsOrErrors"),
            optional("CommandOutcomeLinksMatchDirectCollections"),
            mandatory("PersonalQueryMustDeclareAuthorization"),
            optional("QueryPersonalFlagMatchesOutput"),
            optional("ListSearchQueryDeclaresResultHandling"),
            mandatory("EventNamePastTense"),
            mandatory("EventIsBusinessFactNotCommand"),
            mandatory("EventHasBusinessMeaning"),
            optional("EventHasProducerOrConsumer"),
            mandatory("ProductionRelevantEventHasVersioningMetadata"),
            mandatory("EventSemanticVersionLooksLikeSemVer"),
            mandatory("BusinessErrorIsUserUnderstandable"),
            optional("RetryMeaningfulImpliesRecoverable"),
            mandatory("ConditionIsSpecified"),
            mandatory("AutomatableConditionHasExpression"),
            optional("ConditionReferencesInformationOrConcepts")),
        scenario(
            "process-policy",
            xmi ->
                insertBeforeRootClose(
                    xmi,
                    """
  <processes id="proc-bad" name="Bad Process" longRunning="true" compensationExpected="true">
    <steps xsi:type="cimprocess:CommandStep" id="step-bad" name="Bad Step" stepKind="COMMAND" orderIndex="1" command="cmd-submit-app" />
    <steps xsi:type="cimprocess:QueryStep" id="step-bad-2" name="Bad Step Two" stepKind="QUERY" orderIndex="1" query="qry-status" />
    <steps xsi:type="cimprocess:StartStep" id="start-bad" name="Bad Start" stepKind="END" />
    <steps xsi:type="cimprocess:EndStep" id="end-bad" name="Bad End" stepKind="START" />
    <steps xsi:type="cimprocess:CommandStep" id="command-step-bad" name="Bad Command Step" stepKind="QUERY" command="cmd-submit-app" />
    <steps xsi:type="cimprocess:QueryStep" id="query-step-bad" name="Bad Query Step" stepKind="COMMAND" query="qry-status" />
    <steps xsi:type="cimprocess:EventStep" id="event-step-bad" name="Bad Event Step" stepKind="COMMAND" event="evt-submitted" />
    <steps xsi:type="cimprocess:PolicyStep" id="policy-step-bad" name="Bad Policy Step" stepKind="COMMAND" policy="pol-eligibility" />
    <steps xsi:type="cimprocess:HumanTaskStep" id="human-step-bad" name="Bad Human Step" stepKind="COMMAND" />
    <steps xsi:type="cimprocess:ExternalInteractionStep" id="external-step-bad" name="Bad External Step" stepKind="COMMAND" externalSystem="ext-identity" />
    <steps xsi:type="cimprocess:DecisionStep" id="decision-step-bad" name="Bad Decision Step" stepKind="COMMAND" />
    <steps xsi:type="cimprocess:WaitStep" id="wait-step-bad" name="Bad Wait Step" stepKind="COMMAND" />
    <transitions id="transition-self-bad" name="Bad Self Transition" source="step-bad" target="step-bad" />
    <transitions id="transition-end-start-bad" name="Bad End Start Transition" source="start-bad" target="end-bad" />
    <transitions id="transition-cross-process-bad" name="Bad Cross Process Transition" source="step-bad" target="step-no-start-end-bad" />
    <exceptions id="exception-bad" name="Bad Exception" recoverable="true" compensationRequired="true" />
    <temporalConstraints id="time-bad" name="Bad Time" />
  </processes>
  <processes id="proc-no-start-end-bad" name="Bad Process No Start End" businessTriggerDescription="Manual trigger" completionCriterion="Done">
    <steps xsi:type="cimprocess:CommandStep" id="step-no-start-end-bad" name="No Start End Step" stepKind="COMMAND" command="cmd-submit-app" />
  </processes>
  <processes id="proc-long-no-time-bad" name="Bad Long Process" longRunning="true" businessTriggerDescription="Manual trigger" completionCriterion="Done">
    <steps xsi:type="cimprocess:StartStep" id="long-start-bad" name="Long Start" stepKind="START" />
    <steps xsi:type="cimprocess:EndStep" id="long-end-bad" name="Long End" stepKind="END" />
  </processes>
  <processes id="proc-compensation-no-exception-bad" name="Bad Compensation Process" compensationExpected="true" businessTriggerDescription="Manual trigger" completionCriterion="Done">
    <steps xsi:type="cimprocess:StartStep" id="comp-start-bad" name="Comp Start" stepKind="START" />
    <steps xsi:type="cimprocess:EndStep" id="comp-end-bad" name="Comp End" stepKind="END" />
  </processes>
  <decisionTables id="dt-bad" name="Bad Decision Table" complete="false">
    <rules id="rule-bad-1" name="Bad Rule One" priorityOrder="1" />
    <rules id="rule-bad-2" name="Bad Rule Two" priorityOrder="1" />
  </decisionTables>
  <policies id="policy-bad" name="Bad Policy" enforcementStrength="MANDATORY" policyType="REACTION" />
"""),
            mandatory("ProcessHasStartAndEnd"),
            mandatory("ProcessHasTriggerAndCompletionCriterion"),
            mandatory("LongRunningProcessHasTemporalConstraint"),
            mandatory("ProcessStepOrderIndexesAreUnique"),
            optional("CompensationProcessHasExceptionScenarios"),
            optional("ProcessStepHasResponsibility"),
            mandatory("StartStepKindMatchesClass"),
            mandatory("EndStepKindMatchesClass"),
            mandatory("CommandStepKindMatchesClass"),
            mandatory("QueryStepKindMatchesClass"),
            mandatory("EventStepKindMatchesClass"),
            mandatory("PolicyStepKindMatchesClass"),
            mandatory("HumanTaskStepKindAndDescription"),
            mandatory("ExternalInteractionStepIsExplicit"),
            optional("ExternalInteractionDeclaresInformation"),
            mandatory("DecisionStepHasDecisionLogic"),
            mandatory("DecisionHasBranches"),
            mandatory("WaitStepHasDurationOrReason"),
            mandatory("TransitionConnectsDifferentSteps"),
            mandatory("TransitionDoesNotLeaveEndOrEnterStart"),
            mandatory("DecisionTableRulesHaveUniquePriority"),
            optional("IncompleteDecisionTableHasDefault"),
            mandatory("DecisionRuleHasConditionAndOutcome"),
            optional("DecisionRuleHasExecutableEffect"),
            mandatory("PolicyEitherReactsOrGuards"),
            mandatory("PolicyHasRuleDefinition"),
            optional("ReactionPolicyEmitsOutcome"),
            mandatory("ExceptionScenarioIsActionable"),
            optional("CompensationScenarioEmitsEvent"),
            mandatory("TemporalConstraintHasExpression"),
            optional("TemporalConstraintHasScope")),
        scenario(
            "governance-readiness",
            xmi ->
                insertIntoExistingReadiness(
                    insertBeforeRootClose(
                        xmi.replace(
                                " defaultServiceGranularityRationale=\"Capabilities and"
                                    + " long-running processes provide the cleanest"
                                    + " provider-neutral candidates for later serverless"
                                    + " decomposition\"",
                                "")
                            .replace("productionReady=\"false\"", "productionReady=\"true\"")
                            .replace(
                                " assessedAt=\"2026-05-24T00:00:00.000Z\" assessedBy=\"Model"
                                    + " evaluation team\"",
                                "")
                            .replace(" decisionOwner=\"Senior Reviewer\"", "")
                            .replace(" dueDate=\"2026-06-12T00:00:00.000Z\"", ""),
                        """
  <requirements xsi:type="cimgov:NonFunctionalRequirement" id="nfr-bad" name="Bad NFR" productionBlocking="true" fitCriterion="Bad" constrainedElements="cap-intake" />
  <commands id="cmd-governance-bad" name="Governance Bad Command" expectedEvents="evt-submitted" targetCapability="cap-intake" />
  <requirements xsi:type="cimgov:SecurityConstraint" id="sec-empty-bad" name="Bad Empty Security" fitCriterion="Bad" constrainedElements="cap-intake" />
  <requirements xsi:type="cimgov:SecurityConstraint" id="sec-bad" name="Bad Security" fitCriterion="Bad" constrainedElements="cap-intake" constrainedCommands="cmd-governance-bad" authorizationRule="Must be authorized" />
  <requirements xsi:type="cimgov:PrivacyConstraint" id="priv-bad" name="Bad Privacy" fitCriterion="Bad" constrainedElements="info-resident-id" dataItems="info-resident-id" legalBasis="CONSENT" consentRequired="false" dataResidencyRequired="true" />
  <requirements xsi:type="cimgov:ComplianceConstraint" id="comp-bad" name="Bad Compliance" fitCriterion="Bad" constrainedElements="cap-intake" />
  <risks id="risk-bad" name="Bad Risk" productionBlocking="true" />
  <assumptions id="asm-bad" name="Bad Assumption" accepted="true" />
  <hotspots id="hot-bad" name="Bad Hotspot" blocksProduction="true" />
"""),
                    """
    <checks id="ready-check-bad" name="Bad Check" checkId="bad-check" passed="false" />
    <findings id="ready-finding-bad" name="Bad Finding" blocking="true" />
    <manualDecisions id="manual-decision-bad" name="Bad Manual Decision" question="Who decides this?" blocking="true" />
"""),
            mandatory("NFRMustBeMeasurable"),
            optional("ProductionBlockingNFRHasScenario"),
            mandatory("SecurityConstraintMustConstrainBehaviorOrInformation"),
            mandatory("SecurityConstraintHasRuleOrThreatRationale"),
            optional("SecurityConstraintAlignsCommandAuthorization"),
            optional("SpecializedScopeIncludedInGenericScope"),
            mandatory("PrivacyNeedsPurposeAndLegalBasis"),
            mandatory("PrivacyConstraintHasRetentionOrMinimization"),
            mandatory("ConsentBasisRequiresConsent"),
            optional("DataResidencyHasAllowedArea"),
            mandatory("ComplianceConstraintIsAuditable"),
            optional("ComplianceConstraintHasScope"),
            mandatory("RiskIsActionableWhenBlocking"),
            optional("RiskHasAffectedElements"),
            mandatory("AssumptionHasStatementAndValidation"),
            mandatory("AcceptedAssumptionHasDecisionDetails"),
            mandatory("BlockingHotspotRequiresOwner"),
            optional("BlockingHotspotHasDueDateAndImpact"),
            optional("ServiceGranularityPreferenceHasRationale"),
            optional("RequiredManualDecisionsAreOwned"),
            mandatory("ProductionReadyAssessmentIsConsistent"),
            optional("ReadinessAssessmentHasAssessor"),
            mandatory("BlockingFindingHasRecommendation"),
            mandatory("FailedReadinessCheckHasRemediation"),
            mandatory("BlockingManualDecisionHasOwner"),
            optional("ManualDecisionHasDueDate")));
  }

  private static Scenario scenario(
      String name, UnaryOperator<String> mutator, Expected... expected) {
    return new Scenario(name, mutator, List.of(expected));
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
    try {
      return new EpsilonEvlValidator()
          .validate(
              EvlValidationRequest.forRoot(
                  CIM_EVL,
                  List.of(
                      FileEvlModelConfiguration.readOnly(
                          "CIM", ALIASES, model, List.of(CIM_ECORE))),
                  true));
    } catch (EvlValidationException ex) {
      throw new AssertionError(
          scenario + " failed with diagnostics: " + ex.getReport().diagnostics(), ex);
    }
  }

  private Path writeMutatedSample(String name, UnaryOperator<String> mutator) throws Exception {
    Path model = tempDir.resolve(name + ".cim.xmi");
    Files.writeString(model, mutator.apply(Files.readString(CIM_SAMPLE)));
    return model;
  }

  private static String insertBeforeRootClose(String xmi, String fragment) {
    return xmi.replace("</cim:CIMModel>", fragment + "\n</cim:CIMModel>");
  }

  private static String insertIntoExistingTraceModel(String xmi, String fragment) {
    return xmi.replace("</traceModel>", fragment + "\n  </traceModel>");
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

  private static Set<String> allCimRuleNames() {
    return Set.of(
        "CIMModelDeclaresBusinessScope",
        "ProductionReadyModelHasNoBlockers",
        "TransformationProfileExplicitAuthIsEnforced",
        "TransformationProfilePrivacyClassificationIsEnforced",
        "TraceLinkHasReferenceOrExternalId",
        "TraceLinkDoesNotPointToSameElement",
        "GeneratedElementHasOrigin",
        "ModelElementAvoidsProviderSpecificTerms",
        "ModelElementHasExplanation",
        "TraceableElementHasSourceOrRationale",
        "RequirementHasFitCriterionOrAcceptanceCriteria",
        "ProductionBlockingRequirementIsMandatory",
        "RequirementDoesNotDependOnItself",
        "RequirementSupportsGoalOrConstrainsElement",
        "AcceptanceCriterionUsesGivenWhenThen",
        "AutomatableAcceptanceCriterionHasTarget",
        "BusinessGoalHasSuccessCriterion",
        "CriticalGoalHasOwnerAndKpi",
        "BusinessGoalExplainsValueAndRisk",
        "KPIIsMeasurable",
        "KPIHasMeasurementMethod",
        "KPIHasRecognisableOperator",
        "StakeholderConcernIsDeclared",
        "HumanActorHasRole",
        "ExternalActorDeclaresAuthExpectations",
        "ActorTypeMatchesSubclass",
        "ExternalSystemDeclaresPurposeAndTrust",
        "ExternalSystemDataExchangeIsExplicit",
        "PrivilegedRoleHasPermissionSummary",
        "RoleAssignedToActor",
        "CapabilitySupportsGoal",
        "CapabilityHasOwnerOrResponsibility",
        "CapabilityHasBehaviorOrManagedData",
        "CriticalCapabilityHasRequirementsAndNfrs",
        "CapabilityDependencyIsMeaningful",
        "CriticalDependencyHasRationale",
        "BoundedContextHasBoundaryDefinition",
        "BoundedContextHasScopedContent",
        "UbiquitousLanguageTermHasDefinition",
        "UbiquitousLanguageTermIsUniqueInContext",
        "DomainConceptHasBusinessDefinition",
        "IdentityAttributesAreOwnedAttributes",
        "EntityHasOwningCapability",
        "LifecycleStatesHaveSingleInitialState",
        "ValueObjectShouldBeImmutable",
        "ValueObjectDefinesEquality",
        "DomainRelationshipHasDistinctEnds",
        "DomainRelationshipHasRolesAndMultiplicities",
        "OwnershipRelationshipUsesOwnershipType",
        "RootIsMember",
        "StrongConsistencyRequiresRationale",
        "StrongConsistencyExpectationIsConsistent",
        "EventualConsistencyHasConflictPolicy",
        "AggregateCommandsDeclareIdempotency",
        "BusinessInvariantIsExpressed",
        "BusinessInvariantConstrainsConcepts",
        "StructuredItemsUseObjectOrList",
        "UniqueChildNames",
        "InformationItemHasBusinessName",
        "PersonalOrSensitiveInformationHasPrivacyConstraint",
        "CollectionInformationHasMultiplicity",
        "DerivedInformationHasDerivationRule",
        "SearchOrReportItemHasSourceOfTruth",
        "LengthBoundsAreConsistent",
        "SensitiveDataRequiresProtectionExpectation",
        "PersonalDataHasIdentifiability",
        "RegulatedDataHasRegulatoryCategory",
        "CommandMustCauseBusinessOutcome",
        "CommandHasBehavioralOwner",
        "ActorFacingCommandHasAuthorizationDecision",
        "ExternalOrUntrustedCommandRequiresAuditAndAuthorization",
        "DuplicateSubmissionCommandHasIdempotencyKey",
        "CommandHasPreconditionsOrErrors",
        "CommandOutcomeLinksMatchDirectCollections",
        "PersonalQueryMustDeclareAuthorization",
        "QueryPersonalFlagMatchesOutput",
        "ListSearchQueryDeclaresResultHandling",
        "EventNamePastTense",
        "EventIsBusinessFactNotCommand",
        "EventHasBusinessMeaning",
        "EventHasProducerOrConsumer",
        "ProductionRelevantEventHasVersioningMetadata",
        "EventSemanticVersionLooksLikeSemVer",
        "BusinessErrorIsUserUnderstandable",
        "RetryMeaningfulImpliesRecoverable",
        "ConditionIsSpecified",
        "AutomatableConditionHasExpression",
        "ConditionReferencesInformationOrConcepts",
        "ProcessHasStartAndEnd",
        "ProcessHasTriggerAndCompletionCriterion",
        "LongRunningProcessHasTemporalConstraint",
        "ProcessStepOrderIndexesAreUnique",
        "CompensationProcessHasExceptionScenarios",
        "ProcessStepHasResponsibility",
        "StartStepKindMatchesClass",
        "EndStepKindMatchesClass",
        "CommandStepKindMatchesClass",
        "QueryStepKindMatchesClass",
        "EventStepKindMatchesClass",
        "PolicyStepKindMatchesClass",
        "HumanTaskStepKindAndDescription",
        "ExternalInteractionStepIsExplicit",
        "ExternalInteractionDeclaresInformation",
        "DecisionStepHasDecisionLogic",
        "DecisionHasBranches",
        "WaitStepHasDurationOrReason",
        "TransitionConnectsDifferentSteps",
        "TransitionDoesNotLeaveEndOrEnterStart",
        "DecisionTableRulesHaveUniquePriority",
        "IncompleteDecisionTableHasDefault",
        "DecisionRuleHasConditionAndOutcome",
        "DecisionRuleHasExecutableEffect",
        "PolicyEitherReactsOrGuards",
        "PolicyHasRuleDefinition",
        "ReactionPolicyEmitsOutcome",
        "ExceptionScenarioIsActionable",
        "CompensationScenarioEmitsEvent",
        "TemporalConstraintHasExpression",
        "TemporalConstraintHasScope",
        "NFRMustBeMeasurable",
        "ProductionBlockingNFRHasScenario",
        "SecurityConstraintMustConstrainBehaviorOrInformation",
        "SecurityConstraintHasRuleOrThreatRationale",
        "SecurityConstraintAlignsCommandAuthorization",
        "SpecializedScopeIncludedInGenericScope",
        "PrivacyNeedsPurposeAndLegalBasis",
        "PrivacyConstraintHasRetentionOrMinimization",
        "ConsentBasisRequiresConsent",
        "DataResidencyHasAllowedArea",
        "ComplianceConstraintIsAuditable",
        "ComplianceConstraintHasScope",
        "RiskIsActionableWhenBlocking",
        "RiskHasAffectedElements",
        "AssumptionHasStatementAndValidation",
        "AcceptedAssumptionHasDecisionDetails",
        "BlockingHotspotRequiresOwner",
        "BlockingHotspotHasDueDateAndImpact",
        "ServiceGranularityPreferenceHasRationale",
        "RequiredManualDecisionsAreOwned",
        "ProductionReadyAssessmentIsConsistent",
        "ReadinessAssessmentHasAssessor",
        "BlockingFindingHasRecommendation",
        "FailedReadinessCheckHasRemediation",
        "BlockingManualDecisionHasOwner",
        "ManualDecisionHasDueDate");
  }

  private static Path findRepositoryRoot() {
    Path current = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
    while (current != null) {
      if (Files.isDirectory(current.resolve("mde/metamodels"))
          && Files.isDirectory(current.resolve("mde/validation/cim"))) {
        return current;
      }
      current = current.getParent();
    }
    throw new IllegalStateException("Could not locate repository root from user.dir.");
  }

  private record Scenario(String name, UnaryOperator<String> mutator, List<Expected> expected) {}

  private record Expected(String name, EvlConstraintKind kind) {}
}
