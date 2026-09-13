# Amazon EventBridge resources

EventBridge classes cover buses, rules, targets, schedules, pipes, API destinations, connections, input mapping, and retry policy.

Source: `mde/metamodels/psm/awspsm-events.emf`.

## `EventBridgeBus`

Represents event bridge bus in the PSM vocabulary. It specializes `AwsResource` with the details needed for this modeling concern.

Direct supertypes: `AwsResource`. Inherited attributes and marker capabilities are documented in the [shared kernel](../shared-kernel.md); this section lists every attribute declared by this class.

### Declared attributes

| Attribute         | Type and multiplicity | What it captures and why it exists                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                              | Accepted values and example                                                                                               |
| ----------------- | --------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------- |
| `busName`         | `String` [1]          | For a event bridge bus, the model records the stable name/key/code used for bus name. Keeping the fact with its owning element lets validation and refinement inspect it before artifact generation. Transformation role: ETL rule `EventBus2EventBridgeBus` in `mde/transformations/pim-to-awspsm/data-messaging-events.etl` assigns or materializes this feature while refining `EventBridgeBus`. Generation role: The artifact generator references this feature in `mde/generation/awspsm-to-artifacts/lib/cfn.eol`. The artifact generator references this feature in `mde/generation/awspsm-to-artifacts/lib/values.eol`. | A free-form `String`, subject to this class's semantic meaning and any EVL constraints. Example: `orders-domain`.         |
| `eventSourceName` | `String` [1]          | Records origin/source selected for event source name for event bridge bus. It keeps this decision explicit during review and transformation, so later steps do not have to infer it. Transformation role: ETL rule `EventBus2EventBridgeBus` in `mde/transformations/pim-to-awspsm/data-messaging-events.etl` assigns or materializes this feature while refining `EventBridgeBus`. Generation role: The artifact generator references this feature in `mde/generation/awspsm-to-artifacts/lib/cfn.eol`.                                                                                                                        | A free-form `String`, subject to this class's semantic meaning and any EVL constraints. Example: `EventBridgeBusExample`. |

### Relationships

| Relationship                        | Kind and multiplicity                     | Meaning in the model                                                                                                                          |
| ----------------------------------- | ----------------------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------- |
| `rules` → `EventBridgeRule`         | reference, [*]; opposite `bus`            | References the event bridge rule element(s) used as rules by this event bridge bus; the target may be shared elsewhere in the model.          |
| `archives` → `EventBridgeArchive`   | reference, [*]; opposite `eventSourceBus` | References the event bridge archive element(s) used as archives by this event bridge bus; the target may be shared elsewhere in the model.    |
| `policies` → `EventBridgeBusPolicy` | reference, [*]; opposite `bus`            | References the event bridge bus policy element(s) used as policies by this event bridge bus; the target may be shared elsewhere in the model. |

## `EventBridgeBusPolicy`

Represents event bridge bus policy in the PSM vocabulary. It specializes `AwsResource` with the details needed for this modeling concern.

Direct supertypes: `AwsResource`. Inherited attributes and marker capabilities are documented in the [shared kernel](../shared-kernel.md); this section lists every attribute declared by this class.

### Declared attributes

| Attribute     | Type and multiplicity | What it captures and why it exists                                                                                                                                                                                                                                                                             | Accepted values and example                                                                                          |
| ------------- | --------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | -------------------------------------------------------------------------------------------------------------------- |
| `statementId` | `String` [1]          | Records the stable name/key/code used for statement id for the event bridge bus policy. This keeps the decision explicit even when the element's class or relationships remain unchanged. Generation role: The artifact generator references this feature in `mde/generation/awspsm-to-artifacts/lib/cfn.eol`. | A free-form `String`, subject to this class's semantic meaning and any EVL constraints. Example: `order-123`.        |
| `policyJson`  | `String` [1]          | Records the serialized JSON representation of policy for the event bridge bus policy. This keeps the decision explicit even when the element's class or relationships remain unchanged. Generation role: The artifact generator references this feature in `mde/generation/awspsm-to-artifacts/lib/cfn.eol`.   | A free-form `String`, subject to this class's semantic meaning and any EVL constraints. Example: `{"enabled":true}`. |

### Relationships

| Relationship                           | Kind and multiplicity               | Meaning in the model                                                                                                                       |
| -------------------------------------- | ----------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------ |
| `policyDocument` → `IamPolicyDocument` | containment, [?]                    | Contains the iam policy document element(s) that make up this event bridge bus policy; the contained objects belong to this model element. |
| `bus` → `EventBridgeBus`               | reference, [1]; opposite `policies` | References the event bridge bus element(s) used as bus by this event bridge bus policy; the target may be shared elsewhere in the model.   |

## `EventBridgeRule`

Represents event bridge rule in the PSM vocabulary. It specializes `AwsResource` with the details needed for this modeling concern.

Direct supertypes: `AwsResource`. Inherited attributes and marker capabilities are documented in the [shared kernel](../shared-kernel.md); this section lists every attribute declared by this class.

### Declared attributes

