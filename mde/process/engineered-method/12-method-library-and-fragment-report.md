# The MODRISS Method Content Repository and Reusable Fragment Library

## Abstract

The MODRISS methodology has two parts: a development process and a modeling
framework. This report concerns the process part and its use of reusable SPEM
method content. A software-development process is difficult to reuse if it is
represented only as a fixed sequence of phases. The same activity may be needed
in several projects, but with different roles, evidence requirements, or degrees
of formality. The method library contains role, task, work-product, and
guidance definitions; its fragment repository packages coherent portions of
that content as reusable process patterns; and the delivery process selects
and arranges those elements for a particular serverless project. This report
explains the library design, describes its contents, and records the rationale
for selecting, combining, and governing the fragments. It accompanies the JSON
catalogs and the SPEM-logical representation.

## 1. Why the process component uses a method library

Method engineering treats a development method as an engineered artifact, not
as an indivisible recipe. Brinkkemper (1996) describes method engineering in
terms of constructing methods from method fragments, while later assembly work
emphasizes the need to select and combine fragments whose interfaces and
purposes are understood. Situational method engineering adds a further point:
the suitable method depends on the project situation. A fragment useful in a
regulated, multi-team product may be disproportionate in a short exploratory
study, even though both projects use the same modeling languages.

In MODRISS, the modeling framework defines CIM, PIM, and AWS PSM. The separate
development-process component covers feasibility analysis, release control,
operational learning, retirement, and continuous management. Reusable method
content supports that process. Copying its definitions into each project
process would make tailoring opaque and maintenance error-prone. The method
library instead provides stable definitions that can be placed in several
process configurations. A project process contains _uses_ of those definitions
rather than private copies.

The design follows the separation made by SPEM 2.0 between Method Content and
Process with Methods (OMG, 2008). A `TaskDefinition`, for example, describes
reusable work. A `TaskUse` places that task in a particular activity and may
select the steps relevant to that use. The same distinction applies to roles
and work products. This makes the library useful both as an academic account of
reusable process content and as an executable source for the MODRISS tooling.

## 2. Construction and evidential basis

The repository was not populated by collecting attractive practices without a
selection argument. Its content was derived through the method-engineering
procedure reported in Chapters 1–3 of this package. Ramsin and Paige's (2010)
criteria-based procedure supplied the requirements discipline: candidate
methods were examined, omissions were converted into method requirements, and
the requirements were refined until they were sufficiently precise to guide
construction. Hybrid Methodology Design supplied the top-down construction
logic (Rahimian and Ramsin, 2008). SMEP supplied the process-pattern template
and the vocabulary of task, stage, phase, and continuous patterns (Asadi and
Ramsin, 2009). MDASP contributed model-driven lifecycle patterns, including
CIM/PIM construction, transformation, synchronization, testing, deployment,
and maintenance (Asadi, Esfahani, and Ramsin, 2010).

Those sources were combined with two forms of project-specific evidence. The
first was the serverless evaluation performed by Eidi and Ramsin (2026), which
identified recurring weaknesses in suitability analysis, cost and risk,
provider selection, requirements traceability, deployment strategy, cold-start
treatment, management, and operational feedback. The second was the implemented
MODRISS modeling framework: its Ecore metamodels, EVL semantics, ETL
transformations, EGL/EGX generators, and existing CIM, PIM, PSM, and artifact
processes. A fragment entered the library only when it could be tied to a
method requirement, a source or repository capability, an initial and result
context, responsible roles, and observable work products.

This provides a design rationale, not an empirical effectiveness claim. The
library is structurally traceable and theoretically grounded; its practical
effects still require case-study enactment and practitioner evaluation.

## 3. Repository architecture

The library has three related layers:

1. **Atomic method content** defines reusable roles, tasks, work products, and
   guidance without fixing them to one point in the lifecycle.
