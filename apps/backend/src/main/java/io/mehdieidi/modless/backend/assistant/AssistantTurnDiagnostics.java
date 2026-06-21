package io.mehdieidi.modless.backend.assistant;

/** Structured per-turn diagnostics for assistant observability. */
public record AssistantTurnDiagnostics(
    String stage,
    int snippetCount,
    int toolCalls,
    int repairAttempts,
    String outcome,
    long latencyMs) {}
