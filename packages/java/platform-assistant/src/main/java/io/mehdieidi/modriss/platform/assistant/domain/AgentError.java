package io.mehdieidi.modriss.platform.assistant.domain;

/** Structured assistant failure surfaced to clients and durable turn execution records. */
public record AgentError(
    String code,
    String message,
    boolean modelChanged,
    boolean retryable,
    String phase,
    String diagnosticId) {

  public AgentError {
    code = code == null ? "INTERNAL_ERROR" : code;
    message = message == null ? "" : message;
    phase = phase == null ? "" : phase;
    diagnosticId = diagnosticId == null ? "" : diagnosticId;
  }
}
