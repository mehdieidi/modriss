# CIM organization and bounded contexts

Source: `mde/metamodels/cim/cim-organization.emf`.

This module explains why the modeled work matters and who is responsible for it. It starts with goals and requirements, separates stakeholders from actors, describes responsibilities through roles and capabilities, and then records candidate language and ownership boundaries. These distinctions are important during refinement. A stakeholder may own a goal without issuing a command. An actor may issue a command without owning the capability that handles it. A capability may own entities and processes while several roles perform its work.

The classes inherit from `kernel.TraceableElement`, except where a class's direct subtype is shown below. Inherited names, descriptions, lifecycle, rationale, source evidence, and trace features are documented in the [shared kernel](../shared-kernel.md).

## `Requirement`

`Requirement` records an obligation that the business expects the modeled solution to satisfy. It is deliberately broader than a feature request. Its type can identify a functional expectation, rule, quality concern, security or privacy obligation, compliance control, audit need, report, or data-governance concern. The requirement becomes useful for transformation only when the model states how satisfaction will be judged and what goal or element it affects.

### Declared attributes

| Attribute            | Type and multiplicity                   | Meaning                                                                                                                                                                                | Example                                                                               |
| -------------------- | --------------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------- |
| `mandatory`          | `Boolean` `0..1`                        | States whether the requirement is binding for the current increment. A production-blocking requirement must be mandatory according to EVL.                                             | `true`                                                                                |
| `productionBlocking` | `Boolean` `0..1`                        | States whether failure to satisfy the requirement prevents production readiness. It is a release significance decision, not a statement that implementation is technically impossible. | `true`                                                                                |
| `fitCriterion`       | `String` `0..1`                         | Gives a direct criterion for deciding whether the requirement fits the intended business outcome. EVL accepts it as an alternative to detailed acceptance criteria.                    | `A submitted application is visible to the assigned region within one business hour.` |
| `requirementType`    | `cimtypes.RequirementType` `0..1`       | Identifies the kind of obligation so later validation and refinement can treat a business rule, quality expectation, privacy need, or report appropriately.                            | `FUNCTIONAL`                                                                          |
| `sourceType`         | `cimtypes.RequirementSourceType` `0..1` | Records where the requirement came from, such as a stakeholder, regulation, workshop, existing system, or explicit assumption.                                                         | `REGULATION`                                                                          |
| `priority`           | `kernel.Priority` `0..1`                | Records relative attention for analysis and review. It does not replace `productionBlocking`.                                                                                          | `CRITICAL`                                                                            |

### Relationships

| Feature              | Target and multiplicity      | Kind        | Meaning                                                                                                                          |
| -------------------- | ---------------------------- | ----------- | -------------------------------------------------------------------------------------------------------------------------------- |
| `acceptanceCriteria` | `AcceptanceCriterion` `0..*` | containment | Keeps the observable conditions for judging the requirement inside the requirement.                                              |
| `supportsGoals`      | `BusinessGoal` `0..*`        | reference   | Links the requirement to the outcomes it helps achieve.                                                                          |
| `constrains`         | `kernel.ModelElement` `0..*` | reference   | Identifies the model elements whose design or behavior is limited by the requirement.                                            |
| `dependsOn`          | `Requirement` `0..*`         | reference   | Records requirements that must be satisfied first or whose meaning is needed by this requirement. Self-dependencies are invalid. |
| `conflictsWith`      | `Requirement` `0..*`         | reference   | Records an incompatibility that needs resolution or an explicit trade-off. Self-conflicts are invalid.                           |

EVL requires a `fitCriterion` or at least one complete acceptance criterion and warns when the requirement is disconnected from goals and constrained elements. `Requirement2BusinessRule` carries the requirement into a PIM business rule and preserves its text and trace.

## `RequirementRelationship`

`RequirementRelationship` is a first-class explanation of how two requirements relate. A plain reference would show that requirements are connected, but it could not preserve why the connection matters or whether it blocks progress.

### Declared attributes

