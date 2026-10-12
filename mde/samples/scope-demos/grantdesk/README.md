# GrantDesk demonstration

Show the CIM human review task, assignment/decision contracts, evidence storage and overdue schedule. PIM/PSM use persisted commands for human completion.

Source: `.idea/scope/04-casework-and-approvals/case-study/README.md`. These are small reviewed examples of the specified baseline; change exercises in the brief remain manual modeling exercises.

Import each file into the matching CIM, PIM or PSM tab of a fresh project. Use separate projects for different cases.

- [CIM model](grantdesk.cim.xmi)
- [PIM model](grantdesk.pim.xmi)
- [PSM model](grantdesk.awspsm.xmi)

The PIM/PSM are authored refinements. Transforming the CIM or PIM produces a new draft with its own review decisions; retain that draft separately.

Rules to explain during the demo:

- Budget is positive and at most 100000 cents; supporting evidence must be accessible to its owner.
- Only the active assigned reviewer decides; self-review and stale assignment completion are rejected.
- Persist one reasoned decision per assignment; clarification closes that assignment and preserves history.
- Escalate unfinished reviews after 48 hours, rechecking completion atomically.

Assistant model verification uses only structural Ecore/EMF conformance through `ModelService.validateStructural(...)`. EVL semantic validation is a separate explicit user/model workflow. Importability and model conformance do not establish implemented application behavior.
