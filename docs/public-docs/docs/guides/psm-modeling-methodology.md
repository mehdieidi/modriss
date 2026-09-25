# AWS PSM Modeling Process

The complete AWS PSM reference has separate pages for [activities](../process-reference/psm/activities.md), [tasks](../process-reference/psm/tasks.md), [roles](../process-reference/psm/roles.md), [work products](../process-reference/psm/work-products.md), [guidance](../process-reference/psm/guidance.md), and the [readiness gate](../process-reference/psm/gates.md).

The Platform-Specific Model (PSM) binds the PIM to AWS resources and deployment configuration. The
language covers SAM stacks, IAM, Lambda, API Gateway, DynamoDB, messaging, EventBridge, Step
Functions, and observability. Each model-driven increment invokes six ordered stages for new AWS PSM
work and for refinement after PIM-to-PSM transformation; they are not product-lifecycle phases.
Substages and task uses make up each stage; metamodel bindings show
which AWS PSM classes a task covers, while its process work products are tracked separately.

Treat resources created by PIM-to-PSM transformation as a draft deployment model. Review the AWS
mapping and refine the slice before generation. The stage order provides a working path. Return to an
earlier stage when review finds a wiring or deployment gap.

## PSM Stages

| Stage     | Name                          | In engine | Substages (summary)                                                              |
| --------- | ----------------------------- | --------- | -------------------------------------------------------------------------------- |
| `psm.ph1` | Deployment & Slice Framing    | ✓         | Deployable Slice Planning, Account & Stage, Stack Scaffolding, Security Baseline |
| `psm.ph2` | Network & Identity            | ✓         | Networking, Identity (Cognito)                                                   |
| `psm.ph3` | Storage & Messaging           | ✓         | Durable Storage, Messaging                                                       |
| `psm.ph4` | Event Fabric & Compute        | ✓         | Event Fabric, Compute (Lambda)                                                   |
| `psm.ph5` | API & Orchestration           | ✓         | API Gateway, Workflow & Observability                                            |
| `psm.ph6` | Integration Views & Readiness | ✓         | Integration Views, Trace & Readiness, Increment Review                           |

## Roles

| Role                        | Responsibility in PSM                                                        |
| --------------------------- | ---------------------------------------------------------------------------- |
| **Cloud Platform Engineer** | Engine stages: deployable-slice framing, AWS stacks, resources, integrations |
| **Process Reviewer**        | Readiness cycle: integration views, trace closure, EVL gate                  |

## Stage Flow

```mermaid
flowchart TD
  PH1["Stage 1, Deployment & Slice Framing"]
  PH2["Stage 2, Network & Identity"]
  PH3["Stage 3, Storage & Messaging"]
  PH4["Stage 4, Event Fabric & Compute"]
  PH5["Stage 5, API & Orchestration"]
  PH6["Stage 6, Integration Views & Readiness"]

  PH1 --> PH2 --> PH3 --> PH4 --> PH5 --> PH6
  PH6 -->|EVL pass| GATE["M2T artifact generation"]
```

## Detailed tasks

<!-- TASK-CATALOG:START -->
<!-- Generated from the process definition; do not edit manually -->

## Task Catalog

Process `modriss.psm.modeling` · 6 phases · 28 TaskUses / TaskDefinitions · PSM metamodel coverage enforced in CI.

### Deployment & Slice Framing (`psm.ph1`)

Frame the current deployable slice and establish or refresh AWS account, stack, and security foundations.

**Runs:** in engine cycle · **Role:** Cloud Platform Engineer

**Phase entry:**

- PIM transform complete, prior PSM increment selected, or greenfield PSM

**Phase exit:**

- AWS root and stage strategy configured
- Deployable-slice objective and deployment definition of done are agreed
- Security baseline applied

#### Deployable Slice Planning (`psm.ph1.st0`)

Select the AWS deployable slice and define deployment review expectations.

**Viewpoint:** stack

##### Tasks

#### Plan deployable slice (`psm.ph1.st0.t1`)

