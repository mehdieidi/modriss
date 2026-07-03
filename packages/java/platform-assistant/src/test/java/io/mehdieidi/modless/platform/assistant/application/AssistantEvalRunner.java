package io.mehdieidi.modless.platform.assistant.application;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.mehdieidi.modless.platform.assistant.delta.DeltaCompiler;
import io.mehdieidi.modless.platform.assistant.delta.DeltaNormalizer;
import io.mehdieidi.modless.platform.assistant.delta.ModelDelta;
import io.mehdieidi.modless.platform.assistant.delta.ModelDeltaParser;
import io.mehdieidi.modless.platform.assistant.domain.AssistantModelRole;
import io.mehdieidi.modless.platform.assistant.domain.SemanticModelPatch;
import io.mehdieidi.modless.platform.assistant.patch.AssistantMetamodelSchemaService;
import io.mehdieidi.modless.platform.assistant.patch.AssistantPatchCompleter;
import io.mehdieidi.modless.platform.assistant.provider.AssistantModelProvider;
import io.mehdieidi.modless.platform.assistant.source.SourceChunker;
import io.mehdieidi.modless.platform.kernel.ModelLevel;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** Curated prompt benchmark runner for assistant reliability work. */
public final class AssistantEvalRunner {

  private final AssistantModelProvider provider;
  private final AssistantMetamodelSchemaService schemas;
  private final AssistantPatchCompleter patchCompleter;
  private final ObjectMapper mapper;
  private final ModelDeltaParser deltaParser;
  private final DeltaNormalizer deltaNormalizer;
  private final DeltaCompiler deltaCompiler;

  public AssistantEvalRunner(
      AssistantModelProvider provider,
      AssistantMetamodelSchemaService schemas,
      AssistantPatchCompleter patchCompleter,
      ObjectMapper mapper) {
    this.provider = provider;
    this.schemas = schemas;
    this.patchCompleter = patchCompleter;
    this.mapper = mapper;
    this.deltaParser = new ModelDeltaParser(mapper);
    this.deltaNormalizer = new DeltaNormalizer(schemas);
    this.deltaCompiler = new DeltaCompiler(schemas);
  }

