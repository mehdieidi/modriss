# CIM validation — Behavior

Behavior rules distinguish commands, queries, events, errors, and conditions. Their purpose is to preserve business meaning at the point where it is easiest to lose: before functions, routes, event channels, retries, and authorization policies are generated.

Source profile: `mde/validation/cim/rules/behavior.evl`.

## Reading these rules

Each entry preserves the actual EVL guard and check. Read the guard as the applicability boundary, not as part of the invariant: when it is false, the rule is intentionally skipped. The diagnostic is the runtime-facing message emitted by EVL; its final sentence usually contains the repository's recommended repair.

---

## `CommandMustCauseBusinessOutcome`

**Context:** `CIM!Command`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/cim/rules/behavior.evl:8`

### Why this rule exists

A command represents an intended state change. Expected or rejection events are the model's evidence of that change; without one, the command has no observable business consequence to contract or test.

### When it applies

The rule has no guard, so it applies to every instance of this context in the validated model.

There is no guard expression; every instance of the context is checked.

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.expectedEvents.notEmpty() or self.rejectionEvents.notEmpty()
```

This rule reads: `expectedEvents`, `rejectionEvents`, `labelText`.

### Diagnostic and repair

> Error [CIM-CMD-001] Command ' ' has no expectedEvents or rejectionEvents. Suggestion: link the business facts that occur when the command succeeds or is rejected.

**How to fix it:**

link the business facts that occur when the command succeeds or is rejected.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---

## `CommandHasBehavioralOwner`

**Context:** `CIM!Command`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/cim/rules/behavior.evl:16`

### Why this rule exists

Checks that command has behavioral owner. The command element owns the evidence for this decision, including target capability, target aggregate, label text. At this level, business behavior can be turned into operations, events, policies, and tests without changing its meaning; allowing the model through without that evidence would move an unresolved choice into generated infrastructure. The concrete failure this rule prevents is: Command ' ' has no targetCapability or targetAggregate.

### When it applies

The rule has no guard, so it applies to every instance of this context in the validated model.

There is no guard expression; every instance of the context is checked.

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.targetCapability.isDefined() or self.targetAggregate.isDefined()
```

This rule reads: `targetCapability`, `targetAggregate`, `labelText`.

### Diagnostic and repair

> Error [CIM-CMD-002] Command ' ' has no targetCapability or targetAggregate. Suggestion: assign the capability or aggregate responsible for deciding and executing the command.

**How to fix it:**

assign the capability or aggregate responsible for deciding and executing the command.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---

## `ActorFacingCommandHasAuthorizationDecision`

**Context:** `CIM!Command`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/cim/rules/behavior.evl:24`

### Why this rule exists

Checks that actor facing command has authorization decision. The command element owns the evidence for this decision, including has human issuer, has authorization decision, label text. At this level, business behavior can be turned into operations, events, policies, and tests without changing its meaning; allowing the model through without that evidence would move an unresolved choice into generated infrastructure. The concrete failure this rule prevents is: Human-issued Command ' ' lacks an explicit authorization decision.

### When it applies

The rule is considered only when the guard below is true. A false guard is intentional: it means this invariant is not relevant to the current variant or modeling situation.

```evl
guard : self.hasHumanIssuer()
```

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.hasAuthorizationDecision()
```

This rule reads: `hasHumanIssuer`, `hasAuthorizationDecision`, `labelText`.

### Diagnostic and repair

> Error [CIM-CMD-003] Human-issued Command ' ' lacks an explicit authorization decision. Suggestion: set authorizationRequired=true and add an authorizationRule explaining who may issue it and under what business conditions.

**How to fix it:**

set authorizationRequired=true and add an authorizationRule explaining who may issue it and under what business conditions.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---

## `ExternalOrUntrustedCommandRequiresAuditAndAuthorization`

**Context:** `CIM!Command`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/cim/rules/behavior.evl:33`

### Why this rule exists

Checks that external or untrusted command requires audit and authorization. The command element owns the evidence for this decision, including has external or untrusted issuer, audit required, has authorization decision, label text. At this level, business behavior can be turned into operations, events, policies, and tests without changing its meaning; allowing the model through without that evidence would move an unresolved choice into generated infrastructure. The concrete failure this rule prevents is: Command ' ' is issued by an external/untrusted actor but lacks auditRequired=true or an authorizationRule.

### When it applies

The rule is considered only when the guard below is true. A false guard is intentional: it means this invariant is not relevant to the current variant or modeling situation.

```evl
guard : self.hasExternalOrUntrustedIssuer()
```

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : (self.auditRequired = true) and self.hasAuthorizationDecision()
```