| Attribute            | Type and multiplicity | What it captures and why it exists                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                      | Accepted values and example                                                                                             |
| -------------------- | --------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ----------------------------------------------------------------------------------------------------------------------- |
| `ruleName`           | `String` [1]          | For a event bridge rule, the model records the stable name/key/code used for rule name. Keeping the fact with its owning element lets validation and refinement inspect it before artifact generation. Transformation role: ETL rule `EventRoutingRule2EventBridgeRule` in `mde/transformations/pim-to-awspsm/data-messaging-events.etl` assigns or materializes this feature while refining `EventBridgeRule`. Generation role: The artifact generator references this feature in `mde/generation/awspsm-to-artifacts/lib/cfn.eol`. The artifact generator references this feature in `mde/generation/awspsm-to-artifacts/lib/naming.eol`.                                                                                                                                                                                                                                                                                             | A free-form `String`, subject to this class's semantic meaning and any EVL constraints. Example: `OrderConfirmedRule`.  |
| `descriptionText`    | `String` [1]          | For a event bridge rule, the model records the human explanation of description text. Keeping the fact with its owning element lets validation and refinement inspect it before artifact generation. Transformation role: ETL rule `EventRoutingRule2EventBridgeRule` in `mde/transformations/pim-to-awspsm/data-messaging-events.etl` assigns or materializes this feature while refining `EventBridgeRule`.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                           | A free-form `String`, subject to this class's semantic meaning and any EVL constraints. Example: `Orders processing`.   |
| `eventPatternJson`   | `String` [1]          | Records validation pattern for event pattern json for event bridge rule. It keeps input/schema validation explicit during review and transformation, so later steps do not have to infer it. Semantic validation: `RuleHasPatternOrSchedule` (rule has pattern or schedule) in `mde/validation/psm/rules/events.evl` the value must be present and non-blank. `RuleDoesNotMixPatternAndSchedule` (rule does not mix pattern and schedule) in `mde/validation/psm/rules/events.evl` the value must be present and non-blank. Transformation role: ETL rule `EventRoutingRule2EventBridgeRule` in `mde/transformations/pim-to-awspsm/data-messaging-events.etl` assigns or materializes this feature while refining `EventBridgeRule`. ETL rule `EventRoutingRule2EventBridgeRule` in `mde/transformations/pim-to-awspsm/data-messaging-events.etl` reads or derives this feature while refining `EventBridgeRule`.                       | A free-form `String`, subject to this class's semantic meaning and any EVL constraints. Example: `{"enabled":true}`.    |
| `scheduleExpression` | `String` [1]          | For a event bridge rule, the model records the expression or rule that governs schedule expression. Keeping the fact with its owning element lets validation and refinement inspect it before artifact generation. Semantic validation: `RuleHasPatternOrSchedule` (rule has pattern or schedule) in `mde/validation/psm/rules/events.evl` the value must be present and non-blank. `RuleDoesNotMixPatternAndSchedule` (rule does not mix pattern and schedule) in `mde/validation/psm/rules/events.evl` the value must be present and non-blank. Transformation role: ETL rule `EventRoutingRule2EventBridgeRule` in `mde/transformations/pim-to-awspsm/data-messaging-events.etl` assigns or materializes this feature while refining `EventBridgeRule`. ETL rule `EventRoutingRule2EventBridgeRule` in `mde/transformations/pim-to-awspsm/data-messaging-events.etl` reads or derives this feature while refining `EventBridgeRule`. | A free-form `String`, subject to this class's semantic meaning and any EVL constraints. Example: `$.status == 'READY'`. |
| `state`              | `String` [1]          | For a event bridge rule, the model records the state value. Keeping the fact with its owning element lets validation and refinement inspect it before artifact generation. Transformation role: ETL rule `EventRoutingRule2EventBridgeRule` in `mde/transformations/pim-to-awspsm/data-messaging-events.etl` assigns or materializes this feature while refining `EventBridgeRule`.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                     | A free-form `String`, subject to this class's semantic meaning and any EVL constraints. Example: `ACTIVE`.              |

### Relationships

| Relationship                    | Kind and multiplicity            | Meaning in the model                                                                                                                 |
| ------------------------------- | -------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------ |
| `eventPattern` → `EventPattern` | containment, [?]                 | Contains the event pattern element(s) that make up this event bridge rule; the contained objects belong to this model element.       |
| `targets` → `EventBridgeTarget` | containment, [+]                 | Contains the event bridge target element(s) that make up this event bridge rule; the contained objects belong to this model element. |
| `bus` → `EventBridgeBus`        | reference, [?]; opposite `rules` | References the event bridge bus element(s) used as bus by this event bridge rule; the target may be shared elsewhere in the model.   |

## `EventPattern`

Represents event pattern in the PSM vocabulary. It specializes `TraceableElement` with the details needed for this modeling concern.

Direct supertypes: `TraceableElement`. Inherited attributes and marker capabilities are documented in the [shared kernel](../shared-kernel.md); this section lists every attribute declared by this class.

### Declared attributes

| Attribute     | Type and multiplicity | What it captures and why it exists                                                                                                                                                                                                                                                                                                                                                                                                            | Accepted values and example                                                                                               |
| ------------- | --------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------- |
| `sources`     | `String` [*]          | For a event pattern, the model records the origin/source selected for sources. Keeping the fact with its owning element lets validation and refinement inspect it before artifact generation. Generation role: The artifact generator references this feature in `mde/generation/awspsm-to-artifacts/lib/cfn.eol`. The artifact generator references this feature in `mde/generation/awspsm-to-artifacts/templates/docs/security-review.egl`. | A collection of values. Example: [`Event Pattern Sources`, `Event Pattern Sources-2`].                                    |
| `detailTypes` | `String` [*]          | For a event pattern, the model records the detail types value. Keeping the fact with its owning element lets validation and refinement inspect it before artifact generation. Generation role: The artifact generator references this feature in `mde/generation/awspsm-to-artifacts/lib/cfn.eol`.                                                                                                                                            | A collection of values. Example: [`BUSINESS`, `BUSINESS-2`].                                                              |
| `account`     | `String` [1]          | Records the account value for event pattern. It keeps this decision explicit during review and transformation, so later steps do not have to infer it. Generation role: The artifact generator references this feature in `mde/generation/awspsm-to-artifacts/awspsm2artifacts.egx`. The artifact generator references this feature in `mde/generation/awspsm-to-artifacts/lib/cfn.eol`.                                                      | A free-form `String`, subject to this class's semantic meaning and any EVL constraints. Example: `Event Pattern Account`. |
| `region`      | `String` [1]          | For a event pattern, the model records the region value. Keeping the fact with its owning element lets validation and refinement inspect it before artifact generation. The repository contains no direct EVL rule, ETL assignment, or artifact-generator read for this declared field. For now, it remains a model-level fact for review and future refinement.                                                                              | A free-form `String`, subject to this class's semantic meaning and any EVL constraints. Example: `us-east-1`.             |
| `detailJson`  | `String` [1]          | Records serialized JSON representation of detail for event pattern. It keeps preserving structured provider or contract detail explicit during review and transformation, so later steps do not have to infer it. Generation role: The artifact generator references this feature in `mde/generation/awspsm-to-artifacts/lib/cfn.eol`.                                                                                                        | A free-form `String`, subject to this class's semantic meaning and any EVL constraints. Example: `{"enabled":true}`.      |

### Relationships

This class declares no direct relationships.

## `EventBridgeTarget`

Represents event bridge target in the PSM vocabulary. It specializes `TraceableElement` with the details needed for this modeling concern.

Direct supertypes: `TraceableElement`. Inherited attributes and marker capabilities are documented in the [shared kernel](../shared-kernel.md); this section lists every attribute declared by this class.

### Declared attributes

