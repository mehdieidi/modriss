package io.mehdieidi.varka.mde.validation;

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

/** Semantic regression coverage for the AWS PSM EVL profile. */
@ResourceLock("epsilon-runtime")
class PsmSemanticValidationTest {

  private static final Path REPOSITORY_ROOT = findRepositoryRoot();
  private static final Path PSM_SAMPLE = REPOSITORY_ROOT.resolve("mde/samples/psm.xmi");
  private static final Path PSM_EVL =
      REPOSITORY_ROOT.resolve("mde/validation/psm/psm-semantic-validation.evl");
  private static final Path PSM_ECORE =
      REPOSITORY_ROOT.resolve("mde/metamodels/psm/psm-combined.ecore");
  private static final List<String> ALIASES = List.of("AWSPSMENUMS", "KERNEL");

  private static final String VALID_LAMBDA_ID = "4b085e12-363a-4503-99bc-82bc05510faf";
  private static final String VALID_QUEUE_ID = "66ae5642-5515-4f08-8b46-4c07cd6f84b4";
  private static final String VALID_TOPIC_ID = "9f5c8c43-9c27-4ec8-8584-4ca7f77eb89b";
  private static final String VALID_ROLE_ID = "f7676660-8cab-4df9-82ac-036cf296c869";
  private static final String VALID_LOG_GROUP_ID = "89d73da1-2ded-408a-80c5-f7d4f1754c4d";

  @TempDir Path tempDir;

  @Test
  void repositoryPsmSamplePassesMandatorySemanticsAndReportsOptionalReadinessGaps()
      throws Exception {
    EvlValidationReport report = validate(PSM_SAMPLE);

    assertEquals(EvlValidationStatus.SUCCEEDED, report.status());
    assertTrue(report.diagnostics().isEmpty(), report.diagnostics().toString());
    assertFalse(report.hasMandatoryViolations());
    assertEquals(
        0,
        report.violations().stream().filter(v -> v.kind() == EvlConstraintKind.MANDATORY).count());
    assertTrue(
        violationNames(report).contains("ProductionRoleShouldUsePermissionsBoundary"),
        "The sample should preserve optional production hardening advice.");
  }

