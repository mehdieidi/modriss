# AWS PSM validation — API

API Gateway rules verify that the concrete API graph has routes, integrations, authorizers, certificates, stages, logs, metrics, throttles, and credentials that agree with one another. They catch provider-level omissions that a PIM API contract cannot see.

Source profile: `mde/validation/psm/rules/api.evl`.

## Reading these rules

Each entry preserves the actual EVL guard and check. Read the guard as the applicability boundary, not as part of the invariant: when it is false, the rule is intentionally skipped. The diagnostic is the runtime-facing message emitted by EVL; its final sentence usually contains the repository's recommended repair.

---

## `ApiHasRoutes`

**Context:** `AWSPSM!ApiGatewayApi`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/psm/rules/api.evl:8`

### Why this rule exists

Checks that api has routes. The api gateway api element owns the evidence for this decision, including routes, resource label. At this level, the concrete API Gateway configuration agrees across routes, integrations, authorizers, stages, logs, metrics, and credentials; allowing the model through without that evidence would move an unresolved choice into generated infrastructure. The concrete failure this rule prevents is: API has no routes.

### When it applies

The rule has no guard, so it applies to every instance of this context in the validated model.

There is no guard expression; every instance of the context is checked.

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.routes.notEmpty()
```

This rule reads: `routes`, `resourceLabel`.

### Diagnostic and repair

> API has no routes. Fix: add at least one HttpApiRoute, RestApiRoute, or WebSocketRoute connected to an integration.

**How to fix it:**

add at least one HttpApiRoute, RestApiRoute, or WebSocketRoute connected to an integration.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---

## `ApiShouldHaveStage`

**Context:** `AWSPSM!ApiGatewayApi`  
**Classification:** Advisory critique (warning)  
**Source:** `mde/validation/psm/rules/api.evl:14`

### Why this rule exists

Advises that api should have stage. This is a review signal about stages, resource label, not a cosmetic naming preference. In this part of the model, the concrete API Gateway configuration agrees across routes, integrations, authorizers, stages, logs, metrics, and credentials; the warning makes a decision visible while it can still be discussed and changed. The concrete gap is: API has no stage. If it is left unexplained, a later transformation, generator, or reviewer has to invent an assumption.

### When it applies

The rule has no guard, so it applies to every instance of this context in the validated model.

There is no guard expression; every instance of the context is checked.

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.stages.notEmpty()
```

This rule reads: `stages`, `resourceLabel`.

### Diagnostic and repair

> API has no stage. Fix: add an ApiGatewayStage so the API can be deployed.

**How to fix it:**

add an ApiGatewayStage so the API can be deployed.

A critique does not necessarily make the model invalid. It is a deliberate quality/readiness signal; disposition it by improving the model, documenting why it is acceptable, or carrying the decision into the review record.

---

## `AccessLogsRequireLogGroupAndFormat`

**Context:** `AWSPSM!ApiGatewayApi`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/psm/rules/api.evl:20`

### Why this rule exists

Checks that access logs require log group and format. The api gateway api element owns the evidence for this decision, including access logs enabled, access log group, resource label. At this level, the concrete API Gateway configuration agrees across routes, integrations, authorizers, stages, logs, metrics, and credentials; allowing the model through without that evidence would move an unresolved choice into generated infrastructure. The concrete failure this rule prevents is: API enables access logs but has no accessLogGroup.

### When it applies

The rule is considered only when the guard below is true. A false guard is intentional: it means this invariant is not relevant to the current variant or modeling situation.

```evl
guard : self.accessLogsEnabled = true
```

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.accessLogGroup.isDefined()
```

This rule reads: `accessLogsEnabled`, `accessLogGroup`, `resourceLabel`.

### Diagnostic and repair

> API enables access logs but has no accessLogGroup. Fix: attach a CloudWatchLogGroup for API access logs.

**How to fix it:**

attach a CloudWatchLogGroup for API access logs.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---

## `ProductionApiShouldHaveMetricsAndTracing`

**Context:** `AWSPSM!ApiGatewayApi`  
**Classification:** Advisory critique (warning)  
**Source:** `mde/validation/psm/rules/api.evl:27`

### Why this rule exists

Advises that production api should have metrics and tracing. This is a review signal about is production scoped, metrics enabled, tracing enabled, tracing config, resource label, not a cosmetic naming preference. In this part of the model, the concrete API Gateway configuration agrees across routes, integrations, authorizers, stages, logs, metrics, and credentials; the warning makes a decision visible while it can still be discussed and changed. The concrete gap is: Production-scoped API does not enable both metrics and tracing. If it is left unexplained, a later transformation, generator, or reviewer has to invent an assumption.

### When it applies

The rule is considered only when the guard below is true. A false guard is intentional: it means this invariant is not relevant to the current variant or modeling situation.

```evl
guard : self.isProductionScoped()
```

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : (self.metricsEnabled = true) and ((self.tracingEnabled = true) or self.tracingConfig.isDefined())
```

