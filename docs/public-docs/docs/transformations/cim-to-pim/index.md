# CIM → PIM transformation rules

CIM→PIM refinement is the semantic bridge from business/domain language to provider-independent serverless architecture.

Follow the concern pages below. Each page documents every ETL rule in its source module, including guards, target types, secondary objects, trace identifiers, manual decisions, and repair guidance.

| Concern                | Rules | Page                                                |
| ---------------------- | ----: | --------------------------------------------------- |
| Behavior Contracts     |     7 | [Behavior Contracts](behavior-contracts.md)         |
| Boundaries Security    |     5 | [Boundaries Security](boundaries-security.md)       |
| Domain Data            |     7 | [Domain Data](domain-data.md)                       |
| Integration Deployment |     2 | [Integration Deployment](integration-deployment.md) |
| Process Policy         |    20 | [Process Policy](process-policy.md)                 |
| Root Scaffolding       |     7 | [Root Scaffolding](root-scaffolding.md)             |

## How to investigate a missing target

Start with the source element and the guard. Then check whether an earlier rule should have created the correspondence used by this rule, whether a post phase is responsible for late materialization, and whether the trace/readiness model contains a manual decision. A missing target is often an intentional unsupported/provider-choice hand-off, not necessarily a parser error.
