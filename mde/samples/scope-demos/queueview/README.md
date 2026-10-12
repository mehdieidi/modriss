# QueueView demonstration

Inspect READ_MODEL schemas, versioned projection and queue/status access pattern; trace rebuild/cutover and count consistency.

Source: `.idea/scope/10-read-models-and-projections/case-study/README.md`. These are small reviewed examples of the specified baseline; change exercises in the brief remain manual modeling exercises.

Import each file into the matching CIM, PIM or PSM tab of a fresh project. Use separate projects for different cases.

- [CIM model](queueview.cim.xmi)
- [PIM model](queueview.pim.xmi)
- [PSM model](queueview.awspsm.xmi)

The PIM/PSM are authored refinements. Transforming the CIM or PIM produces a new draft with its own review decisions; retain that draft separately.

Rules to explain during the demo:

- Source events are complete snapshots; apply only a greater aggregate version.
- Commit ticket membership and queue/status counts consistently; all queries/cursors bind to one active generation.
- Expose checkpoint/freshness explicitly, rebuild from snapshot K plus retained events after K, and switch generations only after convergence.
- The ticket simulator is authoritative; there is no ticket authoring, deletion or full event sourcing.

Assistant model verification uses only structural Ecore/EMF conformance through `ModelService.validateStructural(...)`. EVL semantic validation is a separate explicit user/model workflow. Importability and model conformance do not establish implemented application behavior.
