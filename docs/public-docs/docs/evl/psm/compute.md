# AWS PSM validation: Compute

Lambda rules check the parts of a function that AWS cannot safely infer: execution role, code location, runtime/handler pairing, package form, resource limits, environment-secret handling, event-source bounds, destinations, logging, and production safeguards.

Source profile: `mde/validation/psm/rules/compute.evl`.

## Reading these rules

Each entry preserves the actual EVL guard and check. Read the guard as the applicability boundary, not as part of the invariant: when it is false, the rule is intentionally skipped. The diagnostic is the runtime-facing message emitted by EVL; its final sentence usually contains the repository's recommended repair.

---

## `LambdaHasExecutionRole`

**Context:** `AWSPSM!AwsLambdaFunction`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/psm/rules/compute.evl:9`

### Why this rule exists

Lambda's execution role is both a deployment requirement and the boundary for what the function may do. A missing role means the function cannot run and its permission design cannot be reviewed.

### When it applies

The rule has no guard, so it applies to every instance of this context in the validated model.

There is no guard expression; every instance of the context is checked.

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.role.isDefined()
```

This rule reads: `role`, `resourceLabel`.

### Diagnostic and repair

> Lambda has no execution role. Fix: attach an IamRole that Lambda can assume.

**How to fix it:**

attach an IamRole that Lambda can assume.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---

## `LambdaHasLogGroup`

**Context:** `AWSPSM!AwsLambdaFunction`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/psm/rules/compute.evl:15`

### Why this rule exists

Checks that lambda has log group. The aws lambda function element owns the evidence for this decision, including log group, resource label. At this level, Lambda deployment settings agree with the chosen package type, runtime, permissions, limits, event source, and production posture; allowing the model through without that evidence would move an unresolved choice into generated infrastructure. The concrete failure this rule prevents is: Lambda has no logGroup.

### When it applies

The rule has no guard, so it applies to every instance of this context in the validated model.

There is no guard expression; every instance of the context is checked.

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.logGroup.isDefined()
```

This rule reads: `logGroup`, `resourceLabel`.

### Diagnostic and repair

> Lambda has no logGroup. Fix: attach the CloudWatchLogGroup used for function logs.

**How to fix it:**

attach the CloudWatchLogGroup used for function logs.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---

## `LambdaHasCodeConfig`

**Context:** `AWSPSM!AwsLambdaFunction`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/psm/rules/compute.evl:21`

### Why this rule exists

A Lambda resource without code configuration has no executable artifact. Requiring one code source keeps deployment generation from producing an infrastructure object that can never be invoked successfully.

### When it applies

The rule has no guard, so it applies to every instance of this context in the validated model.

There is no guard expression; every instance of the context is checked.

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.code.isDefined()
```

This rule reads: `code`, `resourceLabel`.

### Diagnostic and repair

> Lambda has no code configuration. Fix: attach LambdaZipCodeConfig or LambdaImageCodeConfig.

**How to fix it:**

attach LambdaZipCodeConfig or LambdaImageCodeConfig.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---

## `LambdaHasSupportedCodeConfig`

**Context:** `AWSPSM!AwsLambdaFunction`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/psm/rules/compute.evl:27`

### Why this rule exists

The PSM supports specific code-source forms, and each form has different required properties. This rule rejects a structurally shaped but semantically unsupported combination before CloudFormation does.

### When it applies

The rule has no guard, so it applies to every instance of this context in the validated model.

There is no guard expression; every instance of the context is checked.

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.code.isKindOf(AWSPSM!LambdaZipCodeConfig) or self.code.isKindOf(AWSPSM!LambdaImageCodeConfig)
```

This rule reads: `code`, `resourceLabel`.

### Diagnostic and repair

> Lambda uses an unsupported code configuration. Fix: use LambdaZipCodeConfig for ZIP functions or LambdaImageCodeConfig for container-image functions.

**How to fix it:**

use LambdaZipCodeConfig for ZIP functions or LambdaImageCodeConfig for container-image functions.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---

## `LambdaPackageTypeMatchesCodeConfig`

**Context:** `AWSPSM!AwsLambdaFunction`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/psm/rules/compute.evl:33`

### Why this rule exists

Zip and container-image Lambdas are packaged and deployed differently. The package type must agree with the code object so the generated resource does not ask AWS to interpret one artifact as another.

### When it applies

The rule has no guard, so it applies to every instance of this context in the validated model.

There is no guard expression; every instance of the context is checked.

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.packageType.isUndefined() or self.code.isUndefined() or (self.code.isKindOf(AWSPSM!LambdaZipCodeConfig) and self.packageType = AWSPSMENUMS!PackageType#ZIP) or (self.code.isKindOf(AWSPSM!LambdaImageCodeConfig) and self.packageType = AWSPSMENUMS!PackageType#IMAGE)
```

