package io.mehdieidi.modriss.mde.etl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.eclipse.emf.common.util.TreeIterator;
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

/** Regression tests for the default PIM-to-AWS-PSM ETL profile and its emitted EMF model shape. */
@ResourceLock("epsilon-runtime")
final class PimToAwsPsmEtlRegressionTest {

  /** Repository root discovered from the current test working directory. */
  private static final Path REPOSITORY_ROOT = findRepositoryRoot();

  /** Temporary output directory for generated AWS PSM models. */
  @TempDir Path tempDir;

  /**
   * Locates the repository root by walking upward to the MDE directories used by the regression
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
   * Transforms the repository PIM sample and verifies the AWS PSM model contains the expected
   * production resources, stages, trace links, and readiness backlog.
   *
   * @throws Exception when ETL execution or model loading fails
   */
  @Test
  void transformsSamplePimToAwsPsmWithSpecCompletenessShape() throws Exception {
    Path psmMetamodel = REPOSITORY_ROOT.resolve("mde/metamodels/psm/psm-combined.ecore");
    Path sampleModel = REPOSITORY_ROOT.resolve("mde/samples/pim.xmi");
    Path psmModel = tempDir.resolve("climate-relief-grants-awspsm.xmi");

    EtlExecutionReport report =
        executeOrFail(
            PimToAwsPsmDefaults.request(REPOSITORY_ROOT, sampleModel, psmModel, true, true));

    assertEquals(EtlExecutionStatus.SUCCEEDED, report.status(), report.diagnostics().toString());
    assertTrue(
        Files.isRegularFile(psmModel),
        "The PIM-to-AWS-PSM profile should persist an AWS PSM model.");

    Resource generatedResource = loadModel(psmMetamodel, psmModel);
    assertEquals(
        1,
        generatedResource.getContents().size(),
        "The generated PSM XMI must contain exactly one model root.");
    EObject root = generatedResource.getContents().get(0);
    assertEquals("AwsPsmModel", root.eClass().getName());
    assertEquals(
        3,
        values(root, "stages").size(),
        "The sample's dev/test/prod environments should become AWS stages.");
    assertEquals(
        2,
        values(root, "stacks").size(),
        "The sample's two deployment units should become SAM stacks.");
    assertTrue(
        Boolean.TRUE.equals(get(root, "productionMode")),
        "The sample has a production-like PROD environment.");
    assertTrue(reference(root, "traceModel") != null, "Expected trace model coverage.");
    assertFalse(
        values(reference(root, "traceModel"), "links").isEmpty(),
        "Expected generated trace links.");

    List<EObject> resources = containedAwsResources(root);
    assertFalse(resources.isEmpty(), "Expected generated AWS resources.");
    assertAny(resources, "AwsLambdaFunction", "Expected Lambda functions for PIM functions.");
    assertAnyOf(
        resources,
        List.of("HttpApi", "RestApi"),
        "Expected API Gateway APIs for resource/RPC HTTP PIM APIs.");
    assertAny(
        resources, "DynamoDbTable", "Expected DynamoDB tables for key-value/document stores.");
    assertAny(resources, "SqsQueue", "Expected SQS queues for PIM queues.");
    assertAny(resources, "SnsTopic", "Expected SNS topics for PIM topics.");
    assertAny(resources, "EventBridgeBus", "Expected EventBridge buses for PIM event buses.");
    assertAny(
        resources,
        "StepFunctionStateMachine",
        "Expected Step Functions state machines for PIM workflows.");
    assertAny(
        resources, "CognitoUserPool", "Expected Cognito user pools for PIM identity providers.");
    assertAny(
        resources, "SecretsManagerSecret", "Expected Secrets Manager secrets for PIM secrets.");
    assertAny(
        resources,
        "CloudWatchLogGroup",
        "Expected CloudWatch log groups for generated compute/API/workflow resources.");

    assertTrue(
        resources.stream()
            .filter(r -> "StepFunctionStateMachine".equals(r.eClass().getName()))
            .flatMap(r -> values(reference(r, "aslDocument"), "states").stream())
            .filter(s -> "AslTaskState".equals(s.eClass().getName()))
            .allMatch(s -> reference(s, "invokedResource") != null),
        "Every function-backed sample workflow task must retain its Lambda target.");
    assertTrue(
        resources.stream()
            .filter(r -> "StepFunctionStateMachine".equals(r.eClass().getName()))
            .flatMap(r -> values(reference(r, "aslDocument"), "states").stream())
            .filter(s -> "AslTaskState".equals(s.eClass().getName()))
            .allMatch(s -> !get(s, "resultPath").toString().isBlank()),
        "Every Lambda-backed sample workflow task must preserve or explicitly map its result.");
    assertTrue(
        allObjects(root).stream()
            .filter(s -> "AslWaitState".equals(s.eClass().getName()))
            .anyMatch(s -> Integer.valueOf(432000).equals(get(s, "timeoutSeconds"))),
        "The five-day business wait must become 432000 ASL seconds.");
    List<EObject> choiceStates =
        resources.stream()
            .filter(r -> "StepFunctionStateMachine".equals(r.eClass().getName()))
            .flatMap(r -> values(reference(r, "aslDocument"), "states").stream())
            .filter(s -> "AslChoiceState".equals(s.eClass().getName()))
            .toList();
    assertEquals(
        0,
        choiceStates.size(),
        "Unmapped evaluator-backed decisions must not emit executable ASL choices.");
    List<EObject> failClosedDecisionStates =
        resources.stream()
            .filter(r -> "StepFunctionStateMachine".equals(r.eClass().getName()))
            .flatMap(r -> values(reference(r, "aslDocument"), "states").stream())
            .filter(s -> "AslFailState".equals(s.eClass().getName()))
            .filter(
                s ->
                    List.of(
                            "Eligibility Decision Step",
                            "Recovery Decision Step",
                            "Appeal Routing Decision Step")
                        .contains(get(s, "stateName")))
            .toList();
    assertEquals(
        3,
        failClosedDecisionStates.size(),
        "Every unmapped evaluator-backed decision must fail closed until its routing is modeled.");
    EObject readiness = reference(root, "readiness");
    assertTrue(readiness != null, "Expected readiness assessment.");
    for (EObject choice : choiceStates) {
      EObject evaluator =
          resources.stream()
              .filter(r -> "StepFunctionStateMachine".equals(r.eClass().getName()))
              .flatMap(r -> values(reference(r, "aslDocument"), "states").stream())
              .filter(s -> "AslTaskState".equals(s.eClass().getName()))
              .filter(
                  s -> (get(s, "stateName") + "").equals(get(choice, "stateName") + " Evaluator"))
              .findFirst()
              .orElseThrow(
                  () ->
                      new AssertionError(
                          "Every choice with a decision function needs an evaluator Task state."));
      assertTrue(
          reference(evaluator, "invokedResource") != null,
          "The choice evaluator Task must invoke its generated Lambda.");
      assertEquals(
          get(choice, "stateName"),
          get(evaluator, "nextStateName"),
          "The evaluator Task must continue into the corresponding Choice state.");
      assertEquals(
          "$.decisionEvaluation",
          get(evaluator, "resultPath"),
          "The evaluator result must be retained without replacing the business input.");
      assertEquals(
          "JSONata",
          get(choice, "queryLanguage"),
          "Only Choice states should opt into JSONata for concrete conditions.");
      assertTrue(
          ((String) get(choice, "inputPath")).isBlank()
              && ((String) get(choice, "outputPath")).isBlank(),
          "JSONata Choice states must not retain JSONPath-only input/output fields.");
    }
    assertEquals(
        3,
        values(readiness, "manualDecisions").stream()
            .filter(
                decision ->
                    get(decision, "question")
                        .toString()
                        .startsWith("Define how decision evaluator outcomes select branches"))
            .count(),
        "Every function-backed choice must expose the missing outcome-to-branch mapping as a"
            + " blocker.");

    assertFalse(
        values(root, "relationshipViews").isEmpty(),
        "Expected generated relationship views for API/event/message integrations.");
    assertGeneratedIdsAreUuids(root);

    assertFalse(
        values(readiness, "manualDecisions").isEmpty(),
        "The sample's open AWS-specific decisions should remain visible.");
    assertFalse(values(readiness, "checks").isEmpty(), "Expected generated readiness checks.");
    assertEquals(
        3,
        values(readiness, "manualDecisions").stream()
            .filter(
                decision ->
                    get(decision, "question")
                        .toString()
                        .startsWith("Choose an executable AWS integration"))
            .count(),
        "Every adapter-backed workflow task must produce an explicit integration blocker.");
    assertEquals(
        20,
        values(readiness, "manualDecisions").stream()
            .filter(
                decision ->
                    get(decision, "question")
                        .toString()
                        .startsWith("Implement and test the generated business logic for Lambda"))
            .count(),
        "Every generator-managed sample Lambda must produce an explicit implementation blocker.");
    assertEquals(
        12,
        values(readiness, "manualDecisions").stream()
            .filter(
                decision ->
                    get(decision, "question")
                        .toString()
                        .startsWith("Define how the output of workflow task"))
            .count(),
        "Every ordinary function-backed workflow task with TBD output mapping must require"
            + " review.");
  }

