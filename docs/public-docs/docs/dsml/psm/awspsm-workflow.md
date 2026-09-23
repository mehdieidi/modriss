# Step Functions and Amazon States Language

Workflow classes represent Step Functions state machines and their ASL documents, state types, branches, map processors, retries, catches, logging, and tracing.

Source: `mde/metamodels/psm/awspsm-workflow.emf`.

## `StepFunctionStateMachine`

An AWS Step Functions state-machine resource. It binds the provider-independent workflow to an ASL document, IAM role, logging, tracing, aliases, and SAM event sources.

Direct supertypes: `AwsResource`. Inherited attributes and marker capabilities are documented in the [shared kernel](../shared-kernel.md); this section lists every attribute declared by this class.

### Declared attributes

| Attribute                     | Type and multiplicity  | What it captures and why it exists                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                 | Accepted values and example                                                                                                                                            |
| ----------------------------- | ---------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `stateMachineName`            | `String` [1]           | The deployed Step Functions name presented to operators and promotion tooling. It is separate from the CloudFormation logical ID because a stable operational name and a template identity have different replacement and environment concerns. Transformation role: ETL rule `Workflow2StepFunctionStateMachine` in `mde/transformations/pim-to-awspsm/workflow-security-config.etl` assigns or materializes this feature while refining `StepFunctionStateMachine`. Generation role: The artifact generator references this feature in `mde/generation/awspsm-to-artifacts/lib/cfn.eol`. The artifact generator references this feature in `mde/generation/awspsm-to-artifacts/lib/contracts.eol`.                                                                                                                                                                                               | A free-form `String`, subject to this class's semantic meaning and any EVL constraints. Example: `OrdersWorkflow`.                                                     |
| `definitionUri`               | `String` [1]           | The artifact location containing the ASL definition when the state machine is not assembled from the typed `AslDocument`. EVL requires exactly one definition source, preventing a file and modeled states from silently disagreeing. Semantic validation: `StateMachineHasDefinition` (state machine has definition) in `mde/validation/psm/rules/workflow.evl` the rule's diagnostic or remediation guidance refers to this feature. `StateMachineShouldUseSingleDefinitionSource` (state machine should use single definition source) in `mde/validation/psm/rules/workflow.evl` the rule's diagnostic or remediation guidance refers to this feature.                                                                                                                                                                                                                                          | A free-form `String`, subject to this class's semantic meaning and any EVL constraints. Example: `https://example.com/orders`.                                         |
| `definitionString`            | `String` [1]           | An inline ASL definition supplied directly to the resource. It is useful for a deliberately external/hand-authored definition, but it competes with `definitionUri` and `aslDocument`, so PSM validation treats multiple sources as ambiguous. Semantic validation: `StateMachineHasDefinition` (state machine has definition) in `mde/validation/psm/rules/workflow.evl` the rule's diagnostic or remediation guidance refers to this feature. `StateMachineShouldUseSingleDefinitionSource` (state machine should use single definition source) in `mde/validation/psm/rules/workflow.evl` the rule's diagnostic or remediation guidance refers to this feature. Generation role: The artifact generator references this feature in `mde/generation/awspsm-to-artifacts/lib/cfn.eol`. The artifact generator references this feature in `mde/generation/awspsm-to-artifacts/lib/validation.eol`. | A free-form `String`, subject to this class's semantic meaning and any EVL constraints. Example: `A confirmed order is one accepted for fulfillment by the business.`. |
| `definitionSubstitutionsJson` | `String` [1]           | The substitution map applied while materializing the state-machine definition, typically for resource ARNs or stage-specific values. Keeping substitutions explicit lets generation bind deployment values without embedding environment-specific strings in business workflow logic. Generation role: The artifact generator references this feature in `mde/generation/awspsm-to-artifacts/lib/cfn.eol`.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                         | A free-form `String`, subject to this class's semantic meaning and any EVL constraints. Example: `{"enabled":true}`.                                                   |
| `publishAlias`                | `Boolean` [1]          | The boolean decision for publish alias on this step function state machine. It keeps an important design choice explicit for review and transformation. Semantic validation: `PublishAliasRequiresAliasName` (publish alias requires alias name) in `mde/validation/psm/rules/workflow.evl` the flag must be enabled for this rule to pass. Transformation role: ETL rule `Workflow2StepFunctionStateMachine` in `mde/transformations/pim-to-awspsm/workflow-security-config.etl` assigns or materializes this feature while refining `StepFunctionStateMachine`. Generation role: The artifact generator references this feature in `mde/generation/awspsm-to-artifacts/lib/cfn.eol`.                                                                                                                                                                                                             | Either `true` or `false`. Example: `false`.                                                                                                                            |
| `aliasName`                   | `String` [1]           | The stable alias through which executions address a published state-machine version. It is required only when alias publication is requested and supports controlled promotion/rollback independently of the latest definition. Semantic validation: `PublishAliasRequiresAliasName` (publish alias requires alias name) in `mde/validation/psm/rules/workflow.evl` the value must be present and non-blank. Transformation role: ETL rule `Workflow2StepFunctionStateMachine` in `mde/transformations/pim-to-awspsm/workflow-security-config.etl` assigns or materializes this feature while refining `StepFunctionStateMachine`.                                                                                                                                                                                                                                                                 | A free-form `String`, subject to this class's semantic meaning and any EVL constraints. Example: `StepFunctionStateMachineExample`.                                    |
| `stateMachineType`            | `StepFunctionType` [1] | The AWS execution type, such as Standard or Express. This is where the provider-specific durability, history, latency, and pricing choice becomes concrete after PIM workflow intent has been evaluated. Transformation role: ETL rule `Workflow2StepFunctionStateMachine` in `mde/transformations/pim-to-awspsm/workflow-security-config.etl` assigns or materializes this feature while refining `StepFunctionStateMachine`. Generation role: The artifact generator references this feature in `mde/generation/awspsm-to-artifacts/lib/cfn.eol`.                                                                                                                                                                                                                                                                                                                                                | Exactly one of: `STANDARD`, `EXPRESS`. Example: `STANDARD`.                                                                                                            |

