# Code generation: Documentation and reports

Documentation templates expose the generated system's architecture, deployment, security, operations, traceability, manual actions, and generation evidence. These files are not ornamental: they are the human-readable record of what the model generated and what still requires a decision.

The category contains 17 EGX generation rules and 17 EGL templates.

## Template inventory

| Template                                       | What it contains                                  |     Size |
| ---------------------------------------------- | ------------------------------------------------- | -------: |
| `templates/docs/architecture.egl`              | Generates the architecture artifact.              | 18 lines |
| `templates/docs/artifact-trace-json.egl`       | Generates the artifact trace json artifact.       | 25 lines |
| `templates/docs/deployment.egl`                | Generates the deployment artifact.                | 17 lines |
| `templates/docs/generation-report.egl`         | Generates the generation report artifact.         | 33 lines |
| `templates/docs/manual-actions.egl`            | Generates the manual actions artifact.            | 14 lines |
| `templates/docs/model-summary.egl`             | Generates the model summary artifact.             | 20 lines |
| `templates/docs/model-trace-json.egl`          | Generates the model trace json artifact.          | 14 lines |
| `templates/docs/operations.egl`                | Generates the operations artifact.                | 15 lines |
| `templates/docs/project-scaffold.egl`          | Generates the project scaffold artifact.          |  7 lines |
| `templates/docs/protected-regions-json.egl`    | Generates the protected regions json artifact.    | 16 lines |
| `templates/docs/readme.egl`                    | Generates the readme artifact.                    | 22 lines |
| `templates/docs/runbook-incident-response.egl` | Generates the runbook incident response artifact. | 12 lines |
| `templates/docs/runbook-rollback.egl`          | Generates the runbook rollback artifact.          | 12 lines |
| `templates/docs/security-review.egl`           | Generates the security review artifact.           | 17 lines |
| `templates/docs/security.egl`                  | Generates the security artifact.                  | 21 lines |
| `templates/docs/structured-document.egl`       | Generates the structured document artifact.       |  1 lines |
| `templates/docs/traceability.egl`              | Generates the traceability artifact.              | 13 lines |

---

## `ProjectScaffold`

**Source context:** ``, `shared AWS PSM generation context` 
**Template:**`docs/project-scaffold.egl` 
**Target path expression:**`"generated/reports/project-scaffold.txt"` 
**EGX source:**`mde/generation/awspsm-to-artifacts/awspsm2artifacts.egx:42`

### Why this generation rule exists

A generated project needs a stable explanation of its shape before any individual source file is opened. This report records the artifact layout, model identity, and generation conventions so a modeler can distinguish an intentional scaffold from a missing or manually added file.

### When it runs

There is no explicit guard; the rule is eligible during every generation run. Its template may still render an empty or minimal artifact when the model contains no corresponding optional elements.

### Artifact ownership and regeneration behavior

The artifact is configured to **replace the generated artifact completely**.

Because this rule overwrites rather than merges, the target is a generated projection. Manual edits to it are not a durable customization mechanism.

Template parameters: `awsRoot`, `emitCtx`.

Artifact record emitted by the EGX rule:

- `generated/reports/project-scaffold.txt / PROJECT_SCAFFOLD / AWSPSM2ART_ProjectScaffold / OVERWRITE_GENERATED`

### How to troubleshoot or repair it

Verify that the AWS PSM root and the source objects expected by the template exist, then inspect the generated artifact record and the template parameters. If the file is incomplete, distinguish a model omission from a protected-region customization before changing the EGL template.

### Authoritative sources

The scheduling, guard, target path, merge policy, and artifact record are defined at `mde/generation/awspsm-to-artifacts/awspsm2artifacts.egx:42`. The rendered content is defined by `docs/project-scaffold.egl` under `mde/generation/awspsm-to-artifacts/templates/`.

---

## `StructuredDocumentArtifact`

**Source context:** `documentObj`, `KERNEL!StructuredDocument`  
**Template:** `docs/structured-document.egl`  
**Target path expression:** `documentObj.documentPath()`  
**EGX source:** `mde/generation/awspsm-to-artifacts/awspsm2artifacts.egx:460`

