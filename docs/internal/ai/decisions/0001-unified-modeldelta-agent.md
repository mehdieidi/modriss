# ADR 0001: Unified ModelDelta Modeling Agent

Status: accepted

Date: 2026-07-04

## Context

The assistant previously exposed two provider-facing mutation protocols (`semantic-patch` and
`model-subset`) behind `MODLESS_AI_MODELING_STRATEGY`. Reliability, repair logic, tests, and
documentation diverged across those modes.

## Decision

- Replace both legacy mutation protocols with one provider-facing contract: `ModelDelta`.
- Lower `ModelDelta` to an internal executable patch IR (`SemanticModelPatch` / JSON Pointer patch)
  inside the backend only.
- Route all mutation turns through `ModelingAgent`, `DeltaCompiler`, `DeltaNormalizer`, and
  `StructuralValidationGate`.
- Use `IntentPlanner` structured output for turn intent; do not route modeling behavior with
  English keywords or regex intent classifiers.

## Consequences

- Configuration and docs expose one modeling mode only.
- Evals and live tests assert the `ModelDelta` path exclusively.
- Legacy materializer and heading-parser source shortcuts are removed from the turn pipeline.
