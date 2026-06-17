package io.mehdieidi.modless.backend.assistant;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.mehdieidi.modless.platform.core.PlatformException;
import io.mehdieidi.modless.platform.core.model.ModelLevel;
import io.mehdieidi.modless.platform.core.model.ModelRecord;
import io.mehdieidi.modless.platform.core.model.ProjectRecord;
import io.mehdieidi.modless.platform.core.model.UserRecord;
import io.mehdieidi.modless.platform.core.service.ModelService;
import io.mehdieidi.modless.platform.core.service.ProjectService;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/** Bounded assistant workflow entry point. */
@Service
public class AssistantOrchestrator {

  private static final String EXPLAIN_ONLY_SUFFIX =
      """

      I can explain the model, identify likely next steps, and describe safe changes. Enable
      the assistant provider and proposal rollout mode to draft backend-validated patches.
      """;

  private final AiProperties properties;
  private final AssistantModelProvider provider;
  private final AssistantSessionStore sessions;
  private final AssistantMemoryRepository memory;
  private final SpringAiChatMemoryService chatMemory;
  private final AssistantCatalogService catalogs;
  private final AssistantModelContextIndexService modelContexts;
  private final AssistantPatchCompiler patchCompiler;
  private final AssistantRealtimeHub realtime;
  private final AssistantHardeningService hardening;
  private final ModelService models;
  private final ProjectService projects;

  /**
   * Creates the orchestrator.
   *
   * @param properties AI settings
   * @param provider assistant provider
   * @param sessions session registry
   * @param memory durable assistant history
   * @param chatMemory Spring AI recent chat memory
   * @param catalogs retrieval catalogs
   * @param modelContexts compact model context service
   * @param patchCompiler semantic patch compiler
   * @param realtime realtime event hub
   * @param models model service
   * @param projects project service
   */
  public AssistantOrchestrator(
      AiProperties properties,
      AssistantModelProvider provider,
      AssistantSessionStore sessions,
      AssistantMemoryRepository memory,
      SpringAiChatMemoryService chatMemory,
      AssistantCatalogService catalogs,
      AssistantModelContextIndexService modelContexts,
      AssistantPatchCompiler patchCompiler,
      AssistantRealtimeHub realtime,
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
    this.realtime = realtime;
    this.hardening = hardening;
    this.models = models;
    this.projects = projects;
  }

  /**
   * Starts an assistant session.
   *
   * @param user owner user
   * @param projectId project scope
   * @param level modeling level
   * @param title session title
   * @return created session
   */
  public AssistantSessionStore.AssistantSession startSession(
      UserRecord user, String projectId, ModelLevel level, String title) {
    ProjectRecord project = projects.get(user, projectId);
    String modelId = project.activeModelIds().get(level.apiName());
    Long revision = null;
    if (modelId != null && !modelId.isBlank()) {
      revision = models.get(user, level, modelId).revision();
    }
    memory.ensureThread(user, projectId, level, title, modelId, revision);
    return sessions.create(user.id(), projectId, level, title);
  }

  /**
   * Handles one user message.
   *
   * @param user owner user
   * @param sessionId session ID
   * @param request message request
   * @return assistant response
   */
  public AssistantTurnResponse handleMessage(
      UserRecord user, String sessionId, AssistantTurnRequest request) {
    hardening.checkRateLimit(user.id());
    AssistantSessionStore.AssistantSession session = requireSession(sessionId, user.id());
    String threadId = threadId(user.id(), session.projectId(), session.level());
    Map<String, Object> turnMetadata = new LinkedHashMap<>();
    turnMetadata.put("sessionId", sessionId);
    turnMetadata.put("modelId", request.modelId());
    turnMetadata.put("revision", request.revision());
    memory.appendMessage(threadId, "USER", request.message(), turnMetadata);
    chatMemory.appendUser(threadId, request.message());

    if (!properties.enabled()) {
      String assistantMessage = disabledMessage(session, request);
      memory.appendMessage(
          threadId,
          "ASSISTANT",
          assistantMessage,
          Map.of(
              "modelId",
              request.modelId() == null ? "" : request.modelId(),
              "workflowState",
              AssistantWorkflowState.EXPLAINED.name()));
      chatMemory.appendAssistant(threadId, assistantMessage);
      AssistantTurnResponse response =
          new AssistantTurnResponse(
              assistantMessage,
              request.modelId(),
              request.revision(),
              null,
              List.of(),
              AssistantWorkflowState.EXPLAINED);
      realtime.publish(sessionId, "chat.assistant", response);
      return response;
    }

    if (isTextualApproval(request.message())) {
      java.util.Optional<AssistantMemoryRepository.ProposalRecord> pending =
          memory.findLatestProposal(threadId, "PROPOSED");
      if (pending.isPresent()) {
        AssistantTurnResponse response = applyStoredProposal(user, session, pending.get());
        memory.appendAudit(
            pending.get().id(),
            session.projectId(),
            user.id(),
            "APPROVED",
            Map.of("proposalId", pending.get().id(), "source", "chat-message"));
        memory.appendMessage(
            threadId,
            "ASSISTANT",
            response.assistantMessage(),
            Map.of(
                "modelId",
                response.modelId() == null ? "" : response.modelId(),
                "workflowState",
                response.workflowState().name()));
        chatMemory.appendAssistant(threadId, response.assistantMessage());
        updateRollingSummary(threadId);
        realtime.publish(sessionId, "chat.assistant", response);
        return response;
      }
    }

    ProjectRecord project = projects.get(user, session.projectId());
    String modelId = resolveModelId(request.modelId(), project, session.level());
    ModelRecord model = modelId == null ? null : models.get(user, session.level(), modelId);
    if (model != null
        && request.revision() != null
        && request.revision().longValue() != model.revision()) {
      throw new PlatformException(
          409,
          "The active model changed from revision "
              + request.revision()
              + " to "
              + model.revision()
              + ". Refresh before asking the assistant "
              + "to propose a change.");
    }
    boolean proposalRequested = shouldDraftProposal(session, request);
    ModelService.ValidationResult validation =
        model == null ? null : models.validate(user, session.level(), model.id());
    AssistantModelContextIndexService.AssistantModelContext context =
        model == null
            ? (proposalRequested
                ? new AssistantModelContextIndexService.AssistantModelContext(
                    null,
                    session.projectId(),
                    session.level(),
                    session.title(),
                    0L,
                    List.of(),
                    List.of(),
                    Map.of(),
                    List.of())
                : null)
            : modelContexts.snapshot(model, validation);

    List<AssistantModelProvider.ContextSnippet> snippets =
        retrievalSnippets(request, context, session.level());
    boolean bootstrapRequested = proposalRequested && model == null;
    AssistantProposal proposal =
        bootstrapRequested
            ? createBootstrapProposal(session, context, request, snippets)
            : proposalRequested
                ? maybeCreateProposal(user, session, model, context, request, snippets)
                : null;
    String assistantMessage;
    if (proposal != null) {
      assistantMessage = proposalSummary(proposal, bootstrapRequested);
    } else if (proposalRequested) {
      assistantMessage =
          "I could not draft a safe, grounded proposal for this request. "
              + "No model changes were made. Select relevant elements or describe the "
              + "desired components and relationships more specifically.";
    } else {
      assistantMessage = respondToPrompt(session, request, context, snippets);
    }

    memory.appendMessage(
        threadId,
        "ASSISTANT",
        assistantMessage,
        Map.of(
            "modelId",
            modelId == null ? "" : modelId,
            "workflowState",
            workflowState(proposal).name()));
    chatMemory.appendAssistant(threadId, assistantMessage);
    updateRollingSummary(threadId);
    if (proposal != null && proposal.approvalRequired()) {
      memory.saveProposal(
          threadId,
          session.projectId(),
          modelId,
          model == null ? 0L : model.revision(),
          proposal,
          "PROPOSED");
      Map<String, Object> details = new LinkedHashMap<>();
      details.put("proposalId", proposal.id());
      if (modelId != null && !modelId.isBlank()) {
        details.put("modelId", modelId);
      }
      memory.appendAudit(proposal.id(), session.projectId(), user.id(), "PROPOSED", details);
    }

    Long nextRevision;
    if (proposal != null && !proposal.approvalRequired()) {
      nextRevision = model.revision() + 1L;
    } else if (proposal != null && model == null) {
      nextRevision = 0L;
    } else if (model != null) {
      nextRevision = model.revision();
    } else {
      nextRevision = request.revision();
    }
    AssistantTurnResponse response =
        new AssistantTurnResponse(
            assistantMessage,
            modelId,
            nextRevision,
            proposal,
            proposal == null ? List.of() : proposalChoices(proposal),
            workflowState(proposal));
    realtime.publish(sessionId, "chat.assistant", response);
    return response;
  }

  /**
   * Returns proposal details if stored.
   *
   * @param user owner user
   * @param sessionId session ID
   * @param proposalId proposal ID
   * @return proposal payload
   */
  public AssistantProposal proposal(UserRecord user, String sessionId, String proposalId) {
    AssistantSessionStore.AssistantSession session = requireSession(sessionId, user.id());
    return memory
        .findProposal(proposalId)
        .filter(
            record ->
                record.threadId().equals(threadId(user.id(), session.projectId(), session.level())))
        .map(AssistantMemoryRepository.ProposalRecord::proposal)
        .orElseThrow(() -> new PlatformException(404, "Assistant proposal not found."));
  }

  /**
   * Approves and applies a stored proposal when possible.
   *
   * @param user owner user
   * @param sessionId session ID
   * @param proposalId proposal ID
   * @return updated assistant response
   */
  public AssistantTurnResponse approveProposal(
      UserRecord user, String sessionId, String proposalId) {
    AssistantSessionStore.AssistantSession session = requireSession(sessionId, user.id());
    AssistantMemoryRepository.ProposalRecord record =
        memory
            .findProposal(proposalId)
            .filter(
                found ->
                    found
                        .threadId()
                        .equals(threadId(user.id(), session.projectId(), session.level())))
            .orElseThrow(() -> new PlatformException(404, "Assistant proposal not found."));
    if (!"PROPOSED".equals(record.status())) {
      throw new PlatformException(
          409, "Assistant proposal is already " + record.status().toLowerCase() + ".");
    }
    AssistantTurnResponse response = applyStoredProposal(user, session, record);
    memory.appendAudit(
        proposalId, session.projectId(), user.id(), "APPROVED", Map.of("proposalId", proposalId));
    realtime.publish(sessionId, "chat.assistant", response);
    return response;
  }

