# AWS PSM validation: Core

AWS PSM core rules protect the deployment graph and CloudFormation/SAM safety model: stages and stacks must exist, logical IDs must be valid and unique, imports must be explicit, expressions must have compatible shapes, and production resources must carry the required governance metadata.

Source profile: `mde/validation/psm/rules/core.evl`.

## Reading these rules

Each entry preserves the actual EVL guard and check. Treat the guard as the applicability boundary. When it evaluates to false, EVL skips the rule. The diagnostic is the message emitted at runtime, and its final sentence usually gives the repository's recommended repair.

---

## `ModelHasStacks`

**Context:** `AWSPSM!AwsPsmModel`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/psm/rules/core.evl:9`

### Why this rule exists

The rule checks whether model has stacks. The aws psm model element provides the relevant evidence through stacks. At this level, the generated CloudFormation/SAM deployment graph is addressable, dependency-safe, and governed for production. Missing evidence would leave an unresolved choice in generated infrastructure. The rule prevents the following failure: AWS PSM model has no SAM stacks.

### When it applies

No guard is defined, so the check runs for every instance of this context in the validated model.

No guard expression is present. Every instance of the context is checked.

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.stacks.notEmpty()
```

This rule reads: `stacks`.

### Diagnostic and repair

> AWS PSM model has no SAM stacks. Fix: add at least one SamStack and place deployable AwsResource elements inside it.

**How to fix it:**

add at least one SamStack and place deployable AwsResource elements inside it.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---

## `ModelHasStages`

**Context:** `AWSPSM!AwsPsmModel`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/psm/rules/core.evl:15`

### Why this rule exists

The rule checks whether model has stages. The aws psm model element provides the relevant evidence through stages. At this level, the generated CloudFormation/SAM deployment graph is addressable, dependency-safe, and governed for production. Missing evidence would leave an unresolved choice in generated infrastructure. The rule prevents the following failure: AWS PSM model has no deployment stages.

### When it applies

No guard is defined, so the check runs for every instance of this context in the validated model.

No guard expression is present. Every instance of the context is checked.

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.stages.notEmpty()
```

This rule reads: `stages`.

### Diagnostic and repair

> AWS PSM model has no deployment stages. Fix: add at least one AwsStage with stageName, environmentClass, accountId, and region.

**How to fix it:**

add at least one AwsStage with stageName, environmentClass, accountId, and region.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---

## `ProductionModeHasProdStage`

**Context:** `AWSPSM!AwsPsmModel`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/psm/rules/core.evl:21`

### Why this rule exists

The rule checks whether production mode has prod stage. The aws psm model element provides the relevant evidence through production mode, stages, environment class. At this level, the generated CloudFormation/SAM deployment graph is addressable, dependency-safe, and governed for production. Missing evidence would leave an unresolved choice in generated infrastructure. The rule prevents the following failure: productionMode is true but no PROD stage exists.

### When it applies

The rule is considered only when the guard below is true. A false guard is intentional: it means this invariant is not relevant to the current variant or modeling situation.

```evl
guard : self.productionMode = true
```

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.stages.exists(stageItem | stageItem.environmentClass = AWSPSMENUMS!AwsEnvironmentClass#PROD)
```

This rule reads: `productionMode`, `stages`, `environmentClass`.

### Diagnostic and repair

> productionMode is true but no PROD stage exists. Fix: add an AwsStage with environmentClass PROD, or set productionMode to false for non-production models.

**How to fix it:**

add an AwsStage with environmentClass PROD, or set productionMode to false for non-production models.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---

## `UniqueStackNames`

**Context:** `AWSPSM!AwsPsmModel`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/psm/rules/core.evl:28`

### Why this rule exists

The rule checks whether unique stack names. The aws psm model element provides the relevant evidence through stacks, stack name. At this level, the generated CloudFormation/SAM deployment graph is addressable, dependency-safe, and governed for production. Missing evidence would leave an unresolved choice in generated infrastructure. The rule prevents the following failure: Two or more SAM stacks have the same stackName.

### When it applies

No guard is defined, so the check runs for every instance of this context in the validated model.

No guard expression is present. Every instance of the context is checked.

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.stacks.forAll(stackItem | self.stacks.one(otherStack | otherStack.stackName = stackItem.stackName))
```

This rule reads: `stacks`, `stackName`.

### Diagnostic and repair

> Two or more SAM stacks have the same stackName. Fix: rename stacks so each stackName is unique within the PSM model.

**How to fix it:**

rename stacks so each stackName is unique within the PSM model.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---

## `UniqueStageNames`

**Context:** `AWSPSM!AwsPsmModel`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/psm/rules/core.evl:35`

### Why this rule exists

The rule checks whether unique stage names. The aws psm model element provides the relevant evidence through stages, stage name. At this level, the generated CloudFormation/SAM deployment graph is addressable, dependency-safe, and governed for production. Missing evidence would leave an unresolved choice in generated infrastructure. The rule prevents the following failure: Two or more deployment stages have the same stageName.

### When it applies

No guard is defined, so the check runs for every instance of this context in the validated model.

No guard expression is present. Every instance of the context is checked.

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.stages.forAll(stageItem | self.stages.one(otherStage | otherStage.stageName = stageItem.stageName))
```

This rule reads: `stages`, `stageName`.

### Diagnostic and repair

> Two or more deployment stages have the same stageName. Fix: rename stages so each stageName is unique within the PSM model.

**How to fix it:**

rename stages so each stageName is unique within the PSM model.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---

## `DefaultRegionRecommended`

**Context:** `AWSPSM!AwsPsmModel`  
**Classification:** Advisory critique (warning)  
**Source:** `mde/validation/psm/rules/core.evl:42`

### Why this rule exists

