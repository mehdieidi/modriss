package io.mehdieidi.varka.platform.assistant.agent;

import io.mehdieidi.varka.platform.assistant.application.ProviderCallBudget;
import io.mehdieidi.varka.platform.assistant.config.AiProperties;
import io.mehdieidi.varka.platform.assistant.domain.ModelCommandBatch;
import io.mehdieidi.varka.platform.assistant.metamodel.MetamodelGuideGenerator;
import io.mehdieidi.varka.platform.assistant.metamodel.MetamodelKnowledgeService.AttributeContract;
import io.mehdieidi.varka.platform.assistant.metamodel.MetamodelKnowledgeService.ReferenceContract;
import io.mehdieidi.varka.platform.assistant.metamodel.MetamodelKnowledgeService.TypeContract;
import io.mehdieidi.varka.platform.assistant.metamodel.TypeContractService;
import io.mehdieidi.varka.platform.assistant.provider.AssistantModelProvider;
import io.mehdieidi.varka.platform.assistant.tools.AgentModelTools;
import io.mehdieidi.varka.platform.assistant.turn.AssistantTurnStore;
import io.mehdieidi.varka.platform.assistant.workspace.ModelWorkspace;
import io.mehdieidi.varka.platform.kernel.ModelLevel;
import io.mehdieidi.varka.platform.kernel.PlatformException;
import io.mehdieidi.varka.platform.model.application.ModelService;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.JsonNodeFactory;

/**
 * The paper's two-stage instance-generation workflow: conceptual JSON first, deterministic Ecore
 * compilation second. The compiler accepts the paper's parent-to-child composition dialect and adds
 * only project-level persistence, source-evidence, atomic validation, and repair controls.
 */
public final class ConceptualInstanceModelWorkflow {
  private static final Logger log = LoggerFactory.getLogger(ConceptualInstanceModelWorkflow.class);
  private final AssistantModelProvider provider;
  private final MetamodelGuideGenerator guides;
  private final TypeContractService contracts;
  private final AiProperties properties;
  private final ObjectMapper mapper = new ObjectMapper();

  public ConceptualInstanceModelWorkflow(
      AssistantModelProvider provider,
      MetamodelGuideGenerator guides,
      TypeContractService contracts,
      AiProperties properties) {
    this.provider = java.util.Objects.requireNonNull(provider, "provider");
    this.guides = java.util.Objects.requireNonNull(guides, "guides");
    this.contracts = java.util.Objects.requireNonNull(contracts, "contracts");
    this.properties = java.util.Objects.requireNonNull(properties, "properties");
  }

  /** OpenAI-compatible schema for the paper IR plus optional Varka source evidence. */
  public static String jsonSchema() {
    return "{\"type\":\"object\",\"additionalProperties\":{\"type\":\"object\","
               + "\"required\":[\"type\",\"attributes\",\"associations\"],"
               + "\"properties\":{\"type\":{\"type\":\"string\",\"minLength\":1},"
               + "\"attributes\":{\"type\":\"array\",\"items\":{\"type\":\"object\","
               + "\"required\":[\"dataType\",\"attributeName\",\"value\"],\"properties\":{"
               + "\"dataType\":{\"type\":\"string\"},\"attributeName\":{\"type\":\"string\",\"minLength\":1},"
               + "\"value\":{}}}},\"associations\":{\"type\":\"object\",\"required\":[\"compositions\",\"references\"],"
               + "\"properties\":{\"compositions\":{\"type\":\"array\",\"items\":{\"$ref\":\"#/$defs/association\"}},"
               + "\"references\":{\"type\":\"array\",\"items\":{\"$ref\":\"#/$defs/association\"}}}},"
               + "\"evidence\":{\"type\":\"array\",\"items\":{\"type\":\"object\","
               + "\"required\":[\"sourceUnitId\",\"kind\"],\"properties\":{"
               + "\"sourceUnitId\":{\"type\":\"string\"},\"requirementId\":{\"type\":\"string\"},"
               + "\"kind\":{\"type\":\"string\",\"enum\":[\"SOURCE_GROUNDED\",\"INFERRED\"]},"
               + "\"assumption\":{\"type\":\"string\"}}}}}},"
               + "\"$defs\":{\"association\":{\"type\":\"object\",\"required\":[\"associationName\",\"associatedClassName\",\"instanceID\"],"
               + "\"properties\":{\"associationName\":{\"type\":\"string\",\"minLength\":1},"
               + "\"associatedClassName\":{\"type\":\"string\",\"minLength\":1},\"instanceID\":{\"type\":\"string\",\"minLength\":1}}}}}";
  }

