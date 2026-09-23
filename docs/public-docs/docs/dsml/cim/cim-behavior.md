# CIM behavior

Source: `mde/metamodels/cim/cim-behavior.emf`.

The behavior module distinguishes an intention to change business state from a request to read information and from a fact that has already occurred. That distinction is central to CIM-to-PIM refinement. Commands can seed functions and, when appropriate, API routes. Queries can seed read functions, routes, and access patterns. Events can seed event types, channels, subscriptions, and integration flows. Errors and conditions explain the boundaries around these behaviors.

The classes inherit from `kernel.TraceableElement`. Their inherited source, rationale, review, lifecycle, and trace fields are described in the [shared kernel](../shared-kernel.md).

## `Command`

`Command` represents a business intention to change state. It is phrased as an action requested or triggered at the business boundary, while the events connected to it describe the facts that may result. A command does not mean an HTTP request or a function call. Those are later architectural interpretations.

### Declared attributes

All attributes declared directly by `Command` are optional in Ecore. EVL still requires particular combinations when the command is human-facing, crosses a trust boundary, can be duplicated, or lacks an outcome.

| Attribute                     | Type and multiplicity                    | Meaning                                                                                                                                                                                                 | Example                                                                    |
| ----------------------------- | ---------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | -------------------------------------------------------------------------- |
| `intent`                      | `String` `0..1`                          | States the business change the requester wants. It should describe the outcome in domain language and avoid transport verbs such as `POST`.                                                             | `Approve emergency grant application`                                      |
| `userInitiated`               | `Boolean` `0..1`                         | Records whether the command starts from a user's intent. Together with `commandType` and `interactionExpectation`, it helps refinement decide whether an externally callable route is appropriate.      | `true`                                                                     |
| `idempotencyBusinessKey`      | `String` `0..1`                          | Names the business value or combination of values that identifies repeated submissions of the same intent. It is meaningful when retrying the request must not perform the business change twice.       | `applicationNumber`                                                        |
| `duplicateSubmissionPossible` | `Boolean` `0..1`                         | States whether the same intent can arrive more than once. If true, EVL asks for an `idempotencyBusinessKey`.                                                                                            | `true`                                                                     |
| `auditRequired`               | `Boolean` `0..1`                         | Records that the command's initiation or result must be auditable. This is especially important for external or untrusted issuers and for regulated decisions.                                          | `true`                                                                     |
| `authorizationRequired`       | `Boolean` `0..1`                         | States that the business must decide whether the issuer is allowed to perform the command. A true value must be paired with `authorizationRule` when the command is subject to the relevant EVL checks. | `true`                                                                     |
| `authorizationRule`           | `String` `0..1`                          | Explains the business permission that must hold before the command is accepted. It is later available to PIM security and route transformations.                                                        | `The assigned case officer may approve only applications in their region.` |
| `commandType`                 | `cimtypes.CommandType` `0..1`            | Classifies where the command comes from or what business purpose it has, such as user intent, policy-triggered intent, correction, or cancellation.                                                     | `USER_INTENT`                                                              |
| `interactionExpectation`      | `cimtypes.InteractionExpectation` `0..1` | States whether the requester expects an immediate answer, delayed response, notification, back-office processing, or human review. It influences the shape of later interaction flows.                  | `RESPONSE_CAN_BE_DELAYED`                                                  |
| `priority`                    | `kernel.Priority` `0..1`                 | Records the relative business urgency of the command for review and planning. It does not define runtime scheduling.                                                                                    | `HIGH`                                                                     |

### Relationships

