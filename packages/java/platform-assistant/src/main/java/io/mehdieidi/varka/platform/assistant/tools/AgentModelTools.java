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
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
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

  public List<TypeContract> describeExactTypes(List<String> names) {
    return contracts.describe(active().level(), names);
  }

  /** Resolves an exact root containment from the live Ecore contract index. */
  public java.util.Optional<ReferenceContract> rootContainment(String elementType) {
    return contracts.rootContainment(active().level(), elementType);
  }

  public JsonNode readModel(String id) {
    JsonNode model = active().workspace().snapshot();
    return id == null || id.isBlank() ? model : find(model, id);
  }

  /** Returns root, type counts, and top-level owned elements for enforced edit inspection. */
  public JsonNode inspectSummary() {
    JsonNode root = active().workspace().snapshot();
    List<InspectedElement> elements = new ArrayList<>();
    collectInspected(root, null, null, elements);
    Map<String, Integer> typeCounts = new TreeMap<>();
    elements.forEach(item -> typeCounts.merge(item.eClass(), 1, Integer::sum));
    ObjectNode result = tools.jackson.databind.node.JsonNodeFactory.instance.objectNode();
    result.put("rootId", root.path("id").asText(""));
    result.put("rootType", root.path("eClass").asText(""));
    ObjectNode counts = result.putObject("typeCounts");
    typeCounts.forEach(counts::put);
    ArrayNode owned = result.putArray("topLevelOwnedElements");
    String rootId = root.path("id").asText("");
    elements.stream()
        .filter(item -> rootId.equals(item.ownerId()))
        .limit(80)
        .forEach(
            item -> {
              ObjectNode record = owned.addObject();
              record.put("id", item.id());
              record.put("eClass", item.eClass());
              if (item.name() != null && !item.name().isBlank()) record.put("name", item.name());
              if (item.ownerFeature() != null) record.put("ownerFeature", item.ownerFeature());
            });
    return result;
  }

  /** Returns owner, children, and references near already selected/edit-relevant elements. */
  public JsonNode inspectNeighborhoods(List<String> ids) {
    JsonNode root = active().workspace().snapshot();
    List<InspectedElement> elements = new ArrayList<>();
    collectInspected(root, null, null, elements);
    Map<String, InspectedElement> byId = new LinkedHashMap<>();
    elements.forEach(item -> byId.put(item.id(), item));
    ObjectNode result = tools.jackson.databind.node.JsonNodeFactory.instance.objectNode();
    ArrayNode neighborhoods = result.putArray("neighborhoods");
    for (String id : ids == null ? List.<String>of() : ids) {
      if (id == null || id.isBlank() || !byId.containsKey(id)) continue;
      InspectedElement center = byId.get(id);
      ObjectNode record = neighborhoods.addObject();
      appendInspectionRecord(record.putObject("center"), center, root);
      if (center.ownerId() != null && byId.containsKey(center.ownerId())) {
        appendInspectionRecord(record.putObject("owner"), byId.get(center.ownerId()), root);
      }
      ArrayNode children = record.putArray("containedChildren");
      elements.stream()
          .filter(item -> id.equals(item.ownerId()))
          .limit(40)
          .forEach(item -> appendInspectionRecord(children.addObject(), item, root));
      ArrayNode inbound = record.putArray("inboundReferences");
      elements.stream()
          .filter(
              item -> item.outgoingReferences().stream().anyMatch(ref -> ref.endsWith("=" + id)))
          .limit(40)
          .forEach(item -> appendInspectionRecord(inbound.addObject(), item, root));
    }
    return result;
  }

  private void appendInspectionRecord(ObjectNode record, InspectedElement item, JsonNode root) {
    record.put("id", item.id());
    record.put("eClass", item.eClass());
    if (item.name() != null && !item.name().isBlank()) record.put("name", item.name());
    if (item.ownerId() != null) record.put("ownerId", item.ownerId());
    if (item.ownerFeature() != null) record.put("ownerFeature", item.ownerFeature());
    record.put("preconditionHash", elementHash(find(root, item.id())));
    ArrayNode outgoing = record.putArray("outgoingReferences");
    item.outgoingReferences().forEach(outgoing::add);
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

  /**
   * Lists inspected existing elements that can legally contain any of the requested child types.
   * This is repair context only: callers still have to choose and submit an exact stable id.
   */
  public JsonNode eligibleExistingOwners(List<String> childTypes) {
    ObjectNode result = tools.jackson.databind.node.JsonNodeFactory.instance.objectNode();
    ArrayNode groups = result.putArray("byChildType");
    if (childTypes == null || childTypes.isEmpty()) return result;
    JsonNode root = active().workspace().snapshot();
    List<InspectedElement> elements = new ArrayList<>();
    collectInspected(root, null, null, elements);
    java.util.LinkedHashSet<String> exactChildTypes = new java.util.LinkedHashSet<>();
    for (String childType : childTypes) {
      if (childType == null || childType.isBlank()) continue;
      try {
        exactChildTypes.add(contracts.require(active().level(), childType.trim()).eClass());
      } catch (PlatformException ignored) {
        // The primary validation diagnostic already identifies an unknown EClass. Do not let
        // optional repair context obscure it.
      }
    }
    for (String childType : exactChildTypes) {
      ObjectNode group = groups.addObject();
      group.put("childType", childType);
      ArrayNode candidates = group.putArray("candidates");
      elements.stream()
          .filter(element -> eligibleContainments(element.eClass(), childType).size() > 0)
          .limit(80)
          .forEach(
              element -> {
                ObjectNode candidate = candidates.addObject();
                candidate.put("id", element.id());
                candidate.put("eClass", element.eClass());
                if (element.name() != null && !element.name().isBlank()) {
                  candidate.put("name", element.name());
                }
                ArrayNode containments = candidate.putArray("containments");
                eligibleContainments(element.eClass(), childType).forEach(containments::add);
              });
    }
    return result;
  }

  private List<String> eligibleContainments(String ownerType, String childType) {
    try {
      return contracts.require(active().level(), ownerType).references().stream()
          .filter(ReferenceContract::containment)
          .filter(
              reference ->
                  contracts.assignable(active().level(), childType, reference.targetType()))
          .map(ReferenceContract::name)
          .sorted()
          .toList();
    } catch (PlatformException ignored) {
      return List.of();
    }
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
    Map<String, Set<String>> createdReferenceAssignments = new LinkedHashMap<>();
    for (ModelCommandBatch.Create create : batch.creates()) {
      String clientRef = blank(create.clientRef());
      TypeContract type = contracts.require(active.level(), create.eClass());
      if (clientRef == null)
        throw new PlatformException(422, "Every create requires a non-empty clientRef.");
      if (!type.creatable())
        throw new PlatformException(422, "Type '" + type.eClass() + "' is not creatable.");
      if (isRootModelType(type.eClass(), active.workspace().snapshot()))
        throw new PlatformException(
            422,
            "The model root already exists. Update elementId 'rootId' instead of creating another "
                + type.eClass()
                + ".");
      if (refs.containsKey(clientRef))
        throw new PlatformException(422, "Duplicate clientRef: " + clientRef);
      String requestedOwner = blank(create.owner());
      String requestedReference = blank(create.reference());
      ObjectNode attributes = tools.jackson.databind.node.JsonNodeFactory.instance.objectNode();
      if (create.attributes() != null) create.attributes().forEach(attributes::set);
      attributes = normalizeAttributes(type, attributes);
      requireCreateAttributes(clientRef, type, attributes);
      if (requestedReference == null
          && (requestedOwner == null || "rootId".equals(requestedOwner))) {
        java.util.Optional<ReferenceContract> rootContainment =
            contracts.rootContainment(active.level(), type.eClass());
        if (rootContainment.isPresent()) {
          // This is the paper's deterministic compiler stage: when Ecore provides one exact
          // root containment, filling its mechanical owner/feature syntax cannot alter the
          // LLM-authored business content or intent.
          requestedOwner = "rootId";
          requestedReference = rootContainment.get().name();
        }
      }
      if (requestedOwner == null || requestedReference == null) {
        throw new PlatformException(
            422, "Create '" + clientRef + "' requires explicit owner and containment feature.");
      }
      String elementId = UUID.randomUUID().toString();
      refs.put(clientRef, elementId);
      createdTypes.put(elementId, type.eClass());
      createdAttributes.put(elementId, attributes);
      createdReferenceAssignments.put(elementId, new java.util.LinkedHashSet<>());
      String ownerId = resolveExistingOrBatchRef(requestedOwner, refs, "Create owner");
      String ownerTypeName =
          rootId.equals(ownerId)
              ? active.workspace().snapshot().path("eClass").asText("")
              : createdTypes.get(ownerId);
      if (ownerTypeName == null || ownerTypeName.isBlank())
        ownerTypeName = find(active.workspace().snapshot(), ownerId).path("eClass").asText("");
      String containment =
          requireContainmentReference(
              active.level(), ownerTypeName, requestedReference, type.eClass());
      if (createdReferenceAssignments.containsKey(ownerId)) {
        createdReferenceAssignments.get(ownerId).add(containment);
      }
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
      String id = resolveExistingOrBatchRef(update.elementId(), refs, "Update element");
      if (createdTypes.containsKey(id)) {
        String createdId = id;
        TypeContract createdType = contracts.require(active.level(), createdTypes.get(id));
        ObjectNode attributes = tools.jackson.databind.node.JsonNodeFactory.instance.objectNode();
        if (update.attributes() != null) update.attributes().forEach(attributes::set);
        ObjectNode normalized = normalizeAttributes(createdType, attributes);
        normalized
            .properties()
            .forEach(
                entry -> createdAttributes.get(createdId).set(entry.getKey(), entry.getValue()));
        continue;
      }
      String existingId = id;
      JsonNode element = find(active.workspace().snapshot(), id);
      // The turn-level expectedRevision is the authoritative optimistic concurrency guard.
      // Provider-generated update hashes are advisory for non-destructive edits; keeping them
      // hard-failing made normal edits conflict even when the session revision was current.
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
                          existingId,
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
      String targetType = createdTypes.get(target);
      if (targetType == null)
        targetType = find(active.workspace().snapshot(), target).path("eClass").asText();
      ReferenceContract reference =
          normalizeConnectionReference(
              active.level(), sourceType, targetType, connection.reference());
      if (reference == null) {
        throw new PlatformException(
            422,
            "Connection reference '"
                + connection.reference()
                + "' is not writable on "
                + sourceType.eClass()
                + ". Valid writable references: "
                + writableReferenceAlternatives(sourceType)
                + ".");
      }
      if (!contracts.assignable(active.level(), targetType, reference.targetType())) {
        throw new PlatformException(
            422,
            "Connection "
                + sourceType.eClass()
                + "."
                + reference.name()
                + " requires target "
                + reference.targetType()
                + " but "
                + target
                + " is "
                + targetType
                + ". Valid writable references on "
                + sourceType.eClass()
                + ": "
                + writableReferenceAlternatives(sourceType)
                + ".");
      }
      operations.add(
          new Operation(
              OperationType.CONNECT_ELEMENTS, target, targetType, null, source, reference.name()));
      if (createdReferenceAssignments.containsKey(source)) {
        createdReferenceAssignments.get(source).add(reference.name());
      }
    }
    requireCreateReferences(active.level(), refs, createdTypes, createdReferenceAssignments);
    for (ModelCommandBatch.Deletion deletion : batch.deletions()) {
      String id = resolveExistingOrBatchRef(deletion.elementId(), refs, "Deletion element");
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
    for (ModelCommandBatch.Evidence evidence : batch.evidence()) {
      resolveExistingOrBatchRef(evidence.elementRef(), refs, "Evidence elementRef");
    }
    SemanticModelPatch semantic = new SemanticModelPatch(operations);
    ModelWorkspace.MutationResult result =
        commandCompiler == null
            ? active.workspace().mutate(semantic)
            : mutateAtomically(active.workspace(), semantic);
    // Provider clientRefs are valid only inside this patch.  Persisted provenance must point at
    // stable model UUIDs so evidence can be navigated and audited after the turn completes.
    List<ModelCommandBatch.Evidence> resolvedEvidence =
        batch.evidence().stream()
            .map(
                evidence ->
                    new ModelCommandBatch.Evidence(
                        refs.getOrDefault(evidence.elementRef(), evidence.elementRef()),
                        evidence.sourceUnitId(),
                        evidence.requirementId(),
                        evidence.kind(),
                        evidence.assumption()))
            .toList();
    committedBatch =
        new ModelCommandBatch(
            batch.creates(),
            batch.updates(),
            batch.connections(),
            batch.deletions(),
            resolvedEvidence,
            batch.planSummary(),
            batch.turnComplete());
    return result;
  }

  private ReferenceContract normalizeConnectionReference(
      ModelLevel level, TypeContract sourceType, String targetType, String requestedReference) {
    ReferenceContract named =
        sourceType.references().stream()
            .filter(
                ref ->
                    ref.name().equals(requestedReference) && !ref.containment() && !ref.readonly())
            .findFirst()
            .orElse(null);
    if (named != null && contracts.assignable(level, targetType, named.targetType())) return named;
    List<ReferenceContract> compatible =
        sourceType.references().stream()
            .filter(reference -> !reference.containment() && !reference.readonly())
            .filter(reference -> contracts.assignable(level, targetType, reference.targetType()))
            .toList();
    // This is Ecore-derived normalization, not a name alias: when the source and target types
    // admit exactly one writable structural edge there is no ambiguous modeling choice.
    return compatible.size() == 1 ? compatible.get(0) : named;
  }

  private void requireCreateAttributes(String clientRef, TypeContract type, ObjectNode attributes) {
    List<String> missing =
        type.attributes().stream()
            .filter(AttributeContract::required)
            .map(AttributeContract::name)
            .filter(name -> !"id".equals(name))
            .filter(name -> !attributes.hasNonNull(name))
            .toList();
    if (!missing.isEmpty()) {
      throw new PlatformException(
          422,
          "Create '"
              + clientRef
              + "' of "
              + type.eClass()
              + " is missing required attributes "
              + missing
              + ". Add them using the exact Ecore contract or remove this create.");
    }
  }

  private void requireCreateReferences(
      ModelLevel level,
      Map<String, String> refs,
      Map<String, String> createdTypes,
      Map<String, Set<String>> assignments) {
    Map<String, String> clientRefsById = new LinkedHashMap<>();
    refs.forEach((clientRef, id) -> clientRefsById.putIfAbsent(id, clientRef));
    for (Map.Entry<String, String> created : createdTypes.entrySet()) {
      TypeContract type = contracts.require(level, created.getValue());
      Set<String> assigned = assignments.getOrDefault(created.getKey(), Set.of());
      List<String> missing =
          type.references().stream()
              .filter(ReferenceContract::required)
              .filter(reference -> !reference.readonly())
              .map(ReferenceContract::name)
              .filter(reference -> !assigned.contains(reference))
              .toList();
      if (!missing.isEmpty()) {
        String clientRef = clientRefsById.getOrDefault(created.getKey(), created.getKey());
        throw new PlatformException(
            422,
            "Create '"
                + clientRef
                + "' of "
                + type.eClass()
                + " is missing required Ecore references "
                + missing
                + ". Add compatible creates and connections/containments from this exact"
                + " clientRef, or remove this create. Required reference contracts: "
                + type.references().stream()
                    .filter(ReferenceContract::required)
                    .filter(reference -> !reference.readonly())
                    .map(
                        reference ->
                            reference.name()
                                + "->"
                                + reference.targetType()
                                + (reference.containment() ? " containment" : " connection"))
                    .toList()
                + ".");
      }
    }
  }

  private List<String> writableReferenceAlternatives(TypeContract sourceType) {
    return sourceType.references().stream()
        .filter(reference -> !reference.containment() && !reference.readonly())
        .map(
            reference ->
                reference.name()
                    + "->"
                    + reference.targetType()
                    + (reference.required() ? " required" : " optional")
                    + (reference.many() ? " many" : " one"))
        .sorted()
        .toList();
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

  private String requireContainmentReference(
      ModelLevel level, String ownerTypeName, String requestedReference, String childTypeName) {
    TypeContract ownerType = contracts.require(level, ownerTypeName);
    ReferenceContract exact =
        ownerType.references().stream()
            .filter(ReferenceContract::containment)
            .filter(reference -> reference.name().equals(requestedReference))
            .filter(reference -> contracts.assignable(level, childTypeName, reference.targetType()))
            .findFirst()
            .orElse(null);
    if (exact != null) return exact.name();
    String validContainments =
        ownerType.references().stream()
            .filter(ReferenceContract::containment)
            .filter(reference -> contracts.assignable(level, childTypeName, reference.targetType()))
            .map(reference -> reference.name() + "->" + reference.targetType())
            .sorted()
            .toList()
            .toString();
    throw new PlatformException(
        422,
        "Containment '"
            + requestedReference
            + "' cannot create "
            + childTypeName
            + " under "
            + ownerTypeName
            + ". Valid exact containments for this child: "
            + validContainments
            + ". Valid Ecore construction placements across the model: "
            + contracts.containmentPlacements(level, childTypeName)
            + ". Use rootId only when the listed owner is the model root; otherwise create or use"
            + " an element of the listed owner EClass and pass its exact clientRef/id.");
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
                    422,
                    "Attribute '"
                        + name
                        + "' is not writable on "
                        + type.eClass()
                        + ". Valid exact attributes: "
                        + legal.keySet()
                        + ".");
              }
              if (!contract.enumLiterals().isEmpty()
                  && value != null
                  && !contract.enumLiterals().contains(value.asText())) {
                throw new PlatformException(
                    422,
                    "Invalid enum value '"
                        + value.asText()
                        + "' for "
                        + type.eClass()
                        + "."
                        + name
                        + ". Allowed values: "
                        + contract.enumLiterals());
              }
              normalized.set(name, value);
            });
    return normalized;
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

  private boolean isRootModelType(String eClass, JsonNode snapshot) {
    String rootType = snapshot == null ? "" : snapshot.path("eClass").asText("");
    return eClass != null && !eClass.isBlank() && eClass.equals(rootType);
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

  private record InspectedElement(
      String id,
      String eClass,
      String name,
      String ownerId,
      String ownerFeature,
      List<String> outgoingReferences) {}
}
