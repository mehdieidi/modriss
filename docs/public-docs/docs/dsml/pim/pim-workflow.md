# Provider-independent workflows

Workflow concepts describe orchestration, branching, parallelism, waiting, human approval, compensation, and escalation without embedding ASL syntax.

Source: `mde/metamodels/pim/pim-workflow.emf`.

## `Workflow`

The PIM orchestration boundary that coordinates provider-independent steps, transitions, policies, and service behavior. It is where durability, statefulness, compensation, human approval, and duration are decided before Step Functions or another engine is chosen.

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

| Relationship                            | Kind and multiplicity                           | Meaning in the model                                                                                                                    |
| --------------------------------------- | ----------------------------------------------- | --------------------------------------------------------------------------------------------------------------------------------------- |
| `steps` → `WorkflowStep`                | containment, [+]; opposite `workflow`           | Contains the workflow step element(s) that make up this workflow; the contained objects belong to this model element.                   |
| `transitions` → `WorkflowTransition`    | containment, [*]; opposite `workflow`           | Contains the workflow transition element(s) that make up this workflow; the contained objects belong to this model element.             |
| `service` → `ServerlessService`         | reference; read-only, [1]; opposite `workflows` | References the serverless service element(s) used as service by this workflow; the target may be shared elsewhere in the model.         |
| `observability` → `ObservabilityConfig` | reference, [?]                                  | References the observability config element(s) used as observability by this workflow; the target may be shared elsewhere in the model. |
| `resilience` → `ResiliencePolicy`       | reference, [?]                                  | References the resilience policy element(s) used as resilience by this workflow; the target may be shared elsewhere in the model.       |
| `idempotency` → `IdempotencyPolicy`     | reference, [?]                                  | References the idempotency policy element(s) used as idempotency by this workflow; the target may be shared elsewhere in the model.     |

## `WorkflowStep`

The provider-independent execution unit shared by start/end, task, choice, parallel, map, wait, and pass steps. Its mappings and timeout express state-data and timing intent without embedding ASL syntax.

Direct supertypes: `TraceableElement`, `FlowEndpoint`, `PolicyTarget`, `ProtectedResource`. Inherited attributes and marker capabilities are documented in the [shared kernel](../shared-kernel.md); this section lists every attribute declared by this class.

### Declared attributes

| Attribute        | Type and multiplicity | What it captures and why it exists                                                                                                                                                                                                                                                                                                                                                                                                                                                   | Accepted values and example                                                                                                                                       |
| ---------------- | --------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ | ----------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `orderIndex`     | `Integer` [1]         | The stable order used when a workflow has an ordered list of steps but explicit transitions do not fully determine a next state. CIM-to-PIM copies the source process index; PIM-to-AWS sorts steps by this value to select a deterministic `StartAt` and to infer sequential `Next` links. It is not a branch condition and must not be used to encode alternatives.                                                                                                                | A non-negative integer used for deterministic step ordering. Examples: `0`, `1`, `2`. Do not use it to encode a branch; model branches with `WorkflowTransition`. |
| `timeoutSeconds` | `Integer` [1]         | The maximum wait for this individual step, in seconds, at the provider-independent level. Temporal-constraint refinement can populate it; PIM validation asks for retry/catch handling or rationale when it is set; PIM-to-AWS emits it as ASL `TimeoutSeconds` or wait `Seconds`. Semantic validation: `TimedStepShouldUseTimeoutPolicy` (timed step should use timeout policy) in `mde/validation/pim/rules/workflow.evl` the related value or object must be explicitly provided. | `0` for no explicit step timeout, or a positive integer number of seconds such as `30` or `900`.                                                                  |
| `inputMapping`   | `String` [1]          | The JSONPath/JSONata-style projection that supplies this step's input from the workflow state. It is the contract between preceding output and the step's action; PIM-to-AWS maps it to ASL `InputPath` after normalizing absent/TBD values.                                                                                                                                                                                                                                         | A JSONPath/JSONata projection such as `$.order` or an intentional `TBD` placeholder during transformation review.                                                 |
| `outputMapping`  | `String` [1]          | The projection or destination describing what this step contributes back to workflow state. For task/function steps the PIM-to-AWS transformation uses an explicit mapping as the ASL `ResultPath`; leaving it unresolved means the transformation must preserve state conservatively rather than guess where to write the result.                                                                                                                                                   | A JSONPath/JSONata result location such as `$.reservation` or an intentional `TBD` placeholder during transformation review.                                      |

