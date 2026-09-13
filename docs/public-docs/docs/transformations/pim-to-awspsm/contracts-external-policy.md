# PIM → AWS PSM: Contracts External Policy

Contract and external-policy binding creates AWS-side structured documents and monitoring resources from PIM schemas, event types, business rules, decision models, external adapters, and alert policies. These documents are the bridge between provider-independent meaning and generated OpenAPI/JSON/ASL/IAM-facing artifacts.

Source module: `mde/transformations/pim-to-awspsm/contracts-external-policy.etl`.

## Reading this page

A transformation rule determines whether a source element contributes to the target model and how it is mapped. Use the guard to understand routing and the target table to see the model-level result. The behavior section records important semantic side effects. Trace and manual-decision information identifies work for review and later phases.

---

## Supporting ETL operations

Supporting operations also shape the transformation. They derive defaults, create secondary resources, cache correspondences, and resolve relationships after the main rule runs.

| Operation                                      | Role                                                 | Source                                                                |
| ---------------------------------------------- | ---------------------------------------------------- | --------------------------------------------------------------------- |
| `renderJsonSchema`                             | Renders render json schema.                          | `mde/transformations/pim-to-awspsm/contracts-external-policy.etl:129` |
| `eventContractJson`                            | Computes event contract json.                        | `mde/transformations/pim-to-awspsm/contracts-external-policy.etl:134` |
| `businessRuleDocumentJson`                     | Computes business rule document json.                | `mde/transformations/pim-to-awspsm/contracts-external-policy.etl:144` |
| `applyFunctionPolicies`                        | Computes apply function policies.                    | `mde/transformations/pim-to-awspsm/contracts-external-policy.etl:153` |
| `applyIdempotency`                             | Computes apply idempotency.                          | `mde/transformations/pim-to-awspsm/contracts-external-policy.etl:168` |
| `applyDataProtectionPolicies`                  | Computes apply data protection policies.             | `mde/transformations/pim-to-awspsm/contracts-external-policy.etl:183` |
| `applyObservability`                           | Computes apply observability.                        | `mde/transformations/pim-to-awspsm/contracts-external-policy.etl:201` |
| `createPolicyAlarm`                            | Creates policy alarm.                                | `mde/transformations/pim-to-awspsm/contracts-external-policy.etl:238` |
| `applySecurityPolicies`                        | Computes apply security policies.                    | `mde/transformations/pim-to-awspsm/contracts-external-policy.etl:260` |
| `createLambdaAlarm`                            | Creates lambda alarm.                                | `mde/transformations/pim-to-awspsm/contracts-external-policy.etl:275` |
| `createLambdaDeadLetterConfig`                 | Creates lambda dead letter config.                   | `mde/transformations/pim-to-awspsm/contracts-external-policy.etl:298` |
| `isHttpAdapter`                                | Returns whether the receiver is http adapter.        | `mde/transformations/pim-to-awspsm/contracts-external-policy.etl:320` |
| `createEventBridgeConnectionAndApiDestination` | Creates event bridge connection and api destination. | `mde/transformations/pim-to-awspsm/contracts-external-policy.etl:330` |
| `endpointOf`                                   | Computes endpoint of.                                | `mde/transformations/pim-to-awspsm/contracts-external-policy.etl:361` |
| `createConnectionAuthParameters`               | Creates connection auth parameters.                  | `mde/transformations/pim-to-awspsm/contracts-external-policy.etl:369` |
| `credentialSecretValue`                        | Computes credential secret value.                    | `mde/transformations/pim-to-awspsm/contracts-external-policy.etl:409` |
| `credentialReferenceName`                      | Computes credential reference name.                  | `mde/transformations/pim-to-awspsm/contracts-external-policy.etl:414` |
| `firstCredentialName`                          | Computes first credential name.                      | `mde/transformations/pim-to-awspsm/contracts-external-policy.etl:433` |
| `firstCredentialValue`                         | Computes first credential value.                     | `mde/transformations/pim-to-awspsm/contracts-external-policy.etl:441` |
| `inferConnectionAuthType`                      | Derives infer connection auth type.                  | `mde/transformations/pim-to-awspsm/contracts-external-policy.etl:453` |
| `endpointFromDescription`                      | Computes endpoint from description.                  | `mde/transformations/pim-to-awspsm/contracts-external-policy.etl:464` |
| `inferMetricNamespace`                         | Derives infer metric namespace.                      | `mde/transformations/pim-to-awspsm/contracts-external-policy.etl:476` |
| `parseComparison`                              | Converts the receiver to parse comparison.           | `mde/transformations/pim-to-awspsm/contracts-external-policy.etl:484` |
| `parseThreshold`                               | Converts the receiver to parse threshold.            | `mde/transformations/pim-to-awspsm/contracts-external-policy.etl:499` |

---

## `Schema2StructuredDocument`

