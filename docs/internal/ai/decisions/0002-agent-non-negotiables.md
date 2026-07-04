# ADR 0002: Modeling Agent Non-Negotiables

Status: accepted

Date: 2026-07-04

## Context

Autonomous modeling turns are high risk: they can corrupt persisted models, leak private reasoning,
or apply partial changes after timeouts or concurrent edits.

## Decision

The assistant must obey these rules:

1. **Ecore is canonical.** Metamodel contracts come from combined Ecore resources; UI metadata and
   examples may enrich prompts but cannot override structural contracts.
2. **`ModelDelta` is the only mutation protocol** exposed to providers.
3. **Structural EMF validation only** gates AI apply. EVL is not executed on the assistant apply
   path.
4. **Atomic, revision-guarded turns.** Model-changing turns acquire a short apply lock, recheck
   `expectedRevision`, and either apply one structurally valid patch or leave the persisted model
   unchanged.
5. **Idempotency and cancellation.** Duplicate `idempotencyKey` requests return the stored terminal
   outcome; canceled or timed-out turns cannot apply later.
6. **User-visible trace, not chain-of-thought.** Realtime events stream planning summaries, tool
   use, evidence coverage, validation summaries, and operation previews — never private raw
   reasoning.
7. **Source documents are untrusted data.** Prompt blocks label context roles; instruction-like
   source text is classified as evidence, not executable commands.
8. **Deletion is explicit-intent-only.**

## Consequences

- Turn execution is persisted in `assistant_turn_executions` with diagnostics and terminal outcomes.
- Frontend cancel, trace events, and draft previews are first-class UX requirements.
- Repair is bounded (deterministic normalization once, limited LLM structural repair).
