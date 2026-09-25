# AWS PSM modeling: tasks

This page documents the **SPEM TaskDefinition and TaskUse** elements used by the AWS PSM modeling process. It is generated from the canonical executable definition, so the names and identifiers match the API and process interface. The explanation adds practical teaching around the normative data.

A TaskDefinition describes reusable work. A TaskUse places that definition inside an activity and binds it to process performers and work-product uses. The same responsibility may appear in another process context with different inputs, outputs, or selected steps.

## Summary

| Task                                                     | Primary role            | Inputs | Outputs | Task uses |
| -------------------------------------------------------- | ----------------------- | -----: | ------: | --------: |
| Plan deployable slice                                    | Cloud Platform Engineer |      0 |       1 |         1 |
| Create AWS PSM model root                                | Cloud Platform Engineer |      1 |       1 |         1 |
| Define stage and naming policies                         | Cloud Platform Engineer |      2 |       1 |         1 |
| Establish shared model contract and evidence conventions | Method Engineer         |      2 |       1 |         1 |
| Create SAM stack and globals                             | Cloud Platform Engineer |      2 |       1 |         1 |
| Define CFN parameters and outputs                        | Cloud Platform Engineer |      2 |       1 |         1 |
| Configure IAM roles and policies                         | Cloud Platform Engineer |      2 |       1 |         1 |
| Provision KMS, secrets, and SSM                          | Cloud Platform Engineer |      3 |       1 |         1 |
| Configure VPC and subnets                                | Cloud Platform Engineer |      4 |       1 |         1 |
| Define endpoints and security groups                     | Cloud Platform Engineer |      3 |       1 |         1 |
| Configure Cognito user pools                             | Cloud Platform Engineer |      3 |       1 |         1 |
| Configure clients, groups, and identity pools            | Cloud Platform Engineer |      3 |       1 |         1 |
| Provision DynamoDB tables                                | Cloud Platform Engineer |      4 |       1 |         1 |
| Configure S3 buckets and policies                        | Cloud Platform Engineer |      5 |       1 |         1 |
| Create SQS queues                                        | Cloud Platform Engineer |      4 |       1 |         1 |
| Configure SNS topics and subscriptions                   | Cloud Platform Engineer |      5 |       1 |         1 |
| Configure EventBridge buses and rules                    | Cloud Platform Engineer |      4 |       1 |         1 |
| Configure schedules, pipes, and connections              | Cloud Platform Engineer |      5 |       1 |         1 |
| Deploy Lambda functions                                  | Cloud Platform Engineer |      5 |       1 |         1 |
| Configure event sources and permissions                  | Cloud Platform Engineer |      4 |       1 |         1 |
| Configure APIs and routes                                | Cloud Platform Engineer |      4 |       1 |         1 |
| Configure integrations and authorizers                   | Cloud Platform Engineer |      5 |       1 |         1 |
| Deploy state machines                                    | Cloud Platform Engineer |      5 |       1 |         1 |
| Configure CloudWatch logs and metrics                    | Cloud Platform Engineer |      5 |       1 |         1 |
| Configure alarms and dashboards                          | Cloud Platform Engineer |      5 |       1 |         1 |
| Create integration relationship views                    | Cloud Platform Engineer |      8 |       1 |         1 |
| Complete trace and readiness                             | Process Reviewer        |     12 |       1 |         1 |
| Review and adapt PSM increment                           | Process Reviewer        |      6 |       1 |         1 |

## Detailed tasks

## Plan deployable slice

<small>Task definition: `task.psm.ph1.st0.t1`</small>

Plan deployable slice This task should be performed in the context of the current increment or operational item. Its result is reviewed through the stated exit criteria and validation rules, rather than accepted because an activity was marked complete.

**Accountability and collaboration**

The primary performer is **Cloud Platform Engineer**. The approved method profile may assign this responsibility to a person who holds several roles.

**Inputs**

- No formal input work product is required. The task still uses the accepted scope, decisions, and project context.

**How to perform the task**

1. Select one PIM service slice or deployment unit to materialize on AWS.
2. Record AWS account, region, stage, networking, IAM, and deployment assumptions.
3. Define the PSM EVL gate, artifact-generation readiness criteria, and review participants.

