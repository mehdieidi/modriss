# AWS PSM validation: Security

IAM, KMS, Secrets Manager, and SSM rules enforce the security properties that are too important to leave to template conventions: trust and permission statements, least privilege evidence, key rotation, secret sources, secure parameter types, and safe production defaults.

Source profile: `mde/validation/psm/rules/security.evl`.

## Reading these rules

Each entry preserves the actual EVL guard and check. Read the guard as the applicability boundary, not as part of the invariant: when it is false, the rule is intentionally skipped. The diagnostic is the runtime-facing message emitted by EVL; its final sentence usually contains the repository's recommended repair.

---

## `RoleHasTrustPolicyStatements`

**Context:** `AWSPSM!IamRole`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/psm/rules/security.evl:8`

### Why this rule exists

Permissions say what a role may do; trust policy says who may become that role. Without trust statements, the execution identity is either unusable or governed by an assumption outside the model.

### When it applies

The rule has no guard, so it applies to every instance of this context in the validated model.

There is no guard expression; every instance of the context is checked.

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.assumeRolePolicy.isDefined() and self.assumeRolePolicy.statements.notEmpty()
```

This rule reads: `assumeRolePolicy`, `resourceLabel`.

### Diagnostic and repair

> IAM role has no trust policy statements. Fix: add assumeRolePolicy statements that define who can assume the role.

**How to fix it:**

add assumeRolePolicy statements that define who can assume the role.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---

## `ProductionRoleShouldUsePermissionsBoundary`

**Context:** `AWSPSM!IamRole`  
**Classification:** Advisory critique (warning)  
**Source:** `mde/validation/psm/rules/security.evl:14`

### Why this rule exists

Advises that production role should use permissions boundary. This is a review signal about is production scoped, permissions boundary arn, resource label, not a cosmetic naming preference. In this part of the model, IAM, KMS, Secrets Manager, and SSM artifacts enforce the intended trust, scope, rotation, and secure-reference decisions; the warning makes a decision visible while it can still be discussed and changed. The concrete gap is: Production-scoped IAM role has no permissions boundary. If it is left unexplained, a later transformation, generator, or reviewer has to invent an assumption.

### When it applies

The rule is considered only when the guard below is true. A false guard is intentional: it means this invariant is not relevant to the current variant or modeling situation.

```evl
guard : self.isProductionScoped()
```

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.permissionsBoundaryArn.hasText()
```

This rule reads: `isProductionScoped`, `permissionsBoundaryArn`, `resourceLabel`.

### Diagnostic and repair

> Production-scoped IAM role has no permissions boundary. Fix: attach permissionsBoundaryArn if your organization requires permission boundaries.

**How to fix it:**

attach permissionsBoundaryArn if your organization requires permission boundaries.

A critique does not necessarily make the model invalid. It is a deliberate quality/readiness signal; disposition it by improving the model, documenting why it is acceptable, or carrying the decision into the review record.

---

## `PolicyDocumentHasStatements`

**Context:** `AWSPSM!IamPolicyDocument`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/psm/rules/security.evl:24`

### Why this rule exists

Checks that policy document has statements. The iam policy document element owns the evidence for this decision, including statements. At this level, IAM, KMS, Secrets Manager, and SSM artifacts enforce the intended trust, scope, rotation, and secure-reference decisions; allowing the model through without that evidence would move an unresolved choice into generated infrastructure. The concrete failure this rule prevents is: IAM policy document has no statements.

### When it applies

The rule has no guard, so it applies to every instance of this context in the validated model.

There is no guard expression; every instance of the context is checked.

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.statements.notEmpty()
```

This rule reads: `statements`.

### Diagnostic and repair

> IAM policy document has no statements. Fix: add at least one IamStatement or remove the unused policy document.

**How to fix it:**

add at least one IamStatement or remove the unused policy document.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---

## `StatementHasActionAndResourceSide`

**Context:** `AWSPSM!IamStatement`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/psm/rules/security.evl:33`

