# PIM Semantic Validation Test Coverage

This document traces the PIM EVL semantic validation coverage added in
`packages/java/mde-evl-validator/src/test/java/io/mehdieidi/varka/mde/validation/PimSemanticValidationTest.java`.

## Scope

- Level covered: PIM only.
- Entry EVL: `mde/validation/pim/pim-semantic-validation.evl`.
- Metamodel: `mde/metamodels/pim/pim-combined.ecore`.
- Positive model: `mde/samples/pim.xmi`, asserted to produce zero mandatory and zero optional EVL violations.
- Negative models: generated in JUnit from the positive sample plus minimal focused XMI fixtures.
- Validation boundary: tests use the standalone Epsilon EVL validator and EMF structural loading. They do not use assistant apply/repair/commit paths, `ModelService.validate(...)`, stored validation endpoints, `validateGeneratedXmi(...)`, EVL CLIs, or EVL profiles from chatbot assistant paths.

## Coverage Strategy

The suite validates mandatory EVL `constraint` rules as `MANDATORY` findings and optional EVL
`critique` rules as `OPTIONAL` findings. It covers 178 executable PIM rules out of 184 EVL rule
declarations. The remaining 6 rules are retained in EVL but documented below because the current
metamodel/runtime shape prevents a meaningful failing instance.

| Scenario                                                    | EVL area covered                                                                                                                                      |
| ----------------------------------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------- |
| `repositoryPimSamplePassesAllMandatoryAndOptionalSemantics` | Repository PIM sample is the good model and must pass all mandatory and optional PIM semantics.                                                       |
| `root-core`                                                 | PIM root domain/profile/provider-neutrality, base kernel ids, portable names, generated traceability, manual rationale.                               |
| `deployment`                                                | Services, ownership, exposure/data ownership, deployment units, environments, implementation profile, membership consistency.                         |
| `compute-contracts-api`                                     | Function reachability/responsibility/idempotency/state/event declarations, triggers, function contracts, API/auth/routes/validation/error mappings.   |
| `schemas-events-channels`                                   | Schemas/fields/schema constraints, event types/envelopes, channels, queues, topics, event buses, routing rules, subscriptions, schedules, flows.      |
| `data-workflow-security-policies-readiness`                 | Storage/data models/access, workflows/steps/transitions/handlers, adapters/endpoints/credentials, config/secrets/security, policies, readiness.       |
| `orphan-semantics`                                          | Structural-backstop EVL rules that require structural validation disabled to load bad XMI, plus logging/metrics/alerts/SLO/CORS/envelope/trace rules. |
| `pimEolHelpersHaveExplicitBehaviorCoverage`                 | PIM EOL helper operations used by EVL guards/checks, executed through a helper-only EVL module.                                                       |

## EVL Fixes

`ExternalSchemaShouldStateCompatibility` in `mde/validation/pim/rules/contracts.evl` was fixed to
use EMF explicit-set state:

```eol
self.eIsSet(self.eClass().getEStructuralFeature("compatibility"))
```

The previous check used `self.compatibility.isDefined()`. Because optional EMF enum attributes load
with a default literal, an external schema with no explicit compatibility value could incorrectly
pass the critique.

## EOL Helper Coverage

The helper-only test imports `mde/validation/pim/lib/pim-validation-helpers.eol` and asserts helper
behavior against a compact, valid PIM fixture. All 45 helper operations are directly called by the
helper-contract EVL used in `pimEolHelpersHaveExplicitBehaviorCoverage`. Covered helper behavior
includes:

| Helper behavior                     | Covered by assertion                                                                                                                                                              |
| ----------------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Text/boolean/enum helpers           | `hasText`, `isTrue`, `enumIs`, `enumIn`, `notEmpty`.                                                                                                                              |
| Provider-neutral checks             | `containsProviderToken` detects provider terms and ignores generic terms.                                                                                                         |
| Service ownership helpers           | `ownedElements`, `serviceMemberships`, `serviceMembershipsByServiceId`, `hasMembershipFor`, `displayName`, `allServiceApis`, `allServiceChannels`, `allServiceWorkflows`.         |
| Reachability and function semantics | `reachableFunctionIds`, `hasAnyIncomingBinding`, `changesStateOrEmitsEvents`, `needsIdempotencyPolicy`, `idempotentConsumerFunctionIds`.                                          |
| Schema and data sensitivity         | `hasFieldNamed`, `hasClassifiedSensitiveFields`, event/channel/storage personal-data helpers.                                                                                     |
| Workflow helpers                    | `actionCount`, `startSteps`, `endSteps`, `incomingTransitions`, `outgoingTransitions`, `workflowTransitionsBySourceId`, `workflowTransitionsByTargetId`, `addWorkflowTransition`. |
| Routing/index/event-flow keys       | route uniqueness, duplicate route keys, indexed access-pattern keys, event producer/consumer keys, workflow consumer keys.                                                        |
| Policy/profile helpers              | `rootPolicyIds`, `packageManagerFitsLanguage`.                                                                                                                                    |

## Remaining Structurally Or Runtime-Limited Rules

These EVL rules remain present but are not part of executable failing-model coverage:

| Rule                                   | Reason                                                                                                                                                                                             |
| -------------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `AccessPatternOperationDefined`        | `AccessPattern.operation` is an enum; after EMF loading, omitted values read as the enum default, so EVL cannot distinguish omission without changing the metamodel or generated model convention. |
| `AccessPatternNamesQueryShape`         | Depends on `hasQueryShape()`, which currently treats the defaulted enum operation as a query shape. Tightening this would require coordinated sample/generator/metamodel-impact work.              |
| `CompensationPolicyIsRootOwned`        | `CompensationPolicy` is only loadable through root `PIMModel.policies` containment in current XMI/metamodel usage, so a non-root-owned instance is not meaningfully representable for EVL.         |
| `TimeoutPolicyIsRootOwned`             | Same root-policy containment limitation as compensation policies.                                                                                                                                  |
| `TriggerTargetsFunctionOrWorkflow`     | Contained triggers have the inverse `function` reference defined by containment; a trigger with no target is already represented by `ExactlyOneInvocationTarget` in executable coverage.           |
| `WorkflowStartAndEndStepsAreContained` | The helper methods select start/end steps from the workflow's contained `steps`, making the check tautological for loadable models.                                                                |

No `.emf` metamodel change was made in this session.

## Verification

Focused verification command:

```powershell
mvn -pl packages/java/mde-evl-validator -Dtest=PimSemanticValidationTest test
```

Result: passing.
