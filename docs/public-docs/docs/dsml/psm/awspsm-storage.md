# DynamoDB and S3 resources

Storage classes expose the AWS details needed for DynamoDB key design and S3 governance, lifecycle, notification, replication, and encryption.

Source: `mde/metamodels/psm/awspsm-storage.emf`.

## `DynamoDbTable`

Represents dynamo db table in the PSM vocabulary. It specializes `AwsResource` with the details needed for this modeling concern.

Direct supertypes: `AwsResource`. Inherited attributes and marker capabilities are documented in the [shared kernel](../shared-kernel.md); this section lists every attribute declared by this class.

### Declared attributes

| Attribute                           | Type and multiplicity     | What it captures and why it exists                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                             | Accepted values and example                                                                                          |
| ----------------------------------- | ------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | -------------------------------------------------------------------------------------------------------------------- |
| `tableName`                         | `String` [1]              | Records the stable name/key/code used for table name for the dynamo db table. This keeps the decision explicit even when the element's class or relationships remain unchanged. Transformation role: ETL rule `DataStore2DynamoDbTable` in `mde/transformations/pim-to-awspsm/data-messaging-events.etl` assigns or materializes this feature while refining `DynamoDbTable`. Generation role: The artifact generator references this feature in `mde/generation/awspsm-to-artifacts/lib/cfn.eol`. The artifact generator references this feature in `mde/generation/awspsm-to-artifacts/lib/iam.eol`.                                                                                                                                                                                                                                                                                                                                                         | A free-form `String`, subject to this class's semantic meaning and any EVL constraints. Example: `orders-prod`.      |
| `deletionProtectionEnabled`         | `Boolean` [1]             | For a dynamo db table, the model records the data-lifecycle rule for deletion protection enabled. Keeping the fact with its owning element lets validation and refinement inspect it before artifact generation. Semantic validation: `ProductionTableShouldHavePitRecoveryAndDeletionProtection` (production table should have pit recovery and deletion protection) in `mde/validation/psm/rules/storage.evl` the flag must be enabled for this rule to pass. Transformation role: ETL rule `DataStore2DynamoDbTable` in `mde/transformations/pim-to-awspsm/data-messaging-events.etl` assigns or materializes this feature while refining `DynamoDbTable`.                                                                                                                                                                                                                                                                                                  | Either `true` or `false`. Example: `true`.                                                                           |
| `pointInTimeRecoveryEnabled`        | `Boolean` [1]             | For a dynamo db table, the model records whether point in time recovery enabled applies. Keeping the fact with its owning element lets validation and refinement inspect it before artifact generation. Semantic validation: `ProductionTableShouldHavePitRecoveryAndDeletionProtection` (production table should have pit recovery and deletion protection) in `mde/validation/psm/rules/storage.evl` the flag must be enabled for this rule to pass. Transformation role: ETL rule `DataStore2DynamoDbTable` in `mde/transformations/pim-to-awspsm/data-messaging-events.etl` assigns or materializes this feature while refining `DynamoDbTable`. ETL rule `DataStore2DynamoDbTable` in `mde/transformations/pim-to-awspsm/data-messaging-events.etl` reads or derives this feature while refining `DynamoDbTable`.                                                                                                                                         | Either `true` or `false`. Example: `true`.                                                                           |
| `contributorInsightsEnabled`        | `Boolean` [1]             | Records whether contributor insights enabled applies to dynamo db table. It preserves an explicit architectural or governance decision through review and transformation, so later steps do not have to infer it. Transformation role: ETL rule `DataStore2DynamoDbTable` in `mde/transformations/pim-to-awspsm/data-messaging-events.etl` assigns or materializes this feature while refining `DynamoDbTable`. Generation role: The artifact generator references this feature in `mde/generation/awspsm-to-artifacts/lib/cfn.eol`.                                                                                                                                                                                                                                                                                                                                                                                                                           | Either `true` or `false`. Example: `true`.                                                                           |
| `importSourceSpecificationRequired` | `Boolean` [1]             | For a dynamo db table, the model records the origin/source selected for import source specification required. Keeping the fact with its owning element lets validation and refinement inspect it before artifact generation. Transformation role: ETL rule `DataStore2DynamoDbTable` in `mde/transformations/pim-to-awspsm/data-messaging-events.etl` assigns or materializes this feature while refining `DynamoDbTable`.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                     | Either `true` or `false`. Example: `true`.                                                                           |
| `resourcePolicyJson`                | `String` [1]              | For a dynamo db table, the model records the origin/source selected for resource policy json. Keeping the fact with its owning element lets validation and refinement inspect it before artifact generation. The repository contains no direct EVL rule, ETL assignment, or artifact-generator read for this declared field. For now, it remains a model-level fact for review and future refinement.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                          | A free-form `String`, subject to this class's semantic meaning and any EVL constraints. Example: `{"enabled":true}`. |
| `billingMode`                       | `DynamoDbBillingMode` [1] | For a dynamo db table, the model records the controlled classification or strategy represented by billing mode. Keeping the fact with its owning element lets validation and refinement inspect it before artifact generation. Semantic validation: `ProvisionedModeNeedsThroughput` (provisioned mode needs throughput) in `mde/validation/psm/rules/storage.evl` the feature participates in a semantic validation condition. `PayPerRequestDoesNotUseProvisionedThroughput` (pay per request does not use provisioned throughput) in `mde/validation/psm/rules/storage.evl` the feature participates in a semantic validation condition. Transformation role: ETL rule `DataStore2DynamoDbTable` in `mde/transformations/pim-to-awspsm/data-messaging-events.etl` assigns or materializes this feature while refining `DynamoDbTable`. Generation role: The artifact generator references this feature in `mde/generation/awspsm-to-artifacts/lib/cfn.eol`. | Exactly one of: `PAY_PER_REQUEST`, `PROVISIONED`. Example: `PAY_PER_REQUEST`.                                        |
| `tableClass`                        | `DynamoDbTableClass` [1]  | For a dynamo db table, the model records the controlled classification or strategy represented by table class. Keeping the fact with its owning element lets validation and refinement inspect it before artifact generation. Transformation role: ETL rule `DataStore2DynamoDbTable` in `mde/transformations/pim-to-awspsm/data-messaging-events.etl` assigns or materializes this feature while refining `DynamoDbTable`. Generation role: The artifact generator references this feature in `mde/generation/awspsm-to-artifacts/lib/cfn.eol`.                                                                                                                                                                                                                                                                                                                                                                                                               | Exactly one of: `STANDARD`, `STANDARD_INFREQUENT_ACCESS`. Example: `STANDARD`.                                       |

### Relationships

| Relationship                                                  | Kind and multiplicity | Meaning in the model                                                                                                                                |
| ------------------------------------------------------------- | --------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------- |
| `attributeDefinitions` → `DynamoDbAttributeDefinition`        | containment, [+]      | Contains the dynamo db attribute definition element(s) that make up this dynamo db table; the contained objects belong to this model element.       |
| `keySchema` → `DynamoDbKeySchemaElement`                      | containment, [+]      | Contains the dynamo db key schema element element(s) that make up this dynamo db table; the contained objects belong to this model element.         |
| `localSecondaryIndexes` → `DynamoDbLocalSecondaryIndex`       | containment, [*]      | Contains the dynamo db local secondary index element(s) that make up this dynamo db table; the contained objects belong to this model element.      |
| `globalSecondaryIndexes` → `DynamoDbGlobalSecondaryIndex`     | containment, [*]      | Contains the dynamo db global secondary index element(s) that make up this dynamo db table; the contained objects belong to this model element.     |
| `replicas` → `DynamoDbReplicaSpecification`                   | containment, [*]      | Contains the dynamo db replica specification element(s) that make up this dynamo db table; the contained objects belong to this model element.      |
| `provisionedThroughput` → `DynamoDbProvisionedThroughput`     | containment, [?]      | Contains the dynamo db provisioned throughput element(s) that make up this dynamo db table; the contained objects belong to this model element.     |
| `onDemandThroughput` → `DynamoDbOnDemandThroughput`           | containment, [?]      | Contains the dynamo db on demand throughput element(s) that make up this dynamo db table; the contained objects belong to this model element.       |
| `streamSpecification` → `DynamoDbStreamSpecification`         | containment, [?]      | Contains the dynamo db stream specification element(s) that make up this dynamo db table; the contained objects belong to this model element.       |
| `timeToLiveSpecification` → `DynamoDbTimeToLiveSpecification` | containment, [?]      | Contains the dynamo db time to live specification element(s) that make up this dynamo db table; the contained objects belong to this model element. |
| `sseSpecification` → `DynamoDbSseSpecification`               | containment, [?]      | Contains the dynamo db sse specification element(s) that make up this dynamo db table; the contained objects belong to this model element.          |
| `backupPolicy` → `DynamoDbBackupPolicy`                       | containment, [?]      | Contains the dynamo db backup policy element(s) that make up this dynamo db table; the contained objects belong to this model element.              |
| `resourcePolicy` → `IamPolicyDocument`                        | containment, [?]      | Contains the iam policy document element(s) that make up this dynamo db table; the contained objects belong to this model element.                  |
| `kmsKey` → `KmsKey`                                           | reference, [?]        | References the kms key element(s) used as kms key by this dynamo db table; the target may be shared elsewhere in the model.                         |

