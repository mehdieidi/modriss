package io.mehdieidi.modless.platform.assistant.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import io.mehdieidi.modless.platform.assistant.agent.IntentPlanner;
import io.mehdieidi.modless.platform.assistant.domain.AssistantWorkflowState;
import io.mehdieidi.modless.platform.assistant.domain.memory.AssistantMemoryRecords.ThreadRecord;
import io.mehdieidi.modless.platform.assistant.source.SourceChunker;
import io.mehdieidi.modless.platform.assistant.support.MockModelingLlmSimulator;
import io.mehdieidi.modless.platform.kernel.ModelLevel;
import io.mehdieidi.modless.platform.kernel.PlatformException;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** Orchestrator-level resilience and idempotency integration tests. */
class AssistantOrchestratorResilienceIntegrationTest {

  private static final String MUTATION_DELTA =
      """
      {
        "intent": "MUTATION",
        "kind": "MODEL_DELTA",
        "message": "Created the order function.",
        "questions": [],
        "elements": [
          {
            "localId": "create-order",
            "eClass": "Function",
            "attributes": {"name": "Create order"},
            "placement": {"ownerId": "root", "referenceName": "functions"}
          }
        ],
        "references": [],
        "attributeUpdates": [],
        "deletions": [],
        "assumptions": []
      }
      """;

  @Test
  void staleRevisionDuringSlowProviderLeavesModelUnchanged() throws Exception {
    MockModelingLlmSimulator simulator =
        new MockModelingLlmSimulator()
            .blockStructuredUntilReleased()
            .thenStructuredReply(MUTATION_DELTA);
    AssistantOrchestratorTestHarness harness =
        AssistantOrchestratorTestHarness.builder(simulator).create();
    harness.stubModel(1L, pimModelWithFunction("fn-existing", "Existing"));
    AtomicInteger modelLoads = new AtomicInteger();
    when(harness.models.get(eq(harness.user), eq(ModelLevel.PIM), eq("model-1")))
        .thenAnswer(
            invocation -> {
              long revision = modelLoads.incrementAndGet() == 1 ? 1L : 2L;
              return modelRecord(revision, pimModelWithFunction("fn-existing", "Existing"));
            });
    AssistantOrchestrator orchestrator = harness.build();

    CompletableFuture<AssistantOrchestrator.AssistantTurnResponse> turn =
        CompletableFuture.supplyAsync(
            () ->
                orchestrator.handleMessage(
                    harness.user,
                    "session",
                    turnRequest("Add another function", "model-1", 1L, "stale-rev-key")));

    waitUntil(() -> simulator.structuredCallEntered());
    simulator.releaseStructured();
    AssistantOrchestrator.AssistantTurnResponse response = turn.get(30, TimeUnit.SECONDS);

    assertEquals(AssistantWorkflowState.FAILED, response.workflowState());
    assertTrue(
        response.assistantMessage().toLowerCase().contains("unchanged"),
        response.assistantMessage());
    verify(harness.models, never()).patch(any(), any(), anyString(), anyString(), any(), anyLong());
  }

  @Test
  void providerTimeoutLeavesModelUnchanged() throws Exception {
    MockModelingLlmSimulator simulator =
        new MockModelingLlmSimulator()
            .thenStructuredFailure(new PlatformException(504, "Provider timed out."));
    AssistantOrchestratorTestHarness harness =
        AssistantOrchestratorTestHarness.builder(simulator).create();
    harness.stubModel(1L, emptyPimModel());
    AssistantOrchestrator orchestrator = harness.build();

    AssistantOrchestrator.AssistantTurnResponse response =
        orchestrator.handleMessage(
            harness.user,
            "session",
            turnRequest("Create an order function", "model-1", 1L, "timeout-key"));

    assertEquals(AssistantWorkflowState.FAILED, response.workflowState());
    assertTrue(
        response.assistantMessage().toLowerCase().contains("unchanged"),
        response.assistantMessage());
    verify(harness.models, never()).patch(any(), any(), anyString(), anyString(), any(), anyLong());
  }

  @Test
  void cancellationMidTurnLeavesModelUnchanged() throws Exception {
    MockModelingLlmSimulator simulator =
        new MockModelingLlmSimulator()
            .blockStructuredUntilReleased()
            .thenStructuredReply(MUTATION_DELTA);
    AssistantOrchestratorTestHarness harness =
        AssistantOrchestratorTestHarness.builder(simulator).create();
    harness.stubModel(1L, emptyPimModel());
    AssistantOrchestrator orchestrator = harness.build();

    CompletableFuture<AssistantOrchestrator.AssistantTurnResponse> turn =
        CompletableFuture.supplyAsync(
            () ->
                orchestrator.handleMessage(
                    harness.user,
                    "session",
                    turnRequest("Create an order function", "model-1", 1L, "cancel-key")));

    waitUntil(() -> simulator.structuredCallEntered());
    orchestrator.cancelActiveTurn(harness.user, "session");
    simulator.releaseStructured();
    AssistantOrchestrator.AssistantTurnResponse response = turn.get(30, TimeUnit.SECONDS);

    assertEquals(AssistantWorkflowState.FAILED, response.workflowState());
    assertTrue(
        response.assistantMessage().toLowerCase().contains("unchanged"),
        response.assistantMessage());
    verify(harness.models, never()).patch(any(), any(), anyString(), anyString(), any(), anyLong());
    verify(harness.realtime, atLeastOnce())
        .publish(eq("session"), eq("assistant.turn.failed"), any());
  }

