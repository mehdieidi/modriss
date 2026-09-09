# CIM → PIM — Process Policy

Process and policy refinement preserves the difference between business intent and execution structure. Policies become handler functions or architecture policies, decision tables become choice logic, processes become workflows, and process steps/transitions become a typed PIM graph. Non-functional requirements are routed into the PIM policy that can enforce them, while unsupported ambiguity becomes a visible readiness or manual decision.

Source module: `mde/transformations/cim-to-pim/process-policy.etl`.

## Reading this page

A transformation rule is not a validation constraint: it decides whether and how a source element contributes to the target model. Read the guard as a routing decision, the target table as the model-level result, the behavior section as the important semantic side effects, and the trace/manual-decision information as the hand-off to review and later phases.

---

## Supporting ETL operations

These operations are not independent source-to-target rules, but they materially shape the result. They derive defaults, create secondary resources, cache correspondences, or resolve relationships after the main rule has run.

| Operation                        | Role                                                   | Source                                                  |
| -------------------------------- | ------------------------------------------------------ | ------------------------------------------------------- |
| `shouldGenerateWorkflow`         | Returns whether the receiver should generate workflow. | `mde/transformations/cim-to-pim/process-policy.etl:9`   |
| `workflowStepFor`                | Computes workflow step for.                            | `mde/transformations/cim-to-pim/process-policy.etl:18`  |
| `firstIntegerText`               | Computes first integer text.                           | `mde/transformations/cim-to-pim/process-policy.etl:35`  |
| `secondsFromDurationExpression`  | Computes seconds from duration expression.             | `mde/transformations/cim-to-pim/process-policy.etl:55`  |
| `cloneKernelExpression`          | Computes clone kernel expression.                      | `mde/transformations/cim-to-pim/process-policy.etl:72`  |
| `cloneConditionExpression`       | Computes clone condition expression.                   | `mde/transformations/cim-to-pim/process-policy.etl:88`  |
| `createPolicyContextSchema`      | Creates create policy context schema.                  | `mde/transformations/cim-to-pim/process-policy.etl:96`  |
| `createHumanTaskInputSchema`     | Creates create human task input schema.                | `mde/transformations/cim-to-pim/process-policy.etl:114` |
| `createHumanTaskOutputSchema`    | Creates create human task output schema.               | `mde/transformations/cim-to-pim/process-policy.etl:133` |
| `preferredTemporalRoutingTarget` | Computes preferred temporal routing target.            | `mde/transformations/cim-to-pim/process-policy.etl:833` |

---

## `Policy2PolicyHandlerFunction`

**Source:** `pol` — `CIMPROCESS!Policy`  
**Target:** `fn` — `COMPUTE!Function`  
**Source location:** `mde/transformations/cim-to-pim/process-policy.etl:152`

### Why this rule exists

Reactive, derivation, escalation, validation, and similar policies become handler functions because they represent behavior that must run. The guard avoids creating executable functions for purely architectural or governance policies, which are mapped by the companion rule.

### When the rule runs

The rule is conditional. It runs only when this guard is true; a false guard means the source element belongs to another refinement path or requires a different provider mapping.

```etl
guard : pol.policyType.asString() = "REACTION" or pol.policyType.asString() = "DERIVATION" or pol.policyType.asString() = "ESCALATION" or pol.policyType.asString() = "VALIDATION" or pol.triggeredBy.notEmpty() or pol.emitsCommands.notEmpty() or pol.emitsEvents.notEmpty()
```

### What it creates

- `fn` (`COMPUTE!Function`): Generated function (fn).
- Secondary objects created in the rule body: `CONTRACTS!FunctionContract`, `CONTRACTS!Schema`, `CONTRACTS!SchemaConstraint`.

### Important behavior encoded in the rule

The rule directly assigns: `fn.id`, `fn.name`, `fn.functionKind`, `fn.responsibility`, `fn.handlerResponsibility`, `fn.sourceNameSuggestion`, `fn.publicEntryPoint`, `fn.writesState`, `fn.readsState`, `fn.publishesEvents`, `fn.executionModel`, `fn.computeProfile`, `fn.stateless`, `contract.id`, `contract.name`, `contract.contractVersion` ….
Trace identifiers emitted here: `TR-100`. These identifiers are useful when following the generated element back to the originating CIM/PIM decision.

### How to troubleshoot or repair it

Verify the policy instance first: the guard shown above must evaluate to true for this rule to run. If it should run but does not, check the guarded links, enum values, and earlier transformation outputs; if it is intentionally out of scope, use the trace/readiness report to record that decision rather than adding a dummy target.

The trace record is the best first diagnostic: it tells you whether the target was created, which transformation rule claimed it, and what source element it came from.

### Authoritative source

Read the complete ETL rule at `mde/transformations/cim-to-pim/process-policy.etl:152`. The documentation summarizes its purpose and observable effects; the ETL body remains the authority for exact assignments and helper calls.

---

## `Policy2ArchitecturePolicy`

**Source:** `pol` — `CIMPROCESS!Policy`  
**Target:** `p` — `POLICY!CompliancePolicy`  
**Source location:** `mde/transformations/cim-to-pim/process-policy.etl:226`

### Why this rule exists

Authorization, guard, compliance, and compensation policies are retained as PIM policy objects rather than forced into a generic function. This preserves their architectural scope and lets workflow, API, security, and readiness rules consume the right kind of intent.

### When the rule runs

The rule is conditional. It runs only when this guard is true; a false guard means the source element belongs to another refinement path or requires a different provider mapping.

