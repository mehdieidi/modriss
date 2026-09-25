# AWS PSM modeling: work products

This page documents the **SPEM WorkProductDefinition, WorkProductUse, and ProcessParameter** elements used by the AWS PSM modeling process. It is generated from the canonical executable definition, so the names and identifiers match the API and process interface. The explanation adds practical teaching around the normative data.

A WorkProductDefinition describes maintained information or a tangible result. WorkProductUse binds that definition to an activity or task as an input or output. ProcessParameter makes the direction explicit. A work product can be stored in several physical files or systems if its identity, owner, revision, and evidence links remain clear.

## Summary

| Work product                | Kind     | Producing tasks | Consuming tasks | Uses |
| --------------------------- | -------- | --------------: | --------------: | ---: |
| PSM Increment Plan          | Artifact |               1 |               7 |    8 |
| Deployment Strategy         | Artifact |               3 |              21 |   24 |
| SAM Stack Scaffold          | Artifact |               2 |               8 |   10 |
| Security Baseline           | Artifact |               2 |              19 |   21 |
| Network & Identity          | Artifact |               4 |              17 |   21 |
| Durable Storage Layer       | Artifact |               2 |               5 |    7 |
| Messaging Layer             | Artifact |               2 |               5 |    7 |
| Event Fabric                | Artifact |               2 |               9 |   11 |
| Lambda Compute Layer        | Artifact |               2 |               8 |   10 |
| API Gateway Layer           | Artifact |               2 |               6 |    8 |
| Workflow & Observability    | Artifact |               3 |               5 |    8 |
| Integration View Catalog    | Artifact |               1 |               2 |    3 |
| Deployment Readiness Record | Artifact |               1 |               1 |    2 |
| PSM Increment Review Record | Artifact |               1 |               0 |    1 |

## Detailed work products

## PSM Increment Plan

<small>Artifact: `psm-artifact.increment-plan` · 8 WorkProductUse occurrences</small>

Selected deployable slice, AWS assumptions, and deployment definition of done

**Produced or updated by**

- Plan deployable slice

**Consumed by**

- Create AWS PSM model root
- Define stage and naming policies
- Establish shared model contract and evidence conventions
- Create SAM stack and globals
- Configure VPC and subnets
- Complete trace and readiness
- Review and adapt PSM increment

**Work-product uses**

- `wpu.psm.ph1.st0.t1.output.psm-artifact.increment-plan` as **output** in task `psm.ph1.st0.t1`
- `wpu.psm.ph1.st1.t1.input.psm-artifact.increment-plan` as **input** in task `psm.ph1.st1.t1`
- `wpu.psm.ph1.st1.t2.input.psm-artifact.increment-plan` as **input** in task `psm.ph1.st1.t2`
- `wpu.psm.ph1.st1.t3.input.psm-artifact.increment-plan` as **input** in task `psm.ph1.st1.t3`
- `wpu.psm.ph1.st2.t1.input.psm-artifact.increment-plan` as **input** in task `psm.ph1.st2.t1`
- `wpu.psm.ph2.st1.t1.input.psm-artifact.increment-plan` as **input** in task `psm.ph2.st1.t1`
- `wpu.psm.ph6.st2.t1.input.psm-artifact.increment-plan` as **input** in task `psm.ph6.st2.t1`
- `wpu.psm.ph6.st3.t1.input.psm-artifact.increment-plan` as **input** in task `psm.ph6.st3.t1`

Keep the accepted revision under suitable configuration control. Record its owner, approval state, and trace links to source decisions and downstream evidence.

## Deployment Strategy

<small>Artifact: `psm-artifact.deployment-strategy` · 24 WorkProductUse occurrences</small>

Account, region, naming, tagging policies

**Produced or updated by**

- Create AWS PSM model root
- Define stage and naming policies
- Establish shared model contract and evidence conventions

**Consumed by**