  /**
   * Uses a synthetic PIM model that deliberately exercises the primary direct ETL rules. The
   * assertions verify provider semantics, not just that target classes exist.
   *
   * @throws Exception when fixture creation, ETL execution, or model loading fails
   */
  @Test
  void transformsDirectPimRuleFamiliesWithAwsSemantics() throws Exception {
    Path pimMetamodel = REPOSITORY_ROOT.resolve("mde/metamodels/pim/pim-combined.ecore");
    Path psmMetamodel = REPOSITORY_ROOT.resolve("mde/metamodels/psm/psm-combined.ecore");
    Path pimModel = tempDir.resolve("direct-rule-coverage.pim.xmi");
    Path psmModel = tempDir.resolve("direct-rule-coverage.awspsm.xmi");

    createDirectRuleCoveragePimModel(pimMetamodel, pimModel);
    executeOrFail(PimToAwsPsmDefaults.request(REPOSITORY_ROOT, pimModel, psmModel, true, true));

    EObject root = loadModel(psmMetamodel, psmModel).getContents().get(0);
    List<EObject> all = allObjects(root);
    List<EObject> resources = containedAwsResources(root);

    assertEquals("AwsPsmModel", root.eClass().getName());
    assertEquals("per-stage", get(root, "regionStrategy"));
    assertEquals("eu-west-1", get(root, "defaultRegion"));
    assertTrue(Boolean.TRUE.equals(get(root, "productionMode")));

    EObject prodStage = first(all, "AwsStage", "Production");
    assertEquals("production", get(prodStage, "stageName"));
    assertEquals("PROD", enumLabel(get(prodStage, "environmentClass")));
    assertTrue(Boolean.TRUE.equals(get(prodStage, "requiresManualApproval")));

    EObject stack = first(all, "SamStack", "Orders Unit Stack");
    assertTrue(Boolean.TRUE.equals(get(stack, "packageIndividually")));
    assertTrue(
        values(stack, "resources").stream()
            .anyMatch(r -> "AwsLambdaFunction".equals(r.eClass().getName())));

    EObject lambda = first(resources, "AwsLambdaFunction", "Create Order");
    assertEquals("AWS::Serverless::Function", get(lambda, "awsResourceType"));
    assertEquals("IMAGE", enumLabel(get(lambda, "packageType")));
    assertEquals(2048, get(lambda, "memorySizeMb"));
    assertEquals(900, get(lambda, "timeoutSeconds"));
    assertEquals(7, get(lambda, "reservedConcurrentExecutions"));
    assertEquals(10240, get(lambda, "ephemeralStorageMb"));
    assertEquals("live", get(lambda, "autoPublishAlias"));
    assertTrue(reference(lambda, "role") != null);
    assertTrue(reference(lambda, "deadLetterConfig") != null);
    assertTrue(reference(lambda, "vpcConfig") != null);
    assertTrue(
        values(lambda, "environment").stream()
            .anyMatch(e -> "ORDERS_TABLE_NAME".equals(get(e, "variableName"))));
    assertTrue(
        values(lambda, "metadata").stream()
            .anyMatch(m -> "Idempotency".equals(get(m, "propertyName"))));

    EObject httpApi = first(resources, "HttpApi", "Public Orders API");
    assertTrue(Boolean.TRUE.equals(get(httpApi, "corsEnabled")));
    assertEquals("3.0.3", get(httpApi, "openApiVersion"));
    assertFalse(values(httpApi, "routes").isEmpty());
    EObject httpRoute = values(httpApi, "routes").get(0);
    assertEquals("POST", enumLabel(get(httpRoute, "method")));
    assertEquals("NONE", enumLabel(get(httpRoute, "authorizationType")));
    assertEquals(2000, get(httpRoute, "timeoutInMillis"));
    assertTrue(reference(httpRoute, "integration") != null);
    assertFalse(values(httpRoute, "requestModels").isEmpty());

    EObject restApi = first(resources, "RestApi", "Admin Orders API");
    assertTrue(Boolean.TRUE.equals(get(restApi, "apiKeyRequiredByDefault")));
    EObject restRoute = values(restApi, "routes").get(0);
    assertEquals("API_KEY", enumLabel(get(restRoute, "authorizationType")));
    assertTrue(Boolean.TRUE.equals(get(restRoute, "apiKeyRequired")));
    assertTrue(all.stream().anyMatch(o -> "ApiGatewayUsagePlan".equals(o.eClass().getName())));

    EObject unsupportedApi = first(resources, "AwsNativeResource", "Graph API");
    assertEquals("Custom::UnsupportedApiStyle", get(unsupportedApi, "cloudFormationType"));

    EObject table = first(resources, "DynamoDbTable", "Orders Table");
    assertTrue(Boolean.TRUE.equals(get(table, "pointInTimeRecoveryEnabled")));
    assertTrue(Boolean.TRUE.equals(get(table, "deletionProtectionEnabled")));
    assertEquals(2, values(table, "keySchema").size());
    assertEquals(1, values(table, "globalSecondaryIndexes").size());
    assertTrue(reference(table, "streamSpecification") != null);
    assertTrue(reference(table, "backupPolicy") != null);

    EObject unsupportedStore = first(resources, "AwsNativeResource", "Reporting Store");
    assertEquals("Custom::UnsupportedDataStore", get(unsupportedStore, "cloudFormationType"));

    EObject bucket = first(resources, "S3Bucket", "Order Documents");
    assertEquals("ENABLED", enumLabel(get(bucket, "versioningStatus")));
    assertTrue(reference(bucket, "lifecycle") != null);
    assertTrue(reference(bucket, "notificationConfiguration") != null);
    assertTrue(reference(bucket, "bucketPolicy") != null);

    EObject queue = first(resources, "SqsQueue", "Order Events Queue");
    assertEquals("FIFO", enumLabel(get(queue, "queueType")));
    assertTrue(get(queue, "queueName").toString().endsWith(".fifo"));
    assertEquals(20, get(queue, "receiveMessageWaitTimeSeconds"));
    assertTrue(reference(queue, "redrivePolicy") != null);

    EObject topic = first(resources, "SnsTopic", "Order Topic");
    assertTrue(Boolean.TRUE.equals(get(topic, "fifoTopic")));
    assertTrue(get(topic, "topicName").toString().endsWith(".fifo"));
    assertTrue(all.stream().anyMatch(o -> "SnsSubscription".equals(o.eClass().getName())));

    EObject bus = first(resources, "EventBridgeBus", "Order Bus");
    assertTrue(get(bus, "eventSourceName").toString().contains("orders-domain"));
    EObject rule = first(resources, "EventBridgeRule", "Route Order Events");
    assertEquals("ENABLED", get(rule, "state"));
    assertTrue(get(rule, "eventPatternJson").toString().contains("OrderCreated"));

    EObject schedule = first(resources, "EventBridgeSchedule", "Nightly Orders Job");
    assertEquals("cron(0 2 * * ? *)", get(schedule, "scheduleExpression"));
    assertEquals("Asia/Tehran", get(schedule, "scheduleExpressionTimezone"));

    EObject stateMachine = first(resources, "StepFunctionStateMachine", "Order Workflow");
    assertEquals("STANDARD", enumLabel(get(stateMachine, "stateMachineType")));
    assertEquals("live", get(stateMachine, "aliasName"));
    EObject asl = reference(stateMachine, "aslDocument");
    assertTrue(get(asl, "content").toString().contains("\"StartAt\": \"Start\""));
    assertEquals("JSONPath", get(asl, "queryLanguage"));
    assertTrue(
        values(asl, "states").stream().anyMatch(s -> "AslTaskState".equals(s.eClass().getName())));
    EObject workflowTask = first(values(asl, "states"), "AslTaskState", "Invoke Create Order");
    assertEquals(lambda, reference(workflowTask, "invokedResource"));
    assertEquals(
        "$",
        get(workflowTask, "resultPath"),
        "An explicit root output mapping must be honored for a workflow task.");
    assertTrue(get(asl, "content").toString().contains("\"Resource\": \"${"));

    EObject userPool = first(resources, "CognitoUserPool", "Customer Identity");
    assertEquals("ON", enumLabel(get(userPool, "mfaConfiguration")));
    assertTrue(Boolean.TRUE.equals(get(userPool, "deletionProtection")));

    EObject principalRole = first(resources, "IamRole", "Batch Worker Principal");
    assertTrue(
        values(principalRole, "inlinePolicies").stream()
            .anyMatch(p -> !values(reference(p, "document"), "statements").isEmpty()));

    EObject secret = first(resources, "SecretsManagerSecret", "Payments Api Key");
    assertTrue(Boolean.TRUE.equals(get(secret, "rotationRequired")));
    assertTrue(reference(secret, "rotationSchedule") != null);

    assertTrue(
        all.stream()
            .anyMatch(
                o ->
                    "CfnParameter".equals(o.eClass().getName())
                        && "MaxBatch".equals(get(o, "parameterName"))));
    assertTrue(
        all.stream()
            .anyMatch(
                o ->
                    "SsmParameter".equals(o.eClass().getName())
                        && "SECURE_STRING".equals(enumLabel(get(o, "parameterType")))));

    EObject adapter = first(resources, "AwsNativeResource", "Payment Adapter");
    assertEquals("Custom::ExternalAdapterMetadata", get(adapter, "cloudFormationType"));
    assertTrue(
        resources.stream().anyMatch(r -> "EventBridgeConnection".equals(r.eClass().getName())));
    assertTrue(
        resources.stream().anyMatch(r -> "EventBridgeApiDestination".equals(r.eClass().getName())));

    assertTrue(
        all.stream()
            .anyMatch(
                o ->
                    "StructuredDocument".equals(o.eClass().getName())
                        && get(o, "content").toString().contains("OrderCreated")));
    assertTrue(resources.stream().anyMatch(r -> "CloudWatchAlarm".equals(r.eClass().getName())));
    assertTrue(resources.stream().anyMatch(r -> "KmsKey".equals(r.eClass().getName())));
  }

  /**
   * Exercises post-processing helper logic for relationship resolution, generated permissions,
   * event-source mappings, target invoke roles, JSON schema rendering, and readiness decisions.
   *
   * @throws Exception when fixture creation, ETL execution, or model loading fails
   */
  @Test
  void resolvesPimToAwsRelationshipsAndReviewBacklogSemantics() throws Exception {
    Path pimMetamodel = REPOSITORY_ROOT.resolve("mde/metamodels/pim/pim-combined.ecore");
    Path psmMetamodel = REPOSITORY_ROOT.resolve("mde/metamodels/psm/psm-combined.ecore");
    Path pimModel = tempDir.resolve("relationship-coverage.pim.xmi");
    Path psmModel = tempDir.resolve("relationship-coverage.awspsm.xmi");

    createRelationshipCoveragePimModel(pimMetamodel, pimModel);
    executeOrFail(PimToAwsPsmDefaults.request(REPOSITORY_ROOT, pimModel, psmModel, true, true));

    EObject root = loadModel(psmMetamodel, psmModel).getContents().get(0);
    List<EObject> all = allObjects(root);
    List<EObject> resources = containedAwsResources(root);

    EObject lambda = first(resources, "AwsLambdaFunction", "Relationship Handler");
    assertTrue(
        values(lambda, "permissions").stream()
            .anyMatch(p -> "apigateway.amazonaws.com".equals(get(p, "principal"))));
    assertTrue(
        values(lambda, "permissions").stream()
            .anyMatch(p -> "sns.amazonaws.com".equals(get(p, "principal"))));
    assertTrue(
        values(lambda, "permissions").stream()
            .anyMatch(p -> "s3.amazonaws.com".equals(get(p, "principal"))));
    assertTrue(
        values(lambda, "permissions").stream()
            .anyMatch(
                p ->
                    "events.amazonaws.com".equals(get(p, "principal"))
                        || "scheduler.amazonaws.com".equals(get(p, "principal"))));
    assertTrue(
        values(lambda, "eventSourceMappings").stream()
            .anyMatch(m -> "SqsLambdaEventSourceMapping".equals(m.eClass().getName())));
    assertTrue(
        values(lambda, "eventSourceMappings").stream()
            .anyMatch(m -> "DynamoDbStreamLambdaEventSourceMapping".equals(m.eClass().getName())));

    EObject role = reference(lambda, "role");
    List<EObject> statements =
        values(role, "inlinePolicies").stream()
            .flatMap(policy -> values(reference(policy, "document"), "statements").stream())
            .toList();
    assertTrue(
        statements.stream().anyMatch(s -> valuesAsText(s, "actions").contains("dynamodb:GetItem")));
    assertTrue(
        statements.stream().anyMatch(s -> valuesAsText(s, "actions").contains("s3:PutObject")));
    assertTrue(
        statements.stream().anyMatch(s -> valuesAsText(s, "actions").contains("sns:Publish")));
    assertTrue(
        statements.stream()
            .anyMatch(s -> valuesAsText(s, "actions").contains("sqs:ReceiveMessage")));
    assertTrue(
        statements.stream()
            .anyMatch(s -> valuesAsText(s, "actions").contains("secretsmanager:GetSecretValue")));

    EObject eventRule = first(resources, "EventBridgeRule", "Relationship Flow");
    EObject target = values(eventRule, "targets").get(0);
    assertEquals("SQS", enumLabel(get(target, "targetKind")));
    assertTrue(reference(target, "role") != null);

    EObject schemaDoc =
        all.stream()
            .filter(o -> "StructuredDocument".equals(o.eClass().getName()))
            .filter(o -> "RelSchemaDocument".equals(get(o, "name")))
            .findFirst()
            .orElseThrow(
                () -> new AssertionError("Missing rendered relationship schema document."));
    String schemaJson = get(schemaDoc, "content").toString();
    assertTrue(schemaJson.contains("\"enum\""));
    assertTrue(schemaJson.contains("\"minLength\": 3"));
    assertTrue(schemaJson.contains("\"items\""));

    EObject readiness = reference(root, "readiness");
    assertDecisionRule(readiness, "LAMBDA_TIMEOUT_REDESIGN_REQUIRED");
    assertDecisionRule(readiness, "FIFO_ORDERING_KEY_REQUIRED");
    assertDecisionRule(readiness, "S3_NOTIFICATION_DESTINATION_REQUIRED");
    assertDecisionRule(readiness, "WORKFLOW_TASK_TARGET_REQUIRED");
    assertDecisionRule(readiness, "EXTERNAL_ADAPTER_CREDENTIALS_REQUIRED");
    assertDecisionRule(readiness, "ALARM_THRESHOLD_NUMERIC_REQUIRED");

    assertFalse(values(root, "relationshipViews").isEmpty());
    assertGeneratedIdsAreUuids(root);
  }

