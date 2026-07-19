package io.mehdieidi.varka.platform.assistant.application;

import io.mehdieidi.varka.platform.assistant.domain.AssistantProposal;
import io.mehdieidi.varka.platform.assistant.domain.AssistantWorkflowState;
import io.mehdieidi.varka.platform.assistant.domain.memory.AssistantMemoryRecords.ConversationSummary;
import io.mehdieidi.varka.platform.assistant.domain.memory.AssistantMemoryRecords.MessageRecord;
import io.mehdieidi.varka.platform.assistant.domain.memory.AssistantMemoryRecords.ThreadRecord;
import io.mehdieidi.varka.platform.assistant.patch.AssistantPatchCompiler;
import io.mehdieidi.varka.platform.assistant.provider.AssistantModelProvider;
import io.mehdieidi.varka.platform.assistant.session.AssistantSessionStore;
import io.mehdieidi.varka.platform.assistant.spi.AssistantChatMemory;
import io.mehdieidi.varka.platform.assistant.spi.AssistantMemoryStore;
import io.mehdieidi.varka.platform.assistant.spi.AssistantRealtimePublisher;
import io.mehdieidi.varka.platform.identity.domain.UserRecord;
import io.mehdieidi.varka.platform.kernel.ModelLevel;
import io.mehdieidi.varka.platform.kernel.PlatformException;
import io.mehdieidi.varka.platform.model.application.ModelService;
import io.mehdieidi.varka.platform.model.domain.ModelRecord;
import io.mehdieidi.varka.platform.modeling.config.ModelingConfigService;
import io.mehdieidi.varka.platform.project.application.ProjectService;
import io.mehdieidi.varka.platform.project.domain.ProjectRecord;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import tools.jackson.databind.node.ObjectNode;

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
  private final ModelingConfigService modelingConfig;

  public AgenticAssistantFacade(
      AssistantSessionStore sessions,
      AssistantMemoryStore memory,
      AssistantChatMemory chatMemory,
      ProjectService projects,
      ModelService models,
      AssistantPatchCompiler patches,
      AssistantRealtimePublisher realtime,
      AssistantModelProvider provider,
      AgenticTurnService turns,
      ModelingConfigService modelingConfig) {
    this.sessions = sessions;
    this.memory = memory;
    this.chatMemory = chatMemory;
    this.projects = projects;
    this.models = models;
    this.patches = patches;
    this.realtime = realtime;
    this.provider = provider;
    this.turns = turns;
    this.modelingConfig = modelingConfig;
  }

  public AssistantSessionStore.AssistantSession startSession(
      UserRecord user,
      String projectId,
      ModelLevel level,
      String title,
      String resumeId,
      boolean forceNew) {
    requireSupportedLevel(level);
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
    requireSupportedLevel(level);
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
    return message(user, sessionId, modelId, revision, message, source, false);
  }

  /** Executes a confirmed durable deletion turn; callers must not derive this from user text. */
  public AgenticTurnService.Result message(
      UserRecord user,
      String sessionId,
      String modelId,
      Long revision,
      String message,
      String source,
      boolean destructiveConfirmed) {
    return message(
        user, sessionId, modelId, revision, message, source, destructiveConfirmed, () -> false);
  }

  /** Durable worker variant with persisted cancellation checked before model persistence. */
  public AgenticTurnService.Result message(
      UserRecord user,
      String sessionId,
      String modelId,
      Long revision,
      String message,
      String source,
      boolean destructiveConfirmed,
      java.util.function.BooleanSupplier cancellationRequested) {
    return message(
        user,
        sessionId,
        modelId,
        revision,
        message,
        source,
        destructiveConfirmed,
        cancellationRequested,
        () -> null);
  }

  /** Durable worker variant which can reject a late provider response before persistence. */
  public AgenticTurnService.Result message(
      UserRecord user,
      String sessionId,
      String modelId,
      Long revision,
      String message,
      String source,
      boolean destructiveConfirmed,
      java.util.function.BooleanSupplier cancellationRequested,
      java.util.function.Supplier<io.mehdieidi.varka.platform.kernel.PlatformException>
          stopReason) {
    var session = session(user, sessionId);
    requireSupportedLevel(session.level());
    ModelRecord model = ensureModel(user, sessionId, modelId);
    return turns.run(
        user,
        sessionId,
        session.level(),
        model.id(),
        revision,
        message,
        source,
        destructiveConfirmed,
        cancellationRequested,
        stopReason);
  }

  /** Ensures a persisted, structurally valid starter model before any provider work starts. */
  public ModelRecord ensureModel(UserRecord user, String sessionId, String modelId) {
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
    return models.get(user, session.level(), resolved);
  }

  public ThreadSnapshot thread(UserRecord user, String sessionId) {
    session(user, sessionId);
    List<ThreadMessage> messages =
        memory.recentMessages(sessionId, 100).stream()
            .sorted(java.util.Comparator.comparing(MessageRecord::createdAt))
            .map(item -> new ThreadMessage(item.role(), item.content()))
            .toList();
    return new ThreadSnapshot(
        messages,
        messages.isEmpty() ? AssistantWorkflowState.EXPLAINED : AssistantWorkflowState.APPLIED,
        provider.metadata());
  }

  public AssistantProposal proposal(UserRecord user, String sessionId, String proposalId) {
    throw new PlatformException(
        410, "Assistant proposals were replaced by durable turns and checkpoints.");
  }

  public UndoResult undo(UserRecord user, String sessionId, String proposalId) {
    throw new PlatformException(410, "Use the durable turn checkpoint undo endpoint instead.");
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
    return modelingConfig.starterModel(session.level(), session.title());
  }

  /** AI modeling is intentionally limited to the conceptual and platform-independent levels. */
  private void requireSupportedLevel(ModelLevel level) {
    if (level != ModelLevel.CIM && level != ModelLevel.PIM) {
      throw new PlatformException(422, "AI modeling is available only for CIM and PIM levels.");
    }
  }

  public record ThreadMessage(String role, String content) {}

  public record ThreadSnapshot(
      List<ThreadMessage> messages,
      AssistantWorkflowState workflowState,
      AssistantModelProvider.AssistantProviderMetadata provider) {}

  public record UndoResult(
      String message, String modelId, long revision, AssistantProposal proposal) {}
}
