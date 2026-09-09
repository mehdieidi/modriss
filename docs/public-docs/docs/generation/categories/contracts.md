# Code generation — Contracts

Contract templates publish the executable boundaries of the generated system: OpenAPI, ASL, JSON Schema, event fixtures, and schema catalogs. They are generated from the already-bound PSM, so their purpose is to keep clients, workflows, tests, and deployment artifacts looking at the same modeled contract.

The category contains 11 EGX generation rules and 5 EGL templates.

## Template inventory

| Template                                 | What it contains                                                               |     Size |
| ---------------------------------------- | ------------------------------------------------------------------------------ | -------: |
| `templates/contracts/asl.egl`            | Generates the ASL artifact as a single formatted JSON document.                |  1 lines |
| `templates/contracts/catalog-schema.egl` | Generates the catalog schema artifact.                                         | 12 lines |
| `templates/contracts/json-schema.egl`    | Generates the json schema artifact.                                            |  1 lines |
| `templates/contracts/openapi.egl`        | Generates the openapi artifact. Modeled routes are authoritative when present. |  8 lines |
| `templates/contracts/sample-event.egl`   | Generates the sample event artifact.                                           | 10 lines |

---

## `ApiToOpenApi`

**Source context:** `apiObj` — `AWSPSMAPI!ApiGatewayApi`  
**Template:** `contracts/openapi.egl`  
**Target path expression:** `apiObj.openApiPath()`  
**EGX source:** `mde/generation/awspsm-to-artifacts/awspsm2artifacts.egx:225`

### Why this generation rule exists

The OpenAPI document is the external contract of a modeled API, not a second hand-written API definition. It is emitted only when enabled and when routes exist, so an empty or intentionally undocumented API does not produce misleading interface documentation.

### When it runs

The rule is conditional. A false guard is a deliberate omission boundary, not an empty generated file:

```egx
guard : emitCtx().get("enableOpenApi") = true and apiObj.routes.notEmpty()
```

### Artifact ownership and regeneration behavior

The artifact is configured to **merge with generated content while preserving configured protected regions**.

Merge mode means regeneration is expected to revisit the same file. Do not place durable manual content outside the named protected regions if you expect it to survive future generation.

Protected regions recorded for this artifact: `openapi-extension`. These regions are the explicit human-ownership boundary.

Template parameters: `apiObj`, `routeSeq`, `emitCtx`.

### How to troubleshoot or repair it

First evaluate the guard: `emitCtx().get("enableOpenApi") = true and apiObj.routes.notEmpty()`. If it is false, check the corresponding generation flag or PSM relationship before treating the missing artifact as a failure. If it should be true, verify the source object, its path helper, and the template parameters. Then inspect the generation report and artifact trace for this rule.

### Authoritative sources

The scheduling, guard, target path, merge policy, and artifact record are defined at `mde/generation/awspsm-to-artifacts/awspsm2artifacts.egx:225`. The rendered content is defined by `contracts/openapi.egl` under `mde/generation/awspsm-to-artifacts/templates/`.

---

## `WorkflowToAsl`

**Source context:** `stateMachineObj` — `AWSPSMWORKFLOW!StepFunctionStateMachine`  
**Template:** `contracts/asl.egl`  
**Target path expression:** `stateMachineObj.aslPath()`  
**EGX source:** `mde/generation/awspsm-to-artifacts/awspsm2artifacts.egx:243`

### Why this generation rule exists

ASL is the provider-native executable form of the modeled state machine. This rule emits it only when ASL generation is enabled and the PSM contains a definition, and it overwrites the generated document because protected-region editing is not a safe way to patch executable state-machine JSON.

### When it runs

The rule is conditional. A false guard is a deliberate omission boundary, not an empty generated file:

```egx
guard : emitCtx().get("enableAsl") = true and stateMachineObj.aslDocument.isDefined()
```

### Artifact ownership and regeneration behavior

The artifact is configured to **replace the generated artifact completely**.

Because this rule overwrites rather than merges, the target is a generated projection. Manual edits to it are not a durable customization mechanism.

