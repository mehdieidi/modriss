# Provider-independent workflows

The workflow module represents durable control flow as a graph of owned steps and transitions. Step subtypes distinguish entry, successful and failed termination, executable tasks, choices, parallel branches, mapped iteration, waiting, and state-only pass operations. A transition carries its own condition, evaluator outcome, and default-path decision, so routing does not have to be inferred from list order.

Tasks may invoke a function, adapter, nested workflow, or human task, with callback configuration for externally completed work. Error handlers, retry settings, timeouts, compensation, escalation, and completion events express the failure and waiting behavior around that work. The PIM keeps these semantics independent of Amazon States Language while retaining enough structure for the AWS transformation to build a state machine.

Source: `mde/metamodels/pim/pim-workflow.emf`.

## `Workflow`

A provider-independent orchestration model. Its steps, transitions, error handling, waits, human tasks, compensation, and escalation describe the process before it becomes a Step Functions state machine.

Direct supertypes: `TraceableElement`, `DeployableElement`, `InvocationTarget`, `WorkflowTarget`, `SubscriptionTarget`, `RoutingTarget`, `FlowEndpoint`, `PolicyTarget`, `ProtectedResource`, `ConfigurableElement`. Inherited attributes and marker capabilities are documented in the [shared kernel](../shared-kernel.md); this section lists every attribute declared by this class.

### Declared attributes

| Attribute                    | Type and multiplicity | What it captures and why it exists                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                   | Accepted values and example                                                                                                                                 |
| ---------------------------- | --------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `executionSemantics`         | `String` [1]          | The provider-independent execution contract for the workflow, for example, whether it is durable orchestration, event choreography, or callback-driven coordination. It keeps the orchestration decision visible before ASL or another provider language is selected. Transformation role: ETL rule `BusinessProcess2Workflow` in `mde/transformations/cim-to-pim/process-policy.etl` assigns or materializes this feature while refining `Workflow`.                                                                                                                                                                                                                                                                                                                                                                | A free-form `String`, subject to this class's semantic meaning and any EVL constraints. Example: `Workflow Execution Semantics`.                            |
| `longRunning`                | `Boolean` [1]         | Whether the workflow is expected to remain active across a long business interval. It drives the need for durable state, timeout/expiry policy, compensation, and human-task handling rather than being a direct AWS Express/Standard switch. Transformation role: ETL rule `BusinessProcess2Workflow` in `mde/transformations/cim-to-pim/process-policy.etl` assigns or materializes this feature while refining `Workflow`.                                                                                                                                                                                                                                                                                                                                                                                        | Either `true` or `false`. Example: `false`.                                                                                                                 |
| `stateful`                   | `Boolean` [1]         | Whether workflow progress itself is business-relevant state that must survive individual function invocations. PIM EVL uses it, together with compensation, to require idempotency policy because restart/retry can otherwise repeat side effects. Semantic validation: `WorkflowHasAtLeastOneEndStep` (workflow has at least one end step) in `mde/validation/pim/rules/workflow.evl` the feature participates in a semantic validation condition. `StatefulWorkflowNeedsIdempotency` (stateful workflow needs idempotency) in `mde/validation/pim/rules/workflow.evl` the feature participates in a semantic validation condition. Transformation role: ETL rule `BusinessProcess2Workflow` in `mde/transformations/cim-to-pim/process-policy.etl` assigns or materializes this feature while refining `Workflow`. | Either `true` or `false`. Example: `false`.                                                                                                                 |
| `compensationRequired`       | `Boolean` [1]         | Whether the workflow must reverse completed business actions when a later step fails. It is the PIM counterpart of CIM compensation intent and is used in readiness checks to require idempotency and explicit compensation policy. Semantic validation: `StatefulWorkflowNeedsIdempotency` (stateful workflow needs idempotency) in `mde/validation/pim/rules/workflow.evl` the feature participates in a semantic validation condition. Transformation role: ETL rule `BusinessProcess2Workflow` in `mde/transformations/cim-to-pim/process-policy.etl` assigns or materializes this feature while refining `Workflow`. ETL rule `BusinessProcess2Workflow` in `mde/transformations/cim-to-pim/process-policy.etl` reads or derives this feature while refining `Workflow`.                                        | Either `true` or `false`. Example: `true`.                                                                                                                  |
| `humanApprovalRequired`      | `Boolean` [1]         | Whether a person must authorize progress. This makes the human-in-the-loop boundary explicit so timeout, assignee, escalation, audit, and completion-event behavior can be modeled instead of hidden in a function. Transformation role: ETL rule `BusinessProcess2Workflow` in `mde/transformations/cim-to-pim/process-policy.etl` assigns or materializes this feature while refining `Workflow`. ETL rule `Workflow2StepFunctionStateMachine` in `mde/transformations/pim-to-awspsm/workflow-security-config.etl` reads or derives this feature while refining `Workflow`.                                                                                                                                                                                                                                        | Either `true` or `false`. Example: `true`.                                                                                                                  |
| `expectedMaxDurationSeconds` | `Integer` [1]         | The provider-independent upper expectation for the complete workflow, in seconds. It gives resilience and operational design a numeric budget; it is populated from temporal constraints when CIM-to-PIM can interpret them and is not the timeout of an individual step. Transformation role: ETL rule `BusinessProcess2Workflow` in `mde/transformations/cim-to-pim/process-policy.etl` assigns or materializes this feature while refining `Workflow`.                                                                                                                                                                                                                                                                                                                                                            | A numeric `Integer` value; use the unit or boundary documented for this attribute. Example: `30`.                                                           |
| `expressLowLatencyRequired`  | `Boolean` [1]         | Whether the workflow has a latency/cost profile that favors an express-style execution option. It records an architectural preference for PSM mapping; it must not be used to erase durability or execution-history requirements implied by `stateful` or `longRunning`. Transformation role: ETL rule `BusinessProcess2Workflow` in `mde/transformations/cim-to-pim/process-policy.etl` assigns or materializes this feature while refining `Workflow`. ETL rule `Workflow2StepFunctionStateMachine` in `mde/transformations/pim-to-awspsm/workflow-security-config.etl` reads or derives this feature while refining `Workflow`.                                                                                                                                                                                   | Either `true` or `false`. Example: `true`.                                                                                                                  |
| `workflowKind`               | `WorkflowKind` [1]    | The controlled orchestration category for the workflow. It lets transformation distinguish ordinary orchestration, saga-like coordination, approval flow, and related patterns before choosing concrete Step Functions state types. Transformation role: ETL rule `BusinessProcess2Workflow` in `mde/transformations/cim-to-pim/process-policy.etl` assigns or materializes this feature while refining `Workflow`.                                                                                                                                                                                                                                                                                                                                                                                                  | Exactly one of: `ORCHESTRATION`, `LONG_RUNNING_PROCESS`, `SAGA`, `HUMAN_APPROVAL`, `BATCH_COORDINATION`, `EVENT_ROUTING_PROCESS`. Example: `ORCHESTRATION`. |

