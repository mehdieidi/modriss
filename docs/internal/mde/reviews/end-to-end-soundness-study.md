# End-to-end transformation soundness study

## Scope

This document records findings from an end-to-end validation of the frontend REST workflow for a
realistic serverless domain model. The study uses the browser API contract to create an isolated
project, import a CIM model, validate it, transform it to PIM, review the generated manual backlog,
refine it, transform it to AWS PSM, and review the second backlog.

## Findings recorded before implementation changes

### F-001: CIM XMI import rejects the checked-in CIM fixture

- **Observed:** `POST /api/cim/import?projectId=...&format=xmi` with
  `mde/samples/smart-makerspace-operations/smart-makerspace.cim.xmi` returns HTTP 400:
  `Uploaded model is not valid XMI: Value 'org.eclipse.emf.ecore.impl.DynamicEObjectImpl...`
  `BusinessEvent` ... `is not legal.`
- **Impact:** A model exported or supplied as XMI cannot reliably enter the frontend modeling
  workflow, so the promised import/transform path is blocked before CIM validation.
- **Root cause:** The XMI reader was configured with immediate ID-reference resolution. With the
  dynamic combined Ecore metamodel, a forward reference from a command to a later `BusinessEvent`
  could be inserted into an inverse reference list before the list had been populated, causing the
  EMF `IllegalValueException`.
- **Fix:** Load XMI with deferred ID-reference resolution and let EMF resolve the complete resource
  after parsing. Added a regression test that imports the checked-in Smart Makerspace fixture and
  verifies its CIM graph. The live frontend import path was then re-run successfully.

Further findings will be appended here before their implementation changes.

### F-012: Condition references are copied but incorrectly promoted to default transitions

- **Observed:** `ProcessTransition2WorkflowTransition` copies the executable expression from CIM
  `conditionRef`, but computes `defaultTransition` from the original CIM prose/expression fields.
  A transition with only `conditionRef` therefore becomes a PIM default transition even though it
  has a condition. In the climate-relief sample, both outgoing branches of each decision were
  consequently eligible to overwrite the ASL Choice `Default` path.
- **Impact:** The decision's branch conditions can be lost and the generated workflow can route
  every decision outcome to the last default target.
- **Fix plan:** Derive the default flag from the effective PIM `conditionExpression` after all CIM
  condition sources have been resolved, and add a regression assertion over the CIM-to-PIM output.

### F-013: Function-backed Choice states have no modeled evaluator-outcome mapping

- **Observed:** A PIM `ChoiceStep` can invoke a decision evaluator, but `WorkflowTransition` has no
  property that identifies the evaluator outcome selecting that transition. The transformation can
  invoke the evaluator and preserve its result, but cannot soundly connect the result to a branch.
- **Impact:** Falling back to input predicates would allow a workflow to branch on data that the
  evaluator did not authorize; omitting the mapping produces an invalid/incomplete Choice draft.
- **Fix plan:** Add an optional `decisionOutcome` field to PIM `WorkflowTransition`. Function-backed
  choices use it to emit JSONata conditions against `$.decisionEvaluation.outcome`; missing values
  remain blocking review items and are not silently converted into evaluator-independent branches.

### F-014: Choice evaluator inherits the business Choice output mapping

- **Observed:** The synthetic evaluator Task copies the ChoiceStep `outputMapping`. If a modeler
  supplies a mapping intended for the workflow Choice, it can filter the Lambda result before the
  evaluator outcome is stored at `$.decisionEvaluation`.
- **Impact:** A valid outcome mapping can still fail at runtime because the evaluator result has
  been projected away before branching.
- **Fix plan:** Keep the evaluator Task's output at `$` so its contract result is always available;
  the workflow Choice itself has no result-path field and any unsupported Choice mapping remains a
  reviewer concern.

### F-015: Unsupported wait expressions are emitted as zero-second waits without a blocker

- **Observed:** The PSM mapping emits `Seconds: 0` for any unsupported non-empty wait expression,
  but only creates a manual decision when the expression is empty.
- **Impact:** An unsupported business deadline can silently become an immediate transition.
- **Fix plan:** Treat a non-empty expression as valid only when it is a supported ISO-8601 duration;
  otherwise emit a blocking wait-semantics decision. The zero-second value remains a reviewable
  draft placeholder, never a deployment-ready interpretation.

### F-016: Multiple Choice defaults can silently overwrite one another

- **Observed:** The PIM-to-AWS-PSM transition loop assigned `AslChoiceState.nextState` once for
  every transition marked `defaultTransition`. If a PIM Choice had two defaults, iteration order
  determined which target was retained. An unresolved default target was also skipped without a
  target-specific blocker.
