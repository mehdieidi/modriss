package io.mehdieidi.modless.platform.assistant.application;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.mehdieidi.modless.platform.assistant.domain.AssistantProposal;
import io.mehdieidi.modless.platform.assistant.domain.AssistantWorkflowState;
import io.mehdieidi.modless.platform.assistant.domain.memory.AssistantMemoryRecords.ConversationSummary;
import io.mehdieidi.modless.platform.assistant.domain.memory.AssistantMemoryRecords.MessageRecord;
import io.mehdieidi.modless.platform.assistant.domain.memory.AssistantMemoryRecords.ProposalRecord;
import io.mehdieidi.modless.platform.assistant.domain.memory.AssistantMemoryRecords.ThreadRecord;
import io.mehdieidi.modless.platform.assistant.patch.AssistantPatchCompiler;
import io.mehdieidi.modless.platform.assistant.provider.AssistantModelProvider;
import io.mehdieidi.modless.platform.assistant.session.AssistantSessionStore;
import io.mehdieidi.modless.platform.assistant.spi.AssistantChatMemory;
import io.mehdieidi.modless.platform.assistant.spi.AssistantMemoryStore;
import io.mehdieidi.modless.platform.assistant.spi.AssistantRealtimePublisher;
import io.mehdieidi.modless.platform.identity.domain.UserRecord;
import io.mehdieidi.modless.platform.kernel.ModelLevel;
import io.mehdieidi.modless.platform.kernel.PlatformException;
import io.mehdieidi.modless.platform.model.application.ModelService;
import io.mehdieidi.modless.platform.model.domain.ModelRecord;
import io.mehdieidi.modless.platform.project.application.ProjectService;
import io.mehdieidi.modless.platform.project.domain.ProjectRecord;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

/** Session, history, proposal, and undo facade for the agentic runtime. */
public final class AgenticAssistantFacade {
  private final AssistantSessionStore sessions;
  private final AssistantMemoryStore memory;
  private final AssistantChatMemory chatMemory;
  private final ProjectService projects;
  private final ModelService models;
  private final AssistantPatchCompiler patches;
  private final AssistantRealtimePublisher realtime;
  private final AssistantModelProvider provider;
  private final AgenticTurnService turns;

  public AgenticAssistantFacade(
      AssistantSessionStore sessions,
      AssistantMemoryStore memory,
      AssistantChatMemory chatMemory,
      ProjectService projects,
      ModelService models,
      AssistantPatchCompiler patches,
      AssistantRealtimePublisher realtime,
      AssistantModelProvider provider,
      AgenticTurnService turns) {
    this.sessions = sessions;
    this.memory = memory;
    this.chatMemory = chatMemory;
    this.projects = projects;
    this.models = models;
    this.patches = patches;
    this.realtime = realtime;
    this.provider = provider;
    this.turns = turns;
  }

  public AssistantSessionStore.AssistantSession startSession(
      UserRecord user,
      String projectId,
      ModelLevel level,
      String title,
      String resumeId,
      boolean forceNew) {
    projects.get(user, projectId);
    String display =
        title == null || title.isBlank() ? level.apiName() + "-assistant" : title.trim();
    if (resumeId != null && !resumeId.isBlank()) {
      ThreadRecord thread =
          memory
              .findThread(resumeId.trim(), user.id())
              .orElseThrow(() -> new PlatformException(404, "Assistant conversation not found."));
      if (!thread.projectId().equals(projectId) || thread.level() != level)
        throw new PlatformException(400, "Conversation does not match this project or level.");
      return sessions.create(user.id(), projectId, level, thread.title(), thread.id());
    }
    if (!forceNew) {
      var recent =
          memory.findMostRecentThread(
              user.id(), projectId, level, Instant.now().minus(3, ChronoUnit.DAYS));
      if (recent.isPresent())
        return sessions.create(
            user.id(), projectId, level, recent.get().title(), recent.get().id());
    }
    String id = memory.newThreadId(user.id(), projectId, level);
    memory.createThread(id, user, projectId, level, display, null, null);
    return sessions.create(user.id(), projectId, level, display, id);
  }

  public AssistantSessionStore.AssistantSession session(UserRecord user, String id) {
    try {
      return sessions.require(id, user.id());
    } catch (PlatformException missing) {
      return memory.findThread(id, user.id()).map(sessions::fromThread).orElseThrow(() -> missing);
    }
  }

  public List<ConversationSummary> conversations(
      UserRecord user, String projectId, ModelLevel level, int days, int limit) {
    projects.get(user, projectId);
    return memory.listRecentConversations(
        user.id(),
        projectId,
        level,
        Instant.now().minus(Math.max(1, Math.min(days, 30)), ChronoUnit.DAYS),
        Math.max(1, Math.min(limit, 50)));
  }