- Define stage and naming policies
- Establish shared model contract and evidence conventions
- Create SAM stack and globals
- Define CFN parameters and outputs
- Configure IAM roles and policies
- Provision KMS, secrets, and SSM
- Configure VPC and subnets
- Define endpoints and security groups
- Configure Cognito user pools
- Configure clients, groups, and identity pools
- Provision DynamoDB tables
- Configure S3 buckets and policies
- Create SQS queues
- Configure SNS topics and subscriptions
- Configure EventBridge buses and rules
- Configure schedules, pipes, and connections
- Deploy Lambda functions
- Configure APIs and routes
- Create integration relationship views
- Complete trace and readiness
- Review and adapt PSM increment

**Work-product uses**

- `wpu.psm.ph1.st1.t1.output.psm-artifact.deployment-strategy` as **output** in task `psm.ph1.st1.t1`
- `wpu.psm.ph1.st1.t2.input.psm-artifact.deployment-strategy` as **input** in task `psm.ph1.st1.t2`
- `wpu.psm.ph1.st1.t2.output.psm-artifact.deployment-strategy` as **output** in task `psm.ph1.st1.t2`
- `wpu.psm.ph1.st1.t3.input.psm-artifact.deployment-strategy` as **input** in task `psm.ph1.st1.t3`
- `wpu.psm.ph1.st1.t3.output.psm-artifact.deployment-strategy` as **output** in task `psm.ph1.st1.t3`
- `wpu.psm.ph1.st2.t1.input.psm-artifact.deployment-strategy` as **input** in task `psm.ph1.st2.t1`
- `wpu.psm.ph1.st2.t2.input.psm-artifact.deployment-strategy` as **input** in task `psm.ph1.st2.t2`
- `wpu.psm.ph1.st3.t1.input.psm-artifact.deployment-strategy` as **input** in task `psm.ph1.st3.t1`
- `wpu.psm.ph1.st3.t2.input.psm-artifact.deployment-strategy` as **input** in task `psm.ph1.st3.t2`
- `wpu.psm.ph2.st1.t1.input.psm-artifact.deployment-strategy` as **input** in task `psm.ph2.st1.t1`
- `wpu.psm.ph2.st1.t2.input.psm-artifact.deployment-strategy` as **input** in task `psm.ph2.st1.t2`
- `wpu.psm.ph2.st2.t1.input.psm-artifact.deployment-strategy` as **input** in task `psm.ph2.st2.t1`
- `wpu.psm.ph2.st2.t2.input.psm-artifact.deployment-strategy` as **input** in task `psm.ph2.st2.t2`
- `wpu.psm.ph3.st1.t1.input.psm-artifact.deployment-strategy` as **input** in task `psm.ph3.st1.t1`
- `wpu.psm.ph3.st1.t2.input.psm-artifact.deployment-strategy` as **input** in task `psm.ph3.st1.t2`
- `wpu.psm.ph3.st2.t1.input.psm-artifact.deployment-strategy` as **input** in task `psm.ph3.st2.t1`
- `wpu.psm.ph3.st2.t2.input.psm-artifact.deployment-strategy` as **input** in task `psm.ph3.st2.t2`
- `wpu.psm.ph4.st1.t1.input.psm-artifact.deployment-strategy` as **input** in task `psm.ph4.st1.t1`
- `wpu.psm.ph4.st1.t2.input.psm-artifact.deployment-strategy` as **input** in task `psm.ph4.st1.t2`
- `wpu.psm.ph4.st2.t1.input.psm-artifact.deployment-strategy` as **input** in task `psm.ph4.st2.t1`
- `wpu.psm.ph5.st1.t1.input.psm-artifact.deployment-strategy` as **input** in task `psm.ph5.st1.t1`
- `wpu.psm.ph6.st1.t1.input.psm-artifact.deployment-strategy` as **input** in task `psm.ph6.st1.t1`
- `wpu.psm.ph6.st2.t1.input.psm-artifact.deployment-strategy` as **input** in task `psm.ph6.st2.t1`
- `wpu.psm.ph6.st3.t1.input.psm-artifact.deployment-strategy` as **input** in task `psm.ph6.st3.t1`