| Attribute       | Type and multiplicity       | What it captures and why it exists                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                 | Accepted values and example                                                                                                                               |
| --------------- | --------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `targetId`      | `String` [1]                | Stores the destination selected for target id on the event bridge target. The field keeps the model explicit and reviewable during review and transformation instead of leaving the decision to an inferred default. Semantic validation: `TargetHasResourceOrArn` (target has resource or arn) in `mde/validation/psm/rules/events.evl` the rule's diagnostic or remediation guidance refers to this feature. `CriticalEventTargetsHaveRetryOrDlq` (critical event targets have retry or dlq) in `mde/validation/psm/rules/events.evl` the rule's diagnostic or remediation guidance refers to this feature. Generation role: The artifact generator references this feature in `mde/generation/awspsm-to-artifacts/lib/cfn.eol`. | A free-form `String`, subject to this class's semantic meaning and any EVL constraints. Example: `order-123`.                                             |
| `arnExpression` | `String` [1]                | Records the expression or rule that governs arn expression for the event bridge target. This keeps the decision explicit even when the element's class or relationships remain unchanged. Semantic validation: `TargetHasResourceOrArn` (target has resource or arn) in `mde/validation/psm/rules/events.evl` the value must be present and non-blank. Generation role: The artifact generator references this feature in `mde/generation/awspsm-to-artifacts/lib/cfn.eol`.                                                                                                                                                                                                                                                        | A free-form `String`, subject to this class's semantic meaning and any EVL constraints. Example: `arn:aws:lambda:us-east-1:123456789012:function:orders`. |
| `inputPath`     | `String` [1]                | Records the data location or endpoint represented by input path for the event bridge target. This keeps the decision explicit even when the element's class or relationships remain unchanged. The repository contains no direct EVL rule, ETL assignment, or artifact-generator read for this declared field. For now, it remains a model-level fact for review and future refinement.                                                                                                                                                                                                                                                                                                                                            | A free-form `String`, subject to this class's semantic meaning and any EVL constraints. Example: `/orders/{orderId}`.                                     |
| `roleArn`       | `String` [1]                | Records AWS ARN used for role for event bridge target. It keeps this decision explicit during review and transformation, so later steps do not have to infer it. Semantic validation: `NonLambdaTargetHasInvokeRole` (non lambda target has invoke role) in `mde/validation/psm/rules/events.evl` the value must be present and non-blank.                                                                                                                                                                                                                                                                                                                                                                                         | A free-form `String`, subject to this class's semantic meaning and any EVL constraints. Example: `arn:aws:lambda:us-east-1:123456789012:function:orders`. |
| `targetKind`    | `EventBridgeTargetKind` [1] | Records the destination selected for target kind for the event bridge target. This keeps the decision explicit even when the element's class or relationships remain unchanged. Semantic validation: `NonLambdaTargetHasInvokeRole` (non lambda target has invoke role) in `mde/validation/psm/rules/events.evl` the value must satisfy a numeric or ordering boundary.                                                                                                                                                                                                                                                                                                                                                            | Exactly one of: `LAMBDA`, `SQS`, `SNS`, `STEP_FUNCTIONS`, `API_DESTINATION`, `EVENT_BUS`, `LOG_GROUP`, `OTHER_AWS_RESOURCE`. Example: `LAMBDA`.           |

### Relationships

| Relationship                                       | Kind and multiplicity | Meaning in the model                                                                                                                              |
| -------------------------------------------------- | --------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------- |
| `input` → `ValueExpression`                        | containment, [?]      | Contains the value expression element(s) that make up this event bridge target; the contained objects belong to this model element.               |
| `parameters` → `EventBridgeTargetParameters`       | containment, [?]      | Contains the event bridge target parameters element(s) that make up this event bridge target; the contained objects belong to this model element. |
| `retryPolicy` → `AwsRetryPolicy`                   | containment, [?]      | Contains the aws retry policy element(s) that make up this event bridge target; the contained objects belong to this model element.               |
| `inputTransformer` → `EventBridgeInputTransformer` | containment, [?]      | Contains the event bridge input transformer element(s) that make up this event bridge target; the contained objects belong to this model element. |
| `targetResource` → `AwsResource`                   | reference, [?]        | References the aws resource element(s) used as target resource by this event bridge target; the target may be shared elsewhere in the model.      |
| `role` → `IamRole`                                 | reference, [?]        | References the iam role element(s) used as role by this event bridge target; the target may be shared elsewhere in the model.                     |
| `deadLetterQueue` → `SqsQueue`                     | reference, [?]        | References the sqs queue element(s) used as dead letter queue by this event bridge target; the target may be shared elsewhere in the model.       |

## `EventBridgeTargetParameters`

An abstract event bridge target parameters concept. Use one of its concrete subtypes when creating a model instance; the shared attributes and relationships defined here still apply.

Direct supertypes: `TraceableElement`. Inherited attributes and marker capabilities are documented in the [shared kernel](../shared-kernel.md); this section lists every attribute declared by this class.

### Declared attributes

This class declares no attributes of its own. It inherits the attributes of its supertype, if any.

### Relationships

This class declares no direct relationships.

## `EventBridgeSqsTargetParameters`

Represents event bridge sqs target parameters in the PSM vocabulary. It specializes `EventBridgeTargetParameters` with the details needed for this modeling concern.

Direct supertypes: `EventBridgeTargetParameters`. Inherited attributes and marker capabilities are documented in the [shared kernel](../shared-kernel.md); this section lists every attribute declared by this class.

### Declared attributes

| Attribute        | Type and multiplicity | What it captures and why it exists                                                                                                                                                                                                                                                                                                         | Accepted values and example                                                                                   |
| ---------------- | --------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ | ------------------------------------------------------------------------------------------------------------- |
| `messageGroupId` | `String` [1]          | For a event bridge sqs target parameters, the model records the human explanation of message group id. Keeping the fact with its owning element lets validation and refinement inspect it before artifact generation. Generation role: The artifact generator references this feature in `mde/generation/awspsm-to-artifacts/lib/cfn.eol`. | A free-form `String`, subject to this class's semantic meaning and any EVL constraints. Example: `order-123`. |

### Relationships

This class declares no direct relationships.

## `EventBridgeHttpTargetParameters`

Represents event bridge http target parameters in the PSM vocabulary. It specializes `EventBridgeTargetParameters` with the details needed for this modeling concern.

Direct supertypes: `EventBridgeTargetParameters`. Inherited attributes and marker capabilities are documented in the [shared kernel](../shared-kernel.md); this section lists every attribute declared by this class.

### Declared attributes

This class declares no attributes of its own. It inherits the attributes of its supertype, if any.

### Relationships