```etl
guard : pol.policyType.asString() = "COMPLIANCE" or pol.policyType.asString() = "GUARD" or pol.policyType.asString() = "AUTHORIZATION" or pol.policyType.asString() = "COMPENSATION"
```

### What it creates

- `p` (`POLICY!CompliancePolicy`): Generated compliance policy (p).

### Important behavior encoded in the rule

The rule directly assigns: `p.id`, `p.name`, `p.policyScope`, `p.productionRequired`.
Trace identifiers emitted here: `TR-100`. These identifiers are useful when following the generated element back to the originating CIM/PIM decision.

### How to troubleshoot or repair it

Verify the policy instance first: the guard shown above must evaluate to true for this rule to run. If it should run but does not, check the guarded links, enum values, and earlier transformation outputs; if it is intentionally out of scope, use the trace/readiness report to record that decision rather than adding a dummy target.

The trace record is the best first diagnostic: it tells you whether the target was created, which transformation rule claimed it, and what source element it came from.

### Authoritative source

Read the complete ETL rule at `mde/transformations/cim-to-pim/process-policy.etl:226`. The documentation summarizes its purpose and observable effects; the ETL body remains the authority for exact assignments and helper calls.

---

## `DecisionTable2ChoiceLogic`

**Source:** `dt` — `CIMPROCESS!DecisionTable`  
**Target:** `fn` — `COMPUTE!Function`  
**Source location:** `mde/transformations/cim-to-pim/process-policy.etl:242`

### Why this rule exists

A decision table with executable outcomes becomes a choice-logic function. The generated function carries ordered rules, conditions, outcomes, and default behavior so workflow and policy refinement can invoke a deterministic decision rather than interpret a table ad hoc.

### When the rule runs

There is no explicit guard, so every source instance of the declared type is eligible for this mapping. Eligibility does not guarantee that every optional relationship or downstream target can be resolved.

### What it creates

- `fn` (`COMPUTE!Function`): Generated function (fn).
- Secondary objects created in the rule body: `CONTRACTS!FunctionContract`, `CONTRACTS!Schema`.

### Important behavior encoded in the rule

The rule directly assigns: `fn.id`, `fn.name`, `fn.functionKind`, `fn.responsibility`, `fn.handlerResponsibility`, `fn.writesState`, `fn.readsState`, `fn.executionModel`, `fn.computeProfile`, `fn.stateless`, `contract.id`, `contract.name`, `contract.contractVersion`, `contract.validatesInput`, `contract.validatesOutput`, `contract.correlationIdField` ….
Trace identifiers emitted here: `TR-100`. These identifiers are useful when following the generated element back to the originating CIM/PIM decision.
Manual decisions raised by this rule: `TR-100`. These are intentional hand-off points, not transformation failures; resolve them in the model review/readiness workflow.

### How to troubleshoot or repair it

Start with the manual decision(s) `TR-100` and complete the requested provider or business choice. Then re-run the transformation and validate the resulting target. If the rule did not produce an object, inspect whether the decision table actually satisfies its guard and whether the required upstream correspondence exists.

The trace record is the best first diagnostic: it tells you whether the target was created, which transformation rule claimed it, and what source element it came from.

### Authoritative source

Read the complete ETL rule at `mde/transformations/cim-to-pim/process-policy.etl:242`. The documentation summarizes its purpose and observable effects; the ETL body remains the authority for exact assignments and helper calls.

---

## `DecisionTable2DecisionModel`

**Source:** `dt` — `CIMPROCESS!DecisionTable`  
**Target:** `dm` — `POLICY!DecisionModel`  
**Source location:** `mde/transformations/cim-to-pim/process-policy.etl:314`

### Why this rule exists

The decision table is also preserved as a PIM decision model for review and traceability. This companion output matters because executable choice logic alone would lose the table's business-readable structure and the evidence behind the generated branches.

### When the rule runs

There is no explicit guard, so every source instance of the declared type is eligible for this mapping. Eligibility does not guarantee that every optional relationship or downstream target can be resolved.

### What it creates

- `dm` (`POLICY!DecisionModel`): Generated decision model (dm).
- Secondary objects created in the rule body: `POLICY!DecisionRule`, `KERNEL!Expression`.

### Important behavior encoded in the rule

The rule directly assigns: `dm.id`, `dm.name`, `dm.hitPolicy`, `decisionRuleTarget.id`, `decisionRuleTarget.name`, `condition.id`, `condition.name`, `condition.language`, `condition.body`, `condition.phase`, `condition.sideEffectFree`, `decisionRuleTarget.condition`, `outcome.id`, `outcome.name`, `outcome.language`, `outcome.body` ….
Trace identifiers emitted here: `TR-100`. These identifiers are useful when following the generated element back to the originating CIM/PIM decision.
Manual decisions raised by this rule: `TR-100`. These are intentional hand-off points, not transformation failures; resolve them in the model review/readiness workflow.

### How to troubleshoot or repair it

Start with the manual decision(s) `TR-100` and complete the requested provider or business choice. Then re-run the transformation and validate the resulting target. If the rule did not produce an object, inspect whether the decision table actually satisfies its guard and whether the required upstream correspondence exists.

The trace record is the best first diagnostic: it tells you whether the target was created, which transformation rule claimed it, and what source element it came from.

### Authoritative source

Read the complete ETL rule at `mde/transformations/cim-to-pim/process-policy.etl:314`. The documentation summarizes its purpose and observable effects; the ETL body remains the authority for exact assignments and helper calls.

---

## `BusinessProcess2Workflow`