| Feature            | Target and multiplicity               | Kind and opposite                                         | Meaning                                                                                                                                                                                    |
| ------------------ | ------------------------------------- | --------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| `outcomes`         | `CommandOutcome` `0..*`               | containment; opposite `CommandOutcome.command`            | Keeps detailed success and rejection results under the command. The contained outcomes may provide event, error, and output links that should agree with the command's direct collections. |
| `issuedBy`         | `cimorg.Actor` `0..*`                 | reference; opposite `Actor.issuesCommands`                | Identifies the people, organizations, systems, or other actors that may issue the command. A human issuer triggers the explicit-authorization rule.                                        |
| `targetCapability` | `cimorg.BusinessCapability` `0..1`    | reference; opposite `BusinessCapability.containsCommands` | Places responsibility for the command under a capability. It is one of the possible business boundaries used when creating a PIM service.                                                  |
| `targetAggregate`  | `cimdomain.AggregateCandidate` `0..1` | reference                                                 | Identifies the consistency boundary whose invariants and identity rules govern the state change.                                                                                           |
| `input`            | `cimdomain.InformationItem` `0..*`    | reference                                                 | Lists the business information needed to decide or perform the command.                                                                                                                    |
| `preconditions`    | `Condition` `0..*`                    | reference                                                 | States facts that must already hold before the command can be accepted or executed.                                                                                                        |
| `expectedEvents`   | `BusinessEvent` `0..*`                | reference; opposite `BusinessEvent.expectedByCommands`    | Lists the business facts expected after a successful command. An event is a result fact, not another command.                                                                              |
| `rejectionEvents`  | `BusinessEvent` `0..*`                | reference; opposite `BusinessEvent.rejectedByCommands`    | Lists facts that explain a rejected or unsuccessful business attempt.                                                                                                                      |
| `possibleErrors`   | `BusinessError` `0..*`                | reference                                                 | Names the business failures that consumers and processes should understand.                                                                                                                |

### Validation and refinement

EVL requires a command to have an expected or rejection event and to have either a target capability or an aggregate. Human-issued commands must state an authorization decision. Commands issued by external or untrusted actors must require authorization and auditing. A duplicate-prone command receives a warning when no idempotency business key is supplied. If detailed `outcomes` are present, their emitted events and errors must match the direct event and error collections.

`Command2Function` creates a PIM function and function contract. `UserInitiatedCommand2ApiRoute` creates a route for user-facing or immediate-response commands and carries authorization information into the PIM. `AggregateCandidate` and `InformationItem` references are used to resolve data access and contract fields.

## `CommandOutcome`

`CommandOutcome` makes the result vocabulary of one command explicit. It allows a modeler to distinguish a successful business result from rejection or failure and to attach the facts and information associated with that result. It is contained by `Command` and is therefore part of the command's model subtree.

### Declared attributes

| Attribute           | Type and multiplicity | Meaning                                                                                                                                                                                                                       | Example                                                    |
| ------------------- | --------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ---------------------------------------------------------- |
| `success`           | `Boolean` `0..1`      | States whether this outcome represents a successful command result. The EVL helper treats true as the source of expected events and other values as rejection events, so the value should be explicit when outcomes are used. | `true`                                                     |
| `resultDescription` | `String` `0..1`       | Explains what the outcome means to the business. It gives reviewers a readable result even when the linked events and information items have short names.                                                                     | `The application is approved and queued for disbursement.` |

### Relationships

| Feature         | Target and multiplicity  | Kind and opposite                                | Meaning                                                                                                                                     |
| --------------- | ------------------------ | ------------------------------------------------ | ------------------------------------------------------------------------------------------------------------------------------------------- |
| `command`       | `Command` `1`            | read-only reference; opposite `Command.outcomes` | Identifies the owning command. It is derived from the containment opposite and cannot be used to attach an outcome to an unrelated command. |
| `emittedEvents` | `BusinessEvent` `0..*`   | reference                                        | Lists facts emitted by this particular result.                                                                                              |
| `errors`        | `BusinessError` `0..*`   | reference                                        | Lists business errors associated with this result, especially for a rejection outcome.                                                      |
| `output`        | `InformationItem` `0..*` | reference                                        | Lists information made available by this result.                                                                                            |

## `Query`

`Query` represents a business request for information. It describes what a consumer wants to know and how current, protected, filtered, sorted, or paginated the result needs to be. It does not prescribe SQL, a read model technology, or an API representation.

### Declared attributes

