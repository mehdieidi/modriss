package io.mehdieidi.varka.platform.assistant.domain;

/**
 * Shared realtime event envelope for authenticated SSE delivery.
 *
 * @param type event type
 * @param payload event payload
 */
public record AssistantRealtimeEvent(String type, Object payload) {}
