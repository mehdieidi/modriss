package io.mehdieidi.modless.platform.assistant.delta;

import com.fasterxml.jackson.databind.JsonNode;
import io.mehdieidi.modless.platform.assistant.agent.ModelingAgent;
import io.mehdieidi.modless.platform.assistant.application.AssistantValidationFeedbackResolver;
import io.mehdieidi.modless.platform.assistant.domain.AssistantModelRole;
import io.mehdieidi.modless.platform.assistant.domain.AssistantTurnPlan;
import io.mehdieidi.modless.platform.assistant.domain.SemanticModelPatch;
import io.mehdieidi.modless.platform.assistant.domain.context.AssistantModelContextTypes.AssistantModelContext;
import io.mehdieidi.modless.platform.assistant.patch.AssistantMetamodelSchemaService;
import io.mehdieidi.modless.platform.assistant.patch.AssistantPatchCompleter;
import io.mehdieidi.modless.platform.assistant.provider.AssistantModelProvider;
import io.mehdieidi.modless.platform.assistant.spi.AssistantCatalog;
import io.mehdieidi.modless.platform.assistant.spi.AssistantSettings;
import io.mehdieidi.modless.platform.kernel.ModelLevel;
import io.mehdieidi.modless.platform.kernel.PlatformException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Owns deterministic and LLM-guided ModelDelta repair behavior. */
public class DeltaRepairService {

  private final ModelingAgent modelingAgent;
  private final AssistantValidationFeedbackResolver feedbackResolver;
  private final AssistantMetamodelSchemaService schemas;
  private final AssistantCatalog catalogs;
  private final AssistantPatchCompleter patchCompleter;
  private final AssistantSettings settings;

  public DeltaRepairService(
      ModelingAgent modelingAgent,
      AssistantValidationFeedbackResolver feedbackResolver,
      AssistantMetamodelSchemaService schemas,
      AssistantCatalog catalogs,
      AssistantPatchCompleter patchCompleter,
      AssistantSettings settings) {
    this.modelingAgent = modelingAgent;
    this.feedbackResolver = feedbackResolver;
    this.schemas = schemas;
    this.catalogs = catalogs;
    this.patchCompleter = patchCompleter;
    this.settings = settings;
  }

  /** Applies backend-owned structural defaults derived from exact validation feedback. */
  public AssistantTurnPlan deterministicRepair(
      ModelLevel level,
      AssistantModelContext context,
      AssistantTurnPlan plan,
      List<String> feedback) {
    SemanticModelPatch repaired =
        patchCompleter.repairFromValidationFeedback(
            level,
            plan.patch(),
            existingTypes(context),
            feedbackResolver.missingRequiredFeatures(feedback));
    if (repaired.operations().equals(plan.patch().operations())) {
      return plan;
    }
    return new AssistantTurnPlan(
        plan.intent(), plan.kind(), plan.message(), plan.questions(), repaired);
  }

  /** Rebuilds a rejected mutation with safe metamodel defaults through the ModelDelta agent. */
  public AssistantTurnPlan replanWithSafeDefaults(
      ModelLevel level,
      String systemPrompt,
      String rootMessage,
      AssistantModelContext context,
      List<AssistantModelProvider.ContextSnippet> snippets,
      AssistantTurnPlan rejected,
      List<String> feedback) {
    return replanWithSafeDefaults(
            level, systemPrompt, rootMessage, context, snippets, rejected, feedback, List.of())
        .plan();
  }

  public ModelingAgent.AgentLoopResult replanWithSafeDefaults(
      ModelLevel level,
      String systemPrompt,
      String rootMessage,
      AssistantModelContext context,
      List<AssistantModelProvider.ContextSnippet> snippets,
      AssistantTurnPlan rejected,
      List<String> feedback,
      List<String> candidateTypes) {
    String rejectedSummary =
        rejected == null || rejected.patch().operations().isEmpty()
            ? "none"
            : operationSummary(rejected.patch());
    String feedbackSummary =
        feedback == null || feedback.isEmpty()
            ? ""
            : "\nValidator feedback to correct (fix only these issues):\n"
                + feedback.stream().limit(8).collect(Collectors.joining("\n"));
    AssistantModelProvider.AssistantPrompt prompt =
        new AssistantModelProvider.AssistantPrompt(
            AssistantModelRole.PLANNER,
            systemPrompt
                + "\n\nThis is a mandatory replanning pass. Return kind=MODEL_DELTA only."
                + "\nNever return CLARIFICATION or ANSWER for this mutation."
                + "\nDo not ask the user about IDs, UUIDs, architecture style, runtime language,"
                + " package manager, persistence technology, API style, or layout."
                + "\nInfer safe enum defaults from the retrieved metamodel contracts and starter"
                + " model. The backend will apply the validated change to the canvas."
                + "\nModel only what the user asked for. Use as many elements and references as the"
                + " request genuinely requires, including required nested contracts and attributes."
                + "\nReturn a focused ModelDelta that fixes the listed issues only."
                + " Prefer attributeUpdates and small element additions over full regeneration."
                + "\nRejected prior plan summary: "
                + rejectedSummary
                + feedbackSummary,
            "Original request:\n"
                + (rootMessage == null ? "" : rootMessage)
                + "\n\nReturn one ModelDelta JSON object that satisfies the request using safe"
                + " defaults. Omit unchanged elements.",
            snippets);
    return modelingAgent.repair(
        level, null, context, prompt, candidateTypes == null ? List.of() : candidateTypes);
  }

