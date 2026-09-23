# PIM DSML

The Platform-Independent Model is the architectural middle level of MODRISS. It refines business intent into a serverless design that can be reviewed in terms of services, functions, APIs, data, contracts, integrations, workflows, policies, identity, configuration, and external systems. PIM is specific enough to reason about reachability, state access, failure handling, and deployment boundaries. It remains independent of Lambda, API Gateway, DynamoDB, or another cloud provider's resource vocabulary.

The PIM metamodel keeps several decisions separate because they answer different questions. A `Function` describes computation, a `ServerlessService` describes responsibility, and a `DeploymentUnit` describes release ownership. A `Flow` describes an interaction, while an `EventChannel` or `Api` describes a delivery boundary. A `DataStore` records storage intent, while `DataModel`, `DataField`, and `AccessPattern` explain the shape and use of that data.

## How to use this reference

Begin with the root and deployment pages. Decide which PIM elements belong to a service and which belong to a deployment unit before refining detailed integrations. Use compute, API, contracts, data, and workflow pages together because a function is meaningful only in relation to its entry points, state, payloads, and failure paths. Use policy, security, configuration, and external pages to record cross-cutting decisions that should survive provider mapping.

Each class section includes declared attributes, accepted values or examples, and relationships. Attributes inherited from the shared kernel are documented once and apply to every subtype. The reference describes the relationship between architecture objects as well as the objects themselves, since those connections are what ETL uses to construct a provider-specific design.

## Module map

| Module                | Use it for                                                                                                                                                 | Reference                                                                     |
| --------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------- | ----------------------------------------------------------------------------- |
| `pim-root.emf`        | The PIM root gathers provider-independent serverless architecture, contracts, policies, integrations, data access, security, configuration, and readiness. | [PIM model root](pim-root.md)                                                 |
| `pim-deployment.emf`  | Deployment concepts divide the provider-independent architecture into services, units, environments, and explicit platform-mapping decisions.              | [Services, deployment units, and platform mapping](pim-deployment.md)         |
| `pim-compute.emf`     | Compute elements describe what runs and why without committing to Lambda, containers, or another provider-specific runtime.                                | [Provider-independent compute](pim-compute.md)                                |
| `pim-api.emf`         | API concepts capture externally visible operations, routes, and error behavior before API Gateway or another delivery technology is selected.              | [Provider-independent APIs](pim-api.md)                                       |
| `pim-integration.emf` | Integration concepts describe asynchronous channels, schedules, subscriptions, and flow intent independently of AWS resource names.                        | [Channels, flows, and integrations](pim-integration.md)                       |
| `pim-data.emf`        | Data concepts capture storage intent, data shapes, change streams, indexes, access patterns, and the link between reads/writes and compute.                | [Stores, data models, and access patterns](pim-data.md)                       |
| `pim-contracts.emf`   | Contracts make payload shape and compatibility explicit for APIs, events, messages, and function boundaries.                                               | [Schemas and contracts](pim-contracts.md)                                     |
| `pim-policy.emf`      | Policy concepts capture non-functional and behavioral decisions that shape reliability, security, cost, observability, and runtime behavior.               | [Architecture, business, resilience, and operational policies](pim-policy.md) |
| `pim-workflow.emf`    | Workflow concepts describe orchestration, branching, parallelism, waiting, human approval, compensation, and escalation without embedding ASL syntax.      | [Provider-independent workflows](pim-workflow.md)                             |
| `pim-external.emf`    | External concepts identify systems outside the modeled application and the adapters needed to call or receive from them.                                   | [External endpoints and adapters](pim-external.md)                            |
| `pim-security.emf`    | Security concepts model identities, principals, permissions, and authorization expectations before they are mapped to AWS IAM or Cognito.                  | [Identity, principals, and authorization](pim-security.md)                    |
| `pim-config.emf`      | Configuration concepts separate deploy-time values, runtime environment variables, secrets, and credential requirements from application logic.            | [Configuration, secrets, and credentials](pim-config.md)                      |
| `pim-types.emf`       | Enumerations in this module are the PIM vocabulary for architecture style, execution, contracts, data access, and support decisions.                       | [PIM enumerations](pim-types.md)                                              |

## Model-level guidance

- Treat the Emfatic declarations as the abstract-syntax authority. EVL adds semantic constraints; it does not introduce attributes that are absent from the metamodel.
- Give every externally reachable operation exactly one backend integration. A route, function, contract, and authorization policy should agree on what enters the system and what response or event leaves it.
- Describe state use through `DataAccess` and `AccessPattern`. The storage choice should be explainable from the way the architecture reads, writes, searches, and protects information.
- Keep asynchronous behavior explicit. Queues, topics, event buses, subscriptions, schedules, routing rules, retry policies, and dead-letter decisions carry different operational meanings.
- Use workflows when the business progression requires orchestration, waiting, human approval, compensation, or escalation. Do not hide those decisions inside an opaque function description.
- Keep provider-neutral intent in PIM. AWS names, ARNs, SAM or CloudFormation properties, and service-specific operational decisions belong in AWS PSM.

## Additional resources

- [AWS Serverless Application Lens](https://docs.aws.amazon.com/wellarchitected/latest/serverless-applications-lens/welcome.html)
- [AWS Prescriptive Guidance: serverless patterns](https://aws.amazon.com/serverless/patterns/)
- [Eclipse Epsilon ETL](https://eclipse.dev/epsilon/doc/etl/)