The rule checks whether default region recommended. It examines default region. Within this part of the model, the generated CloudFormation/SAM deployment graph is addressable, dependency-safe, and governed for production. The gap is The model has no defaultRegion. A later transformation, generator, or reviewer would otherwise have to infer the missing decision.

### When it applies

No guard is defined, so the check runs for every instance of this context in the validated model.

No guard expression is present. Every instance of the context is checked.

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.defaultRegion.hasText()
```

This rule reads: `defaultRegion`.

### Diagnostic and repair

> The model has no defaultRegion. Fix: set defaultRegion, for example eu-central-1, or ensure every AwsStage declares an explicit region.

**How to fix it:**

set defaultRegion, for example eu-central-1, or ensure every AwsStage declares an explicit region.

A critique does not necessarily make the model invalid. It is a deliberate quality/readiness signal; disposition it by improving the model, documenting why it is acceptable, or carrying the decision into the review record.

---

## `StackResourcesExist`

**Context:** `AWSPSM!AwsPsmModel`  
**Classification:** Advisory critique (warning)  
**Source:** `mde/validation/psm/rules/core.evl:48`

### Why this rule exists

The rule checks whether stack resources exist. It examines stack resource set, stacks. Within this part of the model, the generated CloudFormation/SAM deployment graph is addressable, dependency-safe, and governed for production. The gap is AWS PSM model has stacks but no stack-contained resources. A later transformation, generator, or reviewer would otherwise have to infer the missing decision.

### When it applies

No guard is defined, so the check runs for every instance of this context in the validated model.

No guard expression is present. Every instance of the context is checked.

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.stackResourceSet().notEmpty() or self.stacks.isEmpty()
```

This rule reads: `stackResourceSet`, `stacks`.

### Diagnostic and repair

> AWS PSM model has stacks but no stack-contained resources. Fix: place generated resources under SamStack.resources.

**How to fix it:**

place generated resources under SamStack.resources.

A critique does not necessarily make the model invalid. It is a deliberate quality/readiness signal; disposition it by improving the model, documenting why it is acceptable, or carrying the decision into the review record.

---

## `StageHasAccountAndRegion`

**Context:** `AWSPSM!AwsStage`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/psm/rules/core.evl:58`

### Why this rule exists

The rule checks whether stage has account and region. The aws stage element provides the relevant evidence through account id, region, stage name. At this level, the generated CloudFormation/SAM deployment graph is addressable, dependency-safe, and governed for production. Missing evidence would leave an unresolved choice in generated infrastructure. The rule prevents the following failure: Stage is missing accountId or region.

### When it applies

No guard is defined, so the check runs for every instance of this context in the validated model.

No guard expression is present. Every instance of the context is checked.

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.accountId.hasText() and self.region.hasText()
```

This rule reads: `accountId`, `region`, `stageName`.

### Diagnostic and repair

> Stage is missing accountId or region. Fix: provide the AWS account ID and region for this deployment stage.

**How to fix it:**

provide the AWS account ID and region for this deployment stage.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---

## `ProdRequiresApproval`

**Context:** `AWSPSM!AwsStage`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/psm/rules/core.evl:64`

### Why this rule exists

The rule checks whether prod requires approval. The aws stage element provides the relevant evidence through environment class, requires manual approval, stage name. At this level, the generated CloudFormation/SAM deployment graph is addressable, dependency-safe, and governed for production. Missing evidence would leave an unresolved choice in generated infrastructure. The rule prevents the following failure: Production stage does not require manual approval.

### When it applies

The rule is considered only when the guard below is true. A false guard is intentional: it means this invariant is not relevant to the current variant or modeling situation.

```evl
guard : self.environmentClass = AWSPSMENUMS!AwsEnvironmentClass#PROD
```

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.requiresManualApproval = true
```

This rule reads: `environmentClass`, `requiresManualApproval`, `stageName`.

### Diagnostic and repair

> Production stage does not require manual approval. Fix: set requiresManualApproval to true for production deployments. Require manual approval for

**How to fix it:**

set requiresManualApproval to true for production deployments. Require manual approval for

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---

## `ProdShouldConfirmChangeset`

**Context:** `AWSPSM!AwsStage`  
**Classification:** Advisory critique (warning)  
**Source:** `mde/validation/psm/rules/core.evl:77`

### Why this rule exists

The rule checks whether prod should confirm changeset. It examines environment class, confirm changeset, stage name. Within this part of the model, the generated CloudFormation/SAM deployment graph is addressable, dependency-safe, and governed for production. The gap is Production stage does not confirm changesets. A later transformation, generator, or reviewer would otherwise have to infer the missing decision.

### When it applies

The rule is considered only when the guard below is true. A false guard is intentional: it means this invariant is not relevant to the current variant or modeling situation.

```evl
guard : self.environmentClass = AWSPSMENUMS!AwsEnvironmentClass#PROD
```

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.confirmChangeset = true
```

This rule reads: `environmentClass`, `confirmChangeset`, `stageName`.

### Diagnostic and repair

> Production stage does not confirm changesets. Fix: set confirmChangeset to true so operators review the CloudFormation changes before execution.

**How to fix it:**

set confirmChangeset to true so operators review the CloudFormation changes before execution.

A critique does not necessarily make the model invalid. It is a deliberate quality/readiness signal; disposition it by improving the model, documenting why it is acceptable, or carrying the decision into the review record.

---

## `StageDeploysAtLeastOneStack`

**Context:** `AWSPSM!AwsStage`  
**Classification:** Advisory critique (warning)  
**Source:** `mde/validation/psm/rules/core.evl:84`

### Why this rule exists

The rule checks whether stage deploys at least one stack. It examines deploys stacks, stage name. Within this part of the model, the generated CloudFormation/SAM deployment graph is addressable, dependency-safe, and governed for production. The gap is Stage does not deploy any stacks. A later transformation, generator, or reviewer would otherwise have to infer the missing decision.

### When it applies

No guard is defined, so the check runs for every instance of this context in the validated model.

No guard expression is present. Every instance of the context is checked.

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.deploysStacks.notEmpty()
```

