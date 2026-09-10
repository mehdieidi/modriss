# Code generation: Tests and fixtures

Test templates turn model contracts and AWS resources into executable test scaffolding. They cover unit, integration, workflow, event, contract, security, and end-to-end concerns; protected assertion and fixture regions are the point where the model stops being able to invent application-specific expectations.

The category contains 8 EGX generation rules and 8 EGL templates.

## Template inventory

| Template                               | What it contains                         |     Size |
| -------------------------------------- | ---------------------------------------- | -------: |
| `templates/tests/contract-test.egl`    | Generates the contract test artifact.    | 94 lines |
| `templates/tests/e2e-test.egl`         | Generates the e2e test artifact.         | 11 lines |
| `templates/tests/event-test.egl`       | Generates the event test artifact.       | 40 lines |
| `templates/tests/fixtures-readme.egl`  | Generates the fixtures readme artifact.  |  5 lines |
| `templates/tests/integration-test.egl` | Generates the integration test artifact. | 65 lines |
| `templates/tests/security-test.egl`    | Generates the security test artifact.    | 32 lines |
| `templates/tests/unit-test-go.egl`     | Generates the unit test go artifact.     | 28 lines |
| `templates/tests/workflow-test.egl`    | Generates the workflow test artifact.    | 26 lines |

---

## `LambdaToUnitTest`

**Source context:** `awsFn`, `AWSPSMCOMPUTE!AwsLambdaFunction`  
**Template:** `tests/unit-test-go.egl`  
**Target path expression:** `awsFn.unitTestPath(emitCtx())`  
**EGX source:** `mde/generation/awspsm-to-artifacts/awspsm2artifacts.egx:302`

### Why this generation rule exists

Each Lambda receives a unit-test scaffold when tests are enabled. The generated test establishes model-derived setup and a stable test name while protected fixtures, assertions, and edge cases remain the modeler's responsibility.

### When it runs

The rule is conditional. A false guard is a deliberate omission boundary, not an empty generated file:

```egx
guard : emitCtx().get("enableTests") = true
```

### Artifact ownership and regeneration behavior

The artifact is configured to **merge with generated content while preserving configured protected regions**.

Merge mode means regeneration is expected to revisit the same file. Do not place durable manual content outside the named protected regions if you expect it to survive future generation.

Protected regions recorded for this artifact: `unit-custom-fixtures`, `unit-custom-assertions`, `unit-edge-cases`. These regions are the explicit human-ownership boundary.

Template parameters: `awsFn`, `emitCtx`.

### How to troubleshoot or repair it

First evaluate the guard: `emitCtx().get("enableTests") = true`. If it is false, check the corresponding generation flag or PSM relationship before treating the missing artifact as a failure. If it should be true, verify the source object, its path helper, and the template parameters. Then inspect the generation report and artifact trace for this rule.

### Authoritative sources

The scheduling, guard, target path, merge policy, and artifact record are defined at `mde/generation/awspsm-to-artifacts/awspsm2artifacts.egx:302`. The rendered content is defined by `tests/unit-test-go.egl` under `mde/generation/awspsm-to-artifacts/templates/`.

---

## `LambdaToIntegrationTest`

**Source context:** `awsFn`, `AWSPSMCOMPUTE!AwsLambdaFunction`  
**Template:** `tests/integration-test.egl`  
**Target path expression:** `awsFn.integrationTestPath(emitCtx())`  
**EGX source:** `mde/generation/awspsm-to-artifacts/awspsm2artifacts.egx:319`

### Why this generation rule exists

Integration tests need a generated starting point that knows the Lambda's deployment identity and contract. The protected setup, cleanup, and assertions are intentionally human-owned because external systems and business expectations cannot be inferred safely.

### When it runs

The rule is conditional. A false guard is a deliberate omission boundary, not an empty generated file:

```egx
guard : emitCtx().get("enableTests") = true
```

### Artifact ownership and regeneration behavior

