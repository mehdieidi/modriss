# Code generation — Infrastructure

Infrastructure templates turn the AWS PSM deployment graph into files that SAM, CloudFormation, and the deployment tooling can consume. The important distinction is between a generated deployment description and a user-owned deployment decision: merge mode and protected regions preserve the latter while the template continuously regenerates the former.

The category contains 11 EGX generation rules and 7 EGL templates.

## Template inventory

| Template                                            | What it contains                                                                                |      Size |
| --------------------------------------------------- | ----------------------------------------------------------------------------------------------- | --------: |
| `templates/infrastructure/default-env-json.egl`     | Generates the default env json artifact.                                                        |   7 lines |
| `templates/infrastructure/env-json.egl`             | Generates the env json artifact.                                                                |  12 lines |
| `templates/infrastructure/gitignore.egl`            | Generates the gitignore artifact.                                                               |  13 lines |
| `templates/infrastructure/go-mod.egl`               | Generates the go mod artifact.                                                                  |   5 lines |
| `templates/infrastructure/iam-policy-rationale.egl` | Generates the iam policy rationale artifact.                                                    |   9 lines |
| `templates/infrastructure/sam-template.egl`         | Generates the sam template artifact.                                                            | 140 lines |
| `templates/infrastructure/samconfig.egl`            | Generates the samconfig artifact. Multi-stack commands pass stack-specific settings explicitly. |   8 lines |

---

## `PackageManifest`

**Source context:** ``—`shared AWS PSM generation context` 
**Template:**`infrastructure/go-mod.egl` 
**Target path expression:**`"go.mod"` 
**EGX source:**`mde/generation/awspsm-to-artifacts/awspsm2artifacts.egx:58`

### Why this generation rule exists

The Go module is the dependency boundary for the generated runtime. Emitting it from the model context makes the generated project buildable as a coherent unit and gives later scripts a predictable module path rather than requiring each handler to invent one.

### When it runs

There is no explicit guard; the rule is eligible during every generation run. Its template may still render an empty or minimal artifact when the model contains no corresponding optional elements.

### Artifact ownership and regeneration behavior

The artifact is configured to **replace the generated artifact completely**.

Because this rule overwrites rather than merges, the target is a generated projection. Manual edits to it are not a durable customization mechanism.

Template parameters: `awsRoot`, `emitCtx`.

Artifact record emitted by the EGX rule:

- `go.mod / PACKAGE_MANIFEST / AWSPSM2ART_PackageManifest / OVERWRITE_GENERATED`

### How to troubleshoot or repair it

Verify that the AWS PSM root and the source objects expected by the template exist, then inspect the generated artifact record and the template parameters. If the file is incomplete, distinguish a model omission from a protected-region customization before changing the EGL template.

### Authoritative sources

The scheduling, guard, target path, merge policy, and artifact record are defined at `mde/generation/awspsm-to-artifacts/awspsm2artifacts.egx:58`. The rendered content is defined by `infrastructure/go-mod.egl` under `mde/generation/awspsm-to-artifacts/templates/`.

---

## `Gitignore`

**Source context:** ``—`shared AWS PSM generation context` 
**Template:**`infrastructure/gitignore.egl` 
**Target path expression:**`".gitignore"` 
**EGX source:**`mde/generation/awspsm-to-artifacts/awspsm2artifacts.egx:74`

### Why this generation rule exists

Generated projects accumulate build output, local environment files, and deployment state that should not become source control inputs. This rule supplies a safe baseline while preserving a protected customization region for repository-specific exclusions.

### When it runs

There is no explicit guard; the rule is eligible during every generation run. Its template may still render an empty or minimal artifact when the model contains no corresponding optional elements.

### Artifact ownership and regeneration behavior

The artifact is configured to **replace the generated artifact completely**.

Because this rule overwrites rather than merges, the target is a generated projection. Manual edits to it are not a durable customization mechanism.

Protected regions recorded for this artifact: `gitignore`. These regions are the explicit human-ownership boundary.

Template parameters: `awsRoot`, `emitCtx`.

Artifact record emitted by the EGX rule:

- `.gitignore / GITIGNORE / AWSPSM2ART_Gitignore / PRESERVE_PROTECTED_REGIONS`

### How to troubleshoot or repair it