This rule reads: `packageType`, `code`, `resourceLabel`.

### Diagnostic and repair

> Lambda packageType does not match its code configuration. Fix: use ZIP for LambdaZipCodeConfig and IMAGE for LambdaImageCodeConfig, or omit packageType when it is derived by generators.

**How to fix it:**

use ZIP for LambdaZipCodeConfig and IMAGE for LambdaImageCodeConfig, or omit packageType when it is derived by generators.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---

## `LambdaMemoryRange`

**Context:** `AWSPSM!AwsLambdaFunction`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/psm/rules/compute.evl:41`

### Why this rule exists

Checks that lambda memory range. The aws lambda function element owns the evidence for this decision, including memory size mb, resource label. At this level, Lambda deployment settings agree with the chosen package type, runtime, permissions, limits, event source, and production posture; allowing the model through without that evidence would move an unresolved choice into generated infrastructure. The concrete failure this rule prevents is: Lambda memorySizeMb is outside the valid range 128..10240.

### When it applies

The rule has no guard, so it applies to every instance of this context in the validated model.

There is no guard expression; every instance of the context is checked.

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : (self.memorySizeMb.isUndefined()) or ((self.memorySizeMb >= 128) and (self.memorySizeMb <= 10240))
```

This rule reads: `memorySizeMb`, `resourceLabel`.

### Diagnostic and repair

> Lambda memorySizeMb is outside the valid range 128..10240. Fix: set memorySizeMb to a valid Lambda memory value in MB.

**How to fix it:**

set memorySizeMb to a valid Lambda memory value in MB.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---

## `LambdaTimeoutRange`

**Context:** `AWSPSM!AwsLambdaFunction`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/psm/rules/compute.evl:47`

### Why this rule exists

Checks that lambda timeout range. The aws lambda function element owns the evidence for this decision, including timeout seconds, resource label. At this level, Lambda deployment settings agree with the chosen package type, runtime, permissions, limits, event source, and production posture; allowing the model through without that evidence would move an unresolved choice into generated infrastructure. The concrete failure this rule prevents is: Lambda timeoutSeconds is outside the valid range 1..900.

### When it applies

The rule has no guard, so it applies to every instance of this context in the validated model.

There is no guard expression; every instance of the context is checked.

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : (self.timeoutSeconds.isUndefined()) or ((self.timeoutSeconds >= 1) and (self.timeoutSeconds <= 900))
```

This rule reads: `timeoutSeconds`, `resourceLabel`.

### Diagnostic and repair

> Lambda timeoutSeconds is outside the valid range 1..900. Fix: choose a timeout from 1 second to 15 minutes.

**How to fix it:**

choose a timeout from 1 second to 15 minutes.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---

## `LambdaEphemeralStorageRange`

**Context:** `AWSPSM!AwsLambdaFunction`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/psm/rules/compute.evl:53`

### Why this rule exists

Checks that lambda ephemeral storage range. The aws lambda function element owns the evidence for this decision, including ephemeral storage mb, resource label. At this level, Lambda deployment settings agree with the chosen package type, runtime, permissions, limits, event source, and production posture; allowing the model through without that evidence would move an unresolved choice into generated infrastructure. The concrete failure this rule prevents is: Lambda ephemeralStorageMb is outside the valid range 512..10240.

### When it applies

The rule has no guard, so it applies to every instance of this context in the validated model.

There is no guard expression; every instance of the context is checked.

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : (self.ephemeralStorageMb.isUndefined()) or ((self.ephemeralStorageMb >= 512) and (self.ephemeralStorageMb <= 10240))
```

This rule reads: `ephemeralStorageMb`, `resourceLabel`.

### Diagnostic and repair

> Lambda ephemeralStorageMb is outside the valid range 512..10240. Fix: set /tmp storage to a valid MB value.

**How to fix it:**

set /tmp storage to a valid MB value.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---

## `ReservedConcurrencyNonNegative`

**Context:** `AWSPSM!AwsLambdaFunction`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/psm/rules/compute.evl:59`

### Why this rule exists

Checks that reserved concurrency non negative. The aws lambda function element owns the evidence for this decision, including reserved concurrent executions, resource label. At this level, Lambda deployment settings agree with the chosen package type, runtime, permissions, limits, event source, and production posture; allowing the model through without that evidence would move an unresolved choice into generated infrastructure. The concrete failure this rule prevents is: Lambda has a negative reservedConcurrentExecutions value.

### When it applies

The rule has no guard, so it applies to every instance of this context in the validated model.

There is no guard expression; every instance of the context is checked.

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : (self.reservedConcurrentExecutions.isUndefined()) or (self.reservedConcurrentExecutions >= 0)
```

