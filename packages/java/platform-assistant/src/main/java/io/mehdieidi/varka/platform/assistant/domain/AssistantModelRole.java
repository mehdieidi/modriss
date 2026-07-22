package io.mehdieidi.varka.platform.assistant.domain;

/** Explicit responsibilities used by the bounded assistant workflow. */
public enum AssistantModelRole {
  /** Directs a request into an answer or modeling workflow. */
  DIRECTOR,
  /** Produces a bounded model patch or repair. */
  MODELER,
  /** Reviews coverage and structural diagnostics. */
  CRITIC,
  /** Compresses durable context. */
  SUMMARIZER,
  /** Produces user-facing assistant responses. */
  RESPONDER
}