**Source:** `bp` — `CIMPROCESS!BusinessProcess`  
**Target:** `wf` — `WORKFLOW!Workflow`  
**Source location:** `mde/transformations/cim-to-pim/process-policy.etl:373`

### Why this rule exists

A business process becomes a PIM workflow only when it contains meaningful workflow signals such as steps, duration, human approval, or compensation. The rule transfers ownership, trigger/completion meaning, long-running posture, and operational policies into a graph that later becomes Step Functions or another orchestrator.

### When the rule runs

The rule is conditional. It runs only when this guard is true; a false guard means the source element belongs to another refinement path or requires a different provider mapping.

```etl
guard : shouldGenerateWorkflow(bp)
```

### What it creates

- `wf` (`WORKFLOW!Workflow`): Generated workflow (wf).

### Important behavior encoded in the rule

The rule directly assigns: `wf.id`, `wf.name`, `wf.workflowKind`, `wf.executionSemantics`, `wf.longRunning`, `wf.stateful`, `wf.compensationRequired`, `wf.humanApprovalRequired`, `wf.expectedMaxDurationSeconds`, `wf.expressLowLatencyRequired`, `wf.observability`, `wf.resilience`.
Trace identifiers emitted here: `TR-110`. These identifiers are useful when following the generated element back to the originating CIM/PIM decision.

### How to troubleshoot or repair it

Verify the business process instance first: the guard shown above must evaluate to true for this rule to run. If it should run but does not, check the guarded links, enum values, and earlier transformation outputs; if it is intentionally out of scope, use the trace/readiness report to record that decision rather than adding a dummy target.

The trace record is the best first diagnostic: it tells you whether the target was created, which transformation rule claimed it, and what source element it came from.

### Authoritative source

Read the complete ETL rule at `mde/transformations/cim-to-pim/process-policy.etl:373`. The documentation summarizes its purpose and observable effects; the ETL body remains the authority for exact assignments and helper calls.

---

## `StartStep2WorkflowStartStep`

**Source:** `s` — `CIMPROCESS!StartStep`  
**Target:** `ws` — `WORKFLOW!StartStep`  
**Source location:** `mde/transformations/cim-to-pim/process-policy.etl:400`

### Why this rule exists

A CIM start step becomes an explicit PIM workflow entry node. The greedy annotation helps it win the correct correspondence when several generic process-step rules could see the same subtype, and its source order/name remain available for later graph completion.

### When the rule runs

There is no explicit guard, so every source instance of the declared type is eligible for this mapping. Eligibility does not guarantee that every optional relationship or downstream target can be resolved.

### What it creates

- `ws` (`WORKFLOW!StartStep`): Generated start step (ws).

### Important behavior encoded in the rule

The rule directly assigns: `ws.id`, `ws.name`, `ws.orderIndex`, `ws.inputMapping`, `ws.outputMapping`, `ws.timeoutSeconds`.
Trace identifiers emitted here: `TR-120`. These identifiers are useful when following the generated element back to the originating CIM/PIM decision.
ETL annotation: `@greedy`. This affects rule selection or correspondence precedence and is part of the mapping behavior.

### How to troubleshoot or repair it

Verify that the start step is present and semantically complete, then rerun the transformation. If the target is missing or incomplete, inspect the source attributes named in the rule body, the referenced helper operation, and the post-phase that resolves its relationships before changing the ETL itself.

The trace record is the best first diagnostic: it tells you whether the target was created, which transformation rule claimed it, and what source element it came from.

### Authoritative source

Read the complete ETL rule at `mde/transformations/cim-to-pim/process-policy.etl:400`. The documentation summarizes its purpose and observable effects; the ETL body remains the authority for exact assignments and helper calls.

---

## `EndStep2WorkflowSuccessEndStep`

**Source:** `s` — `CIMPROCESS!EndStep`  
**Target:** `ws` — `WORKFLOW!SuccessEndStep`  
**Source location:** `mde/transformations/cim-to-pim/process-policy.etl:415`

### Why this rule exists

A business end step becomes a successful workflow terminator, preserving the difference between normal completion and error handling. Without this typed target, PIM could not distinguish a successful process outcome from a failure path when generating transitions.

### When the rule runs

There is no explicit guard, so every source instance of the declared type is eligible for this mapping. Eligibility does not guarantee that every optional relationship or downstream target can be resolved.

### What it creates

- `ws` (`WORKFLOW!SuccessEndStep`): Generated success end step (ws).

### Important behavior encoded in the rule

The rule directly assigns: `ws.id`, `ws.name`, `ws.orderIndex`, `ws.inputMapping`, `ws.outputMapping`, `ws.timeoutSeconds`.
Trace identifiers emitted here: `TR-120`. These identifiers are useful when following the generated element back to the originating CIM/PIM decision.
ETL annotation: `@greedy`. This affects rule selection or correspondence precedence and is part of the mapping behavior.

### How to troubleshoot or repair it

Verify that the end step is present and semantically complete, then rerun the transformation. If the target is missing or incomplete, inspect the source attributes named in the rule body, the referenced helper operation, and the post-phase that resolves its relationships before changing the ETL itself.

The trace record is the best first diagnostic: it tells you whether the target was created, which transformation rule claimed it, and what source element it came from.

### Authoritative source

Read the complete ETL rule at `mde/transformations/cim-to-pim/process-policy.etl:415`. The documentation summarizes its purpose and observable effects; the ETL body remains the authority for exact assignments and helper calls.

---

## `DecisionStep2WorkflowChoiceStep`

**Source:** `s` — `CIMPROCESS!DecisionStep`  
**Target:** `ws` — `WORKFLOW!ChoiceStep`  
**Source location:** `mde/transformations/cim-to-pim/process-policy.etl:430`