| Attribute   | Type and multiplicity                      | Meaning                                                                                                                                    | Example                                                                           |
| ----------- | ------------------------------------------ | ------------------------------------------------------------------------------------------------------------------------------------------ | --------------------------------------------------------------------------------- |
| `rationale` | `String` `0..1`                            | Explains the reason for the relationship, especially when two requirements conflict, duplicate one another, or refine a broader statement. | `The reporting requirement derives from the regulator's monthly submission rule.` |
| `blocking`  | `Boolean` `0..1`                           | States whether the relationship itself blocks the current transformation or readiness decision.                                            | `false`                                                                           |
| `kind`      | `cimtypes.RequirementRelationshipKind` `1` | Gives the controlled meaning of the link between `source` and `target`.                                                                    | `REFINES`                                                                         |

### Relationships

| Feature  | Target and multiplicity | Kind      | Meaning                                                     |
| -------- | ----------------------- | --------- | ----------------------------------------------------------- |
| `source` | `Requirement` `1`       | reference | The requirement whose relationship role is being described. |
| `target` | `Requirement` `1`       | reference | The requirement at the other end of the relationship.       |

The source and target are both mandatory, while the type explains the direction. The CIM root contains these relationship objects independently of the requirements they connect.

## `AcceptanceCriterion`

`AcceptanceCriterion` gives a requirement a testable scenario in a compact Given/When/Then form. It is still business-facing: the outcome may be a state change, event, visible result, or measurable response rather than a unit-test assertion.

### Declared attributes

| Attribute                  | Type and multiplicity | Meaning                                                                                                  | Example                                                                      |
| -------------------------- | --------------------- | -------------------------------------------------------------------------------------------------------- | ---------------------------------------------------------------------------- |
| `givenContext`             | `String` `0..1`       | States the relevant starting facts, permissions, or domain state.                                        | `Given an application is complete and assigned to a region`                  |
| `whenAction`               | `String` `0..1`       | States the business action or stimulus being evaluated.                                                  | `When the case officer submits an approval decision`                         |
| `thenOutcome`              | `String` `0..1`       | States the business result that must follow.                                                             | `Then the application becomes approved and a disbursement event is recorded` |
| `measurableTarget`         | `String` `0..1`       | Adds a concrete threshold or observable target when the criterion is a candidate for automation.         | `Approval decision is visible within 2 minutes`                              |
| `automatableTestCandidate` | `Boolean` `0..1`      | Marks whether the criterion is specific enough to become an automated test or check during later levels. | `true`                                                                       |

EVL requires all three Given/When/Then fields. A criterion marked as automatable receives a warning when it has no measurable target.

## `BusinessGoal`

`BusinessGoal` states the outcome that gives the model its direction. A goal is broader than a requirement and may be realized by several capabilities. Its value and failure consequence explain why the organization should invest in the modeled change.

### Declared attributes

| Attribute            | Type and multiplicity    | Meaning                                                                                                       | Example                                                                       |
| -------------------- | ------------------------ | ------------------------------------------------------------------------------------------------------------- | ----------------------------------------------------------------------------- |
| `successCriterion`   | `String` `0..1`          | States the evidence that will show the goal has been achieved. EVL treats this as mandatory semantic content. | `Eligible applicants receive a decision within the published service period.` |
| `timeHorizon`        | `String` `0..1`          | Places the intended outcome in a business time frame.                                                         | `For the next grant cycle`                                                    |
| `businessValue`      | `String` `0..1`          | Explains the benefit created if the goal is achieved.                                                         | `Reduces uncertainty for households waiting for emergency support.`           |
| `failureConsequence` | `String` `0..1`          | Explains the business harm of failing to achieve the goal.                                                    | `Applicants cannot plan essential repairs and trust in the program declines.` |
| `priority`           | `kernel.Priority` `0..1` | Records the relative importance of the goal. A critical goal is expected to have an owner and KPI.            | `CRITICAL`                                                                    |

### Relationships

