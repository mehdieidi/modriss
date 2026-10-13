# Evidence and Trace Ledger

Record each task occurrence separately. A definition ID or generated sentence
does not prove execution. Evidence must state its kind (planned, simulated,
repository-checked, empirically observed), artifact path, revision/digest, scope,
producer/reviewer, outcome, relevant dependency revisions and reuse rationale.
Changed dependencies, unresolved conflicts and expired exceptions reopen
affected acceptance. Never reuse a passing result merely because its path is
unchanged. Analytical case records cannot authorize production gates.

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
