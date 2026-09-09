# CIM validation — Domain Data

Domain-data rules prevent business concepts from becoming attractive but unusable nouns. They protect identity and lifecycle meaning, aggregate boundaries, equality of value objects, information typing, privacy classification, and the evidence needed to turn domain data into PIM schemas and stores.

Source profile: `mde/validation/cim/rules/domain-data.evl`.

## Reading these rules

Each entry preserves the actual EVL guard and check. Read the guard as the applicability boundary, not as part of the invariant: when it is false, the rule is intentionally skipped. The diagnostic is the runtime-facing message emitted by EVL; its final sentence usually contains the repository's recommended repair.

---

## `DomainConceptHasBusinessDefinition`

**Context:** `CIM!DomainConcept`  
**Classification:** Advisory critique (warning)  
**Source:** `mde/validation/cim/rules/domain-data.evl:8`

### Why this rule exists

Advises that domain concept has business definition. This is a review signal about glossary definition, description, label text, not a cosmetic naming preference. In this part of the model, domain concepts can become schemas and persistence choices without losing ownership, identity, or privacy meaning; the warning makes a decision visible while it can still be discussed and changed. The concrete gap is: DomainConcept ' ' lacks a glossaryDefinition or description. If it is left unexplained, a later transformation, generator, or reviewer has to invent an assumption.

### When it applies

The rule has no guard, so it applies to every instance of this context in the validated model.

There is no guard expression; every instance of the context is checked.

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.glossaryDefinition.hasText() or self.description.hasText()
```

This rule reads: `glossaryDefinition`, `description`, `labelText`.

### Diagnostic and repair

> Warning [CIM-DOM-001] DomainConcept ' ' lacks a glossaryDefinition or description. Suggestion: define the concept in business vocabulary so entities, value objects, and relationships are not interpreted technically.

**How to fix it:**

define the concept in business vocabulary so entities, value objects, and relationships are not interpreted technically.

A critique does not necessarily make the model invalid. It is a deliberate quality/readiness signal; disposition it by improving the model, documenting why it is acceptable, or carrying the decision into the review record.

---

## `IdentityAttributesAreOwnedAttributes`

**Context:** `CIM!DomainEntity`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/cim/rules/domain-data.evl:20`

### Why this rule exists

Checks that identity attributes are owned attributes. The domain entity element owns the evidence for this decision, including primary identity attribute, identity attributes, attributes, label text. At this level, domain concepts can become schemas and persistence choices without losing ownership, identity, or privacy meaning; allowing the model through without that evidence would move an unresolved choice into generated infrastructure. The concrete failure this rule prevents is: DomainEntity ' ' has identityAttributes that are not included in attributes or do not include primaryIdentityAttribute.

### When it applies

The rule is considered only when the guard below is true. A false guard is intentional: it means this invariant is not relevant to the current variant or modeling situation.

```evl
guard : self.primaryIdentityAttribute.isDefined()
```

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.identityAttributes.notEmpty() and self.attributes.includes(self.primaryIdentityAttribute) and self.identityAttributes.includes(self.primaryIdentityAttribute) and self.identityAttributes.forAll(attributeItem | self.attributes.includes(attributeItem))
```

This rule reads: `primaryIdentityAttribute`, `identityAttributes`, `attributes`, `labelText`.

### Diagnostic and repair

> Error [CIM-ENTITY-002] DomainEntity ' ' has identityAttributes that are not included in attributes or do not include primaryIdentityAttribute. Suggestion: add all identity attributes to attributes and choose primaryIdentityAttribute from identityAttributes.

**How to fix it:**

add all identity attributes to attributes and choose primaryIdentityAttribute from identityAttributes.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---

## `EntityHasOwningCapability`

**Context:** `CIM!DomainEntity`  
**Classification:** Advisory critique (warning)  
**Source:** `mde/validation/cim/rules/domain-data.evl:32`

### Why this rule exists

Advises that entity has owning capability. This is a review signal about owning capability, business owner, label text, not a cosmetic naming preference. In this part of the model, domain concepts can become schemas and persistence choices without losing ownership, identity, or privacy meaning; the warning makes a decision visible while it can still be discussed and changed. The concrete gap is: DomainEntity ' ' has no owningCapability or businessOwner. If it is left unexplained, a later transformation, generator, or reviewer has to invent an assumption.

### When it applies

The rule has no guard, so it applies to every instance of this context in the validated model.

There is no guard expression; every instance of the context is checked.

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.owningCapability.isDefined() or self.businessOwner.hasText()
```

This rule reads: `owningCapability`, `businessOwner`, `labelText`.

### Diagnostic and repair

> Warning [CIM-ENTITY-003] DomainEntity ' ' has no owningCapability or businessOwner. Suggestion: assign the business capability or owner responsible for the entity's lifecycle and invariants.

**How to fix it:**

assign the business capability or owner responsible for the entity's lifecycle and invariants.