Keep the accepted revision under suitable configuration control. Record its owner, approval state, and trace links to source decisions and downstream evidence.

## SAM Stack Scaffold

<small>Artifact: `psm-artifact.stack-scaffold` · 10 WorkProductUse occurrences</small>

Stacks, globals, CFN parameters

**Produced or updated by**

- Create SAM stack and globals
- Define CFN parameters and outputs

**Consumed by**

- Define CFN parameters and outputs
- Configure IAM roles and policies
- Provision KMS, secrets, and SSM
- Configure VPC and subnets
- Provision DynamoDB tables
- Configure S3 buckets and policies
- Deploy Lambda functions
- Complete trace and readiness

**Work-product uses**

- `wpu.psm.ph1.st2.t1.output.psm-artifact.stack-scaffold` as **output** in task `psm.ph1.st2.t1`
- `wpu.psm.ph1.st2.t2.input.psm-artifact.stack-scaffold` as **input** in task `psm.ph1.st2.t2`
- `wpu.psm.ph1.st2.t2.output.psm-artifact.stack-scaffold` as **output** in task `psm.ph1.st2.t2`
- `wpu.psm.ph1.st3.t1.input.psm-artifact.stack-scaffold` as **input** in task `psm.ph1.st3.t1`
- `wpu.psm.ph1.st3.t2.input.psm-artifact.stack-scaffold` as **input** in task `psm.ph1.st3.t2`
- `wpu.psm.ph2.st1.t1.input.psm-artifact.stack-scaffold` as **input** in task `psm.ph2.st1.t1`
- `wpu.psm.ph3.st1.t1.input.psm-artifact.stack-scaffold` as **input** in task `psm.ph3.st1.t1`
- `wpu.psm.ph3.st1.t2.input.psm-artifact.stack-scaffold` as **input** in task `psm.ph3.st1.t2`
- `wpu.psm.ph4.st2.t1.input.psm-artifact.stack-scaffold` as **input** in task `psm.ph4.st2.t1`
- `wpu.psm.ph6.st2.t1.input.psm-artifact.stack-scaffold` as **input** in task `psm.ph6.st2.t1`

Keep the accepted revision under suitable configuration control. Record its owner, approval state, and trace links to source decisions and downstream evidence.

## Security Baseline

<small>Artifact: `psm-artifact.security-baseline` · 21 WorkProductUse occurrences</small>

IAM, KMS, secrets, SSM

**Produced or updated by**

- Configure IAM roles and policies
- Provision KMS, secrets, and SSM

**Consumed by**

- Provision KMS, secrets, and SSM
- Configure VPC and subnets
- Define endpoints and security groups
- Configure Cognito user pools
- Configure clients, groups, and identity pools
- Provision DynamoDB tables
- Configure S3 buckets and policies
- Create SQS queues
- Configure SNS topics and subscriptions
- Configure EventBridge buses and rules
- Configure schedules, pipes, and connections
- Deploy Lambda functions
- Configure event sources and permissions
- Configure APIs and routes
- Configure integrations and authorizers
- Deploy state machines
- Configure CloudWatch logs and metrics
- Complete trace and readiness
- Review and adapt PSM increment

**Work-product uses**

