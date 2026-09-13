# CIM → PIM: Domain Data

Domain-data refinement turns concepts into the schemas and stores that PIM behavior can actually use. Entities become object-shaped schemas, value objects become reusable nested structures, aggregates become stores, and classification/privacy/compliance decisions become data-protection policies. Relationship fields and access consequences are completed after the initial objects exist.

Source module: `mde/transformations/cim-to-pim/domain-data.etl`.

## Reading this page

A transformation rule determines whether a source element contributes to the target model and how it is mapped. Use the guard to understand routing and the target table to see the model-level result. The behavior section records important semantic side effects. Trace and manual-decision information identifies work for review and later phases.

---

## Supporting ETL operations

Supporting operations also shape the transformation. They derive defaults, create secondary resources, cache correspondences, and resolve relationships after the main rule runs.

| Operation                        | Role                                        | Source                                              |
| -------------------------------- | ------------------------------------------- | --------------------------------------------------- |
| `aggregateItems`                 | Computes aggregate items.                   | `mde/transformations/cim-to-pim/domain-data.etl:9`  |
| `aggregateContainsSensitiveData` | Computes aggregate contains sensitive data. | `mde/transformations/cim-to-pim/domain-data.etl:25` |
| `aggregateContainsPersonalData`  | Computes aggregate contains personal data.  | `mde/transformations/cim-to-pim/domain-data.etl:30` |
| `storageFieldNameFor`            | Computes storage field name for.            | `mde/transformations/cim-to-pim/domain-data.etl:35` |

---

## `Entity2Schema`

**Source:** `e` to `CIMDOMAIN!DomainEntity`  
**Target:** `s` to `CONTRACTS!Schema`  
**Source location:** `mde/transformations/cim-to-pim/domain-data.etl:40`

### Why this rule exists

An entity's identity and owned attributes become a reusable object schema. The transformation also preserves aggregate context and sensitivity signals, which later determine storage keys, data-access policy, and protection requirements; it is not merely a name-to-name class copy.

### When the rule runs

There is no explicit guard, so every source instance of the declared type is eligible for this mapping. Eligibility does not guarantee that every optional relationship or downstream target can be resolved.

### What it creates

- `s` (`CONTRACTS!Schema`): Generated schema (s).
- Secondary objects created in the rule body: `CONTRACTS!SchemaConstraint`.

### Important behavior encoded in the rule

The rule directly assigns: `s.id`, `s.name`, `s.schemaKind`, `s.semanticVersion`, `s.compatibility`, `s.additionalPropertiesAllowed`, `s.generatedFromCimInformation`, `c.id`, `c.name`, `c.expressionLanguage`, `c.expression`, `c.message`, `c.severity`.
Trace identifiers emitted here: `TR-040`. These identifiers are useful when following the generated element back to the originating CIM/PIM decision.

### How to troubleshoot or repair it

Verify that the domain entity is present and semantically complete, then rerun the transformation. If the target is missing or incomplete, inspect the source attributes named in the rule body, the referenced helper operation, and the post-phase that resolves its relationships before changing the ETL itself.

The trace record is the best first diagnostic: it tells you whether the target was created, which transformation rule claimed it, and what source element it came from.

### Authoritative source

See the complete ETL rule at `mde/transformations/cim-to-pim/domain-data.etl:40`. The purpose and observable effects of the rule are summarized here. Consult the ETL body for exact assignments and helper calls.

---

## `ValueObject2Schema`

**Source:** `v` to `CIMDOMAIN!ValueObject`  
**Target:** `s` to `CONTRACTS!Schema`  
**Source location:** `mde/transformations/cim-to-pim/domain-data.etl:75`

### Why this rule exists

A value object becomes a nested or reusable schema without identity semantics. Its immutability and equality meaning belong in the schema's description and fields, preventing a value such as Money or Address from accidentally becoming an independently mutable resource.

### When the rule runs

There is no explicit guard, so every source instance of the declared type is eligible for this mapping. Eligibility does not guarantee that every optional relationship or downstream target can be resolved.

