---
name: plan-model-edit
description: Plan coherent CIM or PIM creation and editing work before mutation. Use for new models, broad feature requests, architecture changes, and multi-checkpoint work that must select exact EClasses and preserve requirement coverage.
---

# Plan Model Edit

Translate the user's requested outcomes into model responsibilities before choosing elements.

1. Read the authoritative model inventory and distinguish reusable content from missing content.
2. List every requested capability or feature explicitly. Do not silently narrow broad requests.
3. Map each responsibility to concrete, case-sensitive EClass candidates from the supplied type index. Never use conceptual category names as contracts.
4. Put foundational owners and endpoints before their dependent contracts, flows, policies, and details.
5. Group work into the fewest coherent checkpoints that fit the stated operation caps. A checkpoint must produce a useful connected model, not an arbitrary collection of nodes.
6. Put existing names that may be reused in `reuseTargets`; do not plan duplicate elements merely because their IDs are not yet known.
7. Set `turnComplete` only after every listed feature is represented or deliberately reported as remaining work.

For an empty model, plan root-contained architecture aggregates first. Do not ask the user for owner IDs that the batch can create or obtain from `rootId`.