  /**
   * Covers EOL helper branches that are not direct rule happy paths: default stage/stack creation,
   * generated IAM fallback, cost policy tagging/review, advanced ASL rendering, auth review
   * branches, unresolved schedule targets, missing storage keys, invalid IAM bindings, and
   * unassigned deployable readiness.
   *
   * @throws Exception when fixture creation, ETL execution, or model loading fails
   */
  @Test
  void coversPimToAwsPsmHelperEolBranchesWithSemanticAssertions() throws Exception {
    Path pimMetamodel = REPOSITORY_ROOT.resolve("mde/metamodels/pim/pim-combined.ecore");
    Path psmMetamodel = REPOSITORY_ROOT.resolve("mde/metamodels/psm/psm-combined.ecore");
    Path pimModel = tempDir.resolve("helper-eol-coverage.pim.xmi");
    Path psmModel = tempDir.resolve("helper-eol-coverage.awspsm.xmi");

    createHelperEolCoveragePimModel(pimMetamodel, pimModel);
    executeOrFail(PimToAwsPsmDefaults.request(REPOSITORY_ROOT, pimModel, psmModel, true, true));

    EObject root = loadModel(psmMetamodel, psmModel).getContents().get(0);
    List<EObject> all = allObjects(root);
    List<EObject> resources = containedAwsResources(root);

    EObject advancedStateMachine =
        first(resources, "StepFunctionStateMachine", "Advanced Workflow");
    EObject advancedAsl = reference(advancedStateMachine, "aslDocument");
    EObject choiceState = first(values(advancedAsl, "states"), "AslChoiceState", "Choose");
    EObject evaluator = first(values(advancedAsl, "states"), "AslTaskState", "Choose Evaluator");
    assertEquals("Helper Handler", get(reference(evaluator, "invokedResource"), "name"));
    assertEquals("Choose", get(evaluator, "nextStateName"));
    EObject outcomeRule = values(choiceState, "choices").stream().findFirst().orElseThrow();
    assertEquals(
        "{% $states.input.decisionEvaluation.outcome = \"APPROVE\" %}",
        get(outcomeRule, "conditionExpression"));

    EObject defaultStage = first(all, "AwsStage", "dev");
    assertEquals("dev", get(defaultStage, "stageName"));
    EObject defaultStack = first(all, "SamStack", "Default Application Stack");
    assertTrue(
        values(defaultStack, "resources").stream()
            .anyMatch(r -> "AwsLambdaFunction".equals(r.eClass().getName())));

    EObject helperLambda = first(resources, "AwsLambdaFunction", "Helper Handler");
    EObject code = reference(helperLambda, "code");
    assertEquals("LambdaZipCodeConfig", code.eClass().getName());
    assertEquals("java21", get(code, "runtimeIdentifier"));
    assertEquals("src/helper", get(code, "codeUri"));
    assertTrue(
        values(helperLambda, "metadata").stream()
            .anyMatch(m -> "Metric:BusinessEvents".equals(get(m, "propertyName"))));
    assertTrue(
        values(helperLambda, "metadata").stream()
            .anyMatch(m -> "SLO:Availability".equals(get(m, "propertyName"))));

    EObject noAccessLambda = first(resources, "AwsLambdaFunction", "No Access Function");
    EObject noAccessRole = reference(noAccessLambda, "role");
    List<EObject> noAccessStatements =
        values(noAccessRole, "inlinePolicies").stream()
            .flatMap(policy -> values(reference(policy, "document"), "statements").stream())
            .toList();
    assertTrue(
        noAccessStatements.stream().anyMatch(s -> "DENY".equals(enumLabel(get(s, "effect")))));
    assertTrue(
        noAccessStatements.stream().anyMatch(s -> Boolean.TRUE.equals(get(s, "wildcardAction"))));

    EObject jwtApi = first(resources, "HttpApi", "JWT API");
    assertTrue(
        values(jwtApi, "authorizers").stream()
            .anyMatch(a -> "JwtAuthorizer".equals(a.eClass().getName())));
    EObject jwtRoute = values(jwtApi, "routes").get(0);
    assertEquals("JWT", enumLabel(get(jwtRoute, "authorizationType")));
    assertTrue(reference(jwtRoute, "authorizer") != null);

    EObject ambiguousApi = first(resources, "HttpApi", "Ambiguous Auth API");
    EObject ambiguousRoute = values(ambiguousApi, "routes").get(0);
    assertEquals("AWS_IAM", enumLabel(get(ambiguousRoute, "authorizationType")));

    EObject workflow = first(resources, "StepFunctionStateMachine", "Advanced Workflow");
    EObject asl = reference(workflow, "aslDocument");
    String aslJson = get(asl, "content").toString();
    assertTrue(aslJson.contains("\"Choices\""));
    assertTrue(aslJson.contains("\"Retry\""));
    assertTrue(aslJson.contains("\"Catch\""));
    assertTrue(
        values(asl, "states").stream()
            .anyMatch(s -> "AslChoiceState".equals(s.eClass().getName())));
    assertTrue(
        values(asl, "states").stream().anyMatch(s -> "AslFailState".equals(s.eClass().getName())));
    EObject rootReadiness = reference(root, "readiness");
    assertTrue(
        values(rootReadiness, "manualDecisions").stream()
            .anyMatch(
                decision ->
                    Boolean.TRUE.equals(get(decision, "blocking"))
                        && get(decision, "question") != null
                        && get(decision, "question")
                            .toString()
                            .contains("exactly one default path")),
        "Multiple Choice defaults must be a blocking review item.");
    assertTrue(
        values(rootReadiness, "manualDecisions").stream()
            .anyMatch(
                decision ->
                    Boolean.TRUE.equals(get(decision, "blocking"))
                        && get(decision, "question") != null
                        && get(decision, "question")
                            .toString()
                            .contains("Define wait duration or callback semantics")),
        "Unsupported waits must be a blocking review item rather than an immediate wait.");

    EObject missingModelTable = first(resources, "DynamoDbTable", "Missing Model Store");
    assertTrue(values(missingModelTable, "keySchema").isEmpty());
    EObject unresolvedSchedule = first(resources, "EventBridgeSchedule", "Unresolved Schedule");
    assertEquals(
        "OTHER_AWS_RESOURCE",
        enumLabel(get(reference(unresolvedSchedule, "target"), "targetKind")));

    EObject externalAdapter = first(resources, "AwsNativeResource", "No Endpoint Adapter");
    assertEquals("Custom::ExternalAdapterMetadata", get(externalAdapter, "cloudFormationType"));

    EObject principalRole = first(resources, "IamRole", "Invalid Permission Principal");
    assertTrue(
        values(principalRole, "inlinePolicies").stream()
            .flatMap(policy -> values(reference(policy, "document"), "statements").stream())
            .anyMatch(
                statement ->
                    Boolean.TRUE.equals(get(statement, "wildcardAction"))
                        && Boolean.TRUE.equals(get(statement, "wildcardResource"))));

    assertTrue(
        resources.stream().anyMatch(r -> "CloudWatchDashboard".equals(r.eClass().getName())));
    assertTrue(resources.stream().anyMatch(r -> "CloudWatchAlarm".equals(r.eClass().getName())));
    assertTrue(
        values(helperLambda, "tags").stream()
            .anyMatch(
                tag ->
                    "CostDriver".equals(get(tag, "key"))
                        && "orders-volume".equals(get(tag, "value"))));

    EObject schemaDoc = first(all, "StructuredDocument", "RecursiveSchemaDocument");
    assertTrue(get(schemaDoc, "content").toString().contains("\"$ref\""));

    EObject readiness = reference(root, "readiness");
    assertDecisionRule(readiness, "PACKAGING_UNASSIGNED");
    assertDecisionRule(readiness, "COST_ALARM_ACCOUNT_SCOPE");
    assertDecisionRule(readiness, "DYNAMO_DATA_MODEL_REQUIRED");
    assertDecisionRule(readiness, "DYNAMO_INDEX_KEY_FIELD_REQUIRED");
    assertDecisionRule(readiness, "SCHEDULE_TARGET_REQUIRED");
    assertDecisionRule(readiness, "EVENTBRIDGE_TARGET_INVOKE_ROLE_REQUIRED");
    assertDecisionRule(readiness, "EXTERNAL_ADAPTER_ENDPOINT_REQUIRED");
    assertDecisionRule(readiness, "IAM_PERMISSION_AWS_BINDING_REQUIRED");
    assertDecisionRule(readiness, "IAM_WILDCARD_REVIEW");
    assertDecisionRule(readiness, "AUTH_SCHEME_REQUIRED");
    assertDecisionRule(readiness, "API_ROUTE_AUTHORIZER_MISSING");
    assertDecisionRule(readiness, "COGNITO_FEDERATION_DETAILS_REQUIRED");
    assertDecisionRule(readiness, "SECRET_PARAMETER_VALUE_REQUIRED");
    assertDecisionRule(readiness, "WORKFLOW_PARALLEL_BRANCH_DESIGN");
    assertDecisionRule(readiness, "WORKFLOW_MAP_PROCESSOR_DESIGN");

    assertFalse(values(root, "relationshipViews").isEmpty());
  }

