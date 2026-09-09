# Step Functions and Amazon States Language

Workflow classes represent Step Functions state machines and their ASL documents, state types, branches, map processors, retries, catches, logging, and tracing.

Source: `mde/metamodels/psm/awspsm-workflow.emf`.

## `StepFunctionStateMachine`

The AWS resource that hosts the deployed orchestration. It chooses the concrete definition source, execution type, alias strategy, execution role, logging, tracing, and SAM event bindings for the workflow.

Direct supertypes: `AwsResource`. Inherited attributes and marker capabilities are documented in the [shared kernel](../shared-kernel.md); this section lists every attribute declared by this class.

### Declared attributes

| Attribute                     | Type and multiplicity  | What it captures and why it exists                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                 | Accepted values and example                                                                                                                                            |
| ----------------------------- | ---------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `stateMachineName`            | `String` [1]           | The deployed Step Functions name presented to operators and promotion tooling. It is separate from the CloudFormation logical ID because a stable operational name and a template identity have different replacement and environment concerns. Transformation role: ETL rule `Workflow2StepFunctionStateMachine` in `mde/transformations/pim-to-awspsm/workflow-security-config.etl` assigns or materializes this feature while refining `StepFunctionStateMachine`. Generation role: The artifact generator references this feature in `mde/generation/awspsm-to-artifacts/lib/cfn.eol`. The artifact generator references this feature in `mde/generation/awspsm-to-artifacts/lib/contracts.eol`.                                                                                                                                                                                               | A free-form `String`, subject to this class's semantic meaning and any EVL constraints. Example: `OrdersWorkflow`.                                                     |
| `definitionUri`               | `String` [1]           | The artifact location containing the ASL definition when the state machine is not assembled from the typed `AslDocument`. EVL requires exactly one definition source, preventing a file and modeled states from silently disagreeing. Semantic validation: `StateMachineHasDefinition` (state machine has definition) in `mde/validation/psm/rules/workflow.evl` the rule's diagnostic or remediation guidance refers to this feature. `StateMachineShouldUseSingleDefinitionSource` (state machine should use single definition source) in `mde/validation/psm/rules/workflow.evl` the rule's diagnostic or remediation guidance refers to this feature.                                                                                                                                                                                                                                          | A free-form `String`, subject to this class's semantic meaning and any EVL constraints. Example: `https://example.com/orders`.                                         |
| `definitionString`            | `String` [1]           | An inline ASL definition supplied directly to the resource. It is useful for a deliberately external/hand-authored definition, but it competes with `definitionUri` and `aslDocument`, so PSM validation treats multiple sources as ambiguous. Semantic validation: `StateMachineHasDefinition` (state machine has definition) in `mde/validation/psm/rules/workflow.evl` the rule's diagnostic or remediation guidance refers to this feature. `StateMachineShouldUseSingleDefinitionSource` (state machine should use single definition source) in `mde/validation/psm/rules/workflow.evl` the rule's diagnostic or remediation guidance refers to this feature. Generation role: The artifact generator references this feature in `mde/generation/awspsm-to-artifacts/lib/cfn.eol`. The artifact generator references this feature in `mde/generation/awspsm-to-artifacts/lib/validation.eol`. | A free-form `String`, subject to this class's semantic meaning and any EVL constraints. Example: `A confirmed order is one accepted for fulfillment by the business.`. |
| `definitionSubstitutionsJson` | `String` [1]           | The substitution map applied while materializing the state-machine definition, typically for resource ARNs or stage-specific values. Keeping substitutions explicit lets generation bind deployment values without embedding environment-specific strings in business workflow logic. Generation role: The artifact generator references this feature in `mde/generation/awspsm-to-artifacts/lib/cfn.eol`.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                         | A free-form `String`, subject to this class's semantic meaning and any EVL constraints. Example: `{"enabled":true}`.                                                   |
| `publishAlias`                | `Boolean` [1]          | Whether a named state-machine alias should be published. It expresses the release-management decision to address a workflow version through an alias; PSM validation requires `aliasName` when enabled. Semantic validation: `PublishAliasRequiresAliasName` (publish alias requires alias name) in `mde/validation/psm/rules/workflow.evl` the flag must be enabled for this rule to pass. Transformation role: ETL rule `Workflow2StepFunctionStateMachine` in `mde/transformations/pim-to-awspsm/workflow-security-config.etl` assigns or materializes this feature while refining `StepFunctionStateMachine`. Generation role: The artifact generator references this feature in `mde/generation/awspsm-to-artifacts/lib/cfn.eol`.                                                                                                                                                             | Either `true` or `false`. Example: `false`.                                                                                                                            |
| `aliasName`                   | `String` [1]           | The stable alias through which executions address a published state-machine version. It is required only when alias publication is requested and supports controlled promotion/rollback independently of the latest definition. Semantic validation: `PublishAliasRequiresAliasName` (publish alias requires alias name) in `mde/validation/psm/rules/workflow.evl` the value must be present and non-blank. Transformation role: ETL rule `Workflow2StepFunctionStateMachine` in `mde/transformations/pim-to-awspsm/workflow-security-config.etl` assigns or materializes this feature while refining `StepFunctionStateMachine`.                                                                                                                                                                                                                                                                 | A free-form `String`, subject to this class's semantic meaning and any EVL constraints. Example: `StepFunctionStateMachineExample`.                                    |
| `stateMachineType`            | `StepFunctionType` [1] | The AWS execution type, such as Standard or Express. This is where the provider-specific durability, history, latency, and pricing choice becomes concrete after PIM workflow intent has been evaluated. Transformation role: ETL rule `Workflow2StepFunctionStateMachine` in `mde/transformations/pim-to-awspsm/workflow-security-config.etl` assigns or materializes this feature while refining `StepFunctionStateMachine`. Generation role: The artifact generator references this feature in `mde/generation/awspsm-to-artifacts/lib/cfn.eol`.                                                                                                                                                                                                                                                                                                                                                | Exactly one of: `STANDARD`, `EXPRESS`. Example: `STANDARD`.                                                                                                            |

