# Research Synthesis and Adopted Method-Engineering Procedure

## Research question

The construction question is not “how should a user draw a CIM, PIM, or PSM?”
It is broader: **what configurable, end-to-end software development process lets
a team conceive, engineer, release, operate, evolve, and retire a serverless
system while treating CIM, PIM, and PSM models as primary, traceable work
products?**

The distinction matters. A modeling workflow can end after transformation or
generation. A software development method must additionally define people,
decisions, evidence, management, quality, releases, team coordination,
operations, maintenance, and retirement.

## Source corpus

The supplied corpus contains complementary evidence rather than one complete
answer.

### Eidi and Ramsin (2026): evaluation criteria and the serverless gap

The paper evaluates eight model-driven serverless or microservice approaches
through general, MDD-related, and serverless-related criteria. Its main finding
is that no reviewed approach is mature across all three groups. RADON is
strongest across general and serverless concerns; the DevOps-enabled method is
strongest in MDD structure; the standards-based approach is strong in workflow
modeling and deployment. Across the set, systematic weaknesses occur in early
serverless suitability, cost, and risk analysis; provider selection; project
management and risk management; requirements traceability; deployment
strategies; cold-start treatment; and feedback from operations.

This paper is used in two ways:

- its criteria become the seed and final evaluation framework for MODRISS; and
- its observed gaps become explicit methodology requirements, not merely items
  to discuss after the process has been designed.

### Ramsin and Paige (2010): iterative criteria-based requirements engineering

This paper supplies the requirements-engineering method for the method itself.
The procedure begins with high-level requirements, uses them as seed criteria,
selects and summarizes relevant methods with a process-centred template, then
iteratively evaluates those methods. Evaluation findings refine, split, merge,
or add criteria until both the criteria and findings stabilize. The stable
criteria are then converted into detailed methodology requirements with a
specified degree of support and concrete realization tactics.

Four meta-criteria control the evaluation set:

1. general enough to apply to every candidate;
2. precise enough to reveal differences;
3. comprehensive enough to cover significant features; and
4. balanced across technical, managerial, and usage concerns.

The paper's final requirements also establish important qualities for the
resulting method: a clear and process-centred definition; full lifecycle and
umbrella coverage; seamless transitions; requirements basis; testable and
tangible artifacts; active user involvement; practicality; manageable
complexity; configurability and scalability; consistent modeling; and explicit
inconsistency management.

### Asadi and Ramsin (2009): SMEP and reusable method-engineering patterns

The Situational Method Engineering Process (SMEP) treats method construction as
an engineering lifecycle. It distinguishes task, stage, and phase process
patterns and relates each pattern to a problem, initial context, result context,
roles, and work products. SMEP separates method initiation, iterative method
construction, and method deployment.

The source identifies four reusable infrastructure types—base methodology,
metamodel, method chunk, and configuration package—and six construction
policies: assembly, abstraction, instantiation, configuration, artifact-oriented
construction, and integration. It also defines selection, adaptation, merging,
refinement, generalization, testing-in-the-large, deployment, and
non-functional method tactics. This gives MODRISS a defensible vocabulary for
explaining not just the final result, but how the result was engineered.

### Asadi, Esfahani, and Ramsin (2010): MDA process patterns

The generic MDA Software Process (MDASP) supplies reusable development
fragments: project initiation, justification, CIM definition, requirements
analysis, infrastructure setup, planning and management, PIM definition,
transformation, coding and testing, source/target synchronization, testing in
the large, generalization, deployment, maintenance, and postmortem review.

The paper is especially important because it explicitly warns that MDA provides
modeling and transformation principles but no complete software development
process. MODRISS adopts the MDASP backbone but specializes it for event-driven
serverless systems, iterative delivery, modern CI/CD, operations, and
retirement.

### Rahimian and Ramsin (2008): Hybrid Methodology Design

Hybrid Methodology Design provides a top-down, iterative-incremental design
engine. Each iteration prioritizes methodology requirements for the current
abstraction level, selects one or more design policies (instantiation,
artifact-oriented, composition/assembly, or integration), applies them,
restructures the emerging method, selects the next level of detail, and revises
the requirements.

The mobile-method example is valuable because it demonstrates a
requirements-led construction argument: generic lifecycle patterns establish
the backbone; domain-specific concerns add specialized activities; an existing
method contributes review and learning practices; and prototyping addresses
technical risk. MODRISS follows the same logic for serverless rather than
copying that lifecycle.

### Deljouyi: a process-centred model-driven methodology example

