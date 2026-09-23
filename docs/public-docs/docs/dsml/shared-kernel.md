# Shared kernel reference

The shared kernel is the common semantic foundation of CIM, PIM, and AWS PSM. It gives elements at every modeling level the same concepts for identity, explanation, provenance, lifecycle state, trace links, expressions, and readiness evidence. This vocabulary allows an element to remain identifiable while it is refined from a business concept into a provider-independent design and then into an AWS resource.

The kernel is an Ecore package with namespace URI `https://modriss.org/kernel/1.0`. Each DSML imports it instead of reproducing these concepts. An inherited feature such as `rationale` therefore has the same structural meaning in a CIM command, a PIM function, and an AWS PSM Lambda resource.

Source: `mde/metamodels/shared/kernel.emf`.

## How to read this reference

`[1]` means Ecore requires exactly one value, `[0..1]` means the value is optional, and `[*]` means an unbounded collection. A containment is part of its owner's model subtree. An ordinary reference connects independently owned objects. A readonly transient reference is the navigable inverse of another feature and is not serialized as separate state.

Structural multiplicity and semantic expectation are separate concerns. For example, `ModelElement.name` is optional in Ecore, although CIM EVL requires it and PIM EVL recommends a portable form. The tables state Ecore multiplicity first, then explain stronger behavior imposed by transformations or EVL.

EVL belongs to explicit user or model validation workflows. Assistant-generated actions, patches, proposals, checkpoints, and model outputs are gated only by structural Ecore/EMF conformance through `ModelService.validateStructural(...)`. Assistant apply, repair, and commit paths do not run EVL or stored semantic validation.

## Kernel at a glance

| Area                     | Concepts                                                                                          | Purpose                                                               |
| ------------------------ | ------------------------------------------------------------------------------------------------- | --------------------------------------------------------------------- |
| Identity and description | `KeyValue`, `Annotation`, `ModelElement`, `NamedModelElement`                                     | Stable identity, human explanation, metadata, and lifecycle state.    |
| Provenance               | `TraceableElement`, `SemanticRelationship`, `TraceModel`, `TraceLink`, `TransformationAssumption` | Origin, review history, and correspondence across modeling levels.    |
| Semantic values          | `Multiplicity`, `Cardinality`, `Expression`, `StructuredDocument`                                 | Reusable bounds, typed expressions, and document payloads.            |
| Readiness                | `ProductionReadinessAssessment`, `ReadinessFinding`, `ReadinessCheck`, `ManualDecision`           | Review evidence, failed checks, unresolved questions, and gate state. |

## Enumerations

Enumeration values are closed. Serialized models must use the literal spelling shown below.

### `Priority`

`Priority` expresses relative attention or business urgency. CIM uses it on goals, capabilities, and commands. It does not define scheduling behavior.

| Literal    | Meaning                                                                                                      |
| ---------- | ------------------------------------------------------------------------------------------------------------ |
| `LOW`      | The element can receive attention after more urgent work. It remains in scope.                               |
| `MEDIUM`   | The element has ordinary priority, with no exceptional urgency established.                                  |
| `HIGH`     | The element deserves earlier attention because its importance or urgency is above normal.                    |
| `CRITICAL` | The strongest priority in the kernel, used for work requiring immediate attention in the applicable process. |

### `Severity`

`Severity` describes the consequence attached to a constraint violation, readiness result, or unresolved decision. Whether it stops progress is recorded separately by fields such as `blocking`.

| Literal    | Meaning                                                                                                                                                |
| ---------- | ------------------------------------------------------------------------------------------------------------------------------------------------------ |
| `INFO`     | Informational evidence that should remain visible but does not indicate failure by itself.                                                             |
| `WARNING`  | A concern that deserves review and may become a problem if left unresolved.                                                                            |
| `ERROR`    | A substantive defect or inconsistency requiring correction in the applicable workflow.                                                                 |
| `CRITICAL` | A severe condition with major consequences for the model or its intended use.                                                                          |
| `BLOCKER`  | The strongest severity, reserved for a condition that prevents the relevant progression. The explicit `blocking` flag still governs readiness objects. |

### `ConstraintStrength`

CIM business constraints use `ConstraintStrength` to preserve the difference between guidance and an uncompromising rule.

| Literal       | Meaning                                                                         |
| ------------- | ------------------------------------------------------------------------------- |
| `ADVISORY`    | Guidance that informs a decision while permitting a justified departure.        |
| `RECOMMENDED` | The preferred course, with another choice possible when its reason is recorded. |
| `MANDATORY`   | A requirement expected to hold for every applicable case.                       |
| `BLOCKING`    | A requirement whose violation prevents the associated progression or approval.  |

### `LifecycleStatus`

This enumeration supplies a shared maturity vocabulary. The metamodel encodes no transition state machine. Validation checks only the combinations that matter to a given level.

