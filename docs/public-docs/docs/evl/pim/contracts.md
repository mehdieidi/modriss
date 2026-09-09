# PIM validation — Contracts

Contract rules protect the boundaries through which functions, APIs, events, and messages communicate. They require meaningful input/output shape, typed fields, compatible versions, and explicit handling for sensitive or externally sourced schemas.

Source profile: `mde/validation/pim/rules/contracts.evl`.

## Reading these rules

Each entry preserves the actual EVL guard and check. Read the guard as the applicability boundary, not as part of the invariant: when it is false, the rule is intentionally skipped. The diagnostic is the runtime-facing message emitted by EVL; its final sentence usually contains the repository's recommended repair.

---

## `ContractHasAtLeastInputOrOutput`

**Context:** `PIM!FunctionContract`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/pim/rules/contracts.evl:8`

### Why this rule exists

Checks that contract has at least input or output. The function contract element owns the evidence for this decision, including input schema, output schema, display name. At this level, the boundaries between functions, APIs, events, and messages remain typed and compatible during transformation; allowing the model through without that evidence would move an unresolved choice into generated infrastructure. The concrete failure this rule prevents is: [PIM-CONTRACT-001] Function contract ' ' has neither inputSchema nor outputSchema.

### When it applies

The rule has no guard, so it applies to every instance of this context in the validated model.

There is no guard expression; every instance of the context is checked.

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.inputSchema.isDefined() or self.outputSchema.isDefined()
```

This rule reads: `inputSchema`, `outputSchema`, `displayName`.

### Diagnostic and repair

> [PIM-CONTRACT-001] Function contract ' ' has neither inputSchema nor outputSchema. Fix: attach at least one schema so generated handlers have a clear contract.

**How to fix it:**

attach at least one schema so generated handlers have a clear contract.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---

## `InputValidationRequiresInputSchema`

**Context:** `PIM!FunctionContract`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/pim/rules/contracts.evl:14`

### Why this rule exists

Checks that input validation requires input schema. The function contract element owns the evidence for this decision, including validates input, input schema, display name. At this level, the boundaries between functions, APIs, events, and messages remain typed and compatible during transformation; allowing the model through without that evidence would move an unresolved choice into generated infrastructure. The concrete failure this rule prevents is: [PIM-CONTRACT-002] Contract ' ' validates input but has no inputSchema.

### When it applies

The rule is considered only when the guard below is true. A false guard is intentional: it means this invariant is not relevant to the current variant or modeling situation.

```evl
guard : self.validatesInput.isTrue()
```

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.inputSchema.isDefined()
```

This rule reads: `validatesInput`, `inputSchema`, `displayName`.

### Diagnostic and repair

> [PIM-CONTRACT-002] Contract ' ' validates input but has no inputSchema. Fix: attach inputSchema or set validatesInput to false.

**How to fix it:**

attach inputSchema or set validatesInput to false.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---

## `OutputValidationRequiresOutputSchema`

**Context:** `PIM!FunctionContract`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/pim/rules/contracts.evl:21`

### Why this rule exists

Checks that output validation requires output schema. The function contract element owns the evidence for this decision, including validates output, output schema, display name. At this level, the boundaries between functions, APIs, events, and messages remain typed and compatible during transformation; allowing the model through without that evidence would move an unresolved choice into generated infrastructure. The concrete failure this rule prevents is: [PIM-CONTRACT-003] Contract ' ' validates output but has no outputSchema.

### When it applies

The rule is considered only when the guard below is true. A false guard is intentional: it means this invariant is not relevant to the current variant or modeling situation.

```evl
guard : self.validatesOutput.isTrue()
```

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.outputSchema.isDefined()
```

This rule reads: `validatesOutput`, `outputSchema`, `displayName`.

### Diagnostic and repair

> [PIM-CONTRACT-003] Contract ' ' validates output but has no outputSchema. Fix: attach outputSchema or set validatesOutput to false.

**How to fix it:**

attach outputSchema or set validatesOutput to false.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---

## `CorrelationIdFieldShouldExistInSchema`

**Context:** `PIM!FunctionContract`  
**Classification:** Advisory critique (warning)  
**Source:** `mde/validation/pim/rules/contracts.evl:28`

### Why this rule exists

