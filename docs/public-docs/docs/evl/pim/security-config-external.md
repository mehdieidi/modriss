# PIM validation: Security Config External

Security, configuration, and external-system rules stop credentials, identity assumptions, authorization logic, and provider endpoints from remaining implicit. The rules are especially concerned with secret references, least privilege, federation, and environment-specific ownership.

Source profile: `mde/validation/pim/rules/security-config-external.evl`.

## Reading these rules

Each entry preserves the actual EVL guard and check. Treat the guard as the applicability boundary. When it evaluates to false, EVL skips the rule. The diagnostic is the message emitted at runtime, and its final sentence usually gives the repository's recommended repair.

---

## `AdapterHasEndpoint`

**Context:** `PIM!ExternalAdapter`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/pim/rules/security-config-external.evl:8`

### Why this rule exists

The rule checks whether adapter has endpoint. The external adapter element provides the relevant evidence through endpoint, display name. At this level, credentials, identity, secrets, authorization, configuration, and external endpoints have explicit owners and safeguards. Missing evidence would leave an unresolved choice in generated infrastructure. The rule prevents the following failure: [PIM-EXT-000] ExternalAdapter ' ' has no endpoint.

### When it applies

No guard is defined, so the check runs for every instance of this context in the validated model.

No guard expression is present. Every instance of the context is checked.

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.endpoint.isDefined()
```

This rule reads: `endpoint`, `displayName`.

### Diagnostic and repair

> [PIM-EXT-000] ExternalAdapter ' ' has no endpoint. Fix: attach the canonical ExternalEndpoint.

**How to fix it:**

attach the canonical ExternalEndpoint.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---

## `CredentialRequirementForCredentialedAdapter`

**Context:** `PIM!ExternalAdapter`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/pim/rules/security-config-external.evl:14`

### Why this rule exists

The rule checks whether credential requirement for credentialed adapter. The external adapter element provides the relevant evidence through endpoint, credentials, display name. At this level, credentials, identity, secrets, authorization, configuration, and external endpoints have explicit owners and safeguards. Missing evidence would leave an unresolved choice in generated infrastructure. The rule prevents the following failure: [PIM-EXT-001] ExternalAdapter ' ' requires credentials but has none.

### When it applies

The rule is considered only when the guard below is true. A false guard is intentional: it means this invariant is not relevant to the current variant or modeling situation.

```evl
guard : self.endpoint.isDefined() and self.endpoint.credentialsRequired.isTrue()
```

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.credentials.notEmpty()
```

This rule reads: `endpoint`, `credentials`, `displayName`.

### Diagnostic and repair

> [PIM-EXT-001] ExternalAdapter ' ' requires credentials but has none. Fix: add CredentialRequirement entries and link them to Secret references.

**How to fix it:**

add CredentialRequirement entries and link them to Secret references.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---

## `ExternalAdapterShouldDescribeEndpointAndProtocol`

**Context:** `PIM!ExternalAdapter`  
**Classification:** Advisory critique (warning)  
**Source:** `mde/validation/pim/rules/security-config-external.evl:21`

### Why this rule exists

The rule checks whether external adapter should describe endpoint and protocol. It examines endpoint, display name. Within this part of the model, credentials, identity, secrets, authorization, configuration, and external endpoints have explicit owners and safeguards. The gap is [PIM-EXT-002] ExternalAdapter ' ' lacks externalSystemName, protocolFamily or endpointDescription. Suggested fix: describe the external system boundary without embedding provider-specific deployment details. A later transformation, generator, or reviewer would otherwise have to infer the missing decision.

### When it applies

The rule is considered only when the guard below is true. A false guard is intentional: it means this invariant is not relevant to the current variant or modeling situation.