### Why this generation rule exists

Structured PSM documents are emitted as individual artifacts so external adapters, business rules, decisions, and contracts retain their own provenance and path. ASL is excluded because it has a dedicated template and lifecycle rule.

### When it runs

The rule is conditional. A false guard is a deliberate omission boundary, not an empty generated file:

```egx
guard : documentObj.content.isPresent() and documentObj.eClass().getName() <> "AslDocument"
```

### Artifact ownership and regeneration behavior

The artifact is configured to **replace the generated artifact completely**.

Because this rule overwrites rather than merges, the target is a generated projection. Manual edits to it are not a durable customization mechanism.

Template parameters: `documentObj`, `emitCtx`.

### How to troubleshoot or repair it

First evaluate the guard: `documentObj.content.isPresent() and documentObj.eClass().getName() <> "AslDocument"`. If it is false, check the corresponding generation flag or PSM relationship before treating the missing artifact as a failure. If it should be true, verify the source object, its path helper, and the template parameters. Then inspect the generation report and artifact trace for this rule.

### Authoritative sources

The scheduling, guard, target path, merge policy, and artifact record are defined at `mde/generation/awspsm-to-artifacts/awspsm2artifacts.egx:460`. The rendered content is defined by `docs/structured-document.egl` under `mde/generation/awspsm-to-artifacts/templates/`.

---

## `Readme`

**Source context:** ``, `shared AWS PSM generation context` 
**Template:**`docs/readme.egl` 
**Target path expression:**`"README.md"` 
**EGX source:**`mde/generation/awspsm-to-artifacts/awspsm2artifacts.egx:768`

### Why this generation rule exists

The generated README gives a new maintainer the shortest path from model output to local validation, testing, and deployment. Its protected project-notes region keeps team context from being erased by regeneration.

### When it runs

The rule is conditional. A false guard is a deliberate omission boundary, not an empty generated file:

```egx
guard : emitCtx().get("enableDocs") = true
```

### Artifact ownership and regeneration behavior

The artifact is configured to **merge with generated content while preserving configured protected regions**.

Merge mode means regeneration is expected to revisit the same file. Do not place durable manual content outside the named protected regions if you expect it to survive future generation.

Protected regions recorded for this artifact: `readme-custom-project-notes`. These regions are the explicit human-ownership boundary.

Template parameters: `awsRoot`, `emitCtx`.

Artifact record emitted by the EGX rule:

- `README.md / DOCUMENTATION / AWSPSM2ART_Readme / PRESERVE_PROTECTED_REGIONS`

### How to troubleshoot or repair it

First evaluate the guard: `emitCtx().get("enableDocs") = true`. If it is false, check the corresponding generation flag or PSM relationship before treating the missing artifact as a failure. If it should be true, verify the source object, its path helper, and the template parameters. Then inspect the generation report and artifact trace for this rule.

### Authoritative sources

The scheduling, guard, target path, merge policy, and artifact record are defined at `mde/generation/awspsm-to-artifacts/awspsm2artifacts.egx:768`. The rendered content is defined by `docs/readme.egl` under `mde/generation/awspsm-to-artifacts/templates/`.

---

## `ArchitectureDoc`

**Source context:** ``, `shared AWS PSM generation context` 
**Template:**`docs/architecture.egl` 
**Target path expression:**`"docs/architecture.md"` 
**EGX source:**`mde/generation/awspsm-to-artifacts/awspsm2artifacts.egx:780`

### Why this generation rule exists

The architecture document explains how generated AWS resources, services, APIs, data, events, and workflows fit together. It translates the PSM into a human-reviewable system picture and preserves room for manually maintained diagrams.

### When it runs

The rule is conditional. A false guard is a deliberate omission boundary, not an empty generated file:

```egx
guard : emitCtx().get("enableDocs") = true
```

### Artifact ownership and regeneration behavior

The artifact is configured to **merge with generated content while preserving configured protected regions**.