  private void createDirectRuleCoveragePimModel(Path metamodel, Path modelFile) throws IOException {
    Resource metamodelResource = loadMetamodel(metamodel);

    EObject requestSchema = schema(metamodelResource, "schema_order_request", "OrderRequest");
    EObject status = schemaField(metamodelResource, "field_status", "status", "STRING", true);
    add(status, "enumValues", enumLiteral(metamodelResource, "enum_pending", "PENDING"));
    add(
        requestSchema,
        "fields",
        schemaField(metamodelResource, "field_order_id", "orderId", "STRING", true));
    add(requestSchema, "fields", status);
    EObject responseSchema = schema(metamodelResource, "schema_order_response", "OrderResponse");
    add(
        responseSchema,
        "fields",
        schemaField(metamodelResource, "field_accepted", "accepted", "BOOLEAN", true));
    EObject errorSchema = schema(metamodelResource, "schema_order_error", "OrderError");
    add(
        errorSchema,
        "fields",
        schemaField(metamodelResource, "field_error", "message", "STRING", true));
    EObject eventSchema = schema(metamodelResource, "schema_order_event", "OrderEvent");
    add(
        eventSchema,
        "fields",
        schemaField(metamodelResource, "field_event_id", "eventId", "STRING", true));
    EObject eventType = create(metamodelResource, "EventType");
    base(eventType, "event_order_created", "Order Created");
    set(eventType, "semanticName", "OrderCreated");
    set(eventType, "version", "1.0.0");
    set(eventType, "sourceDomain", "Orders");
    set(eventType, "subjectExpression", "$.orderId");
    set(eventType, "orderingKey", "orderId");
    set(eventType, "schema", eventSchema);

    EObject timeout = create(metamodelResource, "TimeoutPolicy");
    base(timeout, "timeout_lambda", "Lambda Timeout");
    set(timeout, "timeoutSeconds", 1200);
    set(timeout, "clientTimeoutSeconds", 2);
    EObject concurrency = create(metamodelResource, "ConcurrencyPolicy");
    base(concurrency, "concurrency_lambda", "Lambda Concurrency");
    set(concurrency, "reservedConcurrencyHint", 7);
    EObject idempotency = create(metamodelResource, "IdempotencyPolicy");
    base(idempotency, "idempotency_lambda", "Lambda Idempotency");
    set(idempotency, "keySource", "body.orderId");
    EObject resilience = create(metamodelResource, "ResiliencePolicy");
    base(resilience, "resilience_lambda", "Lambda Resilience");
    set(resilience, "deadLetterRequired", true);
    EObject observability = create(metamodelResource, "ObservabilityConfig");
    base(observability, "obs_lambda", "Lambda Observability");
    set(observability, "alarmsEnabled", true);
    EObject alert = create(metamodelResource, "AlertPolicy");
    base(alert, "alert_errors", "Lambda Errors");
    set(alert, "metricName", "Errors");
    set(alert, "condition", ">= 1");
    set(alert, "threshold", "1");
    set(alert, "evaluationPeriods", 2);
    add(observability, "alerts", alert);
    EObject dataProtection = create(metamodelResource, "DataProtectionPolicy");
    base(dataProtection, "dp_orders", "Orders Protection");
    set(dataProtection, "maskingRequired", true);
    set(dataProtection, "residencyRequirement", "EU");
    EObject retention = create(metamodelResource, "RetentionPolicy");
    base(retention, "retention_docs", "Document Retention");
    set(retention, "retentionPeriod", "2 years");
    EObject rateLimit = create(metamodelResource, "RateLimitPolicy");
    base(rateLimit, "rate_admin", "Admin Rate");
    set(rateLimit, "requestsPerSecond", 25);
    set(rateLimit, "burstLimit", 50);
    EObject cors = create(metamodelResource, "CorsPolicy");
    base(cors, "cors_public", "Public CORS");
    addValue(cors, "allowedOrigins", "https://app.example.com");
    addValue(cors, "allowedMethods", "POST");
    addValue(cors, "allowedHeaders", "Authorization");
    set(cors, "credentialsAllowed", true);
    set(cors, "maxAgeSeconds", 600);

    EObject secret = create(metamodelResource, "Secret");
    base(secret, "secret_payments_api_key", "Payments Api Key");
    set(secret, "secretKind", enumValue(metamodelResource, "SecretKind", "API_KEY"));
    set(secret, "rotationRequired", true);
    set(secret, "rotationFrequency", "weekly");
    set(secret, "generatedReferenceOnly", true);

    EObject envVar = create(metamodelResource, "EnvironmentVariable");
    base(envVar, "env_payment_secret", "Payment Secret Env");
    set(envVar, "variableName", "PAYMENT_SECRET_ARN");
    set(envVar, "secret", secret);

    EObject function = create(metamodelResource, "Function");
    base(function, "fn_create_order", "Create Order");
    set(function, "summary", "Creates an order.");
    set(function, "handlerResponsibility", "Persist order and publish event.");
    addValue(function, "modelTags", "lambda.packageType=image");
    addValue(
        function,
        "modelTags",
        "lambda.imageUri=111111111111.dkr.ecr.eu-west-1.amazonaws.com/orders:create");
    set(function, "functionKind", enumValue(metamodelResource, "FunctionKind", "COMMAND_HANDLER"));
    set(
        function,
        "computeProfile",
        enumValue(metamodelResource, "ComputeProfile", "BATCH_ORIENTED"));
    set(function, "requiresLargeTemporaryStorage", true);
    set(function, "requiresNetworkAccess", true);
    set(function, "requiresFileSystem", true);
    set(function, "requiresIdempotency", true);
    set(function, "writesState", true);
    set(function, "timeout", timeout);
    set(function, "concurrency", concurrency);
    set(function, "idempotency", idempotency);
    set(function, "resilience", resilience);
    set(function, "observability", observability);
    add(function, "usesSecrets", secret);
    add(function, "environmentVariables", envVar);
    add(function, "publishes", eventType);
    EObject contract = create(metamodelResource, "FunctionContract");
    base(contract, "contract_create_order", "Create Order Contract");
    set(contract, "inputSchema", requestSchema);
    set(contract, "outputSchema", responseSchema);
    add(contract, "emittedEvents", eventType);
    set(function, "contract", contract);

    EObject table = dataStore(metamodelResource, "ds_orders", "Orders Table", "KEY_VALUE");
    set(table, "changeStreamRequired", true);
    set(table, "pointInTimeRecoveryRequired", true);
    add(table, "dataProtectionPolicies", dataProtection);
    EObject dataModel = create(metamodelResource, "DataModel");
    base(dataModel, "dm_order", "Order Model");
    set(dataModel, "schema", requestSchema);
    set(dataModel, "dataModelKind", enumValue(metamodelResource, "SchemaKind", "ENTITY"));
    add(
        dataModel,
        "storageFields",
        dataField(
            metamodelResource, "df_order_id", "orderId", "order_id", "STRING", true, true, false));
    add(
        dataModel,
        "storageFields",
        dataField(
            metamodelResource, "df_status", "status", "status", "STRING", false, false, true));
    add(table, "ownedDataModels", dataModel);
    EObject index = create(metamodelResource, "IndexCandidate");
    base(index, "idx_status", "Status Index");
    set(index, "partitionKeyField", "status");
    set(index, "indexPurpose", "Find by status");
    addValue(index, "projectionFields", "order_id");
    add(table, "indexCandidates", index);
    add(function, "reads", table);
    add(function, "writes", table);

    EObject unsupportedStore =
        dataStore(metamodelResource, "ds_reporting", "Reporting Store", "RELATIONAL");
    EObject bucket = create(metamodelResource, "ObjectStore");
    base(bucket, "os_order_documents", "Order Documents");
    set(bucket, "versioningRequired", true);
    set(bucket, "lifecyclePolicyRequired", true);
    set(bucket, "eventNotificationRequired", true);
    set(bucket, "retentionPolicy", retention);
    add(bucket, "emittedEvents", eventType);
    add(bucket, "dataProtectionPolicies", dataProtection);

    EObject dlq = queue(metamodelResource, "queue_order_dlq", "Order DLQ", false);
    EObject queue = queue(metamodelResource, "queue_order_events", "Order Events Queue", true);
    set(queue, "deduplicationRequired", true);
    set(queue, "longPollingRequired", true);
    set(queue, "visibilityTimeoutSeconds", 45);
    set(queue, "messageRetentionSeconds", 86400);
    set(queue, "maxReceiveAttempts", 4);
    set(queue, "partitionKeyExpression", "orderId");
    set(queue, "deadLetterChannel", dlq);
    add(queue, "eventTypes", eventType);

    EObject topic = create(metamodelResource, "Topic");
    base(topic, "topic_order", "Order Topic");
    set(topic, "channelKind", enumValue(metamodelResource, "ChannelKind", "TOPIC"));
    set(
        topic,
        "orderingRequirement",
        enumValue(metamodelResource, "OrderingRequirement", "PER_KEY"));
    add(topic, "eventTypes", eventType);
    add(topic, "consumers", function);
    EObject subscription = create(metamodelResource, "Subscription");
    base(subscription, "sub_order_lambda", "Order Lambda Subscription");
    set(subscription, "target", function);
    set(subscription, "rawDelivery", true);
    add(topic, "subscriptions", subscription);

    EObject bus = create(metamodelResource, "EventBus");
    base(bus, "bus_order", "Order Bus");
    set(bus, "channelKind", enumValue(metamodelResource, "ChannelKind", "EVENT_BUS"));
    set(bus, "orderingRequirement", enumValue(metamodelResource, "OrderingRequirement", "NONE"));
    add(bus, "eventTypes", eventType);
    EObject routingRule = create(metamodelResource, "EventRoutingRule");
    base(routingRule, "rule_order_events", "Route Order Events");
    set(routingRule, "enabled", true);
    add(routingRule, "eventTypes", eventType);
    add(routingRule, "targets", queue);
    add(bus, "routingRules", routingRule);

    EObject start = step(metamodelResource, "StartStep", "step_start", "Start", 1);
    EObject task = step(metamodelResource, "TaskStep", "step_task", "Invoke Create Order", 2);
    set(task, "invokesFunction", function);
    EObject end = step(metamodelResource, "SuccessEndStep", "step_success", "Success", 3);
    EObject workflow = create(metamodelResource, "Workflow");
    base(workflow, "wf_order", "Order Workflow");
    set(workflow, "workflowKind", enumValue(metamodelResource, "WorkflowKind", "ORCHESTRATION"));
    add(workflow, "steps", start);
    add(workflow, "steps", task);
    add(workflow, "steps", end);
    add(workflow, "transitions", transition(metamodelResource, "tr_start_task", start, task));
    add(workflow, "transitions", transition(metamodelResource, "tr_task_end", task, end));
    EObject schedule = create(metamodelResource, "Schedule");
    base(schedule, "schedule_nightly", "Nightly Orders Job");
    set(schedule, "scheduleExpression", "cron(0 2 * * ? *)");
    set(schedule, "timeZone", "Asia/Tehran");
    set(schedule, "enabled", true);
    add(schedule, "targets", workflow);

    EObject idp = create(metamodelResource, "IdentityProvider");
    base(idp, "idp_customer", "Customer Identity");
    set(idp, "identityKind", enumValue(metamodelResource, "IdentityKind", "USER_DIRECTORY"));
    set(idp, "mfaRequired", true);
    set(idp, "federationRequired", true);
    set(idp, "tokenValidationRules", "issuer=https://issuer.example.com");
    EObject auth = create(metamodelResource, "AuthPolicy");
    base(auth, "auth_admin", "Admin Auth");
    set(auth, "authScheme", "api key");
    set(auth, "authenticationRequired", true);

    EObject principal = create(metamodelResource, "Principal");
    base(principal, "principal_batch", "Batch Worker Principal");
    set(principal, "principalKind", enumValue(metamodelResource, "PrincipalKind", "SERVICE"));
    set(principal, "externalRef", "lambda.amazonaws.com");
    EObject permission = create(metamodelResource, "Permission");
    base(permission, "perm_dynamo_read", "Read Orders");
    set(permission, "action", "dynamodb:GetItem");
    set(permission, "resource", "arn:aws:dynamodb:eu-west-1:111111111111:table/orders");
    set(permission, "leastPrivilegeConfirmed", true);
    set(permission, "effect", enumValue(metamodelResource, "PermissionEffect", "ALLOW"));
    add(principal, "permissions", permission);

    EObject config = create(metamodelResource, "ConfigurationSet");
    base(config, "config_orders", "Orders Config");
    set(config, "scope", enumValue(metamodelResource, "ConfigScope", "APPLICATION"));
    EObject maxBatch =
        configParameter(metamodelResource, "param_max_batch", "Max Batch", false, "25");
    EObject secretParam =
        configParameter(
            metamodelResource,
            "param_private_token",
            "Private Token",
            true,
            "/orders/private-token:1");
    add(config, "parameters", maxBatch);
    add(config, "parameters", secretParam);
    add(config, "environmentVariables", envVar);

    EObject endpoint = create(metamodelResource, "ExternalEndpoint");
    base(endpoint, "endpoint_payment", "Payment Endpoint");
    set(endpoint, "externalSystemName", "Payment Provider");
    set(endpoint, "protocolFamily", "HTTPS");
    set(endpoint, "endpointUri", "https://payments.example.com/charge");
    set(endpoint, "credentialsRequired", true);
    set(endpoint, "rateLimitedByProvider", true);
    EObject adapter = create(metamodelResource, "ExternalAdapter");
    base(adapter, "adapter_payment", "Payment Adapter");
    set(adapter, "endpoint", endpoint);
    EObject credential = create(metamodelResource, "CredentialRequirement");
    base(credential, "cred_payment_token", "Payment Token");
    set(credential, "purpose", "client_id");
    set(credential, "externalId", "/orders/external/payment/client-id");
    set(credential, "secretKind", enumValue(metamodelResource, "SecretKind", "TOKEN"));
    add(adapter, "credentials", credential);

    EObject publicApi = create(metamodelResource, "Api");
    base(publicApi, "api_public_orders", "Public Orders API");
    set(publicApi, "publicName", "Public Orders API");
    set(publicApi, "apiStyle", enumValue(metamodelResource, "ApiStyle", "RESOURCE_ORIENTED_HTTP"));
    set(publicApi, "corsRequired", true);
    set(publicApi, "generatedOpenApiRequired", true);
    set(publicApi, "cors", cors);
    EObject publicRoute = create(metamodelResource, "ApiRoute");
    base(publicRoute, "route_create_order", "Create Order Route");
    set(publicRoute, "pathTemplate", "/orders");
    set(publicRoute, "method", enumValue(metamodelResource, "HttpMethod", "POST"));
    set(publicRoute, "publicRoute", true);
    set(publicRoute, "requestValidationRequired", false);
    set(publicRoute, "requestSchema", requestSchema);
    set(publicRoute, "responseSchema", responseSchema);
    set(publicRoute, "functionIntegration", function);
    set(publicRoute, "timeout", timeout);
    add(publicApi, "routes", publicRoute);

    EObject adminApi = create(metamodelResource, "Api");
    base(adminApi, "api_admin_orders", "Admin Orders API");
    set(adminApi, "apiStyle", enumValue(metamodelResource, "ApiStyle", "RPC_HTTP"));
    set(adminApi, "authRequired", true);
    set(adminApi, "auth", auth);
    set(adminApi, "rateLimit", rateLimit);
    EObject adminRoute = create(metamodelResource, "ApiRoute");
    base(adminRoute, "route_admin_order", "Admin Order Route");
    set(adminRoute, "pathTemplate", "/admin/orders/{id}");
    set(adminRoute, "method", enumValue(metamodelResource, "HttpMethod", "GET"));
    set(adminRoute, "authRequired", true);
    set(adminRoute, "requestValidationRequired", true);
    set(adminRoute, "responseSchema", responseSchema);
    set(adminRoute, "functionIntegration", function);
    add(adminApi, "routes", adminRoute);

    EObject graphApi = create(metamodelResource, "Api");
    base(graphApi, "api_graph", "Graph API");
    set(graphApi, "apiStyle", enumValue(metamodelResource, "ApiStyle", "GRAPHQL"));

    EObject businessRule = create(metamodelResource, "BusinessRule");
    base(businessRule, "br_order_total", "Order Total Rule");
    set(businessRule, "naturalLanguageRule", "Order total must be non-negative.");
    add(businessRule, "inputSchemas", requestSchema);
    add(businessRule, "outputSchemas", responseSchema);
    add(businessRule, "enforcedBy", function);

    EObject decisionModel = create(metamodelResource, "DecisionModel");
    base(decisionModel, "dm_fraud", "Fraud Decision");
    set(decisionModel, "hitPolicy", "FIRST");

    EObject service = create(metamodelResource, "ServerlessService");
    base(service, "svc_orders", "Orders Service");
    set(service, "ownerTeam", "Orders Team");
    set(service, "boundaryType", enumValue(metamodelResource, "BoundaryType", "CAPABILITY_BASED"));
    add(service, "functions", function);
    add(service, "apis", publicApi);
    add(service, "apis", adminApi);
    add(service, "apis", graphApi);
    add(service, "stores", table);
    add(service, "stores", unsupportedStore);
    add(service, "stores", bucket);
    add(service, "channels", dlq);
    add(service, "channels", queue);
    add(service, "channels", topic);
    add(service, "channels", bus);
    add(service, "workflows", workflow);
    add(service, "schedules", schedule);
    add(service, "adapters", adapter);

    EObject prod = environment(metamodelResource, "env_prod", "Production", "PROD", "", true);
    EObject dev = environment(metamodelResource, "env_dev", "Development", "DEV", "", false);
    EObject regionParam =
        configParameter(metamodelResource, "env_prod_region", "region", false, "eu-west-1");
    add(config, "parameters", regionParam);
    add(prod, "parameters", regionParam);
    EObject unit = create(metamodelResource, "DeploymentUnit");
    base(unit, "du_orders", "Orders Unit");
    set(unit, "unitType", enumValue(metamodelResource, "DeploymentUnitType", "APPLICATION"));
    set(unit, "independentlyDeployable", true);
    add(unit, "services", service);

    EObject profile = create(metamodelResource, "ImplementationProfile");
    base(profile, "impl_orders", "Orders Implementation");
    set(profile, "primaryLanguage", enumValue(metamodelResource, "RuntimeLanguage", "TYPESCRIPT"));
    set(profile, "packageManager", enumValue(metamodelResource, "PackageManager", "PNPM"));
    set(profile, "languageVersion", "22.x");
    set(profile, "sourceLayout", "src/functions");

    EObject root = root(metamodelResource, "pim_direct_rule_coverage", "Orders Platform");
    set(root, "domainName", "Orders Domain");
    set(root, "implementationProfile", profile);
    add(root, "services", service);
    add(root, "deploymentUnits", unit);
    add(root, "environments", prod);
    add(root, "environments", dev);
    add(root, "schemas", requestSchema);
    add(root, "schemas", responseSchema);
    add(root, "schemas", errorSchema);
    add(root, "schemas", eventSchema);
    add(root, "eventTypes", eventType);
    add(root, "businessRules", businessRule);
    add(root, "decisionModels", decisionModel);
    add(root, "policies", timeout);
    add(root, "policies", concurrency);
    add(root, "policies", idempotency);
    add(root, "policies", resilience);
    add(root, "policies", observability);
    add(root, "policies", dataProtection);
    add(root, "policies", retention);
    add(root, "policies", rateLimit);
    add(root, "policies", cors);
    add(root, "policies", auth);
    add(root, "externalEndpoints", endpoint);
    add(root, "identityProviders", idp);
    add(root, "principals", principal);
    add(root, "configurations", config);
    add(root, "secrets", secret);

    saveModel(metamodelResource, modelFile, root);
  }

