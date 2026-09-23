# CIM processes and policies

Source: `mde/metamodels/cim/cim-process-policy.emf`.

The process-policy module expresses progression through business work. A `BusinessProcess` owns typed steps and transitions. The step subclasses distinguish commands, queries, events, policies, human work, external interactions, decisions, waits, and the process boundary. `Policy` and `DecisionTable` express rules that can influence those paths. `ExceptionScenario` and `TemporalConstraint` keep failure and time behavior explicit.

The process model is still computation-independent. A `WaitStep` is a business wait, not yet a Step Functions state. A `CommandStep` is a business action, not yet a Lambda invocation. ETL performs those interpretations later and records trace links. All classes inherit from `kernel.TraceableElement`, except `ProcessStep` and `ProcessTransition`, whose direct supertypes are shown below. See the [shared kernel](../shared-kernel.md) for inherited features.

## `BusinessProcess`

`BusinessProcess` represents a named progression from a business trigger to a completion condition. Its steps describe what happens, transitions describe how the path moves, exceptions describe expected disruption, and temporal constraints describe acceptable timing. A process can be straight-through, human-involved, long-running, case-oriented, saga-like, or reporting-oriented.

### Declared attributes

| Attribute                    | Type and multiplicity                   | Meaning                                                                                                                                          | Example                                                                             |
| ---------------------------- | --------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------ | ----------------------------------------------------------------------------------- |
| `businessTriggerDescription` | `String` `0..1`                         | Describes the trigger in business language when a typed trigger reference alone would be too narrow or when the source is a recurring situation. | `A complete grant application enters the regional review queue.`                    |
| `longRunning`                | `Boolean` `0..1`                        | States whether the process can remain active over an extended period. Long-running and saga-like processes require temporal constraints.         | `true`                                                                              |
| `humanApprovalPossible`      | `Boolean` `0..1`                        | States whether a person may need to approve or decide during the process.                                                                        | `true`                                                                              |
| `compensationExpected`       | `Boolean` `0..1`                        | States whether the process needs business compensation when a later step fails after earlier work has completed.                                 | `true`                                                                              |
| `completionCriterion`        | `String` `0..1`                         | States the business condition that means the process is complete. EVL requires it.                                                               | `A decision is communicated and any approved amount is scheduled for disbursement.` |
| `processKind`                | `cimtypes.ProcessKind` `0..1`           | Classifies the process shape so refinement can distinguish workflow candidates and ordinary business progression.                                | `HUMAN_INVOLVED`                                                                    |
| `criticality`                | `cimtypes.CapabilityCriticality` `0..1` | Records the importance of the process to the business outcome.                                                                                   | `IMPORTANT`                                                                         |

### Relationships

| Feature               | Target and multiplicity            | Kind                                                   | Meaning                                                                                                                                                                                       |
| --------------------- | ---------------------------------- | ------------------------------------------------------ | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `steps`               | `ProcessStep` `1..*`               | containment                                            | Owns the ordered or transition-connected work units of the process.                                                                                                                           |
| `transitions`         | `ProcessTransition` `0..*`         | containment                                            | Owns the directed paths between the process's steps.                                                                                                                                          |
| `exceptions`          | `ExceptionScenario` `0..*`         | containment                                            | Owns recognized situations in which normal progression is interrupted.                                                                                                                        |
| `temporalConstraints` | `TemporalConstraint` `0..*`        | containment                                            | Owns deadlines, durations, and ordering rules for the process or its parts.                                                                                                                   |
| `owningCapability`    | `cimorg.BusinessCapability` `0..1` | reference; opposite `BusinessCapability.ownsProcesses` | Identifies the capability responsible for the process.                                                                                                                                        |
| `triggeringActor`     | `cimorg.Actor` `0..1`              | reference                                              | Identifies an actor that starts the process.                                                                                                                                                  |
| `triggeringEvent`     | `cimbehavior.BusinessEvent` `0..1` | reference                                              | Identifies the event that starts the process.                                                                                                                                                 |
| `triggeringCommand`   | `cimbehavior.Command` `0..1`       | reference                                              | Identifies the command that starts the process.                                                                                                                                               |
| `preconditions`       | `cimbehavior.Condition` `0..*`     | reference                                              | States what must be true before the process may start.                                                                                                                                        |
| `postconditions`      | `cimbehavior.Condition` `0..*`     | reference                                              | States what must be true when the process completes.                                                                                                                                          |
| `trigger`             | `kernel.ModelElement` `0..1`       | reference                                              | Provides a generic trigger slot for a model element. EVL limits a defined generic trigger to `BusinessEvent`, `Command`, or `Actor`. The typed trigger references are clearer when available. |