Merge mode means regeneration is expected to revisit the same file. Do not place durable manual content outside the named protected regions if you expect it to survive future generation.

Protected regions recorded for this artifact: `architecture-custom-diagrams`. These regions are the explicit human-ownership boundary.

Template parameters: `awsRoot`, `emitCtx`.

Artifact record emitted by the EGX rule:

- `docs/architecture.md / DOCUMENTATION / AWSPSM2ART_ArchitectureDoc / PRESERVE_PROTECTED_REGIONS`

### How to troubleshoot or repair it

First evaluate the guard: `emitCtx().get("enableDocs") = true`. If it is false, check the corresponding generation flag or PSM relationship before treating the missing artifact as a failure. If it should be true, verify the source object, its path helper, and the template parameters. Then inspect the generation report and artifact trace for this rule.

### Authoritative sources

The scheduling, guard, target path, merge policy, and artifact record are defined at `mde/generation/awspsm-to-artifacts/awspsm2artifacts.egx:780`. The rendered content is defined by `docs/architecture.egl` under `mde/generation/awspsm-to-artifacts/templates/`.

---

## `ModelSummaryDoc`

**Source context:** ``, `shared AWS PSM generation context` 
**Template:**`docs/model-summary.egl` 
**Target path expression:**`"docs/model-summary.md"` 
**EGX source:**`mde/generation/awspsm-to-artifacts/awspsm2artifacts.egx:792`

### Why this generation rule exists

The model summary is an overwrite-generated snapshot of what the PSM currently contains. Unlike mergeable narrative documentation, it is intentionally regenerated wholesale so stale inventory does not survive a model change.

### When it runs

The rule is conditional. A false guard is a deliberate omission boundary, not an empty generated file:

```egx
guard : emitCtx().get("enableDocs") = true
```

### Artifact ownership and regeneration behavior

The artifact is configured to **replace the generated artifact completely**.

Because this rule overwrites rather than merges, the target is a generated projection. Manual edits to it are not a durable customization mechanism.

Template parameters: `awsRoot`, `emitCtx`.

Artifact record emitted by the EGX rule:

- `docs/model-summary.md / DOCUMENTATION / AWSPSM2ART_ModelSummaryDoc / OVERWRITE_GENERATED`

### How to troubleshoot or repair it

First evaluate the guard: `emitCtx().get("enableDocs") = true`. If it is false, check the corresponding generation flag or PSM relationship before treating the missing artifact as a failure. If it should be true, verify the source object, its path helper, and the template parameters. Then inspect the generation report and artifact trace for this rule.

### Authoritative sources

The scheduling, guard, target path, merge policy, and artifact record are defined at `mde/generation/awspsm-to-artifacts/awspsm2artifacts.egx:792`. The rendered content is defined by `docs/model-summary.egl` under `mde/generation/awspsm-to-artifacts/templates/`.

---

## `SecurityDoc`

**Source context:** ``, `shared AWS PSM generation context` 
**Template:**`docs/security.egl` 
**Target path expression:**`"docs/security.md"` 
**EGX source:**`mde/generation/awspsm-to-artifacts/awspsm2artifacts.egx:804`

### Why this generation rule exists

The security document gathers generated trust, permissions, encryption, secrets, authentication, and manual decisions into one review surface. It turns security posture into evidence that can be audited beside the infrastructure.

### When it runs

The rule is conditional. A false guard is a deliberate omission boundary, not an empty generated file:

```egx
guard : emitCtx().get("enableDocs") = true
```

### Artifact ownership and regeneration behavior

The artifact is configured to **merge with generated content while preserving configured protected regions**.

Merge mode means regeneration is expected to revisit the same file. Do not place durable manual content outside the named protected regions if you expect it to survive future generation.

Protected regions recorded for this artifact: `security-manual-decisions`. These regions are the explicit human-ownership boundary.

Template parameters: `awsRoot`, `emitCtx`.

Artifact record emitted by the EGX rule:

- `docs/security.md / DOCUMENTATION / AWSPSM2ART_SecurityDoc / PRESERVE_PROTECTED_REGIONS`

