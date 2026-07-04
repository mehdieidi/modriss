package io.mehdieidi.modless.platform.assistant.spi;

import com.fasterxml.jackson.databind.JsonNode;
import io.mehdieidi.modless.platform.assistant.domain.context.AssistantModelContextTypes.AssistantModelContext;
import io.mehdieidi.modless.platform.kernel.ModelLevel;

/** Whitelisted assistant tool bridge for bounded agent loops. */
public interface AssistantToolBridge {

  /**
   * Binds model context used by element and validation tools for one agent loop turn.
   *
   * @param toolSession active tool session
   */
  void bindSession(ToolSession toolSession);

  /** Clears the active tool session. */
  void clearSession();

  /**
   * Returns and resets the number of tool invocations in the active session.
   *
   * @return tool call count
   */
  int consumeToolCallCount();

  /**
   * Binds a trace listener for user-visible tool events during the active session.
   *
   * @param trace trace listener, or {@code null} to clear
   */
  default void bindTrace(AssistantToolTrace trace) {}

  /** User-visible tool trace callbacks for realtime assistant events. */
  interface AssistantToolTrace {

    /** Publishes a tool invocation start event. */
    void started(String toolName);

    /** Publishes a tool invocation completion event. */
    void completed(String toolName);
  }

  /**
   * Active tool session context.
   *
   * @param level modeling level
   * @param modelJson current model JSON
   * @param context compact model context
   */
  record ToolSession(ModelLevel level, JsonNode modelJson, AssistantModelContext context) {}
}
