package io.mehdieidi.modless.platform.assistant.agent;

/** One labeled prompt block so source, contracts, examples, and instructions stay separated. */
public record PromptBlock(String label, String content, boolean mandatory) {
  public PromptBlock {
    label = label == null || label.isBlank() ? "context" : label.trim();
    content = content == null ? "" : content.trim();
  }
}
