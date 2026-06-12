package io.mehdieidi.modless.backend.assistant;

import io.mehdieidi.modless.backend.config.BackendProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * Registers the assistant websocket endpoint.
 */
@Configuration
@EnableWebSocket
public class ChatbotWebSocketConfig implements WebSocketConfigurer {

    private final BackendProperties properties;
    private final ChatbotWebSocketHandler handler;

    /**
     * Creates the websocket configuration.
     *
     * @param properties backend settings
     * @param handler    websocket handler
     */
    public ChatbotWebSocketConfig(BackendProperties properties, ChatbotWebSocketHandler handler) {
        this.properties = properties;
        this.handler = handler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        String[] origins = properties.allowedOrigins().toArray(String[]::new);
        registry.addHandler(handler, "/ws/chatbot/sessions/*")
                .setAllowedOrigins(origins);
    }
}