The REST methodology chapter illustrates a readable method presentation:
high-level phases, iterative internal stages, roles, input/output work products,
fine-grained activities, feedback paths, and umbrella activities appear
together. It also embeds the model-driven chain inside a wider start-up,
construction, transition, and maintenance lifecycle.

Its value here is structural and comparative. MODRISS reuses neither its REST
DSML nor its exact tasks. It adopts the process-centred presentation and its
explicit integration of modeling, implementation, delivery, maintenance, and
continuous activities. The final MODRISS diagram similarly shows a complete
lifecycle while reducing visual density and making release, operations,
feedback, and retirement explicit.

## Evidence from the MODRISS repository

The repository already implements a substantial modeling framework:

- CIM, PIM, and AWS PSM combined Ecore models contain 90, 144, and 250 EClasses
  respectively, plus shared kernel concepts;
- EVL suites express level-specific semantic rules;
- ETL implements CIM→PIM and PIM→AWS PSM transformations with trace,
  readiness, deterministic identifiers, and three-way synchronization;
- EGL/EGX generates infrastructure, handlers, contracts, tests, CI/CD,
  documentation, and trace artifacts;
- executable process definitions contain 26 CIM tasks, 32 PIM tasks, 28 PSM
  tasks, and 16 artifact-readiness tasks; and
- the integrated process already distinguishes initiation, iterative model
  delivery, release, operations, and retirement.

This analysis changes the engineering problem. The CIM/PIM/PSM task catalogs
are not discarded. They are treated as verified candidate method chunks. The
new work is to supply requirements, selection and assembly rationale,
situational configurations, lifecycle semantics, evaluation evidence, and a
formal SPEM library around them.

## Adopted method-engineering process

MODRISS uses a hybrid of the supplied approaches:

1. **Define seed requirements and meta-criteria.** Start with full lifecycle,
   MDD automation, serverless engineering, traceability, practicality,
   configurability, and explicit governance.
2. **Inventory the situation and assets.** Examine the target domain,
   organizational context, DSML coverage, transformations, generators,
   platform scope, tooling, and existing mini processes.
3. **Summarize candidate sources.** Describe each source by activities,
   products, roles, modeling language, strengths, and weaknesses.
4. **Iteratively refine the criteria.** Apply the criteria to sources and the
   MODRISS baseline; add missing concerns and split ambiguous criteria.
5. **Construct top-down.** Instantiate SPEM and the MDASP lifecycle at the
   highest level; assemble existing CIM/PIM/PSM method chunks at lower levels.
6. **Build an artifact chain.** Connect opportunity, requirements, CIM, PIM,
   PSM, generated artifacts, release evidence, operational evidence, and
   retirement evidence so every transition has an accountable review.
7. **Integrate domain-specific practices.** Add serverless suitability, cost,
   event, state, failure, security, portability, observability, CI/CD, and
   operations fragments.
8. **Configure instead of cloning.** Define situational factors and selectable
   packages for size, criticality, novelty, compliance, multi-team work,
   provider strategy, and release risk.
9. **Test in the large.** Evaluate completeness, coherence, traceability,
   criteria coverage, SPEM consistency, repository alignment, and residual
   gaps.
10. **Deploy and evolve the method.** Publish guidance, train roles, capture
    enactment evidence, and improve the method through retrospectives and
    empirical studies.

This is a method-engineering result rather than a claim that literature alone
proves effectiveness. Structural validity is supported by traceable assembly
and criteria coverage; empirical effectiveness remains a thesis-validation task
that requires enactment in representative projects.

## References from the supplied corpus

1. Eidi, M., and Ramsin, R. (2026). “Model-Driven Approaches for Serverless
   Software Development: Evaluation and Future Directions.” MODELSWARD 2026.
2. Ramsin, R., and Paige, R. F. (2010). “Iterative criteria-based approach to
   engineering the requirements of software development methodologies.” _IET
   Software_, 4(2), 91–104.
3. Asadi, M., and Ramsin, R. (2009). “Patterns of Situational Method
   Engineering.” _Software Engineering Research, Management and Applications_,
   SCI 253, 277–291.
4. Asadi, M., Esfahani, N., and Ramsin, R. (2010). “Process Patterns for
   MDA-Based Software Development.” SERA 2010.
5. Rahimian, V., and Ramsin, R. (2008). “Designing an Agile Methodology for
   Mobile Software Development: A Hybrid Method Engineering Approach.” RCIS 2008.
6. Deljouyi. “Proposed Process for Developing REST-based Web Services,” thesis
   process chapter supplied as `Deljouyi - Process.pdf`.
