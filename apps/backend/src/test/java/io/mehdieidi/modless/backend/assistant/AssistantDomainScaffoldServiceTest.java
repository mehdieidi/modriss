package io.mehdieidi.modless.backend.assistant;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.mehdieidi.modless.platform.identity.application.AuthService;
import io.mehdieidi.modless.platform.kernel.ModelLevel;
import io.mehdieidi.modless.platform.model.application.ModelService;
import io.mehdieidi.modless.platform.modeling.config.ModelingConfigService;
import io.mehdieidi.modless.platform.project.application.ProjectService;
import io.mehdieidi.modless.platform.storage.api.PlatformStore;
import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class AssistantDomainScaffoldServiceTest {

  private final AssistantMetamodelSchemaService schemas = new AssistantMetamodelSchemaService();
  private final AssistantDomainScaffoldService scaffolds =
      new AssistantDomainScaffoldService(schemas);
  private final AssistantPatchCompleter patchCompleter = new AssistantPatchCompleter(schemas);
  private final AssistantPatchCompiler patchCompiler = new AssistantPatchCompiler();

  @Test
  void serverlessScaffoldPassesMandatoryValidation() {
    String prompt = "Create a serverless model for vending machine backend";
    SemanticModelPatch patch = scaffolds.build(ModelLevel.PIM, prompt).orElseThrow();
    SemanticModelPatch completed =
        patchCompleter.complete(ModelLevel.PIM, patch, starterTypes("Vending Machine"));
    var baseModel = new ModelingConfigService().starterModel(ModelLevel.PIM, "Vending Machine");
    var compiled = patchCompiler.compile(baseModel, completed);
    var preview = patchCompiler.apply(baseModel, compiled);

    InMemoryStore store = new InMemoryStore(new ObjectMapper());
    AuthService auth = new AuthService(store, Duration.ofDays(1));
    ModelService models = new ModelService(store, new ProjectService(store, auth));
    ModelService.ValidationResult validation = models.validate(ModelLevel.PIM, preview);

    assertTrue(
        validation.issues().stream().noneMatch(issue -> "ERROR".equalsIgnoreCase(issue.severity())),
        () -> validation.issues().toString());
    assertTrue(
        completed.operations().stream()
                .filter(
                    operation -> operation.type() == SemanticModelPatch.OperationType.ADD_ELEMENT)
                .count()
            >= 8,
        "Expected a substantive scaffold");
    assertTrue(
        completed.operations().size() <= 96,
        () -> "Scaffold exceeds default maxToolCalls: " + completed.operations().size());
  }

  private java.util.Map<String, String> starterTypes(String modelName) {
    var baseModel = new ModelingConfigService().starterModel(ModelLevel.PIM, modelName);
    return Map.of(baseModel.path("id").asText(), baseModel.path("eClass").asText());
  }

  private static final class InMemoryStore implements PlatformStore {
    private final ObjectMapper mapper;
    private final Map<String, Object> records = new LinkedHashMap<>();

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