- `wpu.psm.ph1.st3.t1.output.psm-artifact.security-baseline` as **output** in task `psm.ph1.st3.t1`
- `wpu.psm.ph1.st3.t2.input.psm-artifact.security-baseline` as **input** in task `psm.ph1.st3.t2`
- `wpu.psm.ph1.st3.t2.output.psm-artifact.security-baseline` as **output** in task `psm.ph1.st3.t2`
- `wpu.psm.ph2.st1.t1.input.psm-artifact.security-baseline` as **input** in task `psm.ph2.st1.t1`
- `wpu.psm.ph2.st1.t2.input.psm-artifact.security-baseline` as **input** in task `psm.ph2.st1.t2`
- `wpu.psm.ph2.st2.t1.input.psm-artifact.security-baseline` as **input** in task `psm.ph2.st2.t1`
- `wpu.psm.ph2.st2.t2.input.psm-artifact.security-baseline` as **input** in task `psm.ph2.st2.t2`
- `wpu.psm.ph3.st1.t1.input.psm-artifact.security-baseline` as **input** in task `psm.ph3.st1.t1`
- `wpu.psm.ph3.st1.t2.input.psm-artifact.security-baseline` as **input** in task `psm.ph3.st1.t2`
- `wpu.psm.ph3.st2.t1.input.psm-artifact.security-baseline` as **input** in task `psm.ph3.st2.t1`
- `wpu.psm.ph3.st2.t2.input.psm-artifact.security-baseline` as **input** in task `psm.ph3.st2.t2`
- `wpu.psm.ph4.st1.t1.input.psm-artifact.security-baseline` as **input** in task `psm.ph4.st1.t1`
- `wpu.psm.ph4.st1.t2.input.psm-artifact.security-baseline` as **input** in task `psm.ph4.st1.t2`
- `wpu.psm.ph4.st2.t1.input.psm-artifact.security-baseline` as **input** in task `psm.ph4.st2.t1`
- `wpu.psm.ph4.st2.t2.input.psm-artifact.security-baseline` as **input** in task `psm.ph4.st2.t2`
- `wpu.psm.ph5.st1.t1.input.psm-artifact.security-baseline` as **input** in task `psm.ph5.st1.t1`
- `wpu.psm.ph5.st1.t2.input.psm-artifact.security-baseline` as **input** in task `psm.ph5.st1.t2`
- `wpu.psm.ph5.st2.ss1.t1.input.psm-artifact.security-baseline` as **input** in task `psm.ph5.st2.ss1.t1`
- `wpu.psm.ph5.st2.ss2.t1.input.psm-artifact.security-baseline` as **input** in task `psm.ph5.st2.ss2.t1`
- `wpu.psm.ph6.st2.t1.input.psm-artifact.security-baseline` as **input** in task `psm.ph6.st2.t1`
- `wpu.psm.ph6.st3.t1.input.psm-artifact.security-baseline` as **input** in task `psm.ph6.st3.t1`

Keep the accepted revision under suitable configuration control. Record its owner, approval state, and trace links to source decisions and downstream evidence.

## Network & Identity

<small>Artifact: `psm-artifact.network-identity` · 21 WorkProductUse occurrences</small>

VPC, Cognito resources

**Produced or updated by**

- Configure VPC and subnets
- Define endpoints and security groups
- Configure Cognito user pools
- Configure clients, groups, and identity pools

**Consumed by**

- Define endpoints and security groups
- Configure Cognito user pools
- Configure clients, groups, and identity pools
- Provision DynamoDB tables
- Configure S3 buckets and policies
- Create SQS queues
- Configure SNS topics and subscriptions
- Configure EventBridge buses and rules
- Configure schedules, pipes, and connections
- Deploy Lambda functions
- Configure event sources and permissions
- Configure APIs and routes
- Configure integrations and authorizers
- Deploy state machines
- Configure alarms and dashboards
- Create integration relationship views
- Complete trace and readiness

**Work-product uses**

