package io.mehdieidi.modless.platform.assistant.agent;

import io.mehdieidi.modless.platform.kernel.PlatformException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Whitelisted agent tool surface plus per-turn and per-step budget checks. */
public class ToolRegistry {

  private final Map<String, ToolContract> tools;
  private final int maxToolCalls;
  private final int maxToolCallsPerStep;
  private final int maxAgentSteps;

  public ToolRegistry(int maxToolCalls, int maxToolCallsPerStep, int maxAgentSteps) {
    this(tools(), maxToolCalls, maxToolCallsPerStep, maxAgentSteps);
  }

  public ToolRegistry(
      List<ToolContract> tools, int maxToolCalls, int maxToolCallsPerStep, int maxAgentSteps) {
    LinkedHashMap<String, ToolContract> indexed = new LinkedHashMap<>();
    for (ToolContract tool : tools == null ? List.<ToolContract>of() : tools) {
      indexed.put(tool.name(), tool);
    }
    this.tools = Map.copyOf(indexed);
    this.maxToolCalls = Math.max(1, maxToolCalls);
    this.maxToolCallsPerStep = Math.max(1, maxToolCallsPerStep);
    this.maxAgentSteps = Math.max(1, maxAgentSteps);
  }

  public List<ToolContract> availableTools() {
    return tools.values().stream().toList();
  }

  public ToolContract require(String name) {
    ToolContract tool = tools.get(name);
    if (tool == null) {
      throw new PlatformException(400, "Assistant tool is not whitelisted: " + name);
    }
    return tool;
  }

  public void checkBudget(String toolName, int callsThisTurn, int callsThisStep, int step) {
    require(toolName);
    if (step < 1 || step > maxAgentSteps) {
      throw new PlatformException(429, "Assistant agent step budget exceeded.");
    }
    if (callsThisStep >= maxToolCallsPerStep) {
      throw new PlatformException(429, "Assistant per-step tool budget exceeded.");
    }
    if (callsThisTurn >= maxToolCalls) {
      throw new PlatformException(429, "Assistant per-turn tool budget exceeded.");
    }
  }

  private static List<ToolContract> tools() {
    return List.of(
        new ToolContract(
            "retrieve_context",
            "searchCatalogs",
            "Return metamodel, methodology, model, and source snippets for a RetrievalPlan.",
            true),
        new ToolContract(
            "get_type_contract",
            "getTypeContract",
            "Return exact Ecore attributes, containments, references, and enum literals.",
            true),
        new ToolContract(
            "find_containment",
            "findContainmentOptions",
            "Find legal containment owners/features for ModelDelta placement.",
            true),
        new ToolContract(
            "find_reference",
            "findReferenceOptions",
            "Find writable non-containment EReferences between active model elements.",
            true),
        new ToolContract(
            "list_model_elements",
            "listModelElements",
            "List compact current model element summaries without a full-model dump.",
            true),
        new ToolContract(
            "inspect_element",
            "getElementContext",
            "Inspect selected elements and their one-hop neighborhoods.",
            true),
        new ToolContract(
            "preview_delta",
            "previewModelDelta",
            "Compile and preview ModelDelta without persistence.",
            false),
        new ToolContract(
            "validate_delta",
            "inspectCurrentModelDelta",
            "Compile, preview, and structurally validate ModelDelta without persistence.",
            false),
        new ToolContract(
            "request_clarification",
            "requestUserChoice",
            "Ask a bounded user clarification through coordinator-owned choice UI.",
            false));
  }

  /** One whitelisted tool contract exposed to the agent prompt. */
  public record ToolContract(
      String name, String implementationName, String description, boolean readOnly) {
    public ToolContract {
      name = name == null ? "" : name.trim();
      implementationName = implementationName == null ? "" : implementationName.trim();
      description = description == null ? "" : description.trim();
    }
  }
}