## `DynamoDbAttributeDefinition`

Represents dynamo db attribute definition in the PSM vocabulary. It specializes `TraceableElement` with the details needed for this modeling concern.

Direct supertypes: `TraceableElement`. Inherited attributes and marker capabilities are documented in the [shared kernel](../shared-kernel.md); this section lists every attribute declared by this class.

### Declared attributes

| Attribute       | Type and multiplicity       | What it captures and why it exists                                                                                                                                                                                                                                                                                                                                                                                | Accepted values and example                                                                                 |
| --------------- | --------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ----------------------------------------------------------------------------------------------------------- |
| `attributeName` | `String` [1]                | For a dynamo db attribute definition, the model records the stable name/key/code used for attribute name. Keeping the fact with its owning element lets validation and refinement inspect it before artifact generation. The repository contains no direct EVL rule, ETL assignment, or artifact-generator read for this declared field. For now, it remains a model-level fact for review and future refinement. | A free-form `String`, subject to this class's semantic meaning and any EVL constraints. Example: `orderId`. |
| `attributeType` | `DynamoDbAttributeType` [1] | Records controlled classification or strategy represented by attribute type for dynamo db attribute definition. It keeps this decision explicit during review and transformation, so later steps do not have to infer it. Generation role: The artifact generator references this feature in `mde/generation/awspsm-to-artifacts/lib/cfn.eol`.                                                                    | Exactly one of: `S`, `N`, `B`. Example: `S`.                                                                |

### Relationships

This class declares no direct relationships.

## `DynamoDbKeySchemaElement`

Represents dynamo db key schema element in the PSM vocabulary. It specializes `TraceableElement` with the details needed for this modeling concern.

Direct supertypes: `TraceableElement`. Inherited attributes and marker capabilities are documented in the [shared kernel](../shared-kernel.md); this section lists every attribute declared by this class.

### Declared attributes

| Attribute       | Type and multiplicity | What it captures and why it exists                                                                                                                                                                                                                                                                                                                                                                         | Accepted values and example                                                                                                         |
| --------------- | --------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------- |
| `attributeName` | `String` [1]          | Records the stable name/key/code used for attribute name for the dynamo db key schema element. This keeps the decision explicit even when the element's class or relationships remain unchanged. The repository contains no direct EVL rule, ETL assignment, or artifact-generator read for this declared field. For now, it remains a model-level fact for review and future refinement.                  | A free-form `String`, subject to this class's semantic meaning and any EVL constraints. Example: `DynamoDbKeySchemaElementExample`. |
| `keyType`       | `DynamoDbKeyType` [1] | Records controlled classification or strategy represented by key type for dynamo db key schema element. It keeps this decision explicit during review and transformation, so later steps do not have to infer it. The repository contains no direct EVL rule, ETL assignment, or artifact-generator read for this declared field. For now, it remains a model-level fact for review and future refinement. | Exactly one of: `HASH`, `RANGE`. Example: `HASH`.                                                                                   |

### Relationships

This class declares no direct relationships.

## `DynamoDbProjection`

Represents dynamo db projection in the PSM vocabulary. It specializes `TraceableElement` with the details needed for this modeling concern.

Direct supertypes: `TraceableElement`. Inherited attributes and marker capabilities are documented in the [shared kernel](../shared-kernel.md); this section lists every attribute declared by this class.

### Declared attributes

| Attribute          | Type and multiplicity        | What it captures and why it exists                                                                                                                                                                                                                                                                                                                                           | Accepted values and example                                    |
| ------------------ | ---------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | -------------------------------------------------------------- |
| `projectionType`   | `DynamoDbProjectionType` [1] | Stores the controlled classification or strategy represented by projection type on the dynamo db projection. The field keeps the model explicit and reviewable during review and transformation instead of leaving the decision to an inferred default. Generation role: The artifact generator references this feature in `mde/generation/awspsm-to-artifacts/lib/cfn.eol`. | Exactly one of: `ALL`, `KEYS_ONLY`, `INCLUDE`. Example: `ALL`. |
| `nonKeyAttributes` | `String` [*]                 | Stores the non key attributes value on the dynamo db projection. The field keeps the model explicit and reviewable during review and transformation instead of leaving the decision to an inferred default. Generation role: The artifact generator references this feature in `mde/generation/awspsm-to-artifacts/lib/cfn.eol`.                                             | A collection of values. Example: [`orderId`, `orderId-2`].     |

### Relationships

This class declares no direct relationships.

## `DynamoDbProvisionedThroughput`

Represents dynamo db provisioned throughput in the PSM vocabulary. It specializes `TraceableElement` with the details needed for this modeling concern.

Direct supertypes: `TraceableElement`. Inherited attributes and marker capabilities are documented in the [shared kernel](../shared-kernel.md); this section lists every attribute declared by this class.

### Declared attributes

| Attribute            | Type and multiplicity | What it captures and why it exists                                                                                                                                                                                                                                                                     | Accepted values and example                                                                      |
| -------------------- | --------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ | ------------------------------------------------------------------------------------------------ |
| `readCapacityUnits`  | `Integer` [1]         | Records the read capacity units value for the dynamo db provisioned throughput. This keeps the decision explicit even when the element's class or relationships remain unchanged. Generation role: The artifact generator references this feature in `mde/generation/awspsm-to-artifacts/lib/cfn.eol`. | A numeric `Integer` value; use the unit or boundary documented for this attribute. Example: `1`. |
| `writeCapacityUnits` | `Integer` [1]         | Stores the write capacity units value on the dynamo db provisioned throughput. The field records an operational boundary explicitly instead of leaving it to provider defaults. Generation role: The artifact generator references this feature in `mde/generation/awspsm-to-artifacts/lib/cfn.eol`.   | A numeric `Integer` value; use the unit or boundary documented for this attribute. Example: `1`. |

### Relationships

This class declares no direct relationships.

## `DynamoDbOnDemandThroughput`

Represents dynamo db on demand throughput in the PSM vocabulary. It specializes `TraceableElement` with the details needed for this modeling concern.

Direct supertypes: `TraceableElement`. Inherited attributes and marker capabilities are documented in the [shared kernel](../shared-kernel.md); this section lists every attribute declared by this class.

### Declared attributes

| Attribute              | Type and multiplicity | What it captures and why it exists                                                                                                                                                                                                                                                                                            | Accepted values and example                                                                      |
| ---------------------- | --------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------ |
| `maxReadRequestUnits`  | `Integer` [1]         | For a dynamo db on demand throughput, the model records the max read request units value. Keeping the fact with its owning element lets validation and refinement inspect it before artifact generation. Generation role: The artifact generator references this feature in `mde/generation/awspsm-to-artifacts/lib/cfn.eol`. | A numeric `Integer` value; use the unit or boundary documented for this attribute. Example: `1`. |
| `maxWriteRequestUnits` | `Integer` [1]         | Records the max write request units value for the dynamo db on demand throughput. This keeps the decision explicit even when the element's class or relationships remain unchanged. Generation role: The artifact generator references this feature in `mde/generation/awspsm-to-artifacts/lib/cfn.eol`.                      | A numeric `Integer` value; use the unit or boundary documented for this attribute. Example: `1`. |

