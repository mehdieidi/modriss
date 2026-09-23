# CIM domain and data

Source: `mde/metamodels/cim/cim-domain-data.emf`.

This module gives the CIM a precise business vocabulary. `DomainEntity` is used when identity and lifecycle matter. `ValueObject` is used for a descriptive value whose equality comes from its contents. `InformationItem` describes a piece of business information that can appear in an entity, command, query, event, decision, or external exchange. `AggregateCandidate` records a proposed consistency boundary without committing to a persistence technology.

The model keeps meaning separate from implementation. A domain entity is not automatically a database table. A collection is not automatically a JSON array in a generated contract. A classification is not a cloud encryption setting. These objects describe business intent that later PIM and AWS PSM transformations refine.

## `DomainConcept`

`DomainConcept` is the abstract base for `DomainEntity`, `ValueObject`, and `AggregateCandidate`. It carries vocabulary and ownership context that all three kinds of concept share. It also exposes both sides of every `DomainRelationship`, allowing diagrams and analyses to navigate from a concept to its outgoing and incoming links.

### Declared attributes

| Attribute            | Type and multiplicity | Meaning                                                                                                                                    | Example                                                                          |
| -------------------- | --------------------- | ------------------------------------------------------------------------------------------------------------------------------------------ | -------------------------------------------------------------------------------- |
| `glossaryDefinition` | `String` `0..1`       | Gives the domain definition of the concept. This is the preferred place to distinguish business meaning from a technical class definition. | `An application is a request for assistance submitted by an eligible applicant.` |
| `examples`           | `String` `0..1`       | Provides representative instances or situations that make the concept recognizable to domain reviewers.                                    | `Application A-1042 for roof repair after flooding`                              |
| `businessOwner`      | `String` `0..1`       | Names the person, team, or business area accountable for the concept when an explicit capability link is not available.                    | `Regional grants office`                                                         |

### Relationships

| Feature                 | Target and multiplicity     | Kind and opposite                               | Meaning                                                  |
| ----------------------- | --------------------------- | ----------------------------------------------- | -------------------------------------------------------- |
| `outgoingRelationships` | `DomainRelationship` `0..*` | reference; opposite `DomainRelationship.source` | Lists relationships in which this concept is the source. |
| `incomingRelationships` | `DomainRelationship` `0..*` | reference; opposite `DomainRelationship.target` | Lists relationships in which this concept is the target. |

EVL warns when neither `glossaryDefinition` nor inherited `description` explains a domain concept. A relationship can be navigated from either end, but the `DomainRelationship` object remains owned by the CIM root.

## `DomainEntity`

`DomainEntity` represents a business object that remains recognizable as the same object across time. Identity, lifecycle states, invariants, attributes, and ownership are modeled explicitly because they affect commands, aggregates, events, and later data design.

Direct supertype: `DomainConcept`.

### Declared attributes

| Attribute              | Type and multiplicity           | Meaning                                                                                                                                | Example                                                                                                 |
| ---------------------- | ------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------- |
| `identityDescription`  | `String` `0..1`                 | Explains what makes two observations refer to the same business entity.                                                                | `The application number identifies an application throughout review and award.`                         |
| `lifecycleDescription` | `String` `0..1`                 | Summarizes the meaningful progression of the entity from creation to completion or termination.                                        | `An application moves from draft to submitted, under review, approved or rejected, and finally closed.` |
| `auditRelevant`        | `Boolean` `0..1`                | States whether changes to the entity or its important decisions require an audit trail.                                                | `true`                                                                                                  |
| `identityStrategy`     | `cimtypes.IdentityStrategy` `1` | Classifies the identity approach. The value is required even when it is `UNKNOWN`, because identity is a deliberate modeling question. | `NATURAL_KEY`                                                                                           |

### Relationships