### How to troubleshoot or repair it

First evaluate the guard: `emitCtx().get("enableDocs") = true`. If it is false, check the corresponding generation flag or PSM relationship before treating the missing artifact as a failure. If it should be true, verify the source object, its path helper, and the template parameters. Then inspect the generation report and artifact trace for this rule.

### Authoritative sources

The scheduling, guard, target path, merge policy, and artifact record are defined at `mde/generation/awspsm-to-artifacts/awspsm2artifacts.egx:804`. The rendered content is defined by `docs/security.egl` under `mde/generation/awspsm-to-artifacts/templates/`.

---

## `OperationsDoc`

**Source context:** ``, `shared AWS PSM generation context` 
**Template:**`docs/operations.egl` 
**Target path expression:**`"docs/operations.md"` 
**EGX source:**`mde/generation/awspsm-to-artifacts/awspsm2artifacts.egx:816`

### Why this generation rule exists

The operations document explains logs, metrics, alarms, retries, dead letters, tracing, backups, and runbook hooks derived from the PSM. Protected team-runbook details acknowledge that operational ownership is not inferable from resources alone.

### When it runs

The rule is conditional. A false guard is a deliberate omission boundary, not an empty generated file:

```egx
guard : emitCtx().get("enableDocs") = true
```

### Artifact ownership and regeneration behavior

The artifact is configured to **merge with generated content while preserving configured protected regions**.

Merge mode means regeneration is expected to revisit the same file. Do not place durable manual content outside the named protected regions if you expect it to survive future generation.

Protected regions recorded for this artifact: `operations-team-runbook-details`. These regions are the explicit human-ownership boundary.

Template parameters: `awsRoot`, `emitCtx`.

Artifact record emitted by the EGX rule:

- `docs/operations.md / DOCUMENTATION / AWSPSM2ART_OperationsDoc / PRESERVE_PROTECTED_REGIONS`

### How to troubleshoot or repair it

First evaluate the guard: `emitCtx().get("enableDocs") = true`. If it is false, check the corresponding generation flag or PSM relationship before treating the missing artifact as a failure. If it should be true, verify the source object, its path helper, and the template parameters. Then inspect the generation report and artifact trace for this rule.

### Authoritative sources

The scheduling, guard, target path, merge policy, and artifact record are defined at `mde/generation/awspsm-to-artifacts/awspsm2artifacts.egx:816`. The rendered content is defined by `docs/operations.egl` under `mde/generation/awspsm-to-artifacts/templates/`.

---

## `DeploymentDoc`

**Source context:** ``, `shared AWS PSM generation context` 
**Template:**`docs/deployment.egl` 
**Target path expression:**`"docs/deployment.md"` 
**EGX source:**`mde/generation/awspsm-to-artifacts/awspsm2artifacts.egx:828`

### Why this generation rule exists

The deployment document makes stages, accounts, regions, stacks, parameters, and deployment commands explicit. It prevents deployment knowledge from remaining hidden inside generated templates or a single operator's memory.

### When it runs

The rule is conditional. A false guard is a deliberate omission boundary, not an empty generated file:

```egx
guard : emitCtx().get("enableDocs") = true
```

### Artifact ownership and regeneration behavior

The artifact is configured to **merge with generated content while preserving configured protected regions**.

Merge mode means regeneration is expected to revisit the same file. Do not place durable manual content outside the named protected regions if you expect it to survive future generation.

Protected regions recorded for this artifact: `deployment-account-notes`. These regions are the explicit human-ownership boundary.

Template parameters: `awsRoot`, `emitCtx`.

Artifact record emitted by the EGX rule:

- `docs/deployment.md / DOCUMENTATION / AWSPSM2ART_DeploymentDoc / PRESERVE_PROTECTED_REGIONS`

### How to troubleshoot or repair it

First evaluate the guard: `emitCtx().get("enableDocs") = true`. If it is false, check the corresponding generation flag or PSM relationship before treating the missing artifact as a failure. If it should be true, verify the source object, its path helper, and the template parameters. Then inspect the generation report and artifact trace for this rule.

