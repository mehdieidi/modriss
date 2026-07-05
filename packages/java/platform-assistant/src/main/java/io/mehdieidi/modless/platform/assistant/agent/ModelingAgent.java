package io.mehdieidi.modless.platform.assistant.agent;

import com.fasterxml.jackson.databind.JsonNode;
import io.mehdieidi.modless.platform.assistant.delta.DeltaCompiler;
import io.mehdieidi.modless.platform.assistant.delta.ModelDelta;
import io.mehdieidi.modless.platform.assistant.delta.ModelDeltaSchemaFactory;
import io.mehdieidi.modless.platform.assistant.domain.AssistantTurnPlan;
import io.mehdieidi.modless.platform.assistant.domain.SemanticModelPatch;
import io.mehdieidi.modless.platform.assistant.domain.context.AssistantModelContextTypes.AssistantModelContext;
import io.mehdieidi.modless.platform.assistant.domain.context.AssistantModelContextTypes.ContextElement;
import io.mehdieidi.modless.platform.assistant.provider.AssistantModelProvider;
import io.mehdieidi.modless.platform.assistant.spi.AssistantSettings;
import io.mehdieidi.modless.platform.assistant.spi.AssistantToolBridge;
import io.mehdieidi.modless.platform.kernel.ModelLevel;
import io.mehdieidi.modless.platform.kernel.PlatformException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Unified modeling agent that uses ModelDelta as the only provider-facing mutation protocol. */
public class ModelingAgent {

  private final AssistantModelProvider provider;
  private final ModelDeltaSchemaFactory schemaFactory;
  private final ModelDeltaProviderClient providerClient;
  private final PromptContextBuilder prompts;
  private final DeltaCompiler compiler;
  private final ToolRegistry toolRegistry;
  private final AssistantToolBridge tools;
  private final AssistantSettings settings;

  public ModelingAgent(
      AssistantModelProvider provider,
      ModelDeltaSchemaFactory schemaFactory,
      ModelDeltaProviderClient providerClient,
      PromptContextBuilder prompts,
      DeltaCompiler compiler,
      AssistantSettings settings,
      AssistantToolBridge tools) {
    this(
        provider,
        schemaFactory,
        providerClient,
        prompts,
        compiler,
        tools,
        settings,
        new ToolRegistry(
            settings == null ? 24 : settings.maxToolCalls(),
            settings == null ? 4 : settings.maxToolCallsPerStep(),
            settings == null ? 8 : settings.maxAgentSteps()));
  }

  public ModelingAgent(
      AssistantModelProvider provider,
      ModelDeltaSchemaFactory schemaFactory,
      ModelDeltaProviderClient providerClient,
      PromptContextBuilder prompts,
      DeltaCompiler compiler,
      AssistantToolBridge tools,
      AssistantSettings settings,
      ToolRegistry toolRegistry) {
    this.provider = provider;
    this.schemaFactory = schemaFactory;
    this.providerClient = providerClient;
    this.prompts = prompts;
    this.compiler = compiler;
    this.tools = tools;
    this.settings = settings;
    this.toolRegistry = toolRegistry == null ? new ToolRegistry(24, 4, 8) : toolRegistry;
    if (tools
        instanceof io.mehdieidi.modless.platform.assistant.tools.AssistantToolService service) {
      service.bindToolRegistry(this.toolRegistry);
    }
  }

  /** Plans one mutation-capable turn through a bounded tool loop and ModelDelta commit. */
  public AgentLoopResult plan(
      ModelLevel level,
      JsonNode baseModel,
      AssistantModelContext context,
      AssistantModelProvider.AssistantPrompt prompt,
      AssistantModelProvider.AgentProgress progress,
      List<String> candidateTypes) {
    int steps = 0;
    int toolCalls = 0;
    if (shouldExplore(context, prompt)) {
      steps++;
      if (progress != null) {
        progress.onProgress(
            "QUERYING_METAMODEL", "Inspecting contracts and model context before drafting");
      }
      provider.completeWithTools(explorationPrompt(level, prompt, candidateTypes));
      toolCalls = Math.max(toolCalls, safeToolCount());
    }
    steps++;
    if (progress != null) {
      progress.onProgress("PLANNING_MODEL_DELTA", "Drafting a structurally grounded ModelDelta");
    }
    if (steps > (settings == null ? 8 : settings.maxAgentSteps())) {
      throw new PlatformException(429, "Assistant agent step budget exceeded.");
    }
    ModelDelta delta =
        providerClient.complete(
            level, withModelDeltaProtocol(level, prompt, candidateTypes, false));
    toolCalls = Math.max(toolCalls, safeToolCount());
    CompileAttempt compiled = compileAttempt(level, baseModel, context, delta);
    AssistantTurnPlan plan = toTurnPlan(delta, compiled.patch());
    return new AgentLoopResult(plan, delta, compiled.error(), toolCalls, steps);
  }

  public AgentLoopResult plan(
      ModelLevel level,
      JsonNode baseModel,
      AssistantModelContext context,
      AssistantModelProvider.AssistantPrompt prompt,
      AssistantModelProvider.AgentProgress progress) {
    return plan(level, baseModel, context, prompt, progress, List.of());
  }

  /** Plans one validation-guided repair pass through the same ModelDelta contract. */
  public AgentLoopResult repair(
      ModelLevel level,
      JsonNode baseModel,
      AssistantModelContext context,
      AssistantModelProvider.AssistantPrompt prompt,
      List<String> candidateTypes) {
    ModelDelta delta =
        providerClient.complete(level, withModelDeltaProtocol(level, prompt, candidateTypes, true));
    CompileAttempt compiled = compileAttempt(level, baseModel, context, delta);
    return new AgentLoopResult(toTurnPlan(delta, compiled.patch()), delta, compiled.error(), 0, 1);
  }

