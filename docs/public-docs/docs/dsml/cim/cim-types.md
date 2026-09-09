# CIM enumerations

Enumerations in this module are the controlled vocabulary for CIM attributes. Prefer these values over free-form strings wherever an attribute is typed by an enum.

Source: `mde/metamodels/cim/cim-types.emf`.

## Enumeration values

These are closed vocabularies: use exactly one of the listed literals, preserving capitalization. The generator and EVL rules may assign different consequences to different literals.

### `RequirementType`

| Literal           | Practical meaning                                  |
| ----------------- | -------------------------------------------------- |
| `FUNCTIONAL`      | `functional` as a controlled modeling choice.      |
| `BUSINESS_RULE`   | `business_rule` as a controlled modeling choice.   |
| `QUALITY`         | `quality` as a controlled modeling choice.         |
| `SECURITY`        | `security` as a controlled modeling choice.        |
| `PRIVACY`         | `privacy` as a controlled modeling choice.         |
| `COMPLIANCE`      | `compliance` as a controlled modeling choice.      |
| `AUDIT`           | `audit` as a controlled modeling choice.           |
| `REPORTING`       | `reporting` as a controlled modeling choice.       |
| `DATA_GOVERNANCE` | `data_governance` as a controlled modeling choice. |

### `RequirementSourceType`

| Literal           | Practical meaning                                  |
| ----------------- | -------------------------------------------------- |
| `STAKEHOLDER`     | `stakeholder` as a controlled modeling choice.     |
| `REGULATION`      | `regulation` as a controlled modeling choice.      |
| `POLICY_DOCUMENT` | `policy_document` as a controlled modeling choice. |
| `WORKSHOP`        | `workshop` as a controlled modeling choice.        |
| `EXISTING_SYSTEM` | `existing_system` as a controlled modeling choice. |
| `ASSUMPTION`      | `assumption` as a controlled modeling choice.      |
| `AI_ASSISTANT`    | `ai_assistant` as a controlled modeling choice.    |

### `RequirementRelationshipKind`

| Literal          | Practical meaning                                 |
| ---------------- | ------------------------------------------------- |
| `DEPENDS_ON`     | `depends_on` as a controlled modeling choice.     |
| `CONFLICTS_WITH` | `conflicts_with` as a controlled modeling choice. |
| `REFINES`        | `refines` as a controlled modeling choice.        |
| `DUPLICATES`     | `duplicates` as a controlled modeling choice.     |
| `DERIVES_FROM`   | `derives_from` as a controlled modeling choice.   |

### `ActorType`

| Literal                 | Practical meaning                                        |
| ----------------------- | -------------------------------------------------------- |
| `HUMAN`                 | `human` as a controlled modeling choice.                 |
| `ORGANIZATION`          | `organization` as a controlled modeling choice.          |
| `DEPARTMENT`            | `department` as a controlled modeling choice.            |
| `EXTERNAL_ORGANIZATION` | `external_organization` as a controlled modeling choice. |
| `EXTERNAL_SYSTEM`       | `external_system` as a controlled modeling choice.       |
| `TIME`                  | `time` as a controlled modeling choice.                  |
| `REGULATOR`             | `regulator` as a controlled modeling choice.             |

### `TrustLevel`

| Literal              | Practical meaning                                     |
| -------------------- | ----------------------------------------------------- |
| `TRUSTED_INTERNAL`   | `trusted_internal` as a controlled modeling choice.   |
| `PARTIALLY_TRUSTED`  | `partially_trusted` as a controlled modeling choice.  |
| `UNTRUSTED_EXTERNAL` | `untrusted_external` as a controlled modeling choice. |
| `REGULATED_EXTERNAL` | `regulated_external` as a controlled modeling choice. |

### `CapabilityCriticality`

| Literal            | Practical meaning                                   |
| ------------------ | --------------------------------------------------- |
| `SUPPORTING`       | `supporting` as a controlled modeling choice.       |
| `IMPORTANT`        | `important` as a controlled modeling choice.        |
| `CORE`             | `core` as a controlled modeling choice.             |
| `MISSION_CRITICAL` | `mission_critical` as a controlled modeling choice. |