Template parameters: `stateMachineObj`, `aslDocObj`, `emitCtx`.

### How to troubleshoot or repair it

First evaluate the guard: `emitCtx().get("enableAsl") = true and stateMachineObj.aslDocument.isDefined()`. If it is false, check the corresponding generation flag or PSM relationship before treating the missing artifact as a failure. If it should be true, verify the source object, its path helper, and the template parameters. Then inspect the generation report and artifact trace for this rule.

### Authoritative sources

The scheduling, guard, target path, merge policy, and artifact record are defined at `mde/generation/awspsm-to-artifacts/awspsm2artifacts.egx:243`. The rendered content is defined by `contracts/asl.egl` under `mde/generation/awspsm-to-artifacts/templates/`.

---

## `LambdaToLocalSchema`

**Source context:** `awsFn` — `AWSPSMCOMPUTE!AwsLambdaFunction`  
**Template:** `contracts/json-schema.egl`  
**Target path expression:** `awsFn.localSchemaPath(emitCtx())`  
**EGX source:** `mde/generation/awspsm-to-artifacts/awspsm2artifacts.egx:285`

### Why this generation rule exists

The local JSON Schema gives a handler and its tests a concrete representation of the modeled input boundary. It is overwritten because it is a pure projection of the PSM contract, not a manual implementation file.

### When it runs

There is no explicit guard; the rule is eligible during every generation run. Its template may still render an empty or minimal artifact when the model contains no corresponding optional elements.

### Artifact ownership and regeneration behavior

The artifact is configured to **replace the generated artifact completely**.

Because this rule overwrites rather than merges, the target is a generated projection. Manual edits to it are not a durable customization mechanism.

Template parameters: `schemaOwnerObj`, `schemaKindText`, `emitCtx`.

### How to troubleshoot or repair it

Verify that the AWS PSM root and the source objects expected by the template exist, then inspect the generated artifact record and the template parameters. If the file is incomplete, distinguish a model omission from a protected-region customization before changing the EGL template.

### Authoritative sources

The scheduling, guard, target path, merge policy, and artifact record are defined at `mde/generation/awspsm-to-artifacts/awspsm2artifacts.egx:285`. The rendered content is defined by `contracts/json-schema.egl` under `mde/generation/awspsm-to-artifacts/templates/`.

---

## `EventBridgeRuleToFixture`

**Source context:** `bridgeRuleObj` — `AWSPSMEVENTS!EventBridgeRule`  
**Template:** `contracts/sample-event.egl`  
**Target path expression:** `bridgeRuleObj.eventFixturePath()`  
**EGX source:** `mde/generation/awspsm-to-artifacts/awspsm2artifacts.egx:353`

### Why this generation rule exists

An EventBridge fixture turns a modeled event pattern or schedule into a repeatable test input. The guard prevents an empty rule from generating a fixture that suggests a delivery contract where the PSM contains none.

### When it runs

The rule is conditional. A false guard is a deliberate omission boundary, not an empty generated file:

```egx
guard : bridgeRuleObj.eventPattern.isDefined() or bridgeRuleObj.eventPatternJson.isPresent() or bridgeRuleObj.scheduleExpression.isPresent()
```

### Artifact ownership and regeneration behavior

The artifact is configured to **replace the generated artifact completely**.

Because this rule overwrites rather than merges, the target is a generated projection. Manual edits to it are not a durable customization mechanism.

Template parameters: `eventOwnerObj`, `eventKindText`, `emitCtx`.

### How to troubleshoot or repair it

First evaluate the guard: `bridgeRuleObj.eventPattern.isDefined() or bridgeRuleObj.eventPatternJson.isPresent() or bridgeRuleObj.scheduleExpression.isPresent()`. If it is false, check the corresponding generation flag or PSM relationship before treating the missing artifact as a failure. If it should be true, verify the source object, its path helper, and the template parameters. Then inspect the generation report and artifact trace for this rule.

### Authoritative sources

