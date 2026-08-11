# Status and Roadmap

## Implemented

- Token-based registration, login, logout, and profile update
- Project CRUD, membership, and roles
- CIM, PIM, and PSM CRUD with revision-aware updates and patches
- JSON and XMI import/export
- Modeling configuration and visual editor metadata
- AntV G6 diagram canvas renderer
- Layout and persisted view layout
- CIM-to-PIM and PIM-to-AWS-PSM transformation profiles
- AWS PSM artifact generation
- Artifact browsing, editing, project ZIP download, and artifact ZIP download
- Change impact analysis across CIM, PIM, PSM models and generated artifacts
- PostgreSQL persistence and Flyway migrations
- Unified assistant routing, paper-style conceptual compilation, inspect/contract agent actions,
  source provenance, durable turns/work items, structural validation, checkpoints, undo, and
  authenticated SSE replay
- Reusable MDE Java runners and CLI tools
- Architecture diagrams, samples, and generated-project deployment guidance

## Partial or Evolving Areas

- Browser JSON validation and transformation rely on the JSON/XMI bridge and continue to be
  hardened against the full formal language.
- Generated projects require manual actions and protected-region implementation before production
  use.
- Production deployment security, scaling, monitoring, backup, and secret-management choices are
  environment responsibilities.
- Unified empty-library generation remains sensitive to provider output truncation; conceptual
  persisted-model evolution and repeated paraphrase acceptance are not yet production-complete.
- The adaptive `ANSWER` intention is not yet enforced as a read-only capability boundary.

## Planned Areas

- Admin workspace APIs
- Broader collaboration and versioning workflows
- Deployment automation beyond generated project scripts
- Reverse engineering
- Additional cloud providers

The admin HTTP surface under `/api/admin/**` returns `501` until implemented.