2. **Method fragments** package coherent content around a recurring problem and
   a recognizable result. A fragment also records context, selection rules,
   provenance, and the method requirements it realizes.
3. **Delivery processes and configurations** place the selected definitions and
   fragments into phases, iterations, work sequences, gates, and situational
   profiles.

The consolidated baseline currently contains 17 RoleDefinitions, 132
TaskDefinitions, 88 WorkProductDefinitions, and 44 Guidance elements. The 17
technical role definitions map to 16 conceptual process roles because the
conceptual Requirements/Business Modeler role is represented by two stable
tooling roles: Requirements Engineer and Business Modeler. The task definitions
come from five process components: 30 from the end-to-end lifecycle, 26 from
CIM modeling, 32 from PIM modeling, 28 from AWS PSM modeling, and 16 from
artifact readiness. The guidance collection combines 26 focused practice
guides with 18 process-pattern descriptions.

The complete element-by-element account is provided in
[the reusable method-content catalog](method-library/reusable-method-content-catalog.md).
That catalog is generated from the same sources as the SPEM-logical XML, so it
can be read as an appendix without becoming a separately maintained version of
the process content.

## 4. Structure of a MODRISS method fragment

A fragment is documented as a process pattern rather than merely as a named
group of tasks. Each fragment records:

- the recurring problem that warrants the fragment;
- an initial context in which its use is meaningful;
- a result context that can be examined after enactment;
- the roles that perform, contribute to, or accept the work;
- the work products consumed or produced;
- its granularity—stage, phase, process component, or continuous practice;
- a selection rule stating whether it is required or conditional;
- its literature and repository provenance; and
- the method requirements for which it supplies realization evidence.

The initial and result contexts act as composition ports. They prevent assembly
from being reduced to placing attractive activities next to each other. For
example, CIM-to-PIM transformation begins with an accepted CIM and an explicit
transformation profile; its result is not an “accepted PIM,” but a traceable
PIM draft whose conflicts and manual decisions are visible. PIM refinement is
therefore a necessary following fragment. This attention to boundaries is what
makes the fragment chain defensible.

## 5. Reusable method fragments

### 5.1 Initiation, selection, and organization

#### MF-01 — Opportunity and feasibility

MF-01 prevents a technical solution from being selected before the underlying
value, boundary, constraints, and alternatives are understood. It begins when
a problem, opportunity, or mandated change has been proposed. The Sponsor,
Product Owner, Domain Expert, Solution Architect, and Service Owner frame the
endeavor and decide whether it should be pursued, explored through a bounded
experiment, redirected, or stopped. Its principal evidence is the Product and
System Charter, Feasibility and Serverless Suitability Record, and Risk and
Opportunity Register. The fragment is required because a model-driven process
still needs a justified reason to build the system.

#### MF-02 — Serverless suitability, cost, risk, and provider decision

MF-02 addresses premature commitment to serverless computing or to a specific
cloud provider. Once the intended outcomes and candidate workload are known,
the team examines workload shape, latency, state, integration, availability,
security, compliance, skills, operating model, cost uncertainty, portability,
and exit constraints. Product, architecture, platform, security, FinOps, and
service roles produce a reasoned decision or authorize an experiment where the
evidence is insufficient. This fragment is mandatory before provider-specific
design, but it does not force a serverless outcome: hybrid or non-serverless
realization remains a legitimate conclusion.

#### MF-03 — Situational method tailoring

MF-03 turns project conditions into a versioned method configuration. Its
starting context is a situation whose product, risk, organization, platform,
compliance, and delivery characteristics can be assessed. The Method Engineer
works with delivery, quality, security, service, and assurance roles to select
content, decide evidence depth, assign responsibilities, and record review
triggers. The result is a Situational Method Profile supported by team and plan
information. This fragment is always used; what changes is the selected
configuration, not whether tailoring is made explicit.

#### MF-04 — Product, release, and increment framing