### Relationships

| Relationship                            | Kind and multiplicity | Meaning in the model                                                                                                                                    |
| --------------------------------------- | --------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `logging` → `StepFunctionLoggingConfig` | containment, [?]      | Contains the step function logging config element(s) that make up this step function state machine; the contained objects belong to this model element. |
| `tracing` → `StepFunctionTracingConfig` | containment, [?]      | Contains the step function tracing config element(s) that make up this step function state machine; the contained objects belong to this model element. |
| `aslDocument` → `AslDocument`           | containment, [?]      | Contains the asl document element(s) that make up this step function state machine; the contained objects belong to this model element.                 |
| `samEvents` → `SamStateMachineEvent`    | containment, [*]      | Contains the sam state machine event element(s) that make up this step function state machine; the contained objects belong to this model element.      |
| `role` → `IamRole`                      | reference, [1]        | References the iam role element(s) used as role by this step function state machine; the target may be shared elsewhere in the model.                   |

## `AslDocument`

The typed Amazon States Language document used when the PSM owns the state graph rather than importing an external definition. `startAt` and contained states form the executable graph that AWS validates.

Direct supertypes: `StructuredDocument`. Inherited attributes and marker capabilities are documented in the [shared kernel](../shared-kernel.md); this section lists every attribute declared by this class.

### Declared attributes

| Attribute       | Type and multiplicity | What it captures and why it exists                                                                                                                                                                                                                                                                                                                                                                                                  | Accepted values and example                                                                                                         |
| --------------- | --------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------- |
| `comment`       | `String` [1]          | The explanatory comment embedded in the ASL document. It helps operators understand the generated state machine without changing execution, and gives generation a safe place for provenance/context that should not be encoded as a fake state. Generation role: The artifact generator references this feature in `mde/generation/awspsm-to-artifacts/lib/contracts.eol`.                                                         | A free-form `String`, subject to this class's semantic meaning and any EVL constraints. Example: `Asl Document Comment`.            |
| `queryLanguage` | `String` [1]          | The default expression language for states in the document, normally JSONPath or JSONata. It establishes which path/expression fields are legal; state-level overrides may specialize it, while validation rejects JSONPath-only fields on JSONata states.                                                                                                                                                                          | A free-form `String`, subject to this class's semantic meaning and any EVL constraints. Example: `Asl Document Query Language`.     |
| `startAt`       | `String` [1]          | The exact `stateName` at which an execution begins. PIM-to-AWS derives it from the first ordered PIM start step when building typed ASL; PSM validation checks that it names an actual state, because a typo prevents every execution from starting. Semantic validation: `StartAtReferencesExistingState` (start at references existing state) in `mde/validation/psm/rules/workflow.evl` the value must be present and non-blank. | The exact name of a contained state, such as `ValidateOrder`; it must match one `AslState.stateName` value character-for-character. |

### Relationships

| Relationship          | Kind and multiplicity | Meaning in the model                                                                                                  |
| --------------------- | --------------------- | --------------------------------------------------------------------------------------------------------------------- |
| `states` → `AslState` | containment, [+]      | Contains the asl state element(s) that make up this asl document; the contained objects belong to this model element. |

## `AslState`

The common provider-specific state record from which task, choice, wait, pass, succeed, fail, parallel, and map states inherit. It contains the ASL data-flow, timeout, transition, retry, and catch properties shared by those state kinds.

