# Code generation — Scripts

Script templates assemble the repeatable local and CI workflow around the generated project. They validate models and templates, build, test, package, deploy, and invoke locally; merge mode allows teams to add repository-specific switches without losing the generated baseline.

The category contains 10 EGX generation rules and 10 EGL templates.

## Template inventory

| Template                                   | What it contains                           |     Size |
| ------------------------------------------ | ------------------------------------------ | -------: |
| `templates/scripts/build.egl`              | Generates the build artifact.              | 22 lines |
| `templates/scripts/deploy.egl`             | Generates the deploy artifact.             | 37 lines |
| `templates/scripts/local-invoke.egl`       | Generates the local invoke artifact.       | 18 lines |
| `templates/scripts/local-start-api.egl`    | Generates the local start api artifact.    | 21 lines |
| `templates/scripts/makefile.egl`           | Generates the makefile artifact.           | 47 lines |
| `templates/scripts/package.egl`            | Generates the package artifact.            | 33 lines |
| `templates/scripts/test.egl`               | Generates the test artifact.               | 10 lines |
| `templates/scripts/validate-contracts.egl` | Generates the validate contracts artifact. | 25 lines |
| `templates/scripts/validate-models.egl`    | Generates the validate models artifact.    | 25 lines |
| `templates/scripts/validate-template.egl`  | Generates the validate template artifact.  | 27 lines |

---

## `Makefile`

**Source context:** ``—`shared AWS PSM generation context` 
**Template:**`scripts/makefile.egl` 
**Target path expression:**`"Makefile"` 
**EGX source:**`mde/generation/awspsm-to-artifacts/awspsm2artifacts.egx:87`

### Why this generation rule exists

The Makefile is the human entry point to the generated lifecycle. It gathers validation, build, test, package, deploy, and local commands in one place, while merge mode keeps team shortcuts and environment-specific targets intact across regeneration.

### When it runs

There is no explicit guard; the rule is eligible during every generation run. Its template may still render an empty or minimal artifact when the model contains no corresponding optional elements.

### Artifact ownership and regeneration behavior

The artifact is configured to **merge with generated content while preserving configured protected regions**.

Merge mode means regeneration is expected to revisit the same file. Do not place durable manual content outside the named protected regions if you expect it to survive future generation.

Protected regions recorded for this artifact: `makefile-customizations`. These regions are the explicit human-ownership boundary.

Template parameters: `awsRoot`, `emitCtx`.

Artifact record emitted by the EGX rule:

- `Makefile / MAKEFILE / AWSPSM2ART_Makefile / PRESERVE_PROTECTED_REGIONS`

### How to troubleshoot or repair it

Verify that the AWS PSM root and the source objects expected by the template exist, then inspect the generated artifact record and the template parameters. If the file is incomplete, distinguish a model omission from a protected-region customization before changing the EGL template.

### Authoritative sources

The scheduling, guard, target path, merge policy, and artifact record are defined at `mde/generation/awspsm-to-artifacts/awspsm2artifacts.egx:87`. The rendered content is defined by `scripts/makefile.egl` under `mde/generation/awspsm-to-artifacts/templates/`.

---

## `ValidateModelsScript`

**Source context:** ``—`shared AWS PSM generation context` 
**Template:**`scripts/validate-models.egl` 
**Target path expression:**`"scripts/validate-models.sh"` 
**EGX source:**`mde/generation/awspsm-to-artifacts/awspsm2artifacts.egx:633`

### Why this generation rule exists

This script makes structural and semantic model validation runnable from the generated project. It is a lifecycle gate, not a convenience wrapper: generated deployment should be reproducible from a model that has passed the intended validation profile.

### When it runs

There is no explicit guard; the rule is eligible during every generation run. Its template may still render an empty or minimal artifact when the model contains no corresponding optional elements.

### Artifact ownership and regeneration behavior

The artifact is configured to **merge with generated content while preserving configured protected regions**.