MF-04 keeps modeling tied to an outcome. It begins with an authorized product
and method profile and identifies a bounded vertical slice, its users or
stakeholders, expected outcome, requirements, dependencies, risks, and
acceptance evidence. The Product Owner remains accountable for value, while
domain, requirements, architecture, delivery, and service roles ensure that
the slice is coherent and operable. The fragment is repeated for each
increment; it connects the product roadmap and release hypothesis to the
model-driven engineering engine.

### 5.2 Model-driven discovery, transformation, and realization

#### MF-05 — Event-driven domain discovery and CIM modeling

MF-05 captures business intent before software or provider structure is
allowed to dominate the design. Starting from a ready increment and available
domain knowledge, product, domain, and business-modeling roles construct a CIM
that expresses objectives, vocabulary, information, domain structure,
commands, queries, events, processes, policies, requirements, and governance.
Its result is an accepted computation-independent account of the selected
slice. The implemented CIM process component supplies the detailed tasks and
metamodel bindings.

#### MF-06 — CIM assurance and readiness

MF-06 prevents an incomplete CIM from transmitting false certainty to later
levels. A candidate revision is reviewed for structural conformance,
requirement and behavior coverage, traceability, open decisions, and the
semantic evidence required by the project profile. Product, modeling, quality,
and assurance roles decide whether the revision is accepted, returned for
rework, or deferred. In assistant-generated model workflows, acceptance is not
implied by structural conformance: assistant apply, repair, and commit paths
use only `ModelService.validateStructural(...)`; EVL semantic validation is a
separate explicit user/model validation workflow.

#### MF-07 — CIM-to-PIM transformation and reconciliation

MF-07 governs automation across the first abstraction boundary. It runs the
versioned ETL transformation into a generated target and reconciles that result
with the preceding generated baseline and the refined working PIM. The
Solution Architect, Quality Engineer, and Process Reviewer examine trace links,
assumptions, conflicts, placeholders, and manual decisions. The result is a
traceable architectural draft, not an automatically accepted design. The
fragment is selected whenever the MODRISS CIM-to-PIM transformation is used.

#### MF-08 — Platform-independent serverless architecture

MF-08 develops the PIM as an explicit serverless architecture without binding
it to AWS resources. The Solution Architect leads decisions about service
boundaries, functions, triggers, contracts, events, data and state, APIs,
workflows, integration, security, resilience, SLOs, observability, deployment
units, cost influences, and portability. Software, quality, security, FinOps,
and service roles contribute the evidence needed to judge those decisions. The
result is an accepted provider-independent architecture and associated
decision, threat, failure, privacy, cost, and readiness records.

#### MF-09 — PIM-to-PSM transformation and reconciliation

MF-09 controls provider mapping. It begins only when the PIM is accepted and a
supported provider profile has been selected. The versioned PIM-to-PSM
transformation produces an AWS-specific draft, after which architecture,
platform, quality, security, and assurance roles inspect resources, mappings,
defaults, trace links, conflicts, and unsupported capabilities. The result is
a reconciled PSM draft with visible mapping gaps rather than a silent claim
that platform realization is complete.

#### MF-10 — AWS PSM refinement and assurance

MF-10 completes the provider-specific design. It covers account and stage
structure, naming, stacks, parameters, IAM, keys, secrets, networking, compute,
storage, messaging, APIs, workflows, observability, quotas, concurrency,
retention, recovery, regional behavior, and cost controls. The Cloud Platform
Engineer owns the PSM, with quality, security, cost, service, and assurance
participation. The accepted result is an exact AWS PSM revision ready for
reproducible generation, accompanied by a readiness record rather than an
informal approval.

#### MF-11 — Reproducible model-to-text generation

MF-11 treats code generation as a controlled engineering operation. It records
the accepted PSM revision, transformation and template versions,
configuration, generated file manifest, hashes, warnings, trace links,
protected regions, and manual actions. The generated output is assigned the
state `generated-draft`; generation does not establish correctness or release
readiness. Platform and release roles can therefore reproduce the baseline and
explain why a particular artifact exists.