This rule reads: `deploysStacks`, `stageName`.

### Diagnostic and repair

> Stage does not deploy any stacks. Fix: attach one or more SamStack instances to deploysStacks, or remove the unused stage.

**How to fix it:**

attach one or more SamStack instances to deploysStacks, or remove the unused stage.

A critique does not necessarily make the model invalid. It is a deliberate quality/readiness signal; disposition it by improving the model, documenting why it is acceptable, or carrying the decision into the review record.

---

## `DeployableStackHasResources`

**Context:** `AWSPSM!SamStack`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/psm/rules/core.evl:94`

### Why this rule exists

The rule checks whether deployable stack has resources. The sam stack element provides the relevant evidence through resources, stack name. At this level, the generated CloudFormation/SAM deployment graph is addressable, dependency-safe, and governed for production. Missing evidence would leave an unresolved choice in generated infrastructure. The rule prevents the following failure: SAM stack has no resources.

### When it applies

No guard is defined, so the check runs for every instance of this context in the validated model.

No guard expression is present. Every instance of the context is checked.

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.resources.notEmpty()
```

This rule reads: `resources`, `stackName`.

### Diagnostic and repair

> SAM stack has no resources. Fix: add at least one AwsResource to the stack before transformation/deployment.

**How to fix it:**

add at least one AwsResource to the stack before transformation/deployment.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---

## `StackHasResources`

**Context:** `AWSPSM!SamStack`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/psm/rules/core.evl:100`

### Why this rule exists

The rule checks whether stack has resources. The sam stack element provides the relevant evidence through resources, stack name. At this level, the generated CloudFormation/SAM deployment graph is addressable, dependency-safe, and governed for production. Missing evidence would leave an unresolved choice in generated infrastructure. The rule prevents the following failure: SAM stack has no resources.

### When it applies

No guard is defined, so the check runs for every instance of this context in the validated model.

No guard expression is present. Every instance of the context is checked.

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.resources.notEmpty()
```

This rule reads: `resources`, `stackName`.

### Diagnostic and repair

> SAM stack has no resources. Fix: add at least one AwsResource to the stack before transformation/deployment.

**How to fix it:**

add at least one AwsResource to the stack before transformation/deployment.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---

## `StackLogicalIdsAreUnique`

**Context:** `AWSPSM!SamStack`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/psm/rules/core.evl:106`

### Why this rule exists

The rule checks whether stack logical ids are unique. The sam stack element provides the relevant evidence through resources, stack logical id key, stack name. At this level, the generated CloudFormation/SAM deployment graph is addressable, dependency-safe, and governed for production. Missing evidence would leave an unresolved choice in generated infrastructure. The rule prevents the following failure: SAM stack contains duplicate resource logical IDs.

### When it applies

No guard is defined, so the check runs for every instance of this context in the validated model.

No guard expression is present. Every instance of the context is checked.

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.resources.forAll(resourceItem | not duplicateResourceLogicalIdKeys().includes(resourceItem.stackLogicalIdKey()))
```

This rule reads: `resources`, `stackLogicalIdKey`, `stackName`.

### Diagnostic and repair

> SAM stack contains duplicate resource logical IDs. Fix: rename the duplicate logicalId values; logical IDs must be unique per CloudFormation stack.

**How to fix it:**

rename the duplicate logicalId values; logical IDs must be unique per CloudFormation stack.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---

## `SamTransformRecommended`

**Context:** `AWSPSM!SamStack`  
**Classification:** Advisory critique (warning)  
**Source:** `mde/validation/psm/rules/core.evl:113`

### Why this rule exists

The rule checks whether sam transform recommended. It examines use sam transform, stack name. Within this part of the model, the generated CloudFormation/SAM deployment graph is addressable, dependency-safe, and governed for production. The gap is SAM stack does not enable the SAM transform. A later transformation, generator, or reviewer would otherwise have to infer the missing decision.

### When it applies

No guard is defined, so the check runs for every instance of this context in the validated model.

No guard expression is present. Every instance of the context is checked.

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.useSamTransform = true
```

This rule reads: `useSamTransform`, `stackName`.

### Diagnostic and repair

> SAM stack does not enable the SAM transform. Fix: set useSamTransform to true unless this stack intentionally emits plain CloudFormation only.

**How to fix it:**

set useSamTransform to true unless this stack intentionally emits plain CloudFormation only.

A critique does not necessarily make the model invalid. It is a deliberate quality/readiness signal; disposition it by improving the model, documenting why it is acceptable, or carrying the decision into the review record.

---

## `ValidationToolsRecommended`

**Context:** `AWSPSM!SamStack`  
**Classification:** Advisory critique (warning)  
**Source:** `mde/validation/psm/rules/core.evl:119`

### Why this rule exists

The rule checks whether validation tools recommended. It examines validate with sam, validate with cfn lint, stack name. Within this part of the model, the generated CloudFormation/SAM deployment graph is addressable, dependency-safe, and governed for production. The gap is SAM stack is not configured for both SAM and cfn-lint validation. A later transformation, generator, or reviewer would otherwise have to infer the missing decision.

### When it applies

No guard is defined, so the check runs for every instance of this context in the validated model.

No guard expression is present. Every instance of the context is checked.

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : (self.validateWithSam = true) and (self.validateWithCfnLint = true)
```

This rule reads: `validateWithSam`, `validateWithCfnLint`, `stackName`.

### Diagnostic and repair

> SAM stack is not configured for both SAM and cfn-lint validation. Fix: set validateWithSam and validateWithCfnLint to true in production pipelines.

**How to fix it:**

set validateWithSam and validateWithCfnLint to true in production pipelines.

