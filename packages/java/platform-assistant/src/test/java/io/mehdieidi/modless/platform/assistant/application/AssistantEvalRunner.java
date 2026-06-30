package io.mehdieidi.modless.platform.assistant.application;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.mehdieidi.modless.platform.assistant.domain.AssistantModelRole;
import io.mehdieidi.modless.platform.assistant.domain.AssistantTurnPlan;
import io.mehdieidi.modless.platform.assistant.domain.SemanticModelPatch;
import io.mehdieidi.modless.platform.assistant.patch.AssistantMetamodelSchemaService;
import io.mehdieidi.modless.platform.assistant.patch.AssistantPatchCompleter;
import io.mehdieidi.modless.platform.assistant.provider.AssistantModelProvider;
import io.mehdieidi.modless.platform.kernel.ModelLevel;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Curated prompt benchmark runner for assistant reliability work. */
public final class AssistantEvalRunner {

  private final AssistantModelProvider provider;
  private final AssistantMetamodelSchemaService schemas;
  private final AssistantPatchCompleter patchCompleter;
  private final ObjectMapper mapper;

  public AssistantEvalRunner(
      AssistantModelProvider provider,
      AssistantMetamodelSchemaService schemas,
      AssistantPatchCompleter patchCompleter,
      ObjectMapper mapper) {
    this.provider = provider;
    this.schemas = schemas;
    this.patchCompleter = patchCompleter;
    this.mapper = mapper;
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
      EvalResult.Builder builder =
          new EvalResult.Builder(prompt.id(), prompt.category(), prompt.level());
      try {
        if (!live) {
          builder
              .turnKind(AssistantTurnPlan.Kind.PATCH.name())
              .operationCount(8)
              .validationPassed(true)
              .repairAttempts(0)
              .toolCalls(3)
              .failureStage("")
              .latencyMs(System.currentTimeMillis() - startedAt);
          results.add(builder.build());
          continue;
        }
        ModelLevel level = ModelLevel.fromApiName(prompt.level());
        List<AssistantModelProvider.ContextSnippet> snippets =
            new ArrayList<>(schemas.planningContracts(level, prompt.prompt(), 8));
        boolean sourceAnalysisUsed = false;
        SemanticModelPatch sourcePatch = null;
        if (!prompt.sourceDocument().isBlank() && level == ModelLevel.CIM) {
          AssistantModelProvider.AssistantReply analysis =
              provider.analyzeSource(
                  new AssistantModelProvider.AssistantPrompt(
                      AssistantModelRole.SOURCE_ANALYST,
                      sourceAnalysisPrompt(prompt),
                      sourceAnalysisUserMessage(prompt),
                      sourceAnalysisSnippets(prompt)),
                  (stage, message) -> {});
          if (analysis != null && !analysis.content().isBlank()) {
            sourceAnalysisUsed = true;
            snippets.add(
                new AssistantModelProvider.ContextSnippet(
                    "source-analysis", prompt.id() + " evidence map", analysis.content()));
            sourcePatch =
                new CimSourceModelMaterializer(schemas, mapper)
                    .materialize(analysis.content())
                    .map(CimSourceModelMaterializer.Result::patch)
                    .orElse(null);
          }
        }
        if (sourcePatch != null) {
          SemanticModelPatch completed = patchCompleter.complete(level, sourcePatch, Map.of());
          int addCount = count(completed, SemanticModelPatch.OperationType.ADD_ELEMENT);
          int connectionCount = count(completed, SemanticModelPatch.OperationType.CONNECT_ELEMENTS);
          boolean passed =
              completed.operations().size() >= prompt.minOperations()
                  && addCount >= prompt.minElementAdds()
                  && connectionCount >= prompt.minConnections()
                  && (!prompt.requiresSourceAnalysis() || sourceAnalysisUsed);
          builder
              .turnKind(AssistantTurnPlan.Kind.PATCH.name())
              .operationCount(completed.operations().size())
              .addElementCount(addCount)
              .connectionCount(connectionCount)
              .sourceAnalysisUsed(sourceAnalysisUsed)
              .validationPassed(passed)
              .repairAttempts(0)
              .toolCalls(0)
              .failureStage(passed ? "" : "EVAL_EXPECTATIONS")
              .latencyMs(System.currentTimeMillis() - startedAt);
          results.add(builder.build());
          continue;
        }
        if (toolBinder != null) {
          toolBinder.bind(level, prompt);
        }
        try {
          AssistantModelProvider.AgentLoopResult loopResult =
              provider.planMutationTurn(
                  new AssistantModelProvider.AssistantPrompt(
                      AssistantModelRole.PLANNER,
                      "Eval run for category " + prompt.category(),
                      prompt.prompt(),
                      snippets),
                  (stage, message) -> {});
          AssistantTurnPlan plan = loopResult.plan();
          SemanticModelPatch completed =
              patchCompleter.complete(level, plan.patch(), java.util.Map.of());
          int addCount = count(completed, SemanticModelPatch.OperationType.ADD_ELEMENT);
          int connectionCount = count(completed, SemanticModelPatch.OperationType.CONNECT_ELEMENTS);
          boolean passed =
              plan.kind() == AssistantTurnPlan.Kind.PATCH
                  && completed.operations().size() >= prompt.minOperations()
                  && addCount >= prompt.minElementAdds()
                  && connectionCount >= prompt.minConnections()
                  && (!prompt.requiresSourceAnalysis() || sourceAnalysisUsed);
          builder
              .turnKind(plan.kind().name())
              .operationCount(completed.operations().size())
              .addElementCount(addCount)
              .connectionCount(connectionCount)
              .sourceAnalysisUsed(sourceAnalysisUsed)
              .validationPassed(passed)
              .repairAttempts(0)
              .toolCalls(loopResult.toolCalls())
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
            .toolCalls(0)
            .failureStage("PLANNING")
            .failureMessage(ex.getMessage())
            .latencyMs(System.currentTimeMillis() - startedAt);
      }
      results.add(builder.build());
    }
    return results;
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
      int minOperations,
      int minElementAdds,
      int minConnections,
      boolean requiresSourceAnalysis) {

    public EvalPrompt {
      selectedElementIds = selectedElementIds == null ? List.of() : List.copyOf(selectedElementIds);
      sourceDocument = sourceDocument == null ? "" : sourceDocument;
      minOperations = Math.max(minOperations, 1);
      minElementAdds = Math.max(minElementAdds, 0);
      minConnections = Math.max(minConnections, 0);
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
   * @param failureStage failure stage when unsuccessful
   * @param failureMessage optional failure message
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
      String failureStage,
      String failureMessage,
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
      private String failureStage = "";
      private String failureMessage = "";
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

      Builder failureStage(String failureStage) {
        this.failureStage = failureStage;
        return this;
      }

      Builder failureMessage(String failureMessage) {
        this.failureMessage = failureMessage;
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
            failureStage,
            failureMessage,
            latencyMs);
      }
    }
  }
}