Verify that the AWS PSM root and the source objects expected by the template exist, then inspect the generated artifact record and the template parameters. If the file is incomplete, distinguish a model omission from a protected-region customization before changing the EGL template.

### Authoritative sources

The scheduling, guard, target path, merge policy, and artifact record are defined at `mde/generation/awspsm-to-artifacts/awspsm2artifacts.egx:74`. The rendered content is defined by `infrastructure/gitignore.egl` under `mde/generation/awspsm-to-artifacts/templates/`.

---

## `StackToSamTemplate`

**Source context:** `stackObj` — `AWSPSMCORE!SamStack`  
**Template:** `infrastructure/sam-template.egl`  
**Target path expression:** `stackPathText`  
**EGX source:** `mde/generation/awspsm-to-artifacts/awspsm2artifacts.egx:100`

### Why this generation rule exists

Each modeled SAM stack becomes the deployable CloudFormation document for its resources. The guard avoids emitting empty templates, while merge/protected-region behavior lets the generator own modeled resources and leaves deliberate YAML extensions available to the deployment team.

### When it runs

The rule is conditional. A false guard is a deliberate omission boundary, not an empty generated file:

```egx
guard : stackObj.resources.notEmpty()
```

### Artifact ownership and regeneration behavior

The artifact is configured to **merge with generated content while preserving configured protected regions**.

Merge mode means regeneration is expected to revisit the same file. Do not place durable manual content outside the named protected regions if you expect it to survive future generation.

Protected regions recorded for this artifact: `yaml-extension`. These regions are the explicit human-ownership boundary.

Template parameters: `awsRoot`, `stackObj`, `emitCtx`.

### How to troubleshoot or repair it

First evaluate the guard: `stackObj.resources.notEmpty()`. If it is false, check the corresponding generation flag or PSM relationship before treating the missing artifact as a failure. If it should be true, verify the source object, its path helper, and the template parameters. Then inspect the generation report and artifact trace for this rule.

### Authoritative sources

The scheduling, guard, target path, merge policy, and artifact record are defined at `mde/generation/awspsm-to-artifacts/awspsm2artifacts.egx:100`. The rendered content is defined by `infrastructure/sam-template.egl` under `mde/generation/awspsm-to-artifacts/templates/`.

---

## `SamConfig`

**Source context:** ``—`shared AWS PSM generation context` 
**Template:**`infrastructure/samconfig.egl` 
**Target path expression:**`"samconfig.toml"` 
**EGX source:**`mde/generation/awspsm-to-artifacts/awspsm2artifacts.egx:122`

### Why this generation rule exists

samconfig.toml captures repeatable SAM CLI defaults without placing account-specific secrets in the model. Its merge behavior recognizes that teams often need local profile, region, or parameter overrides that should survive regeneration.

### When it runs

There is no explicit guard; the rule is eligible during every generation run. Its template may still render an empty or minimal artifact when the model contains no corresponding optional elements.

### Artifact ownership and regeneration behavior

The artifact is configured to **merge with generated content while preserving configured protected regions**.

Merge mode means regeneration is expected to revisit the same file. Do not place durable manual content outside the named protected regions if you expect it to survive future generation.

Protected regions recorded for this artifact: `samconfig-local-overrides`. These regions are the explicit human-ownership boundary.

Template parameters: `awsRoot`, `emitCtx`.

Artifact record emitted by the EGX rule:

- `samconfig.toml / SAM_CONFIG / AWSPSM2ART_SamConfig / PRESERVE_PROTECTED_REGIONS`

### How to troubleshoot or repair it

Verify that the AWS PSM root and the source objects expected by the template exist, then inspect the generated artifact record and the template parameters. If the file is incomplete, distinguish a model omission from a protected-region customization before changing the EGL template.

### Authoritative sources

The scheduling, guard, target path, merge policy, and artifact record are defined at `mde/generation/awspsm-to-artifacts/awspsm2artifacts.egx:122`. The rendered content is defined by `infrastructure/samconfig.egl` under `mde/generation/awspsm-to-artifacts/templates/`.

---

## `StageEnvironment`

**Source context:** `stageObj` — `AWSPSMCORE!AwsStage`  
**Template:** `infrastructure/env-json.egl`  
**Target path expression:** `stageObj.envPath()`  
**EGX source:** `mde/generation/awspsm-to-artifacts/awspsm2artifacts.egx:138`