The scheduling, guard, target path, merge policy, and artifact record are defined at `mde/generation/awspsm-to-artifacts/awspsm2artifacts.egx:353`. The rendered content is defined by `contracts/sample-event.egl` under `mde/generation/awspsm-to-artifacts/templates/`.

---

## `SqsQueueToFixture`

**Source context:** `queueObj` — `AWSPSMMESSAGING!SqsQueue`  
**Template:** `contracts/sample-event.egl`  
**Target path expression:** `queueObj.queueFixturePath()`  
**EGX source:** `mde/generation/awspsm-to-artifacts/awspsm2artifacts.egx:371`

### Why this generation rule exists

An SQS fixture gives tests a representative message envelope for the modeled queue. It is generated from queue identity and contract context, while realistic business payloads remain a deliberate fixture decision rather than fabricated data.

### When it runs

There is no explicit guard; the rule is eligible during every generation run. Its template may still render an empty or minimal artifact when the model contains no corresponding optional elements.

### Artifact ownership and regeneration behavior

The artifact is configured to **replace the generated artifact completely**.

Because this rule overwrites rather than merges, the target is a generated projection. Manual edits to it are not a durable customization mechanism.

Template parameters: `eventOwnerObj`, `eventKindText`, `emitCtx`.

### How to troubleshoot or repair it

Verify that the AWS PSM root and the source objects expected by the template exist, then inspect the generated artifact record and the template parameters. If the file is incomplete, distinguish a model omission from a protected-region customization before changing the EGL template.

### Authoritative sources

The scheduling, guard, target path, merge policy, and artifact record are defined at `mde/generation/awspsm-to-artifacts/awspsm2artifacts.egx:371`. The rendered content is defined by `contracts/sample-event.egl` under `mde/generation/awspsm-to-artifacts/templates/`.

---

## `SnsTopicToFixture`

**Source context:** `topicObj` — `AWSPSMMESSAGING!SnsTopic`  
**Template:** `contracts/sample-event.egl`  
**Target path expression:** `topicObj.topicFixturePath()`  
**EGX source:** `mde/generation/awspsm-to-artifacts/awspsm2artifacts.egx:388`

### Why this generation rule exists

An SNS fixture preserves the topic's message shape for event and integration testing. The generated envelope is useful scaffolding; consumer-specific assertions still belong in protected test regions.

### When it runs

There is no explicit guard; the rule is eligible during every generation run. Its template may still render an empty or minimal artifact when the model contains no corresponding optional elements.

### Artifact ownership and regeneration behavior

The artifact is configured to **replace the generated artifact completely**.

Because this rule overwrites rather than merges, the target is a generated projection. Manual edits to it are not a durable customization mechanism.

Template parameters: `eventOwnerObj`, `eventKindText`, `emitCtx`.

### How to troubleshoot or repair it

Verify that the AWS PSM root and the source objects expected by the template exist, then inspect the generated artifact record and the template parameters. If the file is incomplete, distinguish a model omission from a protected-region customization before changing the EGL template.

### Authoritative sources

The scheduling, guard, target path, merge policy, and artifact record are defined at `mde/generation/awspsm-to-artifacts/awspsm2artifacts.egx:388`. The rendered content is defined by `contracts/sample-event.egl` under `mde/generation/awspsm-to-artifacts/templates/`.

---

## `ApiSchemaCatalog`

**Source context:** ``—`shared AWS PSM generation context` 
**Template:**`contracts/catalog-schema.egl` 
**Target path expression:**`"schemas/api/generated-api.schema.json"` 
**EGX source:**`mde/generation/awspsm-to-artifacts/awspsm2artifacts.egx:405`

### Why this generation rule exists

The API schema catalog collects generated request/response schemas into one discoverable artifact. It helps clients and reviewers find the contract surface without traversing every function directory.

### When it runs

There is no explicit guard; the rule is eligible during every generation run. Its template may still render an empty or minimal artifact when the model contains no corresponding optional elements.

### Artifact ownership and regeneration behavior

The artifact is configured to **replace the generated artifact completely**.

