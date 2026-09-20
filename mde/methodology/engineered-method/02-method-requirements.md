# Method Requirements and Situational Factors

## Scope and target situation

The target is a **general but configurable product-development method** for
serverless software whose engineering backbone is MODRISS. It is broader than
one project-specific process, but narrower than a universal software method.
Its intended scope is event-driven and API-oriented information systems that
can be expressed by the MODRISS CIM and PIM and realized on a supported PSM;
the current implemented PSM is AWS.

The method must work for a single cross-functional team and for several teams
working on bounded slices. It must remain useful when only part of a system is
serverless or when external and legacy systems participate. Safety-critical or
hard real-time use is outside the default profile and requires a specialized
assurance extension.

## Requirements-engineering approach

The requirements below are the stabilized result of three inputs:

- the general, MDD, and serverless criteria in Eidi and Ramsin;
- qualities and practices identified by the iterative criteria-based method of
  Ramsin and Paige; and
- concrete capabilities and limitations found in the MODRISS repository.

Each requirement has a stable identifier, a required support level, and a
verification statement. `MUST` denotes core method content; `SHOULD` denotes
content selected by the default profile but tailorable with recorded rationale;
`MAY` denotes an optional extension.

## Core lifecycle requirements

| ID       | Requirement                                                                                                                                                                                         | Level | Verification                                                                                                |
| -------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ----- | ----------------------------------------------------------------------------------------------------------- |
| MR-LC-01 | Cover opportunity/problem exploration, feasibility, requirements, analysis, architecture, detailed design, implementation, verification, release, operation, maintenance/evolution, and retirement. | MUST  | Every lifecycle concern maps to at least one Activity, role, and work product.                              |
| MR-LC-02 | Separate reusable method content from its placement in a delivery process.                                                                                                                          | MUST  | SPEM library contains RoleDefinitions, TaskDefinitions, WorkProductDefinitions, Guidance, and process uses. |
| MR-LC-03 | Deliver in small vertical increments while maintaining a product-level release and service lifecycle.                                                                                               | MUST  | Increment loop, release loop, and operation/change loop are distinct and connected.                         |
| MR-LC-04 | Treat initiation, tailoring, organization, operations, and retirement as first-class work, not prose surrounding the modeling workflow.                                                             | MUST  | Each is a named phase with exit evidence.                                                                   |
| MR-LC-05 | Define entry criteria, exit criteria, accountable roles, inputs, outputs, and evidence for every gate.                                                                                              | MUST  | Gate catalog is complete and auditable.                                                                     |
| MR-LC-06 | Permit concurrency without losing dependency, revision, or ownership control.                                                                                                                       | MUST  | Team topology and dependency records identify scope owner, revision, due date, evidence, and escalation.    |
| MR-LC-07 | Maintain smooth feedback from later work to the earliest affected source instead of patching only downstream artifacts.                                                                             | MUST  | Change-routing rules cover CIM, PIM, PSM, generator, artifact, release, and operations changes.             |

## Requirements and stakeholder requirements

| ID       | Requirement                                                                                                                                                                                 | Level | Verification                                                               |
| -------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ----- | -------------------------------------------------------------------------- |
| MR-RE-01 | Capture stakeholder outcomes, functional requirements, fine-grained NFR/quality scenarios, constraints, acceptance criteria, assumptions, and priorities before committing to architecture. | MUST  | Accepted CIM increment contains each concern or an owned deferral.         |
| MR-RE-02 | Capture business events, commands, queries, policies, actors, information, and external systems as first-class problem-domain concepts.                                                     | MUST  | CIM trace shows requirements to behavior/domain elements.                  |
| MR-RE-03 | Involve product owners, domain experts, users or user representatives in discovery, reviews, and acceptance.                                                                                | MUST  | Participation and decision evidence exists at increment and release gates. |
| MR-RE-04 | Allow requirements to evolve while preserving baselines and impact analysis.                                                                                                                | MUST  | Change request links old/new revisions and affected traces.                |
| MR-RE-05 | Keep requirements as the basis for models, code, tests, release evidence, and operational measures.                                                                                         | MUST  | Bidirectional trace queries reach acceptance and runtime evidence.         |

## MDE and modeling requirements