### Relationships

| Relationship                     | Kind and multiplicity                       | Meaning in the model                                                                                                        |
| -------------------------------- | ------------------------------------------- | --------------------------------------------------------------------------------------------------------------------------- |
| `retry` → `RetryPolicy`          | containment, [?]                            | Contains the retry policy element(s) that make up this workflow step; the contained objects belong to this model element.   |
| `catchHandlers` → `ErrorHandler` | containment, [*]; opposite `step`           | Contains the error handler element(s) that make up this workflow step; the contained objects belong to this model element.  |
| `workflow` → `Workflow`          | reference; read-only, [1]; opposite `steps` | References the workflow element(s) used as workflow by this workflow step; the target may be shared elsewhere in the model. |

## `StartStep`

The unique business entry point from which the process begins after its trigger has been recognized. It is a process step rather than a diagram-only marker because its position and traceability are carried into workflow generation.

Direct supertypes: `WorkflowStep`. Inherited attributes and marker capabilities are documented in the [shared kernel](../shared-kernel.md); this section lists every attribute declared by this class.

### Declared attributes

This class declares no attributes of its own. It inherits the attributes of its supertype, if any.

### Relationships

| Relationship                          | Kind and multiplicity | Meaning in the model                                                                                                                    |
| ------------------------------------- | --------------------- | --------------------------------------------------------------------------------------------------------------------------------------- |
| `compensation` → `CompensationPolicy` | reference, [?]        | References the compensation policy element(s) used as compensation by this start step; the target may be shared elsewhere in the model. |

## `SuccessEndStep`

Represents success end step in the PIM vocabulary. It specializes `WorkflowStep` with the details needed for this modeling concern.

Direct supertypes: `WorkflowStep`. Inherited attributes and marker capabilities are documented in the [shared kernel](../shared-kernel.md); this section lists every attribute declared by this class.

### Declared attributes

This class declares no attributes of its own. It inherits the attributes of its supertype, if any.

### Relationships

This class declares no direct relationships.

## `FailureEndStep`

Represents failure end step in the PIM vocabulary. It specializes `WorkflowStep` with the details needed for this modeling concern.

Direct supertypes: `WorkflowStep`. Inherited attributes and marker capabilities are documented in the [shared kernel](../shared-kernel.md); this section lists every attribute declared by this class.

### Declared attributes

This class declares no attributes of its own. It inherits the attributes of its supertype, if any.

### Relationships

This class declares no direct relationships.

## `TaskStep`

A workflow step that performs exactly one action: invokes a function, calls an adapter, starts a nested workflow, or assigns a human task. EVL enforces that one action is selected so the generated workflow has an unambiguous operation.

Direct supertypes: `WorkflowStep`. Inherited attributes and marker capabilities are documented in the [shared kernel](../shared-kernel.md); this section lists every attribute declared by this class.

### Declared attributes

This class declares no attributes of its own. It inherits the attributes of its supertype, if any.

### Relationships

| Relationship                            | Kind and multiplicity | Meaning in the model                                                                                                                   |
| --------------------------------------- | --------------------- | -------------------------------------------------------------------------------------------------------------------------------------- |
| `invokesFunction` → `Function`          | reference, [?]        | References the function element(s) used as invokes function by this task step; the target may be shared elsewhere in the model.        |
| `invokesAdapter` → `ExternalAdapter`    | reference, [?]        | References the external adapter element(s) used as invokes adapter by this task step; the target may be shared elsewhere in the model. |
| `nestedWorkflow` → `Workflow`           | reference, [?]        | References the workflow element(s) used as nested workflow by this task step; the target may be shared elsewhere in the model.         |
| `humanTask` → `HumanTask`               | reference, [?]        | References the human task element(s) used as human task by this task step; the target may be shared elsewhere in the model.            |
| `compensation` → `CompensationPolicy`   | reference, [?]        | References the compensation policy element(s) used as compensation by this task step; the target may be shared elsewhere in the model. |
| `callbackConfig` → `CallbackTaskConfig` | containment, [?]      | Contains the callback task config element(s) that make up this task step; the contained objects belong to this model element.          |

## `ChoiceStep`

