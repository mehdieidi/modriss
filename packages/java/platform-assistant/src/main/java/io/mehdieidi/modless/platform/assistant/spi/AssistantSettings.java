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

  /** Maximum semantic operations allowed for automatic apply. */
  int maxAutoApplyOperations();

  /** Whether assistant turns should enforce EVL semantic validation in addition to structure. */
  boolean semanticValidationEnabled();

  /** Maximum characters per retrieved snippet. */
  int maxSnippetChars();

  /** Maximum characters in the system prompt. */
  int maxSystemChars();

  /** Minimum retrieval slots reserved for tier-1 schema contracts. */
  int reservedSchemaSnippets();

  /** Outbound AI request timeout. */
  Duration requestTimeout();

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
