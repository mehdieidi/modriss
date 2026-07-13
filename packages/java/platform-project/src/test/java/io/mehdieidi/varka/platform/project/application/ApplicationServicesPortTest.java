package io.mehdieidi.varka.platform.project.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.mehdieidi.varka.platform.identity.application.AuthService;
import io.mehdieidi.varka.platform.identity.domain.UserRecord;
import io.mehdieidi.varka.platform.kernel.PlatformException;
import io.mehdieidi.varka.platform.project.domain.ProjectMember;
import io.mehdieidi.varka.platform.project.domain.ProjectRecord;
import io.mehdieidi.varka.platform.storage.api.PlatformStore;
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

  @Test
  void ownerManagesMembersWhileMembersCanOnlyEditProjectContent() {
    InMemoryPlatformStore store = new InMemoryPlatformStore();
    AuthService auth = new AuthService(store, Duration.ofHours(1));
    ProjectService projects = new ProjectService(store, auth);
    UserRecord owner = auth.register("owner@example.com", "password-1", "Owner").user();
    UserRecord member = auth.register("member@example.com", "password-1", "Member").user();

    ProjectRecord project = projects.create(owner, "Example", "Initial");
    ProjectMember added = projects.invite(owner, project.id(), member.email(), "Architect");

    assertEquals("Architect", added.role());
    assertEquals(
        List.of(project.id()), projects.list(member).stream().map(ProjectRecord::id).toList());

    ProjectRecord memberUpdate =
        projects.update(member, project.id(), "Member edit", "Changed", Map.of());
    assertEquals("Member edit", memberUpdate.name());

    PlatformException inviteFailure =
        assertThrows(
            PlatformException.class,
            () -> projects.invite(member, project.id(), "owner@example.com", "Reviewer"));
    assertEquals(403, inviteFailure.status());

    ProjectMember changed = projects.updateMemberRole(owner, project.id(), member.id(), "Reviewer");
    assertEquals("Reviewer", changed.role());

    PlatformException deleteFailure =
        assertThrows(PlatformException.class, () -> projects.delete(member, project.id()));
    assertEquals(403, deleteFailure.status());

    projects.revoke(owner, project.id(), member.id());

    assertEquals(List.of(), projects.list(member));
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

    private final tools.jackson.databind.ObjectMapper objectMapper =
        new tools.jackson.databind.ObjectMapper();
    private final Map<String, Object> values = new ConcurrentHashMap<>();

    @Override
    public tools.jackson.databind.ObjectMapper objectMapper() {
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
