package io.mehdieidi.modless.platform.assistant.application;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.mehdieidi.modless.platform.assistant.agent.IntentPlanner;
import io.mehdieidi.modless.platform.assistant.patch.AssistantMetamodelSchemaService;
import io.mehdieidi.modless.platform.assistant.patch.AssistantPatchCompiler;
import io.mehdieidi.modless.platform.assistant.patch.AssistantPatchCompleter;
import io.mehdieidi.modless.platform.assistant.persistence.jdbc.JdbcAssistantModelContextIndex;
import io.mehdieidi.modless.platform.assistant.planning.AssistantClarificationGate;
import io.mehdieidi.modless.platform.assistant.provider.AssistantModelProvider;
import io.mehdieidi.modless.platform.assistant.session.AssistantSessionStore;
import io.mehdieidi.modless.platform.assistant.spi.AssistantCatalog;
import io.mehdieidi.modless.platform.assistant.spi.AssistantChatMemory;
import io.mehdieidi.modless.platform.assistant.spi.AssistantMemoryStore;
import io.mehdieidi.modless.platform.assistant.spi.AssistantMetrics;
import io.mehdieidi.modless.platform.assistant.spi.AssistantRealtimePublisher;
import io.mehdieidi.modless.platform.assistant.spi.AssistantSettings;
import io.mehdieidi.modless.platform.assistant.spi.AssistantSourceEvidenceStore;
import io.mehdieidi.modless.platform.assistant.spi.AssistantToolBridge;
import io.mehdieidi.modless.platform.assistant.support.AssistantSettingsFixtures;
import io.mehdieidi.modless.platform.identity.domain.UserRecord;
import io.mehdieidi.modless.platform.kernel.ModelLevel;
import io.mehdieidi.modless.platform.model.application.ModelLockService;
import io.mehdieidi.modless.platform.model.application.ModelService;
import io.mehdieidi.modless.platform.model.domain.ModelRecord;
import io.mehdieidi.modless.platform.project.application.ProjectService;
import io.mehdieidi.modless.platform.project.domain.ProjectRecord;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Shared wiring for orchestrator integration tests. */
final class AssistantOrchestratorTestHarness {

  final AssistantModelProvider provider;
  final AssistantSessionStore sessions = mock(AssistantSessionStore.class);
  final AssistantMemoryStore memory = mock(AssistantMemoryStore.class);
  final AssistantChatMemory chatMemory = mock(AssistantChatMemory.class);
  final AssistantCatalog catalogs = mock(AssistantCatalog.class);
  final AssistantSourceEvidenceStore sourceEvidenceStore = mock(AssistantSourceEvidenceStore.class);
  final JdbcAssistantModelContextIndex contexts = new JdbcAssistantModelContextIndex();
  final AssistantRealtimePublisher realtime = mock(AssistantRealtimePublisher.class);
  final ModelService models = mock(ModelService.class);
  final ModelLockService modelLocks = new ModelLockService();
  final ProjectService projects = mock(ProjectService.class);
  final AssistantMetamodelSchemaService schemas = new AssistantMetamodelSchemaService();
  final AssistantToolBridge tools = mock(AssistantToolBridge.class);
  final AssistantMetrics metrics = mock(AssistantMetrics.class);
  final ObjectMapper mapper = new ObjectMapper();
  final IntentPlanner intentPlanner;
  final CancellationRegistry cancellations = new CancellationRegistry();
  final UserRecord user =
      new UserRecord("user", "user@example.com", "User", "", "", Instant.now(), Instant.now());

  private AssistantOrchestratorTestHarness(
      AssistantModelProvider provider, IntentPlanner intentPlanner) {
    this.provider = provider;
    this.intentPlanner = intentPlanner;
  }

  static Builder builder(AssistantModelProvider provider) {
    return new Builder(provider);
  }

