# Assistant-state migration and rollback

`V14__assistant_baseline.sql` replaces assistant conversation/runtime state. It deliberately does
not modify `projects`, `models`, model revisions, XMI payloads, or metamodel data.

Before deployment, take a PostgreSQL backup of the existing assistant tables. Apply the migration
with the assistant disabled, run Flyway and deterministic structural checks, then enable a small
cohort. If rollback is needed, disable AI and restore the assistant-table backup; persisted model
checkpoints are normal valid model revisions and are not removed.

The baseline creates durable threads/messages, turns/events, checkpoints, source units,
provenance, provider calls, and action audits. Later assistant migrations through V29 add
continuation lineage, requirement mapping, context caches, source blueprints, stored provider
prompts, workflows, private work items, source facts, validation-attempt records, and idempotent
provider-call keys. Current conceptual workflow plans store obligation ledgers, selected types,
blueprints, accounting, diagnostics, and accepted obligation verdicts. The V17 cleanup is
intentionally destructive only for obsolete assistant proposal state.
