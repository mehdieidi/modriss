# Architecture Decision Records

This directory captures significant architectural decisions for Modless. ADRs are immutable
historical records: when a decision changes, add a new ADR that supersedes the previous one.

## Index

| ADR                                                  | Title                                      | Status   |
| ---------------------------------------------------- | ------------------------------------------ | -------- |
| [0001](0001-cim-pim-psm-separation.md)               | CIM/PIM/PSM modeling separation            | Accepted |
| [0002](0002-semi-automated-transformations.md)       | Semi-automated MDE transformations         | Accepted |
| [0003](0003-spring-ai-guarded-apply.md)              | Spring AI with guarded apply               | Accepted |
| [0004](0004-postgresql-pgvector-assistant-memory.md) | PostgreSQL + pgvector for assistant memory | Accepted |

## When to write an ADR

Create a new ADR when changing:

- Core technology choices (database, MDE engine, AI provider strategy)
- Security or trust boundaries
- API contracts that affect multiple clients
- Persistence or deployment topology

Follow the template in any existing ADR. Number sequentially (`0005-...`).
