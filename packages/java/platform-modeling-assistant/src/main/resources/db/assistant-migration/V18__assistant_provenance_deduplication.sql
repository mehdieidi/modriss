-- Evidence is an immutable ledger. Replayed/retried durable work must not duplicate an identical
-- source-grounded or inferred classification for the same committed element.
CREATE UNIQUE INDEX IF NOT EXISTS assistant_element_provenance_dedup_idx
    ON assistant_element_provenance
    (turn_id, element_id, COALESCE(source_unit_id, ''), kind, COALESCE(assumption, ''));
