package io.mehdieidi.modless.platform.assistant.subset;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.mehdieidi.modless.platform.assistant.domain.AssistantTurnPlan;
import io.mehdieidi.modless.platform.assistant.domain.SemanticModelPatch;
import io.mehdieidi.modless.platform.assistant.domain.context.AssistantModelContextTypes.AssistantModelContext;
import io.mehdieidi.modless.platform.assistant.domain.context.AssistantModelContextTypes.ContextElement;
import io.mehdieidi.modless.platform.assistant.provider.AssistantModelProvider;
import io.mehdieidi.modless.platform.kernel.ModelLevel;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** LLM planner that asks for partial model subsets and compiles them deterministically. */
public class AssistantModelSubsetPlanner {

  private final AssistantModelProvider provider;
  private final ModelSubsetSchemaService schemaService;
  private final ModelSubsetPlanParser parser;
  private final ModelSubsetPatchCompiler compiler;

  public AssistantModelSubsetPlanner(
      AssistantModelProvider provider,
      io.mehdieidi.modless.platform.assistant.patch.AssistantMetamodelSchemaService schemas,
      ObjectMapper mapper) {
    this.provider = provider;
    this.schemaService = new ModelSubsetSchemaService(schemas, mapper);
    this.parser = new ModelSubsetPlanParser(mapper);
    this.compiler = new ModelSubsetPatchCompiler(schemas);
  }

  /** Produces a structured turn plan through the JSON model-subset protocol. */
  public AssistantModelProvider.AgentLoopResult plan(
      ModelLevel level,
      JsonNode baseModel,
      AssistantModelContext context,
      AssistantModelProvider.AssistantPrompt prompt,
      AssistantModelProvider.AgentProgress progress) {
    if (progress != null) {
      progress.onProgress("PLANNING_SUBSET", "Drafting a JSON model subset from the schema");
    }
    AssistantModelProvider.AssistantPrompt subsetPrompt =
        new AssistantModelProvider.AssistantPrompt(
            prompt.role(),
            prompt.system() + "\n\n" + subsetProtocolGuidance(false),
            prompt.user(),
            withSubsetSchema(level, prompt.snippets()));
    AssistantModelProvider.AssistantReply reply = provider.completeStructured(subsetPrompt);
    return new AssistantModelProvider.AgentLoopResult(
        toTurnPlan(level, baseModel, context, parser.parse(reply.content())), 0, 1);
  }

  /** Produces a replacement turn plan for validation-guided subset repair. */
  public AssistantTurnPlan repair(
      ModelLevel level,
      JsonNode baseModel,
      AssistantModelContext context,
      AssistantModelProvider.AssistantPrompt prompt) {
    AssistantModelProvider.AssistantPrompt subsetPrompt =
        new AssistantModelProvider.AssistantPrompt(
            prompt.role(),
            prompt.system() + "\n\n" + subsetProtocolGuidance(true),
            prompt.user(),
            withSubsetSchema(level, prompt.snippets()));
    AssistantModelProvider.AssistantReply reply = provider.completeStructured(subsetPrompt);
    return toTurnPlan(level, baseModel, context, parser.parse(reply.content()));
  }

  private AssistantTurnPlan toTurnPlan(
      ModelLevel level,
      JsonNode baseModel,
      AssistantModelContext context,
      ModelSubsetTurnPlan subsetPlan) {
    SemanticModelPatch patch =
        compiler.compile(level, baseModel, existingTypes(context), subsetPlan.subset());
    AssistantTurnPlan.Kind kind =
        patch.operations().isEmpty()
            ? switch (subsetPlan.kind()) {
              case CLARIFICATION -> AssistantTurnPlan.Kind.CLARIFICATION;
              case MODEL_SUBSET -> AssistantTurnPlan.Kind.PATCH;
              case ANSWER -> AssistantTurnPlan.Kind.ANSWER;
            }
            : AssistantTurnPlan.Kind.PATCH;
    return new AssistantTurnPlan(
        patch.operations().isEmpty() ? subsetPlan.intent() : AssistantTurnPlan.Intent.MUTATION,
        kind,
        subsetPlan.message(),
        subsetPlan.questions(),
        patch);
  }

  private Map<String, String> existingTypes(AssistantModelContext context) {
    if (context == null) {
      return Map.of();
    }
    return context.elements().stream()
        .collect(
            Collectors.toMap(
                ContextElement::id,
                ContextElement::type,
                (left, right) -> left,
                LinkedHashMap::new));
  }

  private List<AssistantModelProvider.ContextSnippet> withSubsetSchema(
      ModelLevel level, List<AssistantModelProvider.ContextSnippet> snippets) {
    List<AssistantModelProvider.ContextSnippet> result = new ArrayList<>();
    result.addAll(schemaService.snippets(level));
    if (snippets != null) {
      result.addAll(snippets);
    }
    return List.copyOf(result);
  }

  private String subsetProtocolGuidance(boolean repair) {
    return """
    Use the JSON model-subset protocol for this turn. Return only one JSON object satisfying the
    provided ModlessModelSubsetTurn JSON Schema. Do not emit semantic operations, JSON Pointer
    patches, XMI, prose wrappers, or Markdown fences.

    For model changes, set kind=MODEL_SUBSET and provide subset.elements plus subset.references.
    The subset is a mergeable partial graph: include only the current work slice, but make that
    slice structurally complete. Use localId values for new elements and reuse them in
    containedBy.ownerId and references. Use ownerId="root" for root-owned elements when the
    contract shows a root containment. Use existing stable IDs only when connecting to existing
    model context. Put all scalar, enum, and array EAttributes inside attributes with exact feature
    names. Put containment through containedBy, and writable non-containment EReferences through
    references. Include nested required contained elements in the same subset. For large requests,
    create a substantial coherent subset with many connected elements, then later turns can add
    another subset that connects to this one.

    Ask clarification only for consequential domain conflicts. Otherwise choose conservative
    defaults from the schema and mention assumptions in message.
    """
        + (repair
            ? "\nThis is a repair pass. Return a complete replacement MODEL_SUBSET that fixes every"
                + " validator issue; do not repeat the rejected invalid subset."
            : "");
  }
}
