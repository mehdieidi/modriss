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

## Staged conceptual follow-up

After replacing monolithic conceptual output with the stable-ID blueprint/slice/review protocol,
`create-cim-library` passed a live unified gate against Arvan `DeepSeek-V4-Flash`: `SUCCEEDED`, 304
seconds, 8 audited calls, 21,720 prompt tokens, 23,286 completion tokens, one checkpoint, and
structural validity. Earlier failed staged attempts remain in `target/`; this single pass does not
yet satisfy the repeated-run release gate.

The subsequent final two-object verification on 2026-08-12 failed safely after 130 seconds: two
slice responses were length-limited, four of eight private objects were durably staged, the
conceptual review reserve was reached after five conceptual calls (seven calls including adaptive
routing), and no checkpoint or partial model revision was published. The active follow-up profile
therefore uses one DeepSeek object per slice, stricter bounded JSON collections, smaller
stage-specific completion limits, and explicit recovery/review capacity.

A later one-object run generated and durably staged all seven planned objects without publishing a
partial revision, but its 14-call ceiling was consumed by routing, blueprint correction, two slice
corrections, and review. The LLM reviewer then correctly rejected a missing planned policy
relationship, with no call left to apply its correction. The active ceiling is now 18 so review and
compiler correction cannot be starved by earlier bounded recoveries. Focused tests pass; this final
budget still awaits a successful live rerun and the repeated acceptance campaign.

The first 18-call run then completed all slices, review, and one compiler correction, but correctly
published no checkpoint because the blueprint had selected `DomainEntity` without planning its
required `identityAttributes` and `primaryIdentityAttribute` references to `InformationItem`.
Required writable containment and non-containment reference closure is now checked against Ecore at
blueprint time, where DeepSeek can add the stable dependency IDs or choose another suitable EClass.
That final invariant is covered by focused tests and the rebuilt backend is healthy, but it has not
yet completed a live acceptance run.

The immediate rerun failed safely before slicing when DeepSeek's blueprint correction reached a
temporary 2,500-token stage cap. The cap has been restored to the previously proven 8,000-token
blueprint headroom; object count, schema collections, one-object slices, provider calls, and total
turn time remain bounded. No committed model checkpoint was created by this attempt.

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
