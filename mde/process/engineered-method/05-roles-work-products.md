# Roles, Team Structure, and Work Products

## Role model

Roles are responsibility and competency sets, not mandatory job titles. One
person may play several roles in a small project, and several people may share a
role in a program. A work product or gate still has one accountable owner.

| ID   | Role                              | Accountabilities                                                                                                                        | Core competencies                                        |
| ---- | --------------------------------- | --------------------------------------------------------------------------------------------------------------------------------------- | -------------------------------------------------------- |
| R-01 | Sponsor                           | Authorizes investment, resolves business-level escalation, accepts continuation/stop decisions.                                         | governance, funding, organizational strategy             |
| R-02 | Product Owner                     | Owns product outcomes, backlog order, scope, increment/release acceptance, and user representation.                                     | product discovery, prioritization, stakeholder decisions |
| R-03 | Domain Expert/User Representative | Supplies domain truth, language, rules, scenarios, and usability/operational feedback.                                                  | domain practice and user context                         |
| R-04 | Method Engineer                   | Assesses the situation, tailors the development process, preserves control objectives, and maintains reusable process content.          | situational method engineering, SPEM, facilitation       |
| R-05 | Delivery Lead                     | Owns integrated flow, planning, dependencies, cadence, blockers, communication, and escalation.                                         | delivery/project management, facilitation, systems flow  |
| R-06 | Requirements/Business Modeler     | Leads requirements, CIM discovery, acceptance criteria, and problem-domain traceability.                                                | requirements engineering, domain modeling, CIM DSML      |
| R-07 | Solution Architect                | Owns PIM architecture, service boundaries, contracts, data/integration/workflow decisions, and provider-independent quality trade-offs. | serverless architecture, distributed systems, PIM DSML   |
| R-08 | Cloud Platform Engineer           | Owns provider capability mapping, PSM, infrastructure automation, environments, and platform guardrails.                                | AWS/serverless services, IaC, PSM DSML, networking/IAM   |
| R-09 | Software Engineer                 | Completes business logic, adapters, clients, tests, and supported protected/extension regions.                                          | implementation, testing, event-driven systems            |
| R-10 | Quality Engineer                  | Owns verification strategy, model/test evidence quality, non-functional testing, and defect/finding workflow.                           | test architecture, quality risk, automation              |
| R-11 | Security and Privacy Engineer     | Owns threat/privacy analysis, least privilege, secret/encryption controls, security testing, exceptions, and incident support.          | cloud security, privacy/compliance, IAM                  |
| R-12 | FinOps/Cost Analyst               | Owns cost hypotheses, budgets, allocation, anomaly thresholds, and cost feedback.                                                       | cloud pricing, forecasting, unit economics               |
| R-13 | Release Engineer                  | Owns immutable candidates, CI/CD controls, promotion, version/alias strategy, rollback, and deployment records.                         | delivery automation, release management                  |
| R-14 | Service Owner/SRE                 | Owns operational readiness, SLOs, observability, support/on-call, resilience, incidents, continuity, and service outcomes.              | SRE/operations, reliability, incident management         |
| R-15 | Process/Assurance Reviewer        | Reviews gate evidence independently at the level required by risk and records acceptance or findings.                                   | assurance, auditability, review facilitation             |
| R-16 | Records/Data Steward              | Owns retention, disposition, data migration evidence, and lifecycle closure for records/data.                                           | information governance, retention, migration             |

### Separation of duties

The default profile permits self-review for low-risk exploratory increments,
but a person cannot independently approve their own high-risk security
exception, production release, or irreversible data retirement. Regulated or
critical profiles specify reviewer independence and approval authority.

## Team topology and work division

### One cross-functional team

A small team owns a bounded product/service area from CIM through operations.
Roles are combined—for example, Solution Architect with Software Engineer, or
Release Engineer with Platform Engineer—but Product Owner, technical/service
ownership, security/quality responsibility, and acceptance authority remain
visible.

### Multiple stream-aligned teams