| ID        | Requirement                                                                                                                     | Level  | Verification                                                                              |
| --------- | ------------------------------------------------------------------------------------------------------------------------------- | ------ | ----------------------------------------------------------------------------------------- |
| MR-MDE-01 | State the modeling boundary and purpose of CIM, PIM, PSM, and generated artifacts.                                              | MUST   | Level contracts prohibit misplaced platform or domain concerns.                           |
| MR-MDE-02 | Support structural, functional, behavioral, data, security, deployment, and operational viewpoints at the appropriate levels.   | MUST   | Coverage matrix maps each viewpoint to classifiers and tasks.                             |
| MR-MDE-03 | Provide defined CIM→PIM, PIM→PSM, and PSM→artifact transformations with human review of unresolved decisions.                   | MUST   | Transformation report, trace links, assumptions, and readiness decisions are gate inputs. |
| MR-MDE-04 | Preserve stable identity, provenance, and traceability across levels and generations.                                           | MUST   | Trace models record source, target, rule/profile, and revision.                           |
| MR-MDE-05 | Detect and resolve source/target inconsistencies without silently overwriting independent work.                                 | MUST   | Three-way synchronization produces applied changes or explicit conflicts.                 |
| MR-MDE-06 | Distinguish structural conformance, semantic model validation, transformation verification, and product testing.                | MUST   | Evidence records name validation kind and scope.                                          |
| MR-MDE-07 | Treat transformation output as a draft that requires responsible refinement and acceptance.                                     | MUST   | No transform completion event automatically satisfies a readiness gate.                   |
| MR-MDE-08 | Support reusable models, patterns, transformations, generators, and method fragments.                                           | SHOULD | Generalization task records candidate, quality review, version, and repository location.  |
| MR-MDE-09 | Manage model complexity by bounded slices, layers, packages/views, ownership, and explicit dependencies.                        | MUST   | Tailored method defines slice and ownership strategy.                                     |
| MR-MDE-10 | Provide timely tool and process feedback for invalid models, failed transformations, unresolved decisions, and stale revisions. | MUST   | Feedback events are actionable and attributable.                                          |
| MR-MDE-11 | Provide model-based test derivation where the DSML exposes contracts, workflows, policies, or acceptance information.           | SHOULD | Generated or manually completed tests link to modeled source.                             |
| MR-MDE-12 | State standards alignment and conformance boundaries precisely.                                                                 | MUST   | SPEM mapping and non-conformance claims are explicit.                                     |

## Serverless engineering requirements

