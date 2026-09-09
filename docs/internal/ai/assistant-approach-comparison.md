# Assistant strategy acceptance evidence

Updated: 2026-09-09

Varka exposes one chatbot and uses three internal outcomes. This document compares their current
evidence boundary; exact immutable run details are in `live-eval-gate-report.md` and reports under
`target/live-eval/`.

| Outcome                 | Intended use                                       | Current evidence                                                                                                                                           |
| ----------------------- | -------------------------------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `CONCEPTUAL_GENERATION` | Fresh CIM/PIM and coherent additive evolution      | Fresh CIM/PIM/source baselines pass. A persisted PIM evolution preserved 13 nodes and added 11 in one structurally valid checkpoint.                       |
| `INSPECT_AGENT`         | Surgical, selected, resumed, and destructive edits | Earlier live CIM evolution is positive; repeated preservation/deletion/reference/restart campaigns remain incomplete.                                      |
| `ANSWER`                | Informational model questions                      | Maps to enforced read-only `EXPLAIN_MODEL`; earlier answer-only live baseline completed without mutation. Repeated paraphrase campaign remains incomplete. |

## Conceptual evolution

The original conceptual path selected a small type set and generated one complete document. The
current implementation instead persists an LLM obligation ledger, exact type selection, a stable-ID
blueprint, private slice work items, and an independent obligation verdict before deterministic
compilation and one atomic checkpoint.

This eliminated a known false-positive gate where a structurally valid serverless PIM omitted
command behavior, a real event, workflow behavior, and a payment adapter. Current success requires
100% mandatory obligation coverage plus exact persisted API, function, event, datastore, payment,
observability, security, workflow, concrete-step, and relationship evidence.

## Latest Arvan evidence

- The validated model is `Gemma-4-31B-IT` through the OpenAI-compatible JSON-content adapter.
- Metamodel explanation, source attachment to CIM, fresh CIM, and fresh PIM scenarios have passed.
- On 2026-09-09 an existing-PIM cancellation/refund evolution succeeded in 268 seconds and 10
  provider calls. It preserved the 13-node order/payment/notification base, added 11 nodes, and
  committed revision 3 through one structurally valid checkpoint.
- Malformed associations, incompatible targets, missing required features, truncation, and
  read-only inverse references are covered by focused compiler/workflow tests.
- Focused adaptive routing and conceptual-workflow tests pass 47/47.

Historical failed runs did not publish invalid partial revisions. That is strong safety evidence,
but repeated live success and human usefulness remain separate release requirements.

## Conclusion

Neither mutation outcome is globally superior. Conceptual generation provides coherent private
planning for fresh models; the inspect agent provides surgical access to persisted models. Both
share exact Ecore contracts, private staging, structural-only validation, revision checks,
checkpoints, provider audit, and durable recovery.

Do not claim perfect reliability until repeated CIM, source-backed CIM, PIM, answer, transport,
restart, existing-model preservation, latency, and visual UI campaigns pass.
