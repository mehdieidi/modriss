package io.mehdieidi.varka.platform.assistant.domain;

/** Logical assistant model roles. */
public enum AssistantModelRole {
  /** Decomposes user intent into bounded workflow steps. */
  PLANNER,
  /** Extracts modeling evidence from requirements and event-storming source material. */
  SOURCE_ANALYST,
  /** Produces user-facing assistant responses. */
  RESPONDER,
  /** Compresses conversation history. */
  SUMMARIZER
}
