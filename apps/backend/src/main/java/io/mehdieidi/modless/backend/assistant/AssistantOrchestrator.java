package io.mehdieidi.modless.backend.assistant;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.mehdieidi.modless.platform.identity.domain.UserRecord;
import io.mehdieidi.modless.platform.kernel.ModelLevel;
import io.mehdieidi.modless.platform.kernel.PlatformException;
import io.mehdieidi.modless.platform.model.application.ModelService;
import io.mehdieidi.modless.platform.model.domain.ModelRecord;
import io.mehdieidi.modless.platform.modeling.config.ModelingConfigService;
import io.mehdieidi.modless.platform.project.application.ProjectService;
import io.mehdieidi.modless.platform.project.domain.ProjectRecord;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/** LLM-driven, metamodel-grounded, guarded-apply modeling workflow. */
@Service
public class AssistantOrchestrator {

  private static final Logger log = LoggerFactory.getLogger(AssistantOrchestrator.class);

  private final AiProperties properties;
  private final AssistantModelProvider provider;
  private final AssistantSessionStore sessions;
  private final AssistantMemoryRepository memory;
  private final SpringAiChatMemoryService chatMemory;
  private final AssistantCatalogService catalogs;
  private final AssistantModelContextIndexService modelContexts;
  private final AssistantPatchCompiler patchCompiler;
  private final AssistantRealtimeHub realtime;
  private final AssistantHardeningService hardening;
  private final ModelService models;
  private final ProjectService projects;
  private final ModelingConfigService modelingConfig = new ModelingConfigService();
  private final AssistantMetamodelSchemaService schemas = new AssistantMetamodelSchemaService();

  public AssistantOrchestrator(
      AiProperties properties,
      AssistantModelProvider provider,
      AssistantSessionStore sessions,
      AssistantMemoryRepository memory,
      SpringAiChatMemoryService chatMemory,
      AssistantCatalogService catalogs,
      AssistantModelContextIndexService modelContexts,
      AssistantPatchCompiler patchCompiler,
      AssistantRealtimeHub realtime,
      AssistantHardeningService hardening,
      ModelService models,
      ProjectService projects) {
    this.properties = properties;
    this.provider = provider;
    this.sessions = sessions;
    this.memory = memory;
    this.chatMemory = chatMemory;
    this.catalogs = catalogs;
    this.modelContexts = modelContexts;
    this.patchCompiler = patchCompiler;
    this.realtime = realtime;
    this.hardening = hardening;
    this.models = models;
    this.projects = projects;
  }

  /** Starts or resumes a level-scoped assistant session. */
  public AssistantSessionStore.AssistantSession startSession(
      UserRecord user, String projectId, ModelLevel level, String title) {
    ProjectRecord project = projects.get(user, projectId);
    String modelId = activeModelId(project, level);
    Long revision =
        modelId == null || modelId.isBlank() ? null : models.get(user, level, modelId).revision();
    memory.ensureThread(user, projectId, level, title, modelId, revision);
    return sessions.create(user.id(), projectId, level, title);
  }

  /** Handles one natural-language turn using a structured LLM decision. */
  public AssistantTurnResponse handleMessage(
      UserRecord user, String sessionId, AssistantTurnRequest request) {
    hardening.checkRateLimit(user.id());
    AssistantSessionStore.AssistantSession session = requireSession(sessionId, user.id());
    String threadId = threadId(user.id(), session.projectId(), session.level());
    appendUserMessage(threadId, sessionId, request);

    ProjectRecord project = projects.get(user, session.projectId());
    publishProgress(sessionId, "READING_MODEL", "Reading the active model and validation state");
    String modelId = resolveModelId(request.modelId(), project, session.level());
    ModelRecord model = modelId == null ? null : models.get(user, session.level(), modelId);
    requireCurrentRevision(request.revision(), model);

    JsonNode baseModel =
        model == null
            ? modelingConfig.starterModel(session.level(), session.title())
            : model.modelJson();
    ModelService.ValidationResult currentValidation = models.validate(session.level(), baseModel);
    AssistantModelContextIndexService.AssistantModelContext context =
        model == null
            ? modelContexts.transientSnapshot(
                session.projectId(),
                session.level(),
                session.title(),
                0L,
                baseModel,
                currentValidation)
            : modelContexts.snapshot(model, currentValidation);
    List<AssistantModelProvider.ContextSnippet> snippets =
        retrievalSnippets(request.message(), context, session.level());

    AssistantTurnPlan plan;
    try {
      publishProgress(
          sessionId, "PLANNING", "Understanding intent using the formal language context");
      plan =
          provider.planTurn(
              new AssistantModelProvider.AssistantPrompt(
                  AssistantModelRole.PLANNER,
                  turnPrompt(session, request, context),
                  request.message(),
                  snippets));
    } catch (PlatformException failure) {
      if (failure.status() < 500 && failure.status() != 429) {
        throw failure;
      }
      return finishTurn(
          session,
          threadId,
          new AssistantTurnResponse(
              "The modeling provider is temporarily unavailable. Your model is unchanged and "
                  + "this request is still in the conversation, so you can retry it without "
                  + "re-entering context.",
              modelId,
              model == null ? null : model.revision(),
              null,
              List.of(),
              AssistantWorkflowState.FAILED));
    }

    return switch (plan.kind()) {
      case ANSWER -> {
        if (plan.intent() == AssistantTurnPlan.Intent.MUTATION) {
          yield proposalResponse(
              user, session, threadId, request, model, baseModel, context, snippets, plan);
        }
        yield finishTurn(
            session,
            threadId,
            new AssistantTurnResponse(
                nonBlank(plan.message(), "I completed the model analysis."),
                modelId,
                model == null ? null : model.revision(),
                null,
                List.of(),
                AssistantWorkflowState.EXPLAINED));
      }
      case CLARIFICATION ->
          clarificationResponse(
              session, threadId, request, model, plan.message(), plan.questions());
      case PATCH ->
          proposalResponse(
              user, session, threadId, request, model, baseModel, context, snippets, plan);
    };
  }