Direct supertypes: `TraceableElement`. Inherited attributes and marker capabilities are documented in the [shared kernel](../shared-kernel.md); this section lists every attribute declared by this class.

### Declared attributes

| Attribute          | Type and multiplicity | What it captures and why it exists                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                           | Accepted values and example                                                                                                  |
| ------------------ | --------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ---------------------------------------------------------------------------------------------------------------------------- |
| `stateName`        | `String` [1]          | The unique key used by ASL transitions and `startAt` to identify this state. It is not merely a display label: duplicate names or a mismatched next/start name make the generated state machine structurally invalid. Semantic validation: `NonTerminalStateHasNextOrTerminalType` (non terminal state has next or terminal type) in `mde/validation/psm/rules/workflow.evl` the rule's diagnostic or remediation guidance refers to this feature. `TerminalStateDoesNotHaveNext` (terminal state does not have next) in `mde/validation/psm/rules/workflow.evl` the rule's diagnostic or remediation guidance refers to this feature. Generation role: The artifact generator references this feature in `mde/generation/awspsm-to-artifacts/lib/contracts.eol`. The artifact generator references this feature in `mde/generation/awspsm-to-artifacts/lib/validation.eol`. | A free-form `String`, subject to this class's semantic meaning and any EVL constraints. Example: `ValidateOrder`.            |
| `queryLanguage`    | `String` [1]          | The language override for this state. It controls whether JSONPath fields (`InputPath`, `ResultPath`, `Parameters`) or JSONata fields (`Arguments`, `Assign`, `Output`) are valid, and PSM EVL explicitly prevents mixing incompatible fields. Semantic validation: `JsonataStateDoesNotUseJsonPathFields` (jsonata state does not use json path fields) in `mde/validation/psm/rules/workflow.evl` the feature participates in a semantic validation condition.                                                                                                                                                                                                                                                                                                                                                                                                             | A free-form `String`, subject to this class's semantic meaning and any EVL constraints. Example: `Asl State Query Language`. |
| `resource`         | `String` [1]          | The service integration ARN/name executed by a task state. For Lambda and nested Step Functions integrations, PSM validation requires either this provider target or a referenced `invokedResource`; it is the actual runtime operation, not a business operation label. Generation role: The artifact generator references this feature in `mde/generation/awspsm-to-artifacts/lib/cfn.eol`. The artifact generator references this feature in `mde/generation/awspsm-to-artifacts/lib/contracts.eol`.                                                                                                                                                                                                                                                                                                                                                                      | A free-form `String`, subject to this class's semantic meaning and any EVL constraints. Example: `Asl State Resource`.       |
| `nextStateName`    | `String` [1]          | The serialized ASL name of the state reached after this state. It exists alongside the typed `nextState` reference because generation emits names into JSON; terminal states must not carry it. Semantic validation: `NonTerminalStateHasNextOrTerminalType` (non terminal state has next or terminal type) in `mde/validation/psm/rules/workflow.evl` the value must be present and non-blank. `TerminalStateDoesNotHaveNext` (terminal state does not have next) in `mde/validation/psm/rules/workflow.evl` the value must be present and non-blank. Generation role: The artifact generator references this feature in `mde/generation/awspsm-to-artifacts/lib/contracts.eol`.                                                                                                                                                                                            | The exact `stateName` of another state, such as `PersistOrder`; omit it for terminal states.                                 |
| `end`              | `Boolean` [1]         | Marks this state as a terminal state in the generated ASL. PSM validation uses it to require/forbid next-state routing and to ensure the document has a reachable termination point. Semantic validation: `NonTerminalStateHasNextOrTerminalType` (non terminal state has next or terminal type) in `mde/validation/psm/rules/workflow.evl` the value must be present and non-blank. `TerminalStateDoesNotHaveNext` (terminal state does not have next) in `mde/validation/psm/rules/workflow.evl` the flag must be enabled for this rule to pass. Generation role: The artifact generator references this feature in `mde/generation/awspsm-to-artifacts/lib/contracts.eol`. The artifact generator references this feature in `mde/generation/awspsm-to-artifacts/lib/protected-regions.eol`.                                                                              | Either `true` or `false`. Example: `false`.                                                                                  |
| `inputPath`        | `String` [1]          | The JSONPath projection applied to the incoming execution data before state processing. It narrows the data visible to the state and is emitted from PIM `inputMapping`; it is invalid for JSONata states. Semantic validation: `JsonataStateDoesNotUseJsonPathFields` (jsonata state does not use json path fields) in `mde/validation/psm/rules/workflow.evl` the value must be present and non-blank.                                                                                                                                                                                                                                                                                                                                                                                                                                                                     | A JSONPath such as `$.order` or `$`; do not populate JSONPath-only fields on a JSONata state.                                |
| `outputPath`       | `String` [1]          | The JSONPath projection used to select what leaves the state. It controls the state-data contract for the next state and is kept distinct from `resultPath`, which decides where the raw result is inserted. Semantic validation: `JsonataStateDoesNotUseJsonPathFields` (jsonata state does not use json path fields) in `mde/validation/psm/rules/workflow.evl` the value must be present and non-blank. Generation role: The artifact generator references this feature in `mde/generation/awspsm-to-artifacts/lib/contracts.eol`.                                                                                                                                                                                                                                                                                                                                        | A JSONPath such as `$.result` or `$`; do not populate JSONPath-only fields on a JSONata state.                               |
| `resultPath`       | `String` [1]          | The JSONPath location where a task/result is merged into the current state. PIM `outputMapping` becomes this value for explicit task mappings; using it carefully prevents a worker result from replacing unrelated workflow context. Semantic validation: `JsonataStateDoesNotUseJsonPathFields` (jsonata state does not use json path fields) in `mde/validation/psm/rules/workflow.evl` the value must be present and non-blank.                                                                                                                                                                                                                                                                                                                                                                                                                                          | A JSONPath destination such as `$.reservationResult` or `null` when the task result must be discarded.                       |
| `parametersJson`   | `String` [1]          | The JSONPath-mode parameter object passed to the service integration. It is the serialized provider-specific call shape and is mutually constrained with JSONata `argumentsJson`/`assignJson` fields. Semantic validation: `JsonataStateDoesNotUseJsonPathFields` (jsonata state does not use json path fields) in `mde/validation/psm/rules/workflow.evl` the value must be present and non-blank. Generation role: The artifact generator references this feature in `mde/generation/awspsm-to-artifacts/lib/contracts.eol`.                                                                                                                                                                                                                                                                                                                                               | A free-form `String`, subject to this class's semantic meaning and any EVL constraints. Example: `{"enabled":true}`.         |
| `argumentsJson`    | `String` [1]          | The JSONata-mode arguments supplied to the service integration. It preserves the structured call contract when the state uses JSONata rather than forcing JSONPath `Parameters` semantics. Generation role: The artifact generator references this feature in `mde/generation/awspsm-to-artifacts/lib/contracts.eol`.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                        | A free-form `String`, subject to this class's semantic meaning and any EVL constraints. Example: `{"enabled":true}`.         |
| `assignJson`       | `String` [1]          | The JSONata assignments that update workflow variables during state evaluation. It is distinct from output projection because assignments change named execution variables rather than only selecting a returned payload. Generation role: The artifact generator references this feature in `mde/generation/awspsm-to-artifacts/lib/contracts.eol`.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                         | A free-form `String`, subject to this class's semantic meaning and any EVL constraints. Example: `{"enabled":true}`.         |
| `timeoutSeconds`   | `Integer` [1]         | The maximum time an ASL task may run before it fails, or the wait duration for an ASL wait state. PIM step timeout/wait intent is translated here, and generation emits `TimeoutSeconds` or `Seconds` according to the concrete state type.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                  | A numeric `Integer` value; use the unit or boundary documented for this attribute. Example: `30`.                            |
| `heartbeatSeconds` | `Integer` [1]         | The interval within which a running activity/task must report progress. It detects a lost worker separately from the overall timeout and is therefore meaningful only when the integration supports heartbeats. Generation role: The artifact generator references this feature in `mde/generation/awspsm-to-artifacts/lib/contracts.eol`.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                   | A numeric `Integer` value; use the unit or boundary documented for this attribute. Example: `1`.                             |

