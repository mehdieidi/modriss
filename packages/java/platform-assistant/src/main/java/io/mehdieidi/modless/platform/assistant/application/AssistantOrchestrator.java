package io.mehdieidi.modless.platform.assistant.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.mehdieidi.modless.platform.assistant.domain.AssistantChoice;
import io.mehdieidi.modless.platform.assistant.domain.AssistantModelRole;
import io.mehdieidi.modless.platform.assistant.domain.AssistantProposal;
import io.mehdieidi.modless.platform.assistant.domain.AssistantTurnDiagnostics;
import io.mehdieidi.modless.platform.assistant.domain.AssistantTurnPlan;
import io.mehdieidi.modless.platform.assistant.domain.AssistantValidationSummary;
import io.mehdieidi.modless.platform.assistant.domain.AssistantWorkflowState;
import io.mehdieidi.modless.platform.assistant.domain.SemanticModelPatch;
import io.mehdieidi.modless.platform.assistant.domain.context.AssistantModelContextTypes.AssistantModelContext;
import io.mehdieidi.modless.platform.assistant.domain.context.AssistantModelContextTypes.ContextElement;
import io.mehdieidi.modless.platform.assistant.domain.memory.AssistantMemoryRecords.MessageRecord;
import io.mehdieidi.modless.platform.assistant.domain.memory.AssistantMemoryRecords.PendingInteractionRecord;
import io.mehdieidi.modless.platform.assistant.domain.memory.AssistantMemoryRecords.ProposalRecord;
import io.mehdieidi.modless.platform.assistant.domain.memory.AssistantMemoryRecords.ThreadRecord;
import io.mehdieidi.modless.platform.assistant.patch.AssistantMetamodelSchemaService;
import io.mehdieidi.modless.platform.assistant.patch.AssistantPatchCompiler;
import io.mehdieidi.modless.platform.assistant.patch.AssistantPatchCompleter;
import io.mehdieidi.modless.platform.assistant.planning.AssistantClarificationGate;
import io.mehdieidi.modless.platform.assistant.provider.AssistantModelProvider;
import io.mehdieidi.modless.platform.assistant.session.AssistantSessionStore;
import io.mehdieidi.modless.platform.assistant.spi.AssistantCatalog;
import io.mehdieidi.modless.platform.assistant.spi.AssistantChatMemory;
import io.mehdieidi.modless.platform.assistant.spi.AssistantMemoryStore;
import io.mehdieidi.modless.platform.assistant.spi.AssistantMetrics;
import io.mehdieidi.modless.platform.assistant.spi.AssistantModelContextIndex;
import io.mehdieidi.modless.platform.assistant.spi.AssistantRealtimePublisher;
import io.mehdieidi.modless.platform.assistant.spi.AssistantSettings;
import io.mehdieidi.modless.platform.assistant.spi.AssistantToolBridge;
import io.mehdieidi.modless.platform.identity.domain.UserRecord;
import io.mehdieidi.modless.platform.kernel.ModelLevel;
import io.mehdieidi.modless.platform.kernel.PlatformException;
import io.mehdieidi.modless.platform.model.application.ModelService;
import io.mehdieidi.modless.platform.model.domain.ModelRecord;
import io.mehdieidi.modless.platform.modeling.config.ModelingConfigService;
import io.mehdieidi.modless.platform.project.application.ProjectService;
import io.mehdieidi.modless.platform.project.domain.ProjectRecord;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** LLM-driven, metamodel-grounded, guarded-apply modeling workflow. */
public class AssistantOrchestrator {

  private static final Logger log = LoggerFactory.getLogger(AssistantOrchestrator.class);

  private final AssistantSettings properties;
  private final AssistantModelProvider provider;
  private final AssistantSessionStore sessions;
  private final AssistantMemoryStore memory;
  private final AssistantChatMemory chatMemory;
  private final AssistantCatalog catalogs;
  private final AssistantModelContextIndex modelContexts;
  private final AssistantPatchCompiler patchCompiler;
  private final AssistantPatchCompleter patchCompleter;
  private final AssistantValidationFeedbackResolver feedbackResolver;
  private final AssistantClarificationGate clarificationGate;
  private final AssistantRealtimePublisher realtime;
  private final AssistantHardeningService hardening;
  private final ModelService models;
  private final ProjectService projects;
  private final ModelingConfigService modelingConfig = new ModelingConfigService();
  private final AssistantMetamodelSchemaService schemas;
  private final AssistantToolBridge tools;
  private final AssistantMetrics metrics;
  private final ObjectMapper mapper;

  private final ThreadLocal<AssistantActivity> lastActivity = new ThreadLocal<>();
  private final ThreadLocal<AssistantTurnDiagnostics> lastDiagnostics = new ThreadLocal<>();

  public AssistantOrchestrator(
      AssistantSettings properties,
      AssistantModelProvider provider,
      AssistantSessionStore sessions,
      AssistantMemoryStore memory,
      AssistantChatMemory chatMemory,
      AssistantCatalog catalogs,
      AssistantModelContextIndex modelContexts,
      AssistantPatchCompiler patchCompiler,
      AssistantPatchCompleter patchCompleter,
      AssistantValidationFeedbackResolver feedbackResolver,
      AssistantClarificationGate clarificationGate,
      AssistantMetamodelSchemaService schemas,
      AssistantToolBridge tools,
      AssistantMetrics metrics,
      ObjectMapper mapper,
      AssistantRealtimePublisher realtime,
      AssistantHardeningService hardening,
      ModelService models,
      ProjectService projects) {
    this.properties = properties;
    this.provider = provider;
    this.sessions = sessions;
    this.memory = memory;
    this.chatMemory = chatMemory;
    this.catalogs = catalogs;
    this.modelContexts = modelContexts;
    this.patchCompiler = patchCompiler;
    this.patchCompleter = patchCompleter;
    this.feedbackResolver = feedbackResolver;
    this.clarificationGate = clarificationGate;
    this.schemas = schemas;
    this.tools = tools;
    this.metrics = metrics;
    this.mapper = mapper;
    this.realtime = realtime;
    this.hardening = hardening;
    this.models = models;
    this.projects = projects;
  }

  /** Starts or resumes a level-scoped assistant session. */
  public AssistantSessionStore.AssistantSession startSession(
      UserRecord user, String projectId, ModelLevel level, String title) {
    return startSession(user, projectId, level, title, null, false);
  }

  /**
   * Starts, resumes, or continues a level-scoped assistant session.
   *
   * @param user owner user
   * @param projectId project scope
   * @param level modeling level
   * @param title default title for new conversations
   * @param resumeSessionId optional durable session to resume
   * @param forceNew when true, always starts a fresh conversation
   * @return runtime session
   */
  public AssistantSessionStore.AssistantSession startSession(
      UserRecord user,
      String projectId,
      ModelLevel level,
      String title,
      String resumeSessionId,
      boolean forceNew) {
    ProjectRecord project = projects.get(user, projectId);
    String modelId = activeModelId(project, level);
    Long revision =
        modelId == null || modelId.isBlank() ? null : models.get(user, level, modelId).revision();
    String displayTitle =
        title == null || title.isBlank() ? level.apiName() + "-assistant" : title.trim();

    if (!blank(resumeSessionId)) {
      ThreadRecord thread =
          memory
              .findThread(resumeSessionId.trim(), user.id())
              .orElseThrow(() -> new PlatformException(404, "Assistant conversation not found."));
      if (!thread.projectId().equals(projectId) || thread.level() != level) {
        throw new PlatformException(
            400, "Conversation does not match the current project or modeling level.");
      }
      memory.updateThreadModel(thread.id(), modelId, revision);
      return sessions.create(user.id(), projectId, level, thread.title(), thread.id());
    }

    if (forceNew) {
      String threadId = memory.newThreadId(user.id(), projectId, level);
      memory.createThread(threadId, user, projectId, level, displayTitle, modelId, revision);
      return sessions.create(user.id(), projectId, level, displayTitle, threadId);
    }

    Optional<ThreadRecord> recent =
        memory.findMostRecentThread(
            user.id(), projectId, level, Instant.now().minus(3, ChronoUnit.DAYS));
    if (recent.isPresent()) {
      ThreadRecord thread = recent.get();
      memory.updateThreadModel(thread.id(), modelId, revision);
      return sessions.create(user.id(), projectId, level, thread.title(), thread.id());
    }

    String threadId = memory.newThreadId(user.id(), projectId, level);
    memory.createThread(threadId, user, projectId, level, displayTitle, modelId, revision);
    return sessions.create(user.id(), projectId, level, displayTitle, threadId);
  }

  /** Lists recent conversations for history browsing. */
  public List<ConversationSummary> listConversations(
      UserRecord user, String projectId, ModelLevel level, int days, int limit) {
    projects.get(user, projectId);
    int safeDays = Math.max(1, Math.min(days, 30));
    int safeLimit = Math.max(1, Math.min(limit, 50));
    Instant since = Instant.now().minus(safeDays, ChronoUnit.DAYS);
    return memory.listRecentConversations(user.id(), projectId, level, since, safeLimit).stream()
        .map(
            conversation ->
                new ConversationSummary(
                    conversation.sessionId(),
                    conversationTitle(conversation),
                    conversationPreview(conversation),
                    conversation.updatedAt(),
                    conversation.messageCount()))
        .toList();
  }