### What it creates

- `s` (`CONTRACTS!Schema`): Generated schema (s).

### Important behavior encoded in the rule

The rule directly assigns: `s.id`, `s.name`, `s.schemaKind`, `s.semanticVersion`, `s.compatibility`, `s.additionalPropertiesAllowed`, `s.generatedFromCimInformation`.
Trace identifiers emitted here: `TR-040`. These identifiers are useful when following the generated element back to the originating CIM/PIM decision.

### How to troubleshoot or repair it

Verify that the value object is present and semantically complete, then rerun the transformation. If the target is missing or incomplete, inspect the source attributes named in the rule body, the referenced helper operation, and the post-phase that resolves its relationships before changing the ETL itself.

The trace record is the best first diagnostic: it tells you whether the target was created, which transformation rule claimed it, and what source element it came from.

### Authoritative source

See the complete ETL rule at `mde/transformations/cim-to-pim/domain-data.etl:75`. The purpose and observable effects of the rule are summarized here. Consult the ETL body for exact assignments and helper calls.

---

## `DomainRelationship2SchemaReference`

**Source:** `rel` to `CIMDOMAIN!DomainRelationship`  
**Target:** `field` to `CONTRACTS!SchemaField`, `constraint` to `CONTRACTS!SchemaConstraint`  
**Source location:** `mde/transformations/cim-to-pim/domain-data.etl:95`

### Why this rule exists

A domain relationship becomes a schema reference field because PIM contracts need to express how one concept points to another. The rule carries role and multiplicity information so relationship direction is not lost when domain diagrams become JSON-shaped contracts.

### When the rule runs

There is no explicit guard, so every source instance of the declared type is eligible for this mapping. Eligibility does not guarantee that every optional relationship or downstream target can be resolved.

### What it creates

- `field` (`CONTRACTS!SchemaField`): Generated schema field (field).
- `constraint` (`CONTRACTS!SchemaConstraint`): Generated schema constraint (constraint).

### Important behavior encoded in the rule

The rule directly assigns: `field.id`, `field.name`, `field.fieldType`, `field.required`, `field.nullable`, `field.array`, `field.descriptionForConsumers`, `field.objectSchema`, `constraint.id`, `constraint.name`, `constraint.expressionLanguage`, `constraint.expression`, `constraint.message`, `constraint.severity`.
Trace identifiers emitted here: `TR-040`. These identifiers are useful when following the generated element back to the originating CIM/PIM decision.
Manual decisions raised by this rule: `TR-040`. These are intentional hand-off points. Resolve them in the model review/readiness workflow; they do not indicate transformation failure.

### How to troubleshoot or repair it

Start with the manual decision(s) `TR-040` and complete the requested provider or business choice. Then re-run the transformation and validate the resulting target. If the rule did not produce an object, inspect whether the domain relationship actually satisfies its guard and whether the required upstream correspondence exists.

The trace record is the best first diagnostic: it tells you whether the target was created, which transformation rule claimed it, and what source element it came from.

### Authoritative source

See the complete ETL rule at `mde/transformations/cim-to-pim/domain-data.etl:95`. The purpose and observable effects of the rule are summarized here. Consult the ETL body for exact assignments and helper calls.

---

## `Aggregate2DataStore`

**Source:** `ag` to `CIMDOMAIN!AggregateCandidate`  
**Target:** `store` to `DATA!DataStore`, `dataModel` to `DATA!DataModel`  
**Source location:** `mde/transformations/cim-to-pim/domain-data.etl:175`

### Why this rule exists

An aggregate is the strongest CIM signal for a persistence boundary. This rule creates a PIM data store with source-of-truth, consistency, retention, backup, and sensitivity decisions derived from the aggregate and its members, giving later access-pattern and provider rules something concrete to operate on.

### When the rule runs

There is no explicit guard, so every source instance of the declared type is eligible for this mapping. Eligibility does not guarantee that every optional relationship or downstream target can be resolved.

