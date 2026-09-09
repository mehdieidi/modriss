# Code generation — Lambda runtime

Lambda templates create the Go handler and shared runtime packages used by generated functions. They provide a usable scaffold and common cross-cutting behavior, but protected regions deliberately reserve business logic, custom types, imports, assertions, and other decisions that generation cannot safely own forever.

The category contains 10 EGX generation rules and 10 EGL templates.

## Template inventory

| Template                                      | What it contains                               |      Size |
| --------------------------------------------- | ---------------------------------------------- | --------: |
| `templates/lambda/go-handler.egl`             | Generates the go handler artifact.             | 128 lines |
| `templates/lambda/shared-config.egl`          | Generates the shared config artifact.          |  19 lines |
| `templates/lambda/shared-data-access.egl`     | Generates the shared data access artifact.     |  10 lines |
| `templates/lambda/shared-errors.egl`          | Generates the shared errors artifact.          |  41 lines |
| `templates/lambda/shared-event-publisher.egl` | Generates the shared event publisher artifact. |  17 lines |
| `templates/lambda/shared-idempotency.egl`     | Generates the shared idempotency artifact.     |  38 lines |
| `templates/lambda/shared-logger.egl`          | Generates the shared logger artifact.          |  37 lines |
| `templates/lambda/shared-metrics.egl`         | Generates the shared metrics artifact.         |  17 lines |
| `templates/lambda/shared-tracer.egl`          | Generates the shared tracer artifact.          |  13 lines |
| `templates/lambda/shared-validation.egl`      | Generates the shared validation artifact.      |  28 lines |

---

## `LambdaToHandler`

**Source context:** `awsFn` — `AWSPSMCOMPUTE!AwsLambdaFunction`  
**Template:** `lambda/go-handler.egl`  
**Target path expression:** `awsFn.handlerPath(emitCtx())`  
**EGX source:** `mde/generation/awspsm-to-artifacts/awspsm2artifacts.egx:261`

### Why this generation rule exists

A Lambda code model needs a compilable handler scaffold, but the model cannot author the business algorithm. Merge mode plus named protected regions separates generated wiring, validation, logging, and error structure from business logic and custom code that must survive regeneration.

### When it runs

The rule is conditional. A false guard is a deliberate omission boundary, not an empty generated file:

```egx
guard : awsFn.code.isDefined()
```

### Artifact ownership and regeneration behavior

The artifact is configured to **merge with generated content while preserving configured protected regions**.

Merge mode means regeneration is expected to revisit the same file. Do not place durable manual content outside the named protected regions if you expect it to survive future generation.

Protected regions recorded for this artifact: `custom-imports`, `custom-types`, `custom-validation`, `business-logic`, `custom-error-mapping`. These regions are the explicit human-ownership boundary.

Template parameters: `awsFn`, `emitCtx`.

### How to troubleshoot or repair it

First evaluate the guard: `awsFn.code.isDefined()`. If it is false, check the corresponding generation flag or PSM relationship before treating the missing artifact as a failure. If it should be true, verify the source object, its path helper, and the template parameters. Then inspect the generation report and artifact trace for this rule.

### Authoritative sources

The scheduling, guard, target path, merge policy, and artifact record are defined at `mde/generation/awspsm-to-artifacts/awspsm2artifacts.egx:261`. The rendered content is defined by `lambda/go-handler.egl` under `mde/generation/awspsm-to-artifacts/templates/`.

---

## `SharedRuntimeLogger`

**Source context:** ``—`shared AWS PSM generation context` 
**Template:**`lambda/shared-logger.egl` 
**Target path expression:**`"src/shared/logger." + emitCtx().sourceExtension()` 
**EGX source:**`mde/generation/awspsm-to-artifacts/awspsm2artifacts.egx:486`

### Why this generation rule exists

The shared logger gives generated handlers a consistent structured logging interface. Centralizing it prevents every function template from making a different decision about fields, levels, and correlation context.

### When it runs

There is no explicit guard; the rule is eligible during every generation run. Its template may still render an empty or minimal artifact when the model contains no corresponding optional elements.

### Artifact ownership and regeneration behavior

The artifact is configured to **replace the generated artifact completely**.

Because this rule overwrites rather than merges, the target is a generated projection. Manual edits to it are not a durable customization mechanism.

