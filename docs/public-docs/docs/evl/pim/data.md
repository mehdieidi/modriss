# PIM validation — Data

Data rules connect stores to schemas and access patterns, then connect those choices back to functions and policies. They make transactionality, consistency, indexes, retention, backup, streams, and privacy explicit before a DynamoDB or S3 design is generated.

Source profile: `mde/validation/pim/rules/data.evl`.

## Reading these rules

Each entry preserves the actual EVL guard and check. Read the guard as the applicability boundary, not as part of the invariant: when it is false, the rule is intentionally skipped. The diagnostic is the runtime-facing message emitted by EVL; its final sentence usually contains the repository's recommended repair.

---

## `SensitiveStorageMustBeEncrypted`

**Context:** `PIM!StorageElement`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/pim/rules/data.evl:8`

### Why this rule exists

Checks that sensitive storage must be encrypted. The storage element element owns the evidence for this decision, including storage contains sensitive data, encrypted, display name. At this level, stores, schemas, access patterns, and permissions describe one coherent data design before provider binding; allowing the model through without that evidence would move an unresolved choice into generated infrastructure. The concrete failure this rule prevents is: [PIM-DATA-001] Storage element ' ' contains personal/sensitive data but is not encrypted.

### When it applies

The rule is considered only when the guard below is true. A false guard is intentional: it means this invariant is not relevant to the current variant or modeling situation.

```evl
guard : self.storageContainsSensitiveData()
```

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.encrypted.isTrue()
```

This rule reads: `storageContainsSensitiveData`, `encrypted`, `displayName`.

### Diagnostic and repair

> [PIM-DATA-001] Storage element ' ' contains personal/sensitive data but is not encrypted. Fix: set encrypted to true and attach relevant data protection/security policies.

**How to fix it:**

set encrypted to true and attach relevant data protection/security policies.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---

## `PersistentStorageShouldHaveRetentionPolicy`

**Context:** `PIM!StorageElement`  
**Classification:** Advisory critique (warning)  
**Source:** `mde/validation/pim/rules/data.evl:15`

### Why this rule exists

Advises that persistent storage should have retention policy. This is a review signal about persistent, retention policy, display name, not a cosmetic naming preference. In this part of the model, stores, schemas, access patterns, and permissions describe one coherent data design before provider binding; the warning makes a decision visible while it can still be discussed and changed. The concrete gap is: [PIM-DATA-002] Persistent storage ' ' has no retentionPolicy. Suggested fix: attach RetentionPolicy with retentionPeriod, deletion and legal-hold decisions. If it is left unexplained, a later transformation, generator, or reviewer has to invent an assumption.

### When it applies

The rule is considered only when the guard below is true. A false guard is intentional: it means this invariant is not relevant to the current variant or modeling situation.

```evl
guard : self.persistent.isTrue()
```

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.retentionPolicy.isDefined()
```

This rule reads: `persistent`, `retentionPolicy`, `displayName`.

### Diagnostic and repair

> [PIM-DATA-002] Persistent storage ' ' has no retentionPolicy. Suggested fix: attach RetentionPolicy with retentionPeriod, deletion and legal-hold decisions.

**How to fix it:**

This is advisory rather than a structural blocker. Decide whether the modeled situation genuinely needs the recommendation; if it does, change the referenced model features so the check evaluates to true and record the rationale when the omission is intentional.

A critique does not necessarily make the model invalid. It is a deliberate quality/readiness signal; disposition it by improving the model, documenting why it is acceptable, or carrying the decision into the review record.

---

## `PersistentStorageShouldHaveBackupDecision`

**Context:** `PIM!StorageElement`  
**Classification:** Advisory critique (warning)  
**Source:** `mde/validation/pim/rules/data.evl:22`

### Why this rule exists

Advises that persistent storage should have backup decision. This is a review signal about persistent, backup policy, display name, not a cosmetic naming preference. In this part of the model, stores, schemas, access patterns, and permissions describe one coherent data design before provider binding; the warning makes a decision visible while it can still be discussed and changed. The concrete gap is: [PIM-DATA-003] Persistent storage ' ' has no backupPolicy. Suggested fix: attach BackupPolicy or record why backups are intentionally not required. If it is left unexplained, a later transformation, generator, or reviewer has to invent an assumption.

### When it applies

The rule is considered only when the guard below is true. A false guard is intentional: it means this invariant is not relevant to the current variant or modeling situation.

```evl
guard : self.persistent.isTrue()
```

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.backupPolicy.isDefined()
```

