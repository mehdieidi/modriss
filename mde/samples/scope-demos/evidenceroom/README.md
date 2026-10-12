# EvidenceRoom demonstration

Inspect independent review, version/hash provenance, holds and serialized disposition policy, then the private bucket and scheduled deletion task.

Source: `.idea/scope/14-evidence-and-governance/case-study/README.md`. These are small reviewed examples of the specified baseline; change exercises in the brief remain manual modeling exercises.

Import each file into the matching CIM, PIM or PSM tab of a fresh project. Use separate projects for different cases.

- [CIM model](evidenceroom.cim.xmi)
- [PIM model](evidenceroom.pim.xmi)
- [PSM model](evidenceroom.awspsm.xmi)

The PIM/PSM are authored refinements. Transforming the CIM or PIM produces a new draft with its own review decisions; retain that draft separately.

Rules to explain during the demo:

- Bind evidence and review to exact object version/hash; contributors cannot review their own evidence.
- Only authorized roles access provenance/content or change holds; distinguish denied attempts from accepted actions.
- Study retention is 30 days; serialize holds and deletion authorization on one evidence state, and reject new holds after DELETE_AUTHORIZED.
- Confirm physical deletion before success; retain content-free disposition/custody tombstones for 90 study days. No regulatory certification or WORM claim.

Assistant model verification uses only structural Ecore/EMF conformance through `ModelService.validateStructural(...)`. EVL semantic validation is a separate explicit user/model workflow. Importability and model conformance do not establish implemented application behavior.
