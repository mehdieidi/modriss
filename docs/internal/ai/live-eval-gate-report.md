# Assistant live-evaluation status

Updated: 2026-08-12

This is a curated index of the latest acceptance evidence. Raw reports under `target/` remain the
authoritative per-run record, including failed attempts.

## Inspect/contract agent baseline

| Fixture                 | Result | Latency | Calls | Prompt/completion tokens | Outcome                                                        |
| ----------------------- | ------ | ------: | ----: | -----------------------: | -------------------------------------------------------------- |
| `create-cim-library`    | Passed |   112 s |     4 |          22,193 / 15,748 | 46 nodes, structural validity                                  |
| `cim-feature-evolution` | Passed |   233 s |     8 |          56,904 / 36,843 | 106 final nodes, original feature preserved, new feature added |
| `source-to-cim-pantry`  | Passed |   118 s |     3 |          17,659 / 25,357 | 69 nodes, complete source coverage                             |
| `create-pim-serverless` | Passed |   163 s |    10 |          52,161 / 22,652 | 28 nodes, structural validity                                  |

## Conceptual strategy

| Fixture                 | Result                  | Evidence                                                                                                   |
| ----------------------- | ----------------------- | ---------------------------------------------------------------------------------------------------------- |
| `create-cim-library`    | Passed in optimized run | 91 s, 2 calls, 4,439 / 14,895 tokens, 40 nodes                                                             |
| `cim-feature-evolution` | Failed final acceptance | First feature checkpoint saved, but the second persisted-model update failed; earlier attempts also failed |
| `source-to-cim-pantry`  | Passed                  | 145 s, 3 calls, 5,373 / 12,240 tokens, complete coverage                                                   |
| `create-pim-serverless` | Passed                  | 124 s, 4 calls, 20,350 / 22,346 tokens, compiler repair then structural checkpoint                         |

## Unified production strategy

| Fixture                 | Result                | Evidence                                                                                                                                                                                             |
| ----------------------- | --------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `create-cim-library`    | Not reliably accepted | A 243 s run succeeded and saved a structural checkpoint, but the then-current four-call gate rejected it. The final eight-call rerun failed after 398 s on length-limited output with no checkpoint. |
| `cim-feature-evolution` | Passed                | 266 s, 9 calls, two checkpoints; prior feature preserved and new feature added through the agent path                                                                                                |
| `source-to-cim-pantry`  | Passed                | 121 s, 4 calls, complete source coverage/provenance                                                                                                                                                  |
| `create-pim-serverless` | Passed                | 85 s, 6 calls, structural checkpoint through conceptual generation                                                                                                                                   |

## Interpretation

- Provider connectivity alone is not acceptance.
- A structurally valid checkpoint is not proof of semantic usefulness.
- Source coverage is accounting, not EVL validity.
- A successful stochastic attempt does not erase a later gated failure.
- Current evidence justifies conceptual generation for bounded empty models and the inspect/contract
  agent for persisted updates.

Focused conceptual/compiler/provider/adaptive tests passed 25/25, and the strict backend mode test
passed 1/1 after these runs. No formatter or linter was used.

See [assistant-approach-comparison.md](assistant-approach-comparison.md) for the complete comparison
and exact report filenames.