Advises that correlation id field should exist in schema. This is a review signal about correlation id field, input schema, display name, not a cosmetic naming preference. In this part of the model, the boundaries between functions, APIs, events, and messages remain typed and compatible during transformation; the warning makes a decision visible while it can still be discussed and changed. The concrete gap is: [PIM-CONTRACT-004] Contract ' ' declares correlationIdField ' ' but the input schema does not contain such a field. Suggested fix: add the field to the schema or correct the field name. If it is left unexplained, a later transformation, generator, or reviewer has to invent an assumption.

### When it applies

The rule is considered only when the guard below is true. A false guard is intentional: it means this invariant is not relevant to the current variant or modeling situation.

```evl
guard : self.correlationIdField.hasText() and self.inputSchema.isDefined()
```

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.inputSchema.hasFieldNamed(self.correlationIdField)
```

This rule reads: `correlationIdField`, `inputSchema`, `displayName`.

### Diagnostic and repair

> [PIM-CONTRACT-004] Contract ' ' declares correlationIdField ' ' but the input schema does not contain such a field. Suggested fix: add the field to the schema or correct the field name.

**How to fix it:**

This is advisory rather than a structural blocker. Decide whether the modeled situation genuinely needs the recommendation; if it does, change the referenced model features so the check evaluates to true and record the rationale when the omission is intentional.

A critique does not necessarily make the model invalid. It is a deliberate quality/readiness signal; disposition it by improving the model, documenting why it is acceptable, or carrying the decision into the review record.

---

## `IdempotencyKeyFieldShouldExistInSchema`

**Context:** `PIM!FunctionContract`  
**Classification:** Advisory critique (warning)  
**Source:** `mde/validation/pim/rules/contracts.evl:35`

### Why this rule exists

Advises that idempotency key field should exist in schema. This is a review signal about idempotency key field, input schema, display name, not a cosmetic naming preference. In this part of the model, the boundaries between functions, APIs, events, and messages remain typed and compatible during transformation; the warning makes a decision visible while it can still be discussed and changed. The concrete gap is: [PIM-CONTRACT-005] Contract ' ' declares idempotencyKeyField ' ' but the input schema does not contain such a field. Suggested fix: add the field or update idempotencyKeyField. If it is left unexplained, a later transformation, generator, or reviewer has to invent an assumption.

### When it applies

The rule is considered only when the guard below is true. A false guard is intentional: it means this invariant is not relevant to the current variant or modeling situation.

```evl
guard : self.idempotencyKeyField.hasText() and self.inputSchema.isDefined()
```

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.inputSchema.hasFieldNamed(self.idempotencyKeyField)
```

This rule reads: `idempotencyKeyField`, `inputSchema`, `displayName`.

### Diagnostic and repair

> [PIM-CONTRACT-005] Contract ' ' declares idempotencyKeyField ' ' but the input schema does not contain such a field. Suggested fix: add the field or update idempotencyKeyField.

**How to fix it:**

This is advisory rather than a structural blocker. Decide whether the modeled situation genuinely needs the recommendation; if it does, change the referenced model features so the check evaluates to true and record the rationale when the omission is intentional.

A critique does not necessarily make the model invalid. It is a deliberate quality/readiness signal; disposition it by improving the model, documenting why it is acceptable, or carrying the decision into the review record.

---

## `SchemaHasFieldsUnlessExternal`

**Context:** `PIM!Schema`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/pim/rules/contracts.evl:46`

### Why this rule exists

Checks that schema has fields unless external. The schema element owns the evidence for this decision, including external schema uri, fields, display name. At this level, the boundaries between functions, APIs, events, and messages remain typed and compatible during transformation; allowing the model through without that evidence would move an unresolved choice into generated infrastructure. The concrete failure this rule prevents is: [PIM-SCHEMA-001] Schema ' ' has no fields and no externalSchemaUri.

### When it applies

The rule has no guard, so it applies to every instance of this context in the validated model.

There is no guard expression; every instance of the context is checked.

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.externalSchemaUri.hasText() or self.fields.notEmpty()
```

This rule reads: `externalSchemaUri`, `fields`, `displayName`.

### Diagnostic and repair

> [PIM-SCHEMA-001] Schema ' ' has no fields and no externalSchemaUri. Fix: add SchemaField children or reference an externally managed schema URI.

**How to fix it:**