### Relationships

| Relationship                      | Kind and multiplicity | Meaning in the model                                                                                                                |
| --------------------------------- | --------------------- | ----------------------------------------------------------------------------------------------------------------------------------- |
| `retry` → `AslRetryRule`          | containment, [*]      | Contains the asl retry rule element(s) that make up this asl state; the contained objects belong to this model element.             |
| `catch` → `AslCatchRule`          | containment, [*]      | Contains the asl catch rule element(s) that make up this asl state; the contained objects belong to this model element.             |
| `choices` → `AslChoiceRule`       | containment, [*]      | Contains the asl choice rule element(s) that make up this asl state; the contained objects belong to this model element.            |
| `branches` → `AslBranch`          | containment, [*]      | Contains the asl branch element(s) that make up this asl state; the contained objects belong to this model element.                 |
| `mapConfig` → `AslMapConfig`      | containment, [?]      | Contains the asl map config element(s) that make up this asl state; the contained objects belong to this model element.             |
| `nextState` → `AslState`          | reference, [?]        | References the asl state element(s) used as next state by this asl state; the target may be shared elsewhere in the model.          |
| `invokedResource` → `AwsResource` | reference, [?]        | References the aws resource element(s) used as invoked resource by this asl state; the target may be shared elsewhere in the model. |