| Literal                | Meaning                                                                                                                           |
| ---------------------- | --------------------------------------------------------------------------------------------------------------------------------- |
| `DRAFT`                | Work is in progress and has not reached a review conclusion.                                                                      |
| `INCOMPLETE`           | Known information required for the element is still missing.                                                                      |
| `REVIEW_REQUIRED`      | Human or process review is required before further reliance. Both transformations give generated assessments this initial status. |
| `APPROVED`             | The applicable approval has been received, without claiming readiness for a later lifecycle gate.                                 |
| `TRANSFORMATION_READY` | The element is considered ready for the next model transformation.                                                                |
| `DEPLOYMENT_READY`     | The element is considered sufficiently resolved for deployment work.                                                              |
| `PRODUCTION_READY`     | The strongest operational readiness state. CIM and PIM EVL require supporting evidence when it is claimed.                        |
| `BLOCKED`              | Progress is prevented by an unresolved condition or decision.                                                                     |
| `DEPRECATED`           | The element remains representable for history or compatibility, although it is no longer preferred.                               |

### `TraceConfidence`

`TraceConfidence` qualifies one `TraceLink`. Transformation helpers map their textual confidence argument to these values.

| Literal    | Meaning                                                                                                                                           |
| ---------- | ------------------------------------------------------------------------------------------------------------------------------------------------- |
| `LOW`      | A tentative correspondence requiring close review.                                                                                                |
| `MEDIUM`   | A plausible correspondence that retains interpretation or incomplete evidence. It is the helper's fallback for an unrecognised confidence string. |
| `HIGH`     | Strong confidence that the endpoints correspond as stated.                                                                                        |
| `VERIFIED` | The correspondence has been explicitly verified.                                                                                                  |
| `MANUAL`   | Human judgement supplied the classification. It describes the basis and is not a level above `VERIFIED`.                                          |

### `TraceLinkType`

The endpoints state what is connected; `TraceLinkType` states the semantic direction of that connection.

| Literal          | Meaning                                                                                                                     |
| ---------------- | --------------------------------------------------------------------------------------------------------------------------- |
| `DERIVES_FROM`   | The target originates from evidence or meaning held by the source.                                                          |
| `REFINES`        | The target makes the source more precise while retaining its intent.                                                        |
| `SATISFIES`      | The target fulfils a need, requirement, or condition represented by the source.                                             |
| `CONSTRAINS`     | The source limits the permitted form or behavior of the target.                                                             |
| `GENERATED_FROM` | The target was mechanically produced using the source as input.                                                             |
| `VALIDATES`      | The source records or performs a validation claim concerning the target.                                                    |
| `MITIGATES`      | The source reduces a risk or problem represented by the target.                                                             |
| `CONFLICTS_WITH` | The endpoints contain incompatible intentions or conditions requiring resolution.                                           |
| `MAPS_TO`        | General correspondence without claiming refinement or realization. PIM-to-AWS PSM uses it for several structured documents. |
| `TRANSFORMS_TO`  | A model transformation established the target from the source. Both transformation stages use it for their main traces.     |
| `REALIZES`       | The target provides a concrete realization of the source concept.                                                           |
| `IMPLEMENTS`     | The target implements behavior, policy, or structure specified by the source.                                               |
| `DEPLOYS_TO`     | The source is placed into, or made operational through, the target context.                                                 |
| `OBSERVES`       | The source monitors or records information about the target.                                                                |
| `OTHER`          | A meaningful relation outside the named set. Its rationale should explain the intended meaning.                             |

### `FindingType`

This classification is independent from severity and blocking status, allowing readiness reports to group findings by cause.

| Literal                     | Meaning                                                                                                      |
| --------------------------- | ------------------------------------------------------------------------------------------------------------ |
| `MISSING_INFORMATION`       | Required knowledge or a modeling answer is absent. PIM-to-AWS PSM uses it beside generated manual decisions. |
| `INCONSISTENCY`             | Parts of the model or its evidence disagree.                                                                 |
| `BLOCKING_RISK`             | A known risk prevents the relevant progression.                                                              |
| `BLOCKING_HOTSPOT`          | A concentrated area of uncertainty or design difficulty blocks progress.                                     |
| `VALIDATION_WARNING`        | An advisory validation concern.                                                                              |
| `VALIDATION_ERROR`          | A validation error. The PIM-to-AWS PSM finding helper uses this type.                                        |
| `TRANSFORMATION_ASSUMPTION` | A transformation proceeded on an assumption still needing visibility or confirmation.                        |
| `POLICY_VIOLATION`          | The modeled state conflicts with an applicable policy.                                                       |
| `SECURITY_RISK`             | The concern relates to modeled security properties or exposure.                                              |
| `DEPLOYMENT_RISK`           | The concern affects the ability or safety of deployment.                                                     |
| `OTHER`                     | Valid readiness evidence outside the named categories.                                                       |

### `StructuredFormat`

`StructuredFormat` tells a consumer how document content must be interpreted.

| Literal                    | Meaning                                                                                          |
| -------------------------- | ------------------------------------------------------------------------------------------------ |
| `TEXT`                     | Plain text with no more specific serialization contract.                                         |
| `JSON`                     | General JSON. Transformations use it for schemas, event contracts, rule documents, and metadata. |
| `YAML`                     | General YAML content.                                                                            |
| `TOML`                     | TOML configuration content.                                                                      |
| `XML`                      | XML content.                                                                                     |
| `MARKDOWN`                 | Markdown source intended for human-readable documentation.                                       |
| `ASL_JSON`                 | Amazon States Language as JSON, used by generated `AslDocument` objects.                         |
| `IAM_POLICY_JSON`          | An AWS IAM policy document serialized as JSON.                                                   |
| `CLOUDFORMATION_INTRINSIC` | CloudFormation intrinsic-function syntax rather than ordinary literal data.                      |
| `OTHER`                    | A known format outside this set; surrounding documentation must identify it.                     |