**Source:** `s` to `CONTRACTS!Schema`  
**Target:** `d` to `KERNEL!StructuredDocument`  
**Source location:** `mde/transformations/pim-to-awspsm/contracts-external-policy.etl:2`

### Why this rule exists

A PIM schema becomes an AWS-side structured document so generated OpenAPI, JSON Schema, and contract artifacts can refer to provider-bound metadata without losing schema kind, version, fields, and compatibility.

### When the rule runs

There is no explicit guard, so every source instance of the declared type is eligible for this mapping. Eligibility does not guarantee that every optional relationship or downstream target can be resolved.

### What it creates

- `d` (`KERNEL!StructuredDocument`): Generated structured document (d).

### Important behavior encoded in the rule

The rule directly assigns: `d.id`, `d.name`, `d.format`, `d.content`, `d.externalUri`.

### How to troubleshoot or repair it

Verify that the schema is present and semantically complete, then rerun the transformation. If the target is missing or incomplete, inspect the source attributes named in the rule body, the referenced helper operation, and the post-phase that resolves its relationships before changing the ETL itself.

### Authoritative source

See the complete ETL rule at `mde/transformations/pim-to-awspsm/contracts-external-policy.etl:2`. The purpose and observable effects of the rule are summarized here. Consult the ETL body for exact assignments and helper calls.

---

## `EventType2StructuredDocument`

**Source:** `e` to `CONTRACTS!EventType`  
**Target:** `d` to `KERNEL!StructuredDocument`  
**Source location:** `mde/transformations/pim-to-awspsm/contracts-external-policy.etl:16`

### Why this rule exists

An event type becomes a structured PSM document carrying its event name, version, subject, envelope, and payload schema. This gives EventBridge/SNS/SQS generation a concrete contract representation rather than reconstructing one from a resource.

### When the rule runs

There is no explicit guard, so every source instance of the declared type is eligible for this mapping. Eligibility does not guarantee that every optional relationship or downstream target can be resolved.

### What it creates

- `d` (`KERNEL!StructuredDocument`): Generated structured document (d).

### Important behavior encoded in the rule

The rule directly assigns: `d.id`, `d.name`, `d.format`, `d.content`.

### How to troubleshoot or repair it

Verify that the event type is present and semantically complete, then rerun the transformation. If the target is missing or incomplete, inspect the source attributes named in the rule body, the referenced helper operation, and the post-phase that resolves its relationships before changing the ETL itself.

### Authoritative source

See the complete ETL rule at `mde/transformations/pim-to-awspsm/contracts-external-policy.etl:16`. The purpose and observable effects of the rule are summarized here. Consult the ETL body for exact assignments and helper calls.

---

## `BusinessRule2StructuredDocument`

**Source:** `b` to `POLICY!BusinessRule`  
**Target:** `d` to `KERNEL!StructuredDocument`  
**Source location:** `mde/transformations/pim-to-awspsm/contracts-external-policy.etl:29`

### Why this rule exists

Business rules are retained as structured PSM metadata for generated documentation and review. They are not automatically turned into IAM or runtime conditions because business prose and deployable authorization are different semantic layers.

### When the rule runs

There is no explicit guard, so every source instance of the declared type is eligible for this mapping. Eligibility does not guarantee that every optional relationship or downstream target can be resolved.

### What it creates

- `d` (`KERNEL!StructuredDocument`): Generated structured document (d).

### Important behavior encoded in the rule

The rule directly assigns: `d.id`, `d.name`, `d.format`, `d.content`.

### How to troubleshoot or repair it

Verify that the business rule is present and semantically complete, then rerun the transformation. If the target is missing or incomplete, inspect the source attributes named in the rule body, the referenced helper operation, and the post-phase that resolves its relationships before changing the ETL itself.

### Authoritative source

See the complete ETL rule at `mde/transformations/pim-to-awspsm/contracts-external-policy.etl:29`. The purpose and observable effects of the rule are summarized here. Consult the ETL body for exact assignments and helper calls.

---

## `DecisionModel2PsmMetadata`

**Source:** `dm` to `POLICY!DecisionModel`  
**Target:** `n` to `AWSPSMCORE!AwsNativeResource`  
**Source location:** `mde/transformations/pim-to-awspsm/contracts-external-policy.etl:42`

### Why this rule exists

Decision models become structured provider metadata so generated artifacts can explain which business decision informed a resource or workflow branch. The rule preserves evidence without pretending every decision table can be compiled into AWS configuration.

### When the rule runs

There is no explicit guard, so every source instance of the declared type is eligible for this mapping. Eligibility does not guarantee that every optional relationship or downstream target can be resolved.

### What it creates

- `n` (`AWSPSMCORE!AwsNativeResource`): Provider-side trace and documentation metadata.

### Important behavior encoded in the rule

