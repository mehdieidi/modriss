package io.mehdieidi.modless.platform.assistant.retrieval;

import java.util.List;

/** User-visible and durable diagnostics for retrieval/contract expansion. */
public record RetrievalDiagnostics(
    List<String> requestedConcepts,
    List<String> selectedContracts,
    List<String> missingContracts,
    String embeddingProvider,
    List<String> warnings) {

  public RetrievalDiagnostics {
    requestedConcepts = requestedConcepts == null ? List.of() : List.copyOf(requestedConcepts);
    selectedContracts = selectedContracts == null ? List.of() : List.copyOf(selectedContracts);
    missingContracts = missingContracts == null ? List.of() : List.copyOf(missingContracts);
    embeddingProvider = embeddingProvider == null ? "" : embeddingProvider;
    warnings = warnings == null ? List.of() : List.copyOf(warnings);
  }
}