### Relationships

| Relationship                            | Kind and multiplicity                           | Meaning in the model                                                                                                                                                                                                                                                |
| --------------------------------------- | ----------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `steps` → `WorkflowStep`                | containment, [+]; opposite `workflow`           | The `steps` containment on `Workflow` owns the execution nodes that make up the workflow. The `WorkflowStep` objects are owned by `Workflow` and remain part of its model subtree. Its opposite `workflow` exposes the same connection from the target side.        |
| `transitions` → `WorkflowTransition`    | containment, [*]; opposite `workflow`           | The `transitions` containment on `Workflow` owns the routing edges between workflow steps. The `WorkflowTransition` objects are owned by `Workflow` and remain part of its model subtree. Its opposite `workflow` exposes the same connection from the target side. |
| `service` → `ServerlessService`         | reference; read-only, [1]; opposite `workflows` | Derived back-reference to the service boundary responsible for this element. It mirrors the opposite containment and is not set independently on `Workflow`.                                                                                                        |
| `observability` → `ObservabilityConfig` | reference, [?]                                  | Associates `Workflow` with the telemetry and operational-visibility requirements. The referenced `ObservabilityConfig` remains independently owned and may be reused elsewhere in the model.                                                                        |
| `resilience` → `ResiliencePolicy`       | reference, [?]                                  | Associates `Workflow` with the retry, fallback, and failure-handling policy. The referenced `ResiliencePolicy` remains independently owned and may be reused elsewhere in the model.                                                                                |
| `idempotency` → `IdempotencyPolicy`     | reference, [?]                                  | Associates `Workflow` with the duplicate-processing guarantee. The referenced `IdempotencyPolicy` remains independently owned and may be reused elsewhere in the model.                                                                                             |

## `WorkflowStep`

The common execution and control unit of a PIM workflow. It carries order, responsibility, optionality, and transition context for its specialized step types.

Direct supertypes: `TraceableElement`, `FlowEndpoint`, `PolicyTarget`, `ProtectedResource`. Inherited attributes and marker capabilities are documented in the [shared kernel](../shared-kernel.md); this section lists every attribute declared by this class.

### Declared attributes