- **Impact:** The generated ASL could appear to have a valid default while implementing an
  arbitrary branch, and a missing default target could leave the Choice without the fallback the
  modeler specified.
- **Fix plan:** Require exactly one default transition, emit a blocking ambiguity decision when
  there are multiple defaults, and emit a blocking transition-target decision when the single
  default cannot be resolved. Do not select an arbitrary target in either invalid case.

### F-017: EVL critiques and incomplete workflow drafts were blocking synchronization

- **Observed:** Synchronization called EVL-backed generated-XMI validation and treated an
  incomplete evaluator-backed Choice with no yet-modeled outcome rules as a failed target. The
  same validation result also contained legitimate non-blocking Cognito and IAM critiques.
- **Impact:** A reviewer could not reach the PSM manual backlog to resolve the missing workflow
  semantics, and optional security guidance could be mistaken for a malformed transformation.
- **Fix plan:** Synchronization now validates generated JSON and XMI using Ecore structural
  conformance only. EVL remains available through the explicit model-validation workflow, while
  transformation readiness/manual decisions continue to block deployment for unresolved semantics.

### F-008: Choice decision evaluator functions were not executed

- **Observed:** CIM decision steps are transformed into PIM `ChoiceStep` elements with an
  `invokesFunction` reference to the generated decision-table evaluator. The PIM-to-PSM workflow
  transformation initially emitted only an ASL `Choice` state and ignored that function reference,
  so the decision evaluator Lambda was not reachable from the generated state machine.
- **Impact:** The workflow could branch on input fields without executing the modeled decision
  logic. This breaks behavioral traceability from the CIM business decision to executable AWS
  behavior.
- **Fix plan:** Generate an evaluator ASL `Task` state before each function-backed choice, bind it
  to the generated Lambda, and route incoming transitions to the evaluator while keeping the
  evaluator's `Next` edge pointed at the choice state.

### F-009: Generated Lambda scaffolds were not represented as implementation blockers

- **Observed:** Artifact generation produces source files for the modeled Lambda functions, but the
  generated business-logic region returns `NOT_IMPLEMENTED` until a developer refines it. The PSM
  readiness assessment did not create a corresponding reviewer task, so applying all transformation
  decisions could incorrectly suggest that the generated business behavior was deployable.
- **Impact:** The AWS resource wiring is present, but the CIM behavior is not executable until the
  generated handlers are implemented and tested. This is especially important for decision
  evaluator Lambdas used by ASL Choice states.
- **Fix plan:** Add a blocking, function-specific implementation task for generator-managed ZIP
  Lambda scaffolds. Keep the scaffold and protected regions intact, but make readiness explicitly
  depend on human implementation and verification.

### F-010: Decision evaluator output was disconnected from Choice semantics

- **Observed:** A function-backed PIM `ChoiceStep` was represented by an evaluator Lambda followed
  by an ASL `Choice`, but the Choice conditions still read the original input fields while the
  evaluator contract returns a `condition`/`outcome` result. With the default Step Functions task
  behavior, the task result replaces the state input, and the generated JSONata definition also
  incorrectly mixed JSONata with JSONPath-only `InputPath`/`OutputPath` fields. The PIM metamodel
  has no explicit decision-outcome-to-transition mapping, so the transformation cannot infer which
  returned outcome selects each branch.
- **Impact:** The evaluator could be invoked without affecting routing, or the subsequent Choice
  could fail because its input predicates were no longer present. Treating this workflow as
  executable would therefore overstate behavioral fidelity from the CIM decision table.
- **Fix plan:** Use a JSONPath state-machine default with an explicit JSONata query-language
  override only on Choice states, preserve evaluator results under `$.decisionEvaluation`, and
  create a blocking manual task requiring an explicit outcome-to-branch mapping (or an explicit
  decision to make the input predicates authoritative) before deployment.

### F-011: Ordinary Lambda task results could replace workflow context

- **Observed:** Function-backed ordinary workflow `Task` states had `InputPath`/`OutputPath` set
  to the root but no `ResultPath`. In AWS Step Functions, the default task result behavior replaces
  the state input. The PIM workflow steps in the live model still have `inputMapping` and
  `outputMapping` set to `TBD`, so the transformation cannot safely infer whether a function returns
  the complete workflow context or only a step result.
- **Impact:** A later task or Choice can lose fields produced by earlier steps. The generated ASL
  could therefore contain correct Lambda targets but still fail to execute the business process's
  data flow.