  /** Strict allowlisted schema for the semantic metamodel-contract selection pass. */
  public static String typeSelectionSchema() {
    return "{\"type\":\"object\",\"required\":[\"types\"],\"additionalProperties\":false,"
        + "\"properties\":{\"types\":{\"type\":\"array\",\"minItems\":1,\"maxItems\":8,"
        + "\"uniqueItems\":true,\"items\":{\"type\":\"string\",\"minLength\":1}}}}";
  }

  public AgentTurnLoop.TurnResult run(
      String sessionId,
      ModelLevel level,
      String request,
      ModelWorkspace workspace,
      AgentModelTools tools,
      boolean destructiveConfirmed) {
    String correction = "";
    String previous = "";
    AssistantModelProvider.AssistantReply last = null;
    UsageAudit audit = new UsageAudit();
    int conceptualAttempts = Math.max(1, properties.maxRepairAttempts() + 1);
    int maxCalls = conceptualAttempts + 2;
    ProviderCallBudget.bind(maxCalls);
    try {
      String metamodel;
      try {
        metamodel = selectMetamodelContracts(level, request, workspace.snapshot(), audit);
      } catch (RuntimeException failure) {
        throw new AgentTurnLoop.TurnExecutionException(
            asPlatformException(failure),
            Math.max(audit.callDetails.size(), ProviderCallBudget.count()),
            audit.promptTokens,
            audit.completionTokens,
            audit.callDetails);
      }
      for (int attempt = 0; attempt < conceptualAttempts; attempt++) {
        String system = system(level);
        String user = prompt(request, workspace.snapshot(), metamodel, correction, previous);
        try {
          last = audit.call(system, user, "conceptual_instance_model");
        } catch (RuntimeException failure) {
          if (attempt + 1 < conceptualAttempts && truncated(failure)) {
            previous = "";
            correction =
                "The prior complete response was truncated by the provider. Return a smaller"
                    + " complete model within the stated object boundary, retaining source coverage"
                    + " and required structural content.";
            log.warn(
                "Conceptual provider response was truncated; requesting a smaller complete"
                    + " document");
            continue;
          }
          throw new AgentTurnLoop.TurnExecutionException(
              asPlatformException(failure),
              Math.max(audit.callDetails.size(), ProviderCallBudget.count()),
              audit.promptTokens,
              audit.completionTokens,
              audit.callDetails);
        }
        try {
          ConceptualModel conceptual = ConceptualModel.parse(mapper, last.content());
          ModelCommandBatch batch = conceptual.commands(workspace.snapshot(), contracts, level);
          tools.commitModelBatch(batch, destructiveConfirmed);
          ModelService.ValidationResult validation = tools.validateModel();
          if (validation == null || !validation.valid()) {
            throw new PlatformException(
                422,
                "Structural validation rejected the conceptual model: "
                    + (validation == null ? "no result" : validation.issues()));
          }
          return new AgentTurnLoop.TurnResult(
              "Applied the conceptual instance model and structurally validated the result.",
              workspace.patch(),
              workspace.inversePatch(),
              validation,
              last.provider(),
              last.model(),
              batch,
              Math.max(audit.callDetails.size(), ProviderCallBudget.count()),
              audit.promptTokens,
              audit.completionTokens,
              List.copyOf(audit.callDetails),
              null,
              null,
              null);
        } catch (RuntimeException failure) {
          if (attempt + 1 >= conceptualAttempts) {
            throw new AgentTurnLoop.TurnExecutionException(
                asPlatformException(failure),
                Math.max(audit.callDetails.size(), ProviderCallBudget.count()),
                audit.promptTokens,
                audit.completionTokens,
                audit.callDetails);
          }
          previous = last.content();
          correction = repairInstruction(failure.getMessage());
          log.warn(
              "Conceptual compiler rejected response attempt={} diagnostic={}",
              attempt + 1,
              safe(failure.getMessage()));
        }
      }
    } finally {
      ProviderCallBudget.clear();
    }
    throw new PlatformException(502, "Conceptual instance-model generation did not complete.");
  }

  private PlatformException asPlatformException(RuntimeException failure) {
    return failure instanceof PlatformException platform
        ? platform
        : new PlatformException(422, safe(failure.getMessage()), failure);
  }