  private void createRelationshipCoveragePimModel(Path metamodel, Path modelFile)
      throws IOException {
    Resource metamodelResource = loadMetamodel(metamodel);
    EObject schema = schema(metamodelResource, "schema_rel", "Rel");
    EObject kind = schemaField(metamodelResource, "field_kind", "kind", "STRING", true);
    set(kind, "minLength", 3);
    add(kind, "enumValues", enumLiteral(metamodelResource, "enum_a", "A"));
    EObject tags = schemaField(metamodelResource, "field_tags", "tags", "ARRAY", false);
    set(tags, "array", true);
    add(schema, "fields", kind);
    add(schema, "fields", tags);
    EObject eventType = create(metamodelResource, "EventType");
    base(eventType, "event_rel", "Rel Event");
    set(eventType, "semanticName", "RelEvent");
    set(eventType, "schema", schema);

    EObject timeout = create(metamodelResource, "TimeoutPolicy");
    base(timeout, "timeout_rel", "Relationship Timeout");
    set(timeout, "timeoutSeconds", 1000);
    EObject function = create(metamodelResource, "Function");
    base(function, "fn_relationship_handler", "Relationship Handler");
    set(function, "functionKind", enumValue(metamodelResource, "FunctionKind", "EVENT_HANDLER"));
    set(function, "computeProfile", enumValue(metamodelResource, "ComputeProfile", "LIGHTWEIGHT"));
    set(function, "writesState", true);
    set(function, "requiresIdempotency", true);
    set(function, "timeout", timeout);
    EObject contract = create(metamodelResource, "FunctionContract");
    base(contract, "contract_rel", "Relationship Contract");
    set(contract, "inputSchema", schema);
    set(function, "contract", contract);

    EObject table = dataStore(metamodelResource, "ds_rel", "Relationship Table", "KEY_VALUE");
    set(table, "changeStreamRequired", true);
    EObject stream = create(metamodelResource, "DataChangeStream");
    base(stream, "stream_rel", "Relationship Table Stream");
    set(stream, "enabled", true);
    set(
        stream,
        "orderingRequirement",
        enumValue(metamodelResource, "OrderingRequirement", "PER_KEY"));
    set(
        stream,
        "deliverySemantics",
        enumValue(metamodelResource, "DeliverySemantics", "AT_LEAST_ONCE"));
    add(stream, "emittedEvents", eventType);
    set(table, "changeStream", stream);
    EObject dataModel = create(metamodelResource, "DataModel");
    base(dataModel, "dm_rel", "Relationship Model");
    set(dataModel, "schema", schema);
    set(dataModel, "dataModelKind", enumValue(metamodelResource, "SchemaKind", "ENTITY"));
    add(
        dataModel,
        "storageFields",
        dataField(metamodelResource, "df_rel_id", "relId", "rel_id", "STRING", true, true, false));
    add(table, "ownedDataModels", dataModel);
    add(function, "reads", table);
    add(function, "writes", table);

    EObject secret = create(metamodelResource, "Secret");
    base(secret, "secret_rel", "Relationship Secret");
    set(secret, "secretKind", enumValue(metamodelResource, "SecretKind", "TOKEN"));
    add(function, "usesSecrets", secret);

    EObject queue = queue(metamodelResource, "queue_rel", "Relationship Queue", true);
    set(
        queue,
        "orderingRequirement",
        enumValue(metamodelResource, "OrderingRequirement", "PER_KEY"));
    add(queue, "eventTypes", eventType);
    EObject topic = create(metamodelResource, "Topic");
    base(topic, "topic_rel", "Relationship Topic");
    set(topic, "channelKind", enumValue(metamodelResource, "ChannelKind", "TOPIC"));
    set(topic, "orderingRequirement", enumValue(metamodelResource, "OrderingRequirement", "NONE"));
    add(topic, "eventTypes", eventType);
    add(topic, "consumers", function);
    add(function, "publishes", eventType);
    add(function, "subscribesTo", eventType);

    EObject bucket = create(metamodelResource, "ObjectStore");
    base(bucket, "bucket_rel", "Relationship Bucket");
    set(bucket, "eventNotificationRequired", true);
    add(function, "writes", bucket);

    EObject api = create(metamodelResource, "Api");
    base(api, "api_rel", "Relationship API");
    set(api, "apiStyle", enumValue(metamodelResource, "ApiStyle", "RESOURCE_ORIENTED_HTTP"));
    EObject route = create(metamodelResource, "ApiRoute");
    base(route, "route_rel", "Relationship Route");
    set(route, "pathTemplate", "/relationships");
    set(route, "method", enumValue(metamodelResource, "HttpMethod", "POST"));
    set(route, "publicRoute", true);
    set(route, "functionIntegration", function);
    add(api, "routes", route);

    EObject bus = create(metamodelResource, "EventBus");
    base(bus, "bus_rel", "Relationship Bus");
    set(bus, "channelKind", enumValue(metamodelResource, "ChannelKind", "EVENT_BUS"));
    set(bus, "orderingRequirement", enumValue(metamodelResource, "OrderingRequirement", "NONE"));
    add(bus, "eventTypes", eventType);
    EObject flow = create(metamodelResource, "EventFlow");
    base(flow, "flow_rel", "Relationship Flow");
    set(flow, "source", bus);
    set(flow, "target", queue);
    set(flow, "channel", bus);
    set(flow, "eventType", eventType);
    EObject msgFlow = create(metamodelResource, "MessageFlow");
    base(msgFlow, "flow_queue_rel", "Relationship Queue Flow");
    set(msgFlow, "source", queue);
    set(msgFlow, "target", function);
    set(msgFlow, "queue", queue);
    set(msgFlow, "messageSchema", schema);

    EObject schedule = create(metamodelResource, "Schedule");
    base(schedule, "schedule_rel", "Relationship Schedule");
    set(schedule, "scheduleExpression", "rate(5 minutes)");
    set(schedule, "enabled", true);
    add(schedule, "targets", function);

    EObject triggerQueue = create(metamodelResource, "Trigger");
    base(triggerQueue, "trigger_queue", "Queue Trigger");
    set(triggerQueue, "source", queue);
    set(triggerQueue, "invocationMode", enumValue(metamodelResource, "InvocationMode", "POLLED"));
    add(function, "triggers", triggerQueue);
    EObject triggerTopic = create(metamodelResource, "Trigger");
    base(triggerTopic, "trigger_topic", "Topic Trigger");
    set(triggerTopic, "source", topic);
    set(
        triggerTopic,
        "invocationMode",
        enumValue(metamodelResource, "InvocationMode", "ASYNCHRONOUS"));
    add(function, "triggers", triggerTopic);
    EObject triggerBucket = create(metamodelResource, "Trigger");
    base(triggerBucket, "trigger_bucket", "Bucket Trigger");
    set(triggerBucket, "source", bucket);
    set(
        triggerBucket,
        "invocationMode",
        enumValue(metamodelResource, "InvocationMode", "ASYNCHRONOUS"));
    add(function, "triggers", triggerBucket);
    EObject triggerTable = create(metamodelResource, "Trigger");
    base(triggerTable, "trigger_table", "Table Trigger");
    set(triggerTable, "source", stream);
    set(triggerTable, "invocationMode", enumValue(metamodelResource, "InvocationMode", "POLLED"));
    add(function, "triggers", triggerTable);
    EObject triggerSchedule = create(metamodelResource, "Trigger");
    base(triggerSchedule, "trigger_schedule", "Schedule Trigger");
    set(triggerSchedule, "source", schedule);
    set(
        triggerSchedule,
        "invocationMode",
        enumValue(metamodelResource, "InvocationMode", "SCHEDULED"));
    add(function, "triggers", triggerSchedule);

    EObject task =
        step(metamodelResource, "TaskStep", "step_missing_target", "Missing Target Task", 1);
    EObject workflow = create(metamodelResource, "Workflow");
    base(workflow, "wf_rel", "Relationship Workflow");
    set(workflow, "workflowKind", enumValue(metamodelResource, "WorkflowKind", "ORCHESTRATION"));
    add(workflow, "steps", task);

    EObject endpoint = create(metamodelResource, "ExternalEndpoint");
    base(endpoint, "endpoint_rel", "Relationship Endpoint");
    set(endpoint, "protocolFamily", "HTTPS");
    set(endpoint, "endpointUri", "https://rel.example.com");
    set(endpoint, "credentialsRequired", true);
    EObject adapter = create(metamodelResource, "ExternalAdapter");
    base(adapter, "adapter_rel", "Relationship Adapter");
    set(adapter, "endpoint", endpoint);

    EObject obs = create(metamodelResource, "ObservabilityConfig");
    base(obs, "obs_rel", "Relationship Observability");
    EObject alert = create(metamodelResource, "AlertPolicy");
    base(alert, "alert_rel", "Relationship Alert");
    set(alert, "metricName", "Latency");
    set(alert, "condition", "> threshold");
    set(alert, "threshold", "fast");
    add(obs, "alerts", alert);
    set(function, "observability", obs);

    EObject service = create(metamodelResource, "ServerlessService");
    base(service, "svc_rel", "Relationship Service");
    set(service, "boundaryType", enumValue(metamodelResource, "BoundaryType", "CAPABILITY_BASED"));
    add(service, "functions", function);
    add(service, "apis", api);
    add(service, "stores", table);
    add(service, "stores", bucket);
    add(service, "channels", queue);
    add(service, "channels", topic);
    add(service, "channels", bus);
    add(service, "schedules", schedule);
    add(service, "workflows", workflow);
    add(service, "adapters", adapter);

    EObject root = root(metamodelResource, "pim_relationship_coverage", "Relationship Platform");
    add(root, "services", service);
    add(
        root,
        "environments",
        environment(metamodelResource, "env_dev_rel", "Development", "DEV", "", false));
    add(root, "schemas", schema);
    add(root, "eventTypes", eventType);
    add(root, "flows", flow);
    add(root, "flows", msgFlow);
    add(root, "policies", timeout);
    add(root, "policies", obs);
    add(root, "externalEndpoints", endpoint);
    add(root, "secrets", secret);
    saveModel(metamodelResource, modelFile, root);
  }