This rule reads: `reservedConcurrentExecutions`, `resourceLabel`.

### Diagnostic and repair

> Lambda has a negative reservedConcurrentExecutions value. Fix: remove the value or set it to zero or greater.

**How to fix it:**

remove the value or set it to zero or greater.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---

## `AutoPublishAliasRequiresVersionPublishing`

**Context:** `AWSPSM!AwsLambdaFunction`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/psm/rules/compute.evl:65`

### Why this rule exists

Checks that auto publish alias requires version publishing. The aws lambda function element owns the evidence for this decision, including auto publish alias, publish version, resource label. At this level, Lambda deployment settings agree with the chosen package type, runtime, permissions, limits, event source, and production posture; allowing the model through without that evidence would move an unresolved choice into generated infrastructure. The concrete failure this rule prevents is: Lambda has autoPublishAlias but publishVersion is not true.

### When it applies

The rule is considered only when the guard below is true. A false guard is intentional: it means this invariant is not relevant to the current variant or modeling situation.

```evl
guard : self.autoPublishAlias.hasText()
```

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.publishVersion = true
```

This rule reads: `autoPublishAlias`, `publishVersion`, `resourceLabel`.

### Diagnostic and repair

> Lambda has autoPublishAlias but publishVersion is not true. Fix: set publishVersion to true so aliases can point to immutable versions.

**How to fix it:**

set publishVersion to true so aliases can point to immutable versions.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---

## `CodeSigningDecisionHonored`

**Context:** `AWSPSM!AwsLambdaFunction`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/psm/rules/compute.evl:72`

### Why this rule exists

Checks that code signing decision honored. The aws lambda function element owns the evidence for this decision, including code signing decision, code signing config, resource label. At this level, Lambda deployment settings agree with the chosen package type, runtime, permissions, limits, event source, and production posture; allowing the model through without that evidence would move an unresolved choice into generated infrastructure. The concrete failure this rule prevents is: Lambda requires code signing but has no codeSigningConfig.

### When it applies

The rule is considered only when the guard below is true. A false guard is intentional: it means this invariant is not relevant to the current variant or modeling situation.

```evl
guard : self.codeSigningDecision = AWSPSMENUMS!Decision#REQUIRED
```

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.codeSigningConfig.isDefined()
```

This rule reads: `codeSigningDecision`, `codeSigningConfig`, `resourceLabel`.

### Diagnostic and repair

> Lambda requires code signing but has no codeSigningConfig. Fix: attach a CodeSigningConfig or change codeSigningDecision if code signing is not required.

**How to fix it:**

attach a CodeSigningConfig or change codeSigningDecision if code signing is not required.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---

## `ProductionLambdaShouldUseTracing`

**Context:** `AWSPSM!AwsLambdaFunction`  
**Classification:** Advisory critique (warning)  
**Source:** `mde/validation/psm/rules/compute.evl:79`

### Why this rule exists

Advises that production lambda should use tracing. This is a review signal about is production scoped, tracing, resource label, not a cosmetic naming preference. In this part of the model, Lambda deployment settings agree with the chosen package type, runtime, permissions, limits, event source, and production posture; the warning makes a decision visible while it can still be discussed and changed. The concrete gap is: Production-scoped Lambda does not enable active tracing. If it is left unexplained, a later transformation, generator, or reviewer has to invent an assumption.

### When it applies

The rule is considered only when the guard below is true. A false guard is intentional: it means this invariant is not relevant to the current variant or modeling situation.

```evl
guard : self.isProductionScoped()
```

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.tracing.isDefined() and self.tracing.mode = AWSPSMENUMS!LambdaTracingMode#ACTIVE
```

This rule reads: `isProductionScoped`, `tracing`, `resourceLabel`.

### Diagnostic and repair

> Production-scoped Lambda does not enable active tracing. Fix: add LambdaTracingConfig with mode ACTIVE unless tracing is intentionally disabled with a documented rationale.

**How to fix it:**

add LambdaTracingConfig with mode ACTIVE unless tracing is intentionally disabled with a documented rationale.

A critique does not necessarily make the model invalid. It is a deliberate quality/readiness signal; disposition it by improving the model, documenting why it is acceptable, or carrying the decision into the review record.

---

## `ProductionLambdaShouldUseStructuredLogging`

**Context:** `AWSPSM!AwsLambdaFunction`  
**Classification:** Advisory critique (warning)  
**Source:** `mde/validation/psm/rules/compute.evl:86`

### Why this rule exists

Advises that production lambda should use structured logging. This is a review signal about is production scoped, logging, resource label, not a cosmetic naming preference. In this part of the model, Lambda deployment settings agree with the chosen package type, runtime, permissions, limits, event source, and production posture; the warning makes a decision visible while it can still be discussed and changed. The concrete gap is: Production-scoped Lambda has no explicit logging configuration. If it is left unexplained, a later transformation, generator, or reviewer has to invent an assumption.

### When it applies

The rule is considered only when the guard below is true. A false guard is intentional: it means this invariant is not relevant to the current variant or modeling situation.

```evl
guard : self.isProductionScoped()
```

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.logging.isDefined() and self.logging.logFormat.hasText()
```