### Relationships

This class declares no direct relationships.

## `DynamoDbLocalSecondaryIndex`

Represents dynamo db local secondary index in the PSM vocabulary. It specializes `TraceableElement` with the details needed for this modeling concern.

Direct supertypes: `TraceableElement`. Inherited attributes and marker capabilities are documented in the [shared kernel](../shared-kernel.md); this section lists every attribute declared by this class.

### Declared attributes

| Attribute   | Type and multiplicity | What it captures and why it exists                                                                                                                                                                                                                                                                                                                                                                                              | Accepted values and example                                                                                                            |
| ----------- | --------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------- |
| `indexName` | `String` [1]          | Stores the stable name/key/code used for index name on the dynamo db local secondary index. The field keeps the model explicit and reviewable during review and transformation instead of leaving the decision to an inferred default. The repository contains no direct EVL rule, ETL assignment, or artifact-generator read for this declared field. For now, it remains a model-level fact for review and future refinement. | A free-form `String`, subject to this class's semantic meaning and any EVL constraints. Example: `DynamoDbLocalSecondaryIndexExample`. |

### Relationships

| Relationship                             | Kind and multiplicity | Meaning in the model                                                                                                                                        |
| ---------------------------------------- | --------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `keySchema` → `DynamoDbKeySchemaElement` | containment, [+]      | Contains the dynamo db key schema element element(s) that make up this dynamo db local secondary index; the contained objects belong to this model element. |
| `projection` → `DynamoDbProjection`      | containment, [1]      | Contains the dynamo db projection element(s) that make up this dynamo db local secondary index; the contained objects belong to this model element.         |

## `DynamoDbGlobalSecondaryIndex`

Represents dynamo db global secondary index in the PSM vocabulary. It specializes `TraceableElement` with the details needed for this modeling concern.

Direct supertypes: `TraceableElement`. Inherited attributes and marker capabilities are documented in the [shared kernel](../shared-kernel.md); this section lists every attribute declared by this class.

### Declared attributes

| Attribute   | Type and multiplicity | What it captures and why it exists                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                       | Accepted values and example                                                                                                             |
| ----------- | --------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | --------------------------------------------------------------------------------------------------------------------------------------- |
| `indexName` | `String` [1]          | For a dynamo db global secondary index, the model records the stable name/key/code used for index name. Keeping the fact with its owning element lets validation and refinement inspect it before artifact generation. Semantic validation: `GsiProjectionIncludeHasAttributes` (gsi projection include has attributes) in `mde/validation/psm/rules/storage.evl` the rule's diagnostic or remediation guidance refers to this feature. `GsiProvisionedThroughputPositiveWhenPresent` (gsi provisioned throughput positive when present) in `mde/validation/psm/rules/storage.evl` the rule's diagnostic or remediation guidance refers to this feature. | A free-form `String`, subject to this class's semantic meaning and any EVL constraints. Example: `DynamoDbGlobalSecondaryIndexExample`. |

### Relationships

| Relationship                                              | Kind and multiplicity | Meaning in the model                                                                                                                                             |
| --------------------------------------------------------- | --------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `keySchema` → `DynamoDbKeySchemaElement`                  | containment, [+]      | Contains the dynamo db key schema element element(s) that make up this dynamo db global secondary index; the contained objects belong to this model element.     |
| `projection` → `DynamoDbProjection`                       | containment, [1]      | Contains the dynamo db projection element(s) that make up this dynamo db global secondary index; the contained objects belong to this model element.             |
| `provisionedThroughput` → `DynamoDbProvisionedThroughput` | containment, [?]      | Contains the dynamo db provisioned throughput element(s) that make up this dynamo db global secondary index; the contained objects belong to this model element. |
| `onDemandThroughput` → `DynamoDbOnDemandThroughput`       | containment, [?]      | Contains the dynamo db on demand throughput element(s) that make up this dynamo db global secondary index; the contained objects belong to this model element.   |

## `DynamoDbReplicaSpecification`

Represents dynamo db replica specification in the PSM vocabulary. It specializes `TraceableElement` with the details needed for this modeling concern.

Direct supertypes: `TraceableElement`. Inherited attributes and marker capabilities are documented in the [shared kernel](../shared-kernel.md); this section lists every attribute declared by this class.

### Declared attributes

| Attribute                    | Type and multiplicity | What it captures and why it exists                                                                                                                                                                                                                                                                                                                                                                                           | Accepted values and example                                                                                   |
| ---------------------------- | --------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------- |
| `region`                     | `String` [1]          | Records the region value for the dynamo db replica specification. This keeps the decision explicit even when the element's class or relationships remain unchanged. The repository contains no direct EVL rule, ETL assignment, or artifact-generator read for this declared field. For now, it remains a model-level fact for review and future refinement.                                                                 | A free-form `String`, subject to this class's semantic meaning and any EVL constraints. Example: `us-east-1`. |
| `pointInTimeRecoveryEnabled` | `Boolean` [1]         | Records whether point in time recovery enabled applies to dynamo db replica specification. It preserves an explicit architectural or governance decision through review and transformation, so later steps do not have to infer it. The repository contains no direct EVL rule, ETL assignment, or artifact-generator read for this declared field. For now, it remains a model-level fact for review and future refinement. | Either `true` or `false`. Example: `true`.                                                                    |
| `deletionProtectionEnabled`  | `Boolean` [1]         | Records data-lifecycle rule for deletion protection enabled for dynamo db replica specification. It keeps retention and data-lifecycle controls explicit during review and transformation, so later steps do not have to infer it. The repository contains no direct EVL rule, ETL assignment, or artifact-generator read for this declared field. For now, it remains a model-level fact for review and future refinement.  | Either `true` or `false`. Example: `true`.                                                                    |

### Relationships

| Relationship        | Kind and multiplicity | Meaning in the model                                                                                                                        |
| ------------------- | --------------------- | ------------------------------------------------------------------------------------------------------------------------------------------- |
| `kmsKey` → `KmsKey` | reference, [?]        | References the kms key element(s) used as kms key by this dynamo db replica specification; the target may be shared elsewhere in the model. |

## `DynamoDbStreamSpecification`

Represents dynamo db stream specification in the PSM vocabulary. It specializes `TraceableElement` with the details needed for this modeling concern.

Direct supertypes: `TraceableElement`. Inherited attributes and marker capabilities are documented in the [shared kernel](../shared-kernel.md); this section lists every attribute declared by this class.

### Declared attributes

| Attribute        | Type and multiplicity        | What it captures and why it exists                                                                                                                                                                                                                                                                                                                                     | Accepted values and example                                                                        |
| ---------------- | ---------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | -------------------------------------------------------------------------------------------------- |
| `streamViewType` | `DynamoDbStreamViewType` [1] | For a dynamo db stream specification, the model records the controlled classification or strategy represented by stream view type. Keeping the fact with its owning element lets validation and refinement inspect it before artifact generation. Generation role: The artifact generator references this feature in `mde/generation/awspsm-to-artifacts/lib/cfn.eol`. | Exactly one of: `KEYS_ONLY`, `NEW_IMAGE`, `OLD_IMAGE`, `NEW_AND_OLD_IMAGES`. Example: `KEYS_ONLY`. |

### Relationships

This class declares no direct relationships.

## `DynamoDbTimeToLiveSpecification`

Represents dynamo db time to live specification in the PSM vocabulary. It specializes `TraceableElement` with the details needed for this modeling concern.

Direct supertypes: `TraceableElement`. Inherited attributes and marker capabilities are documented in the [shared kernel](../shared-kernel.md); this section lists every attribute declared by this class.

### Declared attributes

| Attribute       | Type and multiplicity | What it captures and why it exists                                                                                                                                                                                                                                                                                                                                                                                    | Accepted values and example                                                                                                                |
| --------------- | --------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------ |
| `attributeName` | `String` [1]          | Stores the stable name/key/code used for attribute name on the dynamo db time to live specification. The field keeps the model explicit and reviewable during review and transformation instead of leaving the decision to an inferred default. Semantic validation: `EnabledTtlHasAttributeName` (enabled ttl has attribute name) in `mde/validation/psm/rules/storage.evl` the value must be present and non-blank. | A free-form `String`, subject to this class's semantic meaning and any EVL constraints. Example: `DynamoDbTimeToLiveSpecificationExample`. |
| `enabled`       | `Boolean` [1]         | For a dynamo db time to live specification, the model records whether enabled applies. Keeping the fact with its owning element lets validation and refinement inspect it before artifact generation. Semantic validation: `EnabledTtlHasAttributeName` (enabled ttl has attribute name) in `mde/validation/psm/rules/storage.evl` the flag must be enabled for this rule to pass.                                    | Either `true` or `false`. Example: `true`.                                                                                                 |