A workflow branching state whose condition/evaluator chooses among outgoing transitions. It is the PIM form of a business decision and becomes provider-specific choice logic during AWS refinement.

Direct supertypes: `WorkflowStep`. Inherited attributes and marker capabilities are documented in the [shared kernel](../shared-kernel.md); this section lists every attribute declared by this class.

### Declared attributes

| Attribute             | Type and multiplicity | What it captures and why it exists                                                                                                                                                                                                                                                                                                                                                                                                                   | Accepted values and example                                                                                  |
| --------------------- | --------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------ |
| `conditionExpression` | `String` [1]          | The predicate evaluated to choose an outgoing workflow transition. CIM decision conditions are copied here, and PIM-to-AWS uses the expression when creating an ASL choice/evaluator state; it is the decision logic, not explanatory text for the diagram. Transformation role: ETL rule `DecisionStep2WorkflowChoiceStep` in `mde/transformations/cim-to-pim/process-policy.etl` assigns or materializes this feature while refining `ChoiceStep`. | A provider-independent boolean expression, for example `$.order.total >= 1000` or `$.decision = 'APPROVED'`. |

### Relationships

| Relationship                   | Kind and multiplicity | Meaning in the model                                                                                                              |
| ------------------------------ | --------------------- | --------------------------------------------------------------------------------------------------------------------------------- |
| `condition` → `Expression`     | containment, [?]      | Contains the expression element(s) that make up this choice step; the contained objects belong to this model element.             |
| `invokesFunction` → `Function` | reference, [?]        | References the function element(s) used as invokes function by this choice step; the target may be shared elsewhere in the model. |

## `ParallelStep`

A workflow state that starts independent branches concurrently and joins their outcomes. Branch containment makes the parallel graph explicit rather than relying on an implementation-specific fan-out convention.

Direct supertypes: `WorkflowStep`. Inherited attributes and marker capabilities are documented in the [shared kernel](../shared-kernel.md); this section lists every attribute declared by this class.

### Declared attributes

This class declares no attributes of its own. It inherits the attributes of its supertype, if any.

### Relationships

| Relationship                  | Kind and multiplicity                  | Meaning in the model                                                                                                         |
| ----------------------------- | -------------------------------------- | ---------------------------------------------------------------------------------------------------------------------------- |
| `branches` → `ParallelBranch` | containment, [*]; opposite `ownerStep` | Contains the parallel branch element(s) that make up this parallel step; the contained objects belong to this model element. |

## `MapStep`

A workflow state that applies a processor to each item in a collection. Its map configuration captures data selection, concurrency, distribution, and result handling so fan-out is an architectural decision.

Direct supertypes: `WorkflowStep`. Inherited attributes and marker capabilities are documented in the [shared kernel](../shared-kernel.md); this section lists every attribute declared by this class.

### Declared attributes

This class declares no attributes of its own. It inherits the attributes of its supertype, if any.

### Relationships

| Relationship                   | Kind and multiplicity | Meaning in the model                                                                                                     |
| ------------------------------ | --------------------- | ------------------------------------------------------------------------------------------------------------------------ |
| `mapConfig` → `MapStateConfig` | containment, [?]      | Contains the map state config element(s) that make up this map step; the contained objects belong to this model element. |

## `WaitStep`

A provider-independent pause with either timed or callback/event semantics. Its condition and reason are preserved until the PSM can safely emit an ASL wait state or identify a manual decision.

Direct supertypes: `WorkflowStep`. Inherited attributes and marker capabilities are documented in the [shared kernel](../shared-kernel.md); this section lists every attribute declared by this class.

### Declared attributes

| Attribute             | Type and multiplicity | What it captures and why it exists                                                                                                                                                                                                                                                                                                                                                                                     | Accepted values and example                                                                                                                              |
| --------------------- | --------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `conditionExpression` | `String` [1]          | The provider-independent timing or callback expression for the wait. PIM-to-AWS attempts to interpret it as a supported ASL wait duration and creates a blocker/manual decision when the expression cannot be safely represented. Transformation role: ETL rule `WaitLikeStep2WorkflowWaitStep` in `mde/transformations/cim-to-pim/process-policy.etl` assigns or materializes this feature while refining `WaitStep`. | A duration/callback expression such as `PT15M`, `300s`, or a documented callback condition that the PSM transformation can interpret or flag for review. |
| `waitReason`          | `String` [1]          | Why the workflow is intentionally paused. It preserves the business interpretation of a wait after CIM transformation, helping a modeller choose between a timed wait, callback token, event wait, or a missing transition. Transformation role: ETL rule `WaitLikeStep2WorkflowWaitStep` in `mde/transformations/cim-to-pim/process-policy.etl` assigns or materializes this feature while refining `WaitStep`.       | A free-form `String`, subject to this class's semantic meaning and any EVL constraints. Example: `Wait Step Wait Reason`.                                |