#### MF-12 — Artifact completion and test in the small

MF-12 closes the gap between generated scaffolding and working software.
Application, quality, and security roles complete business logic, adapters,
clients, fixtures, and supported extension points, then exercise units,
contracts, components, workflows, permissions, policies, and failure paths.
Structural gaps are returned to the PSM or generator instead of being hidden in
repeated downstream patches. The result is a source-and-test baseline with
component-level verification evidence.

#### MF-13 — Test in the large and release qualification

MF-13 addresses failures that appear only when functions, managed services,
permissions, events, data, and operational controls interact. A candidate is
deployed to a representative non-production environment and tested according
to risk. The scope may include end-to-end behavior, acceptance, performance,
concurrency, cold starts, resilience, recovery, security, compatibility,
usability, operability, and cost signals. Product, engineering, quality,
security, FinOps, release, and service roles use the resulting record to decide
whether an immutable release candidate can be proposed.

### 5.3 Release, operation, change, and closure

#### MF-14 — Progressive CI/CD and transition

MF-14 recognizes that a technically qualified candidate can still fail during
promotion or handover. It combines immutable candidate control, a release and
recovery plan, progressive promotion, stop and rollback thresholds,
observation, communication, and operational acceptance. The deployment
strategy is situational—canary, weighted alias, blue/green, feature control, or
a governed direct promotion—but the need for an exact candidate and explicit
decision is not. If promotion fails, the last accepted baseline remains in
operation and corrective work returns to Phase 1.

#### MF-15 — Operate, observe, control cost, and learn

MF-15 gives the deployed service an owned life after release. Product,
security, FinOps, and service roles monitor outcomes, SLOs, capacity,
throttling, retries, dead letters, workflow failures, provider events,
security, and cost per meaningful unit. Operational evidence is compared with
the release hypothesis and converted into prioritized change. This fragment is
continuous across the active life of the product; G7 starts an operating
baseline rather than ending development.

#### MF-16 — Incident, problem, and controlled change propagation

MF-16 prevents urgent or evolutionary change from separating the running
system from its authoritative sources. It permits immediate restoration work
when necessary, preserves the incident timeline, and subsequently classifies
the cause and impact. A domain or requirement issue returns to CIM; an
architectural issue returns to PIM; an AWS realization issue returns to PSM;
generation, implementation, release-control, and process-design issues return to their
respective sources. The corrected change is then propagated forward and
re-evidenced.

#### MF-17 — Retirement, migration, and closure

MF-17 treats retirement as an engineered transition rather than a switch-off
event. Sponsor, product, security, release, service, and data-steward roles
identify consumers, successor arrangements, data retention and disposition,
integrations, credentials, resources, continuing cost, legal obligations, and
evidence retention. Migration and decommissioning are verified before closure
is accepted. The fragment is required at the end of the product lifecycle,
although it may be rehearsed rather than enacted during a short research case.

### 5.4 Continuous fragment

#### UF-01 — Integrated management, assurance, and evidence

UF-01 spans the lifecycle instead of occupying a late phase. It integrates
product and delivery management, risk and opportunity management, quality
assurance, security and privacy, configuration and change control,
traceability, FinOps, dependency coordination, documentation and knowledge,
measurement, process improvement, and supplier concerns. These disciplines have
different owners and cadences, but they share a purpose: decisions, revisions,
risks, findings, and evidence must remain coherent while delivery proceeds.
The fragment is always selected; its rigor is adjusted through the Situational
Method Profile.

### 5.5 Fragment interface and selection matrix

The following matrix makes the assembly interfaces explicit. Role and work-
product identifiers refer to the definitions described in the reusable content
catalog. “Required” means that the control objective belongs to the core
process; the effort and evidence depth may still be tailored.

