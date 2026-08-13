# ADR 0001: Superseded Unified Delta Agent

Status: superseded by unified adaptive durable runtime

Date: 2026-07-04

## Context

The assistant previously used a provider-facing delta contract after an earlier cleanup of multiple
mutation strategies. That design is no longer current.

## Decision

The current implementation uses one durable runtime with a strict LLM strategy decision. It can run
`ConceptualInstanceModelWorkflow` or `AgentTurnLoop` plus backend-validated `AgentModelTools` over a
`ModelWorkspace`. Durable turns, checkpoints, provenance, and authenticated SSE event replay
replace the old delta/proposal pipeline.

## Consequences

- Configuration and docs expose one modeling mode only.
- Provider output is constrained either to the obligation-ledger/type/blueprint/private-slice
  conceptual protocol or allowlisted agent actions. Both compile to the same checked command and
  workspace boundary.
- Turn execution is persisted in durable assistant tables rather than proposal-specific state.
