package io.mehdieidi.modless.backend.assistant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.mehdieidi.modless.platform.core.model.ModelLevel;
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
        UserRecord user = new UserRecord("user-1", "user@example.com", "User", "hash", "salt",
                Instant.now(), Instant.now());
        ProjectRecord project = new ProjectRecord("project-1", "Project", "", user.id(),
                Map.of(), List.of(new ProjectMember(user.id(), user.email(), user.displayName(),
                io.mehdieidi.modless.platform.core.model.MemberRole.EDITOR, Instant.now())),
                Instant.now(), Instant.now());
        store.put(Path.of("users", user.id() + ".json"), user);
        store.put(Path.of("projects", project.id(), "project.json"), project);

        ProjectService projects = new ProjectService(store, new AuthService(store,
                java.time.Duration.ofDays(1)));
        AssistantMemoryRepository memory = new AssistantMemoryRepository(null, mapper) {
            @Override
            public ThreadRecord ensureThread(UserRecord u, String projectId, ModelLevel level,
                    String title, String modelId, Long revision) {
                return new ThreadRecord("thread-1", u.id(), projectId, level, title, modelId,
                        revision, Instant.now(), Instant.now());
            }

            @Override
            public void appendMessage(String threadId, String role, String content,
                    Map<String, Object> metadata) {
            }

            @Override
            public void saveProposal(String threadId, String projectId, String modelId,
                    long modelRevision, AssistantProposal proposal, String status) {
            }

            @Override
            public void appendAudit(String proposalId, String projectId, String actorId,
                    String action, Map<String, Object> details) {
            }

            @Override
            public Optional<ProposalRecord> findProposal(String proposalId) {
                return Optional.empty();
            }

            @Override
            public void updateProposalStatus(String proposalId, String status) {
            }

            @Override
            public void clearThread(String threadId) {
            }
        };
        AssistantCatalogService catalogs = new AssistantCatalogService(null) {
            @Override
            public List<AssistantModelProvider.ContextSnippet> search(String query, String level,
                    int limit) {
                return List.of();
            }
        };
        AssistantModelContextIndexService contexts = new AssistantModelContextIndexService();
        AssistantPatchCompiler patches = new AssistantPatchCompiler();
        AssistantRealtimeHub realtime = new AssistantRealtimeHub(mapper);
        ModelService models = null;
        AiProperties properties = new AiProperties(false, null, null, null, 0, 0, null, null,
                null, null, null);
        AssistantOrchestrator orchestrator = new AssistantOrchestrator(
                properties,
                new FailingProvider(), new AssistantSessionStore(), memory,
                new SpringAiChatMemoryService(), catalogs, contexts, patches, realtime,
                new AssistantHardeningService(properties, null), models,
                projects);

        String sessionId = orchestrator.startSession(user, project.id(), ModelLevel.CIM,
                "CIM Assistant").id();
        AssistantOrchestrator.AssistantTurnResponse response = orchestrator.handleMessage(user,
                sessionId, new AssistantOrchestrator.AssistantTurnRequest("Explain this", "model-1",
                        7L, "canvas", null, null));

        assertTrue(response.assistantMessage().contains("outbound provider calls are disabled"));
        assertNull(response.proposal());
        assertEquals("model-1", response.modelId());
        assertEquals(7L, response.revision());
        assertEquals(AssistantWorkflowState.EXPLAINED, response.workflowState());
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
        public void writeBytesAtomically(Path path, byte[] bytes) {
        }

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
