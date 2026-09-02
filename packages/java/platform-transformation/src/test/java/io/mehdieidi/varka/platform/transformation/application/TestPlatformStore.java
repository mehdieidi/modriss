package io.mehdieidi.varka.platform.transformation.application;

import io.mehdieidi.varka.platform.artifact.domain.ArtifactIndexRecord;
import io.mehdieidi.varka.platform.artifact.domain.ArtifactRecord;
import io.mehdieidi.varka.platform.identity.domain.AuthSession;
import io.mehdieidi.varka.platform.identity.domain.UserRecord;
import io.mehdieidi.varka.platform.kernel.PlatformException;
import io.mehdieidi.varka.platform.model.domain.ModelIndexRecord;
import io.mehdieidi.varka.platform.model.domain.ModelRecord;
import io.mehdieidi.varka.platform.model.domain.StagedImportRecord;
import io.mehdieidi.varka.platform.project.domain.ProjectRecord;
import io.mehdieidi.varka.platform.storage.api.PlatformStore;
import io.mehdieidi.varka.platform.transformation.domain.MdeJobIdempotencyRecord;
import io.mehdieidi.varka.platform.transformation.domain.MdeJobIndexRecord;
import io.mehdieidi.varka.platform.transformation.domain.MdeJobRecord;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import tools.jackson.core.StreamReadConstraints;
import tools.jackson.core.json.JsonFactory;
import tools.jackson.databind.ObjectMapper;

/** Fast in-memory persistence-port implementation used by platform feature tests. */
final class TestPlatformStore implements PlatformStore {

  private final ObjectMapper mapper;
  private final Map<String, Object> values = new LinkedHashMap<>();
  private int writesBeforeFailure = -1;

  public TestPlatformStore(Path ignored) {
    JsonFactory factory =
        JsonFactory.builder()
            .streamReadConstraints(
                StreamReadConstraints.builder().maxStringLength(128 * 1024 * 1024).build())
            .build();
    mapper = new ObjectMapper(factory);
  }

  public void initialize() {
    // Test stores have no external resources to initialize.
  }

  void failAfterWrites(int writes) {
    if (writes < 0) throw new IllegalArgumentException("writes must be non-negative");
    writesBeforeFailure = writes;
  }

  @Override
  public <T> T inTransaction(java.util.function.Supplier<T> operation) {
    Map<String, Object> snapshot = new LinkedHashMap<>(values);
    try {
      T result = operation.get();
      writesBeforeFailure = -1;
      return result;
    } catch (RuntimeException ex) {
      values.clear();
      values.putAll(snapshot);
      writesBeforeFailure = -1;
      throw ex;
    }
  }

  @Override
  public ObjectMapper objectMapper() {
    return mapper;
  }

  @Override
  public <T> Optional<T> read(Path path, Class<T> type) {
    Object value = values.get(key(path));
    return value == null ? Optional.empty() : Optional.of(type.cast(value));
  }

  @Override
  public void write(Path path, Object value) {
    failIfConfigured();
    values.put(key(path), value);
  }

  @Override
  public void writeBytesAtomically(Path path, byte[] bytes) {
    failIfConfigured();
    byte[] payload = bytes == null ? new byte[0] : bytes.clone();
    if (path.toString().endsWith(".json")) {
      try {
        write(path, mapper.readValue(payload, recordType(path)));
        return;
      } catch (Exception ex) {
        throw new PlatformException(500, "Could not restore test data.");
      }
    }
    values.put(key(path), payload);
  }

  private void failIfConfigured() {
    if (writesBeforeFailure == 0) {
      throw new PlatformException(500, "Injected persistence failure.");
    }
    if (writesBeforeFailure > 0) writesBeforeFailure--;
  }

  @Override
  public Optional<byte[]> readBytes(Path path) {
    Object value = values.get(key(path));
    if (value == null) {
      return Optional.empty();
    }
    if (value instanceof byte[] bytes) {
      return Optional.of(bytes.clone());
    }
    try {
      return Optional.of(mapper.writeValueAsBytes(value));
    } catch (Exception ex) {
      throw new PlatformException(500, "Could not serialize test data.");
    }
  }

  @Override
  public void deleteIfExists(Path path) {
    values.remove(key(path));
  }

  @Override
  public <T> List<T> list(Path directory, Class<T> type) {
    String prefix = prefix(directory);
    return values.entrySet().stream()
        .filter(
            entry ->
                entry.getKey().startsWith(prefix)
                    && entry.getKey().endsWith(".json")
                    && type.isInstance(entry.getValue()))
        .map(entry -> type.cast(entry.getValue()))
        .toList();
  }

  @Override
  public void deleteTree(Path directory) {
    String prefix = prefix(directory);
    values.keySet().removeIf(key -> key.startsWith(prefix));
  }

  private Class<?> recordType(Path path) {
    String key = key(path);
    if (key.startsWith("users/")) {
      return UserRecord.class;
    }
    if (key.startsWith("sessions/")) {
      return AuthSession.class;
    }
    if (key.startsWith("model-imports/")) {
      return StagedImportRecord.class;
    }
    if (key.startsWith("indexes/models/")) {
      return ModelIndexRecord.class;
    }
    if (key.startsWith("indexes/artifacts/")) {
      return ArtifactIndexRecord.class;
    }
    if (key.startsWith("indexes/mde-jobs/")) {
      return MdeJobIndexRecord.class;
    }
    if (key.startsWith("indexes/mde-job-idempotency/")) {
      return MdeJobIdempotencyRecord.class;
    }
    if (key.matches("projects/[^/]+/project\\.json")) {
      return ProjectRecord.class;
    }
    if (key.contains("/models/")) {
      return ModelRecord.class;
    }
    if (key.contains("/artifacts/")) {
      return ArtifactRecord.class;
    }
    if (key.contains("/mde-jobs/")) {
      return MdeJobRecord.class;
    }
    throw new PlatformException(500, "Unsupported test storage key: " + key);
  }

  private String key(Path path) {
    return path.normalize().toString().replace('\\', '/');
  }

  private String prefix(Path path) {
    String key = key(path);
    return key.endsWith("/") ? key : key + "/";
  }
}
