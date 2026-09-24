# Criteria-Based Evaluation of the MODRISS Development Process

## Evaluation protocol

This evaluation uses the complete criterion set published by Eidi and Ramsin.
It follows their three-level scale:

- **A — full coverage:** explicit activities, roles, work products, guidance,
  and decision/evidence rules cover the criterion;
- **B — partial coverage:** the concern is present but restricted in scope,
  weakly automated, or not yet supported end to end; and
- **C — no coverage:** no substantive support exists.

Two ratings are intentionally separated:

1. **Process design** evaluates the lifecycle process specified in this package.
2. **Repository realization** evaluates concrete support in the current
   MODRISS repository, including metamodels, EVL, ETL, EGL/EGX, process JSON,
   generated artifacts, and tools.

Without this separation, a document could score itself “A” for promising a
practice the platform cannot yet perform. Conversely, a repository feature
without roles, context, or lifecycle guidance would be mistaken for process
support.

The criterion set satisfies the Ramsin–Paige/Karam–Casselman meta-criteria:
it is general across candidates, precise enough to distinguish support,
comprehensive across lifecycle/MDD/serverless concerns, and balanced across
technical, managerial, and usage concerns. Ratings are design-analysis results,
not empirical proof of effectiveness.

## General software-development criteria

| Criterion                  |                             Design                              | Realization | Evidence and limitation                                                                                                                                                    |
| -------------------------- | :-------------------------------------------------------------: | :---------: | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Requirements Engineering   |                                A                                |      A      | Phase 0 and CIM formalize stakeholders, functional/NFR concerns, priorities, quality scenarios, and acceptance criteria.                                                   |
| Analysis                   |                                A                                |      A      | Opportunity, domain, information, behavior, policy, risk, suitability, and cost analysis precede provider commitment.                                                      |
| Design                     |                                A                                |      A      | PIM and PSM provide architecture and detailed provider design with decision records.                                                                                       |
| Implementation             |                                A                                |      A      | Generation plus explicit completion of business logic, adapters, clients, and protected regions.                                                                           |
| Test                       |                                A                                |      A      | Test-in-the-small and large cover model, contract, workflow, integration, NFR, security, recovery, and acceptance scopes.                                                  |
| Deployment                 |                                A                                |      A      | Immutable candidate, CI/CD, progressive promotion, rollback, post-deployment checks, and handover.                                                                         |
| Maintenance                |                                A                                |      A      | Operations, maintenance categories, interrupt-driven pull/WIP/SLE control, incidents, emergency reconciliation, controlled change, learning, and retirement are explicit.  |
| Project Management         |                                A                                |      B      | Planned release/increment control, operational capacity/preemption, dependencies, risks, evidence, and progress are defined; a persisted integrated tracker is incomplete. |
| Quality Assurance          |                                A                                |      A      | Continuous reviews, model validation, tests, trace, findings, gates, and independent review in higher-risk profiles.                                                       |
| Risk Management            |                                A                                |      A      | Initial and continuous product, MDE, serverless, security, cost, release, and operational risk work.                                                                       |
| Reusability                |                                A                                |      A      | Generalization/repository fragment plus reusable models, transforms, templates, tests, and method content.                                                                 |
| User Involvement Support   |                                A                                |      B      | Product/user/domain participation is mandatory in discovery and acceptance; the platform cannot enforce attendance or representation quality.                              |
| Adaptability               |                                A                                |      B      | Situational factors, profiles, configuration packages, substitutions, and review triggers are explicit; automated configuration tooling is limited.                        |
| Completeness of Definition |                                A                                |      A      | Process-centred lifecycle, role/product views, fragment catalog, gates, work products, tailoring, evaluation, trace, and SPEM structure are supplied.                      |
| Definition Type            |     Process-oriented with complementary product/role views      |    Same     | The lifecycle is primary; role and work-product views cross-reference it.                                                                                                  |
| Traceability               |                                A                                |      A      | Outcome/requirement→CIM→PIM→PSM→artifact→test/release/runtime/change trace is required; transformation and artifact traces are implemented.                                |
| Based on Requirements      |                                A                                |      A      | Requirements and acceptance evidence govern modeling, design, tests, release, and operational outcomes.                                                                    |
| Complexity Management      |                                A                                |      A      | Abstraction levels, vertical slices, bounded ownership, packages/views, explicit dependencies, and configuration profiles.                                                 |
| Methodology Type           | Model-driven methodology with an integrated development process |    Same     | Models are primary engineering artifacts. The development process incorporates agile, DevOps/SRE, risk, and governance practices.                                          |
| Application Scope          |  Domain-specific: serverless/event-driven information systems   |    Same     | Hybrid and external systems are supported; safety-critical/hard real-time systems require extensions.                                                                      |