A critique does not necessarily make the model invalid. It is a deliberate quality/readiness signal; disposition it by improving the model, documenting why it is acceptable, or carrying the decision into the review record.

---

## `LogicalIdValid`

**Context:** `AWSPSM!AwsResource`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/psm/rules/core.evl:129`

### Why this rule exists

The rule checks whether logical id valid. The aws resource element provides the relevant evidence through logical id. At this level, the generated CloudFormation/SAM deployment graph is addressable, dependency-safe, and governed for production. Missing evidence would leave an unresolved choice in generated infrastructure. The rule prevents the following failure: Resource logicalId " " is invalid.

### When it applies

No guard is defined, so the check runs for every instance of this context in the validated model.

No guard expression is present. Every instance of the context is checked.

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.logicalId.matches('^[A-Za-z][A-Za-z0-9]*$')
```

This rule reads: `logicalId`.

### Diagnostic and repair

> Resource logicalId " " is invalid. Fix: use only letters and digits, start with a letter, and avoid spaces, dashes, underscores, or punctuation.

**How to fix it:**

use only letters and digits, start with a letter, and avoid spaces, dashes, underscores, or punctuation.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---

## `LogicalIdUniqueInStack`

**Context:** `AWSPSM!AwsResource`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/psm/rules/core.evl:135`

### Why this rule exists

The rule checks whether logical id unique in stack. The aws resource element provides the relevant evidence through stack logical id key, logical id. At this level, the generated CloudFormation/SAM deployment graph is addressable, dependency-safe, and governed for production. Missing evidence would leave an unresolved choice in generated infrastructure. The rule prevents the following failure: Logical ID is duplicated within its deployment scope.

### When it applies

No guard is defined, so the check runs for every instance of this context in the validated model.

No guard expression is present. Every instance of the context is checked.

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : not duplicateResourceLogicalIdKeys().includes(self.stackLogicalIdKey())
```

This rule reads: `stackLogicalIdKey`, `logicalId`.

### Diagnostic and repair

> Logical ID is duplicated within its deployment scope. Fix: give each resource in a stack a unique logicalId.

**How to fix it:**

give each resource in a stack a unique logicalId.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---

## `NoDirectSelfDependency`

**Context:** `AWSPSM!AwsResource`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/psm/rules/core.evl:141`

### Why this rule exists

The rule checks whether no direct self dependency. The aws resource element provides the relevant evidence through depends on, resource label. At this level, the generated CloudFormation/SAM deployment graph is addressable, dependency-safe, and governed for production. Missing evidence would leave an unresolved choice in generated infrastructure. The rule prevents the following failure: Resource depends on itself.

### When it applies

No guard is defined, so the check runs for every instance of this context in the validated model.

No guard expression is present. Every instance of the context is checked.

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : not self.dependsOn.includes(self)
```

This rule reads: `dependsOn`, `resourceLabel`.

### Diagnostic and repair

> Resource depends on itself. Fix: remove the self-reference from dependsOn.

**How to fix it:**

remove the self-reference from dependsOn.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---

## `NoDependencyCycles`

**Context:** `AWSPSM!AwsResource`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/psm/rules/core.evl:147`

### Why this rule exists

The rule checks whether no dependency cycles. The aws resource element provides the relevant evidence through id, resource label. At this level, the generated CloudFormation/SAM deployment graph is addressable, dependency-safe, and governed for production. Missing evidence would leave an unresolved choice in generated infrastructure. The rule prevents the following failure: Resource is part of a circular dependency chain.

### When it applies

No guard is defined, so the check runs for every instance of this context in the validated model.

No guard expression is present. Every instance of the context is checked.

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : not resourcesWithDependencyCycles().includes(self.`id`)
```

This rule reads: `id`, `resourceLabel`.

### Diagnostic and repair

> Resource is part of a circular dependency chain. Fix: remove or refactor dependsOn links so the stack dependency graph is acyclic.

**How to fix it:**

remove or refactor dependsOn links so the stack dependency graph is acyclic.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---

## `ImportedResourceHasImportIdentity`

**Context:** `AWSPSM!AwsResource`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/psm/rules/core.evl:153`

### Why this rule exists

The rule checks whether imported resource has import identity. The aws resource element provides the relevant evidence through imported resource, imported arn, import details, resource label. At this level, the generated CloudFormation/SAM deployment graph is addressable, dependency-safe, and governed for production. Missing evidence would leave an unresolved choice in generated infrastructure. The rule prevents the following failure: Imported resource has no import identity.

### When it applies

The rule is considered only when the guard below is true. A false guard is intentional: it means this invariant is not relevant to the current variant or modeling situation.

```evl
guard : self.importedResource = true
```

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.importedArn.hasText() or (self.importDetails.isDefined() and (self.importDetails.importedArn.hasText() or self.importDetails.importedName.hasText() or self.importDetails.importedLogicalId.hasText()))
```

This rule reads: `importedResource`, `importedArn`, `importDetails`, `resourceLabel`.

### Diagnostic and repair

> Imported resource has no import identity. Fix: set importedArn or importDetails.importedArn/importedName/importedLogicalId so generators can reference the external AWS resource safely.

**How to fix it:**

set importedArn or importDetails.importedArn/importedName/importedLogicalId so generators can reference the external AWS resource safely.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---

## `NonImportedResourceShouldNotHaveImportMetadata`

**Context:** `AWSPSM!AwsResource`  
**Classification:** Advisory critique (warning)  
**Source:** `mde/validation/psm/rules/core.evl:162`

### Why this rule exists

The rule checks whether non imported resource should not have import metadata. It examines imported resource, imported arn, import details, resource label. Within this part of the model, the generated CloudFormation/SAM deployment graph is addressable, dependency-safe, and governed for production. The gap is Resource is not marked imported but contains import metadata. A later transformation, generator, or reviewer would otherwise have to infer the missing decision.

