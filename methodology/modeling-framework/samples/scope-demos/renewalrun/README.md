# RenewalRun demonstration

Inspect the 02:00 UTC schedule, immutable snapshot, run/checkpoint data and blocked-period/duplicate-effect policies.

Source: `.idea/scope/07-schedules-and-reconciliation/case-study/README.md`. These are small reviewed examples of the specified baseline; change exercises in the brief remain manual modeling exercises.

Import each file into the matching CIM, PIM or PSM tab of a fresh project. Use separate projects for different cases.

- [CIM model](renewalrun.cim.xmi)
- [PIM model](renewalrun.pim.xmi)
- [PSM model](renewalrun.awspsm.xmi)

The PIM/PSM are authored refinements. Transforming the CIM or PIM produces a new draft with its own review decisions; retain that draft separately.

Rules to explain during the demo:

- Missing or ambiguous accepted snapshot leaves the period BLOCKED without expiring members.
- At 02:00 UTC for date D apply valid renewals first; expire only validThrough < D.
- Never shorten validity; unknown/conflicting renewals become discrepancies.
- Checkpoint bounded pages and resume with one reminder per member/period/kind; corrections are new snapshot revisions.

Assistant model verification uses only structural Ecore/EMF conformance through `ModelService.validateStructural(...)`. EVL semantic validation is a separate explicit user/model workflow. Importability and model conformance do not establish implemented application behavior.
