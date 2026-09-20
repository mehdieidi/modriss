# MODRISS Engineered Software Development Method

This directory is the research and method-engineering package for the MODRISS
software development method. It separates three things that are often conflated:

1. the **modeling framework** implemented by the CIM, PIM, and AWS PSM DSMLs,
   their Ecore abstract syntax, EVL semantics, ETL transformations, and EGL/EGX
   artifact generation;
2. the reusable **method content** that explains how roles perform tasks and
   create or modify work products; and
3. the configurable **delivery process** that places that method content in a
   full product lifecycle from opportunity framing to retirement.

The package was constructed from the six papers supplied in
`.idea/process_papers`, the implemented MODRISS MDE assets, and the existing
modeling-process definitions. It does not treat the three modeling guides as a
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

The existing files under `mde/methodology/process-definitions` remain the
executable, UI-oriented definitions of CIM, PIM, PSM, artifact readiness, and
the integrated lifecycle. This package provides the missing academic argument:
why the lifecycle has this structure, which method fragments it reuses, which
situational factors control it, and how it is evaluated.

The full lifecycle contains a repeatable release cycle rather than one terminal
release. G7 establishes the currently accepted operating baseline. While that
baseline continues to run, selected roadmap or operational work begins a later
release at Phase 1. Phase 4 is reached only through an explicit retirement
decision. The detailed views and synchronization rules are indexed in
[`spem/diagram-catalog.md`](spem/diagram-catalog.md).

The assistant-validation boundary remains unchanged. Assistant-generated model
actions, patches, proposals, checkpoints, and model output are gated only by
structural Ecore/EMF conformance through `ModelService.validateStructural(...)`.
EVL semantic validation belongs only to an explicit user/model validation
workflow outside assistant apply, repair, and commit paths.

## Reading order

For thesis writing, `10-thesis-process-chapter.md` is the cohesive chapter
draft. Files 01–03 retain the detailed method-engineering evidence, 04–06 the
normative methodology, 07–09 the evaluation and validation material, and the
SPEM and diagram directories the formal and visual representations.