| Fragment | Kind                | Selection                                           | Roles                                          | Principal work products                                | Requirements realized                  |
| -------- | ------------------- | --------------------------------------------------- | ---------------------------------------------- | ------------------------------------------------------ | -------------------------------------- |
| MF-01    | Stage               | Required                                            | R-01, R-02, R-03, R-07, R-14                   | WP-01, WP-02, WP-04                                    | MR-LC-01, MR-SL-01, MR-Q-04            |
| MF-02    | Stage               | Required before provider-specific design            | R-02, R-07, R-08, R-11, R-12, R-14             | WP-02, WP-03, WP-04, WP-12, WP-13                      | MR-SL-01–04, MR-SL-17–18               |
| MF-03    | Stage               | Required                                            | R-04, R-05, R-10, R-11, R-14, R-15             | WP-05, WP-06, WP-07                                    | MR-LC-02, MR-Q-03–05, MR-Q-08          |
| MF-04    | Stage               | Required for every increment                        | R-02, R-03, R-05, R-06, R-07, R-14             | WP-01, WP-04, WP-07                                    | MR-LC-03, MR-RE-01, MR-RE-03, MR-MG-02 |
| MF-05    | Process component   | Required                                            | R-02, R-03, R-06                               | WP-08                                                  | MR-RE-01–02, MR-MDE-01–02              |
| MF-06    | Stage               | Required                                            | R-02, R-06, R-10, R-15                         | WP-08, WP-09                                           | MR-MDE-04, MR-MDE-06, MR-Q-07          |
| MF-07    | Stage               | Required when the MODRISS CIM→PIM transform is used | R-07, R-10, R-15                               | WP-09, WP-10, WP-11                                    | MR-MDE-03–05, MR-MDE-07                |
| MF-08    | Process component   | Required                                            | R-07, R-09, R-10, R-11, R-12, R-14             | WP-11, WP-12, WP-13, WP-14                             | MR-SL-05–10, MR-SL-12                  |
| MF-09    | Stage               | Required for a supported PSM                        | R-07, R-08, R-10, R-11, R-15                   | WP-14, WP-15, WP-16                                    | MR-MDE-03, MR-MDE-05, MR-SL-04         |
| MF-10    | Process component   | Required for the AWS profile                        | R-08, R-10, R-11, R-12, R-14, R-15             | WP-16, WP-17                                           | MR-SL-12–13, MR-SL-16–17               |
| MF-11    | Stage               | Required                                            | R-08, R-13                                     | WP-17, WP-18                                           | MR-MDE-03, MR-SL-13, MR-MG-05          |
| MF-12    | Stage               | Required                                            | R-09, R-10, R-11                               | WP-18, WP-19, WP-20                                    | MR-SL-13–14, MR-MDE-11                 |
| MF-13    | Stage               | Required; tests selected by risk                    | R-02, R-09, R-10, R-11, R-12, R-13, R-14       | WP-20, WP-21, WP-22, WP-23                             | MR-SL-14–15, MR-LC-05                  |
| MF-14    | Phase               | Required; promotion strategy varies                 | R-02, R-11, R-13, R-14, R-15                   | WP-21, WP-22, WP-23, WP-24                             | MR-SL-15, MR-MG-04–05                  |
| MF-15    | Phase               | Required during active operation                    | R-02, R-11, R-12, R-14                         | WP-25, WP-26, WP-27, WP-29                             | MR-SL-02, MR-SL-16, MR-RE-04           |
| MF-16    | Stage               | Required                                            | R-05, R-06, R-07, R-08, R-09, R-11, R-13, R-14 | WP-26, WP-27                                           | MR-LC-07, MR-MDE-05, MR-MG-05          |
| MF-17    | Phase               | Required at retirement                              | R-01, R-02, R-11, R-13, R-14, R-16             | WP-28, WP-29                                           | MR-LC-01, MR-LC-04, MR-MG-07           |
| UF-01    | Continuous practice | Required; depth is tailored                         | R-04, R-05, R-10, R-11, R-12, R-14, R-15       | WP-04, WP-05, WP-07, WP-13, WP-20, WP-25, WP-27, WP-29 | MR-MG-01, MR-MG-03–07                  |

