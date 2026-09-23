# DynamoDB and S3 resources

Storage classes expose the AWS details needed for DynamoDB key design and S3 governance, lifecycle, notification, replication, and encryption.

Source: `mde/metamodels/psm/awspsm-storage.emf`.

## `DynamoDbTable`

An AWS DynamoDB table resource. It connects attribute definitions and key schema to capacity, indexes, streams, TTL, backup, replication, encryption, and resource policy decisions.

Direct supertypes: `AwsResource`. Inherited attributes and marker capabilities are documented in the [shared kernel](../shared-kernel.md); this section lists every attribute declared by this class.

### Declared attributes

| Attribute                           | Type and multiplicity     | What it captures and why it exists                                                                                                                                                                                                                                                                                                                                                                                                                  | Accepted values and example                                                                                          |
| ----------------------------------- | ------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | -------------------------------------------------------------------------------------------------------------------- |
| `tableName`                         | `String` [1]              | The table name that identifies this dynamo db table in the model and its generated AWS configuration.                                                                                                                                                                                                                                                                                                                                               | A free-form `String`, subject to this class's semantic meaning and any EVL constraints. Example: `orders-prod`.      |
| `deletionProtectionEnabled`         | `Boolean` [1]             | Whether deletion protection is enabled for this dynamo db table. The flag turns an operational choice into explicit model data.                                                                                                                                                                                                                                                                                                                     | Either `true` or `false`. Example: `true`.                                                                           |
| `pointInTimeRecoveryEnabled`        | `Boolean` [1]             | Whether point in time recovery is enabled for this dynamo db table. The flag turns an operational choice into explicit model data.                                                                                                                                                                                                                                                                                                                  | Either `true` or `false`. Example: `true`.                                                                           |
| `contributorInsightsEnabled`        | `Boolean` [1]             | Whether contributor insights is enabled for this dynamo db table. The flag turns an operational choice into explicit model data. Transformation role: ETL rule `DataStore2DynamoDbTable` in `mde/transformations/pim-to-awspsm/data-messaging-events.etl` assigns or materializes this feature while refining `DynamoDbTable`. Generation role: The artifact generator references this feature in `mde/generation/awspsm-to-artifacts/lib/cfn.eol`. | Either `true` or `false`. Example: `true`.                                                                           |
| `importSourceSpecificationRequired` | `Boolean` [1]             | Whether this dynamo db table requires import source specification. The flag records an obligation that validation and generation can carry forward.                                                                                                                                                                                                                                                                                                 | Either `true` or `false`. Example: `true`.                                                                           |
| `resourcePolicyJson`                | `String` [1]              | The serialized resource policy attached to this resource. It keeps resource-level access rules distinct from the identity policies of callers. Within `DynamoDbTable`, it applies to this specific element and its role in the surrounding model.                                                                                                                                                                                                   | A free-form `String`, subject to this class's semantic meaning and any EVL constraints. Example: `{"enabled":true}`. |
| `billingMode`                       | `DynamoDbBillingMode` [1] | The controlled value used for billing mode on this dynamo db table. It keeps the distinction explicit in the abstract syntax so later rules can interpret it consistently.                                                                                                                                                                                                                                                                          | Exactly one of: `PAY_PER_REQUEST`, `PROVISIONED`. Example: `PAY_PER_REQUEST`.                                        |
| `tableClass`                        | `DynamoDbTableClass` [1]  | The controlled value used for table class on this dynamo db table. It keeps the distinction explicit in the abstract syntax so later rules can interpret it consistently.                                                                                                                                                                                                                                                                           | Exactly one of: `STANDARD`, `STANDARD_INFREQUENT_ACCESS`. Example: `STANDARD`.                                       |

### Relationships

| Relationship                                                  | Kind and multiplicity | Meaning in the model                                                                                                                                                                                                                      |
| ------------------------------------------------------------- | --------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `attributeDefinitions` → `DynamoDbAttributeDefinition`        | containment, [+]      | The DynamoDB scalar attributes used by the table or its index key schemas.                                                                                                                                                                |
| `keySchema` → `DynamoDbKeySchemaElement`                      | containment, [+]      | The `keySchema` containment on `DynamoDbTable` defines the partition and sort key roles used by the table or index. The `DynamoDbKeySchemaElement` objects are owned by `DynamoDbTable` and remain part of its model subtree.             |
| `localSecondaryIndexes` → `DynamoDbLocalSecondaryIndex`       | containment, [*]      | Indexes sharing the table partition key and providing alternative sort-key access.                                                                                                                                                        |
| `globalSecondaryIndexes` → `DynamoDbGlobalSecondaryIndex`     | containment, [*]      | Indexes with independent partition and sort keys maintained for this table.                                                                                                                                                               |
| `replicas` → `DynamoDbReplicaSpecification`                   | containment, [*]      | The `replicas` containment on `DynamoDbTable` keeps the regional replica decisions for the table. The `DynamoDbReplicaSpecification` objects are owned by `DynamoDbTable` and remain part of its model subtree.                           |
| `provisionedThroughput` → `DynamoDbProvisionedThroughput`     | containment, [?]      | Read and write capacity settings used when the table or index operates in provisioned mode.                                                                                                                                               |
| `onDemandThroughput` → `DynamoDbOnDemandThroughput`           | containment, [?]      | Optional request-unit ceilings used while the table or index operates in on-demand mode.                                                                                                                                                  |
| `streamSpecification` → `DynamoDbStreamSpecification`         | containment, [?]      | The `streamSpecification` containment on `DynamoDbTable` declares the change stream emitted by the table. The `DynamoDbStreamSpecification` objects are owned by `DynamoDbTable` and remain part of its model subtree.                    |
| `timeToLiveSpecification` → `DynamoDbTimeToLiveSpecification` | containment, [?]      | The `timeToLiveSpecification` containment on `DynamoDbTable` declares the field and behavior used for automatic expiry. The `DynamoDbTimeToLiveSpecification` objects are owned by `DynamoDbTable` and remain part of its model subtree.  |
| `sseSpecification` → `DynamoDbSseSpecification`               | containment, [?]      | The table's server-side encryption configuration.                                                                                                                                                                                         |
| `backupPolicy` → `DynamoDbBackupPolicy`                       | containment, [?]      | The `backupPolicy` containment on `DynamoDbTable` attaches the access or governance decision represented by backup policy. The `DynamoDbBackupPolicy` objects are owned by `DynamoDbTable` and remain part of its model subtree.          |
| `resourcePolicy` → `IamPolicyDocument`                        | containment, [?]      | The `resourcePolicy` containment on `DynamoDbTable` attaches the resource-level policy controlling access to the secret or data store. The `IamPolicyDocument` objects are owned by `DynamoDbTable` and remain part of its model subtree. |
| `kmsKey` → `KmsKey`                                           | reference, [?]        | The `kmsKey` reference on `DynamoDbTable` selects the KMS key used for provider-side encryption. A `KmsKey` can remain independently owned and can participate in other parts of the model.                                               |

## `DynamoDbAttributeDefinition`

`DynamoDbAttributeDefinition` is a DynamoDB deployment record in the AWS platform-specific model. It carries the storage setting for dynamo db attribute definition. Its declaration gives the concept a precise home through its declared properties. Its references provide the wiring that lets generation assemble the corresponding AWS design.

