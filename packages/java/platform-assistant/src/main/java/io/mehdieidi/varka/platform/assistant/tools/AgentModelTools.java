package io.mehdieidi.varka.platform.assistant.tools;

import io.mehdieidi.varka.platform.assistant.domain.ModelCommandBatch;
import io.mehdieidi.varka.platform.assistant.domain.SemanticModelPatch;
import io.mehdieidi.varka.platform.assistant.domain.SemanticModelPatch.Operation;
import io.mehdieidi.varka.platform.assistant.domain.SemanticModelPatch.OperationType;
import io.mehdieidi.varka.platform.assistant.metamodel.MetamodelKnowledgeService.AttributeContract;
import io.mehdieidi.varka.platform.assistant.metamodel.MetamodelKnowledgeService.ReferenceContract;
import io.mehdieidi.varka.platform.assistant.metamodel.MetamodelKnowledgeService.TypeContract;
import io.mehdieidi.varka.platform.assistant.metamodel.TypeContractService;
import io.mehdieidi.varka.platform.assistant.patch.ModelCommandCompiler;
import io.mehdieidi.varka.platform.assistant.workspace.ModelWorkspace;
import io.mehdieidi.varka.platform.kernel.ModelLevel;
import io.mehdieidi.varka.platform.kernel.PlatformException;
import io.mehdieidi.varka.platform.model.application.ModelService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/** Schema-validated editing and inspection tools exposed to the modeling agent. */
public final class AgentModelTools {

  private final TypeContractService contracts;
  private final ModelService models;
  private final ModelCommandCompiler commandCompiler;

  /** Context permanently attached to a tool object created for one agent turn. */
  private final Context scopedContext;

  private volatile List<PlanItem> scopedPlan = List.of();
  private volatile ModelCommandBatch committedBatch;
  private final ThreadLocal<Context> context = new ThreadLocal<>();

  public AgentModelTools(TypeContractService contracts, ModelService models) {
    this(contracts, models, null, null);
  }

  public AgentModelTools(
      TypeContractService contracts, ModelService models, ModelCommandCompiler commandCompiler) {
    this(contracts, models, commandCompiler, null);
  }

  private AgentModelTools(
      TypeContractService contracts,
      ModelService models,
      ModelCommandCompiler commandCompiler,
      Context scopedContext) {
    this.contracts = contracts;
    this.models = models;
    this.commandCompiler = commandCompiler;
    this.scopedContext = scopedContext;
  }

  /** Returns an isolated, thread-safe tool object for one working-copy agent turn. */
  public AgentModelTools scoped(ModelLevel level, ModelWorkspace workspace) {
    return new AgentModelTools(
        contracts, models, commandCompiler, new Context(level, workspace, List.of()));
  }

  public void bind(ModelLevel level, ModelWorkspace workspace) {
    context.set(new Context(level, workspace, List.of()));
  }

  public void clear() {
    context.remove();
  }

  public List<TypeContract> describeTypes(List<String> names) {
    return contracts.requiredContainmentClosure(active().level(), names);
  }

  public JsonNode readModel(String id) {
    JsonNode model = active().workspace().snapshot();
    return id == null || id.isBlank() ? model : find(model, id);
  }

  /**
   * Returns compact semantic records for a structured model inspection. This deliberately avoids
   * returning an uncontrolled model dump while allowing feature edits to inspect stable ids, types,
   * ownership, and relationship neighbourhoods before proposing a patch.
   */
  public JsonNode inspectModel(InspectionSelector selector) {
    InspectionSelector effective = selector == null ? InspectionSelector.all() : selector;
    List<InspectedElement> elements = new ArrayList<>();
    collectInspected(active().workspace().snapshot(), null, null, elements);
    List<InspectedElement> filtered =
        elements.stream()
            .filter(item -> effective.ids().isEmpty() || effective.ids().contains(item.id()))
            .filter(
                item ->
                    effective.eClasses().isEmpty() || effective.eClasses().contains(item.eClass()))
            .filter(
                item ->
                    effective.ownerIds().isEmpty() || effective.ownerIds().contains(item.ownerId()))
            .filter(item -> matchesSelectorQuery(item, effective.query()))
            .toList();
    int pageSize = Math.max(1, Math.min(effective.pageSize(), 100));
    int from = Math.min(Math.max(0, effective.page()) * pageSize, filtered.size());
    int to = Math.min(from + pageSize, filtered.size());
    ObjectNode result = tools.jackson.databind.node.JsonNodeFactory.instance.objectNode();
    result.put("total", filtered.size());
    result.put("page", Math.max(0, effective.page()));
    result.put("pageSize", pageSize);
    ArrayNode records = result.putArray("elements");
    for (InspectedElement item : filtered.subList(from, to)) {
      ObjectNode record = records.addObject();
      record.put("id", item.id());
      record.put("eClass", item.eClass());
      if (item.name() != null && !item.name().isBlank()) record.put("name", item.name());
      if (item.ownerId() != null) record.put("ownerId", item.ownerId());
      if (item.ownerFeature() != null) record.put("ownerFeature", item.ownerFeature());
      ArrayNode outgoing = record.putArray("outgoingReferences");
      item.outgoingReferences().forEach(outgoing::add);
    }
    return result;
  }