A critique does not necessarily make the model invalid. It is a deliberate quality/readiness signal; disposition it by improving the model, documenting why it is acceptable, or carrying the decision into the review record.

---

## `LifecycleStatesHaveSingleInitialState`

**Context:** `CIM!DomainEntity`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/cim/rules/domain-data.evl:40`

### Why this rule exists

Checks that lifecycle states have single initial state. The domain entity element owns the evidence for this decision, including lifecycle states, initial, terminal, label text. At this level, domain concepts can become schemas and persistence choices without losing ownership, identity, or privacy meaning; allowing the model through without that evidence would move an unresolved choice into generated infrastructure. The concrete failure this rule prevents is: DomainEntity ' ' has lifecycle states but does not have exactly one initial state and at least one terminal state.

### When it applies

The rule is considered only when the guard below is true. A false guard is intentional: it means this invariant is not relevant to the current variant or modeling situation.

```evl
guard : self.lifecycleStates.notEmpty()
```

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.lifecycleStates.one(stateItem | stateItem.initial = true) and self.lifecycleStates.exists(stateItem | stateItem.terminal = true)
```

This rule reads: `lifecycleStates`, `initial`, `terminal`, `labelText`.

### Diagnostic and repair

> Error [CIM-ENTITY-004] DomainEntity ' ' has lifecycle states but does not have exactly one initial state and at least one terminal state. Suggestion: mark one LifecycleStateDefinition as initial=true and at least one as terminal=true.

**How to fix it:**

mark one LifecycleStateDefinition as initial=true and at least one as terminal=true.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---

## `ValueObjectShouldBeImmutable`

**Context:** `CIM!ValueObject`  
**Classification:** Advisory critique (warning)  
**Source:** `mde/validation/cim/rules/domain-data.evl:55`

### Why this rule exists

Advises that value object should be immutable. This is a review signal about immutable, label text, not a cosmetic naming preference. In this part of the model, domain concepts can become schemas and persistence choices without losing ownership, identity, or privacy meaning; the warning makes a decision visible while it can still be discussed and changed. The concrete gap is: ValueObject ' ' is not marked immutable. If it is left unexplained, a later transformation, generator, or reviewer has to invent an assumption.

### When it applies

The rule has no guard, so it applies to every instance of this context in the validated model.

There is no guard expression; every instance of the context is checked.

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.immutable = true
```

This rule reads: `immutable`, `labelText`.

### Diagnostic and repair

> Warning [CIM-VO-001] ValueObject ' ' is not marked immutable. Suggestion: set immutable=true unless this concept is actually an entity with identity and lifecycle.

**How to fix it:**

set immutable=true unless this concept is actually an entity with identity and lifecycle.

A critique does not necessarily make the model invalid. It is a deliberate quality/readiness signal; disposition it by improving the model, documenting why it is acceptable, or carrying the decision into the review record.

---

## `ValueObjectDefinesEquality`

**Context:** `CIM!ValueObject`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/cim/rules/domain-data.evl:63`

### Why this rule exists

Checks that value object defines equality. The value object element owns the evidence for this decision, including equality attributes, label text. At this level, domain concepts can become schemas and persistence choices without losing ownership, identity, or privacy meaning; allowing the model through without that evidence would move an unresolved choice into generated infrastructure. The concrete failure this rule prevents is: ValueObject ' ' has no equalityAttributes.

### When it applies

The rule has no guard, so it applies to every instance of this context in the validated model.

There is no guard expression; every instance of the context is checked.

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.equalityAttributes.notEmpty()
```

This rule reads: `equalityAttributes`, `labelText`.

### Diagnostic and repair

> Error [CIM-VO-002] ValueObject ' ' has no equalityAttributes. Suggestion: select the attributes that determine value equality.

**How to fix it:**

select the attributes that determine value equality.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---

## `DomainRelationshipHasDistinctEnds`

**Context:** `CIM!DomainRelationship`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/cim/rules/domain-data.evl:75`

### Why this rule exists

A relationship from a concept to itself may be valid in some domains, but this metamodel uses a distinct-end relationship to represent a meaningful connection between concepts. The rule catches accidental self-links before ownership and multiplicity are interpreted.

### When it applies

The rule has no guard, so it applies to every instance of this context in the validated model.

