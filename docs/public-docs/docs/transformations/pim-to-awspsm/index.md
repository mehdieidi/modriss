# PIM → AWS PSM transformation rules

PIM→AWS PSM refinement is the provider-binding bridge from portable architecture to deployable AWS design.

Follow the concern pages below. Each page documents every ETL rule in its source module, including guards, target types, secondary objects, trace identifiers, manual decisions, and repair guidance.

| Concern                   | Rules | Page                                                      |
| ------------------------- | ----: | --------------------------------------------------------- |
| Compute API               |     6 | [Compute API](compute-api.md)                             |
| Contracts External Policy |     6 | [Contracts External Policy](contracts-external-policy.md) |
| Data Messaging Events     |     9 | [Data Messaging Events](data-messaging-events.md)         |
| Root Stage Stack          |     4 | [Root Stage Stack](root-stage-stack.md)                   |
| Workflow Security Config  |     7 | [Workflow Security Config](workflow-security-config.md)   |

## How to investigate a missing target

Start with the source element and the guard. Then check whether an earlier rule should have created the correspondence used by this rule, whether a post phase is responsible for late materialization, and whether the trace/readiness model contains a manual decision. A missing target is often an intentional unsupported/provider-choice hand-off, not necessarily a parser error.
