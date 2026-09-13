# Shared kernel validation: Kernel Constraints

The shared kernel rules protect invariants that every DSML relies on: stable identity, traceability, and a usable correspondence between model elements. They run in each level's entry profile, so a model that is meaningful at CIM is still required to remain addressable and traceable after refinement.

Source profile: `mde/validation/shared/kernel-constraints.evl`.

## Reading these rules

Each entry preserves the actual EVL guard and check. Treat the guard as the applicability boundary. When it evaluates to false, EVL skips the rule. The diagnostic is the message emitted at runtime, and its final sentence usually gives the repository's recommended repair.

---

## `ModelElementIdIsPresentAndUnique`

**Context:** `KERNEL!ModelElement`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/shared/kernel-constraints.evl:4`

### Why this rule exists

Identity is the join key for persistence, references, trace links, and incremental transformation. If an element can be loaded without a stable unique ID, the model may still look correct in memory while its relationships and provenance become ambiguous after save/reload.

### When it applies

No guard is defined, so the check runs for every instance of this context in the validated model.

No guard expression is present. Every instance of the context is checked.

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.id.hasText() and KERNEL!ModelElement.all.select(element | element.id = self.id).size() = 1
```

This rule reads: `id`, `labelText`.

### Diagnostic and repair

> Model element ' ' must have a unique, immutable id.

**How to fix it:**

Make the model satisfy the check shown above. Start with the referenced features, then re-run the appropriate EVL profile; do not silence the diagnostic by changing an unrelated display field.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---

## `TraceLinkHasReferenceOrExternalId`

**Context:** `KERNEL!TraceLink`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/shared/kernel-constraints.evl:13`

### Why this rule exists

The rule checks whether trace link has reference or external id. The trace link element provides the relevant evidence through source, target, source element id, target element id, external id. At this level, identity and traceability must survive save/reload and every refinement step. Missing evidence would leave an unresolved choice in generated infrastructure. The rule prevents the following failure: TraceLink ' ' must reference a source/target element or external trace identifier.

### When it applies

No guard is defined, so the check runs for every instance of this context in the validated model.

No guard expression is present. Every instance of the context is checked.

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.source.isDefined() or self.target.isDefined() or self.sourceElementId.hasText() or self.targetElementId.hasText() or self.externalId.hasText()
```

This rule reads: `source`, `target`, `sourceElementId`, `targetElementId`, `externalId`, `labelText`.

### Diagnostic and repair

> TraceLink ' ' must reference a source/target element or external trace identifier.

**How to fix it:**

Make the model satisfy the check shown above. Start with the referenced features, then re-run the appropriate EVL profile; do not silence the diagnostic by changing an unrelated display field.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---