## MDD-related criteria

| Criterion                                 | Design | Realization | Evidence and limitation                                                                                                                                               |
| ----------------------------------------- | :----: | :---------: | --------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Definition of Modeling Boundaries         |   A    |      A      | CIM, PIM, PSM, generated artifact, release, and operational boundaries are explicit.                                                                                  |
| CIM Creation                              |   A    |      A      | Implemented CIM DSML and 26-task process component.                                                                                                                   |
| PIM Creation                              |   A    |      A      | Implemented PIM DSML and 32-task process component.                                                                                                                   |
| PSM Creation                              |   A    |      A      | Implemented AWS PSM DSML and 28-task process component.                                                                                                               |
| Code Generation                           |   A    |      A      | EGX/EGL creates infrastructure, handlers, contracts, tests, pipelines, scripts, docs, and trace.                                                                      |
| CIM to PIM                                |   A    |      A      | Modular ETL transformation with trace/readiness and deterministic identity.                                                                                           |
| PIM to PSM                                |   A    |      A      | Modular PIM→AWS PSM ETL with explicit mapping/manual decisions.                                                                                                       |
| PSM to Code                               |   A    |      A      | Model-to-text generation is an explicit lifecycle component.                                                                                                          |
| Round-Trip Engineering                    |   B    |      B      | Three-way generated/working/baseline reconciliation preserves edits and detects conflicts, but there is no general reverse PSM→PIM→CIM transformation.                |
| Source-Target Model Synchronization       |   A    |      A      | Stable IDs, baselines, three-way merge, conflicts, and change routing are defined and implemented.                                                                    |
| Degree of Code Automation                 |   A    |      B      | Broad artifact automation exists; business logic and explicit manual actions still require completion by design.                                                      |
| Structural, Functional, Behavioral Models |   A    |      A      | Metamodels cover structure, data, behavior, workflow, policy, security, deployment, and operations intent.                                                            |
| Logical Model Quality                     |   A    |      A      | Separation of concerns, abstraction boundaries, constraints, trace/readiness, and reviews are explicit.                                                               |
| Documentation and Guidelines              |   A    |      A      | Detailed generated/user guides plus this research, method, tailoring, and formal package.                                                                             |
| Process Familiarity                       |   B    |      B      | Lifecycle and agile/DevOps patterns are familiar, but three DSMLs, transformation reconciliation, and SPEM increase learning effort.                                  |
| Informational Feedback                    |   A    |      A      | Structural/semantic validation, transformation conflicts/reports, readiness/manual decisions, generation reports, tests, and runtime telemetry.                       |
| Coverage of Metamodels in Domain          |   A    |      A      | Existing coverage matrices bind all CIM/PIM/PSM classifiers to explicit tasks; domain sufficiency still needs case-study validation.                                  |
| All-Levels Transformations                |   A    |      A      | Supported forward chain spans CIM→PIM→AWS PSM→artifacts.                                                                                                              |
| Model Evaluation                          |   A    |      A      | Structural conformance, explicit EVL semantic validation, reviews, trace, and readiness are distinguished.                                                            |
| Metadata Management                       |   A    |      A      | IDs, revisions, provenance, transformation metadata, trace, readiness, manifests, and process evidence are defined.                                                   |
| Automated Testing                         |   A    |      B      | Test templates and pipelines exist; complete automated testing of transformations, generated variants, and all serverless risk conditions is partial.                 |
| MDD Traceability                          |   A    |      A      | Model and artifact trace concepts plus task/work-product evidence across levels.                                                                                      |
| Standard Definitions Compliance           |   B    |      B      | Ecore/EMF and Epsilon technologies are used; process is SPEM-mapped/logically represented, but native vendor-neutral SPEM XMI import compliance is not yet certified. |
| Model-Based Testing                       |   A    |      B      | Contracts/workflows/policies generate test baselines and the process requires traceability; full systematic test derivation and coverage measurement remain partial.  |