| Relationship                              | Kind and multiplicity | Meaning in the model                                                                                                                              |
| ----------------------------------------- | --------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------- |
| `headerParameters` → `HttpParameter`      | containment, [*]      | Contains the http parameter element(s) that make up this event bridge http target parameters; the contained objects belong to this model element. |
| `queryStringParameters` → `HttpParameter` | containment, [*]      | Contains the http parameter element(s) that make up this event bridge http target parameters; the contained objects belong to this model element. |
| `pathParameterValues` → `HttpParameter`   | containment, [*]      | Contains the http parameter element(s) that make up this event bridge http target parameters; the contained objects belong to this model element. |

## `HttpParameter`

Represents http parameter in the PSM vocabulary. It specializes `TraceableElement` with the details needed for this modeling concern.

Direct supertypes: `TraceableElement`. Inherited attributes and marker capabilities are documented in the [shared kernel](../shared-kernel.md); this section lists every attribute declared by this class.

### Declared attributes

| Attribute | Type and multiplicity | What it captures and why it exists                                                                                                                                                                                                                                                                                                                                                                      | Accepted values and example                                                                                 |
| --------- | --------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ----------------------------------------------------------------------------------------------------------- |
| `key`     | `String` [1]          | Stores the stable name/key/code used for key on the http parameter. The field keeps the model explicit and reviewable during review and transformation instead of leaving the decision to an inferred default. The repository contains no direct EVL rule, ETL assignment, or artifact-generator read for this declared field. For now, it remains a model-level fact for review and future refinement. | A free-form `String`, subject to this class's semantic meaning and any EVL constraints. Example: `orderId`. |
| `value`   | `String` [1]          | Stores the value value on the http parameter. The field keeps the model explicit and reviewable during review and transformation instead of leaving the decision to an inferred default. The repository contains no direct EVL rule, ETL assignment, or artifact-generator read for this declared field. For now, it remains a model-level fact for review and future refinement.                       | A free-form `String`, subject to this class's semantic meaning and any EVL constraints. Example: `READY`.   |

### Relationships

This class declares no direct relationships.

## `EventBridgeBatchTargetParameters`

Represents event bridge batch target parameters in the PSM vocabulary. It specializes `EventBridgeTargetParameters` with the details needed for this modeling concern.

Direct supertypes: `EventBridgeTargetParameters`. Inherited attributes and marker capabilities are documented in the [shared kernel](../shared-kernel.md); this section lists every attribute declared by this class.

### Declared attributes

| Attribute       | Type and multiplicity | What it captures and why it exists                                                                                                                                                                                                                                                                                                                                                                                                 | Accepted values and example                                                                                                                                            |
| --------------- | --------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `jobDefinition` | `String` [1]          | For a event bridge batch target parameters, the model records the human explanation of job definition. Keeping the fact with its owning element lets validation and refinement inspect it before artifact generation. The repository contains no direct EVL rule, ETL assignment, or artifact-generator read for this declared field. For now, it remains a model-level fact for review and future refinement.                     | A free-form `String`, subject to this class's semantic meaning and any EVL constraints. Example: `A confirmed order is one accepted for fulfillment by the business.`. |
| `jobName`       | `String` [1]          | For a event bridge batch target parameters, the model records the stable name/key/code used for job name. Keeping the fact with its owning element lets validation and refinement inspect it before artifact generation. The repository contains no direct EVL rule, ETL assignment, or artifact-generator read for this declared field. For now, it remains a model-level fact for review and future refinement.                  | A free-form `String`, subject to this class's semantic meaning and any EVL constraints. Example: `EventBridgeBatchTargetParametersExample`.                            |
| `arraySize`     | `Integer` [1]         | Stores the array size value on the event bridge batch target parameters. The field records an operational boundary explicitly instead of leaving it to provider defaults. The repository contains no direct EVL rule, ETL assignment, or artifact-generator read for this declared field. For now, it remains a model-level fact for review and future refinement.                                                                 | A numeric `Integer` value; use the unit or boundary documented for this attribute. Example: `1`.                                                                       |
| `jobAttempts`   | `Integer` [1]         | Records the job attempts value for event bridge batch target parameters. It keeps an operational boundary that should not be left to provider defaults explicit during review and transformation, so later steps do not have to infer it. The repository contains no direct EVL rule, ETL assignment, or artifact-generator read for this declared field. For now, it remains a model-level fact for review and future refinement. | A numeric `Integer` value; use the unit or boundary documented for this attribute. Example: `1`.                                                                       |

### Relationships

This class declares no direct relationships.

## `EventBridgeInputTransformer`

Represents event bridge input transformer in the PSM vocabulary. It specializes `TraceableElement` with the details needed for this modeling concern.

Direct supertypes: `TraceableElement`. Inherited attributes and marker capabilities are documented in the [shared kernel](../shared-kernel.md); this section lists every attribute declared by this class.

### Declared attributes

| Attribute           | Type and multiplicity | What it captures and why it exists                                                                                                                                                                                                                                                                                                     | Accepted values and example                                                                                                                       |
| ------------------- | --------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------- |
| `inputPathsMapJson` | `String` [1]          | Stores the serialized JSON representation of input paths map on the event bridge input transformer. The field preserves structured provider or contract detail as an explicit, reviewable input. Generation role: The artifact generator references this feature in `mde/generation/awspsm-to-artifacts/lib/cfn.eol`.                  | A free-form `String`, subject to this class's semantic meaning and any EVL constraints. Example: `{"enabled":true}`.                              |
| `inputTemplate`     | `String` [1]          | Stores the input template value on the event bridge input transformer. The field keeps the model explicit and reviewable during review and transformation instead of leaving the decision to an inferred default. Generation role: The artifact generator references this feature in `mde/generation/awspsm-to-artifacts/lib/cfn.eol`. | A free-form `String`, subject to this class's semantic meaning and any EVL constraints. Example: `Event Bridge Input Transformer Input Template`. |

### Relationships

This class declares no direct relationships.

## `EventBridgeArchive`

Represents event bridge archive in the PSM vocabulary. It specializes `AwsResource` with the details needed for this modeling concern.

Direct supertypes: `AwsResource`. Inherited attributes and marker capabilities are documented in the [shared kernel](../shared-kernel.md); this section lists every attribute declared by this class.

### Declared attributes