- **Fix plan:** Preserve the incoming context by default and store each unmodeled function result
  under a stable, per-step `$.stepResults.*` path. Add an actionable blocking task requiring the
  reviewer to model and verify the intended input/output mapping before deployment. Explicit PIM
  output mappings continue to override this conservative draft behavior.

### F-005: The 49 PSM warnings are genuine EVL critiques, not malformed-model errors

- **Observed:** Live `POST /api/psm/{id}/validate` returned `valid: true`, 49 warnings, and zero
  errors. The warning set is exactly two `CognitoAuthorizerShouldReferenceClients` critiques and
  47 `ProductionRoleShouldUsePermissionsBoundary` critiques. The referenced generated objects
  exist: two Cognito authorizers have no `CognitoUserPoolClient` entries, and all 47 production IAM
  roles have an empty `permissionsBoundaryArn`.
- **Impact:** The warnings are real findings on the generated PSM, but interpreting them as proof
  that the PIM→PSM transformation is structurally unsound would be incorrect. They identify
  organization-specific security decisions that the current PIM does not provide. They are also
  not represented by the 47 transformation readiness decisions, so a reviewer can reach
  `DEPLOYMENT_READY` while EVL still reports these critiques.
- **Root cause:** EVL semantic validation and transformation readiness are separate pipelines. The
  PSM transformation emits valid optional security fields without inventing clients or a
  permissions-boundary ARN, while the EVL critiques intentionally flag those omissions.
- **Disposition:** No synthetic Cognito clients or permissions-boundary ARN should be invented by
  the transformation. The warnings remain non-blocking EVL feedback and are exposed by the explicit
  PSM validation endpoint. Transformation readiness and semantic validation findings must remain
  visibly separate; a zero open transformation backlog must not be read as zero EVL findings.

### F-006: PIM executable task targets are dropped before ASL generation

- **Observed:** In the live PIM, 15 process/workflow `TaskStep` elements invoke concrete PIM
  functions. The generated PSM ASL states all contain only the generic string
  `arn:aws:states:::lambda:invoke`; none has `AslState.invokedResource` pointing to its generated
  `AwsLambdaFunction`. The final ASL therefore has no `FunctionName`/CloudFormation substitution
  for the Lambda integration. The PIM also contains three external-interaction steps with
  `invokesAdapter`, but the corresponding PSM states are `AslPassState`.
- **Impact:** The generated state machines do not identify which Lambda to execute. A Step
  Functions `lambda:invoke` integration requires a function target, and a `Pass` state silently
  skips the business operation. Consequently, the CIM process is not faithfully executable in
  AWS even though the PSM model and current EVL rules accept the generic resource string.
- **Root cause:** `workflow-security-config.etl` assigns `AslState.resource` to a service
  integration URI but never assigns `invokedResource` from `TaskStep.invokesFunction`. It has no
  executable PSM mapping for `TaskStep.invokesAdapter`.
- **Fix plan:** Bind function-backed task states to the generated Lambda resource, render the
  required intrinsic/substitution, and keep external adapter tasks explicitly review-blocked
  unless an executable adapter implementation is modeled. Add validation and regression coverage
  for target identity and placeholder states.

### F-007: Workflow timing and decision semantics are not preserved in ASL

- **Observed:** CIM wait durations such as `P5D`, `P10D`, and `PT4H` reach PIM as
  `WaitStep.conditionExpression`, but the PSM ASL renderer emits `"Seconds": 0` for every wait.
  Choice branches such as `eligible or discretionary review` and `evidence gap` are emitted as
  `Variable: $.condition` plus `StringEquals` against the whole prose expression. The ETL-side
  `AslDocument.content` additionally uses a nonstandard `Condition` field while the artifact
  renderer uses a different representation.
- **Impact:** Wait states proceed immediately instead of implementing the business deadlines.
  Choice branches compare against a field/value that the CIM does not define, so the intended
  business routing is not preserved. The in-model ASL content and generated artifact can also
  disagree.
- **Root cause:** The transformation does not convert provider-independent duration expressions to
  ASL wait fields, and it treats natural-language/boolean business conditions as JSONPath literal
  comparisons without a modeled expression strategy. ASL content is rendered in two inconsistent
  places.
- **Fix plan:** Convert supported ISO-8601/provider-independent durations to ASL `Seconds` and
  reject unsupported expressions with an actionable manual task. Use an explicit modeled ASL
  expression strategy for Choice rules (JSONata when configured, or a documented JSONPath
  comparison only when the source supplies a concrete path/operator/value), and use one canonical
  renderer for stored content and generated artifacts.

### F-002: Generated manual-decision titles are cut off without an ellipsis