```evl
guard : self.endpoint.isDefined()
```

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.endpoint.externalSystemName.hasText() and self.endpoint.protocolFamily.hasText() and self.endpoint.endpointDescription.hasText()
```

This rule reads: `endpoint`, `displayName`.

### Diagnostic and repair

> [PIM-EXT-002] ExternalAdapter ' ' lacks externalSystemName, protocolFamily or endpointDescription. Suggested fix: describe the external system boundary without embedding provider-specific deployment details.

**How to fix it:**

This is advisory rather than a structural blocker. Decide whether the modeled situation genuinely needs the recommendation; if it does, change the referenced model features so the check evaluates to true and record the rationale when the omission is intentional.

A critique does not necessarily make the model invalid. It is a deliberate quality/readiness signal; disposition it by improving the model, documenting why it is acceptable, or carrying the decision into the review record.

---

## `RateLimitedAdapterShouldHaveResilience`

**Context:** `PIM!ExternalAdapter`  
**Classification:** Advisory critique (warning)  
**Source:** `mde/validation/pim/rules/security-config-external.evl:28`

### Why this rule exists

The rule checks whether rate limited adapter should have resilience. It examines endpoint, resilience, display name. Within this part of the model, credentials, identity, secrets, authorization, configuration, and external endpoints have explicit owners and safeguards. The gap is [PIM-EXT-003] ExternalAdapter ' ' is rate-limited or private-network dependent but lacks resilience policy. Suggested fix: attach ResiliencePolicy with retry, timeout, fallback and circuit-breaker decisions. A later transformation, generator, or reviewer would otherwise have to infer the missing decision.

### When it applies

The rule is considered only when the guard below is true. A false guard is intentional: it means this invariant is not relevant to the current variant or modeling situation.

```evl
guard : self.endpoint.isDefined() and (self.endpoint.rateLimitedByProvider.isTrue() or self.endpoint.privateNetworkRequired.isTrue())
```

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.resilience.isDefined()
```

This rule reads: `endpoint`, `resilience`, `displayName`.

### Diagnostic and repair

> [PIM-EXT-003] ExternalAdapter ' ' is rate-limited or private-network dependent but lacks resilience policy. Suggested fix: attach ResiliencePolicy with retry, timeout, fallback and circuit-breaker decisions.

**How to fix it:**

This is advisory rather than a structural blocker. Decide whether the modeled situation genuinely needs the recommendation; if it does, change the referenced model features so the check evaluates to true and record the rationale when the omission is intentional.

A critique does not necessarily make the model invalid. It is a deliberate quality/readiness signal; disposition it by improving the model, documenting why it is acceptable, or carrying the decision into the review record.

---

## `ConfigurationSetShouldApplySomewhere`

**Context:** `PIM!ConfigurationSet`  
**Classification:** Advisory critique (warning)  
**Source:** `mde/validation/pim/rules/security-config-external.evl:39`

### Why this rule exists

The rule checks whether configuration set should apply somewhere. It examines environments, applies to, display name. Within this part of the model, credentials, identity, secrets, authorization, configuration, and external endpoints have explicit owners and safeguards. The gap is [PIM-CONFIG-001] ConfigurationSet ' ' is not attached to environments or targets. Suggested fix: link environments/appliesTo so generated configuration is scoped correctly. A later transformation, generator, or reviewer would otherwise have to infer the missing decision.

### When it applies

No guard is defined, so the check runs for every instance of this context in the validated model.

No guard expression is present. Every instance of the context is checked.

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.environments.notEmpty() or self.appliesTo.notEmpty()
```

This rule reads: `environments`, `appliesTo`, `displayName`.

### Diagnostic and repair

> [PIM-CONFIG-001] ConfigurationSet ' ' is not attached to environments or targets. Suggested fix: link environments/appliesTo so generated configuration is scoped correctly.

**How to fix it:**

This is advisory rather than a structural blocker. Decide whether the modeled situation genuinely needs the recommendation; if it does, change the referenced model features so the check evaluates to true and record the rationale when the omission is intentional.

A critique does not necessarily make the model invalid. It is a deliberate quality/readiness signal; disposition it by improving the model, documenting why it is acceptable, or carrying the decision into the review record.

---

## `RequiredParameterShouldHaveDefaultOrStageSpecificDecision`

**Context:** `PIM!ConfigParameter`  
**Classification:** Advisory critique (warning)  
**Source:** `mde/validation/pim/rules/security-config-external.evl:49`

### Why this rule exists

The rule checks whether required parameter should have default or stage specific decision. It examines required, default value, stage specific, secret, display name. Within this part of the model, credentials, identity, secrets, authorization, configuration, and external endpoints have explicit owners and safeguards. The gap is [PIM-CONFIG-002] Required ConfigParameter ' ' has no default, stage-specific decision or secret flag. Suggested fix: provide a safe default, mark stageSpecific, or model it as a secret. A later transformation, generator, or reviewer would otherwise have to infer the missing decision.

### When it applies

The rule is considered only when the guard below is true. A false guard is intentional: it means this invariant is not relevant to the current variant or modeling situation.

```evl
guard : self.required.isTrue()
```

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.defaultValue.hasText() or self.stageSpecific.isTrue() or self.secret.isTrue()
```