### `ExpressionLanguage`

The body remains a string in Ecore. This literal provides its interpretation contract.

| Literal            | Meaning                                                                                                                         |
| ------------------ | ------------------------------------------------------------------------------------------------------------------------------- |
| `NATURAL_LANGUAGE` | Human-readable wording evaluated through review. CIM-to-PIM uses it for provisional decision logic derived from business prose. |
| `OCL`              | Object Constraint Language over model structure.                                                                                |
| `FEEL`             | Friendly Enough Expression Language for decision-oriented logic.                                                                |
| `JSONPATH`         | JSONPath selection over JSON-shaped data.                                                                                       |
| `JSONATA`          | JSONata querying or transformation of JSON data.                                                                                |
| `JAVASCRIPT`       | JavaScript source or expression text.                                                                                           |
| `TYPESCRIPT`       | TypeScript source or expression text.                                                                                           |
| `PYTHON`           | Python source or expression text.                                                                                               |
| `SQL`              | SQL whose exact query context comes from the owning feature.                                                                    |
| `REGEX`            | A regular expression for textual pattern matching.                                                                              |
| `OTHER`            | Another expression language identified by surrounding model context.                                                            |

### `ExpressionPhase`

This value prevents design guidance, validation logic, runtime behavior, and generator directives from being treated as interchangeable text.

| Literal           | Meaning                                                                                    |
| ----------------- | ------------------------------------------------------------------------------------------ |
| `DESIGN_TIME`     | Used in modeling or design reasoning. Generated natural-language decisions use this phase. |
| `VALIDATION_TIME` | Evaluated while checking a model or related artifact.                                      |
| `RUNTIME`         | Participates in deployed-system behavior.                                                  |
| `GENERATION_TIME` | Interpreted while producing a target model or artifacts.                                   |

### `ManualDecisionState`

| Literal    | Meaning                                                                |
| ---------- | ---------------------------------------------------------------------- |
| `OPEN`     | No accepted disposition has been recorded.                             |
| `ACCEPTED` | The answer has been accepted for the current review context.           |
| `REJECTED` | The proposed answer was considered and declined.                       |
| `WAIVED`   | The concern was consciously set aside, potentially until `expiryDate`. |
| `DEFERRED` | Resolution was postponed to a later date, phase, or condition.         |

## Marker interfaces

Marker interfaces declare no features. They act as type contracts: a reference declared against a marker can point to any class that implements that capability. The current metamodel uses them mainly in PIM, allowing meaningful endpoint sets without reducing references to the broad `ModelElement` type.

| Interface                   | Meaning and current use                                                                                                                                                                                                                                                                     |
| --------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `DeployableElement`         | A concept eligible for deployment structure. `DeploymentAssignment.element` selects one, while `DeploymentUnit.contains` groups them. Implementers include functions, workflows, storage, APIs, channels, schedules, identity providers, configuration sets, decision models, and adapters. |
| `InvocationSource`          | An initiator accepted by `Trigger.source`, including API routes, channels, schedules, object stores, data streams, and routing rules.                                                                                                                                                       |
| `InvocationTarget`          | A general invocation receiver. Functions, workflows, and external adapters implement it.                                                                                                                                                                                                    |
| `FunctionTarget`            | A callable target with function-like responsibility. It types producer, consumer, policy-enforcement, data-use, and adapter-function references.                                                                                                                                            |
| `WorkflowTarget`            | A workflow admissible for the narrower `Trigger.startsWorkflow` relationship.                                                                                                                                                                                                               |
| `SubscriptionTarget`        | A receiver of subscription-delivered messages or events, including functions, workflows, stores, channels, and adapters.                                                                                                                                                                    |
| `RoutingTarget`             | An admissible event-routing destination, used by channels, object-store notifications, and routing rules.                                                                                                                                                                                   |
| `FlowEndpoint`              | A source or target for abstract PIM `Flow`, spanning API, compute, workflow, storage, integration, contract, and adapter concepts.                                                                                                                                                          |
| `ProtectedResource`         | A resource eligible for `AccessControl.targetResource`. This keeps security rules independent from one concrete resource family.                                                                                                                                                            |
| `PolicyTarget`              | Anything to which PIM policy may attach, including executable, data, contract, deployment, integration, security, and flow elements.                                                                                                                                                        |
| `DataAccessTarget`          | A modeled data-access destination. `StorageElement` supplies the capability to its concrete subtypes.                                                                                                                                                                                       |
| `ExternalCallTarget`        | An external endpoint or adapter eligible to receive a modeled outbound call.                                                                                                                                                                                                                |
| `EnvironmentTarget`         | An environment eligible for `ConfigurationSet.environments`.                                                                                                                                                                                                                                |
| `CredentialRequirementLike` | The shared contract for credential requirements. PIM `CredentialRequirement` is the current implementer.                                                                                                                                                                                    |
| `RouteEndpoint`             | An API route eligible for callback-task and data-model exposure references.                                                                                                                                                                                                                 |
| `EventCarrier`              | A channel, change stream, or object store capable of carrying events; resilience policy uses it for a dead-letter channel.                                                                                                                                                                  |
| `ConfigurableElement`       | An element eligible for `ConfigurationSet.appliesTo`, including APIs, functions, workflows, stores, deployment structures, and adapters.                                                                                                                                                    |

