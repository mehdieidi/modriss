package io.mehdieidi.varka.platform.assistant.agent;

import io.mehdieidi.varka.platform.assistant.config.AiProperties;
import io.mehdieidi.varka.platform.assistant.domain.ModelCommandBatch;
import io.mehdieidi.varka.platform.assistant.metamodel.MetamodelGuideGenerator;
import io.mehdieidi.varka.platform.assistant.provider.AssistantModelProvider;
import io.mehdieidi.varka.platform.assistant.tools.AgentModelTools;
import io.mehdieidi.varka.platform.assistant.workspace.ModelWorkspace;
import io.mehdieidi.varka.platform.kernel.ModelLevel;
import io.mehdieidi.varka.platform.kernel.PlatformException;
import io.mehdieidi.varka.platform.model.application.ModelService;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.JsonNodeFactory;

/**
 * The paper's two-stage instance-generation workflow: conceptual JSON first, validated model
 * commands second. This class is deliberately independent of the general agent action loop.
 */
public final class ConceptualInstanceModelWorkflow {
  private final AssistantModelProvider provider;
  private final MetamodelGuideGenerator guides;
  private final AiProperties properties;
  private final ObjectMapper mapper = new ObjectMapper();

  public ConceptualInstanceModelWorkflow(
      AssistantModelProvider provider, MetamodelGuideGenerator guides, AiProperties properties) {
    this.provider = provider;
    this.guides = guides;
    this.properties = properties;
  }

  /** OpenAI-compatible JSON-schema hint for the paper IR (dynamic instance IDs are intentional). */
  public static String jsonSchema() {
    return "{\"type\":\"object\",\"additionalProperties\":{\"type\":\"object\","
        + "\"required\":[\"type\",\"attributes\",\"associations\"],"
        + "\"properties\":{\"type\":{\"type\":\"string\",\"minLength\":1},"
        + "\"attributes\":{\"type\":\"array\",\"items\":{\"type\":\"object\","
        + "\"required\":[\"attributeName\",\"value\"],\"properties\":{"
        + "\"dataType\":{\"type\":\"string\"},\"attributeName\":{\"type\":\"string\",\"minLength\":1},"
        + "\"value\":{}}}},\"associations\":{\"type\":\"object\",\"required\":[\"compositions\",\"references\"],"
        + "\"properties\":{\"compositions\":{\"type\":\"array\",\"items\":{\"$ref\":\"#/$defs/association\"}},"
        + "\"references\":{\"type\":\"array\",\"items\":{\"$ref\":\"#/$defs/association\"}}}}}},"
        + "\"$defs\":{\"association\":{\"type\":\"object\",\"required\":[\"associationName\",\"instanceID\"],"
        + "\"properties\":{\"associationName\":{\"type\":\"string\",\"minLength\":1},"
        + "\"associatedClassName\":{\"type\":\"string\"},\"instanceID\":{\"type\":\"string\",\"minLength\":1}}}}}";
  }

  public AgentTurnLoop.TurnResult run(
      String sessionId,
      ModelLevel level,
      String request,
      ModelWorkspace workspace,
      AgentModelTools tools,
      boolean destructiveConfirmed) {
    String correction = "";
    AssistantModelProvider.AssistantReply last = null;
    int calls = 0;
    for (int attempt = 0; attempt <= Math.max(0, properties.maxRepairAttempts()); attempt++) {
      calls++;
      String user = prompt(level, request, workspace.snapshot(), correction);
      last = provider.completeStructured(
          new AssistantModelProvider.AssistantPrompt(
              system(level), user, List.of(), List.of(), "conceptual_instance_model"));
      try {
        ConceptualModel conceptual = ConceptualModel.parse(mapper, last.content());
        ModelCommandBatch batch = conceptual.commands(workspace.snapshot(), tools, level);
        tools.commitModelBatch(batch, destructiveConfirmed);
        ModelService.ValidationResult validation = tools.validateModel();
        if (validation == null || !validation.valid()) {
          throw new PlatformException(422, "Structural validation rejected the conceptual model: "
              + (validation == null ? "no result" : validation.issues()));
        }
        return new AgentTurnLoop.TurnResult(
            "Applied the conceptual instance model and structurally validated the result.",
            workspace.patch(), workspace.inversePatch(), validation,
            last.provider(), last.model(), batch, calls,
            Math.max(0, last.usage().promptTokens()), Math.max(0, last.usage().completionTokens()),
            List.of(), null, null, null);
      } catch (RuntimeException failure) {
        if (attempt >= Math.max(0, properties.maxRepairAttempts())) throw failure;
        correction = "The previous conceptual model was rejected by the deterministic compiler. "
            + "Return a corrected complete conceptual model, preserving valid existing IDs. "
            + "Re-evaluate every containment edge against the authoritative Ecore guide. "
            + "associationName means an EReference/containment feature, never an EAttribute. "
            + "Do not repeat an invalid owner or feature from the previous response. "
            + "Do not invent relationship/edge helper objects: represent relationships only with "
            + "references associations between real EClasses unless the guide explicitly defines "
            + "a creatable relationship EClass and a legal containment for it. "
            + "Do not explain. Compiler diagnostics:\n" + safe(failure.getMessage());
      }
    }
    throw new PlatformException(502, "Conceptual instance-model generation did not complete.");
  }