### Relationships

This class declares no direct relationships.

## `DynamoDbSseSpecification`

Represents dynamo db sse specification in the PSM vocabulary. It specializes `TraceableElement` with the details needed for this modeling concern.

Direct supertypes: `TraceableElement`. Inherited attributes and marker capabilities are documented in the [shared kernel](../shared-kernel.md); this section lists every attribute declared by this class.

### Declared attributes

| Attribute | Type and multiplicity | What it captures and why it exists                                                                                                                                                                                                                                                                                                                                                                | Accepted values and example                                                                                  |
| --------- | --------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------ |
| `enabled` | `Boolean` [1]         | Records whether enabled applies to dynamo db sse specification. It preserves an explicit architectural or governance decision through review and transformation, so later steps do not have to infer it. The repository contains no direct EVL rule, ETL assignment, or artifact-generator read for this declared field. For now, it remains a model-level fact for review and future refinement. | Either `true` or `false`. Example: `true`.                                                                   |
| `sseType` | `String` [1]          | Records the controlled classification or strategy for sse type in the dynamo db sse specification. This keeps the decision explicit even when the element's class or relationships remain unchanged. Generation role: The artifact generator references this feature in `mde/generation/awspsm-to-artifacts/lib/cfn.eol`.                                                                         | A free-form `String`, subject to this class's semantic meaning and any EVL constraints. Example: `BUSINESS`. |

### Relationships

| Relationship        | Kind and multiplicity | Meaning in the model                                                                                                                    |
| ------------------- | --------------------- | --------------------------------------------------------------------------------------------------------------------------------------- |
| `kmsKey` → `KmsKey` | reference, [?]        | References the kms key element(s) used as kms key by this dynamo db sse specification; the target may be shared elsewhere in the model. |

## `DynamoDbBackupPolicy`

Represents dynamo db backup policy in the PSM vocabulary. It specializes `TraceableElement` with the details needed for this modeling concern.

Direct supertypes: `TraceableElement`. Inherited attributes and marker capabilities are documented in the [shared kernel](../shared-kernel.md); this section lists every attribute declared by this class.

### Declared attributes

| Attribute                     | Type and multiplicity | What it captures and why it exists                                                                                                                                                                                                                                                                                                                                                  | Accepted values and example                                                                                                                                                                     |
| ----------------------------- | --------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `backupRationale`             | `String` [1]          | Records reasoning behind backup rationale for dynamo db backup policy. It keeps design review and decision traceability explicit during review and transformation, so later steps do not have to infer it. Generation role: The artifact generator references this feature in `mde/generation/awspsm-to-artifacts/lib/validation.eol`.                                              | A free-form `String`, subject to this class's semantic meaning and any EVL constraints. Example: `The choice protects the business outcome while keeping the design independently deployable.`. |
| `recoveryPointRetentionDays`  | `Integer` [1]         | Stores the data-lifecycle rule for recovery point retention days on the dynamo db backup policy. The field records retention and data-lifecycle controls as an explicit, reviewable input. The repository contains no direct EVL rule, ETL assignment, or artifact-generator read for this declared field. For now, it remains a model-level fact for review and future refinement. | A numeric `Integer` value; use the unit or boundary documented for this attribute. Example: `1`.                                                                                                |
| `pointInTimeRecoveryDecision` | `Decision` [1]        | Records controlled classification or strategy represented by point in time recovery decision for dynamo db backup policy. It keeps this decision explicit during review and transformation, so later steps do not have to infer it. Generation role: The artifact generator references this feature in `mde/generation/awspsm-to-artifacts/lib/validation.eol`.                     | Exactly one of: `UNDECIDED`, `REQUIRED`, `NOT_REQUIRED`, `ACCEPTED`, `NEEDS_REVIEW`, `GENERATOR_OWNED`. Example: `UNDECIDED`.                                                                   |

### Relationships

This class declares no direct relationships.

## `S3Bucket`

Represents s3 bucket in the PSM vocabulary. It specializes `AwsResource` with the details needed for this modeling concern.

Direct supertypes: `AwsResource`. Inherited attributes and marker capabilities are documented in the [shared kernel](../shared-kernel.md); this section lists every attribute declared by this class.

### Declared attributes

| Attribute                        | Type and multiplicity         | What it captures and why it exists                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                       | Accepted values and example                                                                                                  |
| -------------------------------- | ----------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ---------------------------------------------------------------------------------------------------------------------------- |
| `bucketName`                     | `String` [1]                  | Records the stable name/key/code used for bucket name for the s3 bucket. This keeps the decision explicit even when the element's class or relationships remain unchanged. Transformation role: ETL rule `ObjectStore2S3Bucket` in `mde/transformations/pim-to-awspsm/data-messaging-events.etl` assigns or materializes this feature while refining `S3Bucket`. Generation role: The artifact generator references this feature in `mde/generation/awspsm-to-artifacts/lib/cfn.eol`. The artifact generator references this feature in `mde/generation/awspsm-to-artifacts/lib/naming.eol`.                                                                                                                                                                                                                                                                                             | A free-form `String`, subject to this class's semantic meaning and any EVL constraints. Example: `orders-prod-123456789012`. |
| `objectLockEnabled`              | `Boolean` [1]                 | Records whether object lock enabled applies to s3 bucket. This keeps the decision explicit even when the element's class or relationships remain unchanged. Transformation role: ETL rule `ObjectStore2S3Bucket` in `mde/transformations/pim-to-awspsm/data-messaging-events.etl` assigns or materializes this feature while refining `S3Bucket`. Generation role: The artifact generator references this feature in `mde/generation/awspsm-to-artifacts/lib/cfn.eol`.                                                                                                                                                                                                                                                                                                                                                                                                                   | Either `true` or `false`. Example: `true`.                                                                                   |
| `transferAccelerationEnabled`    | `Boolean` [1]                 | Records whether transfer acceleration enabled applies to s3 bucket. The field records an explicit architectural or governance decision as an explicit, reviewable input. Transformation role: ETL rule `ObjectStore2S3Bucket` in `mde/transformations/pim-to-awspsm/data-messaging-events.etl` assigns or materializes this feature while refining `S3Bucket`. Generation role: The artifact generator references this feature in `mde/generation/awspsm-to-artifacts/lib/cfn.eol`.                                                                                                                                                                                                                                                                                                                                                                                                      | Either `true` or `false`. Example: `true`.                                                                                   |
| `eventBridgeNotificationEnabled` | `Boolean` [1]                 | Records whether event bridge notification enabled applies to s3 bucket. It preserves an explicit architectural or governance decision through review and transformation, so later steps do not have to infer it. Transformation role: ETL rule `ObjectStore2S3Bucket` in `mde/transformations/pim-to-awspsm/data-messaging-events.etl` assigns or materializes this feature while refining `S3Bucket`. Generation role: The artifact generator references this feature in `mde/generation/awspsm-to-artifacts/lib/cfn.eol`.                                                                                                                                                                                                                                                                                                                                                              | Either `true` or `false`. Example: `true`.                                                                                   |
| `bucketKeyEnabled`               | `Boolean` [1]                 | Records whether bucket key enabled applies to s3 bucket. The field records an explicit architectural or governance decision as an explicit, reviewable input. Transformation role: ETL rule `ObjectStore2S3Bucket` in `mde/transformations/pim-to-awspsm/data-messaging-events.etl` assigns or materializes this feature while refining `S3Bucket`.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                      | Either `true` or `false`. Example: `true`.                                                                                   |
| `accessControl`                  | `String` [1]                  | Records the access control value for the s3 bucket. This keeps the decision explicit even when the element's class or relationships remain unchanged. Transformation role: ETL rule `ObjectStore2S3Bucket` in `mde/transformations/pim-to-awspsm/data-messaging-events.etl` assigns or materializes this feature while refining `S3Bucket`.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                              | A free-form `String`, subject to this class's semantic meaning and any EVL constraints. Example: `S3 Bucket Access Control`. |
| `websiteConfigurationJson`       | `String` [1]                  | Records serialized JSON representation of website configuration for s3 bucket. It keeps preserving structured provider or contract detail explicit during review and transformation, so later steps do not have to infer it. Semantic validation: `WebsiteBucketShouldNotBeProductionCritical` (website bucket should not be production critical) in `mde/validation/psm/rules/storage.evl` the value must be present and non-blank. Generation role: The artifact generator references this feature in `mde/generation/awspsm-to-artifacts/lib/cfn.eol`.                                                                                                                                                                                                                                                                                                                                | A free-form `String`, subject to this class's semantic meaning and any EVL constraints. Example: `{"enabled":true}`.         |
| `loggingConfigurationJson`       | `String` [1]                  | Records serialized JSON representation of logging configuration for s3 bucket. It keeps preserving structured provider or contract detail explicit during review and transformation, so later steps do not have to infer it. Generation role: The artifact generator references this feature in `mde/generation/awspsm-to-artifacts/lib/cfn.eol`.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                        | A free-form `String`, subject to this class's semantic meaning and any EVL constraints. Example: `{"enabled":true}`.         |
| `versioningStatus`               | `S3VersioningStatus` [1]      | For a s3 bucket, the model records the compatibility/version marker for versioning status. Keeping the fact with its owning element lets validation and refinement inspect it before artifact generation. Semantic validation: `ProductionBucketsShouldVersion` (production buckets should version) in `mde/validation/psm/rules/storage.evl` the feature participates in a semantic validation condition. Transformation role: ETL rule `ObjectStore2S3Bucket` in `mde/transformations/pim-to-awspsm/data-messaging-events.etl` assigns or materializes this feature while refining `S3Bucket`. Generation role: The artifact generator references this feature in `mde/generation/awspsm-to-artifacts/lib/cfn.eol`.                                                                                                                                                                    | Exactly one of: `ENABLED`, `SUSPENDED`. Example: `ENABLED`.                                                                  |
| `publicAccessMode`               | `S3BlockPublicAccessMode` [1] | Stores the controlled classification or strategy represented by public access mode on the s3 bucket. The field keeps the model explicit and reviewable during review and transformation instead of leaving the decision to an inferred default. Semantic validation: `ProductionBucketsBlockPublicAccess` (production buckets block public access) in `mde/validation/psm/rules/storage.evl` the rule's diagnostic or remediation guidance refers to this feature. Transformation role: ETL rule `ObjectStore2S3Bucket` in `mde/transformations/pim-to-awspsm/data-messaging-events.etl` assigns or materializes this feature while refining `S3Bucket`. Generation role: The artifact generator references this feature in `mde/generation/awspsm-to-artifacts/lib/cfn.eol`. The artifact generator references this feature in `mde/generation/awspsm-to-artifacts/lib/validation.eol`. | Exactly one of: `STRICT_BLOCK_ALL`, `CUSTOM`, `DISABLED_NOT_RECOMMENDED`. Example: `STRICT_BLOCK_ALL`.                       |