EVL requires exactly one START step, at least one END step, a trigger or trigger description, and a completion criterion. Transitions must remain within the process, and defined step order indexes must be unique. Compensation-enabled processes are warned when they contain no exception scenario. `BusinessProcess2Workflow` creates a PIM workflow when the process kind or long-running/human properties make workflow refinement appropriate.

## `ProcessStep`

`ProcessStep` is the abstract common part of every process step. It carries ordering, optionality, repetition, responsibility, and the controlled step kind. Concrete subclasses add the business object that the step invokes, observes, or performs.

Direct supertype: `kernel.TraceableElement`.

### Declared attributes

| Attribute        | Type and multiplicity      | Meaning                                                                                                                                                      | Example                   |
| ---------------- | -------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------ | ------------------------- |
| `orderIndex`     | `Integer` `0..1`           | Provides an explicit order number when the process needs a simple sequence view. EVL requires indexes to be unique within one process when they are present. | `3`                       |
| `optional`       | `Boolean` `0..1`           | States whether the step may be skipped under a valid business path.                                                                                          | `true`                    |
| `repeatable`     | `Boolean` `0..1`           | States whether the step may be performed more than once in the process.                                                                                      | `true`                    |
| `responsibility` | `String` `0..1`            | Names the business responsibility for completing the step when a role reference is not enough.                                                               | `Regional review officer` |
| `stepKind`       | `cimtypes.StepKind` `0..1` | Declares the semantic kind of the concrete step. Each subclass has an EVL rule requiring the corresponding literal.                                          | `COMMAND`                 |

### Relationships

| Feature            | Target and multiplicity | Kind      | Meaning                                                         |
| ------------------ | ----------------------- | --------- | --------------------------------------------------------------- |
| `responsibleRoles` | `cimorg.Role` `0..*`    | reference | Identifies roles that perform or own the step's responsibility. |

Non-boundary steps receive a warning when neither responsibility text nor responsible roles are provided. The subclass and `stepKind` must agree so transformations can select the correct PIM workflow construct.

## `StartStep`

`StartStep` marks the entry point of a `BusinessProcess`. It has no declared attributes or relationships because its meaning is provided by the inherited step identity and `stepKind=START`. A process must contain exactly one step with this kind. `StartStep2WorkflowStartStep` creates the corresponding PIM workflow start node.

## `EndStep`

`EndStep` marks a terminal point of a `BusinessProcess`. It has no declared attributes or relationships. A process must contain at least one step with `stepKind=END`; a transition may not leave an END step or enter a START step. `EndStep2WorkflowSuccessEndStep` maps it to a PIM success end node.

## `CommandStep`

`CommandStep` places a state-changing `Command` in a process. It allows the process to show where a business intention occurs without duplicating the command's issuer, input, outcome, and authorization semantics.

### Relationships

| Feature   | Target and multiplicity   | Kind      | Meaning                                        |
| --------- | ------------------------- | --------- | ---------------------------------------------- |
| `command` | `cimbehavior.Command` `1` | reference | Identifies the command performed at this step. |

EVL requires `stepKind=COMMAND`. The process transformation uses the linked command to reuse or create the PIM task function and its contract.

## `QueryStep`

`QueryStep` places a read request in a process. It is useful when a process needs information before choosing a path or completing a task.

### Relationships

| Feature | Target and multiplicity | Kind      | Meaning                                             |
| ------- | ----------------------- | --------- | --------------------------------------------------- |
| `query` | `cimbehavior.Query` `1` | reference | Identifies the business read performed by the step. |

EVL requires `stepKind=QUERY`. The linked query contributes its output, freshness, authorization, and access-pattern semantics to the generated PIM task.

## `EventStep`

`EventStep` places a business fact in a process path. It may represent observing an event before continuing or the point at which a process records a fact for other participants.

### Relationships

