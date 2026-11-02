package io.mehdieidi.modriss.platform.artifact.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.mehdieidi.modriss.platform.identity.application.AuthService;
import io.mehdieidi.modriss.platform.kernel.PlatformException;
import io.mehdieidi.modriss.platform.project.application.ProjectService;
import io.mehdieidi.modriss.platform.storage.api.PlatformStore;
import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.ZipInputStream;
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

    PlatformException failure =
        assertThrows(
            PlatformException.class,
            () -> artifacts.create(user, project.id(), "bad", Map.of("src/../secret.txt", "x")));

    assertEquals(400, failure.status());
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

    PlatformException failure =
        assertThrows(
            PlatformException.class,
            () -> artifacts.updateFile(user, artifact.id(), "/absolute.txt", "x"));

    assertEquals(400, failure.status());
  }

  @Test
  void createsFilesAndFoldersAtApprovedExtensionPoints() throws Exception {
    InMemoryPlatformStore store = new InMemoryPlatformStore();
    AuthService auth = new AuthService(store, Duration.ofHours(1));
    ProjectService projects = new ProjectService(store, auth);
    ArtifactService artifacts = new ArtifactService(store, projects);
    var user = auth.register("editor@example.com", "correct horse", "Editor").user();
    var project = projects.create(user, "Project", "");
    var metadata = store.objectMapper().createObjectNode();
    metadata.putObject("generationRules").put("src/generated.go", "ExampleGenerator");
    var artifact =
        artifacts.create(user, project.id(), "artifact", metadata, Map.of("src/generated.go", "generated"));

    var withDirectory = artifacts.createDirectory(user, artifact.id(), "src/manual");
    var withFile = artifacts.createFile(user, artifact.id(), "src/manual/notes.md", "local notes");
    var zip = artifacts.zip(user, artifact.id());

    assertTrue(withDirectory.modelJson().path("directories").toString().contains("src/manual"));
    assertEquals("local notes", withFile.files().get("src/manual/notes.md"));
    try (var input = new ZipInputStream(new java.io.ByteArrayInputStream(zip))) {
      var entries = new java.util.ArrayList<String>();
      java.util.zip.ZipEntry entry;
      while ((entry = input.getNextEntry()) != null) entries.add(entry.getName());
      assertTrue(entries.contains("src/manual/"));
      assertTrue(entries.contains("src/manual/notes.md"));
    }
    var withRootFolder = artifacts.createDirectory(user, artifact.id(), "manual");
    var withRootFile = artifacts.createFile(user, artifact.id(), "manual/README.md", "local docs");
    artifacts.createFile(user, artifact.id(), "src/plain.txt", "plain file");
    assertTrue(withRootFolder.modelJson().path("directories").toString().contains("manual"));
    assertEquals("local docs", withRootFile.files().get("manual/README.md"));
    assertEquals(
        409,
        assertThrows(
                PlatformException.class,
                () -> artifacts.createDirectory(user, artifact.id(), "src/manual"))
            .status());
    assertEquals(
        400,
        assertThrows(
                PlatformException.class,
                () -> artifacts.createDirectory(user, artifact.id(), "unrelated/nested"))
            .status());
    assertEquals(
        400,
        assertThrows(
                PlatformException.class,
                () -> artifacts.createDirectory(user, artifact.id(), "src/plain.txt/nested"))
            .status());
  }

  @Test
  void renamesUserOwnedFilesAndFoldersButKeepsGeneratedPathsFixed() {
    InMemoryPlatformStore store = new InMemoryPlatformStore();
    AuthService auth = new AuthService(store, Duration.ofHours(1));
    ProjectService projects = new ProjectService(store, auth);
    ArtifactService artifacts = new ArtifactService(store, projects);
    var user = auth.register("rename@example.com", "correct horse", "Editor").user();
    var project = projects.create(user, "Project", "");
    var metadata = store.objectMapper().createObjectNode();
    metadata.putObject("generationRules").put("src/generated.go", "ExampleGenerator");
    var artifact =
        artifacts.create(user, project.id(), "artifact", metadata, Map.of("src/generated.go", "generated"));

    artifacts.createDirectory(user, artifact.id(), "src/manual");
    artifacts.createDirectory(user, artifact.id(), "src/manual/empty");
    artifacts.createFile(user, artifact.id(), "src/manual/notes.md", "user notes");
    var renamedFolder = artifacts.renameEntry(user, artifact.id(), "src/manual", "refined");

    assertEquals("user notes", renamedFolder.files().get("src/refined/notes.md"));
    assertTrue(renamedFolder.modelJson().path("directories").toString().contains("src/refined/empty"));
    assertFalse(renamedFolder.modelJson().path("directories").toString().contains("src/manual"));

    var renamedFile =
        artifacts.renameEntry(user, artifact.id(), "src/refined/notes.md", "README.md");
    assertEquals("user notes", renamedFile.files().get("src/refined/README.md"));
    assertFalse(renamedFile.files().containsKey("src/refined/notes.md"));
    assertEquals("generated", renamedFile.files().get("src/generated.go"));

    assertEquals(
        400,
        assertThrows(
                PlatformException.class,
                () -> artifacts.renameEntry(user, artifact.id(), "src/generated.go", "renamed.go"))
            .status());
    assertEquals(
        400,
        assertThrows(
                PlatformException.class,
                () -> artifacts.renameEntry(user, artifact.id(), "src", "renamed-src"))
            .status());
    assertEquals(
        400,
        assertThrows(
                PlatformException.class,
                () -> artifacts.renameEntry(user, artifact.id(), "src/refined", "../outside"))
            .status());
    assertEquals(
        404,
        assertThrows(
                PlatformException.class,
                () -> artifacts.renameEntry(user, artifact.id(), "src/missing", "renamed"))
            .status());
  }

  @Test
  void importsWrappedArchiveWhileKeepingGeneratorOutputsAndRejectsInvalidStructure() {
    InMemoryPlatformStore store = new InMemoryPlatformStore();
    AuthService auth = new AuthService(store, Duration.ofHours(1));
    ProjectService projects = new ProjectService(store, auth);
    ArtifactService artifacts = new ArtifactService(store, projects);
    var user = auth.register("archive@example.com", "correct horse", "Archive").user();
    var project = projects.create(user, "Project", "");
    var metadata = store.objectMapper().createObjectNode();
    metadata
        .putObject("generationRules")
        .put("src/generated.go", "ExampleGenerator")
        .put("generated/trace/artifact-trace.json", "TraceGenerator");
    var artifact =
        artifacts.create(
            user,
            project.id(),
            "artifact",
            metadata,
            Map.of(
                "src/generated.go", "old",
                "generated/trace/artifact-trace.json", "{\"artifacts\": []}"));

    var imported =
        artifacts.importProjectArchive(
            user,
            artifact.id(),
            Map.of(
                "local-project/src/generated.go", "refined",
                "local-project/generated/trace/artifact-trace.json", "{\"artifacts\": []}",
                "local-project/src/manual/notes.md", "user-owned"),
            Set.of(
                "local-project",
                "local-project/src",
                "local-project/src/empty",
                "local-project/generated/trace"));

    assertEquals(artifact.id(), imported.id());
    assertEquals("refined", imported.files().get("src/generated.go"));
    assertEquals("user-owned", imported.files().get("src/manual/notes.md"));
    assertTrue(imported.modelJson().path("directories").toString().contains("src/empty"));
    assertTrue(imported.modelJson().path("projectArchiveImportedAt").isTextual());
    assertEquals(
        400,
        assertThrows(
                PlatformException.class,
                () ->
                    artifacts.updateFile(
                        user,
                        artifact.id(),
                        "generated/trace/artifact-trace.json",
                        "tampered"))
            .status());

    assertEquals(
        400,
        assertThrows(
                PlatformException.class,
                () ->
                    artifacts.importProjectArchive(
                        user, artifact.id(), Map.of("README.md", "missing generator output"), Set.of()))
            .status());
    var withRootExtension =
        artifacts.importProjectArchive(
            user,
            artifact.id(),
            Map.of(
                "src/generated.go", "refined",
                "generated/trace/artifact-trace.json", "{\"artifacts\": []}",
                "manual/notes.md", "root folder extension"),
            Set.of("manual", "manual/empty", "generated/trace"));
    assertEquals("root folder extension", withRootExtension.files().get("manual/notes.md"));
    assertTrue(withRootExtension.modelJson().path("directories").toString().contains("manual/empty"));

    assertEquals(
        400,
        assertThrows(
                PlatformException.class,
                () ->
                    artifacts.importProjectArchive(
                        user,
                        artifact.id(),
                        Map.of(
                            "src/generated.go", "refined",
                            "generated/trace/artifact-trace.json", "{\"artifacts\": []}",
                            "manual", "file collides with folder"),
                        Set.of("manual", "generated/trace")))
            .status());
    assertFalse(imported.files().isEmpty());
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