### Authoritative sources

The scheduling, guard, target path, merge policy, and artifact record are defined at `mde/generation/awspsm-to-artifacts/awspsm2artifacts.egx:828`. The rendered content is defined by `docs/deployment.egl` under `mde/generation/awspsm-to-artifacts/templates/`.

---

## `IncidentRunbook`

**Source context:** ``, `shared AWS PSM generation context` 
**Template:**`docs/runbook-incident-response.egl` 
**Target path expression:**`"docs/runbooks/incident-response.md"` 
**EGX source:**`mde/generation/awspsm-to-artifacts/awspsm2artifacts.egx:840`

### Why this generation rule exists

The incident runbook translates modeled observability and resilience into an initial response sequence. The protected team-steps region is where organization-specific escalation, contacts, and evidence collection must be added.

### When it runs

The rule is conditional. A false guard is a deliberate omission boundary, not an empty generated file:

```egx
guard : emitCtx().get("enableDocs") = true
```

### Artifact ownership and regeneration behavior

The artifact is configured to **merge with generated content while preserving configured protected regions**.

Merge mode means regeneration is expected to revisit the same file. Do not place durable manual content outside the named protected regions if you expect it to survive future generation.

Protected regions recorded for this artifact: `incident-response-team-steps`. These regions are the explicit human-ownership boundary.

Template parameters: `awsRoot`, `emitCtx`.

Artifact record emitted by the EGX rule:

- `docs/runbooks/incident-response.md / RUNBOOK / AWSPSM2ART_IncidentRunbook / PRESERVE_PROTECTED_REGIONS`

### How to troubleshoot or repair it

First evaluate the guard: `emitCtx().get("enableDocs") = true`. If it is false, check the corresponding generation flag or PSM relationship before treating the missing artifact as a failure. If it should be true, verify the source object, its path helper, and the template parameters. Then inspect the generation report and artifact trace for this rule.

### Authoritative sources

The scheduling, guard, target path, merge policy, and artifact record are defined at `mde/generation/awspsm-to-artifacts/awspsm2artifacts.egx:840`. The rendered content is defined by `docs/runbook-incident-response.egl` under `mde/generation/awspsm-to-artifacts/templates/`.

---

## `RollbackRunbook`

**Source context:** ``, `shared AWS PSM generation context` 
**Template:**`docs/runbook-rollback.egl` 
**Target path expression:**`"docs/runbooks/rollback.md"` 
**EGX source:**`mde/generation/awspsm-to-artifacts/awspsm2artifacts.egx:852`

### Why this generation rule exists

Rollback guidance is generated from deployment and artifact context so a release has a recovery narrative from the start. Team-owned steps remain protected because rollback authority and data-recovery policy are organizational decisions.

### When it runs

The rule is conditional. A false guard is a deliberate omission boundary, not an empty generated file:

```egx
guard : emitCtx().get("enableDocs") = true
```

### Artifact ownership and regeneration behavior

The artifact is configured to **merge with generated content while preserving configured protected regions**.

Merge mode means regeneration is expected to revisit the same file. Do not place durable manual content outside the named protected regions if you expect it to survive future generation.

Protected regions recorded for this artifact: `rollback-team-steps`. These regions are the explicit human-ownership boundary.

Template parameters: `awsRoot`, `emitCtx`.

Artifact record emitted by the EGX rule:

- `docs/runbooks/rollback.md / RUNBOOK / AWSPSM2ART_RollbackRunbook / PRESERVE_PROTECTED_REGIONS`

### How to troubleshoot or repair it

First evaluate the guard: `emitCtx().get("enableDocs") = true`. If it is false, check the corresponding generation flag or PSM relationship before treating the missing artifact as a failure. If it should be true, verify the source object, its path helper, and the template parameters. Then inspect the generation report and artifact trace for this rule.

### Authoritative sources