### Relationships

| Relationship                                                | Kind and multiplicity             | Meaning in the model                                                                                                                          |
| ----------------------------------------------------------- | --------------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------- |
| `ownershipControls` → `S3OwnershipControls`                 | containment, [?]                  | Contains the s3 ownership controls element(s) that make up this s3 bucket; the contained objects belong to this model element.                |
| `cors` → `CorsConfiguration`                                | containment, [?]                  | Contains the cors configuration element(s) that make up this s3 bucket; the contained objects belong to this model element.                   |
| `encryption` → `S3BucketEncryption`                         | containment, [?]                  | Contains the s3 bucket encryption element(s) that make up this s3 bucket; the contained objects belong to this model element.                 |
| `lifecycle` → `S3LifecycleConfiguration`                    | containment, [?]                  | Contains the s3 lifecycle configuration element(s) that make up this s3 bucket; the contained objects belong to this model element.           |
| `publicAccessBlock` → `S3PublicAccessBlockConfiguration`    | containment, [?]                  | Contains the s3 public access block configuration element(s) that make up this s3 bucket; the contained objects belong to this model element. |
| `notificationConfiguration` → `S3NotificationConfiguration` | containment, [?]                  | Contains the s3 notification configuration element(s) that make up this s3 bucket; the contained objects belong to this model element.        |
| `replicationConfiguration` → `S3ReplicationConfiguration`   | containment, [?]                  | Contains the s3 replication configuration element(s) that make up this s3 bucket; the contained objects belong to this model element.         |
| `bucketPolicy` → `S3BucketPolicy`                           | reference, [?]; opposite `bucket` | References the s3 bucket policy element(s) used as bucket policy by this s3 bucket; the target may be shared elsewhere in the model.          |

## `S3BucketEncryption`

Represents s3 bucket encryption in the PSM vocabulary. It specializes `TraceableElement` with the details needed for this modeling concern.

Direct supertypes: `TraceableElement`. Inherited attributes and marker capabilities are documented in the [shared kernel](../shared-kernel.md); this section lists every attribute declared by this class.

### Declared attributes

| Attribute          | Type and multiplicity | What it captures and why it exists                                                                                                                                                                                                                                                                                                                                                                                                                             | Accepted values and example                                                                                                            |
| ------------------ | --------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------- |
| `sseAlgorithm`     | `String` [1]          | Records the sse algorithm value for s3 bucket encryption. It keeps this decision explicit during review and transformation, so later steps do not have to infer it. Semantic validation: `KmsAlgorithmRequiresKmsKey` (kms algorithm requires kms key) in `mde/validation/psm/rules/storage.evl` the value must be present and non-blank. Generation role: The artifact generator references this feature in `mde/generation/awspsm-to-artifacts/lib/cfn.eol`. | A free-form `String`, subject to this class's semantic meaning and any EVL constraints. Example: `S3 Bucket Encryption Sse Algorithm`. |
| `bucketKeyEnabled` | `Boolean` [1]         | Records whether bucket key enabled applies to s3 bucket encryption. It preserves an explicit architectural or governance decision through review and transformation, so later steps do not have to infer it. The repository contains no direct EVL rule, ETL assignment, or artifact-generator read for this declared field. For now, it remains a model-level fact for review and future refinement.                                                          | Either `true` or `false`. Example: `true`.                                                                                             |

### Relationships

| Relationship        | Kind and multiplicity | Meaning in the model                                                                                                             |
| ------------------- | --------------------- | -------------------------------------------------------------------------------------------------------------------------------- |
| `kmsKey` → `KmsKey` | reference, [?]        | References the kms key element(s) used as kms key by this s3 bucket encryption; the target may be shared elsewhere in the model. |

## `S3OwnershipControls`

Represents s3 ownership controls in the PSM vocabulary. It specializes `TraceableElement` with the details needed for this modeling concern.

Direct supertypes: `TraceableElement`. Inherited attributes and marker capabilities are documented in the [shared kernel](../shared-kernel.md); this section lists every attribute declared by this class.

### Declared attributes

This class declares no attributes of its own. It inherits the attributes of its supertype, if any.

### Relationships

| Relationship                | Kind and multiplicity | Meaning in the model                                                                                                                   |
| --------------------------- | --------------------- | -------------------------------------------------------------------------------------------------------------------------------------- |
| `rules` → `S3OwnershipRule` | containment, [+]      | Contains the s3 ownership rule element(s) that make up this s3 ownership controls; the contained objects belong to this model element. |

## `S3OwnershipRule`

Represents s3 ownership rule in the PSM vocabulary. It specializes `TraceableElement` with the details needed for this modeling concern.

Direct supertypes: `TraceableElement`. Inherited attributes and marker capabilities are documented in the [shared kernel](../shared-kernel.md); this section lists every attribute declared by this class.

### Declared attributes

