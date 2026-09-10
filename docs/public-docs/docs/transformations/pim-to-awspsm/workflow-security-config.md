# PIM → AWS PSM: Workflow Security Config

Workflow, identity, security, and configuration binding turns PIM control flow and access intent into Step Functions, Cognito, IAM, Secrets Manager, CloudFormation parameters, and SSM parameters. The module builds an ASL graph in phases, resolves task targets after Lambda resources exist, separates secret from non-secret configuration, and keeps trust/permission decisions traceable.

Source module: `mde/transformations/pim-to-awspsm/workflow-security-config.etl`.

## Reading this page

A transformation rule is not a validation constraint: it decides whether and how a source element contributes to the target model. Read the guard as a routing decision, the target table as the model-level result, the behavior section as the important semantic side effects, and the trace/manual-decision information as the hand-off to review and later phases.

---

## Supporting ETL operations

These operations are not independent source-to-target rules, but they materially shape the result. They derive defaults, create secondary resources, cache correspondences, or resolve relationships after the main rule has run.

| Operation                          | Role                                                                                     | Source                                                               |
| ---------------------------------- | ---------------------------------------------------------------------------------------- | -------------------------------------------------------------------- |
| `resolveWorkflowTaskTargets`       | Resolves workflow task references after all PIM resources have been transformed.         | `mde/transformations/pim-to-awspsm/workflow-security-config.etl:41`  |
| `createAslDocument`                | Creates create asl document.                                                             | `mde/transformations/pim-to-awspsm/workflow-security-config.etl:255` |
| `createChoiceEvaluatorState`       | Creates the Lambda evaluator task that supplies input to a function-backed choice.       | `mde/transformations/pim-to-awspsm/workflow-security-config.etl:312` |
| `createAslState`                   | Creates create asl state.                                                                | `mde/transformations/pim-to-awspsm/workflow-security-config.etl:333` |
| `newAslStateForStep`               | Creates the concrete ASL state classifier corresponding to a PIM workflow step.          | `mde/transformations/pim-to-awspsm/workflow-security-config.etl:440` |
| `choiceRoutingIsComplete`          | Checks whether a choice has enough explicit routing to emit executable ASL.              | `mde/transformations/pim-to-awspsm/workflow-security-config.etl:466` |
| `aslJsonPathOrRoot`                | Returns a valid JSONPath root when an upstream mapping is absent or still a placeholder. | `mde/transformations/pim-to-awspsm/workflow-security-config.etl:475` |
| `defaultWorkflowResultPath`        | Returns a stable per-step path for an output mapping that is still TBD.                  | `mde/transformations/pim-to-awspsm/workflow-security-config.etl:483` |
| `hasExplicitWorkflowOutputMapping` | Checks whether a workflow output mapping is explicit enough to honor directly.           | `mde/transformations/pim-to-awspsm/workflow-security-config.etl:488` |
| `nextOrderedWorkflowStep`          | Computes complete asl state transitions.                                                 | `mde/transformations/pim-to-awspsm/workflow-security-config.etl:493` |
| `completeAslStateTransitions`      | Supporting ETL operation used by the module's transformation rules.                      | `mde/transformations/pim-to-awspsm/workflow-security-config.etl:507` |
| `createAslChoice`                  | Creates create asl choice.                                                               | `mde/transformations/pim-to-awspsm/workflow-security-config.etl:603` |
| `createAslRetry`                   | Creates create asl retry.                                                                | `mde/transformations/pim-to-awspsm/workflow-security-config.etl:640` |
| `createAslCatch`                   | Creates create asl catch.                                                                | `mde/transformations/pim-to-awspsm/workflow-security-config.etl:662` |
| `createStateMachineRole`           | Creates create state machine role.                                                       | `mde/transformations/pim-to-awspsm/workflow-security-config.etl:681` |
| `createStepFunctionLogging`        | Creates create step function logging.                                                    | `mde/transformations/pim-to-awspsm/workflow-security-config.etl:692` |
| `createStepFunctionTracing`        | Creates create step function tracing.                                                    | `mde/transformations/pim-to-awspsm/workflow-security-config.etl:703` |
| `aslSummaryJson`                   | Renders asl summary json.                                                                | `mde/transformations/pim-to-awspsm/workflow-security-config.etl:712` |
| `renderAsl`                        | Renders render asl.                                                                      | `mde/transformations/pim-to-awspsm/workflow-security-config.etl:717` |
| `renderAslState`                   | Renders render asl state.                                                                | `mde/transformations/pim-to-awspsm/workflow-security-config.etl:729` |
| `aslTypeName`                      | Renders asl type name.                                                                   | `mde/transformations/pim-to-awspsm/workflow-security-config.etl:787` |
| `retryJson`                        | Computes retry json.                                                                     | `mde/transformations/pim-to-awspsm/workflow-security-config.etl:813` |
| `catchJson`                        | Computes catch json.                                                                     | `mde/transformations/pim-to-awspsm/workflow-security-config.etl:826` |
| `choiceJson`                       | Computes choice json.                                                                    | `mde/transformations/pim-to-awspsm/workflow-security-config.etl:839` |
| `stringArrayJson`                  | Computes string array json.                                                              | `mde/transformations/pim-to-awspsm/workflow-security-config.etl:851` |
| `jsonataCondition`                 | Converts the supported provider-independent boolean subset into a JSONata condition.     | `mde/transformations/pim-to-awspsm/workflow-security-config.etl:863` |
| `jsonataDecisionOutcomeCondition`  | Builds the JSONata predicate for an explicit evaluator outcome mapping.                  | `mde/transformations/pim-to-awspsm/workflow-security-config.etl:895` |
| `waitSeconds`                      | Converts supported ISO-8601 day/hour/minute durations to ASL seconds.                    | `mde/transformations/pim-to-awspsm/workflow-security-config.etl:900` |
| `isSupportedWaitDuration`          | Returns whether a wait expression was parsed to an AWS-supported duration.               | `mde/transformations/pim-to-awspsm/workflow-security-config.etl:931` |
| `rotationDays`                     | Computes rotation days.                                                                  | `mde/transformations/pim-to-awspsm/workflow-security-config.etl:941` |
| `configurationJson`                | Computes configuration json.                                                             | `mde/transformations/pim-to-awspsm/workflow-security-config.etl:955` |