The scheduling, guard, target path, merge policy, and artifact record are defined at `mde/generation/awspsm-to-artifacts/awspsm2artifacts.egx:852`. The rendered content is defined by `docs/runbook-rollback.egl` under `mde/generation/awspsm-to-artifacts/templates/`.

---

## `TraceabilityDoc`

**Source context:** ``, `shared AWS PSM generation context` 
**Template:**`docs/traceability.egl` 
**Target path expression:**`"docs/traceability.md"` 
**EGX source:**`mde/generation/awspsm-to-artifacts/awspsm2artifacts.egx:864`

### Why this generation rule exists

The traceability document is the human-readable counterpart to machine trace JSON. It lets reviewers follow artifacts back through PSM resources and transformation decisions without reading the Ecore model directly.

### When it runs

The rule is conditional. A false guard is a deliberate omission boundary, not an empty generated file:

```egx
guard : emitCtx().get("enableDocs") = true
```

### Artifact ownership and regeneration behavior

The artifact is configured to **replace the generated artifact completely**.

Because this rule overwrites rather than merges, the target is a generated projection. Manual edits to it are not a durable customization mechanism.

Template parameters: `awsRoot`, `emitCtx`.

Artifact record emitted by the EGX rule:

- `docs/traceability.md / TRACEABILITY_DOC / AWSPSM2ART_TraceabilityDoc / OVERWRITE_GENERATED`

### How to troubleshoot or repair it

First evaluate the guard: `emitCtx().get("enableDocs") = true`. If it is false, check the corresponding generation flag or PSM relationship before treating the missing artifact as a failure. If it should be true, verify the source object, its path helper, and the template parameters. Then inspect the generation report and artifact trace for this rule.

### Authoritative sources

The scheduling, guard, target path, merge policy, and artifact record are defined at `mde/generation/awspsm-to-artifacts/awspsm2artifacts.egx:864`. The rendered content is defined by `docs/traceability.egl` under `mde/generation/awspsm-to-artifacts/templates/`.

---

## `ProtectedRegionsJson`

**Source context:** ``, `shared AWS PSM generation context` 
**Template:**`docs/protected-regions-json.egl` 
**Target path expression:**`"generated/trace/protected-regions.json"` 
**EGX source:**`mde/generation/awspsm-to-artifacts/awspsm2artifacts.egx:876`

### Why this generation rule exists

Protected-region JSON is a machine-readable ownership contract for regeneration. It tells tools and reviewers which parts of generated files are intentionally editable and therefore must not be overwritten casually.

### When it runs

There is no explicit guard; the rule is eligible during every generation run. Its template may still render an empty or minimal artifact when the model contains no corresponding optional elements.

### Artifact ownership and regeneration behavior

The artifact is configured to **replace the generated artifact completely**.

Because this rule overwrites rather than merges, the target is a generated projection. Manual edits to it are not a durable customization mechanism.

Template parameters: `awsRoot`, `emitCtx`.

Artifact record emitted by the EGX rule:

- `generated/trace/protected-regions.json / TRACE_REPORT / AWSPSM2ART_ProtectedRegionsJson / OVERWRITE_GENERATED`

### How to troubleshoot or repair it

Verify that the AWS PSM root and the source objects expected by the template exist, then inspect the generated artifact record and the template parameters. If the file is incomplete, distinguish a model omission from a protected-region customization before changing the EGL template.

### Authoritative sources

The scheduling, guard, target path, merge policy, and artifact record are defined at `mde/generation/awspsm-to-artifacts/awspsm2artifacts.egx:876`. The rendered content is defined by `docs/protected-regions-json.egl` under `mde/generation/awspsm-to-artifacts/templates/`.

---

## `ModelTraceJson`

**Source context:** ``, `shared AWS PSM generation context` 
**Template:**`docs/model-trace-json.egl` 
**Target path expression:**`"generated/trace/model-trace.json"` 
**EGX source:**`mde/generation/awspsm-to-artifacts/awspsm2artifacts.egx:889`

### Why this generation rule exists

Model trace JSON records the PSM-side lineage that generation consumed. It supports debugging, audits, and incremental regeneration by preserving the source identity behind generated artifacts.

