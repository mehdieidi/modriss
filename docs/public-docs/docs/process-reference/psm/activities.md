# AWS PSM modeling: phases and activities

This page documents the **SPEM process-structure Activity, Phase, and TaskUse** elements used by the AWS PSM modeling process. It is generated from the canonical executable definition, so the names and identifiers match the API and process interface. The explanation adds practical teaching around the normative data.

A phase establishes a significant lifecycle period and normally ends at a major checkpoint. An activity groups related work within a phase or process component. A TaskUse places reusable task guidance into that process context. Entry and exit conditions describe evidence states; they are not calendar dates.

## Summary

| Phase or activity             | SPEM type | Contained by                  | Task uses | Execution        |
| ----------------------------- | --------- | ----------------------------- | --------: | ---------------- |
| Deployment & Slice Framing    | Activity  | Process                       |         0 | defined sequence |
| Deployable Slice Planning     | Activity  | Deployment & Slice Framing    |         1 | defined sequence |
| Account & Stage Strategy      | Activity  | Deployment & Slice Framing    |         3 | defined sequence |
| Stack Scaffolding             | Activity  | Deployment & Slice Framing    |         2 | defined sequence |
| Security Baseline             | Activity  | Deployment & Slice Framing    |         2 | defined sequence |
| Network & Identity            | Activity  | Process                       |         0 | defined sequence |
| Networking                    | Activity  | Network & Identity            |         2 | defined sequence |
| Identity                      | Activity  | Network & Identity            |         2 | defined sequence |
| Storage & Messaging           | Activity  | Process                       |         0 | defined sequence |
| Durable Storage               | Activity  | Storage & Messaging           |         2 | defined sequence |
| Messaging                     | Activity  | Storage & Messaging           |         2 | defined sequence |
| Event Fabric & Compute        | Activity  | Process                       |         0 | defined sequence |
| Event Fabric                  | Activity  | Event Fabric & Compute        |         2 | defined sequence |
| Compute                       | Activity  | Event Fabric & Compute        |         2 | defined sequence |
| API & Orchestration           | Activity  | Process                       |         0 | defined sequence |
| API Gateway                   | Activity  | API & Orchestration           |         2 | defined sequence |
| Workflow & Observability      | Activity  | API & Orchestration           |         0 | defined sequence |
| Step Functions                | Activity  | Workflow & Observability      |         1 | defined sequence |
| CloudWatch Observability      | Activity  | Workflow & Observability      |         2 | defined sequence |
| Integration Views & Readiness | Activity  | Process                       |         0 | defined sequence |
| Integration Views             | Activity  | Integration Views & Readiness |         1 | defined sequence |
| Trace & Readiness Gate        | Activity  | Integration Views & Readiness |         1 | defined sequence |
| Increment Review & Adapt      | Activity  | Integration Views & Readiness |         1 | defined sequence |

## Detailed activities

## Deployment & Slice Framing

<small>Activity · MODRISS::Stage: `psm.ph1`</small>

Frame the current deployable slice and establish or refresh AWS account, stack, and security foundations.

**Entry conditions**

- PIM transform complete, prior PSM increment selected, or greenfield PSM

**Exit conditions**

- AWS root and stage strategy configured
- Deployable-slice objective and deployment definition of done are agreed
- Security baseline applied

**Participating roles**

- Cloud Platform Engineer

### Deployable Slice Planning

<small>Activity · MODRISS::SubStage: `psm.ph1.st0` · contained by **Deployment & Slice Framing**</small>

Select the AWS deployable slice and define deployment review expectations.

**Participating roles**

- Cloud Platform Engineer

**Task uses in this activity**

- **Plan deployable slice** (TaskUse `psm.ph1.st0.t1`)

### Account & Stage Strategy

<small>Activity · MODRISS::SubStage: `psm.ph1.st1` · contained by **Deployment & Slice Framing**</small>

Create or refresh AwsPsmModel root with partition, region, naming, and tagging policies.

**Participating roles**

- Cloud Platform Engineer
- Method Engineer

**Task uses in this activity**

- **Create AWS PSM model root** (TaskUse `psm.ph1.st1.t1`)
- **Define stage and naming policies** (TaskUse `psm.ph1.st1.t2`)
- **Establish shared model contract and evidence conventions** (TaskUse `psm.ph1.st1.t3`)