Template parameters: `awsRoot`, `emitCtx`.

### How to troubleshoot or repair it

Verify that the AWS PSM root and the source objects expected by the template exist, then inspect the generated artifact record and the template parameters. If the file is incomplete, distinguish a model omission from a protected-region customization before changing the EGL template.

### Authoritative sources

The scheduling, guard, target path, merge policy, and artifact record are defined at `mde/generation/awspsm-to-artifacts/awspsm2artifacts.egx:486`. The rendered content is defined by `lambda/shared-logger.egl` under `mde/generation/awspsm-to-artifacts/templates/`.

---

## `SharedRuntimeTracer`

**Source context:** ``—`shared AWS PSM generation context` 
**Template:**`lambda/shared-tracer.egl` 
**Target path expression:**`"src/shared/tracer." + emitCtx().sourceExtension()` 
**EGX source:**`mde/generation/awspsm-to-artifacts/awspsm2artifacts.egx:497`

### Why this generation rule exists

The tracer wrapper provides one runtime convention for propagating trace context through generated functions and calls. It keeps observability behavior aligned across handlers instead of scattering SDK details through business code.

### When it runs

There is no explicit guard; the rule is eligible during every generation run. Its template may still render an empty or minimal artifact when the model contains no corresponding optional elements.

### Artifact ownership and regeneration behavior

The artifact is configured to **replace the generated artifact completely**.

Because this rule overwrites rather than merges, the target is a generated projection. Manual edits to it are not a durable customization mechanism.

Template parameters: `awsRoot`, `emitCtx`.

### How to troubleshoot or repair it

Verify that the AWS PSM root and the source objects expected by the template exist, then inspect the generated artifact record and the template parameters. If the file is incomplete, distinguish a model omission from a protected-region customization before changing the EGL template.

### Authoritative sources

The scheduling, guard, target path, merge policy, and artifact record are defined at `mde/generation/awspsm-to-artifacts/awspsm2artifacts.egx:497`. The rendered content is defined by `lambda/shared-tracer.egl` under `mde/generation/awspsm-to-artifacts/templates/`.

---

## `SharedRuntimeMetrics`

**Source context:** ``—`shared AWS PSM generation context` 
**Template:**`lambda/shared-metrics.egl` 
**Target path expression:**`"src/shared/metrics." + emitCtx().sourceExtension()` 
**EGX source:**`mde/generation/awspsm-to-artifacts/awspsm2artifacts.egx:508`

### Why this generation rule exists

The metrics helper standardizes counters, timings, and dimensions emitted by generated code. A shared implementation is important because operational dashboards depend on consistent names and labels across independently generated functions.

### When it runs

There is no explicit guard; the rule is eligible during every generation run. Its template may still render an empty or minimal artifact when the model contains no corresponding optional elements.

### Artifact ownership and regeneration behavior

The artifact is configured to **replace the generated artifact completely**.

Because this rule overwrites rather than merges, the target is a generated projection. Manual edits to it are not a durable customization mechanism.

Template parameters: `awsRoot`, `emitCtx`.

### How to troubleshoot or repair it

Verify that the AWS PSM root and the source objects expected by the template exist, then inspect the generated artifact record and the template parameters. If the file is incomplete, distinguish a model omission from a protected-region customization before changing the EGL template.

### Authoritative sources

The scheduling, guard, target path, merge policy, and artifact record are defined at `mde/generation/awspsm-to-artifacts/awspsm2artifacts.egx:508`. The rendered content is defined by `lambda/shared-metrics.egl` under `mde/generation/awspsm-to-artifacts/templates/`.

---

## `SharedRuntimeErrors`

**Source context:** ``—`shared AWS PSM generation context` 
**Template:**`lambda/shared-errors.egl` 
**Target path expression:**`"src/shared/errors." + emitCtx().sourceExtension()` 
**EGX source:**`mde/generation/awspsm-to-artifacts/awspsm2artifacts.egx:519`

### Why this generation rule exists

Generated handlers need a common error vocabulary and serialization path. This template separates business errors from internal failures so API responses, logs, and tests can agree on what is safe to expose.

### When it runs

There is no explicit guard; the rule is eligible during every generation run. Its template may still render an empty or minimal artifact when the model contains no corresponding optional elements.

### Artifact ownership and regeneration behavior

The artifact is configured to **replace the generated artifact completely**.