This rule reads: `required`, `defaultValue`, `stageSpecific`, `secret`, `displayName`.

### Diagnostic and repair

> [PIM-CONFIG-002] Required ConfigParameter ' ' has no default, stage-specific decision or secret flag. Suggested fix: provide a safe default, mark stageSpecific, or model it as a secret.

**How to fix it:**

This is advisory rather than a structural blocker. Decide whether the modeled situation genuinely needs the recommendation; if it does, change the referenced model features so the check evaluates to true and record the rationale when the omission is intentional.

A critique does not necessarily make the model invalid. It is a deliberate quality/readiness signal; disposition it by improving the model, documenting why it is acceptable, or carrying the decision into the review record.

---

## `SecretEnvironmentVariableReferencesSecret`

**Context:** `PIM!EnvironmentVariable`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/pim/rules/security-config-external.evl:60`

### Why this rule exists

The rule checks whether secret environment variable references secret. The environment variable element provides the relevant evidence through secret reference, secret, variable name. At this level, credentials, identity, secrets, authorization, configuration, and external endpoints have explicit owners and safeguards. Missing evidence would leave an unresolved choice in generated infrastructure. The rule prevents the following failure: [PIM-CONFIG-003] EnvironmentVariable ' ' is a secret reference but has no secret.

### When it applies

The rule is considered only when the guard below is true. A false guard is intentional: it means this invariant is not relevant to the current variant or modeling situation.

```evl
guard : self.secretReference.isTrue()
```

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.secret.isDefined()
```

This rule reads: `secretReference`, `secret`, `variableName`.

### Diagnostic and repair

> [PIM-CONFIG-003] EnvironmentVariable ' ' is a secret reference but has no secret. Fix: link a Secret and avoid storing plaintext values.

**How to fix it:**

link a Secret and avoid storing plaintext values.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---

## `EnvironmentVariableNameIsPortable`

**Context:** `PIM!EnvironmentVariable`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/pim/rules/security-config-external.evl:67`

### Why this rule exists

The rule checks whether environment variable name is portable. The environment variable element provides the relevant evidence through variable name. At this level, credentials, identity, secrets, authorization, configuration, and external endpoints have explicit owners and safeguards. Missing evidence would leave an unresolved choice in generated infrastructure. The rule prevents the following failure: [PIM-CONFIG-004] EnvironmentVariable name ' <missing> ' is not portable.

### When it applies

No guard is defined, so the check runs for every instance of this context in the validated model.

No guard expression is present. Every instance of the context is checked.

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.variableName.hasText() and self.variableName.matches("^[A-Z][A-Z0-9_]*$")
```

This rule reads: `variableName`.

### Diagnostic and repair

> [PIM-CONFIG-004] EnvironmentVariable name ' <missing> ' is not portable. Fix: use uppercase snake case, for example PAYMENT_API_BASE_URL.

**How to fix it:**

use uppercase snake case, for example PAYMENT_API_BASE_URL.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---

## `SecretNotPlainEnvironmentValue`

**Context:** `PIM!EnvironmentVariable`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/pim/rules/security-config-external.evl:73`

### Why this rule exists

The rule checks whether secret not plain environment value. The environment variable element provides the relevant evidence through secret reference, value source, variable name. At this level, credentials, identity, secrets, authorization, configuration, and external endpoints have explicit owners and safeguards. Missing evidence would leave an unresolved choice in generated infrastructure. The rule prevents the following failure: [PIM-CONFIG-005] Secret EnvironmentVariable ' ' appears to define a plain value source.

### When it applies

The rule is considered only when the guard below is true. A false guard is intentional: it means this invariant is not relevant to the current variant or modeling situation.

```evl
guard : self.secretReference.isTrue()
```

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : not self.valueSource.hasText() or self.valueSource.matches("(?i).*(secret|reference|parameter|vault|manager).*" )
```