| Attribute         | Type and multiplicity | What it captures and why it exists                                                                                                                                                                                                                                                                                       | Accepted values and example                                                                                     |
| ----------------- | --------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ | --------------------------------------------------------------------------------------------------------------- |
| `objectOwnership` | `String` [1]          | Records the accountable person, team, or identity for object ownership for the s3 ownership rule. This keeps the decision explicit even when the element's class or relationships remain unchanged. Generation role: The artifact generator references this feature in `mde/generation/awspsm-to-artifacts/lib/cfn.eol`. | A free-form `String`, subject to this class's semantic meaning and any EVL constraints. Example: `orders-team`. |

### Relationships

This class declares no direct relationships.

## `S3LifecycleConfiguration`

Represents s3 lifecycle configuration in the PSM vocabulary. It specializes `TraceableElement` with the details needed for this modeling concern.

Direct supertypes: `TraceableElement`. Inherited attributes and marker capabilities are documented in the [shared kernel](../shared-kernel.md); this section lists every attribute declared by this class.

### Declared attributes

This class declares no attributes of its own. It inherits the attributes of its supertype, if any.

### Relationships

| Relationship                | Kind and multiplicity | Meaning in the model                                                                                                                        |
| --------------------------- | --------------------- | ------------------------------------------------------------------------------------------------------------------------------------------- |
| `rules` → `S3LifecycleRule` | containment, [+]      | Contains the s3 lifecycle rule element(s) that make up this s3 lifecycle configuration; the contained objects belong to this model element. |

## `S3LifecycleRule`

Represents s3 lifecycle rule in the PSM vocabulary. It specializes `TraceableElement` with the details needed for this modeling concern.

Direct supertypes: `TraceableElement`. Inherited attributes and marker capabilities are documented in the [shared kernel](../shared-kernel.md); this section lists every attribute declared by this class.

### Declared attributes

| Attribute                           | Type and multiplicity | What it captures and why it exists                                                                                                                                                                                                                                                                                                                                                                                  | Accepted values and example                                                                                                  |
| ----------------------------------- | --------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ---------------------------------------------------------------------------------------------------------------------------- |
| `ruleId`                            | `String` [1]          | Records the stable name/key/code used for rule id for the s3 lifecycle rule. This keeps the decision explicit even when the element's class or relationships remain unchanged. The repository contains no direct EVL rule, ETL assignment, or artifact-generator read for this declared field. For now, it remains a model-level fact for review and future refinement.                                             | A free-form `String`, subject to this class's semantic meaning and any EVL constraints. Example: `order-123`.                |
| `status`                            | `String` [1]          | Stores the status value on the s3 lifecycle rule. The field keeps the model explicit and reviewable during review and transformation instead of leaving the decision to an inferred default. The repository contains no direct EVL rule, ETL assignment, or artifact-generator read for this declared field. For now, it remains a model-level fact for review and future refinement.                               | A free-form `String`, subject to this class's semantic meaning and any EVL constraints. Example: `ACTIVE`.                   |
| `prefix`                            | `String` [1]          | For a s3 lifecycle rule, the model records the prefix value. Keeping the fact with its owning element lets validation and refinement inspect it before artifact generation. The repository contains no direct EVL rule, ETL assignment, or artifact-generator read for this declared field. For now, it remains a model-level fact for review and future refinement.                                                | A free-form `String`, subject to this class's semantic meaning and any EVL constraints. Example: `S3 Lifecycle Rule Prefix`. |
| `tagFiltersJson`                    | `String` [1]          | Records serialized JSON representation of tag filters for s3 lifecycle rule. It keeps preserving structured provider or contract detail explicit during review and transformation, so later steps do not have to infer it. The repository contains no direct EVL rule, ETL assignment, or artifact-generator read for this declared field. For now, it remains a model-level fact for review and future refinement. | A free-form `String`, subject to this class's semantic meaning and any EVL constraints. Example: `{"enabled":true}`.         |
| `expirationInDays`                  | `Integer` [1]         | Records expiration in days duration or limit, expressed in days for s3 lifecycle rule. It keeps an operational boundary that should not be left to provider defaults explicit during review and transformation, so later steps do not have to infer it. Generation role: The artifact generator references this feature in `mde/generation/awspsm-to-artifacts/lib/cfn.eol`.                                        | A numeric `Integer` value; use the unit or boundary documented for this attribute. Example: `1`.                             |
| `noncurrentVersionExpirationInDays` | `Integer` [1]         | Records compatibility/version marker for noncurrent version expiration in days for s3 lifecycle rule. It keeps compatibility and contract evolution explicit during review and transformation, so later steps do not have to infer it. Generation role: The artifact generator references this feature in `mde/generation/awspsm-to-artifacts/lib/cfn.eol`.                                                         | A numeric `Integer` value; use the unit or boundary documented for this attribute. Example: `1`.                             |

### Relationships

| Relationship                   | Kind and multiplicity | Meaning in the model                                                                                                                 |
| ------------------------------ | --------------------- | ------------------------------------------------------------------------------------------------------------------------------------ |
| `filter` → `S3LifecycleFilter` | containment, [?]      | Contains the s3 lifecycle filter element(s) that make up this s3 lifecycle rule; the contained objects belong to this model element. |
| `transitions` → `S3Transition` | containment, [*]      | Contains the s3 transition element(s) that make up this s3 lifecycle rule; the contained objects belong to this model element.       |

## `S3LifecycleFilter`

Represents s3 lifecycle filter in the PSM vocabulary. It specializes `TraceableElement` with the details needed for this modeling concern.

Direct supertypes: `TraceableElement`. Inherited attributes and marker capabilities are documented in the [shared kernel](../shared-kernel.md); this section lists every attribute declared by this class.

### Declared attributes

| Attribute               | Type and multiplicity | What it captures and why it exists                                                                                                                                                                                                                                                                                                                                                      | Accepted values and example                                                                                                    |
| ----------------------- | --------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------ |
| `prefix`                | `String` [1]          | Stores the prefix value on the s3 lifecycle filter. The field keeps the model explicit and reviewable during review and transformation instead of leaving the decision to an inferred default. The repository contains no direct EVL rule, ETL assignment, or artifact-generator read for this declared field. For now, it remains a model-level fact for review and future refinement. | A free-form `String`, subject to this class's semantic meaning and any EVL constraints. Example: `S3 Lifecycle Filter Prefix`. |
| `objectSizeGreaterThan` | `Integer` [1]         | Records the object size greater than value for the s3 lifecycle filter. This keeps the decision explicit even when the element's class or relationships remain unchanged. The repository contains no direct EVL rule, ETL assignment, or artifact-generator read for this declared field. For now, it remains a model-level fact for review and future refinement.                      | A numeric `Integer` value; use the unit or boundary documented for this attribute. Example: `1`.                               |
| `objectSizeLessThan`    | `Integer` [1]         | For a s3 lifecycle filter, the model records the object size less than value. Keeping the fact with its owning element lets validation and refinement inspect it before artifact generation. The repository contains no direct EVL rule, ETL assignment, or artifact-generator read for this declared field. For now, it remains a model-level fact for review and future refinement.   | A numeric `Integer` value; use the unit or boundary documented for this attribute. Example: `1`.                               |

### Relationships

| Relationship           | Kind and multiplicity | Meaning in the model                                                                                                             |
| ---------------------- | --------------------- | -------------------------------------------------------------------------------------------------------------------------------- |
| `tags` → `S3TagFilter` | containment, [*]      | Contains the s3 tag filter element(s) that make up this s3 lifecycle filter; the contained objects belong to this model element. |

## `S3TagFilter`

Represents s3 tag filter in the PSM vocabulary. It specializes `TraceableElement` with the details needed for this modeling concern.

Direct supertypes: `TraceableElement`. Inherited attributes and marker capabilities are documented in the [shared kernel](../shared-kernel.md); this section lists every attribute declared by this class.

### Declared attributes

| Attribute | Type and multiplicity | What it captures and why it exists                                                                                                                                                                                                                                                                                                                              | Accepted values and example                                                                                 |
| --------- | --------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ----------------------------------------------------------------------------------------------------------- |
| `key`     | `String` [1]          | Records stable name/key/code used for key for s3 tag filter. It keeps this decision explicit during review and transformation, so later steps do not have to infer it. The repository contains no direct EVL rule, ETL assignment, or artifact-generator read for this declared field. For now, it remains a model-level fact for review and future refinement. | A free-form `String`, subject to this class's semantic meaning and any EVL constraints. Example: `orderId`. |
| `value`   | `String` [1]          | Records the value value for s3 tag filter. It keeps this decision explicit during review and transformation, so later steps do not have to infer it. The repository contains no direct EVL rule, ETL assignment, or artifact-generator read for this declared field. For now, it remains a model-level fact for review and future refinement.                   | A free-form `String`, subject to this class's semantic meaning and any EVL constraints. Example: `READY`.   |