This rule reads: `isProductionScoped`, `logging`, `resourceLabel`.

### Diagnostic and repair

> Production-scoped Lambda has no explicit logging configuration. Fix: add LambdaLoggingConfig and set logFormat/applicationLogLevel/systemLogLevel to the platform standard.

**How to fix it:**

add LambdaLoggingConfig and set logFormat/applicationLogLevel/systemLogLevel to the platform standard.

A critique does not necessarily make the model invalid. It is a deliberate quality/readiness signal; disposition it by improving the model, documenting why it is acceptable, or carrying the decision into the review record.

---

## `ProductionLambdaShouldHaveFailureDestinationOrDlq`

**Context:** `AWSPSM!AwsLambdaFunction`  
**Classification:** Advisory critique (warning)  
**Source:** `mde/validation/psm/rules/compute.evl:93`

### Why this rule exists

Advises that production lambda should have failure destination or dlq. This is a review signal about is production scoped, dead letter config, event invoke configs, destination config, resource label, not a cosmetic naming preference. In this part of the model, Lambda deployment settings agree with the chosen package type, runtime, permissions, limits, event source, and production posture; the warning makes a decision visible while it can still be discussed and changed. The concrete gap is: Production-scoped Lambda has no DLQ or async failure destination. If it is left unexplained, a later transformation, generator, or reviewer has to invent an assumption.

### When it applies

The rule is considered only when the guard below is true. A false guard is intentional: it means this invariant is not relevant to the current variant or modeling situation.

```evl
guard : self.isProductionScoped()
```

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.deadLetterConfig.isDefined() or self.eventInvokeConfigs.exists(invokeItem | invokeItem.destinationConfig.isDefined() and invokeItem.destinationConfig.onFailure.isDefined())
```

This rule reads: `isProductionScoped`, `deadLetterConfig`, `eventInvokeConfigs`, `destinationConfig`, `resourceLabel`.

### Diagnostic and repair

> Production-scoped Lambda has no DLQ or async failure destination. Fix: add LambdaDeadLetterConfig or LambdaEventInvokeConfig.destinationConfig.onFailure.

**How to fix it:**

add LambdaDeadLetterConfig or LambdaEventInvokeConfig.destinationConfig.onFailure.

A critique does not necessarily make the model invalid. It is a deliberate quality/readiness signal; disposition it by improving the model, documenting why it is acceptable, or carrying the decision into the review record.

---

## `ZipCodeHasRuntimeAndHandler`

**Context:** `AWSPSM!LambdaZipCodeConfig`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/psm/rules/compute.evl:104`

### Why this rule exists

A zip artifact is not self-describing enough for Lambda to find its entry point. Runtime and handler together tell the platform how to load the function and where execution begins.

### When it applies

The rule has no guard, so it applies to every instance of this context in the validated model.

There is no guard expression; every instance of the context is checked.

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : (self.runtimeIdentifier.hasText() and self.handler.hasText()) or self.isGeneratorManagedCodeSkeleton()
```

This rule reads: `runtimeIdentifier`, `handler`, `isGeneratorManagedCodeSkeleton`.

### Diagnostic and repair

> ZIP Lambda code configuration is missing runtimeIdentifier or handler. Fix: set runtimeIdentifier and handler for externally supplied ZIP code; generator-managed skeletons may rely on the artifact generator runtime defaults.

**How to fix it:**

set runtimeIdentifier and handler for externally supplied ZIP code; generator-managed skeletons may rely on the artifact generator runtime defaults.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---

## `ZipCodeHasExactlyOneLocation`

**Context:** `AWSPSM!LambdaZipCodeConfig`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/psm/rules/compute.evl:110`

### Why this rule exists

Checks that zip code has exactly one location. The lambda zip code config element owns the evidence for this decision, including code uri, s3 bucket, s3 key, inline zip file. At this level, Lambda deployment settings agree with the chosen package type, runtime, permissions, limits, event source, and production posture; allowing the model through without that evidence would move an unresolved choice into generated infrastructure. The concrete failure this rule prevents is: ZIP Lambda code configuration must define exactly one code location.

### When it applies

The rule has no guard, so it applies to every instance of this context in the validated model.