| Feature                    | Target and multiplicity            | Kind                                                     | Meaning                                                                                                                                         |
| -------------------------- | ---------------------------------- | -------------------------------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------- |
| `lifecycleStates`          | `LifecycleStateDefinition` `0..*`  | containment                                              | Owns the states that describe the entity's business lifecycle.                                                                                  |
| `invariants`               | `BusinessInvariant` `0..*`         | containment                                              | Owns rules that must remain true for this entity.                                                                                               |
| `identityAttributes`       | `InformationItem` `1..*`           | reference                                                | Identifies the information items that together express the entity's identity.                                                                   |
| `primaryIdentityAttribute` | `InformationItem` `1`              | reference                                                | Selects the principal identity item. EVL requires it to be one of the entity's attributes and one of its identity attributes.                   |
| `attributes`               | `InformationItem` `0..*`           | reference                                                | Lists information carried by the entity. These items are independently owned by the CIM root and may be reused in commands, queries, or events. |
| `owningCapability`         | `cimorg.BusinessCapability` `0..1` | reference; opposite `BusinessCapability.managesEntities` | Identifies the capability responsible for the entity's lifecycle and invariants.                                                                |

EVL checks the identity collections when a primary identity is present, warns when no capability or business owner is given, and requires exactly one initial lifecycle state plus at least one terminal state when lifecycle states are modeled. `Entity2Schema` creates a PIM contract schema; aggregate and access-pattern rules use the entity and its identity attributes when resolving data structures.

## `ValueObject`

`ValueObject` represents a concept whose significance is defined by its value. It normally has no separate lifecycle or identity. The `equalityAttributes` reference makes the equality decision explicit, which prevents a later implementation from treating every field as significant by accident.

Direct supertype: `DomainConcept`.

### Declared attributes

| Attribute   | Type and multiplicity                   | Meaning                                                                                                                                                                      | Example   |
| ----------- | --------------------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | --------- |
| `valueType` | `cimtypes.PrimitiveBusinessType` `0..1` | Gives the value object a simple business type when it is represented as one value. A structured value can instead use its `attributes`.                                      | `ADDRESS` |
| `immutable` | `Boolean` `0..1`                        | States whether the value is treated as stable after creation. EVL warns when a value object is not marked immutable because mutable identity-free data is usually ambiguous. | `true`    |

### Relationships

| Feature              | Target and multiplicity  | Kind      | Meaning                                                                                               |
| -------------------- | ------------------------ | --------- | ----------------------------------------------------------------------------------------------------- |
| `attributes`         | `InformationItem` `0..*` | reference | Lists the information composing a structured value.                                                   |
| `equalityAttributes` | `InformationItem` `0..*` | reference | Selects the attributes that determine whether two value objects are equal. EVL requires at least one. |

`ValueObject2Schema` turns the value definition into a PIM schema. It preserves the distinction between a descriptive business value and an entity with independent identity.

## `DomainRelationship`

`DomainRelationship` makes a connection between two domain concepts explicit. It carries role names, end multiplicities, navigability, ownership, and a controlled relationship kind. The separate relationship object is important when the link itself has meaning, such as `Applicant submits Application` or `Application contains Evidence`.

Direct supertype: `kernel.SemanticRelationship`.

### Declared attributes

| Attribute             | Type and multiplicity                    | Meaning                                                                                                                                                                    | Example                 |
| --------------------- | ---------------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ----------------------- |
| `sourceRole`          | `String` `0..1`                          | Names the role played by the source concept from the relationship's perspective.                                                                                           | `submitting applicant`  |
| `targetRole`          | `String` `0..1`                          | Names the role played by the target concept.                                                                                                                               | `submitted application` |
| `ownership`           | `Boolean` `0..1`                         | States whether the relationship carries a business ownership implication. EVL checks that an ownership-marked relationship uses an ownership-compatible relationship type. | `false`                 |
| `navigableFromSource` | `Boolean` `0..1`                         | States whether the source concept needs to find or use the target through this relationship.                                                                               | `true`                  |
| `navigableFromTarget` | `Boolean` `0..1`                         | States whether the target concept needs to find or use the source.                                                                                                         | `false`                 |
| `relationshipType`    | `cimtypes.DomainRelationshipType` `0..1` | Classifies the business interpretation of the link, such as association, composition, dependency, or ownership.                                                            | `ASSOCIATION`           |