### Why this rule exists

A business decision becomes a PIM choice step, carrying its condition or decision-table function. The mapping keeps branching logic as a first-class node instead of flattening the decision into an opaque task that a provider generator cannot explain.

### When the rule runs

There is no explicit guard, so every source instance of the declared type is eligible for this mapping. Eligibility does not guarantee that every optional relationship or downstream target can be resolved.

### What it creates

- `ws` (`WORKFLOW!ChoiceStep`): Generated choice step (ws).

### Important behavior encoded in the rule

The rule directly assigns: `ws.id`, `ws.name`, `ws.orderIndex`, `ws.inputMapping`, `ws.outputMapping`, `ws.timeoutSeconds`, `ws.condition`, `ws.conditionExpression`, `ws.invokesFunction`.
Trace identifiers emitted here: `TR-120`. These identifiers are useful when following the generated element back to the originating CIM/PIM decision.
ETL annotation: `@greedy`. This affects rule selection or correspondence precedence and is part of the mapping behavior.

### How to troubleshoot or repair it

Verify that the decision step is present and semantically complete, then rerun the transformation. If the target is missing or incomplete, inspect the source attributes named in the rule body, the referenced helper operation, and the post-phase that resolves its relationships before changing the ETL itself.

The trace record is the best first diagnostic: it tells you whether the target was created, which transformation rule claimed it, and what source element it came from.

### Authoritative source

Read the complete ETL rule at `mde/transformations/cim-to-pim/process-policy.etl:430`. The documentation summarizes its purpose and observable effects; the ETL body remains the authority for exact assignments and helper calls.

---

## `WaitLikeStep2WorkflowWaitStep`

**Source:** `s` — `CIMPROCESS!ProcessStep`  
**Target:** `ws` — `WORKFLOW!WaitStep`  
**Source location:** `mde/transformations/cim-to-pim/process-policy.etl:456`

### Why this rule exists

Wait and event-wait behavior share a PIM wait representation because both suspend progress until time or an external condition is satisfied. The guard deliberately excludes executable steps, and the rule clones typed expressions where available so timing is not left as unstructured prose.

### When the rule runs

The rule is conditional. It runs only when this guard is true; a false guard means the source element belongs to another refinement path or requires a different provider mapping.

```etl
guard : s.isKindOf(CIMPROCESS!WaitStep) or s.isKindOf(CIMPROCESS!EventStep)
```

### What it creates

- `ws` (`WORKFLOW!WaitStep`): Generated wait step (ws).

### Important behavior encoded in the rule

The rule directly assigns: `ws.id`, `ws.name`, `ws.orderIndex`, `ws.inputMapping`, `ws.outputMapping`, `ws.timeoutSeconds`, `ws.waitReason`, `ws.condition`, `ws.conditionExpression`.
Trace identifiers emitted here: `TR-120`. These identifiers are useful when following the generated element back to the originating CIM/PIM decision.
ETL annotation: `@greedy`. This affects rule selection or correspondence precedence and is part of the mapping behavior.

### How to troubleshoot or repair it

Verify the process step instance first: the guard shown above must evaluate to true for this rule to run. If it should run but does not, check the guarded links, enum values, and earlier transformation outputs; if it is intentionally out of scope, use the trace/readiness report to record that decision rather than adding a dummy target.

The trace record is the best first diagnostic: it tells you whether the target was created, which transformation rule claimed it, and what source element it came from.

### Authoritative source

Read the complete ETL rule at `mde/transformations/cim-to-pim/process-policy.etl:456`. The documentation summarizes its purpose and observable effects; the ETL body remains the authority for exact assignments and helper calls.

---

## `ProcessStep2WorkflowTaskStep`

**Source:** `s` — `CIMPROCESS!ProcessStep`  
**Target:** `ws` — `WORKFLOW!TaskStep`  
**Source location:** `mde/transformations/cim-to-pim/process-policy.etl:480`

### Why this rule exists

Command, query, policy, human, and external-interaction steps become PIM tasks, with exactly one invocation responsibility selected from their subtype. This is where business process vocabulary becomes an executable action without losing the possibility of human or external work.

### When the rule runs

The rule is conditional. It runs only when this guard is true; a false guard means the source element belongs to another refinement path or requires a different provider mapping.

```etl
guard : s.isKindOf(CIMPROCESS!CommandStep) or s.isKindOf(CIMPROCESS!QueryStep) or s.isKindOf(CIMPROCESS!PolicyStep) or s.isKindOf(CIMPROCESS!HumanTaskStep) or s.isKindOf(CIMPROCESS!ExternalInteractionStep)
```

### What it creates

- `ws` (`WORKFLOW!TaskStep`): Generated task step (ws).
- Secondary objects created in the rule body: `COMPUTE!Function`, `CONTRACTS!FunctionContract`.

### Important behavior encoded in the rule

The rule directly assigns: `ws.id`, `ws.name`, `ws.orderIndex`, `ws.inputMapping`, `ws.outputMapping`, `ws.timeoutSeconds`, `ws.invokesFunction`, `ws.invokesAdapter`, `taskFn.id`, `taskFn.name`, `taskFn.functionKind`, `taskFn.responsibility`, `taskFn.handlerResponsibility`, `taskFn.sourceNameSuggestion`, `taskFn.publicEntryPoint`, `taskFn.writesState` ….
Trace identifiers emitted here: `TR-120`. These identifiers are useful when following the generated element back to the originating CIM/PIM decision.
Manual decisions raised by this rule: `TR-120`. These are intentional hand-off points, not transformation failures; resolve them in the model review/readiness workflow.
ETL annotation: `@greedy`. This affects rule selection or correspondence precedence and is part of the mapping behavior.