  private String system(ModelLevel level) {
    return "You generate a conceptual instance model for Varka's " + level.name() + " DSML.\n"
        + "The compiler, not you, creates XMI and UUIDs. Return JSON only.\n"
        + "Use only exact EClasses, attributes, enum literals, and associations in the metamodel. "
        + "Do not infer missing business facts. For an existing element, use its exact persisted "
        + "id as the JSON key so the compiler updates it. New keys are temporary instance IDs.\n"
        + "Each value is {type, attributes:[{dataType,attributeName,value}], associations:" 
        + "{compositions:[{associationName,associatedClassName,instanceID}], references:[...]}}.\n"
        + "Compositions are flat: every new child points to its owner instanceID. New top-level "
        + "objects must compose into instanceID rootId using the exact root containment feature. "
        + "Every new object MUST contain at least one composition association; never omit or leave "
        + "the compositions array empty. Top-level objects must use instanceID rootId and the exact "
        + "containment feature from the metamodel. "
        + "associationName is always an exact EReference name from the guide, never an attribute "
        + "name such as name, attributes, or value. Before returning, verify each owner EClass "
        + "actually declares the selected containment and the child EClass is assignable to it. "
        + "References use exact non-containment feature names. Include only requested or existing "
        + "model information needed for this request; never generate the model root. Keep the "
        + "conceptual response concise: omit optional attributes and unrelated objects, and do not "
        + "include reasoning, commentary, or duplicate representations. Unless the request clearly "
        + "requires more, keep the response below 2000 output tokens.";
  }

  private String prompt(ModelLevel level, String request, JsonNode current, String correction) {
    String snapshot = compactSnapshot(current);
    return "METAMODEL (authoritative retrieved Ecore contract; types lists every exact EClass; a=attributes,c=containments,r=references):\n"
        + guides.generateRelevant(level, request)
        + "\n\nCURRENT MODEL (authoritative persisted state):\n" + snapshot
        + "\n\nREQUEST:\n" + request
        + (correction == null || correction.isBlank() ? "" : "\n\nCORRECTION:\n" + correction)
        + "\n\nReturn one JSON object keyed by instanceID. No markdown fences.";
  }

  /** Keeps the complete persisted element inventory while dropping diagram/layout duplication. */
  private String compactSnapshot(JsonNode current) {
    if (current == null || !current.isContainer()) return "{}";
    List<JsonNode> elements = new ArrayList<>();
    collectElements(current, elements);
    var root = JsonNodeFactory.instance.objectNode();
    root.put("rootId", current.path("id").asText("rootId"));
    root.put("rootType", current.path("eClass").asText(""));
    var result = root.putArray("elements");
    for (JsonNode element : elements) {
      var item = JsonNodeFactory.instance.objectNode();
      item.put("id", element.path("id").asText());
      item.put("type", element.path("eClass").asText());
      element.properties().forEach(entry -> {
        String name = entry.getKey();
        JsonNode value = entry.getValue();
        if (name.equals("id") || name.equals("eClass") || value.isContainer()) return;
        if (value.isValueNode() && !value.isNull()) item.set(name, value.deepCopy());
      });
      result.add(item);
    }
    return root.toString();
  }

  private void collectElements(JsonNode node, List<JsonNode> result) {
    if (node == null) return;
    if (node.isObject() && node.hasNonNull("eClass") && node.hasNonNull("id"))
      result.add(node);
    if (node.isContainer()) node.forEach(child -> collectElements(child, result));
  }

  private static String safe(String message) {
    return message == null ? "unknown compiler error" : message.substring(0, Math.min(3000, message.length()));
  }