### When it runs

There is no explicit guard; the rule is eligible during every generation run. Its template may still render an empty or minimal artifact when the model contains no corresponding optional elements.

### Artifact ownership and regeneration behavior

The artifact is configured to **replace the generated artifact completely**.

Because this rule overwrites rather than merges, the target is a generated projection. Manual edits to it are not a durable customization mechanism.

Template parameters: `awsRoot`, `emitCtx`.

Artifact record emitted by the EGX rule:

- `generated/trace/model-trace.json / TRACE_REPORT / AWSPSM2ART_ModelTraceJson / OVERWRITE_GENERATED`

### How to troubleshoot or repair it

Verify that the AWS PSM root and the source objects expected by the template exist, then inspect the generated artifact record and the template parameters. If the file is incomplete, distinguish a model omission from a protected-region customization before changing the EGL template.

### Authoritative sources

The scheduling, guard, target path, merge policy, and artifact record are defined at `mde/generation/awspsm-to-artifacts/awspsm2artifacts.egx:889`. The rendered content is defined by `docs/model-trace-json.egl` under `mde/generation/awspsm-to-artifacts/templates/`.

---

## `SecurityReviewReport`

**Source context:** ``, `shared AWS PSM generation context` 
**Template:**`docs/security-review.egl` 
**Target path expression:**`"generated/reports/security-review.md"` 
**EGX source:**`mde/generation/awspsm-to-artifacts/awspsm2artifacts.egx:915`

### Why this generation rule exists

The security review report consolidates generated security controls and unresolved security decisions. It is evidence for human review, not a claim that generation has proven the deployment secure.

### When it runs

There is no explicit guard; the rule is eligible during every generation run. Its template may still render an empty or minimal artifact when the model contains no corresponding optional elements.

### Artifact ownership and regeneration behavior

The artifact is configured to **replace the generated artifact completely**.

Because this rule overwrites rather than merges, the target is a generated projection. Manual edits to it are not a durable customization mechanism.

Template parameters: `awsRoot`, `emitCtx`.

Artifact record emitted by the EGX rule:

- `generated/reports/security-review.md / SECURITY_REPORT / AWSPSM2ART_SecurityReviewReport / OVERWRITE_GENERATED`

### How to troubleshoot or repair it

Verify that the AWS PSM root and the source objects expected by the template exist, then inspect the generated artifact record and the template parameters. If the file is incomplete, distinguish a model omission from a protected-region customization before changing the EGL template.

### Authoritative sources

The scheduling, guard, target path, merge policy, and artifact record are defined at `mde/generation/awspsm-to-artifacts/awspsm2artifacts.egx:915`. The rendered content is defined by `docs/security-review.egl` under `mde/generation/awspsm-to-artifacts/templates/`.

---

## `ManualActionsReport`

**Source context:** ``, `shared AWS PSM generation context` 
**Template:**`docs/manual-actions.egl` 
**Target path expression:**`"generated/reports/manual-actions.md"` 
**EGX source:**`mde/generation/awspsm-to-artifacts/awspsm2artifacts.egx:928`

### Why this generation rule exists

Manual actions are gathered into one actionable queue so modelers do not need to discover unresolved decisions by searching every generated file. This is the hand-off between automated refinement and accountable human completion.

### When it runs

There is no explicit guard; the rule is eligible during every generation run. Its template may still render an empty or minimal artifact when the model contains no corresponding optional elements.

### Artifact ownership and regeneration behavior

The artifact is configured to **replace the generated artifact completely**.

Because this rule overwrites rather than merges, the target is a generated projection. Manual edits to it are not a durable customization mechanism.

Template parameters: `awsRoot`, `emitCtx`.

Artifact record emitted by the EGX rule:

- `generated/reports/manual-actions.md / MANUAL_ACTIONS_REPORT / AWSPSM2ART_ManualActionsReport / OVERWRITE_GENERATED`

### How to troubleshoot or repair it

