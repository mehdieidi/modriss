package io.mehdieidi.varka.platform.assistant.agent;

import io.mehdieidi.varka.platform.assistant.application.ProviderCallBudget;
import io.mehdieidi.varka.platform.assistant.application.ProviderRequestContext;
import io.mehdieidi.varka.platform.assistant.domain.ModelCommandBatch;
import io.mehdieidi.varka.platform.assistant.metamodel.LexicalRetrievalIndex;
import io.mehdieidi.varka.platform.assistant.metamodel.MetamodelGuideGenerator;
import io.mehdieidi.varka.platform.assistant.metamodel.MetamodelKnowledgeService.AttributeContract;
import io.mehdieidi.varka.platform.assistant.metamodel.MetamodelKnowledgeService.ReferenceContract;
import io.mehdieidi.varka.platform.assistant.metamodel.MetamodelKnowledgeService.TypeContract;
import io.mehdieidi.varka.platform.assistant.prompt.PromptTemplates;
import io.mehdieidi.varka.platform.assistant.provider.AssistantModelProvider;
import io.mehdieidi.varka.platform.assistant.provider.AssistantModelProvider.AssistantPrompt;
import io.mehdieidi.varka.platform.assistant.spi.AssistantMetrics;
import io.mehdieidi.varka.platform.assistant.spi.AssistantRealtimePublisher;
import io.mehdieidi.varka.platform.assistant.tools.AgentModelTools;
import io.mehdieidi.varka.platform.assistant.workspace.ModelWorkspace;
import io.mehdieidi.varka.platform.kernel.ModelLevel;
import io.mehdieidi.varka.platform.kernel.PlatformException;
import io.mehdieidi.varka.platform.model.application.ModelService;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Bounded non-streaming coding-agent-style turn loop over a validated model workspace. */
public final class AgentTurnLoop {

  private final AssistantModelProvider provider;
  private final AgentModelTools tools;
  private final MetamodelGuideGenerator guides;
  private final LexicalRetrievalIndex retrieval;
  private final AssistantRealtimePublisher realtime;
  private final Duration timeout;
  private final Duration sourceTimeout;
  private final int maxSteps;
  private final int maxProviderCalls;
  private final int maxSourceProviderCalls;
  private final AgentActionCodec actions;
  private final AssistantMetrics metrics;
  private final ObjectMapper mapper = new ObjectMapper();
  private final Map<String, AtomicBoolean> cancellations = new ConcurrentHashMap<>();

  public AgentTurnLoop(
      AssistantModelProvider provider,
      AgentModelTools tools,
      MetamodelGuideGenerator guides,
      AssistantRealtimePublisher realtime,
      Duration timeout,
      int maxSteps) {
    this(provider, tools, guides, realtime, timeout, timeout, maxSteps);
  }

  public AgentTurnLoop(
      AssistantModelProvider provider,
      AgentModelTools tools,
      MetamodelGuideGenerator guides,
      AssistantRealtimePublisher realtime,
      Duration timeout,
      Duration sourceTimeout,
      int maxSteps) {
    this(provider, tools, guides, realtime, timeout, sourceTimeout, maxSteps, 3);
  }

  public AgentTurnLoop(
      AssistantModelProvider provider,
      AgentModelTools tools,
      MetamodelGuideGenerator guides,
      AssistantRealtimePublisher realtime,
      Duration timeout,
      Duration sourceTimeout,
      int maxSteps,
      int maxProviderCalls) {
    this(
        provider,
        tools,
        guides,
        null,
        realtime,
        timeout,
        sourceTimeout,
        maxSteps,
        maxProviderCalls,
        Math.max(maxProviderCalls, 4),
        null);
  }

  /** Creates an explicit loop with a local retrieval-only context selector. */
  public AgentTurnLoop(
      AssistantModelProvider provider,
      AgentModelTools tools,
      MetamodelGuideGenerator guides,
      LexicalRetrievalIndex retrieval,
      AssistantRealtimePublisher realtime,
      Duration timeout,
      Duration sourceTimeout,
      int maxSteps,
      int maxProviderCalls) {
    this(
        provider,
        tools,
        guides,
        retrieval,
        realtime,
        timeout,
        sourceTimeout,
        maxSteps,
        maxProviderCalls,
        Math.max(maxProviderCalls, 4),
        null);
  }

  /** Creates a loop with distinct call budgets for ordinary and source-backed work. */
  public AgentTurnLoop(
      AssistantModelProvider provider,
      AgentModelTools tools,
      MetamodelGuideGenerator guides,
      LexicalRetrievalIndex retrieval,
      AssistantRealtimePublisher realtime,
      Duration timeout,
      Duration sourceTimeout,
      int maxSteps,
      int maxProviderCalls,
      int maxSourceProviderCalls) {
    this(
        provider,
        tools,
        guides,
        retrieval,
        realtime,
        timeout,
        sourceTimeout,
        maxSteps,
        maxProviderCalls,
        maxSourceProviderCalls,
        null);
  }

  /** Creates a loop with explicit observability for cost and repair diagnostics. */
  public AgentTurnLoop(
      AssistantModelProvider provider,
      AgentModelTools tools,
      MetamodelGuideGenerator guides,
      LexicalRetrievalIndex retrieval,
      AssistantRealtimePublisher realtime,
      Duration timeout,
      Duration sourceTimeout,
      int maxSteps,
      int maxProviderCalls,
      int maxSourceProviderCalls,
      AssistantMetrics metrics) {
    this.provider = provider;
    this.tools = tools;
    this.guides = guides;
    this.retrieval = retrieval;
    this.realtime = realtime;
    this.timeout = timeout == null ? Duration.ofMinutes(5) : timeout;
    this.sourceTimeout = sourceTimeout == null ? this.timeout : sourceTimeout;
    this.maxSteps = maxSteps <= 0 ? 12 : maxSteps;
    this.maxProviderCalls = maxProviderCalls <= 0 ? 3 : maxProviderCalls;
    this.maxSourceProviderCalls = maxSourceProviderCalls <= 0 ? 4 : maxSourceProviderCalls;
    this.actions = new AgentActionCodec(new ObjectMapper());
    this.metrics = metrics == null ? new AssistantMetrics() {} : metrics;
  }

  public TurnResult run(
      String sessionId,
      ModelLevel level,
      String userMessage,
      String sourceDocument,
      ModelWorkspace workspace) {
    return run(sessionId, level, userMessage, sourceDocument, workspace, false);
  }

  /** Runs one turn. Destructive commands are only enabled by the durable confirmation endpoint. */
  public TurnResult run(
      String sessionId,
      ModelLevel level,
      String userMessage,
      String sourceDocument,
      ModelWorkspace workspace,
      boolean destructiveConfirmed) {
    return run(
        sessionId,
        level,
        userMessage,
        sourceDocument,
        workspace,
        destructiveConfirmed,
        WorkflowMode.AUTO);
  }

  /** Runs one turn with an explicit workflow route chosen by the durable router. */
  public TurnResult run(
      String sessionId,
      ModelLevel level,
      String userMessage,
      String sourceDocument,
      ModelWorkspace workspace,
      boolean destructiveConfirmed,
      WorkflowMode mode) {
    return run(
        sessionId,
        level,
        userMessage,
        sourceDocument,
        workspace,
        destructiveConfirmed,
        mode,
        () -> false,
        () -> null);
  }

  /** Runs with durable cancellation/deadline guards around every provider and tool boundary. */
  public TurnResult run(
      String sessionId,
      ModelLevel level,
      String userMessage,
      String sourceDocument,
      ModelWorkspace workspace,
      boolean destructiveConfirmed,
      java.util.function.BooleanSupplier cancellationRequested,
      java.util.function.Supplier<PlatformException> stopReason) {
    return run(
        sessionId,
        level,
        userMessage,
        sourceDocument,
        workspace,
        destructiveConfirmed,
        WorkflowMode.AUTO,
        cancellationRequested,
        stopReason);
  }