There is no guard expression; every instance of the context is checked.

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : ((self.codeUri.hasText() ? 1 else 0) + (((self.s3Bucket.hasText()) and (self.s3Key.hasText())) ? 1 else 0) + (self.inlineZipFile.hasText() ? 1 else 0)) = 1
```

This rule reads: `codeUri`, `s3Bucket`, `s3Key`, `inlineZipFile`.

### Diagnostic and repair

> ZIP Lambda code configuration must define exactly one code location. Fix: use either codeUri, or s3Bucket+s3Key, or inlineZipFile; remove the others.

**How to fix it:**

use either codeUri, or s3Bucket+s3Key, or inlineZipFile; remove the others.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---

## `ImageCodeHasImageUri`

**Context:** `AWSPSM!LambdaImageCodeConfig`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/psm/rules/compute.evl:121`

### Why this rule exists

An image-based function can only be deployed from a concrete image location. The URI is the bridge between the modeled package choice and the registry artifact that Lambda will execute.

### When it applies

The rule has no guard, so it applies to every instance of this context in the validated model.

There is no guard expression; every instance of the context is checked.

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.imageUri.hasText()
```

This rule reads: `imageUri`.

### Diagnostic and repair

> Image Lambda code configuration has no imageUri. Fix: provide the ECR image URI including repository and tag or digest.

**How to fix it:**

provide the ECR image URI including repository and tag or digest.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---

## `EnvironmentVariableNameValid`

**Context:** `AWSPSM!LambdaEnvironmentVariable`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/psm/rules/compute.evl:130`

### Why this rule exists

Checks that environment variable name valid. The lambda environment variable element owns the evidence for this decision, including variable name. At this level, Lambda deployment settings agree with the chosen package type, runtime, permissions, limits, event source, and production posture; allowing the model through without that evidence would move an unresolved choice into generated infrastructure. The concrete failure this rule prevents is: Lambda environment variable name is invalid.

### When it applies

The rule has no guard, so it applies to every instance of this context in the validated model.

There is no guard expression; every instance of the context is checked.

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.variableName.matches('^[A-Za-z][A-Za-z0-9_]*$')
```

This rule reads: `variableName`.

### Diagnostic and repair

> Lambda environment variable name is invalid. Fix: start with a letter and use only letters, digits, and underscores.

**How to fix it:**

start with a letter and use only letters, digits, and underscores.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---

## `SecretEnvironmentValueUsesSecureReference`

**Context:** `AWSPSM!LambdaEnvironmentVariable`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/psm/rules/compute.evl:136`

### Why this rule exists

Environment variables are easy to inspect and copy, so putting a secret literal there defeats the security model. The rule requires a reference to a managed secure value instead.

### When it applies

The rule is considered only when the guard below is true. A false guard is intentional: it means this invariant is not relevant to the current variant or modeling situation.

```evl
guard : self.value.isDefined() and self.value.secret = true
```

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.value.usesSecureSource()
```

This rule reads: `value`, `variableName`.

### Diagnostic and repair

> Lambda environment variable is marked secret but does not use a secure source. Fix: use SSM_SECURE_REFERENCE, SECRETS_MANAGER_REFERENCE, or DYNAMIC_REFERENCE.

**How to fix it:**

use SSM_SECURE_REFERENCE, SECRETS_MANAGER_REFERENCE, or DYNAMIC_REFERENCE.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---

## `StageSpecificVariableShouldHaveRationale`

**Context:** `AWSPSM!LambdaEnvironmentVariable`  
**Classification:** Advisory critique (warning)  
**Source:** `mde/validation/psm/rules/compute.evl:143`

### Why this rule exists

Advises that stage specific variable should have rationale. This is a review signal about stage specific, rationale, variable name, not a cosmetic naming preference. In this part of the model, Lambda deployment settings agree with the chosen package type, runtime, permissions, limits, event source, and production posture; the warning makes a decision visible while it can still be discussed and changed. The concrete gap is: Stage-specific environment variable has no rationale. If it is left unexplained, a later transformation, generator, or reviewer has to invent an assumption.

### When it applies

The rule is considered only when the guard below is true. A false guard is intentional: it means this invariant is not relevant to the current variant or modeling situation.

```evl
guard : self.stageSpecific = true
```

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.rationale.hasText()
```

This rule reads: `stageSpecific`, `rationale`, `variableName`.

### Diagnostic and repair

> Stage-specific environment variable has no rationale. Fix: add rationale explaining why the value must vary by stage.

**How to fix it:**

add rationale explaining why the value must vary by stage.

A critique does not necessarily make the model invalid. It is a deliberate quality/readiness signal; disposition it by improving the model, documenting why it is acceptable, or carrying the decision into the review record.

---

## `DlqHasExactlyOneTarget`