add SchemaField children or reference an externally managed schema URI.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---

## `SchemaVersionShouldBeSemver`

**Context:** `PIM!Schema`  
**Classification:** Advisory critique (warning)  
**Source:** `mde/validation/pim/rules/contracts.evl:52`

### Why this rule exists

Advises that schema version should be semver. This is a review signal about semantic version, display name, not a cosmetic naming preference. In this part of the model, the boundaries between functions, APIs, events, and messages remain typed and compatible during transformation; the warning makes a decision visible while it can still be discussed and changed. The concrete gap is: [PIM-SCHEMA-002] Schema ' ' has no SemVer-like semanticVersion. Suggested fix: use a version such as 1.0.0 so compatibility and contract evolution can be validated. If it is left unexplained, a later transformation, generator, or reviewer has to invent an assumption.

### When it applies

The rule has no guard, so it applies to every instance of this context in the validated model.

There is no guard expression; every instance of the context is checked.

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.semanticVersion.hasText() and self.semanticVersion.matches("^[0-9]+\\.[0-9]+\\.[0-9]+.*$")
```

This rule reads: `semanticVersion`, `displayName`.

### Diagnostic and repair

> [PIM-SCHEMA-002] Schema ' ' has no SemVer-like semanticVersion. Suggested fix: use a version such as 1.0.0 so compatibility and contract evolution can be validated.

**How to fix it:**

This is advisory rather than a structural blocker. Decide whether the modeled situation genuinely needs the recommendation; if it does, change the referenced model features so the check evaluates to true and record the rationale when the omission is intentional.

A critique does not necessarily make the model invalid. It is a deliberate quality/readiness signal; disposition it by improving the model, documenting why it is acceptable, or carrying the decision into the review record.

---

## `ExternalSchemaShouldStateCompatibility`

**Context:** `PIM!Schema`  
**Classification:** Advisory critique (warning)  
**Source:** `mde/validation/pim/rules/contracts.evl:58`

### Why this rule exists

Advises that external schema should state compatibility. This is a review signal about external schema uri, e is set, e class, display name, not a cosmetic naming preference. In this part of the model, the boundaries between functions, APIs, events, and messages remain typed and compatible during transformation; the warning makes a decision visible while it can still be discussed and changed. The concrete gap is: [PIM-SCHEMA-003] External schema ' ' does not state compatibility. Suggested fix: set compatibility to BACKWARD, FORWARD, FULL or NONE based on the evolution contract. If it is left unexplained, a later transformation, generator, or reviewer has to invent an assumption.

### When it applies

The rule is considered only when the guard below is true. A false guard is intentional: it means this invariant is not relevant to the current variant or modeling situation.

```evl
guard : self.externalSchemaUri.hasText()
```

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.eIsSet(self.eClass().getEStructuralFeature("compatibility"))
```

This rule reads: `externalSchemaUri`, `eIsSet`, `eClass`, `displayName`.

### Diagnostic and repair

> [PIM-SCHEMA-003] External schema ' ' does not state compatibility. Suggested fix: set compatibility to BACKWARD, FORWARD, FULL or NONE based on the evolution contract.

**How to fix it:**

This is advisory rather than a structural blocker. Decide whether the modeled situation genuinely needs the recommendation; if it does, change the referenced model features so the check evaluates to true and record the rationale when the omission is intentional.

A critique does not necessarily make the model invalid. It is a deliberate quality/readiness signal; disposition it by improving the model, documenting why it is acceptable, or carrying the decision into the review record.

---

## `SensitiveFieldIsClassified`

**Context:** `PIM!SchemaField`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/pim/rules/contracts.evl:69`

### Why this rule exists

Checks that sensitive field is classified. The schema field element owns the evidence for this decision, including personal data, sensitive data, secret value, classification, display name. At this level, the boundaries between functions, APIs, events, and messages remain typed and compatible during transformation; allowing the model through without that evidence would move an unresolved choice into generated infrastructure. The concrete failure this rule prevents is: [PIM-SCHEMA-004] Field ' ' is personal, sensitive or secret but has no classification.

### When it applies

The rule is considered only when the guard below is true. A false guard is intentional: it means this invariant is not relevant to the current variant or modeling situation.

```evl
guard : self.personalData.isTrue() or self.sensitiveData.isTrue() or self.secretValue.isTrue()
```

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.classification.hasText()
```