### Why this rule exists

Checks that statement has action and resource side. The iam statement element owns the evidence for this decision, including has action side, has resource side, is trust policy statement. At this level, IAM, KMS, Secrets Manager, and SSM artifacts enforce the intended trust, scope, rotation, and secure-reference decisions; allowing the model through without that evidence would move an unresolved choice into generated infrastructure. The concrete failure this rule prevents is: IAM statement is missing action/notAction or an applicable target side.

### When it applies

The rule has no guard, so it applies to every instance of this context in the validated model.

There is no guard expression; every instance of the context is checked.

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.hasActionSide() and (self.hasResourceSide() or self.isTrustPolicyStatement())
```

This rule reads: `hasActionSide`, `hasResourceSide`, `isTrustPolicyStatement`.

### Diagnostic and repair

> IAM statement is missing action/notAction or an applicable target side. Fix: define resource/notResource for permission policies, or principal entries for trust policies.

**How to fix it:**

define resource/notResource for permission policies, or principal entries for trust policies.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---

## `NoAllowWildcardInProductionWithoutJustification`

**Context:** `AWSPSM!IamStatement`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/psm/rules/security.evl:39`

### Why this rule exists

A wildcard permission may occasionally be unavoidable, but it expands the blast radius beyond what the statement itself explains. Requiring an explicit exception and rationale turns a dangerous shortcut into an auditable decision.

### When it applies

The rule is considered only when the guard below is true. A false guard is intentional: it means this invariant is not relevant to the current variant or modeling situation.

```evl
guard : productionStageExists() and self.effect = AWSPSMENUMS!IamEffect#ALLOW
```

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : (not self.actionIsWildcard()) and (not self.resourceIsWildcard()) or self.hasLeastPrivilegeJustification()
```

This rule reads: `effect`, `actionIsWildcard`, `resourceIsWildcard`, `hasLeastPrivilegeJustification`.

### Diagnostic and repair

> An ALLOW IAM statement uses wildcard action/resource in a model with a PROD stage. Fix: replace wildcards with least-privilege actions/resources, or add conditions and wildcardJustification approved by security.

**How to fix it:**

replace wildcards with least-privilege actions/resources, or add conditions and wildcardJustification approved by security.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---

## `AvoidNotActionInAllowStatements`

**Context:** `AWSPSM!IamStatement`  
**Classification:** Advisory critique (warning)  
**Source:** `mde/validation/psm/rules/security.evl:46`

### Why this rule exists

Advises that avoid not action in allow statements. This is a review signal about effect, not actions, not a cosmetic naming preference. In this part of the model, IAM, KMS, Secrets Manager, and SSM artifacts enforce the intended trust, scope, rotation, and secure-reference decisions; the warning makes a decision visible while it can still be discussed and changed. The concrete gap is: ALLOW IAM statement uses notActions, which is difficult to reason about. If it is left unexplained, a later transformation, generator, or reviewer has to invent an assumption.

### When it applies

The rule is considered only when the guard below is true. A false guard is intentional: it means this invariant is not relevant to the current variant or modeling situation.

```evl
guard : self.effect = AWSPSMENUMS!IamEffect#ALLOW
```

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.notActions.isEmpty()
```

This rule reads: `effect`, `notActions`.

### Diagnostic and repair

> ALLOW IAM statement uses notActions, which is difficult to reason about. Fix: list explicit actions whenever possible.

**How to fix it:**

list explicit actions whenever possible.

A critique does not necessarily make the model invalid. It is a deliberate quality/readiness signal; disposition it by improving the model, documenting why it is acceptable, or carrying the decision into the review record.

---

## `PrincipalHasTypeAndIdentifiers`

