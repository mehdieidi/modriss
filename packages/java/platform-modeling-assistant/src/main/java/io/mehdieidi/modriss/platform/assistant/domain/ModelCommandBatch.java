package io.mehdieidi.modriss.platform.assistant.domain;

import java.util.List;
import java.util.Map;
import tools.jackson.databind.JsonNode;

/** Provider-friendly, stable-ID command payload accepted by {@code commit_model_batch}. */
public record ModelCommandBatch(
    List<Create> creates,
    List<Update> updates,
    List<Connection> connections,
    List<Deletion> deletions,
    List<Evidence> evidence,
    String planSummary,
    boolean turnComplete) {
  public ModelCommandBatch {
    creates = creates == null ? List.of() : List.copyOf(creates);
    updates = updates == null ? List.of() : List.copyOf(updates);
    connections = connections == null ? List.of() : List.copyOf(connections);
    deletions = deletions == null ? List.of() : List.copyOf(deletions);
    evidence = evidence == null ? List.of() : List.copyOf(evidence);
    planSummary = planSummary == null ? "" : planSummary.trim();
  }

  /** Returns whether this batch changes the semantic model. Evidence alone is not a mutation. */
  public boolean hasMutations() {
    return !(creates.isEmpty()
        && updates.isEmpty()
        && connections.isEmpty()
        && deletions.isEmpty());
  }

  public record Create(
      String clientRef,
      String eClass,
      Map<String, JsonNode> attributes,
      String owner,
      String reference,
      String provenance) {}

  public record Update(
      String elementId, Map<String, JsonNode> attributes, String preconditionHash) {}

  public record Connection(String source, String reference, String target) {}

  /**
   * A destructive operation must be tied to the exact inspected element state. The durable
   * confirmation flow may replay a batch, but it must never delete a subsequently edited item.
   */
  public record Deletion(String elementId, String preconditionHash) {
    public Deletion(String elementId) {
      this(elementId, null);
    }
  }

  public record Evidence(
      String elementRef,
      String sourceUnitId,
      String requirementId,
      String kind,
      String assumption) {}
}
