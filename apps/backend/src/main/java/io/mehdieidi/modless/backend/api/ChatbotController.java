package io.mehdieidi.modless.backend.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;
import io.mehdieidi.modless.backend.assistant.AssistantChoice;
import io.mehdieidi.modless.backend.assistant.AssistantOrchestrator;
import io.mehdieidi.modless.backend.assistant.AssistantProposal;
import io.mehdieidi.modless.backend.assistant.AssistantReadyPayload;
import io.mehdieidi.modless.backend.assistant.AssistantRealtimeHub;
import io.mehdieidi.modless.backend.assistant.AssistantSessionStore;
import io.mehdieidi.modless.platform.identity.domain.UserRecord;
import io.mehdieidi.modless.platform.kernel.ModelLevel;
import io.mehdieidi.modless.platform.kernel.PlatformException;
import io.mehdieidi.modless.platform.project.application.ProjectService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.time.Duration;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/** Provides frontend-compatible assistant session, messaging, and event endpoints. */
@RestController
public class ChatbotController {

  private final AssistantOrchestrator assistant;
  private final AssistantRealtimeHub realtime;
  private final AuthSupport auth;
  private final ProjectService projects;

  /**
   * Creates the controller.
   *
   * @param assistant assistant orchestrator
   * @param auth authentication support
   * @param projects project access service
   */
  public ChatbotController(
      AssistantOrchestrator assistant,
      AssistantRealtimeHub realtime,
      AuthSupport auth,
      ProjectService projects) {
    this.assistant = assistant;
    this.realtime = realtime;
    this.auth = auth;
    this.projects = projects;
  }

