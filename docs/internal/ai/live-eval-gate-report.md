# Assistant Live Eval Gate Report

Generated: 2026-09-09T20:38:40+03:30

| Scenario                      | Level | Final state | Total latency (s) | First checkpoint (s) | Provider calls | Prompt tokens | Completion tokens | Checkpoint repairs | Provider retries | Coverage | Checkpoints | Initial nodes | Final nodes | Initial feature | Preserved | Added feature | Structural status | Message                                                                           |
| ----------------------------- | ----- | ----------- | ----------------: | -------------------: | -------------: | ------------: | ----------------: | -----------------: | ---------------: | -------- | ----------: | ------------: | ----------: | --------------- | --------- | ------------- | ----------------- | --------------------------------------------------------------------------------- |
| edit-existing-pim-add-pattern | PIM   | SUCCEEDED   |               268 |                  268 |             10 |         37963 |              4174 |                  0 |                0 | Complete |           1 |            13 |          24 | True            | True      | True          | True              | Applied a staged conceptual instance model and structurally validated the result. |

The edit was exercised through the frontend-facing HTTP API against Arvan's
`Gemma-4-31B-IT`. It preserved the existing order-intake, payment, and notification
elements and added the requested cancellation/refund design. Validation for assistant-generated
output was structural Ecore/EMF conformance only; no EVL semantic validator participated in the
apply path.