- **Observed:** PIM manual tasks retain the complete question in `rationale`, but the user-facing
  `name`/`title` is truncated at 80 question characters by the CIM→PIM EOL helper. Examples end in
  fragments such as `... lint, f` and `... explicit `.
- **Impact:** The task list is scannable but several task titles are not grammatical and do not tell
  the reviewer what the omitted decision is. The full rationale is available, so this is a usability
  and review-quality defect rather than a lost semantic value.
- **Fix plan:** Keep the full question in the task metadata and make the shortened display title an
  intentional, visibly ellipsized summary. Preserve the full question in the generated readiness
  decision and ensure the frontend backlog continues to expose it as guidance.

### F-003: AWS PSM manual-task titles expose rule IDs instead of reviewer questions

- **Observed:** The PIM→AWS PSM output contains 47 required manual decisions, but the frontend-facing
  backlog titles are repeated identifiers such as `AWS_REGION_REQUIRED`,
  `LAMBDA_DLQ_TARGET_REQUIRED`, and `IAM_WILDCARD_REVIEW`. The full decision is only present in
  the rationale/question text.
- **Impact:** A reviewer cannot distinguish tasks from the backlog list without opening each item;
  repeated rule IDs are poor work-item titles even though the rationales are actionable and affected
  element IDs are present.
- **Fix plan:** Keep the stable rule ID in the decision/finding metadata, but use the full human
  question as the manual decision name so the existing platform backlog mirrors an informative title.
  Preserve affected elements and blocking severity.

### F-004: Regeneration resets reviewed generated backlog tasks

- **Observed:** After applying all 47 PSM tasks through the frontend update API, regenerating the
  same PSM from the reviewed PIM preserved the decision text, owner, and readiness flags, but
  rebuilt all 47 issue-board tasks as `OPEN`. The corrected question titles also changed task IDs
  because IDs were derived from display titles.
- **Impact:** Iterative modeling can show a reviewed model as deployment-ready while presenting the
  same decisions as new open work, and a wording improvement can lose task completion history.
- **Fix plan:** Give generated task IDs a title-independent semantic identity and make synchronization
  preserve reviewer state when an existing generated task matches by category, rationale, and
  affected elements. Add a regression test for a completed PSM task surviving regeneration.

## Findings resolved

- **F-001:** Resolved in `XmiModelImportService`; the Smart Makerspace CIM fixture now imports.
- **F-002:** Resolved in the CIM→PIM readiness helper; long titles now end in `...`, while the full
  question remains available to the reviewer.
- **F-003:** Resolved in the PIM→AWS PSM readiness helper; the rule ID remains stable metadata, and
  the backlog title now contains the actionable reviewer question.
- **F-004:** Resolved in the transformation synchronization coordinator; task completion metadata
  now survives regeneration and display-title changes.
- **F-006:** Resolved in the PIM-to-AWS-PSM workflow mapping; function-backed Task states now retain
  generated Lambda targets, while external adapter placeholders remain explicit blockers.
- **F-007:** Resolved in the workflow ASL mapping and artifact renderer; supported durations and
  concrete JSONata conditions are preserved in both persisted ASL and generated artifact files.
- **F-008:** Resolved in the PIM-to-AWS-PSM workflow mapping; every function-backed ChoiceStep now
  has a Lambda evaluator Task whose next state is the corresponding Choice state, and incoming
  transitions target the evaluator entry state.
- **F-009:** Resolved in the PIM-to-AWS-PSM readiness mapping; generator-managed ZIP Lambda
  scaffolds now produce blocking implementation tasks.
- **F-010:** Resolved in the ASL mapping and readiness boundary; JSONata is now scoped to Choice
  states, evaluator Task results are retained without replacing workflow input, and missing
  decision-outcome-to-branch mappings are explicit deployment blockers.
- **F-011:** Resolved in the ASL mapping and readiness boundary; ordinary function-backed tasks now
  preserve workflow context by default, retain their result under a stable per-step path, and emit
  blocking result-mapping review tasks when the PIM mapping is still `TBD`.
- **F-012:** Resolved in the CIM→PIM transition mapping; the default flag is now derived from the
  effective copied condition expression, including expressions supplied only through `conditionRef`.
- **F-013:** Resolved in the PIM workflow metamodel and ASL mapping; `WorkflowTransition.decisionOutcome`
  provides the explicit evaluator-outcome-to-branch contract, and unmapped evaluator-backed branches
  are omitted from the executable draft while the blocking review task remains open.
- **F-014:** Resolved in the evaluator state mapping; evaluator output is always retained at `$` before
  it is merged into `$.decisionEvaluation`, independently of the ChoiceStep's workflow mapping.