This rule reads: `persistent`, `backupPolicy`, `displayName`.

### Diagnostic and repair

> [PIM-DATA-003] Persistent storage ' ' has no backupPolicy. Suggested fix: attach BackupPolicy or record why backups are intentionally not required.

**How to fix it:**

This is advisory rather than a structural blocker. Decide whether the modeled situation genuinely needs the recommendation; if it does, change the referenced model features so the check evaluates to true and record the rationale when the omission is intentional.

A critique does not necessarily make the model invalid. It is a deliberate quality/readiness signal; disposition it by improving the model, documenting why it is acceptable, or carrying the decision into the review record.

---

## `DataStoreHasDataModel`

**Context:** `PIM!DataStore`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/pim/rules/data.evl:33`

### Why this rule exists

Checks that data store has data model. The data store element owns the evidence for this decision, including owned data models, display name. At this level, stores, schemas, access patterns, and permissions describe one coherent data design before provider binding; allowing the model through without that evidence would move an unresolved choice into generated infrastructure. The concrete failure this rule prevents is: [PIM-DATA-004] DataStore ' ' has no ownedDataModels.

### When it applies

The rule has no guard, so it applies to every instance of this context in the validated model.

There is no guard expression; every instance of the context is checked.

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.ownedDataModels.notEmpty()
```

This rule reads: `ownedDataModels`, `displayName`.

### Diagnostic and repair

> [PIM-DATA-004] DataStore ' ' has no ownedDataModels. Fix: add at least one DataModel describing the stored aggregate/entity/read model.

**How to fix it:**

add at least one DataModel describing the stored aggregate/entity/read model.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---

## `DataStoreHasAccessPattern`

**Context:** `PIM!DataStore`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/pim/rules/data.evl:39`

### Why this rule exists

Checks that data store has access pattern. The data store element owns the evidence for this decision, including access patterns, display name. At this level, stores, schemas, access patterns, and permissions describe one coherent data design before provider binding; allowing the model through without that evidence would move an unresolved choice into generated infrastructure. The concrete failure this rule prevents is: [PIM-DATA-005] DataStore ' ' has no accessPatterns.

### When it applies

The rule has no guard, so it applies to every instance of this context in the validated model.

There is no guard expression; every instance of the context is checked.

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.accessPatterns.notEmpty()
```

This rule reads: `accessPatterns`, `displayName`.

### Diagnostic and repair

> [PIM-DATA-005] DataStore ' ' has no accessPatterns. Fix: add AccessPattern entries that describe query/write shapes used by functions or APIs.

**How to fix it:**

add AccessPattern entries that describe query/write shapes used by functions or APIs.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---

## `TransactionalStoreNeedsTransactionalConsistency`

**Context:** `PIM!DataStore`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/pim/rules/data.evl:45`

### Why this rule exists

Checks that transactional store needs transactional consistency. The data store element owns the evidence for this decision, including transactional, consistency need, display name. At this level, stores, schemas, access patterns, and permissions describe one coherent data design before provider binding; allowing the model through without that evidence would move an unresolved choice into generated infrastructure. The concrete failure this rule prevents is: [PIM-DATA-006] Transactional DataStore ' ' does not declare transactional/strong consistency.

### When it applies

The rule is considered only when the guard below is true. A false guard is intentional: it means this invariant is not relevant to the current variant or modeling situation.

```evl
guard : self.transactional.isTrue()
```

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.consistencyNeed.enumIs("TRANSACTIONAL") or self.consistencyNeed.enumIs("STRONG")
```

This rule reads: `transactional`, `consistencyNeed`, `displayName`.

### Diagnostic and repair

> [PIM-DATA-006] Transactional DataStore ' ' does not declare transactional/strong consistency. Fix: align consistencyNeed with transactional intent or set transactional to false.

**How to fix it:**

align consistencyNeed with transactional intent or set transactional to false.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---

## `SourceOfTruthShouldHavePitrOrBackup`

