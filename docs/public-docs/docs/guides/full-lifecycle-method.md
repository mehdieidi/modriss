# Full-Lifecycle Software Development Method

MODRISS uses CIM, PIM, and AWS PSM as connected child methods inside one
software-development lifecycle. The lifecycle method adds the work that a
technical modeling pipeline cannot provide on its own: product intent,
situational tailoring, team topology, release management, transition,
operations, controlled change propagation, and retirement.

The maintained normative method is
[`mde/process/software-development-process.md`](https://github.com/mehdieidi/modriss/blob/main/mde/process/software-development-process.md).
Its machine-readable process definitions are in
[`mde/process/process-definitions/`](https://github.com/mehdieidi/modriss/tree/main/mde/process/process-definitions).

## Lifecycle shape

1. **Initiate, tailor, and organize** — define outcomes, select a situational
   method profile, assign ownership, and establish quality/security/operations
   baselines.
2. **Deliver iterative vertical increments** — frame a slice, execute CIM,
   transform to PIM, refine PIM, transform to AWS PSM, refine PSM, generate
   artifacts, and accept or rework the increment.
3. **Release and transition** — assemble accepted increments, verify the exact
   candidate, promote progressively, rehearse rollback, and hand over service
   ownership.
4. **Operate, evolve, and learn** — inspect SLOs and product outcomes, manage
   incidents, propagate changes through traces and dependencies, and improve
   the method using evidence.
5. **Retire, migrate, and close** — dispose of data, integrations, access, and
   infrastructure safely and retain the required knowledge and records.

## Scientific grounding

The process content is expressed using [OMG SPEM 2.0](https://www.omg.org/spec/SPEM/2.0/About-SPEM),
tailored against the lifecycle scope of [ISO/IEC/IEEE 12207:2026](https://www.iso.org/standard/90219.html)
and [ISO/IEC/IEEE 15288:2023](https://www.iso.org/standard/81702.html). Its situational
composition follows method-engineering research by
[Brinkkemper](<https://doi.org/10.1016/S0950-5849(95)01059-9>) and
[Brinkkemper, Saeki, and Harmsen](https://www.sciencedirect.com/science/article/pii/S0306437999000162).
The model-driven increment structure also uses the process-pattern perspective
of [Asadi, Esfahani, and Ramsin](https://mason.gmu.edu/~nesfaha2/Publications/SERA2010.pdf),
while inspection/adaptation practices are compatible with the
[Agile principles](https://agilemanifesto.org/principles) and
[Scrum Guide](https://scrumguides.org/scrum-guide.html).

## Progress and evidence

Each process definition exposes a progress contract. A project run records the
increment or release, method profile, teams, current phase/stage, state, owner,
evidence links, blockers, decisions, dependencies, and acceptance timestamps.
Core measures include task completion, increment flow time, rework, trace
coverage, open blocking findings, manual-decision closure, dependency age,
release frequency, and escaped defects. Task completion is never a substitute
for acceptance evidence or a gate decision.

## Validation boundary

Assistant-generated actions and model outputs are gated by structural Ecore/EMF
conformance through `ModelService.validateStructural(...)`. Semantic EVL
validation is available only in an explicit user/model validation workflow
outside assistant apply/repair/commit paths.