### Relationships

| Feature              | Target and multiplicity   | Kind and opposite                                         | Meaning                                                                                    |
| -------------------- | ------------------------- | --------------------------------------------------------- | ------------------------------------------------------------------------------------------ |
| `sourceMultiplicity` | `kernel.Multiplicity` `1` | containment                                               | Owns the lower and upper bounds for how many target concepts can be reached from a source. |
| `targetMultiplicity` | `kernel.Multiplicity` `1` | containment                                               | Owns the lower and upper bounds for the reverse end.                                       |
| `source`             | `DomainConcept` `1`       | reference; opposite `DomainConcept.outgoingRelationships` | Identifies the concept from which the relationship is read.                                |
| `target`             | `DomainConcept` `1`       | reference; opposite `DomainConcept.incomingRelationships` | Identifies the concept reached by the relationship.                                        |

EVL requires distinct source and target concepts, and warns when roles or multiplicity bounds are absent. If `ownership=true`, the relationship type must be `COMPOSITION`, `AGGREGATION`, or `OWNERSHIP`. `DomainRelationship2SchemaReference` uses the relationship and its target multiplicity when creating schema references and data fields in PIM.

## `AggregateCandidate`

`AggregateCandidate` proposes a boundary within which related entity changes and invariants should be reasoned about together. It is a candidate because a CIM expresses business consistency intent before PIM decides how that boundary is implemented. The root entity, member set, consistency expectation, conflict policy, handled commands, and emitted events together explain why the candidate exists.

Direct supertype: `DomainConcept`.

### Declared attributes

| Attribute                      | Type and multiplicity                    | Meaning                                                                                                                                   | Example                                                                                 |
| ------------------------------ | ---------------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------- | --------------------------------------------------------------------------------------- |
| `consistencyBoundaryRationale` | `String` `0..1`                          | Explains the invariant or business decision that requires the proposed members to be considered together.                                 | `Approval and award state must agree when an award is issued.`                          |
| `conflictResolutionPolicy`     | `String` `0..1`                          | Describes how conflicting updates or stale views are resolved when strong consistency is not required.                                    | `A regional officer reconciles conflicting eligibility evidence before final approval.` |
| `idempotencyBusinessKey`       | `String` `0..1`                          | Provides an aggregate-level business key for recognizing duplicate commands when command-level keys are absent.                           | `applicationNumber + decisionType`                                                      |
| `strongConsistencyRequired`    | `Boolean` `0..1`                         | States whether the candidate needs a strong consistency boundary. EVL then requires a rationale and a compatible consistency expectation. | `true`                                                                                  |
| `consistencyExpectation`       | `cimtypes.ConsistencyExpectation` `0..1` | States what level of agreement the business accepts, including eventual consistency or manual reconciliation.                             | `MULTI_ENTITY_STRONG_CONSISTENCY`                                                       |

### Relationships

| Feature           | Target and multiplicity                 | Kind        | Meaning                                                                                               |
| ----------------- | --------------------------------------- | ----------- | ----------------------------------------------------------------------------------------------------- |
| `invariants`      | `BusinessInvariant` `0..*`              | containment | Owns invariants that apply to the aggregate boundary.                                                 |
| `root`            | `DomainEntity` `1`                      | reference   | Selects the entity through which the candidate is accessed and named.                                 |
| `members`         | `DomainEntity` `1..*`                   | reference   | Lists the entities inside the proposed boundary. EVL requires the root to be included.                |
| `context`         | `cimorg.BoundedContextCandidate` `0..1` | reference   | Associates the aggregate with a proposed language and ownership boundary.                             |
| `handledCommands` | `cimbehavior.Command` `0..*`            | reference   | Identifies commands that change the aggregate. Duplicate-prone commands trigger idempotency guidance. |
| `emittedEvents`   | `cimbehavior.BusinessEvent` `0..*`      | reference   | Identifies facts emitted when aggregate behavior changes business state.                              |