### Why this generation rule exists

An AWS stage becomes an environment file because deployment and local tooling need a concrete, stage-specific view of account, region, names, and configuration. The target is derived from the stage itself, preventing dev/test/prod settings from collapsing into one shared file.

### When it runs

There is no explicit guard; the rule is eligible during every generation run. Its template may still render an empty or minimal artifact when the model contains no corresponding optional elements.

### Artifact ownership and regeneration behavior

The artifact is configured to **replace the generated artifact completely**.

Because this rule overwrites rather than merges, the target is a generated projection. Manual edits to it are not a durable customization mechanism.

Template parameters: `stageObj`, `emitCtx`.

### How to troubleshoot or repair it

Verify that the AWS PSM root and the source objects expected by the template exist, then inspect the generated artifact record and the template parameters. If the file is incomplete, distinguish a model omission from a protected-region customization before changing the EGL template.

### Authoritative sources

The scheduling, guard, target path, merge policy, and artifact record are defined at `mde/generation/awspsm-to-artifacts/awspsm2artifacts.egx:138`. The rendered content is defined by `infrastructure/env-json.egl` under `mde/generation/awspsm-to-artifacts/templates/`.

---

## `LocalEnvironmentFile`

**Source context:** ``—`shared AWS PSM generation context` 
**Template:**`infrastructure/default-env-json.egl` 
**Target path expression:**`"env/local.json"` 
**EGX source:**`mde/generation/awspsm-to-artifacts/awspsm2artifacts.egx:154`

### Why this generation rule exists

A local environment file is useful even when no local stage was modeled, but it must not overwrite an explicit modeled local stage. The guard makes this a non-invasive convenience artifact rather than a competing source of truth.

### When it runs

The rule is conditional. A false guard is a deliberate omission boundary, not an empty generated file:

```egx
guard : not AWSPSMCORE!AwsStage.all.exists(stageObj | stageObj.envPath() = "env/local.json")
```

### Artifact ownership and regeneration behavior

The artifact is configured to **replace the generated artifact completely**.

Because this rule overwrites rather than merges, the target is a generated projection. Manual edits to it are not a durable customization mechanism.

Template parameters: `stageNameText`, `environmentClassText`, `emitCtx`.

Artifact record emitted by the EGX rule:

- `env/local.json / ENVIRONMENT_CONFIG / AWSPSM2ART_Default_Local_Environment / OVERWRITE_GENERATED`

### How to troubleshoot or repair it

First evaluate the guard: `not AWSPSMCORE!AwsStage.all.exists(stageObj | stageObj.envPath() = "env/local.json")`. If it is false, check the corresponding generation flag or PSM relationship before treating the missing artifact as a failure. If it should be true, verify the source object, its path helper, and the template parameters. Then inspect the generation report and artifact trace for this rule.

### Authoritative sources

The scheduling, guard, target path, merge policy, and artifact record are defined at `mde/generation/awspsm-to-artifacts/awspsm2artifacts.egx:154`. The rendered content is defined by `infrastructure/default-env-json.egl` under `mde/generation/awspsm-to-artifacts/templates/`.

---

## `DevEnvironmentFile`

**Source context:** ``—`shared AWS PSM generation context` 
**Template:**`infrastructure/default-env-json.egl` 
**Target path expression:**`"env/dev.json"` 
**EGX source:**`mde/generation/awspsm-to-artifacts/awspsm2artifacts.egx:168`

### Why this generation rule exists

The development example gives the generated project a runnable starting point when the model has no dev stage. It is intentionally created only when absent, so an explicit stage remains authoritative.

### When it runs

The rule is conditional. A false guard is a deliberate omission boundary, not an empty generated file:

```egx
guard : not AWSPSMCORE!AwsStage.all.exists(stageObj | stageObj.envPath() = "env/dev.json")
```

### Artifact ownership and regeneration behavior

The artifact is configured to **replace the generated artifact completely**.

Because this rule overwrites rather than merges, the target is a generated projection. Manual edits to it are not a durable customization mechanism.

Template parameters: `stageNameText`, `environmentClassText`, `emitCtx`.

Artifact record emitted by the EGX rule:

