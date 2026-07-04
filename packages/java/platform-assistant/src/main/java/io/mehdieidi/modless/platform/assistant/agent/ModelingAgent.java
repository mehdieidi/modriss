package io.mehdieidi.modless.platform.assistant.agent;

import com.fasterxml.jackson.databind.JsonNode;
import io.mehdieidi.modless.platform.assistant.delta.DeltaCompiler;
import io.mehdieidi.modless.platform.assistant.delta.DeltaNormalizer;
import io.mehdieidi.modless.platform.assistant.delta.ModelDelta;
import io.mehdieidi.modless.platform.assistant.delta.ModelDeltaParser;
import io.mehdieidi.modless.platform.assistant.delta.ModelDeltaSchemaFactory;
import io.mehdieidi.modless.platform.assistant.domain.AssistantTurnPlan;
import io.mehdieidi.modless.platform.assistant.domain.SemanticModelPatch;
import io.mehdieidi.modless.platform.assistant.domain.context.AssistantModelContextTypes.AssistantModelContext;
import io.mehdieidi.modless.platform.assistant.domain.context.AssistantModelContextTypes.ContextElement;
import io.mehdieidi.modless.platform.assistant.provider.AssistantModelProvider;
import io.mehdieidi.modless.platform.kernel.ModelLevel;
import io.mehdieidi.modless.platform.kernel.PlatformException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Unified modeling agent that uses ModelDelta as the only provider-facing mutation protocol. */
public class ModelingAgent {

  private final ModelDeltaSchemaFactory schemaFactory;
  private final ModelDeltaProviderClient providerClient;
  private final PromptContextBuilder prompts;
  private final DeltaCompiler compiler;
  private final ToolRegistry toolRegistry;

  public ModelingAgent(
      AssistantModelProvider provider,
      ModelDeltaSchemaFactory schemaFactory,
      ModelDeltaParser parser,
      DeltaCompiler compiler) {
    this(
        schemaFactory,
        new ModelDeltaProviderClient(
            provider,
            parser,
            new DeltaNormalizer(
                new io.mehdieidi.modless.platform.assistant.patch
                    .AssistantMetamodelSchemaService())),
        new PromptContextBuilder(new ContextBudget(24000, 8000, 32)),
        compiler,
        new ToolRegistry(24, 4, 8));
  }

  public ModelingAgent(
      ModelDeltaSchemaFactory schemaFactory,
      ModelDeltaProviderClient providerClient,
      PromptContextBuilder prompts,
      DeltaCompiler compiler) {
    this(schemaFactory, providerClient, prompts, compiler, new ToolRegistry(24, 4, 8));
  }

  public ModelingAgent(
      ModelDeltaSchemaFactory schemaFactory,
      ModelDeltaProviderClient providerClient,
      PromptContextBuilder prompts,
      DeltaCompiler compiler,
      ToolRegistry toolRegistry) {
    this.schemaFactory = schemaFactory;
    this.providerClient = providerClient;
    this.prompts = prompts;
    this.compiler = compiler;
    this.toolRegistry = toolRegistry == null ? new ToolRegistry(24, 4, 8) : toolRegistry;
  }

  /** Plans one mutation-capable turn through the ModelDelta contract. */
  public AgentLoopResult plan(
      ModelLevel level,
      JsonNode baseModel,
      AssistantModelContext context,
      AssistantModelProvider.AssistantPrompt prompt,
      AssistantModelProvider.AgentProgress progress) {
    if (progress != null) {
      progress.onProgress("PLANNING_MODEL_DELTA", "Drafting a structurally grounded ModelDelta");
    }
    ModelDelta delta = providerClient.complete(level, withModelDeltaProtocol(level, prompt, false));
    AssistantTurnPlan plan = toTurnPlan(level, baseModel, context, delta);
    return new AgentLoopResult(plan, 0, 1);
  }

  /** Plans one validation-guided repair pass through the same ModelDelta contract. */
  public AssistantTurnPlan repair(
      ModelLevel level,
      JsonNode baseModel,
      AssistantModelContext context,
      AssistantModelProvider.AssistantPrompt prompt) {
    ModelDelta delta = providerClient.complete(level, withModelDeltaProtocol(level, prompt, true));
    return toTurnPlan(level, baseModel, context, delta);
  }

