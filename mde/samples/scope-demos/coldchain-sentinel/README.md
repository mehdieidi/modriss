# ColdChain Sentinel demonstration

Show the analytical scenario's ingestion, detection, alert fan-out, acknowledgement/escalation and retained evidence. Thresholds/connectivity are review decisions, not asserted domain facts.

Source: `.idea/scope/one evaluation/coldchain-sentinel/coverage-summary.json`. These are small reviewed examples of the specified baseline; change exercises in the brief remain manual modeling exercises.

Import each file into the matching CIM, PIM or PSM tab of a fresh project. Use separate projects for different cases.

- [CIM model](coldchain-sentinel.cim.xmi)
- [PIM model](coldchain-sentinel.pim.xmi)
- [PSM model](coldchain-sentinel.awspsm.xmi)

The PIM/PSM are authored refinements. Transforming the CIM or PIM produces a new draft with its own review decisions; retain that draft separately.

Rules to explain during the demo:

- This is a fictional information-only slice of the analytical ColdChain process scenario; no medical or physical control.
- Retain occurrence/receipt time and stable reading identity; duplicate/late arrivals follow a reviewed per-shipment sequencing policy.
- Correlate one alert identity with an excursion, record acknowledgement, and retry/escalate without duplicate delivery effects.
- Retain versioned excursion evidence for review; ERP connectivity and provider-exit remain explicit human review decisions.

Assistant model verification uses only structural Ecore/EMF conformance through `ModelService.validateStructural(...)`. EVL semantic validation is a separate explicit user/model workflow. Importability and model conformance do not establish implemented application behavior.