### Relationships

| Relationship                            | Kind and multiplicity | Meaning in the model                                                                                                                                                                                                                                     |
| --------------------------------------- | --------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `logging` → `StepFunctionLoggingConfig` | containment, [?]      | The `logging` containment on `StepFunctionStateMachine` attaches the state-machine logging configuration. The `StepFunctionLoggingConfig` objects are owned by `StepFunctionStateMachine` and remain part of its model subtree.                          |
| `tracing` → `StepFunctionTracingConfig` | containment, [?]      | The `tracing` containment on `StepFunctionStateMachine` attaches the tracing configuration for this executable resource. The `StepFunctionTracingConfig` objects are owned by `StepFunctionStateMachine` and remain part of its model subtree.           |
| `aslDocument` → `AslDocument`           | containment, [?]      | The `aslDocument` containment on `StepFunctionStateMachine` attaches the structured ASL state graph generated for the workflow. The `AslDocument` objects are owned by `StepFunctionStateMachine` and remain part of its model subtree.                  |
| `samEvents` → `SamStateMachineEvent`    | containment, [*]      | The `samEvents` containment on `StepFunctionStateMachine` connects this element to the event or message path represented by sam events. The `SamStateMachineEvent` objects are owned by `StepFunctionStateMachine` and remain part of its model subtree. |
| `role` → `IamRole`                      | reference, [1]        | The `role` reference on `StepFunctionStateMachine` attaches the execution role that grants the resource its AWS permissions. An `IamRole` can remain independently owned and can participate in other parts of the model.                                |

## `AslDocument`

The structured Amazon States Language document owned by a Step Functions state machine. It supplies the start state and the contained state graph that generation renders as JSON.

