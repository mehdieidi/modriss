# PSM (AWS) Modeling Methodology

AWS platform-specific modeling follows process `modless.psm.modeling` (`mde/methodology/process-definitions/psm.json`). Eleven phases (`psm.p0`–`psm.p11`) refine an AWS PSM from PIM→PSM ETL or greenfield. The PSM EVL gate (`psm.m1.evl-gate`) must pass before M2T artifact generation.

## Phase Flow

Phases build infrastructure in layers: account strategy and stack scaffolding first, then security and networking foundations, followed by data, messaging, events, compute, API, workflow/observability, and integration views.

```mermaid
flowchart TB
    p0["psm.p0.account-stage-strategy<br/>Account & Stage Strategy · dashboard · 30m"]
    p1["psm.p1.stack-scaffolding<br/>Stack Scaffolding · stack · 1h"]
    p2["psm.p2.security-baseline<br/>Security Baseline · security · 2h"]
    p3["psm.p3.networking<br/>Networking · networking · 1-2h"]
    p4["psm.p4.identity<br/>Identity · identity · 1-2h"]
    p5["psm.p5.durable-storage<br/>Durable Storage · storage · 2-3h"]
    p6["psm.p6.messaging<br/>Messaging · messaging · 1-2h"]
    p7["psm.p7.event-fabric<br/>Event Fabric · events · 2h"]
    p8["psm.p8.compute<br/>Compute · compute · 2-3h"]
    p9["psm.p9.api-gateway<br/>API Gateway · api · 2-3h"]
    p10["psm.p10.workflow-observability<br/>Workflow & Observability · workflow · 2h"]
    p11["psm.p11.integration-views-readiness<br/>Integration Views & Readiness · readiness · 1-2h"]
    gate["psm.m1.evl-gate<br/>PSM EVL validation gate"]

    p0 --> p1 --> p2 --> p3 --> p4 --> p5 --> p6 --> p7 --> p8 --> p9 --> p10 --> p11 --> gate
```

Primary role is **cloud-platform-engineer**; **process-reviewer** owns `psm.p11` and EVL approval.

## SamStack Hub

`SamStack` (`psm.p1.stack-scaffolding`) is the structural hub of the AWS PSM. All deployable resources are composed under one or more SAM stacks with shared globals, CloudFormation parameters, and outputs. Later phases attach resources to this hub rather than creating free-floating CFN fragments.

```mermaid
flowchart TB
    root["AwsPsmModel<br/>psm.p0.account-stage-strategy"]
    stage["AwsStage · naming · tagging policies"]
    stack["SamStack<br/>psm.p1.stack-scaffolding"]
    globals["SamGlobals"]
    cfn["CfnParameter · CfnMapping · CfnCondition · CfnOutput"]

    subgraph resources["Resources composed into SamStack"]
        sec["psm.p2 Security Baseline<br/>IAM, KMS, Secrets Manager, SSM"]
        net["psm.p3 Networking<br/>VPC, Subnet, SecurityGroup"]
        id["psm.p4 Identity<br/>Cognito pools and clients"]
        stor["psm.p5 Durable Storage<br/>DynamoDB, S3"]
        msg["psm.p6 Messaging<br/>SQS, SNS"]
        evt["psm.p7 Event Fabric<br/>EventBridge buses, rules, pipes"]
        cmp["psm.p8 Compute<br/>AwsLambdaFunction, SamFunctionEvent"]
        api["psm.p9 API Gateway<br/>HttpApi, RestApi, integrations"]
        wf["psm.p10 Workflow & Observability<br/>Step Functions, CloudWatch"]
    end

    views["psm.p11 Integration Views<br/>ApiGatewayLambdaIntegrationView, etc."]
    m2t["e2e.p6.m2t-generation<br/>awspsm-to-artifacts"]

    root --> stage --> stack
    stack --> globals
    stack --> cfn
    stack --> sec --> net --> id --> stor --> msg --> evt --> cmp --> api --> wf
    wf --> views --> gate["psm.m1.evl-gate"] --> m2t
```

### SamStack composition detail

```mermaid
flowchart LR
    SamStack["SamStack"]
    SamGlobals["SamGlobals<br/>default runtime, memory, tags"]
    Lambda["AwsLambdaFunction + SamFunctionEvent"]
    API["HttpApi / RestApi + ApiGatewayIntegration"]
    SM["StepFunctionStateMachine + SamStateMachineEvent"]
    Params["CfnParameter / CfnOutput<br/>cross-stack references"]

    SamStack --> SamGlobals
    SamStack --> Lambda
    SamStack --> API
    SamStack --> SM
    SamStack --> Params
    Lambda -->|"LambdaPermission, event sources"| API
    Lambda -->|"targets"| SM
```

Key work products in `psm.p1.stack-scaffolding`:

| Element        | Role in hub                                             |
| -------------- | ------------------------------------------------------- |
| `SamStack`     | Root CloudFormation/SAM template container              |
| `SamGlobals`   | Shared defaults for functions, APIs, and state machines |
| `CfnParameter` | Environment-specific inputs (stage, capacity)           |
| `CfnMapping`   | Region/account conditional lookups                      |
| `CfnCondition` | Feature flags and environment branching                 |
| `CfnOutput`    | Exported ARNs and URLs for integration views            |

## PIM Trace-Through

Each PSM phase exit criteria reference PIM alignment (e.g. `psm.p5` — durable stores match PIM data model; `psm.p8` — compute matches PIM functions). Integration relationship views in `psm.p11` (`ApiGatewayLambdaIntegrationView`, `SqsLambdaEventSourceView`, etc.) document wiring inside the SamStack before `psm-semantic-validation` and M2T generation.