This rule reads: `isProductionScoped`, `metricsEnabled`, `tracingEnabled`, `tracingConfig`, `resourceLabel`.

### Diagnostic and repair

> Production-scoped API does not enable both metrics and tracing. Fix: set metricsEnabled true and enable tracing/tracingConfig unless intentionally disabled with rationale.

**How to fix it:**

set metricsEnabled true and enable tracing/tracingConfig unless intentionally disabled with rationale.

A critique does not necessarily make the model invalid. It is a deliberate quality/readiness signal; disposition it by improving the model, documenting why it is acceptable, or carrying the decision into the review record.

---

## `UniqueHttpRouteWithinApi`

**Context:** `AWSPSM!HttpApiRoute`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/psm/rules/api.evl:37`

### Why this rule exists

Checks that unique http route within api. The http api route element owns the evidence for this decision, including http api route key, method, path, api. At this level, the concrete API Gateway configuration agrees across routes, integrations, authorizers, stages, logs, metrics, and credentials; allowing the model through without that evidence would move an unresolved choice into generated infrastructure. The concrete failure this rule prevents is: HTTP API route is duplicated in API.

### When it applies

The rule has no guard, so it applies to every instance of this context in the validated model.

There is no guard expression; every instance of the context is checked.

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : not duplicateHttpApiRouteKeys().includes(self.httpApiRouteKey())
```

This rule reads: `httpApiRouteKey`, `method`, `path`, `api`.

### Diagnostic and repair

> HTTP API route is duplicated in API . Fix: make method+path unique within the API.

**How to fix it:**

make method+path unique within the API.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---

## `UniqueRestRouteWithinApi`

**Context:** `AWSPSM!RestApiRoute`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/psm/rules/api.evl:46`

### Why this rule exists

Checks that unique rest route within api. The rest api route element owns the evidence for this decision, including rest api route key, method, path, api. At this level, the concrete API Gateway configuration agrees across routes, integrations, authorizers, stages, logs, metrics, and credentials; allowing the model through without that evidence would move an unresolved choice into generated infrastructure. The concrete failure this rule prevents is: REST API route is duplicated in API.

### When it applies

The rule has no guard, so it applies to every instance of this context in the validated model.

There is no guard expression; every instance of the context is checked.

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : not duplicateRestApiRouteKeys().includes(self.restApiRouteKey())
```

This rule reads: `restApiRouteKey`, `method`, `path`, `api`.

### Diagnostic and repair

> REST API route is duplicated in API . Fix: make method+path unique within the API.

**How to fix it:**

make method+path unique within the API.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---

## `UniqueWebSocketRouteKeyWithinApi`

**Context:** `AWSPSM!WebSocketRoute`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/psm/rules/api.evl:55`

### Why this rule exists

Checks that unique web socket route key within api. The web socket route element owns the evidence for this decision, including web socket route key, route key, api. At this level, the concrete API Gateway configuration agrees across routes, integrations, authorizers, stages, logs, metrics, and credentials; allowing the model through without that evidence would move an unresolved choice into generated infrastructure. The concrete failure this rule prevents is: WebSocket routeKey is duplicated in API.

### When it applies

The rule has no guard, so it applies to every instance of this context in the validated model.

There is no guard expression; every instance of the context is checked.

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : not duplicateWebSocketRouteKeys().includes(self.webSocketRouteKey())
```

This rule reads: `webSocketRouteKey`, `routeKey`, `api`.

### Diagnostic and repair

> WebSocket routeKey is duplicated in API . Fix: give each WebSocket route a unique routeKey.

**How to fix it:**

give each WebSocket route a unique routeKey.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---

## `RouteHasApi`