Direct supertypes: `StructuredDocument`. Inherited attributes and marker capabilities are documented in the [shared kernel](../shared-kernel.md); this section lists every attribute declared by this class.

### Declared attributes

| Attribute       | Type and multiplicity | What it captures and why it exists                                                                                                                                                                                                                                                                                                                                                                                                  | Accepted values and example                                                                                                         |
| --------------- | --------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------- |
| `comment`       | `String` [1]          | The explanatory comment embedded in the ASL document. It helps operators understand the generated state machine without changing execution, and gives generation a safe place for provenance/context that should not be encoded as a fake state. Generation role: The artifact generator references this feature in `mde/generation/awspsm-to-artifacts/lib/contracts.eol`.                                                         | A free-form `String`, subject to this class's semantic meaning and any EVL constraints. Example: `Asl Document Comment`.            |
| `queryLanguage` | `String` [1]          | The default expression language for states in the document, normally JSONPath or JSONata. It establishes which path/expression fields are legal; state-level overrides may specialize it, while validation rejects JSONPath-only fields on JSONata states.                                                                                                                                                                          | A free-form `String`, subject to this class's semantic meaning and any EVL constraints. Example: `Asl Document Query Language`.     |
| `startAt`       | `String` [1]          | The exact `stateName` at which an execution begins. PIM-to-AWS derives it from the first ordered PIM start step when building typed ASL; PSM validation checks that it names an actual state, because a typo prevents every execution from starting. Semantic validation: `StartAtReferencesExistingState` (start at references existing state) in `mde/validation/psm/rules/workflow.evl` the value must be present and non-blank. | The exact name of a contained state, such as `ValidateOrder`; it must match one `AslState.stateName` value character-for-character. |

### Relationships

| Relationship          | Kind and multiplicity | Meaning in the model                                                                                                                                                                                |
| --------------------- | --------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `states` → `AslState` | containment, [+]      | The `states` containment on `AslDocument` owns the state objects that form the document's executable graph. The `AslState` objects are owned by `AslDocument` and remain part of its model subtree. |

## `AslState`

The common state representation for an ASL document. It carries paths, transitions, timeout, retry, catch, branch, map, and invoked-resource details shared by concrete state types.

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

| Relationship                      | Kind and multiplicity | Meaning in the model                                                                                                                                                                                  |
| --------------------------------- | --------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `retry` → `AslRetryRule`          | containment, [*]      | The `retry` containment on `AslState` keeps the retry policies applied to this state. The `AslRetryRule` objects are owned by `AslState` and remain part of its model subtree.                        |
| `catch` → `AslCatchRule`          | containment, [*]      | The `catch` containment on `AslState` keeps the failure redirections applied to this state. The `AslCatchRule` objects are owned by `AslState` and remain part of its model subtree.                  |
| `choices` → `AslChoiceRule`       | containment, [*]      | The `choices` containment on `AslState` keeps the conditional branches available from this state. The `AslChoiceRule` objects are owned by `AslState` and remain part of its model subtree.           |
| `branches` → `AslBranch`          | containment, [*]      | The `branches` containment on `AslState` owns the parallel or mapped workflow branches. The `AslBranch` objects are owned by `AslState` and remain part of its model subtree.                         |
| `mapConfig` → `AslMapConfig`      | containment, [?]      | The `mapConfig` containment on `AslState` attaches the collection-processing configuration to the map state. The `AslMapConfig` objects are owned by `AslState` and remain part of its model subtree. |
| `nextState` → `AslState`          | reference, [?]        | The state entered after this state or rule completes.                                                                                                                                                 |
| `invokedResource` → `AwsResource` | reference, [?]        | The `invokedResource` reference on `AslState` identifies the source represented by invoked resource. An `AwsResource` can remain independently owned and can participate in other parts of the model. |

## `AslPassState`

