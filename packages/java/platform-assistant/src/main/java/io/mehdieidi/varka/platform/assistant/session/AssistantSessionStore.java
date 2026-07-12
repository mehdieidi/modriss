package io.mehdieidi.varka.platform.assistant.session;

import io.mehdieidi.varka.platform.assistant.domain.memory.AssistantMemoryRecords;
import io.mehdieidi.varka.platform.kernel.ModelLevel;
import io.mehdieidi.varka.platform.kernel.PlatformException;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Small runtime session registry until durable assistant repositories are wired to the new tables.
 */
public class AssistantSessionStore {

  private final Map<String, AssistantSession> sessions = new ConcurrentHashMap<>();

  /**
   * Creates a session.
   *
   * @param userId owner user ID
   * @param projectId project scope
   * @param level modeling level
   * @param title session title
   * @param threadId durable thread/session ID
   * @return created session
   */
  public AssistantSession create(
      String userId, String projectId, ModelLevel level, String title, String threadId) {
    Instant now = Instant.now();
    String id =
        threadId == null || threadId.isBlank()
            ? legacyThreadId(userId, projectId, level)
            : threadId.trim();
    AssistantSession session =
        new AssistantSession(
            id,
            userId,
            projectId,
            level,
            title == null || title.isBlank() ? level.apiName() + "-assistant" : title,
            now,
            now);
    sessions.put(session.id(), session);
    return session;
  }

  /**
   * Builds a legacy single-thread ID for backward compatibility.
   *
   * @param userId owner user ID
   * @param projectId project scope
   * @param level modeling level
   * @return legacy thread ID
   */
  public static String legacyThreadId(String userId, String projectId, ModelLevel level) {
    return userId + ":" + projectId + ":" + level.name();
  }

  /**
   * Builds a unique thread ID for a new conversation.
   *
   * @param userId owner user ID
   * @param projectId project scope
   * @param level modeling level
   * @return unique thread ID
   */
  public static String newThreadId(String userId, String projectId, ModelLevel level) {
    return legacyThreadId(userId, projectId, level) + ":" + UUID.randomUUID();
  }

  /**
   * Creates a runtime session from a durable thread row.
   *
   * @param thread durable thread
   * @return runtime session
   */
  public AssistantSession fromThread(AssistantMemoryRecords.ThreadRecord thread) {
    AssistantSession session =
        new AssistantSession(
            thread.id(),
            thread.userId(),
            thread.projectId(),
            thread.level(),
            thread.title(),
            thread.createdAt(),
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
   * @param id session ID
   * @param userId owner user ID
   */
  public void clear(String id, String userId) {
    require(id, userId);
    sessions.remove(id);
  }

  /**
   * Runtime assistant session.
   *
   * @param id session ID
   * @param projectId project scope
   * @param level modeling level
   * @param title display title
   * @param createdAt creation time
   * @param updatedAt update time
   */
  public record AssistantSession(
      String id,
      String userId,
      String projectId,
      ModelLevel level,
      String title,
      Instant createdAt,
      Instant updatedAt) {}
}