### How to troubleshoot or repair it

Start with the manual decision(s) `TR-120` and complete the requested provider or business choice. Then re-run the transformation and validate the resulting target. If the rule did not produce an object, inspect whether the process step actually satisfies its guard and whether the required upstream correspondence exists.

The trace record is the best first diagnostic: it tells you whether the target was created, which transformation rule claimed it, and what source element it came from.

### Authoritative source

Read the complete ETL rule at `mde/transformations/cim-to-pim/process-policy.etl:480`. The documentation summarizes its purpose and observable effects; the ETL body remains the authority for exact assignments and helper calls.

---

## `ProcessTransition2WorkflowTransition`

**Source:** `t` — `CIMPROCESS!ProcessTransition`  
**Target:** `wt` — `WORKFLOW!WorkflowTransition`  
**Source location:** `mde/transformations/cim-to-pim/process-policy.etl:546`

### Why this rule exists

A process transition becomes a typed PIM edge. The mapping carries source/target steps, condition labels, default behavior, and ordering so the later workflow transformer can construct a graph rather than infer edges from list position.

### When the rule runs

There is no explicit guard, so every source instance of the declared type is eligible for this mapping. Eligibility does not guarantee that every optional relationship or downstream target can be resolved.

### What it creates

- `wt` (`WORKFLOW!WorkflowTransition`): Generated workflow transition (wt).

### Important behavior encoded in the rule

The rule directly assigns: `wt.id`, `wt.name`, `wt.source`, `wt.target`, `wt.condition`, `wt.conditionExpression`, `wt.defaultTransition`.
Trace identifiers emitted here: `TR-130`. These identifiers are useful when following the generated element back to the originating CIM/PIM decision.

### How to troubleshoot or repair it

Verify that the process transition is present and semantically complete, then rerun the transformation. If the target is missing or incomplete, inspect the source attributes named in the rule body, the referenced helper operation, and the post-phase that resolves its relationships before changing the ETL itself.

The trace record is the best first diagnostic: it tells you whether the target was created, which transformation rule claimed it, and what source element it came from.

### Authoritative source

Read the complete ETL rule at `mde/transformations/cim-to-pim/process-policy.etl:546`. The documentation summarizes its purpose and observable effects; the ETL body remains the authority for exact assignments and helper calls.

---

## `ExceptionScenario2ErrorHandler`

**Source:** `ex` — `CIMPROCESS!ExceptionScenario`  
**Target:** `eh` — `WORKFLOW!ErrorHandler`  
**Source location:** `mde/transformations/cim-to-pim/process-policy.etl:658`

### Why this rule exists

An exception scenario becomes workflow error-handling metadata with selected errors, recovery target, compensation, and escalation intent. That makes failure part of the process design instead of an unmodeled branch discovered only during runtime testing.

### When the rule runs

There is no explicit guard, so every source instance of the declared type is eligible for this mapping. Eligibility does not guarantee that every optional relationship or downstream target can be resolved.

### What it creates

- `eh` (`WORKFLOW!ErrorHandler`): Generated error handler (eh).

### Important behavior encoded in the rule

The rule directly assigns: `eh.id`, `eh.name`, `eh.errorSelector`, `eh.recoveryAction`, `eh.continueWorkflow`.
Trace identifiers emitted here: `TR-130`. These identifiers are useful when following the generated element back to the originating CIM/PIM decision.
Manual decisions raised by this rule: `TR-130`. These are intentional hand-off points, not transformation failures; resolve them in the model review/readiness workflow.

### How to troubleshoot or repair it

Start with the manual decision(s) `TR-130` and complete the requested provider or business choice. Then re-run the transformation and validate the resulting target. If the rule did not produce an object, inspect whether the exception scenario actually satisfies its guard and whether the required upstream correspondence exists.

The trace record is the best first diagnostic: it tells you whether the target was created, which transformation rule claimed it, and what source element it came from.

### Authoritative source

Read the complete ETL rule at `mde/transformations/cim-to-pim/process-policy.etl:658`. The documentation summarizes its purpose and observable effects; the ETL body remains the authority for exact assignments and helper calls.

---

## `SecurityConstraint2SecurityPolicies`

**Source:** `sc` — `CIMGOV!SecurityConstraint`  
**Target:** `sec` — `SECURITY!SecurityPolicy`, `auth` — `SECURITY!AuthPolicy`, `authz` — `SECURITY!AuthorizationPolicy`  
**Source location:** `mde/transformations/cim-to-pim/process-policy.etl:675`

### Why this rule exists

A CIM security constraint is expanded into the PIM policies that can actually guard behavior or information. The transformation preserves rule/threat rationale and affected scope, giving functions, routes, stores, and workflows a security object they can reference.

### When the rule runs

There is no explicit guard, so every source instance of the declared type is eligible for this mapping. Eligibility does not guarantee that every optional relationship or downstream target can be resolved.

### What it creates

- `sec` (`SECURITY!SecurityPolicy`): Generated security policy (sec).
- `auth` (`SECURITY!AuthPolicy`): Generated auth policy (auth).
- `authz` (`SECURITY!AuthorizationPolicy`): Generated authorization policy (authz).

### Important behavior encoded in the rule