---

## `Workflow2StepFunctionStateMachine`

**Source:** `w` to `WORKFLOW!Workflow`  
**Target:** `sm` to `AWSPSMWORKFLOW!StepFunctionStateMachine`  
**Source location:** `mde/transformations/pim-to-awspsm/workflow-security-config.etl:2`

### Why this rule exists

A PIM workflow becomes a Step Functions state machine only when it has steps. The rule creates the ASL document, execution role, logging/tracing, aliases, and state placeholders; later operations complete targets, transitions, retries, catches, and JSON rendering once all PSM resources are available.

### When the rule runs

The rule is conditional. It runs only when this guard is true; a false guard means the source element belongs to another refinement path or requires a different provider mapping.

```etl
guard : w.steps.notEmpty()
```

### What it creates

- `sm` (`AWSPSMWORKFLOW!StepFunctionStateMachine`): Generated step function state machine (sm).

### Important behavior encoded in the rule

The rule directly assigns: `sm.id`, `sm.stateMachineName`, `sm.stateMachineType`, `sm.publishAlias`, `sm.aliasName`, `sm.role`, `sm.aslDocument`, `sm.logging`, `sm.tracing`.
Manual decisions raised by this rule: `WORKFLOW_HUMAN_APPROVAL_DESIGN`. These are intentional hand-off points, not transformation failures; resolve them in the model review/readiness workflow.

### How to troubleshoot or repair it

Start with the manual decision(s) `WORKFLOW_HUMAN_APPROVAL_DESIGN` and complete the requested provider or business choice. Then re-run the transformation and validate the resulting target. If the rule did not produce an object, inspect whether the workflow actually satisfies its guard and whether the required upstream correspondence exists.

### Authoritative source

Read the complete ETL rule at `mde/transformations/pim-to-awspsm/workflow-security-config.etl:2`. The documentation summarizes its purpose and observable effects; the ETL body remains the authority for exact assignments and helper calls.

---

## `IdentityProvider2CognitoUserPool`

**Source:** `idp` to `SECURITY!IdentityProvider`  
**Target:** `pool` to `AWSPSMIDENTITY!CognitoUserPool`  
**Source location:** `mde/transformations/pim-to-awspsm/workflow-security-config.etl:95`

### Why this rule exists

A PIM identity provider becomes a Cognito user pool with production safeguards and explicit OAuth-related configuration. The mapping keeps authentication policy concrete without claiming that every identity-provider feature has a direct Cognito equivalent.

### When the rule runs

There is no explicit guard, so every source instance of the declared type is eligible for this mapping. Eligibility does not guarantee that every optional relationship or downstream target can be resolved.

### What it creates

- `pool` (`AWSPSMIDENTITY!CognitoUserPool`): Generated cognito user pool (pool).

### Important behavior encoded in the rule