  private AssistantTurnResponse proposalResponse(
      UserRecord user,
      AssistantSessionStore.AssistantSession session,
      String threadId,
      AssistantTurnRequest request,
      ModelRecord model,
      JsonNode baseModel,
      AssistantModelContextIndexService.AssistantModelContext context,
      List<AssistantModelProvider.ContextSnippet> snippets,
      AssistantTurnPlan initialPlan) {
    publishProgress(session.id(), "VALIDATING", "Compiling and validating the proposed change");
    AssistantTurnPlan acceptedPlan = safeNormalizePlan(session.level(), context, initialPlan);
    PlanAttempt attempt = evaluatePlan(session.level(), baseModel, context, acceptedPlan);
    for (int repairNumber = 1;
        !attempt.valid() && repairNumber <= properties.validationRepairAttempts();
        repairNumber++) {
      publishProgress(
          session.id(),
          "REPAIRING",
          "Repairing the plan from validator feedback ("
              + repairNumber
              + "/"
              + properties.validationRepairAttempts()
              + ")");
      AssistantTurnPlan repaired;
      try {
        repaired =
            repairPlan(session, request, context, snippets, acceptedPlan, attempt, repairNumber);
      } catch (PlatformException failure) {
        if (failure.status() < 500 && failure.status() != 429) {
          throw failure;
        }
        return recoveryClarification(session, threadId, request, model, attempt);
      }
      if (repaired.kind() == AssistantTurnPlan.Kind.CLARIFICATION) {
        return clarificationResponse(
            session, threadId, request, model, repaired.message(), repaired.questions());
      }
      if (repaired.kind() == AssistantTurnPlan.Kind.ANSWER) {
        continue;
      }
      repaired = safeNormalizePlan(session.level(), context, repaired);
      attempt = evaluatePlan(session.level(), baseModel, context, repaired);
      acceptedPlan = repaired;
    }
    if (!attempt.valid()) {
      return recoveryClarification(session, threadId, request, model, attempt);
    }

    AssistantPatchCompiler.CompiledPatch compiled = attempt.compiled();
    AssistantProposal proposal =
        new AssistantProposal(
            java.util.UUID.randomUUID().toString(),
            compiled.affectedElements(),
            acceptedPlan.patch(),
            compiled.inversePatch(),
            attempt.validation(),
            riskLevel(compiled, attempt.validation()),
            true,
            retrievalCitations(snippets, context),
            Instant.now());
    memory.clearPendingInteraction(threadId);
    memory.saveProposal(
        threadId,
        session.projectId(),
        model == null ? null : model.id(),
        model == null ? 0L : model.revision(),
        proposal,
        "PROPOSED");
    memory.appendAudit(
        proposal.id(),
        session.projectId(),
        user.id(),
        "PROPOSED",
        Map.of("operationCount", proposal.patch().operations().size(), "validationPassed", true));
    String message =
        nonBlank(acceptedPlan.message(), "I prepared the requested model change.")
            + "\n\nThe proposal passed structural and mandatory EVL validation. Review it before "
            + "applying it to the canvas.";
    return finishTurn(
        session,
        threadId,
        new AssistantTurnResponse(
            message,
            model == null ? null : model.id(),
            model == null ? 0L : model.revision(),
            proposal,
            List.of(),
            AssistantWorkflowState.PROPOSED));
  }

  private PlanAttempt evaluatePlan(
      ModelLevel level,
      JsonNode baseModel,
      AssistantModelContextIndexService.AssistantModelContext context,
      AssistantTurnPlan plan) {
    if (plan.patch().operations().isEmpty()) {
      return PlanAttempt.failure("The planner returned no semantic operations.");
    }
    try {
      validateSemanticPatch(level, plan.patch(), context);
      AssistantPatchCompiler.CompiledPatch compiled =
          patchCompiler.compile(baseModel, plan.patch());
      if (compiled.patch().isEmpty()) {
        return PlanAttempt.failure("The semantic operations would not change the model.");
      }
      ObjectNode preview = patchCompiler.apply(baseModel, compiled);
      AssistantValidationSummary validation = validationSummary(models.validate(level, preview));
      return validation.structurallyValid() && validation.mandatoryPassed()
          ? PlanAttempt.success(compiled, validation)
          : PlanAttempt.failure(compiled, validation);
    } catch (PlatformException failure) {
      String operationSummary = operationSummary(plan.patch());
      log.warn(
          "Assistant semantic plan rejected reason={} operations={}",
          failure.getMessage(),
          operationSummary);
      return PlanAttempt.failure(failure.getMessage() + " Operation summary: " + operationSummary);
    }
  }

  private String operationSummary(SemanticModelPatch patch) {
    return patch.operations().stream()
        .filter(java.util.Objects::nonNull)
        .map(
            operation ->
                operation.type()
                    + "(target="
                    + operation.targetElementId()
                    + ",type="
                    + operation.elementType()
                    + ",source="
                    + operation.sourceElementId()
                    + ",feature="
                    + operation.referenceName()
                    + ")")
        .collect(Collectors.joining(","));
  }

  private AssistantTurnPlan normalizePlan(
      ModelLevel level,
      AssistantModelContextIndexService.AssistantModelContext context,
      AssistantTurnPlan plan) {
    if (plan.patch().operations().isEmpty()) {
      return plan;
    }
    SemanticModelPatch normalizedPatch = normalizeNewElementIds(plan.patch());
    Map<String, String> types =
        context.elements().stream()
            .collect(
                Collectors.toMap(
                    AssistantModelContextIndexService.ContextElement::id,
                    AssistantModelContextIndexService.ContextElement::type,
                    (left, right) -> left,
                    LinkedHashMap::new));
    normalizedPatch.operations().stream()
        .filter(
            operation ->
                operation != null
                    && operation.type() == SemanticModelPatch.OperationType.ADD_ELEMENT)
        .filter(operation -> !blank(operation.targetElementId()))
        .forEach(
            operation ->
                types.put(
                    operation.targetElementId(),
                    schemas.canonicalType(level, operation.elementType())));
    List<SemanticModelPatch.Operation> operations =
        orderOperationsForCompilation(
            normalizedPatch.operations().stream()
                .map(operation -> inferUniqueContainment(level, types, operation))
                .map(operation -> normalizeRelationshipDirection(level, types, operation))
                .toList());
    return new AssistantTurnPlan(
        plan.intent(),
        plan.kind(),
        plan.message(),
        plan.questions(),
        new SemanticModelPatch(operations));
  }