  @Test
  void duplicateIdempotencyKeyDoesNotApplyTwice() throws Exception {
    MockModelingLlmSimulator simulator =
        new MockModelingLlmSimulator().thenStructuredReply(MUTATION_DELTA);
    AssistantOrchestratorTestHarness harness =
        AssistantOrchestratorTestHarness.builder(simulator).create();
    harness.stubModel(1L, emptyPimModel());
    AssistantOrchestrator orchestrator = harness.build();
    AssistantOrchestrator.AssistantTurnRequest request =
        turnRequest("Create an order function", "model-1", 1L, "dup-key");

    AssistantOrchestrator.AssistantTurnResponse first =
        orchestrator.handleMessage(harness.user, "session", request);
    AssistantOrchestrator.AssistantTurnResponse second =
        orchestrator.handleMessage(harness.user, "session", request);

    assertEquals(AssistantWorkflowState.APPLIED, first.workflowState());
    assertEquals(first.workflowState(), second.workflowState());
    assertEquals(first.revision(), second.revision());
    verify(harness.models, times(1))
        .patch(eq(harness.user), eq(ModelLevel.PIM), eq("model-1"), anyString(), any(), eq(1L));
  }

  @Test
  void largeSourceDocumentIsChunkedAndEvidencePersistedWithinLimits() throws Exception {
    String largeDocument = "Workshop fact line.\n".repeat(900);
    int chunkCount = new SourceChunker().chunk("large-source", largeDocument, 4000, 24).size();
    assertTrue(chunkCount > 1);

    String coverageJson =
        """
        {
          "sourceId": "large-source",
          "facts": [
            {"id": "fact-1", "chunkId": "chunk-1", "kind": "SOURCE_NOTE", "summary": "Fact one"}
          ],
          "coverage": [
            {"chunkId": "chunk-1", "state": "COVERED", "note": "covered"},
            {"chunkId": "chunk-2", "state": "COMPRESSED", "note": "compressed"}
          ],
          "gaps": []
        }
        """;
    MockModelingLlmSimulator simulator =
        new MockModelingLlmSimulator()
            .sourceAnalysis(coverageJson)
            .thenStructuredReply(
                """
                {
                  "intent": "INFORMATION",
                  "kind": "ANSWER",
                  "message": "Source read.",
                  "questions": [],
                  "elements": [],
                  "references": [],
                  "attributeUpdates": [],
                  "deletions": [],
                  "assumptions": []
                }
                """);
    AssistantOrchestratorTestHarness harness =
        AssistantOrchestratorTestHarness.builder(simulator).create();
    AssistantOrchestrator orchestrator = harness.build();
    when(harness.sessions.require("cim-session", "user")).thenReturn(harness.cimSession());
    when(harness.memory.requireThread("cim-session"))
        .thenReturn(
            new ThreadRecord(
                "cim-session",
                "user",
                "project",
                ModelLevel.CIM,
                "Workshop",
                null,
                null,
                Instant.now(),
                Instant.now()));

    AssistantOrchestrator.AssistantTurnResponse response =
        orchestrator.handleMessage(
            harness.user,
            "cim-session",
            new AssistantOrchestrator.AssistantTurnRequest(
                "Create a CIM draft from these notes",
                null,
                null,
                "cim",
                List.of(),
                null,
                "workshop.md",
                largeDocument,
                "large-source-key"));

    assertEquals(AssistantWorkflowState.EXPLAINED, response.workflowState());
    assertTrue(chunkCount > 1, "fixture should require multiple chunks");
    ArgumentCaptor<
            io.mehdieidi.modless.platform.assistant.spi.AssistantSourceEvidenceStore
                .SourceEvidenceRecord>
        record =
            ArgumentCaptor.forClass(
                io.mehdieidi.modless.platform.assistant.spi.AssistantSourceEvidenceStore
                    .SourceEvidenceRecord.class);
    verify(harness.sourceEvidenceStore).save(record.capture());
    int covered = record.getValue().coverage().path("coveredChunks").asInt(0);
    int compressed = record.getValue().coverage().path("compressedChunks").asInt(0);
    assertTrue(covered + compressed >= 1);
    assertTrue(chunkCount <= 24);
    verify(harness.realtime, atLeastOnce())
        .publish(eq("cim-session"), eq("assistant.source.coverage"), any());
  }