| Attribute        | Type and multiplicity | What it captures and why it exists                                                                                                                                                                                                                                                                                                                                                                                                                                                   | Accepted values and example                                                                                                                                       |
| ---------------- | --------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ | ----------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `orderIndex`     | `Integer` [1]         | The stable order used when a workflow has an ordered list of steps but explicit transitions do not fully determine a next state. CIM-to-PIM copies the source process index; PIM-to-AWS sorts steps by this value to select a deterministic `StartAt` and to infer sequential `Next` links. It is not a branch condition and must not be used to encode alternatives.                                                                                                                | A non-negative integer used for deterministic step ordering. Examples: `0`, `1`, `2`. Do not use it to encode a branch; model branches with `WorkflowTransition`. |
| `timeoutSeconds` | `Integer` [1]         | The maximum wait for this individual step, in seconds, at the provider-independent level. Temporal-constraint refinement can populate it; PIM validation asks for retry/catch handling or rationale when it is set; PIM-to-AWS emits it as ASL `TimeoutSeconds` or wait `Seconds`. Semantic validation: `TimedStepShouldUseTimeoutPolicy` (timed step should use timeout policy) in `mde/validation/pim/rules/workflow.evl` the related value or object must be explicitly provided. | `0` for no explicit step timeout, or a positive integer number of seconds such as `30` or `900`.                                                                  |
| `inputMapping`   | `String` [1]          | The JSONPath/JSONata-style projection that supplies this step's input from the workflow state. It is the contract between preceding output and the step's action; PIM-to-AWS maps it to ASL `InputPath` after normalizing absent/TBD values.                                                                                                                                                                                                                                         | A JSONPath/JSONata projection such as `$.order` or an intentional `TBD` placeholder during transformation review.                                                 |
| `outputMapping`  | `String` [1]          | The projection or destination describing what this step contributes back to workflow state. For task/function steps the PIM-to-AWS transformation uses an explicit mapping as the ASL `ResultPath`; leaving it unresolved means the transformation must preserve state conservatively rather than guess where to write the result.                                                                                                                                                   | A JSONPath/JSONata result location such as `$.reservation` or an intentional `TBD` placeholder during transformation review.                                      |

### Relationships

| Relationship                     | Kind and multiplicity                       | Meaning in the model                                                                                                                                                                  |
| -------------------------------- | ------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `retry` → `RetryPolicy`          | containment, [?]                            | The `retry` containment on `WorkflowStep` keeps the retry policies applied to this state. The `RetryPolicy` objects are owned by `WorkflowStep` and remain part of its model subtree. |
| `catchHandlers` → `ErrorHandler` | containment, [*]; opposite `step`           | Owns the recovery paths owned by this workflow step. The contained `ErrorHandler` records form part of the `WorkflowStep` model subtree and follow its lifecycle.                     |
| `workflow` → `Workflow`          | reference; read-only, [1]; opposite `steps` | Derived back-reference to the orchestration represented by this flow or owned step. It mirrors the opposite containment and is not set independently on `WorkflowStep`.               |

## `StartStep`

The explicit entry node of a workflow graph. It establishes where execution begins and may associate a compensation policy that governs reversal if later work fails.

Direct supertypes: `WorkflowStep`. Inherited attributes and marker capabilities are documented in the [shared kernel](../shared-kernel.md); this section lists every attribute declared by this class.

### Declared attributes

This class declares no attributes of its own. It inherits the attributes of its supertype, if any.

### Relationships

| Relationship                          | Kind and multiplicity | Meaning in the model                                                                                                                                                                  |
| ------------------------------------- | --------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `compensation` → `CompensationPolicy` | reference, [?]        | Associates `StartStep` with the reversal policy for already completed work. The referenced `CompensationPolicy` remains independently owned and may be reused elsewhere in the model. |

## `SuccessEndStep`

A terminal workflow node representing successful completion. Incoming transitions to this node make the successful paths explicit for validation and state-machine generation.

Direct supertypes: `WorkflowStep`. Inherited attributes and marker capabilities are documented in the [shared kernel](../shared-kernel.md); this section lists every attribute declared by this class.

### Declared attributes

This class declares no attributes of its own. It inherits the attributes of its supertype, if any.

### Relationships

This class declares no direct relationships.

## `FailureEndStep`

A terminal workflow node representing unsuccessful completion. It gives failure paths a deliberate destination instead of leaving termination implicit in an error handler or missing transition.

Direct supertypes: `WorkflowStep`. Inherited attributes and marker capabilities are documented in the [shared kernel](../shared-kernel.md); this section lists every attribute declared by this class.

### Declared attributes

This class declares no attributes of its own. It inherits the attributes of its supertype, if any.

### Relationships

This class declares no direct relationships.

## `TaskStep`

A workflow step that performs work through a function, adapter, or nested workflow. It is the bridge between orchestration structure and an executable target.

Direct supertypes: `WorkflowStep`. Inherited attributes and marker capabilities are documented in the [shared kernel](../shared-kernel.md); this section lists every attribute declared by this class.

### Declared attributes

This class declares no attributes of its own. It inherits the attributes of its supertype, if any.

### Relationships

| Relationship                            | Kind and multiplicity | Meaning in the model                                                                                                                                                                                            |
| --------------------------------------- | --------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `invokesFunction` → `Function`          | reference, [?]        | The `invokesFunction` reference on `TaskStep` identifies the function executed by this workflow step. A `Function` can remain independently owned and can participate in other parts of the model.              |
| `invokesAdapter` → `ExternalAdapter`    | reference, [?]        | The `invokesAdapter` reference on `TaskStep` identifies the external adapter used by this step. An `ExternalAdapter` can remain independently owned and can participate in other parts of the model.            |
| `nestedWorkflow` → `Workflow`           | reference, [?]        | The `nestedWorkflow` reference on `TaskStep` identifies the workflow invoked as a nested process. A `Workflow` can remain independently owned and can participate in other parts of the model.                  |
| `humanTask` → `HumanTask`               | reference, [?]        | Associates `TaskStep` with the human work item performed at this step. The referenced `HumanTask` remains independently owned and may be reused elsewhere in the model.                                         |
| `compensation` → `CompensationPolicy`   | reference, [?]        | Associates `TaskStep` with the reversal policy for already completed work. The referenced `CompensationPolicy` remains independently owned and may be reused elsewhere in the model.                            |
| `callbackConfig` → `CallbackTaskConfig` | containment, [?]      | The `callbackConfig` containment on `TaskStep` attaches the configuration record represented by callback config. The `CallbackTaskConfig` objects are owned by `TaskStep` and remain part of its model subtree. |

