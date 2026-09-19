# Metamodel Ownership Conventions

Authoritative checklist for val/ref ownership across CIM, PIM, and PSM Emfatic modules.
Use during metamodel edits, audits, and reviews of sample XMI.

## Rules

| Rule                       | Meaning                                                                                                 |
| -------------------------- | ------------------------------------------------------------------------------------------------------- |
| **Single container**       | Every persistable `EClass` has exactly one `val` owner chain up to the level root                       |
| **Shared/reusable**        | Concepts referenced by multiple owners → `val` at level root, linked via `ref`                          |
| **Scoped/detail**          | Concepts existing only inside one parent → `val` on that parent + `readonly transient ref` back-pointer |
| **Cross-boundary link**    | `ref` with explicit opposite where bidirectional navigation is needed                                   |
| **No duplicate ownership** | No parallel `owns*` refs mirroring containment                                                          |
| **Derived aggregates**     | Use `readonly transient volatile derived` only for computed views (e.g. PSM `allResources`)             |

## Emfatic semantics

- `val` = containment (`EReference.containment=true`)
- `ref` = cross-link between independently owned elements
- Bidirectional pairs use `#oppositeName` on both sides
- Child back-pointers: `readonly transient ref Parent[1]#containerFeature backPointer`

## PIM service boundary

`deployment.ServerlessService` is the single owner of deployable runtime elements:

- `api.Api`, `compute.Function`, `integration.EventChannel`, `integration.Schedule`
- `data.StorageElement` (DataStore, ObjectStore), `workflow.Workflow`, `external.ExternalAdapter`

Shared at `PIMModel` root (reused across services):

- `contracts.Schema`, `contracts.EventType`
- `policy.ArchitecturePolicy` and subtypes
- `security.IdentityProvider`, `security.Principal`
- `config.ConfigurationSet`, `config.Secret`
- `deployment.Environment`, `deployment.DeploymentUnit`, `deployment.ServerlessService`
- `deployment.ServiceElementMembership` (metadata only)
- `integration.Flow`, `workflow.HumanTask`, `workflow.EscalationPolicy`
- `external.ExternalEndpoint`, `data.DataAccess`

Nested under non-service parents:

- `compute.Trigger` → `val` under `compute.Function`
- `api.ApiRoute`, `api.ErrorMapping`, `contracts.ApiContract` → `val` under `api.Api`

## CIM ownership

- Most behavioral and domain concepts remain root-owned (reused across capabilities, processes, aggregates)
- `cimorg.UbiquitousLanguageTerm` → `val` under `cimorg.BoundedContextCandidate`
- Process steps, decision rules, invariants → `val` under their parent container
- Cross-package links (`Command` → `AggregateCandidate`, `ProcessStep` → `Policy`) stay `ref`

## PSM (AWS) ownership

- Top-level deployable resources → `val` under `awspsmcore.SamStack.resources`
- Logical composites (`ApiGatewayApi`, `AwsLambdaFunction`, `RestApi`) → `val` child resources for routes, stages, permissions, event sources, etc.
- A top-level `AwsResource` may retain `readonly transient ref SamStack[?]#resources stack`; nested resources have no direct stack back-pointer
- `AwsPsmModel.allResources` is a derived flattening of nested resources for CFN ordering

## Audit workflow

1. Run `python mde/metamodels/tools/audit-ownership.py` after metamodel edits
2. Review `mde/metamodels/ownership-audit.csv` for `review:` candidates
3. Fix genuinely unreachable concrete types or duplicate containment semantics; a
   `multiple-containment-candidate` is not automatically a defect because one
   classifier may have alternative valid parents (for example, nested PSM
   resources or reusable expressions). Confirm the actual instance ownership
   and, where needed, add an explicit semantic validation rule.
4. Regenerate combined Ecore via `mde-cli`

## CVS / view alignment

- Entry containers (`ServerlessService`, `BoundedContextCandidate`, `SamStack`) use `visualRole: container`
- View palettes list only top-level creatable types for that view
- Contained details use drill-down via Ecore-derived `containmentPalettes`

The audit is inheritance-aware and understands numeric multiplicities. It treats
containment inherited from abstract supertypes as an ownership path and treats
semantic `owns*` references as ordinary cross-links unless they duplicate the
same class's actual containment owner.
