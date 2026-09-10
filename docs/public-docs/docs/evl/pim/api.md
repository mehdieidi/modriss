# PIM validation: API

API rules make a provider-independent interface internally coherent: routes are unique and well formed, each has one backend, protected operations have authorization, and validation settings are backed by schemas. Critiques add the consumer-facing documentation and error behavior that makes an API usable rather than merely callable.

Source profile: `mde/validation/pim/rules/api.evl`.

## Reading these rules

Each entry preserves the actual EVL guard and check. Read the guard as the applicability boundary, not as part of the invariant: when it is false, the rule is intentionally skipped. The diagnostic is the runtime-facing message emitted by EVL; its final sentence usually contains the repository's recommended repair.

---

## `ApiHasAtLeastOneRoute`

**Context:** `PIM!Api`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/pim/rules/api.evl:8`

### Why this rule exists

An API with no routes has no externally observable behavior to refine. Requiring at least one route prevents an empty interface from being mistaken for a finished service boundary.

### When it applies

The rule has no guard, so it applies to every instance of this context in the validated model.

There is no guard expression; every instance of the context is checked.

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.routes.notEmpty()
```

This rule reads: `routes`, `displayName`.

### Diagnostic and repair

> [PIM-API-001] API ' ' has no routes. Fix: add ApiRoute elements for the operations exposed by this API.

**How to fix it:**

add ApiRoute elements for the operations exposed by this API.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---

## `ProtectedApiHasAuthPolicy`

**Context:** `PIM!Api`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/pim/rules/api.evl:14`

### Why this rule exists

The `authRequired` flag changes the API from an open contract into a guarded boundary. This rule requires the corresponding policy object so the transformation has an actual scheme and identity decision to carry forward.

### When it applies

The rule is considered only when the guard below is true. A false guard is intentional: it means this invariant is not relevant to the current variant or modeling situation.

```evl
guard : self.authRequired.isTrue()
```

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.auth.isDefined()
```

This rule reads: `authRequired`, `auth`, `displayName`.

### Diagnostic and repair

> [PIM-API-002] API ' ' requires authentication but has no AuthPolicy. Fix: attach auth with scheme, identity provider and token/scopes validation rules.

**How to fix it:**

attach auth with scheme, identity provider and token/scopes validation rules.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---

## `ConsumerFacingApiHasPublicMetadata`

**Context:** `PIM!Api`  
**Classification:** Advisory critique (warning)  
**Source:** `mde/validation/pim/rules/api.evl:21`

### Why this rule exists

Consumers cannot safely integrate with an interface whose public name, version, or base path is missing. These fields are the stable public description of the contract, not decorative API documentation.

### When it applies

The rule is considered only when the guard below is true. A false guard is intentional: it means this invariant is not relevant to the current variant or modeling situation.

```evl
guard : self.externalConsumerFacing.isTrue()
```

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.publicName.hasText() and self.version.hasText() and self.basePath.hasText()
```

This rule reads: `externalConsumerFacing`, `publicName`, `version`, `basePath`, `displayName`.

### Diagnostic and repair

> [PIM-API-003] Consumer-facing API ' ' lacks publicName, version or basePath. Suggested fix: fill these fields so OpenAPI/docs can be generated with stable public metadata.

**How to fix it:**

This is advisory rather than a structural blocker. Decide whether the modeled situation genuinely needs the recommendation; if it does, change the referenced model features so the check evaluates to true and record the rationale when the omission is intentional.

A critique does not necessarily make the model invalid. It is a deliberate quality/readiness signal; disposition it by improving the model, documenting why it is acceptable, or carrying the decision into the review record.

---

## `GeneratedOpenApiNeedsContract`

**Context:** `PIM!Api`  
**Classification:** Advisory critique (warning)  
**Source:** `mde/validation/pim/rules/api.evl:28`

### Why this rule exists

Generating an OpenAPI document without an operation contract produces a plausible-looking artifact with no trustworthy request or response semantics. The rule keeps documentation generation downstream of an explicit contract.

### When it applies

The rule is considered only when the guard below is true. A false guard is intentional: it means this invariant is not relevant to the current variant or modeling situation.

```evl
guard : self.generatedOpenApiRequired.isTrue()
```

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.contract.isDefined()
```

This rule reads: `generatedOpenApiRequired`, `contract`, `displayName`.

### Diagnostic and repair

> [PIM-API-004] API ' ' requires generated OpenAPI but has no ApiContract. Suggested fix: add request, response and error schemas through ApiContract.

**How to fix it:**