**Context:** `AWSPSM!ApiGatewayRoute`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/psm/rules/api.evl:64`

### Why this rule exists

Checks that route has api. The api gateway route element owns the evidence for this decision, including api, resource label. At this level, the concrete API Gateway configuration agrees across routes, integrations, authorizers, stages, logs, metrics, and credentials; allowing the model through without that evidence would move an unresolved choice into generated infrastructure. The concrete failure this rule prevents is: API route has no owning API.

### When it applies

The rule has no guard, so it applies to every instance of this context in the validated model.

There is no guard expression; every instance of the context is checked.

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.api.isDefined()
```

This rule reads: `api`, `resourceLabel`.

### Diagnostic and repair

> API route has no owning API. Fix: set ApiGatewayRoute.api to the HttpApi, RestApi, or WebSocketApi that contains the route.

**How to fix it:**

set ApiGatewayRoute.api to the HttpApi, RestApi, or WebSocketApi that contains the route.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---

## `ProtectedRouteHasRequiredAuthConfiguration`

**Context:** `AWSPSM!ApiGatewayRoute`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/psm/rules/api.evl:70`

### Why this rule exists

Checks that protected route has required auth configuration. The api gateway route element owns the evidence for this decision, including authorization type, authorizer, resource label. At this level, the concrete API Gateway configuration agrees across routes, integrations, authorizers, stages, logs, metrics, and credentials; allowing the model through without that evidence would move an unresolved choice into generated infrastructure. The concrete failure this rule prevents is: Protected route has authorizationType but no authorizer.

### When it applies

The rule has no guard, so it applies to every instance of this context in the validated model.

There is no guard expression; every instance of the context is checked.

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : (self.authorizationType = AWSPSMENUMS!ApiGatewayAuthorizationType#NONE) or (self.authorizationType = AWSPSMENUMS!ApiGatewayAuthorizationType#AWS_IAM) or (self.authorizationType = AWSPSMENUMS!ApiGatewayAuthorizationType#API_KEY) or self.authorizer.isDefined()
```

This rule reads: `authorizationType`, `authorizer`, `resourceLabel`.

### Diagnostic and repair

> Protected route has authorizationType but no authorizer. Fix: attach a matching JwtAuthorizer, CognitoAuthorizer, or LambdaAuthorizer; AWS_IAM/API_KEY do not require an authorizer object.

**How to fix it:**

attach a matching JwtAuthorizer, CognitoAuthorizer, or LambdaAuthorizer; AWS_IAM/API_KEY do not require an authorizer object.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---

## `RouteHasIntegration`

**Context:** `AWSPSM!ApiGatewayRoute`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/psm/rules/api.evl:79`

### Why this rule exists

Checks that route has integration. The api gateway route element owns the evidence for this decision, including integration, resource label. At this level, the concrete API Gateway configuration agrees across routes, integrations, authorizers, stages, logs, metrics, and credentials; allowing the model through without that evidence would move an unresolved choice into generated infrastructure. The concrete failure this rule prevents is: API route has no integration.

### When it applies

The rule has no guard, so it applies to every instance of this context in the validated model.

There is no guard expression; every instance of the context is checked.

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.integration.isDefined()
```

This rule reads: `integration`, `resourceLabel`.

### Diagnostic and repair

> API route has no integration. Fix: attach an ApiGatewayIntegration that points to Lambda, Step Functions, HTTP, AWS, or MOCK target configuration.

**How to fix it:**

attach an ApiGatewayIntegration that points to Lambda, Step Functions, HTTP, AWS, or MOCK target configuration.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---

## `IntegrationTimeoutShouldNotExceedLambdaTimeout`

**Context:** `AWSPSM!ApiGatewayRoute`  
**Classification:** Advisory critique (warning)  
**Source:** `mde/validation/psm/rules/api.evl:85`

### Why this rule exists

Advises that integration timeout should not exceed lambda timeout. This is a review signal about integration, timeout in millis, resource label, not a cosmetic naming preference. In this part of the model, the concrete API Gateway configuration agrees across routes, integrations, authorizers, stages, logs, metrics, and credentials; the warning makes a decision visible while it can still be discussed and changed. The concrete gap is: Route may time out before Lambda. If it is left unexplained, a later transformation, generator, or reviewer has to invent an assumption.

### When it applies

The rule is considered only when the guard below is true. A false guard is intentional: it means this invariant is not relevant to the current variant or modeling situation.