  public AgenticTurnService.Result message(
      UserRecord user,
      String sessionId,
      String modelId,
      Long revision,
      String message,
      String source) {
    var session = session(user, sessionId);
    String resolved = modelId;
    if (resolved == null || resolved.isBlank())
      resolved = memory.requireThread(sessionId).activeModelId();
    if (resolved == null || resolved.isBlank()) {
      ProjectRecord project = projects.get(user, session.projectId());
      Map<String, String> active = project.activeModelIds();
      if (active != null) {
        resolved = active.get(session.level().apiName());
        if (resolved == null || resolved.isBlank()) resolved = active.get(session.level().name());
      }
    }
    if (resolved == null || resolved.isBlank()) {
      ModelRecord created =
          models.create(
              user, session.level(), session.projectId(), session.title(), emptyModel(session));
      resolved = created.id();
      memory.updateThreadModel(sessionId, created.id(), created.revision());
    }
    return turns.run(user, sessionId, session.level(), resolved, revision, message, source);
  }

  public ThreadSnapshot thread(UserRecord user, String sessionId) {
    session(user, sessionId);
    List<ThreadMessage> messages =
        memory.recentMessages(sessionId, 100).stream()
            .sorted(java.util.Comparator.comparing(MessageRecord::createdAt))
            .map(item -> new ThreadMessage(item.role(), item.content()))
            .toList();
    AssistantProposal proposal =
        memory.findLatestProposal(sessionId, "APPLIED").map(ProposalRecord::proposal).orElse(null);
    return new ThreadSnapshot(
        messages,
        messages.isEmpty() ? AssistantWorkflowState.EXPLAINED : AssistantWorkflowState.APPLIED,
        proposal,
        provider.metadata());
  }

  public AssistantProposal proposal(UserRecord user, String sessionId, String proposalId) {
    session(user, sessionId);
    ProposalRecord record =
        memory
            .findProposal(proposalId)
            .orElseThrow(() -> new PlatformException(404, "Assistant proposal not found."));
    if (!record.threadId().equals(sessionId))
      throw new PlatformException(404, "Assistant proposal not found.");
    return record.proposal();
  }

  public UndoResult undo(UserRecord user, String sessionId, String proposalId) {
    var session = session(user, sessionId);
    ProposalRecord record =
        memory
            .findProposal(proposalId)
            .orElseThrow(() -> new PlatformException(404, "Assistant proposal not found."));
    if (!record.threadId().equals(sessionId)
        || !"APPLIED".equals(record.status())
        || record.proposal().inversePatch().isEmpty())
      throw new PlatformException(409, "Only an applied proposal with an inverse can be undone.");
    ModelRecord model = models.get(user, session.level(), record.modelId());
    var compiled =
        patches.adaptToSnapshot(
            model.modelJson(),
            new AssistantPatchCompiler.CompiledPatch(
                record.proposal().inversePatch(), List.of(), record.proposal().affectedElements()));
    var preview = patches.apply(model.modelJson(), compiled);
    var validation = models.validateStructural(session.level(), preview);
    if (!validation.valid())
      throw new PlatformException(422, "Undo would make the model structurally invalid.");
    ModelRecord updated =
        models.patch(
            user, session.level(), model.id(), model.name(), compiled.patch(), model.revision());
    memory.updateProposalStatus(proposalId, "UNDONE");
    memory.updateThreadModel(sessionId, updated.id(), updated.revision());
    memory.appendAudit(
        proposalId,
        session.projectId(),
        user.id(),
        "UNDONE",
        Map.of("modelId", updated.id(), "revision", updated.revision()));
    realtime.publish(
        sessionId,
        "model.updated",
        Map.of(
            "modelId", updated.id(), "revision", updated.revision(), "model", updated.modelJson()));
    return new UndoResult(
        "The proposal was undone.", updated.id(), updated.revision(), record.proposal());
  }

  public void clear(UserRecord user, String sessionId) {
    session(user, sessionId);
    memory.clearThread(sessionId);
    chatMemory.clear(sessionId);
    sessions.clear(sessionId, user.id());
  }

  public boolean cancel(UserRecord user, String sessionId) {
    session(user, sessionId);
    return turns.cancel(sessionId);
  }

  private ObjectNode emptyModel(AssistantSessionStore.AssistantSession session) {
    ObjectNode model = JsonNodeFactory.instance.objectNode();
    model.put("id", "assistant-root");
    model.put("modelLevel", session.level().name());
    model.put(
        "eClass",
        switch (session.level()) {
          case CIM -> "CIMModel";
          case PIM -> "PIMModel";
          case PSM -> "AwsPsmModel";
        });
    model.put("name", session.title());
    ObjectNode diagram = model.putObject("diagram");
    diagram.putArray("elements");
    diagram.putArray("relationships");
    return model;
  }

  public record ThreadMessage(String role, String content) {}

  public record ThreadSnapshot(
      List<ThreadMessage> messages,
      AssistantWorkflowState workflowState,
      AssistantProposal proposal,
      AssistantModelProvider.AssistantProviderMetadata provider) {}

  public record UndoResult(
      String message, String modelId, long revision, AssistantProposal proposal) {}
}
