package io.mehdieidi.modless.platform.assistant.domain;

/** Structured per-turn diagnostics for assistant observability. */
public record AssistantTurnDiagnostics(
    String stage,
    int snippetCount,
    int providerCalls,
    int toolCalls,
    int repairAttempts,
    String outcome,
    long latencyMs) {

  /** Compatibility constructor for callers that do not yet split provider/tool calls. */
  public AssistantTurnDiagnostics(
      String stage,
      int snippetCount,
      int toolCalls,
      int repairAttempts,
      String outcome,
      long latencyMs) {
    this(stage, snippetCount, 0, toolCalls, repairAttempts, outcome, latencyMs);
  }
}
