# CampusSignal demonstration

Show the topic with two independently buffered subscribers and a delivery simulator; contrast feed success with delivery retry.

Source: `.idea/scope/05-notifications-and-distribution/case-study/README.md`. These are small reviewed examples of the specified baseline; change exercises in the brief remain manual modeling exercises.

Import each file into the matching CIM, PIM or PSM tab of a fresh project. Use separate projects for different cases.

- [CIM model](campussignal.cim.xmi)
- [PIM model](campussignal.pim.xmi)
- [PSM model](campussignal.awspsm.xmi)

The PIM/PSM are authored refinements. Transforming the CIM or PIM produces a new draft with its own review decisions; retain that draft separately.

Rules to explain during the demo:

- Publish stable-ID closure/reopening events to two independent consumers.
- Recheck enabled building preferences immediately before sending; later opt-out cannot retract an accepted send.
- A delivery key is eventId/subjectId/channel; matching simulator replay returns one provider reference.
- Retain failed/skipped/accepted delivery outcomes for authorized replay; no real email or SMS.

Assistant model verification uses only structural Ecore/EMF conformance through `ModelService.validateStructural(...)`. EVL semantic validation is a separate explicit user/model workflow. Importability and model conformance do not establish implemented application behavior.