| Attribute          | Type and multiplicity | What it captures and why it exists                                                                                                                                                                                                                                                                                                                                             | Accepted values and example                                                                                                   |
| ------------------ | --------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ | ----------------------------------------------------------------------------------------------------------------------------- |
| `archiveName`      | `String` [1]          | For a event bridge archive, the model records the stable name/key/code used for archive name. Keeping the fact with its owning element lets validation and refinement inspect it before artifact generation. Generation role: The artifact generator references this feature in `mde/generation/awspsm-to-artifacts/lib/cfn.eol`.                                              | A free-form `String`, subject to this class's semantic meaning and any EVL constraints. Example: `EventBridgeArchiveExample`. |
| `eventPatternJson` | `String` [1]          | Records the validation pattern for event pattern json for the event bridge archive. This keeps the decision explicit even when the element's class or relationships remain unchanged. The repository contains no direct EVL rule, ETL assignment, or artifact-generator read for this declared field. For now, it remains a model-level fact for review and future refinement. | A free-form `String`, subject to this class's semantic meaning and any EVL constraints. Example: `{"enabled":true}`.          |
| `retentionDays`    | `Integer` [1]         | Stores the data-lifecycle rule for retention days on the event bridge archive. The field records retention and data-lifecycle controls as an explicit, reviewable input. The repository contains no direct EVL rule, ETL assignment, or artifact-generator read for this declared field. For now, it remains a model-level fact for review and future refinement.              | A numeric `Integer` value; use the unit or boundary documented for this attribute. Example: `1`.                              |

### Relationships

| Relationship                        | Kind and multiplicity               | Meaning in the model                                                                                                                               |
| ----------------------------------- | ----------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------- |
| `eventSourceBus` → `EventBridgeBus` | reference, [1]; opposite `archives` | References the event bridge bus element(s) used as event source bus by this event bridge archive; the target may be shared elsewhere in the model. |

## `EventBridgeSchedule`

Represents event bridge schedule in the PSM vocabulary. It specializes `AwsResource` with the details needed for this modeling concern.

Direct supertypes: `AwsResource`. Inherited attributes and marker capabilities are documented in the [shared kernel](../shared-kernel.md); this section lists every attribute declared by this class.

### Declared attributes

| Attribute                    | Type and multiplicity | What it captures and why it exists                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                 | Accepted values and example                                                                                                    |
| ---------------------------- | --------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------ |
| `scheduleName`               | `String` [1]          | Stores the stable name/key/code used for schedule name on the event bridge schedule. The field keeps the model explicit and reviewable during review and transformation instead of leaving the decision to an inferred default. Transformation role: ETL rule `Schedule2EventBridgeSchedule` in `mde/transformations/pim-to-awspsm/data-messaging-events.etl` assigns or materializes this feature while refining `EventBridgeSchedule`. Generation role: The artifact generator references this feature in `mde/generation/awspsm-to-artifacts/lib/cfn.eol`.                      | A free-form `String`, subject to this class's semantic meaning and any EVL constraints. Example: `EventBridgeScheduleExample`. |
| `scheduleExpression`         | `String` [1]          | Records expression or rule that governs schedule expression for event bridge schedule. It keeps this decision explicit during review and transformation, so later steps do not have to infer it. Semantic validation: `ScheduleHasExpressionAndRole` (schedule has expression and role) in `mde/validation/psm/rules/events.evl` the value must be present and non-blank. Transformation role: ETL rule `Schedule2EventBridgeSchedule` in `mde/transformations/pim-to-awspsm/data-messaging-events.etl` assigns or materializes this feature while refining `EventBridgeSchedule`. | A free-form `String`, subject to this class's semantic meaning and any EVL constraints. Example: `$.status == 'READY'`.        |
| `scheduleExpressionTimezone` | `String` [1]          | For a event bridge schedule, the model records the schedule expression timezone value. Keeping the fact with its owning element lets validation and refinement inspect it before artifact generation. Transformation role: ETL rule `Schedule2EventBridgeSchedule` in `mde/transformations/pim-to-awspsm/data-messaging-events.etl` assigns or materializes this feature while refining `EventBridgeSchedule`. Generation role: The artifact generator references this feature in `mde/generation/awspsm-to-artifacts/lib/cfn.eol`.                                                | A free-form `String`, subject to this class's semantic meaning and any EVL constraints. Example: `$.status == 'READY'`.        |
| `flexibleTimeWindowJson`     | `String` [1]          | For a event bridge schedule, the model records the serialized JSON representation of flexible time window. Keeping the fact with its owning element lets validation and refinement inspect it before artifact generation. Transformation role: ETL rule `Schedule2EventBridgeSchedule` in `mde/transformations/pim-to-awspsm/data-messaging-events.etl` assigns or materializes this feature while refining `EventBridgeSchedule`. Generation role: The artifact generator references this feature in `mde/generation/awspsm-to-artifacts/lib/cfn.eol`.                            | A free-form `String`, subject to this class's semantic meaning and any EVL constraints. Example: `{"enabled":true}`.           |
| `state`                      | `String` [1]          | Records the state value for event bridge schedule. It keeps this decision explicit during review and transformation, so later steps do not have to infer it. Transformation role: ETL rule `Schedule2EventBridgeSchedule` in `mde/transformations/pim-to-awspsm/data-messaging-events.etl` assigns or materializes this feature while refining `EventBridgeSchedule`.                                                                                                                                                                                                              | A free-form `String`, subject to this class's semantic meaning and any EVL constraints. Example: `ACTIVE`.                     |
| `descriptionText`            | `String` [1]          | Records human explanation of description text for event bridge schedule. It keeps human review and generated guidance explicit during review and transformation, so later steps do not have to infer it. Transformation role: ETL rule `Schedule2EventBridgeSchedule` in `mde/transformations/pim-to-awspsm/data-messaging-events.etl` assigns or materializes this feature while refining `EventBridgeSchedule`.                                                                                                                                                                  | A free-form `String`, subject to this class's semantic meaning and any EVL constraints. Example: `Orders processing`.          |

### Relationships

| Relationship                                           | Kind and multiplicity | Meaning in the model                                                                                                                                   |
| ------------------------------------------------------ | --------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------ |
| `flexibleTimeWindow` → `EventBridgeFlexibleTimeWindow` | containment, [?]      | Contains the event bridge flexible time window element(s) that make up this event bridge schedule; the contained objects belong to this model element. |
| `target` → `EventBridgeTarget`                         | containment, [1]      | Contains the event bridge target element(s) that make up this event bridge schedule; the contained objects belong to this model element.               |
| `role` → `IamRole`                                     | reference, [1]        | References the iam role element(s) used as role by this event bridge schedule; the target may be shared elsewhere in the model.                        |

## `EventBridgeFlexibleTimeWindow`