This rule reads: `secretReference`, `valueSource`, `variableName`.

### Diagnostic and repair

> [PIM-CONFIG-005] Secret EnvironmentVariable ' ' appears to define a plain value source. Fix: reference a Secret/ConfigParameter location instead of storing the secret value directly.

**How to fix it:**

reference a Secret/ConfigParameter location instead of storing the secret value directly.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---

## `SecretReferenceOnly`

**Context:** `PIM!Secret`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/pim/rules/security-config-external.evl:84`

### Why this rule exists

The rule checks whether secret reference only. The secret element provides the relevant evidence through generated reference only, display name. At this level, credentials, identity, secrets, authorization, configuration, and external endpoints have explicit owners and safeguards. Missing evidence would leave an unresolved choice in generated infrastructure. The rule prevents the following failure: [PIM-SECRET-001] Secret ' ' is not marked generatedReferenceOnly.

### When it applies

No guard is defined, so the check runs for every instance of this context in the validated model.

No guard expression is present. Every instance of the context is checked.

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.generatedReferenceOnly.isTrue()
```

This rule reads: `generatedReferenceOnly`, `displayName`.

### Diagnostic and repair

> [PIM-SECRET-001] Secret ' ' is not marked generatedReferenceOnly. Fix: set generatedReferenceOnly to true so generated artifacts only reference secret locations and never contain secret values.

**How to fix it:**

set generatedReferenceOnly to true so generated artifacts only reference secret locations and never contain secret values.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---

## `RotationRequiredNeedsFrequency`

**Context:** `PIM!Secret`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/pim/rules/security-config-external.evl:90`

### Why this rule exists

The rule checks whether rotation required needs frequency. The secret element provides the relevant evidence through rotation required, rotation frequency, display name. At this level, credentials, identity, secrets, authorization, configuration, and external endpoints have explicit owners and safeguards. Missing evidence would leave an unresolved choice in generated infrastructure. The rule prevents the following failure: [PIM-SECRET-002] Secret ' ' requires rotation but has no rotationFrequency.

### When it applies

The rule is considered only when the guard below is true. A false guard is intentional: it means this invariant is not relevant to the current variant or modeling situation.

```evl
guard : self.rotationRequired.isTrue()
```

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.rotationFrequency.hasText()
```

This rule reads: `rotationRequired`, `rotationFrequency`, `displayName`.

### Diagnostic and repair

> [PIM-SECRET-002] Secret ' ' requires rotation but has no rotationFrequency. Fix: specify an abstract rotation cadence such as every-30-days or per-release.

**How to fix it:**

specify an abstract rotation cadence such as every-30-days or per-release.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---

## `EnvironmentSpecificSecretShouldHaveOwner`

**Context:** `PIM!Secret`  
**Classification:** Advisory critique (warning)  
**Source:** `mde/validation/pim/rules/security-config-external.evl:97`

### Why this rule exists

The rule checks whether environment specific secret should have owner. It examines environment specific, owner team, display name. Within this part of the model, credentials, identity, secrets, authorization, configuration, and external endpoints have explicit owners and safeguards. The gap is [PIM-SECRET-003] Environment-specific secret ' ' has no ownerTeam. Suggested fix: set the team responsible for rotation, provisioning and incident response. A later transformation, generator, or reviewer would otherwise have to infer the missing decision.

### When it applies

The rule is considered only when the guard below is true. A false guard is intentional: it means this invariant is not relevant to the current variant or modeling situation.

```evl
guard : self.environmentSpecific.isTrue()
```

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.ownerTeam.hasText()
```

This rule reads: `environmentSpecific`, `ownerTeam`, `displayName`.

### Diagnostic and repair

> [PIM-SECRET-003] Environment-specific secret ' ' has no ownerTeam. Suggested fix: set the team responsible for rotation, provisioning and incident response.

**How to fix it:**

This is advisory rather than a structural blocker. Decide whether the modeled situation genuinely needs the recommendation; if it does, change the referenced model features so the check evaluates to true and record the rationale when the omission is intentional.

A critique does not necessarily make the model invalid. It is a deliberate quality/readiness signal; disposition it by improving the model, documenting why it is acceptable, or carrying the decision into the review record.

---

## `CredentialRequirementHasSecret`

**Context:** `PIM!CredentialRequirement`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/pim/rules/security-config-external.evl:108`