**Viewpoint:** stack
**Duration:** 30m
**Work products:** PSM Increment Plan

**Steps:**

1. Select one PIM service slice or deployment unit to materialize on AWS.
2. Record AWS account, region, stage, networking, IAM, and deployment assumptions.
3. Define the PSM EVL gate, artifact-generation readiness criteria, and review participants.

**Entry criteria:**

- PIM transform complete, prior PSM increment selected, or greenfield PSM

**Exit criteria:**

- Deployable-slice scope and definition of done are agreed

#### Account & Stage Strategy (`psm.ph1.st1`)

Create or refresh AwsPsmModel root with partition, region, naming, and tagging policies.

**Viewpoint:** dashboard

##### Tasks

#### Create AWS PSM model root (`psm.ph1.st1.t1`)

**Viewpoint:** dashboard
**Duration:** 20m
**Inputs:** PSM Increment Plan
**Work products:** Deployment Strategy
**Palette focus:** `AwsPsmModel`

**Steps:**

1. Create or verify AwsPsmModel with partition and region defaults.
2. Link to PIM source model and set lifecycle metadata.

**Entry criteria:**

- Deployable-slice scope agreed

**Exit criteria:**

- AwsPsmModel root exists

#### Define stage and naming policies (`psm.ph1.st1.t2`)

**Viewpoint:** dashboard
**Duration:** 30m
**Inputs:** Deployment Strategy, PSM Increment Plan
**Work products:** Deployment Strategy
**Palette focus:** `AwsStage`, `AwsNamingPolicy`, `AwsTaggingPolicy`

**Steps:**

1. Configure AwsStage elements for dev, staging, and production.
2. Define AwsNamingPolicy and AwsTaggingPolicy conventions.

**Entry criteria:**

- AWS PSM root exists

**Exit criteria:**

- Stage strategy and naming policies configured

#### Establish shared model contract and evidence conventions (`psm.ph1.st1.t3`)

**Viewpoint:** dashboard
**Duration:** 30m
**Inputs:** Deployment Strategy, PSM Increment Plan
**Work products:** Deployment Strategy
**Palette focus:**

- `WafAssociableResource`
- `AwsResource`
- `ValueExpression`
- `NamedValueExpression`
- `AwsNativeResource`
- `DeployableElement`
- `InvocationSource`
- `InvocationTarget`
- `FunctionTarget`
- `WorkflowTarget`
- `SubscriptionTarget`
- `RoutingTarget`

**Steps:**

1. Apply shared identity, annotation, traceability, expression, lifecycle, and release-provenance conventions across AWS resources.
2. Record the source PIM revision, provider mapping revision, and review-state convention for the deployable slice.
3. Keep support and generated relationship concepts in detail views and evidence records rather than treating them as independent resources.

**Entry criteria:**

- AWS PSM root exists

**Exit criteria:**

- Shared model contract and evidence convention are recorded

#### Stack Scaffolding (`psm.ph1.st2`)

Create SAM stack with globals and CloudFormation parameters.

**Viewpoint:** stack

##### Tasks

#### Create SAM stack and globals (`psm.ph1.st2.t1`)

**Viewpoint:** stack
**Duration:** 45m
**Inputs:** Deployment Strategy, PSM Increment Plan
**Work products:** SAM Stack Scaffold
**Palette focus:** `SamStack`, `SamGlobals`

**Steps:**

1. Create SamStack elements per deployment unit.
2. Configure SamGlobals for shared function and API defaults.

**Entry criteria:**

- Stage strategy set

**Exit criteria:**

- Stack scaffolding ready for resources

#### Define CFN parameters and outputs (`psm.ph1.st2.t2`)

**Viewpoint:** stack
**Duration:** 30m
**Inputs:** SAM Stack Scaffold, Deployment Strategy
**Work products:** SAM Stack Scaffold
**Palette focus:** `CfnParameter`, `CfnMapping`, `CfnCondition`, `CfnOutput`

**Steps:**

