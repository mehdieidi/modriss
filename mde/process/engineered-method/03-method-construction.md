# Method Construction, Fragment Assembly, and Rationale

## Construction strategy

The development process was built with a hybrid policy rather than derived
from a single reference process. In SMEP terms, the infrastructure consists
of:

- **metamodel:** SPEM 2.0 provides the method-content/process architecture;
- **base process:** MDASP provides the model-driven lifecycle backbone;
- **method chunks:** the implemented CIM, PIM, PSM, artifact-readiness,
  release, operations, and retirement content supplies reusable fragments;
- **configuration packages:** situational profiles select or strengthen
  assurance, scale, portability, compliance, and release practices; and
- **existing methods/practices:** serverless, DevOps, agile, and operational
  techniques are integrated where the base process is weak.

The selected creation policies are instantiation, assembly, artifact-oriented
construction, integration, and configuration. Abstraction/generalization is
used after enactment to return successful fragments to the method library.

## Top-down Hybrid Methodology Design iterations

### Iteration 0 — establish requirements and evaluation controls

The Eidi criteria and Ramsin–Paige process requirements were adopted as seed
criteria. Repository analysis added explicit requirements for transformation
draft review, three-way synchronization, assistant-validation boundaries,
release reproducibility, operational feedback, and retirement.

Result: the requirements and situational-factor catalog in
`02-method-requirements.md`.

### Iteration 1 — instantiate the lifecycle architecture

SPEM was selected for method representation. MDASP was instantiated as the
initial backbone, but its three broad phases were refined into five lifecycle
phases so releases, operations, and retirement would not be hidden inside
deployment and maintenance:

1. Initiate, Tailor, and Organize;
2. Iterative-Incremental Model-Driven Delivery;
3. Release and Transition;
4. Operate, Evolve, and Learn; and
5. Retire, Migrate, and Close.

This preserves MDASP's initiation–construction–deployment logic while adding a
long-lived product/service lifecycle.

### Iteration 2 — create the artifact chain

The artifact-oriented policy connected:

`Opportunity → Product Charter → Method Profile → Requirements/CIM → PIM → PSM
→ Generated Baseline → Release Candidate → Deployed Release → Operational
Evidence → Change/Retirement Evidence`.

Every transition received a task, accountable role, input/output relation,
trace expectation, and review outcome. This reduces gaps between business,
modeling, implementation, and operational work in the process.

### Iteration 3 — assemble the implemented modeling fragments

The existing CIM, PIM, and PSM processes were selected because they are bound
to the actual Ecore classifiers and validated by coverage tools. Their internal
task order, work-product parameters, and role uses are reused rather than
rewritten. The artifact-readiness fragment was added after PSM-to-text
generation.

The assembly boundary treats each child process as a process component with
declared ports:

| Component               | Input ports                                                         | Output ports                                                  |
| ----------------------- | ------------------------------------------------------------------- | ------------------------------------------------------------- |
| CIM modeling            | accepted increment hypothesis, domain sources, situational controls | accepted CIM revision, trace/readiness evidence               |
| CIM→PIM transformation  | accepted CIM, transformation profile, prior baseline                | generated PIM draft, trace, assumptions, conflicts/report     |
| PIM refinement          | generated PIM draft, requirements/CIM trace                         | accepted PIM revision and platform-readiness evidence         |
| PIM→PSM transformation  | accepted PIM, provider profile, prior baseline                      | generated AWS PSM draft, trace, assumptions, conflicts/report |
| PSM refinement          | generated PSM draft, PIM trace, AWS constraints                     | accepted AWS PSM revision and generation-readiness evidence   |
| PSM→artifact generation | accepted PSM, generator/template version                            | reproducible generated baseline and manifest                  |
| Artifact readiness      | generated baseline, manual-action report                            | verified release-candidate input or rework decision           |

### Iteration 4 — integrate serverless-specific fragments

Criteria that MDASP and the mini processes did not cover sufficiently were
added explicitly: serverless suitability, cost and risk analysis, provider
selection, function granularity, event contracts, choreography, failure and
state modeling, least privilege and secrets, serverless test strategy,
progressive delivery, function versions/aliases, observability, operational
feedback, cold-start decisions, and lock-in/exit decisions.

These are not forced into one model level. Business events and quality intent
begin at CIM; provider-independent architecture belongs at PIM; provider
resources belong at PSM; realized tests, pipelines, and runbooks belong to the
artifact/release process; actual SLO and cost evidence belongs to operations.

### Iteration 5 — add continuous disciplines and scale

Project/delivery management, risk, quality, security/privacy, configuration and
change, traceability, knowledge/reuse, measurement, and supplier management
were added as continuous disciplines with explicit work products. Multi-team
coordination was designed around bounded ownership, published contracts,
integration ownership, dependency records, and release coordination rather
than one shared model edited without boundaries.

### Iteration 6 — configure and test in the large

Situational factors were turned into configuration rules and four reference
profiles. The assembled method was evaluated against the full Eidi criterion
set and checked for requirement, fragment, process, and repository traceability.
Gaps were preserved rather than scored away: multi-cloud PSM realization,
full round-trip transformation, quantitative cost estimation, automated
cold-start testing, native vendor-tool SPEM interchange, and empirical method
validation remain open or partial.

### Iteration 7 — engineer planned/interrupt-driven coexistence