Represents event bridge flexible time window in the PSM vocabulary. It specializes `TraceableElement` with the details needed for this modeling concern.

Direct supertypes: `TraceableElement`. Inherited attributes and marker capabilities are documented in the [shared kernel](../shared-kernel.md); this section lists every attribute declared by this class.

### Declared attributes

| Attribute                | Type and multiplicity | What it captures and why it exists                                                                                                                                                                                                                                                                                                                                                                                                                 | Accepted values and example                                                                                                                |
| ------------------------ | --------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------ |
| `mode`                   | `String` [1]          | Stores the controlled classification or strategy represented by mode on the event bridge flexible time window. The field keeps the model explicit and reviewable during review and transformation instead of leaving the decision to an inferred default. The repository contains no direct EVL rule, ETL assignment, or artifact-generator read for this declared field. For now, it remains a model-level fact for review and future refinement. | A free-form `String`, subject to this class's semantic meaning and any EVL constraints. Example: `Event Bridge Flexible Time Window Mode`. |
| `maximumWindowInMinutes` | `Integer` [1]         | Records the maximum window in minutes duration or limit, expressed in minutes for the event bridge flexible time window. This keeps the decision explicit even when the element's class or relationships remain unchanged. The repository contains no direct EVL rule, ETL assignment, or artifact-generator read for this declared field. For now, it remains a model-level fact for review and future refinement.                                | A numeric `Integer` value; use the unit or boundary documented for this attribute. Example: `1`.                                           |

### Relationships

This class declares no direct relationships.

## `EventBridgePipe`

Represents event bridge pipe in the PSM vocabulary. It specializes `AwsResource` with the details needed for this modeling concern.

Direct supertypes: `AwsResource`. Inherited attributes and marker capabilities are documented in the [shared kernel](../shared-kernel.md); this section lists every attribute declared by this class.

### Declared attributes

| Attribute              | Type and multiplicity | What it captures and why it exists                                                                                                                                                                                                                                                                                                                                                                                                                                      | Accepted values and example                                                                                                                               |
| ---------------------- | --------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `pipeName`             | `String` [1]          | For a event bridge pipe, the model records the stable name/key/code used for pipe name. Keeping the fact with its owning element lets validation and refinement inspect it before artifact generation. Generation role: The artifact generator references this feature in `mde/generation/awspsm-to-artifacts/lib/cfn.eol`.                                                                                                                                             | A free-form `String`, subject to this class's semantic meaning and any EVL constraints. Example: `EventBridgePipeExample`.                                |
| `sourceArn`            | `String` [1]          | Records the origin/source selected for source arn for the event bridge pipe. This keeps the decision explicit even when the element's class or relationships remain unchanged. Semantic validation: `PipeHasSourceTargetAndRole` (pipe has source target and role) in `mde/validation/psm/rules/events.evl` the value must be present and non-blank.                                                                                                                    | A free-form `String`, subject to this class's semantic meaning and any EVL constraints. Example: `arn:aws:lambda:us-east-1:123456789012:function:orders`. |
| `targetArn`            | `String` [1]          | Records destination selected for target arn for event bridge pipe. It keeps this decision explicit during review and transformation, so later steps do not have to infer it. Semantic validation: `PipeHasSourceTargetAndRole` (pipe has source target and role) in `mde/validation/psm/rules/events.evl` the value must be present and non-blank. Generation role: The artifact generator references this feature in `mde/generation/awspsm-to-artifacts/lib/cfn.eol`. | A free-form `String`, subject to this class's semantic meaning and any EVL constraints. Example: `arn:aws:lambda:us-east-1:123456789012:function:orders`. |
| `filterCriteriaJson`   | `String` [1]          | For a event bridge pipe, the model records the serialized JSON representation of filter criteria. Keeping the fact with its owning element lets validation and refinement inspect it before artifact generation. The repository contains no direct EVL rule, ETL assignment, or artifact-generator read for this declared field. For now, it remains a model-level fact for review and future refinement.                                                               | A free-form `String`, subject to this class's semantic meaning and any EVL constraints. Example: `{"enabled":true}`.                                      |
| `enrichmentArn`        | `String` [1]          | Records the AWS ARN used for enrichment for the event bridge pipe. This keeps the decision explicit even when the element's class or relationships remain unchanged. Generation role: The artifact generator references this feature in `mde/generation/awspsm-to-artifacts/lib/cfn.eol`.                                                                                                                                                                               | A free-form `String`, subject to this class's semantic meaning and any EVL constraints. Example: `arn:aws:lambda:us-east-1:123456789012:function:orders`. |
| `sourceParametersJson` | `String` [1]          | For a event bridge pipe, the model records the origin/source selected for source parameters json. Keeping the fact with its owning element lets validation and refinement inspect it before artifact generation. Generation role: The artifact generator references this feature in `mde/generation/awspsm-to-artifacts/lib/cfn.eol`.                                                                                                                                   | A free-form `String`, subject to this class's semantic meaning and any EVL constraints. Example: `{"enabled":true}`.                                      |
| `targetParametersJson` | `String` [1]          | Stores the destination selected for target parameters json on the event bridge pipe. The field preserves structured provider or contract detail as an explicit, reviewable input. Generation role: The artifact generator references this feature in `mde/generation/awspsm-to-artifacts/lib/cfn.eol`.                                                                                                                                                                  | A free-form `String`, subject to this class's semantic meaning and any EVL constraints. Example: `{"enabled":true}`.                                      |
| `desiredState`         | `String` [1]          | Stores the desired state value on the event bridge pipe. The field keeps the model explicit and reviewable during review and transformation instead of leaving the decision to an inferred default. Generation role: The artifact generator references this feature in `mde/generation/awspsm-to-artifacts/lib/cfn.eol`.                                                                                                                                                | A free-form `String`, subject to this class's semantic meaning and any EVL constraints. Example: `ACTIVE`.                                                |

### Relationships

| Relationship                               | Kind and multiplicity | Meaning in the model                                                                                                                                  |
| ------------------------------------------ | --------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------- |
| `sourceResource` → `AwsResource`           | reference, [?]        | References the aws resource element(s) used as source resource by this event bridge pipe; the target may be shared elsewhere in the model.            |
| `targetResource` → `AwsResource`           | reference, [?]        | References the aws resource element(s) used as target resource by this event bridge pipe; the target may be shared elsewhere in the model.            |
| `enrichmentFunction` → `AwsLambdaFunction` | reference, [?]        | References the aws lambda function element(s) used as enrichment function by this event bridge pipe; the target may be shared elsewhere in the model. |
| `role` → `IamRole`                         | reference, [1]        | References the iam role element(s) used as role by this event bridge pipe; the target may be shared elsewhere in the model.                           |

