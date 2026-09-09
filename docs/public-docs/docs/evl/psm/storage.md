# AWS PSM validation — Storage

DynamoDB and S3 rules validate key coverage, capacity-mode consistency, index definitions, TTL/encryption/backup choices, public-access blocking, notifications, and replication. These checks are aimed at preventing deployable templates that still lose or expose data.

Source profile: `mde/validation/psm/rules/storage.evl`.

## Reading these rules

Each entry preserves the actual EVL guard and check. Read the guard as the applicability boundary, not as part of the invariant: when it is false, the rule is intentionally skipped. The diagnostic is the runtime-facing message emitted by EVL; its final sentence usually contains the repository's recommended repair.

---

## `DynamoTableHasValidPrimaryKeySchema`

**Context:** `AWSPSM!DynamoDbTable`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/psm/rules/storage.evl:8`

### Why this rule exists

DynamoDB's primary key is the lookup and distribution contract of the table. Exactly one partition key and at most one sort key keeps the modeled access identity compatible with the service's data model.

### When it applies

The rule has no guard, so it applies to every instance of this context in the validated model.

There is no guard expression; every instance of the context is checked.

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.keySchema.one(keyItem | keyItem.keyType = AWSPSMENUMS!DynamoDbKeyType#HASH) and self.keySchema.atMostNMatch(keyItem | keyItem.keyType = AWSPSMENUMS!DynamoDbKeyType#RANGE, 1)
```

This rule reads: `keySchema`, `keyType`, `resourceLabel`.

### Diagnostic and repair

> DynamoDB table must have exactly one HASH key and at most one RANGE key. Fix: adjust keySchema accordingly.

**How to fix it:**

adjust keySchema accordingly.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---

## `DynamoAttributesCoverTableKeys`

**Context:** `AWSPSM!DynamoDbTable`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/psm/rules/storage.evl:15`

### Why this rule exists

Key schema names and attribute declarations are two halves of one DynamoDB definition. If they diverge, the generated table cannot represent the modeled partitioning and lookup behavior.

### When it applies

The rule has no guard, so it applies to every instance of this context in the validated model.

There is no guard expression; every instance of the context is checked.

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.keySchema.forAll(keyItem | self.attributeDefinitions.exists(attributeItem | attributeItem.attributeName = keyItem.attributeName))
```

This rule reads: `keySchema`, `attributeDefinitions`, `attributeName`, `resourceLabel`.

### Diagnostic and repair

> DynamoDB table has key schema attributes not declared in attributeDefinitions. Fix: add DynamoDbAttributeDefinition entries for every key attribute.

**How to fix it:**

add DynamoDbAttributeDefinition entries for every key attribute.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---

## `DynamoIndexKeysAreCoveredByAttributes`

**Context:** `AWSPSM!DynamoDbTable`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/psm/rules/storage.evl:22`

### Why this rule exists

An index introduces another access path, so every index key must exist in the table's declared attribute set. This catches an index that looks complete in the model but cannot be emitted as valid AWS configuration.

### When it applies

The rule has no guard, so it applies to every instance of this context in the validated model.

There is no guard expression; every instance of the context is checked.

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.localSecondaryIndexes.forAll(indexItem | indexItem.keySchema.forAll(keyItem | self.attributeDefinitions.exists(attributeItem | attributeItem.attributeName = keyItem.attributeName))) and self.globalSecondaryIndexes.forAll(indexItem | indexItem.keySchema.forAll(keyItem | self.attributeDefinitions.exists(attributeItem | attributeItem.attributeName = keyItem.attributeName)))
```

This rule reads: `localSecondaryIndexes`, `keySchema`, `attributeDefinitions`, `attributeName`, `globalSecondaryIndexes`, `resourceLabel`.

### Diagnostic and repair

> DynamoDB table has an index key not declared in attributeDefinitions. Fix: add matching attribute definitions for all LSI/GSI key attributes.

**How to fix it:**

add matching attribute definitions for all LSI/GSI key attributes.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---

## `ProvisionedModeNeedsThroughput`

