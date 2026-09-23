# CIM enumerations

Source: `mde/metamodels/cim/cim-types.emf`.

The enumerations in this file are closed vocabularies. They keep important distinctions explicit and make EVL checks and ETL mappings predictable. A value should be selected because it describes the business meaning of the modeled element. It should not be used as a substitute for the explanation fields inherited from the shared kernel.

## `RequirementType`

Classifies the kind of obligation recorded by a `Requirement`.

| Literal           | Meaning                                                                                    |
| ----------------- | ------------------------------------------------------------------------------------------ |
| `FUNCTIONAL`      | A capability or behavior the business expects the solution to provide.                     |
| `BUSINESS_RULE`   | A rule that governs a business decision, state change, or permitted action.                |
| `QUALITY`         | A quality expectation such as performance, availability, or maintainability.               |
| `SECURITY`        | An expectation about protection, access, authentication, authorization, or accountability. |
| `PRIVACY`         | An expectation about personal or otherwise protected information and its processing.       |
| `COMPLIANCE`      | An obligation arising from a regulation, standard, or formal control.                      |
| `AUDIT`           | A requirement to retain evidence or make an activity reviewable.                           |
| `REPORTING`       | A requirement to provide a report, measurement, or business view.                          |
| `DATA_GOVERNANCE` | An obligation about data ownership, quality, classification, retention, or stewardship.    |

## `RequirementSourceType`

Records where a requirement entered the modeling conversation.

| Literal           | Meaning                                                                                                       |
| ----------------- | ------------------------------------------------------------------------------------------------------------- |
| `STAKEHOLDER`     | The requirement was stated by a stakeholder or represented business interest.                                 |
| `REGULATION`      | The requirement was derived from a law, regulation, or regulator expectation.                                 |
| `POLICY_DOCUMENT` | The requirement comes from an existing organizational policy document.                                        |
| `WORKSHOP`        | The requirement was identified during a workshop or facilitated modeling activity.                            |
| `EXISTING_SYSTEM` | The current system or its observed behavior is the source.                                                    |
| `ASSUMPTION`      | The requirement is based on an explicit assumption that still needs confirmation.                             |
| `AI_ASSISTANT`    | The requirement was proposed through an AI-assisted modeling interaction and remains subject to human review. |

## `RequirementRelationshipKind`

Explains how one `RequirementRelationship` connects two requirements.

| Literal          | Meaning                                                                     |
| ---------------- | --------------------------------------------------------------------------- |
| `DEPENDS_ON`     | The source requirement relies on the target being satisfied.                |
| `CONFLICTS_WITH` | The two requirements cannot both be satisfied as currently stated.          |
| `REFINES`        | The source gives a more specific formulation of the target.                 |
| `DUPLICATES`     | The source repeats the intent already represented by the target.            |
| `DERIVES_FROM`   | The source requirement was derived from the target's statement or evidence. |

## `ActorType`

Identifies what kind of participant an `Actor` is at the business boundary.

| Literal                 | Meaning                                                             |
| ----------------------- | ------------------------------------------------------------------- |
| `HUMAN`                 | A person who performs or initiates business work.                   |
| `ORGANIZATION`          | An organization treated as a participant in a business interaction. |
| `DEPARTMENT`            | An internal department or organizational unit with a business role. |
| `EXTERNAL_ORGANIZATION` | An organization outside the modeled ownership boundary.             |
| `EXTERNAL_SYSTEM`       | A system outside the model that exchanges information or events.    |
| `TIME`                  | A temporal trigger or time-based participant.                       |
| `REGULATOR`             | A regulatory authority or oversight body.                           |

## `TrustLevel`

Expresses the trust assumption attached to an actor. It is a business-level input to later security refinement, not a cloud identity configuration.

| Literal              | Meaning                                                                                             |
| -------------------- | --------------------------------------------------------------------------------------------------- |
| `TRUSTED_INTERNAL`   | The actor is inside the organization and is treated as trusted for the stated business interaction. |
| `PARTIALLY_TRUSTED`  | The actor is known, but trust is limited to defined interactions or permissions.                    |
| `UNTRUSTED_EXTERNAL` | The actor is external and must be treated as untrusted at the boundary.                             |
| `REGULATED_EXTERNAL` | The actor is external and subject to regulatory or contractual controls that affect trust.          |

## `CapabilityCriticality`

States how essential a `BusinessCapability` or related process is to the business outcome.