Direct supertypes: `TraceableElement`. Inherited attributes and marker capabilities are documented in the [shared kernel](../shared-kernel.md); this section lists every attribute declared by this class.

### Declared attributes

| Attribute       | Type and multiplicity       | What it captures and why it exists                                                                                                                                   | Accepted values and example                                                                                 |
| --------------- | --------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ----------------------------------------------------------------------------------------------------------- |
| `attributeName` | `String` [1]                | The attribute name that identifies this dynamo db attribute definition in the model and its generated AWS configuration.                                             | A free-form `String`, subject to this class's semantic meaning and any EVL constraints. Example: `orderId`. |
| `attributeType` | `DynamoDbAttributeType` [1] | The DynamoDB scalar type of this key attribute. Generation role: The artifact generator references this feature in `mde/generation/awspsm-to-artifacts/lib/cfn.eol`. | Exactly one of: `S`, `N`, `B`. Example: `S`.                                                                |

### Relationships

This class declares no direct relationships.

## `DynamoDbKeySchemaElement`

`DynamoDbKeySchemaElement` is a DynamoDB deployment record in the AWS platform-specific model. It carries the storage setting for dynamo db key schema element. Its declaration gives the concept a precise home through its declared properties. Its references provide the wiring that lets generation assemble the corresponding AWS design.

Direct supertypes: `TraceableElement`. Inherited attributes and marker capabilities are documented in the [shared kernel](../shared-kernel.md); this section lists every attribute declared by this class.

### Declared attributes

| Attribute       | Type and multiplicity | What it captures and why it exists                                                                                                                                                  | Accepted values and example                                                                                                         |
| --------------- | --------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------- |
| `attributeName` | `String` [1]          | The attribute name that identifies this dynamo db key schema element in the model and its generated AWS configuration.                                                              | A free-form `String`, subject to this class's semantic meaning and any EVL constraints. Example: `DynamoDbKeySchemaElementExample`. |
| `keyType`       | `DynamoDbKeyType` [1] | The controlled value used for key type on this dynamo db key schema element. It keeps the distinction explicit in the abstract syntax so later rules can interpret it consistently. | Exactly one of: `HASH`, `RANGE`. Example: `HASH`.                                                                                   |

### Relationships

This class declares no direct relationships.

## `DynamoDbProjection`

`DynamoDbProjection` is a DynamoDB deployment record in the AWS platform-specific model. It carries the storage setting for dynamo db projection. Its declaration gives the concept a precise home through its declared properties. Its references provide the wiring that lets generation assemble the corresponding AWS design.

Direct supertypes: `TraceableElement`. Inherited attributes and marker capabilities are documented in the [shared kernel](../shared-kernel.md); this section lists every attribute declared by this class.

### Declared attributes

| Attribute          | Type and multiplicity        | What it captures and why it exists                                                                                                                                                                                                                 | Accepted values and example                                    |
| ------------------ | ---------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | -------------------------------------------------------------- |
| `projectionType`   | `DynamoDbProjectionType` [1] | The index projection strategy, deciding whether only keys, all table attributes, or a named subset are copied into the index. Generation role: The artifact generator references this feature in `mde/generation/awspsm-to-artifacts/lib/cfn.eol`. | Exactly one of: `ALL`, `KEYS_ONLY`, `INCLUDE`. Example: `ALL`. |
| `nonKeyAttributes` | `String` [*]                 | The table attributes projected into an index when the projection type selects an explicit subset. Generation role: The artifact generator references this feature in `mde/generation/awspsm-to-artifacts/lib/cfn.eol`.                             | A collection of values. Example: [`orderId`, `orderId-2`].     |

### Relationships

This class declares no direct relationships.

## `DynamoDbProvisionedThroughput`

`DynamoDbProvisionedThroughput` is a DynamoDB deployment record in the AWS platform-specific model. It carries the storage setting for dynamo db provisioned throughput. Its declaration gives the concept a precise home through its declared properties. Its references provide the wiring that lets generation assemble the corresponding AWS design.

Direct supertypes: `TraceableElement`. Inherited attributes and marker capabilities are documented in the [shared kernel](../shared-kernel.md); this section lists every attribute declared by this class.

### Declared attributes

| Attribute            | Type and multiplicity | What it captures and why it exists                                                                                                                                                                                                                                                                   | Accepted values and example                                                                      |
| -------------------- | --------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------ |
| `readCapacityUnits`  | `Integer` [1]         | The numeric value used for read capacity units on this dynamo db provisioned throughput. Keeping the quantity in the model lets validation and generation apply the same boundary consistently.                                                                                                      | A numeric `Integer` value; use the unit or boundary documented for this attribute. Example: `1`. |
| `writeCapacityUnits` | `Integer` [1]         | Stores the write capacity units value on the dynamo db provisioned throughput. The field records an operational boundary explicitly instead of leaving it to provider defaults. Generation role: The artifact generator references this feature in `mde/generation/awspsm-to-artifacts/lib/cfn.eol`. | A numeric `Integer` value; use the unit or boundary documented for this attribute. Example: `1`. |

### Relationships

This class declares no direct relationships.

## `DynamoDbOnDemandThroughput`

`DynamoDbOnDemandThroughput` is a DynamoDB deployment record in the AWS platform-specific model. It carries the storage setting for dynamo db on demand throughput. Its declaration gives the concept a precise home through its declared properties. Its references provide the wiring that lets generation assemble the corresponding AWS design.

Direct supertypes: `TraceableElement`. Inherited attributes and marker capabilities are documented in the [shared kernel](../shared-kernel.md); this section lists every attribute declared by this class.

### Declared attributes

| Attribute              | Type and multiplicity | What it captures and why it exists                                                                                                                                                                | Accepted values and example                                                                      |
| ---------------------- | --------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------ |
| `maxReadRequestUnits`  | `Integer` [1]         | The numeric value used for max read request units on this dynamo db on demand throughput. Keeping the quantity in the model lets validation and generation apply the same boundary consistently.  | A numeric `Integer` value; use the unit or boundary documented for this attribute. Example: `1`. |
| `maxWriteRequestUnits` | `Integer` [1]         | The numeric value used for max write request units on this dynamo db on demand throughput. Keeping the quantity in the model lets validation and generation apply the same boundary consistently. | A numeric `Integer` value; use the unit or boundary documented for this attribute. Example: `1`. |

### Relationships

This class declares no direct relationships.

## `DynamoDbLocalSecondaryIndex`

`DynamoDbLocalSecondaryIndex` is a DynamoDB deployment record in the AWS platform-specific model. It carries the storage setting for dynamo db local secondary index. Its declaration gives the concept a precise home through key schema, projection. Its references provide the wiring that lets generation assemble the corresponding AWS design.

Direct supertypes: `TraceableElement`. Inherited attributes and marker capabilities are documented in the [shared kernel](../shared-kernel.md); this section lists every attribute declared by this class.

### Declared attributes

| Attribute   | Type and multiplicity | What it captures and why it exists                                                                                    | Accepted values and example                                                                                                            |
| ----------- | --------------------- | --------------------------------------------------------------------------------------------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------- |
| `indexName` | `String` [1]          | The index name that identifies this dynamo db local secondary index in the model and its generated AWS configuration. | A free-form `String`, subject to this class's semantic meaning and any EVL constraints. Example: `DynamoDbLocalSecondaryIndexExample`. |