There is no guard expression; every instance of the context is checked.

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.source <> self.target
```

This rule reads: `source`, `target`, `labelText`.

### Diagnostic and repair

> Error [CIM-REL-001] DomainRelationship ' ' connects a concept to itself. Suggestion: connect distinct source and target concepts, or model the self-relation explicitly with named roles and rationale if it is truly intentional.

**How to fix it:**

connect distinct source and target concepts, or model the self-relation explicitly with named roles and rationale if it is truly intentional.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---

## `DomainRelationshipHasRolesAndMultiplicities`

**Context:** `CIM!DomainRelationship`  
**Classification:** Advisory critique (warning)  
**Source:** `mde/validation/cim/rules/domain-data.evl:83`

### Why this rule exists

Advises that domain relationship has roles and multiplicities. This is a review signal about source role, target role, source multiplicity, target multiplicity, label text, not a cosmetic naming preference. In this part of the model, domain concepts can become schemas and persistence choices without losing ownership, identity, or privacy meaning; the warning makes a decision visible while it can still be discussed and changed. The concrete gap is: DomainRelationship ' ' lacks role names or multiplicities. If it is left unexplained, a later transformation, generator, or reviewer has to invent an assumption.

### When it applies

The rule has no guard, so it applies to every instance of this context in the validated model.

There is no guard expression; every instance of the context is checked.

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.sourceRole.hasText() and self.targetRole.hasText() and self.sourceMultiplicity.hasMultiplicityBounds() and self.targetMultiplicity.hasMultiplicityBounds()
```

This rule reads: `sourceRole`, `targetRole`, `sourceMultiplicity`, `targetMultiplicity`, `labelText`.

### Diagnostic and repair

> Warning [CIM-REL-002] DomainRelationship ' ' lacks role names or multiplicities. Suggestion: provide sourceRole, targetRole, sourceMultiplicity, and targetMultiplicity to remove ambiguity.

**How to fix it:**

provide sourceRole, targetRole, sourceMultiplicity, and targetMultiplicity to remove ambiguity.

A critique does not necessarily make the model invalid. It is a deliberate quality/readiness signal; disposition it by improving the model, documenting why it is acceptable, or carrying the decision into the review record.

---

## `OwnershipRelationshipUsesOwnershipType`

**Context:** `CIM!DomainRelationship`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/cim/rules/domain-data.evl:93`

### Why this rule exists

Checks that ownership relationship uses ownership type. The domain relationship element owns the evidence for this decision, including ownership, relationship type, label text. At this level, domain concepts can become schemas and persistence choices without losing ownership, identity, or privacy meaning; allowing the model through without that evidence would move an unresolved choice into generated infrastructure. The concrete failure this rule prevents is: DomainRelationship ' ' is marked ownership=true but relationshipType is not COMPOSITION, AGGREGATION, or OWNERSHIP.

### When it applies

The rule is considered only when the guard below is true. A false guard is intentional: it means this invariant is not relevant to the current variant or modeling situation.

```evl
guard : self.ownership = true
```

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : (self.relationshipType = CIM!DomainRelationshipType#COMPOSITION) or (self.relationshipType = CIM!DomainRelationshipType#AGGREGATION) or (self.relationshipType = CIM!DomainRelationshipType#OWNERSHIP)
```

This rule reads: `ownership`, `relationshipType`, `labelText`.

### Diagnostic and repair

> Error [CIM-REL-003] DomainRelationship ' ' is marked ownership=true but relationshipType is not COMPOSITION, AGGREGATION, or OWNERSHIP. Suggestion: change relationshipType or set ownership=false.

**How to fix it:**

change relationshipType or set ownership=false.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---

## `RootIsMember`

**Context:** `CIM!AggregateCandidate`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/cim/rules/domain-data.evl:109`

### Why this rule exists

Checks that root is member. The aggregate candidate element owns the evidence for this decision, including members, root, label text. At this level, domain concepts can become schemas and persistence choices without losing ownership, identity, or privacy meaning; allowing the model through without that evidence would move an unresolved choice into generated infrastructure. The concrete failure this rule prevents is: AggregateCandidate ' ' has a root that is not part of members.

### When it applies

The rule has no guard, so it applies to every instance of this context in the validated model.

There is no guard expression; every instance of the context is checked.

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.members.includes(self.root)
```

This rule reads: `members`, `root`, `labelText`.

### Diagnostic and repair

> Error [CIM-AGG-001] AggregateCandidate ' ' has a root that is not part of members. Suggestion: add the root entity to members or select a root from the existing member entities.

**How to fix it:**

add the root entity to members or select a root from the existing member entities.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---

## `StrongConsistencyRequiresRationale`

**Context:** `CIM!AggregateCandidate`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/cim/rules/domain-data.evl:117`

### Why this rule exists

Checks that strong consistency requires rationale. The aggregate candidate element owns the evidence for this decision, including strong consistency required, consistency boundary rationale, label text. At this level, domain concepts can become schemas and persistence choices without losing ownership, identity, or privacy meaning; allowing the model through without that evidence would move an unresolved choice into generated infrastructure. The concrete failure this rule prevents is: AggregateCandidate ' ' requires strong consistency but has no consistencyBoundaryRationale.

### When it applies

The rule is considered only when the guard below is true. A false guard is intentional: it means this invariant is not relevant to the current variant or modeling situation.

```evl
guard : self.strongConsistencyRequired = true
```

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.consistencyBoundaryRationale.hasText()
```

This rule reads: `strongConsistencyRequired`, `consistencyBoundaryRationale`, `labelText`.

