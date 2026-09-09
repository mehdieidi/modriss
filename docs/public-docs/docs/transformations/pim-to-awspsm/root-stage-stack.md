# PIM → AWS PSM — Root Stage Stack

The PSM root rules establish AWS deployment scope: one PIM root becomes one AWS PSM model, environments become stages, deployment units become SAM stacks, and service ownership becomes structured metadata. They give provider-specific rules a stage and stack context in which names, resources, policies, and readiness can be placed.

Source module: `mde/transformations/pim-to-awspsm/root-stage-stack.etl`.

## Reading this page

A transformation rule is not a validation constraint: it decides whether and how a source element contributes to the target model. Read the guard as a routing decision, the target table as the model-level result, the behavior section as the important semantic side effects, and the trace/manual-decision information as the hand-off to review and later phases.

---

## `PIMModel2AwsPsmModel`

**Source:** `p` — `PIM!PIMModel`  
**Target:** `a` — `AWSPSM!AwsPsmModel`  
**Source location:** `mde/transformations/pim-to-awspsm/root-stage-stack.etl:2`

### Why this rule exists

This rule creates the AWS deployment model and copies the PIM domain identity into the provider-specific root. It is the anchor for stage, stack, resource, trace, readiness, and generated-artifact relationships; without it the AWS objects would have no coherent deployment document.

### When the rule runs

There is no explicit guard, so every source instance of the declared type is eligible for this mapping. Eligibility does not guarantee that every optional relationship or downstream target can be resolved.

### What it creates

- `a` (`AWSPSM!AwsPsmModel`): Transformation root.

### Important behavior encoded in the rule

The rule directly assigns: `a.id`, `a.name`, `a.summary`, `a.partition`, `a.accountStrategy`, `a.regionStrategy`, `a.defaultRegion`, `a.namingConvention`, `a.taggingStrategy`, `a.productionMode`, `a.traceModel`, `a.readiness`, `a.namingPolicy`, `a.taggingPolicy`, `a.securityBaseline`, `a.samGlobals`.
Manual decisions raised by this rule: `AWS_REGION_REQUIRED`. These are intentional hand-off points, not transformation failures; resolve them in the model review/readiness workflow.

### How to troubleshoot or repair it

Start with the manual decision(s) `AWS_REGION_REQUIRED` and complete the requested provider or business choice. Then re-run the transformation and validate the resulting target. If the rule did not produce an object, inspect whether the pimmodel actually satisfies its guard and whether the required upstream correspondence exists.

### Authoritative source

Read the complete ETL rule at `mde/transformations/pim-to-awspsm/root-stage-stack.etl:2`. The documentation summarizes its purpose and observable effects; the ETL body remains the authority for exact assignments and helper calls.

---

## `Environment2AwsStage`

**Source:** `e` — `DEPLOYMENT!Environment`  
**Target:** `s` — `AWSPSMCORE!AwsStage`  
**Source location:** `mde/transformations/pim-to-awspsm/root-stage-stack.etl:50`

### Why this rule exists

A PIM environment becomes an AWS stage carrying account, region, production-like behavior, and approval posture. The mapping intentionally keeps deployment scope explicit because the same logical architecture may be deployed with materially different safeguards in dev and production.

### When the rule runs

There is no explicit guard, so every source instance of the declared type is eligible for this mapping. Eligibility does not guarantee that every optional relationship or downstream target can be resolved.

### What it creates

- `s` (`AWSPSMCORE!AwsStage`): Generated aws stage (s).

### Important behavior encoded in the rule

The rule directly assigns: `s.id`, `s.name`, `s.stageName`, `s.environmentClass`, `s.accountId`, `s.region`, `s.requiresManualApproval`, `s.confirmChangeset`, `s.failOnEmptyChangeset`, `s.stackNamePrefix`.

### How to troubleshoot or repair it

Verify that the environment is present and semantically complete, then rerun the transformation. If the target is missing or incomplete, inspect the source attributes named in the rule body, the referenced helper operation, and the post-phase that resolves its relationships before changing the ETL itself.

### Authoritative source

Read the complete ETL rule at `mde/transformations/pim-to-awspsm/root-stage-stack.etl:50`. The documentation summarizes its purpose and observable effects; the ETL body remains the authority for exact assignments and helper calls.

---

## `DeploymentUnit2SamStack`

**Source:** `d` — `DEPLOYMENT!DeploymentUnit`  
**Target:** `s` — `AWSPSMCORE!SamStack`  
**Source location:** `mde/transformations/pim-to-awspsm/root-stage-stack.etl:77`

### Why this rule exists

A provider-independent deployment unit becomes a SAM/CloudFormation stack. The rule transfers ownership and environment placement so later resource rules can be placed into a concrete stack instead of creating a flat list of AWS resources.

### When the rule runs

There is no explicit guard, so every source instance of the declared type is eligible for this mapping. Eligibility does not guarantee that every optional relationship or downstream target can be resolved.

### What it creates

- `s` (`AWSPSMCORE!SamStack`): Generated sam stack (s).

### Important behavior encoded in the rule

The rule directly assigns: `s.id`, `s.name`, `s.stackName`, `s.templatePath`, `s.templateDescription`, `s.useSamTransform`, `s.packageIndividually`, `s.validateWithSam`, `s.validateWithCfnLint`.

### How to troubleshoot or repair it

Verify that the deployment unit is present and semantically complete, then rerun the transformation. If the target is missing or incomplete, inspect the source attributes named in the rule body, the referenced helper operation, and the post-phase that resolves its relationships before changing the ETL itself.

### Authoritative source

Read the complete ETL rule at `mde/transformations/pim-to-awspsm/root-stage-stack.etl:77`. The documentation summarizes its purpose and observable effects; the ETL body remains the authority for exact assignments and helper calls.

---

## `ServerlessService2StructuredMetadata`

**Source:** `svc` — `DEPLOYMENT!ServerlessService`  
**Target:** `doc` — `KERNEL!StructuredDocument`  
**Source location:** `mde/transformations/pim-to-awspsm/root-stage-stack.etl:96`

### Why this rule exists

Service ownership is emitted as structured PSM metadata for generated documentation, traceability, and review. It does not create a deployable resource; it preserves the architectural boundary that explains why the resources belong together.

### When the rule runs

There is no explicit guard, so every source instance of the declared type is eligible for this mapping. Eligibility does not guarantee that every optional relationship or downstream target can be resolved.

### What it creates

- `doc` (`KERNEL!StructuredDocument`): Provider-side trace and documentation metadata.

### Important behavior encoded in the rule

The rule directly assigns: `doc.id`, `doc.name`, `doc.format`, `doc.content`.

### How to troubleshoot or repair it

Verify that the serverless service is present and semantically complete, then rerun the transformation. If the target is missing or incomplete, inspect the source attributes named in the rule body, the referenced helper operation, and the post-phase that resolves its relationships before changing the ETL itself.

### Authoritative source

Read the complete ETL rule at `mde/transformations/pim-to-awspsm/root-stage-stack.etl:96`. The documentation summarizes its purpose and observable effects; the ETL body remains the authority for exact assignments and helper calls.

---
