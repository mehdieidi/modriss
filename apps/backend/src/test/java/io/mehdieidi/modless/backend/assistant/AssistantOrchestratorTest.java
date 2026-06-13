package io.mehdieidi.modless.backend.assistant;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.mehdieidi.modless.platform.core.model.ModelLevel;
import io.mehdieidi.modless.platform.core.model.ModelRecord;
import io.mehdieidi.modless.platform.core.model.ProjectMember;
import io.mehdieidi.modless.platform.core.model.ProjectRecord;
import io.mehdieidi.modless.platform.core.model.UserRecord;
import io.mehdieidi.modless.platform.core.repository.PlatformStore;
import io.mehdieidi.modless.platform.core.service.AuthService;
import io.mehdieidi.modless.platform.core.service.ModelService;
import io.mehdieidi.modless.platform.core.service.ProjectService;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class AssistantOrchestratorTest {

  @Test
  void disabledAssistantReturnsCompatibilityResponseWithoutProviderCall() {
    ObjectMapper mapper = new ObjectMapper();
    InMemoryStore store = new InMemoryStore(mapper);
    UserRecord user =
        new UserRecord(
            "user-1", "user@example.com", "User", "hash", "salt", Instant.now(), Instant.now());
    ProjectRecord project =
        new ProjectRecord(
            "project-1",
            "Project",
            "",
            user.id(),
            Map.of(),
            List.of(
                new ProjectMember(
                    user.id(),
                    user.email(),
                    user.displayName(),
                    io.mehdieidi.modless.platform.core.model.MemberRole.EDITOR,
                    Instant.now())),
            Instant.now(),
            Instant.now());
    store.put(Path.of("users", user.id() + ".json"), user);
    store.put(Path.of("projects", project.id(), "project.json"), project);

    ProjectService projects =
        new ProjectService(store, new AuthService(store, java.time.Duration.ofDays(1)));
    AssistantMemoryRepository memory =
        new AssistantMemoryRepository(null, mapper) {
          @Override
          public ThreadRecord ensureThread(
              UserRecord u,
              String projectId,
              ModelLevel level,
              String title,
              String modelId,
              Long revision) {
            return new ThreadRecord(
                "thread-1",
                u.id(),
                projectId,
                level,
                title,
                modelId,
                revision,
                Instant.now(),
                Instant.now());
          }

          @Override
          public void appendMessage(
              String threadId, String role, String content, Map<String, Object> metadata) {}

          @Override
          public Optional<String> summary(String threadId) {
            return Optional.empty();
          }

          @Override
          public void saveProposal(
              String threadId,
              String projectId,
              String modelId,
              long modelRevision,
              AssistantProposal proposal,
              String status) {}

          @Override
          public void appendAudit(
              String proposalId,
              String projectId,
              String actorId,
              String action,
              Map<String, Object> details) {}

          @Override
          public Optional<ProposalRecord> findProposal(String proposalId) {
            return Optional.empty();
          }

          @Override
          public void updateProposalStatus(String proposalId, String status) {}

          @Override
          public void clearThread(String threadId) {}
        };
    AssistantCatalogService catalogs =
        new AssistantCatalogService(null) {
          @Override
          public List<AssistantModelProvider.ContextSnippet> search(
              String query, String level, int limit) {
            return List.of();
          }
        };
    AssistantModelContextIndexService contexts = new AssistantModelContextIndexService();
    AssistantPatchCompiler patches = new AssistantPatchCompiler();
    AssistantRealtimeHub realtime = new AssistantRealtimeHub(mapper);
    ModelService models = null;
    AiProperties properties =
        new AiProperties(false, null, null, null, 0, 0, null, null, null, null, null, null);
    AssistantOrchestrator orchestrator =
        new AssistantOrchestrator(
            properties,
            new FailingProvider(),
            new AssistantSessionStore(),
            memory,
            new SpringAiChatMemoryService(),
            catalogs,
            contexts,
            patches,
            realtime,
            new AssistantHardeningService(properties, null),
            models,
            projects);

    String sessionId =
        orchestrator.startSession(user, project.id(), ModelLevel.CIM, "CIM Assistant").id();
    AssistantOrchestrator.AssistantTurnResponse response =
        orchestrator.handleMessage(
            user,
            sessionId,
            new AssistantOrchestrator.AssistantTurnRequest(
                "Explain this", "model-1", 7L, "canvas", null, null));

    assertTrue(response.assistantMessage().contains("outbound provider calls are disabled"));
    assertNull(response.proposal());
    assertEquals("model-1", response.modelId());
    assertEquals(7L, response.revision());
    assertEquals(AssistantWorkflowState.EXPLAINED, response.workflowState());
  }

  @Test
  void proposalOnlyCreatesApprovalProposalWithoutCallingResponder() {
    ObjectMapper mapper = new ObjectMapper();
    InMemoryStore store = new InMemoryStore(mapper);
    UserRecord user =
        new UserRecord(
            "user-1", "user@example.com", "User", "hash", "salt", Instant.now(), Instant.now());
    ProjectRecord project =
        new ProjectRecord(
            "project-1",
            "Project",
            "",
            user.id(),
            Map.of("pim", "model-1"),
            List.of(
                new ProjectMember(
                    user.id(),
                    user.email(),
                    user.displayName(),
                    io.mehdieidi.modless.platform.core.model.MemberRole.EDITOR,
                    Instant.now())),
            Instant.now(),
            Instant.now());
    store.put(Path.of("users", user.id() + ".json"), user);
    store.put(Path.of("projects", project.id(), "project.json"), project);

    ProjectService projects =
        new ProjectService(store, new AuthService(store, java.time.Duration.ofDays(1)));
    AssistantMemoryRepository memory =
        new AssistantMemoryRepository(null, mapper) {
          @Override
          public ThreadRecord ensureThread(
              UserRecord u,
              String projectId,
              ModelLevel level,
              String title,
              String modelId,
              Long revision) {
            return new ThreadRecord(
                "thread-1",
                u.id(),
                projectId,
                level,
                title,
                modelId,
                revision,
                Instant.now(),
                Instant.now());
          }

          @Override
          public void appendMessage(
              String threadId, String role, String content, Map<String, Object> metadata) {}

          @Override
          public void saveProposal(
              String threadId,
              String projectId,
              String modelId,
              long modelRevision,
              AssistantProposal proposal,
              String status) {}

          @Override
          public void appendAudit(
              String proposalId,
              String projectId,
              String actorId,
              String action,
              Map<String, Object> details) {}

          @Override
          public Optional<String> summary(String threadId) {
            return Optional.empty();
          }

          @Override
          public List<MessageRecord> recentMessages(String threadId, int limit) {
            return List.of();
          }

          @Override
          public Optional<ProposalRecord> findProposal(String proposalId) {
            return Optional.empty();
          }

          @Override
          public void updateProposalStatus(String proposalId, String status) {}

          @Override
          public void clearThread(String threadId) {}
        };
    AssistantCatalogService catalogs =
        new AssistantCatalogService(null) {
          @Override
          public List<AssistantModelProvider.ContextSnippet> search(
              String query, String level, int limit) {
            return List.of();
          }
        };
    AssistantModelContextIndexService contexts = new AssistantModelContextIndexService();
    AssistantPatchCompiler patches = new AssistantPatchCompiler();
    AssistantRealtimeHub realtime = new AssistantRealtimeHub(mapper);
    ObjectNode starter = JsonNodeFactory.instance.objectNode();
    starter.put("name", "PIM Assistant");
    starter.put("modelLevel", "PIM");
    starter.putObject("diagram").putArray("elements");
    starter.with("diagram").putArray("relationships");
    ModelRecord created =
        new ModelRecord(
            "model-1",
            project.id(),
            ModelLevel.PIM,
            "PIM Assistant",
            starter,
            "test",
            "hash",
            1L,
            "xmi",
            "CURRENT",
            Instant.now(),
            Instant.now());
    ModelService models = mock(ModelService.class);
    when(models.get(eq(user), eq(ModelLevel.PIM), eq(created.id()))).thenReturn(created);
    when(models.validate(eq(user), eq(ModelLevel.PIM), eq(created.id())))
        .thenReturn(new ModelService.ValidationResult(true, List.of()));
    when(models.validate(eq(ModelLevel.PIM), any(com.fasterxml.jackson.databind.JsonNode.class)))
        .thenReturn(new ModelService.ValidationResult(true, List.of()));
    AiProperties properties =
        new AiProperties(
            true,
            AiProperties.RolloutMode.PROPOSAL_ONLY,
            null,
            null,
            0,
            0,
            null,
            null,
            null,
            null,
            null,
            null);
    AssistantOrchestrator orchestrator =
        new AssistantOrchestrator(
            properties,
            new ProposalProvider(),
            new AssistantSessionStore(),
            memory,
            new SpringAiChatMemoryService(),
            catalogs,
            contexts,
            patches,
            realtime,
            new AssistantHardeningService(properties, null),
            models,
            projects);

    String sessionId =
        orchestrator.startSession(user, project.id(), ModelLevel.PIM, "PIM Assistant").id();
    AssistantOrchestrator.AssistantTurnResponse response =
        assertDoesNotThrow(
            () ->
                orchestrator.handleMessage(
                    user,
                    sessionId,
                    new AssistantOrchestrator.AssistantTurnRequest(
                        "Create a serverless model", null, null, "canvas", null, null)));

    assertTrue(response.assistantMessage().contains("Review the preview below"));
    assertNotNull(response.proposal());
    assertTrue(response.proposal().approvalRequired());
    assertEquals("model-1", response.modelId());
    assertEquals(1L, response.revision());
    assertEquals(AssistantWorkflowState.PROPOSED, response.workflowState());
  }

  @Test
  void proposalOnlyBootstrapsBlankModelWhenNoActiveModelExists() {
    ObjectMapper mapper = new ObjectMapper();
    InMemoryStore store = new InMemoryStore(mapper);
    UserRecord user =
        new UserRecord(
            "user-1", "user@example.com", "User", "hash", "salt", Instant.now(), Instant.now());
    ProjectRecord project =
        new ProjectRecord(
            "project-1",
            "Project",
            "",
            user.id(),
            Map.of(),
            List.of(
                new ProjectMember(
                    user.id(),
                    user.email(),
                    user.displayName(),
                    io.mehdieidi.modless.platform.core.model.MemberRole.EDITOR,
                    Instant.now())),
            Instant.now(),
            Instant.now());
    store.put(Path.of("users", user.id() + ".json"), user);
    store.put(Path.of("projects", project.id(), "project.json"), project);

    ProjectService projects =
        new ProjectService(store, new AuthService(store, java.time.Duration.ofDays(1)));
    AssistantMemoryRepository memory =
        new AssistantMemoryRepository(null, mapper) {
          @Override
          public ThreadRecord ensureThread(
              UserRecord u,
              String projectId,
              ModelLevel level,
              String title,
              String modelId,
              Long revision) {
            return new ThreadRecord(
                "thread-1",
                u.id(),
                projectId,
                level,
                title,
                modelId,
                revision,
                Instant.now(),
                Instant.now());
          }

          @Override
          public void appendMessage(
              String threadId, String role, String content, Map<String, Object> metadata) {}

          @Override
          public void saveProposal(
              String threadId,
              String projectId,
              String modelId,
              long modelRevision,
              AssistantProposal proposal,
              String status) {}

          @Override
          public void appendAudit(
              String proposalId,
              String projectId,
              String actorId,
              String action,
              Map<String, Object> details) {}

          @Override
          public Optional<String> summary(String threadId) {
            return Optional.empty();
          }

          @Override
          public List<MessageRecord> recentMessages(String threadId, int limit) {
            return List.of();
          }

          @Override
          public Optional<ProposalRecord> findProposal(String proposalId) {
            return Optional.empty();
          }

          @Override
          public void updateProposalStatus(String proposalId, String status) {}

          @Override
          public void clearThread(String threadId) {}
        };
    AssistantCatalogService catalogs =
        new AssistantCatalogService(null) {
          @Override
          public List<AssistantModelProvider.ContextSnippet> search(
              String query, String level, int limit) {
            return List.of();
          }
        };
    AssistantModelContextIndexService contexts = new AssistantModelContextIndexService();
    AssistantPatchCompiler patches = new AssistantPatchCompiler();
    AssistantRealtimeHub realtime = new AssistantRealtimeHub(mapper);
    ModelService models = mock(ModelService.class);
    when(models.validate(eq(ModelLevel.PIM), any(com.fasterxml.jackson.databind.JsonNode.class)))
        .thenReturn(new ModelService.ValidationResult(true, List.of()));
    AiProperties properties =
        new AiProperties(
            true,
            AiProperties.RolloutMode.PROPOSAL_ONLY,
            null,
            null,
            0,
            0,
            null,
            null,
            null,
            null,
            null,
            null);
    AssistantOrchestrator orchestrator =
        new AssistantOrchestrator(
            properties,
            new BootstrapProvider(),
            new AssistantSessionStore(),
            memory,
            new SpringAiChatMemoryService(),
            catalogs,
            contexts,
            patches,
            realtime,
            new AssistantHardeningService(properties, null),
            models,
            projects);

    String sessionId =
        orchestrator.startSession(user, project.id(), ModelLevel.PIM, "PIM Assistant").id();
    AssistantOrchestrator.AssistantTurnResponse response =
        assertDoesNotThrow(
            () ->
                orchestrator.handleMessage(
                    user,
                    sessionId,
                    new AssistantOrchestrator.AssistantTurnRequest(
                        "Create a serverless architecture model for vending machine backend",
                        null,
                        null,
                        "canvas",
                        null,
                        null)));

    assertTrue(response.assistantMessage().contains("blank starter model proposal"));
    assertNotNull(response.proposal());
    assertTrue(response.proposal().approvalRequired());
    assertTrue(response.proposal().validation().mandatoryPassed());
    assertTrue(response.proposal().patch().operations().isEmpty());
    assertNull(response.modelId());
    assertEquals(0L, response.revision());
    assertEquals(AssistantWorkflowState.PROPOSED, response.workflowState());
  }

  private static final class FailingProvider implements AssistantModelProvider {

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

  private static final class ProposalProvider implements AssistantModelProvider {

    @Override
    public AssistantProviderMetadata metadata() {
      return new AssistantProviderMetadata("test", "test", "direct");
    }

    @Override
    public boolean available() {
      return true;
    }

    @Override
    public AssistantReply complete(AssistantPrompt prompt) {
      throw new AssertionError("Mutation requests should not call the responder.");
    }

    @Override
    public SemanticModelPatch proposePatch(AssistantPrompt prompt) {
      ObjectNode attributes = JsonNodeFactory.instance.objectNode();
      attributes.put("name", "Orders API");
      return new SemanticModelPatch(
          List.of(
              new SemanticModelPatch.Operation(
                  SemanticModelPatch.OperationType.ADD_ELEMENT,
                  "service-1",
                  "Service",
                  attributes,
                  null,
                  null)));
    }
  }

  private static final class BootstrapProvider implements AssistantModelProvider {

    @Override
    public AssistantProviderMetadata metadata() {
      return new AssistantProviderMetadata("test", "test", "direct");
    }

    @Override
    public boolean available() {
      return true;
    }

    @Override
    public AssistantReply complete(AssistantPrompt prompt) {
      throw new AssertionError("Bootstrap requests should not call the responder.");
    }

    @Override
    public SemanticModelPatch proposePatch(AssistantPrompt prompt) {
      return new SemanticModelPatch(List.of());
    }
  }

  private static final class InMemoryStore implements PlatformStore {

    private final ObjectMapper mapper;
    private final java.util.Map<String, Object> records = new java.util.LinkedHashMap<>();

    private InMemoryStore(ObjectMapper mapper) {
      this.mapper = mapper;
    }

    private void put(Path path, Object value) {
      records.put(path.normalize().toString().replace('\\', '/'), value);
    }

    @Override
    public ObjectMapper objectMapper() {
      return mapper;
    }

    @Override
    public <T> Optional<T> read(Path path, Class<T> type) {
      Object value = records.get(path.normalize().toString().replace('\\', '/'));
      return Optional.ofNullable(type.cast(value));
    }

    @Override
    public void write(Path path, Object value) {
      put(path, value);
    }

    @Override
    public void writeBytesAtomically(Path path, byte[] bytes) {}

    @Override
    public Optional<byte[]> readBytes(Path path) {
      return Optional.empty();
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