  private SemanticModelPatch normalizeNewElementIds(SemanticModelPatch patch) {
    Map<String, String> replacements = new LinkedHashMap<>();
    patch.operations().stream()
        .filter(java.util.Objects::nonNull)
        .filter(operation -> operation.type() == SemanticModelPatch.OperationType.ADD_ELEMENT)
        .map(SemanticModelPatch.Operation::targetElementId)
        .filter(id -> id != null && !id.isBlank())
        .filter(id -> !isUuid(id))
        .distinct()
        .forEach(id -> replacements.put(id, java.util.UUID.randomUUID().toString()));
    if (replacements.isEmpty()) {
      return patch;
    }
    List<SemanticModelPatch.Operation> operations =
        patch.operations().stream()
            .map(
                operation -> {
                  if (operation == null) {
                    return null;
                  }
                  return new SemanticModelPatch.Operation(
                      operation.type(),
                      replacements.getOrDefault(
                          operation.targetElementId(), operation.targetElementId()),
                      operation.elementType(),
                      remapIds(operation.attributes(), replacements),
                      replacements.getOrDefault(
                          operation.sourceElementId(), operation.sourceElementId()),
                      operation.referenceName());
                })
            .toList();
    return new SemanticModelPatch(operations);
  }

  private JsonNode remapIds(JsonNode value, Map<String, String> replacements) {
    if (value == null || value.isNull()) {
      return value;
    }
    if (value.isTextual()) {
      String replacement = replacements.get(value.asText());
      return replacement == null
          ? value.deepCopy()
          : com.fasterxml.jackson.databind.node.TextNode.valueOf(replacement);
    }
    if (value.isObject()) {
      ObjectNode result = ((ObjectNode) value).deepCopy();
      value
          .fields()
          .forEachRemaining(
              entry -> result.set(entry.getKey(), remapIds(entry.getValue(), replacements)));
      return result;
    }
    if (value.isArray()) {
      com.fasterxml.jackson.databind.node.ArrayNode result =
          com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.arrayNode();
      value.forEach(item -> result.add(remapIds(item, replacements)));
      return result;
    }
    return value.deepCopy();
  }

  private List<SemanticModelPatch.Operation> orderOperationsForCompilation(
      List<SemanticModelPatch.Operation> operations) {
    List<SemanticModelPatch.Operation> pendingAdds =
        operations.stream()
            .filter(java.util.Objects::nonNull)
            .filter(operation -> operation.type() == SemanticModelPatch.OperationType.ADD_ELEMENT)
            .collect(Collectors.toCollection(ArrayList::new));
    List<SemanticModelPatch.Operation> ordered = new ArrayList<>();
    Set<String> pendingIds =
        pendingAdds.stream()
            .map(SemanticModelPatch.Operation::targetElementId)
            .collect(Collectors.toCollection(LinkedHashSet::new));
    while (!pendingAdds.isEmpty()) {
      List<SemanticModelPatch.Operation> ready =
          pendingAdds.stream()
              .filter(
                  operation ->
                      blank(operation.sourceElementId())
                          || !pendingIds.contains(operation.sourceElementId()))
              .toList();
      if (ready.isEmpty()) {
        ordered.addAll(pendingAdds);
        break;
      }
      ordered.addAll(ready);
      pendingAdds.removeAll(ready);
      ready.stream().map(SemanticModelPatch.Operation::targetElementId).forEach(pendingIds::remove);
    }
    operations.stream()
        .filter(
            operation ->
                operation == null
                    || operation.type() != SemanticModelPatch.OperationType.ADD_ELEMENT)
        .forEach(ordered::add);
    return ordered;
  }

  private boolean isUuid(String value) {
    try {
      return java.util.UUID.fromString(value).toString().equalsIgnoreCase(value);
    } catch (IllegalArgumentException ignored) {
      return false;
    }
  }

  private AssistantTurnPlan safeNormalizePlan(
      ModelLevel level,
      AssistantModelContextIndexService.AssistantModelContext context,
      AssistantTurnPlan plan) {
    try {
      return normalizePlan(level, context, plan);
    } catch (PlatformException ignored) {
      return plan;
    }
  }

  private SemanticModelPatch.Operation inferUniqueContainment(
      ModelLevel level, Map<String, String> types, SemanticModelPatch.Operation operation) {
    if (operation == null || operation.type() != SemanticModelPatch.OperationType.ADD_ELEMENT) {
      return operation;
    }
    if (schemas.rootCollection(level, operation.elementType()).isPresent()) {
      return new SemanticModelPatch.Operation(
          operation.type(),
          operation.targetElementId(),
          operation.elementType(),
          operation.attributes(),
          null,
          null);
    }
    List<ContainmentCandidate> allCandidates =
        types.entrySet().stream()
            .filter(entry -> !entry.getKey().equals(operation.targetElementId()))
            .flatMap(
                entry ->
                    schemas.containments(level, entry.getValue(), operation.elementType()).stream()
                        .map(
                            reference ->
                                new ContainmentCandidate(entry.getKey(), reference.name())))
            .toList();
    List<ContainmentCandidate> requestedCandidates =
        allCandidates.stream()
            .filter(
                candidate ->
                    blank(operation.sourceElementId())
                        || candidate.ownerId().equals(operation.sourceElementId()))
            .filter(
                candidate ->
                    blank(operation.referenceName())
                        || candidate.referenceName().equals(operation.referenceName()))
            .toList();
    ContainmentCandidate candidate;
    if (requestedCandidates.size() == 1) {
      candidate = requestedCandidates.get(0);
    } else if (allCandidates.size() == 1) {
      candidate = allCandidates.get(0);
    } else {
      return operation;
    }
    return new SemanticModelPatch.Operation(
        operation.type(),
        operation.targetElementId(),
        operation.elementType(),
        operation.attributes(),
        candidate.ownerId(),
        candidate.referenceName());
  }