**Outputs**

- PSM Increment Plan

**Ready to start when**

- PIM transform complete, prior PSM increment selected, or greenfield PSM

**Complete when**

- Deployable-slice scope and definition of done are agreed

**Checks and evidence**

- Record the owner, status, decision, and evidence links.

**Uses in this process**

- `psm.ph1.st0.t1` in **Deployable Slice Planning**

## Create AWS PSM model root

<small>Task definition: `task.psm.ph1.st1.t1`</small>

Create AWS PSM model root This task should be performed in the context of the current increment or operational item. Its result is reviewed through the stated exit criteria and validation rules, rather than accepted because an activity was marked complete.

**Accountability and collaboration**

The primary performer is **Cloud Platform Engineer**. The approved method profile may assign this responsibility to a person who holds several roles.

**Inputs**

- PSM Increment Plan

**How to perform the task**

1. Create or verify AwsPsmModel with partition and region defaults.
2. Link to PIM source model and set lifecycle metadata.

**Outputs**

- Deployment Strategy

**Ready to start when**

- Deployable-slice scope agreed

**Complete when**

- AwsPsmModel root exists

**Checks and evidence**

- Record the owner, status, decision, and evidence links.

**Uses in this process**

- `psm.ph1.st1.t1` in **Account & Stage Strategy**

## Define stage and naming policies

<small>Task definition: `task.psm.ph1.st1.t2`</small>

Define stage and naming policies This task should be performed in the context of the current increment or operational item. Its result is reviewed through the stated exit criteria and validation rules, rather than accepted because an activity was marked complete.

**Accountability and collaboration**

The primary performer is **Cloud Platform Engineer**. The approved method profile may assign this responsibility to a person who holds several roles.

**Inputs**

- Deployment Strategy
- PSM Increment Plan

**How to perform the task**

1. Configure AwsStage elements for dev, staging, and production.
2. Define AwsNamingPolicy and AwsTaggingPolicy conventions.

**Outputs**

- Deployment Strategy

**Ready to start when**

- AWS PSM root exists

**Complete when**

- Stage strategy and naming policies configured

**Checks and evidence**

- Record the owner, status, decision, and evidence links.

**Uses in this process**

- `psm.ph1.st1.t2` in **Account & Stage Strategy**

## Establish shared model contract and evidence conventions

<small>Task definition: `task.psm.ph1.st1.t3`</small>

Establish shared model contract and evidence conventions This task should be performed in the context of the current increment or operational item. Its result is reviewed through the stated exit criteria and validation rules, rather than accepted because an activity was marked complete.

**Accountability and collaboration**

The primary performer is **Method Engineer**. The approved method profile may assign this responsibility to a person who holds several roles.

**Inputs**

- Deployment Strategy
- PSM Increment Plan

**How to perform the task**

1. Apply shared identity, annotation, traceability, expression, lifecycle, and release-provenance conventions across AWS resources.
2. Record the source PIM revision, provider mapping revision, and review-state convention for the deployable slice.
3. Keep support and generated relationship concepts in detail views and evidence records rather than treating them as independent resources.

**Outputs**

- Deployment Strategy

**Ready to start when**

- AWS PSM root exists

**Complete when**

- Shared model contract and evidence convention are recorded

**Checks and evidence**

- Record the owner, status, decision, and evidence links.

**Uses in this process**

- `psm.ph1.st1.t3` in **Account & Stage Strategy**

## Create SAM stack and globals

<small>Task definition: `task.psm.ph1.st2.t1`</small>

Create SAM stack and globals This task should be performed in the context of the current increment or operational item. Its result is reviewed through the stated exit criteria and validation rules, rather than accepted because an activity was marked complete.

**Accountability and collaboration**

The primary performer is **Cloud Platform Engineer**. The approved method profile may assign this responsibility to a person who holds several roles.

**Inputs**

- Deployment Strategy
- PSM Increment Plan

**How to perform the task**

1. Create SamStack elements per deployment unit.
2. Configure SamGlobals for shared function and API defaults.

**Outputs**

- SAM Stack Scaffold

