package io.mehdieidi.varka.backend.assistant;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.mehdieidi.varka.platform.assistant.domain.AssistantRealtimeEvent;
import io.mehdieidi.varka.platform.assistant.spi.AssistantRealtimePublisher;
import java.io.IOException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

/** Fan-out hub for assistant realtime events across websocket and SSE transports. */
@Service
public class AssistantRealtimeHub implements AssistantRealtimePublisher {

  private final ObjectMapper mapper;
  private final ConcurrentHashMap<String, Set<SseEmitter>> emitters = new ConcurrentHashMap<>();
  private final ConcurrentHashMap<String, Set<WebSocketSession>> sessions =
      new ConcurrentHashMap<>();

  /**
   * Creates the hub.
   *
   * @param mapper JSON mapper
   */
  public AssistantRealtimeHub(ObjectMapper mapper) {
    this.mapper = mapper;
  }

  /**
   * Registers an SSE listener for a session.
   *
   * @param sessionId session ID
   * @param emitter emitter to track
   */
  public void registerSse(String sessionId, SseEmitter emitter) {
    emitters.computeIfAbsent(sessionId, key -> ConcurrentHashMap.newKeySet()).add(emitter);
    emitter.onCompletion(() -> unregisterSse(sessionId, emitter));
    emitter.onTimeout(() -> unregisterSse(sessionId, emitter));
    emitter.onError(ex -> unregisterSse(sessionId, emitter));
  }

  /**
   * Registers a websocket listener for a session.
   *
   * @param sessionId session ID
   * @param session websocket session
   */
  public void registerWebSocket(String sessionId, WebSocketSession session) {
    sessions.computeIfAbsent(sessionId, key -> ConcurrentHashMap.newKeySet()).add(session);
  }

  /**
   * Unregisters an SSE listener.
   *
   * @param sessionId session ID
   * @param emitter emitter to remove
   */
  public void unregisterSse(String sessionId, SseEmitter emitter) {
    Set<SseEmitter> tracked = emitters.get(sessionId);
    if (tracked != null) {
      tracked.remove(emitter);
      if (tracked.isEmpty()) {
        emitters.remove(sessionId);
      }
    }
  }

  /**
   * Unregisters a websocket listener.
   *
   * @param sessionId session ID
   * @param session websocket session to remove
   */
  public void unregisterWebSocket(String sessionId, WebSocketSession session) {
    Set<WebSocketSession> tracked = sessions.get(sessionId);
    if (tracked != null) {
      tracked.remove(session);
      if (tracked.isEmpty()) {
        sessions.remove(sessionId);
      }
    }
  }

  /**
   * Broadcasts one realtime event to any listeners registered for the session.
   *
   * @param sessionId session ID
   * @param type event type
   * @param payload event payload
   */
  public void publish(String sessionId, String type, Object payload) {
    AssistantRealtimeEvent event = new AssistantRealtimeEvent(type, payload);
    Set<SseEmitter> sseEmitters = emitters.get(sessionId);
    if (sseEmitters != null) {
      for (SseEmitter emitter : sseEmitters.toArray(SseEmitter[]::new)) {
        try {
          emitter.send(SseEmitter.event().name(type).data(event));
        } catch (IOException ex) {
          unregisterSse(sessionId, emitter);
        }
      }
    }

    Set<WebSocketSession> webSockets = sessions.get(sessionId);
    if (webSockets == null) {
      return;
    }
    String json;
    try {
      json = mapper.writeValueAsString(event);
    } catch (Exception ex) {
      return;
    }
    for (WebSocketSession socket : webSockets.toArray(WebSocketSession[]::new)) {
      try {
        if (socket.isOpen()) {
          socket.sendMessage(new TextMessage(json));
        } else {
          unregisterWebSocket(sessionId, socket);
        }
      } catch (IOException ex) {
        unregisterWebSocket(sessionId, socket);
      }
    }
  }
}
