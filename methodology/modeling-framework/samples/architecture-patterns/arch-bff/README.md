# API composition and backend for frontend

Catalogue classification: **conditional**.

Compose a client-facing stock view from two adapter calls with explicit partial failure.

Import these files into the matching tabs of a fresh frontend project:

- [CIM XMI](arch-bff.cim.xmi)
- [PIM XMI](arch-bff.pim.xmi)
- [PSM XMI](arch-bff.awspsm.xmi)

Walkthrough:

1. In CIM, inspect the architecture requirement, actors, consistency boundary and process.
2. In PIM, inspect the typed architecture elements, resource relationships and operation contracts.
3. Open the root readiness assessment and its blocking architecture decision. Explain the custom semantics listed above.
4. In PSM, inspect the AWS structure and its review decision. Resolve applicable source-validation/review issues before using the formal workbench generation workflow; compare a separately transformed draft with this authored refinement.

The supplied PSM represents the supported substrate. Generated handlers still need implementation; it does not establish the complete pattern's runtime correctness.

Verification uses only structural Ecore/EMF conformance through `ModelService.validateStructural(...)`. EVL belongs to a separate explicit user/model validation workflow. No deployment or business acceptance claim is made.