| ID       | Requirement                                                                                                                                                                                            | Level  | Verification                                                                                                        |
| -------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ | ------ | ------------------------------------------------------------------------------------------------------------------- |
| MR-SL-01 | Decide whether serverless is suitable before provider-specific design.                                                                                                                                 | MUST   | Suitability record covers workload, latency, state, integration, compliance, skills, operations, and exit concerns. |
| MR-SL-02 | Estimate cost and identify cost uncertainty from demand, duration, memory, requests, orchestration, data movement, storage, logging, and provisioned capacity.                                         | MUST   | Cost hypothesis and budget guardrails are approved and later compared with telemetry.                               |
| MR-SL-03 | Analyze serverless-specific risks, including cold starts, throttling, concurrency, retry storms, at-least-once delivery, poison events, quotas, regional failure, observability gaps, and lock-in.     | MUST   | Risk register has owner, trigger, treatment, and evidence.                                                          |
| MR-SL-04 | Select a provider/target profile using explicit capabilities, constraints, organizational fit, cost, portability, and operational criteria.                                                            | MUST   | Provider decision record exists before PSM commitment.                                                              |
| MR-SL-05 | Derive function/service boundaries from domain capabilities, bounded contexts, cohesion, coupling, change cadence, security, scaling, and failure isolation—not file size or arbitrary function count. | MUST   | Boundary rationale is reviewed in PIM.                                                                              |
| MR-SL-06 | Model events, sources, triggers, schemas, contracts, compatibility, correlation, idempotency, ordering, and ownership.                                                                                 | MUST   | Event contract catalog and trace links pass the PIM gate.                                                           |
| MR-SL-07 | Model orchestration and choreography explicitly, including timeouts, retries, backoff, dead-letter handling, compensation, and terminal outcomes.                                                      | MUST   | Workflow/event design has success and failure paths.                                                                |
| MR-SL-08 | Model data pipelines, state, consistency, access patterns, retention, privacy, recovery, and change streams.                                                                                           | MUST   | Data architecture and recovery evidence are accepted.                                                               |
| MR-SL-09 | Model external and hybrid integrations with ownership, authentication, rate limits, failure behavior, and support boundaries.                                                                          | MUST   | Each external dependency has a contract and operational owner.                                                      |
| MR-SL-10 | Apply serverless architectural and reliability patterns deliberately and record trade-offs.                                                                                                            | SHOULD | Architecture decision record names context, chosen pattern, alternatives, and consequences.                         |
| MR-SL-11 | Define reusable functions/components only where ownership, compatibility, coupling, and independent change remain manageable.                                                                          | SHOULD | Reuse candidate passes generalization review.                                                                       |
| MR-SL-12 | Enforce least privilege, secrets isolation, encryption, supply-chain controls, threat analysis, and evidence-backed exceptions.                                                                        | MUST   | Security gate has policy, scan/review, and exception evidence.                                                      |
| MR-SL-13 | Generate and verify infrastructure, workflows, contracts, handlers, tests, documentation, and pipelines from an accepted PSM.                                                                          | MUST   | Generation manifest links every generated item to source and template version.                                      |
| MR-SL-14 | Test functions, contracts, integrations, workflows, failure paths, concurrency, cold starts, permissions, quotas, recovery, and end-to-end behavior at appropriate scopes.                             | MUST   | Test strategy and evidence cover selected risk classes.                                                             |
| MR-SL-15 | Support immutable release candidates, automated CI/CD, environment promotion, version/alias management, canary or equivalent progressive delivery, rollback, and post-deployment checks.               | MUST   | Release record identifies artifact digest, environment, strategy, thresholds, and outcome.                          |
| MR-SL-16 | Define logs, metrics, traces, correlation, dashboards, alerts, SLOs, cost signals, and feedback into product/model decisions.                                                                          | MUST   | Operational handover and outcome review are accepted by the service owner.                                          |
| MR-SL-17 | Treat cold-start mitigation and vendor-lock-in mitigation as explicit, situational decisions rather than universal promises.                                                                           | MUST   | Applicable decision record is accepted or marked not applicable with evidence.                                      |
| MR-SL-18 | Permit multi-cloud modeling at CIM/PIM while accurately marking current PSM/generator support.                                                                                                         | SHOULD | Portability claim distinguishes intent portability from behavioral/infrastructure portability.                      |

## Management and continuous-discipline requirements

| ID       | Requirement                                                                                                                                            | Level  | Verification                                                                            |
| -------- | ------------------------------------------------------------------------------------------------------------------------------------------------------ | ------ | --------------------------------------------------------------------------------------- |
| MR-MG-01 | Provide product, project/delivery, risk, quality, security/privacy, configuration/change, knowledge, and supplier management throughout the lifecycle. | MUST   | Each discipline has an owner, cadence, work products, and control points.               |
| MR-MG-02 | Plan by outcomes, increments, dependencies, risks, capacity, and evidence rather than by model element counts alone.                                   | MUST   | Integrated plan links work to outcome and gate evidence.                                |
| MR-MG-03 | Scale through bounded model ownership, cross-functional teams, integration ownership, communities of practice, and explicit dependency management.     | MUST   | Multi-team profile defines decision rights and escalation.                              |
| MR-MG-04 | Make quality assurance continuous through reviews, trace checks, model validation, test automation, release evidence, and retrospectives.              | MUST   | QA strategy spans every lifecycle phase.                                                |
| MR-MG-05 | Keep decisions, risks, findings, exceptions, and evidence versioned and auditable.                                                                     | MUST   | Records have identifier, owner, status, revision, timestamp, and disposition.           |
| MR-MG-06 | Use metrics for system and process improvement, not individual ranking.                                                                                | MUST   | Metric guidance prohibits individual performance use and defines interpretation limits. |
| MR-MG-07 | Capture lessons and generalize reusable assets after increments, releases, incidents, and retirement.                                                  | SHOULD | Retrospective produces improvement action, reusable asset, or reasoned no-op.           |

## Usability and method-quality requirements