Because this rule overwrites rather than merges, the target is a generated projection. Manual edits to it are not a durable customization mechanism.

Template parameters: `awsRoot`, `emitCtx`.

### How to troubleshoot or repair it

Verify that the AWS PSM root and the source objects expected by the template exist, then inspect the generated artifact record and the template parameters. If the file is incomplete, distinguish a model omission from a protected-region customization before changing the EGL template.

### Authoritative sources

The scheduling, guard, target path, merge policy, and artifact record are defined at `mde/generation/awspsm-to-artifacts/awspsm2artifacts.egx:519`. The rendered content is defined by `lambda/shared-errors.egl` under `mde/generation/awspsm-to-artifacts/templates/`.

---

## `SharedRuntimeValidation`

**Source context:** ``—`shared AWS PSM generation context` 
**Template:**`lambda/shared-validation.egl` 
**Target path expression:**`"src/shared/validation." + emitCtx().sourceExtension()` 
**EGX source:**`mde/generation/awspsm-to-artifacts/awspsm2artifacts.egx:530`

### Why this generation rule exists

The validation helper is the runtime counterpart of generated contracts. It lets handlers enforce model-derived input/output expectations without duplicating schema plumbing in every function.

### When it runs

There is no explicit guard; the rule is eligible during every generation run. Its template may still render an empty or minimal artifact when the model contains no corresponding optional elements.

### Artifact ownership and regeneration behavior

The artifact is configured to **replace the generated artifact completely**.

Because this rule overwrites rather than merges, the target is a generated projection. Manual edits to it are not a durable customization mechanism.

Template parameters: `awsRoot`, `emitCtx`.

### How to troubleshoot or repair it

Verify that the AWS PSM root and the source objects expected by the template exist, then inspect the generated artifact record and the template parameters. If the file is incomplete, distinguish a model omission from a protected-region customization before changing the EGL template.

### Authoritative sources

The scheduling, guard, target path, merge policy, and artifact record are defined at `mde/generation/awspsm-to-artifacts/awspsm2artifacts.egx:530`. The rendered content is defined by `lambda/shared-validation.egl` under `mde/generation/awspsm-to-artifacts/templates/`.

---

## `SharedRuntimeConfig`

**Source context:** ``—`shared AWS PSM generation context` 
**Template:**`lambda/shared-config.egl` 
**Target path expression:**`"src/shared/config." + emitCtx().sourceExtension()` 
**EGX source:**`mde/generation/awspsm-to-artifacts/awspsm2artifacts.egx:541`

### Why this generation rule exists

Configuration access belongs in one generated module so stage/environment lookup, defaults, and missing-value behavior are consistent. It also keeps configuration loading separate from secret handling and business logic.

### When it runs

There is no explicit guard; the rule is eligible during every generation run. Its template may still render an empty or minimal artifact when the model contains no corresponding optional elements.

### Artifact ownership and regeneration behavior

The artifact is configured to **replace the generated artifact completely**.

Because this rule overwrites rather than merges, the target is a generated projection. Manual edits to it are not a durable customization mechanism.

Template parameters: `awsRoot`, `emitCtx`.

### How to troubleshoot or repair it

Verify that the AWS PSM root and the source objects expected by the template exist, then inspect the generated artifact record and the template parameters. If the file is incomplete, distinguish a model omission from a protected-region customization before changing the EGL template.

### Authoritative sources

The scheduling, guard, target path, merge policy, and artifact record are defined at `mde/generation/awspsm-to-artifacts/awspsm2artifacts.egx:541`. The rendered content is defined by `lambda/shared-config.egl` under `mde/generation/awspsm-to-artifacts/templates/`.

---

## `SharedRuntimeIdempotency`

**Source context:** ``—`shared AWS PSM generation context` 
**Template:**`lambda/shared-idempotency.egl` 
**Target path expression:**`"src/shared/idempotency." + emitCtx().sourceExtension()` 
**EGX source:**`mde/generation/awspsm-to-artifacts/awspsm2artifacts.egx:552`

### Why this generation rule exists

Idempotency is a cross-cutting runtime concern for retried serverless work. The shared helper gives generated handlers a common way to recognize duplicate intent while preserving a protected region for the project's chosen persistence or policy details.

### When it runs

