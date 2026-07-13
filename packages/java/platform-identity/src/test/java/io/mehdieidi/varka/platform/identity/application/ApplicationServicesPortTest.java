package io.mehdieidi.varka.platform.identity.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.mehdieidi.varka.platform.kernel.PlatformException;
import io.mehdieidi.varka.platform.storage.api.PlatformStore;
import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

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

    private String key(Path path) {
      return path.toString().replace('\\', '/');
    }

    private String prefix(Path path) {
      String key = key(path);
      return key.endsWith("/") ? key : key + "/";
    }
  }
}
