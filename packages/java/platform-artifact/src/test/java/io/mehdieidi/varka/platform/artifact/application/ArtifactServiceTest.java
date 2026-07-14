package io.mehdieidi.varka.platform.artifact.application;

import static org.junit.jupiter.api.Assertions.assertThrows;

import io.mehdieidi.varka.platform.identity.application.AuthService;
import io.mehdieidi.varka.platform.kernel.PlatformException;
import io.mehdieidi.varka.platform.project.application.ProjectService;
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

class ArtifactServiceTest {

  @Test
  void rejectsTraversalPathWhenCreatingArtifact() {
    InMemoryPlatformStore store = new InMemoryPlatformStore();
    AuthService auth = new AuthService(store, Duration.ofHours(1));
    ProjectService projects = new ProjectService(store, auth);
    ArtifactService artifacts = new ArtifactService(store, projects);

    var user = auth.register("user@example.com", "correct horse", "User One").user();
    var project = projects.create(user, "Project", "");

    assertThrows(
        PlatformException.class,
        () -> artifacts.create(user, project.id(), "bad", Map.of("src/../secret.txt", "x")));
  }

  @Test
  void rejectsTraversalPathWhenUpdatingArtifact() {
    InMemoryPlatformStore store = new InMemoryPlatformStore();
    AuthService auth = new AuthService(store, Duration.ofHours(1));
    ProjectService projects = new ProjectService(store, auth);
    ArtifactService artifacts = new ArtifactService(store, projects);

    var user = auth.register("user@example.com", "correct horse", "User One").user();
    var project = projects.create(user, "Project", "");
    var artifact = artifacts.create(user, project.id(), "artifact", Map.of("src/main.go", "ok"));

    assertThrows(
        PlatformException.class,
        () -> artifacts.updateFile(user, artifact.id(), "/absolute.txt", "x"));
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
      return value == null ? Optional.empty() : Optional.of(type.cast(value));
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