  /** Handles one natural-language turn using a structured LLM decision. */
  public AssistantTurnResponse handleMessage(
      UserRecord user, String sessionId, AssistantTurnRequest request) {
    long startedAt = System.currentTimeMillis();
    hardening.checkRateLimit(user.id());
    metrics.recordAssistantRequest();
    AssistantSessionStore.AssistantSession session = requireSession(sessionId, user.id());
    String threadId = session.id();
    ensureDurableThread(user, session);
    appendUserMessage(threadId, sessionId, request);

    ProjectRecord project = projects.get(user, session.projectId());
    publishProgress(sessionId, "READING_MODEL", "Reading the active model and validation state");
    String modelId = resolveModelId(request.modelId(), project, session.level());
    ModelRecord model = modelId == null ? null : models.get(user, session.level(), modelId);
    requireCurrentRevision(request.revision(), model);

    JsonNode persistedModel =
        model == null
            ? modelingConfig.starterModel(session.level(), session.title())
            : model.modelJson();
    JsonNode baseModel =
        resolvePlanningBase(session.level(), persistedModel, request.unsavedDraftPatch());
    ModelService.ValidationResult currentValidation = models.validate(session.level(), baseModel);
    AssistantModelContext context =
        model == null
            ? modelContexts.transientSnapshot(
                session.projectId(),
                session.level(),
                session.title(),
                0L,
                baseModel,
                currentValidation)
            : modelContexts.snapshot(model, currentValidation);
    List<AssistantModelProvider.ContextSnippet> snippets =
        retrievalSnippets(
            request.message(), context, session.level(), request.selectedElementIds());

    tools.bindSession(new AssistantToolBridge.ToolSession(session.level(), baseModel, context));
    int toolCalls = 0;
    int repairAttempts = 0;
    AssistantTurnPlan plan;
    try {
      publishProgress(
          sessionId, "PLANNING", "Understanding intent using the formal language context");
      if (prefersInformationPath(request.rootMessage())) {
        plan =
            provider.planTurn(
                new AssistantModelProvider.AssistantPrompt(
                    AssistantModelRole.PLANNER,
                    turnPrompt(session, request, context),
                    request.message(),
                    snippets));
      } else {
        AssistantModelProvider.AgentLoopResult loopResult =
            provider.planMutationTurn(
                new AssistantModelProvider.AssistantPrompt(
                    AssistantModelRole.PLANNER,
                    turnPrompt(session, request, context),
                    request.message(),
                    snippets),
                (stage, message) -> publishProgress(sessionId, stage, message));
        plan = loopResult.plan();
        toolCalls = loopResult.toolCalls();
        metrics.recordAssistantToolCalls(toolCalls);
        log.info(
            "Assistant agent loop completed sessionId={} steps={} toolCalls={} snippets={}",
            sessionId,
            loopResult.steps(),
            toolCalls,
            snippets.size());
      }
    } catch (PlatformException failure) {
      if (failure.status() < 500 && failure.status() != 429) {
        throw failure;
      }
      recordTurnDiagnostics(
          "FAILED", snippets.size(), toolCalls, repairAttempts, "PROVIDER_UNAVAILABLE", startedAt);
      metrics.recordAssistantTurnOutcome(evalCategory(request, context), "FAILED");
      return finishTurn(
          session,
          threadId,
          new AssistantTurnResponse(
              "The modeling provider is temporarily unavailable. Your model is unchanged and "
                  + "this request is still in the conversation, so you can retry it without "
                  + "re-entering context.",
              modelId,
              model == null ? null : model.revision(),
              null,
              List.of(),
              AssistantWorkflowState.FAILED,
              activityFor(AssistantWorkflowState.FAILED)));
    } finally {
      tools.clearSession();
    }

    AssistantTurnResponse response =
        switch (plan.kind()) {
          case ANSWER -> {
            if (plan.intent() == AssistantTurnPlan.Intent.MUTATION) {
              if (plan.patch().operations().isEmpty()) {
                plan = replanWithSafeDefaults(session, request, context, snippets, plan);
              }
              yield proposalResponse(
                  user, session, threadId, request, model, baseModel, context, snippets, plan);
            }
            yield finishTurn(
                session,
                threadId,
                new AssistantTurnResponse(
                    nonBlank(plan.message(), "I completed the model analysis."),
                    modelId,
                    model == null ? null : model.revision(),
                    null,
                    List.of(),
                    AssistantWorkflowState.EXPLAINED,
                    activityFor(AssistantWorkflowState.EXPLAINED)));
          }
          case CLARIFICATION -> {
            AssistantTurnPlan gated = clarificationGate.apply(plan, request.rootMessage());
            gated = resolveMutationClarification(session, request, context, snippets, plan, gated);
            yield switch (gated.kind()) {
              case CLARIFICATION ->
                  clarificationResponse(
                      session, threadId, request, model, gated.message(), gated.questions());
              case PATCH ->
                  proposalResponse(
                      user, session, threadId, request, model, baseModel, context, snippets, gated);
              case ANSWER -> {
                if (gated.intent() == AssistantTurnPlan.Intent.MUTATION) {
                  AssistantTurnPlan replanned =
                      replanWithSafeDefaults(session, request, context, snippets, gated);
                  yield proposalResponse(
                      user, session, threadId, request, model, baseModel, context, snippets,
                      replanned);
                }
                yield finishTurn(
                    session,
                    threadId,
                    new AssistantTurnResponse(
                        nonBlank(gated.message(), "I completed the model analysis."),
                        modelId,
                        model == null ? null : model.revision(),
                        null,
                        List.of(),
                        AssistantWorkflowState.EXPLAINED,
                        activityFor(AssistantWorkflowState.EXPLAINED)));
              }
            };
          }
          case PATCH ->
              proposalResponse(
                  user, session, threadId, request, model, baseModel, context, snippets, plan);
        };
    recordTurnDiagnostics(
        response.workflowState().name(),
        snippets.size(),
        toolCalls,
        0,
        response.workflowState().name(),
        startedAt);
    metrics.recordAssistantTurnOutcome(
        evalCategory(request, context), response.workflowState().name());
    return response;
  }

  private AssistantTurnResponse proposalResponse(
      UserRecord user,
      AssistantSessionStore.AssistantSession session,
      String threadId,
      AssistantTurnRequest request,
      ModelRecord model,
      JsonNode baseModel,
      AssistantModelContext context,
      List<AssistantModelProvider.ContextSnippet> snippets,
      AssistantTurnPlan initialPlan) {
    publishProgress(session.id(), "VALIDATING", "Compiling and validating the proposed change");
    AssistantTurnPlan acceptedPlan = preparePlan(session.level(), context, initialPlan);
    PlanAttempt attempt = evaluatePlan(session.level(), baseModel, context, acceptedPlan);
    int repairNumber = 0;
    int stagnationCount = 0;
    String lastPatchSignature = patchSignature(acceptedPlan.patch());
    int lastFeedbackCount = attempt.feedback().size();
    while (!attempt.valid() && repairNumber < properties.validationRepairAttempts()) {
      repairNumber++;
      publishProgress(
          session.id(),
          "REPAIRING",
          repairNumber == 1
              ? "Refining the proposal using validator feedback"
              : "Still refining the proposal (pass "
                  + repairNumber
                  + " of "
                  + properties.validationRepairAttempts()
                  + ")");
      if (feedbackResolver.isRepairableStructuralFailure(attempt.feedback())) {
        AssistantTurnPlan deterministic =
            preparePlan(
                session.level(),
                context,
                tryDeterministicRepair(session.level(), context, acceptedPlan, attempt));
        if (!samePatch(deterministic, acceptedPlan)) {
          acceptedPlan = deterministic;
          attempt = evaluatePlan(session.level(), baseModel, context, acceptedPlan);
          if (attempt.valid()) {
            break;
          }
        }
      }
      String signature = patchSignature(acceptedPlan.patch());
      if (signature.equals(lastPatchSignature)) {
        stagnationCount++;
        boolean feedbackImproved = attempt.feedback().size() < lastFeedbackCount;
        if (stagnationCount >= 2 && !feedbackImproved) {
          break;
        }
      } else {
        stagnationCount = 0;
        lastPatchSignature = signature;
      }
      lastFeedbackCount = attempt.feedback().size();
      AssistantTurnPlan repaired;
      try {
        repaired =
            repairPlan(session, request, context, snippets, acceptedPlan, attempt, repairNumber);
      } catch (PlatformException failure) {
        if (failure.status() < 500 && failure.status() != 429) {
          throw failure;
        }
        return providerFailureResponse(session, threadId, model);
      }
      if (repaired.kind() == AssistantTurnPlan.Kind.CLARIFICATION) {
        AssistantTurnPlan gated = clarificationGate.apply(repaired, request.rootMessage());
        if (gated.kind() == AssistantTurnPlan.Kind.CLARIFICATION
            && clarificationGate.shouldDeferToProposal(gated, request.rootMessage())) {
          repaired = replanWithSafeDefaults(session, request, context, snippets, acceptedPlan);
        } else if (gated.kind() == AssistantTurnPlan.Kind.CLARIFICATION) {
          return clarificationResponse(
              session, threadId, request, model, gated.message(), gated.questions());
        } else if (gated.kind() == AssistantTurnPlan.Kind.ANSWER) {
          continue;
        } else {
          repaired = gated;
        }
      }
      if (repaired.kind() == AssistantTurnPlan.Kind.ANSWER) {
        continue;
      }
      if (samePatch(repaired, acceptedPlan)) {
        continue;
      }
      acceptedPlan = preparePlan(session.level(), context, repaired);
      attempt = evaluatePlan(session.level(), baseModel, context, acceptedPlan);
      if (attempt.valid()) {
        break;
      }
    }
    if (!attempt.valid()) {
      for (int replan = 0; replan < 1 && !attempt.valid(); replan++) {
        publishProgress(
            session.id(),
            "PLANNING",
            "Rebuilding the proposal with metamodel defaults and validator feedback");
        acceptedPlan =
            preparePlan(
                session.level(),
                context,
                replanWithSafeDefaults(
                    session, request, context, snippets, acceptedPlan, attempt.feedback()));
        attempt = evaluatePlan(session.level(), baseModel, context, acceptedPlan);
      }
    }
    if (!attempt.valid()) {
      return partialProposalOrFailure(
          user,
          session,
          threadId,
          request,
          model,
          baseModel,
          context,
          snippets,
          acceptedPlan,
          attempt);
    }
    metrics.recordAssistantRepairAttempts(repairNumber);

    AssistantPatchCompiler.CompiledPatch compiled = attempt.compiled();
    AssistantProposal.RiskLevel risk = riskLevel(compiled, attempt.validation());
    if (shouldAutoApply(request, acceptedPlan, risk)) {
      publishProgress(session.id(), "APPLYING", "Applying the validated low-risk change");
      return autoApplyValidatedProposal(
          user, session, threadId, request, model, acceptedPlan, attempt, snippets, context, risk);
    }

    AssistantProposal proposal =
        new AssistantProposal(
            java.util.UUID.randomUUID().toString(),
            compiled.affectedElements(),
            acceptedPlan.patch(),
            compiled.inversePatch(),
            attempt.validation(),
            risk,
            true,
            retrievalCitations(snippets, context),
            Instant.now());
    memory.clearPendingInteraction(threadId);
    memory.saveProposal(
        threadId,
        session.projectId(),
        model == null ? null : model.id(),
        model == null ? 0L : model.revision(),
        proposal,
        "PROPOSED");
    memory.appendAudit(
        proposal.id(),
        session.projectId(),
        user.id(),
        "PROPOSED",
        Map.of("operationCount", proposal.patch().operations().size(), "validationPassed", true));
    String message =
        nonBlank(acceptedPlan.message(), "I prepared the requested model change.")
            + "\n\nThe proposal passed structural and mandatory EVL validation. Review it before "
            + "applying it to the canvas.";
    return finishTurn(
        session,
        threadId,
        new AssistantTurnResponse(
            message,
            model == null ? null : model.id(),
            model == null ? 0L : model.revision(),
            proposal,
            List.of(),
            AssistantWorkflowState.PROPOSED,
            activityFor(AssistantWorkflowState.PROPOSED)));
  }

  private AssistantTurnPlan preparePlan(
      ModelLevel level, AssistantModelContext context, AssistantTurnPlan plan) {
    AssistantTurnPlan normalized = normalizePlan(level, context, plan);
    Map<String, String> types = existingTypes(context);
    SemanticModelPatch completed = patchCompleter.complete(level, normalized.patch(), types);
    return new AssistantTurnPlan(
        normalized.intent(),
        normalized.kind(),
        normalized.message(),
        normalized.questions(),
        completed);
  }

  private Map<String, String> existingTypes(AssistantModelContext context) {
    return context.elements().stream()
        .collect(
            Collectors.toMap(
                ContextElement::id,
                ContextElement::type,
                (left, right) -> left,
                LinkedHashMap::new));
  }