  private boolean truncated(RuntimeException failure) {
    String message = safe(failure.getMessage()).toLowerCase(java.util.Locale.ROOT);
    return message.contains("finish_reason=length")
        || message.contains("no structured json content")
        || message.contains("truncated");
  }

  private String system(ModelLevel level) {
    return "You generate a complete conceptual instance model for Varka's "
        + level.name()
        + " DSML. The compiler, not you, creates EMF/XMI mutations and persisted UUIDs. Return JSON"
        + " only.\n"
        + "Use only exact EClasses, attributes, data types, enum literals, and associations in the"
        + " authoritative metamodel. Do not infer missing business facts. New JSON keys are"
        + " temporary instance IDs. For an existing element, use its exact persisted id as the key"
        + " and repeat its exact EClass.\n"
        + "Use the paper IR exactly: each value has type,"
        + " attributes:[{dataType,attributeName,value}], and"
        + " associations:{compositions:[{associationName,associatedClassName,instanceID}],references:[...]}."
        + " A composition is written on the PARENT object and instanceID identifies its CHILD. A"
        + " reference is written on its source object and instanceID identifies its target. Do not"
        + " reverse either edge. The existing model root is implicit and must not be generated."
        + " When a new object has no incoming composition, the compiler may place it under the root"
        + " only if Ecore admits one unambiguous root containment.\n"
        + "Every associationName and associatedClassName must be exact and case-sensitive. All"
        + " referenced instance IDs must be keys in this complete response or exact persisted IDs."
        + " Objects are independent of JSON order. Omission never deletes an existing object,"
        + " attribute, or relationship. Include existing objects only when updating their"
        + " attributes or when they own new associations. Do not emit deletions or moves.\n"
        + "For source-backed work, add optional evidence on affected objects as"
        + " [{sourceUnitId,requirementId,kind,assumption}]. Use kind SOURCE_GROUNDED only with an"
        + " exact supplied source-unit id; use INFERRED only for an explicit design assumption."
        + " Account for every supplied relevant source unit. Return a corrected COMPLETE conceptual"
        + " model after diagnostics, preserving every valid object and association from the prior"
        + " response. Do not include reasoning or markdown.";
  }

  private String prompt(
      String request, JsonNode current, String metamodel, String correction, String previous) {
    StringBuilder prompt =
        new StringBuilder(
                "METAMODEL (complete authoritative Ecore-derived contracts selected for this"
                    + " response and their construction/required-reference closure; a=attributes,"
                    + " c=containments, r=references, *=required):\n")
            .append(metamodel)
            .append(
                "\n\n"
                    + "CURRENT MODEL (complete persisted semantic inventory; nested owner/feature"
                    + " and outgoing references are authoritative):\n")
            .append(compactSnapshot(current))
            .append("\n\nREQUEST AND SOURCE SPECIFICATION:\n")
            .append(request == null ? "" : request);
    boolean sourceBacked =
        request != null && request.contains("SOURCE SPECIFICATION (authoritative input)");
    prompt.append(
        "\n\nCONTEXT-SIZE BOUNDARY: Return at most "
            + (sourceBacked ? "8" : "10")
            + " semantically important objects in this atomic response. Prefer meaningful"
            + " relationships, complete supplied-source evidence, and required features; omit"
            + " optional decorative attributes. Every supplied source-unit id must occur in"
            + " evidence, and one object may cite multiple source units. Further coherent growth"
            + " belongs in a durable slice.");
    if (correction != null && !correction.isBlank()) {
      prompt
          .append("\n\nREJECTED COMPLETE CONCEPTUAL MODEL:\n")
          .append(previous == null ? "" : previous)
          .append("\n\nCOMPILER DIAGNOSTIC AND REPAIR REQUIREMENT:\n")
          .append(correction);
    }
    return prompt
        .append("\n\nReturn one complete JSON object keyed by instanceID. No markdown fences.")
        .toString();
  }

