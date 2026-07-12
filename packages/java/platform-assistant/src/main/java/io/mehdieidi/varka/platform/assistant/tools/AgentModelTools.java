package io.mehdieidi.varka.platform.assistant.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.mehdieidi.varka.platform.assistant.domain.SemanticModelPatch;
import io.mehdieidi.varka.platform.assistant.domain.SemanticModelPatch.Operation;
import io.mehdieidi.varka.platform.assistant.domain.SemanticModelPatch.OperationType;
import io.mehdieidi.varka.platform.assistant.metamodel.MetamodelKnowledgeService.AttributeContract;
import io.mehdieidi.varka.platform.assistant.metamodel.MetamodelKnowledgeService.ReferenceContract;
import io.mehdieidi.varka.platform.assistant.metamodel.MetamodelKnowledgeService.TypeContract;
import io.mehdieidi.varka.platform.assistant.metamodel.TypeContractService;
import io.mehdieidi.varka.platform.assistant.workspace.ModelWorkspace;
import io.mehdieidi.varka.platform.kernel.ModelLevel;
import io.mehdieidi.varka.platform.kernel.PlatformException;
import io.mehdieidi.varka.platform.model.application.ModelService;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

/** Schema-validated editing and inspection tools exposed to the modeling agent. */
public final class AgentModelTools {

  private final TypeContractService contracts;
  private final ModelService models;

  /**
   * Context permanently attached to a tool object created for one agent turn. Spring AI may invoke
   * tools on its streaming worker rather than the request thread, so this must not rely only on a
   * ThreadLocal.
   */
  private final Context scopedContext;

  private volatile List<PlanItem> scopedPlan = List.of();
  private final ThreadLocal<Context> context = new ThreadLocal<>();

  public AgentModelTools(TypeContractService contracts, ModelService models) {
    this(contracts, models, null);
  }

  private AgentModelTools(
      TypeContractService contracts, ModelService models, Context scopedContext) {
    this.contracts = contracts;
    this.models = models;
    this.scopedContext = scopedContext;
  }

  /** Returns an isolated, thread-safe tool object for one working-copy agent turn. */
  public AgentModelTools scoped(ModelLevel level, ModelWorkspace workspace) {
    return new AgentModelTools(contracts, models, new Context(level, workspace, List.of()));
  }

  public void bind(ModelLevel level, ModelWorkspace workspace) {
    context.set(new Context(level, workspace, List.of()));
  }

  public void clear() {
    context.remove();
  }

  @Tool(name = "describe_types", description = "Return exact live Ecore contracts for named types.")
  public List<TypeContract> describeTypes(
      @ToolParam(description = "Exact type names") List<String> names) {
    return contracts.describe(active().level(), names);
  }

  @Tool(
      name = "read_model",
      description = "Read the current working model or one element by stable id.")
  public JsonNode readModel(
      @ToolParam(description = "Element id; blank returns the model") String id) {
    JsonNode model = active().workspace().snapshot();
    return id == null || id.isBlank() ? model : find(model, id);
  }

  @Tool(
      name = "search_model",
      description = "Deterministically search elements by id, name, label, or EClass.")
  public List<JsonNode> searchModel(
      @ToolParam(description = "Case-insensitive query") String query) {
    String needle = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
    if (needle.isBlank()) throw new PlatformException(400, "Search query is required.");
    List<JsonNode> result = new ArrayList<>();
    collect(
        active().workspace().snapshot(),
        node -> {
          if (node.isObject()
              && (matches(node, "id", needle)
                  || matches(node, "name", needle)
                  || matches(node, "label", needle)
                  || matches(node, "eClass", needle))) result.add(node.deepCopy());
        });
    return result.stream().limit(50).toList();
  }