  /**
   * Starts a modeling assistant session.
   *
   * @param token session token
   * @param request session request
   * @return session response
   */
  @PostMapping("/api/chatbot/sessions")
  CreateSessionResponse createSession(
      @RequestHeader("X-Auth-Token") String token,
      @Valid @RequestBody CreateSessionRequest request) {
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
    AssistantSessionStore.AssistantSession session =
        assistant.startSession(
            user,
            request.projectId(),
            level,
            request.modelName(),
            request.resumeSessionId(),
            Boolean.TRUE.equals(request.forceNew()));
    return new CreateSessionResponse(session.id(), null);
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
  List<ConversationResponse> conversations(
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
    return assistant.listConversations(user, projectId, modelLevel, days, 30).stream()
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

  /**
   * Sends one user message to the assistant.
   *
   * @param token session token
   * @param sessionId session ID
   * @param request message request
   * @return assistant response
   */
  @PostMapping("/api/chatbot/sessions/{sessionId}/messages")
  MessageResponse message(
      @RequestHeader("X-Auth-Token") String token,
      @PathVariable String sessionId,
      @Valid @RequestBody MessageRequest request) {
    UserRecord user = auth.user(token);
    AssistantOrchestrator.AssistantTurnResponse response =
        assistant.handleMessage(
            user,
            sessionId,
            new AssistantOrchestrator.AssistantTurnRequest(
                request.message(),
                request.modelId(),
                request.revision(),
                request.activeView(),
                request.selectedElementIds(),
                request.unsavedDraftPatch()));
    return toMessageResponse(response);
  }

  /**
   * Returns durable thread history for client hydration.
   *
   * @param token session token
   * @param sessionId session ID
   * @return thread snapshot
   */
  @GetMapping("/api/chatbot/sessions/{sessionId}/thread")
  ThreadResponse thread(
      @RequestHeader("X-Auth-Token") String token, @PathVariable String sessionId) {
    AssistantOrchestrator.ThreadSnapshot snapshot = assistant.thread(auth.user(token), sessionId);
    return new ThreadResponse(
        snapshot.messages().stream()
            .map(message -> new ThreadMessageResponse(message.role(), message.content()))
            .toList(),
        snapshot.pendingChoices(),
        snapshot.workflowState(),
        snapshot.proposal(),
        snapshot.provider());
  }

  private MessageResponse toMessageResponse(AssistantOrchestrator.AssistantTurnResponse response) {
    AssistantOrchestrator.AssistantActivity activity = response.activity();
    if (activity == null) {
      activity = new AssistantOrchestrator.AssistantActivity(null, null, response.workflowState());
    }
    return new MessageResponse(
        response.assistantMessage(),
        response.modelId(),
        response.revision(),
        null,
        response.proposal(),
        response.choices(),
        response.workflowState(),
        new ActivityResponse(activity.stage(), activity.message(), activity.workflowState()));
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
  SseEmitter events(@PathVariable String sessionId) {
    SseEmitter emitter = new SseEmitter(Duration.ofMinutes(30).toMillis());
    realtime.registerSse(sessionId, emitter);
    realtime.publish(sessionId, "assistant.ready", new AssistantReadyPayload(sessionId));
    return emitter;
  }

  /**
   * Clears backend runtime memory for a session.
   *
   * @param sessionId session ID
   */
  @DeleteMapping("/api/chatbot/sessions/{sessionId}")
  void clear(@RequestHeader("X-Auth-Token") String token, @PathVariable String sessionId) {
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
  AssistantProposal proposal(
      @RequestHeader("X-Auth-Token") String token,
      @PathVariable String sessionId,
      @PathVariable String proposalId) {
    return assistant.proposal(auth.user(token), sessionId, proposalId);
  }

  /**
   * Reserved endpoint for proposal approval.
   *
   * @param sessionId session ID
   * @param proposalId proposal ID
   */
  @PostMapping("/api/chatbot/sessions/{sessionId}/proposals/{proposalId}/approve")
  MessageResponse approve(
      @RequestHeader("X-Auth-Token") String token,
      @PathVariable String sessionId,
      @PathVariable String proposalId) {
    AssistantOrchestrator.AssistantTurnResponse response =
        assistant.approveProposal(auth.user(token), sessionId, proposalId);
    return toMessageResponse(response);
  }

  /**
   * Reserved endpoint for proposal rejection.
   *
   * @param sessionId session ID
   * @param proposalId proposal ID
   */
  @PostMapping("/api/chatbot/sessions/{sessionId}/proposals/{proposalId}/reject")
  void reject(
      @RequestHeader("X-Auth-Token") String token,
      @PathVariable String sessionId,
      @PathVariable String proposalId) {
    assistant.rejectProposal(auth.user(token), sessionId, proposalId);
  }

  /**
   * Applies the inverse patch for an already-applied proposal.
   *
   * @param sessionId session ID
   * @param proposalId proposal ID
   * @return assistant response
   */
  @PostMapping("/api/chatbot/sessions/{sessionId}/proposals/{proposalId}/undo")
  MessageResponse undo(
      @RequestHeader("X-Auth-Token") String token,
      @PathVariable String sessionId,
      @PathVariable String proposalId) {
    AssistantOrchestrator.AssistantTurnResponse response =
        assistant.undoProposal(auth.user(token), sessionId, proposalId);
    return toMessageResponse(response);
  }

  /**
   * Reserved endpoint for bounded user-choice submission.
   *
   * @param sessionId session ID
   * @param request choice request
   */
  @PostMapping("/api/chatbot/sessions/{sessionId}/choices")
  MessageResponse choose(
      @RequestHeader("X-Auth-Token") String token,
      @PathVariable String sessionId,
      @RequestBody ChoiceRequest request) {
    UserRecord user = auth.user(token);
    if (request == null) {
      throw new PlatformException(400, "Clarification answers are required.");
    }
    List<AssistantOrchestrator.ChoiceAnswer> answers =
        request.answers() == null || request.answers().isEmpty()
            ? List.of(
                new AssistantOrchestrator.ChoiceAnswer(
                    request.choiceId(),
                    request.optionId() == null ? List.of() : List.of(request.optionId()),
                    ""))
            : request.answers().stream()
                .map(
                    answer ->
                        new AssistantOrchestrator.ChoiceAnswer(
                            answer.choiceId(), answer.optionIds(), answer.freeText()))
                .toList();
    AssistantOrchestrator.AssistantTurnResponse response =
        assistant.submitChoices(user, sessionId, answers);
    return toMessageResponse(response);
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
  public record CreateSessionResponse(String sessionId, String modelId) {}

  /**
   * Assistant message payload.
   *
   * @param message user message
   * @param modelId active model ID
   * @param revision active model revision
   * @param activeView active canvas view
   * @param selectedElementIds selected stable element IDs
   * @param unsavedDraftPatch optional compact draft patch
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
      String unsavedDraftPatch,
      String attachmentName,
      String attachmentContent) {}

  /**
   * Assistant message response.
   *
   * @param assistantMessage user-facing message
   * @param modelId associated model ID
   * @param revision associated revision
   * @param model optional updated model, currently withheld
   * @param proposal optional proposal
   * @param choices optional choices
   * @param workflowState explicit assistant workflow state
   */
  public record MessageResponse(
      String assistantMessage,
      String modelId,
      Long revision,
      JsonNode model,
      AssistantProposal proposal,
      List<AssistantChoice> choices,
      io.mehdieidi.modless.backend.assistant.AssistantWorkflowState workflowState,
      ActivityResponse activity) {}

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
      io.mehdieidi.modless.backend.assistant.AssistantWorkflowState workflowState) {}

  /**
   * Durable thread snapshot.
   *
   * @param messages recent messages
   * @param pendingChoices pending clarification questions
   * @param workflowState current workflow state
   * @param proposal latest proposed patch
   * @param provider active provider metadata
   */
  public record ThreadResponse(
      List<ThreadMessageResponse> messages,
      List<AssistantChoice> pendingChoices,
      io.mehdieidi.modless.backend.assistant.AssistantWorkflowState workflowState,
      AssistantProposal proposal,
      io.mehdieidi.modless.backend.assistant.AssistantModelProvider.AssistantProviderMetadata
          provider) {}

  /** One durable thread message. */
  public record ThreadMessageResponse(String role, String content) {}

  /**
   * User choice submission.
   *
   * @param choiceId selected choice ID
   * @param optionId selected option ID
   */
  public record ChoiceRequest(
      String choiceId, String optionId, List<ClarificationAnswer> answers) {}

  /** One answer to a structured clarification question. */
  public record ClarificationAnswer(String choiceId, List<String> optionIds, String freeText) {}

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
}
