package io.mehdieidi.modless.backend.assistant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.mehdieidi.modless.platform.core.model.ModelLevel;
import io.mehdieidi.modless.platform.core.repository.PlatformStore;
import io.mehdieidi.modless.platform.core.service.AuthService;
import io.mehdieidi.modless.platform.core.service.ModelService;
import io.mehdieidi.modless.platform.core.service.ProjectService;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class AssistantBootstrapValidationTest {

  @Test
  void starterModelsPassMandatoryValidationForEveryLevel() throws Exception {
    TestHarness harness = harness();

    Method starter =
        AssistantOrchestrator.class.getDeclaredMethod(
            "starterModel", ModelLevel.class, String.class);
    starter.setAccessible(true);
    for (ModelLevel level : ModelLevel.values()) {
      ObjectNode model =
          (ObjectNode) starter.invoke(harness.orchestrator(), level, level.name() + " Assistant");
      ModelService.ValidationResult validation = harness.models().validate(level, model);

      assertTrue(
          validation.issues().stream().noneMatch(issue -> "ERROR".equals(issue.severity())),
          () -> level + ": " + validation.issues());
    }
  }

  @Test
  void deterministicPimArchitectureProposalPassesMandatoryValidation() throws Exception {
    TestHarness harness = harness();
    Method starter =
        AssistantOrchestrator.class.getDeclaredMethod(
            "starterModel", ModelLevel.class, String.class);
    starter.setAccessible(true);
    ObjectNode model =
        (ObjectNode) starter.invoke(harness.orchestrator(), ModelLevel.PIM, "PIM Assistant");

    Method template =
        AssistantOrchestrator.class.getDeclaredMethod(
            "pimServerlessArchitecturePatch",
            com.fasterxml.jackson.databind.JsonNode.class,
            String.class);
    template.setAccessible(true);
    SemanticModelPatch patch =
        (SemanticModelPatch)
            template.invoke(
                harness.orchestrator(),
                model,
                "Create a serverless architecture model for vending machine backend");

    assertTrue(patch.operations().size() > 0);
    AssistantPatchCompiler.CompiledPatch compiled = harness.patchCompiler().compile(model, patch);
    ObjectNode preview = harness.patchCompiler().apply(model, compiled);

    ModelService.ValidationResult validation = harness.models().validate(ModelLevel.PIM, preview);

    assertTrue(
        validation.issues().stream().noneMatch(issue -> "ERROR".equals(issue.severity())),
        () -> validation.issues().toString());
  }

  @Test
  void deterministicRootAttributePatchesCoverCimAndPsm() throws Exception {
    TestHarness harness = harness();
    Method starter =
        AssistantOrchestrator.class.getDeclaredMethod(
            "starterModel", ModelLevel.class, String.class);
    starter.setAccessible(true);
    Method deterministic =
        AssistantOrchestrator.class.getDeclaredMethod(
            "deterministicPatch",
            AssistantSessionStore.AssistantSession.class,
            com.fasterxml.jackson.databind.JsonNode.class,
            AssistantOrchestrator.AssistantTurnRequest.class);
    deterministic.setAccessible(true);

    ObjectNode cim =
        (ObjectNode) starter.invoke(harness.orchestrator(), ModelLevel.CIM, "CIM Assistant");
    @SuppressWarnings("unchecked")
    Optional<SemanticModelPatch> cimPatch =
        (Optional<SemanticModelPatch>)
            deterministic.invoke(
                harness.orchestrator(),
                new AssistantSessionStore.AssistantSession(
                    "s",
                    "u",
                    "p",
                    ModelLevel.CIM,
                    "CIM Assistant",
                    java.time.Instant.now(),
                    java.time.Instant.now()),
                cim,
                new AssistantOrchestrator.AssistantTurnRequest(
                    "Set the domain name to Smart Vending Operations.",
                    null,
                    null,
                    "canvas",
                    null,
                    null));
    assertTrue(cimPatch.isPresent());
    assertEquals("domainName", cimPatch.get().operations().get(0).referenceName());

    ObjectNode psm =
        (ObjectNode) starter.invoke(harness.orchestrator(), ModelLevel.PSM, "PSM Assistant");
    @SuppressWarnings("unchecked")
    Optional<SemanticModelPatch> psmPatch =
        (Optional<SemanticModelPatch>)
            deterministic.invoke(
                harness.orchestrator(),
                new AssistantSessionStore.AssistantSession(
                    "s",
                    "u",
                    "p",
                    ModelLevel.PSM,
                    "PSM Assistant",
                    java.time.Instant.now(),
                    java.time.Instant.now()),
                psm,
                new AssistantOrchestrator.AssistantTurnRequest(
                    "Set the default region to eu-central-1.", null, null, "canvas", null, null));
    assertTrue(psmPatch.isPresent());
    assertEquals("defaultRegion", psmPatch.get().operations().get(0).referenceName());
  }

  private TestHarness harness() {
    InMemoryStore store = new InMemoryStore(new ObjectMapper());
    AuthService auth = new AuthService(store, java.time.Duration.ofDays(1));
    ProjectService projects = new ProjectService(store, auth);
    ModelService models = new ModelService(store, projects);
    AiProperties properties =
        new AiProperties(false, null, null, null, 0, 0, null, null, null, null, null, null);
    AssistantPatchCompiler patchCompiler = new AssistantPatchCompiler();
    AssistantOrchestrator orchestrator =
        new AssistantOrchestrator(
            properties,
            new DummyProvider(),
            new AssistantSessionStore(),
            new AssistantMemoryRepository(null, store.objectMapper()),
            new SpringAiChatMemoryService(),
            new AssistantCatalogService(null),
            new AssistantModelContextIndexService(),
            patchCompiler,
            new AssistantRealtimeHub(store.objectMapper()),
            new AssistantHardeningService(properties, null),
            models,
            projects);
    return new TestHarness(orchestrator, models, patchCompiler);
  }

  private record TestHarness(
      AssistantOrchestrator orchestrator,
      ModelService models,
      AssistantPatchCompiler patchCompiler) {}

  private static final class DummyProvider implements AssistantModelProvider {
    @Override
    public AssistantProviderMetadata metadata() {
      return new AssistantProviderMetadata("test", "test", "direct");
    }

    @Override
    public boolean available() {
      return false;
    }

    @Override
    public AssistantReply complete(AssistantPrompt prompt) {
      throw new AssertionError("Provider should not be called.");
    }
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
      Object value = records.get(path.normalize().toString().replace('\\', '/'));
      return Optional.ofNullable(value == null ? null : type.cast(value));
    }

    @Override
    public void write(Path path, Object value) {
      records.put(path.normalize().toString().replace('\\', '/'), value);
    }

    @Override
    public void writeBytesAtomically(Path path, byte[] bytes) {
      records.put(
          path.normalize().toString().replace('\\', '/'),
          bytes == null ? new byte[0] : bytes.clone());
    }

    @Override
    public Optional<byte[]> readBytes(Path path) {
      Object value = records.get(path.normalize().toString().replace('\\', '/'));
      return value instanceof byte[] bytes ? Optional.of(bytes.clone()) : Optional.empty();
    }

    @Override
    public void deleteIfExists(Path path) {
      records.remove(path.normalize().toString().replace('\\', '/'));
    }

    @Override
    public <T> List<T> list(Path directory, Class<T> type) {
      String prefix = directory.normalize().toString().replace('\\', '/');
      return records.entrySet().stream()
          .filter(entry -> entry.getKey().startsWith(prefix))
          .map(entry -> type.cast(entry.getValue()))
          .toList();
    }

    @Override
    public void deleteTree(Path directory) {
      String prefix = directory.normalize().toString().replace('\\', '/');
      records.keySet().removeIf(key -> key.startsWith(prefix));
    }
  }
}
