# AWS PSM validation — Identity

Cognito rules protect production authentication posture and OAuth completeness. They make MFA, deletion protection, callback URLs, user-existence behavior, and unauthenticated identity review explicit rather than silently accepting an insecure default.

Source profile: `mde/validation/psm/rules/identity.evl`.

## Reading these rules

Each entry preserves the actual EVL guard and check. Read the guard as the applicability boundary, not as part of the invariant: when it is false, the rule is intentionally skipped. The diagnostic is the runtime-facing message emitted by EVL; its final sentence usually contains the repository's recommended repair.

---

## `MfaForProductionPrivilegedPools`

**Context:** `AWSPSM!CognitoUserPool`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/psm/rules/identity.evl:8`

### Why this rule exists

Privileged user pools are an authentication boundary where password-only access is an avoidable risk. The rule makes the stronger production expectation explicit.

### When it applies

The rule is considered only when the guard below is true. A false guard is intentional: it means this invariant is not relevant to the current variant or modeling situation.

```evl
guard : self.isProductionScoped()
```

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.mfaConfiguration.isDefined() and self.mfaConfiguration <> AWSPSMENUMS!CognitoMfaConfiguration#OFF
```

This rule reads: `isProductionScoped`, `mfaConfiguration`, `resourceLabel`.

### Diagnostic and repair

> Production-scoped Cognito user pool has MFA OFF or unset. Fix: set mfaConfiguration to ON or OPTIONAL, or document a security-approved exception.

**How to fix it:**

set mfaConfiguration to ON or OPTIONAL, or document a security-approved exception.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---

## `MfaDecisionMadeForProductionCriticalPools`

**Context:** `AWSPSM!CognitoUserPool`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/psm/rules/identity.evl:15`

### Why this rule exists

Checks that mfa decision made for production critical pools. The cognito user pool element owns the evidence for this decision, including is production scoped, mfa decision, resource label. At this level, the deployed authentication boundary does not silently accept avoidable production weaknesses or incomplete OAuth settings; allowing the model through without that evidence would move an unresolved choice into generated infrastructure. The concrete failure this rule prevents is: Production-scoped Cognito user pool has no MFA decision.

### When it applies

The rule is considered only when the guard below is true. A false guard is intentional: it means this invariant is not relevant to the current variant or modeling situation.

```evl
guard : self.isProductionScoped()
```

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.mfaDecision.isDefined() and self.mfaDecision <> AWSPSMENUMS!Decision#UNDECIDED
```

This rule reads: `isProductionScoped`, `mfaDecision`, `resourceLabel`.

### Diagnostic and repair

> Production-scoped Cognito user pool has no MFA decision. Fix: set mfaDecision to REQUIRED, NOT_REQUIRED, ACCEPTED, or NEEDS_REVIEW with mfaRationale.

**How to fix it:**

set mfaDecision to REQUIRED, NOT_REQUIRED, ACCEPTED, or NEEDS_REVIEW with mfaRationale.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---

## `ProductionUserPoolShouldUseDeletionProtection`

**Context:** `AWSPSM!CognitoUserPool`  
**Classification:** Advisory critique (warning)  
**Source:** `mde/validation/psm/rules/identity.evl:22`

### Why this rule exists

Advises that production user pool should use deletion protection. This is a review signal about is production scoped, deletion protection, resource label, not a cosmetic naming preference. In this part of the model, the deployed authentication boundary does not silently accept avoidable production weaknesses or incomplete OAuth settings; the warning makes a decision visible while it can still be discussed and changed. The concrete gap is: Production-scoped Cognito user pool does not enable deletion protection. If it is left unexplained, a later transformation, generator, or reviewer has to invent an assumption.

### When it applies

The rule is considered only when the guard below is true. A false guard is intentional: it means this invariant is not relevant to the current variant or modeling situation.

```evl
guard : self.isProductionScoped()
```

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.deletionProtection = true
```

This rule reads: `isProductionScoped`, `deletionProtection`, `resourceLabel`.

### Diagnostic and repair

> Production-scoped Cognito user pool does not enable deletion protection. Fix: set deletionProtection to true.

**How to fix it:**

set deletionProtection to true.

A critique does not necessarily make the model invalid. It is a deliberate quality/readiness signal; disposition it by improving the model, documenting why it is acceptable, or carrying the decision into the review record.

---

## `OAuthClientHasCallbackUrls`

**Context:** `AWSPSM!CognitoUserPoolClient`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/psm/rules/identity.evl:32`