  private SemanticModelPatch.Operation normalizeRelationshipDirection(
      ModelLevel level, Map<String, String> types, SemanticModelPatch.Operation operation) {
    if (operation == null
        || operation.type() != SemanticModelPatch.OperationType.CONNECT_ELEMENTS
        || blank(operation.referenceName())) {
      return operation;
    }
    String sourceType = types.get(operation.sourceElementId());
    String targetType = types.get(operation.targetElementId());
    if (sourceType == null || targetType == null) {
      return operation;
    }
    if (schemas.acceptsReferenceTarget(level, sourceType, operation.referenceName(), targetType)) {
      return operation;
    }
    if (!schemas.acceptsReferenceTarget(level, targetType, operation.referenceName(), sourceType)) {
      return operation;
    }
    return new SemanticModelPatch.Operation(
        operation.type(),
        operation.sourceElementId(),
        operation.elementType(),
        operation.attributes(),
        operation.targetElementId(),
        operation.referenceName());
  }

  private AssistantTurnPlan repairPlan(
      AssistantSessionStore.AssistantSession session,
      AssistantTurnRequest request,
      AssistantModelContextIndexService.AssistantModelContext context,
      List<AssistantModelProvider.ContextSnippet> snippets,
      AssistantTurnPlan failed,
      PlanAttempt attempt,
      int repairNumber) {
    String feedback = attempt.feedback().stream().limit(20).collect(Collectors.joining("\n"));
    List<AssistantModelProvider.ContextSnippet> repairContext = new ArrayList<>();
    repairContext.addAll(schemas.planningContracts(session.level(), feedback, 12));
    failed.patch().operations().stream()
        .filter(java.util.Objects::nonNull)
        .map(SemanticModelPatch.Operation::elementType)
        .filter(type -> type != null && !type.isBlank())
        .distinct()
        .forEach(
            type -> {
              try {
                repairContext.add(schemas.typeContract(session.level(), type));
              } catch (PlatformException ignored) {
                // Validator feedback already identifies unknown types.
              }
            });
    attempt.feedback().stream()
        .limit(8)
        .forEach(issue -> repairContext.addAll(catalogs.search(issue, session.level().name(), 2)));
    repairContext.addAll(snippets);
    String requestWithFeedback =
        "Original user request:\n"
            + request.message()
            + "\n\nRejected turn plan:\n"
            + failed
            + "\n\nBackend validation feedback:\n"
            + feedback
            + "\n\n"
            + "Return one complete replacement turn plan. Use PATCH only if you can correct every"
            + " failure. Add the support elements and references explicitly required by validator"
            + " feedback, choosing safe reversible defaults. Do not repeat the rejected plan or"
            + " ask the user to decide how to satisfy a formal constraint; use CLARIFICATION only"
            + " when the missing decision is genuinely a domain choice.";
    return provider.planTurn(
        new AssistantModelProvider.AssistantPrompt(
            AssistantModelRole.PLANNER,
            turnPrompt(session, request, context)
                + "\nThis is validator-guided repair pass "
                + repairNumber
                + " of "
                + properties.validationRepairAttempts()
                + ". Do not repeat rejected operations.",
            requestWithFeedback,
            deduplicate(repairContext, 24)));
  }

  private AssistantTurnResponse recoveryClarification(
      AssistantSessionStore.AssistantSession session,
      String threadId,
      AssistantTurnRequest request,
      ModelRecord model,
      PlanAttempt attempt) {
    String issue = attempt.feedback().stream().findFirst().orElse("mandatory validation failed");
    List<AssistantChoice> questions =
        List.of(
            new AssistantChoice(
                "validation-recovery",
                "The generated change could not satisfy the formal language: "
                    + issue
                    + " How should I continue?",
                AssistantChoice.SelectionMode.SINGLE,
                List.of(
                    new AssistantChoice.Option(
                        "retry",
                        "Retry with the same scope",
                        "Re-plan using the validation feedback."),
                    new AssistantChoice.Option(
                        "narrow",
                        "Narrow the change",
                        "Ask you to identify the highest-priority part.")),
                true));
    return clarificationResponse(
        session,
        threadId,
        request,
        model,
        "I rejected the invalid plan before it became a proposal. Choose a recovery path or add "
            + "more precise requirements.",
        questions);
  }

  private AssistantTurnResponse clarificationResponse(
      AssistantSessionStore.AssistantSession session,
      String threadId,
      AssistantTurnRequest request,
      ModelRecord model,
      String message,
      List<AssistantChoice> rawQuestions) {
    List<AssistantChoice> questions = normalizeQuestions(rawQuestions);
    if (questions.isEmpty()) {
      questions =
          List.of(
              new AssistantChoice(
                  "modeling-details",
                  "What consequential modeling detail should I use before preparing the proposal?",
                  AssistantChoice.SelectionMode.SINGLE,
                  List.of(),
                  true));
    }
    memory.savePendingInteraction(threadId, request, questions);
    return finishTurn(
        session,
        threadId,
        new AssistantTurnResponse(
            nonBlank(message, "I need one modeling decision before I can continue safely."),
            model == null ? null : model.id(),
            model == null ? null : model.revision(),
            null,
            questions,
            AssistantWorkflowState.WAITING_FOR_CHOICE));
  }

  private List<AssistantChoice> normalizeQuestions(List<AssistantChoice> questions) {
    if (questions == null) {
      return List.of();
    }
    Set<String> ids = new LinkedHashSet<>();
    List<AssistantChoice> result = new ArrayList<>();
    for (AssistantChoice question : questions.stream().limit(3).toList()) {
      if (question == null || question.id().isBlank() || question.prompt().isBlank()) {
        continue;
      }
      String id = uniqueId(question.id(), ids);
      List<AssistantChoice.Option> options =
          question.options().stream()
              .filter(
                  option -> option != null && !option.id().isBlank() && !option.label().isBlank())
              .limit(5)
              .toList();
      if (options.isEmpty() && !question.allowFreeText()) {
        continue;
      }
      result.add(
          new AssistantChoice(
              id, question.prompt(), question.selectionMode(), options, question.allowFreeText()));
    }
    return result;
  }

