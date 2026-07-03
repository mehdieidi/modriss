package io.mehdieidi.modless.platform.assistant.application;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.mehdieidi.modless.platform.assistant.domain.AssistantProposal;
import io.mehdieidi.modless.platform.assistant.domain.AssistantTurnPlan;
import io.mehdieidi.modless.platform.assistant.provider.AssistantModelProvider;
import io.mehdieidi.modless.platform.assistant.session.AssistantSessionStore;
import io.mehdieidi.modless.platform.identity.domain.UserRecord;
import io.mehdieidi.modless.platform.model.domain.ModelRecord;
import java.util.List;
import org.junit.jupiter.api.Test;

class ModelApplyServiceTest {

  @Test
  void delegatesValidatedApplyToAtomicTransactionBoundary() {
    TurnTransactionService transactionService = mock(TurnTransactionService.class);
    ModelApplyService service = new ModelApplyService(transactionService);
    TurnTransactionService.Result expected =
        new TurnTransactionService.Result(
            false, null, null, null, AssistantProposal.RiskLevel.HIGH);
    when(transactionService.applyValidated(
            any(), any(), anyString(), any(), any(), any(), any(), any()))
        .thenReturn(expected);

    TurnTransactionService.Result result =
        service.applyValidated(
            mock(UserRecord.class),
            mock(AssistantSessionStore.AssistantSession.class),
            "thread",
            mock(ModelRecord.class),
            mock(AssistantTurnPlan.class),
            List.<AssistantModelProvider.ContextSnippet>of(),
            null,
            mock(TurnTransactionService.Callbacks.class));

    assertSame(expected, result);
    verify(transactionService)
        .applyValidated(any(), any(), anyString(), any(), any(), any(), any(), any());
  }
}