### Why this rule exists

Checks that o auth client has callback urls. The cognito user pool client element owns the evidence for this decision, including allowed oauth flows user pool client, callback urls, resource label. At this level, the deployed authentication boundary does not silently accept avoidable production weaknesses or incomplete OAuth settings; allowing the model through without that evidence would move an unresolved choice into generated infrastructure. The concrete failure this rule prevents is: Cognito user pool client enables OAuth flows but has no callbackUrls.

### When it applies

The rule is considered only when the guard below is true. A false guard is intentional: it means this invariant is not relevant to the current variant or modeling situation.

```evl
guard : self.allowedOAuthFlowsUserPoolClient = true
```

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.callbackUrls.notEmpty()
```

This rule reads: `allowedOAuthFlowsUserPoolClient`, `callbackUrls`, `resourceLabel`.

### Diagnostic and repair

> Cognito user pool client enables OAuth flows but has no callbackUrls. Fix: add valid callback URLs for the application.

**How to fix it:**

add valid callback URLs for the application.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---

## `PreventUserExistenceErrorsRecommended`

**Context:** `AWSPSM!CognitoUserPoolClient`  
**Classification:** Advisory critique (warning)  
**Source:** `mde/validation/psm/rules/identity.evl:39`

### Why this rule exists

Advises that prevent user existence errors recommended. This is a review signal about prevent user existence errors, resource label, not a cosmetic naming preference. In this part of the model, the deployed authentication boundary does not silently accept avoidable production weaknesses or incomplete OAuth settings; the warning makes a decision visible while it can still be discussed and changed. The concrete gap is: Cognito user pool client does not explicitly prevent user-existence errors. If it is left unexplained, a later transformation, generator, or reviewer has to invent an assumption.

### When it applies

The rule has no guard, so it applies to every instance of this context in the validated model.

There is no guard expression; every instance of the context is checked.

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.preventUserExistenceErrors.hasText() and self.preventUserExistenceErrors.toUpperCase() = 'ENABLED'
```

This rule reads: `preventUserExistenceErrors`, `resourceLabel`.

### Diagnostic and repair

> Cognito user pool client does not explicitly prevent user-existence errors. Fix: set preventUserExistenceErrors to ENABLED.

**How to fix it:**

set preventUserExistenceErrors to ENABLED.

A critique does not necessarily make the model invalid. It is a deliberate quality/readiness signal; disposition it by improving the model, documenting why it is acceptable, or carrying the decision into the review record.

---

## `UnauthenticatedIdentitiesRequireReview`

**Context:** `AWSPSM!CognitoIdentityPool`  
**Classification:** Advisory critique (warning)  
**Source:** `mde/validation/psm/rules/identity.evl:48`

### Why this rule exists

Advises that unauthenticated identities require review. This is a review signal about allow unauthenticated identities, resource label, not a cosmetic naming preference. In this part of the model, the deployed authentication boundary does not silently accept avoidable production weaknesses or incomplete OAuth settings; the warning makes a decision visible while it can still be discussed and changed. The concrete gap is: Cognito identity pool allows unauthenticated identities. If it is left unexplained, a later transformation, generator, or reviewer has to invent an assumption.

### When it applies

The rule has no guard, so it applies to every instance of this context in the validated model.

There is no guard expression; every instance of the context is checked.

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.allowUnauthenticatedIdentities <> true
```

This rule reads: `allowUnauthenticatedIdentities`, `resourceLabel`.

### Diagnostic and repair

> Cognito identity pool allows unauthenticated identities. Fix: disable unauthenticated identities or add rationale and least-privilege roles.

**How to fix it:**

disable unauthenticated identities or add rationale and least-privilege roles.

A critique does not necessarily make the model invalid. It is a deliberate quality/readiness signal; disposition it by improving the model, documenting why it is acceptable, or carrying the decision into the review record.

---