| Feature | Target and multiplicity         | Kind      | Meaning                                                |
| ------- | ------------------------------- | --------- | ------------------------------------------------------ |
| `event` | `cimbehavior.BusinessEvent` `1` | reference | Identifies the business fact associated with the step. |

EVL requires `stepKind=EVENT`. Event steps and event-triggered processes can become workflow waits, event channels, subscriptions, or flow endpoints during refinement.

## `PolicyStep`

`PolicyStep` applies a named `Policy` inside the process. It keeps rule evaluation visible in the process rather than hiding it inside a command or technical implementation.

### Relationships

| Feature  | Target and multiplicity | Kind      | Meaning                                                   |
| -------- | ----------------------- | --------- | --------------------------------------------------------- |
| `policy` | `Policy` `1`            | reference | Identifies the rule applied at this point in the process. |

EVL requires `stepKind=POLICY`. The policy itself determines whether the generated PIM structure is a handler function, architecture policy, or another policy family.

## `HumanTaskStep`

`HumanTaskStep` represents work that a person must perform or confirm. The task description explains the business activity and the completion evidence says how the model can tell that the person completed it.

### Declared attributes

| Attribute            | Type and multiplicity | Meaning                                                       | Example                                                             |
| -------------------- | --------------------- | ------------------------------------------------------------- | ------------------------------------------------------------------- |
| `taskDescription`    | `String` `0..1`       | Describes the work a person performs.                         | `Review submitted evidence and record an eligibility decision.`     |
| `completionEvidence` | `String` `0..1`       | States the record, decision, or event that proves completion. | `A dated approval or rejection decision linked to the application.` |

EVL requires `stepKind=HUMAN_TASK`, both attributes, and a responsible role or responsibility through the inherited rule. The process ETL creates input and output schemas for the human task and preserves its trace into the PIM workflow.

## `ExternalInteractionStep`

`ExternalInteractionStep` represents a business interaction with an external system inside a larger process. It makes the purpose and exchanged information explicit instead of treating the integration as an implementation detail.

### Declared attributes

| Attribute            | Type and multiplicity | Meaning                                                          | Example                                                         |
| -------------------- | --------------------- | ---------------------------------------------------------------- | --------------------------------------------------------------- |
| `interactionPurpose` | `String` `0..1`       | Explains the business reason for contacting the external system. | `Verify the applicant's identity before the approval decision.` |

### Relationships

| Feature                | Target and multiplicity            | Kind      | Meaning                                  |
| ---------------------- | ---------------------------------- | --------- | ---------------------------------------- |
| `externalSystem`       | `cimorg.ExternalSystem` `1`        | reference | Identifies the external participant.     |
| `exchangedInformation` | `cimdomain.InformationItem` `0..*` | reference | Lists information crossing the boundary. |

EVL requires the correct step kind, an external system, and an interaction purpose; it warns when no information is linked. Integration ETL creates an external integration flow and uses the external system's adapter information.

## `DecisionStep`

`DecisionStep` represents a branching point. The decision logic is either a reusable `Condition` or a `DecisionTable`, and the outgoing `ProcessTransition` objects represent the alternative paths.

### Relationships

| Feature         | Target and multiplicity        | Kind      | Meaning                                                                       |
| --------------- | ------------------------------ | --------- | ----------------------------------------------------------------------------- |
| `condition`     | `cimbehavior.Condition` `0..1` | reference | Provides a direct proposition used to choose a branch.                        |
| `decisionTable` | `DecisionTable` `0..1`         | reference | Provides multi-rule decision logic when a single condition is not sufficient. |

EVL requires `stepKind=DECISION`, one of the two logic references, and at least two outgoing transitions. `DecisionStep2WorkflowChoiceStep` creates a PIM choice step and may create a decision function from the linked logic.

## `WaitStep`

`WaitStep` represents a business pause. The reason states why the pause exists, while the duration expression describes how long or until what condition the process waits. It can be used for time-based and event-based progression without choosing a workflow engine.

### Declared attributes

| Attribute            | Type and multiplicity | Meaning                                            | Example                                                       |
| -------------------- | --------------------- | -------------------------------------------------- | ------------------------------------------------------------- |
| `waitReason`         | `String` `0..1`       | Explains the business reason for waiting.          | `Allow the applicant seven days to provide missing evidence.` |
| `durationExpression` | `String` `0..1`       | States the duration or temporal condition in text. | `7 business days after the missing-evidence notice`           |