  /** Performs one schema-parse repair pass when provider output is not valid ModelDelta JSON. */
  public ModelingAgent.AgentLoopResult repairSchemaFailure(
      ModelLevel level,
      String systemPrompt,
      String requestMessage,
      JsonNode baseModel,
      AssistantModelContext context,
      List<AssistantModelProvider.ContextSnippet> snippets,
      String parseFailure,
      List<String> candidateTypes,
      int repairNumber) {
    String failureText =
        parseFailure == null || parseFailure.isBlank()
            ? "AI assistant returned invalid ModelDelta JSON."
            : parseFailure;
    List<AssistantModelProvider.ContextSnippet> repairContext = new ArrayList<>();
    repairContext.addAll(snippets == null ? List.of() : snippets);
    String requestWithFeedback =
        "Original user request:\n"
            + (requestMessage == null ? "" : requestMessage)
            + "\n\nModelDelta schema parse failure:\n"
            + failureText
            + "\n\nReturn one valid ModelDelta JSON object only. Required root fields: intent,"
            + " kind, message. Use exact Ecore eClass names, placement.referenceName for"
            + " containment, and references for writable links. For large source-backed CIM"
            + " models, include the most important actors, commands, events, policies, and"
            + " entities first (up to about 40 elements) instead of an oversized payload.";
    AssistantModelProvider.AssistantPrompt prompt =
        new AssistantModelProvider.AssistantPrompt(
            AssistantModelRole.PLANNER,
            systemPrompt
                + "\nThis is schema-parse repair pass "
                + repairNumber
                + " of "
                + settings.validationRepairAttempts()
                + ". Return strict ModelDelta JSON with no prose or Markdown.",
            requestWithFeedback,
            repairContext);
    return modelingAgent.repair(
        level, baseModel, context, prompt, candidateTypes == null ? List.of() : candidateTypes);
  }

  /** Performs one compile-error repair pass with the rejected ModelDelta draft in context. */
  public ModelingAgent.AgentLoopResult repairCompileFailure(
      ModelLevel level,
      String systemPrompt,
      String requestMessage,
      JsonNode baseModel,
      AssistantModelContext context,
      List<AssistantModelProvider.ContextSnippet> snippets,
      ModelDelta rejectedDelta,
      List<String> feedback,
      List<String> candidateTypes,
      int repairNumber) {
    List<String> safeFeedback = feedback == null ? List.of() : feedback;
    String feedbackText = safeFeedback.stream().limit(8).collect(Collectors.joining("\n"));
    List<AssistantModelProvider.ContextSnippet> repairContext = new ArrayList<>();
    repairContext.addAll(
        feedbackResolver.contractsForFeedback(
            level, safeFeedback, new SemanticModelPatch(List.of()), 12));
    repairContext.addAll(schemas.planningContracts(level, feedbackText, 8));
    safeFeedback.stream()
        .limit(8)
        .forEach(issue -> repairContext.addAll(catalogs.search(issue, level.name(), 2)));
    repairContext.addAll(snippets == null ? List.of() : snippets);
    String requestWithFeedback =
        "Original user request:\n"
            + (requestMessage == null ? "" : requestMessage)
            + "\n\nRejected ModelDelta draft summary:\n"
            + ModelDeltaTexts.summary(rejectedDelta)
            + "\n\nModelDelta compile failure:\n"
            + feedbackText
            + "\n\nReturn a complete replacement ModelDelta that fixes the compile failure."
            + " Use exact Ecore containment names in placement and exact writable reference names"
            + " in references. Do not use containment feature names as references.";
    AssistantModelProvider.AssistantPrompt prompt =
        new AssistantModelProvider.AssistantPrompt(
            AssistantModelRole.PLANNER,
            systemPrompt
                + "\nThis is compile-error repair pass "
                + repairNumber
                + " of "
                + settings.validationRepairAttempts()
                + ".",
            requestWithFeedback,
            repairContext);
    return modelingAgent.repair(
        level, baseModel, context, prompt, candidateTypes == null ? List.of() : candidateTypes);
  }