**Ready to start when**

- Stage strategy set

**Complete when**

- Stack scaffolding ready for resources

**Checks and evidence**

- Record the owner, status, decision, and evidence links.

**Uses in this process**

- `psm.ph1.st2.t1` in **Stack Scaffolding**

## Define CFN parameters and outputs

<small>Task definition: `task.psm.ph1.st2.t2`</small>

Define CFN parameters and outputs This task should be performed in the context of the current increment or operational item. Its result is reviewed through the stated exit criteria and validation rules, rather than accepted because an activity was marked complete.

**Accountability and collaboration**

The primary performer is **Cloud Platform Engineer**. The approved method profile may assign this responsibility to a person who holds several roles.

**Inputs**

- SAM Stack Scaffold
- Deployment Strategy

**How to perform the task**

1. Define CfnParameter, CfnMapping, and CfnCondition elements.
2. Configure CfnOutput and resource lifecycle policies.

**Outputs**

- SAM Stack Scaffold

**Ready to start when**

- SAM stack created

**Complete when**

- CFN scaffolding complete per stack

**Checks and evidence**

- Record the owner, status, decision, and evidence links.

**Uses in this process**

- `psm.ph1.st2.t2` in **Stack Scaffolding**

## Configure IAM roles and policies

<small>Task definition: `task.psm.ph1.st3.t1`</small>

Configure IAM roles and policies This task should be performed in the context of the current increment or operational item. Its result is reviewed through the stated exit criteria and validation rules, rather than accepted because an activity was marked complete.

**Accountability and collaboration**

The primary performer is **Cloud Platform Engineer**. The approved method profile may assign this responsibility to a person who holds several roles.

**Inputs**

- SAM Stack Scaffold
- Deployment Strategy

**How to perform the task**

1. Establish AwsSecurityBaseline with least-privilege defaults.
2. Create IamRole elements with inline and managed policies.

**Outputs**

- Security Baseline

**Ready to start when**

- Stack scaffolding complete

**Complete when**

- IAM roles cover function and service principals

**Checks and evidence**

- Record the owner, status, decision, and evidence links.

**Uses in this process**

- `psm.ph1.st3.t1` in **Security Baseline**

## Provision KMS, secrets, and SSM

<small>Task definition: `task.psm.ph1.st3.t2`</small>

Provision KMS, secrets, and SSM This task should be performed in the context of the current increment or operational item. Its result is reviewed through the stated exit criteria and validation rules, rather than accepted because an activity was marked complete.

**Accountability and collaboration**

The primary performer is **Cloud Platform Engineer**. The approved method profile may assign this responsibility to a person who holds several roles.

**Inputs**

- Security Baseline
- SAM Stack Scaffold
- Deployment Strategy

**How to perform the task**

1. Configure KmsKey and KmsAlias for encryption at rest.
2. Create SecretsManagerSecret with rotation rules.
3. Define SsmParameter elements for non-secret configuration.

**Outputs**

- Security Baseline

**Ready to start when**

- IAM roles configured

**Complete when**

- Security baseline applied

**Checks and evidence**

- Record the owner, status, decision, and evidence links.

**Uses in this process**

- `psm.ph1.st3.t2` in **Security Baseline**

## Configure VPC and subnets

<small>Task definition: `task.psm.ph2.st1.t1`</small>

Configure VPC and subnets This task should be performed in the context of the current increment or operational item. Its result is reviewed through the stated exit criteria and validation rules, rather than accepted because an activity was marked complete.

**Accountability and collaboration**

The primary performer is **Cloud Platform Engineer**. The approved method profile may assign this responsibility to a person who holds several roles.

**Inputs**

- Security Baseline
- Deployment Strategy
- SAM Stack Scaffold
- PSM Increment Plan

**How to perform the task**

1. Create Vpc and Subnet elements when VPC-attached resources are required.
2. Configure VpcAttachmentConfig for cross-stack references.

**Outputs**

- Network & Identity

**Ready to start when**

- Security baseline applied

**Complete when**

- VPC topology defined for increment slice

**Checks and evidence**

- Record the owner, status, decision, and evidence links.

**Uses in this process**