**Context:** `AWSPSM!LambdaDeadLetterConfig`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/psm/rules/compute.evl:153`

### Why this rule exists

Checks that dlq has exactly one target. The lambda dead letter config element owns the evidence for this decision, including target queue, target topic. At this level, Lambda deployment settings agree with the chosen package type, runtime, permissions, limits, event source, and production posture; allowing the model through without that evidence would move an unresolved choice into generated infrastructure. The concrete failure this rule prevents is: Lambda DLQ configuration must choose exactly one target.

### When it applies

The rule has no guard, so it applies to every instance of this context in the validated model.

There is no guard expression; every instance of the context is checked.

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : ((self.targetQueue.isDefined() ? 1 else 0) + (self.targetTopic.isDefined() ? 1 else 0)) = 1
```

This rule reads: `targetQueue`, `targetTopic`.

### Diagnostic and repair

> Lambda DLQ configuration must choose exactly one target. Fix: set either targetQueue or targetTopic, not both.

**How to fix it:**

set either targetQueue or targetTopic, not both.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---

## `BatchSizePositive`

**Context:** `AWSPSM!LambdaEventSourceMapping`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/psm/rules/compute.evl:162`

### Why this rule exists

Checks that batch size positive. The lambda event source mapping element owns the evidence for this decision, including batch size, resource label. At this level, Lambda deployment settings agree with the chosen package type, runtime, permissions, limits, event source, and production posture; allowing the model through without that evidence would move an unresolved choice into generated infrastructure. The concrete failure this rule prevents is: Lambda event source mapping has an invalid batchSize.

### When it applies

The rule has no guard, so it applies to every instance of this context in the validated model.

There is no guard expression; every instance of the context is checked.

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : (self.batchSize.isUndefined()) or (self.batchSize > 0)
```

This rule reads: `batchSize`, `resourceLabel`.

### Diagnostic and repair

> Lambda event source mapping has an invalid batchSize. Fix: remove batchSize to use the service default, or set it to a positive integer.

**How to fix it:**

remove batchSize to use the service default, or set it to a positive integer.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---

## `MaximumBatchingWindowRange`

**Context:** `AWSPSM!LambdaEventSourceMapping`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/psm/rules/compute.evl:168`

### Why this rule exists

Checks that maximum batching window range. The lambda event source mapping element owns the evidence for this decision, including maximum batching window seconds, resource label. At this level, Lambda deployment settings agree with the chosen package type, runtime, permissions, limits, event source, and production posture; allowing the model through without that evidence would move an unresolved choice into generated infrastructure. The concrete failure this rule prevents is: Lambda event source mapping maximumBatchingWindowSeconds is outside 0..300.

### When it applies

The rule has no guard, so it applies to every instance of this context in the validated model.

There is no guard expression; every instance of the context is checked.

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : (self.maximumBatchingWindowSeconds.isUndefined()) or ((self.maximumBatchingWindowSeconds >= 0) and (self.maximumBatchingWindowSeconds <= 300))
```

This rule reads: `maximumBatchingWindowSeconds`, `resourceLabel`.

### Diagnostic and repair

> Lambda event source mapping maximumBatchingWindowSeconds is outside 0..300. Fix: choose a batching window within five minutes.

**How to fix it:**

choose a batching window within five minutes.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---

## `ParallelizationFactorRange`

**Context:** `AWSPSM!LambdaEventSourceMapping`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/psm/rules/compute.evl:174`

### Why this rule exists

Checks that parallelization factor range. The lambda event source mapping element owns the evidence for this decision, including parallelization factor, resource label. At this level, Lambda deployment settings agree with the chosen package type, runtime, permissions, limits, event source, and production posture; allowing the model through without that evidence would move an unresolved choice into generated infrastructure. The concrete failure this rule prevents is: Lambda event source mapping parallelizationFactor is outside 1..10.

### When it applies

The rule has no guard, so it applies to every instance of this context in the validated model.

There is no guard expression; every instance of the context is checked.

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : (self.parallelizationFactor.isUndefined()) or ((self.parallelizationFactor >= 1) and (self.parallelizationFactor <= 10))
```

This rule reads: `parallelizationFactor`, `resourceLabel`.

### Diagnostic and repair

> Lambda event source mapping parallelizationFactor is outside 1..10. Fix: set a value from 1 to 10 or remove it.

**How to fix it:**

set a value from 1 to 10 or remove it.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---

## `StartingTimestampRequiresAtTimestamp`

**Context:** `AWSPSM!LambdaEventSourceMapping`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/psm/rules/compute.evl:180`

### Why this rule exists

Checks that starting timestamp requires at timestamp. The lambda event source mapping element owns the evidence for this decision, including starting position timestamp, starting position, resource label. At this level, Lambda deployment settings agree with the chosen package type, runtime, permissions, limits, event source, and production posture; allowing the model through without that evidence would move an unresolved choice into generated infrastructure. The concrete failure this rule prevents is: Event source mapping has startingPositionTimestamp without AT_TIMESTAMP.

### When it applies

The rule is considered only when the guard below is true. A false guard is intentional: it means this invariant is not relevant to the current variant or modeling situation.

```evl
guard : self.startingPositionTimestamp.hasText()
```

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.startingPosition = AWSPSMENUMS!StartingPosition#AT_TIMESTAMP
```