The markers are orthogonal roles. A function implements several because it is deployable, invocable, configurable, protectable, and eligible for several routing and policy relationships.

## Common identity and description

### `KeyValue`

`KeyValue` is a small reusable record. Its owner determines what the pair means. AWS PSM stage parameter overrides reuse it instead of introducing a provider-specific pair class.

| Attribute | Type            | Meaning                                                                                                       |
| --------- | --------------- | ------------------------------------------------------------------------------------------------------------- |
| `key`     | `String [1]`    | Required lookup name. The class cannot enforce uniqueness because that rule depends on the owning collection. |
| `value`   | `String [0..1]` | Optional text associated with the key.                                                                        |

It has no relationships and does not inherit `ModelElement`, so it has no independent identity or trace history.

### `Annotation`

`Annotation` extends `KeyValue` with a producer or namespace and belongs to one model element. It suits tool metadata that must travel with an element but does not justify a DSML feature.

| Feature  | Type                                             | Meaning                                                                                        |
| -------- | ------------------------------------------------ | ---------------------------------------------------------------------------------------------- |
| `source` | `String [0..1]`                                  | System, convention, or namespace that supplied the annotation, qualifying the inherited key.   |
| `owner`  | `ModelElement [1]`, readonly transient reference | Back-reference maintained through `ModelElement.annotations`; it is not serialized separately. |

The inherited `key` is required and `value` remains optional. The annotation's lifecycle follows its owner.

### `ModelElement` abstract

`ModelElement` establishes stable technical identity beside human explanation, lifecycle state, annotations, and bidirectional trace navigation. Almost every CIM, PIM, and AWS PSM concept inherits it through `TraceableElement`.

| Attribute         | Type                     | Meaning                                                                                                                                                                   |
| ----------------- | ------------------------ | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `id`              | `String [1]`, Ecore ID   | Required synchronization identity used by persistence, references, transformations, and traces. Shared EVL requires non-blank uniqueness across all model elements.       |
| `name`            | `String [0..1]`          | Concise model-facing name used in labels, diagnostics, transformation output, and generated naming. It may change, so it is not the stable identity. CIM EVL requires it. |
| `summary`         | `String [0..1]`          | Compact statement of responsibility or meaning for lists, diagrams, and generated descriptions.                                                                           |
| `description`     | `String [0..1]`          | Fuller semantic explanation of the represented concept.                                                                                                                   |
| `documentation`   | `String [0..1]`          | Extended context or usage material that would make the shorter explanation fields unwieldy.                                                                               |
| `modelTags`       | `String [*]`             | Open classification labels for search, views, and project conventions. The kernel defines no vocabulary or uniqueness rule.                                               |
| `externalId`      | `String [0..1]`          | Identity assigned outside the current model, also accepted by shared trace validation as external trace identity.                                                         |
| `lifecycleStatus` | `LifecycleStatus [0..1]` | Current maturity or readiness classification. This is separate from the free-form review workflow text on `TraceableElement`.                                             |

| Relationship     | Kind                            | Meaning                                                                        |
| ---------------- | ------------------------------- | ------------------------------------------------------------------------------ |
| `annotations`    | containment to `Annotation [*]` | Owns metadata pairs and maintains each child's inverse `owner`.                |
| `incomingTraces` | reference to `TraceLink [*]`    | Links whose `target` is this element. The `TraceModel` still owns those links. |
| `outgoingTraces` | reference to `TraceLink [*]`    | Links whose `source` is this element, enabling source-to-target navigation.    |

The validation helper `labelText()` chooses `name`, then `id`, then the EClass name. This fallback improves diagnostics; it does not replace naming or identity requirements.

### `NamedModelElement` abstract

This subtype is for concepts whose abstract syntax requires a distinct presentation label.

| Attribute     | Type         | Meaning                                                                                 |
| ------------- | ------------ | --------------------------------------------------------------------------------------- |
| `displayName` | `String [1]` | Required human-facing label, kept separate from stable `id` and general-purpose `name`. |

It declares no relationships. No current CIM, PIM, or AWS PSM class extends it directly, but it remains available as a stricter shared naming contract.

## Provenance and correspondence

### `TraceableElement` abstract

`TraceableElement` adds evidence and review history to `ModelElement`. Source fields point to documentary or model evidence. Generation fields record mechanical origin. Review fields show how people should treat generated or manually maintained content.

Every declared attribute is structurally optional. CIM asks each traceable element for a source or rationale and requires an origin when `generatedByTransformation` is true. PIM applies a similar generated-origin critique and asks manually maintained elements for rationale or review notes.