### Stack Scaffolding

<small>Activity · MODRISS::SubStage: `psm.ph1.st2` · contained by **Deployment & Slice Framing**</small>

Create SAM stack with globals and CloudFormation parameters.

**Participating roles**

- Cloud Platform Engineer

**Task uses in this activity**

- **Create SAM stack and globals** (TaskUse `psm.ph1.st2.t1`)
- **Define CFN parameters and outputs** (TaskUse `psm.ph1.st2.t2`)

### Security Baseline

<small>Activity · MODRISS::SubStage: `psm.ph1.st3` · contained by **Deployment & Slice Framing**</small>

Establish IAM roles, KMS keys, secrets, and SSM parameters.

**Participating roles**

- Cloud Platform Engineer

**Task uses in this activity**

- **Configure IAM roles and policies** (TaskUse `psm.ph1.st3.t1`)
- **Provision KMS, secrets, and SSM** (TaskUse `psm.ph1.st3.t2`)

## Network & Identity

<small>Activity · MODRISS::Stage: `psm.ph2`</small>

Configure VPC networking and Cognito identity resources aligned to PIM auth model.

**Entry conditions**

- Deployment & Slice Framing complete

**Exit conditions**

- Network posture defined for workloads
- Identity resources match PIM auth model

**Participating roles**

- Cloud Platform Engineer

### Networking

<small>Activity · MODRISS::SubStage: `psm.ph2.st1` · contained by **Network & Identity**</small>

Configure VPC, subnets, endpoints, and security groups for workloads.

**Participating roles**

- Cloud Platform Engineer

**Task uses in this activity**

- **Configure VPC and subnets** (TaskUse `psm.ph2.st1.t1`)
- **Define endpoints and security groups** (TaskUse `psm.ph2.st1.t2`)

### Identity

<small>Activity · MODRISS::SubStage: `psm.ph2.st2` · contained by **Network & Identity**</small>

Configure Cognito user pools, clients, and identity pools.

**Participating roles**

- Cloud Platform Engineer

**Task uses in this activity**

- **Configure Cognito user pools** (TaskUse `psm.ph2.st2.t1`)
- **Configure clients, groups, and identity pools** (TaskUse `psm.ph2.st2.t2`)

## Storage & Messaging

<small>Activity · MODRISS::Stage: `psm.ph3`</small>

Provision durable storage and messaging resources matching PIM data and event channels.

**Entry conditions**

- Network & Identity complete

**Exit conditions**

- Durable stores match PIM data model
- Messaging matches PIM event channels

**Participating roles**

- Cloud Platform Engineer

### Durable Storage

<small>Activity · MODRISS::SubStage: `psm.ph3.st1` · contained by **Storage & Messaging**</small>

Provision DynamoDB tables and S3 buckets aligned to PIM data architecture.

**Participating roles**

- Cloud Platform Engineer

**Task uses in this activity**

- **Provision DynamoDB tables** (TaskUse `psm.ph3.st1.t1`)
- **Configure S3 buckets and policies** (TaskUse `psm.ph3.st1.t2`)

### Messaging

<small>Activity · MODRISS::SubStage: `psm.ph3.st2` · contained by **Storage & Messaging**</small>

Create SQS queues and SNS topics aligned to PIM event channels.

**Participating roles**

- Cloud Platform Engineer

**Task uses in this activity**

- **Create SQS queues** (TaskUse `psm.ph3.st2.t1`)
- **Configure SNS topics and subscriptions** (TaskUse `psm.ph3.st2.t2`)

## Event Fabric & Compute

<small>Activity · MODRISS::Stage: `psm.ph4`</small>

Configure EventBridge fabric and deploy Lambda compute matching PIM functions.

**Entry conditions**

- Storage & Messaging complete for slice

**Exit conditions**

- Event fabric matches PIM integration
- Compute matches PIM functions

**Participating roles**

- Cloud Platform Engineer

### Event Fabric

<small>Activity · MODRISS::SubStage: `psm.ph4.st1` · contained by **Event Fabric & Compute**</small>

Configure EventBridge buses, rules, schedules, pipes, and API destinations.

