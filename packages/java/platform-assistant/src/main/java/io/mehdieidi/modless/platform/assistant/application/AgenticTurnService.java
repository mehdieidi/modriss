package io.mehdieidi.modless.platform.assistant.application;

import io.mehdieidi.modless.platform.assistant.agent.AgentTurnLoop;
import io.mehdieidi.modless.platform.assistant.domain.AssistantProposal;
import io.mehdieidi.modless.platform.assistant.domain.AssistantValidationSummary;
import io.mehdieidi.modless.platform.assistant.domain.SemanticModelPatch;
import io.mehdieidi.modless.platform.assistant.patch.AssistantPatchCompiler;
import io.mehdieidi.modless.platform.assistant.source.SourceDocumentWorkers;
import io.mehdieidi.modless.platform.assistant.spi.AssistantMemoryStore;
import io.mehdieidi.modless.platform.assistant.spi.AssistantRealtimePublisher;
import io.mehdieidi.modless.platform.assistant.workspace.ModelWorkspace;
import io.mehdieidi.modless.platform.identity.domain.UserRecord;
import io.mehdieidi.modless.platform.kernel.ModelLevel;
import io.mehdieidi.modless.platform.model.application.ModelService;
import io.mehdieidi.modless.platform.model.domain.ModelRecord;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Loads, runs, validates, and atomically commits one agentic workspace turn. */
public final class AgenticTurnService {

  private final ModelService models;
  private final AssistantPatchCompiler patches;
  private final AgentTurnLoop loop;
  private final SourceDocumentWorkers sourceWorkers;
  private final AssistantRealtimePublisher realtime;
  private final AssistantMemoryStore memory;

  public AgenticTurnService(
      ModelService models,
      AssistantPatchCompiler patches,
      AgentTurnLoop loop,
      SourceDocumentWorkers sourceWorkers,
      AssistantRealtimePublisher realtime) {
    this(models, patches, loop, sourceWorkers, realtime, null);
  }

  public AgenticTurnService(
      ModelService models,
      AssistantPatchCompiler patches,
      AgentTurnLoop loop,
      SourceDocumentWorkers sourceWorkers,
      AssistantRealtimePublisher realtime,
      AssistantMemoryStore memory) {
    this.models = models;
    this.patches = patches;
    this.loop = loop;
    this.sourceWorkers = sourceWorkers;
    this.realtime = realtime;
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
    ModelRecord current = models.get(user, level, modelId);
    if (memory != null) memory.appendMessage(sessionId, "USER", message, Map.of());
    long expectedRevision = revision == null ? current.revision() : revision;
    String extracted = sourceDocument;
    if (sourceDocument != null && sourceDocument.length() > 12000) {
      extracted = sourceWorkers.extract(sessionId, sourceDocument);
    }
    ModelWorkspace workspace =
        new ModelWorkspace(
            level,
            modelId,
            expectedRevision,
            current.modelJson(),
            patches,
            event ->
                realtime.publish(
                    sessionId,
                    "model.delta",
                    Map.of(
                        "operations",
                        event.operations(),
                        "affectedElementIds",
                        event.affectedElementIds(),
                        "model",
                        event.model())));
    AgentTurnLoop.TurnResult turn = loop.run(sessionId, level, message, extracted, workspace);
    ModelRecord updated = workspace.patch().isEmpty() ? current : workspace.apply(models, user);
    if (memory != null) {
      memory.appendMessage(
          sessionId, "ASSISTANT", turn.message(), Map.of("workflowState", "APPLIED"));
      memory.updateThreadModel(sessionId, updated.id(), updated.revision());
      if (!workspace.patch().isEmpty()) {
        AssistantValidationSummary validation =
            new AssistantValidationSummary(
                true,
                true,
                0,
                turn.validation().issues().stream()
                    .map(
                        issue ->
                            new AssistantValidationSummary.Issue(
                                issue.severity(),
                                issue.constraint(),
                                issue.elementId(),
                                issue.message()))
                    .toList());
        AssistantProposal proposal =
            new AssistantProposal(
                UUID.randomUUID().toString(),
                workspace.affectedElementIds(),
                new SemanticModelPatch(List.of()),
                workspace.inversePatch(),
                validation,
                AssistantProposal.RiskLevel.LOW,
                List.of(),
                Instant.now());
        memory.saveProposal(
            sessionId, current.projectId(), updated.id(), updated.revision(), proposal, "APPLIED");
      }
    }
    realtime.publish(
        sessionId,
        "model.updated",
        Map.of(
            "modelId", updated.id(), "revision", updated.revision(), "model", updated.modelJson()));
    realtime.publish(
        sessionId,
        "assistant.turn.completed",
        Map.of("message", turn.message(), "modelId", updated.id(), "revision", updated.revision()));
    return new Result(
        turn.message(), updated.id(), updated.revision(), turn.provider(), turn.model());
  }

  public boolean cancel(String sessionId) {
    return loop.cancel(sessionId);
  }

  public record Result(
      String message, String modelId, long revision, String provider, String model) {}
}