| Literal            | Meaning                                                                                        |
| ------------------ | ---------------------------------------------------------------------------------------------- |
| `SUPPORTING`       | The capability supports the domain but failure does not directly threaten its central outcome. |
| `IMPORTANT`        | The capability materially contributes to the outcome and deserves deliberate design attention. |
| `CORE`             | The capability is central to the modeled business purpose.                                     |
| `MISSION_CRITICAL` | The capability is indispensable to the organization's mission or a production gate.            |

## `DomainRelationshipType`

Describes the business interpretation of a `DomainRelationship`.

| Literal          | Meaning                                                                                 |
| ---------------- | --------------------------------------------------------------------------------------- |
| `ASSOCIATION`    | Two concepts are connected for a business reason without an ownership claim.            |
| `COMPOSITION`    | The source concept owns the existence of the target part in the modeled domain.         |
| `AGGREGATION`    | The source groups the target concept while the target can have an independent identity. |
| `GENERALIZATION` | The source and target express a specialization relationship.                            |
| `DEPENDENCY`     | The source relies on the target for a business purpose.                                 |
| `OWNERSHIP`      | The source is responsible for the target's business stewardship or lifecycle.           |

## `PrimitiveBusinessType`

Gives an `InformationItem` or `ValueObject` a business-facing value shape before a platform type is chosen.

| Literal       | Meaning                                                                             |
| ------------- | ----------------------------------------------------------------------------------- |
| `TEXT`        | Human-readable textual content.                                                     |
| `NUMBER`      | A numeric value where the domain does not require a narrower numeric distinction.   |
| `INTEGER`     | A whole-number quantity.                                                            |
| `DECIMAL`     | A number whose fractional precision matters.                                        |
| `BOOLEAN`     | A two-state business fact or decision.                                              |
| `DATE`        | A calendar date without a time-of-day meaning.                                      |
| `TIME`        | A time-of-day value.                                                                |
| `DATETIME`    | A date and time used together as a business value.                                  |
| `MONEY`       | A monetary amount, normally interpreted together with its currency.                 |
| `EMAIL`       | An email address used as business information.                                      |
| `PHONE`       | A telephone number used as business information.                                    |
| `ADDRESS`     | An address or location description.                                                 |
| `IDENTIFIER`  | A value used to identify another business object.                                   |
| `ENUMERATION` | A value selected from a separately defined finite vocabulary.                       |
| `OBJECT`      | A structured value with nested `subItems`.                                          |
| `LIST`        | A collection of repeated values or structured items.                                |
| `DOCUMENT`    | A document-like business artifact.                                                  |
| `BINARY`      | Opaque binary content whose internal representation is outside the CIM type system. |

## `IdentityStrategy`

Explains how a `DomainEntity` is recognized as the same business object over time.

| Literal                 | Meaning                                                            |
| ----------------------- | ------------------------------------------------------------------ |
| `NATURAL_KEY`           | A single business value already identifies the entity.             |
| `COMPOSITE_NATURAL_KEY` | Several business values together form the identity.                |
| `SURROGATE_KEY`         | The entity uses an identity value with no direct business meaning. |
| `EXTERNAL_REFERENCE`    | The identity is assigned or maintained by another system.          |
| `UNKNOWN`               | The identity decision is still unresolved.                         |

## `DataKind`

Classifies the protection or regulatory nature of an `InformationItem` through `DataClassification`.

| Literal                 | Meaning                                                                        |
| ----------------------- | ------------------------------------------------------------------------------ |
| `PUBLIC`                | Information intended for public disclosure.                                    |
| `INTERNAL`              | Information intended for use inside the organization.                          |
| `CONFIDENTIAL`          | Information whose disclosure is restricted by business sensitivity.            |
| `PERSONAL`              | Information relating to an identifiable person.                                |
| `SENSITIVE_PERSONAL`    | Personal information requiring stronger protection because of its sensitivity. |
| `FINANCIAL`             | Financial information with corresponding protection or control expectations.   |
| `HEALTH`                | Health-related information.                                                    |
| `AUTHENTICATION_SECRET` | A secret used to establish or protect authentication.                          |
| `REGULATED`             | Information subject to a regulatory control or category.                       |

## `Identifiability`

Describes how directly an information item can be connected to a person.

| Literal                  | Meaning                                                                              |
| ------------------------ | ------------------------------------------------------------------------------------ |
| `NON_PERSONAL`           | The information does not relate to a person in the modeled use.                      |
| `PSEUDONYMOUS`           | A substitute identifier is used, while a separate means of re-identification exists. |
| `DIRECTLY_IDENTIFYING`   | The item identifies a person directly.                                               |
| `INDIRECTLY_IDENTIFYING` | The item can identify a person when combined with other information.                 |
| `ANONYMOUS`              | The information is intended to have no practicable link back to a person.            |