### What it creates

- `store` (`DATA!DataStore`): Generated data store (store).
- `dataModel` (`DATA!DataModel`): Generated data model (dataModel).
- Secondary objects created in the rule body: `DATA!AccessPattern`, `DATA!IndexCandidate`.

### Important behavior encoded in the rule

The rule directly assigns: `store.id`, `store.name`, `store.persistent`, `store.encrypted`, `store.containsPersonalData`, `store.storeKind`, `store.consistencyNeed`, `store.transactional`, `store.readOptimized`, `store.writeOptimized`, `store.appendOnly`, `store.changeStreamRequired`, `store.pointInTimeRecoveryRequired`, `store.expectedDataVolume`, `store.expectedAccessRate`, `dataModel.id` ….
Trace identifiers emitted here: `TR-050`. These identifiers are useful when following the generated element back to the originating CIM/PIM decision.
Manual decisions raised by this rule: `TR-050`. These are intentional hand-off points. Resolve them in the model review/readiness workflow; they do not indicate transformation failure.

### How to troubleshoot or repair it

Start with the manual decision(s) `TR-050` and complete the requested provider or business choice. Then re-run the transformation and validate the resulting target. If the rule did not produce an object, inspect whether the aggregate candidate actually satisfies its guard and whether the required upstream correspondence exists.

The trace record is the best first diagnostic: it tells you whether the target was created, which transformation rule claimed it, and what source element it came from.

### Authoritative source

See the complete ETL rule at `mde/transformations/cim-to-pim/domain-data.etl:175`. The purpose and observable effects of the rule are summarized here. Consult the ETL body for exact assignments and helper calls.

---

## `DataClassification2DataProtectionPolicy`

**Source:** `dc` to `CIMDOMAIN!DataClassification`  
**Target:** `pol` to `POLICY!DataProtectionPolicy`  
**Source location:** `mde/transformations/cim-to-pim/domain-data.etl:262`

### Why this rule exists

A classification is converted into an enforceable PIM protection expectation. The output connects sensitive information to encryption, masking, access, retention, or residency intent so classification does not remain descriptive metadata that no generated architecture can act on.

### When the rule runs

There is no explicit guard, so every source instance of the declared type is eligible for this mapping. Eligibility does not guarantee that every optional relationship or downstream target can be resolved.

### What it creates

- `pol` (`POLICY!DataProtectionPolicy`): Generated data protection policy (pol).

### Important behavior encoded in the rule

The rule directly assigns: `pol.id`, `pol.name`, `pol.policyScope`, `pol.productionRequired`, `pol.classification`, `pol.encryptionRequired`, `pol.maskingRequired`, `pol.tokenizationRequired`, `pol.accessAuditRequired`, `pol.deletionRequired`, `pol.retentionPeriod`, `pol.residencyRequirement`.
Trace identifiers emitted here: `TR-060`. These identifiers are useful when following the generated element back to the originating CIM/PIM decision.
Manual decisions raised by this rule: `TR-060`. These are intentional hand-off points. Resolve them in the model review/readiness workflow; they do not indicate transformation failure.

### How to troubleshoot or repair it

Start with the manual decision(s) `TR-060` and complete the requested provider or business choice. Then re-run the transformation and validate the resulting target. If the rule did not produce an object, inspect whether the data classification actually satisfies its guard and whether the required upstream correspondence exists.

The trace record is the best first diagnostic: it tells you whether the target was created, which transformation rule claimed it, and what source element it came from.

### Authoritative source

See the complete ETL rule at `mde/transformations/cim-to-pim/domain-data.etl:262`. The purpose and observable effects of the rule are summarized here. Consult the ETL body for exact assignments and helper calls.

---

## `PrivacyConstraint2Policies`

**Source:** `pc` to `CIMGOV!PrivacyConstraint`  
**Target:** `dp` to `POLICY!DataProtectionPolicy`, `rp` to `POLICY!RetentionPolicy`  
**Source location:** `mde/transformations/cim-to-pim/domain-data.etl:288`