### Relationships

| Relationship                             | Kind and multiplicity | Meaning in the model                                                                                                                                                                                                                                      |
| ---------------------------------------- | --------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `keySchema` → `DynamoDbKeySchemaElement` | containment, [+]      | The `keySchema` containment on `DynamoDbLocalSecondaryIndex` defines the partition and sort key roles used by the table or index. The `DynamoDbKeySchemaElement` objects are owned by `DynamoDbLocalSecondaryIndex` and remain part of its model subtree. |
| `projection` → `DynamoDbProjection`      | containment, [1]      | The `projection` containment on `DynamoDbLocalSecondaryIndex` defines which table attributes are available through the index. The `DynamoDbProjection` objects are owned by `DynamoDbLocalSecondaryIndex` and remain part of its model subtree.           |

## `DynamoDbGlobalSecondaryIndex`

`DynamoDbGlobalSecondaryIndex` is a DynamoDB deployment record in the AWS platform-specific model. It carries the storage setting for dynamo db global secondary index. Its declaration gives the concept a precise home through key schema, projection, provisioned throughput. Its references provide the wiring that lets generation assemble the corresponding AWS design.

Direct supertypes: `TraceableElement`. Inherited attributes and marker capabilities are documented in the [shared kernel](../shared-kernel.md); this section lists every attribute declared by this class.

### Declared attributes

| Attribute   | Type and multiplicity | What it captures and why it exists                                                                                     | Accepted values and example                                                                                                             |
| ----------- | --------------------- | ---------------------------------------------------------------------------------------------------------------------- | --------------------------------------------------------------------------------------------------------------------------------------- |
| `indexName` | `String` [1]          | The index name that identifies this dynamo db global secondary index in the model and its generated AWS configuration. | A free-form `String`, subject to this class's semantic meaning and any EVL constraints. Example: `DynamoDbGlobalSecondaryIndexExample`. |

### Relationships

| Relationship                                              | Kind and multiplicity | Meaning in the model                                                                                                                                                                                                                                        |
| --------------------------------------------------------- | --------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `keySchema` → `DynamoDbKeySchemaElement`                  | containment, [+]      | The `keySchema` containment on `DynamoDbGlobalSecondaryIndex` defines the partition and sort key roles used by the table or index. The `DynamoDbKeySchemaElement` objects are owned by `DynamoDbGlobalSecondaryIndex` and remain part of its model subtree. |
| `projection` → `DynamoDbProjection`                       | containment, [1]      | The `projection` containment on `DynamoDbGlobalSecondaryIndex` defines which table attributes are available through the index. The `DynamoDbProjection` objects are owned by `DynamoDbGlobalSecondaryIndex` and remain part of its model subtree.           |
| `provisionedThroughput` → `DynamoDbProvisionedThroughput` | containment, [?]      | Read and write capacity settings used when the table or index operates in provisioned mode.                                                                                                                                                                 |
| `onDemandThroughput` → `DynamoDbOnDemandThroughput`       | containment, [?]      | Optional request-unit ceilings used while the table or index operates in on-demand mode.                                                                                                                                                                    |

## `DynamoDbReplicaSpecification`

`DynamoDbReplicaSpecification` is a DynamoDB deployment record in the AWS platform-specific model. It carries the storage setting for dynamo db replica specification. Its declaration gives the concept a precise home through kms key. Its references provide the wiring that lets generation assemble the corresponding AWS design.

Direct supertypes: `TraceableElement`. Inherited attributes and marker capabilities are documented in the [shared kernel](../shared-kernel.md); this section lists every attribute declared by this class.

### Declared attributes

| Attribute                    | Type and multiplicity | What it captures and why it exists                                                                                                                 | Accepted values and example                                                                                   |
| ---------------------------- | --------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------- |
| `region`                     | `String` [1]          | The AWS region hosting this replica of the global table.                                                                                           | A free-form `String`, subject to this class's semantic meaning and any EVL constraints. Example: `us-east-1`. |
| `pointInTimeRecoveryEnabled` | `Boolean` [1]         | Whether point in time recovery is enabled for this dynamo db replica specification. The flag turns an operational choice into explicit model data. | Either `true` or `false`. Example: `true`.                                                                    |
| `deletionProtectionEnabled`  | `Boolean` [1]         | Whether deletion protection is enabled for this dynamo db replica specification. The flag turns an operational choice into explicit model data.    | Either `true` or `false`. Example: `true`.                                                                    |

### Relationships

| Relationship        | Kind and multiplicity | Meaning in the model                                                                                                                                                                                       |
| ------------------- | --------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `kmsKey` → `KmsKey` | reference, [?]        | The `kmsKey` reference on `DynamoDbReplicaSpecification` selects the KMS key used for provider-side encryption. A `KmsKey` can remain independently owned and can participate in other parts of the model. |

## `DynamoDbStreamSpecification`

`DynamoDbStreamSpecification` is a DynamoDB deployment record in the AWS platform-specific model. It carries the storage setting for dynamo db stream specification. Its declaration gives the concept a precise home through its declared properties. Its references provide the wiring that lets generation assemble the corresponding AWS design.

Direct supertypes: `TraceableElement`. Inherited attributes and marker capabilities are documented in the [shared kernel](../shared-kernel.md); this section lists every attribute declared by this class.

### Declared attributes

| Attribute        | Type and multiplicity        | What it captures and why it exists                                                                                                                                                            | Accepted values and example                                                                        |
| ---------------- | ---------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | -------------------------------------------------------------------------------------------------- |
| `streamViewType` | `DynamoDbStreamViewType` [1] | The controlled value used for stream view type on this dynamo db stream specification. It keeps the distinction explicit in the abstract syntax so later rules can interpret it consistently. | Exactly one of: `KEYS_ONLY`, `NEW_IMAGE`, `OLD_IMAGE`, `NEW_AND_OLD_IMAGES`. Example: `KEYS_ONLY`. |

### Relationships

This class declares no direct relationships.

## `DynamoDbTimeToLiveSpecification`

`DynamoDbTimeToLiveSpecification` is a DynamoDB deployment record in the AWS platform-specific model. It carries the storage setting for dynamo db time to live specification. Its declaration gives the concept a precise home through its declared properties. Its references provide the wiring that lets generation assemble the corresponding AWS design.

Direct supertypes: `TraceableElement`. Inherited attributes and marker capabilities are documented in the [shared kernel](../shared-kernel.md); this section lists every attribute declared by this class.

### Declared attributes

| Attribute       | Type and multiplicity | What it captures and why it exists                                                                                                                                                                                                                                                                   | Accepted values and example                                                                                                                |
| --------------- | --------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------ |
| `attributeName` | `String` [1]          | The attribute name that identifies this dynamo db time to live specification in the model and its generated AWS configuration. Semantic validation: `EnabledTtlHasAttributeName` (enabled ttl has attribute name) in `mde/validation/psm/rules/storage.evl` the value must be present and non-blank. | A free-form `String`, subject to this class's semantic meaning and any EVL constraints. Example: `DynamoDbTimeToLiveSpecificationExample`. |
| `enabled`       | `Boolean` [1]         | Whether this capability or connection is active in the modeled scenario. Keeping the switch explicit distinguishes an intentional omission from a temporarily disabled design. Within `DynamoDbTimeToLiveSpecification`, it applies to this specific element and its role in the surrounding model.  | Either `true` or `false`. Example: `true`.                                                                                                 |