The rule directly assigns: `pool.id`, `pool.userPoolName`, `pool.mfaConfiguration`, `pool.mfaDecision`, `pool.mfaRationale`, `pool.policiesJson`, `pool.deletionProtection`.
Manual decisions raised by this rule: `COGNITO_FEDERATION_DETAILS_REQUIRED`. These are intentional hand-off points, not transformation failures; resolve them in the model review/readiness workflow.

### How to troubleshoot or repair it

Start with the manual decision(s) `COGNITO_FEDERATION_DETAILS_REQUIRED` and complete the requested provider or business choice. Then re-run the transformation and validate the resulting target. If the rule did not produce an object, inspect whether the identity provider actually satisfies its guard and whether the required upstream correspondence exists.

### Authoritative source

Read the complete ETL rule at `mde/transformations/pim-to-awspsm/workflow-security-config.etl:95`. The documentation summarizes its purpose and observable effects; the ETL body remains the authority for exact assignments and helper calls.

---

## `Principal2IamRole`

**Source:** `p` to `SECURITY!Principal`  
**Target:** `role` to `AWSPSMSECURITY!IamRole`  
**Source location:** `mde/transformations/pim-to-awspsm/workflow-security-config.etl:125`

### Why this rule exists

Service and role principals become IAM roles; human-only or unsupported principal kinds are not forced into roles by the guard. The rule carries trust and permission intent, leaving statement materialization to the relationship-resolution phase.

### When the rule runs

The rule is conditional. It runs only when this guard is true; a false guard means the source element belongs to another refinement path or requires a different provider mapping.

```etl
guard : p.principalKind = PIMTYPES!PrincipalKind#SERVICE or p.principalKind = PIMTYPES!PrincipalKind#ROLE
```

### What it creates

- `role` (`AWSPSMSECURITY!IamRole`): Generated iam role (role).
- Secondary objects created in the rule body: `AWSPSMSECURITY!IamInlinePolicy`.

### Important behavior encoded in the rule

The rule directly assigns: `role.id`, `role.roleName`, `role.path`, `role.maxSessionDuration`, `role.descriptionText`, `role.assumeRolePolicy`, `pol.id`, `pol.name`, `pol.policyName`, `pol.document`.

### How to troubleshoot or repair it

Verify the principal instance first: the guard shown above must evaluate to true for this rule to run. If it should run but does not, check the guarded links, enum values, and earlier transformation outputs; if it is intentionally out of scope, use the trace/readiness report to record that decision rather than adding a dummy target.

### Authoritative source

Read the complete ETL rule at `mde/transformations/pim-to-awspsm/workflow-security-config.etl:125`. The documentation summarizes its purpose and observable effects; the ETL body remains the authority for exact assignments and helper calls.

---

## `Secret2SecretsManagerSecret`

**Source:** `s` to `CONFIG!Secret`  
**Target:** `sec` to `AWSPSMSECURITY!SecretsManagerSecret`  
**Source location:** `mde/transformations/pim-to-awspsm/workflow-security-config.etl:161`

### Why this rule exists

A PIM secret becomes a Secrets Manager secret with a generated logical identity and secure storage semantics. The value itself is not casually copied into the model-to-model output; consumers receive a managed reference instead.

### When the rule runs

There is no explicit guard, so every source instance of the declared type is eligible for this mapping. Eligibility does not guarantee that every optional relationship or downstream target can be resolved.

### What it creates

- `sec` (`AWSPSMSECURITY!SecretsManagerSecret`): Generated secrets manager secret (sec).
- Secondary objects created in the rule body: `AWSPSMSECURITY!SecretRotationSchedule`.

### Important behavior encoded in the rule

The rule directly assigns: `sec.id`, `sec.secretName`, `sec.descriptionText`, `sec.generateSecretStringJson`, `sec.rotationRequired`, `rot.id`, `rot.rotationRulesJson`, `rot.secret`, `sec.rotationSchedule`.
Manual decisions raised by this rule: `EXTERNAL_SECRET_VALUE_REQUIRED`, `SECRET_ROTATION_LAMBDA_REQUIRED`. These are intentional hand-off points, not transformation failures; resolve them in the model review/readiness workflow.

### How to troubleshoot or repair it

Start with the manual decision(s) `EXTERNAL_SECRET_VALUE_REQUIRED, SECRET_ROTATION_LAMBDA_REQUIRED` and complete the requested provider or business choice. Then re-run the transformation and validate the resulting target. If the rule did not produce an object, inspect whether the secret actually satisfies its guard and whether the required upstream correspondence exists.

### Authoritative source

Read the complete ETL rule at `mde/transformations/pim-to-awspsm/workflow-security-config.etl:161`. The documentation summarizes its purpose and observable effects; the ETL body remains the authority for exact assignments and helper calls.

---

## `ConfigurationSet2CfnParameterCarrier`