**Participating roles**

- Cloud Platform Engineer

**Task uses in this activity**

- **Configure EventBridge buses and rules** (TaskUse `psm.ph4.st1.t1`)
- **Configure schedules, pipes, and connections** (TaskUse `psm.ph4.st1.t2`)

### Compute

<small>Activity · MODRISS::SubStage: `psm.ph4.st2` · contained by **Event Fabric & Compute**</small>

Deploy Lambda functions with event source mappings and permissions.

**Participating roles**

- Cloud Platform Engineer

**Task uses in this activity**

- **Deploy Lambda functions** (TaskUse `psm.ph4.st2.t1`)
- **Configure event sources and permissions** (TaskUse `psm.ph4.st2.t2`)

## API & Orchestration

<small>Activity · MODRISS::Stage: `psm.ph5`</small>

Configure API Gateway exposure and Step Functions workflows with observability.

**Entry conditions**

- Event Fabric & Compute complete for slice

**Exit conditions**

- API Gateway matches PIM APIs
- Workflows and observability complete

**Participating roles**

- Cloud Platform Engineer

### API Gateway

<small>Activity · MODRISS::SubStage: `psm.ph5.st1` · contained by **API & Orchestration**</small>

Configure HTTP/REST/WebSocket APIs with routes, integrations, and authorizers.

**Participating roles**

- Cloud Platform Engineer

**Task uses in this activity**

- **Configure APIs and routes** (TaskUse `psm.ph5.st1.t1`)
- **Configure integrations and authorizers** (TaskUse `psm.ph5.st1.t2`)

### Workflow & Observability

<small>Activity · MODRISS::SubStage: `psm.ph5.st2` · contained by **API & Orchestration**</small>

Deploy Step Functions state machines and CloudWatch observability stack.

**Participating roles**

- Cloud Platform Engineer

#### Step Functions

<small>Activity · MODRISS::Stage: `psm.ph5.st2.ss1` · contained by **Workflow & Observability**</small>

Deploy state machines with ASL from PIM workflows.

**Participating roles**

- Cloud Platform Engineer

**Task uses in this activity**

- **Deploy state machines** (TaskUse `psm.ph5.st2.ss1.t1`)

#### CloudWatch Observability

<small>Activity · MODRISS::Stage: `psm.ph5.st2.ss2` · contained by **Workflow & Observability**</small>

Configure logs, metrics, alarms, and dashboards.

**Participating roles**

- Cloud Platform Engineer

**Task uses in this activity**

- **Configure CloudWatch logs and metrics** (TaskUse `psm.ph5.st2.ss2.t1`)
- **Configure alarms and dashboards** (TaskUse `psm.ph5.st2.ss2.t2`)

## Integration Views & Readiness

<small>Activity · MODRISS::Stage: `psm.ph6`</small>

Create integration relationship views, close traceability, and pass PSM EVL gate.

**Entry conditions**

- API & Orchestration complete for slice

**Exit conditions**

- PSM EVL passes
- Readiness gate approved
- PSM increment reviewed and improvement actions captured

**Participating roles**

- Process Reviewer

### Integration Views

<small>Activity · MODRISS::SubStage: `psm.ph6.st1` · contained by **Integration Views & Readiness**</small>

Create cross-resource relationship views for deployment wiring validation.

**Participating roles**

- Cloud Platform Engineer

**Task uses in this activity**

- **Create integration relationship views** (TaskUse `psm.ph6.st1.t1`)

### Trace & Readiness Gate

<small>Activity · MODRISS::SubStage: `psm.ph6.st2` · contained by **Integration Views & Readiness**</small>

Close trace links and production readiness before M2T generation.

**Participating roles**

- Process Reviewer

**Task uses in this activity**

- **Complete trace and readiness** (TaskUse `psm.ph6.st2.t1`)

### Increment Review & Adapt

<small>Activity · MODRISS::SubStage: `psm.ph6.st3` · contained by **Integration Views & Readiness**</small>

Review the AWS deployment slice, accept the increment, and adapt the next cycle.

**Participating roles**

- Process Reviewer

**Task uses in this activity**

- **Review and adapt PSM increment** (TaskUse `psm.ph6.st3.t1`)

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