### Relationships

This class declares no direct relationships.

## `DynamoDbSseSpecification`

`DynamoDbSseSpecification` is a DynamoDB deployment record in the AWS platform-specific model. It carries the storage setting for dynamo db sse specification. Its declaration gives the concept a precise home through kms key. Its references provide the wiring that lets generation assemble the corresponding AWS design.

Direct supertypes: `TraceableElement`. Inherited attributes and marker capabilities are documented in the [shared kernel](../shared-kernel.md); this section lists every attribute declared by this class.

### Declared attributes

| Attribute | Type and multiplicity | What it captures and why it exists                                                                                                                                                                                                                                                           | Accepted values and example                                                                                  |
| --------- | --------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------ |
| `enabled` | `Boolean` [1]         | Whether this capability or connection is active in the modeled scenario. Keeping the switch explicit distinguishes an intentional omission from a temporarily disabled design. Within `DynamoDbSseSpecification`, it applies to this specific element and its role in the surrounding model. | Either `true` or `false`. Example: `true`.                                                                   |
| `sseType` | `String` [1]          | The server-side encryption mechanism requested for the DynamoDB table.                                                                                                                                                                                                                       | A free-form `String`, subject to this class's semantic meaning and any EVL constraints. Example: `BUSINESS`. |

### Relationships

| Relationship        | Kind and multiplicity | Meaning in the model                                                                                                                                                                                   |
| ------------------- | --------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| `kmsKey` → `KmsKey` | reference, [?]        | The `kmsKey` reference on `DynamoDbSseSpecification` selects the KMS key used for provider-side encryption. A `KmsKey` can remain independently owned and can participate in other parts of the model. |

## `DynamoDbBackupPolicy`

`DynamoDbBackupPolicy` is a DynamoDB deployment record in the AWS platform-specific model. It carries the storage setting for dynamo db backup policy. Its declaration gives the concept a precise home through its declared properties. Its references provide the wiring that lets generation assemble the corresponding AWS design.

Direct supertypes: `TraceableElement`. Inherited attributes and marker capabilities are documented in the [shared kernel](../shared-kernel.md); this section lists every attribute declared by this class.

### Declared attributes

| Attribute                     | Type and multiplicity | What it captures and why it exists                                                                                                                                                                                                                                                                                                     | Accepted values and example                                                                                                                                                                     |
| ----------------------------- | --------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `backupRationale`             | `String` [1]          | Records reasoning behind backup rationale for dynamo db backup policy. It keeps design review and decision traceability explicit during review and transformation, so later steps do not have to infer it. Generation role: The artifact generator references this feature in `mde/generation/awspsm-to-artifacts/lib/validation.eol`. | A free-form `String`, subject to this class's semantic meaning and any EVL constraints. Example: `The choice protects the business outcome while keeping the design independently deployable.`. |
| `recoveryPointRetentionDays`  | `Integer` [1]         | The numeric value used for recovery point retention days on this dynamo db backup policy. Keeping the quantity in the model lets validation and generation apply the same boundary consistently.                                                                                                                                       | A numeric `Integer` value; use the unit or boundary documented for this attribute. Example: `1`.                                                                                                |
| `pointInTimeRecoveryDecision` | `Decision` [1]        | The recorded engineering decision on whether continuous point-in-time recovery is required for this table. Generation role: The artifact generator references this feature in `mde/generation/awspsm-to-artifacts/lib/validation.eol`.                                                                                                 | Exactly one of: `UNDECIDED`, `REQUIRED`, `NOT_REQUIRED`, `ACCEPTED`, `NEEDS_REVIEW`, `GENERATOR_OWNED`. Example: `UNDECIDED`.                                                                   |

### Relationships

This class declares no direct relationships.

## `S3Bucket`

An AWS S3 bucket resource. It brings object storage intent into provider-specific form through versioning, access blocking, encryption, lifecycle, notification, replication, ownership, and bucket policy settings.

Direct supertypes: `AwsResource`. Inherited attributes and marker capabilities are documented in the [shared kernel](../shared-kernel.md); this section lists every attribute declared by this class.

### Declared attributes

| Attribute                        | Type and multiplicity         | What it captures and why it exists                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                     | Accepted values and example                                                                                                  |
| -------------------------------- | ----------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ---------------------------------------------------------------------------------------------------------------------------- |
| `bucketName`                     | `String` [1]                  | The bucket name that identifies this s3 bucket in the model and its generated AWS configuration.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                       | A free-form `String`, subject to this class's semantic meaning and any EVL constraints. Example: `orders-prod-123456789012`. |
| `objectLockEnabled`              | `Boolean` [1]                 | Whether object lock is enabled for this s3 bucket. The flag turns an operational choice into explicit model data.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                      | Either `true` or `false`. Example: `true`.                                                                                   |
| `transferAccelerationEnabled`    | `Boolean` [1]                 | Whether transfer acceleration is enabled for this s3 bucket. The flag turns an operational choice into explicit model data. Transformation role: ETL rule `ObjectStore2S3Bucket` in `mde/transformations/pim-to-awspsm/data-messaging-events.etl` assigns or materializes this feature while refining `S3Bucket`. Generation role: The artifact generator references this feature in `mde/generation/awspsm-to-artifacts/lib/cfn.eol`.                                                                                                                                                                                                                                                                                                                                 | Either `true` or `false`. Example: `true`.                                                                                   |
| `eventBridgeNotificationEnabled` | `Boolean` [1]                 | Whether event bridge notification is enabled for this s3 bucket. The flag turns an operational choice into explicit model data. Transformation role: ETL rule `ObjectStore2S3Bucket` in `mde/transformations/pim-to-awspsm/data-messaging-events.etl` assigns or materializes this feature while refining `S3Bucket`. Generation role: The artifact generator references this feature in `mde/generation/awspsm-to-artifacts/lib/cfn.eol`.                                                                                                                                                                                                                                                                                                                             | Either `true` or `false`. Example: `true`.                                                                                   |
| `bucketKeyEnabled`               | `Boolean` [1]                 | Whether bucket key is enabled for this s3 bucket. The flag turns an operational choice into explicit model data. Transformation role: ETL rule `ObjectStore2S3Bucket` in `mde/transformations/pim-to-awspsm/data-messaging-events.etl` assigns or materializes this feature while refining `S3Bucket`.                                                                                                                                                                                                                                                                                                                                                                                                                                                                 | Either `true` or `false`. Example: `true`.                                                                                   |
| `accessControl`                  | `String` [1]                  | The canned access-control setting requested for the bucket.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                            | A free-form `String`, subject to this class's semantic meaning and any EVL constraints. Example: `S3 Bucket Access Control`. |
| `websiteConfigurationJson`       | `String` [1]                  | Records serialized JSON representation of website configuration for s3 bucket. It keeps preserving structured provider or contract detail explicit during review and transformation, so later steps do not have to infer it. Semantic validation: `WebsiteBucketShouldNotBeProductionCritical` (website bucket should not be production critical) in `mde/validation/psm/rules/storage.evl` the value must be present and non-blank. Generation role: The artifact generator references this feature in `mde/generation/awspsm-to-artifacts/lib/cfn.eol`.                                                                                                                                                                                                              | A free-form `String`, subject to this class's semantic meaning and any EVL constraints. Example: `{"enabled":true}`.         |
| `loggingConfigurationJson`       | `String` [1]                  | Records serialized JSON representation of logging configuration for s3 bucket. It keeps preserving structured provider or contract detail explicit during review and transformation, so later steps do not have to infer it. Generation role: The artifact generator references this feature in `mde/generation/awspsm-to-artifacts/lib/cfn.eol`.                                                                                                                                                                                                                                                                                                                                                                                                                      | A free-form `String`, subject to this class's semantic meaning and any EVL constraints. Example: `{"enabled":true}`.         |
| `versioningStatus`               | `S3VersioningStatus` [1]      | The controlled value used for versioning status on this s3 bucket. It keeps the distinction explicit in the abstract syntax so later rules can interpret it consistently.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                              | Exactly one of: `ENABLED`, `SUSPENDED`. Example: `ENABLED`.                                                                  |
| `publicAccessMode`               | `S3BlockPublicAccessMode` [1] | The model's consolidated public-access posture for the bucket, used alongside the detailed public-access block configuration. Semantic validation: `ProductionBucketsBlockPublicAccess` (production buckets block public access) in `mde/validation/psm/rules/storage.evl` the rule's diagnostic or remediation guidance refers to this feature. Transformation role: ETL rule `ObjectStore2S3Bucket` in `mde/transformations/pim-to-awspsm/data-messaging-events.etl` assigns or materializes this feature while refining `S3Bucket`. Generation role: The artifact generator references this feature in `mde/generation/awspsm-to-artifacts/lib/cfn.eol`. The artifact generator references this feature in `mde/generation/awspsm-to-artifacts/lib/validation.eol`. | Exactly one of: `STRICT_BLOCK_ALL`, `CUSTOM`, `DISABLED_NOT_RECOMMENDED`. Example: `STRICT_BLOCK_ALL`.                       |