### Relationships

| Feature                   | Target and multiplicity    | Kind        | Meaning                                                                      |
| ------------------------- | -------------------------- | ----------- | ---------------------------------------------------------------------------- |
| `durationExpressionModel` | `kernel.Expression` `0..1` | containment | Owns a typed expression when the duration needs a language, phase, and body. |

EVL requires `stepKind=WAIT` and either a duration expression or a wait reason. The ETL maps wait-like steps, including event steps used as waits, to PIM workflow wait structures.

## `ProcessTransition`

`ProcessTransition` is a directed path between two distinct process steps. It can name the path, give a condition, suggest a probability, and retain an order. The transition belongs to the `BusinessProcess` containment tree, while its source and target are references to steps contained by that same process.

Direct supertype: `kernel.SemanticRelationship`.

### Declared attributes

| Attribute             | Type and multiplicity | Meaning                                                                                             | Example                             |
| --------------------- | --------------------- | --------------------------------------------------------------------------------------------------- | ----------------------------------- |
| `label`               | `String` `0..1`       | Names the path for readers, especially an alternative from a decision.                              | `Eligible`                          |
| `conditionExpression` | `String` `0..1`       | States the path condition as text when a reusable `Condition` or structured expression is not used. | `eligibilityStatus = 'ELIGIBLE'`    |
| `orderIndex`          | `Integer` `0..1`      | Gives an explicit order to outgoing or listed paths where a simple ordering is useful.              | `1`                                 |
| `probabilityHint`     | `String` `0..1`       | Records an indicative business probability or frequency without claiming a runtime distribution.    | `Approximately 70% of applications` |

### Relationships

| Feature        | Target and multiplicity        | Kind        | Meaning                                            |
| -------------- | ------------------------------ | ----------- | -------------------------------------------------- |
| `condition`    | `kernel.Expression` `0..1`     | containment | Owns a structured expression controlling the path. |
| `source`       | `ProcessStep` `1`              | reference   | Identifies the step from which the path leaves.    |
| `target`       | `ProcessStep` `1`              | reference   | Identifies the step reached by the path.           |
| `conditionRef` | `cimbehavior.Condition` `0..1` | reference   | Reuses a business condition for the path.          |

EVL requires distinct source and target steps, forbids leaving END or entering START, and warns when a decision transition has no label or condition. `ProcessTransition2WorkflowTransition` carries the path and its condition into PIM.

## `Policy`

`Policy` expresses a rule that influences business behavior. It can react to events, guard commands, constrain queries, emit commands or events, or provide decision logic. The class keeps the natural-language rule and optional executable expression together because a business rule needs to remain reviewable even when later automation is possible.

### Declared attributes

| Attribute             | Type and multiplicity              | Meaning                                                                                                                       | Example                                                         |
| --------------------- | ---------------------------------- | ----------------------------------------------------------------------------------------------------------------------------- | --------------------------------------------------------------- |
| `naturalLanguageRule` | `String` `0..1`                    | States the rule in business language.                                                                                         | `An application with incomplete evidence must not be approved.` |
| `expression`          | `String` `0..1`                    | Holds a textual rule when the modeler uses an expression/language pair.                                                       | `application.evidenceComplete = false`                          |
| `auditRequired`       | `Boolean` `0..1`                   | States whether evaluation or enforcement of the policy must be auditable.                                                     | `true`                                                          |
| `policyType`          | `cimtypes.PolicyType` `0..1`       | Classifies whether the policy reacts, guards, derives, authorizes, validates, enforces compliance, escalates, or compensates. | `GUARD`                                                         |
| `expressionLanguage`  | `kernel.ExpressionLanguage` `0..1` | Identifies the language of `expression`.                                                                                      | `FEEL`                                                          |
| `enforcementStrength` | `kernel.ConstraintStrength` `0..1` | States how strongly the policy is applied. Mandatory and blocking policies need a violation severity.                         | `BLOCKING`                                                      |
| `violationSeverity`   | `kernel.Severity` `0..1`           | States the consequence of violating the policy when enforcement is mandatory or blocking.                                     | `ERROR`                                                         |

### Relationships

