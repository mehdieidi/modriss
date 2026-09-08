package io.mehdieidi.varka.platform.assistant.agent;

import io.mehdieidi.varka.platform.kernel.PlatformException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/** Parses native structured-output fallback responses without accepting free-form actions. */
public final class AgentActionCodec {
  private final ObjectMapper mapper;

  public AgentActionCodec(ObjectMapper mapper) {
    this.mapper = mapper;
  }

  public AgentAction parse(String response) {
    try {
      JsonNode root = normalizeFlattenedEnvelope(mapper.readTree(jsonObject(response)));
      if (!root.isObject()
          || (!root.hasNonNull("action") && !root.hasNonNull("tool"))
          || !root.path("arguments").isObject()) {
        throw new IllegalArgumentException();
      }
      AgentAction.Kind kind =
          AgentAction.Kind.fromWire(root.path("action").asText(root.path("tool").asText()));
      return new AgentAction(kind, normalizeTerminalArguments(kind, root.path("arguments")));
    } catch (Exception ex) {
      throw new PlatformException(
          422, "Provider returned malformed AgentAction structured output.");
    }
  }

  /**
   * Decodes common compatible-provider wrappers without inventing user-facing content. Some JSON
   * gateways return a terminal answer as {@code content}, {@code text}, or a nested message even
   * when the requested action contract names the field {@code message}.
   */
  private JsonNode normalizeTerminalArguments(AgentAction.Kind kind, JsonNode arguments) {
    if (kind != AgentAction.Kind.ANSWER_USER && kind != AgentAction.Kind.ASK_USER) {
      return arguments;
    }
    if (!(arguments instanceof ObjectNode object)) return arguments;
    JsonNode message = object.get("message");
    String text = terminalText(message);
    if (text.isBlank()) {
      for (String alias : new String[] {"content", "text", "answer", "response"}) {
        text = terminalText(object.get(alias));
        if (!text.isBlank()) break;
      }
    }
    if (!text.isBlank() && (message == null || !message.isTextual())) {
      object.put("message", text);
    }
    return object;
  }

  private String terminalText(JsonNode value) {
    if (value == null || value.isNull()) return "";
    if (value.isTextual()) return value.asText().trim();
    if (value.isObject()) {
      for (String field : new String[] {"message", "content", "text", "answer", "response"}) {
        String text = terminalText(value.get(field));
        if (!text.isBlank()) return text;
      }
    }
    if (value.isArray()) {
      StringBuilder result = new StringBuilder();
      for (JsonNode item : value) {
        String text = terminalText(item);
        if (!text.isBlank()) {
          if (result.length() > 0) result.append('\n');
          result.append(text);
        }
      }
      return result.toString();
    }
    return "";
  }

  /**
   * Normalizes a compatible-provider envelope that emits action arguments beside {@code action}.
   *
   * <p>This changes protocol nesting only. It neither selects an action nor invents argument
   * values; the action allowlist and every downstream Ecore/tool validation remain authoritative.
   */
  private JsonNode normalizeFlattenedEnvelope(JsonNode root) {
    if (root == null
        || !root.isObject()
        || root.has("arguments")
        || (!root.hasNonNull("action") && !root.hasNonNull("tool"))) {
      return root;
    }
    var arguments = mapper.createObjectNode();
    root.properties()
        .forEach(
            entry -> {
              if (!"action".equals(entry.getKey()) && !"tool".equals(entry.getKey())) {
                arguments.set(entry.getKey(), entry.getValue().deepCopy());
              }
            });
    if (arguments.isEmpty()) return root;
    var envelope = mapper.createObjectNode();
    if (root.hasNonNull("action")) envelope.set("action", root.path("action").deepCopy());
    if (root.hasNonNull("tool")) envelope.set("tool", root.path("tool").deepCopy());
    envelope.set("arguments", arguments);
    return envelope;
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