Merge mode means regeneration is expected to revisit the same file. Do not place durable manual content outside the named protected regions if you expect it to survive future generation.

Protected regions recorded for this artifact: `validate-models-customizations`. These regions are the explicit human-ownership boundary.

Template parameters: `awsRoot`, `emitCtx`.

Artifact record emitted by the EGX rule:

- `scripts/validate-models.sh / SCRIPT / AWSPSM2ART_ValidateModelsScript / PRESERVE_PROTECTED_REGIONS`

### How to troubleshoot or repair it

Verify that the AWS PSM root and the source objects expected by the template exist, then inspect the generated artifact record and the template parameters. If the file is incomplete, distinguish a model omission from a protected-region customization before changing the EGL template.

### Authoritative sources

The scheduling, guard, target path, merge policy, and artifact record are defined at `mde/generation/awspsm-to-artifacts/awspsm2artifacts.egx:633`. The rendered content is defined by `scripts/validate-models.egl` under `mde/generation/awspsm-to-artifacts/templates/`.

---

## `ValidateTemplateScript`

**Source context:** ``—`shared AWS PSM generation context` 
**Template:**`scripts/validate-template.egl` 
**Target path expression:**`"scripts/validate-template.sh"` 
**EGX source:**`mde/generation/awspsm-to-artifacts/awspsm2artifacts.egx:644`

### Why this generation rule exists

CloudFormation/SAM template validation catches provider-document errors before deployment. Generating the command alongside the template keeps the deployment workflow honest about the difference between a valid model and a valid AWS template.

### When it runs

There is no explicit guard; the rule is eligible during every generation run. Its template may still render an empty or minimal artifact when the model contains no corresponding optional elements.

### Artifact ownership and regeneration behavior

The artifact is configured to **merge with generated content while preserving configured protected regions**.

Merge mode means regeneration is expected to revisit the same file. Do not place durable manual content outside the named protected regions if you expect it to survive future generation.

Protected regions recorded for this artifact: `validate-template-customizations`. These regions are the explicit human-ownership boundary.

Template parameters: `awsRoot`, `emitCtx`.

Artifact record emitted by the EGX rule:

- `scripts/validate-template.sh / SCRIPT / AWSPSM2ART_ValidateTemplateScript / PRESERVE_PROTECTED_REGIONS`

### How to troubleshoot or repair it

Verify that the AWS PSM root and the source objects expected by the template exist, then inspect the generated artifact record and the template parameters. If the file is incomplete, distinguish a model omission from a protected-region customization before changing the EGL template.

### Authoritative sources

The scheduling, guard, target path, merge policy, and artifact record are defined at `mde/generation/awspsm-to-artifacts/awspsm2artifacts.egx:644`. The rendered content is defined by `scripts/validate-template.egl` under `mde/generation/awspsm-to-artifacts/templates/`.

---

## `ValidateContractsScript`

**Source context:** ``—`shared AWS PSM generation context` 
**Template:**`scripts/validate-contracts.egl` 
**Target path expression:**`"scripts/validate-contracts.sh"` 
**EGX source:**`mde/generation/awspsm-to-artifacts/awspsm2artifacts.egx:655`

### Why this generation rule exists

Contract validation checks generated OpenAPI, JSON Schema, ASL, and catalog artifacts before tests or deployment consume them. It prevents downstream failures from being misdiagnosed as application logic defects.

### When it runs

There is no explicit guard; the rule is eligible during every generation run. Its template may still render an empty or minimal artifact when the model contains no corresponding optional elements.

### Artifact ownership and regeneration behavior

The artifact is configured to **merge with generated content while preserving configured protected regions**.

Merge mode means regeneration is expected to revisit the same file. Do not place durable manual content outside the named protected regions if you expect it to survive future generation.

Protected regions recorded for this artifact: `validate-contracts-customizations`. These regions are the explicit human-ownership boundary.

