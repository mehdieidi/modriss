package io.mehdieidi.modriss.backend.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.mehdieidi.modriss.backend.assistant.AssistantRealtimeHub;
import io.mehdieidi.modriss.backend.assistant.DurableAssistantTurnWorker;
import io.mehdieidi.modriss.backend.assistant.DurableTurnUndoService;
import io.mehdieidi.modriss.backend.guest.GuestAccessService;
import io.mehdieidi.modriss.backend.observability.ModrissMetrics;
import io.mehdieidi.modriss.backend.upload.UploadScope;
import io.mehdieidi.modriss.backend.upload.UploadService;
import io.mehdieidi.modriss.backend.upload.UploadedFileRecord;
import io.mehdieidi.modriss.platform.assistant.application.AgenticAssistantFacade;
import io.mehdieidi.modriss.platform.assistant.domain.AssistantProposal;
import io.mehdieidi.modriss.platform.assistant.domain.AssistantReadyPayload;
import io.mehdieidi.modriss.platform.assistant.session.AssistantSessionStore;
import io.mehdieidi.modriss.platform.assistant.spi.AssistantSettings;
import io.mehdieidi.modriss.platform.assistant.turn.AssistantTurn;
import io.mehdieidi.modriss.platform.assistant.turn.AssistantTurnStore;
import io.mehdieidi.modriss.platform.identity.domain.UserRecord;
import io.mehdieidi.modriss.platform.kernel.ModelLevel;
import io.mehdieidi.modriss.platform.kernel.PlatformException;
import io.mehdieidi.modriss.platform.project.application.ProjectService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import tools.jackson.databind.JsonNode;

/** Provides frontend-compatible assistant session, messaging, and event endpoints. */
@RestController
public class ChatbotController {

  private static final Logger log = LoggerFactory.getLogger(ChatbotController.class);
  private static final Duration DEFAULT_ASSISTANT_TURN_TIMEOUT = Duration.ofMinutes(5);

  private final AgenticAssistantFacade assistant;
  private final AssistantRealtimeHub realtime;
  private final AuthSupport auth;
  private final ProjectService projects;
  private final UploadService uploads;
  private final AssistantTurnStore turns;
  private final DurableTurnUndoService durableUndo;
  private final GuestAccessService guests;
  private final ModrissMetrics metrics;
  private final AssistantSettings assistantSettings;
  private final java.util.concurrent.ScheduledExecutorService turnEventReplay =
      Executors.newSingleThreadScheduledExecutor(
          runnable -> {
            Thread thread = new Thread(runnable, "assistant-turn-sse-replay");
            thread.setDaemon(true);
            return thread;
          });

  private Duration turnTimeout(String sourceText) {
    if (assistantSettings == null) return DEFAULT_ASSISTANT_TURN_TIMEOUT;
    return sourceText == null || sourceText.isBlank()
        ? assistantSettings.turnTimeout()
        : assistantSettings.sourceTurnTimeout();
  }

  /**
   * Creates the controller.
   *
   * @param assistant assistant orchestrator
   * @param auth authentication support
   * @param projects project access service
   */
  public ChatbotController(
      AgenticAssistantFacade assistant,
      AssistantRealtimeHub realtime,
      AuthSupport auth,
      ProjectService projects,
      UploadService uploads) {
    this(assistant, realtime, auth, projects, uploads, null, null, null, null, null);
  }

  public ChatbotController(
      AgenticAssistantFacade assistant,
      AssistantRealtimeHub realtime,
      AuthSupport auth,
      ProjectService projects,
      UploadService uploads,
      AssistantTurnStore turns) {
    this(assistant, realtime, auth, projects, uploads, turns, null, null, null, null);
  }

  @Autowired
  public ChatbotController(
      AgenticAssistantFacade assistant,
      AssistantRealtimeHub realtime,
      AuthSupport auth,
      ProjectService projects,
      UploadService uploads,
      AssistantTurnStore turns,
      DurableTurnUndoService durableUndo,
      GuestAccessService guests,
      ModrissMetrics metrics,
      AssistantSettings assistantSettings) {
    this.assistant = assistant;
    this.realtime = realtime;
    this.auth = auth;
    this.projects = projects;
    this.uploads = uploads;
    this.turns = turns;
    this.durableUndo = durableUndo;
    this.guests = guests;
    this.metrics = metrics;
    this.assistantSettings = assistantSettings;
  }

