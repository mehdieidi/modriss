package io.mehdieidi.modless.backend.assistant;

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

/**
 * Bounded assistant workflow entry point.
 */
@Service
public class AssistantOrchestrator {

    private static final String EXPLAIN_ONLY_SUFFIX = """
            
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
     * @param properties    AI settings
     * @param provider      assistant provider
     * @param sessions      session registry
     * @param memory        durable assistant history
     * @param chatMemory    Spring AI recent chat memory
     * @param catalogs      retrieval catalogs
     * @param modelContexts compact model context service
     * @param patchCompiler semantic patch compiler
     * @param realtime      realtime event hub
     * @param models        model service
     * @param projects      project service
     */
    public AssistantOrchestrator(AiProperties properties, AssistantModelProvider provider,
            AssistantSessionStore sessions, AssistantMemoryRepository memory,
            SpringAiChatMemoryService chatMemory, AssistantCatalogService catalogs,
            AssistantModelContextIndexService modelContexts,
            AssistantPatchCompiler patchCompiler, AssistantRealtimeHub realtime,
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
     * @param user      owner user
     * @param projectId project scope
     * @param level     modeling level
     * @param title     session title
     * @return created session
     */
    public AssistantSessionStore.AssistantSession startSession(UserRecord user, String projectId,
            ModelLevel level, String title) {
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
     * @param user      owner user
     * @param sessionId session ID
     * @param request   message request
     * @return assistant response
     */
    public AssistantTurnResponse handleMessage(UserRecord user, String sessionId,
            AssistantTurnRequest request) {
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
            memory.appendMessage(threadId, "ASSISTANT", assistantMessage,
                    Map.of("modelId", request.modelId() == null ? "" : request.modelId(),
                            "workflowState", AssistantWorkflowState.EXPLAINED.name()));
            chatMemory.appendAssistant(threadId, assistantMessage);
            AssistantTurnResponse response = new AssistantTurnResponse(assistantMessage,
                    request.modelId(), request.revision(), null, List.of(),
                    AssistantWorkflowState.EXPLAINED);
            realtime.publish(sessionId, "chat.assistant", response);
            return response;
        }

        ProjectRecord project = projects.get(user, session.projectId());
        String modelId = resolveModelId(request.modelId(), project, session.level());
        ModelRecord model = modelId == null ? null : models.get(user, session.level(), modelId);
        if (model != null && request.revision() != null
                && request.revision().longValue() != model.revision()) {
            throw new PlatformException(409,
                    "The active model changed from revision " + request.revision()
                            + " to " + model.revision() + ". Refresh before asking the assistant "
                            + "to propose a change.");
        }
        ModelService.ValidationResult validation = model == null ? null
                : models.validate(user, session.level(), model.id());
        AssistantModelContextIndexService.AssistantModelContext context = model == null ? null
                : modelContexts.snapshot(model, validation);

        List<AssistantModelProvider.ContextSnippet> snippets = retrievalSnippets(request,
                context, session.level());
        String assistantMessage = respondToPrompt(session, request, context, snippets);

        AssistantProposal proposal = maybeCreateProposal(user, session, model, context, validation,
                request, snippets);
        if (proposal != null) {
            assistantMessage = assistantMessage + "\n\n" + proposalSummary(proposal);
        }

        memory.appendMessage(threadId, "ASSISTANT", assistantMessage,
                Map.of("modelId", modelId == null ? "" : modelId,
                        "workflowState", workflowState(proposal).name()));
        chatMemory.appendAssistant(threadId, assistantMessage);
        updateRollingSummary(threadId);
        if (proposal != null) {
            memory.saveProposal(threadId, session.projectId(), modelId, model.revision(), proposal,
                    proposal.approvalRequired() ? "PROPOSED" : "APPLIED");
            memory.appendAudit(proposal.id(), session.projectId(), user.id(),
                    proposal.approvalRequired() ? "PROPOSED" : "APPLIED",
                    Map.of("proposalId", proposal.id(), "modelId", modelId));
        }

        Long nextRevision = proposal != null && !proposal.approvalRequired()
                ? model.revision() + 1
                : request.revision();
        AssistantTurnResponse response = new AssistantTurnResponse(assistantMessage, modelId,
                nextRevision, proposal,
                proposal == null ? List.of() : proposalChoices(proposal), workflowState(proposal));
        realtime.publish(sessionId, "chat.assistant", response);
        return response;
    }

    /**
     * Returns proposal details if stored.
     *
     * @param user       owner user
     * @param sessionId  session ID
     * @param proposalId proposal ID
     * @return proposal payload
     */
    public AssistantProposal proposal(UserRecord user, String sessionId, String proposalId) {
        AssistantSessionStore.AssistantSession session = requireSession(sessionId, user.id());
        return memory.findProposal(proposalId)
                .filter(record -> record.threadId().equals(threadId(user.id(),
                        session.projectId(), session.level())))
                .map(AssistantMemoryRepository.ProposalRecord::proposal)
                .orElseThrow(() -> new PlatformException(404, "Assistant proposal not found."));
    }

    /**
     * Approves and applies a stored proposal when possible.
     *
     * @param user       owner user
     * @param sessionId  session ID
     * @param proposalId proposal ID
     * @return updated assistant response
     */
    public AssistantTurnResponse approveProposal(UserRecord user, String sessionId,
            String proposalId) {
        AssistantSessionStore.AssistantSession session = requireSession(sessionId, user.id());
        AssistantMemoryRepository.ProposalRecord record = memory.findProposal(proposalId)
                .filter(found -> found.threadId().equals(threadId(user.id(),
                        session.projectId(), session.level())))
                .orElseThrow(() -> new PlatformException(404, "Assistant proposal not found."));
        if (!"PROPOSED".equals(record.status())) {
            throw new PlatformException(409,
                    "Assistant proposal is already " + record.status().toLowerCase() + ".");
        }
        AssistantTurnResponse response = applyStoredProposal(user, session, record);
        memory.appendAudit(proposalId, session.projectId(), user.id(), "APPROVED",
                Map.of("proposalId", proposalId));
        realtime.publish(sessionId, "chat.assistant", response);
        return response;
    }

    /**
     * Rejects a stored proposal.
     *
     * @param user       owner user
     * @param sessionId  session ID
     * @param proposalId proposal ID
     */
    public void rejectProposal(UserRecord user, String sessionId, String proposalId) {
        AssistantSessionStore.AssistantSession session = requireSession(sessionId, user.id());
        memory.findProposal(proposalId)
                .filter(found -> found.threadId().equals(threadId(user.id(),
                        session.projectId(), session.level())))
                .orElseThrow(() -> new PlatformException(404, "Assistant proposal not found."));
        memory.updateProposalStatus(proposalId, "REJECTED");
        memory.appendAudit(proposalId, session.projectId(), user.id(), "REJECTED",
                Map.of("proposalId", proposalId, "workflowState",
                        AssistantWorkflowState.REJECTED.name()));
        realtime.publish(sessionId, "proposal.rejected", Map.of("proposalId", proposalId));
    }

    /**
     * Applies the stored inverse patch for an already-applied proposal.
     *
     * @param user       owner user
     * @param sessionId  session ID
     * @param proposalId proposal ID
     * @return updated assistant response
     */
    public AssistantTurnResponse undoProposal(UserRecord user, String sessionId,
            String proposalId) {
        AssistantSessionStore.AssistantSession session = requireSession(sessionId, user.id());
        AssistantMemoryRepository.ProposalRecord record = memory.findProposal(proposalId)
                .filter(found -> found.threadId().equals(threadId(user.id(),
                        session.projectId(), session.level())))
                .orElseThrow(() -> new PlatformException(404, "Assistant proposal not found."));
        if (!"APPLIED".equals(record.status())) {
            throw new PlatformException(409,
                    "Only applied assistant proposals can be undone.");
        }
        if (record.proposal().inversePatch().isEmpty()) {
            throw new PlatformException(409, "Assistant proposal has no inverse patch.");
        }
        ModelRecord model = models.get(user, session.level(), record.modelId());
        AssistantPatchCompiler.CompiledPatch inverse =
                new AssistantPatchCompiler.CompiledPatch(record.proposal().inversePatch(),
                        List.of(), record.proposal().affectedElements());
        var preview = patchCompiler.apply(model.modelJson(), inverse);
        AssistantValidationSummary validation = validationSummary(
                models.validate(session.level(), preview));
        if (!validation.structurallyValid() || !validation.mandatoryPassed()) {
            memory.appendAudit(record.id(), session.projectId(), user.id(), "UNDO_FAILED",
                    Map.of("reason", "Inverse patch failed mandatory validation."));
            throw new PlatformException(422,
                    "Proposal cannot be undone because mandatory validation failed.");
        }
        ModelRecord updated = models.patch(user, session.level(), model.id(), model.name(),
                record.proposal().inversePatch(), model.revision());
        memory.updateProposalStatus(record.id(), "UNDONE");
        memory.appendAudit(record.id(), session.projectId(), user.id(), "UNDONE",
                Map.of("modelId", updated.id(), "revision", updated.revision()));
        realtime.publish(sessionId, "model.updated",
                Map.of("modelId", updated.id(), "revision", updated.revision(),
                        "proposalId", record.id(), "undo", true));
        return new AssistantTurnResponse("Proposal undone.", updated.id(), updated.revision(),
                record.proposal(), List.of(), AssistantWorkflowState.UNDONE);
    }

    /**
     * Records a bounded user choice from the assistant UI.
     *
     * @param user      owner user
     * @param sessionId session ID
     * @param choiceId  choice ID
     * @param optionId  chosen option ID
     */
    public void submitChoice(UserRecord user, String sessionId, String choiceId, String optionId) {
        AssistantSessionStore.AssistantSession session = requireSession(sessionId, user.id());
        memory.appendAudit(null, session.projectId(), user.id(), "CHOICE",
                Map.of("choiceId", choiceId, "optionId", optionId));
        realtime.publish(sessionId, "assistant.choice",
                Map.of("choiceId", choiceId, "optionId", optionId));
    }

    /**
     * Clears runtime and durable memory for a session.
     *
     * @param user      owner user
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
            return memory.findThread(sessionId, userId)
                    .map(sessions::fromThread)
                    .orElseThrow(() -> ex);
        }
    }

    private String respondToPrompt(AssistantSessionStore.AssistantSession session,
            AssistantTurnRequest request,
            AssistantModelContextIndexService.AssistantModelContext context,
            List<AssistantModelProvider.ContextSnippet> snippets) {
        if (!properties.enabled() || !provider.available()) {
            return disabledMessage(session, request);
        }
        AssistantModelProvider.AssistantReply reply = provider.complete(
                new AssistantModelProvider.AssistantPrompt(AssistantModelRole.RESPONDER,
                        systemPrompt(session, request, context),
                        request.message(), snippets));
        return reply.content() == null || reply.content().isBlank()
                ? fallbackResponse(session, request, context)
                : reply.content();
    }

    private String fallbackResponse(AssistantSessionStore.AssistantSession session,
            AssistantTurnRequest request,
            AssistantModelContextIndexService.AssistantModelContext context) {
        if (context == null) {
            return "I could not load the active model context yet.";
        }
        return "I found " + context.elements().size() + " elements and "
                + context.relationships().size() + " relationships for "
                + session.level().name() + ".";
    }

    private String systemPrompt(AssistantSessionStore.AssistantSession session,
            AssistantTurnRequest request,
            AssistantModelContextIndexService.AssistantModelContext context) {
        String modelSummary =
                context == null ? "No active model." : modelContexts.summarize(context);
        List<AssistantModelProvider.ContextSnippet> snippets = catalogs.search(request.message(),
                session.level().name(), 6);
        String snippetSummary = snippets.stream().map(snippet -> snippet.source() + ": "
                + snippet.title() + "\n" + snippet.content()).reduce("", (left, right) ->
                left.isBlank() ? right : left + "\n\n" + right);
        return "Project: " + session.projectId()
                + "\nLevel: " + session.level()
                + "\nModel ID: " + nullToUnknown(request.modelId())
                + "\nRevision: " + nullToUnknown(request.revision())
                + "\nActive view: " + nullToUnknown(request.activeView())
                + "\nSelected element IDs: " + request.selectedElementIds()
                + "\nRollout mode: " + properties.mode()
                + "\n\nConversation memory:\n" + conversationMemory(session)
                + "\n\nModel context:\n" + modelSummary
                + "\n\nRetrieval snippets:\n" + snippetSummary;
    }

    private String conversationMemory(AssistantSessionStore.AssistantSession session) {
        String threadId = threadId(session.userId(), session.projectId(), session.level());
        String summary = memory.summary(threadId).orElse("");
        String recent = chatMemory.recent(threadId, properties.hardening().recentMessageWindow())
                .stream()
                .map(message -> message.role() + ": " + message.content())
                .collect(Collectors.joining("\n"));
        if (summary.isBlank()) {
            return recent;
        }
        return "Summary: " + summary + "\nRecent:\n" + recent;
    }

    private String resolveModelId(String requestedModelId, ProjectRecord project,
            ModelLevel level) {
        if (requestedModelId != null && !requestedModelId.isBlank()) {
            return requestedModelId.trim();
        }
        return project.activeModelIds().get(level.apiName());
    }

    private List<AssistantModelProvider.ContextSnippet> retrievalSnippets(
            AssistantTurnRequest request,
            AssistantModelContextIndexService.AssistantModelContext context, ModelLevel level) {
        List<AssistantModelProvider.ContextSnippet> snippets = new ArrayList<>(
                catalogs.search(request.message(), level.name(), 6));
        if (context != null) {
            context.validationIssues().stream().limit(4).forEach(issue -> snippets.add(
                    new AssistantModelProvider.ContextSnippet("validation",
                            issue.constraint(), issue.message())));
        }
        return snippets.stream().limit(properties.tokenBudget() > 0 ? 6 : 3).toList();
    }

    private AssistantProposal maybeCreateProposal(UserRecord user,
            AssistantSessionStore.AssistantSession session, ModelRecord model,
            AssistantModelContextIndexService.AssistantModelContext context,
            ModelService.ValidationResult validation, AssistantTurnRequest request,
            List<AssistantModelProvider.ContextSnippet> snippets) {
        if (model == null) {
            return null;
        }
        if (properties.mode() == AiProperties.RolloutMode.EXPLAIN_ONLY || !provider.available()) {
            return null;
        }
        SemanticModelPatch patch = provider.proposePatch(new AssistantModelProvider.AssistantPrompt(
                AssistantModelRole.PLANNER, plannerPrompt(session, request, context),
                request.message(), snippets));
        validateSemanticPatch(patch, context);
        if (patch.operations().isEmpty()) {
            return null;
        }
        AssistantPatchCompiler.CompiledPatch compiled = patchCompiler.compile(model.modelJson(),
                patch);
        var preview = patchCompiler.apply(model.modelJson(), compiled);
        ModelService.ValidationResult previewValidation = models.validate(session.level(), preview);
        AssistantValidationSummary summary = validationSummary(previewValidation);
        AssistantProposal.RiskLevel riskLevel = riskLevel(compiled, summary);
        boolean approvalRequired = riskLevel != AssistantProposal.RiskLevel.LOW
                || !summary.mandatoryPassed()
                || properties.mode() != AiProperties.RolloutMode.GUARDED_APPLY;
        AssistantProposal proposal = new AssistantProposal(
                java.util.UUID.randomUUID().toString(),
                compiled.affectedElements(), patch, compiled.inversePatch(),
                summary, riskLevel, approvalRequired, retrievalCitations(snippets, context),
                Instant.now());
        if (!approvalRequired) {
            applyProposal(user, session, model, proposal, compiled);
        }
        return proposal;
    }

    private void applyProposal(UserRecord user, AssistantSessionStore.AssistantSession session,
            ModelRecord model, AssistantProposal proposal,
            AssistantPatchCompiler.CompiledPatch compiled) {
        try {
            ModelRecord updated = models.patch(user, session.level(), model.id(), model.name(),
                    compiled.patch(), model.revision());
            memory.appendAudit(proposal.id(), session.projectId(), user.id(), "APPLIED",
                    Map.of("modelId", updated.id(), "revision", updated.revision()));
            realtime.publish(session.id(), "model.updated",
                    Map.of("modelId", updated.id(), "revision",
                            updated.revision(), "proposalId", proposal.id()));
        } catch (Exception ex) {
            memory.appendAudit(proposal.id(), session.projectId(), user.id(), "FAILED",
                    Map.of("error", ex.getMessage()));
            throw ex;
        }
    }

    private AssistantTurnResponse applyStoredProposal(UserRecord user,
            AssistantSessionStore.AssistantSession session,
            AssistantMemoryRepository.ProposalRecord record) {
        ModelRecord model = models.get(user, session.level(), record.modelId());
        AssistantPatchCompiler.CompiledPatch compiled = patchCompiler.compile(model.modelJson(),
                record.proposal().patch());
        var preview = patchCompiler.apply(model.modelJson(), compiled);
        AssistantValidationSummary validation = validationSummary(
                models.validate(session.level(), preview));
        if (!validation.structurallyValid() || !validation.mandatoryPassed()) {
            memory.updateProposalStatus(record.id(), "FAILED");
            memory.appendAudit(record.id(), session.projectId(), user.id(), "FAILED",
                    Map.of("reason", "Proposal no longer passes mandatory validation."));
            throw new PlatformException(422,
                    "Proposal cannot be applied because mandatory validation failed.");
        }
        ModelRecord updated = models.patch(user, session.level(), model.id(), model.name(),
                compiled.patch(), record.modelRevision());
        memory.updateProposalStatus(record.id(), "APPLIED");
        memory.appendAudit(record.id(), session.projectId(), user.id(), "APPLIED",
                Map.of("modelId", updated.id(), "revision", updated.revision()));
        realtime.publish(session.id(), "model.updated",
                Map.of("modelId", updated.id(), "revision",
                        updated.revision(), "proposalId", record.id()));
        return new AssistantTurnResponse("Proposal applied.", updated.id(), updated.revision(),
                record.proposal(), proposalChoices(record.proposal()), AssistantWorkflowState.APPLIED);
    }

    private AssistantValidationSummary validationSummary(
            ModelService.ValidationResult result) {
        List<AssistantValidationSummary.Issue> issues = result == null ? List.of()
                : result.issues().stream().map(issue -> new AssistantValidationSummary.Issue(
                        issue.severity(), issue.constraint(), issue.elementId(),
                        issue.message())).toList();
        long optional = issues.stream()
                .filter(issue -> "WARNING".equalsIgnoreCase(issue.severity())).count();
        boolean mandatoryPassed = issues.stream()
                .noneMatch(issue -> "ERROR".equalsIgnoreCase(issue.severity()));
        return new AssistantValidationSummary(result == null || result.valid(), mandatoryPassed,
                (int) optional, issues);
    }

    private AssistantProposal.RiskLevel riskLevel(AssistantPatchCompiler.CompiledPatch compiled,
            AssistantValidationSummary summary) {
        boolean destructive = compiled.patch().stream().anyMatch(operation ->
                "remove".equalsIgnoreCase(operation.op()));
        if (destructive || !summary.mandatoryPassed()) {
            return AssistantProposal.RiskLevel.HIGH;
        }
        if (compiled.patch().size() > 1 || summary.optionalIssues() > 0) {
            return AssistantProposal.RiskLevel.MEDIUM;
        }
        return AssistantProposal.RiskLevel.LOW;
    }

    private String plannerPrompt(AssistantSessionStore.AssistantSession session,
            AssistantTurnRequest request,
            AssistantModelContextIndexService.AssistantModelContext context) {
        return systemPrompt(session, request, context)
                + "\n\nOnly propose operations grounded in these selected IDs: "
                + request.selectedElementIds()
                + "\nUse at most " + properties.maxToolCalls()
                + " semantic operations. Return no operations when user intent is ambiguous.";
    }

    private void validateSemanticPatch(SemanticModelPatch patch,
            AssistantModelContextIndexService.AssistantModelContext context) {
        if (patch.operations().size() > properties.maxToolCalls()) {
            throw new PlatformException(422, "Assistant proposal exceeded the operation limit.");
        }
        Set<String> existingIds = context.elements().stream()
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
                        throw new PlatformException(422,
                                "Assistant proposed an invalid new element ID.");
                    }
                }
                case CONNECT_ELEMENTS -> {
                    if (!existingIds.contains(operation.sourceElementId())
                            || !existingIds.contains(operation.targetElementId())
                            || operation.referenceName() == null
                            || operation.referenceName().isBlank()) {
                        throw new PlatformException(422,
                                "Assistant proposed an ungrounded relationship.");
                    }
                }
                case SET_ATTRIBUTE, DELETE_ELEMENT -> {
                    if (!existingIds.contains(operation.targetElementId())) {
                        throw new PlatformException(422,
                                "Assistant proposed an unknown target element.");
                    }
                    if (operation.type() == SemanticModelPatch.OperationType.SET_ATTRIBUTE
                            && (operation.referenceName() == null
                            || operation.referenceName().isBlank())) {
                        throw new PlatformException(422,
                                "Assistant proposed an attribute update without an attribute.");
                    }
                }
                default -> throw new PlatformException(422,
                        "Assistant proposed an unsupported semantic operation.");
            }
        }
    }

    private List<AssistantChoice> proposalChoices(AssistantProposal proposal) {
        if (proposal == null || !proposal.approvalRequired()) {
            return List.of();
        }
        return List.of(new AssistantChoice("proposal-decision", "Choose how to proceed",
                List.of(new AssistantChoice.Option("approve", "Approve",
                                "Apply the proposal after validation."),
                        new AssistantChoice.Option("reject", "Reject",
                                "Keep the model unchanged."))));
    }

    private List<String> retrievalCitations(List<AssistantModelProvider.ContextSnippet> snippets,
            AssistantModelContextIndexService.AssistantModelContext context) {
        List<String> citations = new ArrayList<>();
        snippets.stream().map(snippet -> snippet.source() + "#" + snippet.title())
                .filter(value -> !value.isBlank())
                .distinct()
                .limit(8)
                .forEach(citations::add);
        if (context != null) {
            context.validationIssues().stream().limit(4).forEach(issue ->
                    citations.add(issue.constraint()));
        }
        return citations;
    }

    private void updateRollingSummary(String threadId) {
        List<AssistantMemoryRepository.MessageRecord> recent = memory.recentMessages(threadId,
                properties.hardening().recentMessageWindow());
        if (recent.isEmpty()) {
            return;
        }
        String summary = recent.stream()
                .sorted(java.util.Comparator.comparing(
                        AssistantMemoryRepository.MessageRecord::createdAt))
                .map(message -> message.role() + ": " + message.content())
                .collect(Collectors.joining("\n"));
        if (summary.length() > 2000) {
            summary = summary.substring(summary.length() - 2000);
        }
        memory.updateSummary(threadId, summary, recent.get(0).id());
    }

    private String proposalSummary(AssistantProposal proposal) {
        return "Proposal: " + proposal.riskLevel() + ", "
                + (proposal.approvalRequired() ? "approval required" : "auto-apply");
    }

    private AssistantWorkflowState workflowState(AssistantProposal proposal) {
        if (proposal == null) {
            return AssistantWorkflowState.EXPLAINED;
        }
        return proposal.approvalRequired() ? AssistantWorkflowState.PROPOSED
                : AssistantWorkflowState.APPLIED;
    }

    private String threadId(String userId, String projectId, ModelLevel level) {
        return userId + ":" + projectId + ":" + level.name();
    }

    private String disabledMessage(AssistantSessionStore.AssistantSession session,
            AssistantTurnRequest request) {
        String model = request.modelId() == null || request.modelId().isBlank()
                ? "the active " + session.level().apiName() + " model"
                : "model `" + request.modelId() + "`";
        return "AI is configured in " + properties.mode()
                + " mode but outbound provider calls are disabled. I received your request for "
                + model + " at revision " + (request.revision() == null ? "unknown"
                : request.revision()) + "." + EXPLAIN_ONLY_SUFFIX;
    }

    private String nullToUnknown(Object value) {
        return value == null ? "unknown" : String.valueOf(value);
    }

    /**
     * One assistant turn request.
     *
     * @param message            user message
     * @param modelId            active model ID
     * @param revision           active model revision
     * @param activeView         active canvas view
     * @param selectedElementIds selected stable element IDs
     * @param unsavedDraftPatch  optional compact unsaved patch
     */
    public record AssistantTurnRequest(String message, String modelId, Long revision,
                                       String activeView, List<String> selectedElementIds,
                                       String unsavedDraftPatch) {

        public AssistantTurnRequest {
            message = message == null ? "" : message;
            selectedElementIds = selectedElementIds == null ? List.of()
                    : List.copyOf(selectedElementIds);
        }
    }

    /**
     * One assistant turn response.
     *
     * @param assistantMessage user-facing assistant message
     * @param modelId          model ID associated with the response
     * @param revision         model revision associated with the response
     * @param proposal         optional proposal
     * @param choices          optional choices
     * @param workflowState    explicit workflow state
     */
    public record AssistantTurnResponse(String assistantMessage, String modelId, Long revision,
                                        AssistantProposal proposal, List<AssistantChoice> choices,
                                        AssistantWorkflowState workflowState) {

        public AssistantTurnResponse {
            choices = choices == null ? List.of() : List.copyOf(choices);
            workflowState = workflowState == null ? AssistantWorkflowState.EXPLAINED
                    : workflowState;
        }
    }
}