- `psm.ph2.st1.t1` in **Networking**

## Define endpoints and security groups

<small>Task definition: `task.psm.ph2.st1.t2`</small>

Define endpoints and security groups This task should be performed in the context of the current increment or operational item. Its result is reviewed through the stated exit criteria and validation rules, rather than accepted because an activity was marked complete.

**Accountability and collaboration**

The primary performer is **Cloud Platform Engineer**. The approved method profile may assign this responsibility to a person who holds several roles.

**Inputs**

- Network & Identity
- Security Baseline
- Deployment Strategy

**How to perform the task**

1. Configure VpcEndpoint elements for AWS service access.
2. Define SecurityGroup and SecurityGroupRule for workload isolation.

**Outputs**

- Network & Identity

**Ready to start when**

- VPC topology defined

**Complete when**

- Network posture defined for workloads

**Checks and evidence**

- Record the owner, status, decision, and evidence links.

**Uses in this process**

- `psm.ph2.st1.t2` in **Networking**

## Configure Cognito user pools

<small>Task definition: `task.psm.ph2.st2.t1`</small>

Configure Cognito user pools This task should be performed in the context of the current increment or operational item. Its result is reviewed through the stated exit criteria and validation rules, rather than accepted because an activity was marked complete.

**Accountability and collaboration**

The primary performer is **Cloud Platform Engineer**. The approved method profile may assign this responsibility to a person who holds several roles.

**Inputs**

- Network & Identity
- Security Baseline
- Deployment Strategy

**How to perform the task**

1. Create CognitoUserPool elements from PIM identity providers.
2. Configure password policy, schema attributes, and recovery settings.

**Outputs**

- Network & Identity

**Ready to start when**

- Networking configured

**Complete when**

- User pools match PIM principal model

**Checks and evidence**

- Record the owner, status, decision, and evidence links.

**Uses in this process**

- `psm.ph2.st2.t1` in **Identity**

## Configure clients, groups, and identity pools

<small>Task definition: `task.psm.ph2.st2.t2`</small>

Configure clients, groups, and identity pools This task should be performed in the context of the current increment or operational item. Its result is reviewed through the stated exit criteria and validation rules, rather than accepted because an activity was marked complete.

**Accountability and collaboration**

The primary performer is **Cloud Platform Engineer**. The approved method profile may assign this responsibility to a person who holds several roles.

**Inputs**

- Network & Identity
- Security Baseline
- Deployment Strategy

**How to perform the task**

1. Define CognitoUserPoolClient with OAuth configuration.
2. Create CognitoUserPoolGroup and CognitoIdentityPool for federated access.

**Outputs**

- Network & Identity

**Ready to start when**

- User pools configured

**Complete when**

- Identity resources match PIM auth model

**Checks and evidence**

- Record the owner, status, decision, and evidence links.

**Uses in this process**

- `psm.ph2.st2.t2` in **Identity**

## Provision DynamoDB tables

<small>Task definition: `task.psm.ph3.st1.t1`</small>

Provision DynamoDB tables This task should be performed in the context of the current increment or operational item. Its result is reviewed through the stated exit criteria and validation rules, rather than accepted because an activity was marked complete.

**Accountability and collaboration**

The primary performer is **Cloud Platform Engineer**. The approved method profile may assign this responsibility to a person who holds several roles.

**Inputs**

- Network & Identity
- SAM Stack Scaffold
- Security Baseline
- Deployment Strategy

**How to perform the task**

1. Create DynamoDbTable elements from PIM DataStore definitions.
2. Configure key schema, GSIs/LSIs, streams, and TTL.

**Outputs**

- Durable Storage Layer

**Ready to start when**

- Identity configured

**Complete when**

- DynamoDB tables match PIM data models

**Checks and evidence**

- Record the owner, status, decision, and evidence links.

**Uses in this process**

- `psm.ph3.st1.t1` in **Durable Storage**

## Configure S3 buckets and policies

<small>Task definition: `task.psm.ph3.st1.t2`</small>

Configure S3 buckets and policies This task should be performed in the context of the current increment or operational item. Its result is reviewed through the stated exit criteria and validation rules, rather than accepted because an activity was marked complete.