## 6. Reusable method-content families

### 6.1 Roles

The conceptual role model contains 16 responsibility sets. It distinguishes
business authority (Sponsor and Product Owner), domain and requirements
knowledge, method and delivery engineering, solution and platform
architecture, implementation, quality, security, FinOps, release, service
ownership, independent assurance, and records/data stewardship. These are not
mandatory job titles. A small team may combine several roles, whereas a large
program may assign a role to several people. Combining roles does not remove
accountability or separation-of-duty requirements at high-risk decisions.

The reusable RoleDefinitions allow a process configuration to refer to stable
responsibilities. Process-specific RoleUses then say where those
responsibilities apply. This avoids defining “Solution Architect” differently
inside the PIM, PSM, and release processes and makes divergent source names
visible during consolidation.

### 6.2 Tasks

Each TaskDefinition states its purpose, performer roles, declared input and
output work products, ordered steps, entry and exit criteria, validation or
review checks, and—in the modeling components—metamodel coverage. The 132 tasks
are intentionally finer-grained than the 18 fragments. A fragment expresses a
reusable solution to a process problem; a task describes the work required to
realize part of that solution.

| Component            | Tasks | Contribution to the library                                                                                                       |
| -------------------- | ----: | --------------------------------------------------------------------------------------------------------------------------------- |
| End-to-end lifecycle |    30 | initiation, tailoring, increment framing, transformation orchestration, release, operation, change, learning, and retirement      |
| CIM                  |    26 | strategic intent, domain discovery, behavior, requirements, process/policy, governance, assurance, and readiness                  |
| PIM                  |    32 | services, contracts, data, compute, APIs, events, workflows, integration, policies, security, deployment intent, and readiness    |
| AWS PSM              |    28 | platform baseline, IAM, networking, storage, messaging, compute, APIs, workflows, observability, integration views, and readiness |
| Artifact readiness   |    16 | generated-baseline control, environment configuration, CI/CD, testing, release planning, recovery, and handover                   |

The complete task narratives appear in the generated catalog. This preserves
readability in the thesis report while ensuring that entry criteria, steps, and
checks are available without opening JSON.

### 6.3 Work products

Work products create continuity between fragments. The engineered lifecycle
defines 29 cross-lifecycle products, supplemented by the more detailed products
of the CIM, PIM, PSM, artifact, and end-to-end components. The principal chain
is:

`charter and suitability evidence → method profile and plan → accepted CIM →
transformation record → accepted PIM → transformation record → accepted PSM →
generated baseline → source/test baseline → immutable candidate → deployed
release → operational evidence → change or retirement evidence`.

The 29 lifecycle products fall into three families:

| Family                                                                | IDs         | Purpose                                                                                                                                                             |
| --------------------------------------------------------------------- | ----------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Strategy, feasibility, process tailoring, and planning                | WP-01–WP-07 | establish why the endeavor exists, whether serverless is suitable, how risk and cost are controlled, which process configuration applies, and how work is organized |
| Models, transformations, decisions, and generated baseline            | WP-08–WP-18 | preserve accepted CIM/PIM/PSM revisions, transformation provenance, architectural and assurance evidence, and reproducible generation                               |
| Implementation, verification, release, operation, change, and closure | WP-19–WP-29 | control software and tests, exact candidates, promotion and recovery, operational evidence, incidents, changes, retirement, and improvement                         |

A work product is not simply a file. Its definition establishes meaning; its
process use identifies the activity and task context; its revision and state
identify the exact evidence considered by a gate. Editing an accepted product
creates a new draft revision rather than silently changing the earlier
decision.

### 6.4 Guidance

