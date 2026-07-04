package io.mehdieidi.modless.platform.assistant.spi;

import java.time.Duration;

/** Provider-neutral assistant runtime settings consumed by platform services. */
public interface AssistantSettings {

  /** Whether outbound AI calls are allowed. */
  boolean enabled();

  /** Maximum validator-guided replanning passes per mutation. */
  int validationRepairAttempts();

  /** Maximum retrieved snippets passed to the provider per turn. */
  int maxContextSnippets();

  /** Maximum whitelisted tool calls per turn. */
  int maxToolCalls();

  /** Maximum characters per retrieved snippet. */
  int maxSnippetChars();

  /** Maximum characters in the system prompt. */
  int maxSystemChars();

  /** Minimum retrieval slots reserved for tier-1 schema contracts. */
  int reservedSchemaSnippets();

  /** Outbound AI request timeout. */
  Duration requestTimeout();

  /** Overall assistant turn timeout. */
  default Duration turnTimeout() {
    return requestTimeout();
  }

  /** Maximum agentic planner loop iterations per mutation turn. */
  default int maxAgentSteps() {
    return 8;
  }

  /** Maximum tool calls allowed in one agent loop step. */
  default int maxToolCallsPerStep() {
    return 4;
  }

  /** Approximate maximum prompt tokens per provider call. */
  default int maxPromptTokens() {
    return 24000;
  }

  /** Maximum approximate source tokens in one source chunk. */
  default int maxSourceChunkTokens() {
    return 4000;
  }

  /** Maximum source chunks processed in one turn. */
  default int maxSourceChunksPerTurn() {
    return 24;
  }

  /** Whether client turn idempotency keys are required. */
  default boolean requireIdempotencyKey() {
    return true;
  }

  /** Rate-limit and circuit-breaker settings. */
  Hardening hardening();

  /** Production hardening settings. */
  interface Hardening {

    /** Maximum requests per user and window. */
    int perUserRequestsPerWindow();

    /** Rate limit window. */
    Duration rateLimitWindow();

    /** Consecutive provider failures before opening circuit. */
    int circuitFailureThreshold();

    /** Open-circuit duration. */
    Duration circuitOpenDuration();

    /** Provider retry attempts per call. */
    int providerRetryAttempts();

    /** Retry backoff. */
    Duration retryBackoff();

    /** Recent durable messages included in prompt context. */
    int recentMessageWindow();
  }
}