  private boolean matchesSelectorQuery(InspectedElement item, String query) {
    if (query == null || query.isBlank()) return true;
    String needle = query.trim().toLowerCase(Locale.ROOT);
    return item.id().toLowerCase(Locale.ROOT).contains(needle)
        || item.eClass().toLowerCase(Locale.ROOT).contains(needle)
        || (item.name() != null && item.name().toLowerCase(Locale.ROOT).contains(needle));
  }

  private void collectInspected(
      JsonNode node, String ownerId, String ownerFeature, List<InspectedElement> items) {
    if (node == null) return;
    if (node.isObject()) {
      String id = node.path("id").asText("").trim();
      String eClass = node.path("eClass").asText("").trim();
      if (!id.isBlank() && !eClass.isBlank()) {
        List<String> outgoing = new ArrayList<>();
        node.properties()
            .forEach(entry -> collectReferenceIds(entry.getValue(), entry.getKey(), outgoing));
        items.add(
            new InspectedElement(
                id,
                eClass,
                node.path("name").asText(node.path("label").asText("")),
                ownerId,
                ownerFeature,
                outgoing));
        node.properties()
            .forEach(entry -> collectInspected(entry.getValue(), id, entry.getKey(), items));
        return;
      }
    }
    if (node.isObject())
      node.properties()
          .forEach(entry -> collectInspected(entry.getValue(), ownerId, entry.getKey(), items));
    else if (node.isArray())
      node.forEach(child -> collectInspected(child, ownerId, ownerFeature, items));
  }

  private void collectReferenceIds(JsonNode node, String feature, List<String> references) {
    if (node == null) return;
    if (node.isTextual()
        && (feature.endsWith("Id") || feature.endsWith("Ids") || feature.endsWith("Ref")))
      references.add(feature + "=" + node.asText());
    else if (node.isArray() && (feature.endsWith("Ids") || feature.endsWith("Refs")))
      node.forEach(
          value -> {
            if (value.isTextual()) references.add(feature + "=" + value.asText());
          });
  }

  /**
   * Builds compact, deterministic current-model context for the agent prompt.
   *
   * <p>This is intentionally not a serialization of the complete model: it gives the provider the
   * element inventory required for ordinary questions while retaining {@link #readModel(String)}
   * for a focused deep inspection.
   */
  public String modelContext() {
    JsonNode root = active().workspace().snapshot();
    Map<String, Integer> typeCounts = new TreeMap<>();
    List<String> elements = new ArrayList<>();
    collectModelContext(root, root.path("id").asText(), typeCounts, elements);
    StringBuilder modelContext = new StringBuilder();
    modelContext
        .append("rootId=")
        .append(root.path("id").asText("(none)"))
        .append(", rootType=")
        .append(root.path("eClass").asText("(none)"))
        .append(", level=")
        .append(root.path("modelLevel").asText("(none)"))
        .append("\nType counts: ");
    if (typeCounts.isEmpty()) modelContext.append("none");
    else
      modelContext.append(
          typeCounts.entrySet().stream()
              .map(entry -> entry.getKey() + "=" + entry.getValue())
              .collect(java.util.stream.Collectors.joining(", ")));
    modelContext.append("\nElements:");
    if (elements.isEmpty()) modelContext.append(" none");
    else elements.forEach(element -> modelContext.append("\n- ").append(element));
    return modelContext.toString();
  }

  private void collectModelContext(
      JsonNode node, String rootId, Map<String, Integer> typeCounts, List<String> elements) {
    if (node == null) return;
    if (node.isObject()) {
      String type = node.path("eClass").asText("").trim();
      String id = node.path("id").asText("").trim();
      if (!type.isEmpty()) {
        typeCounts.merge(type, 1, Integer::sum);
        if (!id.isEmpty() && !id.equals(rootId) && elements.size() < 80) {
          String name = node.path("name").asText(node.path("label").asText("")).trim();
          String description = node.path("description").asText("").replaceAll("\\s+", " ").trim();
          StringBuilder item = new StringBuilder("id=").append(id).append(", type=").append(type);
          if (!name.isEmpty()) item.append(", name=").append(name);
          if (!description.isEmpty())
            item.append(", description=")
                .append(description, 0, Math.min(description.length(), 240));
          elements.add(item.toString());
        }
      }
    }
    if (node.isContainer())
      node.forEach(child -> collectModelContext(child, rootId, typeCounts, elements));
  }