Guidance captures knowledge that constrains or assists performance without
becoming another task. Focused guidance includes ubiquitous language,
event-storming and GQM practices, thin vertical slicing, contracts-first and
access-pattern reasoning, the rule that generated PIM and PSM models remain
drafts, least-privilege IAM, deployable-slice discipline, controlled generated
baselines, environment parity, secret handling, promotion evidence, and
rehearsed recovery. The 18 fragment descriptions are also represented as
Guidance so that the process-pattern rationale remains available inside the
formal method library.

## 7. Selection, assembly, and enactment

The default configuration selects the full core chain, but selection is not
equivalent to performing every task with the same ceremony. The Situational
Method Profile adjusts review independence, evidence depth, documentation,
testing, release strategy, and team coordination. Conditional fragments retain
their explicit conditions: the MODRISS transformation fragments are selected
when their supported transformation path is used, and AWS-specific PSM work is
selected only for the AWS profile.

Assembly obeys five semantic rules. First, a replacement fragment must satisfy
the same control objective and provide compatible result ports. Second,
generated content remains a draft until reviewed. Third, findings return to
the earliest authoritative source and are then propagated forward. Fourth,
each work product and gate retains one accountable owner even where roles are
combined. Fifth, tailoring occurs primarily at TaskUse, Activity, and
configuration level; stable definitions are not cloned merely to express a
project preference.

The release cycle illustrates the assembled behavior. MF-04 frames one or more
increments; MF-05 through MF-13 produce accepted, verified increments; MF-14
qualifies and transitions an exact release; MF-15 and MF-16 operate the
accepted baseline and convert evidence into later work. A subsequent release
re-enters Phase 1 while the current baseline remains in operation. MF-17 is
selected when retirement is authorized. UF-01 supplies continuous control
throughout this sequence.

### 7.1 Process components

The library exposes five process components. The end-to-end component provides
the five-phase product lifecycle, the nested release cycle, and the vertical
MDE increment. The CIM, PIM, and AWS PSM components encapsulate their respective
modeling engines and can be invoked as child processes without copying their
tasks into the parent process. The artifact-readiness component begins with a
generated project baseline and covers controlled refinement, delivery
configuration, verification, release planning, recovery, and operational
handover. These component boundaries correspond to stable input and result
ports; they are not merely diagram subdivisions.

### 7.2 Method configurations

Four reference configurations turn the common library into enactable starting
points. The **Exploration** configuration is intended for high-uncertainty work
before production commitment. It favors small teams, short experiments, thin
model slices, and concise evidence, and it requires re-tailoring before a
production release. **Standard Product Delivery** is the default for ordinary
business applications: it selects the full lifecycle, peer review, automated
delivery and testing, progressive promotion, service ownership, rollback, and
lightweight but complete decision and trace records. The **Multi-Team
Product/Platform** configuration adds bounded model ownership, contract and
integration governance, platform enablement, dependency coordination, release
alignment, and communities of practice. **Regulated or High-Criticality**
strengthens independent assurance, approval authority, segregation of duties,
trace and records retention, supplier evidence, failure and threat analysis,
recovery rehearsal, coverage, and deviation control. It is a foundation for
domain-specific assurance, not a claim of compliance or certification by
itself.

Conditional packages refine these profiles for multi-team scale, sensitive
data, stringent availability and disaster recovery, portability or
multi-cloud needs, legacy migration, high release risk, external suppliers,
and AI-assisted modeling. The last package preserves the validation boundary:
assistant-generated model changes are gated by structural Ecore/EMF
conformance, while semantic EVL validation remains an explicit user/model
workflow.

## 8. Repository governance and synchronization

The JSON catalogs are authoritative machine-readable sources, but they are no
longer the only readable representation. The build utility produces four
synchronized views: the consolidated JSON index, repository inventory CSV,
SPEM-logical XML, and the complete Markdown method-content catalog. The thesis
report explains the design and rationale; the generated catalog supplies the
element-level detail.

