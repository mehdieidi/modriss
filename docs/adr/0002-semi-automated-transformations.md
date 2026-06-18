# ADR 0002: Semi-Automated MDE Transformations

- **Status:** Accepted
- **Date:** 2026-03-19
- **Deciders:** Modless core team

## Context

Fully automatic model transformations are fast but opaque. Fully manual transformations preserve
human judgment but defeat the purpose of a low-code platform. Modless must support thesis-quality
traceability while remaining practical for iterative modeling.

## Decision

Implement **semi-automated** transformations using Eclipse Epsilon (ETL/EOL/EGX):

- Transformations run as asynchronous MDE jobs with diagnostics, captured output, and timeouts
- Users trigger CIM→PIM, PIM→PSM, and PSM→artifact generation explicitly from the UI
- Validation (EVL) gates execution; failures return structured reports instead of partial writes
- Optimistic concurrency (`expectedRevision`) prevents silent overwrites during review cycles

## Consequences

### Positive

- Human review remains in the loop for each pipeline stage
- Job records provide auditability and troubleshooting data
- The same Epsilon toolchain runs in CLIs, backend jobs, and regression tests

### Negative

- More user actions than a fully automatic pipeline
- Job queue limits and timeouts must be tuned per deployment
- Long-running transformations require observability (metrics, logs, tracing)

## Alternatives considered

- **Synchronous in-request transformations** — rejected due to timeout risk and poor UX for large models
- **Fully manual copy/paste between levels** — rejected as incompatible with low-code goals