**Context:** `AWSPSM!DynamoDbTable`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/psm/rules/storage.evl:31`

### Why this rule exists

Checks that provisioned mode needs throughput. The dynamo db table element owns the evidence for this decision, including billing mode, provisioned throughput, resource label. At this level, DynamoDB and S3 resources protect key correctness, recoverability, encryption, and public-access boundaries; allowing the model through without that evidence would move an unresolved choice into generated infrastructure. The concrete failure this rule prevents is: DynamoDB table uses PROVISIONED billing but has no provisionedThroughput.

### When it applies

The rule is considered only when the guard below is true. A false guard is intentional: it means this invariant is not relevant to the current variant or modeling situation.

```evl
guard : self.billingMode = AWSPSMENUMS!DynamoDbBillingMode#PROVISIONED
```

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.provisionedThroughput.isDefined()
```

This rule reads: `billingMode`, `provisionedThroughput`, `resourceLabel`.

### Diagnostic and repair

> DynamoDB table uses PROVISIONED billing but has no provisionedThroughput. Fix: add readCapacityUnits/writeCapacityUnits or switch to PAY_PER_REQUEST.

**How to fix it:**

add readCapacityUnits/writeCapacityUnits or switch to PAY_PER_REQUEST.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---

## `PayPerRequestDoesNotUseProvisionedThroughput`

**Context:** `AWSPSM!DynamoDbTable`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/psm/rules/storage.evl:38`

### Why this rule exists

Checks that pay per request does not use provisioned throughput. The dynamo db table element owns the evidence for this decision, including billing mode, provisioned throughput, resource label. At this level, DynamoDB and S3 resources protect key correctness, recoverability, encryption, and public-access boundaries; allowing the model through without that evidence would move an unresolved choice into generated infrastructure. The concrete failure this rule prevents is: DynamoDB table uses PAY_PER_REQUEST but also defines provisionedThroughput.

### When it applies

The rule is considered only when the guard below is true. A false guard is intentional: it means this invariant is not relevant to the current variant or modeling situation.

```evl
guard : self.billingMode = AWSPSMENUMS!DynamoDbBillingMode#PAY_PER_REQUEST
```

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.provisionedThroughput.isUndefined()
```

This rule reads: `billingMode`, `provisionedThroughput`, `resourceLabel`.

### Diagnostic and repair

> DynamoDB table uses PAY_PER_REQUEST but also defines provisionedThroughput. Fix: remove provisionedThroughput or switch billingMode to PROVISIONED.

**How to fix it:**

remove provisionedThroughput or switch billingMode to PROVISIONED.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---

## `ProvisionedThroughputPositive`

**Context:** `AWSPSM!DynamoDbTable`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/psm/rules/storage.evl:45`

### Why this rule exists

Checks that provisioned throughput positive. The dynamo db table element owns the evidence for this decision, including provisioned throughput, resource label. At this level, DynamoDB and S3 resources protect key correctness, recoverability, encryption, and public-access boundaries; allowing the model through without that evidence would move an unresolved choice into generated infrastructure. The concrete failure this rule prevents is: DynamoDB table has invalid provisioned throughput.

### When it applies

The rule is considered only when the guard below is true. A false guard is intentional: it means this invariant is not relevant to the current variant or modeling situation.

```evl
guard : self.provisionedThroughput.isDefined()
```

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.provisionedThroughput.readCapacityUnits >= 1 and self.provisionedThroughput.writeCapacityUnits >= 1
```

This rule reads: `provisionedThroughput`, `resourceLabel`.

### Diagnostic and repair

> DynamoDB table has invalid provisioned throughput. Fix: set both readCapacityUnits and writeCapacityUnits to at least 1.

**How to fix it:**

set both readCapacityUnits and writeCapacityUnits to at least 1.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---

## `ProductionTableShouldHavePitRecoveryAndDeletionProtection`

**Context:** `AWSPSM!DynamoDbTable`  
**Classification:** Advisory critique (warning)  
**Source:** `mde/validation/psm/rules/storage.evl:52`

### Why this rule exists

A production table is a business-data boundary, not just a storage object. Point-in-time recovery and deletion protection expose whether the design can survive operator error and restore data after corruption.

### When it applies

The rule is considered only when the guard below is true. A false guard is intentional: it means this invariant is not relevant to the current variant or modeling situation.