  private String selectMetamodelContracts(
      ModelLevel level, String request, JsonNode current, UsageAudit audit) {
    String system =
        "Select the exact EClasses needed to model the request as a conceptual instance model. This"
            + " is a semantic modeling decision: choose all object, relationship, policy, contract,"
            + " and supporting types needed for meaningful nodes and edges. Choose between 4 and 7"
            + " focused types (8 absolute maximum); never copy the full type index. Prefer a"
            + " coherent minimal vocabulary over unrelated alternatives. Use only case-sensitive"
            + " names from the supplied authoritative type index. Do not generate model content or"
            + " explain. Return {\"types\":[...]}.";
    String baseUser =
        guides.index(level)
            + "\n\nCURRENT MODEL TYPES (existing IDs will be supplied to the generation pass):\n"
            + currentTypes(current)
            + "\n\nREQUEST AND SOURCE SPECIFICATION:\n"
            + (request == null ? "" : request);
    String correction = "";
    for (int attempt = 0; attempt < 2; attempt++) {
      AssistantModelProvider.AssistantReply reply =
          audit.call(system, baseUser + correction, "conceptual_type_selection");
      try {
        JsonNode selected = mapper.readTree(reply.content());
        if (!selected.path("types").isArray() || selected.path("types").isEmpty()) {
          throw new PlatformException(
              422, "Conceptual type selection must contain at least one EClass.");
        }
        LinkedHashSet<String> names = new LinkedHashSet<>();
        for (JsonNode item : selected.path("types")) {
          String name = item.asText("").trim();
          if (name.isBlank()) continue;
          names.add(contracts.require(level, name).eClass());
        }
        if (names.size() > 8) {
          throw new PlatformException(
              422,
              "Conceptual type selection contains "
                  + names.size()
                  + " types; the strict maximum is 8.");
        }
        collectTypeNames(current, names);
        String guide =
            guides.generateForTypes(
                level, contracts.requiredContainmentClosure(level, new ArrayList<>(names)));
        log.info(
            "Conceptual Ecore contract selection level={} requestedTypes={} guideChars={}",
            level,
            names,
            guide.length());
        return guide;
      } catch (Exception failure) {
        if (attempt == 1) {
          if (failure instanceof RuntimeException runtime) throw runtime;
          throw new PlatformException(
              422, "LLM did not return parseable conceptual type selection JSON.");
        }
        correction =
            "\n\nThe prior selection was rejected: "
                + safe(failure.getMessage())
                + " Return a corrected focused selection of at most 8 exact EClass names; do not"
                + " copy the full index.";
      }
    }
    throw new PlatformException(422, "Conceptual type selection failed.");
  }

  private String currentTypes(JsonNode current) {
    LinkedHashSet<String> types = new LinkedHashSet<>();
    collectTypeNames(current, types);
    return String.join(",", types);
  }

  private void collectTypeNames(JsonNode node, Set<String> result) {
    if (node == null) return;
    if (node.isObject() && node.hasNonNull("eClass")) result.add(node.path("eClass").asText());
    if (node.isContainer()) node.forEach(child -> collectTypeNames(child, result));
  }

  private final class UsageAudit {
    private long promptTokens;
    private long completionTokens;
    private final List<AssistantTurnStore.ProviderCall> callDetails = new ArrayList<>();

    private AssistantModelProvider.AssistantReply call(
        String system, String user, String requiredTool) {
      long started = System.nanoTime();
      AssistantModelProvider.AssistantReply reply =
          provider.completeStructured(
              new AssistantModelProvider.AssistantPrompt(
                  system, user, List.of(), List.of(), requiredTool));
      long latency = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
      if (reply.usage().reported()) {
        promptTokens += Math.max(0, reply.usage().promptTokens());
        completionTokens += Math.max(0, reply.usage().completionTokens());
      }
      callDetails.add(
          new AssistantTurnStore.ProviderCall(
              reply.provider(),
              reply.model(),
              latency,
              reply.usage().promptTokens(),
              reply.usage().completionTokens(),
              reply.usage().reported(),
              reply.systemPrompt() == null ? system : reply.systemPrompt(),
              reply.userPrompt() == null ? user : reply.userPrompt()));
      return reply;
    }
  }

  private String compactSnapshot(JsonNode current) {
    if (current == null || !current.isContainer()) return "{}";
    List<SnapshotElement> elements = new ArrayList<>();
    collectElements(current, null, null, elements);
    var root = JsonNodeFactory.instance.objectNode();
    root.put("rootId", current.path("id").asText("rootId"));
    root.put("rootType", current.path("eClass").asText(""));
    var result = root.putArray("elements");
    for (SnapshotElement element : elements) {
      var item = result.addObject();
      item.put("id", element.node().path("id").asText());
      item.put("type", element.node().path("eClass").asText());
      if (element.ownerId() != null) item.put("ownerId", element.ownerId());
      if (element.ownerFeature() != null) item.put("ownerFeature", element.ownerFeature());
      var attributes = item.putObject("attributes");
      var references = item.putObject("references");
      element
          .node()
          .properties()
          .forEach(
              entry -> {
                String name = entry.getKey();
                JsonNode value = entry.getValue();
                if (name.equals("id") || name.equals("eClass") || value.isNull()) return;
                if (value.isValueNode()) {
                  attributes.set(name, value.deepCopy());
                } else if (isIdReference(value)) {
                  references.set(name, value.deepCopy());
                }
              });
      if (attributes.isEmpty()) item.remove("attributes");
      if (references.isEmpty()) item.remove("references");
    }
    return root.toString();
  }