### Diagnostic and repair

> Error [CIM-AGG-002] AggregateCandidate ' ' requires strong consistency but has no consistencyBoundaryRationale. Suggestion: explain the invariant or business rule that makes the strong consistency boundary necessary.

**How to fix it:**

explain the invariant or business rule that makes the strong consistency boundary necessary.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---

## `StrongConsistencyExpectationIsConsistent`

**Context:** `CIM!AggregateCandidate`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/cim/rules/domain-data.evl:126`

### Why this rule exists

Checks that strong consistency expectation is consistent. The aggregate candidate element owns the evidence for this decision, including strong consistency required, consistency expectation, label text. At this level, domain concepts can become schemas and persistence choices without losing ownership, identity, or privacy meaning; allowing the model through without that evidence would move an unresolved choice into generated infrastructure. The concrete failure this rule prevents is: AggregateCandidate ' ' sets strongConsistencyRequired=true but consistencyExpectation does not reflect strong consistency.

### When it applies

The rule is considered only when the guard below is true. A false guard is intentional: it means this invariant is not relevant to the current variant or modeling situation.

```evl
guard : self.strongConsistencyRequired = true
```

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : (self.consistencyExpectation = CIM!ConsistencyExpectation#SINGLE_ENTITY) or (self.consistencyExpectation = CIM!ConsistencyExpectation#MULTI_ENTITY_STRONG_CONSISTENCY)
```

This rule reads: `strongConsistencyRequired`, `consistencyExpectation`, `labelText`.

### Diagnostic and repair

> Error [CIM-AGG-003] AggregateCandidate ' ' sets strongConsistencyRequired=true but consistencyExpectation does not reflect strong consistency. Suggestion: set consistencyExpectation to SINGLE_ENTITY or MULTI_ENTITY_STRONG_CONSISTENCY, or set strongConsistencyRequired=false.

**How to fix it:**

set consistencyExpectation to SINGLE_ENTITY or MULTI_ENTITY_STRONG_CONSISTENCY, or set strongConsistencyRequired=false.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---

## `EventualConsistencyHasConflictPolicy`

**Context:** `CIM!AggregateCandidate`  
**Classification:** Advisory critique (warning)  
**Source:** `mde/validation/cim/rules/domain-data.evl:137`

### Why this rule exists

Advises that eventual consistency has conflict policy. This is a review signal about consistency expectation, conflict resolution policy, label text, not a cosmetic naming preference. In this part of the model, domain concepts can become schemas and persistence choices without losing ownership, identity, or privacy meaning; the warning makes a decision visible while it can still be discussed and changed. The concrete gap is: AggregateCandidate ' ' allows eventual/manual consistency but has no conflictResolutionPolicy. If it is left unexplained, a later transformation, generator, or reviewer has to invent an assumption.

### When it applies

The rule is considered only when the guard below is true. A false guard is intentional: it means this invariant is not relevant to the current variant or modeling situation.

```evl
guard : (self.consistencyExpectation = CIM!ConsistencyExpectation#EVENTUAL_CONSISTENCY_ACCEPTABLE) or (self.consistencyExpectation = CIM!ConsistencyExpectation#MANUAL_RECONCILIATION_ACCEPTABLE)
```

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.conflictResolutionPolicy.hasText()
```

This rule reads: `consistencyExpectation`, `conflictResolutionPolicy`, `labelText`.

### Diagnostic and repair

> Warning [CIM-AGG-004] AggregateCandidate ' ' allows eventual/manual consistency but has no conflictResolutionPolicy. Suggestion: describe how conflicting updates, reconciliation, or stale reads are handled.

**How to fix it:**

describe how conflicting updates, reconciliation, or stale reads are handled.

A critique does not necessarily make the model invalid. It is a deliberate quality/readiness signal; disposition it by improving the model, documenting why it is acceptable, or carrying the decision into the review record.

---

## `AggregateCommandsDeclareIdempotency`

**Context:** `CIM!AggregateCandidate`  
**Classification:** Advisory critique (warning)  
**Source:** `mde/validation/cim/rules/domain-data.evl:148`

### Why this rule exists

Advises that aggregate commands declare idempotency. This is a review signal about handled commands, duplicate submission possible, idempotency business key, label text, not a cosmetic naming preference. In this part of the model, domain concepts can become schemas and persistence choices without losing ownership, identity, or privacy meaning; the warning makes a decision visible while it can still be discussed and changed. The concrete gap is: AggregateCandidate ' ' handles duplicate-prone commands without command-level or aggregate-level idempotencyBusinessKey. If it is left unexplained, a later transformation, generator, or reviewer has to invent an assumption.

### When it applies

The rule is considered only when the guard below is true. A false guard is intentional: it means this invariant is not relevant to the current variant or modeling situation.

```evl
guard : self.handledCommands.exists(commandItem | commandItem.duplicateSubmissionPossible = true)
```

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.handledCommands.select(commandItem | commandItem.duplicateSubmissionPossible = true).forAll(commandItem | commandItem.idempotencyBusinessKey.hasText() or self.idempotencyBusinessKey.hasText() )
```

