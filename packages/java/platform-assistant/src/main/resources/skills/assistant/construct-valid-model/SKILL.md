---
name: construct-valid-model
description: Construct structurally conformant CIM and PIM model checkpoints from exact Ecore-derived contracts. Use when creating, updating, connecting, or deleting model elements through the modeling assistant tools.
---

# Construct Valid Model

Treat exact contracts and inspected model facts as the only construction authority.

- Create only `creatable=true` types. Copy required attributes, enum literals, ownership, containment, target types, and multiplicities exactly.
- Give every create a unique stable `clientRef`. Use that exact value for same-batch owner, source, and target references.
- Follow `OWNED_BY` construction recipes literally. Use `rootId` only with a root containment listed for the created EClass.
- Satisfy every required writable non-containment reference in the same checkpoint. Create required target objects first when necessary.
- Express requested interaction using exact writable non-containment references. When the DSML represents a relationship as an EClass, create it under its valid owner and connect its endpoints.
- Prefer a small, connected, feature-complete checkpoint over many weak fragments. Omit unsupported decoration instead of inventing types or features.
- On rejection, change only facts implicated by the structural diagnostic. Reuse accepted planning and exact contracts; do not restart or repeat an unchanged batch.

The backend is the authority for apply and persistence. The generated checkpoint is gated only by structural Ecore/EMF conformance. Never request or use EVL validation in an assistant create, repair, apply, or commit path.