- `env/dev.json / ENVIRONMENT_CONFIG / AWSPSM2ART_Default_Dev_Environment / OVERWRITE_GENERATED`

### How to troubleshoot or repair it

First evaluate the guard: `not AWSPSMCORE!AwsStage.all.exists(stageObj | stageObj.envPath() = "env/dev.json")`. If it is false, check the corresponding generation flag or PSM relationship before treating the missing artifact as a failure. If it should be true, verify the source object, its path helper, and the template parameters. Then inspect the generation report and artifact trace for this rule.

### Authoritative sources

The scheduling, guard, target path, merge policy, and artifact record are defined at `mde/generation/awspsm-to-artifacts/awspsm2artifacts.egx:168`. The rendered content is defined by `infrastructure/default-env-json.egl` under `mde/generation/awspsm-to-artifacts/templates/`.

---

## `TestEnvironmentFile`

**Source context:** ``—`shared AWS PSM generation context` 
**Template:**`infrastructure/default-env-json.egl` 
**Target path expression:**`"env/test.json"` 
**EGX source:**`mde/generation/awspsm-to-artifacts/awspsm2artifacts.egx:182`

### Why this generation rule exists

The test example prevents a new project from having no documented test configuration at all. The guard avoids replacing a modeled test environment with a generic example.

### When it runs

The rule is conditional. A false guard is a deliberate omission boundary, not an empty generated file:

```egx
guard : not AWSPSMCORE!AwsStage.all.exists(stageObj | stageObj.envPath() = "env/test.json")
```

### Artifact ownership and regeneration behavior

The artifact is configured to **replace the generated artifact completely**.

Because this rule overwrites rather than merges, the target is a generated projection. Manual edits to it are not a durable customization mechanism.

Template parameters: `stageNameText`, `environmentClassText`, `emitCtx`.

Artifact record emitted by the EGX rule:

- `env/test.json / ENVIRONMENT_CONFIG / AWSPSM2ART_Default_Test_Environment / OVERWRITE_GENERATED`

### How to troubleshoot or repair it

First evaluate the guard: `not AWSPSMCORE!AwsStage.all.exists(stageObj | stageObj.envPath() = "env/test.json")`. If it is false, check the corresponding generation flag or PSM relationship before treating the missing artifact as a failure. If it should be true, verify the source object, its path helper, and the template parameters. Then inspect the generation report and artifact trace for this rule.

### Authoritative sources

The scheduling, guard, target path, merge policy, and artifact record are defined at `mde/generation/awspsm-to-artifacts/awspsm2artifacts.egx:182`. The rendered content is defined by `infrastructure/default-env-json.egl` under `mde/generation/awspsm-to-artifacts/templates/`.

---

## `StagingEnvironmentFile`

**Source context:** ``—`shared AWS PSM generation context` 
**Template:**`infrastructure/default-env-json.egl` 
**Target path expression:**`"env/staging.json"` 
**EGX source:**`mde/generation/awspsm-to-artifacts/awspsm2artifacts.egx:196`

### Why this generation rule exists

Staging is often operationally useful even when the PSM models only dev, test, and prod. This rule supplies an example only when the model does not already describe that stage, keeping convenience subordinate to modeled deployment intent.

### When it runs

The rule is conditional. A false guard is a deliberate omission boundary, not an empty generated file:

```egx
guard : not AWSPSMCORE!AwsStage.all.exists(stageObj | stageObj.envPath() = "env/staging.json")
```

### Artifact ownership and regeneration behavior

The artifact is configured to **replace the generated artifact completely**.

Because this rule overwrites rather than merges, the target is a generated projection. Manual edits to it are not a durable customization mechanism.

Template parameters: `stageNameText`, `environmentClassText`, `emitCtx`.

Artifact record emitted by the EGX rule:

- `env/staging.json / ENVIRONMENT_CONFIG / AWSPSM2ART_Default_Staging_Environment / OVERWRITE_GENERATED`

### How to troubleshoot or repair it

First evaluate the guard: `not AWSPSMCORE!AwsStage.all.exists(stageObj | stageObj.envPath() = "env/staging.json")`. If it is false, check the corresponding generation flag or PSM relationship before treating the missing artifact as a failure. If it should be true, verify the source object, its path helper, and the template parameters. Then inspect the generation report and artifact trace for this rule.

