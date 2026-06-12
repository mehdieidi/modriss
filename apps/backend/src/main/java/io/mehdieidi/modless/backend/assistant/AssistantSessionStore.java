package io.mehdieidi.modless.backend.assistant;

import io.mehdieidi.modless.platform.core.PlatformException;
import io.mehdieidi.modless.platform.core.model.ModelLevel;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * Small runtime session registry until durable assistant repositories are wired to the new tables.
 */
@Component
public class AssistantSessionStore {

    private final Map<String, AssistantSession> sessions = new ConcurrentHashMap<>();

    /**
     * Creates a session.
     *
     * @param userId    owner user ID
     * @param projectId project scope
     * @param level     modeling level
     * @param title     session title
     * @return created session
     */
    public AssistantSession create(String userId, String projectId, ModelLevel level,
            String title) {
        Instant now = Instant.now();
        String id = userId + ":" + projectId + ":" + level.name();
        AssistantSession session = new AssistantSession(id, userId,
                projectId, level,
                title == null || title.isBlank() ? level.apiName() + "-assistant" : title, now,
                now);
        sessions.put(session.id(), session);
        return session;
    }

    /**
     * Creates a runtime session from a durable thread row.
     *
     * @param thread durable thread
     * @return runtime session
     */
    public AssistantSession fromThread(AssistantMemoryRepository.ThreadRecord thread) {
        AssistantSession session = new AssistantSession(thread.id(), thread.userId(),
                thread.projectId(), thread.level(), thread.title(), thread.createdAt(),
                thread.updatedAt());
        sessions.put(session.id(), session);
        return session;
    }

    /**
     * Loads a session or returns {@code null}.
     *
     * @param id session ID
     * @return session or null
     */
    public AssistantSession require(String id, String userId) {
        AssistantSession session = sessions.get(id);
        if (session == null || !session.userId().equals(userId)) {
            throw new PlatformException(404, "Assistant session not found.");
        }
        return session;
    }

    /**
     * Clears a session from runtime memory.
     *
     * @param id     session ID
     * @param userId owner user ID
     */
    public void clear(String id, String userId) {
        require(id, userId);
        sessions.remove(id);
    }

    /**
     * Runtime assistant session.
     *
     * @param id        session ID
     * @param projectId project scope
     * @param level     modeling level
     * @param title     display title
     * @param createdAt creation time
     * @param updatedAt update time
     */
    public record AssistantSession(String id, String userId, String projectId, ModelLevel level,
                                   String title, Instant createdAt, Instant updatedAt) {

    }
}