## `ChoiceStep`

A workflow branch selected by a condition or decision expression. It exposes routing logic that would otherwise be hidden inside generated state-machine JSON.

Direct supertypes: `WorkflowStep`. Inherited attributes and marker capabilities are documented in the [shared kernel](../shared-kernel.md); this section lists every attribute declared by this class.

### Declared attributes

| Attribute             | Type and multiplicity | What it captures and why it exists                                                                                                                                                                                                                                                                                                                                                                                                                   | Accepted values and example                                                                                  |
| --------------------- | --------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------ |
| `conditionExpression` | `String` [1]          | The predicate evaluated to choose an outgoing workflow transition. CIM decision conditions are copied here, and PIM-to-AWS uses the expression when creating an ASL choice/evaluator state; it is the decision logic, not explanatory text for the diagram. Transformation role: ETL rule `DecisionStep2WorkflowChoiceStep` in `mde/transformations/cim-to-pim/process-policy.etl` assigns or materializes this feature while refining `ChoiceStep`. | A provider-independent boolean expression, for example `$.order.total >= 1000` or `$.decision = 'APPROVED'`. |

### Relationships

| Relationship                   | Kind and multiplicity | Meaning in the model                                                                                                                                                                                 |
| ------------------------------ | --------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `condition` → `Expression`     | containment, [?]      | The `condition` containment on `ChoiceStep` provides the predicate used to choose or qualify this path. The `Expression` objects are owned by `ChoiceStep` and remain part of its model subtree.     |
| `invokesFunction` → `Function` | reference, [?]        | The `invokesFunction` reference on `ChoiceStep` identifies the function executed by this workflow step. A `Function` can remain independently owned and can participate in other parts of the model. |

## `ParallelStep`

A workflow step that starts independent branches together. Its branch references and join behavior capture the concurrency decision at the provider-independent level.

Direct supertypes: `WorkflowStep`. Inherited attributes and marker capabilities are documented in the [shared kernel](../shared-kernel.md); this section lists every attribute declared by this class.

### Declared attributes

This class declares no attributes of its own. It inherits the attributes of its supertype, if any.

### Relationships

| Relationship                  | Kind and multiplicity                  | Meaning in the model                                                                                                                                                                                                                                                  |
| ----------------------------- | -------------------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `branches` → `ParallelBranch` | containment, [*]; opposite `ownerStep` | The `branches` containment on `ParallelStep` owns the parallel or mapped workflow branches. The `ParallelBranch` objects are owned by `ParallelStep` and remain part of its model subtree. Its opposite `ownerStep` exposes the same connection from the target side. |

## `MapStep`

A workflow step that applies a nested process to items in a collection. It holds the collection, item mapping, concurrency, and result behavior needed for later provider mapping.

Direct supertypes: `WorkflowStep`. Inherited attributes and marker capabilities are documented in the [shared kernel](../shared-kernel.md); this section lists every attribute declared by this class.

### Declared attributes

This class declares no attributes of its own. It inherits the attributes of its supertype, if any.

### Relationships

| Relationship                   | Kind and multiplicity | Meaning in the model                                                                                                                                                                                  |
| ------------------------------ | --------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `mapConfig` → `MapStateConfig` | containment, [?]      | The `mapConfig` containment on `MapStep` attaches the collection-processing configuration to the map state. The `MapStateConfig` objects are owned by `MapStep` and remain part of its model subtree. |

## `WaitStep`

A provider-independent workflow step that delays execution. Its duration and reason are architecture decisions because they affect retries, cost, human response, and long-running behavior.

Direct supertypes: `WorkflowStep`. Inherited attributes and marker capabilities are documented in the [shared kernel](../shared-kernel.md); this section lists every attribute declared by this class.

### Declared attributes

| Attribute             | Type and multiplicity | What it captures and why it exists                                                                                                                                                                                                                                                                                                                                                                                     | Accepted values and example                                                                                                                              |
| --------------------- | --------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `conditionExpression` | `String` [1]          | The provider-independent timing or callback expression for the wait. PIM-to-AWS attempts to interpret it as a supported ASL wait duration and creates a blocker/manual decision when the expression cannot be safely represented. Transformation role: ETL rule `WaitLikeStep2WorkflowWaitStep` in `mde/transformations/cim-to-pim/process-policy.etl` assigns or materializes this feature while refining `WaitStep`. | A duration/callback expression such as `PT15M`, `300s`, or a documented callback condition that the PSM transformation can interpret or flag for review. |
| `waitReason`          | `String` [1]          | Why the workflow is intentionally paused. It preserves the business interpretation of a wait after CIM transformation, helping a modeller choose between a timed wait, callback token, event wait, or a missing transition. Transformation role: ETL rule `WaitLikeStep2WorkflowWaitStep` in `mde/transformations/cim-to-pim/process-policy.etl` assigns or materializes this feature while refining `WaitStep`.       | A free-form `String`, subject to this class's semantic meaning and any EVL constraints. Example: `Wait Step Wait Reason`.                                |

