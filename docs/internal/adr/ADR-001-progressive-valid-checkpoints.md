# ADR-001: Progressive valid checkpoints

Assistant mutations are committed as independently structurally valid model revisions. The
inspect/contract path may publish coherent progressive checkpoints. The current conceptual path
persists private work items but publishes one atomic checkpoint only after mandatory-obligation
review and structural validation. A timeout, cancellation, provider failure, or restart keeps every
committed checkpoint and never exposes an uncommitted private preview.
