# MODRISS Method Content Repository

This directory is the reusable content layer of the engineered method. It is
independent of the order in which content is used in one delivery process.

- `method-fragments.json` describes phase/stage-scale process patterns and
  continuous fragments with problem/context/result semantics.
- `core-method-content.json` adds the lifecycle roles and the 29 work-product
  definitions introduced by the engineered method beyond the executable
  modeling-process catalogs. Its role-mapping table links the 16 conceptual
  lifecycle roles (`R-01`–`R-16`) to the stable RoleDefinition identifiers used
  by the executable components.
- `repository-inventory.csv` is generated from the implemented process
  definitions and the two engineered catalogs and lists their reusable roles,
  tasks, work products, and guidance.
- The complete consolidated task catalog is generated into
  `../spem/method-content-index.json`.

Stable source TaskDefinitions remain in
`mde/methodology/process-definitions/{cim,pim,psm,artifact,end-to-end}.json`.
They are referenced rather than manually copied into prose because those files
are already generated from the executable method specifications and validated
against metamodel coverage.

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