The rule directly assigns: `n.id`, `n.cloudFormationType`.
Manual decisions raised by this rule: `DECISION_MODEL_IMPLEMENTATION_REQUIRED`. These are intentional hand-off points. Resolve them in the model review/readiness workflow; they do not indicate transformation failure.

### How to troubleshoot or repair it

Start with the manual decision(s) `DECISION_MODEL_IMPLEMENTATION_REQUIRED` and complete the requested provider or business choice. Then re-run the transformation and validate the resulting target. If the rule did not produce an object, inspect whether the decision model actually satisfies its guard and whether the required upstream correspondence exists.

### Authoritative source

See the complete ETL rule at `mde/transformations/pim-to-awspsm/contracts-external-policy.etl:42`. The purpose and observable effects of the rule are summarized here. Consult the ETL body for exact assignments and helper calls.

---

## `ExternalAdapter2PsmMetadata`

**Source:** `a` to `EXTERNAL!ExternalAdapter`  
**Target:** `n` to `AWSPSMCORE!AwsNativeResource`  
**Source location:** `mde/transformations/pim-to-awspsm/contracts-external-policy.etl:60`

### Why this rule exists

External adapters become structured PSM metadata containing endpoint/protocol and trust context. The output is intentionally metadata because credentials, network paths, and provider integrations may require a manual AWS choice beyond the PIM adapter abstraction.

### When the rule runs

There is no explicit guard, so every source instance of the declared type is eligible for this mapping. Eligibility does not guarantee that every optional relationship or downstream target can be resolved.

### What it creates

- `n` (`AWSPSMCORE!AwsNativeResource`): Provider-side trace and documentation metadata.

### Important behavior encoded in the rule

The rule directly assigns: `n.id`, `n.cloudFormationType`.
Manual decisions raised by this rule: `EXTERNAL_ADAPTER_ENDPOINT_REQUIRED`, `EXTERNAL_ADAPTER_CREDENTIALS_REQUIRED`, `EXTERNAL_ADAPTER_NETWORK_REQUIRED`. These are intentional hand-off points. Resolve them in the model review/readiness workflow; they do not indicate transformation failure.

### How to troubleshoot or repair it

Start with the manual decision(s) `EXTERNAL_ADAPTER_ENDPOINT_REQUIRED, EXTERNAL_ADAPTER_CREDENTIALS_REQUIRED, EXTERNAL_ADAPTER_NETWORK_REQUIRED` and complete the requested provider or business choice. Then re-run the transformation and validate the resulting target. If the rule did not produce an object, inspect whether the external adapter actually satisfies its guard and whether the required upstream correspondence exists.

### Authoritative source

See the complete ETL rule at `mde/transformations/pim-to-awspsm/contracts-external-policy.etl:60`. The purpose and observable effects of the rule are summarized here. Consult the ETL body for exact assignments and helper calls.

---

## `AlertPolicy2CloudWatchAlarm`

**Source:** `a` to `POLICY!AlertPolicy`  
**Target:** `alarm` to `AWSPSMOBSERVABILITY!CloudWatchAlarm`  
**Source location:** `mde/transformations/pim-to-awspsm/contracts-external-policy.etl:99`

### Why this rule exists

A PIM alert policy becomes a CloudWatch alarm with metric, threshold, evaluation, comparison, and actions. The mapping carries operational intent into a provider object that can actually signal an operator, while preserving unresolved metric parsing as reviewable metadata when needed.

### When the rule runs

There is no explicit guard, so every source instance of the declared type is eligible for this mapping. Eligibility does not guarantee that every optional relationship or downstream target can be resolved.

### What it creates

- `alarm` (`AWSPSMOBSERVABILITY!CloudWatchAlarm`): Generated cloud watch alarm (alarm).

### Important behavior encoded in the rule

The rule directly assigns: `alarm.id`, `alarm.alarmName`, `alarm.alarmDescription`, `alarm.namespace`, `alarm.metricName`, `alarm.statistic`, `alarm.period`, `alarm.evaluationPeriods`, `alarm.datapointsToAlarm`, `alarm.threshold`, `alarm.comparisonOperator`, `alarm.treatMissingData`.
Manual decisions raised by this rule: `ALARM_THRESHOLD_REQUIRED`. These are intentional hand-off points. Resolve them in the model review/readiness workflow; they do not indicate transformation failure.

### How to troubleshoot or repair it

Start with the manual decision(s) `ALARM_THRESHOLD_REQUIRED` and complete the requested provider or business choice. Then re-run the transformation and validate the resulting target. If the rule did not produce an object, inspect whether the alert policy actually satisfies its guard and whether the required upstream correspondence exists.

### Authoritative source

See the complete ETL rule at `mde/transformations/pim-to-awspsm/contracts-external-policy.etl:99`. The purpose and observable effects of the rule are summarized here. Consult the ETL body for exact assignments and helper calls.

---
