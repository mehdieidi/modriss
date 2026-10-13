# CIM to PIM ETL Transformation

Entry point:

- `cim-to-pim.etl`

The transformation is split by concern:

- `root-scaffolding.etl` - PIM root, trace/readiness, environments, implementation profile.
- `boundaries-security.etl` - services, principals, identity providers, external adapters.
- `domain-data.etl` - schemas, data stores, data models, data protection, privacy, compliance.
- `behavior-contracts.etl` - commands, queries, events, functions, contracts, API routes, access
  patterns.
- `process-policy.etl` - policies, decision tables, workflows, states, transitions, security
  constraints.
- `integration-deployment.etl` - flows, triggers, data access, deployment units, configuration,
  readiness closure.

Shared EOL libraries live in `lib/` and provide deterministic naming, trace/readiness creation, enum
mappings, PIM builders, and cross-rule resolution helpers.

Expected model aliases follow the transformation specification: `CIM`, `CIMORG`, `CIMDOMAIN`,
`CIMBEHAVIOR`, `CIMPROCESS`, `CIMGOV`, `CIMTRANSFORM`, `PIM`, `DEPLOY`, `COMPUTE`, `API`,
`CONTRACTS`, `DATA`, `INTEGRATION`, `WORKFLOW`, `EXTERNAL`, `POLICY`, `SECURITY`, `CONFIG`,
`KERNEL`, and `PIMTYPES`.

# Iterative synchronization

The platform always runs this ETL module against an empty temporary PIM resource. Generated
elements receive SHA-256-derived IDs from the `cim-to-pim` namespace using the immutable source
element ID plus the target role. A source rename therefore updates generated presentation fields
without changing target identity.

The raw result is `NewGenerated`. The platform merges it with the user-editable `Working` PIM by
using the previous raw result as `Base` (ancestor). Independent Working edits are retained,
independent generated edits are applied, and edits to the same feature are reported as explicit
conflicts. Legacy Working models without a Base are reported as `BOOTSTRAP_REQUIRED` and are never
silently replaced.

The baseline is always the untouched raw output of the most recent successfully accepted ETL
generation. It is never the user-refined merged working model.