```evl
guard : self.integration.isDefined() and self.integration.lambdaTarget.isDefined() and self.timeoutInMillis.isDefined() and self.integration.lambdaTarget.timeoutSeconds.isDefined()
```

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.timeoutInMillis >= (self.integration.lambdaTarget.timeoutSeconds * 1000)
```

This rule reads: `integration`, `timeoutInMillis`, `resourceLabel`.

### Diagnostic and repair

> Route may time out before Lambda . Fix: align route/integration timeout with the Lambda timeout or make the operation asynchronous.

**How to fix it:**

align route/integration timeout with the Lambda timeout or make the operation asynchronous.

A critique does not necessarily make the model invalid. It is a deliberate quality/readiness signal; disposition it by improving the model, documenting why it is acceptable, or carrying the decision into the review record.

---

## `IntegrationHasSingleTarget`

**Context:** `AWSPSM!ApiGatewayIntegration`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/psm/rules/api.evl:95`

### Why this rule exists

Checks that integration has single target. The api gateway integration element owns the evidence for this decision, including target count, integration type, resource label. At this level, the concrete API Gateway configuration agrees across routes, integrations, authorizers, stages, logs, metrics, and credentials; allowing the model through without that evidence would move an unresolved choice into generated infrastructure. The concrete failure this rule prevents is: API integration must have exactly one backend target, unless it is MOCK.

### When it applies

The rule has no guard, so it applies to every instance of this context in the validated model.

There is no guard expression; every instance of the context is checked.

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.targetCount() = 1 or self.integrationType = AWSPSMENUMS!ApiGatewayIntegrationType#MOCK
```

This rule reads: `targetCount`, `integrationType`, `resourceLabel`.

### Diagnostic and repair

> API integration must have exactly one backend target, unless it is MOCK. Fix: set one of lambdaTarget, stateMachineTarget, or integrationUri, and remove conflicting targets.

**How to fix it:**

set one of lambdaTarget, stateMachineTarget, or integrationUri, and remove conflicting targets.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---

## `LongApiGatewayTimeoutRequiresQuotaReview`

**Context:** `AWSPSM!ApiGatewayIntegration`  
**Classification:** Advisory critique (warning)  
**Source:** `mde/validation/psm/rules/api.evl:101`

### Why this rule exists

Advises that long api gateway timeout requires quota review. This is a review signal about timeout in millis, resource label, not a cosmetic naming preference. In this part of the model, the concrete API Gateway configuration agrees across routes, integrations, authorizers, stages, logs, metrics, and credentials; the warning makes a decision visible while it can still be discussed and changed. The concrete gap is: API integration has timeoutInMillis greater than 29 seconds. If it is left unexplained, a later transformation, generator, or reviewer has to invent an assumption.

### When it applies

The rule is considered only when the guard below is true. A false guard is intentional: it means this invariant is not relevant to the current variant or modeling situation.

```evl
guard : self.timeoutInMillis.isDefined()
```

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.timeoutInMillis <= 29000
```

This rule reads: `timeoutInMillis`, `resourceLabel`.

### Diagnostic and repair

> API integration has timeoutInMillis greater than 29 seconds. Fix: verify that the API type, region, and account quota support the longer timeout, or redesign as asynchronous.

**How to fix it:**

verify that the API type, region, and account quota support the longer timeout, or redesign as asynchronous.

A critique does not necessarily make the model invalid. It is a deliberate quality/readiness signal; disposition it by improving the model, documenting why it is acceptable, or carrying the decision into the review record.

---

## `CredentialsArnMatchesCredentialsRole`

**Context:** `AWSPSM!ApiGatewayIntegration`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/psm/rules/api.evl:108`

### Why this rule exists

Checks that credentials arn matches credentials role. The api gateway integration element owns the evidence for this decision, including credentials arn, credentials role, resource label. At this level, the concrete API Gateway configuration agrees across routes, integrations, authorizers, stages, logs, metrics, and credentials; allowing the model through without that evidence would move an unresolved choice into generated infrastructure. The concrete failure this rule prevents is: API integration has a credentialsArn that is not a valid IAM role ARN and no credentialsRole reference.

### When it applies

The rule is considered only when the guard below is true. A false guard is intentional: it means this invariant is not relevant to the current variant or modeling situation.

```evl
guard : self.credentialsArn.hasText()
```

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.credentialsRole.isDefined() or self.credentialsArn.matches('^arn:aws[a-zA-Z-]*:iam::[0-9]{12}:role/.+')
```