  private AssistantTurnResponse partialProposalOrFailure(
      UserRecord user,
      AssistantSessionStore.AssistantSession session,
      String threadId,
      AssistantTurnRequest request,
      ModelRecord model,
      JsonNode baseModel,
      AssistantModelContext context,
      List<AssistantModelProvider.ContextSnippet> snippets,
      AssistantTurnPlan failedPlan,
      PlanAttempt attempt) {
    if (isCreationRequest(request.rootMessage()) && modelContexts.isEmptyCanvas(context)) {
      return failureWithFeedback(session, threadId, request, model, attempt);
    }
    Optional<PlanAttempt> partial =
        findMaximalValidSubset(session.level(), baseModel, context, failedPlan);
    if (partial.isPresent()) {
      PlanAttempt partialAttempt = partial.get();
      AssistantTurnPlan acceptedPlan = partialAttempt.plan();
      AssistantPatchCompiler.CompiledPatch compiled = partialAttempt.compiled();
      AssistantProposal proposal =
          new AssistantProposal(
              java.util.UUID.randomUUID().toString(),
              compiled.affectedElements(),
              acceptedPlan.patch(),
              compiled.inversePatch(),
              partialAttempt.validation(),
              riskLevel(compiled, partialAttempt.validation()),
              true,
              retrievalCitations(snippets, context),
              Instant.now());
      memory.clearPendingInteraction(threadId);
      memory.saveProposal(
          threadId,
          session.projectId(),
          model == null ? null : model.id(),
          model == null ? 0L : model.revision(),
          proposal,
          "PROPOSED");
      memory.appendAudit(
          proposal.id(),
          session.projectId(),
          user.id(),
          "PROPOSED",
          Map.of(
              "operationCount",
              proposal.patch().operations().size(),
              "validationPassed",
              true,
              "partialProposal",
              true));
      String message =
          "I could not satisfy every part of the request in one pass, so I prepared a smaller "
              + "valid proposal with the highest-confidence elements completed.\n\n"
              + nonBlank(
                  acceptedPlan.message(),
                  "Review the partial proposal before applying it to the canvas.");
      return finishTurn(
          session,
          threadId,
          new AssistantTurnResponse(
              message,
              model == null ? null : model.id(),
              model == null ? 0L : model.revision(),
              proposal,
              List.of(),
              AssistantWorkflowState.PROPOSED,
              activityFor(AssistantWorkflowState.PROPOSED)));
    }
    return finishTurn(
        session,
        threadId,
        new AssistantTurnResponse(
            "The generated change could not satisfy the formal language after deterministic "
                + "completion and validator-guided repairs. Refine the scope or add more domain "
                + "detail and try again.",
            model == null ? null : model.id(),
            model == null ? null : model.revision(),
            null,
            List.of(),
            AssistantWorkflowState.FAILED,
            activityFor(AssistantWorkflowState.FAILED)));
  }

  private AssistantTurnResponse failureWithFeedback(
      AssistantSessionStore.AssistantSession session,
      String threadId,
      AssistantTurnRequest request,
      ModelRecord model,
      PlanAttempt attempt) {
    List<String> feedback =
        attempt == null || attempt.feedback() == null ? List.of() : attempt.feedback();
    String issues =
        feedback.isEmpty()
            ? "The planner could not ground the request in the formal metamodel."
            : feedback.stream().limit(6).collect(Collectors.joining("\n- ", "- ", ""));
    String message =
        "I could not prepare a valid model proposal for this request yet.\n\n"
            + issues
            + "\n\n"
            + "Your canvas is unchanged. Add more domain detail, narrow the scope, or answer a "
            + "follow-up question if I ask for one.";
    if (!feedback.isEmpty() && feedbackResolver.isFormalFailure(feedback)) {
      memory.clearPendingInteraction(threadId);
      return finishTurn(
          session,
          threadId,
          new AssistantTurnResponse(
              message,
              model == null ? null : model.id(),
              model == null ? null : model.revision(),
              null,
              List.of(),
              AssistantWorkflowState.FAILED,
              activityFor(AssistantWorkflowState.FAILED)));
    }
    List<AssistantChoice> questions =
        List.of(
            new AssistantChoice(
                "modeling-scope",
                "What should be modeled first for this request?",
                AssistantChoice.SelectionMode.SINGLE,
                List.of(
                    new AssistantChoice.Option(
                        "core-api",
                        "Core API and functions",
                        "Start with the main service, APIs, and command/query handlers."),
                    new AssistantChoice.Option(
                        "events-data",
                        "Events and durable state",
                        "Start with channels, stores, and the functions that use them."),
                    new AssistantChoice.Option(
                        "full-context",
                        "Full bounded context",
                        "Model service, APIs, functions, stores, and integration together.")),
                true));
    memory.savePendingInteraction(threadId, request, questions);
    return finishTurn(
        session,
        threadId,
        new AssistantTurnResponse(
            message,
            model == null ? null : model.id(),
            model == null ? null : model.revision(),
            null,
            questions,
            AssistantWorkflowState.WAITING_FOR_CHOICE,
            activityFor(AssistantWorkflowState.WAITING_FOR_CHOICE)));
  }

  private Optional<PlanAttempt> findMaximalValidSubset(
      ModelLevel level, JsonNode baseModel, AssistantModelContext context, AssistantTurnPlan plan) {
    List<SemanticModelPatch.Operation> accepted = new ArrayList<>();
    for (SemanticModelPatch.Operation operation : plan.patch().operations()) {
      if (operation == null) {
        continue;
      }
      List<SemanticModelPatch.Operation> candidate = new ArrayList<>(accepted);
      candidate.add(operation);
      AssistantTurnPlan trial =
          preparePlan(
              level,
              context,
              new AssistantTurnPlan(
                  plan.intent(),
                  plan.kind(),
                  plan.message(),
                  plan.questions(),
                  new SemanticModelPatch(candidate)));
      PlanAttempt attempt = evaluatePlan(level, baseModel, context, trial);
      if (attempt.valid()) {
        accepted.add(operation);
      }
    }
    if (accepted.isEmpty()) {
      return Optional.empty();
    }
    AssistantTurnPlan subset =
        new AssistantTurnPlan(
            plan.intent(),
            plan.kind(),
            plan.message(),
            plan.questions(),
            new SemanticModelPatch(accepted));
    AssistantTurnPlan prepared = preparePlan(level, context, subset);
    PlanAttempt attempt = evaluatePlan(level, baseModel, context, prepared);
    return attempt.valid() ? Optional.of(attempt.withPlan(prepared)) : Optional.empty();
  }

  private AssistantTurnResponse providerFailureResponse(
      AssistantSessionStore.AssistantSession session, String threadId, ModelRecord model) {
    return finishTurn(
        session,
        threadId,
        new AssistantTurnResponse(
            "The modeling provider became unavailable while repairing the plan. Your model is "
                + "unchanged; retry when the provider is healthy.",
            model == null ? null : model.id(),
            model == null ? null : model.revision(),
            null,
            List.of(),
            AssistantWorkflowState.FAILED,
            activityFor(AssistantWorkflowState.FAILED)));
  }

  private AssistantTurnPlan resolveMutationClarification(
      AssistantSessionStore.AssistantSession session,
      AssistantTurnRequest request,
      AssistantModelContext context,
      List<AssistantModelProvider.ContextSnippet> snippets,
      AssistantTurnPlan original,
      AssistantTurnPlan gated) {
    if (original.intent() != AssistantTurnPlan.Intent.MUTATION) {
      return gated;
    }
    if (gated.kind() == AssistantTurnPlan.Kind.PATCH && gated.patch().operations().isEmpty()) {
      return replanWithSafeDefaults(session, request, context, snippets, original);
    }
    if (gated.kind() != AssistantTurnPlan.Kind.CLARIFICATION) {
      return gated;
    }
    if (!clarificationGate.shouldDeferToProposal(gated, request.rootMessage())) {
      return gated;
    }
    publishProgress(
        session.id(), "PLANNING", "Choosing safe defaults and drafting a complete proposal");
    AssistantTurnPlan replanned =
        replanWithSafeDefaults(session, request, context, snippets, original);
    if (replanned.kind() == AssistantTurnPlan.Kind.CLARIFICATION
        && clarificationGate.shouldDeferToProposal(replanned, request.rootMessage())) {
      replanned = replanWithSafeDefaults(session, request, context, snippets, replanned);
    }
    if (replanned.kind() == AssistantTurnPlan.Kind.CLARIFICATION) {
      return clarificationGate.apply(replanned, request.rootMessage());
    }
    return replanned;
  }

  private AssistantTurnPlan tryDeterministicRepair(
      ModelLevel level,
      AssistantModelContext context,
      AssistantTurnPlan plan,
      PlanAttempt attempt) {
    SemanticModelPatch repaired =
        patchCompleter.repairFromValidationFeedback(
            level,
            plan.patch(),
            existingTypes(context),
            feedbackResolver.missingRequiredFeatures(attempt.feedback()));
    if (repaired.operations().equals(plan.patch().operations())) {
      return plan;
    }
    return new AssistantTurnPlan(
        plan.intent(), plan.kind(), plan.message(), plan.questions(), repaired);
  }

  private boolean isCreationRequest(String message) {
    if (message == null || message.isBlank()) {
      return false;
    }
    return message
        .toLowerCase(java.util.Locale.ROOT)
        .matches("(?s).*(\\bcreate\\b|\\bbuild\\b|\\bdesign\\b|\\badd\\b|\\bscaffold\\b).*");
  }

  private boolean samePatch(AssistantTurnPlan left, AssistantTurnPlan right) {
    if (left == null || right == null) {
      return false;
    }
    return left.patch().operations().equals(right.patch().operations());
  }

  private AssistantTurnPlan replanWithSafeDefaults(
      AssistantSessionStore.AssistantSession session,
      AssistantTurnRequest request,
      AssistantModelContext context,
      List<AssistantModelProvider.ContextSnippet> snippets,
      AssistantTurnPlan rejected) {
    return replanWithSafeDefaults(session, request, context, snippets, rejected, List.of());
  }

  private AssistantTurnPlan replanWithSafeDefaults(
      AssistantSessionStore.AssistantSession session,
      AssistantTurnRequest request,
      AssistantModelContext context,
      List<AssistantModelProvider.ContextSnippet> snippets,
      AssistantTurnPlan rejected,
      List<String> feedback) {
    publishProgress(
        session.id(), "PLANNING", "Replanning with validator feedback and metamodel context");
    String rejectedSummary =
        rejected == null || rejected.patch().operations().isEmpty()
            ? "none"
            : operationSummary(rejected.patch());
    String feedbackSummary =
        feedback == null || feedback.isEmpty()
            ? ""
            : "\nValidator feedback to correct:\n"
                + feedback.stream().limit(12).collect(Collectors.joining("\n"));
    return provider.planTurn(
        new AssistantModelProvider.AssistantPrompt(
            AssistantModelRole.PLANNER,
            turnPrompt(session, request, context)
                + "\n\nThis is a mandatory replanning pass. Return kind=PATCH only."
                + "\nNever return CLARIFICATION or ANSWER for this mutation."
                + "\nDo not ask the user about IDs, UUIDs, architecture style, runtime language,"
                + " package manager, persistence technology, API style, or layout."
                + "\nInfer safe enum defaults from the retrieved metamodel contracts and starter"
                + " model. The user will review the guarded proposal on the canvas."
                + "\nModel only what the user asked for. Use as many operations as the request"
                + " genuinely requires, including required nested contracts and attributes."
                + "\nRejected prior plan summary: "
                + rejectedSummary
                + feedbackSummary,
            "Original request:\n"
                + request.rootMessage()
                + "\n\nReturn one complete PATCH that satisfies the request using safe defaults.",
            deduplicate(snippets, properties.maxContextSnippets())));
  }

  private String patchSignature(SemanticModelPatch patch) {
    if (patch == null || patch.operations().isEmpty()) {
      return "";
    }
    return patch.operations().stream()
        .filter(java.util.Objects::nonNull)
        .map(
            operation ->
                operation.type()
                    + ":"
                    + operation.targetElementId()
                    + ":"
                    + operation.elementType()
                    + ":"
                    + operation.sourceElementId()
                    + ":"
                    + operation.referenceName())
        .collect(Collectors.joining("|"));
  }