## `AslPassState`

Represents asl pass state in the PSM vocabulary. It specializes `AslState` with the details needed for this modeling concern.

Direct supertypes: `AslState`. Inherited attributes and marker capabilities are documented in the [shared kernel](../shared-kernel.md); this section lists every attribute declared by this class.

### Declared attributes

This class declares no attributes of its own. It inherits the attributes of its supertype, if any.

### Relationships

This class declares no direct relationships.

## `AslTaskState`

Represents asl task state in the PSM vocabulary. It specializes `AslState` with the details needed for this modeling concern.

Direct supertypes: `AslState`. Inherited attributes and marker capabilities are documented in the [shared kernel](../shared-kernel.md); this section lists every attribute declared by this class.

### Declared attributes

This class declares no attributes of its own. It inherits the attributes of its supertype, if any.

### Relationships

This class declares no direct relationships.

## `AslChoiceState`

Represents asl choice state in the PSM vocabulary. It specializes `AslState` with the details needed for this modeling concern.

Direct supertypes: `AslState`. Inherited attributes and marker capabilities are documented in the [shared kernel](../shared-kernel.md); this section lists every attribute declared by this class.

### Declared attributes

This class declares no attributes of its own. It inherits the attributes of its supertype, if any.

### Relationships

This class declares no direct relationships.

## `AslWaitState`

Represents asl wait state in the PSM vocabulary. It specializes `AslState` with the details needed for this modeling concern.

Direct supertypes: `AslState`. Inherited attributes and marker capabilities are documented in the [shared kernel](../shared-kernel.md); this section lists every attribute declared by this class.

### Declared attributes

This class declares no attributes of its own. It inherits the attributes of its supertype, if any.

### Relationships

This class declares no direct relationships.

## `AslSucceedState`

Represents asl succeed state in the PSM vocabulary. It specializes `AslState` with the details needed for this modeling concern.

Direct supertypes: `AslState`. Inherited attributes and marker capabilities are documented in the [shared kernel](../shared-kernel.md); this section lists every attribute declared by this class.

### Declared attributes

This class declares no attributes of its own. It inherits the attributes of its supertype, if any.

### Relationships

This class declares no direct relationships.

## `AslFailState`

Represents asl fail state in the PSM vocabulary. It specializes `AslState` with the details needed for this modeling concern.

Direct supertypes: `AslState`. Inherited attributes and marker capabilities are documented in the [shared kernel](../shared-kernel.md); this section lists every attribute declared by this class.

### Declared attributes

This class declares no attributes of its own. It inherits the attributes of its supertype, if any.

### Relationships

This class declares no direct relationships.

## `AslParallelState`

Represents asl parallel state in the PSM vocabulary. It specializes `AslState` with the details needed for this modeling concern.

Direct supertypes: `AslState`. Inherited attributes and marker capabilities are documented in the [shared kernel](../shared-kernel.md); this section lists every attribute declared by this class.

### Declared attributes

This class declares no attributes of its own. It inherits the attributes of its supertype, if any.

### Relationships

This class declares no direct relationships.

## `AslMapState`

Represents asl map state in the PSM vocabulary. It specializes `AslState` with the details needed for this modeling concern.

Direct supertypes: `AslState`. Inherited attributes and marker capabilities are documented in the [shared kernel](../shared-kernel.md); this section lists every attribute declared by this class.

### Declared attributes

This class declares no attributes of its own. It inherits the attributes of its supertype, if any.

### Relationships

This class declares no direct relationships.

## `AslBranch`

A self-contained ASL state graph executed as one branch of a parallel state. Its own `startAt` is required because branch entry is independent of the parent document's entry state.

Direct supertypes: `TraceableElement`. Inherited attributes and marker capabilities are documented in the [shared kernel](../shared-kernel.md); this section lists every attribute declared by this class.

### Declared attributes

| Attribute | Type and multiplicity | What it captures and why it exists                                                                                                                                                | Accepted values and example                                                    |
| --------- | --------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------ |
| `startAt` | `String` [1]          | The first state executed inside this parallel branch. It is validated and emitted separately from the parent document's `startAt` because each branch is its own small ASL graph. | The exact name of a state contained by this branch, such as `ProcessLineItem`. |

### Relationships

| Relationship          | Kind and multiplicity | Meaning in the model                                                                                                |
| --------------------- | --------------------- | ------------------------------------------------------------------------------------------------------------------- |
| `states` → `AslState` | containment, [+]      | Contains the asl state element(s) that make up this asl branch; the contained objects belong to this model element. |