### When it applies

The rule is considered only when the guard below is true. A false guard is intentional: it means this invariant is not relevant to the current variant or modeling situation.

```evl
guard : self.importedResource <> true
```

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : (not self.importedArn.hasText()) and self.importDetails.isUndefined()
```

This rule reads: `importedResource`, `importedArn`, `importDetails`, `resourceLabel`.

### Diagnostic and repair

> Resource is not marked imported but contains import metadata. Fix: either set importedResource to true or remove importedArn/importDetails.

**How to fix it:**

either set importedResource to true or remove importedArn/importDetails.

A critique does not necessarily make the model invalid. It is a deliberate quality/readiness signal; disposition it by improving the model, documenting why it is acceptable, or carrying the decision into the review record.

---

## `DeployableResourceHasAwsType`

**Context:** `AWSPSM!AwsResource`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/psm/rules/core.evl:169`

### Why this rule exists

The rule checks whether deployable resource has aws type. The aws resource element provides the relevant evidence through imported resource, stack, aws resource type, resource label. At this level, the generated CloudFormation/SAM deployment graph is addressable, dependency-safe, and governed for production. Missing evidence would leave an unresolved choice in generated infrastructure. The rule prevents the following failure: Deployable resource has no awsResourceType.

### When it applies

The rule is considered only when the guard below is true. A false guard is intentional: it means this invariant is not relevant to the current variant or modeling situation.

```evl
guard : self.importedResource <> true and self.stack.isDefined()
```

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.awsResourceType.hasText()
```

This rule reads: `importedResource`, `stack`, `awsResourceType`, `resourceLabel`.

### Diagnostic and repair

> Deployable resource has no awsResourceType. Fix: set a valid CloudFormation/SAM resource type.

**How to fix it:**

set a valid CloudFormation/SAM resource type.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---

## `ProductionResourcesHaveRequiredTags`

**Context:** `AWSPSM!AwsResource`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/psm/rules/core.evl:176`

### Why this rule exists

The rule checks whether production resources have required tags. The aws resource element provides the relevant evidence through is production scoped, missing required tag keys, resource label. At this level, the generated CloudFormation/SAM deployment graph is addressable, dependency-safe, and governed for production. Missing evidence would leave an unresolved choice in generated infrastructure. The rule prevents the following failure: Production-scoped resource is missing required tags: ,.

### When it applies

The rule is considered only when the guard below is true. A false guard is intentional: it means this invariant is not relevant to the current variant or modeling situation.

```evl
guard : self.isProductionScoped()
```

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.missingRequiredTagKeys().isEmpty()
```

This rule reads: `isProductionScoped`, `missingRequiredTagKeys`, `resourceLabel`.

### Diagnostic and repair

> Production-scoped resource is missing required tags: , . Fix: add these tags to the resource, or update AwsTaggingPolicy if the requirement is not applicable.

**How to fix it:**

add these tags to the resource, or update AwsTaggingPolicy if the requirement is not applicable.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---

## `TagKeysAreUniquePerResource`

**Context:** `AWSPSM!AwsResource`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/psm/rules/core.evl:183`

### Why this rule exists

The rule checks whether tag keys are unique per resource. The aws resource element provides the relevant evidence through duplicate tag keys, resource label. At this level, the generated CloudFormation/SAM deployment graph is addressable, dependency-safe, and governed for production. Missing evidence would leave an unresolved choice in generated infrastructure. The rule prevents the following failure: Resource has duplicate tag keys: ,.

### When it applies

No guard is defined, so the check runs for every instance of this context in the validated model.

No guard expression is present. Every instance of the context is checked.

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.duplicateTagKeys().isEmpty()
```

This rule reads: `duplicateTagKeys`, `resourceLabel`.

### Diagnostic and repair

> Resource has duplicate tag keys: , . Fix: keep one tag per key and merge/remove duplicate values.

**How to fix it:**

keep one tag per key and merge/remove duplicate values.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---

## `ProductionResourcesShouldRetainOnDelete`

**Context:** `AWSPSM!AwsResource`  
**Classification:** Advisory critique (warning)  
**Source:** `mde/validation/psm/rules/core.evl:189`

### Why this rule exists

The rule checks whether production resources should retain on delete. It examines is production scoped, retain in production, deletion policy, resource label. Within this part of the model, the generated CloudFormation/SAM deployment graph is addressable, dependency-safe, and governed for production. The gap is Production-scoped resource has no retain/snapshot deletion safeguard. A later transformation, generator, or reviewer would otherwise have to infer the missing decision.

### When it applies

The rule is considered only when the guard below is true. A false guard is intentional: it means this invariant is not relevant to the current variant or modeling situation.

```evl
guard : self.isProductionScoped()
```

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : (self.retainInProduction = true) or (self.deletionPolicy = AWSPSMENUMS!CloudFormationDeletionPolicy#RETAIN) or (self.deletionPolicy = AWSPSMENUMS!CloudFormationDeletionPolicy#SNAPSHOT)
```

This rule reads: `isProductionScoped`, `retainInProduction`, `deletionPolicy`, `resourceLabel`.

### Diagnostic and repair

> Production-scoped resource has no retain/snapshot deletion safeguard. Fix: set retainInProduction to true or configure deletionPolicy RETAIN/SNAPSHOT where data loss would be harmful.

**How to fix it:**

set retainInProduction to true or configure deletionPolicy RETAIN/SNAPSHOT where data loss would be harmful.

A critique does not necessarily make the model invalid. It is a deliberate quality/readiness signal; disposition it by improving the model, documenting why it is acceptable, or carrying the decision into the review record.

---

## `NativeResourceTypeNameValid`

