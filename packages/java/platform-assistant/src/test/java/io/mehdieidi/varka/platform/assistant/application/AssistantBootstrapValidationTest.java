package io.mehdieidi.varka.platform.assistant.application;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.mehdieidi.varka.platform.assistant.patch.AssistantMetamodelSchemaService;
import io.mehdieidi.varka.platform.identity.application.AuthService;
import io.mehdieidi.varka.platform.kernel.ModelLevel;
import io.mehdieidi.varka.platform.model.application.ModelService;
import io.mehdieidi.varka.platform.modeling.config.ModelingConfigService;
import io.mehdieidi.varka.platform.project.application.ProjectService;
import io.mehdieidi.varka.platform.storage.api.PlatformStore;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;

@ResourceLock("epsilon-runtime")
class AssistantBootstrapValidationTest {

  @Test
  void ecoreBackedStarterModelsPassMandatoryValidationForEveryLevel() {
    InMemoryStore store = new InMemoryStore(new ObjectMapper());
    AuthService auth = new AuthService(store, java.time.Duration.ofDays(1));
    ModelService models = new ModelService(store, new ProjectService(store, auth));
    ModelingConfigService modeling = new ModelingConfigService();

    for (ModelLevel level : ModelLevel.values()) {
      var starter = modeling.starterModel(level, level.name() + " Assistant");
      ModelService.ValidationResult validation = models.validate(level, starter);
      assertTrue(
          validation.issues().stream().noneMatch(issue -> "ERROR".equals(issue.severity())),
          () -> level + ": " + validation.issues());
    }
  }

  @Test
  void runtimeSchemaDerivesRootPlacementFromCurrentEcore() {
    AssistantMetamodelSchemaService schemas = new AssistantMetamodelSchemaService();
    assertTrue(
        schemas.containments(ModelLevel.PIM, "ServerlessService", "Function").stream()
            .anyMatch(reference -> "functions".equals(reference.name())));
    assertTrue(schemas.rootCollection(ModelLevel.CIM, "BusinessProcess").isPresent());
    assertTrue(schemas.languageIndex(ModelLevel.PSM).contains("AwsPsmModel"));
  }

  private static final class InMemoryStore implements PlatformStore {
    private final ObjectMapper mapper;
    private final java.util.Map<String, Object> records = new java.util.LinkedHashMap<>();

    private InMemoryStore(ObjectMapper mapper) {
      this.mapper = mapper;
    }

    @Override
    public ObjectMapper objectMapper() {
      return mapper;
    }

    @Override
    public <T> Optional<T> read(Path path, Class<T> type) {
      Object value = records.get(key(path));
      return Optional.ofNullable(value == null ? null : type.cast(value));
    }

    @Override
    public void write(Path path, Object value) {
      records.put(key(path), value);
    }

    @Override
    public void writeBytesAtomically(Path path, byte[] bytes) {
      records.put(key(path), bytes);
    }

    @Override
    public Optional<byte[]> readBytes(Path path) {
      Object value = records.get(key(path));
      return value instanceof byte[] bytes ? Optional.of(bytes) : Optional.empty();
    }

    @Override
    public void deleteIfExists(Path path) {
      records.remove(key(path));
    }

    @Override
    public <T> List<T> list(Path directory, Class<T> type) {
      String prefix = key(directory);
      return records.entrySet().stream()
          .filter(entry -> entry.getKey().startsWith(prefix))
          .map(entry -> type.cast(entry.getValue()))
          .toList();
    }

    @Override
    public void deleteTree(Path directory) {
      String prefix = key(directory);
      records.keySet().removeIf(value -> value.startsWith(prefix));
    }

    private String key(Path path) {
      return path.normalize().toString().replace('\\', '/');
    }
  }
}