| Feature      | Target and multiplicity     | Kind and opposite                           | Meaning                                                                     |
| ------------ | --------------------------- | ------------------------------------------- | --------------------------------------------------------------------------- |
| `measuredBy` | `KPI` `0..*`                | reference; opposite `KPI.measures`          | Identifies the metrics used to judge progress toward the goal.              |
| `refinedBy`  | `BusinessCapability` `0..*` | reference                                   | Identifies the capabilities that turn the goal into organizational ability. |
| `owners`     | `Stakeholder` `0..*`        | reference; opposite `Stakeholder.ownsGoals` | Identifies accountable interests for the outcome.                           |

EVL requires a success criterion and warns when a critical goal lacks both an owner and a KPI or when the value and failure consequence are unexplained. `BusinessGoal2BusinessRule` carries the goal's outcome language into a PIM business rule.

## `KPI`

`KPI` defines a measurement for a goal. It separates the metric itself from the goal so that the model can state the target, unit, frequency, source, and acceptance threshold explicitly.

### Declared attributes

| Attribute              | Type and multiplicity | Meaning                                                                                                                                             | Example                                                                |
| ---------------------- | --------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------- | ---------------------------------------------------------------------- |
| `metricName`           | `String` `0..1`       | Names the quantity being measured.                                                                                                                  | `Application decision latency`                                         |
| `metricDefinition`     | `String` `0..1`       | Defines how the metric is calculated so that two teams do not measure different things under the same name.                                         | `Elapsed business time from complete submission to recorded decision.` |
| `operator`             | `String` `0..1`       | States how the measured value is compared with the target. EVL recognizes `<`, `<=`, `>`, `>=`, `=`, `==`, `between`, `increases`, and `decreases`. | `<=`                                                                   |
| `targetValue`          | `String` `0..1`       | Gives the expected value or range. It remains text because the unit and comparison may be domain-specific.                                          | `2`                                                                    |
| `unit`                 | `String` `0..1`       | States how the target is measured.                                                                                                                  | `business days`                                                        |
| `measurementFrequency` | `String` `0..1`       | States when or how often the metric is collected.                                                                                                   | `For every completed application, aggregated weekly`                   |
| `dataSource`           | `String` `0..1`       | Names the business or operational source from which the measurement can be obtained.                                                                | `Grant case decision records`                                          |
| `acceptanceThreshold`  | `String` `0..1`       | Records the threshold used for accepting the goal or release decision when it differs from the normal target.                                       | `At least 95% of completed cases`                                      |

### Relationships

| Feature    | Target and multiplicity | Kind and opposite                             | Meaning                                                   |
| ---------- | ----------------------- | --------------------------------------------- | --------------------------------------------------------- |
| `measures` | `BusinessGoal` `0..1`   | reference; opposite `BusinessGoal.measuredBy` | Identifies the goal for which this KPI provides evidence. |

EVL requires `metricName`, `targetValue`, and `unit`, and warns when the definition, frequency, or source is absent. `KPI2ReadinessCheck` turns the KPI into a readiness check in the generated PIM.

## `Stakeholder`

`Stakeholder` represents an interest, responsibility, or affected viewpoint. It need not perform a modeled command. Its value is in explaining who cares about the goal or requirement and what concern should be preserved during later design.

### Declared attributes

| Attribute         | Type and multiplicity | Meaning                                                                          | Example                                                 |
| ----------------- | --------------------- | -------------------------------------------------------------------------------- | ------------------------------------------------------- |
| `stakeholderType` | `String` `0..1`       | Categorizes the represented interest or stakeholder group.                       | `Compliance office`                                     |
| `concern`         | `String` `0..1`       | States what the stakeholder needs protected, measured, decided, or made visible. | `Evidence must show which officer approved each grant.` |
| `influenceLevel`  | `String` `0..1`       | Records the stakeholder's influence on the decision or model.                    | `High`                                                  |
| `contactRole`     | `String` `0..1`       | Names the role through which the project communicates with the stakeholder.      | `Compliance lead`                                       |

### Relationships

