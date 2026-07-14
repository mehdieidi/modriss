# ADR 0001: Superseded Unified Delta Agent

Status: superseded by durable tool-loop runtime

Date: 2026-07-04

## Context

The assistant previously used a provider-facing delta contract after an earlier cleanup of multiple
mutation strategies. That design is no longer current.

## Decision

The current implementation uses `AgentTurnLoop` plus backend-validated `AgentModelTools` over
`ModelWorkspace`. Durable turns, checkpoints, provenance, and authenticated SSE event replay
replace the old delta/proposal pipeline.

## Consequences

- Configuration and docs expose one modeling mode only.
- Provider output is constrained to tool actions that the backend validates against Ecore-derived
  contracts.
- Turn execution is persisted in durable assistant tables rather than proposal-specific state.
