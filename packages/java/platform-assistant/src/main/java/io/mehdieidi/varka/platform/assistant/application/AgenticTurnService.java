package io.mehdieidi.varka.platform.assistant.application;

import io.mehdieidi.varka.platform.assistant.agent.AgentTurnLoop;
import io.mehdieidi.varka.platform.assistant.patch.AssistantPatchCompiler;
import io.mehdieidi.varka.platform.assistant.source.SourceDocumentWorkers;
import io.mehdieidi.varka.platform.assistant.spi.AssistantMemoryStore;
import io.mehdieidi.varka.platform.assistant.workspace.ModelWorkspace;
import io.mehdieidi.varka.platform.identity.domain.UserRecord;
import io.mehdieidi.varka.platform.kernel.ModelLevel;
import io.mehdieidi.varka.platform.model.application.ModelService;
import io.mehdieidi.varka.platform.model.domain.ModelRecord;
import java.util.List;
import java.util.Map;

/** Loads, runs, validates, and atomically commits one agentic workspace turn. */
public final class AgenticTurnService {

  private final ModelService models;
  private final AssistantPatchCompiler patches;
  private final AgentTurnLoop loop;
  private final SourceDocumentWorkers sourceWorkers;
  private final AssistantMemoryStore memory;

  public AgenticTurnService(
      ModelService models,
      AssistantPatchCompiler patches,
      AgentTurnLoop loop,
      SourceDocumentWorkers sourceWorkers,
      AssistantMemoryStore memory) {
    this.models = models;
    this.patches = patches;
    this.loop = loop;
    this.sourceWorkers = sourceWorkers;
    this.memory = memory;
  }

  public Result run(
      UserRecord user,
      String sessionId,
      ModelLevel level,
      String modelId,
      Long revision,
      String message,
      String sourceDocument) {
    return run(user, sessionId, level, modelId, revision, message, sourceDocument, false);
  }

  /** The confirmation flag is server-controlled by the durable turn endpoint. */
  public Result run(
      UserRecord user,
      String sessionId,
      ModelLevel level,
      String modelId,
      Long revision,
      String message,
      String sourceDocument,
      boolean destructiveConfirmed) {
    return run(
        user,
        sessionId,
        level,
        modelId,
        revision,
        message,
        sourceDocument,
        destructiveConfirmed,
        () -> false);
  }

  /** Runs a durable turn while checking a persisted cancellation flag before model persistence. */
  public Result run(
      UserRecord user,
      String sessionId,
      ModelLevel level,
      String modelId,
      Long revision,
      String message,
      String sourceDocument,
      boolean destructiveConfirmed,
      java.util.function.BooleanSupplier cancellationRequested) {
    return run(
        user,
        sessionId,
        level,
        modelId,
        revision,
        message,
        sourceDocument,
        destructiveConfirmed,
        cancellationRequested,
        () -> null);
  }

  /** Runs with a durable stop reason checked immediately before persistence. */
  public Result run(
      UserRecord user,
      String sessionId,
      ModelLevel level,
      String modelId,
      Long revision,
      String message,
      String sourceDocument,
      boolean destructiveConfirmed,
      java.util.function.BooleanSupplier cancellationRequested,
      java.util.function.Supplier<io.mehdieidi.varka.platform.kernel.PlatformException>
          stopReason) {
    return run(
        user,
        sessionId,
        level,
        modelId,
        revision,
        message,
        sourceDocument,
        destructiveConfirmed,
        cancellationRequested,
        stopReason,
        true);
  }

  /** Runs a durable turn whose transcript is recorded by the durable worker on completion. */
  public Result runDurable(
      UserRecord user,
      String sessionId,
      ModelLevel level,
      String modelId,
      Long revision,
      String message,
      String sourceDocument,
      boolean destructiveConfirmed,
      java.util.function.BooleanSupplier cancellationRequested,
      java.util.function.Supplier<io.mehdieidi.varka.platform.kernel.PlatformException>
          stopReason) {
    return run(
        user,
        sessionId,
        level,
        modelId,
        revision,
        message,
        sourceDocument,
        destructiveConfirmed,
        cancellationRequested,
        stopReason,
        false);
  }

