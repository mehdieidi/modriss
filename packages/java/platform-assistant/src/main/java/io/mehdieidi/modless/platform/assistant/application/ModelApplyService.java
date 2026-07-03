package io.mehdieidi.modless.platform.assistant.application;

import io.mehdieidi.modless.platform.assistant.domain.AssistantTurnPlan;
import io.mehdieidi.modless.platform.assistant.domain.context.AssistantModelContextTypes.AssistantModelContext;
import io.mehdieidi.modless.platform.assistant.provider.AssistantModelProvider;
import io.mehdieidi.modless.platform.assistant.session.AssistantSessionStore;
import io.mehdieidi.modless.platform.identity.domain.UserRecord;
import io.mehdieidi.modless.platform.model.domain.ModelRecord;
import java.util.List;

/** Orchestration-facing service for preview, apply, source XMI update, and proposal audit. */
public class ModelApplyService {

  private final TurnTransactionService transactionService;

  public ModelApplyService(TurnTransactionService transactionService) {
    this.transactionService = transactionService;
  }

  /** Applies an already validated assistant plan through the atomic transaction boundary. */
  public TurnTransactionService.Result applyValidated(
      UserRecord user,
      AssistantSessionStore.AssistantSession session,
      String threadId,
      ModelRecord targetModel,
      AssistantTurnPlan acceptedPlan,
      List<AssistantModelProvider.ContextSnippet> snippets,
      AssistantModelContext context,
      TurnTransactionService.Callbacks callbacks) {
    return transactionService.applyValidated(
        user, session, threadId, targetModel, acceptedPlan, snippets, context, callbacks);
  }
}
