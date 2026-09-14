package io.mehdieidi.modriss.platform.transformation.synchronization;

import java.util.List;

/** Public synchronization result returned through MDE job payloads. */
public record SynchronizationResult(
    SynchronizationStatus status,
    String sessionId,
    String targetModelId,
    int incomingChanges,
    int preservedUserChanges,
    int autoMergedChanges,
    int conflicts,
    int generatedAdditions,
    int generatedDeletions,
    List<ModelConflict> conflictDetails,
    List<String> validationFindings) {

  public SynchronizationResult {
    conflictDetails = conflictDetails == null ? List.of() : List.copyOf(conflictDetails);
    validationFindings = validationFindings == null ? List.of() : List.copyOf(validationFindings);
  }
}