### `DomainRelationshipType`

| Literal          | Practical meaning                                 |
| ---------------- | ------------------------------------------------- |
| `ASSOCIATION`    | `association` as a controlled modeling choice.    |
| `COMPOSITION`    | `composition` as a controlled modeling choice.    |
| `AGGREGATION`    | `aggregation` as a controlled modeling choice.    |
| `GENERALIZATION` | `generalization` as a controlled modeling choice. |
| `DEPENDENCY`     | `dependency` as a controlled modeling choice.     |
| `OWNERSHIP`      | `ownership` as a controlled modeling choice.      |

### `PrimitiveBusinessType`

| Literal       | Practical meaning                              |
| ------------- | ---------------------------------------------- |
| `TEXT`        | `text` as a controlled modeling choice.        |
| `NUMBER`      | `number` as a controlled modeling choice.      |
| `INTEGER`     | `integer` as a controlled modeling choice.     |
| `DECIMAL`     | `decimal` as a controlled modeling choice.     |
| `BOOLEAN`     | `boolean` as a controlled modeling choice.     |
| `DATE`        | `date` as a controlled modeling choice.        |
| `TIME`        | `time` as a controlled modeling choice.        |
| `DATETIME`    | `datetime` as a controlled modeling choice.    |
| `MONEY`       | `money` as a controlled modeling choice.       |
| `EMAIL`       | `email` as a controlled modeling choice.       |
| `PHONE`       | `phone` as a controlled modeling choice.       |
| `ADDRESS`     | `address` as a controlled modeling choice.     |
| `IDENTIFIER`  | `identifier` as a controlled modeling choice.  |
| `ENUMERATION` | `enumeration` as a controlled modeling choice. |
| `OBJECT`      | `object` as a controlled modeling choice.      |
| `LIST`        | `list` as a controlled modeling choice.        |
| `DOCUMENT`    | `document` as a controlled modeling choice.    |
| `BINARY`      | `binary` as a controlled modeling choice.      |

### `IdentityStrategy`

| Literal                 | Practical meaning                                        |
| ----------------------- | -------------------------------------------------------- |
| `NATURAL_KEY`           | `natural_key` as a controlled modeling choice.           |
| `COMPOSITE_NATURAL_KEY` | `composite_natural_key` as a controlled modeling choice. |
| `SURROGATE_KEY`         | `surrogate_key` as a controlled modeling choice.         |
| `EXTERNAL_REFERENCE`    | `external_reference` as a controlled modeling choice.    |
| `UNKNOWN`               | `unknown` as a controlled modeling choice.               |

### `DataKind`

| Literal                 | Practical meaning                                        |
| ----------------------- | -------------------------------------------------------- |
| `PUBLIC`                | `public` as a controlled modeling choice.                |
| `INTERNAL`              | `internal` as a controlled modeling choice.              |
| `CONFIDENTIAL`          | `confidential` as a controlled modeling choice.          |
| `PERSONAL`              | `personal` as a controlled modeling choice.              |
| `SENSITIVE_PERSONAL`    | `sensitive_personal` as a controlled modeling choice.    |
| `FINANCIAL`             | `financial` as a controlled modeling choice.             |
| `HEALTH`                | `health` as a controlled modeling choice.                |
| `AUTHENTICATION_SECRET` | `authentication_secret` as a controlled modeling choice. |
| `REGULATED`             | `regulated` as a controlled modeling choice.             |

### `Identifiability`

| Literal                  | Practical meaning                                         |
| ------------------------ | --------------------------------------------------------- |
| `NON_PERSONAL`           | `non_personal` as a controlled modeling choice.           |
| `PSEUDONYMOUS`           | `pseudonymous` as a controlled modeling choice.           |
| `DIRECTLY_IDENTIFYING`   | `directly_identifying` as a controlled modeling choice.   |
| `INDIRECTLY_IDENTIFYING` | `indirectly_identifying` as a controlled modeling choice. |
| `ANONYMOUS`              | `anonymous` as a controlled modeling choice.              |