**Accountability and collaboration**

The primary performer is **Cloud Platform Engineer**. The approved method profile may assign this responsibility to a person who holds several roles.

**Inputs**

- Durable Storage Layer
- Network & Identity
- Security Baseline
- Deployment Strategy
- SAM Stack Scaffold

**How to perform the task**

1. Create S3Bucket elements from PIM ObjectStore definitions.
2. Configure encryption, lifecycle, notifications, and bucket policies.

**Outputs**

- Durable Storage Layer

**Ready to start when**

- DynamoDB tables provisioned

**Complete when**

- S3 buckets match PIM object stores

**Checks and evidence**

- Record the owner, status, decision, and evidence links.

**Uses in this process**

- `psm.ph3.st1.t2` in **Durable Storage**

## Create SQS queues

<small>Task definition: `task.psm.ph3.st2.t1`</small>

Create SQS queues This task should be performed in the context of the current increment or operational item. Its result is reviewed through the stated exit criteria and validation rules, rather than accepted because an activity was marked complete.

**Accountability and collaboration**

The primary performer is **Cloud Platform Engineer**. The approved method profile may assign this responsibility to a person who holds several roles.

**Inputs**

- Durable Storage Layer
- Network & Identity
- Security Baseline
- Deployment Strategy

**How to perform the task**

1. Create SqsQueue elements from PIM Queue definitions.
2. Configure redrive policies and queue policies.

**Outputs**

- Messaging Layer

**Ready to start when**

- Durable storage provisioned

**Complete when**

- SQS queues match PIM integration topology

**Checks and evidence**

- Record the owner, status, decision, and evidence links.

**Uses in this process**

- `psm.ph3.st2.t1` in **Messaging**

## Configure SNS topics and subscriptions

<small>Task definition: `task.psm.ph3.st2.t2`</small>

Configure SNS topics and subscriptions This task should be performed in the context of the current increment or operational item. Its result is reviewed through the stated exit criteria and validation rules, rather than accepted because an activity was marked complete.

**Accountability and collaboration**

The primary performer is **Cloud Platform Engineer**. The approved method profile may assign this responsibility to a person who holds several roles.

**Inputs**

- Messaging Layer
- Durable Storage Layer
- Network & Identity
- Security Baseline
- Deployment Strategy

**How to perform the task**

1. Create SnsTopic elements from PIM Topic definitions.
2. Define SnsSubscription with filter rules and topic policies.

**Outputs**

- Messaging Layer

**Ready to start when**

- SQS queues created

**Complete when**

- Messaging matches PIM event channels

**Checks and evidence**

- Record the owner, status, decision, and evidence links.

**Uses in this process**

- `psm.ph3.st2.t2` in **Messaging**

## Configure EventBridge buses and rules

<small>Task definition: `task.psm.ph4.st1.t1`</small>

Configure EventBridge buses and rules This task should be performed in the context of the current increment or operational item. Its result is reviewed through the stated exit criteria and validation rules, rather than accepted because an activity was marked complete.

**Accountability and collaboration**

The primary performer is **Cloud Platform Engineer**. The approved method profile may assign this responsibility to a person who holds several roles.

**Inputs**

- Messaging Layer
- Network & Identity
- Security Baseline
- Deployment Strategy

**How to perform the task**

1. Create EventBridgeBus and EventBridgeRule elements from PIM EventBus.
2. Configure targets, input transformers, and retry policies.

**Outputs**

- Event Fabric

**Ready to start when**

- Messaging configured

**Complete when**

- EventBridge rules match PIM routing rules

**Checks and evidence**

- Record the owner, status, decision, and evidence links.

**Uses in this process**

- `psm.ph4.st1.t1` in **Event Fabric**

## Configure schedules, pipes, and connections

<small>Task definition: `task.psm.ph4.st1.t2`</small>

Configure schedules, pipes, and connections This task should be performed in the context of the current increment or operational item. Its result is reviewed through the stated exit criteria and validation rules, rather than accepted because an activity was marked complete.

**Accountability and collaboration**

The primary performer is **Cloud Platform Engineer**. The approved method profile may assign this responsibility to a person who holds several roles.

