# PIM to AWS PSM ETL

Entry point:

```bash
java -jar tools/mde-etl-cli/target/mde-etl-cli-0.0.1-SNAPSHOT.jar run \
  --module mde/transformations/pim-to-awspsm/pim-to-awspsm.etl \
  --source-model path/to/input-pim.xmi \
  --target-model path/to/output-awspsm.xmi \
  --source-metamodel mde/metamodels/pim/pim-combined.ecore \
  --target-metamodel mde/metamodels/psm/psm-combined.ecore \
  --source-name PIM \
  --source-aliases PIM,DEPLOYMENT,COMPUTE,API,CONTRACTS,DATA,INTEGRATION,WORKFLOW,EXTERNAL,POLICY,SECURITY,CONFIG,PIMTYPES \
  --target-name AWSPSM \
  --target-aliases AWSPSM,AWSPSMCORE,AWSPSMCOMPUTE,AWSPSMAPI,AWSPSMSTORAGE,AWSPSMMESSAGING,AWSPSMEVENTS,AWSPSMWORKFLOW,AWSPSMSECURITY,AWSPSMIDENTITY,AWSPSMOBSERVABILITY,AWSPSMINTEGRATIONS,AWSPSMNETWORKING,AWSPSMENUMS,KERNEL \
  --overwrite \
  --verbose
```

Keep `KERNEL` on the target aliases only. The source and target combined Ecore files both contain
the shared kernel package, and assigning the same alias to both sides creates an ambiguous Epsilon
model group when the transformation creates target-side trace, readiness, and structured-document
elements.

## Workflow state mapping

The transformation represents ASL state kinds through concrete EClasses, matching the CIM and PIM
step-class pattern. `StartStep` and `PassStep` become `AslPassState`; `TaskStep`, `ChoiceStep`,
`WaitStep`, `ParallelStep`, `MapStep`, `SuccessEndStep`, and `FailureEndStep` become their
corresponding `Asl*State` classifiers. A task without an executable target deliberately degrades to
`AslPassState` and emits an actionable blocking manual decision. External-adapter tasks use the
`WORKFLOW_EXTERNAL_ADAPTER_IMPLEMENTATION_REQUIRED` decision so a placeholder cannot be mistaken
for an implemented business operation.

Function-backed `TaskStep` instances retain a reference to their generated
`AwsLambdaFunction`; the ASL renderers emit a CloudFormation definition substitution for that
function ARN. Nested workflows use the Step Functions optimized integration and carry a
substituted `StateMachineArn` in `Arguments`. A task backed only by an external adapter has no
safe executable target in the current PSM contract, so it becomes an explicit blocking review
decision rather than a silently executable-looking operation.

Function-backed `ChoiceStep` instances are represented by an evaluator `AslTaskState` followed by
the `AslChoiceState`; incoming transitions target the evaluator entry state. The evaluator is bound
to the Lambda generated from `ChoiceStep.invokesFunction`, preserving decision-table execution
before branching. Each non-default transition must set `WorkflowTransition.decisionOutcome` to the
literal outcome returned by the evaluator. The generated Choice condition then reads
`$states.input.decisionEvaluation.outcome`. Missing outcome mappings remain blocking review items;
the transformation does not silently branch on the pre-evaluator input predicates. Generator-managed
ZIP Lambda code is still a scaffold, so each such function also gets a blocking implementation-and-test
task before deployment readiness can be claimed.

Function-backed ordinary `TaskStep` instances preserve the incoming workflow context by default.
When `WorkflowStep.outputMapping` is still `TBD`, the generated draft stores the Lambda result under
the stable per-step `$.stepResults.*` path and emits a blocking result-mapping review task. An
explicit output mapping is honored directly. This prevents the AWS default `ResultPath: "$"` from
silently replacing the business context while still requiring a reviewer to verify the data contract.

The state-machine default is JSONPath so ordinary states can use `InputPath`, `OutputPath`, and
`ResultPath`; Choice states explicitly opt into JSONata for supported concrete boolean conditions.
The two query-language modes are not mixed within a state. Function-backed choice evaluators retain
their result at `$.decisionEvaluation`, but the PIM currently has no explicit outcome-to-transition
mapping, so each such choice remains a blocking modeling task until the reviewer supplies that
mapping (or explicitly chooses input predicates as the authoritative logic). Wait durations are
converted from supported ISO-8601 day/hour/minute/second values to ASL `Seconds`. Event/callback
waits without a duration remain blocking review decisions because the PIM does not yet define their
completion signal. Prose or unsupported expressions remain review blockers, and every choice must
have a default branch.

`AslState` is abstract. Transformation and generation code must determine a state's ASL `Type`
from its concrete EClass, not from an enum-valued feature. The ETL and artifact generator both
consume the same modeled fields (`invokedResource`, `argumentsJson`, `timeoutSeconds`, and
choice/default references) so the persisted PSM ASL summary and generated template definition
remain consistent.

# Iterative synchronization

Normal platform execution generates into an empty temporary AWS PSM resource; preloaded-target
helpers are not used for preservation. IDs are SHA-256-derived in the `pim-to-awspsm` namespace
from immutable PIM IDs and distinct target roles such as Lambda, IAM role, and log configuration.

The raw ETL result (`NewGenerated`) is merged into the user-refined AWS PSM (`Working`) against the
previous raw ETL result (`Base`). Safe incoming changes and additions are applied, user additions
and independent refinements remain, and delete/change or change/change cases become resumable
conflicts. Pending sessions fingerprint the Working revision so stale decisions cannot be applied.

The baseline is always the untouched raw output of the most recent successfully accepted ETL
generation. It is never the user-refined merged working model.