The domain-data EVL rules check root membership, strong-consistency reasoning, conflict policy for eventual or manual consistency, and idempotency for duplicate-prone commands. `Aggregate2DataStore` refines a candidate into PIM data-store and data-model concepts, while command and event transformations resolve their functions and flows against the aggregate.

## `LifecycleStateDefinition`

`LifecycleStateDefinition` names one meaningful state of a domain entity. It is a business state, not a technical status column. Entry and exit conditions make the transitions into and out of the state reviewable even though the actual transition graph is represented by the entity's behavior and process model.

### Declared attributes

| Attribute        | Type and multiplicity | Meaning                                                                                                                                  | Example                                       |
| ---------------- | --------------------- | ---------------------------------------------------------------------------------------------------------------------------------------- | --------------------------------------------- |
| `stateName`      | `String` `1`          | Names the state in business vocabulary.                                                                                                  | `Under review`                                |
| `initial`        | `Boolean` `0..1`      | Marks whether this is the state in which the entity begins. An entity with lifecycle states must have exactly one initial state.         | `false`                                       |
| `terminal`       | `Boolean` `0..1`      | Marks whether the state represents a terminal point for this lifecycle. At least one terminal state is required when states are modeled. | `false`                                       |
| `entryCondition` | `String` `0..1`       | States what must be true for the entity to enter the state.                                                                              | `All mandatory evidence has been received.`   |
| `exitCondition`  | `String` `0..1`       | States what must be true before the entity can leave the state.                                                                          | `A reviewer records an eligibility decision.` |

The state definitions are contained by `DomainEntity` and are used as business vocabulary by processes, commands, policies, and generated data models.

## `BusinessInvariant`

`BusinessInvariant` states a rule that must remain true for one or more domain concepts. It can be reviewed in natural language and optionally represented by a typed expression for validation or later implementation.

### Declared attributes

| Attribute                  | Type and multiplicity              | Meaning                                                                            | Example                                                                            |
| -------------------------- | ---------------------------------- | ---------------------------------------------------------------------------------- | ---------------------------------------------------------------------------------- |
| `naturalLanguageStatement` | `String` `0..1`                    | Gives the invariant in business language.                                          | `An approved application must have an eligible applicant and a recorded decision.` |
| `expression`               | `String` `0..1`                    | Stores a textual form of the rule when the modeler does not use `expressionModel`. | `application.status = APPROVED implies application.decision.isDefined()`           |
| `violationSeverity`        | `kernel.Severity` `0..1`           | States the consequence of violating the invariant.                                 | `ERROR`                                                                            |
| `expressionLanguage`       | `kernel.ExpressionLanguage` `0..1` | Identifies the language of `expression`.                                           | `OCL`                                                                              |

### Relationships

| Feature               | Target and multiplicity    | Kind        | Meaning                                                                                               |
| --------------------- | -------------------------- | ----------- | ----------------------------------------------------------------------------------------------------- |
| `expressionModel`     | `kernel.Expression` `0..1` | containment | Owns a structured expression with language, phase, and references.                                    |
| `constrainedConcepts` | `DomainConcept` `0..*`     | reference   | Identifies the entities, value objects, aggregates, or other concepts to which the invariant applies. |

EVL requires a natural-language statement, expression, or expression model and warns when no concept is linked. Aggregate invariants are carried into PIM schema constraints by `Entity2Schema` and related data transformation logic.

## `InformationItem`

`InformationItem` is the reusable description of a business datum. It can be an entity attribute, command input, query output, event payload item, decision input, external exchange, or nested member of a structured object. The class combines type, cardinality, value constraints, derivation, provenance, search/reporting use, retention relevance, and protection references.

### Declared attributes