  private void collectElements(
      JsonNode node, String ownerId, String ownerFeature, List<SnapshotElement> result) {
    if (node == null) return;
    if (node.isObject() && node.hasNonNull("eClass") && node.hasNonNull("id")) {
      result.add(new SnapshotElement(node, ownerId, ownerFeature));
      String id = node.path("id").asText();
      node.properties()
          .forEach(
              entry -> {
                JsonNode value = entry.getValue();
                if (value.isObject() && value.hasNonNull("eClass")) {
                  collectElements(value, id, entry.getKey(), result);
                } else if (value.isArray()) {
                  for (JsonNode child : value) {
                    if (child.isObject() && child.hasNonNull("eClass")) {
                      collectElements(child, id, entry.getKey(), result);
                    }
                  }
                }
              });
      return;
    }
    if (node.isContainer())
      node.forEach(child -> collectElements(child, ownerId, ownerFeature, result));
  }

  private boolean isIdReference(JsonNode value) {
    if (value == null) return false;
    if (value.isTextual()) return !value.asText().isBlank();
    if (!value.isArray() || value.isEmpty()) return false;
    for (JsonNode item : value) if (!item.isTextual()) return false;
    return true;
  }

  private String repairInstruction(String message) {
    return "The deterministic Ecore compiler rejected the response below. Return a corrected"
        + " complete conceptual model, preserving all valid content and exact existing IDs."
        + " Do not return a delta, explanation, or partial fragment. Diagnostic:\n"
        + safe(message);
  }

  private static String safe(String message) {
    return message == null
        ? "unknown compiler error"
        : message.substring(0, Math.min(6000, message.length()));
  }

  /** Paper-compatible flat conceptual representation and deterministic command compiler. */
  static final class ConceptualModel {
    private final Map<String, JsonNode> objects;

    private ConceptualModel(Map<String, JsonNode> objects) {
      this.objects = objects;
    }

    static ConceptualModel parse(ObjectMapper mapper, String content) {
      String json = content == null ? "" : content.trim();
      if (json.startsWith("```")) {
        json = json.replaceFirst("^```(?:json)?\\s*", "").replaceFirst("\\s*```$", "").trim();
      }
      if (!json.startsWith("{")) {
        int start = json.indexOf('{');
        int end = json.lastIndexOf('}');
        if (start >= 0 && end > start) json = json.substring(start, end + 1);
      }
      JsonNode root;
      try {
        root = mapper.readTree(json);
      } catch (Exception ex) {
        throw new PlatformException(422, "LLM did not return parseable conceptual-model JSON.");
      }
      if (root == null || !root.isObject()) {
        throw new PlatformException(422, "Conceptual model must be a JSON object.");
      }
      Map<String, JsonNode> result = new LinkedHashMap<>();
      root.properties()
          .forEach(
              entry -> {
                String id = entry.getKey() == null ? "" : entry.getKey().trim();
                JsonNode value = entry.getValue();
                if (id.isBlank())
                  throw new PlatformException(422, "Every instanceID must be non-empty.");
                if (!value.isObject() || value.path("type").asText("").isBlank()) {
                  throw new PlatformException(
                      422, "Conceptual object '" + id + "' has no EClass type.");
                }
                if (value.path("attributes").isObject()) {
                  // Arvan occasionally preserves the conceptual meaning but emits a compact
                  // name/value map. This is protocol normalization only: the Ecore compiler still
                  // resolves and validates every exact datatype below.
                  var normalized = value.deepCopy();
                  var list = mapper.createArrayNode();
                  value
                      .path("attributes")
                      .properties()
                      .forEach(
                          attribute -> {
                            var item = list.addObject();
                            item.put("dataType", "");
                            item.put("attributeName", attribute.getKey());
                            item.set("value", attribute.getValue().deepCopy());
                          });
                  ((tools.jackson.databind.node.ObjectNode) normalized).set("attributes", list);
                  value = normalized;
                } else if (!value.path("attributes").isArray()) {
                  throw new PlatformException(
                      422, "Conceptual object '" + id + "' attributes must be an array.");
                }
                JsonNode associations = value.path("associations");
                if (!associations.isObject()
                    || !associations.path("compositions").isArray()
                    || !associations.path("references").isArray()) {
                  throw new PlatformException(
                      422,
                      "Conceptual object '"
                          + id
                          + "' must contain independent compositions and references arrays.");
                }
                result.put(id, value);
              });
      if (result.isEmpty())
        throw new PlatformException(422, "Conceptual model contains no objects.");
      return new ConceptualModel(result);
    }