### Authoritative sources

The scheduling, guard, target path, merge policy, and artifact record are defined at `mde/generation/awspsm-to-artifacts/awspsm2artifacts.egx:196`. The rendered content is defined by `infrastructure/default-env-json.egl` under `mde/generation/awspsm-to-artifacts/templates/`.

---

## `ProdExampleEnvironmentFile`

**Source context:** ``—`shared AWS PSM generation context` 
**Template:**`infrastructure/default-env-json.egl` 
**Target path expression:**`"env/prod.example.json"` 
**EGX source:**`mde/generation/awspsm-to-artifacts/awspsm2artifacts.egx:210`

### Why this generation rule exists

A production example communicates the required shape of production configuration without embedding a real account or secret. Its filename and guard make the distinction between a safe example and an authoritative production environment explicit.

### When it runs

The rule is conditional. A false guard is a deliberate omission boundary, not an empty generated file:

```egx
guard : not AWSPSMCORE!AwsStage.all.exists(stageObj | stageObj.envPath() = "env/prod.example.json")
```

### Artifact ownership and regeneration behavior

The artifact is configured to **replace the generated artifact completely**.

Because this rule overwrites rather than merges, the target is a generated projection. Manual edits to it are not a durable customization mechanism.

Template parameters: `stageNameText`, `environmentClassText`, `emitCtx`.

Artifact record emitted by the EGX rule:

- `env/prod.example.json / ENVIRONMENT_CONFIG / AWSPSM2ART_Default_Prod_Example_Environment / OVERWRITE_GENERATED`

### How to troubleshoot or repair it

First evaluate the guard: `not AWSPSMCORE!AwsStage.all.exists(stageObj | stageObj.envPath() = "env/prod.example.json")`. If it is false, check the corresponding generation flag or PSM relationship before treating the missing artifact as a failure. If it should be true, verify the source object, its path helper, and the template parameters. Then inspect the generation report and artifact trace for this rule.

### Authoritative sources

The scheduling, guard, target path, merge policy, and artifact record are defined at `mde/generation/awspsm-to-artifacts/awspsm2artifacts.egx:210`. The rendered content is defined by `infrastructure/default-env-json.egl` under `mde/generation/awspsm-to-artifacts/templates/`.

---

## `IamRationaleReport`

**Source context:** ``—`shared AWS PSM generation context` 
**Template:**`infrastructure/iam-policy-rationale.egl` 
**Target path expression:**`"generated/reports/iam-policy-rationale.md"` 
**EGX source:**`mde/generation/awspsm-to-artifacts/awspsm2artifacts.egx:902`

### Why this generation rule exists

IAM rationale is generated as a security review artifact rather than buried in a template. It explains why permissions exist, what resources they cover, and which least-privilege decisions still need attention.

### When it runs

There is no explicit guard; the rule is eligible during every generation run. Its template may still render an empty or minimal artifact when the model contains no corresponding optional elements.

### Artifact ownership and regeneration behavior

The artifact is configured to **replace the generated artifact completely**.

Because this rule overwrites rather than merges, the target is a generated projection. Manual edits to it are not a durable customization mechanism.

Template parameters: `awsRoot`, `emitCtx`.

Artifact record emitted by the EGX rule:

- `generated/reports/iam-policy-rationale.md / SECURITY_REPORT / AWSPSM2ART_IamRationaleReport / OVERWRITE_GENERATED`

### How to troubleshoot or repair it

Verify that the AWS PSM root and the source objects expected by the template exist, then inspect the generated artifact record and the template parameters. If the file is incomplete, distinguish a model omission from a protected-region customization before changing the EGL template.

### Authoritative sources

The scheduling, guard, target path, merge policy, and artifact record are defined at `mde/generation/awspsm-to-artifacts/awspsm2artifacts.egx:902`. The rendered content is defined by `infrastructure/iam-policy-rationale.egl` under `mde/generation/awspsm-to-artifacts/templates/`.

---

## Useful resources

- [AWS SAM documentation](https://docs.aws.amazon.com/serverless-application-model/latest/developerguide/what-is-sam.html)
- [AWS CloudFormation template reference](https://docs.aws.amazon.com/AWSCloudFormation/latest/UserGuide/template-reference.html)
