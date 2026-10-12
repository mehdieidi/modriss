# Architecture-pattern demonstrations

This collection contains **57 examples and 150 XMI files**: CIM/PIM for every example and AWS PSM for the 36 supported/conditional structures.

Dedicated samples cover the pattern index, direct/conditional/outside assessments, topology matrix and transaction matrix. See [coverage](coverage.json) for every source topic and its samples.

Import each XMI into its matching tab in a fresh project. Supported/conditional samples have an authored AWS PSM for the mapped structure; outside-path samples stop at CIM/PIM with a blocking scope decision. None certifies implemented runtime guarantees.

| Sample                                                             | Classification | Models and walkthrough                        |
| ------------------------------------------------------------------ | -------------- | --------------------------------------------- |
| A01 HTTP API and function handler                                  | structural     | [Open](arch-http/README.md)                   |
| A02 CRUD API scaffolding                                           | structural     | [Open](arch-crud/README.md)                   |
| A03 Queue-based asynchronous worker                                | structural     | [Open](arch-queue/README.md)                  |
| A04 Publish/subscribe and event routing                            | structural     | [Open](arch-pubsub/README.md)                 |
| A05 Event bus / pub-sub mesh                                       | conditional    | [Open](arch-event-bus/README.md)              |
| A06 Object-storage event processing                                | structural     | [Open](arch-objects/README.md)                |
| A07 Scheduled function and finite background job                   | structural     | [Open](arch-schedule/README.md)               |
| A08 Sequential workflow orchestration                              | structural     | [Open](arch-workflow/README.md)               |
| A09 Managed key/document data store                                | structural     | [Open](arch-key-store/README.md)              |
| A10 Managed document store                                         | structural     | [Open](arch-document-store/README.md)         |
| A11 Authentication and route authorization                         | conditional    | [Open](arch-auth/README.md)                   |
| A12 Cache-aside / distributed cache                                | conditional    | [Open](arch-cache/README.md)                  |
| A13 Circuit breaker around external dependencies                   | conditional    | [Open](arch-breaker/README.md)                |
| A14 Scatter-gather                                                 | conditional    | [Open](arch-scatter/README.md)                |
| A15 Bulkhead isolation                                             | conditional    | [Open](arch-bulkhead/README.md)               |
| A16 Bounded-context decomposition and microservices                | conditional    | [Open](arch-services/README.md)               |
| A17 Microservices with database per service                        | conditional    | [Open](arch-database-per-service/README.md)   |
| A18 Command/query separation and CQRS                              | conditional    | [Open](arch-cqrs/README.md)                   |
| A19 Event-driven choreography                                      | conditional    | [Open](arch-choreography/README.md)           |
| A20 Saga orchestration and compensation                            | conditional    | [Open](arch-saga/README.md)                   |
| A21 Idempotent consumer and retry                                  | conditional    | [Open](arch-idempotency/README.md)            |
| A22 API composition and backend for frontend                       | conditional    | [Open](arch-bff/README.md)                    |
| A23 Strangler migration and legacy integration                     | conditional    | [Open](arch-strangler/README.md)              |
| A24 DynamoDB stream consumer and limited CDC                       | conditional    | [Open](arch-cdc/README.md)                    |
| A25 Transactional outbox                                           | outside        | [Open](arch-outbox/README.md)                 |
| A26 Event sourcing                                                 | outside        | [Open](arch-event-sourcing/README.md)         |
| A27 GraphQL essential interface                                    | outside        | [Open](arch-graphql/README.md)                |
| A28 WebSocket essential interface                                  | outside        | [Open](arch-websocket/README.md)              |
| A29 Webhook-first essential interface                              | outside        | [Open](arch-webhook/README.md)                |
| A30 Relational primary persistence                                 | outside        | [Open](arch-relational/README.md)             |
| A31 Graph primary persistence                                      | outside        | [Open](arch-graph/README.md)                  |
| A32 Search primary persistence                                     | outside        | [Open](arch-search/README.md)                 |
| A33 Time-series primary persistence                                | outside        | [Open](arch-time-series/README.md)            |
| A34 Cross-service XA/2PC transaction                               | outside        | [Open](arch-xa/README.md)                     |
| A35 Exactly-once business outcome across service boundaries        | outside        | [Open](arch-exactly-once/README.md)           |
| A36 Global ordering across independent services/regions            | outside        | [Open](arch-global-order/README.md)           |
| A37 Full Parallel workflow                                         | outside        | [Open](arch-parallel/README.md)               |
| A38 Full Map workflow                                              | outside        | [Open](arch-map/README.md)                    |
| A39 Callback workflow                                              | outside        | [Open](arch-callback/README.md)               |
| A40 Offline-first edge/client architecture                         | outside        | [Open](arch-offline/README.md)                |
| A41 Non-AWS and non-serverless deployment patterns                 | outside        | [Open](arch-non-aws/README.md)                |
| A42 Traditional modular monolith                                   | outside        | [Open](arch-monolith/README.md)               |
| A43 Hexagonal/clean architecture inside application code           | conditional    | [Open](arch-hexagonal/README.md)              |
| A44 Shared database across many services                           | conditional    | [Open](arch-shared-database/README.md)        |
| A45 DynamoDB access-pattern-first key/index design                 | structural     | [Open](arch-indexes/README.md)                |
| A46 API throttling and function concurrency isolation              | conditional    | [Open](arch-throttling/README.md)             |
| A47 Read replica or separate read database                         | conditional    | [Open](arch-read-replica/README.md)           |
| A48 Continuous event-stream processing / analytics pipeline        | outside        | [Open](arch-stream-analytics/README.md)       |
| A49 Hybrid integration with external systems                       | conditional    | [Open](arch-hybrid/README.md)                 |
| A50 Multi-region active/passive recovery                           | outside        | [Open](arch-passive-recovery/README.md)       |
| A51 Multi-region active/active or multi-cloud                      | outside        | [Open](arch-active-active/README.md)          |
| A52 Tenant-sharded SaaS / cell-based architecture                  | conditional    | [Open](arch-cells/README.md)                  |
| A53 Atomic single-item conditional update / optimistic concurrency | conditional    | [Open](arch-optimistic/README.md)             |
| A54 Multi-item transaction inside one DynamoDB service boundary    | conditional    | [Open](arch-local-transaction/README.md)      |
| A55 Eventual consistency between service-owned records             | conditional    | [Open](arch-eventual/README.md)               |
| A56 One service containing multiple bounded functions/modules      | structural     | [Open](arch-modules/README.md)                |
| A57 Security and observability patterns beyond infrastructure      | conditional    | [Open](arch-security-observability/README.md) |

The compound catalogue entries are expanded into separate cache, breaker, scatter-gather, bulkhead, API-style, storage, workflow and distributed-correctness samples. An unsupported pattern remains unsupported even though its design model imports successfully.

[Verification report](verification.json) records structural conformance, backend round trips, headless frontend view/save checks and available transformation/generation paths. Frontend checks use real workbench modules, not a live-browser upload. Outside examples only run CIM→PIM draft derivation; no complete PIM→AWS/artifact claim is made.

Pipeline verification uses the underlying ETL/EGX runners. The formal workbench workflow performs explicit source validation and may require resolution of the open architecture decisions; that UI gate is not certified by these structural checks.

Assistant-generated model verification is structural Ecore/EMF only via `ModelService.validateStructural(...)`; EVL remains a separate explicit user/model workflow.

Regenerate: `python scripts/build-architecture-demo-models.py`

Verify both collections: `python scripts/verify-scope-demo-models.py --record`
