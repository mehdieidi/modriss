package io.mehdieidi.modriss.backend.assistant;

import io.mehdieidi.modriss.platform.assistant.domain.AssistantRealtimeEvent;
import io.mehdieidi.modriss.platform.assistant.spi.AssistantRealtimePublisher;
import java.io.IOException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/** Authenticated SSE fan-out for assistant session events. */
@Service
public class AssistantRealtimeHub implements AssistantRealtimePublisher {
  private final ConcurrentHashMap<String, Set<SseEmitter>> emitters = new ConcurrentHashMap<>();

  public AssistantRealtimeHub(tools.jackson.databind.ObjectMapper ignoredMapper) {}

  public void registerSse(String sessionId, SseEmitter emitter) {
    emitters.computeIfAbsent(sessionId, key -> ConcurrentHashMap.newKeySet()).add(emitter);
    emitter.onCompletion(() -> unregisterSse(sessionId, emitter));
    emitter.onTimeout(() -> unregisterSse(sessionId, emitter));
    emitter.onError(ex -> unregisterSse(sessionId, emitter));
  }

  public void unregisterSse(String sessionId, SseEmitter emitter) {
    Set<SseEmitter> tracked = emitters.get(sessionId);
    if (tracked == null) return;
    tracked.remove(emitter);
    if (tracked.isEmpty()) emitters.remove(sessionId);
  }

  @Override
  public void publish(String sessionId, String type, Object payload) {
    AssistantRealtimeEvent event = new AssistantRealtimeEvent(type, payload);
    Set<SseEmitter> tracked = emitters.get(sessionId);
    if (tracked == null) return;
    for (SseEmitter emitter : tracked.toArray(SseEmitter[]::new)) {
      try {
        emitter.send(SseEmitter.event().name(type).data(event));
      } catch (IOException ex) {
        unregisterSse(sessionId, emitter);
      }
    }
  }
}