This rule reads: `handledCommands`, `duplicateSubmissionPossible`, `idempotencyBusinessKey`, `labelText`.

### Diagnostic and repair

> Warning [CIM-AGG-005] AggregateCandidate ' ' handles duplicate-prone commands without command-level or aggregate-level idempotencyBusinessKey. Suggestion: define a business key such as request number, external correlation ID, or natural transaction ID.

**How to fix it:**

define a business key such as request number, external correlation ID, or natural transaction ID.

A critique does not necessarily make the model invalid. It is a deliberate quality/readiness signal; disposition it by improving the model, documenting why it is acceptable, or carrying the decision into the review record.

---

## `BusinessInvariantIsExpressed`

**Context:** `CIM!BusinessInvariant`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/cim/rules/domain-data.evl:164`

### Why this rule exists

Checks that business invariant is expressed. The business invariant element owns the evidence for this decision, including natural language statement, expression, expression model, label text. At this level, domain concepts can become schemas and persistence choices without losing ownership, identity, or privacy meaning; allowing the model through without that evidence would move an unresolved choice into generated infrastructure. The concrete failure this rule prevents is: BusinessInvariant ' ' has no naturalLanguageStatement, expression, or expressionModel.

### When it applies

The rule has no guard, so it applies to every instance of this context in the validated model.

There is no guard expression; every instance of the context is checked.

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.naturalLanguageStatement.hasText() or self.expression.hasText() or self.expressionModel.hasExpressionBody()
```

This rule reads: `naturalLanguageStatement`, `expression`, `expressionModel`, `labelText`.

### Diagnostic and repair

> Error [CIM-INV-001] BusinessInvariant ' ' has no naturalLanguageStatement, expression, or expressionModel. Suggestion: write the invariant as a business rule, optionally adding expressionModel.body for automatable validation.

**How to fix it:**

write the invariant as a business rule, optionally adding expressionModel.body for automatable validation.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---

## `BusinessInvariantConstrainsConcepts`

**Context:** `CIM!BusinessInvariant`  
**Classification:** Advisory critique (warning)  
**Source:** `mde/validation/cim/rules/domain-data.evl:172`

### Why this rule exists

Advises that business invariant constrains concepts. This is a review signal about constrained concepts, label text, not a cosmetic naming preference. In this part of the model, domain concepts can become schemas and persistence choices without losing ownership, identity, or privacy meaning; the warning makes a decision visible while it can still be discussed and changed. The concrete gap is: BusinessInvariant ' ' is not linked to constrainedConcepts. If it is left unexplained, a later transformation, generator, or reviewer has to invent an assumption.

### When it applies

The rule has no guard, so it applies to every instance of this context in the validated model.

There is no guard expression; every instance of the context is checked.

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.constrainedConcepts.notEmpty()
```

This rule reads: `constrainedConcepts`, `labelText`.

### Diagnostic and repair

> Warning [CIM-INV-002] BusinessInvariant ' ' is not linked to constrainedConcepts. Suggestion: link the entity, aggregate, value object, or relationship that the invariant constrains.

**How to fix it:**

link the entity, aggregate, value object, or relationship that the invariant constrains.

A critique does not necessarily make the model invalid. It is a deliberate quality/readiness signal; disposition it by improving the model, documenting why it is acceptable, or carrying the decision into the review record.

---

## `StructuredItemsUseObjectOrList`

**Context:** `CIM!InformationItem`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/cim/rules/domain-data.evl:184`

### Why this rule exists

Checks that structured items use object or list. The information item element owns the evidence for this decision, including sub items, type, label text. At this level, domain concepts can become schemas and persistence choices without losing ownership, identity, or privacy meaning; allowing the model through without that evidence would move an unresolved choice into generated infrastructure. The concrete failure this rule prevents is: InformationItem ' ' has subItems but is not typed as OBJECT or LIST.

### When it applies

The rule is considered only when the guard below is true. A false guard is intentional: it means this invariant is not relevant to the current variant or modeling situation.

```evl
guard : self.subItems.notEmpty()
```

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : (self.type.asString() = "OBJECT") or (self.type.asString() = "LIST")
```

This rule reads: `subItems`, `type`, `labelText`.

### Diagnostic and repair

> Error [CIM-INFO-000B] InformationItem ' ' has subItems but is not typed as OBJECT or LIST. Suggestion: use a structured business type for nested information.

**How to fix it:**

use a structured business type for nested information.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---

## `UniqueChildNames`

**Context:** `CIM!InformationItem`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/cim/rules/domain-data.evl:193`

### Why this rule exists