**Inputs**

- Event Fabric
- Messaging Layer
- Network & Identity
- Security Baseline
- Deployment Strategy

**How to perform the task**

1. Define EventBridgeSchedule elements from PIM Schedule triggers.
2. Configure EventBridgePipe and API destinations for external integration.

**Outputs**

- Event Fabric

**Ready to start when**

- EventBridge rules configured

**Complete when**

- Event fabric matches PIM integration

**Checks and evidence**

- Record the owner, status, decision, and evidence links.

**Uses in this process**

- `psm.ph4.st1.t2` in **Event Fabric**

## Deploy Lambda functions

<small>Task definition: `task.psm.ph4.st2.t1`</small>

Deploy Lambda functions This task should be performed in the context of the current increment or operational item. Its result is reviewed through the stated exit criteria and validation rules, rather than accepted because an activity was marked complete.

**Accountability and collaboration**

The primary performer is **Cloud Platform Engineer**. The approved method profile may assign this responsibility to a person who holds several roles.

**Inputs**

- Event Fabric
- SAM Stack Scaffold
- Security Baseline
- Network & Identity
- Deployment Strategy

**How to perform the task**

1. Create AwsLambdaFunction elements from PIM Function definitions.
2. Configure code config, layers, aliases, and environment variables.

**Outputs**

- Lambda Compute Layer

**Ready to start when**

- Event fabric configured

**Complete when**

- Lambda functions match PIM compute catalog

**Checks and evidence**

- Record the owner, status, decision, and evidence links.

**Uses in this process**

- `psm.ph4.st2.t1` in **Compute**

## Configure event sources and permissions

<small>Task definition: `task.psm.ph4.st2.t2`</small>

Configure event sources and permissions This task should be performed in the context of the current increment or operational item. Its result is reviewed through the stated exit criteria and validation rules, rather than accepted because an activity was marked complete.

**Accountability and collaboration**

The primary performer is **Cloud Platform Engineer**. The approved method profile may assign this responsibility to a person who holds several roles.

**Inputs**

- Lambda Compute Layer
- Event Fabric
- Security Baseline
- Network & Identity

**How to perform the task**

1. Configure LambdaEventSourceMapping for SQS, DynamoDB streams, and EventBridge.
2. Set LambdaPermission, dead-letter config, and tracing/logging.

**Outputs**

- Lambda Compute Layer

**Ready to start when**

- Lambda functions deployed

**Complete when**

- Compute matches PIM functions

**Checks and evidence**

- Record the owner, status, decision, and evidence links.

**Uses in this process**

- `psm.ph4.st2.t2` in **Compute**

## Configure APIs and routes

<small>Task definition: `task.psm.ph5.st1.t1`</small>

Configure APIs and routes This task should be performed in the context of the current increment or operational item. Its result is reviewed through the stated exit criteria and validation rules, rather than accepted because an activity was marked complete.

**Accountability and collaboration**

The primary performer is **Cloud Platform Engineer**. The approved method profile may assign this responsibility to a person who holds several roles.

**Inputs**

- Lambda Compute Layer
- Network & Identity
- Security Baseline
- Deployment Strategy

**How to perform the task**

1. Create HttpApi, RestApi, and WebSocketApi from PIM Api definitions.
2. Define routes, resources, and methods per API style.

**Outputs**

- API Gateway Layer

**Ready to start when**

- Compute deployed

**Complete when**

- API routes match PIM API catalog

**Checks and evidence**

- Record the owner, status, decision, and evidence links.

**Uses in this process**

- `psm.ph5.st1.t1` in **API Gateway**

## Configure integrations and authorizers

<small>Task definition: `task.psm.ph5.st1.t2`</small>

Configure integrations and authorizers This task should be performed in the context of the current increment or operational item. Its result is reviewed through the stated exit criteria and validation rules, rather than accepted because an activity was marked complete.

**Accountability and collaboration**

The primary performer is **Cloud Platform Engineer**. The approved method profile may assign this responsibility to a person who holds several roles.

**Inputs**

- API Gateway Layer
- Lambda Compute Layer
- Event Fabric
- Network & Identity
- Security Baseline