  /** Resumes a pending turn with validated structured answers. */
  public AssistantTurnResponse submitChoices(
      UserRecord user, String sessionId, List<ChoiceAnswer> answers) {
    AssistantSessionStore.AssistantSession session = requireSession(sessionId, user.id());
    String threadId = threadId(user.id(), session.projectId(), session.level());
    AssistantMemoryRepository.PendingInteractionRecord pending =
        memory
            .pendingInteraction(threadId)
            .orElseThrow(
                () -> new PlatformException(409, "No clarification is awaiting an answer."));
    Map<String, ChoiceAnswer> byId =
        (answers == null ? List.<ChoiceAnswer>of() : answers)
            .stream()
                .collect(
                    Collectors.toMap(
                        ChoiceAnswer::choiceId, value -> value, (left, right) -> right));
    List<String> resolved = new ArrayList<>();
    for (AssistantChoice question : pending.questions()) {
      ChoiceAnswer answer = byId.get(question.id());
      if (answer == null) {
        throw new PlatformException(400, "Every clarification question requires an answer.");
      }
      Set<String> allowed =
          question.options().stream().map(AssistantChoice.Option::id).collect(Collectors.toSet());
      List<String> selected =
          answer.optionIds().stream().filter(allowed::contains).distinct().toList();
      if (question.selectionMode() == AssistantChoice.SelectionMode.SINGLE && selected.size() > 1) {
        throw new PlatformException(400, "A single-select clarification has multiple answers.");
      }
      if (selected.isEmpty() && answer.freeText().isBlank()) {
        throw new PlatformException(400, "Clarification answer is empty.");
      }
      String labels =
          question.options().stream()
              .filter(option -> selected.contains(option.id()))
              .map(option -> option.label() + ": " + option.description())
              .collect(Collectors.joining("; "));
      resolved.add(
          question.prompt()
              + "\nAnswer: "
              + labels
              + (answer.freeText().isBlank() ? "" : "\nAdditional detail: " + answer.freeText()));
    }
    memory.clearPendingInteraction(threadId);
    memory.appendAudit(
        null,
        session.projectId(),
        user.id(),
        "CLARIFICATION_ANSWERED",
        Map.of("questionCount", pending.questions().size()));
    AssistantTurnRequest original = pending.request();
    AssistantTurnRequest resumed =
        new AssistantTurnRequest(
            "Continue the original request using these user-approved clarification answers.\n\n"
                + "Resolve the original intent now. If it requested a model mutation, return a "
                + "PATCH with operations, never an ANSWER that merely claims completion.\n\n"
                + "Original request:\n"
                + original.rootMessage()
                + "\n\nClarification answers:\n"
                + String.join("\n\n", resolved),
            original.modelId(),
            original.revision(),
            original.activeView(),
            original.selectedElementIds(),
            original.unsavedDraftPatch(),
            original.rootMessage());
    return handleMessage(user, sessionId, resumed);
  }

  /** Compatibility entry point for older single-choice clients. */
  public void submitChoice(UserRecord user, String sessionId, String choiceId, String optionId) {
    submitChoices(user, sessionId, List.of(new ChoiceAnswer(choiceId, List.of(optionId), "")));
  }

  /** Returns a stored proposal owned by this session. */
  public AssistantProposal proposal(UserRecord user, String sessionId, String proposalId) {
    AssistantSessionStore.AssistantSession session = requireSession(sessionId, user.id());
    return requireProposal(user, session, proposalId).proposal();
  }

  /** Revalidates and explicitly applies a stored proposal. */
  public AssistantTurnResponse approveProposal(
      UserRecord user, String sessionId, String proposalId) {
    AssistantSessionStore.AssistantSession session = requireSession(sessionId, user.id());
    AssistantMemoryRepository.ProposalRecord record = requireProposal(user, session, proposalId);
    if (!"PROPOSED".equals(record.status())) {
      throw new PlatformException(409, "Assistant proposal is no longer awaiting approval.");
    }
    ModelRecord model =
        record.modelId() == null
            ? createStarterModel(user, projects.get(user, session.projectId()), session)
            : models.get(user, session.level(), record.modelId());
    if (record.modelId() != null && model.revision() != record.modelRevision()) {
      memory.updateProposalStatus(record.id(), "FAILED");
      throw new PlatformException(409, "The model changed after this proposal was created.");
    }
    AssistantPatchCompiler.CompiledPatch compiled =
        patchCompiler.compile(model.modelJson(), record.proposal().patch());
    ObjectNode preview = patchCompiler.apply(model.modelJson(), compiled);
    AssistantValidationSummary validation =
        validationSummary(models.validate(session.level(), preview));
    if (!validation.structurallyValid() || !validation.mandatoryPassed()) {
      memory.updateProposalStatus(record.id(), "FAILED");
      memory.appendAudit(
          record.id(),
          session.projectId(),
          user.id(),
          "FAILED",
          Map.of("reason", "Proposal failed approval-time validation."));
      throw new PlatformException(422, "The proposal no longer passes mandatory validation.");
    }
    ModelRecord updated =
        models.patch(
            user, session.level(), model.id(), model.name(), compiled.patch(), model.revision());
    memory.markProposalApplied(record.id(), updated.id(), updated.revision());
    memory.appendAudit(
        record.id(),
        session.projectId(),
        user.id(),
        "APPLIED",
        Map.of("modelId", updated.id(), "revision", updated.revision()));
    realtime.publish(
        sessionId,
        "model.updated",
        Map.of("modelId", updated.id(), "revision", updated.revision(), "proposalId", record.id()));
    return new AssistantTurnResponse(
        "The validated proposal was applied to the canvas.",
        updated.id(),
        updated.revision(),
        record.proposal(),
        List.of(),
        AssistantWorkflowState.APPLIED);
  }

  /** Rejects a stored proposal without touching the model. */
  public void rejectProposal(UserRecord user, String sessionId, String proposalId) {
    AssistantSessionStore.AssistantSession session = requireSession(sessionId, user.id());
    AssistantMemoryRepository.ProposalRecord record = requireProposal(user, session, proposalId);
    if (!"PROPOSED".equals(record.status())) {
      throw new PlatformException(409, "Assistant proposal is no longer awaiting a decision.");
    }
    memory.updateProposalStatus(proposalId, "REJECTED");
    memory.appendAudit(
        proposalId, session.projectId(), user.id(), "REJECTED", Map.of("proposalId", proposalId));
    realtime.publish(sessionId, "proposal.rejected", Map.of("proposalId", proposalId));
  }