## `EventBridgeAuthParameters`

An abstract event bridge auth parameters concept. Use one of its concrete subtypes when creating a model instance; the shared attributes and relationships defined here still apply.

Direct supertypes: `TraceableElement`. Inherited attributes and marker capabilities are documented in the [shared kernel](../shared-kernel.md); this section lists every attribute declared by this class.

### Declared attributes

This class declares no attributes of its own. It inherits the attributes of its supertype, if any.

### Relationships

This class declares no direct relationships.

## `EventBridgeApiKeyAuthParameters`

Represents event bridge api key auth parameters in the PSM vocabulary. It specializes `EventBridgeAuthParameters` with the details needed for this modeling concern.

Direct supertypes: `EventBridgeAuthParameters`. Inherited attributes and marker capabilities are documented in the [shared kernel](../shared-kernel.md); this section lists every attribute declared by this class.

### Declared attributes

| Attribute    | Type and multiplicity | What it captures and why it exists                                                                                                                                                                                                                                                                                                                                                                                    | Accepted values and example                                                                                 |
| ------------ | --------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ----------------------------------------------------------------------------------------------------------- |
| `apiKeyName` | `String` [1]          | For a event bridge api key auth parameters, the model records the stable name/key/code used for api key name. Keeping the fact with its owning element lets validation and refinement inspect it before artifact generation. The repository contains no direct EVL rule, ETL assignment, or artifact-generator read for this declared field. For now, it remains a model-level fact for review and future refinement. | A free-form `String`, subject to this class's semantic meaning and any EVL constraints. Example: `orderId`. |

### Relationships

| Relationship                      | Kind and multiplicity | Meaning in the model                                                                                                                                 |
| --------------------------------- | --------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------- |
| `apiKeyValue` → `ValueExpression` | containment, [1]      | Contains the value expression element(s) that make up this event bridge api key auth parameters; the contained objects belong to this model element. |

## `EventBridgeBasicAuthParameters`

Represents event bridge basic auth parameters in the PSM vocabulary. It specializes `EventBridgeAuthParameters` with the details needed for this modeling concern.

Direct supertypes: `EventBridgeAuthParameters`. Inherited attributes and marker capabilities are documented in the [shared kernel](../shared-kernel.md); this section lists every attribute declared by this class.

### Declared attributes

This class declares no attributes of its own. It inherits the attributes of its supertype, if any.

### Relationships

| Relationship                   | Kind and multiplicity | Meaning in the model                                                                                                                               |
| ------------------------------ | --------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------- |
| `username` → `ValueExpression` | containment, [1]      | Contains the value expression element(s) that make up this event bridge basic auth parameters; the contained objects belong to this model element. |
| `password` → `ValueExpression` | containment, [1]      | Contains the value expression element(s) that make up this event bridge basic auth parameters; the contained objects belong to this model element. |

## `EventBridgeOAuthParameters`

Represents event bridge oauth parameters in the PSM vocabulary. It specializes `EventBridgeAuthParameters` with the details needed for this modeling concern.

Direct supertypes: `EventBridgeAuthParameters`. Inherited attributes and marker capabilities are documented in the [shared kernel](../shared-kernel.md); this section lists every attribute declared by this class.

### Declared attributes

| Attribute               | Type and multiplicity       | What it captures and why it exists                                                                                                                                                                                                                                                                                                                                                                             | Accepted values and example                                                                                                                              |
| ----------------------- | --------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `authorizationEndpoint` | `String` [1]                | Stores the authorization endpoint value on the event bridge oauth parameters. The field keeps the model explicit and reviewable during review and transformation instead of leaving the decision to an inferred default. Generation role: The artifact generator references this feature in `mde/generation/awspsm-to-artifacts/lib/values.eol`.                                                               | A free-form `String`, subject to this class's semantic meaning and any EVL constraints. Example: `Event Bridge Oauth Parameters Authorization Endpoint`. |
| `httpMethod`            | `EventBridgeHttpMethod` [1] | Records controlled classification or strategy represented by http method for event bridge oauth parameters. It keeps this decision explicit during review and transformation, so later steps do not have to infer it. The repository contains no direct EVL rule, ETL assignment, or artifact-generator read for this declared field. For now, it remains a model-level fact for review and future refinement. | Exactly one of: `GET`, `POST`, `PUT`, `PATCH`, `DELETE`, `HEAD`, `OPTIONS`. Example: `GET`.                                                              |

### Relationships

| Relationship                                        | Kind and multiplicity | Meaning in the model                                                                                                                                      |
| --------------------------------------------------- | --------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `clientId` → `ValueExpression`                      | containment, [1]      | Contains the value expression element(s) that make up this event bridge oauth parameters; the contained objects belong to this model element.             |
| `clientSecret` → `ValueExpression`                  | containment, [1]      | Contains the value expression element(s) that make up this event bridge oauth parameters; the contained objects belong to this model element.             |
| `oauthHttpParameters` → `EventBridgeHttpParameters` | containment, [?]      | Contains the event bridge http parameters element(s) that make up this event bridge oauth parameters; the contained objects belong to this model element. |

## `EventBridgeHttpParameters`

Represents event bridge http parameters in the PSM vocabulary. It specializes `TraceableElement` with the details needed for this modeling concern.

Direct supertypes: `TraceableElement`. Inherited attributes and marker capabilities are documented in the [shared kernel](../shared-kernel.md); this section lists every attribute declared by this class.

### Declared attributes

This class declares no attributes of its own. It inherits the attributes of its supertype, if any.

### Relationships

| Relationship                              | Kind and multiplicity | Meaning in the model                                                                                                                       |
| ----------------------------------------- | --------------------- | ------------------------------------------------------------------------------------------------------------------------------------------ |
| `headerParameters` → `HttpParameter`      | containment, [*]      | Contains the http parameter element(s) that make up this event bridge http parameters; the contained objects belong to this model element. |
| `queryStringParameters` → `HttpParameter` | containment, [*]      | Contains the http parameter element(s) that make up this event bridge http parameters; the contained objects belong to this model element. |
| `bodyParameters` → `HttpParameter`        | containment, [*]      | Contains the http parameter element(s) that make up this event bridge http parameters; the contained objects belong to this model element. |

## `EventBridgeConnection`

Represents event bridge connection in the PSM vocabulary. It specializes `AwsResource` with the details needed for this modeling concern.