```evl
guard : self.isProductionScoped()
```

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : (self.pointInTimeRecoveryEnabled = true) and (self.deletionProtectionEnabled = true)
```

This rule reads: `isProductionScoped`, `pointInTimeRecoveryEnabled`, `deletionProtectionEnabled`, `resourceLabel`.

### Diagnostic and repair

> Production-scoped DynamoDB table lacks PITR or deletion protection. Fix: enable pointInTimeRecoveryEnabled and deletionProtectionEnabled unless an exception is approved.

**How to fix it:**

enable pointInTimeRecoveryEnabled and deletionProtectionEnabled unless an exception is approved.

A critique does not necessarily make the model invalid. It is a deliberate quality/readiness signal; disposition it by improving the model, documenting why it is acceptable, or carrying the decision into the review record.

---

## `ProductionTableShouldBeEncryptedWithKms`

**Context:** `AWSPSM!DynamoDbTable`  
**Classification:** Advisory critique (warning)  
**Source:** `mde/validation/psm/rules/storage.evl:59`

### Why this rule exists

The critique distinguishes ordinary encryption from an explicitly governed KMS key. For sensitive production data, key ownership and rotation are part of the security and recovery story.

### When it applies

The rule is considered only when the guard below is true. A false guard is intentional: it means this invariant is not relevant to the current variant or modeling situation.

```evl
guard : self.isProductionScoped()
```

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.kmsKey.isDefined() or (self.sseSpecification.isDefined() and self.sseSpecification.enabled = true)
```

This rule reads: `isProductionScoped`, `kmsKey`, `sseSpecification`, `resourceLabel`.

### Diagnostic and repair

> Production-scoped DynamoDB table lacks explicit SSE/KMS configuration. Fix: set sseSpecification and preferably attach kmsKey.

**How to fix it:**

set sseSpecification and preferably attach kmsKey.

A critique does not necessarily make the model invalid. It is a deliberate quality/readiness signal; disposition it by improving the model, documenting why it is acceptable, or carrying the decision into the review record.

---

## `GsiProjectionIncludeHasAttributes`

**Context:** `AWSPSM!DynamoDbGlobalSecondaryIndex`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/psm/rules/storage.evl:69`

### Why this rule exists

Checks that gsi projection include has attributes. The dynamo db global secondary index element owns the evidence for this decision, including projection, index name. At this level, DynamoDB and S3 resources protect key correctness, recoverability, encryption, and public-access boundaries; allowing the model through without that evidence would move an unresolved choice into generated infrastructure. The concrete failure this rule prevents is: DynamoDB GSI uses INCLUDE projection but has no nonKeyAttributes.

### When it applies

The rule is considered only when the guard below is true. A false guard is intentional: it means this invariant is not relevant to the current variant or modeling situation.

```evl
guard : self.projection.isDefined() and self.projection.projectionType = AWSPSMENUMS!DynamoDbProjectionType#INCLUDE
```

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.projection.nonKeyAttributes.notEmpty()
```

This rule reads: `projection`, `indexName`.

### Diagnostic and repair

> DynamoDB GSI uses INCLUDE projection but has no nonKeyAttributes. Fix: add projected attributes or change projectionType to ALL/KEYS_ONLY.

**How to fix it:**

add projected attributes or change projectionType to ALL/KEYS_ONLY.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---

## `GsiProvisionedThroughputPositiveWhenPresent`

**Context:** `AWSPSM!DynamoDbGlobalSecondaryIndex`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/psm/rules/storage.evl:76`

### Why this rule exists

Checks that gsi provisioned throughput positive when present. The dynamo db global secondary index element owns the evidence for this decision, including provisioned throughput, index name. At this level, DynamoDB and S3 resources protect key correctness, recoverability, encryption, and public-access boundaries; allowing the model through without that evidence would move an unresolved choice into generated infrastructure. The concrete failure this rule prevents is: DynamoDB GSI has invalid provisioned throughput.

### When it applies

The rule is considered only when the guard below is true. A false guard is intentional: it means this invariant is not relevant to the current variant or modeling situation.

```evl
guard : self.provisionedThroughput.isDefined()
```

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.provisionedThroughput.readCapacityUnits >= 1 and self.provisionedThroughput.writeCapacityUnits >= 1
```

This rule reads: `provisionedThroughput`, `indexName`.

### Diagnostic and repair

> DynamoDB GSI has invalid provisioned throughput. Fix: set both read and write capacity units to at least 1.

