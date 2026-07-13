# ADR-001: Progressive valid checkpoints

Assistant mutations are committed as independently structurally valid model revisions. A timeout,
cancellation, provider failure, or restart keeps every persisted checkpoint; it never rolls a
model back to an uncommitted preview.
