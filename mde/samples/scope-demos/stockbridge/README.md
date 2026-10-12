# StockBridge demonstration

Compare source/destination contract fields, queue/adapter references and source-version ordering. Both external endpoints are local simulators.

Source: `.idea/scope/08-partner-and-legacy-integration/case-study/README.md`. These are small reviewed examples of the specified baseline; change exercises in the brief remain manual modeling exercises.

Import each file into the matching CIM, PIM or PSM tab of a fresh project. Use separate projects for different cases.

- [CIM model](stockbridge.cim.xmi)
- [PIM model](stockbridge.pim.xmi)
- [PSM model](stockbridge.awspsm.xmi)

The PIM/PSM are authored refinements. Transforming the CIM or PIM produces a new draft with its own review decisions; retain that draft separately.

Rules to explain during the demo:

- Map itemCode/availableQty/itemVersion deterministically to sku/stockAvailable/sourceVersion; quantities are nonnegative integers.
- Newest full snapshot wins per SKU; older snapshots never revert destination stock.
- Stable exchange ID and payload hash resolve timeout-after-commit; provider 429 is retriable, malformed contracts fail permanently.
- Operator replay is authorized and audited; simulators use controlled study endpoints and credentials.

Assistant model verification uses only structural Ecore/EMF conformance through `ModelService.validateStructural(...)`. EVL semantic validation is a separate explicit user/model workflow. Importability and model conformance do not establish implemented application behavior.
