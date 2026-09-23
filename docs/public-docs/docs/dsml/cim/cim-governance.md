# CIM governance

Source: `mde/metamodels/cim/cim-governance.emf`.

Governance classes turn quality and control expectations into model elements that can constrain later architecture. They remain at the business level. A security constraint says what must be protected and why. A privacy constraint says why information may be processed, for how long, and with which data-subject obligations. A compliance constraint identifies the control and evidence. The CIM-to-PIM transformation then chooses concrete policy, security, data-protection, resilience, observability, and contract structures.

`NonFunctionalRequirement` extends `cimorg.Requirement`, so it inherits requirement type, source, priority, mandatory and production-blocking status, acceptance criteria, goal links, and generic constrained elements. The other governance classes inherit from it unless stated otherwise. Shared kernel features remain documented in the [shared kernel](../shared-kernel.md).

## `NonFunctionalRequirement`

`NonFunctionalRequirement` records a quality expectation that must be measurable and connected to model elements. The class is intentionally general. `SecurityConstraint`, `PrivacyConstraint`, and `ComplianceConstraint` specialize it when the concern needs a more specific vocabulary.

### Declared attributes

| Attribute               | Type and multiplicity         | Meaning                                                                                   | Example                                                                                     |
| ----------------------- | ----------------------------- | ----------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------- |
| `metric`                | `String` `0..1`               | Names the quality measure that will be observed.                                          | `Decision response latency`                                                                 |
| `target`                | `String` `0..1`               | States the threshold or target for the metric.                                            | `No more than 2 seconds for 95% of requests`                                                |
| `measurementMethod`     | `String` `0..1`               | Explains how the metric is calculated or observed.                                        | `Measure elapsed time from route acceptance to decision response at the business boundary.` |
| `environmentAssumption` | `String` `0..1`               | States the conditions under which the target applies.                                     | `Normal regional load during business hours`                                                |
| `qualityType`           | `cimtypes.QualityType` `0..1` | Classifies the concern so the PIM transformation can select an appropriate policy family. | `LATENCY`                                                                                   |

### Relationships

| Feature               | Target and multiplicity      | Kind        | Meaning                                                                                                                                                                                                       |
| --------------------- | ---------------------------- | ----------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `scenarios`           | `QualityScenario` `0..*`     | containment | Keeps concrete stimulus-response scenarios under the quality requirement. A production-blocking NFR is warned when it has no scenario.                                                                        |
| `constrainedElements` | `kernel.ModelElement` `1..*` | reference   | Identifies the business or model elements to which the quality expectation applies. The specialized security and privacy links should be consistent with this canonical scope where the EVL rules require it. |

EVL requires a metric and target. A production-blocking NFR should include at least one complete `QualityScenario`. In `process-policy.etl`, availability and reliability become resilience/backup policies; performance, latency, and scalability become timeout, concurrency, cache, and rate-limit policies; auditability, operability, and maintainability become observability policies; cost, data quality, and generic compliance follow their corresponding policy paths.

## `QualityScenario`

`QualityScenario` gives a quality requirement a concrete situation. It follows the source-stimulus-environment-artifact-response-measure pattern so that a statement such as “the system must be fast” becomes a reviewable expectation about a particular business interaction.

### Declared attributes

| Attribute         | Type and multiplicity         | Meaning                                                                        | Example                                                              |
| ----------------- | ----------------------------- | ------------------------------------------------------------------------------ | -------------------------------------------------------------------- |
| `source`          | `String` `0..1`               | Identifies who or what initiates the quality stimulus.                         | `Case officer`                                                       |
| `stimulus`        | `String` `0..1`               | Describes the event or load that activates the scenario.                       | `Requests the current application status`                            |
| `environment`     | `String` `0..1`               | States the operating conditions relevant to the target.                        | `Normal weekday load with 500 concurrent officers`                   |
| `artifact`        | `String` `0..1`               | Names the business behavior, process, or conceptual artifact under evaluation. | `Application status query`                                           |
| `response`        | `String` `0..1`               | Describes the expected response.                                               | `The current status is returned without stale approval information.` |
| `responseMeasure` | `String` `0..1`               | Gives the observable measure and threshold.                                    | `95th percentile response time <= 2 seconds`                         |
| `qualityType`     | `cimtypes.QualityType` `0..1` | Identifies the quality concern addressed by this scenario.                     | `PERFORMANCE`                                                        |

