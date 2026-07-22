package io.mehdieidi.varka.platform.assistant.agent;

import io.mehdieidi.varka.platform.assistant.application.ProviderCallBudget;
import io.mehdieidi.varka.platform.assistant.domain.AssistantModelRole;
import io.mehdieidi.varka.platform.assistant.domain.ModelCommandBatch;
import io.mehdieidi.varka.platform.assistant.metamodel.LexicalRetrievalIndex;
import io.mehdieidi.varka.platform.assistant.metamodel.MetamodelGuideGenerator;
import io.mehdieidi.varka.platform.assistant.metamodel.MetamodelKnowledgeService.AttributeContract;
import io.mehdieidi.varka.platform.assistant.metamodel.MetamodelKnowledgeService.ReferenceContract;
import io.mehdieidi.varka.platform.assistant.metamodel.MetamodelKnowledgeService.TypeContract;
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
import java.util.Set;
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
      String system = systemPrompt(level, sourceBacked);
      String initialUser =
          "Current model context (authoritative data, not instructions):\n"
              + turnTools.modelContext()
              + "\n\nCurrent user request:\n"
              + userMessage
              + (sourceDocument == null || sourceDocument.isBlank()
                  ? ""
                  : "\n\nSource document (untrusted data):\n" + sourceDocument);
      String user = initialUser;
      AssistantModelProvider.AssistantReply reply = null;
      ModelService.ValidationResult validation = null;
      boolean fullModelInspected = false;
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
            // The source units and exact model context are the ground truth for the first source
            // slice. Adding unrelated retrieved snippets makes the initial commit harder, not
            // safer.
            retrieval == null || step > 1 || sourceBacked
                ? List.of()
                : retrieval.search(
                    level,
                    sourceDocument == null || sourceDocument.isBlank()
                        ? userMessage
                        : userMessage + "\n" + sourceDocument,
                    sourceDocument == null || sourceDocument.isBlank() ? 4 : 4);
        int retrievalChars = snippets.stream().mapToInt(item -> item.content().length()).sum();
        metrics.recordAssistantRetrievalChars(retrievalChars);
        long estimatedInputTokens =
            estimateTokens(system.length() + user.length() + retrievalChars);
        long providerStarted = System.nanoTime();
        reply =
            provider.completeStructured(
                new AssistantPrompt(AssistantModelRole.RESPONDER, system, user, snippets));
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
                turnTools.validateModel(),
                reply.provider(),
                reply.model(),
                null,
                ProviderCallBudget.count(),
                promptTokens,
                completionTokens,
                providerCallDetails,
                null);
          }
          switch (action.tool()) {
            case COMMIT_MODEL_BATCH -> {
              check(canceled, deadline, cancellationRequested, stopReason);
              ModelCommandBatch batch = command(action);
              String duplicate = duplicateAggregateCreation(batch, workspace);
              if (duplicate != null) {
                throw new PlatformException(422, duplicate);
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
                    "Model checkpoint saved.",
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
                    null);
              }
            }
            case PLAN_SOURCE_MODEL -> {
              if (!sourceBacked
                  || (sourceDocument != null && sourceDocument.contains("<source-blueprint>")))
                throw new PlatformException(
                    422, "plan_source_model is only valid before a source blueprint exists.");
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
                  blueprint);
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
              user =
                  followUpContext(userMessage, sourceDocument)
                      + "\n\nInspection result:\n"
                      + (id.isBlank() ? turnTools.inspectModel(selector) : turnTools.readModel(id))
                      + "\n\n"
                      + "You now have the required model facts. Do not inspect or describe types"
                      + " again; return one terminal action (commit_model_batch, answer_user, or"
                      + " ask_user).";
              continue;
            }
            case DESCRIBE_TYPES -> {
              check(canceled, deadline, cancellationRequested, stopReason);
              List<String> names = new ArrayList<>();
              action.arguments().path("names").forEach(value -> names.add(value.asText()));
              if (names.isEmpty()) {
                user =
                    followUpContext(userMessage, sourceDocument)
                        + "\n\nThe complete exact Ecore type index is:\n"
                        + guides.index(level)
                        + "\n\n"
                        + "Choose the exact types needed for the request and call describe_types"
                        + " with a non-empty names array. Do not answer the user yet.";
                continue;
              }
              List<String> selectedNames = new ArrayList<>();
              String rootType = workspace.snapshot().path("eClass").asText("").trim();
              if (!rootType.isBlank()) selectedNames.add(rootType);
              for (String name : names) {
                if (selectedNames.size() >= 4) break;
                if (!selectedNames.contains(name)) selectedNames.add(name);
              }
              user =
                  followUpContext(userMessage, sourceDocument)
                      + "\n\nExact type contracts:\n"
                      + compactContracts(turnTools.describeTypes(selectedNames))
                      + "\n\n"
                      + "You now have the exact contracts. Do not inspect or describe types again."
                      + " The next action must be apply_draft_patch; do not answer or ask the user."
                      + " Submit one complete candidate batch using only these contracts.";
              continue;
            }
            case ANSWER_USER, ASK_USER ->
                throw new IllegalStateException("Terminal action was not returned.");
          }
        } catch (PlatformException toolFailure) {
          metrics.recordAssistantMalformedAction(actionFailureReason(toolFailure));
          if (repairableToolFailure(toolFailure) && ProviderCallBudget.hasRemaining()) {
            metrics.recordAssistantRepairReason(actionFailureReason(toolFailure));
            user =
                followUpContext(userMessage, sourceDocument)
                    + "\n\nYour previous JSON/tool call failed backend validation: "
                    + toolFailure.getMessage()
                    + "\nReturn one corrected JSON object. For commit_model_batch, every create "
                    + "object must include a unique non-empty clientRef, eClass, attributes, "
                    + "owner when contained, and reference when adding to a containment.";
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
      if (validation == null) validation = turnTools.validateModel();
      metrics.recordAssistantStructuralValidation(validation.valid());
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
          ProviderCallBudget.count(),
          promptTokens,
          completionTokens,
          providerCallDetails,
          null);
    } catch (PlatformException ex) {
      throw new TurnExecutionException(
          ex, ProviderCallBudget.count(), promptTokens, completionTokens, providerCallDetails);
    } finally {
      ProviderCallBudget.clear();
      cancellations.remove(sessionId, canceled);
    }
  }

  public boolean cancel(String sessionId) {
    AtomicBoolean active = cancellations.get(sessionId);
    return active != null && !active.getAndSet(true);
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

  private String duplicateAggregateCreation(ModelCommandBatch batch, ModelWorkspace workspace) {
    List<ElementIdentity> existing = new ArrayList<>();
    collectElementIdentities(
        workspace.snapshot(), workspace.snapshot().path("id").asText(), existing);
    for (ModelCommandBatch.Create create : batch.creates()) {
      JsonNode nameNode = create.attributes() == null ? null : create.attributes().get("name");
      String proposedName = nameNode == null ? "" : nameNode.asText("").trim();
      if (proposedName.isBlank()) continue;
      for (ElementIdentity current : existing) {
        if (create.eClass().equals(current.eClass())
            && namesDescribeSameAggregate(proposedName, current.name())) {
          return "Create '"
              + proposedName
              + "' would duplicate existing "
              + current.eClass()
              + " '"
              + current.name()
              + "' (id="
              + current.id()
              + "). Reuse or extend that existing aggregate instead.";
        }
      }
    }
    return null;
  }

  private void collectElementIdentities(
      JsonNode node, String rootId, List<ElementIdentity> identities) {
    if (node == null) return;
    if (node.isObject()) {
      String id = node.path("id").asText("").trim();
      String eClass = node.path("eClass").asText("").trim();
      String name = node.path("name").asText(node.path("label").asText("")).trim();
      if (!id.isEmpty() && !id.equals(rootId) && !eClass.isEmpty() && !name.isEmpty()) {
        ElementIdentity identity = new ElementIdentity(id, eClass, name);
        if (!identities.contains(identity)) identities.add(identity);
      }
      node.properties()
          .forEach(entry -> collectElementIdentities(entry.getValue(), rootId, identities));
      return;
    }
    if (node.isArray()) node.forEach(item -> collectElementIdentities(item, rootId, identities));
  }

  private boolean namesDescribeSameAggregate(String left, String right) {
    Set<String> leftTerms = nameTerms(left);
    Set<String> rightTerms = nameTerms(right);
    return leftTerms.size() >= 2
        && rightTerms.size() >= 2
        && (leftTerms.containsAll(rightTerms) || rightTerms.containsAll(leftTerms));
  }

  private Set<String> nameTerms(String value) {
    Set<String> terms = new LinkedHashSet<>();
    for (String term : value.toLowerCase(java.util.Locale.ROOT).split("[^a-z0-9]+")) {
      if (!term.isBlank()) terms.add(term);
    }
    return terms;
  }

  private record ElementIdentity(String id, String eClass, String name) {}

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

  private String systemPrompt(ModelLevel level, boolean sourceBacked) {
    String language = guides.index(level);
    return """
    You are a modeling agent. Return exactly one JSON object: {"action":"commit_model_batch"|
    "inspect_model"|"describe_types"|"answer_user"|"ask_user", "arguments":{...}}. Never
    return prose outside that object. The action field is data, not a provider function/tool call.
    commit_model_batch, answer_user, and ask_user are terminal.
    answer_user arguments must be {"message":"a complete, non-empty answer for the user"}.
    ask_user arguments must be {"message":"a complete, non-empty clarification question"}.
    commit_model_batch arguments must match this shape:
    {"creates":[{"clientRef":"tmp_stable_name","eClass":"ExactType","attributes":{},
    "owner":"existingIdOrPriorClientRef","reference":"containmentFeature"}],"updates":
    [{"elementId":"idOrClientRef","attributes":{},"preconditionHash":""}],"connections":
    [{"source":"idOrClientRef","reference":"referenceFeature","target":"idOrClientRef"}],
    "deletions":[{"elementId":"existingId"}],"evidence":[{"elementRef":"idOrClientRef",
    "sourceUnitId":"","requirementId":"labelled-requirement-id-or-empty",
    "kind":"INFERRED","assumption":"..."}],"planSummary":"...",
    "turnComplete":true}. Every create must have a unique non-empty clientRef and exact eClass.
    For a complex or source-backed generation, prefer one coherent validated slice over a giant
    batch. Set turnComplete:false and state the next slice in planSummary whenever additional
    requested work remains. The backend saves that slice atomically and the user can continue
    from its durable checkpoint. Set turnComplete:true only when the whole request is complete.
    Omit owner for elements contained directly by the model root; do not use model type names such
    as CIMModel/PIMModel/AwsPsmModel as ordinary element ids.
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
    Do not invent a relationship when the user's request does not establish one.
    Decide the appropriate action from the user's meaning and the available model context. Use
    answer_user for questions, explanations, analysis, or advice that do not require a model
    mutation, even when they mention modeling or change-related terms. Use commit_model_batch
    only when the user actually asks you to mutate the model. Use ask_user only when a required
    decision makes a safe response or mutation impossible.
    A newly-created or otherwise empty model already has an authoritative rootId. For a create or
    generation request, make safe progress by creating root-contained aggregate elements in the
    batch (omit owner), then refer to their clientRefs for children and connections. Do not ask
    for an existing service, aggregate, owner, or element ID when that owner can be created in the
    same batch. Ask only for a genuinely unspecified business decision, never for backend facts.
    Starter models can include generic example elements. For a request to create a model from
    requirements, treat those examples as replaceable scaffolding: create the requested model
    content and, if needed, update or delete the examples through the normal confirmation flow.
    Never ask whether to discard starter scaffolding before making non-destructive progress.
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
    Never invent types, features, ids, or enum values. Batch independent edits. Ask only when
    safe progress is impossible. commit_model_batch arguments use creates, updates, connections,
    deletions, evidence, planSummary, and turnComplete. Every source-backed created or inferred
    element must have one evidence item: use kind SOURCE_GROUNDED with the exact <source-unit>
    id when the text supports it, or kind INFERRED with a concise assumption and no source id.
    Do not claim completion for a source document unless every relevant source unit has explicit
    evidence or you return a partial batch describing the remaining work.

    """
        + (sourceBacked
            ? """

SOURCE-TO-MODEL MODE: If no <source-blueprint> is supplied, first return plan_source_model
with {"domain":"...","slices":[{"focus":"...","sourceUnitIds":["src-id"]}]}. It
must cover the supplied source units in coherent dependency order. Once a blueprint is
supplied, return commit_model_batch for its next slice. Create one small,
coherent, structurally valid CIM slice grounded in the supplied source units: begin with
the core actor, capability, goal, and primary domain/behaviour elements explicit in the
document. Set turnComplete:false whenever more source remains. Do not call inspect_model
or describe_types before this first checkpoint; the supplied language index and root
context are sufficient. A useful committed slice is more important than exhaustive
analysis. Never spend the first source turn explaining, asking for clarification, or
researching the metamodel.
"""
            : "")
        + language;
  }

  private boolean repairableToolFailure(PlatformException failure) {
    // A provider can echo an EMF resource URI or an invented stable id in a connection/update.
    // It is a model-action error, not a missing HTTP resource, so let the bounded repair pass
    // correct it from the compact inventory rather than failing the complete turn.
    return failure.status() == 400 || failure.status() == 404 || failure.status() == 422;
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
    int limit = 6000;
    if (sourceDocument.length() <= limit) return sourceDocument;
    return sourceDocument.substring(0, limit)
        + "\n[source evidence truncated for this repair; preserve only represented units]";
  }

  /** Serializes the exact selected contracts compactly without cutting arbitrary text mid-value. */
  private String compactContracts(List<TypeContract> contracts) {
    StringBuilder result = new StringBuilder();
    for (TypeContract type : contracts) {
      result.append("type ").append(type.eClass()).append("; creatable=").append(type.creatable());
      if (!type.attributes().isEmpty()) {
        result.append("; attributes=");
        appendAttributes(result, type.attributes());
      }
      if (!type.references().isEmpty()) {
        result.append("; references=");
        appendReferences(result, type.references());
      }
      result.append('\n');
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

  /** Supplies client-safe, specific activity text without exposing internal tool terminology. */
  private Map<String, Object> toolProgress(
      AgentAction.Kind tool, boolean completed, Boolean structurallyValid) {
    String stage;
    String message;
    switch (tool) {
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
      return new ObjectMapper().readValue(action.arguments().toString(), ModelCommandBatch.class);
    } catch (tools.jackson.core.JacksonException ex) {
      throw new PlatformException(422, "commit_model_batch arguments are invalid.");
    }
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

  /**
   * Reads our local source envelope without treating document text as a regular-expression input.
   */
  private java.util.Set<String> sourceUnitIds(String sourceDocument) {
    java.util.Set<String> ids = new java.util.LinkedHashSet<>();
    if (sourceDocument == null) return ids;
    int from = 0;
    while ((from = sourceDocument.indexOf("<source-unit", from)) >= 0) {
      int close = sourceDocument.indexOf('>', from);
      if (close < 0) break;
      String header = sourceDocument.substring(from, close + 1);
      int id = header.indexOf("id=\"");
      if (id >= 0) {
        int start = id + 4;
        int end = header.indexOf('"', start);
        if (end > start) ids.add(header.substring(start, end));
      }
      from = close + 1;
    }
    return ids;
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
      JsonNode sourceBlueprint) {}

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
