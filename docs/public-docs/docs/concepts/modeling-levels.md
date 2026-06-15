# Modeling Levels

## CIM: Computation-Independent Model

CIM captures the problem and organizational intent without committing to a software architecture or
cloud provider.

Typical concepts include:

- Goals, requirements, stakeholders, actors, and roles
- Capabilities, bounded-context candidates, domain entities, value objects, and aggregates
- Commands, queries, events, policies, decisions, and business processes
- Risks, assumptions, governance, privacy, compliance, and readiness findings

The CIM visual language favors recognizable domain and event-storming notation. The climate-relief
sample demonstrates a compliance-heavy, event-driven public-sector workload.

## PIM: Platform-Independent Model

PIM expresses a serverless architecture without selecting a provider.

Typical concepts include:

- Services, deployment units, functions, APIs, routes, and contracts
- Event channels, queues, topics, stores, workflows, and integrations
- Identities, secrets, policies, configuration, and environments
- Implementation profiles and platform-mapping readiness

The PIM should preserve business traceability while resolving architectural responsibilities and
interaction patterns.

## PSM: Platform-Specific Model

PSM refines the architecture into deployable AWS resources.

Typical concepts include:

- AWS Lambda and API Gateway
- DynamoDB and S3
- SQS, SNS, and EventBridge
- Step Functions
- IAM, Cognito, secrets, networking, and observability
- SAM or CloudFormation stacks, stages, parameters, and mappings

Deep provider configuration is generally edited as contained detail rather than displayed as a
topology node.

## Visual Syntax

The complete structural configuration is derived from combined Ecore metamodels. Level-specific UI
metadata adds labels, icons, colors, palette categories, notation, viewpoints, and presentation
rules. The backend merges both sources and serves the result from `GET /api/modeling/config`.

Every EClass is assigned a visual role:

- `node`
- `container`
- `relationship`
- `detail`
- `support`

Named viewpoints and openable containers keep large models usable without weakening formal
coverage.