A proposed library contribution must have a stable identifier, an owner,
version and provenance, declared context and ports, a relationship to existing
content, and at least one review or enactment. It must state whether it
contributes, specializes, or replaces content. Successful project-specific
work is not generalized immediately: retrospective evidence is examined first,
then the candidate is reviewed and versioned as reusable content.

Two automated checks protect the baseline. The method-package verifier checks
identifier uniqueness, reference resolution, expected fragment and role
mappings, and package counts. The lifecycle synchronization verifier checks
the conditional release and retirement sequences across source JSON, SPEM XML,
PlantUML views, publication diagrams, and prose. These checks demonstrate
internal consistency; they do not replace expert review or empirical
validation.

## 9. Traceability and claim boundary

Every fragment points to method requirements, roles, and work products. Every
reusable task records process provenance, and modeling tasks may additionally
record Ecore classifier bindings. The result supports the trace path:

`research finding or project need → method requirement → method fragment →
TaskDefinition/RoleDefinition/WorkProductDefinition → process use → project
evidence`.

This traceability allows the thesis to justify why a fragment exists and how
its presence can be examined. It does not prove that the process improves
delivery time, quality, cost, or user outcomes. Those claims depend on the
empirical protocol and observed enactments. Similarly, the XML is described as
a SPEM-logical exchange model rather than as certified native interchange for
every vendor tool.

## 10. Conclusion

The MODRISS method library provides reusable content for the development-
process part of the methodology. Its role, task, work-product, and guidance
definitions establish stable semantics. Its fragments explain the problems
that coherent groups of content address and the contexts in which they may be
selected. Process configurations record how that content is used in a project.
The machine-readable and narrative representations serve different purposes,
but describe the same baseline for tooling, verification, academic inspection,
enactment, and critique.

## References

Asadi, M., Esfahani, N., and Ramsin, R. (2010). “Process Patterns for
MDA-Based Software Development.” In _Proceedings of the 8th ACIS International
Conference on Software Engineering Research, Management and Applications
(SERA 2010)_, pp. 190–197. <https://doi.org/10.1109/SERA.2010.32>.

Asadi, M., and Ramsin, R. (2009). “Patterns of Situational Method
Engineering.” In R. Lee and N. Ishii (eds.), _Software Engineering Research,
Management and Applications 2009_, Studies in Computational Intelligence,
vol. 253, pp. 277–291. Springer.
<https://doi.org/10.1007/978-3-642-05441-9_24>.

Brinkkemper, S. (1996). “Method Engineering: Engineering of Information
Systems Development Methods and Tools.” _Information and Software Technology_,
38(4), 275–280. <https://doi.org/10.1016/0950-5849(95)01059-9>.

Eidi, M., and Ramsin, R. (2026). “Model-Driven Approaches for Serverless
Software Development: Evaluation and Future Directions.” In _Proceedings of
the 14th International Conference on Model-Based Software and Systems
Engineering (MODELSWARD 2026)_, pp. 560–567.
<https://doi.org/10.5220/0014634200004058>.

Object Management Group (OMG) (2008). _Software & Systems Process Engineering
Metamodel Specification, Version 2.0_. OMG formal/2008-04-01.
<https://www.omg.org/spec/SPEM/2.0/>.

Rahimian, V., and Ramsin, R. (2008). “Designing an Agile Methodology for Mobile
Software Development: A Hybrid Method Engineering Approach.” In _Proceedings
of the 2nd International Conference on Research Challenges in Information
Science (RCIS 2008)_, pp. 337–342.
<https://doi.org/10.1109/RCIS.2008.4632123>.

Ramsin, R., and Paige, R. F. (2010). “Iterative Criteria-Based Approach to
Engineering the Requirements of Software Development Methodologies.” _IET
Software_, 4(2), 91–104. <https://doi.org/10.1049/iet-sen.2009.0032>.