| Attribute                   | Type             | Meaning                                                                                                                                                                   |
| --------------------------- | ---------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `sourceReference`           | `String [0..1]`  | Human-readable citation or locator for originating evidence, such as a requirement ID, workshop record, policy, or source element.                                        |
| `sourceExcerpt`             | `String [0..1]`  | Relevant source passage retained with the element so reviewers can compare the interpretation with its original wording.                                                  |
| `sourceQualifiedName`       | `String [0..1]`  | Qualified source name, disambiguating equal local names from different packages, boundaries, or documents. Generator naming may use it as a fallback.                     |
| `sourceUri`                 | `String [0..1]`  | URI or location of the source artifact. The kernel deliberately requires no particular URI scheme.                                                                        |
| `sourceLine`                | `String [0..1]`  | Source line, range, or source-specific position. A string accommodates locations richer than one integer.                                                                 |
| `traceId`                   | `String [0..1]`  | Correlation identity across transformations and generated artifacts. CIM-to-PIM builds it from rule, source, and target IDs. Generator naming can use it as a stable key. |
| `generatedFrom`             | `String [0..1]`  | Identity of the source that generated this element, useful when a cross-model live reference cannot be retained.                                                          |
| `generatedByTransformation` | `Boolean [0..1]` | States that transformation logic created the element. Helpers set it for targets, traces, readiness evidence, and generated support objects.                              |
| `rationale`                 | `String [0..1]`  | Explains why the element exists or why one interpretation was selected. Transformation helpers also record their producing rule here.                                     |
| `reviewStatus`              | `String [0..1]`  | Open review-workflow label. CIM-to-PIM uses values such as `reviewable` and `review-required`; the field is intentionally independent from `LifecycleStatus`.             |
| `reviewNotes`               | `String [0..1]`  | Human review commentary. PIM-to-AWS PSM places a generated manual decision's recommendation here.                                                                         |
| `manuallyMaintained`        | `Boolean [0..1]` | Identifies content maintained by people after creation. Transformation-created objects normally set it to `false`; PIM EVL requests justification when it is `true`.      |

No relationships are declared beyond those inherited from `ModelElement`.

### `SemanticRelationship` abstract

`SemanticRelationship` classifies a relationship as a model element with identity and provenance. It adds no features to `TraceableElement`. CIM uses it as the base of `RequirementRelationship`, `CapabilityDependency`, `DomainRelationship`, and `ProcessTransition`. Those links therefore carry their own source evidence, rationale, lifecycle state, annotations, and trace navigation instead of existing only as anonymous Ecore references.

### `TraceModel`

`TraceModel` is the containment root for correspondence links associated with a model. CIM, PIM, and AWS PSM roots can each own one. Both transformation chains create a target-side trace model.

| Attribute               | Type            | Meaning                                                                                                             |
| ----------------------- | --------------- | ------------------------------------------------------------------------------------------------------------------- |
| `sourceModelId`         | `String [0..1]` | Identity of the model from which the recorded transformation started. Individual links identify elements within it. |
| `sourceRevision`        | `String [0..1]` | Source revision used for this run, distinguishing executions against the same model identity.                       |
| `targetModelId`         | `String [0..1]` | Identity of the target model described by the links.                                                                |
| `targetRevision`        | `String [0..1]` | Revision of that target when the trace was recorded.                                                                |
| `transformationId`      | `String [0..1]` | Identity of the transformation definition or pipeline. Links can name a more precise rule.                          |
| `transformationVersion` | `String [0..1]` | Version of the transformation logic, needed when mappings evolve.                                                   |
| `generatedAt`           | `Date [0..1]`   | Time at which the trace model was created.                                                                          |
| `generatedBy`           | `String [0..1]` | Tool, process, or actor that created the record. It differs from the inherited Boolean origin flag.                 |

| Relationship | Kind                           | Meaning                                                                        |
| ------------ | ------------------------------ | ------------------------------------------------------------------------------ |
| `links`      | containment to `TraceLink [*]` | Owns the correspondence records and supplies each link's inverse `traceModel`. |

Current helpers create the container and core link data without filling every run-metadata field. Optionality permits orchestration or review to add those details.

### `TraceLink`

`TraceLink` is one directional correspondence statement. Live endpoint references suit elements available in the loaded resource set. Textual IDs preserve correspondence when an endpoint belongs to another model or serialization.

| Attribute             | Type                  | Meaning                                                                                                                                          |
| --------------------- | --------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------ |
| `transformationRule`  | `String [0..1]`       | ETL rule or helper decision that created the link, giving finer provenance than the enclosing transformation identity.                           |
| `confidenceRationale` | `String [0..1]`       | Reason for the selected confidence, especially useful for tentative or manually classified links.                                                |
| `confidenceScore`     | `Double [0..1]`       | Optional numeric confidence. The kernel defines no range or conversion to `TraceConfidence`; the producing workflow must define that convention. |
| `sourceElementId`     | `String [0..1]`       | Stable textual identity of the source endpoint. Transformations populate it even if no live `source` reference is stored.                        |
| `targetElementId`     | `String [0..1]`       | Stable textual identity of the target endpoint. Helpers populate it together with the source ID.                                                 |
| `linkType`            | `TraceLinkType [1]`   | Required semantic direction, turning two endpoints into a statement such as transformation or refinement.                                        |
| `confidence`          | `TraceConfidence [1]` | Required qualitative trust or assessment basis for the statement.                                                                                |