This rule reads: `hasExternalOrUntrustedIssuer`, `auditRequired`, `hasAuthorizationDecision`, `labelText`.

### Diagnostic and repair

> Error [CIM-CMD-004] Command ' ' is issued by an external/untrusted actor but lacks auditRequired=true or an authorizationRule. Suggestion: require authorization and auditing for commands crossing trust boundaries.

**How to fix it:**

require authorization and auditing for commands crossing trust boundaries.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---

## `DuplicateSubmissionCommandHasIdempotencyKey`

**Context:** `CIM!Command`  
**Classification:** Advisory critique (warning)  
**Source:** `mde/validation/cim/rules/behavior.evl:42`

### Why this rule exists

Advises that duplicate submission command has idempotency key. This is a review signal about duplicate submission possible, idempotency business key, label text, not a cosmetic naming preference. In this part of the model, business behavior can be turned into operations, events, policies, and tests without changing its meaning; the warning makes a decision visible while it can still be discussed and changed. The concrete gap is: Command ' ' may be submitted more than once but has no idempotencyBusinessKey. If it is left unexplained, a later transformation, generator, or reviewer has to invent an assumption.

### When it applies

The rule is considered only when the guard below is true. A false guard is intentional: it means this invariant is not relevant to the current variant or modeling situation.

```evl
guard : self.duplicateSubmissionPossible = true
```

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.idempotencyBusinessKey.hasText()
```

This rule reads: `duplicateSubmissionPossible`, `idempotencyBusinessKey`, `labelText`.

### Diagnostic and repair

> Warning [CIM-CMD-005] Command ' ' may be submitted more than once but has no idempotencyBusinessKey. Suggestion: define the business key that lets the system recognize duplicate intent.

**How to fix it:**

define the business key that lets the system recognize duplicate intent.

A critique does not necessarily make the model invalid. It is a deliberate quality/readiness signal; disposition it by improving the model, documenting why it is acceptable, or carrying the decision into the review record.

---

## `CommandHasPreconditionsOrErrors`

**Context:** `CIM!Command`  
**Classification:** Advisory critique (warning)  
**Source:** `mde/validation/cim/rules/behavior.evl:51`

### Why this rule exists

Advises that command has preconditions or errors. This is a review signal about preconditions, possible errors, label text, not a cosmetic naming preference. In this part of the model, business behavior can be turned into operations, events, policies, and tests without changing its meaning; the warning makes a decision visible while it can still be discussed and changed. The concrete gap is: Command ' ' has no preconditions or possibleErrors. If it is left unexplained, a later transformation, generator, or reviewer has to invent an assumption.

### When it applies

The rule has no guard, so it applies to every instance of this context in the validated model.

There is no guard expression; every instance of the context is checked.

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.preconditions.notEmpty() or self.possibleErrors.notEmpty()
```

This rule reads: `preconditions`, `possibleErrors`, `labelText`.

### Diagnostic and repair

> Warning [CIM-CMD-006] Command ' ' has no preconditions or possibleErrors. Suggestion: document business conditions and rejection/error cases so command semantics are complete.

**How to fix it:**

document business conditions and rejection/error cases so command semantics are complete.

A critique does not necessarily make the model invalid. It is a deliberate quality/readiness signal; disposition it by improving the model, documenting why it is acceptable, or carrying the decision into the review record.

---

## `CommandOutcomeLinksMatchDirectCollections`

**Context:** `CIM!Command`  
**Classification:** Advisory critique (warning)  
**Source:** `mde/validation/cim/rules/behavior.evl:59`

### Why this rule exists

Advises that command outcome links match direct collections. This is a review signal about outcomes, not a cosmetic naming preference. In this part of the model, business behavior can be turned into operations, events, policies, and tests without changing its meaning; the warning makes a decision visible while it can still be discussed and changed. The concrete gap is: . If it is left unexplained, a later transformation, generator, or reviewer has to invent an assumption.

### When it applies

The rule is considered only when the guard below is true. A false guard is intentional: it means this invariant is not relevant to the current variant or modeling situation.