### Why this rule exists

The rule checks whether credential requirement has secret. The credential requirement element provides the relevant evidence through secret, display name. At this level, credentials, identity, secrets, authorization, configuration, and external endpoints have explicit owners and safeguards. Missing evidence would leave an unresolved choice in generated infrastructure. The rule prevents the following failure: [PIM-CRED-001] CredentialRequirement ' ' is not linked to a Secret.

### When it applies

No guard is defined, so the check runs for every instance of this context in the validated model.

No guard expression is present. Every instance of the context is checked.

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.secret.isDefined()
```

This rule reads: `secret`, `displayName`.

### Diagnostic and repair

> [PIM-CRED-001] CredentialRequirement ' ' is not linked to a Secret. Fix: create/link a Secret that will be referenced by generated configuration.

**How to fix it:**

create/link a Secret that will be referenced by generated configuration.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---

## `CredentialRequirementShouldExplainPurpose`

**Context:** `PIM!CredentialRequirement`  
**Classification:** Advisory critique (warning)  
**Source:** `mde/validation/pim/rules/security-config-external.evl:114`

### Why this rule exists

The rule checks whether credential requirement should explain purpose. It examines purpose, display name. Within this part of the model, credentials, identity, secrets, authorization, configuration, and external endpoints have explicit owners and safeguards. The gap is [PIM-CRED-002] CredentialRequirement ' ' has no purpose. Suggested fix: describe why this credential is needed and which external call or adapter uses it. A later transformation, generator, or reviewer would otherwise have to infer the missing decision.

### When it applies

No guard is defined, so the check runs for every instance of this context in the validated model.

No guard expression is present. Every instance of the context is checked.

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.purpose.hasText()
```

This rule reads: `purpose`, `displayName`.

### Diagnostic and repair

> [PIM-CRED-002] CredentialRequirement ' ' has no purpose. Suggested fix: describe why this credential is needed and which external call or adapter uses it.

**How to fix it:**

This is advisory rather than a structural blocker. Decide whether the modeled situation genuinely needs the recommendation; if it does, change the referenced model features so the check evaluates to true and record the rationale when the omission is intentional.

A critique does not necessarily make the model invalid. It is a deliberate quality/readiness signal; disposition it by improving the model, documenting why it is acceptable, or carrying the decision into the review record.

---

## `FederatedIdentityShouldDescribeTokenAndAttributes`

**Context:** `PIM!IdentityProvider`  
**Classification:** Advisory critique (warning)  
**Source:** `mde/validation/pim/rules/security-config-external.evl:124`

### Why this rule exists

The rule checks whether federated identity should describe token and attributes. It examines federation required, token type, user attribute requirements, display name. Within this part of the model, credentials, identity, secrets, authorization, configuration, and external endpoints have explicit owners and safeguards. The gap is [PIM-SEC-001] Federated IdentityProvider ' ' lacks tokenType or userAttributeRequirements. Suggested fix: describe token and required claims/attributes. A later transformation, generator, or reviewer would otherwise have to infer the missing decision.

### When it applies

The rule is considered only when the guard below is true. A false guard is intentional: it means this invariant is not relevant to the current variant or modeling situation.

