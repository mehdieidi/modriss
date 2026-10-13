# AccessRules demonstration

Inspect the ordered FIRST-match decision table and explicit default, immutable policy-version binding and evaluator function. Rule metadata does not execute itself.

Source: `.idea/scope/12-policy-and-eligibility/case-study/README.md`. These are small reviewed examples of the specified baseline; change exercises in the brief remain manual modeling exercises.

Import each file into the matching CIM, PIM or PSM tab of a fresh project. Use separate projects for different cases.

- [CIM model](accessrules.cim.xmi)
- [PIM model](accessrules.pim.xmi)
- [PSM model](accessrules.awspsm.xmi)

The PIM/PSM are authored refinements. Transforming the CIM or PIM produces a new draft with its own review decisions; retain that draft separately.

Rules to explain during the demo:

- FIRST-match v1: SUSPENDED -> DENY/SUSPENDED; missing remaining fact -> NEEDS_INFO/MISSING_FACT; inactive/expired membership -> DENY/MEMBERSHIP_INACTIVE; expired certificate -> DENY/CERTIFICATION_EXPIRED; bookings >= 3 -> WAITLIST/BOOKING_LIMIT; default -> ALLOW/ELIGIBLE.
- UTC validity dates are inclusive; malformed dates/enums/counts are request errors rather than NEEDS_INFO.
- Persist immutable facts/hash and actual policy version; same request key and same facts/version returns the same decision, changed reuse conflicts.
- Only the policy owner activates reviewed versions; evaluator implementation is developer-owned, with independent expected vectors.

Assistant model verification uses only structural Ecore/EMF conformance through `ModelService.validateStructural(...)`. EVL semantic validation is a separate explicit user/model workflow. Importability and model conformance do not establish implemented application behavior.