**How to perform the task**

1. Configure ApiGatewayIntegration to Lambda functions.
2. Set up authorizers, stages, and WAF associations.

**Outputs**

- API Gateway Layer

**Ready to start when**

- API routes defined

**Complete when**

- API Gateway matches PIM APIs

**Checks and evidence**

- Record the owner, status, decision, and evidence links.

**Uses in this process**

- `psm.ph5.st1.t2` in **API Gateway**

## Deploy state machines

<small>Task definition: `task.psm.ph5.st2.ss1.t1`</small>

Deploy state machines This task should be performed in the context of the current increment or operational item. Its result is reviewed through the stated exit criteria and validation rules, rather than accepted because an activity was marked complete.

**Accountability and collaboration**

The primary performer is **Cloud Platform Engineer**. The approved method profile may assign this responsibility to a person who holds several roles.

**Inputs**

- API Gateway Layer
- Lambda Compute Layer
- Event Fabric
- Network & Identity
- Security Baseline

**How to perform the task**

1. Create StepFunctionStateMachine from PIM Workflow definitions.
2. Select the concrete AslPassState, AslTaskState, AslChoiceState, AslWaitState, AslSucceedState, AslFailState, AslParallelState, or AslMapState classifier for every state.
3. Configure ASL transitions, retry/catch rules, branches, map processors, and logging.

**Outputs**

- Workflow & Observability

**Ready to start when**

- API Gateway configured

**Complete when**

- State machines match PIM workflows

**Checks and evidence**

- Record the owner, status, decision, and evidence links.

**Uses in this process**

- `psm.ph5.st2.ss1.t1` in **Step Functions**

## Configure CloudWatch logs and metrics

<small>Task definition: `task.psm.ph5.st2.ss2.t1`</small>

Configure CloudWatch logs and metrics This task should be performed in the context of the current increment or operational item. Its result is reviewed through the stated exit criteria and validation rules, rather than accepted because an activity was marked complete.

**Accountability and collaboration**

The primary performer is **Cloud Platform Engineer**. The approved method profile may assign this responsibility to a person who holds several roles.

**Inputs**

- Workflow & Observability
- Lambda Compute Layer
- API Gateway Layer
- Event Fabric
- Security Baseline

**How to perform the task**

1. Create CloudWatchLogGroup elements for functions and APIs.
2. Configure metric filters and subscription filters.

**Outputs**

- Workflow & Observability

**Ready to start when**

- State machines deployed

**Complete when**

- Logging and metrics configured

**Checks and evidence**

- Record the owner, status, decision, and evidence links.

**Uses in this process**

- `psm.ph5.st2.ss2.t1` in **CloudWatch Observability**

## Configure alarms and dashboards

<small>Task definition: `task.psm.ph5.st2.ss2.t2`</small>

Configure alarms and dashboards This task should be performed in the context of the current increment or operational item. Its result is reviewed through the stated exit criteria and validation rules, rather than accepted because an activity was marked complete.

**Accountability and collaboration**

The primary performer is **Cloud Platform Engineer**. The approved method profile may assign this responsibility to a person who holds several roles.

**Inputs**

- Workflow & Observability
- Lambda Compute Layer
- API Gateway Layer
- Event Fabric
- Network & Identity

**How to perform the task**

1. Define CloudWatchAlarm and composite alarms from PIM SLOs.
2. Create CloudWatchDashboard for increment slice.

**Outputs**

- Workflow & Observability

**Ready to start when**

- Logging and metrics configured

**Complete when**

- Workflows and observability complete

**Checks and evidence**

- Record the owner, status, decision, and evidence links.

**Uses in this process**

- `psm.ph5.st2.ss2.t2` in **CloudWatch Observability**

## Create integration relationship views

<small>Task definition: `task.psm.ph6.st1.t1`</small>

Create integration relationship views This task should be performed in the context of the current increment or operational item. Its result is reviewed through the stated exit criteria and validation rules, rather than accepted because an activity was marked complete.

**Accountability and collaboration**

The primary performer is **Cloud Platform Engineer**. The approved method profile may assign this responsibility to a person who holds several roles.

