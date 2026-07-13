package io.mehdieidi.varka.platform.assistant.agent;

import io.mehdieidi.varka.platform.kernel.PlatformException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Parses native structured-output fallback responses without accepting free-form actions. */
public final class AgentActionCodec {
  private final ObjectMapper mapper;

  public AgentActionCodec(ObjectMapper mapper) {
    this.mapper = mapper;
  }

  public AgentAction parse(String response) {
    try {
      JsonNode root = mapper.readTree(response == null ? "" : response);
      if (!root.isObject() || !root.hasNonNull("tool")) throw new IllegalArgumentException();
      return new AgentAction(
          AgentAction.Kind.fromWire(root.path("tool").asText()), root.path("arguments"));
    } catch (Exception ex) {
      throw new PlatformException(
          422, "Provider returned malformed AgentAction structured output.");
    }
  }
}