**Context:** `AWSPSM!IamPrincipal`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/psm/rules/security.evl:56`

### Why this rule exists

Checks that principal has type and identifiers. The iam principal element owns the evidence for this decision, including principal type, identifiers. At this level, IAM, KMS, Secrets Manager, and SSM artifacts enforce the intended trust, scope, rotation, and secure-reference decisions; allowing the model through without that evidence would move an unresolved choice into generated infrastructure. The concrete failure this rule prevents is: IAM principal is incomplete.

### When it applies

The rule has no guard, so it applies to every instance of this context in the validated model.

There is no guard expression; every instance of the context is checked.

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.principalType.hasText() and self.identifiers.notEmpty()
```

This rule reads: `principalType`, `identifiers`.

### Diagnostic and repair

> IAM principal is incomplete. Fix: set principalType, for example Service or AWS, and provide one or more identifiers.

**How to fix it:**

set principalType, for example Service or AWS, and provide one or more identifiers.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---

## `ConditionComplete`

**Context:** `AWSPSM!IamCondition`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/psm/rules/security.evl:65`

### Why this rule exists

Checks that condition complete. The iam condition element owns the evidence for this decision, including operator, key, values. At this level, IAM, KMS, Secrets Manager, and SSM artifacts enforce the intended trust, scope, rotation, and secure-reference decisions; allowing the model through without that evidence would move an unresolved choice into generated infrastructure. The concrete failure this rule prevents is: IAM condition is incomplete.

### When it applies

The rule has no guard, so it applies to every instance of this context in the validated model.

There is no guard expression; every instance of the context is checked.

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.operator.hasText() and self.key.hasText() and self.values.notEmpty()
```

This rule reads: `operator`, `key`, `values`.

### Diagnostic and repair

> IAM condition is incomplete. Fix: set operator, key, and at least one value.

**How to fix it:**

set operator, key, and at least one value.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---

## `ProductionKmsKeyRotation`

**Context:** `AWSPSM!KmsKey`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/psm/rules/security.evl:74`

### Why this rule exists

Key rotation is part of the long-lived production protection of encrypted data. The rule ensures that choosing KMS also carries a deliberate rotation posture rather than leaving key lifetime implicit.

### When it applies

The rule is considered only when the guard below is true. A false guard is intentional: it means this invariant is not relevant to the current variant or modeling situation.

```evl
guard : self.isProductionScoped()
```

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.enableKeyRotation = true
```

This rule reads: `isProductionScoped`, `enableKeyRotation`, `resourceLabel`.

### Diagnostic and repair

> Production-scoped KMS key does not enable rotation. Fix: set enableKeyRotation to true unless rotation is unsupported for the key type.

**How to fix it:**

set enableKeyRotation to true unless rotation is unsupported for the key type.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---

## `PendingWindowRange`

**Context:** `AWSPSM!KmsKey`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/psm/rules/security.evl:81`

### Why this rule exists

Checks that pending window range. The kms key element owns the evidence for this decision, including pending window in days, resource label. At this level, IAM, KMS, Secrets Manager, and SSM artifacts enforce the intended trust, scope, rotation, and secure-reference decisions; allowing the model through without that evidence would move an unresolved choice into generated infrastructure. The concrete failure this rule prevents is: KMS key pendingWindowInDays is outside 7..30.

### When it applies

The rule has no guard, so it applies to every instance of this context in the validated model.

There is no guard expression; every instance of the context is checked.

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.pendingWindowInDays.isUndefined() or ((self.pendingWindowInDays >= 7) and (self.pendingWindowInDays <= 30))
```

This rule reads: `pendingWindowInDays`, `resourceLabel`.

### Diagnostic and repair

> KMS key pendingWindowInDays is outside 7..30. Fix: choose a valid pending deletion window.

**How to fix it:**

choose a valid pending deletion window.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---

## `KmsAliasNameValid`