EVL requires all six scenario parts: source, stimulus, environment, artifact, response, and response measure. A scenario is contained by its `NonFunctionalRequirement` and is therefore part of that requirement's evidence.

## `SecurityConstraint`

`SecurityConstraint` specializes a non-functional requirement for protection of actors, commands, queries, and information. It can express authentication, authorization, separation of duties, non-repudiation, audit access, and the threat reasoning behind the constraint.

### Declared attributes

| Attribute                     | Type and multiplicity | Meaning                                                                                           | Example                                                                                         |
| ----------------------------- | --------------------- | ------------------------------------------------------------------------------------------------- | ----------------------------------------------------------------------------------------------- |
| `authenticationNeed`          | `String` `0..1`       | Describes how the constrained actor or behavior must establish identity.                          | `Only authenticated agency officers may submit approval decisions.`                             |
| `authorizationRule`           | `String` `0..1`       | States the business permission rule that determines whether an action or read is allowed.         | `The officer must be assigned to the application's region.`                                     |
| `segregationOfDutiesRequired` | `Boolean` `0..1`      | States whether two responsibilities must be performed by different actors.                        | `true`                                                                                          |
| `nonRepudiationRequired`      | `Boolean` `0..1`      | States whether the business needs evidence that an actor cannot later deny performing the action. | `true`                                                                                          |
| `auditAccessRequired`         | `Boolean` `0..1`      | States whether access to the protected behavior or information must be recorded.                  | `true`                                                                                          |
| `threatRationale`             | `String` `0..1`       | Explains the threat or harm that justifies the security constraint.                               | `A compromised external account must not approve an application or hide the action from audit.` |

### Relationships

| Feature                  | Target and multiplicity            | Kind      | Meaning                                                           |
| ------------------------ | ---------------------------------- | --------- | ----------------------------------------------------------------- |
| `constrainedActors`      | `cimorg.Actor` `0..*`              | reference | Identifies actors whose identity or permissions are constrained.  |
| `constrainedCommands`    | `cimbehavior.Command` `0..*`       | reference | Identifies state-changing behavior governed by the security rule. |
| `constrainedQueries`     | `cimbehavior.Query` `0..*`         | reference | Identifies reads governed by the security rule.                   |
| `constrainedInformation` | `cimdomain.InformationItem` `0..*` | reference | Identifies information whose access or handling is restricted.    |

EVL requires at least one specialized target and at least one authentication need, authorization rule, or threat rationale. When an authorization rule is given, every constrained command must also declare authorization. The specialized targets should also appear in inherited `constrainedElements`, which serves as the canonical generic scope. `SecurityConstraint2SecurityPolicies` refines the constraint into PIM security, authentication, and authorization policies.

## `PrivacyConstraint`

`PrivacyConstraint` describes the permitted and expected handling of particular information and data subjects. It connects processing purpose and legal basis with retention, minimization, residency, transfer, and data-subject rights. It does not identify a cloud region or a provider-specific privacy service; those decisions belong in later levels.

### Declared attributes