**Context:** `AWSPSM!AwsNativeResource`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/psm/rules/core.evl:199`

### Why this rule exists

The rule checks whether native resource type name valid. The aws native resource element provides the relevant evidence through cloud formation type, resource label. At this level, the generated CloudFormation/SAM deployment graph is addressable, dependency-safe, and governed for production. Missing evidence would leave an unresolved choice in generated infrastructure. The rule prevents the following failure: Native resource has invalid cloudFormationType.

### When it applies

No guard is defined, so the check runs for every instance of this context in the validated model.

No guard expression is present. Every instance of the context is checked.

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.cloudFormationType.matches('^(AWS::[A-Za-z0-9]+::[A-Za-z0-9]+|Custom::[A-Za-z0-9][A-Za-z0-9_-]*)$')
```

This rule reads: `cloudFormationType`, `resourceLabel`.

### Diagnostic and repair

> Native resource has invalid cloudFormationType. Fix: use AWS::Service::Resource or Custom::Name.

**How to fix it:**

use AWS::Service::Resource or Custom::Name.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---

## `ValueExpressionSourceShape`

**Context:** `AWSPSM!ValueExpression`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/psm/rules/core.evl:208`

### Why this rule exists

The rule checks whether value expression source shape. The value expression element provides the relevant evidence through source kind, literal, resource, parameter, attribute name. At this level, the generated CloudFormation/SAM deployment graph is addressable, dependency-safe, and governed for production. Missing evidence would leave an unresolved choice in generated infrastructure. The rule prevents the following failure: ValueExpression has fields that do not match sourceKind. Fix the literal/resource/attribute/reference shape.

### When it applies

No guard is defined, so the check runs for every instance of this context in the validated model.

No guard expression is present. Every instance of the context is checked.

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : (self.sourceKind = AWSPSMENUMS!ValueSourceKind#PLAINTEXT and self.literal.hasText() and self.resource.isUndefined()) or (self.sourceKind = AWSPSMENUMS!ValueSourceKind#CLOUDFORMATION_REF and (self.resource.isDefined() or self.parameter.isDefined()) and not self.attributeName.hasText()) or (self.sourceKind = AWSPSMENUMS!ValueSourceKind#CLOUDFORMATION_GETATT and self.resource.isDefined() and self.attributeName.hasText()) or (self.sourceKind = AWSPSMENUMS!ValueSourceKind#RESOURCE_ATTRIBUTE and self.resource.isDefined() and self.attributeName.hasText()) or (self.sourceKind = AWSPSMENUMS!ValueSourceKind#SSM_PARAMETER_REFERENCE and (self.expression.hasText() or self.literal.hasText())) or (self.sourceKind = AWSPSMENUMS!ValueSourceKind#SSM_SECURE_REFERENCE and (self.expression.hasText() or self.literal.hasText())) or (self.sourceKind = AWSPSMENUMS!ValueSourceKind#SECRETS_MANAGER_REFERENCE and (self.expression.hasText() or self.literal.hasText())) or (self.sourceKind = AWSPSMENUMS!ValueSourceKind#DYNAMIC_REFERENCE and (self.expression.hasText() or self.literal.hasText())) or (self.sourceKind = AWSPSMENUMS!ValueSourceKind#CLOUDFORMATION_SUB and (self.expression.hasText() or self.literal.hasText())) or (self.sourceKind = AWSPSMENUMS!ValueSourceKind#IMPORT_VALUE and (self.expression.hasText() or self.literal.hasText())) or (self.sourceKind = AWSPSMENUMS!ValueSourceKind#LIST and self.items.notEmpty()) or (self.sourceKind = AWSPSMENUMS!ValueSourceKind#MAP and self.entries.notEmpty())
```

This rule reads: `sourceKind`, `literal`, `resource`, `parameter`, `attributeName`, `expression`, `items`, `entries`, `labelText`.

### Diagnostic and repair

> ValueExpression has fields that do not match sourceKind. Fix the literal/resource/attribute/reference shape.

**How to fix it:**

Make the model satisfy the check shown above. Start with the referenced features, then re-run the appropriate EVL profile; do not silence the diagnostic by changing an unrelated display field.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---

## `SecretLiteralReviewed`

**Context:** `AWSPSM!ValueExpression`  
**Classification:** Advisory critique (warning)  
**Source:** `mde/validation/psm/rules/core.evl:226`

### Why this rule exists

The rule checks whether secret literal reviewed. It examines secret, source kind, review status. Within this part of the model, the generated CloudFormation/SAM deployment graph is addressable, dependency-safe, and governed for production. The gap is Secret ValueExpression uses PLAINTEXT without reviewStatus. Suggested fix: use Secrets Manager/SSM secure references or document test-only plaintext use. A later transformation, generator, or reviewer would otherwise have to infer the missing decision.

### When it applies

The rule is considered only when the guard below is true. A false guard is intentional: it means this invariant is not relevant to the current variant or modeling situation.

```evl
guard : self.secret = true
```

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.sourceKind <> AWSPSMENUMS!ValueSourceKind#PLAINTEXT or self.reviewStatus.hasText()
```

This rule reads: `secret`, `sourceKind`, `reviewStatus`.

### Diagnostic and repair

> Secret ValueExpression uses PLAINTEXT without reviewStatus. Suggested fix: use Secrets Manager/SSM secure references or document test-only plaintext use.

**How to fix it:**

This is advisory rather than a structural blocker. Decide whether the modeled situation genuinely needs the recommendation; if it does, change the referenced model features so the check evaluates to true and record the rationale when the omission is intentional.

A critique does not necessarily make the model invalid. It is a deliberate quality/readiness signal; disposition it by improving the model, documenting why it is acceptable, or carrying the decision into the review record.

---

## `TagKeyHasText`

**Context:** `AWSPSM!AwsTag`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/psm/rules/core.evl:236`

### Why this rule exists