**Context:** `AWSPSM!KmsAlias`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/psm/rules/security.evl:90`

### Why this rule exists

Checks that kms alias name valid. The kms alias element owns the evidence for this decision, including alias name. At this level, IAM, KMS, Secrets Manager, and SSM artifacts enforce the intended trust, scope, rotation, and secure-reference decisions; allowing the model through without that evidence would move an unresolved choice into generated infrastructure. The concrete failure this rule prevents is: KMS alias is invalid.

### When it applies

The rule has no guard, so it applies to every instance of this context in the validated model.

There is no guard expression; every instance of the context is checked.

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.aliasName.startsWith('alias/') and self.aliasName.size() > 6
```

This rule reads: `aliasName`.

### Diagnostic and repair

> KMS alias is invalid. Fix: use the alias/ prefix, for example alias/my-service-key.

**How to fix it:**

use the alias/ prefix, for example alias/my-service-key.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---

## `SecretHasValueOrGenerator`

**Context:** `AWSPSM!SecretsManagerSecret`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/psm/rules/security.evl:99`

### Why this rule exists

A Secrets Manager secret must have a source for its initial value, whether that is an explicit value or a generator. The rule keeps an apparently managed secret from being created empty by accident.

### When it applies

The rule has no guard, so it applies to every instance of this context in the validated model.

There is no guard expression; every instance of the context is checked.

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.secretValue.isDefined() or self.generateSecretStringJson.hasText()
```

This rule reads: `secretValue`, `generateSecretStringJson`, `resourceLabel`.

### Diagnostic and repair

> Secrets Manager secret has no secretValue or generator configuration. Fix: provide secretValue for a referenced secret or generateSecretStringJson for generated secrets.

**How to fix it:**

provide secretValue for a referenced secret or generateSecretStringJson for generated secrets.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---

## `RotationRequiredHasSchedule`

**Context:** `AWSPSM!SecretsManagerSecret`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/psm/rules/security.evl:105`

### Why this rule exists

Checks that rotation required has schedule. The secrets manager secret element owns the evidence for this decision, including rotation required, rotation schedule, resource label. At this level, IAM, KMS, Secrets Manager, and SSM artifacts enforce the intended trust, scope, rotation, and secure-reference decisions; allowing the model through without that evidence would move an unresolved choice into generated infrastructure. The concrete failure this rule prevents is: Secrets Manager secret requires rotation but has no rotationSchedule.

### When it applies

The rule is considered only when the guard below is true. A false guard is intentional: it means this invariant is not relevant to the current variant or modeling situation.

```evl
guard : self.rotationRequired = true
```

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.rotationSchedule.isDefined()
```

This rule reads: `rotationRequired`, `rotationSchedule`, `resourceLabel`.

### Diagnostic and repair

> Secrets Manager secret requires rotation but has no rotationSchedule. Fix: add SecretRotationSchedule and rotation Lambda/resource as needed.

**How to fix it:**

add SecretRotationSchedule and rotation Lambda/resource as needed.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---

## `ProductionSecretShouldUseKms`

**Context:** `AWSPSM!SecretsManagerSecret`  
**Classification:** Advisory critique (warning)  
**Source:** `mde/validation/psm/rules/security.evl:112`

### Why this rule exists

Advises that production secret should use kms. This is a review signal about is production scoped, kms key, resource label, not a cosmetic naming preference. In this part of the model, IAM, KMS, Secrets Manager, and SSM artifacts enforce the intended trust, scope, rotation, and secure-reference decisions; the warning makes a decision visible while it can still be discussed and changed. The concrete gap is: Production-scoped secret has no explicit KMS key. If it is left unexplained, a later transformation, generator, or reviewer has to invent an assumption.

### When it applies

The rule is considered only when the guard below is true. A false guard is intentional: it means this invariant is not relevant to the current variant or modeling situation.