- `wpu.psm.ph2.st1.t1.output.psm-artifact.network-identity` as **output** in task `psm.ph2.st1.t1`
- `wpu.psm.ph2.st1.t2.input.psm-artifact.network-identity` as **input** in task `psm.ph2.st1.t2`
- `wpu.psm.ph2.st1.t2.output.psm-artifact.network-identity` as **output** in task `psm.ph2.st1.t2`
- `wpu.psm.ph2.st2.t1.input.psm-artifact.network-identity` as **input** in task `psm.ph2.st2.t1`
- `wpu.psm.ph2.st2.t1.output.psm-artifact.network-identity` as **output** in task `psm.ph2.st2.t1`
- `wpu.psm.ph2.st2.t2.input.psm-artifact.network-identity` as **input** in task `psm.ph2.st2.t2`
- `wpu.psm.ph2.st2.t2.output.psm-artifact.network-identity` as **output** in task `psm.ph2.st2.t2`
- `wpu.psm.ph3.st1.t1.input.psm-artifact.network-identity` as **input** in task `psm.ph3.st1.t1`
- `wpu.psm.ph3.st1.t2.input.psm-artifact.network-identity` as **input** in task `psm.ph3.st1.t2`
- `wpu.psm.ph3.st2.t1.input.psm-artifact.network-identity` as **input** in task `psm.ph3.st2.t1`
- `wpu.psm.ph3.st2.t2.input.psm-artifact.network-identity` as **input** in task `psm.ph3.st2.t2`
- `wpu.psm.ph4.st1.t1.input.psm-artifact.network-identity` as **input** in task `psm.ph4.st1.t1`
- `wpu.psm.ph4.st1.t2.input.psm-artifact.network-identity` as **input** in task `psm.ph4.st1.t2`
- `wpu.psm.ph4.st2.t1.input.psm-artifact.network-identity` as **input** in task `psm.ph4.st2.t1`
- `wpu.psm.ph4.st2.t2.input.psm-artifact.network-identity` as **input** in task `psm.ph4.st2.t2`
- `wpu.psm.ph5.st1.t1.input.psm-artifact.network-identity` as **input** in task `psm.ph5.st1.t1`
- `wpu.psm.ph5.st1.t2.input.psm-artifact.network-identity` as **input** in task `psm.ph5.st1.t2`
- `wpu.psm.ph5.st2.ss1.t1.input.psm-artifact.network-identity` as **input** in task `psm.ph5.st2.ss1.t1`
- `wpu.psm.ph5.st2.ss2.t2.input.psm-artifact.network-identity` as **input** in task `psm.ph5.st2.ss2.t2`
- `wpu.psm.ph6.st1.t1.input.psm-artifact.network-identity` as **input** in task `psm.ph6.st1.t1`
- `wpu.psm.ph6.st2.t1.input.psm-artifact.network-identity` as **input** in task `psm.ph6.st2.t1`

Keep the accepted revision under suitable configuration control. Record its owner, approval state, and trace links to source decisions and downstream evidence.

## Durable Storage Layer

<small>Artifact: `psm-artifact.storage-layer` · 7 WorkProductUse occurrences</small>

DynamoDB and S3 resources

**Produced or updated by**

- Provision DynamoDB tables
- Configure S3 buckets and policies

**Consumed by**

- Configure S3 buckets and policies
- Create SQS queues
- Configure SNS topics and subscriptions
- Create integration relationship views
- Complete trace and readiness

**Work-product uses**

- `wpu.psm.ph3.st1.t1.output.psm-artifact.storage-layer` as **output** in task `psm.ph3.st1.t1`
- `wpu.psm.ph3.st1.t2.input.psm-artifact.storage-layer` as **input** in task `psm.ph3.st1.t2`
- `wpu.psm.ph3.st1.t2.output.psm-artifact.storage-layer` as **output** in task `psm.ph3.st1.t2`
- `wpu.psm.ph3.st2.t1.input.psm-artifact.storage-layer` as **input** in task `psm.ph3.st2.t1`
- `wpu.psm.ph3.st2.t2.input.psm-artifact.storage-layer` as **input** in task `psm.ph3.st2.t2`
- `wpu.psm.ph6.st1.t1.input.psm-artifact.storage-layer` as **input** in task `psm.ph6.st1.t1`
- `wpu.psm.ph6.st2.t1.input.psm-artifact.storage-layer` as **input** in task `psm.ph6.st2.t1`

