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
  private static final int MAX_BLUEPRINT_OBJECTS = 96;
  private static final int MAX_OBLIGATIONS = 64;
  private static final int MAX_BLUEPRINT_ATTEMPTS = 10;
  private static final int MAX_BLUEPRINT_CRITIQUES = 3;
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
    return "{\"type\":\"object\",\"minProperties\":1,\"maxProperties\":2,"
               + "\"additionalProperties\":{\"type\":\"object\",\"additionalProperties\":false,"
               + "\"required\":[\"type\",\"attributes\",\"associations\"],"
               + "\"properties\":{\"type\":{\"type\":\"string\",\"minLength\":1},"
               + "\"attributes\":{\"type\":\"array\",\"maxItems\":8,"
               + "\"items\":{\"type\":\"object\",\"additionalProperties\":false,"
               + "\"required\":[\"attributeName\",\"value\"],\"properties\":{"
               + "\"dataType\":{\"type\":\"string\"},\"attributeName\":{\"type\":\"string\",\"minLength\":1},"
               + "\"value\":{}}}},\"associations\":{\"type\":\"object\","
               + "\"additionalProperties\":false,\"required\":[\"compositions\",\"references\"],"
               + "\"properties\":{\"compositions\":{\"type\":\"array\",\"maxItems\":16,"
               + "\"items\":{\"$ref\":\"#/$defs/association\"}},"
               + "\"references\":{\"type\":\"array\",\"maxItems\":24,\"items\":{\"$ref\":\"#/$defs/association\"}}}},"
               + "\"evidence\":{\"type\":\"array\",\"maxItems\":16,"
               + "\"items\":{\"type\":\"object\",\"additionalProperties\":false,"
               + "\"required\":[\"sourceUnitId\",\"kind\"],\"properties\":{"
               + "\"sourceUnitId\":{\"type\":\"string\"},\"requirementId\":{\"type\":\"string\"},"
               + "\"kind\":{\"type\":\"string\",\"enum\":[\"SOURCE_GROUNDED\",\"INFERRED\"]},"
               + "\"assumption\":{\"type\":\"string\"}}}}}},"
               + "\"$defs\":{\"association\":{\"type\":\"object\","
               + "\"additionalProperties\":false,\"required\":[\"associationName\","
               + "\"associatedClassName\",\"instanceID\"],"
               + "\"properties\":{\"associationName\":{\"type\":\"string\",\"minLength\":1},"
               + "\"associatedClassName\":{\"type\":\"string\",\"minLength\":1},"
               + "\"instanceID\":{\"type\":\"string\",\"minLength\":1}}}}}";
  }

  /** Strict allowlisted schema for the semantic metamodel-contract selection pass. */
  public static String typeSelectionSchema() {
    return "{\"type\":\"object\",\"required\":[\"types\"],\"additionalProperties\":false,"
        + "\"properties\":{\"types\":{\"type\":\"array\",\"minItems\":1,\"maxItems\":"
        + MAX_BLUEPRINT_OBJECTS
        + ","
        + "\"uniqueItems\":true,\"items\":{\"type\":\"string\",\"minLength\":1}}}}";
  }

  /** Strict schema for the LLM-owned requirement interpretation persisted before type selection. */
  public static String obligationLedgerSchema() {
    return "{\"type\":\"object\",\"required\":[\"obligations\"],\"additionalProperties\":false,"
        + "\"properties\":{\"obligations\":{\"type\":\"array\",\"minItems\":1,\"maxItems\":"
        + MAX_OBLIGATIONS
        + ",\"items\":{\"type\":\"object\",\"additionalProperties\":false,"
        + "\"required\":[\"id\",\"obligation\",\"importance\",\"minimumEvidenceObjects\",\"sourceUnitIds\",\"expectedEClasses\"],"
        + "\"properties\":{\"id\":{\"type\":\"string\",\"minLength\":1},"
        + "\"obligation\":{\"type\":\"string\",\"minLength\":1},"
        + "\"importance\":{\"type\":\"string\",\"enum\":[\"MANDATORY\",\"OPTIONAL\"]},"
        + "\"minimumEvidenceObjects\":{\"type\":\"integer\",\"minimum\":1,\"maximum\":"
        + MAX_BLUEPRINT_OBJECTS
        + "},"
        + "\"sourceUnitIds\":{\"type\":\"array\",\"items\":{\"type\":\"string\"}},"
        + "\"expectedEClasses\":{\"type\":\"array\",\"minItems\":1,\"maxItems\":4,"
        + "\"uniqueItems\":true,\"items\":{\"type\":\"string\",\"minLength\":1}}}}}}}";
  }

  /** Strict schema for the small, stable-ID plan that precedes bounded object slices. */
  public static String blueprintSchema() {
    return "{\"type\":\"object\",\"required\":[\"types\",\"objects\"],\"additionalProperties\":false,"
               + "\"properties\":{\"types\":{\"type\":\"array\",\"minItems\":1,\"maxItems\":"
        + MAX_BLUEPRINT_OBJECTS
        + ","
        + "\"uniqueItems\":true,\"items\":{\"type\":\"string\",\"minLength\":1}},"
        + "\"objects\":{\"type\":\"array\",\"minItems\":1,\"maxItems\":"
        + MAX_BLUEPRINT_OBJECTS
        + ",\"items\":{"
        + "\"type\":\"object\",\"required\":[\"instanceId\",\"type\",\"purpose\",\"slice\"],"
        + "\"properties\":{\"instanceId\":{\"type\":\"string\",\"minLength\":1},"
        + "\"type\":{\"type\":\"string\",\"minLength\":1},\"purpose\":{\"type\":\"string\"},"
        + "\"ownerInstanceId\":{\"type\":\"string\"},\"containment\":{\"type\":\"string\"},"
        + "\"referenceTargets\":{\"type\":\"array\",\"items\":{\"type\":\"string\"}},"
        + "\"obligationIds\":{\"type\":\"array\",\"items\":{\"type\":\"string\"}},"
        + "\"sourceUnitIds\":{\"type\":\"array\",\"items\":{\"type\":\"string\"}},"
        + "\"slice\":{\"type\":\"integer\",\"minimum\":1}}}}}}";
  }

  /** Strict schema for an LLM-authored edit to a rejected private blueprint. */
  public static String blueprintPatchSchema() {
    String object =
        "{\"type\":\"object\",\"required\":[\"instanceId\",\"type\",\"purpose\",\"slice\"],"
            + "\"additionalProperties\":false,\"properties\":{"
            + "\"instanceId\":{\"type\":\"string\",\"minLength\":1},"
            + "\"type\":{\"type\":\"string\",\"minLength\":1},\"purpose\":{\"type\":\"string\"},"
            + "\"ownerInstanceId\":{\"type\":\"string\"},\"containment\":{\"type\":\"string\"},"
            + "\"referenceTargets\":{\"type\":\"array\","
            + "\"items\":{\"type\":\"string\"}},\"obligationIds\":{\"type\":\"array\","
            + "\"items\":{\"type\":\"string\"}},\"sourceUnitIds\":{\"type\":\"array\","
            + "\"items\":{\"type\":\"string\"}},\"slice\":{\"type\":\"integer\",\"minimum\":1}}}";
    return "{\"type\":\"object\",\"required\":[\"removeObjectIds\",\"upsertObjects\"],"
        + "\"additionalProperties\":false,\"properties\":{\"removeObjectIds\":{\"type\":\"array\","
        + "\"maxItems\":24,\"uniqueItems\":true,\"items\":{\"type\":\"string\"}},"
        + "\"upsertObjects\":{\"type\":\"array\",\"maxItems\":24,\"items\":"
        + object
        + "}}}";
  }

  /** Schema for the semantic review; corrections are bounded conceptual objects, never prose. */
  public static String reviewSchema() {
    return "{\"type\":\"object\",\"required\":[\"acceptable\",\"findings\"],"
               + "\"additionalProperties\":false,\"properties\":{\"acceptable\":{\"type\":\"boolean\"},"
               + "\"findings\":{\"type\":\"array\",\"maxItems\":1,\"items\":{\"type\":\"object\","
               + "\"additionalProperties\":false,\"required\":[\"objectIds\",\"problem\","
               + "\"recommendedCorrection\"],\"properties\":{\"objectIds\":{\"type\":\"array\",\"minItems\":1,"
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

  public static String obligationReviewSchema() {
    return "{\"type\":\"object\",\"required\":[\"acceptable\",\"coverage\",\"findings\"],"
        + "\"additionalProperties\":false,\"properties\":{\"acceptable\":{\"type\":\"boolean\"},"
        + "\"coverage\":{\"type\":\"array\",\"maxItems\":"
        + MAX_OBLIGATIONS
        + ",\"items\":{\"type\":\"object\","
        + "\"additionalProperties\":false,\"required\":[\"obligationId\",\"state\","
        + "\"evidenceObjectIds\",\"evidenceRelationships\",\"explanation\"],"
        + "\"properties\":{\"obligationId\":{\"type\":\"string\"},"
        + "\"state\":{\"type\":\"string\",\"enum\":[\"SATISFIED\",\"PARTIAL\",\"MISSING\"]},"
        + "\"evidenceObjectIds\":{\"type\":\"array\",\"items\":{\"type\":\"string\"}},"
        + "\"evidenceRelationships\":{\"type\":\"array\",\"items\":{\"type\":\"object\","
        + "\"additionalProperties\":false,\"required\":[\"sourceId\",\"feature\",\"targetId\"],"
        + "\"properties\":{\"sourceId\":{\"type\":\"string\"},"
        + "\"feature\":{\"type\":\"string\"},\"targetId\":{\"type\":\"string\"}}}},"
        + "\"explanation\":{\"type\":\"string\"}}}},"
        + "\"findings\":{\"type\":\"array\",\"maxItems\":1,\"items\":{\"type\":\"string\"}}}}";
  }

  /** Strict pre-generation review of whether the private blueprint represents the request. */
  public static String blueprintCompletenessSchema() {
    return "{\"type\":\"object\",\"required\":[\"acceptable\",\"findings\"],"
        + "\"additionalProperties\":false,\"properties\":{\"acceptable\":{\"type\":\"boolean\"},"
        + "\"findings\":{\"type\":\"array\",\"maxItems\":12,\"items\":{\"type\":\"object\","
        + "\"additionalProperties\":false,\"required\":[\"missingConcept\",\"reason\","
        + "\"recommendedCorrection\"],\"properties\":{\"missingConcept\":{\"type\":\"string\"},"
        + "\"reason\":{\"type\":\"string\"},\"recommendedCorrection\":{\"type\":\"string\"}}}}}}";
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
    int configuredMaxCalls =
        Math.max(
            1,
            sourceBacked
                ? properties.maxProviderCallsSourceTurn()
                : properties.maxProviderCallsPerTurn());
    boolean ownsProviderBudget = !ProviderCallBudget.isBound();
    int maxCalls =
        Math.max(
            1,
            ownsProviderBudget
                ? configuredMaxCalls
                : Math.min(configuredMaxCalls, Math.max(1, properties.maxProviderCallsPerTurn())));
    UsageAudit audit = new UsageAudit(durableTurnId, maxCalls);
    if (ownsProviderBudget) ProviderCallBudget.bind(Math.max(1, audit.remainingCalls()));
    try {
      try {
        JsonNode current = workspace.snapshot();
        ObligationLedger obligations = restoredObligations(durableTurnId, level);
        if (obligations == null && turns != null) {
          obligations = planObligations(level, request, current, audit, maxCalls);
          persistObligations(durableTurnId, obligations);
        }
        if (obligations == null) obligations = ObligationLedger.empty();
        Blueprint blueprint = restoredBlueprint(durableTurnId, level, current);
        if (blueprint == null) {
          Set<String> selectedTypes = restoredSelectedTypes(durableTurnId, level);
          if (selectedTypes.isEmpty()) {
            selectedTypes =
                selectMetamodelTypes(level, request, current, obligations, audit, maxCalls);
            persistSelectedTypes(durableTurnId, selectedTypes);
          }
          blueprint =
              planBlueprint(level, request, current, selectedTypes, obligations, audit, maxCalls);
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
        if (properties.llmReviewEnabled()) {
          last =
              obligations.obligations().isEmpty()
                  ? review(
                      level, request, current, metamodel, blueprint, generated, audit, maxCalls)
                  : reviewObligations(
                      level, request, current, obligations, blueprint, generated, audit, maxCalls);
        } else {
          AssistantModelProvider.AssistantProviderMetadata metadata = provider.metadata();
          last =
              new AssistantModelProvider.AssistantReply(
                  "",
                  metadata.provider(),
                  metadata.model() == null ? properties.model() : metadata.model());
        }
        persistObjects(durableTurnId, blueprint, generated);
        ConceptualModel conceptual = new ConceptualModel(generated);
        ModelCommandBatch batch = null;
        ModelWorkspace.MutationResult mutation = null;
        int compilerCorrections = 0;
        while (mutation == null) {
          try {
            batch = conceptual.commands(current, contracts, level);
            mutation = tools.commitModelBatch(batch, destructiveConfirmed);
          } catch (RuntimeException compilerFailure) {
            if (ProviderCallBudget.isExceeded(compilerFailure)) throw compilerFailure;
            if (audit.totalCalls() >= maxCalls
                || compilerCorrections >= Math.max(1, properties.maxRepairAttempts())) {
              throw new ProgressiveCompilationFailure(compilerFailure);
            }
            last =
                correctCompilerFailure(
                    level,
                    request,
                    current,
                    metamodel,
                    blueprint,
                    generated,
                    compilerFailure.getMessage(),
                    Set.of(),
                    audit,
                    maxCalls);
            persistObjects(durableTurnId, blueprint, generated);
            conceptual = new ConceptualModel(generated);
            compilerCorrections++;
          }
        }
        ModelService.ValidationResult validation = tools.validateModel();
        if (validation == null || !validation.valid()) {
          throw new PlatformException(
              422,
              "Structural validation rejected the conceptual model: "
                  + (validation == null ? "no result" : validation.issues()));
        }
        return new AgentTurnLoop.TurnResult(
            properties.llmReviewEnabled()
                ? "Applied a staged conceptual instance model, reviewed its requirement coverage,"
                    + " and structurally validated the result."
                : "Applied a staged conceptual instance model and structurally validated the"
                    + " result.",
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
            audit.callDetails,
            failure instanceof ProgressiveCompilationFailure);
      }
    } finally {
      if (ownsProviderBudget) ProviderCallBudget.clear();
    }
  }

  private static final class ProgressiveCompilationFailure extends PlatformException {
    private ProgressiveCompilationFailure(RuntimeException cause) {
      super(
          cause instanceof PlatformException platform ? platform.status() : 422,
          cause.getMessage() == null
              ? "Conceptual compilation repair is incomplete."
              : cause.getMessage(),
          cause);
    }
  }

  private ObligationLedger planObligations(
      ModelLevel level, String request, JsonNode current, UsageAudit audit, int maxCalls) {
    String system =
        "Interpret the request into a compact requirement-obligation ledger before model type"
            + " selection. Semantic interpretation and exact EClass mapping are your decisions."
            + " Preserve every explicit functional, data, integration, security, observability,"
            + " and workflow requirement as a separate MANDATORY obligation; use OPTIONAL only"
            + " for genuinely nonessential enrichment. Map each obligation to the smallest jointly"
            + " sufficient set of exact creatable EClasses from the authoritative live index."
            + " Normally use exactly one EClass per obligation; use multiple only when each is"
            + " indispensable distinct semantic evidence. In particular, a requested behavior"
            + " with an explicit consequence, resulting state, invariant, preservation effect,"
            + " or pre/postcondition needs both a legal behavioral carrier and the metamodel type"
            + " that represents that condition or state (for example, a process plus Condition),"
            + " when the authoritative index supports that representation. Likewise explicit"
            + " publication/consumption can need both a channel and event type. Do not list"
            + " containment owners, contracts,"
            + " workflow steps, schemas, principals, policies, routes, or other structural support"
            + " unless the request explicitly requires that concept. The Ecore closure and"
            + " blueprint phases add necessary support. expectedEClasses is a required set, not a"
            + " list of alternatives. Reuse the same"
            + " EClass across obligations when appropriate. Do not match words mechanically and"
            + " do not collapse a source list of independently modelable named actors, domain"
            + " concepts, lifecycle states, rules, risks, acceptance behaviors, commands, queries,"
            + " or events into one token obligation merely to shorten the ledger. Preserve the"
            + " source's semantic granularity while grouping only statements that genuinely form"
            + " one model concept. Never detach an action from its explicitly required consequence;"
            + " retain every conjunct, condition, duration, preservation effect, and state"
            + " transition in the same obligation or in a separate mandatory obligation. Set"
            + " minimumEvidenceObjects to the number of distinct model"
            + " instances required as evidence for the obligation: normally 1, but count every"
            + " jointly necessary carrier, explicit outcome/condition/state, and"
            + " explicitly named member of a finite source list when the metamodel represents"
            + " those members as individual instances (for example, six named lifecycle states"
            + " require 6). Never treat a comma-separated scalar value as multiple instances."
            + " do not generate objects. Use"
            + " stable IDs OBL-1, OBL-2, ... and return JSON only. Every obligation item must"
            + " literally contain a non-empty string id, a non-empty string obligation,"
            + " importance exactly MANDATORY or OPTIONAL, sourceUnitIds as an array, and"
            + " expectedEClasses as a non-empty array of exact creatable EClass names.";
    String user =
        guides.index(level)
            + "\n\nMINIMUM ECORE CLOSURE COST PER TYPE:\n"
            + structuralSelectionCosts(level)
            + "\n\nCURRENT MODEL TYPES:\n"
            + currentTypes(current)
            + "\n\nREQUEST AND SOURCE SPECIFICATION:\n"
            + (request == null ? "" : request)
            + "\n\nAVAILABLE SOURCE UNIT IDS: "
            + sourceUnitIds(request)
            + "\n\n"
            + "The combined required containment closure of all expectedEClasses must fit within "
            + blueprintCapacity(maxCalls)
            + " non-root types. Prefer overlapping EClasses and omit structural helper types;"
            + " the later blueprint phase adds required containment owners."
            + "\n\n"
            + "Return {obligations:[{id,obligation,importance,minimumEvidenceObjects,"
            + "sourceUnitIds,expectedEClasses}]}";
    RuntimeException lastFailure = null;
    String correction = "";
    for (int attempt = 0; attempt < 5; attempt++) {
      String rejectedLedger = "";
      try {
        var reply = audit.call(system, user + correction, "conceptual_obligation_ledger");
        rejectedLedger = reply.content();
        ObligationLedger ledger =
            ObligationLedger.parse(
                strictObject(reply.content(), "conceptual obligation ledger"),
                contracts,
                level,
                sourceUnitIds(request));
        validateObligationLedgerCapacity(level, ledger, blueprintCapacity(maxCalls));
        return ledger;
      } catch (RuntimeException failure) {
        if (ProviderCallBudget.isExceeded(failure)) throw failure;
        if (failure instanceof ProviderInvocationFailure) throw failure;
        lastFailure = failure;
        if (attempt == 4) throw failure;
        correction =
            "\n\nThe prior ledger was rejected: "
                + safe(failure.getMessage())
                + " Return only the corrected compact ledger with at most "
                + MAX_OBLIGATIONS
                + " obligations. Remove structural"
                + " helpers and alternative refinements; keep two EClasses for an obligation only"
                + " when both are semantically indispensable. Correct the prior ledger below"
                + " instead of reconstructing it from memory.\n\nPRIOR REJECTED LEDGER:\n"
                + rejectedLedger;
      }
    }
    throw lastFailure == null
        ? new PlatformException(422, "Conceptual obligation planning failed.")
        : lastFailure;
  }

  private void validateObligationLedgerCapacity(
      ModelLevel level, ObligationLedger ledger, int capacity) {
    LinkedHashSet<String> mandatoryTypes = new LinkedHashSet<>();
    ledger.mandatory().forEach(item -> mandatoryTypes.addAll(item.expectedEClasses()));
    int closureSize =
        contracts.requiredContainmentClosure(level, new ArrayList<>(mandatoryTypes)).stream()
            .filter(type -> !contracts.rootType(level).equals(type.eClass()))
            .map(TypeContract::eClass)
            .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new))
            .size();
    if (closureSize > capacity) {
      throw new PlatformException(
          422,
          "Mandatory obligation EClasses "
              + mandatoryTypes
              + " require a combined structural closure of "
              + closureSize
              + " types, exceeding capacity "
              + capacity
              + ". Merge overlapping obligations and choose a smaller jointly sufficient"
              + " required set without turning expectedEClasses into alternatives.");
    }
  }

  private Blueprint planBlueprint(
      ModelLevel level,
      String request,
      JsonNode current,
      Set<String> selectedTypes,
      ObligationLedger obligations,
      UsageAudit audit,
      int maxCalls) {
    Set<String> suppliedSourceUnits = sourceUnitIds(request);
    List<TypeContract> focusedContracts =
        contracts.requiredContainmentClosure(level, new ArrayList<>(selectedTypes));
    LinkedHashSet<String> focusedTypes = new LinkedHashSet<>();
    focusedContracts.forEach(type -> focusedTypes.add(type.eClass()));
    Set<String> concreteRequiredOptions = concreteRequiredOptions(level, focusedContracts);
    String system =
        "Plan a bounded conceptual instance model for Varka's "
            + level.name()
            + " DSML. Return a small stable-ID ledger, not full attributes or association payloads."
            + " Every object needed for a coherent useful model must have one unique temporary"
            + " instanceId, exact EClass, short purpose, containment owner/feature when known,"
            + " major reference target IDs, source-unit allocation, and a positive slice number."
            + " For every nested object, include that child's ID in its owner's referenceTargets"
            + " so generation can emit the required incoming containment composition."
            + " sourceUnitIds may contain ONLY IDs from the explicit available-source list; when"
            + " that list is empty every sourceUnitIds array must be empty. Every object must use"
            + " an exact legal containment placement from the supplied index. If a desired type is"
            + " nested, also plan its required owner object. Instantiate every EClass selected by"
            + " the semantic type-selection pass at least once; never silently drop a selected"
            + " request concept. Plan at most "
            + blueprintCapacity(maxCalls)
            + " semantically important objects total. Group semantically coherent objects together."
            + " For every object directly contained by the persisted model root,"
            + " ownerInstanceId MUST be the exact literal rootId (not CIMModel, root, cim-root,"
            + " the persisted UUID, or any other alias) and containment must be the bare Ecore"
            + " feature name such as actors or goals."
            + " Every distinct named instance requested by the source needs its own object when the"
            + " metamodel represents instances individually. In particular, never claim that one"
            + " generic object represents a source list of several actors, terms, states, risks,"
            + " rules, behaviors, or domain concepts. Use relationships to make the planned model"
            + " coherent rather than producing an unconnected catalogue."
            + " Use only the closed focused EClass vocabulary plus the supplied concrete options"
            + " for abstract required targets. For EVERY abstract EClass in the selected"
            + " vocabulary, choose a semantically appropriate concrete subtype from the explicit"
            + " options below; the abstract name may remain in types but must NEVER appear as an"
            + " object's type. Never instantiate an abstract/non-creatable EClass. The existing"
            + " root is rootId and must not be planned as an object. Do not include prose or"
            + " markdown. When the persisted-element index contains a concept that satisfies the"
            + " request, reuse its exact persisted ID instead of creating a semantic duplicate."
            + " Include an existing object only when it supplies obligation evidence, receives a"
            + " new relationship, or needs an attribute update. When a new nested object is owned"
            + " by a persisted element, include an exact-ID blueprint record for that persisted"
            + " owner and include the child's ID in the owner's referenceTargets; generation must"
            + " update the parent with the writable Ecore containment.";
    String user =
        "AUTHORITATIVE FOCUSED ECORE CONTRACTS (closed vocabulary):\n"
            + blueprintStructuralGuide(level, focusedContracts, focusedTypes)
            + "\n\nCONCRETE OPTIONS FOR ABSTRACT SELECTED TYPES AND REQUIRED TARGETS:\n"
            + concreteRequiredOptionsGuide(level, focusedContracts)
            + "\n\nCURRENT MODEL TYPES:\n"
            + currentTypes(current)
            + "\n\nCURRENT PERSISTED ELEMENT INDEX (reuse exact IDs; do not duplicate):\n"
            + currentElementIndex(current)
            + "\n\nAUTHORITATIVE FOCUSED CONTAINMENT PLACEMENTS (child <- owner.feature):\n"
            + containmentIndex(level, focusedTypes)
            + "\n\nREQUEST AND SOURCE SPECIFICATION:\n"
            + (request == null ? "" : request)
            + "\n\nMANDATORY REQUIREMENT OBLIGATION LEDGER:\n"
            + obligations.json()
            + "\n\nAVAILABLE SOURCE UNIT IDS (closed allowlist): "
            + suppliedSourceUnits
            + "\n\nReturn {types:[exact EClass names],objects:[{instanceId,type,purpose,"
            + "ownerInstanceId,containment,referenceTargets,obligationIds,sourceUnitIds,slice}]}."
            + " Every mandatory obligation ID must be allocated to one or more objects whose"
            + " exact EClass is one of its expected EClasses or an assignable concrete subtype."
            + " Do not emit"
            + " attributes or complete conceptual objects.";
    Blueprint blueprint = null;
    String correction = "";
    String rejectionDiagnostic = "";
    JsonNode candidateJson = null;
    int blueprintCritiques = 0;
    RuntimeException lastFailure = null;
    for (int attempt = 0; attempt < MAX_BLUEPRINT_ATTEMPTS; attempt++) {
      try {
        if (candidateJson == null) {
          var reply = audit.call(system, user + correction, "conceptual_blueprint");
          candidateJson = strictObject(reply.content(), "conceptual blueprint");
        } else {
          candidateJson =
              repairBlueprint(
                  level,
                  request,
                  current,
                  obligations,
                  focusedContracts,
                  focusedTypes,
                  suppliedSourceUnits,
                  candidateJson,
                  rejectionDiagnostic,
                  audit,
                  maxCalls);
        }
      } catch (RuntimeException failure) {
        if (ProviderCallBudget.isExceeded(failure)) throw failure;
        if (failure instanceof ProviderInvocationFailure) throw failure;
        lastFailure = failure;
        if (attempt < MAX_BLUEPRINT_ATTEMPTS - 1 && (candidateJson != null || truncated(failure))) {
          rejectionDiagnostic =
              rejectionDiagnostic
                  + "\nPrior blueprint repair attempt failed: "
                  + safe(failure.getMessage());
          if (candidateJson != null) continue;
          correction +=
              "\n\nThe prior blueprint response was truncated. Continue to honor every earlier"
                  + " rejection diagnostic above. Return only the compact ledger"
                  + " fields requested below, with at most "
                  + blueprintCapacity(maxCalls)
                  + " objects and no prose, attributes, or"
                  + " duplicate explanatory text.";
          continue;
        }
        throw failure;
      }
      try {
        Map<String, String> persistedTypes = currentElementTypes(current);
        blueprint =
            Blueprint.parse(
                mapper, candidateJson.toString(), contracts, level, persistedTypes.keySet());
        blueprint = normalizeBlueprintPlacements(level, blueprint, persistedTypes);
        Set<String> outsideFocus = new LinkedHashSet<>(blueprint.types());
        outsideFocus.removeAll(focusedTypes);
        outsideFocus.removeAll(concreteRequiredOptions);
        if (!outsideFocus.isEmpty()) {
          throw new PlatformException(
              422,
              "Conceptual blueprint used EClasses outside the selected closed vocabulary: "
                  + outsideFocus);
        }
        Set<String> plannedTypes =
            blueprint.objects().stream()
                .map(BlueprintObject::type)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        Set<String> omittedSelectedTypes = new LinkedHashSet<>(selectedTypes);
        // The persisted model root is structural context, not a creatable semantic object. Type
        // selection may include it because the focused Ecore closure starts there, but requiring
        // the blueprint to instantiate it contradicts Blueprint.parse(), which correctly rejects
        // a second CIMModel/PIMModel root.
        omittedSelectedTypes.remove(contracts.rootType(level));
        omittedSelectedTypes.removeIf(
            selectedType ->
                plannedTypes.stream()
                        .anyMatch(
                            plannedType -> contracts.assignable(level, plannedType, selectedType))
                    || persistedTypes.values().stream()
                        .anyMatch(
                            persistedType ->
                                contracts.assignable(level, persistedType, selectedType)));
        if (!omittedSelectedTypes.isEmpty()) {
          throw new PlatformException(
              422,
              "Conceptual blueprint omitted selected semantic EClasses "
                  + omittedSelectedTypes
                  + "; instantiate every selected type at least once or revise type selection"
                  + " before blueprinting.");
        }
        Set<String> allocated = new LinkedHashSet<>();
        for (BlueprintObject object : blueprint.objects()) {
          allocated.addAll(object.sourceUnitIds());
        }
        List<String> diagnostics =
            blueprintDiagnostics(level, blueprint, obligations, persistedTypes);
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
        boolean sourceBacked =
            request != null && request.contains("SOURCE SPECIFICATION (authoritative input)");
        if (properties.llmReviewEnabled()
            && (sourceBacked || hasPersistedElements(current))
            && blueprintCritiques < MAX_BLUEPRINT_CRITIQUES) {
          blueprintCritiques++;
          requireBlueprintCompletenessReview(
              level, request, current, obligations, blueprint, audit, maxCalls);
        }
        break;
      } catch (RuntimeException failure) {
        if (failure instanceof ProviderInvocationFailure) throw failure;
        lastFailure = failure;
        blueprint = null;
        if (attempt == MAX_BLUEPRINT_ATTEMPTS - 1) throw failure;
        // A critic-produced patch is only a proposal. Re-review it independently while critique
        // budget remains. The last bounded critique is still repaired; the next iteration
        // structurally checks that LLM-authored repair and then proceeds to generation, where the
        // independent obligation review remains the semantic gate. This prevents an open-ended
        // tail of optional refinements from failing an otherwise repairable durable turn.
        rejectionDiagnostic = safe(failure.getMessage());
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

  private JsonNode repairBlueprint(
      ModelLevel level,
      String request,
      JsonNode current,
      ObligationLedger obligations,
      List<TypeContract> focusedContracts,
      Set<String> focusedTypes,
      Set<String> suppliedSourceUnits,
      JsonNode candidate,
      String diagnostic,
      UsageAudit audit,
      int maxCalls) {
    if (audit.totalCalls() >= maxCalls) throw ProviderCallBudget.exceeded();
    String system =
        "Repair a rejected private conceptual-model blueprint with the smallest coherent edit."
            + " Return only removeObjectIds and upsertObjects. An upsert must contain the complete"
            + " blueprint record for that object, including its stable ID, exact EClass, purpose,"
            + " owner/containment, declared reference target IDs, obligation/source allocations,"
            + " and slice. Every referenced or owning non-root ID must exist after the patch."
            + " When a persisted element supplies obligation evidence, upsert a blueprint record"
            + " using its exact persisted ID and EClass and allocate the obligation to that record;"
            + " merely referencing its ID from another object does not allocate evidence. This"
            + " plans an update/reuse and does not create a duplicate persisted element."
            + " The persisted model root is not a blueprint object: represent it only with the"
            + " exact ownerInstanceId literal rootId and a bare containment feature name. Never"
            + " use CIMModel, root, cim-root, or the persisted UUID as ownerInstanceId."
            + " The candidate currently has "
            + candidate.path("objects").size()
            + " objects and the hard merged limit is "
            + MAX_BLUEPRINT_OBJECTS
            + ". If new IDs would exceed that limit, remove enough genuinely redundant support"
            + " objects in the same patch and update every affected reference."
            + " Treat the diagnostic as mandatory. Do not compress or remove distinct named source"
            + " concepts to save space; remove only redundant support and update all affected"
            + " references. Do not return a complete blueprint, attributes, prose, or markdown.";
    String user =
        "REJECTION DIAGNOSTIC:\n"
            + diagnostic
            + "\n\nFOCUSED ECORE CONTRACTS:\n"
            + blueprintStructuralGuide(level, focusedContracts, focusedTypes)
            + "\n\nCONCRETE OPTIONS FOR ABSTRACT TYPES AND REQUIRED TARGETS:\n"
            + concreteRequiredOptionsGuide(level, focusedContracts)
            + "\n\nLEGAL CONTAINMENT PLACEMENTS:\n"
            + containmentIndex(level, focusedTypes)
            + "\n\nREQUEST AND SOURCE SPECIFICATION:\n"
            + (request == null ? "" : request)
            + "\n\nCURRENT PERSISTED ELEMENT INDEX (reuse exact IDs; do not duplicate):\n"
            + currentElementIndex(current)
            + "\n\nOBLIGATION LEDGER:\n"
            + obligations.json()
            + "\n\nAVAILABLE SOURCE UNIT IDS: "
            + suppliedSourceUnits
            + "\n\nREJECTED BLUEPRINT TO EDIT:\n"
            + candidate
            + "\n\n"
            + "Return {removeObjectIds:[stable IDs],upsertObjects:[complete blueprint records]}.";
    var reply = audit.call(system, user, "conceptual_blueprint_patch");
    JsonNode patch = strictObject(reply.content(), "conceptual blueprint patch");
    if (!patch.path("removeObjectIds").isArray() || !patch.path("upsertObjects").isArray()) {
      throw new PlatformException(
          422, "Conceptual blueprint patch requires removeObjectIds and upsertObjects arrays.");
    }
    LinkedHashMap<String, JsonNode> objects = new LinkedHashMap<>();
    candidate
        .path("objects")
        .forEach(item -> objects.put(item.path("instanceId").asText(""), item.deepCopy()));
    for (JsonNode removal : patch.path("removeObjectIds")) {
      String id = removal.asText("").trim();
      if (id.isBlank() || objects.remove(id) == null) {
        throw new PlatformException(
            422, "Blueprint patch tried to remove unknown object '" + id + "'.");
      }
    }
    for (JsonNode upsert : patch.path("upsertObjects")) {
      String id = upsert.path("instanceId").asText("").trim();
      if (id.isBlank() || "rootId".equals(id)) {
        throw new PlatformException(422, "Blueprint patch upserts require a non-root instanceId.");
      }
      objects.put(id, upsert.deepCopy());
    }
    if (objects.isEmpty()) {
      throw new PlatformException(422, "Blueprint patch cannot remove every planned object.");
    }
    var merged = (tools.jackson.databind.node.ObjectNode) candidate.deepCopy();
    var mergedObjects = mapper.createArrayNode();
    objects.values().forEach(mergedObjects::add);
    merged.set("objects", mergedObjects);
    return merged;
  }

  private void requireBlueprintCompletenessReview(
      ModelLevel level,
      String request,
      JsonNode current,
      ObligationLedger obligations,
      Blueprint blueprint,
      UsageAudit audit,
      int maxCalls) {
    if (audit.totalCalls() >= maxCalls) throw ProviderCallBudget.exceeded();
    String system =
        "Act as an independent requirements-to-model blueprint critic. Check semantic completeness"
            + " before any model objects are generated. A source list containing multiple distinct"
            + " named actors, concepts, states, risks, rules, behaviors, commands, queries, events,"
            + " or acceptance outcomes normally requires separately identifiable planned evidence;"
            + " attaching a source unit to one generic object is not coverage. Also require useful"
            + " relationships for interactions stated by the source. Every relationship finding"
            + " must cite an exact writable Ecore reference from the supplied contracts and a"
            + " compatible source and target type. If the metamodel has no such reference, do not"
            + " demand or invent the relationship. Blueprint referenceTargets are intentionally"
            + " unlabelled stable target IDs: when the source EClass has a compatible writable"
            + " reference, that target is sufficient planning evidence and the generation phase"
            + " will choose the exact feature. Never demand a feature-name field that is absent"
            + " from the blueprint schema. A concrete subtype is compatible with its abstract"
            + " Ecore target. Never recommend instantiating an abstract or non-creatable EClass;"
            + " choose an exact type from the supplied concrete options. Apply a sound"
            + " level-appropriate abstraction"
            + " boundary: when a requirement enumerates command/query inputs, event payload, or"
            + " captured data, plan compatible InformationItem evidence and a relationship target;"
            + " merely mentioning those fields in an object's purpose is not final model evidence."
            + " When an obligation states that an action causes an outcome, changes or preserves"
            + " state, releases or retains something, or is governed by a pre/postcondition,"
            + " require planned evidence for every distinct outcome and a legal relationship from"
            + " its behavioral carrier. A purpose sentence alone is insufficient. Use the"
            + " obligation's expected EClasses and authoritative metamodel vocabulary; recommend"
            + " explicit Condition/state objects when that is the legal representation."
            + " Cohesive sub-actions such as managing working hours, breaks, blocked times,"
            + " and visit-type limits may remain one clearly described command. Do not demand"
            + " separate objects merely to atomize those fields or duplicate an actor as a domain"
            + " entity. For an evolution, compare against the persisted-element index and reject"
            + " any new temporary-ID semantic duplicate; require reuse of the exact persisted ID."
            + " An exact persisted-ID blueprint record is the required notation for reusing or"
            + " updating that existing object and MUST NOT be treated as a duplicate or creation."
            + " A new child under a persisted owner requires that exact persisted owner as a"
            + " blueprint record with the child in referenceTargets, so generation can write the"
            + " required parent-side containment."
            + " Do not"
            + " invent requirements and"
            + " do not judge EVL semantics. Report every material omission you can identify in this"
            + " single pass, up to the schema limit; never defer known findings to a later review."
            + " Return only the strict review schema.";
    String user =
        "LEVEL:\n"
            + level
            + "\n\nREQUEST AND SOURCE SPECIFICATION:\n"
            + (request == null ? "" : request)
            + "\n\nOBLIGATION LEDGER:\n"
            + obligations.json()
            + "\n\n"
            + "AUTHORITATIVE WRITABLE ECORE CONTRACTS (use these to judge legal relationships):\n"
            + blueprintStructuralGuide(
                level,
                contracts.requiredContainmentClosure(level, new ArrayList<>(blueprint.types())),
                blueprint.types())
            + "\n\nCONCRETE OPTIONS FOR ABSTRACT REQUIRED TARGETS:\n"
            + concreteRequiredOptionsGuide(
                level,
                contracts.requiredContainmentClosure(level, new ArrayList<>(blueprint.types())))
            + "\n\nCURRENT PERSISTED ELEMENT INDEX (reuse exact IDs; do not duplicate):\n"
            + currentElementIndex(current)
            + "\n\nCANDIDATE BLUEPRINT:\n"
            + blueprint.json()
            + "\n\nReturn {acceptable,findings:[{missingConcept,reason,recommendedCorrection}]}."
            + " Set acceptable=true only if the blueprint gives concrete, non-compressed evidence"
            + " for every mandatory obligation and explicit named source concept.";
    var reply = audit.call(system, user, "conceptual_blueprint_review");
    JsonNode review = strictObject(reply.content(), "conceptual blueprint completeness review");
    if (review.path("acceptable").asBoolean(false)) return;
    List<String> findings = new ArrayList<>();
    review
        .path("findings")
        .forEach(
            finding ->
                findings.add(
                    finding.path("missingConcept").asText("missing concept")
                        + ": "
                        + finding.path("reason").asText("")
                        + " Correction: "
                        + finding.path("recommendedCorrection").asText("add explicit evidence")));
    throw new PlatformException(
        422,
        "Independent blueprint completeness review rejected the plan: "
            + (findings.isEmpty() ? "the plan is incomplete" : String.join("; ", findings)));
  }

  private int blueprintCapacity(int maxCalls) {
    // Plan against the safe singleton fallback, not the optimistic two-object slice size. Arvan
    // may length-limit a paired Gemma response, and each split plus blueprint repair consumes an
    // additional call. Keep a proportional reserve for interpretation, planning, repair, and the
    // final review so an accepted blueprint is finishable within the configured turn budget.
    int reserve = Math.max(4, maxCalls / 6);
    return Math.min(MAX_BLUEPRINT_OBJECTS, Math.max(1, maxCalls - reserve));
  }

  private int defaultSliceSize() {
    String model =
        properties.model() == null ? "" : properties.model().toLowerCase(java.util.Locale.ROOT);
    return model.startsWith("deepseek") || model.contains("/deepseek") ? 1 : 2;
  }

  private String containmentIndex(ModelLevel level, Set<String> focusedTypes) {
    return focusedTypes.stream()
        .map(type -> contracts.require(level, type))
        .filter(TypeContract::creatable)
        .map(
            type ->
                type.eClass()
                    + " <- "
                    + contracts.containmentPlacements(level, type.eClass()).stream()
                        .filter(
                            placement -> {
                              int dot = placement.indexOf('.');
                              return dot > 0 && focusedTypes.contains(placement.substring(0, dot));
                            })
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

  private String blueprintStructuralGuide(
      ModelLevel level, List<TypeContract> focusedContracts, Set<String> focusedTypes) {
    return focusedContracts.stream()
        .map(
            type -> {
              String references =
                  type.references().stream()
                      .filter(reference -> !reference.readonly())
                      .filter(
                          reference ->
                              reference.required()
                                  || focusedTypes.contains(reference.targetType())
                                  || focusedTypes.stream()
                                      .anyMatch(
                                          candidate ->
                                              contracts.assignable(
                                                  level, candidate, reference.targetType())))
                      .map(
                          reference ->
                              reference.name()
                                  + "->"
                                  + reference.targetType()
                                  + (reference.containment() ? "[containment]" : "[reference]")
                                  + (reference.required() ? "[required]" : "")
                                  + (reference.many() ? "[many]" : "[one]"))
                      .collect(java.util.stream.Collectors.joining(","));
              return type.eClass() + "|creatable=" + type.creatable() + "|references=" + references;
            })
        .collect(java.util.stream.Collectors.joining("\n"));
  }

  private Set<String> concreteRequiredOptions(
      ModelLevel level, List<TypeContract> focusedContracts) {
    LinkedHashSet<String> result = new LinkedHashSet<>();
    for (TypeContract target : focusedContracts) {
      if (target.creatable()) continue;
      contracts.all(level).stream()
          .filter(TypeContract::creatable)
          .filter(candidate -> contracts.assignable(level, candidate.eClass(), target.eClass()))
          .map(TypeContract::eClass)
          .forEach(result::add);
    }
    for (TypeContract owner : focusedContracts) {
      for (ReferenceContract reference : owner.references()) {
        if (!reference.required() || reference.readonly()) continue;
        TypeContract target = contracts.require(level, reference.targetType());
        if (target.creatable()) continue;
        contracts.all(level).stream()
            .filter(TypeContract::creatable)
            .filter(candidate -> contracts.assignable(level, candidate.eClass(), target.eClass()))
            .map(TypeContract::eClass)
            .forEach(result::add);
      }
    }
    return java.util.Collections.unmodifiableSet(result);
  }

  private String concreteRequiredOptionsGuide(
      ModelLevel level, List<TypeContract> focusedContracts) {
    List<String> lines = new ArrayList<>();
    for (TypeContract target : focusedContracts) {
      if (target.creatable()) continue;
      List<String> options = concreteOptions(level, target.eClass());
      lines.add(
          "ABSTRACT SELECTED TYPE "
              + target.eClass()
              + " cannot be an object type; choose one concrete subtype from "
              + options
              + " for each planned instance.");
    }
    for (TypeContract owner : focusedContracts) {
      for (ReferenceContract reference : owner.references()) {
        if (!reference.required() || reference.readonly()) continue;
        TypeContract target = contracts.require(level, reference.targetType());
        if (target.creatable()) continue;
        List<String> options = concreteOptions(level, target.eClass());
        lines.add(
            owner.eClass()
                + "."
                + reference.name()
                + " requires one or more concrete "
                + target.eClass()
                + "; choose semantically from "
                + options);
      }
    }
    return lines.isEmpty() ? "none" : String.join("\n", lines);
  }

  private List<String> concreteOptions(ModelLevel level, String abstractType) {
    return contracts.all(level).stream()
        .filter(TypeContract::creatable)
        .filter(candidate -> contracts.assignable(level, candidate.eClass(), abstractType))
        .map(TypeContract::eClass)
        .sorted()
        .toList();
  }

  private Blueprint normalizeBlueprintPlacements(
      ModelLevel level, Blueprint blueprint, Map<String, String> persistedTypes) {
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
                  : persistedTypes.getOrDefault(object.ownerInstanceId(), "");
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
              object.obligationIds(),
              object.sourceUnitIds(),
              object.slice()));
    }
    return new Blueprint(blueprint.types(), List.copyOf(normalized), json);
  }

  private List<String> blueprintDiagnostics(
      ModelLevel level,
      Blueprint blueprint,
      ObligationLedger obligations,
      Map<String, String> persistedTypes) {
    List<String> diagnostics = new ArrayList<>();
    Map<String, BlueprintObject> objects = new LinkedHashMap<>();
    blueprint.objects().forEach(object -> objects.put(object.instanceId(), object));
    for (BlueprintObject object : blueprint.objects()) {
      TypeContract objectType = contracts.require(level, object.type());
      boolean persistedRoot = object.type().equals(contracts.rootType(level));
      if (persistedRoot) {
        diagnostics.add(
            object.instanceId()
                + " illegally plans a second "
                + contracts.rootType(level)
                + "; the persisted root is represented only by ownerInstanceId=rootId and must"
                + " never be a blueprint object");
        continue;
      }
      String ownerType;
      if ("rootId".equals(object.ownerInstanceId())) {
        ownerType = contracts.rootType(level);
      } else {
        BlueprintObject owner = objects.get(object.ownerInstanceId());
        ownerType =
            owner == null
                ? persistedTypes.getOrDefault(object.ownerInstanceId(), "")
                : owner.type();
      }
      if (!persistedTypes.containsKey(object.instanceId())
          && persistedTypes.containsKey(object.ownerInstanceId())
          && !objects.containsKey(object.ownerInstanceId())) {
        diagnostics.add(
            object.instanceId()
                + " is a new nested object under persisted owner "
                + object.ownerInstanceId()
                + "; add that owner's exact persisted-ID blueprint record and include "
                + object.instanceId()
                + " in its referenceTargets so generation can write the parent-side "
                + object.containment()
                + " containment");
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
                  .map(
                      id -> {
                        BlueprintObject target = objects.get(id);
                        return target == null ? persistedTypes.get(id) : target.type();
                      })
                  .filter(java.util.Objects::nonNull)
                  .anyMatch(
                      candidateType ->
                          contracts.assignable(level, candidateType, required.targetType()));
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
    Map<String, List<BlueprintObject>> allocated = new LinkedHashMap<>();
    for (BlueprintObject object : blueprint.objects()) {
      for (String obligationId : object.obligationIds()) {
        if (!obligations.byId().containsKey(obligationId)) {
          diagnostics.add(object.instanceId() + " allocated unknown obligation " + obligationId);
        } else {
          allocated.computeIfAbsent(obligationId, ignored -> new ArrayList<>()).add(object);
        }
      }
    }
    for (Obligation obligation : obligations.mandatory()) {
      List<BlueprintObject> evidence = allocated.getOrDefault(obligation.id(), List.of());
      if (evidence.isEmpty()) {
        diagnostics.add(
            "mandatory obligation " + obligation.id() + " is not allocated to an object");
        continue;
      }
      if (evidence.size() < obligation.minimumEvidenceObjects()) {
        diagnostics.add(
            "mandatory obligation "
                + obligation.id()
                + " requires at least "
                + obligation.minimumEvidenceObjects()
                + " distinct evidence objects but is allocated to "
                + evidence.size());
      }
      List<String> missingExpectedTypes =
          obligation.expectedEClasses().stream()
              .filter(
                  expected ->
                      evidence.stream()
                          .noneMatch(
                              object -> contracts.assignable(level, object.type(), expected)))
              .toList();
      if (!missingExpectedTypes.isEmpty()) {
        Map<String, List<String>> compatiblePersistedIds = new LinkedHashMap<>();
        for (String expected : missingExpectedTypes) {
          List<String> ids =
              persistedTypes.entrySet().stream()
                  .filter(entry -> contracts.assignable(level, entry.getValue(), expected))
                  .map(Map.Entry::getKey)
                  .toList();
          if (!ids.isEmpty()) compatiblePersistedIds.put(expected, ids);
        }
        diagnostics.add(
            "mandatory obligation "
                + obligation.id()
                + " is allocated to object types "
                + evidence.stream().map(BlueprintObject::type).toList()
                + " but lacks jointly required types "
                + missingExpectedTypes
                + (compatiblePersistedIds.isEmpty()
                    ? ""
                    : "; compatible persisted IDs are "
                        + compatiblePersistedIds
                        + ". Add an exact-ID blueprint record with the obligation allocation; a"
                        + " reference target alone is not allocated evidence"));
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
    int persistedSliceSize = persistedSliceSize(durableTurnId, blueprint);
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
              + sliceMetamodel(level, slice, blueprint)
              + "\n\nSTABLE INSTANCE BLUEPRINT:\n"
              + compactBlueprint(blueprint)
              + "\n\nCURRENT PERSISTED MODEL:\n"
              + compactSnapshot(current)
              + "\n\nREQUEST AND SOURCE SPECIFICATION:\n"
              + (request == null ? "" : request)
              + "\n\nEXACT SOURCE-UNIT ALLOWLIST FOR SOURCE_GROUNDED EVIDENCE: "
              + sourceUnitIds(request)
              + ". Obligation IDs are not source-unit IDs. When this allowlist is empty, every"
              + " evidence item must use INFERRED with an explicit assumption."
              + "\n\nGENERATE ONLY THESE INSTANCE IDS: "
              + ids
              + ". Return exactly those keys with every required attribute and at most 8 meaningful"
              + " writable attributes per object; do not enumerate absent, blank, or decorative"
              + " optional attributes. Include complete real compositions, references, and"
              + " evidence. Every writable reference marked [required] in the authoritative"
              + " contracts MUST appear under compositions or references with its exact"
              + " associationName and a compatible associatedClassName."
              + " Classify each link from the authoritative contract, not from the wording of the"
              + " request: [containment] links belong ONLY in associations.compositions and"
              + " [reference] links belong ONLY in associations.references. The feature name"
              + " context is not a reason to place a link in references. Never copy a link into"
              + " both arrays, and never invent a writable feature."
              + " It is valid to reference any ID declared in the blueprint even if"
              + " that target belongs to a later slice. Maximum objects in this response: "
              + slice.size()
              + ". The allowed target-ID set is exactly the IDs in the blueprint plus persisted"
              + " IDs shown in CURRENT PERSISTED MODEL; never invent, abbreviate, or rename a"
              + " target ID. Association arrays contain only real links: associationName,"
              + " associatedClassName, and instanceID must each be a non-empty JSON string. Use"
              + " [] when no link exists; never emit a placeholder association whose instanceID"
              + " is an array, object, null, or blank. Return one JSON object and no prose.";
      AssistantModelProvider.AssistantReply reply;
      try {
        reply = audit.call(system, user, "conceptual_instance_slice");
      } catch (RuntimeException failure) {
        if (ProviderCallBudget.isExceeded(failure)) throw failure;
        if (failure instanceof ProviderInvocationFailure) throw failure;
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
      RuntimeException lastSchemaFailure = null;
      for (int correctionAttempt = 0;
          correctionAttempt <= Math.max(1, properties.maxRepairAttempts());
          correctionAttempt++) {
        try {
          mergeSlice(
              level, slice, reply.content(), current, blueprint, generated, sourceUnitIds(request));
          persistObjects(durableTurnId, blueprint, generated);
          lastSchemaFailure = null;
          break;
        } catch (RuntimeException schemaFailure) {
          lastSchemaFailure = schemaFailure;
        }
        if (slice.size() > 1 && hasMalformedSliceObjectShape(reply.content(), slice)) {
          int middle = slice.size() / 2;
          persistSliceSize(durableTurnId, Math.max(1, middle), lastSchemaFailure.getMessage());
          pending.addFirst(List.copyOf(slice.subList(middle, slice.size())));
          pending.addFirst(List.copyOf(slice.subList(0, middle)));
          log.warn(
              "Conceptual slice failed protocol validation; split {} objects into {} and {}",
              slice.size(),
              middle,
              slice.size() - middle);
          lastSchemaFailure = null;
          break;
        }
        if (audit.totalCalls() >= maxCalls - 1
            || correctionAttempt >= Math.max(1, properties.maxRepairAttempts())) {
          throw lastSchemaFailure;
        }
        String combinedDiagnostic =
            safe(lastSchemaFailure.getMessage())
                + requiredAttributeDiagnostic(level, slice, reply.content());
        String correctedUser =
            "AUTHORITATIVE METAMODEL CONTRACTS:\n"
                + sliceMetamodel(level, slice, blueprint)
                + "\n\nEXACT SLICE BLUEPRINT:\n"
                + mapper.valueToTree(slice)
                + "\n\nALLOWED BLUEPRINT TARGET IDS AND TYPES:\n"
                + blueprintTargetIndex(blueprint, current, level)
                + "\n\nREJECTED SLICE JSON:\n"
                + safe(reply.content())
                + "\n\nDETERMINISTIC PROTOCOL DIAGNOSTIC:\n"
                + combinedDiagnostic
                + "\n\nReturn the same exact instance IDs with corrected COMPLETE objects."
                + " This is a local correction: do not repeat the source document or invent a"
                + " missing counterpart. Every association target ID must occur in the allowed"
                + " target index and satisfy the exact Ecore target type. If an optional reference"
                + " has no compatible intended target, omit that optional link. Every object must"
                + " contain attributes as an array and associations as an object with separate"
                + " compositions and references arrays. Place each association in the array"
                + " dictated by its authoritative contract: [containment] means compositions and"
                + " [reference] means references. Do not omit valid content and do not return an"
                + " action envelope or prose.";
        reply = audit.call(system, correctedUser, "conceptual_instance_slice");
      }
    }
    if (generated.size() != blueprint.objects().size()) {
      throw new PlatformException(
          422, "Conceptual slices did not generate every blueprint object.");
    }
  }

  private boolean hasMalformedSliceObjectShape(
      String content, List<BlueprintObject> expectedObjects) {
    JsonNode root;
    try {
      root = strictObject(content, "conceptual slice shape");
    } catch (RuntimeException malformed) {
      return true;
    }
    if (root.path("objects").isArray()) {
      Set<String> ids = new LinkedHashSet<>();
      for (JsonNode item : root.path("objects")) {
        if (!item.isObject()) return true;
        String id = item.path("instanceId").asText(item.path("id").asText("")).trim();
        if (id.isBlank() || !ids.add(id)) return true;
      }
      return expectedObjects.stream().anyMatch(object -> !ids.contains(object.instanceId()));
    }
    for (BlueprintObject expected : expectedObjects) {
      JsonNode value = root.get(expected.instanceId());
      if (value == null) return true;
      if (value.isObject()) continue;
      if (value.isArray() && value.size() == 1 && value.get(0).isObject()) continue;
      return true;
    }
    return false;
  }

  private String sliceMetamodel(
      ModelLevel level, List<BlueprintObject> slice, Blueprint blueprint) {
    Map<String, BlueprintObject> byId = new LinkedHashMap<>();
    blueprint.objects().forEach(object -> byId.put(object.instanceId(), object));
    LinkedHashSet<String> names = new LinkedHashSet<>();
    for (BlueprintObject object : slice) {
      names.add(object.type());
      if (!"rootId".equals(object.ownerInstanceId())
          && byId.containsKey(object.ownerInstanceId())) {
        names.add(byId.get(object.ownerInstanceId()).type());
      }
      object.referenceTargets().stream()
          .map(byId::get)
          .filter(java.util.Objects::nonNull)
          .map(BlueprintObject::type)
          .forEach(names::add);
      blueprint.objects().stream()
          .filter(child -> child.ownerInstanceId().equals(object.instanceId()))
          .map(BlueprintObject::type)
          .forEach(names::add);
    }
    return guides.generateForTypes(
        level, names.stream().map(name -> contracts.require(level, name)).toList());
  }

  private void mergeSlice(
      ModelLevel level,
      List<BlueprintObject> slice,
      String content,
      JsonNode current,
      Blueprint blueprint,
      LinkedHashMap<String, JsonNode> generated,
      Set<String> allowedSourceUnitIds) {
    Map<String, String> expectedTypes =
        slice.stream()
            .collect(
                java.util.stream.Collectors.toMap(
                    BlueprintObject::instanceId,
                    BlueprintObject::type,
                    (left, right) -> left,
                    LinkedHashMap::new));
    ConceptualModel parsed = ConceptualModel.parse(mapper, content, expectedTypes);
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
      normalizeAssociationBuckets(level, object.type(), value);
      completeUnambiguousRequiredAssociations(level, object, value, current, blueprint);
      completeBlueprintSourceEvidence(object, value);
      // Canonicalize redundant target metadata before required-reference checks consume it.
      validateAssociations(level, object, value, current, blueprint);
      validateCompleteObject(object.instanceId(), value);
      validateRequiredAttributes(level, object.instanceId(), object.type(), value);
      validateRequiredReferences(level, object.instanceId(), object.type(), value);
      validateSourceEvidence(object.instanceId(), value, allowedSourceUnitIds);
      if (generated.containsKey(object.instanceId())) {
        throw new PlatformException(
            422, "Duplicate conceptual instance ID across slices: " + object.instanceId());
      }
    }
    for (BlueprintObject object : slice) {
      generated.put(object.instanceId(), parsed.objects.get(object.instanceId()));
    }
  }

  /**
   * Normalizes only the protocol bucket for a link whose exact Ecore feature is unambiguous. Gemma
   * sometimes preserves a relationship but places a containment in {@code references} (or vice
   * versa). Moving that link according to the live Ecore contract is structural decoding, not
   * semantic inference; all IDs, types, names, writability, multiplicity, and ownership remain
   * subject to the validators below and the compiler.
   */
  private void normalizeAssociationBuckets(ModelLevel level, String typeName, JsonNode value) {
    if (!(value instanceof tools.jackson.databind.node.ObjectNode object)) return;
    JsonNode associations = object.path("associations");
    if (!(associations instanceof tools.jackson.databind.node.ObjectNode associationObject)) return;
    if (!associations.path("compositions").isArray()
        || !associations.path("references").isArray()) {
      return;
    }
    var compositions = mapper.createArrayNode();
    var references = mapper.createArrayNode();
    for (JsonNode association : associations.path("compositions")) {
      appendNormalizedAssociation(level, typeName, association, true, compositions, references);
    }
    for (JsonNode association : associations.path("references")) {
      appendNormalizedAssociation(level, typeName, association, false, compositions, references);
    }
    associationObject.set("compositions", compositions);
    associationObject.set("references", references);
  }

  private void appendNormalizedAssociation(
      ModelLevel level,
      String typeName,
      JsonNode association,
      boolean compositionBucket,
      tools.jackson.databind.node.ArrayNode compositions,
      tools.jackson.databind.node.ArrayNode references) {
    String feature = association.path("associationName").asText("").trim();
    var contract =
        contracts.require(level, typeName).references().stream()
            .filter(reference -> reference.name().equals(feature))
            .findFirst()
            .orElse(null);
    // Inverse EReferences are exposed in the generated contract so the model can be explained,
    // but EMF derives them from their writable opposite and the patch compiler must not receive
    // them as mutation commands. Gemma occasionally echoes both sides of a relationship; retain
    // the writable side and discard only this known, structurally impossible inverse.
    if (contract != null && contract.readonly()) return;
    if (contract != null && !contract.readonly() && contract.containment() != compositionBucket) {
      (contract.containment() ? compositions : references).add(association);
    } else {
      (compositionBucket ? compositions : references).add(association);
    }
  }

  /**
   * Materializes only Ecore-required links whose target and feature were already fixed by the
   * LLM-authored blueprint. This is structural decoding of the plan, not a business-content
   * fallback: ambiguous mappings remain rejected and must be repaired by the provider.
   */
  private void completeUnambiguousRequiredAssociations(
      ModelLevel level,
      BlueprintObject source,
      JsonNode value,
      JsonNode current,
      Blueprint blueprint) {
    if (!(value.path("associations")
        instanceof tools.jackson.databind.node.ObjectNode associations)) return;
    if (!associations.path("compositions").isArray() || !associations.path("references").isArray())
      return;

    Map<String, String> knownTypes = new LinkedHashMap<>();
    collectCurrentTypes(current, knownTypes);
    for (BlueprintObject object : blueprint.objects()) {
      knownTypes.put(object.instanceId(), object.type());
    }

    List<ReferenceContract> required =
        contracts.require(level, source.type()).references().stream()
            .filter(ReferenceContract::required)
            .filter(reference -> !reference.readonly())
            .toList();
    for (ReferenceContract reference : required) {
      String bucket = reference.containment() ? "compositions" : "references";
      boolean alreadyPresent = false;
      for (JsonNode association : associations.path(bucket)) {
        if (reference.name().equals(association.path("associationName").asText())) {
          alreadyPresent = true;
          break;
        }
      }
      if (alreadyPresent) continue;

      List<String> candidateIds;
      if (reference.containment()) {
        candidateIds =
            blueprint.objects().stream()
                .filter(candidate -> source.instanceId().equals(candidate.ownerInstanceId()))
                .filter(candidate -> reference.name().equals(candidate.containment()))
                .filter(
                    candidate ->
                        contracts.assignable(level, candidate.type(), reference.targetType()))
                .map(BlueprintObject::instanceId)
                .toList();
      } else {
        List<String> compatibleIds =
            source.referenceTargets().stream()
                .filter(knownTypes::containsKey)
                .filter(
                    id -> contracts.assignable(level, knownTypes.get(id), reference.targetType()))
                .toList();
        candidateIds =
            compatibleIds.size() == 1
                ? compatibleIds
                : compatibleIds.stream()
                    .filter(
                        id ->
                            required.stream()
                                    .filter(candidate -> !candidate.containment())
                                    .filter(
                                        candidate ->
                                            contracts.assignable(
                                                level, knownTypes.get(id), candidate.targetType()))
                                    .count()
                                == 1)
                    .toList();
      }
      if (candidateIds.isEmpty()) continue;
      var bucketArray = (tools.jackson.databind.node.ArrayNode) associations.path(bucket);
      int limit = reference.many() ? candidateIds.size() : 1;
      for (int index = 0; index < limit; index++) {
        String targetId = candidateIds.get(index);
        var association = mapper.createObjectNode();
        association.put("associationName", reference.name());
        association.put("associatedClassName", knownTypes.get(targetId));
        association.put("instanceID", targetId);
        bucketArray.add(association);
      }
    }
  }

  /**
   * Carries the LLM blueprint's explicit source allocation into the generated object's provenance.
   * The blueprint has already passed the closed source-unit allowlist and independent review, so
   * this only prevents a slice response from accidentally dropping that LLM-authored mapping.
   */
  private void completeBlueprintSourceEvidence(BlueprintObject source, JsonNode value) {
    if (source.sourceUnitIds().isEmpty()
        || !(value instanceof tools.jackson.databind.node.ObjectNode object)) return;
    tools.jackson.databind.node.ArrayNode evidence;
    if (object.path("evidence").isArray()) {
      evidence = (tools.jackson.databind.node.ArrayNode) object.path("evidence");
    } else if (!object.has("evidence") || object.path("evidence").isNull()) {
      evidence = mapper.createArrayNode();
      object.set("evidence", evidence);
    } else {
      return;
    }
    Set<String> present = new LinkedHashSet<>();
    for (JsonNode item : evidence) {
      if ("SOURCE_GROUNDED"
          .equals(item.path("kind").asText("").trim().toUpperCase(java.util.Locale.ROOT))) {
        present.add(item.path("sourceUnitId").asText("").trim());
      }
    }
    String requirementId =
        source.obligationIds().isEmpty() ? "" : String.join(",", source.obligationIds());
    for (String sourceUnitId : source.sourceUnitIds()) {
      if (present.contains(sourceUnitId)) continue;
      var item = mapper.createObjectNode();
      item.put("sourceUnitId", sourceUnitId);
      item.put("requirementId", requirementId);
      item.put("kind", "SOURCE_GROUNDED");
      item.put("assumption", "");
      evidence.add(item);
    }
  }

  /**
   * Rejects structurally impossible links while the provider still has the focused slice context.
   * The complete compiler remains authoritative, but deferring these checks until every slice has
   * been generated wastes calls and makes a small object defect expensive to repair.
   */
  private void validateAssociations(
      ModelLevel level,
      BlueprintObject source,
      JsonNode value,
      JsonNode current,
      Blueprint blueprint) {
    Map<String, String> knownTypes = new LinkedHashMap<>();
    collectCurrentTypes(current, knownTypes);
    String currentRootId = current.path("id").asText("");
    String currentRootType = current.path("eClass").asText(contracts.rootType(level));
    knownTypes.put("rootId", currentRootType);
    if (!currentRootId.isBlank()) knownTypes.put(currentRootId, currentRootType);
    Map<String, BlueprintObject> planned = new LinkedHashMap<>();
    for (BlueprintObject object : blueprint.objects()) {
      planned.put(object.instanceId(), object);
      knownTypes.put(object.instanceId(), object.type());
    }

    var contract = contracts.require(level, source.type());
    for (String kind : List.of("compositions", "references")) {
      boolean containment = "compositions".equals(kind);
      Map<String, Integer> occurrences = new LinkedHashMap<>();
      for (JsonNode association : value.path("associations").path(kind)) {
        String feature = association.path("associationName").asText("").trim();
        String targetId = association.path("instanceID").asText("").trim();
        if (feature.isBlank() || targetId.isBlank()) {
          throw new PlatformException(
              422,
              "Association on conceptual object '"
                  + source.instanceId()
                  + "' requires non-empty associationName, associatedClassName, and instanceID.");
        }
        String actualType = knownTypes.get(targetId);
        if (actualType == null) {
          throw new PlatformException(
              422,
              "Association '"
                  + source.instanceId()
                  + "."
                  + feature
                  + "' targets unknown instance ID '"
                  + targetId
                  + "'.");
        }
        // associatedClassName is redundant provider metadata. The live target ID is authoritative;
        // canonicalize an omitted or stale declaration while retaining all exact Ecore feature,
        // target, multiplicity, and ownership checks below.
        if (association instanceof tools.jackson.databind.node.ObjectNode associationObject) {
          associationObject.put("associatedClassName", actualType);
        }
        String declaredType = actualType;
        var reference =
            contract.references().stream()
                .filter(candidate -> candidate.name().equals(feature))
                .findFirst()
                .orElseThrow(
                    () ->
                        new PlatformException(
                            422,
                            "Association '"
                                + source.instanceId()
                                + "."
                                + feature
                                + "' is not a writable Ecore reference on "
                                + source.type()
                                + "."));
        // Inverse EReferences are valid for explanation but are derived by EMF and cannot be
        // written by the assistant. The association normalizer normally removes them; retain
        // this guard here as well for provider responses using a non-canonical bucket shape.
        if (reference.readonly() || reference.containment() != containment) continue;
        if (!declaredType.equals(actualType)) {
          throw new PlatformException(
              422,
              "Association '"
                  + source.instanceId()
                  + "."
                  + feature
                  + "' declares associatedClassName "
                  + declaredType
                  + " but instance '"
                  + targetId
                  + "' is "
                  + actualType
                  + ".");
        }
        if (!contracts.assignable(level, actualType, reference.targetType())) {
          throw new PlatformException(
              422,
              "Association '"
                  + source.instanceId()
                  + "."
                  + feature
                  + "' requires "
                  + reference.targetType()
                  + " but instance '"
                  + targetId
                  + "' is "
                  + actualType
                  + ".");
        }
        int count = occurrences.merge(feature, 1, Integer::sum);
        if (!reference.many() && count > 1) {
          throw new PlatformException(
              422,
              "Single-valued association '"
                  + source.instanceId()
                  + "."
                  + feature
                  + "' was emitted more than once.");
        }
        BlueprintObject target = planned.get(targetId);
        if (containment
            && target != null
            && (!source.instanceId().equals(target.ownerInstanceId())
                || !feature.equals(target.containment()))) {
          throw new PlatformException(
              422,
              "Composition '"
                  + source.instanceId()
                  + "."
                  + feature
                  + "' conflicts with the blueprint ownership of '"
                  + targetId
                  + "'.");
        }
      }
    }
  }

  private void collectCurrentTypes(JsonNode node, Map<String, String> knownTypes) {
    if (node == null || node.isNull() || node.isValueNode()) return;
    if (node.isObject()) {
      String id = node.path("id").asText("").trim();
      String type = node.path("eClass").asText("").trim();
      if (!id.isBlank() && !type.isBlank()) knownTypes.put(id, type);
      node.properties().forEach(entry -> collectCurrentTypes(entry.getValue(), knownTypes));
      return;
    }
    node.forEach(child -> collectCurrentTypes(child, knownTypes));
  }

  private void validateSourceEvidence(
      String instanceId, JsonNode value, Set<String> allowedSourceUnitIds) {
    for (JsonNode evidence : value.path("evidence")) {
      String kind = evidence.path("kind").asText("").trim().toUpperCase(java.util.Locale.ROOT);
      if (!"SOURCE_GROUNDED".equals(kind)) continue;
      String sourceUnitId = evidence.path("sourceUnitId").asText("").trim();
      if (sourceUnitId.isBlank() || !allowedSourceUnitIds.contains(sourceUnitId)) {
        throw new PlatformException(
            422,
            "Conceptual object '"
                + instanceId
                + "' used SOURCE_GROUNDED evidence with unknown sourceUnitId '"
                + sourceUnitId
                + "'. Use only the supplied source-unit allowlist, or use INFERRED with an"
                + " explicit assumption when no source unit supports the statement.");
      }
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

  private void validateRequiredAttributes(
      ModelLevel level, String instanceId, String typeName, JsonNode value) {
    Set<String> present = new LinkedHashSet<>();
    for (JsonNode attribute : value.path("attributes")) {
      if (nonBlankText(attribute.get("attributeName")) && !attribute.path("value").isNull()) {
        present.add(attribute.path("attributeName").asText());
      }
    }
    List<String> missing =
        contracts.require(level, typeName).attributes().stream()
            .filter(AttributeContract::required)
            .map(AttributeContract::name)
            .filter(name -> !"id".equals(name))
            .filter(name -> !present.contains(name))
            .toList();
    if (!missing.isEmpty()) {
      throw new PlatformException(
          422,
          "Conceptual object '"
              + instanceId
              + "' of "
              + typeName
              + " is missing required Ecore attributes "
              + missing
              + ".");
    }
  }

  private void validateRequiredReferences(
      ModelLevel level, String instanceId, String typeName, JsonNode value) {
    JsonNode associations = value.path("associations");
    List<String> missing = new ArrayList<>();
    for (ReferenceContract reference : contracts.require(level, typeName).references()) {
      if (!reference.required() || reference.readonly()) continue;
      boolean present = false;
      for (String kind : List.of("compositions", "references")) {
        for (JsonNode association : associations.path(kind)) {
          if (reference.name().equals(association.path("associationName").asText())
              && contracts.assignable(
                  level,
                  association.path("associatedClassName").asText(),
                  reference.targetType())) {
            present = true;
            break;
          }
        }
        if (present) break;
      }
      if (!present) missing.add(reference.name() + "->" + reference.targetType());
    }
    if (!missing.isEmpty()) {
      throw new PlatformException(
          422,
          "Conceptual object '"
              + instanceId
              + "' of "
              + typeName
              + " is missing required Ecore references "
              + missing
              + ". Add each exact associationName with a compatible target declared in the"
              + " blueprint or persisted model.");
    }
  }

  private String requiredAttributeDiagnostic(
      ModelLevel level, List<BlueprintObject> slice, String content) {
    try {
      Map<String, String> expectedTypes =
          slice.stream()
              .collect(
                  java.util.stream.Collectors.toMap(
                      BlueprintObject::instanceId,
                      BlueprintObject::type,
                      (left, right) -> left,
                      LinkedHashMap::new));
      ConceptualModel parsed = ConceptualModel.parse(mapper, content, expectedTypes);
      List<String> diagnostics = new ArrayList<>();
      for (BlueprintObject object : slice) {
        JsonNode value = parsed.objects.get(object.instanceId());
        if (value == null) continue;
        Set<String> present = new LinkedHashSet<>();
        for (JsonNode attribute : value.path("attributes")) {
          if (nonBlankText(attribute.get("attributeName")) && !attribute.path("value").isNull()) {
            present.add(attribute.path("attributeName").asText());
          }
        }
        List<String> missing =
            contracts.require(level, object.type()).attributes().stream()
                .filter(AttributeContract::required)
                .map(AttributeContract::name)
                .filter(name -> !"id".equals(name))
                .filter(name -> !present.contains(name))
                .toList();
        if (!missing.isEmpty()) {
          diagnostics.add(
              "Conceptual object '"
                  + object.instanceId()
                  + "' of "
                  + object.type()
                  + " is also missing required Ecore attributes "
                  + missing
                  + ".");
        }
      }
      return diagnostics.isEmpty()
          ? ""
          : " Additional diagnostics: " + String.join(" ", diagnostics);
    } catch (RuntimeException ignored) {
      return "";
    }
  }

  private static boolean nonBlankText(JsonNode value) {
    return value != null && value.isTextual() && !value.asText().trim().isBlank();
  }

  private AssistantModelProvider.AssistantReply reviewObligations(
      ModelLevel level,
      String request,
      JsonNode current,
      ObligationLedger obligations,
      Blueprint blueprint,
      LinkedHashMap<String, JsonNode> generated,
      UsageAudit audit,
      int maxCalls) {
    String system =
        "Independently judge whether the staged "
            + level.name()
            + " model satisfies each requirement obligation. Use only exact staged object and"
            + " relationship evidence; names or free text alone do not prove behavior or a"
            + " relationship. Each obligation's minimumEvidenceObjects is the minimum number of"
            + " distinct staged instances required; a scalar containing several names is still only"
            + " one instance. Each obligation's expectedEClasses are the minimum jointly required"
            + " mappings selected during interpretation, so require concrete evidence for every"
            + " listed EClass. Judge the obligation text against the concrete evidence. Do not"
            + " accept partial conjunctive coverage: every stated precondition, action, outcome,"
            + " duration, preservation clause, and actor capability must be explicit in cited"
            + " attributes or relationships. An event or command covering only one clause is"
            + " PARTIAL. For an evolution, reject a new object that semantically duplicates a"
            + " matching persisted element instead of reusing its exact ID. Do not invent stronger"
            + " requirements or unrequested supporting concepts. Return one compact JSON verdict,"
            + " no reasoning or model correction. Mark acceptable=true only when every MANDATORY"
            + " obligation is SATISFIED.";
    String baseUser =
        "OBLIGATION LEDGER (authoritative requirements extracted from the request):\n"
            + obligations.json()
            + "\n\nPLANNED OBLIGATION ALLOCATIONS:\n"
            + compactObligationAllocations(blueprint)
            + "\n\nCURRENT PERSISTED ELEMENT INDEX:\n"
            + currentElementIndex(current)
            + "\n\nACTUAL STAGED MODEL EVIDENCE:\n"
            + compactGenerated(generated)
            + "\n\nReturn {acceptable,coverage:[{obligationId,state,evidenceObjectIds,"
            + "evidenceRelationships:[{sourceId,feature,targetId}],explanation}],findings}.";
    AssistantModelProvider.AssistantReply reply = null;
    RuntimeException lastFailure = null;
    int invalidReplies = 0;
    int semanticCorrections = 0;
    while (audit.totalCalls() < maxCalls && invalidReplies < 2) {
      if (audit.totalCalls() >= maxCalls) break;
      try {
        reply =
            audit.call(
                system,
                baseUser
                    + (invalidReplies == 0
                        ? ""
                        : "\n\nThe prior response was rejected: "
                            + safe(
                                lastFailure == null ? "invalid response" : lastFailure.getMessage())
                            + " Return only the tiny terminal verdict, cite only relationships"
                            + " literally present in ACTUAL STAGED MODEL EVIDENCE, and keep each"
                            + " explanation under 12 words."),
                "conceptual_obligation_review");
        requireObligationReview(reply.content(), obligations, generated);
        persistObligationReview(DurableTurnExecutionContext.turnId(), reply.content());
        return reply;
      } catch (RuntimeException failure) {
        if (ProviderCallBudget.isExceeded(failure)) throw failure;
        if (failure instanceof ProviderInvocationFailure) throw failure;
        if (safe(failure.getMessage())
            .startsWith("Mandatory requirement obligations were not satisfied:")) {
          if (semanticCorrections >= Math.max(1, properties.maxRepairAttempts())
              || audit.totalCalls() >= maxCalls) throw failure;
          reply =
              correctObligationFailure(
                  level,
                  request,
                  current,
                  obligations,
                  blueprint,
                  generated,
                  reply.content(),
                  audit,
                  maxCalls);
          persistObjects(DurableTurnExecutionContext.turnId(), blueprint, generated);
          semanticCorrections++;
          invalidReplies = 0;
          continue;
        }
        lastFailure = failure;
        invalidReplies++;
      }
    }
    throw lastFailure == null
        ? new PlatformException(429, "No provider-call budget remains for obligation review.")
        : lastFailure;
  }

  private AssistantModelProvider.AssistantReply correctObligationFailure(
      ModelLevel level,
      String request,
      JsonNode current,
      ObligationLedger obligations,
      Blueprint blueprint,
      LinkedHashMap<String, JsonNode> generated,
      String rejectedReview,
      UsageAudit audit,
      int maxCalls) {
    JsonNode verdict = strictObject(rejectedReview, "rejected conceptual obligation review");
    LinkedHashSet<String> failedObligationIds = new LinkedHashSet<>();
    LinkedHashSet<String> affectedIds = new LinkedHashSet<>();
    for (JsonNode coverage : verdict.path("coverage")) {
      String obligationId = coverage.path("obligationId").asText();
      Obligation obligation = obligations.byId().get(obligationId);
      int evidenceCount =
          new LinkedHashSet<>(Blueprint.strings(coverage.path("evidenceObjectIds"))).size();
      if (!"SATISFIED".equals(coverage.path("state").asText())
          || obligation == null
          || evidenceCount < obligation.minimumEvidenceObjects()) {
        failedObligationIds.add(obligationId);
        affectedIds.addAll(Blueprint.strings(coverage.path("evidenceObjectIds")));
      }
    }
    String verdictText = verdict.toString();
    blueprint.objects().stream()
        .filter(
            object ->
                object.obligationIds().stream().anyMatch(failedObligationIds::contains)
                    || verdictText.contains(object.instanceId()))
        .map(BlueprintObject::instanceId)
        .forEach(affectedIds::add);
    List<String> direct = List.copyOf(affectedIds);
    for (String id : direct) {
      blueprint.objects().stream()
          .filter(object -> object.instanceId().equals(id))
          .findFirst()
          .ifPresent(
              object -> {
                if (!"rootId".equals(object.ownerInstanceId())) {
                  affectedIds.add(object.ownerInstanceId());
                }
                affectedIds.addAll(object.referenceTargets());
              });
      blueprint.objects().stream()
          .filter(object -> object.referenceTargets().contains(id))
          .map(BlueprintObject::instanceId)
          .forEach(affectedIds::add);
    }
    List<String> correctionIds =
        affectedIds.stream().filter(generated::containsKey).limit(6).toList();
    if (correctionIds.isEmpty()) {
      throw new PlatformException(
          422, "Obligation review rejected the model without repairable staged evidence IDs.");
    }
    var rejected = JsonNodeFactory.instance.objectNode();
    correctionIds.forEach(id -> rejected.set(id, generated.get(id).deepCopy()));
    List<BlueprintObject> affectedObjects =
        blueprint.objects().stream()
            .filter(object -> correctionIds.contains(object.instanceId()))
            .toList();
    String system =
        "Repair staged conceptual objects rejected by an independent requirement-obligation"
            + " review. Return {acceptable:false,findings:[...],corrections:{...}} with complete"
            + " replacement objects for ONLY these existing IDs: "
            + correctionIds
            + ". Make every failed clause explicit in legal attributes and relationships, while"
            + " preserving valid semantics and exact EClasses. Do not add, delete, rename, or echo"
            + " any other object. Every relationship target must be an exact ID from the allowed"
            + " target index. Do not return prose.";
    String user =
        "REQUEST:\n"
            + request
            + "\n\nFAILED OBLIGATIONS:\n"
            + obligations.json()
            + "\n\nREJECTED REVIEW VERDICT:\n"
            + verdict
            + "\n\nCURRENT PERSISTED ELEMENT INDEX:\n"
            + currentElementIndex(current)
            + "\n\nFOCUSED METAMODEL:\n"
            + sliceMetamodel(level, affectedObjects, blueprint)
            + "\n\nALLOWED TARGET IDS AND TYPES:\n"
            + blueprintTargetIndex(blueprint, current, level)
            + "\n\nONLY REPAIRABLE STAGED OBJECTS:\n"
            + rejected;
    AssistantModelProvider.AssistantReply reply = audit.call(system, user, "conceptual_correction");
    applyReview(reply.content(), blueprint, generated, correctionIds.size());
    int structuralCorrections = 0;
    while (true) {
      try {
        new ConceptualModel(generated).commands(current, contracts, level);
        break;
      } catch (RuntimeException structuralFailure) {
        if (ProviderCallBudget.isExceeded(structuralFailure)
            || audit.totalCalls() >= maxCalls
            || structuralCorrections >= Math.max(1, properties.maxRepairAttempts())) {
          throw structuralFailure;
        }
        reply =
            correctCompilerFailure(
                level,
                request,
                current,
                sliceMetamodel(level, affectedObjects, blueprint),
                blueprint,
                generated,
                structuralFailure.getMessage(),
                Set.of(),
                audit,
                maxCalls);
        structuralCorrections++;
      }
    }
    return reply;
  }

  private void requireObligationReview(
      String content, ObligationLedger obligations, LinkedHashMap<String, JsonNode> generated) {
    JsonNode verdict = strictObject(content, "conceptual obligation review");
    if (!verdict.path("coverage").isArray() || !verdict.path("findings").isArray()) {
      throw new PlatformException(422, "Obligation review requires coverage and findings arrays.");
    }
    Map<String, JsonNode> coverage = new LinkedHashMap<>();
    for (JsonNode item : verdict.path("coverage")) {
      String id = item.path("obligationId").asText("").trim();
      if (!obligations.byId().containsKey(id) || coverage.putIfAbsent(id, item) != null) {
        throw new PlatformException(422, "Obligation review used an unknown or duplicate ID.");
      }
      if (!item.path("evidenceObjectIds").isArray()
          || !item.path("evidenceRelationships").isArray()) {
        throw new PlatformException(422, "Obligation review evidence must use arrays.");
      }
      for (JsonNode objectId : item.path("evidenceObjectIds")) {
        if (!objectId.isTextual() || !generated.containsKey(objectId.asText())) {
          throw new PlatformException(422, "Obligation review cited an unknown staged object.");
        }
      }
      for (JsonNode relationship : item.path("evidenceRelationships")) {
        String sourceId = relationship.path("sourceId").asText("");
        String feature = relationship.path("feature").asText("");
        String targetId = relationship.path("targetId").asText("");
        if (!actualRelationship(generated, sourceId, feature, targetId)) {
          throw new PlatformException(
              422,
              "Obligation review cited a nonexistent staged relationship "
                  + sourceId
                  + "."
                  + feature
                  + "->"
                  + targetId
                  + ".");
        }
      }
    }
    List<String> missing = new ArrayList<>();
    for (Obligation obligation : obligations.mandatory()) {
      JsonNode item = coverage.get(obligation.id());
      if (item == null
          || !"SATISFIED".equals(item.path("state").asText())
          || new LinkedHashSet<>(Blueprint.strings(item.path("evidenceObjectIds"))).size()
              < obligation.minimumEvidenceObjects()) {
        missing.add(obligation.id());
      }
    }
    if (!verdict.path("acceptable").asBoolean(false) || !missing.isEmpty()) {
      throw new PlatformException(
          422,
          "Mandatory requirement obligations were not satisfied: "
              + missing
              + "; findings="
              + verdict.path("findings"));
    }
  }

  private boolean actualRelationship(
      LinkedHashMap<String, JsonNode> generated, String sourceId, String feature, String targetId) {
    JsonNode source = generated.get(sourceId);
    // Persisted targets are legal evidence in an evolution turn. The staged relationship itself
    // has already passed compilation against the union of persisted and generated instances, so
    // evidence validation only needs to prove that the cited edge literally exists on its staged
    // source. Requiring the target to be newly generated incorrectly rejects reuse.
    if (source == null) return false;
    for (String kind : List.of("compositions", "references")) {
      for (JsonNode relationship : source.path("associations").path(kind)) {
        if (feature.equals(relationship.path("associationName").asText())
            && targetId.equals(relationship.path("instanceID").asText())) return true;
      }
    }
    return false;
  }

  private AssistantModelProvider.AssistantReply review(
      ModelLevel level,
      String request,
      JsonNode current,
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
            + " every recommended correction must be feasible by replacing only the finding's"
            + " existing blueprint objectIds. Never request new IDs, new objects, new EClasses,"
            + " or a different blueprint; express absent concepts through the most relevant"
            + " planned objects and legal relationships."
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
          reply, level, request, current, metamodel, blueprint, generated, audit, maxCalls);
    } catch (RuntimeException failure) {
      if (ProviderCallBudget.isExceeded(failure)) throw failure;
      if (failure instanceof ProviderInvocationFailure) throw failure;
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
              + ". Return a much smaller terminal JSON object with only acceptable and at most"
              + " one short finding. A rejecting finding must include objectIds using exact IDs"
              + " from PLANNED OBJECT PURPOSES, problem, and recommendedCorrection.";
      reply = audit.call(system, reducedUser, "conceptual_review");
      return finishQualityReview(
          reply, level, request, current, metamodel, blueprint, generated, audit, maxCalls);
    }
  }

  private AssistantModelProvider.AssistantReply correctCompilerFailure(
      ModelLevel level,
      String request,
      JsonNode current,
      String metamodel,
      Blueprint blueprint,
      LinkedHashMap<String, JsonNode> generated,
      String diagnostic,
      Set<String> preferredAffectedIds,
      UsageAudit audit,
      int maxCalls) {
    Map<String, BlueprintObject> plannedById = new LinkedHashMap<>();
    blueprint.objects().forEach(object -> plannedById.put(object.instanceId(), object));
    LinkedHashSet<String> affectedIds =
        preferredAffectedIds == null
            ? new LinkedHashSet<>()
            : preferredAffectedIds.stream()
                .filter(plannedById::containsKey)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
    List<String> associationSourceIds =
        blueprint.objects().stream()
            .filter(
                object ->
                    diagnostic != null
                        && diagnostic.contains("Association " + object.instanceId() + "."))
            .map(BlueprintObject::instanceId)
            .toList();
    affectedIds.addAll(associationSourceIds);
    if (associationSourceIds.isEmpty()) {
      affectedIds.addAll(
          blueprint.objects().stream()
              .filter(
                  object ->
                      diagnostic != null
                          && (diagnostic.contains("'" + object.instanceId() + "'")
                              || diagnostic.contains(object.instanceId() + ".")))
              .map(BlueprintObject::instanceId)
              .toList());
    }
    if (affectedIds.isEmpty() && diagnostic != null) {
      blueprint.objects().stream()
          .filter(object -> diagnostic.contains(object.type() + "."))
          .map(BlueprintObject::instanceId)
          .limit(1)
          .forEach(affectedIds::add);
    }
    if (affectedIds.isEmpty()) affectedIds.add(blueprint.objects().get(0).instanceId());
    List<String> directFailures = List.copyOf(affectedIds);
    for (String id : directFailures) {
      BlueprintObject object = plannedById.get(id);
      if (object != null && !"rootId".equals(object.ownerInstanceId())) {
        affectedIds.add(object.ownerInstanceId());
      }
      blueprint.objects().stream()
          .filter(candidate -> candidate.referenceTargets().contains(id))
          .map(BlueprintObject::instanceId)
          .forEach(affectedIds::add);
    }
    // Owners and relationship neighbours may be persisted reuse targets rather than staged
    // objects. They provide localization context but cannot be replaced by this turn's bounded
    // correction payload.
    List<String> correctionIds =
        affectedIds.stream().filter(generated::containsKey).limit(4).toList();
    if (correctionIds.isEmpty()) {
      throw new PlatformException(
          422, "Compiler rejected the model without a repairable staged source object.");
    }
    var rejected = JsonNodeFactory.instance.objectNode();
    correctionIds.forEach(id -> rejected.set(id, generated.get(id).deepCopy()));
    List<BlueprintObject> affectedObjects =
        blueprint.objects().stream()
            .filter(object -> correctionIds.contains(object.instanceId()))
            .toList();
    String focusedMetamodel = sliceMetamodel(level, affectedObjects, blueprint);
    String system =
        "Correct a bounded conceptual model rejected by Varka's deterministic Ecore compiler."
            + " Return {acceptable:false,findings:[...],corrections:{...}}. Corrections must"
            + " contain complete replacement objects for ONLY these affected IDs: "
            + correctionIds
            + ". Preserve all valid semantics. Do not echo other objects, add/delete IDs, or"
            + " return prose. For a missing incoming containment, correct the planned owner by"
            + " adding a composition association on the parent that targets the child; changing"
            + " the child alone cannot repair it.";
    String user =
        "REQUEST:\n"
            + request
            + "\n\nMETAMODEL:\n"
            + focusedMetamodel
            + "\n\nBLUEPRINT:\n"
            + compactBlueprint(blueprint)
            + "\n\nALLOWED TARGET IDS AND TYPES:\n"
            + blueprintTargetIndex(blueprint, current, level)
            + "\n\nONLY AFFECTED REJECTED OBJECTS:\n"
            + rejected
            + "\n\nCOMPILER DIAGNOSTIC:\n"
            + safe(diagnostic);
    AssistantModelProvider.AssistantReply reply;
    try {
      reply = audit.call(system, user, "conceptual_correction");
      applyReview(reply.content(), blueprint, generated, correctionIds.size());
    } catch (RuntimeException firstFailure) {
      if (ProviderCallBudget.isExceeded(firstFailure)) throw firstFailure;
      if (audit.totalCalls() >= maxCalls) throw firstFailure;
      String retryUser =
          user
              + "\n\nThe prior correction was rejected or truncated: "
              + safe(firstFailure.getMessage())
              + " Return a tiny terminal JSON object correcting ONLY "
              + correctionIds
              + ". findings must contain at most one short item.";
      reply = audit.call(system, retryUser, "conceptual_correction");
      applyReview(reply.content(), blueprint, generated, correctionIds.size());
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
      Map<String, String> expectedTypes =
          planned.entrySet().stream()
              .collect(
                  java.util.stream.Collectors.toMap(
                      Map.Entry::getKey,
                      entry -> entry.getValue().type(),
                      (left, right) -> left,
                      LinkedHashMap::new));
      ConceptualModel parsed = ConceptualModel.parse(mapper, corrections.toString(), expectedTypes);
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
      if (!object.obligationIds().isEmpty()) {
        item.set("obligationIds", mapper.valueToTree(object.obligationIds()));
      }
      if (!object.referenceTargets().isEmpty()) {
        item.set("referenceTargets", mapper.valueToTree(object.referenceTargets()));
      }
      if (!object.sourceUnitIds().isEmpty()) {
        item.set("sourceUnitIds", mapper.valueToTree(object.sourceUnitIds()));
      }
    }
    return result;
  }

  private JsonNode compactObligationAllocations(Blueprint blueprint) {
    var result = JsonNodeFactory.instance.arrayNode();
    for (BlueprintObject object : blueprint.objects()) {
      if (object.obligationIds().isEmpty()) continue;
      var item = result.addObject();
      item.put("instanceId", object.instanceId());
      item.put("type", object.type());
      item.set("obligationIds", mapper.valueToTree(object.obligationIds()));
    }
    return result;
  }

  private JsonNode blueprintTargetIndex(Blueprint blueprint, JsonNode current, ModelLevel level) {
    var result = JsonNodeFactory.instance.objectNode();
    result.put("rootId", contracts.rootType(level));
    Map<String, String> persisted = new LinkedHashMap<>();
    collectCurrentTypes(current, persisted);
    persisted.forEach(result::put);
    for (BlueprintObject object : blueprint.objects()) {
      result.put(object.instanceId(), object.type());
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
      JsonNode current,
      String metamodel,
      Blueprint blueprint,
      LinkedHashMap<String, JsonNode> generated,
      UsageAudit audit,
      int maxCalls) {
    JsonNode review = requireReview(reply.content(), blueprint);
    if (review.path("acceptable").asBoolean(false)) return reply;
    if (audit.totalCalls() >= maxCalls) {
      throw new PlatformException(
          422, "Conceptual quality review rejected the staged model: " + review.path("findings"));
    }
    return correctCompilerFailure(
        level,
        request,
        current,
        metamodel,
        blueprint,
        generated,
        "QUALITY REVIEW FINDINGS: " + review.path("findings"),
        reviewFindingObjectIds(review),
        audit,
        maxCalls);
  }

  private Set<String> reviewFindingObjectIds(JsonNode review) {
    LinkedHashSet<String> result = new LinkedHashSet<>();
    for (JsonNode finding : review.path("findings")) {
      for (JsonNode id : finding.path("objectIds")) {
        if (id.isTextual() && !id.asText().isBlank()) result.add(id.asText());
      }
    }
    return result;
  }

  private JsonNode requireReview(String content, Blueprint blueprint) {
    JsonNode review = strictObject(content, "conceptual review");
    if (!review.has("acceptable") || !review.path("findings").isArray()) {
      throw new PlatformException(422, "Conceptual review must contain acceptable and findings.");
    }
    Set<String> plannedIds =
        blueprint.objects().stream()
            .map(BlueprintObject::instanceId)
            .collect(java.util.stream.Collectors.toSet());
    for (JsonNode finding : review.path("findings")) {
      if (!finding.path("objectIds").isArray()
          || finding.path("objectIds").isEmpty()
          || !nonBlankText(finding.get("problem"))
          || !nonBlankText(finding.get("recommendedCorrection"))) {
        throw new PlatformException(
            422,
            "Each conceptual review finding must name exact objectIds, problem, and"
                + " recommendedCorrection.");
      }
      for (JsonNode id : finding.path("objectIds")) {
        if (!id.isTextual() || !plannedIds.contains(id.asText())) {
          throw new PlatformException(
              422, "Conceptual review finding used an undeclared object ID.");
        }
      }
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

  private ObligationLedger restoredObligations(String turnId, ModelLevel level) {
    if (turns == null || turnId == null || turnId.isBlank()) return null;
    return turns
        .workflow(turnId)
        .filter(workflow -> "CONCEPTUAL_GENERATION".equals(workflow.workflowKind()))
        .map(AssistantTurnStore.Workflow::plan)
        .map(plan -> plan.path("obligationLedger"))
        .filter(JsonNode::isObject)
        .map(node -> ObligationLedger.parse(node, contracts, level, Set.of()))
        .orElse(null);
  }

  private void persistObligations(String turnId, ObligationLedger obligations) {
    if (turns == null || turnId == null || turnId.isBlank()) return;
    var plan = durablePlan(turnId);
    plan.set("obligationLedger", obligations.json().deepCopy());
    turns.saveWorkflow(
        new AssistantTurnStore.Workflow(
            turnId, "CONCEPTUAL_GENERATION", "TYPE_SELECTION", null, plan));
  }

  private void persistObligationReview(String turnId, String content) {
    if (turns == null || turnId == null || turnId.isBlank()) return;
    var plan = durablePlan(turnId);
    plan.set("obligationReview", strictObject(content, "conceptual obligation review").deepCopy());
    turns.saveWorkflow(
        new AssistantTurnStore.Workflow(turnId, "CONCEPTUAL_GENERATION", "COMPILING", null, plan));
    turns.setSourceCoverage(turnId, 100, "");
  }

  private Blueprint restoredBlueprint(String turnId, ModelLevel level, JsonNode current) {
    if (turns == null || turnId == null || turnId.isBlank()) return null;
    return turns
        .workflow(turnId)
        .filter(workflow -> "CONCEPTUAL_GENERATION".equals(workflow.workflowKind()))
        .map(AssistantTurnStore.Workflow::plan)
        .filter(plan -> plan != null && plan.path("blueprint").isObject())
        .map(
            plan ->
                Blueprint.parse(
                    mapper,
                    plan.path("blueprint").toString(),
                    contracts,
                    level,
                    currentElementTypes(current).keySet()))
        .orElse(null);
  }

  private Set<String> restoredSelectedTypes(String turnId, ModelLevel level) {
    if (turns == null || turnId == null || turnId.isBlank()) return Set.of();
    JsonNode selected =
        turns
            .workflow(turnId)
            .filter(workflow -> "CONCEPTUAL_GENERATION".equals(workflow.workflowKind()))
            .map(AssistantTurnStore.Workflow::plan)
            .map(plan -> plan.path("selectedTypes"))
            .orElse(null);
    if (selected == null || !selected.isArray() || selected.isEmpty()) return Set.of();
    LinkedHashSet<String> result = new LinkedHashSet<>();
    selected.forEach(type -> result.add(contracts.require(level, type.asText("").trim()).eClass()));
    return java.util.Collections.unmodifiableSet(result);
  }

  private void persistSelectedTypes(String turnId, Set<String> selectedTypes) {
    if (turns == null || turnId == null || turnId.isBlank()) return;
    var plan = durablePlan(turnId);
    var selected = plan.putArray("selectedTypes");
    selectedTypes.forEach(selected::add);
    turns.saveWorkflow(
        new AssistantTurnStore.Workflow(
            turnId, "CONCEPTUAL_GENERATION", "BLUEPRINTING", null, plan));
  }

  private void persistBlueprint(String turnId, Blueprint blueprint) {
    if (turns == null || turnId == null || turnId.isBlank()) return;
    var plan = durablePlan(turnId);
    plan.set("blueprint", blueprint.json().deepCopy());
    if (!plan.has("sliceSize")) plan.put("sliceSize", initialSliceSize(blueprint));
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

  private int persistedSliceSize(String turnId, Blueprint blueprint) {
    int initial = initialSliceSize(blueprint);
    if (turns == null || turnId == null || turnId.isBlank()) return initial;
    return turns
        .workflow(turnId)
        .map(AssistantTurnStore.Workflow::plan)
        .map(plan -> plan.path("sliceSize").asInt(initial))
        .map(size -> Math.max(1, Math.min(2, size)))
        .orElse(initial);
  }

  private int initialSliceSize(Blueprint blueprint) {
    int normal = defaultSliceSize();
    // Large DeepSeek blueprints would otherwise consume the entire turn budget on one-object
    // calls. Start with two related objects and retain the existing durable split-to-one recovery
    // if Arvan length-limits the response.
    return normal == 1 && blueprint.objects().size() > 8 ? 2 : normal;
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
        + "Use the paper IR exactly: each value has type, attributes:[{attributeName,value}]"
        + " (dataType is optional because Ecore is authoritative), and"
        + " associations:{compositions:[{associationName,associatedClassName,instanceID}],references:[...]}."
        + " Associations whose authoritative contract says read-only are derived inverse metadata"
        + " for explanation only and MUST be omitted from both arrays; emit only writable Ecore"
        + " associations. A composition is written on the PARENT object and instanceID identifies"
        + " its CHILD. A reference is written on its source object and instanceID identifies its"
        + " target. Do not reverse either edge. The existing model root is implicit and must not be"
        + " generated. When a new object has no incoming composition, the compiler may place it"
        + " under the root only if Ecore admits one unambiguous root containment.\n"
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

  private Set<String> selectMetamodelTypes(
      ModelLevel level,
      String request,
      JsonNode current,
      ObligationLedger obligations,
      UsageAudit audit,
      int maxCalls) {
    int capacity = blueprintCapacity(maxCalls);
    String system =
        "Select the exact EClasses needed to model the request as a conceptual instance model. This"
            + " is a semantic modeling decision: choose all object, relationship, policy, contract,"
            + " and supporting types needed for meaningful nodes and edges. Internally account for"
            + " every explicit user/source requirement before answering: do not omit requested"
            + " functional concepts merely to make room for generic cross-cutting qualities. Choose"
            + " every EClass in each mandatory obligation's expectedEClasses required set. Reuse"
            + " one type when it genuinely represents multiple obligations. Do not add unrelated"
            + " alternatives merely for completeness; required structural support is added from"
            + " Ecore closure after your semantic choice. "
            + " between 1 and "
            + capacity
            + " focused types; choose fewer only when required to fit the structural budget. Never"
            + " copy the full type index. Prefer a"
            + " coherent minimal vocabulary over unrelated alternatives. Use only case-sensitive"
            + " names from the supplied authoritative type index. The complete required Ecore"
            + " closure must fit within "
            + capacity
            + " non-root object types; use the supplied structural costs"
            + " to avoid combining several expensive types. Do not generate model content or"
            + " explain. Return {\"types\":[...]}.";
    String baseUser =
        guides.index(level)
            + "\n\nMINIMUM ECORE CLOSURE COST PER SELECTED TYPE (non-root object types):\n"
            + structuralSelectionCosts(level)
            + "\n\nCURRENT MODEL TYPES (existing IDs will be supplied to the generation pass):\n"
            + currentTypes(current)
            + "\n\nAUTHORITATIVE LLM REQUIREMENT OBLIGATION LEDGER:\n"
            + obligations.json()
            + "\n\nREQUEST AND SOURCE SPECIFICATION:\n"
            + (request == null ? "" : request);
    String correction = "";
    for (int attempt = 0; attempt < 5; attempt++) {
      AssistantModelProvider.AssistantReply reply;
      try {
        boolean compactRetry = attempt > 0 && !obligations.obligations().isEmpty();
        reply =
            audit.call(
                compactRetry
                    ? "Choose a coherent exact-EClass set from the LLM-authored obligation ledger"
                        + " below. Each mandatory expectedEClasses list is the LLM-authored"
                        + " jointly required set, not alternatives. Include every required type,"
                        + " reusing a type across obligations when appropriate; do not add types"
                        + " outside those sets merely for completeness. Required Ecore support"
                        + " types are added later. Cover every mandatory obligation while fitting"
                        + " the exact combined closure capacity. Return only {\"types\":[...]}."
                    : system,
                compactRetry
                    ? compactTypeSelectionRetry(level, request, obligations, capacity, correction)
                    : baseUser + correction,
                "conceptual_type_selection");
      } catch (RuntimeException failure) {
        if (ProviderCallBudget.isExceeded(failure)) throw failure;
        if (attempt == 4 || !truncated(failure)) throw failure;
        correction =
            correction
                + "\nThe prior type-selection response was truncated. Return only the compact"
                + " JSON selection; no prose or analysis.";
        continue;
      }
      try {
        JsonNode selected = strictObject(reply.content(), "conceptual type selection");
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
        List<String> missingMandatoryMappings = new ArrayList<>();
        for (Obligation obligation : obligations.mandatory()) {
          List<String> missing =
              obligation.expectedEClasses().stream()
                  .filter(
                      expected ->
                          names.stream()
                              .noneMatch(
                                  selectedType ->
                                      contracts.assignable(level, selectedType, expected)))
                  .toList();
          if (!missing.isEmpty()) {
            missingMandatoryMappings.add(obligation.id() + "=" + missing);
          }
        }
        if (!missingMandatoryMappings.isEmpty()) {
          throw new PlatformException(
              422,
              "Type selection "
                  + names
                  + " omitted mandatory obligation EClasses "
                  + missingMandatoryMappings
                  + ". Add every listed missing type and"
                  + " remove lower-priority selected types as needed to remain within capacity;"
                  + " the ledger contains jointly required types, not alternatives.");
        }
        if (names.size() > capacity) {
          throw new PlatformException(
              422,
              "Conceptual type selection contains "
                  + names.size()
                  + " types; the strict maximum is "
                  + capacity
                  + ".");
        }
        List<TypeContract> closure =
            contracts.requiredContainmentClosure(level, new ArrayList<>(names));
        long closureObjects =
            closure.stream()
                .filter(type -> !contracts.rootType(level).equals(type.eClass()))
                .count();
        if (closureObjects > capacity) {
          String costs =
              names.stream()
                  .map(name -> name + "=" + minimumSelectionCost(level, name))
                  .collect(java.util.stream.Collectors.joining(", "));
          String marginalSavings =
              names.stream()
                  .map(
                      name -> {
                        List<String> remaining =
                            names.stream().filter(candidate -> !candidate.equals(name)).toList();
                        long reducedClosure =
                            contracts.requiredContainmentClosure(level, remaining).stream()
                                .filter(type -> !contracts.rootType(level).equals(type.eClass()))
                                .count();
                        return name + "=" + Math.max(0, closureObjects - reducedClosure);
                      })
                  .collect(java.util.stream.Collectors.joining(", "));
          long excess = closureObjects - capacity;
          throw new PlatformException(
              422,
              "Selected EClasses "
                  + names
                  + " have minimum structural costs {"
                  + costs
                  + "}; their combined closure contains "
                  + closureObjects
                  + " non-root object types, exceeding the "
                  + capacity
                  + "-object atomic capacity. Return the smallest importance-ranked selection from"
                  + " the closed obligation-required vocabulary. Preserve every required type and"
                  + " remove only unrelated optional additions. The exact marginal closure savings"
                  + " from removing each one are {"
                  + marginalSavings
                  + "}; remove types whose marginal savings cover at least the "
                  + excess
                  + "-object excess in this correction, while preserving as many explicit"
                  + " requirements as possible. The combined required closure must fit within "
                  + capacity
                  + ". Fewer than 4 types is valid when necessary;"
                  + " do not introduce types outside the obligation-required vocabulary.");
        }
        log.info(
            "Conceptual Ecore contract selection level={} requestedTypes={} closureTypes={}",
            level,
            names,
            closure.stream().map(TypeContract::eClass).toList());
        return java.util.Collections.unmodifiableSet(names);
      } catch (Exception failure) {
        if (attempt == 4) {
          if (failure instanceof RuntimeException runtime) throw runtime;
          throw new PlatformException(
              422, "LLM did not return parseable conceptual type selection JSON.");
        }
        correction =
            "\n\nThe prior selection was rejected: "
                + safe(failure.getMessage())
                + " Return a corrected focused selection of at most "
                + capacity
                + " exact EClass names. Cover every mandatory obligation using all of its jointly"
                + " required EClasses, rebalance other selections when necessary, and"
                + " do not copy the full index.";
      }
    }
    throw new PlatformException(422, "Conceptual type selection failed.");
  }

  private String compactTypeSelectionRetry(
      ModelLevel level,
      String request,
      ObligationLedger obligations,
      int capacity,
      String priorDiagnostic) {
    LinkedHashSet<String> candidates = new LinkedHashSet<>();
    obligations
        .obligations()
        .forEach(obligation -> candidates.addAll(obligation.expectedEClasses()));
    String costs =
        candidates.stream()
            .map(name -> name + "=" + minimumSelectionCost(level, name))
            .collect(java.util.stream.Collectors.joining(", "));
    return "CAPACITY: "
        + capacity
        + " non-root types in the combined required closure.\nCANDIDATE CLOSURE COSTS: "
        + costs
        + "\nOBLIGATIONS: "
        + obligations.json()
        + "\nREQUEST: "
        + (request == null ? "" : request)
        + (priorDiagnostic == null || priorDiagnostic.isBlank()
            ? ""
            : "\nPRIOR REJECTION DIAGNOSTIC: " + priorDiagnostic.trim());
  }

  private String structuralSelectionCosts(ModelLevel level) {
    return contracts.all(level).stream()
        .filter(TypeContract::creatable)
        .sorted(java.util.Comparator.comparing(TypeContract::eClass))
        .map(
            type -> {
              return type.eClass() + "=" + minimumSelectionCost(level, type.eClass());
            })
        .collect(java.util.stream.Collectors.joining(", "));
  }

  private long minimumSelectionCost(ModelLevel level, String type) {
    return contracts.requiredContainmentClosure(level, List.of(type)).stream()
        .filter(contract -> !contracts.rootType(level).equals(contract.eClass()))
        .count();
  }

  private String currentTypes(JsonNode current) {
    LinkedHashSet<String> types = new LinkedHashSet<>();
    collectTypeNames(current, types);
    return String.join(",", types);
  }

  private JsonNode currentElementIndex(JsonNode current) {
    var result = JsonNodeFactory.instance.arrayNode();
    if (current == null || !current.isContainer()) return result;
    List<SnapshotElement> elements = new ArrayList<>();
    Set<String> indexed = new LinkedHashSet<>();
    collectElements(current, null, null, elements);
    for (SnapshotElement element : elements) {
      if (element.ownerId() == null) continue;
      JsonNode node = element.node();
      String id = node.path("id").asText("").trim();
      String type = node.path("eClass").asText("").trim();
      if (id.isBlank() || type.isBlank() || !indexed.add(id)) continue;
      var item = result.addObject();
      item.put("id", id);
      item.put("type", type);
      for (String attribute : List.of("name", "term", "stateName")) {
        String value = node.path(attribute).asText("").trim();
        if (!value.isBlank()) item.put(attribute, value);
      }
      item.put("ownerId", element.ownerId());
      if (element.ownerFeature() != null) item.put("ownerFeature", element.ownerFeature());
    }
    return result;
  }

  private boolean hasPersistedElements(JsonNode current) {
    return !currentElementIndex(current).isEmpty();
  }

  private Map<String, String> currentElementTypes(JsonNode current) {
    LinkedHashMap<String, String> result = new LinkedHashMap<>();
    if (current == null || !current.isContainer()) return result;
    List<SnapshotElement> elements = new ArrayList<>();
    collectElements(current, null, null, elements);
    for (SnapshotElement element : elements) {
      if (element.ownerId() == null) continue;
      String id = element.node().path("id").asText("").trim();
      String type = element.node().path("eClass").asText("").trim();
      if (!id.isBlank() && !type.isBlank()) result.putIfAbsent(id, type);
    }
    return java.util.Collections.unmodifiableMap(result);
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
    private long priorPromptTokens;
    private long priorCompletionTokens;
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
          priorPromptTokens = Math.max(0L, plan.path("promptTokens").asLong(0));
          priorCompletionTokens = Math.max(0L, plan.path("completionTokens").asLong(0));
        }
      }
    }

    private AssistantModelProvider.AssistantReply call(
        String system, String user, String requiredTool) {
      if (priorCalls + totalCalls() >= maxCalls) throw ProviderCallBudget.exceeded();
      if (!ProviderCallBudget.hasRemaining()) throw ProviderCallBudget.exceeded();
      String callKey = java.util.UUID.randomUUID().toString();
      long started = System.nanoTime();
      AssistantModelProvider.AssistantReply reply;
      try {
        reply =
            provider.completeStructured(
                new AssistantModelProvider.AssistantPrompt(
                    system, user, List.of(), List.of(), requiredTool));
      } catch (RuntimeException failure) {
        if (ProviderCallBudget.isExceeded(failure)) throw failure;
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
        if (failure instanceof PlatformException platform && platform.status() == 422
            || truncated(failure)) {
          throw failure;
        }
        throw new ProviderInvocationFailure(failure);
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
      plan.put("providerCalls", priorCalls + totalCalls());
      plan.put("promptTokens", priorPromptTokens + promptTokens);
      plan.put("completionTokens", priorCompletionTokens + completionTokens);
      turns.saveWorkflow(
          new AssistantTurnStore.Workflow(
              turnId,
              "CONCEPTUAL_GENERATION",
              workflow.map(AssistantTurnStore.Workflow::phase).orElse("PLANNING"),
              workflow.map(AssistantTurnStore.Workflow::currentWorkItemId).orElse(null),
              plan));
    }

    private int totalCalls() {
      return callDetails.size();
    }

    private int remainingCalls() {
      return Math.max(0, maxCalls - priorCalls - totalCalls());
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

  /**
   * Marks a failure of the provider invocation itself so semantic repair loops do not consume it.
   */
  private static final class ProviderInvocationFailure extends PlatformException {
    private ProviderInvocationFailure(RuntimeException cause) {
      super(
          cause instanceof PlatformException platform ? platform.status() : 503,
          cause.getMessage() == null ? "AI provider request failed." : cause.getMessage(),
          cause);
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
      return parse(mapper, content, Map.of());
    }

    static ConceptualModel parse(
        ObjectMapper mapper, String content, Map<String, String> expectedTypes) {
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
      // Some Gemma responses use the natural collection envelope even though the keyed paper IR
      // is preferred. Handle that wrapper before iterating root properties; otherwise the
      // collection itself is mistaken for a conceptual instance named "objects".
      if (root.path("objects").isArray()) {
        for (JsonNode item : root.path("objects")) {
          if (!item.isObject()) continue;
          String id = item.path("instanceId").asText(item.path("id").asText("")).trim();
          if (id.isBlank()) continue;
          JsonNode normalized = item;
          String expectedType = expectedTypes.get(id);
          if (expectedType != null
              && (!item.path("type").isTextual() || item.path("type").asText("").isBlank())) {
            normalized = item.deepCopy();
            String emittedEClass = item.path("eClass").asText("").trim();
            ((tools.jackson.databind.node.ObjectNode) normalized)
                .put("type", emittedEClass.isBlank() ? expectedType : emittedEClass);
          }
          result.put(id, normalized);
        }
      }
      if (!result.isEmpty()) return new ConceptualModel(result);
      root.properties()
          .forEach(
              entry -> {
                String id = entry.getKey() == null ? "" : entry.getKey().trim();
                JsonNode value = entry.getValue();
                if (id.isBlank())
                  throw new PlatformException(422, "Every instanceID must be non-empty.");
                if (expectedTypes.containsKey(id)
                    && value != null
                    && value.isArray()
                    && value.size() == 1
                    && value.get(0).isObject()) {
                  // Gemma occasionally wraps one keyed conceptual object in a singleton array.
                  // Unwrap only that lossless protocol shape; the expected ID/type and every
                  // required field remain authoritative and are validated below.
                  value = value.get(0);
                }
                if (value != null
                    && value.isObject()
                    && expectedTypes.containsKey(id)
                    && (!value.path("type").isTextual()
                        || value.path("type").asText("").isBlank())) {
                  var normalized = (tools.jackson.databind.node.ObjectNode) value.deepCopy();
                  String emittedEClass = value.path("eClass").asText("").trim();
                  normalized.put(
                      "type", emittedEClass.isBlank() ? expectedTypes.get(id) : emittedEClass);
                  value = normalized;
                }
                if (!value.isObject() || value.path("type").asText("").isBlank()) {
                  String expectedType = expectedTypes.get(id);
                  throw new PlatformException(
                      422,
                      "Conceptual object '"
                          + id
                          + "' has no EClass type"
                          + (expectedType == null
                              ? "."
                              : ". Expected the JSON object to contain type='"
                                  + expectedType
                                  + "'."));
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
      Map<String, Placement> existingPlacements = new LinkedHashMap<>();
      collectPlacements(current, existingPlacements);
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
            existingPlacements,
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
            existingPlacements,
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

      rejectOccupiedSingleValuedContainments(
          current, creates, types, existingTypes, contracts, level, persistedRootType);

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

    /**
     * Performs the structural part of patch compilation while the provider's stable IDs are still
     * available. This turns a low-level JSON-patch diagnostic into a repairable diagnostic naming
     * the exact conceptual object, and catches collisions before any workspace mutation.
     */
    private static void rejectOccupiedSingleValuedContainments(
        JsonNode current,
        List<ModelCommandBatch.Create> creates,
        Map<String, TypeContract> responseTypes,
        Map<String, String> existingTypes,
        TypeContractService contracts,
        ModelLevel level,
        String persistedRootType) {
      Map<String, String> assigned = new LinkedHashMap<>();
      for (ModelCommandBatch.Create create : creates) {
        String ownerId = create.owner();
        String ownerTypeName =
            "rootId".equals(ownerId)
                ? persistedRootType
                : responseTypes.containsKey(ownerId)
                    ? responseTypes.get(ownerId).eClass()
                    : existingTypes.get(ownerId);
        if (ownerTypeName == null || ownerTypeName.isBlank()) continue;
        TypeContract ownerType = contracts.require(level, ownerTypeName);
        ReferenceContract containment =
            ownerType.references().stream()
                .filter(reference -> reference.name().equals(create.reference()))
                .filter(ReferenceContract::containment)
                .findFirst()
                .orElse(null);
        if (containment == null || containment.many()) continue;

        String placement = ownerId + "." + containment.name();
        String previous = assigned.putIfAbsent(placement, create.clientRef());
        if (previous != null) {
          throw new PlatformException(
              422,
              "Single-valued containment "
                  + placement
                  + " can contain only one new instance; objects '"
                  + previous
                  + "' and '"
                  + create.clientRef()
                  + "' both target it.");
        }
        JsonNode owner = "rootId".equals(ownerId) ? current : findById(current, ownerId);
        if (owner != null && owner.hasNonNull(containment.name())) {
          throw new PlatformException(
              422,
              "Single-valued containment "
                  + placement
                  + " is already occupied while creating conceptual object '"
                  + create.clientRef()
                  + "'. Preserve the existing child or choose a legal empty containment.");
        }
      }
    }

    private static JsonNode findById(JsonNode node, String id) {
      if (node == null) return null;
      if (node.isObject() && id.equals(node.path("id").asText(""))) return node;
      if (!node.isContainer()) return null;
      for (JsonNode child : node) {
        JsonNode found = findById(child, id);
        if (found != null) return found;
      }
      return null;
    }

    private void compileAssociations(
        ModelLevel level,
        TypeContractService contracts,
        Map<String, String> existingTypes,
        Map<String, TypeContract> responseTypes,
        Map<String, String> aliases,
        Map<String, Placement> existingPlacements,
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
        ReferenceContract declaredReference =
            sourceType.references().stream()
                .filter(item -> item.name().equals(name))
                .findFirst()
                .orElse(null);
        // Read-only inverse EReferences are derived by EMF. They may be present in a provider
        // response because the contract is also used for explanation, but they are never patch
        // operations and must not make an otherwise valid conceptual model fail compilation.
        if (declaredReference != null && declaredReference.readonly()) continue;
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
                  + sourceId
                  + "."
                  + name
                  + " on "
                  + sourceType.eClass()
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
            Placement existing = existingPlacements.get(targetId);
            if (existing != null
                && existing.ownerId().equals(sourceId)
                && existing.feature().equals(name)) {
              // Repeating an existing parent-side containment is an idempotent preservation of
              // structure, not a move and not a new patch operation.
              continue;
            }
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
        // dataType is redundant provider metadata. Attribute name resolution above selects the
        // authoritative live Ecore datatype; never spend an LLM repair call correcting a stale or
        // generic annotation when the semantic value and exact feature are otherwise valid.
        JsonNode value =
            item.get("value") == null ? JsonNodeFactory.instance.nullNode() : item.get("value");
        if (contract.many() && !value.isNull() && !value.isArray()) {
          var values = JsonNodeFactory.instance.arrayNode();
          values.add(value.deepCopy());
          value = values;
        }
        boolean invalidEnum =
            !contract.enumLiterals().isEmpty()
                && !value.isNull()
                && (value.isArray()
                    ? java.util.stream.StreamSupport.stream(value.spliterator(), false)
                        .anyMatch(entry -> !contract.enumLiterals().contains(entry.asText()))
                    : !contract.enumLiterals().contains(value.asText()));
        if (invalidEnum) {
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
      List<String> missingRequired =
          legal.values().stream()
              .filter(AttributeContract::required)
              .map(AttributeContract::name)
              .filter(name -> !"id".equals(name))
              .filter(name -> !result.containsKey(name) || result.get(name).isNull())
              .toList();
      if (!missingRequired.isEmpty()) {
        throw new PlatformException(
            422,
            "Conceptual object '"
                + id
                + "' of "
                + type.eClass()
                + " is missing required Ecore attributes "
                + missingRequired
                + ".");
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

    private static void collectPlacements(JsonNode node, Map<String, Placement> placements) {
      if (node == null || !node.isObject()) return;
      String ownerId = node.path("id").asText("");
      node.properties()
          .forEach(
              property -> {
                if ("diagram".equals(property.getKey()) || "graph".equals(property.getKey())) {
                  return;
                }
                JsonNode value = property.getValue();
                if (value.isArray()) {
                  for (JsonNode child : value) {
                    recordPlacement(ownerId, property.getKey(), child, placements);
                    collectPlacements(child, placements);
                  }
                } else if (value.isObject()) {
                  recordPlacement(ownerId, property.getKey(), value, placements);
                  collectPlacements(value, placements);
                }
              });
    }

    private static void recordPlacement(
        String ownerId, String feature, JsonNode child, Map<String, Placement> placements) {
      if (!ownerId.isBlank() && child.hasNonNull("id") && child.hasNonNull("eClass")) {
        placements.putIfAbsent(child.path("id").asText(), new Placement(ownerId, feature));
      }
    }
  }

  private record Placement(String ownerId, String feature) {}

  private record Obligation(
      String id,
      String obligation,
      String importance,
      int minimumEvidenceObjects,
      List<String> sourceUnitIds,
      List<String> expectedEClasses) {
    private boolean mandatory() {
      return "MANDATORY".equals(importance);
    }
  }

  private record ObligationLedger(
      List<Obligation> obligations, Map<String, Obligation> byId, JsonNode json) {
    private static ObligationLedger empty() {
      return new ObligationLedger(List.of(), Map.of(), JsonNodeFactory.instance.objectNode());
    }

    private List<Obligation> mandatory() {
      return obligations.stream().filter(Obligation::mandatory).toList();
    }

    private static ObligationLedger parse(
        JsonNode root,
        TypeContractService contracts,
        ModelLevel level,
        Set<String> suppliedSourceUnits) {
      if (root == null
          || !root.isObject()
          || !root.path("obligations").isArray()
          || root.path("obligations").isEmpty()
          || root.path("obligations").size() > MAX_OBLIGATIONS) {
        throw new PlatformException(
            422, "Obligation ledger requires 1 to " + MAX_OBLIGATIONS + " obligations.");
      }
      List<Obligation> obligations = new ArrayList<>();
      Map<String, Obligation> byId = new LinkedHashMap<>();
      for (JsonNode item : root.path("obligations")) {
        String id = item.path("id").asText("").trim();
        String text = item.path("obligation").asText("").trim();
        String importance = item.path("importance").asText("").trim();
        int minimumEvidenceObjects = item.path("minimumEvidenceObjects").asInt(1);
        List<String> sources = Blueprint.strings(item.path("sourceUnitIds"));
        List<String> expected = Blueprint.strings(item.path("expectedEClasses"));
        if (id.isBlank()
            || text.isBlank()
            || !("MANDATORY".equals(importance) || "OPTIONAL".equals(importance))
            || minimumEvidenceObjects < 1
            || minimumEvidenceObjects > MAX_BLUEPRINT_OBJECTS
            || expected.isEmpty()) {
          throw new PlatformException(
              422, "Every obligation requires its ID, text, importance, and EClasses.");
        }
        if (!suppliedSourceUnits.isEmpty() && !suppliedSourceUnits.containsAll(sources)) {
          throw new PlatformException(422, "Obligation ledger used unknown source-unit IDs.");
        }
        List<String> exactTypes =
            expected.stream()
                .map(name -> contracts.require(level, name))
                .peek(
                    contract -> {
                      if (!contract.creatable()) {
                        throw new PlatformException(
                            422,
                            "Obligation "
                                + id
                                + " mapped to abstract/non-creatable EClass "
                                + contract.eClass()
                                + ".");
                      }
                    })
                .map(TypeContract::eClass)
                .distinct()
                .toList();
        Obligation obligation =
            new Obligation(
                id, text, importance, minimumEvidenceObjects, List.copyOf(sources), exactTypes);
        if (byId.putIfAbsent(id, obligation) != null) {
          throw new PlatformException(422, "Obligation IDs must be unique.");
        }
        obligations.add(obligation);
      }
      if (obligations.stream().noneMatch(Obligation::mandatory)) {
        throw new PlatformException(422, "Obligation ledger requires a mandatory obligation.");
      }
      return new ObligationLedger(
          List.copyOf(obligations), java.util.Collections.unmodifiableMap(byId), root.deepCopy());
    }
  }

  private record Blueprint(Set<String> types, List<BlueprintObject> objects, JsonNode json) {
    private static Blueprint parse(
        ObjectMapper mapper,
        String content,
        TypeContractService contracts,
        ModelLevel level,
        Set<String> persistedIds) {
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
      if (types.isEmpty() || types.size() > MAX_BLUEPRINT_OBJECTS) {
        throw new PlatformException(
            422,
            "Conceptual blueprint must select between 1 and "
                + MAX_BLUEPRINT_OBJECTS
                + " exact EClasses.");
      }
      if (root.path("objects").size() > MAX_BLUEPRINT_OBJECTS) {
        throw new PlatformException(
            422, "Conceptual blueprint may declare at most " + MAX_BLUEPRINT_OBJECTS + " objects.");
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
        if (!contracts.require(level, exactType).creatable()
            && !exactType.equals(contracts.rootType(level))) {
          throw new PlatformException(
              422,
              "Conceptual blueprint object '"
                  + id
                  + "' uses abstract or non-creatable EClass '"
                  + exactType
                  + "'. Choose an exact concrete assignable subtype from the supplied Ecore"
                  + " options.");
        }
        types.add(exactType);
        if (types.size() > MAX_BLUEPRINT_OBJECTS) {
          throw new PlatformException(
              422,
              "Conceptual blueprint uses more than " + MAX_BLUEPRINT_OBJECTS + " exact EClasses.");
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
                strings(item.path("obligationIds")),
                strings(item.path("sourceUnitIds")),
                slice));
      }
      for (BlueprintObject object : objects) {
        if (!object.ownerInstanceId().isBlank()
            && !"rootId".equals(object.ownerInstanceId())
            && !ids.contains(object.ownerInstanceId())
            && !persistedIds.contains(object.ownerInstanceId())) {
          throw new PlatformException(
              422,
              "Blueprint object '"
                  + object.instanceId()
                  + "' has unknown ownerInstanceId '"
                  + object.ownerInstanceId()
                  + "'.");
        }
        for (String target : object.referenceTargets()) {
          if (!ids.contains(target) && !persistedIds.contains(target)) {
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
      List<String> obligationIds,
      List<String> sourceUnitIds,
      int slice) {}

  private record SnapshotElement(JsonNode node, String ownerId, String ownerFeature) {}
}