1. Define CfnParameter, CfnMapping, and CfnCondition elements.
2. Configure CfnOutput and resource lifecycle policies.

**Entry criteria:**

- SAM stack created

**Exit criteria:**

- CFN scaffolding complete per stack

#### Security Baseline (`psm.ph1.st3`)

Establish IAM roles, KMS keys, secrets, and SSM parameters.

**Viewpoint:** security

##### Tasks

#### Configure IAM roles and policies (`psm.ph1.st3.t1`)

**Viewpoint:** security
**Duration:** 1-2h
**Inputs:** SAM Stack Scaffold, Deployment Strategy
**Work products:** Security Baseline
**Palette focus:**

- `AwsSecurityBaseline`
- `IamRole`
- `IamInlinePolicy`
- `IamPolicy`
- `IamManagedPolicy`
- `IamPolicyDocument`
- `IamStatement`
- `IamPrincipal`
- `IamCondition`

**Steps:**

1. Establish AwsSecurityBaseline with least-privilege defaults.
2. Create IamRole elements with inline and managed policies.

**Entry criteria:**

- Stack scaffolding complete

**Exit criteria:**

- IAM roles cover function and service principals

#### Provision KMS, secrets, and SSM (`psm.ph1.st3.t2`)

**Viewpoint:** security
**Duration:** 1h
**Inputs:** Security Baseline, SAM Stack Scaffold, Deployment Strategy
**Work products:** Security Baseline
**Palette focus:**

- `KmsKey`
- `KmsAlias`
- `SecretsManagerSecret`
- `GenerateSecretStringConfig`
- `SecretRotationRules`
- `SecretRotationSchedule`
- `SecretsManagerResourcePolicy`
- `SsmParameter`
- `SsmParameterValueExpression`
- `SecretValueExpression`

**Steps:**

1. Configure KmsKey and KmsAlias for encryption at rest.
2. Create SecretsManagerSecret with rotation rules.
3. Define SsmParameter elements for non-secret configuration.

**Entry criteria:**

- IAM roles configured

**Exit criteria:**

- Security baseline applied

---

### Network & Identity (`psm.ph2`)

Configure VPC networking and Cognito identity resources aligned to PIM auth model.

**Runs:** in engine cycle · **Role:** Cloud Platform Engineer

**Phase entry:**

- Deployment & Slice Framing complete

**Phase exit:**

- Network posture defined for workloads
- Identity resources match PIM auth model

#### Networking (`psm.ph2.st1`)

Configure VPC, subnets, endpoints, and security groups for workloads.

**Viewpoint:** networking

##### Tasks

#### Configure VPC and subnets (`psm.ph2.st1.t1`)

**Viewpoint:** networking
**Duration:** 1h
**Inputs:** Security Baseline, Deployment Strategy, SAM Stack Scaffold, PSM Increment Plan
**Work products:** Network & Identity
**Palette focus:** `Vpc`, `Subnet`, `VpcAttachmentConfig`

**Steps:**

1. Create Vpc and Subnet elements when VPC-attached resources are required.
2. Configure VpcAttachmentConfig for cross-stack references.

**Entry criteria:**

- Security baseline applied

**Exit criteria:**

- VPC topology defined for increment slice

#### Define endpoints and security groups (`psm.ph2.st1.t2`)

**Viewpoint:** networking
**Duration:** 45m
**Inputs:** Network & Identity, Security Baseline, Deployment Strategy
**Work products:** Network & Identity
**Palette focus:** `VpcEndpoint`, `VpcEndpointReference`, `SecurityGroup`, `SecurityGroupRule`

**Steps:**

1. Configure VpcEndpoint elements for AWS service access.
2. Define SecurityGroup and SecurityGroupRule for workload isolation.

**Entry criteria:**

- VPC topology defined

**Exit criteria:**

- Network posture defined for workloads

#### Identity (`psm.ph2.st2`)

Configure Cognito user pools, clients, and identity pools.

**Viewpoint:** identity

##### Tasks