| Relationship | Kind                                           | Meaning                                                              |
| ------------ | ---------------------------------------------- | -------------------------------------------------------------------- |
| `traceModel` | `TraceModel [1]`, readonly transient reference | Container navigation maintained by the opposite `links` containment. |
| `source`     | reference to `ModelElement [0..1]`             | Live source. When present, the link appears in its `outgoingTraces`. |
| `target`     | reference to `ModelElement [0..1]`             | Live target. When present, the link appears in its `incomingTraces`. |

Shared EVL accepts at least one endpoint reference or endpoint ID, or inherited `externalId`. CIM and PIM profiles are stricter and require both live endpoints or both textual IDs. CIM also rejects live self-links. This illustrates a permissive shared structure receiving level-specific semantics.

### `TransformationAssumption` abstract

This class represents a proposition that transformation logic could not derive with certainty. Recording it separates source meaning from transformation judgement.

| Attribute             | Type             | Meaning                                                                                       |
| --------------------- | ---------------- | --------------------------------------------------------------------------------------------- |
| `assumptionStatement` | `String [0..1]`  | The proposition used by the transformation, stated directly enough to accept or challenge.    |
| `validationApproach`  | `String [0..1]`  | Evidence or activity expected to confirm the proposition.                                     |
| `accepted`            | `Boolean [0..1]` | Whether it has been accepted. Absence leaves acceptance unspecified, unlike explicit `false`. |
| `acceptedBy`          | `String [0..1]`  | Person, role, or authority that accepted it.                                                  |
| `acceptedOn`          | `Date [0..1]`    | Time of acceptance.                                                                           |

It declares no relationships. CIM `Assumption` is its current concrete subtype and adds transformation-specific scope and impact, while retaining this shared acceptance record.

## Reusable semantic values

### `Multiplicity`

`Multiplicity` represents participation bounds as a traceable element. CIM `DomainRelationship` requires one at each end, and `CollectionInformation` may use one as cardinality. Bounds can therefore carry source evidence and rationale.

| Attribute    | Type                             | Meaning                                                                                                                            |
| ------------ | -------------------------------- | ---------------------------------------------------------------------------------------------------------------------------------- |
| `lowerBound` | `Integer [1]`                    | Required minimum. This requirement is the main structural difference from `Cardinality`.                                           |
| `upperBound` | `Integer [0..1]`                 | Finite maximum when one exists.                                                                                                    |
| `unbounded`  | `Boolean [0..1]`                 | Explicitly states the absence of a finite maximum, avoiding a sentinel integer.                                                    |
| `ordered`    | `Boolean [0..1]`                 | Whether the order of multiple values carries meaning. Emfatic escapes it as `~ordered` because the word has language significance. |
| `unique`     | `Boolean [0..1]`, default `true` | Whether repeated equal values are disallowed. Omission receives the Ecore default `true`.                                          |

It adds no relationships.

### `Cardinality`

PIM `SchemaField` owns `Cardinality` to express contract-level occurrence rules. Its optional lower bound allows a contract to leave the minimum unspecified.

| Attribute    | Type                             | Meaning                                                                              |
| ------------ | -------------------------------- | ------------------------------------------------------------------------------------ |
| `lowerBound` | `Integer [0..1]`                 | Optional minimum. Absence means unspecified, rather than automatically meaning zero. |
| `upperBound` | `Integer [0..1]`                 | Optional finite maximum.                                                             |
| `unbounded`  | `Boolean [0..1]`                 | Explicit absence of a finite upper limit.                                            |
| `ordered`    | `Boolean [0..1]`                 | Whether value order is semantically relevant.                                        |
| `unique`     | `Boolean [0..1]`, default `true` | Whether duplicates are forbidden, with uniqueness as the default.                    |

It adds no relationships. Inherited provenance can identify the business rule or contract behind a bound.

### `Expression`

`Expression` is the typed wrapper for logic stored as text. CIM uses it for conditions, rules, durations, outcomes, deadlines, and ordering. PIM uses it for filters, workflow choices, schema constraints, business and decision rules, and access rules.

| Attribute        | Type                     | Meaning                                                                                                                 |
| ---------------- | ------------------------ | ----------------------------------------------------------------------------------------------------------------------- |
| `language`       | `ExpressionLanguage [1]` | Required language contract for parsing or reviewing the body.                                                           |
| `body`           | `String [1]`             | Required text whose grammar and evaluation meaning come from `language` and the owning feature.                         |
| `phase`          | `ExpressionPhase [0..1]` | Lifecycle phase in which the expression is intended for use.                                                            |
| `sideEffectFree` | `Boolean [0..1]`         | Explicit claim that evaluation observes without changing external or model state; it is not inferred from the language. |