  /**
   * Starts a modeling assistant session.
   *
   * @param token session token
   * @param request session request
   * @return session response
   */
  @PostMapping("/api/chatbot/sessions")
  public CreateSessionResponse createSession(
      @RequestHeader("X-Auth-Token") String token,
      @Valid @RequestBody CreateSessionRequest request) {
    long started = System.nanoTime();
    if (request == null) {
      throw new PlatformException(400, "Session request is required.");
    }
    if (request.projectId() == null || request.projectId().isBlank()) {
      throw new PlatformException(400, "Project id is required.");
    }
    if (request.modelType() == null || request.modelType().isBlank()) {
      throw new PlatformException(400, "Model type is required.");
    }
    UserRecord user = auth.user(token);
    projects.get(user, request.projectId());
    ModelLevel level = ModelLevel.fromApiName(request.modelType());
    rejectUnsupportedAssistantLevel(level);
    AssistantSessionStore.AssistantSession session =
        assistant.startSession(
            user,
            request.projectId(),
            level,
            request.modelName(),
            request.resumeSessionId(),
            Boolean.TRUE.equals(request.forceNew()));
    log.info(
        "assistant session created requestId={} sessionId={} projectId={} level={} forceNew={} "
            + "resumeSessionId={} elapsedMs={}",
        mdc("requestId"),
        session.id(),
        request.projectId(),
        level.apiName(),
        Boolean.TRUE.equals(request.forceNew()),
        safeLogValue(request.resumeSessionId()),
        elapsedMillis(started));
    return new CreateSessionResponse(session.id(), null, assistant.providerMetadata().model());
  }

  /**
   * Lists recent conversations for history browsing.
   *
   * @param token session token
   * @param projectId project scope
   * @param level modeling level
   * @param days lookback window in days
   * @return recent conversations
   */
  @GetMapping("/api/chatbot/conversations")
  public List<ConversationResponse> conversations(
      @RequestHeader("X-Auth-Token") String token,
      @RequestParam String projectId,
      @RequestParam String level,
      @RequestParam(defaultValue = "3") int days) {
    if (projectId == null || projectId.isBlank()) {
      throw new PlatformException(400, "Project id is required.");
    }
    if (level == null || level.isBlank()) {
      throw new PlatformException(400, "Model type is required.");
    }
    UserRecord user = auth.user(token);
    projects.get(user, projectId);
    ModelLevel modelLevel = ModelLevel.fromApiName(level);
    rejectUnsupportedAssistantLevel(modelLevel);
    return assistant.conversations(user, projectId, modelLevel, days, 30).stream()
        .map(
            conversation ->
                new ConversationResponse(
                    conversation.sessionId(),
                    conversation.title(),
                    conversation.preview(),
                    conversation.updatedAt().toString(),
                    conversation.messageCount()))
        .toList();
  }

  private static void rejectUnsupportedAssistantLevel(ModelLevel level) {
    if (level == ModelLevel.PSM) {
      throw new PlatformException(422, "AI modeling is available only for CIM and PIM levels.");
    }
  }