#### Configure Cognito user pools (`psm.ph2.st2.t1`)

**Viewpoint:** identity
**Duration:** 1h
**Inputs:** Network & Identity, Security Baseline, Deployment Strategy
**Work products:** Network & Identity
**Palette focus:**

- `CognitoUserPool`
- `CognitoPasswordPolicy`
- `CognitoSchemaAttribute`
- `CognitoEmailConfiguration`
- `CognitoAccountRecoverySetting`
- `CognitoRecoveryMechanism`
- `CognitoLambdaConfig`

**Steps:**

1. Create CognitoUserPool elements from PIM identity providers.
2. Configure password policy, schema attributes, and recovery settings.

**Entry criteria:**

- Networking configured

**Exit criteria:**

- User pools match PIM principal model

#### Configure clients, groups, and identity pools (`psm.ph2.st2.t2`)

**Viewpoint:** identity
**Duration:** 45m
**Inputs:** Network & Identity, Security Baseline, Deployment Strategy
**Work products:** Network & Identity
**Palette focus:**

- `CognitoUserPoolClient`
- `CognitoOAuthConfiguration`
- `CognitoUserPoolGroup`
- `CognitoUserPoolDomain`
- `CognitoIdentityPool`

**Steps:**

1. Define CognitoUserPoolClient with OAuth configuration.
2. Create CognitoUserPoolGroup and CognitoIdentityPool for federated access.

**Entry criteria:**

- User pools configured

**Exit criteria:**

- Identity resources match PIM auth model

---

### Storage & Messaging (`psm.ph3`)

Provision durable storage and messaging resources matching PIM data and event channels.

**Runs:** in engine cycle · **Role:** Cloud Platform Engineer

**Phase entry:**

- Network & Identity complete

**Phase exit:**

- Durable stores match PIM data model
- Messaging matches PIM event channels

#### Durable Storage (`psm.ph3.st1`)

Provision DynamoDB tables and S3 buckets aligned to PIM data architecture.

**Viewpoint:** storage

##### Tasks

#### Provision DynamoDB tables (`psm.ph3.st1.t1`)

**Viewpoint:** storage
**Duration:** 1-2h
**Inputs:** Network & Identity, SAM Stack Scaffold, Security Baseline, Deployment Strategy
**Work products:** Durable Storage Layer
**Palette focus:**

- `DynamoDbTable`
- `DynamoDbAttributeDefinition`
- `DynamoDbKeySchemaElement`
- `DynamoDbProjection`
- `DynamoDbProvisionedThroughput`
- `DynamoDbOnDemandThroughput`
- `DynamoDbLocalSecondaryIndex`
- `DynamoDbGlobalSecondaryIndex`
- `DynamoDbReplicaSpecification`
- `DynamoDbStreamSpecification`
- `DynamoDbTimeToLiveSpecification`
- `DynamoDbSseSpecification`

**Steps:**

1. Create DynamoDbTable elements from PIM DataStore definitions.
2. Configure key schema, GSIs/LSIs, streams, and TTL.

**Entry criteria:**

- Identity configured

**Exit criteria:**

- DynamoDB tables match PIM data models

#### Configure S3 buckets and policies (`psm.ph3.st1.t2`)

**Viewpoint:** storage
**Duration:** 1-2h
**Inputs:**

- Durable Storage Layer
- Network & Identity
- Security Baseline
- Deployment Strategy
- SAM Stack Scaffold
  **Work products:** Durable Storage Layer
  **Palette focus:**

- `S3Bucket`
- `S3BucketEncryption`
- `S3OwnershipControls`
- `S3OwnershipRule`
- `S3LifecycleConfiguration`
- `S3LifecycleRule`
- `S3LifecycleFilter`
- `S3TagFilter`
- `S3Transition`
- `S3PublicAccessBlockConfiguration`
- `S3NotificationConfiguration`
- `S3NotificationRule`

**Steps:**

1. Create S3Bucket elements from PIM ObjectStore definitions.
2. Configure encryption, lifecycle, notifications, and bucket policies.