  private PlanAttempt evaluatePlan(
      ModelLevel level, JsonNode baseModel, AssistantModelContext context, AssistantTurnPlan plan) {
    if (plan.patch().operations().isEmpty()) {
      return PlanAttempt.failure("The planner returned no semantic operations.");
    }
    try {
      validateSemanticPatch(level, plan.patch(), context, baseModel);
      AssistantPatchCompiler.CompiledPatch compiled =
          patchCompiler.compile(baseModel, plan.patch());
      if (compiled.patch().isEmpty()) {
        return PlanAttempt.failure("The semantic operations would not change the model.");
      }
      ObjectNode preview = patchCompiler.apply(baseModel, compiled);
      AssistantValidationSummary validation = validationSummary(models.validate(level, preview));
      return validation.structurallyValid() && validation.mandatoryPassed()
          ? PlanAttempt.success(compiled, validation)
          : PlanAttempt.failure(compiled, validation);
    } catch (PlatformException failure) {
      String operationSummary = operationSummary(plan.patch());
      log.warn(
          "Assistant semantic plan rejected reason={} operations={}",
          failure.getMessage(),
          operationSummary);
      return PlanAttempt.failure(failure.getMessage() + " Operation summary: " + operationSummary);
    }
  }

  private String operationSummary(SemanticModelPatch patch) {
    return patch.operations().stream()
        .filter(java.util.Objects::nonNull)
        .map(
            operation ->
                operation.type()
                    + "(target="
                    + operation.targetElementId()
                    + ",type="
                    + operation.elementType()
                    + ",source="
                    + operation.sourceElementId()
                    + ",feature="
                    + operation.referenceName()
                    + ")")
        .collect(Collectors.joining(","));
  }

  private AssistantTurnPlan normalizePlan(
      ModelLevel level, AssistantModelContext context, AssistantTurnPlan plan) {
    if (plan.patch().operations().isEmpty()) {
      return plan;
    }
    SemanticModelPatch normalizedPatch = normalizeNewElementIds(plan.patch());
    Map<String, String> types =
        context.elements().stream()
            .collect(
                Collectors.toMap(
                    ContextElement::id,
                    ContextElement::type,
                    (left, right) -> left,
                    LinkedHashMap::new));
    normalizedPatch.operations().stream()
        .filter(
            operation ->
                operation != null
                    && operation.type() == SemanticModelPatch.OperationType.ADD_ELEMENT)
        .filter(operation -> !blank(operation.targetElementId()))
        .forEach(
            operation -> {
              try {
                types.put(
                    operation.targetElementId(),
                    schemas.canonicalType(level, operation.elementType()));
              } catch (PlatformException ignored) {
                types.put(operation.targetElementId(), operation.elementType());
              }
            });
    List<SemanticModelPatch.Operation> operations =
        orderOperationsForCompilation(
            normalizedPatch.operations().stream()
                .map(operation -> inferUniqueContainment(level, types, operation))
                .map(operation -> normalizeRelationshipDirection(level, types, operation))
                .toList());
    return new AssistantTurnPlan(
        plan.intent(),
        plan.kind(),
        plan.message(),
        plan.questions(),
        new SemanticModelPatch(operations));
  }

  private SemanticModelPatch normalizeNewElementIds(SemanticModelPatch patch) {
    Map<String, String> replacements = new LinkedHashMap<>();
    patch.operations().stream()
        .filter(java.util.Objects::nonNull)
        .filter(operation -> operation.type() == SemanticModelPatch.OperationType.ADD_ELEMENT)
        .map(SemanticModelPatch.Operation::targetElementId)
        .filter(id -> id != null && !id.isBlank())
        .filter(id -> !isUuid(id))
        .distinct()
        .forEach(id -> replacements.put(id, java.util.UUID.randomUUID().toString()));
    if (replacements.isEmpty()) {
      return patch;
    }
    List<SemanticModelPatch.Operation> operations =
        patch.operations().stream()
            .map(
                operation -> {
                  if (operation == null) {
                    return null;
                  }
                  return new SemanticModelPatch.Operation(
                      operation.type(),
                      replacements.getOrDefault(
                          operation.targetElementId(), operation.targetElementId()),
                      operation.elementType(),
                      remapIds(operation.attributes(), replacements),
                      replacements.getOrDefault(
                          operation.sourceElementId(), operation.sourceElementId()),
                      operation.referenceName());
                })
            .toList();
    return new SemanticModelPatch(operations);
  }

  private JsonNode remapIds(JsonNode value, Map<String, String> replacements) {
    if (value == null || value.isNull()) {
      return value;
    }
    if (value.isTextual()) {
      String replacement = replacements.get(value.asText());
      return replacement == null
          ? value.deepCopy()
          : com.fasterxml.jackson.databind.node.TextNode.valueOf(replacement);
    }
    if (value.isObject()) {
      ObjectNode result = ((ObjectNode) value).deepCopy();
      value
          .fields()
          .forEachRemaining(
              entry -> result.set(entry.getKey(), remapIds(entry.getValue(), replacements)));
      return result;
    }
    if (value.isArray()) {
      com.fasterxml.jackson.databind.node.ArrayNode result =
          com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.arrayNode();
      value.forEach(item -> result.add(remapIds(item, replacements)));
      return result;
    }
    return value.deepCopy();
  }

  private List<SemanticModelPatch.Operation> orderOperationsForCompilation(
      List<SemanticModelPatch.Operation> operations) {
    List<SemanticModelPatch.Operation> pendingAdds =
        operations.stream()
            .filter(java.util.Objects::nonNull)
            .filter(operation -> operation.type() == SemanticModelPatch.OperationType.ADD_ELEMENT)
            .collect(Collectors.toCollection(ArrayList::new));
    List<SemanticModelPatch.Operation> ordered = new ArrayList<>();
    Set<String> pendingIds =
        pendingAdds.stream()
            .map(SemanticModelPatch.Operation::targetElementId)
            .collect(Collectors.toCollection(LinkedHashSet::new));
    while (!pendingAdds.isEmpty()) {
      List<SemanticModelPatch.Operation> ready =
          pendingAdds.stream()
              .filter(
                  operation ->
                      blank(operation.sourceElementId())
                          || !pendingIds.contains(operation.sourceElementId()))
              .toList();
      if (ready.isEmpty()) {
        ordered.addAll(pendingAdds);
        break;
      }
      ordered.addAll(ready);
      pendingAdds.removeAll(ready);
      ready.stream().map(SemanticModelPatch.Operation::targetElementId).forEach(pendingIds::remove);
    }
    operations.stream()
        .filter(
            operation ->
                operation == null
                    || operation.type() != SemanticModelPatch.OperationType.ADD_ELEMENT)
        .forEach(ordered::add);
    return ordered;
  }

  private boolean isUuid(String value) {
    try {
      return java.util.UUID.fromString(value).toString().equalsIgnoreCase(value);
    } catch (IllegalArgumentException ignored) {
      return false;
    }
  }

  private SemanticModelPatch.Operation inferUniqueContainment(
      ModelLevel level, Map<String, String> types, SemanticModelPatch.Operation operation) {
    if (operation == null || operation.type() != SemanticModelPatch.OperationType.ADD_ELEMENT) {
      return operation;
    }
    try {
      if (schemas.rootCollection(level, operation.elementType()).isPresent()) {
        return new SemanticModelPatch.Operation(
            operation.type(),
            operation.targetElementId(),
            operation.elementType(),
            operation.attributes(),
            null,
            null);
      }
    } catch (PlatformException ignored) {
      return operation;
    }
    List<ContainmentCandidate> allCandidates;
    try {
      allCandidates =
          types.entrySet().stream()
              .filter(entry -> !entry.getKey().equals(operation.targetElementId()))
              .flatMap(
                  entry ->
                      schemas
                          .containments(level, entry.getValue(), operation.elementType())
                          .stream()
                          .map(
                              reference ->
                                  new ContainmentCandidate(entry.getKey(), reference.name())))
              .toList();
    } catch (PlatformException ignored) {
      return operation;
    }
    List<ContainmentCandidate> requestedCandidates =
        allCandidates.stream()
            .filter(
                candidate ->
                    blank(operation.sourceElementId())
                        || candidate.ownerId().equals(operation.sourceElementId()))
            .filter(
                candidate ->
                    blank(operation.referenceName())
                        || candidate.referenceName().equals(operation.referenceName()))
            .toList();
    ContainmentCandidate candidate;
    if (requestedCandidates.size() == 1) {
      candidate = requestedCandidates.get(0);
    } else if (allCandidates.size() == 1) {
      candidate = allCandidates.get(0);
    } else {
      return operation;
    }
    return new SemanticModelPatch.Operation(
        operation.type(),
        operation.targetElementId(),
        operation.elementType(),
        operation.attributes(),
        candidate.ownerId(),
        candidate.referenceName());
  }

  private SemanticModelPatch.Operation normalizeRelationshipDirection(
      ModelLevel level, Map<String, String> types, SemanticModelPatch.Operation operation) {
    if (operation == null
        || operation.type() != SemanticModelPatch.OperationType.CONNECT_ELEMENTS
        || blank(operation.referenceName())) {
      return operation;
    }
    String sourceType = types.get(operation.sourceElementId());
    String targetType = types.get(operation.targetElementId());
    if (sourceType == null || targetType == null) {
      return operation;
    }
    if (schemas.acceptsReferenceTarget(level, sourceType, operation.referenceName(), targetType)) {
      return operation;
    }
    if (!schemas.acceptsReferenceTarget(level, targetType, operation.referenceName(), sourceType)) {
      return operation;
    }
    return new SemanticModelPatch.Operation(
        operation.type(),
        operation.sourceElementId(),
        operation.elementType(),
        operation.attributes(),
        operation.targetElementId(),
        operation.referenceName());
  }

  private AssistantTurnPlan repairPlan(
      AssistantSessionStore.AssistantSession session,
      AssistantTurnRequest request,
      AssistantModelContext context,
      List<AssistantModelProvider.ContextSnippet> snippets,
      AssistantTurnPlan failed,
      PlanAttempt attempt,
      int repairNumber) {
    String feedback = attempt.feedback().stream().limit(20).collect(Collectors.joining("\n"));
    List<AssistantModelProvider.ContextSnippet> repairContext = new ArrayList<>();
    repairContext.addAll(
        feedbackResolver.contractsForFeedback(
            session.level(), attempt.feedback(), failed.patch(), 12));
    repairContext.addAll(schemas.planningContracts(session.level(), feedback, 8));
    failed.patch().operations().stream()
        .filter(java.util.Objects::nonNull)
        .map(SemanticModelPatch.Operation::elementType)
        .filter(type -> type != null && !type.isBlank())
        .distinct()
        .forEach(
            type -> {
              try {
                repairContext.add(schemas.typeContract(session.level(), type));
              } catch (PlatformException ignored) {
                // Validator feedback already identifies unknown types.
              }
            });
    attempt.feedback().stream()
        .limit(8)
        .forEach(issue -> repairContext.addAll(catalogs.search(issue, session.level().name(), 2)));
    repairContext.addAll(snippets);
    String requestWithFeedback =
        "Original user request:\n"
            + request.message()
            + "\n\nRejected turn plan:\n"
            + failed
            + "\n\nBackend validation feedback:\n"
            + feedback
            + "\n\n"
            + "Return one complete replacement turn plan. Use PATCH only if you can correct every"
            + " failure. Add the support elements and references explicitly required by validator"
            + " feedback, choosing safe reversible defaults. Do not repeat the rejected plan or"
            + " ask the user to decide how to satisfy a formal constraint; use CLARIFICATION only"
            + " when the missing decision is genuinely a domain choice.";
    return provider.planTurn(
        new AssistantModelProvider.AssistantPrompt(
            AssistantModelRole.PLANNER,
            turnPrompt(session, request, context)
                + "\nThis is validator-guided repair pass "
                + repairNumber
                + " of "
                + properties.validationRepairAttempts()
                + ". Do not repeat rejected operations.",
            requestWithFeedback,
            deduplicate(repairContext, properties.maxContextSnippets())));
  }