  private AssistantTurnPlan toTurnPlan(
      ModelLevel level, JsonNode baseModel, AssistantModelContext context, ModelDelta delta) {
    SemanticModelPatch patch = compiler.compile(level, baseModel, existingTypes(context), delta);
    AssistantTurnPlan.Kind kind =
        patch.operations().isEmpty()
            ? switch (delta.kind()) {
              case CLARIFICATION -> AssistantTurnPlan.Kind.CLARIFICATION;
              case MODEL_DELTA -> AssistantTurnPlan.Kind.PATCH;
              case ANSWER -> AssistantTurnPlan.Kind.ANSWER;
            }
            : AssistantTurnPlan.Kind.PATCH;
    String assumptions =
        delta.assumptions().isEmpty()
            ? ""
            : "\n\nAssumptions:\n"
                + delta.assumptions().stream()
                    .map(value -> "- " + value)
                    .collect(Collectors.joining("\n"));
    return new AssistantTurnPlan(
        patch.operations().isEmpty() ? delta.intent() : AssistantTurnPlan.Intent.MUTATION,
        kind,
        delta.message() + assumptions,
        delta.questions(),
        patch);
  }

  private AssistantModelProvider.AssistantPrompt withModelDeltaProtocol(
      ModelLevel level, AssistantModelProvider.AssistantPrompt prompt, boolean repair) {
    return prompts.build(
        prompt,
        List.of(
            new PromptBlock("model-delta-protocol", guidance(repair), true),
            new PromptBlock("tool-registry", toolGuidance(), true)),
        schemaFactory.snippets(level),
        prompt.snippets());
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

  private String guidance(boolean repair) {
    return """
     Use the ModelDelta protocol for this turn. Return only one JSON object satisfying the
    ModelDeltaTurn JSON Schema. Do not emit retired mutation protocols, JSON Patch, XMI, prose
    wrappers, or Markdown fences.

     For changes, set kind=MODEL_DELTA and provide elements, references, attributeUpdates, and
     deletions. Each new element must include localId, eClass, attributes, and placement. placement
     is containment only: use ownerId="root" for root-owned elements, or a saved/local owner ID plus
     the exact containment referenceName from the Ecore contract. Put writable non-containment
     EReferences in references. Put scalar EAttributes in attributes or attributeUpdates.

     Deletion is allowed only when the user explicitly requested deletion. Model only facts grounded
     in the user request, selected model context, source evidence, or explicit assumptions.

     For CIM/event-storming turns, add explicit relationship references instead of isolated
     elements. Common valid CIM links include Actor.issuesCommands -> Command,
     Command.expectedEvents -> BusinessEvent, Command.rejectionEvents -> BusinessEvent,
     Policy.triggeredBy -> BusinessEvent, Policy.guards -> Command, Policy.emitsCommands ->
     Command, Policy.emitsEvents -> BusinessEvent, BusinessCapability.containsCommands -> Command,
     BusinessCapability.containsEvents -> BusinessEvent, AggregateCandidate.handledCommands ->
     Command, AggregateCandidate.emittedEvents -> BusinessEvent, ExternalSystem.producedEvents ->
     BusinessEvent,
     and ExternalSystem.consumedEvents -> BusinessEvent. Use these exact Ecore feature names.
    """
        + (repair
            ? "\nThis is a structural repair pass. Return a complete replacement ModelDelta that"
                + " fixes the reported contract failures. Do not repeat invalid fragments."
            : "");
  }

  private String toolGuidance() {
    return toolRegistry.availableTools().stream()
        .map(
            tool ->
                "- "
                    + tool.name()
                    + " -> "
                    + tool.implementationName()
                    + " ("
                    + (tool.readOnly() ? "read-only" : "non-persistent")
                    + "): "
                    + tool.description())
        .collect(Collectors.joining("\n"));
  }

  /** Fails fast for provider outputs that are not structured ModelDelta JSON. */
  public PlatformException schemaRejected(RuntimeException failure) {
    return new PlatformException(502, "AI assistant ModelDelta was rejected by schema parsing.");
  }

  /**
   * Agent loop result with planner output and loop metrics.
   *
   * @param plan structured turn plan
   * @param toolCalls number of tool invocations
   * @param steps number of agent loop steps
   */
  public record AgentLoopResult(AssistantTurnPlan plan, int toolCalls, int steps) {}
}