**Entry criteria:**

- DynamoDB tables provisioned

**Exit criteria:**

- S3 buckets match PIM object stores

#### Messaging (`psm.ph3.st2`)

Create SQS queues and SNS topics aligned to PIM event channels.

**Viewpoint:** messaging

##### Tasks

#### Create SQS queues (`psm.ph3.st2.t1`)

**Viewpoint:** messaging
**Duration:** 45m
**Inputs:** Durable Storage Layer, Network & Identity, Security Baseline, Deployment Strategy
**Work products:** Messaging Layer
**Palette focus:** `SqsQueue`, `SqsRedrivePolicy`, `SqsRedriveAllowPolicy`, `SqsQueuePolicy`

**Steps:**

1. Create SqsQueue elements from PIM Queue definitions.
2. Configure redrive policies and queue policies.

**Entry criteria:**

- Durable storage provisioned

**Exit criteria:**

- SQS queues match PIM integration topology

#### Configure SNS topics and subscriptions (`psm.ph3.st2.t2`)

**Viewpoint:** messaging
**Duration:** 45m
**Inputs:** Messaging Layer, Durable Storage Layer, Network & Identity, Security Baseline, Deployment Strategy
**Work products:** Messaging Layer
**Palette focus:** `SnsTopic`, `SnsSubscription`, `SnsFilterRule`, `SnsTopicPolicy`

**Steps:**

1. Create SnsTopic elements from PIM Topic definitions.
2. Define SnsSubscription with filter rules and topic policies.

**Entry criteria:**

- SQS queues created

**Exit criteria:**

- Messaging matches PIM event channels

---

### Event Fabric & Compute (`psm.ph4`)

Configure EventBridge fabric and deploy Lambda compute matching PIM functions.

**Runs:** in engine cycle · **Role:** Cloud Platform Engineer

**Phase entry:**

- Storage & Messaging complete for slice

**Phase exit:**

- Event fabric matches PIM integration
- Compute matches PIM functions

#### Event Fabric (`psm.ph4.st1`)

Configure EventBridge buses, rules, schedules, pipes, and API destinations.

**Viewpoint:** events

##### Tasks

#### Configure EventBridge buses and rules (`psm.ph4.st1.t1`)

**Viewpoint:** events
**Duration:** 1-2h
**Inputs:** Messaging Layer, Network & Identity, Security Baseline, Deployment Strategy
**Work products:** Event Fabric
**Palette focus:**

- `EventBridgeBus`
- `EventBridgeBusPolicy`
- `EventBridgeRule`
- `EventPattern`
- `EventBridgeTarget`
- `EventBridgeTargetParameters`
- `EventBridgeSqsTargetParameters`
- `EventBridgeHttpTargetParameters`
- `HttpParameter`
- `EventBridgeBatchTargetParameters`
- `EventBridgeInputTransformer`
- `EventBridgeArchive`

**Steps:**

1. Create EventBridgeBus and EventBridgeRule elements from PIM EventBus.
2. Configure targets, input transformers, and retry policies.

**Entry criteria:**

- Messaging configured

**Exit criteria:**

- EventBridge rules match PIM routing rules

#### Configure schedules, pipes, and connections (`psm.ph4.st1.t2`)

**Viewpoint:** events
**Duration:** 1h
**Inputs:** Event Fabric, Messaging Layer, Network & Identity, Security Baseline, Deployment Strategy
**Work products:** Event Fabric
**Palette focus:**

- `EventBridgeSchedule`
- `EventBridgeFlexibleTimeWindow`
- `EventBridgePipe`
- `EventBridgeAuthParameters`
- `EventBridgeApiKeyAuthParameters`
- `EventBridgeBasicAuthParameters`
- `EventBridgeOAuthParameters`
- `EventBridgeHttpParameters`
- `EventBridgeConnection`
- `EventBridgeApiDestination`

**Steps:**