### Relationships

This class declares no direct relationships.

## `S3Transition`

Represents s3 transition in the PSM vocabulary. It specializes `TraceableElement` with the details needed for this modeling concern.

Direct supertypes: `TraceableElement`. Inherited attributes and marker capabilities are documented in the [shared kernel](../shared-kernel.md); this section lists every attribute declared by this class.

### Declared attributes

| Attribute          | Type and multiplicity | What it captures and why it exists                                                                                                                                                                                                                                                                                                                                      | Accepted values and example                                                                                  |
| ------------------ | --------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------ |
| `storageClass`     | `String` [1]          | For a s3 transition, the model records the storage class value. Keeping the fact with its owning element lets validation and refinement inspect it before artifact generation. The repository contains no direct EVL rule, ETL assignment, or artifact-generator read for this declared field. For now, it remains a model-level fact for review and future refinement. | A free-form `String`, subject to this class's semantic meaning and any EVL constraints. Example: `STANDARD`. |
| `transitionInDays` | `Integer` [1]         | For a s3 transition, the model records the transition in days duration or limit, expressed in days. Keeping the fact with its owning element lets validation and refinement inspect it before artifact generation. Generation role: The artifact generator references this feature in `mde/generation/awspsm-to-artifacts/lib/cfn.eol`.                                 | A numeric `Integer` value; use the unit or boundary documented for this attribute. Example: `1`.             |

### Relationships

This class declares no direct relationships.

## `S3PublicAccessBlockConfiguration`

Represents s3 public access block configuration in the PSM vocabulary. It specializes `TraceableElement` with the details needed for this modeling concern.

Direct supertypes: `TraceableElement`. Inherited attributes and marker capabilities are documented in the [shared kernel](../shared-kernel.md); this section lists every attribute declared by this class.

### Declared attributes

| Attribute               | Type and multiplicity | What it captures and why it exists                                                                                                                                                                                                                                                                                                     | Accepted values and example                |
| ----------------------- | --------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------ |
| `blockPublicAcls`       | `Boolean` [1]         | Records whether block public acls applies to s3 public access block configuration. This keeps the decision explicit even when the element's class or relationships remain unchanged. Generation role: The artifact generator references this feature in `mde/generation/awspsm-to-artifacts/lib/cfn.eol`.                              | Either `true` or `false`. Example: `true`. |
| `blockPublicPolicy`     | `Boolean` [1]         | For a s3 public access block configuration, the model records whether block public policy applies. Keeping the fact with its owning element lets validation and refinement inspect it before artifact generation. Generation role: The artifact generator references this feature in `mde/generation/awspsm-to-artifacts/lib/cfn.eol`. | Either `true` or `false`. Example: `true`. |
| `ignorePublicAcls`      | `Boolean` [1]         | For a s3 public access block configuration, the model records whether ignore public acls applies. Keeping the fact with its owning element lets validation and refinement inspect it before artifact generation. Generation role: The artifact generator references this feature in `mde/generation/awspsm-to-artifacts/lib/cfn.eol`.  | Either `true` or `false`. Example: `true`. |
| `restrictPublicBuckets` | `Boolean` [1]         | Records whether restrict public buckets applies to s3 public access block configuration. The field records an explicit architectural or governance decision as an explicit, reviewable input. Generation role: The artifact generator references this feature in `mde/generation/awspsm-to-artifacts/lib/cfn.eol`.                     | Either `true` or `false`. Example: `true`. |

### Relationships

This class declares no direct relationships.

## `S3NotificationConfiguration`

Represents s3 notification configuration in the PSM vocabulary. It specializes `TraceableElement` with the details needed for this modeling concern.

Direct supertypes: `TraceableElement`. Inherited attributes and marker capabilities are documented in the [shared kernel](../shared-kernel.md); this section lists every attribute declared by this class.

### Declared attributes

| Attribute            | Type and multiplicity | What it captures and why it exists                                                                                                                                                                                                                                                                    | Accepted values and example                |
| -------------------- | --------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------ |
| `eventBridgeEnabled` | `Boolean` [1]         | Records whether event bridge enabled applies to s3 notification configuration. This keeps the decision explicit even when the element's class or relationships remain unchanged. Generation role: The artifact generator references this feature in `mde/generation/awspsm-to-artifacts/lib/cfn.eol`. | Either `true` or `false`. Example: `true`. |

### Relationships

| Relationship                   | Kind and multiplicity                      | Meaning in the model                                                                                                                              |
| ------------------------------ | ------------------------------------------ | ------------------------------------------------------------------------------------------------------------------------------------------------- |
| `rules` → `S3NotificationRule` | containment, [*]; opposite `configuration` | Contains the s3 notification rule element(s) that make up this s3 notification configuration; the contained objects belong to this model element. |

## `S3NotificationRule`

Represents s3 notification rule in the PSM vocabulary. It specializes `TraceableElement` with the details needed for this modeling concern.

Direct supertypes: `TraceableElement`. Inherited attributes and marker capabilities are documented in the [shared kernel](../shared-kernel.md); this section lists every attribute declared by this class.

### Declared attributes

| Attribute      | Type and multiplicity | What it captures and why it exists                                                                                                                                                                                                                                                                                                                                                                                                                                                                                 | Accepted values and example                                                                                                            |
| -------------- | --------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ | -------------------------------------------------------------------------------------------------------------------------------------- |
| `eventTypes`   | `String` [*]          | For a s3 notification rule, the model records the event types value. Keeping the fact with its owning element lets validation and refinement inspect it before artifact generation. Semantic validation: `NotificationRuleHasEventAndDestination` (notification rule has event and destination) in `mde/validation/psm/rules/storage.evl` the collection or referenced set must not be empty. Generation role: The artifact generator references this feature in `mde/generation/awspsm-to-artifacts/lib/cfn.eol`. | A collection of values. Example: [`BUSINESS`, `BUSINESS-2`].                                                                           |
| `filterPrefix` | `String` [1]          | Records the filter prefix value for s3 notification rule. It keeps this decision explicit during review and transformation, so later steps do not have to infer it. Generation role: The artifact generator references this feature in `mde/generation/awspsm-to-artifacts/lib/cfn.eol`.                                                                                                                                                                                                                           | A free-form `String`, subject to this class's semantic meaning and any EVL constraints. Example: `S3 Notification Rule Filter Prefix`. |
| `filterSuffix` | `String` [1]          | For a s3 notification rule, the model records the filter suffix value. Keeping the fact with its owning element lets validation and refinement inspect it before artifact generation. Generation role: The artifact generator references this feature in `mde/generation/awspsm-to-artifacts/lib/cfn.eol`.                                                                                                                                                                                                         | A free-form `String`, subject to this class's semantic meaning and any EVL constraints. Example: `S3 Notification Rule Filter Suffix`. |

### Relationships

| Relationship                                    | Kind and multiplicity                       | Meaning in the model                                                                                                                                         |
| ----------------------------------------------- | ------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| `configuration` → `S3NotificationConfiguration` | reference; read-only, [1]; opposite `rules` | References the s3 notification configuration element(s) used as configuration by this s3 notification rule; the target may be shared elsewhere in the model. |
| `destination` → `S3NotificationDestination`     | reference, [?]                              | References the s3 notification destination element(s) used as destination by this s3 notification rule; the target may be shared elsewhere in the model.     |

## `S3ReplicationConfiguration`

Represents s3 replication configuration in the PSM vocabulary. It specializes `TraceableElement` with the details needed for this modeling concern.

Direct supertypes: `TraceableElement`. Inherited attributes and marker capabilities are documented in the [shared kernel](../shared-kernel.md); this section lists every attribute declared by this class.

### Declared attributes