## `AslMapConfig`

The AWS-specific map execution settings emitted into a typed ASL map state, including item selection, iterator input, concurrency, distributed mode, and result writer behavior.

Direct supertypes: `TraceableElement`. Inherited attributes and marker capabilities are documented in the [shared kernel](../shared-kernel.md); this section lists every attribute declared by this class.

### Declared attributes

| Attribute          | Type and multiplicity | What it captures and why it exists                                                                                                                                                                                                                                                                                                             | Accepted values and example                                                                                           |
| ------------------ | --------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | --------------------------------------------------------------------------------------------------------------------- |
| `itemsPath`        | `String` [1]          | The JSONPath selecting the collection over which the map state iterates. It is the PSM realization of PIM `MapStateConfig.itemsPath`; changing it changes the set of business items processed by the state machine. Generation role: The artifact generator references this feature in `mde/generation/awspsm-to-artifacts/lib/contracts.eol`. | A free-form `String`, subject to this class's semantic meaning and any EVL constraints. Example: `/orders/{orderId}`. |
| `itemSelectorJson` | `String` [1]          | The JSON object used to construct each iterator's input. It makes parent context and item shape explicit at the ASL boundary rather than relying on worker-specific conventions. Generation role: The artifact generator references this feature in `mde/generation/awspsm-to-artifacts/lib/contracts.eol`.                                    | A free-form `String`, subject to this class's semantic meaning and any EVL constraints. Example: `{"enabled":true}`.  |
| `maxConcurrency`   | `Integer` [1]         | The upper bound on simultaneous map iterations. It is an AWS execution-control setting derived from PIM concurrency intent and protects downstream services from uncontrolled fan-out. Generation role: The artifact generator references this feature in `mde/generation/awspsm-to-artifacts/lib/contracts.eol`.                              | A numeric `Integer` value; use the unit or boundary documented for this attribute. Example: `1`.                      |
| `distributed`      | `Boolean` [1]         | Whether this map uses Distributed Map execution. It selects a materially different Step Functions execution model with different scale, observability, and result-storage behavior; it must not be confused with a normal inline map.                                                                                                          | Either `true` or `false`. Example: `false`.                                                                           |
| `resultWriterJson` | `String` [1]          | The AWS result-writer configuration for a distributed map. It tells Step Functions where to persist iterator results when returning all results in parent state would be too large or expensive.                                                                                                                                               | A free-form `String`, subject to this class's semantic meaning and any EVL constraints. Example: `{"enabled":true}`.  |

### Relationships

| Relationship                  | Kind and multiplicity | Meaning in the model                                                                                                     |
| ----------------------------- | --------------------- | ------------------------------------------------------------------------------------------------------------------------ |
| `itemProcessor` → `AslBranch` | containment, [?]      | Contains the asl branch element(s) that make up this asl map config; the contained objects belong to this model element. |

## `AslRetryRule`

One ASL retry clause that names the errors it handles and the backoff curve it applies. Its numeric bounds are validated because retry timing directly affects reliability, cost, and workflow duration.

Direct supertypes: `TraceableElement`. Inherited attributes and marker capabilities are documented in the [shared kernel](../shared-kernel.md); this section lists every attribute declared by this class.

### Declared attributes