This is advisory rather than a structural blocker. Decide whether the modeled situation genuinely needs the recommendation; if it does, change the referenced model features so the check evaluates to true and record the rationale when the omission is intentional.

A critique does not necessarily make the model invalid. It is a deliberate quality/readiness signal; disposition it by improving the model, documenting why it is acceptable, or carrying the decision into the review record.

---

## `ExactlyOneIntegration`

**Context:** `PIM!ApiRoute`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/pim/rules/api.evl:39`

### Why this rule exists

An API operation may have several conceptual consumers, but its modeled backend must be unambiguous. Requiring one integration prevents route behavior from changing depending on generator ordering or provider defaults.

### When it applies

The rule has no guard, so it applies to every instance of this context in the validated model.

There is no guard expression; every instance of the context is checked.

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : (self.functionIntegration.isDefined() and self.workflowIntegration.isUndefined()) or (self.functionIntegration.isUndefined() and self.workflowIntegration.isDefined())
```

This rule reads: `functionIntegration`, `workflowIntegration`, `displayName`.

### Diagnostic and repair

> [PIM-ROUTE-000] API route ' ' must integrate exactly one backend.

**How to fix it:**

Make the model satisfy the check shown above. Start with the referenced features, then re-run the appropriate EVL profile; do not silence the diagnostic by changing an unrelated display field.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---

## `UniqueMethodPathWithinApi`

**Context:** `PIM!ApiRoute`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/pim/rules/api.evl:46`

### Why this rule exists

The method and path pair is the address of an API operation. Two routes with the same pair cannot both be deployed predictably, even if their display names differ.

### When it applies

The rule has no guard, so it applies to every instance of this context in the validated model.

There is no guard expression; every instance of the context is checked.

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : not duplicatePimApiRouteKeys().includes(self.routeUniquenessKey())
```

This rule reads: `routeUniquenessKey`, `method`, `pathTemplate`, `api`.

### Diagnostic and repair

> [PIM-ROUTE-001] API route ' ' is duplicated within API ' '. Fix: change the method or pathTemplate so each route is unique inside the API.

**How to fix it:**

change the method or pathTemplate so each route is unique inside the API.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---

## `RoutePathStartsWithSlash`

**Context:** `PIM!ApiRoute`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/pim/rules/api.evl:52`

### Why this rule exists

A route path is interpreted as a URI path by later transformations. Requiring the leading slash makes the contract canonical before it is translated into API Gateway or another provider's route syntax.

### When it applies

The rule has no guard, so it applies to every instance of this context in the validated model.

There is no guard expression; every instance of the context is checked.

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.pathTemplate.hasText() and self.pathTemplate.startsWith("/")
```

This rule reads: `pathTemplate`.

### Diagnostic and repair

> [PIM-ROUTE-002] API route pathTemplate ' <missing> ' must start with '/'. Fix: use a portable absolute path template such as /orders/{orderId}.

**How to fix it:**

use a portable absolute path template such as /orders/{orderId}.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---

## `RouteIntegrationIsExactlyOneBackend`

**Context:** `PIM!ApiRoute`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/pim/rules/api.evl:58`

### Why this rule exists

This is the route-level version of the API integration rule: one route must resolve to one backend target. A missing target is incomplete, while two targets leave invocation semantics undefined.

### When it applies

The rule has no guard, so it applies to every instance of this context in the validated model.

There is no guard expression; every instance of the context is checked.

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : (self.functionIntegration.isDefined() and self.workflowIntegration.isUndefined()) or (self.functionIntegration.isUndefined() and self.workflowIntegration.isDefined())
```

This rule reads: `functionIntegration`, `workflowIntegration`, `displayName`.

### Diagnostic and repair

> [PIM-ROUTE-003] API route ' ' must integrate with exactly one backend. Fix: set either functionIntegration or workflowIntegration, not both and not neither.

**How to fix it:**

set either functionIntegration or workflowIntegration, not both and not neither.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---

## `ProtectedRouteHasAuthorization`

**Context:** `PIM!ApiRoute`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/pim/rules/api.evl:65`

### Why this rule exists

Route protection is finer-grained than API protection. This rule catches the dangerous case where an API is generally secured but one sensitive operation has no authorization policy of its own.

### When it applies

The rule is considered only when the guard below is true. A false guard is intentional: it means this invariant is not relevant to the current variant or modeling situation.

```evl
guard : self.authRequired.isTrue() or self.api.authRequired.isTrue()
```

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.authorization.isDefined()
```

This rule reads: `authRequired`, `api`, `authorization`, `method`, `pathTemplate`.

### Diagnostic and repair