## Serverless-related criteria

| Criterion                          | Design | Realization | Evidence and limitation                                                                                                                          |
| ---------------------------------- | :----: | :---------: | ------------------------------------------------------------------------------------------------------------------------------------------------ |
| Serverless Suitability Analysis    |   A    |      B      | Mandatory feasibility fragment and decision record; no integrated quantitative decision tool yet.                                                |
| Serverless Cost Analysis           |   A    |      B      | Cost model, guardrails, release forecast, and runtime feedback defined; automated model-to-cost estimation is incomplete.                        |
| Serverless Risk Analysis           |   A    |      B      | Comprehensive risk task and register; automated risk inference from models is partial.                                                           |
| Cloud Provider Selection           |   A    |      B      | Explicit capability/cost/fit/portability decision before PSM; only AWS PSM is implemented.                                                       |
| Event-Driven Requirements Capture  |   A    |      A      | CIM events, commands, queries, processes, policies, actors, and acceptance links.                                                                |
| Fine-Grained NFR Capture           |   A    |      A      | CIM quality scenarios and governance trace into PIM policies/SLOs and PSM controls.                                                              |
| Event Modeling                     |   A    |      A      | Events, envelopes, channels/buses, triggers, routing, EventBridge, schedules, pipes, and contracts.                                              |
| Function Granularity Determination |   A    |      B      | Domain/risk/coupling/scaling guidance and decisions are explicit; automated recommendation is absent.                                            |
| Domain-Driven Design               |   A    |      A      | Ubiquitous language, entities/value objects, aggregates, bounded contexts, commands/queries/events.                                              |
| Workflow Modeling                  |   A    |      A      | CIM processes, PIM workflow/state types, PSM Step Functions mapping, and ASL generation.                                                         |
| Choreography Modeling              |   A    |      B      | Event topology and decentralized flows are modeled; choreography-specific global consistency analysis is limited.                                |
| Configuration Modeling             |   A    |      A      | PIM configuration/environments/deployment units and PSM parameters/stages/resources.                                                             |
| Data Pipeline Modeling             |   A    |      A      | Flows, events, queues/topics/buses, pipes, data access, streams/change notifications.                                                            |
| State Management Modeling          |   A    |      A      | Data stores/models, workflow state, consistency/access/recovery policies, and provider storage.                                                  |
| Event Contracts                    |   A    |      A      | Schemas, event types/envelopes, compatibility/ownership guidance, and generated schemas.                                                         |
| Multi-Cloud Modeling               |   B    |      B      | CIM/PIM are provider-independent and configuration package exists; only AWS PSM/transformation/generator is realized.                            |
| Fault Modeling                     |   A    |      A      | Timeouts, retry/backoff, DLQ, compensation, workflow failure, idempotency, and operational response.                                             |
| External Service Integration       |   A    |      A      | External systems/adapters/endpoints, contracts, auth, rate/failure/ownership guidance, and provider integrations.                                |
| Serverless Patterns Support        |   A    |      B      | Method mandates pattern decisions and PIM/PSM express many patterns; there is no complete curated pattern catalog with automated selection.      |
| Reusable Function Design           |   A    |      B      | Generalization and reuse rules exist; organizational function catalog/governance tooling is partial.                                             |
| Fine-Grained Access Configuration  |   A    |      A      | PIM principals/permissions and PSM IAM/KMS/resource-policy refinement with least-privilege review.                                               |
| Secrets Management                 |   A    |      A      | PIM security/config intent and PSM Secrets Manager/SSM/KMS plus release/operations guidance.                                                     |
| Workflow Generation                |   A    |      A      | PIM workflow maps to concrete ASL states and generated definitions with blockers for unsafe placeholders.                                        |
| Deployment Artifact Generation     |   A    |      A      | SAM/CloudFormation and supporting configuration are generated from AWS PSM.                                                                      |
| Workflow Testing                   |   A    |      A      | Generated workflow, integration, event, and end-to-end test baselines plus explicit non-production exercise.                                     |
| Serverless-Specific Testing        |   A    |      B      | Cold-start, concurrency, retry/DLQ, permission, quota, recovery, and resilience testing are required; automation breadth varies.                 |
| CI/CD Support                      |   A    |      A      | Generated validation/deployment workflows and full candidate/promotion control flow.                                                             |
| Deployment Strategies              |   A    |      B      | Canary/weighted alias/blue-green/feature-control guidance is explicit; generator automation for every strategy is incomplete.                    |
| Function Versioning                |   A    |      B      | Release process requires versions/aliases and immutable candidates; complete automatic lifecycle management is partial.                          |
| Observability—Distributed Tracing  |   A    |      A      | PIM observability intent, PSM observability resources, shared tracer generation, and runtime evidence.                                           |
| Observability—Logging              |   A    |      A      | Structured logging, log configuration/retention, dashboards/alerts, and generated helpers.                                                       |
| Feedback Loop                      |   A    |      B      | Operations-to-backlog/model/method routing is explicit; automatic telemetry-to-model feedback is not implemented.                                |
| Cold-Start Mitigation              |   A    |      B      | Situational analysis, experiment, capacity/architecture decision, monitoring, and test required; no universal automated optimizer.               |
| Vendor Lock-In Mitigation          |   A    |      B      | Provider-independent PIM, portability package, adapter/exit guidance, and source-level change routing; AWS-only PSM limits realized portability. |