Verify that the AWS PSM root and the source objects expected by the template exist, then inspect the generated artifact record and the template parameters. If the file is incomplete, distinguish a model omission from a protected-region customization before changing the EGL template.

### Authoritative sources

The scheduling, guard, target path, merge policy, and artifact record are defined at `mde/generation/awspsm-to-artifacts/awspsm2artifacts.egx:928`. The rendered content is defined by `docs/manual-actions.egl` under `mde/generation/awspsm-to-artifacts/templates/`.

---

## `GenerationReport`

**Source context:** ``, `shared AWS PSM generation context` 
**Template:**`docs/generation-report.egl` 
**Target path expression:**`"generated/reports/generation-report.md"` 
**EGX source:**`mde/generation/awspsm-to-artifacts/awspsm2artifacts.egx:941`

### Why this generation rule exists

The generation report records which artifacts were planned, their ownership mode, and outstanding issues. It is the operational answer to the question 'what did this run actually generate?'.

### When it runs

There is no explicit guard; the rule is eligible during every generation run. Its template may still render an empty or minimal artifact when the model contains no corresponding optional elements.

### Artifact ownership and regeneration behavior

The artifact is configured to **replace the generated artifact completely**.

Because this rule overwrites rather than merges, the target is a generated projection. Manual edits to it are not a durable customization mechanism.

Template parameters: `awsRoot`, `emitCtx`.

Artifact record emitted by the EGX rule:

- `generated/reports/generation-report.md / GENERATION_REPORT / AWSPSM2ART_GenerationReport / OVERWRITE_GENERATED`

### How to troubleshoot or repair it

Verify that the AWS PSM root and the source objects expected by the template exist, then inspect the generated artifact record and the template parameters. If the file is incomplete, distinguish a model omission from a protected-region customization before changing the EGL template.

### Authoritative sources

The scheduling, guard, target path, merge policy, and artifact record are defined at `mde/generation/awspsm-to-artifacts/awspsm2artifacts.egx:941`. The rendered content is defined by `docs/generation-report.egl` under `mde/generation/awspsm-to-artifacts/templates/`.

---

## `ArtifactTraceJson`

**Source context:** ``, `shared AWS PSM generation context` 
**Template:**`docs/artifact-trace-json.egl` 
**Target path expression:**`"generated/trace/artifact-trace.json"` 
**EGX source:**`mde/generation/awspsm-to-artifacts/awspsm2artifacts.egx:954`

### Why this generation rule exists

Artifact trace JSON connects output paths and artifact kinds to model keys, rules, and protected regions. It is the machine-readable evidence needed to diagnose stale, missing, or unexpectedly overwritten files.

### When it runs

There is no explicit guard; the rule is eligible during every generation run. Its template may still render an empty or minimal artifact when the model contains no corresponding optional elements.

### Artifact ownership and regeneration behavior

The artifact is configured to **replace the generated artifact completely**.

Because this rule overwrites rather than merges, the target is a generated projection. Manual edits to it are not a durable customization mechanism.

Template parameters: `awsRoot`, `emitCtx`.

Artifact record emitted by the EGX rule:

- `generated/trace/artifact-trace.json / TRACE_REPORT / AWSPSM2ART_ArtifactTraceJson / OVERWRITE_GENERATED`

### How to troubleshoot or repair it

Verify that the AWS PSM root and the source objects expected by the template exist, then inspect the generated artifact record and the template parameters. If the file is incomplete, distinguish a model omission from a protected-region customization before changing the EGL template.

### Authoritative sources

The scheduling, guard, target path, merge policy, and artifact record are defined at `mde/generation/awspsm-to-artifacts/awspsm2artifacts.egx:954`. The rendered content is defined by `docs/artifact-trace-json.egl` under `mde/generation/awspsm-to-artifacts/templates/`.

---

## Useful resources

- [AWS Well-Architected Framework](https://docs.aws.amazon.com/wellarchitected/latest/framework/welcome.html)
- [AWS Serverless Applications Lens](https://docs.aws.amazon.com/wellarchitected/latest/serverless-applications-lens/welcome.html)