`AslPassState` is a Step Functions deployment record in the AWS platform-specific model. It carries the orchestration setting for asl pass state. Its declaration gives the concept a precise home through its declared properties. Its references provide the wiring that lets generation assemble the corresponding AWS design.

Direct supertypes: `AslState`. Inherited attributes and marker capabilities are documented in the [shared kernel](../shared-kernel.md); this section lists every attribute declared by this class.

### Declared attributes

This class declares no attributes of its own. It inherits the attributes of its supertype, if any.

### Relationships

This class declares no direct relationships.

## `AslTaskState`

An ASL task state that invokes work or an AWS integration. It is where the generated state machine connects an orchestration step to a function or resource.

Direct supertypes: `AslState`. Inherited attributes and marker capabilities are documented in the [shared kernel](../shared-kernel.md); this section lists every attribute declared by this class.

### Declared attributes

This class declares no attributes of its own. It inherits the attributes of its supertype, if any.

### Relationships

This class declares no direct relationships.

## `AslChoiceState`

An ASL choice state that selects a next state using choice rules. It gives generated routing a structured home instead of leaving the branch as raw JSON.

Direct supertypes: `AslState`. Inherited attributes and marker capabilities are documented in the [shared kernel](../shared-kernel.md); this section lists every attribute declared by this class.

### Declared attributes

This class declares no attributes of its own. It inherits the attributes of its supertype, if any.

### Relationships

This class declares no direct relationships.

## `AslWaitState`

`AslWaitState` is a Step Functions deployment record in the AWS platform-specific model. It carries the orchestration setting for asl wait state. Its declaration gives the concept a precise home through its declared properties. Its references provide the wiring that lets generation assemble the corresponding AWS design.

Direct supertypes: `AslState`. Inherited attributes and marker capabilities are documented in the [shared kernel](../shared-kernel.md); this section lists every attribute declared by this class.

### Declared attributes

This class declares no attributes of its own. It inherits the attributes of its supertype, if any.

### Relationships

This class declares no direct relationships.

## `AslSucceedState`

`AslSucceedState` is a Step Functions deployment record in the AWS platform-specific model. It carries the orchestration setting for asl succeed state. Its declaration gives the concept a precise home through its declared properties. Its references provide the wiring that lets generation assemble the corresponding AWS design.

Direct supertypes: `AslState`. Inherited attributes and marker capabilities are documented in the [shared kernel](../shared-kernel.md); this section lists every attribute declared by this class.

### Declared attributes

This class declares no attributes of its own. It inherits the attributes of its supertype, if any.

### Relationships

This class declares no direct relationships.

## `AslFailState`

`AslFailState` is a Step Functions deployment record in the AWS platform-specific model. It carries the orchestration setting for asl fail state. Its declaration gives the concept a precise home through its declared properties. Its references provide the wiring that lets generation assemble the corresponding AWS design.

Direct supertypes: `AslState`. Inherited attributes and marker capabilities are documented in the [shared kernel](../shared-kernel.md); this section lists every attribute declared by this class.

### Declared attributes

This class declares no attributes of its own. It inherits the attributes of its supertype, if any.

### Relationships

This class declares no direct relationships.

## `AslParallelState`

An ASL parallel state that runs branches concurrently. Its branch containments preserve the parallel structure that came from the PIM workflow.

Direct supertypes: `AslState`. Inherited attributes and marker capabilities are documented in the [shared kernel](../shared-kernel.md); this section lists every attribute declared by this class.

### Declared attributes

This class declares no attributes of its own. It inherits the attributes of its supertype, if any.

### Relationships

This class declares no direct relationships.

## `AslMapState`

An ASL map state that processes collection items. Its map configuration controls item selection, processor branches, concurrency, distribution, and result writing.

Direct supertypes: `AslState`. Inherited attributes and marker capabilities are documented in the [shared kernel](../shared-kernel.md); this section lists every attribute declared by this class.

### Declared attributes

This class declares no attributes of its own. It inherits the attributes of its supertype, if any.

### Relationships