| Attribute         | Type and multiplicity | What it captures and why it exists                                                                                                                                                                                                                                                                                                                                                                                                                                                       | Accepted values and example                                                                                                        |
| ----------------- | --------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ---------------------------------------------------------------------------------------------------------------------------------- |
| `errorEquals`     | `String` [*]          | The ASL error names to which this retry policy applies. The list is the boundary that prevents transient recovery from masking permanent/business failures.                                                                                                                                                                                                                                                                                                                              | One or more ASL error names, for example [`States.Timeout`, `Lambda.ServiceException`].                                            |
| `intervalSeconds` | `Integer` [1]         | The delay before the first retry. PSM validation requires at least one second when supplied, and the value is emitted into ASL `IntervalSeconds`. Semantic validation: `RetryRuleRangesValid` (retry rule ranges valid) in `mde/validation/psm/rules/workflow.evl` the value must remain absent in this modeling situation. Generation role: The artifact generator references this feature in `mde/generation/awspsm-to-artifacts/lib/contracts.eol`.                                   | An integer of at least `1`, such as `2` or `30`.                                                                                   |
| `maxAttempts`     | `Integer` [1]         | The number of retry attempts after the initial execution. Zero deliberately means no retry; it is not the same as an omitted policy, which may leave the service default in effect. Semantic validation: `RetryRuleRangesValid` (retry rule ranges valid) in `mde/validation/psm/rules/workflow.evl` the value must remain absent in this modeling situation. Generation role: The artifact generator references this feature in `mde/generation/awspsm-to-artifacts/lib/contracts.eol`. | An integer of `0` or greater; for example `0` disables retries and `3` permits three retry attempts.                               |
| `backoffRate`     | `Double` [1]          | The multiplier applied between retry delays. PSM validation requires a value of at least `1.0`, making the retry curve explicit rather than hiding it in a provider default. Semantic validation: `RetryRuleRangesValid` (retry rule ranges valid) in `mde/validation/psm/rules/workflow.evl` the value must remain absent in this modeling situation. Generation role: The artifact generator references this feature in `mde/generation/awspsm-to-artifacts/lib/contracts.eol`.        | A number of at least `1.0`, such as `2.0` for exponential backoff.                                                                 |
| `maxDelaySeconds` | `Integer` [1]         | The ceiling on exponential retry delay. It prevents a mathematically growing backoff from exceeding the workflow's business timing budget. Semantic validation: `RetryRuleRangesValid` (retry rule ranges valid) in `mde/validation/psm/rules/workflow.evl` the value must remain absent in this modeling situation. Generation role: The artifact generator references this feature in `mde/generation/awspsm-to-artifacts/lib/contracts.eol`.                                          | An integer of at least `1`, such as `300`.                                                                                         |
| `jitterStrategy`  | `String` [1]          | The randomization strategy for retry delay, used to avoid synchronized retry storms. It is retained as an AWS-specific operational decision in the PSM. Generation role: The artifact generator references this feature in `mde/generation/awspsm-to-artifacts/lib/contracts.eol`.                                                                                                                                                                                                       | A free-form `String`, subject to this class's semantic meaning and any EVL constraints. Example: `Asl Retry Rule Jitter Strategy`. |

### Relationships

This class declares no direct relationships.

## `AslCatchRule`

One ASL catch clause that routes named failures to a recovery state and optionally stores the error in a result path. It is the provider-specific form of workflow error handling.

Direct supertypes: `TraceableElement`. Inherited attributes and marker capabilities are documented in the [shared kernel](../shared-kernel.md); this section lists every attribute declared by this class.

### Declared attributes

| Attribute     | Type and multiplicity | What it captures and why it exists                                                                                                                                                                                                                                                                                                                  | Accepted values and example                                                                                           |
| ------------- | --------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | --------------------------------------------------------------------------------------------------------------------- |
| `errorEquals` | `String` [*]          | The ASL errors routed into this catch path. A catch rule without an error list has no defined ownership of failures, which is why PSM EVL requires the collection. Semantic validation: `CatchRuleHasErrorsAndNext` (catch rule has errors and next) in `mde/validation/psm/rules/workflow.evl` the collection or referenced set must not be empty. | One or more ASL error names, for example [`States.ALL`] or [`States.Timeout`, `States.TaskFailed`].                   |
| `resultPath`  | `String` [1]          | The location where the caught error is placed before the recovery state runs. It lets recovery logic inspect failure context without destroying the rest of the execution input.                                                                                                                                                                    | A free-form `String`, subject to this class's semantic meaning and any EVL constraints. Example: `/orders/{orderId}`. |

### Relationships

| Relationship             | Kind and multiplicity | Meaning in the model                                                                                                            |
| ------------------------ | --------------------- | ------------------------------------------------------------------------------------------------------------------------------- |
| `nextState` → `AslState` | reference, [1]        | References the asl state element(s) used as next state by this asl catch rule; the target may be shared elsewhere in the model. |

## `AslChoiceRule`

One ASL choice branch, represented by its condition/variable and target state. It is the provider-specific executable counterpart to a PIM workflow transition condition.

Direct supertypes: `TraceableElement`. Inherited attributes and marker capabilities are documented in the [shared kernel](../shared-kernel.md); this section lists every attribute declared by this class.

### Declared attributes

| Attribute             | Type and multiplicity | What it captures and why it exists                                                                                                                                                                                                                                                                                                                                                                                                                                              | Accepted values and example                                                                                     |
| --------------------- | --------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | --------------------------------------------------------------------------------------------------------------- |
| `conditionExpression` | `String` [1]          | The full ASL condition for this choice branch when the condition is represented as an expression. It is the provider-specific evaluation rule, not a human label. Semantic validation: `ChoiceRuleHasConditionAndNext` (choice rule has condition and next) in `mde/validation/psm/rules/workflow.evl` the value must be present and non-blank. Generation role: The artifact generator references this feature in `mde/generation/awspsm-to-artifacts/lib/contracts.eol`.      | A valid ASL/JSONata choice expression, such as `{% $states.input.status = 'READY' %}` when JSONata is selected. |
| `variable`            | `String` [1]          | The state-data variable inspected by a choice rule. It is the left-hand data location used with the generated comparison operator/expression to select the next state. Semantic validation: `ChoiceRuleHasConditionAndNext` (choice rule has condition and next) in `mde/validation/psm/rules/workflow.evl` the value must be present and non-blank. Generation role: The artifact generator references this feature in `mde/generation/awspsm-to-artifacts/lib/contracts.eol`. | A JSONPath variable such as `$.order.status`, paired with a comparison in the generated choice rule.            |

