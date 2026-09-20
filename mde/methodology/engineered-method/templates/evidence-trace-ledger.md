# Evidence and Trace Ledger

Use one row per trace relation or controlled decision. The ledger can be
implemented in a repository or tracker; this template defines the minimum
information rather than requiring a spreadsheet.

| Trace ID | Upstream kind/ID/revision | Relation                                                                                  | Downstream kind/ID/revision | Owner | Evidence/status | Last verified |
| -------- | ------------------------- | ----------------------------------------------------------------------------------------- | --------------------------- | ----- | --------------- | ------------- |
|          |                           | derives / satisfies / transforms / generates / verifies / deploys / observes / supersedes |                             |       |                 |               |

## Finding and change routing

| Finding/change ID | Trigger and evidence | Earliest authoritative source                                    | Affected downstream traces | Decision/owner | Closure evidence |
| ----------------- | -------------------- | ---------------------------------------------------------------- | -------------------------- | -------------- | ---------------- |
|                   |                      | CIM / PIM / PSM / generator / code / release-operations / method |                            |                |                  |

## Integrity checks

- Every accepted increment and release points to exact input/output revisions.
- No generated draft is marked accepted solely because automation completed.
- Every blocking finding is closed, deferred, or accepted by named authority.
- Emergency downstream changes are reconciled into the authoritative source.
- Superseded and archived evidence remains identifiable according to retention
  rules.