### Relationships

| Relationship               | Kind and multiplicity | Meaning in the model                                                                                                |
| -------------------------- | --------------------- | ------------------------------------------------------------------------------------------------------------------- |
| `condition` → `Expression` | containment, [?]      | Contains the expression element(s) that make up this wait step; the contained objects belong to this model element. |

## `PassStep`

Represents pass step in the PIM vocabulary. It specializes `WorkflowStep` with the details needed for this modeling concern.

Direct supertypes: `WorkflowStep`. Inherited attributes and marker capabilities are documented in the [shared kernel](../shared-kernel.md); this section lists every attribute declared by this class.

### Declared attributes

This class declares no attributes of its own. It inherits the attributes of its supertype, if any.

### Relationships

This class declares no direct relationships.

## `WorkflowTransition`

A directed route between PIM workflow steps. Conditions and literal evaluator outcomes determine branching; `defaultTransition` identifies the fallback path when no conditional route matches.

Direct supertypes: `TraceableElement`. Inherited attributes and marker capabilities are documented in the [shared kernel](../shared-kernel.md); this section lists every attribute declared by this class.

### Declared attributes

| Attribute             | Type and multiplicity | What it captures and why it exists                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                              | Accepted values and example                                                                                                                                               |
| --------------------- | --------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `conditionExpression` | `String` [1]          | The condition under which the transition is eligible. It is copied from CIM's effective transition condition and is later rendered as the branch predicate or sequential routing decision in the provider-specific workflow. Semantic validation: `ConditionalTransitionShouldHaveConditionUnlessDefault` (conditional transition should have condition unless default) in `mde/validation/pim/rules/workflow.evl` the value must be present and non-blank. Transformation role: ETL rule `ProcessTransition2WorkflowTransition` in `mde/transformations/cim-to-pim/process-policy.etl` assigns or materializes this feature while refining `WorkflowTransition`.                                               | A free-form `String`, subject to this class's semantic meaning and any EVL constraints. Example: `$.status == 'READY'`.                                                   |
| `decisionOutcome`     | `String` [1]          | The literal result expected from a function-backed choice evaluator, such as `APPROVED` or `REJECTED`. PIM-to-AWS compares this value in the generated ASL choice logic; leaving it empty intentionally marks the branch as incomplete for review rather than inventing a comparison.                                                                                                                                                                                                                                                                                                                                                                                                                           | A literal outcome returned by the choice evaluator, such as `APPROVED`, `REJECTED`, or `NEEDS_REVIEW`; use an empty value only while the branch is explicitly incomplete. |
| `defaultTransition`   | `Boolean` [1]         | Marks the transition used when no conditional branch matches. CIM-to-PIM sets it when the source transition has no effective condition, and PIM validation uses it to allow a branch without `conditionExpression`; it is the fallback route, not merely a visual default. Semantic validation: `ConditionalTransitionShouldHaveConditionUnlessDefault` (conditional transition should have condition unless default) in `mde/validation/pim/rules/workflow.evl` the value must be present and non-blank. Transformation role: ETL rule `ProcessTransition2WorkflowTransition` in `mde/transformations/cim-to-pim/process-policy.etl` assigns or materializes this feature while refining `WorkflowTransition`. | Either `true` or `false`. Use `true` for the fallback branch that receives control when no conditional transition matches.                                                |

### Relationships

| Relationship               | Kind and multiplicity                             | Meaning in the model                                                                                                                 |
| -------------------------- | ------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------ |
| `condition` → `Expression` | containment, [?]                                  | Contains the expression element(s) that make up this workflow transition; the contained objects belong to this model element.        |
| `workflow` → `Workflow`    | reference; read-only, [1]; opposite `transitions` | References the workflow element(s) used as workflow by this workflow transition; the target may be shared elsewhere in the model.    |
| `source` → `WorkflowStep`  | reference, [1]                                    | References the workflow step element(s) used as source by this workflow transition; the target may be shared elsewhere in the model. |
| `target` → `WorkflowStep`  | reference, [1]                                    | References the workflow step element(s) used as target by this workflow transition; the target may be shared elsewhere in the model. |