This rule reads: `personalData`, `sensitiveData`, `secretValue`, `classification`, `displayName`.

### Diagnostic and repair

> [PIM-SCHEMA-004] Field ' ' is personal, sensitive or secret but has no classification. Fix: set classification, for example PII, confidential, restricted, credential or secret.

**How to fix it:**

set classification, for example PII, confidential, restricted, credential or secret.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---

## `EnumFieldHasLiterals`

**Context:** `PIM!SchemaField`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/pim/rules/contracts.evl:76`

### Why this rule exists

Checks that enum field has literals. The schema field element owns the evidence for this decision, including field type, enum values, display name. At this level, the boundaries between functions, APIs, events, and messages remain typed and compatible during transformation; allowing the model through without that evidence would move an unresolved choice into generated infrastructure. The concrete failure this rule prevents is: [PIM-SCHEMA-005] Enum field ' ' has no enumValues.

### When it applies

The rule is considered only when the guard below is true. A false guard is intentional: it means this invariant is not relevant to the current variant or modeling situation.

```evl
guard : self.fieldType.enumIs("ENUM")
```

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.enumValues.notEmpty()
```

This rule reads: `fieldType`, `enumValues`, `displayName`.

### Diagnostic and repair

> [PIM-SCHEMA-005] Enum field ' ' has no enumValues. Fix: add SchemaEnumLiteral children for every allowed value.

**How to fix it:**

add SchemaEnumLiteral children for every allowed value.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---

## `ObjectFieldHasObjectSchema`

**Context:** `PIM!SchemaField`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/pim/rules/contracts.evl:83`

### Why this rule exists

Checks that object field has object schema. The schema field element owns the evidence for this decision, including field type, object schema, display name. At this level, the boundaries between functions, APIs, events, and messages remain typed and compatible during transformation; allowing the model through without that evidence would move an unresolved choice into generated infrastructure. The concrete failure this rule prevents is: [PIM-SCHEMA-006] Object field ' ' has no objectSchema.

### When it applies

The rule is considered only when the guard below is true. A false guard is intentional: it means this invariant is not relevant to the current variant or modeling situation.

```evl
guard : self.fieldType.enumIs("OBJECT")
```

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.objectSchema.isDefined()
```

This rule reads: `fieldType`, `objectSchema`, `displayName`.

### Diagnostic and repair

> [PIM-SCHEMA-006] Object field ' ' has no objectSchema. Fix: link the nested Schema that describes the object structure.

**How to fix it:**

link the nested Schema that describes the object structure.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---

## `FieldLengthBoundsAreValid`

**Context:** `PIM!SchemaField`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/pim/rules/contracts.evl:90`

### Why this rule exists

Checks that field length bounds are valid. The schema field element owns the evidence for this decision, including min length, max length, display name. At this level, the boundaries between functions, APIs, events, and messages remain typed and compatible during transformation; allowing the model through without that evidence would move an unresolved choice into generated infrastructure. The concrete failure this rule prevents is: [PIM-SCHEMA-007] Field ' ' has invalid minLength/maxLength.

### When it applies

The rule is considered only when the guard below is true. A false guard is intentional: it means this invariant is not relevant to the current variant or modeling situation.

```evl
guard : self.minLength.isDefined() and self.maxLength.isDefined()
```

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.minLength >= 0 and self.maxLength >= self.minLength
```

This rule reads: `minLength`, `maxLength`, `displayName`.

### Diagnostic and repair

> [PIM-SCHEMA-007] Field ' ' has invalid minLength/maxLength. Fix: make minLength non-negative and maxLength greater than or equal to minLength.

**How to fix it:**

make minLength non-negative and maxLength greater than or equal to minLength.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---

## `RequiredNullableFieldNeedsRationale`

**Context:** `PIM!SchemaField`  
**Classification:** Advisory critique (warning)  
**Source:** `mde/validation/pim/rules/contracts.evl:97`

### Why this rule exists