### Relationships

| Relationship               | Kind and multiplicity | Meaning in the model                                                                                                                                                                         |
| -------------------------- | --------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `condition` → `Expression` | containment, [?]      | The `condition` containment on `WaitStep` provides the predicate used to choose or qualify this path. The `Expression` objects are owned by `WaitStep` and remain part of its model subtree. |

## `PassStep`

A workflow node that advances control without invoking computation or an external system. Its inherited input and output mappings can reshape workflow state between substantive steps.

Direct supertypes: `WorkflowStep`. Inherited attributes and marker capabilities are documented in the [shared kernel](../shared-kernel.md); this section lists every attribute declared by this class.

### Declared attributes

This class declares no attributes of its own. It inherits the attributes of its supertype, if any.

### Relationships

This class declares no direct relationships.

## `WorkflowTransition`

A directed edge between two workflow steps. Its condition or decision outcome selects the edge, while `defaultTransition` marks the fallback path when no guarded alternative matches.

Direct supertypes: `TraceableElement`. Inherited attributes and marker capabilities are documented in the [shared kernel](../shared-kernel.md); this section lists every attribute declared by this class.

### Declared attributes

| Attribute             | Type and multiplicity | What it captures and why it exists                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                              | Accepted values and example                                                                                                                                               |
| --------------------- | --------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `conditionExpression` | `String` [1]          | The condition under which the transition is eligible. It is copied from CIM's effective transition condition and is later rendered as the branch predicate or sequential routing decision in the provider-specific workflow. Semantic validation: `ConditionalTransitionShouldHaveConditionUnlessDefault` (conditional transition should have condition unless default) in `mde/validation/pim/rules/workflow.evl` the value must be present and non-blank. Transformation role: ETL rule `ProcessTransition2WorkflowTransition` in `mde/transformations/cim-to-pim/process-policy.etl` assigns or materializes this feature while refining `WorkflowTransition`.                                               | A free-form `String`, subject to this class's semantic meaning and any EVL constraints. Example: `$.status == 'READY'`.                                                   |
| `decisionOutcome`     | `String` [1]          | The literal result expected from a function-backed choice evaluator, such as `APPROVED` or `REJECTED`. PIM-to-AWS compares this value in the generated ASL choice logic; leaving it empty intentionally marks the branch as incomplete for review rather than inventing a comparison.                                                                                                                                                                                                                                                                                                                                                                                                                           | A literal outcome returned by the choice evaluator, such as `APPROVED`, `REJECTED`, or `NEEDS_REVIEW`; use an empty value only while the branch is explicitly incomplete. |
| `defaultTransition`   | `Boolean` [1]         | Marks the transition used when no conditional branch matches. CIM-to-PIM sets it when the source transition has no effective condition, and PIM validation uses it to allow a branch without `conditionExpression`; it is the fallback route, not merely a visual default. Semantic validation: `ConditionalTransitionShouldHaveConditionUnlessDefault` (conditional transition should have condition unless default) in `mde/validation/pim/rules/workflow.evl` the value must be present and non-blank. Transformation role: ETL rule `ProcessTransition2WorkflowTransition` in `mde/transformations/cim-to-pim/process-policy.etl` assigns or materializes this feature while refining `WorkflowTransition`. | Either `true` or `false`. Use `true` for the fallback branch that receives control when no conditional transition matches.                                                |

### Relationships

| Relationship               | Kind and multiplicity                             | Meaning in the model                                                                                                                                                                                             |
| -------------------------- | ------------------------------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `condition` → `Expression` | containment, [?]                                  | The `condition` containment on `WorkflowTransition` provides the predicate used to choose or qualify this path. The `Expression` objects are owned by `WorkflowTransition` and remain part of its model subtree. |
| `workflow` → `Workflow`    | reference; read-only, [1]; opposite `transitions` | Derived back-reference to the orchestration represented by this flow or owned step. It mirrors the opposite containment and is not set independently on `WorkflowTransition`.                                    |
| `source` → `WorkflowStep`  | reference, [1]                                    | The `source` reference on `WorkflowTransition` marks the origin of a directed relationship. A `WorkflowStep` can remain independently owned and can participate in other parts of the model.                     |
| `target` → `WorkflowStep`  | reference, [1]                                    | The `target` reference on `WorkflowTransition` marks the destination of a directed relationship. A `WorkflowStep` can remain independently owned and can participate in other parts of the model.                |

## `ErrorHandler`

A workflow response to a named failure. It connects error matching to a next step, retry or catch behavior, and the business recovery path.

Direct supertypes: `TraceableElement`. Inherited attributes and marker capabilities are documented in the [shared kernel](../shared-kernel.md); this section lists every attribute declared by this class.

### Declared attributes