Checks that unique child names. The information item element owns the evidence for this decision, including sub items, label text. At this level, domain concepts can become schemas and persistence choices without losing ownership, identity, or privacy meaning; allowing the model through without that evidence would move an unresolved choice into generated infrastructure. The concrete failure this rule prevents is: InformationItem ' ' has duplicate child names.

### When it applies

The rule has no guard, so it applies to every instance of this context in the validated model.

There is no guard expression; every instance of the context is checked.

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : collectionHasUniqueNames(self.subItems)
```

This rule reads: `subItems`, `labelText`.

### Diagnostic and repair

> Error [CIM-INFO-000C] InformationItem ' ' has duplicate child names. Suggestion: make nested InformationItem businessName/name values unique.

**How to fix it:**

make nested InformationItem businessName/name values unique.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---

## `InformationItemHasType`

**Context:** `CIM!InformationItem`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/cim/rules/domain-data.evl:201`

### Why this rule exists

Checks that information item has type. The information item element owns the evidence for this decision, including type, label text. At this level, domain concepts can become schemas and persistence choices without losing ownership, identity, or privacy meaning; allowing the model through without that evidence would move an unresolved choice into generated infrastructure. The concrete failure this rule prevents is: InformationItem ' ' has no type.

### When it applies

The rule has no guard, so it applies to every instance of this context in the validated model.

There is no guard expression; every instance of the context is checked.

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.type.isDefined()
```

This rule reads: `type`, `labelText`.

### Diagnostic and repair

> Error [CIM-INFO-001] InformationItem ' ' has no type. Suggestion: choose the PrimitiveBusinessType that best represents the business value, such as TEXT, MONEY, DATE, EMAIL, IDENTIFIER, OBJECT, or LIST.

**How to fix it:**

choose the PrimitiveBusinessType that best represents the business value, such as TEXT, MONEY, DATE, EMAIL, IDENTIFIER, OBJECT, or LIST.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---

## `InformationItemHasBusinessName`

**Context:** `CIM!InformationItem`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/cim/rules/domain-data.evl:209`

### Why this rule exists

Checks that information item has business name. The information item element owns the evidence for this decision, including business name, name. At this level, domain concepts can become schemas and persistence choices without losing ownership, identity, or privacy meaning; allowing the model through without that evidence would move an unresolved choice into generated infrastructure. The concrete failure this rule prevents is: InformationItem has neither businessName nor inherited name.

### When it applies

The rule has no guard, so it applies to every instance of this context in the validated model.

There is no guard expression; every instance of the context is checked.

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.businessName.hasText() or self.name.hasText()
```

This rule reads: `businessName`, `name`.

### Diagnostic and repair

> Error [CIM-INFO-003] InformationItem has neither businessName nor inherited name. Suggestion: provide a business-facing name such as 'Customer email address' rather than a technical field name alone.

**How to fix it:**

provide a business-facing name such as 'Customer email address' rather than a technical field name alone.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---

## `PersonalOrSensitiveInformationHasPrivacyConstraint`

**Context:** `CIM!InformationItem`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/cim/rules/domain-data.evl:217`

### Why this rule exists

Checks that personal or sensitive information has privacy constraint. The information item element owns the evidence for this decision, including requires privacy controls, privacy constraints, label text. At this level, domain concepts can become schemas and persistence choices without losing ownership, identity, or privacy meaning; allowing the model through without that evidence would move an unresolved choice into generated infrastructure. The concrete failure this rule prevents is: InformationItem ' ' is personal, sensitive, financial, health, or regulated data but has no PrivacyConstraint.

### When it applies

The rule is considered only when the guard below is true. A false guard is intentional: it means this invariant is not relevant to the current variant or modeling situation.

```evl
guard : self.requiresPrivacyControls()
```

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.privacyConstraints.notEmpty()
```

This rule reads: `requiresPrivacyControls`, `privacyConstraints`, `labelText`.

### Diagnostic and repair

> Error [CIM-INFO-004] InformationItem ' ' is personal, sensitive, financial, health, or regulated data but has no PrivacyConstraint. Suggestion: add a PrivacyConstraint covering purpose, legalBasis, retention, minimization, and data subject obligations.

**How to fix it:**

add a PrivacyConstraint covering purpose, legalBasis, retention, minimization, and data subject obligations.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---

## `CollectionInformationHasMultiplicity`

**Context:** `CIM!InformationItem`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/cim/rules/domain-data.evl:226`

### Why this rule exists

Checks that collection information has multiplicity. The information item element owns the evidence for this decision, including collection, cardinality, label text. At this level, domain concepts can become schemas and persistence choices without losing ownership, identity, or privacy meaning; allowing the model through without that evidence would move an unresolved choice into generated infrastructure. The concrete failure this rule prevents is: Collection InformationItem ' ' has no cardinality.

### When it applies

The rule is considered only when the guard below is true. A false guard is intentional: it means this invariant is not relevant to the current variant or modeling situation.