Keep the accepted revision under suitable configuration control. Record its owner, approval state, and trace links to source decisions and downstream evidence.

## Messaging Layer

<small>Artifact: `psm-artifact.messaging-layer` · 7 WorkProductUse occurrences</small>

SQS and SNS resources

**Produced or updated by**

- Create SQS queues
- Configure SNS topics and subscriptions

**Consumed by**

- Configure SNS topics and subscriptions
- Configure EventBridge buses and rules
- Configure schedules, pipes, and connections
- Create integration relationship views
- Complete trace and readiness

**Work-product uses**

- `wpu.psm.ph3.st2.t1.output.psm-artifact.messaging-layer` as **output** in task `psm.ph3.st2.t1`
- `wpu.psm.ph3.st2.t2.input.psm-artifact.messaging-layer` as **input** in task `psm.ph3.st2.t2`
- `wpu.psm.ph3.st2.t2.output.psm-artifact.messaging-layer` as **output** in task `psm.ph3.st2.t2`
- `wpu.psm.ph4.st1.t1.input.psm-artifact.messaging-layer` as **input** in task `psm.ph4.st1.t1`
- `wpu.psm.ph4.st1.t2.input.psm-artifact.messaging-layer` as **input** in task `psm.ph4.st1.t2`
- `wpu.psm.ph6.st1.t1.input.psm-artifact.messaging-layer` as **input** in task `psm.ph6.st1.t1`
- `wpu.psm.ph6.st2.t1.input.psm-artifact.messaging-layer` as **input** in task `psm.ph6.st2.t1`

Keep the accepted revision under suitable configuration control. Record its owner, approval state, and trace links to source decisions and downstream evidence.

## Event Fabric

<small>Artifact: `psm-artifact.event-fabric` · 11 WorkProductUse occurrences</small>

EventBridge buses, rules, pipes

**Produced or updated by**

- Configure EventBridge buses and rules
- Configure schedules, pipes, and connections

**Consumed by**

- Configure schedules, pipes, and connections
- Deploy Lambda functions
- Configure event sources and permissions
- Configure integrations and authorizers
- Deploy state machines
- Configure CloudWatch logs and metrics
- Configure alarms and dashboards
- Create integration relationship views
- Complete trace and readiness

**Work-product uses**

- `wpu.psm.ph4.st1.t1.output.psm-artifact.event-fabric` as **output** in task `psm.ph4.st1.t1`
- `wpu.psm.ph4.st1.t2.input.psm-artifact.event-fabric` as **input** in task `psm.ph4.st1.t2`
- `wpu.psm.ph4.st1.t2.output.psm-artifact.event-fabric` as **output** in task `psm.ph4.st1.t2`
- `wpu.psm.ph4.st2.t1.input.psm-artifact.event-fabric` as **input** in task `psm.ph4.st2.t1`
- `wpu.psm.ph4.st2.t2.input.psm-artifact.event-fabric` as **input** in task `psm.ph4.st2.t2`
- `wpu.psm.ph5.st1.t2.input.psm-artifact.event-fabric` as **input** in task `psm.ph5.st1.t2`
- `wpu.psm.ph5.st2.ss1.t1.input.psm-artifact.event-fabric` as **input** in task `psm.ph5.st2.ss1.t1`
- `wpu.psm.ph5.st2.ss2.t1.input.psm-artifact.event-fabric` as **input** in task `psm.ph5.st2.ss2.t1`
- `wpu.psm.ph5.st2.ss2.t2.input.psm-artifact.event-fabric` as **input** in task `psm.ph5.st2.ss2.t2`
- `wpu.psm.ph6.st1.t1.input.psm-artifact.event-fabric` as **input** in task `psm.ph6.st1.t1`
- `wpu.psm.ph6.st2.t1.input.psm-artifact.event-fabric` as **input** in task `psm.ph6.st2.t1`

Keep the accepted revision under suitable configuration control. Record its owner, approval state, and trace links to source decisions and downstream evidence.

