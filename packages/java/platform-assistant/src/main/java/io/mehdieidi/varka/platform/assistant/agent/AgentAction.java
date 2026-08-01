package io.mehdieidi.varka.platform.assistant.agent;

import tools.jackson.databind.JsonNode;

/** Strict provider response envelope for the explicit single-agent tool loop. */
public record AgentAction(Kind tool, JsonNode arguments) {
  public AgentAction {
    tool = tool == null ? Kind.ASK_USER : tool;
  }

  /** The only tools visible to a provider. */
  public enum Kind {
    ANALYZE_SOURCE_UNITS("analyze_source_units"),
    PLAN_CIM_BLUEPRINT("plan_cim_blueprint"),
    PLAN_MODEL_EDIT("plan_model_edit"),
    COMMIT_MODEL_BATCH("commit_model_batch"),
    PLAN_SOURCE_MODEL("plan_source_model"),
    INSPECT_MODEL("inspect_model"),
    DESCRIBE_TYPES("describe_types"),
    ANSWER_USER("answer_user"),
    ASK_USER("ask_user");

    private final String wireName;

    Kind(String wireName) {
      this.wireName = wireName;
    }

    public String wireName() {
      return wireName;
    }

    public static Kind fromWire(String value) {
      for (Kind kind : values()) if (kind.wireName.equals(value)) return kind;
      throw new IllegalArgumentException("Unknown agent tool: " + value);
    }
  }
}
