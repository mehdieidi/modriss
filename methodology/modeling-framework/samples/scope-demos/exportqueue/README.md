# ExportQueue demonstration

Follow durable acceptance into queue/DLQ, bounded worker and private result store; inspect cancellation/success race and partial-batch configuration.

Source: `.idea/scope/15-asynchronous-jobs/case-study/README.md`. These are small reviewed examples of the specified baseline; change exercises in the brief remain manual modeling exercises.

Import each file into the matching CIM, PIM or PSM tab of a fresh project. Use separate projects for different cases.

- [CIM model](exportqueue.cim.xmi)
- [PIM model](exportqueue.pim.xmi)
- [PSM model](exportqueue.awspsm.xmi)

The PIM/PSM are authored refinements. Transforming the CIM or PIM produces a new draft with its own review decisions; retain that draft separately.

Rules to explain during the demo:

- Accept only after job and recoverable dispatch intent persist together; replay returns one job.
- Select at most 1000 rows from the immutable 10000-row snapshot; publish one correct private CSV per job.
- Cancellation commits only in QUEUED/RUNNING; if it wins workers cannot publish success, and if success wins cancellation conflicts.
- Only owners see status/cancel/download; CANCELLED jobs have no result. Configure five concurrent workers and recover partial batches.

Assistant model verification uses only structural Ecore/EMF conformance through `ModelService.validateStructural(...)`. EVL semantic validation is a separate explicit user/model workflow. Importability and model conformance do not establish implemented application behavior.