| Attribute               | Type and multiplicity           | Meaning                                                                                                                                                                  | Example                                                                   |
| ----------------------- | ------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------ | ------------------------------------------------------------------------- |
| `intent`                | `String` `0..1`                 | States the business question answered by the query.                                                                                                                      | `Find applications awaiting regional review`                              |
| `confidentiality`       | `String` `0..1`                 | Describes the sensitivity expected for the returned information. It prevents read behavior from being treated as harmless merely because it does not mutate state.       | `Restricted to case officers in the applicant's region`                   |
| `authorizationRequired` | `Boolean` `0..1`                | Records whether access to the result requires a business authorization decision. It must be true with an `authorizationRule` when personal data is returned or declared. | `true`                                                                    |
| `authorizationRule`     | `String` `0..1`                 | States who may read the result and under which business conditions.                                                                                                      | `Only the applicant and assigned case officers may view the application.` |
| `auditRequired`         | `Boolean` `0..1`                | Records whether access to the query result must be auditable.                                                                                                            | `true`                                                                    |
| `containsPersonalData`  | `Boolean` `0..1`                | Declares the modeler's expectation that the query contains personal data. EVL compares this flag with the classifications of the output items.                           | `true`                                                                    |
| `paginationExpectation` | `String` `0..1`                 | Describes how a potentially large result set should be divided for consumers.                                                                                            | `Cursor pagination by application number`                                 |
| `filteringExpectation`  | `String` `0..1`                 | States which business filters are expected to narrow the result.                                                                                                         | `Filter by region, status, and submission date`                           |
| `sortingExpectation`    | `String` `0..1`                 | States the meaningful business order of the result.                                                                                                                      | `Oldest submitted application first`                                      |
| `queryType`             | `cimtypes.QueryType` `0..1`     | Classifies the read as lookup, list, search, report, status, or analytics. List and search queries receive extra result-handling guidance.                               | `LIST`                                                                    |
| `freshnessNeed`         | `cimtypes.FreshnessNeed` `0..1` | Records how current the result must be. This helps later refinement choose between direct reads, updated views, or periodic materialization.                             | `NEAR_REAL_TIME`                                                          |

### Relationships

| Feature            | Target and multiplicity            | Kind and opposite                                        | Meaning                                                                                                     |
| ------------------ | ---------------------------------- | -------------------------------------------------------- | ----------------------------------------------------------------------------------------------------------- |
| `input`            | `cimdomain.InformationItem` `0..*` | reference                                                | Lists criteria or contextual information supplied to the query.                                             |
| `output`           | `cimdomain.InformationItem` `1..*` | reference                                                | Identifies the information the query promises to return. At least one output item is required structurally. |
| `reads`            | `cimdomain.DomainEntity` `0..*`    | reference                                                | States which entity concepts the query reads. This is a semantic read scope, not a database table list.     |
| `issuedBy`         | `cimorg.Actor` `0..*`              | reference; opposite `Actor.issuesQueries`                | Identifies possible requesters of the query.                                                                |
| `targetCapability` | `cimorg.BusinessCapability` `0..1` | reference; opposite `BusinessCapability.containsQueries` | Associates the read with the capability responsible for the information.                                    |

### Validation and refinement

If a query declares or returns personal data, EVL requires `authorizationRequired=true` and a non-empty `authorizationRule`. List and search queries are warned when pagination and filtering expectations are missing. Every query is also encouraged to declare `freshnessNeed`. `Query2Function` creates a PIM function and contract; `Query2ApiRoute` creates a route; and `Query2AccessPattern` creates a read access pattern when `reads` is populated.

## `BusinessEvent`

`BusinessEvent` represents a fact that has already happened. It is named in past tense and carries the business meaning of that fact, its time interpretation, correlation information, versioning decision, payload, and participants. It can be produced by a command, policy, or external system and consumed by another policy, process, or external system.

### Declared attributes

