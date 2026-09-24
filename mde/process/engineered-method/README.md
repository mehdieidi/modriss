# Engineering the MODRISS Development Process

This directory contains the research and method-engineering materials for the
MODRISS development process. It documents three related concerns:

1. the **modeling framework** implemented by the CIM, PIM, and AWS PSM DSMLs,
   their Ecore abstract syntax, EVL semantics, ETL transformations, and EGL/EGX
   artifact generation;
2. the reusable **method content** that explains how roles perform tasks and
   create or modify work products; and
3. the configurable **delivery process** that places that method content in a
   full product lifecycle from opportunity framing to retirement.

The MODRISS methodology has two parts: the development process and the modeling
framework. The modeling framework is the first concern above. Reusable method
content and the configurable delivery process belong to the process part.
Method content defines the roles, tasks, work products, and guidance used by
process activities.

The package was constructed from the six papers supplied in
`.idea/process_papers`, the implemented MODRISS MDE assets, and the existing
modeling-process definitions, with current official Kanban, SWEBOK, DORA, and
AWS Serverless Lens guidance used for the operational specialization. It does
not treat the three modeling guides as a
complete software process. Instead, it reuses them as method fragments within a
larger, situational, iterative-incremental lifecycle.

## Package map

| Path                                                                                                                     | Purpose                                                                                                                                    |
| ------------------------------------------------------------------------------------------------------------------------ | ------------------------------------------------------------------------------------------------------------------------------------------ |
| [`01-research-synthesis.md`](01-research-synthesis.md)                                                                   | Source-by-source synthesis and the adopted method-engineering procedure                                                                    |
| [`02-method-requirements.md`](02-method-requirements.md)                                                                 | Scope, situational factors, requirements, principles, and acceptance criteria                                                              |
| [`03-method-construction.md`](03-method-construction.md)                                                                 | Hybrid/SMEP construction log, fragment selection, assembly, and rationale                                                                  |
| [`04-development-process.md`](04-development-process.md)                                                                 | Normative full lifecycle, increments, releases, operations, change, and retirement                                                         |
| [`05-roles-work-products.md`](05-roles-work-products.md)                                                                 | Role model, team topology, work-product states, and responsibility rules                                                                   |
| [`06-tailoring-and-governance.md`](06-tailoring-and-governance.md)                                                       | Situational configuration, scale profiles, gates, CI/CD, and continuous disciplines                                                        |
| [`07-evaluation.md`](07-evaluation.md)                                                                                   | Criteria-based evaluation using the complete Eidi criterion set and Ramsin–Paige meta-criteria                                             |
| [`08-traceability.md`](08-traceability.md)                                                                               | Requirements-to-fragment-to-process-to-repository traceability and residual gaps                                                           |
| [`09-empirical-validation-protocol.md`](09-empirical-validation-protocol.md)                                             | Multiple-case validation design, measures, evidence, analysis, and threats to validity                                                     |
| [`10-thesis-process-chapter.md`](10-thesis-process-chapter.md)                                                           | Thesis-ready academic chapter integrating the method-engineering path, final process, method content, diagrams, evaluation, and references |
| [`11-rationale-for-a-model-driven-serverless-methodology.md`](11-rationale-for-a-model-driven-serverless-methodology.md) | Thesis-ready justification for treating serverless as a distinct method-engineering situation and for adopting a model-driven response     |
| [`12-method-library-and-fragment-report.md`](12-method-library-and-fragment-report.md)                                   | Academic account of the method library, reusable fragments, content families, assembly rules, governance, and claim boundary               |
| [`method-library/`](method-library/)                                                                                     | Reusable method-content catalog and machine-readable repository                                                                            |
| [`spem/`](spem/)                                                                                                         | SPEM 2.0 representation, conformance statement, delivery-process model, and synchronized diagram hierarchy                                 |
| [`diagrams/`](diagrams/)                                                                                                 | Process visualizations and the requested publication SVG                                                                                   |
| [`templates/`](templates/)                                                                                               | Enactment templates for tailoring, gate decisions, and trace/evidence control                                                              |
| [`tools/`](tools/)                                                                                                       | Reproducible generation and verification utilities for this package                                                                        |

## Normative status

The normative process narrative is
[`04-development-process.md`](04-development-process.md). The reusable method
content is normative where an element is marked `required`; tailoring may omit
or replace conditional content only under the rules in
[`06-tailoring-and-governance.md`](06-tailoring-and-governance.md).

The SPEM files are the normative structural representation of roles, tasks,
work products, guidance, Activities, TaskUses, and WorkSequences. The files use
SPEM 2.0 vocabulary and preserve the standard separation between method content
and process use. The conformance boundary is stated in
[`spem/README.md`](spem/README.md): the generated XML is a transparent
SPEM-logical exchange model, not an unverified claim of plug-and-play import into
every vendor's proprietary SPEM tooling.

## Relationship to the existing repository

The existing files under `mde/process/process-definitions` remain the
executable, UI-oriented definitions of CIM, PIM, PSM, artifact readiness, and
the integrated lifecycle. This package provides the missing academic argument:
why the lifecycle has this structure, which method fragments it reuses, which
situational factors control it, and how it is evaluated.

The integrated lifecycle coordinates two process components. Development and
Delivery uses three one-time sequential phases: inception, the active product
life, and retirement. Model-driven iterations and release activities repeat
inside the active-product phase; phases never repeat.
Operations and Maintenance begins at the first G7 handover and is explicitly
ongoing and event-driven. Its Kanban system controls production and maintenance
demand while later releases are engineered. DevOps interfaces connect CI/CD,
operational readiness, telemetry, product-changing service work, and learning.
Operations-only work can finish there; model/product changes take the shortest
safe MDE and release path; broader changes are committed to a later release.
Development Phase 2 begins only through an explicit retirement decision, and
both processes finish at G8. The detailed views and synchronization rules are indexed in
[`spem/diagram-catalog.md`](spem/diagram-catalog.md).

The assistant-validation boundary remains unchanged. Assistant-generated model
actions, patches, proposals, checkpoints, and model output are gated only by
structural Ecore/EMF conformance through `ModelService.validateStructural(...)`.
EVL semantic validation belongs only to an explicit user/model validation
workflow outside assistant apply, repair, and commit paths.

## Reading order

For thesis writing, `10-thesis-process-chapter.md` is the cohesive chapter
draft. Files 01–03 retain the detailed method-engineering evidence, 04–06 the
normative process and its tailoring rules, and 07–09 the evaluation and
validation material. The SPEM and diagram directories contain its formal and
visual representations.