This class declares no direct relationships.

## `AslBranch`

`AslBranch` is a Step Functions deployment record in the AWS platform-specific model. It carries the orchestration setting for asl branch. Its declaration gives the concept a precise home through states. Its references provide the wiring that lets generation assemble the corresponding AWS design.

Direct supertypes: `TraceableElement`. Inherited attributes and marker capabilities are documented in the [shared kernel](../shared-kernel.md); this section lists every attribute declared by this class.

### Declared attributes

| Attribute | Type and multiplicity | What it captures and why it exists                                                                                                                                                | Accepted values and example                                                    |
| --------- | --------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------ |
| `startAt` | `String` [1]          | The first state executed inside this parallel branch. It is validated and emitted separately from the parent document's `startAt` because each branch is its own small ASL graph. | The exact name of a state contained by this branch, such as `ProcessLineItem`. |

### Relationships

| Relationship          | Kind and multiplicity | Meaning in the model                                                                                                                                                                            |
| --------------------- | --------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `states` → `AslState` | containment, [+]      | The `states` containment on `AslBranch` owns the state objects that form the document's executable graph. The `AslState` objects are owned by `AslBranch` and remain part of its model subtree. |

## `AslMapConfig`

`AslMapConfig` is a Step Functions deployment record in the AWS platform-specific model. It carries the orchestration setting for asl map config. Its declaration gives the concept a precise home through item processor. Its references provide the wiring that lets generation assemble the corresponding AWS design.

Direct supertypes: `TraceableElement`. Inherited attributes and marker capabilities are documented in the [shared kernel](../shared-kernel.md); this section lists every attribute declared by this class.

### Declared attributes

| Attribute          | Type and multiplicity | What it captures and why it exists                                                                                                                                                                                                                                                                                                             | Accepted values and example                                                                                           |
| ------------------ | --------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | --------------------------------------------------------------------------------------------------------------------- |
| `itemsPath`        | `String` [1]          | The JSONPath selecting the collection over which the map state iterates. It is the PSM realization of PIM `MapStateConfig.itemsPath`; changing it changes the set of business items processed by the state machine. Generation role: The artifact generator references this feature in `mde/generation/awspsm-to-artifacts/lib/contracts.eol`. | A free-form `String`, subject to this class's semantic meaning and any EVL constraints. Example: `/orders/{orderId}`. |
| `itemSelectorJson` | `String` [1]          | The JSON object used to construct each iterator's input. It makes parent context and item shape explicit at the ASL boundary rather than relying on worker-specific conventions. Generation role: The artifact generator references this feature in `mde/generation/awspsm-to-artifacts/lib/contracts.eol`.                                    | A free-form `String`, subject to this class's semantic meaning and any EVL constraints. Example: `{"enabled":true}`.  |
| `maxConcurrency`   | `Integer` [1]         | The upper bound on simultaneous map iterations. It is an AWS execution-control setting derived from PIM concurrency intent and protects downstream services from uncontrolled fan-out. Generation role: The artifact generator references this feature in `mde/generation/awspsm-to-artifacts/lib/contracts.eol`.                              | A numeric `Integer` value; use the unit or boundary documented for this attribute. Example: `1`.                      |
| `distributed`      | `Boolean` [1]         | The boolean decision for distributed on this asl map config. It keeps an important design choice explicit for review and transformation.                                                                                                                                                                                                       | Either `true` or `false`. Example: `false`.                                                                           |
| `resultWriterJson` | `String` [1]          | The AWS result-writer configuration for a distributed map. It tells Step Functions where to persist iterator results when returning all results in parent state would be too large or expensive.                                                                                                                                               | A free-form `String`, subject to this class's semantic meaning and any EVL constraints. Example: `{"enabled":true}`.  |

### Relationships

| Relationship                  | Kind and multiplicity | Meaning in the model                                           |
| ----------------------------- | --------------------- | -------------------------------------------------------------- |
| `itemProcessor` → `AslBranch` | containment, [?]      | The branch that processes each item selected by the Map state. |