There is no explicit guard; the rule is eligible during every generation run. Its template may still render an empty or minimal artifact when the model contains no corresponding optional elements.

### Artifact ownership and regeneration behavior

The artifact is configured to **merge with generated content while preserving configured protected regions**.

Merge mode means regeneration is expected to revisit the same file. Do not place durable manual content outside the named protected regions if you expect it to survive future generation.

Protected regions recorded for this artifact: `idempotency-key-derivation`. These regions are the explicit human-ownership boundary.

Template parameters: `awsRoot`, `emitCtx`.

### How to troubleshoot or repair it

Verify that the AWS PSM root and the source objects expected by the template exist, then inspect the generated artifact record and the template parameters. If the file is incomplete, distinguish a model omission from a protected-region customization before changing the EGL template.

### Authoritative sources

The scheduling, guard, target path, merge policy, and artifact record are defined at `mde/generation/awspsm-to-artifacts/awspsm2artifacts.egx:552`. The rendered content is defined by `lambda/shared-idempotency.egl` under `mde/generation/awspsm-to-artifacts/templates/`.

---

## `SharedRuntimeEventPublisher`

**Source context:** ``—`shared AWS PSM generation context` 
**Template:**`lambda/shared-event-publisher.egl` 
**Target path expression:**`"src/shared/event-publisher." + emitCtx().sourceExtension()` 
**EGX source:**`mde/generation/awspsm-to-artifacts/awspsm2artifacts.egx:563`

### Why this generation rule exists

Event publication needs consistent envelopes, correlation, serialization, and failure reporting. A shared publisher keeps those semantics aligned with the event types and schemas generated elsewhere.

### When it runs

There is no explicit guard; the rule is eligible during every generation run. Its template may still render an empty or minimal artifact when the model contains no corresponding optional elements.

### Artifact ownership and regeneration behavior

The artifact is configured to **replace the generated artifact completely**.

Because this rule overwrites rather than merges, the target is a generated projection. Manual edits to it are not a durable customization mechanism.

Template parameters: `awsRoot`, `emitCtx`.

### How to troubleshoot or repair it

Verify that the AWS PSM root and the source objects expected by the template exist, then inspect the generated artifact record and the template parameters. If the file is incomplete, distinguish a model omission from a protected-region customization before changing the EGL template.

### Authoritative sources

The scheduling, guard, target path, merge policy, and artifact record are defined at `mde/generation/awspsm-to-artifacts/awspsm2artifacts.egx:563`. The rendered content is defined by `lambda/shared-event-publisher.egl` under `mde/generation/awspsm-to-artifacts/templates/`.

---

## `SharedRuntimeDataAccess`

**Source context:** ``—`shared AWS PSM generation context` 
**Template:**`lambda/shared-data-access.egl` 
**Target path expression:**`"src/shared/data-access." + emitCtx().sourceExtension()` 
**EGX source:**`mde/generation/awspsm-to-artifacts/awspsm2artifacts.egx:574`

### Why this generation rule exists

Data access is centralized so generated functions use common connection, serialization, error, and observability behavior. This is the seam where provider-bound stores become callable without embedding storage code in every handler scaffold.

### When it runs

There is no explicit guard; the rule is eligible during every generation run. Its template may still render an empty or minimal artifact when the model contains no corresponding optional elements.

### Artifact ownership and regeneration behavior

The artifact is configured to **replace the generated artifact completely**.

Because this rule overwrites rather than merges, the target is a generated projection. Manual edits to it are not a durable customization mechanism.

Template parameters: `awsRoot`, `emitCtx`.

### How to troubleshoot or repair it

Verify that the AWS PSM root and the source objects expected by the template exist, then inspect the generated artifact record and the template parameters. If the file is incomplete, distinguish a model omission from a protected-region customization before changing the EGL template.

### Authoritative sources

The scheduling, guard, target path, merge policy, and artifact record are defined at `mde/generation/awspsm-to-artifacts/awspsm2artifacts.egx:574`. The rendered content is defined by `lambda/shared-data-access.egl` under `mde/generation/awspsm-to-artifacts/templates/`.

---

## Useful resources

- [AWS Lambda Go handler programming model](https://docs.aws.amazon.com/lambda/latest/dg/golang-handler.html)
- [AWS Lambda best practices](https://docs.aws.amazon.com/lambda/latest/dg/best-practices.html)
