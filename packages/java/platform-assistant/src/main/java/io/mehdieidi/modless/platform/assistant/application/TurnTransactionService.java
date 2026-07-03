package io.mehdieidi.modless.platform.assistant.application;

import com.fasterxml.jackson.databind.node.ObjectNode;
import io.mehdieidi.modless.platform.assistant.delta.StructuralValidationGate;
import io.mehdieidi.modless.platform.assistant.domain.AssistantProposal;
import io.mehdieidi.modless.platform.assistant.domain.AssistantTurnPlan;
import io.mehdieidi.modless.platform.assistant.domain.AssistantValidationSummary;
import io.mehdieidi.modless.platform.assistant.domain.context.AssistantModelContextTypes.AssistantModelContext;
import io.mehdieidi.modless.platform.assistant.patch.AssistantPatchCompiler;
import io.mehdieidi.modless.platform.assistant.provider.AssistantModelProvider;
import io.mehdieidi.modless.platform.assistant.session.AssistantSessionStore;
import io.mehdieidi.modless.platform.assistant.spi.AssistantMemoryStore;
import io.mehdieidi.modless.platform.assistant.spi.AssistantRealtimePublisher;
import io.mehdieidi.modless.platform.identity.domain.UserRecord;
import io.mehdieidi.modless.platform.model.application.ModelService;
import io.mehdieidi.modless.platform.model.domain.ModelRecord;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/** Applies one validated assistant model change as an atomic turn transaction boundary. */
public class TurnTransactionService {

  private final AssistantPatchCompiler patchCompiler;
  private final StructuralValidationGate structuralValidation;
  private final ModelService models;
  private final AssistantMemoryStore memory;
  private final AssistantRealtimePublisher realtime;

  public TurnTransactionService(
      AssistantPatchCompiler patchCompiler,
      StructuralValidationGate structuralValidation,
      ModelService models,
      AssistantMemoryStore memory,
      AssistantRealtimePublisher realtime) {
    this.patchCompiler = patchCompiler;
    this.structuralValidation = structuralValidation;
    this.models = models;
    this.memory = memory;
    this.realtime = realtime;
  }

  /**
   * Recompiles, previews, structurally validates, persists, audits, and publishes a model update.
   */
  public Result applyValidated(
      UserRecord user,
      AssistantSessionStore.AssistantSession session,
      String threadId,
      ModelRecord targetModel,
      AssistantTurnPlan acceptedPlan,
      List<AssistantModelProvider.ContextSnippet> snippets,
      AssistantModelContext context,
      Callbacks callbacks) {
    callbacks.checkActive();
    return models
        .modelLocks()
        .withModelLock(
            targetModel.id(),
            Duration.ofSeconds(30),
            () ->
                applyValidatedLocked(
                    user,
                    session,
                    threadId,
                    targetModel,
                    acceptedPlan,
                    snippets,
                    context,
                    callbacks));
  }