## `ErrorHandler`

The PIM recovery policy attached to a workflow step. It selects the errors it owns and routes them to a next step or handler function, with an explicit choice about whether normal workflow execution continues.

Direct supertypes: `TraceableElement`. Inherited attributes and marker capabilities are documented in the [shared kernel](../shared-kernel.md); this section lists every attribute declared by this class.

### Declared attributes

| Attribute          | Type and multiplicity | What it captures and why it exists                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                 | Accepted values and example                                                                                                       |
| ------------------ | --------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ | --------------------------------------------------------------------------------------------------------------------------------- |
| `errorSelector`    | `String` [1]          | The error names or pattern this handler owns. It prevents one recovery action from catching unrelated failures and becomes the selection input for generated catch rules. Semantic validation: `ErrorHandlerShouldSelectErrors` (error handler should select errors) in `mde/validation/pim/rules/workflow.evl` the value must be present and non-blank. Transformation role: ETL rule `ExceptionScenario2ErrorHandler` in `mde/transformations/cim-to-pim/process-policy.etl` assigns or materializes this feature while refining `ErrorHandler`.                                                                 | A free-form `String`, subject to this class's semantic meaning and any EVL constraints. Example: `Error Handler Error Selector`.  |
| `recoveryAction`   | `String` [1]          | The human-readable recovery intent, retry, compensate, route to support, or terminate safely. EVL requires it with the selector, and it gives PIM-to-PSM a reasoned action instead of a provider-specific catch with no business meaning. Semantic validation: `ErrorHandlerShouldSelectErrors` (error handler should select errors) in `mde/validation/pim/rules/workflow.evl` the value must be present and non-blank. Transformation role: ETL rule `ExceptionScenario2ErrorHandler` in `mde/transformations/cim-to-pim/process-policy.etl` assigns or materializes this feature while refining `ErrorHandler`. | A free-form `String`, subject to this class's semantic meaning and any EVL constraints. Example: `Error Handler Recovery Action`. |
| `continueWorkflow` | `Boolean` [1]         | Whether recovery should return control to the workflow after handling the error. The flag distinguishes a recover-and-continue path from a terminal failure and shapes the target step/ASL catch behavior. Transformation role: ETL rule `ExceptionScenario2ErrorHandler` in `mde/transformations/cim-to-pim/process-policy.etl` assigns or materializes this feature while refining `ErrorHandler`.                                                                                                                                                                                                               | Either `true` or `false`. Example: `false`.                                                                                       |

### Relationships

| Relationship                   | Kind and multiplicity                               | Meaning in the model                                                                                                                |
| ------------------------------ | --------------------------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------- |
| `step` → `WorkflowStep`        | reference; read-only, [1]; opposite `catchHandlers` | References the workflow step element(s) used as step by this error handler; the target may be shared elsewhere in the model.        |
| `nextStep` → `WorkflowStep`    | reference, [?]                                      | References the workflow step element(s) used as next step by this error handler; the target may be shared elsewhere in the model.   |
| `handlerFunction` → `Function` | reference, [?]                                      | References the function element(s) used as handler function by this error handler; the target may be shared elsewhere in the model. |

## `ParallelBranch`

Represents parallel branch in the PIM vocabulary. It specializes `TraceableElement` with the details needed for this modeling concern.

Direct supertypes: `TraceableElement`. Inherited attributes and marker capabilities are documented in the [shared kernel](../shared-kernel.md); this section lists every attribute declared by this class.

### Declared attributes

This class declares no attributes of its own. It inherits the attributes of its supertype, if any.

### Relationships

| Relationship                         | Kind and multiplicity                          | Meaning in the model                                                                                                                 |
| ------------------------------------ | ---------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------ |
| `ownerStep` → `ParallelStep`         | reference; read-only, [1]; opposite `branches` | References the parallel step element(s) used as owner step by this parallel branch; the target may be shared elsewhere in the model. |
| `steps` → `WorkflowStep`             | containment, [+]                               | Contains the workflow step element(s) that make up this parallel branch; the contained objects belong to this model element.         |
| `transitions` → `WorkflowTransition` | containment, [*]                               | Contains the workflow transition element(s) that make up this parallel branch; the contained objects belong to this model element.   |

