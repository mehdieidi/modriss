# ADR 0003: Spring AI with Guarded Apply

- **Status:** Accepted
- **Date:** 2026-03-19
- **Deciders:** Modless core team

## Context

Natural-language assistance can accelerate modeling but introduces risk: hallucinated elements,
invalid metamodel changes, and unreviewed mutations. The platform must support explanation and
proposal flows without compromising model integrity.

## Decision

Integrate **Spring AI** through one autonomous modeling-agent workflow. The LLM emits a structured
answer, clarification, or semantic patch. The backend is the authority for compiling semantic
operations, checking the current Ecore language, running structural and EVL validation, applying
valid mutations, and persisting audit/undo records. No mutation is applied unless validation passes.

Additional controls:

- AI disabled by default (`MODLESS_AI_ENABLED=false`)
- Rate limits, circuit breaker, retries, and optional outbound proxy
- All proposals require approval; undo uses a validated inverse patch
- Clarification questions and pending turns are durable and resumable
- OpenAI-compatible and Gemini providers use runtime fallback with independent circuit breakers
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