  /**
   * Sends one user message to the assistant.
   *
   * @param token session token
   * @param sessionId session ID
   * @param request message request
   * @return assistant response
   */
  @PostMapping("/api/chatbot/sessions/{sessionId}/messages")
  public ResponseEntity<?> message(
      @RequestHeader("X-Auth-Token") String token,
      @PathVariable String sessionId,
      @Valid @RequestBody MessageRequest request) {
    long started = System.nanoTime();
    UserRecord user = auth.user(token);
    long sessionStarted = System.nanoTime();
    AssistantSessionStore.AssistantSession session = assistant.session(user, sessionId);
    log.info(
        "assistant HTTP message session resolved requestId={} sessionId={} projectId={} level={} "
            + "elapsedMs={}",
        mdc("requestId"),
        session.id(),
        session.projectId(),
        session.level().apiName(),
        elapsedMillis(sessionStarted));
    long attachmentStarted = System.nanoTime();
    ResolvedRequestAttachment attachment = resolveRequestAttachments(user, session, request);
    log.info(
        "assistant HTTP message attachment resolved requestId={} sessionId={} attachmentIds={} "
            + "attachmentName={} attachmentChars={} elapsedMs={}",
        mdc("requestId"),
        session.id(),
        request.attachmentIds() == null ? 0 : request.attachmentIds().size(),
        safeLogValue(attachment.name()),
        attachment.content() == null ? 0 : attachment.content().length(),
        elapsedMillis(attachmentStarted));
    if (turns != null) {
      if (request.idempotencyKey() == null || request.idempotencyKey().isBlank()) {
        throw new PlatformException(400, "idempotencyKey is required.");
      }
      var existing = turns.findByIdempotency(sessionId, request.idempotencyKey().trim());
      if (existing.isPresent()) {
        return ResponseEntity.accepted().body(accepted(existing.get()));
      }
      consumeGuestPrompt(user);
      Instant acceptedAt = Instant.now();
      var starter = assistant.ensureModel(user, sessionId, request.modelId());
      Long requestedRevision =
          request.expectedRevision() == null ? request.revision() : request.expectedRevision();
      AssistantTurn turn =
          new AssistantTurn(
              java.util.UUID.randomUUID().toString(),
              sessionId,
              user.id(),
              session.projectId(),
              session.level(),
              starter.id(),
              requestedRevision == null ? starter.revision() : requestedRevision,
              request.idempotencyKey().trim(),
              request.message(),
              attachment.content(),
              request.selectedElementIds(),
              AssistantTurn.State.QUEUED,
              acceptedAt,
              acceptedAt.plus(turnTimeout(attachment.content())),
              null,
              null,
              false,
              null,
              0,
              0,
              null,
              null,
              null,
              0,
              0,
              0);
      turns.create(turn);
      String workflowKind =
          attachment.content() == null || attachment.content().isBlank()
              ? "CREATE_OR_EDIT_MODEL"
              : "DOCUMENT_TO_CIM";
      turns.saveWorkflow(
          new AssistantTurnStore.Workflow(turn.id(), workflowKind, "QUEUED", null, null));
      turns.appendEvent(
          turn.id(), "turn.plan.ready", java.util.Map.of("workflowKind", workflowKind));
      // Record only the submitted request. Automatic slice prompts are worker control data, not
      // chat messages, and never belong in the user's conversation transcript.
      assistant.recordUserMessage(user, sessionId, request.message());
      // A starter model is only the workspace baseline. It is not a checkpoint created by this
      // turn: publishing it would make an explanation appear to mutate the canvas and would
      // make rollback target a revision the assistant never saved.
      return ResponseEntity.status(HttpStatus.ACCEPTED).body(accepted(turn));
    }
    consumeGuestPrompt(user);
    long orchestratorStarted = System.nanoTime();
    var response =
        assistant.message(
            user,
            sessionId,
            request.modelId(),
            request.revision(),
            request.message(),
            attachment.content());
    log.info(
        "assistant HTTP message completed requestId={} sessionId={} workflowState={} modelId={} "
            + "revision={} orchestratorElapsedMs={} totalElapsedMs={}",
        mdc("requestId"),
        session.id(),
        "APPLIED",
        safeLogValue(response.modelId()),
        response.revision(),
        elapsedMillis(orchestratorStarted),
        elapsedMillis(started));
    return ResponseEntity.ok(
        new MessageResponse(
            response.message(),
            response.modelId(),
            response.revision(),
            null,
            null,
            io.mehdieidi.modriss.platform.assistant.domain.AssistantWorkflowState.APPLIED,
            new ActivityResponse(
                "COMPLETED",
                "Agent turn completed",
                io.mehdieidi.modriss.platform.assistant.domain.AssistantWorkflowState.APPLIED)));
  }

  private void consumeGuestPrompt(UserRecord user) {
    if (guests != null) {
      guests.consumePrompt(user);
    }
  }

  private TurnAcceptedResponse accepted(AssistantTurn turn) {
    long cursor =
        turns == null
            ? 0L
            : turns.events(turn.id(), 0).stream()
                .mapToLong(AssistantTurn.Event::eventId)
                .max()
                .orElse(0L);
    return new TurnAcceptedResponse(
        turn.id(),
        turn.state(),
        turn.modelId(),
        turn.revision(),
        turn.acceptedAt().toString(),
        turn.deadlineAt().toString(),
        cursor);
  }

  @GetMapping("/api/chatbot/turns/{turnId}")
  public TurnStatusResponse turn(
      @RequestHeader("X-Auth-Token") String token, @PathVariable String turnId) {
    UserRecord user = auth.user(token);
    AssistantTurn turn =
        turns
            .find(turnId)
            .orElseThrow(() -> new PlatformException(404, "Assistant turn not found."));
    if (!turn.userId().equals(user.id()))
      throw new PlatformException(404, "Assistant turn not found.");
    var workflow = turns.workflow(turnId).orElse(null);
    int repairAttempts = Math.max(0, turns.validationAttempts(turnId).size() - 1);
    return new TurnStatusResponse(
        turn.id(),
        turn.state(),
        turn.modelId(),
        turn.revision(),
        turns.checkpoints(turnId).stream()
            .map(
                item ->
                    new CheckpointResponse(
                        item.id(),
                        item.ordinal(),
                        item.label(),
                        item.modelId(),
                        item.revision(),
                        item.status()))
            .toList(),
        turn.checkpointCount(),
        turn.savedElementCount(),
        turn.coveragePercent(),
        turn.remainingWork(),
        turn.finalMessage(),
        turn.providerCalls(),
        turn.promptTokens(),
        turn.completionTokens(),
        repairAttempts,
        turns.provenance(turnId).stream()
            .map(
                item ->
                    new ProvenanceResponse(
                        item.elementId(),
                        item.sourceUnitId(),
                        item.requirementId(),
                        item.kind(),
                        item.assumption()))
            .toList(),
        List.of(),
        workflow == null ? null : workflow.workflowKind(),
        workflow == null ? null : workflow.phase(),
        workflow == null ? null : workflow.currentWorkItemId(),
        turns.workItems(turnId).stream()
            .map(
                item ->
                    new WorkItemResponse(item.id(), item.ordinal(), item.label(), item.status()))
            .toList());
  }