```evl
guard : self.federationRequired.isTrue()
```

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.tokenType.hasText() and self.userAttributeRequirements.hasText()
```

This rule reads: `federationRequired`, `tokenType`, `userAttributeRequirements`, `displayName`.

### Diagnostic and repair

> [PIM-SEC-001] Federated IdentityProvider ' ' lacks tokenType or userAttributeRequirements. Suggested fix: describe token and required claims/attributes.

**How to fix it:**

This is advisory rather than a structural blocker. Decide whether the modeled situation genuinely needs the recommendation; if it does, change the referenced model features so the check evaluates to true and record the rationale when the omission is intentional.

A critique does not necessarily make the model invalid. It is a deliberate quality/readiness signal; disposition it by improving the model, documenting why it is acceptable, or carrying the decision into the review record.

---

## `PrivilegedPrincipalNeedsPermissions`

**Context:** `PIM!Principal`  
**Classification:** Advisory critique (warning)  
**Source:** `mde/validation/pim/rules/security-config-external.evl:135`

### Why this rule exists

The rule checks whether privileged principal needs permissions. It examines privileged, permissions, display name. Within this part of the model, credentials, identity, secrets, authorization, configuration, and external endpoints have explicit owners and safeguards. The gap is [PIM-SEC-002] Privileged Principal ' ' has no permissions. Suggested fix: either remove privileged=true or model the exact permissions and least-privilege decision. A later transformation, generator, or reviewer would otherwise have to infer the missing decision.

### When it applies

The rule is considered only when the guard below is true. A false guard is intentional: it means this invariant is not relevant to the current variant or modeling situation.

```evl
guard : self.privileged.isTrue()
```

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.permissions.notEmpty()
```

This rule reads: `privileged`, `permissions`, `displayName`.

### Diagnostic and repair

> [PIM-SEC-002] Privileged Principal ' ' has no permissions. Suggested fix: either remove privileged=true or model the exact permissions and least-privilege decision.

**How to fix it:**

This is advisory rather than a structural blocker. Decide whether the modeled situation genuinely needs the recommendation; if it does, change the referenced model features so the check evaluates to true and record the rationale when the omission is intentional.

A critique does not necessarily make the model invalid. It is a deliberate quality/readiness signal; disposition it by improving the model, documenting why it is acceptable, or carrying the decision into the review record.

---

## `PermissionIsScoped`

**Context:** `PIM!Permission`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/pim/rules/security-config-external.evl:146`

### Why this rule exists

The rule checks whether permission is scoped. The permission element provides the relevant evidence through action, resource, target resource, display name. At this level, credentials, identity, secrets, authorization, configuration, and external endpoints have explicit owners and safeguards. Missing evidence would leave an unresolved choice in generated infrastructure. The rule prevents the following failure: [PIM-SEC-003] Permission ' ' is not scoped.

### When it applies

No guard is defined, so the check runs for every instance of this context in the validated model.

No guard expression is present. Every instance of the context is checked.

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.action.hasText() and self.resource.hasText() and self.targetResource.isDefined()
```

This rule reads: `action`, `resource`, `targetResource`, `displayName`.

### Diagnostic and repair

> [PIM-SEC-003] Permission ' ' is not scoped. Fix: fill action, resource and targetResource using provider-independent action/resource descriptions.

**How to fix it:**

fill action, resource and targetResource using provider-independent action/resource descriptions.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---

## `LeastPrivilegeShouldBeConfirmed`

**Context:** `PIM!Permission`  
**Classification:** Advisory critique (warning)  
**Source:** `mde/validation/pim/rules/security-config-external.evl:152`

### Why this rule exists

The rule checks whether least privilege should be confirmed. It examines least privilege confirmed, action, resource, display name. Within this part of the model, credentials, identity, secrets, authorization, configuration, and external endpoints have explicit owners and safeguards. The gap is [PIM-SEC-004] Permission ' ' has not been confirmed as least-privilege or uses wildcards. Suggested fix: narrow action/resource and set leastPrivilegeConfirmed to true after review. A later transformation, generator, or reviewer would otherwise have to infer the missing decision.

### When it applies

No guard is defined, so the check runs for every instance of this context in the validated model.

No guard expression is present. Every instance of the context is checked.

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.leastPrivilegeConfirmed.isTrue() and self.action.hasText() and self.resource.hasText() and not self.action.matches(".*\\*.*") and not self.resource.matches(".*\\*.*")
```

This rule reads: `leastPrivilegeConfirmed`, `action`, `resource`, `displayName`.

### Diagnostic and repair

> [PIM-SEC-004] Permission ' ' has not been confirmed as least-privilege or uses wildcards. Suggested fix: narrow action/resource and set leastPrivilegeConfirmed to true after review.

**How to fix it:**

This is advisory rather than a structural blocker. Decide whether the modeled situation genuinely needs the recommendation; if it does, change the referenced model features so the check evaluates to true and record the rationale when the omission is intentional.

A critique does not necessarily make the model invalid. It is a deliberate quality/readiness signal; disposition it by improving the model, documenting why it is acceptable, or carrying the decision into the review record.

---

## `AuthPolicyHasScheme`

**Context:** `PIM!AuthPolicy`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/pim/rules/security-config-external.evl:162`

