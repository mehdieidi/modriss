package io.mehdieidi.modless.platform.assistant.spi;

import io.mehdieidi.modless.platform.assistant.domain.AssistantProposal;
import io.mehdieidi.modless.platform.assistant.domain.memory.AssistantMemoryRecords.ConversationSummary;
import io.mehdieidi.modless.platform.assistant.domain.memory.AssistantMemoryRecords.MessageRecord;
import io.mehdieidi.modless.platform.assistant.domain.memory.AssistantMemoryRecords.ProposalRecord;
import io.mehdieidi.modless.platform.assistant.domain.memory.AssistantMemoryRecords.ThreadRecord;
import io.mehdieidi.modless.platform.identity.domain.UserRecord;
import io.mehdieidi.modless.platform.kernel.ModelLevel;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Durable assistant conversation, proposal, and audit persistence port. */
public interface AssistantMemoryStore {

  ThreadRecord ensureThread(
      UserRecord user,
      String projectId,
      ModelLevel level,
      String title,
      String modelId,
      Long revision);

  String newThreadId(String userId, String projectId, ModelLevel level);

  ThreadRecord createThread(
      String threadId,
      UserRecord user,
      String projectId,
      ModelLevel level,
      String title,
      String modelId,
      Long revision);

  Optional<ThreadRecord> findMostRecentThread(
      String userId, String projectId, ModelLevel level, Instant since);

  List<ConversationSummary> listRecentConversations(
      String userId, String projectId, ModelLevel level, Instant since, int limit);

  void updateThreadModel(String threadId, String modelId, Long revision);

  void updateThreadTitle(String threadId, String title);

  int userMessageCount(String threadId);

  ThreadRecord requireThread(String threadId);

  Optional<ThreadRecord> findThread(String threadId, String userId);

  void appendMessage(String threadId, String role, String content, Map<String, Object> metadata);

  List<MessageRecord> recentMessages(String threadId, int limit);

  Optional<String> summary(String threadId);

  void updateSummary(String threadId, String summary, String messageId);

  void saveProposal(
      String threadId,
      String projectId,
      String modelId,
      long modelRevision,
      AssistantProposal proposal,
      String status);

  Optional<ProposalRecord> findProposal(String proposalId);

  Optional<ProposalRecord> findLatestProposal(String threadId, String status);

  void updateProposalStatus(String proposalId, String status);

  void markProposalApplied(String proposalId, String modelId, long modelRevision);

  void appendAudit(
      String proposalId,
      String projectId,
      String actorId,
      String action,
      Map<String, Object> details);

  void clearThread(String threadId);
}