The lifecycle was stress-tested against an operating service: planned product
work can be forecast at release and increment horizons, whereas incidents,
vulnerabilities, provider events, cost anomalies, and maintenance requests
arrive unpredictably. Treating both as one sprint/release backlog either hides
interrupts or makes planned commitments meaningless.

MF-18 was therefore assembled from the Kanban Guide's pull-system controls,
SWEBOK maintenance categories, AWS Serverless Lens operational practices, and
the existing MODRISS trace/release fragments. It adds WP-30 Operational Work
Item and WP-31 Operations Flow Policy and Board. Its three result ports are:
operations-only completion, a bounded change through the shortest safe MDE and
release path, or explicit commitment to a future planned release. Emergency
restoration is allowed, but a temporary downstream modification cannot close
until permanent correction or authoritative-source reconciliation is tracked.

## Reusable fragment catalog

Each fragment is documented as a process pattern: problem, initial context,
result context, roles, work products, tasks, evidence, and selection rule. The
machine-readable catalog is in `method-library/method-fragments.json`.

| ID    | Fragment                                                  | Source/rationale                                                   | Default                               |
| ----- | --------------------------------------------------------- | ------------------------------------------------------------------ | ------------------------------------- |
| MF-01 | Opportunity and feasibility                               | MDASP Justify; Ramsin–Paige feasibility; Eidi exploration criteria | Required                              |
| MF-02 | Serverless suitability, cost, risk, and provider decision | Eidi serverless gaps                                               | Required before PSM commitment        |
| MF-03 | Situational method tailoring                              | SMEP requirements/infrastructure setup and configuration           | Required                              |
| MF-04 | Product/release framing                                   | Requirements basis, active user involvement, iterative delivery    | Required                              |
| MF-05 | Event-driven domain discovery and CIM modeling            | MDASP Define CIM; implemented CIM process                          | Required                              |
| MF-06 | CIM assurance and readiness                               | Continuous V&V; MODRISS EVL/readiness/trace                        | Required                              |
| MF-07 | CIM→PIM transformation and reconciliation                 | MDASP Transformation and synchronization; implemented ETL merge    | Conditional on MODRISS transformation |
| MF-08 | Platform-independent serverless architecture              | MDASP Define PIM; implemented PIM process                          | Required                              |
| MF-09 | PIM→PSM transformation and reconciliation                 | MDASP Transformation; implemented ETL merge                        | Required for a supported PSM          |
| MF-10 | AWS PSM refinement and assurance                          | Implemented PSM process, readiness, and trace                      | Required for the AWS profile          |
| MF-11 | Reproducible model-to-text generation                     | Implemented EGX/EGL                                                | Required                              |
| MF-12 | Artifact completion and test in the small                 | MDASP Coding/Testing; artifact-readiness process                   | Required                              |
| MF-13 | Test in the large and release qualification               | MDASP Test in the Large; Eidi testing criteria                     | Required                              |
| MF-14 | Progressive CI/CD and transition                          | Deljouyi transition structure; serverless release criteria         | Required; strategy varies             |
| MF-15 | Operate, observe, control cost, and learn                 | Eidi observability/feedback; lifecycle completeness                | Required                              |
| MF-16 | Incident, problem, and controlled change propagation      | Maintenance plus source/target synchronization                     | Required                              |
| MF-17 | Retirement, migration, and closure                        | Complete lifecycle requirement                                     | Required                              |
| MF-18 | Interrupt-driven operations and maintenance flow          | Kanban Guide; SWEBOK maintenance; AWS Serverless Lens              | Required for production operation     |
| UF-01 | Integrated management, assurance, and evidence            | Ramsin–Paige umbrella activities; MDASP postmortem/generalization  | Continuous; depth is tailored         |

`UF-01` is a composite continuous pattern. It is decomposed operationally in
`06-tailoring-and-governance.md` into product/delivery management, risk and
opportunity, quality assurance, security/privacy/compliance, configuration and
change, traceability/evidence, team dependencies, measurement and method
improvement, reuse/knowledge, and supplier management. Keeping one umbrella
fragment avoids pretending that these disciplines run as ten separate process
lanes; each discipline still has its own cadence, owner, and evidence.

## Assembly rules

1. A core fragment may be replaced only by another fragment that satisfies the
   same control objectives and produces compatible output ports.
2. A downstream component cannot accept generated content solely because a
   transformation or generator completed successfully.
3. When a downstream decision changes upstream intent, the change is routed to
   the earliest authoritative work product and propagated forward.
4. Each work product has one accountable owner even when several roles
   contribute.
5. `TaskDefinition` describes reusable work; `TaskUse` places a selected use of
   that work in an Activity. Tailoring occurs primarily at the use/configuration
   level so library content remains stable.
6. Method extensions use stable identifiers and declare whether they
   contribute, replace, or specialize existing content.
7. Automation reduces effort, not accountability. The role accepting a model,
   artifact, release, or service outcome remains named.

## Validity argument

The construction has four forms of evidence:

- **source validity:** every major lifecycle family is grounded in a supplied
  process or method-engineering source;
- **requirements validity:** stabilized criteria are translated into testable
  method requirements;
- **internal validity:** SPEM relationships and trace matrices connect roles,
  tasks, products, activities, and evidence without silent transitions; and
- **implementation validity:** modeling fragments point to actual MODRISS
  metamodels, validation, transformation, generation, and process-definition
  assets.

This supports a rigorous design claim. It does not substitute for empirical
validation. Case-study enactment, practitioner review, controlled comparison,
and measurement of project outcomes remain necessary to establish practical
effectiveness.