  @PostMapping("/api/chatbot/turns/{turnId}/cancel")
  public ResponseEntity<Void> cancelTurn(
      @RequestHeader("X-Auth-Token") String token, @PathVariable String turnId) {
    UserRecord user = auth.user(token);
    AssistantTurn turn =
        turns
            .find(turnId)
            .orElseThrow(() -> new PlatformException(404, "Assistant turn not found."));
    if (!turn.userId().equals(user.id()))
      throw new PlatformException(404, "Assistant turn not found.");
    turns.requestCancellation(turnId);
    turns.audit(turnId, "CANCELLATION_REQUESTED", java.util.Map.of());
    return ResponseEntity.accepted().build();
  }

  @PostMapping("/api/chatbot/turns/{turnId}/continue")
  public ResponseEntity<TurnAcceptedResponse> continueTurn(
      @RequestHeader("X-Auth-Token") String token, @PathVariable String turnId) {
    UserRecord user = auth.user(token);
    AssistantTurn previous =
        turns
            .find(turnId)
            .orElseThrow(() -> new PlatformException(404, "Assistant turn not found."));
    if (!previous.userId().equals(user.id()))
      throw new PlatformException(404, "Assistant turn not found.");
    turns.resume(
        previous.id(), previous.revision(), Instant.now().plus(turnTimeout(previous.sourceText())));
    AssistantTurn resumed = turns.find(turnId).orElseThrow();
    return ResponseEntity.status(HttpStatus.ACCEPTED).body(accepted(resumed));
  }

  @PostMapping("/api/chatbot/turns/{turnId}/rebase")
  public ResponseEntity<TurnAcceptedResponse> rebaseTurn(
      @RequestHeader("X-Auth-Token") String token,
      @PathVariable String turnId,
      @RequestBody RebaseTurnRequest request) {
    UserRecord user = auth.user(token);
    AssistantTurn turn =
        turns
            .find(turnId)
            .orElseThrow(() -> new PlatformException(404, "Assistant turn not found."));
    if (!turn.userId().equals(user.id()))
      throw new PlatformException(404, "Assistant turn not found.");
    turns.rebase(turnId, request.expectedRevision());
    return ResponseEntity.status(HttpStatus.ACCEPTED)
        .body(accepted(turns.find(turnId).orElseThrow()));
  }

  /** Re-runs a deletion-requesting turn only after an explicit, authenticated confirmation. */
  @PostMapping("/api/chatbot/turns/{turnId}/confirm")
  public ResponseEntity<TurnAcceptedResponse> confirmTurn(
      @RequestHeader("X-Auth-Token") String token, @PathVariable String turnId) {
    UserRecord user = auth.user(token);
    AssistantTurn previous =
        turns
            .find(turnId)
            .orElseThrow(() -> new PlatformException(404, "Assistant turn not found."));
    if (!previous.userId().equals(user.id()))
      throw new PlatformException(404, "Assistant turn not found.");
    if (previous.state() != AssistantTurn.State.NEEDS_CONFIRMATION)
      throw new PlatformException(409, "This turn has no pending destructive action.");
    Instant acceptedAt = Instant.now();
    AssistantTurn next =
        new AssistantTurn(
            java.util.UUID.randomUUID().toString(),
            previous.threadId(),
            previous.userId(),
            previous.projectId(),
            previous.level(),
            previous.modelId(),
            previous.revision(),
            "confirm-" + previous.id() + "-" + java.util.UUID.randomUUID(),
            DurableAssistantTurnWorker.CONFIRMED_DESTRUCTION_PREFIX + previous.message(),
            previous.sourceText(),
            previous.selectedElementIds(),
            AssistantTurn.State.QUEUED,
            acceptedAt,
            acceptedAt.plus(turnTimeout(previous.sourceText())),
            null,
            null,
            false,
            previous.revision(),
            0,
            0,
            previous.coveragePercent(),
            previous.remainingWork(),
            null,
            0,
            0,
            0);
    turns.create(next);
    turns.appendEvent(next.id(), "turn.stage", java.util.Map.of("stage", "DESTRUCTION_CONFIRMED"));
    turns.audit(
        next.id(), "DESTRUCTION_CONFIRMED", java.util.Map.of("previousTurnId", previous.id()));
    return ResponseEntity.status(HttpStatus.ACCEPTED).body(accepted(next));
  }