| Feature             | Target and multiplicity            | Kind and opposite                                      | Meaning                                                          |
| ------------------- | ---------------------------------- | ------------------------------------------------------ | ---------------------------------------------------------------- |
| `expressionModel`   | `kernel.Expression` `0..1`         | containment                                            | Owns the typed expression used by the policy.                    |
| `triggeredBy`       | `cimbehavior.BusinessEvent` `0..*` | reference; opposite `BusinessEvent.consumedByPolicies` | Identifies events that activate a reaction or derivation policy. |
| `guards`            | `cimbehavior.Command` `0..*`       | reference                                              | Identifies commands whose acceptance is guarded by the policy.   |
| `constrainsQueries` | `cimbehavior.Query` `0..*`         | reference                                              | Identifies queries whose results or access are constrained.      |
| `emitsCommands`     | `cimbehavior.Command` `0..*`       | reference                                              | Identifies commands produced as policy outcomes.                 |
| `emitsEvents`       | `cimbehavior.BusinessEvent` `0..*` | reference; opposite `BusinessEvent.causedByPolicies`   | Identifies facts produced by the policy.                         |
| `decisionTable`     | `DecisionTable` `0..1`             | reference                                              | Supplies multi-rule decision logic for the policy.               |

EVL requires a policy to be connected as an event reaction, command guard, or query constraint and to have a rule definition. Mandatory or blocking policies need severity; reaction policies are warned when they emit no command or event. `Policy2PolicyHandlerFunction` maps reaction and derivation behavior to PIM functions, while `Policy2ArchitecturePolicy` maps guard, authorization, compliance, and compensation behavior to PIM policies.

## `DecisionTable`

`DecisionTable` collects ordered or otherwise governed `DecisionRule` objects. It is useful when a business decision depends on several inputs and a single condition would hide the cases that reviewers need to inspect.

### Declared attributes

| Attribute        | Type and multiplicity | Meaning                                                                        | Example               |
| ---------------- | --------------------- | ------------------------------------------------------------------------------ | --------------------- |
| `hitPolicy`      | `String` `0..1`       | States how multiple matching rules are handled.                                | `FIRST`               |
| `defaultOutcome` | `String` `0..1`       | Gives the outcome when no rule matches. An incomplete table should provide it. | `Needs manual review` |
| `complete`       | `Boolean` `0..1`      | States whether the modeled rules cover every expected case.                    | `false`               |

### Relationships

| Feature   | Target and multiplicity            | Kind        | Meaning                                       |
| --------- | ---------------------------------- | ----------- | --------------------------------------------- |
| `rules`   | `DecisionRule` `1..*`              | containment | Owns the rule rows that make up the decision. |
| `inputs`  | `cimdomain.InformationItem` `0..*` | reference   | Lists information read by the decision.       |
| `outputs` | `cimdomain.InformationItem` `0..*` | reference   | Lists information produced by the decision.   |

EVL requires unique `priorityOrder` values among ordered rules and warns when an incomplete table has no default outcome. `DecisionTable2ChoiceLogic` creates executable PIM choice logic, while `DecisionTable2DecisionModel` preserves inputs, outputs, and rule expressions in a PIM decision model.

## `DecisionRule`

`DecisionRule` is one case in a decision table. It has a condition and outcome in readable text or structured expressions, and can connect that outcome to commands and events.

### Declared attributes

| Attribute       | Type and multiplicity | Meaning                                                                                     | Example                                              |
| --------------- | --------------------- | ------------------------------------------------------------------------------------------- | ---------------------------------------------------- |
| `priorityOrder` | `Integer` `0..1`      | Gives the rule's order when the table's hit policy uses priority or first-match evaluation. | `10`                                                 |
| `condition`     | `String` `0..1`       | States when the rule applies in business or expression language.                            | `income <= threshold and residency = eligibleRegion` |
| `outcome`       | `String` `0..1`       | States the business result when the rule applies.                                           | `Eligible for standard grant`                        |

### Relationships

| Feature               | Target and multiplicity            | Kind        | Meaning                                         |
| --------------------- | ---------------------------------- | ----------- | ----------------------------------------------- |
| `conditionExpression` | `kernel.Expression` `0..1`         | containment | Owns a typed condition expression for the rule. |
| `outcomeExpression`   | `kernel.Expression` `0..1`         | containment | Owns a typed expression for the rule's result.  |
| `resultingCommands`   | `cimbehavior.Command` `0..*`       | reference   | Lists commands caused by the rule.              |
| `resultingEvents`     | `cimbehavior.BusinessEvent` `0..*` | reference   | Lists facts caused by the rule.                 |