The rule directly assigns: `sec.id`, `sec.name`, `sec.policyScope`, `sec.authenticationRequired`, `sec.authorizationRequired`, `sec.authStrength`, `sec.auditRequired`, `sec.encryptionInTransitRequired`, `sec.encryptionAtRestRequired`, `sec.secretsRequired`, `sec.threatModelNotes`, `auth.id`, `auth.name`, `auth.policyScope`, `auth.authenticationRequired`, `auth.authScheme` ….
Trace identifiers emitted here: `TR-060`. These identifiers are useful when following the generated element back to the originating CIM/PIM decision.
Manual decisions raised by this rule: `TR-060`. These are intentional hand-off points, not transformation failures; resolve them in the model review/readiness workflow.

### How to troubleshoot or repair it

Start with the manual decision(s) `TR-060` and complete the requested provider or business choice. Then re-run the transformation and validate the resulting target. If the rule did not produce an object, inspect whether the security constraint actually satisfies its guard and whether the required upstream correspondence exists.

The trace record is the best first diagnostic: it tells you whether the target was created, which transformation rule claimed it, and what source element it came from.

### Authoritative source

Read the complete ETL rule at `mde/transformations/cim-to-pim/process-policy.etl:675`. The documentation summarizes its purpose and observable effects; the ETL body remains the authority for exact assignments and helper calls.

---

## `TemporalConstraint2TimeoutPolicy`

**Source:** `tc` — `CIMPROCESS!TemporalConstraint`  
**Target:** `timeout` — `POLICY!TimeoutPolicy`, `ordering` — `POLICY!OrderingPolicy`  
**Source location:** `mde/transformations/cim-to-pim/process-policy.etl:754`

### Why this rule exists

A temporal constraint becomes a timeout policy with expression and scope. The business clock is preserved so later functions, workflows, external adapters, and escalation paths can be bounded consistently rather than each inventing a timeout.

### When the rule runs

There is no explicit guard, so every source instance of the declared type is eligible for this mapping. Eligibility does not guarantee that every optional relationship or downstream target can be resolved.

### What it creates

- `timeout` (`POLICY!TimeoutPolicy`): Generated timeout policy (timeout).
- `ordering` (`POLICY!OrderingPolicy`): Generated ordering policy (ordering).
- Secondary objects created in the rule body: `INTEGRATION!Schedule`.

### Important behavior encoded in the rule

The rule directly assigns: `timeout.id`, `timeout.name`, `timeout.policyScope`, `timeout.productionRequired`, `timeout.timeoutSeconds`, `timeout.clientTimeoutSeconds`, `ordering.id`, `ordering.name`, `ordering.policyScope`, `ordering.productionRequired`, `ordering.orderingRequirement`, `ordering.orderingKey`, `ordering.strictOrderingRequired`, `target.expectedMaxDurationSeconds`, `target.resilience`, `target.timeoutSeconds` ….
Trace identifiers emitted here: `TR-130`. These identifiers are useful when following the generated element back to the originating CIM/PIM decision.
Manual decisions raised by this rule: `TR-130`. These are intentional hand-off points, not transformation failures; resolve them in the model review/readiness workflow.

### How to troubleshoot or repair it

Start with the manual decision(s) `TR-130` and complete the requested provider or business choice. Then re-run the transformation and validate the resulting target. If the rule did not produce an object, inspect whether the temporal constraint actually satisfies its guard and whether the required upstream correspondence exists.

The trace record is the best first diagnostic: it tells you whether the target was created, which transformation rule claimed it, and what source element it came from.

### Authoritative source

Read the complete ETL rule at `mde/transformations/cim-to-pim/process-policy.etl:754`. The documentation summarizes its purpose and observable effects; the ETL body remains the authority for exact assignments and helper calls.

---

## `AvailabilityReliabilityNfr2ResiliencePolicy`

**Source:** `nfr` — `CIMGOV!NonFunctionalRequirement`  
**Target:** `pol` — `POLICY!ResiliencePolicy`, `backup` — `POLICY!BackupPolicy`  
**Source location:** `mde/transformations/cim-to-pim/process-policy.etl:855`

### Why this rule exists

Availability and reliability requirements become resilience policies. The rule translates quality intent into retry, timeout, circuit, fallback, or recovery expectations and links them to the PIM elements whose failure behavior must meet the requirement.

### When the rule runs

The rule is conditional. It runs only when this guard is true; a false guard means the source element belongs to another refinement path or requires a different provider mapping.

```etl
guard : nfr.qualityType.asString() = "AVAILABILITY" or nfr.qualityType.asString() = "RELIABILITY"
```

### What it creates

- `pol` (`POLICY!ResiliencePolicy`): Generated resilience policy (pol).
- `backup` (`POLICY!BackupPolicy`): Generated backup policy (backup).
- Secondary objects created in the rule body: `POLICY!RetryPolicy`.

### Important behavior encoded in the rule

The rule directly assigns: `pol.id`, `pol.name`, `pol.policyScope`, `pol.productionRequired`, `pol.retryEnabled`, `pol.deadLetterRequired`, `pol.fallbackRequired`, `pol.circuitBreakerRequired`, `pol.timeoutRequired`, `retry.id`, `retry.name`, `retry.maxAttempts`, `retry.initialDelaySeconds`, `retry.backoffRate`, `retry.maxDelaySeconds`, `pol.retry` ….
Trace identifiers emitted here: `TR-060`. These identifiers are useful when following the generated element back to the originating CIM/PIM decision.

### How to troubleshoot or repair it

Verify the non functional requirement instance first: the guard shown above must evaluate to true for this rule to run. If it should run but does not, check the guarded links, enum values, and earlier transformation outputs; if it is intentionally out of scope, use the trace/readiness report to record that decision rather than adding a dummy target.

The trace record is the best first diagnostic: it tells you whether the target was created, which transformation rule claimed it, and what source element it came from.

