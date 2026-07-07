package io.mehdieidi.modless.platform.assistant.application;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.mehdieidi.modless.platform.assistant.delta.StructuralValidationGate;
import io.mehdieidi.modless.platform.assistant.domain.AssistantTurnPlan;
import io.mehdieidi.modless.platform.assistant.domain.SemanticModelPatch;
import io.mehdieidi.modless.platform.assistant.patch.AssistantMetamodelSchemaService;
import io.mehdieidi.modless.platform.assistant.patch.AssistantPatchCompiler;
import io.mehdieidi.modless.platform.assistant.session.AssistantSessionStore;
import io.mehdieidi.modless.platform.assistant.spi.AssistantMemoryStore;
import io.mehdieidi.modless.platform.assistant.spi.AssistantRealtimePublisher;
import io.mehdieidi.modless.platform.identity.domain.UserRecord;
import io.mehdieidi.modless.platform.kernel.ModelLevel;
import io.mehdieidi.modless.platform.model.application.ModelLockService;
import io.mehdieidi.modless.platform.model.application.ModelService;
import io.mehdieidi.modless.platform.model.domain.ModelRecord;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TurnTransactionServiceTest {

  private final ObjectMapper mapper = new ObjectMapper();
  private final ModelService models = mock(ModelService.class);
  private final ModelLockService modelLocks = new ModelLockService();
  private final AssistantMemoryStore memory = mock(AssistantMemoryStore.class);
  private final AssistantRealtimePublisher realtime = mock(AssistantRealtimePublisher.class);
  private final TurnTransactionService service =
      new TurnTransactionService(
          new AssistantPatchCompiler(new AssistantMetamodelSchemaService()),
          new StructuralValidationGate(models),
          models,
          memory,
          realtime);
  private final UserRecord user =
      new UserRecord("user", "u@example.com", "User", "", "", Instant.now(), Instant.now());
  private final AssistantSessionStore.AssistantSession session =
      new AssistantSessionStore.AssistantSession(
          "session", "user", "project", ModelLevel.PIM, "Orders", Instant.now(), Instant.now());

  @BeforeEach
  void setUp() {
    when(models.modelLocks()).thenReturn(modelLocks);
  }

  @Test
  void appliesValidPatchAndPersistsProposalAuditAndRealtimeUpdate() throws Exception {
    ModelRecord target = model(1L);
    ModelRecord updated = model(2L);
    when(models.get(eq(user), eq(ModelLevel.PIM), eq("model-1"))).thenReturn(target);
    when(models.validateStructural(eq(ModelLevel.PIM), any()))
        .thenReturn(new ModelService.ValidationResult(true, List.of()));
    when(models.exportModel(eq(ModelLevel.PIM), any(), eq("xmi"))).thenReturn(new byte[] {1, 2});
    when(models.patch(eq(user), eq(ModelLevel.PIM), eq("model-1"), eq("Orders"), any(), eq(1L)))
        .thenReturn(updated);
    List<String> phases = new ArrayList<>();
    List<String> previews = new ArrayList<>();

    TurnTransactionService.Result result =
        service.applyValidated(
            user, session, "thread", target, plan(), List.of(), null, callbacks(phases, previews));

    assertTrue(result.applied());
    verify(models).patch(eq(user), eq(ModelLevel.PIM), eq("model-1"), eq("Orders"), any(), eq(1L));
    verify(models).attachSourceXmi(eq(updated), any());
    verify(memory).clearPendingInteraction("thread");
    verify(memory)
        .saveProposal(eq("thread"), eq("project"), eq("model-1"), eq(2L), any(), eq("APPLIED"));
    verify(memory).markProposalApplied(anyString(), eq("model-1"), eq(2L));
    verify(memory).appendAudit(anyString(), eq("project"), eq("user"), eq("APPLIED"), any());
    verify(realtime).publish(eq("session"), eq("model.updated"), any());
    assertTrue(phases.contains("apply_patch_compiled_against_target"));
    assertTrue(previews.contains("validated"));
  }

  @Test
  void rejectedPreviewDoesNotPersistOrPatch() throws Exception {
    ModelRecord target = model(1L);
    when(models.get(eq(user), eq(ModelLevel.PIM), eq("model-1"))).thenReturn(target);
    when(models.validateStructural(eq(ModelLevel.PIM), any()))
        .thenReturn(
            new ModelService.ValidationResult(
                false,
                List.of(
                    new ModelService.ValidationIssue(
                        "ERROR",
                        "MissingRequiredFeature",
                        "MODEL",
                        "name is required",
                        null,
                        "fn",
                        null))));

    TurnTransactionService.Result result =
        service.applyValidated(
            user,
            session,
            "thread",
            target,
            plan(),
            List.of(),
            null,
            callbacks(new ArrayList<>(), new ArrayList<>()));

    assertFalse(result.applied());
    verify(models, never()).patch(any(), any(), anyString(), anyString(), any(), anyLong());
    verify(memory, never())
        .saveProposal(anyString(), anyString(), anyString(), anyLong(), any(), anyString());
    verify(realtime, never()).publish(eq("session"), eq("model.updated"), any());
  }

  @Test
  void staleRevisionDoesNotValidatePersistOrPatch() throws Exception {
    ModelRecord target = model(1L);
    ModelRecord latest = model(2L);
    when(models.get(eq(user), eq(ModelLevel.PIM), eq("model-1"))).thenReturn(latest);

    TurnTransactionService.Result result =
        service.applyValidated(
            user,
            session,
            "thread",
            target,
            plan(),
            List.of(),
            null,
            callbacks(new ArrayList<>(), new ArrayList<>()));

    assertFalse(result.applied());
    verify(models, never()).validateStructural(any(), any());
    verify(models, never()).patch(any(), any(), anyString(), anyString(), any(), anyLong());
    verify(memory, never())
        .saveProposal(anyString(), anyString(), anyString(), anyLong(), any(), anyString());
    verify(realtime, never()).publish(eq("session"), eq("model.updated"), any());
  }

  private TurnTransactionService.Callbacks callbacks(List<String> phases, List<String> previews) {
    return new TurnTransactionService.Callbacks() {
      @Override
      public void phase(String event, long phaseStartedNanos, Object... keyValues) {
        phases.add(event);
      }

      @Override
      public void publishValidatedPreview(
          ModelRecord targetModel, AssistantPatchCompiler.CompiledPatch compiled) {
        previews.add("validated");
      }

      @Override
      public void checkActive() {}
    };
  }

  private AssistantTurnPlan plan() throws Exception {
    SemanticModelPatch patch =
        new SemanticModelPatch(
            List.of(
                new SemanticModelPatch.Operation(
                    SemanticModelPatch.OperationType.ADD_ELEMENT,
                    "fn",
                    "Function",
                    mapper.createObjectNode().put("name", "Create order"),
                    null,
                    null)));
    return new AssistantTurnPlan(
        AssistantTurnPlan.Intent.MUTATION,
        AssistantTurnPlan.Kind.PATCH,
        "Created function",
        List.of(),
        patch);
  }

  private ModelRecord model(long revision) throws Exception {
    JsonNode json =
        mapper.readTree(
            """
            {
              "id": "root",
              "eClass": "PIMModel",
              "modelLevel": "PIM",
              "name": "Orders",
              "services": [],
              "diagram": {"elements": [], "relationships": []}
            }
            """);
    return new ModelRecord(
        "model-1",
        "project",
        ModelLevel.PIM,
        "Orders",
        json,
        "v1",
        "hash",
        revision,
        null,
        "CURRENT",
        Instant.now(),
        Instant.now());
  }
}