Because this rule overwrites rather than merges, the target is a generated projection. Manual edits to it are not a durable customization mechanism.

Template parameters: `schemaIdText`, `schemaTitleText`, `emitCtx`.

Artifact record emitted by the EGX rule:

- `schemas/api/generated-api.schema.json / JSON_SCHEMA / AWSPSM2ART_Api_Schema_Catalog / OVERWRITE_GENERATED`

### How to troubleshoot or repair it

Verify that the AWS PSM root and the source objects expected by the template exist, then inspect the generated artifact record and the template parameters. If the file is incomplete, distinguish a model omission from a protected-region customization before changing the EGL template.

### Authoritative sources

The scheduling, guard, target path, merge policy, and artifact record are defined at `mde/generation/awspsm-to-artifacts/awspsm2artifacts.egx:405`. The rendered content is defined by `contracts/catalog-schema.egl` under `mde/generation/awspsm-to-artifacts/templates/`.

---

## `EventSchemaCatalog`

**Source context:** ``—`shared AWS PSM generation context` 
**Template:**`contracts/catalog-schema.egl` 
**Target path expression:**`"schemas/events/generated-event.schema.json"` 
**EGX source:**`mde/generation/awspsm-to-artifacts/awspsm2artifacts.egx:416`

### Why this generation rule exists

The event catalog is the searchable index of event payload contracts. It supports consumer discovery and compatibility review, which is especially important when event types are published independently of the function that emits them.

### When it runs

There is no explicit guard; the rule is eligible during every generation run. Its template may still render an empty or minimal artifact when the model contains no corresponding optional elements.

### Artifact ownership and regeneration behavior

The artifact is configured to **replace the generated artifact completely**.

Because this rule overwrites rather than merges, the target is a generated projection. Manual edits to it are not a durable customization mechanism.

Template parameters: `schemaIdText`, `schemaTitleText`, `emitCtx`.

Artifact record emitted by the EGX rule:

- `schemas/events/generated-event.schema.json / JSON_SCHEMA / AWSPSM2ART_Event_Schema_Catalog / OVERWRITE_GENERATED`

### How to troubleshoot or repair it

Verify that the AWS PSM root and the source objects expected by the template exist, then inspect the generated artifact record and the template parameters. If the file is incomplete, distinguish a model omission from a protected-region customization before changing the EGL template.

### Authoritative sources

The scheduling, guard, target path, merge policy, and artifact record are defined at `mde/generation/awspsm-to-artifacts/awspsm2artifacts.egx:416`. The rendered content is defined by `contracts/catalog-schema.egl` under `mde/generation/awspsm-to-artifacts/templates/`.

---

## `MessageSchemaCatalog`

**Source context:** ``—`shared AWS PSM generation context` 
**Template:**`contracts/catalog-schema.egl` 
**Target path expression:**`"schemas/messages/generated-message.schema.json"` 
**EGX source:**`mde/generation/awspsm-to-artifacts/awspsm2artifacts.egx:427`

### Why this generation rule exists

Messaging contracts are gathered separately because queue/topic consumers reason about delivery envelopes and retry behavior differently from HTTP clients or domain events.

### When it runs

There is no explicit guard; the rule is eligible during every generation run. Its template may still render an empty or minimal artifact when the model contains no corresponding optional elements.

### Artifact ownership and regeneration behavior

The artifact is configured to **replace the generated artifact completely**.

Because this rule overwrites rather than merges, the target is a generated projection. Manual edits to it are not a durable customization mechanism.

Template parameters: `schemaIdText`, `schemaTitleText`, `emitCtx`.

Artifact record emitted by the EGX rule:

- `schemas/messages/generated-message.schema.json / JSON_SCHEMA / AWSPSM2ART_Message_Schema_Catalog / OVERWRITE_GENERATED`

### How to troubleshoot or repair it

Verify that the AWS PSM root and the source objects expected by the template exist, then inspect the generated artifact record and the template parameters. If the file is incomplete, distinguish a model omission from a protected-region customization before changing the EGL template.

