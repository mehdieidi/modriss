# Model-to-text transformation and code-generation reference

This reference explains how the AWS PSM becomes a generated project: infrastructure templates, contracts, Lambda code, tests, scripts, CI/CD workflows, documentation, trace reports, and review artifacts. The executable source is EGX for scheduling and EGL for rendering; this documentation explains the design role of each generation rule without treating generated text as a second model.

## Generation is a controlled projection

The generator starts from one `AWSPSM!AwsPsmModel` root, prepares a shared emission context, and then applies EGX rules. Each rule decides whether an artifact applies, chooses a target path, passes model objects to an EGL template, and records what happened. Some files are pure projections and are overwritten; others merge generated regions with protected human content.

## Inventory

| Category                  | EGX rules | EGL templates | Reference                                                  |
| ------------------------- | --------: | ------------: | ---------------------------------------------------------- |
| CI/CD                     |         3 |             3 | [CI/CD generation](categories/cicd.md)                     |
| Contracts                 |        11 |             5 | [Contracts generation](categories/contracts.md)            |
| Documentation and reports |        17 |            17 | [Documentation and reports generation](categories/docs.md) |
| Infrastructure            |        11 |             7 | [Infrastructure generation](categories/infrastructure.md)  |
| Lambda runtime            |        10 |            10 | [Lambda runtime generation](categories/lambda.md)          |
| Scripts                   |        10 |            10 | [Scripts generation](categories/scripts.md)                |
| Tests and fixtures        |         8 |             8 | [Tests and fixtures generation](categories/tests.md)       |

## Generation lifecycle

1. The EGX pre block requires exactly one AWS PSM root, captures the shared root and emission context, and collects blocking/manual issues.
2. Generation rules run according to their guards and invoke EGL templates with model-specific parameters.
3. Each rule records artifact path, artifact kind, generator identity, model keys, ownership mode, and protected regions.
4. The EGX post block reports the number of planned artifacts and manual issues so a run cannot appear complete merely because a process exited.

## Merge, overwrite, and protected regions

| Mode                              | Meaning                                                                   | Modeler consequence                                                                           |
| --------------------------------- | ------------------------------------------------------------------------- | --------------------------------------------------------------------------------------------- |
| `merge: false`, `overwrite: true` | The generated file is a complete projection of the current model.         | Manual edits will be replaced; change the model or add a deliberate extension mechanism.      |
| `merge: true`, `overwrite: true`  | Generated regions are refreshed while protected regions are preserved.    | Put durable custom code, assertions, diagrams, or team notes only in named protected regions. |
| Guarded rule                      | The artifact exists only when its model feature or emission flag applies. | A missing file may be correct; inspect the guard and generation report before repairing it.   |

## What to check when output is missing

Check the single-root precondition, then the rule guard, target-path helper, and generation flags (`enableOpenApi`, `enableAsl`, `enableTests`, `enableCiCd`, and `enableDocs`). If the rule ran, inspect `generated/trace/artifact-trace.json`, `generated/reports/generation-report.md`, and `generated/reports/manual-actions.md`. These artifacts distinguish an omitted rule, an unresolved model decision, a path collision, and a template/rendering problem.

## Security and ownership boundary

Generated infrastructure and code are not proof that the deployment is secure or that business behavior is complete. Review IAM rationale, security reports, manual actions, protected regions, and generated tests before deployment. The assistant mutation boundary remains structural Ecore/EMF validation through `ModelService.validateStructural(...)`; semantic EVL validation and generation are explicit model/user workflows.

## Useful resources

- [Eclipse Epsilon EGL](https://eclipse.dev/epsilon/doc/egl/) — template syntax and model access.
- [Eclipse Epsilon EGX](https://eclipse.dev/epsilon/doc/egx/) — generation-rule orchestration.
- [AWS SAM documentation](https://docs.aws.amazon.com/serverless-application-model/latest/developerguide/what-is-sam.html) — generated serverless infrastructure context.
- [OpenAPI Specification](https://spec.openapis.org/oas/latest.html) — generated HTTP contract context.
- [JSON Schema specification](https://json-schema.org/specification) — generated data and validation contract context.
- [Amazon States Language](https://docs.aws.amazon.com/step-functions/latest/dg/concepts-amazon-states-language.html) — generated workflow JSON context.
- [AWS Lambda Go handler model](https://docs.aws.amazon.com/lambda/latest/dg/golang-handler.html) — generated handler context.
- [GitHub Actions documentation](https://docs.github.com/en/actions) — generated CI/CD workflow context.
