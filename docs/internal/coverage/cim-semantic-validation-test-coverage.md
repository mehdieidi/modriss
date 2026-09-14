# CIM Semantic Validation Test Coverage

This document traces the CIM EVL semantic validation coverage added in
`packages/java/mde-evl-validator/src/test/java/io/mehdieidi/modriss/mde/validation/CimSemanticValidationTest.java`.

## Scope

- Level covered: CIM only.
- Entry EVL: `mde/validation/cim/cim-semantic-validation.evl`.
- Metamodel: `mde/metamodels/cim/cim-combined.ecore`.
- Positive model: `mde/samples/cim.xmi`, asserted to produce zero mandatory and zero optional EVL violations.
- Negative models: generated in JUnit from structurally valid mutations of the positive sample.
- Validation boundary: tests use the standalone Epsilon EVL validator and EMF structural loading. They do not use assistant apply/repair/commit paths, `ModelService.validate(...)`, stored validation endpoints, `validateGeneratedXmi(...)`, `EpsilonEvlValidator` outside this validator module, EVL CLIs, or EVL profiles from chatbot assistant paths.

## Coverage Strategy

The JUnit suite uses the real repository CIM sample as the trusted good model, then creates semantic bad-model variants by business area:

| Scenario               | EVL area covered                                                                                                                                   |
| ---------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------- |
| `core-root-readiness`  | CIM root scope, production readiness blockers, transformation-profile authorization/privacy gates                                                  |
| `kernel-trace`         | kernel trace links, generated-element traceability, provider-neutral language, explanation/source critiques                                        |
| `organization-intent`  | requirements, acceptance criteria, goals, KPIs, stakeholders, actors, roles, external systems, capabilities, bounded contexts, ubiquitous language |
| `domain-data`          | entities, value objects, relationships, aggregates, invariants, information items, classifications                                                 |
| `behavior`             | commands, queries, events, business errors, conditions                                                                                             |
| `process-policy`       | processes, concrete steps, transitions, decision tables/rules, policies, exceptions, temporal constraints                                          |
| `governance-readiness` | NFR/security/privacy/compliance constraints, risks, assumptions, hotspots, transformation profile, readiness checks/findings/manual decisions      |

The test asserts every covered rule by EVL constraint/critique name and expected kind:

- `constraint` => `MANDATORY`
- `critique` => `OPTIONAL`

## Removed Redundant Or Stale EVL Rules

These rules were removed from CIM EVL because they are safely enforced by Ecore structural
conformance before EVL runs, or because the rule described a model shape the current Ecore
metamodel cannot represent.

| Removed rule                                                   | Reason                                                                                                                                                            |
| -------------------------------------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `EntityHasPrimaryIdentityAttribute`                            | `DomainEntity.primaryIdentityAttribute` is required by Ecore.                                                                                                     |
| `QueryMustHaveOutput`                                          | `Query.output` has a lower bound and is required by Ecore.                                                                                                        |
| `DecisionTableHasRules`                                        | `DecisionTable.rules` has a lower bound and is required by Ecore.                                                                                                 |
| `NFRConstrainsElements`                                        | inherited `constrainedElements` is required by Ecore for NFR subclasses.                                                                                          |
| `RequiredDecisionsAreReferencedNotContainedReadinessDecisions` | `TransformationProfile.requiredDecisions` is a non-containment reference in the current metamodel, so the modeled “contained decision” case is not representable. |
| `AcyclicSubItems`                                              | `InformationItem.subItems` is containment in the current metamodel, so a recursive containment cycle is structurally invalid before EVL can evaluate it.          |

## EOL Helper Coverage

The `cimEolHelpersHaveExplicitBehaviorCoverage` test imports
`mde/validation/cim/lib/cim-validation-helpers.eol` through a small helper-only EVL module and
asserts the behavior of every remaining CIM helper operation against explicit positive and negative
model fixtures.

| Helper operation                       | Covered behavior                                                                |
| -------------------------------------- | ------------------------------------------------------------------------------- |
| `hasText`                              | true for trimmed non-empty text; false for empty text.                          |
| `hasItems`                             | true for populated collections; false for empty collections.                    |
| `hasMultiplicityBounds`                | true for complete bounded multiplicity; false when the upper bound is missing.  |
| `isMany`                               | true for multiplicity with upper bound greater than one.                        |
| `hasExpressionBody`                    | true for an `Expression` with language/body; false for a text-only condition.   |
| `collectionHasUniqueNames`             | true for unique child business names; false for duplicate child business names. |
| `cimOutgoingTransitionCountBySourceId` | indirectly covered through `outgoingTransitionCount`.                           |
| `outgoingTransitionCount`              | counts multiple outgoing transitions from a decision step.                      |
| `providerTechnologyPattern`            | matches provider-specific technology terms.                                     |
| `labelText`                            | returns a model element's display name.                                         |
| `containsProviderTechnologyTerm`       | detects provider terms in both `name` and `summary`.                            |
| `isPersonalKind`                       | distinguishes public data from personal data.                                   |
| `requiresStrongProtection`             | returns true for authentication-secret data.                                    |
| `hasPersonalClassification`            | returns true when an information item is classified as personal.                |
| `requiresPrivacyControls`              | returns true for personal data and false for explicitly public data.            |
| `hasHumanIssuer`                       | returns true when a command is issued by a human actor.                         |
| `hasExternalOrUntrustedIssuer`         | distinguishes regulated external issuers from trusted internal issuers.         |
| `hasAuthorizationDecision`             | returns true when a command requires authorization and declares a rule.         |
| `outputsPersonalData`                  | distinguishes queries that output personal data from public-only queries.       |
| `isProductionReadyIntent`              | returns true when readiness explicitly marks the model as production ready.     |

The obsolete `hasSubItemCycle` overloads were removed from
`mde/validation/cim/lib/cim-validation-helpers.eol` together with `AcyclicSubItems` because they
targeted a model shape that cannot be represented by the current containment-based metamodel.

## Remaining Untestable Enum Rules

These EVL rules remain in place, but cannot be triggered by a structurally conforming XMI model
because EMF enum defaults make an omitted value indistinguishable from the first enum literal after
loading.

| Rule                                   | Reason                                                                     |
| -------------------------------------- | -------------------------------------------------------------------------- |
| `ActorHasTypeAndTrustLevel`            | enum defaults make missing `actorType`/`trustLevel` read as defined.       |
| `InformationItemHasType`               | enum default makes missing `type` read as defined.                         |
| `DataClassificationHasKind`            | enum default makes missing `kind` read as defined.                         |
| `QueryDeclaresFreshness`               | enum default makes missing `freshnessNeed` read as defined.                |
| `EventPayloadInformationIsTyped`       | depends on `InformationItem.type`, which has the enum-default issue above. |
| `MandatoryOrBlockingPolicyHasSeverity` | enum default makes missing `violationSeverity` read as defined.            |

No `.emf` metamodel change was made in this session.

## Verification

Focused verification command:

```powershell
mvn -pl packages/java/mde-evl-validator -Dtest=CimSemanticValidationTest test
```

Result: passing.
