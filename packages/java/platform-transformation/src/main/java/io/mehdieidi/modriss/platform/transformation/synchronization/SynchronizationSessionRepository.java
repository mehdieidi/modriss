package io.mehdieidi.modriss.platform.transformation.synchronization;

import io.mehdieidi.modriss.platform.storage.api.PlatformStore;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/** Durable repository for unresolved synchronization sessions. */
public final class SynchronizationSessionRepository {

  private final PlatformStore store;

  public SynchronizationSessionRepository(PlatformStore store) {
    this.store = store;
  }

  public Optional<SynchronizationSession> get(String projectId, String sessionId) {
    return store.read(path(projectId, sessionId), SynchronizationSession.class);
  }

  public void save(SynchronizationSession session) {
    store.write(path(session.projectId(), session.id()), session);
  }

  public void delete(String projectId, String sessionId) {
    store.deleteIfExists(path(projectId, sessionId));
  }

  public List<SynchronizationSession> list(String projectId) {
    return store.list(
        Path.of("projects", projectId, "synchronization", "sessions"),
        SynchronizationSession.class);
  }

  private Path path(String projectId, String sessionId) {
    return Path.of("projects", projectId, "synchronization", "sessions", sessionId + ".json");
  }
}