The artifact is configured to **merge with generated content while preserving configured protected regions**.

Merge mode means regeneration is expected to revisit the same file. Do not place durable manual content outside the named protected regions if you expect it to survive future generation.

Protected regions recorded for this artifact: `integration-external-setup`, `integration-cleanup`, `integration-assertions`. These regions are the explicit human-ownership boundary.

Template parameters: `awsFn`, `emitCtx`.

### How to troubleshoot or repair it

First evaluate the guard: `emitCtx().get("enableTests") = true`. If it is false, check the corresponding generation flag or PSM relationship before treating the missing artifact as a failure. If it should be true, verify the source object, its path helper, and the template parameters. Then inspect the generation report and artifact trace for this rule.

### Authoritative sources

The scheduling, guard, target path, merge policy, and artifact record are defined at `mde/generation/awspsm-to-artifacts/awspsm2artifacts.egx:319`. The rendered content is defined by `tests/integration-test.egl` under `mde/generation/awspsm-to-artifacts/templates/`.

---

## `WorkflowToTest`

**Source context:** `stateMachineObj`, `AWSPSMWORKFLOW!StepFunctionStateMachine`  
**Template:** `tests/workflow-test.egl`  
**Target path expression:** `stateMachineObj.workflowTestPath(emitCtx())`  
**EGX source:** `mde/generation/awspsm-to-artifacts/awspsm2artifacts.egx:336`

### Why this generation rule exists

A workflow test checks that the generated ASL is readable, structurally complete, and starts from the modeled entry state. It is conditional on both test generation and an ASL document so the project does not claim to test a workflow that was never emitted.

### When it runs

The rule is conditional. A false guard is a deliberate omission boundary, not an empty generated file:

```egx
guard : emitCtx().get("enableTests") = true and stateMachineObj.aslDocument.isDefined()
```

### Artifact ownership and regeneration behavior

The artifact is configured to **merge with generated content while preserving configured protected regions**.

Merge mode means regeneration is expected to revisit the same file. Do not place durable manual content outside the named protected regions if you expect it to survive future generation.

Protected regions recorded for this artifact: `workflow-assertions`. These regions are the explicit human-ownership boundary.

Template parameters: `stateMachineObj`, `emitCtx`.

### How to troubleshoot or repair it

First evaluate the guard: `emitCtx().get("enableTests") = true and stateMachineObj.aslDocument.isDefined()`. If it is false, check the corresponding generation flag or PSM relationship before treating the missing artifact as a failure. If it should be true, verify the source object, its path helper, and the template parameters. Then inspect the generation report and artifact trace for this rule.

### Authoritative sources

The scheduling, guard, target path, merge policy, and artifact record are defined at `mde/generation/awspsm-to-artifacts/awspsm2artifacts.egx:336`. The rendered content is defined by `tests/workflow-test.egl` under `mde/generation/awspsm-to-artifacts/templates/`.

---

## `TestFixturesReadme`

**Source context:** ``, `shared AWS PSM generation context` 
**Template:**`tests/fixtures-readme.egl` 
**Target path expression:**`"tests/fixtures/README.md"` 
**EGX source:**`mde/generation/awspsm-to-artifacts/awspsm2artifacts.egx:474`

### Why this generation rule exists

The fixture README explains what generated fixtures represent and where protected test data belongs. It turns a directory of JSON inputs into a usable testing surface for a new modeler.

### When it runs

The rule is conditional. A false guard is a deliberate omission boundary, not an empty generated file:

```egx
guard : emitCtx().get("enableTests") = true
```

### Artifact ownership and regeneration behavior

The artifact is configured to **replace the generated artifact completely**.

Because this rule overwrites rather than merges, the target is a generated projection. Manual edits to it are not a durable customization mechanism.

Template parameters: `awsRoot`, `emitCtx`.

Artifact record emitted by the EGX rule:

- `tests/fixtures/README.md / TEST_FIXTURE_DOC / AWSPSM2ART_Test_Fixtures_Readme / OVERWRITE_GENERATED`

### How to troubleshoot or repair it

First evaluate the guard: `emitCtx().get("enableTests") = true`. If it is false, check the corresponding generation flag or PSM relationship before treating the missing artifact as a failure. If it should be true, verify the source object, its path helper, and the template parameters. Then inspect the generation report and artifact trace for this rule.

### Authoritative sources

The scheduling, guard, target path, merge policy, and artifact record are defined at `mde/generation/awspsm-to-artifacts/awspsm2artifacts.egx:474`. The rendered content is defined by `tests/fixtures-readme.egl` under `mde/generation/awspsm-to-artifacts/templates/`.

---

## `ContractTests`

**Source context:** ``, `shared AWS PSM generation context` 
**Template:**`tests/contract-test.egl` 
**Target path expression:**`"tests/contract/generated_contracts_test." + emitCtx().testExtension()` 
**EGX source:**`mde/generation/awspsm-to-artifacts/awspsm2artifacts.egx:585`

### Why this generation rule exists

Contract tests verify the generated API, event, message, error, and data schemas as a set. They are generated only when enabled and preserve custom assertions so model-derived compatibility checks can coexist with domain-specific expectations.

### When it runs

The rule is conditional. A false guard is a deliberate omission boundary, not an empty generated file:

```egx
guard : emitCtx().get("enableTests") = true
```

### Artifact ownership and regeneration behavior

The artifact is configured to **merge with generated content while preserving configured protected regions**.

Merge mode means regeneration is expected to revisit the same file. Do not place durable manual content outside the named protected regions if you expect it to survive future generation.

Protected regions recorded for this artifact: `contract-custom-assertions`. These regions are the explicit human-ownership boundary.

Template parameters: `awsRoot`, `emitCtx`.

### How to troubleshoot or repair it

First evaluate the guard: `emitCtx().get("enableTests") = true`. If it is false, check the corresponding generation flag or PSM relationship before treating the missing artifact as a failure. If it should be true, verify the source object, its path helper, and the template parameters. Then inspect the generation report and artifact trace for this rule.

### Authoritative sources

The scheduling, guard, target path, merge policy, and artifact record are defined at `mde/generation/awspsm-to-artifacts/awspsm2artifacts.egx:585`. The rendered content is defined by `tests/contract-test.egl` under `mde/generation/awspsm-to-artifacts/templates/`.

---

## `EventTests`

**Source context:** ``, `shared AWS PSM generation context` 
**Template:**`tests/event-test.egl` 
**Target path expression:**`"tests/events/generated_events_test." + emitCtx().testExtension()` 
**EGX source:**`mde/generation/awspsm-to-artifacts/awspsm2artifacts.egx:597`

### Why this generation rule exists

Event tests exercise the generated event fixtures and routing assumptions. They make event delivery testable without requiring every payload or consumer assertion to be invented by the generator.

### When it runs

The rule is conditional. A false guard is a deliberate omission boundary, not an empty generated file:

```egx
guard : emitCtx().get("enableTests") = true
```

### Artifact ownership and regeneration behavior

The artifact is configured to **merge with generated content while preserving configured protected regions**.

Merge mode means regeneration is expected to revisit the same file. Do not place durable manual content outside the named protected regions if you expect it to survive future generation.

Protected regions recorded for this artifact: `event-custom-assertions`. These regions are the explicit human-ownership boundary.

Template parameters: `awsRoot`, `emitCtx`.

### How to troubleshoot or repair it

First evaluate the guard: `emitCtx().get("enableTests") = true`. If it is false, check the corresponding generation flag or PSM relationship before treating the missing artifact as a failure. If it should be true, verify the source object, its path helper, and the template parameters. Then inspect the generation report and artifact trace for this rule.

### Authoritative sources

