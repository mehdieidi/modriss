package io.mehdieidi.varka.platform.assistant.agent;

import io.mehdieidi.varka.platform.assistant.application.DurableTurnExecutionContext;
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
  private final AssistantTurnStore turns;
  private final ObjectMapper mapper = new ObjectMapper();

  public ConceptualInstanceModelWorkflow(
      AssistantModelProvider provider,
      MetamodelGuideGenerator guides,
      TypeContractService contracts,
      AiProperties properties) {
    this(provider, guides, contracts, properties, null);
  }

  public ConceptualInstanceModelWorkflow(
      AssistantModelProvider provider,
      MetamodelGuideGenerator guides,
      TypeContractService contracts,
      AiProperties properties,
      AssistantTurnStore turns) {
    this.provider = java.util.Objects.requireNonNull(provider, "provider");
    this.guides = java.util.Objects.requireNonNull(guides, "guides");
    this.contracts = java.util.Objects.requireNonNull(contracts, "contracts");
    this.properties = java.util.Objects.requireNonNull(properties, "properties");
    this.turns = turns;
  }

  /** OpenAI-compatible schema for the paper IR plus optional Varka source evidence. */
  public static String jsonSchema() {
    return "{\"type\":\"object\",\"minProperties\":1,\"maxProperties\":2,\"additionalProperties\":{\"type\":\"object\",\"additionalProperties\":false,"
               + "\"required\":[\"type\",\"attributes\",\"associations\"],"
               + "\"properties\":{\"type\":{\"type\":\"string\",\"minLength\":1},"
               + "\"attributes\":{\"type\":\"array\",\"maxItems\":24,\"items\":{\"type\":\"object\",\"additionalProperties\":false,"
               + "\"required\":[\"dataType\",\"attributeName\",\"value\"],\"properties\":{"
               + "\"dataType\":{\"type\":\"string\"},\"attributeName\":{\"type\":\"string\",\"minLength\":1},"
               + "\"value\":{}}}},\"associations\":{\"type\":\"object\",\"additionalProperties\":false,\"required\":[\"compositions\",\"references\"],"
               + "\"properties\":{\"compositions\":{\"type\":\"array\",\"maxItems\":16,\"items\":{\"$ref\":\"#/$defs/association\"}},"
               + "\"references\":{\"type\":\"array\",\"maxItems\":24,\"items\":{\"$ref\":\"#/$defs/association\"}}}},"
               + "\"evidence\":{\"type\":\"array\",\"maxItems\":16,\"items\":{\"type\":\"object\",\"additionalProperties\":false,"
               + "\"required\":[\"sourceUnitId\",\"kind\"],\"properties\":{"
               + "\"sourceUnitId\":{\"type\":\"string\"},\"requirementId\":{\"type\":\"string\"},"
               + "\"kind\":{\"type\":\"string\",\"enum\":[\"SOURCE_GROUNDED\",\"INFERRED\"]},"
               + "\"assumption\":{\"type\":\"string\"}}}}}},"
               + "\"$defs\":{\"association\":{\"type\":\"object\",\"additionalProperties\":false,\"required\":[\"associationName\",\"associatedClassName\",\"instanceID\"],"
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
               + "\"objects\":{\"type\":\"array\",\"minItems\":1,\"maxItems\":8,\"items\":{"
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
    return "{\"type\":\"object\",\"required\":[\"acceptable\",\"findings\"],"
        + "\"additionalProperties\":false,\"properties\":{\"acceptable\":{\"type\":\"boolean\"},"
        + "\"findings\":{\"type\":\"array\",\"maxItems\":1,\"items\":{\"type\":\"object\","
        + "\"additionalProperties\":false,\"properties\":{\"objectIds\":{\"type\":\"array\","
        + "\"items\":{\"type\":\"string\"}},\"sourceUnitIds\":{\"type\":\"array\","
        + "\"items\":{\"type\":\"string\"}},\"problem\":{\"type\":\"string\"},"
        + "\"recommendedCorrection\":{\"type\":\"string\"}}}}}}";
  }

  public static String correctionSchema() {
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
    String durableTurnId = DurableTurnExecutionContext.turnId();
    boolean sourceBacked =
        request != null && request.contains("SOURCE SPECIFICATION (authoritative input)");
    int maxCalls =
        Math.max(
            4,
            sourceBacked
                ? properties.maxProviderCallsSourceTurn() - 2
                : properties.maxProviderCallsPerTurn() - 2);
    UsageAudit audit = new UsageAudit(durableTurnId, maxCalls);
    ProviderCallBudget.bind(maxCalls);
    try {
      try {
        JsonNode current = workspace.snapshot();
        Blueprint blueprint = restoredBlueprint(durableTurnId, level);
        if (blueprint == null) {
          blueprint = planBlueprint(level, request, current, audit, maxCalls);
          persistBlueprint(durableTurnId, blueprint);
        }
        String metamodel =
            guides.generateForTypes(
                level,
                contracts.requiredContainmentClosure(level, new ArrayList<>(blueprint.types())));
        LinkedHashMap<String, JsonNode> generated = restoredObjects(durableTurnId, blueprint);
        generateSlices(
            durableTurnId,
            level,
            request,
            current,
            metamodel,
            blueprint,
            generated,
            audit,
            maxCalls);
        last = review(level, request, metamodel, blueprint, generated, audit, maxCalls);
        persistObjects(durableTurnId, blueprint, generated);
        ConceptualModel conceptual = new ConceptualModel(generated);
        ModelCommandBatch batch;
        try {
          batch = conceptual.commands(current, contracts, level);
        } catch (RuntimeException compilerFailure) {
          if (audit.totalCalls() >= maxCalls) throw compilerFailure;
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
          persistObjects(durableTurnId, blueprint, generated);
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
            audit.totalCalls(),
            audit.promptTokens,
            audit.completionTokens,
            List.copyOf(audit.callDetails),
            null,
            null,
            null);
      } catch (RuntimeException failure) {
        throw new AgentTurnLoop.TurnExecutionException(
            asPlatformException(failure),
            audit.totalCalls(),
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
    Set<String> suppliedSourceUnits = sourceUnitIds(request);
    String system =
        "Plan a bounded conceptual instance model for Varka's "
            + level.name()
            + " DSML. Return a small stable-ID ledger, not full attributes or association payloads."
            + " Every object needed for a coherent useful model must have one unique temporary"
            + " instanceId, exact EClass, short purpose, containment owner/feature when known,"
            + " major reference target IDs, source-unit allocation, and a positive slice number."
            + " sourceUnitIds may contain ONLY IDs from the explicit available-source list; when"
            + " that list is empty every sourceUnitIds array must be empty."
            + " Every object must use an exact legal containment placement from the supplied"
            + " index. If a desired type is nested, also plan its required owner object."
            + " Plan at most 8 semantically important objects total."
            + " Group semantically coherent objects together. Use only the authoritative type"
            + " index. The existing root is rootId and must not be planned as an object. Do not"
            + " include prose or markdown.";
    String user =
        guides.index(level)
            + "\n\nCURRENT MODEL TYPES:\n"
            + currentTypes(current)
            + "\n\nAUTHORITATIVE CONTAINMENT PLACEMENTS (child <- owner.feature):\n"
            + containmentIndex(level)
            + "\n\nREQUEST AND SOURCE SPECIFICATION:\n"
            + (request == null ? "" : request)
            + "\n\nAVAILABLE SOURCE UNIT IDS (closed allowlist): "
            + suppliedSourceUnits
            + "\n\nReturn {types:[exact EClass names],objects:[{instanceId,type,purpose,"
            + "ownerInstanceId,containment,referenceTargets,sourceUnitIds,slice}]}. Do not emit"
            + " attributes or complete conceptual objects.";
    Blueprint blueprint = null;
    String correction = "";
    RuntimeException lastFailure = null;
    for (int attempt = 0; attempt < 2; attempt++) {
      AssistantModelProvider.AssistantReply reply =
          audit.call(system, user + correction, "conceptual_blueprint");
      try {
        blueprint = Blueprint.parse(mapper, reply.content(), contracts, level);
        blueprint = normalizeBlueprintPlacements(level, blueprint);
        Set<String> allocated = new LinkedHashSet<>();
        for (BlueprintObject object : blueprint.objects()) {
          allocated.addAll(object.sourceUnitIds());
        }
        List<String> diagnostics = blueprintDiagnostics(level, blueprint);
        if (!suppliedSourceUnits.containsAll(allocated)) {
          Set<String> unknown = new LinkedHashSet<>(allocated);
          unknown.removeAll(suppliedSourceUnits);
          diagnostics.add("invented source-unit IDs " + unknown);
        }
        if (!allocated.containsAll(suppliedSourceUnits)) {
          Set<String> missing = new LinkedHashSet<>(suppliedSourceUnits);
          missing.removeAll(allocated);
          diagnostics.add("did not allocate supplied source units " + missing);
        }
        if (!diagnostics.isEmpty()) {
          throw new PlatformException(
              422, "Conceptual blueprint diagnostics: " + String.join("; ", diagnostics) + ".");
        }
        int capacity = blueprintCapacity(maxCalls);
        if (blueprint.objects().size() > capacity) {
          throw new PlatformException(
              422,
              "Blueprint planned "
                  + blueprint.objects().size()
                  + " objects; the DeepSeek/Arvan bounded capacity is "
                  + capacity
                  + ". Keep the most important coherent objects and relationships.");
        }
        break;
      } catch (RuntimeException failure) {
        lastFailure = failure;
        correction =
            "\n\nThe prior blueprint was rejected: "
                + safe(failure.getMessage())
                + " Return a corrected small blueprint. Use only the closed source-unit allowlist."
                + " Do not return attributes, full objects, prose, or markdown.";
      }
    }
    if (blueprint == null) throw lastFailure;
    int capacity = blueprintCapacity(maxCalls);
    if (blueprint.objects().size() > capacity) {
      throw new PlatformException(
          422,
          "Conceptual blueprint planned "
              + blueprint.objects().size()
              + " objects, exceeding this turn's bounded staged capacity of "
              + capacity
              + ". Use the durable inspect/contract source workflow for a larger model.");
    }
    return blueprint;
  }

  private int blueprintCapacity(int maxCalls) {
    // Reserve blueprint + review calls and two bounded recovery/correction calls. DeepSeek uses
    // one rich object per slice because repeated live two-object responses reached finish_reason
    // length. Other providers may safely pack two while honoring the same recovery reserve.
    return Math.min(8, Math.max(1, maxCalls - 4) * defaultSliceSize());
  }

  private int defaultSliceSize() {
    String model =
        properties.model() == null ? "" : properties.model().toLowerCase(java.util.Locale.ROOT);
    return model.startsWith("deepseek") || model.contains("/deepseek") ? 1 : 2;
  }

  private String containmentIndex(ModelLevel level) {
    return contracts.all(level).stream()
        .filter(TypeContract::creatable)
        .map(
            type ->
                type.eClass()
                    + " <- "
                    + contracts.containmentPlacements(level, type.eClass()).stream()
                        .map(
                            placement -> {
                              int dot = placement.indexOf('.');
                              return dot < 0
                                  ? placement
                                  : "ownerType="
                                      + placement.substring(0, dot)
                                      + ", containment="
                                      + placement.substring(dot + 1);
                            })
                        .toList())
        .collect(java.util.stream.Collectors.joining("\n"));
  }

  private Blueprint normalizeBlueprintPlacements(ModelLevel level, Blueprint blueprint) {
    Map<String, BlueprintObject> byId = new LinkedHashMap<>();
    blueprint.objects().forEach(object -> byId.put(object.instanceId(), object));
    List<BlueprintObject> normalized = new ArrayList<>();
    JsonNode json = blueprint.json().deepCopy();
    for (int index = 0; index < blueprint.objects().size(); index++) {
      BlueprintObject object = blueprint.objects().get(index);
      String ownerType =
          "rootId".equals(object.ownerInstanceId())
              ? contracts.rootType(level)
              : byId.containsKey(object.ownerInstanceId())
                  ? byId.get(object.ownerInstanceId()).type()
                  : "";
      String containment = object.containment();
      String prefix = ownerType + ".";
      if (!ownerType.isBlank() && containment.startsWith(prefix)) {
        containment = containment.substring(prefix.length());
        ((tools.jackson.databind.node.ObjectNode) json.path("objects").get(index))
            .put("containment", containment);
      }
      normalized.add(
          new BlueprintObject(
              object.instanceId(),
              object.type(),
              object.purpose(),
              object.ownerInstanceId(),
              containment,
              object.referenceTargets(),
              object.sourceUnitIds(),
              object.slice()));
    }
    return new Blueprint(blueprint.types(), List.copyOf(normalized), json);
  }

  private List<String> blueprintDiagnostics(ModelLevel level, Blueprint blueprint) {
    List<String> diagnostics = new ArrayList<>();
    Map<String, BlueprintObject> objects = new LinkedHashMap<>();
    blueprint.objects().forEach(object -> objects.put(object.instanceId(), object));
    for (BlueprintObject object : blueprint.objects()) {
      TypeContract objectType = contracts.require(level, object.type());
      String ownerType;
      if ("rootId".equals(object.ownerInstanceId())) {
        ownerType = contracts.rootType(level);
      } else {
        BlueprintObject owner = objects.get(object.ownerInstanceId());
        ownerType = owner == null ? "" : owner.type();
      }
      boolean legal =
          !ownerType.isBlank()
              && contracts.require(level, ownerType).references().stream()
                  .filter(ReferenceContract::containment)
                  .filter(reference -> !reference.readonly())
                  .filter(reference -> reference.name().equals(object.containment()))
                  .anyMatch(
                      reference ->
                          contracts.assignable(level, object.type(), reference.targetType()));
      if (!legal) {
        diagnostics.add(
            object.instanceId()
                + " ("
                + object.type()
                + ") has illegal placement "
                + object.ownerInstanceId()
                + "."
                + object.containment()
                + "; legal placements are "
                + contracts.containmentPlacements(level, object.type()));
      }
      for (ReferenceContract required :
          objectType.references().stream()
              .filter(ReferenceContract::required)
              .filter(reference -> !reference.readonly())
              .toList()) {
        boolean planned;
        if (required.containment()) {
          planned =
              blueprint.objects().stream()
                  .filter(candidate -> candidate.ownerInstanceId().equals(object.instanceId()))
                  .filter(candidate -> candidate.containment().equals(required.name()))
                  .anyMatch(
                      candidate ->
                          contracts.assignable(level, candidate.type(), required.targetType()));
        } else {
          planned =
              object.referenceTargets().stream()
                  .map(objects::get)
                  .filter(java.util.Objects::nonNull)
                  .anyMatch(
                      candidate ->
                          contracts.assignable(level, candidate.type(), required.targetType()));
        }
        if (!planned) {
          diagnostics.add(
              object.instanceId()
                  + " ("
                  + object.type()
                  + ") does not plan required Ecore "
                  + (required.containment() ? "containment " : "reference ")
                  + required.name()
                  + "->"
                  + required.targetType()
                  + "; add a compatible stable-ID object and dependency, or choose a type without"
                  + " that required reference");
        }
      }
    }
    return diagnostics;
  }

  private void generateSlices(
      String durableTurnId,
      ModelLevel level,
      String request,
      JsonNode current,
      String metamodel,
      Blueprint blueprint,
      LinkedHashMap<String, JsonNode> generated,
      UsageAudit audit,
      int maxCalls) {
    ArrayDeque<List<BlueprintObject>> pending = new ArrayDeque<>();
    Map<Integer, List<BlueprintObject>> planned = new LinkedHashMap<>();
    int persistedSliceSize = persistedSliceSize(durableTurnId);
    blueprint.objects().stream()
        .filter(object -> !generated.containsKey(object.instanceId()))
        .sorted(java.util.Comparator.comparingInt(BlueprintObject::slice))
        .forEach(
            object ->
                planned.computeIfAbsent(object.slice(), ignored -> new ArrayList<>()).add(object));
    List<BlueprintObject> packed = new ArrayList<>();
    for (List<BlueprintObject> group : planned.values()) {
      if (group.size() <= persistedSliceSize
          && packed.size() + group.size() <= persistedSliceSize) {
        packed.addAll(group);
        continue;
      }
      if (!packed.isEmpty()) {
        pending.addLast(List.copyOf(packed));
        packed.clear();
      }
      for (int start = 0; start < group.size(); start += persistedSliceSize) {
        List<BlueprintObject> chunk =
            List.copyOf(group.subList(start, Math.min(start + persistedSliceSize, group.size())));
        if (chunk.size() == persistedSliceSize) pending.addLast(chunk);
        else packed.addAll(chunk);
      }
    }
    if (!packed.isEmpty()) pending.addLast(List.copyOf(packed));
    while (!pending.isEmpty()) {
      if (audit.totalCalls() >= maxCalls - 1) {
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
              + ". Association arrays contain only real links: associationName,"
              + " associatedClassName, and instanceID must each be a non-empty JSON string. Use"
              + " [] when no link exists; never emit a placeholder association whose instanceID"
              + " is an array, object, null, or blank. Return one JSON object and no prose.";
      AssistantModelProvider.AssistantReply reply;
      try {
        reply = audit.call(system, user, "conceptual_instance_slice");
      } catch (RuntimeException failure) {
        if (truncated(failure) && slice.size() > 1) {
          int middle = slice.size() / 2;
          persistSliceSize(durableTurnId, Math.max(1, middle), failure.getMessage());
          pending.addFirst(List.copyOf(slice.subList(middle, slice.size())));
          pending.addFirst(List.copyOf(slice.subList(0, middle)));
          log.warn(
              "Conceptual slice truncated; split {} objects into {} and {}",
              slice.size(),
              middle,
              slice.size() - middle);
          continue;
        }
        if (!truncated(failure) || slice.size() != 1 || audit.totalCalls() >= maxCalls - 1) {
          throw failure;
        }
        String reducedUser =
            user
                + "\n\nThe prior one-object response was length-limited. Return the same single"
                + " instance ID as a compact COMPLETE object. Include only meaningful writable"
                + " attributes (normally no more than 8) and real relationships. Do not enumerate"
                + " absent optional attributes or placeholder associations. Preserve the requested"
                + " domain semantics, exact EClass, containment, evidence, and references. Return"
                + " one terminal JSON object and no prose.";
        reply = audit.call(system, reducedUser, "conceptual_instance_slice");
      }
      try {
        mergeSlice(slice, reply.content(), generated);
        persistObjects(durableTurnId, blueprint, generated);
      } catch (RuntimeException schemaFailure) {
        if (audit.totalCalls() >= maxCalls - 1) throw schemaFailure;
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
        persistObjects(durableTurnId, blueprint, generated);
      }
    }
    if (generated.size() != blueprint.objects().size()) {
      throw new PlatformException(
          422, "Conceptual slices did not generate every blueprint object.");
    }
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
      if (!value.path("type").isTextual() || !object.type().equals(value.path("type").asText())) {
        throw new PlatformException(
            422,
            "Conceptual slice changed blueprint type for '"
                + object.instanceId()
                + "' from "
                + object.type()
                + " to "
                + value.path("type")
                + ".");
      }
      validateCompleteObject(object.instanceId(), value);
      if (generated.containsKey(object.instanceId())) {
        throw new PlatformException(
            422, "Duplicate conceptual instance ID across slices: " + object.instanceId());
      }
    }
    for (BlueprintObject object : slice) {
      generated.put(object.instanceId(), parsed.objects.get(object.instanceId()));
    }
  }

  private void validateCompleteObject(String instanceId, JsonNode value) {
    if (!value.path("attributes").isArray()) {
      throw new PlatformException(
          422, "Conceptual object '" + instanceId + "' requires an attributes array.");
    }
    JsonNode associations = value.path("associations");
    if (!associations.isObject()
        || !associations.path("compositions").isArray()
        || !associations.path("references").isArray()) {
      throw new PlatformException(
          422,
          "Conceptual object '"
              + instanceId
              + "' requires associations with compositions and references arrays.");
    }
    for (String kind : List.of("compositions", "references")) {
      for (JsonNode association : associations.path(kind)) {
        if (!association.isObject()
            || !nonBlankText(association.get("associationName"))
            || !nonBlankText(association.get("associatedClassName"))
            || !nonBlankText(association.get("instanceID"))) {
          throw new PlatformException(
              422,
              "Conceptual object '"
                  + instanceId
                  + "' has an invalid "
                  + kind
                  + " entry. associationName, associatedClassName, and instanceID must be"
                  + " non-empty JSON strings; remove placeholder entries and use [] for no"
                  + " association.");
        }
      }
    }
    if (value.has("evidence") && !value.path("evidence").isArray()) {
      throw new PlatformException(
          422, "Conceptual object '" + instanceId + "' requires evidence to be an array.");
    }
  }

  private static boolean nonBlankText(JsonNode value) {
    return value != null && value.isTextual() && !value.asText().trim().isBlank();
  }

  private AssistantModelProvider.AssistantReply review(
      ModelLevel level,
      String request,
      String metamodel,
      Blueprint blueprint,
      LinkedHashMap<String, JsonNode> generated,
      UsageAudit audit,
      int maxCalls) {
    if (audit.totalCalls() >= maxCalls) {
      throw new PlatformException(
          429, "No provider-call budget remains for conceptual quality review.");
    }
    String system =
        "Review a generated "
            + level.name()
            + " conceptual model for modeling usefulness and grounding. Check every user/source"
            + " requirement, duplicated or conflated concepts, meaningful names, appropriate"
            + " abstraction, relationship quality, unsupported inventions, and whether important"
            + " concepts were incorrectly reduced to free text. Return strict JSON with only"
            + " acceptable and findings. Return at most one concise release-blocking finding;"
            + " never regenerate objects and never return prose.";
    String user =
        "REQUEST AND SOURCE:\n"
            + (request == null ? "" : request)
            + "\n\nBLUEPRINT:\n"
            + blueprint.json()
            + "\n\nGENERATED SEMANTIC INVENTORY:\n"
            + compactGenerated(generated)
            + "\n\nReturn {acceptable,findings}.";
    AssistantModelProvider.AssistantReply reply;
    try {
      reply = audit.call(system, user, "conceptual_review");
      return finishQualityReview(
          reply, level, request, metamodel, blueprint, generated, audit, maxCalls);
    } catch (RuntimeException failure) {
      if (audit.totalCalls() >= maxCalls) throw failure;
      String reducedUser =
          "REQUEST AND SOURCE:\n"
              + (request == null ? "" : request)
              + "\n\nPLANNED OBJECT PURPOSES:\n"
              + compactBlueprint(blueprint)
              + "\n\nGENERATED SEMANTIC INVENTORY:\n"
              + compactGenerated(generated)
              + "\n\nThe prior review was rejected by the provider/protocol boundary: "
              + safe(failure.getMessage())
              + ". Return a much smaller terminal JSON"
              + " object with only acceptable and at most one short finding.";
      reply = audit.call(system, reducedUser, "conceptual_review");
      return finishQualityReview(
          reply, level, request, metamodel, blueprint, generated, audit, maxCalls);
    }
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
            .filter(
                object ->
                    diagnostic != null
                        && (diagnostic.contains("'" + object.instanceId() + "'")
                            || diagnostic.contains(object.instanceId() + ".")
                            || diagnostic.contains(object.type() + ".")))
            .map(BlueprintObject::instanceId)
            .limit(4)
            .toList();
    if (affectedIds.isEmpty()) {
      affectedIds = blueprint.objects().stream().map(BlueprintObject::instanceId).limit(1).toList();
    }
    var rejected = JsonNodeFactory.instance.objectNode();
    affectedIds.forEach(id -> rejected.set(id, generated.get(id).deepCopy()));
    List<String> affectedTypes =
        affectedIds.stream().map(id -> generated.get(id).path("type").asText()).distinct().toList();
    String focusedMetamodel =
        guides.generateForTypes(level, contracts.requiredContainmentClosure(level, affectedTypes));
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
            + focusedMetamodel
            + "\n\nBLUEPRINT:\n"
            + blueprint.json()
            + "\n\nONLY AFFECTED REJECTED OBJECTS:\n"
            + rejected
            + "\n\nCOMPILER DIAGNOSTIC:\n"
            + safe(diagnostic);
    AssistantModelProvider.AssistantReply reply;
    try {
      reply = audit.call(system, user, "conceptual_correction");
      applyReview(reply.content(), blueprint, generated, affectedIds.size());
    } catch (RuntimeException firstFailure) {
      if (audit.totalCalls() >= maxCalls) throw firstFailure;
      String retryUser =
          user
              + "\n\nThe prior correction was rejected or truncated: "
              + safe(firstFailure.getMessage())
              + " Return a tiny terminal JSON object correcting ONLY "
              + affectedIds
              + ". findings must contain at most one short item.";
      reply = audit.call(system, retryUser, "conceptual_correction");
      applyReview(reply.content(), blueprint, generated, affectedIds.size());
    }
    return reply;
  }

  private void applyReview(
      String content,
      Blueprint blueprint,
      LinkedHashMap<String, JsonNode> generated,
      int maxCorrections) {
    JsonNode review = strictObject(content, "conceptual review");
    if (review.path("acceptable").asBoolean(false) && review.isObject()) {
      var object = (tools.jackson.databind.node.ObjectNode) review;
      if (!review.path("findings").isArray()) object.putArray("findings");
      if (!review.path("corrections").isObject()) object.putObject("corrections");
    }
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
    if (corrections.size() > maxCorrections) {
      throw new PlatformException(
          422, "Conceptual review may correct at most " + maxCorrections + " objects.");
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

  private JsonNode compactBlueprint(Blueprint blueprint) {
    var result = JsonNodeFactory.instance.arrayNode();
    for (BlueprintObject object : blueprint.objects()) {
      var item = result.addObject();
      item.put("instanceId", object.instanceId());
      item.put("type", object.type());
      item.put("purpose", object.purpose());
      if (!object.referenceTargets().isEmpty()) {
        item.set("referenceTargets", mapper.valueToTree(object.referenceTargets()));
      }
      if (!object.sourceUnitIds().isEmpty()) {
        item.set("sourceUnitIds", mapper.valueToTree(object.sourceUnitIds()));
      }
    }
    return result;
  }

  private JsonNode compactGenerated(LinkedHashMap<String, JsonNode> generated) {
    var result = JsonNodeFactory.instance.arrayNode();
    generated.forEach(
        (id, value) -> {
          var item = result.addObject();
          item.put("instanceId", id);
          item.put("type", value.path("type").asText());
          var names = item.putObject("attributes");
          for (JsonNode attribute : value.path("attributes")) {
            String name = attribute.path("attributeName").asText();
            if (java.util.Set.of("name", "summary", "description", "statement", "term")
                .contains(name)) names.set(name, attribute.path("value").deepCopy());
          }
          var relationships = item.putArray("relationships");
          for (String kind : List.of("compositions", "references")) {
            for (JsonNode relationship : value.path("associations").path(kind)) {
              relationships.add(
                  relationship.path("associationName").asText()
                      + "->"
                      + relationship.path("instanceID").asText());
            }
          }
          var evidence = item.putArray("sourceUnitIds");
          for (JsonNode entry : value.path("evidence")) {
            if (entry.hasNonNull("sourceUnitId")) evidence.add(entry.path("sourceUnitId").asText());
          }
        });
    return result;
  }

  private AssistantModelProvider.AssistantReply finishQualityReview(
      AssistantModelProvider.AssistantReply reply,
      ModelLevel level,
      String request,
      String metamodel,
      Blueprint blueprint,
      LinkedHashMap<String, JsonNode> generated,
      UsageAudit audit,
      int maxCalls) {
    JsonNode review = requireReview(reply.content());
    if (review.path("acceptable").asBoolean(false)) return reply;
    if (audit.totalCalls() >= maxCalls) {
      throw new PlatformException(
          422, "Conceptual quality review rejected the staged model: " + review.path("findings"));
    }
    return correctCompilerFailure(
        level,
        request,
        metamodel,
        blueprint,
        generated,
        "QUALITY REVIEW FINDINGS: " + review.path("findings"),
        audit,
        maxCalls);
  }

  private JsonNode requireReview(String content) {
    JsonNode review = strictObject(content, "conceptual review");
    if (!review.has("acceptable") || !review.path("findings").isArray()) {
      throw new PlatformException(422, "Conceptual review must contain acceptable and findings.");
    }
    return review;
  }

  private Set<String> sourceUnitIds(String request) {
    if (request == null || request.isBlank()) return Set.of();
    java.util.regex.Matcher matcher =
        java.util.regex.Pattern.compile("<source-unit\\s+id=\\\"([^\\\"]+)\\\"").matcher(request);
    LinkedHashSet<String> result = new LinkedHashSet<>();
    while (matcher.find()) result.add(matcher.group(1));
    return result;
  }

  private Blueprint restoredBlueprint(String turnId, ModelLevel level) {
    if (turns == null || turnId == null || turnId.isBlank()) return null;
    return turns
        .workflow(turnId)
        .filter(workflow -> "CONCEPTUAL_GENERATION".equals(workflow.workflowKind()))
        .map(AssistantTurnStore.Workflow::plan)
        .filter(plan -> plan != null && plan.path("blueprint").isObject())
        .map(plan -> Blueprint.parse(mapper, plan.path("blueprint").toString(), contracts, level))
        .orElse(null);
  }

  private void persistBlueprint(String turnId, Blueprint blueprint) {
    if (turns == null || turnId == null || turnId.isBlank()) return;
    var plan = durablePlan(turnId);
    plan.set("blueprint", blueprint.json().deepCopy());
    if (!plan.has("sliceSize")) plan.put("sliceSize", defaultSliceSize());
    turns.saveWorkflow(
        new AssistantTurnStore.Workflow(turnId, "CONCEPTUAL_GENERATION", "SLICING", null, plan));
    persistObjects(turnId, blueprint, new LinkedHashMap<>());
  }

  private LinkedHashMap<String, JsonNode> restoredObjects(String turnId, Blueprint blueprint) {
    LinkedHashMap<String, JsonNode> result = new LinkedHashMap<>();
    if (turns == null || turnId == null || turnId.isBlank()) return result;
    Map<Integer, BlueprintObject> planned = new LinkedHashMap<>();
    for (int index = 0; index < blueprint.objects().size(); index++) {
      planned.put(index + 1, blueprint.objects().get(index));
    }
    for (AssistantTurnStore.WorkItem item : turns.workItems(turnId)) {
      BlueprintObject object = planned.get(item.ordinal());
      JsonNode generated = item.payload() == null ? null : item.payload().get("generated");
      if (object != null && generated != null && generated.isObject()) {
        result.put(object.instanceId(), generated.deepCopy());
      }
    }
    return result;
  }

  private void persistObjects(
      String turnId, Blueprint blueprint, LinkedHashMap<String, JsonNode> generated) {
    if (turns == null || turnId == null || turnId.isBlank()) return;
    List<AssistantTurnStore.WorkItem> items = new ArrayList<>();
    String next = null;
    for (int index = 0; index < blueprint.objects().size(); index++) {
      BlueprintObject object = blueprint.objects().get(index);
      JsonNode value = generated.get(object.instanceId());
      var payload = JsonNodeFactory.instance.objectNode();
      payload.set("blueprintObject", blueprint.json().path("objects").get(index).deepCopy());
      if (value != null) payload.set("generated", value.deepCopy());
      String id = turnId + ":conceptual:" + (index + 1);
      String status = value == null ? "pending" : "generated";
      if (next == null && value == null) next = id;
      items.add(
          new AssistantTurnStore.WorkItem(
              id,
              index + 1,
              object.instanceId() + " (slice " + object.slice() + ")",
              status,
              "conceptual-object-" + object.instanceId(),
              payload));
    }
    turns.saveWorkItems(turnId, items);
    var plan = durablePlan(turnId);
    plan.set("blueprint", blueprint.json().deepCopy());
    turns.saveWorkflow(
        new AssistantTurnStore.Workflow(
            turnId, "CONCEPTUAL_GENERATION", next == null ? "REVIEWING" : "SLICING", next, plan));
  }

  private int persistedSliceSize(String turnId) {
    if (turns == null || turnId == null || turnId.isBlank()) return defaultSliceSize();
    return turns
        .workflow(turnId)
        .map(AssistantTurnStore.Workflow::plan)
        .map(plan -> plan.path("sliceSize").asInt(defaultSliceSize()))
        .map(size -> Math.max(1, Math.min(2, size)))
        .orElse(defaultSliceSize());
  }

  private void persistSliceSize(String turnId, int size, String diagnostic) {
    if (turns == null || turnId == null || turnId.isBlank()) return;
    var workflow = turns.workflow(turnId);
    var plan = durablePlan(turnId);
    plan.put("sliceSize", Math.max(1, Math.min(2, size)));
    plan.put("lastTruncationDiagnostic", safe(diagnostic));
    turns.saveWorkflow(
        new AssistantTurnStore.Workflow(
            turnId,
            "CONCEPTUAL_GENERATION",
            "SLICING",
            workflow.map(AssistantTurnStore.Workflow::currentWorkItemId).orElse(null),
            plan));
  }

  private tools.jackson.databind.node.ObjectNode durablePlan(String turnId) {
    var plan = JsonNodeFactory.instance.objectNode();
    if (turns == null || turnId == null || turnId.isBlank()) return plan;
    turns
        .workflow(turnId)
        .map(AssistantTurnStore.Workflow::plan)
        .filter(JsonNode::isObject)
        .ifPresent(existing -> plan.setAll((tools.jackson.databind.node.ObjectNode) existing));
    return plan;
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
    private final String turnId;
    private final int maxCalls;
    private int priorCalls;
    private long promptTokens;
    private long completionTokens;
    private final List<AssistantTurnStore.ProviderCall> callDetails = new ArrayList<>();

    private UsageAudit(String turnId, int maxCalls) {
      this.turnId = turnId;
      this.maxCalls = maxCalls;
      if (turns != null && turnId != null && !turnId.isBlank()) {
        JsonNode plan = turns.workflow(turnId).map(AssistantTurnStore.Workflow::plan).orElse(null);
        if (plan != null) {
          priorCalls = Math.max(0, plan.path("providerCalls").asInt(0));
          promptTokens = Math.max(0L, plan.path("promptTokens").asLong(0));
          completionTokens = Math.max(0L, plan.path("completionTokens").asLong(0));
        }
      }
    }

    private AssistantModelProvider.AssistantReply call(
        String system, String user, String requiredTool) {
      if (totalCalls() >= maxCalls) {
        throw new PlatformException(
            429, "Conceptual generation exhausted its durable provider-call budget.");
      }
      String callKey = java.util.UUID.randomUUID().toString();
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
        AssistantTurnStore.ProviderCall failed =
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
                safe(failure.getMessage()),
                callKey);
        record(failed);
        throw failure;
      }
      long latency = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
      if (reply.usage().reported()) {
        promptTokens += Math.max(0, reply.usage().promptTokens());
        completionTokens += Math.max(0, reply.usage().completionTokens());
      }
      AssistantTurnStore.ProviderCall completed =
          new AssistantTurnStore.ProviderCall(
              reply.provider(),
              reply.model(),
              latency,
              reply.usage().promptTokens(),
              reply.usage().completionTokens(),
              reply.usage().reported(),
              reply.systemPrompt() == null ? system : reply.systemPrompt(),
              reply.userPrompt() == null ? user : reply.userPrompt(),
              "COMPLETED",
              null,
              callKey);
      record(completed);
      return reply;
    }

    private void record(AssistantTurnStore.ProviderCall call) {
      callDetails.add(call);
      if (turns == null || turnId == null || turnId.isBlank()) return;
      turns.recordProviderCall(turnId, call);
      var workflow = turns.workflow(turnId);
      var plan = durablePlan(turnId);
      plan.put("providerCalls", totalCalls());
      plan.put("promptTokens", promptTokens);
      plan.put("completionTokens", completionTokens);
      turns.saveWorkflow(
          new AssistantTurnStore.Workflow(
              turnId,
              "CONCEPTUAL_GENERATION",
              workflow.map(AssistantTurnStore.Workflow::phase).orElse("PLANNING"),
              workflow.map(AssistantTurnStore.Workflow::currentWorkItemId).orElse(null),
              plan));
    }

    private int totalCalls() {
      return priorCalls + callDetails.size();
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
      if (root.path("objects").size() > 8) {
        throw new PlatformException(422, "Conceptual blueprint may declare at most 8 objects.");
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