## `CommandType`

Identifies the business source or purpose of a `Command`.

| Literal                   | Meaning                                                      |
| ------------------------- | ------------------------------------------------------------ |
| `USER_INTENT`             | A person explicitly requests the state change.               |
| `BUSINESS_SYSTEM_INTENT`  | An internal business behavior initiates the state change.    |
| `EXTERNAL_SYSTEM_INTENT`  | An external system initiates the request.                    |
| `POLICY_TRIGGERED_INTENT` | A policy produces the command as a rule outcome.             |
| `CORRECTION`              | The command corrects an earlier business record or decision. |
| `CANCELLATION`            | The command cancels or withdraws a previous business intent. |

## `QueryType`

Describes the business shape of a `Query`.

| Literal     | Meaning                                                                       |
| ----------- | ----------------------------------------------------------------------------- |
| `LOOKUP`    | Retrieves a particular object or result by a known identity.                  |
| `LIST`      | Returns a collection, usually with pagination or ordering concerns.           |
| `SEARCH`    | Finds results using search criteria that may be broader than identity lookup. |
| `REPORT`    | Produces a view intended for reporting or management use.                     |
| `STATUS`    | Answers the current status of an object or process.                           |
| `ANALYTICS` | Produces an analytical result derived from business information.              |

## `FreshnessNeed`

States how current the result of a query needs to be.

| Literal                 | Meaning                                                                           |
| ----------------------- | --------------------------------------------------------------------------------- |
| `REAL_TIME`             | The result must reflect the business state at the time of the request.            |
| `NEAR_REAL_TIME`        | A small delay is acceptable, but the result should follow current events closely. |
| `EVENTUALLY_CONSISTENT` | The business accepts a delay before all relevant views agree.                     |
| `PERIODIC`              | The result can be refreshed according to a recurring schedule.                    |
| `HISTORICAL`            | The query intentionally examines a past state or time period.                     |

## `EventTimeSemantics`

Identifies which time a `BusinessEvent` is describing.

| Literal            | Meaning                                                    |
| ------------------ | ---------------------------------------------------------- |
| `BUSINESS_TIME`    | The time when the business fact occurred.                  |
| `OBSERVATION_TIME` | The time when a participant observed the fact.             |
| `RECORDING_TIME`   | The time when the fact was recorded in a system or record. |

## `PolicyType`

Classifies the role of a `Policy` in business behavior.

| Literal         | Meaning                                                                               |
| --------------- | ------------------------------------------------------------------------------------- |
| `REACTION`      | Responds to an event or condition by producing a business outcome.                    |
| `GUARD`         | Prevents or permits a command according to a rule.                                    |
| `DERIVATION`    | Derives a fact, value, or decision from existing information.                         |
| `AUTHORIZATION` | Decides whether an actor or behavior is permitted.                                    |
| `VALIDATION`    | Tests whether a business condition is satisfied.                                      |
| `COMPLIANCE`    | Enforces or records an obligation imposed by a regulation or control.                 |
| `ESCALATION`    | Routes a situation to a higher responsibility level.                                  |
| `COMPENSATION`  | Describes the business action used to compensate for an exception or partial failure. |

## `ProcessKind`

Describes the overall character of a `BusinessProcess`.

| Literal                      | Meaning                                                                                    |
| ---------------------------- | ------------------------------------------------------------------------------------------ |
| `STRAIGHT_THROUGH`           | The process can proceed without human intervention in its normal path.                     |
| `HUMAN_INVOLVED`             | A person performs or approves one or more steps.                                           |
| `LONG_RUNNING`               | The process can remain active over an extended business period.                            |
| `CASE_MANAGEMENT`            | The process organizes work around a case that may follow a variable path.                  |
| `SAGA_LIKE_BUSINESS_PROCESS` | The process coordinates several activities with compensating business actions when needed. |
| `REPORTING_PROCESS`          | The process primarily prepares or publishes a report.                                      |

## `StepKind`

Identifies the business meaning of a `ProcessStep`. The concrete step subclass carries details for several of these values.