### `CommandType`

| Literal                   | Practical meaning                                          |
| ------------------------- | ---------------------------------------------------------- |
| `USER_INTENT`             | `user_intent` as a controlled modeling choice.             |
| `BUSINESS_SYSTEM_INTENT`  | `business_system_intent` as a controlled modeling choice.  |
| `EXTERNAL_SYSTEM_INTENT`  | `external_system_intent` as a controlled modeling choice.  |
| `POLICY_TRIGGERED_INTENT` | `policy_triggered_intent` as a controlled modeling choice. |
| `CORRECTION`              | `correction` as a controlled modeling choice.              |
| `CANCELLATION`            | `cancellation` as a controlled modeling choice.            |

### `QueryType`

| Literal     | Practical meaning                            |
| ----------- | -------------------------------------------- |
| `LOOKUP`    | `lookup` as a controlled modeling choice.    |
| `LIST`      | `list` as a controlled modeling choice.      |
| `SEARCH`    | `search` as a controlled modeling choice.    |
| `REPORT`    | `report` as a controlled modeling choice.    |
| `STATUS`    | `status` as a controlled modeling choice.    |
| `ANALYTICS` | `analytics` as a controlled modeling choice. |

### `FreshnessNeed`

| Literal                 | Practical meaning                                        |
| ----------------------- | -------------------------------------------------------- |
| `REAL_TIME`             | `real_time` as a controlled modeling choice.             |
| `NEAR_REAL_TIME`        | `near_real_time` as a controlled modeling choice.        |
| `EVENTUALLY_CONSISTENT` | `eventually_consistent` as a controlled modeling choice. |
| `PERIODIC`              | `periodic` as a controlled modeling choice.              |
| `HISTORICAL`            | `historical` as a controlled modeling choice.            |

### `EventTimeSemantics`

| Literal            | Practical meaning                                   |
| ------------------ | --------------------------------------------------- |
| `BUSINESS_TIME`    | `business_time` as a controlled modeling choice.    |
| `OBSERVATION_TIME` | `observation_time` as a controlled modeling choice. |
| `RECORDING_TIME`   | `recording_time` as a controlled modeling choice.   |

### `PolicyType`

| Literal         | Practical meaning                                |
| --------------- | ------------------------------------------------ |
| `REACTION`      | `reaction` as a controlled modeling choice.      |
| `GUARD`         | `guard` as a controlled modeling choice.         |
| `DERIVATION`    | `derivation` as a controlled modeling choice.    |
| `AUTHORIZATION` | `authorization` as a controlled modeling choice. |
| `VALIDATION`    | `validation` as a controlled modeling choice.    |
| `COMPLIANCE`    | `compliance` as a controlled modeling choice.    |
| `ESCALATION`    | `escalation` as a controlled modeling choice.    |
| `COMPENSATION`  | `compensation` as a controlled modeling choice.  |

### `ProcessKind`

| Literal                      | Practical meaning                                             |
| ---------------------------- | ------------------------------------------------------------- |
| `STRAIGHT_THROUGH`           | `straight_through` as a controlled modeling choice.           |
| `HUMAN_INVOLVED`             | `human_involved` as a controlled modeling choice.             |
| `LONG_RUNNING`               | `long_running` as a controlled modeling choice.               |
| `CASE_MANAGEMENT`            | `case_management` as a controlled modeling choice.            |
| `SAGA_LIKE_BUSINESS_PROCESS` | `saga_like_business_process` as a controlled modeling choice. |
| `REPORTING_PROCESS`          | `reporting_process` as a controlled modeling choice.          |

### `StepKind`

