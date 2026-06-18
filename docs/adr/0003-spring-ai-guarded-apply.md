# ADR 0003: Spring AI with Guarded Apply

- **Status:** Accepted
- **Date:** 2026-03-19
- **Deciders:** Modless core team

## Context

Natural-language assistance can accelerate modeling but introduces risk: hallucinated elements,
invalid metamodel changes, and unreviewed mutations. The platform must support explanation and
proposal flows without compromising model integrity.

## Decision

Integrate **Spring AI** with explicit operating modes and a guarded apply pipeline:

| Mode            | Behavior                                                  |
| --------------- | --------------------------------------------------------- |
| `EXPLAIN_ONLY`  | Answers questions; no model mutations                     |
| `PROPOSE`       | Creates reviewable proposals without applying             |
| `GUARDED_APPLY` | Applies only compiled, validated, risk-classified patches |

Additional controls:

- AI disabled by default (`MODLESS_AI_ENABLED=false`)
- Rate limits, circuit breaker, retries, and optional outbound proxy
- Proposals require approval for risky changes; undo via inverse patches
- Provider credentials supplied only through environment variables or secret managers

## Consequences

### Positive

- Aligns AI assistance with formal metamodel and EVL constraints
- Operators can disable AI entirely in sensitive environments
- Failure modes (rate limit, circuit open) are explicit HTTP errors with metrics

### Negative

- Higher implementation complexity than a thin chat wrapper
- Provider latency and cost require monitoring
- Multiple models (planner/responder/summarizer) increase configuration surface

## Alternatives considered

- **Direct LLM-to-XMI rewriting** — rejected due to validation bypass risk
- **External-only chatbot with no platform integration** — rejected; poor UX for modeling workflows