**Context:** `PIM!DataStore`  
**Classification:** Advisory critique (warning)  
**Source:** `mde/validation/pim/rules/data.evl:52`

### Why this rule exists

Advises that source of truth should have pitr or backup. This is a review signal about owned data models, source of truth, point in time recovery required, backup policy, display name, not a cosmetic naming preference. In this part of the model, stores, schemas, access patterns, and permissions describe one coherent data design before provider binding; the warning makes a decision visible while it can still be discussed and changed. The concrete gap is: [PIM-DATA-007] Source-of-truth DataStore ' ' lacks point-in-time recovery or backup decision. Suggested fix: enable pointInTimeRecoveryRequired or attach BackupPolicy. If it is left unexplained, a later transformation, generator, or reviewer has to invent an assumption.

### When it applies

The rule is considered only when the guard below is true. A false guard is intentional: it means this invariant is not relevant to the current variant or modeling situation.

```evl
guard : self.ownedDataModels.exists(modelItem | modelItem.sourceOfTruth.isTrue())
```

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.pointInTimeRecoveryRequired.isTrue() or self.backupPolicy.isDefined()
```

This rule reads: `ownedDataModels`, `sourceOfTruth`, `pointInTimeRecoveryRequired`, `backupPolicy`, `displayName`.

### Diagnostic and repair

> [PIM-DATA-007] Source-of-truth DataStore ' ' lacks point-in-time recovery or backup decision. Suggested fix: enable pointInTimeRecoveryRequired or attach BackupPolicy.

**How to fix it:**

This is advisory rather than a structural blocker. Decide whether the modeled situation genuinely needs the recommendation; if it does, change the referenced model features so the check evaluates to true and record the rationale when the omission is intentional.

A critique does not necessarily make the model invalid. It is a deliberate quality/readiness signal; disposition it by improving the model, documenting why it is acceptable, or carrying the decision into the review record.

---

## `ObjectStoreWithEventsShouldDeclareEventTypes`

**Context:** `PIM!ObjectStore`  
**Classification:** Advisory critique (warning)  
**Source:** `mde/validation/pim/rules/data.evl:63`

### Why this rule exists

Advises that object store with events should declare event types. This is a review signal about event notification required, emitted events, display name, not a cosmetic naming preference. In this part of the model, stores, schemas, access patterns, and permissions describe one coherent data design before provider binding; the warning makes a decision visible while it can still be discussed and changed. The concrete gap is: [PIM-OBJ-001] ObjectStore ' ' requires event notification but has no emittedEvents. Suggested fix: add EventType entries representing object-created/updated/deleted events. If it is left unexplained, a later transformation, generator, or reviewer has to invent an assumption.

### When it applies

The rule is considered only when the guard below is true. A false guard is intentional: it means this invariant is not relevant to the current variant or modeling situation.

```evl
guard : self.eventNotificationRequired.isTrue()
```

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.emittedEvents.notEmpty()
```

This rule reads: `eventNotificationRequired`, `emittedEvents`, `displayName`.

### Diagnostic and repair

> [PIM-OBJ-001] ObjectStore ' ' requires event notification but has no emittedEvents. Suggested fix: add EventType entries representing object-created/updated/deleted events.

**How to fix it:**

This is advisory rather than a structural blocker. Decide whether the modeled situation genuinely needs the recommendation; if it does, change the referenced model features so the check evaluates to true and record the rationale when the omission is intentional.

A critique does not necessarily make the model invalid. It is a deliberate quality/readiness signal; disposition it by improving the model, documenting why it is acceptable, or carrying the decision into the review record.

---

## `VersionedObjectStoreShouldHaveLifecycleDecision`

**Context:** `PIM!ObjectStore`  
**Classification:** Advisory critique (warning)  
**Source:** `mde/validation/pim/rules/data.evl:70`

### Why this rule exists

Advises that versioned object store should have lifecycle decision. This is a review signal about versioning required, lifecycle policy required, rationale, display name, not a cosmetic naming preference. In this part of the model, stores, schemas, access patterns, and permissions describe one coherent data design before provider binding; the warning makes a decision visible while it can still be discussed and changed. The concrete gap is: [PIM-OBJ-002] ObjectStore ' ' requires versioning without lifecycle policy/rationale. Suggested fix: set lifecyclePolicyRequired or explain retention/lifecycle strategy. If it is left unexplained, a later transformation, generator, or reviewer has to invent an assumption.