| Literal                | Practical meaning                                       |
| ---------------------- | ------------------------------------------------------- |
| `COMMAND`              | `command` as a controlled modeling choice.              |
| `QUERY`                | `query` as a controlled modeling choice.                |
| `EVENT`                | `event` as a controlled modeling choice.                |
| `DECISION`             | `decision` as a controlled modeling choice.             |
| `POLICY`               | `policy` as a controlled modeling choice.               |
| `HUMAN_TASK`           | `human_task` as a controlled modeling choice.           |
| `EXTERNAL_INTERACTION` | `external_interaction` as a controlled modeling choice. |
| `WAIT`                 | `wait` as a controlled modeling choice.                 |
| `START`                | `start` as a controlled modeling choice.                |
| `END`                  | `end` as a controlled modeling choice.                  |

### `QualityType`

| Literal           | Practical meaning                                  |
| ----------------- | -------------------------------------------------- |
| `PERFORMANCE`     | `performance` as a controlled modeling choice.     |
| `LATENCY`         | `latency` as a controlled modeling choice.         |
| `AVAILABILITY`    | `availability` as a controlled modeling choice.    |
| `RELIABILITY`     | `reliability` as a controlled modeling choice.     |
| `SCALABILITY`     | `scalability` as a controlled modeling choice.     |
| `SECURITY`        | `security` as a controlled modeling choice.        |
| `PRIVACY`         | `privacy` as a controlled modeling choice.         |
| `AUDITABILITY`    | `auditability` as a controlled modeling choice.    |
| `MAINTAINABILITY` | `maintainability` as a controlled modeling choice. |
| `OPERABILITY`     | `operability` as a controlled modeling choice.     |
| `COST`            | `cost` as a controlled modeling choice.            |
| `DATA_QUALITY`    | `data_quality` as a controlled modeling choice.    |
| `COMPLIANCE`      | `compliance` as a controlled modeling choice.      |
| `USABILITY`       | `usability` as a controlled modeling choice.       |

### `LegalBasis`

| Literal               | Practical meaning                                      |
| --------------------- | ------------------------------------------------------ |
| `CONSENT`             | `consent` as a controlled modeling choice.             |
| `CONTRACT`            | `contract` as a controlled modeling choice.            |
| `LEGAL_OBLIGATION`    | `legal_obligation` as a controlled modeling choice.    |
| `VITAL_INTERESTS`     | `vital_interests` as a controlled modeling choice.     |
| `PUBLIC_TASK`         | `public_task` as a controlled modeling choice.         |
| `LEGITIMATE_INTEREST` | `legitimate_interest` as a controlled modeling choice. |
| `NOT_APPLICABLE`      | `not_applicable` as a controlled modeling choice.      |
| `UNKNOWN`             | `unknown` as a controlled modeling choice.             |

### `InteractionExpectation`

| Literal                       | Practical meaning                                              |
| ----------------------------- | -------------------------------------------------------------- |
| `IMMEDIATE_RESPONSE_EXPECTED` | `immediate_response_expected` as a controlled modeling choice. |
| `RESPONSE_CAN_BE_DELAYED`     | `response_can_be_delayed` as a controlled modeling choice.     |
| `NOTIFICATION_EXPECTED`       | `notification_expected` as a controlled modeling choice.       |
| `BACK_OFFICE_PROCESSING`      | `back_office_processing` as a controlled modeling choice.      |
| `HUMAN_REVIEW_REQUIRED`       | `human_review_required` as a controlled modeling choice.       |

### `ConsistencyExpectation`

| Literal                            | Practical meaning                                                   |
| ---------------------------------- | ------------------------------------------------------------------- |
| `SINGLE_ENTITY`                    | `single_entity` as a controlled modeling choice.                    |
| `MULTI_ENTITY_STRONG_CONSISTENCY`  | `multi_entity_strong_consistency` as a controlled modeling choice.  |
| `EVENTUAL_CONSISTENCY_ACCEPTABLE`  | `eventual_consistency_acceptable` as a controlled modeling choice.  |
| `MANUAL_RECONCILIATION_ACCEPTABLE` | `manual_reconciliation_acceptable` as a controlled modeling choice. |
