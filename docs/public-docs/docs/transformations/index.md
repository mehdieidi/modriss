# Model-to-model transformation reference

This reference explains how the project refines CIM business meaning into provider-independent PIM architecture and then binds PIM architecture to the AWS PSM. It is written around the actual Epsilon Transformation Language (ETL) rules, not around an idealized class mapping: guards, helper-driven construction, deferred relationship resolution, trace identifiers, placeholders, and manual decisions are all part of the transformation contract.

## What a transformation rule means here

An ETL rule takes a source metaclass instance and may create one or more target instances. Some rules are unconditional; others route subtypes or provider capabilities through guards. A rule can also create secondary objects, attach relationships, emit trace links, or create a readiness/manual decision. A target that appears incomplete immediately after its rule may be deliberately completed by a later post phase.

## Transformation inventory

| Pipeline      | Rules | Purpose                                                                                                                                         | Reference                                     |
| ------------- | ----: | ----------------------------------------------------------------------------------------------------------------------------------------------- | --------------------------------------------- |
| CIM → PIM     |    48 | Preserve business intent as portable services, contracts, data, workflows, policies, integrations, and readiness evidence.                      | [CIM → PIM rules](cim-to-pim/index.md)        |
| PIM → AWS PSM |    32 | Bind portable architecture to AWS stages, stacks, Lambda, APIs, storage, messaging, events, workflows, security, configuration, and operations. | [PIM → AWS PSM rules](pim-to-awspsm/index.md) |

## Pipeline lifecycle

The entry ETL files validate the root count and initialize caches before imported rules run. Post phases then perform the work that cannot be safely done during isolated source-to-target creation: resolving cross-references, materializing late routes, attaching resources to stacks, generating permissions and relationship views, and validating semantic closure.

| Pipeline phase      | What it protects                                                                    |
| ------------------- | ----------------------------------------------------------------------------------- |
| Pre-validation      | Prevents an ambiguous or missing source root from producing a partial target model. |
| Rule execution      | Creates target elements while preserving source meaning and traceability.           |
| Deferred resolution | Resolves relationships whose target did not exist when the source rule ran.         |
| Readiness closure   | Turns unresolved choices, blockers, and evidence gaps into reviewable model facts.  |
| Semantic validation | Checks the completed target model with the target-level EVL profile.                |

## Entry and post phases

| File                                                             | Phase                                | Explanation                                                                                                                                                                                                                                      |
| ---------------------------------------------------------------- | ------------------------------------ | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| `mde/transformations/cim-to-pim/boundaries-security.etl:209`     | `post EnsureServiceExists`           | Closes service ownership after the boundary rules have run, ensuring every generated PIM service candidate has a usable architectural home.                                                                                                      |
| `mde/transformations/cim-to-pim/cim-to-pim.etl:9`                | `pre ValidateInput`                  | Checks that exactly one source root exists, initializes transformation caches, and records the start of the pipeline. A failure here means there is no unambiguous model to refine.                                                              |
| `mde/transformations/cim-to-pim/domain-data.etl:141`             | `post AttachRelationshipDataFields`  | Completes relationship-derived fields after schemas and data stores exist, so domain relationships can refer to concrete PIM fields rather than guessed names.                                                                                   |
| `mde/transformations/cim-to-pim/integration-deployment.etl:1262` | `post LinkAndDerive`                 | Connects generated functions, routes, stores, channels, and relationships after individual rules have created their objects. This is where cross-rule correspondence becomes architecture-level connectivity.                                    |
| `mde/transformations/cim-to-pim/integration-deployment.etl:1325` | `post DeploymentAndConfiguration`    | Completes service membership and deployment/configuration intent, including values that depend on the complete PIM graph rather than a single source element.                                                                                    |
| `mde/transformations/cim-to-pim/integration-deployment.etl:1406` | `post ValidationAndReadinessClosure` | Runs the final CIM-to-PIM semantic closure and records readiness evidence after all generated relationships and policies are available.                                                                                                          |
| `mde/transformations/cim-to-pim/process-policy.etl:577`          | `post AttachWorkflowDetails`         | Adds the process-specific task, transition, condition, and error details that require all workflow nodes to have been created first.                                                                                                             |
| `mde/transformations/cim-to-pim/root-scaffolding.etl:219`        | `post InitialReadinessChecks`        | Seeds readiness evidence from CIM conditions such as transformable behavior, service-boundary candidates, data classification, and authorization decisions. These checks intentionally expose gaps instead of blocking object creation silently. |
| `mde/transformations/pim-to-awspsm/pim-to-awspsm.etl:9`          | `pre ValidatePimInput`               | Checks that exactly one PIM root exists, initializes AWS-resolution caches, and records the start of provider binding. It prevents a partially bound AWS model from being produced from an ambiguous PIM input.                                  |
| `mde/transformations/pim-to-awspsm/pim-to-awspsm.etl:17`         | `post ResolveAndValidate`            | Runs the PIM-to-PSM fix-up sequence: API route materialization, AWS relationship resolution, workflow targets, SQS redrive, IAM statements, resource placement, KMS alignment, relationship views, readiness, and final PSM constraints.         |

## Traceability and manual decisions

Trace calls such as `TR-070`, `TR-080`, `Function2AwsLambdaFunction`, or `DataStore2DynamoDbTable` are not comments for developers; they are the audit trail connecting a target element to the rule and source decision that produced it. Manual decisions are equally intentional: they identify information that cannot be inferred safely, such as a runtime, unsupported store service, EFS mount, DLQ, or authorization choice. Resolve them through the readiness/review workflow rather than weakening the generated target until the warning disappears.

## Important boundary

Transformation execution should happen after source structural and semantic validation. After the target graph is complete, run the target-level EVL validation explicitly. Chatbot assistant apply, repair, and commit paths use structural Ecore/EMF conformance only through `ModelService.validateStructural(...)`; EVL and transformation semantic validation remain explicit model/user validation workflows outside that mutation gate.

## Further reading

- [Epsilon Transformation Language](https://eclipse.dev/epsilon/doc/etl/): ETL rules, guards, and transformation execution.
- [DSML reference](../dsml/index.md): source and target classes, attributes, and relationships.
- [EVL semantic validation reference](../evl/index.md): constraints applied before/after transformation.
- [Validation, transformation, and generation](../concepts/pipeline.md): the validation and transformation steps in the modeling framework.
- [Model-to-text and code-generation reference](../generation/index.md): follow the EGX/EGL rules that turn AWS PSM into infrastructure, code, tests, scripts, CI/CD, and reports.
