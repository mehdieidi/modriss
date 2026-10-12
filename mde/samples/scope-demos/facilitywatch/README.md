# FacilityWatch demonstration

Follow ingestion through the queue and sustained-threshold worker, inspect sequencing/evidence policy, then the separate staleness schedule.

Source: `.idea/scope/09-telemetry-and-incidents/case-study/README.md`. These are small reviewed examples of the specified baseline; change exercises in the brief remain manual modeling exercises.

Import each file into the matching CIM, PIM or PSM tab of a fresh project. Use separate projects for different cases.

- [CIM model](facilitywatch.cim.xmi)
- [PIM model](facilitywatch.pim.xmi)
- [PSM model](facilitywatch.awspsm.xmi)

The PIM/PSM are authored refinements. Transforming the CIM or PIM produces a new draft with its own review decisions; retain that draft separately.

Rules to explain during the demo:

- Accept finite numeric temperature from a registered source, retaining occurredAt and receivedAt separately.
- Three consecutive accepted samples above 28 C open at most one unresolved incident; duplicates/older sequences do not update state and gaps reset the run.
- After five minutes without an accepted sample mark data STALE; only assigned technicians/supervisors resolve with a nonblank note.
- This is supervisory comfort information; there is no physical actuation or safety guarantee.

Assistant model verification uses only structural Ecore/EMF conformance through `ModelService.validateStructural(...)`. EVL semantic validation is a separate explicit user/model workflow. Importability and model conformance do not establish implemented application behavior.
