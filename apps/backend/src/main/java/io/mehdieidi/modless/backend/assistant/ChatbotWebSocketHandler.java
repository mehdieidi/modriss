package io.mehdieidi.modless.backend.assistant;

import io.mehdieidi.modless.platform.assistant.domain.AssistantReadyPayload;
import java.net.URI;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

/** Raw websocket bridge for assistant realtime events. */
@Component
public class ChatbotWebSocketHandler extends TextWebSocketHandler {

  private final AssistantRealtimeHub realtime;

  /**
   * Creates the websocket handler.
   *
   * @param realtime realtime hub
   */
  public ChatbotWebSocketHandler(AssistantRealtimeHub realtime) {
    this.realtime = realtime;
  }

  @Override
  public void afterConnectionEstablished(WebSocketSession session) throws Exception {
    String sessionId = sessionId(session);
    realtime.registerWebSocket(sessionId, session);
    realtime.publish(sessionId, "assistant.ready", new AssistantReadyPayload(sessionId));
  }

  @Override
  protected void handleTextMessage(WebSocketSession session, TextMessage message) {
    // The frontend uses websocket as a receive-only transport for assistant events.
  }

  @Override
  public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
    realtime.unregisterWebSocket(sessionId(session), session);
  }

  private String sessionId(WebSocketSession session) {
    URI uri = session.getUri();
    if (uri == null) {
      return "";
    }
    String[] segments = uri.getPath().split("/");
    return segments.length == 0 ? "" : segments[segments.length - 1];
  }
}