  @Test
  void psmNegativeScenariosExerciseDocumentedSemanticRules() throws Exception {
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
  void psmEolHelpersHaveExplicitBehaviorCoverage() throws Exception {
    Path evlRoot = tempDir.resolve("helper-evl");
    Files.createDirectories(evlRoot.resolve("lib"));
    Files.createDirectories(evlRoot.resolve("../shared").normalize());
    Files.copy(
        REPOSITORY_ROOT.resolve("mde/validation/psm/lib/psm-validation-helpers.eol"),
        evlRoot.resolve("lib/psm-validation-helpers.eol"));
    Files.copy(
        REPOSITORY_ROOT.resolve("mde/validation/shared/shared-validation-helpers.eol"),
        evlRoot.resolve("../shared/shared-validation-helpers.eol").normalize());
    Path evl = evlRoot.resolve("helper-contract.evl");
    Files.writeString(
        evl,
        """
import "../shared/shared-validation-helpers.eol";
import "lib/psm-validation-helpers.eol";

context AWSPSM!AwsPsmModel {
  constraint HelperContracts {
    check {
      var resource = AWSPSM!AwsResource.all.select(r | r.`id` = "helper-function").first();
      var table = AWSPSM!DynamoDbTable.all.select(t | t.`id` = "helper-table").first();
      var bucket = AWSPSM!S3Bucket.all.select(b | b.`id` = "helper-bucket").first();
      var blockBucket = AWSPSM!S3Bucket.all.select(b | b.`id` = "helper-block-bucket").first();
      var openBucket = AWSPSM!S3Bucket.all.select(b | b.`id` = "helper-open-bucket").first();
      var partialBucket = AWSPSM!S3Bucket.all.select(b | b.`id` = "helper-partial-bucket").first();
      var statement = AWSPSM!IamStatement.all.select(s | s.`id` = "helper-statement").first();
      var trustStatement = AWSPSM!IamStatement.all.select(s | s.`id` = "helper-trust-statement").first();
      var notSideStatement = AWSPSM!IamStatement.all.select(s | s.`id` = "helper-not-side-statement").first();
      var conditionStatement = AWSPSM!IamStatement.all.select(s | s.`id` = "helper-condition-statement").first();
      var noSideStatement = AWSPSM!IamStatement.all.select(s | s.`id` = "helper-no-side-statement").first();
      var expression = AWSPSM!ValueExpression.all.select(v | v.`id` = "helper-secret-ref").first();
      var ssmExpression = AWSPSM!ValueExpression.all.select(v | v.`id` = "helper-ssm-ref").first();
      var dynamicExpression = AWSPSM!ValueExpression.all.select(v | v.`id` = "helper-dynamic-ref").first();
      var plainExpression = AWSPSM!ValueExpression.all.select(v | v.`id` = "helper-plain-value").first();
      var route = AWSPSM!HttpApiRoute.all.select(r | r.`id` = "helper-route-a").first();
      var orphanRoute = AWSPSM!HttpApiRoute.all.select(r | r.`id` = "helper-orphan-route").first();
      var restRoute = AWSPSM!RestApiRoute.all.select(r | r.`id` = "helper-rest-route").first();
      var wsRoute = AWSPSM!WebSocketRoute.all.select(r | r.`id` = "helper-ws-route").first();
      var integration = AWSPSM!ApiGatewayIntegration.all.select(i | i.`id` = "helper-integration").first();
      var uriIntegration = AWSPSM!ApiGatewayIntegration.all.select(i | i.`id` = "helper-uri-integration").first();
      var emptyIntegration = AWSPSM!ApiGatewayIntegration.all.select(i | i.`id` = "helper-empty-integration").first();
      var doubleIntegration = AWSPSM!ApiGatewayIntegration.all.select(i | i.`id` = "helper-double-integration").first();
      var machine = AWSPSM!StepFunctionStateMachine.all.select(s | s.`id` = "helper-machine").first();
      var uriMachine = AWSPSM!StepFunctionStateMachine.all.select(s | s.`id` = "helper-uri-machine").first();
      var stringMachine = AWSPSM!StepFunctionStateMachine.all.select(s | s.`id` = "helper-string-machine").first();
      var mixedMachine = AWSPSM!StepFunctionStateMachine.all.select(s | s.`id` = "helper-mixed-machine").first();
      var asl = AWSPSM!AslDocument.all.first();
      var duplicateAsl = AWSPSM!AslDocument.all.select(d | d.`id` = "helper-duplicate-asl").first();
      var namedOnly = AWSPSM!AwsResource.all.select(r | r.`id` = "helper-named-only").first();
      var classOnly = AWSPSM!AwsResource.all.select(r | r.`id` = "helper-class-only").first();
      var cycleA = AWSPSM!AwsResource.all.select(r | r.`id` = "helper-cycle-a").first();
      var generatedCode = AWSPSM!LambdaZipCodeConfig.all.select(c | c.`id` = "helper-generated-code").first();

      return " text ".hasText() and not "".hasText() and not "   ".hasText() and not namedOnly.logicalId.hasText() and
        true.isTrueValue() and not false.isTrueValue() and not classOnly.productionCritical.isTrueValue() and
        false.isFalseValue() and not true.isFalseValue() and not classOnly.productionCritical.isFalseValue() and
        currentPsmModel().isDefined() and productionStageExists() and self.stackResourceSet().includes(resource) and
        self.stackResourceSet().includes(route) and self.stackResourceSet().includes(generatedCode.eContainer()) and
        resource.resourceLabel() = "HelperFunction" and resource.isProductionScoped() and
        namedOnly.resourceLabel() = "Named Only" and classOnly.resourceLabel() = "AwsNativeResource" and
        resource.hasTagKey("Owner") and not resource.hasTagKey("Missing") and resource.missingRequiredTagKeys().isEmpty() and
        table.missingRequiredTagKeys().asSet() = Set{"Owner", "Environment"} and
        resource.duplicateTagKeys().isEmpty() and cycleA.duplicateTagKeys() = Set{"Dup"} and
        resource.stackLogicalIdKey() = "helper-stack::HelperFunction" and
        route.stackLogicalIdKey() = "<no-stack>::HelperRoute" and orphanRoute.stackLogicalIdKey() = "helper-stack::HelperOrphanRoute" and
        duplicateResourceLogicalIdKeys().includes("helper-stack::DuplicateHelperLogical") and
        resourcesWithDependencyCycles().includes("helper-cycle-a") and resourcesWithDependencyCycles().includes("helper-cycle-b") and
        cycleA.transitiveDependencyIds().includes("helper-cycle-a") and table.isDefined() and
        statement.actionIsWildcard() and statement.resourceIsWildcard() and statement.hasLeastPrivilegeJustification() and
        statement.hasActionSide() and statement.hasResourceSide() and not statement.isTrustPolicyStatement() and
        trustStatement.isTrustPolicyStatement() and notSideStatement.hasActionSide() and notSideStatement.hasResourceSide() and
        not notSideStatement.actionIsWildcard() and not notSideStatement.resourceIsWildcard() and
        conditionStatement.hasLeastPrivilegeJustification() and
        not noSideStatement.hasActionSide() and not noSideStatement.hasResourceSide() and
        not noSideStatement.actionIsWildcard() and not noSideStatement.resourceIsWildcard() and
        expression.usesSecureSource() and ssmExpression.usesSecureSource() and dynamicExpression.usesSecureSource() and
        not plainExpression.usesSecureSource() and
        bucket.blocksAllPublicAccess() and blockBucket.blocksAllPublicAccess() and
        not openBucket.blocksAllPublicAccess() and not partialBucket.blocksAllPublicAccess() and
        integration.targetCount() = 1 and route.httpApiRouteKey() = "helper-api::GET::/helper" and
        uriIntegration.targetCount() = 1 and emptyIntegration.targetCount() = 0 and doubleIntegration.targetCount() = 2 and
        orphanRoute.apiRouteKeyPrefix() = "<no-api>" and
        restRoute.restApiRouteKey() = "helper-rest-api::POST::/rest" and
        wsRoute.webSocketRouteKey() = "helper-ws-api::$connect" and
        duplicateHttpApiRouteKeys().includes("helper-api::GET::/helper") and
        duplicateRestApiRouteKeys().includes("helper-rest-api::POST::/rest") and
        duplicateWebSocketRouteKeys().includes("helper-ws-api::$connect") and
        generatedCode.isGeneratorManagedCodeSkeleton() and
        machine.definitionCount() = 1 and uriMachine.definitionCount() = 1 and stringMachine.definitionCount() = 1 and mixedMachine.definitionCount() = 3 and
        asl.hasStateNamed("Done") and not asl.hasStateNamed("Missing") and asl.duplicateStateNames().isEmpty() and
        duplicateAsl.duplicateStateNames() = Set{"Duplicate"};
    }
    message {
      var resource = AWSPSM!AwsResource.all.select(r | r.`id` = "helper-function").first();
      var bucket = AWSPSM!S3Bucket.all.select(b | b.`id` = "helper-bucket").first();
      var blockBucket = AWSPSM!S3Bucket.all.select(b | b.`id` = "helper-block-bucket").first();
      var openBucket = AWSPSM!S3Bucket.all.select(b | b.`id` = "helper-open-bucket").first();
      var partialBucket = AWSPSM!S3Bucket.all.select(b | b.`id` = "helper-partial-bucket").first();
      var route = AWSPSM!HttpApiRoute.all.select(r | r.`id` = "helper-route-a").first();
      var orphanRoute = AWSPSM!HttpApiRoute.all.select(r | r.`id` = "helper-orphan-route").first();
      var restRoute = AWSPSM!RestApiRoute.all.select(r | r.`id` = "helper-rest-route").first();
      var wsRoute = AWSPSM!WebSocketRoute.all.select(r | r.`id` = "helper-ws-route").first();
      var integration = AWSPSM!ApiGatewayIntegration.all.select(i | i.`id` = "helper-integration").first();
      var uriIntegration = AWSPSM!ApiGatewayIntegration.all.select(i | i.`id` = "helper-uri-integration").first();
      var emptyIntegration = AWSPSM!ApiGatewayIntegration.all.select(i | i.`id` = "helper-empty-integration").first();
      var doubleIntegration = AWSPSM!ApiGatewayIntegration.all.select(i | i.`id` = "helper-double-integration").first();
      var machine = AWSPSM!StepFunctionStateMachine.all.select(s | s.`id` = "helper-machine").first();
      var mixedMachine = AWSPSM!StepFunctionStateMachine.all.select(s | s.`id` = "helper-mixed-machine").first();
      var duplicateAsl = AWSPSM!AslDocument.all.select(d | d.`id` = "helper-duplicate-asl").first();
      var namedOnly = AWSPSM!AwsResource.all.select(r | r.`id` = "helper-named-only").first();
      var classOnly = AWSPSM!AwsResource.all.select(r | r.`id` = "helper-class-only").first();
      var cycleA = AWSPSM!AwsResource.all.select(r | r.`id` = "helper-cycle-a").first();
      var table = AWSPSM!DynamoDbTable.all.select(t | t.`id` = "helper-table").first();
      var statement = AWSPSM!IamStatement.all.select(s | s.`id` = "helper-statement").first();
      var trustStatement = AWSPSM!IamStatement.all.select(s | s.`id` = "helper-trust-statement").first();
      var notSideStatement = AWSPSM!IamStatement.all.select(s | s.`id` = "helper-not-side-statement").first();
      var conditionStatement = AWSPSM!IamStatement.all.select(s | s.`id` = "helper-condition-statement").first();
      var noSideStatement = AWSPSM!IamStatement.all.select(s | s.`id` = "helper-no-side-statement").first();
      var expression = AWSPSM!ValueExpression.all.select(v | v.`id` = "helper-secret-ref").first();
      var ssmExpression = AWSPSM!ValueExpression.all.select(v | v.`id` = "helper-ssm-ref").first();
      var dynamicExpression = AWSPSM!ValueExpression.all.select(v | v.`id` = "helper-dynamic-ref").first();
      var plainExpression = AWSPSM!ValueExpression.all.select(v | v.`id` = "helper-plain-value").first();
      return "helper groups: resource=" + (resource.isProductionScoped() and resource.missingRequiredTagKeys().isEmpty()).asString() +
        ", primitive=" + (" text ".hasText() and not "".hasText() and not "   ".hasText() and not namedOnly.logicalId.hasText() and true.isTrueValue() and not false.isTrueValue() and not classOnly.productionCritical.isTrueValue() and false.isFalseValue() and not true.isFalseValue() and not classOnly.productionCritical.isFalseValue()).asString() +
        ", blankText=" + (not "   ".hasText()).asString() +
        ", unsetText=" + (not namedOnly.logicalId.hasText()).asString() +
        ", trueValue=" + true.isTrueValue().asString() +
        ", falseNotTrue=" + (not false.isTrueValue()).asString() +
        ", unsetNotTrue=" + (not classOnly.productionCritical.isTrueValue()).asString() +
        ", falseValue=" + false.isFalseValue().asString() +
        ", trueNotFalse=" + (not true.isFalseValue()).asString() +
        ", unsetNotFalse=" + (not classOnly.productionCritical.isFalseValue()).asString() +
        ", tableMissing=" + table.missingRequiredTagKeys().asString() +
        ", iam=" + (statement.actionIsWildcard() and statement.resourceIsWildcard() and statement.hasLeastPrivilegeJustification() and statement.hasActionSide() and statement.hasResourceSide() and not statement.isTrustPolicyStatement()).asString() +
        ", trust=" + trustStatement.isTrustPolicyStatement().asString() +
        ", notSide=" + (notSideStatement.hasActionSide() and notSideStatement.hasResourceSide() and not notSideStatement.actionIsWildcard() and not notSideStatement.resourceIsWildcard()).asString() +
        ", conditionJustification=" + conditionStatement.hasLeastPrivilegeJustification().asString() +
        ", noSide=" + ((not noSideStatement.hasActionSide()) and (not noSideStatement.hasResourceSide()) and (not noSideStatement.actionIsWildcard()) and (not noSideStatement.resourceIsWildcard())).asString() +
        ", secure=" + (expression.usesSecureSource() and ssmExpression.usesSecureSource() and dynamicExpression.usesSecureSource() and not plainExpression.usesSecureSource()).asString() +
        ", bucket=" + (bucket.blocksAllPublicAccess() and blockBucket.blocksAllPublicAccess()).asString() +
        ", openBucket=" + openBucket.blocksAllPublicAccess().asString() +
        ", partialBucket=" + partialBucket.blocksAllPublicAccess().asString() +
        ", namedOnly=" + namedOnly.resourceLabel() +
        ", classOnly=" + classOnly.resourceLabel() +
        ", routeStackKey=" + route.stackLogicalIdKey() +
        ", orphanStackKey=" + orphanRoute.stackLogicalIdKey() +
        ", orphanPrefix=" + orphanRoute.apiRouteKeyPrefix() +
        ", routeKey=" + route.httpApiRouteKey() +
        ", restKey=" + restRoute.restApiRouteKey() +
        ", wsKey=" + wsRoute.webSocketRouteKey() +
        ", targetCount=" + integration.targetCount().asString() +
        ", uriTargetCount=" + uriIntegration.targetCount().asString() +
        ", emptyTargetCount=" + emptyIntegration.targetCount().asString() +
        ", doubleTargetCount=" + doubleIntegration.targetCount().asString() +
        ", definitionCount=" + machine.definitionCount().asString() +
        ", mixedDefinitionCount=" + mixedMachine.definitionCount().asString() +
        ", duplicateTags=" + cycleA.duplicateTagKeys().asString() +
        ", duplicateStates=" + duplicateAsl.duplicateStateNames().asString() +
        ", duplicateHttp=" + duplicateHttpApiRouteKeys().asString() +
        ", duplicateRest=" + duplicateRestApiRouteKeys().asString() +
        ", duplicateWs=" + duplicateWebSocketRouteKeys().asString() +
        ", cycles=" + resourcesWithDependencyCycles().asString() +
        ", duplicateLogicalIds=" + duplicateResourceLogicalIdKeys().asString();
    }
  }
}
""");

    Path model = writeModel("helper-contract", helperModel());
    EvlValidationReport report;
    try {
      report =
          new EpsilonEvlValidator()
              .validate(
                  EvlValidationRequest.forRoot(
                      evl,
                      List.of(
                          new FileEvlModelConfiguration(
                              "AWSPSM", ALIASES, model, List.of(PSM_ECORE), false)),
                      true));
    } catch (EvlValidationException ex) {
      throw new AssertionError(
          "helper-contract failed with diagnostics: " + ex.getReport().diagnostics(), ex);
    }

    assertEquals(EvlValidationStatus.SUCCEEDED, report.status());
    assertTrue(report.diagnostics().isEmpty(), report.diagnostics().toString());
    assertTrue(report.violations().isEmpty(), report.violations().toString());
  }

  @Test
  void psmEolHelpersCoverExplicitTaggingPolicyBranch() throws Exception {
    Path evlRoot = tempDir.resolve("tagging-helper-evl");
    Files.createDirectories(evlRoot.resolve("lib"));
    Files.createDirectories(evlRoot.resolve("../shared").normalize());
    Files.copy(
        REPOSITORY_ROOT.resolve("mde/validation/psm/lib/psm-validation-helpers.eol"),
        evlRoot.resolve("lib/psm-validation-helpers.eol"));
    Files.copy(
        REPOSITORY_ROOT.resolve("mde/validation/shared/shared-validation-helpers.eol"),
        evlRoot.resolve("../shared/shared-validation-helpers.eol").normalize());
    Path evl = evlRoot.resolve("tagging-helper-contract.evl");
    Files.writeString(
        evl,
        """
import "../shared/shared-validation-helpers.eol";
import "lib/psm-validation-helpers.eol";

context AWSPSM!AwsPsmModel {
  constraint ExplicitTaggingPolicyHelperContracts {
    check {
      var resource = AWSPSM!AwsResource.all.select(r | r.`id` = "tagged-resource").first();
      return activeTaggingPolicy().isDefined() and
        requiredTagKeys().asSet() = Set{"Owner", "CostCenter", "Project"} and
        resource.missingRequiredTagKeys().isEmpty();
    }
  }
}
""");

    Path model = writeModel("tagging-helper-contract", explicitTaggingPolicyHelperModel());
    EvlValidationReport report;
    try {
      report =
          new EpsilonEvlValidator()
              .validate(
                  EvlValidationRequest.forRoot(
                      evl,
                      List.of(
                          new FileEvlModelConfiguration(
                              "AWSPSM", ALIASES, model, List.of(PSM_ECORE), false)),
                      true));
    } catch (EvlValidationException ex) {
      throw new AssertionError(
          "tagging-helper-contract failed with diagnostics: " + ex.getReport().diagnostics(), ex);
    }

    assertEquals(EvlValidationStatus.SUCCEEDED, report.status());
    assertTrue(report.diagnostics().isEmpty(), report.diagnostics().toString());
    assertTrue(report.violations().isEmpty(), report.violations().toString());
  }

  private List<Scenario> scenarios() throws Exception {
    String sample = Files.readString(PSM_SAMPLE);
    return List.of(
        scenarioWithoutStructuralValidation(
            "root-stage-stack",
            minimalBrokenRootModel(),
            mandatory("ModelHasStacks"),
            mandatory("ModelHasStages"),
            mandatory("ProductionModeHasProdStage"),
            optional("DefaultRegionRecommended")),
        scenarioWithoutStructuralValidation(
            "root-stack-resources-exist",
            minimalEmptyStackRootModel(),
            optional("StackResourcesExist"),
            mandatory("DeployableStackHasResources"),
            mandatory("StackHasResources")),
        scenarioWithoutStructuralValidation(
            "stage-stack-core",
            insertBeforeRootClose(
                sample
                    .replace("defaultRegion=\"us-east-1\"", "defaultRegion=\"\"")
                    .replace("stageName=\"dev\"", "stageName=\"prod\""),
                """
  <stages id="bad-stage" name="Bad Stage" stageName="prod" accountId="" region="" environmentClass="PROD" requiresManualApproval="false" confirmChangeset="false"/>
  <stacks id="bad-empty-stack" name="Bad Empty Stack" stackName="empty-stack" useSamTransform="false" validateWithSam="false" validateWithCfnLint="false"/>
  <stacks id="bad-duplicate-stack-a" name="Bad Duplicate Stack A" stackName="duplicate-stack"/>
  <stacks id="bad-duplicate-stack-b" name="Bad Duplicate Stack B" stackName="duplicate-stack"/>
"""),
            mandatory("UniqueStackNames"),
            mandatory("UniqueStageNames"),
            mandatory("StageHasAccountAndRegion"),
            mandatory("ProdRequiresApproval"),
            optional("ProdShouldConfirmChangeset"),
            optional("StageDeploysAtLeastOneStack"),
            mandatory("DeployableStackHasResources"),
            mandatory("StackHasResources"),
            optional("SamTransformRecommended"),
            optional("ValidationToolsRecommended")),
        scenarioWithoutStructuralValidation(
            "resources-values-native",
            insertBeforeFirstStackClose(sample, resourceValueFragment()),
            mandatory("StackLogicalIdsAreUnique"),
            mandatory("LogicalIdValid"),
            mandatory("LogicalIdUniqueInStack"),
            mandatory("NoDirectSelfDependency"),
            mandatory("NoDependencyCycles"),
            mandatory("ImportedResourceHasImportIdentity"),
            optional("NonImportedResourceShouldNotHaveImportMetadata"),
            mandatory("DeployableResourceHasAwsType"),
            mandatory("ProductionResourcesHaveRequiredTags"),
            mandatory("TagKeysAreUniquePerResource"),
            optional("ProductionResourcesShouldRetainOnDelete"),
            mandatory("NativeResourceTypeNameValid"),
            mandatory("ValueExpressionSourceShape"),
            optional("SecretLiteralReviewed"),
            mandatory("TagKeyHasText"),
            optional("AvoidAwsReservedTagPrefix"),
            mandatory("RequiredNativePropertyHasValue"),
            mandatory("SecretNativePropertyUsesSecureExpression"),
            mandatory("PlainTextHasLiteral"),
            mandatory("CloudFormationRefHasTarget"),
            mandatory("GetAttHasResourceAndAttribute"),
            mandatory("ListExpressionHasItems"),
            mandatory("MapExpressionHasEntries"),
            mandatory("SecretValueMustUseSecureReference"),
            mandatory("MapEntryHasKey")),
        scenarioWithoutStructuralValidation(
            "compute",
            insertBeforeFirstStackClose(sample, computeFragment()),
            mandatory("LambdaHasExecutionRole"),
            mandatory("LambdaHasLogGroup"),
            mandatory("LambdaHasCodeConfig"),
            mandatory("LambdaHasSupportedCodeConfig"),
            mandatory("LambdaPackageTypeMatchesCodeConfig"),
            mandatory("LambdaMemoryRange"),
            mandatory("LambdaTimeoutRange"),
            mandatory("LambdaEphemeralStorageRange"),
            mandatory("ReservedConcurrencyNonNegative"),
            mandatory("AutoPublishAliasRequiresVersionPublishing"),
            mandatory("CodeSigningDecisionHonored"),
            optional("ProductionLambdaShouldUseTracing"),
            optional("ProductionLambdaShouldUseStructuredLogging"),
            optional("ProductionLambdaShouldHaveFailureDestinationOrDlq"),
            mandatory("ZipCodeHasRuntimeAndHandler"),
            mandatory("ZipCodeHasExactlyOneLocation"),
            mandatory("ImageCodeHasImageUri"),
            mandatory("EnvironmentVariableNameValid"),
            mandatory("SecretEnvironmentValueUsesSecureReference"),
            optional("StageSpecificVariableShouldHaveRationale"),
            mandatory("DlqHasExactlyOneTarget"),
            mandatory("BatchSizePositive"),
            mandatory("MaximumBatchingWindowRange"),
            mandatory("ParallelizationFactorRange"),
            mandatory("StartingTimestampRequiresAtTimestamp"),
            mandatory("QueueVisibilityGreaterThanFunctionTimeout"),
            optional("PartialBatchFailureRecommendedForSqs"),
            mandatory("RequiredPartialBatchFailureDecisionHonored"),
            mandatory("DynamoStreamMappingRequiresStreamSpecification"),
            mandatory("DynamoStreamMappingHasStartingPosition"),
            mandatory("ProvisionedConcurrencyPositive"),
            mandatory("ProductionFunctionUrlRequiresAuth")),
        scenarioWithoutStructuralValidation(
            "api",
            insertBeforeFirstStackClose(sample, apiFragment()),
            mandatory("ApiHasRoutes"),
            optional("ApiShouldHaveStage"),
            mandatory("AccessLogsRequireLogGroupAndFormat"),
            optional("ProductionApiShouldHaveMetricsAndTracing"),
            mandatory("UniqueHttpRouteWithinApi"),
            mandatory("UniqueRestRouteWithinApi"),
            mandatory("UniqueWebSocketRouteKeyWithinApi"),
            mandatory("RouteHasApi"),
            mandatory("ProtectedRouteHasRequiredAuthConfiguration"),
            mandatory("RouteHasIntegration"),
            optional("IntegrationTimeoutShouldNotExceedLambdaTimeout"),
            mandatory("IntegrationHasSingleTarget"),
            optional("LongApiGatewayTimeoutRequiresQuotaReview"),
            mandatory("CredentialsArnMatchesCredentialsRole"),
            mandatory("AccessLogStageRequiresGroupAndFormat"),
            optional("ProductionStageShouldThrottle"),
            mandatory("JwtAuthorizerHasIssuerAndAudience"),
            optional("CognitoAuthorizerShouldReferenceClients"),
            mandatory("LambdaAuthorizerHasFunction"),
            mandatory("DomainHasCertificate"),
            mandatory("UsagePlanKeyUsesKnownType")),
        scenarioWithoutStructuralValidation(
            "events-messaging-storage-security-identity-workflow-observability-networking-views",
            insertBeforeRootClose(
                insertBeforeFirstStackClose(
                    withNetworkingNamespace(sample),
                    apiFragment() + computeFragment() + platformFragment()),
                relationshipFragment()),
            mandatory("RuleHasPatternOrSchedule"),
            mandatory("RuleDoesNotMixPatternAndSchedule"),
            mandatory("RuleHasTargets"),
            mandatory("TargetIdsUniqueWithinRule"),
            mandatory("TargetHasResourceOrArn"),
            optional("CriticalEventTargetsHaveRetryOrDlq"),
            mandatory("SqsFifoTargetHasMessageGroupId"),
            mandatory("NonLambdaTargetHasInvokeRole"),
            mandatory("RetryPolicyRangesValid"),
            mandatory("ScheduleHasExpressionAndRole"),
            mandatory("ScheduleHasTarget"),
            mandatory("PipeHasSourceTargetAndRole"),
            mandatory("ApiDestinationHasEndpointAndConnection"),
            mandatory("ConnectionAuthParametersMatchAuthorizationType"),
            mandatory("FifoQueueNameSuffix"),
            optional("StandardQueueShouldNotUseFifoSuffix"),
            mandatory("QueueTimingRangesValid"),
            mandatory("QueueMessageSizeRangeValid"),
            mandatory("RedrivePolicyValid"),
            optional("ProductionQueueShouldBeEncrypted"),
            mandatory("FifoTopicNameSuffix"),
            optional("ProductionTopicShouldBeEncrypted"),
            mandatory("SubscriptionHasEndpointOrResource"),
            mandatory("FifoTopicToSqsRequiresFifoQueue"),
            optional("ExternalHttpSubscriptionsShouldHaveDlq"),
            mandatory("FilterRuleHasValues"),
            mandatory("DynamoTableHasValidPrimaryKeySchema"),
            mandatory("DynamoAttributesCoverTableKeys"),
            mandatory("DynamoIndexKeysAreCoveredByAttributes"),
            mandatory("ProvisionedModeNeedsThroughput"),
            mandatory("PayPerRequestDoesNotUseProvisionedThroughput"),
            mandatory("ProvisionedThroughputPositive"),
            optional("ProductionTableShouldHavePitRecoveryAndDeletionProtection"),
            optional("ProductionTableShouldBeEncryptedWithKms"),
            mandatory("GsiProjectionIncludeHasAttributes"),
            mandatory("GsiProvisionedThroughputPositiveWhenPresent"),
            optional("EnabledTtlHasAttributeName"),
            mandatory("ProductionBucketsBlockPublicAccess"),
            mandatory("ProductionBucketsEncrypted"),
            optional("ProductionBucketsShouldVersion"),
            optional("WebsiteBucketShouldNotBeProductionCritical"),
            mandatory("KmsAlgorithmRequiresKmsKey"),
            mandatory("NotificationRuleHasEventAndDestination"),
            mandatory("ReplicationHasRoleAndRules"),
            mandatory("RoleHasTrustPolicyStatements"),
            optional("ProductionRoleShouldUsePermissionsBoundary"),
            mandatory("PolicyDocumentHasStatements"),
            mandatory("StatementHasActionAndResourceSide"),
            mandatory("NoAllowWildcardInProductionWithoutJustification"),
            optional("AvoidNotActionInAllowStatements"),
            mandatory("PrincipalHasTypeAndIdentifiers"),
            mandatory("ConditionComplete"),
            mandatory("ProductionKmsKeyRotation"),
            mandatory("PendingWindowRange"),
            mandatory("KmsAliasNameValid"),
            mandatory("SecretHasValueOrGenerator"),
            mandatory("RotationRequiredHasSchedule"),
            optional("ProductionSecretShouldUseKms"),
            mandatory("RotationScheduleHasRulesOrLambda"),
            mandatory("SecureParameterUsesSecureType"),
            optional("SecureParameterShouldUseKmsKey"),
            mandatory("MfaForProductionPrivilegedPools"),
            mandatory("MfaDecisionMadeForProductionCriticalPools"),
            optional("ProductionUserPoolShouldUseDeletionProtection"),
            mandatory("OAuthClientHasCallbackUrls"),
            optional("PreventUserExistenceErrorsRecommended"),
            optional("UnauthenticatedIdentitiesRequireReview"),
            mandatory("StateMachineHasRole"),
            mandatory("StateMachineHasDefinition"),
            optional("StateMachineShouldUseSingleDefinitionSource"),
            mandatory("StateMachineAslHasStates"),
            mandatory("PublishAliasRequiresAliasName"),
            optional("ProductionStateMachineShouldLogAndTrace"),
            mandatory("StartAtReferencesExistingState"),
            mandatory("StateNamesUnique"),
            mandatory("AslHasTerminalState"),
            mandatory("NonTerminalStateHasNextOrTerminalType"),
            mandatory("TerminalStateDoesNotHaveNext"),
            mandatory("TaskStateHasResource"),
            mandatory("ChoiceStateHasChoices"),
            mandatory("RetryRuleRangesValid"),
            mandatory("CatchRuleHasErrorsAndNext"),
            mandatory("ChoiceRuleHasConditionAndNext"),
            mandatory("ProductionLogRetentionExplicit"),
            optional("ProductionLogGroupShouldUseKms"),
            mandatory("AlarmHasMetricAndThreshold"),
            mandatory("AlarmEvaluationSettingsValid"),
            optional("ProductionAlarmShouldHaveActions"),
            optional("CompositeAlarmShouldHaveActions"),
            mandatory("VpcAttachmentHasSubnetsAndSecurityGroups"),
            optional("ModeledVpcAttachmentShouldNotContradictRawIds"),
            optional("PrivateSubnetsRequireEndpointsWhenFlagged"),
            optional("VpcShouldEnableDns"),
            mandatory("PortRangeValid"),
            optional("PublicAdminIngressRequiresReview"),
            mandatory("RelationshipViewHasEndpoints"),
            mandatory("ApiLambdaViewMatchesDeployableObjects"),
            optional("ApiLambdaPermissionRecommended"),
            mandatory("EventBridgeLambdaViewMatchesDeployableObjects"),
            optional("EventBridgeLambdaPermissionRecommended"),
            mandatory("SnsLambdaViewMatchesDeployableObjects"),
            optional("SnsLambdaPermissionRecommended"),
            mandatory("SqsLambdaViewMatchesMapping"),
            mandatory("StepFunctionEventBridgeViewMatchesTarget")),
        scenarioWithoutStructuralValidation(
            "kernel-trace",
            insertIntoTraceModel(
                sample,
                """
    <links id="psm-bad-trace-link" name="PSM Bad Trace Link"/>
"""),
            mandatory("TraceLinkHasReferenceOrExternalId")));
  }

  private static String resourceValueFragment() {
    return """
    <resources xsi:type="awspsmcore:AwsNativeResource" id="bad-native-a" name="Bad Native A" logicalId="1-Bad" awsResourceType="" productionCritical="true" importedResource="false" importedArn="arn:aws:s3:::existing" retainInProduction="false" deletionPolicy="DELETE" cloudFormationType="Bad::Type">
      <tags id="bad-empty-tag" name="Empty Tag" key="" value="x"/>
      <tags id="bad-reserved-tag" name="Reserved Tag" key="aws:owner" value="x"/>
      <tags id="bad-dup-tag-a" name="Dup A" key="Dup" value="a"/>
      <tags id="bad-dup-tag-b" name="Dup B" key="Dup" value="b"/>
      <metadata id="bad-required-property" name="Required Property" propertyName="Required" required="true" secret="false" format="JSON" validationState="NEEDS_REVIEW"/>
      <metadata id="bad-secret-property" name="Secret Property" propertyName="Secret" required="false" secret="true" format="JSON" validationState="NEEDS_REVIEW">
        <value id="bad-secret-property-value" name="Bad Secret Value" sourceKind="PLAINTEXT" literal="plain" secret="true"/>
      </metadata>
      <metadata id="bad-plaintext" name="Plaintext" propertyName="Plaintext" required="false" secret="false" format="JSON" validationState="NEEDS_REVIEW">
        <value id="bad-plaintext-value" name="Plaintext Value" sourceKind="PLAINTEXT"/>
      </metadata>
      <metadata id="bad-ref" name="Ref" propertyName="Ref" required="false" secret="false" format="JSON" validationState="NEEDS_REVIEW">
        <value id="bad-ref-value" name="Ref Value" sourceKind="CLOUDFORMATION_REF"/>
      </metadata>
      <metadata id="bad-getatt" name="GetAtt" propertyName="GetAtt" required="false" secret="false" format="JSON" validationState="NEEDS_REVIEW">
        <value id="bad-getatt-value" name="GetAtt Value" sourceKind="CLOUDFORMATION_GETATT" resource="bad-native-a"/>
      </metadata>
      <metadata id="bad-list" name="List" propertyName="List" required="false" secret="false" format="JSON" validationState="NEEDS_REVIEW">
        <value id="bad-list-value" name="List Value" sourceKind="LIST"/>
      </metadata>
      <metadata id="bad-map" name="Map" propertyName="Map" required="false" secret="false" format="JSON" validationState="NEEDS_REVIEW">
        <value id="bad-map-value" name="Map Value" sourceKind="MAP">
          <entries id="bad-map-entry" name="Map Entry" key="">
            <value id="bad-map-entry-value" name="Map Entry Value" sourceKind="PLAINTEXT" literal="x"/>
          </entries>
        </value>
      </metadata>
      <metadata id="bad-empty-map" name="Empty Map" propertyName="EmptyMap" required="false" secret="false" format="JSON" validationState="NEEDS_REVIEW">
        <value id="bad-empty-map-value" name="Empty Map Value" sourceKind="MAP"/>
      </metadata>
      <metadata id="bad-secret-value" name="Secret Value" propertyName="SecretValue" required="false" secret="false" format="JSON" validationState="NEEDS_REVIEW">
        <value id="bad-secret-value-expression" name="Secret Value Expression" sourceKind="PLAINTEXT" literal="x" secret="true"/>
      </metadata>
    </resources>
    <resources xsi:type="awspsmcore:AwsNativeResource" id="bad-native-b" name="Bad Native B" logicalId="DuplicateLogicalId" awsResourceType="AWS::S3::Bucket" importedResource="true" cloudFormationType="AWS::S3::Bucket" dependsOn="bad-native-b bad-native-c">
      <properties id="bad-native-b-property" name="Property" propertyName="Name" required="false" secret="false" format="JSON" validationState="NEEDS_REVIEW">
        <value id="bad-native-b-value" name="Value" sourceKind="PLAINTEXT" literal="x"/>
      </properties>
    </resources>
    <resources xsi:type="awspsmcore:AwsNativeResource" id="bad-native-c" name="Bad Native C" logicalId="DuplicateLogicalId" awsResourceType="AWS::S3::Bucket" importedResource="false" cloudFormationType="AWS::S3::Bucket" dependsOn="bad-native-b">
      <properties id="bad-native-c-property" name="Property" propertyName="Name" required="false" secret="false" format="JSON" validationState="NEEDS_REVIEW">
        <value id="bad-native-c-value" name="Value" sourceKind="PLAINTEXT" literal="x"/>
      </properties>
    </resources>
""";
  }

  private static String computeFragment() {
    return """
    <resources xsi:type="awspsmcompute:AwsLambdaFunction" id="bad-lambda" name="Bad Lambda" logicalId="BadLambda" awsResourceType="AWS::Serverless::Function" productionCritical="true" memorySizeMb="64" timeoutSeconds="901" ephemeralStorageMb="511" reservedConcurrentExecutions="-1" autoPublishAlias="live" publishVersion="false" codeSigningDecision="REQUIRED" packageType="IMAGE">
      <code xsi:type="awspsmcompute:LambdaZipCodeConfig" id="bad-zip-code" name="Bad Zip Code" codeUri="src" s3Bucket="bucket" s3Key="key"/>
      <environment id="bad-env" name="Bad Env" variableName="1_BAD" stageSpecific="true">
        <value id="bad-env-value" name="Bad Env Value" sourceKind="PLAINTEXT" literal="secret" secret="true"/>
      </environment>
      <deadLetterConfig id="bad-dlq" name="Bad DLQ" targetQueue="%s" targetTopic="%s"/>
      <eventSourceMappings xsi:type="awspsmcompute:SqsLambdaEventSourceMapping" id="bad-sqs-mapping" name="Bad SQS Mapping" logicalId="BadSqsMapping" awsResourceType="AWS::Lambda::EventSourceMapping" batchSize="0" maximumBatchingWindowSeconds="301" parallelizationFactor="11" startingPositionTimestamp="2026-01-01T00:00:00Z" startingPosition="LATEST" reportBatchItemFailures="false" partialBatchFailureHandlingDecision="REQUIRED" queue="bad-short-visibility-queue"/>
      <eventSourceMappings xsi:type="awspsmcompute:DynamoDbStreamLambdaEventSourceMapping" id="bad-dynamo-mapping" name="Bad Dynamo Mapping" logicalId="BadDynamoMapping" awsResourceType="AWS::Lambda::EventSourceMapping" table="bad-dynamo-no-stream"/>
      <eventSourceMappings xsi:type="awspsmcompute:DynamoDbStreamLambdaEventSourceMapping" id="bad-dynamo-mapping-no-position" name="Bad Dynamo Mapping No Position" logicalId="BadDynamoMappingNoPosition" awsResourceType="AWS::Lambda::EventSourceMapping" table="bad-dynamo-with-stream"/>
      <versions id="bad-version" name="Bad Version" logicalId="BadVersion" awsResourceType="AWS::Lambda::Version">
        <provisionedConcurrencyConfig id="bad-provisioned" name="Bad Provisioned" provisionedConcurrentExecutions="0"/>
      </versions>
      <functionUrl id="bad-function-url" name="Bad Function URL" logicalId="BadFunctionUrl" awsResourceType="AWS::Lambda::Url" authType="NONE"/>
    </resources>
    <resources xsi:type="awspsmmessaging:SqsQueue" id="bad-short-visibility-queue" name="Bad Short Visibility Queue" logicalId="BadShortVisibilityQueue" awsResourceType="AWS::SQS::Queue" queueType="STANDARD" visibilityTimeoutSeconds="1"/>
    <resources xsi:type="awspsmstorage:DynamoDbTable" id="bad-dynamo-no-stream" name="Bad Dynamo No Stream" logicalId="BadDynamoNoStream" awsResourceType="AWS::DynamoDB::Table" billingMode="PAY_PER_REQUEST">
      <attributeDefinitions id="bad-dynamo-no-stream-attr" name="Id" attributeName="id" attributeType="S"/>
      <keySchema id="bad-dynamo-no-stream-key" name="Id Key" attributeName="id" keyType="HASH"/>
    </resources>
    <resources xsi:type="awspsmstorage:DynamoDbTable" id="bad-dynamo-with-stream" name="Bad Dynamo With Stream" logicalId="BadDynamoWithStream" awsResourceType="AWS::DynamoDB::Table" billingMode="PAY_PER_REQUEST">
      <attributeDefinitions id="bad-dynamo-with-stream-attr" name="Id" attributeName="id" attributeType="S"/>
      <keySchema id="bad-dynamo-with-stream-key" name="Id Key" attributeName="id" keyType="HASH"/>
      <streamSpecification id="bad-dynamo-stream" name="Bad Dynamo Stream" streamViewType="NEW_IMAGE"/>
    </resources>
    <resources xsi:type="awspsmcompute:AwsLambdaFunction" id="bad-lambda-no-code" name="Bad Lambda No Code" logicalId="BadLambdaNoCode" awsResourceType="AWS::Serverless::Function" role="%s" logGroup="%s"/>
    <resources xsi:type="awspsmcompute:AwsLambdaFunction" id="bad-lambda-image" name="Bad Lambda Image" logicalId="BadLambdaImage" awsResourceType="AWS::Serverless::Function" role="%s" logGroup="%s">
      <code xsi:type="awspsmcompute:LambdaImageCodeConfig" id="bad-image-code" name="Bad Image Code" imageUri=""/>
    </resources>
"""
        .formatted(
            VALID_QUEUE_ID,
            VALID_TOPIC_ID,
            VALID_ROLE_ID,
            VALID_LOG_GROUP_ID,
            VALID_ROLE_ID,
            VALID_LOG_GROUP_ID);
  }

  private static String apiFragment() {
    return """
    <resources xsi:type="awspsmapi:RestApi" id="bad-api-empty" name="Bad Api Empty" logicalId="BadApiEmpty" awsResourceType="AWS::Serverless::Api" accessLogsEnabled="true" productionCritical="true"/>
    <resources xsi:type="awspsmapi:HttpApi" id="bad-api" name="Bad API" logicalId="BadApi" awsResourceType="AWS::Serverless::HttpApi" productionCritical="true">
      <routes xsi:type="awspsmapi:HttpApiRoute" id="bad-http-route-a" name="Bad Http Route A" logicalId="BadHttpRouteA" awsResourceType="AWS::ApiGatewayV2::Route" path="/dup" method="GET" authorizationType="JWT" timeoutInMillis="1000" integration="bad-integration"/>
      <routes xsi:type="awspsmapi:HttpApiRoute" id="bad-http-route-b" name="Bad Http Route B" logicalId="BadHttpRouteB" awsResourceType="AWS::ApiGatewayV2::Route" path="/dup" method="GET" authorizationType="NONE" integration="bad-integration"/>
      <routes xsi:type="awspsmapi:RestApiRoute" id="bad-rest-route-a" name="Bad Rest Route A" logicalId="BadRestRouteA" awsResourceType="AWS::ApiGateway::Method" path="/dup" method="POST" authorizationType="NONE" integration="bad-integration"/>
      <routes xsi:type="awspsmapi:RestApiRoute" id="bad-rest-route-b" name="Bad Rest Route B" logicalId="BadRestRouteB" awsResourceType="AWS::ApiGateway::Method" path="/dup" method="POST" authorizationType="NONE" integration="bad-integration"/>
      <routes xsi:type="awspsmapi:WebSocketRoute" id="bad-ws-route-a" name="Bad Ws Route A" logicalId="BadWsRouteA" awsResourceType="AWS::ApiGatewayV2::Route" routeKey="dup" authorizationType="NONE" integration="bad-integration"/>
      <routes xsi:type="awspsmapi:WebSocketRoute" id="bad-ws-route-b" name="Bad Ws Route B" logicalId="BadWsRouteB" awsResourceType="AWS::ApiGatewayV2::Route" routeKey="dup" authorizationType="NONE" integration="bad-integration"/>
      <routes xsi:type="awspsmapi:HttpApiRoute" id="bad-route-no-integration" name="Bad Route No Integration" logicalId="BadRouteNoIntegration" awsResourceType="AWS::ApiGatewayV2::Route" path="/none" method="GET" authorizationType="NONE"/>
      <stages xsi:type="awspsmapi:HttpApiStage" id="bad-api-stage" name="Bad Stage" logicalId="BadApiStage" awsResourceType="AWS::ApiGatewayV2::Stage" stageName="prod" accessLogEnabled="true"/>
      <authorizers xsi:type="awspsmapi:JwtAuthorizer" id="bad-jwt-authorizer" name="Bad Jwt" logicalId="BadJwt" awsResourceType="AWS::ApiGatewayV2::Authorizer" authorizationType="JWT"/>
      <authorizers xsi:type="awspsmapi:CognitoAuthorizer" id="bad-cognito-authorizer" name="Bad Cognito" logicalId="BadCognitoAuth" awsResourceType="AWS::ApiGateway::Authorizer" authorizationType="COGNITO_USER_POOLS" userPool="bad-user-pool"/>
      <authorizers xsi:type="awspsmapi:LambdaAuthorizer" id="bad-lambda-authorizer" name="Bad Lambda Authorizer" logicalId="BadLambdaAuthorizer" awsResourceType="AWS::ApiGatewayV2::Authorizer" authorizationType="CUSTOM_LAMBDA"/>
    </resources>
    <resources xsi:type="awspsmapi:ApiGatewayIntegration" id="bad-integration" name="Bad Integration" logicalId="BadIntegration" awsResourceType="AWS::ApiGatewayV2::Integration" integrationType="AWS_PROXY" timeoutInMillis="30000" credentialsArn="not-an-arn" lambdaTarget="%s" stateMachineTarget="bad-state-machine-with-definition" integrationUri="arn:aws:lambda"/>
    <resources xsi:type="awspsmapi:HttpApiRoute" id="bad-orphan-route" name="Bad Orphan Route" logicalId="BadOrphanRoute" awsResourceType="AWS::ApiGatewayV2::Route" path="/orphan" method="GET" authorizationType="NONE" integration="bad-integration"/>
    <resources xsi:type="awspsmapi:ApiGatewayDomainName" id="bad-domain" name="Bad Domain" logicalId="BadDomain" awsResourceType="AWS::ApiGateway::DomainName" domainName="api.example.test"/>
    <resources xsi:type="awspsmapi:ApiGatewayUsagePlanKey" id="bad-usage-key" name="Bad Usage Key" logicalId="BadUsageKey" awsResourceType="AWS::ApiGateway::UsagePlanKey" keyType="BAD" apiKey="bad-api-key" usagePlan="bad-usage-plan"/>
    <resources xsi:type="awspsmapi:ApiGatewayApiKey" id="bad-api-key" name="Bad Api Key" logicalId="BadApiKey" awsResourceType="AWS::ApiGateway::ApiKey"/>
    <resources xsi:type="awspsmapi:ApiGatewayUsagePlan" id="bad-usage-plan" name="Bad Usage Plan" logicalId="BadUsagePlan" awsResourceType="AWS::ApiGateway::UsagePlan"/>
    <resources xsi:type="awspsmworkflow:StepFunctionStateMachine" id="bad-state-machine-with-definition" name="Bad SM With Definition" logicalId="BadStateMachineWithDefinition" awsResourceType="AWS::Serverless::StateMachine" role="%s" definitionUri="state.asl.json" stateMachineType="STANDARD"/>
    <resources xsi:type="awspsmidentity:CognitoUserPool" id="bad-user-pool" name="Bad User Pool" logicalId="BadUserPoolForAuth" awsResourceType="AWS::Cognito::UserPool"/>
"""
        .formatted(VALID_LAMBDA_ID, VALID_ROLE_ID);
  }

  private static String platformFragment() {
    return """
    <resources xsi:type="awspsmevents:EventBridgeRule" id="bad-rule" name="Bad Rule" logicalId="BadRule" awsResourceType="AWS::Events::Rule" eventPatternJson="{ }" scheduleExpression="rate(5 minutes)">
      <targets id="bad-target-a" name="Bad Target A" targetId="dup" targetKind="STEP_FUNCTIONS" targetResource="bad-fifo-queue">
        <retryPolicy id="bad-retry" name="Bad Retry" maximumRetryAttempts="186" maximumEventAgeInSeconds="59"/>
      </targets>
      <targets id="bad-target-b" name="Bad Target B" targetId="dup" targetKind="STEP_FUNCTIONS"/>
      <targets id="bad-target-c" name="Bad Target C" targetId="fifo" targetKind="SQS" targetResource="bad-fifo-queue"/>
      <targets id="bad-target-d" name="Bad Target D" targetId="prod" targetKind="LAMBDA" targetResource="%s"/>
    </resources>
    <resources xsi:type="awspsmevents:EventBridgeRule" id="bad-empty-rule" name="Bad Empty Rule" logicalId="BadEmptyRule" awsResourceType="AWS::Events::Rule"/>
    <resources xsi:type="awspsmevents:EventBridgeSchedule" id="bad-schedule" name="Bad Schedule" logicalId="BadSchedule" awsResourceType="AWS::Scheduler::Schedule" scheduleExpression="">
      <target id="bad-schedule-target" name="Bad Schedule Target" targetId="schedule" targetKind="LAMBDA"/>
    </resources>
    <resources xsi:type="awspsmevents:EventBridgePipe" id="bad-pipe" name="Bad Pipe" logicalId="BadPipe" awsResourceType="AWS::Pipes::Pipe"/>
    <resources xsi:type="awspsmevents:EventBridgeApiDestination" id="bad-destination" name="Bad Destination" logicalId="BadDestination" awsResourceType="AWS::Events::ApiDestination" httpMethod="POST" connection="bad-connection"/>
    <resources xsi:type="awspsmevents:EventBridgeConnection" id="bad-connection" name="Bad Connection" logicalId="BadConnection" awsResourceType="AWS::Events::Connection" authorizationType="API_KEY">
      <authParameters xsi:type="awspsmevents:EventBridgeBasicAuthParameters" id="bad-basic-auth" name="Bad Basic">
        <username id="bad-basic-user" name="User" sourceKind="PLAINTEXT" literal="u"/>
        <password id="bad-basic-password" name="Password" sourceKind="PLAINTEXT" literal="p"/>
      </authParameters>
    </resources>
    <resources xsi:type="awspsmmessaging:SqsQueue" id="bad-fifo-queue" name="Bad Fifo Queue" logicalId="BadFifoQueue" awsResourceType="AWS::SQS::Queue" productionCritical="true" queueName="bad" queueType="FIFO" delaySeconds="-1" maximumMessageSize="1" messageRetentionPeriodSeconds="1" receiveMessageWaitTimeSeconds="21" visibilityTimeoutSeconds="43201">
      <redrivePolicy id="bad-redrive" name="Bad Redrive" maxReceiveCount="0" deadLetterQueue="bad-standard-fifo-name-queue"/>
    </resources>
    <resources xsi:type="awspsmmessaging:SqsQueue" id="bad-standard-fifo-name-queue" name="Bad Standard Queue" logicalId="BadStandardQueue" awsResourceType="AWS::SQS::Queue" queueName="bad.fifo" queueType="STANDARD"/>
    <resources xsi:type="awspsmmessaging:SnsTopic" id="bad-fifo-topic" name="Bad Fifo Topic" logicalId="BadFifoTopic" awsResourceType="AWS::SNS::Topic" productionCritical="true" topicName="bad" fifoTopic="true" subscriptions="bad-sub-empty bad-sub-standard bad-sub-filter"/>
    <resources xsi:type="awspsmmessaging:SnsSubscription" id="bad-sub-empty" name="Bad Subscription Empty" logicalId="BadSubEmpty" awsResourceType="AWS::SNS::Subscription" protocol="HTTPS" topic="bad-fifo-topic"/>
    <resources xsi:type="awspsmmessaging:SnsSubscription" id="bad-sub-standard" name="Bad Subscription Standard" logicalId="BadSubStandard" awsResourceType="AWS::SNS::Subscription" protocol="SQS" endpointResource="bad-standard-fifo-name-queue" topic="bad-fifo-topic"/>
    <resources xsi:type="awspsmmessaging:SnsSubscription" id="bad-sub-filter" name="Bad Subscription Filter" logicalId="BadSubFilter" awsResourceType="AWS::SNS::Subscription" protocol="LAMBDA" endpointResource="%s" topic="bad-fifo-topic">
      <filterRules id="bad-filter" name="Bad Filter" fieldPath="" operator=""/>
    </resources>
    <resources xsi:type="awspsmstorage:DynamoDbTable" id="bad-dynamo-table" name="Bad Dynamo" logicalId="BadDynamo" awsResourceType="AWS::DynamoDB::Table" productionCritical="true" billingMode="PROVISIONED">
      <attributeDefinitions id="bad-dynamo-attr" name="Id" attributeName="id" attributeType="S"/>
      <keySchema id="bad-dynamo-range" name="Range" attributeName="missing" keyType="RANGE"/>
      <globalSecondaryIndexes id="bad-gsi" name="Bad GSI" indexName="BadGsi">
        <keySchema id="bad-gsi-key" name="Bad GSI Key" attributeName="missing-gsi" keyType="HASH"/>
        <projection id="bad-gsi-projection" name="Bad Projection" projectionType="INCLUDE"/>
        <provisionedThroughput id="bad-gsi-throughput" name="Bad GSI Throughput" readCapacityUnits="0" writeCapacityUnits="0"/>
      </globalSecondaryIndexes>
      <timeToLiveSpecification id="bad-ttl" name="Bad TTL" enabled="true"/>
    </resources>
    <resources xsi:type="awspsmstorage:DynamoDbTable" id="bad-dynamo-payper" name="Bad Dynamo PayPer" logicalId="BadDynamoPayPer" awsResourceType="AWS::DynamoDB::Table" billingMode="PAY_PER_REQUEST">
      <attributeDefinitions id="bad-payper-attr" name="Id" attributeName="id" attributeType="S"/>
      <keySchema id="bad-payper-key" name="Id Key" attributeName="id" keyType="HASH"/>
      <provisionedThroughput id="bad-payper-throughput" name="Bad Throughput" readCapacityUnits="0" writeCapacityUnits="0"/>
    </resources>
    <resources xsi:type="awspsmstorage:S3Bucket" id="bad-bucket" name="Bad Bucket" logicalId="BadBucket" awsResourceType="AWS::S3::Bucket" productionCritical="true" websiteConfigurationJson="{}" publicAccessMode="DISABLED_NOT_RECOMMENDED" versioningStatus="SUSPENDED">
      <encryption id="bad-bucket-encryption" name="Bad Encryption" sseAlgorithm="aws:kms"/>
      <notificationConfiguration id="bad-notification-config" name="Bad Notification">
        <rules id="bad-notification-rule" name="Bad Notification Rule"/>
      </notificationConfiguration>
      <replicationConfiguration id="bad-replication" name="Bad Replication"/>
    </resources>
    <resources xsi:type="awspsmstorage:S3Bucket" id="bad-unencrypted-bucket" name="Bad Unencrypted Bucket" logicalId="BadUnencryptedBucket" awsResourceType="AWS::S3::Bucket" productionCritical="true" publicAccessMode="STRICT_BLOCK_ALL" versioningStatus="ENABLED"/>
    <resources xsi:type="awspsmsecurity:IamRole" id="bad-role" name="Bad Role" logicalId="BadRole" awsResourceType="AWS::IAM::Role" productionCritical="true">
      <assumeRolePolicy id="bad-empty-trust-policy" name="Bad Empty Trust"/>
      <inlinePolicies id="bad-inline" name="Bad Inline" policyName="BadInline">
        <document id="bad-inline-document" name="Bad Inline Document">
          <statements id="bad-statement" name="Bad Statement" effect="ALLOW" wildcardAction="true" wildcardResource="true">
            <principals id="bad-principal" name="Bad Principal"/>
            <conditions id="bad-condition" name="Bad Condition"/>
            <notActions>iam:DeleteRole</notActions>
          </statements>
          <statements id="bad-wildcard-statement" name="Bad Wildcard Statement" effect="ALLOW" wildcardAction="true" wildcardResource="true">
            <actions>*</actions>
            <resources>*</resources>
          </statements>
          <statements id="bad-missing-resource-statement" name="Bad Missing Resource Statement" effect="ALLOW">
            <actions>s3:GetObject</actions>
          </statements>
        </document>
      </inlinePolicies>
    </resources>
    <resources xsi:type="awspsmsecurity:KmsKey" id="bad-kms" name="Bad KMS" logicalId="BadKms" awsResourceType="AWS::KMS::Key" productionCritical="true" enableKeyRotation="false" pendingWindowInDays="31" aliases="bad-kms-alias"/>
    <resources xsi:type="awspsmsecurity:KmsAlias" id="bad-kms-alias" name="Bad Alias" logicalId="BadAlias" awsResourceType="AWS::KMS::Alias" aliasName="bad" targetKey="bad-kms"/>
    <resources xsi:type="awspsmsecurity:SecretsManagerSecret" id="bad-secret" name="Bad Secret" logicalId="BadSecret" awsResourceType="AWS::SecretsManager::Secret" productionCritical="true" rotationRequired="true"/>
    <resources xsi:type="awspsmsecurity:SecretRotationSchedule" id="bad-rotation" name="Bad Rotation" logicalId="BadRotation" awsResourceType="AWS::SecretsManager::RotationSchedule" secret="bad-secret"/>
    <resources xsi:type="awspsmsecurity:SecretsManagerSecret" id="bad-secret-no-rotation" name="Bad Secret No Rotation" logicalId="BadSecretNoRotation" awsResourceType="AWS::SecretsManager::Secret" productionCritical="true" rotationRequired="true"/>
    <resources xsi:type="awspsmsecurity:SsmParameter" id="bad-parameter" name="Bad Parameter" logicalId="BadParameter" awsResourceType="AWS::SSM::Parameter" parameterName="/bad/param" parameterType="STRING">
      <value id="bad-parameter-value" name="Bad Parameter Value" sourceKind="PLAINTEXT" literal="secret" secret="true"/>
    </resources>
    <resources xsi:type="awspsmsecurity:SsmParameter" id="bad-secure-parameter" name="Bad Secure Parameter" logicalId="BadSecureParameter" awsResourceType="AWS::SSM::Parameter" parameterName="/bad/secure" parameterType="SECURE_STRING"/>
    <resources xsi:type="awspsmidentity:CognitoUserPool" id="bad-user-pool-full" name="Bad User Pool Full" logicalId="BadUserPoolFull" awsResourceType="AWS::Cognito::UserPool" productionCritical="true" mfaConfiguration="OFF" mfaDecision="UNDECIDED" deletionProtection="false"/>
    <resources xsi:type="awspsmidentity:CognitoIdentityPool" id="bad-identity-pool" name="Bad Identity Pool" logicalId="BadIdentityPool" awsResourceType="AWS::Cognito::IdentityPool" allowUnauthenticatedIdentities="true"/>
    <resources xsi:type="awspsmidentity:CognitoUserPoolClient" id="bad-user-pool-client" name="Bad Client" logicalId="BadClient" awsResourceType="AWS::Cognito::UserPoolClient" allowedOAuthFlowsUserPoolClient="true" preventUserExistenceErrors="LEGACY" userPool="bad-user-pool-full"/>
    <resources xsi:type="awspsmworkflow:StepFunctionStateMachine" id="bad-state-machine" name="Bad State Machine" logicalId="BadStateMachine" awsResourceType="AWS::Serverless::StateMachine" productionCritical="true" publishAlias="true" stateMachineType="STANDARD"/>
    <resources xsi:type="awspsmworkflow:StepFunctionStateMachine" id="bad-state-machine-empty-asl" name="Bad Empty ASL" logicalId="BadEmptyAsl" awsResourceType="AWS::Serverless::StateMachine" role="%s" stateMachineType="STANDARD">
      <aslDocument id="bad-empty-asl" name="Bad Empty ASL" startAt="Missing"/>
    </resources>
    <resources xsi:type="awspsmworkflow:StepFunctionStateMachine" id="bad-state-machine-asl" name="Bad ASL" logicalId="BadAsl" awsResourceType="AWS::Serverless::StateMachine" role="%s" definitionUri="s.asl.json" definitionString="{}" stateMachineType="STANDARD">
      <aslDocument id="bad-asl" name="Bad ASL" startAt="Missing">
        <states xsi:type="awspsmworkflow:AslTaskState" id="bad-state-a" name="Bad State A" stateName="Duplicate">
          <retry id="bad-asl-retry" name="Bad ASL Retry" intervalSeconds="0" maxAttempts="-1" backoffRate="0.5" maxDelaySeconds="0"/>
          <catch id="bad-asl-catch" name="Bad ASL Catch" nextState="bad-state-terminal-next"/>
        </states>
        <states xsi:type="awspsmworkflow:AslTaskState" id="bad-state-b" name="Bad State B" stateName="Duplicate"/>
        <states xsi:type="awspsmworkflow:AslSucceedState" id="bad-state-terminal-next" name="Bad Terminal Next" stateName="Terminal" end="true" nextStateName="After"/>
        <states xsi:type="awspsmworkflow:AslChoiceState" id="bad-choice" name="Bad Choice" stateName="Choice">
          <choices id="bad-choice-rule" name="Bad Choice Rule" nextState="bad-state-terminal-next"/>
        </states>
        <states xsi:type="awspsmworkflow:AslChoiceState" id="bad-choice-empty" name="Bad Choice Empty" stateName="ChoiceEmpty"/>
      </aslDocument>
    </resources>
    <resources xsi:type="awspsmobservability:CloudWatchLogGroup" id="bad-log-group" name="Bad Log Group" logicalId="BadLogGroup" awsResourceType="AWS::Logs::LogGroup" productionCritical="true"/>
    <resources xsi:type="awspsmobservability:CloudWatchAlarm" id="bad-alarm" name="Bad Alarm" logicalId="BadAlarm" awsResourceType="AWS::CloudWatch::Alarm" period="0" evaluationPeriods="1" datapointsToAlarm="2" monitoredResource="%s"/>
    <resources xsi:type="awspsmobservability:CloudWatchCompositeAlarm" id="bad-composite-alarm" name="Bad Composite Alarm" logicalId="BadCompositeAlarm" awsResourceType="AWS::CloudWatch::CompositeAlarm"/>
    <resources xsi:type="awspsmnetworking:Vpc" id="bad-vpc" name="Bad Vpc" logicalId="BadVpc" awsResourceType="AWS::EC2::VPC" enableDnsHostnames="false" enableDnsSupport="false"/>
    <resources xsi:type="awspsmnetworking:SecurityGroup" id="bad-security-group" name="Bad SG" logicalId="BadSecurityGroup" awsResourceType="AWS::EC2::SecurityGroup" vpc="bad-vpc">
      <ingressRules id="bad-sg-rule" name="Bad Rule" ipProtocol="tcp" fromPort="3389" toPort="22" cidrIp="0.0.0.0/0"/>
      <ingressRules id="bad-sg-admin-rule" name="Bad Admin Rule" ipProtocol="tcp" fromPort="22" toPort="22" cidrIp="0.0.0.0/0"/>
    </resources>
    <resources xsi:type="awspsmcompute:AwsLambdaFunction" id="bad-vpc-lambda" name="Bad VPC Lambda" logicalId="BadVpcLambda" awsResourceType="AWS::Serverless::Function" role="%s" logGroup="%s">
      <code xsi:type="awspsmcompute:LambdaZipCodeConfig" id="bad-vpc-code" name="Bad VPC Code" codeUri="src" runtimeIdentifier="nodejs22.x" handler="index.handler"/>
      <vpcConfig id="bad-vpc-config" name="Bad VPC Config" vpc="bad-vpc" vpcId="vpc-123" privateSubnetsRequireNatOrEndpoints="true">
        <requiredEndpoints id="bad-endpoint" name="Bad Endpoint" requiredForPrivateAccess="true"/>
      </vpcConfig>
    </resources>
"""
        .formatted(
            VALID_LAMBDA_ID,
            VALID_LAMBDA_ID,
            VALID_ROLE_ID,
            VALID_ROLE_ID,
            VALID_LAMBDA_ID,
            VALID_ROLE_ID,
            VALID_LOG_GROUP_ID);
  }

  private static String relationshipFragment() {
    return """
  <relationshipViews xsi:type="awspsmintegrations:ApiGatewayLambdaIntegrationView" id="bad-view-empty" name="Bad View Empty"/>
  <relationshipViews xsi:type="awspsmintegrations:ApiGatewayLambdaIntegrationView" id="bad-api-lambda-view" name="Bad Api Lambda View" source="bad-rule" target="%s" route="bad-http-route-a" integration="bad-integration" function="%s"/>
  <relationshipViews xsi:type="awspsmintegrations:EventBridgeLambdaTargetView" id="bad-event-lambda-view" name="Bad Event Lambda View" source="bad-rule" target="%s" rule="bad-rule" targetRow="bad-target-b" function="%s"/>
  <relationshipViews xsi:type="awspsmintegrations:SnsLambdaSubscriptionView" id="bad-sns-lambda-view" name="Bad Sns Lambda View" source="bad-fifo-topic" target="%s" topic="bad-fifo-topic" subscription="bad-sub-empty" function="%s"/>
  <relationshipViews xsi:type="awspsmintegrations:SqsLambdaEventSourceView" id="bad-sqs-lambda-view" name="Bad Sqs Lambda View" source="bad-fifo-queue" target="%s" queue="bad-standard-fifo-name-queue" function="%s" mapping="bad-sqs-mapping"/>
  <relationshipViews xsi:type="awspsmintegrations:StepFunctionEventBridgeTargetView" id="bad-sfn-event-view" name="Bad Sfn Event View" source="bad-rule" target="bad-state-machine" rule="bad-rule" targetRow="bad-target-b" stateMachine="bad-state-machine"/>
"""
        .formatted(
            VALID_LAMBDA_ID,
            VALID_LAMBDA_ID,
            VALID_LAMBDA_ID,
            VALID_LAMBDA_ID,
            VALID_LAMBDA_ID,
            VALID_LAMBDA_ID,
            VALID_LAMBDA_ID,
            VALID_LAMBDA_ID);
  }

  private static String helperModel() {
    return """
<?xml version="1.0" encoding="ASCII"?>
<awspsm:AwsPsmModel xmi:version="2.0" xmlns:xmi="http://www.omg.org/XMI" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns:awspsm="https://varka.org/awspsm/1.0" xmlns:awspsmapi="https://varka.org/awspsm/api/1.0" xmlns:awspsmcompute="https://varka.org/awspsm/compute/1.0" xmlns:awspsmcore="https://varka.org/awspsm/core/1.0" xmlns:awspsmobservability="https://varka.org/awspsm/observability/1.0" xmlns:awspsmsecurity="https://varka.org/awspsm/security/1.0" xmlns:awspsmstorage="https://varka.org/awspsm/storage/1.0" xmlns:awspsmworkflow="https://varka.org/awspsm/workflow/1.0" id="helper-root" name="Helper Root" defaultRegion="us-east-1" productionMode="true">
  <stages id="helper-prod" name="prod" stageName="prod" accountId="123456789012" region="us-east-1" requiresManualApproval="true" confirmChangeset="true" environmentClass="PROD" deploysStacks="helper-stack"/>
  <stacks id="helper-stack" name="Helper Stack" stackName="helper-stack" useSamTransform="true" validateWithSam="true" validateWithCfnLint="true">
    <resources xsi:type="awspsmsecurity:IamRole" id="helper-role" name="Helper Role" logicalId="HelperRole" awsResourceType="AWS::IAM::Role" productionCritical="true">
      <tags id="helper-role-owner" name="Owner" key="Owner" value="team"/>
      <tags id="helper-role-env" name="Environment" key="Environment" value="prod"/>
      <assumeRolePolicy id="helper-trust" name="Helper Trust">
        <statements id="helper-trust-statement" name="Trust" effect="ALLOW">
          <actions>sts:AssumeRole</actions>
          <principals id="helper-principal" name="Lambda Principal" principalType="Service">
            <identifiers>lambda.amazonaws.com</identifiers>
          </principals>
        </statements>
      </assumeRolePolicy>
      <inlinePolicies id="helper-inline" name="Helper Inline" policyName="HelperInline">
        <document id="helper-policy" name="Helper Policy">
        <statements id="helper-statement" name="Helper Statement" effect="ALLOW" wildcardAction="true" wildcardResource="true" wildcardJustification="Approved test wildcard">
          <actions>*</actions>
          <resources>*</resources>
        </statements>
        <statements id="helper-not-side-statement" name="Helper Not Side Statement" effect="ALLOW">
          <notActions>s3:DeleteObject</notActions>
          <notResources>*</notResources>
        </statements>
        <statements id="helper-condition-statement" name="Helper Condition Statement" effect="ALLOW">
          <actions>s3:GetObject</actions>
          <resources>arn:aws:s3:::example/*</resources>
          <conditions id="helper-condition" name="Helper Condition" operator="StringEquals" key="aws:PrincipalOrgID">
            <values>o-example</values>
          </conditions>
        </statements>
        <statements id="helper-no-side-statement" name="Helper No Side Statement" effect="ALLOW"/>
      </document>
    </inlinePolicies>
  </resources>
  <resources xsi:type="awspsmcore:AwsNativeResource" id="helper-named-only" name="Named Only" awsResourceType="Custom::NamedOnly"/>
  <resources xsi:type="awspsmcore:AwsNativeResource" id="helper-class-only" awsResourceType="Custom::ClassOnly"/>
  <resources xsi:type="awspsmcore:AwsNativeResource" id="helper-duplicate-a" name="Duplicate A" logicalId="DuplicateHelperLogical" awsResourceType="Custom::DuplicateA"/>
  <resources xsi:type="awspsmcore:AwsNativeResource" id="helper-duplicate-b" name="Duplicate B" logicalId="DuplicateHelperLogical" awsResourceType="Custom::DuplicateB"/>
  <resources xsi:type="awspsmcore:AwsNativeResource" id="helper-cycle-a" name="Cycle A" logicalId="HelperCycleA" awsResourceType="Custom::CycleA" dependsOn="helper-cycle-b">
    <tags id="helper-cycle-a-tag-a" name="Dup A" key="Dup" value="a"/>
    <tags id="helper-cycle-a-tag-b" name="Dup B" key="Dup" value="b"/>
  </resources>
  <resources xsi:type="awspsmcore:AwsNativeResource" id="helper-cycle-b" name="Cycle B" logicalId="HelperCycleB" awsResourceType="Custom::CycleB" dependsOn="helper-cycle-a"/>
  <resources xsi:type="awspsmobservability:CloudWatchLogGroup" id="helper-log" name="Helper Log" logicalId="HelperLog" awsResourceType="AWS::Logs::LogGroup">
    <tags id="helper-log-owner" name="Owner" key="Owner" value="team"/>
    <tags id="helper-log-env" name="Environment" key="Environment" value="prod"/>
  </resources>
  <resources xsi:type="awspsmcompute:AwsLambdaFunction" id="helper-function" name="Helper Function" logicalId="HelperFunction" awsResourceType="AWS::Serverless::Function" productionCritical="true" retainInProduction="true" role="helper-role" logGroup="helper-log">
    <tags id="helper-function-owner" name="Owner" key="Owner" value="team"/>
    <tags id="helper-function-env" name="Environment" key="Environment" value="prod"/>
    <code xsi:type="awspsmcompute:LambdaZipCodeConfig" id="helper-code" name="Helper Code" codeUri="src" runtimeIdentifier="nodejs22.x" handler="index.handler"/>
  </resources>
  <resources xsi:type="awspsmcompute:AwsLambdaFunction" id="helper-generated-function" name="Helper Generated Function" logicalId="HelperGeneratedFunction" awsResourceType="AWS::Serverless::Function" role="helper-role" logGroup="helper-log">
    <code xsi:type="awspsmcompute:LambdaZipCodeConfig" id="helper-generated-code" name="Helper Generated Code" codeUri="generated" runtimeIdentifier="nodejs22.x" handler="index.handler" generatedByTransformation="true"/>
  </resources>
    <resources xsi:type="awspsmstorage:DynamoDbTable" id="helper-table" name="Helper Table" logicalId="HelperTable" awsResourceType="AWS::DynamoDB::Table" billingMode="PAY_PER_REQUEST">
      <attributeDefinitions id="helper-attr" name="Id" attributeName="id" attributeType="S"/>
      <keySchema id="helper-key" name="Id Key" attributeName="id" keyType="HASH"/>
    </resources>
  <resources xsi:type="awspsmstorage:S3Bucket" id="helper-bucket" name="Helper Bucket" logicalId="HelperBucket" awsResourceType="AWS::S3::Bucket" publicAccessMode="STRICT_BLOCK_ALL"/>
  <resources xsi:type="awspsmstorage:S3Bucket" id="helper-block-bucket" name="Helper Block Bucket" logicalId="HelperBlockBucket" awsResourceType="AWS::S3::Bucket">
    <publicAccessBlock id="helper-public-block" name="Helper Public Block" blockPublicAcls="true" blockPublicPolicy="true" ignorePublicAcls="true" restrictPublicBuckets="true"/>
  </resources>
  <resources xsi:type="awspsmstorage:S3Bucket" id="helper-open-bucket" name="Helper Open Bucket" logicalId="HelperOpenBucket" awsResourceType="AWS::S3::Bucket" publicAccessMode="DISABLED_NOT_RECOMMENDED"/>
  <resources xsi:type="awspsmstorage:S3Bucket" id="helper-partial-bucket" name="Helper Partial Bucket" logicalId="HelperPartialBucket" awsResourceType="AWS::S3::Bucket" publicAccessMode="DISABLED_NOT_RECOMMENDED">
    <publicAccessBlock id="helper-partial-public-block" name="Helper Partial Public Block" blockPublicAcls="true" blockPublicPolicy="true" ignorePublicAcls="true" restrictPublicBuckets="false"/>
  </resources>
  <resources xsi:type="awspsmapi:HttpApi" id="helper-api" name="Helper API" logicalId="HelperApi" awsResourceType="AWS::Serverless::HttpApi">
    <routes xsi:type="awspsmapi:HttpApiRoute" id="helper-route-a" name="Helper Route" logicalId="HelperRoute" awsResourceType="AWS::ApiGatewayV2::Route" path="/helper" method="GET" authorizationType="NONE" integration="helper-integration"/>
    <routes xsi:type="awspsmapi:HttpApiRoute" id="helper-route-b" name="Helper Route Duplicate" logicalId="HelperRouteDuplicate" awsResourceType="AWS::ApiGatewayV2::Route" path="/helper" method="GET" authorizationType="NONE" integration="helper-integration"/>
  </resources>
  <resources xsi:type="awspsmapi:HttpApiRoute" id="helper-orphan-route" name="Helper Orphan Route" logicalId="HelperOrphanRoute" awsResourceType="AWS::ApiGatewayV2::Route" path="/orphan" method="GET" authorizationType="NONE" integration="helper-integration"/>
  <resources xsi:type="awspsmapi:RestApi" id="helper-rest-api" name="Helper Rest API" logicalId="HelperRestApi" awsResourceType="AWS::Serverless::Api">
    <routes xsi:type="awspsmapi:RestApiRoute" id="helper-rest-route" name="Helper Rest Route" logicalId="HelperRestRoute" awsResourceType="AWS::ApiGateway::Method" path="/rest" method="POST" integration="helper-integration"/>
    <routes xsi:type="awspsmapi:RestApiRoute" id="helper-rest-route-duplicate" name="Helper Rest Route Duplicate" logicalId="HelperRestRouteDuplicate" awsResourceType="AWS::ApiGateway::Method" path="/rest" method="POST" integration="helper-integration"/>
  </resources>
  <resources xsi:type="awspsmapi:WebSocketApi" id="helper-ws-api" name="Helper WebSocket API" logicalId="HelperWsApi" awsResourceType="AWS::ApiGatewayV2::Api" routeSelectionExpression="$request.body.action">
    <routes xsi:type="awspsmapi:WebSocketRoute" id="helper-ws-route" name="Helper WS Route" logicalId="HelperWsRoute" awsResourceType="AWS::ApiGatewayV2::Route" routeKey="$connect" authorizationType="NONE" integration="helper-integration"/>
    <routes xsi:type="awspsmapi:WebSocketRoute" id="helper-ws-route-duplicate" name="Helper WS Route Duplicate" logicalId="HelperWsRouteDuplicate" awsResourceType="AWS::ApiGatewayV2::Route" routeKey="$connect" authorizationType="NONE" integration="helper-integration"/>
  </resources>
  <resources xsi:type="awspsmapi:ApiGatewayIntegration" id="helper-integration" name="Helper Integration" logicalId="HelperIntegration" awsResourceType="AWS::ApiGatewayV2::Integration" integrationType="AWS_PROXY" lambdaTarget="helper-function"/>
  <resources xsi:type="awspsmapi:ApiGatewayIntegration" id="helper-uri-integration" name="Helper URI Integration" logicalId="HelperUriIntegration" awsResourceType="AWS::ApiGatewayV2::Integration" integrationType="HTTP_PROXY" integrationUri="https://example.com"/>
  <resources xsi:type="awspsmapi:ApiGatewayIntegration" id="helper-empty-integration" name="Helper Empty Integration" logicalId="HelperEmptyIntegration" awsResourceType="AWS::ApiGatewayV2::Integration" integrationType="AWS_PROXY"/>
  <resources xsi:type="awspsmapi:ApiGatewayIntegration" id="helper-double-integration" name="Helper Double Integration" logicalId="HelperDoubleIntegration" awsResourceType="AWS::ApiGatewayV2::Integration" integrationType="AWS_PROXY" lambdaTarget="helper-function" stateMachineTarget="helper-machine"/>
  <resources xsi:type="awspsmworkflow:StepFunctionStateMachine" id="helper-machine" name="Helper Machine" logicalId="HelperMachine" awsResourceType="AWS::Serverless::StateMachine" role="helper-role" stateMachineType="STANDARD">
    <aslDocument id="helper-asl" name="Helper ASL" startAt="Done">
      <states xsi:type="awspsmworkflow:AslSucceedState" id="helper-done" name="Done" stateName="Done" end="true"/>
    </aslDocument>
  </resources>
  <resources xsi:type="awspsmworkflow:StepFunctionStateMachine" id="helper-uri-machine" name="Helper URI Machine" logicalId="HelperUriMachine" awsResourceType="AWS::Serverless::StateMachine" role="helper-role" stateMachineType="STANDARD" definitionUri="state.asl.json"/>
  <resources xsi:type="awspsmworkflow:StepFunctionStateMachine" id="helper-string-machine" name="Helper String Machine" logicalId="HelperStringMachine" awsResourceType="AWS::Serverless::StateMachine" role="helper-role" stateMachineType="STANDARD" definitionString="{}"/>
  <resources xsi:type="awspsmworkflow:StepFunctionStateMachine" id="helper-mixed-machine" name="Helper Mixed Machine" logicalId="HelperMixedMachine" awsResourceType="AWS::Serverless::StateMachine" role="helper-role" stateMachineType="STANDARD" definitionUri="state.asl.json" definitionString="{}">
    <aslDocument id="helper-duplicate-asl" name="Helper Duplicate ASL" startAt="Duplicate">
      <states xsi:type="awspsmworkflow:AslSucceedState" id="helper-duplicate-state-a" name="Duplicate A" stateName="Duplicate" end="true"/>
      <states xsi:type="awspsmworkflow:AslSucceedState" id="helper-duplicate-state-b" name="Duplicate B" stateName="Duplicate" end="true"/>
    </aslDocument>
  </resources>
  <metadata id="helper-secret-metadata" name="Secret" propertyName="Secret" required="false" secret="false" format="JSON" validationState="NEEDS_REVIEW">
    <value id="helper-secret-ref" name="Secret Ref" sourceKind="SECRETS_MANAGER_REFERENCE" expression="secret" secret="true"/>
  </metadata>
  <metadata id="helper-ssm-metadata" name="SSM" propertyName="SSM" required="false" secret="false" format="JSON" validationState="NEEDS_REVIEW">
    <value id="helper-ssm-ref" name="SSM Ref" sourceKind="SSM_SECURE_REFERENCE" expression="param" secret="true"/>
  </metadata>
  <metadata id="helper-dynamic-metadata" name="Dynamic" propertyName="Dynamic" required="false" secret="false" format="JSON" validationState="NEEDS_REVIEW">
    <value id="helper-dynamic-ref" name="Dynamic Ref" sourceKind="DYNAMIC_REFERENCE" expression="{{resolve:ssm-secure:/x}}" secret="true"/>
  </metadata>
  <metadata id="helper-plain-metadata" name="Plain" propertyName="Plain" required="false" secret="false" format="JSON" validationState="NEEDS_REVIEW">
    <value id="helper-plain-value" name="Plain Value" sourceKind="PLAINTEXT" literal="plain"/>
  </metadata>
</stacks>
</awspsm:AwsPsmModel>
""";
  }

  private static String explicitTaggingPolicyHelperModel() {
    return """
<?xml version="1.0" encoding="ASCII"?>
<awspsm:AwsPsmModel xmi:version="2.0" xmlns:xmi="http://www.omg.org/XMI" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns:awspsm="https://varka.org/awspsm/1.0" xmlns:awspsmcore="https://varka.org/awspsm/core/1.0" id="tagging-helper-root" name="Tagging Helper Root" defaultRegion="us-east-1">
  <taggingPolicy id="tagging-policy" name="Tagging Policy" requireOwnerTag="true" requireEnvironmentTag="false" requireCostCenterTag="true">
    <requiredTags id="project-required-tag" name="Project Required Tag" key="Project"/>
  </taggingPolicy>
  <stacks id="tagging-stack" name="Tagging Stack" stackName="tagging-stack">
    <resources xsi:type="awspsmcore:AwsNativeResource" id="tagged-resource" name="Tagged Resource" logicalId="TaggedResource" awsResourceType="Custom::Tagged" cloudFormationType="Custom::Tagged">
      <tags id="tagged-owner" name="Owner" key="Owner" value="team"/>
      <tags id="tagged-cost-center" name="Cost Center" key="CostCenter" value="cc-1"/>
      <tags id="tagged-project" name="Project" key="Project" value="project-a"/>
      <properties id="tagged-property" name="Name" propertyName="Name" required="false" secret="false" format="JSON" validationState="NEEDS_REVIEW">
        <value id="tagged-property-value" name="Name Value" sourceKind="PLAINTEXT" literal="tagged"/>
      </properties>
    </resources>
  </stacks>
</awspsm:AwsPsmModel>
""";
  }

  private static String minimalBrokenRootModel() {
    return """
<?xml version="1.0" encoding="ASCII"?>
<awspsm:AwsPsmModel xmi:version="2.0" xmlns:xmi="http://www.omg.org/XMI" xmlns:awspsm="https://varka.org/awspsm/1.0" id="bad-root" name="Bad Root" productionMode="true"/>
""";
  }

  private static String minimalEmptyStackRootModel() {
    return """
<?xml version="1.0" encoding="ASCII"?>
<awspsm:AwsPsmModel xmi:version="2.0" xmlns:xmi="http://www.omg.org/XMI" xmlns:awspsm="https://varka.org/awspsm/1.0" id="empty-stack-root" name="Empty Stack Root" defaultRegion="us-east-1" productionMode="false">
  <stages id="empty-dev" name="dev" stageName="dev" accountId="123456789012" region="us-east-1" environmentClass="DEV"/>
  <stacks id="empty-stack" name="Empty Stack" stackName="empty-stack" useSamTransform="true" validateWithSam="true" validateWithCfnLint="true"/>
</awspsm:AwsPsmModel>
""";
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
    return validate(model.toString(), model, true);
  }

  private EvlValidationReport validate(String scenario, Path model, boolean structuralValidation)
      throws Exception {
    try {
      return new EpsilonEvlValidator()
          .validate(
              EvlValidationRequest.forRoot(
                  PSM_EVL,
                  List.of(
                      new FileEvlModelConfiguration(
                          "AWSPSM", ALIASES, model, List.of(PSM_ECORE), structuralValidation)),
                  true));
    } catch (EvlValidationException ex) {
      throw new AssertionError(
          scenario + " failed with diagnostics: " + ex.getReport().diagnostics(), ex);
    }
  }

  private Path writeModel(String name, String xml) throws Exception {
    Path model = tempDir.resolve(name + ".awspsm.xmi");
    Files.writeString(model, xml);
    return model;
  }

  private static String insertBeforeRootClose(String xmi, String fragment) {
    return xmi.replace("</awspsm:AwsPsmModel>", fragment + "\n</awspsm:AwsPsmModel>");
  }

  private static String insertBeforeFirstStackClose(String xmi, String fragment) {
    return xmi.replaceFirst(
        "(?m)^  </stacks>", Matcher.quoteReplacement(fragment + "\n  </stacks>"));
  }

  private static String insertIntoTraceModel(String xmi, String fragment) {
    return xmi.replace("</traceModel>", fragment + "\n  </traceModel>");
  }

  private static String withNetworkingNamespace(String xmi) {
    if (xmi.contains("xmlns:awspsmnetworking=")) {
      return xmi;
    }
    return xmi.replace(
        "xmlns:awspsmobservability=\"https://varka.org/awspsm/observability/1.0\"",
        "xmlns:awspsmnetworking=\"https://varka.org/awspsm/networking/1.0\""
            + " xmlns:awspsmobservability=\"https://varka.org/awspsm/observability/1.0\"");
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

  private static Set<String> executableRuleCoverage() {
    return Set.of(
        "TraceLinkHasReferenceOrExternalId",
        "ApiHasRoutes",
        "ApiShouldHaveStage",
        "AccessLogsRequireLogGroupAndFormat",
        "ProductionApiShouldHaveMetricsAndTracing",
        "UniqueHttpRouteWithinApi",
        "UniqueRestRouteWithinApi",
        "UniqueWebSocketRouteKeyWithinApi",
        "RouteHasApi",
        "ProtectedRouteHasRequiredAuthConfiguration",
        "RouteHasIntegration",
        "IntegrationTimeoutShouldNotExceedLambdaTimeout",
        "IntegrationHasSingleTarget",
        "LongApiGatewayTimeoutRequiresQuotaReview",
        "CredentialsArnMatchesCredentialsRole",
        "AccessLogStageRequiresGroupAndFormat",
        "ProductionStageShouldThrottle",
        "JwtAuthorizerHasIssuerAndAudience",
        "CognitoAuthorizerShouldReferenceClients",
        "LambdaAuthorizerHasFunction",
        "DomainHasCertificate",
        "UsagePlanKeyUsesKnownType",
        "ModelHasStacks",
        "ModelHasStages",
        "ProductionModeHasProdStage",
        "UniqueStackNames",
        "UniqueStageNames",
        "DefaultRegionRecommended",
        "StackResourcesExist",
        "StageHasAccountAndRegion",
        "ProdRequiresApproval",
        "ProdShouldConfirmChangeset",
        "StageDeploysAtLeastOneStack",
        "DeployableStackHasResources",
        "StackHasResources",
        "StackLogicalIdsAreUnique",
        "SamTransformRecommended",
        "ValidationToolsRecommended",
        "LogicalIdValid",
        "LogicalIdUniqueInStack",
        "NoDirectSelfDependency",
        "NoDependencyCycles",
        "ImportedResourceHasImportIdentity",
        "NonImportedResourceShouldNotHaveImportMetadata",
        "DeployableResourceHasAwsType",
        "ProductionResourcesHaveRequiredTags",
        "TagKeysAreUniquePerResource",
        "ProductionResourcesShouldRetainOnDelete",
        "NativeResourceTypeNameValid",
        "ValueExpressionSourceShape",
        "SecretLiteralReviewed",
        "TagKeyHasText",
        "AvoidAwsReservedTagPrefix",
        "RequiredNativePropertyHasValue",
        "SecretNativePropertyUsesSecureExpression",
        "PlainTextHasLiteral",
        "CloudFormationRefHasTarget",
        "GetAttHasResourceAndAttribute",
        "ListExpressionHasItems",
        "MapExpressionHasEntries",
        "SecretValueMustUseSecureReference",
        "MapEntryHasKey",
        "LambdaHasExecutionRole",
        "LambdaHasLogGroup",
        "LambdaHasCodeConfig",
        "LambdaHasSupportedCodeConfig",
        "LambdaPackageTypeMatchesCodeConfig",
        "LambdaMemoryRange",
        "LambdaTimeoutRange",
        "LambdaEphemeralStorageRange",
        "ReservedConcurrencyNonNegative",
        "AutoPublishAliasRequiresVersionPublishing",
        "CodeSigningDecisionHonored",
        "ProductionLambdaShouldUseTracing",
        "ProductionLambdaShouldUseStructuredLogging",
        "ProductionLambdaShouldHaveFailureDestinationOrDlq",
        "ZipCodeHasRuntimeAndHandler",
        "ZipCodeHasExactlyOneLocation",
        "ImageCodeHasImageUri",
        "EnvironmentVariableNameValid",
        "SecretEnvironmentValueUsesSecureReference",
        "StageSpecificVariableShouldHaveRationale",
        "DlqHasExactlyOneTarget",
        "BatchSizePositive",
        "MaximumBatchingWindowRange",
        "ParallelizationFactorRange",
        "StartingTimestampRequiresAtTimestamp",
        "QueueVisibilityGreaterThanFunctionTimeout",
        "PartialBatchFailureRecommendedForSqs",
        "RequiredPartialBatchFailureDecisionHonored",
        "DynamoStreamMappingRequiresStreamSpecification",
        "DynamoStreamMappingHasStartingPosition",
        "ProvisionedConcurrencyPositive",
        "ProductionFunctionUrlRequiresAuth",
        "RuleHasPatternOrSchedule",
        "RuleDoesNotMixPatternAndSchedule",
        "RuleHasTargets",
        "TargetIdsUniqueWithinRule",
        "TargetHasResourceOrArn",
        "CriticalEventTargetsHaveRetryOrDlq",
        "SqsFifoTargetHasMessageGroupId",
        "NonLambdaTargetHasInvokeRole",
        "RetryPolicyRangesValid",
        "ScheduleHasExpressionAndRole",
        "ScheduleHasTarget",
        "PipeHasSourceTargetAndRole",
        "ApiDestinationHasEndpointAndConnection",
        "ConnectionAuthParametersMatchAuthorizationType",
        "MfaForProductionPrivilegedPools",
        "MfaDecisionMadeForProductionCriticalPools",
        "ProductionUserPoolShouldUseDeletionProtection",
        "OAuthClientHasCallbackUrls",
        "PreventUserExistenceErrorsRecommended",
        "UnauthenticatedIdentitiesRequireReview",
        "FifoQueueNameSuffix",
        "StandardQueueShouldNotUseFifoSuffix",
        "QueueTimingRangesValid",
        "QueueMessageSizeRangeValid",
        "RedrivePolicyValid",
        "ProductionQueueShouldBeEncrypted",
        "FifoTopicNameSuffix",
        "ProductionTopicShouldBeEncrypted",
        "SubscriptionHasEndpointOrResource",
        "FifoTopicToSqsRequiresFifoQueue",
        "ExternalHttpSubscriptionsShouldHaveDlq",
        "FilterRuleHasValues",
        "VpcAttachmentHasSubnetsAndSecurityGroups",
        "ModeledVpcAttachmentShouldNotContradictRawIds",
        "PrivateSubnetsRequireEndpointsWhenFlagged",
        "VpcShouldEnableDns",
        "PortRangeValid",
        "PublicAdminIngressRequiresReview",
        "RelationshipViewHasEndpoints",
        "ApiLambdaViewMatchesDeployableObjects",
        "ApiLambdaPermissionRecommended",
        "EventBridgeLambdaViewMatchesDeployableObjects",
        "EventBridgeLambdaPermissionRecommended",
        "SnsLambdaViewMatchesDeployableObjects",
        "SnsLambdaPermissionRecommended",
        "SqsLambdaViewMatchesMapping",
        "StepFunctionEventBridgeViewMatchesTarget",
        "ProductionLogRetentionExplicit",
        "ProductionLogGroupShouldUseKms",
        "AlarmHasMetricAndThreshold",
        "AlarmEvaluationSettingsValid",
        "ProductionAlarmShouldHaveActions",
        "CompositeAlarmShouldHaveActions",
        "RoleHasTrustPolicyStatements",
        "ProductionRoleShouldUsePermissionsBoundary",
        "PolicyDocumentHasStatements",
        "StatementHasActionAndResourceSide",
        "NoAllowWildcardInProductionWithoutJustification",
        "AvoidNotActionInAllowStatements",
        "PrincipalHasTypeAndIdentifiers",
        "ConditionComplete",
        "ProductionKmsKeyRotation",
        "PendingWindowRange",
        "KmsAliasNameValid",
        "SecretHasValueOrGenerator",
        "RotationRequiredHasSchedule",
        "ProductionSecretShouldUseKms",
        "RotationScheduleHasRulesOrLambda",
        "SecureParameterUsesSecureType",
        "SecureParameterShouldUseKmsKey",
        "DynamoTableHasValidPrimaryKeySchema",
        "DynamoAttributesCoverTableKeys",
        "DynamoIndexKeysAreCoveredByAttributes",
        "ProvisionedModeNeedsThroughput",
        "PayPerRequestDoesNotUseProvisionedThroughput",
        "ProvisionedThroughputPositive",
        "ProductionTableShouldHavePitRecoveryAndDeletionProtection",
        "ProductionTableShouldBeEncryptedWithKms",
        "GsiProjectionIncludeHasAttributes",
        "GsiProvisionedThroughputPositiveWhenPresent",
        "EnabledTtlHasAttributeName",
        "ProductionBucketsBlockPublicAccess",
        "ProductionBucketsEncrypted",
        "ProductionBucketsShouldVersion",
        "WebsiteBucketShouldNotBeProductionCritical",
        "KmsAlgorithmRequiresKmsKey",
        "NotificationRuleHasEventAndDestination",
        "ReplicationHasRoleAndRules",
        "StateMachineHasRole",
        "StateMachineHasDefinition",
        "StateMachineShouldUseSingleDefinitionSource",
        "StateMachineAslHasStates",
        "PublishAliasRequiresAliasName",
        "ProductionStateMachineShouldLogAndTrace",
        "StartAtReferencesExistingState",
        "StateNamesUnique",
        "AslHasTerminalState",
        "NonTerminalStateHasNextOrTerminalType",
        "TerminalStateDoesNotHaveNext",
        "TaskStateHasResource",
        "ChoiceStateHasChoices",
        "RetryRuleRangesValid",
        "CatchRuleHasErrorsAndNext",
        "ChoiceRuleHasConditionAndNext");
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
          && Files.isDirectory(current.resolve("mde/validation/psm"))) {
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