  public AgentLoopResult repair(
      ModelLevel level,
      JsonNode baseModel,
      AssistantModelContext context,
      AssistantModelProvider.AssistantPrompt prompt) {
    return repair(level, baseModel, context, prompt, List.of());
  }

  private int safeToolCount() {
    return tools == null ? 0 : tools.consumeToolCallCount();
  }

  private boolean shouldExplore(
      AssistantModelContext context, AssistantModelProvider.AssistantPrompt prompt) {
    int snippets = prompt == null || prompt.snippets() == null ? 0 : prompt.snippets().size();
    int reserved = settings == null ? 10 : settings.reservedSchemaSnippets();
    if (hasSourceEvidenceSnippets(prompt)) {
      return false;
    }
    if (context != null && !context.elements().isEmpty()) {
      return snippets < Math.max(4, reserved / 2);
    }
    if (snippets >= reserved) {
      return false;
    }
    return snippets < Math.max(4, reserved);
  }

  private boolean hasSourceEvidenceSnippets(AssistantModelProvider.AssistantPrompt prompt) {
    if (prompt == null || prompt.snippets() == null) {
      return false;
    }
    return prompt.snippets().stream()
        .anyMatch(
            snippet ->
                snippet != null
                    && snippet.source() != null
                    && (snippet.source().startsWith("source-")
                        || snippet.source().contains("source-evidence")
                        || "source-document".equals(snippet.source())));
  }

  private AssistantModelProvider.AssistantPrompt explorationPrompt(
      ModelLevel level,
      AssistantModelProvider.AssistantPrompt prompt,
      List<String> candidateTypes) {
    return prompts.build(
        new AssistantModelProvider.AssistantPrompt(
            prompt.role(), prompt.system(), prompt.user(), List.of()),
        List.of(
            new PromptBlock(
                PromptLabels.METHODOLOGY_NOTE,
                """
                Use read-only tools to inspect exact Ecore contracts, containment options, and the
                active model neighborhood before the final ModelDelta is drafted. Do not emit
                ModelDelta JSON in this exploration step.
                """
                    + "\n\n"
                    + toolGuidance(),
                true)),
        schemaFactory.snippets(level, candidateTypes),
        prompt.snippets());
  }

  private CompileAttempt compileAttempt(
      ModelLevel level, JsonNode baseModel, AssistantModelContext context, ModelDelta delta) {
    try {
      return new CompileAttempt(
          compiler.compile(level, baseModel, existingTypes(context), delta), null);
    } catch (PlatformException failure) {
      return new CompileAttempt(new SemanticModelPatch(List.of()), failure.getMessage());
    }
  }

  private AssistantTurnPlan toTurnPlan(ModelDelta delta, SemanticModelPatch patch) {
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
      ModelLevel level,
      AssistantModelProvider.AssistantPrompt prompt,
      List<String> candidateTypes,
      boolean repair) {
    return prompts.build(
        prompt,
        List.of(
            new PromptBlock(PromptLabels.METAMODEL_CONTRACT, guidance(repair), true),
            new PromptBlock(PromptLabels.METHODOLOGY_NOTE, toolGuidance(), false)),
        schemaFactory.snippets(level, candidateTypes),
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
     Command, AggregateCandidate.emittedEvents -> BusinessEvent, AggregateCandidate.root/members ->
     DomainEntity, BusinessCapability.entities/CIMModel.entities contain DomainEntity children,
     ExternalSystem.producedEvents ->
     BusinessEvent, ExternalSystem.consumedEvents -> BusinessEvent, BusinessGoal.owners ->
     Stakeholder (never Actor), and Stakeholder.ownsGoals -> BusinessGoal. Model actors and
     stakeholders separately; link actors to commands/events, stakeholders to goals/requirements.
     Use these exact Ecore feature names.

     For PIM/serverless backend turns, model services, APIs, functions, contracts, data stores,
     events, and workflows as connected structures — not isolated boxes. Common valid PIM links
     include PIMModel.services -> ServerlessService, ServerlessService.ownsFunctions -> Function,
     Function.contract -> FunctionContract, PIMModel.apis -> Api, Api.routes -> ApiRoute,
     PIMModel.dataStores -> DataStore, PIMModel.channels -> EventChannel, PIMModel.eventTypes ->
     EventType, PIMModel.workflows -> Workflow, and ApiRoute.functionIntegration -> Function.
     Every Function needs its required FunctionContract in the same patch. Connect API routes to
     functions and wire event types where the domain requires integration. Use exact EClass names
     such as ServerlessService, EventType, and FunctionContract — never invented labels or
     shortened aliases like Event or Service.

     Do not ask the user to confirm backend retrieval, metamodel contracts, or tool usage. The
     backend already assembled exact Ecore contracts, source evidence, and model context for this
     turn. When attachment content or source evidence snippets are present, treat them as the
     authoritative source text; never return kind=ANSWER claiming the source document is missing.
     Unless a domain fact is genuinely missing from all supplied context, proceed with
     kind=MODEL_DELTA and list any assumptions instead of returning CLARIFICATION.
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

  public PlatformException schemaRejected(RuntimeException failure) {
    return new PlatformException(502, "AI assistant ModelDelta was rejected by schema parsing.");
  }

  public record AgentLoopResult(
      AssistantTurnPlan plan, ModelDelta delta, String compileError, int toolCalls, int steps) {}

  private record CompileAttempt(SemanticModelPatch patch, String error) {}
}
