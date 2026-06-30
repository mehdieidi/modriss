package io.mehdieidi.modless.platform.assistant.subset;

import java.util.Locale;

/** Selects the assistant modeling protocol used for LLM mutation turns. */
public enum AssistantModelingStrategy {
  /** Existing provider-neutral semantic operation protocol. */
  SEMANTIC_PATCH,
  /** JSON Schema guided partial model subset protocol. */
  MODEL_SUBSET;

  /** Parses environment/property values while using the schema-guided subset planner by default. */
  public static AssistantModelingStrategy from(String value) {
    String normalized =
        value == null || value.isBlank() ? "model-subset" : value.trim().toLowerCase(Locale.ROOT);
    return switch (normalized.replace('_', '-')) {
      case "model-subset", "subset", "json-subset", "schema-subset" -> MODEL_SUBSET;
      case "semantic-patch", "patch", "operations", "actions" -> SEMANTIC_PATCH;
      default -> MODEL_SUBSET;
    };
  }
}