### When it applies

The rule is considered only when the guard below is true. A false guard is intentional: it means this invariant is not relevant to the current variant or modeling situation.

```evl
guard : self.versioningRequired.isTrue()
```

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.lifecyclePolicyRequired.isTrue() or self.rationale.hasText()
```

This rule reads: `versioningRequired`, `lifecyclePolicyRequired`, `rationale`, `displayName`.

### Diagnostic and repair

> [PIM-OBJ-002] ObjectStore ' ' requires versioning without lifecycle policy/rationale. Suggested fix: set lifecyclePolicyRequired or explain retention/lifecycle strategy.

**How to fix it:**

This is advisory rather than a structural blocker. Decide whether the modeled situation genuinely needs the recommendation; if it does, change the referenced model features so the check evaluates to true and record the rationale when the omission is intentional.

A critique does not necessarily make the model invalid. It is a deliberate quality/readiness signal; disposition it by improving the model, documenting why it is acceptable, or carrying the decision into the review record.

---

## `DataModelHasSchema`

**Context:** `PIM!DataModel`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/pim/rules/data.evl:81`

### Why this rule exists

Checks that data model has schema. The data model element owns the evidence for this decision, including schema, display name. At this level, stores, schemas, access patterns, and permissions describe one coherent data design before provider binding; allowing the model through without that evidence would move an unresolved choice into generated infrastructure. The concrete failure this rule prevents is: [PIM-DATA-008] DataModel ' ' has no schema.

### When it applies

The rule has no guard, so it applies to every instance of this context in the validated model.

There is no guard expression; every instance of the context is checked.

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.schema.isDefined()
```

This rule reads: `schema`, `displayName`.

### Diagnostic and repair

> [PIM-DATA-008] DataModel ' ' has no schema. Fix: link the Schema that describes the stored data shape.

**How to fix it:**

link the Schema that describes the stored data shape.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---

## `SourceOfTruthCannotBeReadModel`

**Context:** `PIM!DataModel`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/pim/rules/data.evl:87`

### Why this rule exists

Checks that source of truth cannot be read model. The data model element owns the evidence for this decision, including source of truth, read model, display name. At this level, stores, schemas, access patterns, and permissions describe one coherent data design before provider binding; allowing the model through without that evidence would move an unresolved choice into generated infrastructure. The concrete failure this rule prevents is: [PIM-DATA-009] DataModel ' ' is both sourceOfTruth and readModel.

### When it applies

The rule is considered only when the guard below is true. A false guard is intentional: it means this invariant is not relevant to the current variant or modeling situation.

```evl
guard : self.sourceOfTruth.isTrue()
```

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : not self.readModel.isTrue()
```

This rule reads: `sourceOfTruth`, `readModel`, `displayName`.

### Diagnostic and repair

> [PIM-DATA-009] DataModel ' ' is both sourceOfTruth and readModel. Fix: split write/source model and read projection model or choose one role.

**How to fix it:**

split write/source model and read projection model or choose one role.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---

## `DataModelShouldExposeStorageFields`

**Context:** `PIM!DataModel`  
**Classification:** Advisory critique (warning)  
**Source:** `mde/validation/pim/rules/data.evl:94`

### Why this rule exists

Advises that data model should expose storage fields. This is a review signal about storage fields, display name, not a cosmetic naming preference. In this part of the model, stores, schemas, access patterns, and permissions describe one coherent data design before provider binding; the warning makes a decision visible while it can still be discussed and changed. The concrete gap is: [PIM-DATA-010] DataModel ' ' has no storageFields. Suggested fix: add storage fields for keys, indexes, sensitive data and generated storage mapping. If it is left unexplained, a later transformation, generator, or reviewer has to invent an assumption.

### When it applies

The rule has no guard, so it applies to every instance of this context in the validated model.

There is no guard expression; every instance of the context is checked.

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.storageFields.notEmpty()
```

This rule reads: `storageFields`, `displayName`.

### Diagnostic and repair

> [PIM-DATA-010] DataModel ' ' has no storageFields. Suggested fix: add storage fields for keys, indexes, sensitive data and generated storage mapping.

