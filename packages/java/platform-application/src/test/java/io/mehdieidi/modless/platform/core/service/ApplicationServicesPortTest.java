package io.mehdieidi.modless.platform.core.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.mehdieidi.modless.platform.core.PlatformException;
import io.mehdieidi.modless.platform.core.model.ProjectRecord;
import io.mehdieidi.modless.platform.core.model.UserRecord;
import io.mehdieidi.modless.platform.core.repository.PlatformStore;
import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.Test;

class ApplicationServicesPortTest {

  @Test
  void authServiceUsesPlatformStorePortForUsersAndSessions() {
    InMemoryPlatformStore store = new InMemoryPlatformStore();
    AuthService auth = new AuthService(store, Duration.ofHours(1));

    AuthService.AuthResult registered =
        auth.register("USER@example.com", "correct horse", "User One");
    AuthService.AuthResult loggedIn = auth.login("user@example.com", "correct horse");

    assertEquals("user@example.com", registered.user().email());
    assertEquals(registered.user().id(), auth.requireUser(loggedIn.token()).id());

    auth.logout(loggedIn.token());

    assertThrows(PlatformException.class, () -> auth.requireUser(loggedIn.token()));
  }

  @Test
  void projectServiceUsesPlatformStorePortForProjectLifecycle() {
    InMemoryPlatformStore store = new InMemoryPlatformStore();
    ProjectService projects = new ProjectService(store, null);
    UserRecord owner = user("owner-1");

    ProjectRecord created = projects.create(owner, "Example", "Initial");
    ProjectRecord updated =
        projects.update(owner, created.id(), "Renamed", "Changed", Map.of("cim", "model-1"));

    assertEquals("Renamed", projects.get(owner, created.id()).name());
    assertEquals(
        List.of(updated.id()), projects.list(owner).stream().map(ProjectRecord::id).toList());

    projects.delete(owner, created.id());

    assertFalse(store.containsPrefix(Path.of("projects", created.id())));
  }

  private UserRecord user(String id) {
    return new UserRecord(
        id,
        id + "@example.com",
        "Owner",
        "hash",
        "salt",
        java.time.Instant.now(),
        java.time.Instant.now());
  }

  private static final class InMemoryPlatformStore implements PlatformStore {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Map<String, Object> values = new ConcurrentHashMap<>();

    @Override
    public ObjectMapper objectMapper() {
      return objectMapper;
    }

    @Override
    public <T> Optional<T> read(Path path, Class<T> type) {
      Object value = values.get(key(path));
      if (value == null) {
        return Optional.empty();
      }
      return Optional.of(type.cast(value));
    }

    @Override
    public void write(Path path, Object value) {
      values.put(key(path), value);
    }

    @Override
    public void writeBytesAtomically(Path path, byte[] bytes) {
      values.put(key(path), bytes == null ? new byte[0] : bytes.clone());
    }

    @Override
    public Optional<byte[]> readBytes(Path path) {
      Object value = values.get(key(path));
      return value instanceof byte[] bytes ? Optional.of(bytes.clone()) : Optional.empty();
    }

    @Override
    public void deleteIfExists(Path path) {
      values.remove(key(path));
    }

    @Override
    public <T> List<T> list(Path directory, Class<T> type) {
      String prefix = prefix(directory);
      return values.entrySet().stream()
          .filter(entry -> entry.getKey().startsWith(prefix) && entry.getKey().endsWith(".json"))
          .map(entry -> type.cast(entry.getValue()))
          .toList();
    }

    @Override
    public void deleteTree(Path directory) {
      String prefix = prefix(directory);
      new LinkedHashMap<>(values)
          .keySet().stream().filter(key -> key.startsWith(prefix)).forEach(values::remove);
    }

    boolean containsPrefix(Path directory) {
      String prefix = prefix(directory);
      return values.keySet().stream().anyMatch(key -> key.startsWith(prefix));
    }

    private String key(Path path) {
      return path.toString().replace('\\', '/');
    }

    private String prefix(Path path) {
      String key = key(path);
      return key.endsWith("/") ? key : key + "/";
    }
  }
}