  private Result applyValidatedLocked(
      UserRecord user,
      AssistantSessionStore.AssistantSession session,
      String threadId,
      ModelRecord planningTarget,
      AssistantTurnPlan acceptedPlan,
      List<AssistantModelProvider.ContextSnippet> snippets,
      AssistantModelContext context,
      Callbacks callbacks) {
    long reloadStarted = System.nanoTime();
    ModelRecord targetModel = models.get(user, session.level(), planningTarget.id());
    callbacks.phase(
        "apply_target_model_reloaded",
        reloadStarted,
        "modelId",
        targetModel.id(),
        "planningRevision",
        planningTarget.revision(),
        "loadedRevision",
        targetModel.revision());
    if (planningTarget.revision() != targetModel.revision()) {
      callbacks.phase(
          "apply_stale_revision_rejected",
          reloadStarted,
          "modelId",
          targetModel.id(),
          "planningRevision",
          planningTarget.revision(),
          "loadedRevision",
          targetModel.revision());
      return Result.rejected(targetModel, staleRevisionSummary(planningTarget, targetModel));
    }

    long compileStarted = System.nanoTime();
    AssistantPatchCompiler.CompiledPatch compiled =
        patchCompiler.compile(targetModel.modelJson(), acceptedPlan.patch());
    callbacks.phase(
        "apply_patch_compiled_against_target",
        compileStarted,
        "semanticOperations",
        acceptedPlan.patch().operations().size(),
        "jsonPatchOperations",
        compiled.patch().size(),
        "affectedElements",
        compiled.affectedElements().size());

    long previewStarted = System.nanoTime();
    ObjectNode preview = patchCompiler.apply(targetModel.modelJson(), compiled);
    callbacks.phase("apply_preview_created", previewStarted);

    long validationStarted = System.nanoTime();
    AssistantValidationSummary validation =
        validationSummary(structuralValidation.validate(session.level(), preview));
    callbacks.phase(
        "apply_preview_validated",
        validationStarted,
        "structurallyValid",
        validation.structurallyValid(),
        "mandatoryPassed",
        validation.mandatoryPassed(),
        "issueCount",
        validation.issues().size(),
        "optionalIssues",
        validation.optionalIssues());
    if (!validation.structurallyValid() || !validation.mandatoryPassed()) {
      return Result.rejected(targetModel, validation);
    }

    callbacks.publishValidatedPreview(targetModel, compiled);
    callbacks.checkActive();

    long xmiStarted = System.nanoTime();
    byte[] sourceXmi = models.exportModel(session.level(), preview, "xmi");
    callbacks.phase(
        "source_xmi_regenerated", xmiStarted, "bytes", sourceXmi == null ? 0 : sourceXmi.length);

    long patchStarted = System.nanoTime();
    ModelRecord updated =
        models.patch(
            user,
            session.level(),
            targetModel.id(),
            targetModel.name(),
            compiled.patch(),
            targetModel.revision());
    callbacks.phase(
        "model_service_patch_completed",
        patchStarted,
        "modelId",
        targetModel.id(),
        "baseRevision",
        targetModel.revision(),
        "patchOperations",
        compiled.patch().size(),
        "updatedRevision",
        updated == null ? null : updated.revision());
    if (updated == null) {
      updated = targetModel;
    } else if (sourceXmi != null && sourceXmi.length > 0) {
      long attachStarted = System.nanoTime();
      models.attachSourceXmi(updated, sourceXmi);
      callbacks.phase("source_xmi_attached", attachStarted, "modelId", updated.id());
    }

    AssistantProposal.RiskLevel risk = riskLevel(compiled, validation);
    AssistantProposal proposal =
        new AssistantProposal(
            UUID.randomUUID().toString(),
            compiled.affectedElements(),
            acceptedPlan.patch(),
            compiled.inversePatch(),
            validation,
            risk,
            retrievalCitations(snippets, context),
            Instant.now());
    memory.clearPendingInteraction(threadId);
    long persistenceStarted = System.nanoTime();
    memory.saveProposal(
        threadId, session.projectId(), updated.id(), updated.revision(), proposal, "APPLIED");
    memory.markProposalApplied(proposal.id(), updated.id(), updated.revision());
    memory.appendAudit(
        proposal.id(),
        session.projectId(),
        user.id(),
        "APPLIED",
        Map.of(
            "operationCount",
            proposal.patch().operations().size(),
            "validationPassed",
            true,
            "autoApplied",
            true));
    callbacks.phase(
        "proposal_persisted",
        persistenceStarted,
        "proposalId",
        proposal.id(),
        "modelId",
        updated.id(),
        "revision",
        updated.revision());
    realtime.publish(
        session.id(),
        "model.updated",
        Map.of(
            "modelId", updated.id(),
            "revision", updated.revision(),
            "proposalId", proposal.id()));
    return Result.applied(updated, proposal, risk);
  }