Template parameters: `awsRoot`, `emitCtx`.

Artifact record emitted by the EGX rule:

- `scripts/validate-contracts.sh / SCRIPT / AWSPSM2ART_ValidateContractsScript / PRESERVE_PROTECTED_REGIONS`

### How to troubleshoot or repair it

Verify that the AWS PSM root and the source objects expected by the template exist, then inspect the generated artifact record and the template parameters. If the file is incomplete, distinguish a model omission from a protected-region customization before changing the EGL template.

### Authoritative sources

The scheduling, guard, target path, merge policy, and artifact record are defined at `mde/generation/awspsm-to-artifacts/awspsm2artifacts.egx:655`. The rendered content is defined by `scripts/validate-contracts.egl` under `mde/generation/awspsm-to-artifacts/templates/`.

---

## `BuildScript`

**Source context:** ``—`shared AWS PSM generation context` 
**Template:**`scripts/build.egl` 
**Target path expression:**`"scripts/build.sh"` 
**EGX source:**`mde/generation/awspsm-to-artifacts/awspsm2artifacts.egx:666`

### Why this generation rule exists

The build script assembles shared runtime code and handlers using the generated project layout. It is kept mergeable because repositories often need compiler flags, private modules, or packaging steps beyond the model's common baseline.

### When it runs

There is no explicit guard; the rule is eligible during every generation run. Its template may still render an empty or minimal artifact when the model contains no corresponding optional elements.

### Artifact ownership and regeneration behavior

The artifact is configured to **merge with generated content while preserving configured protected regions**.

Merge mode means regeneration is expected to revisit the same file. Do not place durable manual content outside the named protected regions if you expect it to survive future generation.

Protected regions recorded for this artifact: `build-customizations`. These regions are the explicit human-ownership boundary.

Template parameters: `awsRoot`, `emitCtx`.

Artifact record emitted by the EGX rule:

- `scripts/build.sh / SCRIPT / AWSPSM2ART_BuildScript / PRESERVE_PROTECTED_REGIONS`

### How to troubleshoot or repair it

Verify that the AWS PSM root and the source objects expected by the template exist, then inspect the generated artifact record and the template parameters. If the file is incomplete, distinguish a model omission from a protected-region customization before changing the EGL template.

### Authoritative sources

The scheduling, guard, target path, merge policy, and artifact record are defined at `mde/generation/awspsm-to-artifacts/awspsm2artifacts.egx:666`. The rendered content is defined by `scripts/build.egl` under `mde/generation/awspsm-to-artifacts/templates/`.

---

## `TestScript`

**Source context:** ``—`shared AWS PSM generation context` 
**Template:**`scripts/test.egl` 
**Target path expression:**`"scripts/test.sh"` 
**EGX source:**`mde/generation/awspsm-to-artifacts/awspsm2artifacts.egx:677`

### Why this generation rule exists

The test script provides one generated command for unit, integration, contract, event, security, and end-to-end suites. Its value is consistent orchestration; protected customization is where teams add service-specific setup and selection.

### When it runs

There is no explicit guard; the rule is eligible during every generation run. Its template may still render an empty or minimal artifact when the model contains no corresponding optional elements.

### Artifact ownership and regeneration behavior

The artifact is configured to **merge with generated content while preserving configured protected regions**.

Merge mode means regeneration is expected to revisit the same file. Do not place durable manual content outside the named protected regions if you expect it to survive future generation.

Protected regions recorded for this artifact: `test-customizations`. These regions are the explicit human-ownership boundary.

Template parameters: `awsRoot`, `emitCtx`.

Artifact record emitted by the EGX rule:

- `scripts/test.sh / SCRIPT / AWSPSM2ART_TestScript / PRESERVE_PROTECTED_REGIONS`

### How to troubleshoot or repair it

