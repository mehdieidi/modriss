# TeamDesk demonstration

Inspect explicit organization ownership, two-tenant composite identity/key design and role-specific operations; compare health-only operator access.

Source: `.idea/scope/13-departmental-and-saas-backends/case-study/README.md`. These are small reviewed examples of the specified baseline; change exercises in the brief remain manual modeling exercises.

Import each file into the matching CIM, PIM or PSM tab of a fresh project. Use separate projects for different cases.

- [CIM model](teamdesk.cim.xmi)
- [PIM model](teamdesk.pim.xmi)
- [PSM model](teamdesk.awspsm.xmi)

The PIM/PSM are authored refinements. Transforming the CIM or PIM produces a new draft with its own review decisions; retain that draft separately.

Rules to explain during the demo:

- Resolve requested organization scope against verified active membership; never trust a body orgId or role.
- Expected-version mutations and attributable history apply within the organization; only admins manage membership/settings.
- Include orgId in data/index/idempotency/background/cache scope, allowing colliding local IDs in two organizations.
- SUSPENDED organizations deny all member business operations; platform operators have health-only access.

Assistant model verification uses only structural Ecore/EMF conformance through `ModelService.validateStructural(...)`. EVL semantic validation is a separate explicit user/model workflow. Importability and model conformance do not establish implemented application behavior.