## `MapStateConfig`

Provider-independent configuration for collection processing: which items to iterate, how to shape each item, how much concurrency to allow, whether distributed execution is needed, and where results belong.

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

| Relationship                       | Kind and multiplicity | Meaning in the model                                                                                                            |
| ---------------------------------- | --------------------- | ------------------------------------------------------------------------------------------------------------------------------- |
| `itemProcessor` → `ParallelBranch` | containment, [1]      | Contains the parallel branch element(s) that make up this map state config; the contained objects belong to this model element. |

## `CallbackTaskConfig`

The contract for a task that pauses until an external actor calls back with the task token. It defines token placement, completion events, and timeout behavior at the architecture level.

Direct supertypes: `TraceableElement`. Inherited attributes and marker capabilities are documented in the [shared kernel](../shared-kernel.md); this section lists every attribute declared by this class.

### Declared attributes

| Attribute       | Type and multiplicity | What it captures and why it exists                                                                                                                                                                                                 | Accepted values and example                                                                                           |
| --------------- | --------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | --------------------------------------------------------------------------------------------------------------------- |
| `taskTokenPath` | `String` [1]          | The path at which the orchestration callback token is placed in the task input. A worker/external actor uses that token to resume the waiting task; the field therefore defines the callback contract, not an arbitrary JSON path. | A free-form `String`, subject to this class's semantic meaning and any EVL constraints. Example: `/orders/{orderId}`. |

### Relationships

| Relationship                      | Kind and multiplicity | Meaning in the model                                                                                                                           |
| --------------------------------- | --------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------- |
| `callbackRoute` → `RouteEndpoint` | reference, [?]        | References the route endpoint element(s) used as callback route by this callback task config; the target may be shared elsewhere in the model. |
| `completionEvents` → `EventType`  | reference, [*]        | References the event type element(s) used as completion events by this callback task config; the target may be shared elsewhere in the model.  |
| `timeout` → `TimeoutPolicy`       | reference, [?]        | References the timeout policy element(s) used as timeout by this callback task config; the target may be shared elsewhere in the model.        |

## `EscalationPolicy`

The rule for escalating an unresolved human task after a defined delay. It connects a time boundary with the people or identities who must take over.

Direct supertypes: `TraceableElement`. Inherited attributes and marker capabilities are documented in the [shared kernel](../shared-kernel.md); this section lists every attribute declared by this class.

### Declared attributes

| Attribute        | Type and multiplicity | What it captures and why it exists                                                                                                                                                             | Accepted values and example                                                                                             |
| ---------------- | --------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ----------------------------------------------------------------------------------------------------------------------- |
| `escalationRule` | `String` [1]          | The condition that turns an overdue or unresolved human task into an escalation. It describes the business escalation decision and is evaluated together with `afterSeconds` and `escalateTo`. | A free-form `String`, subject to this class's semantic meaning and any EVL constraints. Example: `$.status == 'READY'`. |
| `afterSeconds`   | `Integer` [1]         | How long the human task may remain unresolved before escalation, measured in seconds. It is a timing boundary for people/processes rather than the execution timeout of the worker function.   | A numeric `Integer` value; use the unit or boundary documented for this attribute. Example: `1`.                        |

### Relationships

| Relationship               | Kind and multiplicity | Meaning in the model                                                                                                                |
| -------------------------- | --------------------- | ----------------------------------------------------------------------------------------------------------------------------------- |
| `escalateTo` → `Principal` | reference, [*]        | References the principal element(s) used as escalate to by this escalation policy; the target may be shared elsewhere in the model. |

## `HumanTask`

A reusable PIM representation of work assigned to a person, including completion evidence, timeout, escalation, and completion events. It is the human boundary that an implementation must make auditable.

Direct supertypes: `TraceableElement`, `PolicyTarget`. Inherited attributes and marker capabilities are documented in the [shared kernel](../shared-kernel.md); this section lists every attribute declared by this class.

### Declared attributes