  @PostMapping("/api/chatbot/turns/{turnId}/undo")
  public TurnUndoResponse undoTurn(
      @RequestHeader("X-Auth-Token") String token, @PathVariable String turnId) {
    UserRecord user = auth.user(token);
    AssistantTurn turn =
        turns
            .find(turnId)
            .orElseThrow(() -> new PlatformException(404, "Assistant turn not found."));
    if (!turn.userId().equals(user.id()))
      throw new PlatformException(404, "Assistant turn not found.");
    if (durableUndo == null) throw new PlatformException(503, "Durable turn undo is unavailable.");
    var result = durableUndo.undo(user, turn);
    return new TurnUndoResponse(result.modelId(), result.revision());
  }

  @PostMapping("/api/chatbot/turns/{turnId}/checkpoints/{checkpointId}/rollback")
  public TurnUndoResponse rollbackCheckpoint(
      @RequestHeader("X-Auth-Token") String token,
      @PathVariable String turnId,
      @PathVariable long checkpointId) {
    UserRecord user = auth.user(token);
    AssistantTurn turn =
        turns
            .find(turnId)
            .orElseThrow(() -> new PlatformException(404, "Assistant turn not found."));
    if (!turn.userId().equals(user.id()))
      throw new PlatformException(404, "Assistant turn not found.");
    if (durableUndo == null) throw new PlatformException(503, "Durable turn undo is unavailable.");
    var result = durableUndo.rollback(user, turn, checkpointId);
    return new TurnUndoResponse(result.modelId(), result.revision());
  }

  /** Records explicit user acceptance/rejection without storing free-form feedback as telemetry. */
  @PostMapping("/api/chatbot/turns/{turnId}/feedback")
  public ResponseEntity<Void> feedback(
      @RequestHeader("X-Auth-Token") String token,
      @PathVariable String turnId,
      @RequestBody TurnFeedbackRequest request) {
    UserRecord user = auth.user(token);
    AssistantTurn turn =
        turns
            .find(turnId)
            .orElseThrow(() -> new PlatformException(404, "Assistant turn not found."));
    if (!turn.userId().equals(user.id()))
      throw new PlatformException(404, "Assistant turn not found.");
    String signal = request.accepted() ? "accepted" : "rejected";
    turns.appendEvent(turn.id(), "turn.feedback", java.util.Map.of("accepted", request.accepted()));
    turns.audit(turn.id(), "USER_" + signal.toUpperCase(java.util.Locale.ROOT), java.util.Map.of());
    if (metrics != null) metrics.recordAssistantUserSignal(signal);
    return ResponseEntity.accepted().build();
  }

