package io.mehdieidi.modless.backend.assistant;

/**
 * Shared realtime event envelope for websocket and SSE delivery.
 *
 * @param type event type
 * @param payload event payload
 */
public record AssistantRealtimeEvent(String type, Object payload) {}
