package io.mehdieidi.modless.backend.assistant;

import java.util.List;

/**
 * A bounded user choice requested by the assistant.
 *
 * @param id stable choice ID
 * @param prompt user-facing prompt
 * @param options allowed options
 */
public record AssistantChoice(String id, String prompt, List<Option> options) {

  /** Applies immutable collection semantics. */
  public AssistantChoice {
    options = options == null ? List.of() : List.copyOf(options);
  }

  /**
   * One selectable option.
   *
   * @param id option ID
   * @param label option label
   * @param description option description
   */
  public record Option(String id, String label, String description) {}
}