### Authoritative source

Read the complete ETL rule at `mde/transformations/cim-to-pim/process-policy.etl:855`. The documentation summarizes its purpose and observable effects; the ETL body remains the authority for exact assignments and helper calls.

---

## `PerformanceNfr2TimeoutPolicy`

**Source:** `nfr` — `CIMGOV!NonFunctionalRequirement`  
**Target:** `pol` — `POLICY!TimeoutPolicy`, `concurrency` — `POLICY!ConcurrencyPolicy`, `cache` — `POLICY!CachePolicy`, `rateLimit` — `POLICY!RateLimitPolicy`  
**Source location:** `mde/transformations/cim-to-pim/process-policy.etl:897`

### Why this rule exists

Performance, latency, and scalability requirements first become timeout-oriented policy because an unbounded operation cannot be reasoned about operationally. The output keeps the NFR's scenario and target attached for later tuning rather than inventing exact AWS limits.

### When the rule runs

The rule is conditional. It runs only when this guard is true; a false guard means the source element belongs to another refinement path or requires a different provider mapping.

```etl
guard : nfr.qualityType.asString() = "PERFORMANCE" or nfr.qualityType.asString() = "LATENCY" or nfr.qualityType.asString() = "SCALABILITY"
```

### What it creates

- `pol` (`POLICY!TimeoutPolicy`): Generated timeout policy (pol).
- `concurrency` (`POLICY!ConcurrencyPolicy`): Generated concurrency policy (concurrency).
- `cache` (`POLICY!CachePolicy`): Generated cache policy (cache).
- `rateLimit` (`POLICY!RateLimitPolicy`): Generated rate limit policy (rateLimit).

### Important behavior encoded in the rule

The rule directly assigns: `pol.id`, `pol.name`, `pol.policyScope`, `pol.productionRequired`, `pol.timeoutSeconds`, `pol.clientTimeoutSeconds`, `concurrency.id`, `concurrency.name`, `concurrency.policyScope`, `concurrency.productionRequired`, `concurrency.maxConcurrency`, `concurrency.reservedConcurrencyHint`, `concurrency.burstAssumption`, `concurrency.perSourceLimitRequired`, `concurrency.scalingRationale`, `cache.id` ….
Trace identifiers emitted here: `TR-060`. These identifiers are useful when following the generated element back to the originating CIM/PIM decision.
Manual decisions raised by this rule: `TR-060`. These are intentional hand-off points, not transformation failures; resolve them in the model review/readiness workflow.

### How to troubleshoot or repair it

Start with the manual decision(s) `TR-060` and complete the requested provider or business choice. Then re-run the transformation and validate the resulting target. If the rule did not produce an object, inspect whether the non functional requirement actually satisfies its guard and whether the required upstream correspondence exists.

The trace record is the best first diagnostic: it tells you whether the target was created, which transformation rule claimed it, and what source element it came from.

### Authoritative source

Read the complete ETL rule at `mde/transformations/cim-to-pim/process-policy.etl:897`. The documentation summarizes its purpose and observable effects; the ETL body remains the authority for exact assignments and helper calls.

---

## `AuditOperabilityNfr2Observability`

**Source:** `nfr` — `CIMGOV!NonFunctionalRequirement`  
**Target:** `pol` — `POLICY!ObservabilityConfig`  
**Source location:** `mde/transformations/cim-to-pim/process-policy.etl:957`

### Why this rule exists

Auditability, operability, and maintainability requirements become observability policies. The rule ensures the need for logs, metrics, traces, retention, or audit evidence travels with the architecture and is not treated as a post-deployment documentation task.

### When the rule runs

The rule is conditional. It runs only when this guard is true; a false guard means the source element belongs to another refinement path or requires a different provider mapping.

```etl
guard : nfr.qualityType.asString() = "AUDITABILITY" or nfr.qualityType.asString() = "OPERABILITY" or nfr.qualityType.asString() = "MAINTAINABILITY"
```

### What it creates

- `pol` (`POLICY!ObservabilityConfig`): Generated observability config (pol).
- Secondary objects created in the rule body: `POLICY!LoggingPolicy`, `POLICY!TracingPolicy`, `POLICY!MetricPolicy`, `POLICY!Slo`.

### Important behavior encoded in the rule

The rule directly assigns: `pol.id`, `pol.name`, `pol.policyScope`, `pol.productionRequired`, `pol.loggingEnabled`, `pol.metricsEnabled`, `pol.tracingEnabled`, `pol.alarmsEnabled`, `pol.dashboardRequired`, `pol.correlationIdRequired`, `pol.correlationIdField`, `logging.id`, `logging.name`, `logging.logFormat`, `logging.logLevel`, `logging.structuredLogging` ….
Trace identifiers emitted here: `TR-060`. These identifiers are useful when following the generated element back to the originating CIM/PIM decision.

### How to troubleshoot or repair it

Verify the non functional requirement instance first: the guard shown above must evaluate to true for this rule to run. If it should run but does not, check the guarded links, enum values, and earlier transformation outputs; if it is intentionally out of scope, use the trace/readiness report to record that decision rather than adding a dummy target.

The trace record is the best first diagnostic: it tells you whether the target was created, which transformation rule claimed it, and what source element it came from.

### Authoritative source

Read the complete ETL rule at `mde/transformations/cim-to-pim/process-policy.etl:957`. The documentation summarizes its purpose and observable effects; the ETL body remains the authority for exact assignments and helper calls.

---

## `CostNfr2CostPolicy`