The rule checks whether tag key has text. The aws tag element provides the relevant evidence through key. At this level, the generated CloudFormation/SAM deployment graph is addressable, dependency-safe, and governed for production. Missing evidence would leave an unresolved choice in generated infrastructure. The rule prevents the following failure: A tag has an empty key.

### When it applies

No guard is defined, so the check runs for every instance of this context in the validated model.

No guard expression is present. Every instance of the context is checked.

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.key.hasText()
```

This rule reads: `key`.

### Diagnostic and repair

> A tag has an empty key. Fix: provide a non-empty tag key such as Owner, Environment, CostCenter, Service, or DataClassification.

**How to fix it:**

provide a non-empty tag key such as Owner, Environment, CostCenter, Service, or DataClassification.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---

## `AvoidAwsReservedTagPrefix`

**Context:** `AWSPSM!AwsTag`  
**Classification:** Advisory critique (warning)  
**Source:** `mde/validation/psm/rules/core.evl:242`

### Why this rule exists

The rule checks whether avoid aws reserved tag prefix. It examines key. Within this part of the model, the generated CloudFormation/SAM deployment graph is addressable, dependency-safe, and governed for production. The gap is Tag key uses the AWS-reserved aws: prefix. A later transformation, generator, or reviewer would otherwise have to infer the missing decision.

### When it applies

No guard is defined, so the check runs for every instance of this context in the validated model.

No guard expression is present. Every instance of the context is checked.

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : not self.key.toLowerCase().startsWith('aws:')
```

This rule reads: `key`.

### Diagnostic and repair

> Tag key uses the AWS-reserved aws: prefix. Fix: use an application-owned tag namespace instead, for example app:owner or Owner.

**How to fix it:**

use an application-owned tag namespace instead, for example app:owner or Owner.

A critique does not necessarily make the model invalid. It is a deliberate quality/readiness signal; disposition it by improving the model, documenting why it is acceptable, or carrying the decision into the review record.

---

## `RequiredNativePropertyHasValue`

**Context:** `AWSPSM!NativeProperty`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/psm/rules/core.evl:251`

### Why this rule exists

The rule checks whether required native property has value. The native property element provides the relevant evidence through required, value, property name. At this level, the generated CloudFormation/SAM deployment graph is addressable, dependency-safe, and governed for production. Missing evidence would leave an unresolved choice in generated infrastructure. The rule prevents the following failure: Required native property has no value.

### When it applies

The rule is considered only when the guard below is true. A false guard is intentional: it means this invariant is not relevant to the current variant or modeling situation.

```evl
guard : self.required = true
```

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.value.isDefined()
```

This rule reads: `required`, `value`, `propertyName`.

### Diagnostic and repair

> Required native property has no value. Fix: provide a ValueExpression for this property or mark it as not required if it is intentionally omitted.

**How to fix it:**

provide a ValueExpression for this property or mark it as not required if it is intentionally omitted.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---

## `SecretNativePropertyUsesSecureExpression`

**Context:** `AWSPSM!NativeProperty`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/psm/rules/core.evl:258`

### Why this rule exists

The rule checks whether secret native property uses secure expression. The native property element provides the relevant evidence through secret, value, property name. At this level, the generated CloudFormation/SAM deployment graph is addressable, dependency-safe, and governed for production. Missing evidence would leave an unresolved choice in generated infrastructure. The rule prevents the following failure: Secret native property is not backed by a secure value expression.

### When it applies

The rule is considered only when the guard below is true. A false guard is intentional: it means this invariant is not relevant to the current variant or modeling situation.

```evl
guard : self.secret = true
```

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.value.isDefined() and self.value.secret = true and self.value.usesSecureSource()
```

This rule reads: `secret`, `value`, `propertyName`.

### Diagnostic and repair

> Secret native property is not backed by a secure value expression. Fix: mark value.secret true and use SSM_SECURE_REFERENCE, SECRETS_MANAGER_REFERENCE, or DYNAMIC_REFERENCE.

**How to fix it:**

mark value.secret true and use SSM_SECURE_REFERENCE, SECRETS_MANAGER_REFERENCE, or DYNAMIC_REFERENCE.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---

## `PlainTextHasLiteral`

**Context:** `AWSPSM!ValueExpression`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/psm/rules/core.evl:268`

### Why this rule exists

The rule checks whether plain text has literal. The value expression element provides the relevant evidence through source kind, literal. At this level, the generated CloudFormation/SAM deployment graph is addressable, dependency-safe, and governed for production. Missing evidence would leave an unresolved choice in generated infrastructure. The rule prevents the following failure: PLAINTEXT value expression has no literal.

### When it applies

The rule is considered only when the guard below is true. A false guard is intentional: it means this invariant is not relevant to the current variant or modeling situation.

```evl
guard : self.sourceKind = AWSPSMENUMS!ValueSourceKind#PLAINTEXT
```

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.literal.hasText()
```

This rule reads: `sourceKind`, `literal`.

### Diagnostic and repair

> PLAINTEXT value expression has no literal. Fix: set literal to the intended non-secret value, or use a reference sourceKind if the value is resolved elsewhere.

**How to fix it:**

set literal to the intended non-secret value, or use a reference sourceKind if the value is resolved elsewhere.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---

## `CloudFormationRefHasTarget`

**Context:** `AWSPSM!ValueExpression`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/psm/rules/core.evl:275`

### Why this rule exists

The rule checks whether cloud formation ref has target. The value expression element provides the relevant evidence through source kind, resource, parameter, expression. At this level, the generated CloudFormation/SAM deployment graph is addressable, dependency-safe, and governed for production. Missing evidence would leave an unresolved choice in generated infrastructure. The rule prevents the following failure: CLOUDFORMATION_REF value expression has no target.

### When it applies

The rule is considered only when the guard below is true. A false guard is intentional: it means this invariant is not relevant to the current variant or modeling situation.

```evl
guard : self.sourceKind = AWSPSMENUMS!ValueSourceKind#CLOUDFORMATION_REF
```

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.resource.isDefined() or self.parameter.isDefined() or self.expression.hasText()
```