```evl
guard : self.outcomes.notEmpty()
```

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check { var successEvents : Set = Set{}; var rejectionEvents : Set = Set{}; var outcomeErrors : Set = Set{};  // Flatten detailed outcomes before comparing both representations in both directions. for (outcome in self.outcomes) { for (eventItem in outcome.emittedEvents) { if (outcome.success = true) { successEvents.add(eventItem);
```

This rule reads: `outcomes`.

### Diagnostic and repair

**How to fix it:**

This is advisory rather than a structural blocker. Decide whether the modeled situation genuinely needs the recommendation; if it does, change the referenced model features so the check evaluates to true and record the rationale when the omission is intentional.

A critique does not necessarily make the model invalid. It is a deliberate quality/readiness signal; disposition it by improving the model, documenting why it is acceptable, or carrying the decision into the review record.

---

## `PersonalQueryMustDeclareAuthorization`

**Context:** `CIM!Query`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/cim/rules/behavior.evl:96`

### Why this rule exists

A read can expose sensitive data even though it does not mutate state. This rule forces the modeler to make access policy explicit at the point where personal data crosses a query boundary.

### When it applies

The rule is considered only when the guard below is true. A false guard is intentional: it means this invariant is not relevant to the current variant or modeling situation.

```evl
guard : (self.containsPersonalData = true) or self.outputsPersonalData()
```

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : (self.authorizationRequired = true) and self.authorizationRule.hasText()
```

This rule reads: `containsPersonalData`, `outputsPersonalData`, `authorizationRequired`, `authorizationRule`, `labelText`.

### Diagnostic and repair

> Error [CIM-QRY-002] Query ' ' returns or declares personal data but lacks authorizationRequired=true and authorizationRule. Suggestion: specify who may read this data and under which business/legal conditions.

**How to fix it:**

specify who may read this data and under which business/legal conditions.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---

## `QueryPersonalFlagMatchesOutput`

**Context:** `CIM!Query`  
**Classification:** Advisory critique (warning)  
**Source:** `mde/validation/cim/rules/behavior.evl:105`

### Why this rule exists

Advises that query personal flag matches output. This is a review signal about contains personal data, outputs personal data, output, label text, not a cosmetic naming preference. In this part of the model, business behavior can be turned into operations, events, policies, and tests without changing its meaning; the warning makes a decision visible while it can still be discussed and changed. The concrete gap is: Query ' ' has containsPersonalData inconsistent with output classifications. If it is left unexplained, a later transformation, generator, or reviewer has to invent an assumption.

### When it applies

The rule has no guard, so it applies to every instance of this context in the validated model.

There is no guard expression; every instance of the context is checked.

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : (self.containsPersonalData = self.outputsPersonalData()) or ((self.containsPersonalData = true) and self.output.isEmpty())
```

This rule reads: `containsPersonalData`, `outputsPersonalData`, `output`, `labelText`.

### Diagnostic and repair

> Warning [CIM-QRY-003] Query ' ' has containsPersonalData inconsistent with output classifications. Suggestion: update containsPersonalData or classify the output InformationItems correctly.

**How to fix it:**

update containsPersonalData or classify the output InformationItems correctly.

A critique does not necessarily make the model invalid. It is a deliberate quality/readiness signal; disposition it by improving the model, documenting why it is acceptable, or carrying the decision into the review record.

---

## `ListSearchQueryDeclaresResultHandling`

**Context:** `CIM!Query`  
**Classification:** Advisory critique (warning)  
**Source:** `mde/validation/cim/rules/behavior.evl:115`

### Why this rule exists

Advises that list search query declares result handling. This is a review signal about query type, pagination expectation, filtering expectation, label text, not a cosmetic naming preference. In this part of the model, business behavior can be turned into operations, events, policies, and tests without changing its meaning; the warning makes a decision visible while it can still be discussed and changed. The concrete gap is: List/Search Query ' ' lacks paginationExpectation or filteringExpectation. If it is left unexplained, a later transformation, generator, or reviewer has to invent an assumption.

### When it applies

The rule is considered only when the guard below is true. A false guard is intentional: it means this invariant is not relevant to the current variant or modeling situation.

```evl
guard : (self.queryType = CIM!QueryType#LIST) or (self.queryType = CIM!QueryType#SEARCH)
```

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.paginationExpectation.hasText() and self.filteringExpectation.hasText()
```

This rule reads: `queryType`, `paginationExpectation`, `filteringExpectation`, `labelText`.

### Diagnostic and repair

> Warning [CIM-QRY-004] List/Search Query ' ' lacks paginationExpectation or filteringExpectation. Suggestion: declare expected pagination and filtering behavior so frontends and read models can be generated safely.

**How to fix it:**

declare expected pagination and filtering behavior so frontends and read models can be generated safely.

A critique does not necessarily make the model invalid. It is a deliberate quality/readiness signal; disposition it by improving the model, documenting why it is acceptable, or carrying the decision into the review record.

---

## `QueryDeclaresFreshness`

**Context:** `CIM!Query`  
**Classification:** Advisory critique (warning)  
**Source:** `mde/validation/cim/rules/behavior.evl:124`

### Why this rule exists

Advises that query declares freshness. This is a review signal about freshness need, label text, not a cosmetic naming preference. In this part of the model, business behavior can be turned into operations, events, policies, and tests without changing its meaning; the warning makes a decision visible while it can still be discussed and changed. The concrete gap is: Query ' ' has no freshnessNeed. If it is left unexplained, a later transformation, generator, or reviewer has to invent an assumption.

### When it applies

The rule has no guard, so it applies to every instance of this context in the validated model.

There is no guard expression; every instance of the context is checked.

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.freshnessNeed.isDefined()
```

This rule reads: `freshnessNeed`, `labelText`.

### Diagnostic and repair

> Warning [CIM-QRY-005] Query ' ' has no freshnessNeed. Suggestion: choose REAL_TIME, NEAR_REAL_TIME, EVENTUALLY_CONSISTENT, PERIODIC, or HISTORICAL.

**How to fix it:**

choose REAL_TIME, NEAR_REAL_TIME, EVENTUALLY_CONSISTENT, PERIODIC, or HISTORICAL.

A critique does not necessarily make the model invalid. It is a deliberate quality/readiness signal; disposition it by improving the model, documenting why it is acceptable, or carrying the decision into the review record.

---

## `EventNamePastTense`

**Context:** `CIM!BusinessEvent`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/cim/rules/behavior.evl:136`

### Why this rule exists

Checks that event name past tense. The business event element owns the evidence for this decision, including name, occurred in past tense name, label text. At this level, business behavior can be turned into operations, events, policies, and tests without changing its meaning; allowing the model through without that evidence would move an unresolved choice into generated infrastructure. The concrete failure this rule prevents is: BusinessEvent ' ' is not clearly named as a past-tense business fact.

### When it applies

The rule has no guard, so it applies to every instance of this context in the validated model.

There is no guard expression; every instance of the context is checked.

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : (self.name.matches("(?i).*(ed|done|completed|created|updated|deleted|rejected|accepted|submitted|failed|cancelled)$")) or self.occurredInPastTenseName.hasText()
```

This rule reads: `name`, `occurredInPastTenseName`, `labelText`.

### Diagnostic and repair

> Error [CIM-EVT-001] BusinessEvent ' ' is not clearly named as a past-tense business fact. Suggestion: rename it to a past-tense fact such as 'OrderSubmitted', 'PaymentRejected', or fill occurredInPastTenseName.

**How to fix it:**

rename it to a past-tense fact such as 'OrderSubmitted', 'PaymentRejected', or fill occurredInPastTenseName.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---

## `EventIsBusinessFactNotCommand`

**Context:** `CIM!BusinessEvent`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/cim/rules/behavior.evl:146`

### Why this rule exists

Events describe something that happened; commands ask for something to happen. Keeping the distinction prevents generated consumers from treating an event as an instruction and protects event-driven autonomy.

### When it applies

The rule has no guard, so it applies to every instance of this context in the validated model.

There is no guard expression; every instance of the context is checked.

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : (not self.name.startsWith("Create")) and (not self.name.startsWith("Update")) and (not self.name.startsWith("Delete")) and (not self.name.startsWith("Submit")) and (not self.name.startsWith("Approve")) and (not self.name.startsWith("Cancel"))
```

This rule reads: `name`, `labelText`.

### Diagnostic and repair

> Error [CIM-EVT-002] BusinessEvent ' ' is named like an imperative command. Suggestion: model commands as Command elements and events as facts that already happened, e.g. 'CustomerApproved' instead of 'ApproveCustomer'.

**How to fix it:**

model commands as Command elements and events as facts that already happened, e.g. 'CustomerApproved' instead of 'ApproveCustomer'.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---

## `EventHasBusinessMeaning`

**Context:** `CIM!BusinessEvent`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/cim/rules/behavior.evl:160`

### Why this rule exists

Checks that event has business meaning. The business event element owns the evidence for this decision, including business meaning, semantic name, label text. At this level, business behavior can be turned into operations, events, policies, and tests without changing its meaning; allowing the model through without that evidence would move an unresolved choice into generated infrastructure. The concrete failure this rule prevents is: BusinessEvent ' ' lacks businessMeaning or semanticName.

### When it applies

The rule has no guard, so it applies to every instance of this context in the validated model.

There is no guard expression; every instance of the context is checked.

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.businessMeaning.hasText() or self.semanticName.hasText()
```

This rule reads: `businessMeaning`, `semanticName`, `labelText`.

### Diagnostic and repair

> Error [CIM-EVT-003] BusinessEvent ' ' lacks businessMeaning or semanticName. Suggestion: explain the business fact and why it matters.

**How to fix it:**

explain the business fact and why it matters.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---

## `EventHasProducerOrConsumer`

**Context:** `CIM!BusinessEvent`  
**Classification:** Advisory critique (warning)  
**Source:** `mde/validation/cim/rules/behavior.evl:168`

### Why this rule exists

Advises that event has producer or consumer. This is a review signal about expected by commands, rejected by commands, caused by policies, caused by external systems, consumed by policies, not a cosmetic naming preference. In this part of the model, business behavior can be turned into operations, events, policies, and tests without changing its meaning; the warning makes a decision visible while it can still be discussed and changed. The concrete gap is: BusinessEvent ' ' has no producer or consumer. If it is left unexplained, a later transformation, generator, or reviewer has to invent an assumption.

### When it applies

The rule has no guard, so it applies to every instance of this context in the validated model.

There is no guard expression; every instance of the context is checked.

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.expectedByCommands.notEmpty() or self.rejectedByCommands.notEmpty() or self.causedByPolicies.notEmpty() or self.causedByExternalSystems.notEmpty() or self.consumedByPolicies.notEmpty() or self.consumedByProcesses.notEmpty() or self.consumedByExternalSystems.notEmpty()
```

This rule reads: `expectedByCommands`, `rejectedByCommands`, `causedByPolicies`, `causedByExternalSystems`, `consumedByPolicies`, `consumedByProcesses`, `consumedByExternalSystems`, `labelText`.

### Diagnostic and repair

> Warning [CIM-EVT-004] BusinessEvent ' ' has no producer or consumer. Suggestion: connect it to a command, policy, process, or external system that produces or consumes it.

**How to fix it:**

connect it to a command, policy, process, or external system that produces or consumes it.

A critique does not necessarily make the model invalid. It is a deliberate quality/readiness signal; disposition it by improving the model, documenting why it is acceptable, or carrying the decision into the review record.

---

## `ProductionRelevantEventHasVersioningMetadata`

**Context:** `CIM!BusinessEvent`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/cim/rules/behavior.evl:180`

### Why this rule exists

Checks that production relevant event has versioning metadata. The business event element owns the evidence for this decision, including externally visible, audit relevant, retention relevant, semantic version, versioning rationale. At this level, business behavior can be turned into operations, events, policies, and tests without changing its meaning; allowing the model through without that evidence would move an unresolved choice into generated infrastructure. The concrete failure this rule prevents is: Production-relevant BusinessEvent ' ' lacks semanticVersion or versioningRationale.

### When it applies

The rule is considered only when the guard below is true. A false guard is intentional: it means this invariant is not relevant to the current variant or modeling situation.

```evl
guard : self.externallyVisible = true or self.auditRelevant = true or self.retentionRelevant = true
```

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.semanticVersion.hasText() and self.versioningRationale.hasText()
```

This rule reads: `externallyVisible`, `auditRelevant`, `retentionRelevant`, `semanticVersion`, `versioningRationale`, `labelText`.

### Diagnostic and repair

> Error [CIM-EVT-005] Production-relevant BusinessEvent ' ' lacks semanticVersion or versioningRationale. Suggestion: set an explicit semantic version and describe how event meaning/payload changes will be managed.

**How to fix it:**

set an explicit semantic version and describe how event meaning/payload changes will be managed.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---

## `EventSemanticVersionLooksLikeSemVer`

**Context:** `CIM!BusinessEvent`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/cim/rules/behavior.evl:189`

### Why this rule exists

Checks that event semantic version looks like sem ver. The business event element owns the evidence for this decision, including semantic version, label text. At this level, business behavior can be turned into operations, events, policies, and tests without changing its meaning; allowing the model through without that evidence would move an unresolved choice into generated infrastructure. The concrete failure this rule prevents is: BusinessEvent ' ' has semanticVersion ' ' that is not SemVer-like.

### When it applies

The rule is considered only when the guard below is true. A false guard is intentional: it means this invariant is not relevant to the current variant or modeling situation.

```evl
guard : self.semanticVersion.hasText()
```

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.semanticVersion.matches("^[0-9]+\\.[0-9]+\\.[0-9]+(-[0-9A-Za-z.-]+)?(\\+[0-9A-Za-z.-]+)?$")
```

This rule reads: `semanticVersion`, `labelText`.

### Diagnostic and repair

> Error [CIM-EVT-005A] BusinessEvent ' ' has semanticVersion ' ' that is not SemVer-like. Suggestion: use MAJOR.MINOR.PATCH with optional prerelease/build metadata, for example 1.0.0.

**How to fix it:**

use MAJOR.MINOR.PATCH with optional prerelease/build metadata, for example 1.0.0.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---

## `EventPayloadInformationIsTyped`

**Context:** `CIM!BusinessEvent`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/cim/rules/behavior.evl:198`

### Why this rule exists

Checks that event payload information is typed. The business event element owns the evidence for this decision, including payload, label text. At this level, business behavior can be turned into operations, events, policies, and tests without changing its meaning; allowing the model through without that evidence would move an unresolved choice into generated infrastructure. The concrete failure this rule prevents is: BusinessEvent ' ' has payload InformationItems without type.

### When it applies

The rule has no guard, so it applies to every instance of this context in the validated model.

There is no guard expression; every instance of the context is checked.

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.payload.forAll(item | item.type.isDefined())
```

This rule reads: `payload`, `labelText`.

### Diagnostic and repair

> Error [CIM-EVT-006] BusinessEvent ' ' has payload InformationItems without type. Suggestion: type every payload item so event schemas and transformations are deterministic.

**How to fix it:**

type every payload item so event schemas and transformations are deterministic.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---

## `BusinessErrorIsUserUnderstandable`

**Context:** `CIM!BusinessError`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/cim/rules/behavior.evl:210`

### Why this rule exists

Checks that business error is user understandable. The business error element owns the evidence for this decision, including error code, business meaning, user visible message, label text. At this level, business behavior can be turned into operations, events, policies, and tests without changing its meaning; allowing the model through without that evidence would move an unresolved choice into generated infrastructure. The concrete failure this rule prevents is: BusinessError ' ' lacks errorCode, businessMeaning, or userVisibleMessage.

### When it applies

The rule has no guard, so it applies to every instance of this context in the validated model.

There is no guard expression; every instance of the context is checked.

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.errorCode.hasText() and self.businessMeaning.hasText() and self.userVisibleMessage.hasText()
```

This rule reads: `errorCode`, `businessMeaning`, `userVisibleMessage`, `labelText`.

### Diagnostic and repair

> Error [CIM-ERR-001] BusinessError ' ' lacks errorCode, businessMeaning, or userVisibleMessage. Suggestion: define a stable business error code and a user-facing explanation that does not expose implementation details.

**How to fix it:**

define a stable business error code and a user-facing explanation that does not expose implementation details.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---

## `RetryMeaningfulImpliesRecoverable`

**Context:** `CIM!BusinessError`  
**Classification:** Advisory critique (warning)  
**Source:** `mde/validation/cim/rules/behavior.evl:219`

### Why this rule exists

Advises that retry meaningful implies recoverable. This is a review signal about retry meaningful, recoverable, label text, not a cosmetic naming preference. In this part of the model, business behavior can be turned into operations, events, policies, and tests without changing its meaning; the warning makes a decision visible while it can still be discussed and changed. The concrete gap is: BusinessError ' ' says retry is meaningful but recoverable is not true. If it is left unexplained, a later transformation, generator, or reviewer has to invent an assumption.

### When it applies

The rule is considered only when the guard below is true. A false guard is intentional: it means this invariant is not relevant to the current variant or modeling situation.

```evl
guard : self.retryMeaningful = true
```

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.recoverable = true
```

This rule reads: `retryMeaningful`, `recoverable`, `labelText`.

### Diagnostic and repair

> Warning [CIM-ERR-002] BusinessError ' ' says retry is meaningful but recoverable is not true. Suggestion: set recoverable=true or explain why retrying is meaningful despite non-recoverability.

**How to fix it:**

set recoverable=true or explain why retrying is meaningful despite non-recoverability.

A critique does not necessarily make the model invalid. It is a deliberate quality/readiness signal; disposition it by improving the model, documenting why it is acceptable, or carrying the decision into the review record.

---

## `ConditionIsSpecified`

**Context:** `CIM!Condition`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/cim/rules/behavior.evl:232`

### Why this rule exists

A condition with no natural-language, executable, or typed expression cannot be reviewed or evaluated. The rule allows different representation phases but requires at least one meaningful statement.

### When it applies

The rule has no guard, so it applies to every instance of this context in the validated model.

There is no guard expression; every instance of the context is checked.

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.naturalLanguage.hasText() or self.expression.hasText() or self.expressionModel.hasExpressionBody()
```

This rule reads: `naturalLanguage`, `expression`, `expressionModel`, `labelText`.

### Diagnostic and repair

> Error [CIM-COND-001] Condition ' ' has neither naturalLanguage, expression, nor expressionModel. Suggestion: write the condition in business language and optionally add expressionModel.body for automation.

**How to fix it:**

write the condition in business language and optionally add expressionModel.body for automation.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---

## `AutomatableConditionHasExpression`

**Context:** `CIM!Condition`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/cim/rules/behavior.evl:240`

### Why this rule exists

Checks that automatable condition has expression. The condition element owns the evidence for this decision, including must be automatable, expression model, expression language, expression, label text. At this level, business behavior can be turned into operations, events, policies, and tests without changing its meaning; allowing the model through without that evidence would move an unresolved choice into generated infrastructure. The concrete failure this rule prevents is: Condition ' ' must be automatable but lacks a typed expressionModel or expressionLanguage/expression pair.

### When it applies

The rule is considered only when the guard below is true. A false guard is intentional: it means this invariant is not relevant to the current variant or modeling situation.

```evl
guard : self.mustBeAutomatable = true
```

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.expressionModel.hasExpressionBody() or (self.expressionLanguage.isDefined() and self.expression.hasText())
```

This rule reads: `mustBeAutomatable`, `expressionModel`, `expressionLanguage`, `expression`, `labelText`.

### Diagnostic and repair

> Error [CIM-COND-002] Condition ' ' must be automatable but lacks a typed expressionModel or expressionLanguage/expression pair. Suggestion: provide a machine-evaluable kernel.Expression with language and body.

**How to fix it:**

provide a machine-evaluable kernel.Expression with language and body.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---

## `ConditionReferencesInformationOrConcepts`

**Context:** `CIM!Condition`  
**Classification:** Advisory critique (warning)  
**Source:** `mde/validation/cim/rules/behavior.evl:249`

### Why this rule exists

Advises that condition references information or concepts. This is a review signal about referenced information, referenced concepts, label text, not a cosmetic naming preference. In this part of the model, business behavior can be turned into operations, events, policies, and tests without changing its meaning; the warning makes a decision visible while it can still be discussed and changed. The concrete gap is: Condition ' ' is not linked to referencedInformation or referencedConcepts. If it is left unexplained, a later transformation, generator, or reviewer has to invent an assumption.

### When it applies

The rule has no guard, so it applies to every instance of this context in the validated model.

There is no guard expression; every instance of the context is checked.

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.referencedInformation.notEmpty() or self.referencedConcepts.notEmpty()
```

This rule reads: `referencedInformation`, `referencedConcepts`, `labelText`.

### Diagnostic and repair

> Warning [CIM-COND-003] Condition ' ' is not linked to referencedInformation or referencedConcepts. Suggestion: link the data or concepts used by the condition.

**How to fix it:**

link the data or concepts used by the condition.

A critique does not necessarily make the model invalid. It is a deliberate quality/readiness signal; disposition it by improving the model, documenting why it is acceptable, or carrying the decision into the review record.

---