```evl
guard : self.isProductionScoped()
```

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.kmsKey.isDefined()
```

This rule reads: `isProductionScoped`, `kmsKey`, `resourceLabel`.

### Diagnostic and repair

> Production-scoped secret has no explicit KMS key. Fix: attach kmsKey unless the AWS-managed key is intentionally accepted.

**How to fix it:**

attach kmsKey unless the AWS-managed key is intentionally accepted.

A critique does not necessarily make the model invalid. It is a deliberate quality/readiness signal; disposition it by improving the model, documenting why it is acceptable, or carrying the decision into the review record.

---

## `RotationScheduleHasRulesOrLambda`

**Context:** `AWSPSM!SecretRotationSchedule`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/psm/rules/security.evl:122`

### Why this rule exists

Checks that rotation schedule has rules or lambda. The secret rotation schedule element owns the evidence for this decision, including rotation rules json, rotation lambda, resource label. At this level, IAM, KMS, Secrets Manager, and SSM artifacts enforce the intended trust, scope, rotation, and secure-reference decisions; allowing the model through without that evidence would move an unresolved choice into generated infrastructure. The concrete failure this rule prevents is: Secret rotation schedule has no rotationRulesJson or rotationLambda.

### When it applies

The rule has no guard, so it applies to every instance of this context in the validated model.

There is no guard expression; every instance of the context is checked.

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.rotationRulesJson.hasText() or self.rotationLambda.isDefined()
```

This rule reads: `rotationRulesJson`, `rotationLambda`, `resourceLabel`.

### Diagnostic and repair

> Secret rotation schedule has no rotationRulesJson or rotationLambda. Fix: define rotation cadence and the rotation function/resource.

**How to fix it:**

define rotation cadence and the rotation function/resource.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---

## `SecureParameterUsesSecureType`

**Context:** `AWSPSM!SsmParameter`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/psm/rules/security.evl:131`

### Why this rule exists

The parameter's type controls how Systems Manager stores and returns it. A plain string would undermine the modeled secure-parameter intent even if the parameter name sounds sensitive.

### When it applies

The rule is considered only when the guard below is true. A false guard is intentional: it means this invariant is not relevant to the current variant or modeling situation.

```evl
guard : self.value.isDefined() and self.value.secret = true
```

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.parameterType = AWSPSMENUMS!ParameterType#SECURE_STRING
```

This rule reads: `value`, `parameterType`, `resourceLabel`.

### Diagnostic and repair

> SSM parameter stores a secret value but parameterType is not SECURE_STRING. Fix: set parameterType to SECURE_STRING.

**How to fix it:**

set parameterType to SECURE_STRING.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---

## `SecureParameterShouldUseKmsKey`

**Context:** `AWSPSM!SsmParameter`  
**Classification:** Advisory critique (warning)  
**Source:** `mde/validation/psm/rules/security.evl:138`

### Why this rule exists

Advises that secure parameter should use kms key. This is a review signal about parameter type, kms key, parameter name, not a cosmetic naming preference. In this part of the model, IAM, KMS, Secrets Manager, and SSM artifacts enforce the intended trust, scope, rotation, and secure-reference decisions; the warning makes a decision visible while it can still be discussed and changed. The concrete gap is: Secure SSM parameter has no explicit KMS key. If it is left unexplained, a later transformation, generator, or reviewer has to invent an assumption.

### When it applies

The rule is considered only when the guard below is true. A false guard is intentional: it means this invariant is not relevant to the current variant or modeling situation.

```evl
guard : self.parameterType = AWSPSMENUMS!ParameterType#SECURE_STRING
```

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.kmsKey.isDefined()
```

This rule reads: `parameterType`, `kmsKey`, `parameterName`.

### Diagnostic and repair

> Secure SSM parameter has no explicit KMS key. Fix: attach kmsKey where customer-managed key control is required.

**How to fix it:**

attach kmsKey where customer-managed key control is required.

A critique does not necessarily make the model invalid. It is a deliberate quality/readiness signal; disposition it by improving the model, documenting why it is acceptable, or carrying the decision into the review record.

---