  /** Paper-compatible flat conceptual representation and deterministic command compiler. */
  static final class ConceptualModel {
    private final Map<String, JsonNode> objects;
    private ConceptualModel(Map<String, JsonNode> objects) { this.objects = objects; }

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
      try { root = mapper.readTree(json); } catch (Exception ex) { throw new PlatformException(422, "LLM did not return conceptual-model JSON."); }
      if (root == null || !root.isObject()) throw new PlatformException(422, "Conceptual model must be a JSON object.");
      Map<String, JsonNode> result = new LinkedHashMap<>();
      root.properties().forEach(entry -> {
        if (!entry.getValue().isObject() || entry.getValue().path("type").asText("").isBlank())
          throw new PlatformException(422, "Conceptual object '" + entry.getKey() + "' has no type.");
        result.put(entry.getKey(), entry.getValue());
      });
      return new ConceptualModel(result);
    }

    ModelCommandBatch commands(JsonNode current, AgentModelTools tools, ModelLevel level) {
      List<ModelCommandBatch.Create> creates = new ArrayList<>();
      List<ModelCommandBatch.Update> updates = new ArrayList<>();
      List<ModelCommandBatch.Connection> connections = new ArrayList<>();
      Map<String, String> existing = new LinkedHashMap<>();
      collectIds(current, existing);
      Map<String, JsonNode> pendingCreates = new LinkedHashMap<>();
      for (Map.Entry<String, JsonNode> entry : objects.entrySet()) {
        String id = entry.getKey(); JsonNode object = entry.getValue();
        Map<String, JsonNode> attrs = attributes(object.path("attributes"));
        if (existing.containsKey(id)) updates.add(new ModelCommandBatch.Update(id, attrs, ""));
        else pendingCreates.put(id, object);
        for (String kind : List.of("compositions", "references"))
          for (JsonNode association : object.path("associations").path(kind)) {
            String target = association.path("instanceID").asText("");
            String name = association.path("associationName").asText("");
            if (!name.isBlank() && !target.isBlank() && !"compositions".equals(kind))
              connections.add(new ModelCommandBatch.Connection(id, name, target));
          }
      }
      // Resolve containment ownership independently of the provider's JSON property order. This
      // is the deterministic compiler stage of the paper workflow; it does not invent owners or
      // model content, it only topologically orders the LLM's explicit composition edges.
      while (!pendingCreates.isEmpty()) {
        int before = pendingCreates.size();
        for (var iterator = pendingCreates.entrySet().iterator(); iterator.hasNext(); ) {
          Map.Entry<String, JsonNode> entry = iterator.next();
          String owner = "rootId";
          String feature = "";
          for (JsonNode association : entry.getValue().path("associations").path("compositions")) {
            owner = association.path("instanceID").asText("");
            feature = association.path("associationName").asText("");
            break;
          }
          if (feature.isBlank()) {
            var rootContainment = tools.rootContainment(entry.getValue().path("type").asText());
            if (rootContainment.isPresent()) {
              owner = "rootId";
              feature = rootContainment.get().name();
            }
          }
          boolean ownerReady = "rootId".equals(owner) || existing.containsKey(owner);
          if (!ownerReady) {
            for (ModelCommandBatch.Create create : creates) {
              if (create.clientRef().equals(owner)) {
                ownerReady = true;
                break;
              }
            }
          }
          if (ownerReady && !feature.isBlank()) {
            creates.add(new ModelCommandBatch.Create(
                entry.getKey(), entry.getValue().path("type").asText(),
                attributes(entry.getValue().path("attributes")), owner, feature, ""));
            iterator.remove();
          }
        }
        if (pendingCreates.size() == before) {
          String id = pendingCreates.keySet().iterator().next();
          throw new PlatformException(422, "New conceptual object '" + id + "' of type '"
              + pendingCreates.get(id).path("type").asText("")
              + "' has no resolvable composition owner and feature. Remove invented helper "
              + "objects or use an exact Ecore containment from the authoritative guide.");
        }
      }
      if (creates.isEmpty() && updates.isEmpty() && connections.isEmpty()) throw new PlatformException(422, "Conceptual model contains no model changes.");
      return new ModelCommandBatch(creates, updates, connections, List.of(), List.of(), "Conceptual instance model", true);
    }

    private static Map<String, JsonNode> attributes(JsonNode node) {
      Map<String, JsonNode> result = new LinkedHashMap<>();
      for (JsonNode item : node) { String name = item.path("attributeName").asText(""); if (!name.isBlank()) result.put(name, item.get("value") == null ? JsonNodeFactory.instance.nullNode() : item.get("value")); }
      return result;
    }
    private static void collectIds(JsonNode node, Map<String, String> ids) {
      if (node == null || !node.isObject()) return;
      if (node.has("id")) ids.put(node.path("id").asText(), node.path("id").asText());
      node.forEach(child -> collectIds(child, ids));
    }
  }
}