    ModelCommandBatch commands(JsonNode current, TypeContractService contracts, ModelLevel level) {
      Map<String, String> existingTypes = new LinkedHashMap<>();
      collectIds(current, existingTypes);
      String persistedRootId = current.path("id").asText("rootId");
      String persistedRootType = current.path("eClass").asText(contracts.rootType(level));
      Map<String, String> aliases = new LinkedHashMap<>();
      Map<String, JsonNode> effectiveObjects = new LinkedHashMap<>();
      for (var entry : objects.entrySet()) {
        String requestedType = entry.getValue().path("type").asText();
        String effectiveId =
            !existingTypes.containsKey(entry.getKey())
                    && requestedType.equals(persistedRootType)
                    && requestedType.equals(contracts.rootType(level))
                ? persistedRootId
                : entry.getKey();
        aliases.put(entry.getKey(), effectiveId);
        if (effectiveObjects.putIfAbsent(effectiveId, entry.getValue()) != null) {
          throw new PlatformException(
              422, "Conceptual response identifies the model root more than once.");
        }
      }
      Map<String, TypeContract> types = new LinkedHashMap<>();
      Map<String, Map<String, JsonNode>> attributes = new LinkedHashMap<>();
      for (Map.Entry<String, JsonNode> entry : effectiveObjects.entrySet()) {
        String id = entry.getKey();
        TypeContract type = contracts.require(level, entry.getValue().path("type").asText());
        if (!type.creatable() && !existingTypes.containsKey(id)) {
          throw new PlatformException(
              422, "EClass '" + type.eClass() + "' is abstract or not creatable.");
        }
        if (existingTypes.containsKey(id) && !existingTypes.get(id).equals(type.eClass())) {
          throw new PlatformException(
              422,
              "Existing instance '"
                  + id
                  + "' has EClass "
                  + existingTypes.get(id)
                  + ", not "
                  + type.eClass()
                  + ". Use the exact persisted id and EClass together.");
        }
        types.put(id, type);
        attributes.put(id, attributes(id, entry.getValue().path("attributes"), type));
      }

      Map<String, Placement> placements = new LinkedHashMap<>();
      List<ModelCommandBatch.Connection> connections = new ArrayList<>();
      for (Map.Entry<String, JsonNode> entry : effectiveObjects.entrySet()) {
        String sourceId = entry.getKey();
        TypeContract sourceType = types.get(sourceId);
        compileAssociations(
            level,
            contracts,
            existingTypes,
            types,
            aliases,
            sourceId,
            sourceType,
            entry.getValue().path("associations").path("compositions"),
            true,
            placements,
            connections);
        compileAssociations(
            level,
            contracts,
            existingTypes,
            types,
            aliases,
            sourceId,
            sourceType,
            entry.getValue().path("associations").path("references"),
            false,
            placements,
            connections);
      }

      Map<String, JsonNode> pending = new LinkedHashMap<>();
      effectiveObjects.forEach(
          (id, value) -> {
            if (!existingTypes.containsKey(id)) pending.put(id, value);
          });
      List<ModelCommandBatch.Create> creates = new ArrayList<>();
      Set<String> ready = new LinkedHashSet<>(existingTypes.keySet());
      ready.add("rootId");
      while (!pending.isEmpty()) {
        int before = pending.size();
        for (var iterator = pending.entrySet().iterator(); iterator.hasNext(); ) {
          var entry = iterator.next();
          String id = entry.getKey();
          Placement placement = placements.get(id);
          if (placement == null) {
            List<String> rootPlacements = rootPlacements(contracts, level, types.get(id).eClass());
            if (rootPlacements.size() == 1) {
              placement = new Placement("rootId", rootPlacements.get(0));
            } else if (rootPlacements.isEmpty()) {
              throw new PlatformException(
                  422,
                  "New instance '"
                      + id
                      + "' of "
                      + types.get(id).eClass()
                      + " has no incoming composition and cannot be contained by the model root. "
                      + "Add one exact parent composition. Legal placements: "
                      + contracts.containmentPlacements(level, types.get(id).eClass())
                      + ".");
            } else {
              throw new PlatformException(
                  422,
                  "New instance '"
                      + id
                      + "' has ambiguous root containment "
                      + rootPlacements
                      + ". Add an exact parent composition.");
            }
          }
          if (!ready.contains(placement.ownerId())) continue;
          creates.add(
              new ModelCommandBatch.Create(
                  id,
                  types.get(id).eClass(),
                  attributes.get(id),
                  placement.ownerId(),
                  placement.feature(),
                  ""));
          ready.add(id);
          iterator.remove();
        }
        if (pending.size() == before) {
          throw new PlatformException(
              422,
              "Composition dependency cycle or unresolved owner among new instances: "
                  + pending.keySet()
                  + ". Every child must have one acyclic parent composition.");
        }
      }

      List<ModelCommandBatch.Update> updates = new ArrayList<>();
      for (String id : effectiveObjects.keySet()) {
        if (existingTypes.containsKey(id) && !attributes.get(id).isEmpty()) {
          updates.add(new ModelCommandBatch.Update(id, attributes.get(id), ""));
        }
      }
      List<ModelCommandBatch.Evidence> evidence = evidence(effectiveObjects);
      if (creates.isEmpty() && updates.isEmpty() && connections.isEmpty()) {
        throw new PlatformException(422, "Conceptual model contains no model changes.");
      }
      return new ModelCommandBatch(
          creates, updates, connections, List.of(), evidence, "Conceptual instance model", true);
    }

