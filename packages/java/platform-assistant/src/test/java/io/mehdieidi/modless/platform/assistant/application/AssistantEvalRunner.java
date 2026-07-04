package io.mehdieidi.modless.platform.assistant.application;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.mehdieidi.modless.platform.assistant.delta.DeltaCompiler;
import io.mehdieidi.modless.platform.assistant.delta.DeltaNormalizer;
import io.mehdieidi.modless.platform.assistant.delta.ModelDelta;
import io.mehdieidi.modless.platform.assistant.delta.ModelDeltaParser;
import io.mehdieidi.modless.platform.assistant.delta.ModelDeltaSchemaFactory;
import io.mehdieidi.modless.platform.assistant.domain.AssistantModelRole;
import io.mehdieidi.modless.platform.assistant.domain.SemanticModelPatch;
import io.mehdieidi.modless.platform.assistant.patch.AssistantMetamodelSchemaService;
import io.mehdieidi.modless.platform.assistant.patch.AssistantPatchCompleter;
import io.mehdieidi.modless.platform.assistant.provider.AssistantModelProvider;
import io.mehdieidi.modless.platform.assistant.source.SourceChunker;
import io.mehdieidi.modless.platform.assistant.source.SourceEvidenceExtractor;
import io.mehdieidi.modless.platform.assistant.source.SourceEvidenceGraph;
import io.mehdieidi.modless.platform.assistant.source.SourceUnderstandingService;
import io.mehdieidi.modless.platform.kernel.ModelLevel;
import io.mehdieidi.modless.platform.kernel.PlatformException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Curated prompt benchmark runner for assistant reliability work. */
public final class AssistantEvalRunner {

  private final AssistantModelProvider provider;
  private final AssistantMetamodelSchemaService schemas;
  private final AssistantPatchCompleter patchCompleter;
  private final ObjectMapper mapper;
  private final ModelDeltaParser deltaParser;
  private final DeltaNormalizer deltaNormalizer;
  private final DeltaCompiler deltaCompiler;
  private final ModelDeltaSchemaFactory deltaSchemaFactory;
  private final SourceUnderstandingService sourceUnderstanding;

  public AssistantEvalRunner(
      AssistantModelProvider provider,
      AssistantMetamodelSchemaService schemas,
      AssistantPatchCompleter patchCompleter,
      ObjectMapper mapper) {
    this(
        provider,
        schemas,
        patchCompleter,
        mapper,
        new SourceUnderstandingService(new SourceChunker()));
  }

  public AssistantEvalRunner(
      AssistantModelProvider provider,
      AssistantMetamodelSchemaService schemas,
      AssistantPatchCompleter patchCompleter,
      ObjectMapper mapper,
      SourceUnderstandingService sourceUnderstanding) {
    this.provider = provider;
    this.schemas = schemas;
    this.patchCompleter = patchCompleter;
    this.mapper = mapper;
    this.deltaParser = new ModelDeltaParser(mapper);
    this.deltaNormalizer = new DeltaNormalizer(schemas);
    this.deltaCompiler = new DeltaCompiler(schemas);
    this.deltaSchemaFactory = new ModelDeltaSchemaFactory(schemas, mapper);
    this.sourceUnderstanding =
        sourceUnderstanding == null
            ? new SourceUnderstandingService(new SourceChunker())
            : sourceUnderstanding;
  }

  /** Loads curated prompts from the bundled fixture file. */
  public List<EvalPrompt> loadPrompts() {
    return loadPromptsFromResource("/assistant-eval-prompts.json");
  }

  /** Loads the bounded live-gate prompt set (designed to finish within turn latency budgets). */
  public List<EvalPrompt> loadLiveGatePrompts() {
    return loadPromptsFromResource("/assistant-eval-live-gate-prompts.json");
  }

  private List<EvalPrompt> loadPromptsFromResource(String resourcePath) {
    try (InputStream input = AssistantEvalRunner.class.getResourceAsStream(resourcePath)) {
      if (input == null) {
        throw new IllegalStateException(resourcePath + " is missing from test resources.");
      }
      return mapper.readValue(input, new TypeReference<>() {});
    } catch (Exception ex) {
      throw new IllegalStateException(
          "Could not load assistant eval prompts from " + resourcePath, ex);
    }
  }

  /**
   * Runs the benchmark and returns per-prompt results.
   *
   * @param live when true, calls the configured provider; otherwise uses stub planning only
   * @return eval results
   */
  public List<EvalResult> run(boolean live) {
    return run(live, null);
  }

  /**
   * Runs the benchmark with optional tool-session binding for live agent loops.
   *
   * @param live when true, calls the configured provider
   * @param toolBinder binds model context before each live agent loop
   * @return eval results
   */
  public List<EvalResult> run(boolean live, ToolBinder toolBinder) {
    return run(live, toolBinder, loadPrompts());
  }

  /**
   * Runs the benchmark for an explicit prompt set.
   *
   * @param live when true, calls the configured provider
   * @param toolBinder binds model context before each live agent loop
   * @param prompts prompts to run
   * @return eval results
   */
  public List<EvalResult> run(boolean live, ToolBinder toolBinder, List<EvalPrompt> prompts) {
    return run(live, toolBinder, prompts, LiveEvalBudget.defaults());
  }

