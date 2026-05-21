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