### Relationships

| Relationship                                                | Kind and multiplicity             | Meaning in the model                                                                                                                                                                                                                                                                           |
| ----------------------------------------------------------- | --------------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `ownershipControls` → `S3OwnershipControls`                 | containment, [?]                  | Rules deciding ownership and ACL treatment for objects written to the bucket.                                                                                                                                                                                                                  |
| `cors` → `CorsConfiguration`                                | containment, [?]                  | The owned cross-origin policy controlling browser access to this endpoint.                                                                                                                                                                                                                     |
| `encryption` → `S3BucketEncryption`                         | containment, [?]                  | The `encryption` containment on `S3Bucket` declares how the bucket's stored objects are encrypted. The `S3BucketEncryption` objects are owned by `S3Bucket` and remain part of its model subtree.                                                                                              |
| `lifecycle` → `S3LifecycleConfiguration`                    | containment, [?]                  | The `lifecycle` containment on `S3Bucket` keeps the rules that move or expire objects over time. The `S3LifecycleConfiguration` objects are owned by `S3Bucket` and remain part of its model subtree.                                                                                          |
| `publicAccessBlock` → `S3PublicAccessBlockConfiguration`    | containment, [?]                  | The detailed switches that prevent public ACLs and bucket policies from exposing this bucket.                                                                                                                                                                                                  |
| `notificationConfiguration` → `S3NotificationConfiguration` | containment, [?]                  | The `notificationConfiguration` containment on `S3Bucket` owns the event rules emitted by the bucket. The `S3NotificationConfiguration` objects are owned by `S3Bucket` and remain part of its model subtree.                                                                                  |
| `replicationConfiguration` → `S3ReplicationConfiguration`   | containment, [?]                  | The `replicationConfiguration` containment on `S3Bucket` keeps the cross-bucket replication policy. The `S3ReplicationConfiguration` objects are owned by `S3Bucket` and remain part of its model subtree.                                                                                     |
| `bucketPolicy` → `S3BucketPolicy`                           | reference, [?]; opposite `bucket` | The `bucketPolicy` reference on `S3Bucket` attaches the access or governance decision represented by bucket policy. A `S3BucketPolicy` can remain independently owned and can participate in other parts of the model. Its opposite `bucket` exposes the same connection from the target side. |

## `S3BucketEncryption`

`S3BucketEncryption` is an S3 deployment record in the AWS platform-specific model. It carries the object-storage setting for s3 bucket encryption. Its declaration gives the concept a precise home through kms key. Its references provide the wiring that lets generation assemble the corresponding AWS design.

Direct supertypes: `TraceableElement`. Inherited attributes and marker capabilities are documented in the [shared kernel](../shared-kernel.md); this section lists every attribute declared by this class.

### Declared attributes

| Attribute          | Type and multiplicity | What it captures and why it exists                                                                                                                                                                                                                                                                                                                                  | Accepted values and example                                                                                                            |
| ------------------ | --------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------- |
| `sseAlgorithm`     | `String` [1]          | The server-side encryption algorithm used for new objects in the bucket. Semantic validation: `KmsAlgorithmRequiresKmsKey` (kms algorithm requires kms key) in `mde/validation/psm/rules/storage.evl` the value must be present and non-blank. Generation role: The artifact generator references this feature in `mde/generation/awspsm-to-artifacts/lib/cfn.eol`. | A free-form `String`, subject to this class's semantic meaning and any EVL constraints. Example: `S3 Bucket Encryption Sse Algorithm`. |
| `bucketKeyEnabled` | `Boolean` [1]         | Whether bucket key is enabled for this s3 bucket encryption. The flag turns an operational choice into explicit model data.                                                                                                                                                                                                                                         | Either `true` or `false`. Example: `true`.                                                                                             |

### Relationships

| Relationship        | Kind and multiplicity | Meaning in the model                                                                                                                                                                             |
| ------------------- | --------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| `kmsKey` → `KmsKey` | reference, [?]        | The `kmsKey` reference on `S3BucketEncryption` selects the KMS key used for provider-side encryption. A `KmsKey` can remain independently owned and can participate in other parts of the model. |

## `S3OwnershipControls`

`S3OwnershipControls` is an S3 deployment record in the AWS platform-specific model. It carries the object-storage setting for s3 ownership controls. Its declaration gives the concept a precise home through rules. Its references provide the wiring that lets generation assemble the corresponding AWS design.

Direct supertypes: `TraceableElement`. Inherited attributes and marker capabilities are documented in the [shared kernel](../shared-kernel.md); this section lists every attribute declared by this class.

### Declared attributes

This class declares no attributes of its own. It inherits the attributes of its supertype, if any.

### Relationships

| Relationship                | Kind and multiplicity | Meaning in the model                                                                                                                                                                                               |
| --------------------------- | --------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| `rules` → `S3OwnershipRule` | containment, [+]      | The `rules` containment on `S3OwnershipControls` owns the ordered rule records that determine the behavior. The `S3OwnershipRule` objects are owned by `S3OwnershipControls` and remain part of its model subtree. |

## `S3OwnershipRule`

`S3OwnershipRule` is an S3 deployment record in the AWS platform-specific model. It carries the object-storage setting for s3 ownership rule. Its declaration gives the concept a precise home through its declared properties. Its references provide the wiring that lets generation assemble the corresponding AWS design.

Direct supertypes: `TraceableElement`. Inherited attributes and marker capabilities are documented in the [shared kernel](../shared-kernel.md); this section lists every attribute declared by this class.