**Inputs**

- Workflow & Observability
- Deployment Strategy
- Network & Identity
- Durable Storage Layer
- Messaging Layer
- Event Fabric
- Lambda Compute Layer
- API Gateway Layer

**How to perform the task**

1. Generate AwsRelationshipView elements for Lambda integrations.
2. Validate event source, API, and notification wiring views.
3. Apply AwsTag and ResourceImport for cross-stack references.

**Outputs**

- Integration View Catalog

**Ready to start when**

- Workflow and observability complete

**Complete when**

- Integration views cover all wiring paths

**Checks and evidence**

- Record the owner, status, decision, and evidence links.

**Uses in this process**

- `psm.ph6.st1.t1` in **Integration Views**

## Complete trace and readiness

<small>Task definition: `task.psm.ph6.st2.t1`</small>

Complete trace and readiness This task should be performed in the context of the current increment or operational item. Its result is reviewed through the stated exit criteria and validation rules, rather than accepted because an activity was marked complete.

**Accountability and collaboration**

The primary performer is **Process Reviewer**. The approved method profile may assign this responsibility to a person who holds several roles.

**Inputs**

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

**How to perform the task**

1. Build TraceModel links across PIM and PSM resources.
2. Complete ProductionReadinessAssessment and resolve findings.

**Outputs**

- Deployment Readiness Record

**Ready to start when**

- Integration views complete

**Complete when**

- PSM EVL passes; readiness gate approved

**Checks and evidence**

- psm-semantic-validation

**Uses in this process**

- `psm.ph6.st2.t1` in **Trace & Readiness Gate**

## Review and adapt PSM increment

<small>Task definition: `task.psm.ph6.st3.t1`</small>

Review and adapt PSM increment This task should be performed in the context of the current increment or operational item. Its result is reviewed through the stated exit criteria and validation rules, rather than accepted because an activity was marked complete.

**Accountability and collaboration**

The primary performer is **Process Reviewer**. The approved method profile may assign this responsibility to a person who holds several roles.

**Inputs**

- Deployment Readiness Record
- PSM Increment Plan
- Deployment Strategy
- Integration View Catalog
- Security Baseline
- Workflow & Observability

**How to perform the task**

1. Review AWS resource wiring, least-privilege posture, and artifact-generation readiness.
2. Record accepted scope, deferred deployment decisions, and operational feedback.
3. Create improvement actions and backlog adjustments for the next deployable slice.

**Outputs**

- PSM Increment Review Record

**Ready to start when**

- PSM EVL passes or all blocking findings are dispositioned

**Complete when**

- Increment accepted or rework loop selected; improvement actions captured

**Checks and evidence**

- Record the owner, status, decision, and evidence links.

**Uses in this process**

- `psm.ph6.st3.t1` in **Increment Review & Adapt**

## References

These sources explain the standards and practices on which the process structure is based. They are foundations for tailoring and professional judgement, rather than substitutes for project evidence.

- [OMG Software & Systems Process Engineering Meta-Model (SPEM) 2.0](https://www.omg.org/spec/SPEM/2.0/PDF/)
- [ISO/IEC/IEEE 12207:2026, software life cycle processes](https://www.iso.org/standard/90219.html)
- [ISO/IEC/IEEE 15288:2023, system life cycle processes](https://www.iso.org/standard/81702.html)
- [SWEBOK Guide, version 4.0a](https://ieeecs-media.computer.org/media/education/swebok/swebok-v4.pdf)
- [Agile Manifesto principles](https://agilemanifesto.org/principles)
- [The Kanban Guide](https://kanbanguides.org/the-kanban-guide/)
- [FinOps Framework](https://www.finops.org/framework/)
- [AWS Well-Architected Serverless Applications Lens](https://docs.aws.amazon.com/wellarchitected/latest/serverless-applications-lens/welcome.html)
- [Brinkkemper, Method engineering](<https://doi.org/10.1016/S0950-5849(95)01059-9>)
- [Asadi, Esfahani, and Ramsin, Process patterns for MDA-based software development](https://mason.gmu.edu/~nesfaha2/Publications/SERA2010.pdf)