This rule reads: `startingPositionTimestamp`, `startingPosition`, `resourceLabel`.

### Diagnostic and repair

> Event source mapping has startingPositionTimestamp without AT_TIMESTAMP. Fix: set startingPosition to AT_TIMESTAMP or remove startingPositionTimestamp.

**How to fix it:**

set startingPosition to AT_TIMESTAMP or remove startingPositionTimestamp.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---

## `QueueVisibilityGreaterThanFunctionTimeout`

**Context:** `AWSPSM!SqsLambdaEventSourceMapping`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/psm/rules/compute.evl:190`

### Why this rule exists

Checks that queue visibility greater than function timeout. The sqs lambda event source mapping element owns the evidence for this decision, including queue, function, resource label. At this level, Lambda deployment settings agree with the chosen package type, runtime, permissions, limits, event source, and production posture; allowing the model through without that evidence would move an unresolved choice into generated infrastructure. The concrete failure this rule prevents is: SQS queue visibility timeout must be greater than Lambda timeout for mapping.

### When it applies

The rule has no guard, so it applies to every instance of this context in the validated model.

There is no guard expression; every instance of the context is checked.

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.queue.isDefined() and self.`function`.isDefined() and ((self.queue.visibilityTimeoutSeconds.isUndefined()) or (self.`function`.timeoutSeconds.isUndefined()) or (self.queue.visibilityTimeoutSeconds > self.`function`.timeoutSeconds))
```

This rule reads: `queue`, `function`, `resourceLabel`.

### Diagnostic and repair

> SQS queue visibility timeout must be greater than Lambda timeout for mapping . Fix: increase queue.visibilityTimeoutSeconds or reduce function.timeoutSeconds to avoid duplicate processing.

**How to fix it:**

increase queue.visibilityTimeoutSeconds or reduce function.timeoutSeconds to avoid duplicate processing.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---

## `PartialBatchFailureRecommendedForSqs`

**Context:** `AWSPSM!SqsLambdaEventSourceMapping`  
**Classification:** Advisory critique (warning)  
**Source:** `mde/validation/psm/rules/compute.evl:199`

### Why this rule exists

Advises that partial batch failure recommended for sqs. This is a review signal about report batch item failures, resource label, not a cosmetic naming preference. In this part of the model, Lambda deployment settings agree with the chosen package type, runtime, permissions, limits, event source, and production posture; the warning makes a decision visible while it can still be discussed and changed. The concrete gap is: SQS event source mapping does not report partial batch item failures. If it is left unexplained, a later transformation, generator, or reviewer has to invent an assumption.

### When it applies

The rule has no guard, so it applies to every instance of this context in the validated model.

There is no guard expression; every instance of the context is checked.

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.reportBatchItemFailures = true
```

This rule reads: `reportBatchItemFailures`, `resourceLabel`.

### Diagnostic and repair

> SQS event source mapping does not report partial batch item failures. Fix: set reportBatchItemFailures to true and include FunctionResponseTypes=ReportBatchItemFailures in the generated mapping.

**How to fix it:**

set reportBatchItemFailures to true and include FunctionResponseTypes=ReportBatchItemFailures in the generated mapping.

A critique does not necessarily make the model invalid. It is a deliberate quality/readiness signal; disposition it by improving the model, documenting why it is acceptable, or carrying the decision into the review record.

---

## `RequiredPartialBatchFailureDecisionHonored`

**Context:** `AWSPSM!SqsLambdaEventSourceMapping`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/psm/rules/compute.evl:205`

### Why this rule exists

Checks that required partial batch failure decision honored. The sqs lambda event source mapping element owns the evidence for this decision, including partial batch failure handling decision, report batch item failures, resource label. At this level, Lambda deployment settings agree with the chosen package type, runtime, permissions, limits, event source, and production posture; allowing the model through without that evidence would move an unresolved choice into generated infrastructure. The concrete failure this rule prevents is: SQS event source mapping marks partial batch failure handling as REQUIRED but does not enable it.

### When it applies

The rule is considered only when the guard below is true. A false guard is intentional: it means this invariant is not relevant to the current variant or modeling situation.

