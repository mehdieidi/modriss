# AWS PSM validation: Identity

Cognito rules protect production authentication posture and OAuth completeness. They make MFA, deletion protection, callback URLs, user-existence behavior, and unauthenticated identity review explicit rather than silently accepting an insecure default.

Source profile: `mde/validation/psm/rules/identity.evl`.

## Reading these rules

Each entry preserves the actual EVL guard and check. Treat the guard as the applicability boundary. When it evaluates to false, EVL skips the rule. The diagnostic is the message emitted at runtime, and its final sentence usually gives the repository's recommended repair.

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

The rule checks whether mfa decision made for production critical pools. The cognito user pool element provides the relevant evidence through is production scoped, mfa decision, resource label. At this level, the deployed authentication boundary does not silently accept avoidable production weaknesses or incomplete OAuth settings. Missing evidence would leave an unresolved choice in generated infrastructure. The rule prevents the following failure: Production-scoped Cognito user pool has no MFA decision.

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

The rule checks whether production user pool should use deletion protection. It examines is production scoped, deletion protection, resource label. Within this part of the model, the deployed authentication boundary does not silently accept avoidable production weaknesses or incomplete OAuth settings. The gap is Production-scoped Cognito user pool does not enable deletion protection. A later transformation, generator, or reviewer would otherwise have to infer the missing decision.

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

The rule checks whether o auth client has callback urls. The cognito user pool client element provides the relevant evidence through allowed oauth flows user pool client, callback urls, resource label. At this level, the deployed authentication boundary does not silently accept avoidable production weaknesses or incomplete OAuth settings. Missing evidence would leave an unresolved choice in generated infrastructure. The rule prevents the following failure: Cognito user pool client enables OAuth flows but has no callbackUrls.

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

The rule checks whether prevent user existence errors recommended. It examines prevent user existence errors, resource label. Within this part of the model, the deployed authentication boundary does not silently accept avoidable production weaknesses or incomplete OAuth settings. The gap is Cognito user pool client does not explicitly prevent user-existence errors. A later transformation, generator, or reviewer would otherwise have to infer the missing decision.

### When it applies

No guard is defined, so the check runs for every instance of this context in the validated model.

No guard expression is present. Every instance of the context is checked.

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

The rule checks whether unauthenticated identities require review. It examines allow unauthenticated identities, resource label. Within this part of the model, the deployed authentication boundary does not silently accept avoidable production weaknesses or incomplete OAuth settings. The gap is Cognito identity pool allows unauthenticated identities. A later transformation, generator, or reviewer would otherwise have to infer the missing decision.

### When it applies

No guard is defined, so the check runs for every instance of this context in the validated model.

No guard expression is present. Every instance of the context is checked.

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