| Attribute                | Type and multiplicity                | Meaning                                                                                                                                                                                    | Example                                               |
| ------------------------ | ------------------------------------ | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ | ----------------------------------------------------- |
| `businessName`           | `String` `0..1`                      | Gives the information its business-facing label. EVL accepts either this or inherited `name`.                                                                                              | `Applicant email address`                             |
| `required`               | `Boolean` `0..1`                     | States whether the datum is required in the context where it is used. Its exact use is interpreted by the containing command, event, schema, or entity.                                    | `true`                                                |
| `collection`             | `Boolean` `0..1`                     | States whether the datum can contain repeated values. A collection must have a `cardinality` object.                                                                                       | `false`                                               |
| `example`                | `String` `0..1`                      | Provides a representative value without making that value a default or a constraint.                                                                                                       | `applicant@example.org`                               |
| `allowedValues`          | `String` `0..*`                      | Lists permitted values when the vocabulary is small and local to the information item.                                                                                                     | `Pending`, `Approved`, `Rejected`                     |
| `formatHint`             | `String` `0..1`                      | Describes a business-relevant representation hint that is not captured by the primitive type.                                                                                              | `ISO 8601 date`                                       |
| `minValue`               | `String` `0..1`                      | Gives a lower value bound in a text form that can retain domain units or decimal precision.                                                                                                | `0`                                                   |
| `maxValue`               | `String` `0..1`                      | Gives an upper value bound.                                                                                                                                                                | `50000`                                               |
| `minLength`              | `Integer` `0..1`                     | Sets the minimum length for textual or collection content.                                                                                                                                 | `1`                                                   |
| `maxLength`              | `Integer` `0..1`                     | Sets the maximum length. EVL checks that it is not lower than `minLength`.                                                                                                                 | `200`                                                 |
| `pattern`                | `String` `0..1`                      | Gives a pattern for values whose form has a business validation meaning.                                                                                                                   | `^[A-Z]{2}-[0-9]{6}$`                                 |
| `unit`                   | `String` `0..1`                      | States the unit attached to a numeric, temporal, physical, or monetary value.                                                                                                              | `EUR`                                                 |
| `businessValidationRule` | `String` `0..1`                      | States a business validation rule that is too specific for the primitive type alone.                                                                                                       | `The date must fall within the grant program period.` |
| `derived`                | `Boolean` `0..1`                     | Marks the value as derived from other information. In Emfatic this feature is declared `~derived`; it is a modeled derived-value flag, not an instruction for the documentation generator. | `true`                                                |
| `derivationRule`         | `String` `0..1`                      | Explains how a derived value is calculated or which source items and policies determine it. EVL requires it when `derived=true`.                                                           | `Sum of approved eligible expense items.`             |
| `sourceOfTruth`          | `String` `0..1`                      | Identifies the authoritative business source for the datum, especially when it is used for search or reporting.                                                                            | `Approved grant decision record`                      |
| `externallyShared`       | `Boolean` `0..1`                     | States whether the information is shared outside the modeled organizational boundary.                                                                                                      | `true`                                                |
| `auditRelevant`          | `Boolean` `0..1`                     | States whether changes or access to the datum matter for audit evidence.                                                                                                                   | `true`                                                |
| `searchRelevant`         | `Boolean` `0..1`                     | Marks information used to find or filter business objects. EVL asks for a source of truth when it is true.                                                                                 | `true`                                                |
| `reportingRelevant`      | `Boolean` `0..1`                     | Marks information used in reports or management views.                                                                                                                                     | `true`                                                |
| `retentionRelevant`      | `Boolean` `0..1`                     | States whether retaining the value matters for historical, legal, or operational reasons.                                                                                                  | `true`                                                |
| `type`                   | `cimtypes.PrimitiveBusinessType` `1` | Gives the business value a type before platform schema generation. `OBJECT` and `LIST` are required when nested `subItems` are used.                                                       | `EMAIL`                                               |

### Relationships