Verify that the AWS PSM root and the source objects expected by the template exist, then inspect the generated artifact record and the template parameters. If the file is incomplete, distinguish a model omission from a protected-region customization before changing the EGL template.

### Authoritative sources

The scheduling, guard, target path, merge policy, and artifact record are defined at `mde/generation/awspsm-to-artifacts/awspsm2artifacts.egx:677`. The rendered content is defined by `scripts/test.egl` under `mde/generation/awspsm-to-artifacts/templates/`.

---

## `DeployScript`

**Source context:** ``—`shared AWS PSM generation context` 
**Template:**`scripts/deploy.egl` 
**Target path expression:**`"scripts/deploy.sh"` 
**EGX source:**`mde/generation/awspsm-to-artifacts/awspsm2artifacts.egx:688`

### Why this generation rule exists

Deployment is generated as an explicit project operation that validates, packages, and invokes the chosen stage configuration. The merge region lets teams add approvals, profiles, or change-set policy without forking the whole template.

### When it runs

There is no explicit guard; the rule is eligible during every generation run. Its template may still render an empty or minimal artifact when the model contains no corresponding optional elements.

### Artifact ownership and regeneration behavior

The artifact is configured to **merge with generated content while preserving configured protected regions**.

Merge mode means regeneration is expected to revisit the same file. Do not place durable manual content outside the named protected regions if you expect it to survive future generation.

Protected regions recorded for this artifact: `deploy-customizations`. These regions are the explicit human-ownership boundary.

Template parameters: `awsRoot`, `emitCtx`.

Artifact record emitted by the EGX rule:

- `scripts/deploy.sh / SCRIPT / AWSPSM2ART_DeployScript / PRESERVE_PROTECTED_REGIONS`

### How to troubleshoot or repair it

Verify that the AWS PSM root and the source objects expected by the template exist, then inspect the generated artifact record and the template parameters. If the file is incomplete, distinguish a model omission from a protected-region customization before changing the EGL template.

### Authoritative sources

The scheduling, guard, target path, merge policy, and artifact record are defined at `mde/generation/awspsm-to-artifacts/awspsm2artifacts.egx:688`. The rendered content is defined by `scripts/deploy.egl` under `mde/generation/awspsm-to-artifacts/templates/`.

---

## `LocalInvokeScript`

**Source context:** ``—`shared AWS PSM generation context` 
**Template:**`scripts/local-invoke.egl` 
**Target path expression:**`"scripts/local-invoke.sh"` 
**EGX source:**`mde/generation/awspsm-to-artifacts/awspsm2artifacts.egx:699`

### Why this generation rule exists

Local invocation helps a modeler exercise a generated Lambda with a fixture before AWS deployment. The generated baseline knows the project layout, while function-specific event data remains an intentional input.

### When it runs

There is no explicit guard; the rule is eligible during every generation run. Its template may still render an empty or minimal artifact when the model contains no corresponding optional elements.

### Artifact ownership and regeneration behavior

The artifact is configured to **merge with generated content while preserving configured protected regions**.

Merge mode means regeneration is expected to revisit the same file. Do not place durable manual content outside the named protected regions if you expect it to survive future generation.

Protected regions recorded for this artifact: `local-invoke-customizations`. These regions are the explicit human-ownership boundary.

Template parameters: `awsRoot`, `emitCtx`.

Artifact record emitted by the EGX rule:

- `scripts/local-invoke.sh / SCRIPT / AWSPSM2ART_LocalInvokeScript / PRESERVE_PROTECTED_REGIONS`

### How to troubleshoot or repair it

Verify that the AWS PSM root and the source objects expected by the template exist, then inspect the generated artifact record and the template parameters. If the file is incomplete, distinguish a model omission from a protected-region customization before changing the EGL template.

### Authoritative sources

The scheduling, guard, target path, merge policy, and artifact record are defined at `mde/generation/awspsm-to-artifacts/awspsm2artifacts.egx:699`. The rendered content is defined by `scripts/local-invoke.egl` under `mde/generation/awspsm-to-artifacts/templates/`.