The scheduling, guard, target path, merge policy, and artifact record are defined at `mde/generation/awspsm-to-artifacts/awspsm2artifacts.egx:597`. The rendered content is defined by `tests/event-test.egl` under `mde/generation/awspsm-to-artifacts/templates/`.

---

## `SecurityTests`

**Source context:** ``, `shared AWS PSM generation context` 
**Template:**`tests/security-test.egl` 
**Target path expression:**`"tests/e2e/security_generated_test." + emitCtx().testExtension()` 
**EGX source:**`mde/generation/awspsm-to-artifacts/awspsm2artifacts.egx:609`

### Why this generation rule exists

Security tests provide a place to verify generated authorization, secret handling, and production safeguards. The protected assertion region is essential because a model can describe a boundary but cannot know every organization-specific abuse case.

### When it runs

The rule is conditional. A false guard is a deliberate omission boundary, not an empty generated file:

```egx
guard : emitCtx().get("enableTests") = true
```

### Artifact ownership and regeneration behavior

The artifact is configured to **merge with generated content while preserving configured protected regions**.

Merge mode means regeneration is expected to revisit the same file. Do not place durable manual content outside the named protected regions if you expect it to survive future generation.

Protected regions recorded for this artifact: `security-custom-assertions`. These regions are the explicit human-ownership boundary.

Template parameters: `awsRoot`, `emitCtx`.

### How to troubleshoot or repair it

First evaluate the guard: `emitCtx().get("enableTests") = true`. If it is false, check the corresponding generation flag or PSM relationship before treating the missing artifact as a failure. If it should be true, verify the source object, its path helper, and the template parameters. Then inspect the generation report and artifact trace for this rule.

### Authoritative sources

The scheduling, guard, target path, merge policy, and artifact record are defined at `mde/generation/awspsm-to-artifacts/awspsm2artifacts.egx:609`. The rendered content is defined by `tests/security-test.egl` under `mde/generation/awspsm-to-artifacts/templates/`.

---

## `E2eTests`

**Source context:** ``, `shared AWS PSM generation context` 
**Template:**`tests/e2e-test.egl` 
**Target path expression:**`"tests/e2e/generated_flows_test." + emitCtx().testExtension()` 
**EGX source:**`mde/generation/awspsm-to-artifacts/awspsm2artifacts.egx:621`

### Why this generation rule exists

End-to-end tests connect modeled flows across API, function, data, event, and workflow artifacts. The scaffold gives the project a repeatable shape while leaving business-flow assertions and environment setup to the team.

### When it runs

The rule is conditional. A false guard is a deliberate omission boundary, not an empty generated file:

```egx
guard : emitCtx().get("enableTests") = true
```

### Artifact ownership and regeneration behavior

The artifact is configured to **merge with generated content while preserving configured protected regions**.

Merge mode means regeneration is expected to revisit the same file. Do not place durable manual content outside the named protected regions if you expect it to survive future generation.

Protected regions recorded for this artifact: `e2e-business-flow-assertions`. These regions are the explicit human-ownership boundary.

Template parameters: `awsRoot`, `emitCtx`.

### How to troubleshoot or repair it

First evaluate the guard: `emitCtx().get("enableTests") = true`. If it is false, check the corresponding generation flag or PSM relationship before treating the missing artifact as a failure. If it should be true, verify the source object, its path helper, and the template parameters. Then inspect the generation report and artifact trace for this rule.

### Authoritative sources

The scheduling, guard, target path, merge policy, and artifact record are defined at `mde/generation/awspsm-to-artifacts/awspsm2artifacts.egx:621`. The rendered content is defined by `tests/e2e-test.egl` under `mde/generation/awspsm-to-artifacts/templates/`.

---

## Useful resources

- [AWS SAM test concepts](https://docs.aws.amazon.com/serverless-application-model/latest/developerguide/using-sam-cli-test.html)
- [AWS Well-Architected Serverless Applications Lens](https://docs.aws.amazon.com/wellarchitected/latest/serverless-applications-lens/welcome.html)