**How to fix it:**

set both read and write capacity units to at least 1.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---

## `EnabledTtlHasAttributeName`

**Context:** `AWSPSM!DynamoDbTimeToLiveSpecification`  
**Classification:** Advisory critique (warning)  
**Source:** `mde/validation/psm/rules/storage.evl:86`

### Why this rule exists

TTL is not meaningful until DynamoDB knows which attribute contains the expiration timestamp. The rule prevents an enabled lifecycle mechanism from silently doing nothing.

### When it applies

The rule is considered only when the guard below is true. A false guard is intentional: it means this invariant is not relevant to the current variant or modeling situation.

```evl
guard : self.enabled = true
```

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.attributeName.hasText()
```

This rule reads: `enabled`, `attributeName`.

### Diagnostic and repair

> DynamoDB TTL is enabled without an attributeName. Fix: set attributeName to the TTL epoch-seconds attribute.

**How to fix it:**

set attributeName to the TTL epoch-seconds attribute.

A critique does not necessarily make the model invalid. It is a deliberate quality/readiness signal; disposition it by improving the model, documenting why it is acceptable, or carrying the decision into the review record.

---

## `ProductionBucketsBlockPublicAccess`

**Context:** `AWSPSM!S3Bucket`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/psm/rules/storage.evl:96`

### Why this rule exists

S3 public exposure can be introduced through an ACL or bucket policy far from the object definition. Explicit blocking makes the safe production baseline survive those later changes.

### When it applies

The rule is considered only when the guard below is true. A false guard is intentional: it means this invariant is not relevant to the current variant or modeling situation.

```evl
guard : self.isProductionScoped()
```

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.blocksAllPublicAccess()
```

This rule reads: `isProductionScoped`, `blocksAllPublicAccess`, `resourceLabel`.

### Diagnostic and repair

> Production-scoped S3 bucket does not block all public access. Fix: set publicAccessMode STRICT_BLOCK_ALL or configure all publicAccessBlock flags to true.

**How to fix it:**

set publicAccessMode STRICT_BLOCK_ALL or configure all publicAccessBlock flags to true.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---

## `ProductionBucketsEncrypted`

**Context:** `AWSPSM!S3Bucket`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/psm/rules/storage.evl:103`

### Why this rule exists

A production bucket may contain durable business records or artifacts that outlive the request that created them. Requiring encryption prevents storage confidentiality from depending on an implicit AWS default.

### When it applies

The rule is considered only when the guard below is true. A false guard is intentional: it means this invariant is not relevant to the current variant or modeling situation.

```evl
guard : self.isProductionScoped()
```

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.encryption.isDefined()
```

This rule reads: `isProductionScoped`, `encryption`, `resourceLabel`.

### Diagnostic and repair

> Production-scoped S3 bucket has no encryption configuration. Fix: add S3BucketEncryption with SSE-S3 or SSE-KMS settings.

**How to fix it:**

add S3BucketEncryption with SSE-S3 or SSE-KMS settings.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---

## `ProductionBucketsShouldVersion`

**Context:** `AWSPSM!S3Bucket`  
**Classification:** Advisory critique (warning)  
**Source:** `mde/validation/psm/rules/storage.evl:110`

### Why this rule exists

Versioning gives operators a recovery path when an object is overwritten or deleted. The critique makes that trade-off visible for production data instead of assuming every bucket has the same lifecycle.

### When it applies

The rule is considered only when the guard below is true. A false guard is intentional: it means this invariant is not relevant to the current variant or modeling situation.

```evl
guard : self.isProductionScoped()
```

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.versioningStatus = AWSPSMENUMS!S3VersioningStatus#ENABLED
```

This rule reads: `isProductionScoped`, `versioningStatus`, `resourceLabel`.

### Diagnostic and repair

> Production-scoped S3 bucket does not enable versioning. Fix: set versioningStatus to ENABLED unless the bucket is intentionally ephemeral.

**How to fix it:**

set versioningStatus to ENABLED unless the bucket is intentionally ephemeral.

A critique does not necessarily make the model invalid. It is a deliberate quality/readiness signal; disposition it by improving the model, documenting why it is acceptable, or carrying the decision into the review record.

---

## `WebsiteBucketShouldNotBeProductionCritical`

