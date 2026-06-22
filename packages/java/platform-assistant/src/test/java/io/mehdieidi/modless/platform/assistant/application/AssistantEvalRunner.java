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
    List<EvalResult> results = new ArrayList<>();
    for (EvalPrompt prompt : loadPrompts()) {
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
          builder
              .turnKind(plan.kind().name())
              .operationCount(completed.operations().size())
              .validationPassed(plan.kind() == AssistantTurnPlan.Kind.PATCH)
              .repairAttempts(0)
              .toolCalls(loopResult.toolCalls())
              .failureStage(plan.kind() == AssistantTurnPlan.Kind.PATCH ? "" : "PLANNING")
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
   */
  public record EvalPrompt(
      String id,
      String category,
      String level,
      String prompt,
      boolean emptyCanvas,
      List<String> selectedElementIds) {

    public EvalPrompt {
      selectedElementIds = selectedElementIds == null ? List.of() : List.copyOf(selectedElementIds);
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
