# ParcelDesk demonstration

Follow OrderAccepted through the dispatch queue, worker, simulator adapter and OrderDispatched; inspect DLQ and cancellation/return policies.

Source: `.idea/scope/02-orders-and-fulfillment/case-study/README.md`. These are small reviewed examples of the specified baseline; change exercises in the brief remain manual modeling exercises.

Import each file into the matching CIM, PIM or PSM tab of a fresh project. Use separate projects for different cases.

- [CIM model](parceldesk.cim.xmi)
- [PIM model](parceldesk.pim.xmi)
- [PSM model](parceldesk.awspsm.xmi)

The PIM/PSM are authored refinements. Transforming the CIM or PIM produces a new draft with its own review decisions; retain that draft separately.

Rules to explain during the demo:

- Catalogue quantities are 1 through 5; acceptance persists order and dispatch intent together.
- Cancellation and preparation compete on one expected-version state transition.
- Only the buyer can read/return the order; returns are allowed through the inclusive seven-day cutoff.
- Dispatch retry uses orderId as the simulator key; no payment or refund is performed.

Assistant model verification uses only structural Ecore/EMF conformance through `ModelService.validateStructural(...)`. EVL semantic validation is a separate explicit user/model workflow. Importability and model conformance do not establish implemented application behavior.
