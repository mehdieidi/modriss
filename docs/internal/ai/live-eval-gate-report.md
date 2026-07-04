# Assistant live eval quality gate report

Generated: 2026-07-04T14:53:41.309267900Z

Mode: live provider

## Summary

- quality=PASS, structural=100.0%, modelDelta=100.0%, sourceCoverage=100.0%, retrievalRecall=100.0%, avgRepair=0.00, avgProviderCalls=1.17, p95LatencyMs=42199
- latency total=6, p50=13072ms, p95=42199ms, providerP50=13034ms, providerP95=42198ms, backendP50=19ms, backendP95=185ms, avgProviderCalls=1.17
- Overall: **PASS**

## Quality gates

| Gate                      | Threshold                | Actual  | Status |
| ------------------------- | ------------------------ | ------- | ------ |
| Structural pass rate      | >= 95%                   | 100.0%  | PASS   |
| ModelDelta success rate   | 100%                     | 100.0%  | PASS   |
| Retrieval contract recall | >= 90%                   | 100.0%  | PASS   |
| Source coverage rate      | 100% when chunks present | 100.0%  | PASS   |
| Average repair attempts   | < 0.5                    | 0.00    | PASS   |
| Average provider calls    | <= 3.0                   | 1.17    | PASS   |
| p95 latency               | <= 300000ms              | 42199ms | PASS   |

## Per-category baseline

```
Assistant eval baseline
- analysis: 1/1 passed, avgToolCalls=1.0
- create-empty: 1/1 passed, avgToolCalls=1.0
- retrieval: 1/1 passed, avgToolCalls=1.0
- cim-source-document: 1/1 passed, avgToolCalls=2.0
- cim: 1/1 passed, avgToolCalls=1.0
- psm: 1/1 passed, avgToolCalls=1.0
```

## Failing prompts

None.

## Docker compose smoke

| Check                                            | Result                      | Notes                            |
| ------------------------------------------------ | --------------------------- | -------------------------------- |
| Backend readiness (`/actuator/health/readiness`) | PASS                        | `http://127.0.0.1:8080`          |
| Modeling config (`/api/modeling/config`)         | PASS                        | Contains `levels`                |
| Test                                             | `AssistantComposeSmokeTest` | `MODLESS_RUN_COMPOSE_SMOKE=true` |

## Manual exploratory scenarios (UX — not automated)

Run against compose stack + frontend (`http://127.0.0.1:8082`). Check off when verified:

- [ ] Messy notes → draft CIM (no formal sections in attachment)
- [ ] "Make this model more complete" on sparse CIM
- [ ] "Add refund support" with selected payment-flow elements
- [ ] PSM deployment-risk explain (no mutation)
- [ ] Mixed-language workshop notes
- [ ] Domain text plus instruction-like source content
- [ ] Cancel a long source-document turn
- [ ] Manual model edit while provider is still responding

Latency expectation: each turn completes within **5 minutes** (`MODLESS_AI_TURN_TIMEOUT` default).