| Attribute                   | Type and multiplicity                | Meaning                                                                                                                                                                   | Example                                                                           |
| --------------------------- | ------------------------------------ | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | --------------------------------------------------------------------------------- |
| `semanticName`              | `String` `0..1`                      | Provides a stable business name for the fact independent of a topic, message type, or transport.                                                                          | `GrantApplicationSubmitted`                                                       |
| `occurredInPastTenseName`   | `String` `0..1`                      | Provides the explicitly past-tense form used when the normal model name does not make the event grammar clear. EVL accepts this as an alternative to a past-tense `name`. | `Grant application was submitted`                                                 |
| `businessMeaning`           | `String` `0..1`                      | Explains what changed or became true in the business and why consumers might care.                                                                                        | `The agency has received a complete application for review.`                      |
| `businessTimestampItemName` | `String` `0..1`                      | Names the payload information item that records the business time of the fact.                                                                                            | `submittedAt`                                                                     |
| `correlationBusinessKey`    | `String` `0..1`                      | Identifies the business key used to relate this fact to a wider case, order, application, or process.                                                                     | `applicationNumber`                                                               |
| `causationBusinessKey`      | `String` `0..1`                      | Identifies the business key of the action or fact that caused this event.                                                                                                 | `submissionReference`                                                             |
| `orderingKeyCandidate`      | `String` `0..1`                      | Names a candidate business value that can preserve meaningful ordering among related events.                                                                              | `applicationNumber`                                                               |
| `externallyVisible`         | `Boolean` `0..1`                     | States whether the event crosses the modeled boundary and therefore needs stable consumer-facing semantics.                                                               | `true`                                                                            |
| `auditRelevant`             | `Boolean` `0..1`                     | States whether the event is evidence of an action or decision that may need to be reviewed.                                                                               | `true`                                                                            |
| `retentionRelevant`         | `Boolean` `0..1`                     | States whether the event's historical record matters for later retention or reconstruction.                                                                               | `true`                                                                            |
| `semanticVersion`           | `String` `0..1`                      | Records the version of the event meaning and payload contract. Production-relevant events must provide a SemVer-like value.                                               | `1.0.0`                                                                           |
| `versioningRationale`       | `String` `0..1`                      | Explains how and why event meaning or payload changes will be managed.                                                                                                    | `Payload additions remain backward compatible; removal requires a major version.` |
| `eventTimeSemantics`        | `cimtypes.EventTimeSemantics` `0..1` | Distinguishes business time from observation time and recording time. This avoids confusing when something happened with when a system noticed it.                        | `BUSINESS_TIME`                                                                   |

### Relationships

| Feature                     | Target and multiplicity             | Kind and opposite                                   | Meaning                                                                                              |
| --------------------------- | ----------------------------------- | --------------------------------------------------- | ---------------------------------------------------------------------------------------------------- |
| `expectedByCommands`        | `Command` `0..*`                    | reference; opposite `Command.expectedEvents`        | Identifies commands for which this event is an expected success fact.                                |
| `rejectedByCommands`        | `Command` `0..*`                    | reference; opposite `Command.rejectionEvents`       | Identifies commands for which this event records rejection or unsuccessful handling.                 |
| `causedByExternalSystems`   | `cimorg.ExternalSystem` `0..*`      | reference; opposite `ExternalSystem.producedEvents` | Identifies external systems that produce or originate the fact.                                      |
| `consumedByExternalSystems` | `cimorg.ExternalSystem` `0..*`      | reference; opposite `ExternalSystem.consumedEvents` | Identifies external systems that use the fact.                                                       |
| `causedByPolicies`          | `cimprocess.Policy` `0..*`          | reference; opposite `Policy.emitsEvents`            | Identifies policies whose reaction or derivation emits the event.                                    |
| `consumedByPolicies`        | `cimprocess.Policy` `0..*`          | reference; opposite `Policy.triggeredBy`            | Identifies policies activated by the event.                                                          |
| `consumedByProcesses`       | `cimprocess.BusinessProcess` `0..*` | reference                                           | Identifies processes that observe or use the fact as part of their progression.                      |
| `payload`                   | `cimdomain.InformationItem` `0..*`  | reference                                           | Lists the business information carried by the event. EVL requires every payload item to have a type. |
| `affects`                   | `cimdomain.DomainEntity` `0..*`     | reference                                           | States which entity concepts are affected by the fact.                                               |

### Validation and refinement

EVL requires a business event to be named as a past-tense fact, to have a business meaning or semantic name, and to participate in at least one producer or consumer relationship. When it is externally visible, audit-relevant, or retention-relevant, it must have versioning metadata in a SemVer-like form. Its payload information must be typed. `BusinessEvent2EventType` creates a PIM event type and schema, while integration rules create channels, flows, subscriptions, and external event paths.