## `AslRetryRule`

An ASL retry rule attached to a state. It specifies which errors are retried and how interval, attempts, backoff, delay, and jitter are applied.

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

An ASL catch rule that redirects a failed state to a recovery state. It records matching errors and the result path used to preserve failure data.

Direct supertypes: `TraceableElement`. Inherited attributes and marker capabilities are documented in the [shared kernel](../shared-kernel.md); this section lists every attribute declared by this class.

### Declared attributes

| Attribute     | Type and multiplicity | What it captures and why it exists                                                                                                                                                                                                                                                                                                                  | Accepted values and example                                                                                           |
| ------------- | --------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | --------------------------------------------------------------------------------------------------------------------- |
| `errorEquals` | `String` [*]          | The ASL errors routed into this catch path. A catch rule without an error list has no defined ownership of failures, which is why PSM EVL requires the collection. Semantic validation: `CatchRuleHasErrorsAndNext` (catch rule has errors and next) in `mde/validation/psm/rules/workflow.evl` the collection or referenced set must not be empty. | One or more ASL error names, for example [`States.ALL`] or [`States.Timeout`, `States.TaskFailed`].                   |
| `resultPath`  | `String` [1]          | The location where the caught error is placed before the recovery state runs. It lets recovery logic inspect failure context without destroying the rest of the execution input.                                                                                                                                                                    | A free-form `String`, subject to this class's semantic meaning and any EVL constraints. Example: `/orders/{orderId}`. |

### Relationships

| Relationship             | Kind and multiplicity | Meaning in the model                                  |
| ------------------------ | --------------------- | ----------------------------------------------------- |
| `nextState` → `AslState` | reference, [1]        | The state entered after this state or rule completes. |

## `AslChoiceRule`

`AslChoiceRule` is a Step Functions deployment record in the AWS platform-specific model. It carries the orchestration setting for asl choice rule. Its declaration gives the concept a precise home through next state. Its references provide the wiring that lets generation assemble the corresponding AWS design.

Direct supertypes: `TraceableElement`. Inherited attributes and marker capabilities are documented in the [shared kernel](../shared-kernel.md); this section lists every attribute declared by this class.

### Declared attributes

| Attribute             | Type and multiplicity | What it captures and why it exists                                                                                                                                                                                                                                                                                                                                                                                                                                              | Accepted values and example                                                                                     |
| --------------------- | --------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | --------------------------------------------------------------------------------------------------------------- |
| `conditionExpression` | `String` [1]          | The full ASL condition for this choice branch when the condition is represented as an expression. It is the provider-specific evaluation rule, not a human label. Semantic validation: `ChoiceRuleHasConditionAndNext` (choice rule has condition and next) in `mde/validation/psm/rules/workflow.evl` the value must be present and non-blank. Generation role: The artifact generator references this feature in `mde/generation/awspsm-to-artifacts/lib/contracts.eol`.      | A valid ASL/JSONata choice expression, such as `{% $states.input.status = 'READY' %}` when JSONata is selected. |
| `variable`            | `String` [1]          | The state-data variable inspected by a choice rule. It is the left-hand data location used with the generated comparison operator/expression to select the next state. Semantic validation: `ChoiceRuleHasConditionAndNext` (choice rule has condition and next) in `mde/validation/psm/rules/workflow.evl` the value must be present and non-blank. Generation role: The artifact generator references this feature in `mde/generation/awspsm-to-artifacts/lib/contracts.eol`. | A JSONPath variable such as `$.order.status`, paired with a comparison in the generated choice rule.            |

### Relationships

| Relationship             | Kind and multiplicity | Meaning in the model                                  |
| ------------------------ | --------------------- | ----------------------------------------------------- |
| `nextState` → `AslState` | reference, [1]        | The state entered after this state or rule completes. |

## `StepFunctionLoggingConfig`