  @Test
  void ambiguousCleanupDoesNotDeleteExistingElements() throws Exception {
    MockModelingLlmSimulator simulator =
        new MockModelingLlmSimulator()
            .thenStructuredReply(
                """
                {
                  "intent": "MUTATION",
                  "kind": "MODEL_DELTA",
                  "message": "Cleaned up the model.",
                  "questions": [],
                  "elements": [],
                  "references": [],
                  "attributeUpdates": [],
                  "deletions": [{"elementId": "fn-payment", "reason": "cleanup"}],
                  "assumptions": []
                }
                """);
    IntentPlanner intentPlanner = org.mockito.Mockito.mock(IntentPlanner.class);
    when(intentPlanner.classify(any(), any(), any()))
        .thenReturn(
            new IntentPlanner.IntentDecision(
                IntentPlanner.Intent.MUTATION,
                "EXTEND_MODEL",
                false,
                List.of(),
                List.of("Function")));
    AssistantOrchestratorTestHarness harness =
        AssistantOrchestratorTestHarness.builder(simulator).intentPlanner(intentPlanner).create();
    harness.stubModel(1L, pimModelWithFunction("fn-payment", "Payment API"));
    AssistantOrchestrator orchestrator = harness.build();

    AssistantOrchestrator.AssistantTurnResponse response =
        orchestrator.handleMessage(
            harness.user,
            "session",
            turnRequest("Make this model cleaner", "model-1", 1L, "cleanup-key"));

    assertNotEquals(AssistantWorkflowState.APPLIED, response.workflowState());
    verify(harness.models, never()).patch(any(), any(), anyString(), anyString(), any(), anyLong());
  }

  @Test
  void explicitDeleteRequestMayRemoveElements() throws Exception {
    MockModelingLlmSimulator simulator =
        new MockModelingLlmSimulator()
            .thenStructuredReply(
                """
                {
                  "intent": "MUTATION",
                  "kind": "MODEL_DELTA",
                  "message": "Removed the payment function.",
                  "questions": [],
                  "elements": [],
                  "references": [],
                  "attributeUpdates": [],
                  "deletions": [{"elementId": "fn-payment", "reason": "user requested deletion"}],
                  "assumptions": []
                }
                """);
    IntentPlanner intentPlanner = org.mockito.Mockito.mock(IntentPlanner.class);
    when(intentPlanner.classify(any(), any(), any()))
        .thenReturn(
            new IntentPlanner.IntentDecision(
                IntentPlanner.Intent.MUTATION,
                "DELETE_MODEL",
                false,
                List.of(),
                List.of("Function")));
    AssistantOrchestratorTestHarness harness =
        AssistantOrchestratorTestHarness.builder(simulator).intentPlanner(intentPlanner).create();
    harness.stubModel(1L, pimModelWithFunction("fn-payment", "Payment API"));
    AssistantOrchestrator orchestrator = harness.build();

    AssistantOrchestrator.AssistantTurnResponse response =
        orchestrator.handleMessage(
            harness.user,
            "session",
            turnRequest("Delete the Payment API function", "model-1", 1L, "delete-key"));

    assertEquals(AssistantWorkflowState.APPLIED, response.workflowState());
    verify(harness.models, times(1)).patch(any(), any(), anyString(), anyString(), any(), eq(1L));
  }

  private static AssistantOrchestrator.AssistantTurnRequest turnRequest(
      String message, String modelId, long revision, String idempotencyKey) {
    return new AssistantOrchestrator.AssistantTurnRequest(
        message, modelId, revision, "pim", List.of(), null, "", "", idempotencyKey);
  }

  private static JsonNode emptyPimModel() throws Exception {
    return new com.fasterxml.jackson.databind.ObjectMapper()
        .readTree(
            """
            {
              "id": "root",
              "eClass": "PIMModel",
              "modelLevel": "PIM",
              "name": "Orders",
              "functions": [],
              "diagram": {"elements": [], "relationships": []}
            }
            """);
  }

  private static JsonNode pimModelWithFunction(String id, String name) throws Exception {
    return new com.fasterxml.jackson.databind.ObjectMapper()
        .readTree(
            """
            {
              "id": "root",
              "eClass": "PIMModel",
              "modelLevel": "PIM",
              "name": "Orders",
              "functions": [
                {"id": "%s", "eClass": "Function", "name": "%s"}
              ],
              "diagram": {
                "elements": [{"id": "%s", "type": "Function", "label": "%s"}],
                "relationships": []
              }
            }
            """
                .formatted(id, name, id, name));
  }

  private static io.mehdieidi.modless.platform.model.domain.ModelRecord modelRecord(
      long revision, JsonNode modelJson) {
    return new io.mehdieidi.modless.platform.model.domain.ModelRecord(
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
  }

  private static void waitUntil(java.util.function.BooleanSupplier condition) throws Exception {
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
    while (!condition.getAsBoolean()) {
      if (System.nanoTime() > deadline) {
        throw new AssertionError("Timed out waiting for orchestrator condition.");
      }
      Thread.sleep(20);
    }
  }
}