Each stream team owns one bounded capability/service slice and its models,
code, tests, deployment, and runbooks. Shared CIM concepts and cross-context
contracts have named integration owners. Teams publish contracts and avoid
unrestricted concurrent editing of shared model roots.

### Platform enablement team

A platform team owns reusable PSM profiles, generators, CI/CD building blocks,
observability/security guardrails, and self-service environments. It does not
own stream-team product requirements or silently accept releases on their
behalf.

### Communities of practice

Modeling, architecture, security, quality, FinOps, and SRE communities maintain
guidance and reusable assets. They influence standards and learning without
becoming approval bottlenecks for every ordinary decision.

### Dependency record

Every cross-team dependency records: identifier, type, requesting and providing
owners, affected interface/model and revision, needed-by date, status, decision,
evidence, compatibility expectation, and escalation path. Dependency age is
reviewed at least each increment.

## Work-product model

Work products are intentionally broader than DSML instance models. Each has an
accountable role, state, quality criteria, consumers, version/provenance, and a
retention rule.

### Strategy and method products

| ID    | Work product                                  | Accountable role    | Minimum content                                                                             |
| ----- | --------------------------------------------- | ------------------- | ------------------------------------------------------------------------------------------- |
| WP-01 | Product/System Charter                        | Product Owner       | problem, outcomes, stakeholders, boundary, non-goals, constraints, first release hypothesis |
| WP-02 | Feasibility and Serverless Suitability Record | Solution Architect  | alternatives, fit criteria, assumptions, experiments, recommendation                        |
| WP-03 | Initial Cost Model and Budget Guardrails      | FinOps/Cost Analyst | demand assumptions, service cost drivers, scenarios, budgets, uncertainty                   |
| WP-04 | Risk and Opportunity Register                 | Delivery Lead       | cause/event/effect, exposure, owner, treatment, trigger, status/evidence                    |
| WP-05 | Situational Method Profile                    | Method Engineer     | factors, selected packages, substitutions/omissions, evidence depth, review triggers        |
| WP-06 | Team Topology and Responsibility Assignment   | Delivery Lead       | scopes, roles, accountable owners, decision rights, escalation                              |
| WP-07 | Integrated Roadmap/Release and Increment Plan | Product Owner       | outcomes, increments, dependencies, release hypotheses, forecasts                           |

### Model-driven engineering products

| ID    | Work product                                | Accountable role                    | Minimum content                                                                                               |
| ----- | ------------------------------------------- | ----------------------------------- | ------------------------------------------------------------------------------------------------------------- |
| WP-08 | CIM Revision                                | Requirements/Business Modeler       | strategic, organizational, domain, behavior, process/policy, governance, transformation intent                |
| WP-09 | CIM Review and Readiness Record             | Assurance Reviewer                  | structural/semantic evidence, trace, findings, decisions, accepted revision                                   |
| WP-10 | CIM→PIM Transformation Run                  | Solution Architect                  | source/baseline/working/target revisions, rule/profile version, trace, conflicts, manual decisions            |
| WP-11 | PIM Revision                                | Solution Architect                  | services, contracts, data, compute, APIs, events, workflows, integration, policy, security, deployment intent |
| WP-12 | Architecture Decision Record Set            | Solution Architect                  | context, alternatives, decision, consequences, evidence, expiry/review trigger                                |
| WP-13 | Threat, Privacy, Failure, and Cost Analysis | Security/Architecture/FinOps owners | scenarios, controls/treatments, residual risk, tests/monitors                                                 |
| WP-14 | PIM Review and Readiness Record             | Assurance Reviewer                  | validation, platform mapping, trace, findings, accepted revision                                              |
| WP-15 | PIM→PSM Transformation Run                  | Cloud Platform Engineer             | source/baseline/working/target revisions, profile, trace, conflicts, manual decisions                         |
| WP-16 | AWS PSM Revision                            | Cloud Platform Engineer             | provider resources, configuration, relationships, IAM, networking, observability, deployment intent           |
| WP-17 | PSM Review and Readiness Record             | Assurance Reviewer                  | validation, trace, quotas/cost/security decisions, generation readiness                                       |
| WP-18 | Generated Artifact Baseline and Manifest    | Cloud Platform Engineer             | output list/hashes, model and generator revisions, warnings, manual actions, artifact trace                   |