| Feature                | Target and multiplicity | Kind                                      | Meaning                                                                               |
| ---------------------- | ----------------------- | ----------------------------------------- | ------------------------------------------------------------------------------------- |
| `ownsGoals`            | `BusinessGoal` `0..*`   | reference; opposite `BusinessGoal.owners` | Identifies outcomes for which the stakeholder is accountable or represents the owner. |
| `providesRequirements` | `Requirement` `0..*`    | reference                                 | Identifies requirements supplied or sponsored by the stakeholder.                     |

EVL warns when `stakeholderType` or `concern` is missing. A stakeholder is also the source of a PIM `ManualDecision` in `Stakeholder2ManualDecision`, which makes unresolved stakeholder concerns visible to readiness work.

## `Actor`

`Actor` is a participant that can issue behavior, observe events, or be assigned a role. It can represent a person, organization, department, time trigger, regulator, external organization, or external system. Trust and authentication expectations belong here because they shape the business boundary before a concrete identity provider is chosen.

### Declared attributes

| Attribute                   | Type and multiplicity                    | Meaning                                                                                               | Example                                           |
| --------------------------- | ---------------------------------------- | ----------------------------------------------------------------------------------------------------- | ------------------------------------------------- |
| `organizationBoundary`      | `String` `0..1`                          | States whether the actor is inside, outside, or at a particular boundary of the modeled organization. | `Regional agency internal`                        |
| `authenticationExpectation` | `String` `0..1`                          | Describes how the actor is expected to establish identity in business terms.                          | `Authenticated agency account`                    |
| `authorizationExpectation`  | `String` `0..1`                          | Describes the business permissions or conditions that should restrict the actor.                      | `May access cases assigned to the actor's region` |
| `interactionExpectation`    | `cimtypes.InteractionExpectation` `0..1` | States how the actor expects to interact with the business behavior.                                  | `HUMAN_REVIEW_REQUIRED`                           |
| `actorType`                 | `cimtypes.ActorType` `0..1`              | Classifies what kind of participant this is. EVL requires it.                                         | `HUMAN`                                           |
| `trustLevel`                | `cimtypes.TrustLevel` `0..1`             | Records the trust assumption used when reasoning about boundaries. EVL requires it.                   | `TRUSTED_INTERNAL`                                |

### Relationships

| Feature          | Target and multiplicity            | Kind and opposite                      | Meaning                                                     |
| ---------------- | ---------------------------------- | -------------------------------------- | ----------------------------------------------------------- |
| `playsRoles`     | `Role` `0..*`                      | reference; opposite `Role.assignedTo`  | Identifies responsibilities the actor performs.             |
| `issuesCommands` | `cimbehavior.Command` `0..*`       | reference; opposite `Command.issuedBy` | Identifies commands the actor may initiate.                 |
| `issuesQueries`  | `cimbehavior.Query` `0..*`         | reference; opposite `Query.issuedBy`   | Identifies information requests the actor may make.         |
| `observesEvents` | `cimbehavior.BusinessEvent` `0..*` | reference                              | Identifies business facts visible or relevant to the actor. |

EVL requires actor type and trust level. Human actors receive a warning when no role is assigned. External, regulated, or untrusted actors must describe authentication and authorization expectations. `Actor2Principal` and `Role2Principal` refine these concepts into PIM security principals and policies.

## `Role`

`Role` describes a responsibility and the business permissions associated with it. A role is intentionally separate from an actor: a person can change roles, a role can be assigned to several actors, and the role's permission summary can be reviewed without binding it to an identity mechanism.

### Declared attributes

| Attribute                   | Type and multiplicity | Meaning                                                                                                  | Example                                                      |
| --------------------------- | --------------------- | -------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------ |
| `responsibility`            | `String` `0..1`       | States the work or decision responsibility represented by the role.                                      | `Review evidence and approve eligible applications`          |
| `businessPermissionSummary` | `String` `0..1`       | Summarizes what the role may do in business terms. Privileged roles must provide it.                     | `May approve applications up to the regional funding limit.` |
| `privileged`                | `Boolean` `0..1`      | Marks a role whose permissions deserve elevated review. It does not itself grant a technical permission. | `true`                                                       |

