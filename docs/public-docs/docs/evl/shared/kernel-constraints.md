# Shared kernel validation — Kernel Constraints

The shared kernel rules protect invariants that every DSML relies on: stable identity, traceability, and a usable correspondence between model elements. They run in each level's entry profile, so a model that is meaningful at CIM is still required to remain addressable and traceable after refinement.

Source profile: `mde/validation/shared/kernel-constraints.evl`.

## Reading these rules

Each entry preserves the actual EVL guard and check. Read the guard as the applicability boundary, not as part of the invariant: when it is false, the rule is intentionally skipped. The diagnostic is the runtime-facing message emitted by EVL; its final sentence usually contains the repository's recommended repair.

---

## `ModelElementIdIsPresentAndUnique`

**Context:** `KERNEL!ModelElement`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/shared/kernel-constraints.evl:4`

### Why this rule exists

Identity is the join key for persistence, references, trace links, and incremental transformation. If an element can be loaded without a stable unique ID, the model may still look correct in memory while its relationships and provenance become ambiguous after save/reload.

### When it applies

The rule has no guard, so it applies to every instance of this context in the validated model.

There is no guard expression; every instance of the context is checked.

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

Checks that trace link has reference or external id. The trace link element owns the evidence for this decision, including source, target, source element id, target element id, external id. At this level, identity and traceability must survive save/reload and every refinement step; allowing the model through without that evidence would move an unresolved choice into generated infrastructure. The concrete failure this rule prevents is: TraceLink ' ' must reference a source/target element or external trace identifier.

### When it applies

The rule has no guard, so it applies to every instance of this context in the validated model.

There is no guard expression; every instance of the context is checked.

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