```evl
guard : self.partialBatchFailureHandlingDecision = AWSPSMENUMS!Decision#REQUIRED
```

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.reportBatchItemFailures = true
```

This rule reads: `partialBatchFailureHandlingDecision`, `reportBatchItemFailures`, `resourceLabel`.

### Diagnostic and repair

> SQS event source mapping marks partial batch failure handling as REQUIRED but does not enable it. Fix: set reportBatchItemFailures to true or change the decision with rationale.

**How to fix it:**

set reportBatchItemFailures to true or change the decision with rationale.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---

## `DynamoStreamMappingRequiresStreamSpecification`

**Context:** `AWSPSM!DynamoDbStreamLambdaEventSourceMapping`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/psm/rules/compute.evl:215`

### Why this rule exists

Checks that dynamo stream mapping requires stream specification. The dynamo db stream lambda event source mapping element owns the evidence for this decision, including table, resource label. At this level, Lambda deployment settings agree with the chosen package type, runtime, permissions, limits, event source, and production posture; allowing the model through without that evidence would move an unresolved choice into generated infrastructure. The concrete failure this rule prevents is: DynamoDB stream event source mapping points to a table without streamSpecification.

### When it applies

The rule has no guard, so it applies to every instance of this context in the validated model.

There is no guard expression; every instance of the context is checked.

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.table.isDefined() and self.table.streamSpecification.isDefined()
```

This rule reads: `table`, `resourceLabel`.

### Diagnostic and repair

> DynamoDB stream event source mapping points to a table without streamSpecification. Fix: enable DynamoDbStreamSpecification on the table or remove the mapping.

**How to fix it:**

enable DynamoDbStreamSpecification on the table or remove the mapping.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---

## `DynamoStreamMappingHasStartingPosition`

**Context:** `AWSPSM!DynamoDbStreamLambdaEventSourceMapping`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/psm/rules/compute.evl:221`

### Why this rule exists

Checks that dynamo stream mapping has starting position. The dynamo db stream lambda event source mapping element owns the evidence for this decision, including e is set, e class, resource label. At this level, Lambda deployment settings agree with the chosen package type, runtime, permissions, limits, event source, and production posture; allowing the model through without that evidence would move an unresolved choice into generated infrastructure. The concrete failure this rule prevents is: DynamoDB stream event source mapping has no startingPosition.

### When it applies

The rule has no guard, so it applies to every instance of this context in the validated model.

There is no guard expression; every instance of the context is checked.

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.eIsSet(self.eClass().getEStructuralFeature('startingPosition'))
```

This rule reads: `eIsSet`, `eClass`, `resourceLabel`.

### Diagnostic and repair

> DynamoDB stream event source mapping has no startingPosition. Fix: set startingPosition to LATEST, TRIM_HORIZON, or AT_TIMESTAMP.

**How to fix it:**

set startingPosition to LATEST, TRIM_HORIZON, or AT_TIMESTAMP.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---

## `ProvisionedConcurrencyPositive`

**Context:** `AWSPSM!LambdaProvisionedConcurrencyConfig`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/psm/rules/compute.evl:230`

### Why this rule exists

Checks that provisioned concurrency positive. The lambda provisioned concurrency config element owns the evidence for this decision, including provisioned concurrent executions. At this level, Lambda deployment settings agree with the chosen package type, runtime, permissions, limits, event source, and production posture; allowing the model through without that evidence would move an unresolved choice into generated infrastructure. The concrete failure this rule prevents is: Provisioned concurrency must be greater than zero.

### When it applies

The rule has no guard, so it applies to every instance of this context in the validated model.

There is no guard expression; every instance of the context is checked.

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.provisionedConcurrentExecutions > 0
```

This rule reads: `provisionedConcurrentExecutions`.

### Diagnostic and repair

> Provisioned concurrency must be greater than zero. Fix: set provisionedConcurrentExecutions to a positive integer or remove the provisioned concurrency config.

**How to fix it:**

set provisionedConcurrentExecutions to a positive integer or remove the provisioned concurrency config.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---

## `ProductionFunctionUrlRequiresAuth`

**Context:** `AWSPSM!LambdaFunctionUrl`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/psm/rules/compute.evl:239`

### Why this rule exists

A production function URL bypasses many API-layer controls. Requiring an authentication mode makes that direct entry point subject to an explicit access decision.

### When it applies

The rule is considered only when the guard below is true. A false guard is intentional: it means this invariant is not relevant to the current variant or modeling situation.

```evl
guard : self.`function`.isDefined() and self.`function`.isProductionScoped()
```

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.authType <> AWSPSMENUMS!LambdaFunctionUrlAuthType#NONE
```

This rule reads: `function`, `authType`.

### Diagnostic and repair

> Production-scoped Lambda function URL for has authType NONE. Fix: use AWS_IAM or expose the function through an authenticated API Gateway route.

**How to fix it:**

use AWS_IAM or expose the function through an authenticated API Gateway route.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---