---

## `LocalStartApiScript`

**Source context:** ``—`shared AWS PSM generation context` 
**Template:**`scripts/local-start-api.egl` 
**Target path expression:**`"scripts/local-start-api.sh"` 
**EGX source:**`mde/generation/awspsm-to-artifacts/awspsm2artifacts.egx:710`

### Why this generation rule exists

The local API script connects generated routes to the local runtime, making the modeled API surface testable before a deployed gateway exists. It is a workflow adapter, not a replacement for API contract validation.

### When it runs

There is no explicit guard; the rule is eligible during every generation run. Its template may still render an empty or minimal artifact when the model contains no corresponding optional elements.

### Artifact ownership and regeneration behavior

The artifact is configured to **merge with generated content while preserving configured protected regions**.

Merge mode means regeneration is expected to revisit the same file. Do not place durable manual content outside the named protected regions if you expect it to survive future generation.

Protected regions recorded for this artifact: `local-start-api-customizations`. These regions are the explicit human-ownership boundary.

Template parameters: `awsRoot`, `emitCtx`.

Artifact record emitted by the EGX rule:

- `scripts/local-start-api.sh / SCRIPT / AWSPSM2ART_LocalStartApiScript / PRESERVE_PROTECTED_REGIONS`

### How to troubleshoot or repair it

Verify that the AWS PSM root and the source objects expected by the template exist, then inspect the generated artifact record and the template parameters. If the file is incomplete, distinguish a model omission from a protected-region customization before changing the EGL template.

### Authoritative sources

The scheduling, guard, target path, merge policy, and artifact record are defined at `mde/generation/awspsm-to-artifacts/awspsm2artifacts.egx:710`. The rendered content is defined by `scripts/local-start-api.egl` under `mde/generation/awspsm-to-artifacts/templates/`.

---

## `PackageScript`

**Source context:** ``—`shared AWS PSM generation context` 
**Template:**`scripts/package.egl` 
**Target path expression:**`"scripts/package.sh"` 
**EGX source:**`mde/generation/awspsm-to-artifacts/awspsm2artifacts.egx:721`

### Why this generation rule exists

Packaging creates the artifacts SAM and deployment scripts expect. It keeps build output, contracts, and infrastructure preparation in a repeatable sequence rather than relying on undocumented manual commands.

### When it runs

There is no explicit guard; the rule is eligible during every generation run. Its template may still render an empty or minimal artifact when the model contains no corresponding optional elements.

### Artifact ownership and regeneration behavior

The artifact is configured to **merge with generated content while preserving configured protected regions**.

Merge mode means regeneration is expected to revisit the same file. Do not place durable manual content outside the named protected regions if you expect it to survive future generation.

Protected regions recorded for this artifact: `package-customizations`. These regions are the explicit human-ownership boundary.

Template parameters: `awsRoot`, `emitCtx`.

Artifact record emitted by the EGX rule:

- `scripts/package.sh / SCRIPT / AWSPSM2ART_PackageScript / PRESERVE_PROTECTED_REGIONS`

### How to troubleshoot or repair it

Verify that the AWS PSM root and the source objects expected by the template exist, then inspect the generated artifact record and the template parameters. If the file is incomplete, distinguish a model omission from a protected-region customization before changing the EGL template.

### Authoritative sources

The scheduling, guard, target path, merge policy, and artifact record are defined at `mde/generation/awspsm-to-artifacts/awspsm2artifacts.egx:721`. The rendered content is defined by `scripts/package.egl` under `mde/generation/awspsm-to-artifacts/templates/`.

---

## Useful resources

- [AWS SAM CLI documentation](https://docs.aws.amazon.com/serverless-application-model/latest/developerguide/using-sam-cli.html)
- [AWS CLI command reference](https://docs.aws.amazon.com/cli/latest/)