Advises that required nullable field needs rationale. This is a review signal about required, nullable, rationale, description for consumers, display name, not a cosmetic naming preference. In this part of the model, the boundaries between functions, APIs, events, and messages remain typed and compatible during transformation; the warning makes a decision visible while it can still be discussed and changed. The concrete gap is: [PIM-SCHEMA-008] Field ' ' is both required and nullable. Suggested fix: either make it non-nullable or explain the intended null semantics in descriptionForConsumers/rationale. If it is left unexplained, a later transformation, generator, or reviewer has to invent an assumption.

### When it applies

The rule is considered only when the guard below is true. A false guard is intentional: it means this invariant is not relevant to the current variant or modeling situation.

```evl
guard : self.required.isTrue() and self.nullable.isTrue()
```

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.rationale.hasText() or self.descriptionForConsumers.hasText()
```

This rule reads: `required`, `nullable`, `rationale`, `descriptionForConsumers`, `displayName`.

### Diagnostic and repair

> [PIM-SCHEMA-008] Field ' ' is both required and nullable. Suggested fix: either make it non-nullable or explain the intended null semantics in descriptionForConsumers/rationale.

**How to fix it:**

This is advisory rather than a structural blocker. Decide whether the modeled situation genuinely needs the recommendation; if it does, change the referenced model features so the check evaluates to true and record the rationale when the omission is intentional.

A critique does not necessarily make the model invalid. It is a deliberate quality/readiness signal; disposition it by improving the model, documenting why it is acceptable, or carrying the decision into the review record.

---

## `SensitiveFieldShouldNotExposeExamplesOrDefaults`

**Context:** `PIM!SchemaField`  
**Classification:** Advisory critique (warning)  
**Source:** `mde/validation/pim/rules/contracts.evl:104`

### Why this rule exists

Advises that sensitive field should not expose examples or defaults. This is a review signal about personal data, sensitive data, secret value, default value, example, not a cosmetic naming preference. In this part of the model, the boundaries between functions, APIs, events, and messages remain typed and compatible during transformation; the warning makes a decision visible while it can still be discussed and changed. The concrete gap is: [PIM-SCHEMA-009] Sensitive field ' ' has example/default data. Suggested fix: remove real-looking examples/defaults or replace them with clearly synthetic, non-sensitive placeholders. If it is left unexplained, a later transformation, generator, or reviewer has to invent an assumption.

### When it applies

The rule is considered only when the guard below is true. A false guard is intentional: it means this invariant is not relevant to the current variant or modeling situation.

```evl
guard : self.personalData.isTrue() or self.sensitiveData.isTrue() or self.secretValue.isTrue()
```

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : not self.defaultValue.hasText() and not self.example.hasText()
```

This rule reads: `personalData`, `sensitiveData`, `secretValue`, `defaultValue`, `example`, `displayName`.

### Diagnostic and repair

> [PIM-SCHEMA-009] Sensitive field ' ' has example/default data. Suggested fix: remove real-looking examples/defaults or replace them with clearly synthetic, non-sensitive placeholders.

**How to fix it:**

This is advisory rather than a structural blocker. Decide whether the modeled situation genuinely needs the recommendation; if it does, change the referenced model features so the check evaluates to true and record the rationale when the omission is intentional.

A critique does not necessarily make the model invalid. It is a deliberate quality/readiness signal; disposition it by improving the model, documenting why it is acceptable, or carrying the decision into the review record.

---

## `SchemaConstraintHasExpressionAndMessage`

**Context:** `PIM!SchemaConstraint`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/pim/rules/contracts.evl:115`

### Why this rule exists

Checks that schema constraint has expression and message. The schema constraint element owns the evidence for this decision, including expression language, expression, message, display name. At this level, the boundaries between functions, APIs, events, and messages remain typed and compatible during transformation; allowing the model through without that evidence would move an unresolved choice into generated infrastructure. The concrete failure this rule prevents is: [PIM-SCHEMA-010] SchemaConstraint ' ' is incomplete.

### When it applies

The rule has no guard, so it applies to every instance of this context in the validated model.

There is no guard expression; every instance of the context is checked.

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.expressionLanguage.hasText() and self.expression.hasText() and self.`message`.hasText()
```

This rule reads: `expressionLanguage`, `expression`, `message`, `displayName`.

### Diagnostic and repair

> [PIM-SCHEMA-010] SchemaConstraint ' ' is incomplete. Fix: provide expressionLanguage, expression and a user-facing message.

**How to fix it:**

provide expressionLanguage, expression and a user-facing message.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---