- **F-015:** Resolved in the wait mapping; unsupported or out-of-range non-empty duration expressions
  now produce a blocking wait-semantics decision instead of being treated as a valid zero-second wait.
- **F-016:** Resolved in the Choice transition mapping; multiple defaults are now blocking, and an
  unresolved single default target is reported instead of being silently ignored.
- **F-017:** Resolved in synchronization validation; generated targets must be structurally valid,
  while EVL critiques and intentionally incomplete, review-blocked workflow semantics remain
  visible through their respective validation/readiness workflows.

## Live workflow evidence

The frontend-compatible REST workflow was executed against an isolated guest project using the
Climate Relief Grants CIM fixture:

- CIM import succeeded; semantic and structural validation both returned valid with zero issues.
- CIM→PIM completed after six generated-change synchronization choices were accepted. The PIM is
  at revision 3 and contains 3 workflows with 30 workflow steps, including 15 function-backed
  `TaskStep`/`ChoiceStep` references. Its semantic and structural validations returned valid with
  zero issues. The 40 earlier CIM→PIM review tasks were applied.
- PIM→AWS PSM completed without unresolved synchronization conflicts after generated changes were
  accepted. The PSM is revision 10 and contains 88 persisted manual decisions/backlog items: 44
  reviewed `DONE` items and 44 `OPEN` blockers. The generated AWS wiring is present, and previously
  reviewed decisions remain `DONE` across regeneration.
- The 15 PIM function-backed task references become 12 ordinary Lambda-backed ASL `Task` states plus
  3 decision-evaluator `Task` states. Each evaluator invokes the Lambda generated from its PIM
  `ChoiceStep.invokesFunction` and then transitions to the corresponding ASL `Choice` state. The
  three workflows contain 3 ASL choices and 6 waits; the three supported waits preserve 5-day,
  4-hour, and 10-day durations as 432000, 14400, and 864000 seconds.
- The remaining 24 workflow tasks are genuine blockers: three external adapter execution choices,
  three event/callback wait-semantics choices, three missing choice default-path decisions, and
  three explicit decision-outcome-to-branch mapping tasks (one for each function-backed
  `ChoiceStep`), plus 12 ordinary function-task result-mapping reviews. The other 20 open tasks
  require implementation and tests for the generated Lambda business-logic scaffolds.
  They are intentionally not auto-applied because no correct domain implementation can be inferred
  from the transformation alone.
- A cross-level trace audit found no missing PSM targets and four source IDs that are intentional
  PSM-internal subscription links, not missing PIM sources. The remaining PIM-originated source IDs
  resolved against the PIM model.
- PSM structural validation returned valid with zero issues. Explicit EVL semantic validation
  reports three mandatory `ChoiceStateHasChoices` errors for the three intentionally unmapped
  evaluator-backed Choice states, plus 49 genuine non-blocking security critiques: two Cognito
  client-selection warnings and 47 IAM permissions-boundary warnings. The three workflow errors
  are expected until reviewers supply evaluator-outcome mappings; they no longer prevent the
  generated draft from reaching the PSM backlog.
- PSM-to-artifact generation succeeded as job
  `6e06eee9-30d6-48aa-a079-7305c339cf5d`, producing artifact
  `a60f2692-d72c-4b12-b1cb-53cf5488cf4e` with 346 files and no diagnostics. All three generated ASL
  files use top-level `QueryLanguage: JSONPath`; each function-backed Choice state overrides this
  with `QueryLanguage: JSONata`, has no JSONPath-only path fields, and is preceded by a
  Lambda-substituted evaluator task whose result is preserved at `$.decisionEvaluation`. The
  supported wait durations are present in the artifact files. Artifact generation proves the
  artifact pipeline is consistent; it does not waive the open implementation and workflow-design
  blockers.

The validation interpretation follows the official Epsilon separation of concerns: ETL performs
model transformation, EVL reports constraints/critiques and fixes, and EGL/EGX handles generation:
[Epsilon ETL](https://eclipse.dev/epsilon/doc/etl/),
[Epsilon EVL](https://eclipse.dev/epsilon/doc/evl/), and
[Epsilon language overview](https://eclipse.dev/epsilon/doc/).

The ASL findings use the AWS Step Functions definitions for
[state-machine structure](https://docs.aws.amazon.com/step-functions/latest/dg/statemachine-structure.html),
[JSONata/JSONPath data transformation](https://docs.aws.amazon.com/step-functions/latest/dg/transforming-data.html),
[Choice states](https://docs.aws.amazon.com/step-functions/latest/dg/state-choice.html), and
[ResultPath input/output behavior](https://docs.aws.amazon.com/step-functions/latest/dg/input-output-resultpath.html).