| Attribute          | Type and multiplicity | What it captures and why it exists                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                 | Accepted values and example                                                                                                       |
| ------------------ | --------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ | --------------------------------------------------------------------------------------------------------------------------------- |
| `errorSelector`    | `String` [1]          | The error names or pattern this handler owns. It prevents one recovery action from catching unrelated failures and becomes the selection input for generated catch rules. Semantic validation: `ErrorHandlerShouldSelectErrors` (error handler should select errors) in `mde/validation/pim/rules/workflow.evl` the value must be present and non-blank. Transformation role: ETL rule `ExceptionScenario2ErrorHandler` in `mde/transformations/cim-to-pim/process-policy.etl` assigns or materializes this feature while refining `ErrorHandler`.                                                                 | A free-form `String`, subject to this class's semantic meaning and any EVL constraints. Example: `Error Handler Error Selector`.  |
| `recoveryAction`   | `String` [1]          | The human-readable recovery intent, retry, compensate, route to support, or terminate safely. EVL requires it with the selector, and it gives PIM-to-PSM a reasoned action instead of a provider-specific catch with no business meaning. Semantic validation: `ErrorHandlerShouldSelectErrors` (error handler should select errors) in `mde/validation/pim/rules/workflow.evl` the value must be present and non-blank. Transformation role: ETL rule `ExceptionScenario2ErrorHandler` in `mde/transformations/cim-to-pim/process-policy.etl` assigns or materializes this feature while refining `ErrorHandler`. | A free-form `String`, subject to this class's semantic meaning and any EVL constraints. Example: `Error Handler Recovery Action`. |
| `continueWorkflow` | `Boolean` [1]         | Whether recovery should return control to the workflow after handling the error. The flag distinguishes a recover-and-continue path from a terminal failure and shapes the target step/ASL catch behavior. Transformation role: ETL rule `ExceptionScenario2ErrorHandler` in `mde/transformations/cim-to-pim/process-policy.etl` assigns or materializes this feature while refining `ErrorHandler`.                                                                                                                                                                                                               | Either `true` or `false`. Example: `false`.                                                                                       |

### Relationships

| Relationship                   | Kind and multiplicity                               | Meaning in the model                                                                                                                                                                              |
| ------------------------------ | --------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `step` → `WorkflowStep`        | reference; read-only, [1]; opposite `catchHandlers` | Derived back-reference to the workflow step that owns this error handler. It mirrors the opposite containment and is not set independently on `ErrorHandler`.                                     |
| `nextStep` → `WorkflowStep`    | reference, [?]                                      | The `nextStep` reference on `ErrorHandler` identifies the workflow step reached after this step. A `WorkflowStep` can remain independently owned and can participate in other parts of the model. |
| `handlerFunction` → `Function` | reference, [?]                                      | Associates `ErrorHandler` with the function used to perform error recovery. The referenced `Function` remains independently owned and may be reused elsewhere in the model.                       |

## `ParallelBranch`

One owned subgraph executed by a `ParallelStep`. Its local steps and transitions preserve each concurrent path as a complete graph with traceable nodes and edges.

Direct supertypes: `TraceableElement`. Inherited attributes and marker capabilities are documented in the [shared kernel](../shared-kernel.md); this section lists every attribute declared by this class.

### Declared attributes

This class declares no attributes of its own. It inherits the attributes of its supertype, if any.

### Relationships

| Relationship                         | Kind and multiplicity                          | Meaning in the model                                                                                                                                                                                  |
| ------------------------------------ | ---------------------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `ownerStep` → `ParallelStep`         | reference; read-only, [1]; opposite `branches` | Derived back-reference to the parallel step that owns this branch. It mirrors the opposite containment and is not set independently on `ParallelBranch`.                                              |
| `steps` → `WorkflowStep`             | containment, [+]                               | The `steps` containment on `ParallelBranch` owns the execution nodes that make up the workflow. The `WorkflowStep` objects are owned by `ParallelBranch` and remain part of its model subtree.        |
| `transitions` → `WorkflowTransition` | containment, [*]                               | The `transitions` containment on `ParallelBranch` owns the routing edges between workflow steps. The `WorkflowTransition` objects are owned by `ParallelBranch` and remain part of its model subtree. |

## `MapStateConfig`

The iteration contract of a `MapStep`. It identifies the input collection, item projection, concurrency, distributed-execution choice, result writer, and the branch used to process each item.

Direct supertypes: `TraceableElement`. Inherited attributes and marker capabilities are documented in the [shared kernel](../shared-kernel.md); this section lists every attribute declared by this class.

### Declared attributes