This rule reads: `sourceKind`, `resource`, `parameter`, `expression`.

### Diagnostic and repair

> CLOUDFORMATION_REF value expression has no target. Fix: set resource, parameter, or expression so the generator can emit a valid Ref.

**How to fix it:**

set resource, parameter, or expression so the generator can emit a valid Ref.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---

## `GetAttHasResourceAndAttribute`

**Context:** `AWSPSM!ValueExpression`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/psm/rules/core.evl:282`

### Why this rule exists

The rule checks whether get att has resource and attribute. The value expression element provides the relevant evidence through source kind, resource, attribute name. At this level, the generated CloudFormation/SAM deployment graph is addressable, dependency-safe, and governed for production. Missing evidence would leave an unresolved choice in generated infrastructure. The rule prevents the following failure: CLOUDFORMATION_GETATT value expression requires both resource and attributeName.

### When it applies

The rule is considered only when the guard below is true. A false guard is intentional: it means this invariant is not relevant to the current variant or modeling situation.

```evl
guard : self.sourceKind = AWSPSMENUMS!ValueSourceKind#CLOUDFORMATION_GETATT
```

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.resource.isDefined() and self.attributeName.hasText()
```

This rule reads: `sourceKind`, `resource`, `attributeName`.

### Diagnostic and repair

> CLOUDFORMATION_GETATT value expression requires both resource and attributeName. Fix: select the referenced AwsResource and set attributeName, such as Arn or Name.

**How to fix it:**

select the referenced AwsResource and set attributeName, such as Arn or Name.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---

## `ListExpressionHasItems`

**Context:** `AWSPSM!ValueExpression`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/psm/rules/core.evl:289`

### Why this rule exists

The rule checks whether list expression has items. The value expression element provides the relevant evidence through source kind, items. At this level, the generated CloudFormation/SAM deployment graph is addressable, dependency-safe, and governed for production. Missing evidence would leave an unresolved choice in generated infrastructure. The rule prevents the following failure: LIST value expression has no items.

### When it applies

The rule is considered only when the guard below is true. A false guard is intentional: it means this invariant is not relevant to the current variant or modeling situation.

```evl
guard : self.sourceKind = AWSPSMENUMS!ValueSourceKind#LIST
```

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.items.notEmpty()
```

This rule reads: `sourceKind`, `items`.

### Diagnostic and repair

> LIST value expression has no items. Fix: add one or more child ValueExpression items, or change sourceKind if a list is not intended.

**How to fix it:**

add one or more child ValueExpression items, or change sourceKind if a list is not intended.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---

## `MapExpressionHasEntries`

**Context:** `AWSPSM!ValueExpression`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/psm/rules/core.evl:296`

### Why this rule exists

The rule checks whether map expression has entries. The value expression element provides the relevant evidence through source kind, entries. At this level, the generated CloudFormation/SAM deployment graph is addressable, dependency-safe, and governed for production. Missing evidence would leave an unresolved choice in generated infrastructure. The rule prevents the following failure: MAP value expression has no entries.

### When it applies

The rule is considered only when the guard below is true. A false guard is intentional: it means this invariant is not relevant to the current variant or modeling situation.

```evl
guard : self.sourceKind = AWSPSMENUMS!ValueSourceKind#MAP
```

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.entries.notEmpty()
```

This rule reads: `sourceKind`, `entries`.

### Diagnostic and repair

> MAP value expression has no entries. Fix: add NamedValueExpression entries with keys and values, or change sourceKind if a map is not intended.

**How to fix it:**

add NamedValueExpression entries with keys and values, or change sourceKind if a map is not intended.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---

## `SecretValueMustUseSecureReference`

**Context:** `AWSPSM!ValueExpression`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/psm/rules/core.evl:303`

### Why this rule exists

The rule checks whether secret value must use secure reference. The value expression element provides the relevant evidence through secret, uses secure source, source kind. At this level, the generated CloudFormation/SAM deployment graph is addressable, dependency-safe, and governed for production. Missing evidence would leave an unresolved choice in generated infrastructure. The rule prevents the following failure: ValueExpression is marked secret but sourceKind is.

### When it applies

The rule is considered only when the guard below is true. A false guard is intentional: it means this invariant is not relevant to the current variant or modeling situation.

```evl
guard : self.secret = true
```

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.usesSecureSource()
```

This rule reads: `secret`, `usesSecureSource`, `sourceKind`.

### Diagnostic and repair

> ValueExpression is marked secret but sourceKind is . Fix: use SECRETS_MANAGER_REFERENCE, SSM_SECURE_REFERENCE, or DYNAMIC_REFERENCE; do not store secrets as PLAINTEXT.

**How to fix it:**

use SECRETS_MANAGER_REFERENCE, SSM_SECURE_REFERENCE, or DYNAMIC_REFERENCE; do not store secrets as PLAINTEXT.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---

## `MapEntryHasKey`

**Context:** `AWSPSM!NamedValueExpression`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/psm/rules/core.evl:313`

### Why this rule exists

The rule checks whether map entry has key. The named value expression element provides the relevant evidence through key. At this level, the generated CloudFormation/SAM deployment graph is addressable, dependency-safe, and governed for production. Missing evidence would leave an unresolved choice in generated infrastructure. The rule prevents the following failure: A map entry has no key.

### When it applies

No guard is defined, so the check runs for every instance of this context in the validated model.

No guard expression is present. Every instance of the context is checked.

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.key.hasText()
```

This rule reads: `key`.

### Diagnostic and repair

> A map entry has no key. Fix: provide a stable non-empty key for the NamedValueExpression.

**How to fix it:**

provide a stable non-empty key for the NamedValueExpression.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---