  @Tool(name = "create_elements", description = "Create a validated batch of model elements.")
  public ModelWorkspace.MutationResult createElements(List<CreateElement> items) {
    Context active = active();
    if (items == null || items.isEmpty())
      throw new PlatformException(400, "At least one element is required.");
    List<Operation> operations = new ArrayList<>();
    for (CreateElement item : items) {
      TypeContract type = contracts.require(active.level(), item.type());
      if (!type.creatable())
        throw new PlatformException(
            422, "Type '" + type.eClass() + "' is abstract and cannot be created.");
      ObjectNode attributes =
          item.attributes() == null || !item.attributes().isObject()
              ? com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode()
              : ((ObjectNode) item.attributes()).deepCopy();
      if (item.name() != null && !item.name().isBlank()) attributes.put("name", item.name().trim());
      validateAttributes(type, attributes);
      String id =
          item.id() == null || item.id().isBlank()
              ? UUID.randomUUID().toString()
              : item.id().trim();
      operations.add(
          new Operation(
              OperationType.ADD_ELEMENT,
              id,
              type.eClass(),
              attributes,
              blank(item.containerId()),
              blank(item.containerFeature())));
    }
    return active.workspace().mutate(new SemanticModelPatch(operations));
  }

  @Tool(name = "update_elements", description = "Set validated attributes on existing elements.")
  public ModelWorkspace.MutationResult updateElements(List<UpdateElement> items) {
    Context active = active();
    if (items == null || items.isEmpty())
      throw new PlatformException(400, "At least one update is required.");
    List<Operation> operations = new ArrayList<>();
    for (UpdateElement item : items) {
      JsonNode element = find(active.workspace().snapshot(), item.id());
      TypeContract type = contracts.require(active.level(), element.path("eClass").asText());
      ObjectNode attrs =
          item.attributes() != null && item.attributes().isObject()
              ? (ObjectNode) item.attributes()
              : null;
      if (attrs == null || attrs.isEmpty())
        throw new PlatformException(400, "Attributes are required for " + item.id() + ".");
      validateAttributes(type, attrs);
      attrs
          .fields()
          .forEachRemaining(
              entry ->
                  operations.add(
                      new Operation(
                          OperationType.SET_ATTRIBUTE,
                          item.id(),
                          type.eClass(),
                          entry.getValue(),
                          null,
                          entry.getKey())));
    }
    return active.workspace().mutate(new SemanticModelPatch(operations));
  }

  @Tool(name = "connect_elements", description = "Create type-checked EReference connections.")
  public ModelWorkspace.MutationResult connectElements(List<Connection> items) {
    Context active = active();
    if (items == null || items.isEmpty())
      throw new PlatformException(400, "At least one connection is required.");
    List<Operation> operations = new ArrayList<>();
    for (Connection item : items) {
      JsonNode source = find(active.workspace().snapshot(), item.sourceId());
      JsonNode target = find(active.workspace().snapshot(), item.targetId());
      TypeContract sourceType = contracts.require(active.level(), source.path("eClass").asText());
      ReferenceContract reference =
          sourceType.references().stream()
              .filter(
                  ref ->
                      ref.name().equals(item.reference()) && !ref.containment() && !ref.readonly())
              .findFirst()
              .orElseThrow(
                  () ->
                      new PlatformException(
                          422,
                          "Reference '"
                              + item.reference()
                              + "' is not writable on "
                              + sourceType.eClass()
                              + ". Legal references: "
                              + sourceType.references().stream()
                                  .filter(ref -> !ref.containment() && !ref.readonly())
                                  .map(ReferenceContract::name)
                                  .toList()));
      TypeContract targetType = contracts.require(active.level(), target.path("eClass").asText());
      boolean accepts =
          targetType.eClass().equals(reference.targetType())
              || targetType.supertypes().contains(reference.targetType());
      if (!accepts)
        throw new PlatformException(
            422,
            "Reference "
                + sourceType.eClass()
                + "."
                + reference.name()
                + " requires "
                + reference.targetType()
                + " but target is "
                + targetType.eClass()
                + ".");
      operations.add(
          new Operation(
              OperationType.CONNECT_ELEMENTS,
              item.targetId(),
              targetType.eClass(),
              null,
              item.sourceId(),
              item.reference()));
    }
    return active.workspace().mutate(new SemanticModelPatch(operations));
  }