  private AssistantTurnResponse clarificationResponse(
      AssistantSessionStore.AssistantSession session,
      String threadId,
      AssistantTurnRequest request,
      ModelRecord model,
      String message,
      List<AssistantChoice> rawQuestions) {
    List<AssistantChoice> questions = normalizeQuestions(rawQuestions);
    if (questions.isEmpty()) {
      questions =
          List.of(
              new AssistantChoice(
                  "modeling-details",
                  "What consequential modeling detail should I use before preparing the proposal?",
                  AssistantChoice.SelectionMode.SINGLE,
                  List.of(),
                  true));
    }
    memory.savePendingInteraction(threadId, request, questions);
    return finishTurn(
        session,
        threadId,
        new AssistantTurnResponse(
            nonBlank(message, "I need one modeling decision before I can continue safely."),
            model == null ? null : model.id(),
            model == null ? null : model.revision(),
            null,
            questions,
            AssistantWorkflowState.WAITING_FOR_CHOICE,
            activityFor(AssistantWorkflowState.WAITING_FOR_CHOICE)));
  }

  private List<AssistantChoice> normalizeQuestions(List<AssistantChoice> questions) {
    if (questions == null) {
      return List.of();
    }
    Set<String> ids = new LinkedHashSet<>();
    List<AssistantChoice> result = new ArrayList<>();
    for (AssistantChoice question : questions.stream().limit(3).toList()) {
      if (question == null || question.id().isBlank() || question.prompt().isBlank()) {
        continue;
      }
      String id = uniqueId(question.id(), ids);
      List<AssistantChoice.Option> options =
          question.options().stream()
              .filter(
                  option -> option != null && !option.id().isBlank() && !option.label().isBlank())
              .limit(5)
              .toList();
      if (options.isEmpty() && !question.allowFreeText()) {
        continue;
      }
      result.add(
          new AssistantChoice(
              id, question.prompt(), question.selectionMode(), options, question.allowFreeText()));
    }
    return result;
  }

  /** Resumes a pending turn with validated structured answers. */
  public AssistantTurnResponse submitChoices(
      UserRecord user, String sessionId, List<ChoiceAnswer> answers) {
    AssistantSessionStore.AssistantSession session = requireSession(sessionId, user.id());
    String threadId = session.id();
    PendingInteractionRecord pending =
        memory
            .pendingInteraction(threadId)
            .orElseThrow(
                () -> new PlatformException(409, "No clarification is awaiting an answer."));
    Map<String, ChoiceAnswer> byId =
        (answers == null ? List.<ChoiceAnswer>of() : answers)
            .stream()
                .collect(
                    Collectors.toMap(
                        ChoiceAnswer::choiceId, value -> value, (left, right) -> right));
    List<String> resolved = new ArrayList<>();
    for (AssistantChoice question : pending.questions()) {
      ChoiceAnswer answer = byId.get(question.id());
      if (answer == null) {
        throw new PlatformException(400, "Every clarification question requires an answer.");
      }
      Set<String> allowed =
          question.options().stream().map(AssistantChoice.Option::id).collect(Collectors.toSet());
      List<String> selected =
          answer.optionIds().stream().filter(allowed::contains).distinct().toList();
      if (question.selectionMode() == AssistantChoice.SelectionMode.SINGLE && selected.size() > 1) {
        throw new PlatformException(400, "A single-select clarification has multiple answers.");
      }
      if (selected.isEmpty() && answer.freeText().isBlank()) {
        throw new PlatformException(400, "Clarification answer is empty.");
      }
      String labels =
          question.options().stream()
              .filter(option -> selected.contains(option.id()))
              .map(option -> option.label() + ": " + option.description())
              .collect(Collectors.joining("; "));
      resolved.add(
          question.prompt()
              + "\nAnswer: "
              + labels
              + (answer.freeText().isBlank() ? "" : "\nAdditional detail: " + answer.freeText()));
    }
    memory.clearPendingInteraction(threadId);
    memory.appendAudit(
        null,
        session.projectId(),
        user.id(),
        "CLARIFICATION_ANSWERED",
        Map.of("questionCount", pending.questions().size()));
    AssistantTurnRequest original = pending.request();
    AssistantTurnRequest resumed =
        new AssistantTurnRequest(
            "Continue the original request using these user-approved clarification answers.\n\n"
                + "Resolve the original intent now. If it requested a model mutation, return a "
                + "PATCH with operations, never an ANSWER that merely claims completion.\n\n"
                + "Original request:\n"
                + original.rootMessage()
                + "\n\nClarification answers:\n"
                + String.join("\n\n", resolved)
                + "\n\nRejected patch summary:\n"
                + summarizePatch(original.unsavedDraftPatch())
                + "\n\n"
                + "Do not ask about IDs, UUIDs, or other formal details the backend can derive.",
            original.modelId(),
            original.revision(),
            original.activeView(),
            original.selectedElementIds(),
            original.unsavedDraftPatch(),
            original.attachmentName(),
            original.attachmentContent(),
            original.rootMessage());
    return handleMessage(user, sessionId, resumed);
  }

  /** Compatibility entry point for older single-choice clients. */
  public void submitChoice(UserRecord user, String sessionId, String choiceId, String optionId) {
    submitChoices(user, sessionId, List.of(new ChoiceAnswer(choiceId, List.of(optionId), "")));
  }

  /** Returns a stored proposal owned by this session. */
  public AssistantProposal proposal(UserRecord user, String sessionId, String proposalId) {
    AssistantSessionStore.AssistantSession session = requireSession(sessionId, user.id());
    return requireProposal(user, session, proposalId).proposal();
  }

  /** Revalidates and explicitly applies a stored proposal. */
  public AssistantTurnResponse approveProposal(
      UserRecord user, String sessionId, String proposalId) {
    AssistantSessionStore.AssistantSession session = requireSession(sessionId, user.id());
    ProposalRecord record = requireProposal(user, session, proposalId);
    if (!"PROPOSED".equals(record.status())) {
      throw new PlatformException(409, "Assistant proposal is no longer awaiting approval.");
    }
    ModelRecord model =
        record.modelId() == null
            ? createStarterModel(user, projects.get(user, session.projectId()), session)
            : models.get(user, session.level(), record.modelId());
    if (record.modelId() != null && model.revision() != record.modelRevision()) {
      memory.updateProposalStatus(record.id(), "FAILED");
      throw new PlatformException(409, "The model changed after this proposal was created.");
    }
    AssistantPatchCompiler.CompiledPatch compiled =
        patchCompiler.compile(model.modelJson(), record.proposal().patch());
    ObjectNode preview = patchCompiler.apply(model.modelJson(), compiled);
    AssistantValidationSummary validation =
        validationSummary(models.validate(session.level(), preview));
    if (!validation.structurallyValid() || !validation.mandatoryPassed()) {
      memory.updateProposalStatus(record.id(), "FAILED");
      memory.appendAudit(
          record.id(),
          session.projectId(),
          user.id(),
          "FAILED",
          Map.of("reason", "Proposal failed approval-time validation."));
      throw new PlatformException(422, "The proposal no longer passes mandatory validation.");
    }
    ModelRecord updated =
        models.patch(
            user, session.level(), model.id(), model.name(), compiled.patch(), model.revision());
    memory.markProposalApplied(record.id(), updated.id(), updated.revision());
    memory.appendAudit(
        record.id(),
        session.projectId(),
        user.id(),
        "APPLIED",
        Map.of("modelId", updated.id(), "revision", updated.revision()));
    realtime.publish(
        sessionId,
        "model.updated",
        Map.of("modelId", updated.id(), "revision", updated.revision(), "proposalId", record.id()));
    return new AssistantTurnResponse(
        "The validated proposal was applied to the canvas.",
        updated.id(),
        updated.revision(),
        record.proposal(),
        List.of(),
        AssistantWorkflowState.APPLIED);
  }

  /** Rejects a stored proposal without touching the model. */
  public void rejectProposal(UserRecord user, String sessionId, String proposalId) {
    AssistantSessionStore.AssistantSession session = requireSession(sessionId, user.id());
    ProposalRecord record = requireProposal(user, session, proposalId);
    if (!"PROPOSED".equals(record.status())) {
      throw new PlatformException(409, "Assistant proposal is no longer awaiting a decision.");
    }
    memory.updateProposalStatus(proposalId, "REJECTED");
    memory.appendAudit(
        proposalId, session.projectId(), user.id(), "REJECTED", Map.of("proposalId", proposalId));
    realtime.publish(sessionId, "proposal.rejected", Map.of("proposalId", proposalId));
  }

  /** Applies a validated inverse patch to an already-applied proposal. */
  public AssistantTurnResponse undoProposal(UserRecord user, String sessionId, String proposalId) {
    AssistantSessionStore.AssistantSession session = requireSession(sessionId, user.id());
    ProposalRecord record = requireProposal(user, session, proposalId);
    if (!"APPLIED".equals(record.status()) || record.proposal().inversePatch().isEmpty()) {
      throw new PlatformException(409, "Only an applied proposal with an inverse can be undone.");
    }
    ModelRecord model = models.get(user, session.level(), record.modelId());
    AssistantPatchCompiler.CompiledPatch inverse =
        new AssistantPatchCompiler.CompiledPatch(
            record.proposal().inversePatch(), List.of(), record.proposal().affectedElements());
    inverse = patchCompiler.adaptToSnapshot(model.modelJson(), inverse);
    ObjectNode preview = patchCompiler.apply(model.modelJson(), inverse);
    AssistantValidationSummary validation =
        validationSummary(models.validate(session.level(), preview));
    if (!validation.structurallyValid() || !validation.mandatoryPassed()) {
      throw new PlatformException(422, "Undo is blocked because mandatory validation would fail.");
    }
    ModelRecord updated =
        models.patch(
            user, session.level(), model.id(), model.name(), inverse.patch(), model.revision());
    memory.updateProposalStatus(record.id(), "UNDONE");
    memory.appendAudit(
        record.id(),
        session.projectId(),
        user.id(),
        "UNDONE",
        Map.of("modelId", updated.id(), "revision", updated.revision()));
    realtime.publish(
        sessionId,
        "model.updated",
        Map.of("modelId", updated.id(), "revision", updated.revision(), "proposalId", record.id()));
    return new AssistantTurnResponse(
        "The proposal was undone.",
        updated.id(),
        updated.revision(),
        record.proposal(),
        List.of(),
        AssistantWorkflowState.UNDONE);
  }

