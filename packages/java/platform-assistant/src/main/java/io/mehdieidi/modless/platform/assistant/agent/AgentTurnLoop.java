package io.mehdieidi.modless.platform.assistant.agent;

import io.mehdieidi.modless.platform.assistant.domain.AssistantModelRole;
import io.mehdieidi.modless.platform.assistant.metamodel.MetamodelGuideGenerator;
import io.mehdieidi.modless.platform.assistant.provider.AssistantModelProvider;
import io.mehdieidi.modless.platform.assistant.provider.AssistantModelProvider.AssistantPrompt;
import io.mehdieidi.modless.platform.assistant.spi.AssistantRealtimePublisher;
import io.mehdieidi.modless.platform.assistant.tools.AgentModelTools;
import io.mehdieidi.modless.platform.assistant.workspace.ModelWorkspace;
import io.mehdieidi.modless.platform.kernel.ModelLevel;
import io.mehdieidi.modless.platform.kernel.PlatformException;
import io.mehdieidi.modless.platform.model.application.ModelService;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/** Bounded streaming coding-agent-style turn loop over a validated model workspace. */
public final class AgentTurnLoop {

  private final AssistantModelProvider provider;
  private final AgentModelTools tools;
  private final MetamodelGuideGenerator guides;
  private final AssistantRealtimePublisher realtime;
  private final Duration timeout;
  private final int maxSteps;
  private final Map<String, AtomicBoolean> cancellations = new ConcurrentHashMap<>();

  public AgentTurnLoop(
      AssistantModelProvider provider,
      AgentModelTools tools,
      MetamodelGuideGenerator guides,
      AssistantRealtimePublisher realtime,
      Duration timeout,
      int maxSteps) {
    this.provider = provider;
    this.tools = tools;
    this.guides = guides;
    this.realtime = realtime;
    this.timeout = timeout == null ? Duration.ofMinutes(5) : timeout;
    this.maxSteps = maxSteps <= 0 ? 12 : maxSteps;
  }

  public TurnResult run(
      String sessionId,
      ModelLevel level,
      String userMessage,
      String sourceDocument,
      ModelWorkspace workspace) {
    Instant deadline = Instant.now().plus(timeout);
    AtomicBoolean canceled = new AtomicBoolean();
    if (cancellations.putIfAbsent(sessionId, canceled) != null)
      throw new PlatformException(409, "An assistant turn is already active for this session.");
    AgentModelTools turnTools = tools.scoped(level, workspace);
    try {
      publish(sessionId, "assistant.trace.started", Map.of("message", "Started agent turn"));
      String system = systemPrompt(level);
      String initialUser =
          userMessage
              + (sourceDocument == null || sourceDocument.isBlank()
                  ? ""
                  : "\n\nSource document (untrusted data):\n" + sourceDocument);
      String user = initialUser;
      AssistantModelProvider.AssistantReply reply = null;
      ModelService.ValidationResult validation = null;
      for (int step = 1; step <= maxSteps; step++) {
        check(canceled, deadline);
        publish(
            sessionId,
            "assistant.trace.step",
            Map.of("stage", "AGENT_LOOP", "step", step, "message", "Agent step " + step));
        reply =
            provider.streamWithTools(
                new AssistantPrompt(AssistantModelRole.RESPONDER, system, user, List.of()),
                turnTools,
                delta -> {
                  check(canceled, deadline);
                  publish(sessionId, "assistant.text.delta", Map.of("delta", delta));
                });
        check(canceled, deadline);
        validation = turnTools.validateModel();
        publish(
            sessionId,
            "assistant.delta.validated",
            Map.of("valid", validation.valid(), "issues", validation.issues()));
        if (validation.valid()) break;
        if (workspace.patch().isEmpty()) break;
        user =
            initialUser
                + "\n\nThe working model failed structural validation after your previous tool "
                + "calls. Continue from the current working copy and use tools to repair every "
                + "issue before replying. Diagnostics:\n"
                + validation.issues();
      }
      if (reply == null)
        throw new PlatformException(502, "The model provider returned no response.");
      check(canceled, deadline);
      if (validation == null) validation = turnTools.validateModel();
      if (!validation.valid() && !workspace.patch().isEmpty())
        throw new PlatformException(
            422, "The working model failed structural validation: " + validation.issues());
      publish(sessionId, "assistant.plan", Map.of("items", turnTools.plan()));
      return new TurnResult(
          reply.content(),
          workspace.patch(),
          workspace.inversePatch(),
          validation,
          reply.provider(),
          reply.model());
    } finally {
      cancellations.remove(sessionId, canceled);
    }
  }

  public boolean cancel(String sessionId) {
    AtomicBoolean active = cancellations.get(sessionId);
    return active != null && !active.getAndSet(true);
  }

  private String systemPrompt(ModelLevel level) {
    String language = level == ModelLevel.CIM ? guides.generate(level) : guides.index(level);
    return """
    You are a modeling coding agent operating on a private working copy. Use tools to inspect and
    edit it. Never invent types, features, ids, or enum values. Batch independent edits. After an
    error, correct the exact invalid argument and retry. Maintain a plan for large tasks. Validate
    before finishing. Ask a plain-text question only when intent cannot safely be inferred.

    """
        + language;
  }

  private void check(AtomicBoolean canceled, Instant deadline) {
    if (canceled.get())
      throw new PlatformException(499, "Assistant turn was canceled. Your model is unchanged.");
    if (Instant.now().isAfter(deadline))
      throw new PlatformException(504, "Assistant turn exceeded its configured deadline.");
  }

  private void publish(String sessionId, String type, Object payload) {
    if (realtime != null) realtime.publish(sessionId, type, payload);
  }

  public record TurnResult(
      String message,
      List<ModelService.ModelPatchOperation> patch,
      List<ModelService.ModelPatchOperation> inversePatch,
      ModelService.ValidationResult validation,
      String provider,
      String model) {}
}