  /**
   * Rejects a stored proposal.
   *
   * @param user owner user
   * @param sessionId session ID
   * @param proposalId proposal ID
   */
  public void rejectProposal(UserRecord user, String sessionId, String proposalId) {
    AssistantSessionStore.AssistantSession session = requireSession(sessionId, user.id());
    memory
        .findProposal(proposalId)
        .filter(
            found ->
                found.threadId().equals(threadId(user.id(), session.projectId(), session.level())))
        .orElseThrow(() -> new PlatformException(404, "Assistant proposal not found."));
    memory.updateProposalStatus(proposalId, "REJECTED");
    memory.appendAudit(
        proposalId,
        session.projectId(),
        user.id(),
        "REJECTED",
        Map.of("proposalId", proposalId, "workflowState", AssistantWorkflowState.REJECTED.name()));
    realtime.publish(sessionId, "proposal.rejected", Map.of("proposalId", proposalId));
  }

  /**
   * Applies the stored inverse patch for an already-applied proposal.
   *
   * @param user owner user
   * @param sessionId session ID
   * @param proposalId proposal ID
   * @return updated assistant response
   */
  public AssistantTurnResponse undoProposal(UserRecord user, String sessionId, String proposalId) {
    AssistantSessionStore.AssistantSession session = requireSession(sessionId, user.id());
    AssistantMemoryRepository.ProposalRecord record =
        memory
            .findProposal(proposalId)
            .filter(
                found ->
                    found
                        .threadId()
                        .equals(threadId(user.id(), session.projectId(), session.level())))
            .orElseThrow(() -> new PlatformException(404, "Assistant proposal not found."));
    if (!"APPLIED".equals(record.status())) {
      throw new PlatformException(409, "Only applied assistant proposals can be undone.");
    }
    if (record.proposal().inversePatch().isEmpty()) {
      throw new PlatformException(409, "Assistant proposal has no inverse patch.");
    }
    ModelRecord model = models.get(user, session.level(), record.modelId());
    AssistantPatchCompiler.CompiledPatch inverse =
        new AssistantPatchCompiler.CompiledPatch(
            record.proposal().inversePatch(), List.of(), record.proposal().affectedElements());
    var preview = patchCompiler.apply(model.modelJson(), inverse);
    AssistantValidationSummary validation =
        validationSummary(models.validate(session.level(), preview));
    if (!validation.structurallyValid() || !validation.mandatoryPassed()) {
      memory.appendAudit(
          record.id(),
          session.projectId(),
          user.id(),
          "UNDO_FAILED",
          Map.of("reason", "Inverse patch failed mandatory validation."));
      throw new PlatformException(
          422, "Proposal cannot be undone because mandatory validation failed.");
    }
    ModelRecord updated =
        models.patch(
            user,
            session.level(),
            model.id(),
            model.name(),
            record.proposal().inversePatch(),
            model.revision());
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
        Map.of(
            "modelId",
            updated.id(),
            "revision",
            updated.revision(),
            "proposalId",
            record.id(),
            "undo",
            true));
    return new AssistantTurnResponse(
        "Proposal undone.",
        updated.id(),
        updated.revision(),
        record.proposal(),
        List.of(),
        AssistantWorkflowState.UNDONE);
  }

  /**
   * Records a bounded user choice from the assistant UI.
   *
   * @param user owner user
   * @param sessionId session ID
   * @param choiceId choice ID
   * @param optionId chosen option ID
   */
  public void submitChoice(UserRecord user, String sessionId, String choiceId, String optionId) {
    AssistantSessionStore.AssistantSession session = requireSession(sessionId, user.id());
    memory.appendAudit(
        null,
        session.projectId(),
        user.id(),
        "CHOICE",
        Map.of("choiceId", choiceId, "optionId", optionId));
    realtime.publish(
        sessionId, "assistant.choice", Map.of("choiceId", choiceId, "optionId", optionId));
  }

  /**
   * Clears runtime and durable memory for a session.
   *
   * @param user owner user
   * @param sessionId session ID
   */
  public void clear(UserRecord user, String sessionId) {
    AssistantSessionStore.AssistantSession session = requireSession(sessionId, user.id());
    memory.clearThread(threadId(user.id(), session.projectId(), session.level()));
    chatMemory.clear(threadId(user.id(), session.projectId(), session.level()));
    sessions.clear(sessionId, user.id());
  }

  private AssistantSessionStore.AssistantSession requireSession(String sessionId, String userId) {
    try {
      return sessions.require(sessionId, userId);
    } catch (PlatformException ex) {
      if (ex.status() != 404) {
        throw ex;
      }
      return memory.findThread(sessionId, userId).map(sessions::fromThread).orElseThrow(() -> ex);
    }
  }

  private String respondToPrompt(
      AssistantSessionStore.AssistantSession session,
      AssistantTurnRequest request,
      AssistantModelContextIndexService.AssistantModelContext context,
      List<AssistantModelProvider.ContextSnippet> snippets) {
    if (!properties.enabled() || !provider.available()) {
      return disabledMessage(session, request);
    }
    AssistantModelProvider.AssistantReply reply =
        provider.complete(
            new AssistantModelProvider.AssistantPrompt(
                AssistantModelRole.RESPONDER,
                systemPrompt(session, request, context),
                request.message(),
                snippets));
    return reply.content() == null || reply.content().isBlank()
        ? fallbackResponse(session, request, context)
        : reply.content();
  }

  private String fallbackResponse(
      AssistantSessionStore.AssistantSession session,
      AssistantTurnRequest request,
      AssistantModelContextIndexService.AssistantModelContext context) {
    if (context == null) {
      return "I could not load the active model context yet.";
    }
    return "I found "
        + context.elements().size()
        + " elements and "
        + context.relationships().size()
        + " relationships for "
        + session.level().name()
        + ".";
  }

  private String systemPrompt(
      AssistantSessionStore.AssistantSession session,
      AssistantTurnRequest request,
      AssistantModelContextIndexService.AssistantModelContext context) {
    String modelSummary = context == null ? "No active model." : modelContexts.summarize(context);
    return "Project: "
        + session.projectId()
        + "\nLevel: "
        + session.level()
        + "\nModel ID: "
        + nullToUnknown(request.modelId())
        + "\nRevision: "
        + nullToUnknown(request.revision())
        + "\nActive view: "
        + nullToUnknown(request.activeView())
        + "\nSelected element IDs: "
        + request.selectedElementIds()
        + "\nRollout mode: "
        + properties.mode()
        + "\n\nConversation memory:\n"
        + conversationMemory(session)
        + "\n\nModel context:\n"
        + modelSummary
        + "\n\nRelevant compact metamodel and EVL retrieval snippets are supplied with the user "
        + "request.";
  }

  private String conversationMemory(AssistantSessionStore.AssistantSession session) {
    String threadId = threadId(session.userId(), session.projectId(), session.level());
    String summary = memory.summary(threadId).orElse("");
    String recent =
        chatMemory.recent(threadId, properties.hardening().recentMessageWindow()).stream()
            .map(message -> message.role() + ": " + message.content())
            .collect(Collectors.joining("\n"));
    if (summary.isBlank()) {
      return recent;
    }
    return "Summary: " + summary + "\nRecent:\n" + recent;
  }

  private String resolveModelId(String requestedModelId, ProjectRecord project, ModelLevel level) {
    if (requestedModelId != null && !requestedModelId.isBlank()) {
      return requestedModelId.trim();
    }
    Map<String, String> activeModelIds = project.activeModelIds();
    if (activeModelIds == null) {
      return null;
    }
    String modelId = activeModelIds.get(level.apiName());
    return modelId == null ? activeModelIds.get(level.name()) : modelId;
  }

  private boolean shouldCreateModel(AssistantTurnRequest request) {
    String message = request.message().toLowerCase(java.util.Locale.ROOT);
    return message.contains("create")
        || message.contains("generate")
        || message.contains("build")
        || message.contains("design")
        || message.contains("scaffold")
        || message.contains("draft");
  }

  boolean shouldDraftProposal(
      AssistantSessionStore.AssistantSession session, AssistantTurnRequest request) {
    if (properties.mode() == AiProperties.RolloutMode.EXPLAIN_ONLY) {
      return false;
    }
    String message = request.message().toLowerCase(java.util.Locale.ROOT);
    boolean mutationIntent =
        shouldCreateModel(request)
            || message.contains("add ")
            || message.contains("connect ")
            || message.contains("change ")
            || message.contains("update ")
            || message.contains("set ")
            || message.contains("delete ")
            || message.contains("remove ")
            || message.contains("modify ")
            || message.contains("edit ")
            || message.contains("expand ")
            || message.contains("complete ")
            || message.contains("finish ")
            || message.contains("improve ")
            || message.contains("fix ")
            || message.contains("apply ")
            || message.contains("propose ")
            || message.matches(
                "(?s).*\\b(do it|apply it|apply them|make it so|go ahead|proceed)\\b.*")
            || (isTextualApproval(message) && hasRecentMutationContext(session));
    if (!mutationIntent
        && (message.contains("explain")
            || message.contains("describe")
            || message.contains("what is")
            || message.contains("how does")
            || message.contains("why "))) {
      return false;
    }
    return mutationIntent;
  }

  private boolean hasRecentMutationContext(AssistantSessionStore.AssistantSession session) {
    String threadId = threadId(session.userId(), session.projectId(), session.level());
    List<SpringAiChatMemoryService.MemoryMessage> recent =
        chatMemory.recent(threadId, properties.hardening().recentMessageWindow());
    int previousEnd = Math.max(0, recent.size() - 1);
    return recent.subList(0, previousEnd).stream()
        .map(SpringAiChatMemoryService.MemoryMessage::content)
        .map(content -> content == null ? "" : content.toLowerCase(java.util.Locale.ROOT))
        .anyMatch(
            content ->
                content.matches(
                    "(?s).*\\b(create|generate|build|design|add|connect|change|update|set|delete|"
                        + "remove|modify|edit|expand|complete|finish|improve|fix|apply|propose|"
                        + "proposal|patch|model changes)\\b.*"));
  }

  boolean isTextualApproval(String message) {
    String normalized =
        message == null
            ? ""
            : message.trim().toLowerCase(java.util.Locale.ROOT).replaceAll("[.!]+$", "").trim();
    return normalized.matches(
        "(yes|yes please|approve|approved|apply|apply it|apply them|do it|go ahead|proceed)");
  }

  private ModelRecord createStarterModel(
      UserRecord user, ProjectRecord project, AssistantSessionStore.AssistantSession session) {
    String name =
        session.title() == null || session.title().isBlank()
            ? session.level().apiName() + "-model"
            : session.title();
    ModelRecord created =
        models.create(
            user, session.level(), project.id(), name, starterModel(session.level(), name));
    Map<String, String> activeModelIds =
        new LinkedHashMap<>(project.activeModelIds() == null ? Map.of() : project.activeModelIds());
    activeModelIds.put(session.level().apiName(), created.id());
    projects.update(user, project.id(), project.name(), project.description(), activeModelIds);
    memory.ensureThread(
        user, project.id(), session.level(), session.title(), created.id(), created.revision());
    realtime.publish(
        session.id(),
        "model.updated",
        Map.of("modelId", created.id(), "revision", created.revision(), "created", true));
    return created;
  }

  private ObjectNode starterModel(ModelLevel level, String name) {
    ObjectNode root = JsonNodeFactory.instance.objectNode();
    String modelName = name == null || name.isBlank() ? level.apiName() + "-starter-model" : name;
    root.put("id", safeIdentifier(modelName + "-root"));
    root.put("name", modelName);
    root.put(
        "eClass",
        switch (level) {
          case CIM -> "CIMModel";
          case PIM -> "PIMModel";
          case PSM -> "AwsPsmModel";
        });
    root.put("modelLevel", level == ModelLevel.PSM ? "AWS_PSM" : level.name());
    if (level == ModelLevel.CIM) {
      root.put("domainName", modelName);
      root.put("businessScope", "Initial business scope for assisted modeling.");
      root.put("organizationName", "Modeling Team");
      root.put("summary", "Mandatory-valid starter model for assisted CIM modeling.");
      root.put("rationale", "Created as the initial bounded context for assistant changes.");
      root.putArray("goals")
          .add(
              attrs(
                  "id",
                  safeIdentifier(modelName + "-initial-goal"),
                  "name",
                  "Deliver Business Value",
                  "eClass",
                  "BusinessGoal",
                  "summary",
                  "Initial goal for assisted modeling.",
                  "rationale",
                  "Provides a mandatory-valid starting point.",
                  "successCriterion",
                  "The modeled business capability delivers measurable value.",
                  "businessValue",
                  "Creates a clear outcome for later refinement.",
                  "failureConsequence",
                  "The model lacks a measurable business outcome."));
      root.putArray("actors")
          .add(
              attrs(
                  "id",
                  safeIdentifier(modelName + "-initial-actor"),
                  "name",
                  "Primary Business Actor",
                  "eClass",
                  "Actor",
                  "summary",
                  "Initial actor for assisted modeling.",
                  "rationale",
                  "Provides a mandatory-valid starting point.",
                  "actorType",
                  "ORGANIZATION",
                  "trustLevel",
                  "TRUSTED_INTERNAL"));
      ObjectNode capability =
          attrs(
              "id",
              safeIdentifier(modelName + "-initial-capability"),
              "name",
              "Core Business Capability",
              "eClass",
              "BusinessCapability",
              "summary",
              "Initial capability for assisted modeling.",
              "rationale",
              "Provides a mandatory-valid starting point.",
              "responsibility",
              "Own the initial business outcome.");
      capability.putArray("supports").add(safeIdentifier(modelName + "-initial-goal"));
      root.putArray("capabilities").add(capability);
    } else if (level == ModelLevel.PIM) {
      root.put("architectureStyle", "HYBRID_SERVERLESS");
      root.put("domainName", modelName);
      root.put("defaultCorrelationIdName", "correlationId");
      initializePimCollections(root);
      ObjectNode implementationProfile = root.putObject("implementationProfile");
      implementationProfile.put("id", safeIdentifier(modelName + "-profile"));
      implementationProfile.put("name", modelName + " implementation profile");
      implementationProfile.put("eClass", "ImplementationProfile");
      implementationProfile.put("primaryLanguage", "JAVA");
      implementationProfile.put("packageManager", "MAVEN");
      implementationProfile.put("sourceLayout", "src/main/java");
      implementationProfile.put("buildCommand", "./mvnw -q test");
      implementationProfile.put("generateTypedContracts", true);
      implementationProfile.put("generateRuntimeValidation", false);
    } else if (level == ModelLevel.PSM) {
      root.put("partition", "AWS");
      root.put("platform", "AWS");
      root.put("defaultRegion", "us-east-1");
      root.put("productionMode", false);
      root.put("summary", "Mandatory-valid starter model for assisted AWS PSM modeling.");
      root.put("rationale", "Created as the initial bounded context for assistant changes.");
      String stackId = safeIdentifier(modelName + "-main-stack");
      ObjectNode stage =
          attrs(
              "id",
              safeIdentifier(modelName + "-dev-stage"),
              "name",
              "Development",
              "eClass",
              "AwsStage",
              "stageName",
              "dev",
              "environmentClass",
              "DEV",
              "accountId",
              "REVIEW_REQUIRED_ACCOUNT_ID",
              "region",
              "us-east-1",
              "requiresManualApproval",
              false,
              "confirmChangeset",
              false,
              "failOnEmptyChangeset",
              false);
      stage.putArray("deploysStacks").add(stackId);
      root.putArray("stages").add(stage);
      ObjectNode resource =
          attrs(
              "id",
              safeIdentifier(modelName + "-artifact-bucket"),
              "name",
              "Artifact Bucket",
              "eClass",
              "S3Bucket",
              "logicalId",
              "ArtifactBucket",
              "awsResourceType",
              "AWS::S3::Bucket",
              "importedResource",
              false,
              "productionCritical",
              false,
              "retainInProduction",
              false,
              "bucketName",
              safeIdentifier(modelName + "-artifacts"),
              "versioningStatus",
              "ENABLED",
              "objectLockEnabled",
              false,
              "transferAccelerationEnabled",
              false,
              "eventBridgeNotificationEnabled",
              false,
              "publicAccessMode",
              "STRICT_BLOCK_ALL",
              "bucketKeyEnabled",
              true);
      ObjectNode stack =
          attrs(
              "id",
              stackId,
              "name",
              "Main Stack",
              "eClass",
              "SamStack",
              "stackName",
              safeIdentifier(modelName + "-main"),
              "templatePath",
              "template.yaml",
              "templateDescription",
              "Starter stack for assisted modeling.",
              "useSamTransform",
              true,
              "packageIndividually",
              true,
              "validateWithSam",
              true,
              "validateWithCfnLint",
              true);
      stack.putArray("resources").add(resource);
      root.putArray("stacks").add(stack);
      root.putArray("relationshipViews");
    }
    ObjectNode diagram = root.putObject("diagram");
    diagram.putArray("elements");
    diagram.putArray("relationships");
    ObjectNode graph = root.putObject("graph");
    graph.putArray("elements");
    graph.putArray("relationships");
    graph.putArray("traceLinks");
    graph.putArray("assumptions");
    graph.putArray("validationIssues");
    graph.putArray("manualBacklog");
    root.putArray("views");
    root.putArray("fragments");
    return root;
  }

  private String safeIdentifier(String value) {
    String normalized =
        value == null
            ? ""
            : value
                .toLowerCase(java.util.Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+|-+$", "");
    return normalized.isBlank() ? "starter-model" : normalized;
  }

  private List<AssistantModelProvider.ContextSnippet> retrievalSnippets(
      AssistantTurnRequest request,
      AssistantModelContextIndexService.AssistantModelContext context,
      ModelLevel level) {
    Map<String, AssistantModelProvider.ContextSnippet> snippets = new LinkedHashMap<>();
    metamodelTypes(request.message(), level)
        .forEach(type -> addSnippet(snippets, typeDescription(type, level)));
    retrievalTerms(request.message()).stream()
        .limit(6)
        .forEach(term -> addSnippets(snippets, catalogs.search(term, level.name(), 2)));
    addSnippets(snippets, catalogs.search(request.message(), level.name(), 3));
    if (context != null) {
      context.validationIssues().stream()
          .limit(4)
          .forEach(
              issue ->
                  addSnippet(
                      snippets,
                      new AssistantModelProvider.ContextSnippet(
                          "validation", issue.constraint(), issue.message())));
    }
    return snippets.values().stream()
        .map(this::compactSnippet)
        .limit(properties.tokenBudget() > 0 ? 14 : 8)
        .toList();
  }

  private AssistantModelProvider.ContextSnippet typeDescription(String type, ModelLevel level) {
    List<AssistantModelProvider.ContextSnippet> expanded = new ArrayList<>();
    java.util.ArrayDeque<String> pending = new java.util.ArrayDeque<>();
    Set<String> described = new java.util.LinkedHashSet<>();
    pending.add(type);
    while (!pending.isEmpty() && described.size() < 6) {
      String current = pending.removeFirst();
      if (!described.add(current)) {
        continue;
      }
      List<AssistantModelProvider.ContextSnippet> descriptions =
          catalogs.describeType(current, level.name(), current.equals(type) ? 12 : 8);
      expanded.addAll(descriptions);
      descriptions.stream()
          .map(AssistantModelProvider.ContextSnippet::content)
          .map(this::requiredContainedType)
          .flatMap(java.util.Optional::stream)
          .filter(referenced -> !described.contains(referenced))
          .forEach(pending::addLast);
    }
    String content =
        expanded.stream()
            .map(snippet -> snippet.title() + ": " + snippet.content())
            .distinct()
            .collect(Collectors.joining("\n"));
    return new AssistantModelProvider.ContextSnippet("metamodel:" + level.name(), type, content);
  }

  private java.util.Optional<String> requiredContainedType(String content) {
    if (content == null || !content.contains("multiplicity 1..1")) {
      return java.util.Optional.empty();
    }
    java.util.regex.Matcher matcher =
        java.util.regex.Pattern.compile("type #/\\d+/([A-Za-z][A-Za-z0-9_]*)").matcher(content);
    return matcher.find() ? java.util.Optional.of(matcher.group(1)) : java.util.Optional.empty();
  }

  private void addSnippets(
      Map<String, AssistantModelProvider.ContextSnippet> destination,
      List<AssistantModelProvider.ContextSnippet> additions) {
    additions.forEach(snippet -> addSnippet(destination, snippet));
  }

  private void addSnippet(
      Map<String, AssistantModelProvider.ContextSnippet> destination,
      AssistantModelProvider.ContextSnippet snippet) {
    destination.putIfAbsent(snippet.source() + "#" + snippet.title(), snippet);
  }

  private AssistantModelProvider.ContextSnippet compactSnippet(
      AssistantModelProvider.ContextSnippet snippet) {
    String content = snippet.content() == null ? "" : snippet.content().trim();
    int limit = snippet.source().startsWith("metamodel:") ? 2200 : 1000;
    if (content.length() > limit) {
      content = content.substring(0, limit) + "\n[truncated]";
    }
    return new AssistantModelProvider.ContextSnippet(snippet.source(), snippet.title(), content);
  }

  private List<String> retrievalTerms(String message) {
    if (message == null || message.isBlank()) {
      return List.of();
    }
    String normalized = message.toLowerCase(java.util.Locale.ROOT);
    Set<String> stopWords =
        Set.of(
            "about",
            "after",
            "again",
            "architecture",
            "create",
            "existing",
            "improve",
            "model",
            "pattern",
            "please",
            "serverless",
            "should",
            "system",
            "their",
            "these",
            "those",
            "using",
            "when",
            "with");
    List<String> terms = new ArrayList<>();
    java.util.Arrays.stream(normalized.split("[^a-z0-9_]+"))
        .filter(term -> term.length() >= 3)
        .filter(term -> !stopWords.contains(term))
        .distinct()
        .forEach(terms::add);
    return terms.stream().distinct().toList();
  }

  private List<String> metamodelTypes(String message, ModelLevel level) {
    String normalized = message == null ? "" : message.toLowerCase(java.util.Locale.ROOT);
    List<String> types = new ArrayList<>();
    if (requiresConnectedArchitecture(message)) {
      types.addAll(
          switch (level) {
            case CIM ->
                List.of(
                    "CIMModel", "BoundedContextCandidate", "BusinessCapability", "BusinessProcess");
            case PIM ->
                List.of(
                    "ImplementationProfile",
                    "Function",
                    "EventType",
                    "Schema",
                    "SchemaField",
                    "Api",
                    "Workflow",
                    "Queue");
            case PSM ->
                List.of(
                    "AwsPsmModel",
                    "LambdaFunction",
                    "ApiGatewayApi",
                    "DynamoDbTable",
                    "EventBridgeBus",
                    "SqsQueue");
          });
    }
    if (normalized.matches(".*\\bapi\\b.*")) {
      types.add("Api");
    }
    if (normalized.matches(".*\\bfunction\\b.*")) {
      types.add("Function");
    }
    if (normalized.matches(".*\\b(data store|database|storage)\\b.*")) {
      types.add("DataStore");
    }
    if (normalized.matches(".*\\b(queue|message queue)\\b.*")) {
      types.add("Queue");
    }
    if (normalized.matches(".*\\b(workflow|saga|orchestration)\\b.*")) {
      types.add("Workflow");
    }
    return types.stream().distinct().toList();
  }

  private AssistantProposal maybeCreateProposal(
      UserRecord user,
      AssistantSessionStore.AssistantSession session,
      ModelRecord model,
      AssistantModelContextIndexService.AssistantModelContext context,
      AssistantTurnRequest request,
      List<AssistantModelProvider.ContextSnippet> snippets) {
    if (properties.mode() == AiProperties.RolloutMode.EXPLAIN_ONLY || !provider.available()) {
      return null;
    }
    JsonNode baseModel = model.modelJson();
    SemanticModelPatch patch =
        deterministicPatch(session, baseModel, request)
            .orElseGet(
                () ->
                    provider.proposePatch(
                        new AssistantModelProvider.AssistantPrompt(
                            AssistantModelRole.PLANNER,
                            plannerPrompt(session, request, context),
                            request.message(),
                            snippets)));
    patch = ensureArchitecturePatch(session, request, context, snippets, patch);
    PreparedSemanticPatch prepared =
        prepareSemanticPatch(session, request, context, snippets, baseModel, patch);
    patch = prepared.patch();
    if (patch.operations().isEmpty()) {
      return null;
    }
    AssistantPatchCompiler.CompiledPatch compiled = prepared.compiled();
    if (compiled.patch().isEmpty()) {
      return null;
    }
    var preview = patchCompiler.apply(baseModel, compiled);
    ModelService.ValidationResult previewValidation = models.validate(session.level(), preview);
    AssistantValidationSummary summary = validationSummary(previewValidation);
    if (!summary.mandatoryPassed()) {
      SemanticModelPatch repaired =
          repairSemanticPatch(session, request, context, snippets, patch, summary);
      repaired = canonicalizeEnumLiterals(repaired, context, session.level());
      validateSemanticPatch(repaired, context);
      if (!repaired.operations().isEmpty()) {
        patch = repaired;
        compiled = patchCompiler.compile(baseModel, patch);
        preview = patchCompiler.apply(baseModel, compiled);
        summary = validationSummary(models.validate(session.level(), preview));
      }
    }
    AssistantProposal.RiskLevel riskLevel = riskLevel(compiled, summary);
    boolean approvalRequired =
        riskLevel != AssistantProposal.RiskLevel.LOW
            || properties.mode() != AiProperties.RolloutMode.GUARDED_APPLY;
    AssistantProposal proposal =
        new AssistantProposal(
            java.util.UUID.randomUUID().toString(),
            compiled.affectedElements(),
            patch,
            compiled.inversePatch(),
            summary,
            riskLevel,
            approvalRequired,
            retrievalCitations(snippets, context),
            Instant.now());
    if (!approvalRequired && model != null) {
      memory.saveProposal(
          threadId(user.id(), session.projectId(), session.level()),
          session.projectId(),
          model.id(),
          model.revision(),
          proposal,
          "PROPOSED");
      applyProposal(user, session, model, proposal, compiled);
      memory.updateProposalStatus(proposal.id(), "APPLIED");
    }
    return proposal;
  }

  private java.util.Optional<SemanticModelPatch> deterministicPatch(
      AssistantSessionStore.AssistantSession session,
      JsonNode baseModel,
      AssistantTurnRequest request) {
    if (session.level() == ModelLevel.CIM) {
      java.util.Optional<SemanticModelPatch> attributePatch =
          simpleCimAttributePatch(baseModel, request.message());
      if (attributePatch.isPresent()) {
        return attributePatch;
      }
    }
    if (session.level() == ModelLevel.PIM) {
      java.util.Optional<SemanticModelPatch> attributePatch =
          simplePimAttributePatch(baseModel, request.message());
      if (attributePatch.isPresent()) {
        return attributePatch;
      }
    }
    if (session.level() == ModelLevel.PSM) {
      java.util.Optional<SemanticModelPatch> attributePatch =
          simplePsmAttributePatch(baseModel, request.message());
      if (attributePatch.isPresent()) {
        return attributePatch;
      }
    }
    return java.util.Optional.empty();
  }

  private void initializePimCollections(ObjectNode root) {
    List.of(
            "services",
            "serviceMemberships",
            "deploymentUnits",
            "environments",
            "schemas",
            "functions",
            "apis",
            "eventTypes",
            "channels",
            "schedules",
            "triggers",
            "dataStores",
            "objectStores",
            "dataAccesses",
            "workflows",
            "humanTasks",
            "externalEndpoints",
            "externalAdapters",
            "identityProviders",
            "principals",
            "policies")
        .forEach(root::putArray);
  }

  private java.util.Optional<SemanticModelPatch> simpleCimAttributePatch(
      JsonNode model, String message) {
    String rootId = rootId(model);
    if (rootId.isBlank()) {
      return java.util.Optional.empty();
    }
    java.util.regex.Matcher domain =
        java.util.regex.Pattern.compile(
                "(?i)^(set|change|update)(?:\\s+the)?\\s+domain\\s+name\\s+to\\s+(.+)$")
            .matcher(message == null ? "" : message.trim());
    if (domain.find()) {
      return java.util.Optional.of(
          new SemanticModelPatch(
              List.of(setAttribute(rootId, "domainName", cleanSimpleValue(domain.group(2))))));
    }
    return java.util.Optional.empty();
  }

  private java.util.Optional<SemanticModelPatch> simplePimAttributePatch(
      JsonNode model, String message) {
    String rootId = rootId(model);
    if (rootId.isBlank()) {
      return java.util.Optional.empty();
    }
    String normalized = message == null ? "" : message.trim();
    java.util.regex.Matcher correlation =
        java.util.regex.Pattern.compile(
                "(?i)^(set|change|update)(?:\\s+the)?\\s+default\\s+correlation\\s+id\\s+name\\s+to\\s+(.+)$")
            .matcher(normalized);
    if (correlation.find()) {
      return java.util.Optional.of(
          new SemanticModelPatch(
              List.of(
                  setAttribute(
                      rootId,
                      "defaultCorrelationIdName",
                      cleanSimpleValue(correlation.group(2))))));
    }
    java.util.regex.Matcher domain =
        java.util.regex.Pattern.compile(
                "(?i)^(set|change|update)(?:\\s+the)?\\s+domain\\s+name\\s+to\\s+(.+)$")
            .matcher(normalized);
    if (domain.find()) {
      return java.util.Optional.of(
          new SemanticModelPatch(
              List.of(setAttribute(rootId, "domainName", cleanSimpleValue(domain.group(2))))));
    }
    return java.util.Optional.empty();
  }

  private java.util.Optional<SemanticModelPatch> simplePsmAttributePatch(
      JsonNode model, String message) {
    String rootId = rootId(model);
    if (rootId.isBlank()) {
      return java.util.Optional.empty();
    }
    java.util.regex.Matcher region =
        java.util.regex.Pattern.compile(
                "(?i)^(set|change|update)(?:\\s+the)?\\s+default\\s+region\\s+to\\s+([a-z]{2}-[a-z]+-\\d+)\\.?$")
            .matcher(message == null ? "" : message.trim());
    if (region.find()) {
      return java.util.Optional.of(
          new SemanticModelPatch(
              List.of(setAttribute(rootId, "defaultRegion", cleanSimpleValue(region.group(2))))));
    }
    return java.util.Optional.empty();
  }

  private String rootId(JsonNode model) {
    return model == null || !model.isObject() ? "" : model.path("id").asText("");
  }

  private String cleanSimpleValue(String value) {
    if (value == null) {
      return "";
    }
    String cleaned = value.trim();
    cleaned = cleaned.replaceAll("[\"'`]+$", "");
    return cleaned.isBlank() ? "" : cleaned;
  }

  private boolean isArchitectureCreationRequest(String message) {
    String normalized = message == null ? "" : message.toLowerCase(java.util.Locale.ROOT);
    boolean creation =
        normalized.contains("create")
            || normalized.contains("build")
            || normalized.contains("generate")
            || normalized.contains("draft")
            || normalized.contains("model");
    boolean architecture =
        normalized.contains("architecture")
            || normalized.contains("serverless")
            || normalized.contains("backend");
    return creation && architecture;
  }

  private boolean isEmptyPimSemanticModel(JsonNode model) {
    if (model == null || !model.isObject()) {
      return true;
    }
    return semanticArraySize(model, "services") == 0
        && semanticArraySize(model, "functions") == 0
        && semanticArraySize(model, "apis") == 0
        && semanticArraySize(model, "eventTypes") == 0
        && semanticArraySize(model, "channels") == 0
        && semanticArraySize(model, "dataStores") == 0;
  }

  private int semanticArraySize(JsonNode model, String field) {
    JsonNode value = model.path(field);
    return value.isArray() ? value.size() : 0;
  }

  private SemanticModelPatch pimServerlessArchitecturePatch(JsonNode baseModel, String message) {
    String domain = requestedDomain(message);
    String idPrefix = safeIdentifier(domain);
    String serviceId = "service-" + idPrefix;
    String functionId = "function-" + idPrefix + "-request-handler";
    String apiId = "api-" + idPrefix;
    String routeId = "route-" + idPrefix + "-vend";
    String queueId = "queue-" + idPrefix + "-events";
    String eventId = "event-" + idPrefix + "-vend-completed";
    String requestSchemaId = "schema-" + idPrefix + "-request";
    String inventorySchemaId = "schema-" + idPrefix + "-inventory";
    String stateSchemaId = "schema-" + idPrefix + "-state";
    String eventSchemaId = "schema-" + idPrefix + "-event";
    String storeId = "store-" + idPrefix + "-state";
    String idempotencyId = "policy-" + idPrefix + "-idempotency";
    String rootId = baseModel.path("id").asText("");

    List<SemanticModelPatch.Operation> operations = new ArrayList<>();
    if (!rootId.isBlank()) {
      operations.add(setAttribute(rootId, "domainName", domain));
    }
    operations.add(addElement(requestSchemaId, "Schema", requestSchemaAttrs(domain)));
    operations.add(addElement(inventorySchemaId, "Schema", inventorySchemaAttrs(domain)));
    operations.add(
        addElement(stateSchemaId, "Schema", stateSchemaAttrs(domain, inventorySchemaId)));
    operations.add(addElement(eventSchemaId, "Schema", eventSchemaAttrs(domain)));
    operations.add(
        addElement(
            idempotencyId,
            "IdempotencyPolicy",
            attrs(
                "name",
                title(domain) + " Idempotency Policy",
                "keySource",
                "requestId",
                "storeRequired",
                true,
                "scope",
                "vend-request",
                "expirationSeconds",
                86400,
                "appliesToRetries",
                true,
                "appliesToDuplicateRequests",
                true)));
    operations.add(
        addElement(eventId, "EventType", eventTypeAttrs(domain, eventSchemaId, functionId)));
    operations.add(addElement(storeId, "DataStore", dataStoreAttrs(domain, stateSchemaId)));
    operations.add(
        addElement(
            functionId,
            "Function",
            functionAttrs(
                domain, eventId, storeId, idempotencyId, requestSchemaId, eventSchemaId)));
    operations.add(addElement(apiId, "Api", apiAttrs(domain, routeId, functionId)));
    operations.add(addElement(queueId, "Queue", queueAttrs(domain, eventId, functionId)));
    operations.add(
        addElement(
            serviceId,
            "ServerlessService",
            serviceAttrs(domain, functionId, apiId, queueId, storeId)));
    operations.add(
        addElement(
            "membership-" + idPrefix + "-function",
            "ServiceElementMembership",
            membershipAttrs(domain + " function ownership", serviceId, functionId)));
    operations.add(
        addElement(
            "membership-" + idPrefix + "-api",
            "ServiceElementMembership",
            membershipAttrs(domain + " API ownership", serviceId, apiId)));
    operations.add(
        addElement(
            "membership-" + idPrefix + "-queue",
            "ServiceElementMembership",
            membershipAttrs(domain + " event ownership", serviceId, queueId)));
    operations.add(
        addElement(
            "membership-" + idPrefix + "-store",
            "ServiceElementMembership",
            membershipAttrs(domain + " state ownership", serviceId, storeId)));
    return new SemanticModelPatch(operations);
  }

  private SemanticModelPatch.Operation addElement(String id, String type, ObjectNode attrs) {
    return new SemanticModelPatch.Operation(
        SemanticModelPatch.OperationType.ADD_ELEMENT, id, type, attrs, null, null);
  }

  private SemanticModelPatch.Operation setAttribute(String id, String name, String value) {
    return new SemanticModelPatch.Operation(
        SemanticModelPatch.OperationType.SET_ATTRIBUTE,
        id,
        null,
        JsonNodeFactory.instance.textNode(value),
        null,
        name);
  }

  private ObjectNode requestSchemaAttrs(String domain) {
    ObjectNode schema =
        attrs(
            "name",
            title(domain) + " Request Schema",
            "schemaKind",
            "REQUEST",
            "semanticVersion",
            "1.0.0",
            "compatibility",
            "BACKWARD",
            "additionalPropertiesAllowed",
            false);
    schema
        .putArray("fields")
        .add(
            attrs(
                "id",
                "schema-field-" + safeIdentifier(domain) + "-request-id",
                "name",
                "requestId",
                "eClass",
                "SchemaField",
                "fieldType",
                "UUID",
                "required",
                true,
                "nullable",
                false,
                "array",
                false,
                "personalData",
                false,
                "sensitiveData",
                false))
        .add(
            attrs(
                "id",
                "schema-field-" + safeIdentifier(domain) + "-correlation",
                "name",
                "correlationId",
                "eClass",
                "SchemaField",
                "fieldType",
                "STRING",
                "required",
                true,
                "nullable",
                false,
                "array",
                false,
                "personalData",
                false,
                "sensitiveData",
                false))
        .add(
            attrs(
                "id",
                "schema-field-" + safeIdentifier(domain) + "-machine",
                "name",
                "machineId",
                "eClass",
                "SchemaField",
                "fieldType",
                "STRING",
                "required",
                true,
                "nullable",
                false,
                "array",
                false,
                "personalData",
                false,
                "sensitiveData",
                false))
        .add(
            attrs(
                "id",
                "schema-field-" + safeIdentifier(domain) + "-vend-code",
                "name",
                "vendCode",
                "eClass",
                "SchemaField",
                "fieldType",
                "STRING",
                "required",
                true,
                "nullable",
                false,
                "array",
                false,
                "personalData",
                false,
                "sensitiveData",
                false));
    return schema;
  }

  private ObjectNode inventorySchemaAttrs(String domain) {
    ObjectNode schema =
        attrs(
            "name",
            title(domain) + " Inventory Schema",
            "schemaKind",
            "ENTITY",
            "semanticVersion",
            "1.0.0",
            "compatibility",
            "BACKWARD",
            "additionalPropertiesAllowed",
            false);
    schema
        .putArray("fields")
        .add(
            attrs(
                "id",
                "schema-field-" + safeIdentifier(domain) + "-slots",
                "name",
                "slotsAvailable",
                "eClass",
                "SchemaField",
                "fieldType",
                "INTEGER",
                "required",
                true,
                "nullable",
                false,
                "array",
                false,
                "personalData",
                false,
                "sensitiveData",
                false));
    return schema;
  }

  private ObjectNode stateSchemaAttrs(String domain, String inventorySchemaId) {
    ObjectNode schema =
        attrs(
            "name",
            title(domain) + " State Schema",
            "schemaKind",
            "ENTITY",
            "semanticVersion",
            "1.0.0",
            "compatibility",
            "BACKWARD",
            "additionalPropertiesAllowed",
            false);
    schema
        .putArray("fields")
        .add(
            attrs(
                "id",
                "schema-field-" + safeIdentifier(domain) + "-machine-id",
                "name",
                "machineId",
                "eClass",
                "SchemaField",
                "fieldType",
                "STRING",
                "required",
                true,
                "nullable",
                false,
                "array",
                false,
                "personalData",
                false,
                "sensitiveData",
                false,
                "descriptionForConsumers",
                "Stable vending machine identifier."))
        .add(
            attrs(
                "id",
                "schema-field-" + safeIdentifier(domain) + "-inventory",
                "name",
                "inventorySnapshot",
                "eClass",
                "SchemaField",
                "fieldType",
                "OBJECT",
                "required",
                true,
                "nullable",
                false,
                "array",
                false,
                "personalData",
                false,
                "sensitiveData",
                false,
                "descriptionForConsumers",
                "Portable inventory and machine state.",
                "objectSchema",
                inventorySchemaId));
    return schema;
  }

  private ObjectNode eventSchemaAttrs(String domain) {
    ObjectNode schema =
        attrs(
            "name",
            title(domain) + " Event Schema",
            "schemaKind",
            "EVENT",
            "semanticVersion",
            "1.0.0",
            "compatibility",
            "BACKWARD",
            "additionalPropertiesAllowed",
            false);
    schema
        .putArray("fields")
        .add(
            attrs(
                "id",
                "schema-field-" + safeIdentifier(domain) + "-event-id",
                "name",
                "eventId",
                "eClass",
                "SchemaField",
                "fieldType",
                "UUID",
                "required",
                true,
                "nullable",
                false,
                "array",
                false,
                "personalData",
                false,
                "sensitiveData",
                false))
        .add(
            attrs(
                "id",
                "schema-field-" + safeIdentifier(domain) + "-event-machine",
                "name",
                "machineId",
                "eClass",
                "SchemaField",
                "fieldType",
                "STRING",
                "required",
                true,
                "nullable",
                false,
                "array",
                false,
                "personalData",
                false,
                "sensitiveData",
                false))
        .add(
            attrs(
                "id",
                "schema-field-" + safeIdentifier(domain) + "-event-outcome",
                "name",
                "outcome",
                "eClass",
                "SchemaField",
                "fieldType",
                "STRING",
                "required",
                true,
                "nullable",
                false,
                "array",
                false,
                "personalData",
                false,
                "sensitiveData",
                false));
    return schema;
  }

  private ObjectNode eventTypeAttrs(String domain, String schemaId, String producerId) {
    ObjectNode attrs =
        attrs(
            "name",
            title(domain) + " Vend Completed Event",
            "semanticName",
            safeIdentifier(domain).replace("-", ".") + ".vend.completed",
            "version",
            "1.0.0",
            "sourceDomain",
            domain,
            "schema",
            schemaId,
            "orderingKey",
            "machineId",
            "externalEvent",
            false,
            "auditEvent",
            false,
            "replayable",
            false,
            "containsPersonalData",
            false);
    attrs.putArray("producedBy").add(producerId);
    return attrs;
  }

  private ObjectNode dataStoreAttrs(String domain, String schemaId) {
    ObjectNode dataModel =
        attrs(
            "id",
            "data-model-" + safeIdentifier(domain) + "-state",
            "name",
            title(domain) + " State Model",
            "eClass",
            "DataModel",
            "dataModelKind",
            "ENTITY",
            "aggregateRef",
            "VendingMachine",
            "ownershipBoundary",
            domain,
            "sourceOfTruth",
            true,
            "readModel",
            false,
            "auditRequired",
            true,
            "schema",
            schemaId);
    dataModel
        .putArray("storageFields")
        .add(
            attrs(
                "id",
                "field-" + safeIdentifier(domain) + "-machine-id",
                "name",
                "machineId",
                "eClass",
                "DataField",
                "fieldType",
                "STRING",
                "identifier",
                true,
                "partitionKeyCandidate",
                true,
                "required",
                true,
                "personalData",
                false,
                "sensitiveData",
                false))
        .add(
            attrs(
                "id",
                "field-" + safeIdentifier(domain) + "-inventory",
                "name",
                "inventorySnapshot",
                "eClass",
                "DataField",
                "fieldType",
                "OBJECT",
                "required",
                true,
                "personalData",
                false,
                "sensitiveData",
                false));

    ObjectNode accessPattern =
        attrs(
            "id",
            "access-" + safeIdentifier(domain) + "-by-machine",
            "name",
            title(domain) + " By Machine",
            "eClass",
            "AccessPattern",
            "patternName",
            "Get and update machine state",
            "operation",
            "READ_WRITE",
            "accessPatternKind",
            "GET_BY_ID",
            "queryBy",
            "machineId",
            "highFrequency",
            true,
            "stronglyConsistentReadRequired",
            true,
            "transactionalWriteRequired",
            true,
            "latencyTargetMs",
            "100");

    ObjectNode store =
        attrs(
            "name",
            title(domain) + " State Store",
            "storeKind",
            "DOCUMENT",
            "consistencyNeed",
            "TRANSACTIONAL",
            "transactional",
            true,
            "readOptimized",
            true,
            "writeOptimized",
            true,
            "appendOnly",
            false,
            "persistent",
            true,
            "encrypted",
            true,
            "containsPersonalData",
            false,
            "pointInTimeRecoveryRequired",
            true,
            "expectedDataVolume",
            "machine state and " + "inventory snapshots",
            "expectedAccessRate",
            "interactive vending requests");
    store.putArray("ownedDataModels").add(dataModel);
    store.putArray("accessPatterns").add(accessPattern);
    return store;
  }

  private ObjectNode functionAttrs(
      String domain,
      String eventId,
      String storeId,
      String idempotencyId,
      String inputSchemaId,
      String outputSchemaId) {
    ObjectNode contract =
        attrs(
            "id",
            "contract-" + safeIdentifier(domain) + "-request-handler",
            "name",
            title(domain) + " Handler Contract",
            "eClass",
            "FunctionContract",
            "contractVersion",
            "1.0.0",
            "validatesInput",
            false,
            "validatesOutput",
            false,
            "correlationIdField",
            "correlationId",
            "idempotencyKeyField",
            "requestId",
            "authContextRequired",
            false,
            "inputSchema",
            inputSchemaId,
            "outputSchema",
            outputSchemaId);
    contract.putArray("emittedEvents").add(eventId);

    ObjectNode attrs =
        attrs(
            "name",
            title(domain) + " Request Handler",
            "functionKind",
            "COMMAND_HANDLER",
            "responsibility",
            "Validate vend requests, update machine state, and publish completion events.",
            "handlerResponsibility",
            "Process one vending request idempotently.",
            "sourceNameSuggestion",
            "VendRequestHandler",
            "computeProfile",
            "IO_BOUND",
            "executionModel",
            "REQUEST_RESPONSE",
            "stateless",
            true,
            "publicEntryPoint",
            false,
            "readsState",
            true,
            "writesState",
            true,
            "publishesEvents",
            true,
            "requiresNetworkAccess",
            false,
            "requiresIdempotency",
            true,
            "idempotency",
            idempotencyId);
    attrs.set("contract", contract);
    attrs.putArray("reads").add(storeId);
    attrs.putArray("writes").add(storeId);
    attrs.putArray("publishes").add(eventId);
    return attrs;
  }

  private ObjectNode apiAttrs(String domain, String routeId, String functionId) {
    ObjectNode route =
        attrs(
            "id",
            routeId,
            "name",
            "Create Vend Request",
            "eClass",
            "ApiRoute",
            "method",
            "POST",
            "pathTemplate",
            "/vend",
            "operationId",
            "createVendRequest",
            "publicRoute",
            true,
            "authRequired",
            false,
            "descriptionForConsumers",
            "Submits a vending request and returns an abstract acceptance/result response.",
            "expectedSuccessStatus",
            202,
            "requestValidationRequired",
            false,
            "responseValidationRequired",
            false,
            "functionIntegration",
            functionId);
    ObjectNode api =
        attrs(
            "name",
            title(domain) + " API",
            "apiStyle",
            "RESOURCE_ORIENTED_HTTP",
            "publicName",
            title(domain) + " API",
            "version",
            "1.0.0",
            "authRequired",
            false,
            "corsRequired",
            false,
            "externalConsumerFacing",
            true,
            "generatedOpenApiRequired",
            false,
            "basePath",
            "/" + safeIdentifier(domain));
    api.putArray("routes").add(route);
    return api;
  }

  private ObjectNode queueAttrs(String domain, String eventId, String producerId) {
    ObjectNode attrs =
        attrs(
            "name",
            title(domain) + " Events Queue",
            "channelKind",
            "QUEUE",
            "orderingRequirement",
            "PER_KEY",
            "deliverySemantics",
            "AT_LEAST_ONCE",
            "partitionKeyExpression",
            "machineId",
            "encrypted",
            true,
            "replayRequired",
            false,
            "deadLetterRequired",
            false,
            "fifoRequired",
            true,
            "deduplicationRequired",
            true,
            "maxReceiveAttempts",
            1,
            "visibilityTimeoutSeconds",
            60,
            "messageRetentionSeconds",
            345600,
            "longPollingRequired",
            true);
    attrs.putArray("eventTypes").add(eventId);
    attrs.putArray("producers").add(producerId);
    return attrs;
  }

  private ObjectNode serviceAttrs(
      String domain, String functionId, String apiId, String channelId, String storeId) {
    ObjectNode attrs =
        attrs(
            "name",
            title(domain) + " Service",
            "boundaryType",
            "BOUNDED_CONTEXT_BASED",
            "responsibility",
            "Own the vending-machine backend capability across API, processing, events, "
                + "and state.",
            "ownerTeam",
            "Platform Team",
            "businessCapabilityRef",
            domain,
            "externallyExposed",
            true,
            "ownsData",
            true);
    attrs.putArray("ownsFunctions").add(functionId);
    attrs.putArray("ownsApis").add(apiId);
    attrs.putArray("ownsChannels").add(channelId);
    attrs.putArray("ownsStores").add(storeId);
    return attrs;
  }

  private ObjectNode membershipAttrs(String name, String serviceId, String elementId) {
    return attrs(
        "name", title(name), "ownershipKind", "OWNS", "service", serviceId, "element", elementId);
  }

  private ObjectNode attrs(Object... values) {
    ObjectNode node = JsonNodeFactory.instance.objectNode();
    for (int index = 0; index + 1 < values.length; index += 2) {
      String key = String.valueOf(values[index]);
      Object value = values[index + 1];
      if (value instanceof Boolean bool) {
        node.put(key, bool);
      } else if (value instanceof Integer integer) {
        node.put(key, integer);
      } else if (value instanceof Long longValue) {
        node.put(key, longValue);
      } else if (value instanceof JsonNode jsonNode) {
        node.set(key, jsonNode);
      } else {
        node.put(key, value == null ? "" : String.valueOf(value));
      }
    }
    return node;
  }

  private String requestedDomain(String message) {
    String normalized = message == null ? "serverless backend" : message.trim();
    java.util.regex.Matcher matcher =
        java.util.regex.Pattern.compile("(?i)\\bfor\\s+(.+)$").matcher(normalized);
    if (matcher.find()) {
      normalized = matcher.group(1);
    }
    normalized =
        normalized
            .replaceAll("(?i)^a\\s+", "")
            .replaceAll("(?i)^an\\s+", "")
            .replaceAll("(?i)^the\\s+", "")
            .replaceAll("(?i)\\s+architecture\\s+model$", "")
            .trim();
    return normalized.isBlank() ? "serverless backend" : normalized;
  }

  private String title(String value) {
    return java.util.Arrays.stream((value == null ? "" : value).trim().split("[\\s_-]+"))
        .filter(part -> !part.isBlank())
        .map(part -> part.substring(0, 1).toUpperCase(java.util.Locale.ROOT) + part.substring(1))
        .collect(Collectors.joining(" "));
  }

  private AssistantProposal createBootstrapProposal(
      AssistantSessionStore.AssistantSession session,
      AssistantModelContextIndexService.AssistantModelContext context,
      AssistantTurnRequest request,
      List<AssistantModelProvider.ContextSnippet> snippets) {
    JsonNode baseModel = starterModel(session.level(), session.title());
    ModelService.ValidationResult baseValidation = models.validate(session.level(), baseModel);
    AssistantModelContextIndexService.AssistantModelContext bootstrapContext =
        modelContexts.transientSnapshot(
            session.projectId(), session.level(), session.title(), 0L, baseModel, baseValidation);
    SemanticModelPatch patch =
        deterministicPatch(session, baseModel, request)
            .orElseGet(
                () ->
                    provider.proposePatch(
                        new AssistantModelProvider.AssistantPrompt(
                            AssistantModelRole.PLANNER,
                            plannerPrompt(session, request, bootstrapContext),
                            request.message(),
                            snippets)));
    patch = ensureArchitecturePatch(session, request, bootstrapContext, snippets, patch);
    PreparedSemanticPatch prepared =
        prepareSemanticPatch(session, request, bootstrapContext, snippets, baseModel, patch);
    patch = prepared.patch();
    if (patch.operations().isEmpty()) {
      return null;
    }
    AssistantPatchCompiler.CompiledPatch compiled = prepared.compiled();
    if (compiled.patch().isEmpty()) {
      return null;
    }
    var preview = patchCompiler.apply(baseModel, compiled);
    AssistantValidationSummary summary =
        validationSummary(models.validate(session.level(), preview));
    if (!summary.mandatoryPassed()) {
      SemanticModelPatch repaired =
          repairSemanticPatch(session, request, bootstrapContext, snippets, patch, summary);
      repaired = canonicalizeEnumLiterals(repaired, bootstrapContext, session.level());
      validateSemanticPatch(repaired, bootstrapContext);
      if (!repaired.operations().isEmpty()) {
        patch = repaired;
        compiled = patchCompiler.compile(baseModel, patch);
        preview = patchCompiler.apply(baseModel, compiled);
        summary = validationSummary(models.validate(session.level(), preview));
      }
    }
    return new AssistantProposal(
        java.util.UUID.randomUUID().toString(),
        compiled.affectedElements(),
        patch,
        compiled.inversePatch(),
        summary,
        AssistantProposal.RiskLevel.HIGH,
        true,
        retrievalCitations(snippets, context),
        Instant.now());
  }

  private SemanticModelPatch ensureArchitecturePatch(
      AssistantSessionStore.AssistantSession session,
      AssistantTurnRequest request,
      AssistantModelContextIndexService.AssistantModelContext context,
      List<AssistantModelProvider.ContextSnippet> snippets,
      SemanticModelPatch patch) {
    if (!requiresConnectedArchitecture(request.message()) || isConnectedArchitecturePatch(patch)) {
      return patch;
    }
    String correctionRequest =
        "Original request:\n"
            + request.message()
            + "\n\nPrevious semantic patch that was too small or disconnected:\n"
            + patch
            + "\n\nReturn a complete replacement semantic patch for the user's domain. Create "
            + "at least 12 meaningful model elements spanning the relevant concerns and at least "
            + "6 CONNECT_ELEMENTS operations so the canvas is a connected architecture, not a "
            + "collection of isolated nodes. Include semantic references in element attributes "
            + "as required by the metamodel. Do not return only root attributes or no-op changes.";
    SemanticModelPatch corrected =
        provider.proposePatch(
            new AssistantModelProvider.AssistantPrompt(
                AssistantModelRole.PLANNER,
                plannerPrompt(session, request, context)
                    + "\nThis is a bounded correction because the previous architecture was "
                    + "too small or disconnected.",
                correctionRequest,
                snippets));
    if (!isConnectedArchitecturePatch(corrected)) {
      throw new PlatformException(
          502, "AI planner did not produce a complete connected architecture.");
    }
    return corrected;
  }

  private boolean requiresConnectedArchitecture(String message) {
    String normalized = message == null ? "" : message.toLowerCase(java.util.Locale.ROOT);
    return isArchitectureCreationRequest(message)
        || normalized.contains("complete model")
        || normalized.contains("complete architecture")
        || normalized.contains("expand the model")
        || normalized.contains("expand this model")
        || normalized.contains("more complete")
        || normalized.contains("too incomplete");
  }

  private boolean isConnectedArchitecturePatch(SemanticModelPatch patch) {
    if (patch == null) {
      return false;
    }
    long additions =
        patch.operations().stream()
            .filter(operation -> operation != null)
            .filter(operation -> operation.type() == SemanticModelPatch.OperationType.ADD_ELEMENT)
            .count();
    long connections =
        patch.operations().stream()
            .filter(operation -> operation != null)
            .filter(
                operation -> operation.type() == SemanticModelPatch.OperationType.CONNECT_ELEMENTS)
            .count();
    return additions >= 12 && connections >= 6;
  }

  private SemanticModelPatch repairSemanticPatch(
      AssistantSessionStore.AssistantSession session,
      AssistantTurnRequest request,
      AssistantModelContextIndexService.AssistantModelContext context,
      List<AssistantModelProvider.ContextSnippet> snippets,
      SemanticModelPatch failedPatch,
      AssistantValidationSummary validation) {
    String failures =
        validation.issues().stream()
            .filter(issue -> "ERROR".equalsIgnoreCase(issue.severity()))
            .limit(10)
            .map(issue -> issue.constraint() + ": " + issue.message())
            .collect(Collectors.joining("\n"));
    String repairRequest =
        "Original request:\n"
            + request.message()
            + "\n\nPrevious semantic patch:\n"
            + failedPatch
            + "\n\nMandatory validation failures to repair:\n"
            + failures
            + "\n\nReturn a complete replacement semantic patch that satisfies the original "
            + "request and repairs these failures.";
    Map<String, AssistantModelProvider.ContextSnippet> repairSnippets = new LinkedHashMap<>();
    addSnippets(repairSnippets, snippets);
    validation.issues().stream()
        .filter(issue -> "ERROR".equalsIgnoreCase(issue.severity()))
        .limit(10)
        .forEach(
            issue -> {
              addSnippets(
                  repairSnippets, catalogs.search(issue.constraint(), session.level().name(), 2));
              addSnippets(
                  repairSnippets, catalogs.search(issue.message(), session.level().name(), 2));
            });
    Map<String, String> elementTypes =
        context.elements().stream()
            .collect(
                Collectors.toMap(
                    AssistantModelContextIndexService.ContextElement::id,
                    AssistantModelContextIndexService.ContextElement::type,
                    (first, ignored) -> first,
                    LinkedHashMap::new));
    failedPatch.operations().stream()
        .filter(operation -> operation.type() == SemanticModelPatch.OperationType.ADD_ELEMENT)
        .forEach(
            operation -> elementTypes.put(operation.targetElementId(), operation.elementType()));
    failedPatch.operations().stream()
        .map(
            operation ->
                operation.elementType() != null
                    ? operation.elementType()
                    : elementTypes.get(operation.targetElementId()))
        .filter(type -> type != null && !type.isBlank())
        .distinct()
        .forEach(type -> addSnippet(repairSnippets, typeDescription(type, session.level())));
    return provider.proposePatch(
        new AssistantModelProvider.AssistantPrompt(
            AssistantModelRole.PLANNER,
            plannerPrompt(session, request, context)
                + "\nThis is the single bounded validation-repair pass.",
            repairRequest,
            repairSnippets.values().stream().map(this::compactSnippet).limit(20).toList()));
  }

  private PreparedSemanticPatch prepareSemanticPatch(
      AssistantSessionStore.AssistantSession session,
      AssistantTurnRequest request,
      AssistantModelContextIndexService.AssistantModelContext context,
      List<AssistantModelProvider.ContextSnippet> snippets,
      JsonNode baseModel,
      SemanticModelPatch proposedPatch) {
    SemanticModelPatch patch = canonicalizeEnumLiterals(proposedPatch, context, session.level());
    try {
      validateSemanticPatch(patch, context);
      return new PreparedSemanticPatch(patch, patchCompiler.compile(baseModel, patch));
    } catch (PlatformException failure) {
      AssistantValidationSummary groundingFailure =
          new AssistantValidationSummary(
              false,
              false,
              0,
              List.of(
                  new AssistantValidationSummary.Issue(
                      "ERROR", "SemanticPatchGrounding", null, failure.getMessage())));
      SemanticModelPatch repaired =
          repairSemanticPatch(session, request, context, snippets, patch, groundingFailure);
      repaired = canonicalizeEnumLiterals(repaired, context, session.level());
      validateSemanticPatch(repaired, context);
      return new PreparedSemanticPatch(repaired, patchCompiler.compile(baseModel, repaired));
    }
  }

  private void applyProposal(
      UserRecord user,
      AssistantSessionStore.AssistantSession session,
      ModelRecord model,
      AssistantProposal proposal,
      AssistantPatchCompiler.CompiledPatch compiled) {
    try {
      ModelRecord updated =
          models.patch(
              user, session.level(), model.id(), model.name(), compiled.patch(), model.revision());
      memory.appendAudit(
          proposal.id(),
          session.projectId(),
          user.id(),
          "APPLIED",
          Map.of("modelId", updated.id(), "revision", updated.revision()));
      realtime.publish(
          session.id(),
          "model.updated",
          Map.of(
              "modelId",
              updated.id(),
              "revision",
              updated.revision(),
              "proposalId",
              proposal.id()));
    } catch (Exception ex) {
      memory.appendAudit(
          proposal.id(),
          session.projectId(),
          user.id(),
          "FAILED",
          Map.of("error", ex.getMessage()));
      throw ex;
    }
  }

  private AssistantTurnResponse applyStoredProposal(
      UserRecord user,
      AssistantSessionStore.AssistantSession session,
      AssistantMemoryRepository.ProposalRecord record) {
    ModelRecord model =
        record.modelId() == null || record.modelId().isBlank()
            ? createStarterModel(user, projects.get(user, session.projectId()), session)
            : models.get(user, session.level(), record.modelId());
    AssistantPatchCompiler.CompiledPatch compiled =
        patchCompiler.compile(model.modelJson(), record.proposal().patch());
    var preview = patchCompiler.apply(model.modelJson(), compiled);
    AssistantValidationSummary validation =
        validationSummary(models.validate(session.level(), preview));
    if (!validation.structurallyValid() || !validation.mandatoryPassed()) {
      memory.updateProposalStatus(record.id(), "FAILED");
      memory.appendAudit(
          record.id(),
          session.projectId(),
          user.id(),
          "FAILED",
          Map.of("reason", "Proposal no longer passes mandatory validation."));
      throw new PlatformException(
          422, "Proposal cannot be applied because mandatory validation failed.");
    }
    ModelRecord updated =
        compiled.patch().isEmpty()
            ? model
            : models.patch(
                user,
                session.level(),
                model.id(),
                model.name(),
                compiled.patch(),
                model.revision());
    memory.updateProposalStatus(record.id(), "APPLIED");
    memory.appendAudit(
        record.id(),
        session.projectId(),
        user.id(),
        "APPLIED",
        Map.of("modelId", updated.id(), "revision", updated.revision()));
    realtime.publish(
        session.id(),
        "model.updated",
        Map.of("modelId", updated.id(), "revision", updated.revision(), "proposalId", record.id()));
    return new AssistantTurnResponse(
        "Proposal applied.",
        updated.id(),
        updated.revision(),
        record.proposal(),
        List.of(),
        AssistantWorkflowState.APPLIED);
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
    long optional =
        issues.stream().filter(issue -> "WARNING".equalsIgnoreCase(issue.severity())).count();
    boolean mandatoryPassed =
        issues.stream().noneMatch(issue -> "ERROR".equalsIgnoreCase(issue.severity()));
    return new AssistantValidationSummary(
        result == null || result.valid(), mandatoryPassed, (int) optional, issues);
  }

  private AssistantProposal.RiskLevel riskLevel(
      AssistantPatchCompiler.CompiledPatch compiled, AssistantValidationSummary summary) {
    boolean destructive =
        compiled.patch().stream().anyMatch(operation -> "remove".equalsIgnoreCase(operation.op()));
    if (destructive || !summary.mandatoryPassed()) {
      return AssistantProposal.RiskLevel.HIGH;
    }
    if (compiled.patch().size() > 1 || summary.optionalIssues() > 0) {
      return AssistantProposal.RiskLevel.MEDIUM;
    }
    return AssistantProposal.RiskLevel.LOW;
  }

  private String plannerPrompt(
      AssistantSessionStore.AssistantSession session,
      AssistantTurnRequest request,
      AssistantModelContextIndexService.AssistantModelContext context) {
    return systemPrompt(session, request, context)
        + "\n\nExisting targets must use stable IDs listed in Model context. Prefer these "
        + "currently selected IDs when relevant: "
        + request.selectedElementIds()
        + "\nADD_ELEMENT may mint a concise new stable ID. It may reference other existing "
        + "or newly-added stable IDs in its attributes when the metamodel permits it."
        + "\nRepresent every required 1..1 containment reference in ADD_ELEMENT attributes as "
        + "a complete nested object with id, eClass, name, and its required features."
        + "\nFor a contained child in a multi-valued owner feature, use ADD_ELEMENT with "
        + "sourceElementId set to the owner ID and referenceName set to the exact containment "
        + "feature. Do not add owned children as unparented root or canvas-only elements."
        + "\nOmit optional references unless their target is an existing element, another "
        + "ADD_ELEMENT operation, or a complete nested containment object. Never emit an "
        + "unresolved reference ID."
        + "\nNo canvas selection is required for ADD_ELEMENT. Do not return an empty patch for "
        + "an unambiguous creation request merely because selected IDs are empty."
        + "\nCONNECT_ELEMENTS may connect existing or newly-added IDs. Use the exact metamodel "
        + "reference name, not a generic visual label."
        + "\nEnum attribute values must use the exact case-sensitive literal from the metamodel "
        + "catalog, for example TYPESCRIPT rather than TypeScript."
        + "\nFor architecture creation, completion, or expansion requests, produce a "
        + "domain-complete model rather than a minimal scaffold. Cover the relevant APIs, "
        + "functions, data, events/messaging, workflows, security, policies, and external "
        + "integrations. Use at least 12 meaningful ADD_ELEMENT operations and at least 6 "
        + "CONNECT_ELEMENTS operations. Every major element should participate in a semantic "
        + "reference and a visible connection; avoid isolated canvas nodes."
        + "\nFor narrowly scoped pattern requests, add the complete set of elements and "
        + "references needed to represent the requested pattern."
        + "\nIf no saved model exists yet, draft a bootstrap proposal for the blank starter "
        + session.level().apiName()
        + " model rather than returning no operations."
        + "\nUse at most "
        + properties.maxToolCalls()
        + " semantic operations. Return no operations when user intent is ambiguous.";
  }

  private void validateSemanticPatch(
      SemanticModelPatch patch, AssistantModelContextIndexService.AssistantModelContext context) {
    if (patch.operations().size() > properties.maxToolCalls()) {
      throw new PlatformException(502, "AI planner proposal exceeded the operation limit.");
    }
    Set<String> existingIds =
        context.elements().stream()
            .map(AssistantModelContextIndexService.ContextElement::id)
            .collect(Collectors.toSet());
    for (SemanticModelPatch.Operation operation : patch.operations()) {
      if (operation.type() == null) {
        throw new PlatformException(422, "Assistant proposed an operation without a type.");
      }
      switch (operation.type()) {
        case ADD_ELEMENT -> {
          if (operation.targetElementId() == null
              || operation.targetElementId().isBlank()
              || existingIds.contains(operation.targetElementId())) {
            throw new PlatformException(502, "AI planner proposed an invalid new element ID.");
          }
          if (operation.sourceElementId() != null
              && (!existingIds.contains(operation.sourceElementId())
                  || operation.referenceName() == null
                  || operation.referenceName().isBlank())) {
            throw new PlatformException(
                502, "AI planner proposed an ungrounded contained element.");
          }
          existingIds.add(operation.targetElementId());
        }
        case CONNECT_ELEMENTS -> {
          if (!existingIds.contains(operation.sourceElementId())
              || !existingIds.contains(operation.targetElementId())
              || operation.referenceName() == null
              || operation.referenceName().isBlank()) {
            throw new PlatformException(502, "AI planner proposed an ungrounded relationship.");
          }
        }
        case SET_ATTRIBUTE, DELETE_ELEMENT -> {
          if (!existingIds.contains(operation.targetElementId())) {
            throw new PlatformException(502, "AI planner proposed an unknown target element.");
          }
          if (operation.type() == SemanticModelPatch.OperationType.SET_ATTRIBUTE
              && (operation.referenceName() == null || operation.referenceName().isBlank())) {
            throw new PlatformException(
                502, "AI planner proposed an attribute update without an attribute.");
          }
        }
        default ->
            throw new PlatformException(
                502, "AI planner proposed an unsupported semantic operation.");
      }
    }
  }

  private SemanticModelPatch canonicalizeEnumLiterals(
      SemanticModelPatch patch,
      AssistantModelContextIndexService.AssistantModelContext context,
      ModelLevel level) {
    Map<String, String> elementTypes =
        context.elements().stream()
            .collect(
                Collectors.toMap(
                    AssistantModelContextIndexService.ContextElement::id,
                    AssistantModelContextIndexService.ContextElement::type,
                    (first, ignored) -> first,
                    LinkedHashMap::new));
    List<SemanticModelPatch.Operation> operations = new ArrayList<>();
    for (SemanticModelPatch.Operation operation : patch.operations()) {
      String elementType =
          operation.elementType() == null
              ? elementTypes.get(operation.targetElementId())
              : operation.elementType();
      JsonNode attributes = operation.attributes();
      if (operation.type() == SemanticModelPatch.OperationType.SET_ATTRIBUTE
          && attributes != null
          && attributes.isTextual()
          && elementType != null) {
        attributes =
            catalogs
                .canonicalEnumLiteral(
                    elementType, operation.referenceName(), attributes.asText(), level.name())
                .<JsonNode>map(JsonNodeFactory.instance::textNode)
                .orElse(attributes);
      } else if (operation.type() == SemanticModelPatch.OperationType.ADD_ELEMENT
          && attributes instanceof ObjectNode object
          && elementType != null) {
        ObjectNode canonical = object.deepCopy();
        object
            .fields()
            .forEachRemaining(
                entry -> {
                  if (!entry.getValue().isTextual()) {
                    return;
                  }
                  catalogs
                      .canonicalEnumLiteral(
                          elementType, entry.getKey(), entry.getValue().asText(), level.name())
                      .ifPresent(value -> canonical.put(entry.getKey(), value));
                });
        attributes = canonical;
      }
      operations.add(
          new SemanticModelPatch.Operation(
              operation.type(),
              operation.targetElementId(),
              operation.elementType(),
              attributes,
              operation.sourceElementId(),
              operation.referenceName()));
      if (operation.type() == SemanticModelPatch.OperationType.ADD_ELEMENT
          && operation.targetElementId() != null
          && elementType != null) {
        elementTypes.put(operation.targetElementId(), elementType);
      }
    }
    return new SemanticModelPatch(operations);
  }

  private List<AssistantChoice> proposalChoices(AssistantProposal proposal) {
    if (proposal == null || !proposal.approvalRequired()) {
      return List.of();
    }
    return List.of(
        new AssistantChoice(
            "proposal-decision",
            "Choose how to proceed",
            List.of(
                new AssistantChoice.Option(
                    "approve", "Approve", "Apply the proposal after validation."),
                new AssistantChoice.Option("reject", "Reject", "Keep the model unchanged."))));
  }

  private List<String> retrievalCitations(
      List<AssistantModelProvider.ContextSnippet> snippets,
      AssistantModelContextIndexService.AssistantModelContext context) {
    List<String> citations = new ArrayList<>();
    snippets.stream()
        .map(snippet -> snippet.source() + "#" + snippet.title())
        .filter(value -> !value.isBlank())
        .distinct()
        .limit(8)
        .forEach(citations::add);
    if (context != null) {
      context.validationIssues().stream()
          .limit(4)
          .forEach(issue -> citations.add(issue.constraint()));
    }
    return citations;
  }

  private void updateRollingSummary(String threadId) {
    List<AssistantMemoryRepository.MessageRecord> recent =
        memory.recentMessages(threadId, properties.hardening().recentMessageWindow());
    if (recent.isEmpty()) {
      return;
    }
    String summary =
        recent.stream()
            .sorted(
                java.util.Comparator.comparing(AssistantMemoryRepository.MessageRecord::createdAt))
            .map(message -> message.role() + ": " + message.content())
            .collect(Collectors.joining("\n"));
    if (summary.length() > 2000) {
      summary = summary.substring(summary.length() - 2000);
    }
    memory.updateSummary(threadId, summary, recent.get(0).id());
  }

  private String proposalSummary(AssistantProposal proposal, boolean bootstrap) {
    if (!proposal.validation().mandatoryPassed()) {
      return "I drafted a proposal, but backend structural or mandatory EVL validation blocked "
          + "it. No model changes were made. Review the validation issues below.";
    }
    if (bootstrap) {
      return "I drafted the first model and requested changes as a "
          + proposal.riskLevel().name().toLowerCase(java.util.Locale.ROOT)
          + "-risk proposal. Review it below, then approve or reject it.";
    }
    return "I drafted and validated a "
        + proposal.riskLevel().name().toLowerCase(java.util.Locale.ROOT)
        + "-risk proposal. "
        + (proposal.approvalRequired()
            ? "Review the preview below, then approve or reject it."
            : "It passed the guarded-apply checks and was applied.");
  }

  private AssistantWorkflowState workflowState(AssistantProposal proposal) {
    if (proposal == null) {
      return AssistantWorkflowState.EXPLAINED;
    }
    return proposal.approvalRequired()
        ? AssistantWorkflowState.PROPOSED
        : AssistantWorkflowState.APPLIED;
  }

  private String threadId(String userId, String projectId, ModelLevel level) {
    return userId + ":" + projectId + ":" + level.name();
  }

  private String disabledMessage(
      AssistantSessionStore.AssistantSession session, AssistantTurnRequest request) {
    String model =
        request.modelId() == null || request.modelId().isBlank()
            ? "the active " + session.level().apiName() + " model"
            : "model `" + request.modelId() + "`";
    return "AI is configured in "
        + properties.mode()
        + " mode but outbound provider calls are disabled. I received your request for "
        + model
        + " at revision "
        + (request.revision() == null ? "unknown" : request.revision())
        + "."
        + EXPLAIN_ONLY_SUFFIX;
  }

  private String nullToUnknown(Object value) {
    return value == null ? "unknown" : String.valueOf(value);
  }

  /**
   * One assistant turn request.
   *
   * @param message user message
   * @param modelId active model ID
   * @param revision active model revision
   * @param activeView active canvas view
   * @param selectedElementIds selected stable element IDs
   * @param unsavedDraftPatch optional compact unsaved patch
   */
  public record AssistantTurnRequest(
      String message,
      String modelId,
      Long revision,
      String activeView,
      List<String> selectedElementIds,
      String unsavedDraftPatch) {

    public AssistantTurnRequest {
      message = message == null ? "" : message;
      selectedElementIds = selectedElementIds == null ? List.of() : List.copyOf(selectedElementIds);
    }
  }

  /**
   * One assistant turn response.
   *
   * @param assistantMessage user-facing assistant message
   * @param modelId model ID associated with the response
   * @param revision model revision associated with the response
   * @param proposal optional proposal
   * @param choices optional choices
   * @param workflowState explicit workflow state
   */
  public record AssistantTurnResponse(
      String assistantMessage,
      String modelId,
      Long revision,
      AssistantProposal proposal,
      List<AssistantChoice> choices,
      AssistantWorkflowState workflowState) {

    public AssistantTurnResponse {
      choices = choices == null ? List.of() : List.copyOf(choices);
      workflowState = workflowState == null ? AssistantWorkflowState.EXPLAINED : workflowState;
    }
  }

  private record PreparedSemanticPatch(
      SemanticModelPatch patch, AssistantPatchCompiler.CompiledPatch compiled) {}
}
