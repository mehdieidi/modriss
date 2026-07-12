package io.mehdieidi.varka.platform.assistant.spi;

/** Publishes assistant realtime events to connected clients. */
public interface AssistantRealtimePublisher {

  /**
   * Broadcasts one realtime event to listeners registered for the session.
   *
   * @param sessionId session ID
   * @param type event type
   * @param payload event payload
   */
  void publish(String sessionId, String type, Object payload);
}