### Why this rule exists

Privacy constraints may affect collection, processing, retention, minimization, consent, or residency in different ways. This rule emits the corresponding PIM data-protection policies and attaches affected elements, preserving the legal/business reason while allowing implementation choices later.

### When the rule runs

There is no explicit guard, so every source instance of the declared type is eligible for this mapping. Eligibility does not guarantee that every optional relationship or downstream target can be resolved.

### What it creates

- `dp` (`POLICY!DataProtectionPolicy`): Generated data protection policy (dp).
- `rp` (`POLICY!RetentionPolicy`): Generated retention policy (rp).

### Important behavior encoded in the rule

The rule directly assigns: `dp.id`, `dp.name`, `dp.policyScope`, `dp.productionRequired`, `dp.classification`, `dp.encryptionRequired`, `dp.maskingRequired`, `dp.accessAuditRequired`, `dp.deletionRequired`, `dp.retentionPeriod`, `dp.residencyRequirement`, `rp.id`, `rp.name`, `rp.retentionPeriod`, `rp.deletionAfterRetention`, `rp.legalHoldPossible` ….
Trace identifiers emitted here: `TR-060`. These identifiers are useful when following the generated element back to the originating CIM/PIM decision.
Manual decisions raised by this rule: `TR-060`. These are intentional hand-off points. Resolve them in the model review/readiness workflow; they do not indicate transformation failure.

### How to troubleshoot or repair it

Start with the manual decision(s) `TR-060` and complete the requested provider or business choice. Then re-run the transformation and validate the resulting target. If the rule did not produce an object, inspect whether the privacy constraint actually satisfies its guard and whether the required upstream correspondence exists.

The trace record is the best first diagnostic: it tells you whether the target was created, which transformation rule claimed it, and what source element it came from.

### Authoritative source

See the complete ETL rule at `mde/transformations/cim-to-pim/domain-data.etl:288`. The purpose and observable effects of the rule are summarized here. Consult the ETL body for exact assignments and helper calls.

---

## `ComplianceConstraint2CompliancePolicy`

**Source:** `cc` to `CIMGOV!ComplianceConstraint`  
**Target:** `pol` to `POLICY!CompliancePolicy`  
**Source location:** `mde/transformations/cim-to-pim/domain-data.etl:328`

### Why this rule exists

A compliance constraint becomes a PIM compliance policy with auditable scope and evidence expectations. This keeps a regulation from disappearing between CIM and implementation and gives readiness reporting an explicit policy object to inspect.

### When the rule runs

There is no explicit guard, so every source instance of the declared type is eligible for this mapping. Eligibility does not guarantee that every optional relationship or downstream target can be resolved.

### What it creates

- `pol` (`POLICY!CompliancePolicy`): Generated compliance policy (pol).

### Important behavior encoded in the rule

The rule directly assigns: `pol.id`, `pol.name`, `pol.policyScope`, `pol.productionRequired`, `pol.regulation`, `pol.controlId`, `pol.evidenceType`, `pol.auditReportRequired`.
Trace identifiers emitted here: `TR-060`. These identifiers are useful when following the generated element back to the originating CIM/PIM decision.
Manual decisions raised by this rule: `TR-060`. These are intentional hand-off points. Resolve them in the model review/readiness workflow; they do not indicate transformation failure.

### How to troubleshoot or repair it

Start with the manual decision(s) `TR-060` and complete the requested provider or business choice. Then re-run the transformation and validate the resulting target. If the rule did not produce an object, inspect whether the compliance constraint actually satisfies its guard and whether the required upstream correspondence exists.

The trace record is the best first diagnostic: it tells you whether the target was created, which transformation rule claimed it, and what source element it came from.

### Authoritative source

See the complete ETL rule at `mde/transformations/cim-to-pim/domain-data.etl:328`. The purpose and observable effects of the rule are summarized here. Consult the ETL body for exact assignments and helper calls.

---