  /**
   * Runs the benchmark for an explicit prompt set with latency budgets.
   *
   * @param live when true, calls the configured provider
   * @param toolBinder binds model context before each live agent loop
   * @param prompts prompts to run
   * @param budget per-turn and suite latency ceilings for live runs
   * @return eval results
   */
  public List<EvalResult> run(
      boolean live, ToolBinder toolBinder, List<EvalPrompt> prompts, LiveEvalBudget budget) {
    List<EvalResult> results = new ArrayList<>();
    long suiteStartedAt = System.currentTimeMillis();
    LiveEvalBudget limits = budget == null ? LiveEvalBudget.defaults() : budget;
    for (EvalPrompt prompt : prompts == null ? List.<EvalPrompt>of() : prompts) {
      if (live && limits.maxSuiteLatencyMs() > 0) {
        long elapsedSuite = System.currentTimeMillis() - suiteStartedAt;
        if (elapsedSuite > limits.maxSuiteLatencyMs()) {
          results.add(
              suiteBudgetExceededResult(
                  prompt, elapsedSuite, limits.maxSuiteLatencyMs(), results.size()));
          break;
        }
      }
      long startedAt = System.currentTimeMillis();
      int providerCalls = 0;
      long providerWaitMs = 0L;
      EvalResult.Builder builder =
          new EvalResult.Builder(prompt.id(), prompt.category(), prompt.level());
      List<AssistantModelProvider.ContextSnippet> snippets = List.of();
      try {
        ModelLevel level = ModelLevel.fromApiName(prompt.level());
        snippets = new ArrayList<>(deltaSchemaFactory.snippets(level));
        snippets.addAll(schemas.planningContracts(level, prompt.prompt(), 8));
        for (String required : prompt.requiredContracts()) {
          var type = schemas.typeSchema(level, required);
          if (type.isPresent()) {
            snippets.add(schemas.typeContract(level, type.get().name()));
          }
        }
        int requiredContracts = prompt.requiredContracts().size();
        int retrievedRequiredContracts =
            countRequiredContracts(prompt.requiredContracts(), snippets);
        int sourceChunkCount = sourceChunkCount(prompt);
        if (!live) {
          if ("resilience".equals(prompt.category())) {
            runResilienceStub(prompt, builder, startedAt);
          } else {
            runStructuralStub(
                prompt,
                level,
                snippets,
                builder,
                startedAt,
                requiredContracts,
                retrievedRequiredContracts,
                sourceChunkCount);
          }
          results.add(builder.build());
          continue;
        }
        boolean sourceAnalysisUsed = false;
        int coveredSourceChunks = 0;
        if (!prompt.sourceDocument().isBlank() && level == ModelLevel.CIM) {
          long providerStarted = System.currentTimeMillis();
          SourceEvidenceGraph graph =
              sourceUnderstanding.understand(
                  prompt.id(), prompt.id() + ".md", prompt.sourceDocument(), 4000, 24);
          providerWaitMs += System.currentTimeMillis() - providerStarted;
          if (!graph.facts().isEmpty() || !graph.coverage().isEmpty()) {
            sourceAnalysisUsed = true;
            coveredSourceChunks = graph.coverage().size();
            try {
              snippets.add(
                  new AssistantModelProvider.ContextSnippet(
                      "source-analysis",
                      prompt.id() + " evidence graph",
                      compact(mapper.writeValueAsString(graph), 10000)));
            } catch (Exception ignored) {
              // Keep eval running when serialization fails.
            }
          }
        }
        if (toolBinder != null) {
          toolBinder.bind(level, prompt);
        }
        JsonNode baseModel =
            toolBinder == null || toolBinder.baseModel() == null
                ? mapper.createObjectNode()
                : toolBinder.baseModel();
        Map<String, String> existingTypes =
            toolBinder == null || toolBinder.existingTypes() == null
                ? Map.of()
                : toolBinder.existingTypes();
        snippets = new ArrayList<>(snippets);
        snippets.addAll(canvasContextSnippets(level, baseModel, existingTypes));
        try {
          long providerStarted = System.currentTimeMillis();
          AssistantModelProvider.AssistantReply reply =
              provider.completeStructured(
                  new AssistantModelProvider.AssistantPrompt(
                      AssistantModelRole.PLANNER,
                      "Eval run for category "
                          + prompt.category()
                          + ". Return only ModelDelta JSON.\n\n"
                          + modelDeltaProtocol(level, baseModel),
                      prompt.prompt(),
                      budgetSnippets(snippets)));
          providerWaitMs += System.currentTimeMillis() - providerStarted;
          providerCalls++;
          ModelDelta delta = deltaNormalizer.normalize(level, deltaParser.parse(reply.content()));
          SemanticModelPatch completed =
              deltaCompiler.compile(level, baseModel, existingTypes, delta);
          int addCount = count(completed, SemanticModelPatch.OperationType.ADD_ELEMENT);
          int connectionCount = count(completed, SemanticModelPatch.OperationType.CONNECT_ELEMENTS);
          long latencyMs = System.currentTimeMillis() - startedAt;
          boolean passed =
              evalPassed(
                  prompt,
                  delta,
                  completed,
                  sourceAnalysisUsed,
                  sourceChunkCount,
                  coveredSourceChunks);
          String failureStage = "";
          String failureMessage = "";
          if (passed
              && live
              && limits.maxTurnLatencyMs() > 0
              && latencyMs > limits.maxTurnLatencyMs()) {
            passed = false;
            failureStage = "TURN_LATENCY";
            failureMessage =
                "latencyMs="
                    + latencyMs
                    + " exceeded turn budget "
                    + limits.maxTurnLatencyMs()
                    + "ms";
          } else if (!passed) {
            failureStage = "EVAL_EXPECTATIONS";
            failureMessage =
                "operations="
                    + completed.operations().size()
                    + "/"
                    + prompt.minOperations()
                    + ", additions="
                    + addCount
                    + "/"
                    + prompt.minElementAdds()
                    + ", connections="
                    + connectionCount
                    + "/"
                    + prompt.minConnections()
                    + ", sourceAnalysis="
                    + sourceAnalysisUsed;
          }
          builder
              .turnKind(delta.kind().name())
              .operationCount(completed.operations().size())
              .addElementCount(addCount)
              .connectionCount(connectionCount)
              .sourceAnalysisUsed(sourceAnalysisUsed)
              .validationPassed(passed)
              .repairAttempts(0)
              .toolCalls(providerCalls)
              .sourceChunkCount(sourceChunkCount)
              .coveredSourceChunkCount(sourceAnalysisUsed ? coveredSourceChunks : 0)
              .requiredContractCount(requiredContracts)
              .retrievedRequiredContractCount(retrievedRequiredContracts)
              .providerWaitMs(providerWaitMs)
              .failureStage(failureStage)
              .failureMessage(failureMessage)
              .latencyMs(latencyMs);
        } finally {
          if (toolBinder != null) {
            toolBinder.clear();
          }
        }
      } catch (Exception ex) {
        builder
            .turnKind("FAILED")
            .operationCount(0)
            .validationPassed(false)
            .repairAttempts(0)
            .toolCalls(providerCalls)
            .sourceChunkCount(0)
            .coveredSourceChunkCount(0)
            .requiredContractCount(0)
            .retrievedRequiredContractCount(0)
            .providerWaitMs(providerWaitMs)
            .failureStage("PLANNING")
            .failureMessage(ex.getMessage())
            .latencyMs(System.currentTimeMillis() - startedAt);
      }
      results.add(builder.build());
    }
    return results;
  }