  AssistantOrchestrator build() throws Exception {
    AssistantSettings properties = AssistantSettingsFixtures.defaults();
    if (mockingDetails(provider).isMock()) {
      when(provider.metadata())
          .thenReturn(new AssistantModelProvider.AssistantProviderMetadata("test", "", ""));
      when(provider.available()).thenReturn(true);
    }
    when(sessions.require(anyString(), anyString())).thenReturn(pimSession());
    when(projects.get(eq(user), eq("project")))
        .thenReturn(
            new ProjectRecord(
                "project",
                "Project",
                "",
                "user",
                Map.of("PIM", "model-1"),
                List.of(),
                Instant.now(),
                Instant.now()));
    when(models.validateStructural(any(ModelLevel.class), any()))
        .thenReturn(new ModelService.ValidationResult(true, List.of()));
    when(models.modelLocks()).thenReturn(modelLocks);
    when(models.exportModel(any(), any(), anyString())).thenReturn(new byte[] {1});
    when(catalogs.search(anyString(), anyString(), anyInt())).thenReturn(List.of());
    when(catalogs.describeType(anyString(), anyString(), anyInt())).thenReturn(List.of());
    when(memory.summary(anyString())).thenReturn(Optional.empty());
    when(memory.recentMessages(anyString(), anyInt())).thenReturn(List.of());
    when(memory.userMessageCount(anyString())).thenReturn(0);
    when(memory.requireThread(anyString()))
        .thenReturn(
            new io.mehdieidi.modless.platform.assistant.domain.memory.AssistantMemoryRecords
                .ThreadRecord(
                "session",
                "user",
                "project",
                ModelLevel.PIM,
                "Orders",
                null,
                null,
                Instant.now(),
                Instant.now()));
    when(chatMemory.recent(anyString(), anyInt())).thenReturn(List.of());
    when(tools.consumeToolCallCount()).thenReturn(0);

    IntentPlanner planner = intentPlanner;

    return new AssistantOrchestrator(
        properties,
        provider,
        sessions,
        memory,
        chatMemory,
        catalogs,
        contexts,
        sourceEvidenceStore,
        new AssistantPatchCompiler(),
        new AssistantPatchCompleter(schemas),
        new AssistantValidationFeedbackResolver(schemas),
        new AssistantClarificationGate(),
        schemas,
        tools,
        metrics,
        mapper,
        realtime,
        new AssistantHardeningService(properties, null),
        models,
        projects,
        cancellations,
        null,
        null,
        planner,
        null,
        null,
        null,
        null,
        null,
        null,
        null);
  }

  void stubModel(long revision, JsonNode modelJson) {
    ModelRecord record =
        new ModelRecord(
            "model-1",
            "project",
            ModelLevel.PIM,
            "Orders",
            modelJson,
            "v1",
            "hash",
            revision,
            null,
            "CURRENT",
            Instant.now(),
            Instant.now());
    when(models.get(eq(user), eq(ModelLevel.PIM), eq("model-1"))).thenReturn(record);
    when(models.patch(
            eq(user), eq(ModelLevel.PIM), eq("model-1"), anyString(), any(), eq(revision)))
        .thenAnswer(
            invocation ->
                new ModelRecord(
                    record.id(),
                    record.projectId(),
                    record.level(),
                    record.name(),
                    record.modelJson(),
                    record.metamodelVersion(),
                    record.metamodelHash(),
                    revision + 1,
                    record.sourceXmiHash(),
                    record.migrationState(),
                    record.createdAt(),
                    Instant.now()));
  }

  AssistantSessionStore.AssistantSession pimSession() {
    return new AssistantSessionStore.AssistantSession(
        "session", "user", "project", ModelLevel.PIM, "Orders", Instant.now(), Instant.now());
  }

  AssistantSessionStore.AssistantSession cimSession() {
    return new AssistantSessionStore.AssistantSession(
        "cim-session", "user", "project", ModelLevel.CIM, "Workshop", Instant.now(), Instant.now());
  }

  static final class Builder {
    private final AssistantModelProvider provider;
    private IntentPlanner intentPlanner;

    Builder(AssistantModelProvider provider) {
      this.provider = provider;
    }

    Builder intentPlanner(IntentPlanner intentPlanner) {
      this.intentPlanner = intentPlanner;
      return this;
    }

    AssistantOrchestratorTestHarness create() {
      return new AssistantOrchestratorTestHarness(provider, intentPlanner);
    }
  }
}