  private AssistantValidationSummary validationSummary(ModelService.ValidationResult result) {
    List<AssistantValidationSummary.Issue> issues =
        result == null
            ? List.of()
            : result.issues().stream()
                .map(
                    issue ->
                        new AssistantValidationSummary.Issue(
                            issue.severity(),
                            assistantConstraintName(issue.constraint()),
                            issue.elementId(),
                            issue.message()))
                .toList();
    int optional =
        (int) issues.stream().filter(issue -> "WARNING".equalsIgnoreCase(issue.severity())).count();
    boolean mandatory =
        issues.stream().noneMatch(issue -> "ERROR".equalsIgnoreCase(issue.severity()));
    return new AssistantValidationSummary(
        result != null && result.valid(), mandatory, optional, issues);
  }

  private AssistantValidationSummary staleRevisionSummary(
      ModelRecord planningTarget, ModelRecord loadedTarget) {
    return new AssistantValidationSummary(
        false,
        false,
        0,
        List.of(
            new AssistantValidationSummary.Issue(
                "ERROR",
                "StaleRevision",
                loadedTarget.id(),
                "Model revision changed from "
                    + planningTarget.revision()
                    + " to "
                    + loadedTarget.revision()
                    + " before apply.")));
  }

  private String assistantConstraintName(String constraint) {
    if (constraint == null || constraint.isBlank()) {
      return "StructuralValidation";
    }
    String trimmed = constraint.trim();
    int separator = Math.max(trimmed.lastIndexOf("::"), trimmed.lastIndexOf('.'));
    return separator >= 0 && separator + 1 < trimmed.length()
        ? trimmed.substring(separator + 1)
        : trimmed;
  }

  private AssistantProposal.RiskLevel riskLevel(
      AssistantPatchCompiler.CompiledPatch compiled, AssistantValidationSummary validation) {
    boolean destructive =
        compiled.patch().stream().anyMatch(operation -> "remove".equals(operation.op()));
    if (destructive) {
      return AssistantProposal.RiskLevel.HIGH;
    }
    return compiled.patch().size() > 1 || validation.optionalIssues() > 0
        ? AssistantProposal.RiskLevel.MEDIUM
        : AssistantProposal.RiskLevel.LOW;
  }

  private List<String> retrievalCitations(
      List<AssistantModelProvider.ContextSnippet> snippets, AssistantModelContext context) {
    List<String> result =
        (snippets == null ? List.<AssistantModelProvider.ContextSnippet>of() : snippets)
            .stream()
                .map(snippet -> snippet.source() + "#" + snippet.title())
                .filter(value -> !value.isBlank())
                .distinct()
                .limit(12)
                .collect(Collectors.toCollection(ArrayList::new));
    if (context != null) {
      context.validationIssues().stream()
          .map(AssistantValidationSummary.Issue::constraint)
          .filter(value -> value != null && !value.isBlank())
          .limit(6)
          .forEach(result::add);
    }
    return result.stream().distinct().toList();
  }

  /** Transaction callbacks for orchestration-owned progress and cancellation. */
  public interface Callbacks {
    void phase(String event, long phaseStartedNanos, Object... keyValues);

    void publishValidatedPreview(
        ModelRecord targetModel, AssistantPatchCompiler.CompiledPatch compiled);

    void checkActive();
  }

  /** Apply result. */
  public record Result(
      boolean applied,
      ModelRecord model,
      AssistantProposal proposal,
      AssistantValidationSummary validation,
      AssistantProposal.RiskLevel risk) {

    static Result applied(
        ModelRecord model, AssistantProposal proposal, AssistantProposal.RiskLevel risk) {
      return new Result(true, model, proposal, proposal.validation(), risk);
    }

    static Result rejected(ModelRecord model, AssistantValidationSummary validation) {
      return new Result(false, model, null, validation, AssistantProposal.RiskLevel.HIGH);
    }
  }
}
