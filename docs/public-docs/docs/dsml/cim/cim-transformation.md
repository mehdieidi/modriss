# CIM transformation metadata

Source: `mde/metamodels/cim/cim-transformation.emf`.

CIM-to-PIM refinement is deliberately not presented as a complete deduction from business facts. Service granularity, workflow candidacy, event collaboration, authorization, privacy coverage, and unresolved boundary decisions still require judgment. This module records that judgment as model data so it can be reviewed, transformed, and traced.

## `Risk`

`Risk` records a threat to transformation progress or production readiness. It is different from a `Hotspot`: a risk describes a possible harmful event or condition and its mitigation, while a hotspot records an unresolved question or concentration of uncertainty. The affected-elements link prevents a risk from becoming an unattributed project note.

### Declared attributes

| Attribute            | Type and multiplicity | Meaning                                                                                                                                            | Example                                                                                  |
| -------------------- | --------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------- | ---------------------------------------------------------------------------------------- |
| `riskStatement`      | `String` `0..1`       | States the uncertain event or condition that may harm the business or refinement.                                                                  | `The external identity provider may not return a stable identifier for every applicant.` |
| `probability`        | `String` `0..1`       | Records the likelihood assessment in the vocabulary used by the project.                                                                           | `Medium; observed in 8% of current cases`                                                |
| `impact`             | `String` `0..1`       | Explains the business or transformation consequence if the risk occurs.                                                                            | `Manual review volume may exceed the regional team's capacity.`                          |
| `mitigation`         | `String` `0..1`       | States the action or control intended to reduce the likelihood or impact.                                                                          | `Define a manual identity-review path and monitor inconclusive matches.`                 |
| `productionBlocking` | `Boolean` `0..1`      | States whether the unresolved risk blocks production readiness. A true value requires all other actionable fields and affected elements under EVL. | `true`                                                                                   |

### Relationships

| Feature            | Target and multiplicity      | Kind      | Meaning                                                                                                         |
| ------------------ | ---------------------------- | --------- | --------------------------------------------------------------------------------------------------------------- |
| `affectedElements` | `kernel.ModelElement` `0..*` | reference | Identifies the goals, capabilities, processes, behaviors, information, or external systems exposed to the risk. |

EVL requires statement, probability, impact, mitigation, and affected elements for a production-blocking risk and warns about any risk with no affected elements. `Risk2ReadinessFinding` creates a PIM readiness finding, carries the risk message and mitigation into it, and preserves the source trace.

## `Assumption`

`Assumption` records a statement accepted temporarily so modeling or transformation can continue. It is a specialized `kernel.TransformationAssumption`, so it also carries the assumption statement, validation approach, acceptance state, accepter, and acceptance date. The CIM-specific `businessArea` says where in the domain the assumption matters.

### Declared attributes

| Attribute      | Type and multiplicity | Meaning                                                                                                                                         | Example                 |
| -------------- | --------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------- | ----------------------- |
| `businessArea` | `String` `0..1`       | Identifies the business area affected by the temporary assumption. It helps reviewers find the right owner when the assumption must be checked. | `Identity verification` |

### Relationships

| Feature            | Target and multiplicity      | Kind      | Meaning                                                                                     |
| ------------------ | ---------------------------- | --------- | ------------------------------------------------------------------------------------------- |
| `affectedElements` | `kernel.ModelElement` `0..*` | reference | Identifies the model elements whose interpretation or refinement depends on the assumption. |

Inherited `assumptionStatement` and `validationApproach` are required by the CIM EVL rules. If `accepted=true`, `acceptedBy` and `acceptedOn` must be recorded. `Assumption2ReadinessFinding` turns the assumption into a PIM finding so an unverified premise remains visible after refinement.

## `Hotspot`

`Hotspot` records an unresolved question or concentration of uncertainty. Its purpose is to make a difficult boundary decision visible, assign an owner, and record the consequence of leaving the question open. A hotspot may block transformation, production, both, or neither.

### Declared attributes