```evl
guard : self.collection = true
```

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.cardinality.isDefined() and self.cardinality.lowerBound.isDefined() and (self.cardinality.upperBound.isDefined() or self.cardinality.unbounded = true)
```

This rule reads: `collection`, `cardinality`, `labelText`.

### Diagnostic and repair

> Error [CIM-INFO-005] Collection InformationItem ' ' has no cardinality. Suggestion: specify expected cardinality bounds such as 0.._, 1.._, 0..10, or business-specific limits.

**How to fix it:**

specify expected cardinality bounds such as 0.._, 1.._, 0..10, or business-specific limits.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---

## `DerivedInformationHasDerivationRule`

**Context:** `CIM!InformationItem`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/cim/rules/domain-data.evl:236`

### Why this rule exists

Checks that derived information has derivation rule. The information item element owns the evidence for this decision, including derived, derivation rule, label text. At this level, domain concepts can become schemas and persistence choices without losing ownership, identity, or privacy meaning; allowing the model through without that evidence would move an unresolved choice into generated infrastructure. The concrete failure this rule prevents is: Derived InformationItem ' ' lacks derivationRule.

### When it applies

The rule is considered only when the guard below is true. A false guard is intentional: it means this invariant is not relevant to the current variant or modeling situation.

```evl
guard : self.derived = true
```

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.derivationRule.hasText()
```

This rule reads: `derived`, `derivationRule`, `labelText`.

### Diagnostic and repair

> Error [CIM-INFO-006] Derived InformationItem ' ' lacks derivationRule. Suggestion: explain the business formula, source items, or policy used to derive the value.

**How to fix it:**

explain the business formula, source items, or policy used to derive the value.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---

## `SearchOrReportItemHasSourceOfTruth`

**Context:** `CIM!InformationItem`  
**Classification:** Advisory critique (warning)  
**Source:** `mde/validation/cim/rules/domain-data.evl:245`

### Why this rule exists

Advises that search or report item has source of truth. This is a review signal about search relevant, reporting relevant, source of truth, label text, not a cosmetic naming preference. In this part of the model, domain concepts can become schemas and persistence choices without losing ownership, identity, or privacy meaning; the warning makes a decision visible while it can still be discussed and changed. The concrete gap is: Search/reporting InformationItem ' ' has no sourceOfTruth. If it is left unexplained, a later transformation, generator, or reviewer has to invent an assumption.

### When it applies

The rule is considered only when the guard below is true. A false guard is intentional: it means this invariant is not relevant to the current variant or modeling situation.

```evl
guard : (self.searchRelevant = true) or (self.reportingRelevant = true)
```

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.sourceOfTruth.hasText()
```

This rule reads: `searchRelevant`, `reportingRelevant`, `sourceOfTruth`, `labelText`.

### Diagnostic and repair

> Warning [CIM-INFO-007] Search/reporting InformationItem ' ' has no sourceOfTruth. Suggestion: identify the authoritative business source to avoid inconsistent read models and reports.

**How to fix it:**

identify the authoritative business source to avoid inconsistent read models and reports.

A critique does not necessarily make the model invalid. It is a deliberate quality/readiness signal; disposition it by improving the model, documenting why it is acceptable, or carrying the decision into the review record.

---

## `LengthBoundsAreConsistent`

**Context:** `CIM!InformationItem`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/cim/rules/domain-data.evl:254`

### Why this rule exists

Checks that length bounds are consistent. The information item element owns the evidence for this decision, including min length, max length, label text. At this level, domain concepts can become schemas and persistence choices without losing ownership, identity, or privacy meaning; allowing the model through without that evidence would move an unresolved choice into generated infrastructure. The concrete failure this rule prevents is: InformationItem ' ' has minLength greater than maxLength.

### When it applies

The rule is considered only when the guard below is true. A false guard is intentional: it means this invariant is not relevant to the current variant or modeling situation.

```evl
guard : self.minLength.isDefined() and self.maxLength.isDefined()
```

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.minLength <= self.maxLength
```

This rule reads: `minLength`, `maxLength`, `labelText`.

### Diagnostic and repair

> Error [CIM-INFO-008] InformationItem ' ' has minLength greater than maxLength. Suggestion: correct minLength/maxLength so the lower bound is less than or equal to the upper bound.

**How to fix it:**

correct minLength/maxLength so the lower bound is less than or equal to the upper bound.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---

## `DataClassificationHasKind`

**Context:** `CIM!DataClassification`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/cim/rules/domain-data.evl:267`

### Why this rule exists

Checks that data classification has kind. The data classification element owns the evidence for this decision, including kind, label text. At this level, domain concepts can become schemas and persistence choices without losing ownership, identity, or privacy meaning; allowing the model through without that evidence would move an unresolved choice into generated infrastructure. The concrete failure this rule prevents is: DataClassification ' ' has no kind.

### When it applies

The rule has no guard, so it applies to every instance of this context in the validated model.

There is no guard expression; every instance of the context is checked.

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.kind.isDefined()
```

