# Status and Roadmap

## Implemented

- Token-based registration, login, logout, and profile update
- Project CRUD, membership, and roles
- CIM, PIM, and PSM CRUD with revision-aware updates and patches
- JSON and XMI import/export
- Modeling configuration and visual editor metadata
- Layout and persisted view layout
- CIM-to-PIM and PIM-to-AWS-PSM transformation profiles
- AWS PSM artifact generation
- Artifact browsing, editing, project ZIP download, and artifact ZIP download
- PostgreSQL persistence and Flyway migrations
- Assistant sessions, retrieval, memory, proposals, validation, approval, rejection, undo, SSE, and
  WebSocket events
- Reusable MDE Java runners and CLI tools
- Architecture diagrams, samples, and generated-project deployment guidance

## Partial or Evolving Areas

- Browser JSON validation and transformation rely on the JSON/XMI bridge and continue to be
  hardened against the full formal language.
- Generated projects require manual actions and protected-region implementation before production
  use.
- Production deployment security, scaling, monitoring, backup, and secret-management choices are
  environment responsibilities.

## Planned Areas

- Impact analysis
- Admin workspace APIs
- Broader collaboration and versioning workflows
- Deployment automation beyond generated project scripts
- Reverse engineering
- Additional cloud providers

Planned HTTP surfaces under `/api/impact/**` and `/api/admin/**` return `501`
until implemented.
