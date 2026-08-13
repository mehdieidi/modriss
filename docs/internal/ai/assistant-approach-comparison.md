# Assistant strategy acceptance evidence

Updated: 2026-08-13

Varka exposes one chatbot and uses three internal outcomes. This document compares their current
evidence boundary; exact immutable run details are in `live-eval-gate-report.md` and reports under
`target/live-eval/`.

| Outcome                 | Intended use                                                        | Current evidence                                                                                                                                                                    |
| ----------------------- | ------------------------------------------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `CONCEPTUAL_GENERATION` | Fresh empty CIM/PIM creation                                        | Strong focused tests, successful earlier CIM/source/PIM baselines, and reliable atomic failure. The new mandatory-obligation PIM profile has no clean semantic acceptance pass yet. |
| `INSPECT_AGENT`         | Existing models, selected elements, resumed work, destructive edits | Earlier live feature-evolution evidence is positive, but preservation/deletion/reference/restart campaigns remain incomplete.                                                       |
| `ANSWER`                | Informational model questions                                       | Maps to enforced read-only `EXPLAIN_MODEL`; earlier answer-only live baseline completed without mutation. Repeated paraphrase campaign remains incomplete.                          |

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

- Abstract-target handling was live-confirmed when DeepSeek chose concrete `TaskStep`; the run later
  failed during review and left the model unchanged.
- Obligation run 1 exposed and led to fixing JSON action-wrapping for ledger/review documents.
- Obligation run 2 privately generated 14 objects and was correctly blocked by the independent
  reviewer; review alternative semantics were then clarified.
- Obligation run 3 failed after repeated type-selection length truncations.
- Obligation run 4 confirmed compact candidate-only retries and reduced wall time to 239 seconds,
  but the final selected closure exceeded capacity. A fourth bounded selection attempt is now
  deployed and focused-test green but not yet live-confirmed.

No recent obligation-gated failure published a partial model revision. That is safety evidence, not
production-readiness evidence.

## Conclusion

Neither mutation outcome is globally superior. Conceptual generation provides coherent private
planning for fresh models; the inspect agent provides surgical access to persisted models. Both
share exact Ecore contracts, private staging, structural-only validation, revision checks,
checkpoints, provider audit, and durable recovery.

Do not claim perfect reliability until repeated CIM, source-backed CIM, PIM, answer, transport,
restart, existing-model preservation, latency, and visual UI campaigns pass.