  private int sourceChunkCount(EvalPrompt prompt) {
    if (prompt == null || prompt.sourceDocument().isBlank()) {
      return 0;
    }
    return new SourceChunker().chunk(prompt.id(), prompt.sourceDocument(), 4000, 24).size();
  }

  private boolean countsTowardModelDeltaSuccessGate(EvalResult result) {
    if (result == null) {
      return false;
    }
    if ("analysis".equals(result.category())) {
      return false;
    }
    if ("psm".equals(result.category())
        && result.operationCount() == 0
        && result.addElementCount() == 0
        && result.connectionCount() == 0) {
      return false;
    }
    return true;
  }

  private boolean readOnlyLevelPrompt(EvalPrompt prompt) {
    return "psm".equals(prompt.category()) && prompt.minOperations() == 0;
  }

  private boolean evalPassed(
      EvalPrompt prompt,
      ModelDelta delta,
      SemanticModelPatch completed,
      boolean sourceAnalysisUsed,
      int sourceChunkCount,
      int coveredSourceChunks) {
    if ("analysis".equals(prompt.category()) || readOnlyLevelPrompt(prompt)) {
      return delta.kind() == ModelDelta.Kind.ANSWER
          || (delta.kind() == ModelDelta.Kind.MODEL_DELTA && completed.operations().isEmpty());
    }
    if ("resilience".equals(prompt.category())) {
      return false;
    }
    boolean structural =
        delta.kind() == ModelDelta.Kind.MODEL_DELTA
            && completed.operations().size() >= prompt.minOperations()
            && count(completed, SemanticModelPatch.OperationType.ADD_ELEMENT)
                >= prompt.minElementAdds()
            && count(completed, SemanticModelPatch.OperationType.CONNECT_ELEMENTS)
                >= prompt.minConnections()
            && structuralExpectationsMet(prompt, delta, completed);
    if (!prompt.requiresSourceAnalysis()) {
      return structural;
    }
    return structural
        && sourceAnalysisUsed
        && sourceChunkCount > 0
        && coveredSourceChunks >= sourceChunkCount;
  }