  /** Performs one validator-guided LLM repair pass through the ModelDelta agent. */
  public AssistantTurnPlan repair(
      ModelLevel level,
      String systemPrompt,
      String requestMessage,
      JsonNode baseModel,
      AssistantModelContext context,
      List<AssistantModelProvider.ContextSnippet> snippets,
      AssistantTurnPlan failed,
      List<String> feedback,
      int repairNumber) {
    return repair(
            level,
            systemPrompt,
            requestMessage,
            baseModel,
            context,
            snippets,
            failed,
            feedback,
            List.of(),
            repairNumber)
        .plan();
  }

  public ModelingAgent.AgentLoopResult repair(
      ModelLevel level,
      String systemPrompt,
      String requestMessage,
      JsonNode baseModel,
      AssistantModelContext context,
      List<AssistantModelProvider.ContextSnippet> snippets,
      AssistantTurnPlan failed,
      List<String> feedback,
      List<String> candidateTypes,
      int repairNumber) {
    List<String> safeFeedback = feedback == null ? List.of() : feedback;
    String feedbackText = safeFeedback.stream().limit(8).collect(Collectors.joining("\n"));
    List<AssistantModelProvider.ContextSnippet> repairContext = new ArrayList<>();
    repairContext.addAll(
        feedbackResolver.contractsForFeedback(level, safeFeedback, failed.patch(), 12));
    repairContext.addAll(schemas.planningContracts(level, feedbackText, 8));
    failed.patch().operations().stream()
        .filter(java.util.Objects::nonNull)
        .map(SemanticModelPatch.Operation::elementType)
        .filter(type -> type != null && !type.isBlank())
        .distinct()
        .forEach(
            type -> {
              try {
                repairContext.add(schemas.typeContract(level, type));
              } catch (PlatformException ignored) {
                // Validator feedback already identifies unknown types.
              }
            });
    safeFeedback.stream()
        .limit(8)
        .forEach(issue -> repairContext.addAll(catalogs.search(issue, level.name(), 2)));
    repairContext.addAll(snippets == null ? List.of() : snippets);
    String requestWithFeedback =
        "Original user request:\n"
            + (requestMessage == null ? "" : requestMessage)
            + "\n\nRejected semantic operations summary:\n"
            + operationSummary(failed.patch())
            + "\n\nBackend structural validation feedback:\n"
            + feedbackText
            + "\n\n"
            + "Return a focused ModelDelta patch that fixes ONLY the listed validation errors."
            + " Prefer attributeUpdates, reference fixes, and small element additions."
            + " Do NOT regenerate the entire model; omit unchanged elements."
            + " Limit output to fixes for at most 8 issues. Use kind=MODEL_DELTA unless a genuine"
            + " domain choice is missing.";
    AssistantModelProvider.AssistantPrompt prompt =
        new AssistantModelProvider.AssistantPrompt(
            AssistantModelRole.PLANNER,
            systemPrompt
                + "\nThis is validator-guided repair pass "
                + repairNumber
                + " of "
                + settings.validationRepairAttempts()
                + ". Do not repeat rejected operations.",
            requestWithFeedback,
            repairContext);
    return modelingAgent.repair(
        level, baseModel, context, prompt, candidateTypes == null ? List.of() : candidateTypes);
  }

  private Map<String, String> existingTypes(AssistantModelContext context) {
    if (context == null) {
      return Map.of();
    }
    return context.elements().stream()
        .collect(
            Collectors.toMap(
                io.mehdieidi.modless.platform.assistant.domain.context.AssistantModelContextTypes
                        .ContextElement
                    ::id,
                io.mehdieidi.modless.platform.assistant.domain.context.AssistantModelContextTypes
                        .ContextElement
                    ::type,
                (left, right) -> left,
                LinkedHashMap::new));
  }

  private String operationSummary(SemanticModelPatch patch) {
    if (patch == null || patch.operations().isEmpty()) {
      return "none";
    }
    return patch.operations().stream()
        .limit(15)
        .map(
            operation ->
                operation.type()
                    + " "
                    + operation.elementType()
                    + " target="
                    + operation.targetElementId()
                    + " source="
                    + operation.sourceElementId()
                    + " ref="
                    + operation.referenceName())
        .collect(Collectors.joining("\n"));
  }
}