### Relationships

| Feature      | Target and multiplicity | Kind and opposite                      | Meaning                                  |
| ------------ | ----------------------- | -------------------------------------- | ---------------------------------------- |
| `assignedTo` | `Actor` `0..*`          | reference; opposite `Actor.playsRoles` | Identifies actors that perform the role. |

EVL requires responsibility and permission text for privileged roles and warns when a role is assigned to no actor. The ETL rule `Role2Principal` uses the role's responsibility and permission summary when creating PIM security concepts.

## `ExternalSystem`

`ExternalSystem` specializes `Actor` for a system outside the modeled business boundary. It records why the integration exists, who owns the external system, why its trust level is accepted, and what information or events cross the boundary. It should be used for a business-relevant external participant, not for a future cloud service selected by the implementation team.

### Declared attributes

| Attribute                | Type and multiplicity | Meaning                                                                                    | Example                                                                                  |
| ------------------------ | --------------------- | ------------------------------------------------------------------------------------------ | ---------------------------------------------------------------------------------------- |
| `owningOrganization`     | `String` `0..1`       | Identifies the organization responsible for the external system.                           | `National identity service`                                                              |
| `businessPurpose`        | `String` `0..1`       | Explains why the modeled business depends on the system.                                   | `Verify applicant identity before grant approval`                                        |
| `trustRationale`         | `String` `0..1`       | Explains why the chosen trust level is acceptable for the intended exchange.               | `The service is mandated and its responses are signed under the inter-agency agreement.` |
| `contractualSla`         | `String` `0..1`       | Records the relevant business or contractual service expectation.                          | `99.5% monthly availability; response within 10 seconds`                                 |
| `storesBusinessData`     | `Boolean` `0..1`      | States whether the external system retains business information from the modeled exchange. | `false`                                                                                  |
| `sendsBusinessEvents`    | `Boolean` `0..1`      | States whether the system can originate business events consumed by the model.             | `true`                                                                                   |
| `receivesBusinessEvents` | `Boolean` `0..1`      | States whether the system receives business events from the model.                         | `false`                                                                                  |

### Relationships

| Feature                | Target and multiplicity            | Kind and opposite                                             | Meaning                                              |
| ---------------------- | ---------------------------------- | ------------------------------------------------------------- | ---------------------------------------------------- |
| `exchangedInformation` | `cimdomain.InformationItem` `0..*` | reference                                                     | Lists business information crossing the boundary.    |
| `producedEvents`       | `cimbehavior.BusinessEvent` `0..*` | reference; opposite `BusinessEvent.causedByExternalSystems`   | Links facts originated by the system.                |
| `consumedEvents`       | `cimbehavior.BusinessEvent` `0..*` | reference; opposite `BusinessEvent.consumedByExternalSystems` | Links facts delivered to and consumed by the system. |

EVL requires `actorType=EXTERNAL_SYSTEM`, the owning organization, purpose, and trust rationale. When any exchange/storage flag is true, at least one information or event link must be present. `ExternalSystem2Adapter` creates a PIM external adapter, endpoint, and credential requirement from this business boundary.

## `BusinessCapability`

`BusinessCapability` describes an ability the organization possesses or needs to develop. It is outcome-oriented and stable enough to group behavior, data, and processes. In CIM-to-PIM refinement, a capability or a bounded context is a candidate service boundary, so ownership and responsibility must be explicit.

### Declared attributes

| Attribute        | Type and multiplicity                   | Meaning                                                                                      | Example                                          |
| ---------------- | --------------------------------------- | -------------------------------------------------------------------------------------------- | ------------------------------------------------ |
| `maturity`       | `String` `0..1`                         | States how established the capability is in the organization.                                | `Operational but partly manual`                  |
| `responsibility` | `String` `0..1`                         | Describes the business responsibility covered by the capability.                             | `Assess and decide emergency grant applications` |
| `ownerName`      | `String` `0..1`                         | Names the accountable owner when an `Actor` reference is not available or is not sufficient. | `Regional grants directorate`                    |
| `criticality`    | `cimtypes.CapabilityCriticality` `0..1` | Records how essential the capability is to the modeled outcome.                              | `CORE`                                           |

