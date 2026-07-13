package io.mehdieidi.varka.platform.assistant.agent;

import io.mehdieidi.varka.platform.assistant.application.ProviderCallBudget;
import io.mehdieidi.varka.platform.assistant.domain.AssistantModelRole;
import io.mehdieidi.varka.platform.assistant.domain.ModelCommandBatch;
import io.mehdieidi.varka.platform.assistant.metamodel.LexicalRetrievalIndex;
import io.mehdieidi.varka.platform.assistant.metamodel.MetamodelGuideGenerator;
import io.mehdieidi.varka.platform.assistant.provider.AssistantModelProvider;
import io.mehdieidi.varka.platform.assistant.provider.AssistantModelProvider.AssistantPrompt;
import io.mehdieidi.varka.platform.assistant.spi.AssistantRealtimePublisher;
import io.mehdieidi.varka.platform.assistant.tools.AgentModelTools;
import io.mehdieidi.varka.platform.assistant.workspace.ModelWorkspace;
import io.mehdieidi.varka.platform.kernel.ModelLevel;
import io.mehdieidi.varka.platform.kernel.PlatformException;
import io.mehdieidi.varka.platform.model.application.ModelService;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
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
  private final AgentActionCodec actions;
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
        maxProviderCalls);
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
    this.provider = provider;
    this.tools = tools;
    this.guides = guides;
    this.retrieval = retrieval;
    this.realtime = realtime;
    this.timeout = timeout == null ? Duration.ofMinutes(5) : timeout;
    this.sourceTimeout = sourceTimeout == null ? this.timeout : sourceTimeout;
    this.maxSteps = maxSteps <= 0 ? 12 : maxSteps;
    this.maxProviderCalls = maxProviderCalls <= 0 ? 3 : maxProviderCalls;
    this.actions = new AgentActionCodec(new ObjectMapper());
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
    Instant deadline =
        Instant.now()
            .plus(sourceDocument == null || sourceDocument.isBlank() ? timeout : sourceTimeout);
    AtomicBoolean canceled = new AtomicBoolean();
    if (cancellations.putIfAbsent(sessionId, canceled) != null)
      throw new PlatformException(409, "An assistant turn is already active for this session.");
    AgentModelTools turnTools = tools.scoped(level, workspace);
    try {
      ProviderCallBudget.bind(
          sourceDocument == null || sourceDocument.isBlank()
              ? maxProviderCalls
              : Math.max(maxProviderCalls, 3));
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
        check(canceled, deadline, cancellationRequested, stopReason);
        publish(
            sessionId,
            "assistant.trace.step",
            Map.of("stage", "AGENT_LOOP", "step", step, "message", "Agent step " + step));
        List<AssistantModelProvider.ContextSnippet> snippets =
            retrieval == null ? List.of() : retrieval.search(level, userMessage, 6);
        reply =
            provider.completeStructured(
                new AssistantPrompt(AssistantModelRole.RESPONDER, system, user, snippets));
        check(canceled, deadline, cancellationRequested, stopReason);
        AgentAction action = actions.parse(reply.content());
        publish(sessionId, "tool.started", Map.of("tool", action.tool().wireName()));
        if (action.tool() == AgentAction.Kind.ANSWER_USER
            || action.tool() == AgentAction.Kind.ASK_USER) {
          String message = action.arguments().path("message").asText();
          return new TurnResult(
              message,
              workspace.patch(),
              workspace.inversePatch(),
              turnTools.validateModel(),
              reply.provider(),
              reply.model(),
              null,
              ProviderCallBudget.count());
        }
        if (action.tool() == AgentAction.Kind.COMMIT_MODEL_BATCH) {
          check(canceled, deadline, cancellationRequested, stopReason);
          turnTools.commitModelBatch(command(action), destructiveConfirmed);
          check(canceled, deadline, cancellationRequested, stopReason);
          validation = turnTools.validateModel();
          check(canceled, deadline, cancellationRequested, stopReason);
          publish(
              sessionId,
              "tool.completed",
              Map.of("tool", action.tool().wireName(), "valid", validation.valid()));
          if (validation.valid()) {
            return new TurnResult(
                "Model checkpoint saved.",
                workspace.patch(),
                workspace.inversePatch(),
                validation,
                reply.provider(),
                reply.model(),
                turnTools.committedBatch(),
                ProviderCallBudget.count());
          }
        } else if (action.tool() == AgentAction.Kind.INSPECT_MODEL) {
          check(canceled, deadline, cancellationRequested, stopReason);
          String id = action.arguments().path("id").asText("");
          user = initialUser + "\n\nInspection result:\n" + turnTools.readModel(id);
          continue;
        } else if (action.tool() == AgentAction.Kind.DESCRIBE_TYPES) {
          check(canceled, deadline, cancellationRequested, stopReason);
          List<String> names = new ArrayList<>();
          action.arguments().path("names").forEach(value -> names.add(value.asText()));
          user = initialUser + "\n\nExact type contracts:\n" + turnTools.describeTypes(names);
          continue;
        }
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
      check(canceled, deadline, cancellationRequested, stopReason);
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
          reply.model(),
          turnTools.committedBatch(),
          ProviderCallBudget.count());
    } finally {
      ProviderCallBudget.clear();
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
    You are a modeling agent. Return exactly one JSON object: {"tool":"commit_model_batch"|
    "inspect_model"|"describe_types"|"answer_user"|"ask_user", "arguments":{...}}. Never
    return prose outside that object. commit_model_batch, answer_user, and ask_user are terminal.
    Never invent types, features, ids, or enum values. Batch independent edits. Ask only when
    safe progress is impossible. commit_model_batch arguments use creates, updates, connections,
    deletions, evidence, planSummary, and turnComplete. Every source-backed created or inferred
    element must have one evidence item: use kind SOURCE_GROUNDED with the exact <source-unit>
    id when the text supports it, or kind INFERRED with a concise assumption and no source id.
    Do not claim completion for a source document unless every relevant source unit has explicit
    evidence or you return a partial batch describing the remaining work.

    """
        + language;
  }

  private void check(
      AtomicBoolean canceled,
      Instant deadline,
      java.util.function.BooleanSupplier cancellationRequested,
      java.util.function.Supplier<PlatformException> stopReason) {
    if (canceled.get())
      throw new PlatformException(499, "Assistant turn was canceled. Your model is unchanged.");
    if (cancellationRequested != null && cancellationRequested.getAsBoolean())
      throw new PlatformException(499, "Assistant turn was canceled. Your model is unchanged.");
    PlatformException durableStop = stopReason == null ? null : stopReason.get();
    if (durableStop != null) throw durableStop;
    if (Instant.now().isAfter(deadline))
      throw new PlatformException(504, "Assistant turn exceeded its configured deadline.");
  }

  private void publish(String sessionId, String type, Object payload) {
    if (realtime != null) realtime.publish(sessionId, type, payload);
  }

  private ModelCommandBatch command(AgentAction action) {
    try {
      return new ObjectMapper().readValue(action.arguments().toString(), ModelCommandBatch.class);
    } catch (Exception ex) {
      throw new PlatformException(422, "commit_model_batch arguments are invalid.");
    }
  }

  public record TurnResult(
      String message,
      List<ModelService.ModelPatchOperation> patch,
      List<ModelService.ModelPatchOperation> inversePatch,
      ModelService.ValidationResult validation,
      String provider,
      String model,
      ModelCommandBatch commandBatch,
      int providerCalls) {}
}