| ID      | Requirement                                                                                                  | Level | Verification                                                                                    |
| ------- | ------------------------------------------------------------------------------------------------------------ | ----- | ----------------------------------------------------------------------------------------------- |
| MR-Q-01 | Be documented in process-centred form, with complementary role-centred and product-centred views.            | MUST  | Navigation supports all three views with stable IDs.                                            |
| MR-Q-02 | Be understandable to practitioners without requiring them to interpret the metamodel implementation.         | MUST  | Task guidance uses project language and links technical references only where needed.           |
| MR-Q-03 | Be configurable by explicit selection and substitution rules rather than informal deletion.                  | MUST  | Method profile records selected packages, omitted content, replacement controls, and rationale. |
| MR-Q-04 | Remain practical for small teams by allowing role combination and evidence proportionality.                  | MUST  | Small profile preserves accountabilities without imposing separate job titles.                  |
| MR-Q-05 | Scale to multiple teams, higher criticality, and compliance needs by adding, not rewriting, method packages. | MUST  | Configuration packages extend the core and retain stable content IDs.                           |
| MR-Q-06 | Maintain few, purposeful work products with clear state and relationships.                                   | MUST  | Every work product has purpose, owner, consumers, states, and retention rule.                   |
| MR-Q-07 | Make entry, progress, blocking conditions, and done states visible.                                          | MUST  | Process-run state model and gate outcomes are defined.                                          |
| MR-Q-08 | Preserve user involvement, traceability, and assurance when tailoring.                                       | MUST  | These are non-removable control objectives.                                                     |

## Situational factors

The method profile records factors before the first increment and revisits them
at release, major change, incident, or retrospective boundaries.

| Factor                 | Values/examples                                                             | Process effect                                                                                 |
| ---------------------- | --------------------------------------------------------------------------- | ---------------------------------------------------------------------------------------------- |
| Product novelty        | familiar / partly novel / exploratory                                       | Adds discovery spikes, prototypes, and shorter review loops.                                   |
| Serverless fit         | strong / mixed / weak / unknown                                             | Selects full serverless, hybrid, prototype-only, or exit path.                                 |
| Criticality            | ordinary / business-critical / regulated / safety-related                   | Raises independence, evidence, review, testing, and retention requirements.                    |
| Data sensitivity       | public / internal / confidential / restricted                               | Selects privacy, threat, encryption, residency, and audit packages.                            |
| Team topology          | one team / several stream teams / platform-enabled / distributed            | Selects dependency, integration, ownership, and synchronization practices.                     |
| Team skill             | experienced / mixed / new to MDE or serverless                              | Adds training, pairing, model reviews, and platform guardrails.                                |
| Requirement volatility | low / moderate / high                                                       | Changes increment size, baseline frequency, and discovery cadence.                             |
| Architecture novelty   | known patterns / new integration / new platform capability                  | Adds architecture spikes and explicit risk burn-down.                                          |
| Platform strategy      | AWS committed / provider undecided / portability required / multi-cloud     | Controls provider selection, PSM timing, and portability evidence.                             |
| Integration landscape  | isolated / SaaS APIs / legacy / multi-organization                          | Adds contract, supplier, sandbox, and operational-boundary practices.                          |
| Release risk           | low / moderate / high / irreversible data change                            | Selects deployment strategy, approvals, rollback, migration rehearsal, and observation window. |
| Availability objective | best effort / defined SLO / high availability / disaster recovery           | Selects resilience, multi-region, recovery, chaos/failure tests, and on-call work.             |
| Scale uncertainty      | predictable / bursty / unknown / extreme                                    | Adds load models, quotas, concurrency experiments, and cost guardrails.                        |
| Compliance             | none / internal policy / contractual / statutory                            | Adds independent review, evidence retention, segregation, and audit activities.                |
| Delivery cadence       | experiment / periodic release / continuous delivery / continuous deployment | Configures release train, promotion automation, and gate automation.                           |
| Legacy constraints     | greenfield / coexistence / strangler migration / replacement                | Adds migration, compatibility, dual-run, and decommissioning fragments.                        |

## Non-tailorable control objectives

Tasks and roles may be combined or replaced, but a valid profile cannot remove:

- product and stakeholder intent;
- serverless suitability and provider decision;
- accountable model and service ownership;
- requirements and cross-level traceability;
- structural model conformance and appropriate semantic review;
- security, quality, risk, and change responsibility;
- reproducible generation and release identification;
- rollback/recovery and operational ownership; or
- data, integration, access, and evidence closure at retirement.