| Feature                 | Target and multiplicity              | Kind and opposite                                        | Meaning                                                                                                                                         |
| ----------------------- | ------------------------------------ | -------------------------------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------- |
| `cardinality`           | `kernel.Multiplicity` `0..1`         | containment                                              | Owns lower and upper bounds for a collection item. It is required by EVL when `collection=true`.                                                |
| `subItems`              | `InformationItem` `0..*`             | containment; opposite `InformationItem.parent`           | Owns nested information items for an object or list structure. Child names must be unique within the parent.                                    |
| `parent`                | `InformationItem` `0..1`             | read-only reference; opposite `InformationItem.subItems` | Exposes the containing information item for a nested child.                                                                                     |
| `classification`        | `DataClassification` `0..1`          | reference                                                | Attaches the information's confidentiality, regulatory, and identifiability meaning.                                                            |
| `privacyConstraints`    | `cimgov.PrivacyConstraint` `0..*`    | reference; opposite `PrivacyConstraint.dataItems`        | Links privacy obligations governing this information. Personal, sensitive, financial, health, and regulated information must have at least one. |
| `complianceConstraints` | `cimgov.ComplianceConstraint` `0..*` | reference                                                | Links controls and evidence obligations that apply to the information.                                                                          |

EVL checks type, structured-item type, child-name uniqueness, privacy coverage, collection cardinality, derivation explanation, source of truth for search/report use, and length bounds. `InformationItem2Schema` creates PIM schema fields. Privacy, classification, query, event, and data-store transformations reuse the same item so that protection and business meaning are not rediscovered separately at each use site.

## `DataClassification`

`DataClassification` records how information should be treated from a confidentiality, privacy, regulatory, and access-audit perspective. It is a business classification and protection expectation. It does not itself choose encryption technology, masking software, or a provider resource.

### Declared attributes

| Attribute                 | Type and multiplicity             | Meaning                                                                                                                                      | Example                                                                      |
| ------------------------- | --------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------- | ---------------------------------------------------------------------------- |
| `confidentialityLevel`    | `String` `0..1`                   | Describes the confidentiality expectation in the organization's vocabulary.                                                                  | `Restricted`                                                                 |
| `regulatoryCategory`      | `String` `0..1`                   | Names the regulatory category when the information is financial, health, regulated, or otherwise controlled.                                 | `Emergency assistance personal data`                                         |
| `encryptionExpected`      | `Boolean` `0..1`                  | States whether protection through encryption is expected for the classified information. Strong-protection kinds must set it true under EVL. | `true`                                                                       |
| `maskingExpected`         | `Boolean` `0..1`                  | States whether views or uses should mask part of the value.                                                                                  | `true`                                                                       |
| `minimizationRequired`    | `Boolean` `0..1`                  | States whether collection or use should be limited to what the business purpose needs.                                                       | `true`                                                                       |
| `consentRequired`         | `Boolean` `0..1`                  | Records whether consent is expected for the information's processing. A privacy constraint using `CONSENT` must also set this true.          | `true`                                                                       |
| `auditAccessRequired`     | `Boolean` `0..1`                  | States whether access to the information must be audited. Strong-protection kinds require this under EVL.                                    | `true`                                                                       |
| `deletionRightApplies`    | `Boolean` `0..1`                  | Records whether a deletion right applies to the information.                                                                                 | `true`                                                                       |
| `classificationRationale` | `String` `0..1`                   | Explains the evidence or business reasoning behind the classification. EVL asks for it for regulated, financial, and health data.            | `The item identifies a person and is retained for a statutory audit period.` |
| `kind`                    | `cimtypes.DataKind` `1`           | Gives the controlled protection category.                                                                                                    | `SENSITIVE_PERSONAL`                                                         |
| `identifiability`         | `cimtypes.Identifiability` `0..1` | States how directly the information can identify a person. Personal kinds require a personal identifiability value under EVL.                | `DIRECTLY_IDENTIFYING`                                                       |

The classification has no direct containment of information. It is independently contained by the CIM root and attached through `InformationItem.classification`, so the same classification object can be reused where the model intends that shared meaning. EVL requires `kind`, strong protection for sensitive categories, personal identifiability for personal kinds, and a category/rationale for regulated, financial, or health data. `DataClassification2DataProtectionPolicy` and privacy attachment rules refine it into PIM protection policies.