**How to fix it:**

This is advisory rather than a structural blocker. Decide whether the modeled situation genuinely needs the recommendation; if it does, change the referenced model features so the check evaluates to true and record the rationale when the omission is intentional.

A critique does not necessarily make the model invalid. It is a deliberate quality/readiness signal; disposition it by improving the model, documenting why it is acceptable, or carrying the decision into the review record.

---

## `SensitiveDataFieldIsClassified`

**Context:** `PIM!DataField`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/pim/rules/data.evl:104`

### Why this rule exists

Checks that sensitive data field is classified. The data field element owns the evidence for this decision, including personal data, sensitive data, classification, display name. At this level, stores, schemas, access patterns, and permissions describe one coherent data design before provider binding; allowing the model through without that evidence would move an unresolved choice into generated infrastructure. The concrete failure this rule prevents is: [PIM-DATA-011] DataField ' ' is personal/sensitive but has no classification.

### When it applies

The rule is considered only when the guard below is true. A false guard is intentional: it means this invariant is not relevant to the current variant or modeling situation.

```evl
guard : self.personalData.isTrue() or self.sensitiveData.isTrue()
```

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.classification.hasText()
```

This rule reads: `personalData`, `sensitiveData`, `classification`, `displayName`.

### Diagnostic and repair

> [PIM-DATA-011] DataField ' ' is personal/sensitive but has no classification. Fix: set a classification that can drive protection, retention and masking decisions.

**How to fix it:**

set a classification that can drive protection, retention and masking decisions.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---

## `IdentifierFieldShouldBeRequired`

**Context:** `PIM!DataField`  
**Classification:** Advisory critique (warning)  
**Source:** `mde/validation/pim/rules/data.evl:111`

### Why this rule exists

Advises that identifier field should be required. This is a review signal about identifier, required, display name, not a cosmetic naming preference. In this part of the model, stores, schemas, access patterns, and permissions describe one coherent data design before provider binding; the warning makes a decision visible while it can still be discussed and changed. The concrete gap is: [PIM-DATA-012] Identifier field ' ' is not marked required. Suggested fix: set required to true so generated persistence logic treats it as mandatory. If it is left unexplained, a later transformation, generator, or reviewer has to invent an assumption.

### When it applies

The rule is considered only when the guard below is true. A false guard is intentional: it means this invariant is not relevant to the current variant or modeling situation.

```evl
guard : self.identifier.isTrue()
```

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.required.isTrue()
```

This rule reads: `identifier`, `required`, `displayName`.

### Diagnostic and repair

> [PIM-DATA-012] Identifier field ' ' is not marked required. Suggested fix: set required to true so generated persistence logic treats it as mandatory.

**How to fix it:**

This is advisory rather than a structural blocker. Decide whether the modeled situation genuinely needs the recommendation; if it does, change the referenced model features so the check evaluates to true and record the rationale when the omission is intentional.

A critique does not necessarily make the model invalid. It is a deliberate quality/readiness signal; disposition it by improving the model, documenting why it is acceptable, or carrying the decision into the review record.

---

## `AccessPatternOperationDefined`

**Context:** `PIM!AccessPattern`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/pim/rules/data.evl:122`

### Why this rule exists

Checks that access pattern operation defined. The access pattern element owns the evidence for this decision, including operation, display name. At this level, stores, schemas, access patterns, and permissions describe one coherent data design before provider binding; allowing the model through without that evidence would move an unresolved choice into generated infrastructure. The concrete failure this rule prevents is: [PIM-DATA-012A] AccessPattern ' ' has no operation.

### When it applies

The rule has no guard, so it applies to every instance of this context in the validated model.

There is no guard expression; every instance of the context is checked.

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.`operation`.isDefined()
```

This rule reads: `operation`, `displayName`.

### Diagnostic and repair

> [PIM-DATA-012A] AccessPattern ' ' has no operation. Fix: choose a DataAccessMode literal such as READ, WRITE, READ_WRITE, APPEND or DELETE.

**How to fix it:**

choose a DataAccessMode literal such as READ, WRITE, READ_WRITE, APPEND or DELETE.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---

## `AccessPatternNamesQueryShape`

**Context:** `PIM!AccessPattern`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/pim/rules/data.evl:128`