| Attribute          | Type and multiplicity | What it captures and why it exists                                                                                                                                                                                                                                                                                                                                  | Accepted values and example                                                                                           |
| ------------------ | --------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | --------------------------------------------------------------------------------------------------------------------- |
| `itemsPath`        | `String` [1]          | The path in the workflow input that contains the collection to process. It is the data boundary of a map step and maps directly to the provider-specific iterator/items path; a wrong path changes which business records are processed. Generation role: The artifact generator references this feature in `mde/generation/awspsm-to-artifacts/lib/contracts.eol`. | A free-form `String`, subject to this class's semantic meaning and any EVL constraints. Example: `/orders/{orderId}`. |
| `itemSelectorJson` | `String` [1]          | The per-item input construction applied before each map iteration. It lets the workflow pass the item together with context such as correlation or parent identifiers, rather than forcing every worker to reconstruct that context. Generation role: The artifact generator references this feature in `mde/generation/awspsm-to-artifacts/lib/contracts.eol`.     | A free-form `String`, subject to this class's semantic meaning and any EVL constraints. Example: `{"enabled":true}`.  |
| `maxConcurrency`   | `Integer` [1]         | The maximum number of map iterations allowed to run at once. It is an architecture-level load and cost guard; PSM maps it to the AWS map concurrency property and must preserve it when the source system cannot tolerate unbounded fan-out.                                                                                                                        | A numeric `Integer` value; use the unit or boundary documented for this attribute. Example: `1`.                      |
| `distributed`      | `Boolean` [1]         | Whether the map needs distributed execution semantics for large-scale or long-running item processing. The choice affects the PSM state type/configuration and should be made from workload characteristics, not as a synonym for ordinary parallelism.                                                                                                             | Either `true` or `false`. Example: `false`.                                                                           |
| `resultWriterJson` | `String` [1]          | The configuration for where map results are written when they should not remain entirely in the parent execution state. It captures the output-storage decision so PSM can produce the corresponding distributed-map result writer instead of dropping results or overgrowing state.                                                                                | A free-form `String`, subject to this class's semantic meaning and any EVL constraints. Example: `{"enabled":true}`.  |

### Relationships

| Relationship                       | Kind and multiplicity | Meaning in the model                                                                                                                                               |
| ---------------------------------- | --------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| `itemProcessor` → `ParallelBranch` | containment, [1]      | Owns the subgraph executed for every mapped item. The contained `ParallelBranch` records form part of the `MapStateConfig` model subtree and follow its lifecycle. |

## `CallbackTaskConfig`

The callback protocol for a task that pauses until external completion. It records where the task token is stored, which route may return it, the accepted completion events, and the applicable timeout.

Direct supertypes: `TraceableElement`. Inherited attributes and marker capabilities are documented in the [shared kernel](../shared-kernel.md); this section lists every attribute declared by this class.

### Declared attributes

| Attribute       | Type and multiplicity | What it captures and why it exists                                                                                                                                                                                                 | Accepted values and example                                                                                           |
| --------------- | --------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | --------------------------------------------------------------------------------------------------------------------- |
| `taskTokenPath` | `String` [1]          | The path at which the orchestration callback token is placed in the task input. A worker/external actor uses that token to resume the waiting task; the field therefore defines the callback contract, not an arbitrary JSON path. | A free-form `String`, subject to this class's semantic meaning and any EVL constraints. Example: `/orders/{orderId}`. |

### Relationships

| Relationship                      | Kind and multiplicity | Meaning in the model                                                                                                                                                                              |
| --------------------------------- | --------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `callbackRoute` → `RouteEndpoint` | reference, [?]        | Associates `CallbackTaskConfig` with the endpoint through which external completion returns. The referenced `RouteEndpoint` remains independently owned and may be reused elsewhere in the model. |
| `completionEvents` → `EventType`  | reference, [*]        | Associates `CallbackTaskConfig` with the event contracts accepted as completion evidence. The referenced `EventType` remains independently owned and may be reused elsewhere in the model.        |
| `timeout` → `TimeoutPolicy`       | reference, [?]        | Associates `CallbackTaskConfig` with the applicable execution or waiting limit. The referenced `TimeoutPolicy` remains independently owned and may be reused elsewhere in the model.              |

## `EscalationPolicy`

The rule for reassigning or raising an unfinished human task. It states when escalation occurs, the elapsed-time threshold, and the principals who receive the escalated responsibility.

Direct supertypes: `TraceableElement`. Inherited attributes and marker capabilities are documented in the [shared kernel](../shared-kernel.md); this section lists every attribute declared by this class.

### Declared attributes

| Attribute        | Type and multiplicity | What it captures and why it exists                                                                                                                                                             | Accepted values and example                                                                                             |
| ---------------- | --------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ----------------------------------------------------------------------------------------------------------------------- |
| `escalationRule` | `String` [1]          | The condition that turns an overdue or unresolved human task into an escalation. It describes the business escalation decision and is evaluated together with `afterSeconds` and `escalateTo`. | A free-form `String`, subject to this class's semantic meaning and any EVL constraints. Example: `$.status == 'READY'`. |
| `afterSeconds`   | `Integer` [1]         | How long the human task may remain unresolved before escalation, measured in seconds. It is a timing boundary for people/processes rather than the execution timeout of the worker function.   | A numeric `Integer` value; use the unit or boundary documented for this attribute. Example: `1`.                        |

### Relationships

| Relationship               | Kind and multiplicity | Meaning in the model                                                                                                                                                                     |
| -------------------------- | --------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `escalateTo` → `Principal` | reference, [*]        | Associates `EscalationPolicy` with the principals who receive escalated responsibility. The referenced `Principal` remains independently owned and may be reused elsewhere in the model. |

## `HumanTask`