### Relationships

| Relationship             | Kind and multiplicity | Meaning in the model                                                                                                             |
| ------------------------ | --------------------- | -------------------------------------------------------------------------------------------------------------------------------- |
| `nextState` → `AslState` | reference, [1]        | References the asl state element(s) used as next state by this asl choice rule; the target may be shared elsewhere in the model. |

## `StepFunctionLoggingConfig`

The execution-history logging policy for a Step Functions state machine, including detail level, payload inclusion, and the CloudWatch destination. It makes observability and sensitive-data exposure explicit.

Direct supertypes: `TraceableElement`. Inherited attributes and marker capabilities are documented in the [shared kernel](../shared-kernel.md); this section lists every attribute declared by this class.

### Declared attributes

| Attribute              | Type and multiplicity | What it captures and why it exists                                                                                                                                                                                                                                                                                                                                                                                             | Accepted values and example                                                                              |
| ---------------------- | --------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ | -------------------------------------------------------------------------------------------------------- |
| `includeExecutionData` | `Boolean` [1]         | Whether execution input and output data are included in Step Functions logs. It is a sensitive-data and troubleshooting trade-off, so production configuration should set it deliberately rather than inheriting an opaque default. Generation role: The artifact generator references this feature in `mde/generation/awspsm-to-artifacts/lib/cfn.eol`.                                                                       | Either `true` or `false`. Example: `false`.                                                              |
| `level`                | `String` [1]          | The Step Functions event severity level sent to the log group. It balances diagnostic detail against log volume and cost and is emitted as the provider logging configuration. Generation role: The artifact generator references this feature in `mde/generation/awspsm-to-artifacts/lib/cfn.eol`. The artifact generator references this feature in `mde/generation/awspsm-to-artifacts/templates/lambda/shared-logger.egl`. | A free-form `String`, subject to this class's semantic meaning and any EVL constraints. Example: `INFO`. |

### Relationships

| Relationship                      | Kind and multiplicity | Meaning in the model                                                                                                                                     |
| --------------------------------- | --------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `logGroup` → `CloudWatchLogGroup` | reference, [1]        | References the cloud watch log group element(s) used as log group by this step function logging config; the target may be shared elsewhere in the model. |

## `StepFunctionTracingConfig`

Represents step function tracing config in the PSM vocabulary. It specializes `TracingConfig` with the details needed for this modeling concern.

Direct supertypes: `TracingConfig`. Inherited attributes and marker capabilities are documented in the [shared kernel](../shared-kernel.md); this section lists every attribute declared by this class.

### Declared attributes

This class declares no attributes of its own. It inherits the attributes of its supertype, if any.

### Relationships

This class declares no direct relationships.

## `SamStateMachineEvent`

A SAM event binding that starts the state machine from an external trigger. Its event type selects the native event contract, while native properties preserve provider details that are not yet typed.

Direct supertypes: `TraceableElement`. Inherited attributes and marker capabilities are documented in the [shared kernel](../shared-kernel.md); this section lists every attribute declared by this class.

### Declared attributes

| Attribute   | Type and multiplicity | What it captures and why it exists                                                                                                                                               | Accepted values and example                                                                                                     |
| ----------- | --------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------- |
| `eventName` | `String` [1]          | The stable logical name of an event source that starts the state machine through SAM. It gives the generated event mapping an identity for review and CloudFormation references. | A free-form `String`, subject to this class's semantic meaning and any EVL constraints. Example: `SamStateMachineEventExample`. |
| `eventType` | `String` [1]          | The kind of SAM event binding, such as EventBridge, API, or schedule. It selects which native event properties are meaningful and how the state-machine target is wired.         | A free-form `String`, subject to this class's semantic meaning and any EVL constraints. Example: `BUSINESS`.                    |

### Relationships

| Relationship                    | Kind and multiplicity | Meaning in the model                                                                                                                   |
| ------------------------------- | --------------------- | -------------------------------------------------------------------------------------------------------------------------------------- |
| `properties` → `NativeProperty` | containment, [*]      | Contains the native property element(s) that make up this sam state machine event; the contained objects belong to this model element. |