| Attribute                    | Type and multiplicity        | Meaning                                                                                                                                 | Example                                                            |
| ---------------------------- | ---------------------------- | --------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------ |
| `law`                        | `String` `0..1`              | Names the law or privacy framework relevant to the constraint.                                                                          | `National personal data protection act`                            |
| `jurisdiction`               | `String` `0..1`              | States the jurisdiction in which the obligation applies.                                                                                | `Country X`                                                        |
| `dataSubjectType`            | `String` `0..1`              | Identifies the people or organizations whose data is being processed.                                                                   | `Grant applicant`                                                  |
| `purpose`                    | `String` `0..1`              | Explains why the information is collected or used. When data items are linked, EVL requires this field.                                 | `Assess eligibility and communicate the grant decision.`           |
| `retentionPeriod`            | `String` `0..1`              | States how long the information may or must be kept.                                                                                    | `Seven years after final disbursement`                             |
| `consentRequired`            | `Boolean` `0..1`             | Records whether consent is required for the processing. It must be true when `legalBasis=CONSENT`.                                      | `false`                                                            |
| `deletionRequired`           | `Boolean` `0..1`             | States whether deletion is an obligation under the constraint.                                                                          | `true`                                                             |
| `portabilityRequired`        | `Boolean` `0..1`             | States whether the data subject must be able to receive or transfer the data.                                                           | `false`                                                            |
| `rectificationRequired`      | `Boolean` `0..1`             | States whether the process must support correction of the information.                                                                  | `true`                                                             |
| `dataResidencyRequired`      | `Boolean` `0..1`             | States whether processing or storage must remain within a stated residency boundary.                                                    | `true`                                                             |
| `allowedResidencyArea`       | `String` `0..1`              | Names the allowed country, region, jurisdiction, or organizational residency area when residency is required.                           | `Country X and its approved regions`                               |
| `crossBorderTransferAllowed` | `Boolean` `0..1`             | States whether the information may cross the relevant jurisdictional boundary.                                                          | `false`                                                            |
| `minimizationRule`           | `String` `0..1`              | Explains which collection or use is necessary and which information should be excluded. EVL requires either this or a retention period. | `Collect only the contact details needed to notify the applicant.` |
| `legalBasis`                 | `cimtypes.LegalBasis` `0..1` | Records the legal basis for processing. `UNKNOWN` is not accepted when data items are covered.                                          | `LEGAL_OBLIGATION`                                                 |

### Relationships

| Feature        | Target and multiplicity            | Kind and opposite                                        | Meaning                                                                                                                     |
| -------------- | ---------------------------------- | -------------------------------------------------------- | --------------------------------------------------------------------------------------------------------------------------- |
| `dataItems`    | `cimdomain.InformationItem` `1..*` | reference; opposite `InformationItem.privacyConstraints` | Identifies the information covered by the privacy rule. The lower bound makes a privacy constraint about at least one item. |
| `dataSubjects` | `cimorg.Actor` `0..*`              | reference                                                | Identifies actors representing the data subjects or groups whose information is processed.                                  |

EVL requires purpose and a known legal basis when data items are linked, a retention period or minimization rule in every privacy constraint, and consent when the legal basis is `CONSENT`. A residency requirement receives a warning when no allowed area is stated. `PrivacyConstraint2Policies` creates PIM data-protection and retention policies, and integration closure attaches those policies to schemas, stores, routes, and flows.

## `ComplianceConstraint`

`ComplianceConstraint` identifies a formal control that the modeled elements must satisfy. It is designed to preserve audit reasoning through refinement: the regulation and control identify the obligation, the objective explains what the control achieves, and the evidence field says how compliance can be demonstrated.

### Declared attributes

| Attribute               | Type and multiplicity | Meaning                                                        | Example                                                                            |
| ----------------------- | --------------------- | -------------------------------------------------------------- | ---------------------------------------------------------------------------------- |
| `regulation`            | `String` `0..1`       | Names the regulation, standard, or internal control framework. | `Public grants oversight regulation`                                               |
| `controlId`             | `String` `0..1`       | Gives the identifier of the relevant control or clause.        | `GR-7.2`                                                                           |
| `controlObjective`      | `String` `0..1`       | Explains the result the control is intended to ensure.         | `Every approval decision can be traced to an authorized officer and evidence set.` |
| `evidenceRequired`      | `String` `0..1`       | States the evidence that must be retained or presented.        | `Decision record, actor identity, timestamp, and supporting document references`   |
| `auditFrequency`        | `String` `0..1`       | States how often compliance evidence is reviewed.              | `Quarterly and after material process changes`                                     |
| `externalAuditRequired` | `Boolean` `0..1`      | Records whether an external audit is required.                 | `true`                                                                             |

### Relationships

| Feature          | Target and multiplicity      | Kind      | Meaning                                               |
| ---------------- | ---------------------------- | --------- | ----------------------------------------------------- |
| `scopedElements` | `kernel.ModelElement` `0..*` | reference | Identifies the model elements subject to the control. |

EVL requires regulation, control ID, control objective, and evidence information. It warns when no scope is linked. `ComplianceConstraint2CompliancePolicy` refines the control into a PIM compliance policy, while more specialized data and process transformations preserve its trace.
