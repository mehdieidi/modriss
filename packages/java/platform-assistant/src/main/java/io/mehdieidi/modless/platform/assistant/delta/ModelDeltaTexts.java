package io.mehdieidi.modless.platform.assistant.delta;

import java.util.stream.Collectors;

/** Compact text summaries of ModelDelta drafts for repair prompts. */
final class ModelDeltaTexts {

  private ModelDeltaTexts() {}

  static String summary(ModelDelta delta) {
    if (delta == null) {
      return "none";
    }
    StringBuilder builder = new StringBuilder();
    builder.append("kind=").append(delta.kind());
    builder.append(" elements=").append(delta.elements().size());
    builder.append(" references=").append(delta.references().size());
    if (!delta.elements().isEmpty()) {
      builder
          .append("\nElements:\n")
          .append(
              delta.elements().stream()
                  .limit(20)
                  .map(
                      element ->
                          "- "
                              + element.localId()
                              + " "
                              + element.eClass()
                              + " placement="
                              + (element.placement() == null
                                  ? "none"
                                  : element.placement().ownerId()
                                      + "."
                                      + element.placement().referenceName()))
                  .collect(Collectors.joining("\n")));
    }
    if (!delta.references().isEmpty()) {
      builder
          .append("\nReferences:\n")
          .append(
              delta.references().stream()
                  .limit(20)
                  .map(
                      reference ->
                          "- "
                              + reference.sourceId()
                              + "."
                              + reference.referenceName()
                              + " -> "
                              + reference.targetId())
                  .collect(Collectors.joining("\n")));
    }
    return builder.toString();
  }
}