1. Define EventBridgeSchedule elements from PIM Schedule triggers.
2. Configure EventBridgePipe and API destinations for external integration.

**Entry criteria:**

- EventBridge rules configured

**Exit criteria:**

- Event fabric matches PIM integration

#### Compute (`psm.ph4.st2`)

Deploy Lambda functions with event source mappings and permissions.

**Viewpoint:** compute

##### Tasks

#### Deploy Lambda functions (`psm.ph4.st2.t1`)

**Viewpoint:** compute
**Duration:** 2-3h
**Inputs:** Event Fabric, SAM Stack Scaffold, Security Baseline, Network & Identity, Deployment Strategy
**Work products:** Lambda Compute Layer
**Palette focus:**

- `AwsLambdaFunction`
- `LambdaCodeConfig`
- `LambdaZipCodeConfig`
- `LambdaImageCodeConfig`
- `LambdaImageConfig`
- `LambdaEnvironmentVariable`
- `LambdaInvocationBinding`
- `SamFunctionEvent`
- `LambdaLayerVersion`
- `LambdaLayerPermission`
- `LambdaVersion`
- `LambdaAlias`

**Steps:**

1. Create AwsLambdaFunction elements from PIM Function definitions.
2. Configure code config, layers, aliases, and environment variables.

**Entry criteria:**

- Event fabric configured

**Exit criteria:**

- Lambda functions match PIM compute catalog

#### Configure event sources and permissions (`psm.ph4.st2.t2`)

**Viewpoint:** compute
**Duration:** 1-2h
**Inputs:** Lambda Compute Layer, Event Fabric, Security Baseline, Network & Identity
**Work products:** Lambda Compute Layer
**Palette focus:**

- `LambdaEventSourceMapping`
- `SqsLambdaEventSourceMapping`
- `DynamoDbStreamLambdaEventSourceMapping`
- `GenericLambdaEventSourceMapping`
- `LambdaDeadLetterConfig`
- `LambdaEventInvokeConfig`
- `LambdaDestinationConfig`
- `LambdaTracingConfig`
- `LambdaLoggingConfig`
- `LambdaPermission`
- `LambdaFunctionUrl`
- `LambdaUrlCorsConfiguration`

**Steps:**

1. Configure LambdaEventSourceMapping for SQS, DynamoDB streams, and EventBridge.
2. Set LambdaPermission, dead-letter config, and tracing/logging.

**Entry criteria:**

- Lambda functions deployed

**Exit criteria:**

- Compute matches PIM functions

---

### API & Orchestration (`psm.ph5`)

Configure API Gateway exposure and Step Functions workflows with observability.

**Runs:** in engine cycle · **Role:** Cloud Platform Engineer

**Phase entry:**

- Event Fabric & Compute complete for slice

**Phase exit:**

- API Gateway matches PIM APIs
- Workflows and observability complete

#### API Gateway (`psm.ph5.st1`)

Configure HTTP/REST/WebSocket APIs with routes, integrations, and authorizers.

**Viewpoint:** api

##### Tasks

#### Configure APIs and routes (`psm.ph5.st1.t1`)

**Viewpoint:** api
**Duration:** 1-2h
**Inputs:** Lambda Compute Layer, Network & Identity, Security Baseline, Deployment Strategy
**Work products:** API Gateway Layer
**Palette focus:**

- `ApiGatewayApi`
- `HttpApi`
- `RestApi`
- `WebSocketApi`
- `ApiGatewayRoute`
- `HttpApiRoute`
- `RestApiRoute`
- `RestApiResource`
- `RestApiMethod`
- `WebSocketRoute`

**Steps:**

1. Create HttpApi, RestApi, and WebSocketApi from PIM Api definitions.
2. Define routes, resources, and methods per API style.

**Entry criteria:**

- Compute deployed

**Exit criteria:**

- API routes match PIM API catalog

#### Configure integrations and authorizers (`psm.ph5.st1.t2`)