**Context:** `AWSPSM!S3Bucket`  
**Classification:** Advisory critique (warning)  
**Source:** `mde/validation/psm/rules/storage.evl:117`

### Why this rule exists

Advises that website bucket should not be production critical. This is a review signal about website configuration json, is production scoped, resource label, not a cosmetic naming preference. In this part of the model, DynamoDB and S3 resources protect key correctness, recoverability, encryption, and public-access boundaries; the warning makes a decision visible while it can still be discussed and changed. The concrete gap is: S3 bucket has websiteConfigurationJson and is production-scoped. If it is left unexplained, a later transformation, generator, or reviewer has to invent an assumption.

### When it applies

The rule is considered only when the guard below is true. A false guard is intentional: it means this invariant is not relevant to the current variant or modeling situation.

```evl
guard : self.websiteConfigurationJson.hasText()
```

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : not self.isProductionScoped()
```

This rule reads: `websiteConfigurationJson`, `isProductionScoped`, `resourceLabel`.

### Diagnostic and repair

> S3 bucket has websiteConfigurationJson and is production-scoped. Fix: review public website exposure, use CloudFront/OAC where appropriate, and document an exception if needed.

**How to fix it:**

review public website exposure, use CloudFront/OAC where appropriate, and document an exception if needed.

A critique does not necessarily make the model invalid. It is a deliberate quality/readiness signal; disposition it by improving the model, documenting why it is acceptable, or carrying the decision into the review record.

---

## `KmsAlgorithmRequiresKmsKey`

**Context:** `AWSPSM!S3BucketEncryption`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/psm/rules/storage.evl:127`

### Why this rule exists

Checks that kms algorithm requires kms key. The s3 bucket encryption element owns the evidence for this decision, including sse algorithm, kms key. At this level, DynamoDB and S3 resources protect key correctness, recoverability, encryption, and public-access boundaries; allowing the model through without that evidence would move an unresolved choice into generated infrastructure. The concrete failure this rule prevents is: S3 bucket encryption uses a KMS algorithm but has no kmsKey.

### When it applies

The rule is considered only when the guard below is true. A false guard is intentional: it means this invariant is not relevant to the current variant or modeling situation.

```evl
guard : self.sseAlgorithm.hasText() and self.sseAlgorithm.toUpperCase().matches('.*KMS.*')
```

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.kmsKey.isDefined()
```

This rule reads: `sseAlgorithm`, `kmsKey`.

### Diagnostic and repair

> S3 bucket encryption uses a KMS algorithm but has no kmsKey. Fix: attach the KMS key used for bucket encryption.

**How to fix it:**

attach the KMS key used for bucket encryption.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---

## `NotificationRuleHasEventAndDestination`

**Context:** `AWSPSM!S3NotificationRule`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/psm/rules/storage.evl:137`

### Why this rule exists

A storage notification is a routed event, not a flag. It needs both the object event that triggers it and the destination that receives it so the generated integration has observable behavior.

### When it applies

The rule has no guard, so it applies to every instance of this context in the validated model.

There is no guard expression; every instance of the context is checked.

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.eventTypes.notEmpty() and self.destination.isDefined()
```

This rule reads: `eventTypes`, `destination`.

### Diagnostic and repair

> S3 notification rule is missing eventTypes or destination. Fix: add one or more S3 event types and choose a valid notification destination.

**How to fix it:**

add one or more S3 event types and choose a valid notification destination.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---

## `ReplicationHasRoleAndRules`

**Context:** `AWSPSM!S3ReplicationConfiguration`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/psm/rules/storage.evl:146`

### Why this rule exists

Replication crosses resource and often account boundaries. The role and replication rules describe who performs that movement and what is copied; omitting either makes the data-protection promise incomplete.

### When it applies

The rule has no guard, so it applies to every instance of this context in the validated model.

There is no guard expression; every instance of the context is checked.

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.role.isDefined() and (self.rules.notEmpty() or self.rulesJson.hasText())
```

This rule reads: `role`, `rules`, `rulesJson`.

### Diagnostic and repair

> S3 replication configuration lacks role or rules. Fix: attach an IAM role and define replication rules or rulesJson.

**How to fix it:**

attach an IAM role and define replication rules or rulesJson.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---