**Source:** `c` to `CONFIG!ConfigurationSet`  
**Target:** `doc` to `KERNEL!StructuredDocument`  
**Source location:** `mde/transformations/pim-to-awspsm/workflow-security-config.etl:196`

### Why this rule exists

A configuration set becomes a structured parameter carrier so deployment-time values remain stage-specific and externally supplied. This avoids hard-coding environment configuration into generated resources.

### When the rule runs

There is no explicit guard, so every source instance of the declared type is eligible for this mapping. Eligibility does not guarantee that every optional relationship or downstream target can be resolved.

### What it creates

- `doc` (`KERNEL!StructuredDocument`): Generated structured document (doc).

### Important behavior encoded in the rule

The rule directly assigns: `doc.id`, `doc.name`, `doc.format`, `doc.content`.

### How to troubleshoot or repair it

Verify that the configuration set is present and semantically complete, then rerun the transformation. If the target is missing or incomplete, inspect the source attributes named in the rule body, the referenced helper operation, and the post-phase that resolves its relationships before changing the ETL itself.

### Authoritative source

Read the complete ETL rule at `mde/transformations/pim-to-awspsm/workflow-security-config.etl:196`. The documentation summarizes its purpose and observable effects; the ETL body remains the authority for exact assignments and helper calls.

---

## `ConfigParameter2CfnParameter`

**Source:** `p` to `CONFIG!ConfigParameter`  
**Target:** `c` to `AWSPSMCORE!CfnParameter`  
**Source location:** `mde/transformations/pim-to-awspsm/workflow-security-config.etl:209`

### Why this rule exists

Non-secret configuration becomes a CloudFormation parameter. The rule preserves type, default, requiredness, description, and stage intent, allowing deployment environments to supply values without confusing ordinary configuration with secrets.

### When the rule runs

The rule is conditional. It runs only when this guard is true; a false guard means the source element belongs to another refinement path or requires a different provider mapping.

```etl
guard : not isTrue(p.secret)
```

### What it creates

- `c` (`AWSPSMCORE!CfnParameter`): Generated cfn parameter (c).

### Important behavior encoded in the rule

The rule directly assigns: `c.id`, `c.name`, `c.parameterName`, `c.type`, `c.defaultValue`, `c.allowedPattern`, `c.parameterDescription`, `c.noEcho`.

### How to troubleshoot or repair it

Verify the config parameter instance first: the guard shown above must evaluate to true for this rule to run. If it should run but does not, check the guarded links, enum values, and earlier transformation outputs; if it is intentionally out of scope, use the trace/readiness report to record that decision rather than adding a dummy target.

### Authoritative source

Read the complete ETL rule at `mde/transformations/pim-to-awspsm/workflow-security-config.etl:209`. The documentation summarizes its purpose and observable effects; the ETL body remains the authority for exact assignments and helper calls.

---

## `SecretConfigParameter2SsmParameter`

**Source:** `p` to `CONFIG!ConfigParameter`  
**Target:** `s` to `AWSPSMSECURITY!SsmParameter`  
**Source location:** `mde/transformations/pim-to-awspsm/workflow-security-config.etl:230`

### Why this rule exists

Secret configuration parameters are bound to SecureString SSM parameters, separating secret storage from ordinary CloudFormation input. The guard is important: it prevents a sensitive PIM parameter from being emitted as a plain deployment parameter.

### When the rule runs

The rule is conditional. It runs only when this guard is true; a false guard means the source element belongs to another refinement path or requires a different provider mapping.

```etl
guard : isTrue(p.secret)
```

### What it creates

- `s` (`AWSPSMSECURITY!SsmParameter`): Generated ssm parameter (s).

### Important behavior encoded in the rule

The rule directly assigns: `s.id`, `s.parameterName`, `s.parameterType`, `s.tier`, `s.dataType`, `s.allowedPattern`, `s.descriptionText`, `s.value`.
Manual decisions raised by this rule: `SECRET_PARAMETER_VALUE_REQUIRED`. These are intentional hand-off points, not transformation failures; resolve them in the model review/readiness workflow.

### How to troubleshoot or repair it

Start with the manual decision(s) `SECRET_PARAMETER_VALUE_REQUIRED` and complete the requested provider or business choice. Then re-run the transformation and validate the resulting target. If the rule did not produce an object, inspect whether the config parameter actually satisfies its guard and whether the required upstream correspondence exists.

### Authoritative source

Read the complete ETL rule at `mde/transformations/pim-to-awspsm/workflow-security-config.etl:230`. The documentation summarizes its purpose and observable effects; the ETL body remains the authority for exact assignments and helper calls.

---