| Relationship         | Kind                            | Meaning                                                                                                          |
| -------------------- | ------------------------------- | ---------------------------------------------------------------------------------------------------------------- |
| `referencedElements` | reference to `ModelElement [*]` | Model concepts used by the expression, making dependencies inspectable without parsing every supported language. |

CIM-to-PIM preserves all four attributes when cloning an expression. When materialising textual decision logic, it creates `NATURAL_LANGUAGE`, `DESIGN_TIME`, side-effect-free expressions.

### `StructuredDocument`

`StructuredDocument` carries a document payload as a traceable model object. It bridges modeled concepts and serializations visible in the PSM before artifact generation. AWS PSM specialises it as `AslDocument`.

| Attribute     | Type                   | Meaning                                                                                                  |
| ------------- | ---------------------- | -------------------------------------------------------------------------------------------------------- |
| `format`      | `StructuredFormat [1]` | Required declaration of payload encoding. Consumers should not infer it from a name or URI.              |
| `content`     | `String [0..1]`        | Inline payload. The artifact generator emits ordinary structured documents only when content is present. |
| `externalUri` | `String [0..1]`        | Location of externally maintained content or its publication/source location.                            |

PIM-to-AWS PSM creates JSON documents for schemas, event contracts, business rules, configuration metadata, and service metadata, then gathers uncontained documents under `AwsPsmModel.documents`. Generation writes non-ASL documents with inline content to role-sensitive paths. ASL documents use their dedicated generation path.

## Readiness and human review

### `ProductionReadinessAssessment`

This aggregate record is optionally contained by each DSML root. Despite its name, it covers three progressive gates: transformation, deployment, and production. Its children provide the evidence behind the summary state.

| Attribute             | Type                     | Meaning                                                                                                                                        |
| --------------------- | ------------------------ | ---------------------------------------------------------------------------------------------------------------------------------------------- |
| `assessedAt`          | `Date [0..1]`            | Time of assessment. CIM EVL critiques a missing time or assessor.                                                                              |
| `assessedBy`          | `String [0..1]`          | Person, role, tool, or transformation responsible for the result. Generated assessments name their producing transformation.                   |
| `gateId`              | `String [0..1]`          | Methodology gate or review point represented by this assessment.                                                                               |
| `assessedRevision`    | `String [0..1]`          | Model or evidence revision assessed, preventing silent reuse after change.                                                                     |
| `evidenceUri`         | `String [0..1]`          | Location of a larger supporting report or evidence set.                                                                                        |
| `openBlockingCount`   | `Integer [0..1]`         | Stored count for reporting. The metamodel does not derive it automatically from child collections.                                             |
| `transformationReady` | `Boolean [0..1]`         | Summary decision that the model may enter the next model transformation.                                                                       |
| `deploymentReady`     | `Boolean [0..1]`         | Summary decision that deployment work may proceed.                                                                                             |
| `productionReady`     | `Boolean [0..1]`         | Summary production decision. PIM EVL also requires both earlier flags, passed checks, no blocking findings, and answers to blocking decisions. |
| `readinessStatus`     | `LifecycleStatus [0..1]` | Lifecycle classification of the assessment as a whole. CIM requires `PRODUCTION_READY` when the production flag is true.                       |

| Relationship      | Kind                                  | Meaning                                                        |
| ----------------- | ------------------------------------- | -------------------------------------------------------------- |
| `findings`        | containment to `ReadinessFinding [*]` | Owns issue-oriented evidence that may not reduce to pass/fail. |
| `checks`          | containment to `ReadinessCheck [*]`   | Owns repeatable pass/fail evaluations.                         |
| `manualDecisions` | containment to `ManualDecision [*]`   | Owns questions requiring accountable human judgement.          |

Both transformation stages initialize generated assessments as `REVIEW_REQUIRED` with all three readiness flags false. Transformation completion therefore does not claim deployment or production readiness.

### `ReadinessFinding`

A finding records an observed issue, risk, inconsistency, or missing fact. It combines explanation, recommended response, impact, and exact model scope.

| Attribute        | Type                 | Meaning                                                                                                       |
| ---------------- | -------------------- | ------------------------------------------------------------------------------------------------------------- |
| `ruleId`         | `String [0..1]`      | Stable identity of the rule or transformation branch that raised it, suitable for grouping and deduplication. |
| `message`        | `String [0..1]`      | Human-readable account of the observed condition. CIM requires it on blocking findings.                       |
| `recommendation` | `String [0..1]`      | Concrete guidance for resolution, mitigation, or review. PIM recommends it for every finding.                 |
| `blocking`       | `Boolean [0..1]`     | Explicit gate effect. Severity does not compute this value.                                                   |
| `severity`       | `Severity [1]`       | Required consequence classification.                                                                          |
| `findingType`    | `FindingType [0..1]` | Category explaining the nature of the issue.                                                                  |

| Relationship       | Kind                                                              | Meaning                                                                 |
| ------------------ | ----------------------------------------------------------------- | ----------------------------------------------------------------------- |
| `assessment`       | `ProductionReadinessAssessment [1]`, readonly transient reference | Navigation to the owning assessment through its `findings` containment. |
| `affectedElements` | reference to `ModelElement [*]`                                   | Exact target-model elements concerned. The finding never owns them.     |