### Why this rule exists

The rule checks whether auth policy has scheme. The auth policy element provides the relevant evidence through auth scheme, identity provider, display name. At this level, credentials, identity, secrets, authorization, configuration, and external endpoints have explicit owners and safeguards. Missing evidence would leave an unresolved choice in generated infrastructure. The rule prevents the following failure: [PIM-SEC-005] AuthPolicy ' ' lacks authScheme or identityProvider.

### When it applies

No guard is defined, so the check runs for every instance of this context in the validated model.

No guard expression is present. Every instance of the context is checked.

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.authScheme.hasText() and self.identityProvider.isDefined()
```

This rule reads: `authScheme`, `identityProvider`, `displayName`.

### Diagnostic and repair

> [PIM-SEC-005] AuthPolicy ' ' lacks authScheme or identityProvider. Fix: define the authentication scheme and link the identity provider.

**How to fix it:**

define the authentication scheme and link the identity provider.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---

## `AuthorizationPolicyHasDecisionLogic`

**Context:** `PIM!AuthorizationPolicy`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/pim/rules/security-config-external.evl:172`

### Why this rule exists

The rule checks whether authorization policy has decision logic. The authorization policy element provides the relevant evidence through rule expression, role or scope required, allowed principals, permissions, display name. At this level, credentials, identity, secrets, authorization, configuration, and external endpoints have explicit owners and safeguards. Missing evidence would leave an unresolved choice in generated infrastructure. The rule prevents the following failure: [PIM-SEC-006] AuthorizationPolicy ' ' has no decision logic.

### When it applies

No guard is defined, so the check runs for every instance of this context in the validated model.

No guard expression is present. Every instance of the context is checked.

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.ruleExpression.hasText() or self.roleOrScopeRequired.hasText() or self.allowedPrincipals.notEmpty() or self.permissions.notEmpty()
```

This rule reads: `ruleExpression`, `roleOrScopeRequired`, `allowedPrincipals`, `permissions`, `displayName`.

### Diagnostic and repair

> [PIM-SEC-006] AuthorizationPolicy ' ' has no decision logic. Fix: provide ruleExpression, role/scope, allowed principals or permissions.

**How to fix it:**

provide ruleExpression, role/scope, allowed principals or permissions.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---

## `ResourceLevelAuthorizationShouldHaveExpression`

**Context:** `PIM!AuthorizationPolicy`  
**Classification:** Advisory critique (warning)  
**Source:** `mde/validation/pim/rules/security-config-external.evl:179`

### Why this rule exists

The rule checks whether resource level authorization should have expression. It examines resource level authorization, rule expression, display name. Within this part of the model, credentials, identity, secrets, authorization, configuration, and external endpoints have explicit owners and safeguards. The gap is [PIM-SEC-007] Resource-level AuthorizationPolicy ' ' has no ruleExpression. Suggested fix: define the rule that binds principal, resource identity and permitted operation. A later transformation, generator, or reviewer would otherwise have to infer the missing decision.

### When it applies

The rule is considered only when the guard below is true. A false guard is intentional: it means this invariant is not relevant to the current variant or modeling situation.

```evl
guard : self.resourceLevelAuthorization.isTrue()
```

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.ruleExpression.hasText()
```

This rule reads: `resourceLevelAuthorization`, `ruleExpression`, `displayName`.

### Diagnostic and repair

> [PIM-SEC-007] Resource-level AuthorizationPolicy ' ' has no ruleExpression. Suggested fix: define the rule that binds principal, resource identity and permitted operation.

**How to fix it:**

This is advisory rather than a structural blocker. Decide whether the modeled situation genuinely needs the recommendation; if it does, change the referenced model features so the check evaluates to true and record the rationale when the omission is intentional.

A critique does not necessarily make the model invalid. It is a deliberate quality/readiness signal; disposition it by improving the model, documenting why it is acceptable, or carrying the decision into the review record.

---