This rule reads: `credentialsArn`, `credentialsRole`, `resourceLabel`.

### Diagnostic and repair

> API integration has a credentialsArn that is not a valid IAM role ARN and no credentialsRole reference. Fix: attach credentialsRole or provide a valid IAM role ARN.

**How to fix it:**

attach credentialsRole or provide a valid IAM role ARN.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---

## `AccessLogStageRequiresGroupAndFormat`

**Context:** `AWSPSM!ApiGatewayStage`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/psm/rules/api.evl:118`

### Why this rule exists

Checks that access log stage requires group and format. The api gateway stage element owns the evidence for this decision, including access log enabled, resolved access log group, access log format, stage name. At this level, the concrete API Gateway configuration agrees across routes, integrations, authorizers, stages, logs, metrics, and credentials; allowing the model through without that evidence would move an unresolved choice into generated infrastructure. The concrete failure this rule prevents is: API stage enables access logs but lacks an access log group or format.

### When it applies

The rule is considered only when the guard below is true. A false guard is intentional: it means this invariant is not relevant to the current variant or modeling situation.

```evl
guard : self.accessLogEnabled = true
```

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.resolvedAccessLogGroup().isDefined() and self.accessLogFormat.hasText()
```

This rule reads: `accessLogEnabled`, `resolvedAccessLogGroup`, `accessLogFormat`, `stageName`.

### Diagnostic and repair

> API stage enables access logs but lacks an access log group or format. Fix: attach a CloudWatchLogGroup on the stage or API and define a JSON access log format.

**How to fix it:**

attach a CloudWatchLogGroup on the stage or API and define a JSON access log format.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---

## `ProductionStageShouldThrottle`

**Context:** `AWSPSM!ApiGatewayStage`  
**Classification:** Advisory critique (warning)  
**Source:** `mde/validation/psm/rules/api.evl:125`

### Why this rule exists

Advises that production stage should throttle. This is a review signal about api, throttling burst limit, throttling rate limit, stage name, not a cosmetic naming preference. In this part of the model, the concrete API Gateway configuration agrees across routes, integrations, authorizers, stages, logs, metrics, and credentials; the warning makes a decision visible while it can still be discussed and changed. The concrete gap is: Production API stage has no explicit throttling limits. If it is left unexplained, a later transformation, generator, or reviewer has to invent an assumption.

### When it applies

The rule is considered only when the guard below is true. A false guard is intentional: it means this invariant is not relevant to the current variant or modeling situation.

```evl
guard : self.api.isDefined() and self.api.isProductionScoped()
```

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.throttlingBurstLimit.isDefined() and self.throttlingRateLimit.isDefined()
```

This rule reads: `api`, `throttlingBurstLimit`, `throttlingRateLimit`, `stageName`.

### Diagnostic and repair

> Production API stage has no explicit throttling limits. Fix: set throttlingBurstLimit and throttlingRateLimit to protect backend services.

**How to fix it:**

set throttlingBurstLimit and throttlingRateLimit to protect backend services.

A critique does not necessarily make the model invalid. It is a deliberate quality/readiness signal; disposition it by improving the model, documenting why it is acceptable, or carrying the decision into the review record.

---

## `JwtAuthorizerHasIssuerAndAudience`

**Context:** `AWSPSM!JwtAuthorizer`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/psm/rules/api.evl:135`

### Why this rule exists

Checks that jwt authorizer has issuer and audience. The jwt authorizer element owns the evidence for this decision, including issuer, audience, resource label. At this level, the concrete API Gateway configuration agrees across routes, integrations, authorizers, stages, logs, metrics, and credentials; allowing the model through without that evidence would move an unresolved choice into generated infrastructure. The concrete failure this rule prevents is: JWT authorizer is missing issuer or audience.

### When it applies

The rule has no guard, so it applies to every instance of this context in the validated model.