| Attribute              | Type and multiplicity | Meaning                                                                                               | Example                                                                                        |
| ---------------------- | --------------------- | ----------------------------------------------------------------------------------------------------- | ---------------------------------------------------------------------------------------------- |
| `question`             | `String` `0..1`       | States the specific question that needs an answer. It should be answerable by a named person or role. | `Should identity verification remain inside application review or become a shared capability?` |
| `impact`               | `String` `0..1`       | Explains what becomes difficult, unsafe, or delayed while the question remains open.                  | `The service boundary and external integration flow cannot be reviewed consistently.`          |
| `owner`                | `String` `0..1`       | Names the person, role, or team responsible for resolving the question.                               | `Architecture review group`                                                                    |
| `dueDate`              | `Date` `0..1`         | Gives the target date for resolution. Blocking hotspots receive a warning when it is absent.          | `2026-05-15`                                                                                   |
| `blocksTransformation` | `Boolean` `0..1`      | States whether the question prevents a safe CIM-to-PIM refinement.                                    | `true`                                                                                         |
| `blocksProduction`     | `Boolean` `0..1`      | States whether the unresolved question prevents a production-oriented decision.                       | `false`                                                                                        |

### Relationships

| Feature      | Target and multiplicity      | Kind      | Meaning                                                             |
| ------------ | ---------------------------- | --------- | ------------------------------------------------------------------- |
| `attachedTo` | `kernel.ModelElement` `0..*` | reference | Identifies the concepts or decisions to which the question applies. |

EVL requires an owner when either blocking flag is true and warns when a blocking hotspot lacks a due date, impact, or attached element. Integration/deployment ETL creates a manual decision from the hotspot so the open question remains in readiness review.

## `TransformationProfile`

`TransformationProfile` records preferences that guide CIM-to-PIM refinement. It does not force the ETL to ignore the model. Instead, it makes project-level choices explicit when several valid architecture interpretations are possible. The profile is optionally contained by `CIMModel`.

### Declared attributes

| Attribute                                       | Type and multiplicity | Meaning                                                                                                                                                           | Example                                                                                 |
| ----------------------------------------------- | --------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------- | --------------------------------------------------------------------------------------- |
| `preferCapabilityAsServiceBoundary`             | `Boolean` `0..1`      | States whether a capability should normally become the boundary of a PIM service when no bounded context gives a stronger boundary.                               | `true`                                                                                  |
| `preferProcessAsWorkflowCandidate`              | `Boolean` `0..1`      | States whether processes should normally be considered for workflow refinement.                                                                                   | `true`                                                                                  |
| `preferEventDrivenCollaboration`                | `Boolean` `0..1`      | States whether collaboration between capabilities should normally favor business events where the model permits it.                                               | `true`                                                                                  |
| `requireExplicitActorAuthForCommands`           | `Boolean` `0..1`      | Enables a root-level EVL rule requiring authorization decisions on human-issued commands.                                                                         | `true`                                                                                  |
| `requirePrivacyClassificationForAllInformation` | `Boolean` `0..1`      | Enables a root-level EVL rule requiring every information item to have a data classification.                                                                     | `true`                                                                                  |
| `generateReadModelsFromQueries`                 | `Boolean` `0..1`      | States whether query semantics should be used to propose read models during transformation.                                                                       | `false`                                                                                 |
| `defaultServiceGranularityRationale`            | `String` `0..1`       | Explains why the profile's default service granularity is preferred and when an exception is justified. EVL asks for it when capability boundaries are preferred. | `Capabilities align with existing ownership and keep changes independently reviewable.` |

### Relationships

| Feature             | Target and multiplicity        | Kind      | Meaning                                                                                                                                                    |
| ------------------- | ------------------------------ | --------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `requiredDecisions` | `kernel.ManualDecision` `0..*` | reference | Lists decisions that must be answered or explicitly accepted before refinement can be considered complete. Each decision should have a question and owner. |

The profile is read by core and readiness validation and by helper logic in the CIM-to-PIM transformation. It is a record of preference, not a substitute for trace links or for a modeler's review of the generated PIM.

## Why this metadata remains in CIM

The transformation boundary is where an apparently simple business model begins to acquire architecture. A capability may become a service, an aggregate may become a store, a command may become a function and route, and a process may become a workflow. Those choices can be sensible without being logically forced by the CIM. Keeping risks, assumptions, hotspots, manual decisions, and profile preferences in the source model allows the generated PIM to explain its origins and allows later synchronization to distinguish generated changes from human refinements.