  /** Returns durable thread history for client hydration. */
  public ThreadSnapshot thread(UserRecord user, String sessionId) {
    AssistantSessionStore.AssistantSession session = requireSession(sessionId, user.id());
    String threadId = session.id();
    List<ThreadMessage> messages =
        memory.recentMessages(threadId, properties.hardening().recentMessageWindow()).stream()
            .sorted(java.util.Comparator.comparing(MessageRecord::createdAt))
            .map(
                message ->
                    new ThreadMessage(
                        message.role(),
                        message.content(),
                        workflowStateFromMetadata(message.metadata())))
            .toList();
    List<AssistantChoice> pendingChoices =
        memory
            .pendingInteraction(threadId)
            .map(PendingInteractionRecord::questions)
            .orElse(List.of());
    AssistantWorkflowState workflowState = resolveWorkflowState(threadId, messages, pendingChoices);
    Optional<AssistantProposal> proposal =
        memory.findLatestProposal(threadId, "PROPOSED").map(ProposalRecord::proposal);
    return new ThreadSnapshot(
        messages, pendingChoices, workflowState, proposal.orElse(null), provider.metadata());
  }

  /** Clears runtime and durable conversation state. */
  public void clear(UserRecord user, String sessionId) {
    AssistantSessionStore.AssistantSession session = requireSession(sessionId, user.id());
    String threadId = session.id();
    memory.clearThread(threadId);
    chatMemory.clear(threadId);
    sessions.clear(sessionId, user.id());
  }

  private List<AssistantModelProvider.ContextSnippet> retrievalSnippets(
      String query,
      AssistantModelContext context,
      ModelLevel level,
      List<String> selectedElementIds) {
    boolean emptyModel = modelContexts.isEmptyCanvas(context);
    List<AssistantModelProvider.ContextSnippet> tier1 = new ArrayList<>();
    tier1.addAll(schemas.planningContracts(level, query, 14, emptyModel));
    List<String> validationLines =
        context.validationIssues().stream()
            .map(issue -> issue.constraint() + ": " + issue.message())
            .toList();
    tier1.addAll(
        feedbackResolver.contractsForFeedback(
            level, validationLines, new SemanticModelPatch(List.of()), 8));

    List<AssistantModelProvider.ContextSnippet> tier2 = new ArrayList<>();
    context.validationIssues().stream()
        .limit(8)
        .forEach(
            issue ->
                tier2.add(
                    new AssistantModelProvider.ContextSnippet(
                        "current-validation", issue.constraint(), issue.message())));

    List<AssistantModelProvider.ContextSnippet> tier3 =
        List.of(
            new AssistantModelProvider.ContextSnippet(
                "runtime-metamodel",
                level.name() + " language index",
                schemas.languageIndex(level)));

    List<AssistantModelProvider.ContextSnippet> tier4 = new ArrayList<>();
    List<AssistantModelProvider.ContextSnippet> matches = catalogs.search(query, level.name(), 14);
    tier4.addAll(matches);
    if (emptyModel && isCreationRequest(query)) {
      tier4.addAll(catalogs.search(query, level.name(), 6));
    }
    matches.stream()
        .map(AssistantModelProvider.ContextSnippet::title)
        .distinct()
        .limit(8)
        .forEach(title -> tier4.addAll(catalogs.describeType(title, level.name(), 8)));
    schemas.relevantTypes(level, query, emptyModel, 8).stream()
        .distinct()
        .forEach(type -> tier4.addAll(catalogs.describeType(type, level.name(), 6)));

    Set<String> selectedTypes = selectedElementTypes(context, selectedElementIds);
    for (String type : selectedTypes) {
      tier4.addAll(0, catalogs.describeType(type, level.name(), 6));
    }
    context.validationIssues().stream()
        .map(AssistantValidationSummary.Issue::constraint)
        .filter(constraint -> constraint != null && !constraint.isBlank())
        .distinct()
        .limit(6)
        .forEach(constraint -> tier4.addAll(0, catalogs.search(constraint, level.name(), 4)));

    return AssistantSnippetBudget.assemble(properties, tier1, tier2, tier3, tier4);
  }

  private Set<String> selectedElementTypes(
      AssistantModelContext context, List<String> selectedElementIds) {
    if (selectedElementIds == null || selectedElementIds.isEmpty()) {
      return Set.of();
    }
    Set<String> selectedIds = new LinkedHashSet<>(selectedElementIds);
    return context.elements().stream()
        .filter(element -> selectedIds.contains(element.id()))
        .map(ContextElement::type)
        .filter(type -> type != null && !type.isBlank())
        .collect(Collectors.toCollection(LinkedHashSet::new));
  }

  private List<AssistantModelProvider.ContextSnippet> deduplicateCompact(
      List<AssistantModelProvider.ContextSnippet> snippets, int limit) {
    Map<String, AssistantModelProvider.ContextSnippet> result = new LinkedHashMap<>();
    for (AssistantModelProvider.ContextSnippet snippet : snippets) {
      if (snippet == null || result.size() >= limit) {
        continue;
      }
      String content = snippet.content() == null ? "" : snippet.content().trim();
      int max = snippet.source().startsWith("runtime-") ? 5000 : 2200;
      if (content.length() > max) {
        content = content.substring(0, max) + "\n[truncated]";
      }
      AssistantModelProvider.ContextSnippet compact =
          new AssistantModelProvider.ContextSnippet(snippet.source(), snippet.title(), content);
      result.putIfAbsent(snippet.source() + "#" + snippet.title(), compact);
    }
    return List.copyOf(result.values());
  }

  private List<AssistantModelProvider.ContextSnippet> deduplicate(
      List<AssistantModelProvider.ContextSnippet> snippets, int limit) {
    return deduplicateCompact(snippets, limit);
  }

  private String turnPrompt(
      AssistantSessionStore.AssistantSession session,
      AssistantTurnRequest request,
      AssistantModelContext context) {
    return """
    You are operating a formal modeling workbench in guarded-apply mode. Infer the user's
    natural-language intent with the LLM; do not rely on keyword routing. The current model,
    Ecore-derived language catalog, concrete-syntax metadata, and executable EVL findings are
    backend-owned facts. Never invent an EClass, feature, enum literal, existing ID, or
    containment. Use exact stable IDs from the model context.

    For a requested model change, create a semantically complete model for the user's actual
    domain and stated scope. Use as many operations as the task genuinely requires, up to the
    configured operation limit.
    ADD_ELEMENT may mint unique stable IDs. A top-level element omits sourceElementId; an owned
    element names its exact containment owner and feature. CONNECT_ELEMENTS names an exact,
    writable, non-containment EReference. SET_ATTRIBUTE uses the attribute name and puts the new
    scalar or array value directly in attributes. DELETE_ELEMENT is permitted only when the user
    explicitly requests removal. Include every required attribute and containment described by
    the retrieved metamodel. Ask concise questions before planning when consequential intent is
    genuinely ambiguous. The backend will compile and validate every operation and the user must
    approve every valid proposal before application.

    Required containment examples derived from the runtime schema:
    """
        + schemas.containmentPlanningExamples(session.level(), 12)
        + "\nSchema enum defaults (use when not specified by the user): "
        + schemas.planningDefaults(session.level())
        + """
        """
        + "\nProject ID: "
        + session.projectId()
        + "\nLevel: "
        + session.level()
        + "\nActive view: "
        + nonBlank(request.activeView(), "unknown")
        + "\nSelected stable IDs: "
        + request.selectedElementIds()
        + (request.selectedElementIds() == null || request.selectedElementIds().isEmpty()
            ? ""
            : "\n" + modelContexts.focusContext(context, request.selectedElementIds(), schemas))
        + attachmentSection(request)
        + (blank(request.unsavedDraftPatch())
            ? ""
            : "\nUnsaved local draft is included in the planning base model for this turn.")
        + "\nMaximum operations: "
        + properties.maxToolCalls()
        + (modelContexts.isEmptyCanvas(context)
            ? "\n\nEmpty canvas guidance:\n"
                + schemas.domainCreationBlueprint(session.level(), request.rootMessage())
            : "")
        + "\n\nConversation memory:\n"
        + conversationMemory(session)
        + "\n\nCurrent model context:\n"
        + modelContexts.summarize(context);
  }

  private void validateSemanticPatch(
      ModelLevel level,
      SemanticModelPatch patch,
      AssistantModelContext context,
      JsonNode baseModel) {
    if (patch.operations().size() > properties.maxToolCalls()) {
      throw new PlatformException(422, "The proposal exceeds the configured operation limit.");
    }
    Map<String, String> types =
        context.elements().stream()
            .collect(
                Collectors.toMap(
                    ContextElement::id,
                    ContextElement::type,
                    (left, right) -> left,
                    LinkedHashMap::new));
    seedRootType(level, baseModel, types);
    for (SemanticModelPatch.Operation operation : patch.operations()) {
      if (operation == null || operation.type() == null) {
        throw new PlatformException(422, "A semantic operation has no type.");
      }
      switch (operation.type()) {
        case ADD_ELEMENT -> {
          if (blank(operation.targetElementId())
              || types.containsKey(operation.targetElementId())) {
            throw new PlatformException(
                422, "A new element has an invalid or duplicate stable ID.");
          }
          String type = schemas.canonicalType(level, operation.elementType());
          if (!blank(operation.sourceElementId())) {
            String ownerType = types.get(operation.sourceElementId());
            if (ownerType == null || blank(operation.referenceName())) {
              throw new PlatformException(422, "A contained element has an unknown owner.");
            }
            schemas.requireContainment(level, ownerType, operation.referenceName(), type);
          } else if (schemas.rootCollection(level, type).isEmpty()) {
            throw new PlatformException(422, "A contained-only element is missing its owner.");
          }
          types.put(operation.targetElementId(), type);
        }
        case CONNECT_ELEMENTS -> {
          String sourceType = types.get(operation.sourceElementId());
          if (sourceType == null
              || !types.containsKey(operation.targetElementId())
              || blank(operation.referenceName())
              || schemas
                  .reference(level, sourceType, operation.referenceName())
                  .filter(reference -> !reference.containment() && !reference.readonly())
                  .isEmpty()) {
            throw new PlatformException(422, "A relationship is not grounded in the metamodel.");
          }
        }
        case SET_ATTRIBUTE -> {
          String targetType = types.get(operation.targetElementId());
          if (targetType == null
              || blank(operation.referenceName())
              || schemas.attribute(level, targetType, operation.referenceName()).isEmpty()) {
            throw new PlatformException(
                422, "An attribute update is not grounded in the metamodel.");
          }
        }
        case DELETE_ELEMENT -> {
          if (!types.containsKey(operation.targetElementId())) {
            throw new PlatformException(422, "A deletion targets an unknown stable ID.");
          }
          types.remove(operation.targetElementId());
        }
      }
    }
  }

