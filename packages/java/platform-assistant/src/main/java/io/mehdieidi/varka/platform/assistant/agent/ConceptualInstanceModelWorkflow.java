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
import java.util.ArrayDeque;
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

  /** Strict schema for the small, stable-ID plan that precedes bounded object slices. */
  public static String blueprintSchema() {
    return "{\"type\":\"object\",\"required\":[\"types\",\"objects\"],\"additionalProperties\":false,"
               + "\"properties\":{\"types\":{\"type\":\"array\",\"minItems\":1,\"maxItems\":12,"
               + "\"uniqueItems\":true,\"items\":{\"type\":\"string\",\"minLength\":1}},"
               + "\"objects\":{\"type\":\"array\",\"minItems\":1,\"maxItems\":48,\"items\":{"
               + "\"type\":\"object\",\"required\":[\"instanceId\",\"type\",\"purpose\",\"slice\"],"
               + "\"properties\":{\"instanceId\":{\"type\":\"string\",\"minLength\":1},"
               + "\"type\":{\"type\":\"string\",\"minLength\":1},\"purpose\":{\"type\":\"string\"},"
               + "\"ownerInstanceId\":{\"type\":\"string\"},\"containment\":{\"type\":\"string\"},"
               + "\"referenceTargets\":{\"type\":\"array\",\"items\":{\"type\":\"string\"}},"
               + "\"sourceUnitIds\":{\"type\":\"array\",\"items\":{\"type\":\"string\"}},"
               + "\"slice\":{\"type\":\"integer\",\"minimum\":1}}}}}}";
  }

  /** Schema for the semantic review; corrections are bounded conceptual objects, never prose. */
  public static String reviewSchema() {
    return "{\"type\":\"object\",\"required\":[\"acceptable\",\"findings\",\"corrections\"],"
        + "\"additionalProperties\":false,\"properties\":{\"acceptable\":{\"type\":\"boolean\"},"
        + "\"findings\":{\"type\":\"array\",\"items\":{\"type\":\"object\"}},"
        + "\"corrections\":{\"type\":\"object\"}}}";
  }

  public AgentTurnLoop.TurnResult run(
      String sessionId,
      ModelLevel level,
      String request,
      ModelWorkspace workspace,
      AgentModelTools tools,
      boolean destructiveConfirmed) {
    AssistantModelProvider.AssistantReply last = null;
    UsageAudit audit = new UsageAudit();
    boolean sourceBacked =
        request != null && request.contains("SOURCE SPECIFICATION (authoritative input)");
    int maxCalls =
        Math.max(
            4,
            sourceBacked
                ? properties.maxProviderCallsSourceTurn()
                : properties.maxProviderCallsPerTurn());
    ProviderCallBudget.bind(maxCalls);
    try {
      try {
        JsonNode current = workspace.snapshot();
        Blueprint blueprint = planBlueprint(level, request, current, audit, maxCalls);
        String metamodel =
            guides.generateForTypes(
                level,
                contracts.requiredContainmentClosure(level, new ArrayList<>(blueprint.types())));
        LinkedHashMap<String, JsonNode> generated =
            generateSlices(level, request, current, metamodel, blueprint, audit, maxCalls);
        last = review(level, request, metamodel, blueprint, generated, audit, maxCalls);
        ConceptualModel conceptual = new ConceptualModel(generated);
        ModelCommandBatch batch;
        try {
          batch = conceptual.commands(current, contracts, level);
        } catch (RuntimeException compilerFailure) {
          if (ProviderCallBudget.count() >= maxCalls) throw compilerFailure;
          last =
              correctCompilerFailure(
                  level,
                  request,
                  metamodel,
                  blueprint,
                  generated,
                  compilerFailure.getMessage(),
                  audit,
                  maxCalls);
          conceptual = new ConceptualModel(generated);
          batch = conceptual.commands(current, contracts, level);
        }
        tools.commitModelBatch(batch, destructiveConfirmed);
        ModelService.ValidationResult validation = tools.validateModel();
        if (validation == null || !validation.valid()) {
          throw new PlatformException(
              422,
              "Structural validation rejected the conceptual model: "
                  + (validation == null ? "no result" : validation.issues()));
        }
        return new AgentTurnLoop.TurnResult(
            "Applied a staged conceptual instance model, reviewed its requirement coverage, and"
                + " structurally validated the result.",
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
        throw new AgentTurnLoop.TurnExecutionException(
            asPlatformException(failure),
            Math.max(audit.callDetails.size(), ProviderCallBudget.count()),
            audit.promptTokens,
            audit.completionTokens,
            audit.callDetails);
      }
    } finally {
      ProviderCallBudget.clear();
    }
  }

  private Blueprint planBlueprint(
      ModelLevel level, String request, JsonNode current, UsageAudit audit, int maxCalls) {
    String system =
        "Plan a bounded conceptual instance model for Varka's "
            + level.name()
            + " DSML. Return a small stable-ID ledger, not full attributes or association payloads."
            + " Every object needed for a coherent useful model must have one unique temporary"
            + " instanceId, exact EClass, short purpose, containment owner/feature when known,"
            + " major reference target IDs, source-unit allocation, and a positive slice number."
            + " Group semantically coherent objects together. Use only the authoritative type"
            + " index. The existing root is rootId and must not be planned as an object. Do not"
            + " include prose or markdown.";
    String user =
        guides.index(level)
            + "\n\nCURRENT MODEL TYPES:\n"
            + currentTypes(current)
            + "\n\nREQUEST AND SOURCE SPECIFICATION:\n"
            + (request == null ? "" : request)
            + "\n\nReturn {types:[exact EClass names],objects:[{instanceId,type,purpose,"
            + "ownerInstanceId,containment,referenceTargets,sourceUnitIds,slice}]}. Do not emit"
            + " attributes or complete conceptual objects.";
    AssistantModelProvider.AssistantReply reply = audit.call(system, user, "conceptual_blueprint");
    Blueprint blueprint = Blueprint.parse(mapper, reply.content(), contracts, level);
    int capacity = Math.max(1, maxCalls - 2) * 8;
    if (blueprint.objects().size() > capacity) {
      throw new PlatformException(
          422,
          "Conceptual blueprint planned "
              + blueprint.objects().size()
              + " objects, exceeding this turn's bounded staged capacity of "
              + capacity
              + ". Use the durable inspect/contract source workflow for a larger model.");
    }
    Set<String> suppliedSourceUnits = sourceUnitIds(request);
    Set<String> allocated = new LinkedHashSet<>();
    blueprint.objects().forEach(object -> allocated.addAll(object.sourceUnitIds()));
    if (!allocated.containsAll(suppliedSourceUnits)) {
      Set<String> missing = new LinkedHashSet<>(suppliedSourceUnits);
      missing.removeAll(allocated);
      throw new PlatformException(
          422, "Conceptual blueprint did not allocate supplied source units: " + missing + ".");
    }
    return blueprint;
  }

  private LinkedHashMap<String, JsonNode> generateSlices(
      ModelLevel level,
      String request,
      JsonNode current,
      String metamodel,
      Blueprint blueprint,
      UsageAudit audit,
      int maxCalls) {
    ArrayDeque<List<BlueprintObject>> pending = new ArrayDeque<>();
    Map<Integer, List<BlueprintObject>> planned = new LinkedHashMap<>();
    blueprint.objects().stream()
        .sorted(java.util.Comparator.comparingInt(BlueprintObject::slice))
        .forEach(
            object ->
                planned.computeIfAbsent(object.slice(), ignored -> new ArrayList<>()).add(object));
    List<BlueprintObject> packed = new ArrayList<>();
    for (List<BlueprintObject> group : planned.values()) {
      if (group.size() <= 8 && packed.size() + group.size() <= 8) {
        packed.addAll(group);
        continue;
      }
      if (!packed.isEmpty()) {
        pending.addLast(List.copyOf(packed));
        packed.clear();
      }
      for (int start = 0; start < group.size(); start += 8) {
        List<BlueprintObject> chunk =
            List.copyOf(group.subList(start, Math.min(start + 8, group.size())));
        if (chunk.size() == 8) pending.addLast(chunk);
        else packed.addAll(chunk);
      }
    }
    if (!packed.isEmpty()) pending.addLast(List.copyOf(packed));
    LinkedHashMap<String, JsonNode> generated = new LinkedHashMap<>();
    while (!pending.isEmpty()) {
      if (ProviderCallBudget.count() >= maxCalls - 1) {
        throw new PlatformException(
            429, "Conceptual slice generation exhausted its provider-call budget before review.");
      }
      List<BlueprintObject> slice = pending.removeFirst();
      String ids = slice.stream().map(BlueprintObject::instanceId).toList().toString();
      String system =
          system(level)
              .replace(
                  "complete conceptual instance model", "bounded conceptual instance-model slice")
              .replace(
                  "All referenced instance IDs must be keys in this complete response or exact"
                      + " persisted IDs.",
                  "Referenced new instance IDs may be declared in the supplied blueprint and"
                      + " generated in another slice.");
      String user =
          "AUTHORITATIVE METAMODEL CONTRACTS:\n"
              + metamodel
              + "\n\nSTABLE INSTANCE BLUEPRINT:\n"
              + blueprint.json()
              + "\n\nCURRENT PERSISTED MODEL:\n"
              + compactSnapshot(current)
              + "\n\nREQUEST AND SOURCE SPECIFICATION:\n"
              + (request == null ? "" : request)
              + "\n\nGENERATE ONLY THESE INSTANCE IDS: "
              + ids
              + ". Return exactly those keys, with complete attributes, compositions, references,"
              + " and evidence. It is valid to reference any ID declared in the blueprint even if"
              + " that target belongs to a later slice. Maximum objects in this response: "
              + slice.size()
              + ". Return one JSON object and no prose.";
      AssistantModelProvider.AssistantReply reply;
      try {
        reply = audit.call(system, user, "conceptual_instance_slice");
      } catch (RuntimeException failure) {
        if (truncated(failure) && slice.size() > 1) {
          int middle = slice.size() / 2;
          pending.addFirst(List.copyOf(slice.subList(middle, slice.size())));
          pending.addFirst(List.copyOf(slice.subList(0, middle)));
          log.warn(
              "Conceptual slice truncated; split {} objects into {} and {}",
              slice.size(),
              middle,
              slice.size() - middle);
          continue;
        }
        throw failure;
      }
      try {
        mergeSlice(slice, reply.content(), generated);
      } catch (RuntimeException schemaFailure) {
        if (ProviderCallBudget.count() >= maxCalls - 1) throw schemaFailure;
        String correctedUser =
            user
                + "\n\nThe prior slice was rejected by the deterministic protocol validator: "
                + safe(schemaFailure.getMessage())
                + " Return the same exact instance IDs with corrected COMPLETE objects. Every"
                + " object must contain attributes as an array and associations as an object with"
                + " separate compositions and references arrays. Do not omit valid content and do"
                + " not return an action envelope or prose.";
        AssistantModelProvider.AssistantReply corrected =
            audit.call(system, correctedUser, "conceptual_instance_slice");
        mergeSlice(slice, corrected.content(), generated);
      }
    }
    if (generated.size() != blueprint.objects().size()) {
      throw new PlatformException(
          422, "Conceptual slices did not generate every blueprint object.");
    }
    return generated;
  }

  private void mergeSlice(
      List<BlueprintObject> slice, String content, LinkedHashMap<String, JsonNode> generated) {
    ConceptualModel parsed = ConceptualModel.parse(mapper, content);
    Set<String> expected =
        new LinkedHashSet<>(slice.stream().map(BlueprintObject::instanceId).toList());
    if (!parsed.objects.keySet().equals(expected)) {
      throw new PlatformException(
          422,
          "Conceptual slice keys must exactly match "
              + expected
              + " but were "
              + parsed.objects.keySet()
              + ".");
    }
    for (BlueprintObject object : slice) {
      JsonNode value = parsed.objects.get(object.instanceId());
      if (!object.type().equals(value.path("type").asText())) {
        throw new PlatformException(
            422,
            "Conceptual slice changed blueprint type for '"
                + object.instanceId()
                + "' from "
                + object.type()
                + " to "
                + value.path("type").asText()
                + ".");
      }
      if (generated.containsKey(object.instanceId())) {
        throw new PlatformException(
            422, "Duplicate conceptual instance ID across slices: " + object.instanceId());
      }
    }
    for (BlueprintObject object : slice) {
      generated.put(object.instanceId(), parsed.objects.get(object.instanceId()));
    }
  }

  private AssistantModelProvider.AssistantReply review(
      ModelLevel level,
      String request,
      String metamodel,
      Blueprint blueprint,
      LinkedHashMap<String, JsonNode> generated,
      UsageAudit audit,
      int maxCalls) {
    if (ProviderCallBudget.count() >= maxCalls) {
      throw new PlatformException(
          429, "No provider-call budget remains for conceptual quality review.");
    }
    String system =
        "Review a generated "
            + level.name()
            + " conceptual model for modeling usefulness and grounding. Check every user/source"
            + " requirement, duplicated or conflated concepts, meaningful names, appropriate"
            + " abstraction, relationship quality, unsupported inventions, and whether important"
            + " concepts were incorrectly reduced to free text. Return strict JSON. If acceptable,"
            + " corrections must be {}. If not acceptable, corrections must contain complete"
            + " replacement conceptual object for only one affected existing instance ID"
            + " (maximum 1). Return at most one concise finding. Correct only the single most"
            + " important release-blocking"
            + " problems; never add/delete IDs and never return prose.";
    String user =
        "REQUEST AND SOURCE:\n"
            + (request == null ? "" : request)
            + "\n\nBLUEPRINT:\n"
            + blueprint.json()
            + "\n\nMETAMODEL:\n"
            + metamodel
            + "\n\nGENERATED CONCEPTUAL MODEL:\n"
            + mapper.valueToTree(generated)
            + "\n\nReturn {acceptable,findings,corrections}.";
    AssistantModelProvider.AssistantReply reply;
    try {
      reply = audit.call(system, user, "conceptual_review");
    } catch (RuntimeException failure) {
      if (!truncated(failure) || ProviderCallBudget.count() >= maxCalls) throw failure;
      String reducedUser =
          user
              + "\n\nThe prior review response was truncated. Return a much smaller terminal JSON"
              + " object: at most one short finding and at most one corrected object. If no"
              + " release-blocking problem exists, return acceptable:true with empty corrections.";
      reply = audit.call(system, reducedUser, "conceptual_review");
    }
    applyReview(reply.content(), blueprint, generated);
    return reply;
  }

  private AssistantModelProvider.AssistantReply correctCompilerFailure(
      ModelLevel level,
      String request,
      String metamodel,
      Blueprint blueprint,
      LinkedHashMap<String, JsonNode> generated,
      String diagnostic,
      UsageAudit audit,
      int maxCalls) {
    List<String> affectedIds =
        blueprint.objects().stream()
            .map(BlueprintObject::instanceId)
            .filter(
                id ->
                    diagnostic != null
                        && (diagnostic.contains("'" + id + "'") || diagnostic.contains(id + ".")))
            .limit(2)
            .toList();
    if (affectedIds.isEmpty()) {
      affectedIds = blueprint.objects().stream().map(BlueprintObject::instanceId).limit(1).toList();
    }
    var rejected = JsonNodeFactory.instance.objectNode();
    affectedIds.forEach(id -> rejected.set(id, generated.get(id).deepCopy()));
    String system =
        "Correct a bounded conceptual model rejected by Varka's deterministic Ecore compiler."
            + " Return {acceptable:false,findings:[...],corrections:{...}}. Corrections must"
            + " contain complete replacement objects for ONLY these affected IDs: "
            + affectedIds
            + ". Preserve all valid semantics. Do not echo other objects, add/delete IDs, or"
            + " return prose.";
    String user =
        "REQUEST:\n"
            + request
            + "\n\nMETAMODEL:\n"
            + metamodel
            + "\n\nBLUEPRINT:\n"
            + blueprint.json()
            + "\n\nONLY AFFECTED REJECTED OBJECTS:\n"
            + rejected
            + "\n\nCOMPILER DIAGNOSTIC:\n"
            + safe(diagnostic);
    AssistantModelProvider.AssistantReply reply;
    try {
      reply = audit.call(system, user, "conceptual_review");
      applyReview(reply.content(), blueprint, generated);
    } catch (RuntimeException firstFailure) {
      if (ProviderCallBudget.count() >= maxCalls) throw firstFailure;
      String retryUser =
          user
              + "\n\nThe prior correction was rejected or truncated: "
              + safe(firstFailure.getMessage())
              + " Return a tiny terminal JSON object correcting ONLY "
              + affectedIds
              + ". findings must contain at most one short item.";
      reply = audit.call(system, retryUser, "conceptual_review");
      applyReview(reply.content(), blueprint, generated);
    }
    return reply;
  }

  private void applyReview(
      String content, Blueprint blueprint, LinkedHashMap<String, JsonNode> generated) {
    JsonNode review = strictObject(content, "conceptual review");
    if (!review.has("acceptable")
        || !review.path("findings").isArray()
        || !review.path("corrections").isObject()) {
      throw new PlatformException(
          422, "Conceptual review must contain acceptable, findings, and corrections.");
    }
    JsonNode corrections = review.path("corrections");
    if (!review.path("acceptable").asBoolean(false) && corrections.isEmpty()) {
      throw new PlatformException(
          422, "Conceptual reviewer rejected the model without structured corrections.");
    }
    if (corrections.size() > 1) {
      throw new PlatformException(422, "Conceptual review may correct at most 1 object.");
    }
    Map<String, BlueprintObject> planned = new LinkedHashMap<>();
    blueprint.objects().forEach(object -> planned.put(object.instanceId(), object));
    if (!corrections.isEmpty()) {
      ConceptualModel parsed = ConceptualModel.parse(mapper, corrections.toString());
      parsed.objects.forEach(
          (id, value) -> {
            BlueprintObject object = planned.get(id);
            if (object == null)
              throw new PlatformException(
                  422, "Review correction used undeclared ID '" + id + "'.");
            if (!object.type().equals(value.path("type").asText())) {
              throw new PlatformException(
                  422, "Review correction changed EClass for '" + id + "'.");
            }
            generated.put(id, value);
          });
    }
  }

  private JsonNode strictObject(String content, String label) {
    String json = content == null ? "" : content.trim();
    if (json.startsWith("```")) {
      json = json.replaceFirst("^```(?:json)?\\s*", "").replaceFirst("\\s*```$", "").trim();
    }
    try {
      JsonNode parsed = mapper.readTree(json);
      if (parsed == null || !parsed.isObject()) throw new IllegalArgumentException();
      return parsed;
    } catch (Exception failure) {
      throw new PlatformException(
          422, "LLM did not return a valid terminal JSON object for " + label + ".");
    }
  }

  private Set<String> sourceUnitIds(String request) {
    if (request == null || request.isBlank()) return Set.of();
    java.util.regex.Matcher matcher =
        java.util.regex.Pattern.compile("<source-unit\\s+id=\\\"([^\\\"]+)\\\"").matcher(request);
    LinkedHashSet<String> result = new LinkedHashSet<>();
    while (matcher.find()) result.add(matcher.group(1));
    return result;
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
      AssistantModelProvider.AssistantReply reply;
      try {
        reply =
            provider.completeStructured(
                new AssistantModelProvider.AssistantPrompt(
                    system, user, List.of(), List.of(), requiredTool));
      } catch (RuntimeException failure) {
        long latency = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
        AssistantModelProvider.AssistantProviderMetadata metadata = provider.metadata();
        callDetails.add(
            new AssistantTurnStore.ProviderCall(
                metadata.provider(),
                properties.model(),
                latency,
                -1,
                -1,
                false,
                system,
                user,
                failureReason(failure),
                safe(failure.getMessage())));
        throw failure;
      }
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

    private String failureReason(RuntimeException failure) {
      String message = safe(failure.getMessage()).toLowerCase(java.util.Locale.ROOT);
      if (message.contains("finish_reason=length") || message.contains("truncated"))
        return "TRUNCATED";
      if (message.contains("timeout") || message.contains("timed out")) return "TIMEOUT";
      if (message.contains("empty") || message.contains("no structured json content"))
        return "EMPTY_RESPONSE";
      if (message.contains("json")) return "MALFORMED_JSON";
      return "PROVIDER_FAILURE";
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

  private record Blueprint(Set<String> types, List<BlueprintObject> objects, JsonNode json) {
    private static Blueprint parse(
        ObjectMapper mapper, String content, TypeContractService contracts, ModelLevel level) {
      String jsonText = content == null ? "" : content.trim();
      if (jsonText.startsWith("```")) {
        jsonText =
            jsonText.replaceFirst("^```(?:json)?\\s*", "").replaceFirst("\\s*```$", "").trim();
      }
      JsonNode root;
      try {
        root = mapper.readTree(jsonText);
      } catch (Exception failure) {
        throw new PlatformException(422, "LLM did not return parseable conceptual blueprint JSON.");
      }
      if (root == null
          || !root.isObject()
          || !root.path("types").isArray()
          || !root.path("objects").isArray()
          || root.path("objects").isEmpty()) {
        throw new PlatformException(
            422, "Conceptual blueprint requires non-empty types and objects arrays.");
      }
      LinkedHashSet<String> types = new LinkedHashSet<>();
      for (JsonNode type : root.path("types")) {
        String name = type.asText("").trim();
        if (!name.isBlank()) types.add(contracts.require(level, name).eClass());
      }
      if (types.isEmpty() || types.size() > 12) {
        throw new PlatformException(
            422, "Conceptual blueprint must select between 1 and 12 exact EClasses.");
      }
      if (root.path("objects").size() > 48) {
        throw new PlatformException(422, "Conceptual blueprint may declare at most 48 objects.");
      }
      List<BlueprintObject> objects = new ArrayList<>();
      LinkedHashSet<String> ids = new LinkedHashSet<>();
      for (JsonNode item : root.path("objects")) {
        String id = item.path("instanceId").asText("").trim();
        String type = item.path("type").asText("").trim();
        int slice = item.path("slice").asInt(0);
        if (id.isBlank() || "rootId".equals(id) || !ids.add(id)) {
          throw new PlatformException(
              422, "Every conceptual blueprint object needs a unique non-root instanceId.");
        }
        String exactType = contracts.require(level, type).eClass();
        types.add(exactType);
        if (types.size() > 12) {
          throw new PlatformException(
              422, "Conceptual blueprint uses more than 12 exact EClasses.");
        }
        if (slice < 1)
          throw new PlatformException(422, "Conceptual blueprint slice numbers must be positive.");
        objects.add(
            new BlueprintObject(
                id,
                exactType,
                item.path("purpose").asText(""),
                item.path("ownerInstanceId").asText(""),
                item.path("containment").asText(""),
                strings(item.path("referenceTargets")),
                strings(item.path("sourceUnitIds")),
                slice));
      }
      for (BlueprintObject object : objects) {
        if (!object.ownerInstanceId().isBlank()
            && !"rootId".equals(object.ownerInstanceId())
            && !ids.contains(object.ownerInstanceId())) {
          throw new PlatformException(
              422,
              "Blueprint object '"
                  + object.instanceId()
                  + "' has unknown ownerInstanceId '"
                  + object.ownerInstanceId()
                  + "'.");
        }
        for (String target : object.referenceTargets()) {
          if (!ids.contains(target)) {
            throw new PlatformException(
                422,
                "Blueprint object '"
                    + object.instanceId()
                    + "' has unknown reference target '"
                    + target
                    + "'.");
          }
        }
      }
      return new Blueprint(Set.copyOf(types), List.copyOf(objects), root.deepCopy());
    }

    private static List<String> strings(JsonNode node) {
      if (!node.isArray()) return List.of();
      List<String> result = new ArrayList<>();
      for (JsonNode value : node) {
        String text = value.asText("").trim();
        if (!text.isBlank()) result.add(text);
      }
      return List.copyOf(result);
    }
  }

  private record BlueprintObject(
      String instanceId,
      String type,
      String purpose,
      String ownerInstanceId,
      String containment,
      List<String> referenceTargets,
      List<String> sourceUnitIds,
      int slice) {}

  private record SnapshotElement(JsonNode node, String ownerId, String ownerFeature) {}
}