### Implementation, release, and operations products

| ID    | Work product                                 | Accountable role      | Minimum content                                                                                                                                                                 |
| ----- | -------------------------------------------- | --------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| WP-19 | Source and Test Baseline                     | Software Engineer     | completed logic/adapters/client, unit/contract/component tests, provenance                                                                                                      |
| WP-20 | Verification and Validation Record           | Quality Engineer      | test scope, environment, inputs, results, findings, coverage, limitations                                                                                                       |
| WP-21 | Immutable Release Candidate                  | Release Engineer      | artifact digests, configuration/schema versions, dependencies, SBOM/provenance where selected                                                                                   |
| WP-22 | Release and Recovery Plan                    | Release Engineer      | strategy, thresholds, migrations, rollback/roll-forward, communications, approvals                                                                                              |
| WP-23 | Operational Readiness and Handover Pack      | Service Owner         | SLOs, dashboards, alerts, runbooks, support, access, backup/recovery, known risks                                                                                               |
| WP-24 | Deployment/Promotion Record                  | Release Engineer      | candidate, target, timestamps, checks, observations, decision/outcome                                                                                                           |
| WP-25 | Operational Evidence Set                     | Service Owner         | SLI/SLO, incidents, capacity, cost, security, product outcome, provider events                                                                                                  |
| WP-26 | Incident and Problem Record                  | Service Owner         | timeline, impact, recovery, cause/system conditions, actions, learning                                                                                                          |
| WP-27 | Change and Impact Record                     | Requirements Engineer | request, authoritative re-entry point, affected traces, risk, plan, evidence                                                                                                    |
| WP-28 | Retirement/Migration Plan and Closure Record | Service Owner         | stakeholders, data/integrations/access/resources, migration, verification, retained evidence                                                                                    |
| WP-29 | Retrospective and Improvement Record         | Method Engineer       | observations, measures, decisions, actions, reusable candidates, no-op rationale                                                                                                |
| WP-30 | Operational Work Item                        | Service Owner         | source, affected service, maintenance purpose, emergency-temporary status, service class, severity, owner, SLE, state, age, evidence, authoritative re-entry point, disposition |
| WP-31 | Operations Flow Policy and Board             | Delivery Lead         | workflow states, WIP limits, pull/replenishment rules, service classes, SLEs, capacity and expedite policies, board and flow measures                                           |

## Work-product states

The standard state progression is:

`identified → draft → reviewed → accepted → baselined → superseded → archived`.

`rejected`, `deferred`, and `withdrawn` are terminal alternatives for a specific
revision. Generated content also uses `generated-draft` before review. A
release candidate uses `assembled → qualified → authorized → promoted →
withdrawn/superseded`.

WP-30 uses the flow states `intake → triage → ready → active → verify → done`,
with `blocked`, `deferred-to-planned-release`, and `cancelled` explicitly
recorded. WP-31 is a versioned policy: changing a WIP limit, service-level
expectation, capacity rule, or expedite authority creates a reviewed revision
rather than silently rewriting historical flow evidence.

Acceptance is revision-specific. Editing an accepted work product creates a new
draft revision; it does not mutate the prior decision invisibly.

## Responsibility rules

1. Each work product has exactly one accountable role and may have many
   contributors, reviewers, and informed parties.
2. The accountable role may delegate work but not acceptance responsibility.
3. A gate names the authority who accepts residual risk; silence is not
   acceptance.
4. Model ownership follows bounded scope. Cross-boundary references use
   published interfaces and dependency records.
5. Generated artifacts are owned operationally and technically after
   generation; “the generator made it” is not an ownership category.
6. Product and user representatives participate in intent and acceptance;
   technical roles cannot infer business acceptance from passing tests alone.
