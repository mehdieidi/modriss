# PIM DSML

The Platform-Independent Model refines CIM intent into a generic serverless architecture that can be reviewed without binding the design to AWS resource classes.

## How to use this reference

Start with the root page, then follow the module that owns the class you are modeling. Each class section includes declared attributes, accepted values or examples, and relationships. Attributes inherited from the shared kernel are documented once and apply to every subtype.

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
- Use containment (`val`) for objects owned by the containing element and references (`ref`) for shared or cross-cutting concepts.
- Keep provider-neutral intent in CIM/PIM. Put AWS names, ARNs, SAM/CloudFormation properties, and service-specific operational decisions in AWS PSM.
- Preserve traceability and rationale when refining or transforming a model. They are part of the engineering record, not merely editor decoration.

## Additional resources

- [AWS Serverless Application Lens](https://docs.aws.amazon.com/wellarchitected/latest/serverless-applications-lens/welcome.html)
- [AWS Prescriptive Guidance: serverless patterns](https://aws.amazon.com/serverless/patterns/)
- [Eclipse Epsilon ETL](https://eclipse.dev/epsilon/doc/etl/)
