package io.mehdieidi.modless.platform.assistant.application;

import io.mehdieidi.modless.platform.assistant.domain.AgentError;
import io.mehdieidi.modless.platform.kernel.PlatformException;

/** Maps platform failures to structured assistant error contracts. */
public final class AgentErrorResolver {

  private AgentErrorResolver() {}

  public static AgentError resolve(PlatformException failure, String phase, String turnId) {
    String code = codeFor(failure);
    return new AgentError(
        code,
        failure.getMessage(),
        false,
        retryable(code),
        phase == null ? "" : phase,
        turnId == null ? "" : turnId);
  }

  public static AgentError resolve(String code, String message, String phase, String turnId) {
    return new AgentError(code, message, false, retryable(code), phase, turnId);
  }

  private static String codeFor(PlatformException failure) {
    if (failure == null) {
      return "INTERNAL_ERROR";
    }
    String message = failure.getMessage() == null ? "" : failure.getMessage().toLowerCase();
    return switch (failure.status()) {
      case 409 ->
          message.contains("revision") || message.contains("stale") || message.contains("changed")
              ? "STALE_REVISION"
              : "INTERNAL_ERROR";
      case 429 ->
          message.contains("provider call budget")
              ? "TURN_BUDGET_EXCEEDED"
              : message.contains("tool budget") || message.contains("agent step")
                  ? "TURN_DEADLINE_EXCEEDED"
                  : "PROVIDER_UNAVAILABLE";
      case 499 -> "CANCELED";
      case 502 ->
          message.contains("schema") || message.contains("modeldelta")
              ? "SCHEMA_REJECTED"
              : "PROVIDER_UNAVAILABLE";
      case 504 -> "PROVIDER_TIMEOUT";
      case 400 ->
          message.contains("clarification") ? "CLARIFICATION_REQUIRED" : "STRUCTURE_REJECTED";
      case 422 ->
          message.contains("unknown metamodel")
                  || message.contains("modeldelta")
                  || message.contains("containment")
              ? "STRUCTURE_REJECTED"
              : "STRUCTURE_REJECTED";
      case 413 -> "CONTEXT_TOO_LARGE";
      case 503 -> "PROVIDER_UNAVAILABLE";
      default -> "INTERNAL_ERROR";
    };
  }

  private static boolean retryable(String code) {
    return switch (code) {
      case "STALE_REVISION",
          "PROVIDER_TIMEOUT",
          "PROVIDER_UNAVAILABLE",
          "CANCELED",
          "TURN_DEADLINE_EXCEEDED",
          "TURN_BUDGET_EXCEEDED" ->
          true;
      default -> false;
    };
  }
}
