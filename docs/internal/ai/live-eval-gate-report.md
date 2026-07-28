# Assistant Live Eval Gate Report

Generated: 2026-07-29T02:15:00+03:30

This report records the latest local live-eval state for source-backed CIM generation through the
real chatbot multipart upload path. It is not yet a CI gate.

| Scenario             | Level | Final state | Total latency (s) | Provider calls | Repairs | Coverage | Checkpoints | Saved elements | Validation valid | Message                 |
| -------------------- | ----- | ----------- | ----------------: | -------------: | ------: | -------- | ----------: | -------------: | ---------------- | ----------------------- |
| `story-v1-single.md` | CIM   | `SUCCEEDED` |               133 |              4 |       0 | 100%     |           1 |             16 | `false`          | Model checkpoint saved. |

Validation issues from the latest run:

| Severity  | Constraint                             | Summary                                                                                                                           |
| --------- | -------------------------------------- | --------------------------------------------------------------------------------------------------------------------------------- |
| `ERROR`   | `CIMModelHasSemanticCore`              | Semantic validation does not see the required `BusinessGoal`, `Actor`, and `BusinessCapability` core in the live persisted model. |
| `WARNING` | `CIMModelDeclaresBusinessScope`        | Root model scope fields are not yet filled.                                                                                       |
| `WARNING` | `ModelElementHasExplanation`           | Root model lacks an explanatory summary/description.                                                                              |
| `WARNING` | `TraceableElementHasSourceOrRationale` | Root model lacks a source reference or rationale.                                                                                 |

Current interpretation:

- The durable turn lifecycle, upload path, source splitting, checkpointing, and source coverage are
  working for the one-story fixture.
- The remaining blocker is the discrepancy between unit-tested semantic-core synthesis and live
  persisted model validation. Inspect the stored JSON and generated XMI for the live revision before
  expanding the gate to larger fixtures.

Focused regression status:

```text
SourceUnitSplitterTest
OpenAiCompatibleAssistantModelProviderTest
AgentTurnLoopTest
AgentModelToolsTest

26 tests, 0 failures
```