**Viewpoint:** api
**Duration:** 1-2h
**Inputs:** API Gateway Layer, Lambda Compute Layer, Event Fabric, Network & Identity, Security Baseline
**Work products:** API Gateway Layer
**Palette focus:**

- `ApiGatewayRequestModel`
- `ApiGatewayResponseModel`
- `ApiGatewayRequestValidator`
- `ApiGatewayRouteSetting`
- `ApiGatewayAccessLogSetting`
- `ApiGatewayIntegration`
- `ApiGatewayIntegrationRequestTemplate`
- `ApiGatewayIntegrationResponseParameter`
- `ApiGatewayStage`
- `HttpApiStage`
- `RestApiStage`
- `WebSocketStage`

**Steps:**

1. Configure ApiGatewayIntegration to Lambda functions.
2. Set up authorizers, stages, and WAF associations.

**Entry criteria:**

- API routes defined

**Exit criteria:**

- API Gateway matches PIM APIs

#### Workflow & Observability (`psm.ph5.st2`)

Deploy Step Functions state machines and CloudWatch observability stack.

##### Step Functions (`psm.ph5.st2.ss1`)

Deploy state machines with ASL from PIM workflows.

**Viewpoint:** workflow

##### Tasks

#### Deploy state machines (`psm.ph5.st2.ss1.t1`)

**Viewpoint:** workflow
**Duration:** 1-2h
**Inputs:** API Gateway Layer, Lambda Compute Layer, Event Fabric, Network & Identity, Security Baseline
**Work products:** Workflow & Observability
**Palette focus:**

- `StepFunctionStateMachine`
- `AslDocument`
- `AslState`
- `AslPassState`
- `AslTaskState`
- `AslChoiceState`
- `AslWaitState`
- `AslSucceedState`
- `AslFailState`
- `AslParallelState`
- `AslMapState`
- `AslBranch`

**Steps:**

1. Create StepFunctionStateMachine from PIM Workflow definitions.
2. Select the concrete AslPassState, AslTaskState, AslChoiceState, AslWaitState, AslSucceedState, AslFailState, AslParallelState, or AslMapState classifier for every state.
3. Configure ASL transitions, retry/catch rules, branches, map processors, and logging.

**Entry criteria:**

- API Gateway configured

**Exit criteria:**

- State machines match PIM workflows

##### CloudWatch Observability (`psm.ph5.st2.ss2`)

Configure logs, metrics, alarms, and dashboards.

**Viewpoint:** workflow

##### Tasks

#### Configure CloudWatch logs and metrics (`psm.ph5.st2.ss2.t1`)

**Viewpoint:** workflow
**Duration:** 45m
**Inputs:** Workflow & Observability, Lambda Compute Layer, API Gateway Layer, Event Fabric, Security Baseline
**Work products:** Workflow & Observability
**Palette focus:**

- `CloudWatchLogGroup`
- `CloudWatchMetricFilter`
- `CloudWatchMetricTransformation`
- `CloudWatchLogSubscriptionFilter`
- `MetricDimension`

**Steps:**

1. Create CloudWatchLogGroup elements for functions and APIs.
2. Configure metric filters and subscription filters.

**Entry criteria:**

- State machines deployed

**Exit criteria:**

- Logging and metrics configured

#### Configure alarms and dashboards (`psm.ph5.st2.ss2.t2`)

**Viewpoint:** workflow
**Duration:** 45m
**Inputs:**

- Workflow & Observability
- Lambda Compute Layer
- API Gateway Layer
- Event Fabric
- Network & Identity
  **Work products:** Workflow & Observability
  **Palette focus:**

- `CloudWatchAlarm`
- `CloudWatchCompositeAlarm`
- `CloudWatchDashboard`
- `TracingConfig`
- `CorsConfiguration`

**Steps:**

1. Define CloudWatchAlarm and composite alarms from PIM SLOs.
2. Create CloudWatchDashboard for increment slice.

**Entry criteria:**

- Logging and metrics configured

**Exit criteria:**

- Workflows and observability complete

---

### Integration Views & Readiness (`psm.ph6`)