Helpers derive stable finding identities from rule, affected-element keys, and message, reusing an existing object when the identity repeats.

### `ReadinessCheck`

A check records a named pass/fail evaluation and the action required after failure.

| Attribute     | Type             | Meaning                                                                                                   |
| ------------- | ---------------- | --------------------------------------------------------------------------------------------------------- |
| `checkId`     | `String [1]`     | Required durable key, allowing reports to correlate a check when wording changes.                         |
| `passed`      | `Boolean [0..1]` | Evaluation result. Absence leaves the outcome unstated and must not be read as success.                   |
| `message`     | `String [0..1]`  | What was evaluated and why this result was reached.                                                       |
| `remediation` | `String [0..1]`  | Action needed to make a failed check pass. CIM and PIM expect failed checks to provide it with a message. |
| `severity`    | `Severity [1]`   | Required consequence classification of failure or concern.                                                |

| Relationship       | Kind                                                              | Meaning                                   |
| ------------------ | ----------------------------------------------------------------- | ----------------------------------------- |
| `assessment`       | `ProductionReadinessAssessment [1]`, readonly transient reference | Navigation to the owner through `checks`. |
| `affectedElements` | reference to `ModelElement [*]`                                   | Precise model scope of the evaluation.    |

CIM-to-PIM builds the object ID from `checkId` and affected keys. PIM-to-AWS PSM also includes the message, tying a check instance to a generated diagnostic.

### `ManualDecision`

`ManualDecision` turns unresolved human judgement into model data. It keeps the question, responsibility, disposition, timing, impact, and affected elements together, preventing transformation assumptions from disappearing into logs or meeting notes.

| Attribute            | Type                         | Meaning                                                                                                                                              |
| -------------------- | ---------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------- |
| `question`           | `String [1]`                 | Required question for the responsible reviewer. Transformations use this text as the review task.                                                    |
| `decision`           | `String [0..1]`              | Substantive answer, distinct from `state`, which records its disposition. PIM requires an answer and owner for a manually created blocking decision. |
| `state`              | `ManualDecisionState [0..1]` | Structured disposition. Absence means it has not been classified.                                                                                    |
| `decisionOwner`      | `String [0..1]`              | Accountable person, role, or team. CIM requires it for blocking decisions; generated PIM decisions should name an owner or affected elements.        |
| `decisionRecordedAt` | `Date [0..1]`                | Time when the current answer or state was recorded.                                                                                                  |
| `expiryDate`         | `Date [0..1]`                | Date after which a time-limited disposition, especially a waiver or deferral, needs review. The metamodel does not restrict it to those states.      |
| `dueDate`            | `Date [0..1]`                | Expected resolution date. CIM critiques its absence on a blocking decision.                                                                          |
| `blocking`           | `Boolean [0..1]`             | Explicit effect on readiness progression.                                                                                                            |
| `severity`           | `Severity [0..1]`            | Consequence of leaving the question unresolved or choosing an unsafe outcome. Unlike finding and check severity, it is optional.                     |

| Relationship       | Kind                                                                 | Meaning                                                                                                                                                                                                        |
| ------------------ | -------------------------------------------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `assessment`       | `ProductionReadinessAssessment [0..1]`, readonly transient reference | Owning-assessment navigation. It is optional because a decision may exist without belonging to an assessment. For example, `TransformationProfile.requiredDecisions` can reference a manual decision directly. |
| `affectedElements` | reference to `ModelElement [*]`                                      | Elements whose transformation, deployment, or production status depends on the answer.                                                                                                                         |

CIM-to-PIM creates review-required decisions for unresolved choices and attaches target-side affected elements. PIM-to-AWS PSM creates a corresponding `MISSING_INFORMATION` finding beside each decision, preserving both the human task and the readiness issue that caused it.

## Ownership and navigation

```text
CIMModel / PIMModel / AwsPsmModel
├── traceModel: TraceModel
│   └── links: TraceLink[*]
├── readiness: ProductionReadinessAssessment
│   ├── findings: ReadinessFinding[*]
│   ├── checks: ReadinessCheck[*]
│   └── manualDecisions: ManualDecision[*]
└── documents: StructuredDocument[*]      (AWS PSM only)

any ModelElement
└── annotations: Annotation[*]
```

Trace endpoints and `affectedElements` are non-containment references. They point into existing model trees and never transfer ownership of domain content. The inverse features `owner`, `traceModel`, and `assessment` are readonly and transient, so authors edit the containment side.

## Semantic rules governing the kernel

The shared profile enforces two minimum invariants:

- Every `ModelElement.id` contains text and is unique in the logical model.
- A `TraceLink` provides at least one live endpoint, one textual endpoint ID, or inherited `externalId`.

CIM and PIM add contextual rules for names, provenance, complete trace endpoint pairs, readiness consistency, actionable findings, failed-check remediation, and ownership of blocking decisions. These rules do not change Ecore multiplicity. They evaluate whether a structurally conforming model is semantically suitable for a particular workflow.

For exact guards and diagnostics, see [Shared kernel validation](../evl/shared/kernel-constraints.md), together with the CIM and PIM EVL pages for their level-specific kernel contexts.