### Declared attributes

| Attribute         | Type and multiplicity | What it captures and why it exists                                                | Accepted values and example                                                                                     |
| ----------------- | --------------------- | --------------------------------------------------------------------------------- | --------------------------------------------------------------------------------------------------------------- |
| `objectOwnership` | `String` [1]          | The S3 object-ownership mode governing ACL use and ownership of uploaded objects. | A free-form `String`, subject to this class's semantic meaning and any EVL constraints. Example: `orders-team`. |

### Relationships

This class declares no direct relationships.

## `S3LifecycleConfiguration`

`S3LifecycleConfiguration` is an S3 deployment record in the AWS platform-specific model. It carries the object-storage setting for s3 lifecycle configuration. Its declaration gives the concept a precise home through rules. Its references provide the wiring that lets generation assemble the corresponding AWS design.

Direct supertypes: `TraceableElement`. Inherited attributes and marker capabilities are documented in the [shared kernel](../shared-kernel.md); this section lists every attribute declared by this class.

### Declared attributes

This class declares no attributes of its own. It inherits the attributes of its supertype, if any.

### Relationships

| Relationship                | Kind and multiplicity | Meaning in the model                                                                                                                                                                                                         |
| --------------------------- | --------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `rules` → `S3LifecycleRule` | containment, [+]      | The `rules` containment on `S3LifecycleConfiguration` owns the ordered rule records that determine the behavior. The `S3LifecycleRule` objects are owned by `S3LifecycleConfiguration` and remain part of its model subtree. |

## `S3LifecycleRule`

`S3LifecycleRule` is an S3 deployment record in the AWS platform-specific model. It carries the object-storage setting for s3 lifecycle rule. Its declaration gives the concept a precise home through filter, transitions. Its references provide the wiring that lets generation assemble the corresponding AWS design.

Direct supertypes: `TraceableElement`. Inherited attributes and marker capabilities are documented in the [shared kernel](../shared-kernel.md); this section lists every attribute declared by this class.

### Declared attributes

| Attribute                           | Type and multiplicity | What it captures and why it exists                                                                                                                                                                                                                                                                                                                                           | Accepted values and example                                                                                                  |
| ----------------------------------- | --------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ---------------------------------------------------------------------------------------------------------------------------- |
| `ruleId`                            | `String` [1]          | The stable identifier of this lifecycle rule within the bucket configuration.                                                                                                                                                                                                                                                                                                | A free-form `String`, subject to this class's semantic meaning and any EVL constraints. Example: `order-123`.                |
| `status`                            | `String` [1]          | The lifecycle or activation state of the modeled object. It controls how a consumer should treat the object without changing its identity. Within `S3LifecycleRule`, it applies to this specific element and its role in the surrounding model.                                                                                                                              | A free-form `String`, subject to this class's semantic meaning and any EVL constraints. Example: `ACTIVE`.                   |
| `prefix`                            | `String` [1]          | The legacy object-key prefix used to scope this lifecycle rule.                                                                                                                                                                                                                                                                                                              | A free-form `String`, subject to this class's semantic meaning and any EVL constraints. Example: `S3 Lifecycle Rule Prefix`. |
| `tagFiltersJson`                    | `String` [1]          | The serialized representation of tag filters on this s3 lifecycle rule. It carries provider-specific structure that is intentionally retained as document content.                                                                                                                                                                                                           | A free-form `String`, subject to this class's semantic meaning and any EVL constraints. Example: `{"enabled":true}`.         |
| `expirationInDays`                  | `Integer` [1]         | Records expiration in days duration or limit, expressed in days for s3 lifecycle rule. It keeps an operational boundary that should not be left to provider defaults explicit during review and transformation, so later steps do not have to infer it. Generation role: The artifact generator references this feature in `mde/generation/awspsm-to-artifacts/lib/cfn.eol`. | A numeric `Integer` value; use the unit or boundary documented for this attribute. Example: `1`.                             |
| `noncurrentVersionExpirationInDays` | `Integer` [1]         | Records compatibility/version marker for noncurrent version expiration in days for s3 lifecycle rule. It keeps compatibility and contract evolution explicit during review and transformation, so later steps do not have to infer it. Generation role: The artifact generator references this feature in `mde/generation/awspsm-to-artifacts/lib/cfn.eol`.                  | A numeric `Integer` value; use the unit or boundary documented for this attribute. Example: `1`.                             |

### Relationships

| Relationship                   | Kind and multiplicity | Meaning in the model                                                                                                                                                                              |
| ------------------------------ | --------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `filter` → `S3LifecycleFilter` | containment, [?]      | The structured prefix, size, and tag predicate that limits the objects affected by this rule.                                                                                                     |
| `transitions` → `S3Transition` | containment, [*]      | The `transitions` containment on `S3LifecycleRule` owns the routing edges between workflow steps. The `S3Transition` objects are owned by `S3LifecycleRule` and remain part of its model subtree. |

## `S3LifecycleFilter`

`S3LifecycleFilter` is an S3 deployment record in the AWS platform-specific model. It carries the object-storage setting for s3 lifecycle filter. Its declaration gives the concept a precise home through tags. Its references provide the wiring that lets generation assemble the corresponding AWS design.

Direct supertypes: `TraceableElement`. Inherited attributes and marker capabilities are documented in the [shared kernel](../shared-kernel.md); this section lists every attribute declared by this class.

### Declared attributes

| Attribute               | Type and multiplicity | What it captures and why it exists                                                                                                                                                      | Accepted values and example                                                                                                    |
| ----------------------- | --------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------ |
| `prefix`                | `String` [1]          | The object-key prefix matched by the structured lifecycle filter.                                                                                                                       | A free-form `String`, subject to this class's semantic meaning and any EVL constraints. Example: `S3 Lifecycle Filter Prefix`. |
| `objectSizeGreaterThan` | `Integer` [1]         | The numeric value used for object size greater than on this s3 lifecycle filter. Keeping the quantity in the model lets validation and generation apply the same boundary consistently. | A numeric `Integer` value; use the unit or boundary documented for this attribute. Example: `1`.                               |
| `objectSizeLessThan`    | `Integer` [1]         | The numeric value used for object size less than on this s3 lifecycle filter. Keeping the quantity in the model lets validation and generation apply the same boundary consistently.    | A numeric `Integer` value; use the unit or boundary documented for this attribute. Example: `1`.                               |

### Relationships

| Relationship           | Kind and multiplicity | Meaning in the model                                                                                                                                                                            |
| ---------------------- | --------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `tags` → `S3TagFilter` | containment, [*]      | The `tags` containment on `S3LifecycleFilter` attaches the AWS tags emitted with the resource. The `S3TagFilter` objects are owned by `S3LifecycleFilter` and remain part of its model subtree. |

## `S3TagFilter`

`S3TagFilter` is an S3 deployment record in the AWS platform-specific model. It carries the object-storage setting for s3 tag filter. Its declaration gives the concept a precise home through its declared properties. Its references provide the wiring that lets generation assemble the corresponding AWS design.

Direct supertypes: `TraceableElement`. Inherited attributes and marker capabilities are documented in the [shared kernel](../shared-kernel.md); this section lists every attribute declared by this class.

### Declared attributes