## Lambda Compute Layer

<small>Artifact: `psm-artifact.compute-layer` · 10 WorkProductUse occurrences</small>

Functions, mappings, permissions

**Produced or updated by**

- Deploy Lambda functions
- Configure event sources and permissions

**Consumed by**

- Configure event sources and permissions
- Configure APIs and routes
- Configure integrations and authorizers
- Deploy state machines
- Configure CloudWatch logs and metrics
- Configure alarms and dashboards
- Create integration relationship views
- Complete trace and readiness

**Work-product uses**

- `wpu.psm.ph4.st2.t1.output.psm-artifact.compute-layer` as **output** in task `psm.ph4.st2.t1`
- `wpu.psm.ph4.st2.t2.input.psm-artifact.compute-layer` as **input** in task `psm.ph4.st2.t2`
- `wpu.psm.ph4.st2.t2.output.psm-artifact.compute-layer` as **output** in task `psm.ph4.st2.t2`
- `wpu.psm.ph5.st1.t1.input.psm-artifact.compute-layer` as **input** in task `psm.ph5.st1.t1`
- `wpu.psm.ph5.st1.t2.input.psm-artifact.compute-layer` as **input** in task `psm.ph5.st1.t2`
- `wpu.psm.ph5.st2.ss1.t1.input.psm-artifact.compute-layer` as **input** in task `psm.ph5.st2.ss1.t1`
- `wpu.psm.ph5.st2.ss2.t1.input.psm-artifact.compute-layer` as **input** in task `psm.ph5.st2.ss2.t1`
- `wpu.psm.ph5.st2.ss2.t2.input.psm-artifact.compute-layer` as **input** in task `psm.ph5.st2.ss2.t2`
- `wpu.psm.ph6.st1.t1.input.psm-artifact.compute-layer` as **input** in task `psm.ph6.st1.t1`
- `wpu.psm.ph6.st2.t1.input.psm-artifact.compute-layer` as **input** in task `psm.ph6.st2.t1`

Keep the accepted revision under suitable configuration control. Record its owner, approval state, and trace links to source decisions and downstream evidence.

## API Gateway Layer

<small>Artifact: `psm-artifact.api-layer` · 8 WorkProductUse occurrences</small>

HTTP/REST/WebSocket APIs

**Produced or updated by**

- Configure APIs and routes
- Configure integrations and authorizers

**Consumed by**

- Configure integrations and authorizers
- Deploy state machines
- Configure CloudWatch logs and metrics
- Configure alarms and dashboards
- Create integration relationship views
- Complete trace and readiness

**Work-product uses**

- `wpu.psm.ph5.st1.t1.output.psm-artifact.api-layer` as **output** in task `psm.ph5.st1.t1`
- `wpu.psm.ph5.st1.t2.input.psm-artifact.api-layer` as **input** in task `psm.ph5.st1.t2`
- `wpu.psm.ph5.st1.t2.output.psm-artifact.api-layer` as **output** in task `psm.ph5.st1.t2`
- `wpu.psm.ph5.st2.ss1.t1.input.psm-artifact.api-layer` as **input** in task `psm.ph5.st2.ss1.t1`
- `wpu.psm.ph5.st2.ss2.t1.input.psm-artifact.api-layer` as **input** in task `psm.ph5.st2.ss2.t1`
- `wpu.psm.ph5.st2.ss2.t2.input.psm-artifact.api-layer` as **input** in task `psm.ph5.st2.ss2.t2`
- `wpu.psm.ph6.st1.t1.input.psm-artifact.api-layer` as **input** in task `psm.ph6.st1.t1`
- `wpu.psm.ph6.st2.t1.input.psm-artifact.api-layer` as **input** in task `psm.ph6.st2.t1`

Keep the accepted revision under suitable configuration control. Record its owner, approval state, and trace links to source decisions and downstream evidence.

## Workflow & Observability