> [PIM-ROUTE-004] Protected route ' ' has no AuthorizationPolicy. Fix: attach authorization with ruleExpression, role/scope, allowed principals or permissions.

**How to fix it:**

attach authorization with ruleExpression, role/scope, allowed principals or permissions.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---

## `RequestValidationNeedsRequestSchema`

**Context:** `PIM!ApiRoute`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/pim/rules/api.evl:72`

### Why this rule exists

Request validation has meaning only when the validator has a schema to apply. The rule prevents a boolean validation switch from promising input protection that no model shape can implement.

### When it applies

The rule is considered only when the guard below is true. A false guard is intentional: it means this invariant is not relevant to the current variant or modeling situation.

```evl
guard : self.requestValidationRequired.isTrue()
```

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.requestSchema.isDefined()
```

This rule reads: `requestValidationRequired`, `requestSchema`, `displayName`.

### Diagnostic and repair

> [PIM-ROUTE-005] Route ' ' requires request validation but has no requestSchema. Fix: attach a request Schema or set requestValidationRequired to false.

**How to fix it:**

attach a request Schema or set requestValidationRequired to false.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---

## `ResponseValidationNeedsResponseSchema`

**Context:** `PIM!ApiRoute`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/pim/rules/api.evl:79`

### Why this rule exists

Response validation protects the contract consumers rely on, but it cannot work from an absent response shape. Requiring the schema keeps generated gateways and tests aligned with the modeled output.

### When it applies

The rule is considered only when the guard below is true. A false guard is intentional: it means this invariant is not relevant to the current variant or modeling situation.

```evl
guard : self.responseValidationRequired.isTrue()
```

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.responseSchema.isDefined()
```

This rule reads: `responseValidationRequired`, `responseSchema`, `displayName`.

### Diagnostic and repair

> [PIM-ROUTE-006] Route ' ' requires response validation but has no responseSchema. Fix: attach a response Schema or set responseValidationRequired to false.

**How to fix it:**

attach a response Schema or set responseValidationRequired to false.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---

## `PublicRouteShouldDescribeConsumers`

**Context:** `PIM!ApiRoute`  
**Classification:** Advisory critique (warning)  
**Source:** `mde/validation/pim/rules/api.evl:86`

### Why this rule exists

A public route is an integration promise to clients outside the service boundary. Recording its consumer expectations gives API review and generation enough context to preserve compatibility and error behavior.

### When it applies

The rule is considered only when the guard below is true. A false guard is intentional: it means this invariant is not relevant to the current variant or modeling situation.

```evl
guard : self.publicRoute.isTrue()
```

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.descriptionForConsumers.hasText()
```

This rule reads: `publicRoute`, `descriptionForConsumers`, `method`, `pathTemplate`.

### Diagnostic and repair

> [PIM-ROUTE-007] Public route ' ' lacks descriptionForConsumers. Suggested fix: explain what the route does, key request/response fields and important errors.

**How to fix it:**

This is advisory rather than a structural blocker. Decide whether the modeled situation genuinely needs the recommendation; if it does, change the referenced model features so the check evaluates to true and record the rationale when the omission is intentional.

A critique does not necessarily make the model invalid. It is a deliberate quality/readiness signal; disposition it by improving the model, documenting why it is acceptable, or carrying the decision into the review record.

---

## `ErrorMappingShouldBeActionable`

**Context:** `PIM!ErrorMapping`  
**Classification:** Advisory critique (warning)  
**Source:** `mde/validation/pim/rules/api.evl:97`

### Why this rule exists

An error mapping that names no client status or response behavior leaves failures to provider defaults. This critique asks the modeler to decide what the caller can understand and what the operator can diagnose.

### When it applies

The rule has no guard, so it applies to every instance of this context in the validated model.

There is no guard expression; every instance of the context is checked.

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.domainErrorCode.hasText() and self.abstractStatusClass.hasText() and self.responseMessage.hasText()
```

This rule reads: `domainErrorCode`, `abstractStatusClass`, `responseMessage`, `displayName`.

### Diagnostic and repair

> [PIM-ROUTE-008] ErrorMapping ' ' is missing domainErrorCode, abstractStatusClass or responseMessage. Suggested fix: define a stable domain error, map it to an abstract status class, and write a client-safe response message.

**How to fix it:**

This is advisory rather than a structural blocker. Decide whether the modeled situation genuinely needs the recommendation; if it does, change the referenced model features so the check evaluates to true and record the rationale when the omission is intentional.

A critique does not necessarily make the model invalid. It is a deliberate quality/readiness signal; disposition it by improving the model, documenting why it is acceptable, or carrying the decision into the review record.

---