| Literal                | Meaning                                                   |
| ---------------------- | --------------------------------------------------------- |
| `COMMAND`              | Invoke a state-changing business intent.                  |
| `QUERY`                | Obtain business information.                              |
| `EVENT`                | Observe or publish a business fact.                       |
| `DECISION`             | Evaluate a condition or decision table and choose a path. |
| `POLICY`               | Apply a business rule within the process.                 |
| `HUMAN_TASK`           | Assign work that requires human completion or evidence.   |
| `EXTERNAL_INTERACTION` | Exchange information with an external system.             |
| `WAIT`                 | Pause until a time, event, or other business condition.   |
| `START`                | Mark the beginning of the process.                        |
| `END`                  | Mark a terminal process point.                            |

## `QualityType`

Identifies the quality concern represented by a `NonFunctionalRequirement` or `QualityScenario`.

| Literal           | Meaning                                                             |
| ----------------- | ------------------------------------------------------------------- |
| `PERFORMANCE`     | Capacity or execution performance.                                  |
| `LATENCY`         | Time taken to produce a response or outcome.                        |
| `AVAILABILITY`    | The expected ability to provide the business function when needed.  |
| `RELIABILITY`     | The expected consistency and dependability of operation.            |
| `SCALABILITY`     | The ability to accommodate changing load or scope.                  |
| `SECURITY`        | Protection against unauthorized access or misuse.                   |
| `PRIVACY`         | Responsible handling of personal or protected information.          |
| `AUDITABILITY`    | The ability to provide evidence of relevant activity and decisions. |
| `MAINTAINABILITY` | The ease of changing and sustaining the solution.                   |
| `OPERABILITY`     | The ability to operate, diagnose, and manage the solution.          |
| `COST`            | A limit or target for financial consumption.                        |
| `DATA_QUALITY`    | Correctness, completeness, timeliness, or related data properties.  |
| `COMPLIANCE`      | Conformance with an external or internal obligation.                |
| `USABILITY`       | The ease with which intended users can perform the business task.   |

## `LegalBasis`

States the legal basis recorded by a `PrivacyConstraint` for processing covered information.

| Literal               | Meaning                                                                                                      |
| --------------------- | ------------------------------------------------------------------------------------------------------------ |
| `CONSENT`             | Processing is based on the data subject's consent.                                                           |
| `CONTRACT`            | Processing is necessary to perform or prepare a contract.                                                    |
| `LEGAL_OBLIGATION`    | Processing is required by a legal obligation.                                                                |
| `VITAL_INTERESTS`     | Processing is justified by protecting vital interests.                                                       |
| `PUBLIC_TASK`         | Processing is performed for a public task or authority.                                                      |
| `LEGITIMATE_INTEREST` | Processing is based on a legitimate interest with the required balancing.                                    |
| `NOT_APPLICABLE`      | The privacy constraint does not use a legal-basis category in the modeled case.                              |
| `UNKNOWN`             | The basis has not yet been determined. EVL rejects this value when the constraint already covers data items. |

## `InteractionExpectation`

Describes the response relationship expected by a person or participating system.

| Literal                       | Meaning                                                                    |
| ----------------------------- | -------------------------------------------------------------------------- |
| `IMMEDIATE_RESPONSE_EXPECTED` | The requester expects an immediate business response.                      |
| `RESPONSE_CAN_BE_DELAYED`     | The business accepts a later response.                                     |
| `NOTIFICATION_EXPECTED`       | The important result is a notification rather than a synchronous response. |
| `BACK_OFFICE_PROCESSING`      | The work is performed as background or back-office activity.               |
| `HUMAN_REVIEW_REQUIRED`       | A person must review or decide before the interaction is complete.         |

## `ConsistencyExpectation`

States what consistency a proposed `AggregateCandidate` is expected to provide.

| Literal                            | Meaning                                                                             |
| ---------------------------------- | ----------------------------------------------------------------------------------- |
| `SINGLE_ENTITY`                    | The decision concerns one entity and does not require coordination across entities. |
| `MULTI_ENTITY_STRONG_CONSISTENCY`  | Several entities must agree within one strong consistency boundary.                 |
| `EVENTUAL_CONSISTENCY_ACCEPTABLE`  | Temporary disagreement between views or entities is acceptable.                     |
| `MANUAL_RECONCILIATION_ACCEPTABLE` | Differences may be resolved by an explicit human reconciliation activity.           |

## Use of optional enumeration values

Most enumeration-valued attributes are optional in the Ecore metamodel. Optionality records that the business decision may still be open; it does not mean that every value is equally safe to omit. EVL rules require particular values when a decision is needed for a meaningful check. For example, `DomainEntity.identityStrategy`, `InformationItem.type`, `DataClassification.kind`, and `Expression.language` are required by the metamodel, while a `Query` may defer `freshnessNeed` until its read expectation is known.