A workflow task whose completion depends on a person. It preserves the assignment, evidence, deadline, and completion-event expectations that an automated function cannot provide.

Direct supertypes: `TraceableElement`, `PolicyTarget`. Inherited attributes and marker capabilities are documented in the [shared kernel](../shared-kernel.md); this section lists every attribute declared by this class.

### Declared attributes

| Attribute            | Type and multiplicity | What it captures and why it exists                                                                                                                                                                                                  | Accepted values and example                                                                                                                               |
| -------------------- | --------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `taskDescription`    | `String` [1]          | The work assigned to a person and the business result expected from it. The value survives transformation into PIM human-task modeling so assignment, evidence, timeout, escalation, and completion events have a concrete subject. | A free-form `String`, subject to this class's semantic meaning and any EVL constraints. Example: `Processes confirmed orders for the owning capability.`. |
| `completionEvidence` | `String` [1]          | The artifact or decision that proves the human task is complete. It prevents an approval workflow from treating mere task display as completion and supports audit/event-contract design.                                           | A free-form `String`, subject to this class's semantic meaning and any EVL constraints. Example: `Human Task Completion Evidence`.                        |

### Relationships

| Relationship                      | Kind and multiplicity | Meaning in the model                                                                                                                                                                   |
| --------------------------------- | --------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `assignees` → `Principal`         | reference, [*]        | Associates `HumanTask` with the principals eligible to complete the human task. The referenced `Principal` remains independently owned and may be reused elsewhere in the model.       |
| `timeout` → `TimeoutPolicy`       | reference, [?]        | Associates `HumanTask` with the applicable execution or waiting limit. The referenced `TimeoutPolicy` remains independently owned and may be reused elsewhere in the model.            |
| `escalation` → `EscalationPolicy` | reference, [?]        | Associates `HumanTask` with the policy applied when the task is not completed. The referenced `EscalationPolicy` remains independently owned and may be reused elsewhere in the model. |
| `completionEvents` → `EventType`  | reference, [*]        | Associates `HumanTask` with the event contracts accepted as completion evidence. The referenced `EventType` remains independently owned and may be reused elsewhere in the model.      |

## `ApprovalTask`

A human task with an explicit approval decision. It makes the approver, approval evidence, and positive or negative continuation visible in the workflow model.

Direct supertypes: `HumanTask`. Inherited attributes and marker capabilities are documented in the [shared kernel](../shared-kernel.md); this section lists every attribute declared by this class.

### Declared attributes

| Attribute                  | Type and multiplicity | What it captures and why it exists                                                                                                                                                           | Accepted values and example                                                                                                              |
| -------------------------- | --------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------- |
| `explicitApprovalRequired` | `Boolean` [1]         | Whether a positive approval must be recorded explicitly rather than inferred from silence or task completion. It protects high-impact decisions from ambiguous completion semantics.         | Either `true` or `false`. Example: `true`.                                                                                               |
| `approvalOutcomeField`     | `String` [1]          | The field in the completion payload that carries the approval result. It gives the workflow a stable value to inspect when routing `approved`, `rejected`, or returned-for-changes outcomes. | A free-form `String`, subject to this class's semantic meaning and any EVL constraints. Example: `Approval Task Approval Outcome Field`. |

### Relationships

This class declares no direct relationships.

## `CompensationPolicy`

The recovery policy for work that must be reversed or compensated. It connects a failed or cancelled path to compensating functions, events, and escalation.

Direct supertypes: `ArchitecturePolicy`. Inherited attributes and marker capabilities are documented in the [shared kernel](../shared-kernel.md); this section lists every attribute declared by this class.

### Declared attributes

| Attribute              | Type and multiplicity | What it captures and why it exists                                                                                                                                                                                                                                                                                                                                                                 | Accepted values and example                                                                                                                   |
| ---------------------- | --------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------- |
| `compensationStrategy` | `String` [1]          | The business strategy for undoing or mitigating effects after failure. It is the readable policy behind linked compensation functions/events and is what reviewers use to judge whether the workflow is safely reversible. Semantic validation: `CompensationPolicyHasAction` (compensation policy has action) in `mde/validation/pim/rules/workflow.evl` the value must be present and non-blank. | A free-form `String`, subject to this class's semantic meaning and any EVL constraints. Example: `Compensation Policy Compensation Strategy`. |
| `automatic`            | `Boolean` [1]         | Whether compensation can be executed without a human decision. The value separates safe deterministic rollback from remediation that requires approval or operational intervention.                                                                                                                                                                                                                | Either `true` or `false`. Example: `false`.                                                                                                   |

### Relationships

| Relationship                         | Kind and multiplicity | Meaning in the model                                                                                                                                                                      |
| ------------------------------------ | --------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `compensationFunctions` → `Function` | reference, [*]        | Associates `CompensationPolicy` with the functions that reverse completed actions. The referenced `Function` remains independently owned and may be reused elsewhere in the model.        |
| `compensationEvents` → `EventType`   | reference, [*]        | Associates `CompensationPolicy` with the events emitted or consumed during compensation. The referenced `EventType` remains independently owned and may be reused elsewhere in the model. |