  /** Applies a validated inverse patch to an already-applied proposal. */
  public AssistantTurnResponse undoProposal(UserRecord user, String sessionId, String proposalId) {
    AssistantSessionStore.AssistantSession session = requireSession(sessionId, user.id());
    AssistantMemoryRepository.ProposalRecord record = requireProposal(user, session, proposalId);
    if (!"APPLIED".equals(record.status()) || record.proposal().inversePatch().isEmpty()) {
      throw new PlatformException(409, "Only an applied proposal with an inverse can be undone.");
    }
    ModelRecord model = models.get(user, session.level(), record.modelId());
    AssistantPatchCompiler.CompiledPatch inverse =
        new AssistantPatchCompiler.CompiledPatch(
            record.proposal().inversePatch(), List.of(), record.proposal().affectedElements());
    inverse = patchCompiler.adaptToSnapshot(model.modelJson(), inverse);
    ObjectNode preview = patchCompiler.apply(model.modelJson(), inverse);
    AssistantValidationSummary validation =
        validationSummary(models.validate(session.level(), preview));
    if (!validation.structurallyValid() || !validation.mandatoryPassed()) {
      throw new PlatformException(422, "Undo is blocked because mandatory validation would fail.");
    }
    ModelRecord updated =
        models.patch(
            user, session.level(), model.id(), model.name(), inverse.patch(), model.revision());
    memory.updateProposalStatus(record.id(), "UNDONE");
    memory.appendAudit(
        record.id(),
        session.projectId(),
        user.id(),
        "UNDONE",
        Map.of("modelId", updated.id(), "revision", updated.revision()));
    realtime.publish(
        sessionId,
        "model.updated",
        Map.of("modelId", updated.id(), "revision", updated.revision(), "proposalId", record.id()));
    return new AssistantTurnResponse(
        "The proposal was undone.",
        updated.id(),
        updated.revision(),
        record.proposal(),
        List.of(),
        AssistantWorkflowState.UNDONE);
  }

  /** Clears runtime and durable conversation state. */
  public void clear(UserRecord user, String sessionId) {
    AssistantSessionStore.AssistantSession session = requireSession(sessionId, user.id());
    String threadId = threadId(user.id(), session.projectId(), session.level());
    memory.clearThread(threadId);
    chatMemory.clear(threadId);
    sessions.clear(sessionId, user.id());
  }

  private List<AssistantModelProvider.ContextSnippet> retrievalSnippets(
      String query,
      AssistantModelContextIndexService.AssistantModelContext context,
      ModelLevel level) {
    List<AssistantModelProvider.ContextSnippet> result = new ArrayList<>();
    result.add(
        new AssistantModelProvider.ContextSnippet(
            "runtime-metamodel", level.name() + " language index", schemas.languageIndex(level)));
    result.addAll(schemas.planningContracts(level, query, 8));
    List<AssistantModelProvider.ContextSnippet> matches = catalogs.search(query, level.name(), 14);
    result.addAll(matches);
    matches.stream()
        .map(AssistantModelProvider.ContextSnippet::title)
        .distinct()
        .limit(8)
        .forEach(title -> result.addAll(catalogs.describeType(title, level.name(), 8)));
    context.validationIssues().stream()
        .limit(8)
        .forEach(
            issue ->
                result.add(
                    new AssistantModelProvider.ContextSnippet(
                        "current-validation", issue.constraint(), issue.message())));
    return deduplicate(result, 24);
  }

  private List<AssistantModelProvider.ContextSnippet> deduplicate(
      List<AssistantModelProvider.ContextSnippet> snippets, int limit) {
    Map<String, AssistantModelProvider.ContextSnippet> result = new LinkedHashMap<>();
    for (AssistantModelProvider.ContextSnippet snippet : snippets) {
      if (snippet == null) {
        continue;
      }
      String content = snippet.content() == null ? "" : snippet.content().trim();
      int max = snippet.source().startsWith("runtime-") ? 5000 : 2200;
      if (content.length() > max) {
        content = content.substring(0, max) + "\n[truncated]";
      }
      AssistantModelProvider.ContextSnippet compact =
          new AssistantModelProvider.ContextSnippet(snippet.source(), snippet.title(), content);
      result.putIfAbsent(snippet.source() + "#" + snippet.title(), compact);
    }
    return result.values().stream().limit(limit).toList();
  }

  private String turnPrompt(
      AssistantSessionStore.AssistantSession session,
      AssistantTurnRequest request,
      AssistantModelContextIndexService.AssistantModelContext context) {
    return """
    You are operating a formal modeling workbench in guarded-apply mode. Infer the user's
    natural-language intent with the LLM; do not rely on keyword routing. The current model,
    Ecore-derived language catalog, concrete-syntax metadata, and executable EVL findings are
    backend-owned facts. Never invent an EClass, feature, enum literal, existing ID, or
    containment. Use exact stable IDs from the model context.

    For a requested model change, create a semantically complete model for the user's actual
    domain and stated scope, not a canned example or arbitrary minimum-size scaffold. Use as
    many operations as the task genuinely requires, up to the configured operation limit.
    ADD_ELEMENT may mint unique stable IDs. A top-level element omits sourceElementId; an owned
    element names its exact containment owner and feature. CONNECT_ELEMENTS names an exact,
    writable, non-containment EReference. SET_ATTRIBUTE uses the attribute name and puts the new
    scalar or array value directly in attributes. DELETE_ELEMENT is permitted only when the user
    explicitly requests removal. Include every required attribute and containment described by
    the retrieved metamodel. Ask concise questions before planning when consequential intent is
    genuinely ambiguous. The backend will compile and validate every operation and the user must
    approve every valid proposal before application.
    """
        + "\nProject ID: "
        + session.projectId()
        + "\nLevel: "
        + session.level()
        + "\nActive view: "
        + nonBlank(request.activeView(), "unknown")
        + "\nSelected stable IDs: "
        + request.selectedElementIds()
        + "\nMaximum operations: "
        + properties.maxToolCalls()
        + "\n\nConversation memory:\n"
        + conversationMemory(session)
        + "\n\nCurrent model context:\n"
        + modelContexts.summarize(context);
  }