Direct supertypes: `AwsResource`. Inherited attributes and marker capabilities are documented in the [shared kernel](../shared-kernel.md); this section lists every attribute declared by this class.

### Declared attributes

| Attribute           | Type and multiplicity                        | What it captures and why it exists                                                                                                                                                                                                                                                                                                                                                                                                                                                              | Accepted values and example                                                                                                      |
| ------------------- | -------------------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | -------------------------------------------------------------------------------------------------------------------------------- |
| `connectionName`    | `String` [1]                                 | For a event bridge connection, the model records the stable name/key/code used for connection name. Keeping the fact with its owning element lets validation and refinement inspect it before artifact generation. Generation role: The artifact generator references this feature in `mde/generation/awspsm-to-artifacts/lib/cfn.eol`.                                                                                                                                                         | A free-form `String`, subject to this class's semantic meaning and any EVL constraints. Example: `EventBridgeConnectionExample`. |
| `authorizationType` | `EventBridgeConnectionAuthorizationType` [1] | Stores the controlled classification or strategy represented by authorization type on the event bridge connection. The field keeps the model explicit and reviewable during review and transformation instead of leaving the decision to an inferred default. Semantic validation: `ConnectionAuthParametersMatchAuthorizationType` (connection auth parameters match authorization type) in `mde/validation/psm/rules/events.evl` the feature participates in a semantic validation condition. | Exactly one of: `API_KEY`, `BASIC`, `OAUTH_CLIENT_CREDENTIALS`. Example: `API_KEY`.                                              |

### Relationships

| Relationship                                             | Kind and multiplicity | Meaning in the model                                                                                                                                |
| -------------------------------------------------------- | --------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------- |
| `authParameters` → `EventBridgeAuthParameters`           | containment, [1]      | Contains the event bridge auth parameters element(s) that make up this event bridge connection; the contained objects belong to this model element. |
| `invocationHttpParameters` → `EventBridgeHttpParameters` | containment, [?]      | Contains the event bridge http parameters element(s) that make up this event bridge connection; the contained objects belong to this model element. |

## `EventBridgeApiDestination`

Represents event bridge api destination in the PSM vocabulary. It specializes `AwsResource` with the details needed for this modeling concern.

Direct supertypes: `AwsResource`. Inherited attributes and marker capabilities are documented in the [shared kernel](../shared-kernel.md); this section lists every attribute declared by this class.

### Declared attributes

| Attribute                      | Type and multiplicity       | What it captures and why it exists                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                      | Accepted values and example                                                                                                                          |
| ------------------------------ | --------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------- |
| `destinationName`              | `String` [1]                | Stores the destination selected for destination name on the event bridge api destination. The field keeps the model explicit and reviewable during review and transformation instead of leaving the decision to an inferred default. Generation role: The artifact generator references this feature in `mde/generation/awspsm-to-artifacts/lib/cfn.eol`.                                                                                                                                                                               | A free-form `String`, subject to this class's semantic meaning and any EVL constraints. Example: `EventBridgeApiDestinationExample`.                 |
| `invocationEndpoint`           | `String` [1]                | Stores the invocation endpoint value on the event bridge api destination. The field keeps the model explicit and reviewable during review and transformation instead of leaving the decision to an inferred default. Semantic validation: `ApiDestinationHasEndpointAndConnection` (api destination has endpoint and connection) in `mde/validation/psm/rules/events.evl` the value must be present and non-blank. Generation role: The artifact generator references this feature in `mde/generation/awspsm-to-artifacts/lib/cfn.eol`. | A free-form `String`, subject to this class's semantic meaning and any EVL constraints. Example: `Event Bridge Api Destination Invocation Endpoint`. |
| `httpMethod`                   | `EventBridgeHttpMethod` [1] | For a event bridge api destination, the model records the controlled classification or strategy represented by http method. Keeping the fact with its owning element lets validation and refinement inspect it before artifact generation. The repository contains no direct EVL rule, ETL assignment, or artifact-generator read for this declared field. For now, it remains a model-level fact for review and future refinement.                                                                                                     | Exactly one of: `GET`, `POST`, `PUT`, `PATCH`, `DELETE`, `HEAD`, `OPTIONS`. Example: `GET`.                                                          |
| `invocationRateLimitPerSecond` | `Integer` [1]               | Records the invocation rate limit per second value for the event bridge api destination. This keeps the decision explicit even when the element's class or relationships remain unchanged. Generation role: The artifact generator references this feature in `mde/generation/awspsm-to-artifacts/lib/cfn.eol`.                                                                                                                                                                                                                         | A numeric `Integer` value; use the unit or boundary documented for this attribute. Example: `1`.                                                     |

### Relationships

| Relationship                           | Kind and multiplicity | Meaning in the model                                                                                                                                        |
| -------------------------------------- | --------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `connection` → `EventBridgeConnection` | reference, [1]        | References the event bridge connection element(s) used as connection by this event bridge api destination; the target may be shared elsewhere in the model. |

## `AwsRetryPolicy`

Represents aws retry policy in the PSM vocabulary. It specializes `TraceableElement` with the details needed for this modeling concern.

Direct supertypes: `TraceableElement`. Inherited attributes and marker capabilities are documented in the [shared kernel](../shared-kernel.md); this section lists every attribute declared by this class.

### Declared attributes

| Attribute                  | Type and multiplicity | What it captures and why it exists                                                                                                                                                                                                                                                                                                                                                                             | Accepted values and example                                                                      |
| -------------------------- | --------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------ |
| `maximumRetryAttempts`     | `Integer` [1]         | Records the maximum retry attempts value for the aws retry policy. This keeps the decision explicit even when the element's class or relationships remain unchanged. Semantic validation: `RetryPolicyRangesValid` (retry policy ranges valid) in `mde/validation/psm/rules/events.evl` the value must remain absent in this modeling situation.                                                               | A numeric `Integer` value; use the unit or boundary documented for this attribute. Example: `1`. |
| `maximumEventAgeInSeconds` | `Integer` [1]         | For a aws retry policy, the model records the maximum event age in seconds duration or limit, expressed in seconds. Keeping the fact with its owning element lets validation and refinement inspect it before artifact generation. Semantic validation: `RetryPolicyRangesValid` (retry policy ranges valid) in `mde/validation/psm/rules/events.evl` the value must remain absent in this modeling situation. | A numeric `Integer` value; use the unit or boundary documented for this attribute. Example: `1`. |

### Relationships

This class declares no direct relationships.