    private void compileAssociations(
        ModelLevel level,
        TypeContractService contracts,
        Map<String, String> existingTypes,
        Map<String, TypeContract> responseTypes,
        Map<String, String> aliases,
        String sourceId,
        TypeContract sourceType,
        JsonNode associations,
        boolean composition,
        Map<String, Placement> placements,
        List<ModelCommandBatch.Connection> connections) {
      for (JsonNode association : associations) {
        String name = association.path("associationName").asText("").trim();
        String targetId = association.path("instanceID").asText("").trim();
        targetId = aliases.getOrDefault(targetId, targetId);
        String declaredTarget = association.path("associatedClassName").asText("").trim();
        if (name.isBlank() || targetId.isBlank() || declaredTarget.isBlank()) {
          throw new PlatformException(
              422,
              "Association on instance '"
                  + sourceId
                  + "' requires associationName, associatedClassName, and instanceID.");
        }
        String actualTarget =
            responseTypes.containsKey(targetId)
                ? responseTypes.get(targetId).eClass()
                : existingTypes.get(targetId);
        if (actualTarget == null) {
          throw new PlatformException(
              422,
              "Association "
                  + sourceId
                  + "."
                  + name
                  + " targets unknown instanceID '"
                  + targetId
                  + "'. Include that new object in the complete response or use an exact persisted"
                  + " id.");
        }
        contracts.require(level, declaredTarget);
        if (!contracts.assignable(level, actualTarget, declaredTarget)) {
          throw new PlatformException(
              422,
              "Association "
                  + sourceId
                  + "."
                  + name
                  + " declares associatedClassName "
                  + declaredTarget
                  + " but instance '"
                  + targetId
                  + "' is "
                  + actualTarget
                  + ".");
        }
        ReferenceContract reference =
            sourceType.references().stream()
                .filter(item -> item.name().equals(name))
                .filter(item -> item.containment() == composition)
                .filter(item -> !item.readonly())
                .findFirst()
                .orElseThrow(
                    () ->
                        new PlatformException(
                            422,
                            (composition ? "Composition" : "Reference")
                                + " '"
                                + name
                                + "' is not writable on "
                                + sourceType.eClass()
                                + ". Legal "
                                + (composition ? "compositions" : "references")
                                + ": "
                                + sourceType.references().stream()
                                    .filter(item -> item.containment() == composition)
                                    .filter(item -> !item.readonly())
                                    .map(item -> item.name() + "->" + item.targetType())
                                    .toList()));
        if (!contracts.assignable(level, actualTarget, reference.targetType())) {
          throw new PlatformException(
              422,
              "Association "
                  + sourceType.eClass()
                  + "."
                  + name
                  + " requires "
                  + reference.targetType()
                  + " but target '"
                  + targetId
                  + "' is "
                  + actualTarget
                  + ".");
        }
        if (composition) {
          if (existingTypes.containsKey(targetId)) {
            throw new PlatformException(
                422,
                "Conceptual generation cannot move existing instance '"
                    + targetId
                    + "'. Use the inspect/contract-bound agent strategy for reparenting.");
          }
          Placement prior = placements.putIfAbsent(targetId, new Placement(sourceId, name));
          if (prior != null
              && (!prior.ownerId().equals(sourceId) || !prior.feature().equals(name))) {
            throw new PlatformException(
                422,
                "Instance '"
                    + targetId
                    + "' has multiple composition owners: "
                    + prior.ownerId()
                    + "."
                    + prior.feature()
                    + " and "
                    + sourceId
                    + "."
                    + name
                    + ".");
          }
        } else {
          connections.add(new ModelCommandBatch.Connection(sourceId, name, targetId));
        }
      }
    }