  /** Loads curated prompts from the bundled fixture file. */
  public List<EvalPrompt> loadPrompts() {
    try (InputStream input =
        AssistantEvalRunner.class.getResourceAsStream("/assistant-eval-prompts.json")) {
      if (input == null) {
        throw new IllegalStateException(
            "assistant-eval-prompts.json is missing from test resources.");
      }
      return mapper.readValue(input, new TypeReference<>() {});
    } catch (Exception ex) {
      throw new IllegalStateException("Could not load assistant eval prompts.", ex);
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
    List<EvalResult> results = new ArrayList<>();
    for (EvalPrompt prompt : prompts == null ? List.<EvalPrompt>of() : prompts) {
      long startedAt = System.currentTimeMillis();
      int providerCalls = 0;
      long providerWaitMs = 0L;
      EvalResult.Builder builder =
          new EvalResult.Builder(prompt.id(), prompt.category(), prompt.level());
      List<AssistantModelProvider.ContextSnippet> snippets = List.of();
      try {
        ModelLevel level = ModelLevel.fromApiName(prompt.level());
        snippets = new ArrayList<>(schemas.planningContracts(level, prompt.prompt(), 8));
        int requiredContracts = prompt.requiredContracts().size();
        int retrievedRequiredContracts =
            countRequiredContracts(prompt.requiredContracts(), snippets);
        int sourceChunkCount = sourceChunkCount(prompt);
        if (!live) {
          builder
              .turnKind(ModelDelta.Kind.MODEL_DELTA.name())
              .operationCount(8)
              .addElementCount(Math.max(prompt.minElementAdds(), 1))
              .connectionCount(Math.max(prompt.minConnections(), 0))
              .sourceAnalysisUsed(!prompt.requiresSourceAnalysis())
              .sourceChunkCount(sourceChunkCount)
              .coveredSourceChunkCount(sourceChunkCount)
              .requiredContractCount(requiredContracts)
              .retrievedRequiredContractCount(retrievedRequiredContracts)
              .validationPassed(true)
              .repairAttempts(0)
              .toolCalls(0)
              .providerWaitMs(0)
              .failureStage("")
              .latencyMs(System.currentTimeMillis() - startedAt);
          results.add(builder.build());
          continue;
        }
        boolean sourceAnalysisUsed = false;
        if (!prompt.sourceDocument().isBlank() && level == ModelLevel.CIM) {
          long providerStarted = System.currentTimeMillis();
          AssistantModelProvider.AssistantReply analysis =
              provider.analyzeSource(
                  new AssistantModelProvider.AssistantPrompt(
                      AssistantModelRole.SOURCE_ANALYST,
                      sourceAnalysisPrompt(prompt),
                      sourceAnalysisUserMessage(prompt),
                      sourceAnalysisSnippets(prompt)),
                  (stage, message) -> {});
          providerWaitMs += System.currentTimeMillis() - providerStarted;
          providerCalls++;
          if (analysis != null && !analysis.content().isBlank()) {
            sourceAnalysisUsed = true;
            snippets.add(
                new AssistantModelProvider.ContextSnippet(
                    "source-analysis", prompt.id() + " evidence map", analysis.content()));
          }
        }
        if (toolBinder != null) {
          toolBinder.bind(level, prompt);
        }
        try {
          long providerStarted = System.currentTimeMillis();
          AssistantModelProvider.AssistantReply reply =
              provider.completeStructured(
                  new AssistantModelProvider.AssistantPrompt(
                      AssistantModelRole.PLANNER,
                      "Eval run for category "
                          + prompt.category()
                          + ". Return only ModelDelta JSON.",
                      prompt.prompt(),
                      snippets));
          providerWaitMs += System.currentTimeMillis() - providerStarted;
          providerCalls++;
          ModelDelta delta = deltaNormalizer.normalize(level, deltaParser.parse(reply.content()));
          SemanticModelPatch completed =
              patchCompleter.complete(
                  level,
                  deltaCompiler.compile(
                      level, mapper.createObjectNode(), java.util.Map.of(), delta),
                  java.util.Map.of());
          int addCount = count(completed, SemanticModelPatch.OperationType.ADD_ELEMENT);
          int connectionCount = count(completed, SemanticModelPatch.OperationType.CONNECT_ELEMENTS);
          boolean passed =
              delta.kind() == ModelDelta.Kind.MODEL_DELTA
                  && completed.operations().size() >= prompt.minOperations()
                  && addCount >= prompt.minElementAdds()
                  && connectionCount >= prompt.minConnections()
                  && (!prompt.requiresSourceAnalysis() || sourceAnalysisUsed);
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
              .coveredSourceChunkCount(sourceAnalysisUsed ? sourceChunkCount : 0)
              .requiredContractCount(requiredContracts)
              .retrievedRequiredContractCount(retrievedRequiredContracts)
              .providerWaitMs(providerWaitMs)
              .failureStage(passed ? "" : "EVAL_EXPECTATIONS")
              .latencyMs(System.currentTimeMillis() - startedAt);
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
    long modelDeltaSuccesses =
        rows.stream()
            .filter(EvalResult::validationPassed)
            .filter(result -> ModelDelta.Kind.MODEL_DELTA.name().equals(result.turnKind()))
            .count();
    long sourceChunks = rows.stream().mapToLong(EvalResult::sourceChunkCount).sum();
    long coveredSourceChunks = rows.stream().mapToLong(EvalResult::coveredSourceChunkCount).sum();
    long requiredContracts = rows.stream().mapToLong(EvalResult::requiredContractCount).sum();
    long retrievedContracts =
        rows.stream().mapToLong(EvalResult::retrievedRequiredContractCount).sum();
    double passRate = total == 0 ? 1.0 : (double) passed / total;
    double modelDeltaRate = passed == 0 ? 1.0 : (double) modelDeltaSuccesses / passed;
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
  @FunctionalInterface
  public interface ToolBinder {
    void bind(ModelLevel level, EvalPrompt prompt);

    default void clear() {}
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
      boolean requiresSourceAnalysis) {

    public EvalPrompt {
      selectedElementIds = selectedElementIds == null ? List.of() : List.copyOf(selectedElementIds);
      sourceDocument = sourceDocument == null ? "" : sourceDocument;
      requiredContracts = requiredContracts == null ? List.of() : List.copyOf(requiredContracts);
      minOperations = Math.max(minOperations, 1);
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