  private void seedRootType(ModelLevel level, JsonNode baseModel, Map<String, String> types) {
    if (baseModel == null || !baseModel.isObject()) {
      return;
    }
    String id = baseModel.path("id").asText("");
    String eClass = baseModel.path("eClass").asText("");
    if (!id.isBlank() && !eClass.isBlank()) {
      types.putIfAbsent(id, schemas.canonicalType(level, eClass));
    }
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
                            issue.constraint(),
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

  private boolean shouldAutoApply(
      AssistantTurnRequest request, AssistantTurnPlan plan, AssistantProposal.RiskLevel risk) {
    if (risk != AssistantProposal.RiskLevel.LOW) {
      return false;
    }
    if (wantsExplicitReview(request.rootMessage())) {
      return false;
    }
    if (plan.patch().operations().size() > properties.maxAutoApplyOperations()) {
      return false;
    }
    return plan.patch().operations().stream()
        .noneMatch(
            operation -> operation.type() == SemanticModelPatch.OperationType.DELETE_ELEMENT);
  }

  private boolean wantsExplicitReview(String message) {
    String normalized = message == null ? "" : message.toLowerCase(java.util.Locale.ROOT);
    return normalized.contains("propose")
        || normalized.contains("review")
        || normalized.contains("don't apply")
        || normalized.contains("do not apply");
  }

  private AssistantTurnResponse autoApplyValidatedProposal(
      UserRecord user,
      AssistantSessionStore.AssistantSession session,
      String threadId,
      AssistantTurnRequest request,
      ModelRecord model,
      AssistantTurnPlan acceptedPlan,
      PlanAttempt attempt,
      List<AssistantModelProvider.ContextSnippet> snippets,
      AssistantModelContext context,
      AssistantProposal.RiskLevel risk) {
    AssistantPatchCompiler.CompiledPatch compiled = attempt.compiled();
    ProjectRecord project = projects.get(user, session.projectId());
    ModelRecord targetModel = model == null ? createStarterModel(user, project, session) : model;
    ModelRecord updated =
        models.patch(
            user,
            session.level(),
            targetModel.id(),
            targetModel.name(),
            compiled.patch(),
            targetModel.revision());
    AssistantProposal proposal =
        new AssistantProposal(
            java.util.UUID.randomUUID().toString(),
            compiled.affectedElements(),
            acceptedPlan.patch(),
            compiled.inversePatch(),
            attempt.validation(),
            risk,
            false,
            retrievalCitations(snippets, context),
            Instant.now());
    memory.clearPendingInteraction(threadId);
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
    realtime.publish(
        session.id(),
        "model.updated",
        Map.of(
            "modelId", updated.id(),
            "revision", updated.revision(),
            "proposalId", proposal.id()));
    String message =
        nonBlank(acceptedPlan.message(), "I applied the requested change.")
            + "\n\nThe change was low risk and passed mandatory validation, so it was applied "
            + "automatically. You can undo it from the card below.";
    return finishTurn(
        session,
        threadId,
        new AssistantTurnResponse(
            message,
            updated.id(),
            updated.revision(),
            proposal,
            List.of(),
            AssistantWorkflowState.APPLIED,
            activityFor(AssistantWorkflowState.APPLIED)));
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
        snippets.stream()
            .map(snippet -> snippet.source() + "#" + snippet.title())
            .filter(value -> !value.isBlank())
            .distinct()
            .limit(12)
            .collect(Collectors.toCollection(ArrayList::new));
    context.validationIssues().stream()
        .map(issue -> issue.constraint())
        .filter(value -> value != null && !value.isBlank())
        .limit(6)
        .forEach(result::add);
    return result.stream().distinct().toList();
  }

  private AssistantTurnResponse finishTurn(
      AssistantSessionStore.AssistantSession session,
      String threadId,
      AssistantTurnResponse response) {
    AssistantTurnResponse enriched =
        response.activity() == null
            ? new AssistantTurnResponse(
                response.assistantMessage(),
                response.modelId(),
                response.revision(),
                response.proposal(),
                response.choices(),
                response.workflowState(),
                activityFor(response.workflowState()))
            : response;
    memory.appendMessage(
        threadId,
        "ASSISTANT",
        enriched.assistantMessage(),
        Map.of("workflowState", enriched.workflowState().name()));
    chatMemory.appendAssistant(threadId, enriched.assistantMessage());
    updateRollingSummary(threadId);
    realtime.publish(session.id(), "chat.assistant", enriched);
    lastActivity.remove();
    return enriched;
  }

  private AssistantActivity activityFor(AssistantWorkflowState workflowState) {
    AssistantActivity current = lastActivity.get();
    if (current == null) {
      return new AssistantActivity(
          terminalStage(workflowState), terminalMessage(workflowState), workflowState);
    }
    return new AssistantActivity(current.stage(), current.message(), workflowState);
  }

  private String terminalStage(AssistantWorkflowState workflowState) {
    return switch (workflowState) {
      case PROPOSED -> "COMPLETED";
      case WAITING_FOR_CHOICE -> "WAITING";
      case FAILED -> "FAILED";
      default -> "COMPLETED";
    };
  }

  private String terminalMessage(AssistantWorkflowState workflowState) {
    return switch (workflowState) {
      case PROPOSED -> "Proposal ready for review";
      case APPLIED -> "Applied automatically";
      case WAITING_FOR_CHOICE -> "Waiting for your decision";
      case FAILED -> "Could not complete the request";
      case EXPLAINED -> "Analysis complete";
      default -> "Ready";
    };
  }

  private void publishProgress(String sessionId, String stage, String message) {
    lastActivity.set(new AssistantActivity(stage, message, null));
    realtime.publish(sessionId, "assistant.progress", Map.of("stage", stage, "message", message));
  }

  private String summarizePatch(String unsavedDraftPatch) {
    return blank(unsavedDraftPatch) ? "none" : unsavedDraftPatch.trim();
  }

  private JsonNode resolvePlanningBase(
      ModelLevel level, JsonNode persistedModel, String unsavedDraftPatch) {
    if (blank(unsavedDraftPatch)) {
      return persistedModel;
    }
    String draft = unsavedDraftPatch.trim();
    try {
      JsonNode parsed = mapper.readTree(draft);
      if (parsed.isObject() && parsed.has("eClass")) {
        return parsed;
      }
      SemanticModelPatch patch = mapper.readValue(draft, SemanticModelPatch.class);
      if (patch.operations().isEmpty()) {
        return persistedModel;
      }
      AssistantPatchCompiler.CompiledPatch compiled = patchCompiler.compile(persistedModel, patch);
      return patchCompiler.apply(persistedModel, compiled);
    } catch (Exception ex) {
      log.warn("Could not merge unsaved draft patch; using persisted model instead.");
      return persistedModel;
    }
  }

  private boolean prefersInformationPath(String message) {
    if (blank(message)) {
      return true;
    }
    String normalized = message.toLowerCase(java.util.Locale.ROOT);
    boolean explain =
        normalized.matches(
            "(?s).*(\\bexplain\\b|\\banaly[sz]e\\b|\\bwhat is\\b|\\bhow does\\b|\\bwhy"
                + " does\\b|\\bdescribe\\b|\\bcompare\\b|\\breview\\b(?![^\\n"
                + "]*\\b(create|add|build|connect|delete|remove|change|set|rename|update)\\b)).*");
    boolean mutate =
        normalized.matches(
            "(?s).*(\\bcreate\\b|\\badd\\b|\\bbuild\\b|\\bconnect\\b|\\bdelete\\b|\\bremove\\b|\\bchange\\b|\\bset\\b"
                + "|\\brename\\b|\\bupdate\\b|\\bexpand\\b|\\brefine\\b|\\bmerge\\b|\\bsplit\\b).*");
    return explain && !mutate;
  }

  private String attachmentSection(AssistantTurnRequest request) {
    if (blank(request.attachmentName()) && blank(request.attachmentContent())) {
      return "";
    }
    String content = request.attachmentContent() == null ? "" : request.attachmentContent().trim();
    if (content.length() > 4000) {
      content = content.substring(0, 4000) + "\n...[attachment truncated]";
    }
    return "\nAttached context file: "
        + nonBlank(request.attachmentName(), "attachment")
        + "\n"
        + content;
  }

  private String evalCategory(AssistantTurnRequest request, AssistantModelContext context) {
    if (request.selectedElementIds() != null && !request.selectedElementIds().isEmpty()) {
      return "selected-element";
    }
    if (!modelContexts.isEmptyCanvas(context)) {
      return "refine";
    }
    String message = request.rootMessage().toLowerCase(java.util.Locale.ROOT);
    if (message.contains("validate") || message.contains("repair") || message.contains("fix")) {
      return "validate-repair";
    }
    if (prefersInformationPath(message)) {
      return "explain-only";
    }
    return switch (context.level()) {
      case CIM -> "cim";
      case PSM -> "psm";
      default -> "create-empty";
    };
  }

  private void recordTurnDiagnostics(
      String stage,
      int snippetCount,
      int toolCalls,
      int repairAttempts,
      String outcome,
      long startedAt) {
    AssistantTurnDiagnostics diagnostics =
        new AssistantTurnDiagnostics(
            stage,
            snippetCount,
            toolCalls,
            repairAttempts,
            outcome,
            Math.max(0L, System.currentTimeMillis() - startedAt));
    lastDiagnostics.set(diagnostics);
    log.info(
        "Assistant turn diagnostics stage={} snippets={} toolCalls={} repairAttempts={} outcome={}"
            + " latencyMs={}",
        diagnostics.stage(),
        diagnostics.snippetCount(),
        diagnostics.toolCalls(),
        diagnostics.repairAttempts(),
        diagnostics.outcome(),
        diagnostics.latencyMs());
  }

  private AssistantWorkflowState resolveWorkflowState(
      String threadId, List<ThreadMessage> messages, List<AssistantChoice> pendingChoices) {
    if (!pendingChoices.isEmpty()) {
      return AssistantWorkflowState.WAITING_FOR_CHOICE;
    }
    if (memory.findLatestProposal(threadId, "PROPOSED").isPresent()) {
      return AssistantWorkflowState.PROPOSED;
    }
    for (int index = messages.size() - 1; index >= 0; index--) {
      AssistantWorkflowState workflowState = messages.get(index).workflowState();
      if (workflowState != null && workflowState != AssistantWorkflowState.WAITING_FOR_CHOICE) {
        return workflowState;
      }
    }
    return AssistantWorkflowState.EXPLAINED;
  }

  private AssistantWorkflowState workflowStateFromMetadata(Map<String, Object> metadata) {
    if (metadata == null || metadata.isEmpty()) {
      return null;
    }
    Object value = metadata.get("workflowState");
    if (value == null) {
      return null;
    }
    try {
      return AssistantWorkflowState.valueOf(String.valueOf(value));
    } catch (IllegalArgumentException ex) {
      return null;
    }
  }

  private void appendUserMessage(String threadId, String sessionId, AssistantTurnRequest request) {
    Map<String, Object> metadata = new LinkedHashMap<>();
    metadata.put("sessionId", sessionId);
    if (request.modelId() != null) {
      metadata.put("modelId", request.modelId());
    }
    if (request.revision() != null) {
      metadata.put("revision", request.revision());
    }
    boolean firstUserMessage = memory.userMessageCount(threadId) == 0;
    memory.appendMessage(threadId, "USER", request.message(), metadata);
    if (firstUserMessage && !blank(request.message())) {
      memory.updateThreadTitle(threadId, summarizeForTitle(request.message()));
    }
    chatMemory.appendUser(threadId, request.message());
  }

  private String summarizeForTitle(String message) {
    String trimmed = message.trim().replaceAll("\\s+", " ");
    if (trimmed.length() <= 72) {
      return trimmed;
    }
    return trimmed.substring(0, 69).trim() + "...";
  }

  private String conversationTitle(
      io.mehdieidi.modless.platform.assistant.domain.memory.AssistantMemoryRecords
              .ConversationSummary
          conversation) {
    if (!blank(conversation.title()) && !isDefaultAssistantTitle(conversation.title())) {
      return conversation.title().trim();
    }
    return summarizeForTitle(conversation.preview() == null ? "" : conversation.preview());
  }

  private String conversationPreview(
      io.mehdieidi.modless.platform.assistant.domain.memory.AssistantMemoryRecords
              .ConversationSummary
          conversation) {
    if (!blank(conversation.preview())) {
      return summarizeForTitle(conversation.preview());
    }
    if (!blank(conversation.title()) && !isDefaultAssistantTitle(conversation.title())) {
      return conversation.title().trim();
    }
    return "New conversation";
  }

  private boolean isDefaultAssistantTitle(String title) {
    String normalized = title == null ? "" : title.trim().toLowerCase();
    return normalized.endsWith("-assistant") || normalized.equals("assistant");
  }

  private String conversationMemory(AssistantSessionStore.AssistantSession session) {
    String threadId = session.id();
    String summary = memory.summary(threadId).orElse("");
    String recent =
        chatMemory.recent(threadId, properties.hardening().recentMessageWindow()).stream()
            .map(message -> message.role() + ": " + message.content())
            .collect(Collectors.joining("\n"));
    return summary.isBlank() ? recent : "Summary: " + summary + "\nRecent:\n" + recent;
  }

  private void updateRollingSummary(String threadId) {
    List<MessageRecord> recent =
        memory.recentMessages(threadId, properties.hardening().recentMessageWindow());
    if (recent.isEmpty()) {
      return;
    }
    String conversation =
        recent.stream()
            .sorted(java.util.Comparator.comparing(MessageRecord::createdAt))
            .map(message -> message.role() + ": " + message.content())
            .collect(Collectors.joining("\n"));
    String previousSummary = memory.summary(threadId).orElse("");
    String summary = summarizeConversation(previousSummary, conversation);
    memory.updateSummary(threadId, summary, recent.get(0).id());
  }

  private String summarizeConversation(String previousSummary, String conversation) {
    if (!properties.enabled() || !provider.available()) {
      return tailTruncate(conversation, 3000);
    }
    try {
      String prompt =
          """
          Compress this modeling assistant thread into a concise rolling summary. Preserve the
          domain name, modeling scope, key decisions, and any pending work. Do not invent facts.

          Previous summary:
          """
              + (previousSummary.isBlank() ? "(none)" : previousSummary)
              + "\n\nRecent messages:\n"
              + tailTruncate(conversation, 6000);
      AssistantModelProvider.AssistantReply reply =
          provider.complete(
              new AssistantModelProvider.AssistantPrompt(
                  AssistantModelRole.SUMMARIZER,
                  "You summarize modeling conversations for later turns.",
                  prompt,
                  List.of()));
      String content = reply.content() == null ? "" : reply.content().trim();
      if (content.isBlank()) {
        return tailTruncate(conversation, 3000);
      }
      return content.length() > 3000 ? content.substring(0, 3000) : content;
    } catch (RuntimeException ex) {
      log.warn("Assistant summarizer failed; falling back to tail truncation.", ex);
      return tailTruncate(conversation, 3000);
    }
  }

  private String tailTruncate(String value, int maxChars) {
    if (value == null || value.length() <= maxChars) {
      return value == null ? "" : value;
    }
    return value.substring(value.length() - maxChars);
  }

  private ProposalRecord requireProposal(
      UserRecord user, AssistantSessionStore.AssistantSession session, String proposalId) {
    String threadId = session.id();
    return memory
        .findProposal(proposalId)
        .filter(record -> record.threadId().equals(threadId))
        .orElseThrow(() -> new PlatformException(404, "Assistant proposal not found."));
  }

  private ModelRecord createStarterModel(
      UserRecord user, ProjectRecord project, AssistantSessionStore.AssistantSession session) {
    String name = nonBlank(session.title(), session.level().apiName() + "-model");
    ModelRecord created =
        models.create(
            user,
            session.level(),
            project.id(),
            name,
            modelingConfig.starterModel(session.level(), name));
    Map<String, String> active =
        new LinkedHashMap<>(project.activeModelIds() == null ? Map.of() : project.activeModelIds());
    active.put(session.level().apiName(), created.id());
    projects.update(user, project.id(), project.name(), project.description(), active);
    memory.updateThreadModel(session.id(), created.id(), created.revision());
    return created;
  }

  private AssistantSessionStore.AssistantSession requireSession(String sessionId, String userId) {
    try {
      return sessions.require(sessionId, userId);
    } catch (PlatformException failure) {
      if (failure.status() != 404) {
        throw failure;
      }
      return memory
          .findThread(sessionId, userId)
          .map(sessions::fromThread)
          .orElseThrow(() -> failure);
    }
  }

  private void ensureDurableThread(
      UserRecord user, AssistantSessionStore.AssistantSession session) {
    if (memory.requireThread(session.id()) != null) {
      return;
    }
    ProjectRecord project = projects.get(user, session.projectId());
    String modelId = activeModelId(project, session.level());
    Long revision = null;
    if (!blank(modelId)) {
      try {
        revision = models.get(user, session.level(), modelId).revision();
      } catch (PlatformException failure) {
        if (failure.status() != 404) {
          throw failure;
        }
      }
    }
    memory.createThread(
        session.id(),
        user,
        session.projectId(),
        session.level(),
        session.title(),
        modelId,
        revision);
  }

  private void requireCurrentRevision(Long requestedRevision, ModelRecord model) {
    if (model != null
        && requestedRevision != null
        && requestedRevision.longValue() != model.revision()) {
      throw new PlatformException(
          409,
          "The active model changed from revision "
              + requestedRevision
              + " to "
              + model.revision()
              + ". Refresh before requesting a proposal.");
    }
  }

  private String resolveModelId(String requested, ProjectRecord project, ModelLevel level) {
    return blank(requested) ? activeModelId(project, level) : requested.trim();
  }

  private String activeModelId(ProjectRecord project, ModelLevel level) {
    if (project.activeModelIds() == null) {
      return null;
    }
    String value = project.activeModelIds().get(level.apiName());
    return blank(value) ? project.activeModelIds().get(level.name()) : value;
  }

  private String uniqueId(String requested, Set<String> used) {
    String base = requested.trim().replaceAll("[^A-Za-z0-9_-]", "-");
    if (base.isBlank()) {
      base = "question";
    }
    String candidate = base;
    int suffix = 2;
    while (!used.add(candidate)) {
      candidate = base + "-" + suffix++;
    }
    return candidate;
  }

  private boolean blank(String value) {
    return value == null || value.isBlank();
  }

  private String nonBlank(String value, String fallback) {
    return blank(value) ? fallback : value.trim();
  }

  /** One assistant turn request. */
  public record AssistantTurnRequest(
      String message,
      String modelId,
      Long revision,
      String activeView,
      List<String> selectedElementIds,
      String unsavedDraftPatch,
      String attachmentName,
      String attachmentContent,
      String rootMessage) {
    public AssistantTurnRequest(
        String message,
        String modelId,
        Long revision,
        String activeView,
        List<String> selectedElementIds,
        String unsavedDraftPatch) {
      this(
          message,
          modelId,
          revision,
          activeView,
          selectedElementIds,
          unsavedDraftPatch,
          null,
          null,
          message);
    }

    public AssistantTurnRequest(
        String message,
        String modelId,
        Long revision,
        String activeView,
        List<String> selectedElementIds,
        String unsavedDraftPatch,
        String attachmentName,
        String attachmentContent) {
      this(
          message,
          modelId,
          revision,
          activeView,
          selectedElementIds,
          unsavedDraftPatch,
          attachmentName,
          attachmentContent,
          message);
    }

    public AssistantTurnRequest(
        String message,
        String modelId,
        Long revision,
        String activeView,
        List<String> selectedElementIds,
        String unsavedDraftPatch,
        String rootMessage) {
      this(
          message,
          modelId,
          revision,
          activeView,
          selectedElementIds,
          unsavedDraftPatch,
          null,
          null,
          rootMessage);
    }

    public AssistantTurnRequest {
      message = message == null ? "" : message.trim();
      selectedElementIds = selectedElementIds == null ? List.of() : List.copyOf(selectedElementIds);
      rootMessage = rootMessage == null || rootMessage.isBlank() ? message : rootMessage.trim();
      attachmentName = attachmentName == null ? "" : attachmentName.trim();
      attachmentContent = attachmentContent == null ? "" : attachmentContent;
    }
  }

  /** One assistant turn response. */
  public record AssistantTurnResponse(
      String assistantMessage,
      String modelId,
      Long revision,
      AssistantProposal proposal,
      List<AssistantChoice> choices,
      AssistantWorkflowState workflowState,
      AssistantActivity activity) {
    public AssistantTurnResponse(
        String assistantMessage,
        String modelId,
        Long revision,
        AssistantProposal proposal,
        List<AssistantChoice> choices,
        AssistantWorkflowState workflowState) {
      this(assistantMessage, modelId, revision, proposal, choices, workflowState, null);
    }

    public AssistantTurnResponse {
      choices = choices == null ? List.of() : List.copyOf(choices);
      workflowState = workflowState == null ? AssistantWorkflowState.EXPLAINED : workflowState;
    }
  }

  /** HTTP-visible assistant activity snapshot. */
  public record AssistantActivity(
      String stage, String message, AssistantWorkflowState workflowState) {}

  /** Conversation list entry for history browsing. */
  public record ConversationSummary(
      String sessionId, String title, String preview, Instant updatedAt, int messageCount) {}

  /** Durable thread snapshot for client hydration. */
  public record ThreadSnapshot(
      List<ThreadMessage> messages,
      List<AssistantChoice> pendingChoices,
      AssistantWorkflowState workflowState,
      AssistantProposal proposal,
      AssistantModelProvider.AssistantProviderMetadata provider) {}

  /** One durable thread message. */
  public record ThreadMessage(String role, String content, AssistantWorkflowState workflowState) {}

  /** One submitted clarification answer. */
  public record ChoiceAnswer(String choiceId, List<String> optionIds, String freeText) {
    public ChoiceAnswer {
      choiceId = choiceId == null ? "" : choiceId;
      optionIds = optionIds == null ? List.of() : List.copyOf(optionIds);
      freeText = freeText == null ? "" : freeText.trim();
    }
  }

  private record PlanAttempt(
      AssistantTurnPlan plan,
      AssistantPatchCompiler.CompiledPatch compiled,
      AssistantValidationSummary validation,
      List<String> feedback) {
    static PlanAttempt success(
        AssistantPatchCompiler.CompiledPatch compiled, AssistantValidationSummary validation) {
      return new PlanAttempt(null, compiled, validation, List.of());
    }

    static PlanAttempt failure(String feedback) {
      return new PlanAttempt(null, null, null, List.of(feedback));
    }

    static PlanAttempt failure(
        AssistantPatchCompiler.CompiledPatch compiled, AssistantValidationSummary validation) {
      List<String> feedback =
          validation.issues().stream()
              .filter(issue -> "ERROR".equalsIgnoreCase(issue.severity()))
              .map(issue -> issue.constraint() + ": " + issue.message())
              .toList();
      return new PlanAttempt(
          null,
          compiled,
          validation,
          feedback.isEmpty() ? List.of("Mandatory validation failed.") : feedback);
    }

    PlanAttempt withPlan(AssistantTurnPlan plan) {
      return new PlanAttempt(plan, compiled, validation, feedback);
    }

    boolean valid() {
      return compiled != null
          && validation != null
          && validation.structurallyValid()
          && validation.mandatoryPassed();
    }
  }

  private record ContainmentCandidate(String ownerId, String referenceName) {}
}
