# ADR 0002: Modeling Agent Non-Negotiables

Status: updated

Date: 2026-07-04

Current implementation note: rule 2 is implemented as backend-validated agent tool calls. Turn
state is persisted in `assistant_turns` and related checkpoint/event/provenance tables.

## Context

Autonomous modeling turns are high risk: they can corrupt persisted models, leak private reasoning,
or apply partial changes after timeouts or concurrent edits.

## Decision

The assistant must obey these rules:

1. **Ecore is canonical.** Metamodel contracts come from combined Ecore resources; UI metadata and
   examples may enrich prompts but cannot override structural contracts.
2. **Backend tools are the only mutation path** exposed to providers; direct JSON/database mutation
   is not allowed.
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

- Turn execution is persisted in `assistant_turns` with durable events, checkpoints, provenance,
  provider-call records, diagnostics, and terminal outcomes.
- Frontend cancel, event replay, checkpoint, confirmation, continue, and undo controls are
  first-class UX requirements.
- Repair and follow-up work are bounded by provider-call, step, timeout, and durable-turn limits.