**Source:** `nfr` — `CIMGOV!NonFunctionalRequirement`  
**Target:** `pol` — `POLICY!CostPolicy`  
**Source location:** `mde/transformations/cim-to-pim/process-policy.etl:1022`

### Why this rule exists

Cost intent becomes a PIM cost policy so estimates, budgets, and optimization choices remain attached to the design. The transformation records the requirement without pretending that provider pricing can be resolved at CIM time.

### When the rule runs

The rule is conditional. It runs only when this guard is true; a false guard means the source element belongs to another refinement path or requires a different provider mapping.

```etl
guard : nfr.qualityType.asString() = "COST"
```

### What it creates

- `pol` (`POLICY!CostPolicy`): Generated cost policy (pol).

### Important behavior encoded in the rule

The rule directly assigns: `pol.id`, `pol.name`, `pol.policyScope`, `pol.productionRequired`, `pol.budget`, `pol.costDriver`, `pol.optimizationIntent`, `pol.alarmsRequired`.
Trace identifiers emitted here: `TR-060`. These identifiers are useful when following the generated element back to the originating CIM/PIM decision.

### How to troubleshoot or repair it

Verify the non functional requirement instance first: the guard shown above must evaluate to true for this rule to run. If it should run but does not, check the guarded links, enum values, and earlier transformation outputs; if it is intentionally out of scope, use the trace/readiness report to record that decision rather than adding a dummy target.

The trace record is the best first diagnostic: it tells you whether the target was created, which transformation rule claimed it, and what source element it came from.

### Authoritative source

Read the complete ETL rule at `mde/transformations/cim-to-pim/process-policy.etl:1022`. The documentation summarizes its purpose and observable effects; the ETL body remains the authority for exact assignments and helper calls.

---

## `DataQualityNfr2SchemaConstraintAndReadiness`

**Source:** `nfr` — `CIMGOV!NonFunctionalRequirement`  
**Target:** `pol` — `POLICY!DataQualityPolicy`  
**Source location:** `mde/transformations/cim-to-pim/process-policy.etl:1042`

### Why this rule exists

Data-quality requirements have two destinations: a schema constraint where the data shape can enforce them, and a readiness check where evidence or operational verification is needed. Splitting the intent this way prevents quality from becoming either an unenforceable note or an overly broad runtime rule.

### When the rule runs

The rule is conditional. It runs only when this guard is true; a false guard means the source element belongs to another refinement path or requires a different provider mapping.

```etl
guard : nfr.qualityType.asString() = "DATA_QUALITY"
```

### What it creates

- `pol` (`POLICY!DataQualityPolicy`): Generated data quality policy (pol).

### Important behavior encoded in the rule

The rule directly assigns: `pol.id`, `pol.name`, `pol.policyScope`, `pol.productionRequired`, `pol.validationRequired`, `pol.completenessCheckRequired`, `pol.freshnessCheckRequired`, `pol.duplicateDetectionRequired`, `pol.qualityDimensions`, `pol.measurementRule`.
Trace identifiers emitted here: `TR-060`. These identifiers are useful when following the generated element back to the originating CIM/PIM decision.

### How to troubleshoot or repair it

Verify the non functional requirement instance first: the guard shown above must evaluate to true for this rule to run. If it should run but does not, check the guarded links, enum values, and earlier transformation outputs; if it is intentionally out of scope, use the trace/readiness report to record that decision rather than adding a dummy target.

The trace record is the best first diagnostic: it tells you whether the target was created, which transformation rule claimed it, and what source element it came from.

### Authoritative source

Read the complete ETL rule at `mde/transformations/cim-to-pim/process-policy.etl:1042`. The documentation summarizes its purpose and observable effects; the ETL body remains the authority for exact assignments and helper calls.

---

## `GenericComplianceNfr2CompliancePolicy`

**Source:** `nfr` — `CIMGOV!NonFunctionalRequirement`  
**Target:** `pol` — `POLICY!CompliancePolicy`  
**Source location:** `mde/transformations/cim-to-pim/process-policy.etl:1066`

### Why this rule exists

A generic compliance NFR becomes a PIM compliance policy unless it already has a specialized compliance subtype. The guard prevents duplicate policy evidence while ensuring an untyped compliance requirement still reaches governance review.

### When the rule runs

The rule is conditional. It runs only when this guard is true; a false guard means the source element belongs to another refinement path or requires a different provider mapping.

```etl
guard : nfr.qualityType.asString() = "COMPLIANCE" and not nfr.isKindOf(CIMGOV!ComplianceConstraint)
```

### What it creates

- `pol` (`POLICY!CompliancePolicy`): Generated compliance policy (pol).

### Important behavior encoded in the rule

The rule directly assigns: `pol.id`, `pol.name`, `pol.policyScope`, `pol.productionRequired`, `pol.regulation`, `pol.controlId`, `pol.evidenceType`, `pol.auditReportRequired`.
Trace identifiers emitted here: `TR-060`. These identifiers are useful when following the generated element back to the originating CIM/PIM decision.

### How to troubleshoot or repair it

Verify the non functional requirement instance first: the guard shown above must evaluate to true for this rule to run. If it should run but does not, check the guarded links, enum values, and earlier transformation outputs; if it is intentionally out of scope, use the trace/readiness report to record that decision rather than adding a dummy target.

The trace record is the best first diagnostic: it tells you whether the target was created, which transformation rule claimed it, and what source element it came from.

### Authoritative source

Read the complete ETL rule at `mde/transformations/cim-to-pim/process-policy.etl:1066`. The documentation summarizes its purpose and observable effects; the ETL body remains the authority for exact assignments and helper calls.

---