### Authoritative sources

The scheduling, guard, target path, merge policy, and artifact record are defined at `mde/generation/awspsm-to-artifacts/awspsm2artifacts.egx:427`. The rendered content is defined by `contracts/catalog-schema.egl` under `mde/generation/awspsm-to-artifacts/templates/`.

---

## `ErrorSchemaCatalog`

**Source context:** ``—`shared AWS PSM generation context` 
**Template:**`contracts/catalog-schema.egl` 
**Target path expression:**`"schemas/errors/generated-error.schema.json"` 
**EGX source:**`mde/generation/awspsm-to-artifacts/awspsm2artifacts.egx:438`

### Why this generation rule exists

A central error catalog makes business rejection shapes discoverable and consistent across routes, functions, and tests. It prevents useful error semantics from remaining scattered across individual generated schemas.

### When it runs

There is no explicit guard; the rule is eligible during every generation run. Its template may still render an empty or minimal artifact when the model contains no corresponding optional elements.

### Artifact ownership and regeneration behavior

The artifact is configured to **replace the generated artifact completely**.

Because this rule overwrites rather than merges, the target is a generated projection. Manual edits to it are not a durable customization mechanism.

Template parameters: `schemaIdText`, `schemaTitleText`, `emitCtx`.

Artifact record emitted by the EGX rule:

- `schemas/errors/generated-error.schema.json / JSON_SCHEMA / AWSPSM2ART_Error_Schema_Catalog / OVERWRITE_GENERATED`

### How to troubleshoot or repair it

Verify that the AWS PSM root and the source objects expected by the template exist, then inspect the generated artifact record and the template parameters. If the file is incomplete, distinguish a model omission from a protected-region customization before changing the EGL template.

### Authoritative sources

The scheduling, guard, target path, merge policy, and artifact record are defined at `mde/generation/awspsm-to-artifacts/awspsm2artifacts.egx:438`. The rendered content is defined by `contracts/catalog-schema.egl` under `mde/generation/awspsm-to-artifacts/templates/`.

---

## `DataSchemaCatalog`

**Source context:** ``—`shared AWS PSM generation context` 
**Template:**`contracts/catalog-schema.egl` 
**Target path expression:**`"schemas/data/generated-data.schema.json"` 
**EGX source:**`mde/generation/awspsm-to-artifacts/awspsm2artifacts.egx:449`

### Why this generation rule exists

The data catalog exposes storage-facing schemas as a reviewable inventory. It helps compare modeled data shape with table/object definitions without treating storage serialization as the public API contract.

### When it runs

There is no explicit guard; the rule is eligible during every generation run. Its template may still render an empty or minimal artifact when the model contains no corresponding optional elements.

### Artifact ownership and regeneration behavior

The artifact is configured to **replace the generated artifact completely**.

Because this rule overwrites rather than merges, the target is a generated projection. Manual edits to it are not a durable customization mechanism.

Template parameters: `schemaIdText`, `schemaTitleText`, `emitCtx`.

Artifact record emitted by the EGX rule:

- `schemas/data/generated-data.schema.json / JSON_SCHEMA / AWSPSM2ART_Data_Schema_Catalog / OVERWRITE_GENERATED`

### How to troubleshoot or repair it

Verify that the AWS PSM root and the source objects expected by the template exist, then inspect the generated artifact record and the template parameters. If the file is incomplete, distinguish a model omission from a protected-region customization before changing the EGL template.

### Authoritative sources

The scheduling, guard, target path, merge policy, and artifact record are defined at `mde/generation/awspsm-to-artifacts/awspsm2artifacts.egx:449`. The rendered content is defined by `contracts/catalog-schema.egl` under `mde/generation/awspsm-to-artifacts/templates/`.

---

## Useful resources

- [OpenAPI Specification](https://spec.openapis.org/oas/latest.html)
- [JSON Schema specification](https://json-schema.org/specification)
- [AWS Step Functions Amazon States Language](https://docs.aws.amazon.com/step-functions/latest/dg/concepts-amazon-states-language.html)
