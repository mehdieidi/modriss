# Modeling Levels

MODRISS uses three related modeling languages. CIM describes the system's business and domain intent. PIM turns that intent into a provider-independent serverless architecture. AWS PSM records the concrete AWS design that can be used for project generation.

## CIM: Computation-Independent Model

CIM captures the problem and organizational intent without choosing a software architecture or cloud provider. It can describe:

- Goals, requirements, stakeholders, actors, and roles
- Capabilities, bounded-context candidates, domain entities, value objects, and aggregates
- Commands, queries, events, policies, decisions, and business processes
- Risks, assumptions, governance, privacy, compliance, and readiness findings

The CIM visual language uses domain and event-storming notation. The climate-relief sample shows a compliance-heavy, event-driven public-sector workload.

## PIM: Platform-Independent Model

PIM assigns architectural responsibilities while keeping the model independent of a particular provider. Typical elements include:

- Services, deployment units, functions, APIs, routes, and contracts
- Event channels, queues, topics, stores, workflows, and integrations
- Identities, secrets, policies, configuration, and environments
- Implementation profiles and platform-mapping readiness

PIM should retain traceability to business intent as teams settle service boundaries and interactions.

## PSM: Platform-Specific Model

The current PSM targets AWS. It refines the architecture into provider resources and deployment configuration, including:

- AWS Lambda, API Gateway, DynamoDB, and S3
- SQS, SNS, EventBridge, and Step Functions
- IAM, Cognito, secrets, networking, and observability
- SAM or CloudFormation stacks, stages, parameters, and mappings

Deep provider configuration is generally represented as contained detail instead of a topology node.

## Visual syntax

The combined Ecore metamodels define the model structure. Level-specific UI metadata supplies labels, icons, colors, palette categories, notation, viewpoints, and presentation rules. The backend merges these sources and serves the result through `GET /api/modeling/config`.

Every EClass has a visual role: `node`, `container`, `relationship`, `detail`, or `support`. Named viewpoints and openable containers make larger models easier to navigate while preserving the full model structure.
