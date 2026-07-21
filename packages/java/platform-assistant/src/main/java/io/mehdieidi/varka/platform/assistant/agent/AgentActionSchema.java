package io.mehdieidi.varka.platform.assistant.agent;

/** JSON Schema for the closed assistant action envelope. Provider clients add envelope metadata. */
public final class AgentActionSchema {
  private AgentActionSchema() {}

  public static String json() {
    return """
{"type":"object",
"additionalProperties":false,"required":["action","arguments"],"properties":{
"action":{"type":"string","enum":["commit_model_batch","plan_source_model","inspect_model","describe_types","answer_user","ask_user"]},
"arguments":{"type":"object"}}}
"""
        .replaceAll("\\s+", "");
  }
}
