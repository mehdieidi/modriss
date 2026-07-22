package io.mehdieidi.varka.platform.assistant.agent;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;

/** JSON Schema for the closed assistant action envelope. Provider clients add envelope metadata. */
public final class AgentActionSchema {
  private AgentActionSchema() {}

  public static String json() {
    return """
{"type":"object",
"additionalProperties":false,"required":["action","arguments"],"properties":{
"action":{"type":"string","enum":["commit_model_batch","plan_source_model","inspect_model","describe_types","answer_user","ask_user"]},
"arguments":{"type":"object","additionalProperties":false,"required":[],"properties":{}}}}
"""
        .replaceAll("\\s+", "");
  }

  /**
   * Returns the provider-facing function definitions. Each definition is closed and has an exact
   * argument schema; the backend remains the only executor.
   */
  public static Map<String, Object> toolSchema(String toolName) {
    if (!toolNames().contains(toolName))
      throw new IllegalArgumentException("Unknown assistant tool: " + toolName);
    String schema =
        switch (toolName) {
          case "respond_to_user" ->
              "{\"type\":\"object\",\"additionalProperties\":false,\"required\":[\"message\"],\"properties\":{\"message\":{\"type\":\"string\",\"minLength\":1}}}";
          case "inspect_model" ->
              "{\"type\":\"object\",\"additionalProperties\":false,\"required\":[\"id\"],\"properties\":{\"id\":{\"type\":\"string\"}}}";
          case "describe_types" ->
              "{\"type\":\"object\",\"additionalProperties\":false,\"required\":[\"names\"],\"properties\":{\"names\":{\"type\":\"array\",\"items\":{\"type\":\"string\"},\"maxItems\":8}}}";
          case "apply_draft_patch" ->
              """
{"type":"object","additionalProperties":false,
"required":["creates","updates","connections","deletions","evidence","planSummary","turnComplete"],"properties":{
"creates":{"type":"array","items":{"type":"object","additionalProperties":false,"required":["clientRef","eClass","attributes","owner","reference","provenance"],"properties":{"clientRef":{"type":"string"},"eClass":{"type":"string"},"attributes":{"type":"object","additionalProperties":true},"owner":{"type":"string"},"reference":{"type":"string"},"provenance":{"type":"string"}}}},
"updates":{"type":"array","items":{"type":"object","additionalProperties":false,"required":["elementId","attributes","preconditionHash"],"properties":{"elementId":{"type":"string"},"attributes":{"type":"object","additionalProperties":true},"preconditionHash":{"type":"string"}}}},
"connections":{"type":"array","items":{"type":"object","additionalProperties":false,"required":["source","reference","target"],"properties":{"source":{"type":"string"},"reference":{"type":"string"},"target":{"type":"string"}}}},
"deletions":{"type":"array","items":{"type":"object","additionalProperties":false,"required":["elementId","preconditionHash"],"properties":{"elementId":{"type":"string"},"preconditionHash":{"type":"string"}}}},
"evidence":{"type":"array","items":{"type":"object","additionalProperties":false,"required":["elementRef","sourceUnitId","requirementId","kind","assumption"],"properties":{"elementRef":{"type":"string"},"sourceUnitId":{"type":"string"},"requirementId":{"type":"string"},"kind":{"type":"string"},"assumption":{"type":"string"}}}},
"planSummary":{"type":"string"},"turnComplete":{"type":"boolean"}}}
"""
                  .replaceAll("\\s+", "");
          default ->
              "{\"type\":\"object\",\"additionalProperties\":false,\"required\":[],\"properties\":{}}";
        };
    try {
      return new ObjectMapper().readValue(schema, new TypeReference<>() {});
    } catch (java.io.IOException ex) {
      throw new IllegalStateException("Unable to build assistant tool schema", ex);
    }
  }

  /** Tool names intentionally exposed to an OpenAI-compatible provider. */
  public static java.util.List<String> toolNames() {
    return java.util.List.of(
        "inspect_model",
        "search_language",
        "describe_types",
        "apply_draft_patch",
        "complete_checkpoint",
        "respond_to_user");
  }
}