### Why this rule exists

Checks that access pattern names query shape. The access pattern element owns the evidence for this decision, including has query shape, display name. At this level, stores, schemas, access patterns, and permissions describe one coherent data design before provider binding; allowing the model through without that evidence would move an unresolved choice into generated infrastructure. The concrete failure this rule prevents is: [PIM-DATA-013] AccessPattern ' ' does not describe operation/query shape.

### When it applies

The rule has no guard, so it applies to every instance of this context in the validated model.

There is no guard expression; every instance of the context is checked.

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.hasQueryShape()
```

This rule reads: `hasQueryShape`, `displayName`.

### Diagnostic and repair

> [PIM-DATA-013] AccessPattern ' ' does not describe operation/query shape. Fix: fill operation, queryBy, sortBy, filterBy or projection.

**How to fix it:**

fill operation, queryBy, sortBy, filterBy or projection.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---

## `HighFrequencyAccessPatternShouldHaveIndex`

**Context:** `PIM!AccessPattern`  
**Classification:** Advisory critique (warning)  
**Source:** `mde/validation/pim/rules/data.evl:134`

### Why this rule exists

Advises that high frequency access pattern should have index. This is a review signal about high frequency, high cardinality, index support key, display name, not a cosmetic naming preference. In this part of the model, stores, schemas, access patterns, and permissions describe one coherent data design before provider binding; the warning makes a decision visible while it can still be discussed and changed. The concrete gap is: [PIM-DATA-014] High-frequency/high-cardinality AccessPattern ' ' has no supporting IndexCandidate. Suggested fix: add an index candidate and link it to this access pattern. If it is left unexplained, a later transformation, generator, or reviewer has to invent an assumption.

### When it applies

The rule is considered only when the guard below is true. A false guard is intentional: it means this invariant is not relevant to the current variant or modeling situation.

```evl
guard : self.highFrequency.isTrue() or self.highCardinality.isTrue()
```

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : indexedAccessPatternKeys().includes(self.indexSupportKey())
```

This rule reads: `highFrequency`, `highCardinality`, `indexSupportKey`, `displayName`.

### Diagnostic and repair

> [PIM-DATA-014] High-frequency/high-cardinality AccessPattern ' ' has no supporting IndexCandidate. Suggested fix: add an index candidate and link it to this access pattern.

**How to fix it:**

This is advisory rather than a structural blocker. Decide whether the modeled situation genuinely needs the recommendation; if it does, change the referenced model features so the check evaluates to true and record the rationale when the omission is intentional.

A critique does not necessarily make the model invalid. It is a deliberate quality/readiness signal; disposition it by improving the model, documenting why it is acceptable, or carrying the decision into the review record.

---

## `ProductionIndexSupportsAccessPattern`

**Context:** `PIM!IndexCandidate`  
**Classification:** Advisory critique (warning)  
**Source:** `mde/validation/pim/rules/data.evl:145`

### Why this rule exists

Advises that production index supports access pattern. This is a review signal about required for production, supports access patterns, partition key field, display name, not a cosmetic naming preference. In this part of the model, stores, schemas, access patterns, and permissions describe one coherent data design before provider binding; the warning makes a decision visible while it can still be discussed and changed. The concrete gap is: [PIM-DATA-015] Production index candidate ' ' has no access pattern or partition key. Suggested fix: link supportsAccessPatterns and define partitionKeyField. If it is left unexplained, a later transformation, generator, or reviewer has to invent an assumption.

### When it applies

The rule is considered only when the guard below is true. A false guard is intentional: it means this invariant is not relevant to the current variant or modeling situation.

```evl
guard : self.requiredForProduction.isTrue()
```

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.supportsAccessPatterns.notEmpty() and self.partitionKeyField.hasText()
```

This rule reads: `requiredForProduction`, `supportsAccessPatterns`, `partitionKeyField`, `displayName`.

### Diagnostic and repair

> [PIM-DATA-015] Production index candidate ' ' has no access pattern or partition key. Suggested fix: link supportsAccessPatterns and define partitionKeyField.

**How to fix it:**

This is advisory rather than a structural blocker. Decide whether the modeled situation genuinely needs the recommendation; if it does, change the referenced model features so the check evaluates to true and record the rationale when the omission is intentional.

A critique does not necessarily make the model invalid. It is a deliberate quality/readiness signal; disposition it by improving the model, documenting why it is acceptable, or carrying the decision into the review record.

---

## `DataAccessConsistentWithFunctionRefs`

**Context:** `PIM!DataAccess`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/pim/rules/data.evl:156`