  private void validateSemanticPatch(
      ModelLevel level,
      SemanticModelPatch patch,
      AssistantModelContextIndexService.AssistantModelContext context) {
    if (patch.operations().size() > properties.maxToolCalls()) {
      throw new PlatformException(422, "The proposal exceeds the configured operation limit.");
    }
    Map<String, String> types =
        context.elements().stream()
            .collect(
                Collectors.toMap(
                    AssistantModelContextIndexService.ContextElement::id,
                    AssistantModelContextIndexService.ContextElement::type,
                    (left, right) -> left,
                    LinkedHashMap::new));
    for (SemanticModelPatch.Operation operation : patch.operations()) {
      if (operation == null || operation.type() == null) {
        throw new PlatformException(422, "A semantic operation has no type.");
      }
      switch (operation.type()) {
        case ADD_ELEMENT -> {
          if (blank(operation.targetElementId())
              || types.containsKey(operation.targetElementId())) {
            throw new PlatformException(
                422, "A new element has an invalid or duplicate stable ID.");
          }
          String type = schemas.canonicalType(level, operation.elementType());
          if (!blank(operation.sourceElementId())) {
            String ownerType = types.get(operation.sourceElementId());
            if (ownerType == null || blank(operation.referenceName())) {
              throw new PlatformException(422, "A contained element has an unknown owner.");
            }
            schemas.requireContainment(level, ownerType, operation.referenceName(), type);
          } else if (schemas.rootCollection(level, type).isEmpty()) {
            throw new PlatformException(422, "A contained-only element is missing its owner.");
          }
          types.put(operation.targetElementId(), type);
        }
        case CONNECT_ELEMENTS -> {
          String sourceType = types.get(operation.sourceElementId());
          if (sourceType == null
              || !types.containsKey(operation.targetElementId())
              || blank(operation.referenceName())
              || schemas
                  .reference(level, sourceType, operation.referenceName())
                  .filter(reference -> !reference.containment() && !reference.readonly())
                  .isEmpty()) {
            throw new PlatformException(422, "A relationship is not grounded in the metamodel.");
          }
        }
        case SET_ATTRIBUTE -> {
          String targetType = types.get(operation.targetElementId());
          if (targetType == null
              || blank(operation.referenceName())
              || schemas.attribute(level, targetType, operation.referenceName()).isEmpty()) {
            throw new PlatformException(
                422, "An attribute update is not grounded in the metamodel.");
          }
        }
        case DELETE_ELEMENT -> {
          if (!types.containsKey(operation.targetElementId())) {
            throw new PlatformException(422, "A deletion targets an unknown stable ID.");
          }
          types.remove(operation.targetElementId());
        }
      }
    }
  }

  private AssistantValidationSummary validationSummary(ModelService.ValidationResult result) {
    List<AssistantValidationSummary.Issue> issues =
        result == null
            ? List.of()
            : result.issues().stream()
                .map(
                    issue ->
                        new AssistantValidationSummary.Issue(
                            issue.severity(),
                            issue.constraint(),
                            issue.elementId(),
                            issue.message()))
                .toList();
    int optional =
        (int) issues.stream().filter(issue -> "WARNING".equalsIgnoreCase(issue.severity())).count();
    boolean mandatory =
        issues.stream().noneMatch(issue -> "ERROR".equalsIgnoreCase(issue.severity()));
    return new AssistantValidationSummary(
        result != null && result.valid(), mandatory, optional, issues);
  }

  private AssistantProposal.RiskLevel riskLevel(
      AssistantPatchCompiler.CompiledPatch compiled, AssistantValidationSummary validation) {
    boolean destructive =
        compiled.patch().stream().anyMatch(operation -> "remove".equals(operation.op()));
    if (destructive) {
      return AssistantProposal.RiskLevel.HIGH;
    }
    return compiled.patch().size() > 1 || validation.optionalIssues() > 0
        ? AssistantProposal.RiskLevel.MEDIUM
        : AssistantProposal.RiskLevel.LOW;
  }

  private List<String> retrievalCitations(
      List<AssistantModelProvider.ContextSnippet> snippets,
      AssistantModelContextIndexService.AssistantModelContext context) {
    List<String> result =
        snippets.stream()
            .map(snippet -> snippet.source() + "#" + snippet.title())
            .filter(value -> !value.isBlank())
            .distinct()
            .limit(12)
            .collect(Collectors.toCollection(ArrayList::new));
    context.validationIssues().stream()
        .map(issue -> issue.constraint())
        .filter(value -> value != null && !value.isBlank())
        .limit(6)
        .forEach(result::add);
    return result.stream().distinct().toList();
  }

  private AssistantTurnResponse finishTurn(
      AssistantSessionStore.AssistantSession session,
      String threadId,
      AssistantTurnResponse response) {
    memory.appendMessage(
        threadId,
        "ASSISTANT",
        response.assistantMessage(),
        Map.of("workflowState", response.workflowState().name()));
    chatMemory.appendAssistant(threadId, response.assistantMessage());
    updateRollingSummary(threadId);
    realtime.publish(session.id(), "chat.assistant", response);
    return response;
  }

  private void publishProgress(String sessionId, String stage, String message) {
    realtime.publish(sessionId, "assistant.progress", Map.of("stage", stage, "message", message));
  }

  private void appendUserMessage(String threadId, String sessionId, AssistantTurnRequest request) {
    Map<String, Object> metadata = new LinkedHashMap<>();
    metadata.put("sessionId", sessionId);
    if (request.modelId() != null) {
      metadata.put("modelId", request.modelId());
    }
    if (request.revision() != null) {
      metadata.put("revision", request.revision());
    }
    memory.appendMessage(threadId, "USER", request.message(), metadata);
    chatMemory.appendUser(threadId, request.message());
  }

  private String conversationMemory(AssistantSessionStore.AssistantSession session) {
    String threadId = threadId(session.userId(), session.projectId(), session.level());
    String summary = memory.summary(threadId).orElse("");
    String recent =
        chatMemory.recent(threadId, properties.hardening().recentMessageWindow()).stream()
            .map(message -> message.role() + ": " + message.content())
            .collect(Collectors.joining("\n"));
    return summary.isBlank() ? recent : "Summary: " + summary + "\nRecent:\n" + recent;
  }