  private void createHelperEolCoveragePimModel(Path metamodel, Path modelFile) throws IOException {
    Resource metamodelResource = loadMetamodel(metamodel);

    EObject recursiveSchema = schema(metamodelResource, "schema_recursive", "Recursive");
    EObject childField = schemaField(metamodelResource, "field_child", "child", "OBJECT", false);
    set(childField, "objectSchema", recursiveSchema);
    add(
        recursiveSchema,
        "fields",
        schemaField(metamodelResource, "field_recursive_id", "id", "STRING", true));
    add(recursiveSchema, "fields", childField);

    EObject eventSchema = schema(metamodelResource, "schema_helper_event", "HelperEvent");
    add(
        eventSchema,
        "fields",
        schemaField(metamodelResource, "field_helper_event_id", "eventId", "STRING", true));
    EObject eventType = create(metamodelResource, "EventType");
    base(eventType, "event_helper", "Helper Event");
    set(eventType, "semanticName", "HelperEvent");
    set(eventType, "version", "1.0.0");
    set(eventType, "sourceDomain", "Helper");
    set(eventType, "subjectExpression", "$.id");
    set(eventType, "schema", eventSchema);

    EObject retry = create(metamodelResource, "RetryPolicy");
    base(retry, "retry_helper", "Helper Retry");
    set(retry, "initialDelaySeconds", 2);
    set(retry, "maxAttempts", 4);
    set(retry, "backoffRate", 1.5d);
    set(retry, "maxDelaySeconds", 30);
    addValue(retry, "retryableErrors", "States.Timeout");

    EObject observability = create(metamodelResource, "ObservabilityConfig");
    base(observability, "obs_helper", "Helper Observability");
    set(observability, "dashboardRequired", true);
    set(observability, "alarmsEnabled", true);
    EObject metric = create(metamodelResource, "MetricPolicy");
    base(metric, "metric_business_events", "Business Events Metric");
    set(metric, "metricName", "BusinessEvents");
    set(metric, "unit", "Count");
    set(metric, "statistic", "Sum");
    add(observability, "metrics", metric);
    EObject slo = create(metamodelResource, "Slo");
    base(slo, "slo_availability", "Availability");
    set(slo, "objectiveName", "Availability");
    set(slo, "metric", "SuccessRate");
    set(slo, "target", "99.9");
    set(slo, "measurementWindow", "30d");
    add(observability, "slos", slo);
    EObject alert = create(metamodelResource, "AlertPolicy");
    base(alert, "alert_helper", "Helper Alert");
    set(alert, "metricName", "Errors");
    set(alert, "condition", ">= 5");
    set(alert, "threshold", "5");
    set(alert, "evaluationPeriods", 1);
    add(observability, "alerts", alert);

    EObject helperFunction =
        function(metamodelResource, "fn_helper", "Helper Handler", "COMMAND_HANDLER");
    set(helperFunction, "sourceNameSuggestion", "io.modriss.Helper::handleRequest");
    set(helperFunction, "observability", observability);
    EObject helperContract = create(metamodelResource, "FunctionContract");
    base(helperContract, "contract_helper", "Helper Contract");
    set(helperContract, "inputSchema", recursiveSchema);
    set(helperContract, "outputSchema", eventSchema);
    add(helperContract, "emittedEvents", eventType);
    set(helperFunction, "contract", helperContract);
    add(helperFunction, "publishes", eventType);

    EObject noAccessFunction =
        function(metamodelResource, "fn_no_access", "No Access Function", "MAINTENANCE_TASK");
    EObject noAccessContract = create(metamodelResource, "FunctionContract");
    base(noAccessContract, "contract_no_access", "No Access Contract");
    set(noAccessContract, "inputSchema", recursiveSchema);
    set(noAccessFunction, "contract", noAccessContract);

    EObject invalidPrincipal = create(metamodelResource, "Principal");
    base(invalidPrincipal, "principal_invalid", "Invalid Permission Principal");
    set(
        invalidPrincipal,
        "principalKind",
        enumValue(metamodelResource, "PrincipalKind", "SERVICE"));
    EObject invalidPermission = create(metamodelResource, "Permission");
    base(invalidPermission, "perm_invalid", "Invalid Permission");
    set(invalidPermission, "action", "read orders");
    set(invalidPermission, "resource", "orders-table");
    set(invalidPermission, "effect", enumValue(metamodelResource, "PermissionEffect", "ALLOW"));
    add(invalidPrincipal, "permissions", invalidPermission);
    EObject wildcardPermission = create(metamodelResource, "Permission");
    base(wildcardPermission, "perm_wildcard", "Wildcard Permission");
    set(wildcardPermission, "action", "*");
    set(wildcardPermission, "resource", "*");
    set(wildcardPermission, "effect", enumValue(metamodelResource, "PermissionEffect", "ALLOW"));
    add(invalidPrincipal, "permissions", wildcardPermission);

    EObject idp = create(metamodelResource, "IdentityProvider");
    base(idp, "idp_federated_missing", "Federated Missing Details");
    set(idp, "identityKind", enumValue(metamodelResource, "IdentityKind", "FEDERATED_IDENTITY"));
    set(idp, "federationRequired", true);
    set(idp, "mfaRequired", false);

    EObject jwtAuth = create(metamodelResource, "AuthPolicy");
    base(jwtAuth, "auth_jwt", "JWT Auth");
    set(jwtAuth, "authScheme", "jwt");
    set(jwtAuth, "authenticationRequired", true);
    EObject jwtApi = create(metamodelResource, "Api");
    base(jwtApi, "api_jwt", "JWT API");
    set(jwtApi, "apiStyle", enumValue(metamodelResource, "ApiStyle", "RESOURCE_ORIENTED_HTTP"));
    set(jwtApi, "authRequired", true);
    set(jwtApi, "auth", jwtAuth);
    EObject jwtRoute =
        apiRoute(metamodelResource, "route_jwt", "JWT Route", "/jwt", "GET", helperFunction);
    set(jwtRoute, "authRequired", true);
    add(jwtApi, "routes", jwtRoute);

    EObject ambiguousAuth = create(metamodelResource, "AuthPolicy");
    base(ambiguousAuth, "auth_ambiguous", "Ambiguous Auth");
    set(ambiguousAuth, "authenticationRequired", true);
    EObject ambiguousApi = create(metamodelResource, "Api");
    base(ambiguousApi, "api_ambiguous", "Ambiguous Auth API");
    set(
        ambiguousApi,
        "apiStyle",
        enumValue(metamodelResource, "ApiStyle", "RESOURCE_ORIENTED_HTTP"));
    set(ambiguousApi, "authRequired", true);
    set(ambiguousApi, "auth", ambiguousAuth);
    EObject ambiguousRoute =
        apiRoute(
            metamodelResource,
            "route_ambiguous",
            "Ambiguous Route",
            "/ambiguous",
            "GET",
            helperFunction);
    set(ambiguousRoute, "authRequired", true);
    add(ambiguousApi, "routes", ambiguousRoute);

    EObject noModelStore =
        dataStore(metamodelResource, "ds_missing_model", "Missing Model Store", "KEY_VALUE");
    EObject badIndexStore =
        dataStore(metamodelResource, "ds_bad_index", "Bad Index Store", "KEY_VALUE");
    EObject badIndexModel = create(metamodelResource, "DataModel");
    base(badIndexModel, "dm_bad_index", "Bad Index Model");
    set(badIndexModel, "schema", recursiveSchema);
    set(badIndexModel, "dataModelKind", enumValue(metamodelResource, "SchemaKind", "ENTITY"));
    add(
        badIndexModel,
        "storageFields",
        dataField(metamodelResource, "df_bad_id", "id", "id", "STRING", true, true, false));
    add(badIndexStore, "ownedDataModels", badIndexModel);
    EObject badIndex = create(metamodelResource, "IndexCandidate");
    base(badIndex, "idx_missing_field", "Missing Field Index");
    set(badIndex, "partitionKeyField", "missingField");
    set(badIndex, "indexPurpose", "Missing field lookup");
    add(badIndexStore, "indexCandidates", badIndex);

    EObject topic = create(metamodelResource, "Topic");
    base(topic, "topic_helper", "Helper Topic");
    set(topic, "channelKind", enumValue(metamodelResource, "ChannelKind", "TOPIC"));
    set(topic, "orderingRequirement", enumValue(metamodelResource, "OrderingRequirement", "NONE"));
    add(topic, "eventTypes", eventType);

    EObject start = step(metamodelResource, "StartStep", "step_adv_start", "Start", 1);
    EObject choice = step(metamodelResource, "ChoiceStep", "step_adv_choice", "Choose", 2);
    set(choice, "invokesFunction", helperFunction);
    EObject task = step(metamodelResource, "TaskStep", "step_adv_task", "Invoke Helper", 3);
    set(task, "invokesFunction", helperFunction);
    set(task, "retry", retry);
    EObject failure = step(metamodelResource, "FailureEndStep", "step_adv_failure", "Failure", 4);
    EObject success = step(metamodelResource, "SuccessEndStep", "step_adv_success", "Success", 5);
    EObject catchHandler = create(metamodelResource, "ErrorHandler");
    base(catchHandler, "catch_helper", "Catch Helper");
    set(catchHandler, "errorSelector", "States.ALL");
    set(catchHandler, "nextStep", failure);
    add(task, "catchHandlers", catchHandler);
    EObject advancedWorkflow = create(metamodelResource, "Workflow");
    base(advancedWorkflow, "wf_advanced", "Advanced Workflow");
    set(
        advancedWorkflow,
        "workflowKind",
        enumValue(metamodelResource, "WorkflowKind", "ORCHESTRATION"));
    add(advancedWorkflow, "steps", start);
    add(advancedWorkflow, "steps", choice);
    add(advancedWorkflow, "steps", task);
    add(advancedWorkflow, "steps", failure);
    add(advancedWorkflow, "steps", success);
    add(
        advancedWorkflow,
        "transitions",
        transition(metamodelResource, "tr_start_choice", start, choice));
    EObject choiceToTask = transition(metamodelResource, "tr_choice_task", choice, task);
    set(choiceToTask, "conditionExpression", "$.approved == true");
    set(choiceToTask, "decisionOutcome", "APPROVE");
    add(advancedWorkflow, "transitions", choiceToTask);
    EObject choiceToSuccess = transition(metamodelResource, "tr_choice_success", choice, success);
    set(choiceToSuccess, "defaultTransition", true);
    add(advancedWorkflow, "transitions", choiceToSuccess);
    EObject choiceToFailure = transition(metamodelResource, "tr_choice_failure", choice, failure);
    set(choiceToFailure, "defaultTransition", true);
    add(advancedWorkflow, "transitions", choiceToFailure);
    add(
        advancedWorkflow,
        "transitions",
        transition(metamodelResource, "tr_task_success", task, success));

    EObject parallel =
        step(metamodelResource, "ParallelStep", "step_parallel", "Parallel Review", 1);
    EObject parallelWorkflow = create(metamodelResource, "Workflow");
    base(parallelWorkflow, "wf_parallel", "Parallel Workflow");
    set(
        parallelWorkflow,
        "workflowKind",
        enumValue(metamodelResource, "WorkflowKind", "ORCHESTRATION"));
    add(parallelWorkflow, "steps", parallel);

    EObject mapStep = step(metamodelResource, "MapStep", "step_map", "Map Review", 1);
    EObject mapWorkflow = create(metamodelResource, "Workflow");
    base(mapWorkflow, "wf_map", "Map Workflow");
    set(mapWorkflow, "workflowKind", enumValue(metamodelResource, "WorkflowKind", "ORCHESTRATION"));
    add(mapWorkflow, "steps", mapStep);

    EObject unsupportedWait =
        step(metamodelResource, "WaitStep", "step_unsupported_wait", "Unsupported Wait", 1);
    set(unsupportedWait, "conditionExpression", "next business event");
    EObject unsupportedWaitWorkflow = create(metamodelResource, "Workflow");
    base(unsupportedWaitWorkflow, "wf_unsupported_wait", "Unsupported Wait Workflow");
    set(
        unsupportedWaitWorkflow,
        "workflowKind",
        enumValue(metamodelResource, "WorkflowKind", "ORCHESTRATION"));
    add(unsupportedWaitWorkflow, "steps", unsupportedWait);

    EObject unresolvedSchedule = create(metamodelResource, "Schedule");
    base(unresolvedSchedule, "schedule_unresolved", "Unresolved Schedule");
    set(unresolvedSchedule, "scheduleExpression", "rate(1 hour)");
    set(unresolvedSchedule, "enabled", true);

    EObject badTargetRule = create(metamodelResource, "EventRoutingRule");
    base(badTargetRule, "rule_bad_target", "Bad Target Rule");
    set(badTargetRule, "enabled", true);
    add(badTargetRule, "eventTypes", eventType);
    add(badTargetRule, "targets", noModelStore);
    EObject bus = create(metamodelResource, "EventBus");
    base(bus, "bus_helper", "Helper Bus");
    set(bus, "channelKind", enumValue(metamodelResource, "ChannelKind", "EVENT_BUS"));
    set(bus, "orderingRequirement", enumValue(metamodelResource, "OrderingRequirement", "NONE"));
    add(bus, "eventTypes", eventType);
    add(bus, "routingRules", badTargetRule);

    EObject adapter = create(metamodelResource, "ExternalAdapter");
    base(adapter, "adapter_no_endpoint", "No Endpoint Adapter");

    EObject secretParam =
        configParameter(metamodelResource, "param_missing_secret", "Missing Secret", true, "");
    EObject config = create(metamodelResource, "ConfigurationSet");
    base(config, "config_helper", "Helper Config");
    set(config, "scope", enumValue(metamodelResource, "ConfigScope", "APPLICATION"));
    add(config, "parameters", secretParam);

    EObject cost = create(metamodelResource, "CostPolicy");
    base(cost, "cost_helper", "Helper Cost");
    set(cost, "budget", "100");
    set(cost, "costDriver", "orders-volume");
    set(cost, "alarmsRequired", true);
    add(cost, "attachedTo", helperFunction);

    EObject profile = create(metamodelResource, "ImplementationProfile");
    base(profile, "impl_helper", "Helper Implementation");
    set(profile, "primaryLanguage", enumValue(metamodelResource, "RuntimeLanguage", "JAVA"));
    set(profile, "packageManager", enumValue(metamodelResource, "PackageManager", "MAVEN"));
    set(profile, "languageVersion", "21");
    set(profile, "sourceLayout", "src/helper");

    EObject service = create(metamodelResource, "ServerlessService");
    base(service, "svc_helper", "Helper Service");
    set(service, "boundaryType", enumValue(metamodelResource, "BoundaryType", "CAPABILITY_BASED"));
    add(service, "functions", helperFunction);
    add(service, "functions", noAccessFunction);
    add(service, "apis", jwtApi);
    add(service, "apis", ambiguousApi);
    add(service, "stores", noModelStore);
    add(service, "stores", badIndexStore);
    add(service, "channels", topic);
    add(service, "channels", bus);
    add(service, "workflows", advancedWorkflow);
    add(service, "workflows", parallelWorkflow);
    add(service, "workflows", mapWorkflow);
    add(service, "workflows", unsupportedWaitWorkflow);
    add(service, "schedules", unresolvedSchedule);
    add(service, "adapters", adapter);

    EObject root = root(metamodelResource, "pim_helper_eol_coverage", "Helper Platform");
    set(root, "implementationProfile", profile);
    add(root, "services", service);
    add(root, "schemas", recursiveSchema);
    add(root, "schemas", eventSchema);
    add(root, "eventTypes", eventType);
    add(root, "identityProviders", idp);
    add(root, "principals", invalidPrincipal);
    add(root, "configurations", config);
    add(root, "policies", jwtAuth);
    add(root, "policies", ambiguousAuth);
    add(root, "policies", observability);
    add(root, "policies", cost);
    saveModel(metamodelResource, modelFile, root);
  }