  /**
   * Uploads a text attachment for a chatbot session.
   *
   * @param token session token
   * @param sessionId assistant session ID
   * @param file uploaded file
   * @return stored upload metadata
   */
  @PostMapping(
      value = "/api/chatbot/sessions/{sessionId}/attachments",
      consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public AttachmentResponse uploadAttachment(
      @RequestHeader("X-Auth-Token") String token,
      @PathVariable String sessionId,
      @RequestParam("file") MultipartFile file) {
    long started = System.nanoTime();
    UserRecord user = auth.user(token);
    AssistantSessionStore.AssistantSession session = assistant.session(user, sessionId);
    projects.get(user, session.projectId());
    UploadedFileRecord record =
        uploads.uploadAssistantAttachment(
            new UploadScope(user.id(), session.projectId(), session.level(), session.id()), file);
    log.info(
        "assistant attachment uploaded requestId={} sessionId={} projectId={} level={} fileName={} "
            + "contentType={} sizeBytes={} elapsedMs={}",
        mdc("requestId"),
        session.id(),
        session.projectId(),
        session.level().apiName(),
        safeLogValue(record.originalFileName()),
        safeLogValue(record.contentType()),
        record.sizeBytes(),
        elapsedMillis(started));
    return new AttachmentResponse(
        record.id(),
        record.originalFileName(),
        record.contentType(),
        record.sizeBytes(),
        record.level().apiName(),
        record.projectId(),
        record.uploadedAt().toString());
  }

  /**
   * Returns durable thread history for client hydration.
   *
   * @param token session token
   * @param sessionId session ID
   * @return thread snapshot
   */
  @GetMapping("/api/chatbot/sessions/{sessionId}/thread")
  public ThreadResponse thread(
      @RequestHeader("X-Auth-Token") String token, @PathVariable String sessionId) {
    AgenticAssistantFacade.ThreadSnapshot snapshot = assistant.thread(auth.user(token), sessionId);
    AssistantTurn activeTurn = turns.activeForThread(sessionId).orElse(null);
    return new ThreadResponse(
        snapshot.messages().stream()
            .map(message -> new ThreadMessageResponse(message.role(), message.content()))
            .toList(),
        snapshot.workflowState(),
        snapshot.provider(),
        activeTurn == null
            ? null
            : new ActiveTurnResponse(
                activeTurn.id(), activeTurn.state(), accepted(activeTurn).eventCursor()));
  }

  private ResolvedRequestAttachment resolveRequestAttachments(
      UserRecord user, AssistantSessionStore.AssistantSession session, MessageRequest request) {
    return resolveRequestAttachments(
        user,
        session,
        request.attachmentIds(),
        request.attachmentName(),
        request.attachmentContent(),
        false);
  }

  private ResolvedRequestAttachment resolveRequestAttachments(
      UserRecord user,
      AssistantSessionStore.AssistantSession session,
      List<String> attachmentIds,
      String attachmentName,
      String attachmentContent,
      boolean includeRecentFallback) {
    UploadScope scope =
        new UploadScope(user.id(), session.projectId(), session.level(), session.id());
    List<UploadService.ResolvedAttachment> resolved =
        uploads.resolveAssistantAttachments(scope, attachmentIds);
    if (resolved.isEmpty()
        && includeRecentFallback
        && (attachmentIds == null || attachmentIds.isEmpty())
        && (attachmentContent == null || attachmentContent.isBlank())) {
      resolved = uploads.resolveRecentAssistantAttachments(scope, 3);
    }
    if (resolved.isEmpty()) {
      return new ResolvedRequestAttachment(attachmentName, attachmentContent);
    }
    String name =
        resolved.stream()
            .map(UploadService.ResolvedAttachment::name)
            .reduce((left, right) -> left + ", " + right)
            .orElse("attachments");
    String content =
        resolved.stream()
            .map(attachment -> "## " + attachment.name() + "\n\n" + attachment.content())
            .reduce((left, right) -> left + "\n\n---\n\n" + right)
            .orElse("");
    return new ResolvedRequestAttachment(name, content);
  }

  /**
   * Opens an SSE stream for assistant events.
   *
   * @param token session token
   * @param sessionId session ID
   * @return stream
   * @throws IOException if the initial event cannot be written
   */
  @GetMapping(
      value = "/api/chatbot/sessions/{sessionId}/events",
      produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  public SseEmitter events(
      @RequestHeader("X-Auth-Token") String token, @PathVariable String sessionId) {
    assistant.session(auth.user(token), sessionId);
    SseEmitter emitter = new SseEmitter(Duration.ofMinutes(30).toMillis());
    realtime.registerSse(sessionId, emitter);
    realtime.publish(sessionId, "assistant.ready", new AssistantReadyPayload(sessionId));
    return emitter;
  }

  /** Authenticated durable event replay for a turn. */
  @GetMapping(
      value = "/api/chatbot/turns/{turnId}/events",
      produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  public SseEmitter turnEvents(
      @RequestHeader("X-Auth-Token") String token,
      @PathVariable String turnId,
      @RequestHeader(value = "Last-Event-ID", required = false) String lastEventId,
      @RequestParam(value = "eventCursor", defaultValue = "0") long eventCursor) {
    UserRecord user = auth.user(token);
    AssistantTurn turn =
        turns
            .find(turnId)
            .orElseThrow(() -> new PlatformException(404, "Assistant turn not found."));
    if (!turn.userId().equals(user.id()))
      throw new PlatformException(404, "Assistant turn not found.");
    long cursor = eventCursor;
    if (lastEventId != null && !lastEventId.isBlank()) {
      try {
        cursor = Long.parseLong(lastEventId);
      } catch (NumberFormatException ignored) {
        // Keep the query cursor when the browser sends a malformed Last-Event-ID value.
      }
    }
    SseEmitter emitter = new SseEmitter(Duration.ofMinutes(30).toMillis());
    final long[] replayCursor = {cursor};
    final ScheduledFuture<?>[] replay = new ScheduledFuture<?>[1];
    try {
      replayTurnEvents(emitter, turnId, replayCursor);
      if (turn.terminal()) {
        emitter.complete();
      } else {
        replay[0] =
            turnEventReplay.scheduleWithFixedDelay(
                () -> {
                  try {
                    replayTurnEvents(emitter, turnId, replayCursor);
                    if (turns.find(turnId).map(AssistantTurn::terminal).orElse(true))
                      emitter.complete();
                  } catch (IOException | RuntimeException ex) {
                    closeTurnEventStream(emitter, turnId, ex);
                  }
                },
                250,
                250,
                TimeUnit.MILLISECONDS);
        emitter.onCompletion(() -> replay[0].cancel(false));
        emitter.onTimeout(() -> replay[0].cancel(false));
        emitter.onError(error -> replay[0].cancel(false));
      }
    } catch (IOException | RuntimeException ex) {
      closeTurnEventStream(emitter, turnId, ex);
    }
    return emitter;
  }

  private void replayTurnEvents(SseEmitter emitter, String turnId, long[] cursor)
      throws IOException {
    for (AssistantTurn.Event event : turns.events(turnId, cursor[0])) {
      emitter.send(
          SseEmitter.event().id(Long.toString(event.eventId())).name(event.type()).data(event));
      cursor[0] = event.eventId();
    }
  }

  private void closeTurnEventStream(SseEmitter emitter, String turnId, Exception error) {
    if (!(error instanceof IOException))
      log.error("Unable to replay assistant turn events for {}", turnId, error);
    // An SSE response cannot be replaced by the global JSON error response once streaming starts.
    emitter.complete();
  }

  /**
   * Clears backend runtime memory for a session.
   *
   * @param sessionId session ID
   */
  @DeleteMapping("/api/chatbot/sessions/{sessionId}")
  public void clear(@RequestHeader("X-Auth-Token") String token, @PathVariable String sessionId) {
    assistant.clear(auth.user(token), sessionId);
  }

  /**
   * Reserved endpoint for proposal details.
   *
   * @param sessionId session ID
   * @param proposalId proposal ID
   * @return proposal details
   */
  @GetMapping("/api/chatbot/sessions/{sessionId}/proposals/{proposalId}")
  public AssistantProposal proposal(
      @RequestHeader("X-Auth-Token") String token,
      @PathVariable String sessionId,
      @PathVariable String proposalId) {
    return assistant.proposal(auth.user(token), sessionId, proposalId);
  }

  /**
   * Applies the inverse patch for an already-applied proposal.
   *
   * @param sessionId session ID
   * @param proposalId proposal ID
   * @return assistant response
   */
  @PostMapping("/api/chatbot/sessions/{sessionId}/proposals/{proposalId}/undo")
  public MessageResponse undo(
      @RequestHeader("X-Auth-Token") String token,
      @PathVariable String sessionId,
      @PathVariable String proposalId) {
    long started = System.nanoTime();
    AgenticAssistantFacade.UndoResult response =
        assistant.undo(auth.user(token), sessionId, proposalId);
    log.info(
        "assistant undo completed requestId={} sessionId={} proposalId={} workflowState={} "
            + "modelId={} revision={} elapsedMs={}",
        mdc("requestId"),
        sessionId,
        proposalId,
        "UNDONE",
        safeLogValue(response.modelId()),
        response.revision(),
        elapsedMillis(started));
    return new MessageResponse(
        response.message(),
        response.modelId(),
        response.revision(),
        null,
        response.proposal(),
        io.mehdieidi.modriss.platform.assistant.domain.AssistantWorkflowState.UNDONE,
        new ActivityResponse(
            "COMPLETED",
            "Proposal undone",
            io.mehdieidi.modriss.platform.assistant.domain.AssistantWorkflowState.UNDONE));
  }

  private long elapsedMillis(long startedNanos) {
    return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedNanos);
  }

  private String mdc(String key) {
    String value = MDC.get(key);
    return value == null ? "" : value;
  }

  private String safeLogValue(String value) {
    if (value == null || value.isBlank()) {
      return "";
    }
    String compact = value.trim().replaceAll("\\s+", " ");
    return compact.length() <= 120 ? compact : compact.substring(0, 117) + "...";
  }

  /**
   * Session creation payload.
   *
   * @param modelType modeling level
   * @param modelName model display name
   * @param initialDocument legacy initial document hint
   * @param projectId project scope
   */
  @JsonIgnoreProperties(ignoreUnknown = true)
  public record CreateSessionRequest(
      @NotBlank String modelType,
      String modelName,
      String initialDocument,
      @NotBlank String projectId,
      String resumeSessionId,
      Boolean forceNew) {}

  /**
   * Session creation response.
   *
   * @param sessionId session ID
   * @param modelId optional model ID
   */
  public record CreateSessionResponse(String sessionId, String modelId, String modelName) {
    public CreateSessionResponse(String sessionId, String modelId) {
      this(sessionId, modelId, null);
    }
  }

  /**
   * Assistant message payload.
   *
   * @param message user message
   * @param modelId active model ID
   * @param revision active model revision
   * @param activeView active canvas view
   * @param selectedElementIds selected stable element IDs
   * @param attachmentName optional attachment name
   * @param attachmentContent optional attachment content
   */
  @JsonIgnoreProperties(ignoreUnknown = true)
  public record MessageRequest(
      @NotBlank String message,
      String modelId,
      Long revision,
      String activeView,
      List<String> selectedElementIds,
      String attachmentName,
      String attachmentContent,
      List<String> attachmentIds,
      String idempotencyKey,
      Long expectedRevision) {}

  /**
   * Upload response.
   *
   * @param id attachment ID used in message requests
   * @param fileName original file name
   * @param contentType submitted media type
   * @param sizeBytes stored content size
   * @param level modeling level
   * @param projectId project scope
   * @param uploadedAt upload timestamp
   */
  public record AttachmentResponse(
      String id,
      String fileName,
      String contentType,
      long sizeBytes,
      String level,
      String projectId,
      String uploadedAt) {}

  /**
   * Assistant message response.
   *
   * @param assistantMessage user-facing message
   * @param modelId associated model ID
   * @param revision associated revision
   * @param model optional updated model, currently withheld
   * @param proposal optional proposal
   * @param workflowState explicit assistant workflow state
   */
  public record MessageResponse(
      String assistantMessage,
      String modelId,
      Long revision,
      JsonNode model,
      AssistantProposal proposal,
      io.mehdieidi.modriss.platform.assistant.domain.AssistantWorkflowState workflowState,
      ActivityResponse activity) {}

  /** Immediate acknowledgement for an asynchronously persisted turn. */
  public record TurnAcceptedResponse(
      String turnId,
      AssistantTurn.State state,
      String modelId,
      Long revision,
      String acceptedAt,
      String deadlineAt,
      long eventCursor) {}

  /** Durable turn status suitable for polling after an SSE disconnect. */
  public record TurnStatusResponse(
      String turnId,
      AssistantTurn.State state,
      String modelId,
      Long revision,
      List<CheckpointResponse> checkpoints,
      int checkpointCount,
      int savedElementCount,
      Integer coveragePercent,
      String remainingWork,
      String finalMessage,
      int providerCalls,
      long promptTokens,
      long completionTokens,
      int repairAttempts,
      List<ProvenanceResponse> provenance,
      List<ContinuationResponse> continuations,
      String workflowKind,
      String phase,
      String currentWorkItemId,
      List<WorkItemResponse> workItems) {}

  public record WorkItemResponse(String id, int ordinal, String label, String status) {}

  /** Source-grounded and inferred labels for the elements committed in this turn. */
  public record ProvenanceResponse(
      String elementId,
      String sourceUnitId,
      String requirementId,
      String kind,
      String assumption) {}

  /** Direct continuations for an automatically or explicitly resumed progressive turn. */
  public record ContinuationResponse(String turnId, AssistantTurn.State state, Long revision) {}

  /** A persisted model checkpoint available for reload and safe inverse application. */
  public record CheckpointResponse(
      long checkpointId, int ordinal, String label, String modelId, long revision, String status) {}

  /** Revision returned after a safe checkpoint inverse is persisted. */
  public record TurnUndoResponse(String modelId, long revision) {}

  /** Revision observed after the caller has resolved a non-overlapping canvas change. */
  public record RebaseTurnRequest(long expectedRevision) {}

  /** Explicit binary quality signal for an applied assistant turn. */
  public record TurnFeedbackRequest(boolean accepted) {}

  /**
   * HTTP-visible assistant activity snapshot.
   *
   * @param stage current stage
   * @param message human-readable status
   * @param workflowState workflow state
   */
  public record ActivityResponse(
      String stage,
      String message,
      io.mehdieidi.modriss.platform.assistant.domain.AssistantWorkflowState workflowState) {}

  /**
   * Durable thread snapshot.
   *
   * @param messages recent messages
   * @param workflowState current workflow state
   * @param proposal latest proposed patch
   * @param provider active provider metadata
   */
  public record ThreadResponse(
      List<ThreadMessageResponse> messages,
      io.mehdieidi.modriss.platform.assistant.domain.AssistantWorkflowState workflowState,
      io.mehdieidi.modriss.platform.assistant.provider.AssistantModelProvider
              .AssistantProviderMetadata
          provider,
      ActiveTurnResponse activeTurn) {}

  /** Active durable work exposed during thread hydration after a browser reload. */
  public record ActiveTurnResponse(String turnId, AssistantTurn.State state, long eventCursor) {}

  /** One durable thread message. */
  public record ThreadMessageResponse(String role, String content) {}

  /**
   * Conversation list entry for history browsing.
   *
   * @param sessionId durable session ID
   * @param title display title
   * @param preview short gist of the conversation
   * @param updatedAt last activity timestamp
   * @param messageCount total stored messages
   */
  public record ConversationResponse(
      String sessionId, String title, String preview, String updatedAt, int messageCount) {}

  private record ResolvedRequestAttachment(String name, String content) {}
}