Create integration relationship views, close traceability, and pass PSM EVL gate.

**Runs:** in engine cycle · **Role:** Process Reviewer

**Phase entry:**

- API & Orchestration complete for slice

**Phase exit:**

- PSM EVL passes
- Readiness gate approved
- PSM increment reviewed and improvement actions captured

#### Integration Views (`psm.ph6.st1`)

Create cross-resource relationship views for deployment wiring validation.

**Viewpoint:** readiness

##### Tasks

#### Create integration relationship views (`psm.ph6.st1.t1`)

**Viewpoint:** readiness
**Duration:** 1h
**Inputs:**

- Workflow & Observability
- Deployment Strategy
- Network & Identity
- Durable Storage Layer
- Messaging Layer
- Event Fabric
- Lambda Compute Layer
- API Gateway Layer
  **Work products:** Integration View Catalog
  **Palette focus:**

- `AwsRelationshipView`
- `ApiGatewayLambdaIntegrationView`
- `EventBridgeLambdaTargetView`
- `SnsLambdaSubscriptionView`
- `SqsLambdaEventSourceView`
- `StepFunctionEventBridgeTargetView`
- `S3LambdaNotificationView`
- `S3QueueNotificationView`
- `S3TopicNotificationView`
- `AwsTag`
- `ResourceImport`
- `NativeProperty`

**Steps:**

1. Generate AwsRelationshipView elements for Lambda integrations.
2. Validate event source, API, and notification wiring views.
3. Apply AwsTag and ResourceImport for cross-stack references.

**Entry criteria:**

- Workflow and observability complete

**Exit criteria:**

- Integration views cover all wiring paths

#### Trace & Readiness Gate (`psm.ph6.st2`)

Close trace links and production readiness before M2T generation.

**Viewpoint:** readiness

##### Tasks

#### Complete trace and readiness (`psm.ph6.st2.t1`)

**Viewpoint:** readiness
**Duration:** 1-2h
**Inputs:**

- Integration View Catalog
- PSM Increment Plan
- Deployment Strategy
- SAM Stack Scaffold
- Security Baseline
- Network & Identity
- Durable Storage Layer
- Messaging Layer
- Event Fabric
- Lambda Compute Layer
- API Gateway Layer
- Workflow & Observability
  **Work products:** Deployment Readiness Record
  **Palette focus:**

- `TraceModel`
- `TraceLink`
- `TransformationAssumption`
- `ProductionReadinessAssessment`
- `ReadinessFinding`
- `ReadinessCheck`
- `ManualDecision`

**Steps:**

1. Build TraceModel links across PIM and PSM resources.
2. Complete ProductionReadinessAssessment and resolve findings.

**Entry criteria:**

- Integration views complete

**Exit criteria:**

- PSM EVL passes; readiness gate approved

**Validation:**

- `psm-semantic-validation`

#### Increment Review & Adapt (`psm.ph6.st3`)

Review the AWS deployment slice, accept the increment, and adapt the next cycle.

**Viewpoint:** readiness

##### Tasks

#### Review and adapt PSM increment (`psm.ph6.st3.t1`)

**Viewpoint:** readiness
**Duration:** 45m
**Inputs:**

- Deployment Readiness Record
- PSM Increment Plan
- Deployment Strategy
- Integration View Catalog
- Security Baseline
- Workflow & Observability
  **Work products:** PSM Increment Review Record

**Steps:**

1. Review AWS resource wiring, least-privilege posture, and artifact-generation readiness.
2. Record accepted scope, deferred deployment decisions, and operational feedback.
3. Create improvement actions and backlog adjustments for the next deployable slice.

**Entry criteria:**

- PSM EVL passes or all blocking findings are dispositioned

**Exit criteria:**

- Increment accepted or rework loop selected; improvement actions captured

---

## Process transition

The AWS PSM process produces a reviewed deployment model for artifact generation. Generate the AWS project and continue with artifact-readiness review.

<!-- TASK-CATALOG:END -->