| Attribute | Type and multiplicity | What it captures and why it exists                                                                                                                                                                                                                    | Accepted values and example                                                                                 |
| --------- | --------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ----------------------------------------------------------------------------------------------------------- |
| `key`     | `String` [1]          | The object-tag key tested by the lifecycle filter.                                                                                                                                                                                                    | A free-form `String`, subject to this class's semantic meaning and any EVL constraints. Example: `orderId`. |
| `value`   | `String` [1]          | The value carried by this parameter or expression. It is kept separate from the name so references can remain stable while deployment values change. Within `S3TagFilter`, it applies to this specific element and its role in the surrounding model. | A free-form `String`, subject to this class's semantic meaning and any EVL constraints. Example: `READY`.   |

### Relationships

This class declares no direct relationships.

## `S3Transition`

`S3Transition` is an S3 deployment record in the AWS platform-specific model. It carries the object-storage setting for s3 transition. Its declaration gives the concept a precise home through its declared properties. Its references provide the wiring that lets generation assemble the corresponding AWS design.

Direct supertypes: `TraceableElement`. Inherited attributes and marker capabilities are documented in the [shared kernel](../shared-kernel.md); this section lists every attribute declared by this class.

### Declared attributes

| Attribute          | Type and multiplicity | What it captures and why it exists                                                                                                                                          | Accepted values and example                                                                                  |
| ------------------ | --------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------ |
| `storageClass`     | `String` [1]          | The destination S3 storage class for objects affected by this transition.                                                                                                   | A free-form `String`, subject to this class's semantic meaning and any EVL constraints. Example: `STANDARD`. |
| `transitionInDays` | `Integer` [1]         | The numeric value used for transition in days on this s3 transition. Keeping the quantity in the model lets validation and generation apply the same boundary consistently. | A numeric `Integer` value; use the unit or boundary documented for this attribute. Example: `1`.             |

### Relationships

This class declares no direct relationships.

## `S3PublicAccessBlockConfiguration`

`S3PublicAccessBlockConfiguration` is an S3 deployment record in the AWS platform-specific model. It carries the object-storage setting for s3 public access block configuration. Its declaration gives the concept a precise home through its declared properties. Its references provide the wiring that lets generation assemble the corresponding AWS design.

Direct supertypes: `TraceableElement`. Inherited attributes and marker capabilities are documented in the [shared kernel](../shared-kernel.md); this section lists every attribute declared by this class.

### Declared attributes

| Attribute               | Type and multiplicity | What it captures and why it exists                                                                                                                                                                                                                                                              | Accepted values and example                |
| ----------------------- | --------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------ |
| `blockPublicAcls`       | `Boolean` [1]         | The boolean decision for block public acls on this s3 public access block configuration. It keeps an important design choice explicit for review and transformation.                                                                                                                            | Either `true` or `false`. Example: `true`. |
| `blockPublicPolicy`     | `Boolean` [1]         | The boolean decision for block public policy on this s3 public access block configuration. It keeps an important design choice explicit for review and transformation.                                                                                                                          | Either `true` or `false`. Example: `true`. |
| `ignorePublicAcls`      | `Boolean` [1]         | The boolean decision for ignore public acls on this s3 public access block configuration. It keeps an important design choice explicit for review and transformation.                                                                                                                           | Either `true` or `false`. Example: `true`. |
| `restrictPublicBuckets` | `Boolean` [1]         | The boolean decision for restrict public buckets on this s3 public access block configuration. It keeps an important design choice explicit for review and transformation. Generation role: The artifact generator references this feature in `mde/generation/awspsm-to-artifacts/lib/cfn.eol`. | Either `true` or `false`. Example: `true`. |

### Relationships

This class declares no direct relationships.

## `S3NotificationConfiguration`

`S3NotificationConfiguration` is an S3 deployment record in the AWS platform-specific model. It carries the object-storage setting for s3 notification configuration. Its declaration gives the concept a precise home through rules. Its references provide the wiring that lets generation assemble the corresponding AWS design.

Direct supertypes: `TraceableElement`. Inherited attributes and marker capabilities are documented in the [shared kernel](../shared-kernel.md); this section lists every attribute declared by this class.

### Declared attributes

| Attribute            | Type and multiplicity | What it captures and why it exists                                                                                                     | Accepted values and example                |
| -------------------- | --------------------- | -------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------ |
| `eventBridgeEnabled` | `Boolean` [1]         | Whether event bridge is enabled for this s3 notification configuration. The flag turns an operational choice into explicit model data. | Either `true` or `false`. Example: `true`. |

### Relationships

| Relationship                   | Kind and multiplicity                      | Meaning in the model                                                                                                                                                                                                                                                                                                 |
| ------------------------------ | ------------------------------------------ | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `rules` → `S3NotificationRule` | containment, [*]; opposite `configuration` | The `rules` containment on `S3NotificationConfiguration` owns the ordered rule records that determine the behavior. The `S3NotificationRule` objects are owned by `S3NotificationConfiguration` and remain part of its model subtree. Its opposite `configuration` exposes the same connection from the target side. |

## `S3NotificationRule`

`S3NotificationRule` is an S3 deployment record in the AWS platform-specific model. It carries the object-storage setting for s3 notification rule. Its declaration gives the concept a precise home through configuration, destination. Its references provide the wiring that lets generation assemble the corresponding AWS design.

Direct supertypes: `TraceableElement`. Inherited attributes and marker capabilities are documented in the [shared kernel](../shared-kernel.md); this section lists every attribute declared by this class.

### Declared attributes

| Attribute      | Type and multiplicity | What it captures and why it exists                                                                                                                                                                    | Accepted values and example                                                                                                            |
| -------------- | --------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------- |
| `eventTypes`   | `String` [*]          | The S3 event names that cause this notification rule to publish to its destination.                                                                                                                   | A collection of values. Example: [`BUSINESS`, `BUSINESS-2`].                                                                           |
| `filterPrefix` | `String` [1]          | An object-key prefix that limits which S3 events satisfy this notification rule. Generation role: The artifact generator references this feature in `mde/generation/awspsm-to-artifacts/lib/cfn.eol`. | A free-form `String`, subject to this class's semantic meaning and any EVL constraints. Example: `S3 Notification Rule Filter Prefix`. |
| `filterSuffix` | `String` [1]          | An object-key suffix that narrows which matching S3 events are delivered.                                                                                                                             | A free-form `String`, subject to this class's semantic meaning and any EVL constraints. Example: `S3 Notification Rule Filter Suffix`. |

### Relationships

| Relationship                                    | Kind and multiplicity                       | Meaning in the model                                                                                                                                                                                                                                                                                          |
| ----------------------------------------------- | ------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `configuration` → `S3NotificationConfiguration` | reference; read-only, [1]; opposite `rules` | The `configuration` reference on `S3NotificationRule` attaches the configuration record that controls this connection. A `S3NotificationConfiguration` can remain independently owned and can participate in other parts of the model. Its opposite `rules` exposes the same connection from the target side. |
| `destination` → `S3NotificationDestination`     | reference, [?]                              | The resource or structured destination that receives the resulting delivery.                                                                                                                                                                                                                                  |

## `S3ReplicationConfiguration`

`S3ReplicationConfiguration` is an S3 deployment record in the AWS platform-specific model. It carries the object-storage setting for s3 replication configuration. Its declaration gives the concept a precise home through rules, role. Its references provide the wiring that lets generation assemble the corresponding AWS design.

