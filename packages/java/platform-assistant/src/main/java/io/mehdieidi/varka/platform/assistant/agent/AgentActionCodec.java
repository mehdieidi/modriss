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
      JsonNode root = mapper.readTree(jsonObject(response));
      if (!root.isObject()
          || (!root.hasNonNull("action") && !root.hasNonNull("tool"))
          || !root.path("arguments").isObject()) {
        throw new IllegalArgumentException();
      }
      return new AgentAction(
          AgentAction.Kind.fromWire(root.path("action").asText(root.path("tool").asText())),
          root.path("arguments"));
    } catch (Exception ex) {
      throw new PlatformException(
          422, "Provider returned malformed AgentAction structured output.");
    }
  }

  /**
   * Recovers the single JSON object from providers that wrap otherwise-valid structured output in a
   * Markdown fence or a short preamble. This is protocol parsing, not natural-language intent
   * matching; the decoded object is still validated strictly above.
   */
  private String jsonObject(String response) {
    String value = response == null ? "" : response.trim();
    if (value.startsWith("```")) {
      int firstNewline = value.indexOf('\n');
      int closingFence = value.lastIndexOf("```");
      if (firstNewline >= 0 && closingFence > firstNewline) {
        value = value.substring(firstNewline + 1, closingFence).trim();
      }
    }
    if (value.startsWith("{")) return value;
    int start = value.indexOf('{');
    if (start < 0) throw new IllegalArgumentException();
    boolean quoted = false;
    boolean escaped = false;
    int depth = 0;
    for (int index = start; index < value.length(); index++) {
      char character = value.charAt(index);
      if (quoted) {
        if (escaped) escaped = false;
        else if (character == '\\') escaped = true;
        else if (character == '"') quoted = false;
        continue;
      }
      if (character == '"') quoted = true;
      else if (character == '{') depth++;
      else if (character == '}' && --depth == 0) return value.substring(start, index + 1);
    }
    throw new IllegalArgumentException();
  }
}