  private boolean structuralExpectationsMet(
      EvalPrompt prompt, ModelDelta delta, SemanticModelPatch completed) {
    StructuralExpectations expectations = prompt.structuralExpectations();
    if (expectations == null) {
      return true;
    }
    Set<String> elementTypes =
        completed.operations().stream()
            .filter(op -> op.type() == SemanticModelPatch.OperationType.ADD_ELEMENT)
            .map(SemanticModelPatch.Operation::elementType)
            .collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new));
    for (String family : expectations.requiredTypeFamilies()) {
      if (elementTypes.stream().noneMatch(type -> type.equalsIgnoreCase(family))) {
        return false;
      }
    }
    int relationships =
        count(completed, SemanticModelPatch.OperationType.CONNECT_ELEMENTS)
            + (int)
                delta.references().stream()
                    .filter(reference -> reference != null && !reference.referenceName().isBlank())
                    .count();
    if (relationships < expectations.minRelationships()) {
      return false;
    }
    if (expectations.requireEvidenceIds()
        && delta.elements().stream().anyMatch(element -> element.evidenceIds().isEmpty())) {
      return false;
    }
    if (expectations.rejectPlaceholderOnlyNames()) {
      for (ModelDelta.Element element : delta.elements()) {
        String name =
            element.attributes() == null ? "" : element.attributes().path("name").asText("");
        if (!name.isBlank() && name.equalsIgnoreCase(element.eClass())) {
          return false;
        }
      }
    }
    return true;
  }

  private EvalResult suiteBudgetExceededResult(
      EvalPrompt prompt, long elapsedSuiteMs, long maxSuiteLatencyMs, int completedCount) {
    return new EvalResult(
        prompt == null ? "suite-budget" : prompt.id(),
        prompt == null ? "suite" : prompt.category(),
        prompt == null ? "" : prompt.level(),
        "SUITE_BUDGET_EXCEEDED",
        0,
        0,
        0,
        false,
        false,
        0,
        0,
        0,
        0,
        0,
        0,
        "SUITE_LATENCY",
        "suiteLatencyMs="
            + elapsedSuiteMs
            + " exceeded budget "
            + maxSuiteLatencyMs
            + "ms after "
            + completedCount
            + " prompts",
        0,
        elapsedSuiteMs);
  }

  /** Live eval latency budgets aligned with product turn timeout defaults. */
  public record LiveEvalBudget(long maxTurnLatencyMs, long maxSuiteLatencyMs) {
    public static LiveEvalBudget defaults() {
      return new LiveEvalBudget(300_000L, 1_800_000L);
    }

    public static LiveEvalBudget gateSuite() {
      return new LiveEvalBudget(300_000L, 900_000L);
    }
  }

  private void runStructuralStub(
      EvalPrompt prompt,
      ModelLevel level,
      List<AssistantModelProvider.ContextSnippet> snippets,
      EvalResult.Builder builder,
      long startedAt,
      int requiredContracts,
      int retrievedRequiredContracts,
      int sourceChunkCount) {
    boolean schemaReady =
        deltaSchemaFactory.responseSchema(level, prompt.requiredContracts()) != null;
    boolean retrievalOk = requiredContracts == 0 || retrievedRequiredContracts >= requiredContracts;
    boolean sourceOk =
        !prompt.requiresSourceAnalysis()
            || (sourceChunkCount > 0 && !prompt.sourceDocument().isBlank());
    boolean passed = schemaReady && retrievalOk && sourceOk;
    int coveredChunks =
        prompt.sourceDocument().isBlank()
            ? 0
            : new SourceEvidenceExtractor()
                .extract(
                    prompt.id(),
                    new SourceChunker()
                        .chunk(prompt.id(), prompt.sourceDocument(), 4000, 24)
                        .get(0))
                .coverage()
                .size();
    builder
        .turnKind("STRUCTURAL_STUB")
        .operationCount(0)
        .addElementCount(0)
        .connectionCount(0)
        .sourceAnalysisUsed(false)
        .sourceChunkCount(sourceChunkCount)
        .coveredSourceChunkCount(coveredChunks)
        .requiredContractCount(requiredContracts)
        .retrievedRequiredContractCount(retrievedRequiredContracts)
        .validationPassed(passed)
        .repairAttempts(0)
        .toolCalls(0)
        .providerWaitMs(0)
        .failureStage(passed ? "" : "STRUCTURAL_STUB")
        .failureMessage(
            passed
                ? ""
                : "schema=" + schemaReady + ", retrieval=" + retrievalOk + ", source=" + sourceOk)
        .latencyMs(System.currentTimeMillis() - startedAt);
  }

  private void runResilienceStub(EvalPrompt prompt, EvalResult.Builder builder, long startedAt) {
    String expected = prompt.expectedOutcome();
    boolean passed = false;
    String actual = "";
    if ("STALE_REVISION".equals(expected)) {
      actual =
          AgentErrorResolver.resolve(
                  new PlatformException(409, "The model changed while planning. Stale revision."),
                  "APPLY_PRECONDITION",
                  prompt.id())
              .code();
      passed = expected.equals(actual);
    } else if ("PROVIDER_TIMEOUT".equals(expected)) {
      actual =
          AgentErrorResolver.resolve(
                  new PlatformException(504, "Provider timed out."),
                  "MODELING_PROVIDER_CALL",
                  prompt.id())
              .code();
      passed = expected.equals(actual);
    } else if ("IGNORED_INSTRUCTION".equals(expected) && !prompt.sourceDocument().isBlank()) {
      var chunk = new SourceChunker().chunk(prompt.id(), prompt.sourceDocument(), 4000, 24).get(0);
      var graph = new SourceEvidenceExtractor().extract(prompt.id(), chunk);
      passed =
          graph.facts().stream()
              .anyMatch(fact -> "IGNORED_INSTRUCTION".equalsIgnoreCase(fact.kind()));
      actual = passed ? expected : "MISSING_IGNORED_INSTRUCTION";
    }
    builder
        .turnKind("RESILIENCE_STUB")
        .operationCount(0)
        .validationPassed(passed)
        .failureStage(passed ? "" : "RESILIENCE_STUB")
        .failureMessage(passed ? "" : "expected=" + expected + ", actual=" + actual)
        .latencyMs(System.currentTimeMillis() - startedAt);
  }

  private String sourceAnalysisPrompt(EvalPrompt prompt) {
    return """
    Eval source analysis for\
    """
        + prompt.id()
        + """
        . Return only one JSON object with elements and relationships. Classify each source fact
        into exact CIM EClasses and exact writable EReference names from the supplied contracts.
        Include enough actors, commands, events, policies, entities, information items, risks,
        assumptions, hotspots, goals, and relationships to satisfy the source document.
        """;
  }

  private List<AssistantModelProvider.ContextSnippet> canvasContextSnippets(
      ModelLevel level, JsonNode baseModel, Map<String, String> existingTypes) {
    if (baseModel == null
        || baseModel.isEmpty()
        || existingTypes == null
        || existingTypes.isEmpty()) {
      return List.of();
    }
    String rootId = baseModel.path("id").asText("");
    StringBuilder summary = new StringBuilder();
    existingTypes.entrySet().stream()
        .filter(entry -> !entry.getKey().equals(rootId))
        .sorted(Map.Entry.comparingByKey())
        .forEach(
            entry ->
                summary
                    .append("- ")
                    .append(entry.getKey())
                    .append(" (")
                    .append(entry.getValue())
                    .append(")\n"));
    if (summary.isEmpty()) {
      return List.of();
    }
    return List.of(
        new AssistantModelProvider.ContextSnippet(
            "canvas-context",
            "Current saved canvas elements",
            "Use these existing element IDs when extending the model:\n" + summary),
        new AssistantModelProvider.ContextSnippet(
            "canvas-root",
            "Canvas root id",
            "Model root id is "
                + (rootId.isBlank() ? "root" : rootId)
                + ". For root-owned elements set placement.ownerId to "
                + (rootId.isBlank() ? "\"root\"" : ("\"" + rootId + "\" or \"root\""))
                + " and use the exact root containment referenceName from the contracts."));
  }

  private String modelDeltaProtocol(ModelLevel level) {
    return modelDeltaProtocol(level, null);
  }

  private String modelDeltaProtocol(ModelLevel level, JsonNode baseModel) {
    return """
    Use the single provider-facing ModelDelta protocol. Return exactly one JSON object with these
    top-level fields only: intent, kind, message, questions, elements, references,
    attributeUpdates, deletions, assumptions. Do not use retired fields such as operations, op,
    patch, semanticPatch, modelSubset, jsonPatch, or xmi.

    For model changes, set intent=MUTATION and kind=MODEL_DELTA. Put each new model element in
    elements with localId, eClass, attributes, placement, and optional evidenceIds. placement is
    containment only and must use ownerId plus the exact containment referenceName from the Ecore
    contracts; use ownerId="root" for root-owned elements.     Put writable non-containment
    EReferences in references using the exact field name referenceName (never featureName).
    Put scalar EAttributes in attributes or attributeUpdates using attributeName.

    Use only EClasses and features from the supplied Ecore-derived contracts for level \
    """
        + level.apiName()
        + """
. Model source-backed facts with evidenceIds when source evidence is supplied. Do not
synthesize deletion unless the user explicitly asked for deletion.

"""
        + rootPlacementCheatSheet(level)
        + """

For CIM/event-storming turns, add explicit relationship references instead of isolated
elements. Common valid CIM links include Actor.issuesCommands -> Command,
Command.expectedEvents -> BusinessEvent, Command.rejectionEvents -> BusinessEvent,
Policy.triggeredBy -> BusinessEvent, Policy.guards -> Command, Policy.emitsCommands ->
Command, Policy.emitsEvents -> BusinessEvent, BusinessCapability.containsCommands ->
Command, BusinessCapability.containsEvents -> BusinessEvent, AggregateCandidate.handledCommands
-> Command, AggregateCandidate.emittedEvents -> BusinessEvent,
ExternalSystem.producedEvents -> BusinessEvent, and ExternalSystem.consumedEvents ->
BusinessEvent. Use these exact Ecore feature names and include at least several
source-backed references for source documents.
""";
  }

  private String rootPlacementCheatSheet(ModelLevel level) {
    return switch (level) {
      case CIM ->
          """
          Root containment referenceName cheat sheet (ownerId=root):
          Actor->actors, Command->commands, Role->roles, Risk->risks, BusinessEvent->businessEvents,
          Policy->policies, BusinessGoal->businessGoals, Assumption->assumptions.
          Never use the model root EClass (CIMModel) as an element eClass.
          """;
      case PIM ->
          """
          Root containment referenceName cheat sheet (ownerId=root):
          Workflow->workflows, Function->functions, Api->apis, EventChannel->eventChannels.
          Never use the model root EClass (PIMModel) as an element eClass.
          """;
      case PSM ->
          """
          Root containment referenceName cheat sheet (ownerId=root):
          SamStack->stacks, TraceModel->traceModel, AwsSecurityBaseline->securityBaselines,
          AwsNamingPolicy->namingPolicies.
          Never use model root EClasses (AwsPsmModel, PSMModel) as element eClass.
          """;
      default -> "";
    };
  }

  private String sourceAnalysisUserMessage(EvalPrompt prompt) {
    if (prompt.sourceDocument().isBlank()) {
      return prompt.prompt();
    }
    return prompt.prompt()
        + "\n\nAttached source document: "
        + prompt.id()
        + "\n\n"
        + prompt.sourceDocument();
  }

  private List<AssistantModelProvider.ContextSnippet> sourceAnalysisSnippets(EvalPrompt prompt) {
    List<AssistantModelProvider.ContextSnippet> snippets = new ArrayList<>();
    snippets.add(
        new AssistantModelProvider.ContextSnippet(
            "eval-source-document", prompt.id() + " source document", prompt.sourceDocument()));
    snippets.add(
        new AssistantModelProvider.ContextSnippet(
            "runtime-metamodel", "CIM language index", schemas.languageIndex(ModelLevel.CIM)));
    snippets.addAll(schemas.allPlanningContracts(ModelLevel.CIM).stream().limit(20).toList());
    return snippets;
  }

  private List<AssistantModelProvider.ContextSnippet> budgetSnippets(
      List<AssistantModelProvider.ContextSnippet> snippets) {
    List<AssistantModelProvider.ContextSnippet> result = new ArrayList<>();
    for (AssistantModelProvider.ContextSnippet snippet :
        snippets == null ? List.<AssistantModelProvider.ContextSnippet>of() : snippets) {
      int max =
          snippet.source().contains("json-schema")
              ? 7000
              : snippet.source().contains("source-analysis") ? 10000 : 2200;
      result.add(
          new AssistantModelProvider.ContextSnippet(
              snippet.source(), snippet.title(), compact(snippet.content(), max)));
      if (result.size() >= 18) {
        break;
      }
    }
    return result;
  }

  private String compact(String value, int maxChars) {
    if (value == null || value.length() <= maxChars) {
      return value == null ? "" : value;
    }
    return value.substring(0, Math.max(0, maxChars - 80))
        + "\n...[truncated for eval prompt budget; preserve most specific earlier facts]...";
  }

  private int count(SemanticModelPatch patch, SemanticModelPatch.OperationType type) {
    if (patch == null || type == null) {
      return 0;
    }
    return (int)
        patch.operations().stream()
            .filter(operation -> operation != null && operation.type() == type)
            .count();
  }

  private int countRequiredContracts(
      List<String> requiredContracts, List<AssistantModelProvider.ContextSnippet> snippets) {
    if (requiredContracts == null || requiredContracts.isEmpty()) {
      return 0;
    }
    String haystack =
        (snippets == null ? List.<AssistantModelProvider.ContextSnippet>of() : snippets)
            .stream()
                .map(snippet -> snippet.title() + "\n" + snippet.content())
                .reduce("", (left, right) -> left + "\n" + right)
                .toLowerCase(Locale.ROOT);
    int count = 0;
    for (String required : requiredContracts) {
      if (required != null && haystack.contains(required.toLowerCase(Locale.ROOT))) {
        count++;
      }
    }
    return count;
  }

  /** Prints a baseline report grouped by category. */
  public String baselineReport(List<EvalResult> results) {
    StringBuilder report = new StringBuilder("Assistant eval baseline\n");
    results.stream()
        .map(EvalResult::category)
        .distinct()
        .forEach(
            category -> {
              List<EvalResult> categoryResults =
                  results.stream().filter(result -> category.equals(result.category())).toList();
              long passed = categoryResults.stream().filter(EvalResult::validationPassed).count();
              double avgTools =
                  categoryResults.stream().mapToInt(EvalResult::toolCalls).average().orElse(0.0);
              report
                  .append("- ")
                  .append(category)
                  .append(": ")
                  .append(passed)
                  .append("/")
                  .append(categoryResults.size())
                  .append(" passed, avgToolCalls=")
                  .append(String.format(Locale.ROOT, "%.1f", avgTools))
                  .append('\n');
              categoryResults.stream()
                  .filter(result -> !result.validationPassed())
                  .forEach(
                      result ->
                          report
                              .append("  ")
                              .append(result.id())
                              .append(" failed at ")
                              .append(
                                  result.failureStage().isBlank()
                                      ? "UNKNOWN"
                                      : result.failureStage())
                              .append(": ")
                              .append(
                                  result.failureMessage().isBlank()
                                      ? "No failure message captured."
                                      : result.failureMessage())
                              .append('\n'));
            });
    return report.toString().trim();
  }

  /** Summarizes curated evals against the redesign quality gates. */
  public QualityGateReport qualityGateReport(List<EvalResult> results) {
    return qualityGateReport(results, QualityGateConfig.defaults());
  }

  /** Summarizes curated evals against explicit quality gates. */
  public QualityGateReport qualityGateReport(List<EvalResult> results, QualityGateConfig config) {
    List<EvalResult> rows = results == null ? List.of() : List.copyOf(results);
    QualityGateConfig gates = config == null ? QualityGateConfig.defaults() : config;
    int total = rows.size();
    long passed = rows.stream().filter(EvalResult::validationPassed).count();
    long modelDeltaEligible = rows.stream().filter(this::countsTowardModelDeltaSuccessGate).count();
    long modelDeltaSuccesses =
        rows.stream()
            .filter(this::countsTowardModelDeltaSuccessGate)
            .filter(EvalResult::validationPassed)
            .filter(result -> ModelDelta.Kind.MODEL_DELTA.name().equals(result.turnKind()))
            .count();
    long sourceChunks = rows.stream().mapToLong(EvalResult::sourceChunkCount).sum();
    long coveredSourceChunks = rows.stream().mapToLong(EvalResult::coveredSourceChunkCount).sum();
    long requiredContracts = rows.stream().mapToLong(EvalResult::requiredContractCount).sum();
    long retrievedContracts =
        rows.stream().mapToLong(EvalResult::retrievedRequiredContractCount).sum();
    double passRate = total == 0 ? 1.0 : (double) passed / total;
    double modelDeltaRate =
        modelDeltaEligible == 0 ? 1.0 : (double) modelDeltaSuccesses / modelDeltaEligible;
    double sourceCoverageRate =
        sourceChunks == 0 ? 1.0 : (double) coveredSourceChunks / sourceChunks;
    double retrievalRecallRate =
        requiredContracts == 0 ? 1.0 : (double) retrievedContracts / requiredContracts;
    double averageRepairAttempts =
        rows.stream().mapToInt(EvalResult::repairAttempts).average().orElse(0.0);
    double averageProviderCalls =
        rows.stream().mapToInt(EvalResult::toolCalls).average().orElse(0.0);
    long p95LatencyMs = percentileLatency(rows, 0.95);

    List<String> violations = new ArrayList<>();
    if (passRate < gates.minStructuralPassRate()) {
      violations.add(
          "structural pass rate "
              + percent(passRate)
              + " below "
              + percent(gates.minStructuralPassRate()));
    }
    if (modelDeltaRate < gates.minModelDeltaSuccessRate()) {
      violations.add(
          "ModelDelta success rate "
              + percent(modelDeltaRate)
              + " below "
              + percent(gates.minModelDeltaSuccessRate()));
    }
    if (sourceCoverageRate < gates.minSourceCoverageRate()) {
      violations.add(
          "source coverage "
              + percent(sourceCoverageRate)
              + " below "
              + percent(gates.minSourceCoverageRate()));
    }
    if (retrievalRecallRate < gates.minRetrievalRecallRate()) {
      violations.add(
          "retrieval contract recall "
              + percent(retrievalRecallRate)
              + " below "
              + percent(gates.minRetrievalRecallRate()));
    }
    if (averageRepairAttempts > gates.maxAverageRepairAttempts()) {
      violations.add(
          "average repair attempts "
              + String.format(Locale.ROOT, "%.2f", averageRepairAttempts)
              + " above "
              + String.format(Locale.ROOT, "%.2f", gates.maxAverageRepairAttempts()));
    }
    if (averageProviderCalls > gates.maxAverageProviderCalls()) {
      violations.add(
          "average provider calls "
              + String.format(Locale.ROOT, "%.2f", averageProviderCalls)
              + " above "
              + String.format(Locale.ROOT, "%.2f", gates.maxAverageProviderCalls()));
    }
    if (p95LatencyMs > gates.maxP95LatencyMs()) {
      violations.add("p95 latency " + p95LatencyMs + "ms above " + gates.maxP95LatencyMs() + "ms");
    }

    return new QualityGateReport(
        total,
        (int) passed,
        passRate,
        modelDeltaRate,
        sourceCoverageRate,
        retrievalRecallRate,
        averageRepairAttempts,
        averageProviderCalls,
        p95LatencyMs,
        violations);
  }

  /** Reports live-style latency and provider-call statistics for eval output. */
  public LatencyReport latencyReport(List<EvalResult> results) {
    List<EvalResult> rows = results == null ? List.of() : List.copyOf(results);
    double averageProviderCalls =
        rows.stream().mapToInt(EvalResult::toolCalls).average().orElse(0.0);
    long p50Total = percentileLatency(rows, 0.50);
    long p95Total = percentileLatency(rows, 0.95);
    long p50Provider = percentileProviderWait(rows, 0.50);
    long p95Provider = percentileProviderWait(rows, 0.95);
    long p50Backend = percentileBackendLatency(rows, 0.50);
    long p95Backend = percentileBackendLatency(rows, 0.95);
    return new LatencyReport(
        rows.size(),
        p50Total,
        p95Total,
        p50Provider,
        p95Provider,
        p50Backend,
        p95Backend,
        averageProviderCalls);
  }

  private long percentileLatency(List<EvalResult> results, double percentile) {
    return percentile(results, percentile, EvalResult::latencyMs);
  }

  private long percentileProviderWait(List<EvalResult> results, double percentile) {
    return percentile(results, percentile, EvalResult::providerWaitMs);
  }

  private long percentileBackendLatency(List<EvalResult> results, double percentile) {
    return percentile(
        results, percentile, result -> Math.max(0L, result.latencyMs() - result.providerWaitMs()));
  }

  private long percentile(
      List<EvalResult> results,
      double percentile,
      java.util.function.ToLongFunction<EvalResult> value) {
    if (results == null || results.isEmpty()) {
      return 0L;
    }
    List<Long> sorted =
        results.stream().map(value::applyAsLong).sorted(Comparator.naturalOrder()).toList();
    int index = (int) Math.ceil(percentile * sorted.size()) - 1;
    return sorted.get(Math.max(0, Math.min(sorted.size() - 1, index)));
  }

  private String percent(double value) {
    return String.format(Locale.ROOT, "%.1f%%", value * 100.0);
  }

  /** Binds tool context for live agent-loop eval runs. */
  public interface ToolBinder {
    void bind(ModelLevel level, EvalPrompt prompt);

    default void clear() {}

    /** Current canvas JSON used to validate and compile ModelDelta output. */
    default JsonNode baseModel() {
      return null;
    }

    /** Stable element IDs already on the canvas. */
    default Map<String, String> existingTypes() {
      return Map.of();
    }
  }

  /**
   * One curated eval prompt.
   *
   * @param id stable prompt id
   * @param category eval category
   * @param level modeling level
   * @param prompt user prompt
   * @param emptyCanvas whether the canvas starts empty
   * @param selectedElementIds optional selected elements
   * @param sourceDocument optional attached source material
   * @param minOperations minimum acceptable semantic operation count
   * @param minElementAdds minimum acceptable ADD_ELEMENT count
   * @param minConnections minimum acceptable CONNECT_ELEMENTS count
   * @param requiresSourceAnalysis whether the source-analysis phase must run
   */
  public record EvalPrompt(
      String id,
      String category,
      String level,
      String prompt,
      boolean emptyCanvas,
      List<String> selectedElementIds,
      String sourceDocument,
      List<String> requiredContracts,
      int minOperations,
      int minElementAdds,
      int minConnections,
      boolean requiresSourceAnalysis,
      String expectedOutcome,
      StructuralExpectations structuralExpectations) {

    public EvalPrompt(
        String id,
        String category,
        String level,
        String prompt,
        boolean emptyCanvas,
        List<String> selectedElementIds,
        String sourceDocument,
        List<String> requiredContracts,
        int minOperations,
        int minElementAdds,
        int minConnections,
        boolean requiresSourceAnalysis) {
      this(
          id,
          category,
          level,
          prompt,
          emptyCanvas,
          selectedElementIds,
          sourceDocument,
          requiredContracts,
          minOperations,
          minElementAdds,
          minConnections,
          requiresSourceAnalysis,
          "",
          StructuralExpectations.none());
    }

    public EvalPrompt(
        String id,
        String category,
        String level,
        String prompt,
        boolean emptyCanvas,
        List<String> selectedElementIds,
        String sourceDocument,
        List<String> requiredContracts,
        int minOperations,
        int minElementAdds,
        int minConnections,
        boolean requiresSourceAnalysis,
        String expectedOutcome) {
      this(
          id,
          category,
          level,
          prompt,
          emptyCanvas,
          selectedElementIds,
          sourceDocument,
          requiredContracts,
          minOperations,
          minElementAdds,
          minConnections,
          requiresSourceAnalysis,
          expectedOutcome,
          StructuralExpectations.none());
    }

    public EvalPrompt {
      selectedElementIds = selectedElementIds == null ? List.of() : List.copyOf(selectedElementIds);
      sourceDocument = sourceDocument == null ? "" : sourceDocument;
      requiredContracts = requiredContracts == null ? List.of() : List.copyOf(requiredContracts);
      expectedOutcome = expectedOutcome == null ? "" : expectedOutcome.trim();
      structuralExpectations =
          structuralExpectations == null ? StructuralExpectations.none() : structuralExpectations;
      minOperations = Math.max(minOperations, 0);
      minElementAdds = Math.max(minElementAdds, 0);
      minConnections = Math.max(minConnections, 0);
    }
  }

  /** Quality gate thresholds for assistant eval reports. */
  public record QualityGateConfig(
      double minStructuralPassRate,
      double minModelDeltaSuccessRate,
      double minSourceCoverageRate,
      double minRetrievalRecallRate,
      double maxAverageRepairAttempts,
      double maxAverageProviderCalls,
      long maxP95LatencyMs) {

    public static QualityGateConfig defaults() {
      return new QualityGateConfig(0.95, 1.0, 1.0, 0.90, 0.5, 3.0, 300_000L);
    }
  }

  /** Aggregate eval quality report. */
  public record QualityGateReport(
      int total,
      int passed,
      double structuralPassRate,
      double modelDeltaSuccessRate,
      double sourceCoverageRate,
      double retrievalRecallRate,
      double averageRepairAttempts,
      double averageProviderCalls,
      long p95LatencyMs,
      List<String> violations) {

    public QualityGateReport {
      violations = violations == null ? List.of() : List.copyOf(violations);
    }

    public boolean passedGates() {
      return violations.isEmpty();
    }

    public String summary() {
      return "quality="
          + (passedGates() ? "PASS" : "FAIL")
          + ", structural="
          + String.format(Locale.ROOT, "%.1f%%", structuralPassRate * 100.0)
          + ", modelDelta="
          + String.format(Locale.ROOT, "%.1f%%", modelDeltaSuccessRate * 100.0)
          + ", sourceCoverage="
          + String.format(Locale.ROOT, "%.1f%%", sourceCoverageRate * 100.0)
          + ", retrievalRecall="
          + String.format(Locale.ROOT, "%.1f%%", retrievalRecallRate * 100.0)
          + ", avgRepair="
          + String.format(Locale.ROOT, "%.2f", averageRepairAttempts)
          + ", avgProviderCalls="
          + String.format(Locale.ROOT, "%.2f", averageProviderCalls)
          + ", p95LatencyMs="
          + p95LatencyMs;
    }
  }

  /** Live/non-live eval latency summary. */
  public record LatencyReport(
      int total,
      long p50LatencyMs,
      long p95LatencyMs,
      long p50ProviderWaitMs,
      long p95ProviderWaitMs,
      long p50BackendLatencyMs,
      long p95BackendLatencyMs,
      double averageProviderCalls) {

    public String summary() {
      return "latency total="
          + total
          + ", p50="
          + p50LatencyMs
          + "ms, p95="
          + p95LatencyMs
          + "ms, providerP50="
          + p50ProviderWaitMs
          + "ms, providerP95="
          + p95ProviderWaitMs
          + "ms, backendP50="
          + p50BackendLatencyMs
          + "ms, backendP95="
          + p95BackendLatencyMs
          + "ms, avgProviderCalls="
          + String.format(Locale.ROOT, "%.2f", averageProviderCalls);
    }

    public String toJson() {
      return "{"
          + "\"total\":"
          + total
          + ",\"p50LatencyMs\":"
          + p50LatencyMs
          + ",\"p95LatencyMs\":"
          + p95LatencyMs
          + ",\"p50ProviderWaitMs\":"
          + p50ProviderWaitMs
          + ",\"p95ProviderWaitMs\":"
          + p95ProviderWaitMs
          + ",\"p50BackendLatencyMs\":"
          + p50BackendLatencyMs
          + ",\"p95BackendLatencyMs\":"
          + p95BackendLatencyMs
          + ",\"averageProviderCalls\":"
          + String.format(Locale.ROOT, "%.4f", averageProviderCalls)
          + "}";
    }
  }

  /** Golden structural expectations beyond operation counts. */
  public record StructuralExpectations(
      List<String> requiredTypeFamilies,
      int minRelationships,
      boolean requireEvidenceIds,
      boolean rejectPlaceholderOnlyNames) {

    public StructuralExpectations {
      requiredTypeFamilies =
          requiredTypeFamilies == null ? List.of() : List.copyOf(requiredTypeFamilies);
      minRelationships = Math.max(minRelationships, 0);
    }

    public static StructuralExpectations none() {
      return new StructuralExpectations(List.of(), 0, false, false);
    }
  }

  /**
   * One eval result row.
   *
   * @param id prompt id
   * @param category eval category
   * @param level modeling level
   * @param turnKind planner turn kind
   * @param operationCount semantic operation count
   * @param addElementCount ADD_ELEMENT count
   * @param connectionCount CONNECT_ELEMENTS count
   * @param sourceAnalysisUsed whether source analysis was run
   * @param validationPassed whether validation passed
   * @param repairAttempts repair passes used
   * @param toolCalls tool invocations
   * @param sourceChunkCount source chunks expected to be represented in the run
   * @param coveredSourceChunkCount source chunks represented by source analysis/evidence
   * @param requiredContractCount metamodel contracts expected by the fixture
   * @param retrievedRequiredContractCount expected metamodel contracts present in snippets
   * @param failureStage failure stage when unsuccessful
   * @param failureMessage optional failure message
   * @param providerWaitMs measured provider wait
   * @param latencyMs turn latency
   */
  public record EvalResult(
      String id,
      String category,
      String level,
      String turnKind,
      int operationCount,
      int addElementCount,
      int connectionCount,
      boolean sourceAnalysisUsed,
      boolean validationPassed,
      int repairAttempts,
      int toolCalls,
      int sourceChunkCount,
      int coveredSourceChunkCount,
      int requiredContractCount,
      int retrievedRequiredContractCount,
      String failureStage,
      String failureMessage,
      long providerWaitMs,
      long latencyMs) {

    static final class Builder {
      private final String id;
      private final String category;
      private final String level;
      private String turnKind = "";
      private int operationCount;
      private int addElementCount;
      private int connectionCount;
      private boolean sourceAnalysisUsed;
      private boolean validationPassed;
      private int repairAttempts;
      private int toolCalls;
      private int sourceChunkCount;
      private int coveredSourceChunkCount;
      private int requiredContractCount;
      private int retrievedRequiredContractCount;
      private String failureStage = "";
      private String failureMessage = "";
      private long providerWaitMs;
      private long latencyMs;

      Builder(String id, String category, String level) {
        this.id = id;
        this.category = category;
        this.level = level;
      }

      Builder turnKind(String turnKind) {
        this.turnKind = turnKind;
        return this;
      }

      Builder operationCount(int operationCount) {
        this.operationCount = operationCount;
        return this;
      }

      Builder addElementCount(int addElementCount) {
        this.addElementCount = addElementCount;
        return this;
      }

      Builder connectionCount(int connectionCount) {
        this.connectionCount = connectionCount;
        return this;
      }

      Builder sourceAnalysisUsed(boolean sourceAnalysisUsed) {
        this.sourceAnalysisUsed = sourceAnalysisUsed;
        return this;
      }

      Builder validationPassed(boolean validationPassed) {
        this.validationPassed = validationPassed;
        return this;
      }

      Builder repairAttempts(int repairAttempts) {
        this.repairAttempts = repairAttempts;
        return this;
      }

      Builder toolCalls(int toolCalls) {
        this.toolCalls = toolCalls;
        return this;
      }

      Builder sourceChunkCount(int sourceChunkCount) {
        this.sourceChunkCount = sourceChunkCount;
        return this;
      }

      Builder coveredSourceChunkCount(int coveredSourceChunkCount) {
        this.coveredSourceChunkCount = coveredSourceChunkCount;
        return this;
      }

      Builder requiredContractCount(int requiredContractCount) {
        this.requiredContractCount = requiredContractCount;
        return this;
      }

      Builder retrievedRequiredContractCount(int retrievedRequiredContractCount) {
        this.retrievedRequiredContractCount = retrievedRequiredContractCount;
        return this;
      }

      Builder failureStage(String failureStage) {
        this.failureStage = failureStage;
        return this;
      }

      Builder failureMessage(String failureMessage) {
        this.failureMessage = failureMessage;
        return this;
      }

      Builder providerWaitMs(long providerWaitMs) {
        this.providerWaitMs = providerWaitMs;
        return this;
      }

      Builder latencyMs(long latencyMs) {
        this.latencyMs = latencyMs;
        return this;
      }

      EvalResult build() {
        return new EvalResult(
            id,
            category,
            level,
            turnKind,
            operationCount,
            addElementCount,
            connectionCount,
            sourceAnalysisUsed,
            validationPassed,
            repairAttempts,
            toolCalls,
            sourceChunkCount,
            coveredSourceChunkCount,
            requiredContractCount,
            retrievedRequiredContractCount,
            failureStage,
            failureMessage,
            providerWaitMs,
            latencyMs);
      }
    }
  }
}