    private static List<String> rootPlacements(
        TypeContractService contracts, ModelLevel level, String type) {
      return contracts.rootContainments(level, type).stream()
          .map(ReferenceContract::name)
          .distinct()
          .toList();
    }

    private List<ModelCommandBatch.Evidence> evidence(Map<String, JsonNode> effectiveObjects) {
      List<ModelCommandBatch.Evidence> result = new ArrayList<>();
      for (Map.Entry<String, JsonNode> entry : effectiveObjects.entrySet()) {
        JsonNode items = entry.getValue().path("evidence");
        if (items.isMissingNode()) continue;
        if (!items.isArray()) {
          throw new PlatformException(
              422, "Evidence on conceptual object '" + entry.getKey() + "' must be an array.");
        }
        for (JsonNode item : items) {
          String kind = item.path("kind").asText("").trim().toUpperCase(java.util.Locale.ROOT);
          String sourceUnitId = item.path("sourceUnitId").asText("").trim();
          String assumption = item.path("assumption").asText("").trim();
          if (!kind.equals("SOURCE_GROUNDED") && !kind.equals("INFERRED")) {
            throw new PlatformException(422, "Evidence kind must be SOURCE_GROUNDED or INFERRED.");
          }
          if (kind.equals("SOURCE_GROUNDED") && sourceUnitId.isBlank()) {
            throw new PlatformException(422, "SOURCE_GROUNDED evidence requires sourceUnitId.");
          }
          if (kind.equals("INFERRED") && assumption.isBlank()) {
            throw new PlatformException(422, "INFERRED evidence requires an assumption.");
          }
          result.add(
              new ModelCommandBatch.Evidence(
                  entry.getKey(),
                  sourceUnitId,
                  item.path("requirementId").asText(""),
                  kind,
                  assumption));
        }
      }
      return result;
    }

    private static Map<String, JsonNode> attributes(String id, JsonNode node, TypeContract type) {
      Map<String, AttributeContract> legal = new LinkedHashMap<>();
      type.attributes().forEach(attribute -> legal.put(attribute.name(), attribute));
      Map<String, JsonNode> result = new LinkedHashMap<>();
      for (JsonNode item : node) {
        String name = item.path("attributeName").asText("").trim();
        String dataType = item.path("dataType").asText("").trim();
        if (name.isBlank()) {
          throw new PlatformException(
              422, "Attribute on conceptual object '" + id + "' has no name.");
        }
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
        if (!dataType.isBlank() && !dataType.equals(contract.type())) {
          throw new PlatformException(
              422,
              "Attribute "
                  + type.eClass()
                  + "."
                  + name
                  + " has dataType "
                  + dataType
                  + " but Ecore requires "
                  + contract.type()
                  + ".");
        }
        JsonNode value =
            item.get("value") == null ? JsonNodeFactory.instance.nullNode() : item.get("value");
        if (!contract.enumLiterals().isEmpty()
            && !value.isNull()
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
                  + contract.enumLiterals()
                  + ".");
        }
        if (result.putIfAbsent(name, value) != null) {
          throw new PlatformException(
              422, "Duplicate attribute '" + name + "' on conceptual object '" + id + "'.");
        }
      }
      return result;
    }

    private static void collectIds(JsonNode node, Map<String, String> ids) {
      if (node == null) return;
      if (node.isObject() && node.hasNonNull("id") && node.hasNonNull("eClass")) {
        ids.put(node.path("id").asText(), node.path("eClass").asText());
      }
      if (node.isContainer()) node.forEach(child -> collectIds(child, ids));
    }
  }

  private record Placement(String ownerId, String feature) {}

  private record SnapshotElement(JsonNode node, String ownerId, String ownerFeature) {}
}