There is no guard expression; every instance of the context is checked.

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.issuer.hasText() and self.audience.notEmpty()
```

This rule reads: `issuer`, `audience`, `resourceLabel`.

### Diagnostic and repair

> JWT authorizer is missing issuer or audience. Fix: set issuer and add one or more expected audience values.

**How to fix it:**

set issuer and add one or more expected audience values.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---

## `CognitoAuthorizerShouldReferenceClients`

**Context:** `AWSPSM!CognitoAuthorizer`  
**Classification:** Advisory critique (warning)  
**Source:** `mde/validation/psm/rules/api.evl:144`

### Why this rule exists

Advises that cognito authorizer should reference clients. This is a review signal about clients, resource label, not a cosmetic naming preference. In this part of the model, the concrete API Gateway configuration agrees across routes, integrations, authorizers, stages, logs, metrics, and credentials; the warning makes a decision visible while it can still be discussed and changed. The concrete gap is: Cognito authorizer references a user pool but no clients. If it is left unexplained, a later transformation, generator, or reviewer has to invent an assumption.

### When it applies

The rule has no guard, so it applies to every instance of this context in the validated model.

There is no guard expression; every instance of the context is checked.

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.clients.notEmpty()
```

This rule reads: `clients`, `resourceLabel`.

### Diagnostic and repair

> Cognito authorizer references a user pool but no clients. Fix: attach expected CognitoUserPoolClient entries to restrict accepted app clients.

**How to fix it:**

attach expected CognitoUserPoolClient entries to restrict accepted app clients.

A critique does not necessarily make the model invalid. It is a deliberate quality/readiness signal; disposition it by improving the model, documenting why it is acceptable, or carrying the decision into the review record.

---

## `LambdaAuthorizerHasFunction`

**Context:** `AWSPSM!LambdaAuthorizer`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/psm/rules/api.evl:153`

### Why this rule exists

Checks that lambda authorizer has function. The lambda authorizer element owns the evidence for this decision, including function, resource label. At this level, the concrete API Gateway configuration agrees across routes, integrations, authorizers, stages, logs, metrics, and credentials; allowing the model through without that evidence would move an unresolved choice into generated infrastructure. The concrete failure this rule prevents is: Lambda authorizer has no Lambda function.

### When it applies

The rule has no guard, so it applies to every instance of this context in the validated model.

There is no guard expression; every instance of the context is checked.

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.`function`.isDefined()
```

This rule reads: `function`, `resourceLabel`.

### Diagnostic and repair

> Lambda authorizer has no Lambda function. Fix: attach the AwsLambdaFunction that implements authorization.

**How to fix it:**

attach the AwsLambdaFunction that implements authorization.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---

## `DomainHasCertificate`

**Context:** `AWSPSM!ApiGatewayDomainName`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/psm/rules/api.evl:162`

### Why this rule exists

Checks that domain has certificate. The api gateway domain name element owns the evidence for this decision, including certificate arn, domain name. At this level, the concrete API Gateway configuration agrees across routes, integrations, authorizers, stages, logs, metrics, and credentials; allowing the model through without that evidence would move an unresolved choice into generated infrastructure. The concrete failure this rule prevents is: API Gateway domain has no certificateArn.

### When it applies

The rule has no guard, so it applies to every instance of this context in the validated model.

There is no guard expression; every instance of the context is checked.

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.certificateArn.hasText()
```

This rule reads: `certificateArn`, `domainName`.

### Diagnostic and repair

> API Gateway domain has no certificateArn. Fix: attach an ACM certificate ARN in the appropriate region for the endpoint type.

**How to fix it:**

attach an ACM certificate ARN in the appropriate region for the endpoint type.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---

## `UsagePlanKeyUsesKnownType`

**Context:** `AWSPSM!ApiGatewayUsagePlanKey`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/psm/rules/api.evl:171`

### Why this rule exists

Checks that usage plan key uses known type. The api gateway usage plan key element owns the evidence for this decision, including key type, resource label. At this level, the concrete API Gateway configuration agrees across routes, integrations, authorizers, stages, logs, metrics, and credentials; allowing the model through without that evidence would move an unresolved choice into generated infrastructure. The concrete failure this rule prevents is: Usage plan key has invalid keyType.

### When it applies

The rule has no guard, so it applies to every instance of this context in the validated model.

There is no guard expression; every instance of the context is checked.

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.keyType.hasText() and self.keyType.toUpperCase() = 'API_KEY'
```

This rule reads: `keyType`, `resourceLabel`.

### Diagnostic and repair

> Usage plan key has invalid keyType. Fix: set keyType to API_KEY.

**How to fix it:**

set keyType to API_KEY.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---
