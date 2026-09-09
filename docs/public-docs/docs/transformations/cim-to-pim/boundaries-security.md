# CIM → PIM — Boundaries Security

Boundary and security refinement decides where provider-independent services begin and who may act within them. Bounded contexts and capabilities become service ownership, actors and roles become principals, and external systems become adapters. The important design choice is that this page preserves business ownership and trust boundaries instead of producing anonymous functions first.

Source module: `mde/transformations/cim-to-pim/boundaries-security.etl`.

## Reading this page

A transformation rule is not a validation constraint: it decides whether and how a source element contributes to the target model. Read the guard as a routing decision, the target table as the model-level result, the behavior section as the important semantic side effects, and the trace/manual-decision information as the hand-off to review and later phases.

---

## Supporting ETL operations

These operations are not independent source-to-target rules, but they materially shape the result. They derive defaults, create secondary resources, cache correspondences, or resolve relationships after the main rule has run.

| Operation                             | Role                                            | Source                                                      |
| ------------------------------------- | ----------------------------------------------- | ----------------------------------------------------------- |
| `capabilityInBoundedContext`          | Computes capability in bounded context.         | `mde/transformations/cim-to-pim/boundaries-security.etl:8`  |
| `inferExternalProtocolFamily`         | Derives infer external protocol family.         | `mde/transformations/cim-to-pim/boundaries-security.etl:16` |
| `ensurePrivilegedPrincipalPermission` | Creates ensure privileged principal permission. | `mde/transformations/cim-to-pim/boundaries-security.etl:31` |

---

## `BoundedContext2Service`

**Source:** `bc` — `CIMORG!BoundedContextCandidate`  
**Target:** `s` — `DEPLOY!ServerlessService`  
**Source location:** `mde/transformations/cim-to-pim/boundaries-security.etl:52`

### Why this rule exists

A bounded context is a business boundary, so it becomes a PIM service rather than a collection of unrelated generated functions. The rule carries its name and boundary definition into ownership metadata, giving later function, data, and integration rules a place to attach their results.

### When the rule runs

There is no explicit guard, so every source instance of the declared type is eligible for this mapping. Eligibility does not guarantee that every optional relationship or downstream target can be resolved.

### What it creates

- `s` (`DEPLOY!ServerlessService`): Generated serverless service (s).

### Important behavior encoded in the rule

The rule directly assigns: `s.id`, `s.name`, `s.boundaryType`, `s.responsibility`, `s.ownerTeam`, `s.businessCapabilityRef`, `s.externallyExposed`, `s.ownsData`.
Trace identifiers emitted here: `TR-010`. These identifiers are useful when following the generated element back to the originating CIM/PIM decision.

### How to troubleshoot or repair it

Verify that the bounded context candidate is present and semantically complete, then rerun the transformation. If the target is missing or incomplete, inspect the source attributes named in the rule body, the referenced helper operation, and the post-phase that resolves its relationships before changing the ETL itself.

The trace record is the best first diagnostic: it tells you whether the target was created, which transformation rule claimed it, and what source element it came from.

### Authoritative source

Read the complete ETL rule at `mde/transformations/cim-to-pim/boundaries-security.etl:52`. The documentation summarizes its purpose and observable effects; the ETL body remains the authority for exact assignments and helper calls.

---

## `Capability2Service`

**Source:** `cap` — `CIMORG!BusinessCapability`  
**Target:** `s` — `DEPLOY!ServerlessService`  
**Source location:** `mde/transformations/cim-to-pim/boundaries-security.etl:69`

### Why this rule exists

Capabilities are alternate service-boundary seeds. This rule creates a service only when the capability is not already inside a bounded context, preventing nested business ownership from producing duplicate PIM services while still allowing an unbounded capability to define one.

### When the rule runs

The rule is conditional. It runs only when this guard is true; a false guard means the source element belongs to another refinement path or requires a different provider mapping.

```etl
guard : not capabilityInBoundedContext(cap)
```

### What it creates

- `s` (`DEPLOY!ServerlessService`): Generated serverless service (s).

### Important behavior encoded in the rule

The rule directly assigns: `s.id`, `s.name`, `s.boundaryType`, `s.responsibility`, `s.ownerTeam`, `s.businessCapabilityRef`, `s.externallyExposed`, `s.ownsData`.
Trace identifiers emitted here: `TR-010`. These identifiers are useful when following the generated element back to the originating CIM/PIM decision.

### How to troubleshoot or repair it

Verify the business capability instance first: the guard shown above must evaluate to true for this rule to run. If it should run but does not, check the guarded links, enum values, and earlier transformation outputs; if it is intentionally out of scope, use the trace/readiness report to record that decision rather than adding a dummy target.

The trace record is the best first diagnostic: it tells you whether the target was created, which transformation rule claimed it, and what source element it came from.

### Authoritative source

Read the complete ETL rule at `mde/transformations/cim-to-pim/boundaries-security.etl:69`. The documentation summarizes its purpose and observable effects; the ETL body remains the authority for exact assignments and helper calls.

---

## `Actor2Principal`

**Source:** `a` — `CIMORG!Actor`  
**Target:** `p` — `SECURITY!Principal`  
**Source location:** `mde/transformations/cim-to-pim/boundaries-security.etl:89`

### Why this rule exists

An actor becomes a PIM principal because later authorization policies need an identity vocabulary. The mapping preserves actor kind, trust, public/external status, and role context so API and function security can be derived from business actors rather than provider-specific users.

### When the rule runs

There is no explicit guard, so every source instance of the declared type is eligible for this mapping. Eligibility does not guarantee that every optional relationship or downstream target can be resolved.

