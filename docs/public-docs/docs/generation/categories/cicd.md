# Code generation: CI/CD

CI/CD templates make the model's validation and deployment gates reproducible in GitHub Actions. The production workflow is intentionally separate from development deployment so approvals, environments, and credentials are not accidentally treated as interchangeable.

The category contains 3 EGX generation rules and 3 EGL templates.

## Template inventory

| Template                                        | What it contains                                   |     Size |
| ----------------------------------------------- | -------------------------------------------------- | -------: |
| `templates/cicd/github-actions-deploy-dev.egl`  | Generates the github actions deploy dev artifact.  | 30 lines |
| `templates/cicd/github-actions-deploy-prod.egl` | Generates the github actions deploy prod artifact. | 32 lines |
| `templates/cicd/github-actions-validate.egl`    | Generates the github actions validate artifact.    | 32 lines |

---

## `CiValidate`

**Source context:** ``, `shared AWS PSM generation context` 
**Template:**`cicd/github-actions-validate.egl` 
**Target path expression:**`".github/workflows/validate.yml"` 
**EGX source:**`mde/generation/awspsm-to-artifacts/awspsm2artifacts.egx:732`

### Why this generation rule exists

The validation workflow turns model, template, contract, and test checks into a pull-request or branch gate. It is emitted only when CI/CD is enabled so teams that use another delivery system are not forced into GitHub Actions.

### When it runs

The rule is conditional. A false guard is a deliberate omission boundary, not an empty generated file:

```egx
guard : emitCtx().get("enableCiCd") = true
```

### Artifact ownership and regeneration behavior

The artifact is configured to **merge with generated content while preserving configured protected regions**.

Merge mode means regeneration is expected to revisit the same file. Do not place durable manual content outside the named protected regions if you expect it to survive future generation.

Protected regions recorded for this artifact: `ci-validate-customizations`. These regions are the explicit human-ownership boundary.

Template parameters: `awsRoot`, `emitCtx`.

Artifact record emitted by the EGX rule:

- `.github/workflows/validate.yml / CI_WORKFLOW / AWSPSM2ART_CiValidate / PRESERVE_PROTECTED_REGIONS`

### How to troubleshoot or repair it

First evaluate the guard: `emitCtx().get("enableCiCd") = true`. If it is false, check the corresponding generation flag or PSM relationship before treating the missing artifact as a failure. If it should be true, verify the source object, its path helper, and the template parameters. Then inspect the generation report and artifact trace for this rule.

### Authoritative sources

The scheduling, guard, target path, merge policy, and artifact record are defined at `mde/generation/awspsm-to-artifacts/awspsm2artifacts.egx:732`. The rendered content is defined by `cicd/github-actions-validate.egl` under `mde/generation/awspsm-to-artifacts/templates/`.

---

## `CiDeployDev`

**Source context:** ``, `shared AWS PSM generation context` 
**Template:**`cicd/github-actions-deploy-dev.egl` 
**Target path expression:**`".github/workflows/deploy-dev.yml"` 
**EGX source:**`mde/generation/awspsm-to-artifacts/awspsm2artifacts.egx:744`

### Why this generation rule exists

The development deployment workflow provides a lower-risk path for exercising generated infrastructure. Keeping it separate from production allows different credentials, approval, and rollback policy to be reviewed explicitly.

### When it runs

The rule is conditional. A false guard is a deliberate omission boundary, not an empty generated file:

```egx
guard : emitCtx().get("enableCiCd") = true
```

### Artifact ownership and regeneration behavior

The artifact is configured to **merge with generated content while preserving configured protected regions**.

Merge mode means regeneration is expected to revisit the same file. Do not place durable manual content outside the named protected regions if you expect it to survive future generation.

Protected regions recorded for this artifact: `ci-dev-customizations`. These regions are the explicit human-ownership boundary.

Template parameters: `awsRoot`, `emitCtx`.

Artifact record emitted by the EGX rule:

- `.github/workflows/deploy-dev.yml / CI_WORKFLOW / AWSPSM2ART_CiDeployDev / PRESERVE_PROTECTED_REGIONS`

### How to troubleshoot or repair it

First evaluate the guard: `emitCtx().get("enableCiCd") = true`. If it is false, check the corresponding generation flag or PSM relationship before treating the missing artifact as a failure. If it should be true, verify the source object, its path helper, and the template parameters. Then inspect the generation report and artifact trace for this rule.

### Authoritative sources

The scheduling, guard, target path, merge policy, and artifact record are defined at `mde/generation/awspsm-to-artifacts/awspsm2artifacts.egx:744`. The rendered content is defined by `cicd/github-actions-deploy-dev.egl` under `mde/generation/awspsm-to-artifacts/templates/`.

---

## `CiDeployProd`

**Source context:** ``, `shared AWS PSM generation context` 
**Template:**`cicd/github-actions-deploy-prod.egl` 
**Target path expression:**`".github/workflows/deploy-prod.yml"` 
**EGX source:**`mde/generation/awspsm-to-artifacts/awspsm2artifacts.egx:756`

### Why this generation rule exists

The production workflow is a controlled deployment artifact, not merely the dev workflow with a different name. Its separate template preserves room for approvals, protected environment settings, and production-specific validation.

### When it runs

The rule is conditional. A false guard is a deliberate omission boundary, not an empty generated file:

```egx
guard : emitCtx().get("enableCiCd") = true
```

### Artifact ownership and regeneration behavior

The artifact is configured to **merge with generated content while preserving configured protected regions**.

Merge mode means regeneration is expected to revisit the same file. Do not place durable manual content outside the named protected regions if you expect it to survive future generation.

Protected regions recorded for this artifact: `ci-prod-customizations`. These regions are the explicit human-ownership boundary.

Template parameters: `awsRoot`, `emitCtx`.

Artifact record emitted by the EGX rule:

- `.github/workflows/deploy-prod.yml / CI_WORKFLOW / AWSPSM2ART_CiDeployProd / PRESERVE_PROTECTED_REGIONS`

### How to troubleshoot or repair it

First evaluate the guard: `emitCtx().get("enableCiCd") = true`. If it is false, check the corresponding generation flag or PSM relationship before treating the missing artifact as a failure. If it should be true, verify the source object, its path helper, and the template parameters. Then inspect the generation report and artifact trace for this rule.

### Authoritative sources

The scheduling, guard, target path, merge policy, and artifact record are defined at `mde/generation/awspsm-to-artifacts/awspsm2artifacts.egx:756`. The rendered content is defined by `cicd/github-actions-deploy-prod.egl` under `mde/generation/awspsm-to-artifacts/templates/`.

---

## Useful resources

- [GitHub Actions documentation](https://docs.github.com/en/actions)
- [AWS SAM CI/CD concepts](https://docs.aws.amazon.com/serverless-application-model/latest/developerguide/serverless-cicd-github-actions.html)