| Attribute   | Type and multiplicity | What it captures and why it exists                                                                                                                                                                                                                                                                                                                                                                                                                                                                                         | Accepted values and example                                                                                          |
| ----------- | --------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | -------------------------------------------------------------------------------------------------------------------- |
| `rulesJson` | `String` [1]          | Records serialized JSON representation of rules for s3 replication configuration. It keeps preserving structured provider or contract detail explicit during review and transformation, so later steps do not have to infer it. Semantic validation: `ReplicationHasRoleAndRules` (replication has role and rules) in `mde/validation/psm/rules/storage.evl` the value must be present and non-blank. Generation role: The artifact generator references this feature in `mde/generation/awspsm-to-artifacts/lib/cfn.eol`. | A free-form `String`, subject to this class's semantic meaning and any EVL constraints. Example: `{"enabled":true}`. |

### Relationships

| Relationship                  | Kind and multiplicity | Meaning in the model                                                                                                                            |
| ----------------------------- | --------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------- |
| `rules` → `S3ReplicationRule` | containment, [*]      | Contains the s3 replication rule element(s) that make up this s3 replication configuration; the contained objects belong to this model element. |
| `role` → `IamRole`            | reference, [1]        | References the iam role element(s) used as role by this s3 replication configuration; the target may be shared elsewhere in the model.          |

## `S3ReplicationRule`

Represents s3 replication rule in the PSM vocabulary. It specializes `TraceableElement` with the details needed for this modeling concern.

Direct supertypes: `TraceableElement`. Inherited attributes and marker capabilities are documented in the [shared kernel](../shared-kernel.md); this section lists every attribute declared by this class.

### Declared attributes

| Attribute              | Type and multiplicity | What it captures and why it exists                                                                                                                                                                                                                                                                                                                                                                 | Accepted values and example                                                                                                                               |
| ---------------------- | --------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `ruleId`               | `String` [1]          | Records stable name/key/code used for rule id for s3 replication rule. It keeps this decision explicit during review and transformation, so later steps do not have to infer it. The repository contains no direct EVL rule, ETL assignment, or artifact-generator read for this declared field. For now, it remains a model-level fact for review and future refinement.                          | A free-form `String`, subject to this class's semantic meaning and any EVL constraints. Example: `order-123`.                                             |
| `status`               | `String` [1]          | For a s3 replication rule, the model records the status value. Keeping the fact with its owning element lets validation and refinement inspect it before artifact generation. The repository contains no direct EVL rule, ETL assignment, or artifact-generator read for this declared field. For now, it remains a model-level fact for review and future refinement.                             | A free-form `String`, subject to this class's semantic meaning and any EVL constraints. Example: `ACTIVE`.                                                |
| `destinationBucketArn` | `String` [1]          | Records destination selected for destination bucket arn for s3 replication rule. It keeps this decision explicit during review and transformation, so later steps do not have to infer it. The repository contains no direct EVL rule, ETL assignment, or artifact-generator read for this declared field. For now, it remains a model-level fact for review and future refinement.                | A free-form `String`, subject to this class's semantic meaning and any EVL constraints. Example: `arn:aws:lambda:us-east-1:123456789012:function:orders`. |
| `prefix`               | `String` [1]          | Stores the prefix value on the s3 replication rule. The field keeps the model explicit and reviewable during review and transformation instead of leaving the decision to an inferred default. The repository contains no direct EVL rule, ETL assignment, or artifact-generator read for this declared field. For now, it remains a model-level fact for review and future refinement.            | A free-form `String`, subject to this class's semantic meaning and any EVL constraints. Example: `S3 Replication Rule Prefix`.                            |
| `filterJson`           | `String` [1]          | For a s3 replication rule, the model records the serialized JSON representation of filter. Keeping the fact with its owning element lets validation and refinement inspect it before artifact generation. The repository contains no direct EVL rule, ETL assignment, or artifact-generator read for this declared field. For now, it remains a model-level fact for review and future refinement. | A free-form `String`, subject to this class's semantic meaning and any EVL constraints. Example: `{"enabled":true}`.                                      |

### Relationships

| Relationship                               | Kind and multiplicity | Meaning in the model                                                                                                                          |
| ------------------------------------------ | --------------------- | --------------------------------------------------------------------------------------------------------------------------------------------- |
| `filter` → `S3LifecycleFilter`             | containment, [?]      | Contains the s3 lifecycle filter element(s) that make up this s3 replication rule; the contained objects belong to this model element.        |
| `destination` → `S3ReplicationDestination` | containment, [?]      | Contains the s3 replication destination element(s) that make up this s3 replication rule; the contained objects belong to this model element. |

## `S3ReplicationDestination`

Represents s3 replication destination in the PSM vocabulary. It specializes `TraceableElement` with the details needed for this modeling concern.

Direct supertypes: `TraceableElement`. Inherited attributes and marker capabilities are documented in the [shared kernel](../shared-kernel.md); this section lists every attribute declared by this class.

### Declared attributes

| Attribute      | Type and multiplicity | What it captures and why it exists                                                                                                                                                                                                                                                                                                                                                                        | Accepted values and example                                                                                                                               |
| -------------- | --------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `bucketArn`    | `String` [1]          | Records AWS ARN used for bucket for s3 replication destination. It keeps this decision explicit during review and transformation, so later steps do not have to infer it. The repository contains no direct EVL rule, ETL assignment, or artifact-generator read for this declared field. For now, it remains a model-level fact for review and future refinement.                                        | A free-form `String`, subject to this class's semantic meaning and any EVL constraints. Example: `arn:aws:lambda:us-east-1:123456789012:function:orders`. |
| `accountId`    | `String` [1]          | For a s3 replication destination, the model records the stable name/key/code used for account id. Keeping the fact with its owning element lets validation and refinement inspect it before artifact generation. The repository contains no direct EVL rule, ETL assignment, or artifact-generator read for this declared field. For now, it remains a model-level fact for review and future refinement. | A free-form `String`, subject to this class's semantic meaning and any EVL constraints. Example: `order-123`.                                             |
| `storageClass` | `String` [1]          | Records the storage class value for s3 replication destination. It keeps this decision explicit during review and transformation, so later steps do not have to infer it. The repository contains no direct EVL rule, ETL assignment, or artifact-generator read for this declared field. For now, it remains a model-level fact for review and future refinement.                                        | A free-form `String`, subject to this class's semantic meaning and any EVL constraints. Example: `STANDARD`.                                              |

### Relationships

| Relationship               | Kind and multiplicity | Meaning in the model                                                                                                                           |
| -------------------------- | --------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------- |
| `replicaKmsKey` → `KmsKey` | reference, [?]        | References the kms key element(s) used as replica kms key by this s3 replication destination; the target may be shared elsewhere in the model. |

## `S3BucketPolicy`

Represents s3 bucket policy in the PSM vocabulary. It specializes `AwsResource` with the details needed for this modeling concern.

Direct supertypes: `AwsResource`. Inherited attributes and marker capabilities are documented in the [shared kernel](../shared-kernel.md); this section lists every attribute declared by this class.

### Declared attributes

| Attribute            | Type and multiplicity | What it captures and why it exists                                                                                                                                                                                                                                                                                                                                                                                     | Accepted values and example                                                                                          |
| -------------------- | --------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | -------------------------------------------------------------------------------------------------------------------- |
| `policyDocumentJson` | `String` [1]          | Records serialized JSON representation of policy document for s3 bucket policy. It keeps preserving structured provider or contract detail explicit during review and transformation, so later steps do not have to infer it. The repository contains no direct EVL rule, ETL assignment, or artifact-generator read for this declared field. For now, it remains a model-level fact for review and future refinement. | A free-form `String`, subject to this class's semantic meaning and any EVL constraints. Example: `{"enabled":true}`. |

### Relationships

| Relationship                           | Kind and multiplicity                   | Meaning in the model                                                                                                                |
| -------------------------------------- | --------------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------- |
| `policyDocument` → `IamPolicyDocument` | containment, [?]                        | Contains the iam policy document element(s) that make up this s3 bucket policy; the contained objects belong to this model element. |
| `bucket` → `S3Bucket`                  | reference, [1]; opposite `bucketPolicy` | References the s3 bucket element(s) used as bucket by this s3 bucket policy; the target may be shared elsewhere in the model.       |
