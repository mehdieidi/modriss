# AWS PSM DSML

The AWS Platform-Specific Model turns the provider-independent architecture into explicit AWS resources, CloudFormation/SAM settings, integration wiring, and operational controls.

## How to use this reference

Start with the root page, then follow the module that owns the class you are modeling. Each class section includes declared attributes, accepted values or examples, and relationships. Attributes inherited from the shared kernel are documented once and apply to every subtype.

## Module map

| Module                     | Use it for                                                                                                                                                                 | Reference                                                                   |
| -------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | --------------------------------------------------------------------------- |
| `awspsm-root.emf`          | The AWS PSM root is the deployment-oriented container. It owns stages, SAM stacks, AWS policies, documents, and the derived resource index.                                | [AWS PSM model root](awspsm-root.md)                                        |
| `awspsm-core.emf`          | Core classes provide the CloudFormation/SAM vocabulary shared by every AWS service resource, including logical IDs, tags, parameters, expressions, and lifecycle controls. | [AWS resources, SAM stacks, and CloudFormation support](awspsm-core.md)     |
| `awspsm-security.emf`      | These classes map security intent into IAM documents, roles, KMS keys, Secrets Manager secrets, and Systems Manager parameters.                                            | [IAM, KMS, Secrets Manager, and SSM](awspsm-security.md)                    |
| `awspsm-networking.emf`    | Networking classes make VPC attachment, subnet placement, endpoints, security groups, and ingress/egress decisions explicit.                                               | [VPC networking and security groups](awspsm-networking.md)                  |
| `awspsm-identity.emf`      | Cognito classes describe user pools, clients, OAuth, recovery, schema attributes, groups, domains, and identity pools.                                                     | [Amazon Cognito identity resources](awspsm-identity.md)                     |
| `awspsm-storage.emf`       | Storage classes expose the AWS details needed for DynamoDB key design and S3 governance, lifecycle, notification, replication, and encryption.                             | [DynamoDB and S3 resources](awspsm-storage.md)                              |
| `awspsm-messaging.emf`     | Messaging classes configure SQS queues, redrive behavior, queue policies, SNS topics, subscriptions, filters, and topic policies.                                          | [SQS and SNS resources](awspsm-messaging.md)                                |
| `awspsm-events.emf`        | EventBridge classes cover buses, rules, targets, schedules, pipes, API destinations, connections, input mapping, and retry policy.                                         | [Amazon EventBridge resources](awspsm-events.md)                            |
| `awspsm-compute.emf`       | Lambda classes describe code packaging, runtime, environment, event sources, permissions, destinations, layers, URLs, tracing, and operational safeguards.                 | [AWS Lambda resources](awspsm-compute.md)                                   |
| `awspsm-api.emf`           | API Gateway classes refine provider-independent APIs into HTTP, REST, and WebSocket resources, routes, integrations, stages, authorizers, and usage controls.              | [Amazon API Gateway resources](awspsm-api.md)                               |
| `awspsm-workflow.emf`      | Workflow classes represent Step Functions state machines and their ASL documents, state types, branches, map processors, retries, catches, logging, and tracing.           | [Step Functions and Amazon States Language](awspsm-workflow.md)             |
| `awspsm-observability.emf` | Observability classes turn operational intent into CloudWatch log groups, metric filters, alarms, composite alarms, dimensions, and dashboards.                            | [CloudWatch logs, metrics, alarms, and dashboards](awspsm-observability.md) |
| `awspsm-integrations.emf`  | Integration views are explicit wiring records used to inspect and validate how AWS resources connect across service boundaries.                                            | [Cross-resource integration views](awspsm-integrations.md)                  |
| `awspsm-enums.emf`         | Enumerations in this module constrain AWS deployment choices to values understood by the provider-specific model and generators.                                           | [AWS PSM enumerations](awspsm-enums.md)                                     |

## Model-level guidance

- Treat the Emfatic declarations as the abstract-syntax authority. EVL adds semantic constraints; it does not introduce attributes that are absent from the metamodel.
- Use containment (`val`) for objects owned by the containing element and references (`ref`) for shared or cross-cutting concepts.
- Keep provider-neutral intent in CIM/PIM. Put AWS names, ARNs, SAM/CloudFormation properties, and service-specific operational decisions in AWS PSM.
- Preserve traceability and rationale when refining or transforming a model. They are part of the engineering record, not merely editor decoration.

## Additional resources

- [AWS Serverless Application Model](https://docs.aws.amazon.com/serverless-application-model/latest/developerguide/what-is-sam.html)
- [AWS CloudFormation resource and property reference](https://docs.aws.amazon.com/AWSCloudFormation/latest/UserGuide/aws-template-resource-type-ref.html)
- [AWS Lambda developer guide](https://docs.aws.amazon.com/lambda/latest/dg/welcome.html)
- [AWS Step Functions Amazon States Language](https://docs.aws.amazon.com/step-functions/latest/dg/concepts-amazon-states-language.html)