This rule reads: `kind`, `labelText`.

### Diagnostic and repair

> Error [CIM-DATA-001] DataClassification ' ' has no kind. Suggestion: set kind to PUBLIC, INTERNAL, CONFIDENTIAL, PERSONAL, SENSITIVE_PERSONAL, FINANCIAL, HEALTH, AUTHENTICATION_SECRET, or REGULATED.

**How to fix it:**

set kind to PUBLIC, INTERNAL, CONFIDENTIAL, PERSONAL, SENSITIVE_PERSONAL, FINANCIAL, HEALTH, AUTHENTICATION_SECRET, or REGULATED.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---

## `SensitiveDataRequiresProtectionExpectation`

**Context:** `CIM!DataClassification`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/cim/rules/domain-data.evl:275`

### Why this rule exists

Classification alone does not say what to do. This rule requires encryption or masking expectations so privacy-sensitive information produces an actionable architecture decision.

### When it applies

The rule is considered only when the guard below is true. A false guard is intentional: it means this invariant is not relevant to the current variant or modeling situation.

```evl
guard : self.requiresStrongProtection()
```

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : (self.encryptionExpected = true) and (self.auditAccessRequired = true)
```

This rule reads: `requiresStrongProtection`, `encryptionExpected`, `auditAccessRequired`, `labelText`.

### Diagnostic and repair

> Error [CIM-DATA-002] DataClassification ' ' represents sensitive/protected data without encryptionExpected=true and auditAccessRequired=true. Suggestion: enable encryption and audit access, or downgrade kind only if the business classification was wrong.

**How to fix it:**

enable encryption and audit access, or downgrade kind only if the business classification was wrong.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---

## `PersonalDataHasIdentifiability`

**Context:** `CIM!DataClassification`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/cim/rules/domain-data.evl:284`

### Why this rule exists

Checks that personal data has identifiability. The data classification element owns the evidence for this decision, including is personal kind, identifiability, label text. At this level, domain concepts can become schemas and persistence choices without losing ownership, identity, or privacy meaning; allowing the model through without that evidence would move an unresolved choice into generated infrastructure. The concrete failure this rule prevents is: Personal DataClassification ' ' must declare a personal identifiability level.

### When it applies

The rule is considered only when the guard below is true. A false guard is intentional: it means this invariant is not relevant to the current variant or modeling situation.

```evl
guard : self.isPersonalKind()
```

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : (self.identifiability = CIM!Identifiability#PSEUDONYMOUS) or (self.identifiability = CIM!Identifiability#DIRECTLY_IDENTIFYING) or (self.identifiability = CIM!Identifiability#INDIRECTLY_IDENTIFYING)
```

This rule reads: `isPersonalKind`, `identifiability`, `labelText`.

### Diagnostic and repair

> Error [CIM-DATA-003] Personal DataClassification ' ' must declare a personal identifiability level. Suggestion: set identifiability to PSEUDONYMOUS, DIRECTLY_IDENTIFYING, or INDIRECTLY_IDENTIFYING.

**How to fix it:**

set identifiability to PSEUDONYMOUS, DIRECTLY_IDENTIFYING, or INDIRECTLY_IDENTIFYING.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---

## `RegulatedDataHasRegulatoryCategory`

**Context:** `CIM!DataClassification`  
**Classification:** Advisory critique (warning)  
**Source:** `mde/validation/cim/rules/domain-data.evl:296`

### Why this rule exists

Advises that regulated data has regulatory category. This is a review signal about kind, regulatory category, classification rationale, label text, not a cosmetic naming preference. In this part of the model, domain concepts can become schemas and persistence choices without losing ownership, identity, or privacy meaning; the warning makes a decision visible while it can still be discussed and changed. The concrete gap is: DataClassification ' ' is regulated/financial/health data without regulatoryCategory or classificationRationale. If it is left unexplained, a later transformation, generator, or reviewer has to invent an assumption.

### When it applies

The rule is considered only when the guard below is true. A false guard is intentional: it means this invariant is not relevant to the current variant or modeling situation.

```evl
guard : (self.kind = CIM!DataKind#REGULATED) or (self.kind = CIM!DataKind#FINANCIAL) or (self.kind = CIM!DataKind#HEALTH)
```

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.regulatoryCategory.hasText() and self.classificationRationale.hasText()
```

This rule reads: `kind`, `regulatoryCategory`, `classificationRationale`, `labelText`.

### Diagnostic and repair

> Warning [CIM-DATA-004] DataClassification ' ' is regulated/financial/health data without regulatoryCategory or classificationRationale. Suggestion: identify the regulation/category and explain why the data is classified this way.

**How to fix it:**

identify the regulation/category and explain why the data is classified this way.

A critique does not necessarily make the model invalid. It is a deliberate quality/readiness signal; disposition it by improving the model, documenting why it is acceptable, or carrying the decision into the review record.

---