  private void updateRollingSummary(String threadId) {
    List<AssistantMemoryRepository.MessageRecord> recent =
        memory.recentMessages(threadId, properties.hardening().recentMessageWindow());
    if (recent.isEmpty()) {
      return;
    }
    String summary =
        recent.stream()
            .sorted(
                java.util.Comparator.comparing(AssistantMemoryRepository.MessageRecord::createdAt))
            .map(message -> message.role() + ": " + message.content())
            .collect(Collectors.joining("\n"));
    if (summary.length() > 3000) {
      summary = summary.substring(summary.length() - 3000);
    }
    memory.updateSummary(threadId, summary, recent.get(0).id());
  }

  private AssistantMemoryRepository.ProposalRecord requireProposal(
      UserRecord user, AssistantSessionStore.AssistantSession session, String proposalId) {
    String threadId = threadId(user.id(), session.projectId(), session.level());
    return memory
        .findProposal(proposalId)
        .filter(record -> record.threadId().equals(threadId))
        .orElseThrow(() -> new PlatformException(404, "Assistant proposal not found."));
  }

  private ModelRecord createStarterModel(
      UserRecord user, ProjectRecord project, AssistantSessionStore.AssistantSession session) {
    String name = nonBlank(session.title(), session.level().apiName() + "-model");
    ModelRecord created =
        models.create(
            user,
            session.level(),
            project.id(),
            name,
            modelingConfig.starterModel(session.level(), name));
    Map<String, String> active =
        new LinkedHashMap<>(project.activeModelIds() == null ? Map.of() : project.activeModelIds());
    active.put(session.level().apiName(), created.id());
    projects.update(user, project.id(), project.name(), project.description(), active);
    memory.ensureThread(
        user, project.id(), session.level(), session.title(), created.id(), created.revision());
    return created;
  }

  private AssistantSessionStore.AssistantSession requireSession(String sessionId, String userId) {
    try {
      return sessions.require(sessionId, userId);
    } catch (PlatformException failure) {
      if (failure.status() != 404) {
        throw failure;
      }
      return memory
          .findThread(sessionId, userId)
          .map(sessions::fromThread)
          .orElseThrow(() -> failure);
    }
  }

  private void requireCurrentRevision(Long requestedRevision, ModelRecord model) {
    if (model != null
        && requestedRevision != null
        && requestedRevision.longValue() != model.revision()) {
      throw new PlatformException(
          409,
          "The active model changed from revision "
              + requestedRevision
              + " to "
              + model.revision()
              + ". Refresh before requesting a proposal.");
    }
  }

  private String resolveModelId(String requested, ProjectRecord project, ModelLevel level) {
    return blank(requested) ? activeModelId(project, level) : requested.trim();
  }

  private String activeModelId(ProjectRecord project, ModelLevel level) {
    if (project.activeModelIds() == null) {
      return null;
    }
    String value = project.activeModelIds().get(level.apiName());
    return blank(value) ? project.activeModelIds().get(level.name()) : value;
  }

  private String uniqueId(String requested, Set<String> used) {
    String base = requested.trim().replaceAll("[^A-Za-z0-9_-]", "-");
    if (base.isBlank()) {
      base = "question";
    }
    String candidate = base;
    int suffix = 2;
    while (!used.add(candidate)) {
      candidate = base + "-" + suffix++;
    }
    return candidate;
  }

  private String threadId(String userId, String projectId, ModelLevel level) {
    return userId + ":" + projectId + ":" + level.name();
  }

  private boolean blank(String value) {
    return value == null || value.isBlank();
  }

  private String nonBlank(String value, String fallback) {
    return blank(value) ? fallback : value.trim();
  }

  /** One assistant turn request. */
  public record AssistantTurnRequest(
      String message,
      String modelId,
      Long revision,
      String activeView,
      List<String> selectedElementIds,
      String unsavedDraftPatch,
      String rootMessage) {
    public AssistantTurnRequest(
        String message,
        String modelId,
        Long revision,
        String activeView,
        List<String> selectedElementIds,
        String unsavedDraftPatch) {
      this(message, modelId, revision, activeView, selectedElementIds, unsavedDraftPatch, message);
    }

    public AssistantTurnRequest {
      message = message == null ? "" : message.trim();
      selectedElementIds = selectedElementIds == null ? List.of() : List.copyOf(selectedElementIds);
      rootMessage = rootMessage == null || rootMessage.isBlank() ? message : rootMessage.trim();
    }
  }

  /** One assistant turn response. */
  public record AssistantTurnResponse(
      String assistantMessage,
      String modelId,
      Long revision,
      AssistantProposal proposal,
      List<AssistantChoice> choices,
      AssistantWorkflowState workflowState) {
    public AssistantTurnResponse {
      choices = choices == null ? List.of() : List.copyOf(choices);
      workflowState = workflowState == null ? AssistantWorkflowState.EXPLAINED : workflowState;
    }
  }

  /** One submitted clarification answer. */
  public record ChoiceAnswer(String choiceId, List<String> optionIds, String freeText) {
    public ChoiceAnswer {
      choiceId = choiceId == null ? "" : choiceId;
      optionIds = optionIds == null ? List.of() : List.copyOf(optionIds);
      freeText = freeText == null ? "" : freeText.trim();
    }
  }

  private record PlanAttempt(
      AssistantPatchCompiler.CompiledPatch compiled,
      AssistantValidationSummary validation,
      List<String> feedback) {
    static PlanAttempt success(
        AssistantPatchCompiler.CompiledPatch compiled, AssistantValidationSummary validation) {
      return new PlanAttempt(compiled, validation, List.of());
    }

    static PlanAttempt failure(String feedback) {
      return new PlanAttempt(null, null, List.of(feedback));
    }

    static PlanAttempt failure(
        AssistantPatchCompiler.CompiledPatch compiled, AssistantValidationSummary validation) {
      List<String> feedback =
          validation.issues().stream()
              .filter(issue -> "ERROR".equalsIgnoreCase(issue.severity()))
              .map(issue -> issue.constraint() + ": " + issue.message())
              .toList();
      return new PlanAttempt(
          compiled,
          validation,
          feedback.isEmpty() ? List.of("Mandatory validation failed.") : feedback);
    }

    boolean valid() {
      return compiled != null
          && validation != null
          && validation.structurallyValid()
          && validation.mandatoryPassed();
    }
  }

  private record ContainmentCandidate(String ownerId, String referenceName) {}
}