### Relationships

| Feature                | Target and multiplicity                  | Kind and opposite                                      | Meaning                                                                                |
| ---------------------- | ---------------------------------------- | ------------------------------------------------------ | -------------------------------------------------------------------------------------- |
| `owner`                | `Actor` `0..1`                           | reference                                              | Identifies the actor accountable for the capability.                                   |
| `supports`             | `BusinessGoal` `0..*`                    | reference                                              | Links the ability to the outcomes it helps achieve. EVL requires at least one goal.    |
| `realizesRequirements` | `Requirement` `0..*`                     | reference                                              | Identifies obligations realized by this capability.                                    |
| `containsCommands`     | `cimbehavior.Command` `0..*`             | reference; opposite `Command.targetCapability`         | Groups state-changing behavior owned by the capability.                                |
| `containsQueries`      | `cimbehavior.Query` `0..*`               | reference; opposite `Query.targetCapability`           | Groups information requests owned by the capability.                                   |
| `containsEvents`       | `cimbehavior.BusinessEvent` `0..*`       | reference                                              | Identifies facts associated with the capability.                                       |
| `managesEntities`      | `cimdomain.DomainEntity` `0..*`          | reference; opposite `DomainEntity.owningCapability`    | Identifies entity lifecycles and invariants managed by the capability.                 |
| `ownsProcesses`        | `cimprocess.BusinessProcess` `0..*`      | reference; opposite `BusinessProcess.owningCapability` | Identifies longer business progressions for which the capability is responsible.       |
| `constrainedBy`        | `cimgov.NonFunctionalRequirement` `0..*` | reference                                              | Links quality, security, privacy, or compliance constraints that shape the capability. |

EVL requires a supporting goal and either an owner, an owner name, or a responsibility statement. It warns when a capability has no behavior, data, or process and when a core or mission-critical capability lacks requirements or NFRs. `BoundedContext2Service` and `Capability2Service` use capabilities as service seeds, with bounded-context membership taking precedence where applicable.

## `CapabilityDependency`

`CapabilityDependency` records a directed reliance between capabilities. It is more precise than placing two capabilities in the same context because it preserves the reason for the reliance and whether it affects the critical path.

### Declared attributes

| Attribute          | Type and multiplicity | Meaning                                                                             | Example                                                          |
| ------------------ | --------------------- | ----------------------------------------------------------------------------------- | ---------------------------------------------------------------- |
| `dependencyReason` | `String` `0..1`       | Explains what the source needs from the target.                                     | `Application assessment requires a verified applicant identity.` |
| `criticalPath`     | `Boolean` `0..1`      | Marks a dependency whose failure can delay or block the principal business outcome. | `true`                                                           |

### Relationships

| Feature  | Target and multiplicity  | Kind      | Meaning                                            |
| -------- | ------------------------ | --------- | -------------------------------------------------- |
| `source` | `BusinessCapability` `1` | reference | The capability that depends on another capability. |
| `target` | `BusinessCapability` `1` | reference | The capability relied upon by the source.          |

EVL requires distinct source and target capabilities and a dependency reason. A critical-path dependency needs a rationale in either inherited `rationale` or `dependencyReason`. During integration refinement, the dependency can seed message schemas, queues, event types, and producer/consumer flows.

## `BoundedContextCandidate`

`BoundedContextCandidate` proposes a boundary around a consistent language and ownership area. It is a candidate rather than a final service definition. A context may group capabilities, entities, commands, queries, events, and policies, while a capability may still be used as a service boundary when no context claims it.

### Declared attributes

