# MemberLedger demonstration

Inspect the Member aggregate, role-specific commands, version conflict policy and attributable MemberChange history.

Source: `.idea/scope/01-registries-and-administration/case-study/README.md`. These are small reviewed examples of the specified baseline; change exercises in the brief remain manual modeling exercises.

Import each file into the matching CIM, PIM or PSM tab of a fresh project. Use separate projects for different cases.

- [CIM model](memberledger.cim.xmi)
- [PIM model](memberledger.pim.xmi)
- [PSM model](memberledger.awspsm.xmi)

The PIM/PSM are authored refinements. Transforming the CIM or PIM produces a new draft with its own review decisions; retain that draft separately.

Rules to explain during the demo:

- Register once per subject; request-key replay returns the original identity.
- Members edit only their own permitted fields; staff own branch/status changes.
- Commit expected-version mutation, uniqueness marker, request key and history atomically; page at most 25 results.

Assistant model verification uses only structural Ecore/EMF conformance through `ModelService.validateStructural(...)`. EVL semantic validation is a separate explicit user/model workflow. Importability and model conformance do not establish implemented application behavior.