<small>Artifact: `psm-artifact.workflow-observability` · 8 WorkProductUse occurrences</small>

Step Functions and CloudWatch

**Produced or updated by**

- Deploy state machines
- Configure CloudWatch logs and metrics
- Configure alarms and dashboards

**Consumed by**

- Configure CloudWatch logs and metrics
- Configure alarms and dashboards
- Create integration relationship views
- Complete trace and readiness
- Review and adapt PSM increment

**Work-product uses**

- `wpu.psm.ph5.st2.ss1.t1.output.psm-artifact.workflow-observability` as **output** in task `psm.ph5.st2.ss1.t1`
- `wpu.psm.ph5.st2.ss2.t1.input.psm-artifact.workflow-observability` as **input** in task `psm.ph5.st2.ss2.t1`
- `wpu.psm.ph5.st2.ss2.t1.output.psm-artifact.workflow-observability` as **output** in task `psm.ph5.st2.ss2.t1`
- `wpu.psm.ph5.st2.ss2.t2.input.psm-artifact.workflow-observability` as **input** in task `psm.ph5.st2.ss2.t2`
- `wpu.psm.ph5.st2.ss2.t2.output.psm-artifact.workflow-observability` as **output** in task `psm.ph5.st2.ss2.t2`
- `wpu.psm.ph6.st1.t1.input.psm-artifact.workflow-observability` as **input** in task `psm.ph6.st1.t1`
- `wpu.psm.ph6.st2.t1.input.psm-artifact.workflow-observability` as **input** in task `psm.ph6.st2.t1`
- `wpu.psm.ph6.st3.t1.input.psm-artifact.workflow-observability` as **input** in task `psm.ph6.st3.t1`

Keep the accepted revision under suitable configuration control. Record its owner, approval state, and trace links to source decisions and downstream evidence.

## Integration View Catalog

<small>Artifact: `psm-artifact.integration-views` · 3 WorkProductUse occurrences</small>

Cross-resource relationship views

**Produced or updated by**

- Create integration relationship views

**Consumed by**

- Complete trace and readiness
- Review and adapt PSM increment

**Work-product uses**

- `wpu.psm.ph6.st1.t1.output.psm-artifact.integration-views` as **output** in task `psm.ph6.st1.t1`
- `wpu.psm.ph6.st2.t1.input.psm-artifact.integration-views` as **input** in task `psm.ph6.st2.t1`
- `wpu.psm.ph6.st3.t1.input.psm-artifact.integration-views` as **input** in task `psm.ph6.st3.t1`

Keep the accepted revision under suitable configuration control. Record its owner, approval state, and trace links to source decisions and downstream evidence.

## Deployment Readiness Record

<small>Artifact: `psm-artifact.deployment-readiness` · 2 WorkProductUse occurrences</small>

Trace and readiness closure

**Produced or updated by**

- Complete trace and readiness

**Consumed by**

- Review and adapt PSM increment

**Work-product uses**

- `wpu.psm.ph6.st2.t1.output.psm-artifact.deployment-readiness` as **output** in task `psm.ph6.st2.t1`
- `wpu.psm.ph6.st3.t1.input.psm-artifact.deployment-readiness` as **input** in task `psm.ph6.st3.t1`

Keep the accepted revision under suitable configuration control. Record its owner, approval state, and trace links to source decisions and downstream evidence.

## PSM Increment Review Record

<small>Artifact: `psm-artifact.increment-review` · 1 WorkProductUse occurrence</small>

Deployment review outcomes, accepted AWS slice, and improvement actions

**Produced or updated by**

- Review and adapt PSM increment

**Consumed by**

- No task in this scope declares this item as an input.

**Work-product uses**

- `wpu.psm.ph6.st3.t1.output.psm-artifact.increment-review` as **output** in task `psm.ph6.st3.t1`

Keep the accepted revision under suitable configuration control. Record its owner, approval state, and trace links to source decisions and downstream evidence.

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