  private Resource loadMetamodel(Path metamodel) {
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
    return metamodelResource;
  }

  private void saveModel(Resource metamodelResource, Path modelFile, EObject root)
      throws IOException {
    ResourceSet resourceSet = metamodelResource.getResourceSet();
    Resource modelResource = resourceSet.createResource(URI.createFileURI(modelFile.toString()));
    modelResource.getContents().add(root);
    modelResource.save(null);
  }

  private EObject root(Resource metamodelResource, String id, String name) {
    EObject root = create(metamodelResource, "PIMModel");
    base(root, id, name);
    set(root, "domainName", name);
    set(
        root,
        "architectureStyle",
        enumValue(metamodelResource, "ArchitectureStyle", "EVENT_DRIVEN_SERVERLESS"));
    set(root, "defaultCorrelationIdName", "correlationId");
    return root;
  }

  private EObject environment(
      Resource metamodelResource,
      String id,
      String name,
      String environmentClass,
      String region,
      boolean productionLike) {
    EObject env = create(metamodelResource, "Environment");
    base(env, id, name);
    set(env, "nameSuffix", environmentClass.toLowerCase());
    set(
        env,
        "environmentClass",
        enumValue(metamodelResource, "EnvironmentClass", environmentClass));
    set(env, "productionLike", productionLike);
    set(env, "requiresApproval", productionLike);
    if (region != null && !region.isBlank()) {
      EObject parameter =
          configParameter(metamodelResource, id + "_region", "region", false, region);
      add(env, "parameters", parameter);
    }
    return env;
  }