Direct supertypes: `TraceableElement`. Inherited attributes and marker capabilities are documented in the [shared kernel](../shared-kernel.md); this section lists every attribute declared by this class.

### Declared attributes

| Attribute   | Type and multiplicity | What it captures and why it exists                                                                                                                                                                                                                                                                                                                                                                                                                                                                                         | Accepted values and example                                                                                          |
| ----------- | --------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | -------------------------------------------------------------------------------------------------------------------- |
| `rulesJson` | `String` [1]          | Records serialized JSON representation of rules for s3 replication configuration. It keeps preserving structured provider or contract detail explicit during review and transformation, so later steps do not have to infer it. Semantic validation: `ReplicationHasRoleAndRules` (replication has role and rules) in `mde/validation/psm/rules/storage.evl` the value must be present and non-blank. Generation role: The artifact generator references this feature in `mde/generation/awspsm-to-artifacts/lib/cfn.eol`. | A free-form `String`, subject to this class's semantic meaning and any EVL constraints. Example: `{"enabled":true}`. |

### Relationships

| Relationship                  | Kind and multiplicity | Meaning in the model                                                                                                                                                                                                               |
| ----------------------------- | --------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `rules` → `S3ReplicationRule` | containment, [*]      | The `rules` containment on `S3ReplicationConfiguration` owns the ordered rule records that determine the behavior. The `S3ReplicationRule` objects are owned by `S3ReplicationConfiguration` and remain part of its model subtree. |
| `role` → `IamRole`            | reference, [1]        | The `role` reference on `S3ReplicationConfiguration` attaches the execution role that grants the resource its AWS permissions. An `IamRole` can remain independently owned and can participate in other parts of the model.        |

## `S3ReplicationRule`

`S3ReplicationRule` is an S3 deployment record in the AWS platform-specific model. It carries the object-storage setting for s3 replication rule. Its declaration gives the concept a precise home through filter, destination. Its references provide the wiring that lets generation assemble the corresponding AWS design.

Direct supertypes: `TraceableElement`. Inherited attributes and marker capabilities are documented in the [shared kernel](../shared-kernel.md); this section lists every attribute declared by this class.

### Declared attributes

| Attribute              | Type and multiplicity | What it captures and why it exists                                                                                                                                                                                                                | Accepted values and example                                                                                                                               |
| ---------------------- | --------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `ruleId`               | `String` [1]          | The identifier used to distinguish this replication rule within the bucket.                                                                                                                                                                       | A free-form `String`, subject to this class's semantic meaning and any EVL constraints. Example: `order-123`.                                             |
| `status`               | `String` [1]          | The lifecycle or activation state of the modeled object. It controls how a consumer should treat the object without changing its identity. Within `S3ReplicationRule`, it applies to this specific element and its role in the surrounding model. | A free-form `String`, subject to this class's semantic meaning and any EVL constraints. Example: `ACTIVE`.                                                |
| `destinationBucketArn` | `String` [1]          | The destination bucket ARN in the compact replication form.                                                                                                                                                                                       | A free-form `String`, subject to this class's semantic meaning and any EVL constraints. Example: `arn:aws:lambda:us-east-1:123456789012:function:orders`. |
| `prefix`               | `String` [1]          | The legacy object-key prefix limiting the objects covered by replication.                                                                                                                                                                         | A free-form `String`, subject to this class's semantic meaning and any EVL constraints. Example: `S3 Replication Rule Prefix`.                            |
| `filterJson`           | `String` [1]          | The serialized representation of filter on this s3 replication rule. It carries provider-specific structure that is intentionally retained as document content.                                                                                   | A free-form `String`, subject to this class's semantic meaning and any EVL constraints. Example: `{"enabled":true}`.                                      |

### Relationships

| Relationship                               | Kind and multiplicity | Meaning in the model                                                                          |
| ------------------------------------------ | --------------------- | --------------------------------------------------------------------------------------------- |
| `filter` → `S3LifecycleFilter`             | containment, [?]      | The structured prefix, size, and tag predicate that limits the objects affected by this rule. |
| `destination` → `S3ReplicationDestination` | containment, [?]      | The resource or structured destination that receives the resulting delivery.                  |

## `S3ReplicationDestination`

`S3ReplicationDestination` is an S3 deployment record in the AWS platform-specific model. It carries the object-storage setting for s3 replication destination. Its declaration gives the concept a precise home through replica kms key. Its references provide the wiring that lets generation assemble the corresponding AWS design.

Direct supertypes: `TraceableElement`. Inherited attributes and marker capabilities are documented in the [shared kernel](../shared-kernel.md); this section lists every attribute declared by this class.

### Declared attributes

| Attribute      | Type and multiplicity | What it captures and why it exists                            | Accepted values and example                                                                                                                               |
| -------------- | --------------------- | ------------------------------------------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `bucketArn`    | `String` [1]          | The ARN of the bucket that receives replicated objects.       | A free-form `String`, subject to this class's semantic meaning and any EVL constraints. Example: `arn:aws:lambda:us-east-1:123456789012:function:orders`. |
| `accountId`    | `String` [1]          | The AWS account that owns the replication destination bucket. | A free-form `String`, subject to this class's semantic meaning and any EVL constraints. Example: `order-123`.                                             |
| `storageClass` | `String` [1]          | The storage class assigned to replicated object copies.       | A free-form `String`, subject to this class's semantic meaning and any EVL constraints. Example: `STANDARD`.                                              |

### Relationships

| Relationship               | Kind and multiplicity | Meaning in the model                                               |
| -------------------------- | --------------------- | ------------------------------------------------------------------ |
| `replicaKmsKey` → `KmsKey` | reference, [?]        | The KMS key used to encrypt replicated objects at the destination. |

## `S3BucketPolicy`

`S3BucketPolicy` is an S3 deployment record in the AWS platform-specific model. It carries the object-storage setting for s3 bucket policy. Its declaration gives the concept a precise home through policy document, bucket. Its references provide the wiring that lets generation assemble the corresponding AWS design.

Direct supertypes: `AwsResource`. Inherited attributes and marker capabilities are documented in the [shared kernel](../shared-kernel.md); this section lists every attribute declared by this class.

### Declared attributes

| Attribute            | Type and multiplicity | What it captures and why it exists                                                                                                                                                                                                                            | Accepted values and example                                                                                          |
| -------------------- | --------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | -------------------------------------------------------------------------------------------------------------------- |
| `policyDocumentJson` | `String` [1]          | A serialized policy document retained for provider features that are still represented as JSON. It preserves the exact policy payload used by generation. Within `S3BucketPolicy`, it applies to this specific element and its role in the surrounding model. | A free-form `String`, subject to this class's semantic meaning and any EVL constraints. Example: `{"enabled":true}`. |

### Relationships

| Relationship                           | Kind and multiplicity                   | Meaning in the model                                                                                                                                                                                                   |
| -------------------------------------- | --------------------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `policyDocument` → `IamPolicyDocument` | containment, [?]                        | The `policyDocument` containment on `S3BucketPolicy` owns the structured IAM policy document used by the resource. The `IamPolicyDocument` objects are owned by `S3BucketPolicy` and remain part of its model subtree. |
| `bucket` → `S3Bucket`                  | reference, [1]; opposite `bucketPolicy` | The S3 bucket that owns the policy or originates the notification.                                                                                                                                                     |