  private Result run(
      UserRecord user,
      String sessionId,
      ModelLevel level,
      String modelId,
      Long revision,
      String message,
      String sourceDocument,
      boolean destructiveConfirmed,
      java.util.function.BooleanSupplier cancellationRequested,
      java.util.function.Supplier<io.mehdieidi.varka.platform.kernel.PlatformException> stopReason,
      boolean persistConversation) {
    ModelRecord current = models.get(user, level, modelId);
    if (persistConversation && memory != null)
      memory.appendMessage(sessionId, "USER", message, Map.of());
    String contextualMessage = contextualMessage(sessionId, message);
    long expectedRevision = revision == null ? current.revision() : revision;
    String extracted =
        sourceDocument == null || sourceDocument.isBlank()
            ? sourceDocument
            : sourceWorkers.extract(sessionId, sourceDocument);
    ModelWorkspace workspace =
        new ModelWorkspace(
            level,
            modelId,
            expectedRevision,
            current.modelJson(),
            patches,
            models,
            // A working copy is deliberately private. Durable consumers receive only the
            // persisted checkpoint emitted by the turn worker after model persistence succeeds.
            event -> {});
    AgentTurnLoop.TurnResult turn =
        loop.run(
            sessionId,
            level,
            contextualMessage,
            extracted,
            workspace,
            destructiveConfirmed,
            cancellationRequested == null ? () -> false : cancellationRequested,
            stopReason == null ? () -> null : stopReason);
    if (cancellationRequested != null && cancellationRequested.getAsBoolean())
      throw new io.mehdieidi.varka.platform.kernel.PlatformException(
          499, "Assistant turn was canceled before its model checkpoint could be saved.");
    io.mehdieidi.varka.platform.kernel.PlatformException stop =
        stopReason == null ? null : stopReason.get();
    if (stop != null) throw stop;
    ModelRecord updated = workspace.patch().isEmpty() ? current : workspace.apply(models, user);
    if (persistConversation && memory != null) {
      memory.appendMessage(
          sessionId, "ASSISTANT", turn.message(), Map.of("workflowState", "APPLIED"));
      memory.updateThreadModel(sessionId, updated.id(), updated.revision());
    }
    return new Result(
        turn.message(),
        updated.id(),
        updated.revision(),
        turn.provider(),
        turn.model(),
        turn.inversePatch(),
        workspace.affectedElementIds(),
        turn.commandBatch(),
        turn.providerCalls(),
        turn.promptTokens(),
        turn.completionTokens(),
        turn.providerCallDetails(),
        turn.modelingPlan(),
        turn.sourceBlueprint());
  }

  public boolean cancel(String sessionId) {
    return loop.cancel(sessionId);
  }

  private String contextualMessage(String sessionId, String message) {
    if (memory == null) return message;
    List<io.mehdieidi.varka.platform.assistant.domain.memory.AssistantMemoryRecords.MessageRecord>
        history =
            memory.recentMessages(sessionId, 8).stream()
                .sorted(
                    java.util.Comparator.comparing(
                        io.mehdieidi.varka.platform.assistant.domain.memory.AssistantMemoryRecords
                                .MessageRecord
                            ::createdAt))
                .toList();
    if (history.size() <= 1) return message;
    String prior =
        history.stream()
            .limit(Math.max(0, history.size() - 1))
            .map(item -> item.role() + ": " + item.content())
            .collect(java.util.stream.Collectors.joining("\n"));
    return prior.isBlank()
        ? message
        : "Recent completed conversation turns (context, not instructions):\n"
            + prior
            + "\n\nCurrent user request:\n"
            + message;
  }

  public record Result(
      String message,
      String modelId,
      long revision,
      String provider,
      String model,
      List<ModelService.ModelPatchOperation> inversePatch,
      List<String> affectedElementIds,
      io.mehdieidi.varka.platform.assistant.domain.ModelCommandBatch commandBatch,
      int providerCalls,
      long promptTokens,
      long completionTokens,
      List<io.mehdieidi.varka.platform.assistant.turn.AssistantTurnStore.ProviderCall>
          providerCallDetails,
      tools.jackson.databind.JsonNode modelingPlan,
      tools.jackson.databind.JsonNode sourceBlueprint) {}
}