`StepFunctionLoggingConfig` is a Step Functions deployment record in the AWS platform-specific model. It carries the orchestration setting for step function logging config. Its declaration gives the concept a precise home through log group. Its references provide the wiring that lets generation assemble the corresponding AWS design.

Direct supertypes: `TraceableElement`. Inherited attributes and marker capabilities are documented in the [shared kernel](../shared-kernel.md); this section lists every attribute declared by this class.

### Declared attributes

| Attribute              | Type and multiplicity | What it captures and why it exists                                                                                                                                                                                                                                                                                                                                                                                             | Accepted values and example                                                                              |
| ---------------------- | --------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ | -------------------------------------------------------------------------------------------------------- |
| `includeExecutionData` | `Boolean` [1]         | The boolean decision for include execution data on this step function logging config. It keeps an important design choice explicit for review and transformation. Generation role: The artifact generator references this feature in `mde/generation/awspsm-to-artifacts/lib/cfn.eol`.                                                                                                                                         | Either `true` or `false`. Example: `false`.                                                              |
| `level`                | `String` [1]          | The Step Functions event severity level sent to the log group. It balances diagnostic detail against log volume and cost and is emitted as the provider logging configuration. Generation role: The artifact generator references this feature in `mde/generation/awspsm-to-artifacts/lib/cfn.eol`. The artifact generator references this feature in `mde/generation/awspsm-to-artifacts/templates/lambda/shared-logger.egl`. | A free-form `String`, subject to this class's semantic meaning and any EVL constraints. Example: `INFO`. |

### Relationships

| Relationship                      | Kind and multiplicity | Meaning in the model                                                                                                                                                                                                                   |
| --------------------------------- | --------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `logGroup` → `CloudWatchLogGroup` | reference, [1]        | The `logGroup` reference on `StepFunctionLoggingConfig` selects the CloudWatch log group that receives this resource's records. A `CloudWatchLogGroup` can remain independently owned and can participate in other parts of the model. |

## `StepFunctionTracingConfig`

`StepFunctionTracingConfig` is a Step Functions deployment record in the AWS platform-specific model. It carries the orchestration setting for step function tracing config. Its declaration gives the concept a precise home through its declared properties. Its references provide the wiring that lets generation assemble the corresponding AWS design.

Direct supertypes: `TracingConfig`. Inherited attributes and marker capabilities are documented in the [shared kernel](../shared-kernel.md); this section lists every attribute declared by this class.

### Declared attributes

This class declares no attributes of its own. It inherits the attributes of its supertype, if any.

### Relationships

This class declares no direct relationships.

## `SamStateMachineEvent`

A SAM event declaration owned by a Step Functions state machine. Its type selects the trigger mechanism and its native properties retain the provider configuration.

Direct supertypes: `TraceableElement`. Inherited attributes and marker capabilities are documented in the [shared kernel](../shared-kernel.md); this section lists every attribute declared by this class.

### Declared attributes

| Attribute   | Type and multiplicity | What it captures and why it exists                                                                                                                                               | Accepted values and example                                                                                                     |
| ----------- | --------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------- |
| `eventName` | `String` [1]          | The stable logical name of an event source that starts the state machine through SAM. It gives the generated event mapping an identity for review and CloudFormation references. | A free-form `String`, subject to this class's semantic meaning and any EVL constraints. Example: `SamStateMachineEventExample`. |
| `eventType` | `String` [1]          | The kind of SAM event binding, such as EventBridge, API, or schedule. It selects which native event properties are meaningful and how the state-machine target is wired.         | A free-form `String`, subject to this class's semantic meaning and any EVL constraints. Example: `BUSINESS`.                    |

### Relationships

| Relationship                    | Kind and multiplicity | Meaning in the model                                                             |
| ------------------------------- | --------------------- | -------------------------------------------------------------------------------- |
| `properties` → `NativeProperty` | containment, [*]      | Provider-native properties retained for the selected SAM event or resource form. |
