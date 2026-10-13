# Global ordering across independent services/regions

Catalogue classification: **outside**.

One total order across independent services/regions needs an explicit consensus/ordering protocol.

Import these files into the matching tabs of a fresh frontend project:

- [CIM XMI](arch-global-order.cim.xmi)
- [PIM XMI](arch-global-order.pim.xmi)

Walkthrough:

1. In CIM, inspect the architecture requirement, actors, consistency boundary and process.
2. In PIM, inspect the typed architecture elements, resource relationships and operation contracts.
3. Open the root readiness assessment and its blocking architecture decision. Explain the custom semantics listed above.
4. Demonstrate the scope decision: this essential behavior has no complete current generated path. There is deliberately no AWS PSM presented as its implementation.

The PIM represents design intent and, where available, adjacent backend structure. Extending MODRISS or supplying an external subsystem remains explicit work. A successful import is not a claim of pattern realization.

Verification uses only structural Ecore/EMF conformance through `ModelService.validateStructural(...)`. EVL belongs to a separate explicit user/model validation workflow. No deployment or business acceptance claim is made.
