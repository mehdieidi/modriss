# Assistant Live Eval Gate Report

Generated: 2026-07-29T02:15:00+03:30

This report records the latest local live-eval state for source-backed CIM generation through the
real chatbot multipart upload path. It is not yet a CI gate.

| Scenario             | Level | Final state | Total latency (s) | Provider calls | Repairs | Coverage | Checkpoints | Saved elements | Assistant gate | Message                 |
| -------------------- | ----- | ----------- | ----------------: | -------------: | ------: | -------- | ----------: | -------------: | -------------- | ----------------------- |
| `story-v1-single.md` | CIM   | `SUCCEEDED` |               133 |              4 |       0 | 100%     |           1 |             16 | structural     | Model checkpoint saved. |

The assistant gate is structural Ecore/EMF conformance only. EVL validation issues are not part of
chatbot acceptance and should be captured only when a live eval explicitly asks for post-checkpoint
semantic review feedback.

Current interpretation:

- The durable turn lifecycle, upload path, source splitting, checkpointing, and source coverage are
  working for the one-story fixture.
- The assistant apply boundary must remain structural-only; expanding the gate should focus on
  durable turn reliability, source coverage, and EMF/Ecore conformance.

Focused regression status:

```text
SourceUnitSplitterTest
OpenAiCompatibleAssistantModelProviderTest
AgentTurnLoopTest
AgentModelToolsTest

26 tests, 0 failures
```