| Attribute                     | Type and multiplicity | Meaning                                                                                                 | Example                                                                      |
| ----------------------------- | --------------------- | ------------------------------------------------------------------------------------------------------- | ---------------------------------------------------------------------------- |
| `languageBoundary`            | `String` `0..1`       | Describes which terms and meanings belong inside the context and which meanings belong elsewhere.       | `Application review vocabulary; “case” means a submitted grant application.` |
| `ownershipBoundary`           | `String` `0..1`       | Identifies the team, business unit, or authority responsible for the context's decisions and data.      | `Regional grants office`                                                     |
| `externalIntegrationBoundary` | `Boolean` `0..1`      | States whether the candidate explicitly includes a boundary for interaction with external participants. | `true`                                                                       |

### Relationships

| Feature         | Target and multiplicity            | Kind                                                   | Meaning                                                                  |
| --------------- | ---------------------------------- | ------------------------------------------------------ | ------------------------------------------------------------------------ |
| `glossaryTerms` | `UbiquitousLanguageTerm` `0..*`    | containment; opposite `UbiquitousLanguageTerm.context` | Keeps the terms owned by this context in its model subtree.              |
| `capabilities`  | `BusinessCapability` `0..*`        | reference                                              | Groups abilities that share the proposed boundary.                       |
| `entities`      | `cimdomain.DomainEntity` `0..*`    | reference                                              | Identifies entities whose meaning and ownership are inside the boundary. |
| `commands`      | `cimbehavior.Command` `0..*`       | reference                                              | Identifies state changes belonging to the context.                       |
| `queries`       | `cimbehavior.Query` `0..*`         | reference                                              | Identifies reads using the context's language and data.                  |
| `events`        | `cimbehavior.BusinessEvent` `0..*` | reference                                              | Identifies facts emitted or consumed at the boundary.                    |
| `policies`      | `cimprocess.Policy` `0..*`         | reference                                              | Identifies rules interpreted within the context.                         |

EVL requires language and ownership boundaries and warns when the context has no scoped content. It also warns when a command targets aggregate members outside the context without an explicit capability dependency or another integration explanation. A bounded context is transformed into a PIM service by `BoundedContext2Service`.

## `UbiquitousLanguageTerm`

`UbiquitousLanguageTerm` makes a context's vocabulary explicit. It prevents a familiar word from silently acquiring different meanings in different parts of the model and provides text that can be carried into generated business rules and documentation.

### Declared attributes

| Attribute           | Type and multiplicity | Meaning                                                                                                                          | Example                                                                     |
| ------------------- | --------------------- | -------------------------------------------------------------------------------------------------------------------------------- | --------------------------------------------------------------------------- |
| `term`              | `String` `1`          | The exact business word or phrase used in the context. It is required because a term without a word cannot establish vocabulary. | `Applicant`                                                                 |
| `definition`        | `String` `0..1`       | Defines what the term means within this context. EVL requires a non-empty definition.                                            | `A person or organization requesting assistance through the grant program.` |
| `synonyms`          | `String` `0..1`       | Records accepted alternative wording when it is genuinely equivalent in this context.                                            | `Requester; grant seeker`                                                   |
| `forbiddenSynonyms` | `String` `0..1`       | Records words that should not be used because they create ambiguity or import another context's meaning.                         | `Customer`                                                                  |
| `exampleUsage`      | `String` `0..1`       | Shows the term in a sentence or business statement.                                                                              | `The applicant receives a decision after eligibility review.`               |

### Relationships

| Feature   | Target and multiplicity       | Kind and opposite                                                     | Meaning                                                                                      |
| --------- | ----------------------------- | --------------------------------------------------------------------- | -------------------------------------------------------------------------------------------- |
| `context` | `BoundedContextCandidate` `1` | read-only reference; opposite `BoundedContextCandidate.glossaryTerms` | Identifies the context that owns this term. It is derived from the containment relationship. |

EVL requires a term and definition and warns about case-insensitive duplicate terms inside the same context. `UbiquitousLanguageTerm2BusinessRule` creates a PIM business rule that asks generated designs to use the term consistently and avoid forbidden synonyms.
