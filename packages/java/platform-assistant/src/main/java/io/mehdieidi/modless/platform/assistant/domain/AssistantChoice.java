package io.mehdieidi.modless.platform.assistant.domain;

import java.util.List;

/**
 * A bounded user choice requested by the assistant.
 *
 * @param id stable choice ID
 * @param prompt user-facing prompt
 * @param options allowed options
 */
public record AssistantChoice(
    String id,
    String prompt,
    SelectionMode selectionMode,
    List<Option> options,
    boolean allowFreeText) {

  /** Applies immutable collection semantics. */
  public AssistantChoice {
    id = id == null ? "" : id.trim();
    prompt = prompt == null ? "" : prompt.trim();
    selectionMode = selectionMode == null ? SelectionMode.SINGLE : selectionMode;
    options = options == null ? List.of() : List.copyOf(options);
  }

  /** Compatibility constructor for single-select choices. */
  public AssistantChoice(String id, String prompt, List<Option> options) {
    this(id, prompt, SelectionMode.SINGLE, options, false);
  }

  /** Choice cardinality rendered by the client. */
  public enum SelectionMode {
    SINGLE,
    MULTIPLE
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