EVL requires both a condition and an outcome, using either the text or expression form. It warns when the rule has no resulting command, event, or meaningful outcome. The expressions are cloned into PIM decision logic by the process transformation.

## `ExceptionScenario`

`ExceptionScenario` describes a recognized deviation from the normal process. It states the business impact and whether recovery or compensation is expected, so later workflow refinement does not reduce a business failure to an unclassified technical exception.

### Declared attributes

| Attribute              | Type and multiplicity | Meaning                                                                                                 | Example                                                                          |
| ---------------------- | --------------------- | ------------------------------------------------------------------------------------------------------- | -------------------------------------------------------------------------------- |
| `scenario`             | `String` `0..1`       | Describes what exceptional situation occurs.                                                            | `The identity service returns an inconclusive match.`                            |
| `businessImpact`       | `String` `0..1`       | Explains what the exception means for the business process or participant.                              | `The application cannot proceed automatically and may miss the review deadline.` |
| `expected`             | `Boolean` `0..1`      | States whether the situation is expected as part of normal business operation.                          | `true`                                                                           |
| `recoverable`          | `Boolean` `0..1`      | States whether the process can recover from the situation.                                              | `true`                                                                           |
| `recoveryAction`       | `String` `0..1`       | Describes how recovery happens when it is recoverable, and how compensation is performed when required. | `Route the case to manual identity review.`                                      |
| `compensationRequired` | `Boolean` `0..1`      | States whether prior business effects require a compensating action.                                    | `false`                                                                          |

### Relationships

| Feature           | Target and multiplicity            | Kind      | Meaning                                                     |
| ----------------- | ---------------------------------- | --------- | ----------------------------------------------------------- |
| `errors`          | `cimbehavior.BusinessError` `0..*` | reference | Links the business errors that describe the exception.      |
| `resultingEvents` | `cimbehavior.BusinessEvent` `0..*` | reference | Links facts that record recovery, compensation, or failure. |

EVL requires a scenario, business impact, and recovery action for recoverable scenarios. A compensation-required scenario receives a warning when it has no recovery action or resulting event. `ExceptionScenario2ErrorHandler` creates a PIM error handler and attached compensation details.

## `TemporalConstraint`

`TemporalConstraint` records a time or ordering obligation over a process, step, event, command, policy, or other model element. It allows CIM to distinguish “must finish within two days” from “must happen after approval” without choosing a timer, queue, or workflow engine.

### Declared attributes

| Attribute            | Type and multiplicity    | Meaning                                                            | Example                                                          |
| -------------------- | ------------------------ | ------------------------------------------------------------------ | ---------------------------------------------------------------- |
| `deadlineExpression` | `String` `0..1`          | States the business deadline.                                      | `Complete identity review within 2 business days of submission.` |
| `durationExpression` | `String` `0..1`          | States an allowed duration or waiting period.                      | `The applicant has 7 calendar days to provide evidence.`         |
| `orderingExpression` | `String` `0..1`          | States a required order between events or activities.              | `Disbursement must follow approval.`                             |
| `violationSeverity`  | `kernel.Severity` `0..1` | States the consequence of violating the time or order expectation. | `WARNING`                                                        |

### Relationships

| Feature               | Target and multiplicity      | Kind        | Meaning                                                     |
| --------------------- | ---------------------------- | ----------- | ----------------------------------------------------------- |
| `deadline`            | `kernel.Expression` `0..1`   | containment | Owns a structured deadline expression.                      |
| `duration`            | `kernel.Expression` `0..1`   | containment | Owns a structured duration expression.                      |
| `ordering`            | `kernel.Expression` `0..1`   | containment | Owns a structured ordering expression.                      |
| `constrainedElements` | `kernel.ModelElement` `0..*` | reference   | Identifies the elements to which the temporal rule applies. |

EVL requires at least one deadline, duration, or ordering expression and warns when no constrained element is linked. `TemporalConstraint2TimeoutPolicy` creates PIM timeout, ordering, or schedule policies according to the expression and affected elements.