| Attribute            | Type and multiplicity | What it captures and why it exists                                                                                                                                                                                                  | Accepted values and example                                                                                                                               |
| -------------------- | --------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `taskDescription`    | `String` [1]          | The work assigned to a person and the business result expected from it. The value survives transformation into PIM human-task modeling so assignment, evidence, timeout, escalation, and completion events have a concrete subject. | A free-form `String`, subject to this class's semantic meaning and any EVL constraints. Example: `Processes confirmed orders for the owning capability.`. |
| `completionEvidence` | `String` [1]          | The artifact or decision that proves the human task is complete. It prevents an approval workflow from treating mere task display as completion and supports audit/event-contract design.                                           | A free-form `String`, subject to this class's semantic meaning and any EVL constraints. Example: `Human Task Completion Evidence`.                        |

### Relationships

| Relationship                      | Kind and multiplicity | Meaning in the model                                                                                                                |
| --------------------------------- | --------------------- | ----------------------------------------------------------------------------------------------------------------------------------- |
| `assignees` → `Principal`         | reference, [*]        | References the principal element(s) used as assignees by this human task; the target may be shared elsewhere in the model.          |
| `timeout` → `TimeoutPolicy`       | reference, [?]        | References the timeout policy element(s) used as timeout by this human task; the target may be shared elsewhere in the model.       |
| `escalation` → `EscalationPolicy` | reference, [?]        | References the escalation policy element(s) used as escalation by this human task; the target may be shared elsewhere in the model. |
| `completionEvents` → `EventType`  | reference, [*]        | References the event type element(s) used as completion events by this human task; the target may be shared elsewhere in the model. |

## `ApprovalTask`

A human task whose completion carries an explicit approval outcome. It prevents silence or mere task closure from being mistaken for authorization of a consequential business action.

Direct supertypes: `HumanTask`. Inherited attributes and marker capabilities are documented in the [shared kernel](../shared-kernel.md); this section lists every attribute declared by this class.

### Declared attributes

| Attribute                  | Type and multiplicity | What it captures and why it exists                                                                                                                                                           | Accepted values and example                                                                                                              |
| -------------------------- | --------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------- |
| `explicitApprovalRequired` | `Boolean` [1]         | Whether a positive approval must be recorded explicitly rather than inferred from silence or task completion. It protects high-impact decisions from ambiguous completion semantics.         | Either `true` or `false`. Example: `true`.                                                                                               |
| `approvalOutcomeField`     | `String` [1]          | The field in the completion payload that carries the approval result. It gives the workflow a stable value to inspect when routing `approved`, `rejected`, or returned-for-changes outcomes. | A free-form `String`, subject to this class's semantic meaning and any EVL constraints. Example: `Approval Task Approval Outcome Field`. |

### Relationships

This class declares no direct relationships.

## `CompensationPolicy`

The PIM description of how a workflow reverses or mitigates completed effects. It states whether compensation is automatic and links the functions/events that make recovery executable or observable.

Direct supertypes: `ArchitecturePolicy`. Inherited attributes and marker capabilities are documented in the [shared kernel](../shared-kernel.md); this section lists every attribute declared by this class.

### Declared attributes

| Attribute              | Type and multiplicity | What it captures and why it exists                                                                                                                                                                                                                                                                                                                                                                 | Accepted values and example                                                                                                                   |
| ---------------------- | --------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------- |
| `compensationStrategy` | `String` [1]          | The business strategy for undoing or mitigating effects after failure. It is the readable policy behind linked compensation functions/events and is what reviewers use to judge whether the workflow is safely reversible. Semantic validation: `CompensationPolicyHasAction` (compensation policy has action) in `mde/validation/pim/rules/workflow.evl` the value must be present and non-blank. | A free-form `String`, subject to this class's semantic meaning and any EVL constraints. Example: `Compensation Policy Compensation Strategy`. |
| `automatic`            | `Boolean` [1]         | Whether compensation can be executed without a human decision. The value separates safe deterministic rollback from remediation that requires approval or operational intervention.                                                                                                                                                                                                                | Either `true` or `false`. Example: `false`.                                                                                                   |

### Relationships

| Relationship                         | Kind and multiplicity | Meaning in the model                                                                                                                            |
| ------------------------------------ | --------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------- |
| `compensationFunctions` → `Function` | reference, [*]        | References the function element(s) used as compensation functions by this compensation policy; the target may be shared elsewhere in the model. |
| `compensationEvents` → `EventType`   | reference, [*]        | References the event type element(s) used as compensation events by this compensation policy; the target may be shared elsewhere in the model.  |