### What it creates

- `p` (`SECURITY!Principal`): Generated principal (p).
- Secondary objects created in the rule body: `SECURITY!IdentityProvider`.

### Important behavior encoded in the rule

The rule directly assigns: `p.id`, `p.name`, `p.principalKind`, `p.externalRef`, `p.privileged`, `idp.id`, `idp.name`, `idp.identityKind`, `idp.federationRequired`, `idp.mfaRequired`, `idp.tokenType`, `idp.userAttributeRequirements`.
Trace identifiers emitted here: `TR-020`. These identifiers are useful when following the generated element back to the originating CIM/PIM decision.
Manual decisions raised by this rule: `TR-020`. These are intentional hand-off points, not transformation failures; resolve them in the model review/readiness workflow.

### How to troubleshoot or repair it

Start with the manual decision(s) `TR-020` and complete the requested provider or business choice. Then re-run the transformation and validate the resulting target. If the rule did not produce an object, inspect whether the actor actually satisfies its guard and whether the required upstream correspondence exists.

The trace record is the best first diagnostic: it tells you whether the target was created, which transformation rule claimed it, and what source element it came from.

### Authoritative source

Read the complete ETL rule at `mde/transformations/cim-to-pim/boundaries-security.etl:89`. The documentation summarizes its purpose and observable effects; the ETL body remains the authority for exact assignments and helper calls.

---

## `Role2Principal`

**Source:** `r` — `CIMORG!Role`  
**Target:** `p` — `SECURITY!Principal`  
**Source location:** `mde/transformations/cim-to-pim/boundaries-security.etl:130`

### Why this rule exists

A business role is promoted to a principal when it carries permission intent. The rule retains the role's permission summary and creates the seed from which least-privilege review can proceed, without claiming that a business role is already an AWS IAM role.

### When the rule runs

There is no explicit guard, so every source instance of the declared type is eligible for this mapping. Eligibility does not guarantee that every optional relationship or downstream target can be resolved.

### What it creates

- `p` (`SECURITY!Principal`): Generated principal (p).
- Secondary objects created in the rule body: `SECURITY!AuthorizationPolicy`.

### Important behavior encoded in the rule

The rule directly assigns: `p.id`, `p.name`, `p.principalKind`, `p.privileged`, `p.externalRef`, `authz.id`, `authz.name`, `authz.policyScope`, `authz.productionRequired`, `authz.authorizationRequired`, `authz.ruleExpression`, `authz.roleOrScopeRequired`, `authz.resourceLevelAuthorization`.
Trace identifiers emitted here: `TR-020`. These identifiers are useful when following the generated element back to the originating CIM/PIM decision.
Manual decisions raised by this rule: `TR-020`. These are intentional hand-off points, not transformation failures; resolve them in the model review/readiness workflow.

### How to troubleshoot or repair it

Start with the manual decision(s) `TR-020` and complete the requested provider or business choice. Then re-run the transformation and validate the resulting target. If the rule did not produce an object, inspect whether the role actually satisfies its guard and whether the required upstream correspondence exists.

The trace record is the best first diagnostic: it tells you whether the target was created, which transformation rule claimed it, and what source element it came from.

### Authoritative source

Read the complete ETL rule at `mde/transformations/cim-to-pim/boundaries-security.etl:130`. The documentation summarizes its purpose and observable effects; the ETL body remains the authority for exact assignments and helper calls.

---

## `ExternalSystem2Adapter`

**Source:** `es` — `CIMORG!ExternalSystem`  
**Target:** `a` — `EXTERNAL!ExternalAdapter`  
**Source location:** `mde/transformations/cim-to-pim/boundaries-security.etl:162`

### Why this rule exists

External systems become adapters at the PIM boundary because protocols, credentials, and failure behavior belong to an integration contract. The rule carries purpose, trust, endpoint/protocol hints, and exchanged information, while leaving provider binding to the PSM.

### When the rule runs

There is no explicit guard, so every source instance of the declared type is eligible for this mapping. Eligibility does not guarantee that every optional relationship or downstream target can be resolved.

### What it creates

- `a` (`EXTERNAL!ExternalAdapter`): Generated external adapter (a).
- Secondary objects created in the rule body: `EXTERNAL!ExternalEndpoint`, `CONFIG!CredentialRequirement`.

### Important behavior encoded in the rule

The rule directly assigns: `a.id`, `a.name`, `a.responsibility`, `ep.id`, `ep.name`, `ep.externalSystemName`, `ep.protocolFamily`, `ep.endpointDescription`, `ep.expectedSla`, `ep.credentialsRequired`, `ep.rateLimitedByProvider`, `ep.privateNetworkRequired`, `a.endpoint`, `a.resilience`, `a.observability`, `cred.id` ….
Trace identifiers emitted here: `TR-030`. These identifiers are useful when following the generated element back to the originating CIM/PIM decision.
Manual decisions raised by this rule: `TR-030`. These are intentional hand-off points, not transformation failures; resolve them in the model review/readiness workflow.

### How to troubleshoot or repair it

Start with the manual decision(s) `TR-030` and complete the requested provider or business choice. Then re-run the transformation and validate the resulting target. If the rule did not produce an object, inspect whether the external system actually satisfies its guard and whether the required upstream correspondence exists.

The trace record is the best first diagnostic: it tells you whether the target was created, which transformation rule claimed it, and what source element it came from.

### Authoritative source

Read the complete ETL rule at `mde/transformations/cim-to-pim/boundaries-security.etl:162`. The documentation summarizes its purpose and observable effects; the ETL body remains the authority for exact assignments and helper calls.

---