## `BusinessError`

`BusinessError` describes a failure in business language. It is different from a technical exception because it tells a user or consuming process what business outcome could not be achieved and whether retry or recovery makes sense.

### Declared attributes

| Attribute            | Type and multiplicity | Meaning                                                                                                                                               | Example                                                              |
| -------------------- | --------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------- | -------------------------------------------------------------------- |
| `errorCode`          | `String` `0..1`       | Supplies a stable business identifier for the failure.                                                                                                | `APPLICATION_INCOMPLETE`                                             |
| `businessMeaning`    | `String` `0..1`       | Explains the business condition that caused the failure.                                                                                              | `The application does not contain the evidence required for review.` |
| `userVisibleMessage` | `String` `0..1`       | Gives the message suitable for the affected user or business participant. It should avoid implementation details.                                     | `Add the missing income document before submitting the application.` |
| `recoverable`        | `Boolean` `0..1`      | States whether the business situation can be corrected or otherwise recovered.                                                                        | `true`                                                               |
| `retryMeaningful`    | `Boolean` `0..1`      | States whether repeating the same action without a business correction could have value. EVL warns when this is true while `recoverable` is not true. | `false`                                                              |
| `auditRequired`      | `Boolean` `0..1`      | States whether the error itself should be recorded as an auditable business outcome.                                                                  | `true`                                                               |

### Relationships

| Feature         | Target and multiplicity | Kind      | Meaning                                                                                       |
| --------------- | ----------------------- | --------- | --------------------------------------------------------------------------------------------- |
| `emittedEvents` | `BusinessEvent` `0..*`  | reference | Lists facts emitted when this error is recognized, such as a rejection or compensation event. |

EVL requires the code, business meaning, and user-visible message. `BusinessError2ErrorSchema` turns the error into a PIM contract schema so generated interfaces can preserve the business failure vocabulary.

## `Condition`

`Condition` expresses a proposition that must be true, or a test that chooses a business path. It supports a human-readable statement and, when automation is needed, a typed `kernel.Expression`. Referenced information and concepts make the scope of the proposition explicit.

### Declared attributes

| Attribute            | Type and multiplicity              | Meaning                                                                                                                                                              | Example                                                |
| -------------------- | ---------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------ | -------------------- |
| `naturalLanguage`    | `String` `0..1`                    | States the condition in business language so a domain expert can review it.                                                                                          | `The applicant has submitted all mandatory documents.` |
| `expression`         | `String` `0..1`                    | Holds a textual expression when a separate `Expression` object is not used. Its interpretation is given by `expressionLanguage`.                                     | `documents->forAll(d                                   | d.mandatory = true)` |
| `mustBeAutomatable`  | `Boolean` `0..1`                   | States whether the condition must be machine-evaluable during refinement or runtime design. If true, EVL requires a typed expression or an expression/language pair. | `true`                                                 |
| `expressionLanguage` | `kernel.ExpressionLanguage` `0..1` | Identifies how the `expression` string should be interpreted. It prevents a free-form expression from being mistaken for an executable rule without a language.      | `OCL`                                                  |

### Relationships

| Feature                 | Target and multiplicity            | Kind        | Meaning                                                                                                                                        |
| ----------------------- | ---------------------------------- | ----------- | ---------------------------------------------------------------------------------------------------------------------------------------------- |
| `expressionModel`       | `kernel.Expression` `0..1`         | containment | Stores a structured expression with its language, body, phase, side-effect expectation, and referenced elements. It is owned by the condition. |
| `referencedInformation` | `cimdomain.InformationItem` `0..*` | reference   | Identifies the business data read by the condition.                                                                                            |
| `referencedConcepts`    | `cimdomain.DomainConcept` `0..*`   | reference   | Identifies the domain concepts whose meaning is used by the condition.                                                                         |

EVL requires a natural-language statement, textual expression, or expression model. An automatable condition needs an actual typed expression. A warning is reported when the condition is not linked to the information or concepts it uses. Conditions are reused by commands, processes, transitions, decisions, and policies rather than being embedded in one technical implementation.
