package io.mehdieidi.varka.platform.assistant.domain;

import io.mehdieidi.varka.platform.model.application.ModelService;
import java.time.Instant;
import java.util.List;

/**
 * Audited autonomous model change that passed backend validation before apply.
 *
 * @param id proposal ID
 * @param affectedElements stable affected element IDs
 * @param patch backend-owned model operation IR
 * @param inversePatch inverse executable patch
 * @param validation validation preview
 * @param riskLevel risk level
 * @param citations supporting catalog or validation citations
 * @param createdAt creation time
 */
public record AssistantProposal(
    String id,
    List<String> affectedElements,
    SemanticModelPatch patch,
    List<ModelService.ModelPatchOperation> inversePatch,
    AssistantValidationSummary validation,
    RiskLevel riskLevel,
    List<String> citations,
    Instant createdAt) {

  /** Applies immutable collection semantics. */
  public AssistantProposal {
    affectedElements = affectedElements == null ? List.of() : List.copyOf(affectedElements);
    patch = patch == null ? new SemanticModelPatch(List.of()) : patch;
    inversePatch = inversePatch == null ? List.of() : List.copyOf(inversePatch);
    citations = citations == null ? List.of() : List.copyOf(citations);
    riskLevel = riskLevel == null ? RiskLevel.HIGH : riskLevel;
    createdAt = createdAt == null ? Instant.now() : createdAt;
  }

  /** Assistant proposal risk levels. */
  public enum RiskLevel {
    /** Low-risk additive or explanatory change. */
    LOW,
    /** Ambiguous or structurally sensitive change. */
    MEDIUM,
    /** Destructive, bulk, or high-impact change. */
    HIGH
  }
}