### Why this rule exists

Checks that data access consistent with function refs. The data access element owns the evidence for this decision, including mode, function, store, display name. At this level, stores, schemas, access patterns, and permissions describe one coherent data design before provider binding; allowing the model through without that evidence would move an unresolved choice into generated infrastructure. The concrete failure this rule prevents is: [PIM-DATA-015A] DataAccess ' ' is not reflected in Function.reads/writes.

### When it applies

The rule has no guard, so it applies to every instance of this context in the validated model.

There is no guard expression; every instance of the context is checked.

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : (self.mode = PIM!DataAccessMode#READ and self.`function`.reads.includes(self.store)) or ((self.mode = PIM!DataAccessMode#WRITE or self.mode = PIM!DataAccessMode#APPEND or self.mode = PIM!DataAccessMode#DELETE) and self.`function`.writes.includes(self.store)) or (self.mode = PIM!DataAccessMode#READ_WRITE and self.`function`.reads.includes(self.store) and self.`function`.writes.includes(self.store))
```

This rule reads: `mode`, `function`, `store`, `displayName`.

### Diagnostic and repair

> [PIM-DATA-015A] DataAccess ' ' is not reflected in Function.reads/writes. Fix: synchronize DataAccess.mode with the function shortcut references.

**How to fix it:**

synchronize DataAccess.mode with the function shortcut references.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---

## `GeneratesOrReferencesPermission`

**Context:** `PIM!DataAccess`  
**Classification:** Advisory critique (warning)  
**Source:** `mde/validation/pim/rules/data.evl:165`

### Why this rule exists

Advises that generates or references permission. This is a review signal about purpose, function, store, display name, not a cosmetic naming preference. In this part of the model, stores, schemas, access patterns, and permissions describe one coherent data design before provider binding; the warning makes a decision visible while it can still be discussed and changed. The concrete gap is: [PIM-DATA-015B] DataAccess ' ' lacks enough function/store/purpose information to generate or review permissions. Suggested fix: complete the access intent before PSM IAM generation. If it is left unexplained, a later transformation, generator, or reviewer has to invent an assumption.

### When it applies

The rule has no guard, so it applies to every instance of this context in the validated model.

There is no guard expression; every instance of the context is checked.

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.purpose.hasText() and self.`function`.isDefined() and self.store.isDefined()
```

This rule reads: `purpose`, `function`, `store`, `displayName`.

### Diagnostic and repair

> [PIM-DATA-015B] DataAccess ' ' lacks enough function/store/purpose information to generate or review permissions. Suggested fix: complete the access intent before PSM IAM generation.

**How to fix it:**

This is advisory rather than a structural blocker. Decide whether the modeled situation genuinely needs the recommendation; if it does, change the referenced model features so the check evaluates to true and record the rationale when the omission is intentional.

A critique does not necessarily make the model invalid. It is a deliberate quality/readiness signal; disposition it by improving the model, documenting why it is acceptable, or carrying the decision into the review record.

---

## `DataAccessHasPurpose`

**Context:** `PIM!DataAccess`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/pim/rules/data.evl:171`

### Why this rule exists

Checks that data access has purpose. The data access element owns the evidence for this decision, including purpose, display name. At this level, stores, schemas, access patterns, and permissions describe one coherent data design before provider binding; allowing the model through without that evidence would move an unresolved choice into generated infrastructure. The concrete failure this rule prevents is: [PIM-DATA-016] DataAccess ' ' has no purpose.

### When it applies

The rule has no guard, so it applies to every instance of this context in the validated model.

There is no guard expression; every instance of the context is checked.

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.purpose.hasText()
```

This rule reads: `purpose`, `displayName`.

### Diagnostic and repair

> [PIM-DATA-016] DataAccess ' ' has no purpose. Fix: explain why the function accesses this store and what business operation it supports.

**How to fix it:**

explain why the function accesses this store and what business operation it supports.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---