  private EObject schema(Resource metamodelResource, String id, String name) {
    EObject schema = create(metamodelResource, "Schema");
    base(schema, id, name);
    set(schema, "semanticVersion", "1.0.0");
    set(schema, "additionalPropertiesAllowed", false);
    set(schema, "schemaKind", enumValue(metamodelResource, "SchemaKind", "MESSAGE"));
    return schema;
  }

  private EObject schemaField(
      Resource metamodelResource, String id, String name, String fieldType, boolean required) {
    EObject field = create(metamodelResource, "SchemaField");
    base(field, id, name);
    set(field, "fieldType", enumValue(metamodelResource, "FieldType", fieldType));
    set(field, "required", required);
    return field;
  }

  private EObject enumLiteral(Resource metamodelResource, String id, String literal) {
    EObject value = create(metamodelResource, "SchemaEnumLiteral");
    base(value, id, literal);
    set(value, "literal", literal);
    return value;
  }

  private EObject dataStore(Resource metamodelResource, String id, String name, String storeKind) {
    EObject store = create(metamodelResource, "DataStore");
    base(store, id, name);
    set(store, "persistent", true);
    set(store, "encrypted", true);
    set(store, "storeKind", enumValue(metamodelResource, "StoreKind", storeKind));
    set(store, "consistencyNeed", enumValue(metamodelResource, "ConsistencyNeed", "EVENTUAL"));
    return store;
  }

  private EObject dataField(
      Resource metamodelResource,
      String id,
      String name,
      String storageName,
      String fieldType,
      boolean identifier,
      boolean partitionKey,
      boolean sortKey) {
    EObject field = create(metamodelResource, "DataField");
    base(field, id, name);
    set(field, "storageName", storageName);
    set(field, "fieldType", enumValue(metamodelResource, "FieldType", fieldType));
    set(field, "identifier", identifier);
    set(field, "partitionKeyCandidate", partitionKey);
    set(field, "sortKeyCandidate", sortKey);
    set(field, "required", identifier);
    return field;
  }

  private EObject queue(Resource metamodelResource, String id, String name, boolean fifo) {
    EObject queue = create(metamodelResource, "Queue");
    base(queue, id, name);
    set(queue, "channelKind", enumValue(metamodelResource, "ChannelKind", "QUEUE"));
    set(
        queue,
        "orderingRequirement",
        enumValue(metamodelResource, "OrderingRequirement", fifo ? "PER_KEY" : "NONE"));
    set(
        queue,
        "deliverySemantics",
        enumValue(metamodelResource, "DeliverySemantics", "AT_LEAST_ONCE"));
    set(queue, "fifoRequired", fifo);
    return queue;
  }

  private EObject function(
      Resource metamodelResource, String id, String name, String functionKind) {
    EObject function = create(metamodelResource, "Function");
    base(function, id, name);
    set(function, "functionKind", enumValue(metamodelResource, "FunctionKind", functionKind));
    set(function, "computeProfile", enumValue(metamodelResource, "ComputeProfile", "LIGHTWEIGHT"));
    set(function, "stateless", true);
    return function;
  }

  private EObject apiRoute(
      Resource metamodelResource,
      String id,
      String name,
      String path,
      String method,
      EObject functionIntegration) {
    EObject route = create(metamodelResource, "ApiRoute");
    base(route, id, name);
    set(route, "pathTemplate", path);
    set(route, "method", enumValue(metamodelResource, "HttpMethod", method));
    set(route, "functionIntegration", functionIntegration);
    return route;
  }

  private EObject step(
      Resource metamodelResource, String className, String id, String name, int orderIndex) {
    EObject step = create(metamodelResource, className);
    base(step, id, name);
    set(step, "orderIndex", orderIndex);
    set(step, "timeoutSeconds", 30);
    set(step, "inputMapping", "$");
    set(step, "outputMapping", "$");
    return step;
  }

  private EObject transition(
      Resource metamodelResource, String id, EObject source, EObject target) {
    EObject transition = create(metamodelResource, "WorkflowTransition");
    base(transition, id, id);
    set(transition, "source", source);
    set(transition, "target", target);
    return transition;
  }

  private EObject configParameter(
      Resource metamodelResource, String id, String name, boolean secret, String defaultValue) {
    EObject parameter = create(metamodelResource, "ConfigParameter");
    base(parameter, id, name);
    set(parameter, "scope", enumValue(metamodelResource, "ConfigScope", "APPLICATION"));
    set(parameter, "valueKind", "String");
    set(parameter, "defaultValue", defaultValue);
    set(parameter, "secret", secret);
    set(parameter, "required", true);
    return parameter;
  }

  private void base(EObject object, String id, String name) {
    setIfPresent(object, "id", id);
    setIfPresent(object, "name", name);
    setIfPresent(object, "summary", name + " summary.");
    setIfPresent(object, "description", name + " description.");
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
      if (content instanceof EPackage ePackage) {
        EClassifier classifier = classifier(ePackage, name);
        if (classifier != null) {
          return classifier;
        }
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

  private void setIfPresent(EObject object, String featureName, Object value) {
    EStructuralFeature structuralFeature = object.eClass().getEStructuralFeature(featureName);
    if (structuralFeature != null) {
      object.eSet(structuralFeature, value);
    }
  }

  @SuppressWarnings("unchecked")
  private void add(EObject object, String featureName, EObject value) {
    ((List<EObject>) object.eGet(feature(object, featureName))).add(value);
  }

  @SuppressWarnings("unchecked")
  private void addValue(EObject object, String featureName, Object value) {
    ((List<Object>) object.eGet(feature(object, featureName))).add(value);
  }

  private List<EObject> allObjects(EObject root) {
    List<EObject> objects = new ArrayList<>();
    objects.add(root);
    TreeIterator<EObject> contents = root.eAllContents();
    while (contents.hasNext()) {
      objects.add(contents.next());
    }
    return objects;
  }

  private EObject first(List<EObject> objects, String className, String name) {
    return objects.stream()
        .filter(object -> className.equals(object.eClass().getName()))
        .filter(object -> name.equals(String.valueOf(get(object, "name"))))
        .findFirst()
        .orElseThrow(() -> new AssertionError("Missing " + className + " named " + name));
  }

  @SuppressWarnings("unchecked")
  private List<String> valuesAsText(EObject object, String featureName) {
    return ((List<Object>) object.eGet(feature(object, featureName)))
        .stream().map(String::valueOf).toList();
  }

  private String enumLabel(Object value) {
    return String.valueOf(value);
  }

  /**
   * Executes an ETL request and fails the test with collected diagnostics on runner failure.
   *
   * @param request ETL execution request
   * @return successful ETL report
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
   * Loads an XMI model after registering the supplied combined metamodel.
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
   * Registers all root packages contained in a metamodel resource.
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
   * Registers a package and all nested subpackages in the global EMF registry.
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
   * Resolves a structural feature and fails fast when the fixture no longer matches the metamodel.
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

  /** Asserts that the readiness backlog contains a decision with the stable rule identifier. */
  private void assertDecisionRule(EObject readiness, String ruleId) {
    assertTrue(
        values(readiness, "findings").stream()
            .anyMatch(finding -> ruleId.equals(get(finding, "ruleId"))),
        "Expected readiness decision rule " + ruleId);
    assertTrue(
        values(readiness, "manualDecisions").stream()
            .anyMatch(
                decision ->
                    get(decision, "question") != null
                        && !get(decision, "question").toString().isBlank()),
        "Expected an actionable question for readiness decision rule " + ruleId);
  }

  /**
   * Asserts that at least one object has the requested EClass name.
   *
   * @param objects objects to inspect
   * @param className expected EClass name
   * @param message assertion failure message
   */
  private void assertAny(List<EObject> objects, String className, String message) {
    assertTrue(
        objects.stream().anyMatch(object -> className.equals(object.eClass().getName())), message);
  }

  /**
   * Asserts that at least one object belongs to any of the requested EClass names.
   *
   * @param objects objects to inspect
   * @param classNames accepted EClass names
   * @param message assertion failure message
   */
  private void assertAnyOf(List<EObject> objects, List<String> classNames, String message) {
    assertTrue(
        objects.stream().anyMatch(object -> classNames.contains(object.eClass().getName())),
        message);
  }

  /**
   * Collects all contained objects that inherit from {@code AwsResource}.
   *
   * @param root AWS PSM root object
   * @return contained AWS resources
   */
  private List<EObject> containedAwsResources(EObject root) {
    List<EObject> resources = new java.util.ArrayList<>();
    TreeIterator<EObject> contents = root.eAllContents();
    while (contents.hasNext()) {
      EObject object = contents.next();
      // Resource subclasses are spread across nested PSM packages, so use the metamodel type graph.
      if (object.eClass().getEAllSuperTypes().stream()
          .anyMatch(type -> "AwsResource".equals(type.getName()))) {
        resources.add(object);
      }
    }
    return resources;
  }

  /**
   * Verifies every generated EMF object ID is a UUID.
   *
   * @param root AWS PSM root object
   */
  private void assertGeneratedIdsAreUuids(EObject root) {
    List<String> ids = new java.util.ArrayList<>();
    if (feature(root, "id") != null && get(root, "id") != null) {
      ids.add(get(root, "id").toString());
    }
    TreeIterator<EObject> contents = root.eAllContents();
    while (contents.hasNext()) {
      EObject object = contents.next();
      EStructuralFeature id = object.eClass().getEStructuralFeature("id");
      if (id != null && object.eGet(id) != null) {
        ids.add(object.eGet(id).toString());
      }
    }
    assertTrue(
        ids.stream().allMatch(this::isUuid), "Generated AWS PSM model element IDs must be UUIDs.");
  }

  private boolean isUuid(String value) {
    try {
      UUID.fromString(value);
      return true;
    } catch (IllegalArgumentException ex) {
      return false;
    }
  }
}