## Dual-flow lifecycle stress test

The corrected design was walked through four production-demand scenarios: an
availability incident requiring immediate restoration, a dependency
vulnerability, a provider deprecation with a known date, and a perfective cost
improvement. In each case the method now provides (1) a visible WP-30 item,
(2) independent maintenance-purpose, emergency-status, and service-class decisions, (3) pull/WIP/SLE
control through WP-31, (4) an explicit disposition to operations-only work,
bounded MDE/release change, or planned backlog, and (5) authoritative-source
reconciliation after any temporary emergency modification. This is structural
scenario evidence, not empirical proof of effectiveness; the measures in the
validation protocol must still be collected in real enactments.

## Summary and interpretation

The process design achieves full coverage for most criteria because previously
weak concerns—especially exploration, management, release, operations,
feedback, cost, cold start, lock-in, and retirement—are explicit method
content. The repository realization is strongest in modeling, forward
transformations, trace/readiness, AWS resource design, and generation. It is
partial in the areas that require organizational systems or additional
automation: integrated process-run tracking, quantitative cost analysis,
reverse transformation, multi-cloud PSMs, comprehensive deployment-strategy
automation, model-derived testing breadth, and telemetry-to-model automation.

No aggregate percentage is reported. Averaging would hide blocking absences and
would imply equal weights across contexts. A thesis case study should weight
criteria according to its situation, record assessor agreement, and report
criterion-level evidence.

## Residual validation work

1. Enact the process in at least one representative serverless case study and
   record deviations, effort, defects, rework, trace closure, deployment, SLO,
   cost, and practitioner feedback.
2. Ask independent reviewers to score a blinded evidence package and calculate
   agreement before resolving rating differences.
3. Run a second case with different team scale or criticality to test
   configurability rather than only the default profile.
4. Validate the method-content granularity with practitioners: SPEM recommends
   assignable tasks of hours to days, not navigational microsteps or multi-week
   epics.
5. Test native SPEM interchange separately if tool portability is claimed.
6. Re-run the criteria-based evaluation after empirical findings; stabilize the
   criteria and requirements only when further iterations no longer produce
   significant changes.