  @Tool(
      name = "delete_elements",
      description = "Delete elements by stable id from the working copy.")
  public ModelWorkspace.MutationResult deleteElements(List<String> ids) {
    if (ids == null || ids.isEmpty())
      throw new PlatformException(400, "At least one element id is required.");
    List<Operation> operations =
        ids.stream()
            .map(
                id -> {
                  JsonNode element = find(active().workspace().snapshot(), id);
                  return new Operation(
                      OperationType.DELETE_ELEMENT,
                      id,
                      element.path("eClass").asText(),
                      null,
                      null,
                      null);
                })
            .toList();
    return active().workspace().mutate(new SemanticModelPatch(operations));
  }

  @Tool(
      name = "validate_model",
      description = "Run structural Ecore validation on the working copy.")
  public ModelService.ValidationResult validateModel() {
    return models.validateStructural(active().level(), active().workspace().snapshot());
  }

  @Tool(name = "plan_work", description = "Publish the current ordered todo list for this turn.")
  public List<PlanItem> planWork(List<PlanItem> items) {
    Context current = active();
    List<PlanItem> plan = items == null ? List.of() : List.copyOf(items);
    if (scopedContext != null) {
      scopedPlan = plan;
      return plan;
    }
    context.set(new Context(current.level(), current.workspace(), plan));
    return plan;
  }

  public List<PlanItem> plan() {
    if (scopedContext != null) return scopedPlan;
    return active().plan();
  }

  private void validateAttributes(TypeContract type, ObjectNode attributes) {
    Map<String, AttributeContract> legal = new LinkedHashMap<>();
    type.attributes().forEach(attribute -> legal.put(attribute.name(), attribute));
    attributes
        .fieldNames()
        .forEachRemaining(
            name -> {
              if ("label".equals(name)) return;
              AttributeContract contract = legal.get(name);
              if (contract == null)
                throw new PlatformException(
                    422,
                    "Unknown attribute '"
                        + name
                        + "' on "
                        + type.eClass()
                        + ". Legal attributes: "
                        + legal.keySet());
              JsonNode value = attributes.get(name);
              if (!contract.enumLiterals().isEmpty()
                  && value != null
                  && !contract.enumLiterals().contains(value.asText()))
                throw new PlatformException(
                    422,
                    "Invalid "
                        + type.eClass()
                        + "."
                        + name
                        + " value '"
                        + value.asText()
                        + "'. Legal literals: "
                        + contract.enumLiterals());
            });
  }

  private JsonNode find(JsonNode root, String id) {
    if (id == null || id.isBlank()) throw new PlatformException(400, "Element id is required.");
    List<JsonNode> found = new ArrayList<>(1);
    collect(
        root,
        node -> {
          if (found.isEmpty() && node.isObject() && id.equals(node.path("id").asText()))
            found.add(node);
        });
    if (found.isEmpty()) throw new PlatformException(404, "No model element has id '" + id + "'.");
    return found.get(0).deepCopy();
  }

  private void collect(JsonNode node, java.util.function.Consumer<JsonNode> visitor) {
    if (node == null) return;
    visitor.accept(node);
    if (node.isContainerNode()) node.elements().forEachRemaining(child -> collect(child, visitor));
  }

  private boolean matches(JsonNode node, String field, String needle) {
    return node.path(field).asText("").toLowerCase(Locale.ROOT).contains(needle);
  }

  private Context active() {
    if (scopedContext != null) return scopedContext;
    Context active = context.get();
    if (active == null)
      throw new PlatformException(409, "No model workspace is bound to this tool call.");
    return active;
  }

  private String blank(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }

  public record CreateElement(
      String id,
      String type,
      String name,
      JsonNode attributes,
      String containerId,
      String containerFeature) {}

  public record UpdateElement(String id, JsonNode attributes) {}

  public record Connection(String sourceId, String reference, String targetId) {}

  public record PlanItem(String text, String status) {}

  private record Context(ModelLevel level, ModelWorkspace workspace, List<PlanItem> plan) {}
}
