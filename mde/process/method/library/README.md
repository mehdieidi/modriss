# MODRISS Method Content Repository

This directory is the reusable content layer of the engineered method. It is
independent of the order in which content is used in one delivery process.

- `method-fragments.json` describes phase/stage-scale process patterns and
  continuous fragments with problem/context/result semantics.
- `core-method-content.json` adds the lifecycle roles and the 31 work-product
  definitions introduced by the engineered method beyond the executable
  modeling-process catalogs. Its role-mapping table links the 16 conceptual
  lifecycle roles (`R-01`–`R-16`) to the stable RoleDefinition identifiers used
  by the executable components.
- `repository-inventory.csv` is generated from the implemented process
  definitions and the two engineered catalogs and lists their reusable roles,
  tasks, work products, and guidance.
- `reusable-method-content-catalog.md` is the generated human-readable catalog
  of every consolidated role, task, work product, and guidance definition,
  including task steps, entry/exit criteria, checks, and provenance.
- The complete consolidated task catalog is generated into
  `../spem/method-content-index.json`.

The thesis-style rationale and the narrative description of all 19
lifecycle/integration fragments plus the composite continuous fragment are in
`../12-method-library-and-fragment-report.md`.

Stable source TaskDefinitions remain in
`mde/process/definitions/{cim,pim,psm,artifact,end-to-end}.json`.
They are rendered into the Markdown catalog by the package builder rather than
being manually duplicated. This keeps the readable descriptions synchronized
with the executable method specifications and their metamodel coverage.

## Fragment template

Every fragment records:

- the recurring problem it solves;
- initial and result contexts;
- roles and work products;
- reusable activities/tasks;
- selection rule and required/conditional status;
- source/provenance; and
- the requirements it realizes.

This follows the process-pattern model in the supplied SMEP and MDASP papers.

## Contribution rule

A fragment enters the repository only after it has stable identifiers,
documented ports and context, at least one review or enactment, an accountable
owner, version/provenance, and a statement of whether it contributes,
specializes, or replaces existing content.