  public List<JsonNode> searchModel(String query) {
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
              ? tools.jackson.databind.node.JsonNodeFactory.instance.objectNode()
              : ((ObjectNode) item.attributes()).deepCopy();
      if (item.name() != null && !item.name().isBlank()) attributes.put("name", item.name().trim());
      attributes = normalizeAttributes(type, attributes);
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

  /** Returns the terminal batch which produced this workspace checkpoint. */
  public ModelCommandBatch committedBatch() {
    return committedBatch;
  }

  /** Executes the terminal, stable-reference command format used by the explicit agent loop. */
  public ModelWorkspace.MutationResult commitModelBatch(ModelCommandBatch batch) {
    return commitModelBatch(batch, false);
  }

  /** Executes a batch after the durable runtime has explicitly confirmed any deletion. */
  public ModelWorkspace.MutationResult commitModelBatch(
      ModelCommandBatch batch, boolean destructiveConfirmed) {
    if (batch == null) throw new PlatformException(400, "Model command batch is required.");
    if (!batch.hasMutations()) {
      throw new PlatformException(
          422,
          "Model command batch must contain at least one create, update, connection, or deletion; "
              + "evidence alone cannot create a checkpoint.");
    }
    if (!batch.deletions().isEmpty() && !destructiveConfirmed) {
      throw new PlatformException(409, "Deletion requires turn confirmation.");
    }
    Context active = active();
    List<Operation> operations = new ArrayList<>();
    Map<String, String> refs = new LinkedHashMap<>();
    // `rootId` is an explicit symbolic reference in the provider-facing patch contract. It is
    // resolved only to this workspace's immutable root, never guessed from another element.
    String rootId = active.workspace().snapshot().path("id").asText("").trim();
    refs.put("rootId", rootId.isBlank() ? "rootId" : rootId);
    Map<String, String> createdTypes = new LinkedHashMap<>();
    Map<String, ObjectNode> createdAttributes = new LinkedHashMap<>();
    for (ModelCommandBatch.Create create : batch.creates()) {
      if (create.clientRef() == null || create.clientRef().isBlank())
        throw new PlatformException(422, "Create clientRef is required.");
      String elementId = UUID.randomUUID().toString();
      if (refs.put(create.clientRef(), elementId) != null)
        throw new PlatformException(422, "Duplicate clientRef: " + create.clientRef());
      if (blank(create.owner()) == null || blank(create.reference()) == null) {
        throw new PlatformException(
            422,
            "Create '" + create.clientRef() + "' requires explicit owner and containment feature.");
      }
      TypeContract type = contracts.require(active.level(), create.eClass());
      createdTypes.put(elementId, type.eClass());
      ObjectNode attributes = tools.jackson.databind.node.JsonNodeFactory.instance.objectNode();
      if (create.attributes() != null) create.attributes().forEach(attributes::set);
      attributes = normalizeAttributes(type, attributes);
      createdAttributes.put(elementId, attributes);
      String ownerId = resolveRef(create.owner(), refs);
      String ownerTypeName =
          rootId.equals(ownerId)
              ? active.workspace().snapshot().path("eClass").asText("")
              : createdTypes.get(ownerId);
      if (ownerTypeName == null || ownerTypeName.isBlank())
        ownerTypeName = find(active.workspace().snapshot(), ownerId).path("eClass").asText("");
      String containment =
          normalizeContainmentReference(
              active.level(), ownerTypeName, blank(create.reference()), type.eClass());
      operations.add(
          new Operation(
              OperationType.ADD_ELEMENT,
              elementId,
              type.eClass(),
              attributes,
              ownerId,
              containment));
    }
    for (ModelCommandBatch.Update update : batch.updates()) {
      String id = resolveRef(update.elementId(), refs);
      if (createdTypes.containsKey(id)) {
        TypeContract createdType = contracts.require(active.level(), createdTypes.get(id));
        ObjectNode attributes = tools.jackson.databind.node.JsonNodeFactory.instance.objectNode();
        if (update.attributes() != null) update.attributes().forEach(attributes::set);
        ObjectNode normalized = normalizeAttributes(createdType, attributes);
        normalized
            .properties()
            .forEach(entry -> createdAttributes.get(id).set(entry.getKey(), entry.getValue()));
        continue;
      }
      JsonNode element = find(active.workspace().snapshot(), id);
      if (update.preconditionHash() != null
          && !update.preconditionHash().isBlank()
          && !id.equals(rootId)
          && !update.preconditionHash().equals(elementHash(element))) {
        throw new PlatformException(409, "Update precondition changed for element '" + id + "'.");
      }
      TypeContract type = contracts.require(active.level(), element.path("eClass").asText());
      ObjectNode attributes = tools.jackson.databind.node.JsonNodeFactory.instance.objectNode();
      if (update.attributes() != null) update.attributes().forEach(attributes::set);
      attributes = normalizeAttributes(type, attributes);
      attributes
          .properties()
          .forEach(
              entry ->
                  operations.add(
                      new Operation(
                          OperationType.SET_ATTRIBUTE,
                          id,
                          type.eClass(),
                          entry.getValue(),
                          null,
                          entry.getKey())));
    }
    for (ModelCommandBatch.Connection connection : batch.connections()) {
      String source = resolveExistingOrBatchRef(connection.source(), refs, "Connection source");
      String target = resolveExistingOrBatchRef(connection.target(), refs, "Connection target");
      String sourceTypeName = createdTypes.get(source);
      if (sourceTypeName == null)
        sourceTypeName = find(active.workspace().snapshot(), source).path("eClass").asText();
      TypeContract sourceType = contracts.require(active.level(), sourceTypeName);
      ReferenceContract reference =
          sourceType.references().stream()
              .filter(
                  ref ->
                      ref.name().equals(connection.reference())
                          && !ref.containment()
                          && !ref.readonly())
              .findFirst()
              .orElse(null);
      if (reference == null) continue;
      String targetType = createdTypes.get(target);
      if (targetType == null)
        targetType = find(active.workspace().snapshot(), target).path("eClass").asText();
      if (!contracts.assignable(active.level(), targetType, reference.targetType())) continue;
      operations.add(
          new Operation(
              OperationType.CONNECT_ELEMENTS,
              target,
              targetType,
              null,
              source,
              connection.reference()));
    }
    synthesizeRequiredClosure(
        active.level(),
        active.workspace().snapshot(),
        operations,
        refs,
        createdTypes,
        createdAttributes);
    synthesizeCimSemanticCore(
        active.level(),
        active.workspace().snapshot(),
        operations,
        refs,
        createdTypes,
        createdAttributes);
    for (ModelCommandBatch.Deletion deletion : batch.deletions()) {
      String id = resolveRef(deletion.elementId(), refs);
      JsonNode element = find(active.workspace().snapshot(), id);
      if (deletion.preconditionHash() == null || deletion.preconditionHash().isBlank())
        throw new PlatformException(
            422, "Deletion '" + id + "' requires the inspected preconditionHash.");
      if (!deletion.preconditionHash().equals(elementHash(element)))
        throw new PlatformException(409, "Deletion precondition changed for element '" + id + "'.");
      operations.add(
          new Operation(
              OperationType.DELETE_ELEMENT, id, element.path("eClass").asText(), null, null, null));
    }
    SemanticModelPatch semantic = new SemanticModelPatch(operations);
    ModelWorkspace.MutationResult result =
        commandCompiler == null
            ? active.workspace().mutate(semantic)
            : mutateAtomically(active.workspace(), semantic);
    committedBatch = batch;
    return result;
  }

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
      attrs = normalizeAttributes(type, attrs);
      attrs
          .properties()
          .forEach(
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

  private void synthesizeRequiredClosure(
      ModelLevel level,
      JsonNode snapshot,
      List<Operation> operations,
      Map<String, String> refs,
      Map<String, String> createdTypes,
      Map<String, ObjectNode> createdAttributes) {
    List<String> initialIds = new ArrayList<>(createdTypes.keySet());
    for (String id : initialIds) {
      TypeContract type = contracts.require(level, createdTypes.get(id));
      synthesizeRequiredContainments(
          level, operations, refs, createdTypes, createdAttributes, id, type);
    }
    for (String id : new ArrayList<>(createdTypes.keySet())) {
      TypeContract type = contracts.require(level, createdTypes.get(id));
      synthesizeRequiredReferences(
          level, snapshot, operations, refs, createdTypes, createdAttributes, id, type);
    }
  }

  private void synthesizeCimSemanticCore(
      ModelLevel level,
      JsonNode snapshot,
      List<Operation> operations,
      Map<String, String> refs,
      Map<String, String> createdTypes,
      Map<String, ObjectNode> createdAttributes) {
    if (level != ModelLevel.CIM || !"CIMModel".equals(snapshot.path("eClass").asText(""))) return;
    String rootId = snapshot.path("id").asText("").trim();
    if (rootId.isBlank()) return;

    enrichCreatedCimCoreAttributes(createdTypes, createdAttributes);

    String goalId = firstIdOfType(snapshot, createdTypes, "BusinessGoal");
    String actorId = firstIdOfType(snapshot, createdTypes, "Actor");
    String capabilityId = firstIdOfType(snapshot, createdTypes, "BusinessCapability");
    String anchorName = semanticAnchorName(snapshot, createdTypes, createdAttributes);

    if (goalId == null) {
      goalId = uniqueRef(createdTypes, rootId + "-semantic-goal");
      ObjectNode attributes = tools.jackson.databind.node.JsonNodeFactory.instance.objectNode();
      attributes.put("name", anchorName + " outcome");
      attributes.put("successCriterion", "The user outcome described by the source is satisfied.");
      attributes.put("businessValue", "Supports the business value requested in the source.");
      attributes.put("failureConsequence", "Users cannot complete the intended business outcome.");
      attributes = normalizeAttributes(contracts.require(level, "BusinessGoal"), attributes);
      refs.put(goalId, goalId);
      createdTypes.put(goalId, "BusinessGoal");
      createdAttributes.put(goalId, attributes);
      operations.add(
          new Operation(
              OperationType.ADD_ELEMENT, goalId, "BusinessGoal", attributes, rootId, "goals"));
    }

    if (actorId == null) {
      actorId = uniqueRef(createdTypes, rootId + "-semantic-actor");
      ObjectNode attributes = tools.jackson.databind.node.JsonNodeFactory.instance.objectNode();
      attributes.put("name", roleOrDefaultName(snapshot, createdTypes, createdAttributes));
      attributes.put("actorType", "HUMAN");
      attributes.put("trustLevel", "PARTIALLY_TRUSTED");
      attributes = normalizeAttributes(contracts.require(level, "Actor"), attributes);
      refs.put(actorId, actorId);
      createdTypes.put(actorId, "Actor");
      createdAttributes.put(actorId, attributes);
      operations.add(
          new Operation(OperationType.ADD_ELEMENT, actorId, "Actor", attributes, rootId, "actors"));
    }

    if (capabilityId == null) {
      capabilityId = uniqueRef(createdTypes, rootId + "-semantic-capability");
      ObjectNode attributes = tools.jackson.databind.node.JsonNodeFactory.instance.objectNode();
      attributes.put("name", anchorName + " capability");
      attributes.put(
          "responsibility", "Owns the behavior and information needed for " + anchorName + ".");
      attributes.put("ownerName", readableName(actorId));
      attributes.put("criticality", "IMPORTANT");
      attributes = normalizeAttributes(contracts.require(level, "BusinessCapability"), attributes);
      refs.put(capabilityId, capabilityId);
      createdTypes.put(capabilityId, "BusinessCapability");
      createdAttributes.put(capabilityId, attributes);
      operations.add(
          new Operation(
              OperationType.ADD_ELEMENT,
              capabilityId,
              "BusinessCapability",
              attributes,
              rootId,
              "capabilities"));
    } else {
      ObjectNode attributes = createdAttributes.get(capabilityId);
      if (attributes != null && !hasText(attributes.path("responsibility"))) {
        attributes.put(
            "responsibility", "Owns the behavior and information needed for " + anchorName + ".");
      }
    }

    if (!hasConnection(operations, capabilityId, "supports")
        && !hasReference(snapshot, capabilityId, "supports", goalId)) {
      operations.add(
          new Operation(
              OperationType.CONNECT_ELEMENTS,
              goalId,
              "BusinessGoal",
              null,
              capabilityId,
              "supports"));
    }
    if (!hasConnection(operations, capabilityId, "owner")
        && !hasReference(snapshot, capabilityId, "owner", actorId)) {
      operations.add(
          new Operation(
              OperationType.CONNECT_ELEMENTS, actorId, "Actor", null, capabilityId, "owner"));
    }
    String commandId = firstIdOfType(snapshot, createdTypes, "Command");
    if (commandId != null
        && !hasConnection(operations, capabilityId, "containsCommands")
        && !hasReference(snapshot, capabilityId, "containsCommands", commandId)) {
      operations.add(
          new Operation(
              OperationType.CONNECT_ELEMENTS,
              commandId,
              "Command",
              null,
              capabilityId,
              "containsCommands"));
    }
  }

  private void enrichCreatedCimCoreAttributes(
      Map<String, String> createdTypes, Map<String, ObjectNode> createdAttributes) {
    createdTypes.forEach(
        (id, type) -> {
          ObjectNode attributes = createdAttributes.get(id);
          if (attributes == null) return;
          if ("BusinessGoal".equals(type) && !hasText(attributes.path("successCriterion"))) {
            attributes.put(
                "successCriterion", "The user outcome described by the source is satisfied.");
          } else if ("Actor".equals(type)) {
            if (!hasText(attributes.path("actorType"))) attributes.put("actorType", "HUMAN");
            if (!hasText(attributes.path("trustLevel")))
              attributes.put("trustLevel", "PARTIALLY_TRUSTED");
          } else if ("BusinessCapability".equals(type)
              && !hasText(attributes.path("responsibility"))) {
            attributes.put("responsibility", "Owns the modeled business behavior.");
          }
        });
  }

  private String firstIdOfType(
      JsonNode snapshot, Map<String, String> createdTypes, String typeName) {
    for (Map.Entry<String, String> entry : createdTypes.entrySet()) {
      if (contracts.assignable(ModelLevel.CIM, entry.getValue(), typeName)) return entry.getKey();
    }
    List<String> found = new ArrayList<>(1);
    collect(
        snapshot,
        node -> {
          if (found.isEmpty()
              && node.isObject()
              && contracts.assignable(ModelLevel.CIM, node.path("eClass").asText(""), typeName)
              && hasText(node.path("id"))) {
            found.add(node.path("id").asText());
          }
        });
    return found.isEmpty() ? null : found.get(0);
  }

  private String semanticAnchorName(
      JsonNode snapshot,
      Map<String, String> createdTypes,
      Map<String, ObjectNode> createdAttributes) {
    for (String typeName :
        List.of(
            "Requirement",
            "Command",
            "Query",
            "BusinessProcess",
            "DomainEntity",
            "InformationItem")) {
      String name = firstCreatedName(createdTypes, createdAttributes, typeName);
      if (name != null) return name;
    }
    String rootName = snapshot.path("name").asText("business model").trim();
    if (rootName.startsWith("CIM from ")) rootName = rootName.substring("CIM from ".length());
    return rootName.isBlank() ? "business outcome" : rootName;
  }

  private String roleOrDefaultName(
      JsonNode snapshot,
      Map<String, String> createdTypes,
      Map<String, ObjectNode> createdAttributes) {
    String roleName = firstCreatedName(createdTypes, createdAttributes, "Role");
    if (roleName != null) return roleName;
    List<String> found = new ArrayList<>(1);
    collect(
        snapshot,
        node -> {
          if (found.isEmpty()
              && node.isObject()
              && "Role".equals(node.path("eClass").asText(""))
              && hasText(node.path("name"))) {
            found.add(node.path("name").asText());
          }
        });
    return found.isEmpty() ? "Primary user" : found.get(0);
  }

  private String firstCreatedName(
      Map<String, String> createdTypes,
      Map<String, ObjectNode> createdAttributes,
      String typeName) {
    for (Map.Entry<String, String> entry : createdTypes.entrySet()) {
      if (!contracts.assignable(ModelLevel.CIM, entry.getValue(), typeName)) continue;
      ObjectNode attributes = createdAttributes.get(entry.getKey());
      if (attributes == null) continue;
      JsonNode name = attributes.path("name");
      if (hasText(name)) return name.asText();
    }
    return null;
  }

  private boolean hasText(JsonNode value) {
    return value != null && value.isTextual() && !value.asText().isBlank();
  }

  private boolean hasReference(
      JsonNode snapshot, String sourceId, String reference, String targetId) {
    if (!exists(snapshot, sourceId)) return false;
    JsonNode source = find(snapshot, sourceId);
    JsonNode value = source.path(reference);
    if (value.isArray()) {
      for (JsonNode item : value) {
        if (targetId.equals(item.asText())) return true;
      }
      return false;
    }
    return targetId.equals(value.asText(null));
  }

  private void synthesizeRequiredContainments(
      ModelLevel level,
      List<Operation> operations,
      Map<String, String> refs,
      Map<String, String> createdTypes,
      Map<String, ObjectNode> createdAttributes,
      String ownerId,
      TypeContract ownerType) {
    for (ReferenceContract reference : ownerType.references()) {
      if (!reference.required()
          || !reference.containment()
          || hasOwnedCreate(operations, ownerId, reference.name())) {
        continue;
      }
      String childType = containmentDefaultType(level, reference);
      if (childType == null) continue;
      String childId = uniqueRef(createdTypes, ownerId + "-" + reference.name());
      if (!contracts.require(level, childType).creatable()) continue;
      ObjectNode attributes = tools.jackson.databind.node.JsonNodeFactory.instance.objectNode();
      attributes.put("name", readableName(childType));
      refs.put(childId, childId);
      createdTypes.put(childId, childType);
      createdAttributes.put(childId, attributes);
      operations.add(
          new Operation(
              OperationType.ADD_ELEMENT,
              childId,
              childType,
              attributes,
              ownerId,
              reference.name()));
    }
  }

  private String containmentDefaultType(ModelLevel level, ReferenceContract reference) {
    TypeContract exact = contracts.require(level, reference.targetType());
    if (exact.creatable()) return exact.eClass();
    if (level == ModelLevel.CIM && "ProcessStep".equals(reference.targetType())) return "StartStep";
    return contracts.all(level).stream()
        .filter(TypeContract::creatable)
        .filter(type -> contracts.assignable(level, type.eClass(), reference.targetType()))
        .map(TypeContract::eClass)
        .sorted()
        .findFirst()
        .orElse(null);
  }

  private String normalizeContainmentReference(
      ModelLevel level, String ownerTypeName, String requestedReference, String childTypeName) {
    TypeContract ownerType = contracts.require(level, ownerTypeName);
    if (requestedReference != null) {
      boolean valid =
          ownerType.references().stream()
              .anyMatch(
                  reference ->
                      reference.containment()
                          && reference.name().equals(requestedReference)
                          && contracts.assignable(level, childTypeName, reference.targetType()));
      if (valid) return requestedReference;
    }
    List<ReferenceContract> compatible =
        ownerType.references().stream()
            .filter(ReferenceContract::containment)
            .filter(reference -> contracts.assignable(level, childTypeName, reference.targetType()))
            .sorted(
                Comparator.comparing(ReferenceContract::many)
                    .reversed()
                    .thenComparing(
                        reference -> reference.targetType().equals(childTypeName) ? 0 : 1)
                    .thenComparing(ReferenceContract::name))
            .toList();
    if (compatible.size() == 1) return compatible.get(0).name();
    if (compatible.size() > 1
        && compatible.get(0).many() != compatible.get(1).many()
        && compatible.get(0).targetType().equals(childTypeName)) {
      return compatible.get(0).name();
    }
    return requestedReference;
  }

  private void synthesizeRequiredReferences(
      ModelLevel level,
      JsonNode snapshot,
      List<Operation> operations,
      Map<String, String> refs,
      Map<String, String> createdTypes,
      Map<String, ObjectNode> createdAttributes,
      String sourceId,
      TypeContract sourceType) {
    for (ReferenceContract reference : sourceType.references()) {
      if (!reference.required()
          || reference.containment()
          || reference.readonly()
          || hasConnection(operations, sourceId, reference.name())) {
        continue;
      }
      chooseReferenceTarget(level, snapshot, createdTypes, sourceId, reference)
          .ifPresent(
              target ->
                  operations.add(
                      new Operation(
                          OperationType.CONNECT_ELEMENTS,
                          target.id(),
                          target.type(),
                          null,
                          sourceId,
                          reference.name())));
      if (!hasConnection(operations, sourceId, reference.name())) {
        synthesizeReferenceTarget(
                level, snapshot, operations, refs, createdTypes, createdAttributes, reference)
            .ifPresent(
                target ->
                    operations.add(
                        new Operation(
                            OperationType.CONNECT_ELEMENTS,
                            target.id(),
                            target.type(),
                            null,
                            sourceId,
                            reference.name())));
      }
    }
  }

  private java.util.Optional<TargetCandidate> synthesizeReferenceTarget(
      ModelLevel level,
      JsonNode snapshot,
      List<Operation> operations,
      Map<String, String> refs,
      Map<String, String> createdTypes,
      Map<String, ObjectNode> createdAttributes,
      ReferenceContract requiredReference) {
    String targetType = containmentDefaultType(level, requiredReference);
    if (targetType == null) return java.util.Optional.empty();
    String rootId = snapshot.path("id").asText("").trim();
    String rootType = snapshot.path("eClass").asText("").trim();
    if (rootId.isBlank() || rootType.isBlank()) return java.util.Optional.empty();
    String containment = normalizeContainmentReference(level, rootType, null, targetType);
    if (containment == null || containment.isBlank()) return java.util.Optional.empty();
    String id = uniqueRef(createdTypes, rootId + "-" + requiredReference.name());
    ObjectNode attributes = tools.jackson.databind.node.JsonNodeFactory.instance.objectNode();
    attributes.put("name", readableName(requiredReference.name()));
    attributes = normalizeAttributes(contracts.require(level, targetType), attributes);
    refs.put(id, id);
    createdTypes.put(id, targetType);
    createdAttributes.put(id, attributes);
    operations.add(
        new Operation(OperationType.ADD_ELEMENT, id, targetType, attributes, rootId, containment));
    return java.util.Optional.of(new TargetCandidate(id, targetType, 0));
  }

  private java.util.Optional<TargetCandidate> chooseReferenceTarget(
      ModelLevel level,
      JsonNode snapshot,
      Map<String, String> createdTypes,
      String sourceId,
      ReferenceContract reference) {
    List<TargetCandidate> candidates = new ArrayList<>();
    createdTypes.forEach(
        (id, type) -> {
          if (!id.equals(sourceId) && contracts.assignable(level, type, reference.targetType())) {
            candidates.add(new TargetCandidate(id, type, 0));
          }
        });
    collect(
        snapshot,
        node -> {
          if (!node.isObject()) return;
          String id = node.path("id").asText("");
          String type = node.path("eClass").asText("");
          if (!id.isBlank()
              && !id.equals(sourceId)
              && !createdTypes.containsKey(id)
              && contracts.assignable(level, type, reference.targetType())) {
            candidates.add(new TargetCandidate(id, type, 1));
          }
        });
    return candidates.stream()
        .sorted(Comparator.comparingInt(TargetCandidate::rank).thenComparing(TargetCandidate::id))
        .findFirst();
  }

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

  public ModelService.ValidationResult validateModel() {
    return models.validateStructural(active().level(), active().workspace().snapshot());
  }

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

  private ObjectNode normalizeAttributes(TypeContract type, ObjectNode attributes) {
    Map<String, AttributeContract> legal = new LinkedHashMap<>();
    type.attributes().forEach(attribute -> legal.put(attribute.name(), attribute));
    ObjectNode normalized = tools.jackson.databind.node.JsonNodeFactory.instance.objectNode();
    attributes
        .properties()
        .forEach(
            entry -> {
              String name = entry.getKey();
              JsonNode value = entry.getValue();
              AttributeContract contract = legal.get(name);
              if (contract == null) {
                throw new PlatformException(
                    422, "Attribute '" + name + "' is not writable on " + type.eClass() + ".");
              }
              if (!contract.enumLiterals().isEmpty()
                  && value != null
                  && !contract.enumLiterals().contains(value.asText())) {
                throw new PlatformException(
                    422,
                    "Invalid enum value for "
                        + type.eClass()
                        + "."
                        + name
                        + ". Allowed values: "
                        + contract.enumLiterals());
              }
              normalized.set(name, value);
            });
    for (AttributeContract contract : type.attributes()) {
      if (!contract.required()
          || normalized.has(contract.name())
          || contract.enumLiterals().isEmpty()) {
        continue;
      }
      String defaultLiteral =
          contract.enumLiterals().contains("UNKNOWN") ? "UNKNOWN" : contract.enumLiterals().get(0);
      normalized.put(contract.name(), defaultLiteral);
    }
    return normalized;
  }

  private boolean hasConnection(List<Operation> operations, String sourceId, String reference) {
    return operations.stream()
        .anyMatch(
            op ->
                op.type() == OperationType.CONNECT_ELEMENTS
                    && sourceId.equals(op.sourceElementId())
                    && reference.equals(op.referenceName()));
  }

  private boolean hasOwnedCreate(List<Operation> operations, String sourceId, String reference) {
    return operations.stream()
        .anyMatch(
            op ->
                op.type() == OperationType.ADD_ELEMENT
                    && sourceId.equals(op.sourceElementId())
                    && reference.equals(op.referenceName()));
  }

  private String uniqueRef(Map<String, String> refs, String base) {
    String candidate = base;
    int suffix = 2;
    while (refs.containsKey(candidate)) {
      candidate = base + "-" + suffix++;
    }
    return candidate;
  }

  private String readableName(String type) {
    return type.replaceAll("(?<!^)([A-Z])", " $1");
  }

  private ModelWorkspace.MutationResult mutateAtomically(
      ModelWorkspace workspace, SemanticModelPatch semantic) {
    return workspace.mutate(commandCompiler.compile(workspace.snapshot(), semantic));
  }

  private JsonNode find(JsonNode root, String id) {
    if (id == null || id.isBlank()) throw new PlatformException(400, "Element id is required.");
    if ("rootId".equals(id) && root != null && root.isObject()) return root.deepCopy();
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
    if (node.isContainer()) node.forEach(child -> collect(child, visitor));
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

  private String resolveRef(String value, Map<String, String> refs) {
    String result = blank(value);
    return result == null ? null : refs.getOrDefault(result, result);
  }

  private String resolveExistingOrBatchRef(String value, Map<String, String> refs, String label) {
    String raw = blank(value);
    if (raw == null) throw new PlatformException(422, label + " is required.");
    String resolved = refs.get(raw);
    if (resolved != null) return resolved;
    if (exists(active().workspace().snapshot(), raw)) return raw;
    throw new PlatformException(
        422,
        label
            + " '"
            + raw
            + "' is not a known clientRef from this batch or an existing element id. Use the"
            + " exact clientRef from creates without prefixes or aliases. Known clientRefs: "
            + String.join(", ", refs.keySet()));
  }

  private boolean exists(JsonNode root, String id) {
    if (id == null || id.isBlank()) return false;
    if ("rootId".equals(id) && root != null && root.isObject()) return true;
    List<JsonNode> found = new ArrayList<>(1);
    collect(
        root,
        node -> {
          if (found.isEmpty() && node.isObject() && id.equals(node.path("id").asText()))
            found.add(node);
        });
    return !found.isEmpty();
  }

  private String elementHash(JsonNode element) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256")
                  .digest(element.toString().getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException ex) {
      throw new IllegalStateException("SHA-256 unavailable", ex);
    }
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

  public record InspectionSelector(
      List<String> ids,
      List<String> eClasses,
      List<String> ownerIds,
      String query,
      int page,
      int pageSize) {
    public InspectionSelector {
      ids = ids == null ? List.of() : List.copyOf(ids);
      eClasses = eClasses == null ? List.of() : List.copyOf(eClasses);
      ownerIds = ownerIds == null ? List.of() : List.copyOf(ownerIds);
    }

    public static InspectionSelector all() {
      return new InspectionSelector(List.of(), List.of(), List.of(), null, 0, 50);
    }
  }

  private record Context(ModelLevel level, ModelWorkspace workspace, List<PlanItem> plan) {}

  private record TargetCandidate(String id, String type, int rank) {}

  private record InspectedElement(
      String id,
      String eClass,
      String name,
      String ownerId,
      String ownerFeature,
      List<String> outgoingReferences) {}
}
