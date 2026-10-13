# RoomSlot demonstration

Inspect the slot consistency boundary and exact confirm/expiry cutoff, then follow the expiry schedule to its task.

Source: `.idea/scope/03-reservations-and-allocation/case-study/README.md`. These are small reviewed examples of the specified baseline; change exercises in the brief remain manual modeling exercises.

Import each file into the matching CIM, PIM or PSM tab of a fresh project. Use separate projects for different cases.

- [CIM model](roomslot.cim.xmi)
- [PIM model](roomslot.pim.xmi)
- [PSM model](roomslot.awspsm.xmi)

The PIM/PSM are authored refinements. Transforming the CIM or PIM produces a new draft with its own review decisions; retain that draft separately.

Rules to explain during the demo:

- Slots are fixed 30-minute UTC units; slot allocation and reservation commit atomically.
- A hold lasts five minutes; confirmation requires now < expiresAt, and expiry wins at equality.
- Availability is advisory; allocation commands reread authoritative state. Staff close only unallocated slots.

Assistant model verification uses only structural Ecore/EMF conformance through `ModelService.validateStructural(...)`. EVL semantic validation is a separate explicit user/model workflow. Importability and model conformance do not establish implemented application behavior.