  /** Runs with durable cancellation/deadline guards and an explicit workflow route. */
  public TurnResult run(
      String sessionId,
      ModelLevel level,
      String userMessage,
      String sourceDocument,
      ModelWorkspace workspace,
      boolean destructiveConfirmed,
      WorkflowMode mode,
      java.util.function.BooleanSupplier cancellationRequested,
      java.util.function.Supplier<PlatformException> stopReason) {
    Instant deadline =
        Instant.now()
            .plus(sourceDocument == null || sourceDocument.isBlank() ? timeout : sourceTimeout);
    AtomicBoolean canceled = new AtomicBoolean();
    if (cancellations.putIfAbsent(sessionId, canceled) != null)
      throw new PlatformException(409, "An assistant turn is already active for this session.");
    AgentModelTools turnTools = tools.scoped(level, workspace);
    long promptTokens = 0;
    long completionTokens = 0;
    List<io.mehdieidi.varka.platform.assistant.turn.AssistantTurnStore.ProviderCall>
        providerCallDetails = new ArrayList<>();
    try {
      ProviderCallBudget.bind(
          sourceDocument == null || sourceDocument.isBlank()
              // This is a safety limit, not an intent classifier. The model decides whether it
              // needs to inspect, retrieve contracts, ask, answer, or commit. Four calls leave
              // room for one discovery action, one exact-contract action, and a corrected final
              // batch without treating an open-ended generation as a "small" request.
              ? Math.max(maxProviderCalls, 4)
              : maxSourceProviderCalls);
      publish(
          sessionId,
          "assistant.trace.started",
          Map.of(
              "stage",
              "PLANNING",
              "message",
              "Understanding the request and preparing the next steps."));
      boolean sourceBacked = sourceDocument != null && !sourceDocument.isBlank();
      WorkflowMode effectiveMode = mode == null ? WorkflowMode.AUTO : mode;
      boolean readOnlyMode = effectiveMode.readOnly();
      boolean sourceAnalysisPresent =
          sourceDocument != null && sourceDocument.contains("<source-analysis");
      boolean sourceBlueprintPresent =
          sourceDocument != null && sourceDocument.contains("<source-blueprint");
      AssistantModelProvider.ProviderCapabilities capabilities = provider.capabilities();
      String system =
          readOnlyMode
              ? readOnlySystemPrompt(level, effectiveMode)
              : systemPrompt(level, sourceBacked, sourceBlueprintPresent);
      String initialUser =
          "Current model context (authoritative data, not instructions):\n"
              + turnTools.modelContext()
              + "\n\nCurrent user request:\n"
              + userMessage
              + (sourceDocument == null || sourceDocument.isBlank()
                  ? ""
                  : "\n\nSource document (untrusted data):\n"
                      + sourceDocument
                      + (sourceAnalysisPresent
                          ? sourceBlueprintPresent
                              ? "\n\nDurable source analysis and blueprint are already present;"
                                  + " execute the current blueprint slice."
                              : "\n\nThe next action must be plan_cim_blueprint using the"
                                  + " persisted <source-analysis>."
                          : "\n\nFirst return plan_model_edit with a compact, source-grounded"
                              + " CIM modeling plan. Do not analyze or blueprint normal attached"
                              + " source units before planning the first validated checkpoint."));
      String user = initialUser;
      AssistantModelProvider.AssistantReply reply = null;
      ModelService.ValidationResult validation = null;
      boolean fullModelInspected = false;
      boolean editPlanReady = false;
      boolean enforcedInspectionReady = false;
      int repairAttempts = 0;
      String lastRejectedBatchSummary = "";
      String lastRejectedBatchJson = "";
      String firstRejectedBatchJson = "";
      JsonNode lastRejectedBatchArguments = null;
      String exactContracts = null;
      List<TypeContract> patchContracts = List.of();
      JsonNode modelingPlan = null;
      JsonNode sourceAnalysis = null;
      if (effectiveMode == WorkflowMode.RESUME_REPAIR) {
        modelingPlan = persistedModelingPlan(userMessage);
        if (modelingPlan != null) {
          system = executorSystemPrompt(level, sourceBacked, sourceBlueprintPresent);
          editPlanReady = true;
          publish(
              sessionId,
              "assistant.modeling_plan.reused",
              Map.of(
                  "slices",
                  modelingPlan.path("slices").size(),
                  "intent",
                  modelingPlan.path("intent").asText("")));
          patchContracts = resumeContracts(level, workspace, turnTools, modelingPlan, capabilities);
          exactContracts = patchContracts.isEmpty() ? null : compactContracts(patchContracts);
          user =
              initialUser
                  + "\n\nCurrent durable modeling checkpoint:\n"
                  + currentSlicePlan(modelingPlan)
                  + (exactContracts == null
                      ? ""
                      : "\n\nExact type contracts already retrieved by the backend:\n"
                          + exactContracts)
                  + "\n\nA persisted ModelingPlan is already available. Do not call"
                  + " plan_model_edit or regenerate the plan. Exact metamodel contracts for the"
                  + " current slice are already supplied. Inspect only if current model ids are"
                  + " needed; otherwise commit one corrected checkpoint now.";
        } else if (sourceBacked && sourceAnalysisPresent && !sourceBlueprintPresent) {
          user =
              initialUser
                  + "\n\nPersisted source analysis is already available. Do not regenerate it."
                  + " The next action must be plan_cim_blueprint. Convert that analysis into"
                  + " LLM-authored CIM element candidates, stable logical keys, exact intended"
                  + " eClasses, sourceUnitIds, required contract names, relationships, and"
                  + " coherent slices.";
        }
      }
      Map<String, List<TypeContract>> contractCache = new ConcurrentHashMap<>();
      for (int step = 1; step <= maxSteps; step++) {
        check(canceled, deadline, cancellationRequested, stopReason);
        if (!ProviderCallBudget.hasRemaining()) {
          throw new PlatformException(
              502,
              "The model provider did not produce a valid terminal action within the turn budget.");
        }
        publish(
            sessionId,
            "assistant.trace.step",
            Map.of(
                "stage",
                "PLANNING",
                "step",
                step,
                "message",
                step == 1
                    ? "Reviewing the request and available model context."
                    : "Refining the approach using the information gathered so far."));
        List<AssistantModelProvider.ContextSnippet> snippets =
            // Planning and patch execution are stateful now. Exact contracts are retrieved through
            // describe_types after a durable plan exists, so preloading retrieved contracts makes
            // compatible providers slower without adding authority.
            // Discovery is useful before the plan exists and for source-backed turns.  Exact
            // contracts remain authoritative for mutation; retrieved snippets only help the
            // planner select which concepts to describe.
            retrieval == null || step > 1
                ? List.of()
                : retrieval.search(
                    level,
                    sourceDocument == null || sourceDocument.isBlank()
                        ? userMessage
                        : userMessage + "\n" + sourceDocument,
                    2);
        int retrievalChars = snippets.stream().mapToInt(item -> item.content().length()).sum();
        metrics.recordAssistantRetrievalChars(retrievalChars);
        long estimatedInputTokens =
            estimateTokens(system.length() + user.length() + retrievalChars);
        long providerStarted = System.nanoTime();
        AssistantPrompt providerPrompt =
            new AssistantPrompt(
                system,
                user,
                snippets,
                patchContracts,
                requiredTool(
                    readOnlyMode,
                    sourceBacked,
                    sourceAnalysisPresent,
                    sourceBlueprintPresent,
                    editPlanReady,
                    exactContracts));
        reply =
            ProviderRequestContext.with(
                () -> canceled.get() || cancellationRequested.getAsBoolean(),
                stopReason,
                () -> provider.completeStructured(providerPrompt));
        long providerLatency =
            java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - providerStarted);
        metrics.recordAssistantPhaseDuration("provider", providerLatency);
        if (reply.usage().reported()) {
          promptTokens += Math.max(0L, reply.usage().promptTokens());
          completionTokens += Math.max(0L, reply.usage().completionTokens());
          metrics.recordAssistantTokenUsage(
              "input", reply.provider(), Math.max(0L, reply.usage().promptTokens()));
          metrics.recordAssistantTokenUsage(
              "output", reply.provider(), Math.max(0L, reply.usage().completionTokens()));
        } else {
          metrics.recordAssistantTokenEstimate("input", reply.provider(), estimatedInputTokens);
          metrics.recordAssistantTokenEstimate(
              "output", reply.provider(), estimateTokens(reply.content().length()));
        }
        providerCallDetails.add(
            new io.mehdieidi.varka.platform.assistant.turn.AssistantTurnStore.ProviderCall(
                reply.provider(),
                reply.model(),
                providerLatency,
                reply.usage().promptTokens(),
                reply.usage().completionTokens(),
                reply.usage().reported(),
                reply.systemPrompt(),
                reply.userPrompt()));
        check(canceled, deadline, cancellationRequested, stopReason);
        AgentAction action;
        try {
          action = actions.parse(reply.content());
          metrics.recordAssistantAction(action.tool().wireName(), step);
          if (sourceBacked
              && !readOnlyMode
              && !sourceAnalysisPresent
              && action.tool() == AgentAction.Kind.ANALYZE_SOURCE_UNITS) {
            user =
                followUpContext(userMessage, sourceDocument)
                    + "\n\nanalyze_source_units was rejected by backend workflow state: normal"
                    + " attached source units must go directly to a durable CIM modeling plan."
                    + " Return plan_model_edit with sourceUnitIds copied from the supplied"
                    + " markers. Do not analyze, blueprint, answer, or ask unless the source"
                    + " units are unreadable.";
            continue;
          }
          if (sourceBacked
              && !readOnlyMode
              && sourceAnalysisPresent
              && !sourceBlueprintPresent
              && modelingPlan == null
              && action.tool() != AgentAction.Kind.PLAN_CIM_BLUEPRINT) {
            user =
                followUpContext(userMessage, sourceDocument)
                    + "\n\nPersisted source analysis exists but no durable CIM blueprint exists."
                    + " The next action must be plan_cim_blueprint. Do not patch or call"
                    + " describe_types before the blueprint is accepted.";
            continue;
          }
          if (!readOnlyMode
              && !editPlanReady
              && action.tool() != AgentAction.Kind.PLAN_MODEL_EDIT
              && (!sourceBacked
                  || sourceAnalysisPresent
                  || action.tool() != AgentAction.Kind.ANALYZE_SOURCE_UNITS)
              && (!sourceBacked || action.tool() != AgentAction.Kind.PLAN_CIM_BLUEPRINT)
              && action.tool() != AgentAction.Kind.INSPECT_MODEL
              && action.tool() != AgentAction.Kind.ANSWER_USER
              && action.tool() != AgentAction.Kind.ASK_USER) {
            user =
                followUpContext(userMessage, sourceDocument)
                    + "\n\nBefore inspection, contract retrieval, or patch generation, return"
                    + " plan_model_edit with a compact structured plan for this request. If the"
                    + " request is only a question or explanation, answer_user is allowed.";
            continue;
          }
          publish(sessionId, "tool.started", toolProgress(action.tool(), false, null));
          if (action.tool() == AgentAction.Kind.ANSWER_USER
              || action.tool() == AgentAction.Kind.ASK_USER) {
            String message = action.arguments().path("message").asText();
            if (message == null || message.isBlank()) {
              throw new PlatformException(
                  422,
                  action.tool().wireName()
                      + " must include a non-empty user-facing message in arguments.message.");
            }
            if (sourceBacked && !editPlanReady && !sourceUnitIds(sourceDocument).isEmpty()) {
              if (ProviderCallBudget.hasRemaining()) {
                user =
                    followUpContext(userMessage, sourceDocument)
                        + "\n\nYour previous "
                        + action.tool().wireName()
                        + " action was rejected: this is a source-to-CIM modeling request and no"
                        + " durable ModelingPlan exists yet. Return plan_model_edit with a"
                        + " progressive CIM plan grounded in the supplied source units. Do not"
                        + " answer or ask the user unless the source itself is unreadable.";
                continue;
              }
              throw new PlatformException(
                  422, "Source-backed modeling requires a durable plan before completion.");
            }
            if (mutatingPlanWithoutCheckpoint(modelingPlan, turnTools)) {
              if (ProviderCallBudget.hasRemaining()) {
                user =
                    followUpContext(userMessage, sourceDocument)
                        + "\n\nYour previous "
                        + action.tool().wireName()
                        + " action was rejected by backend workflow state: this turn has a"
                        + " durable mutating ModelingPlan and no checkpoint has been committed."
                        + " The exact contracts and current slice have already been supplied."
                        + " The next action must be commit_model_batch. Create one compact,"
                        + " structurally valid checkpoint slice using only those contracts."
                        + " Do not inspect, describe types, answer, or ask the user.";
                continue;
              }
              throw new PlatformException(
                  422, "Mutating modeling plans must commit a checkpoint before answering.");
            }
            if (sourceBacked && !sourceUnitIds(sourceDocument).isEmpty()) {
              if (ProviderCallBudget.hasRemaining()) {
                user =
                    followUpContext(userMessage, sourceDocument)
                        + "\n\nYour previous "
                        + action.tool().wireName()
                        + " action was rejected: this is a source-to-model request and source"
                        + " evidence is still in scope. Do not ask the user whether to model"
                        + " source facts that are already present. Return commit_model_batch with"
                        + " a structurally valid CIM slice grounded in the supplied source ids."
                        + " Every SOURCE_GROUNDED evidence item must include a non-empty"
                        + " sourceUnitId copied from a <source-unit id=\"...\"> marker."
                        + " Use INFERRED evidence only for concise assumptions not directly stated"
                        + " in the source.";
                continue;
              }
              throw new PlatformException(
                  422, "Source-backed modeling must end with a model checkpoint, not an answer.");
            }
            if (action.tool() == AgentAction.Kind.ASK_USER && hasNoModelElements(workspace)) {
              // An empty canvas is not missing user input. The agent has the root and the exact
              // metamodel contracts, so it must either create the required aggregate itself or
              // return an answer. This is a context rule, not a keyword/intent classifier.
              if (ProviderCallBudget.hasRemaining()) {
                user =
                    followUpContext(userMessage, sourceDocument)
                        + "\n\nYour previous ask_user action was rejected: this model has no"
                        + " existing elements, so asking the user to choose an existing owner"
                        + " is invalid. Create the needed root-contained aggregate/container"
                        + " yourself in commit_model_batch, then create its dependent elements"
                        + " and connections using clientRefs. Do not ask for an existing element"
                        + " on an empty model.";
                continue;
              }
              throw new PlatformException(
                  422,
                  "The assistant asked for an existing element even though the model is empty.");
            }
            if (action.tool() == AgentAction.Kind.ASK_USER && !fullModelInspected) {
              // The compact inventory is intentionally small. Before asking the user for any
              // identifier or owner, make the agent inspect the authoritative persisted model.
              // A subsequent ask remains available for a genuine business ambiguity.
              user =
                  followUpContext(userMessage, sourceDocument)
                      + "\n\nBefore asking the user for input, return inspect_model with an"
                      + " empty id and inspect the complete persisted model. Existing ids and"
                      + " ownership are backend facts, not user input. After inspection, extend"
                      + " an existing compatible aggregate when one exists; ask only if a real"
                      + " business decision is still unspecified.";
              continue;
            }
            return new TurnResult(
                message,
                workspace.patch(),
                workspace.inversePatch(),
                readOnlyMode ? null : turnTools.validateModel(),
                reply.provider(),
                reply.model(),
                null,
                ProviderCallBudget.count(),
                promptTokens,
                completionTokens,
                providerCallDetails,
                modelingPlan,
                null,
                null);
          }
          switch (action.tool()) {
            case ANALYZE_SOURCE_UNITS -> {
              if (readOnlyMode) {
                throw new PlatformException(
                    422, "Read-only explanation workflows must not call analyze_source_units.");
              }
              if (!sourceBacked || sourceAnalysisPresent) {
                throw new PlatformException(
                    422, "analyze_source_units is only valid before persisted analysis exists.");
              }
              sourceAnalysis = validatedSourceAnalysis(action.arguments(), sourceDocument);
              publish(
                  sessionId,
                  "assistant.source.analysis",
                  Map.of(
                      "sourceUnits",
                      sourceAnalysis.path("sourceUnits").size(),
                      "concepts",
                      sourceAnalysis.path("concepts").size()));
              return new TurnResult(
                  "Source analysis prepared; planning the CIM blueprint next.",
                  workspace.patch(),
                  workspace.inversePatch(),
                  null,
                  reply.provider(),
                  reply.model(),
                  null,
                  ProviderCallBudget.count(),
                  promptTokens,
                  completionTokens,
                  providerCallDetails,
                  null,
                  null,
                  sourceAnalysis);
            }
            case PLAN_CIM_BLUEPRINT -> {
              if (readOnlyMode) {
                throw new PlatformException(
                    422, "Read-only explanation workflows must not call plan_cim_blueprint.");
              }
              if (!sourceBacked || !sourceAnalysisPresent || sourceBlueprintPresent) {
                throw new PlatformException(
                    422,
                    "plan_cim_blueprint is only valid after source analysis and before a"
                        + " blueprint exists.");
              }
              JsonNode blueprint = validatedCimBlueprint(action.arguments(), sourceDocument, level);
              publish(
                  sessionId,
                  "assistant.source.blueprint",
                  Map.of("slices", blueprint.path("slices").size()));
              return new TurnResult(
                  "CIM blueprint prepared; applying its first model slice next.",
                  workspace.patch(),
                  workspace.inversePatch(),
                  null,
                  reply.provider(),
                  reply.model(),
                  null,
                  ProviderCallBudget.count(),
                  promptTokens,
                  completionTokens,
                  providerCallDetails,
                  null,
                  blueprint,
                  null);
            }
            case PLAN_MODEL_EDIT -> {
              if (readOnlyMode) {
                user =
                    followUpContext(userMessage, sourceDocument)
                        + "\n\nThis route is read-only. Do not plan or commit model changes."
                        + " Answer the user's explanation question, or call inspect_model/"
                        + " describe_types once if exact facts are still needed.";
                continue;
              }
              modelingPlan = normalizeModelingPlan(action.arguments(), capabilities);
              system = executorSystemPrompt(level, sourceBacked, sourceBlueprintPresent);
              editPlanReady = true;
              enforcedInspectionReady = hasNoModelElements(workspace);
              publish(
                  sessionId,
                  "assistant.modeling_plan.ready",
                  Map.of(
                      "slices",
                      modelingPlan.path("slices").size(),
                      "intent",
                      modelingPlan.path("intent").asText("")));
              if (!enforcedInspectionReady) {
                JsonNode summary = turnTools.inspectSummary();
                List<String> relevantIds =
                    relevantElementIds(
                        turnTools, modelingPlan, Math.max(1, capabilities.maxPatchCreates()));
                JsonNode selected =
                    relevantIds.isEmpty()
                        ? mapper.createObjectNode().put("total", 0)
                        : turnTools.inspectModel(
                            new AgentModelTools.InspectionSelector(
                                relevantIds, List.of(), List.of(), null, 0, 50));
                JsonNode neighborhoods = turnTools.inspectNeighborhoods(relevantIds);
                fullModelInspected = true;
                enforcedInspectionReady = true;
                patchContracts =
                    resumeContracts(level, workspace, turnTools, modelingPlan, capabilities);
                exactContracts = patchContracts.isEmpty() ? null : compactContracts(patchContracts);
                user =
                    followUpContext(userMessage, sourceDocument)
                        + "\n\nCurrent durable modeling checkpoint:\n"
                        + currentSlicePlan(modelingPlan)
                        + "\n\nEnforced model inspection sequence completed by backend."
                        + "\n1. Summary:\n"
                        + summary
                        + "\n2. Relevant existing elements:\n"
                        + selected
                        + "\n3. Neighborhoods:\n"
                        + neighborhoods
                        + executionContractGuidance(
                            exactContracts,
                            capabilities,
                            sourceBacked,
                            "\n\n"
                                + "Now retrieve exact metamodel contracts for only the current"
                                + " planned slice using describe_types. Prefer the"
                                + " requiredContracts listed in the plan. Do not patch until"
                                + " contracts are returned.");
              } else {
                patchContracts =
                    resumeContracts(level, workspace, turnTools, modelingPlan, capabilities);
                exactContracts = patchContracts.isEmpty() ? null : compactContracts(patchContracts);
                user =
                    followUpContext(userMessage, sourceDocument)
                        + "\n\nCurrent durable modeling checkpoint:\n"
                        + currentSlicePlan(modelingPlan)
                        + "\n\nThe current model has no user-created elements."
                        + executionContractGuidance(
                            exactContracts,
                            capabilities,
                            sourceBacked,
                            " Retrieve exact metamodel contracts for the first planned slice"
                                + " using describe_types, then create root-contained elements"
                                + " against rootId.");
              }
              continue;
            }
            case COMMIT_MODEL_BATCH -> {
              if (readOnlyMode) {
                throw new PlatformException(
                    422, "Read-only explanation workflows must not call commit_model_batch.");
              }
              if (!sourceBacked && !enforcedInspectionReady && !hasNoModelElements(workspace)) {
                user =
                    followUpContext(userMessage, sourceDocument)
                        + "\n\nPatch rejected by workflow state: non-empty edit requests must use"
                        + " the enforced inspection sequence after plan_model_edit and before"
                        + " commit_model_batch.";
                continue;
              }
              check(canceled, deadline, cancellationRequested, stopReason);
              ModelCommandBatch batch = normalizeSourceEvidence(command(action), sourceDocument);
              validateSourceEvidence(batch, sourceDocument);
              validateSourceEvidenceIds(batch, sourceDocument);
              validatePlannedSourceSliceCoverage(batch, sourceDocument, modelingPlan);
              validateSourceCheckpointUsefulness(
                  batch, sourceDocument, modelingPlan, patchContracts);
              lastRejectedBatchSummary = batchSummary(batch);
              lastRejectedBatchJson = boundedRejectedBatch(action.arguments());
              lastRejectedBatchArguments = action.arguments().deepCopy();
              if (firstRejectedBatchJson.isBlank()) {
                firstRejectedBatchJson = lastRejectedBatchJson;
              }
              if (sourceBacked && !batch.deletions().isEmpty()) {
                throw new PlatformException(
                    422,
                    "Source-backed document-to-CIM generation must be additive. Do not delete"
                        + " starter scaffold or existing model content during source import;"
                        + " create and connect the source-grounded CIM elements instead. Deletions"
                        + " are available only as explicit user-requested edit actions.");
              }
              turnTools.commitModelBatch(batch, destructiveConfirmed);
              check(canceled, deadline, cancellationRequested, stopReason);
              ModelService.ValidationResult checkpointValidation = turnTools.validateModel();
              metrics.recordAssistantStructuralValidation(checkpointValidation.valid());
              validation = checkpointValidation;
              check(canceled, deadline, cancellationRequested, stopReason);
              publish(
                  sessionId,
                  "tool.completed",
                  toolProgress(action.tool(), true, checkpointValidation.valid()));
              if (checkpointValidation.valid()) {
                return new TurnResult(
                    checkpointMessage(turnTools.committedBatch()),
                    workspace.patch(),
                    workspace.inversePatch(),
                    checkpointValidation,
                    reply.provider(),
                    reply.model(),
                    turnTools.committedBatch(),
                    ProviderCallBudget.count(),
                    promptTokens,
                    completionTokens,
                    providerCallDetails,
                    modelingPlan,
                    null,
                    null);
              }
            }
            case PLAN_SOURCE_MODEL -> {
              if (readOnlyMode) {
                throw new PlatformException(
                    422, "Read-only explanation workflows must not call plan_source_model.");
              }
              if (!sourceBacked
                  || (sourceDocument != null && sourceDocument.contains("<source-blueprint")))
                throw new PlatformException(
                    422, "plan_source_model is only valid before a source blueprint exists.");
              if (sourceDocument == null || !sourceDocument.contains("<source-document-map")) {
                user =
                    followUpContext(userMessage, sourceDocument)
                        + "\n\nplan_source_model was rejected by backend workflow state: normal"
                        + " source attachments are already supplied as complete source units."
                        + " Return plan_model_edit with a durable progressive CIM modeling plan"
                        + " for these units. Do not answer or ask the user.";
                continue;
              }
              JsonNode blueprint = validatedSourceBlueprint(action.arguments(), sourceDocument);
              publish(
                  sessionId,
                  "assistant.source.blueprint",
                  Map.of("slices", blueprint.path("slices").size()));
              return new TurnResult(
                  "Source blueprint prepared; applying its first model slice.",
                  workspace.patch(),
                  workspace.inversePatch(),
                  turnTools.validateModel(),
                  reply.provider(),
                  reply.model(),
                  null,
                  ProviderCallBudget.count(),
                  promptTokens,
                  completionTokens,
                  providerCallDetails,
                  null,
                  blueprint,
                  null);
            }
            case INSPECT_MODEL -> {
              check(canceled, deadline, cancellationRequested, stopReason);
              String id = action.arguments().path("id").asText("");
              AgentModelTools.InspectionSelector selector = inspectionSelector(action.arguments());
              if (id.isBlank()
                  && selector.ids().isEmpty()
                  && selector.eClasses().isEmpty()
                  && selector.ownerIds().isEmpty()
                  && (selector.query() == null || selector.query().isBlank()))
                fullModelInspected = true;
              JsonNode inspection =
                  id.isBlank() ? turnTools.inspectModel(selector) : turnTools.readModel(id);
              String inspectionGuidance =
                  readOnlyMode
                      ? "You now have the required model facts. Return answer_user. Do not mutate,"
                          + " validate, repair, or create a checkpoint."
                      : "You now have the required model facts. Do not inspect or describe types"
                          + " again; return one terminal action (commit_model_batch, answer_user,"
                          + " or ask_user).";
              if (id.isBlank()
                  && inspection.path("total").asInt(-1) == 0
                  && !hasNoModelElements(workspace)) {
                fullModelInspected = true;
                inspection = turnTools.inspectModel(AgentModelTools.InspectionSelector.all());
                inspectionGuidance =
                    "Your selected inspection matched zero elements, but the model is not empty."
                        + " The fallback inspection above is authoritative current-model"
                        + " context. Do not conclude that the PIM/CIM has no elements. Extend the"
                        + " compatible existing elements when possible; otherwise create the"
                        + " missing feature slice with structurally valid nodes and edges.";
              }
              user =
                  followUpContext(userMessage, sourceDocument)
                      + "\n\nInspection result:\n"
                      + inspection
                      + "\n\n"
                      + inspectionGuidance;
              continue;
            }
            case DESCRIBE_TYPES -> {
              check(canceled, deadline, cancellationRequested, stopReason);
              if (!readOnlyMode && exactContracts != null && !patchContracts.isEmpty()) {
                user =
                    followUpContext(userMessage, sourceDocument)
                        + "\n\nCurrent durable modeling checkpoint:\n"
                        + currentSlicePlan(modelingPlan)
                        + "\n\nExact type contracts already retrieved by the backend:\n"
                        + exactContracts
                        + "\n\nDo not call describe_types again. The next action must be"
                        + " commit_model_batch using these exact contracts and exact source-unit"
                        + " ids.";
                continue;
              }
              List<String> names = new ArrayList<>();
              action.arguments().path("names").forEach(value -> names.add(value.asText()));
              if (names.isEmpty()) {
                user =
                    followUpContext(userMessage, sourceDocument)
                        + "\n\nThe complete exact Ecore type index is:\n"
                        + guides.index(level)
                        + "\n\n"
                        + (readOnlyMode
                            ? "Choose the exact types needed for the question and call"
                                + " describe_types with a non-empty names array, or answer if the"
                                + " index is sufficient."
                            : "Choose the exact types needed for the request and call"
                                + " describe_types with a non-empty names array. Do not answer the"
                                + " user yet.");
                continue;
              }
              int selectedContractLimit = Math.max(2, capabilities.maxContractCount());
              List<String> selectedNames =
                  selectedContractNames(
                      level, workspace, modelingPlan, names, selectedContractLimit);
              String cacheKey = level.name() + ":" + String.join(",", selectedNames);
              patchContracts =
                  contractCache.computeIfAbsent(
                      cacheKey,
                      ignored ->
                          capabilities.maxContractCount() <= 2
                              ? turnTools.describeExactTypes(selectedNames)
                              : turnTools.describeTypes(selectedNames));
              exactContracts = compactContracts(patchContracts);
              if (readOnlyMode) {
                user =
                    followUpContext(userMessage, sourceDocument)
                        + "\n\nExact type contracts:\n"
                        + exactContracts
                        + "\n\nYou now have the exact contracts. Answer the user using only these"
                        + " metamodel facts. Do not mutate, validate, repair, or create a"
                        + " checkpoint.";
              } else {
                user =
                    followUpContext(userMessage, sourceDocument)
                        + (modelingPlan == null
                            ? ""
                            : "\n\nCurrent durable modeling checkpoint:\n"
                                + currentSlicePlan(modelingPlan))
                        + "\n\nExact type contracts:\n"
                        + exactContracts
                        + "\n\n"
                        + "You now have the exact contracts. Do not inspect or describe types"
                        + " again. The next action must be commit_model_batch; do not answer or ask"
                        + " the user. Submit one structurally complete checkpoint slice using only"
                        + " these contracts. For a complex create or feature-add request, keep the"
                        + " slice compact enough to validate quickly: at most "
                        + capabilities.maxPatchCreates()
                        + " creates, "
                        + capabilities.maxPatchConnections()
                        + " connections, and "
                        + capabilities.maxPatchEvidence()
                        + " evidence items. Set turnComplete:false and put the next concrete slice"
                        + " in planSummary when requested work remains. If this is source-backed,"
                        + " every SOURCE_GROUNDED evidence item must include a non-empty"
                        + " sourceUnitId copied from a supplied <source-unit id=\"...\"> marker."
                        + " Use INFERRED only for assumptions that are not directly stated in the"
                        + " source. Before submitting, audit every create against its contract:"
                        + " every attribute or reference marked with ! is mandatory. Required"
                        + " attributes must appear in attributes with a valid JSON value; enum"
                        + " attributes must use exactly one listed enum literal. Required"
                        + " non-containment references must be satisfied by connections in the same"
                        + " batch, using compatible created or existing targets. Do not create an"
                        + " element when you cannot provide its required attributes and links from"
                        + " the source or a concise stated assumption.";
              }
              continue;
            }
            case ANSWER_USER, ASK_USER ->
                throw new IllegalStateException("Terminal action was not returned.");
          }
        } catch (PlatformException toolFailure) {
          metrics.recordAssistantMalformedAction(actionFailureReason(toolFailure));
          if (!readOnlyMode
              && repairableToolFailure(toolFailure)
              && repairAttempts < Math.max(4, maxProviderCalls - 2)
              && ProviderCallBudget.hasRemaining()) {
            repairAttempts++;
            metrics.recordAssistantRepairReason(actionFailureReason(toolFailure));
            // Rebuild a bounded repair request instead of recursively appending failed prompts.
            // It carries the original work item, exact contracts, rejected draft, and diagnostics
            // without recursively appending prior repair prompts.
            user =
                followUpContext(userMessage, sourceDocument)
                    + (exactContracts == null
                        ? ""
                        : "\n\nExact type contracts already retrieved:\n" + exactContracts)
                    + (lastRejectedBatchSummary.isBlank()
                        ? ""
                        : "\n\nRejected draft summary to preserve additively when valid:\n"
                            + lastRejectedBatchSummary)
                    + (lastRejectedBatchJson.isBlank()
                        ? ""
                        : "\n\nRejected commit_model_batch arguments (edit this draft in place):\n"
                            + lastRejectedBatchJson)
                    + (firstRejectedBatchJson.isBlank()
                            || firstRejectedBatchJson.equals(lastRejectedBatchJson)
                        ? ""
                        : "\n\nOriginal atomic batch (restore any valid creates/connections that"
                            + " a later repair accidentally dropped):\n"
                            + firstRejectedBatchJson)
                    + "\n\nRepair diagnostic JSON:\n"
                    + repairDiagnostic(toolFailure, turnTools, lastRejectedBatchArguments)
                    + "\nThe next action must be commit_model_batch. Return only one corrected"
                    + " tool call. Keep already-valid planned elements and evidence from the"
                    + " rejected draft. No element from a rejected draft has been saved: never"
                    + " submit only the newly corrected element or refer to a dropped clientRef."
                    + " Return the complete corrected atomic batch. Add or correct only missing"
                    + " planned source units,"
                    + " concepts, required attributes, required references, and invalid"
                    + " relationships. Do not replan, do not replace previously covered"
                    + " sourceUnitIds unless the diagnostic says they are invalid, and preserve"
                    + " exact clientRefs when possible. Do not describe types, inspect the"
                    + " model, repeat the rejected patch, or add unrelated elements. For"
                    + " apply_draft_patch, creates must be an array of create objects, never an"
                    + " array of clientRef strings. Every create object needs a unique non-empty"
                    + " clientRef, eClass, attributes, owner, and containment reference."
                    + " connections and evidence must also be arrays of objects, not strings."
                    + " Evidence cannot create a checkpoint by itself. If the diagnostic says"
                    + " the batch needs at least one create, update, connection, or deletion,"
                    + " keep the same intended modeling slice and add concrete mutations using"
                    + " the exact contracts already supplied."
                    + " If backend validation reports RequiredAttribute, keep the intended"
                    + " source-grounded element and add the missing required attribute using the"
                    + " exact returned contract; for enum attributes choose exactly one allowed"
                    + " literal and state any assumption in evidence. If the diagnostic says an"
                    + " attribute is not writable on an EClass, remove that exact attribute from"
                    + " the affected create/update; never resubmit the unchanged invalid key or"
                    + " move it to another element unless that element's exact contract lists it."
                    + " If backend validation"
                    + " reports RequiredReference, add or create a compatible target and connect"
                    + " it through the exact required non-containment reference, or remove the"
                    + " element whose required link cannot be satisfied from the source. If"
                    + " backend validation"
                    + " reports that a relationship reference is not writable or has an invalid"
                    + " target, remove that connection unless the exact source eClass contract"
                    + " contains a valid non-containment, non-readonly replacement reference."
                    + " In CIM, Requirement.dependsOn connects a Requirement only to another"
                    + " Requirement; never use it to connect a requirement to a Command,"
                    + " Query, Actor, Capability, Goal, Event, or domain element.";
            continue;
          }
          throw toolFailure;
        }
        ModelService.ValidationResult currentValidation = validation;
        if (currentValidation == null) {
          throw new IllegalStateException(
              "A non-terminal agent action did not validate the model.");
        }
        publish(
            sessionId,
            "assistant.delta.validated",
            Map.of("valid", currentValidation.valid(), "issues", currentValidation.issues()));
        if (currentValidation.valid()) break;
        if (workspace.patch().isEmpty()) break;
        user =
            followUpContext(userMessage, sourceDocument)
                + "\n\nThe working model failed structural validation after your previous tool "
                + "calls. Continue from the current working copy and use tools to repair every "
                + "issue before replying. Diagnostics:\n"
                + currentValidation.issues();
      }
      if (reply == null)
        throw new PlatformException(502, "The model provider returned no response.");
      check(canceled, deadline, cancellationRequested, stopReason);
      if (!readOnlyMode && validation == null) validation = turnTools.validateModel();
      if (validation != null) metrics.recordAssistantStructuralValidation(validation.valid());
      if (validation != null && !validation.valid() && !workspace.patch().isEmpty())
        throw new PlatformException(
            422, "The working model failed structural validation: " + validation.issues());
      publish(sessionId, "assistant.plan", Map.of("items", turnTools.plan()));
      return new TurnResult(
          reply.content(),
          workspace.patch(),
          workspace.inversePatch(),
          validation,
          reply.provider(),
          reply.model(),
          turnTools.committedBatch(),
          ProviderCallBudget.count(),
          promptTokens,
          completionTokens,
          providerCallDetails,
          modelingPlan,
          null,
          null);
    } catch (PlatformException ex) {
      throw new TurnExecutionException(
          ex, ProviderCallBudget.count(), promptTokens, completionTokens, providerCallDetails);
    } finally {
      ProviderCallBudget.clear();
      cancellations.remove(sessionId, canceled);
    }
  }

  private boolean mutatingPlanWithoutCheckpoint(JsonNode modelingPlan, AgentModelTools turnTools) {
    if (modelingPlan == null || modelingPlan.isMissingNode() || modelingPlan.isNull()) return false;
    String intent = modelingPlan.path("intent").asText("").trim();
    boolean mutating =
        intent.equalsIgnoreCase("CREATE_MODEL")
            || intent.equalsIgnoreCase("ADD_FEATURES")
            || intent.equalsIgnoreCase("EDIT_MODEL");
    return mutating && turnTools.committedBatch() == null;
  }

  public boolean cancel(String sessionId) {
    AtomicBoolean active = cancellations.get(sessionId);
    return active != null && !active.getAndSet(true);
  }

  private JsonNode normalizeModelingPlan(
      JsonNode raw, AssistantModelProvider.ProviderCapabilities capabilities) {
    var plan = mapper.createObjectNode();
    String intent = raw.path("intent").asText("ADD_FEATURES").trim();
    if (intent.isBlank()) intent = "ADD_FEATURES";
    plan.put("intent", intent);
    copyStringArray(raw.path("features"), plan.putArray("features"), 40);
    copyStringArray(raw.path("reuseTargets"), plan.putArray("reuseTargets"), 32);
    copyStringArray(raw.path("newElements"), plan.putArray("newElements"), 80);
    copyStringArray(
        raw.path("requiredContracts"),
        plan.putArray("requiredContracts"),
        capabilities.maxContractCount());
    var slices = plan.putArray("slices");
    int maxSlices = Math.max(1, Math.min(12, raw.path("slices").size()));
    if (raw.path("slices").isArray()) {
      int ordinal = 1;
      for (JsonNode slice : raw.path("slices")) {
        if (ordinal > maxSlices) break;
        var item = slices.addObject();
        item.put("ordinal", ordinal++);
        item.put("label", nonBlank(slice.path("label").asText(""), "Checkpoint " + (ordinal - 1)));
        item.put(
            "purpose",
            nonBlank(slice.path("purpose").asText(""), "Model a coherent request slice."));
        copyStringArray(
            slice.path("requiredContracts"),
            item.putArray("requiredContracts"),
            capabilities.maxContractCount());
        copyStringArray(slice.path("sourceUnitIds"), item.putArray("sourceUnitIds"), 24);
        item.put("status", ordinal == 2 ? "running" : "pending");
      }
    }
    if (slices.isEmpty()) {
      var item = slices.addObject();
      item.put("ordinal", 1);
      item.put("label", "Core architecture");
      item.put("purpose", "Create or update the central modeling elements requested by the user.");
      copyStringArray(
          raw.path("requiredContracts"),
          item.putArray("requiredContracts"),
          capabilities.maxContractCount());
      copyStringArray(raw.path("sourceUnitIds"), item.putArray("sourceUnitIds"), 24);
      item.put("status", "running");
    }
    return plan;
  }

  private void copyStringArray(
      JsonNode source, tools.jackson.databind.node.ArrayNode target, int max) {
    if (source == null || !source.isArray()) return;
    LinkedHashSet<String> seen = new LinkedHashSet<>();
    for (JsonNode value : source) {
      String text = value.asText("").trim();
      if (!text.isBlank()) seen.add(text);
      if (seen.size() >= max) break;
    }
    seen.forEach(target::add);
  }

  private String nonBlank(String value, String fallback) {
    return value == null || value.isBlank() ? fallback : value.trim();
  }

  private String boundedRejectedBatch(JsonNode arguments) {
    if (arguments == null || !arguments.isObject()) return "";
    String value = arguments.toString();
    return value.length() <= 20000 ? value : value.substring(0, 20000);
  }

  private JsonNode persistedModelingPlan(String userMessage) {
    if (userMessage == null || userMessage.isBlank()) return null;
    String marker = "\nPlan:\n";
    int start = userMessage.indexOf(marker);
    if (start < 0) return null;
    start += marker.length();
    int end = userMessage.indexOf("\nWork items:", start);
    String json =
        (end >= 0 ? userMessage.substring(start, end) : userMessage.substring(start)).trim();
    if (json.isBlank() || !json.startsWith("{")) return null;
    try {
      JsonNode parsed = mapper.readTree(json);
      return parsed != null && parsed.isObject() && parsed.path("slices").isArray() ? parsed : null;
    } catch (tools.jackson.core.JacksonException ignored) {
      return null;
    }
  }

  private List<TypeContract> resumeContracts(
      ModelLevel level,
      ModelWorkspace workspace,
      AgentModelTools turnTools,
      JsonNode modelingPlan,
      AssistantModelProvider.ProviderCapabilities capabilities) {
    List<String> names =
        selectedContractNames(
            level,
            workspace,
            modelingPlan,
            List.of(),
            Math.max(2, capabilities.maxContractCount()));
    if (names.isEmpty()) return List.of();
    return capabilities.maxContractCount() <= 2
        ? turnTools.describeExactTypes(names)
        : turnTools.describeTypes(names);
  }

  private List<String> selectedContractNames(
      ModelLevel level,
      ModelWorkspace workspace,
      JsonNode modelingPlan,
      List<String> requestedNames,
      int selectedContractLimit) {
    LinkedHashSet<String> selectedNames = new LinkedHashSet<>();
    String rootType = workspace.snapshot().path("eClass").asText("").trim();
    if (!rootType.isBlank()) selectedNames.add(rootType);
    for (String preferred : emptyModelFirstSliceTypes(level, workspace)) {
      if (selectedNames.size() >= selectedContractLimit) break;
      if (!preferred.isBlank()) selectedNames.add(preferred);
    }
    for (String planned : currentSliceContractNames(modelingPlan)) {
      if (selectedNames.size() >= selectedContractLimit) break;
      if (guides.isKnownType(level, planned)) selectedNames.add(planned);
    }
    for (String name : requestedNames) {
      if (selectedNames.size() >= selectedContractLimit) break;
      if (guides.isKnownType(level, name)) selectedNames.add(name.trim());
    }
    return selectedNames.stream().limit(selectedContractLimit).toList();
  }

  private List<String> relevantElementIds(AgentModelTools turnTools, JsonNode plan, int limit) {
    LinkedHashSet<String> ids = new LinkedHashSet<>();
    List<String> queries = new ArrayList<>();
    plan.path("reuseTargets").forEach(value -> queries.add(value.asText("")));
    plan.path("features").forEach(value -> queries.add(value.asText("")));
    for (String query : queries) {
      if (query == null || query.isBlank()) continue;
      JsonNode result =
          turnTools.inspectModel(
              new AgentModelTools.InspectionSelector(
                  List.of(), List.of(), List.of(), query, 0, 10));
      result
          .path("elements")
          .forEach(
              item -> {
                String id = item.path("id").asText("");
                if (!id.isBlank()) ids.add(id);
              });
      if (ids.size() >= limit) break;
    }
    return ids.stream().limit(limit).toList();
  }

  private List<String> currentSliceContractNames(JsonNode modelingPlan) {
    LinkedHashSet<String> names = new LinkedHashSet<>();
    if (modelingPlan == null || modelingPlan.isMissingNode() || modelingPlan.isNull()) {
      return List.of();
    }
    JsonNode slices = modelingPlan.path("slices");
    int currentSliceIndex = modelingPlan.path("currentSliceIndex").asInt(0);
    JsonNode currentSlice =
        slices.isArray() && !slices.isEmpty()
            ? slices.path(Math.max(0, Math.min(currentSliceIndex, slices.size() - 1)))
            : mapper.createObjectNode();
    currentSlice
        .path("requiredContracts")
        .forEach(
            value -> {
              String name = value.asText("").trim();
              if (!name.isBlank()) names.add(name);
            });
    java.util.Set<Integer> currentOrdinals = new java.util.LinkedHashSet<>();
    currentOrdinals.add(currentSlice.path("ordinal").asInt(currentSlice.path("slice").asInt(1)));
    currentSlice
        .path("candidateKeys")
        .forEach(
            key -> {
              String logicalKey = key.asText("").trim();
              if (logicalKey.isBlank()) return;
              modelingPlan
                  .path("candidates")
                  .forEach(
                      candidate -> {
                        if (logicalKey.equals(candidate.path("logicalKey").asText(""))) {
                          String eClass = candidate.path("eClass").asText("").trim();
                          if (!eClass.isBlank()) names.add(eClass);
                          candidate
                              .path("requiredContracts")
                              .forEach(
                                  value -> {
                                    String name = value.asText("").trim();
                                    if (!name.isBlank()) names.add(name);
                                  });
                        }
                      });
            });
    modelingPlan
        .path("candidates")
        .forEach(
            candidate -> {
              if (!currentOrdinals.contains(candidate.path("slice").asInt(-1))) return;
              String eClass = candidate.path("eClass").asText("").trim();
              if (!eClass.isBlank()) names.add(eClass);
              candidate
                  .path("requiredContracts")
                  .forEach(
                      value -> {
                        String name = value.asText("").trim();
                        if (!name.isBlank()) names.add(name);
                      });
            });
    modelingPlan
        .path("requiredContracts")
        .forEach(
            value -> {
              String name = value.asText("").trim();
              if (!name.isBlank()) names.add(name);
            });
    return List.copyOf(names);
  }

  private String executionContractGuidance(
      String exactContracts,
      AssistantModelProvider.ProviderCapabilities capabilities,
      boolean sourceBacked,
      String missingContractGuidance) {
    if (exactContracts == null || exactContracts.isBlank()) {
      return missingContractGuidance;
    }
    return "\n\nExact type contracts already retrieved by the backend:\n"
        + exactContracts
        + "\n\nDo not call describe_types. The next action must be commit_model_batch using"
        + " these exact contracts. "
        + (sourceBacked
            ? "Submit one coherent source slice: when the current prompt supplies 20 or fewer"
                + " source units and the evidence fits the cap, cover every supplied source unit"
                + " in this checkpoint. Otherwise prefer 8 to 12 related source units when the"
                + " required elements fit within "
            : "Submit one compact, structurally complete model slice using only these PIM/CIM"
                + " types and their listed containment and reference features. Keep the scope"
                + " within ")
        + capabilities.maxPatchCreates()
        + " creates, "
        + capabilities.maxPatchConnections()
        + " connections, and "
        + capabilities.maxPatchEvidence()
        + (sourceBacked
            ? " evidence items. Set turnComplete:false when later source units remain."
            : " evidence items. Set turnComplete:false only when requested work remains.");
  }

  private List<String> emptyModelFirstSliceTypes(ModelLevel level, ModelWorkspace workspace) {
    // Type choice belongs to metamodel discovery and the typed plan.  A fixed starter list makes
    // unrelated requests converge on generic CIM canvases or serverless PIM architectures.
    return List.of();
  }

  private JsonNode currentSlicePlan(JsonNode modelingPlan) {
    var scoped = mapper.createObjectNode();
    if (modelingPlan == null || modelingPlan.isMissingNode() || modelingPlan.isNull()) {
      return scoped;
    }
    scoped.put("intent", modelingPlan.path("intent").asText(""));
    scoped.set("features", modelingPlan.path("features").deepCopy());
    scoped.set("reuseTargets", modelingPlan.path("reuseTargets").deepCopy());
    scoped.set("newElements", modelingPlan.path("newElements").deepCopy());
    JsonNode slices = modelingPlan.path("slices");
    JsonNode current = currentSlice(modelingPlan);
    scoped.set("currentSlice", current.deepCopy());
    scoped.put("checkpoint", current.path("ordinal").asInt(1));
    scoped.put("checkpointCount", slices.isArray() ? slices.size() : 1);
    return scoped;
  }

  /** Returns the durable plan's active slice, never silently falling back to slice one. */
  private JsonNode currentSlice(JsonNode modelingPlan) {
    if (modelingPlan == null || modelingPlan.isMissingNode() || modelingPlan.isNull()) {
      return mapper.createObjectNode();
    }
    JsonNode slices = modelingPlan.path("slices");
    if (!slices.isArray() || slices.isEmpty()) return mapper.createObjectNode();
    int index = modelingPlan.path("currentSliceIndex").asInt(0);
    return slices.get(Math.max(0, Math.min(index, slices.size() - 1)));
  }

  private boolean hasNoModelElements(ModelWorkspace workspace) {
    JsonNode root = workspace.snapshot();
    String rootId = root.path("id").asText();
    return !containsModelElement(root, rootId);
  }

  private AgentModelTools.InspectionSelector inspectionSelector(JsonNode arguments) {
    return new AgentModelTools.InspectionSelector(
        stringValues(arguments.path("ids")),
        stringValues(arguments.path("eClasses")),
        stringValues(arguments.path("ownerIds")),
        arguments.path("query").asText(null),
        Math.max(0, arguments.path("page").asInt(0)),
        Math.max(1, arguments.path("pageSize").asInt(50)));
  }

  private List<String> stringValues(JsonNode values) {
    List<String> result = new ArrayList<>();
    if (values.isArray())
      values.forEach(
          value -> {
            if (!value.asText().isBlank()) result.add(value.asText());
          });
    return result;
  }

  private boolean containsModelElement(JsonNode node, String rootId) {
    if (node == null) return false;
    if (node.isObject()) {
      if (node.hasNonNull("eClass") && !rootId.equals(node.path("id").asText())) return true;
      var fields = node.properties().iterator();
      while (fields.hasNext()) {
        if (containsModelElement(fields.next().getValue(), rootId)) return true;
      }
      return false;
    }
    if (node.isArray()) {
      for (JsonNode item : node) {
        if (containsModelElement(item, rootId)) return true;
      }
    }
    return false;
  }

  private String systemPrompt(
      ModelLevel level, boolean sourceBacked, boolean sourceBlueprintPresent) {
    String language = guides.index(level);
    return promptTemplate("cim-pim-planner")
        + "\n\n"
        + """
You are a modeling agent. Return exactly one JSON object: {"action":"plan_model_edit"|
"commit_model_batch"|"analyze_source_units"|"plan_cim_blueprint"|
"inspect_model"|"describe_types"|"answer_user"|"ask_user",
"arguments":{...}}. Never
return prose outside that object. The action field is data, not a provider function/tool call.
commit_model_batch, answer_user, and ask_user are terminal.
answer_user arguments must be {"message":"a complete, non-empty answer for the user"}.
ask_user arguments must be {"message":"a complete, non-empty clarification question"}.
Legacy action analyze_source_units is accepted only when the backend resumes an older durable
source-analysis workflow. For normal attached source units, do not use it. First return
plan_model_edit. Legacy analyze_source_units arguments are compact,
LLM-authored source analysis:
{"domain":"...","sourceUnits":[{"sourceUnitId":"src-id","actors":[],"goals":[],
"capabilities":[],"requirements":[],"commands":[],"queries":[],"domainEntities":[],
"informationItems":[],"domainEvents":[],"policies":[],"relationships":[]}],"concepts":
[{"key":"stable-logical-key","kind":"actor|goal|capability|requirement|command|query|
domain_entity|information_item|domain_event|policy|business_rule|relationship|interaction|
dependency","name":"...","summary":"...","sourceUnitIds":["src-id"]}],
"crossUnitRelationships":[same concept shape]}. This analysis is persisted and reused; do not
regenerate it when <source-analysis> is already present.
After persisted source analysis exists, return plan_cim_blueprint before any contract retrieval or
patch. Its arguments are LLM-authored execution blueprint:
{"domain":"...","candidates":[{"logicalKey":"stable-key","reuseKey":"name-or-empty",
"eClass":"ExactType","name":"...","sourceUnitIds":["src-id"],"conceptKeys":["..."],
"owner":"rootId-or-logicalKey-if-known","containment":"root containment if known",
"requiredContracts":["ExactType"],"slice":1}],"relationships":[{"sourceKey":"...",
"targetKey":"...","reference":"exact reference if known","relationshipEClass":"",
"sourceUnitIds":["src-id"],"conceptKeys":["..."],"slice":1}],"slices":[{"focus":"...",
"sourceUnitIds":["src-id"],"requiredContracts":["ExactType"],"candidateKeys":["..."],
"relationshipKeys":["..."]}]}. The blueprint is persisted and reused; execution patches must
follow the current blueprint slice instead of reasoning from raw source again.
For ordinary create/edit/add-feature requests and normal source-backed document-to-CIM requests,
first return plan_model_edit before any
inspection, type contract retrieval, or patch generation. Its arguments must be:
{"intent":"CREATE_MODEL|ADD_FEATURES|EDIT_MODEL|EXPLAIN","features":["..."],
"reuseTargets":["existing names to inspect or reuse"],"newElements":["planned new element names"],
"requiredContracts":["ExactType"],"slices":[{"label":"Core architecture",
"purpose":"...","requiredContracts":["ExactType"],"sourceUnitIds":["src-id"]}]}. The plan is
durable backend state: use small coherent slices such as core architecture, events, data stores,
security, and observability when the request is broad. For source-backed work, each slice must
include exact sourceUnitIds and should group related stories by capability, workflow, or dependency
instead of making one checkpoint per story. When the prompt supplies 20 or fewer source units,
make the first slice include all supplied sourceUnitIds unless doing so would exceed patch caps.
requiredContracts may contain only exact case-sensitive EClass names from the supplied metamodel
type index, never conceptual categories such as Relationship, Storage, Security, or Observability.
Include every concrete EClass needed for each requested feature; do not collapse a complete-system
request to one aggregate contract. The backend will add Ecore-derived containment-owner contracts.
commit_model_batch arguments must match this shape:
{"creates":[{"clientRef":"tmp_stable_name","eClass":"ExactType","attributes":{},
"owner":"existingIdOrPriorClientRef","reference":"containmentFeature"}],"updates":
[{"elementId":"idOrClientRef","attributes":{},"preconditionHash":""}],"connections":
[{"source":"idOrClientRef","reference":"referenceFeature","target":"idOrClientRef"}],
"deletions":[{"elementId":"existingId"}],"evidence":[{"elementRef":"idOrClientRef",
"sourceUnitId":"","requirementId":"labelled-requirement-id-or-empty",
"kind":"INFERRED","assumption":"..."}],"planSummary":"...",
"turnComplete":true}. Every create must have a unique non-empty clientRef and exact eClass.
creates, updates, connections, deletions, and evidence are always arrays of JSON objects. Never
put a bare clientRef, id, or string in any of those arrays. A clientRef is only a field inside
a create object or a value used by owner/source/target inside another object.
For a complex or source-backed generation, create one coherent validated slice. One patch may
contain at most 48 creates, 96 connections, and 64 evidence items.
Set turnComplete:false and state the next slice in planSummary whenever additional requested
work remains. The backend saves that slice atomically and the user can continue from its
durable checkpoint. Set turnComplete:true only when the whole request is complete.
Every create needs an owner and containment feature. For an element directly contained by the
model root, use owner:"rootId" (a deterministic alias for the authoritative current root id)
with the exact root containment feature. Do not use model type names such as
CIMModel/PIMModel/AwsPsmModel as ordinary element ids.
Detail types such as AcceptanceCriterion, ProcessStep, DecisionRule, field/parameter/value
objects, policy entries, permissions, and event-source details are not standalone diagram
nodes: create them only when you also provide the exact owner clientRef/id and containment
reference, otherwise summarize that detail on a root-contained aggregate element.
Relationships are first-class model content, never optional decoration. When the request
creates a process, workflow, flow, association, dependency, or otherwise says or clearly
implies that created elements interact, include every valid connection needed to express that
meaning. For a relationship EClass whose contract exposes source and target references,
create that relationship object under its valid containment owner and connect its source and
target in the same batch; it renders as an edge, not a standalone node. For an ordinary
non-containment EReference, add a connections entry from the owning element to the target.
Connection references are source-type-specific: the reference must be listed on the source
eClass contract as a non-containment reference and must not be readonly. Do not use containment,
opposite, readonly, or target-side references as connections.
For CIM Requirement.dependsOn, the target type is Requirement. Do not use it for traceability from
requirements to commands, queries, actors, capabilities, goals, events, or domain elements; omit
that edge unless an exact compatible reference is present in the source eClass contract.
Do not invent a relationship when the user's request does not establish one.
Decide the appropriate action from the user's meaning and the available model context. Use
answer_user for questions, explanations, analysis, or advice that do not require a model
mutation, even when they mention modeling or change-related terms. Use commit_model_batch
only when the user actually asks you to mutate the model. Use ask_user only when a required
decision makes a safe response or mutation impossible.
A newly-created or otherwise empty model already has an authoritative rootId. For a create or
generation request, make safe progress by creating root-contained aggregate elements in the
batch with owner:"rootId" and the exact root containment feature, then refer to their
clientRefs for children and connections. For CIM generation, include an update for
elementId:"rootId" that sets a source-grounded domainName when it is missing. Do not ask
for an existing service, aggregate, owner, or element ID when that owner can be created in the
same batch. Ask only for a genuinely unspecified business decision, never for backend facts.
Every connection source and target must be either an exact clientRef from creates in the same
batch, rootId, or an existing inspected element id. Do not invent connection endpoint aliases,
prefixes, or variants such as info_<clientRef>; use the exact clientRef you created.
Assistant-created CIM models start with an authoritative root only. If an existing model contains
starter examples, source imports must stay additive: leave existing content in place and create
source-grounded CIM elements and relationships. Delete starter or existing content only when the
user explicitly asks for deletion in a non-source editing turn.
The prompt includes a compact current-model inventory. Use it directly for ordinary questions
and explanations. Call inspect_model with an empty id only when the inventory lacks facts
needed to answer; its result contains the complete current model. Do not inspect the same
model repeatedly.
When the inventory is sufficient, select answer_user in your first response. Do not use
describe_types merely to explain the current model: that tool is for exact contracts needed
to plan a mutation.
Call describe_types with a non-empty names array only. If you need to discover type names,
call it once with an empty names array; it returns the full exact Ecore type index, after which
you may call it once more with the selected names. After exact contracts are returned,
immediately choose a terminal action; further research wastes the provider budget.
Retrieved Ecore contracts include the required containment closure of every selected type. Use
those contracts directly rather than asking the user for a child type definition that the
backend has already provided.
In compact contracts, attributes and references marked with ! are required. Every created element
must include all required attributes and satisfy all required non-containment references for its
exact eClass in the same batch. Enum values must be copied exactly from the listed literals; never
omit a required enum such as a primitive type/classification field. For example, do not create a
Query without its required output InformationItem, and do not create a DomainEntity without its
required identityAttributes and primaryIdentityAttribute links.
Never invent types, features, ids, or enum values. Batch independent edits. Ask only when
safe progress is impossible. commit_model_batch arguments use creates, updates, connections,
deletions, evidence, planSummary, and turnComplete. Every source-backed created or inferred
element must have one evidence item: use kind SOURCE_GROUNDED with the exact <source-unit>
id when the text supports it. SOURCE_GROUNDED with a blank sourceUnitId is invalid. Use
kind INFERRED with a concise assumption and no source id only when the source does not directly
state the fact.
Do not claim completion for a source document unless every relevant source unit has explicit
evidence or you return a partial batch describing the remaining work.

"""
        + (sourceBacked
            ? """

SOURCE-TO-MODEL MODE: The attached source document is available as explicit source units.
For normal attached source units, first return plan_model_edit with a durable plan grouped by
capability, workflow, or dependency. Do not call analyze_source_units or plan_cim_blueprint unless
the prompt already contains a persisted <source-analysis> block from an older run. For a supplied
20-story document, the first execution slice should cover all 20 source units when they fit the
48-create and 64-evidence caps. It should be useful on canvas and grounded in the source: include
the shared actors, goals, business capabilities, one requirement per story, key information items,
business processes, policies, and only valid relationships that fit one structurally valid
checkpoint. Add commands, queries, events, and domain entities only in a later slice after their
exact required-reference contracts are selected. Every source-backed slice must list sourceUnitIds
copied exactly from the supplied markers.\
"""
                + (sourceBlueprintPresent
                    ? "A source blueprint is already present; model the supplied current slice."
                    : "Model the supplied source units directly in this turn. Use the durable"
                        + " modeling plan and a checkpoint instead of a separate source-planning"
                        + " action.")
                + " Set turnComplete:false\n"
                + "only when concrete source units still need a later checkpoint; otherwise set"
                + " turnComplete:true.\n"
            : "")
        + language;
  }

  private String executorSystemPrompt(
      ModelLevel level, boolean sourceBacked, boolean sourceBlueprintPresent) {
    return promptTemplate("modeling-executor")
        + "\n\n"
        + """
You are a modeling executor for a %s model. Return exactly one JSON object or one native tool call
matching the supplied action schema. The backend already accepted a durable modeling plan for this
turn. Follow only the current checkpoint slice, the backend inspection facts, and exact contracts
returned by describe_types.

Use describe_types once when exact contracts are missing. After exact contracts are supplied, the
next action must be commit_model_batch. Never invent EClasses, attributes, containment features,
reference names, ids, or enum values. Every create needs owner and containment reference; direct
root containment uses owner:"rootId". Every required attribute and required writable reference in
the exact contract must be satisfied in the same batch.
Create only TYPE contracts marked creatable=true. A creatable=false contract is supplied only to
show an existing owner, an abstract target, or the authoritative model root. Follow the containment
chain literally: if root contains an intermediate owner and that owner contains the requested
element, create/use the intermediate owner and reference its exact clientRef from the child.
OWNED_BY lines are authoritative construction recipes. An inspected existing element can be used
as an owner only when its exact eClass appears on that child's OWNED_BY line; never substitute an
element merely because its name sounds like a service, profile, container, or architecture.

Relationships are first-class model content. Add connections only for writable non-containment
references listed on the source EClass contract, and use exact created clientRefs or inspected ids
as endpoints. Create one coherent validated checkpoint. Set turnComplete:false when later
checkpoint slices remain.

%s

Validation boundary: generated assistant changes are checked only for structural Ecore/EMF
conformance by the backend.
"""
            .formatted(
                level.name(),
                sourceBacked
                    ? "For source-backed CIM conversion, cover every supplied source-unit in the"
                        + " current prompt when the sourceUnitId count is within the evidence cap."
                        + " For each story create at least one source-grounded Requirement, reuse"
                        + " shared Actors and BusinessCapabilities across stories, and add compact"
                        + " BusinessProcess, InformationItem, Policy, and valid connections where"
                        + " the exact contracts support them. Do not shrink to only the first few"
                        + " stories unless the source-unit count exceeds the caps."
                    : "");
  }

  private String readOnlySystemPrompt(ModelLevel level, WorkflowMode mode) {
    return promptTemplate("answer-explanation")
        + "\n\n"
        + """
You are a read-only %s modeling explainer. Return exactly one JSON object:
{"action":"inspect_model"|"describe_types"|"answer_user"|"ask_user","arguments":{...}}.
Never return prose outside that object.

Use the compact current-model inventory directly when it is sufficient. For current-model
questions, call inspect_model only for exact elements, neighborhoods, or full inventory details
that are missing from the prompt. For metamodel questions, call describe_types with exact type
names; call describe_types with an empty names array only when the exact type name is unknown.

Do not call plan_model_edit or commit_model_batch. Do not mutate the model.
Do not ask the backend to validate or repair generated model output. Do not create checkpoints.
Answer using only authoritative model inspection results and exact Ecore/metamodel contracts.
Validation boundary: assistant-generated changes, when they exist in other workflows, are gated
only by structural Ecore/EMF conformance; this read-only workflow performs no generated-change
validation.
"""
            .formatted(mode == WorkflowMode.EXPLAIN_METAMODEL ? "metamodel" : level.name());
  }

  private boolean repairableToolFailure(PlatformException failure) {
    // A provider can echo an EMF resource URI or an invented stable id in a connection/update.
    // It is a model-action error, not a missing HTTP resource, so let the bounded repair pass
    // correct it from the compact inventory rather than failing the complete turn.
    return failure.status() == 400 || failure.status() == 404 || failure.status() == 422;
  }

  private JsonNode repairDiagnostic(
      PlatformException failure, AgentModelTools turnTools, JsonNode rejectedArguments) {
    var diagnostic = mapper.createObjectNode();
    diagnostic.put("status", failure.status());
    diagnostic.put("category", actionFailureReason(failure));
    diagnostic.put("message", failure.getMessage() == null ? "" : failure.getMessage());
    diagnostic.put(
        "requiredAction",
        "Return one corrected tool call using only inspected ids, described contracts, and valid"
            + " features.");
    String message = failure.getMessage() == null ? "" : failure.getMessage().toLowerCase();
    var hints = diagnostic.putArray("hints");
    if (message.contains("required") && message.contains("attribute")) {
      hints.add(
          "Add the missing required attribute with a valid JSON value or remove that create.");
    }
    if (message.contains("attribute") && message.contains("not writable")) {
      hints.add(
          "Delete the rejected attribute key from that create/update. Keep only attributes listed"
              + " in the affected EClass's valid exact attributes; do not repeat the unchanged"
              + " batch.");
    }
    if (message.contains("invalid enum value")) {
      hints.add(
          "Replace the rejected enum value on the named attribute with exactly one literal from"
              + " the diagnostic's allowed-values list; do not repeat or paraphrase it.");
    }
    if (message.contains("containment") || message.contains("owner")) {
      hints.add(
          "Use a containment listed on the owner type contract; direct root ownership uses"
              + " rootId.");
    }
    if (message.contains("reference") || message.contains("target")) {
      hints.add("Use only writable non-containment references whose target type is compatible.");
    }
    if (message.contains("unknown") || message.contains("not found")) {
      hints.add(
          "Use exact clientRefs from this batch or inspected existing ids; do not invent ids.");
    }
    if (message.contains("evidence alone") || message.contains("at least one create")) {
      hints.add(
          "Evidence is provenance only. Return concrete creates, updates, connections, or"
              + " deletions for the current modeling slice; for an empty create request, create"
              + " root-contained aggregate elements with owner rootId.");
    }
    try {
      diagnostic.set("currentModelSummary", turnTools.inspectSummary());
      List<String> childTypes = rejectedCreateTypes(rejectedArguments);
      if (!childTypes.isEmpty()) {
        diagnostic.set("eligibleExistingOwners", turnTools.eligibleExistingOwners(childTypes));
        hints.add(
            "When reusing an existing owner, copy its exact id from eligibleExistingOwners; a"
                + " display name is never an id. Use the listed exact containment feature.");
      }
    } catch (RuntimeException ignored) {
      diagnostic.put("currentModelSummaryUnavailable", true);
    }
    return diagnostic;
  }

  private List<String> rejectedCreateTypes(JsonNode arguments) {
    if (arguments == null || !arguments.isObject()) return List.of();
    java.util.LinkedHashSet<String> result = new java.util.LinkedHashSet<>();
    JsonNode creates = arguments.path("creates");
    if (!creates.isArray()) return List.of();
    creates.forEach(
        create -> {
          if (!create.isObject()) return;
          String eClass = create.path("eClass").asText("").trim();
          if (!eClass.isBlank()) result.add(eClass);
        });
    return List.copyOf(result);
  }

  private String batchSummary(ModelCommandBatch batch) {
    if (batch == null) return "";
    java.util.LinkedHashSet<String> sourceIds = new java.util.LinkedHashSet<>();
    batch.evidence().stream()
        .map(ModelCommandBatch.Evidence::sourceUnitId)
        .filter(id -> id != null && !id.isBlank())
        .forEach(sourceIds::add);
    java.util.LinkedHashSet<String> creates = new java.util.LinkedHashSet<>();
    batch.creates().stream()
        .limit(20)
        .forEach(
            create ->
                creates.add(
                    (create.clientRef() == null ? "" : create.clientRef())
                        + ":"
                        + (create.eClass() == null ? "" : create.eClass())));
    return "creates="
        + creates
        + ", connections="
        + batch.connections().size()
        + ", evidenceSourceUnitIds="
        + sourceIds
        + ", planSummary="
        + batch.planSummary();
  }

  private long estimateTokens(int chars) {
    return Math.max(1L, Math.round(Math.max(0, chars) / 4.0d));
  }

  private String actionFailureReason(PlatformException failure) {
    if (failure.status() == 422) return "validation";
    if (failure.status() == 400) return "invalid_arguments";
    if (failure.status() == 404) return "unknown_element";
    return "other";
  }

  /** Keeps repair calls small while retaining the task identity and selected tool evidence. */
  private String followUpContext(String userMessage, String sourceDocument) {
    return "Original task (do not repeat source or model inventory):\n"
        + userMessage
        + (sourceDocument == null || sourceDocument.isBlank()
            ? ""
            : "\n\nSelected source evidence (untrusted data; retain exact source-unit ids):\n"
                + compactSourceEvidence(sourceDocument));
  }

  private String compactSourceEvidence(String sourceDocument) {
    int limit = 30000;
    if (sourceDocument.length() <= limit) return sourceDocument;
    StringBuilder selected = new StringBuilder(limit + 512);
    int from = 0;
    while ((from = sourceDocument.indexOf("<source-unit", from)) >= 0) {
      int end = sourceDocument.indexOf("</source-unit>", from);
      if (end < 0) break;
      end += "</source-unit>".length();
      String unit = sourceDocument.substring(from, end);
      if (selected.length() + unit.length() + 1 > limit) break;
      selected.append(unit).append('\n');
      from = end;
    }
    if (selected.isEmpty()) {
      selected.append(sourceDocument, 0, Math.min(sourceDocument.length(), limit));
    }
    return selected
        + "\n[source evidence truncated at whole source-unit boundaries for this repair; keep"
        + " represented units exact and set turnComplete:false when unrepresented units remain]";
  }

  /** Serializes the exact selected contracts without dropping legal features. */
  private String compactContracts(List<TypeContract> contracts) {
    StringBuilder result = new StringBuilder();
    result.append(
        "Legend: ! required, ? optional. Containments create owned children; references create"
            + " connections only when writable. creatable=false types are context/owner contracts"
            + " and must never appear in creates. The authoritative model root already exists and"
            + " is updated through elementId rootId.\n");
    for (TypeContract type : contracts) {
      result.append("TYPE|").append(type.eClass()).append("|creatable=").append(type.creatable());
      if (!type.supertypes().isEmpty()) {
        result.append("|supertypes=").append(type.supertypes());
      }
      if (!type.attributes().isEmpty()) {
        result.append("\nATTR|");
        appendAttributes(result, type.attributes());
      }
      List<ReferenceContract> containments =
          type.references().stream().filter(ReferenceContract::containment).toList();
      if (!containments.isEmpty()) {
        result.append("\nCONTAINS|");
        appendReferences(result, containments);
      }
      List<ReferenceContract> references =
          type.references().stream().filter(reference -> !reference.containment()).toList();
      if (!references.isEmpty()) {
        result.append("\nREFS|");
        appendReferences(result, references);
      }
      if (type.creatable()) {
        List<String> owners =
            contracts.stream()
                .flatMap(
                    owner ->
                        owner.references().stream()
                            .filter(ReferenceContract::containment)
                            .filter(reference -> acceptsContainmentTarget(type, reference))
                            .map(reference -> owner.eClass() + "." + reference.name()))
                .distinct()
                .sorted()
                .toList();
        if (!owners.isEmpty()) {
          result
              .append("\nOWNED_BY|")
              .append(type.eClass())
              .append(" <- ")
              .append(String.join(" | ", owners));
        }
      }
      result.append("\n\n");
    }
    return result.toString();
  }

  private void appendAttributes(StringBuilder result, List<AttributeContract> attributes) {
    for (AttributeContract attribute : attributes) {
      result
          .append(attribute.name())
          .append(':')
          .append(attribute.type())
          .append(attribute.required() ? "!" : "?");
      if (!attribute.enumLiterals().isEmpty()) result.append(attribute.enumLiterals());
      result.append(' ');
    }
  }

  private void appendReferences(StringBuilder result, List<ReferenceContract> references) {
    for (ReferenceContract reference : references) {
      result
          .append(reference.name())
          .append("->")
          .append(reference.targetType())
          .append(reference.containment() ? " containment" : " reference")
          .append(reference.required() ? "!" : "?")
          .append(reference.many() ? " many" : " one");
      if (reference.readonly()) result.append(" readonly");
      result.append(' ');
    }
  }

  private boolean acceptsContainmentTarget(TypeContract child, ReferenceContract containment) {
    return child.eClass().equals(containment.targetType())
        || child.supertypes().contains(containment.targetType());
  }

  private void check(
      AtomicBoolean canceled,
      Instant deadline,
      java.util.function.BooleanSupplier cancellationRequested,
      java.util.function.Supplier<PlatformException> stopReason) {
    if (canceled.get())
      throw new PlatformException(
          499, "Assistant turn was canceled before the current step completed.");
    if (cancellationRequested != null && cancellationRequested.getAsBoolean())
      throw new PlatformException(
          499, "Assistant turn was canceled before the current step completed.");
    PlatformException durableStop = stopReason == null ? null : stopReason.get();
    if (durableStop != null) throw durableStop;
    if (Instant.now().isAfter(deadline))
      throw new PlatformException(504, "Assistant turn exceeded its configured deadline.");
  }

  /** Supplies client-safe, specific activity text without exposing internal tool terminology. */
  private Map<String, Object> toolProgress(
      AgentAction.Kind tool, boolean completed, Boolean structurallyValid) {
    String stage;
    String message;
    switch (tool) {
      case ANALYZE_SOURCE_UNITS -> {
        stage = completed ? "ANALYZING_SOURCE" : "ANALYZING_SOURCE";
        message =
            completed
                ? "Source analysis is ready."
                : "Reading the source units and extracting modeling concepts.";
      }
      case PLAN_CIM_BLUEPRINT -> {
        stage = completed ? "PLANNING" : "PLANNING";
        message =
            completed
                ? "CIM blueprint is ready."
                : "Turning the source analysis into model slices and contract needs.";
      }
      case INSPECT_MODEL -> {
        stage = "READING_MODEL";
        message =
            completed
                ? "Finished reviewing the current model structure."
                : "Reviewing the current model so the requested change fits what is already there.";
      }
      case DESCRIBE_TYPES -> {
        stage = "QUERYING_METAMODEL";
        message =
            completed
                ? "Finished checking the relevant modeling rules."
                : "Checking the modeling rules and available element types needed for this"
                    + " request.";
      }
      case COMMIT_MODEL_BATCH -> {
        stage = Boolean.FALSE.equals(structurallyValid) ? "REPAIRING" : "APPLYING";
        message =
            completed
                ? Boolean.FALSE.equals(structurallyValid)
                    ? "The first draft needs a small structural adjustment; refining it now."
                    : "Model changes have been created and are structurally valid."
                : "Building the requested model changes in a working copy.";
      }
      case PLAN_SOURCE_MODEL -> {
        stage = completed ? "PLANNING" : "ANALYZING_SOURCE";
        message =
            completed
                ? "Source blueprint is ready."
                : "Mapping the source into coherent model slices.";
      }
      case PLAN_MODEL_EDIT -> {
        stage = "PLANNING";
        message =
            completed
                ? "Modeling plan is ready."
                : "Breaking the request into durable model checkpoints.";
      }
      case ANSWER_USER -> {
        stage = "COMPLETING";
        message = "Preparing a clear response based on the model context.";
      }
      case ASK_USER -> {
        stage = "WAITING";
        message = "Identifying the one decision needed before the model can be updated.";
      }
      default -> throw new IllegalStateException("Unexpected assistant tool: " + tool);
    }
    return Map.of("tool", tool.wireName(), "stage", stage, "message", message);
  }

  private void publish(String sessionId, String type, Object payload) {
    if (realtime != null) realtime.publish(sessionId, type, payload);
  }

  private ModelCommandBatch command(AgentAction action) {
    try {
      ObjectMapper mapper = new ObjectMapper();
      JsonNode normalized = normalizeCommandArguments(mapper, action.arguments());
      return mapper.readValue(normalized.toString(), ModelCommandBatch.class);
    } catch (tools.jackson.core.JacksonException ex) {
      String detail = ex.getOriginalMessage();
      throw new PlatformException(
          422,
          detail == null || detail.isBlank()
              ? "commit_model_batch arguments are invalid."
              : "commit_model_batch arguments are invalid: " + detail);
    }
  }

  private JsonNode normalizeCommandArguments(ObjectMapper mapper, JsonNode arguments)
      throws tools.jackson.core.JacksonException {
    if (arguments.isTextual()) {
      String text = arguments.asText() == null ? "" : arguments.asText().trim();
      if (text.startsWith("{")) {
        try {
          return normalizeCommandArguments(mapper, mapper.readTree(text));
        } catch (tools.jackson.core.JacksonException ex) {
          throw new PlatformException(
              422,
              "commit_model_batch arguments must be a JSON object, not malformed JSON text. "
                  + "Do not stringify arguments, arrays, or clientRefs; emit normal JSON objects.");
        }
      }
      throw new PlatformException(
          422, "commit_model_batch arguments must be a JSON object, not a string.");
    }
    if (!(arguments instanceof tools.jackson.databind.node.ObjectNode object)) {
      throw new PlatformException(422, "commit_model_batch arguments must be a JSON object.");
    }
    tools.jackson.databind.node.ObjectNode normalized = object.deepCopy();
    normalizeCommandArray(mapper, normalized, "creates");
    normalizeCommandArray(mapper, normalized, "updates");
    normalizeCommandArray(mapper, normalized, "connections");
    normalizeCommandArray(mapper, normalized, "deletions");
    normalizeCommandArray(mapper, normalized, "evidence");
    if (!normalized.has("turnComplete") || !normalized.path("turnComplete").isBoolean()) {
      throw new PlatformException(
          422,
          "commit_model_batch requires explicit boolean turnComplete. Use false only when"
              + " concrete requested work remains for a later checkpoint; otherwise use true.");
    }
    return normalized;
  }

  private void normalizeCommandArray(
      ObjectMapper mapper, tools.jackson.databind.node.ObjectNode arguments, String field)
      throws tools.jackson.core.JacksonException {
    JsonNode values = arguments.path(field);
    if (values.isTextual()) {
      String text = values.asText() == null ? "" : values.asText().trim();
      if (text.startsWith("[")) {
        try {
          values = mapper.readTree(text);
        } catch (tools.jackson.core.JacksonException ex) {
          throw new PlatformException(
              422,
              "commit_model_batch " + field + " must be an array of JSON objects, not a string.");
        }
      } else {
        throw new PlatformException(
            422,
            "commit_model_batch " + field + " must be an array of JSON objects, not a string.");
      }
    }
    if (!values.isArray()) return;
    tools.jackson.databind.node.ArrayNode normalized = mapper.createArrayNode();
    for (JsonNode value : values) {
      if (value.isTextual()) {
        String text = value.asText() == null ? "" : value.asText().trim();
        if (text.startsWith("{")) {
          JsonNode decoded = mapper.readTree(text);
          if (!decoded.isObject()) {
            throw new PlatformException(
                422, "commit_model_batch " + field + " entries must be JSON objects.");
          }
          normalized.add(decoded);
        } else {
          throw new PlatformException(
              422,
              "commit_model_batch "
                  + field
                  + " entries must be JSON objects, not clientRef strings.");
        }
      } else {
        normalized.add(value);
      }
    }
    arguments.set(field, normalized);
  }

  private JsonNode validatedSourceAnalysis(JsonNode analysis, String sourceDocument) {
    if (analysis == null
        || !analysis.isObject()
        || !analysis.path("domain").isTextual()
        || analysis.path("domain").asText().isBlank()) {
      throw new PlatformException(
          422, "analyze_source_units must include a non-empty domain and object payload.");
    }
    JsonNode units = analysis.path("sourceUnits");
    JsonNode concepts = analysis.path("concepts");
    if (!units.isArray() || units.isEmpty() || units.size() > 40) {
      throw new PlatformException(
          422, "analyze_source_units must include 1 to 40 sourceUnits records.");
    }
    if (!concepts.isArray()) {
      throw new PlatformException(422, "analyze_source_units must include concepts array.");
    }
    java.util.Set<String> expectedSourceIds = sourceUnitIds(sourceDocument);
    java.util.Set<String> representedSourceIds = new java.util.LinkedHashSet<>();
    for (JsonNode unit : units) {
      String id = unit.path("sourceUnitId").asText("").trim();
      if (!expectedSourceIds.contains(id)) {
        throw new PlatformException(422, "Source analysis references an unknown source unit.");
      }
      representedSourceIds.add(id);
    }
    for (JsonNode concept : concepts) {
      validateConceptRecord(concept, expectedSourceIds, "Source analysis concept");
    }
    for (JsonNode concept : analysis.path("crossUnitRelationships")) {
      validateConceptRecord(concept, expectedSourceIds, "Source analysis relationship");
    }
    if (!representedSourceIds.containsAll(expectedSourceIds)) {
      throw new PlatformException(
          422, "Source analysis must account for every supplied source unit.");
    }
    return analysis;
  }

  private void validateConceptRecord(
      JsonNode concept, java.util.Set<String> expectedSourceIds, String label) {
    if (!concept.isObject()
        || concept.path("key").asText("").isBlank()
        || concept.path("kind").asText("").isBlank()
        || concept.path("name").asText("").isBlank()
        || !concept.path("sourceUnitIds").isArray()
        || concept.path("sourceUnitIds").isEmpty()) {
      throw new PlatformException(422, label + " needs key, kind, name, and sourceUnitIds.");
    }
    for (JsonNode id : concept.path("sourceUnitIds")) {
      if (!expectedSourceIds.contains(id.asText("").trim())) {
        throw new PlatformException(422, label + " references an unknown source unit.");
      }
    }
  }

  private JsonNode validatedCimBlueprint(
      JsonNode blueprint, String sourceDocument, ModelLevel level) {
    if (blueprint == null
        || !blueprint.isObject()
        || !blueprint.path("domain").isTextual()
        || blueprint.path("domain").asText().isBlank()) {
      throw new PlatformException(422, "plan_cim_blueprint must include a non-empty domain.");
    }
    JsonNode slices = blueprint.path("slices");
    if (!slices.isArray() || slices.isEmpty() || slices.size() > 24) {
      throw new PlatformException(422, "plan_cim_blueprint must include 1 to 24 slices.");
    }
    java.util.Set<String> expectedSourceIds = sourceUnitIds(sourceDocument);
    java.util.Set<String> representedSourceIds = new java.util.LinkedHashSet<>();
    for (JsonNode candidate : blueprint.path("candidates")) {
      String eClass = candidate.path("eClass").asText("").trim();
      if (!eClass.isBlank() && !typeNameSane(eClass)) {
        throw new PlatformException(422, "Blueprint candidate has an invalid eClass name.");
      }
      validateBlueprintSourceIds(candidate.path("sourceUnitIds"), expectedSourceIds);
      candidate.path("sourceUnitIds").forEach(id -> representedSourceIds.add(id.asText("")));
      candidate
          .path("requiredContracts")
          .forEach(
              value -> {
                String name = value.asText("").trim();
                if (!name.isBlank() && !typeNameSane(name)) {
                  throw new PlatformException(
                      422, "Blueprint requiredContracts contain an invalid type name.");
                }
              });
    }
    for (JsonNode relationship : blueprint.path("relationships")) {
      validateBlueprintSourceIds(relationship.path("sourceUnitIds"), expectedSourceIds);
    }
    int ordinal = 1;
    for (JsonNode slice : slices) {
      if (!slice.path("focus").isTextual()
          || slice.path("focus").asText().isBlank()
          || !slice.path("sourceUnitIds").isArray()
          || slice.path("sourceUnitIds").isEmpty()) {
        throw new PlatformException(422, "Each CIM blueprint slice needs focus and sourceUnitIds.");
      }
      ((tools.jackson.databind.node.ObjectNode) slice).put("ordinal", ordinal++);
      validateBlueprintSourceIds(slice.path("sourceUnitIds"), expectedSourceIds);
      slice.path("sourceUnitIds").forEach(id -> representedSourceIds.add(id.asText("")));
    }
    if (level == ModelLevel.CIM && !representedSourceIds.containsAll(expectedSourceIds)) {
      throw new PlatformException(
          422, "CIM blueprint must account for every supplied source unit.");
    }
    return blueprint;
  }

  private void validateBlueprintSourceIds(
      JsonNode sourceIds, java.util.Set<String> expectedSourceIds) {
    if (!sourceIds.isArray()) {
      throw new PlatformException(422, "Blueprint sourceUnitIds must be arrays.");
    }
    for (JsonNode id : sourceIds) {
      if (!expectedSourceIds.contains(id.asText("").trim())) {
        throw new PlatformException(422, "Blueprint references an unknown source unit.");
      }
    }
  }

  /** Reject invalid evidence before the private workspace is mutated or a checkpoint is saved. */
  private void validateSourceEvidenceIds(ModelCommandBatch batch, String sourceDocument) {
    if (sourceDocument == null || sourceDocument.isBlank()) return;
    java.util.Set<String> expected = sourceUnitIds(sourceDocument);
    for (ModelCommandBatch.Evidence evidence : batch.evidence()) {
      if (!"SOURCE_GROUNDED".equals(normalizedEvidenceKind(evidence))) continue;
      String sourceUnitId = evidence.sourceUnitId() == null ? "" : evidence.sourceUnitId().trim();
      if (sourceUnitId.isBlank() || !expected.contains(sourceUnitId)) {
        throw new PlatformException(422, "Evidence references an unknown source unit.");
      }
    }
  }

  private boolean typeNameSane(String name) {
    return name != null && name.matches("[A-Z][A-Za-z0-9_]{1,80}");
  }

  private String checkpointMessage(ModelCommandBatch batch) {
    if (batch == null) return "Model checkpoint saved.";
    java.util.List<String> changes = new java.util.ArrayList<>();
    if (!batch.creates().isEmpty()) changes.add(batch.creates().size() + " created");
    if (!batch.updates().isEmpty()) changes.add(batch.updates().size() + " updated");
    if (!batch.connections().isEmpty()) changes.add(batch.connections().size() + " connected");
    if (!batch.deletions().isEmpty()) changes.add(batch.deletions().size() + " deleted");
    String summary = batch.planSummary() == null ? "" : batch.planSummary().trim();
    String changeSummary = changes.isEmpty() ? "" : " (" + String.join(", ", changes) + ").";
    if (summary.isBlank()) return "Model checkpoint saved" + changeSummary;
    return "Model checkpoint saved" + changeSummary + " " + summary;
  }

  /**
   * Declares the only executable native tool for a persisted workflow state.
   *
   * <p>This is deliberately typed from loop state rather than inferred by provider adapters from
   * incidental wording in a user prompt. The initial turn remains flexible so an explanation can
   * answer without manufacturing a model plan; once a durable plan exists, the next transition is
   * deterministic.
   */
  private String requiredTool(
      boolean readOnlyMode,
      boolean sourceBacked,
      boolean sourceAnalysisPresent,
      boolean sourceBlueprintPresent,
      boolean editPlanReady,
      String exactContracts) {
    if (readOnlyMode) return null;
    if (sourceBacked && sourceAnalysisPresent && !sourceBlueprintPresent && !editPlanReady) {
      return "plan_cim_blueprint";
    }
    if (!editPlanReady) return null;
    return exactContracts == null || exactContracts.isBlank()
        ? "describe_types"
        : "commit_model_batch";
  }

  /** Uses versioned resources at runtime so prompt revisions are not inert test-only artifacts. */
  private String promptTemplate(String name) {
    PromptTemplates.Template template = PromptTemplates.load(name);
    return "Prompt template "
        + template.name()
        + "@"
        + template.version()
        + ":\n"
        + template.body();
  }

  private JsonNode validatedSourceBlueprint(JsonNode blueprint, String sourceDocument) {
    if (blueprint == null
        || !blueprint.path("domain").isTextual()
        || blueprint.path("domain").asText().isBlank())
      throw new PlatformException(422, "plan_source_model must include a non-empty domain.");
    JsonNode slices = blueprint.path("slices");
    if (!slices.isArray() || slices.isEmpty() || slices.size() > 24)
      throw new PlatformException(422, "plan_source_model must include 1 to 24 ordered slices.");
    java.util.Set<String> expectedSourceIds = sourceUnitIds(sourceDocument);
    java.util.Set<String> representedSourceIds = new java.util.LinkedHashSet<>();
    for (JsonNode slice : slices) {
      if (!slice.path("focus").isTextual()
          || slice.path("focus").asText().isBlank()
          || !slice.path("sourceUnitIds").isArray()
          || slice.path("sourceUnitIds").isEmpty())
        throw new PlatformException(
            422, "Each source blueprint slice needs focus and sourceUnitIds.");
      for (JsonNode id : slice.path("sourceUnitIds")) {
        String marker = "id=\"" + id.asText() + "\"";
        if (!sourceDocument.contains(marker))
          throw new PlatformException(422, "Source blueprint references an unknown source unit.");
        representedSourceIds.add(id.asText());
      }
    }
    if (!representedSourceIds.containsAll(expectedSourceIds))
      throw new PlatformException(
          422, "Source blueprint must account for every supplied source unit.");
    return blueprint;
  }

  private void validateSourceEvidence(ModelCommandBatch batch, String sourceDocument) {
    if (batch == null || sourceDocument == null || sourceDocument.isBlank()) return;
    java.util.Set<String> expectedSourceIds = sourceUnitIds(sourceDocument);
    if (expectedSourceIds.isEmpty()) return;
    for (ModelCommandBatch.Evidence evidence : batch.evidence()) {
      String kind = normalizedEvidenceKind(evidence);
      if (!"SOURCE_GROUNDED".equals(kind)) continue;
      String id = evidence.sourceUnitId() == null ? "" : evidence.sourceUnitId().trim();
      if (id.isBlank()) {
        throw new PlatformException(
            422,
            "SOURCE_GROUNDED evidence needs a non-empty sourceUnitId copied from a supplied"
                + " <source-unit id=\"...\"> marker. Use INFERRED only for concise assumptions"
                + " not directly stated in the source.");
      }
      if (!expectedSourceIds.contains(id)) {
        throw new PlatformException(
            422,
            "Evidence sourceUnitId '"
                + id
                + "' is unknown for this turn. Use an exact id from the supplied <source-unit>"
                + " or mark the evidence as INFERRED with an assumption.");
      }
    }
  }

  private void validatePlannedSourceSliceCoverage(
      ModelCommandBatch batch, String sourceDocument, JsonNode modelingPlan) {
    if (batch == null
        || sourceDocument == null
        || sourceDocument.isBlank()
        || modelingPlan == null
        || modelingPlan.isMissingNode()
        || modelingPlan.isNull()) {
      return;
    }
    JsonNode planned = currentSlice(modelingPlan).path("sourceUnitIds");
    if (!planned.isArray() || planned.size() <= 1) return;
    java.util.Set<String> supplied = sourceUnitIds(sourceDocument);
    java.util.Set<String> required = new java.util.LinkedHashSet<>();
    planned.forEach(
        item -> {
          String id = item.asText("").trim();
          if (!id.isBlank() && supplied.contains(id)) required.add(id);
        });
    if (required.size() <= 1) return;
    java.util.Set<String> covered = new java.util.LinkedHashSet<>();
    for (ModelCommandBatch.Evidence evidence : batch.evidence()) {
      if (!"SOURCE_GROUNDED".equals(normalizedEvidenceKind(evidence))) continue;
      String id = evidence.sourceUnitId() == null ? "" : evidence.sourceUnitId().trim();
      if (required.contains(id)) covered.add(id);
    }
    int minimumCovered = Math.min(required.size(), 2);
    if (covered.size() >= minimumCovered) return;
    java.util.List<String> missing = required.stream().filter(id -> !covered.contains(id)).toList();
    throw new PlatformException(
        422,
        "The current planned source slice has multiple sourceUnitIds. A checkpoint must include"
            + " SOURCE_GROUNDED evidence for at least "
            + minimumCovered
            + " of those planned source units unless only one is structurally possible. Covered "
            + covered
            + " but still missing "
            + missing
            + ". Revise the same checkpoint so one accepted batch covers several planned source"
            + " units within the patch limits; do not replan or answer.");
  }

  private void validateSourceCheckpointUsefulness(
      ModelCommandBatch batch,
      String sourceDocument,
      JsonNode modelingPlan,
      List<TypeContract> patchContracts) {
    if (batch == null
        || sourceDocument == null
        || sourceDocument.isBlank()
        || modelingPlan == null
        || modelingPlan.isMissingNode()
        || modelingPlan.isNull()) {
      return;
    }
    JsonNode current = currentSlice(modelingPlan);
    boolean plannedRelationships =
        current.path("relationshipKeys").isArray() && !current.path("relationshipKeys").isEmpty();
    if (plannedRelationships && batch.connections().isEmpty()) {
      throw new PlatformException(
          422,
          "The current blueprint slice includes planned relationships/interactions, but the patch"
              + " has no valid connections. Add at least one structurally valid relationship using"
              + " exact described references, or remove only the invalid relationship while keeping"
              + " the slice additive.");
    }
    java.util.Set<String> availableContracts =
        patchContracts == null
            ? java.util.Set.of()
            : patchContracts.stream()
                .map(TypeContract::eClass)
                .collect(java.util.stream.Collectors.toSet());
    java.util.Set<String> plannedRichTypes = new java.util.LinkedHashSet<>();
    current
        .path("requiredContracts")
        .forEach(
            value -> {
              String eClass = value.asText("").trim();
              if (richCimType(eClass) && availableContracts.contains(eClass)) {
                plannedRichTypes.add(eClass);
              }
            });
    modelingPlan
        .path("requiredContracts")
        .forEach(
            value -> {
              String eClass = value.asText("").trim();
              if (richCimType(eClass) && availableContracts.contains(eClass)) {
                plannedRichTypes.add(eClass);
              }
            });
    modelingPlan
        .path("candidates")
        .forEach(
            candidate -> {
              if (candidate.path("slice").asInt(-1) != current.path("ordinal").asInt(1)) return;
              String eClass = candidate.path("eClass").asText("");
              if (richCimType(eClass) && availableContracts.contains(eClass)) {
                plannedRichTypes.add(eClass);
              }
            });
    if (plannedRichTypes.isEmpty()
        && sourceUnitIds(sourceDocument).size() >= 4
        && availableContracts.stream().anyMatch(this::richCimType)) {
      availableContracts.stream().filter(this::richCimType).forEach(plannedRichTypes::add);
    }
    if (plannedRichTypes.isEmpty() || batch.creates().isEmpty()) return;
    boolean createdRich =
        batch.creates().stream().anyMatch(create -> plannedRichTypes.contains(create.eClass()));
    if (createdRich) return;
    boolean onlyGeneric =
        batch.creates().stream()
            .map(create -> create.eClass() == null ? "" : create.eClass())
            .allMatch(
                eClass ->
                    eClass.equals("Actor")
                        || eClass.equals("BusinessGoal")
                        || eClass.equals("BusinessCapability")
                        || eClass.equals("Requirement"));
    if (onlyGeneric) {
      throw new PlatformException(
          422,
          "The source analysis and blueprint planned richer CIM concepts "
              + plannedRichTypes
              + " and their exact contracts are available, but the patch only creates generic"
              + " actors/goals/capabilities/requirements. Add structurally satisfiable rich"
              + " elements for the same slice, such as commands, queries, events, domain data, or"
              + " policies, while preserving valid existing draft content.");
    }
  }

  private boolean richCimType(String eClass) {
    return java.util.Set.of(
            "Command",
            "Query",
            "BusinessEvent",
            "DomainEntity",
            "InformationItem",
            "Policy",
            "DecisionModel",
            "DecisionRule")
        .contains(eClass);
  }

  private ModelCommandBatch normalizeSourceEvidence(
      ModelCommandBatch batch, String sourceDocument) {
    if (batch == null || sourceDocument == null || sourceDocument.isBlank()) return batch;
    java.util.Map<String, String> aliases = sourceUnitAliases(sourceDocument);
    if (aliases.isEmpty()) return batch;
    java.util.Map<String, String> sourceRefsByElement = sourceReferencesByElement(batch, aliases);
    boolean changed = false;
    java.util.List<ModelCommandBatch.Evidence> evidence = new java.util.ArrayList<>();
    java.util.Set<String> coveredRefs = new java.util.LinkedHashSet<>();
    for (ModelCommandBatch.Evidence item : batch.evidence()) {
      String normalizedKind = normalizedEvidenceKind(item);
      if (!normalizedKind.equals(item.kind())) changed = true;
      String sourceUnitId = item.sourceUnitId() == null ? "" : item.sourceUnitId().trim();
      String normalized = aliases.get(sourceUnitId);
      if (normalized != null && !normalized.equals(sourceUnitId)) {
        changed = true;
        sourceUnitId = normalized;
      }
      if (sourceUnitId.isBlank() && "SOURCE_GROUNDED".equals(normalizedEvidenceKind(item))) {
        String fromElement = sourceRefsByElement.get(item.elementRef());
        if (fromElement != null && !fromElement.isBlank()) {
          changed = true;
          sourceUnitId = fromElement;
        }
      }
      if (!sourceUnitId.isBlank()) coveredRefs.add(item.elementRef());
      evidence.add(
          new ModelCommandBatch.Evidence(
              item.elementRef(),
              sourceUnitId,
              item.requirementId(),
              normalizedKind,
              item.assumption()));
    }
    for (var entry : sourceRefsByElement.entrySet()) {
      if (coveredRefs.contains(entry.getKey())) continue;
      changed = true;
      evidence.add(
          new ModelCommandBatch.Evidence(
              entry.getKey(), entry.getValue(), "", "SOURCE_GROUNDED", ""));
    }
    if (!changed) return batch;
    return new ModelCommandBatch(
        batch.creates(),
        batch.updates(),
        batch.connections(),
        batch.deletions(),
        evidence,
        batch.planSummary(),
        batch.turnComplete());
  }

  private java.util.Map<String, String> sourceReferencesByElement(
      ModelCommandBatch batch, java.util.Map<String, String> aliases) {
    java.util.Map<String, String> sourceRefs = new java.util.LinkedHashMap<>();
    for (ModelCommandBatch.Create create : batch.creates()) {
      String sourceUnitId = sourceReferenceFromAttributes(create.attributes(), aliases);
      if (sourceUnitId != null && create.clientRef() != null && !create.clientRef().isBlank()) {
        sourceRefs.put(create.clientRef(), sourceUnitId);
      }
    }
    for (ModelCommandBatch.Update update : batch.updates()) {
      String sourceUnitId = sourceReferenceFromAttributes(update.attributes(), aliases);
      if (sourceUnitId != null && update.elementId() != null && !update.elementId().isBlank()) {
        sourceRefs.put(update.elementId(), sourceUnitId);
      }
    }
    return sourceRefs;
  }

  private String sourceReferenceFromAttributes(
      java.util.Map<String, JsonNode> attributes, java.util.Map<String, String> aliases) {
    if (attributes == null || attributes.isEmpty()) return null;
    for (String key :
        java.util.List.of("sourceUnitId", "sourceSpanId", "sourceReference", "sourceReferences")) {
      JsonNode value = attributes.get(key);
      String sourceUnitId = sourceReferenceFromText(value == null ? "" : value.asText(""), aliases);
      if (sourceUnitId != null) return sourceUnitId;
    }
    return null;
  }

  private String sourceReferenceFromText(String text, java.util.Map<String, String> aliases) {
    if (text == null || text.isBlank()) return null;
    String trimmed = text.trim();
    String exact = aliases.get(trimmed);
    if (exact != null) return exact;
    java.util.LinkedHashSet<String> knownIds = new java.util.LinkedHashSet<>(aliases.values());
    for (String knownId : knownIds) {
      if (trimmed.contains(knownId)) return knownId;
    }
    for (var alias : aliases.entrySet()) {
      String key = alias.getKey();
      if (key.length() > 4 && trimmed.contains(key)) return alias.getValue();
    }
    return null;
  }

  private String normalizedEvidenceKind(ModelCommandBatch.Evidence evidence) {
    if (evidence.kind() == null || evidence.kind().isBlank()) return "INFERRED";
    String normalized =
        evidence
            .kind()
            .trim()
            .toUpperCase(java.util.Locale.ROOT)
            .replace('-', '_')
            .replace(' ', '_');
    if (normalized.equals("SOURCE_GROUNDING")
        || normalized.startsWith("SOURCE_GROUNDED")
        || normalized.contains("USER_STORY")
        || normalized.contains("ACCEPTANCE")) {
      return "SOURCE_GROUNDED";
    }
    return normalized;
  }

  /**
   * Reads our local source envelope without treating document text as a regular-expression input.
   */
  private java.util.Map<String, String> sourceUnitAliases(String sourceDocument) {
    java.util.Map<String, String> aliases = new java.util.LinkedHashMap<>();
    if (sourceDocument == null) return aliases;
    int from = 0;
    while ((from = sourceDocument.indexOf("<source-unit", from)) >= 0) {
      int close = sourceDocument.indexOf('>', from);
      if (close < 0) break;
      String header = sourceDocument.substring(from, close + 1);
      String id = sourceHeaderAttribute(header, "id");
      String ordinal = sourceHeaderAttribute(header, "ordinal");
      if (!id.isBlank()) {
        aliases.put(id, id);
        int scope = id.indexOf(':');
        if (scope >= 0 && scope + 1 < id.length()) {
          aliases.putIfAbsent(id.substring(scope + 1), id);
        }
        if (!ordinal.isBlank()) {
          aliases.putIfAbsent(ordinal, id);
          aliases.putIfAbsent("source-" + ordinal, id);
          aliases.putIfAbsent("src-" + ordinal, id);
        }
      }
      from = close + 1;
    }
    from = 0;
    while ((from = sourceDocument.indexOf("<source-section", from)) >= 0) {
      int close = sourceDocument.indexOf('>', from);
      if (close < 0) break;
      String header = sourceDocument.substring(from, close + 1);
      String id = sourceHeaderAttribute(header, "id");
      if (!id.isBlank()) {
        aliases.put(id, id);
        int scope = id.indexOf(':');
        if (scope >= 0 && scope + 1 < id.length()) {
          aliases.putIfAbsent(id.substring(scope + 1), id);
        }
      }
      from = close + 1;
    }
    return aliases;
  }

  private java.util.Set<String> sourceUnitIds(String sourceDocument) {
    java.util.Set<String> ids = new java.util.LinkedHashSet<>();
    if (sourceDocument == null) return ids;
    int from = 0;
    while ((from = sourceDocument.indexOf("<source-unit", from)) >= 0) {
      int close = sourceDocument.indexOf('>', from);
      if (close < 0) break;
      String header = sourceDocument.substring(from, close + 1);
      String id = sourceHeaderAttribute(header, "id");
      if (!id.isBlank()) ids.add(id);
      from = close + 1;
    }
    from = 0;
    while ((from = sourceDocument.indexOf("<source-section", from)) >= 0) {
      int close = sourceDocument.indexOf('>', from);
      if (close < 0) break;
      String header = sourceDocument.substring(from, close + 1);
      String id = sourceHeaderAttribute(header, "id");
      if (!id.isBlank()) ids.add(id);
      from = close + 1;
    }
    return ids;
  }

  private String sourceHeaderAttribute(String header, String attribute) {
    if (header == null || attribute == null || attribute.isBlank()) return "";
    String marker = attribute + "=\"";
    int index = header.indexOf(marker);
    if (index < 0) return "";
    int start = index + marker.length();
    int end = header.indexOf('"', start);
    return end > start ? header.substring(start, end).trim() : "";
  }

  public record TurnResult(
      String message,
      List<ModelService.ModelPatchOperation> patch,
      List<ModelService.ModelPatchOperation> inversePatch,
      ModelService.ValidationResult validation,
      String provider,
      String model,
      ModelCommandBatch commandBatch,
      int providerCalls,
      long promptTokens,
      long completionTokens,
      List<io.mehdieidi.varka.platform.assistant.turn.AssistantTurnStore.ProviderCall>
          providerCallDetails,
      JsonNode modelingPlan,
      JsonNode sourceBlueprint,
      JsonNode sourceAnalysis) {}

  /** Durable router mode. Read-only modes never mutate, checkpoint, validate, or repair. */
  public enum WorkflowMode {
    AUTO(false),
    EXPLAIN_MODEL(true),
    EXPLAIN_METAMODEL(true),
    SOURCE_TO_MODEL(false),
    FEATURE_UPDATE(false),
    RESUME_REPAIR(false),
    CLARIFY(true);

    private final boolean readOnly;

    WorkflowMode(boolean readOnly) {
      this.readOnly = readOnly;
    }

    public boolean readOnly() {
      return readOnly;
    }
  }

  /**
   * Carries provider-call accounting across failed turns after the thread-local budget is cleared.
   */
  public static final class TurnExecutionException extends PlatformException {
    private final int providerCalls;
    private final long promptTokens;
    private final long completionTokens;
    private final List<io.mehdieidi.varka.platform.assistant.turn.AssistantTurnStore.ProviderCall>
        providerCallDetails;

    private TurnExecutionException(
        PlatformException cause,
        int providerCalls,
        long promptTokens,
        long completionTokens,
        List<io.mehdieidi.varka.platform.assistant.turn.AssistantTurnStore.ProviderCall>
            providerCallDetails) {
      super(cause.status(), cause.getMessage(), cause);
      this.providerCalls = providerCalls;
      this.promptTokens = Math.max(0L, promptTokens);
      this.completionTokens = Math.max(0L, completionTokens);
      this.providerCallDetails =
          providerCallDetails == null ? List.of() : List.copyOf(providerCallDetails);
    }

    public int providerCalls() {
      return providerCalls;
    }

    public long promptTokens() {
      return promptTokens;
    }

    public long completionTokens() {
      return completionTokens;
    }

    public List<io.mehdieidi.varka.platform.assistant.turn.AssistantTurnStore.ProviderCall>
        providerCallDetails() {
      return providerCallDetails;
    }
  }
}
